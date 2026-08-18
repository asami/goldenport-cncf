# Hygiene Resolution Task Handoff

Status: SUPERSEDED
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cozy
Superseded By: `docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md`

## Purpose

Move Cozy CAR-lint test work under the repository `target/` boundary without
changing lint or publication behavior.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P57.4-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

## Included Hygiene

| ID | Source | Evidence | Target | Risk | Required outcome |
| --- | --- | --- | --- | --- | --- |
| HYG-P57.4-002 | `docs/journal/2026/08/2026-08-14-phase-57.4-hygiene-follow-up.md` | The `CozyCarLintSpec.scala` temporary-directory helper creates work outside the repository artifact boundary; its lint behavior previously passed 21/21 and 118/118. | `src/test/scala/cozy/lint/CozyCarLintSpec.scala` | Low production risk; test-artifact containment. | Keep assertions and lint behavior intact while relocating test work under `target/`. |

## Frozen Boundary

- Allowed repositories: `cozy` only.
- Allowed target programs/files: `src/test/scala/cozy/lint/CozyCarLintSpec.scala`
  and mechanically required local fixtures.
- Allowed behavior change: test artifact location only.
- Prohibited expansion: CAR lint semantics, archive publication, fixture
  redesign, production source, and all other Hygiene records.

## Required Validation

1. Run `CozyCarLintSpec` and prove work is contained under `target/`.
2. Review the complete specification for retained assertions and cleanup.
3. Run the full Cozy validation required by `cncf-goal-task`.

## Completion Contract

- Resolve every included ID or report it unchanged with evidence.
- Update each source Hygiene record with commit and validation evidence.
- Change source status to RESOLVED only after accepted validation and commit.
- Do not absorb Development Candidates or unrelated Hygiene.

## Dependencies and Ordering

Run after any user-owned Cozy video work is isolated; that work is not part of
this handoff.

## Non-goals

- Altering CAR lint decisions or canonical publication output.
- Changing source outside the test-fixture boundary.
