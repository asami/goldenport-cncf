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

## Core Hygiene Batch Admission — 2026-09-28

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P63-CORE-SPEC-PRIVATE-TERM-NAMES
Handoff Journal: goldenport-core:docs/journal/2026/09/2026-09-28-core-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-28
Hygiene Status: RESOLVED
Resolution Batch: goldenport-core:docs/journal/2026/09/2026-09-28-core-hygiene-resolution-batch-handoff.md
Validated On: 2026-09-28
Validation Evidence: goldenport-core: invocation `core-hygiene-output-full-9aa7e76ae1584ae9`; 462 passed, 0 canceled, 0 ignored, 127 existing pending; receipt `6b809b9ef2293828b9d1af23eb0761c82efd205c30a461da6e7dc83dfce878ea`; lock released; goldenport-cncf: invocation `core-hygiene-output-ledger-full-01e8016228044729`; 3815 passed, 13 canceled, 1 ignored, 46 existing pending; receipt `8233e1dc3cba9efd396a784534b8704c6e38a4fc24b0f49be9bda229081495e7`; lock released; CLEAN focused review `25d3bcfafaf9da0952ad3438d3c4466f49ff4a8324961eab764370d7af1bd91c`
