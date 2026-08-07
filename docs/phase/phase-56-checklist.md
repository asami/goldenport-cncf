# Phase 56 Checklist - Namespace-qualified Component Identity and Derived Coordinates

status=in-progress
started_at=2026-08-06
phase=[Phase 56 - Namespace-qualified Component Identity and Derived Coordinates](phase-56.md)
planning_journal=[Phase 56 Identity Planning and Phase Renumbering](../journal/2026/08/2026-08-06-phase-56-component-identity-planning-and-renumbering.md)
entry_handoff=[Current Work Closeout before Phase 56](../journal/2026/08/2026-08-06-current-work-closeout-before-phase-56.md)

This checklist is the authoritative Phase 56 state ledger after Phase 56
starts. Only one stage may be `IN_PROGRESS` at a time. No implementation stage
starts until the current work described by the entry handoff reaches its
bounded stopping point.

Entry evidence: the [closed entry handoff](../journal/2026/08/2026-08-06-current-work-closeout-before-phase-56.md)
records the accepted pre-documentation baseline accumulator diff SHA-256
`da02ef464cb73e745f02a3439975318793375b7dbcf7945ab75e9ad40173e6eb` at HEAD
`e9b00c9ba0f209e2969947c4e454ce769366d625`; this is not the eventual Step
commit hash.

## CID-01: Inventory and Executable Contract Freeze

Stage Status:
- Current status: DONE
- Owner: CNCF, Cozy/sbt-cozy, CAR, repository, launcher, CBD, and BoK maintainers
- Entry rule: Phase 55 and its admitted closeout work are closed.
- Completion rule: Existing authorities, projections, ambiguities, and exact
  failing-first acceptance identities are recorded before implementation.

- [x] Inventory `ComponentId`, `ComponentInstanceId`, `Component.Core.name`,
  descriptor models/codecs, project schemas, generators, and runtime loaders.
- [x] Inventory SBT organization/name/version, Maven group/artifact/version,
  CAR filenames, repository/index/cache keys, and dependency declarations.
- [x] Inventory JVM packages, generated class names, CML Component names,
  display names, titles, paths, Help/Admin identities, and diagnostics.
- [x] Inventory every accepted bare, kebab-case, `textus-`-prefixed, and
  qualified spelling and identify whether it is canonical or compatibility.
- [x] Inventory every admitted CAR repository and freeze its effective
  version, SNAPSHOT/release status, identity shape, derived coordinates, and
  migration owner.
- [x] Freeze the mandatory Phase 56 migration cohort to CARs whose effective
  version is SNAPSHOT and record every non-SNAPSHOT legacy CAR in a separate
  next-version deferral ledger.
- [x] Freeze `namespace`, local `id`, qualified ID, release version, and
  presentation metadata boundaries.
- [x] Freeze word splitting, acronym/digit, package, artifact, filename, and
  path projection algorithms and collision behavior.
- [x] Register failing-first cross-repository acceptance for the canonical
  User Account example and same-local-ID/different-namespace isolation.

CID-01 closure evidence (2026-08-07):

- [Inventory and failing-first contract](../notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md)
  and [CAR migration ledger](../notes/phase-56-cid01-car-migration-ledger.yaml) are
  the exact implementation-freeze artifacts. The inventory note is the
  authoritative Phase-56 working specification until CID-08 normative
  promotion; it is not the final normative design, and executable leaves remain
  target pending.
- The ledger contains exactly 18 eligible CAR repositories: 14
  `SNAPSHOT` records in the mandatory migration cohort and four current-release
  deferrals (`textus-corpus` 0.1.0, `textus-experiment` 0.1.0,
  `textus-georesolver` 0.2.1, and `textus-sanpomap` 0.2.1). The machine-precise
  release deferral rule is: `effective_version == current_release (exact release equality) => deferred; semantically comparable and effective_version > current_release while legacy identity remains => migration-required; effective_version < current_release OR versions are uncomparable OR effective_version is malformed => separate version/inventory error, never deferred or migration-required.`
