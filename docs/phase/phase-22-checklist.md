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

- `CommandExecutionPolicy` is now the canonical three-axis policy surface:
  caller interface mode, Job run mode, and managed-by-Job.
- The default Command path is synchronous direct execution without Job
  management.
- Legacy `CommandExecutionMode` and `execution=sync|async` remain compatibility
  inputs.
- Projection/help output exposes command execution metadata and the effective
  legacy mode label.

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
  `entityKind = workflow` and store-backed management policy.
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

Status: ACTIVE

### Objective

Define Task as the transaction unit inside a Job and establish compensation
boundaries between Tasks.

### Detailed Tasks

- [ ] Treat Aggregate execution as a Task.
- [ ] Define Task transactional success and failure semantics.
- [ ] Define compensation between Tasks.
- [ ] Connect incomplete cleanup with recovery-required events and human
      recovery diagnostics.
- [ ] Align Task compensation with transaction success/failure event lanes.

### Expected Output

- Job internals have explicit transactional boundaries.
- Compensation behavior is inspectable and recoverable when automated cleanup
  cannot complete.

### Guardrails

- Do not implement distributed Saga compensation here.
- Do not hide compensation-of-compensation failures.

---

## JM-05: User Job Notification Policy

Status: TODO

### Objective

Add user notification policy before relying on async Jobs for ordinary
application operations.

### Detailed Tasks

- [ ] Define notification triggers for async Job completion, failure, and
      recovery-required conditions.
- [ ] Separate application user notifications from system/admin diagnostics.
- [ ] Preserve application job result and “My jobs” navigation.
- [ ] Define how notification delivery is configured per application.
- [ ] Keep ordinary synchronous CRUD-style operations outside notification
      overhead.

### Expected Output

- Async Job usage has a clear user feedback path beyond polling result pages.

### Guardrails

- Do not make notification infrastructure a prerequisite for synchronous
  Command execution.
- Do not expose global admin Job details to ordinary application users.

---

## JM-06: Phase 22 Verification and Closure

Status: TODO

### Objective

Verify Phase 22 work and close or explicitly defer remaining Job Management
scope.

### Detailed Tasks

- [ ] Confirm Phase 22 docs and strategy status match implemented behavior.
- [ ] Confirm JM-01 through JM-05 are DONE or explicitly deferred.
- [ ] Run focused and full validations required by touched implementation
      slices.
- [ ] Record closure notes and next development candidates.

### Expected Output

- Phase 22 can be closed without untracked Job Management debt.

### Guardrails

- Do not close Phase 22 while active Job Management work remains implicit.
