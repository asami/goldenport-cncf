# Hygiene Resolution Batch Handoff

Status: BLOCKED
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-compiler-warning-hygiene-batch-handoff.md

## Purpose

Prepare a distinct non-deprecation compiler-warning cleanup batch. It preserves
the separation from deprecated API migration and does not treat type-safety or
diagnostic behavior as formatting work.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-H57-COMPILER-WARNING-004 | `docs/journal/2026/08/2026-08-19-deprecation-warning-hygiene-follow-up.md` | `15277-20260819T020915Z` | HP-001, HP-002 | Remove only behavior-preserving compiler warnings after each case is admitted. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-COMPILER-WARNING-004
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-compiler-warning-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2025/cloud-native-component-framework`.
- Allowed behavior change: none.
- Preserve paths: all existing dirty Scala and journal paths, all deprecated API
  handoffs, and every source file not named by a subsequently admitted package.
- Prohibited expansion: warning suppression, exception-policy changes, generic
  casting to evade type safety, new diagnostics behavior, schema/API changes,
  test expectation changes, and unrelated source cleanup.

## HP-001 — Formatting and syntax-only warnings

- Hygiene IDs: HYG-H57-COMPILER-WARNING-004
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Targets:
  - `src/main/scala/org/goldenport/cncf/action/ActionEngine.scala:153`
  - `src/main/scala/org/goldenport/cncf/component/ComponentDescriptor.scala:198`
  - `src/main/scala/org/goldenport/cncf/job/JobEngine.scala:1051-1099`
- Allowed repair: remove only the redundant `try` block with no catch/finally
  and correct Scala 3 indentation without changing statement nesting, control
  flow, exception behavior, or bindings.
- Prohibited expansion: add a catch/finally, move statements across a scope,
  or refactor the affected methods.
- Focused validation: compile the three targets and their nearest existing
  specifications; `git diff --check`.
- Dependencies: None.

## HP-002 — Pattern-match and erased-generic warnings

- Hygiene IDs: HYG-H57-COMPILER-WARNING-004
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Targets:
  - `src/main/scala/org/goldenport/cncf/config/CncfConfigurationBindingStringCodec.scala:35`
  - `src/main/scala/org/goldenport/cncf/config/CncfConfigurationEnvironmentBindingCodec.scala:83`
  - `src/main/scala/org/goldenport/cncf/observability/ConclusionDiagnostics.scala:178`
  - `src/main/scala/org/goldenport/cncf/entity/aggregate/AggregateSpace.scala:131-133`
  - `src/main/scala/org/goldenport/cncf/entity/view/ViewSpace.scala:213-215,255-257`
- Allowed repair: none while blocked.
- Prohibited expansion: replace an unreachable fallback with a new error policy,
  hide an unchecked test using casts, or add fallback rendering for unreviewed
  `Conclusion` cause kinds.
- Focused validation: after admission, nearest codec/diagnostics/aggregate/view
  executable specifications and a detailed production compilation.
- Dependencies: blocker below.

## Blocker

HP-002 cannot be admitted as Hygiene until the following facts are recorded:

1. `case null` in both codecs preserves the current null-input contract;
2. each `ConclusionDiagnostics` missing cause kind has an existing intended
   rendering, rather than requiring a new diagnostic contract; and
3. the erased-generic `Consequence` patterns have a type-safe replacement with
   unchanged success/failure dispatch.

If any fact needs a new observable diagnostic, error, or generic-dispatch
contract, remove that target from this batch and route it to an ordinary task or
Development Candidate.

## Final Focused Review

- Not runnable until HP-002 is admitted and exact behavior-preserving edits are
  frozen.
- Required checks after admission: warning removal, unchanged control flow,
  unchanged null/failure/diagnostic behavior, package-focused evidence, and
  scope containment.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cloud-native-component-framework`: `sbt --batch test`

## Completion Contract

- Do not set this batch or HYG-H57-COMPILER-WARNING-004 to `RESOLVED` while
  HP-002 remains blocked.
- After all packages are admitted, commit only after the focused review and one
  final full validation gate pass on the reviewed tree.

## Non-goals

- Deprecated API migration, warning suppression, or unreviewed behavioral
  changes.
