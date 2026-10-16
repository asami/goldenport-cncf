package org.goldenport.cncf.job

import java.time.Duration
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventRecord, ReceptionDomainEvent}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.observability.ObservabilityEngine
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobLifecycleOperationalAuditIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobLifecycleOperationalAuditFixtures.*

  "LI1 Real synchronous source conservation" should _scenario("LI1", "R1,R2,R3,R4,R8") {
    "preserve canonical Events status paid credits and provider counts across independent Nop and observing engines" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R2,R3,R4,R8; LI1: two independent deterministic native graphs and seed69709501 shrinking over actual Sync Nil submissions")
      val property = Prop.forAll(Gen.choose(1, 3)) { count =>
        val baseline = _sync_run(count, LogBackend.NopLogBackend)
        val backend = new RecordingBackend
        val observed = _sync_run(count, backend)
        observed shouldBe baseline
        val events = observed._1
        events.map(_.name) shouldBe Vector.fill(count)(Vector("job.submitted", "job.running", "job.succeeded")).flatten
        backend.calls.flatMap(_.attributes.getString("event-id")) shouldBe events.map(_.id.value)
        backend.calls.flatMap(_.attributes.getLong("event-sequence")) shouldBe events.map(_.sequence)
        backend.calls.zip(events).foreach { case (row, event) =>
          row.attributes.getString("job-id") shouldBe event.attributes.get("cncf.job.id")
          row.attributes.getString("occurred-at") shouldBe Some(event.createdAt.toString)
          row.attributes.getBoolean("event-identity-available") shouldBe Some(true)
          row.attributes.getString("status") shouldBe event.attributes.get("cncf.job.status").map(_.toLowerCase(java.util.Locale.ROOT))
          row.attributes.getString("run-mode") shouldBe Some("sync")
        }
        true
      }
      When("ScalaCheck performs at least 24 real paired native runs with the frozen seed and ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709501L)), property)
      Then("canonical values and native counters agree exactly and original acknowledgements supply every observed Event identity")
      withClue(s"LI1 seed69709501 $checked ") { checked.passed shouldBe true }
      checked.succeeded should be >= 24
    }
  }

  "LI2 Real delay and immediate retry" should _scenario("LI2", "R1,R2,R3,R8") {
    "retain native timer promotion and actual RetryNow body order with captured attempt counts" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R2,R3,R8; LI2: genuine manual future Nil Job and genuine RetryNow task")
      def _run_(backend: LogBackend) = withNative() { fixture =>
        withBackend(backend) {
          val delayed = fixture.submit(option = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, scheduledStartAt = Some(instant.plusSeconds(10))))
          fixture.timer.fireDue() shouldBe 0
          fixture.engine.drainAll() shouldBe 0
          fixture.store.stored.map(_.name) shouldBe Vector("job.submitted", "job.delayed.scheduled")
          fixture.timer.advanceBy(Duration.ofSeconds(10)) shouldBe 1
          fixture.engine.drainAll() shouldBe 1
          val task = new RetryTask
          val retried = fixture.submit(List(task))
          fixture.engine.drainAll() shouldBe 2
          task.calls.get() shouldBe 2
          fixture.engine.query(retried).get.retry.attemptCount shouldBe 1
          val facts = (fixture.store.stored, fixture.engine.getStatus(delayed), fixture.engine.getStatus(retried),
            fixture.engine.resourceSnapshot, fixture.engine.lifetimeSnapshot(delayed), fixture.engine.lifetimeSnapshot(retried), fixture.observations, task.calls.get())
          val before = fixture.observations
          fixture.engine.drainAll() shouldBe 0
          fixture.timer.fireDue() shouldBe 0
          fixture.observations shouldBe before
          facts
        }
      }
      When("the due timer fires and the real engine drains both original retry attempts")
      val baseline = _run_(LogBackend.NopLogBackend)
      val backend = new RecordingBackend
      val observed = _run_(backend)
      Then("audit adds no queue timer retry body clock or identity effects and preserves the original event and attempt order")
      observed shouldBe baseline
      backend.calls.flatMap(_.attributes.getString("event-kind")) shouldBe Vector("job.submitted", "job.delayed.scheduled",
        "job.delayed.enqueued", "job.running", "job.succeeded", "job.submitted", "job.running", "job.retry.immediate.submitted", "job.running", "job.succeeded")
      backend.calls.take(5).flatMap(_.attributes.getInt("attempt-count")) shouldBe Vector.fill(5)(0)
      backend.calls.drop(5).flatMap(_.attributes.getInt("attempt-count")) shouldBe Vector(0, 0, 1, 1, 1)
      backend.calls.flatMap(_.attributes.getString("event-id")) shouldBe observed._1.map(_.id.value)
    }
  }

  "LI3 Real controls facade and replay" should _scenario("LI3", "R1,R2,R4,R7,R8") {
    "apply Suspend Resume Cancel once refuse foreign control and preserve separate facade and native frames" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R2,R4,R7,R8; LI3: a genuine owner and foreign session on a native asynchronous Job")
      withNative() { fixture =>
        val jobid = withBackend(LogBackend.NopLogBackend)(fixture.submit())
        val service = new JobExperienceService(fixture.engine)
        val foreign = ExecutionContext.withSecurityContext(fixture.context,
          JobExperienceOperationalAuditFixtures.context("foreign", "foreign-session").security)
        val backend = new RecordingBackend
        When("owner controls apply and replay while a foreign caller is refused")
        withBackend(backend) {
          Vector(JobControlCommand.Suspend, JobControlCommand.Resume, JobControlCommand.Cancel).foreach { command =>
            val applied = service.control(jobid, JobExperienceScope.Mine(None), command)(using fixture.context)
            val after = fixture.facts(jobid)
            val replay = service.control(jobid, JobExperienceScope.Mine(None), command)(using fixture.context)
            val denied = service.control(jobid, JobExperienceScope.Mine(None), command)(using foreign)
            Then("only the applied command has a second native fact and all original response state and effects remain")
            applied.toOption.get.changed shouldBe true
            replay.toOption.get.changed shouldBe false
            applied.toOption.get.status shouldBe replay.toOption.get.status
            denied.isSuccess shouldBe false
            fixture.facts(jobid) shouldBe after
          }
        }
        Then("three native applied frames and nine unchanged facade frames share only their original admitted references")
        backend.calls.flatMap(_.attributes.getString("event-kind")) shouldBe Vector("job.suspended", "job.resumed", "job.cancelled")
        backend.experience.flatMap(_.attributes.getString("outcome")) shouldBe Vector.fill(3)(Vector("applied", "replayed", "failed")).flatten
        backend.events.map(_.attributes.getString("audit.kind").get) shouldBe
          Vector.fill(3)(Vector("job-lifecycle", "job-experience", "job-experience", "job-experience")).flatten
        backend.calls.flatMap(_.attributes.getString("event-id")) shouldBe fixture.store.stored.drop(1).map(_.id.value)
        fixture.store.stored.map(_.name) shouldBe Vector("job.submitted", "job.suspended", "job.resumed", "job.cancelled")
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Cancelled)
      }
    }
  }

  "LI4 Actual DefaultEventBus provenance" should _scenario("LI4", "R1,R2,R4,R8") {
    "persist and dispatch each original event once and retain ambiguous commit-before-response failure without bus identity" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R2,R4,R8; LI4: DefaultEventBus over real EventEngine and EventStore with native failed acknowledgement")
      withNative(useBus = true) { fixture =>
        val backend = new RecordingBackend
        When("a synchronous Job publishes through the real bus while both store and bus are configured")
        val jobid = withBackend(backend)(fixture.submit(option = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)))
        Then("bus priority persists and dispatches original native events once without an observer-created identity")
        fixture.store.appendCalls.get() shouldBe 3
        fixture.bus.calls.get() shouldBe 3
        fixture.dispatched.size shouldBe 3
        fixture.store.stored.map(_.name) shouldBe Vector("job.submitted", "job.running", "job.succeeded")
        fixture.dispatched.zip(fixture.store.stored).foreach { case (domain, stored) =>
          val event = domain.asInstanceOf[ReceptionDomainEvent]
          event.name shouldBe stored.name
          event.kind shouldBe stored.kind
          event.payload shouldBe stored.payload
          event.attributes shouldBe stored.attributes
          event.occurredAt shouldBe stored.createdAt
        }
        backend.calls.foreach { row =>
          row.attributes.getString("transport") shouldBe Some("bus")
          row.attributes.getString("outcome") shouldBe Some("returned")
          row.attributes.getBoolean("persisted") shouldBe Some(true)
          row.attributes.getInt("dispatched-count") shouldBe Some(1)
          row.attributes.getBoolean("event-identity-available") shouldBe Some(false)
          row.attributes.getString("event-id") shouldBe None
          row.attributes.getLong("event-sequence") shouldBe None
        }
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      }
      withNative(useBus = true) { fixture =>
        fixture.store.failAfterAppend = true
        val backend = new RecordingBackend
        When("the original store commits submitted and then returns a failure to the real bus")
        val jobid = withBackend(backend)(fixture.submit())
        Then("the native committed Event remains while the failed bus observation makes no false no-effect or persisted claim")
        fixture.store.stored.size shouldBe 1
        fixture.store.stored.head.name shouldBe "job.submitted"
        fixture.bus.results.head.isSuccess shouldBe false
        fixture.dispatched shouldBe empty
        fixture.store.appendCalls.get() shouldBe 1
        fixture.bus.calls.get() shouldBe 1
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Submitted)
        val row = backend.calls.head.attributes
        row.getString("outcome") shouldBe Some("failed")
        row.getBoolean("persisted") shouldBe None
        row.getInt("dispatched-count") shouldBe None
        row.getString("event-id") shouldBe None
        row.getLong("event-sequence") shouldBe None
        backend.calls.head.message should not include secret
      }
      withNative(noSink = true) { fixture =>
        val backend = new RecordingBackend
        When("a real synchronous Job completes without either native publication sink")
        val jobid = withBackend(backend)(fixture.submit(option = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)))
        Then("the genuine native source still emits unavailable observations without any Event factory store or bus invocation")
        fixture.store.appendCalls.get() shouldBe 0
        fixture.bus.calls.get() shouldBe 0
        fixture.store.stored shouldBe empty
        backend.calls.flatMap(_.attributes.getString("event-kind")) shouldBe Vector("job.submitted", "job.running", "job.succeeded")
        backend.calls.foreach { row =>
          row.attributes.getString("transport") shouldBe Some("unavailable")
          row.attributes.getString("outcome") shouldBe Some("unavailable")
          row.attributes.getString("event-id") shouldBe None
          row.attributes.getBoolean("event-identity-available") shouldBe Some(false)
        }
        fixture.engine.getStatus(jobid) shouldBe Some(JobStatus.Succeeded)
      }
    }
  }

  "LI5 Real hostile failing Task" should _scenario("LI5", "R1,R3,R5,R6,R9") {
    "retain private canonical result and metadata while observing safe Task associations and recovery attempt flags" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R3,R5,R6,R9; LI5: an actual adversarial failing Task on the native manual engine")
      withNative() { fixture =>
        val task = new JobExperienceOperationalAuditFixtures.AdversarialTask(ActionId("native", "hostile", Some(instant), Some("original")))
        val backend = new RecordingBackend
        When("the admitted native Task actually fails and produces its original terminal Event")
        val jobid = withBackend(backend) {
          val jobid = fixture.submit(List(task), JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral,
            requestSummary = Some(secret), parameters = Map("web.private" -> secret)))
          fixture.engine.drainAll() shouldBe 1
          jobid
        }
        Then("the canonical raw Task and Event retain private values while only safe captured associations appear in lifecycle rows")
        val raw = fixture.engine._get_record(jobid).get
        raw.taskReadModels.head.component shouldBe Some(secret + "-component")
        raw.taskReadModels.head.result.message.get should include(secret)
        val failed = fixture.store.stored.find(_.name == "job.failed").get
        failed.payload("web.private") shouldBe secret
        failed.payload("result-summary").asInstanceOf[String] should include(secret)
        task.executions.get() shouldBe 1
        val rows = backend.lifecycle.filter(_.attributes.getString("record-kind").contains("task"))
        rows should not be empty
        rows.foreach { row =>
          row.attributes.getString("job-id") shouldBe Some(jobid.value)
          row.attributes.getString("task-id") shouldBe Some(raw.taskReadModels.head.taskId.value)
          row.attributes.getString("task-kind") shouldBe Some("action")
          row.attributes.getString("target-kind") shouldBe None
          row.attributes.getString("relation") shouldBe None
          row.attributes.getString("component") shouldBe None
          row.attributes.getString("operation") shouldBe Some("emit")
          row.attributes.getInt("attempt-count") shouldBe None
        }
        val call = backend.calls.find(_.attributes.getString("event-kind").contains("job.failed")).get
        call.attributes.getInt("attempt-count") shouldBe Some(raw.retry.attemptCount)
        call.attributes.getBoolean("recovery-required") shouldBe Some(raw.retry.recoveryRequired)
        rows.last.attributes.getBoolean("recovery-required") shouldBe Some(raw.taskReadModels.head.recoveryRequired)
        backend.lifecycle.foreach { row =>
          row.message should not include secret
          row.attributes.asMap.values.foreach(value => String.valueOf(value) should not include secret)
        }
        val before = fixture.facts(jobid)
        fixture.engine.drainAll() shouldBe 0
        fixture.facts(jobid) shouldBe before
        task.executions.get() shouldBe 1
      }
    }
  }

  "LI6 Native exceptions and audit failure order" should _scenario("LI6", "R1,R4,R8") {
    "leave native throw effects original and suppress only postnormal audit failures with exact logger restoration" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R1,R4,R8; LI6: genuine admitted Jobs and native store or bus faults after their original effects")
      val original = LogBackendHolder.backend.get
      val policy = ObservabilityEngine.visibilityPolicy
      Vector(false, true).foreach { usebus =>
        withNative(useBus = usebus) { fixture =>
          val jobid = withBackend(LogBackend.NopLogBackend)(fixture.submit())
          val backend = new RecordingBackend
          val before = fixture.store.stored.size
          val providers = fixture.observations
          if (usebus) fixture.bus.throwAfterPublish = true else fixture.store.throwAfterAppend = true
          When("a real native publication throws after its actual append or dispatch effect")
          val thrown = withBackend(backend) {
            intercept[IllegalStateException](fixture.engine._append_event(jobid, "job.running", Map("private" -> secret)))
          }
          Then("the same native exception escapes and its already-applied effects remain without any observer output")
          (thrown eq (if (usebus) fixture.bus.fault else fixture.store.fault)) shouldBe true
          fixture.store.stored.size shouldBe before + 1
          fixture.store.appendCalls.get() shouldBe providers._5 + 1
          fixture.store.readCalls.get() shouldBe providers._6
          fixture.ids.calls.get() shouldBe providers._2 + 1
          fixture.time.calls.get() shouldBe providers._3 + 1
          fixture.clock.calls.get() shouldBe providers._1
          fixture.timer.pendingCount shouldBe providers._4
          if (usebus) fixture.dispatched.size shouldBe 2
          backend.events shouldBe empty
        }
      }
      When("a throwing audit backend follows normally completed native Sync Nil publication")
      val baseline = _sync_run(1, LogBackend.NopLogBackend)
      val throwing = new JobExperienceOperationalAuditFixtures.ThrowingBackend
      val observed = _sync_run(1, throwing)
      Then("the original native status Events credits and providers agree without propagation retry or extra body work")
      observed shouldBe baseline
      throwing.calls.get() shouldBe 3
      (LogBackendHolder.backend.get eq original) shouldBe true
      ObservabilityEngine.visibilityPolicy shouldBe policy
    }
  }

  private def _sync_run(count: Int, backend: LogBackend) = withNative() { fixture =>
    withBackend(backend) {
      val jobs = Vector.fill(count)(fixture.submit(option = JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral, runMode = JobRunMode.Sync)))
      val facts = jobs.map { jobid =>
        val source = fixture.engine._get_record(jobid).get
        (jobid.value, source.status, source.createdAt, source.updatedAt, source.timeline,
          source.retry, source.taskReadModels, fixture.engine.getResult(jobid), fixture.engine.lifetimeSnapshot(jobid))
      }
      // Verify each audit association against the actual original returned append objects.
      fixture.store.results.flatMap(_.toOption.get) shouldBe fixture.store.stored
      (fixture.store.stored, facts, fixture.engine.resourceSnapshot, fixture.observations, fixture.timer.pendingCount)
    }
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-lifecycle-operational-audit, example:$example, rules:$rules, phase:69.7")
}
