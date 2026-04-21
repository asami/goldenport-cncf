# Phase 14 — Execution Layer Expansion

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 14.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Introduce the built-in execution expansion that follows the Pareto 80/20
  execution-platform boundary.
- Add a lightweight `WorkflowEngine` as the first new execution-layer
  capability after Phase 13 closure.
- Add workflow inspection/projection surfaces after the workflow baseline is
  fixed.
- Add a submission-only `JCL` baseline for sequential batch/job submission.
- Connect `JCL` to workflow entrypoints without turning `JCL` into a workflow
  language.
- Keep retry/dead-letter hardening behind the workflow/JCL baseline.

Current semantic direction:

- built-in Job Management and built-in Workflow target the high-frequency 80%
- `JCL` is submission-only
- Workflow is event-triggered and entity-status-based
- `Workflow instance != Job`
- advanced orchestration remains an external-engine handoff concern

## 3. Non-Goals

- No full workflow platform.
- No DAG/job-net orchestration.
- No branch/loop/parallel execution language.
- No timer-rich orchestration.
- No connector-heavy automation platform.
- No compensation engine.
- No reopening of Phase 13 runtime contracts.

## 4. Current Work Stack

- A (DONE): WF-01 — Introduce lightweight `WorkflowEngine` baseline.
- B (DONE): WF-02 — Add workflow inspection/projection surfaces.
- C (DONE): JCL-01 — Define and add `JCL` job/batch submission baseline.
- D (ACTIVE): JCL-02 — Connect `JCL` to workflow entrypoints.
- E (SUSPENDED): OPS-01 — Add retry/dead-letter operational hardening after workflow/JCL baseline.
- F (SUSPENDED): OPS-02 — Add saga identity / ABAC follow-up only if still in scope after A-E.

Current note:
- Phase 13 is closed and remains frozen.
- Phase 14 starts from the closed Phase 13 runtime and the fixed Pareto 80/20
  execution-platform boundary.
- `WorkflowEngine first` is the chosen ordering because `JCL` should target
  stable workflow entrypoints rather than define orchestration itself.
- `WF-01` runtime baseline and `WF-02` workflow inspection/projection surfaces
  are implemented and validated.
- `JCL-01` submission-only baseline is implemented and validated:
  - action target only
  - top-level `jobs[]`
  - sequential fail-fast execution
  - optional job-level failure hook

## 5. Development Items

- [x] WF-01: Introduce lightweight `WorkflowEngine` baseline.
- [x] WF-02: Add workflow inspection/projection surfaces.
- [x] JCL-01: Define and add `JCL` job/batch submission baseline.
- [ ] JCL-02: Connect `JCL` to workflow entrypoints.
- [ ] OPS-01: Add retry/dead-letter operational hardening after workflow/JCL baseline.
- [ ] OPS-02: Add saga identity / ABAC follow-up only if still in scope after A-E.

## 6. Next Phase Candidates

- NP-1401: External engine integration boundary for specialist orchestration.
- NP-1402: Timer/schedule semantics beyond the built-in Pareto 80/20 boundary.
- NP-1403: Connector-heavy or human-task workflow beyond the built-in workflow scope.

## 7. References

- `docs/design/execution-platform-boundary.md`
- `docs/design/job-management.md`
- `docs/design/statemachine-boundary-contract.md`
- `docs/journal/2026/04/phase-14-candidates-from-phase-13-2026-04-22.md`
- `docs/phase/phase-13.md`
- `docs/phase/phase-13-checklist.md`
