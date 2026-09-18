package org.goldenport.cncf.projection

import org.goldenport.record.Record
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.statemachine.*

/*
 * @since   Mar. 20, 2026
 *  version Mar. 25, 2026
 * @version Aug. 14, 2026
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
    val definitions = component.stateMachineDefinitions.sortBy(_.name)
    val states = definitions.flatMap(_.states).distinct.sorted
    val definitionevents = definitions.flatMap(_.events)
    val events = (rules.map(_.eventName) ++ definitionevents).distinct.sorted
    Record.data(
      "type" -> "statemachine",
      "targetType" -> "component",
      "name" -> component.name,
      "states" -> states,
      "events" -> events,
      "definitions" -> definitions.map(_definition_record),
      "transitions" -> rules.map(_transition_record)
    )
  }

  private def _definition_record(
    p: org.goldenport.cncf.statemachine.CmlStateMachineDefinition
  ): Record =
    Record.create(Vector[(String, Any)](
      "name" -> p.name,
      "states" -> p.states.distinct.sorted,
      "events" -> p.events.distinct.sorted
    ) ++ p.historyFieldName.toVector.map(value => ("historyField", value: Any)) ++
      (if (p.historyComposites.isEmpty) Vector.empty else Vector(
        "historyComposites" -> p.historyComposites.map { composite =>
          Record.create(Vector[(String, Any)](
            "name" -> composite.name,
            "directLeaves" -> composite.directLeaves
          ) ++ composite.fallbackLeaf.toVector.map(value => ("fallbackLeaf", value: Any)))
        }
      )) ++ p.normalized.toVector.map(value => ("normalized", _normalized_record(value): Any)))

  private def _normalized_record(value: CmlNormalizedStateMachine): Record =
    Record.create(Vector[(String, Any)](
      "machine" -> _machine_identity_record(value.identity),
      "version" -> value.version.value,
      "initialState" -> _state_identity_record(value.initialState),
      "states" -> value.states.map(_state_definition_record),
      "transitions" -> value.transitions.map(_normalized_transition_record),
      "terminalTransitions" -> value.terminalTransitions.map(_transition_identity_record),
      "topology" -> _topology_record(value.topology)
    ) ++ value.historyField.toVector.map(value => ("historyField", _history_identity_record(value): Any)))

  private def _machine_identity_record(value: CmlStateMachineIdentity): Record =
    Record.data("name" -> value.name)

  private def _state_identity_record(value: CmlStateMachineStateIdentity): Record =
    Record.data(
      "machine" -> value.machine.name,
      "path" -> value.path.segments,
      "value" -> value.path.render
    )

  private def _state_definition_record(value: CmlStateMachineStateDefinition): Record =
    Record.create(Vector[(String, Any)](
      "identity" -> _state_identity_record(value.identity),
      "kind" -> value.kind.code
    ) ++ value.parent.toVector.map(value => ("parent", _state_identity_record(value): Any)))

  private def _transition_identity_record(value: CmlStateMachineTransitionIdentity): Record =
    Record.data(
      "machine" -> value.machine.name,
      "declarationOrder" -> value.declarationOrder
    )

  private def _trigger_identity_record(value: CmlStateMachineTriggerIdentity): Record =
    Record.data(
      "machine" -> value.machine.name,
      "name" -> value.name
    )

  private def _trigger_context_record(value: CmlStateMachineTriggerContext): Record =
    Record.data(
      "trigger" -> _trigger_identity_record(value.identity.trigger),
      "version" -> value.version.value,
      "fields" -> value.fields.map { field =>
        Record.data(
          "identity" -> _trigger_context_field_identity_record(field.identity),
          "name" -> field.identity.name,
          "scalarType" -> field.scalarType.code
        )
      }
    )

  private def _normalized_transition_record(value: CmlStateMachineTransition): Record = {
    val base = Vector[(String, Any)](
      "identity" -> _transition_identity_record(value.identity),
      "source" -> _state_identity_record(value.source),
      "target" -> _target_record(value.target),
      "trigger" -> Record.data(
        "identity" -> _trigger_identity_record(value.trigger.identity),
        "context" -> _trigger_context_record(value.trigger.context)
      ),
      "priority" -> value.priority,
      "declarationOrder" -> value.identity.declarationOrder,
      "sourceLocation" -> _source_location_record(value.sourceLocation),
      "guard" -> _normalized_guard_record(value.guard),
      "actions" -> value.actions.map(_action_record),
      "historyWrites" -> value.historyWrites.map(_history_write_record)
    )
    Record.create(base)
  }

  private def _target_record(value: CmlStateMachineTransitionTarget): Record =
    value match {
      case CmlStateMachineTransitionTarget.State(state) =>
        Record.data(
          "kind" -> "state",
          "state" -> _state_identity_record(state)
        )
      case CmlStateMachineTransitionTarget.ShallowHistory(target) =>
        Record.data(
          "kind" -> "shallow-history",
          "composite" -> _state_identity_record(target.composite),
          "fallbackLeaf" -> _state_identity_record(target.fallbackLeaf)
        )
      case CmlStateMachineTransitionTarget.Final =>
        Record.data("kind" -> "final")
    }

  private def _source_location_record(value: CmlStateMachineSourceLocation): Record =
    Record.data(
      "machine" -> value.machine.name,
      "declarationPath" -> value.declarationPath
    )

  private def _normalized_guard_record(value: CmlStateMachineGuardProgram): Record =
    value match {
      case CmlStateMachineGuardProgram.Predicate(identity, program) =>
        Record.data(
          "kind" -> "predicate",
          "identity" -> _guard_identity_record(identity),
          "program" -> _predicate_program_record(program)
        )
      case CmlStateMachineGuardProgram.Named(identity, bindingName) =>
        Record.data(
          "kind" -> "named",
          "identity" -> _guard_identity_record(identity),
          "binding" -> bindingName
        )
    }

  private def _guard_identity_record(value: CmlStateMachineGuardIdentity): Record =
    Record.data(
      "transition" -> _transition_identity_record(value.transition),
      "name" -> value.name
    )

  private def _predicate_program_record(value: CmlStateMachinePredicateProgram): Record =
    Record.data(
      "version" -> value.version.value,
      "predicate" -> _predicate_record(value.predicate)
    )

  private def _predicate_record(value: CmlStateMachinePredicate): Record =
    value match {
      case CmlStateMachinePredicate.Literal(result) =>
        Record.data("kind" -> "literal", "value" -> result)
      case CmlStateMachinePredicate.Equal(left, right) =>
        Record.data(
          "kind" -> "equal",
          "left" -> _predicate_value_record(left),
          "right" -> _predicate_value_record(right)
        )
      case CmlStateMachinePredicate.NotEqual(left, right) =>
        Record.data(
          "kind" -> "not-equal",
          "left" -> _predicate_value_record(left),
          "right" -> _predicate_value_record(right)
        )
      case CmlStateMachinePredicate.All(terms) =>
        Record.data("kind" -> "all", "terms" -> terms.map(_predicate_record))
      case CmlStateMachinePredicate.Any(terms) =>
        Record.data("kind" -> "any", "terms" -> terms.map(_predicate_record))
      case CmlStateMachinePredicate.Not(term) =>
        Record.data("kind" -> "not", "term" -> _predicate_record(term))
      case CmlStateMachinePredicate.Present(field) =>
        Record.data("kind" -> "present", "field" -> _trigger_context_field_identity_record(field))
    }

  private def _predicate_value_record(value: CmlStateMachinePredicateValue): Record =
    value match {
      case CmlStateMachinePredicateValue.StringLiteral(text) =>
        Record.data("kind" -> "string", "value" -> text)
      case CmlStateMachinePredicateValue.BooleanLiteral(result) =>
        Record.data("kind" -> "boolean", "value" -> result)
      case CmlStateMachinePredicateValue.IntegerLiteral(number) =>
        Record.data("kind" -> "integer", "value" -> number)
      case CmlStateMachinePredicateValue.Field(identity) =>
        Record.data("kind" -> "field", "identity" -> _trigger_context_field_identity_record(identity))
    }

  private def _trigger_context_field_identity_record(
    value: CmlStateMachineTriggerContextFieldIdentity
  ): Record =
    Record.data(
      "context" -> _trigger_context_identity_record(value.context),
      "name" -> value.name
    )

  private def _trigger_context_identity_record(
    value: CmlStateMachineTriggerContextIdentity
  ): Record =
    Record.data("trigger" -> _trigger_identity_record(value.trigger))

  private def _action_record(value: CmlStateMachineActionBinding): Record =
    Record.data(
      "identity" -> Record.data(
        "transition" -> _transition_identity_record(value.identity.transition),
        "phase" -> value.identity.phase.code,
        "order" -> value.identity.order
      ),
      "binding" -> value.bindingName
    )

  private def _history_identity_record(value: CmlStateMachineHistoryIdentity): Record =
    Record.data(
      "machine" -> value.machine.name,
      "name" -> value.name
    )

  private def _history_write_record(value: CmlStateMachineHistoryWrite): Record =
    Record.data(
      "history" -> _history_identity_record(value.history),
      "composite" -> _state_identity_record(value.composite),
      "leaf" -> _state_identity_record(value.leaf)
    )

  private def _topology_record(value: CmlStateMachineTopology): Record =
    Record.data(
      "composites" -> value.composites.map { composite =>
        Record.data(
          "composite" -> _state_identity_record(composite.composite),
          "directLeaves" -> composite.directLeaves.map(_state_identity_record),
          "shallowHistoryTargets" -> composite.shallowHistoryTargets.map { history =>
            Record.data(
              "composite" -> _state_identity_record(history.composite),
              "fallbackLeaf" -> _state_identity_record(history.fallbackLeaf)
            )
          }
        )
      }
    )

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
  ): Record = {
    val base = Vector[(String, Any)](
      "collection" -> rule.collectionName,
      "trigger" -> rule.trigger.toString.toLowerCase,
      "event" -> rule.eventName,
      "priority" -> rule.priority,
      "declarationOrder" -> rule.declarationOrder,
      "guard" -> _legacy_guard_record(rule.guard),
      "actions" -> Record.data(
        "exit" -> rule.plan.exitActions.size,
        "transition" -> rule.plan.transitionAction.size,
        "entry" -> rule.plan.entryActions.size
      )
    )
    val topology = Vector(
      rule.machineName.map("machine" -> _),
      rule.stateFieldName.map("stateField" -> _),
      rule.fromState.map("fromState" -> _),
      rule.fromStateValue.map("fromStateValue" -> _),
      rule.toState.map("toState" -> _),
      rule.toStateValue.map("toStateValue" -> _),
      rule.historyCompositeName.map("historyComposite" -> _),
      rule.historyFieldName.map("historyField" -> _),
      Option.when(rule.historyDirectLeaves.nonEmpty)("historyDirectLeaves" -> rule.historyDirectLeaves),
      rule.historyFallbackLeaf.map("historyFallbackLeaf" -> _),
      Option.when(rule.expectedHistoryRecordWrites.nonEmpty)(
        "expectedHistoryRecordWrites" -> rule.expectedHistoryRecordWrites.map { write =>
          Record.data("composite" -> write.compositeName, "leaf" -> write.leafName)
        }
      )
    ).flatten
    Record.create(base ++ topology)
  }

  private def _legacy_guard_record(
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
