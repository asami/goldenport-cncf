# EV-02 Instruction (EventStore Baseline)

status=ready
published_at=2026-03-20
owner=cncf-runtime

## Goal

Define and implement EventStore baseline for Phase 5:
- append
- load
- query
- replay

Scope is EV-02 only.

## Background

Phase 5 currently marks EV-02 as ACTIVE.

EV-02 must satisfy two event persistence lanes:

1. transactional lane
- event is persisted atomically with domain data update in the same transaction

2. non-transactional lane
- event (for example error/incident event) is emitted and managed in EventStore independently from domain update transaction

## In-Scope

1. EventStore contract and semantics
- Freeze interface and failure/result semantics
- Align with existing `Consequence`/observation taxonomy

2. Event record model
- Define baseline record fields (id, name/kind, payload/attributes, createdAt, persistent, status)
- Preserve deterministic query/replay ordering

3. Append paths (mandatory two-lane support)
- transactional append path (same transaction atomicity with domain update)
- non-transactional append path (independent append for non-domain-tx events)

4. Query/replay baseline
- Implement query filters and deterministic ordering
- Implement replay entry behavior and idempotency assumptions

5. Documentation alignment
- Reflect EV-02 completion/progress in Phase 5 tracking docs

## Out of Scope

- EventBus subscription routing/dispatch implementation (EV-03).
- Policy visibility/privilege implementation (EV-04).
- Broad cross-cutting executable spec completion beyond EV-02 required coverage (EV-05 full scope).

## Implementation Constraints

- Keep core/CNCF boundary discipline from Phase 4 and Phase 5 docs.
- Do not parse or branch on CanonicalId.
- Do not introduce competing public APIs if existing boundaries can host EV-02.
- Preserve deterministic behavior for ordering-sensitive flows.
- Avoid persistence architecture redesign beyond EventStore baseline needed for EV-02.

## Suggested File Targets

- EventStore model/interface/implementation area:
  - `src/main/scala/org/goldenport/cncf/event/...`
  - related runtime integration points under existing CNCF runtime packages
- Specs:
  - `src/test/scala/org/goldenport/cncf/...` for EventStore append/query/replay and lane semantics

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes for EventStore baseline contract and implementation.
2. Two-lane append support:
- transactional lane (atomic with domain update tx)
- non-transactional lane (independent event append, e.g. error events)
3. Executable specs validating:
- append/load/query baseline behavior
- deterministic ordering
- transactional atomicity rule (contract-level guarantee)
- non-transactional lane behavior
4. Progress updates:
- `docs/phase/phase-5-checklist.md`:
  - EV-02 `Status: DONE`
  - EV-02 detailed tasks `[x]`
- `docs/phase/phase-5.md`:
  - EV-02 checkbox `[x]`
  - Current Work Stack: B as DONE and next ACTIVE item set (EV-03)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*EventStore* org.goldenport.cncf.*Event*"
```

If wildcard matching is noisy, list concrete spec classes explicitly.

## Definition of Done (EV-02)

EV-02 is DONE when all conditions hold:

1. EventStore baseline contract (`append/load/query/replay`) is fixed and implemented.
2. Transactional and non-transactional append lanes are both implemented and validated.
3. Deterministic query/replay ordering is defined and covered by executable specs.
4. Related EV-02 tests pass.
5. `phase-5.md` and `phase-5-checklist.md` are updated consistently.
