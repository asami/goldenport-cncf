# Phase 56 Checklist - Namespace-qualified Component Identity and Derived Coordinates

status=in-progress
started_at=2026-08-06
phase=[Phase 56 - Namespace-qualified Component Identity and Derived Coordinates](phase-56.md)
planning_journal=[Phase 56 Identity Planning and Phase Renumbering](../journal/2026/08/2026-08-06-phase-56-component-identity-planning-and-renumbering.md)
entry_handoff=[Current Work Closeout before Phase 56](../journal/2026/08/2026-08-06-current-work-closeout-before-phase-56.md)
hygiene_journal=[Phase 56 hygiene follow-up](../journal/2026/08/2026-08-07-phase-56-hygiene-follow-up.md)

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
  | Cozy E7 | `CID07-R1` | `/Users/asami/src/dev2026/cncf-collaborator-api/src/main/java/org/goldenport/cncf/component/identity/ComponentIdentityMigrationClassifier.java`, `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/lint/CozyCarIdentityLint.scala`, `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/lint/CozyCarLint.scala`, and `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/Phase56ProjectIdentityContractSpec.scala` |
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
- Current status: DONE
- Entry rule: CID-03 is complete.
- Completion rule: Publication and resolution retain namespace-qualified
  identity and verify every materialized projection.

[CID-04 implementation plan](../notes/phase-56-cid04-car-maven-repository-coordinate-plan.md)
freezes the canonical release-coordinate shape, repository boundary,
serialized schemas, Slice ownership, and executable acceptance matrix.

CID-04 Slice ledger:

| Slice | Scope | Status |
| --- | --- | --- |
| CID-04A | Shared release-coordinate and repository/cache/catalog projection ABI. | ACCEPTED/REVIEWED |
| CID-04B | Cozy canonical component descriptor, API/ABI descriptor, and dependency codecs. | ACCEPTED/REVIEWED |
| CID-04C | Cozy namespace-qualified publisher, catalog, index, Maven metadata, and integrity records. | ACCEPTED/REVIEWED |
| CID-04D | sbt-cozy canonical dependency declarations, local/HTTP/cache resolver, publication wiring, and E9 activation. | ACCEPTED/REVIEWED |
| CID-04E | CNCF v2 repository index and namespace-qualified standard repository/cache consumer. | ACCEPTED/REVIEWED |
| CID-04F | Acceptance-ledger reconciliation and hygiene-journal persistence for CID-04 closure preparation. | ACCEPTED/REVIEWED |

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

CID-04E final focused validation invocation `70737-20260807T094615Z` passed
3 suites and 18 tests with 0 failures and 4 expected ownership cancellations;
`sbt_exit=0`, `wrapper_exit=0`, `lock=released`. Focused re-review was PASS
with no findings.

The dependency-ordered CID-04 Step validation and commits are accepted:

- `cncf-collaborator-api` test invocation `18570-20260807T115729Z` (22 tests),
  `publishLocal` invocation `18799-20260807T115752Z`, commit
  `6493d29920c6db3d0ed2b810485901ec3192a2b1`.
- Cozy invocation `19044-20260807T115822Z` (17 suites/224 tests),
  `publishLocal` invocation `19473-20260807T115922Z`, commit
  `2d65321c5d9362cb7493d90aa7ebf34d03dbee90`.
- CNCF invocation `19701-20260807T115947Z` (3 suites/18 tests plus 4 expected
  ownership cancellations), `publishLocal` invocation
  `19997-20260807T120021Z`, commit
  `9371ab8c0349b9ad28b46c56982cd4c83e938912`.
- sbt-cozy invocation `20315-20260807T120059Z` (6 suites/53 tests), commit
  `a84a91514c8b843da8524d06d61e33dc480ebf38`.

Every invocation had `sbt_exit=0`, `wrapper_exit=0`, `lock=released`. Step
scripted invocation `20534-20260807T120122Z` passed 1/1 with PublisherProbe
plus CncfProbe online/offline. Repository-wide full suites remain
Phase-release-only/pending; this evidence does not close Phase 56.

- [x] Replace canonical descriptor `name`/`component` inputs with `namespace`
  and `id`; retain version as release metadata.
