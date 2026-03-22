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

Status: ACTIVE

### Objective

Define and freeze the operational contract of `textus-user-account`.

### Detailed Tasks

- [ ] Define account lifecycle operation set (create/read/update/disable/enable).
- [ ] Define command/query boundaries for each operation.
- [ ] Define input/output contracts and parameter normalization rules.
- [ ] Define identity linkage points required by `textus-identity`.
- [ ] Define deterministic naming and operation ordering rules.
- [ ] Define failure taxonomy and error response expectations.
- [ ] Add canonical examples and invalid-case constraints.

---

## TU-02: textus-user-account Component Implementation

Status: PLANNED

### Objective

Implement `textus-user-account` component with operation metadata integration.

### Detailed Tasks

- [ ] Implement component operation definitions following TU-01 contract.
- [ ] Integrate operation metadata for runtime/projection discovery.
- [ ] Align command operations with async job default path.
- [ ] Align query operations with sync/ephemeral behavior policy.
- [ ] Add regression safeguards for operation resolution and execution routing.

---

## TI-01: textus-identity Subsystem Contract

Status: PLANNED

### Objective

Define and freeze integration contract for `textus-identity` subsystem.

### Detailed Tasks

- [ ] Define subsystem responsibility boundary vs component boundary.
- [ ] Define identity operation set (issue/verify/lookup/revoke as applicable).
- [ ] Define command/query boundaries and security-sensitive constraints.
- [ ] Define integration contract with `textus-user-account`.
- [ ] Define event/job interaction policy where required.

---

## TI-02: textus-identity Subsystem Runtime Integration

Status: PLANNED

### Objective

Implement runtime integration path for `textus-identity`.

### Detailed Tasks

- [ ] Integrate subsystem into runtime resolution path.
- [ ] Connect identity operations to component execution boundaries.
- [ ] Ensure deterministic routing for command/query paths.
- [ ] Expose stable subsystem operation visibility in meta/projection surfaces.
- [ ] Remove ambiguous integration paths if present.

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
