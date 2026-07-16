package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.rule.{Fact, Rule, RuleActionOutcome, RuleActionPlan, RuleEvaluationResult, RuleExplanation, RuleFireResult, RuleSet}

/*
 * Read-only projection for descriptor and help surfaces. It intentionally
 * exposes declaration metadata, not fact values, actions, or provider state.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
object RuleProjection {
  def project(ruleset: RuleSet): Record =
    Record.data(
      "id" -> ruleset.identity.id.value,
      "version" -> ruleset.identity.version.value,
      "inputFacts" -> ruleset.inputFacts.map(_.value),
      "outputFacts" -> ruleset.outputFacts.map(_.value),
      "rules" -> ruleset.orderedRules.map(_rule_record)
    )

  def projectEvaluation(result: RuleEvaluationResult): Record =
    Record.data(
      "ruleSet" -> Record.data(
        "id" -> result.ruleSet.id.value,
        "version" -> result.ruleSet.version.value
      ),
      "agenda" -> result.agenda.entries.map { activation =>
        Record.data(
          "id" -> activation.id.value,
          "ruleId" -> activation.ruleId.value,
          "priority" -> activation.priority.value,
          "matchedFactIds" -> activation.matchedFactIds.map(_.value)
        )
      },
      "explanations" -> result.explanations.map(_explanation_record),
      "calculations" -> result.calculations.map { calculation =>
        Record.data(
          "ruleId" -> calculation.ruleId.value,
          "name" -> calculation.name,
          "sourceFactIds" -> calculation.sourceFactIds.map(_.value)
        )
      },
      "decisionTables" -> result.decisionTables.map { decision =>
        Record.data(
          "ruleId" -> decision.ruleId.value,
          "tableId" -> decision.tableId.value,
          "selectedRowId" -> decision.selectedRowId.map(_.value).getOrElse(""),
          "sourceFactIds" -> decision.sourceFactIds.map(_.value)
        )
      },
      "constraintViolations" -> result.constraintViolations.map { violation =>
        Record.data(
          "ruleId" -> violation.ruleId.value,
          "code" -> violation.code,
          "message" -> violation.message,
          "sourceFactIds" -> violation.sourceFactIds.map(_.value)
        )
      },
      "derivedFacts" -> result.derivedFacts.map(_fact_record),
      "plannedActions" -> result.actionPlans.map(_action_plan_record)
    )

  def projectFiring(result: RuleFireResult): Record =
    Record.data(
      "ruleSet" -> Record.data(
        "id" -> result.ruleSet.id.value,
        "version" -> result.ruleSet.version.value
      ),
      "outcomes" -> result.outcomes.map(_action_outcome_record)
    )

  private def _rule_record(rule: Rule): Record =
    Record.data(
      "id" -> rule.id.value,
      "family" -> rule.family.token,
      "priority" -> rule.priority.value,
      "conditions" -> rule.definition.conditions,
      "outputs" -> rule.definition.outputs,
      "explanation" -> rule.explanation.summary
    )

  private def _explanation_record(value: RuleExplanation): Record =
    Record.data(
      "ruleId" -> value.ruleId.value,
      "kind" -> value.kind.token,
      "summary" -> value.summary,
      "sourceFactIds" -> value.sourceFactIds.map(_.value),
      "resultFactIds" -> value.resultFactIds.map(_.value)
    )

  private def _fact_record(value: Fact): Record =
    Record.data(
      "id" -> value.id.value,
      "name" -> value.name.value,
      "origin" -> value.provenance.origin.token,
      "sourceRuleId" -> value.provenance.sourceRuleId.map(_.value).getOrElse(""),
      "sourceFactIds" -> value.provenance.sourceFactIds.map(_.value)
    )

  private def _action_plan_record(value: RuleActionPlan): Record = {
    val kind = value match {
      case _: RuleActionPlan.Operation => "operation"
      case _: RuleActionPlan.Event => "event"
      case _: RuleActionPlan.Job => "job"
      case _: RuleActionPlan.Recommendation => "recommendation"
    }
    Record.data(
      "id" -> value.id.value,
      "ruleId" -> value.ruleId.value,
      "kind" -> kind
    )
  }

  private def _action_outcome_record(value: RuleActionOutcome): Record = {
    val (kind, attributes) = value match {
      case RuleActionOutcome.OperationCompleted(_, _) =>
        "operation" -> Map.empty[String, Any]
      case RuleActionOutcome.EventPublished(_, dispatched, persisted) =>
        "event" -> Map("dispatchedCount" -> dispatched, "persistent" -> persisted)
      case RuleActionOutcome.JobSubmitted(_, jobid) =>
        "job" -> Map("jobId" -> jobid.value)
      case RuleActionOutcome.RecommendationRecorded(_) =>
        "recommendation" -> Map.empty[String, Any]
    }
    val plan = value.plan
    Record.data(
      "id" -> plan.id.value,
      "ruleId" -> plan.ruleId.value,
      "kind" -> kind,
      "metadata" -> Record.data(attributes.toVector*)
    )
  }
}
