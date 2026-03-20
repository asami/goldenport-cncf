# EV-04 Instruction (Policy-Driven Visibility and Privilege Checks)

status=ready
published_at=2026-03-20
owner=cncf-runtime

## Goal

Define and implement policy-driven visibility and privilege checks for transition/event surfaces in Phase 5.

Scope is EV-04 only.

## Background

Phase 5 currently marks EV-04 as ACTIVE.

EV-01 to EV-03 are treated as completed prerequisites:
- transition lifecycle envelope exists
- EventStore baseline exists (transactional/non-transactional lanes)
- EventBus publish/dispatch baseline exists

## In-Scope

1. Policy decision points for introspection
- Define where policy checks apply for transition/event projection surfaces
- Ensure visibility filtering is deterministic

2. Policy decision points for execution entry points
- Define checks for `publish`, `dispatch`, and `replay` entry points
- Ensure denial behavior is explicit and stable

3. Privilege integration
- Implement checks using existing execution context boundaries
- Reuse existing privilege model and avoid parallel authorization APIs

4. Default behavior per surface
- Define default-allow/default-deny policy for each relevant surface
- Document rationale for defaults

5. Error/observation mapping
- Map policy denials to deterministic `Consequence`/observation semantics

6. Documentation alignment
- Reflect EV-04 completion/progress in Phase 5 tracking docs

## Out of Scope

- EventStore redesign or additional persistence architecture work (EV-02 scope).
- EventBus contract redesign (EV-03 scope).
- Full policy + replay executable specification closure beyond EV-04 minimum coverage (EV-05 full scope).
- External IAM/IdP integration.

## Implementation Constraints

- Keep core/CNCF boundary discipline from Phase 4/5.
- Do not parse or branch on CanonicalId.
- Reuse existing execution context/privilege boundaries.
- Preserve deterministic behavior for both visible outputs and denial outcomes.
- Do not introduce competing public APIs for authorization.

## Suggested File Targets

- Policy/privilege integration and projection boundary:
  - `src/main/scala/org/goldenport/cncf/...`
- Event entry points (`publish/dispatch/replay`) integration area:
  - existing event/runtime path under CNCF packages
- Specs:
  - `src/test/scala/org/goldenport/cncf/...` for visibility and denial behavior

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes for policy decision points on introspection and execution entry points.
2. Defined defaults (`allow/deny`) per surface with rationale in docs/comments where needed.
3. Deterministic denial mapping to `Consequence`/observation taxonomy.
4. Executable specs validating:
- visibility filtering behavior
- privilege acceptance/denial behavior at `publish/dispatch/replay`
- deterministic error/observation outcomes on denial
5. Progress updates:
- `docs/phase/phase-5-checklist.md`:
  - EV-04 `Status: DONE`
  - EV-04 detailed tasks `[x]`
- `docs/phase/phase-5.md`:
  - EV-04 checkbox `[x]`
  - Current Work Stack: D as DONE and next ACTIVE item set (EV-05)

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*Policy* org.goldenport.cncf.*Event*"
```

If wildcard selection is noisy, list concrete spec classes explicitly.

## Definition of Done (EV-04)

EV-04 is DONE when all conditions hold:

1. Policy decision points are fixed for introspection and execution entry surfaces.
2. Privilege checks are implemented via existing execution context boundaries.
3. Default policy behavior (`allow/deny`) is defined per surface and applied consistently.
4. Policy denials map deterministically to `Consequence`/observation outcomes.
5. Related EV-04 tests pass.
6. `phase-5.md` and `phase-5-checklist.md` are updated consistently.
