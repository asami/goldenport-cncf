package org.goldenport.cncf.job

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobManagementDetailSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-management-detail-contract, example:E1, rules:R1,R2,R3,R4, phase:69.2"
  )

  "E1 Job management detail" must _e1 {
    "return bounded live exact detail and the existing typed result" in {
      Given("a completed live job with debug input that management detail must not disclose")
      val engine = createJobEngine()
      val context = ExecutionContext.test()
      val jobid = _submit(engine, context, "live-detail", JobSubmitOption(
        requestSummary = Some("must not appear"),
        parameters = Map("debug" -> "must not appear")
      ))

      When("the owner reads every exact management projection")
      given ExecutionContext = context
      val detail = _value(engine.queryManagementDetail(jobid))
      val result = _value(engine.queryManagementResult(jobid))
      val tasks = _value(engine.queryManagementTasks(jobid, limit = 1))
      val timeline = _value(engine.queryManagementTimeline(jobid, limit = 1))
      val tree = _value(engine.queryManagementTaskExecutionTree(jobid))
      val taskdetail = tasks.flatMap(_.tasks.headOption).flatMap(task =>
        _value(engine.queryManagementTaskDetail(jobid, task.taskId))
      )

      Then("the facade exposes only its summary detail and preserves the live JobResult")
      detail.map(_.summary.jobId) shouldBe Some(jobid)
      detail.map(_.taskCount) shouldBe Some(1)
      detail.map(_.timelineCount).get should be > 0
      detail.map(_.productElementNames.toSet) shouldBe Some(Set(
        "summary", "retry", "resultSummary", "taskCount", "timelineCount"
      ))
      result shouldBe Some(JobManagementResult.Available(engine.getResult(jobid).get))
      tasks.map(_.fetchedCount) shouldBe Some(1)
      timeline.map(_.fetchedCount) shouldBe Some(1)
      tree.map(_.jobId) shouldBe Some(jobid)
      taskdetail.map(_.jobId) shouldBe Some(jobid)
    }

    "return the closed pending outcome before a live result exists" in {
      Given("a manually scheduled live job which has not started")
      val fixture = createManualJobEngine()
      val context = ExecutionContext.test()
      val task = ActionTask(ActionId.generate(), _success_action("pending"), ActionEngine.create(), None)
      val jobid = fixture.engine.submit(List(task), context, JobSubmitOption(
        runMode = JobRunMode.Async,
        scheduledStartAt = Some(Instant.parse("2026-05-04T00:01:00Z"))
      )).toOption.get

      When("the owner requests its exact management result")
      given ExecutionContext = context
      val result = _value(fixture.engine.queryManagementResult(jobid))

      Then("the existing result summary is returned as pending without constructing a JobResult")
      result shouldBe Some(JobManagementResult.Pending(
        JobResultSummary(JobStatus.Submitted, success = false, message = Some("running"))
      ))
      fixture.engine.getResult(jobid) shouldBe None
    }

    "make denied and missing exact reads indistinguishable" in {
      Given("one existing job and a policy that denies its found model")
      val engine = createJobEngine()
      val context = ExecutionContext.test()
      val jobid = _submit(engine, context, "denied")
      val missing = JobId.generate()
      val denied = new JobQueryPolicy {
        def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit] = {
          val _ = model
          Consequence.operationIllegal("job.management-detail", "denied for executable specification")
        }
      }
      val taskid = engine.queryTasks(jobid).flatMap(_.tasks.headOption).map(_.taskId).get

      When("the denied and missing identities are read through every management facade")
      given ExecutionContext = context
      val deniedresults = Vector(
        engine.queryManagementDetail(jobid, denied),
        engine.queryManagementResult(jobid, denied),
        engine.queryManagementTasks(jobid, policy = denied),
        engine.queryManagementTimeline(jobid, policy = denied),
        engine.queryManagementTaskExecutionTree(jobid, denied),
        engine.queryManagementTaskDetail(jobid, taskid, denied)
      )
      val missingresults = Vector(
        engine.queryManagementDetail(missing, denied),
        engine.queryManagementResult(missing, denied),
        engine.queryManagementTasks(missing, policy = denied),
        engine.queryManagementTimeline(missing, policy = denied),
        engine.queryManagementTaskExecutionTree(missing, denied),
        engine.queryManagementTaskDetail(missing, taskid, denied)
      )

      Then("both identities return the same successful empty outcome without leaking existence")
      deniedresults.map(_.toOption) shouldBe Vector.fill(6)(Some(None))
      missingresults.map(_.toOption) shouldBe Vector.fill(6)(Some(None))
    }

    "reject invalid task and timeline pages before authorization observation" in {
      Given("an existing job and a policy which records authorization attempts")
      val engine = createJobEngine()
      val context = ExecutionContext.test()
      val jobid = _submit(engine, context, "bounds")
      final class CountingJobQueryPolicy extends JobQueryPolicy {
        var calls: Int = 0
        def authorizeRead(model: JobQueryReadModel)(using ExecutionContext): Consequence[Unit] = {
          val _ = model
          calls += 1
          Consequence.unit
        }
      }
      val policy = new CountingJobQueryPolicy

      When("negative offsets and out-of-range limits are requested")
      given ExecutionContext = context
      val negative = engine.queryManagementTasks(jobid, offset = -1, policy = policy)
      val zero = engine.queryManagementTimeline(jobid, limit = 0, policy = policy)
      val over = engine.queryManagementTimeline(
        jobid,
        limit = JobManagementQuery.MaximumLimit + 1,
        policy = policy
      )

      Then("each invalid page fails without locating or authorizing the job")
      negative shouldBe a[Consequence.Failure[_]]
      zero shouldBe a[Consequence.Failure[_]]
      over shouldBe a[Consequence.Failure[_]]
      policy.calls shouldBe 0
    }
  }

  private def _submit(
    engine: JobEngine,
    context: ExecutionContext,
    name: String,
    option: JobSubmitOption = JobSubmitOption()
  ): JobId = {
    val task = ActionTask(ActionId.generate(), _success_action(name), ActionEngine.create(), None)
    engine.submit(List(task), context, option.copy(runMode = JobRunMode.Sync)).toOption.get
  }

  private def _success_action(actionname: String): CommandAction =
    new CommandAction() {
      val request = Request.ofOperation(actionname)
      override def createCall(core: ActionCall.Core): ActionCall = {
        val actionself = this
        val _core = core
        new ActionCall {
          override val core: ActionCall.Core = _core
          override def action: Action = actionself
          def execute(): Consequence[OperationResponse] =
            Consequence.success(OperationResponse.Scalar(actionname))
        }
      }
    }

  private def _value[A](result: Consequence[Option[A]]): Option[A] =
    result.toOption.flatten
}
