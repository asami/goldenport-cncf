# Phase 52 Checklist - Exact Entity ID Serialization and Collection Identity

status=in_progress
phase=[Phase 52 - Exact Entity ID Serialization and Collection Identity](phase-52.md)

This checklist is the authoritative Phase 52 state ledger after Phase 52
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 52 is a clean
break and has no old-format compatibility or migration stage.

## Phase Governance Decisions

### P52-COMP-01: Public Constant Canonicalization (2026-07-30)

- [x] The phase owner selected a breaking migration from PascalCase public
  constants to canonical lower-camel names, with no PascalCase compatibility
  aliases.
- [x] The admitted Phase 52 repository set now includes
  `textus-sample-apps`, `textus-knowledge-editor`, and
  `textus-semantic-integration-engine` for downstream caller migration.
- [x] The decision resets the current slice to PLAN before implementation or
  review continues.

## EID-01: Inventory and Failing-First Contract

Stage Status:
- Current status: DONE
- Owner: CNCF and core identity/persistence maintainers
- Entry rule: Phase 51 is closed.
- Completion rule: Lossy serialization, synthetic collection construction,
  context repair, and every affected core path have failing-first evidence.

- [x] Inventory `EntityId`, `EntityCollectionId`, and UniversalId construction,
  parsing, rendering, equality, and hashing.
- [x] Inventory every core use of `EntityId.value`, `print`, `show`,
  `toString`, and `parts`.
- [x] Inventory datastore collection and entry-key construction.
- [x] Inventory primary-ID and EntityId-valued attribute storage.
- [x] Inventory generated, built-in, custom typed, and raw `Record` codecs.
- [x] Inventory UnitOfWork, direct store, loader, Admin, Association, Blob,
  child-binding, cache, lock, revision, authorization, and diagnostics.
- [x] Prove the current scalar cannot round-trip an exact collection.
- [x] Prove same local identity in different exact collections currently
  collides at the String boundary.
- [x] Register failing-first executable specifications for all Phase 52
  acceptance groups.

Evidence:
- [EID-01 identity consumer inventory and failing-first contract](../notes/phase-52-eid01-inventory-and-failing-first-contract.md) (done).

## EID-02: Canonical Exact Serialization

Stage Status:
- Current status: DONE
- Owner: `simplemodeling-model` and CNCF identity maintainers
- Entry rule: EID-01 is DONE.
- Completion rule: One versioned lossless encoding satisfies exact round-trip,
  non-collision, hostile-input, and transport laws.

- [x] Fix the canonical `EntityCollectionId` String encoding.
- [x] Fix the canonical `EntityId` String encoding.
- [x] Make `EntityId.value` the complete canonical identity.
- [x] Make `EntityId.parse` a pure inverse without external context.
- [x] Remove synthetic collection construction from parsed Entity-local
  `major/minor`.
- [x] Define version recognition and unsupported-version failure.
- [x] Define unambiguous label encoding without boundary guessing.
- [x] Prove stable Record, JSON, HTTP, form, CLI, and datastore round-trips.
- [x] Reject old incomplete, malformed, truncated, overlong, and hostile input
  deterministically.
- [x] Preserve opaque-ID application rules.

Evidence:
- [EID-02 canonical exact serialization](../notes/phase-52-eid02-canonical-exact-serialization.md) (done; model 13/13 and CNCF parser 3/3; independent clean re-review accepted).

## EID-03: Model and Generated-Code Adoption

Stage Status:
- Current status: DONE
- Owner: `simplemodeling-model`, SimpleModeler, Cozy, and sbt-cozy maintainers
- Entry rule: EID-02 is DONE.
- Completion rule: Types and generated persistence store and restore only
  complete exact Entity IDs.

- [x] Update Entity ID construction to carry the exact collection.
- [x] Remove `entityIdInCollectionNamespace` compatibility behavior.
- [x] Generate complete canonical primary-ID storage.
- [x] Generate complete canonical EntityId-valued attribute storage.
- [x] Decode primary and referenced IDs with the same pure parser.
- [x] Keep business/API `fromRecord` separate from datastore decoding where
  their Record purposes differ.
- [x] Update generated schema and examples.
- [x] Update Cozy generated-source assertions.
- [x] Update affected sbt-cozy bridge fixtures.
- [x] Add primary, optional reference, repeated reference, and
  cross-collection round-trip evidence.

Evidence:
- [EID-03 model and generated-code adoption](../notes/phase-52-eid03-model-and-generated-code-adoption.md) (done; focused validation and independent clean re-review accepted).

## EID-04: CNCF Persistence and Routing Simplification

Stage Status:
- Current status: DONE
- Owner: CNCF EntityStore, DataStore, EntitySpace, and UnitOfWork maintainers
- Entry rule: EID-03 is DONE.
- Completion rule: Persistence and routing use exact parsed identity without
  collection reconstruction, context rebinding, or name-only resolution.

