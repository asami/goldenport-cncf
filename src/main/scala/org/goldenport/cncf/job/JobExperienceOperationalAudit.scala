package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record

/*
 * Observes only completed, authorized facade values. No audit state is retained.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobExperienceOperationalAudit {
  def observeList(result: Consequence[JobExperiencePage])(using ctx: ExecutionContext): Consequence[JobExperiencePage] = {
    _observe {
      result match {
        case Consequence.Success(page) =>
          val count = page.entries.size
          val truncated = count > 100
          _emit("list", "returned", "call", Vector("returned-count" -> count, "truncated" -> truncated))
          page.entries.take(100).foreach { entry =>
            _observe {
              val jobid = _reference(entry.jobId.value)
              val status = _job_status(entry.canonicalStatus)
              val origin = _origin(entry.origin)
              jobid.foreach(id => _emit("list", "returned", "job", Vector("job-id" -> id, "status" -> status, "origin" -> origin)))
            }
          }
        case Consequence.Failure(_) => _emit("list", "failed", "call", Vector.empty)
      }
    }
    result
  }

  def observeGet(result: Consequence[JobExperienceView])(using ctx: ExecutionContext): Consequence[JobExperienceView] = {
    _observe {
      result match {
        case Consequence.Success(view) =>
          val summary = view.detail.summary
          val jobid = _job_reference(summary.jobId.value)
          val status = _job_status(summary.status)
          val origin = _origin(summary.origin)
          val attemptcount = view.detail.retry.attemptCount
          val recoveryrequired = view.detail.retry.recoveryRequired
          _emit("get", "returned", "call", jobid ++ Vector("status" -> status, "origin" -> origin,
            "attempt-count" -> attemptcount, "recovery-required" -> recoveryrequired))
        case Consequence.Failure(_) => _emit("get", "failed", "call", Vector.empty)
      }
    }
    result
  }

  def observeDiagnostics(result: Consequence[JobExperienceDiagnostics])(using ctx: ExecutionContext): Consequence[JobExperienceDiagnostics] = {
    _observe {
      result match {
        case Consequence.Success(value) =>
          val jobid = _reference(value.jobId.value)
          val taskcount = value.tasks.tasks.size
          val timelinecount = value.timeline.events.size
          val truncated = taskcount > 100 || timelinecount > 100
          _emit("diagnostics", "returned", "call", _job_reference(value.jobId.value) ++
            Vector("task-count" -> taskcount, "timeline-count" -> timelinecount, "truncated" -> truncated))
          jobid.foreach { id =>
            value.tasks.tasks.take(100).foreach { task =>
              _observe {
                val taskid = _optional_reference("task-id", Some(task.taskId.value))
                val parentid = _optional_reference("parent-task-id", task.parentTaskId.map(_.value))
                val compensatesid = _optional_reference("compensates-task-id", task.compensatesTaskId.map(_.value))
                val status = _task_status(task.status)
                val kind = JobExperienceDiagnosticsRedaction.taskKind(task.taskKind)
                val relation = JobExperienceDiagnosticsRedaction.relation(task.relation)
                val transactionrole = JobExperienceDiagnosticsRedaction.transactionRole(task.transactionRole)
                val transactionscope = JobExperienceDiagnosticsRedaction.transactionScope(task.transactionScope)
                val transactionoutcome = JobExperienceDiagnosticsRedaction.transactionOutcome(task.transactionOutcome)
                val compensationstatus = JobExperienceDiagnosticsRedaction.compensationStatus(task.compensationStatus)
                val recoveryrequired = task.recoveryRequired
                val fields: Vector[(String, String | Int | Long | Boolean)] = Vector("job-id" -> id, "status" -> status, "task-kind" -> kind,
                  "recovery-required" -> recoveryrequired) ++ taskid ++ parentid ++ compensatesid ++
                  _optional("relation", relation) ++ _optional("transaction-role", transactionrole) ++
                  _optional("transaction-scope", transactionscope) ++ _optional("transaction-outcome", transactionoutcome) ++
                  _optional("compensation-status", compensationstatus)
                _emit("diagnostics", "returned", "task", fields)
              }
            }
            value.timeline.events.take(100).foreach { event =>
              _observe {
                val sequence = event.sequence
                val occurredat = event.occurredAt.toString
                val kind = JobExperienceDiagnosticsRedaction.timelineKind(event.kind)
                val taskid = _optional_reference("task-id", event.taskId.map(_.value))
                val parentid = _optional_reference("parent-task-id", event.parentTaskId.map(_.value))
                _emit("diagnostics", "returned", "timeline", Vector("job-id" -> id,
                  "sequence" -> sequence, "event-kind" -> kind, "occurred-at" -> occurredat) ++ taskid ++ parentid)
              }
            }
          }
        case Consequence.Failure(_) => _emit("diagnostics", "failed", "call", Vector.empty)
      }
    }
    result
  }

  def observeControl(result: Consequence[JobControlResponse])(using ctx: ExecutionContext): Consequence[JobControlResponse] = {
    _observe {
      result match {
        case Consequence.Success(response) =>
          val jobid = _job_reference(response.jobId.value)
          val changed = response.changed
          val status = _job_status(response.status)
          val outcome = if (changed) "applied" else "replayed"
          _emit("control", outcome, "call", jobid ++ Vector("changed" -> changed, "status" -> status))
        case Consequence.Failure(_) => _emit("control", "failed", "call", Vector.empty)
      }
    }
    result
  }

  def observeCatalog(result: Consequence[Record])(using ctx: ExecutionContext): Consequence[Record] = {
    _observe {
      result match {
        case Consequence.Success(_) => _emit("catalog", "returned", "call", Vector.empty)
        case Consequence.Failure(_) => _emit("catalog", "failed", "call", Vector.empty)
      }
    }
    result
  }

  private def _observe(body: => Unit): Unit =
    try body catch { case NonFatal(_) => () }

  private def _emit(operation: String, outcome: String, recordkind: String, atoms: Vector[(String, String | Int | Long | Boolean)])(using ctx: ExecutionContext): Unit =
    JobOperationalAuditTransport.experience(operation, outcome, recordkind, atoms)

  private def _safe(value: String, bound: Int): Boolean =
    value != null && value.nonEmpty && !value.exists(c => Character.isISOControl(c)) && value.getBytes(UTF_8).length <= bound

  private def _reference(value: String): Option[String] = Option(value).filter(_safe(_, 512))

  private def _job_reference(value: String): Vector[(String, String | Boolean)] =
    _reference(value).map(id => Vector("job-id" -> id)).getOrElse(Vector("job-id-omitted" -> true))

  private def _optional_reference(key: String, value: Option[String]): Vector[(String, String)] =
    _optional(key, value.flatMap(_reference))

  private def _optional(key: String, value: Option[String]): Vector[(String, String)] =
    value.map(key -> _).toVector

  private def _job_status(status: JobStatus): String = status match {
    case JobStatus.Submitted => "submitted"
    case JobStatus.Running => "running"
    case JobStatus.Succeeded => "succeeded"
    case JobStatus.Failed => "failed"
    case JobStatus.Cancelled => "cancelled"
    case JobStatus.Suspended => "suspended"
  }

  private def _task_status(status: JobTaskStatus): String = status match {
    case JobTaskStatus.Running => "running"
    case JobTaskStatus.Succeeded => "succeeded"
    case JobTaskStatus.Failed => "failed"
  }

  private def _origin(origin: JobDataOrigin): String = origin match {
    case JobDataOrigin.Runtime => "runtime"
    case JobDataOrigin.Durable => "durable"
  }
}
