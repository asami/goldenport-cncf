# Phase 22 — Job Management

status = active

## 1. Purpose of This Document

This work document opens Phase 22 and makes `9.14 Job Management` the
current development item.

Phase 22 started by normalizing Command execution policy, made Job a
store-backed SimpleEntity management projection, and added JCL diagnostics for
declared Event/Action chains, and added JobDefinition binding / execution
record policy. Task transaction and compensation boundaries are now complete;
user notification policy is the active follow-up.

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
- Make Job behavior definable by canonical single-Job JCL, allow Job launch
  with an intended Event/Action chain profile, and compare declared JCL with
  observed execution profiles.
- Define JobDefinition Entity management, Command/Action/Operation binding,
  Job instance definition snapshots, execution-state storage, Task Execution
  Tree, and task-local calltree boundaries before Task compensation work.
- Classify Job and JobDefinition as `entityKind = system`; keep active
  JobDefinitions resident and keep active/recently completed Jobs resident for
  completion confirmation.
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
- JCL describes intended Job behavior as a visible Event/Action chain; observed
  execution profiles record what actually happened.
- JobDefinition is distinct from Job instance. A Job instance is a Job Entity;
  a reusable Job definition is a separate JobDefinition Entity.
- Both are CNCF runtime/system management entities, not business workflow
  entities.
- JCL diagnostics use `profile`; future executable JCL uses separate
  procedural `flow` and Event-driven `events` / `onEvent` sections.
- Job state and state transitions are part of Job control.
- Task boundaries are the natural place to define transactional success,
  failure, and compensation inside a Job.

## 3. Non-Goals

- No Scala/runtime behavior change in the Phase 22 opening slice.
- No broadening of Job usage before Command execution policy is normalized.
- Job Entity management is limited to the synchronized management/search record;
  JobEngine remains the execution authority.
- JM-03 profile difference checking is diagnostics-only; it does not control or
  fail Job execution.
- No Task compensation implementation in JM-01.
- No user notification runtime in JM-01.
- No distributed Saga management in Phase 22; Saga is a separate future item.

## 4. Active Work Stack

- A (DONE): JM-01 — Command Execution Policy Normalization.
- B (DONE): JM-02 — Job Entity Management.
- C (DONE): JM-03 — JCL Profile / Execution Profile Difference Checking.
- D (DONE): JM-03B — JobDefinition Entity / Binding / Execution Record Policy.
- E (DONE): JM-04 — Task Transaction and Compensation Boundary.
- F (ACTIVE): JM-05 — User Job Notification Policy.
- G (TODO): JM-06 — Phase 22 verification and closure.

Resume hint:

- Continue with JM-05. Job Tasks now expose transaction/compensation boundaries,
  Task Execution Tree and task detail diagnostics are available through
  `job_control`, and recovery-required is recorded when cleanup cannot complete.
  JM-05 should define user notification policy for async Job completion,
  failure, and recovery-required states.

## 5. Development Items

- [x] JM-01: Command Execution Policy Normalization.
- [x] JM-02: Job Entity Management.
- [x] JM-03: JCL Profile / Execution Profile Difference Checking.
- [x] JM-03B: JobDefinition Entity / Binding / Execution Record Policy.
- [x] JM-04: Task Transaction and Compensation Boundary.
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
- JobDefinition Entity management, binding, definition snapshots, execution
  state record storage, Task Execution Tree, task-local calltree, and JCL
  language boundaries are completed or explicitly deferred with remaining work
  named.
- Task transaction and compensation boundaries are completed or explicitly
  deferred with remaining work named.
- User job notification policy is completed or explicitly deferred with
  remaining work named.
- No Phase 22 work item remains implicitly untracked.
