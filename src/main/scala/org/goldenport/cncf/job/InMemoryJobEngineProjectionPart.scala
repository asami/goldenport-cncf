package org.goldenport.cncf.job

import java.security.MessageDigest
import java.time.{Duration, Instant}
import java.util.Base64
import java.util.concurrent.{
  ConcurrentHashMap,
  Executors,
  PriorityBlockingQueue,
  ScheduledExecutorService,
  TimeUnit,
  ExecutorService
}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.ExecutionContext as ScalaExecutionContext
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.consequence.Failures
import org.goldenport.id.UniversalId
import org.goldenport.observation.{Cause, Descriptor}
import org.goldenport.conclusion.Disposition
import org.goldenport.observation.Taxonomy
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordEncoder
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, QueryAction}
import org.goldenport.cncf.component.{Component, ComponentLogic}
import org.goldenport.cncf.context.{
  ExecutionContext,
  ExecutionInvocationIdentity,
  ExecutionProfileRuntime,
  ExecutionSchedulerMode,
  ExecutionSchedulingRegistration,
  IdGenerationContext
}
import org.goldenport.cncf.entity.{
  EntityMutationExecutionPolicy,
  EntityPersistentCreate,
  EntityStore
}
import org.simplemodeling.model.datatype.EntityRevision
import org.goldenport.cncf.event.{
  EventBus,
  EventLane,
  EventPublishOption,
  EventRecordFactory,
  EventStore,
  ReceptionDomainEvent
}
import org.goldenport.cncf.naming.NamingConventions
import org.goldenport.cncf.observability.{DiagnosticPayloadExternalizer, ObservabilityEngine}

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[job] trait InMemoryJobEngineProjectionPart extends JobEngine { self: InMemoryJobEngine =>
  import InMemoryJobEngine.*

  private[job] def _task_page(
    record: JobRecord,
    offset: Int,
    limit: Int
  ): JobTaskPage =
    _task_page(record.taskReadModels, offset, limit)

  private[job] def _task_page(
    tasks: Vector[JobTaskReadModel],
    offset: Int,
    limit: Int
  ): JobTaskPage = {
    val sorted = tasks
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

  private[job] def _timeline_page(
    record: JobRecord,
    offset: Int,
    limit: Int
  ): JobTimelinePage =
    _timeline_page(record.timeline, offset, limit)

  private[job] def _timeline_page(
    timeline: Vector[JobTimelineEvent],
    offset: Int,
    limit: Int
  ): JobTimelinePage = {
    val sorted = timeline.sortBy(e => (e.sequence, e.occurredAt.toEpochMilli))
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
    def _build_(parent: Option[TaskId]): Vector[JobTraceTaskNode] =
      childrenbyparent
        .getOrElse(parent, Vector.empty)
        .sortBy(_.startedAt.toEpochMilli)
        .map { task =>
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
            recoveryRequired = task.recoveryRequired,
            events = eventsbytask.getOrElse(task.taskId, Vector.empty).sortBy(_.sequence),
            children = _build_(Some(task.taskId))
          )
        }
    JobTraceTree(
      jobId = record.id,
      roots = _build_(None)
    )
  }

  private def _event_lineage(record: JobRecord): JobEventLineage = {
    val parameters = record.debug.parameters
    def _param_(key: String): Option[String] =
      parameters.get(key).map(_.trim).filter(_.nonEmpty)

    def _failure_disposition_(
      jobrelation: Option[String],
      failurepolicy: Option[String]
    ): AsyncFailureDisposition =
      (
        jobrelation.map(_.toLowerCase(java.util.Locale.ROOT)),
        failurepolicy.map(_.toLowerCase(java.util.Locale.ROOT))
      ) match {
        case (Some("newjob"), Some("retry")) if record.status == JobStatus.Failed =>
          AsyncFailureDisposition.Retryable
        case (Some("newjob"), Some("fail")) if record.status == JobStatus.Failed =>
          AsyncFailureDisposition.Terminal
        case _ =>
          AsyncFailureDisposition.NotApplicable
      }

    val jobrelation = _param_("reception.jobRelation")
    val failurepolicy = _param_("failure.policy")

    JobEventLineage(
      eventName = _param_("event.name"),
      eventKind = _param_("event.kind"),
      parentJobId =
        _param_("cncf.context.jobId")
          .orElse(record.submittedContext.jobContext.jobId.map(_.print)),
      correlationId =
        _param_("cncf.context.correlationId")
          .orElse(record.submittedContext.observability.correlationId.map(_.print)),
      sagaId =
        _param_("saga.id")
          .orElse(_param_("cncf.event.sagaId"))
          .orElse(record.submittedContext.observability.sagaId),
      causationId =
        _param_("cncf.context.causationId")
          .orElse(record.submittedContext.jobContext.causationId),
      sourceSubsystem = _param_("cncf.source.subsystem"),
      sourceComponent = _param_("cncf.source.component"),
      targetSubsystem = _param_("cncf.target.subsystem"),
      targetComponent = _param_("cncf.target.component"),
      receptionRule = _param_("reception.rule"),
      receptionPolicy = _param_("reception.policy"),
      policySource = _param_("reception.policySource"),
      jobRelation = jobrelation,
      taskRelation = _param_("reception.taskRelation"),
      transactionRelation = _param_("reception.transactionRelation"),
      sagaRelation = _param_("saga.relation"),
      failurePolicy = failurepolicy,
      failureDisposition = _failure_disposition_(jobrelation, failurepolicy)
    )
  }

  private[job] def _task_transaction_role(
    parameters: Map[String, String]
  ): Option[String] =
    parameters.get("reception.transactionRelation")
      .map(_.trim.toLowerCase(java.util.Locale.ROOT))
      .collect {
        case "same-transaction" => org.goldenport.cncf.action.TaskTransactionRole.Join.print
        case "new-transaction" => org.goldenport.cncf.action.TaskTransactionRole.Own.print
      }
      .orElse {
        parameters.get("command.caller-transaction-policy")
          .map(_.trim.toLowerCase(java.util.Locale.ROOT))
          .collect {
            case "join-caller" => org.goldenport.cncf.action.TaskTransactionRole.Join.print
            case "new-transaction" => org.goldenport.cncf.action.TaskTransactionRole.Own.print
          }
      }

  private def _continuation_summary(
    record: JobRecord
  ): JobContinuationSummary = {
    val queued = record.timeline
      .filter(_.kind == "job.same-job-async.queued")
      .flatMap(_.taskId)
      .toSet
    val tasks = queued.toVector.sortBy(_.value).map { taskid =>
      record.taskReadModels.find(_.taskId == taskid) match {
        case Some(task) =>
          val action = Vector(task.service, task.operation).flatten match {
            case service +: operation +: _ => Some(s"$service.$operation")
            case _ => task.operation.orElse(task.service).orElse(task.component)
          }
          JobContinuationTaskRef(
            taskId = taskid,
            status = task.status.toString,
            action = action,
            parentTaskId = task.parentTaskId
          )
        case None =>
          val timeline = record.timeline.find(e =>
            e.kind == "job.same-job-async.queued" && e.taskId.contains(taskid)
          )
          JobContinuationTaskRef(
            taskId = taskid,
            status = "Queued",
            action = timeline.flatMap(_.note),
            parentTaskId = timeline.flatMap(_.parentTaskId)
          )
      }
    }
    val mode = record.debug.parameters.get("command.async-continuation")
      .orElse(if (tasks.nonEmpty) Some("event-async-same-job-task") else None)
    val policy = mode.collect {
      case "event-async-same-job-task" => "async-same-job"
      case "event-async-new-job" => "async-new-job"
    }
    JobContinuationSummary(mode, policy, tasks)
  }

  private final case class Page[A](
    offset: Int,
    limit: Int,
    total: Int,
    items: Vector[A]
  )

  private def _page[A](items: Vector[A], offset: Int, limit: Int): Page[A] = {
    val normalizedoffset = math.max(0, offset)
    val normalizedlimit = math.max(0, limit)
    val dropped = if (normalizedoffset > 0) items.drop(normalizedoffset) else items
    val sliced = if (normalizedlimit > 0) dropped.take(normalizedlimit) else Vector.empty
    Page(normalizedoffset, normalizedlimit, items.size, sliced)
  }

  private def _result_summary(record: JobRecord): JobResultSummary =
    record.result match {
      case Some(JobResult.Success(_)) =>
        JobResultSummary(JobStatus.Succeeded, success = true, message = Some("ok"))
      case Some(JobResult.Failure(conclusion)) =>
        JobResultSummary(
          JobStatus.Failed,
          success = false,
          message = conclusion.observation.getEffectiveMessage
        )
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

  private[job] def _read_model(record: JobRecord): JobQueryReadModel = {
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
      scheduledStartAt = record.scheduledStartAt,
      tasks = tasks,
      timeline = timeline,
      traceTree = _trace_tree(record),
      debug = record.debug,
      input = record.input,
      lineage = _event_lineage(record),
      continuation = _continuation_summary(record),
      retry = record.retry,
      resultSummary = _result_summary(record),
      calltree = record.debug.calltree,
      result = record.result.collect { case JobResult.Success(res) => res }
    )
  }

  private[job] def _update_record(
    jobid: JobId,
    status: JobStatus,
    result: Option[JobResult]
  ): Unit =
    _mutate_record(jobid) { record =>
      record.copy(
        status = status,
        result = result.orElse(record.result),
        updatedAt = _now()
      )
    }

  private[job] def _mutate_record(jobid: JobId)(f: JobRecord => JobRecord): Unit =
    _state_monitor.synchronized {
      _get_record(jobid).foreach { current =>
        _put_record(f(current))
      }
    }

  private[job] def _put_record(
    record: JobRecord,
    preserveupdatedatonentitysyncfailure: Boolean = false
  ): Unit =
    _state_monitor.synchronized {
    record.persistence match {
      case JobPersistencePolicy.Persistent =>
        _durable_jobs.put(record.id, record)
          // Serialize the source JobRecord projection with its Entity revision.
          // Otherwise an older projection can load and reuse a newer revision.
        _sync_job_entity(record, preserveupdatedatonentitysyncfailure)
      case JobPersistencePolicy.Ephemeral =>
        _runtime_jobs.put(record.id, record)
    }
    _signal_state_change()
  }

  private def _sync_job_entity(
    record: JobRecord,
    preserveupdatedatonentitysyncfailure: Boolean
  ): Unit = {
    given ExecutionContext = record.submittedContext
    val entity = JobEntity.from(_read_model(record))
    val store              = EntityStore.standard()
    val persistent         = JobEntity.entityPersistent
    val result = store.loadDetached(entity.id)(using persistent, summon[ExecutionContext])
      .flatMap {
        case Some(snapshot) =>
          store.saveDetached(
            entity,
            Some(snapshot.revision),
            EntityMutationExecutionPolicy.default
          )(using persistent, summon[ExecutionContext])
        case None =>
          store.create(
            entity
          )(using EntityPersistentCreate.fromPersistent(persistent), summon[ExecutionContext])
      }
    val _ = result match {
      case Consequence.Success(_) =>
        ()
      case Consequence.Failure(conclusion) =>
        _record_job_entity_sync_failure(record, conclusion, preserveupdatedatonentitysyncfailure)
    }
  }

  private def _record_job_entity_sync_failure(
    record: JobRecord,
    conclusion: Conclusion,
    preserveupdatedatonentitysyncfailure: Boolean
  ): Unit = {
    val message = s"job-entity-sync-failed: ${conclusion.show}"
    val annotated = record.copy(
      debug = record.debug.copy(
        parameters = record.debug.parameters + ("cncf.job.entitySync" -> "failed"),
        executionNotes =
          if (record.debug.executionNotes.contains(message))
            record.debug.executionNotes
          else
            record.debug.executionNotes :+ message
      ),
      updatedAt =
        if (preserveupdatedatonentitysyncfailure)
          record.updatedAt
        else
          _now()
    )
    _durable_jobs.put(record.id, annotated)
  }

  private[job] def _get_record(jobid: JobId): Option[JobRecord] =
    Option(_durable_jobs.get(jobid)).orElse(Option(_runtime_jobs.get(jobid)))

  private[job] def _request_summary(tasks: List[JobTask]): Option[String] =
    tasks.headOption.flatMap(_.requestSummary)

  private[job] def _validate_submit_option(option: JobSubmitOption): Consequence[Unit] =
    option.scheduledStartAt match {
      case Some(scheduledat) if option.runMode == JobRunMode.Sync && scheduledat.isAfter(_now()) =>
        Consequence.argumentInvalid("scheduledStartAt requires async runMode")
      case Some(scheduledat)
          if scheduledat.isAfter(_now().plus(InMemoryJobEngine.MaxNonRetryDelay)) =>
        Consequence.argumentInvalid(
          s"scheduledStartAt exceeds built-in max delay: ${InMemoryJobEngine.MaxNonRetryDelay.toMinutes} minutes"
        )
      case _ =>
        Consequence.unit
    }

  private[job] def _request_parameters(tasks: List[JobTask]): Map[String, String] =
    tasks.headOption.map(_.requestParameters).getOrElse(Map.empty)

  private[job] def _default_submit_option(tasks: List[JobTask]): JobSubmitOption =
    JobSubmitOption(
      persistence = tasks.headOption
        .map(_.defaultPersistence)
        .getOrElse(JobPersistencePolicy.Persistent)
    )

  private[job] def _append_event(
    jobid: JobId,
    name: String,
    payload: Map[String, Any],
    attributes: Map[String, String] = Map.empty
  ): Unit =
    _get_record(jobid).foreach { record =>
      val (eventpayload, eventattributes) = _job_event_metadata(record, name, payload, attributes)
      val occurredat = _now()
      val event = ReceptionDomainEvent(
        name = name,
        kind = name,
        payload = eventpayload,
        attributes = eventattributes,
        occurredAt = occurredat
      )
      given ExecutionContext = record.submittedContext
      _event_bus match {
        case Some(bus) =>
          val _ = bus.publishRuntime(event, EventPublishOption(persistent = true))
        case None =>
          _event_store.foreach { store =>
            val factory = EventRecordFactory.from(record.submittedContext)
            val _ = store.append(Vector(factory.create(event, EventLane.NonTransactional)))
          }
      }
    }

  private def _job_event_metadata(
    record: JobRecord,
    name: String,
    payload: Map[String, Any],
    attributes: Map[String, String]
  ): (Map[String, Any], Map[String, String]) =
    if (name.startsWith("job.")) {
      val submitter = JobSubmitter.from(record.submittedContext)
      val summary = _result_summary(record)
      val extrapayload =
        Map(
          "submitter-principal-id" -> submitter.principalId,
          "submitter-subject-kind" -> submitter.subjectKind,
          "submitter-session-id" -> submitter.sessionId.getOrElse(""),
          "job-run-mode" -> _job_run_mode_label(record.runMode),
          "job-persistence" -> _job_persistence_label(record.persistence),
          "recovery-required" -> record.retry.recoveryRequired.toString
        ) ++
          record.debug.parameters.filter { case (k, _) => k.startsWith("web.") } ++
          summary.message.map("result-summary" -> _)
      val extraattributes =
        Map(
          "cncf.job.id" -> record.id.value,
          "cncf.job.status" -> record.status.toString
        ) ++
          record.submittedContext.observability.correlationId.map(id =>
            "correlation-id" -> id.print
          )
      (payload ++ extrapayload, attributes ++ extraattributes)
    } else
      (payload, attributes)

  private def _job_run_mode_label(runmode: JobRunMode): String =
    runmode match {
      case JobRunMode.Async => "async"
      case JobRunMode.Sync => "sync"
    }

  private def _job_persistence_label(persistence: JobPersistencePolicy): String =
    persistence match {
      case JobPersistencePolicy.Persistent => "persistent"
      case JobPersistencePolicy.Ephemeral => "ephemeral"
    }

}
