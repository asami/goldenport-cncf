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

Status: DONE

### Objective

Generate Aggregate and View from Entity definition and replace manual-only model path.

### Detailed Tasks

- [x] Implement generation rule:
  - `Aggregate` defined -> generate `aggregate/*Aggregate.scala`
  - `View` defined -> generate `view/*View.scala`
- [x] Define generated naming contract (`Order -> OrderAggregate / OrderView`).
- [x] Define generated output package/layout policy.
- [x] Ensure generated code compiles without manual patching.
- [x] Add migration note from manual implementations to generated source of truth.

### Progress Note (2026-03-21)

- AV-02 connection slice is reported as implemented across Cozy/SimpleModeler/CNCF:
  - Cozy: aggregate/view metadata generation in `Modeler`
  - SimpleModeler: metadata propagation to generated `DomainComponent`
  - CNCF: component metadata hooks and bootstrap registration for aggregate/view
- Reported verification:
  - CNCF: `ComponentFactoryAggregateViewBootstrapSpec` pass
  - Cozy: `ModelerGenerationSpec` pass
  - SimpleModeler: `publishLocal` pass
- Reported commits:
  - simple-modeler: `fa84578`
  - cozy: `3421361`
  - cloud-native-component-framework: `16c7d46`

---

## AV-03: Aggregate-to-View Synchronization Policy

Status: DONE

### Objective

Implement synchronization policy between aggregate updates and view/read model updates.

### Detailed Tasks

- [x] Define command flow:
  - Command -> Aggregate -> new state + Event(s)
- [x] Define projection flow:
  - Event -> Projection -> View update
- [x] Define eventual consistency level and delivery assumptions.
- [x] Define deterministic ordering and idempotent projection rules.
- [x] Define synchronization failure policy (retry, skip, dead-letter boundary if any).
- [x] Align execution with Job/Task model (`ActionCall -> Task -> Job`).

---

## AV-04: Projection/Meta Surface Alignment

Status: DONE

### Objective

Align introspection/projection surfaces with finalized aggregate/view model boundaries.

### Detailed Tasks

- [x] Align `meta.*` aggregate/view exposure with runtime authoritative API set.
- [x] Align schema/help/openapi projections with finalized command/read contracts.
- [x] Reflect generated Aggregate/View structure in introspection output.
- [x] Remove ambiguous or duplicate projection entries after boundary freeze.
- [x] Ensure projection output is deterministic and testable.

---

## AV-05: Executable Specifications (CQRS Completion Slice)

Status: DONE

### Objective

Add executable specifications to close Phase 7 with behavior-level guarantees.

### Detailed Tasks

- [x] Add Given/When/Then specs for Aggregate contract (`handle -> state + events`) and invariants.
- [x] Add specs for View contract (`event -> projection`) and rebuildability.
- [x] Add generation specs for Entity -> Aggregate/View output correctness.
- [x] Add specs for synchronization ordering/eventual consistency/idempotent projection.
- [x] Add specs for synchronization failure and retry/idempotency behavior.
- [x] Add projection/meta alignment regression specs for aggregate/view surfaces.
- [x] Add integrated CQRS path regression specs across command->event->projection->query flow.

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
