# Phase 56 - Namespace-qualified Component Identity and Derived Coordinates

status=closed
started_at=2026-08-06
planned_at=2026-08-06
closed_at=2026-08-09
depends_on=[Phase 55](phase-55.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 56 Checklist](phase-56-checklist.md)
planning_journal=[Phase 56 Identity Planning and Phase Renumbering](../journal/2026/08/2026-08-06-phase-56-component-identity-planning-and-renumbering.md)
entry_handoff=[Current Work Closeout before Phase 56](../journal/2026/08/2026-08-06-current-work-closeout-before-phase-56.md)

## Purpose

Replace independently authored Component, artifact, Maven, JVM-package, and
generated-class names with one namespace-qualified Component identity and
deterministic projections.

Phase 56 makes `namespace + id` the sole naming and identity authority. A
release version remains independent release metadata, and localized display
text remains non-identifying presentation metadata.

For the official Textus User Account CAR, the canonical inputs are:

```yaml
namespace: org.simplemodeling.textus
id: UserAccount
```

The word `namespace` is canonical. Maven `organization`/`groupId`, JVM
package, Component name, artifact name, generated class name, and paths are
projections; `package` is not used as the cross-system metadata field.

## Canonical Model

The planned typed identity model is:

```text
ComponentNamespace("org.simplemodeling.textus")
ComponentLocalId("UserAccount")
ComponentId(namespace, localId)
ComponentInstanceId(componentId, "default")
```

`ComponentId` is namespace-qualified. A bare `UserAccount` value is a local
ID or a compatibility spelling, never a globally complete Component ID.

The canonical authoring shape is provisionally:

```yaml
project:
  namespace: org.simplemodeling.textus
  id: UserAccount
  component:
    version: 0.6.0-SNAPSHOT
    displayName: Textus User Account
```

CID-01 freeze artifacts: [identity inventory and failing-first contract](../notes/phase-56-cid01-component-identity-inventory-and-failing-first-contract.md)
and [CAR migration ledger](../notes/phase-56-cid01-car-migration-ledger.yaml).

The CID-01 inventory note is the historical implementation-free working
authority promoted by CID-08 to the normative Component identity design and
specification. Its inventory and rule IDs remain evidence, not a second
identity authority.

Only `namespace` and `id` determine names and identifiers. `version` selects
a release. `displayName`, summaries, and localized titles are descriptive and
must not participate in equality, routing, lookup, repository keys, or code
generation identity.

## Deterministic Projections

For `org.simplemodeling.textus + UserAccount`, Phase 56 freezes and implements
the following projections:

| Projection | Result | Rule |
| --- | --- | --- |
| Qualified Component name/ID | `org.simplemodeling.textus.UserAccount` | `namespace + "." + id` |
| Maven organization/groupId | `org.simplemodeling.textus` | exact namespace |
| Artifact name/artifactId | `textus-user-account` | final namespace segment + kebab-case local ID |
| CAR filename | `textus-user-account-0.6.0-SNAPSHOT.car` | artifact name + release version |
| Maven coordinate | `org.simplemodeling.textus:textus-user-account_3:0.6.0-SNAPSHOT` | namespace + artifact projection + Scala suffix + version |
| JVM package | `org.simplemodeling.textus.useraccount` | namespace + lower-flat local ID |
| Generated Scala API | `UserAccountComponent` | local ID + `Component` |
| Normalized local path segment | `user-account` | kebab-case local ID |
| Legacy Web path alias | `/web/textus-user-account/...` | compatibility projection, not identity |

The exact word-splitting, acronym, digit, validation, escaping, and collision
rules must be one shared library contract. Generators and runtime consumers
must not reimplement these transformations independently.

Artifact filenames are not globally unique identities. Repository and Maven
lookup use the namespace-qualified coordinate. Two namespaces may therefore
produce the same filename without collapsing their identities.

## Descriptor Contract

The target CAR descriptor authoring contract is:

```json
{
  "namespace": "org.simplemodeling.textus",
  "id": "UserAccount",
  "version": "0.6.0-SNAPSHOT"
}
```

`name`, `component`, `className`, `scalaPackage`, Maven organization, and
artifact name are not independent canonical inputs. A generated descriptor or
repository index may materialize projections for convenience, but generation
and admission must recompute them from the canonical identity and reject a
divergent materialized value.

