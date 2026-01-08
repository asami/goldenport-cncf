package org.goldenport.cncf.observability

import org.goldenport.Conclusion
import org.goldenport.http.HttpRequest
import org.goldenport.record.Record
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext}
import org.goldenport.cncf.log.LogBackendHolder

/*
 * @since   Jan.  7, 2026
 * @version Jan.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationContext(
  operationFqn: String
)

object ObservabilityEngine {
  def build(
    scope: ScopeContext,
    http: Option[HttpRequest],
    operation: Option[OperationContext],
    outcome: Either[Conclusion, Unit]
  ): Record = {
    val scopeRecord = Record.data(
      "scope.subsystem" -> scope.name,
      "scope.ingress" -> scope.kind.toString
    )
    val httpRecord = http.map { req =>
      Record.data(
        "http.method" -> req.method.toString,
        "http.path" -> req.path.asString
      )
    }.getOrElse(Record.empty)
    val operationRecord = operation.map { op =>
      Record.data(
        "operation.fqn" -> op.operationFqn
      )
    }.getOrElse(Record.empty)
    val outcomeRecord = outcome match {
      case Right(_) =>
        Record.data(
          "result.success" -> true
        )
      case Left(conclusion) =>
        Record.data(
          "result.success" -> false,
          "error.kind" -> conclusion.observation.causeKind.toString,
          "error.code" -> conclusion.status.webCode.code
        )
    }
    Record(
      scopeRecord.fields ++
        httpRecord.fields ++
        operationRecord.fields ++
        outcomeRecord.fields
    )
  }

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
