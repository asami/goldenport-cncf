package org.goldenport.cncf.datastore

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import scala.util.control.NonFatal

/** Separate internal opt-in: a raw two-record acknowledgement is insufficient.
  * Real providers must preserve transaction post-images and their atomic commit.
  * The final seam alone performs complete same-provider storage readback.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[datastore] trait EntityVerifiedMultiGuardDataStore { self: DataStore =>
  private[datastore] def supportsPreparedEvents: Boolean = false
  private[datastore] final def conditionalMultiGuardVerifiedAdmitted(
    invocation: DataStoreSpace.MultiGuardInvocation
  )(using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    if (invocation == null) Consequence.configurationInvalid("Multi-guard invocation is missing")
    else invocation.validateProviderC(this).flatMap { _ =>
      try commit_multi_guard_admitted(invocation).flatMap {
        case DataStoreMultiGuardAcknowledgement.NotMatched(existing) if existing != null =>
          Consequence.success(DataStoreMultiGuardReadbackResult.NotMatched(existing))
        case DataStoreMultiGuardAcknowledgement.Committed(records) =>
          val verified = for {
            _ <- invocation.validateReadbackC(records)
            _ <- invocation.validateProviderC(this)
            stored <- readback_multi_guard(invocation)
            _ <- invocation.validateReadbackC(stored)
            _ <- if (stored == records) Consequence.unit
              else Consequence.configurationInvalid("Committed multi-guard storage readback differs")
            _ <- invocation.validateProviderC(this)
          } yield DataStoreMultiGuardReadbackResult.verified(records)
          verified.recoverWith { _ => _uncertain }
        case _ => _uncertain
      } catch {
        case NonFatal(_) => _uncertain
      }
    }

  private def _uncertain[A]: Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(
      "Multi-guard commit acknowledgement or complete authoritative readback is uncertain")

  protected def commit_multi_guard_for_readback(plan: DataStoreConditionalMultiGuardPlan)
    (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement]

  /** Managed providers retain the opaque host invocation through native commit.
    * The default preserves existing same-owner candidate implementations.
    */
  protected def commit_multi_guard_admitted(invocation: DataStoreSpace.MultiGuardInvocation)
    (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement] =
    commit_multi_guard_for_readback(invocation.plan)

  /** One coherent native snapshot for the exact invocation targets, in order.
    * Memory holds its monitor; SQL must use one native read transaction/snapshot.
    * Recheck the invocation before reads. No generic load-loop fallback is safe
    * for arbitrary providers, and no caller supplies a transaction callback.
    */
  protected def readback_multi_guard(invocation: DataStoreSpace.MultiGuardInvocation)
    (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]]
}
