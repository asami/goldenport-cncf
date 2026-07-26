# Phase 51 Checklist - CNCF-Cozy CML Generation Version Alignment

status=planned
phase=[Phase 51 - CNCF-Cozy CML Generation Version Alignment](phase-51.md)

This checklist is the authoritative Phase 51 state ledger after Phase 51
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 50 PC-02 closes and Phase 50 returns to CLOSED.

## CV-01: Version-Source Inventory and Failing-First Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy, sbt-cozy, simple-modeler, and representative CAR
  maintainers
- Entry rule: Phase 50 PC-02 is DONE and Phase 50 is CLOSED.
- Completion rule: Every effective build, generation, packaging, and runtime
  version source and every conflicting path is recorded before implementation.

- [ ] Inventory CNCF artifact version, Scala binary version, Cozy generator
  version, simple-modeler backend version, and `simplemodeling-model` version.
- [ ] Inventory every CNCF `build.sbt` CML generation task and launcher path.
- [ ] Inventory Cozy `--runtime`, `--cncf-version`, and
  `--cncf-runtime-descriptor` resolution and validation.
- [ ] Inventory CAR `build.cozyVersion`, exact CNCF compile dependency, and
  `packaging.car.runtime.cncf` fields.
- [ ] Inventory scaffold, sbt bridge, package, review, publication, and runtime
  descriptor ownership.
- [ ] Identify ambient, duplicated, derived, mutable, and silently defaulted
  version sources.
- [ ] Record existing development SNAPSHOT and release workflows.
- [ ] Register failing-first Executable Specification identities for every
  Phase 51 acceptance group.
- [ ] Add acceptance cases for missing, contradictory, unsupported, and
  tampered version evidence.

Evidence:
- Pending.

## CV-02: Compatibility and Ownership Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF and Cozy release/build maintainers
- Entry rule: CV-01 is DONE.
- Completion rule: Build-time generation compatibility and runtime
  compatibility have distinct authoritative models, owners, and precedence.

- [ ] Define the exact CNCF target identity used to compile generated code.
- [ ] Define the exact Cozy coordinate that owns the generation behavior.
- [ ] Define a tested CNCF-Cozy generation compatibility pair or bounded set.
- [ ] Prohibit compatibility inference from equal or similar version numbers.
- [ ] Define the authority and publication location of compatibility evidence.
- [ ] Define `build.cozyVersion` as an exact build-time generator coordinate.
- [ ] Define the CAR CNCF compile dependency as the exact generated-code target.
- [ ] Define `packaging.car.runtime.cncf` as the independent runtime
  compatibility range and tested set.
- [ ] Define precedence and diagnostics when build, project, descriptor, or
  command-line values disagree.
- [ ] Define development SNAPSHOT admission separately from immutable release
  admission.
- [ ] Define which contract is checked during generation, compilation,
  packaging, publication, and runtime activation.

Evidence:
- Pending.

## CV-03: CNCF Build Integration

Stage Status:
- Current status: PLANNED
- Owner: CNCF build maintainers
- Entry rule: CV-02 is DONE.
- Completion rule: CNCF CML generation uses one explicit Cozy generator and
  one exact CNCF target with deterministic inputs and failures.

- [ ] Replace ad hoc CNCF build constants with one authoritative version
  resolution path without adding a competing source.
- [ ] Pin the exact Cozy generator coordinate used by every CNCF CML task.
- [ ] Pass the effective CNCF artifact version as the generation target.
- [ ] Generate or select the matching CNCF runtime descriptor before CML
  generation.
- [ ] Pass the CNCF target and descriptor through the supported Cozy
  invocation.
- [ ] Reject absent, unresolved, or contradictory generation inputs.
- [ ] Ensure incremental and clean builds resolve the same inputs.
- [ ] Ensure concurrent source-generation tasks cannot observe different
  version selections.
- [ ] Add cold-build and repeated-build specifications.

Evidence:
- Pending.

## CV-04: Cozy Target Validation

