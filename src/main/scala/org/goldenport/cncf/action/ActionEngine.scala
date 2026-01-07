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

/*
 * @since   Apr. 11, 2025
 *  version Apr. 15, 2025
 *  version Dec. 21, 2025
 *  version Jan.  1, 2026
 *  version Jan.  2, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class ActionEngine(
  config: ActionEngine.Config,
  authorizationEngine: AuthorizationEngine
) {

  def createActionCall(
    action: Action
  ): ActionCall = {
    val ec = create_execution_context()
    val correlationid = None
    val core = ActionCall.Core(action, ec, correlationid)
    action.createCall(core)
  }

  protected final def create_execution_context(): ExecutionContext = {
    ExecutionContext.create() // TODO
  }

  def run( // unused?
    call: ActionCall
  ): Consequence[OperationResponse] =
    Consequence {
      val ec = call.executionContext
      observe_enter(call)
      val r = call.execute().take
      observe_leave(call, Consequence.Success(r))
      r
    }

  def execute(
    call: ActionCall
  ): Consequence[OperationResponse] = {
    val ec = call.executionContext

    val authresult: Consequence[Unit] =
      Consequence {
        security_authorize(call, ec)
        ()
      }

    authresult.flatMap { _ =>
      Consequence run {
        // Observation hooks apply only to executed actions.
        observe_enter(call)
        try {
          val r = call.execute()
          ec.runtime.commit()
          observe_leave(call, r)
          r
        } catch {
          case e: Throwable =>
            ec.runtime.abort()
            observe_leave(call, Consequence.Failure(org.goldenport.Conclusion.from(e)))
            throw e
        } finally {
          ec.runtime.dispose()
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
          Consequence.failure(reason)
        }
    }

  def toExecutionContext(p: String): Consequence[ExecutionContext] = ???

  def toExecutionContext(p: Array[Byte]): Consequence[ExecutionContext] = ???

  protected def observe_enter(
    call: ActionCall
  ): Unit = {
    // Execution observation hook (not persisted).
    _log_backend_("enter", Some(call.action.name), "", None)
    observe_info("Action started", call)
  }

  protected def observe_leave(
    call: ActionCall,
    result: Consequence[OperationResponse]
  ): Unit = {
    // Execution observation hook (not persisted).
    result match {
      case Consequence.Success(_) =>
        _log_backend_("leave", Some(call.action.name), "", None)
        observe_info("Action completed successfully", call)
      case Consequence.Failure(conclusion) =>
        _log_backend_("error", Some(call.action.name), "", None)
        val message = s"Action failed: ${conclusion.message}"
        observe_error(message, conclusion.getException, call)
    }
  }

  protected def security_authorize(
    call: ActionCall,
    ec: ExecutionContext
  ): Unit = {
    // Phase 1-b: authorization/accesses are not applied
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
    _log_backend_(level, actionname, text, cause)
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

  private def _log_backend_(
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
