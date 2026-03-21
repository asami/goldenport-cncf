# Phase 7 — Aggregate and View Completion Checklist

This document contains detailed task tracking and execution decisions
for Phase 7.

It complements the summary-level phase document (`phase-7.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-7.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]`
  in the phase document.

---

## Status Semantics

- ACTIVE: currently being worked on (only one item may be ACTIVE).
- SUSPENDED: intentionally paused with a clear resume point.
- PLANNED: planned but not started.
- DONE: handling within this phase is complete.

---

## Recommended Work Procedure

Phase 7 implementation proceeds in this order:

1. AV-01 (semantic + AST boundary) must be fixed first.
2. AV-02 (CML-based generation) is implemented on top of finalized model contracts.
3. AV-03 (aggregate-to-view synchronization) is fixed after AV-01/AV-02 contracts stabilize.
4. AV-04 (projection/meta alignment) is applied after generated model boundaries are stable.
5. AV-05 (executable specs) is finalized last as closure criteria.

This order minimizes churn in CQRS responsibilities and projection contracts.

---

## AV-01: Semantic Model and CML/AST Boundary

Status: DONE

### Objective

Define and freeze canonical semantic model and AST boundary:

- Entity as single source of truth
- Aggregate as command-side projection
- View as read-side projection

### Detailed Tasks

- [x] Define canonical Aggregate contract:
  - command input
  - invariant enforcement
  - output: `(new state, events)`
- [x] Define canonical View contract:
  - event-driven projection
  - rebuildability from event stream
- [x] Extend Entity AST shape:
  - `EntityDef.aggregate: Option[AggregateDef]`
  - `EntityDef.view: Option[ViewDef]`
- [x] Define `AggregateDef` and `ViewDef` minimum fields.
- [x] Freeze deterministic constraints:
  - aggregate purity/determinism
  - view reconstructability
  - event independence from view
- [x] Define command/read boundary to prevent cross-side mutation.
- [x] Confirm runtime boundary compatibility with `ActionCall -> Task -> Job`.
- [x] Issue Cozy escalation note for CML/AST source-of-truth alignment.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/cncf-architecture-overview.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-6.md`

---

## AV-02: CML-based Aggregate/View Generation

Status: ACTIVE

### Objective

Generate Aggregate and View from Entity definition and replace manual-only model path.

### Detailed Tasks

- [ ] Implement generation rule:
  - `Aggregate` defined -> generate `aggregate/*Aggregate.scala`
  - `View` defined -> generate `view/*View.scala`
- [ ] Define generated naming contract (`Order -> OrderAggregate / OrderView`).
- [ ] Define generated output package/layout policy.
- [ ] Ensure generated code compiles without manual patching.
- [ ] Add migration note from manual implementations to generated source of truth.

---

## AV-03: Aggregate-to-View Synchronization Policy

Status: PLANNED

### Objective

Implement synchronization policy between aggregate updates and view/read model updates.

### Detailed Tasks

- [ ] Define command flow:
  - Command -> Aggregate -> new state + Event(s)
- [ ] Define projection flow:
  - Event -> Projection -> View update
- [ ] Define eventual consistency level and delivery assumptions.
- [ ] Define deterministic ordering and idempotent projection rules.
- [ ] Define synchronization failure policy (retry, skip, dead-letter boundary if any).
- [ ] Align execution with Job/Task model (`ActionCall -> Task -> Job`).

---

## AV-04: Projection/Meta Surface Alignment

Status: PLANNED

### Objective

Align introspection/projection surfaces with finalized aggregate/view model boundaries.

### Detailed Tasks

- [ ] Align `meta.*` aggregate/view exposure with runtime authoritative API set.
- [ ] Align schema/help/openapi projections with finalized command/read contracts.
- [ ] Reflect generated Aggregate/View structure in introspection output.
- [ ] Remove ambiguous or duplicate projection entries after boundary freeze.
- [ ] Ensure projection output is deterministic and testable.

---

## AV-05: Executable Specifications (CQRS Completion Slice)

Status: PLANNED

### Objective

Add executable specifications to close Phase 7 with behavior-level guarantees.

### Detailed Tasks

- [ ] Add Given/When/Then specs for Aggregate contract (`handle -> state + events`) and invariants.
- [ ] Add specs for View contract (`event -> projection`) and rebuildability.
- [ ] Add generation specs for Entity -> Aggregate/View output correctness.
- [ ] Add specs for synchronization ordering/eventual consistency/idempotent projection.
- [ ] Add specs for synchronization failure and retry/idempotency behavior.
- [ ] Add projection/meta alignment regression specs for aggregate/view surfaces.
- [ ] Add integrated CQRS path regression specs across command->event->projection->query flow.

---

## Deferred / Next Phase Candidates

- Distributed read-model replication and consistency controls.
- Multi-tenant CQRS governance.
- Advanced query optimization and cache invalidation strategy.

---

## Completion Check

Phase 7 is complete when:

- AV-01 through AV-05 are marked DONE.
- `phase-7.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
