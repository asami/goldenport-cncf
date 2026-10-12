package org.goldenport.cncf.component.builtin.jobcontrol

import java.time.Duration
import scala.collection.concurrent.TrieMap
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{
  ActionCall,
  CommandAction,
  ProcedureActionCall,
  QueryAction
}
import org.goldenport.cncf.component.{
  Component,
  ComponentCreate,
  ComponentDescriptor,
  ComponentId,
  ComponentInstanceId,
  EntityRuntimePlanProvider
}
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.{
  EntityPersistentCreate,
  EntityMutationExecutionPolicy,
  EntityQuery,
  EntityRevisionModelKind,
  EntityRevisionRepresentation,
  EntitySearchScope,
  EntitySnapshot,
  EntityStore
}
import org.simplemodeling.model.datatype.EntityRevision
import org.goldenport.cncf.entity.runtime.{
  EntityKind,
  EntityMemoryPolicy,
  EntityRuntimeDescriptor,
  EntityRuntimePlan,
  PartitionStrategy,
  WorkingSetPolicy,
  WorkingSetPolicyEvaluator,
  WorkingSetPolicySource
}
import org.goldenport.cncf.job.{
  JobBatchDefinition,
  JobBatchSubmissionResult,
  JobControlCommand,
  JobControlRequest,
  JobDefinition,
  JobDefinitionEntity,
  JobDefinitionId,
  JobDefinitionSnapshot,
  JobDefinitionStatus,
  JobFailureHook,
  JobId,
  JobPersistencePolicy,
  JobProfileComparison,
  JobProfileReconstructor,
  JobResult,
  JobTaskDetail,
  JobTraceTree,
  TaskId
}
import org.goldenport.cncf.job.{
  JobDataOrigin,
  JobExperienceService,
  JobEntityCollections,
  JobManagementDetail,
  JobManagementPage,
  JobManagementQuery,
  JobManagementResult,
  JobManagementSummary,
  JobQueryReadModel,
  JobStatus,
  JobTaskPage,
  JobTimelinePage
}
import org.goldenport.cncf.event.EventStore
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection}
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.Request
import org.goldenport.protocol.handler.ProtocolHandler
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.RecordFormat
import org.goldenport.schema.DataType
import org.goldenport.value.BaseContent

