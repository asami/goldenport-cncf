# Phase 51 Checklist - CNCF-Cozy CML Generation Version Alignment

status=in-progress
phase=[Phase 51 - CNCF-Cozy CML Generation Version Alignment](phase-51.md)

This checklist is the authoritative Phase 51 state ledger after Phase 51
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 50 PC-02 closes and Phase 50 returns to CLOSED.

## CV-01: Version-Source Inventory and Failing-First Acceptance

Stage Status:
- Current status: CLOSED
- Owner: CNCF, Cozy, sbt-cozy, simple-modeler, and representative CAR
  maintainers
- Entry rule: Phase 50 PC-02 is DONE and Phase 50 is CLOSED.
- Completion rule: Every effective build, generation, packaging, and runtime
  version source and every conflicting path is recorded before implementation,
  and the clean independent RE_REVIEW gate is accepted.

- [x] Inventory CNCF artifact version, Scala binary version, Cozy generator
  version, simple-modeler backend version, and `simplemodeling-model` version.
- [x] Inventory every CNCF `build.sbt` CML generation task and launcher path.
- [x] Inventory Cozy `--runtime`, `--cncf-version`, and
  `--cncf-runtime-descriptor` resolution and validation.
- [x] Inventory CAR `build.cozyVersion`, exact CNCF compile dependency, and
  `packaging.car.runtime.cncf` fields.
- [x] Inventory scaffold, sbt bridge, package, review, publication, and runtime
  descriptor ownership.
- [x] Identify ambient, duplicated, derived, mutable, and silently defaulted
  version sources.
- [x] Record existing development SNAPSHOT and release workflows.
- [x] Register failing-first Executable Specification identities for every
  Phase 51 acceptance group.
- [x] Register the twelve canonical acceptance identities exactly once at
  their owning spec/slice; Cozy CV-02 owns pure compatibility admission and
  source/lifecycle outcomes, while repository CV-01 specs retain only their
  own production-boundary observations.
- [x] Complete a clean independent RE_REVIEW and record acceptance of the
  CV-01 implementation evidence; CV-01 remained IN_PROGRESS until this gate
  was accepted.

Evidence:
- CV-01 implementation evidence and focused-spec coverage are recorded in
  [the version-source inventory](../notes/phase-51-version-source-inventory.md),
  including Cozy's own sbt-cozy build plugin, the CozyScaffold generated
  default, the ArtScene consumer override, and ArtScene's project, descriptor,
  catalog, README, and historical version sources.
- All five implementation repositories were moved to their required
  development SNAPSHOT before these changes were reapplied. simplemodeling-model
  remains release and clean, and is inventory-only.
- Six focused CV-01 acceptance spec files across five repositories passed
  (21 scenarios across CNCF 5, Cozy 6, sbt-cozy 4, simple-modeler 3, and
  ArtScene 3); the focused documentation diff checks passed with
  `git diff --check`.
- Final focused results were CNCF 1 succeeded/4 canceled, Cozy 0/6,
  sbt-cozy 0/4, simple-modeler 0/3, and ArtScene 2/1; no focused test failed.
- The clean independent read-only RE_REVIEW passed with no actionable finding.
- Deferred integration behavior remains canceled/pending in dedicated specs,
  with complete Given/When/Then boundaries before each deferred outcome. CV-01
  is CLOSED together with CV-02, CV-03, CV-04, and CV-05.

## CV-02: Compatibility and Ownership Contract

Stage Status:
- Current status: CLOSED
- Owner: CNCF and Cozy release/build maintainers
- Entry rule: CV-01 is DONE.
- Completion rule: Build-time generation compatibility and runtime
  compatibility have distinct authoritative models, owners, and precedence.
  All present explicit values must agree; project > owning-build bridge > CLI
  selects provenance among agreeing values, while the published default is used
  only when no explicit value exists.

- [x] Define the exact CNCF target identity used to compile generated code.
- [x] Define the exact Cozy coordinate that owns the generation behavior.
- [x] Define a tested CNCF-Cozy generation compatibility pair or bounded set.
- [x] Prohibit compatibility inference from equal or similar version numbers.
- [x] Define the authority and publication location of compatibility evidence.
- [x] Define `build.cozyVersion` as an exact build-time generator coordinate.
- [x] Define the CAR CNCF compile dependency as the exact generated-code target.
- [x] Define `packaging.car.runtime.cncf` as the independent runtime
  compatibility range and tested set.
- [x] Define precedence and diagnostics when project, owning-build bridge, or
  command-line values disagree.
- [x] Define development SNAPSHOT admission separately from immutable release
  admission.
- [x] Complete the independent CV-02 RE_REVIEW/PASS gate.

Evidence:
- Cozy owns `cozy.compatibility.GenerationCompatibility` and the
  `cozy.generation-compatibility.v1` evidence shape. Its production loader
  parses and validates the packaged resource, whose observed 0.5.1/0.3.0 pair
  remains unproven.
