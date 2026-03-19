package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.statemachine.{
  CollectionTransitionRule,
  CollectionTransitionRuleProvider,
  ExpressionGuard,
  Guard,
  RefGuard,
  TransitionEvent
}

/*
 * @since   Mar. 20, 2026
 * @version Mar. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object StateMachineProjection {
  import MetaProjectionSupport._

  def project(base: Component, selector: Option[String] = None): Record =
    resolve(base, selector) match {
      case Target.Subsystem(components, name) =>
        Record.data(
          "type" -> "statemachine",
          "targetType" -> "subsystem",
          "name" -> name,
          "components" -> components.sortBy(_.name).map(_component_record)
        )
      case Target.ComponentTarget(component) =>
        _component_record(component)
      case Target.ServiceTarget(_, _) | Target.OperationTarget(_, _, _) =>
        Record.data(
          "type" -> "error",
          "summary" -> "meta.statemachine selector accepts subsystem or component only"
        )
      case Target.NotFound(target) =>
        Record.data(
          "type" -> "error",
          "name" -> target.getOrElse("unknown"),
          "summary" -> "target not found"
        )
    }

  private def _component_record(component: Component): Record = {
    val rules = _rules(component)
    val events = rules.map(_.eventName).distinct.sorted
    Record.data(
      "type" -> "statemachine",
      "targetType" -> "component",
      "name" -> component.name,
      "states" -> Vector.empty[String],
      "events" -> events,
      "transitions" -> rules.map(_transition_record)
    )
  }

  private def _rules(component: Component): Vector[CollectionTransitionRule[Any]] =
    component match {
      case m: CollectionTransitionRuleProvider =>
        m.stateMachineTransitionRules.sortBy { rule =>
          (
            rule.collectionName,
            rule.trigger.toString,
            rule.eventName,
            rule.priority,
            rule.declarationOrder
          )
        }
      case _ =>
        Vector.empty
    }

  private def _transition_record(
    rule: CollectionTransitionRule[Any]
  ): Record =
    Record.data(
      "collection" -> rule.collectionName,
      "trigger" -> rule.trigger.toString.toLowerCase,
      "event" -> rule.eventName,
      "priority" -> rule.priority,
      "declarationOrder" -> rule.declarationOrder,
      "guard" -> _guard_record(rule.guard),
      "actions" -> Record.data(
        "exit" -> rule.plan.exitActions.size,
        "transition" -> rule.plan.transitionAction.size,
        "entry" -> rule.plan.entryActions.size
      )
    )

  private def _guard_record(
    guard: Option[Guard[Any, TransitionEvent]]
  ): Record =
    guard match {
      case None =>
        Record.data("kind" -> "always")
      case Some(g) =>
        g match {
          case RefGuard(name, _) =>
            Record.data(
              "kind" -> "ref",
              "value" -> name
            )
          case ExpressionGuard(expression, _) =>
            Record.data(
              "kind" -> "expression",
              "value" -> expression
            )
          case other =>
            Record.data(
              "kind" -> "custom",
              "value" -> other.getClass.getName
            )
        }
    }
}
