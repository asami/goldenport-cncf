package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, ScopeContext, ScopeKind, SpanId, TraceId}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  3, 2026
 * @version Oct. 31, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.DoNotDiscover
final class JobOperationalAuditTransportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobLifecycleOperationalAuditFixtures.*

  "LT1 Original experience wire" should _scenario("LT1", "R5,R7,R8") {
    "retain exact ordered primitive framing and original returned facade object" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R5,R7,R8; LT1: an original opaque Job view with safe span and correlation")
      val base = JobExperienceOperationalAuditFixtures.context()
      given ExecutionContext = ExecutionContext.withObservabilityContext(base, base.observability.copy(
        spanId = Some(SpanId("audit", "span", "operation")), correlationId = Some(CorrelationId("audit", "boundary"))))
      val context = summon[ExecutionContext]
      val jobid = JobId("native", "job", Some(instant), Some("opaque_秘密_\"quoted\""))
      val result = Consequence.success(JobExperienceOperationalAuditFixtures.view(jobid))
      val backend = new RecordingBackend
      When("the normal facade result is observed through the extracted transport")
      val observed = withBackend(backend)(JobExperienceOperationalAudit.observeGet(result))
      Then("the original result and experience JSON order and structured atoms remain exact")
      (observed eq result) shouldBe true
      val fields = Vector("audit.kind" -> Json.fromString("job-experience"), "operation" -> Json.fromString("get"),
        "outcome" -> Json.fromString("returned"), "record-kind" -> Json.fromString("call"),
        "trace-id" -> Json.fromString(context.observability.traceId.value),
        "span-id" -> Json.fromString(context.observability.spanId.get.value),
        "correlation-id" -> Json.fromString(context.observability.correlationId.get.value),
        "job-id" -> Json.fromString(jobid.value), "status" -> Json.fromString("failed"), "origin" -> Json.fromString("runtime"),
        "attempt-count" -> Json.fromInt(3), "recovery-required" -> Json.fromBoolean(true))
      val expected = Json.fromFields(fields)
      val event = backend.experience.head
      framed(event.message).noSpaces shouldBe expected.noSpaces
      event.message should include(s"job.audit.v1 bytes=${expected.noSpaces.getBytes(UTF_8).length} ${expected.noSpaces}")
      backend.lifecycle shouldBe empty
      _same_atoms(event.attributes, expected)
    }
  }

  "LT2 Distinct byte bounded lifecycle wire" should _scenario("LT2", "R5,R7") {
    "escape quotes and Unicode and accept exactly 4096 UTF8 bytes while omitting oversized bodies" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R5,R7; LT2: a fixed lifecycle transport and Unicode/quote atoms")
      given ExecutionContext = JobExperienceOperationalAuditFixtures.context()
      val backend = new RecordingBackend
      When("a lifecycle row with original quote and Unicode atoms is emitted")
      withBackend(backend)(JobOperationalAuditTransport.lifecycle("returned", "call", Vector("sample" -> "秘密\"\\")))
      val first = backend.lifecycle.head
      val basebytes = framed(first.message).noSpaces.getBytes(UTF_8).length
      val fill = "a" * (4096 - basebytes)
      backend.clear()
      withBackend(backend) {
        JobOperationalAuditTransport.lifecycle("returned", "call", Vector("sample" -> ("秘密\"\\" + fill)))
        JobOperationalAuditTransport.lifecycle("returned", "call", Vector("sample" -> ("秘密\"\\" + fill + "a")))
      }
      Then("one complete 4096-byte body survives and the 4097-byte body creates no truncated line")
      backend.lifecycle.size shouldBe 1
      val event = backend.lifecycle.head
      val json = framed(event.message)
      json.noSpaces.getBytes(UTF_8).length shouldBe 4096
      json.hcursor.get[String]("sample").toOption shouldBe Some("秘密\"\\" + fill)
      event.message should include("job.lifecycle.audit.v1 bytes=4096 ")
      event.message.getBytes(UTF_8).length should be <= 8192
      event.attributes.getString("audit.kind") shouldBe Some("job-lifecycle")
      _same_atoms(event.attributes, json)
    }
  }

  "LT3 Original context and sink guards" should _scenario("LT3", "R5,R7,R8") {
    "refuse invalid required context omit optional span and preserve providers under hidden Nop null and throwing loggers" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R5,R7,R8; LT3: counted providers and seed69709501 ordinary shrinking over required trace boundaries")
      withNative() { fixture =>
        val base = fixture.context
        val backend = new RecordingBackend
        val before = fixture.observations
        val values = Gen.choose(1, 650)
        val property = Prop.forAll(values) { length =>
          val trace = TraceId("t" * length, "audit")
          val context = ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = trace))
          backend.clear()
          withBackend(backend)(JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using context))
          backend.lifecycle.size shouldBe (if (trace.value.getBytes(UTF_8).length <= 512) 1 else 0)
          backend.lifecycle.foreach(_.attributes.getString("trace-id") shouldBe Some(trace.value))
          fixture.observations shouldBe before
          true
        }
        When("ScalaCheck emits real transport rows with 24 successful seeded shrinking checks")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709501L)), property)
        Then("required references retain their original values or suppress the entire row without provider effects")
        withClue(s"LT3 seed69709501 $checked ") { checked.passed shouldBe true }
        checked.succeeded should be >= 24
        val badcorrelation = ExecutionContext.withObservabilityContext(base, base.observability.copy(correlationId = Some(CorrelationId("c" * 600, "audit"))))
        val badscope = base.withScope(ScopeContext(ScopeKind.Action, "unsafe\nname", Some(base.scope), base.observability))
        val longscope = base.withScope(ScopeContext(ScopeKind.Action, "s" * 129, Some(base.scope), base.observability))
        val badspan = ExecutionContext.withObservabilityContext(base, base.observability.copy(spanId = Some(SpanId("s" * 600, "audit", "operation"))))
        backend.clear()
        When("unsafe correlation and scopes are refused and unsafe optional span is omitted")
        withBackend(backend) {
          Vector(badcorrelation, badscope, longscope).foreach(context => JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using context))
          JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using badspan)
        }
        Then("only the optional-span case emits and it has no alternate span")
        backend.lifecycle.size shouldBe 1
        backend.lifecycle.head.attributes.getString("span-id") shouldBe None
        backend.clear()
        When("hidden Nop null and throwing backends receive the same normal observation")
        withBackend(backend, VisibilityPolicy(minLevel = LogLevel.Error))(JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using base))
        withBackend(LogBackend.NopLogBackend)(JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using base))
        withBackend(null)(JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using base))
        val throwing = new JobExperienceOperationalAuditFixtures.ThrowingBackend
        withBackend(throwing)(JobOperationalAuditTransport.lifecycle("returned", "call", Vector.empty)(using base))
        val hostile = new Object { override def toString: String = throw new IllegalStateException(secret) }
        withBackend(backend)(JobOperationalAuditTransport.lifecycle("returned", "call",
          Vector("unknown" -> hostile).asInstanceOf[Vector[(String, String | Int | Long | Boolean)]])(using base))
        withBackend(backend)(JobOperationalAuditTransport.lifecycle("returned", "call", Vector("null" -> null))(using base))
        Then("no fallback or raw object formatting occurs and provider counts stay exact")
        backend.events shouldBe empty
        throwing.calls.get() shouldBe 1
        fixture.observations shouldBe before
      }
    }
  }

  "LT4 Standard file and saved bootstrap consumers" should _scenario("LT4", "R7,R8") {
    "keep the two frames separate with identical primitive atoms and restore the original holder and visibility" in {
      Given("docs/spec/job-lifecycle-operational-audit-contract.md R7,R8; LT4: standard FileLogBackend and a private bootstrap buffer")
      given ExecutionContext = JobExperienceOperationalAuditFixtures.context()
      val original = LogBackendHolder.backend.get
      val policy = ObservabilityEngine.visibilityPolicy
      val parent = Paths.get("target/job-lifecycle-operational-audit/work")
      Files.createDirectories(parent)
      val directory = Files.createTempDirectory(parent, "transport-")
      val path = directory.resolve("audit.log")
      val backend = new RecordingBackend
      val replayed = new RecordingBackend
      val bootstrap = LogBackend.BootstrapLogBackend(LogBackend.NopLogBackend)
      def _emit_both_(): Unit = {
        JobOperationalAuditTransport.experience("get", "returned", "call", Vector("job-id" -> "opaque秘密"))
        JobOperationalAuditTransport.lifecycle("returned", "call", Vector("job-id" -> "opaque秘密"))
      }
      try {
        When("both fixed frames reach the standard file logger and a saved bootstrap buffer")
        withBackend(bootstrap) {
          _emit_both_()
          withBackend(LogBackend.FileLogBackend(path.toString))(_emit_both_())
          (LogBackendHolder.backend.get eq bootstrap) shouldBe true
          bootstrap.replayTo(replayed)
          withBackend(backend)(_emit_both_())
        }
        val lines = Files.readAllLines(path, UTF_8).asScala.toVector
        Then("file and retained bootstrap decode the same two observations without cross-frame consumption")
        lines.map(framed) shouldBe backend.lines.map(framed)
        replayed.lines.map(framed) shouldBe backend.lines.map(framed)
        backend.experience.size shouldBe 1
        backend.lifecycle.size shouldBe 1
        backend.events.foreach(event => _same_atoms(event.attributes, framed(event.message)))
        (LogBackendHolder.backend.get eq original) shouldBe true
        ObservabilityEngine.visibilityPolicy shouldBe policy
        lines.foreach(line => line.getBytes(UTF_8).length should be <= 8192)
      } finally {
        val entries = Files.walk(directory)
        try entries.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
        finally entries.close()
      }
    }
  }

  private def _same_atoms(record: org.goldenport.record.Record, json: Json): Unit = {
    json.asObject.get.keys.toSet shouldBe record.asMap.keySet
    json.asObject.get.toVector.foreach { case (key, value) =>
      value.fold(throw new IllegalArgumentException("null atom"),
        boolean => record.getBoolean(key) shouldBe Some(boolean),
        number => record.getLong(key) shouldBe number.toLong,
        string => record.getString(key) shouldBe Some(string),
        _ => fail("array atom"), _ => fail("object atom"))
    }
  }

  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-lifecycle-operational-audit, example:$example, rules:$rules, phase:69.7")
}
