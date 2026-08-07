# Phase 56 CID-04B - Cozy Canonical Descriptor and Dependency Codec Plan

status=implementation plan frozen
date=2026-08-07
phase=[Phase 56](../phase/phase-56.md)
checklist=[Phase 56 checklist](../phase/phase-56-checklist.md)
parent=[CID-04 coordinate plan](phase-56-cid04-car-maven-repository-coordinate-plan.md)
slice=CID-04B - Cozy canonical component descriptor, API/ABI descriptor, and dependency codecs

## Goal and authority

CID-04B makes Cozy consume one admitted shared ComponentReleaseCoordinate and
use it as the sole identity/release authority for CAR component descriptors,
component API descriptors, ABI manifests, component API dependency bridge
values, and assembly dependency matching.

The only authoring inputs are project.namespace, project.id, and
project.component.version. project.name, project.component.name, the package
--name, Maven artifact names, CAR filenames, Scala packages, and display
metadata are never fallback identity sources. Package --name and --version
remain transport inputs only long enough to be verified against the shared
coordinate; disagreement fails before archive output.

The collaborator API worktree at
/Users/asami/src/dev2026/cncf-collaborator-api is the dependency authority.
Cozy validation first publishes its dirty 0.2.0-SNAPSHOT through the
serialized SBT boundary; Cozy does not duplicate release, artifact, filename,
or repository projection logic.

## Canonical JSON shapes

All three documents reuse this exact component coordinate object:

    {
      "namespace": "org.simplemodeling.textus",
      "id": "UserAccount",
      "version": "0.6.0-SNAPSHOT"
    }

### Component descriptor v3

Canonical component-descriptor.json is:

    {
      "schemaVersion": 3,
      "component": {
        "namespace": "org.simplemodeling.textus",
        "id": "UserAccount",
        "version": "0.6.0-SNAPSHOT"
      }
    }

componentStyle, componentlets, entities, extensions, and config remain
non-authoritative payload fields beside component. Canonical emission has no
root name, no root version, no string component, and no component.name.
Generated, source-managed, and structured-override routes all validate schema
3 and the exact shared coordinate before packaging.

### Component API descriptor v2

Canonical component-api-descriptor.json uses schemaVersion =
cncf.component-api.v2 and the same component object. Provided and required API
surface fields remain unchanged. Every provided release equals
component.version, and its only admitted API artifact path is
spi/<maven-artifact-id>-api.jar, derived from the shared coordinate.

Model generation receives --component-namespace, --component-id, and
--component-version. The Cozy SBT bridge reads the separate
component.namespace, component.id, and component.version settings. Legacy
component.module does not participate in canonical generation; sbt-cozy
production of the new settings remains CID-04D.

### CAR ABI manifest v2

Canonical abi-manifest.json uses format = cozy.car.abi-manifest.v2, the same
top-level component object, and retains abi.version = 1 because that number
describes the ABI surface, not the document coordinate schema. The legacy car
{name,version} object is not emitted.

The primary exported component is {namespace,id}. ABI dependencies are
{namespace,id,abiRange} and are merged by exact qualified identity. Two equal
qualified identities with different ranges fail; equal local IDs in different
namespaces remain distinct. packaging.car.abi.dependencies therefore requires
separate namespace, id, and abiRange fields.

CID-07 owns version-sensitive lint migration for retained v1 ABI baselines;
CID-04B does not reinterpret v1 as v2.

## Shared Cozy codec boundary

Introduce one package-private Cozy adapter around the collaborator API. It:

1. admits project metadata into ComponentReleaseCoordinate;
2. renders and reads the exact canonical component JSON object;
3. verifies expected versus serialized coordinates by exact
   qualifiedId + ":" + release;
4. exposes the shared artifact/API-path projections without independently
   tokenizing namespace, local ID, or release;
5. converts shared safe validation failures into Cozy argument faults while
   retaining their stable component.identity.* codes.

Coordinate disagreement uses the stable diagnostic prefix
component.release-coordinate.mismatch and includes source, expected, and
actual. A derived field disagreement uses
component.release-coordinate.projection-mismatch and includes source, field,
expected, and actual. Unsupported canonical schemas use these stable codes:

