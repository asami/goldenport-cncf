package org.goldenport.cncf.job

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Explicit bounded expiry, logical deletion and physical-cleanup coordination.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobMaintenanceEntry(
  ordinal: Long,
  retention: Vector[DurableJobRetentionFacet],
  cleanup: Option[DurableJobCleanupFacet]
)

private[job] final case class DurableJobMaintenanceReport(entries: Vector[DurableJobMaintenanceEntry])

private[job] final class DurableJobMaintenance(
  store: DurableJobStore,
  retention: DurableJobRetentionMaintenance,
  cleanup: DurableJobPayloadCleanup
) {
  def sweep(
    source: DurableJobRetentionCandidateSource,
    maxCandidates: Int,
    now: Instant
  )(using ctx: ExecutionContext): Consequence[DurableJobMaintenanceReport] = {
    val result = if (maxCandidates < 1 || maxCandidates > DurableJobRetentionPolicy.maxCandidates || now == null || now == Instant.MAX)
        _refused
      else
        _candidates(source, maxCandidates).flatMap { candidates =>
          if (candidates == null || candidates.size > maxCandidates ||
            candidates.exists(candidate => candidate == null || Option(candidate.jobid).forall(_.trim.isEmpty)) ||
            candidates.map(_.jobid).distinct.size != candidates.size ||
            candidates.zipWithIndex.exists { case (candidate, index) => candidate.ordinal != index.toLong })
            _refused
          else Consequence.success(DurableJobMaintenanceReport(candidates.map(_maintain(_, now))))
        }
    if (store == null) result else store.operationalMetricsRecorder.observeMaintenanceSweep(result)
  }
  private def _candidates(source: DurableJobRetentionCandidateSource, maxcandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]] =
    try {
      if (source == null) _refused
      else source.candidates(maxcandidates) match {
        case success @ Consequence.Success(_) => success
        case _ => _refused
      }
    } catch {
      case NonFatal(_) => _refused
    }

  private def _maintain(candidate: DurableJobRetentionCandidate, now: Instant)(using ctx: ExecutionContext): DurableJobMaintenanceEntry = {
    val access = candidate.access
    if (access == null || access.scopes == null || !access.scopes.contains(DurableJobRetentionPolicy.manageScope))
      DurableJobMaintenanceEntry(candidate.ordinal, Vector(DurableJobRetentionFacet.Refused), None)
    else
      _load(candidate) match {
        case Left(facet) => DurableJobMaintenanceEntry(candidate.ordinal, Vector(facet), None)
        case Right(snapshot) => snapshot.record.body.retention.deletionState match {
          case DurableDeletionState.Active =>
            val expired = _retention(snapshot, access, DurableJobRetentionCommand.Expire, now)
            if (expired == DurableJobRetentionFacet.Expired)
              _delete(candidate, Vector(expired), now)
            else DurableJobMaintenanceEntry(candidate.ordinal, Vector(expired), None)
          case DurableDeletionState.Expired => _delete(candidate, Vector.empty, now)
          case DurableDeletionState.Deleted | DurableDeletionState.Tombstoned => _cleanup(candidate, Vector.empty, now)
        }
      }
  }

  private def _delete(candidate: DurableJobRetentionCandidate, facets: Vector[DurableJobRetentionFacet], now: Instant)(using ctx: ExecutionContext): DurableJobMaintenanceEntry =
    _load(candidate) match {
      case Left(facet) => DurableJobMaintenanceEntry(candidate.ordinal, facets :+ facet, None)
      case Right(snapshot) if snapshot.record.body.retention.deletionState != DurableDeletionState.Expired &&
        snapshot.record.body.retention.deletionState != DurableDeletionState.Deleted =>
        DurableJobMaintenanceEntry(candidate.ordinal, facets :+ DurableJobRetentionFacet.Stale, None)
      case Right(snapshot) =>
        val deleted = _retention(snapshot, candidate.access, DurableJobRetentionCommand.Delete, now)
        val reported = facets :+ deleted
        if (deleted == DurableJobRetentionFacet.Deleted || deleted == DurableJobRetentionFacet.AlreadyDeleted)
          _cleanup(candidate, reported, now)
        else DurableJobMaintenanceEntry(candidate.ordinal, reported, None)
    }

  private def _cleanup(candidate: DurableJobRetentionCandidate, facets: Vector[DurableJobRetentionFacet], now: Instant)(using ctx: ExecutionContext): DurableJobMaintenanceEntry = {
    val facet = try {
      store.loadStartupRecovery(candidate.jobid, candidate.access) match {
        case DurableJobStoreStartupRecoveryLoad.Missing => DurableJobCleanupFacet.Missing
        case DurableJobStoreStartupRecoveryLoad.Corrupt => DurableJobCleanupFacet.Corrupt
        case DurableJobStoreStartupRecoveryLoad.Refused => DurableJobCleanupFacet.Refused
        case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) => cleanup.cleanupObserved(snapshot, candidate.access, now)
      }
    } catch {
      case NonFatal(_) => DurableJobCleanupFacet.Corrupt
    }
    DurableJobMaintenanceEntry(candidate.ordinal, facets, Some(facet))
  }

  private def _load(candidate: DurableJobRetentionCandidate)(using ctx: ExecutionContext): Either[DurableJobRetentionFacet, DurableJobStoreSnapshot] =
    try store.loadStartupRecovery(candidate.jobid, candidate.access) match {
      case DurableJobStoreStartupRecoveryLoad.Missing => Left(DurableJobRetentionFacet.Missing)
      case DurableJobStoreStartupRecoveryLoad.Corrupt => Left(DurableJobRetentionFacet.Corrupt)
      case DurableJobStoreStartupRecoveryLoad.Refused => Left(DurableJobRetentionFacet.Refused)
      case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) => Right(snapshot)
    } catch {
      case NonFatal(_) => Left(DurableJobRetentionFacet.Corrupt)
    }

  private def _retention(snapshot: DurableJobStoreSnapshot, access: DurableJobRecordAccess, command: DurableJobRetentionCommand, now: Instant)(using ctx: ExecutionContext): DurableJobRetentionFacet =
    try retention.maintainObserved(snapshot, access, command, now)
    catch { case NonFatal(_) => DurableJobRetentionFacet.WriteRefused }

  private def _refused[A]: Consequence[A] = Consequence.stateInvalid("Durable Job maintenance batch refused")
}