- [x] Derive and verify artifact name, CAR filename, Maven coordinate, and
  repository path/index metadata.
- [x] Include namespace in repository, cache, dependency, and integrity keys
  even when filenames collide.
- [x] Update dependency declaration codecs and error diagnostics.
- [x] Test publish, retrieve, cache, offline, and transitive dependency paths.
- [x] Record the clean CID-04F review, set CID-04 to DONE, and persist the
  hygiene journal.

## CID-05: CNCF Runtime Identity Migration

Stage Status:
- Current status: DONE / COMMITTED
- Entry rule: CID-04 is complete.
- Completion rule: Runtime loading and all internal consumers use the exact
  qualified `ComponentId`.

Slice ledger:

| Slice | Scope | Status |
| --- | --- | --- |
| CID-05A | Runtime Core Identity Admission | ACCEPTED/REVIEWED (44742-20260807T131308Z; 1 suite, 12 executable leaves, 24 matrix ops, 1 intentional pending, exits 0, lock released) |
| CID-05B | Descriptor/factory/loading/dependency/configuration identity | ACCEPTED/REVIEWED; final review-fix validation `81149-20260807T150153Z`: 106 passed, 1 intentional pending, 9 suite executions completed, exits 0, lock released. Independent focused re-review closed CID05B-R1 through R7 with PASS on reviewed tracked diff `4d0f835fab40b5b4e0dfb6c69370e4ca17659f848b32602f3c848abcb5e1b459`. |
| CID-05C | ComponentSpace/routing/Help/Admin/diagnostics identity | ACCEPTED/REVIEWED; initial findings `CID05C-R1` through `CID05C-R9` and second-pass findings `RF-CID05C-010`/`RF-CID05C-011` are repaired. Authoritative invocation `11800-20260707T210921Z` passed 234 tests across 12 suites with one intentional pending leaf, exits 0, and lock released. Independent focused re-review returned PASS on reviewed tracked diff `b5c00333fa8d91d09a03473f8cbcff741dbb5f23877039db034cc4715ac6fd93`; validation repairs `VF-CID05C-012` through `VF-CID05C-015` are closed. |
| CID-05D | Namespace-isolated runtime integration | ACCEPTED/REVIEWED. RF-CID05D-001..013 are closed, preserving exact schema/binding identity, namespace-isolated Web/manual roots, subsystem-owned CAR loader lifecycle, current header history, method-local helper naming, and repository-local temporary-directory lifecycle. Independent focused re-review returned PASS with no actionable finding on reviewed scoped diff `64fd912685db918b9c92e5b02e556b81b42b39cd83b2450aeb31ec86255b46ec`; `FULL_REVIEW_REQUIRED=no`. |

- [x] Align CML declaration, generated Component core, descriptor admission,
  and runtime `ComponentId` without heuristic normalization.
- [x] Migrate instance IDs, dependency lookup, class loading, configuration
  targets, routing, Help/Admin metadata, logs, and diagnostics.
- [x] Remove internal artifact-name-as-Component-ID use.
- [x] Test two namespaces with one local ID through loading and routing.
- [x] Preserve presentation-only display names and titles.

CID-05D implementation record (2026-08-08): exact qualified declarations are
promoted only by `ComponentId.parseC` before admission discovery/closure/
evaluation and canonical instance validation. Two release `0.6.0` CARs retain
their own Core/default instance/artifact ID/repository origin while both display
`Shared`; qualified Request routing returns distinct scalars, and bare `Shared`
is non-selecting with ambiguity evidence naming both qualified candidates.
The plain-`Action` implicit-job defect exposed by this path is handed to
[Phase 57 - Action Execution Semantics](phase-57.md) and does not extend
CID-05. CID-06, CID-01 E4, Phase full validation, and HYG-P56-005 remain
incomplete or unchanged as applicable.

Implementation-focused validation passed with invocation `33307-20260807T220812Z`:
4 suites, 55 succeeded, 1 intentional pending leaf, exits 0, lock released.
The exact parent-owned command was:

```text
testOnly org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec
```

