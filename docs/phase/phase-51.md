# Phase 51 - CNCF-Cozy CML Generation Version Alignment

status=closed
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

Phase 51 establishes generation-version alignment before Phase 59 Information
runtime canonicalization and applies it generally to CNCF CML and generated
CAR projects.

Phase 52 is the clean-break follow-up for lossless Entity ID serialization.
It may supersede Phase 51's context-restoration implementation without
requiring compatibility for old scalar IDs or existing CAR data.

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
| CV-01 | Version-source inventory and failing-first acceptance | Existing CNCF, Cozy, CML, CAR, and runtime version sources and contradictory paths are fixed as executable acceptance. | closed |
| CV-02 | Compatibility and ownership contract | Exact generation pairs, runtime ranges, authority, and precedence are defined without numeric-equality inference. | closed |
| CV-03 | CNCF build integration | CNCF `build.sbt` selects and validates one Cozy generator and one CNCF target deterministically. | closed |
| CV-04 | Cozy target validation | Cozy validates the requested CNCF target and runtime descriptor before generation. | closed |
| CV-05 | Generation provenance | Generated output records stable version, source, backend, and digest evidence. | closed |
| CV-06 | CAR metadata consistency | Cozy scaffold, build, packaging, review, and publication agree on build-time and runtime version meanings. | closed |
| CV-07 | Development and release acceptance | SNAPSHOT workflows remain explicit while releases use immutable compatible coordinates and deterministic diagnostics. | closed |
| CI-01 | Exact Collection Identity contract | Generated, custom, and raw persistence adapters preserve or receive the exact owning collection without name-only inference, runtime type exceptions, or a second decode. | closed |
| SP-01 | Persisted scalar store projection | CNCF owns store-value restoration, generated `fromStoreRecord` uses it, and the current-release nominal String compatibility fallback is removed. | closed |
| CV-08 | Downstream validation and closure | Representative generated projects pass, mismatches fail early, and canonical documentation records the verified contract. | closed |

## CV-08 Implementation Evidence

- CNCF cold/repeated Information generation is byte-stable for 25 Scala files
  and its provenance. The build and runtime-admission gate passed all 14
  focused scenarios.
- Cozy passed 76 compatibility, descriptor, lifecycle, package,
  runtime-manifest, publication, and scaffold scenarios. A shared-temporary
  directory assertion found by the aggregate run now checks only snapshots
  created by the operation under test.
- sbt-cozy's selected CAR publication and review-evidence scripted projects
  pass with the exact development Cozy/CNCF coordinates declared by their CAR
  fixture.
- ArtScene regenerated 155 Scala files, built and locally published
  `textus-art-scene-0.1.2-SNAPSHOT.car`, and passed all 15 representative
  identity, persisted-value, lifecycle, and assembly scenarios.
- CNCF continues to reject independently released pre-manifest CARs. CV-08
  records that boundary and does not weaken runtime admission or rewrite those
  immutable dependencies.
- Clean temporary builds supplied strict-manifest AI runtime, scraper, and
  toolchain CARs to ArtScene. The isolated maintained packaged-smoke mode
  supplied all four CARs explicitly and passed real standalone description,
  seed, definition, review-persistence, registration, and update-report
  behavior without project/default repository fallback.
- The following RE_REVIEW found that isolation also replaced normal discovery
  without an explicit provider-CAR directory. REVIEW_FIX confines isolation to
  that explicit mode, requires the provider directory for smoke-only
  execution, and restores normal CNCF discovery. The four-CAR smoke and
  configuration guard pass; normal discovery reaches the intentional
  pre-manifest-CAR rejection rather than failing with a missing provider API.
- The next RE_REVIEW found an unsafe cleanup acceptance regression and a
  `user.home` documentation contradiction. REVIEW_FIX restores explicit
  `dry_run=true`, documents the isolated child-JVM exception, and repairs raw
  exact-ID decoding exposed by the complete path. Five focused ArtScene
  scenarios pass, and the rebuilt four-CAR assembly passes cleanup preview,
  restart, Web/REST, backup export, and clean restore end to end.
- The fresh accumulator RE_REVIEW found that malformed raw identity could
  still reach a synthetic fallback, scalar normalization could discard an
  existing canonical owner, the exact-ID specification mixed two behavior
  boundaries, and the CI-01 note contradicted the owned raw-adapter contract.
  REVIEW_FIX now preserves canonical IDs, parses only scalar ingress, rejects
  malformed context-aware decode, separates the behavior steps, and clarifies
  the persistence-adapter boundary. Six focused scenarios, `Test/compile`, CAR
  lint, and the rebuilt complete four-CAR standalone path pass.
- A later fresh accumulator RE_REVIEW found that ArtScene's raw Record adapter
  could still rebind a typed same-name foreign owner, three specifications
  lacked complete action or responsibility boundaries, two current-task
  headers were stale, and the path-level compliance ledger omitted four of the
  current 82 Scala paths. That REVIEW_FIX preserved exact typed owners, added
  direct adapter regression evidence, repaired the specification structure and
  headers, completed the 82-file ledger, and passed 14 focused scenarios.
- The following RE_REVIEW found that three ArtScene ledger rows and the
  canonical phase/strategy summaries still described that older evidence, and
  that exact-owner restoration, service scalar ingress, automatic delivery,
  physical scalar projection, and repeat dispatch were not yet isolated as one
  semantic contract per executable example. REVIEW_FIX now separates those
  contracts, reconciles every canonical status source, and passes the expanded
  17-scenario identity/lifecycle/assembly gate plus `Test/compile`; normal CAR
  lint remains failure-free with only the recorded development warnings.
