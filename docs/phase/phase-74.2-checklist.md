# Phase 74.2 Checklist - EntityId CNCF and Generated Consumer Adoption

status=planned
phase=[Phase 74.2](phase-74.2.md)

## EIR-03: CNCF and Generated Consumer Adoption

Stage Status:
- Current status: OPEN
- Owner: CNCF and simple-modeler maintainers
- Entry evidence: Phase 74.1 migration handoff and its producer artifact are
  accepted and identified.
- Update rule: Close only from the typed-consumer evidence below.

- [ ] Connect IdGenerationContext to subtype construction using the normal
      context namespace/time/entropy, without a caller-supplied key-derived token.
- [ ] Introduce JobDefinitionId extending EntityId and use it on
      JobDefinitionEntity, preserving generic EntityStore interoperability.
- [ ] Persist `{ id, key, ... }` on JobDefinition creation; use key search to
      obtain that saved ID on later reads, with no key-derived ID, correspondence
      table, hash, or companion integrity value. Treat a key index as optional
      later store optimization.
- [ ] Decode persisted JobDefinitionId with the expected exact collection;
      separate key search from saved-ID load and preserve identity on update.
- [ ] Verify distinct `a-b`/`a_b` definitions remain distinct, existing duplicate-key
      rejection remains effective, and restart lookup returns the stored ID.
- [ ] Classify existing special Job-related ID bridges against their contracts;
      do not mechanically remove all explicit timestamp/entropy calls.
- [ ] Migrate each audited CNCF direct-construction site to the explicit
      ordinary-issue, restore, or special-bridge API. Where a complete canonical
      ID is already available, validate its exact collection rather than silently
      replacing the collection during reconstruction.
- [ ] Adapt affected generated entity IDs and verify representative generated
      Scala compilation and runtime use through the same model contract.

## Handoff Gate to Phase 74.3

- [ ] Record changed source/artifact identities, focused consumer receipts, and
      unresolved compatibility limits as a release handoff.
- [ ] Confirm that final aggregate validation is deferred solely to Phase 74.3.
