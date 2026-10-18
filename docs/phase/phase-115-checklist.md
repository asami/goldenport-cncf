# Phase 115 Checklist - Canonical Record Policy Adoption

Status: IN_PROGRESS
Owner: CNCF DataStore / EntityStore integration
Plan: [Phase 115](phase-115.md)

Current Step: RCP-MAIN / RCP-MAIN-A; focused validation passed all 51 examples
across five suites. RCP-MAIN passed independent protected focused Step acceptance;
consumer migration and final Phase acceptance remain pending. All five items are OPEN. Existing Phase 100 evidence is not acceptance of this change.

- [ ] **RCP-01 - Contracts aligned.** Adopted Core Phase 3 dependency and Phase
  100 mapping baseline are identified. Record normalization, typed cardinality,
  SQL NULL, patch instructions and XJson responsibilities are explicit in the
  affected working specifications and representative executable scenarios.
  Native-null handling belongs only to the I/O/foreign-API boundaries; normal
  logic consumes declared non-null values, typed absence and typed instructions.
  Cardinality/semantic type selection and the boundary between standard model
  types and internal input abstractions follow the collection representation rule.
- [ ] **RCP-02 - Main route passes.** Entity save/read/patch/explicit-clear
  exercises real mapping integration. NonEmptyVector survives as an array and
  reconstructs when declared; null attributes are omitted; empty objects/arrays
  remain present. Omitted patches do not clear values, and required values
  retain their established validation. Invalid sequences fail explicitly.
  Zero/multiple-result searches retain Vector bodies and existing page metadata;
  declared ordering and duplicate preservation are demonstrated.
- [ ] **RCP-03 - Consumers and rules migrated.** Actual affected consumer paths
  and constructor/physical-row uses follow the new Core contract. Collection
  rules and examples agree. No duplicate normalizer or generated-source
  workaround is introduced; unrelated external work retains its owner. Provider
  null cannot leak through internal mapping layers; explicit SQL clearing is
  materialized only at the output adapter. No repeated internal null checks or
  null sentinels remain in the affected normal paths.
  Affected generated attributes and callers adopt Vector/NonEmptyVector with
  producer ownership explicit; no List restoration is added solely for an old
  declaration. Generated signatures and reconstruction agree in actual evidence.
- [ ] **RCP-04 - Retained-value behavior demonstrated.** Test-owned stored values
  exercise read normalization and subsequent saves; nested empty objects remain
  present and raw JSON nulls remain unchanged. Live data is not rewritten by
  the test or by implicit migration.
- [ ] **RCP-05 - Acceptance and closure recorded.** Relevant integration and
  regression specifications, independent review and required final validation
  pass. Release/handoff records describe actual dependency, behavioral change
  and remaining separately owned work. No other Phase is marked complete.
