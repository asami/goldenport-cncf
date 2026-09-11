package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 11, 2026
 * @version Sep. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobManagementControlSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-management-control-contract, example:E1, rules:R1,R2,R3,R4, phase:69.2"
  )

  "E1 Job management control" must _e1 {
    "authorize before observing an existing or missing job" in {
      Given("a submitted Job, a denied policy, and an unknown JobId")
      val fixture = createManualJobEngine()
      val jobid = _jobid(fixture.engine.submit(Nil, ExecutionContext.test()))
      val missing = JobId.generate()
      given ExecutionContext = ExecutionContext.test()

      When("Cancel is denied for both supplied ids")
      val existing = fixture.engine.control(
        jobid,
        JobControlRequest(JobControlCommand.Cancel),
        _denied_control_policy
      )
      val absent = fixture.engine.control(
        missing,
        JobControlRequest(JobControlCommand.Cancel),
        _denied_control_policy
      )

      Then("both requests share the policy failure and leave the existing Job unchanged")
      _failure_signature(existing) shouldBe _failure_signature(absent)
      fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
      fixture.engine.query(jobid).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 0
    }

    "apply each control command once and make the desired-state replay a no-op" in {
      Given("manual Jobs at the source state for every supported command")
      val cancel = createManualJobEngine()
      val cancelid = _jobid(cancel.engine.submit(Nil, ExecutionContext.test()))
      val suspend = createManualJobEngine()
      val suspendid = _jobid(suspend.engine.submit(Nil, ExecutionContext.test()))
      val resume = createManualJobEngine()
      val resumeid = _jobid(resume.engine.submit(Nil, ExecutionContext.test()))
      val retry = createManualJobEngine()
      val retryid = _jobid(retry.engine.submit(List(FailingTask()), ExecutionContext.test()))
      retry.engine.drainOne() shouldBe true
      retry.engine.getStatus(retryid) shouldBe Some(JobStatus.Failed)
      given ExecutionContext = ExecutionContext.test()

      When("each command is applied and then repeated")
      val cancelled = _success(cancel.engine.control(cancelid, JobControlRequest(JobControlCommand.Cancel), _permissive_control_policy))
      val cancelledreplay = _success(cancel.engine.control(cancelid, JobControlRequest(JobControlCommand.Cancel), _permissive_control_policy))
      val suspended = _success(suspend.engine.control(suspendid, JobControlRequest(JobControlCommand.Suspend), _permissive_control_policy))
      val suspendedreplay = _success(suspend.engine.control(suspendid, JobControlRequest(JobControlCommand.Suspend), _permissive_control_policy))
      _success(resume.engine.control(resumeid, JobControlRequest(JobControlCommand.Suspend), _permissive_control_policy))
      val resumed = _success(resume.engine.control(resumeid, JobControlRequest(JobControlCommand.Resume), _permissive_control_policy))
      val resumedreplay = _success(resume.engine.control(resumeid, JobControlRequest(JobControlCommand.Resume), _permissive_control_policy))
      val retried = _success(retry.engine.control(retryid, JobControlRequest(JobControlCommand.Retry), _permissive_control_policy))
      val retriedreplay = _success(retry.engine.control(
        retryid,
        JobControlRequest(
          JobControlCommand.Retry,
          JobControlOption(mode = JobCommandMode.Sync, timeoutMillis = 0L)
        ),
        _permissive_control_policy
      ))

      Then("the first response changes state or queues work and the replay leaves one control evidence path")
      cancelled.status shouldBe JobStatus.Cancelled
      cancelled.changed shouldBe true
      cancelledreplay.status shouldBe JobStatus.Cancelled
      cancelledreplay.changed shouldBe false
      cancel.engine.query(cancelid).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 1

      suspended.status shouldBe JobStatus.Suspended
      suspended.changed shouldBe true
      suspendedreplay.status shouldBe JobStatus.Suspended
      suspendedreplay.changed shouldBe false
      suspend.engine.query(suspendid).get.timeline.events.count(_.kind == "job.suspended") shouldBe 1

      resumed.status shouldBe JobStatus.Running
      resumed.changed shouldBe true
      resumedreplay.status shouldBe JobStatus.Running
      resumedreplay.changed shouldBe false
      resume.engine.query(resumeid).get.timeline.events.count(_.kind == "job.resumed") shouldBe 1

      retried.status shouldBe JobStatus.Submitted
      retried.changed shouldBe true
      retriedreplay.status shouldBe JobStatus.Submitted
      retriedreplay.async shouldBe false
      retriedreplay.changed shouldBe false
      retry.engine.query(retryid).get.timeline.events.count(_.kind == "job.retry.submitted") shouldBe 1
      retry.engine.drainAll() shouldBe 1
    }
  }

  private val _permissive_control_policy = new JobControlPolicy {
    def authorize(
      jobId: JobId,
      request: JobControlRequest
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.unit
    }
  }

  private val _denied_control_policy = new JobControlPolicy {
    def authorize(
      jobId: JobId,
      request: JobControlRequest
    )(using ExecutionContext): Consequence[Unit] = {
      val _ = jobId
      val _ = request
      Consequence.operationIllegal("job.control", "denied by test policy")
    }
  }

  private def _jobid(result: Consequence[JobId]): JobId =
    result.toOption.get

  private def _success[A](result: Consequence[A]): A =
    result.toOption.get

  private def _failure_signature[A](result: Consequence[A]) =
    result match {
      case Consequence.Failure(conclusion) =>
        (
          conclusion.observation.taxonomy.category,
          conclusion.observation.taxonomy.symptom,
          conclusion.observation.getEffectiveMessage
        )
      case _ => fail("expected failure")
    }

  private final case class FailingTask(
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskFailed(Consequence.stateInvalid[Nothing]("initial failure").conclusion)
    }
  }
}
