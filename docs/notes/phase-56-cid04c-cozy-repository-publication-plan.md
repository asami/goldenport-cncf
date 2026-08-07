# Phase 56 CID-04C - Cozy Repository Publication Plan

status=implementation plan frozen
date=2026-08-07
phase=[Phase 56](../phase/phase-56.md)
checklist=[Phase 56 checklist](../phase/phase-56-checklist.md)
parent=[CID-04 coordinate plan](phase-56-cid04-car-maven-repository-coordinate-plan.md)
slice=CID-04C - Cozy namespace-qualified CAR publication, catalog, index, Maven metadata, and integrity

## Goal and authority

CID-04C makes the shared `ComponentReleaseCoordinate` the sole authority for
every CAR publication destination and persisted identity produced by Cozy.
The admitted project fields `project.namespace`, `project.id`, and
`project.component.version` produce the coordinate. `--name` and `--version`
remain transport arguments only and must equal the derived Maven artifact ID
and release. Project name, component display name, CML filename, archive input
filename, Scala package, and catalog filename are never identity fallbacks.

The collaborator API worktree at
`/Users/asami/src/dev2026/cncf-collaborator-api` remains the projection
authority. Cozy extends its package-private adapter only to expose shared
Maven group, release-key, repository, catalog, filename, and integrity
projections. It must not reconstruct those values by splitting or joining
identity strings independently.

CID-04B's canonical component descriptor v3 and ABI manifest v2 are required
publication inputs. The prebuilt-CAR admission reader is updated in this Slice
to verify their exact shared coordinate; it does not add legacy descriptor
admission.

## Canonical CAR publication layout

For coordinate namespace `org.alpha.textus`, local ID `Shared`, and release
`0.6.0`, the derived artifact is `textus-shared`. Below a chosen warehouse,
Cozy writes these exact paths:

    repository/car/org/alpha/textus/textus-shared/0.6.0/textus-shared-0.6.0.car
    repository/car/org/alpha/textus/textus-shared/0.6.0/textus-shared-0.6.0.car.sha256
    repository/car/org/alpha/textus/textus-shared/maven-metadata.xml
    repository/catalog/car/org/alpha/textus/textus-shared.yaml
    repository/catalog/car/org/alpha/textus/textus-shared.cml
    repository/catalog/car/org/alpha/textus/textus-shared.model-metadata.json
    repository/catalog/car/org/alpha/textus/textus-shared.model-metadata.yaml

The project-owned source catalog is:

    src/main/catalog/car/org/alpha/textus/textus-shared.yaml

The archive path below `repository/car`, the catalog path below
`repository/catalog`, and the archive filename come directly from the shared
ABI. The SHA-256 sidecar contains the bare lowercase 64-hex digest followed by
one newline. Catalog, sidecar, and the bytes read back from the published CAR
must agree before the index is replaced.

Two coordinates may produce the same artifact and CAR filename when their
namespaces have the same final segment. Their full group paths keep every
archive, metadata, catalog, sidecar, and index identity distinct.

## Repository artifact catalog v2

Canonical CAR catalogs use string `schemaVersion: 2` and carry these root
identity fields:

    schemaVersion: 2
    kind: car
    namespace: org.alpha.textus
    id: Shared
    artifactId: textus-shared

`artifactId` is a verified derived projection, not an authored identity.
Selectors, status, aliases, tags, terms, runtime compatibility, and release
history retain their current meanings. Every release entry carries the exact
shared repository-relative `file`, a bare lowercase `checksum.sha256`, and an
`integrityKey` equal to:

    org.alpha.textus:textus-shared:0.6.0@sha256:<digest>

Loading or merging a v2 CAR catalog re-admits namespace, ID, and release,
then verifies artifact ID, file, checksum format, and integrity key through
the shared coordinate. A canonical publisher does not silently upgrade or
reinterpret a v1 CAR catalog; CID-06 owns legacy CAR catalog decoding.

SAR publication and catalog schema v1 remain unchanged. The shared catalog
model may represent both forms, but schema v2 identity requirements apply to
CAR only and schema v1 remains admitted for SAR only.

## Component repository index v2

The public index remains at `repository/catalog/index.json` and uses
`schemaVersion = cncf.component-repository-index.v2`. Each CAR entry contains:

- `kind`, exact `namespace`, exact local `id`, verified `artifactId`;
- namespace-bearing `catalog`, relative to `repository/catalog`;
- existing status and recommended/latest selectors.

CAR index identity is `(kind, namespace, id)`, not `(kind, artifactId)`.
Deterministic ordering uses that identity. Index validation loads the detailed
catalog and verifies exact identity, derived artifact, catalog path, status,
selectors, archive path, SHA-256 sidecar, and published archive digest before
admitting the entry.

The index remains shared with SAR. Existing SAR entries retain their
kind-plus-artifact identity and omit component namespace/ID because SAR is not
a Component. Writing the v2 index preserves unrelated valid SAR entries while
making namespace and ID mandatory for every CAR entry.

## Maven metadata, integrity, and publication transaction

