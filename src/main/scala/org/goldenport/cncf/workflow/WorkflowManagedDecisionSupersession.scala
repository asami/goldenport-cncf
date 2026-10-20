package org.goldenport.cncf.workflow

import io.circe.Json
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision

/** Explicit owning disposition of an unanswered Decision. This is not a Human
  * answer or an ordinary mechanical transition. The application owns independent
  * admission of its new directive and the allowed business successor. CNCF owns
  * the actual pending Decision selection and atomic native instance/Continuation
  * update, preserving the original question and recording Superseded separately.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  */
final class WorkflowManagedDecisionSupersession[W, A] private (
  subsystem: Subsystem, component: ComponentId, configuration: Configuration,
  operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
  instances: DataStore.CollectionId, continuations: DataStore.CollectionId,
  inputCodec: PayloadCodec[W], codec: PayloadCodec[A], owner: DataStoreComponentOwner
) {
  import WorkflowManagedDecisionSupersession.*
  import WorkflowInstanceDataStorePersistence.{recordField, revisionField, storageText, storageJsonC}

  def executeC(identity: ContinuationIdentity, expected: InstanceRevision, current: InstanceRecord,
    entry: HistoryEntry, payload: TypedValue[A])(using context: ExecutionContext): Consequence[Retained[A]] =
    _with_host_c { (store, space, native, issued, claims) => for {
      _ <- if (identity != null && expected != null && current != null && entry != null && payload != null &&
          payload.typeIdentity == codec.typeIdentity) Consequence.unit
        else Consequence.stateConflict("Decision supersession intent is incomplete")
      loaded <- native.load(configuration, current.identity)
      actual <- _required_c(loaded, "Decision supersession instance is absent")
      _ <- if (actual.revision == expected && actual.lifecycle == Lifecycle.Active &&
          actual.suspension.exists(_.continuationIdentity == NativeIdentity(identity.value)) &&
          entry.lifecycle == Lifecycle.Active && entry.suspension.isEmpty && entry.correlation == actual.correlation)
        Consequence.unit else Consequence.stateConflict("Decision supersession requires its exact current unanswered frontier")
      selected <- issued._load_selected_c(identity, actual).flatMap(_required_c(_, "Decision was not atomically issued"))
      request <- WorkflowIssuedWorkDataStorePersistence.requestC(selected._1, WorkflowIssuedContinuationKind.Decision)
      _ <- if (selected._2._1.status == ContinuationRuntimePersistence.Status.Available && selected._2._1.claimId.isEmpty &&
          actual.suspension.exists(boundary => boundary.contextSnapshot.value == request.context.snapshot.workflowRevision &&
            boundary.completion.value == request.completion.identity && boundary.evidence.value == request.evidence.identity))
        Consequence.unit else Consequence.stateConflict("Decision supersession requires an unclaimed matching issued Decision")
      _ <- payload.validateC
      next <- actual.appendC(expected, entry)
      receipt = Receipt(identity, expected, next.revision, payload.value)
      saved <- _encode_c(receipt, codec)
      superseded <- ContinuationRecordJsonV1.encodeC(selected._2._1.copy(status = ContinuationRuntimePersistence.Status.Superseded))
      rootrevision <- EntityRevision.createC(actual.revision.value + 1L)
      nextroot <- rootrevision.nextC
      nextclaim <- selected._2._2.nextC
      root <- DataStoreConditionalRoot.create(owner, instances, DataStore.StringEntryId(actual.identity.value),
        revisionField, Some(rootrevision), Vector.empty,
        Record.dataAuto(recordField -> storageText(WorkflowInstanceRecordJsonV1._encode_unchecked(next))), nextroot)
      second <- DataStoreConditionalRoot.create(owner, continuations, WorkflowContinuationDataStorePersistence.id(identity),
        WorkflowContinuationDataStorePersistence.revisionField, Some(selected._2._2), Vector.empty,
        Record.dataAuto(WorkflowContinuationDataStorePersistence.payloadField -> storageText(superseded),
          _payload_field -> storageText(saved)), nextclaim)
      plan <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Update(second),
        correlation = DataStoreConditionalCorrelation("workflow-decision-supersession"))
      prepared = WorkflowPreparedDataStoreTransition.create(store, plan, changed => changed match {
        case DataStoreConditionalTransitionResult.Transitioned(a, b) => for {
          instance <- native.decodeC(a, actual.identity)
          continuation <- claims.decodeStoredC(b, identity)
          raw <- _required_c(b.getString(_payload_field), "Committed Decision disposition is absent")
          retained <- storageJsonC(raw).flatMap(_decode_text_c(_, codec))
          _ <- if (continuation._1.status == ContinuationRuntimePersistence.Status.Superseded) Consequence.unit
            else Consequence.stateConflict("Committed Decision has no superseded disposition")
        } yield new Retained(retained, instance)
        case _ => Consequence.stateConflict("Decision supersession native revision or Continuation update was refused")
      })
      participant <- prepared.hostParticipantC(space, Vector.empty, Vector.empty,
        () => Consequence.unit, _ => Consequence.unit)
      committed <- {
        val unitofwork = new ManagedCompletionUnitOfWork(context, participant)
        unitofwork.commit().flatMap { _ => unitofwork.storageOutcome match {
          case Some(ManagedCompletionParticipant.Outcome.Verified(value)) => Consequence.success(value)
          case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate("Decision supersession outcome is unknown")
        }}
      }
    } yield committed }

  /** Read only the real disposition; a historical receipt grants no new effect. */
  def loadRetainedC(identity: ContinuationIdentity, instance: InstanceIdentity)(using ExecutionContext):
      Consequence[Option[Retained[A]]] = _with_host_c { (store, _, native, issued, claims) => for {
    loaded <- native.load(configuration, instance)
    actual <- _required_c(loaded, "Decision supersession instance is absent")
    selected <- issued._load_selected_c(identity, actual)
    retained <- selected match {
      case Some((_, (continuation, _))) if continuation.status == ContinuationRuntimePersistence.Status.Superseded => for {
        row <- store.load(continuations, WorkflowContinuationDataStorePersistence.id(identity))
          .flatMap(_required_c(_, "Superseded Decision is absent"))
        raw <- _required_c(row.getString(_payload_field), "Superseded Decision disposition is absent")
        receipt <- storageJsonC(raw).flatMap(_decode_text_c(_, codec))
        prior <- actual.history.find(_.revision == receipt.sourceRevision)
          .map(Consequence.success).getOrElse(Consequence.stateConflict("Decision disposition predecessor is absent"))
        next <- actual.history.find(_.revision == receipt.nextRevision)
          .map(Consequence.success).getOrElse(Consequence.stateConflict("Decision disposition successor is absent"))
        _ <- if (receipt.identity == identity && prior.suspension.exists(_.continuationIdentity == NativeIdentity(identity.value)) &&
            receipt.nextRevision == receipt.sourceRevision.next && next.lifecycle == Lifecycle.Active && next.suspension.isEmpty)
          Consequence.unit else Consequence.stateConflict("Decision disposition differs from actual native history")
      } yield Some(new Retained(receipt, actual))
      case _ => Consequence.success(None)
    }
  } yield retained }

  private def _with_host_c[B](body: (DataStore & EntityConditionalUpdateDataStore, DataStoreSpace,
    WorkflowInstanceDataStorePersistence, WorkflowIssuedWorkDataStorePersistence[W],
    WorkflowContinuationDataStorePersistence) => Consequence[B])(using context: ExecutionContext): Consequence[B] =
    if (context == null) Consequence.stateConflict("Decision supersession requires its current execution context")
    else for {
      _ <- subsystem.admitWorkflowStorageC(component, configuration.workflowStore.name, instances, continuations)
      result <- subsystem.withWorkflowVerifiedMultiGuardSpaceC(component,
        ComponentDataStore.Environment(context.runtime.resolvedParameters, Some(subsystem.configuration))) { space => for {
        root <- space.dataStore(instances)
        second <- space.dataStore(continuations)
        store <- root match {
          case value: EntityConditionalUpdateDataStore if root eq second =>
            Consequence.success(value.asInstanceOf[DataStore & EntityConditionalUpdateDataStore])
          case _ => Consequence.configurationInvalid("Decision supersession requires the same admitted guarded provider")
        }
        native <- WorkflowInstanceDataStorePersistence.bindC(store, configuration, definitions, instances)
        issued <- WorkflowIssuedWorkDataStorePersistence.bindForKindC(store, configuration, component, operation,
          definitions, instances, continuations, inputCodec, WorkflowIssuedContinuationKind.Decision)
        claims <- WorkflowContinuationDataStorePersistence.bindC(store, configuration, continuations)
        value <- body(store, space, native, issued, claims)
      } yield value }
    } yield result
}

