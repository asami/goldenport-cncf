package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 21, 2026
 *  version Apr. 22, 2026
 * @version May.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobQueryReadModelSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  "Job query read model" should {
    "return sync-equivalent result payload by JobId" in {
      Given("a command task submitted as a persistent job")
      val engine = createJobEngine()
      val action = _success_action("createPerson", "ok-1")
      val task = ActionTask(ActionId.generate(), action, ActionEngine.create(), None)
      val ctx = ExecutionContext.test()

      When("job completes")
      val jobid = _jobid(engine.submit(List(task), ctx))
      val result = awaitResult(engine, jobid)
      val read = awaitQuery(engine, jobid)

      Then("read model and getResponse expose the same OperationResponse shape")
      result shouldBe a[Some[_]]
      read shouldBe a[Some[_]]
      val r1 = engine.getResponse(jobid)
      val r2 = read.flatMap(_.result)
      r1.map(_.toResponse) shouldBe r2.map(_.toResponse)
    }

    "project deterministic task/timeline order with pagination" in {
      Given("a job with two sequential tasks")
      val engine = createJobEngine()
      val task1 = ActionTask(ActionId.generate(), _success_action("first", "1"), ActionEngine.create(), None)
      val task2 = ActionTask(ActionId.generate(), _success_action("second", "2"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(List(task1, task2), ExecutionContext.test()))
      val _ = awaitResult(engine, jobid)

      When("querying tasks/timeline with same pagination twice")
      val p1a = engine.queryTasks(jobid, offset = 0, limit = 1).get
      val p1b = engine.queryTasks(jobid, offset = 0, limit = 1).get
      val t1a = engine.queryTimeline(jobid, offset = 1, limit = 2).get
      val t1b = engine.queryTimeline(jobid, offset = 1, limit = 2).get

      Then("output order and slice are deterministic")
      p1a shouldBe p1b
      t1a shouldBe t1b
      p1a.fetchedCount shouldBe 1
      t1a.events.map(_.sequence) shouldBe t1a.events.map(_.sequence).sorted
    }

    "expose persistent vs ephemeral origin explicitly" in {
      Given("one persistent job and one ephemeral job")
      val engine = createJobEngine()
      val persistentId = _jobid(engine.submit(
        List(ActionTask(ActionId.generate(), _success_action("persist", "ok"), ActionEngine.create(), None)),
        ExecutionContext.test()
      ))
      val ephemeralId = _jobid(engine.submit(
        List(ActionTask(ActionId.generate(), _success_action("ephemeral", "ok"), ActionEngine.create(), None)),
        ExecutionContext.test(),
        JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)
      ))

      val _ = awaitResult(engine, persistentId)
      val _ = awaitResult(engine, ephemeralId)

      When("querying read models")
      val persistentRead = engine.query(persistentId).get
      val ephemeralRead = engine.query(ephemeralId).get

      Then("persistence policy and data origin are explicit")
      persistentRead.persistence shouldBe JobPersistencePolicy.Persistent
      persistentRead.origin shouldBe JobDataOrigin.Durable
      ephemeralRead.persistence shouldBe JobPersistencePolicy.Ephemeral
      ephemeralRead.origin shouldBe JobDataOrigin.Runtime
    }

    "include task structure and debug/trace baseline fields" in {
      Given("a job with explicit submit debug metadata")
      val engine = createJobEngine()
      val action = _success_action("debugAction", "done")
      val task = ActionTask(ActionId.generate(), action, ActionEngine.create(), None)
      val option = JobSubmitOption(
        persistence = JobPersistencePolicy.Persistent,
        requestSummary = Some("request-summary"),
        parameters = Map("p1" -> "v1"),
        executionNotes = Vector("note-1")
      )
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test(), option))
      val _ = awaitResult(engine, jobid)

      When("querying read model")
      val read = engine.query(jobid).get

      Then("task fields and debug/trace fields are present")
      read.tasks.totalCount should be >= 1
      read.tasks.tasks.head.taskId should not be null
      read.tasks.tasks.head.startedAt should not be null
      read.tasks.tasks.head.finishedAt should not be empty
      read.debug.requestSummary shouldBe Some("request-summary")
      read.debug.parameters.get("p1") shouldBe Some("v1")
      read.debug.executionNotes should contain("note-1")
      read.traceTree.roots.nonEmpty shouldBe true
      read.submitter.principalId shouldBe "test-user-principal"
    }

    "expose scheduled start time for delayed job submission" in {
      Given("a delayed async job submission")
      val engine = createJobEngine()
      val scheduledAt = Instant.now().plusMillis(120L)
      val task = ActionTask(ActionId.generate(), _success_action("delayedRead", "done"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(
        List(task),
        ExecutionContext.test(),
        JobSubmitOption(scheduledStartAt = Some(scheduledAt))
      ))

      When("querying the read model before completion")
      val read = engine.query(jobid).get

      Then("the scheduled start time is projected explicitly")
      read.scheduledStartAt shouldBe Some(scheduledAt)
      awaitResult(engine, jobid)
    }

    "expose event-triggered lineage explicitly in the read model" in {
      Given("an async job submitted from event reception metadata")
      val engine = createJobEngine()
      val parentJobId = JobId.generate().print
      val task = ActionTask(ActionId.generate(), _success_action("dispatch", "done"), ActionEngine.create(), None)
      val option = JobSubmitOption(
        persistence = JobPersistencePolicy.Ephemeral,
        requestSummary = Some("event.continuation:person.created"),
        parameters = Map(
          "event.name" -> "person.created",
          "event.kind" -> "created",
          "cncf.context.jobId" -> parentJobId,
          "cncf.context.correlationId" -> "corr-1",
          "saga.id" -> "saga-1",
          "cncf.context.causationId" -> "cause-1",
          "cncf.source.subsystem" -> "crm",
          "cncf.source.component" -> "publisher",
          "cncf.target.subsystem" -> "sample",
          "cncf.target.component" -> "public-notice",
          "reception.rule" -> "person-created-sync",
          "reception.policy" -> "async:new-job:same-saga:new-transaction",
          "reception.policySource" -> "explicit-rule",
          "reception.jobRelation" -> "newjob",
          "saga.relation" -> "same-saga",
          "failure.policy" -> "retry"
        ),
        executionNotes = Vector("event reception policy source: explicit-rule")
      )

      When("querying the completed job")
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test(), option))
      val _ = awaitResult(engine, jobid)
      val read = engine.query(jobid).get

      Then("lineage fields are materialized as structured metadata")
      read.lineage.eventTriggered shouldBe true
      read.lineage.eventName shouldBe Some("person.created")
      read.lineage.parentJobId shouldBe Some(parentJobId)
      read.lineage.sagaId shouldBe Some("saga-1")
      read.lineage.sourceSubsystem shouldBe Some("crm")
      read.lineage.sourceComponent shouldBe Some("publisher")
      read.lineage.targetComponent shouldBe Some("public-notice")
      read.lineage.receptionRule shouldBe Some("person-created-sync")
      read.lineage.receptionPolicy shouldBe Some("async:new-job:same-saga:new-transaction")
      read.lineage.policySource shouldBe Some("explicit-rule")
      read.lineage.sagaRelation shouldBe Some("same-saga")
      read.lineage.failurePolicy shouldBe Some("retry")
      read.lineage.failureDisposition shouldBe AsyncFailureDisposition.NotApplicable
    }

    "project retryable async failure disposition for failed child jobs" in {
      Given("an event-triggered async child job with retry policy")
      val engine = createJobEngine()
      val task = _failed_task("dispatchRetry")
      val option = JobSubmitOption(
        persistence = JobPersistencePolicy.Ephemeral,
        requestSummary = Some("event.continuation:person.created"),
        parameters = Map(
          "event.name" -> "person.created",
          "reception.jobRelation" -> "newjob",
          "failure.policy" -> "retry"
        )
      )

      When("the job fails")
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test(), option))
      val _ = awaitResult(engine, jobid)
      val read = engine.query(jobid).get

      Then("the read model classifies it as retryable")
      read.status shouldBe JobStatus.Failed
      read.lineage.failureDisposition shouldBe AsyncFailureDisposition.Retryable
    }

    "project terminal async failure disposition for failed child jobs" in {
      Given("an event-triggered async child job with fail policy")
      val engine = createJobEngine()
      val task = _failed_task("dispatchFail")
      val option = JobSubmitOption(
        persistence = JobPersistencePolicy.Ephemeral,
        requestSummary = Some("event.continuation:person.created"),
        parameters = Map(
          "event.name" -> "person.created",
          "reception.jobRelation" -> "newjob",
          "failure.policy" -> "fail"
        )
      )

      When("the job fails")
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test(), option))
      val _ = awaitResult(engine, jobid)
      val read = engine.query(jobid).get

      Then("the read model classifies it as terminal")
      read.status shouldBe JobStatus.Failed
      read.lineage.failureDisposition shouldBe AsyncFailureDisposition.Terminal
    }

    "enforce policy visibility on query surfaces" in {
      Given("a completed job")
      val engine = createJobEngine()
      val task = ActionTask(ActionId.generate(), _success_action("visible", "ok"), ActionEngine.create(), None)
      val jobid = _jobid(engine.submit(List(task), ExecutionContext.test()))
      val _ = awaitResult(engine, jobid)

      When("queryVisible is called as the submitting user")
      val ownerAllowed = {
        given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
        engine.queryVisible(jobid)
      }

      Then("the owner can read query surface")
      ownerAllowed shouldBe a[Consequence.Success[_]]
      ownerAllowed.toOption.flatten.map(_.jobId) shouldBe Some(jobid)

      When("queryVisible is called as anonymous")
      val denied = {
        given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.Anonymous)
        engine.queryVisible(jobid)
      }

      Then("a different subject is denied")
      denied shouldBe a[Consequence.Failure[_]]

      When("queryVisible is called as content manager")
      val allowed = {
        given ExecutionContext =
          ExecutionContext.test(SecurityContext.Privilege.ApplicationContentManager)
        engine.queryVisible(jobid)
      }

      Then("content manager can read query surface")
      allowed shouldBe a[Consequence.Success[_]]
      allowed.toOption.flatten.map(_.jobId) shouldBe Some(jobid)
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

  private def _failed_task(actionname: String): JobTask =
    new JobTask {
      val actionId: ActionId = ActionId.generate()
      def run(ctx: ExecutionContext): TaskOutcome = {
        val _ = ctx
        TaskFailed(_operation_invalid(actionname))
      }
    }

  private def _operation_invalid(name: String): Conclusion =
    Consequence.operationInvalid(name, "forced failure") match {
      case Consequence.Failure(conclusion) => conclusion
    }

}
