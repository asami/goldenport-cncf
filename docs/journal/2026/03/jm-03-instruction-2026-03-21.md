# JM-03 Instruction (Job Control Command Model)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Implement Job control command model for Phase 6:

- `cancel`
- `retry`
- `suspend`
- `resume`
- command execution mode policy (`async default`, `sync option`)

Scope is JM-03 only.

## Background

Phase 6 currently treats:
- JM-01 as completed
- JM-02 as completed
- JM-03 as the next control-side implementation focus

JM-03 must follow the Job/Task execution persistence design and Phase 6 checklist constraints.

## In-Scope

1. Control command contracts
- Define request/response contracts and preconditions for:
  - cancel
  - retry
  - suspend
  - resume

2. State-guarded command transitions
- Implement state transition guards by command.
- Define deterministic outcomes for invalid command/state combinations.

3. Command execution mode policy
- Asynchronous execution by default.
- Return `JobId` on command submission.
- Optional synchronous execution by explicit option.

4. Synchronous option contract
- Define response payload compatibility with existing synchronous query/command response shape.
- Define deterministic timeout/failure mapping when sync completion is not reached.

5. Task-first invariant enforcement
- Ensure command execution always materializes Task under Job.
- Prevent direct ActionCall execution bypass.

6. Security/policy integration
- Apply existing policy/privilege checks at control entry points.
- Keep denial outcomes deterministic in consequence/observation taxonomy.

7. Documentation alignment
- Reflect JM-03 completion/progress in Phase 6 tracking docs.

## Out of Scope

- Event continuation/correlation/replay implementation details (JM-04 scope).
- Full executable-spec closure across all Job CQRS concerns (JM-05 scope).
- Distributed scheduler/backend integration.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Preserve deterministic behavior for command results and failures.
- Reuse existing Action/Event/Job boundaries; do not introduce competing control APIs.
- Keep command mode behavior explicit (`async` default, `sync` option).

## Suggested File Targets

- Job control model/engine:
  - `src/main/scala/org/goldenport/cncf/job/...`
- Service/component control entry integration:
  - `src/main/scala/org/goldenport/cncf/service/...`
  - `src/main/scala/org/goldenport/cncf/component/...`
- Policy/security integration points:
  - existing security/policy modules under CNCF packages
- Specs:
  - `src/test/scala/org/goldenport/cncf/job/...`
  - related scenario specs under `src/test/scala/org/goldenport/cncf/...`

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes for Job control commands (`cancel/retry/suspend/resume`).
2. State-guarded command transition handling and deterministic invalid-state outcomes.
3. Command mode implementation:
- async default + `JobId` return
- sync option with deterministic completion/timeout/failure semantics
4. Enforcement of Task-first invariant for command execution path.
5. Policy/privilege checks on control entry points with deterministic denial mapping.
6. Executable specs validating:
- control command semantics
- async/sync mode behavior
- `JobId` return contract
- invalid transition failure mapping
- policy allow/deny behavior
7. Progress updates:
- `docs/phase/phase-6-checklist.md`:
  - JM-03 `Status: DONE`
  - JM-03 detailed tasks `[x]`
- `docs/phase/phase-6.md`:
  - JM-03 checkbox `[x]`
  - Current Work Stack: C as DONE and next ACTIVE item set (JM-04)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.job.* org.goldenport.cncf.service.*"
```

If wildcard selection is noisy, list concrete spec classes explicitly.

## Definition of Done (JM-03)

JM-03 is DONE when all conditions hold:

1. Control command contracts and preconditions are fixed for cancel/retry/suspend/resume.
2. State-guarded transition logic and invalid-state outcomes are deterministic.
3. Command mode policy is implemented (`async` default + `JobId` return, `sync` option).
4. Command execution path enforces Task-first invariant (no direct ActionCall bypass).
5. Policy/privilege checks are applied consistently on control entry points.
6. Related JM-03 tests pass.
7. `phase-6.md` and `phase-6-checklist.md` are updated consistently.
