package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import java.time.Instant
import org.goldenport.cncf.context.ExecutionContextId
import org.goldenport.cncf.event.ActionEvent
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.security.AuthorizationEngine
import org.goldenport.cncf.security.{Action as SecurityAction, SecuredResource}
import org.goldenport.cncf.log.LogBackendHolder
import org.goldenport.cncf.observability.{CallTreeContext, CallTreeValueSummary, DiagnosticPayloadSummary, ObservabilityEngine, OperationContext}
import org.goldenport.cncf.context.{ScopeContext, ScopeKind}
import org.goldenport.cncf.context.GlobalRuntimeContext
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.config.ResolvedParameter
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.schema.DataConfidentiality

/*
 * @since   Apr. 11, 2025
 *  version Apr. 15, 2025
 *  version Dec. 21, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 *  version Jan. 29, 2026
 *  version Feb.  6, 2026
 *  version Mar. 13, 2026
 *  version Apr. 25, 2026
 * @version May. 11, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngine(
  config: ActionEngine.Config,
  authorizationEngine: AuthorizationEngine
) {

  // private def createActionCall(
  //   action: Action
  // ): ActionCall = {
  //   val ec = create_execution_context()
  //   val correlationid = None
  //   val core = ActionCall.Core(action, ec, correlationid)
  //   action.createCall(core)
  // }

  // protected final def create_execution_context(): ExecutionContext = {
  //   ExecutionContext.create() // TODO
  // }

  // def run( // unused?
  //   call: ActionCall
  // ): Consequence[OperationResponse] =
  //   Consequence {
  //     val ec = call.executionContext
  //     observe_enter(call)
  //     val r = call.execute().take
  //     observe_leave(call, Consequence.Success(r))
  //     r
  //   }

  def execute(
    call: ActionCall
  ): Consequence[OperationResponse] = {
    val ec = call.executionContext
    val runtime = ec.runtime
    val calltree = ec.observability.callTreeContext
    val label =
      if (call.action.name.nonEmpty) s"action:${call.action.name}"
      else "action"
    var executionOutcome: Option[Either[org.goldenport.Conclusion, OperationResponse]] = None

    val authresult: Consequence[Unit] =
      Consequence {
        security_authorize(call, ec)
        ()
      }

    authresult.recoverWith { conclusion =>
      _record_pre_execution_failure_calltree(call, ec, calltree, label, conclusion)
      Consequence.Failure(conclusion)
    }.flatMap { _ =>
      Consequence run {
        val params = _build_resolved_parameters(call)
        runtime.setResolvedParameters(params)
        val inputAttributes = _calltree_input_attributes(call, params)
        var leaveAttributes: Map[String, String] = Map.empty
        try {
          calltree.enter(label, inputAttributes)
          try {
            // Observation hooks apply only to executed actions.
            observe_enter(call)
            try {
              val r = call.execute()
              r match {
                case Consequence.Success(response) =>
                  leaveAttributes = _calltree_output_attributes(call, response) + ("outcome" -> "success")
                  executionOutcome = Some(Right(response))
                case Consequence.Failure(conclusion) =>
                  leaveAttributes = _calltree_error_attributes(conclusion) + ("outcome" -> "failure")
                  calltree.failure("io:error", conclusion.display, _calltree_error_attributes(conclusion) + ("calltree_kind" -> "io-error"))
                  executionOutcome = Some(Left(conclusion))
              }
              ec.runtime.commit()
              observe_leave(call, r)
              val _ = ObservabilityEngine.build( // TODO
                scope = ScopeContext(
                  kind = ScopeKind.Action,
                  name = call.action.name,
                  parent = None,
                  observabilityContext = ec.observability
                ),
                http = None,
                operation = Some(OperationContext(call.action.name)),
                outcome = r match {
                  case Consequence.Success(_) => Right(())
                  case Consequence.Failure(c) => Left(c)
                }
              )
              r
            } catch {
              case e: Throwable =>
                ec.runtime.abort()
                val conclusion = org.goldenport.Conclusion.from(e)
                leaveAttributes = _calltree_error_attributes(conclusion) + ("outcome" -> "failure")
                calltree.failure("io:error", conclusion.display, _calltree_error_attributes(conclusion) + ("calltree_kind" -> "io-error"))
                executionOutcome = Some(Left(conclusion))
                observe_leave(call, Consequence.Failure(conclusion))
                val _ = ObservabilityEngine.build( // TODO
                  scope = ScopeContext(
                    kind = ScopeKind.Action,
                    name = call.action.name,
                    parent = None,
                    observabilityContext = ec.observability
                  ),
                  http = None,
                  operation = Some(OperationContext(call.action.name)),
                  outcome = Left(conclusion)
                )
                throw e
            }
          } finally {
            try {
              ec.runtime.dispose()
            } finally {
              calltree.leave(leaveAttributes)
              val builtCallTree = calltree.build()
              if (ec.framework.inlineCallTree) {
                ec.runtime.noteExecutionContext(
                  ec.observability.sagaId,
                  ec.jobContext.jobId.map(_.value),
                  ec.jobContext.currentTask.orElse(ec.jobContext.taskId).map(_.value)
                )
                builtCallTree.foreach { tree =>
                  ec.runtime.noteInlineCallTree(
                    ObservabilityEngine.callTreeRecord(tree, ec.jobContext.jobId.map(_.value))
                  )
                }
              }
              executionOutcome.foreach { outcome =>
                RuntimeDashboardMetrics.recordActionCall(outcome.isLeft)
                ObservabilityEngine.recordActionExecution(
                  operation = call.action.name,
                  parameters = _sanitize_calltree_record(call.request.toRecord, call.fieldConfidentiality),
                  parametersText = _calltree_resolved_parameters_text(params, call.fieldConfidentiality),
                  outcome = outcome,
                  resultConfidentiality = call.resultFieldConfidentiality,
                  jobId = ec.jobContext.jobId.map(_.value),
                  calltree = builtCallTree
                )
              }
            }
          }
        } finally {
          runtime.clearResolvedParameters()
        }
      }
    }
  }

  def executeAuthorized(
    actionName: String,
    ec: ExecutionContext
  )(
    buildCall: => ActionCall
  ): Consequence[OperationResponse] =
    authorize_pre(actionName, ec) match {
      case AuthorizationDecision.Allow =>
        val call = buildCall
        execute(call)
      case AuthorizationDecision.Deny =>
        val reason = "authorization denied"
        val event = ActionEvent.authorizationFailed(
          ExecutionContextId.generate(),
          actionName,
          reason,
          Instant.now()
        )
        ec.runtime.unitOfWork.commit(Seq(event)).flatMap { _ =>
          Consequence.securityPermissionDenied(reason)
        }
    }

  def toExecutionContext(p: String): Consequence[ExecutionContext] =
    Consequence.RAISE.NotImplemented

  def toExecutionContext(p: Array[Byte]): Consequence[ExecutionContext] =
    Consequence.RAISE.NotImplemented

  protected def observe_enter(
    call: ActionCall
  ): Unit = {
    // Execution observation hook (not persisted).
    val params = _parameter_source_text(call).getOrElse("")
    _log_backend_("enter", Some(call.action.name), params, None)
    observe_info("Action started", call)
  }

  protected def observe_leave(
    call: ActionCall,
    result: Consequence[OperationResponse]
  ): Unit = {
    // Execution observation hook (not persisted).
    result match {
      case Consequence.Success(_) =>
        _log_backend_("leave", Some(call.action.name), "", None) // TODO
        observe_info("Action completed successfully", call)
      case Consequence.Failure(conclusion) =>
        _log_backend_("error", Some(call.action.name), "", None) // TODO
        val message = s"Action failed: ${conclusion.show}"
        observe_error(message, conclusion.getException, call)
    }
  }

  protected def security_authorize(
    call: ActionCall,
    ec: ExecutionContext
  ): Unit = {
    call.authorize()(using ec) match {
      case Consequence.Success(_) => ()
      case Consequence.Failure(conclusion) =>
        throw conclusion.getException.getOrElse(new org.goldenport.ConsequenceException(Consequence.Failure(conclusion)))
    }
  }

  protected def observe_authorization(
    call: ActionCall,
    decision: AuthorizationDecision
  ): Unit = {
  }

  protected def observe_fatal(
    message: String,
    cause: Option[Throwable] = None
  ): Unit =
    _observe("fatal", message, cause, None)

  protected def observe_error(
    message: String,
    cause: Option[Throwable] = None
  ): Unit =
    _observe("error", message, cause, None)

  protected def observe_warning(
    message: String
  ): Unit =
    _observe("warning", message, None, None)

  protected def observe_info(
    message: String
  ): Unit =
    _observe("info", message, None, None)

  protected def observe_debug(
    message: String
  ): Unit = {
    // TODO: make debug control configurable.
    val enabled = false
    if (enabled) {
      _observe("debug", message, None, None)
    }
  }

  protected def observe_trace(
    message: String
  ): Unit = {
    // TODO: make trace control configurable.
    val enabled = false
    if (enabled) {
      _observe("trace", message, None, None)
    }
  }

  protected def observe_info(
    message: String,
    call: ActionCall
  ): Unit =
    _observe("info", message, None, Some(call))

  protected def observe_error(
    message: String,
    cause: Option[Throwable],
    call: ActionCall
  ): Unit =
    _observe("error", message, cause, Some(call))

  private def _observe(
    level: String,
    message: String,
    cause: Option[Throwable],
    call: Option[ActionCall]
  ): Unit = {
    val ctx = _observation_context(call)
    val text = if (ctx.isEmpty) message else s"$message $ctx"
    val actionname = call.map(_.action.name)
    _log_backend_(level, actionname, text, cause) // TODO
  }

  private def _observation_context(
    call: Option[ActionCall]
  ): String = {
    val scope = call.map(_ => "action").getOrElse("operation")
    val actionname = call.map(_.action.name)
    val ec = call.map(_.executionContext)
    val traceid = ec.map(_.observability.traceId.value)
    val executionid = ec.flatMap(_.observability.correlationId.map(_.value))
    val parts = Vector(
      Some(s"scope=$scope"),
      actionname.map(n => s"action=$n"),
      traceid.map(t => s"traceId=$t"),
      executionid.map(e => s"executionId=$e")
    ).flatten
    if (parts.isEmpty) "" else parts.mkString("[", " ", "]")
  }

  private def _log_backend_( // TODO
    level: String,
    actionname: Option[String],
    message: String,
    cause: Option[Throwable]
  ): Unit = {
    LogBackendHolder.backend.foreach { backend =>
      val name = actionname.getOrElse("unknown")
      val prefix = s"event=$level scope=Action name=$name "
      val text = cause match {
        case Some(c) => s"$message cause=${c.getMessage}"
        case None => message
      }
      backend.log(level, s"$prefix$text")
    }
  }

  private def _build_resolved_parameters(
    call: ActionCall
  ): ResolvedParameters = {
    val runtimeparams = call.executionContext.runtime.resolvedParameters
    val parent =
      if (runtimeparams.hasLocalFramework("http"))
        Some(runtimeparams)
      else
        GlobalRuntimeContext.current.map(_.resolvedParameters)
    ResolvedParameters.forOperation(
      arguments = call.arguments,
      switches = call.switches,
      properties = call.properties,
      parent = parent
    )
  }

  private def _record_pre_execution_failure_calltree(
    call: ActionCall,
    ec: ExecutionContext,
    calltree: CallTreeContext,
    label: String,
    conclusion: org.goldenport.Conclusion
  ): Unit =
    if (calltree.isEnabled) {
      val params = _build_resolved_parameters(call)
      params.markAllLocalUsed()
      calltree.enter(label, _calltree_input_attributes(call, params))
      try {
        calltree.failure("io:error", conclusion.display, _calltree_error_attributes(conclusion) + ("calltree_kind" -> "io-error"))
      } finally {
        calltree.leave(_calltree_error_attributes(conclusion) + ("outcome" -> "failure"))
        if (ec.framework.inlineCallTree) {
          ec.runtime.noteExecutionContext(
            ec.observability.sagaId,
            ec.jobContext.jobId.map(_.value),
            ec.jobContext.currentTask.orElse(ec.jobContext.taskId).map(_.value)
          )
          calltree.build().foreach { tree =>
            ec.runtime.noteInlineCallTree(
              ObservabilityEngine.callTreeRecord(tree, ec.jobContext.jobId.map(_.value))
            )
          }
        }
        calltree.clear()
      }
    }

  private def _calltree_input_attributes(
    call: ActionCall,
    params: ResolvedParameters
  ): Map[String, String] = {
    val (request, web) = _calltree_parameter_records(params, call.fieldConfidentiality)
    Map(
      "calltree_kind" -> "action",
      "display_label" -> call.action.name,
      "component" -> call.request.component.getOrElse(""),
      "service" -> call.request.service.getOrElse(""),
      "operation" -> call.request.operation,
      "request" -> _calltree_record_summary_json(request, call.fieldConfidentiality),
      "web_parameters" -> _calltree_record_summary_json(web, call.fieldConfidentiality)
    ).filter(_._2.nonEmpty)
  }

  private def _calltree_output_attributes(
    call: ActionCall,
    response: OperationResponse
  ): Map[String, String] =
    _operation_response_summary_attributes(response, call.resultFieldConfidentiality)

  private def _calltree_error_attributes(
    conclusion: org.goldenport.Conclusion
  ): Map[String, String] =
    Map(
      "status" -> conclusion.status.webCode.code.toString,
      "error" -> _truncate_calltree_text(conclusion.display, 4000)
    )

  private def _calltree_record_text(
    record: Record
  ): String =
    _calltree_record_text(record, Map.empty)

  private def _calltree_record_text(
    record: Record,
    confidentiality: Map[String, DataConfidentiality]
  ): String =
    _truncate_calltree_text(_sanitize_calltree_record(record, confidentiality).print, 4000)

  private def _calltree_record_json(
    record: Record
  ): String =
    if (record.asMap.isEmpty)
      ""
    else
      _truncate_calltree_text(RecordEncoder.json(record), 8000)

  private def _calltree_record_summary_json(
    record: Record,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String =
    if (record.asMap.isEmpty)
      ""
    else
      _calltree_record_json(DiagnosticPayloadSummary.recordSummary(record, includeInline = true, confidentiality).toRecord)

  private def _operation_response_summary_attributes(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Map[String, String] =
    response match {
      case OperationResponse.RecordResponse(record) =>
        Map(
          "response_type" -> response.getClass.getSimpleName.stripSuffix("$"),
          "response" -> _calltree_record_json(CallTreeValueSummary.recordSummary(record, includeInline = true, confidentiality))
        )
      case _ =>
        val summary = CallTreeValueSummary.operationResponseSummary(response, confidentiality)
        Map(
          "response_type" -> response.getClass.getSimpleName.stripSuffix("$"),
          "response" -> _calltree_record_json(summary)
        )
    }

  private def _calltree_parameter_records(
    params: ResolvedParameters,
    confidentiality: Map[String, DataConfidentiality]
  ): (Record, Record) = {
    val entries = params.usedEntries
    val request = entries.filterNot(p => _is_web_calltree_key(p.key)).map(p => p.key -> _calltree_parameter_value(p, confidentiality))
    val web = entries.filter(p => _is_web_calltree_key(p.key)).map(p => p.key -> _calltree_parameter_value(p, confidentiality))
    (
      Record.dataAuto(request*),
      Record.dataAuto(web*)
    )
  }

  private def _calltree_parameter_value(
    param: ResolvedParameter,
    confidentiality: Map[String, DataConfidentiality]
  ): String =
    if (_is_sensitive_calltree_key(param.key, confidentiality))
      "***"
    else
      org.goldenport.cncf.config.ResolvedParameter.format_value(param.value)

  private def _is_web_calltree_key(
    key: String
  ): Boolean = {
    val normalized = key.trim.toLowerCase(java.util.Locale.ROOT).replace('-', '_')
    normalized == "accept" ||
      normalized == "accept_encoding" ||
      normalized == "accept_language" ||
      normalized == "authorization" ||
      normalized == "connection" ||
      normalized == "content_length" ||
      normalized == "content_type" ||
      normalized == "cookie" ||
      normalized == "host" ||
      normalized == "origin" ||
      normalized == "referer" ||
      normalized == "referrer" ||
      normalized == "upgrade_insecure_requests" ||
      normalized == "user_agent" ||
      normalized.startsWith("sec_") ||
      normalized.startsWith("x_textus_") ||
      normalized.startsWith("textus.debug.") ||
      normalized.startsWith("cncf.debug.")
  }

  private def _sanitize_calltree_record(
    record: Record,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Record =
    Record.dataAuto(
      record.fields
        .map(field => field.key -> _sanitize_calltree_value(field.key, field.value.single, confidentiality))*
    )

  private def _calltree_resolved_parameters_text(
    params: ResolvedParameters,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String = {
    val entries = params.usedEntries
    if (entries.isEmpty)
      ""
    else {
      val text = entries.map { param =>
        val value =
          if (_is_sensitive_calltree_key(param.key, confidentiality))
            "***"
          else
            org.goldenport.cncf.config.ResolvedParameter.format_value(param.value)
        s"${param.key}=${value}(source=${param.source.label})"
      }.mkString("params=", ", ", "")
      _truncate_calltree_text(text, 4000)
    }
  }

  private def _sanitize_calltree_value(
    key: String,
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    if (_is_sensitive_calltree_key(key, confidentiality))
      "***"
    else
      _sanitize_calltree_nested_value(value, confidentiality)

  private def _sanitize_calltree_nested_value(
    value: Any,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Any =
    value match {
      case r: Record =>
        _sanitize_calltree_record(r, confidentiality)
      case xs: Seq[?] =>
        xs.map(_sanitize_calltree_nested_value(_, confidentiality))
      case xs: Array[?] =>
        xs.toVector.map(_sanitize_calltree_nested_value(_, confidentiality))
      case m: scala.collection.Map[?, ?] =>
        m.toVector.map {
          case (k, v) =>
            val key = Option(k).map(_.toString).getOrElse("")
            key -> _sanitize_calltree_value(key, v, confidentiality)
        }.toMap
      case _ =>
        value
    }

  private def _is_sensitive_calltree_key(
    key: String,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): Boolean = {
    val normalized = _normalize_calltree_key(key)
    confidentiality.get(key)
      .orElse(confidentiality.find { case (k, _) => _normalize_calltree_key(k) == normalized }.map(_._2))
      .exists(_.shouldRedactByDefault) ||
    normalized.contains("password") ||
      normalized.contains("passwd") ||
      normalized.contains("secret") ||
      normalized.contains("token") ||
      normalized.contains("session") ||
      normalized.contains("authorization") ||
      normalized.contains("cookie") ||
      normalized.contains("credential") ||
      normalized.contains("apikey") ||
      normalized.contains("privatekey")
  }

  private def _normalize_calltree_key(key: String): String =
    Option(key).getOrElse("").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")

  private def _truncate_calltree_text(
    value: String,
    limit: Int
  ): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private def _parameter_source_text(
    call: ActionCall
  ): Option[String] =
    Option(_calltree_resolved_parameters_text(call.executionContext.runtime.resolvedParameters)).filter(_.nonEmpty)

  private def _operation_response_text(
    response: OperationResponse,
    confidentiality: Map[String, DataConfidentiality] = Map.empty
  ): String =
    response match {
      case OperationResponse.RecordResponse(record) =>
        _sanitize_calltree_record(record, confidentiality).print
      case _ =>
        _redact_sensitive_text(response.show)
    }

  private def _redact_sensitive_text(
    value: String
  ): String = {
    val sensitive = """password|passwd|secret|token|access[-_]?session[-_]?id|refresh[-_]?session[-_]?id|session[-_]?id|session|authorization|cookie|credential|api[-_]?key|private[-_]?key"""
    val jsonLike = s"""(?i)("(?:$sensitive)"\\s*:\\s*)"[^"]*"""".r
    val formLike = s"""(?i)(^|[?&\\s,;])($sensitive)(\\s*[=:]\\s*)([^&\\s,;]+)""".r
    val jsonRedacted = jsonLike.replaceAllIn(value, m => s"""${m.group(1)}"***"""")
    formLike.replaceAllIn(jsonRedacted, m => s"${m.group(1)}${m.group(2)}${m.group(3)}***")
  }

  protected def authorize_pre(
    actionName: String,
    ec: ExecutionContext
  ): AuthorizationDecision = {
    val resource = new SecuredResource {
      def securityLevel = ec.security.level
    }
    val action = SecurityAction(actionName)
    authorizationEngine.authorize(ec, resource, action)
  }
}

object ActionEngine {
  case class Config()

  def create(): ActionEngine = ActionEngine(
    Config(),
    AuthorizationEngine.create() // TODO
  )
}
