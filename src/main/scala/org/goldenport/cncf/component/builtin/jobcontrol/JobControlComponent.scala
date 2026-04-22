package org.goldenport.cncf.component.builtin.jobcontrol

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobBatchDefinition, JobBatchSubmissionResult, JobControlCommand, JobControlRequest, JobDefinition, JobFailureHook, JobId, JobPersistencePolicy, JobResult, JobSubmitOption}
import org.goldenport.cncf.job.{JobQueryReadModel, JobTimelinePage}
import org.goldenport.cncf.event.ReceptionDomainEvent
import org.goldenport.cncf.subsystem.resolver.OperationResolver
import org.goldenport.cncf.event.EventStore
import org.goldenport.cncf.workflow.WorkflowEntrypoint
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.schema.DataType
import org.goldenport.value.BaseContent

/*
 * @since   Mar. 28, 2026
 *  version Mar. 29, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponent() extends Component {
}

object JobControlComponent {
  trait JobService {
    def getJobStatus(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobQueryReadModel]
    def loadJobHistory(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTimelinePage]
    def getJobResult(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobResult]
    def awaitJobResult(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[OperationResponse]
    def describeJobDefinition(body: String): Consequence[JobBatchDefinition]
    def submitJobDefinition(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult]
    def submitJobBatch(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult]
  }

  trait JobAdminService {
    def cancelJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def suspendJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def resumeJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]]
  }

  val name: String = "job_control"
  val componentId: ComponentId = ComponentId(name)

  object Factory extends Component.SinglePrimaryBundleFactory {
    protected def create_Component(params: ComponentCreate): Component =
      JobControlComponent()

    protected def create_Core(
      params: ComponentCreate,
      comp: Component
    ): Component.Core = {
      val request = spec.RequestDefinition()
      val idrequest = _job_id_request
      val getJobStatus = new GetJobStatusOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("JobQueryReadModel"))))
      val loadJobHistory = new LoadJobHistoryOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("JobTimelinePage"))))
      val getJobResult = new GetJobResultOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("JobResult"))))
      val awaitJobResult = new AwaitJobResultOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("OperationResponse"))))
      val bodyrequest = _body_request
      val describeJobDefinition = new DescribeJobDefinitionOperationDefinition(bodyrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val submitJobDefinition = new SubmitJobDefinitionOperationDefinition(bodyrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val submitJobBatch = new SubmitJobBatchOperationDefinition(bodyrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val cancelJob = new ControlJobOperationDefinition(
        name = "cancel_job",
        command = JobControlCommand.Cancel,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val suspendJob = new ControlJobOperationDefinition(
        name = "suspend_job",
        command = JobControlCommand.Suspend,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val resumeJob = new ControlJobOperationDefinition(
        name = "resume_job",
        command = JobControlCommand.Resume,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val loadJobEvents = new LoadJobEventsOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("RecordList"))))
      val jobService = spec.ServiceDefinition(
        name = "job",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(getJobStatus, loadJobHistory, getJobResult, awaitJobResult, describeJobDefinition, submitJobDefinition, submitJobBatch)
        )
      )
      val jobAdminService = spec.ServiceDefinition(
        name = "job_admin",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(cancelJob, suspendJob, resumeJob, loadJobEvents)
        )
      )
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          services = Vector(jobService, jobAdminService)
        ),
        handler = ProtocolHandler.default
      )
      comp.withPort(
        Component.Port.of(new DefaultJobService(comp), new DefaultJobAdminService(comp))
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        name,
        componentId,
        instanceid,
        protocol
      )
    }

    private def _job_id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument))
      )

    private def _body_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("body"), kind = spec.ParameterDefinition.Kind.Argument))
      )
  }

  private final class DefaultJobService(component: Component) extends JobService {
    private final case class _Submission(
      jobIds: Vector[JobId],
      response: Consequence[OperationResponse]
    )

    def getJobStatus(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobQueryReadModel] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(model) => Consequence.success(model)
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def loadJobHistory(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTimelinePage] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.jobEngine.queryTimeline(jobId) match {
            case Some(page) => Consequence.success(page)
            case None => Consequence.operationNotFound(s"job history:${jobId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def getJobResult(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobResult] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.logic.getJobResult(jobId) match {
            case Some(result) => Consequence.success(result)
            case None => Consequence.operationNotFound(s"job result:${jobId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def awaitJobResult(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[OperationResponse] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) => component.logic.awaitJobResult(jobId)
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def describeJobDefinition(body: String): Consequence[JobBatchDefinition] =
      JobBatchDefinition.parseYaml(body)

    def submitJobDefinition(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      JobBatchDefinition.parseYaml(body).flatMap { batch =>
        if (batch.jobs.size != 1)
          Consequence.argumentInvalid("submit_job_definition requires exactly one job in jobs[]")
        else
          _submit_batch(batch)
      }

    def submitJobBatch(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      JobBatchDefinition.parseYaml(body).flatMap(_submit_batch)

    private def _submit_batch(
      batch: JobBatchDefinition
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      _submit_jobs(batch.jobs, Vector.empty)

    private def _submit_jobs(
      jobs: Vector[JobDefinition],
      submitted: Vector[JobId],
      index: Int = 0
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      jobs.headOption match {
        case None =>
          Consequence.success(JobBatchSubmissionResult(submitted, success = true))
        case Some(job) =>
          _submit_one(job).flatMap { submission =>
            val updated = submitted ++ submission.jobIds
            submission.response match {
              case Consequence.Success(_) =>
                _submit_jobs(jobs.drop(1), updated, index + 1)
              case Consequence.Failure(conclusion) =>
                _run_failure_hook(job.onFailure).map { hook =>
                  JobBatchSubmissionResult(
                    submittedJobIds = updated,
                    success = false,
                    stoppedAtIndex = Some(index),
                    stoppedAtName = Some(job.name),
                    failureMessage = Some(conclusion.show),
                    failureHookJobId = hook._1,
                    failureHookMessage = hook._2
                  )
                }
            }
          }
      }

    private def _run_failure_hook(
      hook: Option[JobFailureHook]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[(Option[JobId], Option[String])] =
      hook match {
        case None => Consequence.success((None, None))
        case Some(h) =>
          _submit_action(
            selector = h.action,
            parameters = h.parameters,
            requestSummary = Some(s"jcl.failure-hook:${h.action}"),
            persistence = JobPersistencePolicy.Persistent
          ).map { case (jobid, response) =>
            response match {
              case Consequence.Success(_) => (Some(jobid), None)
              case Consequence.Failure(conclusion) => (Some(jobid), Some(conclusion.show))
            }
          }
      }

    private def _submit_one(
      job: JobDefinition
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[_Submission] =
      job.target match {
        case x if x.action.nonEmpty =>
          _submit_action(
            selector = x.action.get,
            parameters = job.parameters,
            requestSummary = job.submit.requestSummary.orElse(Some(job.name)),
            persistence = job.submit.persistence
          ).map { case (jobid, response) =>
            _Submission(Vector(jobid), response)
          }
        case x if x.workflow.nonEmpty =>
          _submit_workflow(
            entry = x.workflow.get,
            parameters = job.parameters,
            requestSummary = job.submit.requestSummary.orElse(Some(job.name))
          )
        case _ =>
          Consequence.argumentInvalid("JCL target must contain action or workflow")
      }

    private def _submit_action(
      selector: String,
      parameters: Map[String, String],
      requestSummary: Option[String],
      persistence: JobPersistencePolicy
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[(JobId, Consequence[OperationResponse])] =
      _resolve_target_action(selector, parameters).map { case (target, action) =>
        val task = ActionTask(ActionId.generate(), action, target.actionEngine, Some(target))
        val option = JobSubmitOption(
          persistence = persistence,
          requestSummary = requestSummary,
          parameters = parameters ++ Map("jcl.target.action" -> selector),
          executionNotes = Vector("jcl submission")
        )
        val jobid = component.jobEngine.submit(List(task), summon[org.goldenport.cncf.context.ExecutionContext], option)
        (jobid, component.logic.awaitJobResult(jobid))
      }

    private def _submit_workflow(
      entry: org.goldenport.cncf.job.JobWorkflowTarget,
      parameters: Map[String, String],
      requestSummary: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[_Submission] =
      _resolve_workflow_entrypoint(entry).flatMap { endpoint =>
        val event = _workflow_start_event(endpoint, parameters)
        component.subsystem match {
          case Some(subsystem) =>
            subsystem.workflowEngine.handle(endpoint.component.name, event).flatMap { decision =>
              if (decision.progressed)
                decision.relatedJobId match {
                  case Some(jobid) =>
                    Consequence.success(
                      _Submission(
                        Vector(jobid),
                        Consequence.success(OperationResponse.Scalar(requestSummary.getOrElse("workflow-started")))
                      )
                    )
                  case None =>
                    Consequence.stateConflict(s"workflow progressed without managed job: ${entry.definition}/${entry.registration}")
                }
              else
                Consequence.success(
                  _Submission(
                    Vector.empty,
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
                  Consequence.argumentInvalid(s"unknown JCL workflow definition: ${entry.definition}")
                case Some(_) =>
                  Consequence.argumentInvalid(s"unknown JCL workflow registration: ${entry.definition}/${entry.registration}")
              }
          }
        case None =>
          Consequence.serviceUnavailable("subsystem is not available")
      }

    private def _workflow_start_event(
      endpoint: WorkflowEntrypoint,
      parameters: Map[String, String]
    ): ReceptionDomainEvent = {
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
        attributes = attributes
      )
    }

    private def _resolve_target_action(
      selector: String,
      parameters: Map[String, String]
    ): Consequence[(Component, Action)] =
      component.subsystem.map(_.operationResolver.resolve(selector)).getOrElse(OperationResolver.ResolutionResult.Invalid("subsystem is not available")) match {
        case OperationResolver.ResolutionResult.Resolved(_, componentName, serviceName, operationName) =>
          component.subsystem.flatMap(_.findComponent(componentName)) match {
            case Some(target) =>
              val request = Request.of(
                component = componentName,
                service = serviceName,
                operation = operationName,
                arguments = parameters.toVector.sortBy(_._1).map { case (k, v) => org.goldenport.protocol.Argument(k, v) }.toList
              )
              target.logic.makeOperationRequest(request).flatMap {
                case action: Action => Consequence.success((target, action))
                case _: OperationRequest => Consequence.argumentInvalid(s"JCL target is not action: $selector")
              }
            case None =>
              Consequence.operationNotFound(s"JCL target component: $componentName")
          }
        case OperationResolver.ResolutionResult.NotFound(_, s) =>
          Consequence.operationNotFound(s"JCL target action: $s")
        case OperationResolver.ResolutionResult.Ambiguous(s, candidates) =>
          Consequence.argumentInvalid(s"ambiguous JCL target action: $s => ${candidates.mkString(",")}")
        case OperationResolver.ResolutionResult.Invalid(message) =>
          Consequence.argumentInvalid(message)
      }
  }

  private final class DefaultJobAdminService(component: Component) extends JobAdminService {
    def cancelJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Cancel))

    def suspendJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Suspend))

    def resumeJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse] =
      component.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Resume))

    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]] =
      component.eventStore match {
        case Some(store) =>
          store.query(EventStore.Query()).map { records =>
            records.filter { record =>
              record.payload.get("job-id").contains(jobId.value) ||
              record.attributes.get("job-id").contains(jobId.value)
            }.map(_event_record)
          }
        case None =>
          Consequence.serviceUnavailable("event store is not available")
      }
  }

  private final class GetJobStatusOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "get_job_status",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        GetJobStatusAction(req, jobid)
      }
  }

  private final class LoadJobHistoryOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "load_job_history",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        LoadJobHistoryAction(req, jobid)
      }
  }

  private final class GetJobResultOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "get_job_result",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        GetJobResultAction(req, jobid)
      }
  }

  private final class AwaitJobResultOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "await_job_result",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        AwaitJobResultAction(req, jobid)
      }
  }

  private final class DescribeJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "describe_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _body(req).map(DescribeJobDefinitionAction(req, _))
  }

  private final class SubmitJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "submit_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _body(req).map(SubmitJobDefinitionAction(req, _))
  }

  private final class SubmitJobBatchOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "submit_job_batch",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _body(req).map(SubmitJobBatchAction(req, _))
  }

  private final class ControlJobOperationDefinition(
    name: String,
    command: JobControlCommand,
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = name,
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        ControlJobAction(req, jobid, command)
      }
  }

  private final class LoadJobEventsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "load_job_events",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        LoadJobEventsAction(req, jobid)
      }
  }

  private final case class GetJobStatusAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobStatusCall(core, jobId)
  }

  private final case class LoadJobHistoryAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobHistoryCall(core, jobId)
  }

  private final case class GetJobResultAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobResultCall(core, jobId)
  }

  private final case class AwaitJobResultAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      AwaitJobResultCall(core, jobId)
  }

  private final case class DescribeJobDefinitionAction(
    request: Request,
    body: String
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      DescribeJobDefinitionCall(core, body)
  }

  private final case class SubmitJobDefinitionAction(
    request: Request,
    body: String
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      SubmitJobDefinitionCall(core, body)
  }

  private final case class SubmitJobBatchAction(
    request: Request,
    body: String
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      SubmitJobBatchCall(core, body)
  }

  private final case class ControlJobAction(
    request: Request,
    jobId: JobId,
    command: JobControlCommand
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ControlJobCall(core, jobId, command)
  }

  private final case class LoadJobEventsAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobEventsCall(core, jobId)
  }

  private abstract class SyncJobAction extends CommandAction {
    override def commandExecutionMode: org.goldenport.cncf.action.CommandExecutionMode =
      org.goldenport.cncf.action.CommandExecutionMode.SyncDirectNoJob
  }

  private final case class GetJobStatusCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.getJobStatus(jobId)(using core.executionContext)) match {
            case Some(result) => result.map(model => OperationResponse.RecordResponse(_job_record(model)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class LoadJobHistoryCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.loadJobHistory(jobId)(using core.executionContext)) match {
            case Some(result) => result.map(page => OperationResponse.RecordResponse(_timeline_record(jobId, page)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetJobResultCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_result_response(core, jobId, _.getJobResult(jobId)(using core.executionContext))
  }

  private final case class AwaitJobResultCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.awaitJobResult(jobId)(using core.executionContext)) match {
            case Some(result) => result
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class DescribeJobDefinitionCall(
    core: ActionCall.Core,
    body: String
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.describeJobDefinition(body)) match {
            case Some(result) => result.map(model => OperationResponse.RecordResponse(model.toRecord))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class SubmitJobDefinitionCall(
    core: ActionCall.Core,
    body: String
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _jcl_submission_response(core, _.submitJobDefinition(body)(using core.executionContext))
  }

  private final case class SubmitJobBatchCall(
    core: ActionCall.Core,
    body: String
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _jcl_submission_response(core, _.submitJobBatch(body)(using core.executionContext))
  }

  private def _jcl_submission_response(
    core: ActionCall.Core,
    f: JobService => Consequence[JobBatchSubmissionResult]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        component.port.get[JobService].map(f) match {
          case Some(result) => result.map(x => OperationResponse.RecordResponse(x.toRecord))
          case None => Consequence.serviceUnavailable("job service is not available")
        }
      case None =>
        Consequence.serviceUnavailable("component is not initialized")
    }

  private def _job_result_response(
    core: ActionCall.Core,
    jobId: JobId,
    f: JobService => Consequence[JobResult]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        component.port.get[JobService].map(f) match {
          case Some(result) =>
            result.flatMap {
              case JobResult.Success(response) => Consequence.success(response)
              case JobResult.Failure(conclusion) => Consequence.Failure(conclusion)
            }
          case None =>
            Consequence.serviceUnavailable("job service is not available")
        }
      case None =>
        Consequence.serviceUnavailable("component is not initialized")
    }
  

  private final case class ControlJobCall(
    core: ActionCall.Core,
    jobId: JobId,
    command: JobControlCommand
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          given org.goldenport.cncf.context.ExecutionContext = core.executionContext
          component.port.get[JobAdminService] match {
            case Some(jobAdmin) =>
              val response = command match {
                case JobControlCommand.Cancel => jobAdmin.cancelJob(jobId)
                case JobControlCommand.Suspend => jobAdmin.suspendJob(jobId)
                case JobControlCommand.Resume => jobAdmin.resumeJob(jobId)
                case JobControlCommand.Retry => component.logic.controlJob(jobId, JobControlRequest(command))
              }
              response.map { response =>
                OperationResponse.RecordResponse(
                  Record.data(
                    "job-id" -> response.jobId.value,
                    "status" -> response.status.toString,
                    "async" -> response.async,
                    "response" -> response.response.map(_.print).getOrElse("")
                  )
                )
              }
            case None =>
              Consequence.serviceUnavailable("job admin service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class LoadJobEventsCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobAdminService].map(_.loadJobEvents(jobId)) match {
            case Some(result) =>
              result.map { records =>
                OperationResponse.RecordResponse(
                  Record.data(
                    "job-id" -> jobId.value,
                    "events" -> records
                  )
                )
              }
            case None =>
              Consequence.serviceUnavailable("job admin service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private def _job_id(req: Request): Consequence[JobId] =
    req.arguments.find(_.name == "id") match {
      case Some(arg) => JobId.parse(arg.value.toString)
      case None =>
        req.properties.find(_.name == "id") match {
          case Some(prop) => JobId.parse(prop.value.toString)
          case None => Consequence.argumentMissing("id")
        }
    }

  private def _body(req: Request): Consequence[String] =
    req.arguments.find(_.name == "body").map(_.value.toString).filter(_.trim.nonEmpty)
      .orElse(req.properties.find(_.name == "body").map(_.value.toString).filter(_.trim.nonEmpty))
      .orElse(req.properties.find(_.name == "http.body").map(_.value.toString).filter(_.trim.nonEmpty)) match {
      case Some(body) => Consequence.success(body)
      case None => Consequence.argumentMissing("body")
    }

  private def _job_matches(
    record: org.goldenport.cncf.event.EventRecord,
    jobId: JobId
  ): Boolean =
    record.payload.get("job-id").exists(_.toString == jobId.value) ||
      record.attributes.get("job-id").exists(_ == jobId.value)

  private def _job_record(model: JobQueryReadModel): Record =
    Record.data(
      "job-id" -> model.jobId.value,
      "status" -> model.status.toString,
      "persistence" -> model.persistence.toString,
      "origin" -> model.origin.toString,
      "submitter-principal-id" -> model.submitter.principalId,
      "submitter-subject-kind" -> model.submitter.subjectKind,
      "submitter-session-id" -> model.submitter.sessionId.getOrElse(""),
      "created-at" -> model.createdAt.toString,
      "updated-at" -> model.updatedAt.toString,
      "result-success" -> model.resultSummary.success,
      "result-message" -> model.resultSummary.message.getOrElse(""),
      "result" -> model.result.map(_.print).getOrElse(""),
      "event-triggered" -> model.lineage.eventTriggered,
      "event-name" -> model.lineage.eventName.getOrElse(""),
      "event-kind" -> model.lineage.eventKind.getOrElse(""),
      "parent-job-id" -> model.lineage.parentJobId.getOrElse(""),
      "correlation-id" -> model.lineage.correlationId.getOrElse(""),
      "causation-id" -> model.lineage.causationId.getOrElse(""),
      "source-subsystem" -> model.lineage.sourceSubsystem.getOrElse(""),
      "source-component" -> model.lineage.sourceComponent.getOrElse(""),
      "target-subsystem" -> model.lineage.targetSubsystem.getOrElse(""),
      "target-component" -> model.lineage.targetComponent.getOrElse(""),
      "reception-rule" -> model.lineage.receptionRule.getOrElse(""),
      "reception-policy" -> model.lineage.receptionPolicy.getOrElse(""),
      "policy-source" -> model.lineage.policySource.getOrElse(""),
      "job-relation" -> model.lineage.jobRelation.getOrElse(""),
      "task-relation" -> model.lineage.taskRelation.getOrElse(""),
      "transaction-relation" -> model.lineage.transactionRelation.getOrElse(""),
      "saga-relation" -> model.lineage.sagaRelation.getOrElse(""),
      "failure-policy" -> model.lineage.failurePolicy.getOrElse(""),
      "failure-disposition" -> model.lineage.failureDisposition.print,
      "retry-kind" -> model.retry.kind.print,
      "retry-attempt-count" -> model.retry.attemptCount,
      "retry-max-attempts" -> model.retry.maxAttempts,
      "retry-next-due-at" -> model.retry.nextRetryDueAt.map(_.toString).getOrElse(""),
      "retry-exhausted" -> model.retry.exhausted,
      "recovery-required" -> model.retry.recoveryRequired,
      "dead-letter" -> model.retry.deadLetter,
      "poison" -> model.retry.poison,
      "retry-user-action" -> model.retry.lastFailureUserAction.getOrElse(""),
      "task-count" -> model.tasks.totalCount,
      "tasks" -> model.tasks.tasks.map { task =>
        Record.data(
          "task-id" -> task.taskId.value,
          "parent-task-id" -> task.parentTaskId.map(_.value).getOrElse(""),
          "status" -> task.status.toString,
          "success" -> task.result.success,
          "message" -> task.result.message.getOrElse(""),
          "component" -> task.component.getOrElse(""),
          "service" -> task.service.getOrElse(""),
          "operation" -> task.operation.getOrElse(""),
          "started-at" -> task.startedAt.toString,
          "finished-at" -> task.finishedAt.map(_.toString).getOrElse("")
        )
      },
      "timeline" -> model.timeline.events.map(_timeline_event_record),
      "debug-request-summary" -> model.debug.requestSummary.getOrElse(""),
      "debug-execution-notes" -> model.debug.executionNotes,
      "debug-parameters" -> model.debug.parameters.toVector.sortBy(_._1).map { case (k, v) =>
        s"$k=$v"
      }
    )

  private def _timeline_record(
    jobId: JobId,
    page: JobTimelinePage
  ): Record =
    Record.data(
      "job-id" -> jobId.value,
      "offset" -> page.offset,
      "limit" -> page.limit,
      "total-count" -> page.totalCount,
      "fetched-count" -> page.fetchedCount,
      "events" -> page.events.map(_timeline_event_record)
    )

  private def _timeline_event_record(
    event: org.goldenport.cncf.job.JobTimelineEvent
  ): Record =
    Record.data(
      "sequence" -> event.sequence,
      "occurred-at" -> event.occurredAt.toString,
      "kind" -> event.kind,
      "task-id" -> event.taskId.map(_.value).getOrElse(""),
      "parent-task-id" -> event.parentTaskId.map(_.value).getOrElse(""),
      "note" -> event.note.getOrElse("")
    )

  private def _event_record(
    event: org.goldenport.cncf.event.EventRecord
  ): Record =
    Record.data(
      "event-id" -> event.id.value,
      "name" -> event.name,
      "kind" -> event.kind,
      "sequence" -> event.sequence,
      "created-at" -> event.createdAt.toString,
      "lane" -> event.lane.value,
      "persistent" -> event.persistent,
      "payload" -> event.payload.toVector.sortBy(_._1).map { case (k, v) =>
        Record.data(k -> v)
      }
    )
}
