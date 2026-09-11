package org.goldenport.cncf.component.builtin.jobcontrol

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.job.{
  JobDataOrigin,
  JobId,
  JobManagementDetail,
  JobManagementPage,
  JobManagementQuery,
  JobManagementResult,
  JobManagementRetrySummary,
  JobManagementSummary,
  JobResult,
  JobResultSummary,
  JobStatus,
  JobTaskDetail,
  JobTaskPage,
  JobTaskReadModel,
  JobTimelinePage,
  JobTraceTree,
  TaskId
}
import org.goldenport.cncf.openapi.{OpenApiHttpMethod, OpenApiOperationProjection}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.value.BaseContent

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[jobcontrol] object JobManagementProtocol {
  def listRequest: spec.RequestDefinition =
    spec.RequestDefinition(
      parameters = List("persistentOnly", "status", "origin", "limit", "cursor").map { name =>
        spec.ParameterDefinition(
          content = BaseContent.simple(name),
          kind = spec.ParameterDefinition.Kind.Argument
        )
      }
    )

  def pageRequest: spec.RequestDefinition =
    spec.RequestDefinition(
      parameters = List("id", "offset", "limit").map { name =>
        spec.ParameterDefinition(
          content = BaseContent.simple(name),
          kind = spec.ParameterDefinition.Kind.Argument
        )
      }
    )

  def listManagementJobsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new ListManagementJobsOperationDefinition(request, response)

  def getManagementJobDetailOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new GetManagementJobDetailOperationDefinition(request, response)

  def getManagementJobResultOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new GetManagementJobResultOperationDefinition(request, response)

  def listManagementJobTasksOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new ListManagementJobTasksOperationDefinition(request, response)

  def listManagementJobTimelineOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new ListManagementJobTimelineOperationDefinition(request, response)

  def getManagementJobTaskExecutionTreeOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new GetManagementJobTaskExecutionTreeOperationDefinition(request, response)

  def getManagementJobTaskDetailOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ): spec.OperationDefinition =
    new GetManagementJobTaskDetailOperationDefinition(request, response)

  def pageRecord(page: JobManagementPage): Record =
    Record.data(
      "entries" -> page.entries.map(_summary_record),
      "total-count" -> page.totalCount,
      "next-cursor" -> page.nextCursor.map(_.value).getOrElse("")
    )

  def detailRecord(detail: JobManagementDetail): Record =
    Record.data(
      "summary" -> _summary_record(detail.summary),
      "retry" -> _retry_record(detail.retry),
      "result-summary" -> _result_summary_record(detail.resultSummary),
      "task-count" -> detail.taskCount,
      "timeline-count" -> detail.timelineCount
    )

  def resultRecord(result: JobManagementResult): Record =
    result match {
      case JobManagementResult.Available(value) =>
        Record.data(
          "availability" -> "available",
          "result-summary" -> _result_summary_record(_result_summary(value)),
          "result-value" -> _result_value(value)
        )
      case JobManagementResult.Pending(summary) =>
        Record.data(
          "availability" -> "pending",
          "result-summary" -> _result_summary_record(summary)
        )
      case JobManagementResult.UnavailableAfterRestart(summary) =>
        Record.data(
          "availability" -> "unavailable-after-restart",
          "result-summary" -> _result_summary_record(summary)
        )
    }

  def taskPageRecord(
    jobid: JobId,
    page: JobTaskPage,
    taskrecord: JobTaskReadModel => Record
  ): Record =
    Record.data(
      "job-id" -> jobid.value,
      "offset" -> page.offset,
      "limit" -> page.limit,
      "total-count" -> page.totalCount,
      "fetched-count" -> page.fetchedCount,
      "tasks" -> page.tasks.map(taskrecord)
    )

  def timelinePageRecord(
    jobid: JobId,
    page: JobTimelinePage,
    timelinerecord: (JobId, JobTimelinePage) => Record
  ): Record =
    timelinerecord(jobid, page)

  def taskTreeRecord(
    tree: JobTraceTree,
    treerecord: JobTraceTree => Record
  ): Record =
    treerecord(tree)

  def taskDetailRecord(
    detail: JobTaskDetail,
    detailrecord: JobTaskDetail => Record
  ): Record =
    detailrecord(detail)

  private abstract class ManagementOperationDefinition(
    name: String,
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends spec.OperationDefinition with OpenApiOperationProjection {
    final val openApiHttpMethod: OpenApiHttpMethod = OpenApiHttpMethod.GET
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(name = name, request = request, response = response)
  }

  private final class ListManagementJobsOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("list_management_jobs", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _query(req).map(ListManagementJobsAction(req, _))
  }

  private final class GetManagementJobDetailOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("get_management_job_detail", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map(GetManagementJobDetailAction(req, _))
  }

  private final class GetManagementJobResultOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("get_management_job_result", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map(GetManagementJobResultAction(req, _))
  }

  private final class ListManagementJobTasksOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("list_management_job_tasks", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _page_request_values(req).map { case (jobid, offset, limit) =>
        ListManagementJobTasksAction(req, jobid, offset, limit)
      }
  }

  private final class ListManagementJobTimelineOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("list_management_job_timeline", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _page_request_values(req).map { case (jobid, offset, limit) =>
        ListManagementJobTimelineAction(req, jobid, offset, limit)
      }
  }

  private final class GetManagementJobTaskExecutionTreeOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("get_management_job_task_execution_tree", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      _job_id(req).map(GetManagementJobTaskExecutionTreeAction(req, _))
  }

  private final class GetManagementJobTaskDetailOperationDefinition(
    request: spec.RequestDefinition,
    response: spec.ResponseDefinition
  ) extends ManagementOperationDefinition("get_management_job_task_detail", request, response) {
    def createOperationRequest(req: Request): Consequence[OperationRequest] =
      for {
        jobid <- _job_id(req)
        taskid <- _task_id(req)
      } yield GetManagementJobTaskDetailAction(req, jobid, taskid)
  }

  private final case class ListManagementJobsAction(
    request: Request,
    query: JobManagementQuery
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ListManagementJobsCall(core, query)
  }

  private final case class GetManagementJobDetailAction(
    request: Request,
    jobid: JobId
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      GetManagementJobDetailCall(core, jobid)
  }

  private final case class GetManagementJobResultAction(
    request: Request,
    jobid: JobId
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      GetManagementJobResultCall(core, jobid)
  }

  private final case class ListManagementJobTasksAction(
    request: Request,
    jobid: JobId,
    offset: Int,
    limit: Int
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ListManagementJobTasksCall(core, jobid, offset, limit)
  }

  private final case class ListManagementJobTimelineAction(
    request: Request,
    jobid: JobId,
    offset: Int,
    limit: Int
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      ListManagementJobTimelineCall(core, jobid, offset, limit)
  }

  private final case class GetManagementJobTaskExecutionTreeAction(
    request: Request,
    jobid: JobId
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      GetManagementJobTaskExecutionTreeCall(core, jobid)
  }

  private final case class GetManagementJobTaskDetailAction(
    request: Request,
    jobid: JobId,
    taskid: TaskId
  ) extends QueryAction() {
    def createCall(core: ActionCall.Core): ActionCall =
      GetManagementJobTaskDetailCall(core, jobid, taskid)
  }

  private final case class ListManagementJobsCall(
    core: ActionCall.Core,
    query: JobManagementQuery
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.listManagementJobs(query).map(pageRecord))
    }
  }

  private final case class GetManagementJobDetailCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.getManagementJobDetail(jobid).map(detailRecord))
    }
  }

  private final case class GetManagementJobResultCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.getManagementJobResult(jobid).map(resultRecord))
    }
  }

  private final case class ListManagementJobTasksCall(
    core: ActionCall.Core,
    jobid: JobId,
    offset: Int,
    limit: Int
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.listManagementJobTasks(jobid, offset, limit).map(page =>
        taskPageRecord(jobid, page, JobControlComponent.taskRecord)
      ))
    }
  }

  private final case class ListManagementJobTimelineCall(
    core: ActionCall.Core,
    jobid: JobId,
    offset: Int,
    limit: Int
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.listManagementJobTimeline(jobid, offset, limit).map(page =>
        timelinePageRecord(jobid, page, JobControlComponent.timelineRecord)
      ))
    }
  }

  private final case class GetManagementJobTaskExecutionTreeCall(
    core: ActionCall.Core,
    jobid: JobId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.getManagementJobTaskExecutionTree(jobid).map(tree =>
        taskTreeRecord(tree, JobControlComponent.taskTreeRecord)
      ))
    }
  }

  private final case class GetManagementJobTaskDetailCall(
    core: ActionCall.Core,
    jobid: JobId,
    taskid: TaskId
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      given org.goldenport.cncf.context.ExecutionContext = core.executionContext
      _service_response(core)(_.getManagementJobTaskDetail(jobid, taskid).map(detail =>
        taskDetailRecord(detail, JobControlComponent.taskDetailRecord)
      ))
    }
  }

  private def _query(req: Request): Consequence[JobManagementQuery] =
    for {
      persistentonly <- _boolean(req, "persistentOnly", default = true)
      status <- _status(req)
      origin <- _origin(req)
      limit <- _int(req, "limit", JobManagementQuery.DefaultLimit)
    } yield JobManagementQuery(
      persistentOnly = persistentonly,
      status = status,
      origin = origin,
      limit = limit,
      cursor = _string_argument(req, "cursor").map(org.goldenport.cncf.job.JobManagementCursor.fromOpaque)
    )

  private def _page_request_values(req: Request): Consequence[(JobId, Int, Int)] =
    for {
      jobid <- _job_id(req)
      offset <- _int(req, "offset", 0)
      limit <- _int(req, "limit", JobManagementQuery.DefaultLimit)
    } yield (jobid, offset, limit)

  private def _job_id(req: Request): Consequence[JobId] =
    _string_argument(req, "id") match {
      case Some(value) => JobId.parse(value)
      case None => Consequence.argumentMissing("id")
    }

  private def _task_id(req: Request): Consequence[TaskId] =
    _string_argument(req, "taskId")
      .orElse(_string_argument(req, "task-id")) match {
        case Some(value) => TaskId.parse(value)
        case None => Consequence.argumentMissing("taskId")
      }

  private def _boolean(req: Request, name: String, default: Boolean): Consequence[Boolean] =
    _string_argument(req, name) match {
      case None => Consequence.success(default)
      case Some(value) => value.toLowerCase match {
        case "true" => Consequence.success(true)
        case "false" => Consequence.success(false)
        case _ => Consequence.argumentInvalid(s"$name must be true or false")
      }
    }

  private def _int(req: Request, name: String, default: Int): Consequence[Int] =
    _string_argument(req, name) match {
      case None => Consequence.success(default)
      case Some(value) => scala.util.Try(value.toInt).toOption match {
        case Some(number) => Consequence.success(number)
        case None => Consequence.argumentInvalid(s"$name must be an integer")
      }
    }

  private def _status(req: Request): Consequence[Option[JobStatus]] =
    _string_argument(req, "status") match {
      case None => Consequence.success(None)
      case Some(value) =>
        val status = value.toLowerCase match {
          case "submitted" => Some(JobStatus.Submitted)
          case "running" => Some(JobStatus.Running)
          case "suspended" => Some(JobStatus.Suspended)
          case "cancelled" => Some(JobStatus.Cancelled)
          case "succeeded" => Some(JobStatus.Succeeded)
          case "failed" => Some(JobStatus.Failed)
          case _ => None
        }
        status.map(x => Consequence.success(Some(x))).getOrElse(
          Consequence.argumentInvalid(s"invalid management status: $value")
        )
    }

  private def _origin(req: Request): Consequence[Option[JobDataOrigin]] =
    _string_argument(req, "origin") match {
      case None => Consequence.success(None)
      case Some(value) => JobDataOrigin.values.find(_.toString.equalsIgnoreCase(value)) match {
        case Some(origin) => Consequence.success(Some(origin))
        case None => Consequence.argumentInvalid(s"invalid management origin: $value")
      }
    }

  private def _string_argument(req: Request, name: String): Option[String] =
    req.arguments.find(_.name == name).map(_.value.toString).filter(_.trim.nonEmpty)
      .orElse(req.properties.find(_.name == name).map(_.value.toString).filter(_.trim.nonEmpty))
      .map(_.trim)

  private def _service_response(
    core: ActionCall.Core
  )(
    f: JobControlComponent.JobService => Consequence[Record]
  ): Consequence[OperationResponse] =
    core.component match {
      case Some(component) =>
        given org.goldenport.cncf.context.ExecutionContext = core.executionContext
        component.port.get[JobControlComponent.JobService].map(f) match {
          case Some(result) => result.map(OperationResponse.RecordResponse.apply)
          case None => Consequence.serviceUnavailable("job service is not available")
        }
      case None => Consequence.serviceUnavailable("component is not initialized")
    }

  private def _summary_record(summary: JobManagementSummary): Record =
    Record.data(
      "job-id" -> summary.jobId.value,
      "status" -> summary.status.toString,
      "persistence" -> summary.persistence.toString,
      "origin" -> summary.origin.toString,
      "created-at" -> summary.createdAt.toString,
      "updated-at" -> summary.updatedAt.toString,
      "scheduled-start-at" -> summary.scheduledStartAt.map(_.toString).getOrElse("")
    )

  private def _result_summary_record(summary: JobResultSummary): Record =
    Record.data(
      "status" -> summary.status.toString,
      "success" -> summary.success,
      "message" -> summary.message.getOrElse("")
    )

  private def _retry_record(retry: JobManagementRetrySummary): Record =
    Record.data(
      "kind" -> retry.kind.print,
      "attempt-count" -> retry.attemptCount,
      "max-attempts" -> retry.maxAttempts,
      "next-retry-due-at" -> retry.nextRetryDueAt.map(_.toString).getOrElse(""),
      "exhausted" -> retry.exhausted,
      "recovery-required" -> retry.recoveryRequired,
      "dead-letter" -> retry.deadLetter,
      "poison" -> retry.poison
    )

  private def _result_summary(result: JobResult): JobResultSummary =
    result match {
      case JobResult.Success(_) => JobResultSummary(JobStatus.Succeeded, true, None)
      case JobResult.Failure(conclusion) =>
        JobResultSummary(JobStatus.Failed, false, Some(conclusion.show))
    }

  private def _result_value(result: JobResult): String =
    result match {
      case JobResult.Success(response) => response.print
      case JobResult.Failure(conclusion) => conclusion.show
    }
}
