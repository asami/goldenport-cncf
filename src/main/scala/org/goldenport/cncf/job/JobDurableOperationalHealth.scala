package org.goldenport.cncf.job

/*
 * Pure public availability facets; no backend or authorization values escape.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
enum JobDurableOperationalState {
  case Unconfigured, Starting, Ready, Degraded, Closing, Closed
}

final case class JobDurableOperationalHealth(
  state: JobDurableOperationalState,
  configured: Boolean,
  storageReady: Boolean,
  startupReady: Boolean,
  maintenanceRegistered: Boolean,
  maintenanceHealthy: Boolean,
  executionAvailable: Boolean
)
