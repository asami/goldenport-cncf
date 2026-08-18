# Phase 66 Checklist - CAR Skill Bundle Contract

status=planned
phase=[Phase 66 - CAR Skill Bundle Contract](phase-66.md)
cozy_handoff=`cozy:docs/phase/phase-24-checklist.md#SK24-01`

This checklist is the authoritative Phase 66 state ledger after Phase 66
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 65 closes. Cozy Phase 24 remains planned and blocked at `SK24-01` until
CSB-06 records the accepted Phase 66 handoff.

## CSB-01: Inventory and Ownership Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component/CAR, resource, compatibility, and contract maintainers
- Entry rule: Phase 65 is closed.
- Completion rule: Existing contracts, conflicts, consumer boundaries, and
  failing-first acceptance identities are frozen before model implementation.

- [ ] Inventory Component/CAR identity, archive layout, resource path,
  integrity, repository, cache, and compatibility contracts from completed
  and planned CNCF phases consumed by Skill bundles.
- [ ] Inventory Phase 59 Skill catalog metadata and preserve its
  discovery-only, non-installing boundary.
- [ ] Inventory the 2026-07-21 Skill bundle direction and strategy item 9.38,
  separating accepted decisions from still-provisional details.
- [ ] Inventory Cozy Phase 24 `SK24-01` through `SK24-04` and map every field,
  validation outcome, fixture, and handoff expected from CNCF.
- [ ] Freeze CNCF ownership of model, schema/versioning, identity, canonical
  paths, digest, compatibility, deterministic validation, and shared fixtures.
- [ ] Freeze Cozy ownership of source validation, CAR projection, lint, and
  package provenance without admitting those implementations into Phase 66.
- [ ] Freeze CNCF Launcher and Textus Launcher ownership of explicit local
  installation without admitting command implementation into Phase 66.
- [ ] Freeze the boundary between Skill discovery metadata, bundle packaging,
  installation state, MCP requirements, and runtime Operation/MCP authority.
- [ ] Register exact failing-first Executable Specifications for schema,
  codec, path, digest, compatibility, dependency, MCP, equivalence, and hostile
  input acceptance groups.

Evidence:
- Pending.

## CSB-02: Manifest Model and Schema

Stage Status:
- Current status: PLANNED
- Owner: CNCF Skill bundle contract maintainers
- Entry rule: CSB-01 is DONE.
- Completion rule: One versioned transport-neutral model and schema define all
  required identity, metadata, requirement, and evolution semantics.

- [ ] Define `schemaVersion` and explicit supported/required version behavior.
- [ ] Define bundle id, version, description, owner/component association, and
  stable logical identity without duplicating canonical Component identity.
- [ ] Define Skill id, description, relative entry path, declared file set,
  media/type role where required, and content digest records.
- [ ] Define deterministic declaration ordering and duplicate identity rules.
- [ ] Define required and optional Codex, CNCF, Cozy, launcher, and bundle
  compatibility declarations without host-specific version inference.
- [ ] Define dependency declarations as non-activating information with
  explicit required/optional admission semantics.
- [ ] Define optional logical MCP endpoint and public-tool requirements without
  credentials, local configuration paths, implicit connectivity, or authority.
- [ ] Define unknown-field, forward-compatibility, unsupported-required-field,
  and schema-evolution policy.
- [ ] Define stable Record/JSON representation and public diagnostic fields.
- [ ] Add schema/model construction, round-trip, compatibility, and malformed
  representation Executable Specifications.

Evidence:
- Pending.

## CSB-03: Canonical Path, Digest, and Equivalence Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF CAR resource and integrity contract maintainers
- Entry rule: CSB-02 is DONE.
- Completion rule: Source/archive location, path normalization, canonical
  bytes, digest input, and equivalence are relocation-stable and unambiguous.

- [ ] Define the canonical development-source manifest and content root.
- [ ] Define the canonical CAR-archive manifest and content root through the
  existing CAR resource model rather than a second archive scanner.
- [ ] Define normalized relative-path syntax, separator/case policy, Unicode
  normalization, reserved names, and maximum path/depth limits.
- [ ] Reject absolute paths, empty or ambiguous segments, traversal, symlink
  escape, duplicate normalized paths, and manifest/content-root escape.
- [ ] Define canonical file-byte input, SHA-256 representation, manifest
  digest, file ordering, and aggregate bundle digest.
- [ ] Define closed-view missing and undeclared content behavior while leaving
  Cozy responsible for applying it to real source and CAR packaging trees.
- [ ] Define source/archive equivalence independently of host path, checkout
  root, archive container, timestamp, entry order, or filesystem metadata.
- [ ] Define size/count limits and deterministic bounded diagnostics.
- [ ] Add property-based normalization, relocation, repeated-run, digest,
  hostile-path, and equivalence Executable Specifications.

Evidence:
- Pending.

