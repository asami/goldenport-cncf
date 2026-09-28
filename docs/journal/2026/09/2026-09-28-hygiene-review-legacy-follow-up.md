# Hygiene review legacy follow-up

Created: 2026-09-28
Source: independent Hygiene batch review retained by goal CNCF-HYGIENE-20260928.
User admission: 残り３件も修正して。

The original review records below are reproduced exactly. Their OPEN descriptions are historical review evidence; subsequent handoff and resolution fields record actual follow-up outcomes.

## HYG-CNCF-HYGIENE-20260928-LEGACY-HEADER-001 — Preserved post-commit spec history compression

- Status: OPEN / nonblocking.
- Owner repository: /Users/asami/src/dev2026/goldenport-cncf.
- Discovered: 2026-09-28 independent final focused review of the 50-ID Hygiene batch HP-001..HP-010.
- Affected path: src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkPostCommitConsequenceSpec.scala:18.
- Evidence: The unchanged header contains @since Aug. 12, 2026, history Aug. 12, 2026, history Sep. 18, 2026, and @version Sep. 19, 2026. The first history duplicates @since and the September history shares the latest version month. Both violate RULE.md's canonical history compression rules. The file is byte-unchanged from entry HEAD 8864264454bfba2dd8d848d5e2f6c7738f14078f. The admitted SMR stale-date maintenance already had later accepted September history evidence; this distinct compression debt was not introduced by the current batch.
- Classification and risk: Hygiene; source-history conformance only, with no implementation/specification behavior defect.
- Intended boundary: A separately admitted comment-only header normalization for this specification, preserving all valid historical evidence and established author attribution. This finding is outside the completed 50-ID admission set.
- Dependency and resume condition: Preserve the accepted batch; resume through a bounded header-maintenance task with current-date normalization and diff checks.
- Prohibited workaround: Do not edit executable code, replace accepted history with fabricated dates, or reopen SMR behavioral work.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-CNCF-HYGIENE-20260928-LEGACY-HEADER-001
Handoff Journal: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff-02.md
Handed Off On: 2026-09-28
Hygiene Status: RESOLVED
Resolution Batch: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff-02.md
Validated On: 2026-09-28
Validation Evidence: cncf-hygiene-full-c62adaec734e4088; receipt 70cf80eae9100b0751c10d2927835f8dfe9dd486723556260fb8a04ee656ad76; final focused review 6440759b5a5f52aa7dd7a6a8e2456148f6921a80f933550d865c9065a3c6f78c
Acceptance Commit: reported externally after the grouped acceptance commit


## HYG-CNCF-HYGIENE-20260928-LEGACY-NAMING-001 — Preserved target-program naming