Review-fix validation is complete for `RF-CID05D-001` through
`RF-CID05D-010`. Validation repairs `VF-CID05D-001` (replaced the illegal
anonymous sealed `Specification` with existing concrete development-repository
specifications) and `VF-CID05D-002` (corrected lifecycle observation to the
Subsystem-owned loader snapshot without changing lifecycle semantics) are
complete. Authoritative final focused validation is invocation
`59976-20260807T231428Z`, with exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.ComponentDescriptorSpec org.goldenport.cncf.component.repository.ComponentRepositoryCarSpec org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec org.goldenport.cncf.subsystem.resolver.OperationResolverSpec org.goldenport.cncf.projection.GeneratedHelpProjectionSpec org.goldenport.cncf.subsystem.GenericSubsystemDescriptorSpec org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec"]
```

Eleven suites completed, zero aborted; 243 tests succeeded, zero
failed/canceled/ignored, and one intentional pending remained;
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Smallest lifecycle
correction evidence is invocation `59556-20260807T231330Z`: 1/1 passed,
exits 0, and lock released. RF-CID05D-011..013 were applied in this pass:
ComponentDependency retains the Jul. 30 history while carrying the current
Aug. 8 header, ComponentDescriptor uses the required method-local helper
form, and RuntimeComponentDevelopmentWebProjectionSpec scopes all temporary
directories under its deterministic target work root with finally-based
cleanup. The authoritative focused validation for these repairs is invocation
`75311-20260808T000025Z`, using the exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec"]
```

The final wrapper was
`/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh --batch 'testOnly org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec'`;
one suite completed with 10 tests succeeded and zero failed, canceled,
ignored, or pending; `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.
The parent also verified that
`target/cncf-test/work/runtime-component-development-web-projection-spec`
is absent after the suite and that the owned diff-check is clean. Independent
focused re-review closed `RF-CID05D-011` through `RF-CID05D-013` with PASS,
no new finding, and no full-review escalation on reviewed scoped diff
`64fd912685db918b9c92e5b02e556b81b42b39cd83b2450aeb31ec86255b46ec`.
CID-05D is accepted/reviewed and the CID-05 completion boxes are closed. The
Step commit is `d5d3c5bb71962d93898ac8b1ddbcac7d9c8cfe83` with message `Carry
qualified component identity through CNCF runtime`. Exact validation invocation
`86240-20260808T003231Z` completed four suites with 59 tests succeeded, zero
failed/canceled/ignored, one intentional pending, both exits zero, and the lock
released. Phase full validation remains pending, and HYG-P56-005 remains
separate.

## CID-06: Compatibility Adapters

Stage Status:
- Current status: IN_PROGRESS
- Entry rule: CID-05 is complete.
- Completion rule: Old inputs remain bounded decode aliases and all internal
  state is canonical.

CID-06 Slice ledger:

| Slice | Scope | Status |
| --- | --- | --- |
| CID-06A | Typed compatibility result and assembly-binding admission; exact qualified selection, unique bare adaptation, deterministic ambiguity/unsupported results, and active CID-01 E4. | ACCEPTED / REVIEWED |
| CID-06B | Legacy descriptor-field agreement and canonical in-memory projection. | ACCEPTED / REVIEWED |
| CID-06C | Runtime selector, Help/Meta, Web alias, and warning/Admin convergence. | ACCEPTED / REVIEWED. RF-CID06C-001 through RF-CID06C-012 are closed; invocation `52869-20260808T033203Z` passed all 11 suites and 119 tests without warnings. Independent focused re-review returned PASS on tracked diff `50ed3a22239bd8ed5dab2819957c421d72562abc1a0f683b4b409517a53252c6`; `FULL_REVIEW_REQUIRED=no`. CID-06E is also accepted/reviewed; the CID-06 Step commit, Phase full validation, CID-07, and notice-removal ownership remain pending. |
| CID-06D | Exact deferred-release registry and unchanged legacy CAR ClassLoader/factory compatibility. | ACCEPTED / REVIEWED; post-fix invocation `74557-20260808T044055Z` passed 5 suites/109 tests warning-free, and independent focused re-review closed RF-CID06D-001 through RF-CID06D-004 with PASS and `FULL_REVIEW_REQUIRED=no`. |
| CID-06E | End-to-end compatibility acceptance and Step convergence. | ACCEPTED / REVIEWED; [separate plan](../notes/phase-56-cid06e-compatibility-step-acceptance-plan.md) records RF-CID06E-001 exact-provenance repair; `85353-20260808T051139Z` passed 11 suites/179 tests warning-free; RF-CID06E-002 lifecycle contradictions are repaired and independent focused re-review returned PASS. |

CID-06A completed `AssemblyBinding`; CID-06B adds `DescriptorField` projection
without creating a second normalization authority. `REVIEW #1` admitted
`R1-F1` static dev-repository candidate discovery, `R1-F2` adapter visibility,
`R1-F3` direct spelling/metadata preservation evidence, `R1-F4` semantic
specification grouping, `R1-F5` adapter-plan lifecycle reporting, and `R1-F6`
phase/checklist lifecycle reporting. `REVIEW_FIX #1` is applied. The separate
[descriptor compatibility plan](../notes/phase-56-cid06b-component-descriptor-compatibility-plan.md)
keeps legacy projection expected-identity-bound, schema-3 strict, and unbound
decode untyped. `VF-CID06B-001` and `VF-CID06B-002` correct the two admission
brace placements. Focused evidence invocation `10489-20260808T013738Z`
completed four suites with 36 tests succeeded, zero failed/canceled/ignored/
pending/aborted, main and test compile succeeded, `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. `VF-CID06B-003` then repaired one
discarded-Assertion warning. Final warning-free focused evidence invocation
`13371-20260808T014534Z` used the exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.component.Phase56ComponentIdentityCompatibilitySpec org.goldenport.cncf.component.Phase56ComponentDescriptorCompatibilitySpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.SubsystemAssemblyAdmissionSpec"]
```

