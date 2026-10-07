package org.goldenport.cncf.workflow

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.event.{EventRecord, EventRecordJsonV1}
import org.goldenport.cncf.unitofwork.TransactionContext

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
/** Borrowed local record preparation, not a transaction, lease, UoW or authority. */
final class WorkflowPreparedDataStoreTransition[A] private (
  store: DataStore & EntityConditionalTransitionDataStore,
  val plan: DataStoreConditionalTransitionPlan,
  project: DataStoreConditionalTransitionResult => Consequence[A]
)(using ExecutionContext) {
  /** Append closed canonical record effects, never callbacks or a second store. */
  def withSideEffectsC(sideEffects: Vector[EntityVersionedSideEffect]):
      Consequence[WorkflowPreparedDataStoreTransition[A]] = {
    try {
      if (sideEffects == null) Consequence.configurationInvalid("Prepared local effects are missing")
      else DataStoreConditionalTransitionPlan.create(plan.root, plan.successor,
        plan.sideEffects ++ sideEffects, plan.correlation).flatMap { admitted =>
        if (admitted.sideEffects.forall(effect => store.isAccept(effect.collection)))
          Consequence.success(new WorkflowPreparedDataStoreTransition(store, admitted, project))
        else Consequence.configurationInvalid("Prepared local effects require the same accepting datastore")
      }
    } catch {
      case NonFatal(_) => Consequence.configurationInvalid("Prepared local effects could not be admitted")
    }
  }

  /** Attach already-issued transactional events to this same canonical record plan.
    * This does not allocate sequences, implement EventStore, or enlist a UnitOfWork.
    */
  def withEventRecordsC(
    collection: DataStore.CollectionId,
    records: Vector[EventRecord]
  ): Consequence[WorkflowPreparedDataStoreTransition[A]] = {
    try {
      if (collection == null || records == null || records.isEmpty || records.exists(_ == null))
        Consequence.configurationInvalid("Prepared event records require a collection and nonempty issued batch")
      else if (plan.sideEffects.exists(_.collection == collection))
        Consequence.stateConflict("Prepared event collection already has side effects")
      else {
        val sequences = records.map(_.sequence)
        if (sequences.distinct.size != records.size ||
            sequences.zip(sequences.drop(1)).exists { case (left, right) => left >= right })
          Consequence.stateConflict("Prepared event batch has duplicate identity or unordered sequence")
        else records.foldLeft[Consequence[Vector[EntityVersionedSideEffect.Create]]](
          Consequence.success(Vector.empty)) { (acc, record) =>
          acc.flatMap(effects => EventRecordJsonV1.createSideEffectC(collection, record)
            .map(effect => effects :+ effect))
        }.flatMap { effects =>
          val ids = effects.map(_.entryId.print)
          if (ids.distinct.size != effects.size)
            Consequence.stateConflict("Prepared event batch has duplicate identity or unordered sequence")
          else withSideEffectsC(effects).map { prepared =>
            new WorkflowPreparedDataStoreTransition(store, prepared.plan, result => result match {
              case changed: DataStoreConditionalTransitionResult.Transitioned =>
                _admitEventReadbackC(collection, effects).flatMap(_ => project(changed))
              case unchanged => project(unchanged)
            })
          }
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Prepared event batch admission failed")
    }
  }

  private def _admitEventReadbackC(
    collection: DataStore.CollectionId,
    effects: Vector[EntityVersionedSideEffect.Create]
  ): Consequence[Unit] =
    effects.foldLeft(Consequence.unit) { (acc, effect) =>
      acc.flatMap { _ =>
        store.load(collection, effect.entryId).flatMap {
          case Some(stored) if stored.getString("event_json") == effect.record.getString("event_json") =>
            Consequence.unit
          case _ =>
            Consequence.stateConflict("Committed event record readback differs from the prepared batch")
        }
      }
    }

  /** One canonical guarded commit; the provider rechecks all prepared guards.
    * No automatic retry, reconnection, callback publication or resource ownership.
    */
  def commitC(): Consequence[A] = {
    try store.conditionalTransition(plan).flatMap {
      case changed: DataStoreConditionalTransitionResult.Transitioned =>
        project(changed).recoverWith { _ =>
          DataStoreConditionalTransitionFailure.transactionIndeterminate(
            "Prepared Workflow committed but authoritative projection could not be admitted")
        }
      case changed => project(changed)
    } catch {
      case NonFatal(_) => DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Prepared Workflow commit or authoritative projection has an uncertain outcome")
    }
  }

  /** Internal storage integration, not UoW completion or a delivery permit.
    * The host chooses the event route; this adapter only admits the original
    * prepared provider and projects its already-verified Workflow pair.
    */
  private[cncf] def commitHostEventsC(space: DataStoreSpace, records: Vector[EventRecord]): Consequence[A] =
    _hostResultC(space, records).flatMap {
      case verified: DataStoreMultiGuardReadbackResult.Verified => _projectHostVerifiedC(verified)
      case DataStoreMultiGuardReadbackResult.NotMatched(existing) =>
        project(DataStoreConditionalTransitionResult.NotMatched(existing))
    }

  /** Non-activated host binding. Real managed storage supplies native evidence;
    * the trusted host supplies frozen completion callbacks and a delivery port.
    * Local revision guards/Creates use the same host-selected native commit;
    * no arbitrary closing program or postcommit data write is inferred.
    * This does not install the participant in central UnitOfWork.
    */
  private[cncf] def hostParticipantC(space: DataStoreSpace, records: Vector[EventRecord],
      callbacks: Vector[() => Consequence[Unit]], cleanup: () => Consequence[Unit],
      delivery: Vector[EventRecord] => Consequence[Unit],
      localGuards: Vector[DataStoreConditionalRoot] = Vector.empty,
      localCreates: Vector[EntityVersionedSideEffect.Create] = Vector.empty): Consequence[ManagedCompletionParticipant[A]] =
    if (delivery == null || records == null || records.exists(_ == null) || localGuards == null ||
        localCreates == null || localGuards.exists(_ == null) || localCreates.exists(_ == null))
      Consequence.configurationInvalid("Managed host participant requires ordinary input and a delivery port")
    else _hostProviderC(space).flatMap { _ =>
      var batch: Option[Vector[EventRecord]] = Some(records)
      var finalized: Option[Vector[EventRecord]] = None
      val native = new ManagedCompletionParticipant.Native[A] {
        def commit(tx: TransactionContext): ManagedCompletionParticipant.Outcome[A] = {
          import ManagedCompletionParticipant.Outcome
          _hostProviderC(space).flatMap(_ => space.conditionalHostEventsFinalizedC(
            plan, batch.getOrElse(Vector.empty), localGuards, localCreates)) match {
            case Consequence.Success((verified: DataStoreMultiGuardReadbackResult.Verified, actual)) =>
              finalized = Some(actual)
              _projectHostVerifiedC(verified) match {
                case Consequence.Success(value) => Outcome.Verified(value)
                case Consequence.Failure(conclusion) => Outcome.Indeterminate(conclusion)
              }
            case Consequence.Success((_: DataStoreMultiGuardReadbackResult.NotMatched, _)) =>
              Outcome.Rejected(Consequence.stateConflict[Unit]("Completion instance guard was consumed").conclusion)
            case Consequence.Failure(conclusion) =>
              // Failure alone cannot prove rollback, including lost host admission.
              Outcome.Indeterminate(conclusion)
          }
        }
        def deliverC(value: A): Consequence[Unit] = finalized match {
          case Some(original) => delivery(original)
          case None => Consequence.stateConflict("Managed delivery batch was discarded")
        }
        def discardC(): Consequence[Unit] = { batch = None; finalized = None; Consequence.unit }
      }
      ManagedCompletionParticipant.createC(native, callbacks, cleanup)
    }

  private def _hostProviderC(space: DataStoreSpace): Consequence[Unit] =
    if (space == null || plan.sideEffects.nonEmpty)
      Consequence.configurationInvalid("Host event completion requires an effect-free prepared pair")
    else for {
      rootStore <- space.dataStore(plan.root.collection)
      successorStore <- space.dataStore(plan.successor.collection)
      _ <- if ((rootStore eq store) && (successorStore eq store)) Consequence.unit
        else Consequence.configurationInvalid("Prepared Workflow provider differs from the admitted host provider")
    } yield ()

  private def _hostResultC(space: DataStoreSpace, records: Vector[EventRecord],
      localGuards: Vector[DataStoreConditionalRoot] = Vector.empty,
      localCreates: Vector[EntityVersionedSideEffect.Create] = Vector.empty): Consequence[DataStoreMultiGuardReadbackResult] =
    if (space == null || records == null || plan.sideEffects.nonEmpty)
      Consequence.configurationInvalid("Host event completion requires an effect-free prepared pair and ordinary input")
    else for {
      _ <- _hostProviderC(space)
      result <- space.conditionalHostEventsWithLocalIntentsVerified(plan, records, localGuards, localCreates)
    } yield result

  private def _projectHostVerifiedC(verified: DataStoreMultiGuardReadbackResult.Verified): Consequence[A] =
          try {
            val pair = verified.records.take(2)
            if (pair.map(r => r.collection -> r.entryId) != Vector(plan.root.collection -> plan.root.entryId,
                plan.successor.collection -> plan.successor.entryId))
              DataStoreConditionalTransitionFailure.transactionIndeterminate("Verified Workflow pair coverage differs")
            else project(DataStoreConditionalTransitionResult.Transitioned(pair.head.record, pair(1).record))
              .recoverWith { _ => DataStoreConditionalTransitionFailure.transactionIndeterminate(
                "Host Workflow committed but authoritative projection could not be admitted") }
          } catch {
            case NonFatal(_) => DataStoreConditionalTransitionFailure.transactionIndeterminate(
              "Host Workflow committed but authoritative projection threw")
          }
}

object WorkflowPreparedDataStoreTransition {
  /** Trusted Workflow adapters supply only their existing readback admission. */
  private[workflow] def create[A](
    store: DataStore & EntityConditionalTransitionDataStore,
    plan: DataStoreConditionalTransitionPlan,
    project: DataStoreConditionalTransitionResult => Consequence[A]
  )(using ExecutionContext): WorkflowPreparedDataStoreTransition[A] =
    new WorkflowPreparedDataStoreTransition(store, plan, project)
}
