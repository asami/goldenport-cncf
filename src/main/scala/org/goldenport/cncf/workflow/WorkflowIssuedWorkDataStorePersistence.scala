package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Sep. 29, 2026
 * @version Oct.  9, 2026
 */
/** Borrowed, same-store issued-work publication and recovery. Initial issue is
  * exclusively a compound instance/Continuation create, never a later SPI write.
  * Reads/retries confer no claim or progression authority. This is not UoW,
  * admission-evidence or completion atomicity.
  */
final class WorkflowIssuedWorkDataStorePersistence[W] private (
  store: DataStore,
  configuration: Configuration,
  component: ComponentId,
  completionOperation: StateMachineOperationIdentity,
  instances: WorkflowInstanceDataStorePersistence,
  continuations: WorkflowContinuationDataStorePersistence,
  continuationCollection: DataStore.CollectionId,
  codec: PayloadCodec[W],
  kind: WorkflowIssuedContinuationKind
)(using ExecutionContext) extends IssuedWorkOrderPersistence[W] {
  import WorkflowIssuedWorkDataStorePersistence.*
  import WorkflowInstanceDataStorePersistence.storageJsonC

  def suspendC(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    issued: WorkflowInteraction[W, Nothing], owner: DataStoreComponentOwner
  ): Consequence[WorkflowInstanceDataStorePersistence.PersistedSuspension] =
    prepareSuspensionC(intent, issued, owner).flatMap(_.commitC())

  /** Exact issue admission without publishing a claimable Continuation. */
  def prepareSuspensionC(intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    issued: WorkflowInteraction[W, Nothing], owner: DataStoreComponentOwner
  ): Consequence[WorkflowPreparedDataStoreTransition[WorkflowInstanceDataStorePersistence.PersistedSuspension]] =
    instances.prepareIssuedSuspensionC(intent, owner, continuationCollection, issued, codec, component, completionOperation, kind)

  /** Select original saved work only; refuse to retrofit a separately published Continuation. */
  def putIfAbsentC(issued: WorkflowInteraction[W, Nothing]):
      Consequence[WorkflowInteraction[W, Nothing]] =
    for {
      request <- requestC(issued, kind)
      existing <- loadC(request.continuationId)
      admitted <- existing match {
        case Some(value) => IssuedWorkOrderPersistence.selectIssuedC(value, issued, kind)
        case None => Consequence.stateConflict("Initial WorkOrder issue requires atomic suspension")
      }
    } yield admitted

  def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowInteraction[W, Nothing]]] =
    _load_source_c(identity).flatMap {
      case None => Consequence.success(None)
      case Some((work, source)) => for {
        loaded <- instances.load(configuration, work.handle.instanceIdentity)
        instance <- loaded.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Issued WorkOrder instance is unavailable"))
        _ <- matchingC(instance, source._1.continuation, work, component, completionOperation, kind)
      } yield Some(work)
    }

  /** Consume one native source with the completion owner's actual instance.
    * No instance reload, second Continuation read or publication certificate.
    */
  private[workflow] def _load_selected_c(identity: ContinuationIdentity, instance: InstanceRecord):
      Consequence[Option[(WorkflowInteraction[W, Nothing], (ContinuationRuntimePersistence.Record, EntityRevision))]] =
    _load_source_c(identity).flatMap {
      case None => Consequence.success(None)
      case Some(value) => matchingC(instance, value._2._1.continuation, value._1,
        component, completionOperation, kind).map(_ => Some(value))
    }

  private def _load_source_c(identity: ContinuationIdentity):
      Consequence[Option[(WorkflowInteraction[W, Nothing], (ContinuationRuntimePersistence.Record, EntityRevision))]] =
    if (identity == null || identity.value == null || identity.value.trim.isEmpty)
      Consequence.stateConflict("Issued WorkOrder requires a Continuation identity")
    else store.load(continuationCollection, WorkflowContinuationDataStorePersistence.id(identity)).flatMap {
      case None => Consequence.success(None)
      case Some(stored) => for {
        continuation <- continuations.decodeStoredC(stored, identity)
        payload <- stored.getString(payloadField).map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Atomically issued WorkOrder payload is missing"))
        raw <- storageJsonC(payload)
        issued <- decodeC(raw, codec, kind)
      } yield Some((issued, continuation))
    }
}

object WorkflowIssuedWorkDataStorePersistence {
  private[workflow] val payloadField = "issuedWorkPayload"

