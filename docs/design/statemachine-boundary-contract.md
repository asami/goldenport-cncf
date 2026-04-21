# State Machine Boundary Contract

Status: draft

## Purpose

Define canonical boundaries for state machine handling across DSL, AST, core model, and CNCF runtime integration.

## Layered Responsibility

Processing flow is fixed:

DSL
  -> AST
  -> core model (`org.goldenport.statemachine`)
  -> CNCF runtime adapter/executor

Responsibilities:

- DSL: textual declaration surface only.
- AST: parsed/normalized intermediate representation only.
- core model: canonical pure semantics and deterministic transition decision.
- CNCF: binding resolution, runtime context integration, effect execution lifecycle.

## Core Contract (`org.goldenport.statemachine`)

Canonical primitives:

- `StateMachine`
- `State`
- `Transition`
- `Guard`
- `Effect`

Core invariants:

- no CNCF dependency
- no runtime context dependency
- deterministic transition ordering by `(priority asc, declarationOrder asc)`
- `guard=false` is non-match
- guard evaluation failure is propagated as failure

## CNCF Adapter Contract

CNCF must not redefine core primitives.

CNCF responsibilities:

- resolve named guards (`GuardBindingResolver`)
- resolve named effects (`EffectBindingResolver`)
- adapt core `Effect` to CNCF execution plan (`EffectAdapter`)
- execute planned actions within existing runtime hooks

## Determinism and Error Semantics

Determinism:

- smaller `priority` wins
- same `priority` uses `declarationOrder`

Error semantics:

- guard returns `false` -> continue candidate scan
- guard returns failure -> stop and return failure

## Extension Points

Allowed extensions:

- additional resolver strategies (scope/FQ/global)
- execution planner enrichment (entry/exit/transition phases)
- introspection projection adapters
- workflow-facing adapters that consume state machine context without moving workflow ownership into the state machine layer

## Relationship to WorkflowEngine

State machine and workflow are separate layers.

State machine owns domain transition semantics.
WorkflowEngine may consume event plus entity status plus state machine context
in order to decide the next action, but workflow progression is not owned by
the state machine layer itself.

This means:

- state machine stays the semantic source/planner
- workflow remains an orchestration layer outside the state machine boundary
- JobEngine remains the execution substrate

For the execution-platform boundary, including the Pareto 80/20
product-boundary rule, see:

- `docs/design/execution-platform-boundary.md`

## Non-Goals

- full workflow engine orchestration inside the state machine layer
- persistence redesign
- core primitive duplication inside CNCF
