# StateMachine DSL and Execution Design (CML + core + CNCF)
==========================================================

status=work-in-progress
published_at=2026-03-19

---

# Overview

This note summarizes the design of a StateMachine DSL in CML (Cozy Modeling Language) and its integration with:

- a pure core model
- the CNCF execution runtime

The goal is to establish a clean separation between declarative specification and executable behavior, while keeping the system compatible with:

- AI-assisted generation
- literate modeling (SmartDox / Cozy)
- CNCF component execution model

---

# Design Principles

## 1. Separation of Concerns

The architecture is explicitly layered:

CML (DSL)
  ↓
AST (StateMachineDef)
  ↓
core (pure model)
  ↓
CNCF (execution)

- DSL: declarative specification
- AST: structured intermediate representation
- core: pure evaluation (no side effects)
- CNCF: execution (side effects, IO, orchestration)

---

## 2. Declarative First

The DSL describes what should happen, not how to execute it.

Example:

guard :: amount > 0
action :: recordPayment

- guard is declarative logic
- action is a symbolic reference

---

## 3. Name-Based Binding

All executable elements are referenced by name:

- Action → ActionRef
- Guard → GuardExpr (Ref or Expression)

Binding is resolved at runtime by CNCF.

---

## 4. Pure Core Model

The core StateMachine:

- does NOT execute actions
- does NOT access external systems
- evaluates only:
  - transitions
  - guard conditions abstractly (actual evaluation may be delegated to runtime)

---

# CML StateMachine DSL

## Structure

# StateMachine

## <Name>

### State

#### <StateName>

##### Entry
- action :: <ActionName>

##### Exit
- action :: <ActionName>

##### Transition
- to :: <NextState>
- on :: <EventName>
- guard :: <GuardExpr>
- action :: <ActionName>

---

### Event

#### <EventName>

---

## Example

# StateMachine

## OrderStatus

### State

#### Created

##### Entry
- action :: initOrder

##### Transition
- to :: Paid
- on :: pay
- guard :: event.amount > 0
- action :: recordPayment

#### Paid

##### Entry
- action :: notifyPayment

##### Transition
- to :: Shipped
- on :: ship
- action :: reserveStock

---

### Event

#### pay

#### ship

---

# AST Design

## Root

case class StateMachineDef(
  name: String,
  states: Vector[StateDef],
  events: Vector[EventDef]
)

---

## State

case class StateDef(
  name: String,
  entry: Vector[ActionExpr],
  exit: Vector[ActionExpr],
  transitions: Vector[TransitionDef]
)

---

## Transition

case class TransitionDef(
  to: String,
  event: String,
  guard: Option[GuardExpr],
  actions: Vector[ActionExpr],
  priority: Int
)

Priority rule:

- smaller value = higher priority
- if multiple transitions have same priority → deterministic order or error

---

## Event

case class EventDef(
  name: String,
  payload: Vector[FieldDef]
)

---

# Guard Expression Design

GuardExpr supports two forms:

- Ref (name-based guard)
- Expression (MVEL)

Ref is used for reusable domain logic.
Expression is used for inline conditions.

Detailed runtime behavior is defined in:
→ Guard Parsing and Evaluation

---

# Action Binding (CNCF)

## Concept

ActionRef("recordPayment")
  ↓
Component / Service / Operation
  ↓
Execution

---

## Resolver

trait ActionBindingResolver[S, E] {
  def resolve(name: String): ResolvedAction[S, E]
}

---

## Resolved Action

trait ResolvedAction[S, E] {
  def run(state: S, event: E): Consequence[Unit]
}

---

# Execution Flow

Event received
  ↓
StateMachinePlanner.plan()
  ↓
ExecutionPlan
  ↓
CNCF Executor
  ↓
exit → transition → entry
  ↓
state update

---

# Key Design Decisions

## 1. Entry / Exit / Transition Separation

Execution order:

exit(from)
→ transition action
→ entry(to)

---

## 2. Guard Strategy

- Simple condition → MVEL expression
- Complex logic → named guard (Ref)

---

## 3. Action Strategy

- DSL: symbolic name
- CNCF: concrete execution

---

## 4. Determinism

- (state, event) → at most one transition
- controlled by priority

---

# Advantages

## 1. AI-Friendly

- Structured DSL
- Explicit logic
- No hidden behavior

---

## 2. Testability

- core evaluation is pure
- guards can be evaluated without runtime

---

## 3. Extensibility

Future extensions:

- composite state
- async transitions
- saga orchestration
- guard expression enrichment

---

# Next Steps

1. Implement parser (CML → AST)
2. Implement guard parser/evaluator (MVEL + Ref)
3. Implement action/guard resolver wiring in CNCF runtime
4. Provide full end-to-end example (CML → execution)

---

# Conclusion

This design establishes:

- a declarative StateMachine DSL
- a pure evaluation model
- a pluggable execution layer (CNCF)

It enables:

- AI-assisted modeling
- executable specifications
- clean separation of logic and runtime

