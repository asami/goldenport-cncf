package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.{Action, ActionCall, Query, ResourceAccess}
import cats.{Id, ~>}
import org.goldenport.cncf.context.{ExecutionContext, RuntimeContext, ScopeKind, SystemContext}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.job.{JobEngine, JobId, JobResult, JobStatus, JobTask}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter

/*
 * @since   Jan.  3, 2026
 * @version Jan. 17, 2026
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

  def makeOperationRequest(request: Request): Consequence[OperationRequest] =
    _ping_action_(request).getOrElse(component.protocolLogic.makeOperationRequest(request))

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
    val base0 = ExecutionContext.createWithSystem(component.systemContext)
    val base1 = ExecutionContext.withSubsystemHttpDriver(
      base0,
      component.subsystem.flatMap(_.httpDriver)
    )
    val base2 = ExecutionContext.withComponentHttpDriver(
      base1,
      component.applicationConfig.httpDriver
    )
    val resolved = base2.resolve_http_driver
    val uow = component.unitOfWork.withHttpDriver(resolved)
    val runtime = new _ComponentRuntimeContext(uow)
    val withruntime = ExecutionContext.withRuntimeContext(base2, runtime)
    component.applicationConfig.applicationContext match {
      case Some(app) =>
        ExecutionContext.withApplicationContext(withruntime, app)
      case None =>
        withruntime
    }
  }

  private def _ping_action_(
    request: Request
  ): Option[Consequence[OperationRequest]] = {
    val isping =
      request.service.contains("admin.system") && request.operation == "ping"
    if (isping) {
      Some(Consequence.success(ComponentLogic.PingAction(request)))
    } else {
      None
    }
  }

  private final class _ComponentRuntimeContext(
    uow: UnitOfWork
  ) extends RuntimeContext {
    def unitOfWork: UnitOfWork = uow

    def unitOfWorkInterpreter[T]: (UnitOfWorkOp ~> Id) =
      new (UnitOfWorkOp ~> Id) {
        def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
          new UnitOfWorkInterpreter(uow).executeDirect(fa)
      }

    def unitOfWorkTryInterpreter[T]: (UnitOfWorkOp ~> scala.util.Try) =
      new (UnitOfWorkOp ~> scala.util.Try) {
        def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
          throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not available in component runtime")
      }

    def unitOfWorkEitherInterpreter[T](op: UnitOfWorkOp[T]): Either[Throwable, T] =
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

  private def _fallback_http_driver_(): HttpDriver =
    new HttpDriver {
      def get(path: String): HttpResponse =
        throw new UnsupportedOperationException(s"HttpDriver not configured: GET ${path}")

      def post(
        path: String,
        body: Option[String],
        headers: Map[String, String]
      ): HttpResponse =
        throw new UnsupportedOperationException(s"HttpDriver not configured: POST ${path}")
    }
}

object ComponentLogic {
  final case class PingAction(request: Request) extends Query() {
//    def name = "ping"

    def createCall(core: ActionCall.Core): ActionCall =
      PingActionCall(core)
  }

  final case class PingActionCall(
    core: ActionCall.Core
  ) extends ActionCall {
    override def action: Action = core.action
    def execute(): Consequence[OperationResponse] = {
      val info = RuntimeMetadata.fromSystem(core.executionContext.system)
      Consequence.success(OperationResponse.Scalar(RuntimeMetadata.format(info)))
    }
  }
}
