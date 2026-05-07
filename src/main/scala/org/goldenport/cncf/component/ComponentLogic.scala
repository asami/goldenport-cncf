package org.goldenport.cncf.component

import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.http.{HttpRequest, HttpResponse}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.record.Record
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode, CommandExecutionPolicy, CommandInterfaceMode, CommandJobRunMode, ProcedureActionCall, QueryAction, ResourceAccess}
import cats.~>
import org.goldenport.cncf.context.{DataStoreContext, EntitySpaceContext, EntityStoreContext, ExecutionContext, GlobalRuntimeContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.backend.collaborator.Collaborator
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.event.{EventEngine, EventStore, ReceptionInput}
import org.goldenport.cncf.http.HttpDriver
import org.goldenport.cncf.job.{ActionId, ActionTask, JobBatchDefinition, JobControlPolicy, JobControlRequest, JobControlResponse, JobDefinitionEntity, JobDefinitionSnapshot, JobEngine, JobFailureHook, JobId, JobPersistencePolicy, JobResult, JobRunMode, JobStatus, JobSubmitOption, JobTask}
import org.goldenport.cncf.subsystem.resolver.OperationResolver
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
 *  version Apr. 24, 2026
 * @version May.  7, 2026
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
    ctx.runtime.clearExecutionMetadata()
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
    if (ctx.framework.traceJob) {
      val option = _default_submit_option(List(task)).copy(
        persistence = JobPersistencePolicy.Persistent,
        runMode = JobRunMode.Sync,
        executionNotes = Vector("debug trace query")
      )
      submitJob(List(task), ctx, option).flatMap { jobid =>
        _note_job_response(ctx, jobid)
        awaitJobResult(jobid)
      }
    } else {
      task.run(ctx).result
    }

  private def _execute_command_action(
    task: ActionTask,
    action: Action,
    ctx: ExecutionContext
  ): Consequence[OperationResponse] = {
    val policy = action match {
      case command: CommandAction =>
        _effective_command_execution_policy(action, command, ctx)
      case _ =>
        ctx.framework.commandExecutionMode
          .map(CommandExecutionPolicy.fromLegacyMode)
          .orElse(_operation_execution_policy(action.request.operation))
          .getOrElse(CommandExecutionPolicy.default)
    }
    if (!policy.managedByJob) {
      execute(createActionCall(action, ctx))
    } else {
      val runmode = policy.jobRunMode match {
        case CommandJobRunMode.Sync => JobRunMode.Sync
        case CommandJobRunMode.Async => JobRunMode.Async
      }
      val option0 = _default_submit_option(List(task)).copy(runMode = runmode)
      _job_definition_submit_binding(action, option0, ctx).flatMap { binding =>
        _task_with_compensation(task, binding.compensation, action, ctx).flatMap { task1 =>
          submitJob(List(task1), ctx, binding.option).flatMap { jobid =>
            _note_job_response(ctx, jobid)
            policy.interfaceMode match {
              case CommandInterfaceMode.Async =>
                Consequence.success(OperationResponse.Scalar(jobid.value))
              case CommandInterfaceMode.Sync =>
                awaitJobResult(jobid)
            }
          }
        }
      }
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

  private def _effective_command_execution_policy(
    action: Action,
    command: CommandAction,
    ctx: ExecutionContext
  ): CommandExecutionPolicy =
    ctx.framework.commandExecutionMode.map(CommandExecutionPolicy.fromLegacyMode).getOrElse {
      _operation_execution_policy(action.request.operation).getOrElse {
        if (_is_command_script_combo(action))
          CommandExecutionPolicy.default
        else
          CommandExecutionPolicy.fromLegacyMode(command.commandExecutionMode)
      }
    }

  private def _operation_execution_policy(
    operationname: String
  ): Option[CommandExecutionPolicy] =
    _operation_definition(operationname).flatMap { definition =>
      definition.commandExecutionPolicy
        .orElse(definition.execution.flatMap(CommandExecutionPolicy.fromLegacyExecution))
    }

  private def _operation_definition(
    operationname: String
  ): Option[CmlOperationDefinition] =
    component.operationDefinitions.find(_.name == operationname)

  private final case class _JobDefinitionBinding(
    option: JobSubmitOption,
    compensation: Option[JobFailureHook]
  )

  private def _job_definition_submit_binding(
    action: Action,
    base: JobSubmitOption,
    ctx: ExecutionContext
  ): Consequence[_JobDefinitionBinding] =
    _job_definition_ref(action) match {
      case None => Consequence.success(_JobDefinitionBinding(base, None))
      case Some(ref) =>
        given ExecutionContext = ctx
        _load_job_definition(ref, ctx).flatMap {
          case Some(entity) if entity.isActive =>
            val snapshot = JobDefinitionSnapshot.from(entity)
            _job_definition_compensation(entity).map { compensation =>
              _JobDefinitionBinding(
                base.copy(
                  declaredProfile = base.declaredProfile.orElse(snapshot.profile),
                  jobDefinitionSnapshot = Some(snapshot)
                ),
                compensation
              )
            }
          case Some(entity) =>
            Consequence.argumentInvalid(s"JobDefinition is not active: ${entity.key}")
          case None =>
            Consequence.operationNotFound(s"JobDefinition:$ref")
        }
    }

  private def _job_definition_compensation(
    entity: JobDefinitionEntity
  ): Consequence[Option[JobFailureHook]] =
    JobBatchDefinition.parseYaml(entity.jclSource).flatMap { batch =>
      batch.jobs.headOption match {
        case Some(job) if batch.jobs.size == 1 =>
          Consequence.success(job.compensation)
        case Some(_) =>
          Consequence.argumentInvalid(s"JobDefinition must contain exactly one job: ${entity.key}")
        case None =>
          Consequence.argumentInvalid(s"JobDefinition has no job: ${entity.key}")
      }
    }

  private def _load_job_definition(
    ref: String,
    ctx: ExecutionContext
  ): Consequence[Option[JobDefinitionEntity]] = {
    given ExecutionContext = ctx
    EntityStore.standard().load[JobDefinitionEntity](JobDefinitionEntity.entityId(ref))(using JobDefinitionEntity.entityPersistent, ctx).flatMap {
      case some @ Some(_) => Consequence.success(some)
      case None => _load_job_definition_from_job_control(ref, ctx)
    }
  }

  private def _load_job_definition_from_job_control(
    ref: String,
    ctx: ExecutionContext
  ): Consequence[Option[JobDefinitionEntity]] =
    component.subsystem
      .flatMap(_.components.find(_.name == org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent.name))
      .flatMap(_.port.get[org.goldenport.cncf.component.builtin.jobcontrol.JobControlComponent.JobService]) match {
        case Some(service) =>
          given ExecutionContext = ctx
          service.getJobDefinition(ref).flatMap(JobDefinitionEntity.fromRecord).map(Some(_))
        case None =>
          Consequence.success(None)
      }

  private def _task_with_compensation(
    task: ActionTask,
    compensation: Option[JobFailureHook],
    action: Action,
    ctx: ExecutionContext
  ): Consequence[ActionTask] =
    compensation match {
      case None => Consequence.success(task)
      case Some(hook) =>
        _resolve_action(hook.action, _action_parameters(action) ++ hook.parameters, ctx).map { case (target, compensationAction) =>
          task.copy(
            compensationActionRef = Some(hook.action),
            compensationTask = Some(ActionTask(ActionId.generate(), compensationAction, target.actionEngine, Some(target)))
          )
        }
    }

  private def _resolve_action(
    selector: String,
    parameters: Map[String, String],
    ctx: ExecutionContext
  ): Consequence[(Component, Action)] =
    component.subsystem.map(_.operationResolver.resolve(selector)).getOrElse(OperationResolver.ResolutionResult.Invalid("subsystem is not available")) match {
      case OperationResolver.ResolutionResult.Resolved(_, componentName, serviceName, operationName) =>
        component.subsystem.flatMap(_.findComponent(componentName)) match {
          case Some(target) =>
            val request = Request.of(
              component = componentName,
              service = serviceName,
              operation = operationName,
              arguments = parameters.toVector.sortBy(_._1).map { case (k, v) => Argument(k, v) }.toList
            )
            target.logic.makeOperationRequest(request).flatMap {
              case action: Action => Consequence.success((target, action))
              case _: OperationRequest => Consequence.argumentInvalid(s"JobDefinition compensation target is not action: $selector")
            }
          case None =>
            Consequence.operationNotFound(s"JobDefinition compensation component: $componentName")
        }
      case OperationResolver.ResolutionResult.NotFound(_, s) =>
        Consequence.operationNotFound(s"JobDefinition compensation action: $s")
      case OperationResolver.ResolutionResult.Ambiguous(s, candidates) =>
        Consequence.argumentInvalid(s"ambiguous JobDefinition compensation action: $s => ${candidates.mkString(",")}")
      case OperationResolver.ResolutionResult.Invalid(message) =>
        Consequence.argumentInvalid(message)
    }

  private def _action_parameters(
    action: Action
  ): Map[String, String] =
    (action.arguments.map(x => x.name -> x.value.toString) ++
      action.properties.map(x => x.name -> x.value.toString)).toMap

  private def _job_definition_ref(
    action: Action
  ): Option[String] =
    _request_job_definition_ref(action)
      .orElse(_operation_definition(action.request.operation).flatMap(_.jobDefinitionRef))

  private def _request_job_definition_ref(
    action: Action
  ): Option[String] =
    action.arguments.find(_.name == "jobDefinitionRef").map(_.value.toString)
      .orElse(action.properties.find(_.name == "jobDefinitionRef").map(_.value.toString))
      .map(_.trim)
      .filter(_.nonEmpty)

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

  private def _note_job_response(
    ctx: ExecutionContext,
    jobid: JobId
  ): Unit = {
    ctx.runtime.noteResponseJobId(jobid.value)
    if (ctx.framework.traceJob)
      ctx.runtime.noteDebugJobId(jobid.value)
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
      case Some(ActionTask(_, _: QueryAction, _, _, _, _)) =>
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
    val global = _global_runtime_context(parent)
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
    val runtime = new RuntimeContext(
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
      operationMode = global
        .map(_.config.operationMode)
        .getOrElse(RuntimeConfig.DefaultOperationMode),
      transitionValidationHook = new PlannedTransitionValidationHook(
        component.stateMachinePlannerProvider
      )
    )
    global.foreach(g => runtime.setResolvedParameters(g.resolvedParameters))
    runtime
  }

  private def _global_runtime_context(
    scope: ScopeContext
  ): Option[GlobalRuntimeContext] =
    scope match {
      case m: GlobalRuntimeContext =>
        Some(m)
      case other =>
        other.parent.flatMap(_global_runtime_context)
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
