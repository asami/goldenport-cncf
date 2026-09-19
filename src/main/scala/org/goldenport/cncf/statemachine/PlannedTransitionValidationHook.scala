package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ExecutionInvocationIdentity}
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.cncf.event.{CommittedTransition, TransitionLifecycleEvent, TransitionLifecycleFailureOutcome, TransitionLifecycleFailureStage}
import org.goldenport.record.Record

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Jul. 16, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransitionEvent(
  name: String,
  targetId: Option[EntityId],
  currentRecord: Option[Record] = None,
  proposedRecord: Option[Record] = None,
  invocation: Option[ExecutionInvocationIdentity] = None
)

trait StateMachinePlannerProvider {
  def planForSave[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]]

  def planForUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]]

  def planForUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[(EntityId, P), TransitionEvent]]]

  /**
   * Outcome-aware compatibility path.  Existing providers only implement the
   * Consequence methods above; their unannotated failures conservatively map
   * to NoMatch while preserving the original Conclusion.
   */
  def planForSaveOutcome[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): TransitionPlanningResult[T] =
    TransitionPlanningResult.fromConsequence(planForSave(entity, tc, event))

  def planForUpdateOutcome[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): TransitionPlanningResult[T] =
    TransitionPlanningResult.fromConsequence(planForUpdate(entity, tc, event))

  def planForUpdateByIdOutcome[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    event: TransitionEvent
  )(using ExecutionContext): TransitionPlanningResult[(EntityId, P)] =
    TransitionPlanningResult.fromConsequence(planForUpdateById(id, patch, tc, event))
}

object StateMachinePlannerProvider {
  val noop: StateMachinePlannerProvider = new StateMachinePlannerProvider {
    def planForSave[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
      val _ = (entity, tc, event)
      Consequence.success(None)
    }

    def planForUpdate[T](
      entity: T,
      tc: EntityPersistent[T],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
      val _ = (entity, tc, event)
      Consequence.success(None)
    }

    def planForUpdateById[P](
      id: EntityId,
      patch: P,
      tc: EntityPersistentUpdate[P],
      event: TransitionEvent
    )(using ExecutionContext): Consequence[Option[ExecutionPlan[(EntityId, P), TransitionEvent]]] = {
      val _ = (id, patch, tc, event)
      Consequence.success(None)
    }
  }
}

