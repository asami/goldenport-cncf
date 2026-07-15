package org.goldenport.cncf.job

import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Conclusion
import org.goldenport.configuration.{
  Configuration,
  ConfigurationTrace,
  ConfigurationValue,
  ResolvedConfiguration
}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{
  ExecutionContext,
  ExecutionProfileResolver,
  GlobalRuntimeContext,
  IdGenerationContext,
  ScopeKind
}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ExecutionProfileJobSchedulingSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  "A controlled execution profile" should {
    "run equal-due Job work deterministically without host sleeping" in {
      Given("one runtime-owned manual clock, scheduler, and JobEngine adapter")
      val start = Instant.parse("2026-07-28T09:00:00Z")
      val configuration = _controlled_configuration
      val base = ExecutionContext.create()
      val global = GlobalRuntimeContext.create(
        "execution-profile-job-scheduling-spec",
        RuntimeConfig.from(configuration),
        configuration,
        base.observability,
        AliasResolver.empty
      )
      val subsystem = Subsystem(
        name = "execution-profile-job-scheduling-spec",
        scopeContext = Some(global.createChildScope(
          ScopeKind.Subsystem,
          "execution-profile-job-scheduling-spec"
        )),
        configuration = configuration,
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command
      )
      val control = global.executionProfileRuntime.testControl.get
      val engine = registerJobEngine(subsystem.jobEngine)
      val order = ArrayBuffer.empty[String]
      val dueat = start.plusSeconds(30L)

      When("two asynchronous Jobs share a due time and logical time reaches it")
      val first = engine.submit(
        List(_RecordingTask("first", order)),
        ExecutionContext.test(),
        JobSubmitOption(runMode = JobRunMode.Async, scheduledStartAt = Some(dueat))
      ).toOption.get
      val second = engine.submit(
        List(_RecordingTask("second", order)),
        ExecutionContext.test(),
        JobSubmitOption(runMode = JobRunMode.Async, scheduledStartAt = Some(dueat))
      ).toOption.get
      control.runUntilIdle() shouldBe 0
      control.advanceBy(Duration.ofSeconds(30L))
      val executed = control.runUntilIdle()

      Then("due timers and Job queue order follow due time then submission sequence")
      executed shouldBe 2
      order.toVector shouldBe Vector("first", "second")
      engine.getStatus(first) shouldBe Some(JobStatus.Succeeded)
      engine.getStatus(second) shouldBe Some(JobStatus.Succeeded)
      engine.query(first).map(_.createdAt) shouldBe Some(start)
      engine.query(second).map(_.updatedAt) shouldBe Some(dueat)
    }

    "make a delayed retry eligible through the same runtime control" in {
      Given("a controlled Job whose first Task attempt requests RetryLater")
      val profile = ExecutionProfileResolver.resolveForSpec(_controlled_configuration).toOption.get
      val runtime = profile.newRuntime(IdGenerationContext.DefaultNamespace)
      val control = runtime.testControl.get
      val engine = registerJobEngine(InMemoryJobEngine.create(runtime))
      val attempts = new AtomicInteger(0)
      val task = _RetryOnceTask(attempts = attempts)

      When("the initial work and default delayed retry are driven without host sleeping")
      val jobid = engine.submit(List(task), ExecutionContext.test()).toOption.get
      control.runUntilIdle() shouldBe 1
      val waiting = engine.query(jobid).get
      control.advanceBy(Duration.ofMinutes(1L))
      val executed = control.runUntilIdle()

      Then("retry due time, timestamps, and execution use the selected operational clock")
      waiting.retry.nextRetryDueAt shouldBe Some(control.now)
      executed shouldBe 1
      attempts.get() shouldBe 2
      engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      engine.query(jobid).map(_.updatedAt) shouldBe Some(control.now)
    }
  }

  private final case class _RecordingTask(
    name: String,
    order: ArrayBuffer[String],
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      order += name
      TaskSucceeded(OperationResponse.Scalar(name))
    }
  }

  private final case class _RetryOnceTask(
    attempts: AtomicInteger,
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      if (attempts.incrementAndGet() == 1)
        TaskFailed(
          Conclusion
            .simple("retry through controlled scheduler")
            .copy(disposition = Disposition(Disposition.UserAction.RetryLater))
        )
      else
        TaskSucceeded(OperationResponse.Scalar("retried"))
    }
  }

  private def _controlled_configuration: ResolvedConfiguration =
    ResolvedConfiguration(
      Configuration(Map(
        RuntimeConfig.OperationModeKey -> ConfigurationValue.StringValue("test"),
        RuntimeConfig.EXECUTION_PROFILE_KEY -> ConfigurationValue.StringValue("controlled"),
        RuntimeConfig.EXECUTION_KEY -> ConfigurationValue.StringValue("job-scheduling-run"),
        RuntimeConfig.EXECUTION_TIME_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_TIME_START_AT_KEY -> ConfigurationValue.StringValue("2026-07-28T09:00:00Z"),
        RuntimeConfig.EXECUTION_RANDOM_MODE_KEY -> ConfigurationValue.StringValue("seeded"),
        RuntimeConfig.EXECUTION_RANDOM_SEED_KEY -> ConfigurationValue.StringValue("job-scheduling-seed"),
        RuntimeConfig.EXECUTION_IDS_MODE_KEY -> ConfigurationValue.StringValue("deterministic"),
        RuntimeConfig.EXECUTION_SCHEDULER_MODE_KEY -> ConfigurationValue.StringValue("manual"),
        RuntimeConfig.EXECUTION_ORDERING_MODE_KEY -> ConfigurationValue.StringValue("deterministic")
      )),
      ConfigurationTrace.empty
    )
}
