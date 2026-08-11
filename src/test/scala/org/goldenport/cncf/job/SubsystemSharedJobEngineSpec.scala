package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 *  version Apr. 22, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemSharedJobEngineSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  "Subsystem shared JobEngine" should {
    "share one JobEngine across administrative components" in {
      Given("a subsystem with command support")
      SubsystemTestFixture.withAdmittedSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent("admin").get
        val jobcontrol = subsystem.findComponent("job_control").get
        val submitctx = ExecutionContext.create()
        val controlctx = ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager)
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)

        When("admin submits an asynchronous Job and job-control cancels it")
        admin.jobEngine eq jobcontrol.jobEngine shouldBe true
        val jobid = admin.logic.submitJob(
          List(BlockingTask(ActionId.generate(), entered, release)),
          submitctx,
          JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("shared-job-engine"))
        ).toOption.get

        entered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true
        try {
          jobcontrol.logic.controlJob(jobid, JobControlRequest(JobControlCommand.Cancel))(using controlctx).TAKE
          release.countDown()

          Then("the shared JobEngine records the cancellation after the admitted task settles")
          awaitStatus(jobcontrol.jobEngine, jobid, Set(JobStatus.Cancelled)) shouldBe Some(JobStatus.Cancelled)
        } finally {
          release.countDown()
        }
      }
    }

    "suspend a running Job through the subsystem operation" in {
      Given("a submitted Job whose task has entered and remains controlled by the specification")
      SubsystemTestFixture.withAdmittedSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent("admin").get
        val jobcontrol = subsystem.findComponent("job_control").get
        val submitctx = ExecutionContext.test()
        val entered = new CountDownLatch(1)
        val release = new CountDownLatch(1)

        admin.jobEngine eq jobcontrol.jobEngine shouldBe true
        val jobid = admin.logic.submitJob(
          List(BlockingTask(ActionId.generate(), entered, release)),
          submitctx,
          JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("subsystem-execute-job-control"))
        ).toOption.get

        entered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true

        When("the job-control suspend operation is executed")
        val request = Request.of(
          component = "job_control",
          service = "job_admin",
          operation = "suspend_job",
          arguments = List(Argument("id", jobid.value)),
          properties = List(
            org.goldenport.protocol.Property("cncf.security.privilege", "content_admin", None)
          )
        )

        try {
          val result = subsystem.execute(request)

          Then("the shared JobEngine records the suspended state before the task may settle")
          result shouldBe a[Consequence.Success[_]]
          awaitStatus(jobcontrol.jobEngine, jobid, Set(JobStatus.Suspended)) shouldBe Some(JobStatus.Suspended)
        } finally {
          release.countDown()
        }
      }
    }
  }

  private final case class BlockingTask(
    actionId: ActionId,
    entered: CountDownLatch,
    release: CountDownLatch
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      entered.countDown()
      release.await()
      TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Void())
    }
  }
}