Four suites completed with zero aborted; 36 tests succeeded; zero failed,
canceled, ignored, or pending; compile was warning-free; `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. Independent focused re-review returned
PASS with no findings and `FULL_REVIEW_REQUIRED=no` on status SHA
`cd38b345a039fdf0d850b5b9b3db4167e4989eaf098219f2a540bdac1b5ddb16` and
tracked diff SHA
`5af442141553a258093364af59afe476c9f7c313c5872438096db0b9561a344f`; all ten
target hashes were exact. `R1-F1` through `R1-F6` and `VF-CID06B-001` through
`VF-CID06B-003` are closed. This CID-06B record is historical; current state
has CID-06C through CID-06E accepted/reviewed. The CID-06 Step feature-test,
commit, and Phase full validation remain pending. No CID-06 completion box is closed until review and Step
acceptance converge.

CID-06C has a separate
[runtime selector and compatibility observability plan](../notes/phase-56-cid06c-runtime-selector-observability-plan.md).
Its acceptance matrix covers typed notice retention, unique and ambiguous
runtime selectors, Help/Meta/Web convergence, warning deduplication, and the
existing Admin assembly warning/report projection. Status:
`ACCEPTED / REVIEWED`; RF-CID06C-001 through
RF-CID06C-012 and validation repairs are applied. The earlier post-fix
invocation `43771-20260808T030432Z` used the exact 11-suite CID-06C focused
`testOnly` accumulator: 11 suites completed, zero aborted; 119 tests succeeded;
zero failed, canceled, ignored, or pending; `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. The subsequent full re-review found no runtime defect and
admitted only bounded metadata/grouping, internal naming, and header repairs.
Initial validation `52300-20260808T033036Z` found exactly three stale test
references after the internal field rename. Corrective authoritative invocation
`52869-20260808T033203Z` then completed 11 suites with 119 tests succeeded,
zero failed/canceled/ignored/pending/aborted, no warnings, both exits zero, and
the lock released. Independent focused re-review returned PASS with no findings
on status SHA `33c605da019b1eddca1fae3697e720b65c5894adab5042318641ccab040ff5a6`
and tracked diff SHA
`50ed3a22239bd8ed5dab2819957c421d72562abc1a0f683b4b409517a53252c6`;
`FULL_REVIEW_REQUIRED=no`. Historical pre-review invocation `26232` is
superseded. CID-06E is accepted/reviewed; the CID-06 Step commit remains
pending.
The complete finding-to-boundary ledger is retained in the primary
[CID-06 compatibility adapter plan](../notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md).

