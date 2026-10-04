package org.goldenport.cncf.job

import java.util.concurrent.ConcurrentHashMap

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext

/*
 * Closed lifecycle boundary for the durable writes admitted by JM69-03N and JM69-03O.
 * The source supplies closed evidence and access independently of the bridge;
 * the bridge does not derive durable facts from live execution objects.
 *
 * @since   Sep.  9, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] enum DurableJobLifecycleWriteBoundary {
  case Admission
  case StartIntent
  case RunningIntent
  case TaskStartIntent
  case TaskOutcomeCheckpoint
  case TerminalOutcomeCheckpoint
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
  case TerminalOutcomeCheckpoint(
    jobid: JobId,
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
    case DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(_, _) =>
      DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
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
  case TerminalOutcomeCheckpointRefused
}

private[job] final case class DurableJobLifecycleWriteFailureFact(
  jobid: JobId,
  failure: DurableJobLifecycleWriteFailure
)

/*
 * Engine-owned canonical admission, first-start, and task lifecycle checkpoint bridge.
 * Retains acknowledged signed snapshots and quota/access ownership handles.
 * Neither handle nor local counters substitute for persisted lifetime authority.
 */
private[job] final class DurableJobLifecycleWriteBridge(
  store: DurableJobStore,
  evidence: DurableJobLifecycleWriteEvidenceSource,
  lifetimepolicy: DurableJobLifetimeQuotaPolicy = DurableJobLifetimeQuotaPolicy.default
) {
  private final case class Entry(snapshot: DurableJobStoreSnapshot,
    binding: DurableJobLifetimeBinding, recovered: Option[DurableJobLifetimeRecovery.Prepared])
  private val _entries = new ConcurrentHashMap[JobId, Entry]()

  def hasAdmittedSnapshot(jobid: JobId): Boolean =
    _entries.containsKey(jobid)

  def lifetimeBinding(jobId: JobId): Option[DurableJobLifetimeBinding] =
    Option(_entries.get(jobId)).map(_.binding)

  def recoveryPolicy(policy: JobLifetimePolicy): DurableJobLifetimeQuotaPolicy =
    lifetimepolicy.copy(lifetime = policy)

  def ownsStore(selected: DurableJobStore): Boolean = store eq selected

  def adoptRecovery(jobId: JobId, prepared: DurableJobLifetimeRecovery.Prepared): Consequence[Unit] =
    if (prepared == null || jobId.value != prepared.snapshot.record.body.identity.jobId)
      DurableJobLifetimeQuotaPolicy.invalid
    else if (_entries.putIfAbsent(jobId, Entry(prepared.snapshot, prepared.native, Some(prepared))) == null)
      Consequence.unit
    else DurableJobLifetimeQuotaPolicy.unavailable

  def recoveredCancelled(record: JobRecord): Boolean =
    record.status == JobStatus.Cancelled && record.result.nonEmpty && record.taskReadModels.isEmpty &&
      Option(_entries.get(record.id)).exists(entry => entry.recovered.exists(_.quota.cancelclaimed) &&
        entry.snapshot.record.body.result == DurableResultOutcome.Pending &&
        (entry.snapshot.record.body.lifecycle.status == DurableJobLifecycleStatus.Submitted ||
          entry.snapshot.record.body.lifecycle.status == DurableJobLifecycleStatus.Running))

  def admit(record: JobRecord, policy: JobLifetimePolicy = lifetimepolicy.lifetime)
      (using ctx: ExecutionContext): Consequence[Unit] =
    _submitted(record).flatMap { _ =>
      _root_candidate(record, policy).flatMap { case (nativepolicy, taskcount, notecount) =>
        _evidence(record.id, DurableJobLifecycleWriteRequest.Admission, 1L).flatMap { supplied =>
          DurableJobProjection
            .projectV2(record, supplied.projection)
            .flatMap(DurableJobLifetimeAdmission.admit(
              store, _, supplied.access, taskcount, notecount, nativepolicy))
            .flatMap { admitted =>
              if (_entries.putIfAbsent(record.id, Entry(admitted.snapshot, admitted.binding, None)) == null)
                Consequence.unit
              else DurableJobLifetimeQuotaPolicy.unavailable
            }
        }
      }
    }

  private def _root_candidate(record: JobRecord, policy: JobLifetimePolicy)
      : Consequence[(DurableJobLifetimeQuotaPolicy, Int, Long)] =
    DurableJobLifetimeQuotaPolicy.pure {
      if (lifetimepolicy == null) DurableJobLifetimeQuotaPolicy.invalid
      else {
        val nativepolicy = lifetimepolicy.copy(lifetime = policy)
        for {
          _ <- nativepolicy.validate
          taskcount <- JobLifetimeBootstrap.taskCount(record.tasks, JobResourcePolicy.default)
          _ <- if (record.debug != null) Consequence.unit else JobResourcePolicy.refused
          notecount <- JobLifetimeBootstrap.noteCount(record.debug.executionNotes, policy)
          _ <- new JobLifetimeBudget(policy).prepareRoot(taskcount, notecount)
        } yield (nativepolicy, taskcount, notecount)
      }
    }

  def establishStartIntent(record: JobRecord)(using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobLifetimeQuotaPolicy.protect { _submitted(record).flatMap { _ =>
      _current_snapshot(record.id).flatMap { snapshot =>
        _start_basis(record, snapshot).flatMap { basis =>
          val candidate = _start_intent_candidate(basis)
          _evidence(record.id, DurableJobLifecycleWriteRequest.StartIntent, _next_semantic_revision(snapshot))
            .flatMap(_checkpoint(snapshot, candidate, _))
        }
      }
    }}

  def establishRunningIntent(record: JobRecord)(using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobLifetimeQuotaPolicy.protect { _submitted(record).flatMap { _ =>
      _revision_two_snapshot(record.id).flatMap { snapshot =>
        _recovery_basis(record, snapshot).map(_.getOrElse(_running_intent_basis(record, snapshot)))
          .flatMap { basis =>
            val candidate = _running_intent_candidate(basis)
            _evidence(record.id, DurableJobLifecycleWriteRequest.RunningIntent, _next_semantic_revision(snapshot))
              .flatMap(_checkpoint(snapshot, candidate, _))
        }
      }
    }}

  def establishTaskStart(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  )(using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobLifetimeQuotaPolicy.protect { _running(record).flatMap { _ =>
      _nonterminal_running_pending_snapshot(record.id).flatMap { snapshot =>
        _task_start_request(record, request).flatMap { taskstart =>
          _task_intent_basis(record, snapshot, taskstart.taskreadmodels).flatMap { basis =>
            val candidate = _task_start_intent_candidate(
              basis,
              taskstart.taskid,
              taskstart.parenttaskid
            )
            _evidence(record.id, taskstart, _next_semantic_revision(snapshot)).flatMap { supplied =>
              _checkpoint(snapshot, candidate, supplied)
            }
          }
        }
      }
    }}

  def establishTaskOutcome(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  )(using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobLifetimeQuotaPolicy.protect { _task_outcome_record(record).flatMap { _ =>
      _nonterminal_running_pending_snapshot(record.id).flatMap { snapshot =>
        _task_outcome_request(record, request).flatMap { taskoutcome =>
          _task_outcome_basis(record, snapshot, taskoutcome.taskreadmodels).flatMap { basis =>
            val candidate = _task_outcome_checkpoint_candidate(
              basis,
              taskoutcome.completedtaskid,
              taskoutcome.parenttaskid
            )
            _evidence(record.id, taskoutcome, _next_semantic_revision(snapshot)).flatMap { supplied =>
              _checkpoint(snapshot, candidate, supplied)
            }
          }
        }
      }
    }}

  def establishTerminalOutcome(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  )(using ctx: ExecutionContext): Consequence[Unit] =
    DurableJobLifetimeQuotaPolicy.protect { _terminal(record).flatMap { _ =>
      _terminal_snapshot(record).flatMap { snapshot =>
        _terminal_outcome_request(record, request).flatMap { terminal =>
          _terminal_intent_basis(record, snapshot, terminal.taskreadmodels).flatMap { basis =>
            val candidate = _terminal_outcome_checkpoint_candidate(basis)
            _evidence(record.id, terminal, _next_semantic_revision(snapshot)).flatMap { supplied =>
              _checkpoint(snapshot, candidate, supplied)
            }
          }
        }
      }
    }}

  private def _evidence(
    jobid: JobId,
    request: DurableJobLifecycleWriteRequest,
    expectedrevision: Long
  ): Consequence[DurableJobLifecycleWriteEvidence] =
    (evidence match {
      case source: DurableJobKeyedEvidenceSource => DurableJobKeyedEvidence.fetch(jobid, expectedrevision, request, source)
      case _ => evidence(request)
    }).flatMap { supplied =>
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

  private def _task_outcome_record(record: JobRecord): Consequence[Unit] =
    if (record == null)
      Consequence.argumentInvalid("durable task outcome record is missing")
    else if (record.persistence != JobPersistencePolicy.Persistent)
      Consequence.argumentInvalid("durable task outcome requires Persistent job admission")
    else if (
      (record.status == JobStatus.Running && record.result.isEmpty) ||
        (record.status == JobStatus.Cancelled && record.result.exists {
          case JobResult.Failure(_) => true
          case _ => false
        })
    )
      Consequence.unit
    else
      Consequence.stateInvalid(
        "durable task outcome requires a Running job without a result or a Cancelled job with a failure result"
      )

  private def _terminal(record: JobRecord): Consequence[Unit] =
    if (record == null)
      Consequence.argumentInvalid("durable terminal checkpoint record is missing")
    else if (record.persistence != JobPersistencePolicy.Persistent)
      Consequence.argumentInvalid("durable terminal checkpoint requires Persistent job admission")
    else if (!JobStatus.isTerminal(record.status) || record.result.isEmpty)
      Consequence.stateInvalid("durable terminal checkpoint requires a settled terminal result")
    else
      Consequence.unit

  private def _current_snapshot(jobid: JobId): Consequence[DurableJobStoreSnapshot] =
    Option(_entries.get(jobid)).map(entry => Consequence.success(entry.snapshot)).getOrElse(
      Consequence.stateInvalid("durable lifecycle write has no admitted snapshot")
    )

  private def _revision_two_snapshot(jobid: JobId): Consequence[DurableJobStoreSnapshot] =
    _current_snapshot(jobid).flatMap { snapshot =>
      if (
        snapshot.record.body.identity.revision == Option(_entries.get(jobid))
          .flatMap(_.recovered).map(value => Math.addExact(value.snapshot.record.body.identity.revision, 1L))
          .getOrElse(2L) &&
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
    Math.addExact(snapshot.record.body.identity.revision, 1L)

  private def _start_basis(record: JobRecord, snapshot: DurableJobStoreSnapshot): Consequence[JobRecord] = {
    val recovered = Option(_entries.get(record.id)).flatMap(_.recovered)
    val revision = recovered.map(_.snapshot.record.body.identity.revision).getOrElse(1L)
    if (snapshot.record.body.identity.revision != revision) DurableJobLifetimeQuotaPolicy.invalid
    else _recovery_basis(record, snapshot).map(_.getOrElse(record))
  }

  private def _recovery_basis(record: JobRecord, snapshot: DurableJobStoreSnapshot)
      : Consequence[Option[JobRecord]] =
    Option(_entries.get(record.id)).flatMap(_.recovered) match {
      case Some(prepared) => DurableJobRecoveryCheckpointProjection.basis(record, prepared.snapshot, snapshot).map(Some(_))
      case None => Consequence.success(None)
    }

  private def _checkpoint_timeline(record: JobRecord, snapshot: DurableJobStoreSnapshot,
    models: Vector[JobTaskReadModel]): Consequence[Vector[JobTimelineEvent]] =
    _recovery_basis(record, snapshot).flatMap {
      case Some(basis) => Consequence.success(basis.timeline)
      case None => _rehydrated_retained_timeline(snapshot, models)
    }

  private def _terminal_snapshot(record: JobRecord): Consequence[DurableJobStoreSnapshot] =
    if (recoveredCancelled(record)) _current_snapshot(record.id)
    else _nonterminal_running_pending_snapshot(record.id)

  private def _checkpoint(snapshot: DurableJobStoreSnapshot, candidate: JobRecord,
    supplied: DurableJobLifecycleWriteEvidence)(using ctx: ExecutionContext): Consequence[Unit] = {
    val entry = _entries.get(candidate.id)
    if (entry == null || entry.snapshot != snapshot) DurableJobLifetimeQuotaPolicy.unavailable
    else {
      val cancelled = recoveredCancelled(candidate)
      val projected = entry.recovered match {
        case Some(prepared) =>
          val confirmed = if (cancelled) prepared.native.quota.snapshot(candidate.id.value,
            prepared.native.access)(using prepared.context).flatMap(value =>
              if (value.cancelclaimed) Consequence.unit else DurableJobLifetimeQuotaPolicy.unavailable)
            else Consequence.unit
          confirmed.flatMap(_ => DurableJobRecoveryCheckpointProjection.project(candidate, supplied,
            prepared, snapshot, cancelled))
        case None => DurableJobProjection.projectV2(candidate, supplied.projection)
      }
      projected.flatMap(store.checkpoint(snapshot, _, supplied.access)).flatMap { acknowledged =>
        if (_entries.replace(candidate.id, entry, entry.copy(snapshot = acknowledged))) Consequence.unit
        else DurableJobLifetimeQuotaPolicy.unavailable
      }
    }
  }

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

  private def _terminal_outcome_request(
    record: JobRecord,
    request: DurableJobLifecycleWriteRequest
  ): Consequence[DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint] =
    request match {
      case terminal @ DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint(jobid, tasks) =>
        if (jobid != record.id)
          Consequence.argumentInvalid("durable terminal request job id diverges from the settled record")
        else if (tasks == null || tasks != record.taskReadModels)
          Consequence.argumentInvalid("durable terminal request tasks diverge from the settled record")
        else
          Consequence.success(terminal)
      case _ =>
        Consequence.argumentInvalid("durable terminal checkpoint requires a closed TerminalOutcomeCheckpoint request")
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
    snapshot: DurableJobStoreSnapshot,
    taskreadmodels: Vector[JobTaskReadModel]
  ): Consequence[JobRecord] =
    _checkpoint_timeline(record, snapshot, taskreadmodels).map { timeline =>
      record.copy(
        updatedAt = _maximum_updated_at(record.updatedAt, snapshot.record.body.identity.updatedAt),
        timeline = timeline
      )
    }

  private def _task_outcome_basis(
    record: JobRecord,
    snapshot: DurableJobStoreSnapshot,
    taskreadmodels: Vector[JobTaskReadModel]
  ): Consequence[JobRecord] =
    _checkpoint_timeline(record, snapshot, taskreadmodels).map { timeline =>
      record.copy(
        status = JobStatus.Running,
        result = None,
        updatedAt = _maximum_updated_at(record.updatedAt, snapshot.record.body.identity.updatedAt),
        timeline = timeline
      )
    }

  private def _terminal_intent_basis(
    record: JobRecord,
    snapshot: DurableJobStoreSnapshot,
    taskreadmodels: Vector[JobTaskReadModel]
  ): Consequence[JobRecord] =
    _checkpoint_timeline(record, snapshot, taskreadmodels).map { timeline =>
      record.copy(
        updatedAt = _maximum_updated_at(record.updatedAt, snapshot.record.body.identity.updatedAt),
        timeline = timeline
      )
    }

  private def _rehydrated_retained_timeline(
    snapshot: DurableJobStoreSnapshot,
    taskreadmodels: Vector[JobTaskReadModel]
  ): Consequence[Vector[JobTimelineEvent]] =
    snapshot.record.body.timeline.foldLeft(Consequence.success(Vector.empty[JobTimelineEvent])) {
      case (events, event) =>
        events.flatMap { timeline =>
          event.taskId match {
            case Some(taskid) =>
              taskreadmodels.find(_.taskId.value == taskid).map { task =>
                Consequence.success(timeline :+ JobTimelineEvent(
                  sequence = event.sequence,
                  occurredAt = event.occurredAt,
                  kind = event.kind,
                  taskId = Some(task.taskId),
                  parentTaskId = None,
                  note = event.summary
                ))
              }.getOrElse(
                Consequence.stateInvalid(
                  s"durable terminal timeline task id $taskid is absent from closed task read models"
                )
              )
            case None =>
              Consequence.success(timeline :+ JobTimelineEvent(
                sequence = event.sequence,
                occurredAt = event.occurredAt,
                kind = event.kind,
                taskId = None,
                parentTaskId = None,
                note = event.summary
              ))
          }
        }
    }

  private def _maximum_updated_at(left: java.time.Instant, right: java.time.Instant): java.time.Instant =
    if (left.isAfter(right)) left else right

  private def _start_intent_candidate(record: JobRecord): JobRecord = {
    val updatedat = record.updatedAt.plusMillis(1L)
    val sequence = Math.addExact(record.timeline.map(_.sequence).foldLeft(0L)(math.max), 1L)
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
    val sequence = Math.addExact(record.timeline.map(_.sequence).foldLeft(0L)(math.max), 1L)
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
    val sequence = Math.addExact(record.timeline.map(_.sequence).foldLeft(0L)(math.max), 1L)
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
    val sequence = Math.addExact(record.timeline.map(_.sequence).foldLeft(0L)(math.max), 1L)
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

  private def _terminal_outcome_checkpoint_candidate(record: JobRecord): JobRecord = {
    val updatedat = record.updatedAt.plusMillis(1L)
    val sequence = Math.addExact(record.timeline.map(_.sequence).foldLeft(0L)(math.max), 1L)
    record.copy(
      updatedAt = updatedat,
      timeline = record.timeline :+ JobTimelineEvent(
        sequence = sequence,
        occurredAt = updatedat,
        kind = "job.durable-terminal-outcome-checkpoint",
        taskId = None,
        parentTaskId = None,
        note = None
      )
    )
  }
}
