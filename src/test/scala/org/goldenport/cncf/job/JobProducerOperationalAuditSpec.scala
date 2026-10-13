package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, ExecutionContext, Principal, PrincipalId, ScopeContext, ScopeKind, SpanId, TraceId}
import org.goldenport.cncf.log.{LogBackend, LogBackendHolder}
import org.goldenport.cncf.observability.{LogLevel, ObservabilityEngine, VisibilityPolicy}
import org.goldenport.cncf.workflow.{WorkflowDefinitionId, WorkflowInstance, WorkflowInstanceId, WorkflowStatus}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class JobProducerOperationalAuditSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import JobProducerOperationalAuditTestSupport.*

  "PA1 Original closed producer identities" should _scenario("PA1", "R1,R4,R5,R6") {
    "retain exact canonical values and primitive JSON and Record atoms under ordinary shrinking" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R1,R4,R5,R6; PA1: original native identities and seed69709601/min24 ordinary shrinking variants")
      given ExecutionContext = context(new CountingClock, new CountingIds)
      val backend = new RecordingBackend
      val variants = Gen.choose(0, 90)
      val property = Prop.forAll(variants) { variant =>
        val entropy = s"opaque-秘密-\"$variant\""
        val jobid = _job(entropy)
        val actionid = _action(entropy)
        val instance = _instance(_workflow(entropy))
        val definitionid = _definition(entropy)
        val snapshot = JobDefinitionSnapshot(s"stored-秘密-$variant", secret, None, Some(secret), Some(secret))
        val response = Consequence.success(OperationResponse.Scalar(secret))
        val before = (jobid.value, actionid.value, instance, definitionid.value, snapshot, response)
        backend.clear()
        withBackend(backend) {
          JobProducerOperationalAudit.workflow(definitionid, instance, jobid, actionid)
          JobProducerOperationalAudit.jcl(jobid, actionid, Some(snapshot), response)
        }
        backend.events.size shouldBe 2
        val common = _common(summon[ExecutionContext])
        val workflow = Json.fromFields(common ++ Vector(
          "producer" -> Json.fromString("workflow"), "job-id" -> Json.fromString(jobid.value),
          "operation-action-id" -> Json.fromString(actionid.value),
          "workflow-instance-id" -> Json.fromString(instance.id.value),
          "workflow-definition-id" -> Json.fromString(definitionid.value),
          "workflow-status" -> Json.fromString("submitted"), "occurred-at" -> Json.fromString(instance.updatedAt.toString)))
        val jcl = Json.fromFields(common ++ Vector(
          "producer" -> Json.fromString("jcl-action"), "job-id" -> Json.fromString(jobid.value),
          "operation-action-id" -> Json.fromString(actionid.value),
          "job-definition-present" -> Json.fromBoolean(true), "job-definition-id" -> Json.fromString(snapshot.id),
          "result-outcome" -> Json.fromString("succeeded")))
        backend.events.map(event => framed(event.message).noSpaces) shouldBe Vector(workflow.noSpaces, jcl.noSpaces)
        backend.events.foreach(event => _same_atoms(event.attributes, framed(event.message)))
        backend.lines.map(framed) shouldBe Vector(workflow, jcl)
        (jobid.value, actionid.value, instance, definitionid.value, snapshot, response) shouldBe before
        (response eq before._6) shouldBe true
        true
      }
      When("both native producer associations are observed for the ordinary shrinking identity property")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709601L)), property)
      Then("every successful variant preserves original identities and exact two closed frames")
      withClue(s"PA1 seed69709601 $checked ") { checked.passed shouldBe true }
      checked.succeeded should be >= 24
    }
  }

  "PA2 Reachable native identity boundaries" should _scenario("PA2", "R5") {
    "accept inclusive512 bytes suppress513 controls and null references and omit malformed optional String IDs" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R5; PA2: final native required IDs cannot expose empty canonical value; explicit epoch/entropy constructors measure real UTF8 prefix overhead")
      given ExecutionContext = context(new CountingClock, new CountingIds)
      val backend = new RecordingBackend
      val jobid = _job("original")
      val actionid = _action("original")
      val instance = _instance(_workflow("original"))
      val definitionid = _definition("original")
      val response = Consequence.success(OperationResponse.Scalar("original"))
      val constructors: Vector[String => String] = Vector(
        entropy => _job(entropy).value, entropy => _action(entropy).value,
        entropy => _workflow(entropy).value, entropy => _definition(entropy).value)
      When("each actual required type is observed at inclusive512 and adjacent513 UTF8 bytes and with ISO controls")
      withBackend(backend) {
        constructors.zipWithIndex.foreach { case (constructor, index) =>
          Vector(512, 513).foreach { bound =>
            val entropy = _padding(constructor, bound)
            constructor(entropy).getBytes(UTF_8).length shouldBe bound
            backend.clear()
            JobProducerOperationalAudit.workflow(
              if (index == 3) _definition(entropy) else definitionid,
              if (index == 2) instance.copy(id = _workflow(entropy)) else instance,
              if (index == 0) _job(entropy) else jobid,
              if (index == 1) _action(entropy) else actionid)
            backend.events.size shouldBe (if (bound == 512) 1 else 0)
            if (bound == 512) {
              val key = Vector("job-id", "operation-action-id", "workflow-instance-id", "workflow-definition-id")(index)
              backend.events.head.attributes.getString(key) shouldBe Some(constructor(entropy))
            }
            if (index < 2) {
              backend.clear()
              JobProducerOperationalAudit.jcl(if (index == 0) _job(entropy) else jobid,
                if (index == 1) _action(entropy) else actionid, None, response)
              backend.events.size shouldBe (if (bound == 512) 1 else 0)
            }
          }
          backend.clear()
          val entropy = "control\nvalue"
          constructor(entropy).exists(c => Character.isISOControl(c)) shouldBe true
          JobProducerOperationalAudit.workflow(if (index == 3) _definition(entropy) else definitionid,
            if (index == 2) instance.copy(id = _workflow(entropy)) else instance,
            if (index == 0) _job(entropy) else jobid, if (index == 1) _action(entropy) else actionid)
          if (index < 2) JobProducerOperationalAudit.jcl(if (index == 0) _job(entropy) else jobid,
            if (index == 1) _action(entropy) else actionid, None, response)
          backend.events shouldBe empty
        }
        backend.clear()
        JobProducerOperationalAudit.workflow(definitionid, instance, null, actionid)
        JobProducerOperationalAudit.workflow(definitionid, instance, jobid, null)
        JobProducerOperationalAudit.workflow(null, instance, jobid, actionid)
        JobProducerOperationalAudit.workflow(definitionid, null, jobid, actionid)
        JobProducerOperationalAudit.workflow(definitionid, instance.copy(id = null), jobid, actionid)
        JobProducerOperationalAudit.jcl(null, actionid, None, response)
        JobProducerOperationalAudit.jcl(jobid, null, None, response)
        backend.events shouldBe empty
        Vector("", null, "control\nvalue", "秘" + "a" * 510).foreach { id =>
          backend.clear()
          JobProducerOperationalAudit.jcl(jobid, actionid, Some(JobDefinitionSnapshot(id, secret, None, None, None)), response)
          backend.events.size shouldBe 1
          val attributes = backend.events.head.attributes
          attributes.getBoolean("job-definition-present") shouldBe Some(true)
          attributes.getBoolean("job-definition-id-omitted") shouldBe Some(true)
          attributes.getString("job-definition-id") shouldBe None
        }
        backend.clear()
        JobProducerOperationalAudit.jcl(jobid, actionid, Some(null), response)
        backend.events.head.attributes.getBoolean("job-definition-id-omitted") shouldBe Some(true)
        backend.events.head.attributes.getBoolean("job-definition-present") shouldBe Some(true)
        backend.clear()
        JobProducerOperationalAudit.jcl(jobid, actionid, None, response)
        backend.events.head.attributes.getBoolean("job-definition-present") shouldBe Some(false)
        backend.events.head.attributes.getBoolean("job-definition-id-omitted") shouldBe None
        backend.clear()
        val optional = "秘" + "a" * 509
        optional.getBytes(UTF_8).length shouldBe 512
        JobProducerOperationalAudit.jcl(jobid, actionid, Some(JobDefinitionSnapshot(optional, secret, None, None, None)), response)
        backend.events.head.attributes.getString("job-definition-id") shouldBe Some(optional)
        constructors.foreach(constructor => constructor("").nonEmpty shouldBe true)
        backend.clear()
        JobProducerOperationalAudit.workflow(_definition(""), _instance(_workflow("")), _job(""), _action(""))
        backend.events.size shouldBe 1
      }
      Then("the defensive nonempty rule remains without fabricated empty native evidence and optional presence stays truthful")
      backend.events.head.attributes.getString("job-id") shouldBe Some(_job("").value)
    }
  }

  "PA3 Hostile source privacy" should _scenario("PA3", "R1,R4,R5") {
    "leave source payload formatting and security getters unread under the finite shrinking hostile property" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R1,R4,R5; PA3: two hostile value variants with seed69709601/min24 and a payload formatter and principal getters that must not run")
      val reads = new AtomicInteger(0)
      val base = context(new CountingClock, new CountingIds)
      val principal = new Principal {
        def id: PrincipalId = { reads.incrementAndGet(); throw new IllegalStateException(secret) }
        def attributes: Map[String, String] = { reads.incrementAndGet(); throw new IllegalStateException(secret) }
      }
      given ExecutionContext = ExecutionContext.withSecurityContext(base, base.security.copy(principal = principal))
      val backend = new RecordingBackend
      val variants = Gen.choose(0, 1)
      val property = Prop.forAll(variants) { variant =>
        val marker = if (variant == 0) secret else secret + "-tenant-subject-JCL-key-source"
        val payload = new Object {
          override def toString: String = { reads.incrementAndGet(); throw new IllegalStateException(marker) }
        }
        val response = Consequence.success(OperationResponse.RecordResponse(Record.dataAuto("private" -> payload)))
        val snapshot = JobDefinitionSnapshot("original-definition", marker, None, Some(marker), Some(marker))
        val instance = _instance(_workflow("original")).copy(registrationName = marker, entityCollection = marker,
          entityId = marker, triggeringEventName = marker, lastAction = Some(marker))
        reads.get() shouldBe 0
        backend.clear()
        withBackend(backend) {
          JobProducerOperationalAudit.workflow(_definition("original"), instance, _job("original"), _action("original"))
          JobProducerOperationalAudit.jcl(_job("original"), _action("original"), Some(snapshot), response)
          JobProducerOperationalAudit.jcl(_job("original"), _action("original"), Some(snapshot), Consequence.argumentInvalid(marker))
        }
        reads.get() shouldBe 0
        backend.events.size shouldBe 3
        backend.events.last.attributes.getString("result-outcome") shouldBe Some("failed")
        backend.events.foreach { event =>
          event.message should not include marker
          event.attributes.asMap.values.mkString should not include marker
          _same_atoms(event.attributes, framed(event.message))
        }
        response.toOption.get match {
          case OperationResponse.RecordResponse(record) => (record.getAny("private").get.asInstanceOf[AnyRef] eq payload) shouldBe true
          case _ => fail("original hostile response changed")
        }
        true
      }
      When("native captured source associations are observed across the finite hostile variants with ordinary shrinking")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(69709601L)), property)
      Then("only admitted IDs and fixed outcome facts appear and no hostile formatting or principal getter runs")
      withClue(s"PA3 seed69709601 $checked ") { checked.passed shouldBe true }
      checked.succeeded should be >= 24
      reads.get() shouldBe 0
    }
  }

  "PA4 Context and transport limits" should _scenario("PA4", "R5,R6") {
    "suppress unsafe context and reachable escaped body overflow preserve optional span and prior frame bytes" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R5,R6; PA4: required context guards and four actual native512-byte quote-entropy IDs that can overflow the closed body")
      val base = context(new CountingClock, new CountingIds)
      val backend = new RecordingBackend
      val badtrace = ExecutionContext.withObservabilityContext(base, base.observability.copy(traceId = TraceId("t" * 600, "audit")))
      val badcorrelation = ExecutionContext.withObservabilityContext(base, base.observability.copy(correlationId = Some(CorrelationId("c" * 600, "audit"))))
      val badscope = base.withScope(ScopeContext(ScopeKind.Action, "unsafe\nname", Some(base.scope), base.observability))
      val longscope = base.withScope(ScopeContext(ScopeKind.Action, "s" * 129, Some(base.scope), base.observability))
      val badspan = ExecutionContext.withObservabilityContext(base, base.observability.copy(spanId = Some(SpanId("s" * 600, "audit", "operation"))))
      When("unsafe required context is refused and unsafe optional span is omitted")
      withBackend(backend) {
        Vector(badtrace, badcorrelation, badscope, longscope).foreach(ctx => _observe()(using ctx))
      }
      Then("none of the unsafe required contexts produces an association")
      backend.events shouldBe empty
      withBackend(backend)(_observe()(using badspan))
      backend.events.size shouldBe 1
      backend.events.head.attributes.getString("span-id") shouldBe None
      val jobid = _job(_quote_padding(entropy => _job(entropy).value))
      val actionid = _action(_quote_padding(entropy => _action(entropy).value))
      val instance = _instance(_workflow(_quote_padding(entropy => _workflow(entropy).value)))
      val definitionid = _definition(_quote_padding(entropy => _definition(entropy).value))
      Vector(jobid.value, actionid.value, instance.id.value, definitionid.value).foreach(_.getBytes(UTF_8).length shouldBe 512)
      val expected = Json.fromFields(_common(base) ++ Vector("producer" -> Json.fromString("workflow"),
        "job-id" -> Json.fromString(jobid.value), "operation-action-id" -> Json.fromString(actionid.value),
        "workflow-instance-id" -> Json.fromString(instance.id.value), "workflow-definition-id" -> Json.fromString(definitionid.value),
        "workflow-status" -> Json.fromString("submitted"), "occurred-at" -> Json.fromString(instance.updatedAt.toString)))
      expected.noSpaces.getBytes(UTF_8).length should be > 4096
      backend.clear()
      When("the actual closed producer body overflows4096 bytes")
      withBackend(backend)(JobProducerOperationalAudit.workflow(definitionid, instance, jobid, actionid)(using base))
      Then("the full frame is omitted and no truncation or line-only impossible fixture is invented")
      backend.events shouldBe empty
      backend.lines shouldBe empty
      val prior = new JobLifecycleOperationalAuditFixtures.RecordingBackend
      val experience = Vector[(String, String | Int | Long | Boolean)]("job-id" -> "original-秘密")
      withBackend(prior) {
        JobOperationalAuditTransport.experience("get", "returned", "call", experience)(using base)
        JobOperationalAuditTransport.lifecycle("returned", "call", experience)(using base)
      }
      val before = prior.events.map(_.message)
      prior.clear()
      withBackend(prior) {
        JobOperationalAuditTransport.experience("get", "returned", "call", experience)(using base)
        _observe()(using base)
        JobOperationalAuditTransport.lifecycle("returned", "call", experience)(using base)
      }
      prior.events.map(_.message) shouldBe before
      withBackend(backend)(_observe()(using base))
      backend.lines.foreach(_.getBytes(UTF_8).length should be <= 8192)
      backend.events.foreach(event => _same_atoms(event.attributes, framed(event.message)))
    }
  }

  "PA5 Standard UTF8 file consumer" should _scenario("PA5", "R4,R6,R8") {
    "decode exact multibyte producer bytes with identical structured atoms and bounded owned cleanup" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R4,R6,R8; PA5: standard FileLogBackend and owned bounded target directory")
      given ExecutionContext = context(new CountingClock, new CountingIds)
      val parent = Paths.get("target/job-producer-operational-audit/work")
      Files.createDirectories(parent)
      val directory = Files.createTempDirectory(parent, "producer-")
      val path = directory.resolve("audit.log")
      val backend = new RecordingBackend
      try {
        When("the same original multibyte associations reach the native file and structured consumers")
        withBackend(LogBackend.FileLogBackend(path.toString))(_observe("秘密\"original"))
        withBackend(backend)(_observe("秘密\"original"))
        val lines = Files.readAllLines(path, UTF_8).asScala.toVector.filter(_.contains("job.producer.audit.v1 bytes="))
        Then("the standard file contains the exact bounded framed JSON with matching primitive atoms and no private source text")
        lines.size shouldBe 1
        lines.map(framed) shouldBe backend.events.map(event => framed(event.message))
        backend.events.foreach(event => _same_atoms(event.attributes, framed(event.message)))
        lines.foreach { line =>
          line.getBytes(UTF_8).length should be <= 8192
          line should not include secret
          val json = framed(line)
          line should include(s"job.producer.audit.v1 bytes=${json.noSpaces.getBytes(UTF_8).length} ${json.noSpaces}")
        }
      } finally {
        Files.deleteIfExists(path)
        Files.deleteIfExists(directory)
      }
    }
  }

  "PA6 Logging has no domain effects" should _scenario("PA6", "R1,R6,R8") {
    "preserve provider counts and exact holder and visibility under Nop refusal and one throwing attempt" in {
      Given("docs/spec/job-producer-operational-correlation-contract.md R1,R6,R8; PA6: counted original context and original installed backend and visibility")
      val clock = new CountingClock
      val ids = new CountingIds
      given ExecutionContext = context(clock, ids)
      val backend = new RecordingBackend
      val throwing = new ThrowingBackend
      val original = LogBackendHolder.backend.get
      val policy = ObservabilityEngine.visibilityPolicy
      val before = (clock.calls.get(), ids.calls.get())
      When("the normal observation meets Nop visibility refusal and a throwing native backend")
      withBackend(LogBackend.NopLogBackend)(_observe())
      withBackend(backend, VisibilityPolicy(minLevel = LogLevel.Error))(_observe())
      withBackend(throwing)(_observe())
      Then("no refusal or NonFatal logging failure escapes retries or consumes any extra native provider call")
      backend.events shouldBe empty
      throwing.calls.get() shouldBe 1
      (clock.calls.get(), ids.calls.get()) shouldBe before
      (LogBackendHolder.backend.get eq original) shouldBe true
      ObservabilityEngine.visibilityPolicy shouldBe policy
    }
  }

  private def _job(entropy: String): JobId = JobId("native", "producer", Some(Instant.EPOCH), Some(entropy))
  private def _action(entropy: String): ActionId = ActionId("native", "producer", Some(Instant.EPOCH), Some(entropy))
  private def _workflow(entropy: String): WorkflowInstanceId = WorkflowInstanceId("native", "producer", Some(Instant.EPOCH), Some(entropy))
  private def _definition(entropy: String): WorkflowDefinitionId = WorkflowDefinitionId("native", "producer", Some(Instant.EPOCH), Some(entropy))
  private def _instance(id: WorkflowInstanceId): WorkflowInstance = WorkflowInstance(id, secret, secret, secret,
    WorkflowStatus.Submitted, Some(secret), secret, Instant.EPOCH, Instant.EPOCH, Some(secret), Vector.empty, Vector.empty)
  private def _observe(entropy: String = "original")(using ExecutionContext): Unit =
    JobProducerOperationalAudit.workflow(_definition(entropy), _instance(_workflow(entropy)), _job(entropy), _action(entropy))
  private def _padding(constructor: String => String, bound: Int): String =
    "秘" + "a" * (bound - constructor("").getBytes(UTF_8).length - "秘".getBytes(UTF_8).length)
  private def _quote_padding(constructor: String => String): String =
    "\"" * (512 - constructor("").getBytes(UTF_8).length)
  private def _common(ctx: ExecutionContext): Vector[(String, Json)] = Vector(
    "audit.kind" -> Json.fromString("job-producer"), "operation" -> Json.fromString("correlate"),
    "outcome" -> Json.fromString("submitted"), "record-kind" -> Json.fromString("association"),
    "trace-id" -> Json.fromString(ctx.observability.traceId.value)) ++
    ctx.observability.spanId.map(id => "span-id" -> Json.fromString(id.value)).toVector ++
    ctx.observability.correlationId.map(id => "correlation-id" -> Json.fromString(id.value)).toVector
  private def _same_atoms(record: Record, json: Json): Unit = {
    json.asObject.get.keys.toSet shouldBe record.asMap.keySet
    json.asObject.get.toVector.foreach { case (key, atom) =>
      atom.fold(fail("null atom"), boolean => record.getBoolean(key) shouldBe Some(boolean),
        number => record.getLong(key) shouldBe number.toLong, string => record.getString(key) shouldBe Some(string),
        _ => fail("array atom"), _ => fail("object atom"))
    }
  }
  private def _scenario(example: String, rules: String) =
    afterWord(s"in spec:job-producer-operational-correlation, example:$example, rules:$rules, phase:69.7")
}