- Cozy `Phase51Cv02CompatibilitySpec` contains and passes 17 CV-02 scenarios:
  packaged-resource parsing, malformed evidence, exact admission, lifecycle,
  unsupported and missing dimensions, evidence semantics, precedence,
  deterministic diagnostic ordering, and deferred ownership. Its property
  scenario performs 50 ScalaCheck iterations; those iterations are not extra
  CV-02 scenarios.
- CV-02 owns pure generation compatibility admission: missing CNCF/Cozy
  coordinates, unsupported CNCF, unsupported Cozy, unproven and incompatible
  pairs, and development/release lifecycle admission. It also owns explicit
  source contradiction/precedence resolution. The twelve canonical
  acceptance identities are registered exactly once at their authoritative
  owner in the inventory; repository-local CV-01 specs retain only their own
  production boundary observations.
- CV-03 owns wiring/resolving explicit inputs at the actual CNCF/Cozy
  generation invocation and rejecting absent or contradictory invocation
  sources before generation. CV-04 owns runtime descriptor target/schema/
  digest validation only. CV-05 owns provenance and provenance/digest
  tampering. CAR metadata/runtime enforcement remains CV-06.
- The clean independent read-only RE_REVIEW passed with no actionable finding.
  `simplemodeling-model` remains release-valued, clean, and inventory-only.

## CV-03: CNCF Build Integration

Stage Status:
- Current status: CLOSED
- Owner: CNCF build maintainers
- Entry rule: CV-02 is DONE.
- Completion rule: CNCF CML generation uses one explicit Cozy generator and
  one exact CNCF target with deterministic inputs and failures.

- [x] Replace ad hoc CNCF build constants with one authoritative version
  resolution path without adding a competing source.
- [x] Pin the exact Cozy generator coordinate used by every CNCF CML task.
- [x] Pass the effective CNCF artifact version as the generation target.
- [x] Generate or select the matching CNCF runtime descriptor before CML
  generation.
- [x] Pass the CNCF target and descriptor through the supported Cozy
  invocation.
- [x] Reject absent or unresolved generation inputs; the build exposes only one
  source for each input, so contradictory invocation sources cannot coexist.
- [x] Ensure incremental and clean builds resolve the same inputs.
- [x] Ensure concurrent source-generation tasks cannot observe different
  version selections.
- [x] Add cold-build and repeated-build specifications.
- [x] Complete the independent CV-03 RE_REVIEW/PASS gate.

Evidence:
- At CV-03 closure, `CncfGenerationBuildContract` resolved pinned Cozy `0.3.0`,
  the root build's `version.value` (`0.5.2-SNAPSHOT`), and the descriptor
  produced by `generateCncfRuntimeDescriptor`; CV-05B deliberately advances the
  generator to provenance-capable `0.3.1-SNAPSHOT` and adds source/provenance
  inputs without introducing another authority.
- The focused CV-03 executable specification passed all four scenarios,
  including missing-input rejection and 32 concurrent resolution evaluations;
  `Test/compile` passed in the same serialized SBT run.
- `verifyInformationCmlGenerationDeterminism` is a durable build acceptance
  task and a dependency of the full `Test / test` task. It launches the actual
  supported Cozy generation command twice against the same output boundary and
  compares the complete relative Scala file set and SHA-256 digests.
- Serialized cold and repeated generation both succeeded with Cozy `0.3.0`,
  CNCF `0.5.2-SNAPSHOT`, and the same generated runtime descriptor. All 25
  generated Scala files retained identical SHA-256 hashes.
- The independent CV-03 RE_REVIEW/PASS gate passed with no actionable
  findings.

## CV-04: Cozy Target Validation

Stage Status:
- Current status: CLOSED
- Owner: Cozy CLI, modeler, and sbt bridge maintainers
- Entry rule: CV-03 is DONE.
- Completion rule: Cozy verifies that its requested CNCF target, descriptor,
  and supported generation contract agree before emitting source.

- [x] Reuse the existing `--cncf-version` and
  `--cncf-runtime-descriptor` contract.
- [x] Verify the runtime descriptor identifies the requested CNCF target.
- [x] Reject a missing descriptor when the selected generator feature requires
  CNCF runtime catalog evidence.
- [x] Emit structured diagnostics identifying source, expected value, actual
  value, and corrective action.
- [x] Keep generator compatibility validation out of CAR runtime activation.
- [x] Preserve deterministic behavior across CLI and sbt bridge invocation.
- [x] Add runtime-descriptor target/schema/digest validation specifications.
- [x] Complete the independent CV-04 RE_REVIEW/PASS gate.

Evidence:
- Cozy CLI preflight and the sbt bridge use one descriptor validator before
  source generation. It requires the exact target, descriptor, and additive
  `--cncf-runtime-descriptor-sha256` argument together, and validates file
  existence, digest, root schema/runtime identity, target version, and
  predefined Result schema.
