# Cncf Runtime Match Warning Follow-up

Status: RESOLVED
Created: 2026-08-19
Repository: /Users/asami/src/dev2025/cloud-native-component-framework

## HYG-H57-WARN-001 — Exhaustive runtime profile admission

Detailed Scala compilation identified a non-exhaustive `match` at
`CncfRuntimeInstanceLifecyclePart.scala:603`. The matched value has type
`Option[SubsystemExecutionProfile]`; the prior branches handled only
`Some(SubsystemExecutionProfile.Fixed)` and `None`, although the profile type
also represents non-Fixed values. The selected repair is `case _ =>
Consequence.success(preflight)`: standalone HOME-profile admission remains
limited to `Fixed`, and all other profiles preserve the existing no-op result.

Evidence before Batch 1 expansion:

- focused regression invocation `13855-20260819T020629Z`:
  `RuntimeStandaloneUserProfileAdmissionSpec`, 3 succeeded, 0 failed;
- detailed compile invocation `13467-20260819T030205Z`: no remaining
  Batch-1 label diagnostics; and
- full-suite invocation `15856-20260819T030808Z`: 443 suites, 3,262
  succeeded, 0 failed.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-WARN-001
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-named-callers-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

Hygiene Status: RESOLVED
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-named-callers-hygiene-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: focused regression `20611-20260819T032328Z` (3 succeeded, 0 failed); focused review `CLEAN`; full test `23484-20260819T033031Z` (3,262 succeeded, 0 failed)
Acceptance Commit: reported externally after commit execution