## Compatibility Boundary

- New authoring and serialization emit only the namespace-qualified model.
- Existing `UserAccount`, `textus-user-account`, and previously admitted
  prefixed spellings are decode/route compatibility aliases.
- Compatibility aliases resolve to one canonical `ComponentId` before lookup,
  routing, caching, or diagnostics.
- Ambiguous legacy spellings are rejected; namespace is never guessed when
  more than one canonical identity could match.
- Existing `/web/textus-user-account/...` paths may remain supported without
  becoming canonical Component IDs.
- Compatibility has an explicit warning/removal policy and cannot become a
  second write authority.

## CAR Migration Cohort and Lint Policy

Phase 56 includes migration of every admitted first-party/development CAR
whose effective artifact version is `SNAPSHOT` at the CID-01 inventory freeze.
The inventory records the exact repository, current version, identity shape,
derived coordinates, and migration owner. A SNAPSHOT CAR cannot be declared
Phase 56-complete while it still authors the legacy identity shape.

An admitted CAR whose effective version is not `SNAPSHOT` is not rewritten or
republished during Phase 56 solely for this identity change. Its current
release remains loadable through the compatibility adapter, and migration is
required when development of its next version begins.

The machine-precise release deferral rule is: `effective_version == current_release (exact release equality) => deferred; semantically comparable and effective_version > current_release while legacy identity remains => migration-required; effective_version < current_release OR versions are uncomparable OR effective_version is malformed => separate version/inventory error, never deferred or migration-required.`

CAR lint makes this boundary executable:

- canonical `namespace + id` with consistent projections passes;
- a legacy-identity SNAPSHOT CAR fails lint as migration-required;
- a legacy-identity non-SNAPSHOT CAR is detected as
  migration-deferred-to-next-version without invalidating the existing
  release;
- when that CAR advances beyond the release version recorded in the deferral
  ledger—normally to its next SNAPSHOT, but also if it advances directly to a
  release version—the condition becomes migration-required and fails lint;
- a lower, uncomparable, or malformed effective version is a separate
  version/inventory error and is never classified as deferred or
  migration-required;
  and
- a declared or materialized derived value that disagrees with the canonical
  identity fails lint for both SNAPSHOT and release versions.

Lint must report the effective version, canonical-or-legacy identity shape,
expected derived values, migration status, and actionable owner/path. It must
not silently infer a namespace or rewrite project metadata.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CID-01 | Inventory and contract freeze | Every authored/derived name, identity type, descriptor field, coordinate, path, alias, and consumer is inventoried; the exact SNAPSHOT migration cohort and non-SNAPSHOT deferral ledger are frozen with failing-first acceptance identities. | done |
| CID-02 | Typed identity and derivation core | Namespace, local ID, qualified Component ID, instance ID, and one shared projection library are implemented with validation and collision behavior. | done |
| CID-02A | Shared Java identity/projection core | `cncf-collaborator-api` owns the Scala-version-neutral Java identity/projection ABI. | accepted/reviewed |
| CID-02B | CNCF adoption | CNCF runtime semantics and adapters adopt the shared ABI. | accepted/reviewed |
| CID-02C | Cozy adoption | Cozy and sbt-cozy consume the shared ABI through package-private Java-ABI adapters; project schema/generation remains CID-03, coordinate/repository/publication wiring CID-04, and lint CID-07. | accepted/reviewed |
| CID-03 | Cozy project schema and generation | `project.yaml`, Cozy, sbt-cozy, generated Scala APIs, package output, build metadata, and descriptor generation use `namespace + id`. | accepted/reviewed |
| CID-04 | CAR, Maven, and repository coordinates | CAR descriptor, filename, Maven group/artifact, repository layout/index, dependency declarations, cache keys, and integrity metadata use canonical or verified derived values. CID-04E adds the CNCF v2 index reader and direct canonical resolver while legacy runtime repository migration remains CID-05/06. | done |
| CID-05 | CNCF runtime identity migration | `Component.Core.name`, `ComponentId`, instance identity, loading, dependency resolution, routing, Help/Admin identity, diagnostics, and configuration targets use the qualified ID. | done |
| CID-06 | Compatibility adapters | Legacy descriptor fields, bare IDs, artifact spellings, prefixed spellings, and Web paths decode through bounded single-authority adapters with ambiguity diagnostics. | done; committed |
| CID-07 | CAR lint and development CAR migration | CAR lint classifies canonical, required-SNAPSHOT-migration, deferred-release, and disagreement states; every inventoried SNAPSHOT CAR migrates and non-SNAPSHOT CARs enter the next-version ledger. | done; committed |
| CID-08 | Ecosystem regression and normative closure | Promote the accepted ecosystem behavior, migration guidance, compatibility ledger, and successor-phase contract to normative design/specification. | done; committed |

