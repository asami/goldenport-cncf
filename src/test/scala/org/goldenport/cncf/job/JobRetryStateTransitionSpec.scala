package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.provisional.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May.  4, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobRetryStateTransitionSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  import JobRetryStateTransitionSpec.*

  "OPS-01 deterministic retry state transitions" should {
    "retry immediately without wall-clock waiting" in {
      val fixture = createManualJobEngine()
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryNow, "retry-now"),
        attempts = attempts,
        succeedAt = 3
      )

      try {
        val jobId = _jobid(fixture.engine.submit(List(task), ExecutionContext.test()))
        fixture.engine.drainAll()
        val model = fixture.engine.query(jobId).get

        model.status shouldBe JobStatus.Succeeded
        model.retry.kind shouldBe JobRetryKind.Immediate
        model.retry.attemptCount shouldBe 2
        attempts.get() shouldBe 3
      } finally {
        fixture.engine.shutdown()
      }
    }

    "promote delayed RetryLater only after manual time advance" in {
      val fixture = createManualJobEngine(
        schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60L)))
      )
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later"),
        attempts = attempts,
        succeedAt = 2
      )

      try {
        val jobId = _jobid(fixture.engine.submit(List(task), ExecutionContext.test()))
        fixture.engine.drainOne() shouldBe true
        val scheduled = fixture.engine.query(jobId).get
        scheduled.status shouldBe JobStatus.Submitted
        scheduled.retry.nextRetryDueAt should not be empty
        attempts.get() shouldBe 1

        fixture.timer.fireDue() shouldBe 0
        fixture.engine.drainAll() shouldBe 0
        attempts.get() shouldBe 1

        fixture.timer.advanceBy(Duration.ofMillis(60L)) shouldBe 1
        val enqueued = fixture.engine.query(jobId).get
        enqueued.retry.nextRetryDueAt shouldBe empty
        attempts.get() shouldBe 1

        fixture.engine.drainAll()
        val model = fixture.engine.query(jobId).get
        model.status shouldBe JobStatus.Succeeded
        model.retry.kind shouldBe JobRetryKind.Delayed
        model.retry.attemptCount shouldBe 1
        attempts.get() shouldBe 2
      } finally {
        fixture.engine.shutdown()
      }
    }

    "preserve original priority when delayed retry joins the ready queue" in {
      val fixture = createManualJobEngine(
        schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(80L)))
      )
      val order = ArrayBuffer.empty[String]
      val attempts = new AtomicInteger(0)

      try {
        val retryId = _jobid(fixture.engine.submit(
          List(_PriorityRetryTask(order, attempts)),
          ExecutionContext.test(),
          JobSubmitOption(priority = -5)
        ))
        fixture.engine.drainOne() shouldBe true
        fixture.engine.query(retryId).get.retry.nextRetryDueAt should not be empty

        val regularId = _jobid(fixture.engine.submit(
          List(_RecordingTask("regular", order)),
          ExecutionContext.test(),
          JobSubmitOption(priority = 0)
        ))

        fixture.timer.advanceBy(Duration.ofMillis(80L)) shouldBe 1
        fixture.engine.drainAll()

        fixture.engine.query(retryId).get.status shouldBe JobStatus.Succeeded
        fixture.engine.query(regularId).get.status shouldBe JobStatus.Succeeded
        order.synchronized(order.toVector) shouldBe Vector("retry", "regular")
        attempts.get() shouldBe 2
      } finally {
        fixture.engine.shutdown()
      }
    }

    "dead-letter exhausted delayed retries without wall-clock waiting" in {
      val fixture = createManualJobEngine(
        schedule = InMemoryJobEngine.RetrySchedule(
          delayedRetryDelays = Vector(Duration.ofMillis(20L), Duration.ofMillis(30L), Duration.ofMillis(40L))
        )
      )
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later-exhausted"),
        attempts = attempts,
        succeedAt = Int.MaxValue
      )

      try {
        val jobId = _jobid(fixture.engine.submit(List(task), ExecutionContext.test()))
        fixture.engine.drainOne() shouldBe true
        Vector(20L, 30L, 40L).foreach { millis =>
          fixture.timer.advanceBy(Duration.ofMillis(millis)) shouldBe 1
          fixture.engine.drainAll()
        }
        val model = fixture.engine.query(jobId).get

        model.status shouldBe JobStatus.Failed
        model.retry.kind shouldBe JobRetryKind.Delayed
        model.retry.attemptCount shouldBe 3
        model.retry.exhausted shouldBe true
        model.retry.deadLetter shouldBe true
        model.retry.poison shouldBe false
        model.retry.recoveryRequired shouldBe true
        attempts.get() shouldBe 4
      } finally {
        fixture.engine.shutdown()
      }
    }

    "rehydrate delayed retries into the manual timer after restart" in {
      val state = InMemoryJobEngine.State()
      val schedule = InMemoryJobEngine.RetrySchedule(Vector(Duration.ofMillis(60L)))
      val clock = new ManualJobTimeSource(Instant.parse("2026-05-04T00:00:00Z"))
      val timer1 = new InMemoryJobEngine.ManualJobTimer(clock)
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later-restart"),
        attempts = attempts,
        succeedAt = 2
      )
      val engine1 = createManualInMemoryJobEngine(state, schedule, clock, timer1)
      val jobId = _jobid(engine1.submit(List(task), ExecutionContext.test()))

      engine1.drainOne() shouldBe true
      engine1.query(jobId).get.retry.nextRetryDueAt should not be empty
      engine1.shutdown()

      val timer2 = new InMemoryJobEngine.ManualJobTimer(clock)
      val engine2 = createManualInMemoryJobEngine(state, schedule, clock, timer2)
      try {
        timer2.pendingCount shouldBe 1
        timer2.advanceBy(Duration.ofMillis(60L)) shouldBe 1
        engine2.drainAll()
        val model = engine2.query(jobId).get

        model.status shouldBe JobStatus.Succeeded
        model.retry.kind shouldBe JobRetryKind.Delayed
        model.retry.attemptCount shouldBe 1
        attempts.get() shouldBe 2
      } finally {
        engine2.shutdown()
      }
    }
  }
}

object JobRetryStateTransitionSpec {
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

  private final case class _PriorityRetryTask(
    trace: ArrayBuffer[String],
    attempts: AtomicInteger,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      val current = attempts.incrementAndGet()
      if (current == 1)
        TaskFailed(_failure(Disposition.UserAction.RetryLater, "priority-retry"))
      else {
        trace.synchronized {
          trace += "retry"
        }
        TaskSucceeded(OperationResponse.Scalar("retry"))
      }
    }
  }
}