- Project defaults, owning-build bridge settings, and request arguments must
  agree for each descriptor-contract value. Contradictory explicit values fail
  with the typed `CNCF_DESCRIPTOR_SOURCE_CONFLICT` diagnostic rather than
  silently applying override precedence.
- Ambient global `~/.cozy` operation defaults are excluded from generation
  version and descriptor resolution, so owning-build generation cannot change
  or fail because of a machine-local default.
- sbt-cozy already extracts the descriptor and SHA-256; the Cozy bridge now
  preserves both `runtime.cncf.descriptor` settings, and the CNCF build
  computes and passes the digest for its generated descriptor.
- The final serialized Cozy focused run compiled all affected test sources and
  passed 27 scenarios with no failure or cancellation, including
  CLI/bridge preflight, unreadable and invalid-digest diagnostics,
  project/bridge/request contradiction rejection, ambient-global exclusion,
  and at least 50 ScalaCheck digest mutations.
- The serialized sbt-cozy descriptor run passed 3 scenarios. CNCF validation
  passed all 4 build-contract scenarios and verified identical generation for
  25 Scala files through released Cozy `0.3.0`, which accepted the additive
  digest argument.
- The independent read-only CV-04 RE_REVIEW/PASS gate found no actionable
  finding after the final focused validation and accepted the complete
  five-repository diff identity.

## CV-05: Generation Provenance

Stage Status:
- Current status: CLOSED
- Owner: Cozy modeler and CNCF build maintainers
- Entry rule: CV-04 is DONE.
- Completion rule: Generated output carries reproducible evidence of its
  target, generator, source, backend, and result.

- [x] Define a stable generation provenance schema and schema version.
- [x] Record the CNCF target version and runtime descriptor digest.
- [x] Record the exact Cozy generator and simple-modeler backend versions.
- [x] Record CML source identity and digest.
- [x] Record generated-output identity and deterministic digest.
- [x] Exclude machine-local paths, timestamps, and unstable ordering from
  reproducibility-critical evidence.
- [x] Define whether provenance is embedded, packaged as metadata, or both.
- [x] Reject tampered or internally contradictory provenance.
- [x] Add deterministic cold-generation and tamper-detection specifications.
- [x] Complete the Cozy-owned CV-05A producer/validator core through a clean
  independent RE_REVIEW.
- [x] Adopt and verify provenance at the CNCF build boundary in CV-05B.
- [x] Complete the independent CV-05 REVIEW and RE_REVIEW/PASS gates.

Evidence:
- Cozy writes packaged metadata at
  `target/cozy/generation-provenance.json` with schema
  `cozy.generation-provenance.v1`; generated Scala remains free of embedded
  provenance.
- The manifest records exact CNCF target and descriptor digest, executing Cozy
  version, compiled simple-modeler backend version, selected
  `simplemodeling-model` version, explicitly selected non-empty
  project-relative CML identity and digest, sorted generated Scala identities
  and file digests, an aggregate output digest, and a canonical evidence
  digest.
- Production validation compares expected generation inputs and recomputes
  source, generated-file, aggregate-output, and evidence digests. It rejects
  malformed schemas, source/output tampering, and internally contradictory
  evidence with typed deterministic diagnostics.
- Descriptor digest, validated fields, predefined Result catalog, and
  provenance use one immutable descriptor byte snapshot. Explicit CLI source
  identity is mandatory and excludes absolute, parent-escaping, and
  drive-prefixed identities; the sbt bridge derives it only within its owning
  project directory. Missing/unreadable evidence and invalid expected inputs
  remain typed validation results.
- Cozy captures CML bytes once before generation. The modeler and CML metadata
  producer consume an isolated materialization of that capture. A stale
  manifest is cleared before generation, and replacement provenance is
  validated at a temporary path before atomic publication.
- The serialized focused CV-05A review-fix pass compiled two main and three test
  Scala sources and completed `Test/compile`. After moving one matcher to its
  correct Then boundary, an intermediate rerun exposed and reported an extra
  spec brace; the brace was removed, and the final exact-state rerun compiled
  the changed test source and passed 36 tests across the CV-04, CV-05,
  predefined Result catalog, bridge, and CV-01 compliance suites with no
  failure. Six CV-01 ownership-registration scenarios canceled as specified.
  The subsequent strict-schema review fix adds a ninth CV-05 scenario that
  rejects unsigned fields at the root, target, generator, source, output, and
  artifact object levels. The nine CV-05 scenarios include immutable
  captured-source materialization,
  stale/failed manifest exclusion, drive-relative identity rejection, and a
  50-iteration source-identity property. Bridge source-identity derivation also
  passed 50 generated cases. The serialized strict-schema rerun compiled one
  main and one test Scala source, completed `Test/compile`, and passed 37 tests
  across the same five suites with six expected CV-01 cancellations and no
  failure. A second exact-state rerun preserved those counts after restoring
  unsupported-schema diagnostic precedence: a future schema with additional
  fields returns `SchemaMismatch`, while unknown fields in supported v1
  provenance return `ProvenanceMalformed`. The following clean independent
  RE_REVIEW accepted CV-05A with no actionable finding.