CID-06D RF-CID06D-001 through RF-CID06D-004 are applied. Historical invocation
`66284` failed during compilation; `66671` compiled and reported three failures;
`67421` completed five suites with 109 tests passed, no warnings, `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. That evidence predates and is superseded by the
review fixes. Post-fix invocation `74557-20260808T044055Z` completed the same
five suites with 109 tests passed, no warnings, `sbt_exit=0`, `wrapper_exit=0`,
and `lock=released`. Independent focused re-review verified exact repaired
hashes and returned PASS without new findings. CID-06E is accepted/reviewed;
the CID-06 Step commit and Phase full validation remain pending.

CID-06E now has one E1 executable acceptance in
`Phase56ComponentIdentityCompatibilityAcceptanceSpec`. It composes the real
GenericSubsystemFactory, the only configured packed-CAR repository candidate,
raw/effective exact-release extraction, generated factory/Core, canonical
Component/default-instance/artifact/cache/resolver state, canonical and unique
legacy public routing, Help/Meta, owned AssemblyReport/Admin warnings, strict
negative selectors, and one-time loader shutdown. The Corpus fixture keeps the
intentional bare generated ID and adds one scalar operation. CID-06D and E use
the shared `LegacyDeferredReleaseCarFixture`. All work is hermetic below the
deterministic CID-06E target directory. Focused validation is complete:
`84975-20260808T051049Z` passed E1 and authoritative
`85353-20260808T051139Z` passed 11 suites/179 tests warning-free. Full review
found no production or specification defect and admitted only RF-CID06E-002
lifecycle contradictions. Those documentation repairs are applied, and
independent focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`.

CID-06E validation chain: `80957` aborted on invalid WorkAreaId; `81384`
exposed missing capabilities; `81844` exposed the provider owner; and `82252`
plus `82742` exposed deferred scope loss at GenericSubsystemFactory
rematerialization. RF-CID06E-001 carries the exact repository-admitted
deferred entry as package-internal, nonserialized Component provenance, scopes
only the rematerializing factory/Core call, and propagates the same provenance
to the recreated Component. It does not rederive from artifact/path/name,
change ArtifactMetadata, relax strict cases, or expand the registry. Smallest
corrective invocation `84975-20260808T051049Z` passed E1, and authoritative
invocation `85353-20260808T051139Z` passed 11 suites/179 tests warning-free
with both exits zero and the lock released. Full review found only
RF-CID06E-002 lifecycle contradictions. Their documentation repair is applied,
and independent focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`; CID-06E is accepted/reviewed.

The CID-06 Step full review admitted `CID06-FR-001` through `CID06-FR-003`:
failure-safe acceptance shutdown cleanup, complete semantic grouping/metadata
in `SubsystemAssemblyAdmissionSpec`, and synchronized CID-06C/D lifecycle
truth. Invocation `98250-20260808T055120Z` passed the two affected suites and
all 10 tests without warnings, with both exits zero and the lock released.
Independent focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`. The CID-06 Step feature-test/commit and Phase full
validation remain pending.

The final CID-06 Step accumulator `9227-20260808T062445Z` completed 19 suites
and 306 tests with no failures, pending cases, aborts, or warnings; both exits
were zero and the shared lock was released. Commit
`6afab962ccd33431e6e2944c8d9191295d1a7f38`
(`Preserve bounded component identity compatibility`) closes the CID-06 Step.
Phase full validation remains pending.

- [x] Decode legacy descriptor `name`/`component` shapes through an explicit
  compatibility adapter.
- [x] Admit known bare, artifact, prefixed, and legacy Web-path spellings only
  where a unique canonical identity is available.
- [x] Reject ambiguity and disagreement with structured diagnostics.
- [x] Emit only the new descriptor/project identity shape.
- [x] Record warning, observability, and removal policy for every alias.
- [x] Preserve non-SNAPSHOT legacy CAR releases without rewriting or
  republishing them solely for identity migration.

## CID-07: CAR Lint and Development CAR Migration

