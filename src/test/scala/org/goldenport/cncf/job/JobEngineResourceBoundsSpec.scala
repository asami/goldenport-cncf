package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.cncf.context.{ExecutionContext, IdGenerationContext}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
class JobEngineResourceBoundsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 admission before effects" should afterWord(
    "in spec:job-operations-contract, example:E1, rules:R1,R2,R3, phase:69.7"
  ) {
    "admit the inclusive pending and retained limits and refuse one over without effects" in {
      Given("docs/spec/job-operations-contract.md; R1,R2,R3; E1; an engine with two retained and one pending slots")
      val fixture = _fixture(JobResourcePolicy(2, 1, 1, 1024, 32))
      val events = EventStore.inMemory
      fixture.engine.withEventStore(events)
      val task = new RecordingTask()
      val nativecalls = new AtomicInteger(0)
      fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(
        new DurableJobStore(EntityStore.standard()), _ => {
          nativecalls.incrementAndGet()
          Consequence.success(_evidence(1L))
        }
      ))
      try {
        When("the pending boundary is filled and another admission is requested")
        val first = _success(fixture.engine.submit(List(task), fixture.context))
        val ids = fixture.ids.calls.get()
        val eventcount = _events(events)
        val refused = fixture.engine.submit(List(task), fixture.context)

        Then("refusal generates no IDs, native admission, events, records or task execution")
        refused.isSuccess shouldBe false
        fixture.ids.calls.get() shouldBe ids
        nativecalls.get() shouldBe 1
        _events(events) shouldBe eventcount
        fixture.engine.resourceSnapshot shouldBe JobResourceSnapshot(1, 1, 0, fixture.policy)
        task.runs.get() shouldBe 0
        _control(fixture, first, JobControlCommand.Cancel).isSuccess shouldBe true
        fixture.engine.resourceSnapshot.pending shouldBe 0
        _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_sync))
        fixture.engine.resourceSnapshot.retained shouldBe 2
        val full = fixture.engine.resourceSnapshot
        val fullids = fixture.ids.calls.get()
        fixture.engine.submit(Nil, fixture.context, _ephemeral_sync).isSuccess shouldBe false
        fixture.engine.resourceSnapshot shouldBe full
        fixture.ids.calls.get() shouldBe fullids
      } finally fixture.engine.shutdown()
    }
  }

  "E2 construction and submission cardinality" should afterWord(
    "in spec:job-operations-contract, example:E2, rules:R1,R2,R9, phase:69.7"
  ) {
    "admit 1024 submitted tasks, refuse 1025 and retain empty-list compatibility" in {
      Given("docs/spec/job-operations-contract.md; R1,R2; E2; the inclusive default submission task limit")
      val fixture = _fixture()
      val task = new RecordingTask()
      try {
        When("inclusive, one-over and empty submissions are requested")
        val admitted = fixture.engine.submit(List.fill(1024)(task), fixture.context, _ephemeral_async)
        val before = fixture.engine.resourceSnapshot
        val ids = fixture.ids.calls.get()
        val refused = fixture.engine.submit(List.fill(1025)(task), fixture.context, _ephemeral_async)

        Then("the whole oversized submission is refused before any new ID or task effect")
        admitted.isSuccess shouldBe true
        refused.isSuccess shouldBe false
        fixture.engine.resourceSnapshot shouldBe before
        fixture.ids.calls.get() shouldBe ids
        task.runs.get() shouldBe 0
        val empty = _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_sync))
        fixture.engine.getStatus(empty) shouldBe Some(JobStatus.Succeeded)
      } finally fixture.engine.shutdown()
    }

    "refuse invalid worker and retry construction before timer or task effects" in {
      Given("docs/spec/job-operations-contract.md; R1,R9; E2; malformed worker/retry configuration and preseeded oversupply")
      val clock = new ManualJobTimeSource(_instant)
      val timer = new RecordingTimer(clock)
      val configs = Vector(InMemoryJobEngine.SchedulerConfig(0), InMemoryJobEngine.SchedulerConfig(65))
      val retries = Vector(
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(1)), -1),
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(1)), 33),
        InMemoryJobEngine.RetrySchedule(null, 1),
        InMemoryJobEngine.RetrySchedule(Vector(null), 1),
        InMemoryJobEngine.RetrySchedule(Vector.fill(33)(Duration.ofMillis(1)), 1),
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ZERO), 1),
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(-1)), 1),
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ofHours(24).plusNanos(1)), 1)
      )
      val task = new RecordingTask()
      val state = InMemoryJobEngine.State()
      _success(state.bindResourcePolicy(JobResourcePolicy(1, 1, 1, 1, 1)))
      state.durableJobs.put(_id("seedone"), _live_record(_id("seedone"), task))
      state.runtimeJobs.put(_id("seedtwo"), _live_record(_id("seedtwo"), task))

      When("invalid constructors and an oversized complete seed are attempted")
      configs.foreach { config =>
        an[IllegalArgumentException] should be thrownBy new InMemoryJobEngine(
          schedulerConfig = config, timeSource = clock, timer = Some(timer)
        )(scala.concurrent.ExecutionContext.global)
      }
      retries.foreach { retry =>
        an[IllegalArgumentException] should be thrownBy new InMemoryJobEngine(
          retrySchedule = retry, timeSource = clock, timer = Some(timer)
        )(scala.concurrent.ExecutionContext.global)
      }
      an[IllegalArgumentException] should be thrownBy new InMemoryJobEngine(
        runtimeState = state, retrySchedule = InMemoryJobEngine.RetrySchedule(Vector.empty, 0),
        timeSource = clock, timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global)

      Then("no timer is registered, neither accepted record is truncated and no task runs")
      timer.calls.get() shouldBe 0
      timer.shutdowns.get() shouldBe 0
      state.durableJobs.size() shouldBe 1
      state.runtimeJobs.size() shouldBe 1
      task.runs.get() shouldBe 0
    }
  }

  "E3 native admission refusal" should afterWord(
    "in spec:job-operations-contract, example:E3, rules:R2,R3,R10, phase:69.7"
  ) {
    "release unpublished slots and retain bounded refusal facts across repeated native refusal" in {
      Given("docs/spec/job-operations-contract.md; R2,R3,R10; E3; a signed native store with mismatched access and a two-fact diagnostic cap")
      val fixture = _fixture(JobResourcePolicy(2, 1, 1, 1024, 32))
      val task = new RecordingTask()
      val store = new DurableJobStore(EntityStore.standard())
      fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(
        store, _ => Consequence.success(_evidence(1L).copy(access = _access.copy(tenantId = "other")))
      ))
      try {
        When("five native admissions refuse and an explicit original bridge failure is returned")
        val refusals = Vector.fill(5)(fixture.engine.submit(List(task), fixture.context))
        val original = Conclusion.simple("original admission refusal")
        fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(
          store, _ => Consequence.Failure(original)
        ))
        val last = fixture.engine.submit(List(task), fixture.context)

        Then("all provisional capacity returns, prior facts remain capped and the original failure survives")
        refusals.forall(!_.isSuccess) shouldBe true
        last match {
          case Consequence.Failure(conclusion) => conclusion shouldBe original
          case _ => fail("Expected native admission refusal")
        }
        fixture.engine.resourceSnapshot shouldBe JobResourceSnapshot(0, 0, 0, fixture.policy)
        fixture.engine.durableLifecycleWriteFailureFacts.size shouldBe 2
        fixture.engine.durableLifecycleWriteFailureFacts.map(_.failure).distinct shouldBe
          Vector(DurableJobLifecycleWriteFailure.AdmissionRefused)
        fixture.engine.runtimeState.durableJobs.isEmpty shouldBe true
        task.runs.get() shouldBe 0
        task.admissionfailures.get() shouldBe 6
      } finally fixture.engine.shutdown()
    }
  }

  "E4 one physical execution bound" should afterWord(
    "in spec:job-operations-contract, example:E4, rules:R2,R4,R5, phase:69.7"
  ) {
    "share the execution cap across workers, Sync roots and manual drains" in {
      Given("docs/spec/job-operations-contract.md; R2,R4,R5; E4; worker and manual engines sharing one execution slot")
      val state = _state(JobResourcePolicy(8, 4, 1, 1024, 32))
      val worker = _fixture(state = state, auto = true)
      val manual = _fixture(state = state)
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blocking = new RecordingTask(ctx => {
        val _ = ctx
        entered.countDown()
        if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout")
        _ok
      })
      val queued = new RecordingTask()
      try {
        When("a worker occupies the slot while Sync and drain requests compete")
        _success(worker.engine.submit(List(blocking), worker.context, _ephemeral_async))
        entered.await(3, TimeUnit.SECONDS) shouldBe true
        _success(manual.engine.submit(List(queued), manual.context, _ephemeral_async))
        val before = manual.engine.resourceSnapshot
        manual.engine.submit(List(queued), manual.context, _ephemeral_sync).isSuccess shouldBe false
        manual.engine.drainOne() shouldBe false

        Then("busy work stays pending and can execute only after the physical worker exits")
        manual.engine.resourceSnapshot shouldBe before
        queued.runs.get() shouldBe 0
        release.countDown()
        worker.engine.shutdown()
        worker.engine._worker_pool.awaitTermination(3, TimeUnit.SECONDS) shouldBe true
        manual.engine.resourceSnapshot.executing shouldBe 0
        manual.engine.drainOne() shouldBe true
        manual.engine.resourceSnapshot.pending shouldBe 0
        manual.engine.resourceSnapshot.executing shouldBe 0
        queued.runs.get() shouldBe 1
      } finally {
        release.countDown()
        worker.engine.shutdown()
        manual.engine.shutdown()
        worker.engine._worker_pool.awaitTermination(3, TimeUnit.SECONDS)
      }
    }

    "preserve priority then sequence ordering and release capacity after throws or checkpoint refusal" in {
      Given("docs/spec/job-operations-contract.md; R4; E4; a manual queue and a signed native checkpoint bridge")
      val fixture = _fixture(JobResourcePolicy(8, 4, 1, 1024, 32))
      val order = ArrayBuffer.empty[String]
      val checkpointtask = new RecordingTask()
      try {
        When("three jobs queue by priority and one dispatch throws")
        Vector((2, "low"), (0, "first"), (0, "second")).foreach { case (priority, label) =>
          val task = new RecordingTask(_ => { order += label; _ok })
          _success(fixture.engine.submit(List(task), fixture.context, _ephemeral_async.copy(priority = priority)))
        }
        fixture.engine.drainAll() shouldBe 3
        _success(fixture.engine.submit(List(new RecordingTask(_ => throw new IllegalStateException("task"))),
          fixture.context, _ephemeral_async))
        fixture.engine.drainOne() shouldBe true
        val store = new DurableJobStore(EntityStore.standard())
        fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, request =>
          if (request == DurableJobLifecycleWriteRequest.Admission) Consequence.success(_evidence(1L))
          else Consequence.serviceUnavailable("checkpoint refusal")
        ))
        val id = _success(fixture.engine.submit(List(checkpointtask), fixture.context,
          JobSubmitOption(runMode = JobRunMode.Sync)))

        Then("ordering survives and exception or durable refusal returns execution without task dispatch")
        order.toVector shouldBe Vector("first", "second", "low")
        fixture.engine.resourceSnapshot.executing shouldBe 0
        checkpointtask.runs.get() shouldBe 0
        fixture.engine.durableLifecycleWriteFailureFacts.exists(fact =>
          fact.jobid == id && fact.failure == DurableJobLifecycleWriteFailure.StartIntentRefused) shouldBe true
      } finally fixture.engine.shutdown()
    }

    "return the physical lease after an interrupted worker and a throwing Sync root" in {
      Given("docs/spec/job-operations-contract.md; R4; E4; one physical execution slot and an interruptible worker task")
      val fixture = _fixture(JobResourcePolicy(4, 2, 1, 1024, 32), auto = true)
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val task = new RecordingTask(_ => {
        entered.countDown()
        if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout")
        _ok
      })
      try {
        When("the occupied worker is interrupted through owned shutdown")
        _success(fixture.engine.submit(List(task), fixture.context, _ephemeral_async))
        entered.await(3, TimeUnit.SECONDS) shouldBe true
        fixture.engine.shutdown()
        fixture.engine._worker_pool.awaitTermination(3, TimeUnit.SECONDS) shouldBe true

        Then("capacity returns only after worker termination")
        fixture.engine.resourceSnapshot.executing shouldBe 0
        task.runs.get() shouldBe 1
      } finally { release.countDown(); fixture.engine.shutdown() }
      val sync = _fixture(JobResourcePolicy(2, 1, 1, 1024, 32))
      try {
        an[IllegalStateException] should be thrownBy sync.engine.submit(
          List(new RecordingTask(_ => throw new IllegalStateException("task failure"))),
          sync.context, _ephemeral_sync)
        sync.engine.resourceSnapshot.executing shouldBe 0
        _success(sync.engine.submit(Nil, sync.context, _ephemeral_sync))
        sync.engine.resourceSnapshot.executing shouldBe 0
      } finally sync.engine.shutdown()
    }
  }

  "E5 delayed generations" should afterWord(
    "in spec:job-operations-contract, example:E5, rules:R3,R6,R7, phase:69.7"
  ) {
    "reserve delayed pending immediately and transfer a duplicate concurrent callback only once" in {
      Given("docs/spec/job-operations-contract.md; R3,R6; E5; one delayed-start pending slot and a hostile duplicate timer")
      val fixture = _fixture(JobResourcePolicy(4, 1, 1, 1024, 32))
      val task = new RecordingTask()
      try {
        When("a future job is admitted, time advances and callbacks race at the same generation")
        _success(fixture.engine.submit(List(task), fixture.context,
          _ephemeral_async.copy(scheduledStartAt = Some(_instant.plusMillis(5)))))
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.engine.submit(Nil, fixture.context, _ephemeral_async).isSuccess shouldBe false
        fixture.clock.advanceBy(Duration.ofMillis(5))
        val start = new CountDownLatch(1)
        val threads = Vector.fill(4)(new Thread(() => {
          if (start.await(3, TimeUnit.SECONDS)) fixture.timer.fire(includeclosed = true)
        }))
        threads.foreach(_.start())
        try { start.countDown(); threads.foreach(_.join(3000L)) }
        finally threads.foreach(_.interrupt())

        Then("the same pending lease transfers once and repeated callbacks produce one execution")
        threads.exists(_.isAlive) shouldBe false
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.engine.drainAll() shouldBe 1
        fixture.timer.fire(includeclosed = true)
        fixture.engine.drainAll() shouldBe 0
        task.runs.get() shouldBe 1
        fixture.engine.resourceSnapshot.pending shouldBe 0
      } finally fixture.engine.shutdown()
    }

    "preserve admitted identities and original retry failures when timer registration refuses" in {
      Given("docs/spec/job-operations-contract.md; R7; E5; timers that refuse with raw provider text")
      val initial = _fixture(failafter = 0)
      val retry = _fixture(failafter = 0)
      val original = _failure(Disposition.UserAction.RetryLater)
      try {
        When("initial delayed start and automatic delayed retry registration fail")
        val initialid = _success(initial.engine.submit(List(new RecordingTask()), initial.context,
          _ephemeral_async.copy(scheduledStartAt = Some(_instant.plusSeconds(1)))))
        val retryid = _success(retry.engine.submit(List(new RecordingTask(_ => TaskFailed(original))),
          retry.context, _ephemeral_sync))

        Then("both identities remain retained with bounded terminal failure and no leaked pending capacity")
        initial.engine.getStatus(initialid) shouldBe Some(JobStatus.Failed)
        initial.engine.resourceSnapshot.retained shouldBe 1
        initial.engine.resourceSnapshot.pending shouldBe 0
        initial.engine.query(initialid).get.timeline.events.flatMap(_.note).mkString should not include "PRIVATE-PROVIDER"
        retry.engine.getResult(retryid) shouldBe Some(JobResult.Failure(original))
        retry.engine.query(retryid).get.retry.attemptCount shouldBe 0
        retry.engine.query(retryid).get.retry.deadLetter shouldBe true
        retry.engine.query(retryid).get.retry.poison shouldBe false
        retry.engine.resourceSnapshot.pending shouldBe 0
      } finally { initial.engine.shutdown(); retry.engine.shutdown() }
    }
  }

  "E6 SameJob task admission" should afterWord(
    "in spec:job-operations-contract, example:E6, rules:R2,R5, phase:69.7"
  ) {
    "preserve busy child records and reject forged execution context authority" in {
      Given("docs/spec/job-operations-contract.md; R2,R5; E6; one queued child and a physical root executing on another thread")
      val fixture = _fixture(JobResourcePolicy(4, 1, 1, 1024, 32))
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val failures = new java.util.concurrent.ConcurrentLinkedQueue[Throwable]()
      val parent = _success(fixture.engine.submit(List(new RecordingTask()), fixture.context, _ephemeral_sync))
      val child = new RecordingTask()
      _success(fixture.engine.enqueueTaskInJob(parent, child, fixture.context))
      val running = new RecordingTask(_ => {
        entered.countDown()
        if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout")
        _ok
      })
      val thread = new Thread(() => {
        try _success(fixture.engine.submit(List(running), fixture.context, _ephemeral_sync))
        catch { case e: Throwable => failures.add(e) }
        ()
      })
      try {
        When("another child is queued and a caller supplies the busy Job context as alleged nesting")
        val before = fixture.engine.query(parent)
        val ids = fixture.ids.calls.get()
        fixture.engine.enqueueTaskInJob(parent, child, fixture.context).isSuccess shouldBe false
        fixture.engine.query(parent) shouldBe before
        fixture.ids.calls.get() shouldBe ids
        thread.start()
        entered.await(3, TimeUnit.SECONDS) shouldBe true
        val forged = ExecutionContext.withJobContext(fixture.context, JobContext.empty.copy(jobId = Some(parent)))
        val busybefore = fixture.engine.query(parent)
        val busyids = fixture.ids.calls.get()
        val result = fixture.engine.runTaskInJobSync(parent, child, forged)

        Then("external context does not reuse a lease or mutate IDs, results, timeline or counters")
        result.isSuccess shouldBe false
        fixture.engine.query(parent) shouldBe busybefore
        fixture.ids.calls.get() shouldBe busyids
        fixture.engine.runtimeState.runtimeJobs.get(parent).pendingTaskCount shouldBe 1
        child.runs.get() shouldBe 0
      } finally {
        release.countDown()
        thread.join(3000L)
        fixture.engine.shutdown()
      }
      thread.isAlive shouldBe false
      failures.isEmpty shouldBe true
    }

    "reuse actual same-thread nesting and keep compensation within the root lease" in {
      Given("docs/spec/job-operations-contract.md; R5; E6; a physical root with one execution slot and compensatable work")
      val fixture = _fixture(JobResourcePolicy(4, 2, 1, 1024, 32))
      val child = new RecordingTask()
      val compensation = new RecordingTask()
      val first = new RecordingTask(_ => _ok, Some(compensation))
      val original = Conclusion.simple("business failure")
      val root = new RecordingTask(ctx => {
        _success(fixture.engine.runTaskInJobSync(ctx.jobContext.jobId.get, child, ctx))
        fixture.engine.resourceSnapshot.executing shouldBe 1
        fixture.engine.submit(Nil, ctx, _ephemeral_sync).isSuccess shouldBe false
        TaskFailed(original)
      })
      try {
        When("the executor invokes a nested SameJob task and later compensates a committed root task")
        val id = _success(fixture.engine.submit(List(first, root), fixture.context, _ephemeral_sync))

        Then("nested work and compensation run once without a second physical lease")
        child.runs.get() shouldBe 1
        compensation.runs.get() shouldBe 1
        fixture.engine.getResult(id) shouldBe Some(JobResult.Failure(original))
        fixture.engine.resourceSnapshot.executing shouldBe 0
      } finally fixture.engine.shutdown()
    }
  }

  "E7 retry admission refusal" should afterWord(
    "in spec:job-operations-contract, example:E7, rules:R2,R7, phase:69.7"
  ) {
    "preserve Failed and Cancelled evidence when control Retry is busy" in {
      Given("docs/spec/job-operations-contract.md; R2,R7; E7; terminal jobs and a fully occupied pending quota")
      val fixture = _fixture(JobResourcePolicy(6, 1, 1, 1024, 32))
      val events = EventStore.inMemory
      fixture.engine.withEventStore(events)
      try {
        val failed = _success(fixture.engine.submit(List(new RecordingTask(_ => TaskFailed(Conclusion.simple("failed")))),
          fixture.context, _ephemeral_sync))
        val cancelled = _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_async))
        _success(_control(fixture, cancelled, JobControlCommand.Cancel))
        _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_async))

        When("control Retry competes for the occupied pending slot")
        val old = Vector(failed, cancelled).map(id => fixture.engine.query(id))
        val results = Vector(failed, cancelled).map(fixture.engine.getResult)
        val eventcount = _events(events)
        val refusals = Vector(failed, cancelled).map(id => _control(fixture, id, JobControlCommand.Retry))

        Then("both original records, results, retry fields and event histories survive refusal")
        refusals.forall(!_.isSuccess) shouldBe true
        Vector(failed, cancelled).map(fixture.engine.query) shouldBe old
        Vector(failed, cancelled).map(fixture.engine.getResult) shouldBe results
        _events(events) shouldBe eventcount
        fixture.engine.resourceSnapshot.pending shouldBe 1
      } finally fixture.engine.shutdown()
    }

    "preserve original immediate and delayed failure counts on automatic quota exhaustion" in {
      Given("docs/spec/job-operations-contract.md; R7; E7; a pending slot occupied before automatic retry selection")
      Vector(Disposition.UserAction.RetryNow, Disposition.UserAction.RetryLater).foreach { action =>
        val fixture = _fixture(JobResourcePolicy(4, 1, 1, 1024, 32))
        val original = _failure(action)
        val task = new RecordingTask(_ => TaskFailed(original))
        try {
          When("the failed Sync root requests another attempt while pending capacity is exhausted")
          _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_async))
          val id = _success(fixture.engine.submit(List(task), fixture.context, _ephemeral_sync))

          Then("the actual failure is terminal, exhausted, non-poison and requires recovery without a fake attempt")
          fixture.engine.getResult(id) shouldBe Some(JobResult.Failure(original))
          val retry = fixture.engine.query(id).get.retry
          retry.attemptCount shouldBe 0
          retry.exhausted shouldBe true
          retry.deadLetter shouldBe true
          retry.recoveryRequired shouldBe true
          retry.poison shouldBe false
          fixture.engine.resourceSnapshot.pending shouldBe 1
          task.runs.get() shouldBe 1
        } finally fixture.engine.shutdown()
      }
    }

    "checkpoint exhaustion before terminal publication and preserve a native checkpoint refusal fact" in {
      Given("docs/spec/job-operations-contract.md; R7,R10; E7; admitted native Running/Pending authority and a refused terminal checkpoint")
      val fixture = _fixture(JobResourcePolicy(4, 1, 1, 1024, 32))
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val requests = ArrayBuffer.empty[DurableJobLifecycleWriteBoundary]
      val original = _failure(Disposition.UserAction.RetryNow)
      val task = new RecordingTask(_ => TaskFailed(original))
      val events = EventStore.inMemory
      fixture.engine.withEventStore(events)
      fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, request => {
        requests += request.boundary
        request match {
          case _: DurableJobLifecycleWriteRequest.TerminalOutcomeCheckpoint =>
            Consequence.serviceUnavailable("terminal checkpoint refusal")
          case start: DurableJobLifecycleWriteRequest.TaskStartIntent =>
            val evidence = _evidence(requests.size.toLong)
            Consequence.success(evidence.copy(projection = evidence.projection.copy(tasks = start.taskreadmodels.map(_descriptor))))
          case outcome: DurableJobLifecycleWriteRequest.TaskOutcomeCheckpoint =>
            val evidence = _evidence(requests.size.toLong)
            Consequence.success(evidence.copy(projection = evidence.projection.copy(tasks = outcome.taskreadmodels.map(_descriptor))))
          case _ => Consequence.success(_evidence(requests.size.toLong))
        }
      }))
      try {
        When("automatic RetryNow is busy after actual durable task outcome admission")
        _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_async))
        val id = _success(fixture.engine.submit(List(task), fixture.context, JobSubmitOption(runMode = JobRunMode.Sync)))
        val stored = _success(store.load(id.value, _access)).get

        Then("terminal checkpoint refusal remains visible and no durable terminal success is published")
        requests.toVector shouldBe Vector(DurableJobLifecycleWriteBoundary.Admission,
          DurableJobLifecycleWriteBoundary.StartIntent, DurableJobLifecycleWriteBoundary.RunningIntent,
          DurableJobLifecycleWriteBoundary.TaskStartIntent, DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint,
          DurableJobLifecycleWriteBoundary.TerminalOutcomeCheckpoint)
        fixture.engine.durableLifecycleWriteFailureFacts.exists(fact => fact.jobid == id &&
          fact.failure == DurableJobLifecycleWriteFailure.TerminalOutcomeCheckpointRefused) shouldBe true
        stored.record.body.lifecycle.status shouldBe DurableJobLifecycleStatus.Running
        stored.record.body.result shouldBe DurableResultOutcome.Pending
        fixture.engine.getResult(id) shouldBe Some(JobResult.Failure(original))
        fixture.engine.query(id).get.retry.attemptCount shouldBe 0
        task.observations.get() shouldBe 0
        _success(events.query(EventStore.Query())).exists(_.name == "job.failed") shouldBe false
        fixture.engine.resourceSnapshot.executing shouldBe 0
      } finally fixture.engine.shutdown()
    }
  }

  "E8 bounded retry schedule" should afterWord(
    "in spec:job-operations-contract, example:E8, rules:R1,R7, phase:69.7"
  ) {
    "admit zero and 32 retries and positive subsecond or inclusive 24 hour delays" in {
      Given("docs/spec/job-operations-contract.md; R1,R7; E8; the retry policy's inclusive boundaries")
      val schedules = Vector(
        InMemoryJobEngine.RetrySchedule(Vector.empty, 0),
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ofNanos(1)), 32),
        InMemoryJobEngine.RetrySchedule(Vector(Duration.ofHours(24)), 32)
      )

      When("each valid schedule is attached to an engine")
      schedules.foreach { schedule =>
        val fixture = _fixture(schedule = schedule)
        try {
          Then("construction admits the closed policy and zero retries exhausts at the first failure")
          fixture.engine.resourceSnapshot.policy.maxretryattempts shouldBe 32
          if (schedule.maxRetries == 0) {
            val task = new RecordingTask(_ => TaskFailed(_failure(Disposition.UserAction.RetryNow)))
            val id = _success(fixture.engine.submit(List(task), fixture.context, _ephemeral_sync))
            fixture.engine.query(id).get.retry.attemptCount shouldBe 0
            fixture.engine.query(id).get.retry.exhausted shouldBe true
            fixture.engine.resourceSnapshot.pending shouldBe 0
          }
        } finally fixture.engine.shutdown()
      }
    }

    "exhaust a short delay vector and clock overflow without indexing or inventing attempts" in {
      Given("docs/spec/job-operations-contract.md; R7; E8; one 60ms delay for three possible retries and a maximum clock")
      val fixture = _fixture(schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60)), 3))
      val original = _failure(Disposition.UserAction.RetryLater)
      val task = new RecordingTask(_ => TaskFailed(original))
      try {
        When("the first retry becomes due and a second delay is unavailable")
        val id = _success(fixture.engine.submit(List(task), fixture.context, _ephemeral_sync))
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.clock.advanceBy(Duration.ofMillis(60))
        fixture.timer.fire()
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.engine.drainAll() shouldBe 1

        Then("only two actual executions occur and the missing delay is bounded terminal exhaustion")
        task.runs.get() shouldBe 2
        fixture.engine.getResult(id) shouldBe Some(JobResult.Failure(original))
        fixture.engine.query(id).get.retry.attemptCount shouldBe 1
        fixture.engine.query(id).get.retry.deadLetter shouldBe true
        fixture.engine.resourceSnapshot.pending shouldBe 0
      } finally fixture.engine.shutdown()
      val overflow = _fixture(initial = Instant.MAX)
      try {
        val id = _success(overflow.engine.submit(List(new RecordingTask(_ => TaskFailed(original))),
          overflow.context, _ephemeral_sync))
        val record = overflow.engine.runtimeState.runtimeJobs.get(id)
        record.retry.attemptCount shouldBe 0
        record.retry.exhausted shouldBe true
        record.retry.deadLetter shouldBe true
        record.retry.recoveryRequired shouldBe true
        record.retry.poison shouldBe false
        overflow.timer.calls.get() shouldBe 0
        overflow.engine.getResult(id) shouldBe Some(JobResult.Failure(original))
        overflow.engine.resourceSnapshot.pending shouldBe 0
      } finally overflow.engine.shutdown()
    }

    "execute the inclusive 32 retry attempts and exhaust at the actual cap" in {
      Given("docs/spec/job-operations-contract.md; R1,R7; E8; a RetryNow task and a schedule permitting 32 retries")
      val fixture = _fixture(schedule = InMemoryJobEngine.RetrySchedule(Vector.empty, 32))
      val original = _failure(Disposition.UserAction.RetryNow)
      val task = new RecordingTask(_ => TaskFailed(original))
      try {
        When("the admitted Sync attempt and all immediate retries complete unsuccessfully")
        val id = _success(fixture.engine.submit(List(task), fixture.context, _ephemeral_sync))
        val drained = fixture.engine.drainAll()

        Then("32 queued retries execute without overflow and terminal evidence retains the original conclusion")
        drained shouldBe 32
        task.runs.get() shouldBe 33
        fixture.engine.query(id).get.retry.attemptCount shouldBe 32
        fixture.engine.query(id).get.retry.exhausted shouldBe true
        fixture.engine.getResult(id) shouldBe Some(JobResult.Failure(original))
        fixture.engine.resourceSnapshot.pending shouldBe 0
        fixture.engine.resourceSnapshot.executing shouldBe 0
      } finally fixture.engine.shutdown()
    }
  }

  "E9 cancellation and lifecycle ownership" should afterWord(
    "in spec:job-operations-contract, example:E9, rules:R3,R8, phase:69.7"
  ) {
    "remove matching pending children and generations while quiescing drains admitted work" in {
      Given("docs/spec/job-operations-contract.md; R3,R8; E9; delayed, queued-child and unrelated pending work")
      val fixture = _fixture(JobResourcePolicy(8, 4, 1, 1024, 32))
      val child = new RecordingTask()
      val other = new RecordingTask()
      try {
        val parent = _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_async))
        _success(fixture.engine.enqueueTaskInJob(parent, child, fixture.context))
        val delayed = _success(fixture.engine.submit(List(child), fixture.context,
          _ephemeral_async.copy(scheduledStartAt = Some(_instant.plusSeconds(1)))))
        _success(fixture.engine.submit(List(other), fixture.context, _ephemeral_async))

        When("matching jobs are cancelled and new admission is quiesced")
        _success(_control(fixture, parent, JobControlCommand.Cancel))
        _success(_control(fixture, delayed, JobControlCommand.Cancel))
        fixture.engine.quiesce()
        val ids = fixture.ids.calls.get()

        Then("removed child counters settle, new work refuses and the unrelated admitted work drains")
        fixture.engine.runtimeState.runtimeJobs.get(parent).pendingTaskCount shouldBe 0
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.timer.pendingCount shouldBe 0
        fixture.engine.submit(Nil, fixture.context, _ephemeral_async).isSuccess shouldBe false
        fixture.engine.enqueueTaskInJob(parent, child, fixture.context).isSuccess shouldBe false
        _control(fixture, delayed, JobControlCommand.Retry).isSuccess shouldBe false
        fixture.ids.calls.get() shouldBe ids
        fixture.clock.advanceBy(Duration.ofSeconds(1))
        fixture.timer.fire(includeclosed = true)
        fixture.engine.drainAll() shouldBe 1
        other.runs.get() shouldBe 1
        child.runs.get() shouldBe 0
      } finally fixture.engine.shutdown()
      fixture.timer.shutdowns.get() shouldBe 0
    }

    "close only owned registrations and retain active execution until physical completion" in {
      Given("docs/spec/job-operations-contract.md; R8; E9; a shared timer, two State owners and an uninterruptible active task")
      val state = _state(JobResourcePolicy(8, 4, 1, 1024, 32))
      val first = _fixture(state = state)
      val second = _fixture(state = state, sharedtimer = Some(first.timer), sharedclock = Some(first.clock))
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val task = new RecordingTask(_ => {
        entered.countDown()
        var done = false
        while (!done) {
          try done = release.await(3, TimeUnit.SECONDS)
          catch { case _: InterruptedException => () }
        }
        _ok
      })
      val thread = new Thread(() => { val _ = first.engine.submit(List(task), first.context, _ephemeral_sync) })
      try {
        _success(first.engine.submit(Nil, first.context,
          _ephemeral_async.copy(scheduledStartAt = Some(_instant.plusSeconds(1)))))
        _success(second.engine.submit(Nil, second.context,
          _ephemeral_async.copy(scheduledStartAt = Some(_instant.plusSeconds(2)))))

        When("the first owner shuts down while its physical Sync task is still active")
        thread.start()
        entered.await(3, TimeUnit.SECONDS) shouldBe true
        first.engine.shutdown()

        Then("the other registration and active lease survive, and the shared timer remains available")
        first.engine.resourceSnapshot.pending shouldBe 1
        first.engine.resourceSnapshot.executing shouldBe 1
        first.timer.pendingCount shouldBe 1
        first.timer.shutdowns.get() shouldBe 0
        second.engine.drainOne() shouldBe false
      } finally {
        release.countDown()
        thread.join(3000L)
        first.engine.shutdown()
        second.engine.shutdown()
      }
      thread.isAlive shouldBe false
      first.engine.resourceSnapshot.executing shouldBe 0
    }
  }

  "E10 shared State admission and restart" should afterWord(
    "in spec:job-operations-contract, example:E10, rules:R2,R3,R6,R9, phase:69.7"
  ) {
    "share concurrent admission and restore delayed retry once after owner shutdown" in {
      Given("docs/spec/job-operations-contract.md; R2,R3,R6,R9; E10; two engines sharing two pending slots and a persisted delayed retry")
      val state = _state(JobResourcePolicy(8, 2, 1, 1024, 32))
      val first = _fixture(state = state)
      val second = _fixture(state = state)
      val start = new CountDownLatch(1)
      val outcomes = new java.util.concurrent.ConcurrentLinkedQueue[Boolean]()
      val threads = Vector.tabulate(8)(index => new Thread(() => {
        if (start.await(3, TimeUnit.SECONDS)) {
          val fixture = if (index % 2 == 0) first else second
          outcomes.add(fixture.engine.submit(Nil, fixture.context, _ephemeral_async).isSuccess)
        }
      }))
      try {
        When("callers race at the common finite capacity")
        threads.foreach(_.start())
        try { start.countDown(); threads.foreach(_.join(3000L)) }
        finally threads.foreach(_.interrupt())

        Then("the shared counters admit exactly two callers and policy attachment is frozen")
        import scala.jdk.CollectionConverters.*
        outcomes.asScala.count(identity) shouldBe 2
        first.engine.resourceSnapshot.pending shouldBe 2
        second.engine.resourceSnapshot shouldBe first.engine.resourceSnapshot
        state.bindResourcePolicy(JobResourcePolicy.default).isSuccess shouldBe false
      } finally { first.engine.shutdown(); second.engine.shutdown() }
      val retryfirst = _fixture(state = state)
      // Explicit outcomes keep the runtime record itself as the restart authority.
      val retrytask = new RecordingTask(_ => TaskFailed(_failure(Disposition.UserAction.RetryLater)))
      val id = _success(retryfirst.engine.submit(List(retrytask), retryfirst.context,
        JobSubmitOption(runMode = JobRunMode.Sync)))
      retryfirst.engine.resourceSnapshot.pending shouldBe 1
      val simultaneous = _fixture(state = state)
      try {
        simultaneous.timer.pendingCount shouldBe 0
        retryfirst.engine.shutdown()
        val restored = _fixture(state = state)
        try {
          restored.timer.pendingCount shouldBe 1
          restored.engine.resourceSnapshot.pending shouldBe 1
          restored.clock.advanceBy(Duration.ofMillis(60))
          restored.timer.fire(includeclosed = true)
          restored.timer.fire(includeclosed = true)
          restored.engine.drainOne() shouldBe true
          retrytask.runs.get() shouldBe 2
          restored.engine.resourceSnapshot.executing shouldBe 0
        } finally restored.engine.shutdown()
      } finally { retryfirst.engine.shutdown(); simultaneous.engine.shutdown() }
      retrytask.runs.get() shouldBe 2
    }

    "refuse the whole delayed bootstrap batch before its first timer and unwind timer failure" in {
      Given("docs/spec/job-operations-contract.md; R9; E10; two delayed records and one pending slot")
      val state = _state(JobResourcePolicy(4, 1, 1, 1024, 32))
      val task = new RecordingTask()
      Vector("batchone", "batchtwo").foreach { label =>
        val id = _id(label)
        state.durableJobs.put(id, _live_record(id, task).copy(scheduledStartAt = Some(_instant.plusSeconds(1))))
      }
      val clock = new ManualJobTimeSource(_instant)
      val timer = new RecordingTimer(clock)

      When("construction attempts the complete oversupplied batch")
      an[IllegalArgumentException] should be thrownBy new InMemoryJobEngine(
        runtimeState = state, retrySchedule = InMemoryJobEngine.RetrySchedule(Vector.empty, 0),
        timeSource = clock, timer = Some(timer)
      )(scala.concurrent.ExecutionContext.global)

      Then("no first registration or task effect occurs and records remain complete")
      timer.calls.get() shouldBe 0
      state.durableJobs.size() shouldBe 2
      state._resource_budget.snapshot.pending shouldBe 0
      task.runs.get() shouldBe 0
      val fitting = _state(JobResourcePolicy(4, 2, 1, 1024, 32))
      fitting.durableJobs.putAll(state.durableJobs)
      val failing = new RecordingTimer(clock, failafter = 1)
      an[IllegalArgumentException] should be thrownBy new InMemoryJobEngine(
        runtimeState = fitting, retrySchedule = InMemoryJobEngine.RetrySchedule(Vector.empty, 0),
        timeSource = clock, timer = Some(failing)
      )(scala.concurrent.ExecutionContext.global)
      fitting._resource_budget.snapshot.pending shouldBe 0
      failing.pendingCount shouldBe 0
      failing.shutdowns.get() shouldBe 0
      task.runs.get() shouldBe 0
    }

    "keep seeded shared-engine admission, drain and cancellation sequences inside every finite bound" in {
      Given("docs/spec/job-operations-contract.md; R1,R2,R3,R4,R8; E10; generated lower policies and two actual State owners")
      val random = new Random(69709310L)

      When("seeded operations submit, claim, cancel and close owners across generated finite limits")
      (0 until 12).foreach { _ =>
        val policy = JobResourcePolicy(1 + random.nextInt(6), 1 + random.nextInt(4), 1, 2, 32)
        val state = _state(policy)
        val first = _fixture(state = state)
        val second = _fixture(state = state)
        val fixtures = Vector(first, second)
        val identities = ArrayBuffer.empty[JobId]
        val task = new RecordingTask()
        try {
          (0 until 40).foreach { _ =>
            val fixture = fixtures(random.nextInt(2))
            random.nextInt(5) match {
              case 0 | 1 =>
                val option = if (random.nextBoolean()) _ephemeral_async else _ephemeral_sync
                fixture.engine.submit(List.fill(random.nextInt(4))(task), fixture.context, option)
                  .toOption.foreach(identities += _)
              case 2 => fixture.engine.drainOne()
              case 3 if identities.nonEmpty =>
                _control(fixture, identities(random.nextInt(identities.size)), JobControlCommand.Cancel)
              case 4 => fixture.engine.quiesce()
              case _ => ()
            }
            val snapshot = fixture.engine.resourceSnapshot
            snapshot.retained should (be >= 0 and be <= policy.maxretainedjobs)
            snapshot.pending should (be >= 0 and be <= policy.maxpendingwork)
            snapshot.executing shouldBe 0
            first.engine.resourceSnapshot shouldBe second.engine.resourceSnapshot
            snapshot.retained shouldBe state.runtimeJobs.size()
          }
        } finally { first.engine.shutdown(); second.engine.shutdown() }

        Then("each generated sequence preserves retained identities and returns all physical work capacity")
        first.engine.resourceSnapshot.retained shouldBe identities.distinct.size
        first.engine.resourceSnapshot.pending shouldBe 0
        first.engine.resourceSnapshot.executing shouldBe 0
      }
    }
  }

  "E11 signed native registration gates" should afterWord(
    "in spec:job-operations-contract, example:E11, rules:R2,R9,R10, phase:69.7"
  ) {
    "refuse retained, pending, task and recovered retry oversupply before runtime insertion or checkpoints" in {
      Given("docs/spec/job-operations-contract.md; R2,R9,R10; E11; signed native records and explicit source and reconstruction port")
      Vector("retained", "pending", "tasks", "retry", "attempts").foreach { boundary =>
        val fixture = _fixture(JobResourcePolicy(1, 1, 1, 1, 1),
          schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60)), 1))
        given ExecutionContext = fixture.context
        val store = new DurableJobStore(EntityStore.standard())
        val id = _id(s"recover$boundary")
        val record = _native_record(id,
          retrymax = if (boundary == "retry") 2 else if (boundary == "attempts") 3 else 1,
          attempts = if (boundary == "attempts") 2 else 0)
        val observed = JobDurableLifetimeRecoveryFixtures.publish(store, record)
        JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, observed.record, fixture.context)
        val calls = new AtomicInteger(0)
        val task = new RecordingTask()
        val port = _port(calls, fixture.context,
          List.fill(if (boundary == "tasks") 2 else 1)(task))
        try {
          if (boundary == "retained") _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_sync))
          if (boundary == "pending") _success(fixture.engine.submit(Nil, fixture.context, _ephemeral_async))

          When("the coordinator reads the native record and offers runtime registration beyond a finite bound")
          val before = fixture.engine.resourceSnapshot
          val report = _success(fixture.engine.recoverDurableStartup(_source(Vector(id)), store, port, 1))

          Then("registration is a closed refusal after source/port lookup, preserving native revision and timestamp")
          report.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
          calls.get() shouldBe 1
          fixture.engine.resourceSnapshot shouldBe before
          fixture.engine.query(id) shouldBe None
          _success(store.load(id.value, _access)) shouldBe Some(observed)
          fixture.timer.pendingCount shouldBe 0
          task.runs.get() shouldBe 0
        } finally fixture.engine.shutdown()
      }
    }

    "retain canonical authorization, duplicate refusal and observed timestamps for admitted runtime and terminal facts" in {
      Given("docs/spec/job-operations-contract.md; R9,R10; E11; signed current native runtime and terminal snapshots")
      val fixture = _fixture(JobResourcePolicy(2, 2, 1, 1024, 32))
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val id = _id("admittedruntime")
      val terminal = _id("terminalprojection")
      val over = _id("terminalover")
      val observed = JobDurableLifetimeRecoveryFixtures.publish(store, _native_record(id))
      JobDurableLifetimeRecoveryFixtures.bind(fixture.engine, store, observed.record, fixture.context)
      JobDurableLifetimeRecoveryFixtures.publishTerminal(store, _native_record(terminal, terminal = true))
      JobDurableLifetimeRecoveryFixtures.publishTerminal(store, _native_record(over, terminal = true))
      val calls = new AtomicInteger(0)
      val task = new RecordingTask()
      val port = _port(calls, fixture.context, List(task))
      try {
        When("authorized runtime registration, duplicate replay and terminal projection share the retained budget")
        val admitted = _success(fixture.engine.recoverDurableStartup(_source(Vector(id)), store, port, 1))
        val duplicate = _success(fixture.engine.recoverDurableStartup(_source(Vector(id)), store, port, 1))
        val projected = _success(fixture.engine.recoverDurableTerminalFacts(_source(Vector(terminal)), store, 1))
        val refused = _success(fixture.engine.recoverDurableTerminalFacts(_source(Vector(over)), store, 1))
        val unauthorized = _success(fixture.engine.recoverDurableStartup(
          _source(Vector(id), _access.copy(subjectId = "wrong")), store, port, 1))

        Then("current authority and timestamps survive while duplicates, hostile access and one-over terminal facts refuse")
        admitted.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Registered)
        duplicate.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
        projected.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Registered)
        refused.candidates.map(_.fact) shouldBe Vector(DurableJobTerminalProjectionFact.Refused)
        unauthorized.candidates.map(_.fact) shouldBe Vector(DurableJobRuntimeRehydrationFact.Refused)
        calls.get() shouldBe 2
        fixture.engine.query(id).get.updatedAt shouldBe observed.record.body.identity.updatedAt
        fixture.engine.resourceSnapshot.retained shouldBe 2
        fixture.engine.resourceSnapshot.pending shouldBe 1
        fixture.engine.durableTerminalProjection(over) shouldBe None
        _success(store.load(id.value, _access)) shouldBe Some(observed)
        task.runs.get() shouldBe 0
      } finally fixture.engine.shutdown()
    }

    "refuse a terminal projection carrying an older signed native snapshot without consuming retained capacity" in {
      Given("docs/spec/job-operations-contract.md; R9,R10; E11; a signed terminal projection and a newer authoritative checkpoint")
      val fixture = _fixture(JobResourcePolicy(1, 1, 1, 1024, 32))
      given ExecutionContext = fixture.context
      val store = new DurableJobStore(EntityStore.standard())
      val id = _id("staleprojection")
      val old = JobDurableLifetimeRecoveryFixtures.publishTerminal(store, _native_record(id, terminal = true))
      val candidate = _success(_source(Vector(id)).candidates(1)).head
      val decision = _success(DurableJobRecovery.classify(old.record, candidate.replayEvidence))
      val projection = _success(DurableJobTerminalProjection.project(candidate, old, decision))
      val changed = _success(DurableJobRecord.createV2(old.record.body.copy(identity =
        old.record.body.identity.copy(revision = old.record.body.identity.revision + 1L, updatedAt = _instant.plusSeconds(21)))))
      val current = _success(store.checkpoint(old, changed, _access))
      try {
        When("the stale projected snapshot is presented alongside current native authority")
        val before = fixture.engine.resourceSnapshot
        val fact = fixture.engine._register_durable_terminal_fact(candidate, current, decision, projection,
          store, DurableJobLifetimeQuotaPolicy.default, fixture.context)

        Then("the mismatch is a closed refusal with no insertion, checkpoint or resource consumption")
        fact shouldBe DurableJobTerminalProjectionFact.Refused
        fixture.engine.resourceSnapshot shouldBe before
        fixture.engine.durableTerminalProjection(id) shouldBe None
        _success(store.load(id.value, _access)) shouldBe Some(current)
        fixture.timer.pendingCount shouldBe 0
      } finally fixture.engine.shutdown()
    }
  }

  private val _instant = Instant.parse("2026-10-01T00:00:00Z")
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _ephemeral_async = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)
  private val _ephemeral_sync = _ephemeral_async.copy(runMode = JobRunMode.Sync)
  private val _ok = TaskSucceeded(OperationResponse.Scalar("ok"))
  private val _control_policy = new JobControlPolicy {
    def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.unit
    }
  }

  private final class CountingIds extends IdGenerationContext {
    private val _delegate = IdGenerationContext.default(IdGenerationContext.IdNamespace("cncf", "quota"))
    val calls = new AtomicInteger(0)
    def namespace: IdGenerationContext.IdNamespace = _delegate.namespace
    def entityId(collection: EntityCollectionId, purpose: String): EntityId = {
      calls.incrementAndGet()
      _delegate.entityId(collection, purpose)
    }
    def opaqueId(purpose: String): String = {
      calls.incrementAndGet()
      _delegate.opaqueId(purpose)
    }
  }

  private final class RecordingTask(
    body: ExecutionContext => TaskOutcome = _ => TaskSucceeded(OperationResponse.Scalar("ok")),
    compensation: Option[JobTask] = None
  ) extends JobTask {
    val actionId: ActionId = ActionId("cncf", "quota", Some(_instant), Some("task"))
    val runs = new AtomicInteger(0)
    val admissionfailures = new AtomicInteger(0)
    val observations = new AtomicInteger(0)
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def componentName: Option[String] = Some("component-a")
    override def serviceName: Option[String] = Some("service-a")
    override def operationName: Option[String] = Some("run")
    override def transactionRole: Option[String] = Some("own")
    override def transactionScope: Option[String] = Some("per-task")
    override def compensationTask: Option[JobTask] = compensation
    override def compensationActionRef: Option[String] = compensation.map(_ => "compensate")
    def run(ctx: ExecutionContext): TaskOutcome = { runs.incrementAndGet(); body(ctx) }
    override def observeAdmissionFailure(conclusion: Conclusion, ctx: ExecutionContext): Unit = {
      val _ = conclusion
      val _ = ctx
      admissionfailures.incrementAndGet()
    }
    override def observeCanonicalOutcome(outcome: TaskOutcome, ctx: ExecutionContext, cancelled: Boolean): Unit = {
      val _ = outcome
      val _ = ctx
      val _ = cancelled
      observations.incrementAndGet()
    }
  }

  private final class RecordingTimer(clock: ManualJobTimeSource, failafter: Int = Int.MaxValue) extends JobTimer {
    private final case class Entry(dueat: Instant, body: () => Unit, var closed: Boolean = false)
    private val _entries = ArrayBuffer.empty[Entry]
    val calls = new AtomicInteger(0)
    val shutdowns = new AtomicInteger(0)
    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = synchronized {
      if (calls.incrementAndGet() > failafter) throw new IllegalStateException("PRIVATE-PROVIDER/PATH")
      val entry = Entry(dueat, () => body)
      _entries += entry
      new JobTimerRegistration {
        def close(): Unit = RecordingTimer.this.synchronized { entry.closed = true }
      }
    }
    def fire(includeclosed: Boolean = false): Unit = {
      val entries = synchronized {
        _entries.filter(entry => (includeclosed || !entry.closed) && !entry.dueat.isAfter(clock.now())).toVector
      }
      entries.foreach(_.body())
    }
    def pendingCount: Int = synchronized { _entries.count(!_.closed) }
    override def shutdown(): Unit = { shutdowns.incrementAndGet(); () }
  }

  private final case class Fixture(
    engine: InMemoryJobEngine,
    clock: ManualJobTimeSource,
    timer: RecordingTimer,
    context: ExecutionContext,
    ids: CountingIds,
    policy: JobResourcePolicy
  )

  private def _state(policy: JobResourcePolicy): InMemoryJobEngine.State = {
    val state = InMemoryJobEngine.State()
    _success(state.bindResourcePolicy(policy))
    state
  }

  private def _fixture(
    policy: JobResourcePolicy = JobResourcePolicy.default,
    state: InMemoryJobEngine.State = null,
    auto: Boolean = false,
    schedule: InMemoryJobEngine.RetrySchedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60)), 3),
    failafter: Int = Int.MaxValue,
    initial: Instant = Instant.parse("2026-10-01T00:00:00Z"),
    sharedtimer: Option[RecordingTimer] = None,
    sharedclock: Option[ManualJobTimeSource] = None
  ): Fixture = {
    val actualstate = Option(state).getOrElse(_state(policy))
    val clock = sharedclock.getOrElse(new ManualJobTimeSource(initial))
    val timer = sharedtimer.getOrElse(new RecordingTimer(clock, failafter))
    val ids = new CountingIds
    val context = ExecutionContext.withIdGenerationContext(ExecutionContext.test(), ids)
    val engine = new InMemoryJobEngine(actualstate, schedule,
      InMemoryJobEngine.SchedulerConfig(1, auto), clock, Some(timer))(scala.concurrent.ExecutionContext.global)
    Fixture(engine, clock, timer, context, ids, actualstate._resource_budget.policy)
  }

  private def _control(fixture: Fixture, id: JobId, command: JobControlCommand): Consequence[JobControlResponse] =
    fixture.engine.control(id, JobControlRequest(command), _control_policy)(using fixture.context)

  private def _events(store: EventStore): Int = _success(store.query(EventStore.Query())).size

  private def _failure(action: Disposition.UserAction): Conclusion =
    Conclusion.simple("original failure").copy(disposition = Disposition(action))

  private def _id(entropy: String): JobId = JobId("cncf", "quota", Some(_instant), Some(entropy))

  private def _live_record(id: JobId, task: JobTask): JobRecord =
    JobRecord(id, List(task), ExecutionContext.test(), JobStatus.Submitted, None,
      JobPersistencePolicy.Persistent, JobRunMode.Async, 0, None, _instant, _instant,
      Vector.empty, timeline = Vector.empty, debug = JobDebugInfo(None, Map.empty, Vector.empty))

  private def _source(ids: Vector[JobId], access: DurableJobRecordAccess = _access): DurableJobStartupRecoverySource =
    new DurableJobStartupRecoverySource {
      def candidates(maxCandidates: Int): Consequence[Vector[DurableJobStartupRecoveryCandidate]] =
        Consequence.success(ids.take(maxCandidates).zipWithIndex.map { case (id, index) =>
          DurableJobStartupRecoveryCandidate(index.toLong, id.value, access, DurableReplayEvidence(
            Some("idempotency"), Some("input"), Some("definition"), Some("authorization"),
            Some("provider"), Some("compatibility")
          ))
        })
    }

  private def _port(calls: AtomicInteger, context: ExecutionContext, tasks: List[JobTask]): DurableJobRuntimeRehydrationPort =
    new DurableJobRuntimeRehydrationPort {
      def rehydrate(candidate: DurableJobStartupRecoveryCandidate, snapshot: DurableJobStoreSnapshot,
        decision: DurableJobRecoveryDecision): Consequence[DurableJobRuntimeRehydration] = {
        val _ = snapshot
        val _ = decision
        calls.incrementAndGet()
        Consequence.success(DurableJobRuntimeRehydration(_success(JobId.parse(candidate.jobId)), tasks, context, None, None))
      }
    }

  private def _native_record(id: JobId, terminal: Boolean = false, retrymax: Int = 3, attempts: Int = 0): DurableJobRecord = {
    val evidence = _evidence(1L)
    _success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(id.value, 1L, _instant, _instant.plusSeconds(20)), evidence.projection.authorization,
      DurableJobLifecycle(if (terminal) DurableJobLifecycleStatus.Succeeded else DurableJobLifecycleStatus.Submitted,
        0, DurableRunMode.Async, DurableRetryEvidence(Vector.tabulate(attempts)(index =>
          DurableAttemptEvidence(index + 1, _instant.plusMillis(index.toLong),
            Some(_instant.plusMillis(index.toLong + 1)), DurableAttemptOutcome.Failed,
            Some(DurableFailureSummary("execution", "failed", "failure", retryable = true)))
        ), retrymax, None, false, false),
        DurableScheduleState(Some(_instant.plusSeconds(30)), None, None)),
      evidence.projection.tasks.map(task => if (terminal) task.copy(transaction =
        task.transaction.copy(role = DurableTransactionRole.Own, scope = DurableTransactionScope.PerTask, outcome = DurableTransactionOutcome.Committed)) else task), Vector.empty,
      if (terminal) DurableResultOutcome.Succeeded(DurableValue.Absent) else DurableResultOutcome.Pending,
      Vector.empty, Vector.empty, None, evidence.projection.definitionSnapshot, evidence.projection.retention
    )))
  }

  private def _evidence(revision: Long): DurableJobLifecycleWriteEvidence =
    DurableJobLifecycleWriteEvidence(DurableJobProjectionEvidence(
      revision,
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      Vector(DurableTaskDescriptor(TaskId("cncf", "quota", Some(_instant), Some("task001")).value, None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component-a", Some("service-a"), "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.None, DurableTransactionScope.None, DurableTransactionOutcome.Pending), None)),
      Vector.empty, DurableResultOutcome.Pending, DurableRetryEvidence(Vector.empty, 3, None, false, false),
      Vector.empty, None,
      DurableDefinitionSnapshot("definition001", "quota-job", 1, 1L, "a" * 64, None, Some("jcl"), Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
    ), _access)

  private def _descriptor(model: JobTaskReadModel): DurableTaskDescriptor =
    DurableTaskDescriptor(model.taskId.value, model.parentTaskId.map(_.value), DurableTaskKind.Operation,
      DurableTaskTarget("operation", DurableOperationReference("component-a", Some("service-a"), "run", None)),
      DurableTaskRelation(DurableTaskRelationKind.Root, model.parentTaskId.map(_.value)),
      DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask,
        model.status match {
          case JobTaskStatus.Running => DurableTransactionOutcome.Pending
          case JobTaskStatus.Succeeded => DurableTransactionOutcome.Committed
          case JobTaskStatus.Failed => DurableTransactionOutcome.Failed
        }), None)

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
