package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.security.AuthorizationEngine

/*
 * @since   Apr. 11, 2025
 *  version Apr. 15, 2025
 *  version Dec. 21, 2025
 *  version Jan.  1, 2026
 * @version Jan.  2, 2026
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

  def toExecutionContext(p: String): Consequence[ExecutionContext] = ???

  def toExecutionContext(p: Array[Byte]): Consequence[ExecutionContext] = ???

  protected def observe_enter(
    call: ActionCall
  ): Unit = {
  }

  protected def observe_leave(
    call: ActionCall,
    result: Consequence[OperationResponse]
  ): Unit = {
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
}

object ActionEngine {
  case class Config()

  def create(): ActionEngine = ActionEngine(
    Config(),
    AuthorizationEngine.create() // TODO
  )
}
