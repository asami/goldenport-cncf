# Phase 56 - Namespace-qualified Component Identity and Derived Coordinates

status=in-progress
started_at=2026-08-06
planned_at=2026-08-06
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

The CID-01 inventory note is the authoritative Phase-56 working specification
until CID-08 normative promotion. It is implementation-free working authority,
not the final normative identity design; executable leaves remain target
pending until their owning stage implements them.

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
| CID-06 | Compatibility adapters | Legacy descriptor fields, bare IDs, artifact spellings, prefixed spellings, and Web paths decode through bounded single-authority adapters with ambiguity diagnostics. | planned |
| CID-07 | CAR lint and development CAR migration | CAR lint classifies canonical, required-SNAPSHOT-migration, deferred-release, and disagreement states; every inventoried SNAPSHOT CAR migrates and non-SNAPSHOT CARs enter the next-version ledger. | planned |
| CID-08 | Ecosystem regression and normative closure | Representative samples, launchers, CBD/BoK metadata, and dependency consumers adopt the contract; cross-repository tests, migration guidance, design/spec promotion, review, compatibility ledger, and release evidence close the phase. | planned |

CID-05 slice ledger is accepted/reviewed and ready for its Step commit:

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
CID-05D and the CID-05 Step closure criterion are accepted; the Step commit,
Phase full validation, and Phase 56 closure remain pending, and HYG-P56-005
remains separate.

CID-05D does not implement CID-06 compatibility adapters or CID-01 E4 bare
assembly admission, alter ComponentId syntax, repository/cache behavior, or
HYG-P56-005. CID-05 is complete; Phase 56 and Phase full validation remain
incomplete.

CID-05D also exposed an adjacent plain-`Action` implicit-job defect. Because
fixing it changes observable execution behavior rather than Component
identity, [Phase 57 - Action Execution Semantics](phase-57.md) owns its
compatibility inventory, executable matrix, implementation, validation, and
review. It does not extend CID-05 or block CID-05D review on unrelated
execution-policy work.

CID-05B excludes compatibility adapters (CID-06), routing and presentation
projection (CID-05C), end-to-end closure (CID-05D), and cross-file/public
hygiene (HYG-P56-005). Phase 56 remains incomplete.

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
