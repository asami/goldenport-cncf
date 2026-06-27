# Phase 22 — Job Management

status = closed

## 1. Purpose of This Document

This work document records the closed Phase 22 work for `9.14 Job Management`.

Phase 22 started by normalizing Command execution policy, made Job a
store-backed SimpleEntity management projection, and added JCL diagnostics for
declared Event/Action chains, and added JobDefinition binding / execution
record policy. Task transaction and compensation boundaries are now complete;
user notification now uses Event routing and provider forwarding. Page-view
context data now has a query-only CompositeQuery boundary so Web tier page
rendering can aggregate App-tier data without coupling Domain tier to Web
layout concerns.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Record the closed baseline for `9.14 Job Management`.
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
- Add a query-only CompositeQuery boundary for page view context so Web tier,
  App tier, and Domain tier can batch display-support queries without allowing
  Command/Job-producing execution.

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

- No Scala/runtime behavior change in the JM-06 closure slice.
- No broadening of Job usage before Command execution policy is normalized.
- Job Entity management is limited to the synchronized management/search record;
  JobEngine remains the execution authority.
- JM-03 profile difference checking is diagnostics-only; it does not control or
  fail Job execution.
- No Task compensation implementation in JM-01.
- No user notification runtime in JM-01.
- No distributed Saga management in Phase 22; Saga is a separate future item.

## 4. Closed Work Stack

- A (DONE): JM-01 — Command Execution Policy Normalization.
- B (DONE): JM-02 — Job Entity Management.
- C (DONE): JM-03 — JCL Profile / Execution Profile Difference Checking.
- D (DONE): JM-03B — JobDefinition Entity / Binding / Execution Record Policy.
- E (DONE): JM-04 — Task Transaction and Compensation Boundary.
- F (DONE): JM-05 — User Job Notification Policy.
- G (DONE): JM-05B — Event-Based User Notification Forwarding.
- H (DONE): CQ-01 — Composite Query Boundary for Page View Context.
- I (DONE): JM-06 — Phase 22 verification and closure.

Resume hint:

- Phase 22 is closed. Select the next phase before adding new active work.
- JobEngine emits Job lifecycle/recovery events only; configured Event
  forwarding rules translate matching user-visible Job events into
  `UserNotificationProvider` requests.
- `textus-blog` is the JM-05B application driver: its deemed-subsystem assembly
  includes `textus-user-notification`, explicit Job event forwarding rules, and
  a header badge that reads the current user's unconfirmed notification count.
- Page-view context providers now contribute optional `NamedQuery` entries that
  are executed by the query-only CompositeQuery boundary and mapped back into
  server-rendered page context.

## 5. Development Items

- [x] JM-01: Command Execution Policy Normalization.
- [x] JM-02: Job Entity Management.
- [x] JM-03: JCL Profile / Execution Profile Difference Checking.
- [x] JM-03B: JobDefinition Entity / Binding / Execution Record Policy.
- [x] JM-04: Task Transaction and Compensation Boundary.
- [x] JM-05: User Job Notification Policy.
- [x] JM-05B: Event-Based User Notification Forwarding.
- [x] CQ-01: Composite Query Boundary for Page View Context.
- [x] JM-06: Phase 22 verification and closure.

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
- User job notification policy and Event-based forwarding are completed or
  explicitly deferred with remaining work named.
- Composite page-view context query aggregation is completed or explicitly
  deferred with remaining work named.
- No Phase 22 work item remains implicitly untracked.
- Phase 22 closure notes are recorded in `phase-22-checklist.md`.
- JM-06 closure validation is docs-only: `git diff --check` and the
  Phase 22 closure status `rg` check are sufficient because no runtime files
  change in this closure slice.

## 7. Closure Note

Phase 22 is closed with the following completed baseline:

- Command execution policy is normalized around explicit interface timing,
  Job run timing, and Job management policy.
- Job and JobDefinition are CNCF runtime/system management Entities using
  `entityKind = system`.
- JCL diagnostics can declare, compare, and reconstruct intended Event/Action
  chains, while executable JCL remains future work.
- Task transaction and explicit compensation boundaries are defined and
  observable through Job diagnostics.
- Job user notification is implemented as Event-based forwarding to the
  `UserNotificationProvider` SPI, not as direct JobEngine notification logic.
- Page-view support data has a query-only CompositeQuery boundary for
  server-rendered Web context aggregation.

Post-closure maintenance recorded on Jun. 27, 2026 keeps the external
execution envelope contract aligned with downstream Cozy/sbt-cozy scripted
usage: execution metadata is emitted with kebab-case public keys such as
`interface-shape`, `requested-mode`, `managed-by-job`, and
`async-continuation`, and `requested-mode` preserves the requested contract
token instead of an internal enum label.

Deferred items are not active Phase 22 work. They are recorded as future
development candidates in `docs/strategy/cncf-development-strategy.md`,
including Job Management follow-ups, observability externalization, distributed
Saga, distributed component runtime, Event reception policy follow-ups, and
notification UX/operations.
