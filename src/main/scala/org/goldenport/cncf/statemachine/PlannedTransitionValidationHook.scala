package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.cncf.event.{CommittedTransition, TransitionLifecycleEvent, TransitionLifecycleFailureStage}
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
  proposedRecord: Option[Record] = None
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
    val event = TransitionEvent("save", Some(tc.id(entity)))
    for {
      plan <- _observe_planning_failure(
        event,
        Some(tc.id(entity).collection.name),
        plannerProvider.planForSave(entity, tc, event)
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
    val event = TransitionEvent("update", Some(tc.id(entity)))
    for {
      plan <- _observe_planning_failure(
        event,
        Some(tc.id(entity).collection.name),
        plannerProvider.planForUpdate(entity, tc, event)
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
    val event = TransitionEvent("update", Some(tc.id(entity)), Some(current), Some(proposed))
    for {
      plan <- _observe_planning_failure(
        event,
        Some(tc.id(entity).collection.name),
        plannerProvider.planForUpdate(entity, tc, event)
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
    val event = TransitionEvent("updateById", Some(id))
    val state = (id, patch)
    for {
      plan <- _observe_planning_failure(
        event,
        Some(id.collection.name),
        plannerProvider.planForUpdateById(id, patch, tc, event)
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
    val event = TransitionEvent("updateById", Some(id), Some(current), Some(proposed))
    val state = (id, patch)
    for {
      plan <- _observe_planning_failure(
        event,
        Some(id.collection.name),
        plannerProvider.planForUpdateById(id, patch, tc, event)
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
        _stage(TransitionLifecycleEvent.beforeTransition(transitionevent, collection))
      }

      def after(
        plan: ExecutionPlan[S, TransitionEvent],
        state: S,
        event: TransitionEvent
      ): Unit = {
        val _ = (state, event)
        _stage(TransitionLifecycleEvent.afterTransition(transitionevent, collection))
        for {
          binding <- plan.selectedTransitionBinding
          entityid <- transitionevent.targetId
        } _stage_committed_transition(binding, entityid, transitionevent.name)
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
          TransitionLifecycleFailureStage.Action
        )
      }
    }

  private def _observe_planning_failure[S](
    transitionevent: TransitionEvent,
    collection: Option[String],
    result: Consequence[Option[ExecutionPlan[S, TransitionEvent]]]
  )(using ctx: ExecutionContext): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    result match {
      case Consequence.Failure(failure) =>
        _stage_failure(
          transitionevent,
          collection,
          failure,
          TransitionLifecycleFailureStage.Planning
        )
        Consequence.Failure(failure)
      case success => success
    }

  private def _stage_failure(
    transitionevent: TransitionEvent,
    collection: Option[String],
    failure: org.goldenport.Conclusion,
    stage: TransitionLifecycleFailureStage
  )(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stagePostAbortEventC { _ =>
      TransitionLifecycleEvent.transitionFailed(
        transitionevent,
        collection,
        failure,
        stage
      )
    }

  private def _stage(event: org.goldenport.cncf.event.DomainEvent)(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stageEvent(event)

  private def _stage_committed_transition(
    binding: CmlTransitionBinding,
    entityid: EntityId,
    operationid: String
  )(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stagePostCommitEventC { transactionid =>
      CommittedTransition.create(entityid, binding, operationid, transactionid)
    }
}
