package org.goldenport.cncf.statemachine

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.context.ExecutionInvocationIdentity
import org.simplemodeling.model.datatype.EntityCollectionId

/*
 * Cozy-generated statemachine metadata passed through component contracts.
 *
 * @since   Mar. 24, 2026
 *  version Mar. 25, 2026
 *  version Aug. 14, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final case class CmlStateMachineDefinition(
  name: String,
  states: Vector[String] = Vector.empty,
  events: Vector[String] = Vector.empty,
  historyFieldName: Option[String] = None,
  historyComposites: Vector[CmlHistoryCompositeDefinition] = Vector.empty,
  normalized: Option[CmlNormalizedStateMachine] = None
) {
  require(
    normalized.forall(_.identity.name == name),
    "StateMachine normalized declaration must belong to its legacy definition name"
  )
}

trait CmlStateMachineDefinitionProvider {
  def stateMachineDefinitions: Vector[CmlStateMachineDefinition] = Vector.empty
}

final case class CmlHistoryCompositeDefinition(
  name: String,
  directLeaves: Vector[String] = Vector.empty,
  fallbackLeaf: Option[String] = None
)

/*
 * The generated ABI below intentionally contains declaration data only.  In
 * particular it does not carry a raw expression, an executable action, or a
 * runtime resolver.  Generated code can therefore preserve the normalized CML
 * contract without inventing a second selector or execution path.
 */
final case class CmlStateMachineIdentity(name: String) {
  require(CmlStateMachineAbi.isName(name), "StateMachine identity name must be nonempty")
}

final case class CmlStateMachineVersion(value: Int) {
  require(value > 0, "StateMachine version must be positive")
}

final case class CmlStateMachineStatePath(segments: Vector[String]) {
  require(segments.nonEmpty, "StateMachine state path must be nonempty")
  require(segments.forall(CmlStateMachineAbi.isName), "StateMachine state path segments must be nonempty")

  def render: String = segments.mkString("/")
}

final case class CmlStateMachineStateIdentity(
  machine: CmlStateMachineIdentity,
  path: CmlStateMachineStatePath
)

final case class CmlStateMachineTriggerIdentity(
  machine: CmlStateMachineIdentity,
  name: String
) {
  require(CmlStateMachineAbi.isName(name), "StateMachine trigger identity name must be nonempty")
}

/**
 * The service/operation suffix of an explicit StateMachine operation trigger.
 * The component identity remains on [[CmlTransitionBinding]], preventing a
 * second rendered selector format from entering the generated ABI.
 */
final case class CmlStateMachineOperationIdentity(
  service: String,
  operation: String
) {
  require(CmlStateMachineAbi.isOperationSegment(service), "StateMachine operation service must be one nonempty segment")
  require(CmlStateMachineAbi.isOperationSegment(operation), "StateMachine operation name must be one nonempty segment")
}

/*
 * Complete generated-CML identity for a transition selected by the runtime.
 * It intentionally carries declarations only: a planner attaches it to the
 * selected plan, while later runtime stages must not recover it from an
 * operation name, a state value, or a record payload.
 */
final case class CmlTransitionBinding(
  componentId: ComponentId,
  entityType: EntityCollectionId,
  machine: CmlStateMachineIdentity,
  version: CmlStateMachineVersion,
  transition: CmlStateMachineTransitionIdentity,
  source: CmlStateMachineStateIdentity,
  target: CmlStateMachineTransitionTarget,
  trigger: CmlStateMachineTriggerIdentity,
  operation: Option[CmlStateMachineOperationIdentity] = None,
  triggerContext: Option[CmlStateMachineTriggerContext] = None
) {
  require(transition.machine == machine, "StateMachine transition binding must belong to its machine")
  require(source.machine == machine, "StateMachine transition binding source must belong to its machine")
  require(trigger.machine == machine, "StateMachine transition binding trigger must belong to its machine")
  require(
    CmlStateMachineAbi.targetMachine(target).forall(_ == machine),
    "StateMachine transition binding target must belong to its machine"
  )
  require(
    triggerContext.forall(_.identity.trigger == trigger),
    "StateMachine transition binding trigger context must belong to its trigger"
  )
  require(
    triggerContext.forall(_.version == version),
    "StateMachine transition binding trigger context must have the binding version"
  )
  require(
    operation.forall(_ => triggerContext.nonEmpty),
    "Explicit StateMachine operation binding requires its typed trigger context"
  )

  /**
   * Explicit operation bindings are authorized by the execution invocation
   * identity, never by a transition event name or record-derived value.
   */
  def matchesOperationInvocation(
    invocation: Option[ExecutionInvocationIdentity]
  ): Boolean =
    operation.fold(true) { value =>
      val selector = Vector(componentId.name.trim, value.service, value.operation).mkString(".")
      invocation.exists(_.operationSelector.trim == selector)
    }
}