/*
 * @since   Mar. 28, 2026
 *  version Mar. 29, 2026
 *  version Apr. 22, 2026
 *  version May. 31, 2026
 *  version Aug.  8, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponent() extends Component with EntityRuntimePlanProvider {
  override def displayName: String = JobControlComponent.name
  override def componentDescriptors: Vector[ComponentDescriptor] =
    super.componentDescriptors ++ JobControlComponent.componentDescriptors
  override def entityRuntimePlans: Vector[EntityRuntimePlan[Any]] =
    JobControlComponent.componentDescriptors
      .flatMap(_.entityRuntimeDescriptors)
      .map(_.toPlan)
}

object JobControlComponent  extends JobControlRuntimeSupport with JobControlRecordSupport {
  trait JobService {
    def listManagementJobs(request: JobManagementQuery)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobManagementPage]
    def getManagementJobDetail(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobManagementDetail]
    def getManagementJobResult(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobManagementResult]
    def listManagementJobTasks(jobId: JobId, offset: Int, limit: Int)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTaskPage]
    def listManagementJobTimeline(jobId: JobId, offset: Int, limit: Int)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTimelinePage]
    def getManagementJobTaskExecutionTree(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTraceTree]
    def getManagementJobTaskDetail(jobId: JobId, taskId: TaskId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTaskDetail]
    def getJobStatus(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobQueryReadModel]
    def loadJobHistory(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTimelinePage]
    def getJobCalltree(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
    def getTaskExecutionTree(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTraceTree]
    def getTaskDetail(jobId: JobId, taskId: TaskId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobTaskDetail]
    def getJobResult(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobResult]
    def awaitJobResult(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[OperationResponse]
    def describeJobDefinition(body: String, format: RecordFormat): Consequence[JobBatchDefinition]
    def submitJobDefinition(body: String, format: RecordFormat)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobBatchSubmissionResult]
    def submitJobBatch(body: String, format: RecordFormat)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[JobBatchSubmissionResult]
    def compareJobProfile(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
    def reconstructJobProfile(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
    def createJobDefinition(
        key: String,
        body: String,
        format: RecordFormat,
        status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def updateJobDefinition(
        key: String,
        body: String,
        format: RecordFormat,
        status: Option[String]
    )(using org.goldenport.cncf.context.ExecutionContext): Consequence[Record]
    def activateJobDefinition(key: String)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
    def retireJobDefinition(key: String)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
    def getJobDefinition(key: String)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
    def searchJobDefinitions()(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[Record]
  }

  trait JobAdminService {
    def cancelJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def suspendJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def resumeJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def retryJob(jobId: JobId)(using
        org.goldenport.cncf.context.ExecutionContext
    ): Consequence[org.goldenport.cncf.job.JobControlResponse]
    def loadJobEvents(jobId: JobId): Consequence[Vector[Record]]
  }

  val name: String = "job_control"
  val componentId: ComponentId = org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL

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
          revisionModelKind = Some(EntityRevisionModelKind.NonSimpleEntity),
          revisionRepresentation =
            Some(EntityRevisionRepresentation.Detached),
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
          revisionModelKind = Some(EntityRevisionModelKind.NonSimpleEntity),
          revisionRepresentation =
            Some(EntityRevisionRepresentation.Detached),
          workingSetPolicy = Some(WorkingSetPolicy.Custom(
            "active-job-definition",
            ActiveJobDefinitionWorkingSetPolicy
          )),
          workingSetPolicySource = Some(WorkingSetPolicySource.Code)
        )
      )
    ))

  private object ActiveJobDefinitionWorkingSetPolicy extends WorkingSetPolicyEvaluator {
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
      val managementlistrequest = JobManagementProtocol.listRequest
      val managementpagerequest = JobManagementProtocol.pageRequest
      val getjobstatus = new GetJobStatusOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobQueryReadModel")))
      )
      val loadjobhistory = new LoadJobHistoryOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobTimelinePage")))
      )
      val getjobcalltree = new GetJobCalltreeOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val gettaskexecutiontree = new GetTaskExecutionTreeOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val gettaskdetail = new GetTaskDetailOperationDefinition(
        request = _job_task_request,
        response = spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val getjobresult = new GetJobResultOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobResult")))
      )
      val awaitjobresult = new AwaitJobResultOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("OperationResponse")))
      )
      val listmanagementjobs = JobManagementProtocol.listManagementJobsOperationDefinition(
        managementlistrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val getmanagementjobdetail = JobManagementProtocol.getManagementJobDetailOperationDefinition(
        idrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val getmanagementjobresult = JobManagementProtocol.getManagementJobResultOperationDefinition(
        idrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val listmanagementjobtasks = JobManagementProtocol.listManagementJobTasksOperationDefinition(
        managementpagerequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val listmanagementjobtimeline = JobManagementProtocol.listManagementJobTimelineOperationDefinition(
        managementpagerequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val getmanagementjobtaskexecutiontree = JobManagementProtocol.getManagementJobTaskExecutionTreeOperationDefinition(
        idrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val getmanagementjobtaskdetail = JobManagementProtocol.getManagementJobTaskDetailOperationDefinition(
        _job_task_request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val bodyrequest = _body_request
      val describejobdefinition = new DescribeJobDefinitionOperationDefinition(
        bodyrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val submitjobdefinition = new SubmitJobDefinitionOperationDefinition(
        bodyrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val submitjobbatch = new SubmitJobBatchOperationDefinition(
        bodyrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val comparejobprofile = new CompareJobProfileOperationDefinition(
        idrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val reconstructjobprofile = new ReconstructJobProfileOperationDefinition(
        idrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val definitionrequest = _job_definition_request
      val definitionkeyrequest = _job_definition_key_request
      val createjobdefinition = new CreateJobDefinitionOperationDefinition(
        definitionrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val updatejobdefinition = new UpdateJobDefinitionOperationDefinition(
        definitionrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val activatejobdefinition = new ActivateJobDefinitionOperationDefinition(
        definitionkeyrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val retirejobdefinition = new RetireJobDefinitionOperationDefinition(
        definitionkeyrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val getjobdefinition = new GetJobDefinitionOperationDefinition(
        definitionkeyrequest,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val searchjobdefinitions = new SearchJobDefinitionsOperationDefinition(
        request,
        spec.ResponseDefinition(result = List(DataType.Named("Record")))
      )
      val canceljob = new ControlJobOperationDefinition(
        name = "cancel_job",
        command = JobControlCommand.Cancel,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val suspendjob = new ControlJobOperationDefinition(
        name = "suspend_job",
        command = JobControlCommand.Suspend,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val resumejob = new ControlJobOperationDefinition(
        name = "resume_job",
        command = JobControlCommand.Resume,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val retryjob = new ControlJobOperationDefinition(
        name = "retry_job",
        command = JobControlCommand.Retry,
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("JobControlResponse")))
      )
      val loadjobevents = new LoadJobEventsOperationDefinition(
        request = idrequest,
        response = spec.ResponseDefinition(result = List(DataType.Named("RecordList")))
      )
      val jobservice = spec.ServiceDefinition(
        name = "job",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(
            getjobstatus,
            loadjobhistory,
            getjobcalltree,
            gettaskexecutiontree,
            gettaskdetail,
            getjobresult,
            awaitjobresult,
            listmanagementjobs,
            getmanagementjobdetail,
            getmanagementjobresult,
            listmanagementjobtasks,
            listmanagementjobtimeline,
            getmanagementjobtaskexecutiontree,
            getmanagementjobtaskdetail,
            describejobdefinition,
            submitjobdefinition,
            submitjobbatch,
            comparejobprofile,
            reconstructjobprofile,
            createjobdefinition,
            updatejobdefinition,
            activatejobdefinition,
            retirejobdefinition,
            getjobdefinition,
            searchjobdefinitions
          )
        )
      )
      val jobadminservice = spec.ServiceDefinition(
        name = "job_admin",
        operations = spec.OperationDefinitionGroup(
          operations = NonEmptyVector.of(canceljob, suspendjob, resumejob, retryjob, loadjobevents)
        )
      )
      val jobexperienceservice = JobExperienceProtocol.serviceDefinition
      val protocol = Protocol(
        services = spec.ServiceDefinitionGroup(
          services = Vector(jobservice, jobadminservice, jobexperienceservice)
        ),
        handler = ProtocolHandler.default
      )
      comp.withPort(
        Component.Port.of(
          new DefaultJobService(comp),
          new DefaultJobAdminService(comp),
          new JobExperienceService(params.subsystem.jobEngine)
        )
      )
      val instanceid = ComponentInstanceId.default(componentId)
      Component.Core.create(
        componentId.name,
        componentId,
        instanceid,
        protocol
      )
    }

    private def _job_id_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(
          content = BaseContent.simple("id"),
          kind = spec.ParameterDefinition.Kind.Argument
        ))
      )

    private def _body_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple("body"),
            kind = spec.ParameterDefinition.Kind.Argument
          ),
          spec.ParameterDefinition(
            content = BaseContent.simple("jclFormat"),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      )

    private def _job_task_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple("id"),
            kind = spec.ParameterDefinition.Kind.Argument
          ),
          spec.ParameterDefinition(
            content = BaseContent.simple("taskId"),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      )

    private def _job_definition_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(
          spec.ParameterDefinition(
            content = BaseContent.simple("key"),
            kind = spec.ParameterDefinition.Kind.Argument
          ),
          spec.ParameterDefinition(
            content = BaseContent.simple("body"),
            kind = spec.ParameterDefinition.Kind.Argument
          ),
          spec.ParameterDefinition(
            content = BaseContent.simple("jclFormat"),
            kind = spec.ParameterDefinition.Kind.Argument
          ),
          spec.ParameterDefinition(
            content = BaseContent.simple("status"),
            kind = spec.ParameterDefinition.Kind.Argument
          )
        )
      )

    private def _job_definition_key_request: spec.RequestDefinition =
      spec.RequestDefinition(
        parameters = List(spec.ParameterDefinition(
          content = BaseContent.simple("key"),
          kind = spec.ParameterDefinition.Kind.Argument
        ))
      )
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
      for {
        body <- _body(req)
        format <- _jcl_format(req)
      } yield DescribeJobDefinitionAction(req, body, format)
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
      for {
        body <- _body(req)
        format <- _jcl_format(req)
      } yield SubmitJobDefinitionAction(req, body, format)
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
      for {
        body <- _body(req)
        format <- _jcl_format(req)
      } yield SubmitJobBatchAction(req, body, format)
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
        format <- _jcl_format(req)
      } yield CreateJobDefinitionAction(req, key, body, format, _status(req))
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
        format <- _jcl_format(req)
      } yield UpdateJobDefinitionAction(req, key, body, format, _status(req))
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
  ) extends spec.OperationDefinition with OpenApiOperationProjection {
    final val openApiHttpMethod: OpenApiHttpMethod = OpenApiHttpMethod.POST
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
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobStatusCall(core, jobid)
  }

  private final case class LoadJobHistoryAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobHistoryCall(core, jobid)
  }

  private final case class GetJobCalltreeAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobCalltreeCall(core, jobid)
  }

  private final case class GetTaskExecutionTreeAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetTaskExecutionTreeCall(core, jobid)
  }

  private final case class GetTaskDetailAction(
    request: Request,
    jobid: JobId,
    taskid: TaskId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetTaskDetailCall(core, jobid, taskid)
  }

  private final case class GetJobResultAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      GetJobResultCall(core, jobid)
  }

  private final case class AwaitJobResultAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      AwaitJobResultCall(core, jobid)
  }

  private final case class DescribeJobDefinitionAction(
    request: Request,
    body: String,
    format: RecordFormat
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      DescribeJobDefinitionCall(core, body, format)
  }

  private final case class SubmitJobDefinitionAction(
    request: Request,
    body: String,
    format: RecordFormat
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      SubmitJobDefinitionCall(core, body, format)
  }

  private final case class SubmitJobBatchAction(
    request: Request,
    body: String,
    format: RecordFormat
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      SubmitJobBatchCall(core, body, format)
  }

  private final case class CompareJobProfileAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      CompareJobProfileCall(core, jobid)
  }

  private final case class ReconstructJobProfileAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ReconstructJobProfileCall(core, jobid)
  }

  private final case class CreateJobDefinitionAction(
    request: Request,
    key: String,
    body: String,
    format: RecordFormat,
    status: Option[String]
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      CreateJobDefinitionCall(core, key, body, format, status)
  }

  private final case class UpdateJobDefinitionAction(
    request: Request,
    key: String,
    body: String,
    format: RecordFormat,
    status: Option[String]
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      UpdateJobDefinitionCall(core, key, body, format, status)
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
    jobid: JobId,
    command: JobControlCommand
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      ControlJobCall(core, jobid, command)
  }

  private final case class LoadJobEventsAction(
    request: Request,
    jobid: JobId
  ) extends SyncJobAction {
    def createCall(core: ActionCall.Core): ActionCall =
      LoadJobEventsCall(core, jobid)
  }

  private abstract class SyncJobAction extends CommandAction {
    override def commandExecutionMode: org.goldenport.cncf.action.CommandExecutionMode =
      org.goldenport.cncf.action.CommandExecutionMode.Sync
  }

  private final case class GetJobStatusCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(
            _.getJobStatus(jobid)(using core.executionContext)
          ) match {
            case Some(result) =>
              result.map(model => OperationResponse.RecordResponse(_job_record(model)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class LoadJobHistoryCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(
            _.loadJobHistory(jobid)(using core.executionContext)
          ) match {
            case Some(result) =>
              result.map(page => OperationResponse.RecordResponse(_timeline_record(jobid, page)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetJobCalltreeCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(
            _.getJobCalltree(jobid)(using core.executionContext)
          ) match {
            case Some(result) => result.map(OperationResponse.RecordResponse.apply)
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetTaskExecutionTreeCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.getTaskExecutionTree(jobid)(using
          core.executionContext)) match {
            case Some(result) =>
              result.map(tree => OperationResponse.RecordResponse(_task_tree_record(tree)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetTaskDetailCall(
    core: ActionCall.Core,
    jobid: JobId,
    taskid: TaskId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.getTaskDetail(jobid, taskid)(using
          core.executionContext)) match {
            case Some(result) =>
              result.map(detail => OperationResponse.RecordResponse(_task_detail_record(detail)))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class GetJobResultCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_result_response(core, jobid, _.getJobResult(jobid)(using core.executionContext))
  }

  private final case class AwaitJobResultCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(
            _.awaitJobResult(jobid)(using core.executionContext)
          ) match {
            case Some(result) => result
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class DescribeJobDefinitionCall(
    core: ActionCall.Core,
    body: String,
    format: RecordFormat
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobService].map(_.describeJobDefinition(body, format)) match {
            case Some(result) =>
              result.map(model => OperationResponse.RecordResponse(model.toRecord))
            case None => Consequence.serviceUnavailable("job service is not available")
          }
        case None =>
          Consequence.serviceUnavailable("component is not initialized")
      }
  }

  private final case class SubmitJobDefinitionCall(
    core: ActionCall.Core,
    body: String,
    format: RecordFormat
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _jcl_submission_response(
        core,
        _.submitJobDefinition(body, format)(using core.executionContext)
      )
  }

  private final case class SubmitJobBatchCall(
    core: ActionCall.Core,
    body: String,
    format: RecordFormat
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _jcl_submission_response(core, _.submitJobBatch(body, format)(using core.executionContext))
  }

  private final case class CompareJobProfileCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.compareJobProfile(jobid)(using core.executionContext))
  }

  private final case class ReconstructJobProfileCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(core, _.reconstructJobProfile(jobid)(using core.executionContext))
  }

  private final case class CreateJobDefinitionCall(
    core: ActionCall.Core,
    key: String,
    body: String,
    format: RecordFormat,
    status: Option[String]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(
        core,
        _.createJobDefinition(key, body, format, status)(using core.executionContext)
      )
  }

  private final case class UpdateJobDefinitionCall(
    core: ActionCall.Core,
    key: String,
    body: String,
    format: RecordFormat,
    status: Option[String]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _job_profile_response(
        core,
        _.updateJobDefinition(key, body, format, status)(using core.executionContext)
      )
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

  private final case class ControlJobCall(
    core: ActionCall.Core,
    jobid: JobId,
    command: JobControlCommand
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          given org.goldenport.cncf.context.ExecutionContext = core.executionContext
          component.port.get[JobAdminService] match {
            case Some(jobAdmin) =>
              val response = command match {
                case JobControlCommand.Cancel => jobAdmin.cancelJob(jobid)
                case JobControlCommand.Suspend => jobAdmin.suspendJob(jobid)
                case JobControlCommand.Resume => jobAdmin.resumeJob(jobid)
                case JobControlCommand.Retry => jobAdmin.retryJob(jobid)
              }
              response.map { response =>
                OperationResponse.RecordResponse(
                  Record.data(
                    "job-id" -> response.jobId.value,
                    "status" -> response.status.toString,
                    "async" -> response.async,
                    "response" -> response.response.map(_.print).getOrElse(""),
                    "changed" -> response.changed
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
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      core.component match {
        case Some(component) =>
          component.port.get[JobAdminService].map(_.loadJobEvents(jobid)) match {
            case Some(result) =>
              result.map { records =>
                OperationResponse.RecordResponse(
                  Record.data(
                    "job-id" -> jobid.value,
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

  private[jobcontrol] def timelineRecord(jobid: JobId, page: JobTimelinePage): Record =
    _timeline_record(jobid, page)

  private[jobcontrol] def taskTreeRecord(tree: JobTraceTree): Record =
    _task_tree_record(tree)

  private[jobcontrol] def taskDetailRecord(detail: JobTaskDetail): Record =
    _task_detail_record(detail)

  private[jobcontrol] def taskRecord(
    task: org.goldenport.cncf.job.JobTaskReadModel
  ): Record =
    _task_record(task)

}
