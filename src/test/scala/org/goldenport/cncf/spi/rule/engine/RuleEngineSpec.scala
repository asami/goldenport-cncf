package org.goldenport.cncf.spi.rule.engine

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.http.RuntimeDashboardMetrics
import org.goldenport.cncf.rule.{DecisionColumn, DecisionCondition, DecisionTable, DecisionTableId, DecisionTableRow, DecisionTableRowId, Fact, FactId, FactName, Rule, RuleConstraint, RuleConstraintMode, RuleDecisionTable, RuleDecisionTableBinding, RuleDerivation, RuleExpression, RuleFactValue, RuleFamily, RulePriority, RuleProgram, RuleSet, RuleSetId, RuleSetIdentity, RuleSetVersion, WorkingMemory}
import org.goldenport.cncf.spi.{SpiProvider, SpiProviderComponent, SpiResolver, SpiSelection}
import org.goldenport.cncf.testutil.TestComponentFactory
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.protocol.Protocol
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * Executable specification for provider-neutral RuleEngine and InferenceEngine
 * sockets. The providers are intentionally small: selection remains owned by
 * the common SPI resolver.
 *
 * @since   Jul. 16, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class RuleEngineSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RuleEngine SPI" should {
    "evaluate a built-in calculation through the provider-neutral contract" in {
      Given("a RuleEngine request with a calculation RuleProgram")
      given ExecutionContext = ExecutionContext.create()
      val ruleset = _ruleset(Vector(_rule("tax", RuleFamily.Calculation)))
      val program = _program(
        ruleset,
        calculations = Vector(
          org.goldenport.cncf.rule.RuleCalculation(
            org.goldenport.cncf.rule.RuleId("tax"),
            "tax",
            RuleExpression.Multiply(_fact_value("subtotal"), _literal(BigDecimal("0.10")))
          )
        )
      )
      val request = RuleEvaluationRequest(ruleset, program, _memory(_fact("subtotal", BigDecimal(1000))))

      When("the built-in RuleEngine is invoked")
      val result = _success(RuleEngine.builtin.evaluate(request))

      Then("the SPI returns the immutable evaluation result without exposing an engine implementation type")
      result.values.getDecimal("tax") shouldBe Some(BigDecimal(100))
      result.workingMemory.facts.map(_.id.value) shouldBe Vector("subtotal")
    }

    "evaluate tax and pricing decision tables through the installed RuleEngine" in {
      Given("a consumer RuleEngine socket with price and tax domain tables")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("rule_engine_tables")
      val provider = _initialized_component(subsystem, "rule-engine-provider", BuiltinProviderComponent())
      val consumer = _initialized_component(subsystem, "rule-engine-consumer", RuleConsumerComponent())
      val ruleset = _ruleset(Vector(
        _rule("price", RuleFamily.DecisionTable),
        _rule("tax-rate", RuleFamily.DecisionTable)
      ))
      val pricetable = _table(
        "price-list",
        "product",
        Vector(_decisionrow("book", "product", "book", Record.data("unitPrice" -> BigDecimal(1200))))
      )
      val taxtable = _table(
        "tax-rate",
        "jurisdiction",
        Vector(_decisionrow("jp-standard", "jurisdiction", "JP", Record.data("taxRate" -> BigDecimal("0.10"))))
      )
      val program = _success(RuleProgram.createC(
        ruleset,
        decisiontables = Vector(
          RuleDecisionTable(
            org.goldenport.cncf.rule.RuleId("price"),
            pricetable,
            Vector(RuleDecisionTableBinding("product", FactId("product")))
          ),
          RuleDecisionTable(
            org.goldenport.cncf.rule.RuleId("tax-rate"),
            taxtable,
            Vector(RuleDecisionTableBinding("jurisdiction", FactId("jurisdiction")))
          )
        )
      ))

      When("the component evaluates the normalized domain Facts through its SPI socket")
      SpiResolver.resolve(Vector(provider, consumer)) shouldBe a[Consequence.Success[_]]
      val result = _success(consumer.ruleEngine.evaluate(RuleEvaluationRequest(
        ruleset,
        program,
        _memory(_fact("product", "book"), _fact("jurisdiction", "JP"))
      )))

      Then("both deterministic table selections are part of the canonical RuleEngine result")
      result.decisionTables.map(_.tableId.value) shouldBe Vector("price-list", "tax-rate")
      result.decisionTables.map(_.selectedRowId.map(_.value)) shouldBe Vector(Some("book"), Some("jp-standard"))
      result.decisionTables.map(_.output) shouldBe Vector(
        Record.data("unitPrice" -> BigDecimal(1200)),
        Record.data("taxRate" -> BigDecimal("0.10"))
      )
    }

    "treat a missing decision-table Fact as no match and reject incomplete bindings" in {
      Given("a decision-table Rule whose product column is bound to one Fact")
      given ExecutionContext = ExecutionContext.create()
      val ruleset = _ruleset(Vector(_rule("price", RuleFamily.DecisionTable)))
      val table = _table(
        "price-list",
        "product",
        Vector(_decisionrow("book", "product", "book", Record.data("unitPrice" -> BigDecimal(1200))))
      )
      val complete = _success(RuleProgram.createC(
        ruleset,
        decisiontables = Vector(RuleDecisionTable(
          org.goldenport.cncf.rule.RuleId("price"),
          table,
          Vector(RuleDecisionTableBinding("product", FactId("product")))
        ))
      ))
      val incomplete = RuleProgram.createC(
        ruleset,
        decisiontables = Vector(RuleDecisionTable(
          org.goldenport.cncf.rule.RuleId("price"),
          table,
          Vector.empty
        ))
      )

      When("the RuleEngine receives no bound product Fact")
      val result = _success(RuleEngine.builtin.evaluate(
        RuleEvaluationRequest(ruleset, complete, WorkingMemory.empty)
      ))

      Then("evaluation has no selected row and malformed bindings fail structurally before evaluation")
      result.decisionTables.map(_.selectedRowId) shouldBe Vector(None)
      result.decisionTables.map(_.output) shouldBe Vector(Record.empty)
      _is_failure(incomplete) shouldBe true
    }

    "install built-in RuleEngine and InferenceEngine services through independent sockets" in {
      Given("one built-in provider component and separate consumer sockets")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("rule_engine_sockets")
      val provider = _initialized_component(subsystem, "rule-engine-provider", BuiltinProviderComponent())
      val ruleconsumer = _initialized_component(subsystem, "rule-engine-consumer", RuleConsumerComponent())
      val inferenceconsumer = _initialized_component(subsystem, "inference-engine-consumer", InferenceConsumerComponent())
      val evaluationruleset = _ruleset(Vector(_rule("tax", RuleFamily.Calculation)))
      val inferenceruleset = _ruleset(Vector(_rule("derive", RuleFamily.Derivation)))

      When("the common SPI resolver installs services")
      val result = SpiResolver.resolve(Vector(provider, ruleconsumer, inferenceconsumer))

      Then("each consumer receives the corresponding canonical SPI contract")
      result shouldBe a[Consequence.Success[_]]
      ruleconsumer.isSpiInstalled shouldBe true
      inferenceconsumer.isSpiInstalled shouldBe true
      ruleconsumer.ruleEngine.evaluate(RuleEvaluationRequest(evaluationruleset, _program(evaluationruleset), WorkingMemory.empty)) shouldBe a[Consequence.Success[_]]
      inferenceconsumer.inferenceEngine.infer(RuleInferenceRequest(inferenceruleset, _program(inferenceruleset), WorkingMemory.empty)) shouldBe a[Consequence.Success[_]]
    }

    "allow an alternate provider to replace the built-in engine without changing the request model" in {
      Given("a consumer and an alternate provider with the same RuleEngine contract")
      given ExecutionContext = ExecutionContext.create()
      val subsystem = TestComponentFactory.emptySubsystem("rule_engine_alternate")
      val provider = _initialized_component(subsystem, "alternate-rule-engine-provider", AlternateProviderComponent())
      val consumer = _initialized_component(subsystem, "alternate-rule-engine-consumer", RuleConsumerComponent())
      val ruleset = _ruleset(Vector(_rule("tax", RuleFamily.Calculation)))
      val program = _program(
        ruleset,
        calculations = Vector(
          org.goldenport.cncf.rule.RuleCalculation(
            org.goldenport.cncf.rule.RuleId("tax"),
            "tax",
            _literal(BigDecimal(100))
          )
        )
      )

      When("the SPI resolver uses the alternate provider")
      val result = SpiResolver.resolve(Vector(provider, consumer))
      val evaluation = _success(consumer.ruleEngine.evaluate(RuleEvaluationRequest(ruleset, program, WorkingMemory.empty)))

      Then("provider substitution preserves the CNCF result contract")
      result shouldBe a[Consequence.Success[_]]
      provider.engine.invocations shouldBe 1
      evaluation.values.getDecimal("tax") shouldBe Some(BigDecimal(100))
    }

    "perform derivation-only inference without evaluating rejecting constraints" in {
      Given("a program containing both a rejecting constraint and an eligible derivation")
      given ExecutionContext = ExecutionContext.create()
      val ruleset = _ruleset(
        Vector(
          _rule("constraint", RuleFamily.Constraint),
          _rule("derive", RuleFamily.Derivation)
        )
      )
      val program = _program(
        ruleset,
        constraints = Vector(
          RuleConstraint(
            org.goldenport.cncf.rule.RuleId("constraint"),
            RuleExpression.Equal(_fact_value("enabled"), _literal(true)),
            "enabled",
            "Feature must be enabled",
            RuleConstraintMode.Reject
          )
        ),
        derivations = Vector(
          RuleDerivation(
            org.goldenport.cncf.rule.RuleId("derive"),
            FactId("eligible"),
            FactName("eligible"),
            RuleExpression.Equal(_fact_value("enabled"), _literal(false)),
            _literal(true)
          )
        )
      )

      When("the built-in InferenceEngine receives the same program")
      val evaluation = RuleEngine.builtin.evaluate(
        RuleEvaluationRequest(ruleset, program, _memory(_fact("enabled", false)))
      )
      val result = _success(
        InferenceEngine.builtin.infer(
          RuleInferenceRequest(ruleset, program, _memory(_fact("enabled", false)))
        )
      )

      Then("it returns only forward-derived facts and does not apply constraint rejection")
      _is_failure(evaluation) shouldBe true
      result.derivedFacts.map(_.id.value) shouldBe Vector("eligible")
      result.workingMemory.facts.map(_.id.value) shouldBe Vector("eligible", "enabled")
    }

    "trace consumer-side RuleEngine evaluation without recording WorkingMemory values" in {
      Given("a RuleEngine socket resolved with a calltree-enabled consumer context")
      given ExecutionContext = ExecutionContext.withFrameworkCallTreeEnabled(ExecutionContext.create(), enabled = true)
      val subsystem = TestComponentFactory.emptySubsystem("rule_engine_trace")
      val provider = _initialized_component(subsystem, "trace-rule-engine-provider", BuiltinProviderComponent())
      val consumer = _initialized_component(subsystem, "trace-rule-engine-consumer", RuleConsumerComponent())
      val ruleset = _ruleset(Vector(_rule("tax", RuleFamily.Calculation)))
      val program = _program(
        ruleset,
        calculations = Vector(
          org.goldenport.cncf.rule.RuleCalculation(
            org.goldenport.cncf.rule.RuleId("tax"),
            "tax",
            _literal(BigDecimal(100))
          )
        )
      )
      val request = RuleEvaluationRequest(ruleset, program, _memory(_fact("subtotal", "confidential-subtotal")))
      val before = RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total

      When("the consumer invokes the installed RuleEngine")
      SpiResolver.resolve(Vector(provider, consumer)) shouldBe a[Consequence.Success[_]]
      val result = _success(consumer.ruleEngine.evaluate(request))
      val calltree = summon[ExecutionContext].observability.callTreeContext.build().map(_.toRecord.print).getOrElse("")

      Then("the caller trace and SPI metric expose structural evaluation metadata only")
      result.values.getDecimal("tax") shouldBe Some(BigDecimal(100))
      calltree should include ("spi:rule-engine.evaluate")
      calltree should include ("outcome=success")
      calltree should include ("rule_set=domain")
      calltree should not include "confidential-subtotal"
      RuntimeDashboardMetrics.spiInvocationSnapshot.summary.cumulative.total should be > before
    }
  }

  private def _ruleset(rules: Vector[Rule]): RuleSet =
    _success(RuleSet.createC(RuleSetIdentity(RuleSetId("domain"), RuleSetVersion("1")), rules))

  private def _program(
    ruleset: RuleSet,
    calculations: Vector[org.goldenport.cncf.rule.RuleCalculation] = Vector.empty,
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
    Rule(org.goldenport.cncf.rule.RuleId(id), family)

  private def _memory(facts: Fact*): WorkingMemory =
    _success(WorkingMemory.fromFactsC(facts))

  private def _fact(id: String, value: Any): Fact =
    Fact(FactId(id), FactName(id), RuleFactValue.scalar(value))

  private def _fact_value(id: String): RuleExpression =
    RuleExpression.FactValue(FactId(id))

  private def _literal(value: Any): RuleExpression =
    RuleExpression.Literal(RuleFactValue.scalar(value))

  private def _table(
    id: String,
    column: String,
    rows: Vector[DecisionTableRow]
  ): DecisionTable =
    _success(DecisionTable.createC(DecisionTableId(id), Vector(DecisionColumn(column)), rows))

  private def _decisionrow(
    id: String,
    column: String,
    expected: Any,
    output: org.goldenport.record.Record
  ): DecisionTableRow =
    DecisionTableRow(
      DecisionTableRowId(id),
      RulePriority.Default,
      Map(column -> DecisionCondition.Equals(RuleFactValue.scalar(expected))),
      output
    )

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

  private def _initialized_component[A <: Component](
    subsystem: Subsystem,
    name: String,
    component: A
  ): A = {
    val componentid = TestComponentFactory.componentId(name)
    component.initialize(ComponentInit(
      subsystem = subsystem,
      core = Component.Core.create(
        name = componentid.name,
        componentid = componentid,
        instanceid = ComponentInstanceId.default(componentid),
        protocol = Protocol.empty
      ),
      origin = ComponentOrigin.Builtin
    ))
    component
  }

  private final case class BuiltinProviderComponent() extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(BuiltinRuleEngineProvider(), BuiltinInferenceEngineProvider())
  }

  private final case class RuleConsumerComponent() extends Component with RuleEngineSocket

  private final case class InferenceConsumerComponent() extends Component with InferenceEngineSocket

  private final case class AlternateProviderComponent(
    engine: AlternateRuleEngine = AlternateRuleEngine()
  ) extends Component with SpiProviderComponent {
    def spiProviders: Vector[SpiProvider[?]] =
      Vector(AlternateRuleEngineProvider(engine))
  }

  private final case class AlternateRuleEngine(
    private var _invocations: Int = 0
  ) extends RuleEngine {
    def invocations: Int = _invocations

    def evaluate(request: RuleEvaluationRequest)(using ExecutionContext): Consequence[org.goldenport.cncf.rule.RuleEvaluationResult] = {
      _invocations += 1
      RuleEngine.builtin.evaluate(request)
    }
  }

  private final case class AlternateRuleEngineProvider(engine: AlternateRuleEngine) extends SpiProvider[RuleEngine] {
    def supports(
      contract: org.goldenport.cncf.spi.SpiContract[RuleEngine],
      selection: SpiSelection
    )(using ExecutionContext): Boolean =
      contract.name == RuleEngine.contractname && contract.runtimeClass == classOf[RuleEngine]

    def provide(
      contract: org.goldenport.cncf.spi.SpiContract[RuleEngine],
      selection: SpiSelection
    )(using ExecutionContext): Consequence[RuleEngine] =
      Consequence.success(engine)
  }
}