final case class CmlStateMachineTriggerContextIdentity(
  trigger: CmlStateMachineTriggerIdentity
)

final case class CmlStateMachineTriggerContextFieldIdentity(
  context: CmlStateMachineTriggerContextIdentity,
  name: String
) {
  require(CmlStateMachineAbi.isName(name), "StateMachine trigger-context field name must be nonempty")
}

final case class CmlStateMachineTransitionIdentity(
  machine: CmlStateMachineIdentity,
  declarationOrder: Int
) {
  require(declarationOrder >= 0, "StateMachine transition declaration order must be nonnegative")
}

final case class CmlStateMachineGuardIdentity(
  transition: CmlStateMachineTransitionIdentity,
  name: String
) {
  require(CmlStateMachineAbi.isName(name), "StateMachine guard identity name must be nonempty")
}

final case class CmlStateMachineHistoryIdentity(
  machine: CmlStateMachineIdentity,
  name: String
) {
  require(CmlStateMachineAbi.isName(name), "StateMachine history identity name must be nonempty")
}

final case class CmlStateMachineActionIdentity(
  transition: CmlStateMachineTransitionIdentity,
  phase: CmlStateMachineActionPhase,
  order: Int
) {
  require(order >= 0, "StateMachine action order must be nonnegative")
}

final case class CmlStateMachineSourceLocation(
  machine: CmlStateMachineIdentity,
  declarationPath: Vector[String]
) {
  require(declarationPath.nonEmpty, "StateMachine source location path must be nonempty")
  require(declarationPath.forall(CmlStateMachineAbi.isName), "StateMachine source location path segments must be nonempty")
}

enum CmlStateMachineStateKind(val code: String):
  case Leaf extends CmlStateMachineStateKind("leaf")
  case Composite extends CmlStateMachineStateKind("composite")

enum CmlStateMachineScalarType(val code: String):
  case StringValue extends CmlStateMachineScalarType("string")
  case BooleanValue extends CmlStateMachineScalarType("boolean")
  case IntegerValue extends CmlStateMachineScalarType("integer")

enum CmlStateMachineActionPhase(val code: String, val rank: Int):
  case Exit extends CmlStateMachineActionPhase("exit", 0)
  case Transition extends CmlStateMachineActionPhase("transition", 1)
  case Entry extends CmlStateMachineActionPhase("entry", 2)

final case class CmlStateMachineTriggerContextField(
  identity: CmlStateMachineTriggerContextFieldIdentity,
  scalarType: CmlStateMachineScalarType
)

final case class CmlStateMachineTriggerContext(
  identity: CmlStateMachineTriggerContextIdentity,
  version: CmlStateMachineVersion,
  fields: Vector[CmlStateMachineTriggerContextField]
) {
  require(
    fields.forall(_.identity.context == identity),
    "StateMachine trigger-context fields must belong to their context"
  )
  require(
    CmlStateMachineAbi.unique(fields.map(_.identity.name)),
    "StateMachine trigger-context field names must be unique"
  )
}

final case class CmlStateMachineTrigger(
  identity: CmlStateMachineTriggerIdentity,
  context: CmlStateMachineTriggerContext
) {
  require(
    context.identity.trigger == identity,
    "StateMachine trigger context must belong to its trigger"
  )
}

sealed trait CmlStateMachinePredicateValue

object CmlStateMachinePredicateValue {
  final case class StringLiteral(value: String) extends CmlStateMachinePredicateValue
  final case class BooleanLiteral(value: Boolean) extends CmlStateMachinePredicateValue
  final case class IntegerLiteral(value: Long) extends CmlStateMachinePredicateValue
  final case class Field(identity: CmlStateMachineTriggerContextFieldIdentity) extends CmlStateMachinePredicateValue
}

sealed trait CmlStateMachinePredicate

