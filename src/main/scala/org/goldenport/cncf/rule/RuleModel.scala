package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuleSetId(value: String) {
  def print: String = value
}

final case class RuleSetVersion(value: String) {
  def print: String = value
}

final case class RuleSetIdentity(
  id: RuleSetId,
  version: RuleSetVersion
) {
  def print: String = s"${id.print}@${version.print}"
}

final case class RuleId(value: String) {
  def print: String = value
}

final case class RuleActivationId(value: String) {
  def print: String = value
}

final case class FactId(value: String) {
  def print: String = value
}

final case class FactName(value: String) {
  def print: String = value
}

enum RuleFamily {
  case Constraint
  case Calculation
  case Derivation
  case DecisionTable
  case Production

  def token: String = this match {
    case Constraint => "constraint"
    case Calculation => "calculation"
    case Derivation => "derivation"
    case DecisionTable => "decision-table"
    case Production => "production"
  }
}

object RuleFamily {
  def parseC(value: String): Consequence[RuleFamily] =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "constraint" => Consequence.success(RuleFamily.Constraint)
      case "calculation" => Consequence.success(RuleFamily.Calculation)
      case "derivation" => Consequence.success(RuleFamily.Derivation)
      case "decision-table" | "decision_table" | "decisiontable" => Consequence.success(RuleFamily.DecisionTable)
      case "production" => Consequence.success(RuleFamily.Production)
      case other => Consequence.argumentExpectedActualMismatch("Rule.family", "constraint|calculation|derivation|decision-table|production", other)
    }
}

final case class RulePriority(value: Int) {
  def print: String = value.toString
}

object RulePriority {
  val Default: RulePriority = RulePriority(0)
}

sealed abstract class RuleFactValue {
  def value: Any
}

object RuleFactValue {
  final case class Scalar(value: Any) extends RuleFactValue
  final case class Structured(value: Record) extends RuleFactValue

  def scalar(value: Any): RuleFactValue = Scalar(value)
  def structured(value: Record): RuleFactValue = Structured(value)
}

enum RuleFactOrigin {
  case Asserted
  case Imported
  case Calculated
  case Derived

  def token: String = this match {
    case Asserted => "asserted"
    case Imported => "imported"
    case Calculated => "calculated"
    case Derived => "derived"
  }
}

final case class RuleFactProvenance(
  origin: RuleFactOrigin = RuleFactOrigin.Asserted,
  sourceRuleId: Option[RuleId] = None,
  sourceFactIds: Vector[FactId] = Vector.empty
) {
  def normalized: RuleFactProvenance =
    copy(sourceFactIds = sourceFactIds.sortBy(_.value))
}

final case class Fact(
  id: FactId,
  name: FactName,
  value: RuleFactValue,
  typeName: Option[String] = None,
  attributes: Record = Record.empty,
  provenance: RuleFactProvenance = RuleFactProvenance()
) {
  def normalized: Fact = copy(provenance = provenance.normalized)
}

final case class RuleDefinition(
  conditions: Record = Record.empty,
  outputs: Record = Record.empty
)

final case class RuleExplanationTemplate(
  summary: String = "",
  attributes: Record = Record.empty
)

final case class Rule(
  id: RuleId,
  family: RuleFamily,
  priority: RulePriority = RulePriority.Default,
  definition: RuleDefinition = RuleDefinition(),
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
)

final case class RuleSet private (
  identity: RuleSetIdentity,
  rules: Vector[Rule],
  inputFacts: Vector[FactName],
  outputFacts: Vector[FactName],
  metadata: Record
) {
  def orderedRules: Vector[Rule] = rules
}

