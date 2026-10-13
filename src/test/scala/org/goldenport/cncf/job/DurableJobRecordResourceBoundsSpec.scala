package org.goldenport.cncf.job

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

import org.goldenport.Consequence
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
@org.scalatest.tags.Slow
final class DurableJobRecordResourceBoundsSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Durable record resource admission" should {
    "received text" which {
      "include surrounding whitespace in the inclusive ASCII byte budget" in {
        Given("test-owned V1 and V2 records padded with JSON whitespace to exactly 1,048,576 bytes")
        val records = Vector(_manual_record(_body("ascii", false), false), _manual_record(_body("ascii", true), true))
        val texts = records.map { record =>
          val text = _wire(record)
          "\n" + text + (" " * (_maximum_bytes - _bytes(text) - 1))
        }
        When("the exact-limit texts and their one-byte-longer counterparts are admitted")
        val admitted = texts.map(DurableJobRecordCodec.decode(_, _access))
        val refused = texts.map(text => DurableJobRecordCodec.decode(text + " ", _access))
        val messages = texts.map(text => DurableJobRecordCodec._parse_json(text + " ").left.toOption)
        Then("exact-limit records survive unchanged and the extra whitespace produces only the byte-limit refusal")
        texts.foreach(text => _bytes(text) shouldBe _maximum_bytes)
        admitted shouldBe records.map(Consequence.success(_))
        refused.foreach(_ shouldBe a[Consequence.Failure[_]])
        messages shouldBe Vector.fill(2)(Some(_byte_message))
      }

      "count UTF-8 bytes rather than UTF-16 string length at the inclusive limit" in {
        Given("valid signed V1 and V2 texts containing multibyte and supplementary Unicode at the exact byte limit")
        val records = Vector(false, true).map(pending => _manual_record(_sized_body(_maximum_bytes, pending, "界😀"), pending))
        val texts = records.map(_wire)
        When("the Unicode records and one-byte-longer wire texts are decoded")
        val admitted = texts.map(DurableJobRecordCodec.decode(_, _access))
        val refused = texts.map(text => DurableJobRecordCodec._parse_json(text + " "))
        Then("shorter character counts do not weaken the byte boundary and exact-limit Unicode remains intact")
        texts.foreach { text =>
          text.length should be < _maximum_bytes
          _bytes(text) shouldBe _maximum_bytes
        }
        admitted shouldBe records.map(Consequence.success(_))
        refused.foreach(_.left.toOption shouldBe Some(_byte_message))
      }

      "refuse oversized text before grammar or schema interpretation" in {
        Given("malformed sensitive text exceeding the character budget and Unicode text exceeding only the byte budget")
        val texts = Vector("private-subject{" + ("x" * _maximum_bytes), "界" * (_maximum_bytes / 3 + 1))
        When("the parser entry and public decoder receive those hostile texts")
        val parsed = texts.map(DurableJobRecordCodec._parse_json)
        val decoded = texts.map(DurableJobRecordCodec.decode(_, _access))
        Then("both fail with the constant byte-limit message and no decoded value")
        parsed.foreach(_.left.toOption shouldBe Some(_byte_message))
        decoded.foreach(_.toOption shouldBe None)
      }

      "allow depth 64 and refuse depth 65 or extreme nesting before parsing" in {
        Given("JSON arrays nested to 64, 65 and 100,000 containers within the byte budget")
        val boundary = Vector(_nested(64), ("{\"v\":" * 64) + "0" + ("}" * 64))
        val hostile = Vector(_nested(65), _nested(100000), ("{\"v\":" * 65) + "0" + ("}" * 65))
        When("the parser entry considers their container depth")
        val admitted = boundary.map(DurableJobRecordCodec._parse_json)
        val refused = hostile.map(DurableJobRecordCodec._parse_json)
        val decoded = hostile.map(DurableJobRecordCodec.decode(_, _access))
        Then("the inclusive depth boundary reaches the grammar parser and deeper inputs return a bounded refusal")
        admitted.foreach(_.isRight shouldBe true)
        refused.foreach(_.left.toOption shouldBe Some(_depth_message))
        decoded.foreach(_.toOption shouldBe None)
      }

      "ignore escaped quotes, backslashes and braces inside admitted string values" in {
        Given("a valid record whose identity contains many escaped quotes, backslashes and container characters")
        val id = "quoted-" + ("\\\"{[\\\\]}" * 100)
        val record = _manual_record(_body(id, false), false)
        val text = _wire(record)
        When("the signed text passes pre-parser depth admission and authorized decoding")
        val decoded = DurableJobRecordCodec.decode(text, _access)
        Then("quoted delimiters never contribute nesting and every escape survives unchanged")
        decoded shouldBe Consequence.success(record)
        decoded.toOption.map(_.body.identity.jobId) shouldBe Some(id)
      }

      "retain strict grammar, duplicate-key and closed-schema refusal below the budgets" in {
        Given("a valid signed text and under-limit malformed, duplicate-key and unknown-field variants")
        val text = _wire(_manual_record(_body("strict", false), false))
        val texts = Vector(text.dropRight(1), text.replace("\"revision\":1", "\"revision\":1,\"revision\":1"), text.dropRight(1) + ",\"unknown\":true}")
        When("the decoder interprets each invalid variant")
        val results = texts.map(DurableJobRecordCodec.decode(_, _access))
        Then("resource admission does not relax the existing parser or schema authority")
        results.foreach(_ shouldBe a[Consequence.Failure[_]])
      }
    }

    "canonical producers and consumers" which {
      "produce readable V1 and V2 signed records exactly at the final-envelope limit" in {
        Given("terminal V1 and pending V2 bodies sized for an exact-limit signed envelope")
        val bodies = Vector(false, true).map(pending => _sized_body(_maximum_bytes, pending, "x"))
        When("both constructors sign and every canonical output entry renders their records")
        val records = Vector(DurableJobRecord.create(bodies.head), DurableJobRecord.createV2(bodies.last)).map(_.toOption.get)
        val texts = records.map(DurableJobRecordCodec.canonicalJson(_).toOption.get)
        val bytes = records.map(DurableJobRecordCodec.canonicalBytes(_).toOption.get.toVector)
        val wrappers = records.map(DurableJobRecordCodec.toRecord)
        val decoded = texts.map(DurableJobRecordCodec.decode(_, _access))
        val migrated = DurableJobRecord.migrateV1ToV2(records.head)
        Then("the complete signature overhead fits inclusively and all successful outputs can be read")
        texts.foreach(text => _bytes(text) shouldBe _maximum_bytes)
        bytes shouldBe texts.map(_.getBytes(StandardCharsets.UTF_8).toVector)
        wrappers.foreach(_.toOption.nonEmpty shouldBe true)
        decoded shouldBe records.map(Consequence.success(_))
        migrated.toOption.map(_.body) shouldBe Some(bodies.head)
      }

      "refuse signing when only the final signature envelope exceeds the budget" in {
        Given("otherwise valid V1 and V2 bodies whose unsigned envelopes fit but signed envelopes exceed by one byte")
        val bodies = Vector(false, true).map(pending => _sized_body(_maximum_bytes + 1, pending, "x"))
        val unsigned = bodies.zip(Vector(DurableRecordFormat.V1, DurableRecordFormat.V2)).map { case (body, format) =>
          DurableJobRecordCodec._unsigned_json(format, body).noSpaces
        }
        When("the legacy and explicit V2 signing entry paths consider those bodies")
        val results = Vector(DurableJobRecord.create(bodies.head), DurableJobRecord.createV2(bodies.last), DurableJobRecordCodec.sign(bodies.head), DurableJobRecordCodec.signV2(bodies.last))
        Then("no successful producer emits a record that the bounded reader would refuse")
        unsigned.foreach(text => _bytes(text) should be <= _maximum_bytes)
        results.foreach(_ shouldBe a[Consequence.Failure[_]])
      }

      "refuse over-budget unsigned bodies through every constructor" in {
        Given("terminal and pending bodies with an identity larger than the whole wire budget")
        val bodies = Vector(false, true).map(pending => _body("x" * (_maximum_bytes + 1), pending))
        When("V1, V2 and V0 producers consider their unsigned output")
        val results = Vector(DurableJobRecord.create(bodies.head), DurableJobRecord.createV2(bodies.last))
        val v0 = DurableJobRecordCodec.canonicalV0Json(bodies.head)
        Then("all producers refuse rather than truncate or drop admitted fields")
        results.foreach(_.toOption shouldBe None)
        v0 shouldBe a[Consequence.Failure[_]]
      }

      "refuse manually constructed oversize signed records at every admitted output entry" in {
        Given("correctly digested V1 and V2 records constructed outside signing with final text one byte over budget")
        val records = Vector(false, true).map(pending => _manual_record(_sized_body(_maximum_bytes + 1, pending, "x"), pending))
        When("canonical text, bytes, Record wrappers, public projections and explicit migration are requested")
        val texts = records.map(DurableJobRecordCodec.canonicalJson)
        val bytes = records.map(DurableJobRecordCodec.canonicalBytes)
        val wrappers = records.map(DurableJobRecordCodec.toRecord)
        val projections = records.map(_.publicProjection(_access))
        val migration = DurableJobRecord.migrateV1ToV2(records.head)
        Then("a valid digest cannot bypass the final canonical wire budget")
        texts.foreach(_.toOption shouldBe None)
        bytes.foreach(_.toOption shouldBe None)
        wrappers.foreach(_.toOption shouldBe None)
        projections.foreach(_.toOption shouldBe None)
        migration.toOption shouldBe None
      }

      "admit an exact-limit V0 envelope but refuse migration when V1 integrity overhead cannot fit" in {
        Given("a V0 body whose own canonical envelope fits exactly and a small compatible V0 body")
        val baseline = _body("x", false)
        val basebytes = _bytes(DurableJobRecordCodec._unsigned_json(DurableRecordFormat.V0, baseline).noSpaces)
        val large = _body("x" * (_maximum_bytes - basebytes + 1), false)
        val small = _body("legacy", false)
        When("the V0 producer renders both bodies and the decoder attempts their explicit V1 migration")
        val text = DurableJobRecordCodec.canonicalV0Json(large).toOption.get
        val tooheavy = DurableJobRecordCodec.decode(text, _access)
        val migrated = DurableJobRecordCodec.decode(DurableJobRecordCodec.canonicalV0Json(small).toOption.get, _access)
        val overlimit = DurableJobRecordCodec.canonicalV0Json(large.copy(identity = large.identity.copy(jobId = large.identity.jobId + "x")))
        Then("V0 has its inclusive budget while migration refuses additional envelope bytes without a partial record")
        _bytes(text) shouldBe _maximum_bytes
        tooheavy.toOption shouldBe None
        overlimit.toOption shouldBe None
        migrated shouldBe Consequence.success(_manual_record(small, false))
      }
    }

    "startup and compatibility" which {
      "classify byte and depth refusal as corruption while preserving distinct authorization refusal" in {
        Given("oversized text, excessively nested text and an ordinary signed record with a wrong subject")
        val hostile = Vector(" " * (_maximum_bytes + 1), _nested(65))
        val record = _manual_record(_body("startup", false), false)
        val wrong = _access.copy(subjectId = "other")
        When("startup admission considers each resource, authorized and unauthorized input")
        val corrupt = hostile.map(DurableJobRecordCodec.startupAdmission(_, _access))
        val refused = DurableJobRecordCodec.startupAdmission(_wire(record), wrong)
        val admitted = DurableJobRecordCodec.startupAdmission(_wire(record), _access)
        Then("resource refusal exposes no record and authorization keeps its existing distinct classification")
        corrupt shouldBe Vector.fill(2)(DurableJobRecordStartupAdmission.Corrupt)
        refused shouldBe DurableJobRecordStartupAdmission.Refused
        admitted shouldBe DurableJobRecordStartupAdmission.Admitted(record)
      }

      "preserve format-bound digests, canonical ordering and metadata-only authorized projections" in {
        Given("terminal V1 and V2 records with equivalent differently ordered profile maps")
        val body = _body("compatible", false)
        val left = body.copy(definitionSnapshot = body.definitionSnapshot.copy(declaredProfile = Map("z" -> "last", "a" -> "first")))
        val right = body.copy(definitionSnapshot = body.definitionSnapshot.copy(declaredProfile = Map("a" -> "first", "z" -> "last")))
        val wrong = Vector(_access.copy(tenantId = "other"), _access.copy(subjectId = "other"), _access.copy(scopes = Set.empty))
        When("both formats sign, render, migrate and project under matching and mismatching access")
        val v1 = DurableJobRecord.create(left).toOption.get
        val v2 = DurableJobRecord.createV2(left).toOption.get
        val canonical = DurableJobRecordCodec.canonicalJson(v1).toOption.get
        val reordered = DurableJobRecordCodec.canonicalJson(DurableJobRecord.create(right).toOption.get).toOption.get
        val projection = v1.publicProjection(_access).toOption.get
        val decoded = DurableJobRecordCodec.decode(canonical, _access)
        val migrated = DurableJobRecord.migrateV1ToV2(v1)
        val denials = wrong.map(access => (DurableJobRecordCodec.decode(canonical, access), v1.publicProjection(access)))
        Then("the unchanged canonical schema and SHA-256 boundary preserve identity and scope enforcement")
        canonical shouldBe reordered
        v1 shouldBe _manual_record(left, false)
        v2 shouldBe _manual_record(left, true)
        v2.integrity should not be v1.integrity
        decoded shouldBe Consequence.success(v1)
        migrated shouldBe Consequence.success(v2)
        projection.identity shouldBe body.identity
        projection.result shouldBe DurablePublicResult("succeeded", Some(DurablePublicValue("absent", None, None)), None)
        denials.foreach { case (decode, project) =>
          decode.toOption shouldBe None
          project.toOption shouldBe None
        }
      }

      "round-trip bounded quote, escape and Unicode identities with a reproducible property check" in {
        Given("bounded generated identities combining ordinary text, quotes, backslashes, braces and Unicode for both formats")
        val chunks = Gen.oneOf("x", "\"", "\\", "{", "}", "[", "]", "界", "😀", "\n")
        val identities = Gen.choose(1, 80).flatMap(size => Gen.listOfN(size, chunks)).map(parts => "job-" + parts.mkString)
        val property = Prop.forAll(identities, Gen.oneOf(false, true)) { (id, pending) =>
          val record = _manual_record(_body(id, pending), pending)
          val text = _wire(record)
          DurableJobRecordCodec.decode(text, _access).toOption.contains(record) &&
            DurableJobRecordCodec.canonicalJson(record).toOption.contains(text)
        }
        When("ScalaCheck evaluates 48 examples with explicit seed 6909")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48).withInitialSeed(Seed(6909L)), property)
        Then("all generated identities retain their value and canonical bytes under the admitted resource policy")
        checked.passed shouldBe true
      }
    }
  }

  private val _maximum_bytes = 1048576
  private val _byte_message = "Durable job record exceeds UTF-8 byte limit (1048576)"
  private val _depth_message = "Durable job record exceeds JSON nesting depth limit (64)"
  private val _instant = Instant.parse("2026-10-01T01:02:03Z")
  private val _access = DurableJobRecordAccess("tenant-a", "subject-a", Set("job.read"))

  private def _bytes(text: String): Int = text.getBytes(StandardCharsets.UTF_8).length

  private def _nested(depth: Int): String = ("[" * depth) + "0" + ("]" * depth)

  private def _wire(record: DurableJobRecord): String = DurableJobRecordCodec._signed_json(record).noSpaces

  private def _manual_record(body: DurableJobRecordBody, pending: Boolean): DurableJobRecord = {
    val format = if (pending) DurableRecordFormat.V2 else DurableRecordFormat.V1
    val unsigned = DurableJobRecordCodec._unsigned_json(format, body).noSpaces
    val digest = MessageDigest.getInstance("SHA-256").digest(unsigned.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
    DurableJobRecord(format, body, DurableRecordIntegrity("sha-256", digest))
  }

  private def _sized_body(bytes: Int, pending: Boolean, unit: String): DurableJobRecordBody = {
    val baseline = _body("x", pending)
    val available = bytes - _bytes(_wire(_manual_record(baseline, pending))) + 1
    val unitbytes = _bytes(unit)
    _body((unit * (available / unitbytes)) + ("x" * (available % unitbytes)), pending)
  }

  private def _body(id: String, pending: Boolean): DurableJobRecordBody =
    DurableJobRecordBody(
      DurableJobIdentity(id, 1L, _instant, _instant),
      DurableJobAuthorization("tenant-a", DurableSubject("subject-a", "user"), DurableVisibility.Subject, Set("job.read")),
      DurableJobLifecycle(
        if (pending) DurableJobLifecycleStatus.Running else DurableJobLifecycleStatus.Succeeded,
        0,
        DurableRunMode.Async,
        DurableRetryEvidence(Vector.empty, 1, None, false, false),
        DurableScheduleState(Some(_instant), None, None)
      ),
      Vector(DurableTaskDescriptor(
        "task-001", None, DurableTaskKind.Operation,
        DurableTaskTarget("operation", DurableOperationReference("component-a", None, "run", None)),
        DurableTaskRelation(DurableTaskRelationKind.Root, None),
        DurableTransactionDescriptor(DurableTransactionRole.Own, DurableTransactionScope.PerTask, DurableTransactionOutcome.Pending),
        None
      )),
      Vector(DurableInputReference("request", DurableValue.Absent)),
      if (pending) DurableResultOutcome.Pending else DurableResultOutcome.Succeeded(DurableValue.Absent),
      Vector.empty,
      Vector.empty,
      None,
      DurableDefinitionSnapshot("definition-001", "job", 1, 1L, "a" * 64, None, None, Map.empty),
      DurableRetentionState(None, None, None, DurableDeletionState.Active, None)
    )
}
