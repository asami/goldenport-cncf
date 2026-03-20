package org.goldenport.cncf.job

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext as ScalaExecutionContext, Future}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.id.UniversalId
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, QueryAction}
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.observation.Descriptor.Facet
import org.goldenport.provisional.observation.Taxonomy

/*
 * @since   Jan.  4, 2026
 * @version Mar. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "job")

object JobId {
  def generate(): JobId =
    JobId("cncf", "job")

  def parse(s: String): Consequence[JobId] =
    UniversalId.parseParts(s, "job").map(parts => JobId(parts.major, parts.minor))
}

final case class TaskId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "task")

object TaskId {
  def generate(): TaskId =
    TaskId("cncf", "task")

  def parse(s: String): Consequence[TaskId] =
    UniversalId.parseParts(s, "task").map(parts => TaskId(parts.major, parts.minor))
}

final case class ActionId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "action")

object ActionId {
  def generate(): ActionId =
    ActionId("cncf", "action")

  def parse(s: String): Consequence[ActionId] =
    UniversalId.parseParts(s, "action").map(parts => ActionId(parts.major, parts.minor))
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
  case object Succeeded extends JobStatus
  case object Failed extends JobStatus
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
  def run(ctx: ExecutionContext): TaskOutcome
}

final case class ActionTask(
  actionId: ActionId,
  action: Action,
  actionEngine: ActionEngine,
  component: Option[Component]
) extends JobTask {
  def run(ctx: ExecutionContext): TaskOutcome = {
    val correlationid = ctx.observability.correlationId
    val core = ActionCall.Core(action, ctx, component, correlationid)
    val call = action.createCall(core)
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
  requestSummary: Option[String] = None,
  parameters: Map[String, String] = Map.empty,
  executionNotes: Vector[String] = Vector.empty
)

enum JobDataOrigin {
  case Durable
  case Runtime
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
  result: JobTaskResultSummary
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
  createdAt: Instant,
  updatedAt: Instant,
  tasks: JobTaskPage,
  timeline: JobTimelinePage,
  traceTree: JobTraceTree,
  debug: JobDebugInfo,
  resultSummary: JobResultSummary,
  result: Option[OperationResponse]
)

trait JobQueryPolicy {
  def authorizeRead(using ExecutionContext): Consequence[Unit]
}

object JobQueryPolicy {
  val default: JobQueryPolicy = new DefaultJobQueryPolicy

  private final class DefaultJobQueryPolicy extends JobQueryPolicy {
    private val _read_caps = Set("job_view", "job_admin", "content_manager", "content_admin")

    def authorizeRead(using ctx: ExecutionContext): Consequence[Unit] =
      if (ctx.security.hasAnyCapability(_read_caps))
        Consequence.unit
      else
        Consequence.fail(
          Taxonomy(Taxonomy.Category.Operation, Taxonomy.Symptom.Illegal),
          Facet.Operation("job.query"),
          Facet.Message(s"required capability: ${_read_caps.toVector.sorted.mkString("|")}")
        )
  }
}

trait JobEngine {
  def submit(tasks: List[JobTask], ctx: ExecutionContext): JobId
  def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): JobId
  def getStatus(jobId: JobId): Option[JobStatus]
  def getResult(jobId: JobId): Option[JobResult]

  def getResponse(jobId: JobId): Option[OperationResponse] =
    getResult(jobId) match {
      case Some(JobResult.Success(response)) => Some(response)
      case _ => None
    }

  def query(jobId: JobId): Option[JobQueryReadModel]
  def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage]
  def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage]

  def queryVisible(
    jobId: JobId,
    policy: JobQueryPolicy = JobQueryPolicy.default
  )(using ExecutionContext): Consequence[Option[JobQueryReadModel]] =
    policy.authorizeRead.flatMap(_ => Consequence.success(query(jobId)))
}

final class InMemoryJobEngine(
  implicit val executionContext: ScalaExecutionContext
) extends JobEngine {
  private val _durable_jobs = new ConcurrentHashMap[JobId, JobRecord]()
  private val _runtime_jobs = new ConcurrentHashMap[JobId, JobRecord]()

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
    _run_job_(jobid, tasks, ctx)
    jobid
  }

  def getStatus(jobId: JobId): Option[JobStatus] =
    _get_record(jobId).map(_.status)

  def getResult(jobId: JobId): Option[JobResult] =
    _get_record(jobId).flatMap(_.result)

  def query(jobId: JobId): Option[JobQueryReadModel] =
    _get_record(jobId).map { record =>
      val tasks = _task_page(record, 0, math.max(record.taskReadModels.size, 1))
      val timeline = _timeline_page(record, 0, math.max(record.timeline.size, 1))
      JobQueryReadModel(
        jobId = record.id,
        status = record.status,
        persistence = record.persistence,
        origin = _origin(record),
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
        tasks = tasks,
        timeline = timeline,
        traceTree = _trace_tree(record),
        debug = record.debug,
        resultSummary = _result_summary(record),
        result = record.result.collect { case JobResult.Success(res) => res }
      )
    }

  def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage] =
    _get_record(jobId).map(_task_page(_, offset, limit))

  def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage] =
    _get_record(jobId).map(_timeline_page(_, offset, limit))

  private def _run_job_(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext
  ): Unit = {
    Future {
      _append_timeline_(jobid, "job.running", None, None, None)
      _update_record_(jobid, JobStatus.Running, None)
      tasks match {
        case Nil =>
          _append_timeline_(jobid, "job.succeeded", None, None, Some("no task"))
          _update_record_(jobid, JobStatus.Succeeded, None)
        case _ =>
          var previous: Option[TaskId] = None
          var failure: Option[Conclusion] = None
          var successResponse: Option[OperationResponse] = None
          tasks.foreach { task =>
            if (failure.isEmpty) {
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
              _append_task_running_(jobid, taskid, previous, startedat)
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
          failure match {
            case Some(c) =>
              _append_timeline_(jobid, "job.failed", None, None, c.observation.getEffectiveMessage)
              _update_record_(jobid, JobStatus.Failed, Some(JobResult.Failure(c)))
            case None =>
              _append_timeline_(jobid, "job.succeeded", None, None, None)
              _update_record_(
                jobid,
                JobStatus.Succeeded,
                successResponse.map(JobResult.Success.apply)
              )
          }
      }
    }
    ()
  }

  private def _append_task_running_(
    jobid: JobId,
    taskid: TaskId,
    parent: Option[TaskId],
    startedat: Instant
  ): Unit =
    _mutate_record(jobid) { record =>
      val task = JobTaskReadModel(
        taskId = taskid,
        parentTaskId = parent,
        status = JobTaskStatus.Running,
        startedAt = startedat,
        finishedAt = None,
        result = JobTaskResultSummary(success = true, message = None)
      )
      val timeline = _next_timeline(
        record.timeline,
        "task.running",
        Some(taskid),
        parent,
        None
      )
      record.copy(
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
        taskReadModels = tasks,
        timeline = timeline,
        updatedAt = Instant.now()
      )
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
}

object InMemoryJobEngine {
  def create(): InMemoryJobEngine =
    new InMemoryJobEngine()(scala.concurrent.ExecutionContext.global)
}

final case class JobRecord(
  id: JobId,
  tasks: List[JobTask],
  status: JobStatus,
  result: Option[JobResult],
  persistence: JobPersistencePolicy,
  createdAt: Instant,
  updatedAt: Instant,
  taskReadModels: Vector[JobTaskReadModel],
  timeline: Vector[JobTimelineEvent],
  debug: JobDebugInfo
)
