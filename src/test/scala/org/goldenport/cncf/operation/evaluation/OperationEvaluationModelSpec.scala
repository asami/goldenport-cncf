package org.goldenport.cncf.operation.evaluation

import java.time.{Duration, Instant}
import org.goldenport.cncf.observability.ConclusionDiagnostics
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the Phase 48 provider-neutral capture model.
 *
 * @since   Jul. 23, 2026
 * @version Jul. 23, 2026
 * @author  ASAMI, Tomoharu
 */
final class OperationEvaluationModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _instant = Instant.parse("2026-07-23T00:00:00Z")
  private val _names = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString.take(48))

  "Operation Evaluation capture model" should {
    "admit bounded logical identities without interpreting provider references" which {
      "normalizes generated operation names and rejects unsafe external references" in {
        Given("generated logical names and malformed provider-owned references")
        val property = Prop.forAll(_names) { name =>
          OperationEvaluationName.parseC(name.toUpperCase).toOption.exists(_.print == name)
        }
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(48), property)
        val rejected = Vector("", "bad\u0000reference", "x" * 257).map(CorpusRevisionReference.parseC)

        When("the framework constructs safe capture identity values")
        val operation = OperationEvaluationOperationIdentity.createC("demo", "catalog", "price_query")

        Then("logical names are canonical and provider references remain bounded opaque values")
        checked.passed shouldBe true
        operation.toOption.map(_.print) shouldBe Some("demo.catalog.price_query")
        rejected.forall(_.isFaillure) shouldBe true
      }

      "require complete experiment run correlation while preserving retry identity" in {
        Given("one logical execution with two attempts and provider-owned experiment references")
        val execution = _execution_id("execution")
        val attemptone = _attempt_id("attempt-1")
        val attempttwo = _attempt_id("attempt-2")
        val experiment = ExperimentReference.parseC("experiment-17").toOption.get
        val arm = ExperimentArmReference.parseC("arm-b").toOption.get
        val run = ExperimentRunReference.parseC("run-4").toOption.get
        val revision = CorpusRevisionReference.parseC("revision-9").toOption.get

        When("complete and incomplete run correlations are constructed")
        val admitted = ExperimentEvaluationCorrelation.createC(
          experiment,
          Some(arm),
          Some(run),
          Some(revision)
        )
        val incomplete = ExperimentEvaluationCorrelation.createC(experiment, run = Some(run))
        val first = _correlation(execution, attemptone, experiment = admitted.toOption)
        val retry = first.copy(attemptId = attempttwo)

        Then("a run requires arm and corpus revision and retry changes only attempt identity")
        admitted.isSuccess shouldBe true
        incomplete.isFaillure shouldBe true
        retry.executionId shouldBe first.executionId
        retry.experiment shouldBe first.experiment
        retry.attemptId should not be first.attemptId
      }
    }

    "represent automatic execution facts without business payloads" which {
      "emits framework start and terminal structures with stable correlation" in {
        Given("one authorized operation attempt and its canonical terminal outcome")
        val correlation = _correlation(_execution_id("execution"), _attempt_id("attempt"))
        val start = OperationEvaluationStartFact.create(_fact_id("start"), correlation, _instant)
        val terminal = OperationEvaluationTerminalFact.createC(
          _fact_id("terminal"),
          correlation,
          _instant.plusMillis(25),
          OperationEvaluationOutcome.Failure,
          Duration.ofMillis(25),
          Some(ConclusionDiagnostics.unknown)
        ).toOption.get

        When("the facts are projected for a provider-neutral sink")
        val startrecord = start.toRecord
        val terminalrecord = terminal.toRecord
        val text = s"$startrecord $terminalrecord"

        Then("source, outcome, and diagnostics are present without request or response payloads")
        start.source shouldBe OperationEvaluationFactSource.Framework
        terminal.source shouldBe OperationEvaluationFactSource.Framework
        startrecord.getString("kind") shouldBe Some("operation-start")
        terminalrecord.getString("outcome") shouldBe Some("failure")
        text should not include "requestPayload"
        text should not include "responsePayload"
        text should not include "prompt"
        text should not include "credential"
      }

      "rejects contradictory terminal structures" in {
        Given("a successful outcome with a failure diagnostic and a negative duration")
        val correlation = _correlation(_execution_id("execution"), _attempt_id("attempt"))

        When("the terminal facts are constructed")
        val diagnostic = OperationEvaluationTerminalFact.createC(
          _fact_id("diagnostic"),
          correlation,
          _instant,
          OperationEvaluationOutcome.Success,
          Duration.ZERO,
          Some(ConclusionDiagnostics.unknown)
        )
        val duration = OperationEvaluationTerminalFact.createC(
          _fact_id("duration"),
          correlation,
          _instant,
          OperationEvaluationOutcome.Failure,
          Duration.ofMillis(-1)
        )

        Then("the model rejects both contradictions before sink delivery")
        diagnostic.isFaillure shouldBe true
        duration.isFaillure shouldBe true
      }
    }

    "bound application supplemental facts and provider delivery results" which {
      "distinguishes application facts from provider delivery diagnostics" in {
        Given("one bounded corpus candidate, experiment observation, and provider sink")
        val correlation = _correlation(_execution_id("execution"), _attempt_id("attempt"))
        val label = OperationEvaluationLabel.createC("quality", "reviewed").toOption.get
        val measurement = OperationEvaluationMeasurement.createC("score", BigDecimal("0.95")).toOption.get
        val candidate = CorpusCandidateFact.createC(
          _fact_id("candidate"),
          correlation,
          _instant,
          labels = Vector(label)
        ).toOption.get
        val observation = ExperimentObservationFact.createC(
          _fact_id("observation"),
          correlation,
          _instant,
          Vector(measurement)
        ).toOption.get
        val sink = OperationEvaluationSinkIdentity.createC(
          "corpus-sink",
          "catalog",
          "textus-corpus",
          Some("primary")
        ).toOption.get
        val delivery = OperationEvaluationDeliveryResult.createC(
          candidate.id,
          sink,
          OperationEvaluationDeliveryStatus.Delivered
        ).toOption.get

        When("the supplemental and delivery records are projected")
        val oversized = CorpusCandidateFact.createC(
          _fact_id("oversized"),
          correlation,
          _instant,
          labels = Vector.fill(CorpusCandidateFact.MAXIMUM_LABELS + 1)(label)
        )
        val unboundedmeasurement = OperationEvaluationMeasurement.createC(
          "unbounded",
          BigDecimal("1" * (OperationEvaluationMeasurement.MAXIMUM_PRECISION + 1))
        )
        val unboundeddelivery = OperationEvaluationDeliveryResult.createC(
          candidate.id,
          sink,
          OperationEvaluationDeliveryStatus.Limited,
          Vector.fill(OperationEvaluationDeliveryResult.MAXIMUM_LIMITATIONS + 1)(
            OperationEvaluationLimitation(OperationEvaluationLimitationKind.Saturated)
          )
        )

        Then("application and provider ownership remain explicit and collection bounds are enforced")
        candidate.source shouldBe OperationEvaluationFactSource.Application
        observation.source shouldBe OperationEvaluationFactSource.Application
        delivery.source shouldBe OperationEvaluationFactSource.Provider
        delivery.confidentiality shouldBe org.goldenport.schema.DataConfidentiality.Internal
        delivery.toRecord.getString("source") shouldBe Some("provider")
        oversized.isFaillure shouldBe true
        unboundedmeasurement.isFaillure shouldBe true
        unboundeddelivery.isFaillure shouldBe true
      }
    }

    "classify malformed and overflowing values through structured validation" in {
      Given("empty text, control text, empty measurements, and oversized text")
      val correlation = _correlation(_execution_id("execution"), _attempt_id("attempt"))
      val emptytext = OperationEvaluationText.parseC("")
      val controltext = OperationEvaluationText.parseC("unsafe\u0000text")
      val oversizedtext = OperationEvaluationText.parseC("x" * (OperationEvaluationText.MAXIMUM_BYTES + 1))
      val emptymeasurements = ExperimentObservationFact.createC(
        _fact_id("empty-measurements"),
        correlation,
        _instant,
        Vector.empty
      )

      When("the common Conclusion diagnostics classify each failed boundary")
      val keys = Vector(emptytext, controltext, oversizedtext, emptymeasurements).map(
        x => x.fold(
          conclusion => ConclusionDiagnostics.classify(conclusion).diagnosticKey,
          _ => fail("expected structured validation failure")
        )
      )

      Then("format and invalid values are not reported as size overflow")
      keys shouldBe Vector("argument", "format", "limit", "argument")
    }

    "project optional CML evaluation declarations independently from automatic capture" in {
      Given("an operation declaration containing logical corpus and experiment policy names")
      val profile = OperationEvaluationName.parseC("route-resolution").toOption.get
      val declaration = CmlOperationEvaluationDeclaration(
        corpus = Some(CmlCorpusEvaluationDeclaration(
          CorpusCaptureMode.Candidate,
          profile,
          outcomes = Vector(OperationEvaluationOutcome.Success, OperationEvaluationOutcome.Failure)
        )),
        experiment = Some(CmlExperimentEvaluationDeclaration(
          eligible = true,
          purpose = profile,
          variantProfile = Some(OperationEvaluationName.parseC("execution-plan").toOption.get)
        ))
      )

      When("the declaration is projected as operation metadata")
      val record = declaration.toRecord

      Then("only logical policy metadata is exposed and an absent declaration remains valid")
      record.getRecord("corpus").flatMap(_.getString("profile")) shouldBe Some("route-resolution")
      record.getRecord("experiment").flatMap(_.getString("variantProfile")) shouldBe Some("execution-plan")
      CmlOperationEvaluationDeclaration().isEmpty shouldBe true
    }
  }

  private def _execution_id(entropy: String): OperationEvaluationExecutionId =
    OperationEvaluationExecutionId("spec", "evaluation", Some(_instant), Some(entropy))

  private def _attempt_id(entropy: String): OperationEvaluationAttemptId =
    OperationEvaluationAttemptId("spec", "evaluation", Some(_instant), Some(entropy))

  private def _fact_id(entropy: String): OperationEvaluationFactId =
    OperationEvaluationFactId("spec", "evaluation", Some(_instant), Some(entropy))

  private def _correlation(
    executionid: OperationEvaluationExecutionId,
    attemptid: OperationEvaluationAttemptId,
    experiment: Option[ExperimentEvaluationCorrelation] = None
  ): OperationEvaluationCorrelation =
    OperationEvaluationCorrelation(
      executionid,
      attemptid,
      OperationEvaluationOperationIdentity.createC("demo", "catalog", "price").toOption.get,
      experiment = experiment
    )
}
