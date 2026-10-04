package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed

/*
 * @since   Oct. 1, 2026
 * @version Oct. 3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobInputRecoveryResourceBoundsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobInputBoundsFixtures.*

  "Recovered runtime input (spec:job-input-resource-contract, phase:69.7, slice:JM69-09B3B3C1)" should {
    "IR1 refuse complete preseed input scans before budget seed, owner, clock or timers" in {
      Given("valid prefix records followed by invalid input in either map and any retained lifecycle status")
      val invalids: Vector[Option[JobInput]] = Vector(null, Some(null),
        Some(input(Vector(descriptor, null))),
        Some(input(Vector(descriptor, descriptor.copy(inlineBase64 = Some(encoded(8388609)))))),
        Some(input(Vector(descriptor, descriptor.copy(filename = Some("x" * 1025))))))
      for (durable <- Vector(true, false);
           status <- Vector(JobStatus.Submitted, JobStatus.Succeeded, JobStatus.Failed, JobStatus.Cancelled);
           value <- invalids) {
        val state = InMemoryJobEngine.State()
        val clock = new CountingClock
        val timer = new CountingTimer(clock)
        val task = new CountingTask
        val context = ExecutionContext.create(clock)
        val target = if (durable) state.durableJobs else state.runtimeJobs
        val prefix = _live_record(id("prefix"), task, context, Some(input()))
        val invalid = _live_record(id("invalid"), task, context, value).copy(status = status)
        target.put(prefix.id, prefix)
        target.put(invalid.id, invalid)
        val opposite = if (durable) state.runtimeJobs else state.durableJobs
        val other = _live_record(id("other"), task, context, Some(input()))
        opposite.put(other.id, other)
        val before = state._resource_budget.snapshot
        val clocks = clock.calls.get()
        val getters = task.getters.get()
        When("actual construction attaches a State containing an invalid complete-map value")
        val thrown = intercept[IllegalArgumentException] {
          new InMemoryJobEngine(runtimeState = state,
            schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
            timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
        }
        Then("bootstrap refuses without mutating either map, prior accounting or any callback")
        thrown.getMessage shouldBe "JobEngine resource bootstrap refused"
        target.size() shouldBe 2
        target.get(prefix.id) shouldBe prefix
        target.get(invalid.id) shouldBe invalid
        opposite.size() shouldBe 1
        opposite.get(other.id) shouldBe other
        state._resource_budget.snapshot shouldBe before
        clock.calls.get() shouldBe clocks
        task.getters.get() shouldBe getters
        task.runs.get() shouldBe 0
        timer.calls.get() shouldBe 0
        timer.pendingCount shouldBe 0
        state.bindResourcePolicy(JobResourcePolicy.default).isSuccess shouldBe true
      }
      for (durable <- Vector(true, false)) {
        val state = InMemoryJobEngine.State()
        success(state.bindResourcePolicy(JobResourcePolicy(maxretainedjobs = 1)))
        val task = new CountingTask
        val context = ExecutionContext.test()
        val target = if (durable) state.durableJobs else state.runtimeJobs
        Vector("one", "sentinel").foreach { label =>
          val record = _live_record(id(label), task, context, Some(input()))
          target.put(record.id, record)
        }
        val clock = new CountingClock
        val timer = new CountingTimer(clock)
        val before = state._resource_budget.snapshot
        When("a lower-only map limit encounters the first oversupply sentinel")
        val thrown = intercept[IllegalArgumentException] {
          new InMemoryJobEngine(runtimeState = state, timeSource = clock, timer = Some(timer))(
            scala.concurrent.ExecutionContext.global)
        }
        Then("the whole valid prefix remains unseeded and no timer or task runs")
        thrown.getMessage shouldBe "JobEngine resource bootstrap refused"
        target.size() shouldBe 2
        state._resource_budget.snapshot shouldBe before
        timer.calls.get() shouldBe 0
        clock.calls.get() shouldBe 0
        task.runs.get() shouldBe 0
      }
      Given("a null record-map container cannot be represented as a valid State")
      val missing = InMemoryJobEngine.State(durableJobs = null)
      val clock = new CountingClock
      val timer = new CountingTimer(clock)
      When("the constructor sees that missing map before seeding")
      val thrown = intercept[IllegalArgumentException] {
        new InMemoryJobEngine(runtimeState = missing, timeSource = clock, timer = Some(timer))(
          scala.concurrent.ExecutionContext.global)
      }
      Then("it normalizes the bootstrap error without a timer or clock read")
      thrown.getMessage shouldBe "JobEngine resource bootstrap refused"
      timer.calls.get() shouldBe 0
      clock.calls.get() shouldBe 0
    }

    "IR2 preserve inclusive preseed boundaries, duplicate union accounting and shared pending work" in {
      Given("count, metadata and known-byte boundary records, with one duplicate identity across both maps")
      val state = InMemoryJobEngine.State()
      val context = ExecutionContext.test()
      val task = new CountingTask
      val count = _live_record(id("count"), task, context,
        Some(input(Vector.fill(1024)(descriptor))))
      val metadata = _live_record(id("metadata"), task, context,
        Some(input(Vector.fill(1024)(descriptor.copy(filename = Some("f" * 1018))))))
        .copy(scheduledStartAt = Some(instant.plusSeconds(10)))
      val bytes = _live_record(id("bytes"), task, context,
        Some(input(Vector(descriptor.copy(byteSize = Some(8388608L)))))).copy(status = JobStatus.Succeeded)
      state.durableJobs.put(count.id, count)
      state.runtimeJobs.put(count.id, count)
      state.durableJobs.put(metadata.id, metadata)
      state.runtimeJobs.put(bytes.id, bytes)
      When("a real engine attaches and restores the single scheduled root")
      val fixture = fresh(state)
      try {
        Then("all boundary values remain complete and duplicate IDs consume one retained identity")
        fixture.engine.resourceSnapshot.retained shouldBe 3
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.timer.calls.get() shouldBe 1
        fixture.timer.pendingCount shouldBe 1
        state.durableJobs.get(count.id).input shouldBe count.input
        state.runtimeJobs.get(count.id).input shouldBe count.input
        state.durableJobs.get(metadata.id).input shouldBe metadata.input
        state.runtimeJobs.get(bytes.id) shouldBe bytes
        fixture.engine.drainAll() shouldBe 0
        task.runs.get() shouldBe 0
        val queuedtask = new CountingTask
        val queued = success(fixture.engine.submit(List(queuedtask), fixture.context,
          sync.copy(runMode = JobRunMode.Async, input = Some(input(Vector(descriptor))))))
        When("another owner attaches to the already occupied shared State")
        val other = fresh(state)
        try {
          Then("existing retained and pending accounting survives without duplicate physical timers")
          other.engine.resourceSnapshot shouldBe fixture.engine.resourceSnapshot
          other.engine.resourceSnapshot.retained shouldBe 4
          other.engine.resourceSnapshot.pending shouldBe 2
          other.timer.pendingCount shouldBe 0
          state.runtimeJobs.containsKey(queued) shouldBe true
          When("the original manual due timer and queue drain normally")
          fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          fixture.engine.drainAll() shouldBe 2
          Then("only existing normal work dispatch executes both admitted tasks")
          task.runs.get() shouldBe 1
          queuedtask.runs.get() shouldBe 1
          fixture.engine.resourceSnapshot.pending shouldBe 0
          fixture.engine.resourceSnapshot.executing shouldBe 0
        } finally other.engine.shutdown()
      } finally fixture.engine.shutdown()
    }

    "IR3 refuse invalid port input after real signed source and provider discovery without runtime effects" in {
      Given("proof-complete eligible signed V2 native records and a port returning invalid input")
      val invalids: Vector[Option[JobInput]] = Vector(null, Some(null),
        Some(input(Vector.fill(1025)(descriptor))),
        Some(input(Vector(descriptor.copy(byteSize = Some(8388609L))))),
        Some(input(Vector.fill(1024)(descriptor.copy(filename = Some("f" * 1019))))),
        Some(input(Vector(descriptor.copy(inlineBase64 = Some("PRIVATE@@"))))),
        Some(input(Vector(descriptor.copy(inlineBase64 = Some(encoded(8388609)))))),
        Some(input(Vector(descriptor)).copy(cleanedAt = null)))
      invalids.zipWithIndex.foreach { case (value, index) =>
        val fixture = fresh()
        given ExecutionContext = fixture.context
        val store = new DurableJobStore(EntityStore.standard())
        val jobid = id(s"refused$index")
        val native = success(store.create(_native_record(jobid), access))
        val task = new CountingTask
        val sourcecalls = new AtomicInteger(0)
        val portcalls = new AtomicInteger(0)
        val source = _source(jobid, sourcecalls)
        val port = _port(portcalls, candidate =>
          DurableJobRuntimeRehydration(success(JobId.parse(candidate.jobId)), List(task), fixture.context, value, None))
        try {
          val before = fixture.observation(task)
          When("actual native recovery invokes the eligible reconstruction port and offers invalid input")
          val report = success(fixture.engine.recoverDurableStartup(source, store, port, 1))
          Then("closed Refused preserves native authority and all runtime resource/timer/clock/getter facts")
          report.candidates shouldBe Vector(DurableJobRuntimeRehydrationCandidateReport(
            0L, jobid.value, DurableJobRuntimeRehydrationFact.Refused))
          fixture.engine.durableRuntimeRehydrationReport shouldBe Some(report)
          sourcecalls.get() shouldBe 1
          portcalls.get() shouldBe 1
          fixture.observation(task) shouldBe before
          fixture.engine._get_record(jobid) shouldBe None
          fixture.engine.drainAll() shouldBe 0
          success(store.load(jobid.value, access)) shouldBe Some(native)
        } finally fixture.engine.shutdown()
      }
    }

    "IR4 retain original identity and structural refusals when input is also invalid" in {
      Given("eligible native records and invalid task/context/option/runtime identities paired with oversized input")
      val builders: Vector[DurableJobRuntimeRehydration => DurableJobRuntimeRehydration] = Vector(
        _ => null, _.copy(jobId = null), _.copy(tasks = null), _.copy(tasks = Nil),
        _.copy(tasks = List(null)), _.copy(context = null), _.copy(input = null),
        _.copy(input = Some(null)), _.copy(definitionSnapshot = null),
        _.copy(definitionSnapshot = Some(null)), _.copy(jobId = id("wrongruntime")))
      builders.zipWithIndex.foreach { case (build, index) =>
        val fixture = fresh()
        given ExecutionContext = fixture.context
        val store = new DurableJobStore(EntityStore.standard())
        val jobid = id(s"precedence$index")
        val native = success(store.create(_native_record(jobid), access))
        val task = new CountingTask
        val base = DurableJobRuntimeRehydration(jobid, List(task), fixture.context,
          Some(input(Vector.fill(1025)(descriptor))), None)
        val sourcecalls = new AtomicInteger(0)
        val portcalls = new AtomicInteger(0)
        try {
          val before = fixture.observation(task)
          When("the existing native coordinator offers an originally malformed reconstruction")
          val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sourcecalls),
            store, _port(portcalls, _ => build(base)), 1))
          Then("the original closed refusal precedes input processing and leaves native/runtime authority unchanged")
          report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
          portcalls.get() shouldBe 1
          sourcecalls.get() shouldBe 1
          fixture.observation(task) shouldBe before
          success(store.load(jobid.value, access)) shouldBe Some(native)
          When("the actual registration boundary also receives a mismatching candidate/snapshot identity")
          val candidate = success(_source(id("wrongcandidate"), new AtomicInteger(0)).candidates(1)).head
          val decision = success(DurableJobRecovery.classify(native.record, candidate.replayEvidence))
          val fact = fixture.engine._register_durable_runtime_rehydration(candidate, native, decision, base,
            store, DurableJobLifetimeQuotaPolicy.default, fixture.context)
          Then("candidate mismatch keeps its original closed refusal without reserving or rewriting native state")
          fact shouldBe DurableJobRuntimeRehydrationFact.Refused
          fixture.observation(task) shouldBe before
          success(store.load(jobid.value, access)) shouldBe Some(native)
        } finally fixture.engine.shutdown()
      }
    }

    "IR5 preserve exact sanitized metadata and native timestamps before normal scheduled drain" in {
      Given("a signed native scheduled record and valid raw inline/Blob input with legacy SHA and TTL metadata")
      val fixture = fresh()
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val jobid = id("validrehydration")
      val value = input(Vector(descriptor.copy(fieldName = Some("upload"), filename = Some("legacy.txt"),
        contentType = Some("text/plain"), byteSize = Some(3L), sha256 = Some("abc"),
        inlineBase64 = Some("YWJj"), blobId = Some("blob-1"))))
        .copy(retentionPolicy = JobInputRetentionPolicy.Ttl, createdAt = instant.minusSeconds(20),
          cleanedAt = Some(instant.minusSeconds(10)))
      val native = JobDurableLifetimeRecoveryFixtures.publish(store,
        _native_record(jobid, JobDurableLifetimeRecoveryFixtures.durableInputs(value)))
      val bridge = JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, native.record, fixture.context)
      val task = new CountingTask
      val sourcecalls = new AtomicInteger(0)
      val portcalls = new AtomicInteger(0)
      try {
        When("the eligible native port result registers through the existing engine boundary")
        val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sourcecalls), store,
          _port(portcalls, _ => DurableJobRuntimeRehydration(jobid, List(task), fixture.context, Some(value), None)), 1))
        val registered = fixture.engine.runtimeState.durableJobs.get(jobid)
        Then("only raw inline and Blob references are stripped while every metadata and identity timestamp survives")
        report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
        native.record.body.inputs shouldBe Vector(DurableInputReference("upload", DurableValue.Absent))
        registered.input shouldBe Some(value.copy(payloads = value.payloads.map(_.sanitized)))
        registered.input.get.createdAt shouldBe value.createdAt
        registered.input.get.cleanedAt shouldBe value.cleanedAt
        registered.input.get.ttl shouldBe JobInput.DefaultTtl
        registered.input.get.payloads.head.inlineBase64 shouldBe None
        registered.input.get.payloads.head.blobId shouldBe None
        registered.input.get.payloads.head.sha256 shouldBe Some("abc")
        registered.createdAt shouldBe native.record.body.identity.createdAt
        registered.updatedAt shouldBe native.record.body.identity.updatedAt
        sourcecalls.get() shouldBe 1
        portcalls.get() shouldBe 1
        fixture.ids.calls.get() shouldBe 0
        fixture.timer.calls.get() shouldBe 1
        fixture.timer.pendingCount shouldBe 1
        fixture.engine.drainAll() shouldBe 0
        task.runs.get() shouldBe 0
        success(store.load(jobid.value, access)) shouldBe Some(native)
        When("the existing manual scheduled-start timer becomes due and its queue drains")
        fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
        fixture.engine.drainAll() shouldBe 1
        Then("the original task executes through five actual acknowledged native checkpoints")
        task.runs.get() shouldBe 1
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        fixture.engine.resourceSnapshot.pending shouldBe 0
        fixture.engine.resourceSnapshot.executing shouldBe 0
        val completed = success(store.load(jobid.value, access)).get
        completed.record.body.identity.revision shouldBe native.record.body.identity.revision + 5L
        completed.record.body.identity.createdAt shouldBe native.record.body.identity.createdAt
        completed.record.body.authorization shouldBe native.record.body.authorization
        completed.record.body.inputs shouldBe native.record.body.inputs
        completed.record.body.definitionSnapshot shouldBe native.record.body.definitionSnapshot
        completed.record.body.retention shouldBe native.record.body.retention
        bridge.revisions shouldBe (2L to 6L).toVector
        success(store.lifetimeQuota(DurableJobLifetimeQuotaPolicy.default).snapshot(jobid.value, access)).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
      } finally fixture.engine.shutdown()
    }

    "IR6 conserve generated actual native recovery and due dispatch with 24 seeded checks" in {
      Given("finite nonempty native reconstruction inputs across valid raw/declared/opaque and invalid shapes")
      val generator = for {
        count <- Gen.choose(1, 5)
        size <- Gen.choose(0, 64)
        kind <- Gen.choose(0, 4)
      } yield (count, size, kind)
      val property = Prop.forAll(generator) { case (count, size, kind) =>
        val fixture = fresh()
        given ExecutionContext = fixture.context
        val store = new DurableJobStore(EntityStore.standard())
        val jobid = id(s"generated$count$size$kind")
        val task = new CountingTask
        val calls = new AtomicInteger(0)
        val sources = new AtomicInteger(0)
        val payload = descriptor.copy(filename = Some("é" * size), blobId = Some("blob-1"),
          sha256 = Some("sha256"), inlineBase64 = if (kind == 0) Some("@@") else Some(encoded(size)),
          byteSize = if (kind == 1) Some(size.toLong + 1) else None)
        val value = input(Vector.fill(count)(payload))
        val supplied = if (kind == 2) Some(value.copy(ttl = null)) else Some(value)
        val accepted = kind >= 3
        val native = if (accepted) JobDurableLifetimeRecoveryFixtures.publish(store,
          _native_record(jobid, JobDurableLifetimeRecoveryFixtures.durableInputs(value)))
          else success(store.create(_native_record(jobid), access))
        if (accepted) JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, native.record, fixture.context)
        try {
          val before = fixture.observation(task)
          When("the real signed source and reconstruction port offer generated input")
          val report = success(fixture.engine.recoverDurableStartup(_source(jobid, sources), store,
            _port(calls, _ => DurableJobRuntimeRehydration(jobid, List(task), fixture.context, supplied, None)), 1))
          Then("the model predicts exact insertion and conserved resource/physical-timer effects")
          report.candidates.map(_.fact) shouldBe Vector(if (accepted)
            DurableJobRuntimeRehydrationFact.Registered else DurableJobRuntimeRehydrationFact.Refused)
          calls.get() shouldBe 1
          sources.get() shouldBe 1
          success(store.load(jobid.value, access)) shouldBe Some(native)
          if (accepted) {
            native.record.body.inputs shouldBe Vector.tabulate(count)(index =>
              DurableInputReference(s"input-$index", DurableValue.Absent))
            fixture.engine._get_record(jobid).get.input shouldBe Some(value.copy(payloads = value.payloads.map(_.sanitized)))
            fixture.engine.resourceSnapshot.retained shouldBe 1
            fixture.engine.resourceSnapshot.pending shouldBe 1
            fixture.timer.pendingCount shouldBe 1
            fixture.timer.calls.get() shouldBe 1
            fixture.engine.drainAll() shouldBe 0
            task.runs.get() shouldBe 0
            When("the generated valid positive control advances and drains normal work")
            fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
            fixture.engine.drainAll() shouldBe 1
            Then("physical pending work is acknowledged and the live task runs once")
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
            fixture.timer.pendingCount shouldBe 0
            task.runs.get() shouldBe 1
            val completed = success(store.load(jobid.value, access)).get
            completed.record.body.identity.revision shouldBe native.record.body.identity.revision + 5L
            completed.record.body.inputs shouldBe native.record.body.inputs
            completed.record.body.definitionSnapshot shouldBe native.record.body.definitionSnapshot
            success(store.lifetimeQuota(DurableJobLifetimeQuotaPolicy.default).snapshot(jobid.value, access)).seed.paid shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
          } else {
            fixture.observation(task) shouldBe before
            fixture.engine._get_record(jobid) shouldBe None
            fixture.engine.drainAll() shouldBe 0
          }
          true
        } finally fixture.engine.shutdown()
      }
      When("ScalaCheck executes the fixed-seed actual native attempts")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(697093342L)), property)
      Then("at least 24 real recovery attempts satisfy registration, refusal and due-drain conservation")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  private def _live_record(jobid: JobId, task: JobTask, context: ExecutionContext,
    value: Option[JobInput]): JobRecord =
    JobRecord(jobid, List(task), context, JobStatus.Submitted, None,
      JobPersistencePolicy.Persistent, JobRunMode.Async, 0, None, instant, instant,
      Vector.empty, timeline = Vector.empty, debug = JobDebugInfo(None, Map.empty, Vector.empty), input = value)

  private def _source(jobid: JobId, calls: AtomicInteger): DurableJobStartupRecoverySource =
    new DurableJobStartupRecoverySource {
      def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] = {
        val _ = maxCandidates
        calls.incrementAndGet()
        Consequence.success(Vector(DurableJobStartupRecoveryCandidate(0L, jobid.value, access,
          DurableReplayEvidence(Some("idempotency"), Some("input"), Some("definition"),
            Some("authorization"), Some("provider"), Some("compatibility")))))
      }
    }

  private def _port(calls: AtomicInteger,
    build: DurableJobStartupRecoveryCandidate => DurableJobRuntimeRehydration): DurableJobRuntimeRehydrationPort =
    new DurableJobRuntimeRehydrationPort {
      def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
        decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] = {
        val _ = snapshot
        val _ = decision
        calls.incrementAndGet()
        Consequence.success(build(candidate))
      }
    }

  private def _native_record(jobid: JobId, inputs: Vector[DurableInputReference] = Vector.empty): DurableJobRecord = {
    val supplied = evidence(1L).projection
    success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(jobid.value, 1L, instant, instant.plusSeconds(20)), supplied.authorization,
      DurableJobLifecycle(DurableJobLifecycleStatus.Submitted, 0, DurableRunMode.Async,
        supplied.retry, DurableScheduleState(Some(instant.plusSeconds(10)), None, None)),
      supplied.tasks, inputs, DurableResultOutcome.Pending, Vector.empty, Vector.empty,
      None, supplied.definitionSnapshot, supplied.retention)))
  }
}
