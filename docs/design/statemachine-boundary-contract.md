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

## Non-Goals

- workflow engine orchestration
- persistence redesign
- core primitive duplication inside CNCF
