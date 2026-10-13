package org.goldenport.cncf.workflow

/**
 * Host-selected issue contract. Never selected by a public result payload.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[workflow] enum WorkflowIssuedContinuationKind {
  case WorkOrder, Decision
}