object WorkflowManagedDecisionSupersession {
  private val _payload_field = "decisionSupersessionPayload"
  private val _schema_version = "cncf.workflow-decision-supersession.v1"
  final case class Receipt[A](identity: ContinuationIdentity, sourceRevision: InstanceRevision,
    nextRevision: InstanceRevision, payload: A)
  final class Retained[A] private[WorkflowManagedDecisionSupersession](val receipt: Receipt[A], val current: InstanceRecord)
  private def _required_c[A](value: Option[A], reason: String): Consequence[A] =
    value.map(Consequence.success).getOrElse(Consequence.stateConflict(reason))
  private def _encode_c[A](value: Receipt[A], codec: PayloadCodec[A]): Consequence[String] =
    _guarded_c(Consequence.success(Json.obj("schemaVersion" -> Json.fromString(_schema_version),
      "identity" -> Json.fromString(value.identity.value), "sourceRevision" -> Json.fromString(value.sourceRevision.value.toString),
      "nextRevision" -> Json.fromString(value.nextRevision.value.toString), "typeIdentity" -> Json.fromString(codec.typeIdentity),
      "payload" -> codec.encode(value.payload)).noSpaces))
  private def _decode_text_c[A](value: String, codec: PayloadCodec[A]): Consequence[Receipt[A]] =
    io.circe.parser.parse(value).fold(
      _ => Consequence.stateConflict("Decision disposition is not valid JSON"),
      _decode_c(_, codec)
    )
  private def _decode_c[A](value: Json, codec: PayloadCodec[A]): Consequence[Receipt[A]] = _guarded_c {
    val decoded = for {
      fields <- value.asObject.toRight("Decision disposition must be an object")
      schema <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      id <- value.hcursor.get[String]("identity").left.map(_.message)
      source <- value.hcursor.get[String]("sourceRevision").left.map(_.message)
      next <- value.hcursor.get[String]("nextRevision").left.map(_.message)
      kind <- value.hcursor.get[String]("typeIdentity").left.map(_.message)
      _ <- Either.cond(schema == _schema_version && kind == codec.typeIdentity &&
        fields.keys.toSet == Set("schemaVersion", "identity", "sourceRevision", "nextRevision", "typeIdentity", "payload") &&
        WorkflowMechanicalTransition.canonical(id), (), "Decision disposition contract differs")
      a <- source.toLongOption.filter(n => n > 0 && n.toString == source && n < Long.MaxValue).toRight("Invalid Decision source revision")
      b <- next.toLongOption.filter(n => n == a + 1 && n.toString == next).toRight("Invalid Decision next revision")
      payload <- codec.decode(fields("payload").get)
      _ <- Either.cond(payload != null, (), "Decision disposition payload is null")
    } yield Receipt(ContinuationIdentity(id), InstanceRevision(a), InstanceRevision(b), payload)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }
  private def _guarded_c[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case interrupted: InterruptedException => Thread.currentThread().interrupt(); throw interrupted
    case NonFatal(_) => Consequence.stateConflict("Decision disposition application codec failed")
  }
  def bindC[W, A](subsystem: Subsystem, component: ComponentId, configuration: Configuration,
    operation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, inputCodec: PayloadCodec[W], codec: PayloadCodec[A]):
      Consequence[WorkflowManagedDecisionSupersession[W, A]] = for {
    _ <- if (subsystem != null && component != null && configuration != null && operation != null &&
        inputCodec != null && codec != null && WorkflowMechanicalTransition.canonical(codec.typeIdentity)) Consequence.unit
      else Consequence.configurationInvalid("Decision supersession binding is incomplete")
    accepted <- configuration.validateC
    _ <- accepted.delivery match {
      case _: DeliveryConfiguration.SameStore => Consequence.unit
      case _ => Consequence.configurationInvalid("Decision supersession requires same-store delivery")
    }
    _ <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
    _ <- subsystem.admitWorkflowStorageC(component, accepted.workflowStore.name, instances, continuations)
    owner <- DataStoreComponentOwner.create(component.name)
  } yield new WorkflowManagedDecisionSupersession(subsystem, component, accepted, operation, definitions,
    instances, continuations, inputCodec, codec, owner)
}
