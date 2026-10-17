package org.goldenport.cncf.job

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.config.{OperationMode, RuntimeOperationSecurityPolicy}
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, RuntimeContext, SecurityContext, SessionContext}
import org.goldenport.cncf.security.{SecurityAuthenticationProvenance, SecuritySubject}
import org.goldenport.protocol.operation.OperationResponse
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Configured audit readers observe canonical bounded Jobs without gaining
 * payload, diagnostic, control or polling authority.
 *
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class JobAuditReadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _production_policy = RuntimeOperationSecurityPolicy.default.copy(
    operationMode = OperationMode.Production,
    webProductionAdminEnabled = true,
    webProductionAdminJobsRoles = Vector("audit_viewer")
  )

  "Configured Job audit reads" should {
    "admit provider-authorized foreign Jobs through the canonical reader without querying private payloads" in {
      Given("a Production system-privilege provider subject with the configured audit role and foreign canonical Jobs")
      given ExecutionContext = _context("auditor")
      val engine = new AuditFixtureEngine(Vector(_model(JobStatus.Succeeded), _model(JobStatus.Running)))
      val service = new JobExperienceService(engine, Some(_production_policy))

      When("Audit lists and reads an exact bounded detail")
      val page = service.list(JobExperienceQuery(JobExperienceScope.Audit)).toOption.getOrElse(fail("audit page denied"))
      val view = service.get(engine.models.head.jobId, JobExperienceScope.Audit).toOption.getOrElse(fail("audit detail denied"))

      Then("both foreign Jobs are visible, result and controls are unavailable, and only management page/detail were queried")
      page.totalCount shouldBe 2
      page.entries.map(_.jobId).toSet shouldBe engine.models.map(_.jobId).toSet
      view.detail.summary.jobId shouldBe engine.models.head.jobId
      view.result shouldBe JobExperienceResult.Unavailable
      view.controls shouldBe empty
      view.refreshable shouldBe false
      engine.pageCalls shouldBe 1
      engine.detailCalls shouldBe 1
      engine.privateCalls shouldBe 0
      engine.controlCalls shouldBe 0
    }

    "remain read-only across generated status and administrator capability combinations" in {
      Given("every canonical status and independently generated Job/content administrator capabilities")
      val statuses = Gen.oneOf(JobStatus.Submitted, JobStatus.Running, JobStatus.Suspended, JobStatus.Cancelled, JobStatus.Succeeded, JobStatus.Failed)
      val property = Prop.forAll(statuses, Gen.oneOf(true, false), Gen.oneOf(true, false)) { (status, jobadmin, contentadmin) =>
        given ExecutionContext = _context("mixed-auditor", capabilities =
          Option.when(jobadmin)(Capability("job_admin")).toSet ++ Option.when(contentadmin)(Capability("content_admin")).toSet)
        val engine = new AuditFixtureEngine(Vector(_model(status)))
        val service = new JobExperienceService(engine, Some(_production_policy))
        val id = engine.models.head.jobId

        When("Audit detail, diagnostics and every lifecycle control are requested")
        val view = service.get(id, JobExperienceScope.Audit).toOption.getOrElse(fail("audit detail denied"))
        val diagnostics = service.diagnostics(id, JobExperienceScope.Audit, 0, 25)
        val controls = Vector(JobControlCommand.Cancel, JobControlCommand.Suspend, JobControlCommand.Resume, JobControlCommand.Retry)
          .map(service.control(id, JobExperienceScope.Audit, _))

        Then("status survives while private reads, mutation and polling remain unavailable regardless of additional authority")
        view.detail.summary.status shouldBe status
        view.result shouldBe JobExperienceResult.Unavailable
        view.controls shouldBe empty
        view.refreshable shouldBe false
        diagnostics.isSuccess shouldBe false
        controls.foreach(_.isSuccess shouldBe false)
        engine.privateCalls shouldBe 0
        engine.controlCalls shouldBe 0
        true
      }
      Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property).passed shouldBe true
    }

    "deny absent disabled non-Production forged low-privilege and wrong-role authority before any read" in {
      Given("valid provider authority with one required admission condition removed in each case")
      val cases = Vector(
        (_context("no-policy"), Option.empty[RuntimeOperationSecurityPolicy]),
        (_context("disabled"), Some(_production_policy.copy(webProductionAdminEnabled = false))),
        (_context("develop-policy"), Some(_production_policy.copy(operationMode = OperationMode.Develop))),
        (_context("test-context", mode = OperationMode.Test), Some(_production_policy)),
        (_context("forged", trusted = false), Some(_production_policy)),
        (_context("low", privilege = SecurityContext.Privilege.User), Some(_production_policy)),
        (_context("wrong-role", role = "system_admin"), Some(_production_policy)),
        (_context("custom-mismatch"), Some(_production_policy.copy(webProductionAdminJobsRoles = Vector("custom_auditor"))))
      )
      cases.foreach { case (context, policy) =>
        given ExecutionContext = context
        val engine = new AuditFixtureEngine(Vector(_model(JobStatus.Running)))
        val service = new JobExperienceService(engine, policy)

        When("Audit scope, direct reader policy, list and detail admission are attempted")
        val admission = new JobExperiencePolicy(JobExperienceScope.Audit, policy)
        val missing = new JobExperienceService(new AuditFixtureEngine(Vector.empty), policy)
          .get(engine.models.head.jobId, JobExperienceScope.Audit)
        val denied = service.get(engine.models.head.jobId, JobExperienceScope.Audit)

        Then("the structured scope/read failures deny access and exact denial is indistinguishable from missing")
        admission.authorizeScope shouldBe a[Consequence.Failure[_]]
        admission.authorizeRead(engine.models.head) shouldBe a[Consequence.Failure[_]]
        service.list(JobExperienceQuery(JobExperienceScope.Audit)).isSuccess shouldBe false
        (denied, missing) match {
          case (Consequence.Failure(left), Consequence.Failure(right)) =>
            left.status shouldBe right.status
            left.displayMessage shouldBe right.displayMessage
          case _ => fail("denied and missing audit details must both fail")
        }
        engine.pageCalls shouldBe 0
        engine.detailCalls shouldBe 0
        engine.privateCalls shouldBe 0
      }
    }

    "bind canonical Audit cursors to subject authorization scope filter and snapshot" in {
      Given("a configured mixed-capability auditor and three foreign canonical records")
      val owner = _context("cursor-auditor", capabilities = Set(Capability("job_admin")))
      val engine = new AuditFixtureEngine(Vector.fill(3)(_model(JobStatus.Succeeded)))
      val service = new JobExperienceService(engine, Some(_production_policy))
      val query = JobExperienceQuery(JobExperienceScope.Audit, limit = 1)
      val first = service.list(query)(using owner).toOption.getOrElse(fail("first page denied"))
      val cursor = first.nextCursor.getOrElse(fail("cursor missing"))

      When("the same cursor is continued or replayed across changed request identities")
      val next = service.list(query.copy(cursor = Some(cursor)))(using owner)
      val foreign = service.list(query.copy(cursor = Some(cursor)))(using _context("foreign-auditor", capabilities = Set(Capability("job_admin"))))
      val changedauth = service.list(query.copy(cursor = Some(cursor)))(using _context("cursor-auditor"))
      val operator = service.list(query.copy(scope = JobExperienceScope.Operator, cursor = Some(cursor)))(using owner)
      val filter = service.list(query.copy(status = Some(JobStatus.Succeeded), cursor = Some(cursor)))(using owner)
      val malformed = service.list(query.copy(cursor = Some(JobManagementCursor.fromOpaque("invalid"))))(using owner)
      val changedengine = new AuditFixtureEngine(engine.models.drop(1))
      val expired = new JobExperienceService(changedengine, Some(_production_policy)).list(query.copy(cursor = Some(cursor)))(using owner)

      Then("only the original visibility contract continues and the page is bounded without overlap")
      next.isSuccess shouldBe true
      next.toOption.get.entries.map(_.jobId).toSet.intersect(first.entries.map(_.jobId).toSet) shouldBe empty
      first.totalCount shouldBe 3
      Vector(foreign, changedauth, operator, filter, malformed, expired).foreach(_.isSuccess shouldBe false)
    }

    "keep original constructors and deny Audit selector authority on default services" in {
      Given("the original one-argument constructors and an otherwise authorized provider context")
      given ExecutionContext = _context("default-auditor")
      val engine = new AuditFixtureEngine(Vector(_model(JobStatus.Succeeded)))
      val service = new JobExperienceService(engine)
      val policy = new JobExperiencePolicy(JobExperienceScope.Audit)

      When("Audit is parsed with and without an application selector")
      val parsed = JobExperienceScope.parse(Some("audit"), None)
      val invalid = JobExperienceScope.parse(Some("audit"), Some("blog"))

      Then("the additive selector has distinct visibility but no ambient authority and the JVM constructors remain present")
      parsed.toOption shouldBe Some(JobExperienceScope.Audit)
      JobExperienceScope.Audit.visibilityKey shouldBe "audit"
      JobExperienceScope.Audit.applicationOption shouldBe None
      invalid.isSuccess shouldBe false
      policy.authorizeScope.isSuccess shouldBe false
      service.list(JobExperienceQuery(JobExperienceScope.Audit)).isSuccess shouldBe false
      classOf[JobExperienceService].getConstructor(classOf[JobEngine]).getParameterCount shouldBe 1
      classOf[JobExperiencePolicy].getConstructor(classOf[JobExperienceScope]).getParameterCount shouldBe 1
    }
  }

  private def _context(
    principal: String,
    role: String = "audit_viewer",
    privilege: SecurityContext.Privilege = SecurityContext.Privilege.System,
    trusted: Boolean = true,
    mode: OperationMode = OperationMode.Production,
    capabilities: Set[Capability] = Set.empty
  ): ExecutionContext = {
    val base = ExecutionContext.create(privilege)
    val security = base.security.copy(
      principal = new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = Map(
          "authenticated" -> "true", "role" -> role, "privilege" -> privilege.name,
          SecuritySubject.AuthenticationProvenanceAttribute -> SecuritySubject.ProviderAuthenticationProvenance
        )
      },
      capabilities = capabilities,
      session = Some(SessionContext(sessionId = Some(principal)))
    )
    val authenticated = ExecutionContext.withSecurityContext(base,
      if (trusted) SecurityAuthenticationProvenance.providerAuthenticated(security) else security)
    val runtime = new RuntimeContext(
      core = base.runtime.core,
      unitofworksupplier = () => base.unitOfWork,
      unitofworkinterpreterfn = base.runtime.unitOfWorkInterpreter,
      commitaction = _ => (), abortaction = _ => (), disposeaction = _ => (),
      token = "job-audit-read-spec", operationMode = mode
    )
    ExecutionContext.withRuntimeContext(authenticated, runtime)
  }

  private def _model(status: JobStatus): JobQueryReadModel = {
    val id = JobId.generate()
    JobQueryReadModel(
      id, status, JobPersistencePolicy.Persistent, JobDataOrigin.Runtime,
      JobSubmitter("foreign-owner", "User", Some("foreign-session")),
      Instant.EPOCH, Instant.EPOCH, None,
      JobTaskPage(0, 25, 0, 0, Vector.empty),
      JobTimelinePage(0, 25, 0, 0, Vector.empty), JobTraceTree(id, Vector.empty),
      JobDebugInfo(None, Map("secret" -> "private-debug"), Vector.empty), None,
      JobEventLineage(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, AsyncFailureDisposition.NotApplicable),
      JobContinuationSummary.empty, JobRetryState(),
      JobResultSummary(status, success = status == JobStatus.Succeeded, message = Some("private-failure")), None, None
    )
  }

  private final class AuditFixtureEngine(val models: Vector[JobQueryReadModel]) extends JobEngine {
    private val _snapshot = JobManagementReader.Snapshot(models,
      models.map(model => model.jobId -> JobResult.Success(OperationResponse.Scalar("private-result"))).toMap, Set.empty)
    var pageCalls: Int = 0
    var detailCalls: Int = 0
    var privateCalls: Int = 0
    var controlCalls: Int = 0

    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = Consequence.operationInvalid("fixture")
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = Consequence.operationInvalid("fixture")
    def getStatus(jobId: JobId): Option[JobStatus] = models.find(_.jobId == jobId).map(_.status)
    def getResult(jobId: JobId): Option[JobResult] = { privateCalls += 1; _snapshot.results.get(jobId) }
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = { privateCalls += 1; Consequence.operationInvalid("fixture") }
    def query(jobId: JobId): Option[JobQueryReadModel] = { privateCalls += 1; models.find(_.jobId == jobId) }
    def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage] = { privateCalls += 1; None }
    def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage] = { privateCalls += 1; None }
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy = JobControlPolicy.default)(using ExecutionContext): Consequence[JobControlResponse] = {
      controlCalls += 1
      Consequence.operationInvalid("fixture")
    }
    override def queryPage(request: JobManagementQuery, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[JobManagementPage] = {
      pageCalls += 1
      JobManagementReader.queryPage(_snapshot, request, policy)
    }
    override def queryManagementDetail(jobId: JobId, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobManagementDetail]] = {
      detailCalls += 1
      JobManagementReader.queryDetail(_snapshot, jobId, policy)
    }
    override def queryManagementResult(jobId: JobId, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobManagementResult]] = {
      privateCalls += 1
      JobManagementReader.queryResult(_snapshot, jobId, policy)
    }
    override def queryManagementTasks(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobTaskPage]] = {
      privateCalls += 1
      JobManagementReader.queryTasks(_snapshot, jobId, offset, limit, policy)
    }
    override def queryManagementTimeline(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobTimelinePage]] = {
      privateCalls += 1
      JobManagementReader.queryTimeline(_snapshot, jobId, offset, limit, policy)
    }
  }
}
