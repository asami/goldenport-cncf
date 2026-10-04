package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder, StructuredLogEvent}
import org.goldenport.cncf.testutil.SubsystemTestFixture
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceOperationalAuditIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobExperienceOperationalAuditFixtures.*

  "AI1 Native scoped paging and exact detail" should _scenario("AI1", "R1,R2,R3,R5,R6") {
    "observe only returned owned Jobs while preserving native calls cursors order and canonical state" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R2,R3,R5,R6; AI1: three session-owned Jobs and one foreign Job on the manual native engine")
      withNative { fixture =>
        given ExecutionContext = fixture.owner
        val owned = Vector.fill(3)(fixture.submit())
        val foreign = fixture.submit(foreignOwner = true)
        val all = owned :+ foreign
        val before = fixture.snapshot(all)
        val observations = fixture.observations
        val backend = new RecordingBackend
        val service = fixture.service
        val query = JobExperienceQuery(JobExperienceScope.Mine(None), limit = 2)
        When("the owner follows the actual page cursor and reads an exact view")
        val (first, second, detail) = withBackend(backend) {
          val first = service.list(query).toOption.get
          val second = service.list(query.copy(cursor = first.nextCursor)).toOption.get
          val detail = service.get(first.entries.head.jobId, JobExperienceScope.Mine(None)).toOption.get
          (first, second, detail)
        }
        Then("the observer adds no query or state effect and associates only the original returned authorized rows")
        first.entries.size shouldBe 2
        first.totalCount shouldBe 3
        first.nextCursor should not be empty
        second.entries.size shouldBe 1
        second.nextCursor shouldBe None
        (first.entries ++ second.entries).map(_.jobId).toSet shouldBe owned.toSet
        fixture.engine.calls shouldBe Vector("page", "page", "detail", "result")
        fixture.snapshot(all) shouldBe before
        fixture.observations shouldBe observations
        val rows = backend.events.filter(_.attributes.getString("record-kind").contains("job"))
        rows.flatMap(_.attributes.getString("job-id")) shouldBe (first.entries ++ second.entries).map(_.jobId.value)
        backend.events.head.attributes.getInt("returned-count") shouldBe Some(2)
        backend.events.head.attributes.getString("job-id") shouldBe None
        backend.events.last.attributes.getString("job-id") shouldBe Some(detail.detail.summary.jobId.value)
        backend.events.last.attributes.getInt("attempt-count") shouldBe Some(detail.detail.retry.attemptCount)
        backend.lines.foreach(_ should not include foreign.value)
        _safe_events(backend.events)
      }
    }
  }

  "AI2 Native denial and missing equivalence" should _scenario("AI2", "R1,R3,R8") {
    "retain original refusal without requested identities or unauthorized extra work" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R3,R8; AI2: a real owner Job, broad foreign capability, ordinary owner and anonymous contexts")
      withNative { fixture =>
        val jobid = fixture.submit()
        val missing = JobId.create("missing", instant, fixture.owner.idGeneration)
        val before = fixture.snapshot(Vector(jobid))
        val observations = fixture.observations
        val backend = new RecordingBackend
        val service = fixture.service
        When("foreign known and missing IDs plus anonymous and low-privileged diagnostics/catalogue are requested")
        val failures = withBackend(backend) {
          val denied = service.get(jobid, JobExperienceScope.Mine(None))(using fixture.foreign)
          val absent = service.get(missing, JobExperienceScope.Mine(None))(using fixture.foreign)
          val anonymous = service.get(jobid, JobExperienceScope.Mine(None))(using ExecutionContext.create(SecurityContext.Privilege.Anonymous))
          val diagnostics = service.diagnostics(jobid, JobExperienceScope.Mine(None), 0, 1)(using fixture.ordinary)
          val catalogue = service.operatorCatalog(null)(using fixture.ordinary)
          Vector(denied.isSuccess, absent.isSuccess, anonymous.isSuccess, diagnostics.isSuccess, catalogue.isSuccess)
        }
        Then("both exact lookup refusals disclose the same call facts and no native control or unauthorized body runs")
        failures shouldBe Vector.fill(5)(false)
        backend.events.size shouldBe 5
        backend.events(0).attributes shouldBe backend.events(1).attributes
        fixture.engine.calls shouldBe Vector("detail", "detail")
        fixture.snapshot(Vector(jobid)) shouldBe before
        fixture.observations shouldBe observations
        backend.events.foreach { event =>
          event.attributes.getString("outcome") shouldBe Some("failed")
          event.attributes.getString("job-id") shouldBe None
          event.attributes.getString("task-id") shouldBe None
          event.attributes.asMap.keySet.subsetOf(Set("audit.kind", "operation", "outcome", "record-kind", "trace-id", "span-id", "correlation-id")) shouldBe true
          event.message should not include jobid.value
          event.message should not include missing.value
          event.message should not include "foreign-session"
        }
        _safe_events(backend.events)
      }
    }
  }

  "AI3 Native hostile task diagnostics" should _scenario("AI3", "R1,R5,R6,R9") {
    "retain canonical task and timeline relations and remove private metadata at two actual offsets" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R5,R6,R9; AI3: two real adversarial failing Tasks run by the native manual engine")
      withNative { fixture =>
        given ExecutionContext = fixture.owner
        val tasks = Vector.fill(2)(new AdversarialTask(ActionId.create("adversarial", instant, fixture.ids)))
        val jobs = tasks.map(task => fixture.submit(List(task)))
        fixture.manual.engine.drainOne() shouldBe true
        fixture.manual.engine.drainOne() shouldBe true
        tasks.foreach(_.executions.get() shouldBe 1)
        val raw = fixture.snapshot(jobs)
        raw.foreach { model =>
          model.get.tasks.tasks.head.component shouldBe Some(secret + "-component")
          model.get.tasks.tasks.head.result.message.get should include(secret)
          model.get.timeline.events should not be empty
        }
        val observations = fixture.observations
        val backend = new RecordingBackend
        When("the admitted administrator reads real limit-one diagnostics at offset zero and offset one")
        val pages = withBackend(backend) {
          jobs.flatMap(jobid => Vector(0, 1).map(offset => fixture.service.diagnostics(jobid,
            JobExperienceScope.Mine(None), offset, 1).toOption.get))
        }
        Then("each original detail/tasks/timeline sequence stays exact and safe rows share only actual Job Task sequence and trace facts")
        fixture.engine.calls shouldBe Vector.fill(4)(Vector("detail", "tasks", "timeline")).flatten
        fixture.snapshot(jobs) shouldBe raw
        fixture.observations shouldBe observations
        tasks.foreach(_.executions.get() shouldBe 1)
        pages.foreach { page =>
          val model = raw(jobs.indexOf(page.jobId)).get
          page.tasks.tasks.foreach { task =>
            model.tasks.tasks.find(_.taskId == task.taskId).map(_.status) shouldBe Some(task.status)
            task.component shouldBe None
            task.service shouldBe None
            task.operation shouldBe None
            task.targetKind shouldBe None
            task.result.message shouldBe None
            task.compensationActionRef shouldBe None
            task.taskKind shouldBe "action"
          }
          page.timeline.events.foreach { event =>
            model.timeline.events.find(_.sequence == event.sequence).map(_.occurredAt) shouldBe Some(event.occurredAt)
            event.note shouldBe None
          }
        }
        val taskrows = backend.events.filter(_.attributes.getString("record-kind").contains("task"))
        taskrows.size shouldBe 2
        taskrows.foreach { event =>
          val jobid = event.attributes.getString("job-id").get
          val taskid = event.attributes.getString("task-id").get
          val model = raw.flatten.find(_.jobId.value == jobid).get
          model.tasks.tasks.map(_.taskId.value) should contain(taskid)
          event.attributes.getString("trace-id") shouldBe Some(fixture.owner.observability.traceId.value)
        }
        backend.events.filter(_.attributes.getString("record-kind").contains("timeline")).foreach { event =>
          val model = raw.flatten.find(_.jobId.value == event.attributes.getString("job-id").get).get
          model.timeline.events.map(_.sequence) should contain(event.attributes.getLong("sequence").get)
        }
        _safe_events(backend.events)
      }
    }
  }

  "AI4 Native control effect and replay" should _scenario("AI4", "R1,R2,R3,R8") {
    "preserve applied replayed and refused transitions even when observation throws after the normal result" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R2,R3,R8; AI4: actual owned submitted Jobs and foreign/no-capability sessions")
      withNative { fixture =>
        val jobid = fixture.submit()
        val plainid = fixture.manual.engine.submit(Nil, fixture.ordinary, JobSubmitOption(persistence = JobPersistencePolicy.Ephemeral)).toOption.get
        val throwingid = fixture.submit()
        val backend = new RecordingBackend
        val service = fixture.service
        When("the owner cancels and replays while unauthorized callers request the same command")
        val (applied, replay, foreign, ordinary) = withBackend(backend) {
          val applied = service.control(jobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)(using fixture.owner)
          val replay = service.control(jobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)(using fixture.owner)
          val foreign = service.control(jobid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)(using fixture.foreign)
          val ordinary = service.control(plainid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)(using fixture.ordinary)
          (applied, replay, foreign, ordinary)
        }
        val after = fixture.snapshot(Vector(jobid, plainid))
        val throwing = new ThrowingBackend
        val result = withBackend(throwing)(service.control(throwingid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)(using fixture.owner))
        val beforeplay = fixture.snapshot(Vector(throwingid))
        val observations = fixture.observations
        val replaythrown = withBackend(throwing)(service.control(throwingid, JobExperienceScope.Mine(None), JobControlCommand.Cancel)(using fixture.owner))
        Then("one native cancellation event per affected Job survives and audit failures cause no second command or state mutation")
        applied.toOption.map(response => (response.status, response.changed)) shouldBe Some((JobStatus.Cancelled, true))
        replay.toOption.map(response => (response.status, response.changed)) shouldBe Some((JobStatus.Cancelled, false))
        foreign.isSuccess shouldBe false
        ordinary.isSuccess shouldBe false
        result.toOption.map(response => (response.status, response.changed)) shouldBe Some((JobStatus.Cancelled, true))
        replaythrown.toOption.map(response => (response.status, response.changed)) shouldBe Some((JobStatus.Cancelled, false))
        result.toOption.get.response shouldBe replaythrown.toOption.get.response
        fixture.engine.calls shouldBe Vector("detail", "control", "detail", "control", "detail", "detail", "detail", "control", "detail", "control")
        fixture.manual.engine.query(jobid).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 1
        fixture.manual.engine.query(throwingid).get.timeline.events.count(_.kind == "job.cancelled") shouldBe 1
        fixture.manual.engine.getStatus(plainid) shouldBe Some(JobStatus.Submitted)
        fixture.snapshot(Vector(jobid, plainid)) shouldBe after
        fixture.snapshot(Vector(throwingid)) shouldBe beforeplay
        fixture.observations shouldBe observations
        // C5 adds one native applied Cancel audit to the two unchanged facade
        // applied/replayed attempts; the replay creates no second native event.
        throwing.calls.get() shouldBe 3
        backend.events.map(_.attributes.getString("outcome").get) shouldBe Vector("applied", "replayed", "failed", "failed")
        backend.events.drop(2).foreach(_.attributes.getString("job-id") shouldBe None)
        _safe_events(backend.events)
      }
    }
  }

  "AI5 Standard UTF8 file audit consumer" should _scenario("AI5", "R3,R4,R5,R7,R8") {
    "decode actual default text-only output with the same authorized facts and bounded single lines" in {
      Given("docs/spec/job-operational-audit-contract.md R3,R4,R5,R7,R8; AI5: the standard FileLogBackend and a unique repository target work subtree")
      val parent = Paths.get("target/job-experience-operational-audit/work")
      Files.createDirectories(parent)
      val directory = Files.createTempDirectory(parent, "file-consumer-")
      val path = directory.resolve("audit.log")
      try {
        withNative { fixture =>
          given ExecutionContext = fixture.owner
          val task = new AdversarialTask(ActionId.create("file-task", instant, fixture.ids))
          val failedjob = fixture.submit(List(task))
          fixture.manual.engine.drainOne() shouldBe true
          val canceljob = fixture.submit()
          val service = fixture.service
          val recording = new RecordingBackend
          val file = LogBackend.FileLogBackend(path.toString)
          When("real facade get diagnostics and control results reach the standard file logger")
          val bootstrap = LogBackend.BootstrapLogBackend(LogBackend.NopLogBackend)
          val replayed = new RecordingBackend
          val restored = withBackend(bootstrap) {
            val seeded = service.get(failedjob, JobExperienceScope.Mine(None))
            val (get, diagnostics, control) = withBackend(file) {
              (service.get(failedjob, JobExperienceScope.Mine(None)),
                service.diagnostics(failedjob, JobExperienceScope.Mine(None), 0, 100),
                service.control(canceljob, JobExperienceScope.Mine(None), JobControlCommand.Cancel))
            }
            val restored = LogBackendHolder.backend.exists(_ eq bootstrap)
            bootstrap.replayTo(replayed)
            withBackend(recording) {
              JobExperienceOperationalAudit.observeGet(get)
              JobExperienceOperationalAudit.observeDiagnostics(diagnostics)
              JobExperienceOperationalAudit.observeControl(control)
            }
            (restored, seeded.isSuccess)
          }
          val lines = Files.readAllLines(path, UTF_8).asScala.toVector.filter(_.contains("job.audit.v1 bytes="))
          Then("byte framing isolates the same JSON from original context suffixes and preserves actual native associations")
          restored shouldBe (true, true)
          replayed.lines.map(framed) shouldBe Vector(framed(recording.lines.head))
          lines.map(framed) shouldBe recording.lines.map(framed)
          lines.size shouldBe recording.events.size
          val json = lines.map(framed)
          json.head.hcursor.get[String]("job-id").toOption shouldBe Some(failedjob.value)
          json.last.hcursor.get[String]("outcome").toOption shouldBe Some("applied")
          json.last.hcursor.get[String]("job-id").toOption shouldBe Some(canceljob.value)
          val raw = fixture.manual.engine.query(failedjob).get
          json.filter(_.hcursor.get[String]("record-kind").toOption.contains("task")).foreach { event =>
            raw.tasks.tasks.map(_.taskId.value) should contain(event.hcursor.get[String]("task-id").toOption.get)
          }
          json.filter(_.hcursor.get[String]("record-kind").toOption.contains("timeline")).foreach { event =>
            raw.timeline.events.map(_.sequence) should contain(event.hcursor.get[Long]("sequence").toOption.get)
          }
          lines.foreach { line =>
            line should not include secret
            line should not include "\n"
            line.getBytes(UTF_8).length should be <= 8192
            framed(line).noSpaces.getBytes(UTF_8).length should be <= 4096
            framed(line).hcursor.get[String]("trace-id").toOption shouldBe Some(fixture.owner.observability.traceId.value)
          }
          task.executions.get() shouldBe 1
          _safe_events(recording.events)
        }
      } finally {
        val entries = Files.walk(directory)
        try entries.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
        finally entries.close()
      }
    }
  }

  "AI6 Installed operator catalogue" should _scenario("AI6", "R1,R2,R5,R8,R10") {
    "observe installed admin success and User refusal while retaining runtime-only health and unavailable retention" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R2,R5,R8,R10; AI6: the actual default Subsystem and installed job_control facade")
      val backend = new RecordingBackend
      SubsystemTestFixture.withSubsystem(SubsystemTestFixture.Startup.Default(Some("command"))) { subsystem =>
        val component = subsystem.findComponent(BuiltinComponentIdentity.JOB_CONTROL).getOrElse(fail("installed job_control missing"))
        val service = component.port.get[JobExperienceService].getOrElse(fail("installed facade missing"))
        val admin = context()
        val user = context(privilege = SecurityContext.Privilege.User)
        val before = subsystem.jobEngine.metrics
        When("the installed facade reads its native catalogue under the two actual policies")
        val (accepted, denied) = withBackend(backend) {
          (service.operatorCatalog(component)(using admin), service.operatorCatalog(component)(using user))
        }
        Then("catalogue facts retain their original limitations and audit has no catalogue paths or inferred readiness")
        accepted.isSuccess shouldBe true
        denied.isSuccess shouldBe false
        val catalogue = accepted.toOption.get
        val categories = catalogue.getAny("categories").collect {
          case values: Seq[?] => values.collect { case value: Record => value }
        }.getOrElse(Vector.empty)
        categories.find(_.getString("name").contains("health")).flatMap(_.getBoolean("runtime-only")) shouldBe Some(true)
        categories.find(_.getString("name").contains("retention")).flatMap(_.getBoolean("available")) shouldBe Some(false)
        subsystem.jobEngine.metrics shouldBe before
        backend.events.map(_.attributes.getString("outcome").get) shouldBe Vector("returned", "failed")
        backend.events.foreach { event =>
          event.attributes.getString("operation") shouldBe Some("catalog")
          event.attributes.asMap.keySet.subsetOf(Set("audit.kind", "operation", "outcome", "record-kind", "trace-id", "span-id", "correlation-id")) shouldBe true
        }
        _safe_events(backend.events)
      }
    }
  }

  private def _safe_events(events: Vector[StructuredLogEvent]): Unit = events.foreach { event =>
    event.message should not include secret
    event.attributes.asMap.values.foreach(value => String.valueOf(value) should not include secret)
    val json = framed(event.message)
    json.asObject.get.keys.toSet shouldBe event.attributes.asMap.keySet
    json.noSpaces.getBytes(UTF_8).length should be <= 4096
    event.message.getBytes(UTF_8).length should be <= 8192
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operational-audit, example:$example, rules:$rules, phase:69.7" +
      (if (example == "AI4") ", companion-spec:job-lifecycle-operational-audit, companion-example:AI4, companion-rules:R8" else ""))
}