object CmlStateMachinePredicate {
  final case class Literal(value: Boolean) extends CmlStateMachinePredicate
  final case class Equal(
    left: CmlStateMachinePredicateValue,
    right: CmlStateMachinePredicateValue
  ) extends CmlStateMachinePredicate
  final case class NotEqual(
    left: CmlStateMachinePredicateValue,
    right: CmlStateMachinePredicateValue
  ) extends CmlStateMachinePredicate
  final case class All(terms: Vector[CmlStateMachinePredicate]) extends CmlStateMachinePredicate {
    require(terms.nonEmpty, "StateMachine predicate all requires at least one term")
  }
  final case class Any(terms: Vector[CmlStateMachinePredicate]) extends CmlStateMachinePredicate {
    require(terms.nonEmpty, "StateMachine predicate any requires at least one term")
  }
  final case class Not(term: CmlStateMachinePredicate) extends CmlStateMachinePredicate
  final case class Present(field: CmlStateMachineTriggerContextFieldIdentity) extends CmlStateMachinePredicate
}

final case class CmlStateMachinePredicateProgram(
  version: CmlStateMachineVersion,
  predicate: CmlStateMachinePredicate
)

sealed trait CmlStateMachineGuardProgram {
  def identity: CmlStateMachineGuardIdentity
}

object CmlStateMachineGuardProgram {
  final case class Predicate(
    identity: CmlStateMachineGuardIdentity,
    program: CmlStateMachinePredicateProgram
  ) extends CmlStateMachineGuardProgram

  final case class Named(
    identity: CmlStateMachineGuardIdentity,
    bindingName: String
  ) extends CmlStateMachineGuardProgram {
    require(CmlStateMachineAbi.isName(bindingName), "StateMachine named guard binding must be nonempty")
  }
}

final case class CmlStateMachineActionBinding(
  identity: CmlStateMachineActionIdentity,
  bindingName: String
) {
  require(CmlStateMachineAbi.isName(bindingName), "StateMachine named action binding must be nonempty")
}

final case class CmlStateMachineStateDefinition(
  identity: CmlStateMachineStateIdentity,
  kind: CmlStateMachineStateKind,
  parent: Option[CmlStateMachineStateIdentity] = None
) {
  require(
    parent.forall(_.machine == identity.machine),
    "StateMachine parent state must belong to its machine"
  )
}

final case class CmlStateMachineShallowHistoryTarget(
  composite: CmlStateMachineStateIdentity,
  fallbackLeaf: CmlStateMachineStateIdentity
) {
  require(
    fallbackLeaf.machine == composite.machine,
    "StateMachine shallow-history fallback must belong to its machine"
  )
}

sealed trait CmlStateMachineTransitionTarget

object CmlStateMachineTransitionTarget {
  final case class State(state: CmlStateMachineStateIdentity) extends CmlStateMachineTransitionTarget
  final case class ShallowHistory(target: CmlStateMachineShallowHistoryTarget) extends CmlStateMachineTransitionTarget
  case object Final extends CmlStateMachineTransitionTarget
}

final case class CmlStateMachineCompositeTopology(
  composite: CmlStateMachineStateIdentity,
  directLeaves: Vector[CmlStateMachineStateIdentity],
  shallowHistoryTargets: Vector[CmlStateMachineShallowHistoryTarget] = Vector.empty
) {
  require(directLeaves.nonEmpty, "StateMachine composite topology requires direct leaves")
  require(
    directLeaves.forall(_.machine == composite.machine),
    "StateMachine composite leaves must belong to its machine"
  )
  require(
    CmlStateMachineAbi.unique(directLeaves),
    "StateMachine composite direct leaves must be unique"
  )
  require(
    shallowHistoryTargets.forall(_.composite == composite),
    "StateMachine shallow-history targets must belong to their composite"
  )
  require(
    shallowHistoryTargets.size <= 1,
    "StateMachine composite topology permits at most one shallow-history target"
  )
}

final case class CmlStateMachineTopology(
  composites: Vector[CmlStateMachineCompositeTopology] = Vector.empty
) {
  require(
    CmlStateMachineAbi.unique(composites.map(_.composite)),
    "StateMachine composite topology entries must be unique"
  )
}

final case class CmlStateMachineHistoryWrite(
  history: CmlStateMachineHistoryIdentity,
  composite: CmlStateMachineStateIdentity,
  leaf: CmlStateMachineStateIdentity
) {
  require(history.machine == composite.machine, "StateMachine history write composite must belong to its machine")
  require(history.machine == leaf.machine, "StateMachine history write leaf must belong to its machine")
}

