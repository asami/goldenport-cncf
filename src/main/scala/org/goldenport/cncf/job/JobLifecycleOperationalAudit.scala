package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventPublishResult, EventRecord}

/*
 * Observes the captured native source after publication returns normally.
 * No event construction, lookup, provider invocation or audit state belongs here.
 *
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobLifecycleOperationalAudit {
  def observeStore(record: JobRecord, eventName: String, occurredAt: Instant,
    result: Consequence[Vector[EventRecord]])(using ExecutionContext): Consequence[Vector[EventRecord]] = {
    _observe {
      result match {
        case Consequence.Success(rows) =>
          val identity = _acknowledgement(record, eventName, rows)
          _emit(record, eventName, occurredAt, "store", "returned", identity)
        case Consequence.Failure(_) =>
          _emit(record, eventName, occurredAt, "store", "failed", Vector("event-identity-available" -> false))
        case null => ()
      }
    }
    result
  }

  def observeBus(record: JobRecord, eventName: String, occurredAt: Instant,
    result: Consequence[EventPublishResult])(using ExecutionContext): Consequence[EventPublishResult] = {
    _observe {
      result match {
        case Consequence.Success(value) =>
          val facts: Vector[(String, String | Int | Long | Boolean)] = Vector(
            "event-identity-available" -> false, "persisted" -> value.persisted
          ) ++ (if (value.dispatchedCount >= 0) Vector("dispatched-count" -> value.dispatchedCount) else Vector.empty)
          _emit(record, eventName, occurredAt, "bus", "returned", facts)
        case Consequence.Failure(_) =>
          _emit(record, eventName, occurredAt, "bus", "failed", Vector("event-identity-available" -> false))
        case null => ()
      }
    }
    result
  }

  def observeUnavailable(record: JobRecord, eventName: String, occurredAt: Instant)(using ExecutionContext): Unit =
    _observe { _emit(record, eventName, occurredAt, "unavailable", "unavailable", Vector("event-identity-available" -> false)) }

  private val _event_names: Set[String] = Set(
    "job.submitted", "job.running", "job.succeeded", "job.failed",
    "job.delayed.scheduled", "job.delayed.enqueued", "job.retry.immediate.submitted",
    "job.retry.delayed.scheduled", "job.retry.delayed.submitted", "job.retry.submitted",
    "job.recovery-required", "job.cancelled", "job.suspended", "job.resumed"
  )

  private def _observe(body: => Unit): Unit = try body catch { case NonFatal(_) => () }

  private def _acknowledgement(record: JobRecord, name: String, rows: Vector[EventRecord]): Vector[(String, String | Long | Boolean)] = {
    val identity = try {
      Option(rows).filter(_.size == 1).flatMap(_.headOption).flatMap(Option(_)).filter { row =>
        row.name == name && row.kind == name && row.sequence >= 0 &&
          row.attributes.get("cncf.job.id").contains(record.id.value)
      }.flatMap(row => _reference(row.id.value).map(id => Vector("event-id" -> id, "event-sequence" -> row.sequence)))
    } catch { case NonFatal(_) => None }
    Vector("event-identity-available" -> identity.nonEmpty) ++ identity.getOrElse(Vector.empty)
  }

  private def _emit(record: JobRecord, name: String, occurredat: Instant, transport: String,
    outcome: String, acknowledgement: Vector[(String, String | Int | Long | Boolean)])(using ExecutionContext): Unit =
    if (_event_names.contains(name)) {
      val jobid = _reference(record.id.value)
      val retry = record.retry
      val tasks = record.taskReadModels
      val common: Vector[(String, String | Int | Long | Boolean)] = Vector(
        "event-kind" -> name, "transport" -> transport, "occurred-at" -> occurredat.toString
      ) ++ jobid.map("job-id" -> _).toVector ++ (if (jobid.isEmpty) Vector("job-id-omitted" -> true) else Vector.empty)
      val call: Vector[(String, String | Int | Long | Boolean)] = Vector(
        "status" -> _job_status(record.status),
        "persistence" -> (record.persistence match {
          case JobPersistencePolicy.Persistent => "persistent"
          case JobPersistencePolicy.Ephemeral => "ephemeral"
        }),
        "run-mode" -> (record.runMode match { case JobRunMode.Sync => "sync"; case JobRunMode.Async => "async" }),
        "attempt-count" -> retry.attemptCount, "max-attempts" -> retry.maxAttempts,
        "retry-kind" -> (retry.kind match {
          case JobRetryKind.None => "none"
          case JobRetryKind.Immediate => "now"
          case JobRetryKind.Delayed => "later"
        }),
        "retry-exhausted" -> retry.exhausted, "recovery-required" -> retry.recoveryRequired,
        "dead-letter" -> retry.deadLetter, "poison" -> retry.poison,
        "task-count" -> tasks.size, "truncated" -> (tasks.size > 100),
        "definition-present" -> record.debug.jobDefinitionSnapshot.nonEmpty,
        "input-present" -> record.input.nonEmpty, "calltree-present" -> record.debug.calltree.nonEmpty
      )
      JobOperationalAuditTransport.lifecycle(outcome, "call", common ++ call ++ acknowledgement)
      jobid.foreach { _ =>
        tasks.take(100).foreach { task =>
          _observe {
            val facts: Vector[(String, String | Int | Long | Boolean)] = Vector(
              "status" -> _task_status(task.status),
              "task-kind" -> JobExperienceDiagnosticsRedaction.taskKind(task.taskKind),
              "recovery-required" -> task.recoveryRequired
            ) ++ _optional_reference("task-id", Some(task.taskId.value)) ++
              _optional_reference("parent-task-id", task.parentTaskId.map(_.value)) ++
              _optional_reference("compensates-task-id", task.compensatesTaskId.map(_.value)) ++
              _optional("target-kind", JobExperienceDiagnosticsRedaction.targetKind(task.targetKind)) ++
              _optional("relation", JobExperienceDiagnosticsRedaction.relation(task.relation)) ++
              _optional("transaction-role", JobExperienceDiagnosticsRedaction.transactionRole(task.transactionRole)) ++
              _optional("transaction-scope", JobExperienceDiagnosticsRedaction.transactionScope(task.transactionScope)) ++
              _optional("transaction-outcome", JobExperienceDiagnosticsRedaction.transactionOutcome(task.transactionOutcome)) ++
              _optional("compensation-status", JobExperienceDiagnosticsRedaction.compensationStatus(task.compensationStatus))
            JobOperationalAuditTransport.lifecycle(outcome, "task", common ++ facts)
          }
        }
      }
    }

  private def _reference(value: String): Option[String] = Option(value).filter(value =>
    value.nonEmpty && !value.exists(c => Character.isISOControl(c)) && value.getBytes(UTF_8).length <= 512)

  private def _optional_reference(key: String, value: Option[String]): Vector[(String, String)] =
    _optional(key, value.flatMap(_reference))

  private def _optional(key: String, value: Option[String]): Vector[(String, String)] = value.map(key -> _).toVector

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
}
