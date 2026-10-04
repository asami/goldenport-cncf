package org.goldenport.cncf.job

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Whole-candidate reservation and actual stream accounting are operation-local.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobIntegrityByteBudget(maxPhysicalBytes: Long = 16777216L) {
  private var _reserved = 0L
  private var _actual = 0L

  def isValid: Boolean = maxPhysicalBytes > 0L && maxPhysicalBytes <= 16777216L
  def physicalBytesRead: Long = _actual

  def reserve(byteSizes: Vector[Long], maxPayloadBytes: Long): Boolean = {
    if (!isValid || byteSizes == null || maxPayloadBytes <= 0L ||
        byteSizes.exists(size => size < 0L || size > maxPayloadBytes)) false
    else {
      try {
        val cost = byteSizes.foldLeft(0L)((total, size) => Math.addExact(total, Math.addExact(size, 1L)))
        if (cost > maxPhysicalBytes - _reserved) false
        else { _reserved = Math.addExact(_reserved, cost); true }
      } catch { case _: ArithmeticException => false }
    }
  }

  def recordRead(count: Int): Unit = {
    val next = Math.addExact(_actual, count.toLong)
    if (count < 0 || next > _reserved) throw new IllegalStateException("durable integrity inspection refused")
    _actual = next
  }
}

private[job] final class DurableJobIntegrityInspection(
  source: DurableJobStartupRecoverySource,
  store: DurableJobStore,
  payloads: DurableJobOwnedBlobPayloads,
  lifetime: DurableJobLifetimeQuota,
  maxPhysicalBytes: Long = 16777216L
) {
  import JobDurableIntegrityStatus.*

  def inspect(maxCandidates: Int)(using ctx: ExecutionContext): Consequence[JobDurableIntegrityReport] =
    try {
      val budget = new DurableJobIntegrityByteBudget(maxPhysicalBytes)
      if (!budget.isValid || maxCandidates < 1 || maxCandidates > 1024 || source == null ||
          store == null || payloads == null || lifetime == null || Thread.currentThread().isInterrupted)
        DurableJobIntegrityInspection.refused
      else new DurableJobStartupRecoveryCoordinator(source, store)._admitted_candidates_c(maxCandidates)
        .recoverWith(_ => DurableJobIntegrityInspection.refused).flatMap { candidates =>
          if (candidates.exists(candidate => !DurableJobStorageResourcePolicy.validId(candidate.jobId)))
            DurableJobIntegrityInspection.refused
          else {
            val entries = candidates.map { candidate =>
              if (Thread.currentThread().isInterrupted) throw new InterruptedException()
              JobDurableIntegrityEntry(candidate.ordinal, _inspect_candidate(candidate, budget))
            }
            if (Thread.currentThread().isInterrupted) DurableJobIntegrityInspection.refused
            else Consequence.success(JobDurableIntegrityReport(entries, budget.physicalBytesRead))
          }
        }
    } catch {
      case _: InterruptedException => Thread.currentThread().interrupt(); DurableJobIntegrityInspection.refused
      case NonFatal(_) => DurableJobIntegrityInspection.refused
    }

  private def _inspect_candidate(candidate: DurableJobStartupRecoveryCandidate,
    budget: DurableJobIntegrityByteBudget)(using ctx: ExecutionContext): JobDurableIntegrityStatus =
    store.loadStartupRecovery(candidate.jobId, candidate.access) match {
      case DurableJobStoreStartupRecoveryLoad.Missing => Missing
      case DurableJobStoreStartupRecoveryLoad.Corrupt => Corrupt
      case DurableJobStoreStartupRecoveryLoad.Refused => AccessRefused
      case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
        if (snapshot.record.body.retention.deletionState != DurableDeletionState.Active) Inactive
        else if (snapshot.record.format == DurableRecordFormat.V1) MigrationRequired
        else lifetime.recoverySnapshot(snapshot, candidate.access, store) match {
          case Consequence.Success(_) => payloads.inspect(snapshot.record, budget) match {
            case Consequence.Success(DurableJobPayloadIntegrityOutcome.Verified) =>
              store.loadStartupRecovery(candidate.jobId, candidate.access) match {
                case DurableJobStoreStartupRecoveryLoad.Admitted(current) if current == snapshot => Healthy
                case _ => Stale
              }
            case Consequence.Success(DurableJobPayloadIntegrityOutcome.ResourceBoundRefused) => ResourceBoundRefused
            case _ => PayloadRefused
          }
          case _ => LifetimeRefused
        }
    }
}

private[job] object DurableJobIntegrityInspection {
  def refused[A]: Consequence[A] = Consequence.stateInvalid("durable integrity inspection refused")
}