Stage Status:
- Current status: PLANNED
- Owner: Cozy CLI, modeler, and sbt bridge maintainers
- Entry rule: CV-03 is DONE.
- Completion rule: Cozy verifies that its requested CNCF target, descriptor,
  and supported generation contract agree before emitting source.

- [ ] Reuse the existing `--cncf-version` and
  `--cncf-runtime-descriptor` contract.
- [ ] Verify the runtime descriptor identifies the requested CNCF target.
- [ ] Reject a missing descriptor when the selected generator feature requires
  CNCF runtime catalog evidence.
- [ ] Verify the resolved Cozy coordinate is admitted for the CNCF target.
- [ ] Emit structured diagnostics identifying source, expected value, actual
  value, and corrective action.
- [ ] Keep generator compatibility validation out of CAR runtime activation.
- [ ] Preserve deterministic behavior across CLI and sbt bridge invocation.
- [ ] Add compatible-pair, incompatible-pair, missing-version, and
  descriptor-mismatch specifications.

Evidence:
- Pending.

## CV-05: Generation Provenance

Stage Status:
- Current status: PLANNED
- Owner: Cozy modeler and CNCF build maintainers
- Entry rule: CV-04 is DONE.
- Completion rule: Generated output carries reproducible evidence of its
  target, generator, source, backend, and result.

- [ ] Define a stable generation provenance schema and schema version.
- [ ] Record the CNCF target version and runtime descriptor digest.
- [ ] Record the exact Cozy generator and simple-modeler backend versions.
- [ ] Record CML source identity and digest.
- [ ] Record generated-output identity and deterministic digest.
- [ ] Exclude machine-local paths, timestamps, and unstable ordering from
  reproducibility-critical evidence.
- [ ] Define whether provenance is embedded, packaged as metadata, or both.
- [ ] Reject tampered or internally contradictory provenance.
- [ ] Add deterministic cold-generation and tamper-detection specifications.

Evidence:
- Pending.

## CV-06: CAR Metadata Consistency

Stage Status:
- Current status: PLANNED
- Owner: Cozy scaffold, archive, review, publication, and CAR maintainers
- Entry rule: CV-05 is DONE.
- Completion rule: CAR build and package paths use one coherent interpretation
  of generator, compile-target, and runtime-compatibility metadata.

- [ ] Preserve exact `build.cozyVersion` in scaffold and build invocation.
- [ ] Preserve the exact CNCF compile dependency selected for generated code.
- [ ] Reconcile the compile target with
  `packaging.car.runtime.cncf.minimum`, maximum, excluded, and tested values.
- [ ] Reject runtime ranges that exclude the exact compile target.
- [ ] Ensure package, review, and publication report the same compatibility
  decision.
- [ ] Include generation provenance in CAR metadata without making Cozy a
  runtime dependency.
- [ ] Keep runtime activation dependent on CNCF runtime/ABI compatibility,
  integrity, and the CAR runtime range.
- [ ] Add scaffold-to-package and packaged-CAR round-trip specifications.

Evidence:
- Pending.

## CV-07: Development and Release Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy, and release maintainers
- Entry rule: CV-06 is DONE.
- Completion rule: Development and release builds apply explicit, reproducible
  policies and cannot silently cross their coordinate boundaries.

- [ ] Define admitted CNCF-SNAPSHOT and Cozy-SNAPSHOT pairing for local
  development.
- [ ] Require explicit opt-in and diagnostic output for mutable development
  coordinates.
- [ ] Require released CNCF generation to use an immutable released Cozy
  coordinate.
- [ ] Reject release output built with an unbounded, unresolved, or mutable
  generator coordinate.
- [ ] Verify compatibility evidence is published before a new pair is selected
  by default.
- [ ] Verify cache and local-publication behavior cannot disguise a coordinate
  mismatch.
- [ ] Add release/SNAPSHOT boundary, stale cache, and absent artifact
  specifications.
- [ ] Document the manual recovery path for an unavailable compatible
  generator.

Evidence:
- Pending.

## CI-01: Exact Collection Identity Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity runtime, SimpleModeler generation, Cozy integration, and
  representative CAR maintainers
