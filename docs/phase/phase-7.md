# Phase 7 — Aggregate and View Completion

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 7.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Finalize Aggregate model boundaries and runtime execution contract.
- Finalize View model boundaries and read-side query contract.
- Establish explicit CQRS separation between command (aggregate) and query (view) paths.
- Adopt single source of truth:
  - CML `Entity` definition is the canonical source.
  - Aggregate and View are generated projections from Entity.
- Extend CML/AST model for Aggregate/View definitions.
- Define and implement aggregate-to-view synchronization rules (event bridge, eventual consistency, idempotent projection).
- Align projection/meta surfaces with finalized aggregate/view model.
- Add executable specifications for aggregate/view consistency, determinism, generation correctness, and failure paths.

## 3. Non-Goals

- No distributed read-model replication.
- No workflow/orchestration engine introduction.
- No security model redesign (handled in dedicated security phase).
- No web dashboard/console implementation in this phase.

## 4. Current Work Stack

- A (DONE): Finalize semantic contract and CML/AST boundary (Entity -> Aggregate/View).
- B (ACTIVE): Implement CML-based Aggregate/View generation.
- C (SUSPENDED): Define aggregate-to-view runtime synchronization and consistency rules.
- D (SUSPENDED): Align projection/meta surfaces with generated model.
- E (SUSPENDED): Add executable specifications and regression coverage.

Resume hint:
- Finalize semantic and AST boundary first; generation/runtime contracts depend on stable model shape.

## 5. Development Items

- [x] AV-01: Define and freeze semantic contract and CML/AST boundary (`EntityDef` + `AggregateDef` + `ViewDef`).
- [ ] AV-02: Implement CML-based generation of Aggregate/View models from Entity definition.
- [ ] AV-03: Implement aggregate-to-view synchronization policy (event bridge, timing, ordering, idempotency, failure behavior).
- [ ] AV-04: Align introspection/projection surfaces (`meta.*`, schema/help surfaces) with generated aggregate/view model.
- [ ] AV-05: Add executable specifications for CQRS separation, generation correctness, deterministic behavior, and failure paths.

## 6. Inputs from Previous Phases

- Phase 6 Job Management CQRS closure and contracts are prerequisites.
- Phase 5 Event foundations are prerequisites for synchronization/event propagation rules.

## 7. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/strategy/cncf-development-strategy.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-6.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-6-checklist.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-5.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/cncf-architecture-overview.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/aggregate-view-semantic-boundary.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/aggregate-view-design-handoff.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/av-01-cozy-escalation-2026-03-21.md`
