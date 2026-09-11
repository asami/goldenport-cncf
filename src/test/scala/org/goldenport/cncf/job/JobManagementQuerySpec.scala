package org.goldenport.cncf.job

import org.goldenport.Consequence
import org.goldenport.cncf.action.{Action, ActionCall, ActionEngine, CommandAction}
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
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
final class JobManagementQuerySpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen
  with JobEngineTestFixture {
  private val _e1 = afterWord(
    "in spec:durable-job-management-query-contract, example:E1, rules:R1,R2,R3,R4, phase:69.2"
  )

  "E1 Job management query" must _e1 {
    "traverse an authorized snapshot deterministically by page" in {
      Given("three completed persistent jobs")
      val engine = createJobEngine()
      val ctx = ExecutionContext.test()
      val ids = Vector("one", "two", "three").map(_submit(engine, _, ctx))

      When("the caller follows limit-one continuations")
      given ExecutionContext = ctx
      val first = engine.queryPage(JobManagementQuery(limit = 1)).toOption.get
      val second = engine.queryPage(JobManagementQuery(limit = 1, cursor = first.nextCursor)).toOption.get
      val third = engine.queryPage(JobManagementQuery(limit = 1, cursor = second.nextCursor)).toOption.get
      val found = Vector(first, second, third).flatMap(_.entries.map(_.jobId))

      Then("the complete page traversal is ordered, unique, and cursor-complete")
      found.toSet shouldBe ids.toSet
      found shouldBe found.distinct
      first.totalCount shouldBe 3
      third.nextCursor shouldBe None
    }

    "refuse a continuation used with different filters" in {
      Given("two completed successful jobs with a continuation")
      val engine = createJobEngine()
      val ctx = ExecutionContext.test()
      val _ = _submit(engine, "filter-one", ctx)
      val _ = _submit(engine, "filter-two", ctx)

      When("the cursor is replayed with a different status filter")
      given ExecutionContext = ctx
      val first = engine.queryPage(JobManagementQuery(status = Some(JobStatus.Succeeded), limit = 1)).toOption.get
      val result = engine.queryPage(JobManagementQuery(status = Some(JobStatus.Failed), limit = 1, cursor = first.nextCursor))

      Then("the result is classified as an invalid cursor")
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include("invalid cursor")
    }

    "expire a continuation when the authorized source changes" in {
      Given("a first page over two completed persistent jobs")
      val engine = createJobEngine()
      val ctx = ExecutionContext.test()
      val _ = _submit(engine, "before-one", ctx)
      val _ = _submit(engine, "before-two", ctx)
      given ExecutionContext = ctx
      val first = engine.queryPage(JobManagementQuery(limit = 1)).toOption.get
      val firstentries = first.entries.map(_.jobId)
      val firsttotalcount = first.totalCount

      When("another authorized job is added before continuation")
      val _ = _submit(engine, "after", ctx)
      val result = engine.queryPage(JobManagementQuery(limit = 1, cursor = first.nextCursor))

      Then("the page is refused as an expired snapshot rather than mixed")
      first.entries.map(_.jobId) shouldBe firstentries
      first.totalCount shouldBe firsttotalcount
      result shouldBe a[Consequence.Failure[_]]
      _failure_message(result) should include("expired snapshot")
    }

    "isolate subjects without count or cursor leakage" in {
      Given("one completed job for each distinct subject")
      val engine = createJobEngine()
      val user = ExecutionContext.test(SecurityContext.Privilege.User)
      val anonymous = ExecutionContext.test(SecurityContext.Privilege.Anonymous)
      val userid = _submit(engine, "user", user)
      val _ = _submit(engine, "anonymous", anonymous)

      When("the user requests the first management page")
      given ExecutionContext = user
      val page = engine.queryPage(JobManagementQuery(limit = 1)).toOption.get

      Then("only that subject's entry, count, and continuation are observable")
      page.entries.map(_.jobId) shouldBe Vector(userid)
      page.totalCount shouldBe 1
      page.nextCursor shouldBe None
    }

    "refuse limits outside the bounded request range" in {
      Given("an otherwise empty management-query engine")
      val engine = createJobEngine()

      When("zero and over-maximum limits are requested")
      given ExecutionContext = ExecutionContext.test()
      val zero = engine.queryPage(JobManagementQuery(limit = 0))
      val over = engine.queryPage(JobManagementQuery(limit = 101))

      Then("both requests fail before a page is formed")
      zero shouldBe a[Consequence.Failure[_]]
      over shouldBe a[Consequence.Failure[_]]
    }

    "project summary-only page entries" in {
      Given("a completed job containing debug and response data")
      val engine = createJobEngine()
      val ctx = ExecutionContext.test()
      val _ = _submit(engine, "summary", ctx, JobSubmitOption(
        requestSummary = Some("raw input must not appear"),
        parameters = Map("debug" -> "must not appear")
      ))

      When("the management page is requested")
      given ExecutionContext = ctx
      val entry = engine.queryPage(JobManagementQuery()).toOption.get.entries.head

      Then("the entry exposes only the bounded summary shape")
      entry.productElementNames.toSet shouldBe Set(
        "jobId", "status", "persistence", "origin", "createdAt", "updatedAt", "scheduledStartAt"
      )
    }

    "retain the legacy listJobs compatibility facade" in {
      Given("one persistent and one ephemeral completed job")
      val engine = createJobEngine()
      val ctx = ExecutionContext.test()
      val persistent = _submit(engine, "persistent", ctx)
      val ephemeral = _submit(engine, "ephemeral", ctx, JobSubmitOption(
        persistence = JobPersistencePolicy.Ephemeral
      ))

      When("the unchanged legacy list facade is called")
      val defaultlist = engine.listJobs()
      val alllist = engine.listJobs(persistentOnly = false)

      Then("its persistent-only default and expanded compatibility behavior remain")
      defaultlist.map(_.jobId) should contain(persistent)
      defaultlist.map(_.jobId) should not contain ephemeral
      alllist.map(_.jobId) should contain allOf (persistent, ephemeral)
    }
  }

  private def _submit(
    engine: JobEngine,
    name: String,
    ctx: ExecutionContext,
    option: JobSubmitOption = JobSubmitOption()
  ): JobId = {
    val task = ActionTask(ActionId.generate(), _success_action(name), ActionEngine.create(), None)
    val jobid = engine.submit(List(task), ctx, option.copy(runMode = JobRunMode.Sync)).toOption.get
    engine.getResult(jobid) should not be empty
    jobid
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

  private def _failure_message[A](result: Consequence[A]): String =
    result match {
      case Consequence.Failure(conclusion) => conclusion.toString
      case _ => ""
    }
}
