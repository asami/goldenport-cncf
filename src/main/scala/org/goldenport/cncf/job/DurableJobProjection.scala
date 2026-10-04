package org.goldenport.cncf.job

import java.time.Instant

import org.goldenport.Consequence

/*
 * Closed evidence supplied by a durable writer.  The evidence deliberately
 * contains values rather than a JobTask, Action, ExecutionContext, provider,
 * or any other live execution object.
 *
 * @since   Sep.  9, 2026
 *  version Sep. 28, 2026
 * @version Oct. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] final case class DurableJobProjectionEvidence(
  semanticRevision: Long,
  authorization: DurableJobAuthorization,
  tasks: Vector[DurableTaskDescriptor],
  inputs: Vector[DurableInputReference],
  result: DurableResultOutcome,
  retry: DurableRetryEvidence,
  diagnostics: Vector[DurableDiagnosticSummary],
  calltreeReference: Option[DurableExternalReference],
  definitionSnapshot: DurableDefinitionSnapshot,
  retention: DurableRetentionState
)

private[job] enum DurableReplayEvidenceCategory(val wire: String) {
  case Idempotency extends DurableReplayEvidenceCategory("idempotency")
  case Input extends DurableReplayEvidenceCategory("input")
  case Definition extends DurableReplayEvidenceCategory("definition")
  case Authorization extends DurableReplayEvidenceCategory("authorization")
  case Provider extends DurableReplayEvidenceCategory("provider")
  case Compatibility extends DurableReplayEvidenceCategory("compatibility")
}

/*
 * Each member is an opaque, independently supplied proof token.  A token is
 * not resolved here and does not select a provider or execute a replay.
 */
private[job] final case class DurableReplayEvidence(
  idempotency: Option[String] = None,
  input: Option[String] = None,
  definition: Option[String] = None,
  authorization: Option[String] = None,
  provider: Option[String] = None,
  compatibility: Option[String] = None
)

private[job] enum DurableReplayDecision {
  case Allowed
  case Refused(
    failedCategories: Vector[DurableReplayEvidenceCategory],
    reasons: Vector[String]
  )

  def failedCategoryNames: Vector[String] = this match {
    case DurableReplayDecision.Allowed => Vector.empty
    case DurableReplayDecision.Refused(categories, _) => categories.map(_.wire)
  }

  def message: String = this match {
    case DurableReplayDecision.Allowed => "replay assessment allowed"
    case DurableReplayDecision.Refused(categories, _) =>
      s"replay refused; failed evidence categories: ${categories.map(_.wire).mkString(",")}"
  }
}

private[job] object DurableReplayAssessment {
  private val _categories = Vector(
    DurableReplayEvidenceCategory.Idempotency,
    DurableReplayEvidenceCategory.Input,
    DurableReplayEvidenceCategory.Definition,
    DurableReplayEvidenceCategory.Authorization,
    DurableReplayEvidenceCategory.Provider,
    DurableReplayEvidenceCategory.Compatibility
  )

  def assess(evidence: DurableReplayEvidence): DurableReplayDecision = {
    val values = Vector(
      DurableReplayEvidenceCategory.Idempotency -> evidence.idempotency,
      DurableReplayEvidenceCategory.Input -> evidence.input,
      DurableReplayEvidenceCategory.Definition -> evidence.definition,
      DurableReplayEvidenceCategory.Authorization -> evidence.authorization,
      DurableReplayEvidenceCategory.Provider -> evidence.provider,
      DurableReplayEvidenceCategory.Compatibility -> evidence.compatibility
    )
    val failures = values.flatMap { case (category, value) =>
      value match {
        case None => Some(category -> "missing")
        case Some(null) => Some(category -> "missing")
        case Some(text) if text.trim.isEmpty => Some(category -> "blank")
        case Some(_) => None
      }
    }
    if (failures.isEmpty)
      DurableReplayDecision.Allowed
    else
      DurableReplayDecision.Refused(
        failures.map(_._1),
        failures.map { case (category, reason) => s"${category.wire}:$reason" }
      )
  }

  def categories: Vector[DurableReplayEvidenceCategory] = _categories
}