- The E1-E10 registry paths are frozen as follows:

  | Identity | Stable rules | Owning executable/specification or production path |
  | --- | --- | --- |
  | CNCF E1 | `CID01-R1,R2` | `src/test/scala/org/goldenport/cncf/component/Phase56ComponentIdentityContractSpec.scala`; `src/main/scala/org/goldenport/cncf/component/Component.scala` |
  | CNCF E2 | `CID01-R1,R3` | `src/test/scala/org/goldenport/cncf/component/Phase56ComponentIdentityContractSpec.scala`; `src/main/scala/org/goldenport/cncf/component/Component.scala` |
  | CNCF E3 | `CID01-R4,R5` | `src/test/scala/org/goldenport/cncf/component/Phase56ComponentIdentityContractSpec.scala`; `src/main/scala/org/goldenport/cncf/component/Component.scala` |
  | CNCF E4 | `CID01-R6,R7` | `src/test/scala/org/goldenport/cncf/component/Phase56ComponentIdentityContractSpec.scala`; `src/main/scala/org/goldenport/cncf/subsystem/SubsystemAssemblyAdmission.scala`, `GenericSubsystemDescriptor.scala`, `GenericSubsystemFactory.scala`, and `Subsystem.scala` |
  | Cozy E5 | `CID01-R1,R2` | `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/scaffold/CozyScaffold.scala`, `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/archive/CozyArchivePackager.scala`, and `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/CozyCarPublisher.scala` |
  | Cozy E6 | `CID01-R3,R4` | `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/scaffold/CozyScaffold.scala`, `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/lint/CozyCarLint.scala`, and scenario-only `NotImplemented` `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/ProjectIdentityContractScenarioSpi.scala` |
  | Cozy E7 | `CID01-R5,R6` | `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/lint/CozyCarLint.scala`, `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/lint/CozyRepositoryLint.scala`, and `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/lint/CozyBuildLint.scala` |
  | sbt-cozy E8 | `CID01-R1,R2,R3` | `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala` and scenario-only `NotImplemented` `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala` |
  | sbt-cozy E9 | `CID01-R4,R5,R6` | `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarDependencyResolver.scala`, `CozyPlugin.scala` repository destinations, and scenario-only `NotImplemented` `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala` |
  | sbt-cozy E10 | `CID01-R7,R8,R9` | `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`, bridge/manifest generation, and scenario-only `NotImplemented` `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala` |
- E1-E4 are documentary target contracts: each Given names the exact note path,
  applicable domain rules, and example; each executable leaf has one terminal
  `pendingUntilFixed` block and does not assert contradictory current behavior.
  Cozy and sbt-cozy SPI paths are scenario-only deferred boundaries, not
  identity algorithms or API behavior. Pending behavior belongs to CID-02
  through CID-07; no pending behavior is claimed as implemented here.
- Evidence is read-only from the authoritative current worktrees: CNCF HEAD
  `5841a3f55a2676c88a5503fa7944110b02d2472b` was clean at plan freeze, and
  each CAR record carries full HEAD, clean/dirty boolean, project.yaml
  SHA-256, project.yaml dirty boolean, effective-version source, and explicit
  derived-value divergence. No CAR was mutated.

CID-01 completion evidence: the nine CID-01 boxes above are checked; CID-01
does not claim implementation, migration, or Phase 56 completion.

## CID-02: Typed Identity and Derivation Core

Stage Status:
- Current status: DONE
- Entry rule: CID-01 is complete.
- Completion rule: One validated typed identity produces every naming
  projection through one tested implementation.

- [x] Implement or admit `ComponentNamespace` and `ComponentLocalId`.
- [x] Make `ComponentId` namespace-qualified and make instance identity carry
  that exact Component ID.
- [x] Implement one deterministic projection API for qualified name, artifact,
  Maven group/artifact, JVM package, generated class, and path segments.
- [x] Test validation, normalization rejection, acronym/digit boundaries,
  namespace-leaf collisions, and round trips.
- [x] Prevent display metadata and version from entering identity equality.

CID-02 Slice ledger:

| Slice | Scope | Status |
| --- | --- | --- |
| CID-02A | Shared Scala-version-neutral Java identity/projection core in `cncf-collaborator-api`. | ACCEPTED/REVIEWED |
| CID-02B | CNCF runtime semantics and adapter adoption of the shared ABI. | ACCEPTED/REVIEWED |
| CID-02C | Cozy and sbt-cozy package-private Java-ABI adapters consume shared validation, projection, and collision behavior; project schema/generation remains CID-03, coordinate/repository/publication wiring CID-04, and lint CID-07. | ACCEPTED/REVIEWED |

CID-02 Step feature-test evidence:

All nine serialized invocations completed in dependency order with
`lock=released`:

1. `cncf-collaborator-api` `ComponentIdentityTest`, invocation
   `42569-20260806T195014Z`: 18 passed, 0 pending, 0 failed; PASS;
   `lock=released`.
2. `cncf-collaborator-api` `Test/compile`, invocation
   `42743-20260806T195026Z`: PASS; `lock=released`.
