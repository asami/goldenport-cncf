package org.goldenport.cncf.job

import java.time.Duration
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.conclusion.Disposition
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.event.EventStore
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed

/*
 * @since   Oct. 1, 2026
 * @version Oct. 1, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobLifetimeAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobLifetimeFixtures.*
  import JobInputBoundsFixtures.{instant, sync, success, input, descriptor, access}

  "Paid State lifetime admission (spec:job-lifetime-resource-contract, phase:69.7, slice:JM69-09B3B3C2A)" should {
    "work batches" which {
      "LS1 preserve Sync/Async Persistent/Ephemeral roots, empty work and the full 1024-task batch" in {
        Given("real manual engines with unchanged input, modes, results and complete task history")
        for (mode <- Vector(JobRunMode.Sync, JobRunMode.Async);
             persistence <- Vector(JobPersistencePolicy.Persistent, JobPersistencePolicy.Ephemeral)) {
          val fixture = fresh()
          val task = new Task
          val supplied = input(Vector(descriptor))
          try {
            When("a paid root dispatches in its original mode")
            val jobid = success(fixture.engine.submit(List(task), fixture.context,
              sync.copy(runMode = mode, persistence = persistence, input = Some(supplied))))
            if (mode == JobRunMode.Async) fixture.engine.drainAll() shouldBe 1
            Then("the root retains input, result and task facts with one payment and released physical work")
            fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
            fixture.engine._get_record(jobid).get.input shouldBe Some(supplied)
            fixture.engine.getResult(jobid) shouldBe Some(JobResult.Success(OperationResponse.Scalar("ok")))
            fixture.engine.queryTasks(jobid).get.tasks.size shouldBe 1
            paid(fixture, jobid) shouldBe JobLifetimeSnapshot(2L, 96L, 1L, 1L)
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.resourceSnapshot.executing shouldBe 0
            task.runs.get() shouldBe 1
          } finally fixture.engine.shutdown()
        }
        for (count <- Vector(0, 1024)) {
          val fixture = fresh()
          val task = new Task
          try {
            When("the complete empty or maximum base batch is admitted")
            val jobid = success(fixture.engine.submit(List.fill(count)(task), fixture.context, sync))
            Then("all normal outcomes are retained and companion capacity was prepaid")
            task.runs.get() shouldBe count
            fixture.engine.queryTasks(jobid, 0, 2048).get.tasks.size shouldBe count
            paid(fixture, jobid) shouldBe JobLifetimeSnapshot(2L * count, 32L + 64L * count, 1L, 1L)
          } finally fixture.engine.shutdown()
        }
      }

      "LS2 admit inclusive lower-only policies and freeze binding before actual attachment" in {
        Given("positive lower policies and zero, negative and upper-plus-one variants")
        val invalid = Vector(JobLifetimePolicy(maxchargedtasks = 0), JobLifetimePolicy(maxchargedtasks = -1),
          JobLifetimePolicy(maxchargedtasks = 8193), JobLifetimePolicy(maxhistorycredits = 0),
          JobLifetimePolicy(maxhistorycredits = 524289), JobLifetimePolicy(maxadmissions = 0),
          JobLifetimePolicy(maxadmissions = -1), JobLifetimePolicy(maxadmissions = 16385))
        invalid.foreach { policy =>
          val state = InMemoryJobEngine.State()
          When("the actual State attempts to bind an invalid policy")
          val result = state.bindLifetimePolicy(policy)
          Then("the fixed capacity failure leaves the default policy available")
          refusal(result)
          state._lifetime_budget.policy shouldBe JobLifetimePolicy.default
        }
        val fixture = fresh(JobLifetimePolicy(2, 96, 1))
        try {
          val task = new Task
          When("the inclusive batch is submitted after the first attachment")
          val jobid = success(fixture.engine.submit(List(task), fixture.context, sync))
          val binding = fixture.engine.runtimeState.bindLifetimePolicy(JobLifetimePolicy.default)
          Then("the exact lower boundary succeeds and subsequent binding cannot replace its payment")
          paid(fixture, jobid) shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
          refusal(binding)
          fixture.engine.runtimeState._lifetime_budget.policy shouldBe JobLifetimePolicy(2, 96, 1)
          task.runs.get() shouldBe 1
        } finally fixture.engine.shutdown()
      }

      "LS3 refuse a complete multi-task root before IDs, clocks, getters, native evidence or leases" in {
        Given("two-task candidates at exact task/history limits and one-credit or one-task oversupply")
        for ((policy, count, accepted) <- Vector((JobLifetimePolicy(4, 160, 1), 2, true),
             (JobLifetimePolicy(4, 159, 1), 2, false), (JobLifetimePolicy(4, 224, 1), 3, false))) {
          val fixture = fresh(policy)
          val task = new Task
          val evidencecalls = new AtomicInteger(0)
          fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(
            new DurableJobStore(EntityStore.standard()), _ => {
              evidencecalls.incrementAndGet()
              Consequence.success(JobInputBoundsFixtures.evidence(1L))
            }))
          try {
            val before = facts(fixture, task)
            When("the actual engine offers the complete base batch")
            val result = fixture.engine.submit(List.fill(count)(task), fixture.context, sync)
            Then("only the exact fitting batch changes task, resource and history facts")
            if (accepted) {
              val jobid = success(result)
              paid(fixture, jobid).chargedtasks shouldBe 4L
              paid(fixture, jobid).historycredits shouldBe 160L
              task.runs.get() shouldBe 2
            } else {
              refusal(result)
              facts(fixture, task) shouldBe before
              evidencecalls.get() shouldBe 0
              fixture.engine.durableLifecycleWriteFailureFacts shouldBe empty
            }
          } finally fixture.engine.shutdown()
        }
      }

      "LS4 pay each Sync, queued Async and nested inline expansion exactly once through drain and Cancel" in {
        Given("a root and two paid child slots on the same Job")
        val fixture = fresh(JobLifetimePolicy(6, 288, 3))
        val child = new Task
        val nested = new Task(ctx => {
          success(fixture.engine.runTaskInJobSync(ctx.jobContext.jobId.get, child, ctx))
          TaskSucceeded(OperationResponse.Scalar("ok"))
        })
        val root = new Task(ctx => {
          success(fixture.engine.runTaskInJobSync(ctx.jobContext.jobId.get, nested, ctx))
          TaskSucceeded(OperationResponse.Scalar("ok"))
        })
        try {
          When("nested Sync work runs inside the one original physical execution lease")
          val jobid = success(fixture.engine.submit(List(root), fixture.context, sync))
          val before = facts(fixture, child)
          val record = fixture.engine._get_record(jobid).get
          val refused = fixture.engine.enqueueTaskInJob(jobid, child, fixture.context)
          Then("three logical payments remain occupied after all physical execution finishes")
          paid(fixture, jobid) shouldBe JobLifetimeSnapshot(6, 288, 3, 3)
          refusal(refused)
          facts(fixture, child) shouldBe before
          fixture.engine._get_record(jobid).get shouldBe record
          child.runs.get() shouldBe 1
          fixture.engine.resourceSnapshot.executing shouldBe 0
        } finally fixture.engine.shutdown()
        val queued = fresh(JobLifetimePolicy(4, 192, 2))
        val task = new Task
        try {
          When("a queued root and its accepted same-Job Async child are canceled before dispatch")
          val jobid = success(queued.engine.submit(List(task), queued.context, sync.copy(runMode = JobRunMode.Async)))
          success(queued.engine.enqueueTaskInJob(jobid, task, queued.context))
          val paidbefore = paid(queued, jobid)
          success(control(queued, jobid, JobControlCommand.Cancel))
          queued.engine.drainAll() shouldBe 0
          Then("queue removal releases its physical lease and retains both lifetime admissions")
          paid(queued, jobid).chargedtasks shouldBe paidbefore.chargedtasks
          paid(queued, jobid).historycredits shouldBe paidbefore.historycredits
          paid(queued, jobid).admissions shouldBe 2L
          queued.engine.resourceSnapshot.pending shouldBe 0
          task.runs.get() shouldBe 0
          queued.engine.queryTimeline(jobid).get.events.map(_.kind) should contain("job.same-job-async.queued")
        } finally queued.engine.shutdown()
        val drained = fresh(JobLifetimePolicy(4, 192, 2))
        try {
          When("an accepted child instead drains through the real queue")
          val jobid = success(drained.engine.submit(List(new Task), drained.context, sync))
          val task = new Task
          success(drained.engine.enqueueTaskInJob(jobid, task, drained.context))
          val payment = paid(drained, jobid)
          drained.engine.drainAll() shouldBe 1
          Then("normal dispatch consumes its prepaid Task ID without a second charge")
          paid(drained, jobid) shouldBe payment
          task.runs.get() shouldBe 1
        } finally drained.engine.shutdown()
      }

      "LS5 reserve complete explicit, Immediate and Delayed Retry batches and retain original exhaustion" in {
        Given("a failing two-task root with one additional full Retry payment")
        val explicit = fresh(JobLifetimePolicy(8, 320, 2))
        val failed = new Task(_ => TaskFailed(Conclusion.simple("original")))
        val ordinary = new Task
        try {
          When("the failed root is explicitly retried and drained")
          val jobid = success(explicit.engine.submit(List(ordinary, failed), explicit.context, sync))
          success(control(explicit, jobid, JobControlCommand.Retry))
          explicit.engine.drainAll() shouldBe 1
          val before = facts(explicit, failed)
          val record = explicit.engine._get_record(jobid).get
          val result = control(explicit, jobid, JobControlCommand.Retry)
          Then("each attempt pays its full batch and clearing retry state grants no new capacity")
          paid(explicit, jobid).chargedtasks shouldBe 8L
          paid(explicit, jobid).historycredits shouldBe 320L
          paid(explicit, jobid).admissions shouldBe 2L
          refusal(result)
          facts(explicit, failed) shouldBe before
          explicit.engine._get_record(jobid).get shouldBe record
          failed.runs.get() shouldBe 2
        } finally explicit.engine.shutdown()
        for (action <- Vector(Disposition.UserAction.RetryNow, Disposition.UserAction.RetryLater)) {
          val fixture = fresh(JobLifetimePolicy(4, 192, 2))
          val original = failure(action)
          val task = new Task(_ => TaskFailed(original))
          try {
            When("the ordinary failure schedules one affordable automatic attempt")
            val jobid = success(fixture.engine.submit(List(task), fixture.context, sync))
            if (action == Disposition.UserAction.RetryLater)
              fixture.timer.advanceBy(Duration.ofMinutes(1)) shouldBe 1
            fixture.engine.drainAll() shouldBe 1
            Then("the next attempt exhausts lifetime admission with the unchanged original failure")
            task.runs.get() shouldBe 2
            paid(fixture, jobid).admissions shouldBe 2L
            fixture.engine.getResult(jobid) shouldBe Some(JobResult.Failure(original))
            val retry = fixture.engine._get_record(jobid).get.retry
            retry.attemptCount shouldBe 1
            retry.exhausted shouldBe true
            retry.deadLetter shouldBe true
            retry.recoveryRequired shouldBe true
            fixture.timer.pendingCount shouldBe 0
            fixture.engine.resourceSnapshot.pending shouldBe 0
            fixture.engine.queryTimeline(jobid).get.events.map(_.kind) should contain("job.retry.exhausted")
          } finally fixture.engine.shutdown()
        }
      }

      "LS6 pay actual Suspend/Resume, keep replay free and permit prepaid Cancel at exhaustion" in {
        Given("a queued root with exactly three control applications left")
        val fixture = fresh(JobLifetimePolicy(4, 256, 4))
        val task = new Task
        try {
          When("Suspend, Resume and Suspend exhaust the finite control admissions")
          val jobid = success(fixture.engine.submit(List(task), fixture.context, sync.copy(runMode = JobRunMode.Async)))
          success(control(fixture, jobid, JobControlCommand.Suspend))
          val suspended = paid(fixture, jobid)
          success(control(fixture, jobid, JobControlCommand.Suspend)).changed shouldBe false
          paid(fixture, jobid) shouldBe suspended
          success(control(fixture, jobid, JobControlCommand.Resume))
          success(control(fixture, jobid, JobControlCommand.Suspend))
          val exhausted = paid(fixture, jobid)
          val before = facts(fixture, task)
          val result = control(fixture, jobid, JobControlCommand.Resume)
          Then("the next Apply refuses before effects, replay remains free and Cancel uses paid safety capacity")
          refusal(result)
          facts(fixture, task) shouldBe before
          success(control(fixture, jobid, JobControlCommand.Cancel)).changed shouldBe true
          paid(fixture, jobid).historycredits shouldBe exhausted.historycredits
          paid(fixture, jobid).admissions shouldBe exhausted.admissions
          paid(fixture, jobid).cancelcredits shouldBe 0L
          val canceled = facts(fixture, task)
          success(control(fixture, jobid, JobControlCommand.Cancel)).changed shouldBe false
          facts(fixture, task) shouldBe canceled
          fixture.engine.queryTimeline(jobid).get.events.count(_.kind == "job.cancelled") shouldBe 1
          events(fixture).count(_.name == "job.cancelled") shouldBe 1
          task.runs.get() shouldBe 0
          fixture.engine.resourceSnapshot.pending shouldBe 0
        } finally fixture.engine.shutdown()
        val retried = fresh(JobLifetimePolicy(4, 192, 2))
        try {
          When("a canceled work admission is retried then canceled again")
          val jobid = success(retried.engine.submit(List(new Task), retried.context, sync.copy(runMode = JobRunMode.Async)))
          success(control(retried, jobid, JobControlCommand.Cancel))
          success(control(retried, jobid, JobControlCommand.Retry))
          success(control(retried, jobid, JobControlCommand.Cancel))
          Then("the new work payment supplied its own single Cancel allowance")
          paid(retried, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 0)
          retried.engine.queryTimeline(jobid).get.events.count(_.kind == "job.cancelled") shouldBe 2
        } finally retried.engine.shutdown()
      }

      "LS7 settle successful, missing and failed compensations after new-work exhaustion" in {
        for (kind <- Vector("succeeded", "missing", "failed")) {
          Given("a fully paid committed task and a failing normal task with no new expansion capacity")
          val fixture = fresh(JobLifetimePolicy(4, 160, 1))
          val compensation = new Task(_ => if (kind == "failed") TaskFailed(Conclusion.simple("compensation failure"))
            else TaskSucceeded(OperationResponse.Scalar("undone")))
          val original = new Task(companion = if (kind == "missing") None else Some(compensation))
          val failed = new Task(_ => TaskFailed(Conclusion.simple("original failure")))
          try {
            When("the paid root reaches the failure and its owed compensation selection")
            val jobid = success(fixture.engine.submit(List(original, failed), fixture.context, sync))
            val before = paid(fixture, jobid)
            val result = fixture.engine.runTaskInJobSync(jobid, new Task, fixture.context)
            Then("normal failure and every selected compensation fact remain complete without another admission")
            refusal(result)
            paid(fixture, jobid) shouldBe before
            original.compensations.get() shouldBe 1
            compensation.runs.get() shouldBe (if (kind == "missing") 0 else 1)
            val model = fixture.engine.queryTasks(jobid, 0, 100).get.tasks.find(_.compensationStatus.contains(kind)).get
            model.compensationStatus shouldBe Some(kind)
            if (kind != "succeeded") {
              fixture.engine._get_record(jobid).get.retry.recoveryRequired shouldBe true
              fixture.engine._get_record(jobid).get.debug.executionNotes.exists(_.startsWith("recovery-required:")) shouldBe true
            }
            fixture.engine.queryTimeline(jobid).get.events.map(_.kind) should contain(s"task.compensation.$kind")
            fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Failed)
          } finally fixture.engine.shutdown()
        }
      }

      "LS8 retain delayed Retry scheduling, submitted and enqueued history in original order" in {
        Given("a real delayed retry whose next attempt succeeds")
        val fixture = fresh(JobLifetimePolicy(4, 192, 2))
        val attempts = new AtomicInteger(0)
        val task = new Task(_ => if (attempts.incrementAndGet() == 1) TaskFailed(failure(Disposition.UserAction.RetryLater))
          else TaskSucceeded(OperationResponse.Scalar("ok")))
        try {
          When("the first failure appends its scheduling marker and the timer later enqueues its retry")
          val jobid = success(fixture.engine.submit(List(task), fixture.context, sync))
          val prefix = fixture.engine._get_record(jobid).get.timeline
          fixture.timer.advanceBy(Duration.ofMinutes(1)) shouldBe 1
          val enqueued = fixture.engine._get_record(jobid).get.timeline
          fixture.engine.drainAll() shouldBe 1
          Then("latest-record merges preserve every prior task and all delayed retry markers")
          enqueued.take(prefix.size) shouldBe prefix
          prefix.map(_.kind) should contain("job.retry.delayed.scheduled")
          enqueued.map(_.kind) should contain inOrder("job.retry.delayed.submitted", "job.retry.delayed.enqueued")
          fixture.engine._get_record(jobid).get.timeline.take(enqueued.size) shouldBe enqueued
          fixture.engine.queryTasks(jobid).get.tasks.size shouldBe 2
          fixture.engine._get_record(jobid).get.retry.attemptCount shouldBe 1
          fixture.engine._get_record(jobid).get.retry.nextRetryDueAt shouldBe None
          paid(fixture, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
          fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
        } finally fixture.engine.shutdown()
      }

      "LS9 conserve unpublished native refusal and published checkpoint, scheduling and clock failures" in {
        Given("a real durable bridge that explicitly refuses its original Admission request")
        val fixture = fresh(JobLifetimePolicy(4, 192, 2))
        val calls = new AtomicInteger(0)
        val task = new Task
        fixture.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(
          new DurableJobStore(EntityStore.standard()), _ => {
            calls.incrementAndGet(); Consequence.stateInvalid("original native admission refusal")
          }))
        try {
          When("the fitting Persistent candidate reaches and is refused by original native admission")
          val result = fixture.engine.submit(List(task), fixture.context, sync.copy(persistence = JobPersistencePolicy.Persistent))
          Then("original IDs, clocks and observers remain visible while no published ledger or runnable Job exists")
          result.isSuccess shouldBe false
          calls.get() shouldBe 1
          task.admissions.get() shouldBe 1
          task.runs.get() shouldBe 0
          fixture.engine.runtimeState.durableJobs.size() shouldBe 0
          fixture.engine.resourceSnapshot.retained shouldBe 0
          val refusedid = fixture.engine.durableLifecycleWriteFailureFacts.head.jobid
          fixture.engine.lifetimeSnapshot(refusedid) shouldBe None
          fixture.ids.calls.get() should be > 0
          fixture.clock.calls.get() should be > 0
        } finally fixture.engine.shutdown()
        Given("a bound signed native bridge whose TaskOutcome checkpoint source refuses after actual task execution")
        val checkpoint = fresh(JobLifetimePolicy(2, 96, 1))
        given ExecutionContext = checkpoint.context
        val store = new DurableJobStore(EntityStore.standard())
        val checkpointcalls = new AtomicInteger(0)
        val checkpointtask = new JobInputBoundsFixtures.CountingTask
        checkpoint.engine.bindDurableLifecycleWriteBridge(new DurableJobLifecycleWriteBridge(store, request => {
          val revision = checkpointcalls.incrementAndGet().toLong
          if (request.boundary == DurableJobLifecycleWriteBoundary.TaskOutcomeCheckpoint)
            Consequence.stateInvalid("original checkpoint refusal")
          else {
            val models = request match {
              case DurableJobLifecycleWriteRequest.TaskStartIntent(_, _, _, tasks) => tasks
              case _ => Vector.empty
            }
            val supplied = JobInputBoundsFixtures.evidence(revision)
            Consequence.success(supplied.copy(projection = supplied.projection.copy(
              tasks = if (models.nonEmpty) models.map(JobInputBoundsFixtures.taskDescriptor) else supplied.projection.tasks)))
          }
        }))
        try {
          When("the admitted native root dispatches and its original checkpoint refuses")
          val jobid = success(checkpoint.engine.submit(List(checkpointtask), checkpoint.context,
            sync.copy(persistence = JobPersistencePolicy.Persistent, runMode = JobRunMode.Async)))
          checkpoint.engine.drainAll() shouldBe 1
          Then("the original refusal fact and completed task survive with paid charges and acknowledged physical release")
          checkpointtask.runs.get() shouldBe 1
          checkpoint.engine.durableLifecycleWriteFailureFacts.map(_.failure) should contain(
            DurableJobLifecycleWriteFailure.TaskOutcomeCheckpointRefused)
          checkpointcalls.get() shouldBe 5
          paid(checkpoint, jobid) shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
          checkpoint.engine.queryTasks(jobid).get.tasks.head.status shouldBe JobTaskStatus.Succeeded
          checkpoint.engine.resourceSnapshot.pending shouldBe 0
          checkpoint.engine.resourceSnapshot.executing shouldBe 0
          val native = success(store.load(jobid.value, access)).get
          native.record.body.identity.revision shouldBe 4L
          val blocked = new Task
          val before = facts(checkpoint, blocked)
          checkpoint.engine.enqueueTaskInJob(jobid, blocked, checkpoint.context).isSuccess shouldBe false
          facts(checkpoint, blocked) shouldBe before
          success(store.load(jobid.value, access)) shouldBe Some(native)
        } finally checkpoint.engine.shutdown()
        Given("a pending cap already occupied by an unrelated root")
        val limited = fresh(JobLifetimePolicy(4, 192, 2), JobResourcePolicy(maxpendingwork = 1))
        val child = new Task
        try {
          When("the existing Job attempts an expansion that fits lifetime but not pending resources")
          val jobid = success(limited.engine.submit(List(new Task), limited.context, sync))
          success(limited.engine.submit(List(new Task), limited.context, sync.copy(runMode = JobRunMode.Async)))
          val before = facts(limited, child)
          val payment = paid(limited, jobid)
          val result = limited.engine.enqueueTaskInJob(jobid, child, limited.context)
          Then("pure resource refusal never commits its provisional lifetime delta")
          refusal(result)
          facts(limited, child) shouldBe before
          paid(limited, jobid) shouldBe payment
        } finally limited.engine.shutdown()
        Given("an injected physical timer that refuses an already admitted delayed start")
        val state = InMemoryJobEngine.State()
        success(state.bindLifetimePolicy(JobLifetimePolicy(2, 96, 1)))
        val clock = new JobInputBoundsFixtures.CountingClock
        val timer = new JobTimer {
          def schedule(dueAt: java.time.Instant)(body: => Unit): JobTimerRegistration =
            throw new IllegalStateException("timer refused")
        }
        val engine = new InMemoryJobEngine(state, schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
          timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
        try {
          When("the original native-free root publishes before timer registration fails")
          val jobid = success(engine.submit(List(new Task), ExecutionContext.create(clock),
            sync.copy(runMode = JobRunMode.Async, scheduledStartAt = Some(instant.plusSeconds(1)))))
          Then("published charges survive terminal scheduling failure and shutdown")
          engine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 1))
          engine.getStatus(jobid) shouldBe Some(JobStatus.Failed)
          engine.resourceSnapshot.pending shouldBe 0
          engine.shutdown()
          engine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 1))
        } finally engine.shutdown()
        Given("a published queued root and a one-shot engine clock failure during actual dispatch")
        val clockstate = InMemoryJobEngine.State()
        success(clockstate.bindLifetimePolicy(JobLifetimePolicy(2, 96, 1)))
        val dispatchclock = new JobInputBoundsFixtures.CountingClock
        val dispatchtimer = new JobInputBoundsFixtures.CountingTimer(dispatchclock)
        val armed = new AtomicBoolean(false)
        val source = new JobTimeSource {
          def now(): java.time.Instant =
            if (armed.getAndSet(false)) throw new IllegalStateException("clock refused")
            else dispatchclock.now()
        }
        val clockengine = new InMemoryJobEngine(clockstate,
          schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
          timeSource = source, timer = Some(dispatchtimer))(scala.concurrent.ExecutionContext.global)
        val clocktask = new Task
        try {
          val jobid = success(clockengine.submit(List(clocktask), ExecutionContext.create(dispatchclock),
            sync.copy(runMode = JobRunMode.Async)))
          val prefix = clockengine._get_record(jobid).get.timeline
          When("the already admitted queue reaches an engine clock that throws once")
          armed.set(true)
          clockengine.drainAll() shouldBe 1
          Then("original worker failure settlement and all prior history remain with spent credits after release")
          clockengine.getStatus(jobid) shouldBe Some(JobStatus.Failed)
          clockengine._get_record(jobid).get.timeline.take(prefix.size) shouldBe prefix
          clockengine.queryTimeline(jobid).get.events.map(_.kind) should contain("job.failed")
          clockengine.getResult(jobid).isDefined shouldBe true
          clocktask.runs.get() shouldBe 0
          clockengine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 1))
          clockengine.resourceSnapshot.pending shouldBe 0
          clockengine.resourceSnapshot.executing shouldBe 0
          dispatchtimer.pendingCount shouldBe 0
          clockengine.shutdown()
          clockengine.lifetimeSnapshot(jobid) shouldBe Some(JobLifetimeSnapshot(2, 96, 1, 1))
        } finally clockengine.shutdown()
      }

      "LS10 conserve at least 24 fixed-seed real expansion, control, retry and drain sequences" in {
        Given("finite lower budgets with queued roots, actual Suspend/Resume and generated expansions or retries")
        val generator = for { capacity <- Gen.choose(2, 5); actions <- Gen.listOfN(8, Gen.choose(0, 3)) }
          yield (capacity, actions)
        val property = Prop.forAll(generator) { case (capacity, actions) =>
          val fixture = fresh(JobLifetimePolicy(2L * capacity, 96L * capacity + 64, capacity.toLong + 2))
          val root = new Task(_ => TaskFailed(Consequence.stateInvalid[Unit]("original failure").conclusion))
          val task = new Task
          try {
            When("one queued root applies Suspend and Resume before normal failing dispatch")
            val jobid = success(fixture.engine.submit(List(root), fixture.context, sync.copy(runMode = JobRunMode.Async)))
            success(control(fixture, jobid, JobControlCommand.Suspend)).changed shouldBe true
            success(control(fixture, jobid, JobControlCommand.Resume)).changed shouldBe true
            fixture.engine.drainAll() shouldBe 1
            var work = 1L
            val controls = 2L
            var cancels = 0L
            actions.foreach { action =>
              val previous = paid(fixture, jobid)
              val history = fixture.engine._get_record(jobid).get.timeline
              When("a finite generated Sync child, queued child, canceled Retry or drained Retry actually attempts admission")
              action match {
                case 0 =>
                  val result = fixture.engine.runTaskInJobSync(jobid, task, fixture.context)
                  if (result.isSuccess) work += 1L
                case 1 =>
                  val result = fixture.engine.enqueueTaskInJob(jobid, task, fixture.context)
                  if (result.isSuccess) { work += 1L; fixture.engine.drainAll() }
                case 2 =>
                  val result = control(fixture, jobid, JobControlCommand.Retry)
                  if (result.isSuccess && success(result).changed) {
                    work += 1L
                    success(control(fixture, jobid, JobControlCommand.Cancel)).changed shouldBe true
                    cancels += 1L
                  }
                case _ =>
                  val result = control(fixture, jobid, JobControlCommand.Retry)
                  if (result.isSuccess && success(result).changed) { work += 1L; fixture.engine.drainAll() }
              }
              Then("prior history and exact paid work/control/Cancel costs agree after every actual action")
              val current = paid(fixture, jobid)
              current.chargedtasks shouldBe 2L * work
              current.historycredits shouldBe 96L * work + 32L * controls
              current.admissions shouldBe work + controls
              current.cancelcredits shouldBe work - cancels
              current.chargedtasks should be >= previous.chargedtasks
              fixture.engine._get_record(jobid).get.timeline.take(history.size) shouldBe history
              fixture.engine.resourceSnapshot.executing shouldBe 0
              fixture.engine.resourceSnapshot.pending shouldBe 0
            }
            When("a final actual expansion attempts the remaining boundary")
            val before = facts(fixture, task)
            val result = fixture.engine.enqueueTaskInJob(jobid, task, fixture.context)
            Then("exhausted or canceled refusal conserves all callbacks, queue, clock and ID effects")
            if (work == capacity || fixture.engine.getStatus(jobid).contains(JobStatus.Cancelled)) {
              result.isSuccess shouldBe false
              if (!fixture.engine.getStatus(jobid).contains(JobStatus.Cancelled)) refusal(result)
              facts(fixture, task) shouldBe before
            } else {
              result.isSuccess shouldBe true
              fixture.engine.drainAll() shouldBe 1
              paid(fixture, jobid).chargedtasks shouldBe 2L * (work + 1)
            }
            true
          } finally fixture.engine.shutdown()
        }
        When("ScalaCheck drives the fixed-seed actual action sequences")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
          .withInitialSeed(Seed(6970933521L)), property)
        Then("at least 24 behavioral sequences satisfy complete history and payment conservation")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }

      "LS11 charge supplied and appended notes exactly, reject Unit annotation atomically and attach a profile once" in {
        Given("initial notes at the exact root-history boundary and malformed or oversized note batches")
        for (notes <- Vector[Vector[String]](null, Vector(null), Vector("one", "two"))) {
          val fixture = fresh(JobLifetimePolicy(2, 97, 2))
          val task = new Task
          try {
            val before = facts(fixture, task)
            When("the root offers malformed or one-row-over initial notes")
            val result = fixture.engine.submit(List(task), fixture.context, sync.copy(executionNotes = notes))
            Then("note refusal precedes IDs, clocks, getters and all original resource effects")
            refusal(result)
            facts(fixture, task) shouldBe before
          } finally fixture.engine.shutdown()
        }
        val fixture = fresh(JobLifetimePolicy(2, 163, 3))
        val task = new Task
        try {
          When("one initial note, one two-row annotation and one empty annotation consume exact capacity")
          val jobid = success(fixture.engine.submit(List(task), fixture.context, sync.copy(executionNotes = Vector("initial"))))
          fixture.engine.annotateJob(jobid, Map("k" -> "v"), Vector("one", "two"))
          fixture.engine.annotateJob(jobid, Map.empty)
          val before = facts(fixture, task)
          val record = fixture.engine._get_record(jobid).get
          val payment = paid(fixture, jobid)
          val thrown = intercept[IllegalArgumentException] { fixture.engine.annotateJob(jobid, Map("k" -> "bad")) }
          Then("Unit refusal has its fixed message and preserves note order, maps, counters and all physical effects")
          thrown.getMessage shouldBe "JobEngine lifetime annotation refused"
          fixture.engine._get_record(jobid).get shouldBe record
          paid(fixture, jobid) shouldBe payment
          payment shouldBe JobLifetimeSnapshot(2, 163, 3, 1)
          facts(fixture, task) shouldBe before
          record.debug.executionNotes shouldBe Vector("initial", "one", "two")
          When("a missing Job receives malformed notes and a profile is attached twice to the existing root")
          fixture.engine.annotateJob(JobInputBoundsFixtures.id("missing"), Map.empty, null)
          fixture.engine.annotateJobProfile(jobid, JobDeclaredProfile())
          val first = fixture.engine._get_record(jobid).get
          fixture.engine.annotateJobProfile(jobid, JobDeclaredProfile())
          Then("missing annotation is a no-op and the one fixed profile row uses prepaid closure")
          fixture.engine._get_record(jobid).get shouldBe first
          first.debug.executionNotes shouldBe Vector("initial", "one", "two", "jcl declared profile attached")
          paid(fixture, jobid) shouldBe payment
        } finally fixture.engine.shutdown()
        Given("a running user task with observable business effects before its exhausted annotation")
        val running = fresh(JobLifetimePolicy(2, 96, 1))
        val effects = new AtomicInteger(0)
        val annotating = new Task(ctx => {
          effects.incrementAndGet()
          running.engine.annotateJob(ctx.jobContext.jobId.get, Map.empty, Vector("unpaid"))
          TaskSucceeded(OperationResponse.Scalar("unreachable"))
        })
        try {
          When("the actual queued user task reaches its Unit annotation refusal")
          val jobid = success(running.engine.submit(List(annotating), running.context, sync.copy(runMode = JobRunMode.Async)))
          running.engine.drainAll() shouldBe 1
          Then("prior user work remains visible and the original worker failure settlement is retained")
          effects.get() shouldBe 1
          annotating.runs.get() shouldBe 1
          running.engine.getStatus(jobid) shouldBe Some(JobStatus.Failed)
          running.engine._get_record(jobid).get.debug.executionNotes should not contain "unpaid"
          running.engine.queryTimeline(jobid).get.events.map(_.kind) should contain("job.failed")
          paid(running, jobid) shouldBe JobLifetimeSnapshot(2, 96, 1, 1)
        } finally running.engine.shutdown()
      }
    }
  }
}

private[job] object JobLifetimeFixtures extends Matchers {
  type Fixture = JobInputBoundsFixtures.Fixture

  def fresh(policy: JobLifetimePolicy = JobLifetimePolicy.default,
    resources: JobResourcePolicy = JobResourcePolicy.default): Fixture = {
    val state = InMemoryJobEngine.State()
    JobInputBoundsFixtures.success(state.bindLifetimePolicy(policy))
    JobInputBoundsFixtures.success(state.bindResourcePolicy(resources))
    JobInputBoundsFixtures.fresh(state)
  }

  def paid(fixture: Fixture, jobId: JobId): JobLifetimeSnapshot = fixture.engine.lifetimeSnapshot(jobId).get
  def events(fixture: Fixture): Vector[org.goldenport.cncf.event.EventRecord] =
    JobInputBoundsFixtures.success(fixture.events.query(EventStore.Query()))

  def facts(fixture: Fixture, task: Task): (JobResourceSnapshot, Int, Int, Int, Int, Int, Int, Int, Int, Int) =
    (fixture.engine.resourceSnapshot, fixture.ids.calls.get(), fixture.clock.calls.get(), events(fixture).size,
      fixture.timer.calls.get(), fixture.timer.pendingCount, task.getters.get(), task.runs.get(),
      fixture.engine._work_queue.size(), task.admissions.get())

  def control(fixture: Fixture, jobId: JobId, command: JobControlCommand): Consequence[JobControlResponse] =
    fixture.engine.control(jobId, JobControlRequest(command), new JobControlPolicy {
      def authorize(id: JobId, request: JobControlRequest)(using ctx: ExecutionContext): Consequence[Unit] =
        Consequence.unit
    })(using fixture.context)

  def failure(action: Disposition.UserAction): Conclusion =
    Conclusion.simple("original failure").copy(disposition = Disposition(action))

  def refusal[A](result: Consequence[A]): Unit = result match {
    case Consequence.Failure(actual) =>
      val expected = JobResourcePolicy.refused[A].conclusion
      actual.status shouldBe expected.status
      actual.observation.taxonomy shouldBe expected.observation.taxonomy
      actual.observation.cause.kind shouldBe expected.observation.cause.kind
      actual.observation.phenomenon shouldBe expected.observation.phenomenon
      actual.displayMessage shouldBe expected.displayMessage
    case Consequence.Success(_) => fail("expected lifetime capacity refusal")
  }

  final class Task(
    body: ExecutionContext => TaskOutcome = _ => TaskSucceeded(OperationResponse.Scalar("ok")),
    companion: Option[JobTask] = None
  ) extends JobTask {
    val getters = new AtomicInteger(0)
    val runs = new AtomicInteger(0)
    val admissions = new AtomicInteger(0)
    val compensations = new AtomicInteger(0)
    def actionId: ActionId = {
      getters.incrementAndGet()
      ActionId("cncf", "lifetime", Some(JobInputBoundsFixtures.instant), Some("task"))
    }
    override def requestSummary: Option[String] = { getters.incrementAndGet(); None }
    override def requestParameters: Map[String, String] = { getters.incrementAndGet(); Map.empty }
    override def taskKind: String = "operation"
    override def targetKind: Option[String] = Some("operation")
    override def relation: Option[String] = Some("root")
    override def componentName: Option[String] = Some("component-a")
    override def serviceName: Option[String] = Some("service-a")
    override def operationName: Option[String] = Some("run")
    override def compensationTask: Option[JobTask] = { compensations.incrementAndGet(); companion }
    override def compensationActionRef: Option[String] = companion.map(_ => "compensate")
    def run(ctx: ExecutionContext): TaskOutcome = { runs.incrementAndGet(); body(ctx) }
    override def observeAdmissionFailure(conclusion: Conclusion, ctx: ExecutionContext): Unit = {
      val _ = conclusion
      val _ = ctx
      admissions.incrementAndGet()
      ()
    }
  }
}