---

This becomes a foundational piece for:

CML → CNCF → Executable Domain Models

---

# Action Binding Details (CNCF)

## Naming Strategy

By default, an action name maps to a CNCF operation using convention:

```
action name = operation name
```

Example:

```
recordPayment → order.main.recordPayment
```

Fully-qualified form (supported):

```
action :: order.main.recordPayment
```

---

## Action Binding Resolution Rules (Frozen)

Resolution order is fixed as follows:

1. If action is fully-qualified (`component.service.operation`), resolve directly.
2. If action is unqualified:
   - first resolve inside the bound execution scope (current component/service),
   - then try global unique match.
3. If multiple candidates remain, fail with configuration error (ambiguous action).
4. If no candidate exists, fail with configuration error (action not found).

This avoids implicit cross-component routing and keeps resolution deterministic.

---

## Resolver Design

```
trait ActionBindingResolver[S, E] {
  def resolve(name: String): ResolvedAction[S, E]
}

trait ResolvedAction[S, E] {
  def run(state: S, event: E): Consequence[Unit]
}
```

Example implementation:

```
case class ServiceCallAction[S, E](
  component: String,
  service: String,
  operation: String
) extends ResolvedAction[S, E] {
  def run(state: S, event: E): Consequence[Unit] =
    invoke(component, service, operation, state, event)
}
```

---

# Guard Parsing and Evaluation

## Parsing Strategy

- If guard is a single identifier → GuardExpr.Ref
- Otherwise → treat as expression string (MVEL)

Examples:

paymentConfirmed → GuardExpr.Ref
amount > 0 → GuardExpr.Expression
amount > 0 && confirmed == true → GuardExpr.Expression

---

## Evaluation Context

```
trait GuardContext {
  def get(name: String): Option[GuardValue]
}
```

Resolution order:

1. event
2. state
3. context

Collision handling is fixed:

- `event.*`, `state.*`, `context.*` are canonical and always valid.
- unqualified lookup (`amount`) is allowed only when it resolves uniquely.
- if unqualified name matches multiple sources, evaluation fails as configuration error.

---

## Expression Guard (runtime)

``` 
case class ExpressionGuard[S, E](
  expression: String,
  contextFactory: (S, E) => Map[String, Any]
) extends Guard[S, E] {
  def eval(state: S, event: E): Boolean = {
    val ctx = contextFactory(state, event)
    MvelEvaluator.evalBoolean(expression, ctx)
  }
}
```

---

## Guard Binding (Ref)

Ref-based guards are resolved via a binding resolver, similar to ActionBinding.

```
trait GuardBindingResolver[S, E] {
  def resolve(name: String): Guard[S, E]
}
```

---

## Ref Guard (runtime)

```
case class RefGuard[S, E](
  name: String,
  resolver: GuardBindingResolver[S, E]
) extends Guard[S, E] {
  def eval(state: S, event: E): Boolean = {
    val guard = resolver.resolve(name)
    guard.eval(state, event)
  }
}
```

---

## Guard Resolution Strategy

At runtime, GuardExpr is converted as follows:

```
GuardExpr
  Ref        → RefGuard
  Expression → ExpressionGuard
```

This ensures:

- Expression → inline evaluation (MVEL)
- Ref → reusable domain logic via resolver

---

# Event Payload Integration

Event definitions may include payload:

```
#### pay

##### Payload
amount :: Money
currency :: String
```

These values are exposed to GuardContext:

```
event.amount
event.currency
```

Also accessible as:

```
amount
currency
```

---

# End-to-End Execution Example

## CML

```
Transition Created -> Paid
  on: pay
  guard: event.amount > 0
  action: recordPayment
```

## Flow

```
Event(pay)
  ↓
Planner selects transition
  ↓
Guard evaluated (amount > 0)
  ↓
Action resolved (recordPayment)
  ↓
CNCF execution
  ↓
State updated (Created → Paid)
```

---

# Error Handling Strategy

- Missing action → fail fast (configuration error)
- Guard evaluation failure (parse/eval/runtime) → explicit failure, not false
- Guard result `false` → normal non-match (not failure)
- No matching transition → explicit failure or no-op (configurable)

---

# Determinism Rules (Frozen)

Transition selection is fixed as:

1. Filter transitions by `(currentState, event)`.
2. Evaluate guards.
3. Sort by `priority` (smaller value first).
4. For same `priority`, preserve declaration order in DSL/AST.
5. Select the first transition only.

This rule is normative for parser, planner, and runtime.

---

# Future Extensions (Planned)

- Composite State (hierarchical state machine)
- Async transition (job-based execution)
- Saga / compensation support
- Typed event payload validation
- Guard DSL enhancements (functions, collections)

---

# Design Status

Current state:

- DSL structure: defined
- AST: defined
- core model: defined
- Action binding: defined
- Guard expression: defined

Next milestone:

→ Implement parser (CML → AST)
→ Implement resolver (AST → runtime)
→ Execute end-to-end scenario
