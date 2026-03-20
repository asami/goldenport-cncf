# Phase 5 — Event Lifecycle and Policy Integration Checklist

This document contains detailed task tracking and execution decisions
for Phase 5.

It complements the summary-level phase document (`phase-5.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-5.md`) holds summary only.
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

Phase 5 implementation proceeds in this order:

1. EV-01 (lifecycle envelope) must be fixed first.
2. EV-02 (EventStore baseline) is implemented next with transaction consistency.
3. EV-03 (EventBus dispatch path) is implemented after EventStore/event envelope contract stabilizes.
4. EV-04 (policy visibility/privilege) is applied once projection/execution surfaces are explicit.
5. EV-05 (executable specs) is expanded alongside EV-02 to EV-04 and finalized last.

This order minimizes churn in runtime contracts and observation semantics.

---

## EV-01: Transition Lifecycle Event Envelope

Status: DONE

### Objective

Define canonical event envelope and emission timing for state transition lifecycle.

### Detailed Tasks

- [x] Define lifecycle event taxonomy (`before-transition`, `after-transition`, `transition-failed`).
- [x] Define canonical envelope fields (event id/name/kind, timestamp, correlation metadata, transition metadata).
- [x] Define required metadata mapping from runtime context without parsing/interpreting CanonicalId.
- [x] Define emission points in runtime path with deterministic ordering guarantees.
- [x] Define failure-path emission semantics and consequence mapping.
- [x] Publish/align normative design note references used by implementation.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-mechanism-design.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-4.md` (NP-401)

---

## EV-02: EventStore Baseline Contract and Transaction Consistency

Status: DONE

### Objective

Implement EventStore baseline (`append/load/query/replay`) with explicit transaction consistency rules.

### Detailed Tasks

- [x] Freeze EventStore interface and result/error semantics.
- [x] Define event record schema (id, kind, payload, attributes, status, createdAt, persistent).
- [x] Implement transactional append path: event persisted atomically with domain data update in the same transaction.
- [x] Implement non-transactional append path: event persisted/managed independently from domain update transaction (for example error events).
- [x] Define and implement query filters and deterministic ordering.
- [x] Implement replay entry with idempotent reprocessing assumptions documented.
- [x] Ensure same-transaction atomicity rule for transactional lane between domain update and event append.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-store-design.md`

---

## EV-03: EventBus Publish/Dispatch/Subscription Path

Status: DONE

### Objective

Implement EventBus runtime flow aligned with ActionCall-centric execution.

### Detailed Tasks

- [x] Freeze `publish/register` contract and subscription model.
- [x] Implement subscription resolution and deterministic dispatch ordering.
- [x] Integrate dispatch path with ActionCall execution model.
- [x] Define sync dispatch default policy and async/job extension point boundary.
- [x] Ensure ephemeral events dispatch without persistence.
- [x] Map dispatch failures to consequence/observation taxonomy consistently.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/event-mechanism-design.md`

---

## EV-04: Policy-Driven Visibility and Privilege Checks

Status: DONE

### Objective

Apply policy/privilege checks to transition/event visibility and execution-facing surfaces.

### Detailed Tasks

- [x] Define policy decision points for introspection projection surfaces.
- [x] Define policy decision points for execution entry points (publish/dispatch/replay).
- [x] Implement privilege checks using existing execution context boundaries.
- [x] Define default-deny/default-allow behavior per surface and document rationale.
- [x] Add deterministic error/observation mapping for policy denial.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-4.md` (NP-402)

---

## EV-05: Executable Specifications (Event Lifecycle / Replay / Policy)

Status: DONE

### Objective

Add executable specifications covering lifecycle emission, persistence modes, replay, and policy behavior.

### Detailed Tasks

- [x] Add Given/When/Then specs for lifecycle emission sequence and metadata integrity.
- [x] Add specs for persistent vs ephemeral event handling.
- [x] Add specs for replay behavior and deterministic re-dispatch ordering.
- [x] Add specs for dispatch failure and transition failure emission paths.
- [x] Add specs for policy visibility/privilege acceptance and denial paths.
- [x] Add regression specs for state-machine transition -> event lifecycle linkage.

---

## Deferred / Next Phase Candidates

- Distributed event bus transport and remote subscriber delivery guarantees.
- Full event sourcing adoption decision and migration strategy.
- Saga/compensation orchestration beyond baseline hooks.

---

## Completion Check

Phase 5 is complete when:

- EV-01 through EV-05 are marked DONE.
- `phase-5.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