  /** Read the selected stored issue's binding references only. A Completion
    * query does not need to decode application input or re-admit AI work.
    * This reference carries no claim, execution or publication authority.
    */
  private[workflow] def _reference_c(bytes: String, kind: WorkflowIssuedContinuationKind,
      component: ComponentId, completionoperation: StateMachineOperationIdentity): Consequence[WorkflowHandle] = safelyC {
    import io.circe.parser.parse
    import WorkflowResultJsonV1.{_decodeHandle, _expect, _fields, _string}
    val decoded = for {
      json <- Option(bytes).toRight("Issued reference bytes are missing")
        .flatMap(value => parse(value).left.map(_ => "Invalid issued reference JSON"))
      fields <- json.asObject.map(_.toMap).toRight("Issued reference must be an object")
      schema <- _string(fields, "schemaVersion")
      _ <- _expect(schema == WorkflowProtocolV1.schemaVersion, "Unsupported issued reference schema")
      selectedkind <- _string(fields, "kind")
      _ <- _expect(selectedkind == (kind match {
        case WorkflowIssuedContinuationKind.WorkOrder => "WORK_ORDER"
        case WorkflowIssuedContinuationKind.Decision => "DECISION"
      }), "Foreign issued reference kind")
      handle <- fields.get("handle").toRight("Issued handle is missing").flatMap(_decodeHandle)
      _ <- _expect(handle.componentIdentity == component, "Foreign issued Component")
      request <- fields.get("request").flatMap(_.asObject).toRight("Issued request reference is missing")
      route <- request("completionOperation").toRight("Issued completion Operation is missing")
        .flatMap(value => _fields(value, Set("service", "operation")))
      service <- _string(route, "service")
      operation <- _string(route, "operation")
      _ <- _expect(StateMachineOperationIdentity(service, operation) == completionoperation,
        "Foreign issued completion Operation")
    } yield handle
    decoded.fold(Consequence.stateConflict(_), handle => handle.validateC.map(_ => handle))
  }

  def bindC[W](store: DataStore, configuration: Configuration, component: ComponentId,
    completionOperation: StateMachineOperationIdentity,
    definitions: Vector[DefinitionBinding], instanceCollection: DataStore.CollectionId,
    continuationCollection: DataStore.CollectionId, codec: PayloadCodec[W]
  )(using ExecutionContext): Consequence[WorkflowIssuedWorkDataStorePersistence[W]] =
    bindForKindC(store, configuration, component, completionOperation, definitions,
      instanceCollection, continuationCollection, codec, WorkflowIssuedContinuationKind.WorkOrder)

  private[workflow] def bindForKindC[W](store: DataStore, configuration: Configuration, component: ComponentId,
    completionOperation: StateMachineOperationIdentity,
    definitions: Vector[DefinitionBinding], instanceCollection: DataStore.CollectionId,
    continuationCollection: DataStore.CollectionId, codec: PayloadCodec[W], kind: WorkflowIssuedContinuationKind
  )(using ExecutionContext): Consequence[WorkflowIssuedWorkDataStorePersistence[W]] =
    if (component == null || completionOperation == null ||
        completionOperation.service == null || completionOperation.service.trim.isEmpty ||
        completionOperation.operation == null || completionOperation.operation.trim.isEmpty ||
        codec == null || codec.typeIdentity == null ||
        codec.typeIdentity.trim.isEmpty || configuration == null || kind == null)
      Consequence.configurationInvalid("Issued WorkOrder binding is incomplete")
    else for {
      _ <- configuration.delivery match {
        case _: WorkflowInstancePersistence.DeliveryConfiguration.SameStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Issued WorkOrder publication requires same-store delivery")
      }
      _ <- (instanceCollection, continuationCollection) match {
        case (a: DataStore.CollectionId.EntityStore, b: DataStore.CollectionId.EntityStore)
            if a.collectionName != b.collectionName => Consequence.unit
        case _ => Consequence.configurationInvalid("Issued work requires distinct explicit EntityStore collections")
      }
      _ <- store match {
        case _: EntityConditionalTransitionDataStore => Consequence.unit
        case _ => Consequence.configurationInvalid("Issued work requires canonical compound transition capability")
      }
      instances <- WorkflowInstanceDataStorePersistence.bindC(store, configuration, definitions, instanceCollection)
      continuations <- WorkflowContinuationDataStorePersistence.bindC(store, configuration, continuationCollection)
    } yield new WorkflowIssuedWorkDataStorePersistence(store, configuration, component, completionOperation,
      instances, continuations, continuationCollection, codec, kind)

  private[workflow] def requestC[W](issued: WorkflowInteraction[W, Nothing],
    kind: WorkflowIssuedContinuationKind = WorkflowIssuedContinuationKind.WorkOrder):
      Consequence[ContinuationRequest[W]] =
    if (issued == null) Consequence.stateConflict("Issued WorkOrder is missing")
    else issued.current match {
      case order: WorkflowContinuation.WorkOrder[?]
          if kind == WorkflowIssuedContinuationKind.WorkOrder && order.request != null =>
        Consequence.success(order.asInstanceOf[WorkflowContinuation.WorkOrder[W]].request)
      case decision: WorkflowContinuation.Decision[?]
          if kind == WorkflowIssuedContinuationKind.Decision && decision.request != null &&
            decision.request.resultType != null && decision.request.resultType.nonEmpty =>
        Consequence.success(decision.asInstanceOf[WorkflowContinuation.Decision[W]].request)
      case _ => Consequence.stateConflict("Issued work must be a WorkOrder")
    }

