package org.goldenport.cncf.datastore

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/** Separate opt-in for host-selected transient/empty completion. Storage proof
  * grants neither transient dispatch nor application/UoW completion.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[datastore] trait EntityTransientEventDataStore { self: DataStore =>
  private[datastore] final def transientEventsVerified(inv: DataStoreSpace.TransientEventInvocation)
      (using ExecutionContext): Consequence[DataStoreMultiGuardReadbackResult] =
    inv.validateProviderC(this).flatMap { _ =>
      try commit_transient_events(inv).flatMap {
        case DataStoreMultiGuardAcknowledgement.NotMatched(existing) if existing != null =>
          Consequence.success(DataStoreMultiGuardReadbackResult.NotMatched(existing))
        case DataStoreMultiGuardAcknowledgement.Committed(records) =>
          (for {
            _ <- inv.validateReadbackC(records)
            _ <- inv.validateProviderC(this)
            stored <- readback_transient_events(inv)
            _ <- inv.validateReadbackC(stored)
            _ <- if (stored == records) Consequence.unit else Consequence.stateConflict("Transient pair acknowledgement differs")
            _ <- inv.validateProviderC(this)
          } yield DataStoreMultiGuardReadbackResult.verified(stored)).recoverWith { _ => uncertain }
        case _ => uncertain
      } catch { case NonFatal(_) => uncertain }
    }
  private def uncertain[A]: Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(
    "Transient completion commit or complete readback is uncertain")
  protected def commit_transient_events(inv: DataStoreSpace.TransientEventInvocation)
    (using ExecutionContext): Consequence[DataStoreMultiGuardAcknowledgement]
  protected def readback_transient_events(inv: DataStoreSpace.TransientEventInvocation)
    (using ExecutionContext): Consequence[Vector[DataStoreMultiGuardRecord]]
}