CID-07A was implemented and review-fixed as part of the subsequently committed
CID-07 Step. The exact registry and classification authority now lives in the
shared Java ABI, CNCF consumes it through its existing runtime adapter, and
Cozy CAR lint exposes canonical, migration-required, exact-deferred,
inventory-error, and projection-disagreement results. Final focused evidence
is collaborator API `22292-20260808T070443Z` (27 tests), publishLocal
`22502-20260808T070455Z`, CNCF `22679-20260808T070510Z` (23 tests), and Cozy
`23433-20260808T070648Z` (30 tests), all with zero failures and released locks.
Independent focused re-review closed one stale Cozy E7 authority-ledger row and
returned PASS with no findings and `FULL_REVIEW_REQUIRED=no`; CID-07A is
ACCEPTED / REVIEWED. CID-07B now carries canonical identity through the
SimpleModeler, Cozy, sbt-cozy, and User Account build/runtime boundary. Focused
evidence is SimpleModeler `38242` plus publish `38436`, Cozy `40022` plus
publish `40358`, sbt-cozy `40696` plus publish `40900`, User Account `42502`,
and an exit-zero canonical `cozy lint car` result. Independent review findings
RF-CID07B-001/002 are repaired; review-fix evidence is Cozy `49328` (55 tests)
and User Account `49550` (1 test), both with zero failures and released locks.
CID-07B focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`; CID-07B is ACCEPTED / REVIEWED. CID-07C review fixes
RF-CID07C-001 through RF-CID07C-008 are applied and carry
canonical build, descriptor, ABI, generated Core, and runtime identity through
the nine noncolliding SNAPSHOT repositories. The prior focused evidence is
historical only: Textus AI
`56453` (26 tests), ArtScene `75354` (8 tests with isolated Scraper CAR
`70824`), BoK `87430`, Control Center `81667`, Scraper `64539`, Supervisor
`82759`, Toolchain Runner `84531`, User Account `49550`, and User Notification
`85662`; every historical marker exited zero with the lock released. Normal
Cozy CAR lint historically reported the exact canonical identity and no FAIL
finding for all nine projects. Corrective post-review-fix evidence is Cozy
`98391` (31 tests; superseding compile failure `97994`), ArtScene `98798`
(8), Textus AI `99544` (25), BoK `99900` (1), Control Center `203` (3),
Scraper `472` (3), Supervisor `816` (2), Toolchain Runner `1016` (7), and
User Notification `1185` (24). Every final marker exited zero with the lock
released. Corrective User Notification CAR lint exited zero with its exact
canonical identity and no FAIL finding. The first focused re-review closed
RF-CID07C-001 through -007 but found BoK's legacy ABI dependency field;
RF-CID07C-008 now aligns the exact
`org.simplemodeling.textus.SemanticIntegrationEngine` dependency in
`project.yaml` and the ABI v2 manifest. Invocation
`8279-20260808T104439Z` built the BoK CAR and passed its 1-test component spec
with both exits zero and the lock released. Independent focused re-review
closed RF-CID07C-008 with PASS, no findings, and
`FULL_REVIEW_REQUIRED=no`; CID-07C is ACCEPTED / REVIEWED. No CID-07C commit
or Phase full-validation claim is made. Separate debt is the coordinated Cozy
`Resolved.projectrelativepath` rename and BoK WARN-only nominal wrappers;
protected unrelated paths remain unchanged. CID-07D is
ACCEPTED / REVIEWED. REVIEW_FIX findings
`CID07D-R1` (stale Scala `@version` headers) and `CID07D-R2` (Knowledge Editor
assembly-identity executable-spec structure) are applied. The official
port-inclusion authority is Textus Control Center's
`docs/spec/default-server-port-registry.md`; the five exact current hunks are
explicitly user-authorized CID-07D scope, while unrelated pre-existing changes
remain excluded. Pre-fix final focused validation evidence is AWS `47205`
(2/2), Blog `47523` (30/30), CBD `36824` (14/14), Knowledge Editor `41016`
(124/124), framework `44150` (27/27) plus `publishLocal` `44394`, and SIE
`49993` (33/33), all exits zero with released locks. Final Cozy CAR lint for
all five collision repositories exited zero with no FAIL findings. Post-review-
fix focused validation invocation `58134-20260808T130002Z` used the exact
serialized wrapper command
`/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh --batch 'cozyBuildCar; testOnly org.goldenport.textus.knowledge.editor.ComponentFactorySpec'`.
The CAR was built; one suite completed with 125 tests succeeded and zero
failed, aborted, canceled, ignored, or pending, with `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. Nonblocking warnings were unused
`cozyCarName`, SNAPSHOT, mutable-pair, and nine deprecation warnings. Post-fix
full review returned PASS with Actionable findings 0; `CID07D-R1` and
`CID07D-R2` are CLOSED and `FULL_REVIEW_REQUIRED=no`. CID-07D is ACCEPTED /
REVIEWED. CID-07D and CID-07E were accepted/reviewed before the completed
CID-07 Step commit. At that checkpoint, Phase full validation remained
pending.

