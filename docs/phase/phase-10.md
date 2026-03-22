# Phase 10 — Textus Identity and User Account Practicalization

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 10.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Implement `textus-user-account` component as a practical account-management boundary.
- Implement `textus-identity` subsystem as identity/auth integration boundary.
- Establish CNCF-runtime usable flows for:
  - account lifecycle operations
  - identity issuance/verification operations
  - component/subsystem integration route for command/query execution
- Expose stable introspection/projection surfaces (`help`, `describe`, `schema`, `meta.*`) for new operations.
- Add executable specifications for account/identity integration behavior.
- Support configurable package name for generated Component.
- Enforce EntityValue package layout as `${package}/entity/*`.

## 3. Non-Goals

- No redesign of core security model outside required integration boundaries.
- No federation/multi-tenant policy expansion unless explicitly required.
- No broad UI/UX redesign.
- No unrelated protocol/runtime refactoring.

## 4. Current Work Stack

- A (ACTIVE): Define Textus account/identity capability contract and boundaries.
- B (SUSPENDED): Implement `textus-user-account` component operations and metadata.
- C (SUSPENDED): Implement `textus-identity` subsystem integration and routing.
- D (SUSPENDED): Align runtime/projection/meta surfaces for practical operation use.
- E (SUSPENDED): Add executable specifications and close phase documentation.

Resume hint:
- Fix capability contract first; implementation and runtime alignment depend on stable boundaries.

## 5. Development Items

- [ ] TU-01: Define and freeze `textus-user-account` component contract.
- [ ] TU-02: Implement `textus-user-account` component and operation metadata integration.
- [ ] TI-01: Define and freeze `textus-identity` subsystem integration contract.
- [ ] TI-02: Implement `textus-identity` subsystem runtime integration.
- [ ] PX-01: Align command/query practical runtime behavior and projection visibility.
- [ ] PX-02: Add executable specifications and finalize Phase 10 closure.

## 6. Inputs from Previous Phases

- Phase 8 operation grammar/model propagation and runtime metadata integration baselines.
- Phase 6 job/task execution policy baseline for command/query behavior.
- Phase 5 event/reception/action integration baseline where identity/account events are required.

## 6.1 Implementation Repositories

- `textus-identity`:
  - `/Users/asami/src/dev2026/textus-identity`
- `textus-user-account`:
  - `/Users/asami/src/dev2026/textus-user-account`

Phase 10 development tracks these repositories together with CNCF integration.

## 7. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8-checklist.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/strategy/cncf-development-strategy.md`
