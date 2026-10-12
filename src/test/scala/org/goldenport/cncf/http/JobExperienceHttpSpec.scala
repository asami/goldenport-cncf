package org.goldenport.cncf.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.parser.parse
import java.time.Instant
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.component.builtin.auth.AuthComponent
import org.goldenport.cncf.context.{Capability, ExecutionContext, Principal, PrincipalId, SecurityContext, SecurityLevel, SessionContext, SubjectKind}
import org.goldenport.cncf.job.{ActionId, InMemoryJobEngine, JobDebugInfo, JobId, JobPersistencePolicy, JobRecord, JobRunMode, JobStatus, JobSubmitOption, JobTask, TaskOutcome, TaskSucceeded}
import org.goldenport.cncf.security.{AuthenticationProvider, AuthenticationRequest, AuthenticationResult}
import org.goldenport.cncf.subsystem.{GenericSubsystemAuthenticationBinding, GenericSubsystemAuthenticationProviderBinding, GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemRuntimeBinding, GenericSubsystemSecurityBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationProviderBinding, Subsystem, SubsystemExecutionProfile, SubsystemUserMode}
import org.goldenport.cncf.usernotification.{UserNotificationInboxEntry, UserNotificationInboxPage, UserNotificationInboxProvider, UserNotificationInboxQuery, UserNotificationInboxReadResult, UserNotificationInboxUpdate, UserNotificationRequest, UserNotificationResult}
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.goldenport.Consequence
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.typelevel.ci.CIString

