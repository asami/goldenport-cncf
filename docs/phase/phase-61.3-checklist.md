# Phase 61.3 Checklist - Information Curation and Knowledge Lifecycle Migration

status=closed
phase=[Phase 61.3 - Information Curation and Knowledge Lifecycle Migration](phase-61.3.md)
predecessor=[Phase 61.2 Checklist](phase-61.2-checklist.md)
successor=[Phase 61.4 Checklist](phase-61.4-checklist.md)

## IC-05: Curation and Knowledge Lifecycle Migration

Stage Status:
- Current status: DONE
- Owner: CNCF Information, Knowledge, Tag, and provider maintainers
- Update rule: Update only from accepted IC-05 evidence; preserve IC-04
  persistence/OCC behavior.
- Entry rule: IC-04 is DONE in Phase 61.2.
- Completion rule: Phase 26/27 curation and Knowledge behavior is preserved on
  the generated revision-aware Entity.

- [x] Migrate register/import while preserving raw and working data separation.
- [x] Migrate update and field-event append behavior.
- [x] Migrate validation and actionable issue projection.
- [x] Migrate candidate creation, selection, clearing, and binding state.
- [x] Migrate confirm, reject, and reopen through the CML lifecycle contract.
- [x] Migrate publication success and failure behavior.
- [x] Migrate conflict recording and resolution.
- [x] Migrate snapshot, count, lookup, list, and search behavior.
- [x] Preserve Information-specific capability checks and separation of duty.
- [x] Preserve Tag bindings and dedicated Information TagSpace behavior.
- [x] Preserve Information-to-Knowledge materialization and 1.5-hop
  neighborhood behavior.
- [x] Preserve distinct Information, Entity, RDF, external, Tag, Knowledge
  node, and Knowledge frame identities.
- [x] Preserve provider failures without false publication or revision state.
- [x] Add lifecycle property tests and invalid-transition matrices.
- [x] Re-run Phase 26/27 Information and Knowledge regression specifications.

Evidence:
- The accepted IC-05 Step commit is `d0ec24ac396c5128fcf781595629ba2dd48e6c9e`.
- The mandatory Phase review found `CPB-61.3-001`; repair cycle 1 centralized
  generated-CML lifecycle admission before persistence/cache mutation. Focused
  validation passed 78 tests in 10 suites (`72401-20260831T165410Z`), and its
  independent closure review closed that blocker.
- The cycle-1 re-review found `CB-61.3-RR-001`; repair cycle 2 made editor
  action availability derive from the generated lifecycle. Focused validation
  passed 78 tests in 10 suites (`89107-20260831T173703Z`), and the independent
  closure re-review passed with no Current Phase Blocker.
- The final full suite, `sbt --batch test`, passed 3,510 tests in 474 suites
  with no failure (serialized receipt `96580-20260831T175306Z`). The distinct
  release commit is bound under `phase61.3-clb-ic05-20260901`; Phase 61.4
  remains planned and unstarted.
