# Component Resource Subcomponent Specification

Status: normative

## Authority

This specification defines the verified static resource-Subcomponent behavior.
The shared Component identity, membership, and authority contract is the
[Component and Subcomponent Architecture Specification](component-subcomponent-architecture.md).
The [Component and Subcomponent Architecture](../design/component-subcomponent-architecture.md)
and the [Component Resource Subcomponent](../design/component-resource-subcomponent.md)
are non-normative design context and are not required to interpret these rules.

The exact executable evidence is frozen in the
[RSC01-B Failing-First Acceptance Registry](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix).
This specification neither introduces a second resolver or identity model nor
defines a public API, schema, type, wire, archive-layout, or lifecycle contract.

## Rules

### R1 Shared Component identity and composition membership

A resource Subcomponent MUST be a declared, independently identified Component
with its own CAR in its parent's composition membership. The shared identity,
membership, role, and authority requirements are those of R1 through R12 of
the Component and Subcomponent Architecture Specification. Resource
composition MUST NOT duplicate that identity or create another identity model.

### R2 Required-release completeness

Parent/child CAR and payload packaging MUST be deterministic and retain
post-package integrity evidence. An incomplete or invalid required membership
MUST NOT become repository-visible. Publication completeness MUST NOT imply
runtime activation.

### R3 Deterministic resolution and dual-form provenance

The primary Component and each independently identified child Component/CAR
MUST resolve with deterministic precedence across embedded, development,
expanded, local, cache, remote, and offline sources. Each resolved resource
MUST retain its logical Component/release identity together with physical
artifact, source, and provenance evidence. Availability, integrity, and
authorization MUST remain orthogonal during resolution.

### R4 Non-activation and external-deployment boundary

Composition membership, discovery, availability, and resolution MUST NOT
activate a child, invoke an operation or MCP capability, or deploy an
external-platform payload. Composition membership, discovery, and availability
MUST NOT grant disclosure authority; content exposure remains subject to R6.
External-platform deployment remains explicit and MUST NOT acquire a CNCF
fallback.

### R5 Runtime operation-mode policy

Develop, Test, Demo, and Production policies MUST be runtime-owned and
deterministic. The admitted resource policy is:

| Mode | Documentation | SourceCode | Remote access |
| --- | --- | --- | --- |
| Develop | Resolve required content only under admitted explicit policy. | Use the development tree only when admitted; otherwise resolve the exact artifact under access policy. | Permitted only by configured development policy. |
| Test | Use explicit deterministic fixtures or bundles. | Use explicit deterministic fixtures. | No implicit access. |
| Demo | Use installed or cached content, or explicitly enabled remote access. | No automatic resolution. | Documentation only under explicit policy. |
| Production | Primary activation remains independent; select only embedded-primary resources. | Never automatically resolve, mount, or fetch. | No remote resource selection. |

An OperationMode MUST NOT enter Component-domain APIs or grant authority.

### R6 Authorization, integrity, path safety, and non-disclosure

Authorization, parent/release evidence, digest, signature, and path safety
MUST be established before content exposure. Traversal, symlink escape,
archive ambiguity, corrupt cache, and unauthorized source inputs MUST reject
safely. Inventory visibility and authorized content access MUST remain
distinct; restricted content and diagnostics MUST NOT reveal content,
credentials, host paths, secrets, or other protected material. Membership or a
registry entry MUST NOT grant authority.

### R7 Immutable artifacts, cache, lifecycle, and concurrency

Resource artifacts MUST be immutable release evidence. Shared artifacts,
in-flight resolution, refresh, invalidation, cache ownership, release, unload,
shutdown, waiter cancellation, and interruption MUST have deterministic
concurrent ownership and preserve actual terminal outcomes. Observability MUST
be bounded, non-sensitive, and isolate unrelated release failures.

### R8 Read-only Help and Admin projections

Help and Admin MUST consume identical read-only projections of parent,
Subcomponent Component, and Subsystem identity, role, availability, integrity,
and provenance. They MUST NOT scan CAR, Subcomponent, cache, repository, or
development paths directly, and the projection MUST NOT grant authority.

### R9 No additional public contract or resolver

This specification MUST be implemented through the shared Component
identity/authority and resolution boundaries. It MUST NOT create a second
resolver, identity model, public API, schema, type, wire contract, or other
competing public contract.

## Executable Evidence

The following accepted evidence is exact as recorded in the frozen registry.
The linked matrix retains the paths, scenarios, repositories, and fixture
identities for every listed group.

| Registry group | Exact executable-suite or scripted identity | Rules |
| --- | --- | --- |
| [RSC03-PACKAGING-PUBLICATION](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `cozy.archive.SubcomponentReleasePackagingSpec`; `subcomponent-release-publication` | R1-R2 |
| [RSC04-RESOLUTION-PROVENANCE](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `org.goldenport.cncf.component.repository.ResolvedComponentResourcesSpec` | R1, R3-R4 |
| [RSC05-MODE-COMPOSITION](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `org.goldenport.cncf.cli.ComponentResourceOperationModePolicySpec` | R1, R4-R5, R9 |
| [RSC06-AUTHORIZATION-INTEGRITY](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `org.goldenport.cncf.component.repository.ComponentResourceAuthorizationSpec` | R1, R3-R4, R6, R9 |
| [RSC07-LIFECYCLE-OBSERVABILITY](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `org.goldenport.cncf.component.repository.ComponentResourceLifecycleSpec` | R1, R3-R4, R7, R9 |
| [RSC07B-TERMINAL-OUTCOME-RECOVERY](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `org.goldenport.cncf.component.repository.ComponentResourceLifecycleSpec` | R7 |
| [RSC08-CONSUMER-PROJECTION](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `org.goldenport.cncf.projection.ResolvedComponentResourcesConsumerSpec` | R1, R6, R8-R9 |
| [RSC09-CROSS-REPOSITORY](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md#authoritative-acceptance-matrix) | `component-subcomponent-end-to-end`; `11.f-component-subcomponent-acceptance`; `cwitter.ComponentSubcomponentCompositionAcceptanceSpec` | R1-R9 |
