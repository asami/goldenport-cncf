package org.goldenport.cncf.job

import java.time.{Clock, Instant, ZoneOffset}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.event.{
  DomainEvent,
  EventBus,
  EventDispatchHandler,
  EventEngine,
  EventRecord,
  EventRecordFactory,
  EventStore,
  EventSubscription
}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 *  version Jul. 16, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobLifecycleEventExecutionDeterminismSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Job lifecycle Event execution capabilities" should {
    "materialize EventStore fallback records from the submitted Job profile" in {
      Given("generated execution instants and deterministic submitted Job profiles")
      val property = Prop.forAll(Gen.chooseNum(Int.MinValue, Int.MaxValue)) { epochoffset =>
        val instant = _instant(epochoffset)
        val left = _fallback_records(instant, "job-event-seed")
        val right = _fallback_records(instant, "job-event-seed")

        left.map(_.id) == right.map(_.id) &&
        left.map(_.name) == Vector("job.submitted", "job.running", "job.succeeded") &&
        left.map(_.id).distinct.size == 3 &&
        left.forall(_.id.major == "submitted") &&
        left.forall(_.id.minor == "job_event") &&
        left.forall(_.createdAt == instant)
      }

      When("equivalent synchronous Jobs are replayed through the EventStore fallback")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(8), property)

      Then("lifecycle Event identity and time are replay-stable and remain distinct")
      checked.passed shouldBe true
    }

    "preserve the submitted Job profile through EventBus persistence and dispatch" in {
      Given("an EventBus whose fallback profile differs from the submitted Job profile")
      val instant = Instant.parse("2026-07-16T00:00:00Z")
      val context = _execution_context(instant, "submitted-job-seed")
      val fallbackclock = Clock.fixed(instant.minusSeconds(60L), ZoneOffset.UTC)
      val fallbackfactory = EventRecordFactory(
        fallbackclock,
        IdGenerationContext.deterministic(
          IdGenerationContext.IdNamespace("fallback", "job_event"),
          fallbackclock,
          "fallback-job-event-seed"
        )
      )
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(
        DataStore.noop(),
        eventstore = eventstore,
        recordfactory = fallbackfactory
      )
      val eventbus = EventBus.default(eventengine)
      val dispatched = ArrayBuffer.empty[ExecutionContext]
      eventbus.register(
        EventSubscription(
          name = "job-lifecycle-profile",
          kind = Some("job.submitted"),
          handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              val _ = event
              Consequence.operationInvalid("runtime dispatch context was not propagated")
            }

            override def dispatchAuthorized(
              event: DomainEvent
            )(using current: ExecutionContext): Consequence[Unit] = {
              val _ = event
              dispatched += current
              Consequence.unit
            }
          }
        )
      )
      val timesource = new ManualJobTimeSource(instant)
      val timer = new InMemoryJobEngine.ManualJobTimer(timesource)
      val engine = new InMemoryJobEngine(
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
        timeSource = timesource,
        timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global).withEventBus(eventbus)

      When("the Job publishes its lifecycle Events")
      val submitted =
        try {
          engine.submit(Nil, context, JobSubmitOption(runMode = JobRunMode.Sync))
        } finally {
          engine.shutdown()
        }

      Then("EventBus records and handlers use the submitted execution profile")
      submitted.toOption should not be empty
      dispatched.toVector shouldBe Vector(context)
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
      records.map(_.name) shouldBe Vector("job.submitted", "job.running", "job.succeeded")
      records.forall(_.id.major == "submitted") shouldBe true
      records.forall(_.id.minor == "job_event") shouldBe true
      records.forall(_.createdAt == instant) shouldBe true
    }

    "publish EventStore fallback control transitions immediately and once" in {
      Given("an unstarted asynchronous Job with an EventStore fallback and permitted Suspend, Resume, and Cancel control")
      val instant = Instant.parse("2026-09-10T00:00:00Z")
      val context = _execution_context(instant, "control-event-store-seed")
      given ExecutionContext = context
      val eventstore = EventStore.inMemory
      val timesource = new ManualJobTimeSource(instant)
      val timer = new InMemoryJobEngine.ManualJobTimer(timesource)
      val engine = new InMemoryJobEngine(
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
        timeSource = timesource,
        timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global).withEventStore(eventstore)

      When("the submitted Job is suspended, resumed, and cancelled before any durable task checkpoint can own cancellation")
      val jobid = _success(engine.submit(Nil, context))
      _success(engine.control(jobid, JobControlRequest(JobControlCommand.Suspend), _permissive_control_policy))
      _success(engine.control(jobid, JobControlRequest(JobControlCommand.Resume), _permissive_control_policy))
      _success(engine.control(jobid, JobControlRequest(JobControlCommand.Cancel), _permissive_control_policy))
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)

      Then("the fallback records the local control order and one immediate cancellation Event")
      records.map(_.name) shouldBe Vector(
        "job.submitted",
        "job.suspended",
        "job.resumed",
        "job.cancelled"
      )
      records.count(_.name == "job.cancelled") shouldBe 1
      records.forall(_.id.major == "submitted") shouldBe true
      records.forall(_.id.minor == "job_event") shouldBe true
      records.forall(_.createdAt == instant) shouldBe true
      engine.shutdown()
    }

    "persist and dispatch EventBus control transitions immediately and once" in {
      Given("an EventBus-backed unstarted asynchronous Job with a dispatch observer for control lifecycle Events")
      val instant = Instant.parse("2026-09-10T00:00:00Z")
      val context = _execution_context(instant, "control-event-bus-seed")
      given ExecutionContext = context
      val eventstore = EventStore.inMemory
      val eventengine = EventEngine.noop(DataStore.noop(), eventstore = eventstore)
      val eventbus = EventBus.default(eventengine)
      val dispatched = ArrayBuffer.empty[ExecutionContext]
      eventbus.register(
        EventSubscription(
          name = "job-control-events",
          kind = None,
          handler = new EventDispatchHandler {
            def dispatch(event: DomainEvent): Consequence[Unit] = {
              val _ = event
              Consequence.operationInvalid("runtime dispatch context was not propagated")
            }

            override def dispatchAuthorized(
              event: DomainEvent
            )(using current: ExecutionContext): Consequence[Unit] = {
              val _ = event
              dispatched += current
              Consequence.unit
            }
          }
        )
      )
      val timesource = new ManualJobTimeSource(instant)
      val timer = new InMemoryJobEngine.ManualJobTimer(timesource)
      val engine = new InMemoryJobEngine(
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
        timeSource = timesource,
        timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global).withEventBus(eventbus)

      When("the submitted Job receives Suspend, Resume, and Cancel before any durable task checkpoint can own cancellation")
      val jobid = _success(engine.submit(Nil, context))
      _success(engine.control(jobid, JobControlRequest(JobControlCommand.Suspend), _permissive_control_policy))
      _success(engine.control(jobid, JobControlRequest(JobControlCommand.Resume), _permissive_control_policy))
      _success(engine.control(jobid, JobControlRequest(JobControlCommand.Cancel), _permissive_control_policy))
      val records = eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)

      Then("EventBus persists and dispatches the submitted-profile control order with one cancellation Event")
      records.map(_.name) shouldBe Vector(
        "job.submitted",
        "job.suspended",
        "job.resumed",
        "job.cancelled"
      )
      records.count(_.name == "job.cancelled") shouldBe 1
      dispatched.toVector shouldBe Vector.fill(4)(context)
      engine.shutdown()
    }

    "publish durable cancellation only after its terminal checkpoint accepts" in {
      Given("a queued terminal retry with EventStore/EventBus observers and matching or incompatible closed cancellation evidence")
      val instant = Instant.parse("2026-09-11T00:00:00Z")
      val context = _execution_context(instant, "durable-cancelled-retry-seed")
      given ExecutionContext = context
      val accepted = new DurableCancellationFixture(context, _durable_cancelled_terminal_result)
      val refused = new DurableCancellationFixture(context, _durable_succeeded_terminal_result)

      When("each first attempt queues its terminal retry, is cancelled before the queued body runs, and then drains the inert work item")
      val acceptedid = _success(accepted.engine.submit(List(accepted.task), context))
      accepted.engine.drainOne() shouldBe true
      _success(accepted.engine.control(
        acceptedid,
        JobControlRequest(JobControlCommand.Cancel),
        _permissive_control_policy
      ))
      accepted.engine.drainAll() shouldBe 1
      val refusedid = _success(refused.engine.submit(List(refused.task), context))
      refused.engine.drainOne() shouldBe true
      _success(refused.engine.control(
        refusedid,
        JobControlRequest(JobControlCommand.Cancel),
        _permissive_control_policy
      ))
      refused.engine.drainAll() shouldBe 1

      Then("EventStore and EventBus receive one cancellation only after accepted terminal evidence, while refusal suppresses it and neither queued body runs")
      accepted.source.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      accepted.cancelledEventsAtTerminalCheckpoint shouldBe 0
      accepted.eventNames.count(_ == "job.cancelled") shouldBe 1
      accepted.eventNames.lastOption shouldBe Some("job.cancelled")
      accepted.cancelledDispatches shouldBe 1
      accepted.task.runCount shouldBe 1
      accepted.engine.getStatus(acceptedid) shouldBe Some(JobStatus.Cancelled)
      refused.source.boundaries.last shouldBe DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      refused.cancelledEventsAtTerminalCheckpoint shouldBe 0
      refused.eventNames.count(_ == "job.cancelled") shouldBe 0
      refused.cancelledDispatches shouldBe 0
      refused.task.runCount shouldBe 1
      refused.engine.getStatus(refusedid) shouldBe Some(JobStatus.Cancelled)
      refused.engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(
          refusedid,
          DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
        )
      )
      accepted.engine.shutdown()
      refused.engine.shutdown()
    }

    "settle cancelled queued same-job work through the durable terminal checkpoint gate" in {
      Given("a Persistent Job whose first task queues a same-job child and EventStore/EventBus observe accepted or refused cancellation evidence")
      val instant = Instant.parse("2026-09-11T00:00:00Z")
      val context = _execution_context(instant, "durable-cancelled-same-job-seed")
      given ExecutionContext = context
      val accepted = new DurableQueuedSameJobCancellationFixture(context, _durable_cancelled_terminal_result)
      val refused = new DurableQueuedSameJobCancellationFixture(context, _durable_succeeded_terminal_result)
      accepted.child.runCount shouldBe 0
      refused.child.runCount shouldBe 0

      When("the first task queues a child, cancellation arrives before that child dispatches, and the queued work drains")
      val acceptedid = _success(accepted.engine.submit(List(accepted.parent), context))
      accepted.engine.drainOne() shouldBe true
      val acceptedcancelled = _success(accepted.engine.control(
        acceptedid,
        JobControlRequest(JobControlCommand.Cancel),
        _permissive_control_policy
      ))
      acceptedcancelled.status shouldBe JobStatus.Cancelled
      accepted.engine.drainAll() shouldBe 1
      val refusedid = _success(refused.engine.submit(List(refused.parent), context))
      refused.engine.drainOne() shouldBe true
      val refusedcancelled = _success(refused.engine.control(
        refusedid,
        JobControlRequest(JobControlCommand.Cancel),
        _permissive_control_policy
      ))
      refusedcancelled.status shouldBe JobStatus.Cancelled
      refused.engine.drainAll() shouldBe 1

      Then("accepted terminal evidence precedes one cancellation observation, refusal emits none, and neither queued child body runs")
      accepted.source.boundaries shouldBe Vector(
        DurableJobLifecycleWriteBoundary.Admission,
        DurableJobLifecycleWriteBoundary.StartIntent,
        DurableJobLifecycleWriteBoundary.RunningIntent,
        DurableJobLifecycleWriteBoundary.TaskStartIntent,
        DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
        DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint
      )
      accepted.cancelledEventsAtTerminalCheckpoint shouldBe 0
      accepted.eventNames.count(_ == "job.cancelled") shouldBe 1
      accepted.cancelledDispatches shouldBe 1
      accepted.parent.runCount shouldBe 1
      accepted.child.runCount shouldBe 0
      accepted.engine.getStatus(acceptedid) shouldBe Some(JobStatus.Cancelled)
      refused.source.boundaries shouldBe accepted.source.boundaries
      refused.cancelledEventsAtTerminalCheckpoint shouldBe 0
      refused.eventNames.count(_ == "job.cancelled") shouldBe 0
      refused.cancelledDispatches shouldBe 0
      refused.parent.runCount shouldBe 1
      refused.child.runCount shouldBe 0
      refused.engine.getStatus(refusedid) shouldBe Some(JobStatus.Cancelled)
      refused.engine.durableLifecycleWriteFailureFacts shouldBe Vector(
        DurableJobLifecycleWriteFailureFact(
          refusedid,
          DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused
        )
      )
      accepted.engine.shutdown()
      refused.engine.shutdown()
    }
  }

  private val _permissive_control_policy = new JobControlPolicy {
    def authorize(
      jobId: JobId,
      request: JobControlRequest
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.unit
    }
  }

  private def _success[A](consequence: Consequence[A]): A =
    consequence.toOption.getOrElse(fail("expected success"))

  private def _fallback_records(
    instant: Instant,
    seed: String
  ): Vector[EventRecord] = {
    val context = _execution_context(instant, seed)
    val eventstore = EventStore.inMemory
    val timesource = new ManualJobTimeSource(instant)
    val timer = new InMemoryJobEngine.ManualJobTimer(timesource)
    val engine = new InMemoryJobEngine(
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
      timeSource = timesource,
      timer = Some(timer)
    )(scala.concurrent.ExecutionContext.global).withEventStore(eventstore)
    try {
      engine.submit(Nil, context, JobSubmitOption(runMode = JobRunMode.Sync)).toOption
      eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty)
    } finally {
      engine.shutdown()
    }
  }

  private def _execution_context(
    instant: Instant,
    seed: String
  ): ExecutionContext = {
    val clock = Clock.fixed(instant, ZoneOffset.UTC)
    val base = ExecutionContext.create(clock)
    val idgeneration = IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("submitted", "job_event"),
      clock,
      seed
    )
    ExecutionContext.withIdGenerationContext(base, idgeneration)
  }

  private def _instant(offset: Int): Instant = {
    val epoch = 1_700_000_000L + Math.floorMod(offset.toLong, 1_000_000L)
    Instant.ofEpochSecond(epoch)
  }

  private final class DurableCancellationFixture(
    context: ExecutionContext,
    terminalresult: DurableResultOutcome
  ) {
    private val _store = new DurableJobStore(EntityStore.standard())
    private val _eventstore = EventStore.inMemory
    private val _eventengine = EventEngine.noop(DataStore.noop(), eventstore = _eventstore)
    private val _eventbus = EventBus.default(_eventengine)
    private val _timesource = new ManualJobTimeSource(_durable_instant)
    private val _timer = new InMemoryJobEngine.ManualJobTimer(_timesource)
    var cancelledDispatches = 0
    var cancelledEventsAtTerminalCheckpoint = -1
    val source = new DurableLifecycleEvidenceSource(
      terminalresult,
      () => cancelledEventsAtTerminalCheckpoint = eventNames.count(_ == "job.cancelled")
    )
    val engine = new InMemoryJobEngine(
      retrySchedule = InMemoryJobEngine.RetrySchedule(Vector.empty),
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
      timeSource = _timesource,
      timer = Some(_timer)
    )(scala.concurrent.ExecutionContext.global).withEventBus(_eventbus)
    val task = new DurableRetryTask

    _eventbus.register(
      EventSubscription(
        name = "durable-cancelled-retry-observer",
        kind = Some("job.cancelled"),
        handler = new EventDispatchHandler {
          def dispatch(event: DomainEvent): Consequence[Unit] = {
            val _ = event
            Consequence.operationInvalid("runtime dispatch context was not propagated")
          }

          override def dispatchAuthorized(
            event: DomainEvent
          )(using current: ExecutionContext): Consequence[Unit] = {
            val _ = event
            val _ = current
            cancelledDispatches += 1
            Consequence.unit
          }
        }
      )
    )
    engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(_store, source))

    def eventNames: Vector[String] =
      _eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty).map(_.name)
  }

  private final class DurableQueuedSameJobCancellationFixture(
    context: ExecutionContext,
    terminalresult: DurableResultOutcome
  ) {
    private val _store = new DurableJobStore(EntityStore.standard())
    private val _eventstore = EventStore.inMemory
    private val _eventengine = EventEngine.noop(DataStore.noop(), eventstore = _eventstore)
    private val _eventbus = EventBus.default(_eventengine)
    private val _timesource = new ManualJobTimeSource(_durable_instant)
    private val _timer = new InMemoryJobEngine.ManualJobTimer(_timesource)
    var cancelledDispatches = 0
    var cancelledEventsAtTerminalCheckpoint = -1
    val source = new DurableLifecycleEvidenceSource(
      terminalresult,
      () => cancelledEventsAtTerminalCheckpoint = eventNames.count(_ == "job.cancelled")
    )
    val engine = new InMemoryJobEngine(
      retrySchedule = InMemoryJobEngine.RetrySchedule(Vector.empty),
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false),
      timeSource = _timesource,
      timer = Some(_timer)
    )(scala.concurrent.ExecutionContext.global).withEventBus(_eventbus)
    val child = new DurableQueuedSameJobChildTask
    val parent = new DurableQueuedSameJobParentTask(child)
    parent.bind(engine)

    _eventbus.register(
      EventSubscription(
        name = "durable-cancelled-same-job-observer",
        kind = Some("job.cancelled"),
        handler = new EventDispatchHandler {
          def dispatch(event: DomainEvent): Consequence[Unit] = {
            val _ = event
            Consequence.operationInvalid("runtime dispatch context was not propagated")
          }

          override def dispatchAuthorized(
            event: DomainEvent
          )(using current: ExecutionContext): Consequence[Unit] = {
            val _ = event
            val _ = current
            cancelledDispatches += 1
            Consequence.unit
          }
        }
      )
    )
    engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(_store, source))

    def eventNames: Vector[String] =
      _eventstore.query(EventStore.Query()).toOption.getOrElse(Vector.empty).map(_.name)
  }

  private final class DurableRetryTask extends JobTask {
    val actionId: ActionId = ActionId(
      "cncf",
      "durable_cancelled_retry",
      Some(_durable_instant),
      Some("task")
    )
    var runCount = 0

    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def operationName: Option[String] = Some("run")

    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      runCount += 1
      if (runCount == 1)
        TaskFailed(_retry_now_conclusion)
      else
        TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Scalar("retry-ran"))
    }
  }

  private final class DurableQueuedSameJobChildTask extends JobTask {
    val actionId: ActionId = ActionId(
      "cncf",
      "durable_cancelled_same_job_child",
      Some(_durable_instant),
      Some("task")
    )
    var runCount = 0

    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("child")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def operationName: Option[String] = Some("run-child")

    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      runCount += 1
      TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Scalar("child-ran"))
    }
  }

  private final class DurableQueuedSameJobParentTask(
    child: DurableQueuedSameJobChildTask
  ) extends JobTask {
    val actionId: ActionId = ActionId(
      "cncf",
      "durable_cancelled_same_job_parent",
      Some(_durable_instant),
      Some("task")
    )
    private var _engine: Option[InMemoryJobEngine] = None
    var runCount = 0

    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def componentName: Option[String] = Some("component-a")
    override def operationName: Option[String] = Some("run-parent")

    def bind(engine: InMemoryJobEngine): Unit =
      _engine = Some(engine)

    def run(ctx: ExecutionContext): TaskOutcome = {
      runCount += 1
      (_engine, ctx.jobContext.jobId) match {
        case (Some(engine), Some(jobid)) =>
          engine.enqueueTaskInJob(jobid, child, ctx) match {
            case Consequence.Success(_) =>
              TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Scalar("parent-ran"))
            case Consequence.Failure(c) =>
              TaskFailed(c)
          }
        case _ =>
          TaskFailed(Consequence.stateInvalid[Nothing](
            "same-job parent has no bound engine or Job context"
          ).conclusion)
      }
    }
  }

  private final class DurableLifecycleEvidenceSource(
    terminalresult: DurableResultOutcome,
    onterminalcheckpoint: () => Unit
  ) extends (DurableJobLifecycleWriteRequest => Consequence[DurableJobLifecycleWriteEvidence]) {
    private var _requests = Vector.empty[DurableJobLifecycleWriteRequest]

    def apply(request: DurableJobLifecycleWriteRequest): Consequence[DurableJobLifecycleWriteEvidence] = {
      _requests :+= request
      request match {
        case DurableJobLifecycleWriteRequest.Admission =>
          Consequence.success(_durable_evidence(1L))
        case DurableJobLifecycleWriteRequest.StartIntent =>
          Consequence.success(_durable_evidence(2L))
        case DurableJobLifecycleWriteRequest.RunningIntent =>
          Consequence.success(_durable_evidence(3L))
        case taskstart: DurableJobLifecycleWriteRequest.TaskStartIntent =>
          Consequence.success(_durable_evidence(
            _requests.size.toLong,
            taskstart.taskreadmodels.map(_durable_task_descriptor)
          ))
        case taskoutcome: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint =>
          Consequence.success(_durable_evidence(
            _requests.size.toLong,
            taskoutcome.taskreadmodels.map(_durable_task_descriptor)
          ))
        case terminal: DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint =>
          onterminalcheckpoint()
          Consequence.success(_durable_evidence(
            _requests.size.toLong,
            terminal.taskreadmodels.map(_durable_task_descriptor),
            terminalresult,
            _durable_terminal_retry(terminalresult)
          ))
      }
    }

    def boundaries: Vector[DurableJobLifecycleWriteBoundary] = _requests.map(_.boundary)
  }

  private val _durable_instant = Instant.parse("2026-09-11T00:00:00Z")
  private val _durable_digest = "a" * 64
  private val _durable_access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _durable_cancelled_terminal_result = DurableResultOutcome.Cancelled(Some(
    DurableFailureSummary("control", "cancelled", "cancelled", retryable = false)
  ))
  private val _durable_succeeded_terminal_result = DurableResultOutcome.Succeeded(DurableValue.Absent)

  private def _retry_now_conclusion =
    org.goldenport.Conclusion.simple("retry now").copy(
      disposition = org.goldenport.conclusion.Disposition(
        org.goldenport.conclusion.Disposition.UserAction.RetryNow
      )
    )

  private def _durable_evidence(
    revision: Long,
    tasks: Vector[DurableTaskDescriptor] = Vector(_durable_closed_task_descriptor),
    result: DurableResultOutcome = DurableResultOutcome.Pending,
    retry: DurableRetryEvidence = DurableRetryEvidence(Vector.empty, 3, None, exhausted = false, recoveryRequired = false)
  ): DurableJobLifecycleWriteEvidence =
    DurableJobLifecycleWriteEvidence(
      projection = DurableJobProjectionEvidence(
        semanticRevision = revision,
        authorization = DurableJobAuthorization(
          "tenant-a",
          DurableSubject("subject-a", "user"),
          DurableVisibility.Subject,
          Set("job.read")
        ),
        tasks = tasks,
        inputs = Vector.empty,
        result = result,
        retry = retry,
        diagnostics = Vector(DurableDiagnosticSummary("admission", "accepted", "info", "closed evidence")),
        calltreeReference = None,
        definitionSnapshot = DurableDefinitionSnapshot(
          "definition-001",
          "durable-job",
          1,
          1L,
          _durable_digest,
          None,
          None,
          Map.empty
        ),
        retention = DurableRetentionState(
          Some(_durable_instant.plusSeconds(86400L)),
          None,
          None,
          DurableDeletionState.Active,
          None
        )
      ),
      access = _durable_access
    )

  private val _durable_closed_task_descriptor = DurableTaskDescriptor(
    taskId = "closed-task-001",
    parentTaskId = None,
    kind = DurableTaskKind.Operation,
    target = DurableTaskTarget(
      "operation",
      DurableOperationReference("component-a", None, "run", None)
    ),
    relation = DurableTaskRelation(DurableTaskRelationKind.Root, None),
    transaction = DurableTransactionDescriptor(
      DurableTransactionRole.Own,
      DurableTransactionScope.PerTask,
      DurableTransactionOutcome.Pending
    ),
    compensation = None
  )

  private def _durable_task_descriptor(model: JobTaskReadModel): DurableTaskDescriptor =
    DurableTaskDescriptor(
      taskId = model.taskId.value,
      parentTaskId = model.parentTaskId.map(_.value),
      kind = DurableTaskKind.parse(model.taskKind).fold(message => fail(message), identity),
      target = DurableTaskTarget(
        model.targetKind.getOrElse(fail("task target kind is missing")),
        DurableOperationReference(
          model.component.getOrElse(fail("task component is missing")),
          model.service,
          model.operation.getOrElse(fail("task operation is missing")),
          None
        )
      ),
      relation = DurableTaskRelation(
        DurableTaskRelationKind.parse(model.relation.getOrElse(
          fail("task relation is missing")
        )).fold(message => fail(message), identity),
        model.parentTaskId.map(_.value)
      ),
      transaction = DurableTransactionDescriptor(
        DurableTransactionRole.parse(model.transactionRole.getOrElse(
          fail("task transaction role is missing")
        )).fold(message => fail(message), identity),
        DurableTransactionScope.parse(model.transactionScope.getOrElse(
          fail("task transaction scope is missing")
        )).fold(message => fail(message), identity),
        model.transactionOutcome match {
          case Some("running") => DurableTransactionOutcome.Pending
          case Some(value) => DurableTransactionOutcome.parse(value).fold(message => fail(message), identity)
          case None => fail("task transaction outcome is missing")
        }
      ),
      compensation = None
    )

  private def _durable_terminal_retry(result: DurableResultOutcome): DurableRetryEvidence = {
    val outcome = result match {
      case DurableResultOutcome.Succeeded(_) => DurableAttemptOutcome.Succeeded
      case DurableResultOutcome.Failed(_) => DurableAttemptOutcome.Failed
      case DurableResultOutcome.Cancelled(_) => DurableAttemptOutcome.Cancelled
      case DurableResultOutcome.Pending => fail("terminal evidence cannot be pending")
    }
    DurableRetryEvidence(
      Vector(DurableAttemptEvidence(1, _durable_instant, Some(_durable_instant.plusMillis(1L)), outcome, None)),
      3,
      None,
      exhausted = false,
      recoveryRequired = outcome == DurableAttemptOutcome.Failed
    )
  }
}
