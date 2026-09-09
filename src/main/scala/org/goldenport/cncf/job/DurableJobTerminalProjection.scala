package org.goldenport.cncf.job

import org.goldenport.Consequence

/*
 * Package-internal terminal durable fact.  The retained snapshot is the
 * already canonically admitted closed record and provider revision; the
 * derived facade deliberately contains no live JobResult, JobTask,
 * ExecutionContext, provider, or payload body.
 *
 * @since   Sep.  9, 2026
 * @version Sep.  9, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final class DurableJobTerminalProjection private (
  private[job] val snapshot: DurableJobStoreSnapshot,
  private[job] val decision: DurableJobRecoveryDecision,
  val queryReadModel: JobQueryReadModel
)

/* Terminal registration retains only this closed disposition per candidate. */
private[job] enum DurableJobTerminalProjectionFact {
  case Registered
  case Missing
  case Corrupt
  case Refused
}

private[job] final case class DurableJobTerminalProjectionCandidateReport(
  ordinal: Long,
  jobId: String,
  fact: DurableJobTerminalProjectionFact
)

private[job] final case class DurableJobTerminalProjectionReport(
  candidates: Vector[DurableJobTerminalProjectionCandidateReport]
)

private[job] object DurableJobTerminalProjection {
  def project(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision
  ): Consequence[DurableJobTerminalProjection] =
    for {
      _ <- _candidate_snapshot_identity(candidate, snapshot)
      _ <- _canonical_terminal(snapshot, decision)
      jobid <- JobId.parse(snapshot.record.body.identity.jobId)
      tasks <- _collect(snapshot.record.body.tasks)(_task_read_model(_, snapshot.record))
      timeline <- _collect(snapshot.record.body.timeline)(_timeline_event)
    } yield new DurableJobTerminalProjection(
      snapshot,
      decision,
      _query_read_model(snapshot.record, jobid, tasks, timeline)
    )

  private def _candidate_snapshot_identity(
    candidate: DurableJobStartupRecoveryCandidate,
    snapshot: DurableJobStoreSnapshot
  ): Consequence[Unit] =
    if (candidate == null || snapshot == null || snapshot.record == null)
      Consequence.argumentInvalid("durable terminal projection requires an admitted candidate and record")
    else if (candidate.jobId != snapshot.record.body.identity.jobId)
      Consequence.stateInvalid("durable terminal projection candidate identity does not match the admitted record")
    else
      Consequence.unit

  private def _canonical_terminal(
    snapshot: DurableJobStoreSnapshot,
    decision: DurableJobRecoveryDecision
  ): Consequence[Unit] =
    if (decision == null)
      Consequence.argumentInvalid("durable terminal projection requires a recovery decision")
    else
      DurableJobRecordCodec.canonicalJson(snapshot.record).flatMap { _ =>
        (snapshot.record.body.lifecycle.status, snapshot.record.body.result, decision.outcome) match {
          case (
                DurableJobLifecycleStatus.Succeeded,
                DurableResultOutcome.Succeeded(_),
                DurableJobRecoveryOutcome.TerminalSucceeded
              ) => Consequence.unit
          case (
                DurableJobLifecycleStatus.Failed,
                DurableResultOutcome.Failed(_),
                DurableJobRecoveryOutcome.TerminalFailed
              ) => Consequence.unit
          case (
                DurableJobLifecycleStatus.Cancelled,
                DurableResultOutcome.Cancelled(_),
                DurableJobRecoveryOutcome.TerminalCancelled
              ) => Consequence.unit
          case _ =>
            Consequence.stateInvalid(
              "durable terminal projection requires a lifecycle/result-consistent terminal decision"
            )
        }
      }

  private def _task_read_model(
    descriptor: DurableTaskDescriptor,
    record: DurableJobRecord
  ): Consequence[JobTaskReadModel] =
    descriptor.transaction.outcome match {
      case DurableTransactionOutcome.Pending =>
        Consequence.stateInvalid(
          "terminal durable task transaction outcome pending is not representable"
        )
      case DurableTransactionOutcome.Committed =>
        _closed_task_read_model(descriptor, record, JobTaskStatus.Succeeded, "committed")
      case DurableTransactionOutcome.Failed =>
        _closed_task_read_model(descriptor, record, JobTaskStatus.Failed, "failed")
      case DurableTransactionOutcome.Compensated =>
        descriptor.compensation match {
          case Some(compensation) =>
            compensation.status match {
              case DurableCompensationStatus.Succeeded =>
                _closed_task_read_model(
                  descriptor,
                  record,
                  JobTaskStatus.Succeeded,
                  "compensation-committed"
                )
              case DurableCompensationStatus.Failed =>
                _closed_task_read_model(
                  descriptor,
                  record,
                  JobTaskStatus.Failed,
                  "compensation-failed"
                )
              case DurableCompensationStatus.NotRequired |
                  DurableCompensationStatus.Pending =>
                Consequence.stateInvalid(
                  "compensated terminal durable task has no representable compensation status"
                )
            }
          case None =>
            Consequence.stateInvalid(
              "compensated terminal durable task has no compensation descriptor"
            )
        }
    }

  private def _closed_task_read_model(
    descriptor: DurableTaskDescriptor,
    record: DurableJobRecord,
    status: JobTaskStatus,
    transactionoutcome: String
  ): Consequence[JobTaskReadModel] =
    for {
      taskid <- TaskId.parse(descriptor.taskId)
      parenttaskid <- _task_id(descriptor.parentTaskId)
      compensatestaskid <- _task_id(descriptor.compensation.map(_.compensatesTaskId))
    } yield {
      val compensation = descriptor.compensation
      JobTaskReadModel(
        taskId = taskid,
        parentTaskId = parenttaskid,
        status = status,
        startedAt = record.body.lifecycle.schedule.startedAt.getOrElse(record.body.identity.createdAt),
        finishedAt = record.body.lifecycle.schedule.completedAt,
        result = JobTaskResultSummary(
          success = status == JobTaskStatus.Succeeded,
          message = compensation.flatMap(_.failure.map(_.summary))
        ),
        component = Some(descriptor.target.operation.componentId),
        service = descriptor.target.operation.serviceId,
        operation = Some(descriptor.target.operation.operationId),
        taskKind = descriptor.kind.wire,
        targetKind = Some(descriptor.target.targetKind),
        relation = Some(descriptor.relation.kind.wire),
        transactionRole = Some(descriptor.transaction.role.wire),
        transactionScope = Some(descriptor.transaction.scope.wire),
        transactionOutcome = Some(transactionoutcome),
        compensationActionRef = compensation.map(_.operation.operationId),
        compensatesTaskId = compensatestaskid,
        compensationStatus = compensation.map(_.status.wire),
        compensationFailureSummary = compensation.flatMap(_.failure.map(_.summary)),
        recoveryRequired = false
      )
    }

  private def _timeline_event(
    event: DurableTimelineEvent
  ): Consequence[JobTimelineEvent] =
    _task_id(event.taskId).map { taskid =>
      JobTimelineEvent(
        sequence = event.sequence,
        occurredAt = event.occurredAt,
        kind = event.kind,
        taskId = taskid,
        parentTaskId = None,
        note = event.summary
      )
    }

  private def _task_id(value: Option[String]): Consequence[Option[TaskId]] =
    value match {
      case Some(taskid) => TaskId.parse(taskid).map(Some(_))
      case None => Consequence.success(None)
    }

  private def _query_read_model(
    record: DurableJobRecord,
    jobid: JobId,
    tasks: Vector[JobTaskReadModel],
    timeline: Vector[JobTimelineEvent]
  ): JobQueryReadModel = {
    val sortedtasks = tasks.sortBy(task => (task.startedAt.toEpochMilli, task.taskId.print))
    val sortedtimeline = timeline.sortBy(event => (event.sequence, event.occurredAt.toEpochMilli))
    JobQueryReadModel(
      jobId = jobid,
      status = _status(record.body.lifecycle.status),
      persistence = JobPersistencePolicy.Persistent,
      origin = JobDataOrigin.Durable,
      submitter = JobSubmitter(
        record.body.authorization.submitter.id,
        record.body.authorization.submitter.kind
      ),
      createdAt = record.body.identity.createdAt,
      updatedAt = record.body.identity.updatedAt,
      scheduledStartAt = record.body.lifecycle.schedule.scheduledAt,
      tasks = JobTaskPage(0, math.max(sortedtasks.size, 1), sortedtasks.size, sortedtasks.size, sortedtasks),
      timeline = JobTimelinePage(0, math.max(sortedtimeline.size, 1), sortedtimeline.size, sortedtimeline.size, sortedtimeline),
      traceTree = _trace_tree(jobid, sortedtasks, sortedtimeline),
      debug = JobDebugInfo(None, Map.empty, Vector.empty),
      input = None,
      lineage = _empty_lineage,
      continuation = JobContinuationSummary.empty,
      retry = JobRetryState(
        attemptCount = record.body.lifecycle.retry.attempts.size,
        maxAttempts = record.body.lifecycle.retry.maxAttempts,
        exhausted = record.body.lifecycle.retry.exhausted,
        recoveryRequired = false
      ),
      resultSummary = _result_summary(record.body.result),
      calltree = None,
      result = None
    )
  }

  private def _status(value: DurableJobLifecycleStatus): JobStatus =
    value match {
      case DurableJobLifecycleStatus.Succeeded => JobStatus.Succeeded
      case DurableJobLifecycleStatus.Failed => JobStatus.Failed
      case DurableJobLifecycleStatus.Cancelled => JobStatus.Cancelled
      case _ => throw new IllegalStateException("non-terminal durable lifecycle reached terminal projection")
    }

  private def _result_summary(value: DurableResultOutcome): JobResultSummary =
    value match {
      case DurableResultOutcome.Succeeded(_) =>
        JobResultSummary(JobStatus.Succeeded, success = true, message = Some("ok"))
      case DurableResultOutcome.Failed(failure) =>
        JobResultSummary(JobStatus.Failed, success = false, message = Some(failure.summary))
      case DurableResultOutcome.Cancelled(summary) =>
        JobResultSummary(JobStatus.Cancelled, success = false, message = summary.map(_.summary))
      case DurableResultOutcome.Pending =>
        throw new IllegalStateException("pending durable result reached terminal projection")
    }

  private def _trace_tree(
    jobid: JobId,
    tasks: Vector[JobTaskReadModel],
    timeline: Vector[JobTimelineEvent]
  ): JobTraceTree = {
    val eventsbytask = tasks.map { task =>
      task.taskId -> timeline.filter(_.taskId.contains(task.taskId))
    }.toMap
    val childrenbyparent = tasks
      .groupBy(_.parentTaskId)
      .view
      .mapValues(_.sortBy(_.startedAt.toEpochMilli))
      .toMap
    val roots = childrenbyparent.getOrElse(None, Vector.empty)
    val nodes = scala.collection.mutable.Map.empty[TaskId, JobTraceTaskNode]
    var pending = roots.reverse.map(task => task -> false).toList
    while (pending.nonEmpty) {
      val (task, expanded) = pending.head
      pending = pending.tail
      if (expanded) {
        val children = childrenbyparent
          .getOrElse(Some(task.taskId), Vector.empty)
          .map(child => nodes(child.taskId))
        nodes.update(
          task.taskId,
          JobTraceTaskNode(
            taskId = task.taskId,
            parentTaskId = task.parentTaskId,
            taskKind = task.taskKind,
            relation = task.relation,
            status = task.status,
            transactionRole = task.transactionRole,
            transactionScope = task.transactionScope,
            transactionOutcome = task.transactionOutcome,
            compensationStatus = task.compensationStatus,
            recoveryRequired = false,
            events = eventsbytask.getOrElse(task.taskId, Vector.empty).sortBy(_.sequence),
            children = children
          )
        )
      } else {
        pending = (task -> true) :: pending
        childrenbyparent
          .getOrElse(Some(task.taskId), Vector.empty)
          .reverseIterator
          .foreach(child => pending = (child -> false) :: pending)
      }
    }
    JobTraceTree(jobid, roots.map(task => nodes(task.taskId)))
  }

  private val _empty_lineage = JobEventLineage(
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    AsyncFailureDisposition.NotApplicable
  )

  private def _collect[A, B](
    values: Vector[A]
  )(
    f: A => Consequence[B]
  ): Consequence[Vector[B]] =
    values.foldLeft(Consequence.success(Vector.empty[B])) { (acc, value) =>
      acc.flatMap(items => f(value).map(items :+ _))
    }
}