Stage Status:
- Current status: ACCEPTED / REVIEWED / STEP_COMMIT_PENDING
- Entry rule: CID-06 is complete.
- Completion rule: CAR lint enforces the version-sensitive migration policy,
  every CAR in the frozen SNAPSHOT cohort uses canonical authoring, and every
  non-SNAPSHOT legacy CAR has a next-version migration entry.

- [x] Extend CAR lint to pass canonical identity with consistent projections.
- [x] Make legacy identity on a SNAPSHOT CAR a migration-required lint error.
- [x] Detect legacy identity on a non-SNAPSHOT CAR as a
  deferred-to-next-version warning without invalidating the existing release.
- [x] Prove advancing a deferred CAR beyond its recorded release version,
  whether to a SNAPSHOT or directly to another release, promotes that finding
  to a migration-required lint error.
- [x] Make canonical/derived disagreement a lint error regardless of version.
- [x] Include effective version, identity shape, expected projections,
  migration status, owner, and actionable path in lint diagnostics.
- [x] Migrate Textus User Account to
  `org.simplemodeling.textus + UserAccount`.
- [x] Verify `org.simplemodeling.textus.UserAccount`,
  `textus-user-account`, `UserAccountComponent`, and
  `org.simplemodeling.textus.useraccount` are projections, not copied inputs.
- [x] Migrate representative first-party CARs and one same-local-ID fixture.
- [x] Migrate every admitted CAR whose frozen effective version is SNAPSHOT;
  do not limit Phase 56 adoption to representative fixtures.
- [x] Leave non-SNAPSHOT CAR releases unchanged and record their exact next
  development version migration owner and entry condition.

Migration inventory acceptance (CID-07E):

