package org.goldenport.cncf.observability

import org.goldenport.record.Record
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext}
import org.goldenport.cncf.log.LogBackendHolder

/*
 * @since   Jan.  7, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object ObservabilityEngine {
  def emitInfo(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("info", context, scope, name, attributes, None)

  def emitWarn(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("warn", context, scope, name, attributes, None)

  def emitError(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("error", context, scope, name, attributes, None)

  private def _emit(
    level: String,
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record,
    cause: Option[Throwable]
  ): Unit = {
    val text = _message(context, name)
    _log_backend_(level, scope, text, attributes)
    val _ = cause
  }

  private def _log_backend_(
    level: String,
    scope: ScopeContext,
    message: String,
    attributes: Record
  ): Unit = {
    val _ = attributes
    LogBackendHolder.backend.foreach { backend =>
      val prefix = s"event=$level scope=${scope.kind} name=${scope.name} "
      backend.log(level, s"$prefix$message")
    }
  }

  private def _message(
    context: ObservabilityContext,
    name: String
  ): String = {
    val traceid = Option(context.traceId.value).map(t => s"traceId=$t")
    val correlationid =
      context.correlationId.map(id => s"correlationId=${id.value}")
    val parts = Vector(traceid, correlationid).flatten
    if (parts.isEmpty) name else s"$name ${parts.mkString("[", " ", "]")}"
  }
}
