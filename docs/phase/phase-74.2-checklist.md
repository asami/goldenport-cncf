# Phase 74.2 Checklist - EntityId CNCF and Generated Consumer Adoption

status=closed
phase=[Phase 74.2](phase-74.2.md)

## EIR-03: CNCF and Generated Consumer Adoption

Stage Status:
- Current status: CLOSED
- Owner: CNCF and simple-modeler maintainers
- Entry evidence: Phase 74.1 migration handoff and its producer artifact are
  accepted and identified.
- Closure evidence: CNCF Step
  `e72b58cdddee85d26e44e6181500d3633129ee3b`; focused compile,
  representative, and accumulator receipts; repair receipt
  `P742-PHASE-FIX-001-VAL-JOBCONTROL-001`; Phase full review; and accepted
  focused re-review. `CPB-74.2-001` was resolved by its bounded local naming
  repair.
- Aggregate validation: deferred, unchanged, to PHASE-74.3. This checklist
  does not claim a repository full-suite result.

- [x] Connect IdGenerationContext to subtype construction using the normal
      context namespace/time/entropy, without a caller-supplied key-derived token.
- [x] Introduce JobDefinitionId extending EntityId and use it on
      JobDefinitionEntity, preserving generic EntityStore interoperability.
- [x] Persist `{ id, key, ... }` on JobDefinition creation; use key search to
      obtain that saved ID on later reads, with no key-derived ID, correspondence
      table, hash, or companion integrity value. Treat a key index as optional
      later store optimization.
- [x] Decode persisted JobDefinitionId with the expected exact collection;
      separate key search from saved-ID load and preserve identity on update.
- [x] Verify distinct `a-b`/`a_b` definitions remain distinct, existing duplicate-key
      rejection remains effective, and restart lookup returns the stored ID.
- [x] Classify existing special Job-related ID bridges against their contracts;
      do not mechanically remove all explicit timestamp/entropy calls.
- [x] Migrate each audited CNCF direct-construction site to the explicit
      ordinary-issue, restore, or special-bridge API. Where a complete canonical
      ID is already available, validate its exact collection rather than silently
      replacing the collection during reconstruction.
- [x] Adapt affected generated entity IDs and verify representative generated
      Scala compilation and runtime use through the same model contract.

## Handoff Gate to Phase 74.3

- [x] Record changed source/artifact identities, focused consumer receipts, and
      unresolved compatibility limits as a release handoff.
- [x] Confirm that final aggregate validation is deferred solely to Phase 74.3.