- [x] `textus-user-account` (CID-07B) is canonical and final lint has no FAIL.
- [x] `textus-ai` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-art-scene` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-bok` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-control-center` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-scraper` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-supervisor` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-toolchain-runner` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-user-notification` (CID-07C) is canonical and final lint has no FAIL.
- [x] `textus-aws` (CID-07D) is canonical and final lint has no FAIL.
- [x] `textus-blog` (CID-07D) is canonical and final lint has no FAIL.
- [x] `textus-cbd-support` (CID-07D) is canonical and final lint has no FAIL.
- [x] `textus-knowledge-editor` (CID-07D) is canonical and final lint has no FAIL.
- [x] `textus-semantic-integration-engine` (CID-07D) is canonical and final lint has no FAIL.
- [x] Classify all 18/18 records: 14/14 canonical SNAPSHOT CARs and four
  exact released deferrals.
- [x] Keep `textus-corpus` 0.1.0 unchanged; corrective lint exits zero with
  identity-deferred WARN and no FAIL.
- [x] Keep `textus-experiment` 0.1.0 unchanged; corrective lint exits zero
  with identity-deferred WARN and no FAIL.
- [x] Keep `textus-georesolver` 0.2.1 unchanged; corrective lint exits zero
  with identity-deferred WARN and no FAIL.
- [x] Keep `textus-sanpomap` 0.2.1 unchanged; corrective lint exits zero with
  identity-deferred WARN and no FAIL.

The frozen implementation sequence and worktree-collision gate are recorded in
the [CID-07 CAR lint and development migration plan](../notes/phase-56-cid07-car-lint-and-development-migration-plan.md).
CID-07A is implemented and review-fixed. Final focused evidence is collaborator
API `22292` (27 tests), publishLocal `22502`, CNCF `22679` (23 tests), and Cozy
`23433` (30 tests), all with zero failures and released locks. Independent
focused re-review closed one stale E7 ledger row and returned PASS with no
findings and `FULL_REVIEW_REQUIRED=no`. CID-07A is ACCEPTED / REVIEWED.
CID-07B implementation evidence is SimpleModeler `38242` / publish `38436`,
Cozy `40022` / publish `40358`, sbt-cozy `40696` / publish `40900`, User
Account `42502`, and exit-zero canonical `cozy lint car` JSON. Independent
review findings RF-CID07B-001/002 are repaired: E8 now exercises actual lint
for equal local IDs under distinct namespaces, and edited Scala headers are
current/compressed. Corrective validation is Cozy `49328` (55 tests) and User
Account `49550` (1 test), both with zero failures and released locks. Focused
re-review returned PASS with no findings and `FULL_REVIEW_REQUIRED=no`;
CID-07B is ACCEPTED / REVIEWED. CID-07C is ACCEPTED / REVIEWED.
RF-CID07C-001 through RF-CID07C-008 repair
canonical User Notification selectors, ArtScene descriptor truth, admitted
parameter naming, Scala headers, executable-spec metadata, actual Cozy
publisher lifecycle evidence, and checklist truth. Its prior focused markers
are historical only: Textus AI `56453` (26 tests), ArtScene `75354`
(8 tests, using target-local Scraper CAR publication `70824`), BoK `87430`,
Control Center `81667`, Scraper `64539`, Supervisor `82759`, Toolchain Runner
`84531`, User Account `49550`, and User Notification `85662`. All final SBT
markers exit zero with released locks. Normal Cozy CAR lint for all nine
projects historically exited zero, reported each exact canonical identity, and
had no FAIL finding. Corrective post-review-fix validation is Cozy `98391`
(31 tests; superseding compile failure `97994`), ArtScene `98798` (8), Textus
AI `99544` (25), BoK `99900` (1), Control Center `203` (3), Scraper `472`
(3), Supervisor `816` (2), Toolchain Runner `1016` (7), and User Notification
`1185` (24). Every final marker exited zero with its lock released. Corrective
User Notification CAR lint exited zero with the exact canonical identity and
no FAIL finding. The first focused re-review closed RF-CID07C-001 through -007
but found BoK's legacy ABI dependency field. RF-CID07C-008 aligns
`org.simplemodeling.textus.SemanticIntegrationEngine` in `project.yaml` and
the ABI v2 manifest; `8279-20260808T104439Z` built the BoK CAR and passed its
1-test component spec with both exits zero and the lock released. Independent
focused re-review closed RF-CID07C-008 with PASS, no findings, and
`FULL_REVIEW_REQUIRED=no`. CID-07D is ACCEPTED / REVIEWED. REVIEW_FIX findings
`CID07D-R1` (stale Scala
`@version` headers) and `CID07D-R2` (Knowledge Editor assembly-identity
executable-spec structure) are applied. The official port-inclusion authority
is Textus Control Center's `docs/spec/default-server-port-registry.md`; the
five exact current hunks are explicitly user-authorized CID-07D scope, while
unrelated pre-existing changes remain excluded. Pre-fix final focused
validation evidence is AWS `47205` (2/2), Blog `47523` (30/30), CBD `36824`
(14/14), Knowledge Editor `41016` (124/124), framework `44150` (27/27) plus
`publishLocal` `44394`, and SIE `49993` (33/33), all exits zero with released
locks. Final Cozy CAR lint for all five collision repositories exited zero with
no FAIL findings. Post-review-fix focused validation invocation
`58134-20260808T130002Z` used the exact serialized wrapper command
`/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh --batch 'cozyBuildCar; testOnly org.goldenport.textus.knowledge.editor.ComponentFactorySpec'`.
The CAR was built; one suite completed with 125 tests succeeded and zero
failed, aborted, canceled, ignored, or pending, with `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. Nonblocking warnings were unused
`cozyCarName`, SNAPSHOT, mutable-pair, and nine deprecation warnings. Post-fix
full review returned PASS with Actionable findings 0; `CID07D-R1` and
`CID07D-R2` are CLOSED and `FULL_REVIEW_REQUIRED=no`. CID-07D is ACCEPTED /
REVIEWED. No CID-07D commit has been made; CID-07E is implemented with Step
full review pending. The CID-07 Step commit and Phase full validation remain
pending.
CID-07E is `ACCEPTED / REVIEWED`: 18/18 inventory records
are classified, 14/14 SNAPSHOT CARs are canonical with final lint showing no
FAIL, and the four exact released deferrals (Corpus 0.1.0, Experiment 0.1.0,
GeoResolver 0.2.1, Sanpomap 0.2.1) now lint exit zero with valid JSON,
identity-deferred WARN, and no FAIL. Those four source repositories were not
modified. CID-07D remains ACCEPTED / REVIEWED.

