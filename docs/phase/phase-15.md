# Phase 15 — Job Scheduling and Timer Boundary

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 15.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Define and implement the built-in job scheduling model after the completed
  Phase 14 execution baseline.
- Define the built-in timer and scheduling boundary around that scheduling
  model.
- Route async execution through the shared bounded `JobEngine` scheduler.
- Standardize priority semantics across scheduled execution paths.
- Prevent accidental expansion from operational scheduling and delay/retry
  handling into a general scheduling platform.
- Fix the built-in scheduler as the JobEngine-owned job scheduler so async work
  remains traceable and measurable.
- Keep external engine integration as a separate carryover concern from
  Phase 14 rather than the theme of this phase.

Current semantic direction:

- Phase 14 remains closed and fixed.
- Phase 15 combines execution-model work and timer-boundary work.
- `JS-01` is complete.
- `PR-01` is complete.
- `TM-01` is the active next item.
- Priority normalization follows scheduler implementation in the same phase.
- Async execution must always pass through Job management.
- This rule applies at operation-call and event-driven execution granularity.
- Timer-rich orchestration remains outside the built-in Pareto 80/20 boundary.

## 3. Non-Goals

- No general cron engine.
- No recurring calendar scheduling platform.
- No timer authoring in `JCL`.
- No timer orchestration in `WorkflowEngine`.
- No reopening of Phase 14 runtime contracts.

## 4. Current Work Stack

- A (DONE): JS-01 — Route all async execution through the bounded shared `JobEngine` scheduler.
- B (DONE): PR-01 — Add explicit job queue priority and normalize workflow priority semantics.
- C (ACTIVE): TM-01 — Define the timer and scheduling boundary around the built-in scheduler.
- D (PENDING): TM-02 — Decide whether any bounded non-retry delayed execution is allowed inside job control.

Current note:
- Phase 14 is closed and remains the built-in execution baseline.
- The existing retry scheduler is treated as the anchor timer case inside the
  built-in scheduler model.
- External engine integration remains a carryover item in Phase 14 and is not
  the default theme of Phase 15.

## 5. Development Items

- [x] JS-01: Route all async execution through the bounded shared `JobEngine` scheduler.
- [x] PR-01: Add explicit job queue priority and normalize workflow priority semantics.
- [ ] TM-01: Define the timer and scheduling boundary around the built-in scheduler.
- [ ] TM-02: Decide whether any bounded non-retry delayed execution is allowed inside job control.

## 6. Next Phase Candidates

- TM-03: Define timer/schedule inspection visibility if built-in delayed work grows beyond retry.
- Carryover from Phase 14: external engine integration boundary for specialist orchestration.

## 7. References

- `docs/design/execution-platform-boundary.md`
- `docs/design/job-management.md`
- `docs/design/statemachine-boundary-contract.md`
- `docs/phase/phase-14.md`
- `docs/phase/phase-14-checklist.md`
