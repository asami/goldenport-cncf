# Phase 14 — Execution Layer Expansion Checklist

This document contains detailed task tracking and execution decisions
for Phase 14.

It complements the summary-level phase document (`phase-14.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-14.md`) holds summary only.
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

Phase 14 implementation proceeds in this order:

1. WF-01 fixes the lightweight workflow baseline first.
2. WF-02 makes workflow definitions and workflow instances inspectable.
3. JCL-01 adds sequential submission-only `JCL` with action targets only.
4. JCL-02 connects `JCL` to workflow entrypoints without mixing the two languages.
5. OPS-01 adds retry/dead-letter hardening after the new execution baseline exists.
6. OPS-02 handles saga identity and ABAC follow-up only if still inside the phase boundary.

This order keeps the new execution layer aligned with the Pareto 80/20 rule and
avoids building `JCL` against an unstable workflow target.

---

## WF-01: Lightweight WorkflowEngine Baseline

Status: DONE

### Objective

Introduce the minimum built-in `WorkflowEngine` contract.

The baseline must be:

- event-triggered
- entity-status-based
- lightweight/orchestrator-only
- separate from `JobEngine`
- separate from `JCL`

### Detailed Tasks

- [x] Define workflow definition/registration model.
- [x] Define workflow instance/status model.
- [x] Define event-to-workflow matching using `event + entity status`.
- [x] Define next-action decision contract.
- [x] Define delegation path into existing runtime/job execution.
- [x] Define linkage from workflow instances to resulting job ids.

### Expected Outcome

- CNCF has a minimal built-in workflow baseline that can react to events,
  observe entity status, decide the next action, and delegate execution to the
  existing runtime/job path.

### Guardrails

- No branch/loop/parallel.
- No timers.
- No graph orchestration.
- No embedded scripting.
- No workflow-owned execution engine.

---

## WF-02: Workflow Inspection and Projection

Status: DONE

### Objective

Make workflow definitions and workflow instances visible through builtin
projection/inspection surfaces.

### Detailed Tasks

- [x] Add workflow definition projection.
- [x] Add workflow instance status projection.
- [x] Add workflow instance history projection.
- [x] Add admin discovery links to authoritative workflow/job surfaces.
- [x] Preserve Phase 13 runtime participant identity rules in workflow visibility.

### Expected Outcome

- Operators and developers can inspect workflow definitions and workflow
  instances without inventing a parallel execution model.

---

## JCL-01: Submission-Only JCL Baseline

Status: DONE

### Objective

Define and add a submission-only `JCL` baseline for sequential batch/job
submission.

### Detailed Tasks

- [x] Define YAML `JCL` shape with top-level `jobs[]`.
- [x] Keep `JCL` sequential-only.
- [x] Support action target only in JCL-01.
- [x] Support parameters and submit policy.
- [x] Support job-level failure hook via named action.
- [x] Reject workflow semantics inside `JCL`.

### Expected Outcome

- CNCF has a small submission language for the high-frequency 80% of batch/job
  submission use cases.

---

## JCL-02: JCL to Workflow Entrypoints

Status: DONE

### Objective

Connect `JCL` to workflow entrypoints while preserving the separation between
submission and orchestration.

### Detailed Tasks

- [x] Allow `JCL` target to reference a workflow entrypoint.
- [x] Ensure `JCL` starts work while workflow controls progression.
- [x] Keep `JCL` and workflow as separate models/languages.
- [x] Preserve existing runtime/job visibility on submitted work.

### Expected Outcome

- `JCL` can start workflow-driven execution without becoming a workflow DSL.

---

## OPS-01: Retry and Dead-Letter Hardening

Status: DONE

### Objective

Add operational hardening only after the workflow/JCL baseline exists.

### Detailed Tasks

- [x] Define retry orchestration strategy.
- [x] Define dead-letter / poison-event handling.
- [x] Define operator recovery visibility.
- [x] Align retry/dead-letter behavior with existing disposition visibility.

### Expected Outcome

- CNCF execution becomes more operationally survivable without changing the
  core workflow/JCL separation.
- Built-in retry interpretation is fixed:
  - `RetryNow` => immediate retry, max 3 retries
  - `RetryLater` => delayed retry, backoff `1 / 5 / 15 minutes`, max 3 retries
  - `FixInput` / `Escalation` / absent hint => terminal now
  - retry exhaustion => dead-letter
  - non-retryable terminal failure => poison

---

## OPS-02: Saga Identity and ABAC Follow-Up

Status: ACTIVE

### Objective

Handle semantic follow-up work only if it still fits inside the Phase 14
boundary after WF/JCL and OPS-01.

### Detailed Tasks

- [ ] Define final saga identity standardization if still in scope.
- [ ] Define ABAC execution follow-up for event-rule matching if still in scope.
- [ ] Confirm both remain inside the Pareto 80/20 built-in boundary.

### Expected Outcome

- Only the semantic follow-up work that still belongs inside the built-in 80%
  layer is carried by this phase.

---

## Deferred / Out-of-Scope Notes

- Full workflow platform expansion.
- DAG/job-net orchestration.
- Timer-rich orchestration.
- Connector-heavy automation.
- Compensation engine.
- Reopening Phase 13 runtime contracts.

---

## Completion Check

Phase 14 kickoff is complete when:

- [x] `phase-14.md` exists and is the active phase document.
- [x] `phase-14-checklist.md` exists and contains the work stack above.
- [x] Phase 14 purpose/non-goals reflect the Pareto 80/20 boundary.
- [x] Phase 13 documents remain unchanged except by reference.
