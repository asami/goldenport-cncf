package org.goldenport.cncf.context

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.conclusion.Disposition
import org.goldenport.protocol.{Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.cncf.action.{Action, ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.event.{EventRecord, EventStore, ReceptionDomainEvent}
import org.goldenport.cncf.job.{JobId, JobQueryReadModel, JobStatus}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.TestComponentFactory
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionDeterminismReplayScenarioSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "A controlled CNCF execution" should {
    "replay business data and its Job, Task, retry, Event, and profile evidence" in {
      Given("two independent runtimes created from the same controlled profile")

      When("the same asynchronous command retries once and then commits a domain event")
      val left = _run_scenario()
      val right = _run_scenario()

      Then("all semantic results and execution evidence are identical")
      left shouldBe right
      left.jobStatus shouldBe JobStatus.Succeeded
      left.taskStatuses shouldBe Vector("failed", "succeeded")
      left.domainEvents.map(_.name) shouldBe Vector("phase31.replay.completed")
      left.profileText should not include _run_key
      left.profileText should not include _random_seed
    }
  }

  private def _run_scenario(): _ReplaySnapshot = {
    val configuration = _controlled_configuration
    val global = GlobalRuntimeContext.create(
      "execution-determinism-replay-scenario",
      RuntimeConfig.from(configuration),
      configuration,
      ExecutionContext.create().observability,
      AliasResolver.empty
    )
    val subsystem = Subsystem(
      name = "execution-determinism-replay-scenario",
      scopeContext = Some(global.createChildScope(
        ScopeKind.Subsystem,
        "execution-determinism-replay-scenario"
      )),
      configuration = configuration,
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    )
    val component = TestComponentFactory.create(
      "execution_determinism_replay_component",
      Protocol.empty,
      subsystem = subsystem
    )
    subsystem.add(component)

    try {
      val control = global.executionProfileRuntime.testControl.getOrElse(
        fail("controlled runtime must expose execution test control")
      )
      val response = component.logic.executeAction(_ReplayCommand(new AtomicInteger(0)))
        .toOption
        .getOrElse(fail("JobAsync command submission failed"))
      val jobid = _job_id(response)

      control.runUntilIdle() shouldBe 1
      val waiting = subsystem.jobEngine.query(jobid).getOrElse(fail("retrying Job is missing"))
      val retrydueat = waiting.retry.nextRetryDueAt.getOrElse(fail("retry due time is missing"))

      control.advanceBy(Duration.ofMinutes(1L))
      control.runUntilIdle() shouldBe 1

      val completed = subsystem.jobEngine.query(jobid).getOrElse(fail("completed Job is missing"))
      val data = completed.result match {
        case Some(OperationResponse.RecordResponse(record)) => record
        case other => fail(s"expected replay business record but got: $other")
      }
      val events = subsystem.eventStore.query(EventStore.Query()).toOption.getOrElse(
        fail("EventStore query failed")
      )
      _snapshot(data, jobid, waiting, retrydueat, completed, events)
    } finally {
      subsystem.shutdown()
    }
  }

  private def _job_id(response: OperationResponse): JobId =
    response match {
      case OperationResponse.Scalar(value) =>
        JobId.parse(value.toString).toOption.getOrElse(fail("response does not contain a JobId"))
      case other =>
        fail(s"expected JobId scalar but got: $other")
    }

  private def _snapshot(
    data: Record,
    jobid: JobId,
    waiting: JobQueryReadModel,
    retrydueat: Instant,
    completed: JobQueryReadModel,
    events: Vector[EventRecord]
  ): _ReplaySnapshot = {
    val tasks = completed.tasks.tasks.map { task =>
      _TaskSnapshot(
        id = task.taskId.value,
        parentId = task.parentTaskId.map(_.value),
        status = task.status.toString.toLowerCase,
        startedAt = task.startedAt,
        finishedAt = task.finishedAt,
        transactionOutcome = task.transactionOutcome
      )
    }
    val timeline = completed.timeline.events.map { event =>
      _TimelineSnapshot(
        sequence = event.sequence,
        occurredAt = event.occurredAt,
        kind = event.kind,
        taskId = event.taskId.map(_.value),
        parentTaskId = event.parentTaskId.map(_.value)
      )
    }
    val eventrecords = events.map { event =>
      _EventSnapshot(
        id = event.id.value,
        name = event.name,
        kind = event.kind,
        occurredAt = event.createdAt,
        sequence = event.sequence,
        lane = event.lane.value,
        payload = event.payload,
        attributes = _semantic_event_attributes(event.attributes)
      )
    }
    val profile = data.getRecord("executionProfile").getOrElse(
      fail("business response is missing execution profile evidence")
    )
    _ReplaySnapshot(
      data = data,
      jobId = jobid.value,
      jobStatus = completed.status,
      createdAt = completed.createdAt,
      waitingUpdatedAt = waiting.updatedAt,
      completedUpdatedAt = completed.updatedAt,
      retryDueAt = retrydueat,
      retryAttempts = completed.retry.attemptCount,
      taskStatuses = tasks.map(_.status),
      tasks = tasks,
      timeline = timeline,
      domainEvents = eventrecords.filter(_.name == "phase31.replay.completed"),
      allEvents = eventrecords,
      profileText = profile.print
    )
  }

  private def _controlled_configuration: ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("test"),
        RuntimeConfig.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("controlled"),
        RuntimeConfig.EXECUTION_KEY -> ConfigurationValue.StringValue(_run_key),
        RuntimeConfig.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue("2026-07-28T09:00:00Z"),
        RuntimeConfig.EXECUTION_RANDOM_MODE_KEY -> ConfigurationValue.StringValue("seeded"),
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue(_random_seed),
        RuntimeConfig.EXECUTION_IDS_MODE_KEY -> ConfigurationValue.StringValue("deterministic"),
        RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_ORDERING_MODE_KEY -> ConfigurationValue.StringValue("deterministic")
      )),
      ConfigurationTrace.empty
    )

  private def _semantic_event_attributes(
    attributes: Map[String, String]
  ): Map[String, String] =
    attributes.filterNot { case (key, _) =>
      _diagnostic_event_attribute_keys.contains(key)
    }

  private final case class _ReplayCommand(
    attempts: AtomicInteger
  ) extends CommandAction {
    val request: Request = Request.of(
      component = "execution_determinism_replay_component",
      service = "replay",
      operation = "execute"
    )

    override def commandExecutionMode: CommandExecutionMode =
      CommandExecutionMode.JobAsync

    def createCall(core: ActionCall.Core): ActionCall =
      _ReplayCall(core, this, attempts)
  }

  private final case class _ReplayCall(
    core: ActionCall.Core,
    override val action: Action,
    attempts: AtomicInteger
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      if (attempts.incrementAndGet() == 1)
        Consequence.Failure(
          Conclusion
            .simple("controlled replay retry")
            .copy(disposition = Disposition(Disposition.UserAction.RetryLater))
        )
      else {
        val occurredat = current_instant
        val price = random_int("phase31.replay.price", 10000)
        val entityid = entity_id(_replay_collection, "phase31.replay.entity")
        execution_context.jobContext.actionId match {
          case Some(actionid) =>
            val event = ReceptionDomainEvent(
              name = "phase31.replay.completed",
              kind = "domain-event",
              payload = Map(
                "entityId" -> entityid.value,
                "price" -> price
              ),
              attributes = Map(
                "source" -> "phase31-replay",
                "actionId" -> actionid.value
              ),
              occurredAt = occurredat
            )
            execution_context.runtime.unitOfWork.stageEvent(event)
            Consequence.success(OperationResponse.RecordResponse(Record.data(
              "entityId" -> entityid.value,
              "price" -> price,
              "occurredAt" -> occurredat.toString,
              "actionId" -> actionid.value,
              "executionProfile" -> execution_context.executionControl.toRecord
            )))
          case None =>
            Consequence.stateConflict("controlled replay ActionId is missing")
        }
      }
  }

  private final case class _ReplaySnapshot(
    data: Record,
    jobId: String,
    jobStatus: JobStatus,
    createdAt: Instant,
    waitingUpdatedAt: Instant,
    completedUpdatedAt: Instant,
    retryDueAt: Instant,
    retryAttempts: Int,
    taskStatuses: Vector[String],
    tasks: Vector[_TaskSnapshot],
    timeline: Vector[_TimelineSnapshot],
    domainEvents: Vector[_EventSnapshot],
    allEvents: Vector[_EventSnapshot],
    profileText: String
  )

  private final case class _TaskSnapshot(
    id: String,
    parentId: Option[String],
    status: String,
    startedAt: Instant,
    finishedAt: Option[Instant],
    transactionOutcome: Option[String]
  )

  private final case class _TimelineSnapshot(
    sequence: Long,
    occurredAt: Instant,
    kind: String,
    taskId: Option[String],
    parentTaskId: Option[String]
  )

  private final case class _EventSnapshot(
    id: String,
    name: String,
    kind: String,
    occurredAt: Instant,
    sequence: Long,
    lane: String,
    payload: Map[String, Any],
    attributes: Map[String, String]
  )

  private val _run_key = "phase31-private-run-key"
  private val _random_seed = "phase31-private-random-seed"
  private val _replay_collection = EntityCollectionId("phase31", "replay", "entity")
  private val _diagnostic_event_attribute_keys = Set(
    "correlation-id",
    "trace-id",
    "span-id",
    "traceId",
    "spanId"
  )
}
