package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.metrics.RuntimeMetricPoint

/*
 * Closed diagnostics over completed native durable-operation results.
 *
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobOperationalMetrics {
  private val _counts = Array.fill[Long](59)(0L)

  def observeCreate[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(0)
      case Consequence.Failure(_) => _record(1)
    }
    result
  }

  def observeCheckpoint[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(9)
      case Consequence.Failure(_) => _record(10)
    }
    result
  }

  def observeStorageSnapshot[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(11)
      case Consequence.Failure(_) => _record(12)
    }
    result
  }

  def observePayloadSnapshot[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(13)
      case Consequence.Failure(_) => _record(14)
    }
    result
  }

  def observePayloadPut[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(15)
      case Consequence.Failure(_) => _record(16)
    }
    result
  }

  def observePayloadPrepare[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(17)
      case Consequence.Failure(_) => _record(18)
    }
    result
  }

  def observePayloadDelete[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(19)
      case Consequence.Failure(_) => _record(20)
    }
    result
  }

  def observeMaintenanceSweep[A](result: Consequence[A]): Consequence[A] = {
    result match {
      case Consequence.Success(_) => _record(57)
      case Consequence.Failure(_) => _record(58)
    }
    result
  }

  def observeLoad[A](result: Consequence[Option[A]]): Consequence[Option[A]] = {
    result match {
      case Consequence.Success(Some(_)) => _record(2)
      case Consequence.Success(None) => _record(3)
      case Consequence.Failure(_) => _record(4)
    }
    result
  }

  def observeStartup(result: DurableJobStoreStartupRecoveryLoad): DurableJobStoreStartupRecoveryLoad = {
    result match {
      case DurableJobStoreStartupRecoveryLoad.Admitted(_) => _record(5)
      case DurableJobStoreStartupRecoveryLoad.Missing => _record(6)
      case DurableJobStoreStartupRecoveryLoad.Corrupt => _record(7)
      case DurableJobStoreStartupRecoveryLoad.Refused => _record(8)
      case null => ()
    }
    result
  }

  def observeRetentionSweep(result: Consequence[DurableJobRetentionReport]): Consequence[DurableJobRetentionReport] = {
    result match {
      case Consequence.Success(report) =>
        if (report != null && report.entries != null)
          report.entries.foreach(entry => if (entry != null) observeRetention(entry.facet))
        _record(21)
      case Consequence.Failure(_) => _record(22)
    }
    result
  }

  def observeRetention(result: DurableJobRetentionFacet): DurableJobRetentionFacet = {
    result match {
      case DurableJobRetentionFacet.Kept => _record(23)
      case DurableJobRetentionFacet.NotDue => _record(24)
      case DurableJobRetentionFacet.Held => _record(25)
      case DurableJobRetentionFacet.NotTerminal => _record(26)
      case DurableJobRetentionFacet.AlreadyExpired => _record(27)
      case DurableJobRetentionFacet.AlreadyDeleted => _record(28)
      case DurableJobRetentionFacet.Tombstoned => _record(29)
      case DurableJobRetentionFacet.Expired => _record(30)
      case DurableJobRetentionFacet.Deleted => _record(31)
      case DurableJobRetentionFacet.HoldApplied => _record(32)
      case DurableJobRetentionFacet.HoldReleased => _record(33)
      case DurableJobRetentionFacet.Missing => _record(34)
      case DurableJobRetentionFacet.Corrupt => _record(35)
      case DurableJobRetentionFacet.Refused => _record(36)
      case DurableJobRetentionFacet.Stale => _record(37)
      case DurableJobRetentionFacet.InvalidTransition => _record(38)
      case DurableJobRetentionFacet.RevisionExhausted => _record(39)
      case DurableJobRetentionFacet.WriteRefused => _record(40)
      case null => ()
    }
    result
  }

  def observeCleanup(result: DurableJobCleanupFacet): DurableJobCleanupFacet = {
    result match {
      case DurableJobCleanupFacet.Tombstoned => _record(41)
      case DurableJobCleanupFacet.AlreadyTombstoned => _record(42)
      case DurableJobCleanupFacet.NotDeleted => _record(43)
      case DurableJobCleanupFacet.Held => _record(44)
      case DurableJobCleanupFacet.NotDue => _record(45)
      case DurableJobCleanupFacet.NotTerminal => _record(46)
      case DurableJobCleanupFacet.Missing => _record(47)
      case DurableJobCleanupFacet.Corrupt => _record(48)
      case DurableJobCleanupFacet.Refused => _record(49)
      case DurableJobCleanupFacet.Stale => _record(50)
      case DurableJobCleanupFacet.RevisionExhausted => _record(51)
      case DurableJobCleanupFacet.InvalidTime => _record(52)
      case DurableJobCleanupFacet.OwnershipRefused => _record(53)
      case DurableJobCleanupFacet.ResourceBoundRefused => _record(54)
      case DurableJobCleanupFacet.PayloadDeleteRefused => _record(55)
      case DurableJobCleanupFacet.CheckpointRefused => _record(56)
      case null => ()
    }
    result
  }

  private def _record(index: Int): Unit = synchronized {
    _counts(index) = JobOperationalMetrics.saturatingIncrement(_counts(index))
  }

  def snapshot(): Vector[RuntimeMetricPoint] = synchronized {
    _counts.indices.iterator.filter(_counts(_) > 0L).map { index =>
      val (operation, outcome, error) = DurableJobOperationalMetrics._cells(index)
      val count = _counts(index)
      RuntimeMetricPoint("job.durable.operation", "calls",
        Map("operation" -> operation, "outcome" -> outcome), count,
        errorCount = if (error) count else 0L)
    }.toVector
  }
}

private[job] object DurableJobOperationalMetrics {
  private val _cells = Vector(
    ("create", "succeeded", false),
    ("create", "failed", true),
    ("load", "found", false),
    ("load", "missing", false),
    ("load", "failed", true),
    ("startup", "admitted", false),
    ("startup", "missing", false),
    ("startup", "corrupt", true),
    ("startup", "refused", true),
    ("checkpoint", "succeeded", false),
    ("checkpoint", "failed", true),
    ("storage_snapshot", "succeeded", false),
    ("storage_snapshot", "failed", true),
    ("payload_snapshot", "succeeded", false),
    ("payload_snapshot", "failed", true),
    ("payload_put", "succeeded", false),
    ("payload_put", "failed", true),
    ("payload_prepare", "succeeded", false),
    ("payload_prepare", "failed", true),
    ("payload_delete", "succeeded", false),
    ("payload_delete", "failed", true),
    ("retention_sweep", "returned", false),
    ("retention_sweep", "failed", true),
    ("retention", "kept", false),
    ("retention", "not_due", false),
    ("retention", "held", false),
    ("retention", "not_terminal", false),
    ("retention", "already_expired", false),
    ("retention", "already_deleted", false),
    ("retention", "tombstoned", false),
    ("retention", "expired", false),
    ("retention", "deleted", false),
    ("retention", "hold_applied", false),
    ("retention", "hold_released", false),
    ("retention", "missing", true),
    ("retention", "corrupt", true),
    ("retention", "refused", true),
    ("retention", "stale", true),
    ("retention", "invalid_transition", true),
    ("retention", "revision_exhausted", true),
    ("retention", "write_refused", true),
    ("cleanup", "tombstoned", false),
    ("cleanup", "already_tombstoned", false),
    ("cleanup", "not_deleted", false),
    ("cleanup", "held", false),
    ("cleanup", "not_due", false),
    ("cleanup", "not_terminal", false),
    ("cleanup", "missing", true),
    ("cleanup", "corrupt", true),
    ("cleanup", "refused", true),
    ("cleanup", "stale", true),
    ("cleanup", "revision_exhausted", true),
    ("cleanup", "invalid_time", true),
    ("cleanup", "ownership_refused", true),
    ("cleanup", "resource_bound_refused", true),
    ("cleanup", "payload_delete_refused", true),
    ("cleanup", "checkpoint_refused", true),
    ("maintenance_sweep", "returned", false),
    ("maintenance_sweep", "failed", true)
  )
}
