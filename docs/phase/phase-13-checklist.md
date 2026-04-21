# Phase 13 — Event Mechanism Extension Checklist

This document contains detailed task tracking and execution decisions
for Phase 13.

It complements the summary-level phase document (`phase-13.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-13.md`) holds summary only.
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

Phase 13 implementation proceeds in this order:

1. EVX-01 fixes subsystem-level ownership and wiring semantics first.
2. EVX-02 makes subscription registration a normal component startup path.
3. EVX-03 stabilizes the dispatch contract on top of the fixed wiring path.
4. EVX-04 clarifies continuation/job semantics on top of the dispatch contract.
5. EVX-05 adds internal event-aware await support for local verification paths.
6. EVX-06 adds operator/developer observability for the collaboration path.
7. EVX-07 closes the phase with executable specification protection.

This order minimizes churn in runtime semantics and keeps tests aligned with
the actual event collaboration contract.

---

## EVX-01: Subsystem-Level Shared Event Wiring

Status: DONE

### Objective

Make subsystem-owned event facilities explicit and deterministic so components
inside the same subsystem can publish and receive events through a shared
runtime context.

### Detailed Tasks

- [x] Make subsystem-level `EventBus` ownership explicit.
- [x] Make subsystem-level `EventReception` ownership explicit.
- [x] Ensure component bootstrap can register subscriptions into subsystem scope.
- [x] Define lifecycle and visibility of subsystem event facilities.

### Expected Outcome

- Components in the same subsystem can publish and receive events through a
  shared subsystem event context.

### Inputs

- `docs/journal/2026/04/event-mechanism-extension-work-items.md`
- `docs/journal/2026/03/event-mechanism-design.md`
- `docs/notes/event-reception-latest-processing-spec.md`
- `docs/notes/event-reception-policy-selection.md`

---

## EVX-02: Component Subscription Bootstrap

Status: DONE

### Objective

Make subscription registration part of normal component startup rather than an
ad hoc manual path.

### Detailed Tasks

- [x] Define how component descriptors / generated definitions contribute subscriptions.
- [x] Define handwritten registration path for custom factories.
- [x] Ensure duplicate registration and ordering behavior are deterministic.
- [x] Clarify authorization boundary for subscription registration.

### Expected Outcome

- Event-capable components do not require ad hoc manual wiring for normal
  subsystem collaboration.

---

## EVX-03: Event-to-Action Dispatch Contract

Status: DONE

### Objective

Refine the contract by which a received event triggers an action call.

### Detailed Tasks

- [x] Confirm canonical target resolution rules.
- [x] Confirm event payload to action input mapping rules.
- [x] Document selector behavior and failure handling.
- [x] Clarify same-component versus cross-component dispatch semantics.

### Expected Outcome

- Event-triggered action execution becomes predictable and testable.

---

## EVX-04: Event Continuation and Job Integration

Status: DONE

### Objective

Promote existing continuation behavior into a clearer execution model.

### Detailed Tasks

- [x] Formalize `SameJob` versus `NewJob`.
- [x] Define job metadata inherited from event context.
- [x] Define correlation / causation / parent-job propagation.
- [x] Define failure, retry, and dead-letter expectations for event-triggered jobs within the Phase 13 boundary.

### Expected Outcome

- Event reception can start follow-up work under job management in a
  consistent way.

---

## EVX-05: Internal Await Based on Event Completion

Status: DONE

### Objective

Replace ad hoc demo/test waiting with event-aware waiting support.

### Detailed Tasks

- [x] Keep the current polling helper internal only.
- [x] Add event-aware internal await utilities where practical.
- [x] Define minimal contract for waiting on event visibility or event-derived completion.
- [x] Apply it first to tests, executable specs, and demos.

### Expected Outcome

- Local verification paths can wait for event-driven completion without fixed sleeps.

---

## EVX-06: Observability and Inspection

Status: DONE

### Objective

Expose enough visibility to debug event-driven collaboration.

### Detailed Tasks

- [x] Ensure emitted events can be inspected per job / per correlation.
- [x] Confirm builtin event component coverage for event-driven paths.
- [x] Add traces for subscription matching and dispatch outcome.
- [x] Define minimum operator-facing diagnostics for event dispatch failure.

### Expected Outcome

- Event-driven execution is observable enough for development and operations.

---

## EVX-07: Executable Specifications

Status: DONE

### Objective

Protect the event extension path with focused regression coverage.

### Detailed Tasks

- [x] Add subsystem-level component-to-component event collaboration test.
- [x] Add same-job continuation test.
- [x] Add new-job continuation test.
- [x] Add action-call wait/visibility test without fixed sleep.
- [x] Add authorization and failure-path tests for event dispatch.

### Expected Outcome

- Event collaboration becomes a protected framework capability rather than an
  experimental behavior.

---

## Deferred / Next Phase Candidates

- Authentication/authorization expansion on top of the event-capable runtime.
- Broader metrics/OpenTelemetry integration.
- External or multi-subsystem event transport.

---

## Completion Check

Phase 13 is complete when:

- [x] EVX-01 through EVX-07 are marked DONE.
- [x] `phase-13.md` summary checkboxes are aligned.
- [x] No item remains ACTIVE or SUSPENDED.
