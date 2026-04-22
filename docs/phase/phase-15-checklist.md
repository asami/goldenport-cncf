# Phase 15 — Job Scheduling and Timer Boundary Checklist

This document contains detailed task tracking and execution decisions
for Phase 15.

It complements the summary-level phase document (`phase-15.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-15.md`) holds summary only.
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

Phase 15 work proceeds in this order:

1. JS-01 fixes the shared async scheduling model first.
2. PR-01 standardizes queue and workflow priority semantics on top of JS-01.
3. TM-01 freezes the timer/scheduling boundary around the implemented scheduler.
4. TM-02 considers whether any bounded non-retry delayed execution belongs in
   built-in job control.
5. External engine integration remains a separate carryover concern and must not
   displace the scheduling/timer theme of this phase.

This order keeps CNCF aligned with the Pareto 80/20 rule and prevents scheduler
usage from expanding implicitly.

---

## JS-01: Shared Job Scheduler for Async Execution

Status: DONE

### Objective

Route all async execution through the bounded shared `JobEngine` scheduler after
the completed Phase 14 execution baseline.

JS-01 must ensure:

- async new-job execution enters one scheduler queue
- same-job async task execution enters that same queue
- due `RetryLater` retries enter that same queue
- operation-call execution and event-driven execution are the required
  granularity for this rule
- sync execution remains immediate
- scheduler concurrency is bounded and configurable
- no async execution path bypasses the scheduler
- queue/backlog/execution metrics remain accurate enough for tuning and
  incident investigation

### Detailed Tasks

- [x] Route async submit through the shared scheduler queue.
- [x] Route same-job async task execution through the shared scheduler queue.
- [x] Rework delayed retry so due retries enqueue into the shared scheduler.
- [x] Add bounded/configurable worker concurrency.
- [x] Keep public status stable and expose queueing through timeline/metrics.
- [x] Ensure scheduler-owned metrics and traceability remain authoritative for
  async execution.

### Expected Outcome

- CNCF has one bounded async execution path owned by `JobEngine`, with
  authoritative metrics and traceability for async execution.

### Guardrails

- Sync execution remains immediate.
- `Submitted` remains the queued async backlog state in v1 unless proven
  insufficient.
- Application-internal branching/selection remains application-owned for now.
- Workflow/JCL inherit scheduler behavior through `JobEngine`; they do not grow
  independent schedulers.

---

## PR-01: Queue Priority and Workflow Priority Normalization

Status: DONE

### Objective

Standardize priority semantics across queue scheduling and workflow-triggered
execution.

PR-01 must ensure:

- `JobSubmitOption` carries explicit queue priority
- smaller value means higher priority
- same priority uses FIFO ordering
- same-job async inherits parent priority
- delayed retry preserves original priority
- workflow registration priority uses the same smaller-first rule

### Detailed Tasks

- [x] Add explicit job queue priority to the async submission contract.
- [x] Define ready-queue ordering as `priority asc, FIFO`.
- [x] Normalize workflow registration winner semantics to smaller-first.
- [x] Preserve deterministic equal-priority ambiguity failure for workflow bootstrap.

### Expected Outcome

- Queue ordering and workflow-triggered execution use one priority convention.

### Guardrails

- No metadata-derived queue priority in v1.
- No fairness lanes or aging policy in v1.
- Strict priority starvation is accepted in v1.

---

## TM-01: Timer and Scheduling Boundary

Status: DONE

### Objective

Define the built-in timer and scheduling boundary around the implemented shared
`JobEngine` scheduler.

TM-01 must answer:

- what timer/scheduling semantics CNCF built-ins may own
- what timer/scheduling semantics CNCF must not own
- how the current retry scheduler fits inside the shared scheduler model
- which future timer/schedule requests belong to external engines

### Detailed Tasks

- [x] Define the built-in allowed timer/scheduling scope.
- [x] Define the built-in non-goals for timer/scheduling.
- [x] Define ownership split across `JobEngine`, `WorkflowEngine`, `JCL`, and external engines.
- [x] Confirm that current retry scheduling remains valid under the boundary.

### Expected Outcome

- CNCF has an explicit timer/scheduling boundary that preserves the built-in
  execution baseline without expanding into a scheduling platform.

### Guardrails

- No cron language.
- No business-calendar semantics.
- No schedule authoring in `JCL`.
- No timer-driven workflow DSL.

---

## TM-02: Bounded Non-Retry Delayed Execution

Status: ACTIVE

### Objective

Decide whether any bounded delayed execution beyond retry belongs in built-in
job control.

TM-02 must answer:

- whether built-in job control may own bounded non-retry delayed execution
- what constraints would keep that in the Pareto 80/20 boundary
- what must remain outside built-in support

### Detailed Tasks

- [ ] Decide whether bounded non-retry delay is allowed at all.
- [ ] Define constraints if allowed.
- [ ] Confirm that cron/recurrence and long-lived schedules remain out of scope.

### Expected Outcome

- CNCF either rejects or tightly bounds non-retry delayed execution in built-in
  job control.

### Guardrails

- No cron language.
- No business deadline model.
- No delayed workflow start DSL.

---

## Deferred / Out-of-Scope Notes

- General cron/recurrence scheduling.
- Business schedule semantics.
- Human reminder/task scheduling.
- Timer orchestration in `WorkflowEngine`.
- External engine integration implementation.
