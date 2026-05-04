package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.provisional.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.Assertions.fail
import org.scalatest.{GivenWhenThen, Tag}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobRetryTimingSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  import JobRetryTimingSpec.*

  "OPS-01 retry scheduler timing integration" should {
    "run delayed retry after real scheduler delay" taggedAs TimingTag in {
      val engine = createInMemoryJobEngine(
        retrySchedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60L)))
      )
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later"),
        attempts = attempts,
        succeedAt = 2
      )

      try {
        val jobId = _jobid(engine.submit(List(task), ExecutionContext.test()))
        val model = awaitQuery(
          engine,
          jobId,
          Set(JobStatus.Succeeded, JobStatus.Failed),
          timeoutMillis = 10000L
        ).getOrElse(fail(s"job query timeout: ${jobId.value}"))

        model.status shouldBe JobStatus.Succeeded
        model.retry.kind shouldBe JobRetryKind.Delayed
        model.retry.attemptCount shouldBe 1
        attempts.get() shouldBe 2
      } finally {
        engine.shutdown()
      }
    }

    "enqueue delayed retries into the shared scheduler under worker saturation" taggedAs TimingTag in {
      val engine = createInMemoryJobEngine(
        retrySchedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(200L))),
        schedulerConfig = InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later-queued"),
        attempts = attempts,
        succeedAt = 2
      )
      val blockerEntered = new CountDownLatch(1)
      val blockerRelease = new CountDownLatch(1)

      try {
        val jobId = _jobid(engine.submit(List(task), ExecutionContext.test()))
        awaitCondition({
          engine.query(jobId).exists(_.retry.nextRetryDueAt.nonEmpty)
        }, timeoutMillis = 5000L) shouldBe true
        val blockerId = _jobid(engine.submit(List(_BlockingTask(blockerEntered, blockerRelease, "blocker")), ExecutionContext.test()))
        blockerEntered.await(5, TimeUnit.SECONDS) shouldBe true

        awaitCondition({
          engine.query(jobId).exists { model =>
            model.retry.nextRetryDueAt.isEmpty &&
            model.retry.kind == JobRetryKind.Delayed
          }
        }, timeoutMillis = 5000L) shouldBe true
        attempts.get() shouldBe 1

        blockerRelease.countDown()
        awaitQuery(
          engine,
          blockerId,
          Set(JobStatus.Succeeded, JobStatus.Failed),
          timeoutMillis = 10000L
        ).getOrElse(fail(s"job query timeout: ${blockerId.value}"))
        val model = awaitQuery(
          engine,
          jobId,
          Set(JobStatus.Succeeded, JobStatus.Failed),
          timeoutMillis = 10000L
        ).getOrElse(fail(s"job query timeout: ${jobId.value}"))

        model.status shouldBe JobStatus.Succeeded
        attempts.get() shouldBe 2
      } finally {
        blockerRelease.countDown()
        engine.shutdown()
      }
    }

    "resume delayed retry after real scheduler engine restart" taggedAs TimingTag in {
      val state = InMemoryJobEngine.State()
      val schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60L)))
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later-restart"),
        attempts = attempts,
        succeedAt = 2
      )
      var engine1 = Option(createInMemoryJobEngine(runtimeState = state, retrySchedule = schedule))
      val jobId = _jobid(engine1.get.submit(List(task), ExecutionContext.test()))
      try {
        awaitCondition({
          engine1.get.query(jobId).exists(_.retry.nextRetryDueAt.nonEmpty)
        }, timeoutMillis = 5000L) shouldBe true

        engine1.get.shutdown()
        engine1 = None
        val engine2 = createInMemoryJobEngine(runtimeState = state, retrySchedule = schedule)
        try {
          val model = awaitQuery(
            engine2,
            jobId,
            Set(JobStatus.Succeeded, JobStatus.Failed),
            timeoutMillis = 10000L
          ).getOrElse(fail(s"job query timeout: ${jobId.value}"))

          model.status shouldBe JobStatus.Succeeded
          model.retry.kind shouldBe JobRetryKind.Delayed
          model.retry.attemptCount shouldBe 1
          attempts.get() shouldBe 2
        } finally {
          engine2.shutdown()
        }
      } finally {
        engine1.foreach(_.shutdown())
      }
    }

    "run delayed start after real scheduler delay" taggedAs TimingTag in {
      val engine = createJobEngine()
      val scheduledAt = Instant.now().plusMillis(150L)
      val jobId = _jobid(engine.submit(
        List(_ValueTask("delayed")),
        ExecutionContext.test(),
        JobSubmitOption(scheduledStartAt = Some(scheduledAt))
      ))

      try {
        val beforeDue = engine.query(jobId).get
        beforeDue.status shouldBe JobStatus.Submitted
        beforeDue.scheduledStartAt shouldBe Some(scheduledAt)
        beforeDue.timeline.events.exists(_.kind == "job.delayed.scheduled") shouldBe true

        awaitResult(engine, jobId, timeoutMillis = 10000L) shouldBe a[Some[_]]
        engine.query(jobId).get.timeline.events.exists(_.kind == "job.delayed.enqueued") shouldBe true
      } finally {
        engine.shutdown()
      }
    }

    "enqueue due delayed start into the shared scheduler under worker saturation" taggedAs TimingTag in {
      val engine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val blockerEntered = new CountDownLatch(1)
      val blockerRelease = new CountDownLatch(1)

      try {
        val blockerId = _jobid(engine.submit(List(_BlockingTask(blockerEntered, blockerRelease, "blocker")), ExecutionContext.test()))
        blockerEntered.await(1, TimeUnit.SECONDS) shouldBe true
        val scheduledAt = Instant.now().plusMillis(100L)
        val delayedId = _jobid(engine.submit(
          List(_ValueTask("queued-after-due")),
          ExecutionContext.test(),
          JobSubmitOption(scheduledStartAt = Some(scheduledAt))
        ))

        awaitCondition({
          engine.query(delayedId).exists(_.timeline.events.exists(_.kind == "job.delayed.enqueued"))
        }, timeoutMillis = 10000L) shouldBe true

        engine.getStatus(delayedId) shouldBe Some(JobStatus.Submitted)
        blockerRelease.countDown()
        awaitResult(engine, blockerId, timeoutMillis = 10000L)
        awaitResult(engine, delayedId, timeoutMillis = 10000L) shouldBe a[Some[_]]
      } finally {
        blockerRelease.countDown()
        engine.shutdown()
      }
    }

    "keep single-worker execution serialized over an observation window" taggedAs TimingTag in {
      val engine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1)
      )
      val running = new AtomicInteger(0)
      val maxRunning = new AtomicInteger(0)
      val firstEntered = new CountDownLatch(1)
      val secondEntered = new CountDownLatch(1)
      val release = new CountDownLatch(1)

      try {
        val firstId = _jobid(engine.submit(
          List(_ConcurrencyTask("first", running, maxRunning, firstEntered, release)),
          ExecutionContext.test()
        ))
        firstEntered.await(1, TimeUnit.SECONDS) shouldBe true
        val secondId = _jobid(engine.submit(
          List(_ConcurrencyTask("second", running, maxRunning, secondEntered, release)),
          ExecutionContext.test()
        ))

        secondEntered.await(150L, TimeUnit.MILLISECONDS) shouldBe false
        maxRunning.get() shouldBe 1
        engine.getStatus(secondId) shouldBe Some(JobStatus.Submitted)

        release.countDown()
        awaitResult(engine, firstId, timeoutMillis = 10000L)
        awaitResult(engine, secondId, timeoutMillis = 10000L) shouldBe a[Some[_]]
      } finally {
        release.countDown()
        engine.shutdown()
      }
    }
  }
}

