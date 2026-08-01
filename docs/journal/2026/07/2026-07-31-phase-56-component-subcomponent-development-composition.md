# Phase 56 Component SubComponent and Development Composition Decision

Date: 2026-07-31

Status: planning decision record

## Context

The existing Phase 56 plan treated a Component-specific Documentation
Component as an optional physical split for large manuals, Scaladoc, source,
and media. It also admitted a source-omitted commercial profile.

The follow-up discussion strengthened the definition of Component:

- a Component must be logically self-describing for users, operators,
  developers, and AI;
- Documentation and admin projections must make every Component-owned element
  discoverable;
- putting every manual, rendered artifact, and source file into the runtime
  CAR is not always operationally practical; and
- development AI requires exact documentation, models, source, examples,
  tests, and provenance as part of its normal context.

## Decision

One Component is one logical, versioned release. It may be physically composed
from:

```text
primary execution CAR
  + Documentation SubComponent
  + SourceCode SubComponent
```

Documentation and SourceCode SubComponents use Component Repository identity,
integrity, cache, and retrieval mechanisms. They are resource artifacts, not
runtime Components, Componentlets, or Subsystem participants.

The primary CAR retains:

- Component identity and runtime descriptor;
- runtime manifest and classpath;
- runtime-required model, schema, and configuration metadata;
- a root composition manifest;
- exact SubComponent coordinates, versions, digests, requiredness, and access
  policy; and
- minimal Help and structured SubComponent diagnostics.

## Logical Completeness and Runtime Independence

Publication completeness and runtime activation are separate contracts.

- A primary release is not published as complete until every required
  SubComponent exists at the exact coordinate/version and passes digest,
  signature, parent, and access-policy validation.
- Production may activate the primary CAR without downloading Documentation or
  SourceCode SubComponents.
- Repository or network unavailability does not stop primary production
  execution.
- Help, admin-oriented inventory, CBD Support, and AI must report whether each
  SubComponent is local, remote, restricted, unavailable, incompatible, stale,
  or corrupt.
- Offline complete-release bundles carry the primary and all required
  SubComponents.

Source is embedded, supplied by the development directory, or carried by an
exact SourceCode SubComponent. Source omission is not a complete logical
Component profile. Restricted source remains represented and repository
complete but is not disclosed, indexed, or claimed as inspected without
authorization.

Credentials, developer-local configuration, caches, generated secrets, and
untracked host state are never release source.

SourceCode SubComponent completeness is judged by later reproduction,
investigation, and debugging needs. It therefore includes admitted managed
source generated for the release together with the generator inputs, identity,
options, digests, and provenance needed to explain that output.

Build paths such as `target/scala-*/src_managed/**` are collection inputs, not
artifact paths. Their admitted contents are normalized under
`generated-source/main` or `generated-source/test`. The rest of `target`,
including class files, incremental caches, temporary files, logs, and
host-specific state, is excluded.

## Development Composition

`OperationMode.Develop` selects an automatic runtime-owned resource-composition
profile:

```text
explicit development directory
  -> development-local documentation/source
  -> expanded SubComponents
  -> local Component Repository
  -> remote Component Repository
```

The Documentation SubComponent is resolved, verified, and mounted
automatically. The development target's source tree satisfies the SourceCode
role when present; otherwise the exact SourceCode SubComponent is resolved
subject to disclosure and authorization.

Missing, stale, corrupt, or incompatible required development resources
produce structured `development-resource-incomplete` failure. This condition
does not become a Component-domain mode, and Component implementation never
receives or branches on `OperationMode`.

The other operation modes keep the same separation:

- `Test` uses explicitly selected deterministic local fixtures, expanded
  artifacts, or offline bundles and does not implicitly access a remote
  repository;
- `Demo` may expose installed or cached Documentation on demand, with remote
  retrieval enabled only by explicit policy, and does not automatically mount
  source; and
- `Production` permits primary-only activation and authorized Documentation
  retrieval on demand, but never automatically resolves, mounts, or fetches
  source.

These are runtime resource policies, not Component behavior modes.

## AI Development Context

The resolved logical Component projects one
`ComponentDevelopmentContext` or accepted equivalent containing:

- User Guide and Reference Manual;
- Entity, Powertype, StateMachine, Value, Datatype, and relationship metadata;
- deterministic class and StateMachine diagrams;
- Service, Operation, and SPI contracts;
- configuration schema;
- examples;
- source and generated source;
- Scaladoc;
- tests;
- build and generation provenance; and
- exact dependency Component documentation.

The context is manifest-based. AI does not receive arbitrary repository
directories, secrets, local configuration, caches, or unauthorized source.
Every admitted resource retains identity, version, origin, digest, authority,
license, disclosure, and physical provenance.

## Later Phase Split

The initial decision combined physical composition, documentation/AI, and
Admin consumption under Phase 56. The later planning decision recorded in
`2026-07-31-phase-56-resource-subcomponent-phase-split.md` separates them:

- Phase 56 owns Resource SubComponent identity, physical packaging contract,
  publication completeness, repository/cache access, resolution, integrity,
  operation-mode resource policy, lifecycle, provenance, and common consumer
  APIs.
- Phase 57 owns knowledge/model manifests, manuals, diagrams, Scaladoc,
  admitted source content, Help, AI development context, CBD Support, and BoK
  integration.
- Phase 58 owns Component Admin runtime visibility and authorized management.

This journal remains the chronological source of the composition discussion.
Where its original Phase 56 assignment conflicts with the split, the later
Phase 56, Phase 57, and Phase 58 phase documents govern planning.

The earlier optional Component-specific Documentation Component and
source-omitted profiles are superseded by this decision. Optional framework
Documentation Components remain a separate mechanism for exact snapshots of
shared CNCF/CML/Cozy/SmartDox publication knowledge.

## Downstream Admin Boundary

Phase 58 Component Admin consumes the same composition, knowledge, and model
manifests. It adds runtime instance, effective configuration,
provenance, datastore/schema/collection, StateMachine registration, health,
and authorized management state. It does not reconstruct documentation,
source, or model metadata independently.
