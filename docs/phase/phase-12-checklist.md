# Phase 12 — Event Mechanism Extension Checklist

This document contains detailed task tracking and execution decisions
for Phase 12.

It complements the summary-level phase document (`phase-12.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-12.md`) holds summary only.
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

Phase 12 implementation proceeds in this order:

1. EM-01 (subsystem-level event wiring) must be fixed first.
2. EM-02 (Component subscription bootstrap) is implemented on top of shared event facilities.
3. EM-03 (dispatch and continuation/job semantics) is tightened after registration paths are stable.
4. EM-04 (internal await support) is added after the canonical collaboration path exists.
5. EM-05 (Executable Specifications and observability coverage) is expanded alongside EM-01 to EM-04 and finalized last.

This order minimizes churn in event collaboration contracts and test infrastructure.

---

## EM-01: Subsystem-Owned Event Wiring

Status: ACTIVE

### Objective

Make subsystem-level ownership of shared event facilities explicit so
Components in the same subsystem collaborate through a canonical shared
event context.

### Detailed Tasks

- [ ] Define where subsystem-owned `EventBus` is held.
- [ ] Define where subsystem-owned `EventReception` is held.
- [ ] Clarify lifecycle and visibility of shared event facilities during subsystem bootstrap.
- [ ] Ensure Component runtime can publish into subsystem-shared event context.
- [ ] Ensure shared facilities are not recreated ad hoc per Component.

### Inputs

- `docs/journal/2026/04/event-mechanism-extension-work-items.md`
- `src/main/scala/org/goldenport/cncf/event/EventBus.scala`
- `src/main/scala/org/goldenport/cncf/event/EventReception.scala`

---

## EM-02: Component Subscription Bootstrap

Status: PLANNED

### Objective

Make subscription registration part of normal Component startup and subsystem assembly.

### Detailed Tasks

- [ ] Define how Component descriptors or generated definitions contribute subscriptions.
- [ ] Define handwritten registration path for custom Component factories.
- [ ] Ensure duplicate registration behavior is deterministic.
- [ ] Ensure registration order is deterministic.
- [ ] Clarify authorization boundary for registration.

### Inputs

- `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala`
- `src/main/scala/org/goldenport/cncf/event/EventReception.scala`

---

## EM-03: Event-to-Action Dispatch and Continuation/Job Semantics

Status: PLANNED

### Objective

Stabilize the contract by which a received event triggers an action call
and continues in the same job or a new job.

### Detailed Tasks

- [ ] Freeze canonical target resolution rules for same-Component and cross-Component dispatch.
- [ ] Freeze payload-to-action input mapping rules.
- [ ] Confirm selector behavior and deterministic mismatch handling.
- [ ] Formalize `SameJob` versus `NewJob`.
- [ ] Define propagation rules for correlation, causation, parent-job, and security metadata.
- [ ] Define failure handling expectations for dispatch failure and authorization denial.

### Inputs

- `src/main/scala/org/goldenport/cncf/event/EventReception.scala`
- `src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala`

---

## EM-04: Internal Await Support for Event Completion

Status: PLANNED

### Objective

Replace ad hoc sleeps/polling in tests and demos with internal event-aware waiting support.

### Detailed Tasks

- [ ] Keep existing polling helpers internal only.
- [ ] Add minimal internal await utility for event visibility or event-derived completion.
- [ ] Apply the await utility first to tests, executable specs, and demos.
- [ ] Avoid introducing a public application-facing async API in this phase.

### Inputs

- `docs/journal/2026/04/event-mechanism-extension-work-items.md`
- existing importer/event demo test support as reference for internal waiting only

---

## EM-05: Executable Specifications and Observability Coverage

Status: PLANNED

### Objective

Protect subsystem event collaboration as a framework capability with
Executable Specifications and minimum operator-facing diagnostics.

### Detailed Tasks

- [ ] Add subsystem-level Component-to-Component event collaboration spec.
- [ ] Add `SameJob` continuation spec.
- [ ] Add `NewJob` continuation spec.
- [ ] Add event-driven completion/wait spec without fixed sleep.
- [ ] Add authorization and failure-path specs for event dispatch.
- [ ] Add traces or diagnostics for subscription match and dispatch outcome.

---

## Deferred / Next Phase Candidates

- Inter-subsystem or external service-bus transport.
- Public application-facing async abstraction.
- Retry/dead-letter policy formalization.

---

## Completion Check

Phase 12 is complete when:

- EM-01 through EM-05 are marked DONE.
- `phase-12.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
