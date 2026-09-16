# Phase 74.1 - EntityId Model Contract Materialization

status=planned
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-74.3
aggregate_validation_sequence=["PHASE-74","PHASE-74.1","PHASE-74.2","PHASE-74.3"]
planned_at=2026-09-16
split_from=[Phase 74](phase-74.md)
depends_on=[Phase 74](phase-74.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#961-entityid-inheritance-contract-restoration)
checklist=[Phase 74.1 Checklist](phase-74.1-checklist.md)
successor=[Phase 74.2](phase-74.2.md)

## Purpose

Materialize the accepted abstract EntityId model in `simplemodeling-model`.
This is EIR-02 of the former unsplit Phase 74: a bounded producer change made
against the authority handoff from Phase 74, not a reopening of identity policy.

## Ownership and Scope

- `simplemodeling-model` owns the abstract EntityId base, concrete typed
  materialization, generic restoration, and executable producer specifications.
- `simplemodeling-lib` retains the unchanged UniversalId foundation.
- Restore an abstract EntityId base and explicit concrete materialization
  without exposing case-class assumptions as the base contract.
- Verify that an entity-specific subtype is not interchangeable with an
  unrelated EntityId subtype at typed consumer boundaries.
- Keep ordinary entropy automatically generated and materialized once; preserve
  explicit special-purpose entropy and deterministic test generation.
- Verify a concrete XxxId subclass, generic parsing, and typed factory/decoder
  round trips with unchanged complete collection and canonical value.
- Verify equality/hash, Record/JSON, and identity immutability on affected APIs.
- Refresh the changed producer through `publishLocal` before handing consumers
  the accepted source/artifact identity.

## Phase Plan Gate: PROCEED

- target and ceiling: expected 6 hours (range 5–7); hard ceiling 8 hours.
- estimate calibration: no comparable completed slice exists; this is the
  original EIR-02 six-hour work group.
- planning demand: bounded-settled implementation; recommended profile:
  `gpt-5.6-terra / high`; profile cost role: lower-cost execution.
- expensive reasoning kernel: owned only by Phase 74; this Phase implements its
  frozen contract and must return an ambiguity to the parent rather than decide
  a new issuance/restoration policy.
- incoming semantic handoff:
  - from: `PHASE-74`; kind: `authority`; owner: Phase 74.
  - input: accepted EntityId hierarchy, explicit issuance/parse/restore/
    `bridgeFromParts` boundaries, classified construction matrix, and identity
    acceptance definitions.
  - action: implement the producer API exactly as released.
  - output: typed model API, source/artifact identity, and producer receipts for
    Phase 74.2; invalidation requires an explicit Phase 74 planning change.
- merge/rebalance evidence: none; this is one original six-hour unit and has no
  separate expensive decision to merge back into the parent.
- runtime suitability: re-evaluate at execution; no implementation starts from
  this planning record.

## Completion and Handoff Conditions

- A concrete entity-specific ID extends abstract EntityId and supports normal
  issue, typed persistence/decoding, and generic EntityStore boundaries.
- Generic EntityId materialization remains available for type-erased common
  boundaries, but generic new durable issuance is not introduced.
- The released handoff to Phase 74.2 identifies the exact source/artifact,
  typed migration API, producer tests, and compatibility limits.

## Non-Goals

- CNCF JobDefinition/store adoption, direct-construction migration, and affected
  generator edits; these belong to Phase 74.2.
- Aggregate full suite, cross-producer closure review, or release closure; these
  belong to Phase 74.3.

## References

- [Phase 74](phase-74.md)
- [Phase 74.1 Checklist](phase-74.1-checklist.md)
- [Provisional Specification](../notes/entityid-inheritance-contract-restoration-provisional-specification.md)
