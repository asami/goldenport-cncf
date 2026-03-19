# EV-03 Instruction (EventBus Publish/Dispatch/Subscription)

status=ready
published_at=2026-03-20
owner=cncf-runtime

## Goal

Define and implement EventBus runtime flow for Phase 5:
- publish
- register subscription
- resolve and dispatch

Scope is EV-03 only.

## Background

Phase 5 currently marks EV-03 as ACTIVE.

EV-01 and EV-02 are treated as completed prerequisites:
- lifecycle event envelope is available
- EventStore baseline exists with transactional/non-transactional lanes

## In-Scope

1. EventBus contract and subscription model
- Freeze `publish/register` contract
- Define subscription shape and resolution semantics

2. Dispatch execution model
- Resolve subscriptions deterministically
- Dispatch via ActionCall-centric execution path
- Keep ordering deterministic for same input

3. Dispatch policy baseline
- Default synchronous dispatch behavior
- Explicit async/job extension point boundary (design-level, not full implementation)

4. Persistence interaction boundary
- For persistent events, align with EventStore contract already defined in EV-02
- For ephemeral events, ensure dispatch works without requiring persistence

5. Failure semantics
- Map dispatch failures to existing `Consequence`/observation taxonomy consistently

6. Documentation alignment
- Reflect EV-03 completion/progress in Phase 5 tracking docs

## Out of Scope

- EventStore contract redesign or additional persistence architecture changes (EV-02 scope).
- Policy visibility/privilege implementation (EV-04).
- Full replay/policy executable specification completion (EV-05 full scope).
- Distributed transport implementation.

## Implementation Constraints

- Keep core/CNCF boundary discipline from Phase 4/5.
- Do not parse or branch on CanonicalId.
- Reuse existing runtime boundaries; do not introduce competing public APIs.
- Preserve deterministic dispatch ordering.
- Do not bypass ActionCall execution semantics.

## Suggested File Targets

- EventBus model/interface/implementation area:
  - `src/main/scala/org/goldenport/cncf/event/...`
- Runtime integration points:
  - existing execution/action call path under CNCF runtime packages
- Specs:
  - `src/test/scala/org/goldenport/cncf/...` for publish/register/dispatch behavior

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes for EventBus publish/register/dispatch baseline.
2. Deterministic subscription resolution and dispatch ordering.
3. ActionCall-path integration for event-triggered execution.
4. Ephemeral event dispatch behavior without mandatory persistence dependency.
5. Executable specs validating:
- publish/register contract behavior
- deterministic ordering
- dispatch success/failure consequence mapping
- ephemeral vs persistent dispatch boundary behavior
6. Progress updates:
- `docs/phase/phase-5-checklist.md`:
  - EV-03 `Status: DONE`
  - EV-03 detailed tasks `[x]`
- `docs/phase/phase-5.md`:
  - EV-03 checkbox `[x]`
  - Current Work Stack: C as DONE and next ACTIVE item set (EV-04)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*EventBus* org.goldenport.cncf.*Event*"
```

If wildcard selection is noisy, list concrete spec classes explicitly.

## Definition of Done (EV-03)

EV-03 is DONE when all conditions hold:

1. EventBus `publish/register` contract and subscription resolution are fixed and implemented.
2. Dispatch executes through ActionCall path with deterministic ordering.
3. Ephemeral events dispatch correctly without persistence requirement.
4. Dispatch failure mapping is covered by executable specs.
5. Related EV-03 tests pass.
6. `phase-5.md` and `phase-5-checklist.md` are updated consistently.
