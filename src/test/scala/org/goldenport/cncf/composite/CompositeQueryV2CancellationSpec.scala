package org.goldenport.cncf.composite

import java.util.concurrent.atomic.AtomicReference
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, JobContext, JobControlCommand, JobControlPolicy, JobControlRequest, JobEngine, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeQueryV2CancellationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e13 = afterWord("in spec:composite-query-v2, example:E13, rules:CQ2-R9, phase:69.5, slice:JM69-07A")
  private val _e14 = afterWord("in spec:composite-query-v2, example:E14, rules:CQ2-R5,CQ2-R9, phase:69.5, slice:JM69-07A")
  private val _e15 = afterWord("in spec:composite-query-v2, example:E15, rules:CQ2-R5,CQ2-R8,CQ2-R9, phase:69.5, slice:JM69-07A")
  private val _e16 = afterWord("in spec:composite-query-v2, example:E16, rules:CQ2-R9,CQ2-R10, phase:69.5, slice:JM69-07A")
  private val _e17 = afterWord("in spec:composite-query-v2, example:E17, rules:CQ2-R5,CQ2-R9, phase:69.5, slice:JM69-07A")
  private val _e18 = afterWord("in spec:composite-query-v2, example:E18, rules:CQ2-R9, phase:69.5, slice:JM69-07A")
  private val _e19 = afterWord("in spec:composite-query-v2, example:E19, rules:CQ2-R1,CQ2-R3, phase:69.5, slice:JM69-07A")
  private val _e20 = afterWord("in spec:composite-query-v2, example:E20, rules:CQ2-R9, phase:69.5, slice:JM69-07A")

  "CompositeQueryV2 cancellation" should {
    "E13 avoid admission for pre-cancelled real execution" must _e13 {
      "when the caller cancellation is already set" in _with_fixture { fixture =>
        Given("an admitted real query and a pre-cancelled token")
        val cancellation = CompositeQueryV2Cancellation.fresh
        cancellation.cancel()
        When("executeBlocking is entered")
        val response = _success(_execute(fixture, Vector(fixture.branch("first")), cancellation = cancellation))
        Then("no real QueryAction enters and the response is cancelled")
        response.status shouldBe CompositeQueryV2AggregateStatus.Cancelled
        fixture.state.queryCalls.isEmpty shouldBe true
      }

      "when an active real QueryAction receives caller cancellation" in _with_fixture { fixture =>
        Given("a gated QueryAction, active caller token, and inherited Job cancellation scope")
        val cancellation = CompositeQueryV2Cancellation.fresh
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(
          fixture,
          Vector(fixture.branch("active").copy(request = fixture.request("gate"))),
          cancellation = cancellation
        )))
        When("the running caller token is cancelled")
        try {
          caller.start()
          val entered = fixture.awaitEntered()
          cancellation.cancel()
          caller.join(1500L)
          fixture.release()
          val exited = fixture.state.awaitExited(1)
          Then("the owned child effect scope observes cancellation and the aggregate is terminal")
          entered shouldBe true
          exited shouldBe true
          _success(result.get()).status shouldBe CompositeQueryV2AggregateStatus.Cancelled
          fixture.state.cancelledObserved.get() shouldBe true
        } finally {
          fixture.release()
          caller.join(1500L)
        }
      }
    }

    "E14 cancel an active gated query without allowing queued query side effects" must _e14 {
      "when cancellation races with a scheduler-queued branch" in {
        Given("a one-worker official scheduler with one running and one admitted queued branch")
        val fixture = new CompositeQueryV2Fixture(workerCount = 1)
        val controlled = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        val cancellation = CompositeQueryV2Cancellation.fresh
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(
          fixture,
          Vector(fixture.branch("first").copy(request = fixture.request("gate")), fixture.branch("second")),
          CompositeQueryV2Policy(maxParallelism = 2),
          cancellation,
          controlled
        )))
        When("the first QueryAction enters and the caller cancels")
        try {
          caller.start()
          val entered = fixture.awaitEntered()
          cancellation.cancel()
          caller.join(1500L)
          fixture.release()
          val exited = fixture.state.awaitExited(1)
          val disposedsignal = new java.util.concurrent.atomic.AtomicInteger(0)
          val disposedregistration = CompositeQueryV2Cancellation.fresh
          val registration = disposedregistration.register(disposedsignal.incrementAndGet())
          registration.close()
          disposedregistration.cancel()
          val latesignal = new java.util.concurrent.atomic.AtomicInteger(0)
          val lateregistration = cancellation.register(latesignal.incrementAndGet())
          lateregistration.close()
          Then("the aggregate cancels admitted work, disposes registrations, and prevents queued QueryAction execution")
          entered shouldBe true
          controlled.admissions.get() shouldBe 2
          exited shouldBe true
          _success(result.get()).status shouldBe CompositeQueryV2AggregateStatus.Cancelled
          fixture.state.cancelledObserved.get() shouldBe true
          fixture.state.queryCalls.contains("second") shouldBe false
          disposedsignal.get() shouldBe 0
          latesignal.get() shouldBe 1
        } finally {
          fixture.release()
          caller.join(1500L)
          fixture.close()
        }
      }
    }

    "E15 retain the reservation of a non-cooperative timed-out query" must _e15 {
      "when an admitted branch waits behind a single-worker official scheduler" in {
        Given("a non-cooperative running QueryAction and a separately admitted queued branch")
        val fixture = new CompositeQueryV2Fixture(workerCount = 1)
        try {
          val queued = fixture.branch("queued").copy(timeoutMillis = Some(40L))
          When("queue wait consumes the queued branch deadline before its body starts")
          val response = _success(_execute(fixture, Vector(fixture.branch("running").copy(request = fixture.request("slow")), queued), CompositeQueryV2Policy(maxParallelism = 2, timeoutMillis = 250L)))
          Then("the queued branch times out without side effects and physical bodies never exceed worker width")
          response.result("queued").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.TimedOut)
          fixture.state.queryCalls.contains("queued") shouldBe false
          fixture.state.maxActive.get() should be <= 1
        } finally {
          fixture.release()
          fixture.close()
        }
      }

      "when a four-worker scheduler has a live body after its local timeout" in _with_fixture { fixture =>
        Given("a non-cooperative gated branch with an explicit TimedOut fallback and an independent queued branch")
        val controlled = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        val fallback = CompositeQueryV2Fallback(OperationResponse.void, Set(CompositeQueryV2FailureCode.TimedOut))
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(
          fixture,
          Vector(
            fixture.branch("timed").copy(request = fixture.request("gate"), timeoutMillis = Some(120L), fallback = Some(fallback)),
            fixture.branch("queued")
          ),
          CompositeQueryV2Policy(maxParallelism = 1, timeoutMillis = 1000L),
          engine = controlled
        )))
        When("the local deadline cancels the owned Job while its physical body remains live")
        try {
          caller.start()
          val entered = fixture.awaitEntered()
          val cancelsent = controlled.cancellationControls.await(1500L, java.util.concurrent.TimeUnit.MILLISECONDS)
          val queuedbeforeexit = fixture.state.queryCalls.contains("queued")
          val live = fixture.state.active.get()
          fixture.release()
          val exited = fixture.state.awaitExited(1)
          caller.join(1500L)
          Then("fallback is terminal but the live reservation blocks the queued body until actual exit")
          entered shouldBe true
          cancelsent shouldBe true
          queuedbeforeexit shouldBe false
          live shouldBe 1
          fixture.state.maxActive.get() should be <= 1
          exited shouldBe true
          _success(result.get()).result("timed").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Fallback)
          _success(result.get()).result("queued").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
          fixture.state.queryCalls.contains("queued") shouldBe true
        } finally {
          fixture.release()
          caller.join(1500L)
        }
      }
    }

    "E16 preserve completed result when request deadline seals remaining work" must _e16 {
      "when an echo precedes a gated branch" in _with_fixture { fixture =>
        Given("a completed first QueryAction, a fallback-bearing incomplete gate, and an owned scheduler")
        val controlled = new CompositeQueryV2ControlledJobEngine(fixture.jobEngine)
        val branches = Vector(
          fixture.branch("done"),
          fixture.branch("late").copy(
            request = fixture.request("gate"),
            fallback = Some(CompositeQueryV2Fallback(OperationResponse.void, Set(CompositeQueryV2FailureCode.TimedOut)))
          )
        )
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(
          fixture,
          branches,
          CompositeQueryV2Policy(maxParallelism = 1, timeoutMillis = 300L),
          engine = controlled
        )))
        When("the total request deadline expires")
        try {
          caller.start()
          val entered = fixture.awaitEntered(2)
          val cancelsent = controlled.cancellationControls.await(1500L, java.util.concurrent.TimeUnit.MILLISECONDS)
          caller.join(1500L)
          fixture.release()
          val exited = fixture.state.awaitExited(2)
          val response = _success(result.get())
          Then("the completed response remains, global timeout does not choose fallback, and owned scope is cancelled")
          entered shouldBe true
          cancelsent shouldBe true
          response.result("done").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Succeeded)
          response.result("late").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.TimedOut)
          response.result("late").flatMap(_.response) shouldBe None
          response.result("late").flatMap(_.recoveredFailure) shouldBe None
          fixture.state.cancelledObserved.get() shouldBe true
          exited shouldBe true
        } finally {
          fixture.release()
          caller.join(1500L)
        }
      }
    }

    "E17 reject late success after branch timeout" must _e17 {
      "when a timed-out gate is later released" in _with_fixture { fixture =>
        Given("a gated QueryAction with short branch deadline")
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(fixture, Vector(
          fixture.branch("lateSuccess").copy(request = fixture.request("gate"), timeoutMillis = Some(40L)),
          fixture.branch("lateFailure").copy(request = fixture.request("failgate"), timeoutMillis = Some(40L)),
          fixture.branch("successDependent", dependencies = Vector(CompositeQueryV2Dependency("lateSuccess"))),
          fixture.branch("failureDependent", dependencies = Vector(CompositeQueryV2Dependency("lateFailure")))
        ), CompositeQueryV2Policy(maxParallelism = 2, timeoutMillis = 250L))))
        When("the caller observes timeout then the gate is released")
        try {
          caller.start()
          val entered = fixture.awaitEntered(2)
          caller.join(1000L)
          fixture.release()
          caller.join(1000L)
          Then("late success and late failure cannot replace timeout or start their value-dependent branches")
          entered shouldBe true
          _success(result.get()).result("lateSuccess").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.TimedOut)
          _success(result.get()).result("lateFailure").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.TimedOut)
          _success(result.get()).result("successDependent").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Skipped)
          _success(result.get()).result("failureDependent").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Skipped)
        } finally {
          fixture.release()
          caller.join(1000L)
        }
        Given("a separate active branch whose owning Job is externally cancelled")
        val external = new CompositeQueryV2Fixture
        val controlled = new CompositeQueryV2ControlledJobEngine(external.jobEngine)
        val externalresult = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val externalcaller = new Thread(() => externalresult.set(_execute(external, Vector(external.branch("external").copy(request = external.request("gate"))), engine = controlled)))
        try {
          When("the real JobEngine receives an external Cancel and then shuts down after release")
          externalcaller.start()
          val externalentered = external.awaitEntered()
          given ExecutionContext = ExecutionContext.test()
          val externalpolicy = new JobControlPolicy {
            def authorize(jobid: org.goldenport.cncf.job.JobId, request: JobControlRequest)(using ExecutionContext): Consequence[Unit] = Consequence.unit
          }
          val controlledjob = controlled.submitted.peek()
          val cancelled = external.jobEngine.control(controlledjob, JobControlRequest(JobControlCommand.Cancel), externalpolicy)
          external.release()
          externalcaller.join(1500L)
          external.jobEngine.shutdown()
          Then("external cancellation is terminal and shutdown leaves no caller registration stranded")
          externalentered shouldBe true
          cancelled.isSuccess shouldBe true
          _success(externalresult.get()).result("external").map(_.outcome) shouldBe Some(CompositeQueryV2Outcome.Cancelled)
        } finally {
          external.release()
          externalcaller.join(1500L)
          external.close()
        }
      }
    }

    "E18 terminate boundedly after interruption and active engine shutdown" must _e18 {
      "when an active caller thread is interrupted" in _with_fixture { fixture =>
        Given("a gated real QueryAction and caller thread")
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val interrupted = new java.util.concurrent.atomic.AtomicBoolean(false)
        val caller = new Thread(() => {
          result.set(_execute(fixture, Vector(fixture.branch("gate").copy(request = fixture.request("gate")))))
          interrupted.set(Thread.currentThread.isInterrupted)
        })
        When("the waiting caller is interrupted")
        try {
          caller.start()
          val entered = fixture.awaitEntered()
          caller.interrupt()
          caller.join(1000L)
          Then("the aggregate is cancelled, caller interrupt is restored, and worker scope can be released")
          entered shouldBe true
          _success(result.get()).status shouldBe CompositeQueryV2AggregateStatus.Cancelled
          interrupted.get() shouldBe true
        } finally {
          fixture.release()
          caller.join(1000L)
        }
      }

      "when the supplied JobEngine shuts down while a real QueryAction and coordinator caller are active" in {
        Given("a live gated QueryAction, an active compositor caller, and its supplied official scheduler")
        val fixture = new CompositeQueryV2Fixture
        val result = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val caller = new Thread(() => result.set(_execute(
          fixture,
          Vector(fixture.branch("shutdown").copy(request = fixture.request("gate"))),
          CompositeQueryV2Policy(timeoutMillis = 1000L)
        )))
        When("the engine shuts down before the live body is released")
        try {
          caller.start()
          val entered = fixture.awaitEntered()
          fixture.jobEngine.shutdown()
          caller.join(1500L)
          val terminated = !caller.isAlive
          fixture.release()
          val exited = fixture.state.awaitExited(1)
          Then("shutdown produces a bounded terminal result and leaves no active caller registration")
          entered shouldBe true
          terminated shouldBe true
          _success(result.get()).status shouldBe CompositeQueryV2AggregateStatus.Cancelled
          exited shouldBe true
        } finally {
          fixture.release()
          caller.join(1500L)
          fixture.close()
        }
      }
    }

    "E19 reject a real active Job worker before nested admission" must _e19 {
      "when an existing JobTask invokes the V2 engine" in {
        Given("a real scheduler task whose JobContext is active")
        val fixture = new CompositeQueryV2Fixture(workerCount = 1)
        try {
        val observed = new AtomicReference[Consequence[CompositeQueryV2Response]]()
        val task = new JobTask {
          val actionId: ActionId = ActionId.generate()
          def run(context: ExecutionContext): TaskOutcome = {
            given ExecutionContext = context
            observed.set(CompositeQueryV2Engine(fixture.subsystem, fixture.jobEngine, CompositeQueryV2Targets.empty).executeBlocking(CompositeQueryV2Request(Vector(fixture.branch("nested")))))
            TaskSucceeded(OperationResponse.void)
          }
        }
        given ExecutionContext = ExecutionContext.test()
        When("the existing JobEngine runs that task")
        val job = _success(fixture.jobEngine.submit(List(task), summon[ExecutionContext]))
        val completed = fixture.jobEngine.awaitResult(job, 1000L)
        Then("nested blocking composition is refused before its own branch admission")
        completed.isSuccess shouldBe true
        observed.get().isSuccess shouldBe false
        fixture.state.queryCalls.isEmpty shouldBe true
        } finally fixture.close()
      }
    }

    "E20 reject actual cancellation control for an unrelated Job ID" must _e20 {
      "when an invocation-scoped policy sees another Job" in _with_fixture { fixture =>
        Given("a policy with no captured job IDs and an unrelated real Job ID")
        given ExecutionContext = ExecutionContext.test()
        val task = new JobTask { val actionId: ActionId = ActionId.generate(); def run(context: ExecutionContext): TaskOutcome = TaskSucceeded(OperationResponse.void) }
        val other = _success(fixture.jobEngine.submit(List(task), summon[ExecutionContext]))
        val policy = new CompositeQueryV2JobControlPolicy(() => Set.empty, summon[ExecutionContext].security.principal.id.toString)
        When("the actual JobEngine control path receives the invocation-scoped policy")
        val result = fixture.jobEngine.control(other, JobControlRequest(JobControlCommand.Cancel), policy)
        Then("the unrelated Job cannot be controlled and its terminal state remains available")
        result.isSuccess shouldBe false
        fixture.jobEngine.getStatus(other).nonEmpty shouldBe true
      }
    }
  }

  private def _execute(fixture: CompositeQueryV2Fixture, branches: Vector[CompositeQueryV2Branch], policy: CompositeQueryV2Policy = CompositeQueryV2Policy(), cancellation: CompositeQueryV2Cancellation = CompositeQueryV2Cancellation.fresh, engine: JobEngine = null): Consequence[CompositeQueryV2Response] = {
    given ExecutionContext = ExecutionContext.test()
    CompositeQueryV2Engine(fixture.subsystem, Option(engine).getOrElse(fixture.jobEngine), CompositeQueryV2Targets.empty).executeBlocking(CompositeQueryV2Request(branches, policy), cancellation)
  }

  private def _with_fixture(body: CompositeQueryV2Fixture => Unit): Unit = { val fixture = new CompositeQueryV2Fixture; try body(fixture) finally fixture.close() }
  private def _success[A](value: Consequence[A]): A = value.toOption.getOrElse(fail("expected Consequence.Success"))
}
