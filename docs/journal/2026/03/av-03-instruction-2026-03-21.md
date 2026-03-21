# AV-03 Instruction (Aggregate-to-View Synchronization Policy)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Implement AV-03 for Phase 7 by defining and implementing synchronization
policy between Aggregate updates and View projection updates.

Scope is AV-03 only.

## Background

Phase 7 currently marks:
- AV-01: DONE
- AV-02: DONE
- AV-03: ACTIVE

AV-03 must align with:
- Phase 5 Event foundations (Event envelope, EventBus/EventStore baseline)
- Phase 6 Job/Task execution model (`ActionCall -> Task -> Job`)
- AV-01 semantic boundary (Entity as source, Aggregate/View as projections)

## In-Scope

1. Synchronization flow contract
- Define command-side flow:
  - `Command -> Aggregate -> (new state, Event(s))`
- Define read-side projection flow:
  - `Event -> Projection -> View update`

2. Ordering and consistency policy
- Define deterministic ordering contract for projection application.
- Define eventual consistency level and guarantees.
- Define idempotent projection requirement for repeated/replayed events.

3. Failure and retry policy
- Define projection failure behavior:
  - retry / skip / dead-letter boundary (as selected by implementation)
- Define observable error mapping and minimum diagnostics.
- Ensure command-side success/failure semantics remain explicit.

4. Runtime integration boundary
- Integrate synchronization path with current Job/Event runtime boundaries.
- Preserve Task-first execution rule; avoid direct bypass paths.
- Keep Event as the bridge (no View-dependent Event design).

5. Documentation and phase alignment
- Reflect AV-03 implementation result in phase tracking docs.

## Out of Scope

- Meta/projection surface completion work (AV-04).
- Full executable spec closure across Phase 7 (AV-05).
- Distributed projection transport/replication.
- Security model redesign.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Preserve deterministic behavior under replay/retry conditions.
- Do not introduce competing synchronization APIs.
- Keep synchronization policy explicit and auditable in code/spec.

## Suggested File Targets

- Synchronization runtime implementation:
  - `src/main/scala/org/goldenport/cncf/entity/aggregate/...`
  - `src/main/scala/org/goldenport/cncf/entity/view/...`
  - `src/main/scala/org/goldenport/cncf/event/...`
  - `src/main/scala/org/goldenport/cncf/job/...`
- Specs:
  - `src/test/scala/org/goldenport/cncf/entity/aggregate/...`
  - `src/test/scala/org/goldenport/cncf/entity/view/...`
  - `src/test/scala/org/goldenport/cncf/event/...`
  - `src/test/scala/org/goldenport/cncf/job/...` (if integration path is covered here)
- Phase docs:
  - `docs/phase/phase-7.md`
  - `docs/phase/phase-7-checklist.md`

## Required Deliverables

1. Synchronization policy definition (ordering/eventual consistency/idempotency/failure).
2. Runtime implementation for aggregate-event-view synchronization.
3. Executable specs validating:
- command->event->projection->view flow
- deterministic ordering
- idempotent projection on repeated/replayed events
- failure and retry/skip behavior
4. Progress updates:
- `docs/phase/phase-7-checklist.md`:
  - AV-03 `Status: DONE`
  - AV-03 detailed tasks `[x]`
- `docs/phase/phase-7.md`:
  - AV-03 checkbox `[x]`
  - Current Work Stack: C as DONE and next ACTIVE item set (AV-04)

## Validation

Run focused tests first, then impacted integration suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.entity.aggregate.* org.goldenport.cncf.entity.view.* org.goldenport.cncf.event.*"
```

If wildcard scope is noisy, list concrete spec classes explicitly.

## Definition of Done (AV-03)

AV-03 is DONE when all conditions hold:

1. Aggregate->Event->Projection->View synchronization contract is explicit.
2. Deterministic ordering and eventual-consistency assumptions are defined and implemented.
3. Idempotent projection behavior for replay/repeated events is validated.
4. Failure handling policy (retry/skip/dead-letter boundary) is implemented and test-covered.
5. Job/Task execution boundary compatibility is preserved.
6. Related AV-03 tests pass.
7. `phase-7.md` and `phase-7-checklist.md` are updated consistently.