- Status: OPEN / nonblocking.
- Owner repository: /Users/asami/src/dev2026/goldenport-cncf.
- Discovered: 2026-09-28 independent final focused review of the 50-ID Hygiene batch HP-001..HP-010.
- Affected paths: src/main/scala/org/goldenport/cncf/http/WebDescriptorParsingPart.scala:265 and :298; original owner src/main/scala/org/goldenport/cncf/http/WebDescriptor.scala at entry HEAD 8864264454bfba2dd8d848d5e2f6c7738f14078f, lines 1316 and 1349.
- Additional affected paths: src/test/scala/org/goldenport/cncf/statemachine/StateMachineRuleBuilderSpec.scala:110 (private _Entity) and src/test/scala/org/goldenport/cncf/statemachine/PlannedTransitionValidationHookSpec.scala:720 (private _ProviderWithPlanningFailure). Both private type declarations and their references remain unchanged from the entry baseline; the admitted recorded type renames address other explicit identities. RULE.md requires UpperCamelCase without a leading underscore for these declarations.
- Evidence: Method-local values modeRaw and compositionRaw retain lowerCamelCase. The independent original-to-current method-body and literal comparison confirms both declarations were relocated unchanged. RULE.md requires ordinary local values to use flatcase; public model fields and named-argument labels with the same spellings remain compatibility contracts and must stay distinct from these local values.
- Classification and risk: Hygiene; pre-existing local naming inconsistency only. No behavior, schema, public/protected API, initialization, or current validation defect.
- Intended boundary: A separately admitted naming-only maintenance task for those private parsing method locals and their same-method uses, plus the two unchanged private specification type declarations and their references. This finding is outside the completed 50-ID admission set.
- Dependency and resume condition: The accepted Hygiene batch must remain unchanged; resume only in an explicitly bounded follow-up with focused WebDescriptor validation.
- Prohibited workaround: Do not rename the public modeRaw/compositionRaw model fields or named-argument labels, change parsing, or absorb this item into the current batch.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-CNCF-HYGIENE-20260928-LEGACY-NAMING-001
Handoff Journal: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff-02.md
Handed Off On: 2026-09-28
Hygiene Status: RESOLVED
Resolution Batch: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff-02.md
Validated On: 2026-09-28
Validation Evidence: cncf-hygiene-full-c62adaec734e4088; receipt 70cf80eae9100b0751c10d2927835f8dfe9dd486723556260fb8a04ee656ad76; final focused review 6440759b5a5f52aa7dd7a6a8e2456148f6921a80f933550d865c9065a3c6f78c
Acceptance Commit: reported externally after the grouped acceptance commit


## HYG-CNCF-HYGIENE-20260928-LEGACY-SPEC-001 — Preserved executable-spec Given/When/Then debt

- Status: OPEN / nonblocking.
- Owner repository: /Users/asami/src/dev2026/goldenport-cncf.
- Discovered: 2026-09-28 independent final focused review of the 50-ID Hygiene batch HP-001..HP-010.
- Affected paths: src/test/scala/org/goldenport/cncf/http/WebDescriptorSpec.scala and src/test/scala/org/goldenport/cncf/statemachine/StateMachineRuleBuilderSpec.scala:97.
- Evidence: The complete-file scenario scan finds 42 existing WebDescriptorSpec in blocks without Given, When, or Then clauses (first at line 29) and the existing StateMachineRuleBuilderSpec scenario "create expression guard helper instance" without those clauses. WebDescriptorSpec is byte-unchanged from entry HEAD 8864264454bfba2dd8d848d5e2f6c7738f14078f; the guard-helper scenario body is unchanged while a separate fixture term was mechanically renamed. No newly authored or semantically changed scenario lacks GWT. The 334 relocated renderer scenarios all retain their existing GWT and assertion bodies.
- Classification and risk: Hygiene; demonstrably pre-existing executable-document style debt, with accepted assertions retained. This does not weaken the current focused review or change CLEAN.
- Intended boundary: A separately admitted executable-spec maintenance task that adds semantic clauses at actual setup/action/expectation boundaries while preserving scenario behavior and every assertion. This finding is outside the completed 50-ID admission set.
- Dependency and resume condition: Preserve the accepted batch; resume after freezing the exact legacy scenario set and its focused WebDescriptor/StateMachineRuleBuilder validation.
- Prohibited workaround: Do not weaken assertions, rewrite guard or descriptor behavior, or treat the unchanged scenarios as newly introduced authoring failures in this batch.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-CNCF-HYGIENE-20260928-LEGACY-SPEC-001
Handoff Journal: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff-02.md
Handed Off On: 2026-09-28
Hygiene Status: RESOLVED
Resolution Batch: goldenport-cncf:docs/journal/2026/09/2026-09-28-hygiene-resolution-batch-handoff-02.md
Validated On: 2026-09-28
Validation Evidence: cncf-hygiene-full-c62adaec734e4088; receipt 70cf80eae9100b0751c10d2927835f8dfe9dd486723556260fb8a04ee656ad76; final focused review 6440759b5a5f52aa7dd7a6a8e2456148f6921a80f933550d865c9065a3c6f78c
Acceptance Commit: reported externally after the grouped acceptance commit
