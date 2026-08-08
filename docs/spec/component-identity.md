# Component Identity Specification

status: normative
phase: 56

## Requirements

1. `namespace + local ID` MUST be the sole Component identity authority.
2. A canonical Component ID MUST be namespace-qualified and validated exactly.
3. Release version, title, display name, artifact, package, class, path, and
   route MUST NOT participate in Component identity equality.
4. `Component.Core`, `ComponentId`, and `ComponentInstanceId` MUST retain the
   same exact canonical identity.
5. Every materialized derived projection MUST equal the shared deterministic
   projection or admission MUST fail.
6. Repository, cache, dependency, and Maven keys MUST retain namespace
   isolation.
7. Exact qualified selection MUST take precedence over compatibility aliases.
8. A unique legacy alias MAY adapt only to an already admitted canonical
   candidate and MUST emit a compatibility notice.
9. An ambiguous, unknown, malformed, or conflicting alias MUST fail closed.
10. Artifact names, Web paths, and subsystem names MAY remain stable
    compatibility or presentation surfaces but MUST NOT author Component ID.
11. An exact registered legacy release MAY defer migration; a greater release
    with legacy identity MUST fail lint as migration-required.
12. No migration step in Phase 56 changes a release from or to SNAPSHOT merely
    to satisfy identity migration.

## Author guidance

New and migrated CAR projects author only:

```yaml
project:
  namespace: org.simplemodeling.textus
  id: UserAccount
  component:
    version: 0.6.0-SNAPSHOT
    displayName: Textus User Account
```

Build, descriptor, manifest, generated Scala, CAR, Maven, repository, cache,
and runtime projections are derived or verified. Authors must not add a
source-managed descriptor when CML model metadata generates the descriptor.

Downstream assembly component entries use exact `namespace`, `id`, and
`version`. Runtime selectors use the qualified ID when identity is required.
Artifact selection may continue to use the artifact name, and admitted Web
aliases may remain unchanged.

## Compatibility ledger

| Surface | Canonical behavior | Compatibility owner / removal gate |
| --- | --- | --- |
| descriptor and assembly | exact qualified identity | CNCF adapter; remove after all admitted legacy descriptors migrate |
| runtime selector and Help/Meta | exact qualified identity first | CNCF adapter; retain while registered aliases are in supported deployments |
| Web path | canonical runtime ownership with stable path aliases | Web projection owner; route removal requires separate compatibility policy |
| CAR artifact/repository | namespace-qualified coordinate with derived artifact filename | Cozy/sbt-cozy; artifact spelling remains distribution metadata |
| deferred released CAR | exact registry release only | owning CAR migrates at its next development version |

The deferred release owners remain `textus-corpus`, `textus-experiment`,
`textus-georesolver`, and `textus-sanpomap`. Their released versions remain
unchanged by Phase 56.

## Closure ledger

- CID-05: `d5d3c5bb71962d93898ac8b1ddbcac7d9c8cfe83`.
- CID-06: `6afab962ccd33431e6e2944c8d9191295d1a7f38`.
- CID-07 CNCF contract: `34cb4483813a3d276edab2d5e604804087a96c08`.
- CID-07 ecosystem: 19 repository-local commits, 14 canonical SNAPSHOT CARs,
  four exact release deferrals, and no accepted final lint FAIL.

Phase 57 and Phase 58 consume this specification as closed input. Phase full
validation remains a separate Phase 56 release gate.
