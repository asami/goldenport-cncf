package org.goldenport.cncf.job

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.observation.Taxonomy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Apr. 22, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobControlCommandSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {

  "Job control commands" should {
    "preserve cancellation after an admitted task completes" in {
      Given("a running job whose task is held after admission")
      val engine = createJobEngine()
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val task = new JobTask {
        val actionId: ActionId = ActionId.generate()

        def run(ctx: ExecutionContext): TaskOutcome = {
          val _ = ctx
          entered.countDown()
          release.await()
          TaskSucceeded(OperationResponse.Void())
        }
      }
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      entered.await(3L, TimeUnit.SECONDS) shouldBe true

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("cancellation is requested before the admitted task returns")
      try {
        engine.control(jobid, JobControlRequest(JobControlCommand.Cancel)) shouldBe a[Consequence.Success[_]]
        release.countDown()

        Then("the terminal cancellation is not overwritten by successful task settlement")
        awaitStatus(engine, jobid, Set(JobStatus.Cancelled)) shouldBe Some(JobStatus.Cancelled)
        engine.query(jobid).map(_.status) shouldBe Some(JobStatus.Cancelled)
      } finally {
        release.countDown()
      }
    }

    "return async acknowledgment by default" in {
      Given("a running job and content-manager privilege")
      val engine = createJobEngine()
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val task = BlockingTask(ActionId.generate(), entered, release)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      entered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("suspend is requested with async default")
      try {
        val result = engine.control(jobid, JobControlRequest(JobControlCommand.Suspend))

        Then("jobId acknowledgment is returned without sync payload")
        result shouldBe a[Consequence.Success[_]]
        val ack = result.toOption.get
        ack.jobId shouldBe jobid
        ack.async shouldBe true
        ack.response shouldBe None
      } finally {
        release.countDown()
      }
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
      Given("a failed job whose retry attempt remains in progress")
      val engine = createJobEngine()
      val retryEntered = new CountDownLatch(1)
      val retryRelease = new CountDownLatch(1)
      val task = FailThenBlockTask(ActionId.generate(), retryEntered, retryRelease)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      awaitStatus(engine, jobid, Set(JobStatus.Failed)) shouldBe Some(JobStatus.Failed)

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
      try {
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
        retryEntered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true
        result match {
          case Consequence.Failure(c) =>
            c.observation.taxonomy.category shouldBe Taxonomy.Category.Operation
            c.observation.taxonomy.symptom shouldBe Taxonomy.Symptom.Unavailable
          case _ =>
            fail("expected timeout failure")
        }
      } finally {
        retryRelease.countDown()
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

    "refresh the Job SimpleEntity management record after control transitions" in {
      import JobEntity.given

      Given("a running persistent job and content-manager privilege")
      val engine = createJobEngine()
      val entered = new CountDownLatch(1)
      val release = new CountDownLatch(1)
      val task = BlockingTask(ActionId.generate(), entered, release)
      val submittedctx = createJobEntityContext()
      val jobid = _jobid(engine.submit(List(task), submittedctx))
      entered.await(DefaultAwaitTimeoutMillis, TimeUnit.MILLISECONDS) shouldBe true

      given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)

      When("cancel is requested")
      try {
        val result = engine.control(jobid, JobControlRequest(JobControlCommand.Cancel))
        result shouldBe a[Consequence.Success[_]]
        release.countDown()
        awaitStatus(engine, jobid, Set(JobStatus.Cancelled)) shouldBe Some(JobStatus.Cancelled)

        Then("the Job Entity status is updated")
        val loaded = EntityStore.standard().load[JobEntity](JobEntity.entityId(jobid))(using JobEntity.entityPersistent, submittedctx).toOption.flatten
        loaded.flatMap(_.record.getString("status")) shouldBe Some("Cancelled")
      } finally {
        release.countDown()
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

  private final case class BlockingTask(
    actionId: ActionId,
    entered: CountDownLatch,
    release: CountDownLatch
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      entered.countDown()
      release.await()
      TaskSucceeded(OperationResponse.Void())
    }
  }

  private final case class FailThenBlockTask(
    actionId: ActionId,
    retryEntered: CountDownLatch,
    retryRelease: CountDownLatch
  ) extends JobTask {
    private val _attempt = new AtomicInteger(0)

    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      if (_attempt.getAndIncrement() == 0)
        TaskFailed(Consequence.stateInvalid[Nothing]("initial attempt failed").conclusion)
      else {
        retryEntered.countDown()
        retryRelease.await()
        TaskSucceeded(OperationResponse.Void())
      }
    }
  }

}
