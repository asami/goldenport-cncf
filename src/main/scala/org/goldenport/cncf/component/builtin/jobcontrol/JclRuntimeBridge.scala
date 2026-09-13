package org.goldenport.cncf.component.builtin.jobcontrol

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.event.{EventPublishOption, ReceptionDomainEvent}
import org.goldenport.cncf.job.{
  ActionId,
  ActionTask,
  JobContinuation,
  JobDefinitionSnapshot,
  JobEventEmission,
  JobFailureHook,
  JobFlowStep,
  JobId,
  JobPersistencePolicy,
  JobSemanticPlan,
  JobSubmitOption,
  JobTask,
  TaskFailed,
  TaskOutcome,
  TaskSucceeded
}
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.workflow.WorkflowEntrypoint
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}

/*
 * @since   Sep. 13, 2026
 * @version Sep. 13, 2026
 */
/** Package-private runtime owner for accepted JCL action and workflow targets. */
private[jobcontrol] final class JclRuntimeBridge(component: Component) {
  def submitAction(
    selector: String,
    parameters: Map[String, String],
    requestSummary: Option[String],
    persistence: JobPersistencePolicy,
    declaredProfile: Option[org.goldenport.cncf.job.JobDeclaredProfile],
    definitionSnapshot: Option[JobDefinitionSnapshot] = None,
    compensation: Option[JobFailureHook] = None,
    plan: JobSemanticPlan = JobSemanticPlan(
      rootAction = None,
      flow = None,
      events = None,
      continuations = Vector.empty
    )
  )(using ctx: org.goldenport.cncf.context.ExecutionContext):
      Consequence[(JobId, Consequence[OperationResponse])] =
    _resolve_target_action(selector, parameters).flatMap { case (target, action) =>
      _resolve_compensation_task(compensation, parameters).flatMap { comp =>
        val roottask = ActionTask(
          ActionId.create("jcl.submit", ctx.clock.instant(), ctx.idGeneration),
          action,
          target.actionEngine,
          Some(target),
          compensationActionRef = compensation.map(_.action),
          compensationTask = comp
        )
        val flowsteps = plan.steps
        val flowids = flowsteps.map(_.id).mkString(",")
        val flowparameters: Map[String, String] =
          if (flowsteps.nonEmpty) Map("jcl.flow.step.ids" -> flowids)
          else Map.empty
        val flowexecutionnotes =
          if (flowsteps.nonEmpty)
            Vector(s"jcl flow step ids: $flowids")
          else
            Vector.empty
        val eventids = plan.emittedEvents.map(_.id).mkString(",")
        val continuationids = plan.continuations.map(_.id).mkString(",")
        val eventparameters: Map[String, String] =
          (if (plan.emittedEvents.nonEmpty)
             Map("jcl.event.ids" -> eventids)
           else Map.empty) ++
            (if (plan.continuations.nonEmpty)
               Map("jcl.continuation.ids" -> continuationids)
             else Map.empty)
        val eventexecutionnotes =
          (if (plan.emittedEvents.nonEmpty)
             Vector(s"jcl event ids: $eventids")
           else Vector.empty) ++
            (if (plan.continuations.nonEmpty)
               Vector(s"jcl continuation ids: $continuationids")
             else Vector.empty)
        val option = JobSubmitOption(
          persistence = persistence,
          requestSummary = requestSummary,
          parameters = parameters ++ Map("jcl.target.action" -> selector) ++ flowparameters ++ eventparameters ++ compensation.map(h =>
            "jcl.compensation.action" -> h.action
          ),
          executionNotes = Vector("jcl submission") ++ flowexecutionnotes ++ eventexecutionnotes,
          declaredProfile = declaredProfile,
          jobDefinitionSnapshot = definitionSnapshot
        )
        _prepare_operation_task(action, roottask, ctx).flatMap {
          case (preparedroottask, preparedcontext) =>
          _prepare_flow_tasks(flowsteps, parameters, ctx).flatMap { preparedflowtasks =>
            _prepare_continuation_tasks(plan.continuations, parameters, ctx).flatMap { preparedcontinuations =>
              val preparedtasks = _wrap_event_tasks(
                preparedroottask,
                flowsteps,
                preparedflowtasks,
                plan.emittedEvents,
                preparedcontinuations
              )
              component.jobEngine.submit(
                preparedtasks._1 :: preparedtasks._2.toList,
                preparedcontext,
                option
              ).map { jobid =>
                (jobid, component.logic.awaitJobResult(jobid))
              }
            }
          }
        }
      }
    }

  def submitWorkflow(
    entry: org.goldenport.cncf.job.JobWorkflowTarget,
    parameters: Map[String, String],
    requestSummary: Option[String],
    declaredProfile: Option[org.goldenport.cncf.job.JobDeclaredProfile],
    definitionSnapshot: Option[JobDefinitionSnapshot] = None
  )(using org.goldenport.cncf.context.ExecutionContext):
      Consequence[(Vector[JobId], Consequence[OperationResponse])] =
    _resolve_workflow_entrypoint(entry).flatMap { endpoint =>
      val event = _workflow_start_event(endpoint, parameters)
      component.subsystem match {
        case Some(subsystem) =>
          subsystem.workflowEngine.handle(endpoint.component.name, event).flatMap { decision =>
            if (decision.progressed)
              decision.relatedJobId match {
                case Some(jobid) =>
                  declaredProfile.foreach { profile =>
                    component.jobEngine.annotateJob(
                      jobid,
                      Map(
                        "jcl.workflow.definition" -> entry.definition,
                        "jcl.workflow.registration" -> entry.registration
                      ),
                      Vector("jcl workflow profile submission")
                    )
                    component.jobEngine.annotateJobProfile(jobid, profile)
                  }
                  definitionSnapshot.foreach { snapshot =>
                    component.jobEngine.annotateJob(
                      jobid,
                      snapshot.toParameters,
                      Vector("jcl jobDefinition snapshot attached")
                    )
                  }
                  Consequence.success(
                    (
                      Vector(jobid),
                      Consequence.success(
                        OperationResponse.Scalar(requestSummary.getOrElse("workflow-started"))
                      )
                    )
                  )
                case None =>
                  Consequence.stateConflict(
                    s"workflow progressed without managed job: ${entry.definition}/${entry.registration}"
                  )
              }
            else
              Consequence.success(
                (
                      Vector.empty[JobId],
                  Consequence.argumentInvalid(
                    s"workflow did not progress: ${decision.reason.getOrElse("unknown")}"
                  )
                )
              )
          }
        case None =>
          Consequence.serviceUnavailable("subsystem is not available")
      }
    }

  private def _prepare_continuation_tasks(
    continuations: Vector[JobContinuation],
    rootparameters: Map[String, String],
    context: org.goldenport.cncf.context.ExecutionContext
  ): Consequence[Vector[(JobContinuation, JobTask)]] =
    continuations.foldLeft(
      Consequence.success(Vector.empty[(JobContinuation, JobTask)])
    ) { (result, continuation) =>
      result.flatMap { preparedtasks =>
        val parameters = rootparameters ++ continuation.parameters
        _resolve_target_action(continuation.action, parameters).flatMap {
          case (target, action) =>
            val task = ActionTask(
              ActionId.create(
                "jcl.submit.continuation",
                context.clock.instant(),
                context.idGeneration
              ),
              action,
              target.actionEngine,
              Some(target)
            )
            _prepare_operation_task(action, task, context).map {
              case (preparedtask, _) => preparedtasks :+ (continuation -> preparedtask)
            }
        }
      }
    }

  private def _wrap_event_tasks(
    preparedroot: JobTask,
    flowsteps: Vector[JobFlowStep],
    preparedflowtasks: Vector[JobTask],
    emissions: Vector[JobEventEmission],
    preparedcontinuations: Vector[(JobContinuation, JobTask)]
  ): (JobTask, Vector[JobTask]) = {
    def _wrap_(task: JobTask, after: String): JobTask = {
      val matching = emissions.filter(_.after == after)
      if (matching.isEmpty)
        task
      else
        JclEventAwareTask(
          delegate = task,
          emissions = matching,
          continuations = preparedcontinuations,
          component = component
        )
    }
    val root = _wrap_(preparedroot, "root")
    val flow = flowsteps.zip(preparedflowtasks).map { case (step, task) =>
      _wrap_(task, step.id)
    }
    (root, flow)
  }

  private def _prepare_flow_tasks(
    flowsteps: Vector[JobFlowStep],
    rootparameters: Map[String, String],
    context: org.goldenport.cncf.context.ExecutionContext
  ): Consequence[Vector[JobTask]] =
    flowsteps.foldLeft(Consequence.success(Vector.empty[JobTask])) { (result, step) =>
      result.flatMap { preparedtasks =>
        val parameters = rootparameters ++ step.parameters
        _resolve_target_action(step.action, parameters).flatMap { case (target, action) =>
          val task = ActionTask(
            ActionId.create("jcl.submit.flow", context.clock.instant(), context.idGeneration),
            action,
            target.actionEngine,
            Some(target)
          )
          _prepare_operation_task(action, task, context).map { case (preparedtask, _) =>
            preparedtasks :+ preparedtask
          }
        }
      }
    }

  private def _prepare_operation_task(
    action: Action,
    task: ActionTask,
    context: org.goldenport.cncf.context.ExecutionContext
  ): Consequence[(JobTask, org.goldenport.cncf.context.ExecutionContext)] =
    component.subsystem match {
      case Some(subsystem) => subsystem._prepare_operation_task(action, task, context)
      case None => Consequence.serviceUnavailable("component subsystem is not available")
    }

  private def _resolve_compensation_task(
    compensation: Option[JobFailureHook],
    parameters: Map[String, String]
  )(using ctx: org.goldenport.cncf.context.ExecutionContext): Consequence[Option[JobTask]] =
    compensation match {
      case None => Consequence.success(None)
      case Some(hook) =>
        _resolve_target_action(hook.action, parameters ++ hook.parameters).flatMap {
          case (target, action) =>
            val task = ActionTask(
              ActionId.create("jcl.compensation", ctx.clock.instant(), ctx.idGeneration),
              action,
              target.actionEngine,
              Some(target)
            )
            _prepare_operation_task(action, task, ctx).map(x => Some(x._1))
        }
    }

  private def _resolve_workflow_entrypoint(
    entry: org.goldenport.cncf.job.JobWorkflowTarget
  ): Consequence[WorkflowEntrypoint] =
    component.subsystem match {
      case Some(subsystem) =>
        subsystem.workflowEngine.findEntrypoint(entry.definition, entry.registration) match {
          case Some(endpoint) => Consequence.success(endpoint)
          case None =>
            subsystem.workflowEngine.findDefinition(entry.definition) match {
              case None =>
                Consequence.argumentInvalid(
                  s"unknown JCL workflow definition: ${entry.definition}"
                )
              case Some(_) =>
                Consequence.argumentInvalid(
                  s"unknown JCL workflow registration: ${entry.definition}/${entry.registration}"
                )
            }
        }
      case None =>
        Consequence.serviceUnavailable("subsystem is not available")
    }

  private def _workflow_start_event(
    endpoint: WorkflowEntrypoint,
    parameters: Map[String, String]
  )(using ctx: org.goldenport.cncf.context.ExecutionContext): ReceptionDomainEvent = {
    val payload: Map[String, Any] = parameters.toVector.map(x => x._1 -> x._2).toMap
    val attributes = parameters ++ Map(
      "entity" -> endpoint.registration.entityCollection,
      "jcl.workflow.definition" -> endpoint.definition.name,
      "jcl.workflow.registration" -> endpoint.registration.name,
      "jcl.synthetic-start" -> "true"
    )
    ReceptionDomainEvent(
      name = endpoint.registration.eventName,
      kind = "domain-event",
      payload = payload,
      attributes = attributes,
      occurredAt = ctx.clock.instant()
    )
  }

  private def _resolve_target_action(
    selector: String,
    parameters: Map[String, String]
  ): Consequence[(Component, Action)] =
    component.subsystem.map(_.operationResolver.resolve(selector)).getOrElse(
      OperationResolver.ResolutionResult.Invalid("subsystem is not available")
    ) match {
      case OperationResolver.ResolutionResult.Resolved(
            _,
            componentName,
            serviceName,
            operationName
          ) =>
        component.subsystem.flatMap(_.findComponent(componentName)) match {
          case Some(target) =>
            val request = Request.of(
              component = componentName,
              service = serviceName,
              operation = operationName,
              arguments = parameters.toVector.sortBy(_._1).map { case (k, v) =>
                org.goldenport.protocol.Argument(k, v)
              }.toList
            )
            target.logic.makeOperationRequest(request).flatMap {
              case action: Action => Consequence.success((target, action))
              case _: OperationRequest =>
                Consequence.argumentInvalid(s"JCL target is not action: $selector")
            }
          case None =>
            Consequence.operationNotFound(s"JCL target component: $componentName")
        }
      case OperationResolver.ResolutionResult.NotFound(_, s) =>
        Consequence.operationNotFound(s"JCL target action: $s")
      case OperationResolver.ResolutionResult.Ambiguous(s, candidates) =>
        Consequence.argumentInvalid(
          s"ambiguous JCL target action: $s => ${candidates.mkString(",")}"
        )
      case OperationResolver.ResolutionResult.Invalid(message) =>
        Consequence.argumentInvalid(message)
    }

  /** Adapter that emits declared events and runs matching continuations after success. */
  private final case class JclEventAwareTask(
    delegate: JobTask,
    emissions: Vector[JobEventEmission],
    continuations: Vector[(JobContinuation, JobTask)],
    component: Component
  ) extends JobTask {
    def actionId: ActionId = delegate.actionId
    override def taskKind: String = delegate.taskKind
    override def targetKind: Option[String] = delegate.targetKind
    override def relation: Option[String] = delegate.relation
    override def transactionRole: Option[String] = delegate.transactionRole
    override def transactionScope: Option[String] = delegate.transactionScope
    override def compensationActionRef: Option[String] = delegate.compensationActionRef
    override def compensationTask: Option[JobTask] = delegate.compensationTask
    override def componentName: Option[String] = delegate.componentName
    override def serviceName: Option[String] = delegate.serviceName
    override def operationName: Option[String] = delegate.operationName
    override def requestSummary: Option[String] = delegate.requestSummary
    override def requestParameters: Map[String, String] = delegate.requestParameters
    override def defaultPersistence: JobPersistencePolicy = delegate.defaultPersistence

    def run(ctx: org.goldenport.cncf.context.ExecutionContext): TaskOutcome =
      delegate.run(ctx) match {
        case success @ TaskSucceeded(_) =>
          _process_success(ctx) match {
            case Consequence.Success(_) => success
            case Consequence.Failure(conclusion) => TaskFailed(conclusion)
          }
        case failure: TaskFailed => failure
      }

    override def observeCanonicalOutcome(
      outcome: TaskOutcome,
      ctx: org.goldenport.cncf.context.ExecutionContext,
      cancelled: Boolean
    ): Unit =
      delegate.observeCanonicalOutcome(outcome, ctx, cancelled)

    override def observeAdmissionFailure(
      conclusion: org.goldenport.Conclusion,
      ctx: org.goldenport.cncf.context.ExecutionContext
    ): Unit =
      delegate.observeAdmissionFailure(conclusion, ctx)

    private def _process_success(
      ctx: org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Unit] =
      emissions.foldLeft(Consequence.success(())) { (result, emission) =>
        result.flatMap { _ =>
          _publish(emission, ctx).flatMap { _ =>
            continuations
              .filter(_._1.event == emission.name)
              .foldLeft(Consequence.success(())) { (continuationresult, pair) =>
                continuationresult.flatMap { _ =>
                  _run_continuation(pair._2, ctx)
                }
              }
          }
        }
      }

    private def _publish(
      emission: JobEventEmission,
      ctx: org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Unit] =
      component.subsystem match {
        case Some(subsystem) =>
          given org.goldenport.cncf.context.ExecutionContext = ctx
          subsystem.eventBus.publishRuntime(
            _event(emission, ctx),
            EventPublishOption(persistent = emission.persistent.getOrElse(false))
          ).map(_ => ())
        case None =>
          Consequence.serviceUnavailable("component subsystem is not available")
      }

    private def _run_continuation(
      task: JobTask,
      ctx: org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Unit] =
      ctx.jobContext.jobId match {
        case Some(jobid) =>
          component.jobEngine.runTaskInJobSync(jobid, task, ctx).flatMap {
            case TaskSucceeded(_) => Consequence.unit
            case TaskFailed(conclusion) => Consequence.Failure(conclusion)
          }
        case None =>
          Consequence.stateInvalid("JCL continuation requires managed job context")
      }

    private def _event(
      emission: JobEventEmission,
      ctx: org.goldenport.cncf.context.ExecutionContext
    ): ReceptionDomainEvent = {
      val job = ctx.jobContext
      val correlationid = ctx.observability.correlationId.map(_.print)
      val actionid = job.actionId.map(_.print)
      val causationid = job.causationId.orElse(actionid).orElse(correlationid)
      val contextattributes = Vector(
        job.jobId.map(x => "cncf.context.jobId" -> x.print),
        job.currentTask.orElse(job.taskId).map(x => "cncf.context.taskId" -> x.print),
        correlationid.map(x => "cncf.context.correlationId" -> x),
        causationid.map(x => "cncf.context.causationId" -> x)
      ).flatten.toMap
      val sourceattributes = Vector(
        _scope_name(ctx.scope, org.goldenport.cncf.context.ScopeKind.Subsystem)
          .map(x => "cncf.source.subsystem" -> x),
        _scope_name(ctx.scope, org.goldenport.cncf.context.ScopeKind.Component)
          .orElse(delegate.componentName)
          .map(x => "cncf.source.component" -> x),
        _scope_name(ctx.scope, org.goldenport.cncf.context.ScopeKind.Action)
          .orElse(delegate.operationName)
          .map(x => "cncf.source.action" -> x)
      ).flatten.toMap
      ReceptionDomainEvent(
        name = emission.name,
        kind = emission.kind.getOrElse("domain-event"),
        payload = Map.empty,
        attributes = Map(
          "jcl.event.id" -> emission.id,
          "jcl.event.after" -> emission.after
        ) ++ contextattributes ++ sourceattributes,
        occurredAt = ctx.clock.instant()
      )
    }

    private def _scope_name(
      scope: org.goldenport.cncf.context.ScopeContext,
      kind: org.goldenport.cncf.context.ScopeKind
    ): Option[String] =
      if (scope.core.kind == kind)
        Some(scope.core.name)
      else
        scope.core.parent.flatMap(_scope_name(_, kind))
  }
}
