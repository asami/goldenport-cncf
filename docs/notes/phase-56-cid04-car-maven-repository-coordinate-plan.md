# Phase 56 CID-04 - CAR, Maven, and Repository Coordinate Plan

status=implementation plan frozen
date=2026-08-07
phase=[Phase 56](../phase/phase-56.md)
checklist=[Phase 56 checklist](../phase/phase-56-checklist.md)
authority=[CID-01 inventory and failing-first contract](phase-56-cid01-component-identity-inventory-and-failing-first-contract.md)
step=CID-04 - CAR, Maven, and repository coordinates
slices=CID-04A,CID-04B,CID-04C,CID-04D,CID-04E,CID-04F

## Goal and closure

CID-04 closes when one canonical `namespace + id` and independent release
version produce one verified CAR release coordinate across descriptors,
dependency declarations, Maven metadata, warehouse catalog/index entries,
repository paths, remote retrieval, local cache paths, and integrity records.
Two identities may intentionally have the same human-facing artifact and CAR
filename, but their dependency, repository, cache, catalog, index, and
integrity keys must remain distinct through their full namespaces.

## Repository boundary

Allowed update root is `/Users/asami/src`. The active repository set is:

1. `/Users/asami/src/dev2026/cncf-collaborator-api` - shared Java release
   coordinate projection ABI;
2. `/Users/asami/src/dev2025/cozy` - descriptor, component API, publisher,
   catalog, index, checksum, and bridge-side dependency codec producer;
3. `/Users/asami/src/dev2026/sbt-cozy` - public dependency declaration,
   repository/cache resolver, SBT publication wiring, and E9 executable owner;
4. `/Users/asami/src/dev2025/cloud-native-component-framework` - canonical
   repository index reader and standard repository/cache consumer.

`textus-art-scene` and `textus-semantic-integration-engine` contain existing
two-argument `CarDependency` call sites. They are read-only observations in
this Step and remain CID-08 ecosystem migration targets. CID-04 must preserve
their source compilation through an explicitly legacy/unqualified constructor,
but resolution of that value must fail closed with a namespace-required
diagnostic. CID-06 owns contextual alias admission; CID-04 must not guess a
namespace from artifact, organization, package, filename, or repository.

## Frozen coordinate shape

The shared release coordinate is composed from the admitted shared
`ComponentId`, its `ComponentIdentityProjection`, and an opaque validated
release token. Its exact projections are:

- qualified identity: `namespace + "." + exact local id`;
- dependency key: `qualified identity + ":" + release`;
- Maven group: exact namespace;
- artifact: final namespace segment plus `-` plus kebab local id;
- Maven release key: `group + ":" + artifact + ":" + release`;
- group path: namespace segments joined with `/`;
- repository/cache artifact path:
  `group-path/artifact/release/artifact-release.car`;
- catalog path: `car/group-path/artifact.yaml`;
- index identity: `kind + namespace + local id`, with artifact retained only
  as a verified derived projection;
- integrity key: the exact Maven release key plus the archive SHA-256;
- CAR filename: `artifact-release.car` and therefore permitted to be equal
  across distinct namespaces.

The repository root (`repository/car`) and cache root are external location
choices and are not part of identity. All relative paths are produced by the
shared ABI and must be traversal-free. No consumer may rebuild these fields by
tokenizing strings independently.

## Canonical serialized shapes

- Component descriptor canonical emission uses schema version 3 with exact
  `namespace`, `id`, and `version`; legacy root `name` and string `component`
  are not canonical inputs.
- Component API descriptor canonical emission uses
  `cncf.component-api.v2` and component `{namespace,id,version}`.
- CAR ABI manifest canonical emission uses `cozy.car.abi-manifest.v2` and the
  same component identity/release fields.
- Repository artifact catalog uses schema version 2 and carries
  `namespace`, `id`, verified `artifactId`, release file path, and SHA-256.
- Component repository index uses
  `cncf.component-repository-index.v2`; entries carry `kind`, `namespace`,
  `id`, verified `artifactId`, and namespace-bearing catalog path.
- SBT-to-Cozy dependency bridge values carry namespace, local id, version,
  and archive path as separate fields. A tab-delimited legacy three-field
  payload is rejected, not reinterpreted.

CID-06 owns bounded decoding of legacy descriptor, catalog, index, and bare
dependency spellings. CID-04 emits only canonical shapes and may retain an
explicit legacy source constructor solely to preserve downstream compilation;
that constructor never creates a canonical key.

## Slice ledger

### CID-04A - Shared release coordinate ABI

Extend `cncf-collaborator-api` with the one validated release-coordinate value
and repository/cache/catalog/integrity projections. Keep validation safe and
non-throwing with `require*` conveniences. Prove that two namespaces ending in
`textus` with local id `Shared` have one shared filename but distinct exact
keys and paths. This is a public Java ABI change and uses the public-contract
implementation/review profile.

### CID-04B - Cozy canonical descriptor and dependency codecs

Update archive/component API generation and validation to consume canonical
namespace, id, and version values and emit the frozen descriptor/API/ABI
schemas. Update the component API dependency bridge and assembly dependency
matching to compare exact qualified identity plus release. Reject any derived
or serialized disagreement with a stable diagnostic containing expected and
actual coordinates. Do not implement legacy alias admission.

The exact JSON shapes, four-field bridge payload, diagnostic codes, file
ownership, and focused executable specification are frozen in the
[CID-04B implementation plan](phase-56-cid04b-cozy-canonical-descriptor-codec-plan.md).