- Cozy now exposes the authoritative validator through
  `generation-provenance-validate`. Its tenth CV-05 scenario exercises a valid
  direct CLI validation, expected generator contradiction, and help surface.
  The serialized five-suite run compiled three main and one test Scala source,
  passed 38 tests with six expected CV-01 cancellations, and completed
  `Test/compile` with no failure. The exact tested source state was then
  published locally as `org.simplemodeling:cozy_2.12:0.3.1-SNAPSHOT` for the
  explicit development integration.
- CNCF CV-05B resolves the Information CML project-relative identity and
  pre-launch digest together with the pinned generator, target, and descriptor.
  Generation passes that identity, then the build invokes Cozy's authoritative
  validator with the same resolved evidence before accepting Scala.
  Cold/repeated snapshots include every generated Scala identity/digest and the
  provenance-file digest.
- The serialized CNCF CV-03/CV-05B focused run compiled one build-definition
  and three test Scala sources, passed seven scenarios with no failure, and
  completed `Test/compile`. Real generation used Cozy `0.3.1-SNAPSHOT` against
  CNCF `0.5.2-SNAPSHOT`; both validator runs passed, and repeated generation
  retained all 25 Scala files plus identical provenance bytes.
- Serialized CV-01 compliance runs also passed for sbt-cozy, simple-modeler,
  and ArtScene. Their new production-boundary properties each completed 50
  iterations; the deferred ownership scenarios retained their specified
  cancellation counts.
- The independent CV-05 REVIEW found one actionable naming issue in the CNCF
  build contract. REVIEW_FIX renamed `generationProvenancePath` to the
  rule-compliant `GENERATION_PROVENANCE_PATH`; its serialized focused rerun
  passed all three CV-05B scenarios and `Test/compile`, including real
  provenance validation.
- The subsequent independent read-only CV-05 RE_REVIEW/PASS gate found no
  actionable finding and accepted the complete five-repository implementation
  state. The accepted content identities were CNCF
  `e25d02359e53b9d4f7d146cb8cedeaed6dd2d93e96fe5d1f8ee286ef441df435`,
  Cozy
  `7326287bf827ba22809f6c78207992edcb99bea69fe9c88c2debf1977b235d45`,
  sbt-cozy
  `139ff6afad050dc73917933cefe453cc44350fd5dcbdae9784b3a2a2e0200a77`,
  simple-modeler
  `5d4a3d02619a9510fefab9ba2841ad2421babf461044381c5c4c47895ede555b`,
  and ArtScene
  `ac53c0d0f8bc5a9e2a9fbd96941e94596483bf75afa2fb23a02c28d07f13f3b8`.

## CV-06: CAR Metadata Consistency

Stage Status:
- Current status: CLOSED
- Owner: Cozy scaffold, archive, review, publication, and CAR maintainers
- Entry rule: CV-05 is DONE.
- Completion rule: CAR build and package paths use one coherent interpretation
  of generator, compile-target, and runtime-compatibility metadata.

- [x] Preserve exact `build.cozyVersion` in scaffold and build invocation.
- [x] Preserve the exact CNCF compile dependency selected for generated code.
- [x] Reconcile the compile target with
  `packaging.car.runtime.cncf.minimum`, maximum, excluded, and tested values.
- [x] Reject runtime ranges that exclude the exact compile target.
- [x] Ensure package, review, and publication report the same compatibility
  decision.
- [x] Include generation provenance in CAR metadata without making Cozy a
  runtime dependency.
- [x] Keep runtime activation dependent on CNCF runtime/ABI compatibility,
  integrity, and the CAR runtime range.
- [x] Add scaffold-to-package and packaged-CAR round-trip specifications.

Evidence:
- CV-06A adds Cozy `CarMetadataCompatibility`, which reads the unmerged CAR
  project contract, preserves the exact scaffold generator and CNCF compile
  coordinate, reconciles minimum/maximum/excluded/tested metadata, and returns
  stable typed diagnostics for missing, multiple, excluded, untested, or
  otherwise contradictory values.
- `CozyArchivePackager` applies that contract before archive acceptance,
  requires exactly one JAR descriptor whose runtime/module/version identifies
  the selected CNCF dependency, and prevents merged operation defaults from
  replacing either project metadata or resolved artifact evidence.
- The independent CV-06A REVIEW recorded six actionable findings: incomplete
  JAR identity validation, a packaging-kind classification bypass, a second
  merged-default runtime decision, missing negative package wiring coverage,
  mixed/incorrect specification placement, and premature checklist closure.
  REVIEW_FIX addressed all six.
