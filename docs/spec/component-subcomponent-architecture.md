# Component and Subcomponent Architecture Specification

Status: normative

## Authority

This specification defines the accepted Component/Subcomponent behavior. Its
architectural context is described by the non-behavioral
[Component and Subcomponent Architecture](../design/component-subcomponent-architecture.md).
That design document is not required to interpret any rule in this
specification.

The exact executable-evidence identities are frozen in the
[RSC01-B Failing-First Acceptance Registry](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix).
The evidence table below records those identities without selecting a new
schema, API, type, wire format, archive layout, resolver representation,
lifecycle mechanism, or consumer implementation.

## Terms

- A **Component** has a canonical Component identity and a CAR.
- A **declared Subcomponent** is a Component named by a parent's composition
  membership.
- A **payload role** identifies information carried by a Component.
- **Logical identity** identifies the Component release; **physical identity**
  identifies the artifact and source evidence that supply it.

## Rules

### R1 Declared Subcomponent identity and CAR

Every declared Subcomponent MUST be an independent Component with canonical
identity and its own CAR.

### R2 Documentation and SourceCode payload roles

`Documentation` and `SourceCode` MUST identify non-authoritative,
non-executable information-payload roles. A payload role MUST NOT itself be
treated as a Component.

### R3 Parent membership is not an authority grant

Parent membership MUST reference the child's canonical identity without
duplicating mutable child identity. Membership MUST NOT grant activation,
operation, MCP, disclosure, or deployment authority.

### R4 Role and technology separation

A payload role MUST remain separate from implementation technology.

### R5 Explicit external-platform deployment

An external-platform child MUST retain a CNCF Component and CAR
identity/metadata surface. Deployment of its platform artifact MUST be
explicit, and CNCF MUST NOT silently fall back to automatic platform
deployment.

### R6 Parent and Subsystem relations

Parent membership and general Subsystem membership MUST be represented as
different relations. Neither relation implies the other.

### R7 Logical and physical identity separation

Logical release identity MUST remain distinct from physical artifact identity,
artifact digest, path, and provenance.

### R8 Publication does not activate

Publication completeness MUST NOT imply runtime activation.

### R9 Discovery and availability do not activate

Discovery or availability MUST NOT activate a child or deploy an
external-platform payload.

### R10 Dual-form resolved provenance

Resolved provenance MUST retain the logical Component identity and physical
source evidence together.

### R11 Orthogonal authorization, integrity, and availability

Authorization, integrity, and availability MUST remain orthogonal dimensions.
An implementation MUST NOT collapse them into one enum or state that loses a
dimension's independent outcome.

### R12 Contract-preserving implementation choices

Registry schema, APIs, type names, wire formats, archive layout, resolver
representation, lifecycle machinery, and consumer implementation details MAY
vary, but MUST preserve R1 through R11.

### R13 Composition compatibility behavior

Composition encoding and decoding MUST preserve canonical parent/child
membership without duplicate identity. Role and technology, and logical and
physical identities, MUST remain distinct through the compatibility boundary.
Duplicate, cyclic, conflicting, malformed, unsafe, incompatible, and
unknown-field inputs MUST follow the accepted compatibility boundary.

### R14 Packaging and publication behavior

Parent/child CAR and payload packaging MUST be deterministic and retain
post-package integrity evidence. Incomplete or invalid required membership
MUST NOT become repository-visible. Local and remote publication,
repeated-build, and source/archive evidence MUST remain equivalent.

### R15 Resolution and source behavior

The primary Component and every independently identified child Component/CAR
MUST resolve across the accepted embedded, development, expanded, local,
cache, remote, and offline sources with deterministic precedence and dual-form
provenance. Availability, integrity, and authorization outcomes MUST remain
orthogonal during resolution.

### R16 Mode and composition behavior

Develop, Test, Demo, and Production composition policies MUST be
runtime-owned and deterministic. Production MUST NOT automatically resolve
`SourceCode`, and an OperationMode MUST NOT enter Component-domain APIs or
grant authority.

### R17 Authorization and integrity behavior

