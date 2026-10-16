package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Trusted composition resolves only the current caller's raw durable grants.
 *
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] abstract class DurableJobCallerAccessResolver {
  def resolve(context: ExecutionContext): Consequence[DurableJobRecordAccess]
}

private[job] object DurableJobCallerAccessResolver {
  def refused[A]: Consequence[A] = Consequence.stateInvalid("durable job result query refused")

  def admitAccess(access: DurableJobRecordAccess): Consequence[DurableJobRecordAccess] =
    if (access == null || !_token(access.tenantId) || !_token(access.subjectId) ||
        access.scopes == null || !access.scopes.forall(_token)) refused
    else Consequence.success(access)

  private def _token(value: String): Boolean = value != null && value.trim.nonEmpty
}
