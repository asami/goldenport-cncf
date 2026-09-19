package org.goldenport.cncf.statemachine

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.TransitionLifecycleFailureOutcome
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.entity.{EntityPersistent, EntityPersistentUpdate}
import org.goldenport.record.Record

/*
 * @since   Mar. 19, 2026
 *  version Mar. 24, 2026
 *  version Aug. 14, 2026
 * @version Sep. 19, 2026
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
  toStateValue: Option[Int] = None,
  historyCompositeName: Option[String] = None,
  historyFieldName: Option[String] = None,
  historyDirectLeaves: Vector[String] = Vector.empty,
  historyDirectLeafValues: Map[String, Int] = Map.empty,
  historyFallbackLeaf: Option[String] = None,
  expectedHistoryRecordWrites: Vector[HistoryRecordWrite] = Vector.empty,
  binding: Option[CmlTransitionBinding] = None,
  trigger: TransitionTrigger = TransitionTrigger.Update
) {
  require(
    trigger != TransitionTrigger.Operation || binding.exists { value =>
      value.operation.nonEmpty && value.triggerContext.nonEmpty
    },
    "Operation transition rules require an explicit CML operation binding and typed trigger context"
  )

  def isStructural: Boolean =
    stateFieldName.isDefined && fromState.isDefined &&
      (toState.isDefined || historyCompositeName.isDefined)
}

final class CollectionStateMachinePlanner[S](
  rules: Vector[TransitionRule[S]]
) {
  def plan(
    state: S,
    event: TransitionEvent
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    planWithOutcome(state, event).toConsequence

  def planWithOutcome(
    state: S,
    event: TransitionEvent
  ): TransitionPlanningResult[S] =
    if (rules.exists(_.isStructural))
      _plan_structural_with_outcome(state, event)
    else
      _plan_legacy_with_outcome(state, event)

  private def _plan_legacy_with_outcome(
    state: S,
    event: TransitionEvent
  ): TransitionPlanningResult[S] =
    TransitionPlanningResult.fromConsequence(_plan_legacy(state, event))

  private def _plan_legacy(
    state: S,
    event: TransitionEvent
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] = {
    val candidates =
      rules.zipWithIndex.collect {
        case (rule, i) if _matches_legacy_or_explicit_trigger(rule, event) =>
          TransitionCandidate(rule, priority = rule.priority, declarationOrder = rule.declarationOrder + i)
      }.toVector
    TransitionSelector
      .select(candidates) { c =>
        c.guard.fold(Consequence.success(true))(_.eval(state, event))
      }
      .map(_.map(_selected_plan))
  }

  private def _plan_structural(
    state: S,
    event: TransitionEvent
  ): Consequence[Option[ExecutionPlan[S, TransitionEvent]]] =
    _plan_structural_with_outcome(state, event).toConsequence

  private def _plan_structural_with_outcome(
    state: S,
    event: TransitionEvent
  ): TransitionPlanningResult[S] = {
    val structuralrules = rules.filter(_.isStructural)
    if (
      (event.currentRecord.isEmpty || event.proposedRecord.isEmpty) &&
      !_requires_structural_record_comparison(structuralrules, event)
    )
      TransitionPlanningResult.NoTransition[S]()
    else
      (event.currentRecord, event.proposedRecord) match {
      case (Some(current), Some(proposed)) =>
        val changedfields = structuralrules.flatMap(_.stateFieldName).distinct.filter { fieldname =>
          !_same_state_value(current.getAny(fieldname), proposed.getAny(fieldname))
        }
        changedfields match {
          case Vector() => TransitionPlanningResult.NoTransition[S]()
          case Vector(fieldname) =>
            val currentvalue = current.getAny(fieldname)
            val proposedvalue = proposed.getAny(fieldname)
            val candidates = structuralrules.zipWithIndex.collect {
              case (rule, i)
                  if rule.stateFieldName.contains(fieldname) &&
                    _matches_state(currentvalue, rule.fromState, rule.fromStateValue) &&
                    _matches_explicit_operation(rule, event) =>
                TransitionCandidate(rule, priority = rule.priority, declarationOrder = rule.declarationOrder + i)
            }.toVector
            if (candidates.isEmpty)
              _rejected_from(
                TransitionLifecycleFailureOutcome.Source,
                _state_conflict(fieldname, currentvalue, proposedvalue),
                None
              )
            else
              _select_structural_candidate(
                state,
                event,
                current,
                proposed,
                proposedvalue,
                fieldname,
                currentvalue,
                candidates
              )
          case fields =>
            _rejected_from(
              TransitionLifecycleFailureOutcome.Target,
              Consequence.stateConflict(
                s"One update cannot change multiple state-machine fields: ${fields.mkString(", ")}"
              ),
              None
            )
        }
      case _ =>
        _rejected_from(
          TransitionLifecycleFailureOutcome.Target,
          Consequence.stateConflict("Structural state-machine validation requires current and proposed records"),
          None
        )
      }
    }

  private def _select_structural_candidate(
    state: S,
    event: TransitionEvent,
    current: Record,
    proposed: Record,
    proposedstate: Option[Any],
    fieldname: String,
    currentvalue: Option[Any],
    candidates: Vector[TransitionCandidate[TransitionRule[S]]]
  ): TransitionPlanningResult[S] = {
    var guardrejected = false
    var guardbinding: Option[CmlTransitionBinding] = None
    var targetbinding: Option[CmlTransitionBinding] = None
    var failureoutcome = TransitionLifecycleFailureOutcome.Target
    var failurebinding: Option[CmlTransitionBinding] = None
    val selected = TransitionSelector.select(candidates) { rule =>
      failureoutcome = TransitionLifecycleFailureOutcome.Target
      failurebinding = rule.binding
      _matches_transition_target(rule, current, proposed, proposedstate).flatMap {
        case false =>
          if (targetbinding.isEmpty)
            targetbinding = rule.binding
          Consequence.success(false)
        case true =>
          val semanticevent = event.copy(name = rule.eventName)
          failureoutcome = TransitionLifecycleFailureOutcome.Guard
          rule.guard.fold(Consequence.success(true))(_.eval(state, semanticevent)).map {
            case false =>
              guardrejected = true
              guardbinding = rule.binding
              false
            case true => true
          }
      }
    }
    selected match {
      case Consequence.Success(Some(rule)) =>
        TransitionPlanningResult.Selected(_selected_plan(rule))
      case Consequence.Success(None) =>
        val outcome = if (guardrejected)
          TransitionLifecycleFailureOutcome.Guard
        else
          TransitionLifecycleFailureOutcome.Target
        val binding = if (guardrejected) guardbinding else targetbinding
        _rejected_from(
          outcome,
          _state_conflict(fieldname, currentvalue, proposedstate),
          binding
        )
      case Consequence.Failure(conclusion) =>
        TransitionPlanningResult.Rejected(failureoutcome, conclusion, failurebinding)
    }
  }

  private def _rejected_from[A](
    outcome: TransitionLifecycleFailureOutcome,
    result: Consequence[A],
    binding: Option[CmlTransitionBinding]
  ): TransitionPlanningResult[S] =
    result match {
      case Consequence.Failure(conclusion) =>
        TransitionPlanningResult.Rejected(outcome, conclusion, binding)
      case Consequence.Success(_) =>
        throw new IllegalStateException("StateMachine rejection requires a failure Conclusion")
    }

  private def _requires_structural_record_comparison(
    structuralrules: Vector[TransitionRule[S]],
    event: TransitionEvent
  ): Boolean =
    structuralrules.exists { rule =>
      if (rule.trigger == TransitionTrigger.Operation)
        _matches_explicit_operation(rule, event)
      else
        true
    }

  private def _selected_plan(
    rule: TransitionRule[S]
  ): ExecutionPlan[S, TransitionEvent] =
    rule.plan.copy(
      selectedTransitionBinding = rule.binding,
      selectedTransitionTrigger = Some(rule.trigger)
    )

  private def _matches_legacy_or_explicit_trigger(
    rule: TransitionRule[S],
    event: TransitionEvent
  ): Boolean =
    if (rule.trigger == TransitionTrigger.Operation)
      _matches_explicit_operation(rule, event)
    else
      rule.eventName == event.name

  /**
   * An explicit CML operation transition is selected only by the invocation
   * identity produced for the running action.  No event-name, record, or
   * state-field value is used to infer this binding's operation.
   */
  private def _matches_explicit_operation(
    rule: TransitionRule[S],
    event: TransitionEvent
  ): Boolean =
    rule.trigger match {
      case TransitionTrigger.Operation =>
        rule.binding.exists { binding =>
          binding.operation.nonEmpty &&
            binding.triggerContext.nonEmpty &&
            binding.matchesOperationInvocation(event.invocation)
        }
      case _ => true
    }

  private def _matches_transition_target(
    rule: TransitionRule[S],
    current: Record,
    proposed: Record,
    proposedstate: Option[Any]
  ): Consequence[Boolean] =
    rule.historyCompositeName match {
      case Some(composite) =>
        _history_transition_target(rule, current, composite).flatMap { expected =>
          if (_matches_state(proposedstate, Some(expected), rule.historyDirectLeafValues.get(expected)))
            _validate_history_record_writes(
              proposed,
              rule.historyFieldName,
              Vector(HistoryRecordWrite(composite, expected))
            ).map(_ => true)
          else
            Consequence.success(false)
        }
      case None =>
        if (_matches_state(proposedstate, rule.toState, rule.toStateValue))
          _validate_history_record_writes(
            proposed,
            rule.historyFieldName,
            rule.expectedHistoryRecordWrites
          ).map(_ => true)
        else
          Consequence.success(false)
    }

  private def _history_transition_target(
    rule: TransitionRule[S],
    current: Record,
    composite: String
  ): Consequence[String] =
    (rule.historyFieldName, rule.historyFallbackLeaf) match {
      case (Some(fieldname), Some(fallback)) if rule.historyDirectLeaves.nonEmpty =>
        _history_record(current, fieldname, required = false).flatMap { history =>
          history.getAny(composite) match {
            case None => Consequence.success(fallback)
            case Some(value: String) if value.trim.isEmpty => Consequence.success(fallback)
            case Some(value: String) =>
              rule.historyDirectLeaves.find(_.equalsIgnoreCase(value.trim)).map(Consequence.success).getOrElse {
                Consequence.stateConflict(
                  s"History record '$fieldname' has unrecognized leaf '${value.trim}' for composite '$composite'"
                )
              }
            case Some(_) =>
              Consequence.stateConflict(
                s"History record '$fieldname' has a non-string leaf for composite '$composite'"
              )
          }
        }
      case _ =>
        Consequence.stateConflict(s"History transition for '$composite' has incomplete metadata")
    }

  private def _validate_history_record_writes(
    proposed: Record,
    fieldname: Option[String],
    writes: Vector[HistoryRecordWrite]
  ): Consequence[Unit] =
    if (writes.isEmpty)
      Consequence.unit
    else
      fieldname match {
        case Some(name) =>
          _history_record(proposed, name, required = true).flatMap { history =>
            writes.foldLeft(Consequence.unit) { (z, write) =>
              z.flatMap { _ =>
                history.getAny(write.compositeName) match {
                  case Some(value: String) if value.trim.equalsIgnoreCase(write.leafName) =>
                    Consequence.unit
                  case _ =>
                    Consequence.stateConflict(
                      s"Proposed history record '$name' must contain ${write.compositeName} -> ${write.leafName}"
                    )
                }
              }
            }
          }
        case None =>
          Consequence.stateConflict("State-machine transition requires a history record field")
      }

  private def _history_record(
    record: Record,
    fieldname: String,
    required: Boolean
  ): Consequence[Record] =
    record.getAny(fieldname) match {
      case Some(history: Record) => Consequence.success(history)
      case None if !required => Consequence.success(Record.empty)
      case None => Consequence.stateConflict(s"State-machine transition requires history record '$fieldname'")
      case Some(_) => Consequence.stateConflict(s"State-machine history field '$fieldname' must be a record")
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
    planForSaveOutcome(entity, tc, event).toConsequence
  }

  override def planForSaveOutcome[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): TransitionPlanningResult[T] = {
    val name = tc.id(entity).collection.name
    _save_planners.get(name) match {
      case Some(planner) =>
        planner
          .asInstanceOf[CollectionStateMachinePlanner[T]]
          .planWithOutcome(entity, event)
      case None =>
        underlying.planForSaveOutcome(entity, tc, event)
    }
  }

  def planForUpdate[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[T, TransitionEvent]]] = {
    planForUpdateOutcome(entity, tc, event).toConsequence
  }

  override def planForUpdateOutcome[T](
    entity: T,
    tc: EntityPersistent[T],
    event: TransitionEvent
  )(using ExecutionContext): TransitionPlanningResult[T] = {
    val name = tc.id(entity).collection.name
    _update_planners.get(name) match {
      case Some(planner) =>
        planner
          .asInstanceOf[CollectionStateMachinePlanner[T]]
          .planWithOutcome(entity, event)
      case None =>
        underlying.planForUpdateOutcome(entity, tc, event)
    }
  }

  def planForUpdateById[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    event: TransitionEvent
  )(using ExecutionContext): Consequence[Option[ExecutionPlan[(EntityId, P), TransitionEvent]]] =
    planForUpdateByIdOutcome(id, patch, tc, event).toConsequence

  override def planForUpdateByIdOutcome[P](
    id: EntityId,
    patch: P,
    tc: EntityPersistentUpdate[P],
    event: TransitionEvent
  )(using ExecutionContext): TransitionPlanningResult[(EntityId, P)] =
    _update_planners.get(id.collection.name) match {
      case Some(planner) =>
        planner
          .asInstanceOf[CollectionStateMachinePlanner[(EntityId, P)]]
          .planWithOutcome((id, patch), event)
      case None =>
        underlying.planForUpdateByIdOutcome(id, patch, tc, event)
    }
}