- component.descriptor.schema.unsupported;
- component.api.schema.unsupported;
- car.abi.schema.unsupported.

No codec guesses namespace from artifact, module, archive name, package, or
assembly membership.

## Component API dependency bridge

The Cozy resolver accepts exactly four tab-separated fields in this order:

1. namespace;
2. exact local ID;
3. release version;
4. absolute or project-resolved CAR archive path.

The existing three-field name<TAB>version<TAB>archive payload fails with
component.api.dependency.payload.v2.required; it is not reinterpreted. The
resolver validates each dependency CAR's v2 component API descriptor against
the declared shared coordinate, validates the exact derived API artifact path,
and reports expected and actual dependency keys on mismatch.

Assembly dependency entries match exact namespace, id, and version. Missing or
disagreeing entries report the exact dependency key. Extracted API JARs are
namespace-isolated below
group-path/maven-artifact-id/release/api-jar-name; equal final namespace
segments and local IDs cannot overwrite one another.

The sbt-cozy producer and public CarDependency constructor remain CID-04D.
CID-04B changes only Cozy's admitted consumer shape and executable codec
contract.

## Source ownership

The implementation worker may change only the necessary subset of:

- src/main/scala/cozy/archive/CozyComponentReleaseCoordinateCodec.scala (new);
- src/main/scala/cozy/modeler/ComponentApiDescriptor.scala;
- src/main/scala/cozy/Cozy.scala;
- src/main/scala/cozy/runtime/CozySbtBridge.scala;
- src/main/scala/cozy/archive/CozyArchivePackager.scala;
- src/main/scala/cozy/archive/CozyCarAbiManifest.scala;
- src/main/scala/cozy/archive/ComponentApiDependencyResolver.scala;
- src/main/scala/cozy/archive/ComponentApiJarPackager.scala;
- src/main/scala/cozy/archive/CozyCarRuntimeManifest.scala and
  CozyDevelopmentRuntimeManifest.scala only where descriptor/ABI coordinate
  verification requires canonical v3/v2 awareness;
- focused executable specifications for these exact boundaries.

CozyCarPublisher, repository catalogs/indexes, Maven metadata, checksum
sidecars, warehouse paths, and publication locks remain CID-04C. sbt-cozy and
CNCF files are not editable in CID-04B.

## Executable specification

Focused Cozy specifications prove:

1. canonical project metadata emits exact component descriptor v3, component
   API v2, and ABI manifest v2 coordinate objects;
2. all emitted documents omit legacy identity fields and derive the expected
   Maven artifact/API path;
3. source-managed or override documents with a different namespace, ID, or
   release fail with expected and actual dependency keys;
4. missing or malformed canonical fields retain shared validation codes;
5. ABI dependency merging distinguishes equal local IDs across namespaces and
   rejects one qualified identity with conflicting ranges;
6. the four-field dependency payload resolves a matching provider and exact
   assembly entry, while a three-field payload fails closed;
7. two dependencies with equal artifact/API filenames in different namespaces
   retain distinct keys and extraction paths;
8. API descriptor artifact-path disagreement is rejected by both the API JAR
   packager and CAR packager boundaries;
9. the Cozy SBT bridge forwards namespace, ID, and version as separate modeler
   arguments and never forwards component.module as canonical identity.

Focused validation order is:

1. collaborator API serialized publishLocal;
2. the new Phase 56 canonical descriptor/codec spec plus updated component API
   dependency, API JAR, and bridge specs;
3. Cozy Test/compile;
4. git diff --check in both dirty repositories.

No repository-wide suite runs in this Slice.

## Non-goals

- Legacy descriptor/API/ABI decoding or contextual alias admission: CID-06.
- ABI baseline lint migration and the retained CAR cohort: CID-07.
- Publisher, warehouse, repository, catalog, index, Maven metadata, checksum,
  or locking changes: CID-04C.
- sbt-cozy CarDependency, bridge production, repository/cache resolution, or
  E9 activation: CID-04D.
- Runtime Component.Core, assembly selectors, subsystem routing, or component
  display-name migration: CID-05.
- External Textus call-site migration: CID-08.
