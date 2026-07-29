# Phase 52 Checklist - Exact Entity ID Serialization and Collection Identity

status=planned
phase=[Phase 52 - Exact Entity ID Serialization and Collection Identity](phase-52.md)

This checklist is the authoritative Phase 52 state ledger after Phase 52
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 52 is a clean
break and has no old-format compatibility or migration stage.

## EID-01: Inventory and Failing-First Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF and core identity/persistence maintainers
- Entry rule: Phase 51 is closed.
- Completion rule: Lossy serialization, synthetic collection construction,
  context repair, and every affected core path have failing-first evidence.

- [ ] Inventory `EntityId`, `EntityCollectionId`, and UniversalId construction,
  parsing, rendering, equality, and hashing.
- [ ] Inventory every core use of `EntityId.value`, `print`, `show`,
  `toString`, and `parts`.
- [ ] Inventory datastore collection and entry-key construction.
- [ ] Inventory primary-ID and EntityId-valued attribute storage.
- [ ] Inventory generated, built-in, custom typed, and raw `Record` codecs.
- [ ] Inventory UnitOfWork, direct store, loader, Admin, Association, Blob,
  child-binding, cache, lock, revision, authorization, and diagnostics.
- [ ] Prove the current scalar cannot round-trip an exact collection.
- [ ] Prove same local identity in different exact collections currently
  collides at the String boundary.
- [ ] Register failing-first executable specifications for all Phase 52
  acceptance groups.

Evidence:
- Pending.

## EID-02: Canonical Exact Serialization

Stage Status:
- Current status: PLANNED
- Owner: `simplemodeling-model` and CNCF identity maintainers
- Entry rule: EID-01 is DONE.
- Completion rule: One versioned lossless encoding satisfies exact round-trip,
  non-collision, hostile-input, and transport laws.

- [ ] Fix the canonical `EntityCollectionId` String encoding.
- [ ] Fix the canonical `EntityId` String encoding.
- [ ] Make `EntityId.value` the complete canonical identity.
- [ ] Make `EntityId.parse` a pure inverse without external context.
- [ ] Remove synthetic collection construction from parsed Entity-local
  `major/minor`.
- [ ] Define version recognition and unsupported-version failure.
- [ ] Define unambiguous label encoding without boundary guessing.
- [ ] Prove stable Record, JSON, HTTP, form, CLI, and datastore round-trips.
- [ ] Reject old incomplete, malformed, truncated, overlong, and hostile input
  deterministically.
- [ ] Preserve opaque-ID application rules.

Evidence:
- Pending.

## EID-03: Model and Generated-Code Adoption

Stage Status:
- Current status: PLANNED
- Owner: `simplemodeling-model`, SimpleModeler, Cozy, and sbt-cozy maintainers
- Entry rule: EID-02 is DONE.
- Completion rule: Types and generated persistence store and restore only
  complete exact Entity IDs.

- [ ] Update Entity ID construction to carry the exact collection.
- [ ] Remove `entityIdInCollectionNamespace` compatibility behavior.
- [ ] Generate complete canonical primary-ID storage.
- [ ] Generate complete canonical EntityId-valued attribute storage.
- [ ] Decode primary and referenced IDs with the same pure parser.
- [ ] Keep business/API `fromRecord` separate from datastore decoding where
  their Record purposes differ.
- [ ] Update generated schema and examples.
- [ ] Update Cozy generated-source assertions.
- [ ] Update affected sbt-cozy bridge fixtures.
- [ ] Add primary, optional reference, repeated reference, and
  cross-collection round-trip evidence.

Evidence:
- Pending.

## EID-04: CNCF Persistence and Routing Simplification

Stage Status:
- Current status: PLANNED
- Owner: CNCF EntityStore, DataStore, EntitySpace, and UnitOfWork maintainers
- Entry rule: EID-03 is DONE.
- Completion rule: Persistence and routing use exact parsed identity without
  collection reconstruction, context rebinding, or name-only resolution.

- [ ] Use `id.collection` as the exact datastore collection.
- [ ] Use complete `id.value` as the datastore entry key.
- [ ] Require datastore collection and encoded ID collection to match.
- [ ] Remove normal identity restoration from `EntityStoreDecodeContext`.
- [ ] Remove synthetic and name-only Entity ID canonicalization from
  identity-sensitive paths.
- [ ] Make all UnitOfWork Entity operations exact-ID operations.
- [ ] Repair direct `EntityStoreSpace` and `EntityLoader` paths.
- [ ] Update revision, conditional transition, search, and exclusion keys.
- [ ] Reject old incomplete scalar IDs before datastore access.
- [ ] Add same-local-ID/different-collection provider tests.

Evidence:
- Pending.

## EID-05: Identity Consumers and Built-Ins

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component, Association, security, and observability maintainers
- Entry rule: EID-04 is DONE.
- Completion rule: Every core identity-sensitive consumer uses exact parsed
  identity and no compensation logic remains.

- [ ] Repair Admin Entity delete and resident eviction.
- [ ] Make Association validation return and persist the exact target ID.
- [ ] Remove Association collection scanning and name-only target fallback.
- [ ] Repair Blob, child binding, tag, workflow, and other scalar ingress.
- [ ] Update resident caches and snapshots.
- [ ] Update dirty-entity maps and aggregate locks.
- [ ] Update authorization resources, audit, and CallTree identity.
- [ ] Update built-in and raw `Record` persistence adapters.
- [ ] Prove no synthetic or rebound ID reaches a datastore, cache, lock,
  authorization, or Association side effect.

Evidence:
- Pending.

## EID-06: Core Validation and Canonical Closure

Stage Status:
- Current status: PLANNED
- Owner: core Phase 52 repository maintainers
- Entry rule: EID-05 is DONE.
- Completion rule: Core validation passes and canonical documentation defines
  complete String round-trip as the only Entity ID contract.

- [ ] Run full `simplemodeling-model` validation.
- [ ] Run full `simple-modeler` validation.
- [ ] Run full CNCF validation.
- [ ] Run full Cozy validation.
- [ ] Run affected sbt-cozy validation.
- [ ] Verify no old-format compatibility, migration, dual parser, read repair,
  or context rebinding remains.
- [ ] Perform a clean full review and focused re-review until findings close.
- [ ] Promote verified behavior to `docs/design` and `docs/spec`.
- [ ] Mark the Phase 51 compensation model as historical/superseded without
  rewriting its evidence.
- [ ] Record known CAR incompatibilities as CAR-local follow-up work without
  making them Phase 52 closure gates.
- [ ] Update strategy and Phase 52 status with exact validation evidence.
- [ ] Close Phase 52 only after all core completion rules pass.

Evidence:
- Pending.

## CAR Follow-Up Rule

- CAR repositories are outside the Phase 52 required repository set.
- Do not add compatibility logic to CNCF to keep an affected CAR working.
- When a CAR failure is found, correct its generated or hand-written code in
  that CAR.
- A CAR correction adopts the canonical exact String contract and does not
  restore the old incomplete format.
- Track each correction in that CAR's own phase, journal, issue, or handoff as
  appropriate.

## Status

Phase 52 is PLANNED. EID-01 has not started.
