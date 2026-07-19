package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 19, 2026
 * @version Jul. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobPrimaryResultSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with JobEngineTestFixture {
  "Job primary result" should {
    "remain independent from an asynchronous continuation failure" in {
      Given("a synchronous primary Task that queues a failing continuation on a manual scheduler")
      val engine = createJobEngine(
        InMemoryJobEngine.SchedulerConfig(workerCount = 1, autoStartWorkers = false)
      )
      val continuation = _FailingTask("continuation failed")
      val primary = _EnqueueingTask(engine, continuation, "primary")

      When("the primary Task completes before the continuation is drained")
      val jobid = engine.submit(
        List(primary),
        ExecutionContext.test(),
        JobSubmitOption(runMode = JobRunMode.Sync)
      ).toOption.getOrElse(fail("job submission failed"))

      Then("the primary result is available while the final Job result remains open")
      engine.getPrimaryResult(jobid) shouldBe Some(JobResult.Success(OperationResponse.Scalar("primary")))
      engine.getResult(jobid) shouldBe None

      When("the queued continuation fails")
      engine.drainOne() shouldBe true

      Then("the final Job fails without replacing the primary response")
      engine.getResult(jobid) shouldBe a[Some[JobResult.Failure]]
      engine.getPrimaryResult(jobid) shouldBe Some(JobResult.Success(OperationResponse.Scalar("primary")))
    }
  }

  private final case class _EnqueueingTask(
    engine: InMemoryJobEngine,
    continuation: JobTask,
    value: String,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome =
      ctx.jobContext.jobId match {
        case Some(jobid) =>
          engine.enqueueTaskInJob(jobid, continuation, ctx) match {
            case Consequence.Success(_) => TaskSucceeded(OperationResponse.Scalar(value))
            case Consequence.Failure(conclusion) => TaskFailed(conclusion)
          }
        case None =>
          TaskFailed(Consequence.stateInvalid[Nothing]("primary Task has no Job context").conclusion)
      }
  }

  private final case class _FailingTask(
    message: String,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskFailed(Consequence.stateInvalid[Nothing](message).conclusion)
    }
  }
}
