package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.{Action, ActionCall}
import cats.{Id, ~>}
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, ScopeKind, SystemContext}
import org.goldenport.cncf.job.{JobEngine, JobId, JobResult, JobStatus, JobTask}
import org.goldenport.cncf.unitofwork.UnitOfWork

/*
 * @since   Jan.  3, 2026
 * @version Jan.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * ComponentLogic owns the system-scoped context and produces action-scoped ExecutionContext.
 */
case class ComponentLogic(
  component: Component,
  system: SystemContext = SystemContext.empty
) {
  def jobEngine: JobEngine = component.jobEngine

  def makeOperationRequest(args: Array[String]): Consequence[OperationRequest] =
    component.protocolLogic.makeOperationRequest(args)

  def makeOperationRequest(request: org.goldenport.protocol.Request): Consequence[OperationRequest] =
    component.protocolLogic.makeOperationRequest(request)

  def makeStringOperationResponse(res: OperationResponse): Consequence[String] =
    component.protocolLogic.makeStringOperationResponse(res)

  def createActionCall(action: Action): ActionCall = {
    val ctx = _execution_context()
    // TODO: action scope should be derived from service scope once available.
    val actionscope = component.scopeContext.createChildScope(ScopeKind.Action, action.name)
    val _ = actionscope
    val core = ActionCall.Core(action, ctx, ctx.observability.correlationId)
    action.createCall(core)
  }

  def execute(ac: ActionCall): Consequence[OperationResponse] = // ActionResponse
    component.actionEngine.execute(ac)

  def submitJob(tasks: List[JobTask], ctx: ExecutionContext): JobId =
    component.jobEngine.submit(tasks, ctx)

  def getJobStatus(jobId: JobId): Option[JobStatus] =
    component.jobEngine.getStatus(jobId)

  def getJobResult(jobId: JobId): Option[JobResult] =
    component.jobEngine.getResult(jobId)

  private def _execution_context(): ExecutionContext = {
    val base = ExecutionContext.createWithSystem(system)
    val runtime = new _ComponentRuntimeContext(component.unitOfWork)
    val withruntime = ExecutionContext.withRuntimeContext(base, runtime)
    component.applicationConfig.applicationContext match {
      case Some(app) =>
        ExecutionContext.withApplicationContext(withruntime, app)
      case None =>
        withruntime
    }
  }

  private final class _ComponentRuntimeContext(
    uow: UnitOfWork
  ) extends RuntimeContext {
    def unitOfWork: UnitOfWork = uow

    def unitOfWorkInterpreter[T]: (UnitOfWork.UnitOfWorkOp ~> Id) =
      new (UnitOfWork.UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWork.UnitOfWorkOp[A]): Id[A] =
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not available in component runtime")
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWork.UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWork.UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWork.UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not available in component runtime")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWork.UnitOfWorkOp[T]): Either[Throwable, T] =
      Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not available in component runtime"))

    def commit(): Unit = {
      unitOfWork.commit()
      ()
    }

    def abort(): Unit = {
      unitOfWork.rollback()
      ()
    }

    def dispose(): Unit = {}

    def toToken: String = "component-runtime-context"
  }
}
