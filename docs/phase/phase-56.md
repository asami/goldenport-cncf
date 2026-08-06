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
| CID-02 | Typed identity and derivation core | Namespace, local ID, qualified Component ID, instance ID, and one shared projection library are implemented with validation and collision behavior. | in-progress |
| CID-03 | Cozy project schema and generation | `project.yaml`, Cozy, sbt-cozy, generated Scala APIs, package output, build metadata, and descriptor generation use `namespace + id`. | planned |
| CID-04 | CAR, Maven, and repository coordinates | CAR descriptor, filename, Maven group/artifact, repository layout/index, dependency declarations, cache keys, and integrity metadata use canonical or verified derived values. | planned |
| CID-05 | CNCF runtime identity migration | `Component.Core.name`, `ComponentId`, instance identity, loading, dependency resolution, routing, Help/Admin identity, diagnostics, and configuration targets use the qualified ID. | planned |
| CID-06 | Compatibility adapters | Legacy descriptor fields, bare IDs, artifact spellings, prefixed spellings, and Web paths decode through bounded single-authority adapters with ambiguity diagnostics. | planned |
| CID-07 | CAR lint and development CAR migration | CAR lint classifies canonical, required-SNAPSHOT-migration, deferred-release, and disagreement states; every inventoried SNAPSHOT CAR migrates and non-SNAPSHOT CARs enter the next-version ledger. | planned |
| CID-08 | Ecosystem regression and normative closure | Representative samples, launchers, CBD/BoK metadata, and dependency consumers adopt the contract; cross-repository tests, migration guidance, design/spec promotion, review, compatibility ledger, and release evidence close the phase. | planned |

## Repository Ownership

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
