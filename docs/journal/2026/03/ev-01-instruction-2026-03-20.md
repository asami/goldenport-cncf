# EV-01 Instruction (Phase 5 Kickoff)

status=ready
published_at=2026-03-20
owner=cncf-runtime

## Goal

Define and implement the canonical transition lifecycle event envelope for Phase 5.

Scope is EV-01 only.

## Background

Phase 4 closed with NP-401 carried into Phase 5:
- Link state transition lifecycle to event emission envelope.

Phase 5 currently marks EV-01 as ACTIVE.

## In-Scope

1. Lifecycle taxonomy
- `before-transition`
- `after-transition`
- `transition-failed`

2. Canonical event envelope definition
- Event identity fields (event id, name, kind)
- Time field
- Correlation metadata
- Transition metadata (`machine/state/event/transition` context)
- Failure metadata (only for failed path)

3. Runtime emission points
- Determine exactly where each lifecycle event is emitted in runtime path.
- Preserve deterministic ordering with existing transition execution order.

4. Failure semantics
- Define how transition failure is mapped to lifecycle event + consequence/observation.

5. Documentation alignment
- Reflect finalized EV-01 decisions in Phase 5 tracking docs.

## Out of Scope

- EventStore implementation details (EV-02).
- EventBus subscription dispatch implementation (EV-03).
- Policy enforcement implementation (EV-04).
- Broad replay specs (EV-05).

## Implementation Constraints

- Keep core/CNCF boundary discipline from Phase 4.
- Do not parse or branch on CanonicalId.
- Do not introduce competing public APIs if existing boundary can host EV-01.
- Follow Executable Specification policy (Given/When/Then, property-focused where applicable).

## Suggested File Targets

- Runtime/event model boundary:
  - `src/main/scala/org/goldenport/cncf/...` (event envelope and emission integration points)
- Runtime transition integration:
  - existing state machine execution hook path (`unitofwork` / `statemachine` integration area)
- Specs:
  - `src/test/scala/org/goldenport/cncf/...` (transition lifecycle emission behavior)

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes implementing EV-01 lifecycle event envelope and emission points.
2. Executable specs validating:
- emission sequence
- envelope metadata presence/consistency
- failed transition emission semantics
3. Progress updates:
- `docs/phase/phase-5-checklist.md`:
  - EV-01 `Status: DONE`
  - EV-01 detailed tasks `[x]`
- `docs/phase/phase-5.md`:
  - EV-01 checkbox `[x]`
  - Current Work Stack: A as DONE and next ACTIVE item set (EV-02)

## Validation

Run focused tests first, then broader impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*Event* org.goldenport.cncf.*StateMachine*"
```

If wildcard selection is too broad, list concrete EV-01-related specs explicitly.

## Definition of Done (EV-01)

EV-01 is DONE when all conditions hold:

1. Lifecycle taxonomy and envelope are fixed in code-level boundary.
2. Emission timing is deterministic and aligned with transition runtime path.
3. Failure path emits `transition-failed` with defined metadata semantics.
4. Related executable specs pass.
5. `phase-5.md` and `phase-5-checklist.md` are updated consistently.