TEST_FIX #1: the initial four deferral lints classified identity correctly but
exited 1 on compatibility metadata. Corpus and Experiment reported typed
`ReleaseGenerationPairRejected` (authored 0.3.0 versus executing
0.3.4-SNAPSHOT); GeoResolver and Sanpomap reported typed
`CozyVersionMissing`. Cozy lint downgrades only those two typed diagnostics to
WARN when the exact identity code is
`CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED`; every other state/diagnostic
remains FAIL. E-CID07E-1..3 cover Corpus, GeoResolver, and the canonical User
Account negative case. Validation `63778-20260808T131546Z` used the exact
serialized wrapper `testOnly CozyCarLintSpec +
Phase56ProjectIdentityContractSpec`: two suites, 34 successes, zero other
statuses, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Corrective four
Cozy lints exited zero with valid JSON, no FAIL, empty stderr, and no source
mutation.

STEP REVIEW_FIX #1 (`CID07-FR-001`) closes the registered legacy local-ID
fail-open path. `ComponentIdentityMigrationClassifier` now validates the
registered `legacyLocalId` before exact, SNAPSHOT, or stable release branches
and returns `INVENTORY_ERROR` / `local-id-mismatch`; the exact-release check is
no longer duplicated. The Java classifier matrix adds advanced SNAPSHOT with a
wrong local ID and advanced stable with a missing local ID. The Cozy E7
projection spec adds matching `textus-corpus` wrong-class/local cases while
retaining valid advanced `MIGRATION_REQUIRED` cases. Focused validation of
`ComponentIdentityMigrationClassifierTest` and
`Phase56ProjectIdentityContractSpec` passed. Collaborator API invocation
`70784-20260808T133527Z` passed 27 tests with zero failures, errors, or ignored
tests, exited 0, and released its lock; `publishLocal` invocation
`71030-20260808T133554Z` published coordinate
`org.goldenport:cncf-collaborator-api:0.2.0-SNAPSHOT`, exited 0, and released
its lock (an initial invalid working directory did not launch SBT); Cozy
invocation `71199-20260808T133608Z` passed 2 suites and 34 tests with zero
failures, errors, ignored, aborted, canceled, or pending statuses, exited 0,
and released its lock. CID07-FR-001 post-fix validation passed. Independent
focused re-review closed CID07-FR-001 with no new finding, zero actionable
findings, `FULL_REVIEW_REQUIRED=no`, and PASS.

Commit-manifest validation closed `CID07-VF-002` through `CID07-VF-004`:
User Account uses validated `sbt-cozy 0.1.20-SNAPSHOT` (`84492`, CAR + 1/1),
Control Center projects canonical assembly/SPI/config/security identities
(`88259`, CAR + 3/3), and ArtScene removes duplicate source descriptor authority
while E3 checks the packaged schema-3 descriptor (`90629`, CAR + three suites /
8 tests). The serialized chain completed at `91574-20260808T142233Z`; all
accepted invocations exited zero and released the lock. Focused re-review found
no actionable issue and returned PASS with `FULL_REVIEW_REQUIRED=no`. Official
registry port hunks remain admitted and unchanged.

Parent scope clarification: path-level staging is safe. The Blog source
`BundleFactory` duplicate is excluded because the managed generated file has
identical SHA-1 `e39535...`; the official port inclusion is preserved. CID-07
A-E are accepted/reviewed. No Step commit or full Phase claim is made; Step
feature validation/commit remains pending.

The Cozy repair is accepted/reviewed and the CID-07 Step commit is not claimed.
Step feature validation/commit and Phase full validation remain pending. The
five official registry port hunks admitted in CID-07D
remain in scope for the eventual commit.
Existing separate debt is the coordinated Cozy `Resolved.projectrelativepath`
rename and BoK WARN-only nominal wrappers; protected unrelated paths remain
unchanged.

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
- [ ] Update Phase 57 entry contracts to consume the qualified Component,
  Service, Operation, and runtime routing identities without reopening Phase
  56 naming decisions.
- [ ] Update Phase 58 entry contracts to consume the qualified Component
  identity without reopening Phase 56 naming decisions.
