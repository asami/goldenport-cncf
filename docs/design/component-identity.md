# Component Identity

status: normative
phase: 56

## Authority

A Component has exactly one identity authority: an exact validated
`namespace + local ID` pair. The canonical external spelling is the qualified
Component ID:

```text
org.simplemodeling.textus.UserAccount
```

Release version and display metadata are not identity. A title, localized
display name, CAR artifact, repository path, generated class, JVM package,
Web path, or subsystem name never becomes a second identity source.

## Typed model

`ComponentNamespace`, `ComponentLocalId`, `ComponentId`, and
`ComponentInstanceId` preserve the shared identity contract. Namespace and
local-ID validation is exact and fail-closed. Runtime Core identity,
descriptor identity, default instance identity, routing, Help/Meta/Admin
projection, repository coordinate, and cache identity must agree with the
same admitted `ComponentId`.

Distinct namespaces with the same local ID remain distinct. A unique scoped
legacy alias may adapt to an already admitted canonical candidate and emits a
compatibility notice. An ambiguous or unknown alias fails with an actionable
diagnostic; compatibility never infers a namespace.

## Deterministic projections

The shared projection contract derives Maven group/artifact, JVM package,
generated class, path, CAR filename, repository key, and cache key. Derived
values are verified, not independently authored. Namespace-qualified
repository and Maven coordinates retain isolation even when two releases have
the same human-facing filename.

## Compatibility and migration

Artifact names such as `textus-user-account` and Web paths such as
`/web/textus-user-account` remain presentation, distribution, or compatibility
surfaces. They are not canonical Component IDs. Existing admitted Web routes
remain stable.

Legacy non-SNAPSHOT CARs are not rewritten. An exact registry release may use
the bounded deferred-release adapter. Once that artifact advances beyond the
registered release, legacy identity becomes migration-required. Lower,
malformed, or incomparable versions fail as inventory errors. Adapter notices
are removed only by their recorded future owner after the last registered
release no longer requires them.

## Successor phases

Phase 57 composition manifests reference the exact parent Component identity
without duplicating it. Phase 58 knowledge and Help resources use the same
qualified Component identity and may retain artifact/Web aliases only as
presentation metadata. Neither phase may reopen Phase 56 naming or projection
decisions.
