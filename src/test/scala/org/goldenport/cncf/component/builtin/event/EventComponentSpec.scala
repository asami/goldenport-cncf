package org.goldenport.cncf.component.builtin.event

import org.goldenport.Consequence
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, ActionTask, JobId, JobRunMode, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.{Argument, Request, Response}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 28, 2026
 * @version Mar. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventComponentSpec extends AnyWordSpec with Matchers {
  "EventComponent" should {
    "expose event read and job event observation routes" in {
      val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
      val admin = subsystem.components.find(_.name == "admin").get
      val event = subsystem.components.find(_.name == "event").get
      val ctx = ExecutionContext.create()
      val jobId = admin.logic.submitJob(
        List(SleepTask(ActionId.generate(), 10L)),
        ctx,
        JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some("event-component"))
      )

      val searchReq = Request(
        component = Some("event"),
        service = Some("event"),
        operation = "search_event",
        arguments = Nil,
        switches = Nil,
        properties = Nil
      )
      subsystem.execute(searchReq) match {
        case Consequence.Success(res) =>
          val value = res.toString
          value should include ("job.submitted")
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }

      val loadReq = Request(
        component = Some("event"),
        service = Some("event_admin"),
        operation = "load_job_events",
        arguments = List(Argument("id", jobId.value)),
        switches = Nil,
        properties = Nil
      )
      subsystem.execute(loadReq) match {
        case Consequence.Success(res) =>
          val value = res.toString
          value should include ("job.submitted")
          value should include (jobId.value)
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
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
      TaskSucceeded(OperationResponse.Void())
    }
  }
}
