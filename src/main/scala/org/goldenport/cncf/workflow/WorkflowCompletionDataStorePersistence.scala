package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.{Record as DataRecord}
import org.simplemodeling.model.datatype.EntityRevision

/*
 * @since   Sep. 29, 2026
 * @version Oct.  5, 2026
 */
/** Borrowed two-record completion. No closing program, external effect, UoW,
  * admission payload or production claim/Operation service is inferred.
  */
final class WorkflowCompletionDataStorePersistence[W, R] private (
  store: DataStore & EntityConditionalUpdateDataStore,
  configuration: Configuration,
  instances: WorkflowInstanceDataStorePersistence,
  continuations: WorkflowContinuationDataStorePersistence,
  issued: WorkflowIssuedWorkDataStorePersistence[W],
  instanceCollection: DataStore.CollectionId,
  continuationCollection: DataStore.CollectionId,
  resultCodec: PayloadCodec[R],
  kind: WorkflowIssuedContinuationKind
)(using ExecutionContext) {
  import WorkflowCompletionDataStorePersistence.*
  import WorkflowInstanceDataStorePersistence.{configurationField, configurationJson, storageText, storageJsonC}
  private val policyBytes = storageText(configurationJson(configuration).noSpaces)

  /** The host owns this intent and token; the submitted result grants no authority. */
  def completeC(intent: Intent[R], owner: DataStoreComponentOwner): Consequence[PersistedCompletion[R]] =
    prepareCompletionC(intent, owner).flatMap(_.commitC())

  /** Internal managed storage path. Existing typed result/claim/history
    * admission is retained; event classification belongs only to the host.
    * The returned storage projection is not an active-UoW completion permit.
    */
  private[cncf] def completeHostEventsC(intent: Intent[R], owner: DataStoreComponentOwner,
      space: DataStoreSpace, records: Vector[org.goldenport.cncf.event.EventRecord]): Consequence[PersistedCompletion[R]] =
    prepareCompletionC(intent, owner).flatMap(_.commitHostEventsC(space, records))

  /** Admission/reads only. No completed claim, history or result is published. */
  def prepareCompletionC(intent: Intent[R], owner: DataStoreComponentOwner):
      Consequence[WorkflowPreparedDataStoreTransition[PersistedCompletion[R]]] =
    prepareCompletionWithClosingResultC(intent, owner, None)

  /** Internal joint binding: actual program result and local effects are
    * consumed from the same immutable preparation, never from caller flags.
    */
  private[cncf] def prepareClosingHostParticipantC(intent: Intent[R], owner: DataStoreComponentOwner,
      space: DataStoreSpace, records: Vector[org.goldenport.cncf.event.EventRecord],
      closing: WorkflowClosingProgramPreparation, callbacks: Vector[() => Consequence[Unit]],
      cleanup: () => Consequence[Unit], delivery: Vector[org.goldenport.cncf.event.EventRecord] => Consequence[Unit]):
      Consequence[ManagedCompletionParticipant[PersistedCompletion[R]]] =
    if (closing == null) Consequence.configurationInvalid("Prepared closing program is missing")
    else for {
      prepared <- prepareCompletionWithClosingResultC(intent, owner, Some(closing.result))
      local <- closing.localIntentsC
      participant <- prepared.hostParticipantC(space, records, callbacks, cleanup, delivery, local._1, local._2)
    } yield participant

  /** Single-user host execution: Available becomes Completed in the joint
    * native commit. No claim, token, release or claimed-projection route.
    * This internal entry is not a public activation or a request-controlled route.
    */
  private[cncf] def executeSelectedClosingHostC(current: InstanceRecord, entry: HistoryEntry,
      submitted: ContinuationResult[R], program: ExecUowM[ActionExecution],
      expectedClosingType: StateMachineResultTypeReference, owner: DataStoreComponentOwner,
      space: DataStoreSpace, records: Vector[org.goldenport.cncf.event.EventRecord],
      delivery: Vector[org.goldenport.cncf.event.EventRecord] => Consequence[Unit]):
      Consequence[PersistedCompletion[R]] =
    if (current == null || entry == null || submitted == null || program == null ||
        expectedClosingType == null || expectedClosingType.value == null || expectedClosingType.value.trim.isEmpty ||
        owner == null || space == null || records == null || records.exists(_ == null) || delivery == null ||
        entry.suspension == null || entry.suspension.nonEmpty)
      Consequence.configurationInvalid("Managed selected completion requires complete host bindings")
    else for {
      rootStore <- space.dataStore(instanceCollection)
      continuationStore <- space.dataStore(continuationCollection)
      _ <- if ((rootStore eq store) && (continuationStore eq store)) Consequence.unit
        else Consequence.configurationInvalid("Managed completion requires the admitted same physical provider")
      selected <- selectCompletionC(current, current.revision, entry, submitted)
      _ <- if (owner.name == selected.work.handle.componentIdentity.name) Consequence.unit
        else Consequence.configurationInvalid("Managed completion owner differs from the admitted Component")
      _ <- if (selected.source._1.status == ContinuationRuntimePersistence.Status.Available &&
          selected.source._1.claimId.isEmpty) Consequence.unit
        else Consequence.stateConflict("Single-user completion requires an unclaimed Available Continuation")
      result <- executeSelectedC(selected, program,
        expectedClosingType, owner, space, records, delivery)
    } yield result

  private def executeSelectedC(selected: CompletionSelection, program: ExecUowM[ActionExecution],
      expectedClosingType: StateMachineResultTypeReference, owner: DataStoreComponentOwner,
      space: DataStoreSpace, records: Vector[org.goldenport.cncf.event.EventRecord],
      delivery: Vector[org.goldenport.cncf.event.EventRecord] => Consequence[Unit]):
      Consequence[PersistedCompletion[R]] = {
    try {
      for {
        closing <- WorkflowClosingProgramPreparation.prepareC(program)
        _ <- if (closing.result.typeReference == expectedClosingType) Consequence.unit
          else Consequence.stateConflict("Actual closing result differs from the host-declared result contract")
        prepared <- _prepare_selected_completion_c(selected, owner, Some(closing.result))
        local <- closing.localIntentsC
        participant <- prepared.hostParticipantC(space, records, Vector.empty,
          () => Consequence.unit, delivery, local._1, local._2)
        completed <- {
          val uow = new ManagedCompletionUnitOfWork(summon[ExecutionContext], participant)
          uow.commit().flatMap { _ => uow.storageOutcome match {
            case Some(ManagedCompletionParticipant.Outcome.Verified(value)) => Consequence.success(value)
            case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate(
              "Managed completion lacks verified retained completion")
          }}
        }
      } yield completed
    } catch {
      case NonFatal(e) => Consequence.Failure(Conclusion.from(e))
      case original: Throwable =>
        if (original.isInstanceOf[InterruptedException]) Thread.currentThread().interrupt()
        throw original
    }
  }

  private def prepareCompletionWithClosingResultC(intent: Intent[R], owner: DataStoreComponentOwner,
      closingResult: Option[StateMachineOperationResult]):
      Consequence[WorkflowPreparedDataStoreTransition[PersistedCompletion[R]]] =
    if (intent == null || intent.current == null || intent.entry == null ||
        intent.result == null || intent.entry.suspension == null ||
        intent.claimId == null || intent.claimId.trim.isEmpty)
      Consequence.stateConflict("Completion host intent or claim token is missing")
    else for {
      selected <- selectCompletionC(intent.current, intent.expectedRevision, intent.entry, intent.result)
      _ <- if (selected.source._1.status == ContinuationRuntimePersistence.Status.Claimed &&
          selected.source._1.claimId.contains(intent.claimId)) Consequence.unit
        else Consequence.stateConflict("Completion claim token or status differs")
      prepared <- _prepare_selected_completion_c(selected, owner, closingResult)
    } yield prepared

  /** Constructed only at the natural stored-source/result boundary. No public
    * constructor, management hash or token is needed for semantic selection.
    */
  private final class CompletionSelection(val current: InstanceRecord, val next: InstanceRecord,
      val work: WorkflowInteraction[W, Nothing],
      val source: (ContinuationRuntimePersistence.Record, EntityRevision),
      val result: ContinuationResult[R])

  private def selectCompletionC(suppliedCurrent: InstanceRecord, expectedRevision: InstanceRevision,
      entry: HistoryEntry, submitted: ContinuationResult[R]): Consequence[CompletionSelection] =
    for {
      _ <- WorkflowCompletionRecordJsonV1.encodeC(
        WorkflowCompletionRecordJsonV1.Record(entry.revision, submitted), resultCodec).map(_ => ())
      loaded <- instances.load(configuration, suppliedCurrent.identity)
      current <- requiredC(loaded, "Completion instance is unavailable")
      actual <- WorkflowInstanceRecordJsonV1.encodeC(current)
      supplied <- WorkflowInstanceRecordJsonV1.encodeC(suppliedCurrent)
      _ <- if (actual == supplied && current.lifecycle == Lifecycle.Active &&
          entry.suspension.isEmpty) Consequence.unit
        else Consequence.stateConflict("Completion requires the exact active suspended instance and unsuspended host entry")
      work <- issued.loadC(submitted.continuationId).flatMap(requiredC(_, "Completion WorkOrder was not atomically issued"))
      request <- WorkflowIssuedWorkDataStorePersistence.requestC(work, kind)
      _ <- matchingBoundaryC(current, request)
      _ <- admitResultC(work, submitted).map(_ => ())
      source <- continuations.loadStoredC(submitted.continuationId).flatMap(requiredC(_, "Completion Continuation is unavailable"))
      next <- current.appendC(expectedRevision, entry)
    } yield new CompletionSelection(current, next, work, source, submitted)

  /** Mechanical storage preparation consumes the admitted selection, rather
    * than loading and semantically admitting the source a second time.
    */
  private def _prepare_selected_completion_c(selected: CompletionSelection, owner: DataStoreComponentOwner,
      closingresult: Option[StateMachineOperationResult]):
      Consequence[WorkflowPreparedDataStoreTransition[PersistedCompletion[R]]] = {
    val current = selected.current
    val next = selected.next
    val source = selected.source
    for {
      resultbytes <- WorkflowCompletionRecordJsonV1.encodeC(
        WorkflowCompletionRecordJsonV1.Record(next.revision, selected.result, closingresult), resultCodec)
      retained <- WorkflowCompletionRecordJsonV1.decodeC(resultbytes, resultCodec)
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(next)
      completed = source._1.copy(status = ContinuationRuntimePersistence.Status.Completed)
      completedbytes <- ContinuationRecordJsonV1.encodeC(completed)
      expected <- EntityRevision.createC(current.revision.value + 1L)
      nextinstancerevision <- expected.nextC
      nextcontinuationrevision <- source._2.nextC
      policy <- DataStoreConditionalValue.from(policyBytes)
      root <- DataStoreConditionalRoot.create(owner, instanceCollection,
        DataStore.StringEntryId(current.identity.value), WorkflowInstanceDataStorePersistence.revisionField,
        Some(expected), Vector(DataStoreConditionalExpectedField(configurationField, policy)),
        DataRecord.dataAuto(WorkflowInstanceDataStorePersistence.recordField -> storageText(encoded)), nextinstancerevision)
      second <- DataStoreConditionalRoot.create(owner, continuationCollection,
        WorkflowContinuationDataStorePersistence.id(selected.result.continuationId),
        WorkflowContinuationDataStorePersistence.revisionField, Some(source._2),
        Vector(DataStoreConditionalExpectedField(configurationField, policy)),
        DataRecord.dataAuto(WorkflowContinuationDataStorePersistence.payloadField -> storageText(completedbytes),
          payloadField -> storageText(resultbytes)), nextcontinuationrevision)
      plan <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Update(second),
        correlation = DataStoreConditionalCorrelation("workflow-instance-completion"))
    } yield WorkflowPreparedDataStoreTransition.create(store, plan, changed => changed match {
        case DataStoreConditionalTransitionResult.Transitioned(rootvalue, secondvalue) =>
          (for {
            instance <- instances.decodeC(rootvalue, current.identity)
            bytes <- WorkflowInstanceRecordJsonV1.encodeC(instance)
            continuation <- continuations.decodeStoredC(secondvalue, selected.result.continuationId)
            _ <- if (bytes == encoded && continuation._1 == completed &&
                continuation._2 == nextcontinuationrevision &&
                secondvalue.getString(payloadField).contains(storageText(resultbytes))) Consequence.unit
              else Consequence.stateConflict("Completion authoritative readback differs")
          } yield PersistedCompletion(instance, continuation._1, retained)).recoverWith { _ =>
              DataStoreConditionalTransitionFailure.transactionIndeterminate(
                "Completion committed but authoritative readback could not be admitted")
            }
        case _: DataStoreConditionalTransitionResult.NotMatched =>
          Consequence.stateConflict("Completion instance guard was consumed")
    })
  }

  /** Recovery is a query only: it neither claims nor replays semantic/effect work. */
  def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowCompletionRecordJsonV1.Record[R]]] =
    if (identity == null || identity.value == null || identity.value.trim.isEmpty)
      Consequence.stateConflict("Completion identity is missing")
    else store.load(continuationCollection, WorkflowContinuationDataStorePersistence.id(identity)).flatMap {
      case None => Consequence.success(None)
      case Some(stored) => continuations.decodeStoredC(stored, identity).flatMap { decoded =>
        if (decoded._1.status != ContinuationRuntimePersistence.Status.Completed) {
          if (stored.getString(payloadField).isEmpty) Consequence.success(None)
          else Consequence.stateConflict("Retained completion exists without Completed Continuation")
        } else for {
          payload <- requiredC(stored.getString(payloadField), "Completed Continuation lacks retained result")
          raw <- storageJsonC(payload)
          record <- WorkflowCompletionRecordJsonV1.decodeC(raw, resultCodec)
          canonical <- WorkflowCompletionRecordJsonV1.encodeC(record, resultCodec)
          _ <- if (raw == canonical && record.result.continuationId == identity) Consequence.unit
            else Consequence.stateConflict("Retained completion identity or canonical bytes differ")
          work <- issued.loadC(identity).flatMap(requiredC(_, "Completed WorkOrder is unavailable"))
          request <- WorkflowIssuedWorkDataStorePersistence.requestC(work, kind)
          _ <- admitResultC(work, record.result).map(_ => ())
          instance <- instances.load(configuration, work.handle.instanceIdentity)
            .flatMap(requiredC(_, "Completed instance is unavailable"))
          index = instance.history.indexWhere(_.revision == record.instanceRevision)
          _ <- if (index > 0 && instance.history(index).suspension.isEmpty &&
              boundaryMatches(instance.history(index - 1).suspension, request)) Consequence.unit
            else Consequence.stateConflict("Retained completion lacks adjacent suspension/completion history")
        } yield Some(record)
      }
    }

  private def matchingBoundaryC(instance: InstanceRecord, request: ContinuationRequest[W]): Consequence[Unit] =
    if (boundaryMatches(instance.suspension, request)) Consequence.unit
    else Consequence.stateConflict("Completion does not match the current suspension boundary")

  private def admitResultC(work: WorkflowInteraction[W, Nothing], submitted: ContinuationResult[R]):
      Consequence[StateMachineOperationResult] = kind match {
    case WorkflowIssuedContinuationKind.WorkOrder => WorkflowProtocolV1.admitResultC(work, submitted)
    case WorkflowIssuedContinuationKind.Decision => WorkflowProtocolV1.admitDecisionResultC(work, submitted)
  }

  private def boundaryMatches(boundary: Option[SuspensionBoundary], request: ContinuationRequest[W]): Boolean =
    boundary.exists(value => value.continuationIdentity == InstanceContinuationIdentity(request.continuationId.value) &&
      value.contextSnapshot.value == request.context.snapshot.workflowRevision &&
      value.completion.value == request.completion.identity && value.evidence.value == request.evidence.identity)

  private def requiredC[A](value: Option[A], message: String): Consequence[A] =
    value.map(Consequence.success).getOrElse(Consequence.stateConflict(message))
}

