package org.goldenport.cncf.usernotification

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.CountDownLatch
import scala.collection.mutable.ArrayBuffer
import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentId, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, Principal, PrincipalId}
import org.goldenport.cncf.event.{EventPublishOption, EventStore, ReceptionDomainEvent}
import org.goldenport.cncf.job.{JobStatus, JobSubmitOption}
import org.goldenport.cncf.subsystem.{GenericSubsystemComponentBinding, GenericSubsystemDescriptor, GenericSubsystemRuntimeBinding, GenericSubsystemUserNotificationBinding, GenericSubsystemUserNotificationProviderBinding, Subsystem}
import org.goldenport.cncf.testutil.ExecutionContextTestFixture
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.Protocol
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Provider-owned inbox runtime contracts. The fixture deliberately exposes the
 * SPI through the normal subsystem provider resolver, never through local state.
 *
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class UserNotificationInboxSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "User notification inbox query" should {
    "N1 keep provider-owned atomic dedupe identity stable by recipient and trigger" in {
      Given("one installed provider and repeated notification create requests")
      val (_, provider) = _fixture()
      val same = UserNotificationRequest("alice", "cncf.job", "in-app", "Done", "done", dedupeKey = Some("job-1:succeeded"))
      val differentrecipient = same.copy(recipientUserId = "bob")

      When("the same trigger is retried and a distinct recipient trigger is created")
      given ExecutionContext = ExecutionContext.create()
      val first = provider.notify(same)
      val replay = provider.notify(same)
      val distinct = provider.notify(differentrecipient)

      Then("the provider preserves the first identity for a replay and creates the distinct recipient entry")
      first.toOption.flatMap(_.notificationId) shouldBe replay.toOption.flatMap(_.notificationId)
      first.toOption.flatMap(_.notificationId) should not be distinct.toOption.flatMap(_.notificationId)
      provider.notifyCalls shouldBe 2
    }

    "N1 resolve the component-owned provider for create-or-upsert retries without moving inbox state into CNCF" in {
      Given("an installed inbox provider and one recipient trigger submitted through the normal provider resolver")
      val (context, provider) = _fixture()
      val request = UserNotificationRequest("alice", "cncf.job", "in-app", "Done", "done", dedupeKey = Some("job-2:succeeded"))

      When("the same create-or-upsert request is retried through the public runtime entry point")
      val first = UserNotificationProviderRuntime.notify(context, request)
      val replay = UserNotificationProviderRuntime.notify(context, request)

      Then("the component provider retains the first delivery identity and CNCF has performed no local inbox read mutation")
      first.toOption.flatMap(_.notificationId) shouldBe replay.toOption.flatMap(_.notificationId)
      provider.notifyCalls shouldBe 1
      provider.isRead shouldBe false
      provider.markCalls shouldBe 0
    }

    "N2 bound provider page requests" in {
      Given("an authoritative recipient and an oversized request")
      val query = UserNotificationInboxQuery("alice", Some("blog"), unreadOnly = true, limit = 101)

      When("the provider query is validated")
      val result = query.validate

      Then("the runtime rejects it before a provider can construct a page")
      result.isSuccess shouldBe false
    }

    "N2 bind real unread pages and Next cursors to provider caller query and snapshot without GET mutation" in {
      Given("an installed provider with a controlled unread entry and a provider-owned snapshot cursor")
      val (context, provider) = _fixture()

      When("the caller follows its page then reuses the cursor for another scope caller and snapshot")
      val first = UserNotificationInboxRuntime.list(context, None, unreadOnly = true, limit = 25, cursor = None)
      val cursor = first.toOption.flatMap(_.nextCursor).getOrElse(fail("provider must issue an opaque Next cursor"))
      val replay = UserNotificationInboxRuntime.list(context, None, unreadOnly = true, limit = 25, cursor = Some(cursor))
      val changedscope = UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = true, limit = 25, cursor = Some(cursor))
      val anothercaller = _caller(context, "bob")
      val changedcaller = UserNotificationInboxRuntime.list(anothercaller, None, unreadOnly = true, limit = 25, cursor = Some(cursor))
      provider.advanceSnapshot()
      val changedsnapshot = UserNotificationInboxRuntime.list(context, None, unreadOnly = true, limit = 25, cursor = Some(cursor))

      Then("only the original read-only query obtains its page while every changed binding is rejected without marking the Job notice read")
      first.toOption.map(page => page.entries.size -> page.totalCount) shouldBe Some(1 -> 1)
      replay.toOption.map(_.nextCursor) shouldBe Some(None)
      changedscope.isSuccess shouldBe false
      changedcaller.isSuccess shouldBe false
      changedsnapshot.isSuccess shouldBe false
      provider.isRead shouldBe false
      provider.markCalls shouldBe 0
    }

    "N2 remove a read notification from a later real unread page while preserving the global count" in {
      Given("an unread provider-owned entry and a fixed first-read clock")
      val (context, provider) = _fixture()

      When("the exact mark-read command completes before an unread GET page")
      val marked = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))
      val unread = UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = true, limit = 25, cursor = None)
      val all = UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 25, cursor = None)

      Then("the provider's read state affects only the requested unread projection")
      marked.toOption.map(_.changed) shouldBe Some(true)
      unread.toOption.map(page => page.entries.size -> page.totalCount) shouldBe Some(0 -> 0)
      all.toOption.map(page => page.entries.size -> page.totalCount) shouldBe Some(1 -> 1)
    }

    "N3 preserve provider replay read time after exact identity admission" in {
      Given("a provider entry and a controlled first-read clock")
      val (context, provider) = _fixture()

      When("the exact mark-read command is first submitted and then replayed")
      val first = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))
      val replay = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))

      Then("the runtime returns the provider's unchanged first-read value without a local mutation")
      first.toOption.map(_.changed) shouldBe Some(true)
      replay.toOption.map(_.changed) shouldBe Some(false)
      first.toOption.flatMap(_.readAt) shouldBe Some(Instant.EPOCH)
      replay.toOption.flatMap(_.readAt) shouldBe Some(Instant.EPOCH)
      provider.markCalls shouldBe 2
    }

    "N3 accept an ordinary provider first read and immutable replay across an advancing runtime clock" in {
      Given("an installed ordinary provider and a deterministic execution clock advancing one millisecond per read")
      val (base, provider) = _fixture()
      val context = ExecutionContextTestFixture.withClock(base, new AdvancingClock)

      When("the actual inbox runtime resolves the provider and submits a first mark followed by its replay")
      val first = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))
      val replay = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))

      Then("the provider-owned first timestamp is admitted within the operation interval and replay remains immutable")
      first.toOption.map(_.changed) shouldBe Some(true)
      replay.toOption.map(_.changed) shouldBe Some(false)
      first.toOption.flatMap(_.readAt) shouldBe Some(Instant.ofEpochMilli(101L))
      replay.toOption.flatMap(_.readAt) shouldBe Some(Instant.ofEpochMilli(101L))
      provider.markCalls shouldBe 2
    }

    "N3 reject a provider first-read result beyond an advancing runtime operation interval" in {
      Given("an installed provider that reports a future first-read timestamp through the actual resolver")
      val (base, provider) = _fixture(readresultmode = "future")
      val context = ExecutionContextTestFixture.withClock(base, new AdvancingClock)

      When("the current recipient submits the exact mark-read command once")
      val result = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))

      Then("the runtime refuses the future provider result after its single mutation call")
      result.isSuccess shouldBe false
      provider.markCalls shouldBe 1
    }

    "N3 reject a provider first-read result before an advancing runtime operation interval" in {
      Given("an installed provider that reports an epoch first-read timestamp through the actual resolver")
      val (base, provider) = _fixture(readresultmode = "past")
      val context = ExecutionContextTestFixture.withClock(base, new AdvancingClock)

      When("the current recipient submits the exact mark-read command once")
      val result = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))

      Then("the runtime refuses the past provider result after its single mutation call")
      result.isSuccess shouldBe false
      provider.markCalls shouldBe 1
    }

    "N3 reject an inconsistent exact read result after provider invocation" in {
      Given("a provider that returns a mark-read result for another recipient")
      val (context, provider) = _fixture(inconsistentread = true)

      When("the current recipient marks the exact entry read")
      val result = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))

      Then("the mismatch is unavailable rather than a cross-recipient read")
      result.isSuccess shouldBe false
      provider.markCalls shouldBe 1
    }

    "N3 reject missing and rewritten provider first-read timestamps without replacing stored identity" in {
      Given("an unread provider that omits its first timestamp and an already-read provider that rewrites it")
      val (nonecontext, noneprovider) = _fixture(readresultmode = "none")
      val (rewritecontext, rewriteprovider) = _fixture(read = true, readresultmode = "rewrite")

      When("each current recipient submits the exact provider-owned mark-read command once")
      val missing = UserNotificationInboxRuntime.markRead(nonecontext, noneprovider.entryId, Some("blog"))
      val rewritten = UserNotificationInboxRuntime.markRead(rewritecontext, rewriteprovider.entryId, Some("blog"))

      Then("neither missing nor rewritten timestamps cross the runtime boundary and each provider sees one command")
      missing.isSuccess shouldBe false
      rewritten.isSuccess shouldBe false
      noneprovider.markCalls shouldBe 1
      rewriteprovider.markCalls shouldBe 1
      rewriteprovider.isRead shouldBe true
    }

    "N3 reject a changed-false readback that rewrites the admitted entry creation identity" in {
      Given("an unread provider that reports a first read through changed=false and then rewrites its stored creation identity")
      val (context, provider) = _fixture(readresultmode = "readback-rewrite")

      When("the current recipient marks the exact entry read once")
      val result = UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog"))

      Then("the required readback rejects the rewritten immutable provider entry without a second mutation")
      result.isSuccess shouldBe false
      provider.markCalls shouldBe 1
    }

    "N3 serialize concurrent exact read replays at the provider while retaining the controlled first-read instant" in {
      Given("two simultaneous mark-read callers held at a component-owned provider scheduling gate")
      val (context, provider) = _fixture(concurrentreadgate = true)
      given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

      When("both callers pass the provider gate and submit the same exact read command")
      val left = Future(UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog")))
      val right = Future(UserNotificationInboxRuntime.markRead(context, provider.entryId, Some("blog")))
      val results = Await.result(Future.sequence(Vector(left, right)), 1.second)

      Then("exactly one controlled read changes provider state and its concurrent replay returns the same immutable read time")
      results.flatMap(_.toOption.map(_.changed)).toSet shouldBe Set(true, false)
      results.flatMap(_.toOption.flatMap(_.readAt)).toSet shouldBe Set(Instant.EPOCH)
      provider.markCalls shouldBe 2
      provider.isRead shouldBe true
    }

    "N4 deny inbox updates before a non-administrator provider mutation" in {
      Given("a current user without notification administration capability")
      val (context, provider) = _fixture()

      When("that subject tries to update a notification title")
      val result = UserNotificationInboxRuntime.update(context, provider.entryId, Some("blog"), UserNotificationInboxUpdate(title = Some("changed")))

      Then("the update is rejected and the provider update operation is not reached")
      result.isSuccess shouldBe false
      provider.updateCalls shouldBe 0
    }

    "N4 accept an administrator's bounded update while preserving provider identity" in {
      Given("an inbox provider and a notification-administrator execution context")
      val (base, provider) = _fixture()
      val elevated = ExecutionContext.withSecurityContext(base, ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager).security)

      When("the administrator updates the public notification title")
      val result = UserNotificationInboxRuntime.update(elevated, provider.entryId, Some("blog"), UserNotificationInboxUpdate(title = Some("Revised")))

      Then("the provider receives one bounded mutation and identity stays server-owned")
      result.toOption.map(_.title) shouldBe Some("Revised")
      result.toOption.map(_.id) shouldBe Some(provider.entryId)
      result.toOption.map(_.application) shouldBe Some(Some("blog"))
      provider.updateCalls shouldBe 1
    }

    "N4 reject a provider update that rewrites immutable recipient application or creation identity" in {
      Given("a notification administrator and a provider that changes an immutable update result field")
      val (base, provider) = _fixture(invalidupdate = true)
      val elevated = ExecutionContext.withSecurityContext(base, ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager).security)

      When("the administrator submits an otherwise valid title update")
      val result = UserNotificationInboxRuntime.update(elevated, provider.entryId, Some("blog"), UserNotificationInboxUpdate(title = Some("Revised")))

      Then("the complete rewritten provider result is unavailable instead of projecting a forged identity")
      result.isSuccess shouldBe false
      provider.updateCalls shouldBe 1
    }

    "N5 reject an unsafe provider page as a whole without leaking an entry" in {
      Given("a provider that supplies an external or encoded-traversal action link")
      val (context, _) = _fixture(unsafelink = true)

      When("the current user requests the inbox page")
      val result = UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 25, cursor = None)

      Then("the complete provider page is treated as unavailable")
      result.isSuccess shouldBe false
    }

    "N5 admit only safe local URL vectors and reject every unsafe page vector as a whole" in {
      Given("provider pages with relative application URLs and external encoded traversal network and control-character vectors")
      val safe = Vector("/web/blog/jobs/job-1", "/web/blog/jobs/job-1?tab=result#detail")
      val unsafe = Vector("https://outside.example/jobs/1", "//outside.example/jobs/1", "/web/%2e%2e/private", "/web/%252e%252e/private", "/web/%2fprivate", "/web/ok\\private", "/web/ok%00private")

      When("the inbox runtime projects every provider page through its URL admission boundary")
      val admitted = safe.map { url =>
        val (context, _) = _fixture(actionurl = Some(url))
        UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 25, cursor = None)
      }
      val rejected = unsafe.map { url =>
        val (context, _) = _fixture(actionurl = Some(url))
        UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 25, cursor = None)
      }

      Then("only bounded same-origin paths reach the public page and no unsafe entry is partially projected")
      admitted.forall(_.isSuccess) shouldBe true
      rejected.forall(!_.isSuccess) shouldBe true
    }

    "PV69-11 preserve admitted encoded links through real provider page get and update operations" in {
      Given("provider-owned entries carrying safe encoded filename Unicode literal-percent and query-only references")
      val safeurls = Vector(
        "/web/blog/jobs/release%2Ev1",
        "/web/blog/jobs/東京",
        "/web/blog/jobs/100%25complete",
        "/web/blog/jobs/job-1?return=%2Fresults%2Ejson#detail"
      )

      When("each original reference crosses the provider page exact-get and administrator update boundaries")
      val results = safeurls.map { url =>
        val (base, provider) = _fixture(actionurl = Some(url))
        val elevated = ExecutionContext.withSecurityContext(base, ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager).security)
        (
          UserNotificationInboxRuntime.list(base, Some("blog"), unreadOnly = false, limit = 25, cursor = None),
          UserNotificationInboxRuntime.get(base, provider.entryId, Some("blog")),
          UserNotificationInboxRuntime.update(elevated, provider.entryId, Some("blog"), UserNotificationInboxUpdate(actionUrl = Some(url)))
        )
      }

      Then("the original safe references remain unchanged in every public provider-runtime projection")
      results.zip(safeurls).foreach { case ((page, exact, updated), url) =>
        page.toOption.flatMap(_.entries.headOption.flatMap(_.actionUrl)) shouldBe Some(url)
        exact.toOption.flatMap(_.actionUrl) shouldBe Some(url)
        updated.toOption.flatMap(_.actionUrl) shouldBe Some(url)
      }
    }

    "PV69-11 reject malformed traversal and deeper-than-bound links through actual provider boundaries" in {
      Given("provider-owned entries carrying external authority javascript traversal slash backslash control raw-space malformed-percent and nested attack references")
      val unsafeurls = Vector(
        "https://outside.example/jobs/1",
        "//outside.example/jobs/1",
        "javascript:alert(1)",
        "/web/../private",
        "/web/%2e%2e/private",
        "/web/%252e%252e/private",
        "/web/%2fprivate",
        "/web/%5cprivate",
        "/web/%00private",
        "/web/raw space",
        "/web/%",
        "/web/%2525252e%2525252e/private"
      )

      When("each reference is projected by page and exact-get and submitted to the bounded update entry")
      val results = unsafeurls.map { url =>
        val (base, provider) = _fixture(actionurl = Some(url))
        val elevated = ExecutionContext.withSecurityContext(base, ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager).security)
        (
          UserNotificationInboxRuntime.list(base, Some("blog"), unreadOnly = false, limit = 25, cursor = None),
          UserNotificationInboxRuntime.get(base, provider.entryId, Some("blog")),
          UserNotificationInboxRuntime.update(elevated, provider.entryId, Some("blog"), UserNotificationInboxUpdate(actionUrl = Some(url))),
          provider.updateCalls
        )
      }

      Then("no unsafe value reaches a page exact notification or provider update mutation")
      results.foreach { case (page, exact, updated, updatecalls) =>
        page.isSuccess shouldBe false
        exact.isSuccess shouldBe false
        updated.isSuccess shouldBe false
        updatecalls shouldBe 0
      }
    }

    "N5 reject foreign and malformed provider page identity vectors before returning any notification" in {
      Given("provider pages whose recipient application count or entry bound disagrees with the admitted query")
      val modes = Vector("foreign-recipient", "foreign-application", "invalid-count", "oversized")

      When("the current user requests the page from each malformed provider response")
      val results = modes.map { mode =>
        val (context, _) = _fixture(pagemode = mode)
        UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 1, cursor = None)
      }

      Then("every malformed page fails as one unavailable response without exposing a partial entry")
      results.forall(!_.isSuccess) shouldBe true
    }

    "N5 make expired and missing exact entries observationally equivalent" in {
      Given("one expired provider entry and one absent identifier")
      val (context, provider) = _fixture(expired = true)

      When("the current user reads each exact identifier")
      val expired = UserNotificationInboxRuntime.get(context, provider.entryId, Some("blog"))
      val missing = UserNotificationInboxRuntime.get(context, "missing", Some("blog"))

      Then("both exact requests fail without exposing expiry or ownership state")
      expired.isSuccess shouldBe false
      missing.isSuccess shouldBe false
    }

    "N5 make a foreign exact entry equivalent to missing without leaking its provider identity" in {
      Given("a provider that returns Alice's exact entry to a different authenticated caller")
      val (alice, _) = _fixture(fixedrecipient = Some("alice"))
      val bob = _caller(alice, "bob")

      When("Bob asks for the known provider identifier")
      val foreign = UserNotificationInboxRuntime.get(bob, "notice-1", Some("blog"))
      val missing = UserNotificationInboxRuntime.get(bob, "missing", Some("blog"))

      Then("both exact lookups fail at the same public admission boundary")
      foreign.isSuccess shouldBe false
      missing.isSuccess shouldBe false
    }

    "N5 keep public views free of recipient and deduplication data" in {
      Given("a provider-owned inbox entry with private identity metadata")
      val entry = UserNotificationInboxEntry("n-1", "alice", Some("blog"), "Done", "Completed", Instant.EPOCH, Instant.EPOCH, None, None, "job:1:succeeded", Some("/web/blog/jobs/1"), Map("private" -> "value"))

      When("the public view value is constructed")
      val view = UserNotificationInboxView(entry.id, entry.application, entry.title, entry.body, entry.createdAt, entry.updatedAt, entry.readAt, entry.expiresAt, entry.actionUrl, entry.status)

      Then("only the intended public fields are present")
      view.id shouldBe "n-1"
      view.application shouldBe Some("blog")
      view.title shouldBe "Done"
      view.status shouldBe "active"
    }

    "N6 report an unsupported legacy provider without creating local inbox state" in {
      Given("an execution context without an inbox-capable provider")
      val context = ExecutionContext.create()

      When("the current user lists notifications")
      val result = UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 25, cursor = None)

      Then("the outcome is structured as unavailable")
      result.isSuccess shouldBe false
      withClue(result.fold(conclusion => s"N6 unsupported provider status: ${conclusion.show}", _ => "N6 unsupported provider unexpectedly succeeded")) {
        result.fold(conclusion => conclusion.status.webCode.code, _ => 0) shouldBe 503
      }
    }

    "N6 convert a throwing provider into a safe unavailable result" in {
      Given("an installed inbox provider that throws during page retrieval")
      val (context, _) = _fixture(throwonquery = true)

      When("the user lists notifications")
      val result = UserNotificationInboxRuntime.list(context, Some("blog"), unreadOnly = false, limit = 25, cursor = None)

      Then("no provider exception reaches the public boundary")
      result.isSuccess shouldBe false
      withClue(result.fold(conclusion => s"N6 throwing provider status: ${conclusion.show}", _ => "N6 throwing provider unexpectedly succeeded")) {
        result.fold(conclusion => conclusion.status.webCode.code, _ => 0) shouldBe 503
      }
    }

    "N6/N7 retry refused failed and throwing EventBus forwarding until one provider-owned delivery without changing the Job" in {
      Given("a component-owned provider that refuses, fails, throws, and then accepts one repeated application Job event")
      val (component, subsystem, provider, sink) = _forwarding_fixture()
      val jobcontext = component.logic.executionContext()
      val jobid = subsystem.jobEngine.submit(
        Nil,
        jobcontext,
        JobSubmitOption(
          runMode = org.goldenport.cncf.job.JobRunMode.Async,
          scheduledStartAt = Some(jobcontext.clock.instant().plusSeconds(300L))
        )
      ).toOption.getOrElse(fail("Job submission failed"))
      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
      val event = ReceptionDomainEvent(
        name = "job.succeeded",
        kind = "job.succeeded",
        payload = Map(
          "job-id" -> jobid.value,
          "status" -> "succeeded",
          "job-run-mode" -> "async",
          "submitter-principal-id" -> "alice",
          "web.app" -> "blog",
          "message" -> "private Job failure body",
          "result" -> "private Job result"
        ),
        attributes = Map("correlation-id" -> "inbox-forwarding-correlation"),
        occurredAt = Instant.EPOCH
      )

      When("the identical lifecycle event is published five times through the real EventBus subscription")
      val attempts = Vector.fill(5)(subsystem.eventBus.publish(event, EventPublishOption(persistent = true)))
      val failures = subsystem.eventStore.query(EventStore.Query(name = Some("user-notification.forwarding.failed"))).toOption.getOrElse(Vector.empty)
      val sent = subsystem.eventStore.query(EventStore.Query(name = Some("user-notification.forwarding.sent"))).toOption.getOrElse(Vector.empty)

      Then("three failures remain retryable, two accepted events retain one provider delivery, and neither diagnostics nor Job state disclose private payload")
      attempts.forall(_.isSuccess) shouldBe true
      provider.attempts shouldBe 5
      sink should have size 1
      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
      failures should have size 3
      sent should have size 2
      (failures ++ sent).forall(record =>
        !record.payload.contains("message") &&
          !record.payload.contains("result") &&
          !record.payload.values.exists(value => value.toString.contains("private Job"))
      ) shouldBe true
    }

    "PV69-12 bound real EventBus forwarding presentation while preserving canonical recipient dedupe and Job state" in {
      Given("a descriptor-bound provider, one submitted Job, and safe plus oversized control and unsafe link event references")
      val (component, subsystem, provider, sink) = _forwarding_fixture()
      val jobcontext = component.logic.executionContext()
      val jobid = subsystem.jobEngine.submit(
        Nil,
        jobcontext,
        JobSubmitOption(
          runMode = org.goldenport.cncf.job.JobRunMode.Async,
          scheduledStartAt = Some(jobcontext.clock.instant().plusSeconds(300L))
        )
      ).toOption.getOrElse(fail("Job submission failed"))
      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
      val safeevent = ReceptionDomainEvent(
        name = "job.succeeded",
        kind = "job.succeeded",
        payload = Map(
          "job-id" -> jobid.value,
          "job-run-mode" -> "async",
          "submitter-principal-id" -> "alice",
          "web.app" -> "blog"
        ),
        attributes = Map.empty,
        occurredAt = Instant.EPOCH
      )
      val unsafeevents = Vector(
        ("bad/app", "bad-app-id", "service", "operation"),
        ("blog", "..", "service", "operation"),
        ("blog", "bad/id", "service", "operation"),
        ("blog", "x" * 257, "\u0000" + ("service" * 32), "operation" * 32),
        (" Blog ", "noncanonical-app-id", "service", "operation"),
        ((" " * 260) + "blog" + (" " * 260), "oversized-app-id", "service", "operation")
      ).map { case (app, id, service, operation) =>
        ReceptionDomainEvent(
          name = "job.succeeded",
          kind = "job.succeeded",
          payload = Map(
            "job-id" -> id,
            "job-run-mode" -> "async",
            "submitter-principal-id" -> "alice",
            "web.app" -> app,
            "web.service" -> service,
            "web.operation" -> operation
          ),
          attributes = Map.empty,
          occurredAt = Instant.EPOCH
        )
      }

      When("the safe event is retried through the real EventBus and each unsafe reference is forwarded once")
      val safeattempts = Vector.fill(4)(subsystem.eventBus.publish(safeevent, EventPublishOption(persistent = true)))
      val unsafeattempts = unsafeevents.map(event => subsystem.eventBus.publish(event, EventPublishOption(persistent = true)))
      val saferequest = sink.find(_.dedupeKey.contains(s"cncf.job:${jobid.value}:succeeded")).getOrElse(fail("safe event was not delivered"))
      val unsaferequests = sink.filterNot(_.dedupeKey.contains(s"cncf.job:${jobid.value}:succeeded"))

      Then("safe links retain provider-owned retry semantics while all forwarded public text and metadata are bounded and unsafe links are absent")
      safeattempts.forall(_.isSuccess) shouldBe true
      unsafeattempts.forall(_.isSuccess) shouldBe true
      provider.attempts shouldBe 10
      saferequest.recipientUserId shouldBe "alice"
      saferequest.dedupeKey shouldBe Some(s"cncf.job:${jobid.value}:succeeded")
      saferequest.actionUrl shouldBe Some(s"/web/blog/jobs/${jobid.value}")
      unsaferequests should have size 6
      unsaferequests.foreach { request =>
        request.recipientUserId shouldBe "alice"
        request.title.length should be <= 256
        request.body.length should be <= 4096
        request.title.exists(Character.isISOControl) shouldBe false
        request.body.exists(Character.isISOControl) shouldBe false
        request.metadata.values.forall(value => value.nonEmpty && value.length <= 256 && !value.exists(Character.isISOControl)) shouldBe true
        request.actionUrl shouldBe None
      }
      unsaferequests.find(_.dedupeKey.contains("cncf.job:..:succeeded")).flatMap(_.metadata.get("jobId")) shouldBe Some("..")
      unsaferequests.find(_.dedupeKey.exists(_.contains("x" * 257))).flatMap(_.metadata.get("jobId")) shouldBe None
      unsaferequests.find(_.dedupeKey.contains("cncf.job:noncanonical-app-id:succeeded")).map(_.recipientUserId) shouldBe Some("alice")
      unsaferequests.find(_.dedupeKey.contains("cncf.job:oversized-app-id:succeeded")).map(_.recipientUserId) shouldBe Some("alice")
      subsystem.jobEngine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
    }
  }

  private final class InboxProvider(
    read: Boolean,
    unsafelink: Boolean,
    throwonquery: Boolean,
    inconsistentread: Boolean,
    expired: Boolean,
    actionurl: Option[String],
    pagemode: String,
    fixedrecipient: Option[String],
    invalidupdate: Boolean,
    concurrentreadgate: Boolean,
    readresultmode: String
  ) extends UserNotificationInboxProvider {
    val name: String = "inbox-provider"
    val entryId: String = "notice-1"
    var markCalls: Int = 0
    var updateCalls: Int = 0
    var notifyCalls: Int = 0
    private var _read = read
    private var _read_at: Option[Instant] = Option.when(read)(Instant.EPOCH)
    private var _title = "Completed"
    private var _snapshot = "snapshot-1"
    private val _notifications = TrieMap.empty[(String, String), UserNotificationResult]
    private val _read_gate = new CountDownLatch(if (concurrentreadgate) 2 else 0)

    def isRead: Boolean = _read
    def advanceSnapshot(): Unit = _snapshot = "snapshot-2"

    def notify(request: UserNotificationRequest)(using ExecutionContext): Consequence[UserNotificationResult] = synchronized {
      val key = request.recipientUserId -> request.dedupeKey.getOrElse(java.util.UUID.randomUUID.toString)
      Consequence.success(_notifications.getOrElseUpdate(key, {
        notifyCalls += 1
        UserNotificationResult(notificationId = Some(s"sent-$notifyCalls"))
      }))
    }

    def queryInbox(query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[UserNotificationInboxPage] = {
      if (throwonquery) throw new IllegalStateException("provider failure")
      val expected = _cursor(query)
      if (query.cursor.exists(_ != expected)) {
        Consequence.operationIllegal("inbox.cursor", "cursor does not belong to this provider query snapshot")
      } else {
        val entries = if (query.unreadOnly && _read) Vector.empty else Vector(_entry(query))
        val next = Option.when(query.cursor.isEmpty && entries.nonEmpty)(expected)
        val page = UserNotificationInboxPage(query.recipientUserId, query.application, query.unreadOnly, entries, entries.size, next)
        pagemode match {
          case "foreign-recipient" => Consequence.success(page.copy(recipientUserId = "foreign-recipient"))
          case "foreign-application" => Consequence.success(page.copy(application = Some("other-app")))
          case "invalid-count" => Consequence.success(page.copy(totalCount = -1))
          case "oversized" => Consequence.success(page.copy(entries = Vector.fill(query.limit + 1)(_entry(query)), totalCount = query.limit + 1))
          case _ => Consequence.success(page)
        }
      }
    }

    def getInboxEntry(id: String, query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[Option[UserNotificationInboxEntry]] =
      Consequence.success(Option.when(id == entryId)(_entry(query)))

    def markInboxRead(id: String, query: UserNotificationInboxQuery)(using ExecutionContext): Consequence[UserNotificationInboxReadResult] = {
      if (concurrentreadgate) {
        _read_gate.countDown()
        _read_gate.await()
      }
      synchronized {
      markCalls += 1
      val changed = !_read
      _read = true
      val recipient = if (inconsistentread) "other-recipient" else query.recipientUserId
      if (changed) _read_at = Some(summon[ExecutionContext].clock.instant())
      val readat = readresultmode match {
        case "none" => None
        case "rewrite" => Some(Instant.ofEpochMilli(1L))
        case "future" => Some(summon[ExecutionContext].clock.instant().plusSeconds(30L))
        case "past" => Some(Instant.EPOCH)
        case _ => _read_at
      }
      val resultchanged = if (readresultmode == "readback-rewrite") false else changed
      Consequence.success(UserNotificationInboxReadResult(id, recipient, query.application, readat, resultchanged))
      }
    }

    def updateInboxEntry(id: String, query: UserNotificationInboxQuery, update: UserNotificationInboxUpdate)(using ExecutionContext): Consequence[UserNotificationInboxEntry] = {
      updateCalls += 1
      update.title.foreach(value => _title = value)
      Consequence.success(if (invalidupdate) _entry(query).copy(recipientUserId = "rewritten-recipient") else _entry(query))
    }

    private def _entry(query: UserNotificationInboxQuery): UserNotificationInboxEntry =
      val entry = UserNotificationInboxEntry(
        entryId,
        fixedrecipient.getOrElse(query.recipientUserId),
        Some("blog"),
        _title,
        "The Job completed.",
        Instant.EPOCH,
        Instant.EPOCH,
        _read_at,
        Option.when(expired)(Instant.EPOCH),
        "provider-owned-dedupe",
        actionurl.orElse(Some(if (unsafelink) "/%252e%252e/private" else "/web/blog/jobs/job-1")),
        Map("provider" -> "private"),
        "active"
      )
      if (readresultmode == "readback-rewrite" && markCalls > 0)
        entry.copy(createdAt = Instant.ofEpochMilli(-1L))
      else entry

    private def _cursor(query: UserNotificationInboxQuery): String =
      s"${query.recipientUserId}|${query.application.getOrElse("global")}|${query.unreadOnly}|${query.limit}|$_snapshot"
  }

  private final class RetryingForwardingProvider(
    val name: String,
    sink: ArrayBuffer[UserNotificationRequest]
  ) extends UserNotificationProvider {
    var attempts: Int = 0
    private val _delivered = TrieMap.empty[(String, String), UserNotificationResult]

    def notify(request: UserNotificationRequest)(using ExecutionContext): Consequence[UserNotificationResult] = synchronized {
      attempts += 1
      attempts match {
        case 1 => Consequence.success(UserNotificationResult(accepted = false))
        case 2 => Consequence.operationIllegal("notification.provider", "provider refused request")
        case 3 => throw new IllegalStateException("provider transport unavailable")
        case _ =>
          val dedupe = request.recipientUserId -> request.dedupeKey.getOrElse(fail("forwarded Job notifications require a dedupe key"))
          Consequence.success(_delivered.getOrElseUpdate(dedupe, {
            sink += request
            UserNotificationResult(notificationId = Some("retry-delivered"))
          }))
      }
    }
  }

  private def _fixture(
    read: Boolean = false,
    unsafelink: Boolean = false,
    throwonquery: Boolean = false,
    inconsistentread: Boolean = false,
    expired: Boolean = false,
    actionurl: Option[String] = None,
    pagemode: String = "normal",
    fixedrecipient: Option[String] = None,
    invalidupdate: Boolean = false,
    concurrentreadgate: Boolean = false,
    readresultmode: String = "normal"
  ): (ExecutionContext, InboxProvider) = {
    val subsystem = Subsystem("inbox-spec", configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))
    val provider = new InboxProvider(read, unsafelink, throwonquery, inconsistentread, expired, actionurl, pagemode, fixedrecipient, invalidupdate, concurrentreadgate, readresultmode)
    val component = new Component() {
      override def userNotificationProviders: Vector[UserNotificationProvider] = Vector(provider)
    }
    val id = ComponentId("org.goldenport.cncf.test.InboxProvider")
    val initialized = component.initialize(ComponentInit(
      subsystem,
      Component.Core.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty, jobEngine = subsystem.jobEngine),
      ComponentOrigin.Builtin
    )).withArtifactMetadata(Component.ArtifactMetadata(
      sourceType = "spec",
      name = "inbox-provider",
      version = "0.0.0",
      component = Some("inbox-provider")
    ))
    subsystem.add(Vector(initialized))
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"),
      subsystemName = "inbox-spec",
      componentBindings = Vector(GenericSubsystemComponentBinding("inbox-provider")),
      runtime = Some(GenericSubsystemRuntimeBinding(userNotification = Some(GenericSubsystemUserNotificationBinding(
        providers = Vector(GenericSubsystemUserNotificationProviderBinding(
          name = "inbox-provider",
          component = "inbox-provider",
          enabled = Some(true),
          priority = Some(100),
          isDefault = Some(true)
        ))
      ))))
    ))
    ExecutionContextTestFixture.withClock(initialized.logic.executionContext(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) -> provider
  }

  private final class AdvancingClock extends Clock {
    private var _current = Instant.ofEpochMilli(100L)

    override def getZone: ZoneId = ZoneOffset.UTC

    override def withZone(zone: ZoneId): Clock = this

    override def instant(): Instant = synchronized {
      val result = _current
      _current = _current.plusMillis(1L)
      result
    }
  }

  private def _forwarding_fixture(): (Component, Subsystem, RetryingForwardingProvider, ArrayBuffer[UserNotificationRequest]) = {
    val subsystem = Subsystem("inbox-forwarding-spec", configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty))
    val sink = ArrayBuffer.empty[UserNotificationRequest]
    val provider = new RetryingForwardingProvider("inbox-forwarding-provider", sink)
    val component = new Component() {
      override def userNotificationProviders: Vector[UserNotificationProvider] = Vector(provider)
    }
    val id = ComponentId("org.goldenport.cncf.test.InboxForwardingProvider")
    val initialized = component.initialize(ComponentInit(
      subsystem,
      Component.Core.create(id.name, id, ComponentInstanceId.default(id), Protocol.empty, jobEngine = subsystem.jobEngine),
      ComponentOrigin.Builtin
    )).withArtifactMetadata(Component.ArtifactMetadata("spec", "inbox-forwarding-provider", "0.0.0", Some("inbox-forwarding-provider")))
    subsystem.add(Vector(initialized))
    subsystem.withDescriptor(GenericSubsystemDescriptor(
      path = java.nio.file.Path.of("<memory>"),
      subsystemName = "inbox-forwarding-spec",
      componentBindings = Vector(GenericSubsystemComponentBinding("inbox-forwarding-provider")),
      runtime = Some(GenericSubsystemRuntimeBinding(userNotification = Some(GenericSubsystemUserNotificationBinding(
        providers = Vector(GenericSubsystemUserNotificationProviderBinding(
          name = "inbox-forwarding-provider", component = "inbox-forwarding-provider", enabled = Some(true), priority = Some(100), isDefault = Some(true)
        ))
      ))))
    ))
    (initialized, subsystem, provider, sink)
  }

  private def _caller(base: ExecutionContext, principal: String): ExecutionContext =
    ExecutionContext.withSecurityContext(base, base.security.copy(principal = new Principal {
      def id: PrincipalId = PrincipalId(principal)
      def attributes: Map[String, String] = Map("authenticated" -> "true")
    }))
}