- [x] Use `id.collection` as the exact datastore collection.
- [x] Use complete `id.value` as the datastore entry key.
- [x] Require datastore collection and encoded ID collection to match.
- [x] Remove normal identity restoration from `EntityStoreDecodeContext`.
- [x] Remove synthetic and name-only Entity ID canonicalization from
  identity-sensitive paths.
- [x] Make all UnitOfWork Entity operations exact-ID operations.
- [x] Repair direct `EntityStoreSpace` and `EntityLoader` paths.
- [x] Update revision, conditional transition, search, and exclusion keys.
- [x] Reject old incomplete scalar IDs before datastore access.
- [x] Add same-local-ID/different-collection provider tests.

Evidence:
- [EID-04 persistence and routing simplification](../notes/phase-52-eid04-persistence-and-routing-simplification.md) (implementation validation, independent review/review-fix/re-review, and the 2026-07-30 full framework suite 2,639/2,639 passed).

## EID-05: Identity Consumers and Built-Ins

Stage Status:
- Current status: DONE (Admin detail/edit canonical-route revalidation passed)
- Owner: CNCF Component, Association, security, and observability maintainers
- Entry rule: EID-04 is DONE.
- Completion rule: Every core identity-sensitive consumer uses exact parsed
  identity and no compensation logic remains.

- [x] Repair Admin Entity delete and resident eviction.
- [x] Make Association validation return and persist the exact target ID.
- [x] Remove Association collection scanning and name-only target fallback.
- [x] Repair Blob, child binding, tag, workflow, and other scalar ingress.
- [x] Update resident caches and snapshots.
- [x] Update dirty-entity maps and aggregate locks.
- [x] Update authorization resources, audit, and CallTree identity.
- [x] Update built-in and raw `Record` persistence adapters.
- [x] Revalidate Admin detail/edit rendering: scalar and foreign canonical route
  locators must emit neither a page nor an actionable form/link.
- [x] Prove no synthetic or rebound ID reaches a datastore, cache, lock,
  authorization, or Association side effect.

Evidence:
- [EID-05 identity consumers and built-ins](../notes/phase-52-eid05-identity-consumers-and-built-ins.md) (implementation, review/review-fix/re-review, `Test / compile`, and 14-suite 158/158 focused validation accepted).

## EID-06: Core Validation and Canonical Closure

Stage Status:
- Current status: DONE
- Owner: core Phase 52 repository maintainers
- Entry rule: EID-05 is DONE.
- Completion rule: Core validation passes and canonical documentation defines
  complete String round-trip as the only Entity ID contract.

- [x] Run full `simplemodeling-model` validation.
- [x] Run full `simple-modeler` validation.
- [x] Run full CNCF validation.
- [x] Run full Cozy validation.
- [x] Run affected sbt-cozy validation.
- [x] Verify no old-format compatibility, migration, dual parser, read repair,
  or context rebinding remains.
- [x] Perform the EID-06 closure review and focused re-review until findings close.
- [x] Promote verified behavior to `docs/design` and `docs/spec`.
- [x] Mark the Phase 51 compensation model as historical/superseded without
  rewriting its evidence.
- [x] Record known CAR incompatibilities as CAR-local follow-up work without
  making them Phase 52 closure gates.
- [x] Update strategy and Phase 52 status with exact validation evidence.
- [x] Close Phase 52 only after all core completion rules pass.

Evidence:

- 2026-07-30 JST full validation: `simplemodeling-model` 65/65;
  `simple-modeler` 44/44; CNCF 2,722 report tests with 0 failures/errors;
  Cozy 742/742; sbt-cozy 124/124 (5 cancelled); Cwitter 1/1; Knowledge Editor
  127/127; Semantic Integration Engine 142/142 (one cancelled).
- CNCF `0.5.2-SNAPSHOT`, simplemodeler `1.1.25-SNAPSHOT`, and sbt-cozy
  `0.1.17-SNAPSHOT` were published locally before the downstream validation.
- The generated exact-ID scripted acceptance passed with
  `EID03_GENERATED_ENTITY_EXACT_ROUNDTRIP_OK`; aggregate single-record passed
  with `AGGREGATE_SINGLE_RECORD_PROOF_OK` against the same explicit
  development coordinates.
- `aggregate-external-update-proof` currently reaches generated compilation
  and exposes a separate P51 aggregate-operation generator type mismatch
  (`ExecUowM[EntitySnapshot[ShipmentOrder]]` where `Unit` is required).  It is
  not an Entity ID compatibility failure; no fallback or rebinding was added.
- 2026-07-30 JST closure validation: `simplemodeling-model` 66/66 plus
  `publishLocal`; `notice-board-event-driven` 8/8; CNCF 2,660/2,660 across
  376 suites with 0 failures (14 environment-gated cancelled, 1 ignored, and
  59 pending).  The final independent focused re-review found no actionable
  findings.  No CAR incompatibility was discovered in the required repository
  set; any future CAR repair remains CAR-local under the rule below.

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

Phase 52 is CLOSED. EID-01 through EID-06 are DONE.
