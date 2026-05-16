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
  -> SharedDependencyClassLoader
    -> ComponentLocalFirstClassLoader
```

`org.goldenport.*`, `org.simplemodeling.model.*`, `scala.*`, and JDK packages
are parent-first to preserve CNCF runtime ABI identity. Generated application
classes such as `org.simplemodeling.textus.*` remain component-local so a CAR
can override stale classes on the parent runtime classpath. Other component
dependency classes are local-first in the component-local loader.

CAR root `lib/` remains supported for libraries that are not published to Maven-like
repositories, such as commercial, internal, or temporary jars. These embedded
jars are treated as component-local classpath entries.

## Conflict Policy

The v1 shared dependency version conflict policy is temporary and fail-fast. CNCF
resolves the complete shared coordinate set together, then rejects direct or
transitive shared-module version conflicts. If two components request or resolve
different versions of the same shared module, startup fails deterministically and
the version-specific dependency must be declared as `local`.

This is not the production target policy. Production dependency mediation,
compatibility validation, and administrator-selected conflict handling remain
future work.
