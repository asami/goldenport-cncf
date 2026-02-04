package org.goldenport.cncf.job

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext as ScalaExecutionContext, Future}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.id.UniversalId
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.component.Component

/*
 * @since   Jan.  4, 2026
 * @version Feb.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final case class JobId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "job")

object JobId {
  def generate(): JobId =
    JobId("cncf", "job")
}

final case class TaskId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "task")

object TaskId {
  def generate(): TaskId =
    TaskId("cncf", "task")
}

final case class ActionId(
  major: String,
  minor: String
) extends UniversalId(major, minor, "action")

object ActionId {
  def generate(): ActionId =
    ActionId("cncf", "action")
}

final case class JobContext(
  jobId: Option[JobId],
  taskId: Option[TaskId],
  actionId: Option[ActionId]
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

trait JobEngine {
  def submit(tasks: List[JobTask], ctx: ExecutionContext): JobId
  def getStatus(jobId: JobId): Option[JobStatus]
  def getResult(jobId: JobId): Option[JobResult]
}

final class InMemoryJobEngine(
  implicit val executionContext: ScalaExecutionContext
) extends JobEngine {
  private val _jobs = new ConcurrentHashMap[JobId, JobRecord]()

  def submit(tasks: List[JobTask], ctx: ExecutionContext): JobId = {
    val jobid = JobId.generate()
    val record = JobRecord(jobid, tasks, JobStatus.Submitted, None)
    _jobs.put(jobid, record)
    _run_job_(jobid, tasks, ctx)
    jobid
  }

  def getStatus(jobId: JobId): Option[JobStatus] =
    Option(_jobs.get(jobId)).map(_.status)

  def getResult(jobId: JobId): Option[JobResult] =
    Option(_jobs.get(jobId)).flatMap(_.result)

  private def _run_job_(
    jobid: JobId,
    tasks: List[JobTask],
    ctx: ExecutionContext
  ): Unit = {
    Future {
      _update_record_(jobid, JobStatus.Running, None)
      tasks match {
        case Nil =>
          _update_record_(jobid, JobStatus.Succeeded, None)
        case task :: _ =>
          val taskid = TaskId.generate()
          val jobcontext = JobContext(
            jobId = Some(jobid),
            taskId = Some(taskid),
            actionId = Some(task.actionId)
          )
          val executioncontext = ExecutionContext.withJobContext(ctx, jobcontext)
          task.run(executioncontext) match {
            case TaskSucceeded(res) =>
              _update_record_(jobid, JobStatus.Succeeded, Some(JobResult.Success(res)))
            case TaskFailed(c) =>
              _update_record_(jobid, JobStatus.Failed, Some(JobResult.Failure(c)))
          }
      }
    }
    ()
  }

  private def _update_record_(
    jobid: JobId,
    status: JobStatus,
    result: Option[JobResult]
  ): Unit = {
    val current = _jobs.get(jobid)
    if (current != null) {
      _jobs.put(jobid, current.copy(status = status, result = result))
    }
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
  result: Option[JobResult]
)
