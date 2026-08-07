# Phase 56 CID-04D - sbt-cozy Repository Resolution Plan

status=implementation plan frozen
date=2026-08-07
phase=[Phase 56](../phase/phase-56.md)
checklist=[Phase 56 checklist](../phase/phase-56-checklist.md)
parent=[CID-04 coordinate plan](phase-56-cid04-car-maven-repository-coordinate-plan.md)
slice=CID-04D - sbt-cozy canonical CAR dependency, repository/cache resolution, publication wiring, and E9 activation
baseline_repository=/Users/asami/src/dev2026/sbt-cozy
baseline_head=23736614e91d6865cd55d5089624273fa9c5cb25

## Goal and authority

CID-04D makes the shared collaborator-API `ComponentReleaseCoordinate` the
sole authority for every sbt-cozy CAR dependency key, repository path, cache
path, runtime extraction root, Cozy bridge dependency tuple, and CAR
publication destination. The canonical inputs are namespace, exact local ID,
and release. No namespace is inferred from module name, publication name,
artifact, filename, Scala package, repository path, or cache contents.

The collaborator API worktree at
`/Users/asami/src/dev2026/cncf-collaborator-api` supplies the authoritative
release and path projections. `CarComponentIdentityAdapter` may expose those
shared projections to sbt-cozy, but must not reconstruct them by splitting or
joining strings independently.

CID-04C already froze Cozy's four-field dependency bridge and canonical CAR
publication layout. CID-04D aligns the sbt-cozy producer and resolver with that
boundary. CNCF repository/index consumption remains CID-04E.

## CarDependency compatibility boundary

Canonical source construction is:

    CarDependency(namespace, localId, version)

The existing two-argument source expression remains constructible for the
compatibility window:

    CarDependency(name, version)

That legacy value carries no inferred namespace and fails before repository,
cache, filesystem, or network access with the stable diagnostic
`component.identity.namespace.required`. The compatibility promise is limited
to two-argument source construction. CID-04D does not promise the old binary
ABI, `Product2`, two-field pattern matching, `copy`, or `unapply` behavior.
Existing external two-argument call sites in Textus repositories are read-only
CID-08 migration targets and are not edited by this Slice.

Canonical admission produces the exact shared projections:

- dependency key `<qualified-id>:<release>`;
- Maven release key `<namespace>:<artifact>:<release>`;
- CAR filename `<artifact>-<release>.car`;
- repository path `<group-path>/<artifact>/<release>/<filename>`;
- cache path `<group-path>/<artifact>/<release>/<filename>`.

Invalid namespace, local ID, or release returns the shared stable diagnostic.
Missing resolution evidence names the canonical dependency key.

## Local, file, HTTP, and cache resolution

For every repository entry, `CarDependencyResolver` derives one canonical
repository-relative path through the shared coordinate. Plain local roots and
`file:` roots resolve that exact path. HTTP and HTTPS append the same path to
the configured root and never use a filename-only or artifact-only fallback.

Resolution preserves repository order and stops at the first admitted hit.
It must not materialize or contact later candidates after success. Existing
HTTP/HTTPS SNAPSHOT suppression remains unchanged. Release downloads use a
coordinate-qualified cache destination, write through a sibling temporary
file, replace the final cache entry only after a complete copy, and clean the
temporary on failure. A present cache entry is returned without contacting the
source, proving source-stop/offline reuse at the focused resolver boundary.

Two coordinates such as `org.alpha.textus/Shared/0.6.0` and
`org.beta.textus/Shared/0.6.0` may have the same human filename
`textus-shared-0.6.0.car`; their group paths keep repository and cache bytes
distinct.

Checksum admission is not added here. CID-04C owns publication integrity and
CID-04E owns the CNCF repository/index consumer boundary.

## Runtime extraction and Cozy bridge

CAR runtime extraction uses the parent directory of the shared cache-relative
path as its output root:

    <output>/<group-path>/<artifact>/<release>

It must not extract below bare local ID or filename. Equal filenames from two
namespaces therefore retain distinct `component/main.jar` and `lib` evidence.

The Cozy component-API dependency bridge keeps its v1 request envelope but
emits exactly one production-built four-field value per dependency:

    namespace<TAB>localId<TAB>version<TAB>absoluteArchivePath

A package-private pure argument builder is the tested authority for this
payload. A legacy two-argument dependency fails namespace admission and never
emits a three-field compatibility payload.

Delegated generation settings change from `component.module` to the canonical
`component.namespace` and `component.id`, retaining `component.version`.
Those settings come from admitted `project.namespace`, `project.id`, and the
effective project version; module or publication names are not fallbacks.

## CAR publication wiring

`cozyBuildCar`, `cozyPublishCar`, `cozyPublishLocalCar`, and
`cozyDistributeCar` derive the CAR transport artifact, filename, release, and
destination from one admitted project release coordinate. The build archive
uses the canonical CAR filename. Publish bridge `--name` is the derived Maven
artifact and `--version` is the admitted release, matching CID-04C's transport
validation. Task results and log destinations use the shared
`carRepositoryRelativePath` below the chosen warehouse.

