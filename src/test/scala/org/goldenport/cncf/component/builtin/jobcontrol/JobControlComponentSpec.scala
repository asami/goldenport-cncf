package org.goldenport.cncf.component.builtin.jobcontrol

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, ActionTask, JobPersistencePolicy, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded, TaskFailed}
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.goldenport.provisional.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 21, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponentSpec extends AnyWordSpec with Matchers {
  "JobControlComponent" should {
    "expose event-triggered lineage and policy source on job inspection surfaces" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
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
            "saga.id" -> "saga-1",
            "cncf.context.causationId" -> "cause-1",
            "cncf.source.subsystem" -> "crm",
            "cncf.source.component" -> "publisher",
            "cncf.target.subsystem" -> subsystem.name,
            "cncf.target.component" -> "public-notice",
            "reception.rule" -> "person-created-sync",
            "reception.policy" -> "async:new-job:same-saga:new-transaction",
            "reception.policySource" -> "compatibility-mapping",
            "reception.jobRelation" -> "newjob",
            "reception.taskRelation" -> "separate-task",
            "reception.transactionRelation" -> "new-transaction",
            "saga.relation" -> "same-saga",
            "failure.policy" -> "retry"
          ),
          executionNotes = Vector("event reception policy source: compatibility-mapping")
        )
      ).toOption.get
      given ExecutionContext = ExecutionContext.test()

      service.getJobStatus(jobId) match {
        case Consequence.Success(model) =>
          model.lineage.eventName shouldBe Some("person.created")
          model.lineage.sagaId shouldBe Some("saga-1")
          model.lineage.sourceSubsystem shouldBe Some("crm")
          model.lineage.targetComponent shouldBe Some("public-notice")
          model.lineage.receptionRule shouldBe Some("person-created-sync")
          model.lineage.policySource shouldBe Some("compatibility-mapping")
          model.lineage.taskRelation shouldBe Some("separate-task")
          model.lineage.transactionRelation shouldBe Some("new-transaction")
          model.lineage.failurePolicy shouldBe Some("retry")
          model.lineage.failureDisposition.print shouldBe "not-applicable"
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
      }
    }

    "expose retry and recovery visibility on job inspection surfaces" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
      val admin = subsystem.components.find(_.name == "admin").get
      val jobControl = subsystem.components.find(_.name == "job_control").get
      val service = jobControl.port.get[JobControlComponent.JobService].get
      val task = _FailureTask(
        ActionId.generate(),
        Conclusion.simple("retry-now").copy(disposition = Disposition(Disposition.UserAction.RetryNow))
      )
      val jobId = admin.logic.submitJob(
        List(task),
        ExecutionContext.create(),
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          requestSummary = Some("ops-01-retry-visibility")
        )
      ).toOption.get
      given ExecutionContext = ExecutionContext.test()

      _await_status(service, jobId)

      service.getJobStatus(jobId) match {
        case Consequence.Success(model) =>
          model.retry.kind.print shouldBe "now"
          model.retry.attemptCount shouldBe 3
          model.retry.exhausted shouldBe true
          model.retry.deadLetter shouldBe true
          model.retry.recoveryRequired shouldBe true
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
      }
    }

    "expose scheduled start visibility on job inspection surfaces" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
      val admin = subsystem.components.find(_.name == "admin").get
      val jobControl = subsystem.components.find(_.name == "job_control").get
      val service = jobControl.port.get[JobControlComponent.JobService].get
      val scheduledAt = Instant.now().plusMillis(150L)
      val jobId = admin.logic.submitJob(
        List(_ImmediateTask(ActionId.generate())),
        ExecutionContext.create(),
        JobSubmitOption(
          persistence = JobPersistencePolicy.Persistent,
          scheduledStartAt = Some(scheduledAt),
          requestSummary = Some("tm-02-delayed-start")
        )
      ).toOption.get
      given ExecutionContext = ExecutionContext.test()

      service.getJobStatus(jobId) match {
        case Consequence.Success(model) =>
          model.scheduledStartAt shouldBe Some(scheduledAt)
        case Consequence.Failure(conclusion) =>
          fail(conclusion.show)
      }
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

  private final case class _FailureTask(
    actionId: ActionId,
    conclusion: Conclusion
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskFailed(conclusion)
    }
  }

  private def _await_status(
    service: JobControlComponent.JobService,
    jobId: org.goldenport.cncf.job.JobId,
    timeoutMillis: Long = 3000L
  )(using ExecutionContext): Unit = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var done = false
    while (!done && System.currentTimeMillis() < deadline) {
      service.getJobStatus(jobId) match {
        case Consequence.Success(model) if Set("Succeeded", "Failed", "Cancelled").contains(model.status.toString) =>
          done = true
        case _ =>
          Thread.sleep(10L)
      }
    }
    done shouldBe true
  }
}
