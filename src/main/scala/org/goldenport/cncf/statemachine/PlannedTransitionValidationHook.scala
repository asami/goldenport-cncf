package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.cncf.event.TransitionLifecycleEvent

/*
 * @since   Mar. 19, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransitionEvent(
  name: String,
  targetId: Option[EntityId]
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
      plan <- plannerProvider.planForSave(entity, tc, event)
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
      plan <- plannerProvider.planForUpdate(entity, tc, event)
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
      plan <- plannerProvider.planForUpdateById(id, patch, tc, event)
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
        val _ = (plan, state, event)
        _stage(TransitionLifecycleEvent.afterTransition(transitionevent, collection))
      }

      def failed(
        plan: ExecutionPlan[S, TransitionEvent],
        state: S,
        event: TransitionEvent,
        failure: org.goldenport.Conclusion
      ): Unit = {
        val _ = (plan, state, event)
        _stage(TransitionLifecycleEvent.transitionFailed(transitionevent, collection, failure))
      }
    }

  private def _stage(event: org.goldenport.cncf.event.DomainEvent)(using ctx: ExecutionContext): Unit =
    ctx.runtime.unitOfWork.stageEvent(event)
}
