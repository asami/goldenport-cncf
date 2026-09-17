# Phase 74.3 Checklist - EntityId Producer/Consumer Closure

status=planned
phase=[Phase 74.3](phase-74.3.md)

## EIR-04: Acceptance and Closure

Stage Status:
- Current status: EIR-04.2 FOCUSED VALIDATION COMPLETE; AGGREGATE CLOSURE PENDING
- Owner: CNCF phase coordinator
- Entry evidence: Phase 74.2 release handoff identifies exact producer and
  consumer sources/artifacts and focused receipts.
- Update rule: Close only when every required validation/review/release item is checked.

EIR-04.1 is recorded by `bd04514bbc07e7280f653b797efde2d0ff63c473`. The
first aggregate suite exposed duplicate test fixture identities. EIR-04.2
completed focused validation as `P743-EIR042-ASSERT-VAL-001` (four suites,
345 tests); the aggregate suite, Phase review, and release evidence remain
pending.

- [ ] Run focused producer, CNCF JobDefinition/store, and affected generator
      specifications on the settled source/artifact identities.
- [x] EIR-04.2: make the test-only fixture bridge EPOCH-fixed with required
      entropy, migrate every call site explicitly, and verify distinct fixture
      identities without changing production issuance.
- [ ] Verify canonical Record/JSON, references/associations, and affected
      persistence consumers; run the one admitted aggregate final suite only here.
- [ ] Promote accepted notes into owner design/spec documents and reconcile
      strategy/phase links without altering unrelated phase status.
- [ ] Complete review and bounded repair/closure for the accepted contract.
- [ ] Record local artifact freshness, release commits, aggregate suite result,
      and closure evidence.

## Aggregate Final-Validation Gate

- [ ] Confirm `PHASE-74`, `PHASE-74.1`, and `PHASE-74.2` accepted handoffs and
      exact source/artifact identities before starting the full suite.
- [ ] Verify the explicit `aggregate_exceptional_predecessors` declarations
      against the durable force-release projections for Phase 74.1 and 74.2;
      retain their forced disposition and exception IDs in the final audit.
- [ ] Record the final suite, review disposition, and release evidence as the
      single aggregate closure for `PHASE-74` through `PHASE-74.3`.
