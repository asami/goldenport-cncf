package org.goldenport.cncf.job

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext, Principal, PrincipalId}
import org.goldenport.cncf.event.{EventRecord, EventStore}
import org.goldenport.cncf.metrics.RuntimeMetricPoint
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobOperationMetricsIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E4 PN1 native overload accounting" must _scenario("E4", "R1,R3,R4") {
    "count two public submissions once and preserve actual Nil results and facts" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R3,R4; E4/PN1: a manual engine, genuine EventStore and counted deterministic clock, time and IDs")
      val fixture = _fixture()
      try {
        When("a two-argument Async Nil Job drains and an explicit three-argument Sync Nil Job completes")
        val first = _success(fixture.engine.submit(Nil, fixture.context))
        fixture.engine.drainAll() shouldBe 1
        val second = _success(fixture.engine.submit(Nil, fixture.context, _sync))
        val before = _facts(fixture)
        val points = _operations(fixture)
        val repeated = (0 until 20).map(_ => _operations(fixture)).toVector
        Then("exactly two accepted public calls accompany three original Events per Job and no observation changes providers or paid facts")
        points shouldBe Vector(_point("submit", "accepted", 2L))
        repeated shouldBe Vector.fill(20)(points)
        _facts(fixture) shouldBe before
        Vector(first, second).foreach { jobid =>
          fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
          fixture.engine.getResult(jobid) shouldBe None
          fixture.engine.lifetimeSnapshot(jobid) should not be empty
        }
        _events(fixture).map(_.name) shouldBe Vector.fill(2)(Vector("job.submitted", "job.running", "job.succeeded")).flatten
        _events(fixture).map(_.id).distinct.size shouldBe 6
        _events(fixture).map(_.createdAt) shouldBe Vector.fill(6)(_instant)
        fixture.engine.resourceSnapshot.pending shouldBe 0
        fixture.engine.resourceSnapshot.executing shouldBe 0
        fixture.clock.calls.get() should be > 0
        fixture.ids.calls.get() should be > 0
        fixture.time.calls.get() should be > 0
        _assert_lifecycle(fixture)
      } finally fixture.engine.shutdown()
    }
  }

  "E5 PN2 original pure submission refusal" must _scenario("E5", "R1,R3,R5") {
    "retain structured preflight failures without admission effects" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R3,R5; E5/PN2: a fresh actual manual engine and its empty record, Event, resource and provider baseline")
      val fixture = _fixture()
      try {
        val before = _facts(fixture)
        When("null task list, null explicit option, null input-option and two-argument submission after quiesce reach original refusals")
        val tasks = fixture.engine.submit(null, fixture.context)
        val option = fixture.engine.submit(Nil, fixture.context, null)
        val input = fixture.engine.submit(Nil, fixture.context, _sync.copy(input = null))
        fixture.engine.quiesce()
        val closed = fixture.engine.submit(Nil, fixture.context)
        Then("four normally returned original failures count once each while records, Events, paid credits, clock, IDs and timers remain unchanged")
        Vector(tasks, option, input, closed).foreach { result =>
          result shouldBe a[Consequence.Failure[_]]
          _failure(result).show should not be empty
        }
        val invalid = _failure(JobInputResourcePolicy.invalid[JobId])
        Vector(option, input).foreach { result =>
          val actual = _failure(result)
          actual.copy(observation = actual.observation.copy(timestamp = invalid.observation.timestamp)) shouldBe invalid
        }
        _operations(fixture) shouldBe Vector(_point("submit", "failed", 4L))
        _facts(fixture) shouldBe before
        _events(fixture) shouldBe Vector.empty
        fixture.engine.listJobs(persistentOnly = false) shouldBe Vector.empty
        fixture.timer.pendingCount shouldBe 0
      } finally fixture.engine.shutdown()
    }
  }

  "E6 PN3 policy precedence and control replay" must _scenario("E6", "R1,R3,R5") {
    "count actual applied replayed and refused returns without changing native transitions" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R3,R5; E6/PN3: an actual unstarted Async Nil Job and counted refusing, permissive and throwing policies")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      try {
        val jobid = _success(fixture.engine.submit(Nil, fixture.context))
        val refusal = Consequence.operationInvalid[Unit]("PRIVATE-POLICY-REFUSAL")
        val policycalls = new AtomicInteger(0)
        val denying = new JobControlPolicy {
          def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = {
            jobId shouldBe null
            request shouldBe null
            policycalls.incrementAndGet()
            refusal
          }
        }
        val before = _facts(fixture)
        When("the refusing policy receives null identity and request before shape or lookup, then permitted controls apply and replay")
        val denied = fixture.engine.control(null, null, denying)
        _failure(denied) shouldBe _failure(refusal)
        policycalls.get() shouldBe 1
        _facts(fixture) shouldBe before
        Vector(JobControlCommand.Suspend, JobControlCommand.Resume, JobControlCommand.Cancel)
          .zipWithIndex.foreach { case (command, index) =>
            val applied = _success(fixture.engine.control(jobid, JobControlRequest(command), _allow))
            val accepted = _facts(fixture)
            val replayed = _success(fixture.engine.control(jobid, JobControlRequest(command), _allow))
            applied.changed shouldBe true
            replayed.changed shouldBe false
            _facts(fixture) shouldBe accepted
            _operations(fixture) shouldBe Vector(_point("submit", "accepted"),
              _point("control", "applied", index.toLong + 1L),
              _point("control", "replayed", index.toLong + 1L), _point("control", "failed"))
          }
        val completed = _operations(fixture)
        val sentinel = new IllegalStateException("PRIVATE-POLICY-THROW")
        val throwing = new JobControlPolicy {
          def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = {
            val _ = jobId
            val _ = request
            throw sentinel
          }
        }
        val thrown = intercept[IllegalStateException](fixture.engine.control(null, null, throwing))
        Then("three applications and three replays retain the original four Events and the identical thrown sentinel contributes no completed call")
        (thrown eq sentinel) shouldBe true
        _operations(fixture) shouldBe completed
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.suspended", "job.resumed", "job.cancelled")
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Cancelled)
        _assert_lifecycle(fixture)
      } finally fixture.engine.shutdown()
    }
  }

  "E7 PN4 failed result after native retry effects" must _scenario("E7", "R1,R3") {
    "record the actual Sync Retry timeout as failed while retaining its queued native transition" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R3; E7/PN4: an actual manual submitted Nil Job, permissive control policy and zero-timeout Sync Retry")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      try {
        val jobid = _success(fixture.engine.submit(Nil, fixture.context))
        When("Cancel applies asynchronously and Retry applies its native transition before its synchronous wait times out")
        val cancelled = _success(fixture.engine.control(jobid, JobControlRequest(JobControlCommand.Cancel), _allow))
        val paidbefore = fixture.engine.lifetimeSnapshot(jobid)
        val retry = fixture.engine.control(jobid, JobControlRequest(JobControlCommand.Retry,
          JobControlOption(mode = JobCommandMode.Sync, timeoutMillis = 0L)), _allow)
        val queued = _facts(fixture)
        val points = _operations(fixture)
        Then("the final Retry failure counts failed rather than a second application even though Submitted state, paid facts and retry Event exist")
        cancelled.changed shouldBe true
        retry shouldBe a[Consequence.Failure[_]]
        _failure(retry).show should include("timeout")
        points shouldBe Vector(_point("submit", "accepted"), _point("control", "applied"), _point("control", "failed"))
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.engine.lifetimeSnapshot(jobid) should not be empty
        fixture.engine.lifetimeSnapshot(jobid) should not be paidbefore
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.cancelled", "job.retry.submitted")
        (0 until 20).foreach(_ => _operations(fixture) shouldBe points)
        _facts(fixture) shouldBe queued
        When("the real manual engine drains the queued retry")
        val drained = fixture.engine.drainAll()
        Then("completion adds original lifecycle Events and no public operation cells")
        drained shouldBe 1
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        _events(fixture).map(_.name) shouldBe Vector("job.submitted", "job.cancelled", "job.retry.submitted", "job.running", "job.succeeded")
        _operations(fixture) shouldBe points
        fixture.engine.resourceSnapshot.pending shouldBe 0
        _assert_lifecycle(fixture)
      } finally fixture.engine.shutdown()
    }
  }

  "E8 PN5 actual pagination and lazy validation" must _scenario("E8", "R1,R3") {
    "retain cursor and page metadata and refuse invalid shapes before policy observations" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R3; E8/PN5: two genuine completed Sync Nil records and a counted permissive read policy")
      val fixture = _fixture()
      given ExecutionContext = fixture.context
      try {
        val ids = Vector.fill(2)(_success(fixture.engine.submit(Nil, fixture.context, _sync)))
        val policy = new CountingReadPolicy(false)
        val before = _facts(fixture)
        When("limit-one page and real continuation, valid task/timeline pages and missing identities are read alongside invalid shapes")
        val first = _success(fixture.engine.queryPage(JobManagementQuery(limit = 1), policy))
        val second = _success(fixture.engine.queryPage(JobManagementQuery(limit = 1, cursor = first.nextCursor), policy))
        val beforeinvalid = policy.calls.get()
        val invalidpages = Vector(fixture.engine.queryPage(null, policy),
          fixture.engine.queryPage(JobManagementQuery(limit = 101), policy),
          fixture.engine.queryPage(JobManagementQuery(cursor = Some(JobManagementCursor.fromOpaque("PRIVATE-BAD-CURSOR"))), policy))
        invalidpages.foreach(_ shouldBe a[Consequence.Failure[_]])
        policy.calls.get() shouldBe beforeinvalid
        val tasks = _success(fixture.engine.queryManagementTasks(ids.head, limit = 1, policy = policy)).get
        val timeline = _success(fixture.engine.queryManagementTimeline(ids.head, limit = 2, policy = policy)).get
        val beforedetails = policy.calls.get()
        fixture.engine.queryManagementTasks(ids.head, offset = -1, policy = policy) shouldBe a[Consequence.Failure[_]]
        fixture.engine.queryManagementTasks(ids.head, limit = 101, policy = policy) shouldBe a[Consequence.Failure[_]]
        fixture.engine.queryManagementTimeline(ids.head, offset = -1, policy = policy) shouldBe a[Consequence.Failure[_]]
        fixture.engine.queryManagementTimeline(ids.head, limit = 101, policy = policy) shouldBe a[Consequence.Failure[_]]
        policy.calls.get() shouldBe beforedetails
        val missing = JobId("missing", "PRIVATE_IDENTITY")
        _success(fixture.engine.queryManagementTasks(missing, policy = policy)) shouldBe None
        _success(fixture.engine.queryManagementTimeline(missing, policy = policy)) shouldBe None
        Then("ordered IDs, total counts, cursor end and actual empty-task/real-timeline metadata survive with exact three public reader facets")
        (first.entries ++ second.entries).map(_.jobId) shouldBe ids.sortBy(_.print)
        first.totalCount shouldBe 2
        second.totalCount shouldBe 2
        first.nextCursor should not be empty
        second.nextCursor shouldBe None
        tasks.offset shouldBe 0
        tasks.limit shouldBe 1
        tasks.totalCount shouldBe 0
        tasks.fetchedCount shouldBe 0
        tasks.tasks shouldBe Vector.empty
        timeline.offset shouldBe 0
        timeline.limit shouldBe 2
        timeline.totalCount shouldBe fixture.engine.queryTimeline(ids.head).get.totalCount
        timeline.events shouldBe fixture.engine.queryTimeline(ids.head, limit = 2).get.events
        timeline.fetchedCount shouldBe 2
        policy.calls.get() shouldBe 6
        _operations(fixture) shouldBe Vector(_point("submit", "accepted", 2L),
          _point("page", "returned", 2L), _point("page", "failed", 3L),
          _point("tasks", "returned", 2L), _point("tasks", "failed", 2L),
          _point("timeline", "returned", 2L), _point("timeline", "failed", 2L))
        _assert_read_purity(fixture, before)
      } finally fixture.engine.shutdown()
    }
  }

  "E9 PN6 owner visibility and successful hidden results" must _scenario("E9", "R1,R3,R5") {
    "count normal empty and None results as returned without disclosing protected facets" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R3,R5; E9/PN6: actual PRIVATE owner/foreign contexts, default ownership policy and a counted denying policy")
      val fixture = _fixture()
      val owner = _context(fixture.context, "PRIVATE-OWNER")
      val foreign = _context(fixture.context, "PRIVATE-FOREIGN")
      try {
        val ownerid = _success(fixture.engine.submit(Nil, owner, _sync))
        val foreignid = _success(fixture.engine.submit(Nil, foreign, _sync))
        val policy = new CountingReadPolicy(true)
        val before = _facts(fixture)
        When("default owner-only and explicitly denied reads observe existing foreign and missing records")
        val visible = _success(fixture.engine.queryPage(JobManagementQuery())(using owner))
        val hidden = _success(fixture.engine.queryPage(JobManagementQuery(), policy)(using owner))
        val missing = JobId("missing", "PRIVATE_MISSING")
        val tasks = Vector(foreignid, missing).map(jobid =>
          _success(fixture.engine.queryManagementTasks(jobid, policy = policy)(using owner)))
        val timeline = Vector(foreignid, missing).map(jobid =>
          _success(fixture.engine.queryManagementTimeline(jobid, policy = policy)(using owner)))
        Then("the owner sees only its Job, denied pages remain empty and None remains successful with zero failed counters and no metric data leak")
        visible.entries.map(_.jobId) shouldBe Vector(ownerid)
        visible.totalCount shouldBe 1
        hidden.entries shouldBe Vector.empty
        hidden.totalCount shouldBe 0
        hidden.nextCursor shouldBe None
        tasks shouldBe Vector(None, None)
        timeline shouldBe Vector(None, None)
        policy.calls.get() shouldBe 4
        policy.subjects.toVector shouldBe Vector.fill(4)("PRIVATE-OWNER")
        policy.identities.take(2).toSet shouldBe Set(ownerid, foreignid)
        policy.identities.drop(2).toVector shouldBe Vector(foreignid, foreignid)
        fixture.engine.query(ownerid).get.submitter.principalId shouldBe "PRIVATE-OWNER"
        fixture.engine.query(foreignid).get.submitter.principalId shouldBe "PRIVATE-FOREIGN"
        val points = _operations(fixture)
        points shouldBe Vector(_point("submit", "accepted", 2L), _point("page", "returned", 2L),
          _point("tasks", "returned", 2L), _point("timeline", "returned", 2L))
        points.foreach { point =>
          point.errorCount shouldBe 0L
          Vector(point.scope, point.name).foreach(_ should not include "PRIVATE")
          point.labels.foreach { case (key, value) =>
            key should not include "PRIVATE"
            value should not include "PRIVATE"
          }
        }
        _assert_read_purity(fixture, before)
      } finally fixture.engine.shutdown()
    }
  }

  "E10 PN7 exact same-State operation lifetime" must _scenario("E10", "R4") {
    "share completed public history across actual owners and preserve fresh zero" in {
      Given("docs/spec/job-operation-metrics-contract.md R4; E10/PN7: two real owners of one exact State, separate deterministic seeds and one fresh-State owner")
      val state = InMemoryJobEngine.State()
      val first = _fixture(state, "first")
      try {
        _success(first.engine.submit(Nil, first.context, _sync))
        _success(first.engine.queryPage(JobManagementQuery())(using first.context))
        val second = _fixture(state, "second")
        try {
          val fresh = _fixture(seed = "fresh")
          try {
            When("the first owner closes and the same-State second owner completes another submission and selected public read")
            val inherited = _operations(second)
            first.engine.shutdown()
            val afterclose = _operations(second)
            _success(second.engine.submit(Nil, second.context, _sync))
            _success(second.engine.queryManagementTasks(JobId("missing", "job"))(using second.context))
            Then("no bootstrap or shutdown resets shared call history and an independent State has no reconstructed points")
            inherited shouldBe Vector(_point("submit", "accepted"), _point("page", "returned"))
            afterclose shouldBe inherited
            _operations(second) shouldBe Vector(_point("submit", "accepted", 2L),
              _point("page", "returned"), _point("tasks", "returned"))
            _operations(first) shouldBe _operations(second)
            fresh.engine.operationalMetrics shouldBe Some(Vector.empty)
            (_events(first) ++ _events(second)).map(_.name).groupMapReduce(identity)(_ => 1)(_ + _) shouldBe
              Map("job.submitted" -> 2, "job.running" -> 2, "job.succeeded" -> 2)
          } finally fresh.engine.shutdown()
        } finally second.engine.shutdown()
      } finally first.engine.shutdown()
    }
  }

  "E11 PN8 shrinking real public action histories" must _scenario("E11", "R1,R2,R3,R4,R5") {
    "match every actual native result against an independent returned-outcome multiset" in {
      Given("docs/spec/job-operation-metrics-contract.md R1,R2,R3,R4,R5; E11/PN8: finite one-to-twelve real public actions, genuine manual fixtures and independent multiset, seed 697097201")
      val sequences = Gen.chooseNum(1, 12).flatMap(size => Gen.listOfN(size, Gen.chooseNum(0, 8)))
      val property = Prop.forAll(sequences) { actions =>
        val fixture = _fixture(seed = "property")
        given ExecutionContext = fixture.context
        var latest = Option.empty[JobId]
        val observations = scala.collection.mutable.ArrayBuffer.empty[(String, String)]
        try {
          actions.foreach { action =>
            val jobid = latest.getOrElse(JobId("missing", "job"))
            action match {
              case 0 | 1 =>
                val result = if (action == 0) fixture.engine.submit(Nil, fixture.context, _sync)
                  else fixture.engine.submit(Nil, fixture.context)
                result.isSuccess shouldBe true
                latest = Some(_success(result))
                fixture.engine.getStatus(latest.get) shouldBe
                  Some(if (action == 0) JobStatus.Succeeded else JobStatus.Submitted)
                fixture.engine.getResult(latest.get) shouldBe None
                observations += "submit" -> "accepted"
              case 2 =>
                val result = fixture.engine.submit(null, fixture.context)
                result shouldBe a[Consequence.Failure[_]]
                observations += "submit" -> "failed"
              case 3 | 4 | 8 =>
                val query = action match {
                  case 3 => JobManagementQuery()
                  case 4 => JobManagementQuery(limit = 0)
                  case _ => JobManagementQuery(cursor = Some(JobManagementCursor.fromOpaque("PRIVATE-MALFORMED")))
                }
                val result = fixture.engine.queryPage(query)
                result.isSuccess shouldBe (action == 3)
                if (action == 3) _success(result).totalCount shouldBe fixture.engine.listJobs().size
                observations += "page" -> (if (result.isSuccess) "returned" else "failed")
              case 5 =>
                val result = fixture.engine.queryManagementTasks(jobid)
                result.isSuccess shouldBe true
                _success(result).isDefined shouldBe latest.isDefined
                _success(result).foreach(_.tasks shouldBe Vector.empty)
                observations += "tasks" -> "returned"
              case 6 =>
                val result = fixture.engine.queryManagementTimeline(jobid, offset = -1)
                result shouldBe a[Consequence.Failure[_]]
                observations += "timeline" -> "failed"
              case _ =>
                val prior = fixture.engine.getStatus(jobid)
                val applicable = prior.exists(status => status == JobStatus.Submitted ||
                  status == JobStatus.Running || status == JobStatus.Suspended || status == JobStatus.Cancelled)
                val result = fixture.engine.control(jobid, JobControlRequest(JobControlCommand.Cancel), _allow)
                result.isSuccess shouldBe applicable
                result match {
                  case Consequence.Success(response) =>
                    response.status shouldBe JobStatus.Cancelled
                    response.changed shouldBe !prior.contains(JobStatus.Cancelled)
                    observations += "control" -> (if (response.changed) "applied" else "replayed")
                  case Consequence.Failure(_) => observations += "control" -> "failed"
                }
            }
            _operations(fixture) shouldBe _expected(observations.toVector)
            _operations(fixture).map(_.count).sum shouldBe observations.size.toLong
            _assert_lifecycle(fixture)
            _assert_read_purity(fixture, _facts(fixture))
          }
          _operations(fixture).size <= 11
        } finally fixture.engine.shutdown()
      }
      When("ScalaCheck executes at least twenty-four ordinary shrinking native histories with every actual return checked")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(697097201L)), property)
      Then("all actual return domains, closed cell counts, Event conservation and pure reads match independently")
      withClue(s"PN8 seed 697097201: $checked ") {
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }
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
  private val _oracle = Vector("submit" -> "accepted", "submit" -> "failed",
    "control" -> "applied", "control" -> "replayed", "control" -> "failed",
    "page" -> "returned", "page" -> "failed", "tasks" -> "returned", "tasks" -> "failed",
    "timeline" -> "returned", "timeline" -> "failed")

  private final class CountingReadPolicy(deny: Boolean) extends JobQueryPolicy {
    val calls = new AtomicInteger(0)
    val subjects = scala.collection.mutable.ArrayBuffer.empty[String]
    val identities = scala.collection.mutable.ArrayBuffer.empty[JobId]
    def authorizeRead(model: JobQueryReadModel)(using ctx: ExecutionContext): Consequence[Unit] = {
      calls.incrementAndGet()
      subjects += ctx.security.principal.id.value
      identities += model.jobId
      if (deny) Consequence.operationInvalid("PRIVATE-READ-REFUSAL") else Consequence.unit
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

  private final case class Fixture(engine: InMemoryJobEngine, context: ExecutionContext,
    events: EventStore, timer: InMemoryJobEngine.ManualJobTimer,
    clock: CountingClock, ids: CountingIds, time: CountingTime)

  private def _fixture(state: InMemoryJobEngine.State = InMemoryJobEngine.State(), seed: String = "operation"): Fixture = {
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

  private def _context(base: ExecutionContext, subject: String): ExecutionContext = {
    val principal = new Principal {
      def id: PrincipalId = PrincipalId(subject)
      def attributes: Map[String, String] = Map("tenant" -> "PRIVATE-TENANT", "secret" -> "PRIVATE-SECRET")
    }
    ExecutionContext.withSecurityContext(base,
      base.security.copy(principal = principal, capabilities = Set.empty, session = None))
  }

  private def _events(fixture: Fixture): Vector[EventRecord] =
    _success(fixture.events.query(EventStore.Query()))

  private def _facts(fixture: Fixture) = {
    val models = fixture.engine.listJobs(persistentOnly = false).sortBy(_.jobId.print)
    (models, models.map(model => fixture.engine._get_record(model.jobId)),
      models.map(model => fixture.engine.getResult(model.jobId)),
      models.map(model => fixture.engine.lifetimeSnapshot(model.jobId)),
      fixture.engine.resourceSnapshot, _events(fixture), fixture.timer.pendingCount,
      fixture.clock.calls.get(), fixture.ids.calls.get(), fixture.time.calls.get())
  }

  private def _operations(fixture: Fixture): Vector[RuntimeMetricPoint] =
    fixture.engine.operationalMetrics.get.filter(_.scope == "job.operation")

  private def _point(operation: String, outcome: String, count: Long = 1L): RuntimeMetricPoint =
    RuntimeMetricPoint("job.operation", "calls", Map("operation" -> operation, "outcome" -> outcome),
      count, errorCount = if (outcome == "failed") count else 0L)

  private def _expected(observations: Vector[(String, String)]): Vector[RuntimeMetricPoint] =
    _oracle.flatMap { case (operation, outcome) =>
      val count = observations.count(_ == ((operation, outcome))).toLong
      Option.when(count > 0L)(_point(operation, outcome, count))
    }

  private def _assert_read_purity(fixture: Fixture, before: Any): Unit = {
    val points = fixture.engine.operationalMetrics
    (0 until 20).foreach(_ => fixture.engine.operationalMetrics shouldBe points)
    _facts(fixture) shouldBe before
  }

  private def _assert_lifecycle(fixture: Fixture): Unit = {
    val events = _events(fixture)
    val points = fixture.engine.operationalMetrics.get.filter(_.scope == "job.lifecycle")
    val names = Map(("admission", "submit", "accepted") -> "job.submitted",
      ("execution", "run", "started") -> "job.running", ("execution", "run", "succeeded") -> "job.succeeded",
      ("control", "cancel", "applied") -> "job.cancelled", ("control", "suspend", "applied") -> "job.suspended",
      ("control", "resume", "applied") -> "job.resumed", ("retry", "control", "submitted") -> "job.retry.submitted")
    points.foreach { point =>
      val name = names((point.labels("family"), point.labels("operation"), point.labels("outcome")))
      point.count shouldBe events.count(event => event.name == name &&
        event.payload.get("job-run-mode").contains(point.labels("run_mode")) &&
        event.payload.get("job-persistence").contains(point.labels("persistence"))).toLong
    }
    points.map(_.count).sum shouldBe events.size.toLong
  }

  private def _failure[A](result: Consequence[A]): org.goldenport.Conclusion = result match {
    case Consequence.Failure(conclusion) => conclusion
    case _ => fail("expected original structured failure")
  }

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operation-metrics, example:$example, rules:$rules, phase:69.7")
}
