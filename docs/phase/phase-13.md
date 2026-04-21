# Phase 13 — Event Mechanism Extension

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 13.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Extend the existing CNCF event mechanism into a standard subsystem-internal
  collaboration path.
- Make subsystem-level event wiring explicit and deterministic.
- Make component subscription bootstrap part of normal component startup.
- Stabilize event-to-action dispatch semantics across same-component and
  cross-component paths.
- Promote event continuation and job integration into a clearer execution model.
- Add internal event-aware await support for tests, executable specifications,
  and demos.
- Add focused executable specifications and observability coverage for the
  event-driven collaboration path.

## 3. Non-Goals

- No inter-subsystem transport over an external service bus.
- No public application-facing async abstraction redesign in this phase.
- No replacement of the existing `EventBus` / `EventReception` / `Job`
  baseline with a competing mechanism.
- No finalization of external delivery protocols or API gateway behavior.

## 4. Current Work Stack

- A (ACTIVE): EVX-01 — Define subsystem-level shared event wiring baseline.
- B (SUSPENDED): EVX-02 — Make component subscription bootstrap a standard startup path.
- C (SUSPENDED): EVX-03 — Stabilize event-to-action dispatch contract.
- D (SUSPENDED): EVX-04 — Formalize continuation/job integration semantics.
- E (SUSPENDED): EVX-05 — Add internal event-aware await support.
- F (SUSPENDED): EVX-06 — Add observability/inspection coverage for event-driven collaboration.
- G (SUSPENDED): EVX-07 — Protect the extension path with executable specifications.

Current note:
- The framework already has `EventBus`, `EventReception`, continuation modes,
  and builtin event/job-control inspection components.
- This phase extends those foundations into the canonical subsystem-level
  event collaboration model rather than inventing a new event mechanism.

## 5. Development Items

- [ ] EVX-01: Define and implement subsystem-level shared event wiring.
- [ ] EVX-02: Define and implement component subscription bootstrap.
- [ ] EVX-03: Refine event-to-action dispatch contract.
- [ ] EVX-04: Formalize event continuation and job integration semantics.
- [ ] EVX-05: Add internal await support based on event completion.
- [ ] EVX-06: Add observability and inspection coverage for event-driven collaboration.
- [ ] EVX-07: Add executable specifications for the event extension path.

## 6. Next Phase Candidates

- NP-1301: Authentication and authorization expansion on top of the event-capable runtime.
- NP-1302: Broader metrics and OpenTelemetry integration for event/job flows.
- NP-1303: Inter-subsystem or external event transport strategy.

## 7. References

- `docs/strategy/cncf-development-strategy.md`
- `docs/journal/2026/04/event-mechanism-extension-work-items.md`
- `docs/journal/2026/03/event-mechanism-design.md`
- `docs/notes/event-reception-latest-processing-spec.md`
- `docs/notes/event-reception-policy-selection.md`
- `docs/phase/phase-13-checklist.md`
