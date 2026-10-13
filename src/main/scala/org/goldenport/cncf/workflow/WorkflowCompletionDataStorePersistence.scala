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
 * @version Oct. 12, 2026
 */
/** Borrowed two-record completion. No closing program, external effect, UoW,
  * admission payload or production claim/Operation service is inferred.
  */
final class WorkflowCompletionDataStorePersistence[W, R] private (
  store: DataStore & EntityConditionalUpdateDataStore,
  configuration: Configuration,
  component: ComponentId,
  completionoperation: StateMachineOperationIdentity,
  instances: WorkflowInstanceDataStorePersistence,
  continuations: WorkflowContinuationDataStorePersistence,
  issued: WorkflowIssuedWorkDataStorePersistence[W],
  instancecollection: DataStore.CollectionId,
  continuationcollection: DataStore.CollectionId,
  resultcodec: PayloadCodec[R],
  kind: WorkflowIssuedContinuationKind
)(using ExecutionContext) {
  import WorkflowCompletionDataStorePersistence.*
  import WorkflowInstanceDataStorePersistence.{storageText, storageJsonC}

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
    _prepare_completion_with_closing_result_c(intent, owner, None)

  /** Internal joint binding: actual program result and local effects are
    * consumed from the same immutable preparation, never from caller flags.
    */
  private[cncf] def prepareClosingHostParticipantC(intent: Intent[R], owner: DataStoreComponentOwner,
      space: DataStoreSpace, records: Vector[org.goldenport.cncf.event.EventRecord],
      closing: WorkflowClosingProgramPreparation, callbacks: Vector[() => Consequence[Unit]],
      cleanup: () => Consequence[Unit], delivery: Vector[org.goldenport.cncf.event.EventRecord] => Consequence[Unit]):
      Consequence[ManagedCompletionParticipant[PersistedCompletion[R]]] = {
    if (closing == null) Consequence.configurationInvalid("Prepared closing program is missing")
    else for {
      prepared <- _prepare_completion_with_closing_result_c(intent, owner, Some(closing.result))
      local <- closing.localIntentsC
      participant <- prepared.hostParticipantC(space, records, callbacks, cleanup, delivery, local._1, local._2)
    } yield participant

  }
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
    executeSelectedClosingHostC(current, entry, submitted, program, expectedClosingType,
      owner, space, () => Consequence.success(records), delivery)

  /** Defer event preparation until native source, result, owner and status are selected.
    * The eager-vector entry remains compatible; neither entry creates an admission mark.
    */
  private[cncf] def executeSelectedClosingHostC(current: InstanceRecord, entry: HistoryEntry,
      submitted: ContinuationResult[R], program: ExecUowM[ActionExecution],
      expectedClosingType: StateMachineResultTypeReference, owner: DataStoreComponentOwner,
      space: DataStoreSpace, prepareEvents: () => Consequence[Vector[org.goldenport.cncf.event.EventRecord]],
      delivery: Vector[org.goldenport.cncf.event.EventRecord] => Consequence[Unit]):
      Consequence[PersistedCompletion[R]] = {
    if (current == null || entry == null || submitted == null || program == null ||
        expectedClosingType == null || expectedClosingType.value == null || expectedClosingType.value.trim.isEmpty ||
        owner == null || space == null || prepareEvents == null || delivery == null ||
        entry.suspension == null || entry.suspension.nonEmpty)
      Consequence.configurationInvalid("Managed selected completion requires complete host bindings")
    else for {
      rootstore <- space.dataStore(instancecollection)
      continuationstore <- space.dataStore(continuationcollection)
      _ <- if ((rootstore eq store) && (continuationstore eq store)) Consequence.unit
        else Consequence.configurationInvalid("Managed completion requires the admitted same physical provider")
      selected <- _select_completion_c(current, current.revision, entry, submitted)
      _ <- if (owner.name == selected.work.handle.componentIdentity.name) Consequence.unit
        else Consequence.configurationInvalid("Managed completion owner differs from the admitted Component")
      _ <- if (selected.source._1.status == ContinuationRuntimePersistence.Status.Available &&
          selected.source._1.claimId.isEmpty) Consequence.unit
        else Consequence.stateConflict("Single-user completion requires an unclaimed Available Continuation")
      records <- prepareEvents()
      _ <- if (records != null && !records.contains(null)) Consequence.unit
        else Consequence.configurationInvalid("Managed event batch is not representable")
      result <- _execute_selected_c(selected, program,
        expectedClosingType, owner, space, records, delivery)
    } yield result
  }

  private def _execute_selected_c(selected: CompletionSelection, program: ExecUowM[ActionExecution],
      expectedclosingtype: StateMachineResultTypeReference, owner: DataStoreComponentOwner,
      space: DataStoreSpace, records: Vector[org.goldenport.cncf.event.EventRecord],
      delivery: Vector[org.goldenport.cncf.event.EventRecord] => Consequence[Unit]):
      Consequence[PersistedCompletion[R]] = {
    try {
      for {
        closing <- WorkflowClosingProgramPreparation.prepareC(program)
        _ <- if (closing.result.typeReference == expectedclosingtype) Consequence.unit
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

  private def _prepare_completion_with_closing_result_c(intent: Intent[R], owner: DataStoreComponentOwner,
      closingresult: Option[StateMachineOperationResult]):
      Consequence[WorkflowPreparedDataStoreTransition[PersistedCompletion[R]]] = {
    if (intent == null || intent.current == null || intent.entry == null ||
        intent.result == null || intent.entry.suspension == null ||
        intent.claimId == null || intent.claimId.trim.isEmpty)
      Consequence.stateConflict("Completion host intent or claim token is missing")
    else for {
      selected <- _select_completion_c(intent.current, intent.expectedRevision, intent.entry, intent.result)
      _ <- if (selected.source._1.status == ContinuationRuntimePersistence.Status.Claimed &&
          selected.source._1.claimId.contains(intent.claimId)) Consequence.unit
        else Consequence.stateConflict("Completion claim token or status differs")
      prepared <- _prepare_selected_completion_c(selected, owner, closingresult)
    } yield prepared

  }
  /** Constructed only at the natural stored-source/result boundary. No public
    * constructor, management hash or token is needed for semantic selection.
    */
  private final class CompletionSelection(val current: InstanceRecord, val next: InstanceRecord,
      val work: WorkflowInteraction[W, Nothing],
      val source: (ContinuationRuntimePersistence.Record, EntityRevision),
      val result: ContinuationResult[R])

  private def _select_completion_c(suppliedcurrent: InstanceRecord, expectedrevision: InstanceRevision,
      entry: HistoryEntry, submitted: ContinuationResult[R]): Consequence[CompletionSelection] = {
    for {
      loaded <- instances.load(configuration, suppliedcurrent.identity)
      current <- _required_c(loaded, "Completion instance is unavailable")
      _ <- if (entry.suspension.isEmpty) Consequence.unit
        else Consequence.stateConflict("Completion requires an unsuspended host entry")
      next <- current._append_c_unchecked(expectedrevision, entry)
      selected <- issued._load_selected_c(submitted.continuationId, current)
        .flatMap(_required_c(_, "Completion WorkOrder was not atomically issued"))
      work = selected._1
      source = selected._2
      request <- WorkflowIssuedWorkDataStorePersistence.requestC(work, kind)
      _ <- _matching_boundary_c(current, request)
      _ <- _admit_native_result_c(work, request, source._1.continuation, submitted).map(_ => ())
    } yield new CompletionSelection(current, next, work, source, submitted)

  }
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
        WorkflowCompletionRecordJsonV1.Record(next.revision, selected.result, closingresult), resultcodec)
      encoded = WorkflowInstanceRecordJsonV1._encode_unchecked(next)
      completed = source._1.copy(status = ContinuationRuntimePersistence.Status.Completed)
      completedbytes <- ContinuationRecordJsonV1.encodeC(completed)
      expected <- EntityRevision.createC(current.revision.value + 1L)
      nextinstancerevision <- expected.nextC
      nextcontinuationrevision <- source._2.nextC
      root <- DataStoreConditionalRoot.create(owner, instancecollection,
        DataStore.StringEntryId(current.identity.value), WorkflowInstanceDataStorePersistence.revisionField,
        Some(expected), Vector.empty,
        DataRecord.dataAuto(WorkflowInstanceDataStorePersistence.recordField -> storageText(encoded)), nextinstancerevision)
      second <- DataStoreConditionalRoot.create(owner, continuationcollection,
        WorkflowContinuationDataStorePersistence.id(selected.result.continuationId),
        WorkflowContinuationDataStorePersistence.revisionField, Some(source._2),
        Vector.empty,
        DataRecord.dataAuto(WorkflowContinuationDataStorePersistence.payloadField -> storageText(completedbytes),
          payloadField -> storageText(resultbytes)), nextcontinuationrevision)
      plan <- DataStoreConditionalTransitionPlan.create(root, DataStoreConditionalSuccessor.Update(second),
        correlation = DataStoreConditionalCorrelation("workflow-instance-completion"))
    } yield WorkflowPreparedDataStoreTransition.create(store, plan, changed => changed match {
        case DataStoreConditionalTransitionResult.Transitioned(rootvalue, secondvalue) =>
          (for {
            instance <- instances.decodeC(rootvalue, current.identity)
            continuation <- continuations.decodeStoredC(secondvalue, selected.result.continuationId)
            payload <- _required_c(secondvalue.getString(payloadField), "Completion authoritative result is unavailable")
            raw <- storageJsonC(payload)
            retained <- WorkflowCompletionRecordJsonV1.decodeC(raw, resultcodec)
            // Consume the guarded provider's committed representation. The
            // selected plan was already admitted before the atomic write;
            // its copies are not a second post-commit certificate.
          } yield PersistedCompletion(instance, continuation._1, retained)).recoverWith { _ =>
              DataStoreConditionalTransitionFailure.transactionIndeterminate(
                "Completion committed but authoritative readback could not be admitted")
            }
        case _: DataStoreConditionalTransitionResult.NotMatched =>
          Consequence.stateConflict("Completion instance guard was consumed")
    })
  }

  /** Query stored facts only, not a history-based joint-publication certificate.
    * Issued references select actual scope; input/evidence admission is not replayed.
    */
  def loadC(identity: ContinuationIdentity): Consequence[Option[WorkflowCompletionRecordJsonV1.Record[R]]] = {
    if (identity == null || identity.value == null || identity.value.trim.isEmpty)
      Consequence.stateConflict("Completion identity is missing")
    else store.load(continuationcollection, WorkflowContinuationDataStorePersistence.id(identity)).flatMap {
      case None => Consequence.success(None)
      case Some(stored) => continuations.decodeStoredC(stored, identity).flatMap { decoded =>
        if (decoded._1.status != ContinuationRuntimePersistence.Status.Completed) {
          if (stored.getString(payloadField).isEmpty) Consequence.success(None)
          else Consequence.stateConflict("Retained completion exists without Completed Continuation")
        } else for {
          payload <- _required_c(stored.getString(payloadField), "Completed Continuation lacks retained result")
          raw <- storageJsonC(payload)
          record <- WorkflowCompletionRecordJsonV1.decodeC(raw, resultcodec)
          _ <- if (record.result.continuationId == identity) Consequence.unit
            else Consequence.stateConflict("Retained completion targets another Continuation")
          issuepayload <- _required_c(stored.getString(WorkflowIssuedWorkDataStorePersistence.payloadField),
            "Completed issued reference is unavailable")
          issuebytes <- storageJsonC(issuepayload)
          handle <- WorkflowIssuedWorkDataStorePersistence._reference_c(issuebytes, kind, component, completionoperation)
          _ <- if (record.result.handle == handle) Consequence.unit
            else Consequence.stateConflict("Retained result targets another issued instance")
          instance <- instances.load(configuration, handle.instanceIdentity)
            .flatMap(_required_c(_, "Completed instance is unavailable"))
          _ <- if (instance.definition.workflowIdentity == handle.workflowIdentity &&
              instance.definition.workflowRevision == handle.workflowRevision) Consequence.unit
            else Consequence.stateConflict("Retained result Workflow reference cannot be resolved")
          continuation = decoded._1.continuation
          _ <- if (record.result.runId == continuation.runId &&
              record.result.expectedRevision == continuation.expectedRevision &&
              WorkflowProtocolV1.sameContextScope(record.result.contextSnapshot, continuation.context.snapshot) &&
              continuation.requiredOperation.resultType.contains(StateMachineResultTypeReference(record.result.result.typeIdentity)))
            Consequence.unit
          else Consequence.stateConflict("Retained result is outside its native Continuation scope or type")
        } yield Some(record)
      }
    }
  }

  private def _matching_boundary_c(instance: InstanceRecord, request: ContinuationRequest[W]): Consequence[Unit] = {
    if (_boundary_matches(instance.suspension, request)) Consequence.unit
    else Consequence.stateConflict("Completion does not match the current suspension boundary")

  }
  /** Native Required Operation owns result conditions. The saved issued copy
    * still owns input/presentation/execution disposition, not a second contract.
    */
  private def _admit_native_result_c(work: WorkflowInteraction[W, Nothing], request: ContinuationRequest[W],
      continuation: Continuation, submitted: ContinuationResult[R]): Consequence[StateMachineOperationResult] = {
    val metadata = continuation.requiredOperation.metadata
    val actualrequest = request.copy(completion = metadata.completionContract, evidence = metadata.evidenceContract)
    work.current match {
      case order: WorkflowContinuation.WorkOrder[?] if kind == WorkflowIssuedContinuationKind.WorkOrder =>
        val selected = work.copy(current = order.asInstanceOf[WorkflowContinuation.WorkOrder[W]].copy(request = actualrequest))
        WorkflowProtocolV1.admitResultC(selected, submitted)
      case decision: WorkflowContinuation.Decision[?] if kind == WorkflowIssuedContinuationKind.Decision =>
        val selected = work.copy(current = decision.asInstanceOf[WorkflowContinuation.Decision[W]].copy(request = actualrequest))
        WorkflowProtocolV1.admitDecisionResultC(selected, submitted)
      case _ => Consequence.stateConflict("Completion requires its selected issued kind")
    }
  }

  private def _boundary_matches(boundary: Option[SuspensionBoundary], request: ContinuationRequest[W]): Boolean =
    boundary.exists(value => value.continuationIdentity == InstanceContinuationIdentity(request.continuationId.value) &&
      value.contextSnapshot.value == request.context.snapshot.workflowRevision &&
      value.completion.value == request.completion.identity && value.evidence.value == request.evidence.identity)

  private def _required_c[A](value: Option[A], message: String): Consequence[A] =
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
  )(using ExecutionContext): Consequence[WorkflowCompletionDataStorePersistence[W, R]] = {
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
    } yield new WorkflowCompletionDataStorePersistence(compound, configuration, component, completionOperation, instances, continuations,
      issued, instanceCollection, continuationCollection, resultCodec, kind)
  }
}
