package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.record.Record
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall, QueryAction, ResourceAccess}
import cats.~>
import org.goldenport.cncf.context.{DataStoreContext, EntitySpaceContext, EntityStoreContext, ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeKind}
import org.goldenport.cncf.backend.collaborator.Collaborator
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{EventEngine, EventStore, ReceptionInput}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.job.{ActionId, ActionTask, JobControlPolicy, JobControlRequest, JobControlResponse, JobEngine, JobId, JobPersistencePolicy, JobResult, JobRunMode, JobStatus, JobSubmitOption, JobTask}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.unitofwork.UnitOfWorkInterpreter
import org.goldenport.cncf.statemachine.PlannedTransitionValidationHook
import org.goldenport.cncf.operation.CmlOperationDefinition

/*
 * @since   Jan.  3, 2026
 *  version Jan. 20, 2026
 *  version Feb. 25, 2026
 *  version Mar. 31, 2026
 * @version Apr. 22, 2026
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
    createActionCall(action, ctx0)
  }

  def createActionCall(
    action: Action,
    ctx0: ExecutionContext
  ): ActionCall = {
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
    _event_action_call(action, core).getOrElse(action.createCall(core))
  }

  private def _event_action_call(
    action: Action,
    core: ActionCall.Core
  ): Option[ActionCall] = {
    val opname = action.request.operation
    val implementation = _operation_implementation(opname)
    if (component.eventReceptionDefinitions.isEmpty && component.eventSubscriptionDefinitions.isEmpty)
      None
    else if (implementation.contains("event-emit") || _is_event_emit_operation(opname))
      Some(ComponentLogic.EmitEventActionCall(core, action))
    else if (implementation.contains("event-effect-record") || _is_event_record_operation(opname))
      Some(ComponentLogic.RecordEffectActionCall(core, action))
    else if (implementation.contains("event-effect-load") || _is_event_load_operation(opname))
      Some(ComponentLogic.LoadEffectActionCall(core, action))
    else
      None
  }

  private def _operation_implementation(
    operationname: String
  ): Option[String] =
    component.operationDefinitions.find(_.name == operationname).flatMap(_.implementation.map(_.trim.toLowerCase))

  private def _is_event_emit_operation(opname: String): Boolean =
    opname.equalsIgnoreCase("emitEvent")

  private def _is_event_record_operation(opname: String): Boolean =
    opname.equalsIgnoreCase("recordEffect")

  private def _is_event_load_operation(opname: String): Boolean =
    opname.equalsIgnoreCase("loadEffect")

  private def _get_collaborator: Option[Collaborator] = component match {
    case m: CollaboratorComponent => Some(m.collaborator)
    case _ => None
  }

  private[cncf] def execute(ac: ActionCall): Consequence[OperationResponse] = // ActionResponse
    component.actionEngine.execute(ac)

  def executeAction(action: Action): Consequence[OperationResponse] =
    executeAction(action, _execution_context())

  def executeAction(
    action: Action,
    ctx: ExecutionContext
  ): Consequence[OperationResponse] = {
    val actionscope = component.scopeContext.createChildScope(ScopeKind.Action, action.name)
    val scopedCtx = ctx.withScope(actionscope)
    val task = ActionTask(ActionId.generate(), action, component.actionEngine, Some(component))
    _resolve_operation_kind(action) match {
      case Some(ComponentLogic.OperationKind.Query) =>
        _execute_query_action(task, scopedCtx)
      case Some(ComponentLogic.OperationKind.Command) =>
        _execute_command_action(task, action, scopedCtx)
      case None =>
        action match {
          case _: QueryAction =>
            _execute_query_action(task, scopedCtx)
          case _: CommandAction =>
            _execute_command_action(task, action, scopedCtx)
          case _ =>
            submitJob(List(task), scopedCtx).map(jobid => OperationResponse.Scalar(jobid.value))
      }
    }
  }

  def executeEventContinuationAction(
    action: Action,
    ctx: ExecutionContext
  ): Consequence[OperationResponse] = {
    val call = createActionCall(action, ctx)
    given ExecutionContext = ctx
    call.authorize().flatMap { _ =>
      try {
        call.execute()
      } catch {
        case e: Throwable =>
          Consequence.Failure(Conclusion.from(e))
      }
    }
  }

  private def _execute_query_action(
    task: ActionTask,
    ctx: ExecutionContext
  ): Consequence[OperationResponse] =
    submitJob(List(task), ctx).flatMap(jobid => awaitJobResult(jobid))

  private def _execute_command_action(
    task: ActionTask,
    action: Action,
    ctx: ExecutionContext
  ): Consequence[OperationResponse] = {
    val mode = action match {
      case command: CommandAction =>
        _effective_command_execution_mode(action, command, ctx)
      case _ =>
        CommandExecutionMode.AsyncJob
    }
    mode match {
      case CommandExecutionMode.AsyncJob =>
        val option = _default_submit_option(List(task)).copy(runMode = JobRunMode.Async)
        submitJob(List(task), ctx, option).map(jobid => OperationResponse.Scalar(jobid.value))
      case CommandExecutionMode.AsyncJobAndAwait =>
        val option = _default_submit_option(List(task)).copy(runMode = JobRunMode.Async)
        submitJob(List(task), ctx, option).flatMap(jobid => awaitJobResult(jobid))
      case CommandExecutionMode.SyncJob =>
        val option = _default_submit_option(List(task)).copy(runMode = JobRunMode.Sync)
        submitJob(List(task), ctx, option).flatMap(jobid => awaitJobResult(jobid))
      case CommandExecutionMode.SyncJobAsyncInterface =>
        val option = _default_submit_option(List(task)).copy(runMode = JobRunMode.Async)
        submitJob(List(task), ctx, option).flatMap { jobid =>
          val _ = awaitJobResult(jobid)
          Consequence.success(OperationResponse.Scalar(jobid.value))
        }
      case CommandExecutionMode.SyncDirectNoJob =>
        execute(createActionCall(action, ctx))
    }
  }

  private def _resolve_operation_kind(
    action: Action
  ): Option[ComponentLogic.OperationKind] = {
    val operationname = action.request.operation
    component.operationDefinitions.find(_.name == operationname).flatMap(_to_operation_kind)
  }

  private def _to_operation_kind(
    definition: CmlOperationDefinition
  ): Option[ComponentLogic.OperationKind] =
    definition.kind.trim.toUpperCase match {
      case "QUERY" => Some(ComponentLogic.OperationKind.Query)
      case "COMMAND" => Some(ComponentLogic.OperationKind.Command)
      case _ => None
    }

  private def _effective_command_execution_mode(
    action: Action,
    command: CommandAction,
    ctx: ExecutionContext
  ): CommandExecutionMode =
    ctx.framework.commandExecutionMode.getOrElse {
      _operation_execution_mode(action.request.operation).getOrElse {
        if (_is_command_script_combo(action))
          CommandExecutionMode.SyncDirectNoJob
        else
          command.commandExecutionMode
      }
    }

  private def _operation_execution_mode(
    operationname: String
  ): Option[CommandExecutionMode] =
    component.operationDefinitions.find(_.name == operationname).flatMap { definition =>
      definition.execution.map(_.trim.toLowerCase).collect {
        case "sync" => CommandExecutionMode.SyncDirectNoJob
        case "async" => CommandExecutionMode.AsyncJob
      }
    }

  private def _is_command_script_combo(action: Action): Boolean = {
    val isscriptcomponent = action.request.component.exists(_.equalsIgnoreCase("SCRIPT"))
    val iscommandmode = GlobalRuntimeContext.current.exists(_.runtimeMode.name == "command")
    isscriptcomponent && iscommandmode
  }

  def submitJob(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] =
    component.jobEngine.submit(tasks, ctx, _default_submit_option(tasks))

  def submitJob(
    tasks: List[JobTask],
    ctx: ExecutionContext,
    option: JobSubmitOption
  ): Consequence[JobId] =
    component.jobEngine.submit(tasks, ctx, option)

  def getJobStatus(jobId: JobId): Option[JobStatus] =
    component.jobEngine.getStatus(jobId)

  def getJobResult(jobId: JobId): Option[JobResult] =
    component.jobEngine.getResult(jobId)

  def controlJob(
    jobId: JobId,
    request: JobControlRequest,
    policy: JobControlPolicy = JobControlPolicy.default
  )(using ExecutionContext): Consequence[JobControlResponse] =
    component.jobEngine.control(jobId, request, policy)

  def awaitJobResult(
    jobid: JobId,
    timeoutMillis: Long = 3000L,
    pollMillis: Long = 10L
  ): Consequence[OperationResponse] = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var result: Option[JobResult] = None
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      result = getJobResult(jobid)
      if (result.isEmpty)
        Thread.sleep(pollMillis)
    }
    result match {
      case Some(JobResult.Success(response)) => Consequence.success(response)
      case Some(JobResult.Failure(conclusion)) => Consequence.Failure(conclusion)
      case None => Consequence.stateConflict(s"job timeout: ${jobid.value}")
    }
  }

  def executionContext(): ExecutionContext =
    _execution_context()

  private def _execution_context(): ExecutionContext = {
    val driver = component.applicationConfig.httpDriver
      .orElse(component.subsystem.flatMap(_.httpDriver))
      .getOrElse(_fallback_http_driver_())
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    // Bind UnitOfWork to the ActionCall execution context.
    lazy val uow: UnitOfWork = new UnitOfWork(
      context = context,
      eventengine = EventEngine.noop(
        DataStore.noop(),
        eventstore = component.eventStore.getOrElse(EventStore.inMemory)
      )
    )
    lazy val runtime: RuntimeContext = _component_runtime_context(() => uow, driver)
    context
  }

  private def _default_submit_option(tasks: List[JobTask]): JobSubmitOption =
    tasks.headOption match {
      case Some(ActionTask(_, _: QueryAction, _, _)) =>
        JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)
      case _ =>
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent)
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
    uowsupplier: () => UnitOfWork,
    driver: HttpDriver
  ): RuntimeContext = {
    val parent = component.scopeContext
    val core = RuntimeContext.core(
      name = "component-runtime",
      parent = Some(parent),
      observabilityContext = parent.observabilityContext,
      httpDriverOption = Some(driver),
      datastore = Some(DataStoreContext(parent.dataStoreSpace)),
      entitystore = Some(EntityStoreContext(parent.entityStoreSpace)),
      entityspace = Some(EntitySpaceContext(component.entitySpace))
    )
    val consequenceInterpreter = new (UnitOfWorkOp ~> Consequence) {
      def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] =
        new UnitOfWorkInterpreter(uowsupplier()).interpret(fa)
    }
    new RuntimeContext(
      core = core,
      unitOfWorkSupplier = uowsupplier,
      unitOfWorkInterpreterFn = consequenceInterpreter,
      commitAction = commitUow => {
        val _ = commitUow.commit()
        ()
      },
      abortAction = abortUow => {
        val _ = abortUow.rollback()
        ()
      },
      disposeAction = _ => (),
      token = "component-runtime-context",
      operationMode = GlobalRuntimeContext.current
        .map(_.config.operationMode)
        .getOrElse(org.goldenport.cncf.config.RuntimeConfig.DefaultOperationMode),
      transitionValidationHook = new PlannedTransitionValidationHook(
        component.stateMachinePlannerProvider
      )
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
  private enum OperationKind {
    case Query
    case Command
  }

  private final case class EmitEventActionCall(
    core: ActionCall.Core,
    override val action: Action
  ) extends ProcedureActionCall {
    private def _component = core.component
    private def _executionContext = core.executionContext

    private def _payload: Map[String, Any] =
      action.request.toRecord.fields.map(x => x.key -> x.value).toMap

    def execute(): Consequence[OperationResponse] =
      _component.flatMap(_.eventReception) match {
        case Some(reception) =>
          _component.flatMap(_.eventReceptionDefinitions.headOption) match {
            case Some(definition) =>
              val input = ReceptionInput(
                name = definition.name,
                kind = definition.kind.getOrElse("domain-event"),
                payload = _payload,
                attributes = definition.selectors,
                persistent = false
              )
              given ExecutionContext = _executionContext
              reception.receiveAuthorized(input).map { result =>
                OperationResponse.create(
                  Record.data(
                    "outcome" -> result.outcome.toString,
                    "dispatchedCount" -> result.dispatchedCount,
                    "persisted" -> result.persisted
                  )
                )
              }
            case None =>
              Consequence.serviceUnavailable("event reception definition is missing")
          }
        case None =>
          Consequence.serviceUnavailable("event reception is not initialized")
      }
  }

  private final case class RecordEffectActionCall(
    core: ActionCall.Core,
    override val action: Action
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(c) =>
          val record = action.request.toRecord
          c.recordEventEffect(record)
          Consequence.success(OperationResponse.create(record))
        case None =>
          Consequence.stateConflict("component is not initialized")
      }
  }

  private final case class LoadEffectActionCall(
    core: ActionCall.Core,
    override val action: Action
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(c) =>
          Consequence.success(OperationResponse.create(c.loadEventEffect()))
        case None =>
          Consequence.stateConflict("component is not initialized")
      }
  }

  // TODO migrate to AdminComponent
  final case class PingAction(request: Request) extends QueryAction() {
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
