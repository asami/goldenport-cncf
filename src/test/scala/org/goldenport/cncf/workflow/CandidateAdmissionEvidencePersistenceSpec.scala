package org.goldenport.cncf.workflow

import java.nio.file.Files
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionEvidencePersistence.*
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/** @since Sep. 25, 2026
  * @version Sep. 25, 2026
  */
final class CandidateAdmissionEvidencePersistenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _evidence_codec = new PayloadCodec[String] {
    val typeIdentity = "ReviewEvidence"
    def encode(value: String): Json = Json.fromString(value)
    def decode(value: Json): Either[String, String] = value.asString.toRight("expected review text")
  }
  private val _scope_codec = new PayloadCodec[Int] {
    val typeIdentity = "ReviewScope"
    def encode(value: Int): Json = Json.fromInt(value)
    def decode(value: Json): Either[String, Int] = value.asNumber.flatMap(_.toInt).toRight("expected scope")
  }
  private val _target = CandidateRef("candidate-1", "revision-1")
  private val _requirement = AdmissionRequirement("review", RequirementKind.Semantic,
    "ReviewEvidence", TypedValue("ReviewScope", 3))
  private val _submission = AdmissionSubmission("submission-1",
    CandidateSnapshot(_target, TypedValue("Candidate", "draft")), Vector.empty[AdmissionEvidence[String, Int]])
  private val _policy = new EvidencePolicy[String, Int] {
    def isFresh(evidence: AdmissionEvidence[String, Int], target: CandidateRef): Boolean =
      evidence.provenance != "stale"
    def covers(evidence: AdmissionEvidence[String, Int], requirement: AdmissionRequirement[Int]): Boolean =
      evidence.scope.value >= requirement.scope.value
  }
  private val _evidence = AdmissionEvidence("review-1", _requirement.identity, _target,
    TypedValue("ReviewEvidence", "approved"), TypedValue("ReviewScope", 3), "worker-result-1")

  "Candidate admission evidence persistence" should {
    "round-trip a typed record and reject codec, schema, and field drift" in {
      Given("a typed evidence record with application-owned codecs")
      val record = Record(_submission.identity, _evidence)
      When("the record is encoded and decoded through the exact JSON schema")
      val encoded = JsonV1.encodeC(record, _evidence_codec, _scope_codec).toOption.get
      val decoded = JsonV1.decodeC(encoded, _evidence_codec, _scope_codec)
      Then("the record round-trips without losing its typed identity")
      decoded shouldBe Consequence.success(record)
      When("schema, provenance, field set, or codec is incompatible")
      val invalidschema = JsonV1.decodeC(encoded.replace(JsonV1.schemaVersion, "unknown-schema"),
        _evidence_codec, _scope_codec)
      val invalidprovenance = JsonV1.decodeC(encoded.replace("worker-result-1", ""), _evidence_codec, _scope_codec)
      val extrafield = JsonV1.decodeC(encoded.dropRight(1) + ",\"selectedTransition\":\"commit\"}",
        _evidence_codec, _scope_codec)
      val invalidtype = JsonV1.encodeC(
        record.copy(evidence = _evidence.copy(payload = TypedValue("Other", "approved"))),
        _evidence_codec, _scope_codec)
      val brokencodec = new PayloadCodec[String] {
        val typeIdentity = "ReviewEvidence"
        def encode(value: String): Json = Json.fromString(value)
        def decode(value: Json): Either[String, String] = throw new IllegalStateException("codec failed")
      }
      val invalidcodec = JsonV1.decodeC(encoded, brokencodec, _scope_codec)
      Then("each incompatible form is rejected rather than admitted")
      invalidschema shouldBe a[Consequence.Failure[?]]
      invalidprovenance shouldBe a[Consequence.Failure[?]]
      extrafield shouldBe a[Consequence.Failure[?]]
      invalidtype shouldBe a[Consequence.Failure[?]]
      invalidcodec shouldBe a[Consequence.Failure[?]]
    }

    "retain evidence after reopening the local store without granting admission by storage alone" in {
      Given("a separate local evidence directory and a candidate awaiting review")
      val directory = Files.createTempDirectory("cam-evidence-test-")
      try {
        val first = new LocalJson[String, Int](directory, _evidence_codec, _scope_codec)
        When("evidence is written once and the store is reopened")
        val firstwrite = first.putIfAbsentC(_submission.identity, _evidence)
        val repeatedwrite = first.putIfAbsentC(_submission.identity, _evidence)
        val conflictingwrite = first.putIfAbsentC(_submission.identity, _evidence.copy(provenance = "different"))
        val reopened = new LocalJson[String, Int](directory, _evidence_codec, _scope_codec)
        val recovered = reopened.loadC(_submission.identity).toOption.get
        Then("the stored record is recovered without crossing submission identities")
        firstwrite shouldBe Consequence.unit
        repeatedwrite shouldBe Consequence.unit
        conflictingwrite shouldBe a[Consequence.Failure[?]]
        recovered shouldBe Vector(_evidence)
        reopened.loadC("another-submission") shouldBe Consequence.success(Vector.empty)
        When("the application explicitly re-evaluates the original and revised candidates")
        val admitted = evaluateC(_submission, Vector(_requirement), _policy, recovered).toOption.get.result
        val revised = _submission.copy(candidate = _submission.candidate.copy(
          reference = _target.copy(revision = "revision-2")))
        val reviseddefects = evaluateC(revised, Vector(_requirement), _policy, recovered).toOption.get.gaps.head.defects
        Then("only the original matching revision is admitted")
        admitted shouldBe AdmissionResult.Admitted(_target, Vector(_evidence.identity))
        reviseddefects should
          contain (EvidenceDefect.WrongTarget)
      } finally {
        val entries = Files.list(directory)
        try entries.forEach(path => Files.deleteIfExists(path)) finally entries.close()
        Files.deleteIfExists(directory)
      }
    }
  }
}
