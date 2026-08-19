# Hygiene Resolution Batch Handoff

Status: BLOCKED
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md

## Purpose

Prepare a test-source-only cleanup batch for the 419 deprecation warnings
reported by the full suite, without mixing it with production source changes or
silently changing executable-specification intent.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-H57-DEPRECATION-003 | `docs/journal/2026/08/2026-08-19-deprecation-warning-hygiene-follow-up.md` | `16358-20260819T021219Z` | HP-001 | Remove only mechanically replaceable test-source deprecation warnings. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-003
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2025/cloud-native-component-framework`.
- Allowed behavior change: none.
- Preserve paths: `src/main/scala/**`, all existing dirty Scala and journal
  paths, and every test file not named by the detail inventory.
- Prohibited expansion: production migration, test expectation changes,
  fixture semantic changes, public API change, compatibility layer, or warning
  suppression.

## HP-001 — Inventory and migrate mechanical test callers

- Hygiene IDs: HYG-H57-DEPRECATION-003
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Targets: not yet frozen; the prior full-suite evidence contains only the
  aggregate count and cannot identify exact executable-specification files.
- Allowed repair: none while blocked.
- Prohibited expansion: editing a test before its deprecation location and
  compiler-announced replacement are recorded; changing Given/When/Then intent
  or assertions to accommodate a migration.
- Focused validation: detail-enabled test compilation to inventory locations;
  then each affected specification's focused test command and `git diff --check`.
- Dependencies: prerequisite below.

## Blocker

Run a detail-enabled test compilation that emits every test-source deprecation
location and replacement. Partition only direct, compiler-announced call-site
spelling changes into this batch. Route any warning whose fix changes fixture,
assertion, test setup, or executable-specification semantics out of the batch.

## Final Focused Review

- Not runnable until exact test files and focused commands are frozen.
- Required checks after admission: preserved Given/When/Then wording and
  behavior assertions; no production paths changed; no semantic test rewrite.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cloud-native-component-framework`: `sbt --batch test`

## Completion Contract

- Do not set this batch or HYG-H57-DEPRECATION-003 to `RESOLVED` while blocked.
- After admission, commit only after focused review and one full validation gate
  pass on the reviewed tree.

## Non-goals

- Treating the aggregate warning count as a target-file inventory.
- Modifying production sources or test semantics.
