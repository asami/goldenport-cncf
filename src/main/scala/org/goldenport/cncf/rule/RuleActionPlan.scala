package org.goldenport.cncf.rule

import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.{EventPolicyEngine, EventPublishOption, ReceptionDomainEvent}
import org.goldenport.cncf.job.{ActionId, ActionTask, JobId, JobPersistencePolicy, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.resolver.OperationResolver

/*
 * Production rules describe runtime intent only. The plan is immutable and
 * contains no repository, ActionCall, provider, or thread handle.
 *
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RuleActionPlanId(value: String) {
  def print: String = value
}

sealed abstract class RuleActionPlan {
  def id: RuleActionPlanId
  def ruleId: RuleId
}

object RuleActionPlan {
  final case class Operation(
    id: RuleActionPlanId,
    ruleId: RuleId,
    selector: String,
    parameters: Record = Record.empty
  ) extends RuleActionPlan

  final case class Event(
    id: RuleActionPlanId,
    ruleId: RuleId,
    name: String,
    kind: String = "domain-event",
    payload: Record = Record.empty,
    attributes: Map[String, String] = Map.empty,
    persistent: Boolean = false
  ) extends RuleActionPlan

  final case class Job(
    id: RuleActionPlanId,
    ruleId: RuleId,
    selector: String,
    parameters: Record = Record.empty
  ) extends RuleActionPlan

  final case class Recommendation(
    id: RuleActionPlanId,
    ruleId: RuleId,
    summary: String,
    details: Record = Record.empty
  ) extends RuleActionPlan
}

final case class RuleProduction(
  ruleId: RuleId,
  predicate: RuleExpression,
  plans: Vector[RuleActionPlan],
  explanation: RuleExplanationTemplate = RuleExplanationTemplate()
) {
  def normalized: RuleProduction =
    copy(plans = plans.sortBy(_.id.value))
}

sealed abstract class RuleActionOutcome {
  def plan: RuleActionPlan
}

object RuleActionOutcome {
  final case class OperationCompleted(
    plan: RuleActionPlan.Operation,
    response: OperationResponse
  ) extends RuleActionOutcome

  final case class EventPublished(
    plan: RuleActionPlan.Event,
    dispatchedCount: Int,
    persisted: Boolean
  ) extends RuleActionOutcome

  final case class JobSubmitted(
    plan: RuleActionPlan.Job,
    jobId: JobId
  ) extends RuleActionOutcome

  final case class RecommendationRecorded(
    plan: RuleActionPlan.Recommendation
  ) extends RuleActionOutcome
}

final case class RuleFireResult(
  ruleSet: RuleSetIdentity,
  outcomes: Vector[RuleActionOutcome]
)

/**
 * The only production adapter for firing a RuleActionPlan. It resolves an
 * operation to an Action and delegates execution to ComponentLogic, publishes
 * events through EventBus authorization, and submits asynchronous work through
 * the target component JobEngine.
 */
final class RuleActionAdmission(
  subsystem: Subsystem
) {
  def fireC(
    evaluation: RuleEvaluationResult
  )(using ctx: ExecutionContext): Consequence[RuleFireResult] =
    RuleTraceSupport.fireC(evaluation) {
      evaluation.actionPlans.foldLeft(Consequence.success(Vector.empty[RuleActionOutcome])) { (z, plan) =>
        z.flatMap(outcomes => _admit_c(plan).map(outcomes :+ _))
      }.map(outcomes => RuleFireResult(evaluation.ruleSet, outcomes))
    }

  private def _admit_c(
    plan: RuleActionPlan
  )(using ctx: ExecutionContext): Consequence[RuleActionOutcome] =
    plan match {
      case operation: RuleActionPlan.Operation =>
        _resolve_action_c(operation.selector, operation.parameters).flatMap { case (component, action) =>
          component.logic.executeAction(action, ctx).map(RuleActionOutcome.OperationCompleted(operation, _))
        }
      case event: RuleActionPlan.Event =>
        val domain_event = ReceptionDomainEvent(
          event.name,
          event.kind,
          event.payload.fields.map(field => field.key -> field.value.single).toMap,
          event.attributes,
          ctx.clock.instant()
        )
        subsystem.eventBus.publishAuthorized(
          domain_event,
          EventPublishOption(event.persistent),
          EventPolicyEngine.default
        ).map(result => RuleActionOutcome.EventPublished(event, result.dispatchedCount, result.persisted))
      case job: RuleActionPlan.Job =>
        _resolve_action_c(job.selector, job.parameters).flatMap { case (component, action) =>
          val task = ActionTask(
            ActionId.create("rule.production", ctx.clock.instant(), ctx.idGeneration),
            action,
            component.actionEngine,
            Some(component)
          )
          val option = JobSubmitOption(
            persistence = JobPersistencePolicy.Persistent,
            runMode = JobRunMode.Async,
            requestSummary = Some(s"rule:${job.ruleId.value}:${job.selector}"),
            executionNotes = Vector(s"rule action plan: ${job.id.value}")
          )
          component.logic.submitJob(List(task), ctx, option).map(RuleActionOutcome.JobSubmitted(job, _))
        }
      case recommendation: RuleActionPlan.Recommendation =>
        Consequence.success(RuleActionOutcome.RecommendationRecorded(recommendation))
    }

  private def _resolve_action_c(
    selector: String,
    parameters: Record
  ): Consequence[(Component, Action)] =
    subsystem.operationResolver.resolve(selector) match {
      case OperationResolver.ResolutionResult.Resolved(_, componentName, serviceName, operationName) =>
        subsystem.findComponent(componentName) match {
          case Some(component) =>
            val request = Request.of(
              component = componentName,
              service = serviceName,
              operation = operationName,
              arguments = parameters.fields.map(field => Argument(field.key, field.value.single)).toList
            )
            component.logic.makeOperationRequest(request).flatMap {
              case action: Action => Consequence.success(component -> action)
              case _ => Consequence.argumentInvalid(s"Rule action target is not an Action: $selector")
            }
          case None =>
            Consequence.operationNotFound(s"Rule action component: $componentName")
        }
      case OperationResolver.ResolutionResult.NotFound(_, value) =>
        Consequence.operationNotFound(s"Rule action operation: $value")
      case OperationResolver.ResolutionResult.Ambiguous(value, candidates) =>
        Consequence.argumentInvalid(s"ambiguous Rule action operation: $value => ${candidates.mkString(",")}")
      case OperationResolver.ResolutionResult.Invalid(message) =>
        Consequence.argumentInvalid(message)
    }
}
