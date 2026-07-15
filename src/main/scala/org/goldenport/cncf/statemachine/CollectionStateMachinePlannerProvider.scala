package org.goldenport.cncf.statemachine

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.record.Record

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class TransitionRule[S](
  eventName: String,
  priority: Int,
  declarationOrder: Int,
  guard: Option[Guard[S, TransitionEvent]],
  plan: ExecutionPlan[S, TransitionEvent],
  machineName: Option[String] = None,
  stateFieldName: Option[String] = None,
  fromState: Option[String] = None,
  fromStateValue: Option[Int] = None,
  toState: Option[String] = None,
  toStateValue: Option[Int] = None
) {
  def isStructural: Boolean =
    stateFieldName.isDefined && fromState.isDefined && toState.isDefined
}

final class CollectionStateMachinePlanner[S](
  rules: Vector[TransitionRule[S]]
) {
  def plan(
    state: S,
    event: TransitionEvent
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    if (rules.exists(_.isStructural))
      _plan_structural(state, event)
    else
      _plan_legacy(state, event)

  private def _plan_legacy(
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

  private def _plan_structural(
    state: S,
    event: TransitionEvent
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    (event.currentRecord, event.proposedRecord) match {
      case (Some(current), Some(proposed)) =>
        val structuralrules = rules.filter(_.isStructural)
        val changedfields = structuralrules.flatMap(_.stateFieldName).distinct.filter { fieldname =>
          !_same_state_value(current.getAny(fieldname), proposed.getAny(fieldname))
        }
        changedfields match {
          case Vector() => Consequence.success(None)
          case Vector(fieldname) =>
            val currentvalue = current.getAny(fieldname)
            val proposedvalue = proposed.getAny(fieldname)
            val candidates = structuralrules.zipWithIndex.collect {
              case (rule, i)
                  if rule.stateFieldName.contains(fieldname) &&
                    _matches_state(currentvalue, rule.fromState, rule.fromStateValue) &&
                    _matches_state(proposedvalue, rule.toState, rule.toStateValue) =>
                TransitionCandidate(rule, priority = rule.priority, declarationOrder = rule.declarationOrder + i)
            }.toVector
            if (candidates.isEmpty)
              _state_conflict(fieldname, currentvalue, proposedvalue)
            else
              TransitionSelector
                .select(candidates) { rule =>
                  val semanticevent = event.copy(name = rule.eventName)
                  rule.guard.fold(Consequence.success(true))(_.eval(state, semanticevent))
                }
                .flatMap {
                  case Some(rule) => Consequence.success(Some(rule.plan))
                  case None => _state_conflict(fieldname, currentvalue, proposedvalue)
                }
          case fields =>
            Consequence.stateConflict(
              s"One update cannot change multiple state-machine fields: ${fields.mkString(", ")}"
            )
        }
      case _ =>
        Consequence.stateConflict("Structural state-machine validation requires current and proposed records")
    }

  private def _state_conflict(
    fieldname: String,
    current: Option[Any],
    proposed: Option[Any]
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    Consequence.stateConflict(
      s"Transition for '$fieldname' is not allowed: ${_show_state(current)} -> ${_show_state(proposed)}"
    )

  private def _same_state_value(lhs: Option[Any], rhs: Option[Any]): Boolean =
    (_normalized_state(lhs), _normalized_state(rhs)) match {
      case (Some((lname, lvalue)), Some((rname, rvalue))) =>
        lname.equalsIgnoreCase(rname) || (lvalue.isDefined && lvalue == rvalue)
      case (None, None) => true
      case _ => false
    }

  private def _matches_state(
    actual: Option[Any],
    name: Option[String],
    value: Option[Int]
  ): Boolean =
    _normalized_state(actual).exists { case (actualname, actualvalue) =>
      name.exists(_.equalsIgnoreCase(actualname)) ||
        (value.isDefined && value == actualvalue)
    }

  private def _normalized_state(p: Option[Any]): Option[(String, Option[Int])] =
    p.flatMap(_unwrap_state).map { value =>
      val text = value.toString.trim
      text -> text.toIntOption
    }

  private def _unwrap_state(p: Any): Option[Any] =
    p match {
      case null => None
      case Some(value) => _unwrap_state(value)
      case None => None
      case record: Record => record.getAny("value").flatMap(_unwrap_state)
      case product: Product if product.productArity == 1 && product.productElementName(0) == "value" =>
        _unwrap_state(product.productElement(0))
      case value => Some(value)
    }

  private def _show_state(p: Option[Any]): String =
    _normalized_state(p).map(_._1).getOrElse("<missing>")
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
    _update_planners.get(id.collection.name) match {
      case Some(planner) =>
        planner
          .asInstanceOf[CollectionStateMachinePlanner[(EntityId, P)]]
          .plan((id, patch), event)
      case None =>
        underlying.planForUpdateById(id, patch, tc, event)
    }
}
