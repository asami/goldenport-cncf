package org.goldenport.cncf.statemachine

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}

/*
 * @since   Mar. 19, 2026
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransitionRule[S](
  eventName: String,
  priority: Int,
  declarationOrder: Int,
  guard: Option[Guard[S, TransitionEvent]],
  plan: ExecutionPlan[S, TransitionEvent]
)

final class CollectionStateMachinePlanner[S](
  rules: Vector[TransitionRule[S]]
) {
  def plan(
    state: S,
    event: TransitionEvent
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] = {
    val candidates =
      rules.zipWithIndex.collect {
        case (rule, i) if rule.eventName == event.name =>
          TransitionCandidate(rule, priority = rule.priority, declarationOrder = rule.declarationOrder + i)
      }.toVector
    TransitionSelector
      .select(candidates) { c =>
        c.guard.fold(Consequence.success(true))(_.eval(state, event))
      }
      .map(_.map(_.plan))
  }
}

final class CollectionStateMachinePlannerProvider(
  underlying: StateMachinePlannerProvider = StateMachinePlannerProvider.noop
) extends StateMachinePlannerProvider {
  private val _update_planners: mutable.Map[String, CollectionStateMachinePlanner[Any]] = mutable.Map.empty
  private val _save_planners: mutable.Map[String, CollectionStateMachinePlanner[Any]] = mutable.Map.empty

  def registerUpdate[S](
    collectionName: String,
    planner: CollectionStateMachinePlanner[S]
  ): Unit =
    _update_planners.update(collectionName, planner.asInstanceOf[CollectionStateMachinePlanner[Any]])

  def registerSave[S](
    collectionName: String,
    planner: CollectionStateMachinePlanner[S]
  ): Unit =
    _save_planners.update(collectionName, planner.asInstanceOf[CollectionStateMachinePlanner[Any]])

  def planForSave[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
    val name = tc.id(entity).collection.name
    _save_planners.get(name) match {
      case Some(planner) =>
        planner
          .asInstanceOf[CollectionStateMachinePlanner[T]]
          .plan(entity, event)
      case None =>
        underlying.planForSave(entity, tc, event)
    }
  }

  def planForUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
    val name = tc.id(entity).collection.name
    _update_planners.get(name) match {
      case Some(planner) =>
        planner
          .asInstanceOf[CollectionStateMachinePlanner[T]]
          .plan(entity, event)
      case None =>
        underlying.planForUpdate(entity, tc, event)
    }
  }

  def planForUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[(EntityId, P), TransitionEvent]]] =
    underlying.planForUpdateById(id, patch, tc, event)
}