- Serialized review-fix validation passed
  `CarMetadataCompatibilitySpec`, `CozyArchivePackagerCv06Spec`,
  `CozyArchivePackagerSpec`, and `ModelerScaffoldSpec`: 4 suites, 50 tests,
  zero failed/canceled, followed by successful `Test/compile`. The two CV-06A
  specs now separate compatibility-interface semantics from archive workflow,
  cover strict positive and negative package admission, and property-check at
  least 50 generated exact patch-version contracts.
- The following independent RE_REVIEW found two remaining actionable issues:
  resolved-artifact evidence still inferred a missing root descriptor
  `version` from legacy runtime/module fields, and accumulated Phase 51 specs
  still used artificial phase-only packages. The second REVIEW_FIX now accepts
  only the descriptor's root `version`, covers root-version absence at both
  evaluator and archive-output boundaries, and places every affected spec in
  its production responsibility package. The cross-responsibility Cozy CV-01
  inventory is split between `cozy.config` and `cozy.archive`.
- Serialized second-review-fix validation passed in all three affected
  repositories. Cozy completed 9 suites with 87 succeeded, 6 expected
  cancellations, and 0 failed; CNCF completed 3 suites with 8 succeeded,
  4 expected cancellations, and 0 failed; simple-modeler completed 1 suite
  with 3 expected cancellations and 0 failed. `Test/compile` succeeded in all
  three repositories.
- The subsequent accumulated-change RE_REVIEW found two presentation and
  ledger issues: the ten-scenario Cozy CV-05 provenance specification lacked
  large-surface `which` grouping, and CV-01 documentation still described five
  focused specs after the Cozy responsibility split produced six spec files
  across five repositories. REVIEW_FIX grouped the provenance behaviors by
  reproducible evidence, contradictory evidence, and source/owning-build
  identity, and corrected both CV-01 counts.
- Serialized validation of the exact grouped specification passed all 10
  scenarios with zero failed/canceled, followed by successful `Test/compile`.
- The next fresh RE_REVIEW found that Cozy's canonical CAR ownership/scaffold
  documents still omitted the implemented CV-06A package-gate contract and
  that twelve modified header-bearing Cozy Scala files retained stale
  `@version` dates. REVIEW_FIX now documents `build.cozyVersion`, unmerged
  `project.yaml` authority, exact resolved-JAR identity, and runtime-range
  admission in all affected Cozy design/spec surfaces, and normalizes every
  modified header-bearing Scala file to `Jul. 28, 2026`.
- Serialized latest-review-fix validation compiled 10 main and 2 test Scala
  sources, completed the four CV-06A compatibility/archive/packager/scaffold
  suites, and passed all 51 tests with zero failed/canceled, followed by
  successful `Test/compile`. Five unavailable-runtime-metadata warnings were
  expected fixture behavior.
- A clean independent RE_REVIEW accepted the latest CV-06A fixes, closing its
  four completion items.
- CV-06B implements one project-only compatibility decision shared by package,
  integrated CAR lint/Review, and publication. Publication preflights the
  decision before repository writes and projects catalog runtime metadata from
  the accepted contract; resolved-JAR evidence remains package-only. Focused
  serialized validation completed five suites and all 40 tests with zero
  failed/canceled, followed by successful `Test/compile`.
- The independent CV-06B REVIEW found four actionable inconsistencies:
  ArtScene omitted the exact Cozy generator required by normal CAR lint, the
  accumulated publisher and lint specs lacked active PBT, their setup crossed
  six Given boundaries, and the strategy/compliance ledger overstated review
  state and whole-file conformance.
- REVIEW_FIX adds ArtScene `build.cozyVersion: 0.3.1-SNAPSHOT` and feeds it to
  sbt-cozy's `cozyDelegateCoursierVersion`, so the checked-in project owns the
  exact Coursier-launched generator instead of relying on ambient `cozy`.
  ArtScene's acceptance spec observes the same version. At that validation
  point, normal CAR lint reported `car.metadata.compatibility.accepted` with
  only the existing missing ABI-baseline warning.
- REVIEW_FIX also restores all identified setup/Given boundaries and adds
  50-case properties through production publication and integrated-lint
  paths. Serialized Cozy validation compiled both modified specs and completed
  five suites with 42 passed and zero failed/canceled. Serialized ArtScene
  validation compiled its modified Scala 3 spec and completed 2 passed,
  1 expected CV-06 cancellation, and zero failed; `Test/compile` succeeded in
  both repositories.
- The first clean-attempt CV-06B RE_REVIEW accepted the accumulated code,
  executable specifications, whole-file Scala compliance, CAR lint, and diff
  hygiene, but found two blocking inventory inconsistencies: an obsolete
  CV-06A re-review-pending sentence and a final focused matrix that still
  reported the pre-fix 40-test baseline. REVIEW_FIX removes the obsolete state
  and records the validated 42-test result consistently. No code, build, or
  executable-specification file changed in this correction.
- The fresh clean CV-06B RE_REVIEW passed with no actionable finding. It
  accepted the accumulated code/specification ledger, all 32 modified Scala
  files, the 42-test Cozy and 2-pass/1-expected-cancel ArtScene evidence, and
  the normal CAR lint result captured at that validation point, which contained
  only the documented missing ABI-baseline warning.
