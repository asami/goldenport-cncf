# Phase 6 — Job Management (CQRS) Checklist

This document contains detailed task tracking and execution decisions
for Phase 6.

It complements the summary-level phase document (`phase-6.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-6.md`) holds summary only.
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

Phase 6 implementation proceeds in this order:

1. JM-01 (job model/lifecycle) must be fixed first.
2. Freeze Task-first execution rule (`ActionCall -> Task -> Job`) as an implementation invariant.
3. Freeze persistence policy boundary (`Persistent Job` / `Ephemeral Job`) before expanding query/control APIs.
4. JM-02 (query read model) is implemented after lifecycle + persistence policy contracts stabilize.
5. JM-03 (control command model and async/sync command mode) is implemented with policy boundaries.
6. JM-04 (event/replay correlation and continuation model) is aligned once query/control contracts are stable.
7. Add TraceTree/debug information baseline wiring to support query/control observability.
8. JM-05 (executable specs) is finalized after behavior is fixed.

This order minimizes churn across read/control boundaries and event integration.

---

## JM-01: Canonical Job Model and Lifecycle Contract

Status: DONE

### Objective

Define canonical Job model and deterministic lifecycle transition semantics.

### Detailed Tasks

- [x] Define core job fields (`jobId`, state, created/updated timestamps, correlation keys).
- [x] Define lifecycle states and terminal/non-terminal boundaries.
- [x] Define transition table and invalid transition behavior.
- [x] Define idempotency expectations for repeated control inputs/events.
- [x] Define baseline contract that Command/Event execution is tracked under Job management.
- [x] Align with existing provisional docs and record resolved deltas.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-management.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-state-transition.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/job-task-execution-persistence-design.md`

---

## JM-02: Job Query Read Model

Status: DONE

### Objective

Implement query-side projection for Job status/trace/timeline.

### Detailed Tasks

- [x] Define read model shape for status, trace, and timeline.
- [x] Define job result retrieval API contract by `JobId`.
- [x] Ensure job result API returns payload in the same shape as synchronous execution response.
- [x] Define read model fields for Task structure (`taskId`, `parentTaskId`, status timestamps, result summary).
- [x] Define read model fields for Job debug metadata (`request summary`, `parameters`, execution notes).
- [x] Define TraceTree representation (`Job -> Task -> Event -> Task`) and projection shape.
- [x] Define deterministic ordering and pagination behavior.
- [x] Define query behavior for persistence policies:
  - persistent job: query from durable store + observability references
  - ephemeral job: query from runtime/observability-only path (no durable job row required)
- [x] Integrate with existing projection/help/meta surfaces as needed.
- [x] Expose job result retrieval API through command/client/server entry surfaces.
- [x] Define visibility behavior under existing policy boundaries.
- [x] Add baseline query consistency checks.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-event-log.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/event-shape.md`

---

## JM-03: Job Control Command Model

Status: DONE

### Objective

Implement control-side commands: cancel, retry, suspend, resume.

### Detailed Tasks

- [x] Define command contracts and preconditions for each control operation.
- [x] Implement state-guarded transition logic for control commands.
- [x] Implement command execution mode policy:
  - asynchronous execution by default
  - return `JobId` on command submission
  - optional synchronous execution by explicit option
- [x] Define synchronous option behavior contract:
  - response payload compatibility with existing synchronous query/command expectations
  - deterministic timeout/failure mapping when sync completion is not reached
- [x] Ensure command execution always materializes Task under Job (no direct ActionCall execution path).
- [x] Integrate policy/privilege checks for control entry points.
- [x] Define deterministic outcomes for invalid command/state combinations.
- [x] Map control failures to consequence/observation taxonomy consistently.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-management.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-5.md`

---

## JM-04: Job/Event Correlation and Replay Boundary

Status: DONE

### Objective

Define and implement Job correlation behavior with event and replay flows.

### Detailed Tasks

- [x] Define correlation keys and matching rules between Job and events.
- [x] Ensure event-triggered execution is also represented under Job management lifecycle.
- [x] Implement event continuation model:
  - same-job continuation (synchronous chain)
  - new-job continuation (asynchronous fork)
- [x] Define and persist/propagate event linkage metadata:
  - correlationId
  - causationId
  - parentJobId (optional)
- [x] Define replay interaction policy for Job state reconstruction/update.
- [x] Define handling for error events and compensation trigger boundaries.
- [x] Ensure deterministic behavior under repeated/replayed events.
- [x] Align with EventStore/EventBus contracts from Phase 5.

### Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-plan-expected-event.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/event-id-event-type.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-5-checklist.md`

---

## JM-05: Executable Specifications (Job CQRS)

Status: ACTIVE

### Objective

Add executable specifications for lifecycle, query, control, and event-correlation behavior.

### Detailed Tasks

- [ ] Add Given/When/Then specs for lifecycle transitions (success/failure/invalid transition).
- [ ] Add specs for query read model consistency and deterministic ordering.
- [ ] Add specs for control command semantics (`cancel/retry/suspend/resume`).
- [ ] Add specs for command async default behavior and `JobId` return contract.
- [ ] Add specs for optional synchronous command execution path and result semantics.
- [ ] Add specs proving Task-first invariant (`ActionCall` is executed only via Task/Job path).
- [ ] Add specs for persistence policy boundary:
  - Query execution -> Ephemeral Job path (non-persistent)
  - Event with no matched subscription -> Ephemeral Job path (non-persistent)
  - all other executions -> Persistent Job path
- [ ] Add specs for event continuation modes (same-job vs new-job).
- [ ] Add specs for correlation metadata propagation (`correlationId/causationId/parentJobId`).
- [ ] Add specs for TraceTree/debugInfo projection consistency.
- [ ] Add specs for replay and repeated-event idempotency behavior.
- [ ] Add policy denial/allow specs for query and control paths.
- [ ] Add regression specs across ActionCall/Event/Job integration path.

---

## Deferred / Next Phase Candidates

- Distributed scheduling and queue backends.
- External orchestrator/workflow integration.
- Multi-tenant job governance and quota enforcement.

---

## Completion Check

Phase 6 is complete when:

- JM-01 through JM-05 are marked DONE.
- `phase-6.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
