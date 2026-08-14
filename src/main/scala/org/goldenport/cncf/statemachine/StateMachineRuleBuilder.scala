package org.goldenport.cncf.statemachine

import org.goldenport.Consequence

/*
 * Builder helpers for generated/component-defined transition rules.
 *
 * @since   Mar. 19, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
object StateMachineRuleBuilder {
  def action[S](
    f: (S, TransitionEvent) => Consequence[Unit]
  ): ResolvedAction[S, TransitionEvent] =
    new ResolvedAction[S, TransitionEvent] {
      def run(state: S, event: TransitionEvent): Consequence[Unit] =
        f(state, event)
    }

  def plan[S](
    exit: Vector[ResolvedAction[S, TransitionEvent]] = Vector.empty,
    transition: Option[ResolvedAction[S, TransitionEvent]] = None,
    entry: Vector[ResolvedAction[S, TransitionEvent]] = Vector.empty
  ): ExecutionPlan[S, TransitionEvent] =
    ExecutionPlan(
      exitActions = exit,
      transitionAction = transition,
      entryActions = entry
    )

  def guardExpression[S](
    expr: String
  )(
    contextFactory: (S, TransitionEvent) => Map[String, Any]
  ): Guard[S, TransitionEvent] =
    ExpressionGuard(expr, contextFactory)

  def guardRef[S](
    name: String,
    resolver: GuardBindingResolver[S, TransitionEvent]
  ): Guard[S, TransitionEvent] =
    RefGuard(name, resolver)

  def updateRule[S](
    collectionName: String,
    eventName: String = "update",
    priority: Int = 0,
    declarationOrder: Int = 0,
    guard: Option[Guard[S, TransitionEvent]] = None,
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
    historyFallbackLeaf: Option[String] = None,
    expectedHistoryRecordWrites: Vector[HistoryRecordWrite] = Vector.empty
  ): CollectionTransitionRule[Any] =
    CollectionTransitionRule[Any](
      collectionName = collectionName,
      trigger = TransitionTrigger.Update,
      eventName = eventName,
      priority = priority,
      declarationOrder = declarationOrder,
      guard = guard.asInstanceOf[Option[Guard[Any, TransitionEvent]]],
      plan = plan.asInstanceOf[ExecutionPlan[Any, TransitionEvent]],
      machineName = machineName,
      stateFieldName = stateFieldName,
      fromState = fromState,
      fromStateValue = fromStateValue,
      toState = toState,
      toStateValue = toStateValue,
      historyCompositeName = historyCompositeName,
      historyFieldName = historyFieldName,
      historyDirectLeaves = historyDirectLeaves,
      historyFallbackLeaf = historyFallbackLeaf,
      expectedHistoryRecordWrites = expectedHistoryRecordWrites
    )

  def saveRule[S](
    collectionName: String,
    eventName: String = "save",
    priority: Int = 0,
    declarationOrder: Int = 0,
    guard: Option[Guard[S, TransitionEvent]] = None,
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
    historyFallbackLeaf: Option[String] = None,
    expectedHistoryRecordWrites: Vector[HistoryRecordWrite] = Vector.empty
  ): CollectionTransitionRule[Any] =
    CollectionTransitionRule[Any](
      collectionName = collectionName,
      trigger = TransitionTrigger.Save,
      eventName = eventName,
      priority = priority,
      declarationOrder = declarationOrder,
      guard = guard.asInstanceOf[Option[Guard[Any, TransitionEvent]]],
      plan = plan.asInstanceOf[ExecutionPlan[Any, TransitionEvent]],
      machineName = machineName,
      stateFieldName = stateFieldName,
      fromState = fromState,
      fromStateValue = fromStateValue,
      toState = toState,
      toStateValue = toStateValue,
      historyCompositeName = historyCompositeName,
      historyFieldName = historyFieldName,
      historyDirectLeaves = historyDirectLeaves,
      historyFallbackLeaf = historyFallbackLeaf,
      expectedHistoryRecordWrites = expectedHistoryRecordWrites
    )
}
