# Phase 12 — Event Mechanism Extension

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 12.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Extend the existing CNCF event mechanism into a standard subsystem runtime capability.
- Make subsystem-owned event wiring explicit.
- Make Component subscription bootstrap part of normal startup.
- Stabilize event-triggered action dispatch across Components inside the same subsystem.
- Add internal event-aware waiting support for tests, demos, and local verification.
- Tighten observability and failure diagnostics for event-driven collaboration.

## 3. Non-Goals

- No external service-bus transport in this phase.
- No public application-facing async abstraction finalization.
- No distributed delivery guarantee or global saga/workflow system.
- No redesign of the existing EventStore persistence baseline outside what event collaboration requires.

## 4. Current Work Stack

- A (ACTIVE): EM-01 — Establish subsystem-level shared event wiring.
- B (SUSPENDED): EM-02 — Add Component subscription bootstrap and deterministic registration.
- C (SUSPENDED): EM-03 — Stabilize event-to-action dispatch and continuation/job semantics.
- D (SUSPENDED): EM-04 — Add internal await support and executable specifications.

Current note:
- EventBus, EventReception, continuation modes, and standard event attributes already exist.
- This phase extends those foundations into subsystem-internal Component collaboration as a normal runtime path.

## 5. Development Items

- [ ] EM-01: Define and implement subsystem-owned event wiring.
- [ ] EM-02: Add Component subscription bootstrap and registration rules.
- [ ] EM-03: Stabilize event-to-action dispatch and continuation/job integration.
- [ ] EM-04: Add internal await support for event-driven completion.
- [ ] EM-05: Add executable specifications and observability coverage for subsystem event collaboration.

## 6. Next Phase Candidates

- NP-1201: Inter-subsystem event transport and external bus integration.
- NP-1202: Public async abstraction for application-facing event completion.
- NP-1203: Retry/dead-letter policy formalization for event-triggered jobs.

## 7. References

- `docs/journal/2026/04/event-mechanism-extension-work-items.md`
- `docs/phase/phase-5.md`
- `src/main/scala/org/goldenport/cncf/event/EventBus.scala`
- `src/main/scala/org/goldenport/cncf/event/EventReception.scala`
- `src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala`
- `docs/phase/phase-12-checklist.md`
