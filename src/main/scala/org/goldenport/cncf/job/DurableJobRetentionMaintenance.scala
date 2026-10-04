package org.goldenport.cncf.job

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Authorized, optimistic retention maintenance with explicit bounded discovery.
 * Reports contain only ordinal and closed facet; discovery finishes before effects.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobRetentionCandidate(
  ordinal: Long,
  jobid: String,
  access: DurableJobRecordAccess
)

private[job] trait DurableJobRetentionCandidateSource {
  def candidates(maxCandidates: Int): Consequence[Vector[DurableJobRetentionCandidate]]
}

private[job] final case class DurableJobRetentionReportEntry(
  ordinal: Long,
  facet: DurableJobRetentionFacet
)

private[job] final case class DurableJobRetentionReport(
  entries: Vector[DurableJobRetentionReportEntry]
)

private[job] final class DurableJobRetentionMaintenance(store: DurableJobStore) {
  def maintainObserved(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    command: DurableJobRetentionCommand,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobRetentionFacet = {
    val result = _maintain_observed(snapshot, access, command, now)
    if (store == null) result else store.operationalMetricsRecorder.observeRetention(result)
  }

  def sweep(
    source: DurableJobRetentionCandidateSource,
    maxCandidates: Int,
    now: Instant
  )(using ctx: ExecutionContext): Consequence[DurableJobRetentionReport] = {
    val result = if (maxCandidates < 1 || maxCandidates > DurableJobRetentionPolicy.maxCandidates)
        Consequence.argumentInvalid("Durable retention candidate bound must be in 1..1024")
      else
        _candidates(source, maxCandidates).flatMap(_validate(_, maxCandidates)).map { candidates =>
          DurableJobRetentionReport(candidates.map { candidate =>
            DurableJobRetentionReportEntry(candidate.ordinal, _expire(candidate, now))
          })
        }
    if (store == null) result else store.operationalMetricsRecorder.observeRetentionSweep(result)
  }
  private def _maintain_observed(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    command: DurableJobRetentionCommand,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobRetentionFacet = {
    import DurableJobRetentionFacet.*
    if (!_capable(access))
      Refused
    else if (snapshot == null || snapshot.record == null)
      Corrupt
    else
      DurableJobRecordCodec.canonicalJson(snapshot.record) match {
        case Consequence.Failure(_) => Corrupt
        case Consequence.Success(canonical) =>
          DurableJobRecordCodec.startupAdmission(canonical, access) match {
            case DurableJobRecordStartupAdmission.Corrupt => Corrupt
            case DurableJobRecordStartupAdmission.Refused => Refused
            case DurableJobRecordStartupAdmission.Admitted(record) =>
              store.loadStartupRecovery(record.body.identity.jobId, access) match {
                case DurableJobStoreStartupRecoveryLoad.Missing => Missing
                case DurableJobStoreStartupRecoveryLoad.Corrupt => Corrupt
                case DurableJobStoreStartupRecoveryLoad.Refused => Refused
                case DurableJobStoreStartupRecoveryLoad.Admitted(current) =>
                  if (current != snapshot)
                    Stale
                  else
                    _maintain(current, access, command, now)
              }
          }
      }
  }


  private def _capable(access: DurableJobRecordAccess): Boolean =
    access != null && access.scopes != null &&
      access.scopes.contains(DurableJobRetentionPolicy.manageScope)

  private def _maintain(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    command: DurableJobRetentionCommand,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobRetentionFacet =
    DurableJobRetentionPolicy.evaluate(snapshot.record, command, now) match {
      case DurableJobRetentionDecision.Unchanged(facet) => facet
      case DurableJobRetentionDecision.Checkpoint(retention, facet) =>
        val record = snapshot.record
        val identity = record.body.identity.copy(
          revision = record.body.identity.revision + 1L,
          updatedAt = now
        )
        val body = record.body.copy(identity = identity, retention = retention)
        val signed = record.format match {
          case DurableRecordFormat.V1 => DurableJobRecordCodec.sign(body)
          case DurableRecordFormat.V2 => DurableJobRecordCodec.signV2(body)
          case _ => Consequence.stateInvalid("Durable retention format refused")
        }
        signed.flatMap(store.checkpoint(snapshot, _, access)) match {
          case Consequence.Success(_) => facet
          case Consequence.Failure(_) => DurableJobRetentionFacet.WriteRefused
        }
    }

  private def _candidates(
    source: DurableJobRetentionCandidateSource,
    maxcandidates: Int
  ): Consequence[Vector[DurableJobRetentionCandidate]] =
    try {
      if (source == null)
        Consequence.stateInvalid("Durable retention candidate source failed")
      else
        source.candidates(maxcandidates) match {
          case Consequence.Success(candidates) => Consequence.success(candidates)
          case _ => Consequence.stateInvalid("Durable retention candidate source failed")
        }
    } catch {
      case NonFatal(_) => Consequence.stateInvalid("Durable retention candidate source failed")
    }

  private def _validate(
    candidates: Vector[DurableJobRetentionCandidate],
    maxcandidates: Int
  ): Consequence[Vector[DurableJobRetentionCandidate]] =
    if (candidates == null || candidates.size > maxcandidates ||
      candidates.exists(candidate => candidate == null ||
        Option(candidate.jobid).forall(_.trim.isEmpty)) ||
      candidates.map(_.jobid).distinct.size != candidates.size ||
      candidates.zipWithIndex.exists { case (candidate, index) => candidate.ordinal != index.toLong }
    )
      Consequence.argumentInvalid("Durable retention candidate batch refused")
    else
      Consequence.success(candidates)

  private def _expire(
    candidate: DurableJobRetentionCandidate,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobRetentionFacet = {
    import DurableJobRetentionFacet.*
    if (!_capable(candidate.access))
      Refused
    else
      store.loadStartupRecovery(candidate.jobid, candidate.access) match {
        case DurableJobStoreStartupRecoveryLoad.Missing => Missing
        case DurableJobStoreStartupRecoveryLoad.Corrupt => Corrupt
        case DurableJobStoreStartupRecoveryLoad.Refused => Refused
        case DurableJobStoreStartupRecoveryLoad.Admitted(snapshot) =>
          _maintain_observed(snapshot, candidate.access, DurableJobRetentionCommand.Expire, now)
      }
  }
}
