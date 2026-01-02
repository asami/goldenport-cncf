package org.goldenport.cncf.action

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.component.ComponentActionEntry
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext}
import org.goldenport.cncf.security.AuthorizationDecision
import org.goldenport.cncf.security.AuthorizationEngine

/*
 * @since   Apr. 11, 2025
 *  version Apr. 15, 2025
 *  version Dec. 21, 2025
 * @version Jan.  1, 2026
 * @author  ASAMI, Tomoharu
 */
trait ActionExecutor {
  def execute(
    entry: ComponentActionEntry,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse]
}

class DefaultActionExecutor(
  builder: ActionCallBuilder
) extends ActionExecutor {

  override def execute(
    entry: ComponentActionEntry,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse] = {
    builder.build(entry.opdef, request, executionContext, correlationId).flatMap { basecall =>
      val call = ActionLogicCall(basecall, entry.logic)
      call.apply(call.request)
    }
  }
}

class EngineActionExecutor(
  engine: Engine,
  builder: ActionCallBuilder
) extends ActionExecutor {

  override def execute(
    entry: ComponentActionEntry,
    request: Request,
    executionContext: ExecutionContext,
    correlationId: Option[CorrelationId]
  ): Consequence[OperationResponse] = {
    builder.build(entry.opdef, request, executionContext, correlationId).flatMap { basecall =>
      val call = ActionLogicCall(basecall, entry.logic)
      engine.execute(call)
    }
  }
}

final case class ActionLogicCall(
  base: ActionCall,
  logic: ActionLogic
) extends ActionCall {
  def action: Action = base.action
  def executionContext: ExecutionContext = base.executionContext
  def correlationId: Option[CorrelationId] = base.correlationId
  def request = base.request
  def accesses: Seq[ResourceAccess] = base.accesses

  def apply(req: org.goldenport.protocol.operation.OperationRequest): Consequence[OperationResponse] =
    logic.execute(this)
}

abstract class ActionLogic {
  def execute(
    call: ActionCall
  ): Consequence[OperationResponse]
}

class Engine(
  authorizationEngine: AuthorizationEngine
) {
  def run(
    call: ActionCall
  ): Consequence[OperationResponse] =
    Consequence {
      val ec = call.executionContext
      observe_enter(call)
      val r = call().take
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
          val r = call()
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
    call.accesses.foreach { access =>
      val decision =
        authorizationEngine.authorize(
          ec,
          access.resource,
          access.action
        )
      observe_authorization(call, decision)
    }
  }

  protected def observe_authorization(
    call: ActionCall,
    decision: AuthorizationDecision
  ): Unit = {
  }
}

object Engine {
  def create(
    authorizationEngine: AuthorizationEngine
  ): Engine =
    new Engine(authorizationEngine)
}
