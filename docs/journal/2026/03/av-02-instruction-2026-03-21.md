# AV-02 Instruction (CML-based Aggregate/View Generation)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Close AV-02 for Phase 7 by finalizing CML-based Aggregate/View generation
and completing remaining migration/closure tasks.

Scope is AV-02 only.

## Background

Phase 7 currently marks:
- AV-01: DONE
- AV-02: ACTIVE

Current AV-02 status:
- Cross-repository connection slice (Cozy -> SimpleModeler -> CNCF) is implemented.
- CNCF metadata hooks and bootstrap registration are implemented and tested.
- Remaining open item is migration-note closure from manual model path
  to generated source-of-truth.

## In-Scope

1. Generation path confirmation
- Confirm generated DomainComponent metadata path is canonical:
  - `aggregateDefinitions`
  - `viewDefinitions`
- Confirm CNCF bootstrap consumes this metadata without manual patching.

2. Migration note completion (remaining AV-02 task)
- Add explicit migration note covering:
  - old manual aggregate/view implementation path
  - new generated source-of-truth path
  - compatibility and removal policy for legacy/manual definitions
  - rollback policy (if required)

3. Documentation alignment
- Align Phase 7 docs to AV-02 DONE once migration note is in place and verified.

## Out of Scope

- Synchronization runtime policy implementation (AV-03).
- Projection/meta surface completion (AV-04).
- Full CQRS executable-spec closure (AV-05).
- New CML grammar expansion beyond AV-01 agreed boundary.

## Cozy Coordination Rule

If additional CML-side changes are required to close AV-02:
- Request Cozy development explicitly.
- CNCF must not fork or redefine CML source-of-truth independently.

Required Cozy request payload:
1. Target metadata shape and field semantics.
2. Expected generated Scala output shape.
3. Required compatibility/breaking policy.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not introduce competing metadata paths.
- Preserve deterministic bootstrap behavior.
- Keep AV-02 closure limited to generation-path completion and migration clarity.

## Suggested File Targets

- Phase tracking:
  - `docs/phase/phase-7.md`
  - `docs/phase/phase-7-checklist.md`
- Migration note destination (choose one):
  - `docs/journal/2026/03/aggregate-view-generation-migration-note-2026-03-21.md`
  - or append to `docs/journal/2026/03/aggregate-view-design-handoff.md`
- CNCF verification specs:
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryAggregateViewBootstrapSpec.scala`

## Required Deliverables

1. Migration note documenting manual -> generated transition policy.
2. AV-02 verification result update (CNCF/Cozy/SimpleModeler evidence references).
3. Progress updates:
- `docs/phase/phase-7-checklist.md`:
  - AV-02 `Status: DONE`
  - AV-02 detailed tasks all `[x]`
- `docs/phase/phase-7.md`:
  - AV-02 checkbox `[x]`
  - Current Work Stack: B as DONE and next ACTIVE item set (AV-03)

## Validation

Run focused tests first.

Example command:

```bash
sbt "testOnly org.goldenport.cncf.component.ComponentFactoryAggregateViewBootstrapSpec"
```

If broader regression is needed, add impacted aggregate/view specs explicitly.

## Definition of Done (AV-02)

AV-02 is DONE when all conditions hold:

1. Generation metadata path (Cozy -> SimpleModeler -> CNCF) is confirmed as canonical.
2. CNCF bootstrap consumes generated metadata deterministically.
3. Migration note from manual to generated source-of-truth is documented.
4. AV-02 related tests pass.
5. `phase-7.md` and `phase-7-checklist.md` are updated consistently.
