package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.cncf.action.{Action, ActionCall, Query, ResourceAccess}
import cats.{Id, ~>}
import org.goldenport.cncf.context.{ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeKind}
import org.goldenport.cncf.backend.collaborator.Collaborator
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.job.{JobEngine, JobId, JobResult, JobStatus, JobTask}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter

/*
 * @since   Jan.  3, 2026
 *  version Jan. 20, 2026
 * @version Feb.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * ComponentLogic owns the system-scoped context and produces action-scoped ExecutionContext.
 */
case class ComponentLogic(
  component: Component
  // system: SystemContext = SystemContext.empty
) {
  def jobEngine: JobEngine = component.jobEngine

  def makeOperationRequest(args: Array[String]): Consequence[OperationRequest] =
    component.protocolLogic.makeOperationRequest(args)

  def makeOperationRequest(request: Request): Consequence[OperationRequest] =
//    _ping_action_(request).getOrElse(component.protocolLogic.makeOperationRequest(request))
    component.protocolLogic.makeOperationRequest(request)

  def makeStringOperationResponse(res: OperationResponse): Consequence[String] =
    component.protocolLogic.makeStringOperationResponse(res)

  def createActionCall(action: Action): ActionCall = {
    val ctx0 = _execution_context()
    // TODO: action scope should be derived from service scope once available.
    val actionscope = component.scopeContext.createChildScope(ScopeKind.Action, action.name)
    val ctx = ctx0.withScope(actionscope)
    val collaborator = _get_collaborator
    val core = ActionCall.Core(
      action,
      ctx,
      Some(component),
      ctx.observability.correlationId
    )
    action.createCall(core)
  }

  private def _get_collaborator: Option[Collaborator] = component match {
    case m: CollaboratorComponent => Some(m.collaborator)
    case _ => None
  }

  def execute(ac: ActionCall): Consequence[OperationResponse] = // ActionResponse
    component.actionEngine.execute(ac)

  def submitJob(tasks: List[JobTask], ctx: ExecutionContext): JobId =
    component.jobEngine.submit(tasks, ctx)

  def getJobStatus(jobId: JobId): Option[JobStatus] =
    component.jobEngine.getStatus(jobId)

  def getJobResult(jobId: JobId): Option[JobResult] =
    component.jobEngine.getResult(jobId)

  def executionContext(): ExecutionContext =
    _execution_context()

  private def _execution_context(): ExecutionContext = {
    val driver = component.applicationConfig.httpDriver
      .orElse(component.subsystem.flatMap(_.httpDriver))
      .getOrElse(_fallback_http_driver_())
    val uow = component.unitOfWork.withHttpDriver(Some(driver))
    val runtime = _component_runtime_context(uow, driver)
    ExecutionContext.create(runtime)
  }

  // private def _ping_action_(
  //   request: Request
  // ): Option[Consequence[OperationRequest]] = {
  //   val isping =
  //     request.service.contains("admin.system") && request.operation == "ping"
  //   if (isping) {
  //     Some(Consequence.success(ComponentLogic.PingAction(request)))
  //   } else {
  //     None
  //   }
  // }

  private def _component_runtime_context(
    uow: UnitOfWork,
    driver: HttpDriver
  ): RuntimeContext = {
    val parent = component.scopeContext
    val core = RuntimeContext.core(
      name = "component-runtime",
      parent = Some(parent),
      observabilityContext = parent.observabilityContext,
      httpDriverOption = Some(driver)
    )
    val idInterpreter = new (UnitOfWorkOp ~> Id) {
      def apply[A](fa: UnitOfWorkOp[A]): Id[A] =
        new UnitOfWorkInterpreter(uow).execute(fa)
    }
    val tryInterpreter = new (UnitOfWorkOp ~> scala.util.Try) {
      def apply[A](fa: UnitOfWorkOp[A]): scala.util.Try[A] =
        throw new UnsupportedOperationException("unitOfWorkTryInterpreter is not available in component runtime")
    }
    val eitherInterpreter = new (UnitOfWorkOp ~> RuntimeContext.EitherThrowable) {
      def apply[A](op: UnitOfWorkOp[A]): Either[Throwable, A] =
        Left(new UnsupportedOperationException("unitOfWorkEitherInterpreter is not available in component runtime"))
    }
    new RuntimeContext(
      core = core,
      unitOfWorkSupplier = () => uow,
      unitOfWorkInterpreterFn = idInterpreter,
      unitOfWorkTryInterpreterFn = tryInterpreter,
      unitOfWorkEitherInterpreterFn = eitherInterpreter,
      commitAction = commitUow => {
        val _ = commitUow.commit()
        ()
      },
      abortAction = abortUow => {
        val _ = abortUow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "component-runtime-context"
    )
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

      def put(
        path: String,
        body: Option[String],
        headers: Map[String, String]
      ): HttpResponse =
        throw new UnsupportedOperationException(s"HttpDriver not configured: PUT ${path}")
    }
}

object ComponentLogic {
  // TODO migrate to AdminComponent
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
      Consequence.success(OperationResponse.Scalar(core.executionContext.runtime.formatPing))
    }
  }
}
