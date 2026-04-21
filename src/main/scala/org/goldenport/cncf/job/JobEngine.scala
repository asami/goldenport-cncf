package org.goldenport.cncf.job

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext as ScalaExecutionContext, Future}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.consequence.Failures
import org.goldenport.id.UniversalId
import org.goldenport.observation.Descriptor
import org.goldenport.provisional.observation.Taxonomy
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentLogic}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventId, EventLane, EventRecord, EventStore}

/*
 * @since   Jan.  4, 2026
 *  version Mar. 30, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "job", timestamp, entropy)

object JobId {
  def generate(): JobId =
    JobId("cncf", "job")

  def parse(s: String): Consequence[JobId] =
    UniversalId.parseParts(s, "job").map(parts => JobId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy)))
}

final case class TaskId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "task", timestamp, entropy)

object TaskId {
  def generate(): TaskId =
    TaskId("cncf", "task")

  def parse(s: String): Consequence[TaskId] =
    UniversalId.parseParts(s, "task").map(parts => TaskId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy)))
}

final case class ActionId(
  major: String,
  minor: String,
  timestamp: Option[Instant] = None,
  entropy: Option[String] = None
) extends UniversalId(major, minor, "action", timestamp, entropy)

object ActionId {
  def generate(): ActionId =
    ActionId("cncf", "action")

  def parse(s: String): Consequence[ActionId] =
    UniversalId.parseParts(s, "action").map(parts => ActionId(parts.major, parts.minor, Some(parts.timestamp), Some(parts.entropy)))
}

final case class JobContext(
  jobId: Option[JobId],
  taskId: Option[TaskId],
  actionId: Option[ActionId],
  parentJobId: Option[JobId] = None,
  currentTask: Option[TaskId] = None,
  taskStack: Vector[TaskId] = Vector.empty,
  causationId: Option[String] = None,
  traceMetadata: Map[String, String] = Map.empty
)

object JobContext {
  val empty: JobContext = JobContext(None, None, None)
}

sealed trait JobStatus
object JobStatus {
  case object Submitted extends JobStatus
  case object Running extends JobStatus
  case object Suspended extends JobStatus
  case object Cancelled extends JobStatus
  case object Succeeded extends JobStatus
  case object Failed extends JobStatus
}

enum JobControlCommand {
  case Cancel
  case Retry
  case Suspend
  case Resume
}

enum JobCommandMode {
  case Async
  case Sync
}

final case class JobControlOption(
  mode: JobCommandMode = JobCommandMode.Async,
  timeoutMillis: Long = 3000L,
  pollMillis: Long = 10L
)

final case class JobControlRequest(
  command: JobControlCommand,
  option: JobControlOption = JobControlOption()
)

final case class JobControlResponse(
  jobId: JobId,
  status: JobStatus,
  response: Option[OperationResponse],
  async: Boolean
)

final case class JobMetrics(
  running: Int,
  queued: Int,
  completed: Int,
  failed: Int
)

trait JobControlPolicy {
  def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit]
}

object JobControlPolicy {
  val default: JobControlPolicy = new DefaultJobControlPolicy

  private final class DefaultJobControlPolicy extends JobControlPolicy {
    private val _control_caps = Set("job_control", "job_admin", "content_manager", "content_admin")

    def authorize(jobId: JobId, request: JobControlRequest)(using ctx: ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      if (ctx.security.hasAnyCapability(_control_caps))
        Consequence.unit
      else
        Consequence.operationIllegal(
          "job.control",
          s"required capability: ${_control_caps.toVector.sorted.mkString("|")}"
        )
    }
  }
}

sealed trait JobResult
object JobResult {
  final case class Success(response: OperationResponse) extends JobResult
  final case class Failure(conclusion: Conclusion) extends JobResult
}

sealed trait TaskOutcome {
  def result: Consequence[OperationResponse]
}

final case class TaskSucceeded(
  response: OperationResponse
) extends TaskOutcome {
  def result: Consequence[OperationResponse] =
    Consequence.success(response)
}

final case class TaskFailed(
  conclusion: Conclusion
) extends TaskOutcome {
  def result: Consequence[OperationResponse] =
    Consequence.Failure(conclusion)
}

trait JobTask {
  def actionId: ActionId
  def componentName: Option[String] = None
  def serviceName: Option[String] = None
  def operationName: Option[String] = None
  def run(ctx: ExecutionContext): TaskOutcome
}

final case class ActionTask(
  actionId: ActionId,
  action: Action,
  actionEngine: ActionEngine,
  component: Option[Component]
) extends JobTask {
  override def componentName: Option[String] =
    component.map(_.name)

  override def serviceName: Option[String] =
    None

  override def operationName: Option[String] =
    Some(action.name)

  def run(ctx: ExecutionContext): TaskOutcome = {
    val call = component.map(ComponentLogic(_).createActionCall(action, ctx)).getOrElse {
      val correlationid = ctx.observability.correlationId
      val core = ActionCall.Core(action, ctx, component, correlationid)
      action.createCall(core)
    }
    actionEngine.execute(call) match {
      case Consequence.Success(res) =>
        TaskSucceeded(res)
      case Consequence.Failure(c) =>
        TaskFailed(c)
    }
  }
}

enum JobPersistencePolicy {
  case Persistent
  case Ephemeral
}

final case class JobSubmitOption(
  persistence: JobPersistencePolicy = JobPersistencePolicy.Persistent,
  runMode: JobRunMode = JobRunMode.Async,
  requestSummary: Option[String] = None,
  parameters: Map[String, String] = Map.empty,
  executionNotes: Vector[String] = Vector.empty
)

enum JobRunMode {
  case Async
  case Sync
}

enum JobDataOrigin {
  case Durable
  case Runtime
}

enum AsyncFailureDisposition {
  case NotApplicable
  case Retryable
  case Terminal

  def print: String = this match {
    case AsyncFailureDisposition.NotApplicable => "not-applicable"
    case AsyncFailureDisposition.Retryable => "retryable"
    case AsyncFailureDisposition.Terminal => "terminal"
  }
}

enum JobTaskStatus {
  case Running
  case Succeeded
  case Failed
}

final case class JobResultSummary(
  status: JobStatus,
  success: Boolean,
  message: Option[String]
)

final case class JobTaskResultSummary(
  success: Boolean,
  message: Option[String]
)

final case class JobTaskReadModel(
  taskId: TaskId,
  parentTaskId: Option[TaskId],
  status: JobTaskStatus,
  startedAt: Instant,
  finishedAt: Option[Instant],
  result: JobTaskResultSummary,
  component: Option[String] = None,
  service: Option[String] = None,
  operation: Option[String] = None
)

final case class JobTimelineEvent(
  sequence: Long,
  occurredAt: Instant,
  kind: String,
  taskId: Option[TaskId],
  parentTaskId: Option[TaskId],
  note: Option[String]
)

final case class JobDebugInfo(
  requestSummary: Option[String],
  parameters: Map[String, String],
  executionNotes: Vector[String]
)

final case class JobEventLineage(
  eventName: Option[String],
  eventKind: Option[String],
  parentJobId: Option[String],
  correlationId: Option[String],
  causationId: Option[String],
  sourceSubsystem: Option[String],
  sourceComponent: Option[String],
  targetSubsystem: Option[String],
  targetComponent: Option[String],
  receptionRule: Option[String],
  receptionPolicy: Option[String],
  policySource: Option[String],
  jobRelation: Option[String],
  taskRelation: Option[String],
  transactionRelation: Option[String],
  sagaRelation: Option[String],
  failurePolicy: Option[String],
  failureDisposition: AsyncFailureDisposition
) {
  def eventTriggered: Boolean =
    eventName.nonEmpty || receptionRule.nonEmpty || receptionPolicy.nonEmpty
}

final case class JobTimelinePage(
  offset: Int,
  limit: Int,
  totalCount: Int,
  fetchedCount: Int,
  events: Vector[JobTimelineEvent]
)

final case class JobTaskPage(
  offset: Int,
  limit: Int,
  totalCount: Int,
  fetchedCount: Int,
  tasks: Vector[JobTaskReadModel]
)

final case class JobTraceTaskNode(
  taskId: TaskId,
  parentTaskId: Option[TaskId],
  events: Vector[JobTimelineEvent],
  children: Vector[JobTraceTaskNode]
)

final case class JobTraceTree(
  jobId: JobId,
  roots: Vector[JobTraceTaskNode]
)

final case class JobQueryReadModel(
  jobId: JobId,
  status: JobStatus,
  persistence: JobPersistencePolicy,
  origin: JobDataOrigin,
  submitter: JobSubmitter,
  createdAt: Instant,
  updatedAt: Instant,
  tasks: JobTaskPage,
  timeline: JobTimelinePage,
  traceTree: JobTraceTree,
  debug: JobDebugInfo,
  lineage: JobEventLineage,
  resultSummary: JobResultSummary,
  result: Option[OperationResponse]
)

final case class JobSubmitter(
  principalId: String,
  subjectKind: String,
  sessionId: Option[String] = None
)

object JobSubmitter {
  def from(ctx: ExecutionContext): JobSubmitter =
    JobSubmitter(
      ctx.security.principal.id.value,
      ctx.security.subjectKind.toString,
      ctx.security.session.flatMap(_.sessionId)
    )
}

trait JobQueryPolicy {
  def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit]
}

object JobQueryPolicy {
  val default: JobQueryPolicy = new DefaultJobQueryPolicy

  private final class DefaultJobQueryPolicy extends JobQueryPolicy {
    private val _read_caps = Set("job_view", "job_admin", "content_manager", "content_admin")

    def authorizeRead(model: JobQueryReadModel)(using ctx: ExecutionContext): Consequence[Unit] =
      if (ctx.security.hasAnyCapability(_read_caps))
        Consequence.unit
      else if (_same_submitter(model.submitter, ctx))
        Consequence.unit
      else
        Consequence.operationIllegal(
          "job.query",
          s"job is not owned by the current subject; required capability: ${_read_caps.toVector.sorted.mkString("|")}"
        )

    private def _same_submitter(
      submitter: JobSubmitter,
      ctx: ExecutionContext
    ): Boolean =
      submitter.sessionId match {
        case Some(sessionId) =>
          ctx.security.session.flatMap(_.sessionId).contains(sessionId)
        case None =>
          submitter.principalId == ctx.security.principal.id.value &&
            submitter.subjectKind == ctx.security.subjectKind.toString
      }
  }
}

trait JobEngine {
  def submit(tasks: List[JobTask], ctx: ExecutionContext): JobId
  def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): JobId
  def getStatus(jobId: JobId): Option[JobStatus]
  def getResult(jobId: JobId): Option[JobResult]
  def control(
    jobId: JobId,
    request: JobControlRequest,
    policy: JobControlPolicy = JobControlPolicy.default
  )(using ExecutionContext): Consequence[JobControlResponse]

  def getResponse(jobId: JobId): Option[OperationResponse] =
    getResult(jobId) match {
      case Some(JobResult.Success(response)) => Some(response)
      case _ => None
    }

  def query(jobId: JobId): Option[JobQueryReadModel]
  def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage]
  def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage]
  def metrics: Option[JobMetrics] = None
  def annotateJob(jobId: JobId, parameters: Map[String, String], executionNotes: Vector[String] = Vector.empty): Unit = ()
  def runTaskInJobSync(jobId: JobId, task: JobTask, ctx: ExecutionContext): Consequence[TaskOutcome] =
    Consequence.operationInvalid("job.same-job.sync-task")
  def enqueueTaskInJob(jobId: JobId, task: JobTask, ctx: ExecutionContext): Consequence[TaskId] =
    Consequence.operationInvalid("job.same-job.async-task")

  def queryVisible(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobQueryReadModel]] =
    query(jobId) match {
      case Some(model) => policy.authorizeRead(model).map(_ => Some(model))
      case None => Consequence.success(None)
    }
}

final class InMemoryJobEngine(
  implicit val executionContext: ScalaExecutionContext
) extends JobEngine {
  private val _durable_jobs = new ConcurrentHashMap[JobId, JobRecord]()
  private val _runtime_jobs = new ConcurrentHashMap[JobId, JobRecord]()
  private var _event_store: Option[EventStore] = None

  def withEventStore(store: EventStore): InMemoryJobEngine = {
    _event_store = Some(store)
    this
  }

  def submit(tasks: List[JobTask], ctx: ExecutionContext): JobId =
    submit(tasks, ctx, _default_submit_option(tasks))

  def submit(
    tasks: List[JobTask],
    ctx: ExecutionContext,
    option: JobSubmitOption
  ): JobId = {
    val jobid = JobId.generate()
    val now = Instant.now()
    val initialdebug = JobDebugInfo(
      requestSummary = option.requestSummary.orElse(_request_summary(tasks)),
      parameters = if (option.parameters.nonEmpty) option.parameters else _request_parameters(tasks),
      executionNotes = option.executionNotes
    )
    val record = JobRecord(
      id = jobid,
      tasks = tasks,
      submittedContext = ctx,
      status = JobStatus.Submitted,
      result = None,
      persistence = option.persistence,
      createdAt = now,
      updatedAt = now,
      taskReadModels = Vector.empty,
      timeline = Vector(
        JobTimelineEvent(
          sequence = 1L,
          occurredAt = now,
          kind = "job.submitted",
          taskId = None,
          parentTaskId = None,
          note = None
        )
      ),
      debug = initialdebug
    )
    _put_record(record)
    _append_event_(
      name = "job.submitted",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Submitted.toString,
        "request-summary" -> initialdebug.requestSummary.getOrElse("")
      )
    )
    option.runMode match {
      case JobRunMode.Async =>
        _run_job_async_(jobid, tasks, ctx)
      case JobRunMode.Sync =>
        _run_job_sync_(jobid, tasks, ctx)
    }
    jobid
  }

  def getStatus(jobId: JobId): Option[JobStatus] =
    _get_record(jobId).map(_.status)

  def getResult(jobId: JobId): Option[JobResult] =
    _get_record(jobId).flatMap(_.result)

  def control(
    jobId: JobId,
    request: JobControlRequest,
    policy: JobControlPolicy = JobControlPolicy.default
  )(using ctx: ExecutionContext): Consequence[JobControlResponse] =
    policy.authorize(jobId, request).flatMap { _ =>
      _control(jobId, request)
    }

  def query(jobId: JobId): Option[JobQueryReadModel] =
    _get_record(jobId).map { record =>
      val tasks = _task_page(record, 0, math.max(record.taskReadModels.size, 1))
      val timeline = _timeline_page(record, 0, math.max(record.timeline.size, 1))
      JobQueryReadModel(
        jobId = record.id,
        status = record.status,
        persistence = record.persistence,
        origin = _origin(record),
        submitter = JobSubmitter.from(record.submittedContext),
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
        tasks = tasks,
        timeline = timeline,
        traceTree = _trace_tree(record),
        debug = record.debug,
        lineage = _event_lineage(record),
        resultSummary = _result_summary(record),
        result = record.result.collect { case JobResult.Success(res) => res }
      )
    }

  def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage] =
    _get_record(jobId).map(_task_page(_, offset, limit))

  def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage] =
    _get_record(jobId).map(_timeline_page(_, offset, limit))

  override def metrics: Option[JobMetrics] = {
    val records = _durable_jobs.values().toArray(new Array[JobRecord](0)).toVector ++
      _runtime_jobs.values().toArray(new Array[JobRecord](0)).toVector
    val running = records.count(_.status == JobStatus.Running)
    val queued = records.count(_.status == JobStatus.Submitted)
    val completed = records.count(_.status == JobStatus.Succeeded)
    val failed = records.count(_.status == JobStatus.Failed)
    Some(JobMetrics(running = running, queued = queued, completed = completed, failed = failed))
  }

  override def annotateJob(
    jobId: JobId,
    parameters: Map[String, String],
    executionNotes: Vector[String] = Vector.empty
  ): Unit =
    _mutate_record(jobId) { record =>
      record.copy(
        debug = record.debug.copy(
          parameters = record.debug.parameters ++ parameters,
          executionNotes = record.debug.executionNotes ++ executionNotes
        ),
        updatedAt = Instant.now()
      )
    }

  override def runTaskInJobSync(
    jobId: JobId,
    task: JobTask,
    ctx: ExecutionContext
  ): Consequence[TaskOutcome] =
    _get_record(jobId) match {
      case Some(_) =>
        val outcome = _run_same_job_task_(jobId, task, ctx)
        outcome.result.map(_ => outcome)
      case None =>
        Consequence.operationNotFound(s"job:${jobId.value}")
    }

  override def enqueueTaskInJob(
    jobId: JobId,
    task: JobTask,
    ctx: ExecutionContext
  ): Consequence[TaskId] =
    _get_record(jobId) match {
      case Some(_) =>
        val taskid = TaskId.generate()
        _append_timeline_(jobId, "job.same-job-async.enqueued", Some(taskid), ctx.jobContext.currentTask, Some(task.operationName.getOrElse(task.actionId.print)))
        Future(_run_same_job_task_(jobId, task, ctx, Some(taskid)))
        Consequence.success(taskid)
      case None =>
        Consequence.operationNotFound(s"job:${jobId.value}")
    }

  private def _run_job_async_(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext
  ): Unit = {
    Future(_run_job_body_(jobid, tasks, ctx))
    ()
  }

  private def _run_job_sync_(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext
  ): Unit =
    _run_job_body_(jobid, tasks, ctx)

  private def _run_job_body_(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext
  ): Unit = {
    _append_timeline_(jobid, "job.running", None, None, None)
    _update_record_(jobid, JobStatus.Running, None)
    _append_event_(
      name = "job.running",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Running.toString
      )
    )
    tasks match {
      case Nil =>
        _append_timeline_(jobid, "job.succeeded", None, None, Some("no task"))
        _update_record_(jobid, JobStatus.Succeeded, None)
        _append_event_(
          name = "job.succeeded",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> JobStatus.Succeeded.toString
          )
        )
      case _ =>
        var previous: Option[TaskId] = None
        var failure: Option[Conclusion] = None
        var successResponse: Option[OperationResponse] = None
        tasks.foreach { task =>
          if (failure.isEmpty && _can_run_next_task(jobid)) {
            if (_await_if_suspended(jobid)) {
              val taskid = TaskId.generate()
              val startedat = Instant.now()
              val jobcontext = JobContext(
                jobId = Some(jobid),
                taskId = Some(taskid),
                actionId = Some(task.actionId),
                parentJobId = ctx.jobContext.jobId,
                currentTask = Some(taskid),
                taskStack = previous.toVector :+ taskid,
                causationId = ctx.observability.correlationId.map(_.print),
                traceMetadata = Map(
                  "traceId" -> ctx.observability.traceId.print
                ) ++ ctx.observability.correlationId.map(x => "correlationId" -> x.print)
              )
              val executioncontext = ExecutionContext.withJobContext(ctx, jobcontext)
              _append_task_running_(jobid, taskid, previous, startedat, task)
              task.run(executioncontext) match {
                case TaskSucceeded(res) =>
                  successResponse = Some(res)
                  _append_task_finished_(
                    jobid,
                    taskid,
                    previous,
                    JobTaskStatus.Succeeded,
                    JobTaskResultSummary(success = true, message = Some("ok")),
                    Instant.now()
                  )
                  previous = Some(taskid)
                case TaskFailed(c) =>
                  failure = Some(c)
                  _append_task_finished_(
                    jobid,
                    taskid,
                    previous,
                    JobTaskStatus.Failed,
                    JobTaskResultSummary(success = false, message = c.observation.getEffectiveMessage),
                    Instant.now()
                  )
              }
            }
          }
        }
        val deferred = _get_record(jobid).map(_.status) match {
          case Some(JobStatus.Cancelled) =>
            Some(JobResult.Failure(Conclusion.simple("job cancelled")))
          case _ =>
            failure.map(JobResult.Failure.apply).orElse(successResponse.map(JobResult.Success.apply))
        }
        _mark_base_completion_(jobid, deferred)
    }
  }

  private def _run_same_job_task_(
    jobid: JobId,
    task: JobTask,
    ctx: ExecutionContext,
    forcedTaskId: Option[TaskId] = None
  ): TaskOutcome = {
    val taskid = forcedTaskId.getOrElse(TaskId.generate())
    val parent = ctx.jobContext.currentTask
    val startedat = Instant.now()
    val jobcontext = JobContext(
      jobId = Some(jobid),
      taskId = Some(taskid),
      actionId = Some(task.actionId),
      parentJobId = ctx.jobContext.jobId,
      currentTask = Some(taskid),
      taskStack = ctx.jobContext.taskStack :+ taskid,
      causationId = ctx.observability.correlationId.map(_.print),
      traceMetadata = Map("traceId" -> ctx.observability.traceId.print) ++
        ctx.observability.correlationId.map(x => "correlationId" -> x.print)
    )
    val executioncontext = ExecutionContext.withJobContext(ctx, jobcontext)
    _append_task_running_(jobid, taskid, parent, startedat, task)
    val outcome = task.run(executioncontext)
    outcome match {
      case TaskSucceeded(res) =>
        _append_task_finished_(
          jobid,
          taskid,
          parent,
          JobTaskStatus.Succeeded,
          JobTaskResultSummary(success = true, message = Some("ok")),
          Instant.now()
        )
      case TaskFailed(c) =>
        _append_task_finished_(
          jobid,
          taskid,
          parent,
          JobTaskStatus.Failed,
          JobTaskResultSummary(success = false, message = c.observation.getEffectiveMessage),
          Instant.now()
        )
        _update_deferred_result_(jobid, Some(JobResult.Failure(c)))
    }
    _settle_if_ready_(jobid)
    outcome
  }

  private def _control(
    jobid: JobId,
    request: JobControlRequest
  ): Consequence[JobControlResponse] =
    _get_record(jobid) match {
      case None =>
        _control_failure(s"job not found: ${jobid.value}")
      case Some(record) =>
        _transition_for(request.command, record.status).flatMap { target =>
          request.command match {
            case JobControlCommand.Retry =>
              _retry_job_(jobid, record)
            case _ =>
              _append_timeline_for_control_(jobid, request.command, target)
              _update_record_(jobid, target, _control_result_for(target))
              _append_event_for_control_(jobid, request.command, target)
          }
          request.option.mode match {
            case JobCommandMode.Async =>
              Consequence.success(
                JobControlResponse(
                  jobId = jobid,
                  status = target,
                  response = None,
                  async = true
                )
              )
            case JobCommandMode.Sync =>
              _await_control_sync(jobid, request, target)
          }
        }
    }

  private def _retry_job_(jobid: JobId, record: JobRecord): Unit = {
    val now = Instant.now()
    _put_record(
      record.copy(
        status = JobStatus.Submitted,
        result = None,
        updatedAt = now
      )
    )
    _append_timeline_(jobid, "job.retry.submitted", None, None, None)
    _append_event_(
      name = "job.retry.submitted",
      payload = Map(
        "job-id" -> jobid.value,
        "status" -> JobStatus.Submitted.toString
      )
    )
    _run_job_async_(jobid, record.tasks, record.submittedContext)
  }

  private def _append_timeline_for_control_(
    jobid: JobId,
    command: JobControlCommand,
    status: JobStatus
  ): Unit =
    command match {
      case JobControlCommand.Cancel =>
        _append_timeline_(jobid, "job.cancelled", None, None, Some(status.toString))
      case JobControlCommand.Suspend =>
        _append_timeline_(jobid, "job.suspended", None, None, Some(status.toString))
      case JobControlCommand.Resume =>
        _append_timeline_(jobid, "job.resumed", None, None, Some(status.toString))
      case JobControlCommand.Retry =>
        ()
    }

  private def _append_event_for_control_(
    jobid: JobId,
    command: JobControlCommand,
    status: JobStatus
  ): Unit =
    command match {
      case JobControlCommand.Cancel =>
        _append_event_(
          name = "job.cancelled",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> status.toString,
            "control-command" -> command.toString
          )
        )
      case JobControlCommand.Suspend =>
        _append_event_(
          name = "job.suspended",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> status.toString,
            "control-command" -> command.toString
          )
        )
      case JobControlCommand.Resume =>
        _append_event_(
          name = "job.resumed",
          payload = Map(
            "job-id" -> jobid.value,
            "status" -> status.toString,
            "control-command" -> command.toString
          )
        )
      case JobControlCommand.Retry =>
        ()
    }

  private def _control_result_for(status: JobStatus): Option[JobResult] =
    status match {
      case JobStatus.Cancelled => Some(JobResult.Failure(Conclusion.simple("job cancelled")))
      case _ => None
    }

  private def _await_control_sync(
    jobid: JobId,
    request: JobControlRequest,
    target: JobStatus
  ): Consequence[JobControlResponse] = {
    val deadline = System.currentTimeMillis() + math.max(0L, request.option.timeoutMillis)
    var status = getStatus(jobid).getOrElse(target)
    while (
      request.command == JobControlCommand.Retry &&
      !_is_retry_settled(status) &&
      System.currentTimeMillis() < deadline
    ) {
      Thread.sleep(math.max(1L, request.option.pollMillis))
      status = getStatus(jobid).getOrElse(status)
    }
    val timedout =
      request.command == JobControlCommand.Retry &&
      !_is_retry_settled(status) &&
      System.currentTimeMillis() >= deadline
    if (timedout) {
      _control_timeout(s"sync timeout for job control: ${jobid.value}")
    } else {
      val response = request.command match {
        case JobControlCommand.Retry =>
          getResponse(jobid)
        case _ =>
          Some(OperationResponse.Scalar(status.toString))
      }
      Consequence.success(
        JobControlResponse(
          jobId = jobid,
          status = status,
          response = response,
          async = false
        )
      )
    }
  }

  private def _is_retry_settled(status: JobStatus): Boolean =
    status match {
      case JobStatus.Succeeded | JobStatus.Failed | JobStatus.Cancelled => true
      case _ => false
    }

  private def _transition_for(
    command: JobControlCommand,
    status: JobStatus
  ): Consequence[JobStatus] =
    (command, status) match {
      case (JobControlCommand.Cancel, JobStatus.Submitted | JobStatus.Running | JobStatus.Suspended) =>
        Consequence.success(JobStatus.Cancelled)
      case (JobControlCommand.Suspend, JobStatus.Submitted | JobStatus.Running) =>
        Consequence.success(JobStatus.Suspended)
      case (JobControlCommand.Resume, JobStatus.Suspended) =>
        Consequence.success(JobStatus.Running)
      case (JobControlCommand.Retry, JobStatus.Failed | JobStatus.Cancelled) =>
        Consequence.success(JobStatus.Submitted)
      case _ =>
        _control_invalid_transition(command, status)
    }

  private def _can_run_next_task(jobid: JobId): Boolean =
    _get_record(jobid).exists(r => r.status != JobStatus.Cancelled)

  private def _await_if_suspended(jobid: JobId): Boolean = {
    var status = _get_record(jobid).map(_.status)
    while (status.contains(JobStatus.Suspended)) {
      Thread.sleep(10L)
      status = _get_record(jobid).map(_.status)
    }
    status.forall(_ != JobStatus.Cancelled)
  }

  private def _control_invalid_transition[A](
    command: JobControlCommand,
    status: JobStatus
  ): Consequence[A] =
    Consequence.operationInvalid(
      s"job.control.${command.toString.toLowerCase}: invalid transition: status=${status.toString.toLowerCase}"
    )

  private def _control_failure[A](message: String): Consequence[A] =
    Consequence.operationInvalid(s"job.control: $message")

  private def _control_timeout[A](message: String): Consequence[A] =
    Failures.fail(
      Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Unavailable),
      Seq(
        Descriptor.Facet.Operation("job.control"),
        Descriptor.Facet.Message(s"job.control: $message")
      )
    )

  private def _append_task_running_(
    jobid: JobId,
    taskid: TaskId,
    parent: Option[TaskId],
    startedat: Instant,
    taskdef: JobTask
  ): Unit =
    _mutate_record(jobid) { record =>
      val task = JobTaskReadModel(
        taskId = taskid,
        parentTaskId = parent,
        status = JobTaskStatus.Running,
        startedAt = startedat,
        finishedAt = None,
        result = JobTaskResultSummary(success = true, message = None),
        component = taskdef.componentName,
        service = taskdef.serviceName,
        operation = taskdef.operationName
      )
      val timeline = _next_timeline(
        record.timeline,
        "task.running",
        Some(taskid),
        parent,
        None
      )
      record.copy(
        status = JobStatus.Running,
        activeTaskCount = record.activeTaskCount + 1,
        taskReadModels = record.taskReadModels :+ task,
        timeline = timeline,
        updatedAt = Instant.now()
      )
    }

  private def _append_task_finished_(
    jobid: JobId,
    taskid: TaskId,
    parent: Option[TaskId],
    status: JobTaskStatus,
    summary: JobTaskResultSummary,
    finishedat: Instant
  ): Unit =
    _mutate_record(jobid) { record =>
      val tasks = record.taskReadModels.map { task =>
        if (task.taskId == taskid)
          task.copy(
            status = status,
            finishedAt = Some(finishedat),
            result = summary
          )
        else
          task
      }
      val timeline = _next_timeline(
        record.timeline,
        status match {
          case JobTaskStatus.Succeeded => "task.succeeded"
          case JobTaskStatus.Failed => "task.failed"
          case JobTaskStatus.Running => "task.running"
        },
        Some(taskid),
        parent,
        summary.message
      )
      record.copy(
        activeTaskCount = math.max(0, record.activeTaskCount - 1),
        taskReadModels = tasks,
        timeline = timeline,
        updatedAt = Instant.now()
      )
    }

  private def _mark_base_completion_(
    jobid: JobId,
    result: Option[JobResult]
  ): Unit = {
    _mutate_record(jobid) { record =>
      record.copy(
        baseTasksCompleted = true,
        deferredResult = result.orElse(record.deferredResult),
        updatedAt = Instant.now()
      )
    }
    _settle_if_ready_(jobid)
  }

  private def _update_deferred_result_(
    jobid: JobId,
    result: Option[JobResult]
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        deferredResult = result.orElse(record.deferredResult),
        updatedAt = Instant.now()
      )
    }

  private def _settle_if_ready_(jobid: JobId): Unit =
    _get_record(jobid).foreach { record =>
      if (record.baseTasksCompleted && record.activeTaskCount == 0) {
        record.deferredResult match {
          case Some(JobResult.Failure(c)) if record.status != JobStatus.Cancelled =>
            _append_timeline_(jobid, "job.failed", None, None, c.observation.getEffectiveMessage)
            _update_record_(jobid, JobStatus.Failed, Some(JobResult.Failure(c)))
            _append_event_(
              name = "job.failed",
              payload = Map(
                "job-id" -> jobid.value,
                "status" -> JobStatus.Failed.toString,
                "message" -> c.show
              )
            )
          case Some(success @ JobResult.Success(_)) =>
            _append_timeline_(jobid, "job.succeeded", None, None, None)
            _update_record_(jobid, JobStatus.Succeeded, Some(success))
            _append_event_(
              name = "job.succeeded",
              payload = Map(
                "job-id" -> jobid.value,
                "status" -> JobStatus.Succeeded.toString
              )
            )
          case None =>
            ()
        }
        _mutate_record(jobid)(_.copy(baseTasksCompleted = false, deferredResult = None, updatedAt = Instant.now()))
      }
    }

  private def _append_timeline_(
    jobid: JobId,
    kind: String,
    taskid: Option[TaskId],
    parent: Option[TaskId],
    note: Option[String]
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        timeline = _next_timeline(record.timeline, kind, taskid, parent, note),
        updatedAt = Instant.now()
      )
    }

  private def _next_timeline(
    current: Vector[JobTimelineEvent],
    kind: String,
    taskid: Option[TaskId],
    parent: Option[TaskId],
    note: Option[String]
  ): Vector[JobTimelineEvent] = {
    val seq = current.lastOption.map(_.sequence + 1).getOrElse(1L)
    current :+ JobTimelineEvent(
      sequence = seq,
      occurredAt = Instant.now(),
      kind = kind,
      taskId = taskid,
      parentTaskId = parent,
      note = note
    )
  }

  private def _task_page(
    record: JobRecord,
    offset: Int,
    limit: Int
  ): JobTaskPage = {
    val sorted = record.taskReadModels
      .sortBy(t => (t.startedAt.toEpochMilli, t.taskId.print))
    val page = _page(sorted, offset, limit)
    JobTaskPage(
      offset = page.offset,
      limit = page.limit,
      totalCount = page.total,
      fetchedCount = page.items.size,
      tasks = page.items
    )
  }

  private def _timeline_page(
    record: JobRecord,
    offset: Int,
    limit: Int
  ): JobTimelinePage = {
    val sorted = record.timeline.sortBy(e => (e.sequence, e.occurredAt.toEpochMilli))
    val page = _page(sorted, offset, limit)
    JobTimelinePage(
      offset = page.offset,
      limit = page.limit,
      totalCount = page.total,
      fetchedCount = page.items.size,
      events = page.items
    )
  }

  private def _trace_tree(record: JobRecord): JobTraceTree = {
    val taskids = record.taskReadModels.map(_.taskId)
    val eventsbytask = taskids.map { tid =>
      tid -> record.timeline.filter(_.taskId.contains(tid))
    }.toMap
    val childrenbyparent = record.taskReadModels.groupBy(_.parentTaskId)
    def build(parent: Option[TaskId]): Vector[JobTraceTaskNode] =
      childrenbyparent
        .getOrElse(parent, Vector.empty)
        .sortBy(_.startedAt.toEpochMilli)
        .map { task =>
          JobTraceTaskNode(
            taskId = task.taskId,
            parentTaskId = task.parentTaskId,
            events = eventsbytask.getOrElse(task.taskId, Vector.empty).sortBy(_.sequence),
            children = build(Some(task.taskId))
          )
        }
    JobTraceTree(
      jobId = record.id,
      roots = build(None)
    )
  }

  private def _event_lineage(record: JobRecord): JobEventLineage = {
    val parameters = record.debug.parameters
    def _param(key: String): Option[String] =
      parameters.get(key).map(_.trim).filter(_.nonEmpty)

    def _failure_disposition(
      jobrelation: Option[String],
      failurepolicy: Option[String]
    ): AsyncFailureDisposition =
      (jobrelation.map(_.toLowerCase(java.util.Locale.ROOT)), failurepolicy.map(_.toLowerCase(java.util.Locale.ROOT))) match {
        case (Some("newjob"), Some("retry")) if record.status == JobStatus.Failed =>
          AsyncFailureDisposition.Retryable
        case (Some("newjob"), Some("fail")) if record.status == JobStatus.Failed =>
          AsyncFailureDisposition.Terminal
        case _ =>
          AsyncFailureDisposition.NotApplicable
      }

    val jobrelation = _param("reception.jobRelation")
    val failurepolicy = _param("failure.policy")

    JobEventLineage(
      eventName = _param("event.name"),
      eventKind = _param("event.kind"),
      parentJobId =
        _param("cncf.context.jobId")
          .orElse(record.submittedContext.jobContext.jobId.map(_.print)),
      correlationId =
        _param("cncf.context.correlationId")
          .orElse(record.submittedContext.observability.correlationId.map(_.print)),
      causationId =
        _param("cncf.context.causationId")
          .orElse(record.submittedContext.jobContext.causationId),
      sourceSubsystem = _param("cncf.source.subsystem"),
      sourceComponent = _param("cncf.source.component"),
      targetSubsystem = _param("cncf.target.subsystem"),
      targetComponent = _param("cncf.target.component"),
      receptionRule = _param("reception.rule"),
      receptionPolicy = _param("reception.policy"),
      policySource = _param("reception.policySource"),
      jobRelation = jobrelation,
      taskRelation = _param("reception.taskRelation"),
      transactionRelation = _param("reception.transactionRelation"),
      sagaRelation = _param("saga.relation"),
      failurePolicy = failurepolicy,
      failureDisposition = _failure_disposition(jobrelation, failurepolicy)
    )
  }

  private final case class _Page[A](
    offset: Int,
    limit: Int,
    total: Int,
    items: Vector[A]
  )

  private def _page[A](items: Vector[A], offset: Int, limit: Int): _Page[A] = {
    val normalizedoffset = math.max(0, offset)
    val normalizedlimit = math.max(0, limit)
    val dropped = if (normalizedoffset > 0) items.drop(normalizedoffset) else items
    val sliced = if (normalizedlimit > 0) dropped.take(normalizedlimit) else Vector.empty
    _Page(normalizedoffset, normalizedlimit, items.size, sliced)
  }

  private def _result_summary(record: JobRecord): JobResultSummary =
    record.result match {
      case Some(JobResult.Success(_)) =>
        JobResultSummary(JobStatus.Succeeded, success = true, message = Some("ok"))
      case Some(JobResult.Failure(conclusion)) =>
        JobResultSummary(JobStatus.Failed, success = false, message = conclusion.observation.getEffectiveMessage)
      case None =>
        record.status match {
          case JobStatus.Failed =>
            JobResultSummary(JobStatus.Failed, success = false, message = Some("failed"))
          case JobStatus.Succeeded =>
            JobResultSummary(JobStatus.Succeeded, success = true, message = Some("ok"))
          case m =>
            JobResultSummary(m, success = false, message = Some("running"))
        }
    }

  private def _origin(record: JobRecord): JobDataOrigin =
    record.persistence match {
      case JobPersistencePolicy.Persistent => JobDataOrigin.Durable
      case JobPersistencePolicy.Ephemeral => JobDataOrigin.Runtime
    }

  private def _update_record_(
    jobid: JobId,
    status: JobStatus,
    result: Option[JobResult]
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        status = status,
        result = result.orElse(record.result),
        updatedAt = Instant.now()
      )
    }

  private def _mutate_record(jobid: JobId)(f: JobRecord => JobRecord): Unit =
    _get_record(jobid).foreach { current =>
      _put_record(f(current))
    }

  private def _put_record(record: JobRecord): Unit =
    record.persistence match {
      case JobPersistencePolicy.Persistent =>
        _durable_jobs.put(record.id, record)
      case JobPersistencePolicy.Ephemeral =>
        _runtime_jobs.put(record.id, record)
    }

  private def _get_record(jobid: JobId): Option[JobRecord] =
    Option(_durable_jobs.get(jobid)).orElse(Option(_runtime_jobs.get(jobid)))

  private def _request_summary(tasks: List[JobTask]): Option[String] =
    tasks.headOption.flatMap {
      case m: ActionTask => Some(m.action.show)
      case _ => None
    }

  private def _request_parameters(tasks: List[JobTask]): Map[String, String] =
    tasks.headOption.collect {
      case m: ActionTask =>
        val req = m.action.request
        val args = req.arguments.map(a => a.name -> a.value.toString).toMap
        val switches = req.switches.map(s => s.name -> s.value.toString).toMap
        val props = req.properties.map(p => p.name -> p.value.toString).toMap
        args ++ switches ++ props
    }.getOrElse(Map.empty)

  private def _default_submit_option(tasks: List[JobTask]): JobSubmitOption =
    tasks.headOption match {
      case Some(ActionTask(_, _: QueryAction, _, _)) =>
        JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)
      case _ =>
        JobSubmitOption(persistence = JobPersistencePolicy.Persistent)
    }

  private def _append_event_(
    name: String,
    payload: Map[String, Any]
  ): Unit =
    _event_store.foreach { store =>
      val _ = store.append(
        Vector(
          EventRecord(
            id = EventId.generate(),
            name = name,
            kind = name,
            payload = payload,
            attributes = Map.empty,
            createdAt = Instant.now(),
            persistent = true,
            status = EventRecord.Status.Stored,
            lane = EventLane.NonTransactional
          )
        )
      )
    }
}

object InMemoryJobEngine {
  def create(): InMemoryJobEngine =
    new InMemoryJobEngine()(scala.concurrent.ExecutionContext.global)
}

final case class JobRecord(
  id: JobId,
  tasks: List[JobTask],
  submittedContext: ExecutionContext,
  status: JobStatus,
  result: Option[JobResult],
  persistence: JobPersistencePolicy,
  createdAt: Instant,
  updatedAt: Instant,
  taskReadModels: Vector[JobTaskReadModel],
  timeline: Vector[JobTimelineEvent],
  debug: JobDebugInfo,
  deferredResult: Option[JobResult] = None,
  activeTaskCount: Int = 0,
  baseTasksCompleted: Boolean = false
)
