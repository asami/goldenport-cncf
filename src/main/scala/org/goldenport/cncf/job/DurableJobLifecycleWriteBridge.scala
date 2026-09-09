package org.goldenport.cncf.job

import java.util.concurrent.ConcurrentHashMap

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Closed lifecycle boundary for the only durable writes admitted by JM69-03L.
 * The source supplies closed evidence and access independently of the bridge;
 * the bridge does not derive durable facts from live execution objects.
 *
 * @since   Sep.  9, 2026
 * @version Sep. 10, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobLifecycleWriteBoundary {
  case Admission
  case StartIntent
  case RunningIntent
  case TaskStartIntent
  case TaskOutcomeCheckpoint
}

/*
 * The evidence source receives only an engine-constructed closed request.
 * The first three boundaries have no task execution data; TaskStartIntent
 * carries only the registered read-model values needed by V2.
 */
private[job] enum DurableJobLifecycleWriteRequest {
  case Admission
  case StartIntent
  case RunningIntent
  case TaskStartIntent(
    jobid: JobId,
    taskid: TaskId,
    parenttaskid: Option[TaskId],
    taskreadmodels: Vector[JobTaskReadModel]
  )
  case TaskOutcomeCheckpoint(
    jobid: JobId,
    completedtaskid: TaskId,
    parenttaskid: Option[TaskId],
    taskreadmodels: Vector[JobTaskReadModel]
  )

  def boundary: DurableJobLifecycleWriteBoundary = this match {
    case DurableJobLifecycleWriteRequest.Admission => DurableJobLifecycleWriteBoundary.Admission
    case DurableJobLifecycleWriteRequest.StartIntent => DurableJobLifecycleWriteBoundary.StartIntent
    case DurableJobLifecycleWriteRequest.RunningIntent => DurableJobLifecycleWriteBoundary.RunningIntent
    case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, _) =>
      DurableJobLifecycleWriteBoundary.TaskStartIntent
    case DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(_, _, _, _) =>
      DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint
  }
}

/* The evidence/access pair is closed and has no provider or live execution value. */
private[job] final case class DurableJobLifecycleWriteEvidence(
  projection: DurableJobProjectionEvidence,
  access: DurableJobRecordAccess
)

/*
 * A configured engine supplies exactly one closed evidence/access pair per
 * admitted request.  It deliberately receives no JobRecord, JobTask, or
 * ExecutionContext from the bridge.
 */
private[job] type DurableJobLifecycleWriteEvidenceSource =
  DurableJobLifecycleWriteRequest => Consequence[DurableJobLifecycleWriteEvidence]

/* Package-only observation; it deliberately omits provider and failure detail. */
private[job] enum DurableJobLifecycleWriteFailure {
  case AdmissionRefused
  case StartIntentRefused
  case RunningIntentRefused
  case TaskStartIntentRefused
  case TaskOutcomeCheckpointRefused
}

private[job] final case class DurableJobLifecycleWriteFailureFact(
  jobid: JobId,
  failure: DurableJobLifecycleWriteFailure
)

/*
 * Engine-owned canonical admission, first-start, and task lifecycle checkpoint bridge.
 * Its only per-job retained state is the current store snapshot returned by a
 * successful create or checkpoint.
 */
