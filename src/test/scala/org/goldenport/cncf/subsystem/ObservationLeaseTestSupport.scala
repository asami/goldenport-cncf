package org.goldenport.cncf.subsystem

/**
 * Test-only access to the same revocation operation used by SystemNode.
 * @since   Sep. 30, 2026
 * @version Oct.  8, 2026
 */
private[cncf] object ObservationLeaseTestSupport {
  def revoke(lease: SystemNodeResourceLease): Unit = lease.revoke()
}
