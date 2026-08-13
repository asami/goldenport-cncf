package org.goldenport.cncf.component.builtin.jobcontrol

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.Conclusion
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.entity.runtime.{EntityKind, WorkingSetPolicy}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobEngineTestFixture, JobPersistencePolicy, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded, TaskFailed}
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.goldenport.conclusion.Disposition
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 21, 2026
 *  version Apr. 22, 2026
 * @version Aug. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlComponentSpec extends AnyWordSpec with Matchers with JobEngineTestFixture {
  "JobControlComponent" should {
    "expose Job and JobDefinition as system SimpleEntity management descriptors" in {
      val descriptors = JobControlComponent.componentDescriptors.flatMap(_.entityRuntimeDescriptors)
      val job = descriptors.find(_.entityName == "job").get
      val definition = descriptors.find(_.entityName == "jobDefinition").get

      job.entityKind shouldBe EntityKind.System
      job.workingSetPolicy.map(_.label) shouldBe Some("recent(1d on updatedAt)")
      job.collectionId.name shouldBe "job"
      definition.entityKind shouldBe EntityKind.System
      definition.workingSetPolicy.map(_.label) shouldBe Some("active-job-definition")
      definition.collectionId.name shouldBe "jobDefinition"
    }

    "expose event-triggered lineage and policy source on job inspection surfaces" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        val service = jobcontrol.port.get[JobControlComponent.JobService].get
        val task = ImmediateTask(ActionId.generate())
        val jobid = admin.logic.submitJob(
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

        service.getJobStatus(jobid) match {
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
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        val service = jobcontrol.port.get[JobControlComponent.JobService].get
        val task = FailureTask(
          ActionId.generate(),
          Conclusion.simple("retry-now").copy(disposition = Disposition(Disposition.UserAction.RetryNow))
        )
        val jobid = admin.logic.submitJob(
          List(task),
          ExecutionContext.create(),
          JobSubmitOption(
            persistence = JobPersistencePolicy.Persistent,
            requestSummary = Some("ops-01-retry-visibility")
          )
        ).toOption.get
        given ExecutionContext = ExecutionContext.test()

        _await_service_terminal_status(service, jobid)

        service.getJobStatus(jobid) match {
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
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        val service = jobcontrol.port.get[JobControlComponent.JobService].get
        val scheduledat = Instant.now().plusMillis(150L)
        val jobid = admin.logic.submitJob(
          List(ImmediateTask(ActionId.generate())),
          ExecutionContext.create(),
          JobSubmitOption(
            persistence = JobPersistencePolicy.Persistent,
            scheduledStartAt = Some(scheduledat),
            requestSummary = Some("tm-02-delayed-start")
          )
        ).toOption.get
        given ExecutionContext = ExecutionContext.test()

        service.getJobStatus(jobid) match {
          case Consequence.Success(model) =>
            model.scheduledStartAt shouldBe Some(scheduledat)
          case Consequence.Failure(conclusion) =>
            fail(conclusion.show)
        }
      }
    }

    "expose Task Execution Tree and Task detail through job_control operations" in {
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val admin = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.ADMIN).get
        val jobcontrol = subsystem.findComponent(org.goldenport.cncf.component.builtin.BuiltinComponentIdentity.JOB_CONTROL).get
        val service = jobcontrol.port.get[JobControlComponent.JobService].get
        val jobid = admin.logic.submitJob(
          List(ImmediateTask(ActionId.generate())),
          ExecutionContext.create(),
          JobSubmitOption(
            persistence = JobPersistencePolicy.Persistent,
            requestSummary = Some("jm-04-task-tree")
          )
        ).toOption.get
        given ExecutionContext = ExecutionContext.test()

        _await_service_terminal_status(service, jobid)
        val status = service.getJobStatus(jobid).toOption.get
        val taskid = status.tasks.tasks.head.taskId

        service.getTaskExecutionTree(jobid) match {
          case Consequence.Success(tree) =>
            tree.jobId shouldBe jobid
            tree.roots.exists(_.taskId == taskid) shouldBe true
          case Consequence.Failure(conclusion) =>
            fail(conclusion.show)
        }
        service.getTaskDetail(jobid, taskid) match {
          case Consequence.Success(detail) =>
            detail.task.taskId shouldBe taskid
            detail.events.exists(_.kind == "task.succeeded") shouldBe true
          case Consequence.Failure(conclusion) =>
            fail(conclusion.show)
        }
      }
    }
  }

  private final case class ImmediateTask(
    actionId: ActionId
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskSucceeded(OperationResponse.Scalar("ok"))
    }
  }

  private final case class FailureTask(
    actionId: ActionId,
    conclusion: Conclusion
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskFailed(conclusion)
    }
  }

  private def _await_service_terminal_status(
    service: JobControlComponent.JobService,
    jobid: org.goldenport.cncf.job.JobId,
    timeoutmillis: Long = 3000L
  )(using ExecutionContext): Unit = {
    val done = awaitCondition({
      service.getJobStatus(jobid) match {
        case Consequence.Success(model) if Set("Succeeded", "Failed", "Cancelled").contains(model.status.toString) =>
          true
        case _ =>
          false
      }
    }, timeoutMillis = timeoutmillis)
    done shouldBe true
  }
}