Authorization, parent/release evidence, digest, and signature MUST be checked
before content exposure. Traversal, symlink escape, archive ambiguity, corrupt
cache, and unauthorized sources MUST reject safely. Diagnostics MUST NOT leak
content, credentials, host paths, or secrets.

### R18 Lifecycle and observability behavior

Shared immutable artifacts, in-flight resolution, refresh, and invalidation
MUST have deterministic concurrent ownership. Release, unload, shutdown,
refresh, ordinary waiter cancellation, and interruption races MUST preserve
their actual terminal outcomes. Observability data MUST be bounded,
non-sensitive, and isolate unrelated release failures.

### R19 Terminal-outcome recovery behavior

A cancelled loading producer MUST be removed and receive its cancelled
no-resource result before provenance-mismatch evaluation. An admitted waiter
MUST receive its mismatch failure without duplicate loading, resource
publication, or owner leakage. Cancellation after shared-flight completion
MUST preserve the completed release, unload, or shutdown result; metrics
change only when cancellation wins.

### R20 Consumer projection behavior

Help and Admin MUST consume identical projections of parent, Subcomponent
Component, and Subsystem identity, role, availability, integrity, and
provenance. Those consumers MUST NOT scan CAR, Subcomponent, cache,
repository, or development paths directly. Inventory visibility and authorized
content access MUST remain distinct.

### R21 Cross-repository acceptance behavior

The shared profiles `embedded-primary`, `parent-documentation-source`,
`parent-external-platform`, `restricted-source`, `development-override`,
`repository-online-offline`, `production-primary-only`, `failure-matrix`,
`lifecycle-concurrency`, and `help-admin-consumer` MUST cover the accepted
parent-only, split, restricted, development, repository, offline, production,
and consumer behavior. Missing, incompatible, duplicate, unsafe, stale,
corrupt, lifecycle, and concurrency outcomes MUST remain deterministic.
Cross-repository package, publish, resolve, and consume evidence MUST agree,
and external deployment MUST have no CNCF fallback.

## Executable Evidence

The following exact suite and scripted identities are the verified executable
evidence for R13 through R21. Their frozen paths, scenarios, repositories, and
shared fixtures remain defined by the RSC01-B registry linked above.

| Registry group | Exact executable-suite or scripted identity | Rules |
| --- | --- | --- |
| RSC02-IDENTITY-CODEC | `org.goldenport.cncf.component.ComponentSubcomponentCompositionCodecSpec` | R1-R7, R10-R13 |
| RSC03-PACKAGING-PUBLICATION | `cozy.archive.SubcomponentReleasePackagingSpec`; `subcomponent-release-publication` | R1-R3, R5, R7-R8, R10-R11, R14 |
| RSC04-RESOLUTION-PROVENANCE | `org.goldenport.cncf.component.repository.ResolvedComponentResourcesSpec` | R1, R3, R5, R7-R11, R15 |
| RSC05-MODE-COMPOSITION | `org.goldenport.cncf.cli.ComponentResourceOperationModePolicySpec` | R1-R3, R5-R6, R8-R9, R12, R16 |
| RSC06-AUTHORIZATION-INTEGRITY | `org.goldenport.cncf.component.repository.ComponentResourceAuthorizationSpec` | R3, R5, R7-R12, R17 |
| RSC07-LIFECYCLE-OBSERVABILITY | `org.goldenport.cncf.component.repository.ComponentResourceLifecycleSpec` | R1, R3, R6-R12, R18 |
| RSC07B-TERMINAL-OUTCOME-RECOVERY | `org.goldenport.cncf.component.repository.ComponentResourceLifecycleSpec` | R1, R3, R6-R12, R19 |
| RSC08-CONSUMER-PROJECTION | `org.goldenport.cncf.projection.ResolvedComponentResourcesConsumerSpec` | R1-R3, R6-R7, R9-R12, R20 |
| RSC09-CROSS-REPOSITORY | `component-subcomponent-end-to-end`; `11.f-component-subcomponent-acceptance`; `cwitter.ComponentSubcomponentCompositionAcceptanceSpec` | R1-R21 |