`cozyPublicationName`, `moduleName`, the input archive name, and compatibility
metadata cannot replace canonical project identity. A small package-private
pure publication projection is exercised by a new focused
`CozyCarPublicationCoordinateSpec`.

SAR build, publish, local publish, distribution, catalog schema, paths, and
transport behavior remain byte-for-byte semantically unchanged. Shared helper
changes must carry an explicit SAR preservation assertion.

## E9 activation

Only the E9 namespace-retention `pendingUntilFixed` leaf is activated.
`CarCoordinateContractScenarioSpi.NamespaceRetention` returns exact:

- dependency keys for both canonical coordinates;
- repository-relative paths for both coordinates;
- cache-relative paths for both coordinates;
- the equal human CAR filenames.

The scenario uses `org.alpha.textus/Shared` and `org.beta.textus/Shared` at
`0.6.0-SNAPSHOT`. It proves distinct qualified keys and paths with the same
filename. Its legacy assertion changes to prove that two-argument construction
is retained but resolution fails with `component.identity.namespace.required`.
E8 and E10 behavior and pending state remain unchanged. The E9 provenance tag
is corrected to CID-04D.

## Source ownership

The implementation worker may change only the necessary subset of:

- `src/main/scala/org/goldenport/cozy/CarDependencyResolver.scala`;
- `src/main/scala/org/goldenport/cozy/CarComponentIdentityAdapter.scala`;
- `src/main/scala/org/goldenport/cozy/CarRuntimeClasspathResolver.scala`;
- `src/main/scala/org/goldenport/cozy/CozyPlugin.scala`;
- `src/main/scala/org/goldenport/cozy/CarCoordinateContractScenarioSpi.scala`;
- focused resolver, runtime classpath, bridge, delegated-generation,
  publication-coordinate, and Phase 56 contract specs named below.

No collaborator API, Cozy, CNCF runtime, external Textus build, scripted
fixture, repository publication, or version file is editable in CID-04D. New
shared projection requirements return to PLAN.

## Executable specification

Focused sbt-cozy specifications prove:

1. canonical plain-local and `file:` lookup uses exact namespace-qualified
   paths and short-circuits after the first hit;
2. loopback HTTP release retrieval writes two distinct canonical caches for
   equal filenames, and source-stop cache hits perform no HTTP access;
3. legacy dependency, invalid coordinate, missing canonical key, failed
   download cleanup, and SNAPSHOT remote suppression use stable behavior;
4. runtime extraction keeps two equal filenames in distinct namespace roots
   with their original bytes;
5. the production bridge emits exactly four fields and rejects a legacy value
   before argument emission;
6. delegated generation emits canonical namespace, ID, and version settings;
7. CAR build/publish/local/distribute projections use the exact shared
   filename and repository path while SAR behavior is unchanged;
8. only E9 is promoted and reports exact dependency, repository, cache, and
   filename evidence.

The focused serialized SBT invocation is one `testOnly` containing:

    org.goldenport.cozy.Phase56CarCoordinateContractSpec
    org.goldenport.cozy.CarDependencyResolverSpec
    org.goldenport.cozy.CarRuntimeClasspathResolverSpec
    org.goldenport.cozy.ComponentApiDependencyResolutionSpec
    org.goldenport.cozy.CozyDelegatedGeneratorSpec
    org.goldenport.cozy.CozyCarPublicationCoordinateSpec

An existing focused plugin/publication spec may be added only when required to
exercise an edited production task boundary. `testOnly` supplies compile
coverage; a duplicate standalone `Test/compile` is not run. The HTTP case uses
`127.0.0.1` on an ephemeral port and stops its server in `finally`. If sandbox
bind is rejected, the exact focused invocation is rerun through the approved
escalated command-runner boundary rather than replaced by a filesystem test.

After focused validation, run `git diff --check` in sbt-cozy, collaborator API,
Cozy, and the Phase repository. No repository-wide suite, scripted fixture,
Step commit, publish, or external Textus validation runs in this Slice.

## Deferred Step acceptance and non-goals

The Step scripted fixture
`sbt-cozy/src/sbt-test/cozy/namespace-qualified-car-repository` is created and
run only after both CID-04D and CID-04E are accepted. It owns the end-to-end
publication, remote retrieval, source-stop offline reuse, CNCF consumer, and
transitive traversal evidence. CID-04D must not pre-implement or claim it.

Additional non-goals are:

- CNCF v2 index parsing and standard repository/cache consumption: CID-04E;
- transitive CNCF dependency traversal and the final Step fixture;
- checksum verification at the sbt-cozy resolver boundary;
- migration of external Textus two-argument dependency call sites: CID-08;
- legacy descriptor/index/dependency inference or aliases: CID-06;
- runtime `Component.Core`, instance identity, routing, Help/Admin, and
  diagnostics: CID-05;
- repository-wide suites, publishing, pushing, or deployment.