CID-07E is `ACCEPTED / REVIEWED`. The frozen inventory is
18/18 classified: all 14/14 SNAPSHOT CARs are canonical and final lint has no
FAIL findings, using accepted Slice evidence for User Account from CID-07B,
eight CARs from CID-07C, and five CARs from CID-07D. The exact four released
deferrals are Corpus 0.1.0, Experiment 0.1.0, GeoResolver 0.2.1, and Sanpomap
0.2.1. Their corrective lints exit zero with valid JSON, identity-deferred
WARN, and no FAIL; the four source repositories were not modified. CID-07D
remains ACCEPTED / REVIEWED.

TEST_FIX #1 records that the initial four deferral lints classified identity
correctly but exited 1 on compatibility metadata: Corpus and Experiment had
`ReleaseGenerationPairRejected` (authored 0.3.0 versus executing
0.3.4-SNAPSHOT), and GeoResolver and Sanpomap had `CozyVersionMissing`. Cozy
lint downgrades only typed `CozyVersionMissing` and
`ReleaseGenerationPairRejected` to WARN when the exact identity code is
`CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED`; every other state or diagnostic
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

Commit-manifest validation also closed `CID07-VF-002` through
`CID07-VF-004`: User Account now consumes validated `sbt-cozy
0.1.20-SNAPSHOT` (`84492`, CAR + 1/1); Control Center carries canonical
assembly/SPI/config/security identities (`88259`, CAR + 3/3); and ArtScene
removes duplicate source descriptor authority while E3 verifies the packaged
schema-3 descriptor (`90629`, CAR + three suites / 8 tests). The serialized
chain completed at `91574-20260808T142233Z`; accepted invocations exited zero
and released the shared lock. Focused re-review returned PASS, no findings, and
`FULL_REVIEW_REQUIRED=no`. Official registry port hunks remain admitted and
unchanged.

Parent scope clarification: path-level staging is safe. The Blog source
`BundleFactory` duplicate is excluded because the managed generated file has
identical SHA-1 `e39535...`; the official port inclusion is preserved. CID-07
A-E are accepted/reviewed and the CID-07 Step commit is complete. At that Step
checkpoint, no full Phase claim was made and Phase full validation remained
pending.

The Cozy repair is accepted/reviewed and included in the completed CID-07 Step
commit. At that Step checkpoint, Phase full validation remained pending. The
five official registry port hunks admitted in CID-07D were included in that
Step commit.

CID-05 slice ledger is committed and done:

