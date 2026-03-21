# AV-05 Instruction (Executable Specifications Closure)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Complete AV-05 for Phase 7 by closing executable specification coverage
for Aggregate/View CQRS behavior and regressions.

Scope is AV-05 only.

## Background

Phase 7 currently marks:
- AV-01: DONE
- AV-02: DONE
- AV-03: DONE
- AV-04: DONE
- AV-05: ACTIVE

AV-05 is the closure gate for Phase 7.

## In-Scope

1. Aggregate contract specs
- Add/complete Given/When/Then specs for:
  - command handling contract
  - invariant enforcement
  - output shape (`new state + events`)

2. View contract specs
- Add/complete specs for:
  - event-driven projection
  - rebuildability from event stream

3. Generation correctness specs
- Add/complete specs for Entity -> Aggregate/View generation mapping:
  - naming
  - structural shape
  - deterministic output

4. Synchronization behavior specs
- Add/complete specs for:
  - ordering determinism
  - eventual consistency assumptions
  - idempotent projection on replay/repeated events
  - failure policy behavior (retry/skip/dead-letter boundary)

5. Projection/meta regression specs
- Add/complete regression specs for:
  - help/describe/schema/openapi consistency
  - generated aggregate/view introspection alignment

6. End-to-end CQRS regression
- Add integrated path specs:
  - command -> event -> projection -> query
  - ensure no command/read boundary violation regression

## Out of Scope

- New runtime feature introduction outside existing AV-01~AV-04 contracts.
- Security/authorization redesign.
- Distributed projection architecture.

## Cozy Relevance

Cozy is usually not required for AV-05 itself.

Cozy escalation is needed only if:
- testable generation contract requires new metadata fields from Cozy outputs.

Default policy:
- close AV-05 using current generated metadata contracts.
- request Cozy only for concrete test-blocking metadata gaps.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Preserve deterministic behavior in specs.
- Prefer behavior specifications over example-only tests.
- Avoid introducing parallel APIs only for tests.

## Suggested File Targets

- Specs:
  - `src/test/scala/org/goldenport/cncf/entity/aggregate/...`
  - `src/test/scala/org/goldenport/cncf/entity/view/...`
  - `src/test/scala/org/goldenport/cncf/projection/...`
  - `src/test/scala/org/goldenport/cncf/action/...`
  - `src/test/scala/org/goldenport/cncf/component/...` (if integration edge is here)
- Phase tracking:
  - `docs/phase/phase-7.md`
  - `docs/phase/phase-7-checklist.md`

## Required Deliverables

1. Executable specs covering all AV-05 checklist items.
2. Focused and impacted suite test results (all green).
3. Progress updates:
- `docs/phase/phase-7-checklist.md`:
  - AV-05 `Status: DONE`
  - AV-05 detailed tasks all `[x]`
- `docs/phase/phase-7.md`:
  - AV-05 checkbox `[x]`
  - Current Work Stack: E as DONE
  - Phase status closure update (if completion check is satisfied)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.entity.aggregate.* org.goldenport.cncf.entity.view.* org.goldenport.cncf.projection.* org.goldenport.cncf.action.*"
```

If wildcard scope is noisy, list concrete spec classes explicitly.

## Definition of Done (AV-05)

AV-05 is DONE when all conditions hold:

1. Aggregate contract specs are complete and green.
2. View contract/rebuildability specs are complete and green.
3. Generation correctness specs are complete and green.
4. Synchronization determinism/idempotency/failure-policy specs are complete and green.
5. Projection/meta alignment regression specs are complete and green.
6. End-to-end command->event->projection->query regression specs are complete and green.
7. `phase-7.md` and `phase-7-checklist.md` are updated consistently.
