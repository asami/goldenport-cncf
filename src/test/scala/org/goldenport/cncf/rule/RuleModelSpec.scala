package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the engine-neutral Rule and Inference model.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuleModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _priorities =
    Gen.listOfN(6, Gen.chooseNum(-100, 100))

  "Rule model foundations" should {
    "canonicalize RuleSet declaration order independently of input order" in {
      Given("generated rules with stable identifiers and arbitrary priorities")
      val property = Prop.forAll(_priorities) { priorities =>
        val rules = priorities.zipWithIndex.map { case (priority, index) =>
          Rule(
            RuleId(s"rule-$index"),
            RuleFamily.Calculation,
            RulePriority(priority)
          )
        }.toVector
        val identity = RuleSetIdentity(RuleSetId("pricing"), RuleSetVersion("1"))

        val left = RuleSet.createC(identity, rules, Vector(FactName("product")), Vector(FactName("price")))
        val right = RuleSet.createC(identity, rules.reverse, Vector(FactName("product")), Vector(FactName("price")))

        _rule_ids(left) == _rule_ids(right)
      }

      When("equivalent RuleSets are checked across generated declaration orders")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("their rule priorities and identifiers produce the same agenda basis")
      checked.passed shouldBe true
    }

    "canonicalize WorkingMemory and Agenda order without changing fact or activation identity" in {
      Given("facts and activations supplied in reverse order")
      val facts = Vector(
        _fact("fact-c", "product"),
        _fact("fact-a", "customer"),
        _fact("fact-b", "quantity")
      )
      val activations = Vector(
        RuleActivation(RuleActivationId("activation-c"), RuleId("tax"), RulePriority(10)),
        RuleActivation(RuleActivationId("activation-a"), RuleId("price"), RulePriority(20)),
        RuleActivation(RuleActivationId("activation-b"), RuleId("discount"), RulePriority(20))
      )

      When("the immutable collections are created")
      val memory = _success(WorkingMemory.fromFactsC(facts))
      val agenda = _success(Agenda.fromActivationsC(activations))

      Then("the collection ordering is deterministic and highest priority is first")
      memory.facts.map(_.id.value) shouldBe Vector("fact-a", "fact-b", "fact-c")
      agenda.entries.map(_.id.value) shouldBe Vector("activation-b", "activation-a", "activation-c")
      agenda.next.map(_.ruleId.value) shouldBe Some("discount")
    }

    "admit WorkingMemory replacement only through the same validation boundary" in {
      Given("an immutable WorkingMemory with one valid Fact")
      val memory = _success(WorkingMemory.fromFactsC(Vector(_fact("product", "product"))))

      When("a valid Fact replaces it and an invalid Fact is attempted")
      val replaced = memory.putC(_fact("product", "product-price"))
      val rejected = memory.putC(_fact("", "missing-id"))

      Then("replacement retains stable Fact identity while invalid state is rejected")
      _success(replaced).facts.map(_.name.value) shouldBe Vector("product-price")
      _is_failure(rejected) shouldBe true
    }

    "reject duplicate identifiers instead of selecting an implicit winner" in {
      Given("duplicate rule, fact, and activation identifiers")
      val identity = RuleSetIdentity(RuleSetId("pricing"), RuleSetVersion("1"))
      val duplicaterules = Vector(
        Rule(RuleId("price"), RuleFamily.DecisionTable),
        Rule(RuleId("price"), RuleFamily.Calculation)
      )
      val duplicatefacts = Vector(_fact("product", "product"), _fact("product", "product-copy"))
      val duplicateactivations = Vector(
        RuleActivation(RuleActivationId("price-1"), RuleId("price"), RulePriority.Default),
        RuleActivation(RuleActivationId("price-1"), RuleId("price"), RulePriority.Default)
      )

      When("the foundation collection factories validate their inputs")
      val ruleset = RuleSet.createC(identity, duplicaterules)
      val memory = WorkingMemory.fromFactsC(duplicatefacts)
      val agenda = Agenda.fromActivationsC(duplicateactivations)

      Then("each duplicate is represented as a structured Consequence failure")
      _is_failure(ruleset) shouldBe true
      _is_failure(memory) shouldBe true
      _is_failure(agenda) shouldBe true
    }

    "reject an empty RuleSet rather than creating an evaluator with no semantic definition" in {
      Given("a named and versioned RuleSet with no Rules")
      val identity = RuleSetIdentity(RuleSetId("pricing"), RuleSetVersion("1"))

      When("the RuleSet factory validates the definition")
      val ruleset = RuleSet.createC(identity, Vector.empty)

      Then("the empty definition is represented as a structured Consequence failure")
      _is_failure(ruleset) shouldBe true
    }

    "reject an Agenda explanation attributed to a different Rule" in {
      Given("an activation whose explanation identifies another Rule")
      val activation = RuleActivation(
        RuleActivationId("price-1"),
        RuleId("price"),
        RulePriority.Default,
        explanation = Some(RuleExplanation(RuleId("tax"), RuleExplanationKind.Matched, "wrong attribution"))
      )

      When("the Agenda validates explanation attribution")
      val agenda = Agenda.fromActivationsC(Vector(activation))

      Then("the conflicting explanation is rejected rather than normalized silently")
      _is_failure(agenda) shouldBe true
    }

    "normalize explanation provenance without exposing a mutable evaluation model" in {
      Given("an evaluation result with unsorted input and output fact references")
      val identity = RuleSetIdentity(RuleSetId("pricing"), RuleSetVersion("1"))
      val memory = _success(WorkingMemory.fromFactsC(Vector(_fact("product", "product"))))
      val explanation = RuleExplanation(
        RuleId("price"),
        RuleExplanationKind.Calculated,
        "Price selected",
        Vector(FactId("quantity"), FactId("customer")),
        Vector(FactId("price"), FactId("currency")),
        Record.empty
      )

      When("the immutable evaluation result is normalized for diagnostics")
      val result = RuleEvaluationResult(identity, memory, explanations = Vector(explanation)).normalized

      Then("only stable identifiers and explanation structure determine the order")
      result.explanations.head.sourceFactIds.map(_.value) shouldBe Vector("customer", "quantity")
      result.explanations.head.resultFactIds.map(_.value) shouldBe Vector("currency", "price")
      result.workingMemory.facts.head.id.value shouldBe "product"
    }

    "keep explanation ordering deterministic when Fact identifiers contain delimiter characters" in {
      Given("two explanations whose Fact identifiers would collide under delimiter joining")
      val identity = RuleSetIdentity(RuleSetId("pricing"), RuleSetVersion("1"))
      val memory = _success(WorkingMemory.fromFactsC(Vector(_fact("product", "product"))))
      val left = RuleExplanation(
        RuleId("price"),
        RuleExplanationKind.Calculated,
        "Price selected",
        Vector(FactId("a"), FactId("b\u0001c"))
      )
      val right = RuleExplanation(
        RuleId("price"),
        RuleExplanationKind.Calculated,
        "Price selected",
        Vector(FactId("a\u0001b"), FactId("c"))
      )

      When("the same explanations are normalized from opposite input orders")
      val first = RuleEvaluationResult(identity, memory, explanations = Vector(right, left)).normalized
      val second = RuleEvaluationResult(identity, memory, explanations = Vector(left, right)).normalized

      Then("unambiguous Fact identifier encoding yields the same explanation order")
      first.explanations shouldBe second.explanations
      first.explanations.head.sourceFactIds.map(_.value) shouldBe Vector("a", "b\u0001c")
    }
  }

  private def _fact(id: String, name: String): Fact =
    Fact(FactId(id), FactName(name), RuleFactValue.scalar(name))

  private def _rule_ids(result: Consequence[RuleSet]): Vector[String] =
    result.toOption.map(_.orderedRules.map(_.id.value)).getOrElse(Vector.empty)

  private def _success[A](result: Consequence[A]): A =
    result match {
      case Consequence.Success(value) => value
      case Consequence.Failure(conclusion) => fail(conclusion.show)
    }

  private def _is_failure[A](result: Consequence[A]): Boolean =
    result match {
      case Consequence.Success(_) => false
      case Consequence.Failure(_) => true
    }
}
