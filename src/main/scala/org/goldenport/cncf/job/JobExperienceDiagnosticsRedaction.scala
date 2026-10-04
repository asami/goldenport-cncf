package org.goldenport.cncf.job

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] object JobExperienceDiagnosticsRedaction {
  def tasks(page: JobTaskPage): JobTaskPage =
    page.copy(tasks = page.tasks.map { task =>
      task.copy(
        component = None,
        service = None,
        operation = None,
        taskKind = taskKind(task.taskKind),
        targetKind = targetKind(task.targetKind),
        relation = relation(task.relation),
        transactionRole = transactionRole(task.transactionRole),
        transactionScope = transactionScope(task.transactionScope),
        transactionOutcome = transactionOutcome(task.transactionOutcome),
        compensationActionRef = None,
        compensationStatus = compensationStatus(task.compensationStatus),
        compensationFailureSummary = None,
        result = JobTaskResultSummary(task.result.success, None)
      )
    })

  def timeline(page: JobTimelinePage): JobTimelinePage =
    page.copy(events = page.events.map(event => event.copy(kind = timelineKind(event.kind), note = None)))

  def taskKind(value: String): String =
    if (_task_kind.contains(value)) value else "redacted"

  private val _task_kind: Set[String] = Set(
    "action",
    "operation",
    "aggregate",
    "event",
    "continuation"
  )

  def targetKind(value: Option[String]): Option[String] =
    value.filter(_target_kind.contains)

  private val _target_kind: Set[String] = Set(
    "action",
    "operation",
    "aggregate",
    "event",
    "continuation"
  )

  def relation(value: Option[String]): Option[String] =
    value.filter(_relation.contains)

  private val _relation: Set[String] = Set(
    "root",
    "child",
    "event-reception",
    "continuation",
    "compensation"
  )

  def transactionRole(value: Option[String]): Option[String] =
    value.filter(_transaction_role.contains)

  private val _transaction_role: Set[String] = Set(
    "own",
    "join",
    "none"
  )

  def transactionScope(value: Option[String]): Option[String] =
    value.filter(_transaction_scope.contains)

  private val _transaction_scope: Set[String] = Set(
    "per-task",
    "whole-job",
    "none"
  )

  def transactionOutcome(value: Option[String]): Option[String] =
    value.filter(_transaction_outcome.contains)

  private val _transaction_outcome: Set[String] = Set(
    "pending",
    "running",
    "committed",
    "failed",
    "compensated",
    "compensation-committed",
    "compensation-failed"
  )

  def compensationStatus(value: Option[String]): Option[String] =
    value.filter(_compensation_status.contains)

  private val _compensation_status: Set[String] = Set(
    "not-required",
    "pending",
    "succeeded",
    "failed"
  )

  def timelineKind(value: String): String =
    if (_timeline_kind.contains(value)) value else "redacted"

  private val _timeline_kind: Set[String] = Set(
    "created",
    "submitted",
    "running",
    "completed",
    "succeeded",
    "failed",
    "cancelled",
    "suspended",
    "resumed",
    "job.submitted",
    "job.async.queued",
    "job.running",
    "job.scheduler.started",
    "job.delayed.scheduled",
    "job.delayed.enqueued",
    "job.succeeded",
    "job.failed",
    "job.cancelled",
    "job.suspended",
    "job.resumed",
    "job.retry.submitted",
    "job.retry.immediate.submitted",
    "job.retry.delayed.scheduled",
    "job.retry.delayed.submitted",
    "job.retry.delayed.enqueued",
    "job.retry.exhausted",
    "job.dead-letter",
    "job.poison",
    "job.recovery-required",
    "job.runtime-rehydrated",
    "job.same-job-async.queued",
    "job.compensation.started",
    "job.compensation.finished",
    "job.durable-start-intent",
    "job.durable-running-intent",
    "job.durable-terminal-outcome-checkpoint",
    "task.running",
    "task.succeeded",
    "task.failed",
    "task.transaction.committed",
    "task.transaction.failed",
    "task.compensation.started",
    "task.compensation.succeeded",
    "task.compensation.failed",
    "task.compensation.missing",
    "task.durable-start-intent",
    "task.durable-outcome-checkpoint"
  )

}
