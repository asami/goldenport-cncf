# Phase 5 — Event Lifecycle and Policy Integration

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 5.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Establish Phase-5 event lifecycle envelope linked to state transition execution.
- Define CNCF EventBus/EventStore integration boundary on top of existing runtime model.
- Introduce persistence/replay baseline for persistent events and explicit handling for ephemeral events.
- Define two persistence lanes for events:
  - transactional lane: events persisted atomically with domain data updates in the same transaction.
  - non-transactional lane: events (for example error/incident events) emitted and managed in EventStore independently from domain update transaction.
- Add policy-driven transition/event visibility checks for introspection and execution surfaces.
- Keep core/CNCF boundary discipline from Phase 4 (core primitives remain authoritative).

## 3. Non-Goals

- No full event sourcing migration in this phase.
- No distributed workflow/saga engine implementation.
- No redesign of core state machine primitives.
- No broad persistence architecture rewrite beyond EventStore baseline.

## 4. Current Work Stack

- A (DONE): Define transition lifecycle event envelope and runtime emission points.
- B (DONE): Implement EventStore baseline (append/query/replay contract) with transaction consistency rule.
- C (DONE): Implement EventBus dispatch/subscription path aligned with ActionCall-centric execution.
- D (DONE): Add policy-driven visibility/privilege checks for transition/event surfaces.
- E (ACTIVE): Prepare executable specifications for event lifecycle, replay behavior, and policy checks.

Resume hint:
- Start from lifecycle envelope definition first, because EventStore/EventBus contracts depend on emitted event shape and timing.

## 5. Development Items

- [x] EV-01: Define canonical transition lifecycle event envelope (`before/after transition`, failure path, metadata envelope).
- [x] EV-02: Define and implement EventStore baseline contract (append/load/query/replay) with same-transaction persistence rule.
- [x] EV-03: Define and implement EventBus publish/dispatch/subscription path with deterministic execution ordering.
- [x] EV-04: Define policy-driven visibility and privilege checks for transition/event projection and execution entry points.
- [ ] EV-05: Add executable specifications for lifecycle emission, persistence modes (`persistent/ephemeral`), replay, and policy enforcement.

## 6. Input from Previous Phase

- NP-401 (from Phase 4): Link state transition lifecycle to event emission envelope.
- NP-402 (from Phase 4): Add policy-driven transition visibility and privilege checks.

These items are now in-scope for Phase 5 as EV-01 and EV-04.

## 7. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-4.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/phase-4-close-handoff-2026-03-20.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-mechanism-design.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-store-design.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/statemachine-boundary-contract.md`
