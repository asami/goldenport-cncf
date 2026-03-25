# Phase 10 — Textus Identity and User Account Practicalization Checklist

This document contains detailed task tracking and execution decisions
for Phase 10.

It complements the summary-level phase document (`phase-10.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-10.md`) holds summary only.
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

Phase 10 implementation proceeds in this order:

1. TU-01 (user-account contract) must be fixed first.
2. TU-02 (user-account component implementation) follows the fixed contract.
3. TI-01 (identity subsystem contract) is fixed before integration work.
4. TI-02 (identity subsystem runtime integration) follows contract freeze.
5. PX-01 (runtime/projection practicalization) aligns exposure and behavior.
6. PX-02 (executable specs + closure docs) finalizes phase completion.

This order minimizes churn across contract/runtime/spec boundaries.

---

## Implementation Repositories

- `textus-identity`: `/Users/asami/src/dev2026/textus-identity`
- `textus-user-account`: `/Users/asami/src/dev2026/textus-user-account`
- CNCF integration repo: `/Users/asami/src/dev2025/cloud-native-component-framework`

---

## TU-01: textus-user-account Component Contract

Status: DONE

### Objective

Define and freeze the operational contract of `textus-user-account`.

### Detailed Tasks

- [x] Define account lifecycle operation set (create/read/update/disable/enable).
- [x] Define command/query boundaries for each operation.
- [x] Define input/output contracts and parameter normalization rules.
- [x] Define package-name configuration contract for generated Component.
- [x] Define EntityValue package contract as `${package}/entity/*`.
- [x] Define identity linkage points required by `textus-identity`.
- [x] Define deterministic naming and operation ordering rules.
- [x] Define failure taxonomy and error response expectations.
- [x] Add canonical examples and invalid-case constraints.

---

## TU-02: textus-user-account Component Implementation

Status: DONE

### Objective

Implement `textus-user-account` component with operation metadata integration.

### Detailed Tasks

- [x] Implement component operation definitions following TU-01 contract.
- [x] Integrate operation metadata for runtime/projection discovery.
- [x] Implement configurable package-name support for generated Component.
- [x] Implement EntityValue output path/package as `${package}/entity/*`.
- [x] Align command operations with async job default path.
- [x] Align query operations with sync/ephemeral behavior policy.
- [x] Add regression safeguards for operation resolution and execution routing.

---

## TI-01: textus-identity Subsystem Contract

Status: DONE

### Objective

Define and freeze integration contract for `textus-identity` subsystem.

### Detailed Tasks

- [x] Define subsystem responsibility boundary vs component boundary.
- [x] Define identity operation set (issue/verify/lookup/revoke as applicable).
- [x] Define command/query boundaries and security-sensitive constraints.
- [x] Define integration contract with `textus-user-account`.
- [x] Define event/job interaction policy where required.

### Evidence

- `/Users/asami/src/dev2026/textus-identity/docs/journal/2026/03/ti-01-contract-freeze-2026-03-26.md`
- `/Users/asami/src/dev2026/textus-identity/src/main/cozy/textus-identity-subsystem.cml`

---

## TI-02: textus-identity Subsystem Runtime Integration

Status: ACTIVE

### Objective

Implement runtime integration path for `textus-identity`.

### Detailed Tasks

- [x] Preserve reusable generic subsystem bootstrap/load mechanisms from the
  original TI-02 slice.
- [x] Preserve reusable repository/configuration plumbing for component
  acquisition.
- [ ] Realize the revised descriptor-first minimal Subsystem runtime path.
- [ ] Implement the versioned CAR coordinate + development override model in
  the active `textus-identity` slice.
- [ ] Expose deterministic subsystem structure visibility for the revised
  minimal shape.
- [ ] Remove or quarantine superseded `textus-identity`-specific prototype
  assumptions from the active runtime path.

Resume point:

- Continue from
  `/Users/asami/src/dev2026/textus-identity/docs/journal/2026/03/ti-02-handoff-2026-03-26.md`
  and
  `/Users/asami/src/dev2026/textus-identity/docs/journal/2026/03/ti-02-instruction-2026-03-26-revised.md`.
  Close descriptor-to-runtime hookup before starting PX-01.

History comment:

- 2026-03-26: TI-02 was reclassified from DONE to ACTIVE after checking the
  actual `textus-identity` repository state. The old TI-02 slice produced
  reusable generic runtime mechanisms, but revised minimal-subsystem closure
  work remains open.

---

## PX-01: Practical Runtime and Projection Alignment

Status: PLANNED

### Objective

Ensure CNCF practical usability for account/identity operations.

### Detailed Tasks

- [ ] Validate end-to-end command execution returns job id and retrievable results.
- [ ] Validate end-to-end query execution remains synchronous by default.
- [ ] Validate help/describe/schema/meta exposure is deterministic and complete.
- [ ] Validate command-help guidance route for new operations.
- [ ] Confirm baseline operational scenarios for practical usage are covered.

---

## PX-02: Executable Specifications and Phase Closure

Status: PLANNED

### Objective

Close Phase 10 with executable-spec guarantees and document alignment.

### Detailed Tasks

- [ ] Add executable specs for user-account component behavior.
- [ ] Add executable specs for identity subsystem integration behavior.
- [ ] Add cross-boundary specs for account-identity interaction.
- [ ] Add runtime/projection regression specs for practical usage scenarios.
- [ ] Update `phase-10.md` and `phase-10-checklist.md` consistently.
- [ ] Mark Phase 10 closed only after all items are DONE.

---

## Deferred / Next Phase Candidates

- Cross-system identity federation.
- Fine-grained authorization policy expansion.
- External identity provider adapters.

---

## Completion Check

Phase 10 is complete when:

- TU-01 through PX-02 are marked DONE.
- `phase-10.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
