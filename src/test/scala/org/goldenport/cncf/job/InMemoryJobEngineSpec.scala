package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
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
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
class InMemoryJobEngineSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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
      val jobEngine = InMemoryJobEngine.create()
      val ctx = ExecutionContext.test()
      val task = ActionTask(ActionId.generate(), action, actionEngine, None)

      val jobid = jobEngine.submit(List(task), ctx)
      val result = _await_result_(jobEngine, jobid)

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
      val jobEngine = InMemoryJobEngine.create(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blocker = _BlockingTask(entered, release, "blocker")
      val queued = _ValueTask("queued")

      When("two async jobs are submitted")
      val first = jobEngine.submit(List(blocker), ExecutionContext.test())
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      val second = jobEngine.submit(List(queued), ExecutionContext.test())

      Then("the second job remains submitted with an async queued timeline event")
      _await_condition_ {
        jobEngine.query(second).exists { model =>
          model.status == JobStatus.Submitted &&
          model.timeline.events.exists(_.kind == "job.async.queued")
        }
      } shouldBe true

      release.countDown()
      _await_result_(jobEngine, first)
      _await_result_(jobEngine, second)
      jobEngine.shutdown()
    }

    "serialize async execution when workerCount is one" in {
      Given("two blocking jobs and a single-worker scheduler")
      val jobEngine = InMemoryJobEngine.create(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val running = new AtomicInteger(0)
      val maxRunning = new AtomicInteger(0)
      val firstEntered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val first = _ConcurrencyTask("first", running, maxRunning, Some(firstEntered), release)
      val second = _ConcurrencyTask("second", running, maxRunning, None, release)

      When("both jobs are submitted")
      val firstId = jobEngine.submit(List(first), ExecutionContext.test())
      firstEntered.await(1, TimeUnit.SECONDS) shouldBe true
      val secondId = jobEngine.submit(List(second), ExecutionContext.test())
      Thread.sleep(100L)

      Then("only one job is running at a time")
      maxRunning.get() shouldBe 1
      jobEngine.getStatus(secondId) shouldBe Some(JobStatus.Submitted)

      release.countDown()
      _await_result_(jobEngine, firstId)
      _await_result_(jobEngine, secondId)
      jobEngine.shutdown()
    }

    "allow parallel async execution up to workerCount" in {
      Given("two blocking jobs and a two-worker scheduler")
      val jobEngine = InMemoryJobEngine.create(
        InMemoryJobEngine.SchedulerConfig(workerCount = 2)
      )
      val running = new AtomicInteger(0)
      val maxRunning = new AtomicInteger(0)
      val entered = new CountDownLatch(2)
      val release = new CountDownLatch(1)
      val first = _ConcurrencyTask("first", running, maxRunning, Some(entered), release)
      val second = _ConcurrencyTask("second", running, maxRunning, Some(entered), release)

      When("both jobs are submitted")
      val firstId = jobEngine.submit(List(first), ExecutionContext.test())
      val secondId = jobEngine.submit(List(second), ExecutionContext.test())

      Then("both jobs can start in parallel")
      entered.await(1, TimeUnit.SECONDS) shouldBe true
      maxRunning.get() shouldBe 2

      release.countDown()
      _await_result_(jobEngine, firstId)
      _await_result_(jobEngine, secondId)
      jobEngine.shutdown()
    }

    "queue same-job async tasks before execution" in {
      Given("a completed parent job and a busy single-worker scheduler")
      val jobEngine = InMemoryJobEngine.create(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val parentId = jobEngine.submit(List(_ValueTask("parent")), ExecutionContext.test())
      _await_result_(jobEngine, parentId)

      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val blockerId = jobEngine.submit(List(_BlockingTask(entered, release, "blocker")), ExecutionContext.test())
      entered.await(1, TimeUnit.SECONDS) shouldBe true

      When("a same-job async task is enqueued while the worker is busy")
      val taskId = jobEngine.enqueueTaskInJob(parentId, _ValueTask("child"), ExecutionContext.test()).toOption.get

      Then("the task is queued and not started until worker capacity is available")
      _await_condition_ {
        jobEngine.query(parentId).exists(_.timeline.events.exists(e => e.kind == "job.same-job-async.queued" && e.taskId.contains(taskId)))
      } shouldBe true

      release.countDown()
      _await_result_(jobEngine, blockerId)
      _await_condition_ {
        jobEngine.query(parentId).exists(_.tasks.tasks.exists(_.taskId == taskId))
      } shouldBe true
      jobEngine.shutdown()
    }
  }

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

  private def _await_result_(
    engine: JobEngine,
    jobid: JobId
  ): Option[JobResult] = {
    val deadline = System.currentTimeMillis() + 2000L
    var result: Option[JobResult] = None
    while (result.isEmpty && System.currentTimeMillis() < deadline) {
      result = engine.getResult(jobid)
      if (result.isEmpty) {
        Thread.sleep(10)
      }
    }
    result
  }

  private def _await_condition_(
    p: => Boolean,
    timeoutMillis: Long = 2000L
  ): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var result = p
    while (!result && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L)
      result = p
    }
    result
  }
}
