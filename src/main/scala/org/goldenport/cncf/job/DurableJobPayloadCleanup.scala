package org.goldenport.cncf.job

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * A tombstone certifies acknowledged disposal of exclusively Job-owned bytes.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobCleanupFacet {
  case Tombstoned, AlreadyTombstoned, NotDeleted, Held, NotDue, NotTerminal
  case Missing, Corrupt, Refused, Stale, RevisionExhausted, InvalidTime
  case OwnershipRefused, ResourceBoundRefused, PayloadDeleteRefused, CheckpointRefused
}

private[job] final class DurableJobPayloadCleanup(
  store: DurableJobStore,
  payloads: DurableJobOwnedBlobPayloads
) {
  import DurableJobCleanupFacet.*

  def cleanupObserved(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobCleanupFacet = {
    val result = {
      if (access == null || access.scopes == null || !access.scopes.contains(DurableJobRetentionPolicy.manageScope))
        Refused
      else if (snapshot == null || snapshot.record == null)
        Corrupt
      else
        try {
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
                      if (current != snapshot) Stale else _cleanup(current, access, now)
                  }
              }
          }
        } catch {
          case NonFatal(_) => Corrupt
        }
    }
    if (store == null) result else store.operationalMetricsRecorder.observeCleanup(result)
  }
  private def _cleanup(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobCleanupFacet = {
    val record = snapshot.record
    val retention = record.body.retention
    if (retention.deletionState == DurableDeletionState.Tombstoned) AlreadyTombstoned
    else if (retention.deletionState != DurableDeletionState.Deleted) NotDeleted
    else if (retention.retainUntil.contains(Instant.MAX)) Held
    else if (!_terminal(record.body.lifecycle.status)) NotTerminal
    else if (now == null || now.isBefore(record.body.identity.updatedAt)) InvalidTime
    else if (retention.retainUntil.exists(_.isAfter(now))) NotDue
    else if (record.body.identity.revision == Long.MaxValue) RevisionExhausted
    else if (DurableJobOwnedBlobPayloads.referenceCount(record) > DurableJobOwnedBlobPayloads.maxReferences) ResourceBoundRefused
    else
      payloads.prepare(record) match {
        case Consequence.Failure(_) => OwnershipRefused
        case Consequence.Success(targets) =>
          val disposed = targets.foldLeft(Consequence.unit) { (result, target) => result.flatMap(_ => payloads.delete(target)) }
          disposed match {
            case Consequence.Failure(_) => PayloadDeleteRefused
            case Consequence.Success(_) => _checkpoint(snapshot, access, now)
          }
      }
  }

  private def _checkpoint(
    snapshot: DurableJobStoreSnapshot,
    access: DurableJobRecordAccess,
    now: Instant
  )(using ctx: ExecutionContext): DurableJobCleanupFacet =
    try {
      store.loadStartupRecovery(snapshot.record.body.identity.jobId, access) match {
        case DurableJobStoreStartupRecoveryLoad.Admitted(current) if current == snapshot =>
          val record = current.record
          val retention = record.body.retention.copy(deletionState = DurableDeletionState.Tombstoned,
            tombstone = Some(DurableTombstone(record.body.retention.deletedAt.get, "job-owned-payload-cleanup-complete", None)))
          val body = record.body.copy(identity = record.body.identity.copy(revision = record.body.identity.revision + 1L,
            updatedAt = now), retention = retention)
          val signed = record.format match {
            case DurableRecordFormat.V1 => DurableJobRecordCodec.sign(body)
            case DurableRecordFormat.V2 => DurableJobRecordCodec.signV2(body)
            case _ => Consequence.stateInvalid("Durable Job cleanup checkpoint refused")
          }
          signed.flatMap(store.checkpoint(current, _, access)) match {
            case Consequence.Success(_) => Tombstoned
            case Consequence.Failure(_) => CheckpointRefused
          }
        case _ => CheckpointRefused
      }
    } catch {
      case NonFatal(_) => CheckpointRefused
    }

  private def _terminal(status: DurableJobLifecycleStatus): Boolean = status match {
    case DurableJobLifecycleStatus.Succeeded | DurableJobLifecycleStatus.Failed | DurableJobLifecycleStatus.Cancelled => true
    case _ => false
  }
}
