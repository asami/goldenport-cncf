# Phase 51 - CNCF-Cozy CML Generation Version Alignment

status=planned
planned_at=2026-07-26
depends_on=[Phase 50](phase-50.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 51 Checklist](phase-51-checklist.md)

## Purpose

Make the CNCF version targeted by CML generation and the Cozy version that
performs that generation an explicit, reproducible, and validated build
contract.

Phase 51 aligns CNCF `build.sbt`, Cozy generation, CAR compile dependencies,
and declared CNCF runtime compatibility without assuming that CNCF and Cozy
share the same version number.

## Dependency

Phase 51 begins after Phase 50 PC-02 closes and Phase 50 returns to CLOSED.

Phase 51 establishes generation-version alignment before Phase 53 Information
runtime canonicalization and applies it generally to CNCF CML and generated
CAR projects.

## Scope

- Inventory every CNCF and Cozy version source used by CML generation.
- Define one owned compatibility contract for a CNCF target and Cozy generator.
- Pin and validate the Cozy generator selected by CNCF `build.sbt`.
- Pass and validate the exact CNCF generation target and runtime descriptor.
- Align Cozy `project.yaml` build, compile, and runtime compatibility metadata.
- Replace the current-release logical-name-only collection validation with an
  exact generated/runtime Collection Identity contract.
- Add a CNCF-owned persisted-value projection contract that restores physical
  datastore values to declared Entity attribute types before ordinary
  `fromRecord` decoding.
- Make Cozy/SimpleModeler-generated `EntityPersistent.fromStoreRecord` code use
  that CNCF contract, including nominal String values whose JSON object text is
  returned by a datastore as a `Record`.
- Remove the current-release generator compatibility fallback after the CNCF
  persisted-value projection is available and downstream generated code has
  migrated.
- Record deterministic generation provenance and content digests.
- Reject missing, contradictory, unsupported, and mutable release inputs early.
- Validate development SNAPSHOT and immutable release workflows.
- Promote verified build and generation contracts to design/specification.

## Non-Goals

- Requiring CNCF and Cozy to have equal numeric versions.
- Making Cozy a CAR runtime dependency.
- Replacing CNCF runtime/ABI compatibility with generator-version checks.
- Folding Information runtime canonicalization back into this phase.
- Redesigning CML semantics or unrelated generator output.
- Introducing a second version source beside existing build and project
  metadata.
- Treating arbitrary business/API `Record` values as persisted scalar strings
  in the ordinary `ValueReader` or `fromRecord` path.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CV-01 | Version-source inventory and failing-first acceptance | Existing CNCF, Cozy, CML, CAR, and runtime version sources and contradictory paths are fixed as executable acceptance. | planned |
| CV-02 | Compatibility and ownership contract | Exact generation pairs, runtime ranges, authority, and precedence are defined without numeric-equality inference. | planned |
| CV-03 | CNCF build integration | CNCF `build.sbt` selects and validates one Cozy generator and one CNCF target deterministically. | planned |
| CV-04 | Cozy target validation | Cozy validates the requested CNCF target and runtime descriptor before generation. | planned |
| CV-05 | Generation provenance | Generated output records stable version, source, backend, and digest evidence. | planned |
| CV-06 | CAR metadata consistency | Cozy scaffold, build, packaging, review, and publication agree on build-time and runtime version meanings. | planned |
| CV-07 | Development and release acceptance | SNAPSHOT workflows remain explicit while releases use immutable compatible coordinates and deterministic diagnostics. | planned |
| CI-01 | Exact Collection Identity contract | Generated, custom, and raw persistence adapters preserve or receive the exact owning collection without name-only inference, runtime type exceptions, or a second decode. | planned |
| SP-01 | Persisted scalar store projection | CNCF owns store-value restoration, generated `fromStoreRecord` uses it, and the current-release nominal String compatibility fallback is removed. | planned |
| CV-08 | Downstream validation and closure | Representative generated projects pass, mismatches fail early, and canonical documentation records the verified contract. | planned |

## Acceptance

- CNCF CML generation resolves one exact Cozy generator and CNCF target without
  ambient version selection.
- Supported CNCF-Cozy generation compatibility is explicit and tested; version
  number similarity has no semantic meaning.
- `build.cozyVersion`, the exact CNCF compile dependency, and
  `packaging.car.runtime.cncf` retain distinct, consistent meanings.
- Generation rejects a missing or incompatible target/descriptor before
  compilation or packaging.
- Generated artifacts expose reproducible version and digest provenance.
- Released builds use immutable released coordinates; development SNAPSHOT
  usage is explicit and diagnosable.
- CAR runtime activation validates CNCF runtime/ABI compatibility and does not
  require Cozy to be installed.
- Multiple collections with the same logical name are either resolved by exact
  identity or rejected deterministically; logical-name equality is not the
  final ownership contract.
- Persisted nominal String values round-trip when a datastore exposes JSON
  object text as a `Record`, without changing ordinary `ValueReader` semantics
  or introducing application-local Base64/prefix encodings.
- Generated `EntityPersistent.fromStoreRecord` uses the CNCF persisted-value
  projection API, while `fromRecord` remains the ordinary business/API Record
  decoder.
- The temporary SimpleModeler nominal String `Record` fallback used to close
  the preceding release is removed after representative generated CARs migrate.
- Full CNCF, Cozy, and representative downstream validation passes.

## Planning References

- [CNCF build](../../build.sbt)
- [Phase 50 - SimpleEntity Revision and OCC Simplification](phase-50.md)
- [Phase 53 - Information CML Runtime Canonicalization](phase-53.md)
- [ArtScene Collection Identity Contract Handoff](../journal/2026/07/2026-07-26-artscene-collection-identity-contract-handoff.md)
- [Cozy CAR project metadata ownership](../../../cozy/docs/design/car-project-metadata-ownership.md)
- [Cozy CAR project scaffold](../../../cozy/docs/spec/car-project-scaffold.md)