final case class CmlStateMachineTransition(
  identity: CmlStateMachineTransitionIdentity,
  source: CmlStateMachineStateIdentity,
  target: CmlStateMachineTransitionTarget,
  trigger: CmlStateMachineTrigger,
  priority: Int,
  guard: CmlStateMachineGuardProgram,
  actions: Vector[CmlStateMachineActionBinding] = Vector.empty,
  historyWrites: Vector[CmlStateMachineHistoryWrite] = Vector.empty,
  sourceLocation: CmlStateMachineSourceLocation
) {
  require(priority >= 0, "StateMachine transition priority must be nonnegative")
  require(source.machine == identity.machine, "StateMachine transition source must belong to its machine")
  require(trigger.identity.machine == identity.machine, "StateMachine transition trigger must belong to its machine")
  require(guard.identity.transition == identity, "StateMachine transition guard must belong to its transition")
  require(sourceLocation.machine == identity.machine, "StateMachine source location must belong to its machine")
  require(
    CmlStateMachineAbi.targetMachine(target).forall(_ == identity.machine),
    "StateMachine transition target must belong to its machine"
  )
  require(
    actions.forall(_.identity.transition == identity),
    "StateMachine action bindings must belong to their transition"
  )
  require(
    CmlStateMachineAbi.unique(actions.map(action => (action.identity.phase, action.identity.order))),
    "StateMachine action phase/order pairs must be unique"
  )
  require(
    actions == actions.sortBy(action => (action.identity.phase.rank, action.identity.order)),
    "StateMachine action bindings must be in phase/order declaration order"
  )
  require(
    historyWrites.forall(_.history.machine == identity.machine),
    "StateMachine history writes must belong to its machine"
  )
  require(
    CmlStateMachineAbi.guardFields(guard).forall { field =>
      trigger.context.fields.exists(_.identity == field)
    },
    "StateMachine predicate fields must be declared by its trigger context"
  )
  require(
    CmlStateMachineAbi.unique(historyWrites.map(write => (write.history, write.composite))),
    "StateMachine history writes must be unique per history/composite"
  )
}

final case class CmlNormalizedStateMachine(
  identity: CmlStateMachineIdentity,
  version: CmlStateMachineVersion,
  initialState: CmlStateMachineStateIdentity,
  states: Vector[CmlStateMachineStateDefinition],
  transitions: Vector[CmlStateMachineTransition],
  terminalTransitions: Vector[CmlStateMachineTransitionIdentity],
  topology: CmlStateMachineTopology = CmlStateMachineTopology(),
  historyField: Option[CmlStateMachineHistoryIdentity] = None
) {
  private val _state_identities = states.map(_.identity)
  private val _topologies = topology.composites

  require(states.nonEmpty, "StateMachine normalized declaration requires states")
  require(
    states.forall(_.identity.machine == identity),
    "StateMachine states must belong to their machine"
  )
  require(CmlStateMachineAbi.unique(_state_identities), "StateMachine state identities must be unique")
  require(initialState.machine == identity, "StateMachine initial state must belong to its machine")
  require(_state_identities.contains(initialState), "StateMachine initial state must be declared")
  require(
    transitions.forall(_.identity.machine == identity),
    "StateMachine transitions must belong to their machine"
  )
  require(
    CmlStateMachineAbi.unique(transitions.map(_.identity.declarationOrder)),
    "StateMachine transition declaration orders must be unique"
  )
  require(
    transitions == transitions.sortBy(_.identity.declarationOrder),
    "StateMachine transitions must be in declaration order"
  )
  require(
    terminalTransitions == transitions.collect {
      case transition if transition.target == CmlStateMachineTransitionTarget.Final => transition.identity
    },
    "StateMachine terminal transitions must exactly match transitions targeting final"
  )
  require(
    transitions.forall(transition => CmlStateMachineAbi.referencesDeclaredStates(
      transition,
      _state_identities.toSet,
      _topologies
    )),
    "StateMachine transition references must be declared by their machine"
  )
  require(
    historyField.forall(_.machine == identity),
    "StateMachine history field must belong to its machine"
  )
  require(
    transitions.flatMap(_.historyWrites).forall(write => historyField.contains(write.history)),
    "StateMachine history writes must use the declared history field"
  )
  require(
    CmlStateMachineAbi.validTopology(states, _topologies),
    "StateMachine topology must use declared direct composite leaves and shallow-history fallbacks"
  )
}

private object CmlStateMachineAbi {
  def isName(value: String): Boolean = value.trim.nonEmpty

