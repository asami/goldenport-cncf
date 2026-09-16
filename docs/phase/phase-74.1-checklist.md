# Phase 74.1 Checklist - EntityId Model Contract Materialization

status=planned
phase=[Phase 74.1](phase-74.1.md)

## EIR-02: Model Contract Restoration

Stage Status:
- Current status: OPEN
- Owner: simplemodeling-model maintainers
- Entry evidence: Phase 74 authority handoff is accepted and complete.
- Update rule: Close only from executable producer acceptance and the typed
  handoff identified below.

- [ ] Restore an abstract EntityId base and explicit concrete materialization
      without exposing case-class assumptions as the base contract.
- [ ] Verify that a concrete entity-specific ID is not interchangeable with an
      unrelated EntityId subtype at typed consumer boundaries.
- [ ] Keep ordinary entropy automatically generated and materialized once;
      preserve explicit special-purpose entropy and deterministic test generation.
- [ ] Verify a concrete XxxId subclass, generic parsing, and typed factory/decoder
      round trips with unchanged complete collection and canonical value.
- [ ] Verify equality/hash, Record/JSON, and identity immutability on affected APIs.
- [ ] Refresh the changed producer through publishLocal before downstream validation.

## Handoff Gate to Phase 74.2

- [ ] Record the producer source/artifact identity, typed API, compatibility
      boundaries, and focused acceptance receipts as a migration handoff.
- [ ] Confirm no unresolved policy is carried forward; any policy change returns
      to Phase 74 rather than being decided during consumer adoption.
