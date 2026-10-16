package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.cncf.event.EventStore
import org.goldenport.observation.Taxonomy
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
final class JobWaitResourceBoundsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Job wait resources" should {
    "pre-effect request admission" which {
      "WT1 refuse invalid await and every control shape before clock, timer or mutation" in {
        Given("an actual failed job with events, a retained cancellation scope and a counted control policy")
        val fixture = _fixture()
        val id = _failed(fixture)
        val policy = new ControlProbe
        val badtimes = Vector(Long.MinValue, -1L, 86400001L, Long.MaxValue)
        try {
          for (timeout <- badtimes) {
            val before = _facts(fixture, id)
            val clocks = fixture.clock.calls.get()
            When("an invalid await timeout is supplied for existing and missing identities")
            val existing = fixture.engine.awaitResult(id, timeout)
            val missing = fixture.engine.awaitResult(_missing, timeout)
            Then("both receive the fixed argument refusal before clock or existence effects")
            _message(existing) should include("JobEngine wait request refused")
            existing match {
              case Consequence.Failure(conclusion) =>
                conclusion.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Invalid
              case Consequence.Success(_) => fail("expected invalid wait refusal")
            }
            _message(missing) shouldBe _message(existing)
            fixture.clock.calls.get() shouldBe clocks
            _facts(fixture, id) shouldBe before
          }
          val numeric = for {
            command <- JobControlCommand.values.toVector
            mode <- JobCommandMode.values.toVector
            timeout <- badtimes
          } yield JobControlRequest(command, JobControlOption(mode, timeout))
          val shapes = Vector(null, JobControlRequest(null), JobControlRequest(JobControlCommand.Retry, null),
            JobControlRequest(JobControlCommand.Retry, JobControlOption(mode = null)))
          for (request <- numeric ++ shapes) {
            val before = _facts(fixture, id)
            val clocks = fixture.clock.calls.get()
            val policies = policy.calls.get()
            When("a malformed control request is admitted by policy")
            val result = fixture.engine.control(id, request, policy)(using fixture.context)
            val absent = fixture.engine.control(_missing, request, policy)(using fixture.context)
            Then("policy precedes the fixed redacted refusal and all engine effects remain unchanged")
            _message(result) should include("JobEngine wait request refused")
            _message(absent) shouldBe _message(result)
            _message(result) should not include id.value
            policy.calls.get() shouldBe policies + 2
            fixture.clock.calls.get() shouldBe clocks
            _facts(fixture, id) shouldBe before
          }
        } finally _dispose(fixture)
      }

      "WT2 admit zero and 24-hour boundaries using the real manual timer" in {
        Given("a queued manual job and a failed job eligible for Sync Retry")
        val fixture = _fixture()
        val pending = _pending(fixture)
        val failed = _failed(fixture)
        var call = Option.empty[WaitCall[JobResult]]
        var control = Option.empty[WaitCall[JobControlResponse]]
        try {
          When("zero timeout is requested without waiting")
          val zero = fixture.engine.awaitResult(pending, 0L)
          val zerocontrol = _control(fixture, failed, JobControlCommand.Retry, JobCommandMode.Sync, 0L)
          Then("legacy immediate timeout categories survive with no registration or charged slot")
          zero shouldBe a[Consequence.Failure[_]]
          _message(zero) should include("job timeout")
          _message(zerocontrol) should include("sync timeout")
          fixture.timer.calls.get() shouldBe 0
          fixture.engine._resource_budget.waitCount shouldBe 0
          When("an inclusive 24-hour await is completed by draining actual work")
          val waiting = _start(() => fixture.engine.awaitResult(pending, 86400000L))
          call = Some(waiting)
          fixture.timer.awaitCalls(1) shouldBe true
          fixture.engine.drainAll() should be >= 1
          _join(waiting)
          Then("the actual result is returned only after owned close and thread termination")
          waiting.result.get() shouldBe a[Consequence.Success[_]]
          waiting.thread.isAlive shouldBe false
          fixture.timer.acknowledged.get() shouldBe 1
          fixture.engine._resource_budget.waitCount shouldBe 0
          val nextfailed = _failed(fixture)
          When("an inclusive 24-hour Sync Retry is allowed to time out under manual time")
          val retry = _start(() => _control(fixture, nextfailed, JobControlCommand.Retry, JobCommandMode.Sync, 86400000L))
          control = Some(retry)
          fixture.timer.awaitCalls(2) shouldBe true
          fixture.timer.advanceBy(Duration.ofHours(24))
          _join(retry)
          Then("the original Sync timeout leaves admitted work authoritative and releases its wait")
          _message(retry.result.get()) should include("sync timeout")
          fixture.engine.getStatus(nextfailed) shouldBe Some(JobStatus.Submitted)
          fixture.timer.pendingCount shouldBe 0
          fixture.engine._resource_budget.waitCount shouldBe 0
        } finally {
          call.foreach(_stop(_))
          control.foreach(_stop(_))
          _dispose(fixture)
        }
      }

      "WT3 refuse throwing or overflowing initial deadlines before Sync Retry effects" in {
        Given("a normally initialized failed job before the injected clock becomes hostile")
        val fixture = _fixture()
        val id = _failed(fixture)
        try {
          for (instant <- Vector(None, Some(Instant.MAX.minusMillis(1)))) {
            val before = _facts(fixture, id)
            fixture.clock.set(instant, throwing = instant.isEmpty)
            When("Sync Retry and await attempt an initial deadline that throws or overflows")
            val control = _control(fixture, id, JobControlCommand.Retry, JobCommandMode.Sync, 2L)
            val await = fixture.engine.awaitResult(id, 2L)
            Then("a fixed invalid-wait refusal precedes queue, record, timeline, event and scope changes")
            _message(control) should include("JobEngine wait request refused")
            _message(await) should include("JobEngine wait request refused")
            _message(control) should not include "PRIVATE-CLOCK"
            _facts(fixture, id) shouldBe before
            fixture.clock.set(None)
          }
        } finally {
          fixture.clock.set(None)
          _dispose(fixture)
        }
      }

      "WT4 preserve failed and native checkpoint facts on invalid Retry and retain Async poll compatibility" in {
        Given("an actual failed job and its signed native failed checkpoint in a real store")
        val fixture = _fixture()
        given ExecutionContext = fixture.context
        val id = _failed(fixture)
        val store = new DurableJobStore(EntityStore.standard())
        val admitted = _success(store.create(_native_failed(id), _access))
        val checkpoint = _success(DurableJobRecord.createV2(admitted.record.body.copy(
          identity = admitted.record.body.identity.copy(revision = 2L, updatedAt = _instant.plusSeconds(1)))))
        val native = _success(store.checkpoint(admitted, checkpoint, _access))
        val before = _facts(fixture, id)
        try {
          When("Sync Retry carries an invalid duration and an extreme unused poll field")
          val invalid = _control(fixture, id, JobControlCommand.Retry, JobCommandMode.Sync, -1L, Long.MaxValue)
          val retained = _success(store.load(id.value, _access))
          Then("failed Job, queue, events, scope and exact native checkpoint remain unchanged")
          invalid shouldBe a[Consequence.Failure[_]]
          _facts(fixture, id) shouldBe before
          retained shouldBe Some(native)
          When("valid Async Retry and replay use negative and extreme ignored poll values")
          val retry = _success(_control(fixture, id, JobControlCommand.Retry, JobCommandMode.Async, 3000L, Long.MinValue))
          val replay = _success(_control(fixture, id, JobControlCommand.Retry, JobCommandMode.Async, 3000L, Long.MaxValue))
          Then("the original Submitted admission and changed=false replay do not introduce polling")
          retry.status shouldBe JobStatus.Submitted
          retry.async shouldBe true
          retry.changed shouldBe true
          replay.changed shouldBe false
          fixture.engine._work_queue.size() shouldBe 1
          fixture.engine._resource_budget.waitCount shouldBe 0
          fixture.timer.calls.get() shouldBe 0
          _success(store.load(id.value, _access)) shouldBe Some(native)
        } finally _dispose(fixture)
      }

      "WT5 preserve policy denial, synchronous replay and default Async guards" in {
        Given("an actual Submitted job, a user denied by the original policy, and an authorized control context")
        val fixture = _fixture()
        val id = _pending(fixture)
        val user = ExecutionContext.test(SecurityContext.Privilege.User)
        val before = _facts(fixture, id)
        val clocks = fixture.clock.calls.get()
        try {
          When("the denied caller supplies existing, missing, malformed and invalid-duration inputs")
          val denied = for {
            jobid <- Vector(id, _missing)
            request <- Vector(null, JobControlRequest(JobControlCommand.Retry, JobControlOption(timeoutMillis = -1)),
              JobControlRequest(JobControlCommand.Cancel))
          } yield fixture.engine.control(jobid, request)(using user)
          Then("the identical original policy failure precedes every engine admission and existence observation")
          denied.foreach(result => result shouldBe a[Consequence.Failure[_]])
          denied.map(_message(_)).distinct.size shouldBe 1
          _message(denied.head) should include("required capability")
          fixture.clock.calls.get() shouldBe clocks
          _facts(fixture, id) shouldBe before
          When("an authorized Sync Retry replay targets the already Submitted job")
          val replay = _success(_control(fixture, id, JobControlCommand.Retry, JobCommandMode.Sync))
          Then("the replay is immediate and unchanged with no registration")
          replay.changed shouldBe false
          replay.async shouldBe false
          _facts(fixture, id) shouldBe before
          When("the default authorized control applies cancellation and an invalid Resume guard")
          val manager = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
          val cancelled = _success(fixture.engine.control(id, JobControlRequest(JobControlCommand.Cancel))(using manager))
          val invalid = fixture.engine.control(id, JobControlRequest(JobControlCommand.Resume))(using manager)
          Then("the default remains Async and original invalid state guards remain intact")
          cancelled.async shouldBe true
          cancelled.response shouldBe None
          cancelled.status shouldBe JobStatus.Cancelled
          invalid shouldBe a[Consequence.Failure[_]]
          fixture.timer.calls.get() shouldBe 0
          fixture.engine._resource_budget.waitCount shouldBe 0
        } finally _dispose(fixture)
      }
    }

    "shared physical timer capacity" which {
      "WT6 bound 64 real waits across owners and admit exactly one last-slot racer" in {
        Given("two actual engines sharing State, clock and physical timer with independent failed Retry targets")
        val fixture = _fixture()
        val other = _fixture(fixture.engine.runtimeState, Some(fixture.clock), Some(fixture.timer))
        val pending = _pending(fixture)
        val retryid = _failed(other)
        val refusedid = _failed(other)
        val calls = ArrayBuffer.empty[WaitCall[?]]
        try {
          When("63 awaits and one actual Sync Retry physically register across both engines")
          for (index <- 0 until 63) {
            val engine = if (index % 2 == 0) fixture.engine else other.engine
            calls += _start(() => engine.awaitResult(pending, 86400000L))
          }
          calls += _start(() => _control(other, retryid, JobControlCommand.Retry, JobCommandMode.Sync, 86400000L))
          fixture.timer.awaitCalls(64) shouldBe true
          val before = _facts(other, refusedid)
          val refusedawait = fixture.engine.awaitResult(pending, 86400000L)
          val refusedretry = _control(other, refusedid, JobControlCommand.Retry, JobCommandMode.Sync, 86400000L)
          Then("the 65th requests refuse before Retry effects and both owners observe exactly 64 occupied slots")
          refusedawait shouldBe a[Consequence.Failure[_]]
          refusedretry shouldBe a[Consequence.Failure[_]]
          _facts(other, refusedid) shouldBe before
          fixture.timer.pendingCount shouldBe 64
          fixture.engine._resource_budget.waitCount shouldBe 64
          other.engine._resource_budget.waitCount shouldBe 64
          When("manual time terminates the first physical cohort")
          fixture.timer.advanceBy(Duration.ofHours(24))
          calls.foreach(_join(_))
          Then("actual joins and successful close acknowledgements release that cohort")
          calls.foreach(call => call.thread.isAlive shouldBe false)
          fixture.timer.pendingCount shouldBe 0
          fixture.engine._resource_budget.waitCount shouldBe 0
          calls.clear()
          val raceone = _failed(fixture)
          val racetwo = _failed(other)
          val onebefore = fixture.engine._get_record(raceone)
          val twobefore = other.engine._get_record(racetwo)
          val onescope = fixture.engine._cancellation_scopes.get(raceone)
          val twoscope = other.engine._cancellation_scopes.get(racetwo)
          for (_ <- 0 until 63) calls += _start(() => fixture.engine.awaitResult(pending, 86400000L))
          fixture.timer.awaitCalls(127) shouldBe true
          val ready = new CountDownLatch(2)
          val release = new CountDownLatch(1)
          val finished = new CountDownLatch(1)
          val one = _start(() => {
            ready.countDown()
            if (!release.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("race gate timed out")
            val result = _control(fixture, raceone, JobControlCommand.Retry, JobCommandMode.Sync, 86400000L)
            result
          }, Some(finished))
          val two = _start(() => {
            ready.countDown()
            if (!release.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("race gate timed out")
            val result = _control(other, racetwo, JobControlCommand.Retry, JobCommandMode.Sync, 86400000L)
            result
          }, Some(finished))
          calls ++= Vector(one, two)
          When("two owners race for the last free slot behind a bounded barrier")
          try {
            ready.await(5L, TimeUnit.SECONDS) shouldBe true
            release.countDown()
            fixture.timer.awaitCalls(128) shouldBe true
            finished.await(5L, TimeUnit.SECONDS) shouldBe true
            Then("exactly one registers and the other remains Failed with its original scope and record")
            fixture.timer.calls.get() shouldBe 128
            fixture.timer.pendingCount shouldBe 64
            fixture.engine._resource_budget.waitCount shouldBe 64
            Vector(fixture.engine.getStatus(raceone), other.engine.getStatus(racetwo)).sortBy(_.toString) shouldBe
              Vector(Some(JobStatus.Failed), Some(JobStatus.Submitted)).sortBy(_.toString)
            val refused = Vector(one, two).filter(_.result.get() != null)
            refused.size shouldBe 1
            refused.head.result.get() shouldBe a[Consequence.Failure[_]]
            if (fixture.engine.getStatus(raceone).contains(JobStatus.Failed)) {
              fixture.engine._get_record(raceone) shouldBe onebefore
              fixture.engine._cancellation_scopes.get(raceone) should be theSameInstanceAs onescope
            } else {
              other.engine._get_record(racetwo) shouldBe twobefore
              other.engine._cancellation_scopes.get(racetwo) should be theSameInstanceAs twoscope
            }
          } finally release.countDown()
          When("the second cohort reaches its manual deadline")
          fixture.timer.advanceBy(Duration.ofHours(24))
          calls.foreach(_join(_))
          Then("all race and wait threads physically terminate and acknowledged slots return to zero")
          calls.foreach(call => call.thread.isAlive shouldBe false)
          fixture.engine._resource_budget.waitCount shouldBe 0
          fixture.timer.pendingCount shouldBe 0
        } finally {
          calls.foreach(_stop(_))
          _dispose(other)
          _dispose(fixture)
        }
      }

      "WT7 retain ghost charge after scheduling acknowledgement loss or null registration" in {
        Given("fault timers that physically register before throwing or returning null")
        for (fault <- Vector(TimerFault.ThrowAfter, TimerFault.NullAfter)) {
          val fixture = _fixture()
          val id = _pending(fixture)
          fixture.timer.setFault(fault)
          var other = Option.empty[Fixture]
          try {
            When("64 actual awaits lose their schedule acknowledgement")
            val results = Vector.fill(64)(fixture.engine.awaitResult(id, 86400000L))
            Then("fixed unavailable outcomes retain every potentially live registration as an occupied ghost")
            results.foreach(result => _message(result) should include("JobEngine resource capacity unavailable"))
            results.foreach(result => _message(result) should not include "PRIVATE-TIMER")
            fixture.timer.calls.get() shouldBe 64
            fixture.timer.pendingCount shouldBe 64
            fixture.timer.closeAttempts.get() shouldBe 0
            fixture.engine._resource_budget.waitCount shouldBe 64
            When("the owner closes and a fresh engine attaches to the same State")
            fixture.engine.shutdown()
            val fresh = _fixture(fixture.engine.runtimeState, Some(fixture.clock), Some(fixture.timer))
            other = Some(fresh)
            val refused = fresh.engine.awaitResult(id, 86400000L)
            Then("no owner reset launders capacity and no 65th physical timer is created")
            refused shouldBe a[Consequence.Failure[_]]
            fresh.engine._resource_budget.waitCount shouldBe 64
            fixture.timer.calls.get() shouldBe 64
            fixture.timer.shutdowns.get() shouldBe 0
          } finally {
            other.foreach(_dispose(_))
            _dispose(fixture)
          }
        }
      }

      "WT8 let close failure override completion and preserve admitted Retry transitions on timer failure" in {
        Given("a real wait whose physical close throws, for both completion and timeout")
        for (complete <- Vector(true, false)) {
          val fixture = _fixture()
          val id = _pending(fixture)
          fixture.timer.setFault(TimerFault.CloseBefore)
          val call = _start(() => fixture.engine.awaitResult(id, 86400000L))
          var other = Option.empty[Fixture]
          try {
            fixture.timer.awaitCalls(1) shouldBe true
            When("the admitted wait completes or its manual deadline expires")
            if (complete) fixture.engine.drainAll() else fixture.timer.advanceBy(Duration.ofHours(24))
            _join(call)
            Then("the unavailable close outcome overrides completion or timeout and retains one charged registration")
            _message(call.result.get()) should include("JobEngine resource capacity unavailable")
            fixture.timer.closeAttempts.get() shouldBe 1
            fixture.timer.acknowledged.get() shouldBe 0
            fixture.timer.pendingCount shouldBe 1
            fixture.engine._resource_budget.waitCount shouldBe 1
            val failed = _failed(fixture)
            val before = _facts(fixture, failed)
            fixture.timer.setFault(TimerFault.ThrowAfter)
            When("an admitted Sync Retry loses its timer schedule acknowledgement after its authoritative transition")
            val retry = _control(fixture, failed, JobControlCommand.Retry, JobCommandMode.Sync, 86400000L)
            Then("the error does not roll back Submitted, queued work, events, timeline or replacement scope")
            _message(retry) should include("JobEngine resource capacity unavailable")
            fixture.engine.getStatus(failed) shouldBe Some(JobStatus.Submitted)
            fixture.engine._work_queue.size() shouldBe before.queue + 1
            _events(fixture.events) should be > before.events
            fixture.engine.query(failed).get.timeline.events.map(_.kind) should contain("job.retry.submitted")
            fixture.engine._cancellation_scopes.get(failed) should not be theSameInstanceAs(before.scope)
            fixture.engine._resource_budget.waitCount shouldBe 2
            When("the owner closes and a replacement observes the shared budget")
            fixture.engine.shutdown()
            val fresh = _fixture(fixture.engine.runtimeState, Some(fixture.clock), Some(fixture.timer))
            other = Some(fresh)
            Then("both acknowledgement ambiguities remain charged through owner replacement")
            fresh.engine._resource_budget.waitCount shouldBe 2
            fixture.timer.shutdowns.get() shouldBe 0
          } finally {
            _stop(call)
            other.foreach(_dispose(_))
            _dispose(fixture)
          }
        }
      }
    }

    "acknowledged lifetime and clock failures" which {
      "WT9 release once after actual close and preserve the interrupted thread flag" in {
        Given("a queued real job and a close acknowledgement gate owned by its timer registration")
        val fixture = _fixture()
        val id = _pending(fixture)
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)
        fixture.timer.setCloseGate(Some(entered -> release))
        val completed = _start(() => fixture.engine.awaitResult(id, 86400000L))
        val calls = ArrayBuffer[WaitCall[?]](completed)
        try {
          fixture.timer.awaitCalls(1) shouldBe true
          When("real queue drain completes the job while physical close has not acknowledged")
          fixture.engine.drainAll() shouldBe 1
          entered.await(5L, TimeUnit.SECONDS) shouldBe true
          Then("the wait stays charged and its thread remains live until close acknowledges")
          fixture.engine._resource_budget.waitCount shouldBe 1
          fixture.timer.pendingCount shouldBe 1
          completed.thread.isAlive shouldBe true
          When("the fixture acknowledges its own registration close")
          release.countDown()
          _join(completed)
          fixture.timer.setCloseGate(None)
          Then("successful result, actual termination and exactly one close return capacity")
          completed.result.get() shouldBe a[Consequence.Success[_]]
          completed.thread.isAlive shouldBe false
          fixture.timer.acknowledged.get() shouldBe 1
          fixture.engine._resource_budget.waitCount shouldBe 0
          val timeoutid = _pending(fixture)
          val timeout = _start(() => fixture.engine.awaitResult(timeoutid, 86400000L))
          calls += timeout
          fixture.timer.awaitCalls(2) shouldBe true
          When("a second real wait reaches the manual timeout")
          fixture.timer.advanceBy(Duration.ofHours(24))
          _join(timeout)
          Then("timeout closes its own registration exactly once")
          _message(timeout.result.get()) should include("job timeout")
          fixture.timer.acknowledged.get() shouldBe 2
          fixture.engine._resource_budget.waitCount shouldBe 0
          val interrupted = _start(() => fixture.engine.awaitResult(timeoutid, 86400000L))
          calls += interrupted
          fixture.timer.awaitCalls(3) shouldBe true
          When("a third real wait is interrupted and physically joined")
          interrupted.thread.interrupt()
          _join(interrupted)
          Then("the original interrupted outcome and thread flag survive one acknowledged close")
          _message(interrupted.result.get()) should include("job await interrupted")
          interrupted.interrupted.get() shouldBe true
          interrupted.thread.isAlive shouldBe false
          fixture.timer.acknowledged.get() shouldBe 3
          fixture.timer.pendingCount shouldBe 0
          fixture.engine._resource_budget.waitCount shouldBe 0
          When("an immediate completed await reuses the released capacity")
          val next = fixture.engine.awaitResult(id, 0L)
          Then("it succeeds without another physical registration")
          next shouldBe a[Consequence.Success[_]]
          fixture.timer.calls.get() shouldBe 3
          fixture.engine._resource_budget.waitCount shouldBe 0
        } finally {
          release.countDown()
          calls.foreach(_stop(_))
          _dispose(fixture)
        }
      }

      "WT10 retain admitted waits through quiesce and wake only the closing owner" in {
        Given("two engines sharing State and one injected timer with physically registered local awaits")
        val fixture = _fixture()
        val other = _fixture(fixture.engine.runtimeState, Some(fixture.clock), Some(fixture.timer))
        val id = _pending(fixture)
        val one = _start(() => fixture.engine.awaitResult(id, 86400000L))
        val two = _start(() => other.engine.awaitResult(id, 86400000L))
        try {
          fixture.timer.awaitCalls(2) shouldBe true
          When("the first owner quiesces and a new wait is attempted")
          fixture.engine.quiesce()
          val refused = fixture.engine.awaitResult(id, 0L)
          Then("new waits refuse while both unfinished admitted waits remain physically registered and charged")
          refused shouldBe a[Consequence.Failure[_]]
          fixture.engine._resource_budget.waitCount shouldBe 2
          fixture.timer.pendingCount shouldBe 2
          one.thread.isAlive shouldBe true
          two.thread.isAlive shouldBe true
          When("the first owner force-cancels and its local thread is joined")
          fixture.engine.forceCancel()
          _join(one)
          Then("only the local registration closes while the other engine retains its active wait")
          one.thread.isAlive shouldBe false
          two.thread.isAlive shouldBe true
          fixture.engine._resource_budget.waitCount shouldBe 1
          fixture.timer.pendingCount shouldBe 1
          fixture.timer.shutdowns.get() shouldBe 0
          When("shutdown signals the second owner and its thread is physically joined")
          other.engine.shutdown()
          _join(two)
          Then("its acknowledged close releases the final wait without shutting down the injected shared timer")
          two.thread.isAlive shouldBe false
          other.engine._resource_budget.waitCount shouldBe 0
          fixture.timer.pendingCount shouldBe 0
          fixture.timer.acknowledged.get() shouldBe 2
          fixture.timer.shutdowns.get() shouldBe 0
        } finally {
          _stop(one)
          _stop(two)
          _dispose(other)
          _dispose(fixture)
        }
      }

      "WT11 conserve actual generated waits and refused control sequences" in {
        Given("finite valid and invalid durations generated under a fixed ScalaCheck seed")
        val generator = for {
          timeout <- Gen.oneOf(0L, 1L, 86400000L, -1L, 86400001L, Long.MaxValue)
          command <- Gen.oneOf(JobControlCommand.values.toSeq)
        } yield (timeout, command)
        val property = Prop.forAll(generator) { case (timeout, command) =>
          val fixture = _fixture()
          val complete = _success(fixture.engine.submit(List(new OutcomeTask()), fixture.context, _sync))
          val failed = _failed(fixture)
          val pending = _pending(fixture)
          val before = _facts(fixture, failed)
          var call = Option.empty[WaitCall[JobResult]]
          try {
            When("the generated duration is used on actual completed await and Async control")
            val result = fixture.engine.awaitResult(complete, timeout)
            val controlled = _control(fixture, failed, command, JobCommandMode.Async, timeout)
            Then("invalid durations preserve all facts while valid completed awaits need no timer")
            if (timeout < 0 || timeout > 86400000L) {
              result shouldBe a[Consequence.Failure[_]]
              controlled shouldBe a[Consequence.Failure[_]]
              _facts(fixture, failed) shouldBe before
            } else {
              result shouldBe a[Consequence.Success[_]]
              if (command == JobControlCommand.Retry) controlled shouldBe a[Consequence.Success[_]]
              else controlled shouldBe a[Consequence.Failure[_]]
            }
            fixture.engine._resource_budget.waitCount shouldBe 0
            fixture.timer.calls.get() shouldBe 0
            When("a real unfinished wait is registered and reaches its manual deadline")
            val waiting = _start(() => fixture.engine.awaitResult(pending, 86400000L))
            call = Some(waiting)
            fixture.timer.awaitCalls(1) shouldBe true
            fixture.timer.advanceBy(Duration.ofHours(24))
            _join(waiting)
            Then("the observed wait outcome, acknowledged close and joined thread conserve capacity")
            _message(waiting.result.get()) should include("job timeout")
            waiting.thread.isAlive shouldBe false
            fixture.timer.closeAttempts.get() shouldBe 1
            fixture.timer.acknowledged.get() shouldBe 1
            fixture.timer.pendingCount shouldBe 0
            fixture.engine._resource_budget.waitCount shouldBe 0
            true
          } finally {
            call.foreach(_stop(_))
            _dispose(fixture)
          }
        }
        When("ScalaCheck executes at least 24 successful seeded actual sequences")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709332L)), property)
        Then("all generated wait/control effects and close conservation satisfy the contract")
        checked.passed shouldBe true
        checked.succeeded should be >= 24
      }

      "WT12 close admitted registrations on throwing, backward or extreme clocks without laundering close ambiguity" in {
        Given("a normally admitted real wait before its injected clock changes")
        for (instant <- Vector(None, Some(_instant.minus(Duration.ofHours(25))), Some(Instant.MIN));
             ambiguous <- Vector(false, true)) {
          val fixture = _fixture()
          val id = _pending(fixture)
          val call = _start(() => fixture.engine.awaitResult(id, 86400000L))
          try {
            fixture.timer.awaitCalls(1) shouldBe true
            if (ambiguous) fixture.timer.setFault(TimerFault.CloseBefore)
            When("the admitted clock throws or moves backward beyond the admitted duration and the timer signals")
            fixture.clock.set(instant, throwing = instant.isEmpty)
            fixture.timer.signalAll()
            _join(call)
            Then("a fixed unavailable outcome attempts only its own close and retains only unacknowledged charge")
            _message(call.result.get()) should include("JobEngine resource capacity unavailable")
            _message(call.result.get()) should not include "PRIVATE-CLOCK"
            call.thread.isAlive shouldBe false
            fixture.timer.closeAttempts.get() shouldBe 1
            fixture.timer.acknowledged.get() shouldBe (if (ambiguous) 0 else 1)
            fixture.timer.pendingCount shouldBe (if (ambiguous) 1 else 0)
            fixture.engine._resource_budget.waitCount shouldBe (if (ambiguous) 1 else 0)
            fixture.timer.shutdowns.get() shouldBe 0
          } finally {
            fixture.clock.set(None)
            _stop(call)
            _dispose(fixture)
          }
        }
      }
    }
  }

  private val _instant = Instant.parse("2026-10-01T00:00:00Z")
  private val _missing = JobId("cncf", "wait", Some(_instant), Some("missing"))
  private val _sync = JobSubmitOption(runMode = JobRunMode.Sync)
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))
  private val _allow = new ControlProbe

  private final class ControlProbe extends JobControlPolicy {
    val calls = new AtomicInteger(0)
    def authorize(jobId: JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      calls.incrementAndGet()
      Consequence.unit
    }
  }

  private final class OutcomeTask(failed: Boolean = false) extends JobTask {
    val actionId: ActionId = ActionId.generate()
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      if (failed) TaskFailed(Consequence.stateInvalid[Nothing]("original failed task").conclusion)
      else TaskSucceeded(OperationResponse.Scalar("complete"))
    }
  }

  private final class ControlledClock extends JobTimeSource {
    val manual = new ManualJobTimeSource(_instant)
    val calls = new AtomicInteger(0)
    @volatile private var _override = Option.empty[Instant]
    @volatile private var _throwing = false
    def now(): Instant = {
      calls.incrementAndGet()
      if (_throwing) throw new IllegalStateException("PRIVATE-CLOCK/PATH")
      _override.getOrElse(manual.now())
    }
    def set(instant: Option[Instant], throwing: Boolean = false): Unit = {
      _override = instant
      _throwing = throwing
    }
  }

  private enum TimerFault {
    case None, ThrowAfter, NullAfter, CloseBefore
  }

  private final class PhysicalTimer(clock: ControlledClock) extends JobTimer {
    private final case class Entry(dueat: Instant, body: () => Unit, var closed: Boolean = false)
    private val _monitor = new Object
    private val _entries = ArrayBuffer.empty[Entry]
    @volatile private var _fault = TimerFault.None
    @volatile private var _close_gate = Option.empty[(CountDownLatch, CountDownLatch)]
    val calls = new AtomicInteger(0)
    val closeAttempts = new AtomicInteger(0)
    val acknowledged = new AtomicInteger(0)
    val shutdowns = new AtomicInteger(0)

    def setFault(fault: TimerFault): Unit = { _fault = fault }
    def setCloseGate(gate: Option[(CountDownLatch, CountDownLatch)]): Unit = { _close_gate = gate }

    def schedule(dueat: Instant)(body: => Unit): JobTimerRegistration = {
      val entry = _monitor.synchronized {
        val entry = Entry(dueat, () => body)
        _entries += entry
        calls.incrementAndGet()
        _monitor.notifyAll()
        entry
      }
      _fault match {
        case TimerFault.ThrowAfter => throw new IllegalStateException("PRIVATE-TIMER/PATH")
        case TimerFault.NullAfter => null
        case _ => new JobTimerRegistration {
          def close(): Unit = {
            closeAttempts.incrementAndGet()
            _close_gate.foreach { case (entered, release) =>
              entered.countDown()
              if (!release.await(5L, TimeUnit.SECONDS)) throw new IllegalStateException("close gate timed out")
            }
            if (_fault == TimerFault.CloseBefore) throw new IllegalStateException("PRIVATE-TIMER/CLOSE")
            _monitor.synchronized { entry.closed = true }
            acknowledged.incrementAndGet()
          }
        }
      }
    }

    def pendingCount: Int = _monitor.synchronized { _entries.count(!_.closed) }

    def awaitCalls(count: Int): Boolean = _monitor.synchronized {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
      var remaining = deadline - System.nanoTime()
      while (calls.get() < count && remaining > 0L) {
        _monitor.wait(remaining / 1000000L, (remaining % 1000000L).toInt)
        remaining = deadline - System.nanoTime()
      }
      calls.get() >= count
    }

    def advanceBy(duration: Duration): Unit = {
      clock.manual.advanceBy(duration)
      val due = _monitor.synchronized {
        _entries.filter(entry => !entry.closed && !entry.dueat.isAfter(clock.manual.now())).toVector
      }
      due.foreach(_.body())
    }

    def signalAll(): Unit = {
      val entries = _monitor.synchronized { _entries.filterNot(_.closed).toVector }
      entries.foreach(_.body())
    }

    def disposePhysical(): Unit = _monitor.synchronized { _entries.foreach(_.closed = true) }
    override def shutdown(): Unit = { shutdowns.incrementAndGet(); () }
  }

  private final case class Fixture(
    engine: InMemoryJobEngine,
    clock: ControlledClock,
    timer: PhysicalTimer,
    context: ExecutionContext,
    events: EventStore
  )

  private final case class Facts(
    record: Option[JobRecord],
    resources: JobResourceSnapshot,
    queue: Int,
    scope: JobCancellationScope,
    events: Int,
    registrations: Int,
    waits: Int
  )

  private final case class WaitCall[A](
    thread: Thread,
    result: AtomicReference[Consequence[A]],
    failure: AtomicReference[Exception],
    interrupted: AtomicBoolean
  )

  private def _fixture(
    state: InMemoryJobEngine.State = InMemoryJobEngine.State(),
    sharedclock: Option[ControlledClock] = None,
    sharedtimer: Option[PhysicalTimer] = None
  ): Fixture = {
    val clock = sharedclock.getOrElse(new ControlledClock)
    val timer = sharedtimer.getOrElse(new PhysicalTimer(clock))
    val events = EventStore.inMemory
    val engine = new InMemoryJobEngine(state, schedulerConfig = InMemoryJobEngine.SchedulerConfig(1, false),
      timeSource = clock, timer = Some(timer))(scala.concurrent.ExecutionContext.global)
    engine.withEventStore(events)
    Fixture(engine, clock, timer, ExecutionContext.test(), events)
  }

  private def _pending(fixture: Fixture): JobId =
    _success(fixture.engine.submit(List(new OutcomeTask()), fixture.context))

  private def _failed(fixture: Fixture): JobId =
    _success(fixture.engine.submit(List(new OutcomeTask(failed = true)), fixture.context, _sync))

  private def _control(
    fixture: Fixture,
    id: JobId,
    command: JobControlCommand,
    mode: JobCommandMode,
    timeout: Long = 3000L,
    poll: Long = 10L
  ): Consequence[JobControlResponse] =
    fixture.engine.control(id, JobControlRequest(command, JobControlOption(mode, timeout, poll)), _allow)(using fixture.context)

  private def _facts(fixture: Fixture, id: JobId): Facts =
    Facts(fixture.engine._get_record(id), fixture.engine.resourceSnapshot, fixture.engine._work_queue.size(),
      fixture.engine._cancellation_scopes.get(id), _events(fixture.events), fixture.timer.calls.get(),
      fixture.engine._resource_budget.waitCount)

  private def _start[A](body: () => Consequence[A], finished: Option[CountDownLatch] = None): WaitCall[A] = {
    val result = new AtomicReference[Consequence[A]]()
    val failure = new AtomicReference[Exception]()
    val interrupted = new AtomicBoolean(false)
    val thread = new Thread(new Runnable {
      def run(): Unit = {
        try result.set(body())
        catch {
          case exception: InterruptedException =>
            Thread.currentThread().interrupt()
            failure.set(exception)
          case NonFatal(exception: Exception) => failure.set(exception)
        } finally {
          interrupted.set(Thread.currentThread().isInterrupted)
          finished.foreach(_.countDown())
        }
      }
    }, "job-wait-resource-spec")
    thread.start()
    WaitCall(thread, result, failure, interrupted)
  }

  private def _join(call: WaitCall[?]): Unit = {
    call.thread.join(5000L)
    call.thread.isAlive shouldBe false
    call.failure.get() shouldBe null
  }

  private def _stop(call: WaitCall[?]): Unit = {
    if (call.thread.isAlive) call.thread.interrupt()
    call.thread.join(5000L)
    call.thread.isAlive shouldBe false
  }

  private def _dispose(fixture: Fixture): Unit = {
    fixture.engine.shutdown()
    // Test fault timers alone own this cleanup; it makes no budget acknowledgement.
    fixture.timer.disposePhysical()
  }

  private def _events(events: EventStore): Int = _success(events.query(EventStore.Query())).size

  private def _message[A](result: Consequence[A]): String = result match {
    case Consequence.Failure(conclusion) => conclusion.show
    case _ => ""
  }

  private def _native_failed(id: JobId): DurableJobRecord =
    _success(DurableJobRecord.createV2(DurableJobRecordBody(
      DurableJobIdentity(id.value, 1L, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(DurableJobLifecycleStatus.Failed, 0, DurableRunMode.Sync,
        DurableRetryEvidence(Vector.empty, 3, None, false, false), DurableScheduleState(None, Some(_instant), Some(_instant))),
      Vector(DurableTaskDescriptor(TaskId("cncf", "wait", Some(_instant), Some("native-task")).value, None,
        DurableTaskKind.Operation, DurableTaskTarget("operation", DurableOperationReference("component-a", Some("service-a"), "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Failed), None)),
      Vector.empty, DurableResultOutcome.Failed(DurableFailureSummary("execution", "failed", "original failed task", retryable = false)),
      Vector.empty, Vector.empty, None,
      DurableDefinitionSnapshot("definition-wait", "wait-job", 1, 1L, "a" * 64, None, None, Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
    )))

  private def _success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) => fail(conclusion.show)
  }
}
