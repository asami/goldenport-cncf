# Phase 22 — Job Management

status = active

## 1. Purpose of This Document

This work document opens Phase 22 and makes `9.14 Job Management` the
current development item.

Phase 22 started by normalizing Command execution policy and then made Job a
store-backed SimpleEntity management projection. JCL, Task transaction,
compensation, and user notification work is tracked in later JM slices.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Make `9.14 Job Management` the active development item.
- Normalize Command sync/async execution policy before broadening Job usage.
- Prepare Command definition metadata for `commandKind`,
  `commandExecutionProperties`, and `commandExecutionPolicy`.
- Keep ordinary one-Entity CRUD-style Commands synchronous by default.
- Require async Job execution to be explicit by operation metadata or runtime
  override.
- Manage Job as a first-class SimpleEntity management record synchronized from
  JobEngine lifecycle snapshots.
- Make Job behavior definable by JCL, allow Job launch with an intended JCL
  profile, and compare declared JCL with observed execution profiles.
- Treat Task as the transaction unit inside a Job and define compensation
  boundaries between Tasks.
- Add application user notification policy before async Jobs become ordinary
  application UX.

Final semantic direction:

- CQRS identifies state-changing operations as Commands, but Command does not
  automatically mean async Job execution.
- Runtime cost and application UX determine whether a Command runs
  synchronously or as a Job.
- Job execution is explicit platform behavior, not the default path for simple
  CRUD-style Commands.
- JCL describes intended Job behavior; observed execution profiles record what
  actually happened.
- Job state and state transitions are part of Job control.
- Task boundaries are the natural place to define transactional success,
  failure, and compensation inside a Job.

## 3. Non-Goals

- No Scala/runtime behavior change in the Phase 22 opening slice.
- No broadening of Job usage before Command execution policy is normalized.
- Job Entity management is limited to the synchronized management/search record;
  JobEngine remains the execution authority.
- No JCL profile difference checking or reconstruction in JM-01.
- No Task compensation implementation in JM-01.
- No user notification runtime in JM-01.
- No distributed Saga management in Phase 22; Saga is a separate future item.

## 4. Active Work Stack

- A (DONE): JM-01 — Command Execution Policy Normalization.
- B (DONE): JM-02 — Job Entity Management.
- C (ACTIVE): JM-03 — JCL Profile / Execution Profile Difference Checking.
- D (TODO): JM-04 — Task Transaction and Compensation Boundary.
- E (TODO): JM-05 — User Job Notification Policy.
- F (TODO): JM-06 — Phase 22 verification and closure.

Resume hint:

- Continue with JM-03. Job is now exposed as a store-backed SimpleEntity
  management record synchronized from JobEngine lifecycle state; do not pull
  Task compensation, user notification, or distributed Saga work into JCL
  profile checking unless explicitly selected.

## 5. Development Items

- [x] JM-01: Command Execution Policy Normalization.
- [x] JM-02: Job Entity Management.
- [ ] JM-03: JCL Profile / Execution Profile Difference Checking.
- [ ] JM-04: Task Transaction and Compensation Boundary.
- [ ] JM-05: User Job Notification Policy.
- [ ] JM-06: Phase 22 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-22-checklist.md`.

## 6. Completion Conditions

Phase 22 closure conditions:

- Command execution policy has deterministic sync/async defaults and explicit
  override behavior.
- Command metadata exposes `commandKind`, `commandExecutionProperties`, and
  `commandExecutionPolicy` consistently enough for runtime, Web/Form UX, and
  documentation.
- Ordinary CRUD-style one-Entity Commands remain synchronous by default.
- Async Job Commands have application-level result and “My jobs” UX hooks.
- Job Entity management scope and implementation are completed or explicitly
  deferred with remaining work named.
- JCL declared profile, observed execution profile, difference checking, and
  reconstruction policy are completed or explicitly deferred with remaining
  work named.
- Task transaction and compensation boundaries are completed or explicitly
  deferred with remaining work named.
- User job notification policy is completed or explicitly deferred with
  remaining work named.
- No Phase 22 work item remains implicitly untracked.
