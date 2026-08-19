# Conclusion Diagnostics Availability-Kind Development Task

Status: RESOLVED
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-task /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-conclusion-diagnostics-availability-kind-development-task.md

## Purpose

Define and implement the observable diagnostic-key contract for the three
availability `Cause.Kind` values that `ConclusionDiagnostics` does not yet
classify. This is development work, not compiler-warning Hygiene: the chosen
keys flow into metrics, dashboards, and diagnostic projections.

## Source Evidence

- Source Hygiene record:
  `docs/journal/2026/08/2026-08-19-compiler-warning-hygiene-batch-handoff.md`
  (`HYG-H57-COMPILER-WARNING-004`, HP-002).
- `Cause.Kind` declares `NotRunning`, `ConnectionRefused`, and `Unreachable`
  in `simplemodeling-lib`, while
  `ConclusionDiagnostics._diagnostic_key` has no cases for them.
- No current CNCF producer, diagnostic-key assertion, or existing rendering
  policy establishes their intended keys.

## Frozen Boundary

### Production target

- `src/main/scala/org/goldenport/cncf/observability/ConclusionDiagnostics.scala`

### Required contract

Add explicit diagnostic-key mappings:

| Cause kind | `causeKind` value (preserved) | New `diagnosticKey` |
| --- | --- | --- |
| `NotRunning` | `not-running` | `not_running` |
| `ConnectionRefused` | `connection-refused` | `connection_refused` |
| `Unreachable` | `unreachable` | `unreachable` |

The pre-existing `causeKind` projection remains the exact `Cause.Kind.name`.
Only the diagnostic key follows the existing underscore token convention.

### Executable evidence

- Add or extend an executable specification for `ConclusionDiagnostics` that
  constructs one `Conclusion` per availability kind and proves its exact
  `diagnosticKey`, preserved `causeKind`, and unchanged status projection.
- Keep the current mappings for all already-classified kinds unchanged.

## Non-goals

- Do not introduce a generic `case _`, map these kinds to `unknown`, or make
  taxonomy fallback the availability policy.
- Do not alter `Cause.Kind`, availability classification in
  `simplemodeling-lib`, HTTP status policy, schemas, or dashboard layout.
- Do not absorb `AggregateSpace` or `ViewSpace` erased-generic warnings.

## Validation

1. Run the focused `ConclusionDiagnostics` executable specification.
2. Compile the production and test targets without the non-exhaustive
   `Cause.Kind` warning.
3. Run the full CNCF test gate selected by `cncf-goal-task` before acceptance.

## Completion Contract

Complete only when all three mappings and their executable evidence are
accepted, no catch-all changes diagnostic behavior, and the task journal
records focused/full validation and acceptance evidence.

## Development Candidate

`DEV-009` owns this bounded diagnostic contract until an explicitly selected
Phase adopts it. It is retained in Strategy 9.4 because no current CNCF
consumer supplies a Phase admission driver.

Candidate Triage: COMPLETED
Canonical ID: DEV-009
Disposition: STRATEGY_ITEM
Strategy Record: docs/strategy/cncf-development-strategy.md#9-development-item-status
Target Phase: -
Triaged On: 2026-08-19

## Resolution Evidence

- Focused validation: SBT invocation `36203-20260819T085108Z` — 28 succeeded
  across 2 suites with 0 failures and no warnings.
- Full task review: `CLEAN`; no Current Task Blockers, Task Hygiene, or
  Development Candidates.
- Final validation: SBT invocation `37876-20260819T085428Z` — 3,262 succeeded
  across 444 suites with 0 failures and no warnings.
- Acceptance Commit: reported externally after commit execution.