## CSB-04: Codec, Validator, and Failure Semantics

Stage Status:
- Current status: PLANNED
- Owner: CNCF codec, validation, and structured failure maintainers
- Entry rule: CSB-03 is DONE.
- Completion rule: One public contract surface deterministically decodes,
  encodes, validates, and classifies every admitted or rejected bundle view.

- [ ] Implement one canonical codec for the selected transport representation.
- [ ] Implement deterministic manifest-only and manifest-plus-content-view
  validation without embedding Cozy or launcher policy.
- [ ] Implement structured failures for unsupported schema, malformed fields,
  duplicate identities, unsafe paths, missing/undeclared content, digest
  mismatch, incompatibility, and unsatisfied required declarations.
- [ ] Preserve stable error identity and bounded safe facets without exposing
  file content, credentials, host-local paths, or sensitive configuration.
- [ ] Ensure interruption/fatal handling follows the canonical CNCF failure
  boundary and is not flattened into ordinary validation rejection.
- [ ] Guarantee codec and validator execution invokes no Skill file and opens
  no network, process, MCP, credential, or user-configuration capability.
- [ ] Define deterministic diagnostics suitable for Cozy lint and launcher
  presentation without prescribing those products' command text.
- [ ] Add Given/When/Then plus property-based totality, determinism,
  round-trip, hostile-input, redaction, and side-effect-boundary specifications.

Evidence:
- Pending.

## CSB-05: Normative Fixtures and Consumer API

Stage Status:
- Current status: PLANNED
- Owner: CNCF contract, fixture, and compatibility maintainers
- Entry rule: CSB-04 is DONE.
- Completion rule: Cozy and both launchers can consume one stable API and one
  identity-addressable valid/invalid fixture family without reimplementing
  CNCF semantics.

- [ ] Publish a minimal valid single-Skill fixture and a representative
  multi-Skill fixture with fixed canonical bytes and digests.
- [ ] Publish valid fixtures for optional compatibility, dependency, and MCP
  requirement declarations.
- [ ] Publish invalid fixtures for every required schema, identity, path,
  digest, content-view, compatibility, dependency, and MCP rejection class.
- [ ] Record fixture identity, expected outcome, canonical digest, and intended
  consumer in a machine-readable index.
- [ ] Define a stable consumer API for manifest decode/encode, validation,
  normalized content view, digest result, and structured failure inspection.
- [ ] Prove fixture behavior is deterministic across cold/repeated execution
  and every supported JVM/runtime combination.
- [ ] Prove the API does not expose mutable host paths, installer operations,
  configuration mutation, or runtime execution authority.
- [ ] Provide integration guidance for Cozy `SK24-02`, CNCF Launcher
  `SK24-03`, and Textus Launcher `SK24-04` without implementing them.

Evidence:
- Pending.

## CSB-06: Contract Promotion and Cozy Handoff

Stage Status:
- Current status: PLANNED
- Owner: CNCF release maintainers with Cozy Phase 24 contract consumers
- Entry rule: CSB-05 is DONE.
- Completion rule: The normative contract, accepted artifact, complete
  validation/review evidence, and item-by-item Cozy `SK24-01` handoff are
  recorded without starting downstream implementation.

- [ ] Promote accepted ownership, model, path, digest, compatibility,
  dependency, MCP, and safety semantics into CNCF `docs/design`.
- [ ] Promote schema, codec, validator, canonicalization, fixture, API, and
  failure behavior into CNCF `docs/spec` with Executable Specification links.
- [ ] Map every Cozy Phase 24 `SK24-01` checklist item to an exact Phase 66
  design/spec section, executable test identity, fixture identity, and accepted
  commit/artifact identity.
- [ ] Verify the handoff permits Cozy to begin `SK24-02` without redefining a
  CNCF-owned schema, path, digest, compatibility, or validation rule.
- [ ] Verify the handoff does not implement or claim Cozy `SK24-02`, CNCF
  Launcher `SK24-03`, Textus Launcher `SK24-04`, or end-to-end closure.
- [ ] Run focused codec, validator, resource, compatibility, and fixture
  Executable Specifications through the serialized SBT contract.
- [ ] Run `Test/compile` and the complete CNCF test suite through the serialized
  SBT contract.
- [ ] Run deterministic fixture and source/archive-view acceptance without
  executing Skill content or requiring network access.
- [ ] Complete clean read-only review, admitted review-fix, focused re-review,
  naming, executable-specification, and `git diff --check` gates.
- [ ] Record accepted CNCF commit, artifact/version, fixture digests, validation,
  review, and no-authority/no-execution evidence.
- [ ] Update strategy and Phase 66 closure ledgers only after every acceptance
  item passes.
- [ ] Hand the accepted evidence to the Cozy repository; Cozy ledger mutation
  and `SK24-01` closure remain a separate Cozy-authorized change.

Evidence:
- Pending.
