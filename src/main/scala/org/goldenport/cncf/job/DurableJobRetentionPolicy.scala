package org.goldenport.cncf.job

import java.time.Instant

/*
 * Pure decisions over an already admitted signed retention value.
 *
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobRetentionCommand {
  case Expire, Delete, Hold
  case ReleaseHold(retainuntil: Option[Instant])
}

private[job] enum DurableJobRetentionFacet {
  case Kept, NotDue, Held, NotTerminal
  case AlreadyExpired, AlreadyDeleted, Tombstoned
  case Expired, Deleted, HoldApplied, HoldReleased
  case Missing, Corrupt, Refused, Stale
  case InvalidTransition, RevisionExhausted, WriteRefused
}

private[job] enum DurableJobRetentionDecision {
  case Unchanged(facet: DurableJobRetentionFacet)
  case Checkpoint(retention: DurableRetentionState, facet: DurableJobRetentionFacet)
}

private[job] object DurableJobRetentionPolicy {
  val maxCandidates: Int = 1024
  val manageScope: String = "cncf.job.retention.manage"

  def evaluate(
    record: DurableJobRecord,
    command: DurableJobRetentionCommand,
    now: Instant
  ): DurableJobRetentionDecision = {
    import DurableJobRetentionCommand.*
    import DurableJobRetentionDecision.*
    import DurableJobRetentionFacet.*
    val retention = record.body.retention
    val active = retention.deletionState == DurableDeletionState.Active
    val held = retention.retainUntil.contains(Instant.MAX)
    val decision = command match {
      case Hold if !active => Unchanged(InvalidTransition)
      case ReleaseHold(_) if !active => Unchanged(InvalidTransition)
      case Hold if held => Unchanged(Held)
      case Hold => Checkpoint(retention.copy(retainUntil = Some(Instant.MAX)), HoldApplied)
      case ReleaseHold(replacement) =>
        if (!held || replacement == null || replacement.exists(value =>
          value == null || value == Instant.MAX || value.isBefore(record.body.identity.createdAt)
        ))
          Unchanged(InvalidTransition)
        else
          Checkpoint(retention.copy(retainUntil = replacement), HoldReleased)
      case Expire | Delete if retention.deletionState == DurableDeletionState.Deleted =>
        Unchanged(AlreadyDeleted)
      case Expire | Delete if retention.deletionState == DurableDeletionState.Tombstoned =>
        Unchanged(Tombstoned)
      case Expire if retention.deletionState == DurableDeletionState.Expired =>
        Unchanged(AlreadyExpired)
      case Expire | Delete if held => Unchanged(Held)
      case Expire | Delete if !_terminal(record.body.lifecycle.status) => Unchanged(NotTerminal)
      case Expire if retention.expiresAt.isEmpty => Unchanged(Kept)
      case Expire | Delete if now == null => Unchanged(InvalidTransition)
      case Expire if _future(retention.expiresAt, now) || _future(retention.retainUntil, now) =>
        Unchanged(NotDue)
      case Expire =>
        Checkpoint(retention.copy(deletionState = DurableDeletionState.Expired), Expired)
      case Delete if _future(retention.retainUntil, now) => Unchanged(NotDue)
      case Delete =>
        Checkpoint(retention.copy(deletedAt = Some(now), deletionState = DurableDeletionState.Deleted), Deleted)
      case null => Unchanged(InvalidTransition)
    }
    decision match {
      case Checkpoint(_, _) if now == null || now.isBefore(record.body.identity.updatedAt) =>
        Unchanged(InvalidTransition)
      case Checkpoint(_, _) if record.body.identity.revision == Long.MaxValue =>
        Unchanged(RevisionExhausted)
      case other => other
    }
  }

  private def _future(value: Option[Instant], now: Instant): Boolean =
    value.exists(_.isAfter(now))

  private def _terminal(status: DurableJobLifecycleStatus): Boolean =
    status match {
      case DurableJobLifecycleStatus.Succeeded | DurableJobLifecycleStatus.Failed |
          DurableJobLifecycleStatus.Cancelled => true
      case DurableJobLifecycleStatus.Submitted | DurableJobLifecycleStatus.Running |
          DurableJobLifecycleStatus.Suspended => false
    }
}
