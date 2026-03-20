# JM-02 Instruction (Job Query Read Model)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Implement Job query read model for Phase 6.

Scope is JM-02 only.

## Background

Phase 6 currently marks:
- JM-01: DONE
- JM-02: ACTIVE

JM-02 must follow the latest Job/Task execution persistence design:
- Task-first execution model (`ActionCall -> Task -> Job`)
- policy-driven persistence (`Persistent Job` / `Ephemeral Job`)
- observability-inclusive query surfaces (TraceTree/debug information)

## In-Scope

1. Query model shape
- Define query response model for:
  - job status
  - trace timeline
  - task list
  - result summary
- Define result retrieval API by `JobId`.
- Ensure result payload is returned in the same response shape as synchronous execution.

2. Task-level projection
- Include task structure fields:
  - `taskId`
  - `parentTaskId`
  - status
  - started/finished timestamps
  - task-level result summary

3. Debug and trace projection
- Include Job debug metadata:
  - request summary
  - parameters
  - execution notes
- Define TraceTree projection shape:
  - `Job -> Task -> Event -> Task`

4. Deterministic read behavior
- Define ordering/pagination contract for timeline and tasks.
- Ensure deterministic output order for the same source state.

5. Persistence policy aware query
- Define/read behavior by job persistence policy:
  - persistent job: durable store + observability references
  - ephemeral job: runtime/observability path (no durable job row required)

6. Surface integration
- Integrate query read model with existing projection/help/meta surfaces where applicable.
- Expose job result retrieval API to command/client/server execution surfaces.

7. Documentation alignment
- Reflect JM-02 completion/progress in Phase 6 tracking docs.

## Out of Scope

- Job control command implementation (`cancel/retry/suspend/resume`) (JM-03).
- Event continuation/replay correlation implementation (JM-04).
- Full spec closure beyond JM-02-required coverage (JM-05 full scope).

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Preserve deterministic projection behavior.
- Reuse existing event/job/observability boundaries; avoid competing public APIs.
- Keep query model explicit about data origin (durable vs ephemeral/observability).

## Suggested File Targets

- Job query/projection implementation:
  - `src/main/scala/org/goldenport/cncf/job/...`
  - existing projection/meta integration area under CNCF packages
- Supporting model/journal wiring if needed:
  - `src/main/scala/org/goldenport/cncf/log/journal/...`
  - `src/main/scala/org/goldenport/cncf/job/journal/...`
- Specs:
  - `src/test/scala/org/goldenport/cncf/job/...`

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes implementing JM-02 query read model.
2. Code changes implementing job result retrieval API (`JobId` -> sync-equivalent response shape).
3. Deterministic task/timeline projection with pagination behavior.
4. Persistence-aware query behavior for persistent and ephemeral jobs.
5. TraceTree/debugInfo projection baseline exposure.
6. Executable specs validating:
- query shape consistency
- job result API response-shape compatibility with synchronous path
- deterministic ordering/pagination
- persistent vs ephemeral read behavior
- TraceTree/debug projection consistency
- policy visibility behavior on query surfaces
7. Progress updates:
- `docs/phase/phase-6-checklist.md`:
  - JM-02 `Status: DONE`
  - JM-02 detailed tasks `[x]`
- `docs/phase/phase-6.md`:
  - JM-02 checkbox `[x]`
  - Current Work Stack: B as DONE and next ACTIVE item set (JM-03)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.job.*"
```

If wildcard matching is noisy, list concrete spec classes explicitly.

## Definition of Done (JM-02)

JM-02 is DONE when all conditions hold:

1. Job query read model is implemented for status/trace/timeline/task views.
2. Job result retrieval API (`JobId`) is implemented and returns sync-equivalent response shape.
3. Projection includes task structure and debug/TraceTree baseline fields.
4. Persistent vs ephemeral query behavior is explicit and validated.
5. Ordering/pagination behavior is deterministic and covered by executable specs.
6. Related JM-02 tests pass.
7. `phase-6.md` and `phase-6-checklist.md` are updated consistently.
