package org.goldenport.cncf.component

import org.goldenport.cncf.assembly.{AssemblyReport, AssemblyWarning}

/*
 * Runtime observer for retained Component identity compatibility notices.
 *
 * @since   Aug.  8, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cncf] object ComponentIdentityCompatibilityObserver {
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