private[job] object DurableJobProjection {
  def project(
    record: JobRecord,
    evidence: DurableJobProjectionEvidence
  ): Consequence[DurableJobRecord] =
    _body(record, evidence) match {
      case Left(message) => Consequence.argumentInvalid(s"durable projection refused: $message")
      case Right(body) => DurableJobRecord.create(body)
    }

  def projectV2(
    record: JobRecord,
    evidence: DurableJobProjectionEvidence
  ): Consequence[DurableJobRecord] =
    _body_v2(record, evidence) match {
      case Left(message) => Consequence.argumentInvalid(s"durable v2 projection refused: $message")
      case Right(body) => DurableJobRecord.createV2(body)
    }

  def assessReplay(evidence: DurableReplayEvidence): DurableReplayDecision =
    DurableReplayAssessment.assess(evidence)

  private def _body(
    record: JobRecord,
    evidence: DurableJobProjectionEvidence
  ): Either[String, DurableJobRecordBody] =
    for {
      _ <- _persistent(record)
      _ <- _terminal(record.status)
      _ <- _live_result_shape(record)
      _ <- _positive(evidence.semanticRevision, "semantic revision")
      status <- _status(record.status)
      runmode <- _run_mode(record.runMode)
      tasks <- DurableJobTaskProjection.project(record.taskReadModels, evidence.tasks, record.status)
      schedule <- _schedule(record, record.taskReadModels)
      timeline <- _timeline(record.timeline, record.createdAt, record.updatedAt, tasks)
      _ <- _retry(record.retry, evidence.retry, record.status)
      _ <- _definition(record.debug, evidence.definitionSnapshot)
      _ <- _inputs(record.input, evidence.inputs)
      _ <- _result(record, evidence.result)
    } yield DurableJobRecordBody(
      identity = DurableJobIdentity(
        record.id.value,
        evidence.semanticRevision,
        record.createdAt,
        record.updatedAt
      ),
      authorization = evidence.authorization,
      lifecycle = DurableJobLifecycle(
        status,
        record.priority,
        runmode,
        evidence.retry,
        schedule
      ),
      tasks = tasks,
      inputs = evidence.inputs,
      result = evidence.result,
      timeline = timeline,
      diagnostics = evidence.diagnostics,
      calltreeReference = evidence.calltreeReference,
      definitionSnapshot = evidence.definitionSnapshot,
      retention = evidence.retention
    )

  private def _body_v2(
    record: JobRecord,
    evidence: DurableJobProjectionEvidence
  ): Either[String, DurableJobRecordBody] =
    for {
      _ <- _persistent(record)
      _ <- _live_result_shape_v2(record)
      _ <- _positive(evidence.semanticRevision, "semantic revision")
      status <- _status(record.status)
      runmode <- _run_mode(record.runMode)
      tasks <- DurableJobTaskProjection.projectV2(
        record.taskReadModels,
        evidence.tasks,
        record.status,
        record.retry,
        record.timeline
      )
      schedule <- if (JobStatus.isTerminal(record.status))
        _schedule(record, record.taskReadModels)
      else
        _schedule_v2(record, record.taskReadModels)
      timeline <- _timeline(record.timeline, record.createdAt, record.updatedAt, tasks)
      _ <- _retry_v2(record.retry, evidence.retry, record.status)
      _ <- _definition(record.debug, evidence.definitionSnapshot)
      _ <- _inputs(record.input, evidence.inputs)
      _ <- _result_v2(record, evidence.result)
    } yield DurableJobRecordBody(
      identity = DurableJobIdentity(
        record.id.value,
        evidence.semanticRevision,
        record.createdAt,
        record.updatedAt
      ),
      authorization = evidence.authorization,
      lifecycle = DurableJobLifecycle(
        status,
        record.priority,
        runmode,
        evidence.retry,
        schedule
      ),
      tasks = tasks,
      inputs = evidence.inputs,
      result = evidence.result,
      timeline = timeline,
      diagnostics = evidence.diagnostics,
      calltreeReference = evidence.calltreeReference,
      definitionSnapshot = evidence.definitionSnapshot,
      retention = evidence.retention
    )

  private def _persistent(record: JobRecord): Either[String, Unit] =
    Either.cond(
      record.persistence == JobPersistencePolicy.Persistent,
      (),
      "Ephemeral JobRecord cannot be projected as durable"
    )

  private def _terminal(status: JobStatus): Either[String, Unit] =
    Either.cond(JobStatus.isTerminal(status), (), "durable projection requires a terminal JobStatus")

  private def _live_result_shape(record: JobRecord): Either[String, Unit] =
    record.status match {
      case JobStatus.Succeeded =>
        Either.cond(
          record.result.exists {
            case JobResult.Success(_) => true
            case JobResult.Failure(_) => false
          },
          (),
          "succeeded JobRecord must have a live success result"
        )
      case JobStatus.Failed =>
        Either.cond(
          record.result.exists {
            case JobResult.Success(_) => false
            case JobResult.Failure(_) => true
          },
          (),
          "failed JobRecord must have a live failure result"
        )
      case JobStatus.Cancelled =>
        Either.cond(
          record.result.exists {
            case JobResult.Success(_) => false
            case JobResult.Failure(_) => true
          },
          (),
          "cancelled JobRecord must have a live cancellation failure result"
        )
      case _ => Left("durable projection requires a terminal JobStatus")
    }

  private def _live_result_shape_v2(record: JobRecord): Either[String, Unit] =
    record.status match {
      case JobStatus.Submitted | JobStatus.Running | JobStatus.Suspended =>
        Either.cond(
          record.result.isEmpty,
          (),
          "non-terminal JobRecord must not have a live result"
        )
      case JobStatus.Succeeded =>
        Either.cond(
          record.result.exists {
            case JobResult.Success(_) => true
            case JobResult.Failure(_) => false
          },
          (),
          "succeeded JobRecord must have a live success result"
        )
      case JobStatus.Failed | JobStatus.Cancelled =>
        Either.cond(
          record.result.exists {
            case JobResult.Success(_) => false
            case JobResult.Failure(_) => true
          },
          (),
          "failed or cancelled JobRecord must have a live failure result"
        )
      case null => Left(s"unknown JobStatus: ${record.status}")
    }

  private def _status(status: JobStatus): Either[String, DurableJobLifecycleStatus] =
    status match {
      case JobStatus.Submitted => Right(DurableJobLifecycleStatus.Submitted)
      case JobStatus.Running => Right(DurableJobLifecycleStatus.Running)
      case JobStatus.Suspended => Right(DurableJobLifecycleStatus.Suspended)
      case JobStatus.Succeeded => Right(DurableJobLifecycleStatus.Succeeded)
      case JobStatus.Failed => Right(DurableJobLifecycleStatus.Failed)
      case JobStatus.Cancelled => Right(DurableJobLifecycleStatus.Cancelled)
      case null => Left(s"unknown JobStatus: $status")
    }

  private def _run_mode(mode: JobRunMode): Either[String, DurableRunMode] =
    mode match {
      case JobRunMode.Async => Right(DurableRunMode.Async)
      case JobRunMode.Sync => Right(DurableRunMode.Sync)
      case null => Left(s"unknown JobRunMode: $mode")
    }

  private def _schedule(
    record: JobRecord,
    models: Vector[JobTaskReadModel]
  ): Either[String, DurableScheduleState] = {
    val starts = models.map(_.startedAt)
    val finishes = models.flatMap(_.finishedAt)
    for {
      _ <- Either.cond(record.scheduledStartAt.forall(!_.isBefore(record.createdAt)), (), "scheduled start precedes creation")
      _ <- Either.cond(starts.forall(!_.isBefore(record.createdAt)), (), "task start precedes job creation")
      _ <- Either.cond(finishes.forall(!_.isBefore(record.createdAt)), (), "task completion precedes job creation")
      started <- starts.sortWith((left, right) => left.isBefore(right)).headOption.toRight("durable projection requires a task start")
      completed <- finishes.sortWith((left, right) => left.isBefore(right)).lastOption.toRight("durable projection requires task completion evidence")
      _ <- Either.cond(!completed.isBefore(started), (), "task completion precedes task start")
      _ <- Either.cond(!completed.isAfter(record.updatedAt), (), "task completion follows job update")
      _ <- Either.cond(record.scheduledStartAt.forall(!started.isBefore(_)), (), "task start precedes scheduled start")
    } yield DurableScheduleState(record.scheduledStartAt, Some(started), Some(completed))
  }

  private def _schedule_v2(
    record: JobRecord,
    models: Vector[JobTaskReadModel]
  ): Either[String, DurableScheduleState] = {
    val starts = models.map(_.startedAt)
    val finishes = models.flatMap(_.finishedAt)
    val started = starts.sortWith((left, right) => left.isBefore(right)).headOption
    val completed = finishes.sortWith((left, right) => left.isBefore(right)).lastOption
    for {
      _ <- Either.cond(record.scheduledStartAt.forall(!_.isBefore(record.createdAt)), (), "scheduled start precedes creation")
      _ <- Either.cond(starts.forall(!_.isBefore(record.createdAt)), (), "task start precedes job creation")
      _ <- Either.cond(finishes.forall(!_.isBefore(record.createdAt)), (), "task completion precedes job creation")
      _ <- Either.cond(completed.forall(!_.isAfter(record.updatedAt)), (), "task completion follows job update")
      _ <- Either.cond(started.forall(start => record.scheduledStartAt.forall(!start.isBefore(_))), (), "task start precedes scheduled start")
      _ <- Either.cond(completed.forall(end => started.forall(!end.isBefore(_))), (), "task completion precedes task start")
    } yield DurableScheduleState(record.scheduledStartAt, started, completed)
  }

  private def _timeline(
    events: Vector[JobTimelineEvent],
    createdat: Instant,
    updatedat: Instant,
    tasks: Vector[DurableTaskDescriptor]
  ): Either[String, Vector[DurableTimelineEvent]] = {
    val taskids = tasks.map(_.taskId).toSet
    val result = events.map { event =>
      for {
        _ <- _timeline_kind(event.kind, event.sequence)
        _ <- Either.cond(event.occurredAt.compareTo(createdat) >= 0, (), s"timeline event ${event.sequence} precedes job creation")
        _ <- Either.cond(event.occurredAt.compareTo(updatedat) <= 0, (), s"timeline event ${event.sequence} follows job update")
        _ <- Either.cond(event.taskId.forall(id => taskids.contains(id.value)), (), s"timeline event ${event.sequence} references an unknown task")
        _ <- Either.cond(event.parentTaskId.forall(id => taskids.contains(id.value)), (), s"timeline event ${event.sequence} references an unknown parent task")
        mapped = DurableTimelineEvent(event.sequence, event.occurredAt, event.kind, event.taskId.map(_.value), event.note)
      } yield mapped
    }
    _sequence(result)
  }

  private def _timeline_kind(kind: String, sequence: Long): Either[String, Unit] =
    Either.cond(
      kind.trim.nonEmpty && (
        kind.startsWith("job.") ||
          kind.startsWith("task.") ||
          kind.startsWith("event.") ||
          kind.startsWith("command.") ||
          kind.startsWith("scheduler.")
      ),
      (),
      s"unknown timeline event kind at sequence $sequence: $kind"
    )

  private def _retry(
    live: JobRetryState,
    supplied: DurableRetryEvidence,
    status: JobStatus
  ): Either[String, Unit] =
    for {
      _ <- Either.cond(live.maxAttempts == supplied.maxAttempts, (), "retry max-attempts evidence diverges from the live record")
      _ <- Either.cond(live.nextRetryDueAt == supplied.nextRetryAt, (), "retry next-due evidence diverges from the live record")
      _ <- Either.cond(live.exhausted == supplied.exhausted, (), "retry exhaustion evidence diverges from the live record")
      _ <- Either.cond(live.recoveryRequired == supplied.recoveryRequired, (), "retry recovery evidence diverges from the live record")
      _ <- Either.cond(supplied.nextRetryAt.isEmpty || !JobStatus.isTerminal(status), (), "terminal retry evidence must not schedule another retry")
      _ <- Either.cond(
        supplied.attempts.nonEmpty,
        (),
        "durable projection requires explicit retry attempt evidence"
      )
      _ <- Either.cond(
        status match {
          case JobStatus.Succeeded => supplied.attempts.last.outcome == DurableAttemptOutcome.Succeeded
          case JobStatus.Failed => supplied.attempts.last.outcome == DurableAttemptOutcome.Failed
          case JobStatus.Cancelled => supplied.attempts.last.outcome == DurableAttemptOutcome.Cancelled
          case _ => false
        },
        (),
        "terminal retry attempt outcome diverges from JobStatus"
      )
    } yield ()

  private def _retry_v2(
    live: JobRetryState,
    supplied: DurableRetryEvidence,
    status: JobStatus
  ): Either[String, Unit] =
    if (JobStatus.isTerminal(status)) {
      _retry(live, supplied, status)
    } else {
      for {
        _ <- Either.cond(live.maxAttempts == supplied.maxAttempts, (), "retry max-attempts evidence diverges from the live record")
        _ <- Either.cond(live.nextRetryDueAt == supplied.nextRetryAt, (), "retry next-due evidence diverges from the live record")
        _ <- Either.cond(live.exhausted == supplied.exhausted, (), "retry exhaustion evidence diverges from the live record")
        _ <- Either.cond(live.recoveryRequired == supplied.recoveryRequired, (), "retry recovery evidence diverges from the live record")
      } yield ()
    }

  private def _definition(
    debug: JobDebugInfo,
    supplied: DurableDefinitionSnapshot
  ): Either[String, Unit] = {
    val snapshotmatch = debug.jobDefinitionSnapshot.map { live =>
      Either.cond(
        live.id == supplied.definitionId &&
          live.key == supplied.key &&
          live.jclFormat == supplied.format,
        (),
        "definition snapshot evidence diverges from the live definition snapshot"
      )
    }.getOrElse(Right(()))
    val parameters = Map(
      "jcl.jobDefinition.id" -> supplied.definitionId,
      "jcl.jobDefinition.key" -> supplied.key
    ) ++ supplied.format.map("jcl.jobDefinition.format" -> _)
    for {
      _ <- snapshotmatch
      _ <- _sequence(parameters.toVector.map { case (key, expected) =>
        debug.parameters.get(key) match {
          case Some(actual) => Either.cond(actual == expected, (), s"definition parameter $key diverges from supplied snapshot")
          case None => Right(())
        }
      })
    } yield ()
  }

  private def _inputs(
    live: Option[JobInput],
    supplied: Vector[DurableInputReference]
  ): Either[String, Unit] = {
    val fields = live.toVector.flatMap(_.payloads).flatMap(_.fieldName)
    for {
      _ <- _unique(supplied.map(_.name), "durable input")
      _ <- Either.cond(live.forall(_.payloads.size == supplied.size), (), "input reference count diverges from the live input read-model")
      _ <- Either.cond(fields.isEmpty || fields == supplied.map(_.name), (), "input reference names diverge from the live input read-model")
    } yield ()
  }

  private def _result(
    record: JobRecord,
    supplied: DurableResultOutcome
  ): Either[String, Unit] = {
    val expected = record.status match {
      case JobStatus.Succeeded => supplied match {
        case DurableResultOutcome.Succeeded(_) => true
        case _ => false
      }
      case JobStatus.Failed => supplied match {
        case DurableResultOutcome.Failed(_) => true
        case _ => false
      }
      case JobStatus.Cancelled => supplied match {
        case DurableResultOutcome.Cancelled(_) => true
        case _ => false
      }
      case _ => false
    }
    Either.cond(expected, (), "terminal result evidence does not match JobStatus")
  }

  private def _result_v2(
    record: JobRecord,
    supplied: DurableResultOutcome
  ): Either[String, Unit] = {
    val expected = record.status match {
      case JobStatus.Submitted | JobStatus.Running | JobStatus.Suspended =>
        supplied == DurableResultOutcome.Pending
      case JobStatus.Succeeded => supplied match {
        case DurableResultOutcome.Succeeded(_) => true
        case _ => false
      }
      case JobStatus.Failed => supplied match {
        case DurableResultOutcome.Failed(_) => true
        case _ => false
      }
      case JobStatus.Cancelled => supplied match {
        case DurableResultOutcome.Cancelled(_) => true
        case _ => false
      }
      case null => false
    }
    Either.cond(expected, (), "v2 result evidence does not match JobStatus")
  }

  private def _positive(value: Long, name: String): Either[String, Unit] =
    Either.cond(value >= 1L, (), s"$name must be positive")

  private def _unique(values: Vector[String], name: String): Either[String, Unit] =
    Either.cond(values.distinct.size == values.size, (), s"duplicate $name identity")

  private def _sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) {
      case (Right(acc), Right(value)) => Right(acc :+ value)
      case (Left(message), _) => Left(message)
      case (_, Left(message)) => Left(message)
    }
}
