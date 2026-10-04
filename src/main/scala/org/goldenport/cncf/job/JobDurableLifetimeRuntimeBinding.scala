package org.goldenport.cncf.job

import org.goldenport.cncf.context.ExecutionContext

/*
 * State-owned admission handles and refusal bookkeeping. All access uses the
 * existing State monitor. Paid counters always come from authenticated quota.
 *
 * @since   Oct. 2, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class JobDurableLifetimeRuntimeBinding(
  val native: DurableJobLifetimeBinding,
  val context: ExecutionContext,
  val bridge: Option[DurableJobLifecycleWriteBridge],
  var scope: JobCancellationScope
) {
  var refused: Boolean = false
  var checkpointfailure: Option[DurableJobLifecycleWriteFailure] = None
  var cancellationSettled: Boolean = false
  private var _uncertain_claims = Set.empty[TaskId]

  def _has_uncertain_claim(taskid: TaskId): Boolean = _uncertain_claims.contains(taskid)

  def _remember_uncertain_claim(taskid: TaskId, maximum: Long): Unit = {
    refused = true
    if (_uncertain_claims.contains(taskid) || _uncertain_claims.size.toLong < maximum)
      _uncertain_claims += taskid
  }
}
