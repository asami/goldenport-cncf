package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.Response
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction, ResourceAccess}
import org.goldenport.cncf.context.ExecutionContext
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jan.  4, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class InMemoryJobEngineSpec extends AnyWordSpec with Matchers with GivenWhenThen with JobEngineTestFixture {
  "InMemoryJobEngine" should {
    "execute a Command as a Job and store the result" in {
      val action = new CommandAction() {
        // val name = "test"
        val request = Request.ofOperation("test")
        override def createCall(core: ActionCall.Core): ActionCall = {
          val actionself = this
          val _core_ = core
          new ActionCall {
            override val core: ActionCall.Core = _core_
            override def action: Action = actionself
            def execute(): Consequence[OperationResponse] =
              Consequence.success(OperationResponse.Scalar("ok"))
          }
        }
      }

      val actionEngine = ActionEngine.create()
      val jobEngine = createJobEngine()
      val ctx = ExecutionContext.test()
      val task = ActionTask(ActionId.generate(), action, actionEngine, None)

      val jobid = _jobid(jobEngine.submit(List(task), ctx))
      val result = awaitResult(jobEngine, jobid)

      result.isDefined shouldBe true
      result.get match {
        case JobResult.Success(res) =>
          res.toResponse shouldBe Response.Scalar("ok")
        case JobResult.Failure(c) =>
          fail(c.toString)
      }
      jobEngine.shutdown()
    }

    "queue async jobs before execution and expose queue timeline" in {
      Given("a single-worker scheduler with a blocking first job")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blocker = _BlockingTask(entered, release, "blocker")
      val queued = _ValueTask("queued")

      When("two async jobs are submitted")
      val first = _jobid(jobEngine.submit(List(blocker), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val second = _jobid(jobEngine.submit(List(queued), ExecutionContext.test()))

      Then("the second job remains submitted with an async queued timeline event")
      awaitCondition {
        jobEngine.query(second).exists { model =>
          model.status == JobStatus.Submitted &&
          model.timeline.events.exists(_.kind == "job.async.queued")
        }
      } shouldBe true

      release.countDown()
      awaitResult(jobEngine, first)
      awaitResult(jobEngine, second)
      jobEngine.shutdown()
    }

    "serialize async execution when workerCount is one" in {
      Given("two blocking jobs and a single-worker scheduler")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val running = new AtomicInteger(0)
      val maxRunning = new AtomicInteger(0)
      val firstEntered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val first = _ConcurrencyTask("first", running, maxRunning, Some(firstEntered), release)
      val second = _ConcurrencyTask("second", running, maxRunning, None, release)

      When("both jobs are submitted")
      val firstId = _jobid(jobEngine.submit(List(first), ExecutionContext.test()))
      firstEntered.await(1, TimeUnit.SECONDS) shouldBe true
      val secondId = _jobid(jobEngine.submit(List(second), ExecutionContext.test()))
      val queued = jobEngine.query(secondId).get

      Then("only one job is running at a time")
      maxRunning.get() shouldBe 1
      queued.status shouldBe JobStatus.Submitted
      queued.timeline.events.exists(_.kind == "job.async.queued") shouldBe true

      release.countDown()
      awaitResult(jobEngine, firstId)
      awaitResult(jobEngine, secondId)
      jobEngine.shutdown()
    }

    "allow parallel async execution up to workerCount" in {
      Given("two blocking jobs and a two-worker scheduler")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 2)
      )
      val running = new AtomicInteger(0)
      val maxRunning = new AtomicInteger(0)
      val entered = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val first = _ConcurrencyTask("first", running, maxRunning, Some(entered), release)
      val second = _ConcurrencyTask("second", running, maxRunning, Some(entered), release)

      When("both jobs are submitted")
      val firstId = _jobid(jobEngine.submit(List(first), ExecutionContext.test()))
      val secondId = _jobid(jobEngine.submit(List(second), ExecutionContext.test()))

      Then("both jobs can start in parallel")
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      maxRunning.get() shouldBe 2

      release.countDown()
      awaitResult(jobEngine, firstId)
      awaitResult(jobEngine, secondId)
      jobEngine.shutdown()
    }

    "run lower numeric priority jobs before higher numeric priority jobs" in {
      Given("a busy single-worker scheduler and two queued jobs with different priorities")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val order = ArrayBuffer.empty[String]

      val blockerId = _jobid(jobEngine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val lowId = _jobid(jobEngine.submit(List(_RecordingTask("low", order)), ExecutionContext.test(), JobSubmitOption(priority = 10)))
      val highId = _jobid(jobEngine.submit(List(_RecordingTask("high", order)), ExecutionContext.test(), JobSubmitOption(priority = -10)))

      When("worker capacity becomes available")
      release.countDown()
      awaitResult(jobEngine, blockerId)
      awaitResult(jobEngine, lowId)
      awaitResult(jobEngine, highId)

      Then("the lower numeric priority runs first")
      order.toVector shouldBe Vector("high", "low")
      jobEngine.shutdown()
    }

    "preserve FIFO order for same-priority queued jobs" in {
      Given("a busy single-worker scheduler and two queued jobs with the same priority")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val order = ArrayBuffer.empty[String]

      val blockerId = _jobid(jobEngine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val firstId = _jobid(jobEngine.submit(List(_RecordingTask("first", order)), ExecutionContext.test(), JobSubmitOption(priority = 0)))
      val secondId = _jobid(jobEngine.submit(List(_RecordingTask("second", order)), ExecutionContext.test(), JobSubmitOption(priority = 0)))

      When("worker capacity becomes available")
      release.countDown()
      awaitResult(jobEngine, blockerId)
      awaitResult(jobEngine, firstId)
      awaitResult(jobEngine, secondId)

      Then("queue order remains FIFO within the same priority")
      order.toVector shouldBe Vector("first", "second")
      jobEngine.shutdown()
    }

    "queue same-job async tasks before execution" in {
      Given("a completed parent job and a busy single-worker scheduler")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val parentId = _jobid(jobEngine.submit(List(_ValueTask("parent")), ExecutionContext.test()))
      awaitResult(jobEngine, parentId)

      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blockerId = _jobid(jobEngine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true

      When("a same-job async task is enqueued while the worker is busy")
      val taskId = jobEngine.enqueueTaskInJob(parentId, _ValueTask("child"), ExecutionContext.test()).toOption.get

      Then("the task is queued and not started until worker capacity is available")
      awaitCondition {
        jobEngine.query(parentId).exists(_.timeline.events.exists(e => e.kind == "job.same-job-async.queued" && e.taskId.contains(taskId)))
      } shouldBe true

      release.countDown()
      awaitResult(jobEngine, blockerId)
      awaitCondition {
        jobEngine.query(parentId).exists(_.tasks.tasks.exists(_.taskId == taskId))
      } shouldBe true
      jobEngine.shutdown()
    }

    "inherit parent priority for same-job async tasks" in {
      Given("a completed high-priority parent job and a competing lower-priority job")
      val jobEngine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val order = ArrayBuffer.empty[String]
      val parentId = _jobid(jobEngine.submit(
        List(_ValueTask("parent")),
        ExecutionContext.test(),
        JobSubmitOption(priority = -5)
      ))
      awaitResult(jobEngine, parentId)

      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blockerId = _jobid(jobEngine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test()))
      entered.await(1, TimeUnit.SECONDS) shouldBe true

      val regularId = _jobid(jobEngine.submit(
        List(_RecordingTask("regular", order)),
        ExecutionContext.test(),
        JobSubmitOption(priority = 5)
      ))

      When("a same-job async task is enqueued after the lower-priority job")
      val _ = jobEngine.enqueueTaskInJob(parentId, _RecordingTask("same-job", order), ExecutionContext.test()).toOption.get
      release.countDown()
      awaitResult(jobEngine, blockerId)
      awaitResult(jobEngine, regularId)

      Then("the same-job task inherits the parent priority and runs first")
      awaitCondition(order.synchronized(order.size == 2)) shouldBe true
      order.synchronized(order.toVector) shouldBe Vector("same-job", "regular")
      jobEngine.shutdown()
    }

    "treat scheduledStartAt at or before now as immediate async execution" in {
      Given("an async job whose scheduled start is already due")
      val jobEngine = createJobEngine()

      When("the job is submitted")
      val jobId = _jobid(jobEngine.submit(
        List(_ValueTask("immediate")),
        ExecutionContext.test(),
        JobSubmitOption(scheduledStartAt = Some(Instant.now().minusSeconds(1)))
      ))

      Then("it behaves like an immediate async submit")
      awaitResult(jobEngine, jobId) shouldBe a[Some[_]]
      jobEngine.query(jobId).get.timeline.events.exists(_.kind == "job.async.queued") shouldBe true
      jobEngine.shutdown()
    }

    "reject delayed start beyond the built-in maximum window" in {
      Given("a delayed start request beyond the supported maximum")
      val jobEngine = createJobEngine()

      When("the job is submitted")
      val result = jobEngine.submit(
          List(_ValueTask("too-late")),
          ExecutionContext.test(),
          JobSubmitOption(scheduledStartAt = Some(Instant.now().plusSeconds(16 * 60L)))
        )

      Then("submission fails deterministically")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.show should include ("scheduledStartAt exceeds built-in max delay")
        case _ =>
          fail("expected delayed submit failure")
      }
      jobEngine.shutdown()
    }

    "rehydrate persistent delayed jobs after engine restart" in {
      Given("a persistent delayed-start job and shared durable state")
      val state = InMemoryJobEngine.State()
      val schedule = InMemoryJobEngine.RetrySchedule.default
      val clock = new ManualJobTimeSource(Instant.parse("2026-05-04T00:00:00Z"))
      val timer1 = new InMemoryJobEngine.ManualJobTimer(clock)
      val scheduledAt = clock.now().plusMillis(120L)
      val engine1 = createManualInMemoryJobEngine(state, schedule, clock, timer1)
      val jobId = _jobid(engine1.submit(
        List(_ValueTask("rehydrated")),
        ExecutionContext.test(),
        JobSubmitOption(scheduledStartAt = Some(scheduledAt))
      ))
      engine1.query(jobId).flatMap(_.scheduledStartAt) shouldBe Some(scheduledAt)

      When("the engine is restarted before the delayed start fires")
      engine1.shutdown()
      val timer2 = new InMemoryJobEngine.ManualJobTimer(clock)
      val engine2 = createManualInMemoryJobEngine(state, schedule, clock, timer2)

      Then("the delayed job is rehydrated and executes after manual clock advance")
      timer2.pendingCount shouldBe 1
      timer2.advanceBy(Duration.ofMillis(120L)) shouldBe 1
      engine2.drainAll()
      awaitResult(engine2, jobId) shouldBe a[Some[_]]
      engine2.shutdown()
    }

  }

  private def _jobid(p: Consequence[JobId]): JobId =
    p.toOption.get

  private final case class _ValueTask(
    value: String,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private final case class _BlockingTask(
    entered: CountDownLatch,
    release: CountDownLatch,
    value: String,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      entered.countDown()
      release.await(2, TimeUnit.SECONDS)
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private final case class _ConcurrencyTask(
    value: String,
    running: AtomicInteger,
    maxRunning: AtomicInteger,
    entered: Option[CountDownLatch],
    release: CountDownLatch,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val current = running.incrementAndGet()
      _update_max_(maxRunning, current)
      entered.foreach(_.countDown())
      release.await(2, TimeUnit.SECONDS)
      running.decrementAndGet()
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private final case class _RecordingTask(
    value: String,
    trace: ArrayBuffer[String],
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      trace.synchronized {
        trace += value
      }
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private def _update_max_(maxRunning: AtomicInteger, current: Int): Unit = {
    var updated = false
    while (!updated) {
      val previous = maxRunning.get()
      if (current <= previous)
        updated = true
      else
        updated = maxRunning.compareAndSet(previous, current)
    }
  }

}
