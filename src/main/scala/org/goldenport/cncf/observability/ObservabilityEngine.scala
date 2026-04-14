package org.goldenport.cncf.observability

import java.util.Locale
import java.time.Instant

import org.goldenport.Conclusion
import org.goldenport.http.HttpRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.cncf.context.{ObservabilityContext, ScopeContext}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.observation.calltree.CallTree

/*
 * @since   Jan.  7, 2026
 *  version Jan. 29, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final case class OperationContext(
  operationFqn: String
)

object ObservabilityEngine {
  final case class ExecutionHistoryFilter(
    operationContains: Option[String] = None
  ) {
    def matches(entry: ExecutionHistoryEntry): Boolean =
      operationContains.forall(entry.operation.contains)
  }

  final case class ExecutionHistoryConfig(
    recentLimit: Int = 100,
    filteredLimit: Int = 10000,
    filters: Vector[ExecutionHistoryFilter] = Vector.empty
  ) {
    def shouldRetainFiltered(entry: ExecutionHistoryEntry): Boolean =
      filters.exists(_.matches(entry))
  }

  final case class ExecutionHistoryEntry(
    id: Long,
    operation: String,
    parameters: Record,
    parametersText: String,
    outcome: String,
    resultType: String,
    resultSummary: String,
    capturedAt: Instant,
    calltree: Option[CallTree]
  ) {
    def toRecord: Record =
      Record.createFull(
        Vector(
        "id" -> id,
        "operation" -> operation,
        "parameters" -> parameters,
        "parameters_text" -> parametersText,
        "outcome" -> outcome,
        "result_type" -> resultType,
        "result_summary" -> resultSummary,
        "captured_at" -> capturedAt.toString
        )
      ) ++ calltree.map(tree => Record.data("calltree" -> _calltree_records(tree.toRecord))).getOrElse(Record.empty)

    def calltreeRecord: Record =
      Record.createFull(
        Vector(
        "id" -> id,
        "operation" -> operation,
        "parameters" -> parameters,
        "parameters_text" -> parametersText,
        "outcome" -> outcome,
        "result_type" -> resultType,
        "result_summary" -> resultSummary,
        "captured_at" -> capturedAt.toString,
        "calltree" -> calltree.map(tree => _calltree_records(tree.toRecord)).getOrElse(Vector.empty)
        )
      )

    private def _calltree_records(record: Record): Vector[Any] =
      record.asMap.toVector
        .sortBy { case (k, _) => scala.util.Try(k.toString.toInt).toOption.getOrElse(Int.MaxValue) }
        .map(_._2)
  }

  @volatile private var _execution_history_config: ExecutionHistoryConfig = ExecutionHistoryConfig()
  private var _execution_history_sequence: Long = 0L
  private var _recent_execution_history: Vector[ExecutionHistoryEntry] = Vector.empty
  private var _filtered_execution_history: Vector[ExecutionHistoryEntry] = Vector.empty

  def executionHistoryConfig: ExecutionHistoryConfig =
    _execution_history_config

  def updateExecutionHistoryConfig(
    config: ExecutionHistoryConfig
  ): Unit =
    synchronized {
      _execution_history_config = config
      _filtered_execution_history = _filtered_execution_history.filter(config.shouldRetainFiltered).takeRight(config.filteredLimit)
      _recent_execution_history = _recent_execution_history.takeRight(config.recentLimit)
    }

  def clearExecutionHistory(): Unit =
    synchronized {
      _execution_history_sequence = 0L
      _recent_execution_history = Vector.empty
      _filtered_execution_history = Vector.empty
    }

  def executionHistory: Vector[ExecutionHistoryEntry] =
    executionHistory(None)

  def executionHistory(
    operationContains: Option[String]
  ): Vector[ExecutionHistoryEntry] =
    synchronized {
      (_recent_execution_history ++ _filtered_execution_history)
        .groupBy(_.id)
        .values
        .flatMap(_.headOption)
        .toVector
        .filter(entry => operationContains.forall(entry.operation.contains))
        .sortBy(_.id)
    }

  def latestExecution: Option[ExecutionHistoryEntry] =
    synchronized {
      _recent_execution_history.lastOption.orElse(_filtered_execution_history.lastOption)
    }

  def recordActionExecution(
    operation: String,
    parameters: Record,
    parametersText: String,
    outcome: Either[Conclusion, OperationResponse],
    calltree: Option[CallTree]
  ): Unit =
    synchronized {
      if (!_is_execution_admin_operation(operation)) {
        _execution_history_sequence += 1
        val entry = ExecutionHistoryEntry(
          id = _execution_history_sequence,
          operation = operation,
          parameters = parameters,
          parametersText = parametersText,
          outcome = outcome.fold(_ => "failure", _ => "success"),
          resultType = outcome.fold(_ => "Conclusion", _result_type_),
          resultSummary = outcome.fold(_failure_summary_, _result_summary_),
          capturedAt = Instant.now(),
          calltree = calltree
        )
        val config = _execution_history_config
        _recent_execution_history = (_recent_execution_history :+ entry).takeRight(config.recentLimit)
        if (config.shouldRetainFiltered(entry))
          _filtered_execution_history = (_filtered_execution_history :+ entry).takeRight(config.filteredLimit)
      }
    }

  private def _is_execution_admin_operation(operation: String): Boolean =
    operation == "admin.execution.history" || operation == "admin.execution.calltree"

  private def _result_type_(response: OperationResponse): String =
    response.getClass.getSimpleName.stripSuffix("$")

  private def _result_summary_(response: OperationResponse): String =
    _truncate(response.show, 1000)

  private def _failure_summary_(conclusion: Conclusion): String =
    _truncate(conclusion.display, 1000)

  private def _truncate(s: String, limit: Int): String =
    if (s.length <= limit) s else s.take(limit) + "..."

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
          "error.kind" -> conclusion.observation.taxonomy.print,
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
      if (shouldEmit(level, scope, "org.goldenport.cncf", "ObservabilityEngine", backend)) {
        val prefix = s"event=$level scope=${scope.kind} name=${scope.name} "
        backend.log(level, s"$prefix$message")
      }
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
