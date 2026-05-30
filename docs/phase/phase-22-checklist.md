# Phase 22 — Job Management Checklist

This document contains detailed task tracking and decisions for Phase 22.
It complements the summary-level phase document (`phase-22.md`) and may be
used as the closure record for completed Phase 22 work.

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-22.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## JM-01: Command Execution Policy Normalization

Status: DONE

### Objective

Normalize Command sync/async execution policy before broadening Job usage.

### Detailed Tasks

- [x] Audit current Command execution behavior and Phase 6 async-default
      compatibility assumptions.
- [x] Define Command metadata:
  - `commandKind`;
  - `commandExecutionProperties`;
  - `commandExecutionPolicy`.
- [x] Make ordinary one-Entity CRUD-style Commands synchronous by default.
- [x] Preserve explicit async Job execution through operation metadata or
      runtime override.
- [x] Define how effective execution policy drives Form/Web UX:
  - sync Commands return ordinary result pages;
  - async Job Commands return application job result and “My jobs” navigation.
- [x] Keep existing explicitly async Command and Job behavior compatible.
- [x] Add focused specs for policy defaults, explicit overrides, and metadata
      projection.

### Expected Output

- A deterministic Command execution policy model.
- Default synchronous behavior for ordinary one-Entity CRUD-style Commands.
- Explicit async Job behavior where configured.
- Runtime/Web metadata that makes the effective policy inspectable.

### Guardrails

- Do not implement Job Entity management in JM-01.
- Do not implement JCL profile checking in JM-01.
- Do not implement Task compensation in JM-01.
- Do not add user notification runtime in JM-01.
- Do not make every Command async merely because CQRS names it a Command.

### Completion Notes

- `CommandExecutionPolicy` is now the canonical policy surface: caller
  interface mode, Job run mode, managed-by-Job, and async continuation intent.
- The default Command path is synchronous direct execution without Job
  management.
- Canonical production modes are `Sync`, `JobSync`, `JobAsync`, and
  `JobSyncWithAsyncCont`.
- Legacy `CommandExecutionMode`, `execution=sync|async`, `AsyncJobAndAwait`,
  and `SyncJobAsyncInterface` remain compatibility inputs.
- Projection/help output exposes command execution metadata and the effective
  canonical mode label.

---

## JM-02: Job Entity Management

Status: DONE

### Objective

Manage Job as a first-class Entity rather than only as an engine-local runtime
record.

### Detailed Tasks

- [x] Define the Job Entity model and lifecycle state.
- [x] Preserve compatibility with existing JobEngine runtime records.
- [x] Connect Job state and state transitions to Job control operations.
- [x] Expose Job Entity records through application and admin result surfaces.
- [x] Define the storage and migration boundary between runtime JobEngine
      state and canonical Job Entity state.

### Expected Output

- Job is visible and manageable as an Entity with state, lifecycle, and
  inspection surfaces.

### Guardrails

- Do not move Saga/distributed ownership into Job Entity scope.
- Do not require Job Entity creation for synchronous one-Entity CRUD-style
  Commands.

### Completion Notes

- `job_control` now exposes a built-in `job` SimpleEntity descriptor with
  `entityKind = system` and store-backed management policy.
- Persistent JobEngine records synchronize lightweight Job Entity records on
  submit, lifecycle transitions, control transitions, retry/recovery metadata,
  and result summary updates.
- `JobEngine` remains the execution authority. Existing Job read models,
  result/timeline/calltree pages, and control operations continue to use the
  JobEngine runtime path.
- Ephemeral/debug runtime Jobs stay out of the Job Entity store and remain
  visible through the existing runtime read model according to persistence
  policy.

---

## JM-03: JCL Profile / Execution Profile Difference Checking

Status: DONE

### Objective

Make Job behavior definable by JCL and compare intended behavior with observed
runtime execution.

### Detailed Tasks

- [x] Add canonical single-Job `job:` JCL syntax.
- [x] Preserve existing `jobs:` batch syntax for compatibility.
- [x] Allow Job launch with an intended JCL Event/Action chain profile.
- [x] Record declared profile metadata on Job launch.
- [x] Record the actual execution profile from runtime behavior.
- [x] Compare declared JCL with observed execution profile.
- [x] Reconstruct a canonical `job:` JCL candidate from observed execution
      profiles.
- [x] Report contradictions, missing declarations, undeclared runtime
      extensions, and possible JCL extensions.