private[job] final class DurableJobLifecycleWriteBridge(
  store: DurableJobStore,
  evidence: DurableJobLifecycleWriteEvidenceSource
) {
  private val _snapshots = new ConcurrentHashMap[JobId, DurableJobStoreSnapshot]()

  def hasAdmittedSnapshot(jobid: JobId): Boolean =
    _snapshots.containsKey(jobid)

  def admit(record: JobRecord)(using ctx: ExecutionContext): Consequence[Unit] =
    _submitted(record).flatMap { _ =>
      _evidence(DurableJobLifecycleWriteRequest.Admission, 1L).flatMap { supplied =>
        DurableJobProjection
          .projectV2(record, supplied.projection)
          .flatMap(store.create(_, supplied.access))
          .map { snapshot =>
            _snapshots.put(record.id, snapshot)
            ()
          }
      }
    }

  def establishStartIntent(record: JobRecord)(using ctx: ExecutionContext): Consequence[Unit] =
    _submitted(record).flatMap { _ =>
      _current_snapshot(record.id).flatMap { snapshot =>
        val candidate = _start_intent_candidate(record)
        _evidence(DurableJobLifecycleWriteRequest.StartIntent, 2L).flatMap { supplied =>
          DurableJobProjection
            .projectV2(candidate, supplied.projection)
            .flatMap(store.checkpoint(snapshot, _, supplied.access))
            .map { checkpointed =>
              _snapshots.put(record.id, checkpointed)
              ()
            }
        }
      }
    }

  def establishRunningIntent(record: JobRecord)(using ctx: ExecutionContext): Consequence[Unit] =
    _submitted(record).flatMap { _ =>
      _revision_two_snapshot(record.id).flatMap { snapshot =>
        val candidate = _running_intent_candidate(_running_intent_basis(record, snapshot))
        _evidence(DurableJobLifecycleWriteRequest.RunningIntent, 3L).flatMap { supplied =>
          DurableJobProjection
            .projectV2(candidate, supplied.projection)
            .flatMap(store.checkpoint(snapshot, _, supplied.access))
            .map { checkpointed =>
              _snapshots.put(record.id, checkpointed)
              ()
            }
        }
      }
    }

  def establishTaskStart(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _running(record).flatMap { _ =>
      _nonterminal_running_pending_snapshot(record.id).flatMap { snapshot =>
        _task_start_request(record, request).flatMap { taskstart =>
          val candidate = _task_start_intent_candidate(
            _task_intent_basis(record, snapshot),
            taskstart.taskid,
            taskstart.parenttaskid
          )
          _evidence(taskstart, _next_semantic_revision(snapshot)).flatMap { supplied =>
            DurableJobProjection
              .projectV2(candidate, supplied.projection)
              .flatMap(store.checkpoint(snapshot, _, supplied.access))
              .map { checkpointed =>
                _snapshots.put(record.id, checkpointed)
                ()
              }
          }
        }
      }
    }

  def establishTaskOutcome(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  )(using ctx: ExecutionContext): Consequence[Unit] =
    _running(record).flatMap { _ =>
      _nonterminal_running_pending_snapshot(record.id).flatMap { snapshot =>
        _task_outcome_request(record, request).flatMap { taskoutcome =>
          val candidate = _task_outcome_checkpoint_candidate(
            _task_intent_basis(record, snapshot),
            taskoutcome.completedtaskid,
            taskoutcome.parenttaskid
          )
          _evidence(taskoutcome, _next_semantic_revision(snapshot)).flatMap { supplied =>
            DurableJobProjection
              .projectV2(candidate, supplied.projection)
              .flatMap(store.checkpoint(snapshot, _, supplied.access))
              .map { checkpointed =>
                _snapshots.put(record.id, checkpointed)
                ()
              }
          }
        }
      }
    }

  private def _evidence(
    request: DurableJobLifecycleWriteRequest,
    expectedrevision: Long
  ): Consequence[DurableJobLifecycleWriteEvidence] =
    evidence(request).flatMap { supplied =>
      if (supplied == null || supplied.projection == null || supplied.access == null)
        Consequence.argumentInvalid("durable lifecycle write evidence is missing")
      else if (supplied.projection.semanticRevision != expectedrevision)
        Consequence.argumentInvalid(
          s"durable ${request.boundary.toString} evidence must use semantic revision $expectedrevision"
        )
      else if (
        (request.boundary == DurableJobLifecycleWriteBoundary.TaskStartIntent ||
          request.boundary == DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint) &&
          supplied.projection.result != DurableResultOutcome.Pending
      )
        Consequence.argumentInvalid(
          s"durable ${request.boundary.toString} evidence must retain a Pending job result"
        )
      else
        Consequence.success(supplied)
    }

  private def _submitted(record: JobRecord): Consequence[Unit] =
    if (record == null)
      Consequence.argumentInvalid("durable lifecycle write record is missing")
    else if (record.persistence != JobPersistencePolicy.Persistent)
      Consequence.argumentInvalid("durable lifecycle write requires Persistent job admission")
    else if (record.status != JobStatus.Submitted || record.result.nonEmpty)
      Consequence.stateInvalid("durable lifecycle write requires a Submitted job without a result")
    else
      Consequence.unit

  private def _running(record: JobRecord): Consequence[Unit] =
    if (record == null)
      Consequence.argumentInvalid("durable task-start record is missing")
    else if (record.persistence != JobPersistencePolicy.Persistent)
      Consequence.argumentInvalid("durable task-start requires Persistent job admission")
    else if (record.status != JobStatus.Running || record.result.nonEmpty)
      Consequence.stateInvalid("durable task-start requires a Running job without a result")
    else
      Consequence.unit

  private def _current_snapshot(jobid: JobId): Consequence[DurableJobStoreSnapshot] =
    Option(_snapshots.get(jobid)).map(Consequence.success).getOrElse(
      Consequence.stateInvalid("durable lifecycle write has no admitted snapshot")
    )

  private def _revision_two_snapshot(jobid: JobId): Consequence[DurableJobStoreSnapshot] =
    _current_snapshot(jobid).flatMap { snapshot =>
      if (
        snapshot.record.body.identity.revision == 2L &&
          snapshot.record.body.lifecycle.status == DurableJobLifecycleStatus.Submitted
      )
        Consequence.success(snapshot)
      else
        Consequence.stateInvalid(
          "durable lifecycle RunningIntent requires the admitted revision-2 Submitted snapshot"
      )
    }

  private def _nonterminal_running_pending_snapshot(
    jobid: JobId
  ): Consequence[DurableJobStoreSnapshot] =
    _current_snapshot(jobid).flatMap { snapshot =>
      if (
        snapshot.record.body.identity.revision >= 3L &&
        snapshot.record.body.lifecycle.status == DurableJobLifecycleStatus.Running &&
        snapshot.record.body.result == DurableResultOutcome.Pending
      )
        Consequence.success(snapshot)
      else
        Consequence.stateInvalid(
          "durable task checkpoint requires a nonterminal Running/Pending snapshot"
        )
    }

  private def _next_semantic_revision(snapshot: DurableJobStoreSnapshot): Long =
    snapshot.record.body.identity.revision + 1L

  private def _task_start_request(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  ): Consequence[DurableJobLifecycleWriteRequest.TaskStartIntent] =
    request match {
      case taskstart @ DurableJobLifecycleWriteRequest.TaskStartIntent(
            jobid,
            taskid,
            parent,
            tasks
          ) =>
        if (jobid != record.id)
          Consequence.argumentInvalid("durable task-start request job id diverges from the live record")
        else if (tasks == null || tasks != record.taskReadModels)
          Consequence.argumentInvalid("durable task-start request tasks diverge from the live record")
        else
          tasks.find(_.taskId == taskid) match {
            case Some(current)
                if current.status == JobTaskStatus.Running && current.parentTaskId == parent =>
              if (record.timeline.exists(event =>
                event.kind == "task.running" &&
                  event.taskId.contains(taskid) &&
                  event.parentTaskId == parent
              ))
                Consequence.success(taskstart)
              else
                Consequence.stateInvalid("durable task-start requires a locally registered Running task")
            case _ =>
              Consequence.argumentInvalid("durable task-start request lacks the live Running task")
          }
      case _ =>
        Consequence.argumentInvalid("durable task-start requires a closed TaskStartIntent request")
    }

  private def _task_outcome_request(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  ): Consequence[DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint] =
    request match {
      case taskoutcome @ DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint(
            jobid,
            taskid,
            parent,
            tasks
          ) =>
        if (jobid != record.id)
          Consequence.argumentInvalid("durable task outcome request job id diverges from the live record")
        else if (tasks == null || tasks != record.taskReadModels)
          Consequence.argumentInvalid("durable task outcome request tasks diverge from the live record")
        else
          tasks.find(_.taskId == taskid) match {
            case Some(current)
                if current.parentTaskId == parent &&
                  current.finishedAt.nonEmpty &&
                  current.status == JobTaskStatus.Succeeded &&
                  current.transactionOutcome.contains(JobTaskTransactionOutcome.Committed.print) =>
              _closed_task_outcome_registered(
                record,
                taskid,
                parent,
                "task.succeeded",
                "task.transaction.committed",
                taskoutcome
              )
            case Some(current)
                if current.parentTaskId == parent &&
                  current.finishedAt.nonEmpty &&
                  current.status == JobTaskStatus.Failed &&
                  current.transactionOutcome.contains(JobTaskTransactionOutcome.Failed.print) =>
              _closed_task_outcome_registered(
                record,
                taskid,
                parent,
                "task.failed",
                "task.transaction.failed",
                taskoutcome
              )
            case _ =>
              Consequence.argumentInvalid(
                "durable task outcome request lacks the locally closed completed task"
              )
          }
      case _ =>
        Consequence.argumentInvalid("durable task outcome requires a closed TaskOutcomeCheckpoint request")
    }

  private def _closed_task_outcome_registered(
    record: JobRecord,
    taskid: TaskId,
    parent: Option[TaskId],
    completionkind: String,
    transactionkind: String,
    request: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint
  ): Consequence[DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint] =
    val registered = record.timeline.exists(event =>
      event.kind == completionkind &&
        event.taskId.contains(taskid) &&
        event.parentTaskId == parent
    ) && record.timeline.exists(event =>
      event.kind == transactionkind &&
        event.taskId.contains(taskid) &&
        event.parentTaskId == parent
    )
    if (registered)
      Consequence.success(request)
    else
      Consequence.stateInvalid("durable task outcome requires local completion and transaction registration")

  private def _running_intent_basis(
    record: JobRecord,
    snapshot: DurableJobStoreSnapshot
  ): JobRecord = {
    record.copy(
      updatedAt = snapshot.record.body.identity.updatedAt,
      timeline = snapshot.record.body.timeline.map { event =>
        JobTimelineEvent(
          sequence = event.sequence,
          occurredAt = event.occurredAt,
          kind = event.kind,
          taskId = None,
          parentTaskId = None,
          note = event.summary
        )
      }
    )
  }

  private def _task_intent_basis(
    record: JobRecord,
    snapshot: DurableJobStoreSnapshot
  ): JobRecord =
    _running_intent_basis(record, snapshot).copy(
      updatedAt = _maximum_updated_at(record.updatedAt, snapshot.record.body.identity.updatedAt)
    )

  private def _maximum_updated_at(left: java.time.Instant, right: java.time.Instant): java.time.Instant =
    if (left.isAfter(right)) left else right

  private def _start_intent_candidate(record: JobRecord): JobRecord = {
    val updatedat = record.updatedAt.plusMillis(1L)
    val sequence = record.timeline.map(_.sequence).foldLeft(0L)(math.max) + 1L
    record.copy(
      updatedAt = updatedat,
      timeline = record.timeline :+ JobTimelineEvent(
        sequence = sequence,
        occurredAt = updatedat,
        kind = "job.durable-start-intent",
        taskId = None,
        parentTaskId = None,
        note = None
      )
    )
  }

  private def _running_intent_candidate(record: JobRecord): JobRecord = {
    val updatedat = record.updatedAt.plusMillis(1L)
    val sequence = record.timeline.map(_.sequence).foldLeft(0L)(math.max) + 1L
    record.copy(
      status = JobStatus.Running,
      updatedAt = updatedat,
      timeline = record.timeline :+ JobTimelineEvent(
        sequence = sequence,
        occurredAt = updatedat,
        kind = "job.durable-running-intent",
        taskId = None,
        parentTaskId = None,
        note = None
      )
    )
  }

  private def _task_start_intent_candidate(
    record: JobRecord,
    taskid: TaskId,
    parent: Option[TaskId]
  ): JobRecord = {
    val updatedat = record.updatedAt.plusMillis(1L)
    val sequence = record.timeline.map(_.sequence).foldLeft(0L)(math.max) + 1L
    record.copy(
      updatedAt = updatedat,
      timeline = record.timeline :+ JobTimelineEvent(
        sequence = sequence,
        occurredAt = updatedat,
        kind = "task.durable-start-intent",
        taskId = Some(taskid),
        parentTaskId = parent,
        note = None
      )
    )
  }

  private def _task_outcome_checkpoint_candidate(
    record: JobRecord,
    taskid: TaskId,
    parent: Option[TaskId]
  ): JobRecord = {
    val updatedat = record.updatedAt.plusMillis(1L)
    val sequence = record.timeline.map(_.sequence).foldLeft(0L)(math.max) + 1L
    record.copy(
      updatedAt = updatedat,
      timeline = record.timeline :+ JobTimelineEvent(
        sequence = sequence,
        occurredAt = updatedat,
        kind = "task.durable-outcome-checkpoint",
        taskId = Some(taskid),
        parentTaskId = parent,
        note = None
      )
    )
  }
}
