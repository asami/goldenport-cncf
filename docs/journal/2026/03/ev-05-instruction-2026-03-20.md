# EV-05 Instruction (Executable Specifications for Event Lifecycle / Replay / Policy)

status=ready
published_at=2026-03-20
owner=cncf-runtime

## Goal

Complete executable specifications for Phase 5 event behavior:
- lifecycle emission
- persistence modes (transactional/non-transactional, persistent/ephemeral)
- replay behavior
- policy acceptance/denial behavior

Scope is EV-05 only.

## Background

Phase 5 currently marks EV-05 as ACTIVE.

EV-01 to EV-04 are treated as completed prerequisites:
- lifecycle event envelope exists
- EventStore baseline exists with transactional/non-transactional lanes
- EventBus publish/dispatch baseline exists
- policy-driven visibility/privilege checks baseline exists

## In-Scope

1. Lifecycle emission specs
- Given/When/Then coverage for lifecycle sequence and metadata integrity
- Include transition success/failure paths

2. Persistence mode specs
- persistent vs ephemeral behavior
- transactional lane vs non-transactional lane behavior

3. Replay specs
- deterministic re-dispatch ordering
- replay behavior assumptions (idempotent-safe path)

4. Policy specs
- privilege acceptance and denial paths at event execution entry points
- deterministic denial consequence/observation mapping

5. Regression linkage specs
- state-machine transition -> event lifecycle linkage

6. Documentation alignment
- Reflect EV-05 completion/progress in Phase 5 tracking docs

## Out of Scope

- New EventStore/EventBus architecture changes not required for testability.
- New policy model design changes (EV-04 follow-up scope).
- Distributed/event-transport guarantees.

## Implementation Constraints

- Follow Executable Specification policy in this repository:
  - Given / When / Then readability
  - property-focused checks where applicable
- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Preserve deterministic assertions for ordering-sensitive behavior.

## Suggested File Targets

- Specs:
  - `src/test/scala/org/goldenport/cncf/...`
    - event lifecycle
    - event store/replay
    - event bus dispatch
    - policy/privilege behavior

Prefer extending existing spec suites before adding new top-level tracks.

## Required Deliverables

1. Spec additions for all EV-05 checklist bullets.
2. All related tests passing.
3. Progress updates:
- `docs/phase/phase-5-checklist.md`:
  - EV-05 `Status: DONE`
  - EV-05 detailed tasks `[x]`
- `docs/phase/phase-5.md`:
  - EV-05 checkbox `[x]`
  - Current Work Stack: E as DONE
  - mark phase closure when completion conditions are satisfied

## Validation

Run focused specs first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*Event* org.goldenport.cncf.*StateMachine* org.goldenport.cncf.*Policy*"
```

If wildcard selection is noisy, list concrete spec classes explicitly.

## Definition of Done (EV-05)

EV-05 is DONE when all conditions hold:

1. Lifecycle, persistence mode, replay, and policy paths are covered by executable specs.
2. Deterministic ordering and deterministic denial outcomes are asserted.
3. Regression linkage between state-machine transitions and event lifecycle is covered.
4. Related tests pass.
5. `phase-5.md` and `phase-5-checklist.md` are updated consistently.
6. Phase 5 completion check conditions are satisfied.