- [x] Keep JM-03 diagnostics-only; operator accept/apply and enforcement are
      later work.

### Expected Output

- JCL can describe intended Job behavior as an Event/Action chain.
- Runtime profiles can explain what actually happened.
- Difference checking can guide JCL correction or future enforcement.

### Guardrails

- JCL cannot directly control every runtime behavior because some Job behavior
  is Event-driven.
- Do not make JCL the distributed Saga language in this slice.
- Do not fail or control Job execution from JM-03 diagnostics.

### Completion Notes

- Canonical authoring now uses single-Job `job:` JCL. Existing `jobs:` remains
  supported for batch compatibility, and simultaneous `job:` / `jobs:` roots
  fail deterministically.
- `profile.eventChain` declares Actions, emitted Events, receiver Actions,
  guard metadata, and `required` / `possible` / `forbidden` occurrence
  expectations.
- `job_control.job.compare_job_profile` compares declared and observed profiles
  and reports stable diagnostics without changing Job execution.
- `job_control.job.reconstruct_job_profile` emits a canonical `job:` candidate
  from observed runtime behavior.
- Event receiver definitions may remain distributed across components; JM-03
  provides a Job-level visibility and diagnostics surface for the chain.

---

## JM-03B: JobDefinition Entity / Binding / Execution Record Policy

Status: DONE

### Objective

Separate reusable Job definitions from Job instances, define how Commands /
Actions / Operations bind to Job definitions, and fix the execution-state
recording boundary before Task transaction and compensation work begins.

### Detailed Tasks

- [x] Add `entityKind = system` and classify Job / JobDefinition as system
      management Entities.
- [x] Define `JobDefinition Entity` as the canonical management record for
      reusable Job definitions.
- [x] Define JobDefinition lifecycle and metadata:
  - JCL source;
  - normalized diagnostics `profile`;
  - reserved future executable `flow`;
  - reserved future executable `events` / `onEvent`;
  - version / revision / hash;
  - draft / active / retired state;
  - target Action / Command binding metadata;
  - owner / visibility / authorization metadata.
- [x] Define `jobDefinitionRef` binding from Command / Action / Operation
      metadata to JobDefinition.
- [x] Keep inline JCL submit as compatibility and debug input, not the normal
      reusable definition registry.
- [x] Define Job instance linkage:
  - `jobDefinitionId`;
  - `jobDefinitionVersion`;
  - `jobDefinitionHash`;
  - declared profile snapshot;
  - optional normalized JCL/source snapshot.
- [x] Define execution-record storage policy:
  - fields retained directly on Job Entity;
  - full timeline storage;
  - Task Execution Tree storage;
  - task-local calltree storage;
  - large result body storage;
  - raw event history storage.
- [x] Define `Task Execution Tree` as the canonical diagnostic structure for
      Event/subtask/retry/compensation parent-child execution relationships.
- [x] Define task-local calltree as Task detail diagnostics, distinct from the
      Job-level summary/calltree reference.
- [x] Update JCL language documentation:
  - `profile` is diagnostics-only;
  - `flow` is the future procedural orchestration section;
  - `events` / `onEvent` are the future Event-driven orchestration section.

### Expected Output

- JobDefinition and Job instance responsibilities are distinct.
- JCL can evolve toward a Job orchestration language without overloading the
  diagnostics-only `profile`.
- Job execution state has an explicit storage and management boundary.
- JM-04 Task compensation can build on a clear Task tree and task-local
  diagnostic model.

### Guardrails

- Do not implement executable JCL `flow` or `onEvent` runtime in JM-03B.
- Do not start Task compensation before execution record policy is fixed.
- Do not store full timeline, full Task calltrees, large results, or raw event
  history directly in the lightweight Job Entity body.

### Completion Notes

- `EntityKind.System` is available for runtime/system management Entities.
- Job is a `system` Entity with active/recent-completion residency.
- JobDefinition is a `system` Entity with active-definition residency.
- `job_control.job` exposes create/update/activate/retire/get/search
  JobDefinition operations and can submit a Job by `jobDefinitionRef`.
- Job launch records definition id/version/revision/hash/profile/source
  snapshot metadata on the Job instance.
- JCL `flow`, `events`, and `onEvent` are accepted as inert future language
  sections and are not executed.

---

## JM-04: Task Transaction and Compensation Boundary

Status: DONE

### Objective

