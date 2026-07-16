package org.goldenport.cncf.spi.rule.engine

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.rule.{BuiltinRuleEvaluator, RuleEvaluationResult, RuleInferenceResult, RuleProgram, RuleSet, WorkingMemory}
import org.goldenport.cncf.spi.{ComponentSelector, SpiContract, SpiProvider, SpiSelection, SpiSocket, SpiTraceMetadata, SpiTraceSupport, StandardSpiSocketSet}

/*
 * Provider-neutral rule and inference engine SPI contracts.
 *
 * Providers may replace the implementation, but they receive and return only
 * CNCF-owned immutable rule values. They never receive runtime action handles.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuleEvaluationRequest(
  ruleSet: RuleSet,
  program: RuleProgram,
  workingMemory: WorkingMemory
)

final case class RuleInferenceRequest(
  ruleSet: RuleSet,
  program: RuleProgram,
  workingMemory: WorkingMemory
)

trait RuleEngine {
  def evaluate(request: RuleEvaluationRequest)(using ExecutionContext): Consequence[RuleEvaluationResult]
}

object RuleEngine {
  val contractname = "rule-engine"

  def traced(
    underlying: RuleEngine,
    metadata: SpiTraceMetadata
  ): RuleEngine =
    _TracedRuleEngine(underlying, metadata)

  val builtin: RuleEngine = new RuleEngine {
    def evaluate(request: RuleEvaluationRequest)(using ExecutionContext): Consequence[RuleEvaluationResult] =
      BuiltinRuleEvaluator.evaluateC(request.ruleSet, request.program, request.workingMemory)
  }

  private final case class _TracedRuleEngine(
    underlying: RuleEngine,
    base: SpiTraceMetadata
  ) extends RuleEngine {
    def evaluate(request: RuleEvaluationRequest)(using ExecutionContext): Consequence[RuleEvaluationResult] =
      SpiTraceSupport.trace(base.withOperation("evaluate"), _evaluation_attributes)(underlying.evaluate(request))
  }

  private def _evaluation_attributes(result: RuleEvaluationResult): Map[String, String] =
    Map(
      "rule_set" -> result.ruleSet.id.value,
      "rule_version" -> result.ruleSet.version.value,
      "agenda_count" -> result.agenda.entries.size.toString,
      "explanation_count" -> result.explanations.size.toString,
      "decision_table_count" -> result.decisionTables.size.toString,
      "planned_action_count" -> result.actionPlans.size.toString
    )
}

trait InferenceEngine {
  def infer(request: RuleInferenceRequest)(using ExecutionContext): Consequence[RuleInferenceResult]
}

object InferenceEngine {
  val contractname = "inference-engine"

  def traced(
    underlying: InferenceEngine,
    metadata: SpiTraceMetadata
  ): InferenceEngine =
    _TracedInferenceEngine(underlying, metadata)

  val builtin: InferenceEngine = new InferenceEngine {
    def infer(request: RuleInferenceRequest)(using ExecutionContext): Consequence[RuleInferenceResult] =
      BuiltinRuleEvaluator.inferC(request.ruleSet, request.program, request.workingMemory)
  }

  private final case class _TracedInferenceEngine(
    underlying: InferenceEngine,
    base: SpiTraceMetadata
  ) extends InferenceEngine {
    def infer(request: RuleInferenceRequest)(using ExecutionContext): Consequence[RuleInferenceResult] =
      SpiTraceSupport.trace(base.withOperation("infer"), _inference_attributes)(underlying.infer(request))
  }

  private def _inference_attributes(result: RuleInferenceResult): Map[String, String] =
    Map(
      "rule_set" -> result.ruleSet.id.value,
      "rule_version" -> result.ruleSet.version.value,
      "derived_fact_count" -> result.derivedFacts.size.toString,
      "explanation_count" -> result.explanations.size.toString
    )
}

trait RuleEngineSocket extends SpiSocket[RuleEngine] {
  private var _rule_engine: Option[RuleEngine] = None

  def ruleEngine: RuleEngine =
    _rule_engine.getOrElse(
      throw new IllegalStateException("RuleEngine SPI is not installed.")
    )

  def withRuleEngine(spi: RuleEngine): this.type = {
    _rule_engine = Some(spi)
    this
  }

  override def spiContract: SpiContract[RuleEngine] =
    SpiContract(RuleEngine.contractname, classOf[RuleEngine])

  override def spiSelection: SpiSelection =
    SpiSelection()

  override def isSpiInstalled: Boolean =
    _rule_engine.nonEmpty

  override def installSpi(spi: RuleEngine): Unit =
    withRuleEngine(spi)
}

trait RuleEngineSocketSet extends StandardSpiSocketSet[RuleEngine] {
  def ruleEngine(
    selector: ComponentSelector = ComponentSelector()
  )(using ExecutionContext): Consequence[RuleEngine] =
    resolve(selector)

  override def spiContract: SpiContract[RuleEngine] =
    SpiContract(RuleEngine.contractname, classOf[RuleEngine])
}

trait InferenceEngineSocket extends SpiSocket[InferenceEngine] {
  private var _inference_engine: Option[InferenceEngine] = None

  def inferenceEngine: InferenceEngine =
    _inference_engine.getOrElse(
      throw new IllegalStateException("InferenceEngine SPI is not installed.")
    )

  def withInferenceEngine(spi: InferenceEngine): this.type = {
    _inference_engine = Some(spi)
    this
  }

  override def spiContract: SpiContract[InferenceEngine] =
    SpiContract(InferenceEngine.contractname, classOf[InferenceEngine])

  override def spiSelection: SpiSelection =
    SpiSelection()

  override def isSpiInstalled: Boolean =
    _inference_engine.nonEmpty

  override def installSpi(spi: InferenceEngine): Unit =
    withInferenceEngine(spi)
}

trait InferenceEngineSocketSet extends StandardSpiSocketSet[InferenceEngine] {
  def inferenceEngine(
    selector: ComponentSelector = ComponentSelector()
  )(using ExecutionContext): Consequence[InferenceEngine] =
    resolve(selector)

  override def spiContract: SpiContract[InferenceEngine] =
    SpiContract(InferenceEngine.contractname, classOf[InferenceEngine])
}

final case class BuiltinRuleEngineProvider() extends SpiProvider[RuleEngine] {
  def supports(
    contract: SpiContract[RuleEngine],
    selection: SpiSelection
  )(using ExecutionContext): Boolean =
    contract.name == RuleEngine.contractname && contract.runtimeClass == classOf[RuleEngine]

  def provide(
    contract: SpiContract[RuleEngine],
    selection: SpiSelection
  )(using ExecutionContext): Consequence[RuleEngine] =
    Consequence.success(RuleEngine.builtin)
}

final case class BuiltinInferenceEngineProvider() extends SpiProvider[InferenceEngine] {
  def supports(
    contract: SpiContract[InferenceEngine],
    selection: SpiSelection
  )(using ExecutionContext): Boolean =
    contract.name == InferenceEngine.contractname && contract.runtimeClass == classOf[InferenceEngine]

  def provide(
    contract: SpiContract[InferenceEngine],
    selection: SpiSelection
  )(using ExecutionContext): Consequence[InferenceEngine] =
    Consequence.success(InferenceEngine.builtin)
}
