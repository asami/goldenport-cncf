package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for the pure restricted calculation, constraint,
 * and forward-derivation evaluator.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class BuiltinRuleEvaluatorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _ages = Gen.chooseNum(0, 100)

  "BuiltinRuleEvaluator" should {
    "calculate consumption tax from exact decimal facts without mutating WorkingMemory" in {
      Given("a tax calculation Rule and decimal input Facts")
      val ruleset = _ruleset(
        Vector(_rule("tax", RuleFamily.Calculation))
      )
      val program = _program(
        ruleset,
        calculations = Vector(
          RuleCalculation(
            RuleId("tax"),
            "tax",
            RuleExpression.Multiply(_fact_value("subtotal"), _fact_value("rate")),
            RuleExplanationTemplate("Calculated consumption tax")
          )
        )
      )
      val memory = _memory(
        _fact("subtotal", "subtotal", BigDecimal(1000)),
        _fact("rate", "rate", BigDecimal("0.10"))
      )

      When("the built-in evaluator evaluates the restricted arithmetic expression")
      val result = _success(BuiltinRuleEvaluator.evaluateC(ruleset, program, memory))

      Then("the calculation is a structural result and the supplied facts remain unchanged")
      result.values shouldBe Record.data("tax" -> BigDecimal(100))
      result.calculations.map(_.sourceFactIds.map(_.value)) shouldBe Vector(Vector("rate", "subtotal"))
      result.explanations.map(_.summary) should contain("Calculated consumption tax")
      result.workingMemory shouldBe memory
      result.derivedFacts shouldBe Vector.empty
    }

    "return a side-effect-free constraint violation when reporting mode fails" in {
      Given("an adult-age constraint configured to report rather than reject")
      val ruleset = _ruleset(Vector(_rule("adult", RuleFamily.Constraint)))
      val program = _program(
        ruleset,
        constraints = Vector(
          RuleConstraint(
            RuleId("adult"),
            RuleExpression.GreaterThanOrEqual(_fact_value("age"), _number(20)),
            "minimum-age",
            "Applicant must be an adult",
            explanation = RuleExplanationTemplate("Age policy failed")
          )
        )
      )
      val memory = _memory(_fact("age", "age", 17))

      When("the predicate evaluates to false")
      val result = _success(BuiltinRuleEvaluator.evaluateC(ruleset, program, memory))

      Then("evaluation returns a structural violation without changing the working facts")
      result.constraintViolations.map(_.code) shouldBe Vector("minimum-age")
      result.explanations.map(_.kind) should contain(RuleExplanationKind.ConstraintViolation)
      result.explanations.map(_.summary) should contain("Age policy failed")
      result.workingMemory shouldBe memory
    }

    "return a structured Consequence failure when rejecting constraint mode fails" in {
      Given("an adult-age constraint configured to reject")
      val ruleset = _ruleset(Vector(_rule("adult", RuleFamily.Constraint)))
      val program = _program(
        ruleset,
        constraints = Vector(
          RuleConstraint(
            RuleId("adult"),
            RuleExpression.GreaterThanOrEqual(_fact_value("age"), _number(20)),
            "minimum-age",
            "Applicant must be an adult",
            RuleConstraintMode.Reject
          )
        )
      )

      When("the predicate evaluates to false")
      val result = BuiltinRuleEvaluator.evaluateC(ruleset, program, _memory(_fact("age", "age", 17)))

      Then("no component-local error value is created")
      _is_failure(result) shouldBe true
    }

    "reach a deterministic forward-derivation fixed point across canonical Rule order" in {
      Given("two derivations whose dependency runs opposite to canonical Rule identifier order")
      val ruleset = _ruleset(
        Vector(
          _rule("derive-z-adult", RuleFamily.Derivation),
          _rule("derive-a-eligible", RuleFamily.Derivation)
        )
      )
      val program = _program(
        ruleset,
        derivations = Vector(
          RuleDerivation(
            RuleId("derive-z-adult"),
            FactId("adult"),
            FactName("adult"),
            RuleExpression.GreaterThanOrEqual(_fact_value("age"), _number(20)),
            _boolean(true),
            explanation = RuleExplanationTemplate("Derived adult status")
          ),
          RuleDerivation(
            RuleId("derive-a-eligible"),
            FactId("eligible"),
            FactName("eligible"),
            RuleExpression.Equal(_fact_value("adult"), _boolean(true)),
            _boolean(true),
            explanation = RuleExplanationTemplate("Derived eligibility")
          )
        ).reverse
      )

      When("the first canonical pass cannot yet satisfy the earlier dependent Rule")
      val result = _success(BuiltinRuleEvaluator.evaluateC(ruleset, program, _memory(_fact("age", "age", 20))))

      Then("fixed-point evaluation derives both Facts with structural provenance")
      result.derivedFacts.map(_.id.value) shouldBe Vector("adult", "eligible")
      result.workingMemory.facts.map(_.id.value) shouldBe Vector("adult", "age", "eligible")
      result.derivedFacts.map(_.provenance.sourceRuleId.map(_.value)) shouldBe Vector(
        Some("derive-z-adult"),
        Some("derive-a-eligible")
      )
      result.explanations.map(_.summary) should contain allOf ("Derived adult status", "Derived eligibility")
    }

    "remain deterministic for generated fact declaration orders" in {
      Given("an eligibility derivation and generated age inputs")
      val property = Prop.forAll(_ages) { age =>
        val ruleset = _ruleset(Vector(_rule("adult", RuleFamily.Derivation)))
        val program = _program(
          ruleset,
          derivations = Vector(
            RuleDerivation(
              RuleId("adult"),
              FactId("adult"),
              FactName("adult"),
              RuleExpression.GreaterThanOrEqual(_fact_value("age"), _number(20)),
              _boolean(true)
            )
          )
        )
        val facts = Vector(_fact("age", "age", age), _fact("ignored", "ignored", "value"))
        val left = _success(BuiltinRuleEvaluator.evaluateC(ruleset, program, _memory(facts*)))
        val right = _success(BuiltinRuleEvaluator.evaluateC(ruleset, program, _memory(facts.reverse*)))

        left.workingMemory == right.workingMemory &&
          left.derivedFacts == right.derivedFacts &&
          left.explanations == right.explanations
      }

      When("the same fact set is supplied in different declaration orders")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(32), property)

      Then("the evaluator returns the same immutable memory and explanations")
      checked.passed shouldBe true
    }

    "reject invalid program Rule-family references and floating-point arithmetic" in {
      Given("a calculation Program that references a constraint Rule and a floating-point input Fact")
      val ruleset = _ruleset(Vector(_rule("constraint", RuleFamily.Constraint), _rule("calculation", RuleFamily.Calculation)))
      val invalidprogram = RuleProgram.createC(
        ruleset,
        calculations = Vector(RuleCalculation(RuleId("constraint"), "value", _number(1)))
      )
      val program = _program(
        ruleset,
        calculations = Vector(
          RuleCalculation(
            RuleId("calculation"),
            "value",
            RuleExpression.Add(_fact_value("amount"), _number(1))
          )
        )
      )

      When("configuration and arithmetic input are evaluated")
      val result = BuiltinRuleEvaluator.evaluateC(ruleset, program, _memory(_fact("amount", "amount", 1.5d)))

      Then("both conditions remain structured Consequence failures")
      _is_failure(invalidprogram) shouldBe true
      _is_failure(result) shouldBe true
    }

    "plan matching production actions without firing them during evaluation" in {
      Given("a production Rule whose predicate depends on a supplied Fact")
      val ruleset = _ruleset(Vector(_rule("notify", RuleFamily.Production)))
      val plan = RuleActionPlan.Recommendation(
        RuleActionPlanId("recommend-review"),
        RuleId("notify"),
        "Review the exceptional order"
      )
      val program = _success(RuleProgram.createC(
        ruleset,
        productions = Vector(RuleProduction(
          RuleId("notify"),
          RuleExpression.Equal(_fact_value("exceptional"), _boolean(true)),
          Vector(plan),
          RuleExplanationTemplate("Planned exceptional-order review")
        ))
      ))

      When("the built-in evaluator evaluates matching Facts")
      val result = _success(BuiltinRuleEvaluator.evaluateC(
        ruleset,
        program,
        _memory(_fact("exceptional", "exceptional", true))
      ))

      Then("evaluation returns the immutable plan and explanation without executing a runtime action")
      result.actionPlans shouldBe Vector(plan)
      result.explanations.map(_.kind) should contain(RuleExplanationKind.Planned)
      result.workingMemory.facts.map(_.id.value) shouldBe Vector("exceptional")
    }
  }

  private def _ruleset(rules: Vector[Rule]): RuleSet =
    _success(RuleSet.createC(RuleSetIdentity(RuleSetId("domain"), RuleSetVersion("1")), rules))

  private def _program(
    ruleset: RuleSet,
    calculations: Vector[RuleCalculation] = Vector.empty,
    constraints: Vector[RuleConstraint] = Vector.empty,
    derivations: Vector[RuleDerivation] = Vector.empty
  ): RuleProgram =
    _success(RuleProgram.createC(
      ruleset,
      calculations = calculations,
      constraints = constraints,
      derivations = derivations
    ))

  private def _rule(id: String, family: RuleFamily): Rule =
    Rule(RuleId(id), family)

  private def _memory(facts: Fact*): WorkingMemory =
    _success(WorkingMemory.fromFactsC(facts))

  private def _fact(id: String, name: String, value: Any): Fact =
    Fact(FactId(id), FactName(name), RuleFactValue.scalar(value))

  private def _fact_value(id: String): RuleExpression =
    RuleExpression.FactValue(FactId(id))

  private def _number(value: Int): RuleExpression =
    RuleExpression.Literal(RuleFactValue.scalar(BigDecimal(value)))

  private def _boolean(value: Boolean): RuleExpression =
    RuleExpression.Literal(RuleFactValue.scalar(value))

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
