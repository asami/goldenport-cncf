package org.goldenport.cncf.job

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.provisional.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 22, 2026
 * @version Apr. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobRetryOperationalSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  "OPS-01 retry scheduler" should {
    "retry immediately when userAction is RetryNow and succeed on a later attempt" in {
      Given("a job that fails twice with RetryNow and then succeeds")
      val engine = new InMemoryJobEngine(
        retrySchedule = InMemoryJobEngine.RetrySchedule(
          delayedRetryDelays = Vector(Duration.ofMillis(25L), Duration.ofMillis(50L), Duration.ofMillis(75L))
        )
      )(scala.concurrent.ExecutionContext.global)
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryNow, "retry-now"),
        attempts = attempts,
        succeedAt = 3
      )

      When("the job is submitted")
      val jobId = engine.submit(List(task), ExecutionContext.test())
      val model = _await_query(engine, jobId)

      Then("the job succeeds after automatic retries and records retry attempts")
      model.status shouldBe JobStatus.Succeeded
      model.retry.kind shouldBe JobRetryKind.Immediate
      model.retry.attemptCount shouldBe 2
      attempts.get() shouldBe 3
      engine.shutdown()
    }

    "schedule delayed retries and resume them after engine restart" in {
      Given("a persistent job that requests RetryLater")
      val state = InMemoryJobEngine.State()
      val schedule = InMemoryJobEngine.RetrySchedule(
        delayedRetryDelays = Vector(Duration.ofMillis(60L), Duration.ofMillis(90L), Duration.ofMillis(120L))
      )
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later"),
        attempts = attempts,
        succeedAt = 2
      )
      val engine1 = new InMemoryJobEngine(state, schedule)(scala.concurrent.ExecutionContext.global)
      val jobId = engine1.submit(List(task), ExecutionContext.test())
      val queued = _await_condition {
        engine1.query(jobId).exists(_.retry.nextRetryDueAt.nonEmpty)
      }
      queued shouldBe true

      When("the engine is restarted before the delayed retry fires")
      engine1.shutdown()
      val engine2 = new InMemoryJobEngine(state, schedule)(scala.concurrent.ExecutionContext.global)
      val model = _await_query(engine2, jobId)

      Then("the delayed retry is resumed from persisted state and the job succeeds")
      model.status shouldBe JobStatus.Succeeded
      model.retry.kind shouldBe JobRetryKind.Delayed
      model.retry.attemptCount shouldBe 1
      attempts.get() shouldBe 2
      engine2.shutdown()
    }

    "dead-letter exhausted RetryLater jobs after three retries" in {
      Given("a delayed-retry job that always fails")
      val engine = new InMemoryJobEngine(
        retrySchedule = InMemoryJobEngine.RetrySchedule(
          delayedRetryDelays = Vector(Duration.ofMillis(20L), Duration.ofMillis(30L), Duration.ofMillis(40L))
        )
      )(scala.concurrent.ExecutionContext.global)
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = _failure(Disposition.UserAction.RetryLater, "retry-later-exhausted"),
        attempts = attempts,
        succeedAt = Int.MaxValue
      )

      When("the retries are exhausted")
      val jobId = engine.submit(List(task), ExecutionContext.test())
      val model = _await_query(engine, jobId, statuses = Set(JobStatus.Failed))

      Then("the final state is dead-lettered with recovery visibility")
      model.status shouldBe JobStatus.Failed
      model.retry.kind shouldBe JobRetryKind.Delayed
      model.retry.attemptCount shouldBe 3
      model.retry.exhausted shouldBe true
      model.retry.deadLetter shouldBe true
      model.retry.poison shouldBe false
      model.retry.recoveryRequired shouldBe true
      attempts.get() shouldBe 4
      engine.shutdown()
    }

    "fail immediately as poison when retry is not requested" in {
      Given("a job that fails without retry-worthiness")
      val engine = InMemoryJobEngine.create()
      val attempts = new AtomicInteger(0)
      val task = _CountingTask(
        conclusion = Conclusion.simple("fix-input"),
        attempts = attempts,
        succeedAt = Int.MaxValue
      )

      When("the job fails")
      val jobId = engine.submit(List(task), ExecutionContext.test())
      val model = _await_query(engine, jobId, statuses = Set(JobStatus.Failed))

      Then("it is marked as poison without automatic retry")
      model.retry.kind shouldBe JobRetryKind.None
      model.retry.attemptCount shouldBe 0
      model.retry.deadLetter shouldBe false
      model.retry.poison shouldBe true
      attempts.get() shouldBe 1
      engine.shutdown()
    }
  }

  private def _failure(
    action: Disposition.UserAction,
    message: String
  ): Conclusion =
    Conclusion.simple(message).copy(disposition = Disposition(action))

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

  private def _await_query(
    engine: JobEngine,
    jobId: JobId,
    statuses: Set[JobStatus] = Set(JobStatus.Succeeded, JobStatus.Failed)
  ): JobQueryReadModel = {
    val deadline = System.currentTimeMillis() + 4000L
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
    timeoutMillis: Long = 1000L
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
