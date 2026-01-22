package org.goldenport.cncf.observability.global

import org.goldenport.cncf.context.ScopeContext

/*
 * @since   Jan. 24, 2026
 * @version Jan. 24, 2026
 * @author  ASAMI, Tomoharu
 */
trait GlobalObservable {
  protected def observe_scope: ScopeContext =
    ObservabilityScopeDefaults.Subsystem

  private val _observe_class: Class[_] =
    this.getClass

  final protected def observe_error(msg: String): Unit =
    GlobalObservability.observeError(observe_scope, msg, _observe_class)

  final protected def observe_warn(msg: String): Unit =
    GlobalObservability.observeWarn(observe_scope, msg, _observe_class)

  final protected def observe_info(msg: String): Unit =
    GlobalObservability.observeInfo(observe_scope, msg, _observe_class)

  final protected def observe_debug(msg: String): Unit =
    GlobalObservability.observeDebug(observe_scope, msg, _observe_class)

  final protected def observe_trace(msg: String): Unit =
    GlobalObservability.observeTrace(observe_scope, msg, _observe_class)
}