final class PlannedTransitionValidationHook(
  plannerProvider: StateMachinePlannerProvider
) extends TransitionValidationHook {
  def beforeSave[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent("save", Some(tc.id(entity)), invocation = ctx.executionControl.invocation)
    for {
      plan <- _observe_planning_result(
        event,
        Some(tc.id(entity).collection.name),
        _validate_selected_operation_binding(
          plannerProvider.planForSaveOutcome(entity, tc, event),
          event
        )
      )
      _ <- plan.fold(Consequence.unit) { p =>
        ExecutionPlanExecutor.execute(
          p,
          entity,
          event,
          _lifecycle_observer[T](event, Some(tc.id(entity).collection.name))
        )
      }
    } yield ()
  }

  override def beforeSave[T](
    entity: T,
    tc: EntityPersistent[T],
    current: Record,
    proposed: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent(
      "save",
      Some(tc.id(entity)),
      Some(current),
      Some(proposed),
      ctx.executionControl.invocation
    )
    for {
      plan <- _observe_planning_result(
        event,
        Some(tc.id(entity).collection.name),
        _validate_selected_operation_binding(
          plannerProvider.planForSaveOutcome(entity, tc, event),
          event
        )
      )
      _ <- plan.fold(Consequence.unit) { p =>
        ExecutionPlanExecutor.execute(
          p,
          entity,
          event,
          _lifecycle_observer[T](event, Some(tc.id(entity).collection.name))
        )
      }
    } yield ()
  }

  def beforeUpdate[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent("update", Some(tc.id(entity)), invocation = ctx.executionControl.invocation)
    for {
      plan <- _observe_planning_result(
        event,
        Some(tc.id(entity).collection.name),
        _validate_selected_operation_binding(
          plannerProvider.planForUpdateOutcome(entity, tc, event),
          event
        )
      )
      _ <- plan.fold(Consequence.unit) { p =>
        ExecutionPlanExecutor.execute(
          p,
          entity,
          event,
          _lifecycle_observer[T](event, Some(tc.id(entity).collection.name))
        )
      }
    } yield ()
  }

  override def beforeUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    current: Record,
    proposed: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent(
      "update",
      Some(tc.id(entity)),
      Some(current),
      Some(proposed),
      ctx.executionControl.invocation
    )
    for {
      plan <- _observe_planning_result(
        event,
        Some(tc.id(entity).collection.name),
        _validate_selected_operation_binding(
          plannerProvider.planForUpdateOutcome(entity, tc, event),
          event
        )
      )
      _ <- plan.fold(Consequence.unit) { p =>
        ExecutionPlanExecutor.execute(
          p,
          entity,
          event,
          _lifecycle_observer[T](event, Some(tc.id(entity).collection.name))
        )
      }
    } yield ()
  }

  def beforeUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent("updateById", Some(id), invocation = ctx.executionControl.invocation)
    val state = (id, patch)
    for {
      plan <- _observe_planning_result(
        event,
        Some(id.collection.name),
        _validate_selected_operation_binding(
          plannerProvider.planForUpdateByIdOutcome(id, patch, tc, event),
          event
        )
      )
      _ <- plan.fold(Consequence.unit) { p =>
        ExecutionPlanExecutor.execute(
          p,
          state,
          event,
          _lifecycle_observer[(EntityId, P)](event, Some(id.collection.name))
        )
      }
    } yield ()
  }

  override def beforeUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    current: Record,
    proposed: Record
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent(
      "updateById",
      Some(id),
      Some(current),
      Some(proposed),
      ctx.executionControl.invocation
    )
    val state = (id, patch)
    for {
      plan <- _observe_planning_result(
        event,
        Some(id.collection.name),
        _validate_selected_operation_binding(
          plannerProvider.planForUpdateByIdOutcome(id, patch, tc, event),
          event
        )
      )
      _ <- plan.fold(Consequence.unit) { p =>
        ExecutionPlanExecutor.execute(
          p,
          state,
          event,
          _lifecycle_observer[(EntityId, P)](event, Some(id.collection.name))
        )
      }
    } yield ()
  }

  private def _lifecycle_observer[S](
    transitionevent: TransitionEvent,
    collection: Option[String]
  )(using ctx: ExecutionContext): TransitionLifecycleObserver[S, TransitionEvent] =
    new TransitionLifecycleObserver[S, TransitionEvent] {
      def before(
        plan: ExecutionPlan[S, TransitionEvent],
        state: S,
        event: TransitionEvent
      ): Unit = {
        val _ = (plan, state, event)
        _stage(TransitionLifecycleEvent.beforeTransition(
          transitionevent,
          collection,
          plan.selectedTransitionBinding
        ))
      }

      def after(
        plan: ExecutionPlan[S, TransitionEvent],
        state: S,
        event: TransitionEvent
      ): Unit = {
        val _ = (state, event)
        _stage(TransitionLifecycleEvent.afterTransition(
          transitionevent,
          collection,
          plan.selectedTransitionBinding
        ))
        for {
          binding <- plan.selectedTransitionBinding
          entityid <- transitionevent.targetId
          operationid <- _committed_operation_id(binding, transitionevent)
        } _stage_committed_transition(binding, entityid, operationid)
        plan.selectedTransitionBinding.foreach { binding =>
          _stage_selected_termination_failure(transitionevent, collection, binding)
        }
      }

      def failed(
        plan: ExecutionPlan[S, TransitionEvent],
        state: S,
        event: TransitionEvent,
        failure: org.goldenport.Conclusion
      ): Unit = {
        val _ = (plan, state, event)
        _stage_failure(
          transitionevent,
          collection,
          failure,
          TransitionLifecycleFailureStage.Action,
          TransitionLifecycleFailureOutcome.Action,
          plan.selectedTransitionBinding
        )
      }
    }

  private def _observe_planning_result[S](
    transitionevent: TransitionEvent,
    collection: Option[String],
    result: TransitionPlanningResult[S]
  )(using ctx: ExecutionContext): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    result match {
      case TransitionPlanningResult.Selected(plan) =>
        Consequence.success(Some(plan))
      case TransitionPlanningResult.NoTransition() =>
        Consequence.success(None)
      case TransitionPlanningResult.Rejected(outcome, conclusion, binding) =>
        _stage_failure(
          transitionevent,
          collection,
          conclusion,
          TransitionLifecycleFailureStage.Planning,
          outcome,
          binding
        )
        Consequence.Failure(conclusion)
    }

  private def _validate_selected_operation_binding[S](
    result: TransitionPlanningResult[S],
    event: TransitionEvent
  ): TransitionPlanningResult[S] =
    result match {
      case TransitionPlanningResult.Selected(value)
          if value.selectedTransitionTrigger.contains(TransitionTrigger.Operation) &&
          !value.selectedTransitionBinding.exists { binding =>
            binding.operation.nonEmpty &&
              binding.triggerContext.nonEmpty &&
              binding.matchesOperationInvocation(event.invocation)
          } =>
        _operation_binding_rejection(
          "StateMachine operation transition requires its matching explicit CML binding",
          value.selectedTransitionBinding
        )
      case TransitionPlanningResult.Selected(value) if value.selectedTransitionBinding.exists(
          binding => binding.operation.nonEmpty && !binding.matchesOperationInvocation(event.invocation)
        ) =>
        _operation_binding_rejection(
          "StateMachine explicit operation binding does not match the execution invocation",
          value.selectedTransitionBinding
        )
      case _ => result
    }

  private def _operation_binding_rejection[S](
    message: String,
    binding: Option[CmlTransitionBinding]
  ): TransitionPlanningResult[S] =
    Consequence.stateConflict(message) match {
      case Consequence.Failure(conclusion) =>
        TransitionPlanningResult.Rejected(
          TransitionLifecycleFailureOutcome.NoMatch,
          conclusion,
          binding
        )
    }

  private def _stage_failure(
    transitionevent: TransitionEvent,
    collection: Option[String],
    failure: org.goldenport.Conclusion,
    stage: TransitionLifecycleFailureStage,
    outcome: TransitionLifecycleFailureOutcome,
    binding: Option[CmlTransitionBinding]
  )(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stagePostAbortEventC { _ =>
      TransitionLifecycleEvent.transitionFailed(
        transitionevent,
        collection,
        failure,
        stage,
        outcome,
        binding
      )
    }

  private def _stage_selected_termination_failure(
    transitionevent: TransitionEvent,
    collection: Option[String],
    binding: CmlTransitionBinding
  )(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stagePostAbortEventC { (outcome, _) =>
      val failure = Consequence.stateConflict("selected transition was not committed") match {
        case Consequence.Failure(conclusion) => conclusion
        case _ => throw new IllegalStateException("state conflict must produce a failure conclusion")
      }
      val lifecycleoutcome = outcome match {
        case org.goldenport.cncf.unitofwork.UnitOfWork.PostAbortOutcome.Persistence =>
          TransitionLifecycleFailureOutcome.Persistence
        case org.goldenport.cncf.unitofwork.UnitOfWork.PostAbortOutcome.Rollback =>
          TransitionLifecycleFailureOutcome.Rollback
      }
      TransitionLifecycleEvent.transitionFailed(
        transitionevent,
        collection,
        failure,
        TransitionLifecycleFailureStage.Action,
        lifecycleoutcome,
        Some(binding)
      )
    }

  private def _stage(event: org.goldenport.cncf.event.DomainEvent)(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stageEvent(event)

  private def _stage_committed_transition(
    binding: CmlTransitionBinding,
    entityid: EntityId,
    operationid: String
  )(using ctx: ExecutionContext): Unit = {
    val occurrence = CommittedTransition.pending(entityid, binding, operationid, ctx.executionControl.invocation)
    ctx.runtime.unitOfWork.stagePostCommitEventC { transactionid =>
      occurrence.deliver(transactionid)
    }
  }

  /**
   * Legacy bindings retain their physical hook event name.  An explicit
   * operation binding is only committed when the selected binding still
   * matches the actual invocation; its committed operation is that selector.
   */
  private def _committed_operation_id(
    binding: CmlTransitionBinding,
    event: TransitionEvent
  ): Option[String] =
    binding.operation match {
      case None => Some(event.name)
      case Some(_) if binding.matchesOperationInvocation(event.invocation) =>
        event.invocation.map(_.operationSelector)
      case Some(_) => None
    }
}