3. `cncf-collaborator-api` `publishLocal`, invocation
   `42897-20260806T195037Z`: PASS; this published the Java-17
   `0.2.0-SNAPSHOT` producer before consumer tests; `lock=released`.
4. CNCF `Phase56ComponentIdentityContractSpec`, invocation
   `43047-20260806T195048Z`: 2 passed, 2 deferred pending, 0 failed; PASS;
   `lock=released`.
5. CNCF `Test/compile`, invocation `43563-20260806T195154Z`: PASS;
   `lock=released`.
6. Cozy `Phase56ProjectIdentityContractSpec`, invocation
   `43778-20260806T195211Z`: 4 passed, 3 deferred pending, 0 failed; PASS;
   `lock=released`.
7. Cozy `Test/compile`, invocation `44023-20260806T195229Z`: PASS;
   `lock=released`.
8. sbt-cozy `Phase56CarCoordinateContractSpec`, invocation
   `44193-20260806T195240Z`: 6 passed, 3 deferred pending, 0 failed; PASS;
   `lock=released`.
9. sbt-cozy `Test/compile`, invocation `44354-20260806T195251Z`: PASS;
   `lock=released`.

CID-02 review-fix revalidation evidence:

- CNCF focused revalidation invocation `54471-20260806T202132Z` exposed one
  already-passing E4 namespace-isolation property still wrapped in
  `pendingUntilFixed`: 6 passed, 2 deferred pending, 1 failed; `sbt_exit=1`,
  `wrapper_exit=1`, `lock=released`. The repair promoted that property to an
  active assertion without changing production behavior.
- CNCF `Phase56ComponentIdentityContractSpec`, superseding invocation
  `55362-20260806T202347Z`: 7 passed, 2 deferred pending, 0 failed; PASS;
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
- CNCF `Test/compile`, invocation `55620-20260806T202410Z`: PASS;
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
- sbt-cozy `Phase56CarCoordinateContractSpec`, invocation
  `55833-20260806T202427Z`: 6 passed, 3 deferred pending, 0 failed; PASS;
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
- sbt-cozy `Test/compile`, invocation `56001-20260806T202440Z`: PASS;
  `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.

Repository-wide full suites and scripted validation remain Phase-release-only.

## CID-03: Cozy Project Schema and Generation

Stage Status:
- Current status: DONE
- Entry rule: CID-02 is complete.
- Completion rule: A CAR project authors identity once and generation produces
  consistent source, build, and descriptor outputs.

CID-03 Slice ledger:

| Slice | Scope | Status |
| --- | --- | --- |
| CID-03A | Canonical Cozy project identity schema/admission. | ACCEPTED/REVIEWED |
| CID-03B | Cozy scaffold and generated Scala/package projection. | ACCEPTED/REVIEWED |
| CID-03C | sbt-cozy canonical build/descriptor metadata, compatibility disagreement rejection, and upgrade/lint evidence exposure. | ACCEPTED/REVIEWED |

- [x] Add canonical `project.namespace` and `project.id` schema fields.
- [x] Remove or deprecate independently authored component name, class name,
  Scala package, artifact name, and organization fields.
- [x] Derive SBT/Maven metadata, JVM package, generated API class, descriptor,
  and CAR filename from the canonical identity plus version.
- [x] Reject explicitly supplied derived values that disagree during the
  compatibility window.
- [x] Add scaffold, regeneration, and upgrade tests in Cozy and sbt-cozy.
- [x] Expose enough canonical/legacy and effective-version evidence for CAR
  lint to classify migration status without guessing identity.

CID-03 Step validation and feature-test evidence:

1. Cozy `Phase56ProjectIdentityContractSpec`, invocation
   `15681-20260806T225917Z`: 12 passed, 1 deferred pending, 0 failed; PASS;
   `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
2. Cozy `ModelerScaffoldSpec`, invocation `15874-20260806T225932Z`:
   14 passed, 0 failed; PASS; `sbt_exit=0`, `wrapper_exit=0`,
   `lock=released`.
3. Cozy `Test/compile`, invocation `16093-20260806T225944Z`: PASS;
   `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
4. sbt-cozy `Phase56CarCoordinateContractSpec`, invocation
   `21614-20260806T231120Z`: 9 passed, 1 deferred pending, 0 failed; PASS;
   `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
