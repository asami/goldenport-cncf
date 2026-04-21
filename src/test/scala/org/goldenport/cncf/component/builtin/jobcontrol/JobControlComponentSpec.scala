package org.goldenport.cncf.component.builtin.jobcontrol

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 21, 2026
 * @version Apr. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponentSpec extends AnyWordSpec with Matchers {
  "JobControlComponent" should {
    "expose event-triggered lineage and policy source on job inspection surfaces" in {
      val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
      val admin = subsystem.components.find(_.name == "admin").get
      val jobControl = subsystem.components.find(_.name == "job_control").get
      val service = jobControl.port.get[JobControlComponent.JobService].get
      val task = _ImmediateTask(ActionId.generate())
      val jobId = admin.logic.submitJob(
        List(task),
        ExecutionContext.create(),
        JobSubmitOption(
          persistence = JobPersistencePolicy.Ephemeral,
          requestSummary = Some("event.continuation:person.created"),
          parameters = Map(
            "event.name" -> "person.created",
            "event.kind" -> "created",
            "cncf.context.jobId" -> "cncf.job.parent.20260421.000000.000000000000",
            "cncf.context.correlationId" -> "corr-1",
            "cncf.context.causationId" -> "cause-1",
            "cncf.source.subsystem" -> "crm",
            "cncf.source.component" -> "publisher",
            "cncf.target.subsystem" -> subsystem.name,
            "cncf.target.component" -> "public-notice",
            "reception.rule" -> "person-created-sync",
            "reception.policy" -> "async:new-job:same-saga:new-transaction",
            "reception.policySource" -> "compatibility-mapping",
            "reception.jobRelation" -> "newjob",
            "saga.relation" -> "same-saga",
            "failure.policy" -> "retry"
          ),
          executionNotes = Vector("event reception policy source: compatibility-mapping")
        )
      )
      given ExecutionContext = ExecutionContext.test()

      service.getJobStatus(jobId) match {
        case Consequence.Success(model) =>
          model.lineage.eventName shouldBe Some("person.created")
          model.lineage.sourceSubsystem shouldBe Some("crm")
          model.lineage.targetComponent shouldBe Some("public-notice")
          model.lineage.receptionRule shouldBe Some("person-created-sync")
          model.lineage.policySource shouldBe Some("compatibility-mapping")
          model.lineage.failurePolicy shouldBe Some("retry")
          model.lineage.failureDisposition.print shouldBe "not-applicable"
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
    }
  }

  private final case class _ImmediateTask(
    actionId: ActionId
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskSucceeded(OperationResponse.Scalar("ok"))
    }
  }
}