/*
 * Real Http4s route admission coverage for the public Job experience. These
 * cases go through routes rather than renderer or codec-only entry points.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceHttpSpec extends AnyWordSpec with Matchers with GivenWhenThen with StaticFormAppRendererSpecFixtures {
  "Job experience Http4s routes" should {
    "H1 serve Mine Web and REST list requests through the live server" in {
      Given("an authenticated Mine subject and the admitted Http4s server")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-mine-session", "job-mine-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the user requests the canonical Web and REST Mine list paths")
      val web = app.run(_with_session(_get_request("/web/system/jobs?status=running"), "job-mine-session")).unsafeRunSync()
      val rest = app.run(_with_session(_get_request("/rest/v1/job-control/job-experience/list-my-jobs?status=running"), "job-mine-session")).unsafeRunSync()

      Then("both paths execute their real request pipeline and the subject page is private")
      subsystem.executionProfileC.toOption shouldBe Some(SubsystemExecutionProfile.Authenticated)
      web.status.code shouldBe 200
      rest.status.code shouldBe 200
      _cache(web) shouldBe Some("private, no-store")
      _cache(rest) shouldBe Some("private, no-store")
      rest.as[String].unsafeRunSync() should include ("total_count")
    }

    "H1 isolate a real submitted owner Job across Mine, Application, Operator, detail, and REST routes" in {
      Given("a real synchronous application Job submitted under one authenticated session")
      val subsystem = _job_experience_fixture_subsystem()
      _install_sessions(subsystem, Vector(
        _session_summary("owner-session", "owner", Map("role" -> "user", "privilege" -> "user")),
        _session_summary("other-session", "other", Map("role" -> "user", "privilege" -> "user"))
      ))
      val jobcomponent = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = jobcomponent.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(
        principal = new Principal {
          def id: PrincipalId = PrincipalId("owner")
          def attributes: Map[String, String] = Map("authenticated" -> "true")
        },
        session = Some(SessionContext(sessionId = Some("owner-session")))
      ))
      val jobid = subsystem.jobEngine.submit(
        List(_success_task("http-owner-job")),
        owner,
        JobSubmitOption(
          runMode = JobRunMode.Sync,
          parameters = Map("web.application-job" -> "true", "web.app" -> "blog")
        )
      ).toOption.getOrElse(fail("owner Job submission failed"))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the owner, another session, and the real REST detail dispatcher request the Job")
      val ownerlist = app.run(_with_session(_get_request("/web/system/jobs?persistentOnly=true&limit=25"), "owner-session")).unsafeRunSync()
      val ownerdetail = app.run(_with_session(_get_request(s"/web/system/jobs/${jobid.value}"), "owner-session")).unsafeRunSync()
      val foreigndetail = app.run(_with_session(_get_request(s"/web/system/jobs/${jobid.value}"), "other-session")).unsafeRunSync()
      val restdetail = app.run(_with_session(_get_request(s"/rest/v1/job-control/job-experience/get-job-experience?id=${jobid.value}&scope=mine"), "owner-session")).unsafeRunSync()

      Then("the owner receives the submitted record while the other session receives the same not-found boundary")
      ownerlist.status.code shouldBe 200
      ownerlist.as[String].unsafeRunSync() should include (jobid.value)
      ownerdetail.status.code shouldBe 200
      foreigndetail.status.code shouldBe 404
      restdetail.status.code shouldBe 200
      restdetail.as[String].unsafeRunSync() should include (jobid.value)
      _cache(ownerdetail) shouldBe Some("private, no-store")
      _cache(foreigndetail) shouldBe Some("private, no-store")
      _cache(restdetail) shouldBe Some("private, no-store")
    }

    "H1 preserve normalized application scope count and Next while admitting application and operator readers only" in {
      Given("two persisted blog Jobs, a foreign blog Job, and owner, manager, operator, other, and anonymous request contexts")
      val subsystem = _job_experience_fixture_subsystem()
      _install_sessions(subsystem, Vector(
        _session_summary("h1-owner", "h1-owner", Map("role" -> "user", "privilege" -> "user")),
        _session_summary("h1-other", "h1-other", Map("role" -> "user", "privilege" -> "user")),
        _session_summary("h1-manager", "h1-manager", Map("role" -> "content_manager", "privilege" -> "application_content_manager")).copy(capabilities = Vector("content_manager", "content_admin")),
        _session_summary("h1-operator", "h1-operator", Map("role" -> "content_admin", "privilege" -> "application_content_manager")).copy(capabilities = Vector("content_admin"))
      ))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      def _submitted_(principal: String, session: String, name: String) = {
        val context = ExecutionContext.withSecurityContext(base, base.security.copy(
          principal = new Principal { def id: PrincipalId = PrincipalId(principal); def attributes: Map[String, String] = Map("authenticated" -> "true") },
          session = Some(SessionContext(sessionId = Some(session)))
        ))
        subsystem.jobEngine.submit(
          List(_success_task(name)),
          context,
          JobSubmitOption(runMode = JobRunMode.Sync, parameters = Map("web.application-job" -> "true", "web.app" -> "blog"))
        ).toOption.getOrElse(fail(s"$name submission failed"))
      }
      val first = _submitted_("h1-owner", "h1-owner", "h1-blog-first")
      val second = _submitted_("h1-owner", "h1-owner", "h1-blog-second")
      val foreign = _submitted_("h1-other", "h1-other", "h1-blog-foreign")
      val descriptor = WebDescriptor(apps = Vector(WebDescriptor.App("blog"), WebDescriptor.App("system")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor))).routes(null).orNotFound

      When("the actual application, normalized application alias, operator, other, anonymous, detail, and REST paths are requested")
      val owner = app.run(_with_session(_get_request("/web/blog/jobs?status=succeeded&persistentOnly=true&limit=1"), "h1-owner")).unsafeRunSync()
      val application = app.run(_with_session(_get_request("/web/BLOG/admin/jobs?status=succeeded&persistentOnly=true&limit=1"), "h1-manager")).unsafeRunSync()
      val operator = app.run(_with_session(_get_request("/web/system/admin/jobs?status=succeeded&persistentOnly=true&limit=1"), "h1-operator")).unsafeRunSync()
      val otherdetail = app.run(_with_session(_get_request(s"/web/blog/jobs/${first.value}"), "h1-other")).unsafeRunSync()
      val anonymousdetail = app.run(_get_request(s"/web/blog/jobs/${first.value}")).unsafeRunSync()
      val rest = app.run(_with_session(_get_request("/rest/v1/job-control/job-experience/list-application-jobs?application=blog&status=succeeded&persistentOnly=true&limit=1"), "h1-manager")).unsafeRunSync()
      val ownerhtml = owner.as[String].unsafeRunSync()
      val applicationhtml = application.as[String].unsafeRunSync()
      val restbody = rest.as[String].unsafeRunSync()

      Then("the owner sees only its entries, the admitted readers retain the filtered Next, foreign and anonymous detail remain private not-found, and REST reports the filtered count")
      owner.status.code shouldBe 200
      (ownerhtml.contains(first.value) || ownerhtml.contains(second.value)) shouldBe true
      ownerhtml should not include (foreign.value)
      application.status.code shouldBe 200
      applicationhtml should include ("Next")
      applicationhtml should include ("status=succeeded")
      applicationhtml should include ("persistentOnly=true")
      operator.status.code shouldBe 200
      rest.status.code shouldBe 200
      restbody should include ("\"total_count\":3")
      Vector(otherdetail, anonymousdetail).foreach { response =>
        response.status.code shouldBe 404
        _cache(response) shouldBe Some("private, no-store")
      }
      Vector(owner, application, operator, rest).foreach(response => _cache(response) shouldBe Some("private, no-store"))
    }

    "H2 reject forged Web query and header administration selectors" in {
      Given("a production user session without Job administrator capability")
      val subsystem = _job_experience_fixture_subsystem(Configuration(Map(
        RuntimeConfig.operationModeKey -> ConfigurationValue.StringValue("production"),
        RuntimeConfig.webProductionAdminEnabledKey -> ConfigurationValue.StringValue("true")
      )))
      _install_job_session(subsystem, _session_summary("job-ordinary-session", "job-ordinary-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("forged operator fields accompany the system administration Job request")
      val response = app.run(_with_session(
        _get_request("/web/system/admin/jobs?scope=operator&principalId=operator&role=system_admin&privilege=system")
          .putHeaders(org.http4s.Header.Raw(CIString("X-CNCF-Role"), "system_admin")),
        "job-ordinary-session"
      )).unsafeRunSync()

      Then("only the authenticated session is used and access is denied")
      response.status.code shouldBe 403
    }

    "H2 apply installed-app disablement before dispatch while accepting the normalized installed application alias" in {
      Given("one authenticated owner and independently disabled and enabled application Web descriptors")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("h2-app-owner", "h2-app-owner", Map("role" -> "user", "privilege" -> "user")))
      val disabled = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(WebDescriptor(apps = Vector(WebDescriptor.App("other-app")))))
      ).routes(null).orNotFound
      val enabled = HttpRuntimeBindingAdmissionFixture.server(
        new HttpExecutionEngine(subsystem, Some(WebDescriptor(apps = Vector(WebDescriptor.App("blog")))))
      ).routes(null).orNotFound

      When("the actual blog Job list is requested from a descriptor that omits it and from its normalized installed alias")
      val denied = disabled.run(_with_session(_get_request("/web/blog/jobs"), "h2-app-owner")).unsafeRunSync()
      val admitted = enabled.run(_with_session(_get_request("/web/BLOG/jobs"), "h2-app-owner")).unsafeRunSync()

      Then("the disabled application is rejected before facade dispatch and the normalized alias reaches the private admitted route")
      denied.status.code shouldBe 403
      admitted.status.code shouldBe 200
      _cache(admitted) shouldBe Some("private, no-store")
    }

    "H3 redirect a fresh-CSRF Job command only through its canonical form endpoint" in {
      Given("the canonical job-control form endpoint and a fresh CSRF cookie")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-form-session", "job-form-user", Map("role" -> "user", "privilege" -> "user")))
      val token = WebCsrf.issue(Some("job-form-session"))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("a canonical mark-read form is submitted with its matching security token")
      val response = app.run(_with_session(
        _post_form_request("/form/job_control/job_experience/mark_notification_read", "id=missing&application=blog", Some(token), Some(token)),
        "job-form-session"
      )).unsafeRunSync()

      Then("the live command path keeps its response private even when the provider rejects the request")
      _cache(response) shouldBe Some("private, no-store")
      withClue(if (response.status.code == 503) "" else s"H3 fresh-CSRF provider failure response body: ${response.as[String].unsafeRunSync()}") {
        response.status.code shouldBe 503
      }
    }

    "H3 obtain the real detail CSRF cookie and submit successful canonical control PRG including changed=false replay" in {
      Given("a control-capable authenticated owner, a submitted Job, and its rendered detail form")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-control-session", "job-control-owner", Map("role" -> "content_manager", "privilege" -> "application_content_manager")).copy(capabilities = Vector("content_manager", "content_admin")))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(
        principal = new Principal { def id: PrincipalId = PrincipalId("job-control-owner"); def attributes: Map[String, String] = Map("authenticated" -> "true") },
        session = Some(SessionContext(sessionId = Some("job-control-session")))
      ))
      val id = subsystem.jobEngine.submit(
        Nil,
        owner,
        JobSubmitOption(
          runMode = JobRunMode.Async,
          persistence = JobPersistencePolicy.Ephemeral,
          scheduledStartAt = Some(owner.clock.instant().plusSeconds(300L))
        )
      ).toOption.getOrElse(fail("submission failed"))
      subsystem.jobEngine.getStatus(id) shouldBe Some(JobStatus.Submitted)
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the browser extracts the issued CSRF token and posts Cancel twice to the installed command")
      val detail = app.run(_with_session(_get_request(s"/web/system/jobs/${id.value}"), "job-control-session")).unsafeRunSync()
      val html = detail.as[String].unsafeRunSync()
      val token = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("CSRF token missing"))
      val cookie = detail.headers.headers.find(_.name.toString.equalsIgnoreCase("Set-Cookie")).map(_.value.takeWhile(_ != ';').split("=", 2).last).getOrElse(fail("CSRF cookie missing"))
      val first = app.run(_with_session(_post_form_request("/form/job_control/job_experience/control_job_experience", s"id=${id.value}&scope=mine&command=Cancel", Some(token), Some(cookie)), "job-control-session")).unsafeRunSync()
      val replay = app.run(_with_session(_post_form_request("/form/job_control/job_experience/control_job_experience", s"id=${id.value}&scope=mine&command=Cancel", Some(token), Some(cookie)), "job-control-session")).unsafeRunSync()

      Then("both command responses use private 303 detail PRG while canonical state contains one cancellation transition")
      withClue(if (first.status.code == 303) "" else s"H3 control first response body: ${first.as[String].unsafeRunSync()}") {
        first.status.code shouldBe 303
      }
      withClue(if (replay.status.code == 303) "" else s"H3 control replay response body: ${replay.as[String].unsafeRunSync()}") {
        replay.status.code shouldBe 303
      }
      first.headers.get(CIString("Location")).map(_.head.value) shouldBe Some(s"/web/system/jobs/${id.value}")
      _cache(first) shouldBe Some("private, no-store")
      subsystem.jobEngine.getStatus(id) shouldBe Some(org.goldenport.cncf.job.JobStatus.Cancelled)
      subsystem.jobEngine.query(id).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 1
    }

    "H3 prefer the installed descriptor successRedirect over the safe scoped Job default PRG" in {
      Given("a control-capable owner, a submitted Job, and an explicit descriptor redirect for the installed command")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("h3-redirect", "h3-redirect-owner", Map("role" -> "content_manager", "privilege" -> "application_content_manager")).copy(capabilities = Vector("content_manager", "content_admin")))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(
        principal = new Principal { def id: PrincipalId = PrincipalId("h3-redirect-owner"); def attributes: Map[String, String] = Map.empty },
        session = Some(SessionContext(sessionId = Some("h3-redirect")))
      ))
      val id = subsystem.jobEngine.submit(
        Nil,
        owner,
        JobSubmitOption(
          runMode = JobRunMode.Async,
          persistence = JobPersistencePolicy.Ephemeral,
          scheduledStartAt = Some(owner.clock.instant().plusSeconds(300L))
        )
      ).toOption.getOrElse(fail("submission failed"))
      subsystem.jobEngine.getStatus(id) shouldBe Some(JobStatus.Submitted)
      val descriptor = WebDescriptor(form = Map(
        "job_control.job_experience.control_job_experience" -> WebDescriptor.Form(successRedirect = Some("/web/system/jobs?command=accepted"))
      ))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor))).routes(null).orNotFound

      When("the issued detail form CSRF values submit the real Cancel command")
      val detail = app.run(_with_session(_get_request(s"/web/system/jobs/${id.value}"), "h3-redirect")).unsafeRunSync()
      val html = detail.as[String].unsafeRunSync()
      val token = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("CSRF token missing"))
      val cookie = detail.headers.headers.find(_.name.toString.equalsIgnoreCase("Set-Cookie")).map(_.value.takeWhile(_ != ';').split("=", 2).last).getOrElse(fail("CSRF cookie missing"))
      val response = app.run(_with_session(_post_form_request("/form/job_control/job_experience/control_job_experience", s"id=${id.value}&scope=mine&command=Cancel", Some(token), Some(cookie)), "h3-redirect")).unsafeRunSync()

      Then("the successful command uses the descriptor redirect before the derived scoped default and keeps the response private")
      withClue(if (response.status.code == 303) "" else s"H3 explicit descriptor redirect response body: ${response.as[String].unsafeRunSync()}") {
        response.status.code shouldBe 303
      }
      response.headers.get(CIString("Location")).map(_.head.value) shouldBe Some("/web/system/jobs?command=accepted")
      _cache(response) shouldBe Some("private, no-store")
      subsystem.jobEngine.getStatus(id) shouldBe Some(org.goldenport.cncf.job.JobStatus.Cancelled)
    }

    "H4 reject stale or missing CSRF before a Job command changes provider state" in {
      Given("a Job command form without a CSRF token")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-stale-csrf-session", "job-stale-csrf-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the form reaches the real Http4s route without its browser token")
      val response = app.run(_with_session(
        _post_form_request("/form/job_control/job_experience/mark_notification_read", "id=missing", csrfvalue = None),
        "job-stale-csrf-session"
      )).unsafeRunSync()

      Then("the server rejects it before dispatch and marks the error private")
      response.status.code shouldBe 403
      _cache(response) shouldBe Some("private, no-store")
    }

    "H4 reject a stale control token before the real canonical Job counter and status can change" in {
      Given("a submitted owner Job and a form request whose hidden token disagrees with its cookie")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-stale-control", "job-stale-owner", Map("role" -> "content_manager", "privilege" -> "application_content_manager")).copy(capabilities = Vector("content_manager", "content_admin")))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(principal = new Principal { def id: PrincipalId = PrincipalId("job-stale-owner"); def attributes: Map[String, String] = Map.empty }, session = Some(SessionContext(sessionId = Some("job-stale-control")))))
      val id = subsystem.jobEngine.submit(
        Nil,
        owner,
        JobSubmitOption(
          runMode = JobRunMode.Async,
          scheduledStartAt = Some(owner.clock.instant().plusSeconds(300L))
        )
      ).toOption.getOrElse(fail("submission failed"))
      subsystem.jobEngine.getStatus(id) shouldBe Some(JobStatus.Submitted)
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("Cancel reaches Http4s with a forged token and a different CSRF cookie")
      val response = app.run(_with_session(_post_form_request("/form/job_control/job_experience/control_job_experience", s"id=${id.value}&scope=mine&command=Cancel", Some("forged"), Some("issued")), "job-stale-control")).unsafeRunSync()

      Then("the response is private denial and the submitted Job has no control transition")
      response.status.code shouldBe 403
      _cache(response) shouldBe Some("private, no-store")
      subsystem.jobEngine.getStatus(id) shouldBe Some(org.goldenport.cncf.job.JobStatus.Submitted)
      subsystem.jobEngine.query(id).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 0
    }

    "H5 keep the live notification inbox route private for the current session" in {
      Given("an authenticated user with no installed inbox provider")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-notification-session", "job-notification-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the user requests the global notification route")
      val response = app.run(_with_session(_get_request("/web/system/notifications"), "job-notification-session")).unsafeRunSync()

      Then("the route does not disclose a provider failure and cannot be shared by caches")
      response.status.code shouldBe 404
      _cache(response) shouldBe Some("private, no-store")
    }

    "H5 use the provider-backed global inbox GET and CSRF mark-read PRG without mutating on GET" in {
      Given("an installed component-owned inbox provider and an authenticated notification recipient")
      val subsystem = _job_experience_fixture_subsystem()
      val provider = _install_http_inbox_provider(subsystem)
      _install_job_session(subsystem, _session_summary("notice-owner", "notice-owner", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the browser loads the global inbox then posts its issued CSRF token to mark the exact notice read twice")
      val get = app.run(_with_session(_get_request("/web/system/notifications"), "notice-owner")).unsafeRunSync()
      val html = get.as[String].unsafeRunSync()
      val token = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(html).map(_.group(1)).getOrElse(fail("notification CSRF token missing"))
      val cookie = get.headers.headers.find(_.name.toString.equalsIgnoreCase("Set-Cookie")).map(_.value.takeWhile(_ != ';').split("=", 2).last).getOrElse(fail("notification CSRF cookie missing"))
      val unreadafterget = provider.read
      val first = app.run(_with_session(_post_form_request("/form/job_control/job_experience/mark_notification_read", s"id=${provider.entryId}", Some(token), Some(cookie)), "notice-owner")).unsafeRunSync()
      val replay = app.run(_with_session(_post_form_request("/form/job_control/job_experience/mark_notification_read", s"id=${provider.entryId}", Some(token), Some(cookie)), "notice-owner")).unsafeRunSync()

      Then("GET leaves provider read state unchanged and both successful commands redirect privately to the global inbox with one first read")
      get.status.code shouldBe 200
      html should include (provider.entryId)
      unreadafterget shouldBe false
      withClue(if (first.status.code == 303) "" else s"H5 global inbox first response body: ${first.as[String].unsafeRunSync()}") {
        first.status.code shouldBe 303
      }
      withClue(if (replay.status.code == 303) "" else s"H5 global inbox replay response body: ${replay.as[String].unsafeRunSync()}") {
        replay.status.code shouldBe 303
      }
      first.headers.get(CIString("Location")).map(_.head.value) shouldBe Some("/web/system/notifications")
      provider.read shouldBe true
      provider.markCalls shouldBe 2
      _cache(first) shouldBe Some("private, no-store")
    }

    "H5 render one stored application notification in global and application inboxes and retain the application PRG on first-read replay" in {
      Given("a provider-owned blog notification, authenticated recipient, and an installed blog Web application")
      val subsystem = _job_experience_fixture_subsystem()
      val provider = _install_http_inbox_provider(subsystem, Some("blog"))
      _install_job_session(subsystem, _session_summary("h5-app-owner", "h5-app-owner", Map("role" -> "user", "privilege" -> "user")))
      val descriptor = WebDescriptor(
        apps = Vector(WebDescriptor.App("blog"), WebDescriptor.App("system")),
        form = Map("job_control.job_experience.mark_notification_read" -> WebDescriptor.Form(enabled = Some(true), access = Some(WebDescriptor.Exposure.Protected)))
      )
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem, Some(descriptor))).routes(null).orNotFound

      When("global and application inbox pages load the same entry, the global page first marks it read, and the application page replays the exact command")
      val global = app.run(_with_session(_get_request("/web/system/notifications"), "h5-app-owner")).unsafeRunSync()
      val application = app.run(_with_session(_get_request("/web/blog/notifications"), "h5-app-owner")).unsafeRunSync()
      val globalhtml = global.as[String].unsafeRunSync()
      val applicationhtml = application.as[String].unsafeRunSync()
      val globaltoken = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(globalhtml).map(_.group(1)).getOrElse(fail("global notification CSRF token missing"))
      val token = """name="csrf" value="([^"]+)""".r.findFirstMatchIn(applicationhtml).map(_.group(1)).getOrElse(fail("application notification CSRF token missing"))
      val globalid = """name="id" value="([^"]+)""".r.findFirstMatchIn(globalhtml).map(_.group(1)).getOrElse(fail("global notification id missing"))
      val applicationid = """name="id" value="([^"]+)""".r.findFirstMatchIn(applicationhtml).map(_.group(1)).getOrElse(fail("application notification id missing"))
      val globalapplication = """name="application" value="([^"]*)""".r.findFirstMatchIn(globalhtml).map(_.group(1)).getOrElse(fail("global notification application missing"))
      val applicationvalue = """name="application" value="([^"]*)""".r.findFirstMatchIn(applicationhtml).map(_.group(1)).getOrElse(fail("application notification application missing"))
      val globalcookie = global.headers.headers.find(_.name.toString.equalsIgnoreCase("Set-Cookie")).map(_.value.takeWhile(_ != ';').split("=", 2).last).getOrElse(fail("global notification CSRF cookie missing"))
      val cookie = application.headers.headers.find(_.name.toString.equalsIgnoreCase("Set-Cookie")).map(_.value.takeWhile(_ != ';').split("=", 2).last).getOrElse(fail("application notification CSRF cookie missing"))
      val globalfirst = app.run(_with_session(_post_form_request("/form/job_control/job_experience/mark_notification_read", s"id=$globalid&application=$globalapplication", Some(globaltoken), Some(globalcookie)), "h5-app-owner")).unsafeRunSync()
      val applicationreplay = app.run(_with_session(_post_form_request("/form/job_control/job_experience/mark_notification_read", s"id=$applicationid&application=$applicationvalue", Some(token), Some(cookie)), "h5-app-owner")).unsafeRunSync()

      Then("both pages expose the same stored application-owned entry, its first read stays on the global route, and the application replay remains on its application route")
      global.status.code shouldBe 200
      application.status.code shouldBe 200
      globalhtml should include (provider.entryId)
      applicationhtml should include (provider.entryId)
      globalid shouldBe provider.entryId
      applicationid shouldBe provider.entryId
      globalapplication shouldBe ""
      applicationvalue shouldBe "blog"
      withClue(if (globalfirst.status.code == 303) "" else s"H5 application global first response body: ${globalfirst.as[String].unsafeRunSync()}") {
        globalfirst.status.code shouldBe 303
      }
      withClue(if (applicationreplay.status.code == 303) "" else s"H5 application replay response body: ${applicationreplay.as[String].unsafeRunSync()}") {
        applicationreplay.status.code shouldBe 303
      }
      globalfirst.headers.get(CIString("Location")).map(_.head.value) shouldBe Some("/web/system/notifications")
      applicationreplay.headers.get(CIString("Location")).map(_.head.value) shouldBe Some("/web/blog/notifications")
      provider.markCalls shouldBe 2
      provider.read shouldBe true
      Vector(global, application, globalfirst, applicationreplay).foreach(response => _cache(response) shouldBe Some("private, no-store"))
    }

    "H6 keep the live await route private for a missing or unauthorized Job" in {
      Given("an authenticated subject and the real await route")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-await-session", "job-await-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the subject awaits an absent Job through Http4s")
      val response = app.run(_with_session(
        _post_form_request("/web/system/jobs/missing/await", "", csrfvalue = None),
        "job-await-session"
      )).unsafeRunSync()

      Then("the response remains a private same-or-not-found outcome")
      response.status.code shouldBe 404
      _cache(response) shouldBe Some("private, no-store")
    }

    "H6 await a live owner Job but never permit another authenticated session to observe its result" in {
      Given("a completed owner Job and a separate authenticated session")
      val subsystem = _job_experience_fixture_subsystem()
      _install_sessions(subsystem, Vector(
        _session_summary("await-owner", "await-owner", Map("role" -> "user", "privilege" -> "user")),
        _session_summary("await-other", "await-other", Map("role" -> "user", "privilege" -> "user"))
      ))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(principal = new Principal { def id: PrincipalId = PrincipalId("await-owner"); def attributes: Map[String, String] = Map.empty }, session = Some(SessionContext(sessionId = Some("await-owner")))))
      val id = subsystem.jobEngine.submit(List(_success_task("await-safe")), owner, JobSubmitOption(runMode = JobRunMode.Sync)).toOption.getOrElse(fail("submission failed"))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the owner and other session use the real await path")
      val own = app.run(_with_session(_post_form_request(s"/web/system/jobs/${id.value}/await", "", csrfvalue = None), "await-owner")).unsafeRunSync()
      val foreign = app.run(_with_session(_post_form_request(s"/web/system/jobs/${id.value}/await", "", csrfvalue = None), "await-other")).unsafeRunSync()

      Then("the owner receives a private success while the foreign session receives only the private not-found boundary")
      own.status.code shouldBe 200
      foreign.status.code shouldBe 404
      _cache(own) shouldBe Some("private, no-store")
      _cache(foreign) shouldBe Some("private, no-store")
      foreign.as[String].unsafeRunSync() should not include ("await-safe")
    }

    "H7 route the polling REST request through the descriptor operation with locale input" in {
      Given("an authenticated Mine user and the server REST dispatcher")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-poll-session", "job-poll-user", Map("role" -> "user", "privilege" -> "user")))
      val id = JobId.generate()
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the detail polling URL supplies an explicit Japanese locale")
      val response = app.run(_with_session(
        _get_request(s"/rest/v1/job-control/job-experience/get-job-experience?id=${id.value}&scope=mine&lang=ja"),
        "job-poll-session"
      )).unsafeRunSync()

      Then("the actual descriptor route returns a bounded not-found response rather than a renderer-only result")
      response.status.code shouldBe 404
    }

    "H7 send the renderer-emitted polling URL to Http4s and receive a bounded Japanese JSON record" in {
      Given("a live owner Job, its Japanese first-paint document, and the installed REST dispatcher")
      val subsystem = _job_experience_fixture_subsystem(Configuration(Map(
        WebExecutionResolutionPolicy.DISPLAY_OVERRIDE_ENABLED_KEY -> ConfigurationValue.StringValue("true")
      )))
      _install_job_session(subsystem, _session_summary("poll-owner", "poll-owner", Map("role" -> "user", "privilege" -> "user")))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(principal = new Principal { def id: PrincipalId = PrincipalId("poll-owner"); def attributes: Map[String, String] = Map.empty }, session = Some(SessionContext(sessionId = Some("poll-owner")))))
      val id = subsystem.jobEngine.submit(
        Nil,
        owner,
        JobSubmitOption(
          runMode = JobRunMode.Async,
          scheduledStartAt = Some(owner.clock.instant().plusSeconds(300L))
        )
      ).toOption.getOrElse(fail("submission failed"))
      subsystem.jobEngine.getStatus(id) shouldBe Some(JobStatus.Submitted)
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the server renders locale=ja and the page's emitted poll URL is requested through the same session")
      val detail = app.run(_with_session(_get_request(s"/web/system/jobs/${id.value}?lang=ja"), "poll-owner")).unsafeRunSync()
      val html = detail.as[String].unsafeRunSync()
      val url = """data-poll-url="([^"]+)""".r.findFirstMatchIn(html).map(_.group(1).replace("&amp;", "&")).getOrElse(fail("poll URL missing"))
      val json = app.run(_with_session(_get_request(url), "poll-owner")).unsafeRunSync()

      Then("the actual REST record matches the emitted canonical URL, has bounded fields, and the initial page is localized")
      detail.status.code shouldBe 200
      html should include ("待機中")
      json.status.code shouldBe 200
      json.as[String].unsafeRunSync() should include (id.value)
      json.as[String].unsafeRunSync() should not include ("submitter")
      _cache(json) shouldBe Some("private, no-store")
    }

    "H7 render terminal and unavailable English/Japanese first paint without polling and return their real bounded REST JSON records" in {
      Given("a completed owner Job, a canonical terminal record whose result is unavailable, and the same installed Web and REST dispatchers")
      val subsystem = _job_experience_fixture_subsystem(Configuration(Map(
        WebExecutionResolutionPolicy.DISPLAY_OVERRIDE_ENABLED_KEY -> ConfigurationValue.StringValue("true")
      )))
      _install_job_session(subsystem, _session_summary("h7-terminal", "h7-terminal-owner", Map("role" -> "user", "privilege" -> "user")))
      val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("job_control is missing"))
      val base = component.logic.executionContext()
      val owner = ExecutionContext.withSecurityContext(base, base.security.copy(
        principal = new Principal { def id: PrincipalId = PrincipalId("h7-terminal-owner"); def attributes: Map[String, String] = Map.empty },
        session = Some(SessionContext(sessionId = Some("h7-terminal")))
      ))
      val id = subsystem.jobEngine.submit(List(_success_task("h7-terminal")), owner, JobSubmitOption(runMode = JobRunMode.Sync)).toOption.getOrElse(fail("terminal submission failed"))
      val unavailableid = JobId.generate()
      subsystem.jobEngine.asInstanceOf[InMemoryJobEngine].runtimeState.durableJobs.put(
        unavailableid,
        JobRecord(
          id = unavailableid,
          tasks = Nil,
          submittedContext = owner,
          status = JobStatus.Failed,
          result = None,
          persistence = JobPersistencePolicy.Persistent,
          runMode = JobRunMode.Sync,
          priority = 0,
          createdAt = Instant.EPOCH,
          updatedAt = Instant.EPOCH,
          taskReadModels = Vector.empty,
          timeline = Vector.empty,
          debug = JobDebugInfo(None, Map.empty, Vector.empty)
        )
      )
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("English and Japanese detail pages and the actual descriptor REST detail are requested for the terminal and unavailable Jobs")
      val english = app.run(_with_session(_get_request(s"/web/system/jobs/${id.value}?lang=en"), "h7-terminal")).unsafeRunSync()
      val japanese = app.run(_with_session(_get_request(s"/web/system/jobs/${id.value}?lang=ja"), "h7-terminal")).unsafeRunSync()
      val rest = app.run(_with_session(_get_request(s"/rest/v1/job-control/job-experience/get-job-experience?id=${id.value}&scope=mine"), "h7-terminal")).unsafeRunSync()
      val unavailableenglish = app.run(_with_session(_get_request(s"/web/system/jobs/${unavailableid.value}?lang=en"), "h7-terminal")).unsafeRunSync()
      val unavailablerest = app.run(_with_session(_get_request(s"/rest/v1/job-control/job-experience/get-job-experience?id=${unavailableid.value}&scope=mine"), "h7-terminal")).unsafeRunSync()
      val restbody = rest.as[String].unsafeRunSync()
      val unavailablerestbody = unavailablerest.as[String].unsafeRunSync()
      val root = parse(restbody).fold(error => fail(error.getMessage), identity)
      val unavailableroot = parse(unavailablerestbody).fold(error => fail(error.getMessage), identity)
      val record = root.hcursor.downField("data").focus.getOrElse(root)
      val unavailablerecord = unavailableroot.hcursor.downField("data").focus.getOrElse(unavailableroot)

      Then("terminal and unavailable first paint remain manual with no poll script while the actual JSON exposes only typed safe fields and availability")
      english.status.code shouldBe 200
      japanese.status.code shouldBe 200
      unavailableenglish.status.code shouldBe 200
      english.as[String].unsafeRunSync() should include ("Completed")
      japanese.as[String].unsafeRunSync() should include ("完了")
      unavailableenglish.as[String].unsafeRunSync() should include ("Result is no longer available.")
      Vector(english, japanese, unavailableenglish).foreach { response =>
        val html = response.as[String].unsafeRunSync()
        html should include ("data-poll-enabled=\"false\"")
        html should not include ("<script>")
        html should include (">Refresh<")
      }
      rest.status.code shouldBe 200
      record.hcursor.get[String]("job_id").toOption shouldBe Some(id.value)
      record.hcursor.get[String]("status").toOption shouldBe Some("Completed")
      record.hcursor.downField("result").get[String]("availability").toOption shouldBe Some("available")
      unavailablerest.status.code shouldBe 200
      unavailablerecord.hcursor.get[String]("job_id").toOption shouldBe Some(unavailableid.value)
      unavailablerecord.hcursor.downField("result").get[String]("availability").toOption shouldBe Some("unavailable")
      restbody should not include ("submitter")
      restbody should not include ("debug")
      _cache(rest) shouldBe Some("private, no-store")
      _cache(unavailablerest) shouldBe Some("private, no-store")
    }

    "H8 apply private no-store cache headers to all subject-associated Job Web responses" in {
      Given("an authenticated Mine request")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-cache-session", "job-cache-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("the Job list is served without JavaScript")
      val response = app.run(_with_session(_get_request("/web/system/jobs"), "job-cache-session")).unsafeRunSync()

      Then("the server-owned HTML is safe for a private browser cache only")
      response.status.code shouldBe 200
      _cache(response) shouldBe Some("private, no-store")
    }

    "H8 keep actual detail denial notification failure and anonymous ingress pages private and accessible" in {
      Given("an authenticated session, an anonymous request, and the real Job Http4s application")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("job-h8", "job-h8-user", Map("role" -> "user", "privilege" -> "user")))
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("missing detail unavailable notification and anonymous administration paths are requested")
      val detail = app.run(_with_session(_get_request("/web/system/jobs/missing"), "job-h8")).unsafeRunSync()
      val notifications = app.run(_with_session(_get_request("/web/system/notifications"), "job-h8")).unsafeRunSync()
      val anonymous = app.run(_get_request("/web/system/admin/jobs")).unsafeRunSync()

      Then("every subject-related error or denial is private no-store and renders an accessible HTML outcome")
      Vector(detail, notifications, anonymous).foreach(response => _cache(response) shouldBe Some("private, no-store"))
      detail.as[String].unsafeRunSync() should include ("role=\"alert\"")
      notifications.as[String].unsafeRunSync() should include ("role=\"alert\"")
    }

    "H8 mark admitted denied error REST form and notification responses private no-store with an accessible manual error fallback" in {
      Given("an authenticated owner, anonymous ingress, and the live Job HTTP application without a notification provider")
      val subsystem = _job_experience_fixture_subsystem()
      _install_job_session(subsystem, _session_summary("h8-boundary", "h8-boundary-owner", Map("role" -> "user", "privilege" -> "user")))
      val id = JobId.generate()
      val app = HttpRuntimeBindingAdmissionFixture.server(new HttpExecutionEngine(subsystem)).routes(null).orNotFound

      When("admitted list, denied exact REST, missing detail, invalid form, unavailable notification, and anonymous list ingress are exercised")
      val admittedweb = app.run(_with_session(_get_request("/web/system/jobs"), "h8-boundary")).unsafeRunSync()
      val admittedrest = app.run(_with_session(_get_request("/rest/v1/job-control/job-experience/list-my-jobs"), "h8-boundary")).unsafeRunSync()
      val deniedrest = app.run(_with_session(_get_request(s"/rest/v1/job-control/job-experience/get-job-experience?id=${id.value}&scope=mine"), "h8-boundary")).unsafeRunSync()
      val errordetail = app.run(_with_session(_get_request("/web/system/jobs/missing"), "h8-boundary")).unsafeRunSync()
      val formdenial = app.run(_with_session(_post_form_request("/form/job_control/job_experience/mark_notification_read", "id=missing", csrfvalue = None), "h8-boundary")).unsafeRunSync()
      val notificationerror = app.run(_with_session(_get_request("/web/system/notifications"), "h8-boundary")).unsafeRunSync()
      val anonymous = app.run(_get_request("/web/system/jobs")).unsafeRunSync()

      Then("every relevant outcome carries the subject-data cache policy, and the initial error remains accessible with a manual return path")
      admittedweb.status.code shouldBe 200
      admittedrest.status.code shouldBe 200
      deniedrest.status.code shouldBe 404
      errordetail.status.code shouldBe 404
      formdenial.status.code shouldBe 403
      notificationerror.status.code shouldBe 404
      anonymous.status.code shouldBe 404
      Vector(admittedweb, admittedrest, deniedrest, errordetail, formdenial, notificationerror, anonymous).foreach { response =>
        _cache(response) shouldBe Some("private, no-store")
      }
      errordetail.as[String].unsafeRunSync() should include ("role=\"alert\"")
      errordetail.as[String].unsafeRunSync() should include (">Return to Jobs<")
    }
  }

  private def _cache(response: org.http4s.Response[IO]): Option[String] =
    response.headers.get(CIString("Cache-Control")).map(_.head.value)

  private def _success_task(name: String): JobTask =
    new JobTask {
      val actionId: ActionId = ActionId.generate()
      override def operationName: Option[String] = Some(name)
      def run(ctx: ExecutionContext): TaskOutcome = {
        val _ = ctx
        TaskSucceeded(OperationResponse.Scalar(name))
      }
    }

  private def _job_experience_fixture_subsystem(
    configuration: Configuration = Configuration.empty
  ): Subsystem =
    _management_console_fixture_subsystem(
      Configuration(configuration.values.updated(
        SubsystemUserMode.CONFIGURATION_KEY,
        ConfigurationValue.StringValue("multi-user")
      ))
    )

  private def _install_job_session(
    subsystem: Subsystem,
    summary: AuthComponent.SessionSummary
  ): Unit =
    _install_sessions(subsystem, Vector(summary))

  private def _install_sessions(
    subsystem: Subsystem,
    sessions: Vector[AuthComponent.SessionSummary]
  ): Unit = {
    val knownsessions = sessions
    val provider = new AuthenticationProvider {
      override val name: String = "job-experience-authentication"

      private def _result(summary: AuthComponent.SessionSummary): AuthenticationResult =
        AuthenticationResult(
          principalId = summary.principalId.map(PrincipalId.apply).getOrElse(PrincipalId("anonymous")),
          attributes = summary.attributes,
          capabilities = SecurityContext.Privilege.fromName(summary.securityLevel)
            .map(_.capabilities)
            .getOrElse(Set.empty[Capability]) ++ summary.capabilities.map(Capability.apply).toSet,
          level = SecurityLevel(summary.securityLevel),
          subjectKind = SubjectKind.User,
          session = Some(SessionContext(sessionId = summary.sessionId))
        )

      override def authenticate(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
        request.sessionId match {
          case Some(sessionid) =>
            knownsessions.find(_.sessionId.contains(sessionid))
              .map(summary => Consequence.success(Some(_result(summary))))
              .getOrElse(Consequence.success(None))
          case None =>
            Consequence.success(None)
        }

      override def currentSession(request: AuthenticationRequest)(using ExecutionContext): Consequence[Option[AuthenticationResult]] =
        authenticate(request)
    }
    val componentid = ComponentId("org.goldenport.cncf.test.JobExperienceAuthentication")
    val component = new Component {
      override def authenticationProviders: Vector[AuthenticationProvider] = Vector(provider)
    }
    val initialized = component.initialize(ComponentInit(
      subsystem,
      Component.Core.create(
        componentid.name,
        componentid,
        ComponentInstanceId.default(componentid),
        org.goldenport.protocol.Protocol.empty,
        jobEngine = subsystem.jobEngine
      ),
      ComponentOrigin.Builtin
    )).withArtifactMetadata(Component.ArtifactMetadata(
      sourceType = "spec",
      name = "job-experience-authentication",
      version = "0.0.0",
      component = Some("job-experience-authentication")
    ))
    subsystem.add(Vector(initialized))
    val descriptor = subsystem.descriptor.getOrElse(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"),
      subsystemName = subsystem.name
    ))
    val existingauthentication = descriptor.security
      .flatMap(_.authentication)
      .getOrElse(GenericSubsystemAuthenticationBinding())
    val providerbinding = GenericSubsystemAuthenticationProviderBinding(
      name = "job-experience-authentication",
      component = "job-experience-authentication",
      enabled = Some(true)
    )
    val providers =
      if (existingauthentication.providers.exists(binding =>
        binding.name == providerbinding.name && binding.component == providerbinding.component
      )) existingauthentication.providers
      else existingauthentication.providers :+ providerbinding
    val authentication = existingauthentication.copy(
      convention = Some("disabled"),
      fallbackPrivilege = Some("disabled"),
      providers = providers
    )
    val security = descriptor.security
      .getOrElse(GenericSubsystemSecurityBinding())
      .copy(authentication = Some(authentication))
    subsystem.withDescriptor(descriptor.copy(security = Some(security)))
  }

  private final class HttpInboxProvider(application: Option[String] = None) extends UserNotificationInboxProvider {
    val name: String = "http-inbox"
    val entryId: String = "http-notice"
    var markCalls = 0
    private var _read = false
    private var _read_at: Option[java.time.Instant] = None
    def read: Boolean = _read
    private def _entry(query: UserNotificationInboxQuery) = UserNotificationInboxEntry(entryId, query.recipientUserId, application, "Job notice", "Completed", java.time.Instant.EPOCH, _read_at.getOrElse(java.time.Instant.EPOCH), _read_at, None, "http-dedupe", Some(application.fold("/web/system/jobs")(app => s"/web/$app/jobs")))
    def notify(request: UserNotificationRequest)(using ExecutionContext): Consequence[UserNotificationResult] = Consequence.success(UserNotificationResult(notificationId = Some(entryId)))
    def queryInbox(query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[UserNotificationInboxPage] = Consequence.success(UserNotificationInboxPage(query.recipientUserId, query.application, query.unreadOnly, Vector(_entry(query)).filter(entry => !query.unreadOnly || entry.readAt.isEmpty), if (query.unreadOnly && _read) 0 else 1, None))
    def getInboxEntry(id: String, query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[Option[UserNotificationInboxEntry]] = Consequence.success(Option.when(id == entryId)(_entry(query)))
    def markInboxRead(id: String, query: UserNotificationInboxQuery)(using context: ExecutionContext): Consequence[UserNotificationInboxReadResult] = synchronized { markCalls += 1; val changed = !_read; _read = true; if (changed) _read_at = Some(context.clock.instant()); Consequence.success(UserNotificationInboxReadResult(id, query.recipientUserId, application, _read_at, changed)) }
    def updateInboxEntry(id: String, query: UserNotificationInboxQuery, update: UserNotificationInboxUpdate)(using ExecutionContext): Consequence[UserNotificationInboxEntry] = Consequence.success(_entry(query))
  }

  private def _install_http_inbox_provider(subsystem: Subsystem, application: Option[String] = None): HttpInboxProvider = {
    val provider = new HttpInboxProvider(application)
    val component = new Component() { override def userNotificationProviders = Vector(provider) }
    val id = ComponentId("org.goldenport.cncf.test.HttpInbox")
    val initialized = component.initialize(ComponentInit(subsystem, Component.Core.create(id.name, id, ComponentInstanceId.default(id), org.goldenport.protocol.Protocol.empty, jobEngine = subsystem.jobEngine), ComponentOrigin.Builtin))
      .withArtifactMetadata(Component.ArtifactMetadata("spec", "http-inbox", "0.0.0", Some("http-inbox")))
    subsystem.add(Vector(initialized))
    val descriptor = subsystem.descriptor.getOrElse(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"),
      subsystemName = subsystem.name
    ))
    val usernotification = descriptor.runtime
      .flatMap(_.userNotification)
      .getOrElse(GenericSubsystemUserNotificationBinding())
    val providerbinding = GenericSubsystemUserNotificationProviderBinding(
      name = "http-inbox",
      component = "http-inbox",
      enabled = Some(true),
      priority = Some(100),
      isDefault = Some(true)
    )
    val notificationproviders =
      if (usernotification.providers.exists(binding =>
        binding.name == providerbinding.name && binding.component == providerbinding.component
      )) usernotification.providers
      else usernotification.providers :+ providerbinding
    val runtime = descriptor.runtime
      .getOrElse(GenericSubsystemRuntimeBinding())
      .copy(userNotification = Some(usernotification.copy(providers = notificationproviders)))
    val componentbindings =
      if (descriptor.componentBindings.exists(_.componentName == "http-inbox")) descriptor.componentBindings
      else descriptor.componentBindings :+ GenericSubsystemComponentBinding("http-inbox")
    subsystem.withDescriptor(descriptor.copy(
      componentBindings = componentbindings,
      runtime = Some(runtime)
    ))
    provider
  }
}