Define Task as the observable execution step inside a Job, define how a Task
owns or joins a transaction, and establish compensation boundaries between
Tasks. Task and transaction are related but not identical runtime objects.

### Detailed Tasks

- [x] Treat Aggregate execution as a Task target in Job task metadata.
- [x] Define Task transactional success and failure semantics.
- [x] Define Command/Event transaction semantics:
  - `JoinCaller` default for synchronous Command phases;
  - `Required` default for Event-driven same-transaction handling;
  - explicit `BestEffort`, `Ignore`, or `NewTransaction` relaxation;
  - conservative Event/EventReception transaction capability composition.
- [x] Define `JobSyncWithAsyncCont` as same-Job async continuation Task with
      a new transaction and traceability through the primary `job.id`.
- [x] Define explicit compensation between Tasks.
- [x] Connect incomplete cleanup with recovery-required events and human
      recovery diagnostics.
- [x] Align Task compensation with transaction success/failure diagnostic lanes.

### Expected Output

- Job internals have explicit transactional boundaries.
- Default Command/Event handling is transactionally strict unless the operation
  designer explicitly relaxes it.
- Compensation behavior is inspectable and recoverable when automated cleanup
  cannot complete.
- `job_control` exposes Task Execution Tree and Task detail diagnostics.

### Guardrails

- Do not implement distributed Saga compensation here.
- Do not hide compensation-of-compensation failures.
- Do not infer business compensation when no explicit compensation action is
  declared.

---

## JM-05: User Job Notification Policy

Status: DONE

### Objective

Add user notification policy before relying on async Jobs for ordinary
application operations.

### Detailed Tasks

- [x] Define notification triggers for async Job completion, failure, and
      recovery-required conditions.
- [x] Separate application user notifications from system/admin diagnostics.
- [x] Preserve application job result and “My jobs” navigation.
- [x] Define how notification delivery is configured per application.
- [x] Keep ordinary synchronous CRUD-style operations outside notification
      overhead.

### Expected Output

- CNCF provides `UserNotificationProvider` as the runtime SPI for application
  user notifications.
- User-visible async managed Job success/failure/cancel/recovery-required
  outcomes are eligible for user notification when Event forwarding is
  configured and a provider is available.
- System/admin/background Jobs without application Web context do not notify by
  default unless an explicit Event forwarding rule opts in.
- `textus-user-notification` can implement the provider to create actual
  in-app notification records.

### Guardrails

- Do not make notification infrastructure a prerequisite for synchronous
  Command execution.
- Do not expose global admin Job details to ordinary application users.

---

## JM-05B: Event-Based User Notification Forwarding

Status: DONE

### Objective

Keep JobEngine independent of user notification. Jobs emit lifecycle/recovery
events; a CNCF Event routing bridge forwards matching events to
`UserNotificationProvider`.

### Detailed Tasks

- [x] Remove direct provider invocation and notification policy state from
      JobEngine / JobSubmitOption / JobRecord.
- [x] Publish Job lifecycle/recovery facts through EventBus/EventEngine
      routing with payload needed for notification decisions.
- [x] Add `runtime.userNotification.eventForwarding` rules for event-to-user
      notification conversion.
- [x] Keep default forwarding restricted to application-visible Job events.
- [x] Move deduplication and provider-failure diagnostics into the forwarding
      bridge.
- [x] Use `textus-blog` as the first application driver by wiring
      `textus-user-notification` into the deemed Subsystem and exposing an
      unconfirmed notification badge in the Blog header.

### Expected Output

- JobEngine has no direct dependency on the user notification provider runtime.
- `UserNotificationProvider` remains the SPI used by forwarding rules.
- Provider absence or failure never changes Job state or result.
- `textus-blog` can opt into the provider and forwarding rules without binding
  Blog domain logic directly to notification persistence.

### Guardrails

- Do not treat EventStore append as the routing surface; use EventBus /
  EventEngine routing.
- Do not make user notification part of the Job core model.

---

## CQ-01: Composite Query Boundary for Page View Context

Status: DONE

### Objective

Add a query-only composite request mechanism that can aggregate page-view
support data through Web tier -> App tier and App tier -> Domain tier
boundaries without making Domain tier aware of Web layout, badges, navigation,
or page context.

### Detailed Tasks

- [x] Add `CompositeQueryRequest`, `NamedQuery`,
      `CompositeQueryResponse`, `CompositeQueryResult`, and
      `CompositeQueryPolicy`.
