package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class JobLifetimePolicy(
  maxchargedtasks: Long = 8192L,
  maxhistorycredits: Long = 524288L,
  maxadmissions: Long = 16384L
) {
  def validate: Consequence[Unit] =
    if (maxchargedtasks < 1L || maxchargedtasks > 8192L ||
        maxhistorycredits < 1L || maxhistorycredits > 524288L ||
        maxadmissions < 1L || maxadmissions > 16384L)
      JobResourcePolicy.refused
    else Consequence.unit

  private[job] def _fits(value: JobLifetimeSnapshot): Boolean =
    value.chargedtasks >= 0L && value.chargedtasks <= maxchargedtasks &&
      value.historycredits >= 0L && value.historycredits <= maxhistorycredits &&
      value.admissions >= 0L && value.admissions <= maxadmissions && value.cancelcredits >= 0L
}

private[job] object JobLifetimePolicy {
  val default: JobLifetimePolicy = JobLifetimePolicy()
}

private[job] final case class JobLifetimeSnapshot(
  chargedtasks: Long,
  historycredits: Long,
  admissions: Long,
  cancelcredits: Long
)
