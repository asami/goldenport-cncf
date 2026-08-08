package org.goldenport.cncf.component

import org.goldenport.cncf.assembly.{AssemblyReport, AssemblyWarning}

/*
 * Runtime observer for bounded Component identity compatibility notices.
 *
 * @since   Aug.  8, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentIdentityCompatibilityObserver {
  def observeDeferredRelease(
    report: AssemblyReport,
    entry: ComponentIdentityDeferredReleaseEntry
  ): Unit =
    if (report != null && entry != null)
      report.addWarning(
        AssemblyWarning(
          kind = "component-identity-deferred-release",
          severity = "warning",
          componentName = entry.componentid.name,
          reason = Some("exact-deferred-release"),
          message =
            s"exact deferred Component release admitted: canonical=${entry.componentid.name}; release=${entry.release}; legacy-artifact=${entry.legacyartifact}; migration-owner=${entry.migrationowner}; reason=exact-deferred-release"
        )
      )

  def observe(
    report: AssemblyReport,
    notices: Vector[ComponentIdentityCompatibilityAdapter.Notice]
  ): Unit =
    if (report != null)
      notices.filter(_ != null).distinct.foreach(notice => observe(report, notice))

  def observe(
    report: AssemblyReport,
    notice: ComponentIdentityCompatibilityAdapter.Notice
  ): Unit =
    if (report != null && notice != null)
      report.addWarning(
        AssemblyWarning(
          kind = "component-identity-compatibility",
          severity = "warning",
          componentName = notice.componentid.name,
          reason = Some(
            s"surface=${notice.surface.code}; alias-kind=${notice.aliaskind.code}; alias=${notice.alias}; canonical=${notice.componentid.name}"
          ),
          message = notice.message
        )
      )
}
