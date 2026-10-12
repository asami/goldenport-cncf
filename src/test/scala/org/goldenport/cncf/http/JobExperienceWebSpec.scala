package org.goldenport.cncf.http

import java.time.Instant
import org.goldenport.cncf.job.{JobDataOrigin, JobExperienceEntry, JobExperiencePage, JobExperienceProgress, JobExperienceResult, JobExperienceScope, JobExperienceStatus, JobExperienceView, JobExperienceVocabulary, JobId, JobManagementCursor, JobManagementDetail, JobManagementRetrySummary, JobManagementSummary, JobPersistencePolicy, JobResultSummary, JobRetryKind, JobStatus}
import org.goldenport.cncf.usernotification.{UserNotificationInboxView, UserNotificationInboxViewPage}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceWebSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Job experience Web renderer" should {
    "emit escaped accessible detail with a CSRF-protected canonical control" in {
      Given("an active Job detail with text that must be escaped")
      val view = _view(JobStatus.Running, JobExperienceResult.Available("<safe>"))

      When("the initial no-JavaScript document is rendered")
      val html = JobExperienceWebRenderer.detail(view, JobExperienceScope.Mine(Some("blog")), "csrf-token")

      Then("the status, progress, manual refresh, and form remain accessible without raw text injection")
      html should include ("&lt;safe&gt;")
      html should include ("aria-live=\"polite\"")
      html should include ("Refresh")
      html should include ("name=\"csrf\"")
      html should include ("/form/job_control/job_experience/control_job_experience")
    }

    "W1 render escaped list, notification, empty, and opaque Next states" in {
      Given("a scoped page containing presentation text and an opaque continuation")
      val id = JobId.generate()
      val entry = JobExperienceEntry(id, JobStatus.Running, JobExperienceStatus.Running, JobExperienceVocabulary("Running", "<next>"), JobPersistencePolicy.Persistent, JobDataOrigin.Runtime, Instant.EPOCH, Instant.EPOCH)
      val jobs = JobExperiencePage(Vector(entry), 2, Some(JobManagementCursor.fromOpaque("next token")))
      val notifications = UserNotificationInboxViewPage(Vector(UserNotificationInboxView("notice", Some("blog"), "<title>", "<body>", Instant.EPOCH, Instant.EPOCH, None, None, Some("/web/blog/jobs/a"), "active")), 1, None)

      When("the initial list and notification documents are rendered")
      val list = JobExperienceWebRenderer.list(jobs, JobExperienceScope.Mine(Some("blog")), Map("status" -> "running", "origin" -> "runtime", "persistentOnly" -> "false", "limit" -> "25"))
      val inbox = JobExperienceWebRenderer.notifications(notifications, Some("blog"), Map.empty, "csrf-token")
      val empty = JobExperienceWebRenderer.list(JobExperiencePage(Vector.empty, 0, None), JobExperienceScope.Mine(None), Map.empty)
      val error = JobExperienceWebRenderer.error("<unavailable>", "/web/system/jobs")

      Then("text is escaped, filters survive Next, and empty state remains usable without script")
      list should include ("&lt;next&gt;")
      list should include ("status=running")
      list should include ("origin=runtime")
      list should include ("persistentOnly=false")
      list should include ("limit=25")
      list should include ("cursor=next%20token")
      inbox should include ("&lt;title&gt;")
      inbox should include ("name=\"csrf\"")
      empty should include ("No admitted jobs are available.")
      error should include ("&lt;unavailable&gt;")
    }

    "W2 retain labels captions scoped headers manual refresh and localized first paint" in {
      Given("a Japanese active Job detail")
      val view = _view(JobStatus.Running, JobExperienceResult.Pending).copy(vocabulary = JobExperienceVocabulary.resolve(JobStatus.Running, java.util.Locale.JAPANESE))

      When("the no-JavaScript detail is rendered")
      val html = JobExperienceWebRenderer.detail(view, JobExperienceScope.Mine(None), "csrf-token")

      Then("the document supplies the accessible controls and localized state before polling")
      html should include ("実行中")
      html should include ("aria-live=\"polite\"")
      html should include (">Refresh<")
      html should include ("<section aria-label=\"Job controls\">")
      html should include ("<button type=\"submit\">")
    }

    "W1 render an active detail and terminal unavailable detail as complete initial HTML without relying on polling" in {
      Given("one active result-bearing Job and one terminal result-unavailable Job")
      val active = _view(JobStatus.Running, JobExperienceResult.Available("<private-result>"))
      val terminal = _view(JobStatus.Succeeded, JobExperienceResult.UnavailableAfterRestart).copy(refreshable = false, controls = Vector.empty)

      When("both documents are rendered before any browser script runs")
      val activehtml = JobExperienceWebRenderer.detail(active, JobExperienceScope.Mine(Some("blog")), "csrf-token")
      val terminalhtml = JobExperienceWebRenderer.detail(terminal, JobExperienceScope.Mine(Some("blog")), "csrf-token")

      Then("the active page has escaped initial result and manual fallback while terminal availability carries no poll script")
      activehtml should include ("&lt;private-result&gt;")
      activehtml should include ("data-poll-enabled=\"true\"")
      activehtml should include (">Refresh<")
      terminalhtml should include ("Result is unavailable after restart.")
      terminalhtml should include ("data-poll-enabled=\"false\"")
      terminalhtml should not include ("AbortController")
    }

    "bound polling to active visible details only" in {
      Given("a fresh polling state")
      val state = JobExperiencePolling.State()

      When("an active visible detail is admitted and then becomes terminal")
      val admitted = JobExperiencePolling.mayPoll(state, JobExperiencePolling.Observation(active = true))
      val terminal = JobExperiencePolling.observed(JobExperiencePolling.started(state), JobExperiencePolling.Observation(active = true, terminal = true))

      Then("one request is allowed before the terminal observation stops it")
      admitted shouldBe true
      JobExperiencePolling.mayPoll(terminal, JobExperiencePolling.Observation(active = true)) shouldBe false
      JobExperiencePolling.maximumAttempts shouldBe 60
      JobExperiencePolling.intervalMillis shouldBe 5000L
      JobExperiencePolling.timeoutMillis shouldBe 5000L
    }

    "W3 stop polling for pause hidden unload timeout redirect and invalid observations" in {
      Given("one in-flight active polling request")
      val started = JobExperiencePolling.started(JobExperiencePolling.State())

      When("each terminal browser or response observation is recorded")
      val paused = JobExperiencePolling.observed(started, JobExperiencePolling.Observation(active = true, paused = true))
      val hidden = JobExperiencePolling.observed(started, JobExperiencePolling.Observation(active = true, hidden = true))
      val unloading = JobExperiencePolling.observed(started, JobExperiencePolling.Observation(active = true, unloading = true))
      val rejected = JobExperiencePolling.observed(started, JobExperiencePolling.Observation(active = true, responseAccepted = false))

      Then("no second request is admitted until a fresh manual page load")
      Vector(paused, hidden, unloading, rejected).foreach { state =>
        JobExperiencePolling.mayPoll(state, JobExperiencePolling.Observation(active = true)) shouldBe false
      }
      JobExperiencePolling.mayPoll(started, JobExperiencePolling.Observation(active = true)) shouldBe false
      JobExperiencePolling.mayPoll(JobExperiencePolling.resumed(paused), JobExperiencePolling.Observation(active = true)) shouldBe true
      JobExperiencePolling.mayPoll(JobExperiencePolling.State(attempts = JobExperiencePolling.maximumAttempts), JobExperiencePolling.Observation(active = true)) shouldBe false
      JobExperiencePolling.mayPoll(JobExperiencePolling.State(), JobExperiencePolling.Observation(active = false)) shouldBe false
    }

    "W3 enforce original attempt budget across pause and resume while keeping Submitted and Running the only pollable states" in {
      Given("fresh active Submitted and Running observations plus a paused near-budget state")
      val submitted = JobExperiencePolling.Observation(active = true)
      val running = JobExperiencePolling.Observation(active = true)
      val paused = JobExperiencePolling.observed(JobExperiencePolling.State(attempts = JobExperiencePolling.maximumAttempts - 1), JobExperiencePolling.Observation(active = true, paused = true))

      When("the browser resumes before and after the fixed attempt budget and observes redirect invalid timeout errors")
      val resumed = JobExperiencePolling.resumed(paused)
      val budgetexhausted = JobExperiencePolling.started(resumed)
      val redirect = JobExperiencePolling.observed(JobExperiencePolling.started(JobExperiencePolling.State()), JobExperiencePolling.Observation(active = true, responseAccepted = false))
      val invalidjson = JobExperiencePolling.observed(JobExperiencePolling.started(JobExperiencePolling.State()), JobExperiencePolling.Observation(active = true, responseAccepted = false))
      val timeout = JobExperiencePolling.observed(JobExperiencePolling.started(JobExperiencePolling.State()), JobExperiencePolling.Observation(active = true, responseAccepted = false))

      Then("active states admit one in-flight request, while resume never resets the original attempt count and every invalid response stops")
      JobExperiencePolling.mayPoll(JobExperiencePolling.State(), submitted) shouldBe true
      JobExperiencePolling.mayPoll(JobExperiencePolling.State(), running) shouldBe true
      JobExperiencePolling.mayPoll(resumed, submitted) shouldBe true
      JobExperiencePolling.mayPoll(budgetexhausted, running) shouldBe false
      Vector(redirect, invalidjson, timeout).foreach(state => JobExperiencePolling.mayPoll(state, running) shouldBe false)
    }
  }

  private def _view(status: JobStatus, result: JobExperienceResult): JobExperienceView = {
    val id = JobId.generate()
    val summary = JobManagementSummary(id, status, JobPersistencePolicy.Persistent, JobDataOrigin.Runtime, Instant.EPOCH, Instant.EPOCH, None)
    val detail = JobManagementDetail(summary, JobManagementRetrySummary(JobRetryKind.None, 0, 3, None, false, false, false, false), JobResultSummary(status, success = false, message = None), 1, 0)
    JobExperienceView(detail, JobExperienceStatus.from(status), JobExperienceVocabulary.resolve(status, java.util.Locale.ENGLISH), JobExperienceProgress("indeterminate", 1), result, Vector(org.goldenport.cncf.job.JobControlCommand.Cancel), refreshable = true)
  }
}
