package org.goldenport.cncf.workflow

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import scala.util.control.NonFatal

/** Internal route adapter, not a successful Workflow completion projection.
  * It retains DataStoreSpace only, never a raw provider. Commit authorization
  * is rechecked by the live host view on every invocation; preparation alone
  * grants no right to commit. No automatic retries or dispatch callbacks.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[cncf] final class WorkflowPreparedMultiGuardTransition private (
  space: DataStoreSpace,
  val plan: DataStoreConditionalMultiGuardPlan
) {
  /** Complete storage verification only, not application success/dispatch authorization. */
  def commitVerifiedC()(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    try space.conditionalMultiGuardVerified(plan)
    catch {
      case NonFatal(_) => DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Workflow multi-guard complete readback outcome is uncertain")
    }

  def commitC()(using ExecutionContext): Consequence[DataStoreConditionalTransitionResult] =
    try space.conditionalMultiGuard(plan)
    catch {
      case NonFatal(_) => DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Workflow multi-guard provider outcome is uncertain")
    }
}

private[cncf] object WorkflowPreparedMultiGuardTransition {
  def prepareC(space: DataStoreSpace, plan: DataStoreConditionalMultiGuardPlan):
      Consequence[WorkflowPreparedMultiGuardTransition] =
    if (space == null || plan == null)
      Consequence.configurationInvalid("Workflow multi-guard preparation is incomplete")
    else DataStoreConditionalMultiGuardPlan.create(plan.transition,
      plan.additionalGuards, plan.scope).map(admitted =>
        new WorkflowPreparedMultiGuardTransition(space, admitted))
}