object WorkflowCompletionDataStorePersistence {
  private[workflow] val payloadField = "completionPayload"
  final case class Intent[R](current: InstanceRecord, expectedRevision: InstanceRevision,
    entry: HistoryEntry, claimId: String, result: ContinuationResult[R])
  final case class PersistedCompletion[R](instance: InstanceRecord,
    continuation: ContinuationRuntimePersistence.Record, retained: WorkflowCompletionRecordJsonV1.Record[R])

  def bindC[W, R](store: DataStore, configuration: Configuration, component: ComponentId,
    completionOperation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instanceCollection: DataStore.CollectionId, continuationCollection: DataStore.CollectionId,
    inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R]
  )(using ExecutionContext): Consequence[WorkflowCompletionDataStorePersistence[W, R]] =
    bindForKindC(store, configuration, component, completionOperation, definitions,
      instanceCollection, continuationCollection, inputCodec, resultCodec, WorkflowIssuedContinuationKind.WorkOrder)

  private[workflow] def bindForKindC[W, R](store: DataStore, configuration: Configuration, component: ComponentId,
    completionOperation: StateMachineOperationIdentity, definitions: Vector[DefinitionBinding],
    instanceCollection: DataStore.CollectionId, continuationCollection: DataStore.CollectionId,
    inputCodec: PayloadCodec[W], resultCodec: PayloadCodec[R], kind: WorkflowIssuedContinuationKind
  )(using ExecutionContext): Consequence[WorkflowCompletionDataStorePersistence[W, R]] =
    if (resultCodec == null || resultCodec.typeIdentity == null || resultCodec.typeIdentity.trim.isEmpty || kind == null)
      Consequence.configurationInvalid("Completion result codec is incomplete")
    else for {
      compound <- store match {
        case value: EntityConditionalUpdateDataStore => Consequence.success(value.asInstanceOf[DataStore & EntityConditionalUpdateDataStore])
        case _ => Consequence.configurationInvalid("Completion requires canonical guarded existing-successor update capability")
      }
      issued <- WorkflowIssuedWorkDataStorePersistence.bindForKindC(store, configuration, component, completionOperation,
        definitions, instanceCollection, continuationCollection, inputCodec, kind)
      instances <- WorkflowInstanceDataStorePersistence.bindC(store, configuration, definitions, instanceCollection)
      continuations <- WorkflowContinuationDataStorePersistence.bindC(store, configuration, continuationCollection)
    } yield new WorkflowCompletionDataStorePersistence(compound, configuration, instances, continuations,
      issued, instanceCollection, continuationCollection, resultCodec, kind)
}
