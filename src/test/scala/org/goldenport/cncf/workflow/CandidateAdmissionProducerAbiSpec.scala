package org.goldenport.cncf.workflow

import scala.io.Source

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 23, 2026
 * @version Sep. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class CandidateAdmissionProducerAbiSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {
  import CandidateAdmissionProducerAbi.*

  "Candidate-Admission producer ABI receiver" should {
    "admit the frozen Cozy fixture and a complete declared JudgmentResult" in {
      Given("the exact Cozy 73 Candidate-Admission producer fixture")
      When("the CNCF receiver parses and admits its closed producer ABI")
      val artifact = _success(CandidateAdmissionProducerAbi.parseC(_fixture))
      val result = _success(CandidateAdmissionProducerAbi.admitJudgmentResultC(artifact, _result()))
      Then("the immutable receiver preserves producer facts and the declared alternative")
      artifact.schemaVersion shouldBe acceptedSchemaVersion
      artifact.generator.generatorIdentity shouldBe acceptedGeneratorIdentity
      artifact.models.map(_.model.identity) shouldBe Vector(ModelIdentity("OrderProgress"))
      artifact.models.head.judgments.map(_.identity) shouldBe Vector(JudgmentActionIdentity("judge-payment"))
      artifact.models.head.admissions.map(_.identity) shouldBe Vector(AdmissionActionIdentity("admit-payment"))
      artifact.models.head.requiredSpi.map(_.identity) shouldBe Vector(RequiredSpiIdentity("capture-payment-capability"))
      result.judgment shouldBe JudgmentActionIdentity("judge-payment")
      result.selectedAlternative shouldBe AlternativeIdentity("approve")
    }

    "reject malformed JSON with InvalidJson" in {
      Given("a malformed Candidate-Admission producer ABI JSON document")
      val text = "{malformed-json"
      When("the CNCF receiver parses the producer ABI through its public boundary")
      val result = CandidateAdmissionProducerAbi.parseC(text)
      Then("the receiver reports the stable InvalidJson diagnostic")
      _diagnostic(result).code shouldBe DiagnosticCode.InvalidJson
    }

    "reject structurally invalid JSON with InvalidShape" in {
      Given("a valid JSON object that is missing the producer ABI structure")
      val text = "{}"
      When("the CNCF receiver parses the producer ABI through its public boundary")
      val result = CandidateAdmissionProducerAbi.parseC(text)
      Then("the receiver reports the stable InvalidShape diagnostic")
      _diagnostic(result).code shouldBe DiagnosticCode.InvalidShape
    }

    "reject a result when its complete producer artifact is incompatible" in {
      Given("a complete result and an otherwise complete artifact with an unsupported schema")
      val artifact = _artifact.copy(schemaVersion = "cozy.cml.candidate-admission-producer-abi.v2")
      val result = _result()
      When("the receiver re-admits the artifact before checking result compatibility")
      val admission = CandidateAdmissionProducerAbi.admitJudgmentResultC(artifact, result)
      Then("the incompatible producer artifact fails at the artifact boundary")
      _diagnostic(admission).code shouldBe DiagnosticCode.UnsupportedSchema
    }

    "reject an unsupported schema or generator provenance" in {
      Given("the otherwise complete frozen artifact with altered producer identities")
      val artifact = _artifact
      val unsupportedschema = artifact.copy(schemaVersion = "cozy.cml.candidate-admission-producer-abi.v2")
      val unsupportedgenerator = artifact.copy(generator = artifact.generator.copy(generatorIdentity = "cozy.modeler.OtherGenerator"))
      When("receiver admission evaluates the version and generator boundary")
      val schemaresult = CandidateAdmissionProducerAbi.admitC(unsupportedschema)
      val generatorresult = CandidateAdmissionProducerAbi.admitC(unsupportedgenerator)
      Then("both producer compatibility violations fail with stable diagnostics")
      _diagnostic(schemaresult).code shouldBe DiagnosticCode.UnsupportedSchema
      _diagnostic(generatorresult).code shouldBe DiagnosticCode.UnsupportedGenerator
    }

    "reject blank or unprovenanced model Judgment Admission and Required-SPI facts" in {
      Given("separate corrupted copies of the accepted producer facts")
      val artifact = _artifact
      val model = artifact.models.head
      val missingmodelsource = artifact.copy(models = Vector(model.copy(model = model.model.copy(source = SourceLocation(0)))))
      val missingjudgmentfact = artifact.copy(models = Vector(model.copy(judgments = Vector(model.judgments.head.copy(goal = SourceReference("", SourceLocation(61)))))))
      val missingadmissionsource = artifact.copy(models = Vector(model.copy(admissions = Vector(model.admissions.head.copy(actionSource = SourceLocation(0))))))
      val missingrequiredspifact = artifact.copy(models = Vector(model.copy(requiredSpi = Vector(model.requiredSpi.head.copy(identity = RequiredSpiIdentity(""))))))
      When("receiver admission checks source-attributed values before use")
      val modelresult = CandidateAdmissionProducerAbi.admitC(missingmodelsource)
      val judgmentresult = CandidateAdmissionProducerAbi.admitC(missingjudgmentfact)
      val admissionresult = CandidateAdmissionProducerAbi.admitC(missingadmissionsource)
      val requiredspiresult = CandidateAdmissionProducerAbi.admitC(missingrequiredspifact)
      Then("every incomplete fact is rejected as a deterministic structured failure")
      _diagnostic(modelresult).code shouldBe DiagnosticCode.MissingRequiredProvenance
      _diagnostic(judgmentresult).code shouldBe DiagnosticCode.MissingRequiredFact
      _diagnostic(admissionresult).code shouldBe DiagnosticCode.MissingRequiredProvenance
      _diagnostic(requiredspiresult).code shouldBe DiagnosticCode.MissingRequiredFact
    }

    "reject duplicate model action and Required-SPI identities" in {
      Given("the accepted artifact with repeated identity values")
      val artifact = _artifact
      val model = artifact.models.head
      val duplicatedmodel = artifact.copy(models = Vector(model, model))
      val duplicatedjudgment = artifact.copy(models = Vector(model.copy(judgments = Vector(model.judgments.head, model.judgments.head))))
      val duplicatedadmission = artifact.copy(models = Vector(model.copy(admissions = Vector(model.admissions.head, model.admissions.head))))
      val duplicatedrequiredspi = artifact.copy(models = Vector(model.copy(requiredSpi = Vector(model.requiredSpi.head, model.requiredSpi.head))))
      When("receiver admission applies its identity uniqueness rules")
      val results = Vector(duplicatedmodel, duplicatedjudgment, duplicatedadmission, duplicatedrequiredspi).map(CandidateAdmissionProducerAbi.admitC)
      Then("each duplicate identity fails before any candidate result can be admitted")
      results.map(result => _diagnostic(result).code) shouldBe Vector.fill(4)(DiagnosticCode.DuplicateIdentity)
    }

    "reject empty or duplicate alternatives and criteria" in {
      Given("a Judgment descriptor with empty or duplicate decision vocabulary")
      val artifact = _artifact
      val model = artifact.models.head
      val judgment = model.judgments.head
      val emptyalternatives = artifact.copy(models = Vector(model.copy(judgments = Vector(judgment.copy(alternatives = Vector.empty)))))
      val duplicatealternatives = artifact.copy(models = Vector(model.copy(judgments = Vector(judgment.copy(alternatives = Vector(judgment.alternatives.head, judgment.alternatives.head))))))
      val emptycriteria = artifact.copy(models = Vector(model.copy(judgments = Vector(judgment.copy(criteria = Vector.empty)))))
      val duplicatecriteria = artifact.copy(models = Vector(model.copy(judgments = Vector(judgment.copy(criteria = Vector(judgment.criteria.head, judgment.criteria.head))))))
      When("receiver admission checks the declared decision vocabulary")
      val results = Vector(emptyalternatives, duplicatealternatives, emptycriteria, duplicatecriteria).map(CandidateAdmissionProducerAbi.admitC)
      Then("the nonempty unique alternative and criterion boundary is fail closed")
      results.map(result => _diagnostic(result).code) shouldBe Vector(
        DiagnosticCode.MissingAlternatives,
        DiagnosticCode.DuplicateAlternative,
        DiagnosticCode.MissingCriteria,
        DiagnosticCode.DuplicateCriterion
      )
    }

    "require one-to-one Judgment Admission pairing with agreeing identities" in {
      Given("the accepted model with a missing relation and a mismatched candidate Judgment")
      val artifact = _artifact
      val model = artifact.models.head
      val missingpair = artifact.copy(models = Vector(model.copy(judgmentAdmissions = Vector.empty)))
      val mismatchedpair = artifact.copy(models = Vector(model.copy(
        admissions = Vector(model.admissions.head.copy(candidateJudgmentActionIdentity = JudgmentActionIdentity("other-judgment")))
      )))
      When("receiver admission verifies the explicit Judgment-to-Admission relation")
      val missingresult = CandidateAdmissionProducerAbi.admitC(missingpair)
      val mismatchresult = CandidateAdmissionProducerAbi.admitC(mismatchedpair)
      Then("the relation must contain exactly one agreeing pair for each declared action")
      _diagnostic(missingresult).code shouldBe DiagnosticCode.JudgmentAdmissionPairing
      _diagnostic(mismatchresult).code shouldBe DiagnosticCode.JudgmentAdmissionIdentityMismatch
    }

    "require equal operation and input binding through Judgment Admission and Required SPI" in {
      Given("otherwise valid descriptors with mismatched operation or input contracts")
      val artifact = _artifact
      val model = artifact.models.head
      val admissionoperation = artifact.copy(models = Vector(model.copy(
        admissions = Vector(model.admissions.head.copy(operation = model.admissions.head.operation.copy(name = OperationIdentity("reversePayment"))))
      )))
      val admissionbinding = artifact.copy(models = Vector(model.copy(
        admissions = Vector(model.admissions.head.copy(inputBinding = Some(InputBinding("other.subject"))))
      )))
      val requiredspioperation = artifact.copy(models = Vector(model.copy(
        requiredSpi = Vector(model.requiredSpi.head.copy(operation = model.requiredSpi.head.operation.copy(name = OperationIdentity("reversePayment"))))
      )))
      When("receiver admission compares the producer's correlation contracts")
      val operationresult = CandidateAdmissionProducerAbi.admitC(admissionoperation)
      val bindingresult = CandidateAdmissionProducerAbi.admitC(admissionbinding)
      val requiredspiresult = CandidateAdmissionProducerAbi.admitC(requiredspioperation)
      Then("all operation and input-binding disagreement is rejected deterministically")
      _diagnostic(operationresult).code shouldBe DiagnosticCode.OperationMismatch
      _diagnostic(bindingresult).code shouldBe DiagnosticCode.InputBindingMismatch
      _diagnostic(requiredspiresult).code shouldBe DiagnosticCode.OperationMismatch
    }

    "require LOCAL effect class and REQUIRED transaction for Admission" in {
      Given("admission descriptors that declare an external effect or optional transaction")
      val artifact = _artifact
      val model = artifact.models.head
      val wrongeffect = artifact.copy(models = Vector(model.copy(admissions = Vector(model.admissions.head.copy(effectClass = "EXTERNAL")))))
      val wrongtransaction = artifact.copy(models = Vector(model.copy(admissions = Vector(model.admissions.head.copy(transactionRequirement = "OPTIONAL")))))
      When("receiver admission checks the fixed local transaction boundary")
      val effectresult = CandidateAdmissionProducerAbi.admitC(wrongeffect)
      val transactionresult = CandidateAdmissionProducerAbi.admitC(wrongtransaction)
      Then("both Admission boundary violations fail closed")
      _diagnostic(effectresult).code shouldBe DiagnosticCode.InvalidAdmissionEffectClass
      _diagnostic(transactionresult).code shouldBe DiagnosticCode.InvalidAdmissionTransactionRequirement
    }

    "reject unknown mismatched and incomplete JudgmentResult values" in {
      Given("the admitted fixture and nonadmitted result candidates")
      val artifact = _artifact
      val unknownjudgment = _result(judgment = "unknown-judgment")
      val unknownalternative = _result(alternative = "defer")
      val incompleteresult = _result(rationale = "")
      When("the receiver admits each typed result against the known Judgment declaration")
      val unknownjudgmentresult = CandidateAdmissionProducerAbi.admitJudgmentResultC(artifact, unknownjudgment)
      val unknownalternativeresult = CandidateAdmissionProducerAbi.admitJudgmentResultC(artifact, unknownalternative)
      val incompleteadmission = CandidateAdmissionProducerAbi.admitJudgmentResultC(artifact, incompleteresult)
      Then("unknown alternatives and incomplete evidence contracts cannot cross the receiver boundary")
      _diagnostic(unknownjudgmentresult).code shouldBe DiagnosticCode.UnknownJudgmentResult
      _diagnostic(unknownalternativeresult).code shouldBe DiagnosticCode.UnknownJudgmentAlternative
      _diagnostic(incompleteadmission).code shouldBe DiagnosticCode.IncompleteJudgmentResult
    }

    "admit a versioned typed JudgmentResult only at the declared Operation result type" in {
      Given("the accepted Cozy sidecar and a CNCF-owned typed result wrapper")
      val artifact = _artifact
      val typed = TypedJudgmentResultV1(
        acceptedTypedJudgmentResultSchemaVersion,
        _result(),
        TypeIdentity("PaymentResult"),
        ContextReference("payment-result", "1")
      )

      When("the result is admitted without interpreting expectedResult prose as a type")
      val admitted = _success(CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(artifact, typed))

      Then("its declared alternative and payload reference remain typed")
      admitted.result.selectedAlternative shouldBe AlternativeIdentity("approve")
      admitted.payloadType shouldBe TypeIdentity("PaymentResult")
      admitted.payload shouldBe ContextReference("payment-result", "1")
    }

    "reject an incompatible or missing typed JudgmentResult payload" in {
      Given("a declared PaymentResult Operation result type")
      val artifact = _artifact
      val valid = TypedJudgmentResultV1(
        acceptedTypedJudgmentResultSchemaVersion,
        _result(), TypeIdentity("PaymentResult"), ContextReference("payment-result", "1")
      )

      When("schema, type, payload reference, or declared result type is absent")
      val badSchema = CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(
        artifact, valid.copy(schemaVersion = "cncf.candidate-admission-judgment-result.v2")
      )
      val badType = CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(
        artifact, valid.copy(payloadType = TypeIdentity("OtherResult"))
      )
      val badPayload = CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(
        artifact, valid.copy(payload = ContextReference("", "1"))
      )
      val model = artifact.models.head
      val noDeclaredType = CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(
        artifact.copy(models = Vector(model.copy(
          judgments = model.judgments.map(j => j.copy(operation = j.operation.copy(resultType = None))),
          admissions = model.admissions.map(a => a.copy(operation = a.operation.copy(resultType = None))),
          requiredSpi = model.requiredSpi.map(s => s.copy(operation = s.operation.copy(resultType = None)))
        ))), valid
      )

      Then("each mismatch fails closed before StateMachine routing")
      _diagnostic(badSchema).code shouldBe DiagnosticCode.UnsupportedTypedJudgmentResultSchema
      _diagnostic(badType).code shouldBe DiagnosticCode.IncompatibleJudgmentPayloadType
      _diagnostic(badPayload).code shouldBe DiagnosticCode.IncompleteJudgmentPayload
      _diagnostic(noDeclaredType).code shouldBe DiagnosticCode.MissingDeclaredJudgmentPayloadType
    }
  }

  private def _artifact: Artifact = _success(CandidateAdmissionProducerAbi.parseC(_fixture))

  private def _result(
    judgment: String = "judge-payment",
    alternative: String = "approve",
    rationale: String = "decision-rationale",
    evidence: String = "payment-evidence",
    scope: String = "order",
    freshness: String = "current",
    provenance: String = "payment-ledger"
  ): JudgmentResult =
    JudgmentResult(
      JudgmentActionIdentity(judgment),
      AlternativeIdentity(alternative),
      Rationale(rationale),
      Evidence(evidence),
      EvidenceScope(scope),
      EvidenceFreshness(freshness),
      EvidenceProvenance(provenance)
    )

  private def _fixture: String = {
    val stream = Option(getClass.getResourceAsStream("/workflow/candidate-admission-producer-abi.json"))
      .getOrElse(fail("candidate-admission producer ABI fixture is missing"))
    val source = Source.fromInputStream(stream, "UTF-8")
    try source.mkString finally source.close()
  }

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail("expected Candidate-Admission receiver admission to succeed"))

  private def _diagnostic[A](result: Consequence[A]): Diagnostic =
    result match {
      case Consequence.Failure(conclusion) =>
        conclusion.getException match {
          case Some(exception: CandidateAdmissionProducerAbiException) => exception.diagnostic
          case other => fail(s"expected Candidate-Admission receiver diagnostic but got $other")
        }
      case _ => fail("expected Candidate-Admission receiver admission to fail")
    }
}