- CV-06C1 now revalidates an existing
  `target/cozy/generation-provenance.json` against its source, generated Scala,
  aggregate/evidence digests, and accepted CNCF/Cozy project coordinates. The
  package gate validates and preserves one immutable accepted byte snapshot as
  top-level
  `generation-provenance.json`, while non-generated and legacy CAR sources may
  omit it. A scaffold-to-package executable scenario reads the packaged bytes
  back unchanged, and a contradictory target fails before archive output.
- Serialized CV-06C1 IMPLEMENT validation completed
  `CozyArchivePackagerCv06Spec`,
  `Phase51Cv05GenerationProvenanceSpec`, and `ModelerScaffoldSpec`: 3 suites,
  29 passed, zero failed/canceled, followed by successful `Test/compile`.
  The first focused run exposed a missing ABI-manifest fixture after the test
  introduced a CML source; the fixture was repaired without weakening the
  production gate, and the clean rerun passed.
- The independent CV-06C1 REVIEW found three actionable issues: the normal
  sbt-cozy generation path left provenance in its delegate work directory,
  generic `src/main/car` entries could inject an unvalidated document with the
  reserved provenance name, and the immutable package snapshot leaked its
  temporary file on success and failure.
- REVIEW_FIX delegates final-output evidence rebinding to Cozy after sbt-cozy
  installs managed Scala, requires the final provenance side output for
  incremental reuse, rejects multiple singular-v1 manifests, reserves the
  generic CAR source name, and scopes immutable package snapshots with
  unconditional cleanup. The executable specifications cover final project
  rebinding, bridge installation, multi-source rejection, reserved-name
  rejection, byte preservation, contradictory targets, and snapshot cleanup.
- Serialized REVIEW_FIX validation passed the two Cozy provenance/archive
  suites with 17 tests and the sbt-cozy delegated-generator suite with 17
  tests, with zero failure or cancellation. Both repositories completed
  `Test/compile`. The first realistic in-project delegate-work run exposed a
  missing exclusion at atomic pre-publication self-validation; the exclusion
  is now shared by evidence construction and that final validation, and the
  clean rerun passed.
- The next independent CV-06C1 RE_REVIEW found five actionable gaps: the
  sbt-cozy lifecycle scenario used a fake delegate rather than the current
  Cozy bridge, an explicitly selected delegated manifest was parsed but its
  canonical sibling was validated, canonical Cozy generation compatibility
  documents omitted the rebind/package lifecycle and singular-v1 constraints,
  the seventeen-scenario sbt-cozy specification lacked `which` grouping, and
  two modified sbt-cozy headers plus the modified-file compliance inventory
  were stale.
- The second REVIEW_FIX validates the exact selected delegated manifest,
  documents reserved-source rejection, isolated generation, exact rebind
  validation, delegate-root exclusion/deletion, atomic final publication,
  incremental side-output requirements, and singular-v1 multi-source failure.
  The sbt-cozy behaviors are grouped by responsibility and all modified
  header-bearing Scala sources now record `Jul. 28, 2026`.
- A new cross-repository executable integration specification runs the actual
  current Cozy `sbt-bridge v1` implementation through production sbt-cozy
  generation, invokes public provenance validation, packages the CAR, verifies
  exact top-level provenance bytes, and confirms delegate-work cleanup.
  Serialized validation first passed the two Cozy provenance/archive suites
  with 18 tests and `Compile/packageBin`; the refreshed current Cozy
  classpath then passed the grouped sbt-cozy and actual-bridge integration
  suites with 18 tests and `Test/compile`. Both runs had zero failures or
  cancellations.
- Whole-file static compliance was rechecked across all 35 modified Scala
  files (4 CNCF, 25 Cozy, 4 sbt-cozy, 1 simple-modeler, and 1 ArtScene):
  private/member naming, raw `assert`, and stale modified-file `@version`
  scans produced no finding. At that validation point, normal ArtScene CAR
  lint also passed with only the documented missing ABI-baseline warning;
  semantic inspection found its reference manual and user guide still cover
  purpose, contracts, configuration, first-success workflows, failures,
  troubleshooting, and canonical help/manual/OpenAPI navigation. Runtime
  routes were not re-run in this REVIEW_FIX. CV-06C1 still awaits a fresh
  independent clean RE_REVIEW.
- The following fresh RE_REVIEW found one remaining machine-contract drift:
  production and sbt-cozy used the new `rebind-generation-provenance` action,
  while the canonical `sbt-bridge v1` action list, README, request-fixture
  inventory, and real-parser specification omitted it.
- REVIEW_FIX registers the additive v1 action in the machine-readable contract
  and README, adds a canonical request carrying the delegated manifest,
  delegated output root, and owning project root, and verifies both action
  membership and exact representative arguments through the production bridge
  parser. The first focused `BridgeContractSpec` run exposed two older
  publish-CAR fixtures that lacked the now-required project-owned Cozy and CNCF
  compile coordinates; updating their shared project fixture to the current
  CV-06 contract produced a clean rerun with 13 passed, zero failed/canceled,
  and successful `Test/compile`.
