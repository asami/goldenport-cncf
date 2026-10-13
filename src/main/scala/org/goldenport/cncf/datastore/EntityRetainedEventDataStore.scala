package org.goldenport.cncf.datastore

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/** Separate opt-in for a real Workflow write with validation-only event facts.
  * No dummy cursor update, generic provider fallback or dispatch authorization.
  * @since   Sep. 30, 2026
  * @version Oct.  8, 2026
  */
private[datastore] trait EntityRetainedEventDataStore { self: DataStore =>
  private[datastore] final def retainedEventsVerified(invocation: DataStoreSpace.RetainedEventInvocation)
      (using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    invocation.validateProviderC(this).flatMap { _ =>
      try commit_retained_events(invocation).flatMap {
        case DataStoreMultiGuardAcknowledgement.NotMatched(existing) if existing != null =>
          Consequence.success(DataStoreMultiGuardReadbackResult.NotMatched(existing))
        case DataStoreMultiGuardAcknowledgement.Committed(records) =>
          (for {
            _ <- invocation.validateReadbackC(records)
            _ <- invocation.validateProviderC(this)
            stored <- readback_retained_events(invocation)
            _ <- invocation.validateReadbackC(stored)
            _ <- if (stored == records) Consequence.unit else Consequence.stateConflict("Retained native acknowledgement differs")
            _ <- invocation.validateProviderC(this)
          } yield DataStoreMultiGuardReadbackResult.verified(stored)).recoverWith { _ => uncertain }
        case _ => uncertain
      } catch { case NonFatal(_) => uncertain }
    }
  private def uncertain[A]: Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(
    "Retained completion commit or complete readback is uncertain")
  protected def commit_retained_events(invocation: DataStoreSpace.RetainedEventInvocation)
    (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement]
  protected def readback_retained_events(invocation: DataStoreSpace.RetainedEventInvocation)
    (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]]
}