5. sbt-cozy `Test/compile`, invocation `21804-20260806T231133Z`: PASS;
   `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
6. sbt-cozy scripted `cozy/project-yaml-canonical-identity`, invocation
   `23773-20260806T231616Z`: 1 of 1 case passed; PASS; `sbt_exit=0`,
   `wrapper_exit=0`, `lock=released`.

The remaining Cozy E7 lint assertion belongs to CID-07, and the remaining
sbt-cozy E9 repository/publication assertion belongs to CID-04. They remain
the sole deferred pending properties in their respective Phase 56 focused
specifications and do not weaken CID-03 closure.

## CID-04: CAR, Maven, and Repository Coordinates

Stage Status:
- Current status: IN_PROGRESS
- Entry rule: CID-03 is complete.
- Completion rule: Publication and resolution retain namespace-qualified
  identity and verify every materialized projection.

[CID-04 implementation plan](../notes/phase-56-cid04-car-maven-repository-coordinate-plan.md)
freezes the canonical release-coordinate shape, repository boundary,
serialized schemas, Slice ownership, and executable acceptance matrix.

CID-04 Slice ledger:

| Slice | Scope | Status |
| --- | --- | --- |
| CID-04A | Shared release-coordinate and repository/cache/catalog projection ABI. | COMPLETED |
| CID-04B | Cozy canonical component descriptor, API/ABI descriptor, and dependency codecs. | COMPLETED |
| CID-04C | Cozy namespace-qualified publisher, catalog, index, Maven metadata, and integrity records. | ACCEPTED/REVIEWED |
| CID-04D | sbt-cozy canonical dependency declarations, local/HTTP/cache resolver, publication wiring, and E9 activation. | ACCEPTED/REVIEWED |
| CID-04E | CNCF v2 repository index and namespace-qualified standard repository/cache consumer. | IMPLEMENTATION/REVIEW_PENDING |

[CID-04B implementation plan](../notes/phase-56-cid04b-cozy-canonical-descriptor-codec-plan.md)
freezes the exact canonical JSON shapes, four-field dependency bridge,
diagnostics, source ownership, and focused executable specification.

[CID-04C implementation plan](../notes/phase-56-cid04c-cozy-repository-publication-plan.md)
freezes CAR catalog/index v2, namespace-qualified warehouse and sidecar paths,
Maven metadata, integrity validation, publication locking, and focused tests.

[CID-04D implementation plan](../notes/phase-56-cid04d-sbt-cozy-repository-resolution-plan.md)
freezes canonical and legacy `CarDependency` construction, shared local/file/
HTTP/cache paths, namespace-isolated runtime extraction, the four-field Cozy
bridge, canonical CAR publication task wiring, SAR preservation, focused
loopback evidence, and sole E9 activation.

[CID-04E implementation plan](../notes/phase-56-cid04e-cncf-repository-resolution-plan.md)
freezes the CNCF v2 index reader, direct canonical CAR repository/cache
resolver, namespace-isolated local/remote/offline behavior, and focused
executable specification.

CID-04C focused acceptance and review evidence:

- Cozy exact changed-spec validation invocation `99017-20260807T061825Z`:
  17 suites and 224 tests passed; `sbt_exit=0`, `wrapper_exit=0`,
  `lock=released`.
- Catalog/index residual validation invocation `98681-20260807T061738Z`:
  29 tests passed; `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.
- Parallel publisher/fault-seam isolation invocation
  `89318-20260807T054751Z`: 27 tests passed; `sbt_exit=0`,
  `wrapper_exit=0`, `lock=released`.
- Independent review and focused re-review converged for canonical descriptor
  and ABI admission, namespace-isolated warehouse/catalog/index/Maven
  projections, exact checksum and integrity evidence, prepared-candidate
  validation, index-last atomic replacement, complete rollback and debris
  cleanup, strict catalog/index parsing, SAR-v1 preservation, naming, and
  executable-spec structure. The retained history header in
  `CozyCarRuntimeManifestSpec` is an explicit user decision and follows the
  canonical date format.

CID-04D focused acceptance and review evidence:

- Validation invocation `39887-20260807T081517Z`: 6 suites, 53/53;
  `sbt=0`, `wrapper=0`, `lock=released`.
- Focused re-review was clean.

CID-04E acceptance remains required before the CID-04 Step scripted acceptance.
The Step fixture
`sbt-cozy/src/sbt-test/cozy/namespace-qualified-car-repository`, the Step
commit, and repository-wide suites therefore remain pending; repository-wide
suites remain Phase-release-only.

- [x] Replace canonical descriptor `name`/`component` inputs with `namespace`
  and `id`; retain version as release metadata.
- [ ] Derive and verify artifact name, CAR filename, Maven coordinate, and
  repository path/index metadata.
- [ ] Include namespace in repository, cache, dependency, and integrity keys
  even when filenames collide.