- [x] Add `CompositeQueryEngine.execute` with deterministic sequential
      execution in declared order.
- [x] Reject duplicate query names and unknown `dependsOn` references.
- [x] Limit v1 to Query operations; reject Command and trace-job /
      Job-producing query execution.
- [x] Preserve required vs optional query behavior:
  - required query failure fails the composite request;
  - optional query failure is recorded in composite diagnostics and does not
    block other queries.
- [x] Refactor `WebPageContextProvider` into a contributor shape that can
      provide `NamedQuery` entries and map the composite response into
      server-rendered `WebPageContext` values.
- [x] Move `textus-user-notification` notification badge context resolution to
      the CompositeQuery path rather than direct provider action execution.

### Expected Output

- Page-view context can be resolved from a single query-only aggregation
  boundary.
- Web tier page rendering can request App-tier page context without directly
  bundling Domain-tier Web concerns.
- `/form-api` remains optional enhancement/refresh/validation, not the primary
  page render path for badge/context data.

### Guardrails

- Do not allow Command, Task, Job, compensation, or mutation semantics inside
  CompositeQuery.
- Do not expose Web-specific names in Domain-tier APIs.
- Do not make Domain tier aware of layout/header/badge/navigation concepts.
- Keep v1 sequential; parallel execution can be added behind the same
  interface later.

---

## JM-06: Phase 22 Verification and Closure

Status: DONE

### Objective

Verify Phase 22 work and close or explicitly defer remaining Job Management
scope.

### Detailed Tasks

- [x] Confirm Phase 22 docs and strategy status match implemented behavior.
- [x] Confirm JM-01 through JM-05B and CQ-01 are DONE or explicitly deferred.
- [x] Run focused and full validations required by touched implementation
      slices.
- [x] Record closure notes and next development candidates.

### Expected Output

- Phase 22 can be closed without untracked Job Management debt.

### Guardrails

- Do not close Phase 22 while active Job Management work remains implicit.

### Closure Notes

Phase 22 is closed. The completed baseline includes:

- Command execution policy normalization with synchronous direct/no-Job as the
  ordinary default and explicit async managed Job execution where configured.
- Job SimpleEntity management and JobDefinition SimpleEntity management as
  CNCF `entityKind = system` runtime management records.
- Single-Job JCL diagnostics for declared/observed Event/Action chains,
  profile comparison, and reconstruction.
- JobDefinition binding, launch snapshot policy, and execution-record
  boundaries for lightweight Job Entity records, full timeline, Task Execution
  Tree, task-local calltree, large result bodies, and raw event history.
- Task transaction and explicit compensation boundaries, including
  recovery-required diagnostics for incomplete cleanup.
- Event-based user notification forwarding to `UserNotificationProvider`,
  with `textus-user-notification` as the first provider driver.
- Query-only CompositeQuery boundary for page-view context aggregation across
  Web tier, App tier, and Domain tier responsibilities.

Deferred items are future development candidates, not active Phase 22 work:

- `9.14 Job Management Follow-ups`: executable JCL `flow`, `events`, and
  `onEvent` runtime; JobDefinition accept/apply workflow and operational
  lifecycle expansion; Job/Task execution record persistence beyond the
  current lightweight projection; CompositeQuery v2 parallel execution,
  cross-subsystem protocol surface, and richer App-tier page-view contracts.
- `9.4 Metrics and Observability`: large result/response externalization for
  CallTree, Task calltree, execution history, and Job diagnostics; compact
  summary/reference storage policy for Job result and diagnostic payloads.
- `9.15 Saga Management`: distributed Saga as the cross-subsystem and
  long-running extension of Job management, including remote retry, remote
  compensation, saga identity propagation, and distributed observability.
- `9.13 Distributed Component Runtime`: distributed scheduler, clustered
  Job/Event ownership, and cross-instance Working Set/View consistency.
- `9.2 Event Mechanism Follow-ups`: richer Event reception policy and
  async/sync same-job continuation semantics that executable JCL may later
  consume.
- `9.1 Web Next Stage Follow-ups`: full user notification inbox/admin
  operations beyond the Phase 22 provider, forwarding, badge, and baseline
  composed-page work.

Closure validation for JM-06 is docs-only:

- `git diff --check`
- Phase 22 status `rg` check over the phase/checklist/strategy files.
