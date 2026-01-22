package org.goldenport.cncf.observability

import java.util.Locale

import org.goldenport.Conclusion
import org.goldenport.http.HttpRequest
import org.goldenport.record.Record
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}

/*
 * @since   Jan.  7, 2026
 * @version Jan. 23, 2026
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

  def emitDebug(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("debug", context, scope, name, attributes, None)

  def emitTrace(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("trace", context, scope, name, attributes, None)

  def emitError(
    context: ObservabilityContext,
    scope: ScopeContext,
    name: String,
    attributes: Record
  ): Unit =
    _emit("error", context, scope, name, attributes, None)

  private var _policy: VisibilityPolicy = VisibilityPolicy()

  // TODO Phase 2.85: configure visibility policy from --log-* CLI flags and runtime config.
  def visibilityPolicy: VisibilityPolicy = _policy

  def updateVisibilityPolicy(policy: VisibilityPolicy): Unit =
    _policy = policy

  def shouldEmit(
    level: String,
    scope: ScopeContext,
    packageName: String,
    className: String,
    backend: LogBackend
  ): Boolean =
    _policy.allows(level, scope.name, packageName, className, backend)

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
    val traceid = s"traceId=${context.traceId.value}"
    val correlationid =
      context.correlationId.map(id => s"correlationId=${id.value}")
    val parts = traceid +: correlationid.toVector
    if (parts.isEmpty) name else s"$name ${parts.mkString("[", " ", "]")}"
  }
}

/**
 * Placeholder for the future visibility policy that responds to --log-* CLI flags and config values.
 */
enum LogLevel(val priority: Int) {
  case Error extends LogLevel(50)
  case Warn extends LogLevel(40)
  case Info extends LogLevel(30)
  case Debug extends LogLevel(20)
  case Trace extends LogLevel(10)
}

object LogLevel {
  private val _locale: Locale = Locale.ROOT

  def from(value: String): Option[LogLevel] =
    Option(value)
      .map(_.trim.toLowerCase(_locale))
      .flatMap {
        case "error"  => Some(LogLevel.Error)
        case "warn" | "warning" => Some(LogLevel.Warn)
        case "info"   => Some(LogLevel.Info)
        case "debug"  => Some(LogLevel.Debug)
        case "trace"  => Some(LogLevel.Trace)
        case _        => None
      }
}

final case class VisibilityPolicy(
  minLevel: LogLevel = LogLevel.Info,
  scopes: Option[Set[String]] = None,
  packages: Option[Set[String]] = None,
  classes: Option[Set[String]] = None,
  backend: Option[String] = None
) {
  def allows(
    levelValue: String,
    scope: String,
    packageName: String,
    className: String,
    backendValue: LogBackend
  ): Boolean = {
    val candidate = LogLevel.from(levelValue).getOrElse(LogLevel.Info)
    candidate.priority >= minLevel.priority
  }
}

object VisibilityPolicy {
  val AllowAll: VisibilityPolicy = VisibilityPolicy()
}
