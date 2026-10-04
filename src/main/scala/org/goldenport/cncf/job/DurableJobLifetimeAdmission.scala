package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Acknowledged native root admission. The binding retains provider/access
 * ownership only; persisted counters remain authoritative in the native ledger.
 *
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobLifetimeBinding(
  quota: DurableJobLifetimeQuota,
  access: DurableJobRecordAccess
)

private[job] final case class DurableJobLifetimeAdmissionResult(
  snapshot: DurableJobStoreSnapshot,
  binding: DurableJobLifetimeBinding
)

private[job] object DurableJobLifetimeAdmission {
  def admit(
    store: DurableJobStore,
    record: DurableJobRecord,
    access: DurableJobRecordAccess,
    taskCount: Int,
    noteCount: Long,
    policy: DurableJobLifetimeQuotaPolicy
  )(using ctx: ExecutionContext): Consequence[DurableJobLifetimeAdmissionResult] =
    DurableJobLifetimeQuotaPolicy.protect {
      val quota = store.lifetimeQuota(policy)
      for {
        lease <- quota.reserveRoot(record, access, taskCount, noteCount)
        snapshot <- store.create(record, access)
        _ <- quota.publishRoot(lease, access)
      } yield DurableJobLifetimeAdmissionResult(snapshot, DurableJobLifetimeBinding(quota, access))
    }
}
