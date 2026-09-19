package org.goldenport.cncf.statemachine

/*
 * Transitional component-side rule provider.
 *
 * Cozy/simplemodeling metadata binding is not wired yet, so components can
 * optionally provide transition rules directly through this interface.
 *
 * @since   Mar. 19, 2026
 *  version Aug. 14, 2026
 * @version Sep. 19, 2026
 * @author  ASAMI, Tomoharu
 */
enum TransitionTrigger {
  case Save, Update, Operation
}

final case class CollectionTransitionRule[S](
  collectionName: String,
  trigger: TransitionTrigger,
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
  binding: Option[CmlTransitionBinding] = None
) {
  require(
    trigger != TransitionTrigger.Operation || binding.exists { value =>
      value.operation.nonEmpty && value.triggerContext.nonEmpty
    },
    "Operation transition rules require an explicit CML operation binding and typed trigger context"
  )

  def isStructural: Boolean =
    stateFieldName.isDefined && fromState.isDefined &&
      (toState.isDefined || historyCompositeName.isDefined ||
        binding.exists(_.target == CmlStateMachineTransitionTarget.Final))
}

final case class HistoryRecordWrite(
  compositeName: String,
  leafName: String
)

trait CollectionTransitionRuleProvider {
  def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] =
    Vector.empty
}
