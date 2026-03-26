# Phase 10 — Textus Identity and User Account Practicalization

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 10.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Implement `textus-user-account` component as a practical account-management boundary.
- Implement `textus-identity` subsystem as identity/auth integration boundary.
- Track `cncf-samples` development as the practical verification vehicle for
  Phase 10 features.
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

- A (DONE): Define Textus account/identity capability contract and boundaries.
- B (DONE): Implement `textus-user-account` component operations and metadata.
- C (DONE): Realize revised minimal-subsystem runtime integration for `textus-identity`.
- D (ACTIVE): Align runtime/projection/meta surfaces for practical operation use.
- E (PLANNED): Add executable specifications and close phase documentation.

Resume hint:
- Validate practical runtime/projection behavior for the now-connected
  `textus-identity` minimal subsystem and advance PX-01.

## 5. Development Items

- [x] TU-01: Define and freeze `textus-user-account` component contract.
- [x] TU-02: Implement `textus-user-account` component and operation metadata integration.
- [x] TI-01: Define and freeze `textus-identity` subsystem integration contract.
- [x] TI-02: Implement `textus-identity` subsystem runtime integration.
- [ ] TI-03: Track CNCF sample development status and integration checkpoints.
- [ ] PX-01: Align command/query practical runtime behavior and projection visibility.
- [ ] PX-02: Add executable specifications and finalize Phase 10 closure.

## 6.2 Latest Verification Snapshot (2026-03-26)

- `textus-identity`:
  - contract artifacts confirmed:
    - `docs/journal/2026/03/ti-01-contract-freeze-2026-03-26.md`
    - `src/main/cozy/textus-identity-subsystem.cml`
  - focused validation:
    - `sbt --batch compile`
    - result: passed
  - contract notes:
    - subsystem boundary is fixed to descriptor-first orchestration/configuration,
      not duplicated account logic
    - no subsystem-owned identity operation surface is frozen in TI-01
    - `textus-user-account` dependency/binding is explicit in the canonical example
  - revised TI-02 status:
    - descriptor-to-runtime hookup is implemented
    - development override repository path is covered by focused spec
    - revised minimal-subsystem runtime integration is complete
- `textus-user-account`:
  - latest implementation commit: `40b30d0`
  - focused verification:
    - `sbt --batch compile`
    - result: passed
    - `sbt --batch test`
    - result: passed
- `cncf-samples`:
  - development has started in:
    - `/Users/asami/src/dev2026/cncf-samples`
  - detailed implementation progress is managed in the sample repository
    itself
  - Phase 10 tracks only integration checkpoints and overall dependency on the
    sample work
- Implementation notes:
  - generated Component package override is active
  - EntityValue output/package is generated under `${package}/entity/*`
  - generated `operationDefinitions` are present with `COMMAND/QUERY` and input value kind metadata
  - TU-02 implementation scope is complete
  - TI-01 contract freeze is complete
  - TI-02 runtime hookup is complete
  - `cncf-samples` is the practical validation vehicle for upcoming PX/TI work
  - current active item is PX-01; TI-03 records external sample checkpoints

## 6.3 Progress History Comment (2026-03-26)

- Phase 10 progress was corrected to match the actual `textus-identity` state.
- TI-01 remains DONE based on the contract-freeze record and canonical
  subsystem CML.
- TI-02 was returned to ACTIVE because the revised minimal-subsystem slice is
  still open: reusable generic runtime mechanism exists, but the descriptor to
  runtime hookup and development override path are not yet closed under the
  revised contract.
- 2026-03-26 focused validation closed the revised TI-02 slice and moved
  Phase 10 to PX-01.

## 6. Inputs from Previous Phases

- Phase 8 operation grammar/model propagation and runtime metadata integration baselines.
- Phase 6 job/task execution policy baseline for command/query behavior.
- Phase 5 event/reception/action integration baseline where identity/account events are required.

## 6.1 Implementation Repositories

- `textus-identity`:
  - `/Users/asami/src/dev2026/textus-identity`
- `textus-user-account`:
  - `/Users/asami/src/dev2026/textus-user-account`
- `cncf-samples`:
  - `/Users/asami/src/dev2026/cncf-samples`

Phase 10 development tracks these repositories together with CNCF integration.

## 7. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-8-checklist.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/strategy/cncf-development-strategy.md`
