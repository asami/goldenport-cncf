# Phase 74.3 - EntityId Producer/Consumer Closure

status=planned
split_full_test_policy=final-only
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-74.3
aggregate_validation_sequence=["PHASE-74","PHASE-74.1","PHASE-74.2","PHASE-74.3"]
aggregate_exceptional_predecessors=[{"phase_identity":"PHASE-74.1","commit":"0dacd39a7dd325ac800bfaad1f87a220b230359d","exception_ids":["FORCE-VALIDATION-001"]},{"phase_identity":"PHASE-74.2","commit":"2487025ef14ed1892216b3f087e39f2a282bac6c","exception_ids":["FORCE-AGGREGATE-PREDECESSOR-HANDOFF-001"]}]
planned_at=2026-09-16
split_from=[Phase 74](phase-74.md)
depends_on=[Phase 74.2](phase-74.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#961-entityid-inheritance-contract-restoration)
checklist=[Phase 74.3 Checklist](phase-74.3-checklist.md)

Status: EIR-04.2 FOCUSED VALIDATION COMPLETE; AGGREGATE CLOSURE PENDING

EIR-04.1's owner-document promotion is recorded by CNCF Step
`bd04514bbc07e7280f653b797efde2d0ff63c473`. The first aggregate suite found
that Phase 74.2's test-only `EntityIdFixtureBridge` supplied a default entropy,
so fixtures with the same structural parts could silently receive the same ID.
EIR-04.2 now fixes fixture timestamps at `Instant.EPOCH`, requires an explicit
entropy at every call site, and has a passing focused receipt
`P743-EIR042-ASSERT-VAL-001` (four suites, 345 tests). This status does not
claim the aggregate suite, Phase full review, or final release.

The user-approved acceptance change preserves the separately recorded forced
releases of Phase 74.1 and Phase 74.2.  This final owner accepts only the
exact commits and exception IDs declared above, after verifying their durable
force-release audit projections.  They remain `forced / exceptions-recorded`:
the aggregate suite and this Phase's new full review are the compensating
closure evidence, not a retroactive normal-release classification.

## Purpose

Close EIR-04 for the accepted EntityId producer/consumer change. This final
member owns aggregate final validation, specification promotion, closure review,
and release evidence for the entire Phase 74 split sequence.

## Ownership and Scope

- CNCF coordinates accepted producer, JobDefinition/store, and affected
  generator validation on the exact source/artifact identities supplied by
  Phase 74.2.
- Run focused producer, CNCF JobDefinition/store, and affected generator
  specifications before the one required aggregate full suite.
- Verify canonical Record/JSON, references/associations, and affected persistence
  consumers. Run admitted final suites without duplicating earlier focused work.
- Promote accepted notes into owner design/spec documents and reconcile
  strategy/phase links without changing unrelated phase status.
- Correct only the test fixture bridge: it always materializes at
  `Instant.EPOCH` and requires an explicit entropy at every call site. Migrate
  the fixture call sites and prove that distinct entropy values produce distinct
  deterministic fixture identities.
- Complete review, bounded repair, release commits, and closure evidence.

## Phase Plan Gate: PROCEED

- target and ceiling: expected 6 hours (range 5–7); hard ceiling 8 hours.
- estimate calibration: no comparable completed slice exists; this is the
  original EIR-04 six-hour closure group. The full suite is intentionally run
  here once under `split_full_test_policy=final-only`.
- planning demand: bounded-settled validation/closure; recommended profile:
  `gpt-5.6-terra / high`; profile cost role: lower-cost execution.
- expensive reasoning kernel: owned only by Phase 74. Final validation checks
  the released contract; a semantic discrepancy returns to its producing Phase
  rather than creating a new closing policy.
- incoming semantic handoff:
  - from: `PHASE-74.2`; kind: `release`; owner: Phase 74.2.
  - input: integrated consumer source, exact producer/consumer identities,
    focused receipts, and disclosed compatibility limits.
  - action: execute cross-producer validation and record closure against the
    accepted sequence.
  - output: aggregate validation, review, release, and specification-promotion
    receipts; invalidation requires a changed predecessor source/artifact.
- merge/rebalance evidence: none; the final-only suite and closure duties must
  have one owner, while the preceding three independent units fit six hours.
- runtime suitability: re-evaluate at execution; this planning record starts no
  test or release operation.

## Completion Conditions

- Focused and aggregate validation cover the released producer, JobDefinition/
  store, and affected generator paths with canonical persistence/reference
  evidence.
- The one aggregate full suite is run only here after every predecessor handoff
  is accepted and EIR-04.2 focused validation passes; its exact inputs and
  result are recorded.
- The exceptional predecessor declaration is limited to the recorded Phase
  74.1 and 74.2 force commits and their exact exception IDs; an undeclared or
  mismatched forced release remains a closure blocker.
- Owner notes/specifications, review disposition, bounded repairs, release
  commits, and final closure evidence are reconciled without claiming unrelated
  Phase completion.

## Non-Goals

- Reopening the EntityId model policy, unbounded consumer migration, or an
  unrelated full-suite sweep.

## References

- [Phase 74.2](phase-74.2.md)
- [Phase 74.3 Checklist](phase-74.3-checklist.md)
- [Provisional Specification](../notes/entityid-inheritance-contract-restoration-provisional-specification.md)
