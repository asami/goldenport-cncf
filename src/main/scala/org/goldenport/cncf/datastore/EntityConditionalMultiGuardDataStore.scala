package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/** Internal opt-in provider dispatch seam. This is not a public owner-label
  * grant or a claim that a particular provider supports atomic multi-guard
  * commit. DataStoreSpace alone constructs its invocation after host/domain
  * admission. Real providers must additionally prove atomicity and readback.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[cncf] trait EntityConditionalMultiGuardDataStore { self: DataStore =>
  private[datastore] final def conditionalMultiGuardAdmitted(
    invocation: DataStoreSpace.MultiGuardInvocation
  )(using ExecutionContext): Consequence[DataStoreConditionalTransitionResult] =
    if (invocation == null) Consequence.configurationInvalid("Multi-guard invocation is missing")
    else invocation.validateProviderC(this).flatMap(_ =>
      commit_multi_guard(invocation.plan))

  protected def commit_multi_guard(
    plan: DataStoreConditionalMultiGuardPlan
  )(using ExecutionContext): Consequence[DataStoreConditionalTransitionResult]
}
