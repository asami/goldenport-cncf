# AV-04 Instruction (Projection/Meta Surface Alignment)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Implement AV-04 for Phase 7 by aligning projection/meta surfaces with
the finalized generated Aggregate/View model.

Scope is AV-04 only.

## Background

Phase 7 currently marks:
- AV-01: DONE
- AV-02: DONE
- AV-03: DONE
- AV-04: ACTIVE

AV-04 must align runtime exposure and docs/spec surfaces so that:
- `meta.*` outputs match authoritative API set
- schema/help/openapi projections reflect generated aggregate/view structure
- projection output remains deterministic

## In-Scope

1. Meta API alignment
- Align `meta.*` operation exposure with aggregate/view runtime model.
- Remove or fix ambiguous meta entries.

2. Projection alignment
- Align help/schema/openapi projection outputs with:
  - generated Aggregate/View names
  - command/read boundary contracts
- Reflect named views and aggregate collections in introspection outputs where applicable.

3. Determinism and regression safety
- Ensure projection output is deterministic.
- Add/update regression specs for projection/meta consistency.

4. Documentation alignment
- Update docs that list or describe meta/projection surfaces.

## Out of Scope

- New synchronization behavior (AV-03 scope).
- Full Phase 7 executable-spec closure (AV-05 scope).
- Security model redesign.

## Cozy Relevance

Cozy is **not mandatory** for base AV-04 work.

Cozy involvement is required only if:
- projection/meta alignment needs new generated metadata fields not currently emitted by Cozy, or
- CML/modeler output shape must change to represent new introspection elements.

Default policy:
- Implement AV-04 first on CNCF side using current generated metadata.
- Escalate to Cozy only when a concrete metadata gap is identified.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not introduce competing meta APIs.
- Keep projection output deterministic and test-backed.

## Suggested File Targets

- Runtime/projection:
  - `src/main/scala/org/goldenport/cncf/projection/...`
  - `src/main/scala/org/goldenport/cncf/component/...`
  - `src/main/scala/org/goldenport/cncf/cli/...`
- Specs:
  - `src/test/scala/org/goldenport/cncf/projection/...`
  - `src/test/scala/org/goldenport/cncf/component/...`
  - `src/test/scala/org/goldenport/cncf/action/...` (if affected by introspection mapping)
- Phase tracking:
  - `docs/phase/phase-7.md`
  - `docs/phase/phase-7-checklist.md`

## Required Deliverables

1. Code changes aligning meta/projection surfaces with generated aggregate/view model.
2. Regression specs proving deterministic and consistent projection/meta outputs.
3. Doc updates for aligned meta/projection surface definitions.
4. Progress updates:
- `docs/phase/phase-7-checklist.md`:
  - AV-04 `Status: DONE`
  - AV-04 detailed tasks all `[x]`
- `docs/phase/phase-7.md`:
  - AV-04 checkbox `[x]`
  - Current Work Stack: D as DONE and next ACTIVE item set (AV-05)

## Validation

Run focused tests first, then impacted suite.

Example command:

```bash
sbt "testOnly org.goldenport.cncf.projection.* org.goldenport.cncf.component.* org.goldenport.cncf.action.*"
```

If wildcard scope is noisy, list concrete spec classes explicitly.

## Definition of Done (AV-04)

AV-04 is DONE when all conditions hold:

1. `meta.*` exposure is consistent with runtime authoritative API set.
2. help/schema/openapi projections reflect generated Aggregate/View structure.
3. Projection output is deterministic and regression-tested.
4. Related docs/spec surfaces are aligned.
5. `phase-7.md` and `phase-7-checklist.md` are updated consistently.
