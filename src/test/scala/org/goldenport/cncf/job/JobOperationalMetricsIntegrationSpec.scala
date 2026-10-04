package org.goldenport.cncf.job

import java.time.{Clock, Duration, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.cncf.context.{
  CorrelationId, ExecutionContext, IdGenerationContext, Principal, PrincipalId, SpanId, TraceId
}
import org.goldenport.cncf.event.{EventRecord, EventStore}
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobOperationalMetricsIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E5 ON1 native synchronous snapshot purity" must _scenario("E5", "R1,R3,R4") {
    "preserve semantic Events, results, paid facts and time and ID call counts" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R3,R4; E5/ON1: a real manual engine, EventStore and counted execution clock, engine time and ID providers; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val fixture = _fixture()
      try {
        When("a synchronous Nil Job executes through the real admission and completion path")
        val jobid = _success(fixture.engine.submit(Nil, fixture.context, _sync))
        val before = _facts(fixture, jobid)
        val metrics = fixture.engine.operationalMetrics
        val repeated = (0 until 20).map(_ => fixture.engine.operationalMetrics).toVector
        val after = _facts(fixture, jobid)
        Then("repeated counter reads preserve exact Event identity/time/payload, result/status, resource/lifetime facts and all provider counts")
        after shouldBe before
        repeated shouldBe Vector.fill(20)(metrics)
        fixture.clock.calls.get() should be > 0
        fixture.time.calls.get() should be > 0
        fixture.ids.calls.get() should be > 0
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.running", "job.succeeded")
        _events(fixture).map(_.id).distinct.size shouldBe 3
        _events(fixture).map(_.createdAt) shouldBe Vector.fill(3)(_instant)
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        fixture.engine.getResult(jobid) shouldBe None
        fixture.engine.resourceSnapshot.pending shouldBe 0
        fixture.engine.resourceSnapshot.executing shouldBe 0
        fixture.engine.lifetimeSnapshot(jobid) should not be empty
        metrics shouldBe Some(_sync_points(1L))
        _assert_event_counts(fixture)
      } finally fixture.engine.shutdown()
    }
  }

  "E6 ON2 real delayed scheduling" must _scenario("E6", "R1,R3,R4") {
    "count only emitted future admission, promotion and completion facts" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R3,R4; E6/ON2: a real future asynchronous Nil Job and ManualJobTimer; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val fixture = _fixture()
      try {
        When("the asynchronous Job is admitted for ten seconds in the future")
        val jobid = _success(fixture.engine.submit(Nil, fixture.context,
          JobSubmitOption(scheduledStartAt = Some(_instant.plusSeconds(10L)))))
        val before = _facts(fixture, jobid)
        val initial = fixture.engine.operationalMetrics
        val earlytimer = fixture.timer.fireDue()
        val earlydrain = fixture.engine.drainAll()
        Then("only submitted and scheduled facts exist and snapshot reads cause no provider or timing effects")
        earlytimer shouldBe 0
        earlydrain shouldBe 0
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.delayed.scheduled")
        initial shouldBe Some(Vector(
          _point("admission", "submit", "accepted", "async"),
          _point("queue", "delay", "scheduled", "async"),
          _operation_point("submit", "accepted")
        ))
        fixture.engine.operationalMetrics shouldBe initial
        _facts(fixture, jobid) shouldBe before
        When("manual time reaches the due instant and the real engine drains the promoted Job")
        val fired = fixture.timer.advanceBy(Duration.ofSeconds(10L))
        val drained = fixture.engine.drainAll()
        val completed = _facts(fixture, jobid)
        val counters = fixture.engine.operationalMetrics
        Then("promotion, running and success each occur once with exact observed Event counts")
        fired shouldBe 1
        drained shouldBe 1
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.delayed.scheduled",
          "job.delayed.enqueued", "job.running", "job.succeeded")
        counters shouldBe Some(Vector(
          _point("admission", "submit", "accepted", "async"),
          _point("execution", "run", "started", "async"),
          _point("execution", "run", "succeeded", "async"),
          _point("queue", "delay", "scheduled", "async"),
          _point("queue", "delay", "enqueued", "async"),
          _operation_point("submit", "accepted")
        ))
        _assert_event_counts(fixture)
        When("an empty second drain and repeated snapshots observe the completed Job")
        val seconddrain = fixture.engine.drainAll()
        val repeated = fixture.engine.operationalMetrics
        Then("the second observation adds no counters, action or provider calls")
        seconddrain shouldBe 0
        repeated shouldBe counters
        _facts(fixture, jobid) shouldBe completed
      } finally fixture.engine.shutdown()
    }
  }

  "E7 ON3 native accepted controls and replay" must _scenario("E7", "R1,R3,R4") {
    "observe authorized Suspend Resume Cancel once and refuse unowned Cancel without credit" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R3,R4; E7/ON3: a real unstarted asynchronous Job and explicit permissive/refusing control policies; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      try {
        val jobid = _success(fixture.engine.submit(Nil, fixture.context))
        val before = _facts(fixture, jobid)
        val initial = fixture.engine.operationalMetrics
        When("the refusing policy receives Cancel")
        val denied = fixture.engine.control(jobid, JobControlRequest(JobControlCommand.Cancel), _deny)
        Then("refusal changes no lifecycle fact, resource or lifetime credit")
        denied.isSuccess shouldBe false
        fixture.engine.operationalMetrics.get.filter(_.scope == "job.lifecycle") shouldBe
          initial.get.filter(_.scope == "job.lifecycle")
        fixture.engine.operationalMetrics.get.filter(_.scope == "job.operation") shouldBe
          Vector(_operation_point("submit", "accepted"), _operation_point("control", "failed"))
        _facts(fixture, jobid) shouldBe before
        Vector(JobControlCommand.Suspend, JobControlCommand.Resume, JobControlCommand.Cancel).zipWithIndex.foreach { case (command, index) =>
          When(s"the permitted $command transition applies and the identical request replays")
          val applied = _success(fixture.engine.control(jobid, JobControlRequest(command), _allow))
          val accepted = fixture.engine.operationalMetrics
          val events = _events(fixture)
          val replayed = _success(fixture.engine.control(jobid, JobControlRequest(command), _allow))
          Then(s"$command applies once and Replay emits and counts no second transition")
          applied.changed shouldBe true
          replayed.changed shouldBe false
          fixture.engine.operationalMetrics.get.filter(_.scope == "job.lifecycle") shouldBe
            accepted.get.filter(_.scope == "job.lifecycle")
          fixture.engine.operationalMetrics.get.filter(_.scope == "job.operation") shouldBe Vector(
            _operation_point("submit", "accepted"),
            _operation_point("control", "applied", index.toLong + 1L),
            _operation_point("control", "replayed", index.toLong + 1L),
            _operation_point("control", "failed"))
          _events(fixture) shouldBe events
          _assert_event_counts(fixture)
        }
        Then("the native control sequence remains submitted suspended resumed cancelled")
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.suspended", "job.resumed", "job.cancelled")
        fixture.engine.operationalMetrics shouldBe Some(Vector(
          _point("admission", "submit", "accepted", "async"),
          _point("control", "cancel", "applied", "async"),
          _point("control", "suspend", "applied", "async"),
          _point("control", "resume", "applied", "async"),
          _operation_point("submit", "accepted"),
          _operation_point("control", "applied", 3L),
          _operation_point("control", "replayed", 3L),
          _operation_point("control", "failed")
        ))
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Cancelled)
      } finally fixture.engine.shutdown()
    }
  }

  "E8 ON4 exact same-State ownership" must _scenario("E8", "R4") {
    "share cumulative history across genuine owners while a fresh State starts empty" in {
      Given("docs/spec/job-operational-metrics-contract.md R4; E8/ON4: two real engine owners sharing one State and a separate fresh-State owner; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val state = InMemoryJobEngine.State()
      val first = _fixture(state, "owner-first")
      try {
        _success(first.engine.submit(Nil, first.context, _sync))
        val second = _fixture(state, "owner-second")
        try {
          val fresh = _fixture(seed = "owner-fresh")
          try {
            When("the new same-State owner snapshots, the first owner shuts down and the second submits another synchronous Job")
            val inherited = second.engine.operationalMetrics
            val empty = fresh.engine.operationalMetrics
            first.engine.shutdown()
            val afterclose = second.engine.operationalMetrics
            _success(second.engine.submit(Nil, second.context, _sync))
            Then("bootstrap and shutdown preserve exact shared history and the independent State has no reconstructed counters")
            inherited shouldBe Some(_sync_points(1L))
            afterclose shouldBe inherited
            empty shouldBe Some(Vector.empty)
            fresh.engine.operationalMetrics shouldBe empty
            first.engine.operationalMetrics shouldBe Some(_sync_points(2L))
            second.engine.operationalMetrics shouldBe first.engine.operationalMetrics
            (_events(first) ++ _events(second)).map(_.name).groupMapReduce(identity)(_ => 1)(_ + _) shouldBe
              Map("job.submitted" -> 2, "job.running" -> 2, "job.succeeded" -> 2)
          } finally fresh.engine.shutdown()
        } finally second.engine.shutdown()
      } finally first.engine.shutdown()
    }
  }

  "E9 ON5 actual immediate retry execution" must _scenario("E9", "R1,R3") {
    "count real RetryNow and two actual body calls without a fabricated fact hook" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R3; E9/ON5: a real JobTask returning Conclusion.simple with RetryNow once, then success; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val fixture = _fixture()
      val task = new CountingTask(retryonce = true)
      try {
        When("the real manual engine admits the asynchronous task and drains both attempts")
        val jobid = _success(fixture.engine.submit(List(task), fixture.context))
        val drained = fixture.engine.drainAll()
        Then("two real body calls emit exactly submitted running retry submitted running succeeded facts")
        drained shouldBe 2
        task.calls.get() shouldBe 2
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.running",
          "job.retry.immediate.submitted", "job.running", "job.succeeded")
        fixture.engine.operationalMetrics shouldBe Some(Vector(
          _point("admission", "submit", "accepted", "async"),
          _point("execution", "run", "started", "async", count = 2L),
          _point("execution", "run", "succeeded", "async"),
          _point("retry", "immediate", "submitted", "async"),
          _operation_point("submit", "accepted")
        ))
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        fixture.engine.query(jobid).get.retry.attemptCount shouldBe 1
        _assert_event_counts(fixture)
      } finally fixture.engine.shutdown()
    }
  }

  "E10 ON6 closed facets protect diagnostic data" must _scenario("E10", "R1,R3,R5") {
    "preserve authorized Job and Event metadata while counters contain only closed labels" in {
      Given("docs/spec/job-operational-metrics-contract.md R1,R3,R5; E10/ON6: real Job/context markers for secret tenant subject job task trace span parameters notes summary payload path and provider; additive docs/spec/job-operation-metrics-contract.md R1,R3,R4,R5,R6")
      val fixture = _fixture()
      val principal = new Principal {
        def id: PrincipalId = PrincipalId("PRIVATE-SUBJECT")
        def attributes: Map[String, String] = Map("tenant" -> "PRIVATE-TENANT", "credential" -> "PRIVATE-SECRET")
      }
      val secured = ExecutionContext.withSecurityContext(fixture.context,
        fixture.context.security.copy(principal = principal))
      val observed = ExecutionContext.withObservabilityContext(secured, secured.observability.copy(
        traceId = TraceId("PRIVATE_TRACE", "diagnostic"),
        spanId = Some(SpanId("PRIVATE_SPAN", "diagnostic", "task")),
        correlationId = Some(CorrelationId("PRIVATE_CORRELATION", "diagnostic"))))
      val context = ExecutionContext.withJobContext(observed, observed.jobContext.copy(
        parentJobId = Some(JobId("private", "parent", Some(_instant), Some("PRIVATE-JOB"))),
        traceMetadata = Map("tenant" -> "PRIVATE-TENANT", "provider" -> "PRIVATE-PROVIDER")))
      val task = new CountingTask(marker = "PRIVATE-PAYLOAD")
      val parameters = Map("secret" -> "PRIVATE-SECRET", "tenant" -> "PRIVATE-TENANT",
        "provider" -> "PRIVATE-PROVIDER", "path" -> "/PRIVATE-PATH", "web.marker" -> "PRIVATE-PARAMETER")
      val option = _sync.copy(parameters = parameters, requestSummary = Some("PRIVATE-SUMMARY"),
        executionNotes = Vector("PRIVATE-NOTE"))
      try {
        When("the actual marked Job executes and its operational snapshot is read repeatedly")
        val jobid = _success(fixture.engine.submit(List(task), context, option))
        val model = fixture.engine.query(jobid).get
        val events = _events(fixture)
        val points = fixture.engine.operationalMetrics.get
        val repeated = fixture.engine.operationalMetrics
        Then("all metric names and scope-specific five lifecycle or two operation labels remain closed while original authorized read-model and semantic Event facts survive")
        repeated shouldBe Some(points)
        points shouldBe _sync_points(1L)
        points.foreach { point =>
          point.scope match {
            case "job.lifecycle" =>
              point.name shouldBe "facts"
              point.labels.keySet shouldBe Set("family", "operation", "outcome", "persistence", "run_mode")
            case "job.operation" =>
              point.name shouldBe "calls"
              point.labels.keySet shouldBe Set("operation", "outcome")
            case other => fail(s"unexpected metric scope $other")
          }
          Vector(point.scope, point.name).foreach(_ should not include "PRIVATE")
          point.labels.foreach { case (key, value) =>
            key should not include "PRIVATE"
            value should not include "PRIVATE"
          }
        }
        model.debug.parameters.toSeq should contain allElementsOf parameters.toSeq
        model.debug.executionNotes should contain("PRIVATE-NOTE")
        model.submitter.principalId shouldBe "PRIVATE-SUBJECT"
        fixture.engine.getResponse(jobid) shouldBe Some(OperationResponse.Scalar("PRIVATE-PAYLOAD"))
        task.calls.get() shouldBe 1
        events.foreach { event =>
          event.payload("submitter-principal-id") shouldBe "PRIVATE-SUBJECT"
          event.payload("web.marker") shouldBe "PRIVATE-PARAMETER"
          event.attributes("cncf.job.id") shouldBe jobid.value
          event.attributes("correlation-id") shouldBe context.observability.correlationId.get.print
        }
        fixture.engine.query(jobid) shouldBe Some(model)
        _events(fixture) shouldBe events
        _assert_event_counts(fixture)
      } finally fixture.engine.shutdown()
    }
  }

  private val _instant = Instant.parse("2026-10-03T00:00:00Z")
  private val _sync = JobSubmitOption(runMode = JobRunMode.Sync)
  private val _allow = new JobControlPolicy {
    def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.unit
    }
  }
  private val _deny = new JobControlPolicy {
    def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.operationInvalid("control refused")
    }
  }

  private final class CountingClock extends Clock {
    val calls = new AtomicInteger(0)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = Clock.fixed(_instant, zone)
    def instant(): Instant = {
      calls.incrementAndGet()
      _instant
    }
  }

  private final class CountingIds(delegate: IdGenerationContext) extends IdGenerationContext {
    val calls = new AtomicInteger(0)
    def namespace: IdGenerationContext.IdNamespace = delegate.namespace
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
      calls.incrementAndGet()
      delegate.entityId(collection, purpose)
    }
    def opaqueId(purpose: String): String = {
      calls.incrementAndGet()
      delegate.opaqueId(purpose)
    }
  }

  private final class CountingTime(delegate: ManualJobTimeSource) extends JobTimeSource {
    val calls = new AtomicInteger(0)
    def now(): Instant = {
      calls.incrementAndGet()
      delegate.now()
    }
  }

  private final class CountingTask(retryonce: Boolean = false, marker: String = "completed") extends JobTask {
    val calls = new AtomicInteger(0)
    val actionId: ActionId = ActionId("private", "task", Some(_instant), Some("PRIVATE-TASK"))
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def componentName: Option[String] = Some("PRIVATE-PROVIDER")
    override def operationName: Option[String] = Some("run")
    override def relation: Option[String] = Some("root")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val count = calls.incrementAndGet()
      if (retryonce && count == 1) TaskFailed(Conclusion.simple("retry now").copy(
        disposition = Disposition(Disposition.UserAction.RetryNow)))
      else TaskSucceeded(OperationResponse.Scalar(marker))
    }
  }

  private final case class Fixture(
    engine: InMemoryJobEngine,
    context: ExecutionContext,
    events: EventStore,
    timer: InMemoryJobEngine.ManualJobTimer,
    clock: CountingClock,
    ids: CountingIds,
    time: CountingTime
  )

  private def _fixture(state: InMemoryJobEngine.State = InMemoryJobEngine.State(), seed: String = "operational"): Fixture = {
    val clock = new CountingClock
    val ids = new CountingIds(IdGenerationContext.deterministic(
      IdGenerationContext.IdNamespace("submitted", "job_event"), clock, seed))
    val context = ExecutionContext.withIdGenerationContext(ExecutionContext.create(clock), ids)
    val manual = new ManualJobTimeSource(_instant)
    val time = new CountingTime(manual)
    val timer = new InMemoryJobEngine.ManualJobTimer(manual)
    val events = EventStore.inMemory
    val engine = new InMemoryJobEngine(state,
      schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false), timeSource = time,
      timer = Some(timer))(scala.concurrent.ExecutionContext.global).withEventStore(events)
    Fixture(engine, context, events, timer, clock, ids, time)
  }

  private def _facts(fixture: Fixture, jobid: JobId) = (
    fixture.engine._get_record(jobid), fixture.engine.query(jobid), fixture.engine.getStatus(jobid),
    fixture.engine.getResult(jobid), fixture.engine.resourceSnapshot, fixture.engine.lifetimeSnapshot(jobid),
    _events(fixture), fixture.time.calls.get(), fixture.clock.calls.get(), fixture.ids.calls.get(), fixture.timer.pendingCount
  )

  private def _events(fixture: Fixture): Vector[EventRecord] =
    _success(fixture.events.query(EventStore.Query()))

  private def _assert_event_counts(fixture: Fixture): Unit = {
    val events = _events(fixture)
    val points = fixture.engine.operationalMetrics.get.filter(_.scope == "job.lifecycle")
    points.foreach { point =>
      val name = (point.labels("family"), point.labels("operation"), point.labels("outcome")) match {
        case ("admission", "submit", "accepted") => "job.submitted"
        case ("execution", "run", "started") => "job.running"
        case ("execution", "run", "succeeded") => "job.succeeded"
        case ("execution", "run", "failed") => "job.failed"
        case ("queue", "delay", "scheduled") => "job.delayed.scheduled"
        case ("queue", "delay", "enqueued") => "job.delayed.enqueued"
        case ("retry", "immediate", "submitted") => "job.retry.immediate.submitted"
        case ("control", "cancel", "applied") => "job.cancelled"
        case ("control", "suspend", "applied") => "job.suspended"
        case ("control", "resume", "applied") => "job.resumed"
        case other => fail(s"unexpected observed lifecycle facet $other")
      }
      point.count shouldBe events.count(_.name == name).toLong
    }
    points.map(_.count).sum shouldBe events.size.toLong
  }

  private def _point(family: String, operation: String, outcome: String, mode: String, count: Long = 1L): RuntimeMetricPoint =
    RuntimeMetricPoint("job.lifecycle", "facts", Map("family" -> family, "operation" -> operation,
      "outcome" -> outcome, "persistence" -> "persistent", "run_mode" -> mode), count)

  private def _sync_points(count: Long): Vector[RuntimeMetricPoint] = Vector(
    _point("admission", "submit", "accepted", "sync", count),
    _point("execution", "run", "started", "sync", count),
    _point("execution", "run", "succeeded", "sync", count),
    _operation_point("submit", "accepted", count)
  )

  private def _operation_point(operation: String, outcome: String, count: Long = 1L): RuntimeMetricPoint =
    RuntimeMetricPoint("job.operation", "calls", Map("operation" -> operation, "outcome" -> outcome),
      count, errorCount = if (outcome == "failed") count else 0L)

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operational-metrics, example:$example, rules:$rules, phase:69.7, additive-spec:job-operation-metrics, additive-rules:R1,R3,R4,R5")
}
