package org.goldenport.cncf.job

/*
 * Ordinal-only bounded operational evidence; no native authority escapes.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
enum JobDurableIntegrityStatus {
  case Healthy, Inactive, Missing, Corrupt, AccessRefused, MigrationRequired,
    LifetimeRefused, PayloadRefused, ResourceBoundRefused, Stale, Unavailable
}

final case class JobDurableIntegrityEntry(ordinal: Long, status: JobDurableIntegrityStatus)

final case class JobDurableIntegrityReport(
  entries: Vector[JobDurableIntegrityEntry],
  physicalBytesRead: Long
) {
  def isHealthy: Boolean = entries.nonEmpty && entries.forall(_.status == JobDurableIntegrityStatus.Healthy)
}
