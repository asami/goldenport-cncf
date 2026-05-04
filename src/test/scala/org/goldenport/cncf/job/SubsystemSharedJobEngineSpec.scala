package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubsystemSharedJobEngineSpec extends AnyWordSpec with Matchers with Eventually {
  "Subsystem shared JobEngine" should {
    "shared-engine" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.components.find(_.name == "admin").get
        val jobControl = subsystem.components.find(_.name == "job_control").get
        val submitCtx = ExecutionContext.create()
        val controlCtx = ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager)

        admin.jobEngine eq jobControl.jobEngine shouldBe true
        val jobId = admin.logic.submitJob(
          List(SleepTask(ActionId.generate(), 1000L)),
          submitCtx,
          JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("shared-job-engine"))
        ).toOption.get

        eventually {
          jobControl.jobEngine.query(jobId).isDefined shouldBe true
        }

        jobControl.logic.controlJob(jobId, JobControlRequest(JobControlCommand.Cancel))(using controlCtx).TAKE

        eventually {
          jobControl.jobEngine.getStatus(jobId) shouldBe Some(JobStatus.Cancelled)
        }
      }
    }

    "subsystem-execute-job-control" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.components.find(_.name == "admin").get
        val jobControl = subsystem.components.find(_.name == "job_control").get
        val submitCtx = ExecutionContext.test()

        admin.jobEngine eq jobControl.jobEngine shouldBe true
        val jobId = admin.logic.submitJob(
          List(SleepTask(ActionId.generate(), 5000L)),
          submitCtx,
          JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("subsystem-execute-job-control"))
        ).toOption.get

        eventually {
          admin.jobEngine.getStatus(jobId) should (be (Some(JobStatus.Submitted)) or be (Some(JobStatus.Running)))
        }

        val request = Request.of(
          component = "job_control",
          service = "job_admin",
          operation = "suspend_job",
          arguments = List(Argument("id", jobId.value)),
          properties = List(
            org.goldenport.protocol.Property("cncf.security.privilege", "content_admin", None)
          )
        )

        val result = subsystem.execute(request)
        result shouldBe a[Consequence.Success[_]]
        eventually {
          jobControl.jobEngine.getStatus(jobId) shouldBe Some(JobStatus.Suspended)
        }
      }
    }
  }

  private final case class SleepTask(
    actionId: ActionId,
    durationMillis: Long
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      Thread.sleep(durationMillis)
      TaskSucceeded(org.goldenport.protocol.operation.OperationResponse.Void())
    }
  }
}