CAR Maven metadata uses the exact component namespace as `groupId` and the
shared derived artifact as `artifactId`. Version selectors and timestamps
retain their existing catalog-driven behavior. The metadata destination uses
the same group path as the archive.

Publication follows this order under the existing process monitor and
warehouse file lock:

1. admit project and archive coordinates and validate the existing index and
   detailed catalogs before changing warehouse output;
2. calculate the source snapshot digest and prepare the complete catalog,
   metadata, index, checksum sidecar, and CML sidecar payloads;
3. write each destination through a sibling temporary file and atomic replace,
   with the established fallback only where `ATOMIC_MOVE` is unsupported;
4. read back the archive digest and checksum record, validate the complete
   candidate index, and replace `index.json` last.

A mismatch fails before replacing any existing coordinate destination.
Republishing the same coordinate and bytes with the same explicit
`--published-at` is idempotent: it creates no duplicate release or index entry
and renders byte-identical catalog, metadata, checksum, and index content.
Republishing different admitted bytes replaces that release and all of its
integrity evidence coherently; older releases and unrelated coordinates are
preserved.

Stable diagnostic prefixes are:

- `component.release-coordinate.mismatch` for namespace/ID/release conflict;
- `component.release-coordinate.projection-mismatch` for transport name,
  artifact, filename, catalog path, Maven coordinate, or integrity projection;
- `repository.artifact.catalog.schema.unsupported` for non-v2 CAR catalog
  input at the canonical publisher boundary;
- `component.repository-index.schema.unsupported` for a non-v2 canonical
  index;
- `component.repository.integrity.mismatch` for archive, catalog digest,
  integrity key, or `.sha256` sidecar disagreement.

Diagnostics include source, field where applicable, expected, and actual.

## Source ownership

The implementation worker may change only the necessary subset of:

- `src/main/scala/cozy/archive/CozyComponentReleaseCoordinateCodec.scala`;
- `src/main/scala/cozy/archive/CozyCarPublisher.scala`;
- `src/main/scala/cozy/archive/RepositoryArtifactPublisher.scala`;
- `src/main/scala/cozy/archive/RepositoryArtifactCatalog.scala`;
- `src/main/scala/cozy/archive/RepositoryArtifactMavenMetadata.scala`;
- `src/main/scala/cozy/archive/ComponentRepositoryIndex.scala`;
- `src/main/scala/cozy/archive/CozyCarRuntimeManifest.scala`, only for
  canonical prebuilt-CAR coordinate admission;
- public Cozy catalog wrappers/type aliases only as required by the catalog
  model change;
- focused Cozy publisher, catalog, index, and canonical publication specs,
  plus SAR preservation assertions required by the shared publisher/index.

No collaborator API, sbt-cozy, CNCF runtime, BoK publication, media
publication, or external Textus file is editable in CID-04C. Evidence that
requires a new shared ABI projection returns to PLAN.

## Executable specification

Focused Cozy specifications prove:

1. one canonical project publishes exact group/artifact/release archive,
   Maven metadata, source/public catalog, CML sidecar, checksum sidecar, and
   v2 index paths;
2. two coordinates with equal artifacts and filenames in different full
   namespaces coexist in one warehouse without overwriting any output or
   index entry;
3. v2 catalog and index parse/render deterministically and reject unknown,
   missing, legacy CAR, mismatched, duplicate, or traversal-bearing identity
   and projection fields;
4. Maven group/artifact and all catalog/index paths equal the shared
   coordinate projections;
5. the published bytes, catalog digest, integrity key, and checksum sidecar
   agree exactly, and tampering is rejected;
6. deterministic republish replaces only the selected release and keeps
   unrelated versions, namespaces, and SAR entries;
7. name, release, descriptor, ABI, existing catalog, and existing index
   disagreement fail before warehouse mutation;
8. SAR schema-v1 publication and preservation in the shared v2 index do not
   regress.

Focused validation order is:

1. collaborator API serialized `publishLocal` only if its dirty coordinate
   ABI is not already current in the local dependency cache;
2. the new Phase 56 namespace-qualified CAR publication spec plus updated
   `CozyCarPublisherSpec`, `RepositoryArtifactCatalogSpec`, and
   `ComponentRepositoryIndexSpec` selectors;
3. the focused SAR publisher/index preservation selector;
4. Cozy `Test/compile`;
5. `git diff --check` in Cozy, collaborator API, and the Phase repository.

No repository-wide suite or Step commit runs in this Slice.

## Non-goals

- Legacy CAR descriptor, catalog, index, or dependency decoding: CID-06.
- sbt-cozy `CarDependency`, bridge production, local/file/HTTP retrieval,
  cache layout, publication task wiring, or E9 activation: CID-04D.
- CNCF repository/index readers and standard resolution: CID-04E.
- Runtime `Component.Core`, instance IDs, assembly/routing selectors, Help or
  Admin identity, or runtime diagnostics: CID-05.
- CAR lint cohort migration: CID-07.
- External Textus call-site migration, publishing, pushing, or deployment.
