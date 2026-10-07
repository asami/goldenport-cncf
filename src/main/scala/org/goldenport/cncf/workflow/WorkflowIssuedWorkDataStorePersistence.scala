package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
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

  /** Immutable retry only. Refuse to retrofit a separately published Continuation. */
  def putIfAbsentC(issued: WorkflowInteraction[W, Nothing]):
      Consequence[WorkflowInteraction[W, Nothing]] =
    for {
      bytes <- encodeExactC(issued, codec, kind)
      request <- requestC(issued, kind)
      existing <- loadC(request.continuationId)
      admitted <- existing match {
        case Some(value) => encodeExactC(value, codec, kind).flatMap { actual =>
          if (actual == bytes) Consequence.success(value)
          else Consequence.stateConflict("A different WorkOrder is already issued")
        }
        case None => Consequence.stateConflict("Initial WorkOrder issue requires atomic suspension")
      }
    } yield admitted

  def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowInteraction[W, Nothing]]] =
    if (identity == null || identity.value == null || identity.value.trim.isEmpty)
      Consequence.stateConflict("Issued WorkOrder requires a Continuation identity")
    else store.load(continuationCollection, WorkflowContinuationDataStorePersistence.id(identity)).flatMap {
      case None => Consequence.success(None)
      case Some(stored) => for {
        continuation <- continuations.decodeStoredC(stored, identity)
        payload <- stored.getString(payloadField).map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Atomically issued WorkOrder payload is missing"))
        raw <- storageJsonC(payload)
        issued <- decodeExactC(raw, codec, kind)
        loaded <- instances.load(configuration, issued.handle.instanceIdentity)
        instance <- loaded.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Issued WorkOrder instance is unavailable"))
        _ <- matchingC(instance, continuation._1.continuation, issued, component, completionOperation, kind)
      } yield Some(issued)
    }
}

object WorkflowIssuedWorkDataStorePersistence {
  private[workflow] val payloadField = "issuedWorkPayload"

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
      expectedHandle <- WorkflowHandle.fromRecordC(component, instance)
      request <- requestC(issued, kind)
      required = continuation.requiredOperation
      _ <- if (issued.handle == expectedHandle &&
          request.completionOperation == completionOperation &&
          request.runId == continuation.runId &&
          request.continuationId == continuation.continuationId &&
          request.expectedRevision == continuation.expectedRevision &&
          request.requiredOperation == required.identity &&
          request.operation == required.operation &&
          request.input.map(_.typeIdentity) == required.inputType.map(_.value) &&
          request.resultType == required.resultType &&
          request.context == continuation.context &&
          request.completion == required.metadata.completionContract &&
          request.evidence == required.metadata.evidenceContract)
        Consequence.unit
      else Consequence.stateConflict("Issued WorkOrder differs from instance or Continuation facts")
      _ <- if (instance.history.exists(entry => entry.suspension.exists(boundary =>
          boundary.continuationIdentity.value == request.continuationId.value &&
          boundary.expectedRevision == entry.revision &&
          boundary.contextSnapshot.value == request.context.snapshot.workflowRevision &&
          boundary.completion.value == request.completion.identity &&
          boundary.evidence.value == request.evidence.identity)))
        Consequence.unit
      else Consequence.stateConflict("Issued WorkOrder has no matching instance suspension history")
    } yield ()

  /** Application codecs must be exact and must fail before datastore publication. */
  private[workflow] def encodeExactC[W](issued: WorkflowInteraction[W, Nothing], codec: PayloadCodec[W],
    kind: WorkflowIssuedContinuationKind = WorkflowIssuedContinuationKind.WorkOrder):
      Consequence[String] =
    safelyC {
      for {
        _ <- requestC(issued, kind)
        bytes <- encodeForKindC(issued, codec, kind)
        decoded <- decodeForKindC(bytes, codec, kind)
        roundTrip <- encodeForKindC(decoded, codec, kind)
        _ <- if (decoded == issued && roundTrip == bytes) Consequence.unit
          else Consequence.stateConflict("Issued WorkOrder codec is not an exact round-trip")
      } yield bytes
    }

  private def decodeExactC[W](bytes: String, codec: PayloadCodec[W], kind: WorkflowIssuedContinuationKind):
      Consequence[WorkflowInteraction[W, Nothing]] =
    safelyC {
      for {
        decoded <- decodeForKindC(bytes, codec, kind)
        encoded <- encodeExactC(decoded, codec, kind)
        _ <- if (encoded == bytes) Consequence.unit
          else Consequence.stateConflict("Persisted issued WorkOrder bytes are not canonical")
      } yield decoded
    }

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
