package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.cncf.projection.RuleProjection
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the restricted RuleSet descriptor and its
 * payload-safe read-only projection.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuleSetDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RuleSetDescriptor" should {
    "decode a restricted Record definition and project only declaration metadata" in {
      Given("a Record-shaped RuleSet descriptor with calculation and production Rules")
      val descriptor = Record.data(
        "id" -> "pricing",
        "version" -> "2026-07",
        "inputFacts" -> Vector("subtotal", "country"),
        "outputFacts" -> Vector("tax"),
        "rules" -> Vector(
          Record.data(
            "id" -> "tax",
            "family" -> "calculation",
            "priority" -> 10,
            "conditions" -> Record.data("currency" -> "JPY"),
            "outputs" -> Record.data("name" -> "tax"),
            "explanation" -> "Calculate consumption tax"
          ),
          Record.data(
            "id" -> "notify",
            "family" -> "production",
            "priority" -> 0
          )
        )
      )

      When("the restricted descriptor is decoded and projected")
      val ruleset = _success(RuleSetDescriptor.decodeC(descriptor))
      val projection = RuleProjection.project(ruleset)

      Then("canonical Rule order and declaration metadata are available without executable code or runtime handles")
      ruleset.orderedRules.map(_.id.value) shouldBe Vector("tax", "notify")
      projection.getString("id") shouldBe Some("pricing")
      projection.getAny("rules") should not be empty
      projection.toString should not include "ActionCall"
      projection.toString should not include "payload"
    }

    "project an evaluation agenda and explanations without serializing fact or action payload values" in {
      Given("an evaluation result containing confidential fact and action payload values")
      val ruleset = _success(RuleSetDescriptor.decodeC(_descriptor("calculation", 0)))
      val memory = _success(WorkingMemory.fromFactsC(Vector(
        Fact(FactId("customer"), FactName("customer"), RuleFactValue.scalar("confidential-customer"))
      )))
      val agenda = _success(Agenda.fromActivationsC(Vector(
        RuleActivation(RuleActivationId("tax-activation"), RuleId("tax"), RulePriority(0), Vector(FactId("customer")))
      )))
      val result = RuleEvaluationResult(
        ruleset.identity,
        memory,
        agenda = agenda,
        explanations = Vector(
          RuleExplanation(RuleId("tax"), RuleExplanationKind.Calculated, "Calculated tax", Vector(FactId("customer")))
        ),
        calculations = Vector(
          RuleCalculationResult(RuleId("tax"), "tax", RuleFactValue.scalar("confidential-tax"), Vector(FactId("customer")))
        ),
        decisionTables = Vector(
          RuleDecisionTableResult(
            RuleId("tax"),
            DecisionTableId("tax-rate"),
            Some(DecisionTableRowId("jp-standard")),
            Record.data("taxRate" -> "confidential-tax-rate"),
            Vector(FactId("customer"))
          )
        ),
        actionPlans = Vector(
          RuleActionPlan.Recommendation(
            RuleActionPlanId("review"),
            RuleId("tax"),
            "Review tax",
            Record.data("internal" -> "confidential-action-detail")
          )
        )
      ).normalized

      When("the evaluation is rendered for a read-only diagnostic surface")
      val projection = RuleProjection.projectEvaluation(result)

      Then("agenda, explanation, and action kinds are available without raw values")
      projection.getAny("agenda") should not be empty
      projection.getAny("explanations") should not be empty
      projection.getAny("decisionTables") should not be empty
      projection.getAny("plannedActions") should not be empty
      projection.toString should not include "confidential-customer"
      projection.toString should not include "confidential-tax"
      projection.toString should not include "confidential-tax-rate"
      projection.toString should not include "confidential-action-detail"
    }

    "reject malformed rule family and non-integral priority values" in {
      Given("descriptors whose declared Rule values cannot be interpreted deterministically")
      val invalidfamily = _descriptor("unknown", 0)
      val fractionalpriority = _descriptor("calculation", BigDecimal("1.5"))

      When("the decoder validates the restricted vocabulary")
      val first = RuleSetDescriptor.decodeC(invalidfamily)
      val second = RuleSetDescriptor.decodeC(fractionalpriority)

      Then("both invalid forms remain structured Consequence failures")
      _is_failure(first) shouldBe true
      _is_failure(second) shouldBe true
    }

    "reject scalar structural fields instead of silently removing descriptor metadata" in {
      Given("a Rule descriptor whose conditions value is not a Record")
      val descriptor = _descriptor("calculation", 0).upsertSingle("rules", Vector(
        Record.data(
          "id" -> "tax",
          "family" -> "calculation",
          "conditions" -> "not-a-record"
        )
      ))

      When("the restricted decoder validates the descriptor shape")
      val result = RuleSetDescriptor.decodeC(descriptor)

      Then("the malformed structural field is a structured failure")
      _is_failure(result) shouldBe true
    }
  }

  private def _descriptor(family: String, priority: Any): Record =
    Record.data(
      "id" -> "pricing",
      "version" -> "1",
      "rules" -> Vector(Record.data(
        "id" -> "tax",
        "family" -> family,
        "priority" -> priority
      ))
    )

  private def _success[A](result: Consequence[A]): A =
    result.toOption.getOrElse(fail(s"expected success: $result"))

  private def _is_failure[A](result: Consequence[A]): Boolean =
    result match {
      case Consequence.Success(_) => false
      case Consequence.Failure(_) => true
    }
}
