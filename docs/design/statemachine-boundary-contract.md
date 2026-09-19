# State Machine Boundary Contract

Status: accepted Phase 63 boundary

## Purpose

Define canonical boundaries for state machine handling across DSL, AST, core model, and CNCF runtime integration.

## Layered Responsibility

Processing flow is fixed:

non-Workflow CML declaration
  -> parsed AST
  -> closed normalized StateMachine model
  -> core model (`org.goldenport.statemachine`)
  -> CNCF adapter

Responsibilities:

- CML: textual non-Workflow declaration surface only.
- AST/normalizer: parsed source to the closed typed model and deterministic
  diagnostics only.
- core model: canonical pure semantics and deterministic transition decision.
- CNCF: explicit binding/adaptation boundary; it does not redefine selection.
- Workflow: a separate orchestration owner. Cozy's Workflow grammar and state
  are not StateMachine-normalization inputs or outputs.

The normative normalized-model, identity, predicate, compatibility, topology,
and diagnostic rules are in
`docs/spec/cml-statemachine-normalization-contract.md`.

## Core Contract (`org.goldenport.statemachine`)

Canonical primitives:

- `StateMachine`
- `State`
- `Transition`
- `Guard`
- `Effect`
- `StateMachineIdentity` and `TransitionIdentity`
- `TransitionPlan` and `TransitionSelectionOutcome`

The CML semantic-model boundary owns the remaining closed declaration types:
state, trigger, guard, and action identities; `PredicateProgram`; and the
explicit trigger context. Those are declaration semantics, not Entity IDs or
runtime object identities.

Core invariants:

- no CNCF dependency
- no runtime context dependency
- deterministic transition ordering by `(priority asc, declarationOrder asc)`
- `orderedCanonical` / `decideCanonical` reject absent, inconsistent, or
  duplicate canonical declaration identity; legacy `ordered` / `decide` retain
  only their existing collection-order compatibility behavior
- `guard=false` is non-match
- guard evaluation failure is propagated as failure
- canonical selection returns an unexecuted candidate-state/effect plan or an
  explicit no-match outcome
- no raw-expression evaluation or runtime access through the pure contract

## CNCF Adapter Contract

CNCF must not redefine core selection or normalize arbitrary source text.

CNCF responsibilities:

- adapt typed trigger-context values to the core contract
- resolve named guards only through `GuardBindingResolver`
- represent legacy raw expression as an explicit non-admitted compatibility
  result, never an MVEL fallback on the new path
- defer effect execution and UnitOfWork lifecycle changes to Phase 63.1

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
- generation and execution planner enrichment in Phase 63.1
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
- timer and scheduling semantics are not implied by state machine or workflow
  integration

For the execution-platform boundary, including the Pareto 80/20
product-boundary rule, see:

- `docs/design/execution-platform-boundary.md`
- `docs/design/timer-scheduling-boundary.md`

## Non-Goals

- full workflow engine orchestration or CML Workflow grammar inside the state
  machine layer
- persistence redesign
- core primitive duplication inside CNCF
