package org.goldenport.cncf.statemachine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
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
      _ <- plan.fold(Consequence.unit)(ExecutionPlanExecutor.execute(_, entity, event))
    } yield ()
  }

  def beforeUpdate[T](
    entity: T,
    tc: EntityPersistent[T]
  )(using ctx: ExecutionContext): Consequence[Unit] = {
    val event = TransitionEvent("update", Some(tc.id(entity)))
    for {
      plan <- plannerProvider.planForUpdate(entity, tc, event)
      _ <- plan.fold(Consequence.unit)(ExecutionPlanExecutor.execute(_, entity, event))
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
      _ <- plan.fold(Consequence.unit)(ExecutionPlanExecutor.execute(_, state, event))
    } yield ()
  }
}

