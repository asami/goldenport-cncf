package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.provisional.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlCommandSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {

  "Job control commands" should {
    "return async acknowledgment by default" in {
      Given("a running job and content-manager privilege")
      val engine = createJobEngine()
      val task = ActionTask(ActionId.generate(), _sleep_action("slow", 300L, "ok"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      awaitStatus(engine, jobid, Set(JobStatus.Running, JobStatus.Succeeded))

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("suspend is requested with async default")
      val result = engine.control(jobid, JobControlRequest(JobControlCommand.Suspend))

      Then("jobId acknowledgment is returned without sync payload")
      result shouldBe a[Consequence.Success[_]]
      val ack = result.toOption.get
      ack.jobId shouldBe jobid
      ack.async shouldBe true
      ack.response shouldBe None
    }

    "reject invalid state transitions deterministically" in {
      Given("a succeeded job")
      val engine = createJobEngine()
      val task = ActionTask(ActionId.generate(), _success_action("done", "ok"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      awaitStatus(engine, jobid, Set(JobStatus.Succeeded))

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("resume is requested from succeeded")
      val result = engine.control(jobid, JobControlRequest(JobControlCommand.Resume))

      Then("invalid transition failure is returned")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Invalid
        case _ =>
          fail("expected failure")
      }
    }

    "map sync timeout deterministically for retry" in {
      Given("a cancelled long-running job")
      val engine = createJobEngine()
      val task = ActionTask(ActionId.generate(), _sleep_action("slow", 800L, "ok"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      awaitStatus(engine, jobid, Set(JobStatus.Running, JobStatus.Succeeded))

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      val _ = engine.control(jobid, JobControlRequest(JobControlCommand.Cancel))
      awaitStatus(engine, jobid, Set(JobStatus.Cancelled))

      When("retry is requested in sync mode with short timeout")
      val result = engine.control(
        jobid,
        JobControlRequest(
          command = JobControlCommand.Retry,
          option = JobControlOption(mode = JobCommandMode.Sync, timeoutMillis = 1L, pollMillis = 1L)
        )
      )

      Then("timeout failure is returned with deterministic taxonomy")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Unavailable
        case _ =>
          fail("expected timeout failure")
      }
    }

    "deny control command for user privilege" in {
      Given("a job and user privilege")
      val engine = createJobEngine()
      val task = ActionTask(ActionId.generate(), _success_action("cmd", "ok"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)

      When("cancel is requested")
      val result = engine.control(jobid, JobControlRequest(JobControlCommand.Cancel))

      Then("policy denial is returned")
      result shouldBe a[Consequence.Failure[_]]
      result match {
        case Consequence.Failure(c) =>
          c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
          c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Illegal
        case _ =>
          fail("expected failure")
      }
    }
  }

  private def _jobid(p: Consequence[JobId]): JobId =
    p.toOption.get

  private def _success_action(actionname: String, value: String): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(actionname)
      override def createCall(core: ActionCall.Core): ActionCall = {
        val actionself = this
        val _core = core
        new ActionCall {
          override val core: ActionCall.Core = _core
          override def action: Action = actionself
          def execute(): Consequence[OperationResponse] =
            Consequence.success(OperationResponse.Scalar(value))
        }
      }
    }

  private def _sleep_action(actionname: String, millis: Long, value: String): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(actionname)
      override def createCall(core: ActionCall.Core): ActionCall = {
        val actionself = this
        val _core = core
        new ActionCall {
          override val core: ActionCall.Core = _core
          override def action: Action = actionself
          def execute(): Consequence[OperationResponse] = {
            Thread.sleep(millis)
            Consequence.success(OperationResponse.Scalar(value))
          }
        }
      }
    }

}