object RuleSet {
  def createC(
    identity: RuleSetIdentity,
    rules: Vector[Rule],
    inputfacts: Vector[FactName] = Vector.empty,
    outputfacts: Vector[FactName] = Vector.empty,
    metadata: Record = Record.empty
  ): Consequence[RuleSet] = {
    val ruleids = rules.map(_.id.value)
    val inputnames = inputfacts.map(_.value)
    val outputnames = outputfacts.map(_.value)
    if (identity.id.value.trim.isEmpty)
      Consequence.argumentInvalid("RuleSet identity must have a non-empty id")
    else if (identity.version.value.trim.isEmpty)
      Consequence.argumentInvalid("RuleSet identity must have a non-empty version")
    else if (rules.isEmpty)
      Consequence.argumentInvalid("RuleSet must contain at least one Rule")
    else if (ruleids.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("RuleSet rules must have non-empty ids")
    else if (_has_duplicates(ruleids))
      Consequence.argumentInvalid("RuleSet rule ids must be unique")
    else if (inputnames.exists(_.trim.isEmpty) || outputnames.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("RuleSet fact vocabulary names must be non-empty")
    else if (_has_duplicates(inputnames) || _has_duplicates(outputnames))
      Consequence.argumentInvalid("RuleSet fact vocabulary names must be unique")
    else
      Consequence.success(
        new RuleSet(
          identity,
          _order_rules(rules),
          inputfacts.sortBy(_.value),
          outputfacts.sortBy(_.value),
          metadata
        )
      )
  }

  private def _order_rules(rules: Vector[Rule]): Vector[Rule] =
    rules.sortWith { (left, right) =>
      left.priority.value > right.priority.value ||
        (left.priority.value == right.priority.value && left.id.value < right.id.value)
    }

  private def _has_duplicates(values: Vector[String]): Boolean =
    values.distinct.size != values.size
}

final case class WorkingMemory private (facts: Vector[Fact]) {
  def find(id: FactId): Option[Fact] = facts.find(_.id == id)

  def putC(fact: Fact): Consequence[WorkingMemory] =
    WorkingMemory.fromFactsC(facts.filterNot(_.id == fact.id) :+ fact)

  def remove(id: FactId): WorkingMemory =
    new WorkingMemory(facts.filterNot(_.id == id))
}

object WorkingMemory {
  val empty: WorkingMemory = new WorkingMemory(Vector.empty)

  def apply(): WorkingMemory = empty

  def fromFactsC(facts: Iterable[Fact]): Consequence[WorkingMemory] = {
    val values = facts.toVector
    val ids = values.map(_.id.value)
    if (ids.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("WorkingMemory facts must have non-empty ids")
    else if (values.exists(_.name.value.trim.isEmpty))
      Consequence.argumentInvalid("WorkingMemory facts must have non-empty names")
    else if (ids.distinct.size != ids.size)
      Consequence.argumentInvalid("WorkingMemory fact ids must be unique")
    else
      Consequence.success(new WorkingMemory(values.map(_.normalized).sortBy(_.id.value)))
  }
}

enum RuleExplanationKind {
  case Matched
  case NotMatched
  case Selected
  case Calculated
  case Derived
  case ConstraintViolation
  case Planned

  def token: String = this match {
    case Matched => "matched"
    case NotMatched => "not-matched"
    case Selected => "selected"
    case Calculated => "calculated"
    case Derived => "derived"
    case ConstraintViolation => "constraint-violation"
    case Planned => "planned"
  }
}

final case class RuleExplanation(
  ruleId: RuleId,
  kind: RuleExplanationKind,
  summary: String,
  sourceFactIds: Vector[FactId] = Vector.empty,
  resultFactIds: Vector[FactId] = Vector.empty,
  attributes: Record = Record.empty
) {
  def normalized: RuleExplanation =
    copy(
      sourceFactIds = sourceFactIds.sortBy(_.value),
      resultFactIds = resultFactIds.sortBy(_.value)
    )
}

final case class RuleDecisionTableResult(
  ruleId: RuleId,
  tableId: DecisionTableId,
  selectedRowId: Option[DecisionTableRowId],
  output: Record,
  sourceFactIds: Vector[FactId] = Vector.empty,
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def normalized: RuleDecisionTableResult =
    copy(sourceFactIds = sourceFactIds.distinct.sortBy(_.value))
}

final case class RuleActivation(
  id: RuleActivationId,
  ruleId: RuleId,
  priority: RulePriority,
  matchedFactIds: Vector[FactId] = Vector.empty,
  explanation: Option[RuleExplanation] = None
) {
  def normalized: RuleActivation =
    copy(
      matchedFactIds = matchedFactIds.sortBy(_.value),
      explanation = explanation.map(_.normalized)
    )
}

final case class Agenda private (entries: Vector[RuleActivation]) {
  def next: Option[RuleActivation] = entries.headOption
}

object Agenda {
  val empty: Agenda = new Agenda(Vector.empty)

  def apply(): Agenda = empty

  def fromActivationsC(activations: Iterable[RuleActivation]): Consequence[Agenda] = {
    val values = activations.toVector
    val ids = values.map(_.id.value)
    if (ids.exists(_.trim.isEmpty))
      Consequence.argumentInvalid("Agenda activations must have non-empty ids")
    else if (values.exists(_.ruleId.value.trim.isEmpty))
      Consequence.argumentInvalid("Agenda activations must have non-empty rule ids")
    else if (ids.distinct.size != ids.size)
      Consequence.argumentInvalid("Agenda activation ids must be unique")
    else if (values.exists(value => value.explanation.exists(_.ruleId != value.ruleId)))
      Consequence.argumentInvalid("Agenda activation explanations must identify the activated rule")
    else
      Consequence.success(new Agenda(_order_activations(values.map(_.normalized))))
  }

  private def _order_activations(values: Vector[RuleActivation]): Vector[RuleActivation] =
    values.sortWith { (left, right) =>
      left.priority.value > right.priority.value ||
        (left.priority.value == right.priority.value &&
          (left.ruleId.value < right.ruleId.value ||
            (left.ruleId.value == right.ruleId.value && left.id.value < right.id.value)))
    }
}

final case class RuleEvaluationResult(
  ruleSet: RuleSetIdentity,
  workingMemory: WorkingMemory,
  agenda: Agenda = Agenda(),
  explanations: Vector[RuleExplanation] = Vector.empty,
  values: Record = Record.empty,
  calculations: Vector[RuleCalculationResult] = Vector.empty,
  decisionTables: Vector[RuleDecisionTableResult] = Vector.empty,
  constraintViolations: Vector[RuleConstraintViolation] = Vector.empty,
  derivedFacts: Vector[Fact] = Vector.empty,
  actionPlans: Vector[RuleActionPlan] = Vector.empty
) {
  def normalized: RuleEvaluationResult = {
    val normalizedexplanations = explanations.map(_.normalized).sortBy { value =>
      (
        value.ruleId.value,
        value.kind.token,
        value.summary,
        value.sourceFactIds.map(id => s"${id.value.length}:${id.value}").mkString,
        value.resultFactIds.map(id => s"${id.value.length}:${id.value}").mkString
      )
    }
    copy(
      explanations = normalizedexplanations,
      calculations = calculations.map(_.normalized).sortBy(value => (value.ruleId.value, value.name)),
      decisionTables = decisionTables.map(_.normalized).sortBy(value => (value.ruleId.value, value.tableId.value)),
      constraintViolations = constraintViolations.sortBy(value => (value.ruleId.value, value.code)),
      derivedFacts = derivedFacts.map(_.normalized).sortBy(_.id.value),
      actionPlans = actionPlans.sortBy(plan => (plan.ruleId.value, plan.id.value))
    )
  }
}