| Slice | Status | Boundary |
| --- | --- | --- |
| CID-05A | ACCEPTED/REVIEWED | Invocation `44742-20260807T131308Z`: 1 suite, 12 executable leaves, 24 matrix operations, 1 intentional pending, exits 0, lock released. |
| CID-05B | ACCEPTED/REVIEWED | Canonical schema-3 descriptor, repository/factory, and configuration identity. Final review-fix validation `81149-20260807T150153Z`: 106 passed, 1 intentional pending, 9 suite executions completed, exits 0, lock released. Independent focused re-review closed CID05B-R1 through R7 with PASS on reviewed tracked diff `4d0f835fab40b5b4e0dfb6c69370e4ca17659f848b32602f3c848abcb5e1b459`. Future Step integration remains Phase 56 A+B. |
| CID-05C | ACCEPTED/REVIEWED | Initial findings `CID05C-R1` through `CID05C-R9` and second-pass findings `RF-CID05C-010`/`RF-CID05C-011` are repaired. Authoritative invocation `11800-20260707T210921Z` passed 234 tests across 12 suites with one intentional pending leaf, exits 0, and lock released. Independent focused re-review returned PASS on reviewed tracked diff `b5c00333fa8d91d09a03473f8cbcff741dbb5f23877039db034cc4715ac6fd93`; validation repairs `VF-CID05C-012` through `VF-CID05C-015` are closed. |
| CID-05D | ACCEPTED/REVIEWED | RF-CID05D-001..013 are closed, preserving exact schema/binding identity, namespace-isolated Web/manual roots, subsystem-owned successful-CAR loader lifecycle, current header history, method-local helper naming, and repository-local temporary-directory lifecycle. Independent focused re-review returned PASS with no actionable finding on reviewed scoped diff `64fd912685db918b9c92e5b02e556b81b42b39cd83b2450aeb31ec86255b46ec`; `FULL_REVIEW_REQUIRED=no`. |

CID-05D writes a real assembly descriptor with the two exact qualified
declarations but no authored binding IDs. Runtime admission promotes only
successful `ComponentId.parseC` values; the ordinary CAR repository, integrity,
ClassLoader, factory, Core, ComponentSpace, resolver, and public Request path
then retain both identities. Both components display `Shared`, exact qualified
Requests return namespace-distinguishing scalars, and bare `Shared` selects
neither component while resolver ambiguity names both qualified candidates.

Implementation-focused validation passed with invocation `33307-20260807T220812Z`:
4 suites completed, 55 tests succeeded, 1 intentional pending leaf remained,
both exits were zero, and the shared lock was released. The exact command was:

```text
testOnly org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec
```

Review-fix validation is complete for `RF-CID05D-001` through
`RF-CID05D-010`. Validation repairs `VF-CID05D-001` (replaced the illegal
anonymous sealed `Specification` with existing concrete development-repository
specifications) and `VF-CID05D-002` (observe the Subsystem-owned loader
snapshot without changing lifecycle semantics) are also complete. The
authoritative final focused validation is invocation
`59976-20260807T231428Z`, using the exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityMigrationSpec org.goldenport.cncf.subsystem.Phase56RuntimeIdentityProjectionSpec org.goldenport.cncf.subsystem.Phase56NamespaceIsolatedRuntimeIntegrationSpec org.goldenport.cncf.component.ComponentDescriptorSpec org.goldenport.cncf.component.repository.ComponentRepositoryCarSpec org.goldenport.cncf.http.RuntimeComponentDevelopmentWebProjectionSpec org.goldenport.cncf.subsystem.resolver.OperationResolverSpec org.goldenport.cncf.projection.GeneratedHelpProjectionSpec org.goldenport.cncf.subsystem.GenericSubsystemDescriptorSpec org.goldenport.cncf.subsystem.GenericSubsystemFactorySpec"]
```

Eleven suites completed, zero aborted; 243 tests succeeded, zero
failed/canceled/ignored, and one intentional pending remained;
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. The smallest lifecycle
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
CID-05D and the CID-05 Step closure criterion are accepted. The Step commit is
`d5d3c5bb71962d93898ac8b1ddbcac7d9c8cfe83` (`Carry qualified component
identity through CNCF runtime`). Its exact four-suite validation invocation
`86240-20260808T003231Z` passed 59 tests with one intentional pending leaf;
both exits were zero and the lock was released. At that CID-05 checkpoint,
Phase full validation and Phase 56 closure remained pending, and HYG-P56-005
remained separate.

CID-05D does not implement CID-06 compatibility adapters or CID-01 E4 bare
assembly admission, alter ComponentId syntax, repository/cache behavior, or
HYG-P56-005. CID-05 was complete while Phase 56 and Phase full validation
remained incomplete at that checkpoint.

CID-06 is implemented through the separate
[compatibility adapter plan](../notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md).
CID-06A completed the `AssemblyBinding` adapter. The current CID-06A + CID-06B
boundary owns `AssemblyBinding` plus `DescriptorField`: exact qualified,
bare, normalized-artifact, and namespace-leaf-prefixed artifact aliases remain
central-adapter decisions only. `REVIEW #1` admitted `R1-F1` static candidate
discovery, `R1-F2` adapter visibility, `R1-F3` projection evidence, `R1-F4`
semantic spec grouping, `R1-F5` adapter-plan lifecycle reporting, and `R1-F6`
phase/checklist lifecycle reporting. `REVIEW_FIX #1` is applied. CID-06B
provides expected-identity-bound legacy descriptor projection, strict schema-3
preservation, and assembly/static-repository integration; its
[separate plan](../notes/phase-56-cid06b-component-descriptor-compatibility-plan.md)
retains unbound decode without namespace inference and defers notices to
CID-06C. `VF-CID06B-001` and `VF-CID06B-002` correct the two admission-method
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
`VF-CID06B-003` are closed. CID-06B is `ACCEPTED / REVIEWED`. That record is
historical; current lifecycle state has CID-06C through CID-06E
accepted/reviewed, while the CID-06 Step feature-test/commit and Phase full
validation remained pending at that checkpoint.

