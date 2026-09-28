HYG-P63-CNCF-SPEC-GWT-LEGACY: src/test/scala/org/goldenport/cncf/statemachine/GuardRuntimeSpec.scala retains four pre-existing RefGuard and GuardRuntime.build scenarios without semantic Given/When/Then clauses. Preserve legacy guard behavior and raw-expression compatibility; add the clauses only in a dedicated executable-spec hygiene change.
HYG-P63-CORE-SPEC-PRIVATE-TERM-NAMES: src/test/scala/org/goldenport/statemachine/TransitionDeciderSpec.scala retains pre-existing private fixture terms start, middle, end, and machine without the required _snake_case form. Preserve this behavior-neutral debt outside Phase 63; schedule a dedicated executable-spec hygiene change that renames only those fixtures.

## Hygiene Batch Admission — 2026-09-28

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P63-CNCF-SPEC-GWT-LEGACY
Handoff Journal: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-28
Hygiene Status: RESOLVED
Resolution Batch: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff.md
Validated On: 2026-09-28
Validation Evidence: cncf-hygiene-full-575ba1af14e74b16; receipt e4bf67dad562898b46fcce7a2c0b586098790f5b7757dc74565272f2569709d8; final focused review a762cd8f7af7905460eb54eb96d595130c5b6dd82396193ab1e40e3e38e26d1f
Acceptance Commit: reported externally after the grouped acceptance commit