  private[workflow] def matchingC[W](instance: InstanceRecord, continuation: Continuation,
    issued: WorkflowInteraction[W, Nothing], component: ComponentId,
    completionOperation: StateMachineOperationIdentity,
    kind: WorkflowIssuedContinuationKind = WorkflowIssuedContinuationKind.WorkOrder
  ): Consequence[Unit] =
    for {
      request <- requestC(issued, kind)
      expectedhandle = WorkflowHandle(component, instance.definition.workflowIdentity,
        instance.definition.workflowRevision, instance.identity)
      required = continuation.requiredOperation
      _ <- if (issued.handle == expectedhandle &&
          request.completionOperation == completionOperation &&
          request.runId == continuation.runId &&
          request.continuationId == continuation.continuationId &&
          request.expectedRevision == continuation.expectedRevision &&
          request.requiredOperation == required.identity &&
          request.operation == required.operation &&
          request.input.map(_.typeIdentity) == required.inputType.map(_.value) &&
          request.resultType == required.resultType &&
          request.context != null && WorkflowProtocolV1.sameContextScope(request.context.snapshot, continuation.context.snapshot) &&
          request.completion.identity == required.metadata.completionContract.identity &&
          request.evidence.identity == required.metadata.evidenceContract.identity)
        Consequence.unit
      else Consequence.stateConflict("Issued WorkOrder differs from instance or Continuation facts")
    } yield ()

  /** Initial publication resolves the actual adjacent suspension once. Stored
    * reads do not reconstruct this boundary from historical annotations.
    */
  private[workflow] def _publication_c[W](instance: InstanceRecord, continuation: Continuation,
      issued: WorkflowInteraction[W, Nothing], component: ComponentId,
      completionoperation: StateMachineOperationIdentity, kind: WorkflowIssuedContinuationKind): Consequence[Unit] =
    for {
      _ <- matchingC(instance, continuation, issued, component, completionoperation, kind)
      metadata = continuation.requiredOperation.metadata
      _ <- if (instance.suspension.exists(boundary =>
          boundary.contextSnapshot.value == continuation.context.snapshot.workflowRevision &&
          boundary.completion.value == metadata.completionContract.identity &&
          boundary.evidence.value == metadata.evidenceContract.identity))
        Consequence.unit
      else Consequence.stateConflict("Issued work does not resolve the actual suspension contracts")
    } yield ()

  /** Serialize once; application meaning is not certified by a duplicate round trip. */
  private[workflow] def encodeC[W](issued: WorkflowInteraction[W, Nothing], codec: PayloadCodec[W],
    kind: WorkflowIssuedContinuationKind = WorkflowIssuedContinuationKind.WorkOrder):
      Consequence[String] =
    safelyC {
      for {
        _ <- requestC(issued, kind)
        bytes <- encodeForKindC(issued, codec, kind)
      } yield bytes
    }

  private[workflow] def decodeC[W](bytes: String, codec: PayloadCodec[W], kind: WorkflowIssuedContinuationKind):
      Consequence[WorkflowInteraction[W, Nothing]] =
    safelyC { decodeForKindC(bytes, codec, kind) }

  private def encodeForKindC[W](issued: WorkflowInteraction[W, Nothing], codec: PayloadCodec[W],
    kind: WorkflowIssuedContinuationKind): Consequence[String] = kind match {
    case WorkflowIssuedContinuationKind.WorkOrder => WorkflowWorkOrderJsonV1.encodeC(issued, codec)
    case WorkflowIssuedContinuationKind.Decision => WorkflowDecisionJsonV1.encodeC(issued, codec)
  }

  private def decodeForKindC[W](bytes: String, codec: PayloadCodec[W],
    kind: WorkflowIssuedContinuationKind): Consequence[WorkflowInteraction[W, Nothing]] = kind match {
    case WorkflowIssuedContinuationKind.WorkOrder => WorkflowWorkOrderJsonV1.decodeC(bytes, codec)
    case WorkflowIssuedContinuationKind.Decision => WorkflowDecisionJsonV1.decodeC(bytes, codec)
  }

  private def safelyC[A](operation: => Consequence[A]): Consequence[A] =
    try operation catch {
      case NonFatal(_) => Consequence.stateConflict("Issued WorkOrder application codec failed")
    }
}