- [ ] Update dependency declaration codecs and error diagnostics.
- [ ] Test publish, retrieve, cache, offline, and transitive dependency paths.

## CID-05: CNCF Runtime Identity Migration

Stage Status:
- Current status: PLANNED
- Entry rule: CID-04 is complete.
- Completion rule: Runtime loading and all internal consumers use the exact
  qualified `ComponentId`.

- [ ] Align CML declaration, generated Component core, descriptor admission,
  and runtime `ComponentId` without heuristic normalization.
- [ ] Migrate instance IDs, dependency lookup, class loading, configuration
  targets, routing, Help/Admin metadata, logs, and diagnostics.
- [ ] Remove internal artifact-name-as-Component-ID use.
- [ ] Test two namespaces with one local ID through loading and routing.
- [ ] Preserve presentation-only display names and titles.

## CID-06: Compatibility Adapters

Stage Status:
- Current status: PLANNED
- Entry rule: CID-05 is complete.
- Completion rule: Old inputs remain bounded decode aliases and all internal
  state is canonical.

- [ ] Decode legacy descriptor `name`/`component` shapes through an explicit
  compatibility adapter.
- [ ] Admit known bare, artifact, prefixed, and legacy Web-path spellings only
  where a unique canonical identity is available.
- [ ] Reject ambiguity and disagreement with structured diagnostics.
- [ ] Emit only the new descriptor/project identity shape.
- [ ] Record warning, observability, and removal policy for every alias.
- [ ] Preserve non-SNAPSHOT legacy CAR releases without rewriting or
  republishing them solely for identity migration.

## CID-07: CAR Lint and Development CAR Migration

Stage Status:
- Current status: PLANNED
- Entry rule: CID-06 is complete.
- Completion rule: CAR lint enforces the version-sensitive migration policy,
  every CAR in the frozen SNAPSHOT cohort uses canonical authoring, and every
  non-SNAPSHOT legacy CAR has a next-version migration entry.

- [ ] Extend CAR lint to pass canonical identity with consistent projections.
- [ ] Make legacy identity on a SNAPSHOT CAR a migration-required lint error.
- [ ] Detect legacy identity on a non-SNAPSHOT CAR as a
  deferred-to-next-version warning without invalidating the existing release.
- [ ] Prove advancing a deferred CAR beyond its recorded release version,
  whether to a SNAPSHOT or directly to another release, promotes that finding
  to a migration-required lint error.
- [ ] Make canonical/derived disagreement a lint error regardless of version.
- [ ] Include effective version, identity shape, expected projections,
  migration status, owner, and actionable path in lint diagnostics.
- [ ] Migrate Textus User Account to
  `org.simplemodeling.textus + UserAccount`.
- [ ] Verify `org.simplemodeling.textus.UserAccount`,
  `textus-user-account`, `UserAccountComponent`, and
  `org.simplemodeling.textus.useraccount` are projections, not copied inputs.
- [ ] Migrate representative first-party CARs and one same-local-ID fixture.
- [ ] Migrate every admitted CAR whose frozen effective version is SNAPSHOT;
  do not limit Phase 56 adoption to representative fixtures.
- [ ] Leave non-SNAPSHOT CAR releases unchanged and record their exact next
  development version migration owner and entry condition.

## CID-08: Ecosystem Regression and Normative Closure

Stage Status:
- Current status: PLANNED
- Entry rule: CID-07 is complete.
- Completion rule: Cross-repository evidence, CAR lint results, and normative
  documentation show exactly one identity authority and a complete migration
  ledger.

- [ ] Update launchers, samples, CBD Support, BoK, repository metadata, and
  downstream dependency consumers.
- [ ] Verify existing admitted Web routes through compatibility aliases.
- [ ] Run focused and full validation for every modified repository under the
  required serialized SBT execution policy.
- [ ] Run CAR generation, packaging, publication-local, repository-resolution,
  launcher, and representative runtime acceptance.
- [ ] Perform independent review of collisions, compatibility, routing,
  package generation, and coordinate integrity.
- [ ] Promote verified behavior to CNCF/Cozy design and specification.
- [ ] Publish migration guidance for CAR authors and downstream consumers.
- [ ] Record exact commits, commands, results, remaining aliases, and removal
  owners before closing Phase 56.
- [ ] Record a lint-clean result for every frozen SNAPSHOT CAR and a complete
  next-version deferral report for every non-SNAPSHOT legacy CAR.
- [ ] Update Phase 57 entry contracts to consume the qualified Component
  identity without reopening Phase 56 naming decisions.