- The next RE_REVIEW found that the UnitOfWork example still executed
  EntitySpace canonicalization before its declared action, that SP-01's
  completed checklist and closed evidence contradicted its reopened status, and
  that the Stage Status blocks did not use the repository's mandatory stable
  status/update-rule schema. REVIEW_FIX now leaves only the UnitOfWork action in
  that example, restores SP-01 to `CLOSED`, adds all ten update rules, moves
  workflow detail to CV-08's `Current step`, and passes the stable 17-scenario
  gate plus `Test/compile`; normal CAR lint remains failure-free with only the
  recorded development warnings.
- The clean accumulator RE_REVIEW entered the final release gate.
  `simplemodeling-model` passed its full suite and `publishLocal`, while
  `simple-modeler` exposed one stale pre-projection expectation in
  `EntityCustomTypeResolutionSpec`. PHASE_TEST_FIX updated only that
  expectation and its executable-contract wording; the targeted suite passed
  all 7 scenarios plus `Test/compile`, and the scoped REVIEW, REVIEW_FIX, and
  clean RE_REVIEW accepted the correction. The next full-gate attempt passed
  the complete `simple-modeler` suite and `publishLocal`, then Cozy exposed two
  stale `BridgeContractSpec` publication fixtures after 737 of 739 tests
  succeeded. PHASE_TEST_FIX changed only those fixtures to build valid
  SNAPSHOT CARs with canonical project metadata, ABI evidence, and the current
  proven development generation pair, while preserving the rule that
  SNAPSHOT publication does not create a release catalog. The bridge suite
  passed all 13 scenarios plus `Test/compile`; its scoped REVIEW and clean
  RE_REVIEW passed. The following final-gate attempt passed and locally
  published `simplemodeling-model` and `simple-modeler`, then CNCF's
  deterministic 25-file generation gate passed but its full suite failed 23 of
  2,611 tests across ten action/entity/UnitOfWork/blob/component suites.
  CNCF was not locally published. Independent Cozy and sbt-cozy full suites
  passed 739 and 123 tests respectively and were locally published; ArtScene
  remained unrun because it requires the failed CNCF publication. No commit
  was created. CNCF PHASE_TEST_FIX, scoped REVIEW, and clean RE_REVIEW are now
  complete. Fresh all-required full validation, final release evidence, and
  release commits remain required before CV-08 and Phase 51 can close.
- The next fresh final gate passed and locally published all five upstream
  repositories. ArtScene CAR lint remained free of `FAIL`, and its CAR built,
  but its full suite passed 388 of 389 tests before
  `ArtSceneFacilitySubscriptionSpec` failed at line 55. ArtScene
  `publishLocal` did not run and no commit was created. The next
  PHASE_TEST_FIX and review are limited to that latest ArtScene failure.
- The ArtScene-only correction now canonicalizes the raw subscription lookup
  id and passes the resolved aggregate's authoritative id into the generated
  update command. Its focused 5-scenario suite and `Test/compile` passed,
  followed by scoped REVIEW and clean RE_REVIEW. The fresh ArtScene full gate,
  final evidence, and release commits remain.
- The final affected-repository gate built and locally published the ArtScene
  CAR and passed all 389 tests across 41 suites. The unchanged upstream
  validated trees retain passing full suites and local publications:
  `simplemodeling-model` 58 tests, `simple-modeler` 44, CNCF 2,613, Cozy 739,
  and sbt-cozy 123. All compatibility, provenance, generation, runtime,
  downstream, review, documentation, and release-commit gates pass. CV-08 and
  Phase 51 are closed.

## Acceptance

- CNCF CML generation resolves one exact Cozy generator and CNCF target without
  ambient version selection.
- Supported CNCF-Cozy generation compatibility is explicit and tested; version
  number similarity has no semantic meaning.
- `build.cozyVersion`, the exact CNCF compile dependency, and
  `packaging.car.runtime.cncf` retain distinct, consistent meanings.
- The CV-02 production admission API rejects missing, unsupported, and
  unproven/incompatible generation inputs with typed diagnostics. Its explicit
  source resolver requires project, owning-build bridge, and CLI values to
  agree, selects provenance in that order, and uses published default only as
  a no-explicit-source fallback. Enforcement
  at the actual CNCF/Cozy generation invocation remains CV-03. CV-03 rejects
  absent or contradictory invocation sources before generation. CV-04 owns
  runtime descriptor target/schema/digest validation only; CV-05 owns
  provenance and provenance/digest tampering.
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
- [Phase 52 - Exact Entity ID Serialization and Collection Identity](phase-52.md)
- [Phase 59 - Information CML Runtime Canonicalization](phase-59.md)
- [ArtScene Collection Identity Contract Handoff](../journal/2026/07/2026-07-26-artscene-collection-identity-contract-handoff.md)
- [CV-07 Development and Release Acceptance](../notes/phase-51-cv07-development-release-acceptance.md)
- [CI-01 Exact Collection Identity Contract](../notes/phase-51-ci01-exact-collection-identity-contract.md)
- [Entity Collection Identity Design](../design/entity-collection-identity.md)
- [Entity Collection Identity Specification](../spec/entity-collection-identity.md)
- [Cozy CAR project metadata ownership](../../../cozy/docs/design/car-project-metadata-ownership.md)
- [Cozy CAR project scaffold](../../../cozy/docs/spec/car-project-scaffold.md)
