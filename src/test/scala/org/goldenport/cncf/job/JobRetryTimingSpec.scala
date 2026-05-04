package org.goldenport.cncf.job

import java.time.Duration
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
        val model = _await_query(engine, jobId)

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
        _await_condition {
          engine.query(jobId).exists(_.retry.nextRetryDueAt.nonEmpty)
        } shouldBe true
        val blockerId = _jobid(engine.submit(List(_BlockingTask(blockerEntered, blockerRelease, "blocker")), ExecutionContext.test()))
        blockerEntered.await(5, TimeUnit.SECONDS) shouldBe true

        _await_condition {
          engine.query(jobId).exists { model =>
            model.retry.nextRetryDueAt.isEmpty &&
            model.retry.kind == JobRetryKind.Delayed
          }
        } shouldBe true
        attempts.get() shouldBe 1

        blockerRelease.countDown()
        _await_query(engine, blockerId)
        val model = _await_query(engine, jobId)

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
        _await_condition {
          engine1.get.query(jobId).exists(_.retry.nextRetryDueAt.nonEmpty)
        } shouldBe true

        engine1.get.shutdown()
        engine1 = None
        val engine2 = createInMemoryJobEngine(runtimeState = state, retrySchedule = schedule)
        try {
          val model = _await_query(engine2, jobId)

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

  private def _await_query(
    engine: JobEngine,
    jobId: JobId,
    statuses: Set[JobStatus] = Set(JobStatus.Succeeded, JobStatus.Failed)
  ): JobQueryReadModel = {
    val deadline = System.currentTimeMillis() + 10000L
    var result = Option.empty[JobQueryReadModel]
    while (
      (result.isEmpty || !statuses.contains(result.get.status)) &&
      System.currentTimeMillis() < deadline
    ) {
      result = engine.query(jobId)
      if (result.isEmpty || !statuses.contains(result.get.status))
        Thread.sleep(10L)
    }
    result.getOrElse(fail(s"job query timeout: ${jobId.value}"))
  }

  private def _await_condition(
    p: => Boolean,
    timeoutMillis: Long = 5000L
  ): Boolean = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var matched = p
    while (!matched && System.currentTimeMillis() < deadline) {
      Thread.sleep(10L)
      matched = p
    }
    matched
  }
}