CID-06E is frozen separately in the
[compatibility Step acceptance plan](../notes/phase-56-cid06e-compatibility-step-acceptance-plan.md).
Status: `ACCEPTED / REVIEWED`. One hermetic exact
Corpus release fixture now crosses real assembly,
repository, ClassLoader/factory/Core, public routing, Help/Meta,
AssemblyReport/Admin, strict negative-selector, and shutdown boundaries.
Focused validation is complete: `84975-20260808T051049Z` passed E1 and
`85353-20260808T051139Z` passed 11 suites/179 tests warning-free. Full review
found no production or specification defect and admitted only RF-CID06E-002
stale lifecycle wording. That documentation repair is applied, and independent
focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`.

The E1 executable acceptance uses `GenericSubsystemFactory`, the configured
packed-CAR repository, the unchanged generated `ComponentId("Corpus")` Core,
public canonical and legacy Request routing, Help/Meta, the owning Admin
assembly report, strict negative selectors, and repeated shutdown. CID-06D and
CID-06E share `LegacyDeferredReleaseCarFixture`; all acceptance work is rooted
under the deterministic CID-06E target directory and cleaned by the spec.

The acceptance validation chain reached the frozen defect in stages: `80957`
aborted on invalid WorkAreaId, `81384` exposed missing fixture capabilities,
`81844` exposed the provider owner, and `82252`/`82742` exposed deferred scope
loss during GenericSubsystemFactory rematerialization. RF-CID06E-001 now
carries the exact repository-admitted deferred entry as package-internal,
nonserialized Component provenance, scopes only the rematerializing
factory/Core call, and propagates the same provenance. Smallest corrective
invocation `84975-20260808T051049Z` passed E1; authoritative invocation
`85353-20260808T051139Z` passed the exact 11-suite accumulator with 179 tests,
no warnings, both exits zero, and the lock released. Full review found only
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
validation remained pending at that checkpoint.

The final CID-06 Step accumulator `9227-20260808T062445Z` completed all 19
suites with 306 tests succeeded, zero failed/canceled/ignored/pending/aborted,
no warnings, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Commit
`6afab962ccd33431e6e2944c8d9191295d1a7f38`
(`Preserve bounded component identity compatibility`) closes the CID-06 Step.
At that CID-06 checkpoint, only Phase full validation remained pending.

CID-06C review-fix implementation is recorded in the separate
[runtime selector and compatibility observability plan](../notes/phase-56-cid06c-runtime-selector-observability-plan.md).
It centralizes admitted runtime display/artifact alias decisions, retains
typed assembly and descriptor notices through the runtime factory boundary,
and projects deduplicated warnings through the existing assembly Admin report.
Status: `ACCEPTED / REVIEWED`. Runtime adapter,
admission notice retention, resolver, Help/Meta, Web, and existing
AssemblyReport/Admin warning boundaries are implemented with repaired E1-E9
executable evidence. Post-fix focused invocation `43771-20260808T030432Z`
completed 11 suites with 119 tests succeeded, zero failed/canceled/ignored/
pending/aborted, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. The
required full re-review
closed the runtime semantics but admitted RF-CID06C-009 through
RF-CID06C-012 for executable metadata/grouping, internal naming, and edited
Scala headers. Those bounded repairs are applied. Initial validation
`52300-20260808T033036Z` stopped during test compilation on exactly three stale
test references to renamed internal fields. After correcting those references,
authoritative invocation `52869-20260808T033203Z` completed all 11 suites with
119 tests succeeded, zero failed/canceled/ignored/pending/aborted, no warnings,
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Independent focused
re-review returned PASS with no findings on tracked diff
`50ed3a22239bd8ed5dab2819957c421d72562abc1a0f683b4b409517a53252c6`;
`FULL_REVIEW_REQUIRED=no`. At that checkpoint CID-06C was accepted/reviewed,
but no CID-06 Step commit was yet claimed. Historical pre-review invocation
`26232` is retained only as superseded evidence. CID-06E was
accepted/reviewed; the Step commit, Phase full validation, CID-07, and
notice-removal ownership remained pending at that checkpoint.

CID-06D review fixes are applied under the separate
[deferred-release compatibility plan](../notes/phase-56-cid06d-deferred-release-compatibility-plan.md).
The frozen boundary contains exactly four released coordinates, separates raw
archive evidence from canonical in-memory projection, scopes legacy generated
bare `ComponentId` calls to exact repository factory execution, and keeps
SNAPSHOT/future/lower/malformed/unregistered inputs strict or invalid. The
exact registry, raw/effective archive roles, runtime-evidence exception,
thread-local factory/Core scope, repository admission wrapper, warning, and
E1-E10 executable fixture matrix are implemented. RF-CID06D-001 through
RF-CID06D-004 restrict deferral to schema 2, reject an existing non-regular
runtime-manifest path, and repair the specification and lifecycle evidence.
Status: `ACCEPTED / REVIEWED`. Historical
invocation `66284` failed during compilation; `66671` compiled and reported three
failures; `67421` completed five suites with 109 tests passed, no warnings,
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. That evidence predates and
is superseded by these review fixes. Post-fix invocation
`74557-20260808T044055Z` completed the same five suites with 109 tests passed,
no warnings, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Independent
focused re-review verified the exact repaired hashes, closed RF-CID06D-001
through RF-CID06D-004 without new findings, and returned PASS with
`FULL_REVIEW_REQUIRED=no`. CID-06E was accepted/reviewed; the CID-06 Step
commit and Phase full validation remained pending at that checkpoint.

CID-05D also exposed an adjacent plain-`Action` implicit-job defect. Because
fixing it changes observable execution behavior rather than Component
identity, [Phase 57.1 - Plain Action Direct Execution](phase-57.1.md) owns its
compatibility inventory, executable matrix, implementation, validation, and
review. It does not extend CID-05 or block CID-05D review on unrelated
execution-policy work.

CID-05B excludes compatibility adapters (CID-06), routing and presentation
projection (CID-05C), end-to-end closure (CID-05D), and cross-file/public
hygiene (HYG-P56-005). At that CID-05B checkpoint, Phase 56 remained
incomplete.

## Repository Ownership

- `cncf-collaborator-api` owns the Scala-version-neutral Java identity/projection
  ABI. `cloud-native-component-framework` owns runtime semantics and adapters;
  `cozy` and `sbt-cozy` consume the ABI without reimplementing identity
  validation, tokenization, or projections.
- `simplemodeling-lib` owns a generic validated namespace/local-ID and naming
  transformation foundation only if existing generic identity facilities are
  insufficient.
- `cloud-native-component-framework` owns Component identity semantics,
  runtime admission, descriptor interpretation, routing, diagnostics, and
  compatibility policy.
- `cozy` and `sbt-cozy` own project schema, generation, package/class
  projections, build metadata, generated descriptor consistency, and CAR lint
  migration classification/diagnostics.
- CAR projects own migration to canonical inputs and removal of independently
  authored derived names.
- Component Repository, launchers, Textus CBD Support, and Textus BoK own
  coordinate/index/transport adoption without defining alternate identities.

## Acceptance

- Two Components with the same local ID and different namespaces remain
  distinct through descriptor, runtime, repository, cache, dependency,
  routing, and diagnostics.
- One canonical identity deterministically reproduces every declared
  projection, including acronym and digit edge cases.
- No new project or descriptor can independently set conflicting `name`,
  `component`, organization, artifact, class, or package identities.
- CAR descriptor, generated Scala class, runtime `Component.Core`, and
  `ComponentId` agree without string normalization heuristics.
- Maven coordinates and CAR repository keys retain the full namespace even
  when the human-facing artifact filename uses only its final segment.
- User Account resolves as `org.simplemodeling.textus.UserAccount`, publishes
  as `textus-user-account`, generates `UserAccountComponent`, and uses
  `org.simplemodeling.textus.useraccount` without duplicate authoring.
- Every SNAPSHOT CAR in the frozen Phase 56 cohort uses canonical
  `namespace + id` authoring and passes CAR lint.
- Every non-SNAPSHOT legacy CAR is preserved unchanged, remains compatible,
  and is visible in CAR lint and the next-version migration ledger.
- Advancing a deferred CAR beyond its recorded current release version makes
  legacy identity a lint failure until that CAR migrates.
- Legacy spellings remain usable only through tested compatibility adapters;
  ambiguous aliases fail with actionable diagnostics.
- Display names and titles can change or localize without changing identity,
  routes, packages, artifacts, or dependencies.
- Phase closure leaves one authoritative identity model in implementation,
  generated output, design, specification, and migration guidance.

## Boundary

- Phase 56 does not redesign Component behavior, Service/Operation APIs,
  configuration semantics, resource SubComponents, Admin presentation, or Web
  CSRF behavior except where they consume Component identity.
- Phase 56 does not rename existing public Web routes merely to make them look
  canonical; route migration is compatibility-policy work.
- Phase 56 does not treat the version or display metadata as part of the
  namespace/local-ID naming truth.
- Phase 56 does not rewrite or republish a non-SNAPSHOT CAR release only to
  adopt the new identity; that CAR migrates in its next development version.
- No partial schema is released in which some tools author the old names and
  others author the new fields without consistency validation.

## Completion Rule

Phase 56 closes only after the canonical identity and derivation contract is
implemented and accepted end to end across CNCF, Cozy/sbt-cozy, CAR packaging,
Maven publication, repository resolution, User Account, and representative
runtime consumers. Every frozen SNAPSHOT CAR must be migrated and lint-clean,
and every non-SNAPSHOT deferral must have a lint-visible next-version owner.
Verified behavior must be promoted to normative design and specification;
this phase document and working notes are not the final authority.

## Closure Record

Phase 56 is closed on 2026-08-09. The normative authority is the
[Component Identity Design](../design/component-identity.md) plus the
[Component Identity Specification](../spec/component-identity.md); CID-01
through CID-08 are implemented, accepted, reviewed, and committed. The final
Step commits are:

- CID-05: `d5d3c5bb71962d93898ac8b1ddbcac7d9c8cfe83`;
- CID-06: `6afab962ccd33431e6e2944c8d9191295d1a7f38`;
- CID-07 CNCF contract: `34cb4483813a3d276edab2d5e604804087a96c08`
  with the 18 companion repository commits recorded by the CID-07 plan; and
- CID-08: `3d723c3ba18954bdca55f85ba3d64ac046c7512d`.

The user explicitly waived final Phase-wide full validation on 2026-08-09.
No new full-test claim is made. Closure relies on the recorded Slice/Step
focused, integration, CAR packaging/publication/resolution, lint, and
independent-review evidence accepted before each Step commit. All SNAPSHOT
versions remain unchanged, including `sbt-cozy 0.1.20-SNAPSHOT` and
`cozy 0.3.4-SNAPSHOT`. The Phase 57 series may begin and must not reopen the qualified
Component identity contract.