- The next fresh RE_REVIEW found one documentation-parity defect: the
  machine-readable contract, canonical request fixture, and real-parser
  specification all included `publish-video`, but the modified canonical
  `sbt-bridge v1` README omitted it from the supported-action list.
- REVIEW_FIX restores `publish-video` in the README and adds an executable
  parity guard requiring every action declared by `contract.json` to appear
  in that README. The improved serialized SBT launch path completed the
  focused `BridgeContractSpec` and `Test/compile` with 13 passed, zero
  failed/canceled, and no persistent generated side effects.
- The following fresh RE_REVIEW accepted the bridge contract/documentation
  parity fix but found that the accumulated modified-file compliance evidence
  had not covered ordinary locals, private parameters, local constants,
  method-local helpers, and truly private model fields in
  `CozyPlugin.scala` and `CozyCarLint.scala`.
- REVIEW_FIX normalizes those internal identifiers while preserving public and
  package-visible sbt keys/APIs, named-argument labels, CLI spelling, serialized
  fields, and generated-source field names. Whole-file scans now find no
  remaining violation in the corrected categories. Serialized validation
  passed `CozyCarLintSpec` with 17 tests and the seven affected sbt-cozy
  parser/generator/web/delegation suites with 35 tests; both repositories also
  passed `Test/compile`, with zero failures/cancellations and no persistent
  generated side effects.
- The goal-phase baseline review then found that Cozy's `buildCar` boundary
  could treat non-CAR project metadata as an out-of-scope compatibility
  decision and still emit an archive without the runtime manifest that CNCF
  now requires for every CAR. REVIEW_FIX adds the CAR-specific
  `requireValidCarProject` boundary, rejects missing CAR classification with a
  structured diagnostic before writing output, and makes the accepted
  compatibility contract and generated runtime manifest mandatory.
  `CozyArchivePackagerCv06Spec` now covers this rejection and verifies that no
  unusable archive is produced. The serialized compatibility/archive run
  passed 12 tests in two suites with zero failures, and `Test/compile`
  succeeded.
- The subsequent review found that strict CAR admission had not yet migrated
  the public `package-car` help or the broader low-level archive
  specifications, that package admission repeated CNCF runtime metadata
  validation after `CarMetadataCompatibility`, and that non-CAR
  classification diagnostics always reported `actual=missing`.
  REVIEW_FIX keeps the strict archive boundary: `package-car` requires
  `--project-dir` and explicit CAR classification, accepted packaging always
  emits the runtime manifest, and generic metadata inspection alone remains
  optional for non-CAR projects. A structured `CarPackagingSpecSupport`
  supplies complete project and resolved-JAR evidence to low-level package
  fixtures through the same actual main/library JAR boundary used by
  production. The production-only validation-JAR input is absent, the duplicate
  runtime validator is removed, and classification diagnostics now report the
  actual project/packaging values. The public command rejects a missing
  `--project-dir` before metadata admission.
- The complete touched `CozyArchivePackagerSpec` now has 30 executable
  scenarios with one Given/When/Then boundary per scenario and five
  responsibility-level `which` sections; `ComponentApiJarPackagerSpec` retains
  8 of 8 scenarios under two responsibility sections. The latest serialized
  focused run covers those two suites plus the CV-06 archive and compatibility
  suites: 4 suites, 51 passed, zero failed/canceled, followed by successful
  `Test/compile`. Canonical Cozy ownership/scaffold documentation now states
  the strict `package-car` project contract.
- CV-06C2 retains CNCF runtime-range/ABI/archive-integrity activation and its
  packaged-CAR runtime round trip. At that validation point, the accumulated
  CV-06C1/C2 changes remained clean-re-review-pending, so their checklist items
  stayed open until the independent gate accepted the fixes.
- The independent CV-06C2 RE_REVIEW accepted the implementation, executable
  specifications, naming, focused test evidence, and diff hygiene, but found
  two documentation inconsistencies. The canonical design still named a
  removed validation-JAR input, and historical ArtScene CAR lint results were
  phrased as current-state claims. REVIEW_FIX now names the production
  main/library input JAR boundary consistently and marks historical lint
  results as execution-point evidence. Current normal ArtScene CAR lint exits
  successfully and accepts the compatibility contract; in addition to the
  missing ABI baseline, it reports pre-existing nominal String wrapper warnings
  from untouched CML under the current lint rules. Those application-model
  warnings are outside the CV-06C2 contract change and remain visible rather
  than being misreported or suppressed.
- The clean CV-06C2 RE_REVIEW found no actionable issue after the documentation
  correction. It revalidated the canonical main/library input-JAR contract,
  current normal ArtScene CAR lint, and diff hygiene in all five Phase 51
  repositories. CV-06C1/C2 and the three remaining CV-06 completion items are
  accepted, so CV-06 is CLOSED.

