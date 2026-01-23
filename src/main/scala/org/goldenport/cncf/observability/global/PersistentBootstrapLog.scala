package org.goldenport.cncf.observability.global

import org.goldenport.cncf.bootstrap.BootstrapLog
import org.goldenport.cncf.context.ScopeContext

/*
 * This helper routes Bootstrap-logged messages through GlobalObservability so that
 * bootstrap-phase diagnostics participate in the same buffering/visibility pipeline.
 * It intentionally delegates to GlobalObservability to avoid sprinkling direct stderr
 * calls outside the bootstrap stage.
 */
/*
 * @since   Jan. 23, 2026
 * @version Jan. 23, 2026
 * @author  ASAMI, Tomoharu
 */
object PersistentBootstrapLog {
  def forClass(clazz: Class[_], scope: ScopeContext = ObservabilityScopeDefaults.Bootstrap): BootstrapLog =
    new ObservabilityBootstrapLog(clazz, scope)

  private final class ObservabilityBootstrapLog(
    clazz: Class[_],
    scope: ScopeContext
  ) extends BootstrapLog {
    override def info(msg: String): Unit =
      GlobalObservability.observeInfo(scope, msg, clazz)

    override def warn(msg: String): Unit =
      GlobalObservability.observeWarn(scope, msg, clazz)

    override def error(msg: String): Unit =
      GlobalObservability.observeError(scope, msg, clazz)
  }
}
