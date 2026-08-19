# Hygiene Resolution Task Handoff

Status: CLOSED (SUPERSEDED)
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cozy
Superseded By: `docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md`

## Purpose

Repair the recorded Cozy article-media full-suite fixture isolation and
concurrency reliability debt without changing CAR publication behavior.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P57.4-003
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-08-19

## Included Hygiene

| ID | Source | Evidence | Target | Risk | Required outcome |
| --- | --- | --- | --- | --- | --- |
| HYG-P57.4-003 | `docs/journal/2026/08/2026-08-14-phase-57.4-hygiene-follow-up.md` | Full-suite run `73349-20260813T194937Z` had 13 failures from a retained temporary `user.home` whose `/var` path aliases `/private/var`, plus one article-media latch assertion failure. | `src/test/scala/cozy/CozyBokProjectSpec.scala`; `src/test/scala/cozy/publication/CozyArticleMediaPublicationOrchestrationSpec.scala`; `src/test/scala/cozy/publication/CozyArticleMediaVideoCommandSpec.scala` | Medium test-reliability risk; no demonstrated CAR publication risk. | Isolate and clean the test `user.home` fixture, stabilize the recorded assertion, then rerun the Cozy full suite. |

## Frozen Boundary

- Allowed repositories: `cozy` only.
- Allowed target programs/files: the three named test specifications and
  mechanically required local test fixtures.
- Allowed behavior change: test-fixture lifecycle and deterministic assertion
  coordination only.
- Prohibited expansion: production article-media behavior, CAR archive/lint/
  modeler producer paths, publication semantics, and other Hygiene records.

## Required Validation

1. Run each named article-media and BOK specification with isolated fixtures.
2. Verify the `user.home` lifecycle uses a canonical path and cleans up after
   each test boundary; review the concurrency assertion for determinism.
3. Run the full Cozy suite and retain its exact result as final evidence.

## Completion Contract

- Resolve every included ID or report it unchanged with evidence.
- Update each source Hygiene record with commit and validation evidence.
- Change source status to RESOLVED only after accepted validation and commit.
- Do not absorb Development Candidates or unrelated Hygiene.

## Dependencies and Ordering

Run after any user-owned Cozy video work is isolated; that work is not part of
this handoff.

## Non-goals

- Changing CAR publication behavior or rewriting article-media product flows.
- Treating the original nonzero full-suite result as a passing run.
