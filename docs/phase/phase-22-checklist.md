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

Status: ACTIVE

### Objective

Make Job behavior definable by JCL and compare intended behavior with observed
runtime execution.

### Detailed Tasks

- [ ] Allow Job launch with an intended JCL profile.
- [ ] Record the actual execution profile from runtime behavior.
- [ ] Compare declared JCL with observed execution profile.
- [ ] Reconstruct a JCL candidate from observed execution profiles.
- [ ] Report contradictions, missing declarations, and possible JCL extensions.
- [ ] Define how operators accept profile-derived JCL changes or treat them as
      errors.

### Expected Output

- JCL can describe intended Job behavior.
- Runtime profiles can explain what actually happened.
- Difference checking can guide JCL correction or enforcement.

### Guardrails

- JCL cannot directly control every runtime behavior because some Job behavior
  is Event-driven.
- Do not make JCL the distributed Saga language in this slice.

---

## JM-04: Task Transaction and Compensation Boundary

Status: TODO

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
