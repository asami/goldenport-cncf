package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobLifetimeConcurrencySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobLifetimeFixtures.*
  import JobInputBoundsFixtures.{success, sync}

  "Shared State lifetime ownership" should {
    "LC1 admit exactly one last-slot expansion across two engines and keep another State independent" in {
      Given("one completed root, two actual engines sharing its four-slot State, and two racing child tasks")
      val first = fresh(JobLifetimePolicy(4, 192, 2))
      val second = JobInputBoundsFixtures.fresh(first.engine.runtimeState)
      val independent = fresh(JobLifetimePolicy(4, 192, 2))
      try {
        val root = new Task
        val jobid = success(first.engine.submit(List(root), first.context, sync))
        val left = new Task
        val right = new Task
        val beforeids = first.ids.calls.get() + second.ids.calls.get()
        val beforeclock = first.clock.calls.get() + second.clock.calls.get()
        val beforehistory = first.engine._get_record(jobid).get.timeline
        When("both owners race one queued same-Job expansion at the last paid slot")
        val results = _race(
          first.engine.enqueueTaskInJob(jobid, left, first.context),
          second.engine.enqueueTaskInJob(jobid, right, second.context)
        )
        Then("exactly one admission, Task ID and queue item exist while the other Task has no callbacks")
        Vector(results._1, results._2).count(_.isSuccess) shouldBe 1
        Vector(results._1, results._2).filterNot(_.isSuccess).foreach(refusal)
        first.ids.calls.get() + second.ids.calls.get() shouldBe beforeids + 1
        first.clock.calls.get() + second.clock.calls.get() should be > beforeclock
        first.engine._work_queue.size() + second.engine._work_queue.size() shouldBe 1
        paid(first, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
        paid(second, jobid) shouldBe paid(first, jobid)
        val loser = if (results._1.isSuccess) right else left
        loser.getters.get() shouldBe 0
        loser.runs.get() shouldBe 0
        loser.admissions.get() shouldBe 0
        first.engine._get_record(jobid).get.timeline.take(beforehistory.size) shouldBe beforehistory
        first.engine.drainAll() + second.engine.drainAll() shouldBe 1
        left.runs.get() + right.runs.get() shouldBe 1
        first.engine.resourceSnapshot.pending shouldBe 0
        first.engine.resourceSnapshot.executing shouldBe 0
        When("an independent State admits the same amount of real work")
        val independentid = success(independent.engine.submit(List(new Task), independent.context, sync))
        success(independent.engine.enqueueTaskInJob(independentid, new Task, independent.context))
        Then("its credits and physical queue remain independent")
        paid(independent, independentid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
        independent.engine.drainAll() shouldBe 1
        paid(first, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
      } finally {
        first.engine.shutdown()
        second.engine.shutdown()
        independent.engine.shutdown()
      }
    }

    "LC2 pay nested inline work while another thread cannot borrow its physical lease" in {
      Given("a single execution lease and a root that runs a nested Sync task before a competing external Sync action")
      val fixture = fresh(JobLifetimePolicy(6, 288, 3), JobResourcePolicy(maxexecutingwork = 1))
      val other = JobInputBoundsFixtures.fresh(fixture.engine.runtimeState)
      val entered = new CountDownLatch(1)
      val done = new CountDownLatch(1)
      val child = new Task(ctx => {
        Thread.holdsLock(fixture.engine.runtimeState) shouldBe false
        fixture.engine.resourceSnapshot.executing shouldBe 1
        TaskSucceeded(OperationResponse.Scalar("nested"))
      })
      val external = new Task
      val root = new Task(ctx => {
        val jobid = ctx.jobContext.jobId.get
        success(fixture.engine.runTaskInJobSync(jobid, child, ctx))
        entered.countDown()
        done.await(2, TimeUnit.SECONDS) shouldBe true
        TaskSucceeded(OperationResponse.Scalar("root"))
      })
      try {
        When("the root holds the lease and an external thread attempts same-Job Sync work")
        val results = _race(
          fixture.engine.submit(List(root), fixture.context, sync),
          {
            entered.await(2, TimeUnit.SECONDS) shouldBe true
            val jobid = fixture.engine.runtimeState.runtimeJobs.keySet().iterator().next()
            val result = other.engine.runTaskInJobSync(jobid, external, other.context)
            done.countDown()
            result
          }
        )
        Then("the nested task ran once under the same lease and the external refusal added no lifetime payment")
        val jobid = success(results._1)
        refusal(results._2)
        root.runs.get() shouldBe 1
        child.runs.get() shouldBe 1
        external.getters.get() shouldBe 0
        external.runs.get() shouldBe 0
        paid(fixture, jobid) shouldBe JobLifetimeSnapshot(4, 192, 2, 2)
        fixture.engine.resourceSnapshot.executing shouldBe 0
        fixture.engine.queryTasks(jobid).get.tasks.size shouldBe 2
        When("one later Sync child uses the final lifetime admission after the physical lease is released")
        success(other.engine.runTaskInJobSync(jobid, external, other.context))
        Then("the shared cap now refuses the next expansion with every callback conserved")
        paid(fixture, jobid) shouldBe JobLifetimeSnapshot(6, 288, 3, 3)
        val refused = new Task
        val before = facts(other, refused)
        refusal(other.engine.runTaskInJobSync(jobid, refused, other.context))
        facts(other, refused) shouldBe before
      } finally {
        done.countDown()
        fixture.engine.shutdown()
        other.engine.shutdown()
      }
    }

    "LC3 claim the original companion once across real base and child failures" in {
      Vector("succeeded", "missing", "failed").foreach { mode =>
        Given(s"a committed original task with $mode compensation and simultaneous base/child failures")
        val fixture = fresh(JobLifetimePolicy(6, 256, 2))
        val other = JobInputBoundsFixtures.fresh(fixture.engine.runtimeState)
        val entered = new CountDownLatch(1)
        val failures = new CountDownLatch(2)
        val originalfailure = Consequence.stateInvalid[Unit]("original failure").conclusion
        val companion = new Task(_ => if (mode == "failed") TaskFailed(originalfailure)
          else TaskSucceeded(OperationResponse.Scalar("compensated")))
        val original = new Task(companion = if (mode == "missing") None else Some(companion))
        def _failure_(announce: Boolean): Task = new Task(_ => {
          if (announce) entered.countDown()
          failures.countDown()
          failures.await(2, TimeUnit.SECONDS) shouldBe true
          TaskFailed(originalfailure)
        })
        val basefailure = _failure_(true)
        val childfailure = _failure_(false)
        try {
          val jobid = success(fixture.engine.submit(List(original, basefailure), fixture.context,
            sync.copy(runMode = JobRunMode.Async)))
          When("the normal root failure and same-Job child failure select the same committed original")
          val results = _race(
            fixture.engine.drainOne(),
            {
              entered.await(2, TimeUnit.SECONDS) shouldBe true
              other.engine.runTaskInJobSync(jobid, childfailure, other.context)
            }
          )
          Then("the State claim precedes the getter and permits only one compensation history group")
          results._1 shouldBe true
          results._2 match {
            case Consequence.Failure(conclusion) =>
              conclusion.status shouldBe originalfailure.status
              conclusion.observation.taxonomy shouldBe originalfailure.observation.taxonomy
              conclusion.observation.cause.kind shouldBe originalfailure.observation.cause.kind
              conclusion.observation.phenomenon shouldBe originalfailure.observation.phenomenon
              conclusion.displayMessage shouldBe originalfailure.displayMessage
            case Consequence.Success(_) => fail("the actual failed child must retain its failure")
          }
          original.compensations.get() shouldBe 1
          companion.runs.get() shouldBe (if (mode == "missing") 0 else 1)
          val record = fixture.engine._get_record(jobid).get
          val originalmodel = record.taskReadModels.find(_.relation.contains("root")).get
          originalmodel.compensationStatus shouldBe Some(mode)
          record.timeline.count(_.kind == "job.compensation.started") shouldBe 1
          record.timeline.count(_.kind == "job.compensation.finished") shouldBe 1
          record.taskReadModels.count(_.compensatesTaskId.contains(originalmodel.taskId)) shouldBe
            (if (mode == "missing") 0 else 1)
          record.retry.recoveryRequired shouldBe true
          paid(fixture, jobid) shouldBe JobLifetimeSnapshot(6, 256, 2, 2)
          val before = (record.timeline, record.debug.executionNotes, fixture.ids.calls.get(),
            other.ids.calls.get(), companion.getters.get(), original.compensations.get(), companion.runs.get())
          When("both selectors revisit the already claimed original")
          val repeat = _race(
            fixture.engine._run_compensations(jobid, None, Vector(originalmodel.taskId -> original), fixture.context),
            other.engine._run_compensations(jobid, None, Vector(originalmodel.taskId -> original), other.context)
          )
          Then("losing selections produce neither getters nor IDs, runs or group markers")
          repeat._1 shouldBe empty
          repeat._2 shouldBe empty
          val after = fixture.engine._get_record(jobid).get
          (after.timeline, after.debug.executionNotes, fixture.ids.calls.get(), other.ids.calls.get(),
            companion.getters.get(), original.compensations.get(), companion.runs.get()) shouldBe before
        } finally {
          failures.countDown()
          failures.countDown()
          fixture.engine.shutdown()
          other.engine.shutdown()
        }
      }
    }

    "LC4 retain published child credits through bounded closure and drain races" in {
      Vector("cancel", "quiesce", "shutdown", "drain").foreach { mode =>
        Given(s"a real pending root and a same-Job child racing $mode")
        val fixture = fresh(JobLifetimePolicy(4, 192, 2))
        val other = JobInputBoundsFixtures.fresh(fixture.engine.runtimeState)
        val root = new Task
        val child = new Task
        try {
          val jobid = success(fixture.engine.submit(List(root), fixture.context,
            sync.copy(runMode = JobRunMode.Async)))
          val prefix = fixture.engine._get_record(jobid).get.timeline
          When("the queue admission and closure or dispatch actor start together")
          val results = _race(
            fixture.engine.enqueueTaskInJob(jobid, child, fixture.context),
            mode match {
              case "cancel" => control(other, jobid, JobControlCommand.Cancel); ()
              case "quiesce" => fixture.engine.quiesce()
              case "shutdown" => fixture.engine.shutdown()
              case "drain" => fixture.engine.drainAll(); ()
            }
          )
          Then("all paid admissions remain and physical work is removed or dispatched at most once")
          val admissions = if (results._1.isSuccess) 2L else 1L
          val snapshot = paid(fixture, jobid)
          snapshot.chargedtasks shouldBe 2 * admissions
          snapshot.historycredits shouldBe 96 * admissions
          snapshot.admissions shouldBe admissions
          fixture.engine._get_record(jobid).get.timeline.take(prefix.size) shouldBe prefix
          fixture.engine.shutdown()
          other.engine.shutdown()
          fixture.timer.pendingCount shouldBe 0
          fixture.engine.resourceSnapshot.pending shouldBe 0
          fixture.engine.resourceSnapshot.executing shouldBe 0
          root.runs.get() should be <= 1
          child.runs.get() should be <= 1
          paid(fixture, jobid) shouldBe snapshot
          fixture.engine.drainAll() shouldBe 0
          paid(other, jobid) shouldBe snapshot
        } finally {
          fixture.engine.shutdown()
          other.engine.shutdown()
        }
      }
    }

    "LC5 retain exact paid counters and compensation claims on second attachment and control" in {
      Given("a completed original, one claimed compensation, and no remaining expansion capacity")
      val first = fresh(JobLifetimePolicy(4, 224, 3))
      val companion = new Task
      val original = new Task(companion = Some(companion))
      try {
        val failed = new Task(_ => TaskFailed(Consequence.stateInvalid[Unit]("original failure").conclusion))
        When("the original succeeds and the following task fails, triggering its compensation")
        val jobid = success(first.engine.submit(List(original, failed), first.context, sync))
        val originalids = first.engine.queryTasks(jobid).get.tasks.flatMap(_.compensatesTaskId)
        Then("one compensation identifies the actual original and reselecting it does no work")
        originalids.size shouldBe 1
        val originalid = originalids.head
        first.engine._run_compensations(jobid, None, Vector(originalid -> original), first.context) shouldBe empty
        val before = paid(first, jobid)
        val second = JobInputBoundsFixtures.fresh(first.engine.runtimeState)
        try {
          When("the new owner attaches, reads, reselects compensation and attempts Retry")
          second.engine.query(jobid).get.jobId shouldBe jobid
          second.engine.queryTasks(jobid).get.tasks.size shouldBe 3
          second.engine._run_compensations(jobid, None, Vector(originalid -> original), second.context) shouldBe empty
          refusal(control(second, jobid, JobControlCommand.Retry))
          Then("the original counters and claim remain exact instead of prospective repayment or reset")
          paid(second, jobid) shouldBe before
          original.compensations.get() shouldBe 1
          companion.runs.get() shouldBe 1
          first.engine.resourceSnapshot shouldBe second.engine.resourceSnapshot
          second.engine.resourceSnapshot.pending shouldBe 0
          val task = new Task
          val effects = facts(second, task)
          refusal(second.engine.enqueueTaskInJob(jobid, task, second.context))
          facts(second, task) shouldBe effects
          paid(first, jobid) shouldBe before
        } finally second.engine.shutdown()
      } finally first.engine.shutdown()
    }

    "LC6 conserve real shared-State facts in at least 24 fixed-seed bounded interleavings" in {
      Given("finite lower policies and two real owners with bounded races of queued or Sync expansions")
      val property = Prop.forAll(Gen.choose(2, 5), Gen.listOfN(5, Gen.oneOf(true, false))) { (capacity, modes) =>
        val first = fresh(JobLifetimePolicy(2L * capacity, 96L * capacity, capacity.toLong))
        val second = JobInputBoundsFixtures.fresh(first.engine.runtimeState)
        try {
          val jobid = success(first.engine.submit(List(new Task), first.context, sync))
          var admitted = 1L
          modes.foreach { queued =>
            val left = new Task
            val right = new Task
            val prefix = first.engine._get_record(jobid).get.timeline
            val beforeids = first.ids.calls.get() + second.ids.calls.get()
            When("two owners race finite real work, then drain their queues")
            val results = if (queued) _race(
              first.engine.enqueueTaskInJob(jobid, left, first.context),
              second.engine.enqueueTaskInJob(jobid, right, second.context)
            ) else _race(
              first.engine.runTaskInJobSync(jobid, left, first.context),
              second.engine.runTaskInJobSync(jobid, right, second.context)
            )
            val accepted = Vector(results._1, results._2).count(_.isSuccess)
            admitted += accepted
            Then("IDs, paid credits, task outcomes and preserved history equal only the actual accepted work")
            Vector(results._1, results._2).filterNot(_.isSuccess).foreach(refusal)
            first.ids.calls.get() + second.ids.calls.get() shouldBe beforeids + accepted
            first.engine._get_record(jobid).get.timeline.take(prefix.size) shouldBe prefix
            first.engine.drainAll() + second.engine.drainAll() shouldBe (if (queued) accepted else 0)
            left.runs.get() + right.runs.get() shouldBe accepted
            if (!results._1.isSuccess) left.getters.get() shouldBe 0
            if (!results._2.isSuccess) right.getters.get() shouldBe 0
            paid(first, jobid) shouldBe JobLifetimeSnapshot(2 * admitted, 96 * admitted, admitted, admitted)
            paid(second, jobid) shouldBe paid(first, jobid)
            first.engine.resourceSnapshot.pending shouldBe 0
            first.engine.resourceSnapshot.executing shouldBe 0
          }
          admitted shouldBe capacity.toLong
          val refused = new Task
          val before = facts(second, refused)
          refusal(second.engine.enqueueTaskInJob(jobid, refused, second.context))
          facts(second, refused) shouldBe before
          first.engine.queryTasks(jobid).get.tasks.size shouldBe admitted.toInt
          true
        } finally {
          first.engine.shutdown()
          second.engine.shutdown()
        }
      }
      When("ScalaCheck executes 24 deterministic successful behavioral checks")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(24)
        .withInitialSeed(Seed(6970933531L)), property)
      Then("every finite shared-State interleaving satisfies the real admission and conservation assertions")
      result.passed shouldBe true
      result.succeeded should be >= 24
    }
  }

  private def _race[A, B](left: => A, right: => B): (A, B) = {
    val ready = new CountDownLatch(2)
    val start = new CountDownLatch(1)
    val leftresult = new AtomicReference[Either[Throwable, A]]()
    val rightresult = new AtomicReference[Either[Throwable, B]]()
    val leftthread = new Thread(() => {
      ready.countDown()
      try {
        start.await(2, TimeUnit.SECONDS) shouldBe true
        leftresult.set(Right(left))
      } catch { case error: Throwable => leftresult.set(Left(error)) }
    }, "lifetime-left")
    val rightthread = new Thread(() => {
      ready.countDown()
      try {
        start.await(2, TimeUnit.SECONDS) shouldBe true
        rightresult.set(Right(right))
      } catch { case error: Throwable => rightresult.set(Left(error)) }
    }, "lifetime-right")
    leftthread.start()
    rightthread.start()
    try {
      ready.await(2, TimeUnit.SECONDS) shouldBe true
      start.countDown()
      leftthread.join(5000)
      rightthread.join(5000)
      leftthread.isAlive shouldBe false
      rightthread.isAlive shouldBe false
      val leftvalue = leftresult.get() match {
        case Right(value) => value
        case Left(error) => throw error
        case null => fail("left actor did not return")
      }
      val rightvalue = rightresult.get() match {
        case Right(value) => value
        case Left(error) => throw error
        case null => fail("right actor did not return")
      }
      (leftvalue, rightvalue)
    } finally {
      start.countDown()
      if (leftthread.isAlive) leftthread.interrupt()
      if (rightthread.isAlive) rightthread.interrupt()
      leftthread.join(5000)
      rightthread.join(5000)
      leftthread.isAlive shouldBe false
      rightthread.isAlive shouldBe false
    }
  }
}
