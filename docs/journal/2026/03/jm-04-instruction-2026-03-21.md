# JM-04 Instruction (Job/Event Correlation and Replay Boundary)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Define and implement Job/event correlation and replay boundary behavior for Phase 6.

Scope is JM-04 only.

## Background

Phase 6 currently marks:
- JM-01: DONE
- JM-02: DONE
- JM-03: DONE
- JM-04: ACTIVE

JM-04 must align with:
- Phase 5 Event foundations
- Job/Task execution persistence design
- Task-first invariant

## In-Scope

1. Correlation model
- Define canonical correlation keys between Job and events.
- Define matching rules used by runtime and read model.

2. Event-triggered execution under Job lifecycle
- Ensure event-triggered execution is represented under Job lifecycle state tracking.
- Keep ActionCall execution routed through Task/Job path.

3. Event continuation model
- Implement and define both continuation modes:
  - same-job continuation (synchronous chain)
  - new-job continuation (asynchronous fork)

4. Linkage metadata propagation
- Define and propagate metadata:
  - `correlationId`
  - `causationId`
  - `parentJobId` (optional)

5. Replay boundary
- Define replay interaction policy for Job state reconstruction/update.
- Define deterministic behavior when replayed events are duplicated/reordered within supported assumptions.

6. Error/compensation boundary
- Define handling policy for error events and compensation trigger boundaries in Job lifecycle.

7. Documentation alignment
- Reflect JM-04 completion/progress in Phase 6 tracking docs.

## Out of Scope

- Full executable specification closure across all Phase 6 concerns (JM-05 scope).
- Distributed scheduler/transport backends.
- External workflow engine integration.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Preserve deterministic behavior for correlation, continuation, and replay outcomes.
- Reuse existing EventBus/EventStore/Job boundaries; avoid competing APIs.
- Keep continuation mode choice explicit and auditable.

## Suggested File Targets

- Job/event correlation and continuation implementation:
  - `src/main/scala/org/goldenport/cncf/job/...`
  - `src/main/scala/org/goldenport/cncf/event/...`
- Event/job metadata wiring:
  - `src/main/scala/org/goldenport/cncf/context/...`
  - `src/main/scala/org/goldenport/cncf/log/journal/...`
- Specs:
  - `src/test/scala/org/goldenport/cncf/job/...`
  - `src/test/scala/org/goldenport/cncf/event/...`

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes for Job/event correlation key model and matching rules.
2. Code changes for event continuation modes (same-job / new-job).
3. Metadata propagation implementation (`correlationId`, `causationId`, `parentJobId`).
4. Replay boundary behavior implementation with deterministic outcomes.
5. Error/compensation boundary handling rules at Job lifecycle integration points.
6. Executable specs validating:
- correlation matching behavior
- continuation mode behavior
- metadata propagation correctness
- replay idempotency/determinism expectations
- error/compensation boundary behavior
7. Progress updates:
- `docs/phase/phase-6-checklist.md`:
  - JM-04 `Status: DONE`
  - JM-04 detailed tasks `[x]`
- `docs/phase/phase-6.md`:
  - JM-04 checkbox `[x]`
  - Current Work Stack: D as DONE and next ACTIVE item set (JM-05)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.job.* org.goldenport.cncf.event.*"
```

If wildcard matching is noisy, list concrete spec classes explicitly.

## Definition of Done (JM-04)

JM-04 is DONE when all conditions hold:

1. Correlation keys and matching rules are fixed and implemented.
2. Event-triggered execution is represented under Job lifecycle with Task-first invariant preserved.
3. Continuation modes (same-job/new-job) are implemented and deterministic.
4. Linkage metadata (`correlationId/causationId/parentJobId`) is propagated and verifiable.
5. Replay interaction and error/compensation boundaries are defined and implemented.
6. Related JM-04 tests pass.
7. `phase-6.md` and `phase-6-checklist.md` are updated consistently.