## CV-07: Development and Release Acceptance

Stage Status:
- Current status: IN_PROGRESS (REVIEW_FIX complete; RE_REVIEW pending)
- Owner: CNCF, Cozy, and release maintainers
- Entry rule: CV-06 is DONE.
- Completion rule: Development and release builds apply explicit, reproducible
  policies and cannot silently cross their coordinate boundaries.

- [x] Define admitted CNCF-SNAPSHOT and Cozy-SNAPSHOT pairing for local
  development.
- [x] Require explicit opt-in and diagnostic output for mutable development
  coordinates.
- [x] Require released CNCF generation to use an immutable released Cozy
  coordinate.
- [x] Reject release output built with an unbounded, unresolved, or mutable
  generator coordinate.
- [x] Verify compatibility evidence is published before a new pair is selected
  by default.
- [x] Verify cache and local-publication behavior cannot disguise a coordinate
  mismatch.
- [x] Add release/SNAPSHOT boundary, stale cache, and absent artifact
  specifications.
- [x] Document the manual recovery path for an unavailable compatible
  generator.

Evidence:
- The implementation specification is
  [Phase 51 CV-07 Development and Release Acceptance](../notes/phase-51-cv07-development-release-acceptance.md).
- PLAN keeps existing exact version authorities. An explicit mutable pair is
  the development opt-in and cannot be selected as a published default. The
  owning output version distinguishes development from release; release output
  requires a proven immutable pair, exact executing-generator identity, and
  valid provenance for generated CML.
- The implementation scope is Cozy compatibility/package/review/publication,
  sbt-cozy delegate/state wiring, the CNCF generation command boundary, and the
  ArtScene representative CAR. Full suites remain reserved for the final Phase
  51 release gate.
- Cozy REVIEW_FIX focused validation passed 26 scenarios across prebuilt CAR
  publication, runtime-manifest admission, and generation provenance. A
  package-built prebuilt CAR must preserve exact identity, runtime range,
  archive path-set and SHA-256 evidence; a generated release additionally
  requires its packaged provenance bytes to equal an immutable snapshot
  revalidated against the current owning CML/generated Scala output for the
  accepted CNCF/Cozy pair. The admitted CAR bytes are also snapshotted through
  the repository copy. Arbitrary, stale, or replaced prebuilt bytes are
  rejected before repository writes.
- sbt-cozy REVIEW_FIX focused validation passed 28 scenarios. CAR/SAR
  generation now requires project-owned `build.cozyVersion` and exactly one
  CNCF compile dependency declaration, including rejection of duplicate aliases
  for the same version. Exact CAR archives and their component-local Maven
  dependencies can be resolved for tests without loading their source
  projects, and unavailable delegated generators report their exact coordinate
  and recovery action.
- CNCF build/provenance validation passed 8 scenarios. The production command
  carries the exact expected Cozy generator and deterministic launch-failure
  recovery.
- CNCF development assembly preflight now reads the active CAR's
  `assembly-descriptor.yaml` before generated API validation. ArtScene's
  `textus-scraper@0.1.1` dependency is routed to the search repository before
  `TextusScraperApi` inspection rather than being discovered only after
  component initialization. Focused validation passed 22 scenarios, the
  runtime-admission fixture repair passed 81 scenarios, and the CNCF full suite
  passed 2,594 tests with zero failures.
- ArtScene's representative focused validation passed 8 scenarios with zero
  failures or cancellations. The build resolves the immutable
  `textus-scraper@0.1.1` CAR, extracts its runtime JARs, resolves its
  component-local jsoup dependency for tests, and retains only its component
  API JAR on the production classpath; it no longer loads or publishes the
  scraper source project. Its normal build explicitly selects the current
  `sbt-cozy 0.1.16-SNAPSHOT` development plugin without requiring an ambient
  override. The stale source-managed component descriptor is removed, so
  `project.yaml` remains the single version authority.
- The Phase 51 modified-Scala compliance ledger now contains 43 files:
  9 CNCF, 25 Cozy, 6 sbt-cozy, 1 simple-modeler, and 2 ArtScene files. The
  REVIEW_FIX additions use the repository naming rules and executable
  Given/When/Then specification style; final whole-file compliance and full
  suites remain gates of RE_REVIEW and PHASE_RELEASE_COMMIT.
- Normal ArtScene CAR lint exits successfully and reports
  `car.metadata.compatibility.accepted` for CNCF `0.5.1` and Cozy
  `0.3.1-SNAPSHOT`; the full `cncf-car-lint` pass also exits successfully with
  pre-existing ABI-baseline and nominal-string-wrapper warnings plus the
  deliberate `sbt-cozy 0.1.16-SNAPSHOT` development-coordinate warning.
- CV-07 remains open until the clean independent RE_REVIEW, final full-test,
  and release-commit gates complete.

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
