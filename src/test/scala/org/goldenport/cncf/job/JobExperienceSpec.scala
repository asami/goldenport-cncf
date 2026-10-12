package org.goldenport.cncf.job

import java.time.Instant
import java.util.Locale
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId, SecurityContext, SessionContext, SubjectKind}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceSpec extends AnyWordSpec with Matchers with GivenWhenThen with JobEngineTestFixture {
  "Job experience" should {
    "J1 reject anonymous Mine admission before any canonical query" in {
      Given("an anonymous execution context and a Mine scope")
      given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.Anonymous)
      val policy = new JobExperiencePolicy(JobExperienceScope.Mine(None))

      When("scope admission is evaluated")
      val admission = policy.authorizeScope

      Then("the anonymous request is rejected without selecting a Job")
      admission.isSuccess shouldBe false
    }

    "J1 prefer a stored session owner over broad capability or matching principal" in {
      Given("a stored Job submitter session and two broad-capability contexts")
      val base = ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager)
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(session = Some(SessionContext(sessionId = Some("owner-session")))))
      val sameprincipaldifferentsession = ExecutionContext.withSecurityContext(base, base.security.copy(session = Some(SessionContext(sessionId = Some("other-session")))))
      val submitter = JobSubmitter(owner.security.principal.id.value, owner.security.subjectKind.toString, Some("owner-session"))

      When("the canonical ownership predicate is evaluated")
      val admitted = JobQueryPolicy.isOwner(submitter, owner)
      val rejected = JobQueryPolicy.isOwner(submitter, sameprincipaldifferentsession)

      Then("the stored session remains authoritative even for a broad caller")
      admitted shouldBe true
      rejected shouldBe false
    }

    "J1 use the legacy principal and subject-kind fallback for real Mine pages and exact views" in {
      Given("a canonical legacy Job without a session and callers sharing or changing its principal kind")
      val sameprincipal = _context("legacy-owner", "other-session", SecurityContext.Privilege.User)
      val otherkindbase = _context("legacy-owner", "operator-session", SecurityContext.Privilege.ApplicationContentManager)
      val otherkind = ExecutionContext.withSecurityContext(otherkindbase, otherkindbase.security.copy(subjectKind = SubjectKind.Subsystem))
      val legacy = _model(JobId.generate(), JobSubmitter("legacy-owner", "User", None), JobStatus.Submitted, Map.empty)
      val engine = new CanonicalFixtureEngine(Vector(legacy), Map(legacy.jobId -> JobResult.Success(OperationResponse.Scalar("safe"))))
      val service = new JobExperienceService(engine)

      When("matching and different legacy callers request the Mine page and exact Job")
      val page = {
        given ExecutionContext = sameprincipal
        service.list(JobExperienceQuery(JobExperienceScope.Mine(None), persistentOnly = false))
      }
      val exact = {
        given ExecutionContext = sameprincipal
        service.get(legacy.jobId, JobExperienceScope.Mine(None))
      }
      val foreign = {
        given ExecutionContext = otherkind
        service.get(legacy.jobId, JobExperienceScope.Mine(None))
      }

      Then("the legacy principal plus subject kind admits only its matching user and hides the same Job from another kind")
      page.toOption.map(_.entries.map(_.jobId)) shouldBe Some(Vector(legacy.jobId))
      exact.toOption.map(_.detail.summary.jobId) shouldBe Some(legacy.jobId)
      foreign.isSuccess shouldBe false
    }

    "J2 normalize only safe application scopes" in {
      Given("an application name with a presentation spelling")
      val application = "My Blog"

      When("the application scope is parsed")
      val scope = JobExperienceScope.application(application)

      Then("the canonical normalized segment is retained")
      scope.toOption shouldBe Some(JobExperienceScope.Application("my-blog"))
    }

    "J2 reject traversal and unknown scope selectors" in {
      Given("unsafe and unknown scope inputs")
      val traversal = JobExperienceScope.application("../private")

      When("the scopes are parsed")
      val unknown = JobExperienceScope.parse(Some("subject"), None)

      Then("neither can select another subject or path")
      traversal.isSuccess shouldBe false
      unknown.isSuccess shouldBe false
    }

    "J2 require broad capability for Application and Operator scope admission" in {
      Given("ordinary and content-administrator execution contexts")
      val user = ExecutionContext.create(SecurityContext.Privilege.User)
      val administrator = ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager)

      When("Application and Operator scope admission is requested")
      val userapplication = {
        given ExecutionContext = user
        new JobExperiencePolicy(JobExperienceScope.Application("blog")).authorizeScope
      }
      val useroperator = {
        given ExecutionContext = user
        new JobExperiencePolicy(JobExperienceScope.Operator).authorizeScope
      }
      val administratorapplication = {
        given ExecutionContext = administrator
        new JobExperiencePolicy(JobExperienceScope.Application("blog")).authorizeScope
      }
      val administratoroperator = {
        given ExecutionContext = administrator
        new JobExperiencePolicy(JobExperienceScope.Operator).authorizeScope
      }

      Then("only the admitted broad-capability context can select those scopes")
      userapplication.isSuccess shouldBe false
      useroperator.isSuccess shouldBe false
      administratorapplication.isSuccess shouldBe true
      administratoroperator.isSuccess shouldBe true
    }

    "J2 admit Application records only from stored Job annotations" in {
      Given("an application administrator and canonical visible missing empty and foreign application records")
      val administrator = _context("administrator", "admin-session", SecurityContext.Privilege.ApplicationContentManager)
      val submitter = JobSubmitter("other", "User", Some("other-session"))
      val visible = _model(JobId.generate(), submitter, JobStatus.Running, Map("web.application-job" -> "true", "web.app" -> "blog"))
      val hidden = _model(JobId.generate(), submitter, JobStatus.Running, Map("web.app" -> "blog"))
      val empty = _model(JobId.generate(), submitter, JobStatus.Running, Map.empty)
      val foreign = _model(JobId.generate(), submitter, JobStatus.Running, Map("web.application-job" -> "true", "web.app" -> "other-app"))
      val service = new JobExperienceService(new CanonicalFixtureEngine(Vector(visible, hidden, empty, foreign)))

      When("the canonical Application page is requested")
      val page = {
        given ExecutionContext = administrator
        service.list(JobExperienceQuery(JobExperienceScope.Application("blog"), persistentOnly = false)).toOption.get
      }

      Then("only the exact stored application-job annotation admits the requested foreign application record")
      page.totalCount shouldBe 1
      page.entries.map(_.jobId) shouldBe Vector(visible.jobId)
    }

    "J4 preserve canonical status while choosing server vocabulary" in {
      Given("a submitted Job and the Japanese resolved execution locale")
      val status = JobStatus.Submitted

      When("the presentation vocabulary is resolved")
      val vocabulary = JobExperienceVocabulary.resolve(status, Locale.JAPANESE)

      Then("the display label is localized without changing the canonical state")
      status shouldBe JobStatus.Submitted
      vocabulary.status shouldBe "待機中"
    }

    "J4 project canonical detail without inventing task progress" in {
      Given("a canonical submitted management detail and a safe live result")
      val engine = new FixtureEngine
      given ExecutionContext = ExecutionContext.create()
      val service = new JobExperienceService(engine)

      When("the owner requests the Mine detail projection")
      val view = service.get(engine.jobId, JobExperienceScope.Mine(None)).toOption.get

      Then("the page reports indeterminate progress and the recorded task count")
      view.detail.summary.status shouldBe JobStatus.Submitted
      view.progress.state shouldBe "indeterminate"
      view.progress.taskCount shouldBe 2
      view.result shouldBe JobExperienceResult.Available("ok")
    }

    "J4 cover all canonical statuses, locale vocabulary, and terminal result availability" in {
      Given("six canonical owner records with live, failed, pending, and durable terminal result states")
      val owner = _context("owner", "owner-session", SecurityContext.Privilege.ApplicationContentManager)
      val submitter = JobSubmitter("owner", "User", Some("owner-session"))
      val statuses: Vector[JobStatus] = Vector(JobStatus.Submitted, JobStatus.Running, JobStatus.Suspended, JobStatus.Cancelled, JobStatus.Succeeded, JobStatus.Failed)
      val records = statuses.map(status => _model(JobId.generate(), submitter, status, Map.empty))
      val running = records.find(_.status == JobStatus.Running).get
      val cancelled = records.find(_.status == JobStatus.Cancelled).get
      val succeeded = records.find(_.status == JobStatus.Succeeded).get
      val engine = new CanonicalFixtureEngine(
        records,
        Map(running.jobId -> JobResult.Success(OperationResponse.Scalar("safe")), cancelled.jobId -> JobResult.Failure(org.goldenport.Conclusion.simple("private failure"))),
        Set(succeeded.jobId)
      )
      val service = new JobExperienceService(engine)

      When("the owner resolves every detail and both server vocabularies")
      val views = {
        given ExecutionContext = owner
        records.map(model => model.status -> service.get(model.jobId, JobExperienceScope.Mine(None)).toOption.get).toMap
      }
      val english = statuses.map(status => JobExperienceVocabulary.resolve(status, Locale.ENGLISH).status)
      val japanese = statuses.map(status => JobExperienceVocabulary.resolve(status, Locale.JAPANESE).status)

      Then("all states retain canonical identity with truthful availability and locale-specific display text")
      views.keySet shouldBe statuses.toSet
      views(JobStatus.Running).result shouldBe JobExperienceResult.Available("safe")
      views(JobStatus.Cancelled).result shouldBe JobExperienceResult.Failed
      views(JobStatus.Submitted).result shouldBe JobExperienceResult.Pending
      views(JobStatus.Failed).result shouldBe JobExperienceResult.Unavailable
      views(JobStatus.Succeeded).result shouldBe JobExperienceResult.UnavailableAfterRestart
      views(JobStatus.Submitted).progress.state shouldBe "indeterminate"
      views(JobStatus.Running).progress.state shouldBe "indeterminate"
      views(JobStatus.Suspended).progress.state shouldBe "paused"
      Vector(JobStatus.Cancelled, JobStatus.Succeeded, JobStatus.Failed).foreach(status =>
        views(status).progress.state shouldBe "terminal"
      )
      views(JobStatus.Succeeded).refreshable shouldBe false
      english should have size 6
      japanese should have size 6
      english should not be japanese
    }

    "J4 make a denied exact result indistinguishable from a missing Job at the experience boundary" in {
      Given("one session-owned canonical result and another broad-capability session")
      val owner = _context("result-owner", "owner-session", SecurityContext.Privilege.ApplicationContentManager)
      val foreign = _context("result-operator", "foreign-session", SecurityContext.Privilege.ApplicationContentManager)
      val model = _model(JobId.generate(), JobSubmitter("result-owner", "User", Some("owner-session")), JobStatus.Succeeded, Map.empty)
      val service = new JobExperienceService(new CanonicalFixtureEngine(Vector(model), Map(model.jobId -> JobResult.Success(OperationResponse.Scalar("safe")))))

      When("the foreign session requests the known id and an unrelated missing id")
      val denied = {
        given ExecutionContext = foreign
        service.get(model.jobId, JobExperienceScope.Mine(None))
      }
      val missing = {
        given ExecutionContext = foreign
        service.get(JobId.generate(), JobExperienceScope.Mine(None))
      }

      Then("neither result is projected or classified to the caller")
      denied.isSuccess shouldBe false
      missing.isSuccess shouldBe false
    }

    "J3 keep Mine and Application cursor scope identities distinct at shared ingress" in {
      Given("the same management filters under two canonical scope contracts")
      val mine = JobExperienceQueryCodec.parse(None, Some("blog"), None, Some("running"), None, Some("25"), None, Some("mine")).toOption.get
      val application = JobExperienceQueryCodec.parse(None, Some("blog"), None, Some("running"), None, Some("25"), None, Some("application")).toOption.get

      When("the parsed query contracts are compared before any engine page request")
      val minekey = mine.scope.visibilityKey
      val applicationkey = application.scope.visibilityKey

      Then("the cursor-binding policy receives different normalized visibility keys")
      minekey shouldBe "mine:blog"
      applicationkey shouldBe "application:blog"
      minekey should not be applicationkey
    }

    "J1/J3 apply Mine ownership before count and bind the resulting next cursor to scope and caller" in {
      Given("canonical management records for two sessions and a three-entry owner page")
      val owner = _context("owner", "owner-session", SecurityContext.Privilege.ApplicationContentManager)
      val other = _context("other", "other-session", SecurityContext.Privilege.ApplicationContentManager)
      val ownersubmitter = JobSubmitter("owner", "User", Some("owner-session"))
      val othersubmitter = JobSubmitter("other", "User", Some("other-session"))
      val engine = new CanonicalFixtureEngine(Vector(
        _model(JobId.generate(), ownersubmitter, JobStatus.Running, Map("web.application-job" -> "true", "web.app" -> "blog")),
        _model(JobId.generate(), ownersubmitter, JobStatus.Submitted, Map("web.application-job" -> "true", "web.app" -> "blog")),
        _model(JobId.generate(), ownersubmitter, JobStatus.Succeeded, Map("web.application-job" -> "true", "web.app" -> "blog")),
        _model(JobId.generate(), othersubmitter, JobStatus.Running, Map("web.application-job" -> "true", "web.app" -> "blog"))
      ))
      val service = new JobExperienceService(engine)
      val firstquery = JobExperienceQuery(JobExperienceScope.Mine(Some("blog")), persistentOnly = false, limit = 1)

      When("the owner obtains a first page and another session or scope reuses its cursor")
      val first = {
        given ExecutionContext = owner
        service.list(firstquery).toOption.get
      }
      val cursor = first.nextCursor.getOrElse(fail("first owner page must have a cursor"))
      val ownersecond = {
        given ExecutionContext = owner
        service.list(firstquery.copy(cursor = Some(cursor)))
      }
      val changedscope = {
        given ExecutionContext = owner
        service.list(firstquery.copy(scope = JobExperienceScope.Application("blog"), cursor = Some(cursor)))
      }
      val changedcaller = {
        given ExecutionContext = other
        service.list(firstquery.copy(cursor = Some(cursor)))
      }

      Then("foreign records never enter count or pages, while the cursor admits only its original caller and scope")
      first.totalCount shouldBe 3
      first.entries.map(_.jobId) should not contain engine.models.last.jobId
      ownersecond.isSuccess shouldBe true
      changedscope.isSuccess shouldBe false
      changedcaller.isSuccess shouldBe false
    }

    "J3 reject malformed changed-query and expired canonical page cursors while preserving the empty-key management reader path" in {
      Given("a canonical owner page, its first scoped cursor, and a later snapshot with a removed record")
      val owner = _context("cursor-owner", "cursor-session", SecurityContext.Privilege.ApplicationContentManager)
      val submitter = JobSubmitter("cursor-owner", "User", Some("cursor-session"))
      val firstmodel = _model(JobId.generate(), submitter, JobStatus.Running, Map("web.application-job" -> "true", "web.app" -> "blog"))
      val secondmodel = _model(JobId.generate(), submitter, JobStatus.Submitted, Map("web.application-job" -> "true", "web.app" -> "blog"))
      val engine = new CanonicalFixtureEngine(Vector(firstmodel, secondmodel))
      val service = new JobExperienceService(engine)
      val query = JobExperienceQuery(JobExperienceScope.Mine(None), persistentOnly = false, limit = 1)

      When("the same cursor is followed normally then presented as malformed changed-filter and expired-snapshot input")
      val first = {
        given ExecutionContext = owner
        service.list(query).toOption.get
      }
      val cursor = first.nextCursor.getOrElse(fail("first canonical page must carry a cursor"))
      val normal = {
        given ExecutionContext = owner
        service.list(query.copy(cursor = Some(cursor)))
      }
      val changedfilter = {
        given ExecutionContext = owner
        service.list(query.copy(status = Some(JobStatus.Running), cursor = Some(cursor)))
      }
      val malformed = {
        given ExecutionContext = owner
        service.list(query.copy(cursor = Some(JobManagementCursor.fromOpaque("not-a-management-cursor"))))
      }
      val expired = {
        given ExecutionContext = owner
        new JobExperienceService(new CanonicalFixtureEngine(Vector(firstmodel))).list(query.copy(cursor = Some(cursor)))
      }
      val legacyfirst = {
        given ExecutionContext = owner
        engine.queryPage(query.management, JobQueryPolicy.default).toOption.get
      }
      val legacyreplay = {
        given ExecutionContext = owner
        engine.queryPage(query.management.copy(cursor = legacyfirst.nextCursor), JobQueryPolicy.default)
      }

      Then("only the bound scoped and legacy empty-key follow-ups are admitted; altered or stale opaque cursors fail before page projection")
      normal.isSuccess shouldBe true
      changedfilter.isSuccess shouldBe false
      malformed.isSuccess shouldBe false
      expired.isSuccess shouldBe false
      legacyreplay.isSuccess shouldBe true
    }

    "J5 drive the canonical runtime control transition and replay through the admitted Mine facade" in {
      Given("a manual runtime Job owned by a control-capable session")
      val fixture = createManualJobEngine()
      val owner = _context("control-owner", "control-session", SecurityContext.Privilege.ApplicationContentManager)
      val jobid = _jobid(fixture.engine.submit(Nil, owner, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)))
      val service = new JobExperienceService(fixture.engine)

      When("the owner cancels the Job and repeats the same desired-state command")
      val changed = {
        given ExecutionContext = owner
        service.control(jobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)
      }
      val replay = {
        given ExecutionContext = owner
        service.control(jobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)
      }

      Then("the first canonical transition changes state and the replay returns the engine's changed=false race-safe outcome")
      changed.toOption.map(_.status) shouldBe Some(JobStatus.Cancelled)
      changed.toOption.map(_.changed) shouldBe Some(true)
      replay.toOption.map(_.status) shouldBe Some(JobStatus.Cancelled)
      replay.toOption.map(_.changed) shouldBe Some(false)
      fixture.engine.query(jobid).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 1
    }

    "J5 reject missing foreign no-capability and durable controls before canonical Job effects" in {
      Given("a manual runtime Job, a foreign session, a non-controlling owner, and a durable canonical projection")
      val fixture = createManualJobEngine()
      val owner = _context("owner", "owner-session", SecurityContext.Privilege.ApplicationContentManager)
      val foreign = _context("foreign", "foreign-session", SecurityContext.Privilege.ApplicationContentManager)
      val nocapabilityowner = _context("plain-owner", "plain-session", SecurityContext.Privilege.User)
      val jobid = _jobid(fixture.engine.submit(Nil, owner, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)))
      val plainjobid = _jobid(fixture.engine.submit(Nil, nocapabilityowner, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)))
      val durable = _model(JobId.generate(), JobSubmitter("owner", "User", Some("owner-session")), JobStatus.Failed, Map.empty, JobDataOrigin.Durable)
      val durableengine = new CanonicalFixtureEngine(Vector(durable))
      val runtime = new JobExperienceService(fixture.engine)
      val archived = new JobExperienceService(durableengine)

      When("each denied caller requests Cancel before a state transition can be emitted")
      val missing = {
        given ExecutionContext = owner
        runtime.control(JobId.generate(), JobExperienceScope.Mine(None), JobControlCommand.Cancel)
      }
      val foreignresult = {
        given ExecutionContext = foreign
        runtime.control(jobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)
      }
      val nocapability = {
        given ExecutionContext = nocapabilityowner
        runtime.control(plainjobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)
      }
      val durableresult = {
        given ExecutionContext = owner
        archived.control(durable.jobId, JobExperienceScope.Mine(None), JobControlCommand.Retry)
      }

      Then("all observations fail while runtime state and durable control invocation stay unchanged")
      missing.isSuccess shouldBe false
      foreignresult.isSuccess shouldBe false
      nocapability.isSuccess shouldBe false
      durableresult.isSuccess shouldBe false
      fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
      fixture.engine.getStatus(plainjobid) shouldBe Some(JobStatus.Submitted)
      fixture.engine.query(jobid).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 0
      durableengine.controlCalls shouldBe 0
    }

    "J5 retain the admitted canonical control response without exposing a facade-owned replacement" in {
      Given("an owner-admitted runtime Job whose engine control response carries a canonical payload")
      val fixture = new FixtureEngine
      val owner = _context("payload-owner", "payload-session", SecurityContext.Privilege.ApplicationContentManager)
      val service = new JobExperienceService(fixture)

      When("the owner submits the one canonical cancel command through the experience facade")
      val response = {
        given ExecutionContext = owner
        service.control(fixture.jobId, JobExperienceScope.Mine(None), JobControlCommand.Cancel)
      }

      Then("the exact engine response payload survives the facade without a second engine action")
      response.toOption.flatMap(_.response) shouldBe Some(OperationResponse.Scalar("canonical-control-payload"))
      fixture.controlCount shouldBe 1
    }

    "J5 route canonical suspend resume and failure recovery transitions through the Mine control contract" in {
      Given("manual runtime Jobs at submitted suspended and failed recovery states")
      val suspendedfixture = createManualJobEngine()
      val failedfixture = createManualJobEngine()
      val owner = _context("lifecycle-owner", "lifecycle-session", SecurityContext.Privilege.ApplicationContentManager)
      val suspendedid = _jobid(suspendedfixture.engine.submit(Nil, owner, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)))
      val failedid = _jobid(failedfixture.engine.submit(List(FailingTask()), owner, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)))
      failedfixture.engine.drainOne() shouldBe true
      failedfixture.engine.getStatus(failedid) shouldBe Some(JobStatus.Failed)
      val suspendedservice = new JobExperienceService(suspendedfixture.engine)
      val failedservice = new JobExperienceService(failedfixture.engine)

      When("the admitted owner suspends and resumes one Job and retries the canonical failed Job")
      val suspended = {
        given ExecutionContext = owner
        suspendedservice.control(suspendedid, JobExperienceScope.Mine(None), JobControlCommand.Suspend)
      }
      val resumed = {
        given ExecutionContext = owner
        suspendedservice.control(suspendedid, JobExperienceScope.Mine(None), JobControlCommand.Resume)
      }
      val retried = {
        given ExecutionContext = owner
        failedservice.control(failedid, JobExperienceScope.Mine(None), JobControlCommand.Retry)
      }

      Then("each response is the canonical lifecycle outcome and recovery emits one retry submission before execution resumes")
      suspended.toOption.map(result => result.status -> result.changed) shouldBe Some(JobStatus.Suspended -> true)
      resumed.toOption.map(result => result.status -> result.changed) shouldBe Some(JobStatus.Running -> true)
      retried.toOption.map(result => result.status -> result.changed) shouldBe Some(JobStatus.Submitted -> true)
      suspendedfixture.engine.query(suspendedid).get.timeline.events.count(_.kind == "job.suspended") shouldBe 1
      suspendedfixture.engine.query(suspendedid).get.timeline.events.count(_.kind == "job.resumed") shouldBe 1
      failedfixture.engine.query(failedid).get.timeline.events.count(_.kind == "job.retry.submitted") shouldBe 1
    }

    "J6 return bounded diagnostics without task result or timeline note text" in {
      Given("an administrator-scoped Job diagnostics request")
      val engine = new FixtureEngine
      given ExecutionContext = ExecutionContext.create(SecurityContext.Privilege.ApplicationContentManager)
      val service = new JobExperienceService(engine)

      When("the admitted diagnostics projection reads its canonical task and timeline pages")
      val diagnostics = service.diagnostics(engine.jobId, JobExperienceScope.Mine(None), offset = 0, limit = 25).toOption.get

      Then("private task and timeline text is redacted at the experience boundary")
      diagnostics.tasks.tasks.head.result.message shouldBe None
      diagnostics.timeline.events.head.note shouldBe None
    }

    "J6 read bounded diagnostics through the canonical scoped reader and reject unauthorized or out-of-range pages" in {
      Given("a canonical owner Job with private task and timeline text plus administrator and ordinary owner contexts")
      val administrator = _context("diagnostic-owner", "diagnostic-session", SecurityContext.Privilege.ApplicationContentManager)
      val ordinaryowner = _context("diagnostic-owner", "diagnostic-session", SecurityContext.Privilege.User)
      val task = JobTaskReadModel(TaskId.generate(), None, JobTaskStatus.Succeeded, Instant.EPOCH, Some(Instant.EPOCH), JobTaskResultSummary(success = true, Some("private task result")))
      val timeline = JobTimelineEvent(1L, Instant.EPOCH, "completed", None, None, Some("private timeline note"))
      val model = _model(
        JobId.generate(),
        JobSubmitter("diagnostic-owner", "User", Some("diagnostic-session")),
        JobStatus.Succeeded,
        Map.empty
      ).copy(
        tasks = JobTaskPage(0, 25, 1, 1, Vector(task)),
        timeline = JobTimelinePage(0, 25, 1, 1, Vector(timeline))
      )
      val service = new JobExperienceService(new CanonicalFixtureEngine(Vector(model)))

      When("the administrator requests an admitted page while the ordinary owner and invalid page bounds also request diagnostics")
      val admitted = {
        given ExecutionContext = administrator
        service.diagnostics(model.jobId, JobExperienceScope.Mine(None), 0, 25)
      }
      val denied = {
        given ExecutionContext = ordinaryowner
        service.diagnostics(model.jobId, JobExperienceScope.Mine(None), 0, 25)
      }
      val negativeoffset = {
        given ExecutionContext = administrator
        service.diagnostics(model.jobId, JobExperienceScope.Mine(None), -1, 25)
      }
      val oversized = {
        given ExecutionContext = administrator
        service.diagnostics(model.jobId, JobExperienceScope.Mine(None), 0, 101)
      }

      Then("only the scoped bounded reader page is returned and it removes result and timeline note text")
      admitted.toOption.map(_.tasks.tasks.head.result.message) shouldBe Some(None)
      admitted.toOption.map(_.timeline.events.head.note) shouldBe Some(None)
      denied.isSuccess shouldBe false
      negativeoffset.isSuccess shouldBe false
      oversized.isSuccess shouldBe false
    }
  }

  private final class FixtureEngine extends JobEngine {
    val jobId: JobId = JobId.generate()
    var controlCount: Int = 0
    private val _summary = JobManagementSummary(jobId, JobStatus.Submitted, JobPersistencePolicy.Persistent, JobDataOrigin.Runtime, Instant.EPOCH, Instant.EPOCH, None)
    private val _detail = JobManagementDetail(_summary, JobManagementRetrySummary(JobRetryKind.None, 0, 3, None, false, false, false, false), JobResultSummary(JobStatus.Submitted, success = false, message = None), 2, 0)

    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = Consequence.success(jobId)
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = Consequence.success(jobId)
    def getStatus(jobId: JobId): Option[JobStatus] = Some(JobStatus.Submitted)
    def getResult(jobId: JobId): Option[JobResult] = Some(JobResult.Success(OperationResponse.Scalar("ok")))
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = Consequence.success(getResult(jobId).get)
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy = JobControlPolicy.default)(using ExecutionContext): Consequence[JobControlResponse] = {
      controlCount += 1
      Consequence.success(JobControlResponse(jobId, JobStatus.Submitted, Some(OperationResponse.Scalar("canonical-control-payload")), async = true, changed = false))
    }
    def query(jobId: JobId): Option[JobQueryReadModel] = None
    def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage] = Some(JobTaskPage(offset, limit, 0, 0, Vector.empty))
    def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage] = Some(JobTimelinePage(offset, limit, 0, 0, Vector.empty))
    override def queryPage(request: JobManagementQuery, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[JobManagementPage] = Consequence.success(JobManagementPage(Vector(_summary), 1, None))
    override def queryManagementDetail(jobId: JobId, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobManagementDetail]] = Consequence.success(Some(_detail))
    override def queryManagementResult(jobId: JobId, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobManagementResult]] = Consequence.success(Some(JobManagementResult.Available(getResult(jobId).get)))
    override def queryManagementTasks(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobTaskPage]] =
      Consequence.success(Some(JobTaskPage(offset, limit, 1, 1, Vector(JobTaskReadModel(TaskId.generate(), None, JobTaskStatus.Succeeded, Instant.EPOCH, Some(Instant.EPOCH), JobTaskResultSummary(success = true, Some("private task result")))))))
    override def queryManagementTimeline(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobTimelinePage]] =
      Consequence.success(Some(JobTimelinePage(offset, limit, 1, 1, Vector(JobTimelineEvent(1L, Instant.EPOCH, "completed", None, None, Some("private timeline note"))))))
  }

  private final class CanonicalFixtureEngine(
    val models: Vector[JobQueryReadModel],
    results: Map[JobId, JobResult] = Map.empty,
    durableTerminalIds: Set[JobId] = Set.empty
  ) extends JobEngine {
    private val _snapshot = JobManagementReader.Snapshot(models, results, durableTerminalIds)
    var controlCalls: Int = 0

    def submit(tasks: List[JobTask], ctx: ExecutionContext): Consequence[JobId] = Consequence.operationInvalid("fixture", "submission is not used")
    def submit(tasks: List[JobTask], ctx: ExecutionContext, option: JobSubmitOption): Consequence[JobId] = Consequence.operationInvalid("fixture", "submission is not used")
    def getStatus(jobId: JobId): Option[JobStatus] = models.find(_.jobId == jobId).map(_.status)
    def getResult(jobId: JobId): Option[JobResult] = results.get(jobId)
    def awaitResult(jobId: JobId, timeoutMillis: Long): Consequence[JobResult] = Consequence.operationNotFound("fixture")
    def control(jobId: JobId, request: JobControlRequest, policy: JobControlPolicy = JobControlPolicy.default)(using ExecutionContext): Consequence[JobControlResponse] = {
      controlCalls += 1
      Consequence.operationInvalid("fixture", "control is not used")
    }
    def query(jobId: JobId): Option[JobQueryReadModel] = models.find(_.jobId == jobId)
    def queryTasks(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTaskPage] = None
    def queryTimeline(jobId: JobId, offset: Int = 0, limit: Int = 100): Option[JobTimelinePage] = None
    override def queryPage(request: JobManagementQuery, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[JobManagementPage] = JobManagementReader.queryPage(_snapshot, request, policy)
    override def queryManagementDetail(jobId: JobId, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobManagementDetail]] = JobManagementReader.queryDetail(_snapshot, jobId, policy)
    override def queryManagementResult(jobId: JobId, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobManagementResult]] = JobManagementReader.queryResult(_snapshot, jobId, policy)
    override def queryManagementTasks(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobTaskPage]] =
      JobManagementReader.queryTasks(_snapshot, jobId, offset, limit, policy)
    override def queryManagementTimeline(jobId: JobId, offset: Int, limit: Int, policy: JobQueryPolicy = JobQueryPolicy.default)(using ExecutionContext): Consequence[Option[JobTimelinePage]] =
      JobManagementReader.queryTimeline(_snapshot, jobId, offset, limit, policy)
  }

  private def _context(
    principal: String,
    session: String,
    privilege: SecurityContext.Privilege
  ): ExecutionContext = {
    val base = ExecutionContext.create(privilege)
    ExecutionContext.withSecurityContext(base, base.security.copy(
      principal = new Principal {
        def id: PrincipalId = PrincipalId(principal)
        def attributes: Map[String, String] = Map("authenticated" -> "true")
      },
      session = Some(SessionContext(sessionId = Some(session)))
    ))
  }

  private def _jobid(result: Consequence[JobId]): JobId =
    result.toOption.getOrElse(fail("manual Job submission failed"))

  private final case class FailingTask(
    actionId: ActionId = ActionId.generate()
  ) extends JobTask {
    def run(ctx: ExecutionContext): TaskOutcome = {
      val _ = ctx
      TaskFailed(Consequence.stateInvalid[Nothing]("initial failure").conclusion)
    }
  }

  private def _model(
    id: JobId,
    submitter: JobSubmitter,
    status: JobStatus,
    parameters: Map[String, String],
    origin: JobDataOrigin = JobDataOrigin.Runtime
  ): JobQueryReadModel =
    JobQueryReadModel(
      id,
      status,
      JobPersistencePolicy.Persistent,
      origin,
      submitter,
      Instant.EPOCH,
      Instant.EPOCH,
      None,
      JobTaskPage(0, 25, 0, 0, Vector.empty),
      JobTimelinePage(0, 25, 0, 0, Vector.empty),
      JobTraceTree(id, Vector.empty),
      JobDebugInfo(None, parameters, Vector.empty),
      None,
      JobEventLineage(None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, None, AsyncFailureDisposition.NotApplicable),
      JobContinuationSummary.empty,
      JobRetryState(),
      JobResultSummary(status, success = status == JobStatus.Succeeded, message = None),
      None,
      None
    )
}
