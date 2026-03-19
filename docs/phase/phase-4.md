# Phase 4 — State Machine Foundation

status = active

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 4.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Introduce a first-class state machine model for CNCF domain subsystems and components.
- Define canonical representation for state / transition / guard / effect.
- Provide introspection outputs for state machine structures (for example transition table/diagram source).
- Add runtime hooks to validate transitions during execution.
- Build core state machine primitives in `org.goldenport.statemachine` first, then consume/extend from CNCF only where required.

## 3. Non-Goals

- No workflow engine implementation.
- No event sourcing mandate.
- No persistence architecture redesign in this phase.
- No redefinition of core state machine primitives inside CNCF.

## 4. Current Work Stack

- A (ACTIVE): Define canonical state machine model and runtime boundary.
- B (SUSPENDED): Implement transition validation hook integration into existing execution path.
- C (SUSPENDED): Add state machine introspection projection surface.
- D (SUSPENDED): Prepare executable specifications for transition semantics and guard behavior.

## 5. Development Items

- [ ] SM-01: Define state / transition / guard / effect model and boundary contracts.
- [ ] SM-02: Implement transition validation hook in runtime execution path.
- [ ] SM-03: Expose state machine introspection output for CLI/meta projection.
- [ ] SM-04: Add executable specifications for transition validity, guard checks, and failure behavior.

## 6. Next Phase Candidates

- NP-401: Link state transition lifecycle to Phase 5 event emission envelope.
- NP-402: Add policy-driven transition visibility and privilege checks.

## 7. References

- `docs/strategy/cncf-development-strategy.md`
- `docs/phase/phase-3.md`
- `docs/phase/phase-4-checklist.md`
- `docs/journal/2026/03/statemachine-dsl-execution-design.md`
