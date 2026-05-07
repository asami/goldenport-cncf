package org.goldenport.cncf.component.builtin.jobcontrol

import java.time.Duration
import scala.collection.concurrent.TrieMap
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.entity.runtime.{EntityKind, EntityMemoryPolicy, EntityRuntimeDescriptor, PartitionStrategy, WorkingSetPolicy, WorkingSetPolicyEvaluator, WorkingSetPolicySource}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobBatchDefinition, JobBatchSubmissionResult, JobControlCommand, JobControlRequest, JobDefinition, JobDefinitionEntity, JobDefinitionSnapshot, JobDefinitionStatus, JobFailureHook, JobId, JobPersistencePolicy, JobProfileComparison, JobProfileReconstructor, JobResult, JobSubmitOption, JobTaskDetail, JobTraceTree, TaskId}
import org.goldenport.cncf.job.{JobEntityCollections, JobQueryReadModel, JobTimelinePage}
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
 *  version Apr. 22, 2026
 * @version May.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponent() extends Component {
  override def componentDescriptors: Vector[ComponentDescriptor] =
    super.componentDescriptors ++ JobControlComponent.componentDescriptors
}

object JobControlComponent {
  trait JobService {
    def getJobStatus(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobQueryReadModel]
    def loadJobHistory(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTimelinePage]
    def getJobCalltree(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def getTaskExecutionTree(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTraceTree]
    def getTaskDetail(jobId: JobId, taskId: TaskId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTaskDetail]
    def getJobResult(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobResult]
    def awaitJobResult(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[OperationResponse]
    def describeJobDefinition(body: String): Consequence[JobBatchDefinition]
    def submitJobDefinition(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult]
    def submitJobBatch(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult]
    def compareJobProfile(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def reconstructJobProfile(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def createJobDefinition(key: String, body: String, status: Option[String])(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def updateJobDefinition(key: String, body: String, status: Option[String])(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def activateJobDefinition(key: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def retireJobDefinition(key: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def getJobDefinition(key: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def searchJobDefinitions()(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
  }

  trait JobAdminService {
    def cancelJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def suspendJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def resumeJob(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]]
  }

  val name: String = "job_control"
  val componentId: ComponentId = ComponentId(name)

  def componentDescriptors: Vector[ComponentDescriptor] =
    Vector(ComponentDescriptor(
      componentName = Some(name),
      entityRuntimeDescriptors = Vector(
        EntityRuntimeDescriptor(
          entityName = "job",
          collectionId = JobEntityCollections.Job,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 12,
          maxEntitiesPerPartition = 10000,
          entityKind = EntityKind.System,
          entityKindExplicit = true,
          workingSetPolicy = Some(WorkingSetPolicy.Recent(Duration.ofDays(1), "updatedAt")),
          workingSetPolicySource = Some(WorkingSetPolicySource.Code)
        ),
        EntityRuntimeDescriptor(
          entityName = "jobDefinition",
          collectionId = JobEntityCollections.JobDefinition,
          memoryPolicy = EntityMemoryPolicy.LoadToMemory,
          partitionStrategy = PartitionStrategy.byOrganizationMonthUTC,
          maxPartitions = 4,
          maxEntitiesPerPartition = 10000,
          entityKind = EntityKind.System,
          entityKindExplicit = true,
          workingSetPolicy = Some(WorkingSetPolicy.Custom("active-job-definition", _ActiveJobDefinitionWorkingSetPolicy)),
          workingSetPolicySource = Some(WorkingSetPolicySource.Code)
        )
      )
    ))

  private object _ActiveJobDefinitionWorkingSetPolicy extends WorkingSetPolicyEvaluator {
    def isResident(
      record: Record,
      now: java.time.Instant
    ): Boolean = {
      val _ = now
      record.getString("definitionStatus").exists(_.trim.equalsIgnoreCase("active"))
    }
  }

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
      val getJobCalltree = new GetJobCalltreeOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val getTaskExecutionTree = new GetTaskExecutionTreeOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val getTaskDetail = new GetTaskDetailOperationDefinition(request = _job_task_request, response = spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val getJobResult = new GetJobResultOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("JobResult"))))
      val awaitJobResult = new AwaitJobResultOperationDefinition(request = idrequest, response = spec.ResponseDefinition(result = List(DataType.Named("OperationResponse"))))
      val bodyrequest = _body_request
      val describeJobDefinition = new DescribeJobDefinitionOperationDefinition(bodyrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val submitJobDefinition = new SubmitJobDefinitionOperationDefinition(bodyrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val submitJobBatch = new SubmitJobBatchOperationDefinition(bodyrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val compareJobProfile = new CompareJobProfileOperationDefinition(idrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val reconstructJobProfile = new ReconstructJobProfileOperationDefinition(idrequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val definitionRequest = _job_definition_request
      val definitionKeyRequest = _job_definition_key_request
      val createJobDefinition = new CreateJobDefinitionOperationDefinition(definitionRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val updateJobDefinition = new UpdateJobDefinitionOperationDefinition(definitionRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val activateJobDefinition = new ActivateJobDefinitionOperationDefinition(definitionKeyRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val retireJobDefinition = new RetireJobDefinitionOperationDefinition(definitionKeyRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val getJobDefinition = new GetJobDefinitionOperationDefinition(definitionKeyRequest, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
      val searchJobDefinitions = new SearchJobDefinitionsOperationDefinition(request, spec.ResponseDefinition(result = List(DataType.Named("Record"))))
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
          operations = NonEmptyVector.of(
            getJobStatus,
            loadJobHistory,
            getJobCalltree,
            getTaskExecutionTree,
            getTaskDetail,
            getJobResult,
            awaitJobResult,
            describeJobDefinition,
            submitJobDefinition,
            submitJobBatch,
            compareJobProfile,
            reconstructJobProfile,
            createJobDefinition,
            updateJobDefinition,
            activateJobDefinition,
            retireJobDefinition,
            getJobDefinition,
            searchJobDefinitions
          )
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

    private def _job_task_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(content = BaseContent.simple("id"), kind = spec.ParameterDefinition.Kind.Argument),
          spec.ParameterDefinition(content = BaseContent.simple("taskId"), kind = spec.ParameterDefinition.Kind.Argument)
        )
      )

    private def _job_definition_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(content = BaseContent.simple("key"), kind = spec.ParameterDefinition.Kind.Argument),
          spec.ParameterDefinition(content = BaseContent.simple("body"), kind = spec.ParameterDefinition.Kind.Argument),
          spec.ParameterDefinition(content = BaseContent.simple("status"), kind = spec.ParameterDefinition.Kind.Argument)
        )
      )

    private def _job_definition_key_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(content = BaseContent.simple("key"), kind = spec.ParameterDefinition.Kind.Argument))
      )
  }

  private final class DefaultJobService(component: Component) extends JobService {
    private val _definitions: TrieMap[String, JobDefinitionEntity] =
      TrieMap.empty

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

    def getJobCalltree(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      component.jobEngine.queryVisible(jobId).map {
        case Some(model) =>
          _job_calltree_record(model)
        case None =>
          _job_calltree_not_found_record(jobId)
      }

    def getTaskExecutionTree(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTraceTree] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.jobEngine.queryTaskExecutionTree(jobId) match {
            case Some(tree) => Consequence.success(tree)
            case None => Consequence.operationNotFound(s"job task tree:${jobId.value}")
          }
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def getTaskDetail(jobId: JobId, taskId: TaskId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobTaskDetail] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(_) =>
          component.jobEngine.queryTaskDetail(jobId, taskId) match {
            case Some(detail) => Consequence.success(detail)
            case None => Consequence.operationNotFound(s"job task:${jobId.value}/${taskId.value}")
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
      _submit_definition_ref(body) match {
        case Some(ref) =>
          _definition_by_ref(ref).flatMap { definition =>
            _submit_definition_entity(definition)
          }
        case None =>
          JobBatchDefinition.parseYaml(body).flatMap { batch =>
            if (batch.jobs.size != 1)
              Consequence.argumentInvalid("submit_job_definition requires exactly one job in jobs[]")
            else
              _submit_batch(batch, None)
          }
      }

    def submitJobBatch(body: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      JobBatchDefinition.parseYaml(body).flatMap(_submit_batch(_, None))

    def compareJobProfile(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(model) => Consequence.success(JobProfileComparison.compare(model).toRecord)
        case None => Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def reconstructJobProfile(jobId: JobId)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      component.jobEngine.queryVisible(jobId).flatMap {
        case Some(model) =>
          Consequence.success(JobBatchDefinition(
            Vector(JobProfileReconstructor.reconstruct(model)),
            org.goldenport.cncf.job.JobJclRootKind.SingleJob
          ).toRecord)
        case None =>
          Consequence.operationNotFound(s"job:${jobId.value}")
      }

    def createJobDefinition(
      key: String,
      body: String,
      status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      if (_definitions.contains(_normalize_definition_key(key)))
        Consequence.stateConflict(s"JobDefinition already exists: $key")
      else
        _definition_entity(key, body, status.getOrElse("draft")).flatMap { entity =>
          _save_definition(entity).map(_.toRecord())
        }

    def updateJobDefinition(
      key: String,
      body: String,
      status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _definition_by_ref(key).flatMap { current =>
        for {
          parsed <- _definition_payload(key, body, status)
          updated = JobDefinitionEntity.updated(
            current = current,
            jclSource = body,
            profile = parsed._1.profile,
            flowSource = parsed._1.flow.map(_.show),
            eventsSource = parsed._1.events.map(_.show),
            onEventSource = parsed._1.onEvent.map(_.show),
            status = parsed._2,
            targetAction = parsed._1.target.action
          )
          saved <- _save_definition(updated)
        } yield saved.toRecord()
      }

    def activateJobDefinition(key: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _change_definition_status(key, JobDefinitionStatus.Active)

    def retireJobDefinition(key: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _change_definition_status(key, JobDefinitionStatus.Retired)

    def getJobDefinition(key: String)(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _definition_by_ref(key).map(_.toRecord())

    def searchJobDefinitions()(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      Consequence.success(
        Record.data(
          "jobDefinitions" -> _definitions.values.toVector.sortBy(_.key).map(_.toRecord())
        )
      )

    private def _submit_batch(
      batch: JobBatchDefinition,
      snapshot: Option[JobDefinitionSnapshot]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      _submit_jobs(batch.jobs, Vector.empty, snapshot)

    private def _submit_jobs(
      jobs: Vector[JobDefinition],
      submitted: Vector[JobId],
      snapshot: Option[JobDefinitionSnapshot],
      index: Int = 0
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      jobs.headOption match {
        case None =>
          Consequence.success(JobBatchSubmissionResult(submitted, success = true))
        case Some(job) =>
          _submit_one(job, snapshot).flatMap { submission =>
            val updated = submitted ++ submission.jobIds
            submission.response match {
              case Consequence.Success(_) =>
                _submit_jobs(jobs.drop(1), updated, snapshot, index + 1)
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
            persistence = JobPersistencePolicy.Persistent,
            declaredProfile = None
          ).map { case (jobid, response) =>
            response match {
              case Consequence.Success(_) => (Some(jobid), None)
              case Consequence.Failure(conclusion) => (Some(jobid), Some(conclusion.show))
            }
          }
      }

    private def _submit_one(
      job: JobDefinition,
      snapshot: Option[JobDefinitionSnapshot]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[_Submission] =
      job.target match {
        case x if x.action.nonEmpty =>
          _submit_action(
            selector = x.action.get,
            parameters = job.parameters,
            requestSummary = job.submit.requestSummary.orElse(Some(job.name)),
            persistence = job.submit.persistence,
            declaredProfile = job.profile,
            definitionSnapshot = snapshot,
            compensation = job.compensation
          ).map { case (jobid, response) =>
            _Submission(Vector(jobid), response)
          }
        case x if x.workflow.nonEmpty =>
          _submit_workflow(
            entry = x.workflow.get,
            parameters = job.parameters,
            requestSummary = job.submit.requestSummary.orElse(Some(job.name)),
            declaredProfile = job.profile,
            definitionSnapshot = snapshot
          )
        case _ =>
          Consequence.argumentInvalid("JCL target must contain action or workflow")
      }

    private def _submit_action(
      selector: String,
      parameters: Map[String, String],
      requestSummary: Option[String],
      persistence: JobPersistencePolicy,
      declaredProfile: Option[org.goldenport.cncf.job.JobDeclaredProfile],
      definitionSnapshot: Option[JobDefinitionSnapshot] = None,
      compensation: Option[JobFailureHook] = None
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[(JobId, Consequence[OperationResponse])] =
      _resolve_target_action(selector, parameters).flatMap { case (target, action) =>
        _resolve_compensation_task(compensation, parameters).flatMap { comp =>
          val task = ActionTask(
            ActionId.generate(),
            action,
            target.actionEngine,
            Some(target),
            compensationActionRef = compensation.map(_.action),
            compensationTask = comp
          )
          val option = JobSubmitOption(
            persistence = persistence,
            requestSummary = requestSummary,
            parameters = parameters ++ Map("jcl.target.action" -> selector) ++ compensation.map(h => "jcl.compensation.action" -> h.action),
            executionNotes = Vector("jcl submission"),
            declaredProfile = declaredProfile,
            jobDefinitionSnapshot = definitionSnapshot
          )
          component.jobEngine.submit(List(task), summon[org.goldenport.cncf.context.ExecutionContext], option).map { jobid =>
            (jobid, component.logic.awaitJobResult(jobid))
          }
        }
      }

    private def _resolve_compensation_task(
      compensation: Option[JobFailureHook],
      parameters: Map[String, String]
    ): Consequence[Option[ActionTask]] =
      compensation match {
        case None => Consequence.success(None)
        case Some(hook) =>
          _resolve_target_action(hook.action, parameters ++ hook.parameters).map { case (target, action) =>
            Some(ActionTask(ActionId.generate(), action, target.actionEngine, Some(target)))
          }
      }

    private def _submit_workflow(
      entry: org.goldenport.cncf.job.JobWorkflowTarget,
      parameters: Map[String, String],
      requestSummary: Option[String],
      declaredProfile: Option[org.goldenport.cncf.job.JobDeclaredProfile],
      definitionSnapshot: Option[JobDefinitionSnapshot] = None
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[_Submission] =
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

    private def _submit_definition_entity(
      entity: JobDefinitionEntity
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobBatchSubmissionResult] =
      if (!entity.isActive)
        Consequence.argumentInvalid(s"JobDefinition is not active: ${entity.key}")
      else
        JobBatchDefinition.parseYaml(entity.jclSource).flatMap { batch =>
          if (batch.jobs.size != 1)
            Consequence.argumentInvalid(s"JobDefinition must contain exactly one job: ${entity.key}")
          else
            _submit_batch(batch, Some(JobDefinitionSnapshot.from(entity)))
        }

    private def _definition_entity(
      key: String,
      body: String,
      status: String
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] =
      _definition_payload(key, body, Some(status)).map { case (job, parsedStatus) =>
        JobDefinitionEntity.create(
          key = key,
          jclSource = body,
          profile = job.profile,
          flowSource = job.flow.map(_.show),
          eventsSource = job.events.map(_.show),
          onEventSource = job.onEvent.map(_.show),
          status = parsedStatus.getOrElse(JobDefinitionStatus.Draft),
          targetAction = job.target.action
        )
      }

    private def _definition_payload(
      key: String,
      body: String,
      status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[(JobDefinition, Option[JobDefinitionStatus])] =
      for {
        parsedStatus <- status.map(s => JobDefinitionStatus.parse(s).map(Some(_))).getOrElse(Consequence.success(None))
        batch <- JobBatchDefinition.parseYaml(body)
        _ <- if (batch.jobs.size == 1) Consequence.unit else Consequence.argumentInvalid(s"JobDefinition must contain exactly one job: $key")
      } yield (batch.jobs.head, parsedStatus)

    private def _save_definition(
      entity: JobDefinitionEntity
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] =
      EntityStore.standard().save(entity)(using JobDefinitionEntity.entityPersistent, summon[org.goldenport.cncf.context.ExecutionContext]).map { _ =>
        _definitions.put(entity.key, entity)
        entity
      }

    private def _change_definition_status(
      key: String,
      status: JobDefinitionStatus
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record] =
      _definition_by_ref(key).flatMap { current =>
        val updated = current.copy(status = status, revision = current.revision + 1, updatedAt = java.time.Instant.now())
        _save_definition(updated).map(_.toRecord())
      }

    private def _definition_by_ref(
      ref: String
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[JobDefinitionEntity] =
      _definitions.get(_normalize_definition_key(ref)) match {
        case Some(entity) => Consequence.success(entity)
        case None =>
          EntityStore.standard().load[JobDefinitionEntity](JobDefinitionEntity.entityId(ref))(using JobDefinitionEntity.entityPersistent, summon[org.goldenport.cncf.context.ExecutionContext]).flatMap {
            case Some(entity) =>
              _definitions.put(entity.key, entity)
              Consequence.success(entity)
            case None =>
              Consequence.operationNotFound(s"JobDefinition:$ref")
          }
      }

    private def _submit_definition_ref(body: String): Option[String] =
      "(?m)^\\s*jobDefinitionRef\\s*:\\s*([^\\s#]+)\\s*$".r
        .findFirstMatchIn(body)
        .map(_.group(1).trim)

    private def _normalize_definition_key(key: String): String =
      key.trim

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

  private final class GetJobCalltreeOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "get_job_calltree",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        GetJobCalltreeAction(req, jobid)
      }
  }

  private final class GetTaskExecutionTreeOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "get_task_execution_tree",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map { jobid =>
        GetTaskExecutionTreeAction(req, jobid)
      }
  }

  private final class GetTaskDetailOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
        spec.OperationDefinition.Specification(
        name = "get_task_detail",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      for {
        jobid <- _job_id(req)
        taskid <- _task_id(req)
      } yield GetTaskDetailAction(req, jobid, taskid)
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

  private final class CompareJobProfileOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "compare_job_profile",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map(CompareJobProfileAction(req, _))
  }

  private final class ReconstructJobProfileOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "reconstruct_job_profile",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map(ReconstructJobProfileAction(req, _))
  }

  private final class CreateJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "create_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      for {
        key <- _key(req)
        body <- _body(req)
      } yield CreateJobDefinitionAction(req, key, body, _status(req))
  }

  private final class UpdateJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "update_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      for {
        key <- _key(req)
        body <- _body(req)
      } yield UpdateJobDefinitionAction(req, key, body, _status(req))
  }

  private final class ActivateJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "activate_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _key(req).map(ActivateJobDefinitionAction(req, _))
  }

  private final class RetireJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "retire_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _key(req).map(RetireJobDefinitionAction(req, _))
  }

  private final class GetJobDefinitionOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "get_job_definition",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _key(req).map(GetJobDefinitionAction(req, _))
  }

  private final class SearchJobDefinitionsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "search_job_definitions",
        request = request,
        response = response
      )

    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      Consequence.success(SearchJobDefinitionsAction(req))
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

  private final case class GetJobCalltreeAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobCalltreeCall(core, jobId)
  }

  private final case class GetTaskExecutionTreeAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetTaskExecutionTreeCall(core, jobId)
  }

  private final case class GetTaskDetailAction(
    request: Request,
    jobId: JobId,
    taskId: TaskId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetTaskDetailCall(core, jobId, taskId)
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

  private final case class CompareJobProfileAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      CompareJobProfileCall(core, jobId)
  }

  private final case class ReconstructJobProfileAction(
    request: Request,
    jobId: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ReconstructJobProfileCall(core, jobId)
  }

  private final case class CreateJobDefinitionAction(
    request: Request,
    key: String,
    body: String,
    status: Option[String]
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      CreateJobDefinitionCall(core, key, body, status)
  }

  private final case class UpdateJobDefinitionAction(
    request: Request,
    key: String,
    body: String,
    status: Option[String]
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      UpdateJobDefinitionCall(core, key, body, status)
  }

  private final case class ActivateJobDefinitionAction(
    request: Request,
    key: String
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ActivateJobDefinitionCall(core, key)
  }

  private final case class RetireJobDefinitionAction(
    request: Request,
    key: String
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      RetireJobDefinitionCall(core, key)
  }

  private final case class GetJobDefinitionAction(
    request: Request,
    key: String
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobDefinitionCall(core, key)
  }

  private final case class SearchJobDefinitionsAction(
    request: Request
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      SearchJobDefinitionsCall(core)
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

  private final case class GetJobCalltreeCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.getJobCalltree(jobId)(using core.executionContext)) match {
            case Some(result) => result.map(OperationResponse.RecordResponse.apply)
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetTaskExecutionTreeCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.getTaskExecutionTree(jobId)(using core.executionContext)) match {
            case Some(result) => result.map(tree => OperationResponse.RecordResponse(_task_tree_record(tree)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetTaskDetailCall(
    core: ActionCall.Core,
    jobId: JobId,
    taskId: TaskId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.getTaskDetail(jobId, taskId)(using core.executionContext)) match {
            case Some(result) => result.map(detail => OperationResponse.RecordResponse(_task_detail_record(detail)))
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

  private final case class CompareJobProfileCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.compareJobProfile(jobId)(using core.executionContext))
  }

  private final case class ReconstructJobProfileCall(
    core: ActionCall.Core,
    jobId: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.reconstructJobProfile(jobId)(using core.executionContext))
  }

  private final case class CreateJobDefinitionCall(
    core: ActionCall.Core,
    key: String,
    body: String,
    status: Option[String]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.createJobDefinition(key, body, status)(using core.executionContext))
  }

  private final case class UpdateJobDefinitionCall(
    core: ActionCall.Core,
    key: String,
    body: String,
    status: Option[String]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.updateJobDefinition(key, body, status)(using core.executionContext))
  }

  private final case class ActivateJobDefinitionCall(
    core: ActionCall.Core,
    key: String
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.activateJobDefinition(key)(using core.executionContext))
  }

  private final case class RetireJobDefinitionCall(
    core: ActionCall.Core,
    key: String
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.retireJobDefinition(key)(using core.executionContext))
  }

  private final case class GetJobDefinitionCall(
    core: ActionCall.Core,
    key: String
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.getJobDefinition(key)(using core.executionContext))
  }

  private final case class SearchJobDefinitionsCall(
    core: ActionCall.Core
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.searchJobDefinitions()(using core.executionContext))
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

  private def _job_profile_response(
    core: ActionCall.Core,
    f: JobService => Consequence[Record]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        component.port.get[JobService].map(f) match {
          case Some(result) => result.map(OperationResponse.RecordResponse.apply)
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

  private def _task_id(req: Request): Consequence[TaskId] =
    _string_argument(req, "taskId")
      .orElse(_string_argument(req, "task-id")) match {
        case Some(value) => TaskId.parse(value)
        case None => Consequence.argumentMissing("taskId")
      }

  private def _body(req: Request): Consequence[String] =
    req.arguments.find(_.name == "body").map(_.value.toString).filter(_.trim.nonEmpty)
      .orElse(req.properties.find(_.name == "body").map(_.value.toString).filter(_.trim.nonEmpty))
      .orElse(req.properties.find(_.name == "http.body").map(_.value.toString).filter(_.trim.nonEmpty)) match {
      case Some(body) => Consequence.success(body)
      case None => Consequence.argumentMissing("body")
    }

  private def _key(req: Request): Consequence[String] =
    _string_argument(req, "key")
      .orElse(_string_argument(req, "jobDefinitionRef")) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.argumentMissing("key")
      }

  private def _status(req: Request): Option[String] =
    _string_argument(req, "status")

  private def _string_argument(req: Request, name: String): Option[String] =
    req.arguments.find(_.name == name).map(_.value.toString).filter(_.trim.nonEmpty)
      .orElse(req.properties.find(_.name == name).map(_.value.toString).filter(_.trim.nonEmpty))
      .map(_.trim)

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
      "scheduled-start-at" -> model.scheduledStartAt.map(_.toString).getOrElse(""),
      "result-success" -> model.resultSummary.success,
      "result-message" -> model.resultSummary.message.getOrElse(""),
      "result" -> model.result.map(_.print).getOrElse(""),
      "event-triggered" -> model.lineage.eventTriggered,
      "event-name" -> model.lineage.eventName.getOrElse(""),
      "event-kind" -> model.lineage.eventKind.getOrElse(""),
      "parent-job-id" -> model.lineage.parentJobId.getOrElse(""),
      "correlation-id" -> model.lineage.correlationId.getOrElse(""),
      "saga-id" -> model.lineage.sagaId.getOrElse(""),
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
      "calltree" -> model.calltree.map(_.show).getOrElse(""),
      "calltree-saved" -> model.debug.calltreeSaved,
      "calltree-storage" -> model.debug.calltreeStorage.getOrElse(""),
      "calltree-serialized-bytes" -> model.debug.calltreeSerializedBytes.getOrElse(0),
      "calltree-drop-reason" -> model.debug.calltreeDropReason.getOrElse(""),
      "task-count" -> model.tasks.totalCount,
      "tasks" -> model.tasks.tasks.map { task =>
        _task_record(task)
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

  private def _job_calltree_record(
    model: JobQueryReadModel
  ): Record =
    Record.data(
      "job-id" -> model.jobId.value,
      "calltree-saved" -> model.debug.calltreeSaved,
      "calltree-storage" -> model.debug.calltreeStorage.getOrElse(""),
      "calltree-serialized-bytes" -> model.debug.calltreeSerializedBytes.getOrElse(0),
      "calltree-drop-reason" -> model.debug.calltreeDropReason.getOrElse(""),
      "calltree" -> model.calltree.getOrElse(Record.empty)
    )

  private def _job_calltree_not_found_record(
    jobId: JobId
  ): Record =
    Record.data(
      "job-id" -> jobId.value,
      "calltree-saved" -> false,
      "calltree-drop-reason" -> "job_not_found",
      "calltree" -> Record.empty
    )

  private def _task_tree_record(
    tree: JobTraceTree
  ): Record =
    Record.data(
      "job-id" -> tree.jobId.value,
      "roots" -> tree.roots.map(_task_node_record)
    )

  private def _task_detail_record(
    detail: JobTaskDetail
  ): Record =
    Record.data(
      "job-id" -> detail.jobId.value,
      "task" -> _task_record(detail.task),
      "events" -> detail.events.map(_timeline_event_record),
      "children" -> detail.children.map(_task_node_record)
    )

  private def _task_node_record(
    node: org.goldenport.cncf.job.JobTraceTaskNode
  ): Record =
    Record.data(
      "task-id" -> node.taskId.value,
      "parent-task-id" -> node.parentTaskId.map(_.value).getOrElse(""),
      "task-kind" -> node.taskKind,
      "relation" -> node.relation.getOrElse(""),
      "status" -> node.status.toString,
      "transaction-outcome" -> node.transactionOutcome.getOrElse(""),
      "compensation-status" -> node.compensationStatus.getOrElse(""),
      "recovery-required" -> node.recoveryRequired,
      "events" -> node.events.map(_timeline_event_record),
      "children" -> node.children.map(_task_node_record)
    )

  private def _task_record(
    task: org.goldenport.cncf.job.JobTaskReadModel
  ): Record =
    Record.data(
      "task-id" -> task.taskId.value,
      "parent-task-id" -> task.parentTaskId.map(_.value).getOrElse(""),
      "status" -> task.status.toString,
      "success" -> task.result.success,
      "message" -> task.result.message.getOrElse(""),
      "component" -> task.component.getOrElse(""),
      "service" -> task.service.getOrElse(""),
      "operation" -> task.operation.getOrElse(""),
      "task-kind" -> task.taskKind,
      "target-kind" -> task.targetKind.getOrElse(""),
      "relation" -> task.relation.getOrElse(""),
      "transaction-outcome" -> task.transactionOutcome.getOrElse(""),
      "compensation-action-ref" -> task.compensationActionRef.getOrElse(""),
      "compensates-task-id" -> task.compensatesTaskId.map(_.value).getOrElse(""),
      "compensation-status" -> task.compensationStatus.getOrElse(""),
      "compensation-failure-summary" -> task.compensationFailureSummary.getOrElse(""),
      "recovery-required" -> task.recoveryRequired,
      "started-at" -> task.startedAt.toString,
      "finished-at" -> task.finishedAt.map(_.toString).getOrElse("")
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
