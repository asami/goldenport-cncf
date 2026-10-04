package org.goldenport.cncf.event

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, Principal, PrincipalId, ScopeContext, ScopeKind, SpanId, TraceId}
import org.goldenport.cncf.job.JobProducerOperationalAuditTestSupport.{CountingClock, CountingIds}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.goldenport.id.UniversalId
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class EventPersistenceOperationalAuditSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import EventPersistenceOperationalAuditTestSupport.*

  "EA1 Original closed acknowledgement" should _scenario("EA1", "E1,E3,E4,E5") {
    "retain exact original identity and primitive framed atoms under ordinary shrinking" in {
      Given("E1,E3,E4,E5: counted original context, typed receipt references and entropy0..96 with seed69709602L/min24")
      val clock = new CountingClock
      val ids = new CountingIds
      given ctx: ExecutionContext = context(clock, ids)
      val backend = new RecordingBackend
      val property = Prop.forAll(Gen.choose(0, 96)) { variant =>
        Given("one matching original Stored NonTransactional receipt with explicit epoch and entropy")
        val record = acknowledgement(s"opaque-秘密-\"$variant\"").copy(sequence = variant.toLong)
        val records = Vector(record)
        val response = Consequence.success(records)
        val before = (clock.calls.get(), ids.calls.get(), record.id.value, record.sequence, record.createdAt)
        backend.clear()
        When("the fixed observer receives the already captured result")
        withBackend(backend)(EventPersistenceOperationalAudit.observe(record.name, record.kind, response))
        Then("text and structured consumers expose exactly the original closed primitive fields without effects")
        backend.events.size shouldBe 1
        val json = expected(ctx, record)
        framed(backend.events.head.message).noSpaces shouldBe json.noSpaces
        backend.lines.map(framed) shouldBe Vector(json)
        sameAtoms(backend.events.head.attributes, json)
        (response.toOption.get eq records) shouldBe true
        (records.head eq record) shouldBe true
        (clock.calls.get(), ids.calls.get(), record.id.value, record.sequence, record.createdAt) shouldBe before
        true
      }
      When("the ordinary shrinking property executes with the declared seed and minimum")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709602L)), property)
      Then("the property passes at least24 successful checks")
      checked.passed shouldBe true
      checked.succeeded should be >= 24
    }
  }

  "EA2 Reachable receipt admission" should _scenario("EA2", "E1,E3,E4") {
    "admit inclusive512 native ID bytes and reject each malformed successful identity without changing Success" in {
      Given("E1,E3,E4: actual final native EventId with explicit epoch/entropy; empty canonical value is unreachable")
      given ExecutionContext = context(new CountingClock, new CountingIds)
      val backend = new RecordingBackend
      val original = acknowledgement()
      val boundary = acknowledgement(padding(entropy => eventId(entropy).value, 512))
      val over = acknowledgement(padding(entropy => eventId(entropy).value, 513))
      boundary.id.value.getBytes(UTF_8).length shouldBe 512
      over.id.value.getBytes(UTF_8).length shouldBe 513
      eventId("").value.nonEmpty shouldBe true
      val invalid: Vector[(String, String, Vector[EventRecord])] = Vector(
        (original.name, original.kind, Vector(over)),
        (original.name, original.kind, Vector(acknowledgement("control\nvalue"))),
        (original.name, original.kind, Vector(original.copy(id = null))),
        (original.name, original.kind, Vector(original.copy(createdAt = null))),
        (original.name, original.kind, Vector(original.copy(sequence = -1L))),
        (original.name, original.kind, Vector(original.copy(name = "other"))),
        (original.name, original.kind, Vector(original.copy(kind = "other"))),
        (original.name, original.kind, Vector(original.copy(lane = EventLane.Transactional))),
        (original.name, original.kind, Vector(original.copy(lane = null))),
        (original.name, original.kind, Vector(original.copy(persistent = false))),
        (original.name, original.kind, Vector(original.copy(status = null))),
        (null, original.kind, Vector(original)), (original.name, null, Vector(original)),
        (original.name, original.kind, Vector(null)), (original.name, original.kind, Vector.empty),
        (original.name, original.kind, Vector(original, boundary)),
        (original.name, original.kind, Vector(original, original)), (original.name, original.kind, null))
      When("all admissible and malformed native acknowledgement shapes return normally to observation")
      withBackend(backend) {
        EventPersistenceOperationalAudit.observe(boundary.name, boundary.kind, Consequence.success(Vector(boundary)))
        invalid.foreach { case (name, kind, records) =>
          Given("an original successful source response with a malformed identity shape")
          val response = Consequence.success(records)
          When("only the already captured normal result is observed")
          EventPersistenceOperationalAudit.observe(name, kind, response)
          Then("the original successful result and returned vector reference remain unchanged")
          response.isSuccess shouldBe true
          response match {
            case Consequence.Success(returned) => (returned eq records) shouldBe true
            case _ => fail("original successful acknowledgement changed")
          }
        }
      }
      Then("only the admitted single receipt carries original identity; every other Success remains succeeded with identity=false")
      backend.events.size shouldBe invalid.size + 1
      backend.events.head.attributes.getString("event-id") shouldBe Some(boundary.id.value)
      backend.events.tail.foreach { event =>
        event.attributes.getString("outcome") shouldBe Some("succeeded")
        _without_identity(event.attributes)
        sameAtoms(event.attributes, framed(event.message))
      }
      backend.clear()
      When("a null observation has no recognizable normal source acknowledgement")
      withBackend(backend)(EventPersistenceOperationalAudit.observe(original.name, original.kind, null))
      Then("best effort may suppress without inventing an identity")
      backend.events shouldBe empty
    }
  }

  "EA3 Seventeen hostile source facets" should _scenario("EA3", "E1,E3,E4") {
    "retain private source objects while omitting every marker and failure text without traversing them" in {
      Given("E1,E3,E4: seventeen private facets in name/kind/payload/attributes/conclusion and a throwing formatter/security getter")
      val reads = new AtomicInteger(0)
      val clock = new CountingClock
      val ids = new CountingIds
      val base = context(clock, ids)
      val principal = new Principal {
        def id: PrincipalId = { reads.incrementAndGet(); throw new IllegalStateException(markers.mkString) }
        def attributes: Map[String, String] = { reads.incrementAndGet(); throw new IllegalStateException(markers.mkString) }
      }
      given ExecutionContext = ExecutionContext.withSecurityContext(base, base.security.copy(principal = principal))
      val trap = new Object {
        override def toString: String = { reads.incrementAndGet(); throw new IllegalStateException(markers.mkString) }
      }
      val record = acknowledgement().copy(name = markers.mkString("|"), kind = markers.reverse.mkString("|"),
        payload = Map("private" -> Map("nested" -> trap), "facets" -> markers), attributes = facets.zip(markers).toMap)
      val success = Consequence.success(Vector(record))
      val failure = Consequence.stateInvalid[Vector[EventRecord]](markers.mkString("|"))
      val backend = new RecordingBackend
      val before = (clock.calls.get(), ids.calls.get())
      When("the fixed observer sees the matching private success and original failure response")
      withBackend(backend) {
        EventPersistenceOperationalAudit.observe(record.name, record.kind, success)
        EventPersistenceOperationalAudit.observe(record.name, record.kind, failure)
      }
      Then("only admitted identity/outcome/context atoms appear and every canonical private value stays in its original owner")
      backend.events.size shouldBe 2
      backend.events.foreach { event =>
        markers.foreach { marker =>
          event.message should not include marker
          event.attributes.asMap.values.mkString should not include marker
        }
        sameAtoms(event.attributes, framed(event.message))
        event.attributes.asMap.keySet should not contain "persisted"
        event.message should not include "no-effect"
        event.message should not include "rollback"
      }
      backend.events.last.attributes.getString("outcome") shouldBe Some("failed")
      _without_identity(backend.events.last.attributes)
      (success.toOption.get.head eq record) shouldBe true
      (record.payload("private").asInstanceOf[Map[String, Any]]("nested").asInstanceOf[AnyRef] eq trap) shouldBe true
      record.attributes shouldBe facets.zip(markers).toMap
      reads.get() shouldBe 0
      (clock.calls.get(), ids.calls.get()) shouldBe before
    }
  }

  "EA4 Context and reachable transport bounds" should _scenario("EA4", "E3,E5") {
    "suppress unsafe required context omit unsafe optional span and retain bounded native escaped identity" in {
      Given("E3,E5: original trace/correlation/scope/span references with inclusive512/512/128 and optional512 guards")
      val clock = new CountingClock
      val ids = new CountingIds
      val base = context(clock, ids)
      val record = acknowledgement()
      val response = Consequence.success(Vector(record))
      val backend = new RecordingBackend
      val overtrace = _native_id_at_bytes(value => TraceId(value, "audit"), 513)
      val overcorrelation = _native_id_at_bytes(value => CorrelationId(value, "audit"), 513)
      val overspan = _native_id_at_bytes(value => SpanId(value, "audit", "operation"), 513)
      Vector(overtrace.value, overcorrelation.value, overspan.value).foreach(_.getBytes(UTF_8).length shouldBe 513)
      val invalid = Vector(
        ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = null)),
        ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = overtrace)),
        ExecutionContext.withObservabilityContext(base, base.observability.copy(correlationId = Some(overcorrelation))),
        base.withScope(ScopeContext(ScopeKind.Action, "unsafe\nname", Some(base.scope), base.observability)),
        base.withScope(ScopeContext(ScopeKind.Action, "s" * 129, Some(base.scope), base.observability)))
      val badspan = ExecutionContext.withObservabilityContext(base, base.observability.copy(spanId = Some(overspan)))
      When("invalid required context and an invalid optional span reach the unchanged transport")
      withBackend(backend) {
        invalid.foreach(ctx => EventPersistenceOperationalAudit.observe(record.name, record.kind, response)(using ctx))
      }
      Then("invalid required context emits no frame")
      backend.events shouldBe empty
      When("only optional span is unsafe")
      withBackend(backend)(EventPersistenceOperationalAudit.observe(record.name, record.kind, response)(using badspan))
      Then("the acknowledgement remains while the unsafe span is omitted")
      backend.events.size shouldBe 1
      backend.events.head.attributes.getString("span-id") shouldBe None
      Given("genuine legal-label native trace correlation and span values measured at exactly512 bytes")
      val trace = _native_id_at_bytes(value => TraceId(value, "audit"), 512)
      val correlation = _native_id_at_bytes(value => CorrelationId(value, "audit"), 512)
      val span = _native_id_at_bytes(value => SpanId(value, "audit", "operation"), 512)
      Vector(trace.value, correlation.value, span.value).foreach(_.getBytes(UTF_8).length shouldBe 512)
      val inclusivecontexts = Vector(
        ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = trace)),
        ExecutionContext.withObservabilityContext(base, base.observability.copy(correlationId = Some(correlation))),
        ExecutionContext.withObservabilityContext(base, base.observability.copy(spanId = Some(span))))
      backend.clear()
      When("each actual512-byte context reference is independently admitted with an ordinary receipt")
      withBackend(backend) {
        inclusivecontexts.foreach(ctx => EventPersistenceOperationalAudit.observe(record.name, record.kind, response)(using ctx))
      }
      Then("the inclusive trace correlation and optional span boundaries each produce the exact original primitive frame")
      backend.events.size shouldBe inclusivecontexts.size
      backend.events.zip(inclusivecontexts).foreach { case (event, ctx) =>
        framed(event.message).noSpaces shouldBe expected(ctx, record).noSpaces
        sameAtoms(event.attributes, framed(event.message))
      }
      Given("three original512 context references, scope128 and genuine EventId512 with alternating quote/backslash entropy at explicit epoch")
      val bounded = ExecutionContext.withObservabilityContext(base, base.observability.copy(
        traceId = trace, correlationId = Some(correlation), spanId = Some(span)))
        .withScope(ScopeContext(ScopeKind.Action, "s" * 128, Some(base.scope), base.observability))
      val entropywidth = padding(value => eventId(value).value, 512).length
      val entropy = ("\"\\" * ((entropywidth + 1) / 2)).take(entropywidth)
      val escaped = acknowledgement(entropy).copy(sequence = Long.MaxValue, createdAt = Instant.MAX)
      escaped.id.value.getBytes(UTF_8).length shouldBe 512
      escaped.id.value should include("\"")
      escaped.id.value should include("\\")
      val records = Vector(escaped)
      val escapedresponse = Consequence.success(records)
      val rawid = escaped.id.value
      val before = (clock.calls.get(), ids.calls.get(), rawid, escaped.sequence, escaped.createdAt)
      val json = expected(bounded, escaped)
      backend.clear()
      When("the native closed acknowledgement is observed with the original maximal receipt sequence and Instant.MAX")
      withBackend(backend)(EventPersistenceOperationalAudit.observe(escaped.name, escaped.kind, escapedresponse)(using bounded))
      Then("the exact original escaped identity and primitive frame succeed within the conservative2914/4163 bounds without source effects")
      backend.events.size shouldBe 1
      backend.lines.size shouldBe 1
      framed(backend.events.head.message).noSpaces shouldBe json.noSpaces
      backend.lines.map(framed) shouldBe Vector(json)
      framed(backend.events.head.message).hcursor.get[String]("event-id").toOption shouldBe Some(rawid)
      backend.events.head.attributes.getString("event-id") shouldBe Some(rawid)
      sameAtoms(backend.events.head.attributes, json)
      json.noSpaces.getBytes(UTF_8).length should be <= 2914
      backend.lines.foreach(_.getBytes(UTF_8).length should be <= 4163)
      (escapedresponse.toOption.get eq records) shouldBe true
      (records.head eq escaped) shouldBe true
      (bounded.observability.traceId eq trace) shouldBe true
      (bounded.observability.correlationId.get eq correlation) shouldBe true
      (bounded.observability.spanId.get eq span) shouldBe true
      (clock.calls.get(), ids.calls.get(), escaped.id.value, escaped.sequence, escaped.createdAt) shouldBe before
      Given("the independent valid128 scope and ordinary original identity")
      val inclusive = base.withScope(ScopeContext(ScopeKind.Action, "s" * 128, Some(base.scope), base.observability))
      backend.clear()
      When("a valid bounded scope and ordinary identity are observed")
      withBackend(backend)(EventPersistenceOperationalAudit.observe(record.name, record.kind, response)(using inclusive))
      Then("each emitted body/line obeys4096/8192 and primitive structured agreement")
      backend.events.size shouldBe 1
      backend.lines.foreach(_.getBytes(UTF_8).length should be <= 8192)
      backend.events.foreach(event => sameAtoms(event.attributes, framed(event.message)))
    }
  }

  "EA5 Standard UTF8 file consumer" should _scenario("EA5", "E4,E5,E7") {
    "decode identical multibyte quote-bearing receipt frames and remove only the owned temporary file and directory" in {
      Given("E4,E5,E7: standard FileLogBackend and structured consumer in owned target/event-persistence-operational-audit/work")
      given ExecutionContext = context(new CountingClock, new CountingIds)
      val record = acknowledgement("original-秘密-\"quoted\"")
      val response = Consequence.success(Vector(record))
      val parent = Paths.get("target/event-persistence-operational-audit/work")
      Files.createDirectories(parent)
      val directory = Files.createTempDirectory(parent, "event-")
      val path = directory.resolve("audit.log")
      val backend = new RecordingBackend
      try {
        When("the same safe original receipt is observed by real file and structured backends")
        withBackend(LogBackend.FileLogBackend(path.toString))(EventPersistenceOperationalAudit.observe(record.name, record.kind, response))
        withBackend(backend)(EventPersistenceOperationalAudit.observe(record.name, record.kind, response))
        Then("strict framed UTF8/JSON and primitive atoms agree with bounded output and no private marker")
        val lines = Files.readAllLines(path, UTF_8).asScala.toVector.filter(_.contains(prefix))
        lines.size shouldBe 1
        lines.map(framed) shouldBe backend.events.map(event => framed(event.message))
        backend.events.foreach(event => sameAtoms(event.attributes, framed(event.message)))
        lines.foreach { line =>
          val json = framed(line)
          json.noSpaces.getBytes(UTF_8).length should be <= 4096
          line.getBytes(UTF_8).length should be <= 8192
          line should include(s"$prefix${json.noSpaces.getBytes(UTF_8).length} ${json.noSpaces}")
          markers.foreach(marker => line should not include marker)
        }
      } finally {
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
      }
    }
  }

  "EA6 Observation failure isolation" should _scenario("EA6", "E1,E5,E7") {
    "preserve original acknowledgement providers holder and visibility under Nop refusal and exactly one NonFatal attempt" in {
      Given("E1,E5,E7: original installed backend/visibility, counted context and exact captured source acknowledgement")
      val clock = new CountingClock
      val ids = new CountingIds
      given ExecutionContext = context(clock, ids)
      val record = acknowledgement()
      val response = Consequence.success(Vector(record))
      val backend = new RecordingBackend
      val throwing = new ThrowingBackend
      val holder = LogBackendHolder.backend.get
      val visibility = ObservabilityEngine.visibilityPolicy
      val before = (clock.calls.get(), ids.calls.get())
      When("observation meets Nop, visibility refusal and an event-prefix throwing backend")
      withBackend(LogBackend.NopLogBackend)(EventPersistenceOperationalAudit.observe(record.name, record.kind, response))
      withBackend(backend, VisibilityPolicy(minLevel = LogLevel.Error))(EventPersistenceOperationalAudit.observe(record.name, record.kind, response))
      withBackend(throwing)(EventPersistenceOperationalAudit.observe(record.name, record.kind, response))
      Then("logging cannot propagate retry mutate source or add provider work and exact installed objects are restored")
      backend.events shouldBe empty
      throwing.calls.get() shouldBe 1
      (response.toOption.get.head eq record) shouldBe true
      (clock.calls.get(), ids.calls.get()) shouldBe before
      (LogBackendHolder.backend.get eq holder) shouldBe true
      (ObservabilityEngine.visibilityPolicy eq visibility) shouldBe true
    }
  }

  private def _native_id_at_bytes[A <: UniversalId](constructor: String => A, bound: Int): A = {
    val sample = constructor("n")
    val width = math.max(1, bound - sample.value.getBytes(UTF_8).length + 1)
    def _candidate_(labelwidth: Int, remaining: Int): A = {
      if (remaining == 0) fail(s"no genuine native ID measured at $bound UTF8 bytes within64 attempts")
      val candidate = constructor("n" * labelwidth)
      val actual = candidate.value.getBytes(UTF_8).length
      if (actual == bound) candidate
      else _candidate_(math.max(1, labelwidth + bound - actual), remaining - 1)
    }
    _candidate_(width, 64)
  }

  private def _without_identity(record: org.goldenport.record.Record): Unit = {
    record.getBoolean("event-identity-available") shouldBe Some(false)
    record.getString("event-id") shouldBe None
    record.getLong("event-sequence") shouldBe None
    record.getString("event-lane") shouldBe None
    record.getString("occurred-at") shouldBe None
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:event-persistence-operational-audit, example:$example, rules:$rules, phase:69.7, slice:C7")
}
