# Component Dependency Loading

## Summary

Component CARs are lightweight by default. A CAR contains `component/main.jar`
and component resources, while runtime dependencies are resolved by CNCF from
`component-dependencies.yaml` when present.

## Manifest

`component-dependencies.yaml` is located at the CAR root.

```yaml
dependencies:
  provided:
    - org.goldenport:goldenport-cncf_3:0.4.8-SNAPSHOT
  shared:
    - org.postgresql:postgresql:42.7.3
  local:
    - com.example:legacy-driver:1.2.0
  repositories:
    - maven-central
    - https://www.simplemodeling.org/repository/maven
```

- `provided`: supplied by CNCF runtime; not resolved or added by component loading.
- `shared`: resolved into the CNCF shared dependency pool and reused across components.
- `local`: resolved into the component-local classloader and can override shared dependencies.
- `repositories`: additional repositories used by the resolver.

`runtime` is intentionally not used because the scope is ambiguous.

## Classloading

The CAR component scan target remains `component/main.jar` only. Dependency jars
are never scanned for component definitions.

Classloader shape:

```text
CNCF runtime classloader
  -> AssemblyApiClassLoader
    -> SharedDependencyClassLoader
      -> ComponentLocalFirstClassLoader
```

The assembly API loader is created before component instantiation. It reads
contract-only API JARs declared by active and dependency CAR metadata and owns
one class identity for each generated component API package. API JARs are
metadata inputs and are never component factory scan targets.

`org.goldenport.*`, `org.simplemodeling.model.*`, `cats.*`, `io.circe.*`,
`scala.*`, and JDK packages are parent-first because CNCF public/internal DSL
signatures expose those runtime ABI types. Generated component API packages
are also parent-first through the assembly API loader. Generated application
implementation classes such as `org.simplemodeling.textus.*.impl.*` remain
component-local so CAR implementations can evolve independently. Other
component dependency classes are local-first in the component-local loader.

The component-local loader remains available for the runtime lifetime of the
component. Factory discovery must not close it because component operations may
load declared `local` dependencies after startup.

CAR root `lib/` remains supported for libraries that are not published to Maven-like
repositories, such as commercial, internal, or temporary jars. These embedded
jars are treated as component-local classpath entries.

## Component API Dependencies

A consumer declares exact dependent CAR coordinates through sbt-cozy:

```scala
cozyCarDependencies += CarDependency("textus-scraper", "0.1.0-SNAPSHOT")
```

The consumer CML declares the required generated component API. sbt-cozy
matches that requirement against `component-api-descriptor.json` in each
declared CAR and adds only the provider's contract API JAR to consumer
compilation. Provider `component/main.jar` and implementation packages are not
consumer libraries.

For local SNAPSHOT development, publish the provider CAR first:

```bash
cd ../textus-scraper
sbt --batch publishLocal
```

The local publication must contain both `component-api-descriptor.json` and the
declared `spi/*-api.jar`. Missing required APIs, missing declared API JARs,
incompatible coordinates, duplicate API classes with different content, and
ABI hash conflicts fail deterministically. CNCF does not infer a compatible API
from provider source directories or implementation JARs.

Production code must use only `cozyCarDependencies`. A test project may retain
a source dependency for provider implementation fixtures, but that test-only
edge is not packaging or deployment evidence.

## Development And Packaged Parity

Development components publish the same component API metadata under
`target/cozy`. CNCF reads that metadata for an explicitly active development
target and builds the same assembly API parent used for packaged CARs.
Dependency search repositories contribute API metadata during preflight but do
not become active component discovery roots.

Standard verification must use the launcher and normal repository resolution,
for example `cncf . server`. A flattened classpath containing consumer and
provider implementation classes can be useful for a unit fixture, but it does
not prove CAR dependency resolution or classloader identity.

## Conflict Policy

The v1 shared dependency version conflict policy is temporary and fail-fast. CNCF
resolves the complete shared coordinate set together, then rejects direct or
transitive shared-module version conflicts. If two components request or resolve
different versions of the same shared module, startup fails deterministically and
the version-specific dependency must be declared as `local`.

This is not the production target policy. Production dependency mediation,
compatibility validation, and administrator-selected conflict handling remain
future work.