  def isOperationSegment(value: String): Boolean =
    Option(value).exists(x => x.trim.nonEmpty && !x.contains("."))

  def unique[A](values: Vector[A]): Boolean = values.distinct.size == values.size

  def targetMachine(target: CmlStateMachineTransitionTarget): Option[CmlStateMachineIdentity] =
    target match {
      case CmlStateMachineTransitionTarget.State(state) => Some(state.machine)
      case CmlStateMachineTransitionTarget.ShallowHistory(history) => Some(history.composite.machine)
      case CmlStateMachineTransitionTarget.Final => None
    }

  def referencesDeclaredStates(
    transition: CmlStateMachineTransition,
    states: Set[CmlStateMachineStateIdentity],
    topologies: Vector[CmlStateMachineCompositeTopology]
  ): Boolean = {
    val targetvalid = transition.target match {
      case CmlStateMachineTransitionTarget.State(state) => states.contains(state)
      case CmlStateMachineTransitionTarget.ShallowHistory(history) =>
        topologies.exists(_.shallowHistoryTargets.contains(history))
      case CmlStateMachineTransitionTarget.Final => true
    }
    states.contains(transition.source) && targetvalid &&
      transition.historyWrites.forall { write =>
        topologies.exists { topology =>
          topology.composite == write.composite && topology.directLeaves.contains(write.leaf)
        }
      }
  }

  def validTopology(
    states: Vector[CmlStateMachineStateDefinition],
    topologies: Vector[CmlStateMachineCompositeTopology]
  ): Boolean = {
    val definitions = states.map(state => state.identity -> state).toMap
    val composites = states.collect {
      case state if state.kind == CmlStateMachineStateKind.Composite => state.identity
    }
    val parentsvalid = states.forall { state =>
      state.parent match {
        case Some(parent) =>
          definitions.get(parent).exists { parentdefinition =>
            state.kind == CmlStateMachineStateKind.Leaf &&
              parentdefinition.kind == CmlStateMachineStateKind.Composite &&
              parentdefinition.parent.isEmpty &&
              parent.path.segments.size == 1 &&
              state.identity.path.segments == (parent.path.segments :+ state.identity.path.segments.last)
          }
        case None =>
          state.identity.path.segments.size == 1
      }
    }
    parentsvalid && composites.toSet == topologies.map(_.composite).toSet && topologies.forall { topology =>
      val declaredleaves = states.collect {
        case definition
            if definition.kind == CmlStateMachineStateKind.Leaf &&
              definition.parent.contains(topology.composite) =>
          definition.identity
      }
      definitions.get(topology.composite).exists { definition =>
        definition.kind == CmlStateMachineStateKind.Composite &&
          definition.parent.isEmpty &&
          topology.composite.path.segments.size == 1
      } &&
        topology.directLeaves == declaredleaves &&
        topology.shallowHistoryTargets.forall { history =>
          topology.directLeaves.contains(history.fallbackLeaf)
        }
    }
  }

  def guardFields(
    guard: CmlStateMachineGuardProgram
  ): Vector[CmlStateMachineTriggerContextFieldIdentity] =
    guard match {
      case CmlStateMachineGuardProgram.Predicate(_, program) => _predicate_fields(program.predicate)
      case CmlStateMachineGuardProgram.Named(_, _) => Vector.empty
    }

  private def _predicate_fields(
    predicate: CmlStateMachinePredicate
  ): Vector[CmlStateMachineTriggerContextFieldIdentity] =
    predicate match {
      case CmlStateMachinePredicate.Literal(_) => Vector.empty
      case CmlStateMachinePredicate.Equal(left, right) => _predicate_value_fields(left) ++ _predicate_value_fields(right)
      case CmlStateMachinePredicate.NotEqual(left, right) => _predicate_value_fields(left) ++ _predicate_value_fields(right)
      case CmlStateMachinePredicate.All(terms) => terms.flatMap(_predicate_fields)
      case CmlStateMachinePredicate.Any(terms) => terms.flatMap(_predicate_fields)
      case CmlStateMachinePredicate.Not(term) => _predicate_fields(term)
      case CmlStateMachinePredicate.Present(field) => Vector(field)
    }

  private def _predicate_value_fields(
    value: CmlStateMachinePredicateValue
  ): Vector[CmlStateMachineTriggerContextFieldIdentity] =
    value match {
      case CmlStateMachinePredicateValue.Field(identity) => Vector(identity)
      case _ => Vector.empty
    }
}
