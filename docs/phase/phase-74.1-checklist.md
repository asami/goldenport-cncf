# Phase 74.1 Checklist - EntityId Model Contract Materialization

status=closed
phase=[Phase 74.1](phase-74.1.md)

## EIR-02: Model Contract Restoration

Stage Status:
- Current status: CLOSED
- Owner: simplemodeling-model maintainers
- Entry evidence: Phase 74 authority handoff is accepted and complete.
- Closure evidence: producer Step
  `4d440f99cfc3ccb349a5af1cf4cc127242aa7fef`; focused receipts P005/P006;
  `publishLocal` receipt P007 for
  `org.simplemodeling:simplemodeling-model:0.2.2-SNAPSHOT`; clean Phase full
  review and closure-projection focused review.
- Aggregate validation: deferred, unchanged, to PHASE-74.3. No full-suite
  result is claimed by this checklist.

- [x] Restore an abstract EntityId base and explicit concrete materialization
      without exposing case-class assumptions as the base contract.
- [x] Verify that a concrete entity-specific ID is not interchangeable with an
      unrelated EntityId subtype at typed consumer boundaries.
- [x] Keep ordinary entropy automatically generated and materialized once;
      preserve explicit special-purpose entropy and deterministic test generation.
- [x] Verify a concrete XxxId subclass, generic parsing, and typed factory/decoder
      round trips with unchanged complete collection and canonical value.
- [x] Verify equality/hash, Record/JSON, and identity immutability on affected APIs.
- [x] Refresh the changed producer through publishLocal before downstream validation.

## Handoff Gate to Phase 74.2

- [x] Record the producer source/artifact identity, typed API, compatibility
      boundaries, and focused acceptance receipts as a migration handoff.
- [x] Confirm no unresolved policy is carried forward; any policy change returns
      to Phase 74 rather than being decided during consumer adoption.

The accepted handoff is [Phase 74.1 EntityId Model Contract Handoff](../notes/phase-74.1-entityid-model-contract-handoff.md).
