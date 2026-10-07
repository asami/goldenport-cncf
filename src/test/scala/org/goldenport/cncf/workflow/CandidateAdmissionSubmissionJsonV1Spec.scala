package org.goldenport.cncf.workflow

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 */
final class CandidateAdmissionSubmissionJsonV1Spec extends AnyWordSpec
    with Matchers with GivenWhenThen {
  private val _text_codec = new PayloadCodec[String] {
    val typeIdentity = "candidate.text.v1"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("text required")
  }
  private val _scope_codec = new PayloadCodec[Int] {
    val typeIdentity = "review.scope.v1"
    def encode(value: Int): Json = Json.fromInt(value)
    def decode(value: Json): Either[String, Int] = value.asNumber.flatMap(_.toInt).toRight("integer required")
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"unexpected rejection: $other")
  }
  private def _submission(text: String = "日本語 / exact \"scope\"", scope: Int = 3) = {
    val target = CandidateRef("candidate-1", "revision-2")
    AdmissionSubmission("submission-1", CandidateSnapshot(target, TypedValue(_text_codec.typeIdentity, text)),
      Vector(AdmissionEvidence("evidence-1", "semantic-review", target,
        TypedValue(_text_codec.typeIdentity, text), TypedValue(_scope_codec.typeIdentity, scope), "review-run-1")))
  }
  private def _codec(candidate: PayloadCodec[String] = _text_codec) =
    _take(CandidateAdmissionSubmissionJsonV1.payloadCodecC("review.submission.v1",
      candidate, _text_codec, _scope_codec))

  "Candidate-Admission submission encoding" should {
    "preserve candidate, evidence payload and application scope over arbitrary typed values" in {
      Given("typed candidate/evidence text and an integer scope, including Unicode and JSON escapes")
      val texts = Gen.oneOf(Gen.alphaNumStr, Gen.const("改行\n\"quoted\"\\\u0000"))
      val property = Prop.forAll(texts, Gen.choose(-10000, 10000)) { (text, scope) =>
        val submission = _submission(text, scope)
        When("the declared result codec encodes and decodes the submission")
        val json = _codec().encode(submission)
        val decoded = _codec().decode(json)
        Then("all payload, scope, references and provenance remain exact")
        decoded shouldBe Right(submission)
        _codec().encode(decoded.toOption.get) shouldBe json
        true
      }
      Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property).passed shouldBe true
    }

    "reject undeclared JSON fields, versions, nested submission identities and duplicate evidence" in {
      Given("one exact typed submission JSON")
      val json = _codec().encode(_submission())
      val evidence = json.hcursor.downField("evidence").focus.get.asArray.get.head
      val variants = Vector(
        json.deepMerge(Json.obj("advance" -> Json.fromString("commit"))),
        json.deepMerge(Json.obj("schemaVersion" -> Json.fromString("foreign"))),
        json.deepMerge(Json.obj("evidence" -> Json.arr(evidence.deepMerge(
          Json.obj("submissionIdentity" -> Json.fromString("foreign")))))),
        json.deepMerge(Json.obj("evidence" -> Json.arr(evidence, evidence))),
        json.deepMerge(Json.obj("candidate" -> json.hcursor.downField("candidate").focus.get.deepMerge(
          Json.obj("payload" -> Json.obj("typeIdentity" -> Json.fromString("foreign"), "value" -> Json.fromString("draft"))))))
      )
      When("each incompatible envelope is decoded")
      val outcomes = variants.map(_codec().decode)
      Then("no incompatible or ambiguous submission is admitted by the codec")
      outcomes.foreach(_.isLeft shouldBe true)
    }

    "carry wrong-target, stale and insufficient-coverage evidence without granting admission" in {
      Given("shape-valid evidence that does not satisfy the application policy")
      val original = _submission(scope = 1)
      val submission = original.copy(evidence = original.evidence.map(_.copy(
        target = CandidateRef("candidate-1", "revision-1"), provenance = "expired")))
      When("the evidence is encoded and recovered")
      val recovered = _codec().decode(_codec().encode(submission))
      Then("storage interpretation preserves defects instead of fabricating acceptance or deleting evidence")
      recovered shouldBe Right(submission)
      recovered.toOption.get.evidence.head.target should not be submission.candidate.reference
    }

    "reject lossy and throwing application codecs before exposing retained bytes" in {
      Given("application candidate codecs that lose meaning or throw")
      val lossy = new PayloadCodec[String] {
        val typeIdentity = _text_codec.typeIdentity
        def encode(value: String): Json = Json.fromString("lost")
        def decode(value: Json): Either[String, String] = value.asString.toRight("text required")
      }
      val throwing = new PayloadCodec[String] {
        val typeIdentity = _text_codec.typeIdentity
        def encode(value: String): Json = throw new IllegalStateException("unavailable")
        def decode(value: Json): Either[String, String] = throw new IllegalStateException("unavailable")
      }
      When("checked encoding and decoding use the incompatible application codecs")
      val encoded = Vector(lossy, throwing).map(value =>
        CandidateAdmissionSubmissionJsonV1.encodeC(_submission(), value, _text_codec, _scope_codec))
      val decoded = _codec(throwing).decode(_codec().encode(_submission()))
      Then("failure stays a typed boundary result rather than losing payload/scope or throwing during decode")
      encoded.foreach(_.toOption shouldBe None)
      decoded.isLeft shouldBe true
    }

    "reject missing codec bindings and incomplete submissions" in {
      Given("a missing declared result identity or payload codec")
      When("bindings and incomplete submissions are admitted")
      val missing = Vector(
        CandidateAdmissionSubmissionJsonV1.payloadCodecC("", _text_codec, _text_codec, _scope_codec),
        CandidateAdmissionSubmissionJsonV1.payloadCodecC("review.submission.v1", null, _text_codec, _scope_codec))
      val incomplete = CandidateAdmissionSubmissionJsonV1.encodeC(
        _submission().copy(evidence = null), _text_codec, _text_codec, _scope_codec)
      Then("none becomes a usable typed result or retained byte envelope")
      missing.foreach(_.toOption shouldBe None)
      incomplete.toOption shouldBe None
    }
  }
}