- Entry rule: CV-07 is DONE.
- Completion rule: Exact collection ownership survives generation, storage,
  decoding, and runtime projection without the current-release same-name
  assumption.

- [ ] Specify which boundary owns the complete `EntityCollectionId`.
- [ ] Specify a storage/decoder context contract that does not rewrite a custom
  codec's physical input or invoke it a second time.
- [ ] Specify generated Entity, custom typed codec, and raw `Record` adapter
  behavior without runtime `isInstanceOf` policy inference.
- [ ] Define deterministic behavior when multiple collections share one
  logical name.
- [ ] Define compatibility and migration behavior for older generated
  artifacts and persisted scalar `EntityId` values.
- [ ] Add failing-first executable coverage for exact identity preservation,
  same-name ambiguity, cross-collection rejection, and custom scalar codecs.
- [ ] Validate representative generated CARs, including ArtScene, without
  application-local identity repair.
- [ ] Remove the Phase 50 current-release logical-name-only closure assumption.
- [ ] Promote the verified contract to design and specification documents.

Evidence:
- Pending.

## SP-01: Persisted Scalar Store Projection

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity runtime, SimpleModeler generation, Cozy integration, and
  representative CAR maintainers
- Entry rule: CI-01 is DONE.
- Completion rule: Physical datastore values are restored through one
  CNCF-owned projection before generated Entity decoding, and no ordinary
  `ValueReader` or application-local encoding workaround owns that policy.

- [ ] Specify the boundary between ordinary `fromRecord` decoding and
  persistence-specific `fromStoreRecord` decoding.
- [ ] Add a CNCF persisted-value projection API driven by the declared Entity
  attribute/storage metadata.
- [ ] Preserve scalar String identity when JSON object text is returned by a
  datastore as a `Record`.
- [ ] Keep `{ value: ... }` wrapper decoding in the ordinary generated
  `ValueReader` contract without treating every arbitrary `Record` as a
  persisted scalar.
- [ ] Make Cozy/SimpleModeler-generated `EntityPersistent.fromStoreRecord`
  implementations call the CNCF projection API.
- [ ] Add failing-first executable coverage for nominal String JSON-object
  round-trip, wrapper decoding, non-JSON scalar values, optional values, and
  malformed persisted representations.
- [ ] Validate the ArtScene `NotificationIntentMetadataJson` update and
  post-commit EntitySpace projection path without Base64, prefix, or
  application-local repair.
- [ ] Remove the current-release SimpleModeler nominal String `Record`
  compatibility fallback after generated downstream code has migrated.
- [ ] Promote the verified persisted-value projection contract to CNCF design
  and specification documents.

Evidence:
- Pending.

## CV-08: Downstream Validation and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy, representative CAR, and downstream maintainers
- Entry rule: SP-01 is DONE.
- Completion rule: Compatible builds and runtime combinations pass,
  incompatible inputs fail at their owning boundary, and canonical
  documentation matches verified behavior.

- [ ] Validate CNCF Information CML cold generation through `build.sbt`.
- [ ] Validate a representative Cozy-generated CAR from scaffold through
  compile, package, review, and publication checks.
- [ ] Validate the exact CNCF compile target against every declared tested
  runtime version.
- [ ] Verify an unsupported CNCF-Cozy generation pair fails before source
  compilation or packaging.
- [ ] Verify an unsupported CNCF runtime fails CAR activation independently of
  Cozy provenance.
- [ ] Verify a compatible packaged CAR runs without Cozy installed.
- [ ] Run full affected CNCF and Cozy validation.
- [ ] Run representative downstream smoke tests.
- [ ] Perform read-only review, review-fix, and clean re-review.
- [ ] Promote verified ownership and lifecycle decisions to `docs/design`.
- [ ] Promote public build, metadata, and diagnostics contracts to `docs/spec`.
- [ ] Update strategy, phase, checklist, build, and generated documentation.
- [ ] Record final compatibility, dependency, provenance, and release evidence.
- [ ] Close Phase 51 only after all completion rules and documentation gates
  pass.

Evidence:
- Pending.