### CID-04C - Cozy repository producer, catalog, index, and integrity

Make CAR publication derive the warehouse artifact, catalog, metadata, index,
and sidecar destinations from the shared release coordinate. Catalog/index
identity becomes namespace-qualified; artifact and filename remain verified
derived values. Preserve atomic publication/index locking and checksum
verification. Test two equal filenames in one warehouse, deterministic
republish, mismatch rejection, and exact SHA-256 records.

### CID-04D - sbt-cozy dependency, repository, cache, and E9

Make canonical `CarDependency` carry namespace, local id, and version. Keep a
source-compatible explicitly legacy two-argument construction path whose
resolution fails with a namespace-required diagnostic. Update local/file/HTTP
resolution, cache destinations, SBT publication destinations, and bridge
payloads to use the shared coordinate. Activate the sole E9
`pendingUntilFixed` scenario and keep E8/E10 behavior unchanged.

### CID-04E - CNCF repository index and standard resolver

Adopt the v2 index and shared release-coordinate paths in CNCF repository and
cache resolution. Direct canonical APIs require qualified Component IDs;
unqualified values fail with the shared qualified-ID diagnostic. Do not migrate
runtime `Component.Core`, assembly/routing selectors, or compatibility aliases;
those remain CID-05/CID-06. Prove exact local, remote, cached-offline, and
same-filename namespace-isolated resolution.

### CID-04F - Acceptance ledger and hygiene persistence

CID-04F owns only reconciliation of the acceptance ledger and persistence of the
already accepted hygiene journal for CID-04 closure preparation. It makes no
code, API, schema, or behavior change and does not set CID-04 or Phase 56 to
closed.

The dependency-ordered Step commits are `cncf-collaborator-api`
`6493d29920c6db3d0ed2b810485901ec3192a2b1`, Cozy
`2d65321c5d9362cb7493d90aa7ebf34d03dbee90`, CNCF
`9371ab8c0349b9ad28b46c56982cd4c83e938912`, and sbt-cozy
`a84a91514c8b843da8524d06d61e33dc480ebf38`. Accepted Step validation ran in
that order: collaborator 22 tests (`18570-20260807T115729Z`) and
`publishLocal` (`18799-20260807T115752Z`); Cozy 17 suites/224 tests
(`19044-20260807T115822Z`) and `publishLocal` (`19473-20260807T115922Z`);
CNCF 3 suites/18 tests plus 4 expected ownership cancellations
(`19701-20260807T115947Z`) and `publishLocal` (`19997-20260807T120021Z`);
sbt-cozy 6 suites/53 tests (`20315-20260807T120059Z`); and Step scripted
`20534-20260807T120122Z`, 1/1 passed with PublisherProbe plus CncfProbe
online/offline. Every invocation had `sbt_exit=0`, `wrapper_exit=0`,
`lock=released`. This records accepted Step evidence only; repository-wide
full-suite completion remains Phase-release-only/pending.

## Executable acceptance matrix

| Evidence | Required behavior |
| --- | --- |
| API release-coordinate contract | Exact keys/paths, invalid release/path rejection, same filename with namespace-isolated keys. |
| Cozy descriptor/dependency specs | v3/v2 canonical schemas, exact qualified dependency matching, disagreement rejection. |
| Cozy publisher/catalog/index specs | Two colliding filenames coexist, v2 catalogs/index retain namespaces, checksum and republish are deterministic. |
| sbt-cozy E9 and resolver specs | Canonical local/file/HTTP lookup, distinct caches, stable diagnostics, sole E9 pending promoted. |
| CNCF repository/index specs | v2 parse/render, qualified local/remote resolution, offline cache reuse, bare-ID rejection. |
| Step scripted acceptance | Publish two same-filename CARs, retrieve both, stop the source, reuse both caches offline, and resolve a transitive dependency without key collapse. |

The scripted acceptance fixture will be
`sbt-cozy/src/sbt-test/cozy/namespace-qualified-car-repository`. It must use an
isolated warehouse/cache and deterministic loopback HTTP source for the remote
leg; if localhost bind requires sandbox escalation, the test is rerun through
the approved execution boundary rather than weakened to a filesystem-only
substitute.

Each Slice runs its focused executable specification and `Test/compile` only.
Repository-wide suites remain pending until Phase release.

## Non-goals

- Runtime `Component.Core`, instance identity, subsystem assembly, routing,
  Help/Admin selectors, and diagnostics migration: CID-05.
- Legacy descriptor/index/dependency alias admission and ambiguity policy:
  CID-06.
- Version-sensitive CAR lint and migration of the 18-record cohort: CID-07.
- Migration of external Textus build call sites and ecosystem regression:
  CID-08.
- Publishing, pushing, deploying, or rewriting an existing release.

## Review and implementation profiles

- CID-04A: public ABI implementation with Terra high; full Sol high review.
- CID-04B/C: serialization and repository-persistence implementation with
  Terra high; full Sol high review at each frozen Slice boundary.
- CID-04D: public SBT API plus HTTP/cache implementation with Terra high; full
  Sol high review.
- CID-04E: repository/index/runtime-boundary implementation with Terra high;
  full Sol high review.
- Any admitted bounded fix uses the frozen finding-to-fix manifest and an
  independent Luna focused re-review selected by complexity.

No implementation worker may change the repository set, canonical shapes,
legacy boundary, or Slice ownership frozen here. Such evidence returns to a
new parent PLAN rather than being decided during implementation.
