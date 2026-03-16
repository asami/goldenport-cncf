package org.goldenport.cncf.action

/*
 * @since   Mar. 16, 2026
 * @version Mar. 16, 2026
 * @author  ASAMI, Tomoharu
 */
trait ActionCallHelper {
  protected final def action_call(
    actionname: String,
    pair: ActionCallSupport.Pair
  )(createcall: ActionCall.Core => ActionCall): ActionCall =
    ActionCallSupport.actionCall(actionname, pair)(createcall)

  protected final def action_call(
    actionname: String,
    kind: ActionCallSupport.TestDataKind
  )(createcall: ActionCall.Core => ActionCall): ActionCall =
    ActionCallSupport.actionCall(actionname, kind)(createcall)

  protected final def action_call(
    kind: ActionCallSupport.TestDataKind
  )(createcall: ActionCall.Core => ActionCall): ActionCall =
    ActionCallSupport.actionCall(kind)(createcall)
}