object JobRetryTimingSpec {
  val TimingTag: Tag = Tag("org.goldenport.tags.TimingSpec")

  private def _failure(
    action: Disposition.UserAction,
    message: String
  ): Conclusion =
    Conclusion.simple(message).copy(disposition = Disposition(action))

  private def _jobid(p: Consequence[JobId]): JobId =
    p.toOption.get

  private final case class _CountingTask(
    actionId: ActionId = ActionId.generate(),
    conclusion: Conclusion,
    attempts: AtomicInteger,
    succeedAt: Int
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val current = attempts.incrementAndGet()
      if (current >= succeedAt)
        TaskSucceeded(OperationResponse.Scalar(s"ok-$current"))
      else
        TaskFailed(conclusion)
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
      release.await(30, TimeUnit.SECONDS)
      TaskSucceeded(OperationResponse.Scalar(value))
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

  private final case class _ConcurrencyTask(
    value: String,
    running: AtomicInteger,
    maxRunning: AtomicInteger,
    entered: CountDownLatch,
    release: CountDownLatch,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val current = running.incrementAndGet()
      _update_max(maxRunning, current)
      entered.countDown()
      release.await(30, TimeUnit.SECONDS)
      running.decrementAndGet()
      TaskSucceeded(OperationResponse.Scalar(value))
    }
  }

  private def _update_max(maxRunning: AtomicInteger, current: Int): Unit = {
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
