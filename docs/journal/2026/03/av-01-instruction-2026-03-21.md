# AV-01 Instruction (Semantic Model and CML/AST Boundary)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Implement AV-01 for Phase 7 by defining and freezing the canonical
semantic model and CML/AST boundary for Aggregate/View generation.

Scope is AV-01 only.

## Background

Phase 7 currently marks:
- AV-01: ACTIVE
- AV-02: PLANNED
- AV-03: PLANNED
- AV-04: PLANNED
- AV-05: PLANNED

AV-01 is based on:
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/aggregate-view-design-handoff.md`

Core direction:
- `Entity` is the single source of truth.
- `Aggregate` (write model) and `View` (read model) are derived projections.
- Event is the bridge between aggregate update and view projection.

## In-Scope

1. Semantic contract freeze
- Define canonical Aggregate contract:
  - command input
  - invariant enforcement
  - result: `(new state, events)`
- Define canonical View contract:
  - event-driven projection
  - rebuildability from event stream

2. CML/AST boundary freeze
- Define AST extension shape:
  - `EntityDef.aggregate: Option[AggregateDef]`
  - `EntityDef.view: Option[ViewDef]`
- Define minimum fields:
  - `AggregateDef(commands, state, invariants)`
  - `ViewDef(attributes, queries)`

3. Constraint freeze
- Aggregate must remain pure and deterministic.
- View must be reconstructable from events.
- Event must not depend on View.
- Command/read boundary must prohibit cross-side mutation.

4. Runtime boundary note
- Confirm execution model compatibility:
  - `ActionCall -> Task -> Job`
- AV-01 defines contract and model boundaries only.
- Generation implementation is AV-02 scope.

5. Documentation alignment
- Update phase/checklist docs if wording changes are needed for AV-01 completion.

## Out of Scope

- Code generation implementation (`Entity -> Aggregate/View`) (AV-02).
- Synchronization runtime implementation (AV-03).
- Projection/meta surface implementation changes (AV-04).
- Full executable spec closure (AV-05).

## CML Change Handling (Cozy Coordination)

If AV-01 requires CML grammar/AST changes outside CNCF ownership,
request Cozy development explicitly.

Escalation trigger:
- CNCF cannot safely define/maintain required CML structures in this repository alone.
- Parser/modeler changes are needed in Cozy source of truth.

Required Cozy request payload:
1. Required AST shape (`EntityDef`, `AggregateDef`, `ViewDef`).
2. Minimum field semantics and constraints.
3. Backward-compatibility expectation (breaking allowed for this workstream).
4. Expected generated model contract for CNCF integration.

While waiting for Cozy update:
- Keep CNCF-side model boundary docs and adapter contract ready.
- Do not introduce parallel or competing CML definitions.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Do not introduce duplicate public APIs for aggregate/view semantics.
- Keep contracts deterministic and testable.

## Suggested File Targets

- Phase tracking:
  - `docs/phase/phase-7.md`
  - `docs/phase/phase-7-checklist.md`
- Design/handoff alignment:
  - `docs/journal/2026/03/aggregate-view-design-handoff.md`
- CNCF semantic model / AST bridge (if present):
  - `src/main/scala/org/goldenport/cncf/...` (model/parser boundary area)
- Specs:
  - `src/test/scala/org/goldenport/cncf/...` (semantic contract tests)

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. AV-01 semantic contract document updates (Aggregate/View/Event boundary).
2. AV-01 CML/AST boundary definition updates (`EntityDef/AggregateDef/ViewDef`).
3. Constraint definition updates (determinism/rebuildability/boundary rules).
4. Executable specs (or skeleton specs) validating semantic contract invariants.
5. Cozy escalation note (only if CML source changes are required).
6. Progress updates:
- `docs/phase/phase-7-checklist.md`:
  - AV-01 `Status: DONE`
  - AV-01 detailed tasks `[x]`
- `docs/phase/phase-7.md`:
  - AV-01 checkbox `[x]`
  - Current Work Stack: A as DONE and next ACTIVE item set (AV-02)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*Aggregate* org.goldenport.cncf.*View*"
```

If wildcard matching is noisy, list concrete spec classes explicitly.

## Definition of Done (AV-01)

AV-01 is DONE when all conditions hold:

1. Aggregate/View semantic contracts are explicit and stable.
2. CML/AST extension boundary for Entity->Aggregate/View is explicitly defined.
3. Determinism/rebuildability/boundary constraints are documented and testable.
4. CNCF runtime boundary with Job/Task execution model is documented as compatible.
5. Cozy escalation is issued when required (and tracked), without forking CML truth.
6. Related AV-01 tests pass (or agreed skeleton coverage is in place).
7. `phase-7.md` and `phase-7-checklist.md` are updated consistently.
