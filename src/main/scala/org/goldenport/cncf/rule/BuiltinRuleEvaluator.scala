package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.record.Record

/*
 * The built-in evaluator is intentionally an engine-neutral, restricted AST.
 * It evaluates supplied facts only and never invokes runtime actions or stores.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
sealed abstract class RuleExpression {
  def factIds: Vector[FactId]
}

object RuleExpression {
  final case class Literal(value: RuleFactValue) extends RuleExpression {
    val factIds: Vector[FactId] = Vector.empty
  }

  final case class FactValue(id: FactId) extends RuleExpression {
    val factIds: Vector[FactId] = Vector(id)
  }

  final case class Add(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class Subtract(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class Multiply(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class Equal(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class GreaterThanOrEqual(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class GreaterThan(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class LessThanOrEqual(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class LessThan(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class And(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class Or(left: RuleExpression, right: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = _fact_ids(left, right)
  }

  final case class Not(expression: RuleExpression) extends RuleExpression {
    def factIds: Vector[FactId] = expression.factIds.distinct.sortBy(_.value)
  }

  private def _fact_ids(left: RuleExpression, right: RuleExpression): Vector[FactId] =
    (left.factIds ++ right.factIds).distinct.sortBy(_.value)
}

final case class RuleCalculation(
  ruleId: RuleId,
  name: String,
  expression: RuleExpression,
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
)

final case class RuleCalculationResult(
  ruleId: RuleId,
  name: String,
  value: RuleFactValue,
  sourceFactIds: Vector[FactId] = Vector.empty,
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def normalized: RuleCalculationResult =
    copy(sourceFactIds = sourceFactIds.distinct.sortBy(_.value))
}

enum RuleConstraintMode {
  case Report
  case Reject
}

final case class RuleConstraint(
  ruleId: RuleId,
  predicate: RuleExpression,
  code: String,
  message: String,
  mode: RuleConstraintMode = RuleConstraintMode.Report,
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
)

final case class RuleConstraintViolation(
  ruleId: RuleId,
  code: String,
  message: String,
  sourceFactIds: Vector[FactId],
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def normalized: RuleConstraintViolation =
    copy(sourceFactIds = sourceFactIds.distinct.sortBy(_.value))
}

final case class RuleDerivation(
  ruleId: RuleId,
  factId: FactId,
  factName: FactName,
  when: RuleExpression,
  expression: RuleExpression,
  typeName: Option[String] = None,
  attributes: Record = Record.empty,
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def sourceFactIds: Vector[FactId] =
    (when.factIds ++ expression.factIds).distinct.sortBy(_.value)
}

final case class RuleDecisionTableBinding(
  column: String,
  factId: FactId
)

final case class RuleDecisionTable(
  ruleId: RuleId,
  table: DecisionTable,
  bindings: Vector[RuleDecisionTableBinding],
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def sourceFactIds: Vector[FactId] =
    bindings.map(_.factId).distinct.sortBy(_.value)
}

final case class RuleProgram private (
  ruleSet: RuleSetIdentity,
  calculations: Vector[RuleCalculation],
  decisionTables: Vector[RuleDecisionTable],
  constraints: Vector[RuleConstraint],
  derivations: Vector[RuleDerivation],
  productions: Vector[RuleProduction]
)

final case class RuleInferenceResult(
  ruleSet: RuleSetIdentity,
  workingMemory: WorkingMemory,
  derivedFacts: Vector[Fact],
  explanations: Vector[RuleExplanation]
) {
  def normalized: RuleInferenceResult =
    copy(
      derivedFacts = derivedFacts.map(_.normalized).sortBy(_.id.value),
      explanations = RuleEvaluationResult(ruleSet, workingMemory, explanations = explanations).normalized.explanations
    )
}

object RuleProgram {
  def createC(
    ruleset: RuleSet,
    calculations: Vector[RuleCalculation] = Vector.empty,
    decisiontables: Vector[RuleDecisionTable] = Vector.empty,
    constraints: Vector[RuleConstraint] = Vector.empty,
    derivations: Vector[RuleDerivation] = Vector.empty,
    productions: Vector[RuleProduction] = Vector.empty
  ): Consequence[RuleProgram] = {
    val allruleids = calculations.map(_.ruleId) ++ decisiontables.map(_.ruleId) ++ constraints.map(_.ruleId) ++ derivations.map(_.ruleId) ++ productions.map(_.ruleId)
    val calculationnames = calculations.map(_.name)
    val constraintcodes = constraints.map(_.code)
    val derivationfactids = derivations.map(_.factId.value)
    val planids = productions.flatMap(_.plans.map(_.id.value))
    val expressionfactids =
      calculations.flatMap(_.expression.factIds) ++
        decisiontables.flatMap(_.sourceFactIds) ++
        constraints.flatMap(_.predicate.factIds) ++
        derivations.flatMap(derivation => derivation.when.factIds ++ derivation.expression.factIds) ++
        productions.flatMap(_.predicate.factIds)
    val byid = ruleset.rules.map(rule => rule.id -> rule).toMap
    if (_has_duplicates(allruleids.map(_.value)))
      Consequence.argumentInvalid("RuleProgram may define one executable instruction for each Rule")
    else if (calculationnames.exists(_.trim.isEmpty) || _has_duplicates(calculationnames))
      Consequence.argumentInvalid("RuleProgram calculation names must be non-empty and unique")
    else if (decisiontables.exists(_invalid_decision_table))
      Consequence.argumentInvalid("RuleProgram decision-table Rules must bind every declared input column to a non-empty Fact id")
    else if (constraintcodes.exists(_.trim.isEmpty) || _has_duplicates(constraintcodes))
      Consequence.argumentInvalid("RuleProgram constraint codes must be non-empty and unique")
    else if (constraints.exists(_.message.trim.isEmpty))
      Consequence.argumentInvalid("RuleProgram constraint messages must be non-empty")
    else if (derivationfactids.exists(_.trim.isEmpty) || _has_duplicates(derivationfactids))
      Consequence.argumentInvalid("RuleProgram derived Fact ids must be non-empty and unique")
    else if (derivations.exists(_.factName.value.trim.isEmpty))
      Consequence.argumentInvalid("RuleProgram derived Fact names must be non-empty")
    else if (productions.exists(_.plans.isEmpty))
      Consequence.argumentInvalid("RuleProgram production Rules must contain at least one action plan")
    else if (planids.exists(_.trim.isEmpty) || _has_duplicates(planids))
      Consequence.argumentInvalid("RuleProgram action plan ids must be non-empty and unique")
    else if (productions.exists(production => production.plans.exists(_.ruleId != production.ruleId)))
      Consequence.argumentInvalid("RuleProgram action plan rule ids must match their production Rule")
    else if (productions.exists(production => production.plans.exists(_invalid_action_plan)))
      Consequence.argumentInvalid("RuleProgram action plans must have valid target metadata")
    else if (expressionfactids.exists(_.value.trim.isEmpty))
      Consequence.argumentInvalid("RuleProgram expression Fact ids must be non-empty")
    else
      _validate_families_c(byid, calculations.map(_.ruleId -> RuleFamily.Calculation) ++ decisiontables.map(_.ruleId -> RuleFamily.DecisionTable) ++ constraints.map(_.ruleId -> RuleFamily.Constraint) ++ derivations.map(_.ruleId -> RuleFamily.Derivation) ++ productions.map(_.ruleId -> RuleFamily.Production)).map { _ =>
        new RuleProgram(
          ruleset.identity,
          _order_by_rule(ruleset, calculations)(_.ruleId),
          _order_by_rule(ruleset, decisiontables)(_.ruleId),
          _order_by_rule(ruleset, constraints)(_.ruleId),
          _order_by_rule(ruleset, derivations)(_.ruleId),
          _order_by_rule(ruleset, productions)(_.ruleId).map(_.normalized)
        )
      }
  }

  private def _invalid_action_plan(plan: RuleActionPlan): Boolean =
    plan match {
      case RuleActionPlan.Operation(_, _, selector, _) => selector.trim.isEmpty
      case RuleActionPlan.Event(_, _, name, kind, _, attributes, _) =>
        name.trim.isEmpty || kind.trim.isEmpty || attributes.exists { case (key, value) => key.trim.isEmpty || value == null }
      case RuleActionPlan.Job(_, _, selector, _) => selector.trim.isEmpty
      case RuleActionPlan.Recommendation(_, _, summary, _) => summary.trim.isEmpty
    }

  private def _invalid_decision_table(value: RuleDecisionTable): Boolean = {
    val columns = value.table.inputColumns.map(_.name).toSet
    val bindings = value.bindings.map(_.column)
    value.bindings.exists(binding => binding.column.trim.isEmpty || binding.factId.value.trim.isEmpty) ||
      bindings.distinct.size != bindings.size ||
      bindings.toSet != columns
  }

  private def _validate_families_c(
    rules: Map[RuleId, Rule],
    references: Vector[(RuleId, RuleFamily)]
  ): Consequence[Unit] =
    references.foldLeft(Consequence.success(())) { case (z, (id, family)) =>
      z.flatMap { _ =>
        rules.get(id) match {
          case Some(rule) if rule.family == family => Consequence.success(())
          case Some(_) => Consequence.argumentExpectedActualMismatch("RuleProgram.rule.family", family.token, rules(id).family.token)
          case None => Consequence.argumentExpectedActualMismatch("RuleProgram.ruleId", "RuleSet Rule id", id.value)
        }
      }
    }

  private def _order_by_rule[A](ruleset: RuleSet, values: Vector[A])(f: A => RuleId): Vector[A] = {
    val priority = ruleset.rules.map(rule => rule.id -> rule.priority.value).toMap
    values.sortWith { (left, right) =>
      priority(f(left)) > priority(f(right)) ||
        (priority(f(left)) == priority(f(right)) && f(left).value < f(right).value)
    }
  }

  private def _has_duplicates(values: Vector[String]): Boolean =
    values.distinct.size != values.size
}

object BuiltinRuleEvaluator {
  def evaluateC(
    ruleset: RuleSet,
    program: RuleProgram,
    memory: WorkingMemory
  ): Consequence[RuleEvaluationResult] =
    _validate_program_c(ruleset, program).flatMap { _ =>
      for {
        violations <- _evaluate_constraints_c(program.constraints, memory)
        calculations <- _evaluate_calculations_c(program.calculations, memory)
        decisions <- _evaluate_decision_tables_c(program.decisionTables, memory)
        derived <- _derive_c(program.derivations, memory)
        plans <- _evaluate_productions_c(program.productions, derived._1)
      } yield {
        val values = calculations.foldLeft(Record.empty) { (z, calculation) =>
          z.upsertSingle(calculation.name, calculation.value.value)
        }
        val explanations =
          calculations.map(_calculation_explanation) ++
            decisions.map(_decision_table_explanation) ++
            violations.map(_constraint_explanation) ++
            derived._2.map { derivation =>
              RuleExplanation(
                derivation.ruleId,
                RuleExplanationKind.Derived,
                _summary(derivation.explanation, s"Derived ${derivation.factName.value}"),
                derivation.sourceFactIds,
                Vector(derivation.factId)
              )
            } ++
            plans._2
        RuleEvaluationResult(
          ruleset.identity,
          derived._1,
          explanations = explanations,
            values = values,
            calculations = calculations,
            decisionTables = decisions,
            constraintViolations = violations,
            derivedFacts = derived._3,
            actionPlans = plans._1
        ).normalized
      }
    }

  private def _evaluate_constraints_c(
    constraints: Vector[RuleConstraint],
    memory: WorkingMemory
  ): Consequence[Vector[RuleConstraintViolation]] =
    constraints.foldLeft(Consequence.success(Vector.empty[RuleConstraintViolation])) { (z, constraint) =>
      z.flatMap { values =>
        _boolean_c(constraint.predicate, memory).flatMap {
          case true => Consequence.success(values)
          case false =>
            val violation = RuleConstraintViolation(
              constraint.ruleId,
              constraint.code,
              constraint.message,
              constraint.predicate.factIds,
              constraint.explanation
            ).normalized
            constraint.mode match {
              case RuleConstraintMode.Report => Consequence.success(values :+ violation)
              case RuleConstraintMode.Reject =>
                Consequence.argumentPolicyViolation("RuleConstraint", constraint.code, "predicate=true", "predicate=false")
            }
        }
      }
    }

  private def _evaluate_calculations_c(
    calculations: Vector[RuleCalculation],
    memory: WorkingMemory
  ): Consequence[Vector[RuleCalculationResult]] =
    calculations.foldLeft(Consequence.success(Vector.empty[RuleCalculationResult])) { (z, calculation) =>
      z.flatMap { values =>
        _evaluate_expression_c(calculation.expression, memory).map { value =>
          values :+ RuleCalculationResult(
            calculation.ruleId,
            calculation.name,
            value,
            calculation.expression.factIds,
            calculation.explanation
        ).normalized
      }
    }
    }

  private def _evaluate_decision_tables_c(
    decisions: Vector[RuleDecisionTable],
    memory: WorkingMemory
  ): Consequence[Vector[RuleDecisionTableResult]] =
    decisions.foldLeft(Consequence.success(Vector.empty[RuleDecisionTableResult])) { (z, decision) =>
      z.flatMap { values =>
        _decision_inputs_c(decision, memory).flatMap { inputs =>
          DecisionTableEvaluator.evaluateC(decision.table, inputs).map { evaluation =>
            val selected = evaluation.selected
            values :+ RuleDecisionTableResult(
              decision.ruleId,
              decision.table.id,
              selected.map(_.row.id),
              selected.map(_.row.output).getOrElse(Record.empty),
              decision.sourceFactIds,
              decision.explanation
            ).normalized
          }
        }
      }
    }

  private def _decision_inputs_c(
    decision: RuleDecisionTable,
    memory: WorkingMemory
  ): Consequence[DecisionTableInputs] =
    decision.bindings.foldLeft(Consequence.success(Vector.empty[DecisionTableInput])) { (z, binding) =>
      z.flatMap { values =>
        memory.find(binding.factId) match {
          case Some(fact) => Consequence.success(values :+ DecisionTableInput(binding.column, fact.value))
          case None => Consequence.success(values)
        }
      }
    }.flatMap(DecisionTableInputs.fromInputsC)

  def inferC(
    ruleset: RuleSet,
    program: RuleProgram,
    memory: WorkingMemory
  ): Consequence[RuleInferenceResult] =
    _validate_program_c(ruleset, program).flatMap { _ =>
      _derive_c(program.derivations, memory).map { derived =>
        RuleInferenceResult(
          ruleset.identity,
          derived._1,
          derived._3,
          derived._2.map { derivation =>
            RuleExplanation(
              derivation.ruleId,
              RuleExplanationKind.Derived,
              _summary(derivation.explanation, s"Derived ${derivation.factName.value}"),
              derivation.sourceFactIds,
              Vector(derivation.factId)
            )
          }
        ).normalized
      }
    }

  private def _evaluate_productions_c(
    productions: Vector[RuleProduction],
    memory: WorkingMemory
  ): Consequence[(Vector[RuleActionPlan], Vector[RuleExplanation])] =
    productions.foldLeft(Consequence.success((Vector.empty[RuleActionPlan], Vector.empty[RuleExplanation]))) { (z, production) =>
      z.flatMap { case (plans, explanations) =>
        _derivation_matches_c(production.predicate, memory).map {
          case true =>
            val summary = _summary(production.explanation, s"Planned ${production.plans.size} runtime action(s)")
            val explanation = RuleExplanation(
              production.ruleId,
              RuleExplanationKind.Planned,
              summary,
              production.predicate.factIds
            )
            (plans ++ production.plans, explanations :+ explanation)
          case false =>
            (plans, explanations)
        }
      }
    }

  private def _decision_table_explanation(
    result: RuleDecisionTableResult
  ): RuleExplanation =
    RuleExplanation(
      result.ruleId,
      RuleExplanationKind.Selected,
      _summary(result.explanation, result.selectedRowId.fold(s"No ${result.tableId.value} row selected")(id => s"Selected ${result.tableId.value} row ${id.value}")),
      result.sourceFactIds
    )

  private def _validate_program_c(ruleset: RuleSet, program: RuleProgram): Consequence[Unit] =
    if (program.ruleSet != ruleset.identity)
      Consequence.argumentExpectedActualMismatch("RuleProgram.ruleSet", ruleset.identity.print, program.ruleSet.print)
    else
      Consequence.success(())

  private def _derive_c(
    derivations: Vector[RuleDerivation],
    memory: WorkingMemory
  ): Consequence[(WorkingMemory, Vector[RuleDerivation], Vector[Fact])] = {
    def go(
      current: WorkingMemory,
      fired: Vector[RuleDerivation],
      facts: Vector[Fact],
      remaining: Int
    ): Consequence[(WorkingMemory, Vector[RuleDerivation], Vector[Fact])] =
      if (remaining == 0)
        Consequence.success((current, fired, facts))
      else
        _derive_pass_c(derivations, current).flatMap { case (next, created) =>
          if (created.isEmpty)
            Consequence.success((next, fired, facts))
          else
            go(next, fired ++ created.map(_._1), facts ++ created.map(_._2), remaining - 1)
        }

    go(memory, Vector.empty, Vector.empty, derivations.size + 1)
  }

  private def _derive_pass_c(
    derivations: Vector[RuleDerivation],
    memory: WorkingMemory
  ): Consequence[(WorkingMemory, Vector[(RuleDerivation, Fact)])] =
    derivations.foldLeft(Consequence.success(memory -> Vector.empty[(RuleDerivation, Fact)])) { (z, derivation) =>
      z.flatMap { case (current, created) =>
        _derivation_matches_c(derivation.when, current).flatMap {
          case false => Consequence.success(current -> created)
          case true =>
            _evaluate_expression_c(derivation.expression, current).flatMap { value =>
              val fact = Fact(
                derivation.factId,
                derivation.factName,
                value,
                derivation.typeName,
                derivation.attributes,
                RuleFactProvenance(RuleFactOrigin.Derived, Some(derivation.ruleId), derivation.sourceFactIds)
              ).normalized
              current.find(derivation.factId) match {
                case None => current.putC(fact).map(next => next -> (created :+ (derivation -> fact)))
                case Some(existing) if existing == fact => Consequence.success(current -> created)
                case Some(_) =>
                  Consequence.argumentPolicyViolation(
                    "RuleDerivation.factId",
                    "rule.derivation.no-overwrite",
                    "unbound or equivalent Fact",
                    "conflicting Fact"
                  )
              }
            }
        }
      }
    }

  private def _evaluate_expression_c(
    expression: RuleExpression,
    memory: WorkingMemory
  ): Consequence[RuleFactValue] =
    expression match {
      case RuleExpression.Literal(value) => Consequence.success(value)
      case RuleExpression.FactValue(id) =>
        memory.find(id).map(fact => Consequence.success(fact.value)).getOrElse(
          Consequence.argumentExpectedActualMismatch("RuleExpression.factId", "WorkingMemory Fact id", id.value)
        )
      case RuleExpression.Add(left, right) => _numeric_binary_c(left, right, memory)(_ + _)
      case RuleExpression.Subtract(left, right) => _numeric_binary_c(left, right, memory)(_ - _)
      case RuleExpression.Multiply(left, right) => _numeric_binary_c(left, right, memory)(_ * _)
      case RuleExpression.Equal(left, right) => _binary_c(left, right, memory)((x, y) => Consequence.success(x == y))
      case RuleExpression.GreaterThanOrEqual(left, right) => _numeric_compare_c(left, right, memory)(_ >= _)
      case RuleExpression.GreaterThan(left, right) => _numeric_compare_c(left, right, memory)(_ > _)
      case RuleExpression.LessThanOrEqual(left, right) => _numeric_compare_c(left, right, memory)(_ <= _)
      case RuleExpression.LessThan(left, right) => _numeric_compare_c(left, right, memory)(_ < _)
      case RuleExpression.And(left, right) => _boolean_binary_c(left, right, memory)(_ && _)
      case RuleExpression.Or(left, right) => _boolean_binary_c(left, right, memory)(_ || _)
      case RuleExpression.Not(value) => _boolean_c(value, memory).map(result => RuleFactValue.scalar(!result))
    }

  private def _binary_c(
    left: RuleExpression,
    right: RuleExpression,
    memory: WorkingMemory
  )(f: (RuleFactValue, RuleFactValue) => Consequence[Boolean]): Consequence[RuleFactValue] =
    for {
      leftvalue <- _evaluate_expression_c(left, memory)
      rightvalue <- _evaluate_expression_c(right, memory)
      result <- f(leftvalue, rightvalue)
    } yield RuleFactValue.scalar(result)

  private def _numeric_binary_c(
    left: RuleExpression,
    right: RuleExpression,
    memory: WorkingMemory
  )(f: (BigDecimal, BigDecimal) => BigDecimal): Consequence[RuleFactValue] =
    for {
      leftvalue <- _evaluate_expression_c(left, memory)
      rightvalue <- _evaluate_expression_c(right, memory)
      leftnumber <- _decimal_c(leftvalue)
      rightnumber <- _decimal_c(rightvalue)
    } yield RuleFactValue.scalar(f(leftnumber, rightnumber))

  private def _numeric_compare_c(
    left: RuleExpression,
    right: RuleExpression,
    memory: WorkingMemory
  )(f: (BigDecimal, BigDecimal) => Boolean): Consequence[RuleFactValue] =
    for {
      leftvalue <- _evaluate_expression_c(left, memory)
      rightvalue <- _evaluate_expression_c(right, memory)
      leftnumber <- _decimal_c(leftvalue)
      rightnumber <- _decimal_c(rightvalue)
    } yield RuleFactValue.scalar(f(leftnumber, rightnumber))

  private def _boolean_binary_c(
    left: RuleExpression,
    right: RuleExpression,
    memory: WorkingMemory
  )(f: (Boolean, Boolean) => Boolean): Consequence[RuleFactValue] =
    for {
      leftvalue <- _boolean_c(left, memory)
      rightvalue <- _boolean_c(right, memory)
    } yield RuleFactValue.scalar(f(leftvalue, rightvalue))

  private def _boolean_c(expression: RuleExpression, memory: WorkingMemory): Consequence[Boolean] =
    _evaluate_expression_c(expression, memory).flatMap {
      case RuleFactValue.Scalar(value: Boolean) => Consequence.success(value)
      case RuleFactValue.Scalar(value) => Consequence.argumentExpectedActualMismatch("RuleExpression", "Boolean", value)
      case RuleFactValue.Structured(_) => Consequence.argumentExpectedActualMismatch("RuleExpression", "Boolean", "Record")
    }

  private def _derivation_matches_c(expression: RuleExpression, memory: WorkingMemory): Consequence[Boolean] =
    if (expression.factIds.exists(id => memory.find(id).isEmpty))
      Consequence.success(false)
    else
      _boolean_c(expression, memory)

  private def _decimal_c(value: RuleFactValue): Consequence[BigDecimal] =
    value match {
      case RuleFactValue.Scalar(value: BigDecimal) => Consequence.success(value)
      case RuleFactValue.Scalar(value: java.math.BigDecimal) => Consequence.success(BigDecimal(value))
      case RuleFactValue.Scalar(value: BigInt) => Consequence.success(BigDecimal(value))
      case RuleFactValue.Scalar(value: java.math.BigInteger) => Consequence.success(BigDecimal(new java.math.BigDecimal(value)))
      case RuleFactValue.Scalar(value: Byte) => Consequence.success(BigDecimal(value))
      case RuleFactValue.Scalar(value: Short) => Consequence.success(BigDecimal(value))
      case RuleFactValue.Scalar(value: Int) => Consequence.success(BigDecimal(value))
      case RuleFactValue.Scalar(value: Long) => Consequence.success(BigDecimal(value))
      case RuleFactValue.Scalar(value) => Consequence.argumentExpectedActualMismatch("RuleExpression", "BigDecimal or integral value", value)
      case RuleFactValue.Structured(_) => Consequence.argumentExpectedActualMismatch("RuleExpression", "BigDecimal or integral value", "Record")
    }

  private def _calculation_explanation(calculation: RuleCalculationResult): RuleExplanation =
    RuleExplanation(
      calculation.ruleId,
      RuleExplanationKind.Calculated,
      _summary(calculation.explanation, s"Calculated ${calculation.name}"),
      calculation.sourceFactIds
    )

  private def _constraint_explanation(violation: RuleConstraintViolation): RuleExplanation =
    RuleExplanation(
      violation.ruleId,
      RuleExplanationKind.ConstraintViolation,
      _summary(violation.explanation, violation.message),
      violation.sourceFactIds
    )

  private def _summary(template: RuleExplanationTemplate, default: String): String =
    Option(template.summary).map(_.trim).filter(_.nonEmpty).getOrElse(default)
}
