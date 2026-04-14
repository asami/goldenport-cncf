# Aggregate/View Semantic Boundary (AV-01)

status = frozen
since = 2026-03-21
phase = 7

## Purpose

Freeze the canonical semantic boundary for Aggregate/View in CNCF Phase 7 AV-01.

This document defines contract shape and constraints only.
Generation/runtime implementation is out of AV-01 scope.

## Canonical Direction

- `Entity` is the single source of truth.
- `Aggregate` is the write-side projection.
- `View` is the read-side projection.
- `Event` is the bridge from Aggregate update to View projection.

## Aggregate Contract

Aggregate is defined as a pure command handler with invariant enforcement.

Input:
- command
- current state

Output:
- new state
- emitted events

Canonical shape:

```scala
handle(command, state) -> Consequence[(newState, events)]
```

Rules:
- deterministic for the same `(command, state)` input
- no dependency on view state
- invariant violation returns failure

## View Contract

View is defined as an event-driven projection.

Input:
- event stream (or event sequence)

Output:
- projected read model

Rules:
- rebuildable from event stream
- deterministic for the same event sequence
- query/read side must not mutate aggregate state

## CML/AST Boundary

Entity AST extension shape (source-of-truth side):

```scala
EntityDef.aggregate: Option[AggregateDef]
EntityDef.view: Option[ViewDef]
```

Minimum fields:

```scala
AggregateDef(creates, commands, state, invariants)
ViewDef(attributes, queries)
```

## Aggregate Create / Command Semantics

Aggregate creation is a distinct Aggregate Root capability.

- `CREATE` defines how a new Aggregate Root is created.
- `COMMAND` defines business operations against an existing Aggregate Root.
- `STATE` defines aggregate state exposed to invariant and command handling.
- `INVARIANT` defines rules that must hold for valid aggregate state.

The runtime metadata shape is:

```scala
AggregateDefinition(
  creates = Vector(AggregateCreateDefinition(...)),
  commands = Vector(AggregateCommandDefinition(...))
)
```

`AggregateCreateDefinition.name` names an Aggregate Root creation operation.
`AggregateCommandDefinition.name` names a business command operation on an
existing Aggregate Root.

Operations are external entry points. Their implementations are expected to call
Aggregate Root capabilities:

- create operations call the Aggregate Root `create` function or equivalent
  factory method.
- command operations call Aggregate Root domain methods.
- neither Web nor Management Console creates or patches aggregate state
  directly.

This keeps application logic centered on Aggregate Root behavior while allowing
transport layers, Form Web, and management tooling to discover available
actions from metadata.

Ownership note:
- CML parser/modeler source-of-truth is Cozy.
- CNCF defines runtime contract consumption boundaries only.
- No parallel/competing CML grammar is allowed in CNCF.

## CQRS Boundary Constraints

- command side (aggregate) and read side (view) must be separated.
- cross-side mutation is prohibited by contract.
- event schema must not depend on view shape.

## Runtime Compatibility Note

AV-01 is compatible with existing execution path:

`ActionCall -> Task -> Job`

AV-01 does not change runtime orchestration.

## Out of Scope

- Entity -> Aggregate/View generation implementation (AV-02)
- synchronization runtime implementation (AV-03)
- projection/meta surface implementation (AV-04)
- full executable spec closure (AV-05)
