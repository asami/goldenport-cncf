package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ScopeContext, ScopeKind, SpanId, TraceId}
import org.goldenport.cncf.log.LogBackend
import org.goldenport.cncf.observability.{LogLevel, VisibilityPolicy}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class JobExperienceOperationalAuditSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobExperienceOperationalAuditFixtures.*

  "OA1 Completed strict facade observations" should _scenario("OA1", "R1,R2,R3,R7,R8") {
    "retain all original success and failure instances and exclude private conclusions and catalogues" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R2,R3,R7,R8; OA1: actual Consequence objects and the recording backend")
      given ExecutionContext = context()
      val backend = new RecordingBackend
      val list = Consequence.success(JobExperiencePage(Vector.empty, 0, None))
      val get = Consequence.success(view(JobId.generate()))
      val tasks = hostileTasks()
      val diagnostics = Consequence.success(JobExperienceDiagnostics(JobId.generate(), tasks, hostileTimeline(tasks)))
      val control = Consequence.success(JobControlResponse(JobId.generate(), JobStatus.Cancelled,
        Some(OperationResponse.RecordResponse(Record.data("credential" -> Record.data("provider" -> secret)))), false))
      val catalogue = Consequence.success(Record.data("credential" -> Record.data("path" -> secret)))
      val failure = Consequence.stateInvalid[Nothing](s"credential=$secret provider=$secret path=$secret request=$secret")
      When("each normally returned value is observed through its strict signature")
      val identities = withBackend(backend) {
        val successes = Vector(
          JobExperienceOperationalAudit.observeList(list) eq list,
          JobExperienceOperationalAudit.observeGet(get) eq get,
          JobExperienceOperationalAudit.observeDiagnostics(diagnostics) eq diagnostics,
          JobExperienceOperationalAudit.observeControl(control) eq control,
          JobExperienceOperationalAudit.observeCatalog(catalogue) eq catalogue
        )
        backend.clear()
        val failures = Vector(
          JobExperienceOperationalAudit.observeList(failure) eq failure,
          JobExperienceOperationalAudit.observeGet(failure) eq failure,
          JobExperienceOperationalAudit.observeDiagnostics(failure) eq failure,
          JobExperienceOperationalAudit.observeControl(failure) eq failure,
          JobExperienceOperationalAudit.observeCatalog(failure) eq failure
        )
        successes ++ failures
      }
      Then("five failures disclose only fixed call facts and existing context references")
      identities shouldBe Vector.fill(10)(true)
      backend.events.size shouldBe 5
      backend.events.map(_.attributes.getString("operation").get) shouldBe Vector("list", "get", "diagnostics", "control", "catalog")
      backend.events.foreach { event =>
        event.attributes.asMap.keySet shouldBe _context_keys(summon[ExecutionContext])
        event.attributes.getString("outcome") shouldBe Some("failed")
        event.attributes.getString("record-kind") shouldBe Some("call")
        _same_facts(event.attributes, framed(event.message))
        event.message should not include secret
      }
      Given("OA1/R2,R3: an empty successful list and a private catalogue return normally")
      backend.clear()
      When("their already returned values are observed")
      withBackend(backend) {
        JobExperienceOperationalAudit.observeList(list)
        JobExperienceOperationalAudit.observeCatalog(catalogue)
      }
      Then("only the fixed returned call facts appear")
      backend.events.size shouldBe 2
      backend.events.head.attributes.getInt("returned-count") shouldBe Some(0)
      backend.events.head.attributes.getBoolean("truncated") shouldBe Some(false)
      backend.events.head.attributes.getString("job-id") shouldBe None
      backend.events.last.attributes.asMap.keySet shouldBe _context_keys(summon[ExecutionContext])
      backend.lines.foreach(_ should not include secret)
    }
  }

  "OA2 Accepted detail correlation" should _scenario("OA2", "R1,R4,R5,R7") {
    "encode the same opaque references and retry facts for structured and byte-framed consumers" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R4,R5,R7; OA2: an accepted view with private available result and summary")
      val base = context()
      val observation = base.observability.copy(spanId = Some(SpanId("audit", "detail", "operation")),
        correlationId = Some(CorrelationId("audit", "boundary")))
      given ExecutionContext = ExecutionContext.withObservabilityContext(base, observation)
      val jobid = JobId("single", "global", Some(instant), Some("opaque_秘密_\"escaped\""))
      val original = Consequence.success(view(jobid))
      val backend = new RecordingBackend
      When("the normally returned detail is observed once")
      val result = withBackend(backend)(JobExperienceOperationalAudit.observeGet(original))
      Then("the original object survives and the audit contains only allowed actual facets")
      (result eq original) shouldBe true
      backend.events.size shouldBe 1
      val event = backend.events.head
      val json = framed(event.message)
      _same_facts(event.attributes, json)
      json.hcursor.get[String]("job-id").toOption shouldBe Some(jobid.value)
      json.hcursor.get[String]("trace-id").toOption shouldBe Some(observation.traceId.value)
      json.hcursor.get[String]("span-id").toOption shouldBe observation.spanId.map(_.value)
      json.hcursor.get[String]("correlation-id").toOption shouldBe observation.correlationId.map(_.value)
      json.hcursor.get[Int]("attempt-count").toOption shouldBe Some(3)
      json.hcursor.get[Boolean]("recovery-required").toOption shouldBe Some(true)
      json.hcursor.get[String]("status").toOption shouldBe Some("failed")
      json.hcursor.get[String]("origin").toOption shouldBe Some("runtime")
      event.attributes.asMap.keySet shouldBe _context_keys(summon[ExecutionContext]) ++ Set("job-id", "status", "origin", "attempt-count", "recovery-required")
      event.message should not include secret
      json.noSpaces.getBytes(UTF_8).length should be <= 4096
      event.message.getBytes(UTF_8).length should be <= 8192
    }
  }

  "OA3 Control replay and unsafe transport references" should _scenario("OA3", "R1,R2,R3,R4,R5") {
    "retain control payloads while applying inclusive byte guards and suppressing unsafe context emission" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R2,R3,R4,R5; OA3: typed controls and eagerly constructed transport-boundary IDs")
      val base = context()
      val backend = new RecordingBackend
      val emptyid = JobId("single", "global", Some(instant), Some(""))
      val prefixbytes = emptyid.value.getBytes(UTF_8).length
      val exact = JobId("single", "global", Some(instant), Some("é" + "a" * (512 - prefixbytes - 2)))
      val oversized = JobId("single", "global", Some(instant), Some("é" + "a" * (513 - prefixbytes - 2)))
      val unsafe = JobId("single", "global", Some(instant), Some("unsafe\nvalue"))
      val payload = Some(OperationResponse.Scalar(secret))
      val values = Vector(exact, oversized, unsafe).flatMap(id => Vector(true, false).map(changed =>
        Consequence.success(JobControlResponse(id, JobStatus.Cancelled, payload, false, changed))))
      When("normal controls are observed and required context references fail their transport guards")
      val identities = withBackend(backend) {
        values.map(original => JobExperienceOperationalAudit.observeControl(original)(using base) eq original)
      }
      Then("applied and replayed reflect changed alone, oversized Job references are marked, and payloads remain private")
      identities shouldBe Vector.fill(6)(true)
      exact.value.getBytes(UTF_8).length shouldBe 512
      oversized.value.getBytes(UTF_8).length shouldBe 513
      backend.events.map(_.attributes.getString("outcome").get) shouldBe Vector.fill(3)(Vector("applied", "replayed")).flatten
      backend.events.take(2).foreach(_.attributes.getString("job-id") shouldBe Some(exact.value))
      backend.events.drop(2).foreach { event =>
        event.attributes.getBoolean("job-id-omitted") shouldBe Some(true)
        event.attributes.getString("job-id") shouldBe None
      }
      values.foreach(_.toOption.get.response shouldBe payload)
      backend.lines.foreach(_ should not include secret)
      Given("OA3/R4: unsafe required trace, correlation, scope and null observer shapes")
      val badtrace = ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = TraceId("t" * 600, "audit")))
      val badcorrelation = ExecutionContext.withObservabilityContext(base, base.observability.copy(correlationId = Some(CorrelationId("c" * 600, "audit"))))
      val badscope = base.withScope(ScopeContext(ScopeKind.Action, "unsafe\nname", Some(base.scope), base.observability))
      val longscope = base.withScope(ScopeContext(ScopeKind.Action, "s" * 129, Some(base.scope), base.observability))
      val nulltrace = ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = null))
      backend.clear()
      When("these contexts observe an unchanged successful control result")
      val suppressed = withBackend(backend) {
        Vector(badtrace, badcorrelation, badscope, longscope, nulltrace).map { ctx =>
          JobExperienceOperationalAudit.observeControl(values.head)(using ctx) eq values.head
        }
      }
      Then("required context guards suppress logging and preserve all results")
      suppressed shouldBe Vector.fill(5)(true)
      backend.events shouldBe empty
      Given("OA3/R4: an invalid optional span on an otherwise valid original context")
      val badspan = ExecutionContext.withObservabilityContext(base, base.observability.copy(spanId = Some(SpanId("s" * 600, "audit", "operation"))))
      When("the same successful control is observed with the invalid optional span")
      val optionalresult = withBackend(backend)(JobExperienceOperationalAudit.observeControl(values.head)(using badspan))
      Then("the invalid span alone is absent")
      (optionalresult eq values.head) shouldBe true
      backend.events.size shouldBe 1
      backend.events.head.attributes.getString("span-id") shouldBe None
    }
  }

  "OA4 Best effort visibility and row bounds" should _scenario("OA4", "R1,R6,R7,R8") {
    "retain results under hidden noop throwing and malformed observations and cap only emitted rows" in {
      Given("docs/spec/job-operational-audit-contract.md R1,R6,R7,R8; OA4: strict success and failure values and real logging policies")
      given ExecutionContext = context()
      val failure = Consequence.stateInvalid[Nothing](secret)
      val success = Consequence.success(view(JobId.generate()))
      val backend = new RecordingBackend
      val throwing = new ThrowingBackend
      val tasks = hostileTasks(count = 105)
      val jobid = JobId.generate()
      val diagnostics = Consequence.success(JobExperienceDiagnostics(jobid, tasks, hostileTimeline(tasks)))
      val entry = JobExperienceEntry(jobid, JobStatus.Submitted, JobExperienceStatus.Queued,
        JobExperienceVocabulary("Queued", "Wait"), JobPersistencePolicy.Ephemeral, JobDataOrigin.Runtime, instant, instant)
      val cursor = Some(JobManagementCursor.fromOpaque("unchanged-cursor"))
      val page = Consequence.success(JobExperiencePage(Vector.fill(105)(entry), 999, cursor))
      When("observation is disabled or fails and oversized returned vectors reach the bounded observer")
      val emptylist = Consequence.success(JobExperiencePage(Vector.empty, 0, None))
      val emptydiagnostics = Consequence.success(JobExperienceDiagnostics(jobid,
        JobTaskPage(0, 1, 0, 0, Vector.empty), JobTimelinePage(0, 1, 0, 0, Vector.empty)))
      val changed = Consequence.success(JobControlResponse(jobid, JobStatus.Cancelled, None, false, true))
      val catalogue = Consequence.success(Record.data("private" -> secret))
      val conservation = Vector[LogBackend](backend, LogBackend.NopLogBackend, throwing).map { logger =>
        val policy = if (logger eq backend) VisibilityPolicy(minLevel = LogLevel.Error) else VisibilityPolicy.AllowAll
        withBackend(logger, policy) {
          Vector(
            JobExperienceOperationalAudit.observeList(emptylist) eq emptylist,
            JobExperienceOperationalAudit.observeGet(success) eq success,
            JobExperienceOperationalAudit.observeDiagnostics(emptydiagnostics) eq emptydiagnostics,
            JobExperienceOperationalAudit.observeControl(changed) eq changed,
            JobExperienceOperationalAudit.observeCatalog(catalogue) eq catalogue,
            JobExperienceOperationalAudit.observeList(failure) eq failure,
            JobExperienceOperationalAudit.observeGet(failure) eq failure,
            JobExperienceOperationalAudit.observeDiagnostics(failure) eq failure,
            JobExperienceOperationalAudit.observeControl(failure) eq failure,
            JobExperienceOperationalAudit.observeCatalog(failure) eq failure
          )
        }
      }
      val malformed = Consequence.success(null: JobExperienceView)
      val bounded = withBackend(backend) {
        Vector(JobExperienceOperationalAudit.observeGet(malformed) eq malformed,
          JobExperienceOperationalAudit.observeList(page) eq page,
          JobExperienceOperationalAudit.observeDiagnostics(diagnostics) eq diagnostics)
      }
      Then("only bounded observation rows are delivered, with no page or cursor mutation or fallback")
      conservation.foreach(_ shouldBe Vector.fill(10)(true))
      bounded shouldBe Vector.fill(3)(true)
      throwing.calls.get() shouldBe 10
      backend.events.size shouldBe 302
      backend.events.count(_.attributes.getString("record-kind").contains("job")) shouldBe 100
      backend.events.count(_.attributes.getString("record-kind").contains("task")) shouldBe 100
      backend.events.count(_.attributes.getString("record-kind").contains("timeline")) shouldBe 100
      backend.events.filter(_.attributes.getString("record-kind").contains("call")).foreach(_.attributes.getBoolean("truncated") shouldBe Some(true))
      backend.events.head.attributes.getInt("returned-count") shouldBe Some(105)
      backend.events(101).attributes.getInt("task-count") shouldBe Some(105)
      page.toOption.get.nextCursor shouldBe cursor
      page.toOption.get.entries.size shouldBe 105
      diagnostics.toOption.get.tasks shouldBe tasks
      backend.events.foreach { event =>
        framed(event.message).noSpaces.getBytes(UTF_8).length should be <= 4096
        event.message.getBytes(UTF_8).length should be <= 8192
        event.message should not include secret
      }
    }
  }

  private def _same_facts(record: Record, json: Json): Unit = {
    json.asObject.get.keys.toSet shouldBe record.asMap.keySet
    json.asObject.get.toVector.foreach { case (key, atom) =>
      atom.asString.foreach(value => record.getString(key) shouldBe Some(value))
      atom.asBoolean.foreach(value => record.getBoolean(key) shouldBe Some(value))
      atom.asNumber.flatMap(_.toLong).foreach(value => record.getLong(key) shouldBe Some(value))
    }
  }

  private def _context_keys(ctx: ExecutionContext): Set[String] =
    Set("audit.kind", "operation", "outcome", "record-kind", "trace-id") ++
      ctx.observability.spanId.map(_ => "span-id") ++ ctx.observability.correlationId.map(_ => "correlation-id")

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-operational-audit, example:$example, rules:$rules, phase:69.7")
}
