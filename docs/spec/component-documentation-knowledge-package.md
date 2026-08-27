# Component Documentation Knowledge Package Specification

Status: normative P5910-DOC10-A1 contract

## Authority and scope

This specification consolidates the established Core documentation boundary.
It introduces no schema, API, codec, carrier, resolver, endpoint, route, or
new behavior. Exact terms are governed by the existing [Component Resource
Subcomponent design](../design/component-resource-subcomponent.md) and
[specification](component-resource-subcomponent.md), [Component Knowledge
Manifest design](../design/component-knowledge-manifest.md) and
[specification](component-knowledge-manifest.md), [Component Knowledge Help
design](../design/component-knowledge-help-contract.md) and
[specification](component-knowledge-help-contract.md), and [Framework
Documentation Profile design](../design/framework-documentation-profile.md)
and [specification](framework-documentation-profile.md).

The requirements below describe the already implemented and evidenced
boundary. They do not authorize Phase 60, Admin, CBD Support, BoK, publication,
or runtime changes.

## Requirements

### R1 Logical Component-release material

Component-specific documentation and admitted source MUST be treated as
logical material of one exact Component release. Physical Phase 58 packaging
MUST remain the authority for the primary execution CAR and any declared,
independently identified Documentation or SourceCode Subcomponent.

The package MUST preserve the Phase 58 logical identity, physical/provenance
evidence, availability, integrity, and authorization distinctions. Required
Subcomponent membership MUST remain release completeness evidence and MUST NOT
imply activation or access authority.

### R2 Caller-supplied resource boundary

Manifest and consumer projections MUST consume caller-supplied resolved Phase
58 resources. They MUST NOT discover resources, scan CARs or directories,
select a source, read content, fetch from a repository, access a cache, or
invoke a resolver.

The projections MUST preserve exact resource evidence, including availability,
integrity, authorization, and safe provenance. Inventory visibility MUST NOT
be treated as content authorization.

### R3 Logical paths and transport descriptors

`logicalPath` MUST remain the exact raw logical resource identity and
membership key. An existing Help or Direct-AI transport route MUST remain a
separate encoded descriptor. Route encoding MUST NOT normalize, join, resolve,
decode early, or replace the raw logical path; any future adapter MUST compare
the recovered raw path with the exact retained resource identity.

This specification does not create or wire a route, HTTP endpoint, CLI parser,
or transport adapter.

### R4 Help and exact-pair Direct-AI access

Human Help and Direct-AI navigation MUST consume the same canonical resource
inventory and preserve Component/release identity, logical path, kind, role,
language, media type, availability, integrity, authorization, and the
existing route descriptor.

Direct-AI access MUST be admitted only for one exact retained resource pair
whose Component, logical release, raw logical path, and route identity match
the caller-supplied Phase 58 resource. Unknown, foreign, mismatched,
traversal-like, or noncanonical selections MUST fail closed. An admitted
request MUST delegate to the existing
`ResolvedComponentResourcesConsumerProjection` with the `DirectAi` consumer.

The existing authorization policy MUST remain responsible for content bytes,
integrity, archive, cache, availability, and authorization decisions. Help
and Direct-AI MUST NOT scan, fetch, mount, cache, re-resolve, fall back,
inspect, or transform hostile content, and MUST NOT grant activation,
operation, MCP, disclosure, deployment, or Admin authority.

### R5 Development evidence and state

The package MUST preserve every existing Phase 58 availability value without
rewriting it, including `Available`, `Restricted`, `Unavailable`, `Missing`,
`Stale`, `Incompatible`, and `Corrupt`. Availability, integrity, and
authorization MUST remain distinct.

Caller-supplied `ComponentDevelopmentContextOutcome.Ready` MUST be projected
only with its supplied knowledge. `Incomplete` MUST retain the existing
`development-resource-incomplete` code and issue evidence. Help MUST perform
no independent readiness calculation, fallback, or source choice.

### R6 Framework/publication separation

Framework publication evidence and an optional Framework Documentation
Component snapshot MUST remain separate developer/toolchain navigation values;
they MUST NOT be added to the operator Component inventory. The profile MUST
retain caller-supplied framework product/version, publication identity,
generation, canonical reference, digest, and availability according to the
existing Framework Documentation Profile contract.

An absent snapshot MUST remain valid descriptive evidence and MUST NOT become
a Component startup, Help, manual, or development-context precondition. Online
evidence MUST remain online, and aliases such as `latest` MUST NOT become
immutable evidence identities. A snapshot MUST NOT establish target Component
identity, documentation ownership, authoring authority, activation,
installation, execution, or access authority.

### R7 Descriptive public metadata

Public Directive and Skill Catalog metadata MUST remain descriptive and
nonactivating. Public projections MUST NOT expose restricted Directive
source/profile/rule/prompt content, credentials, approvals, protected source,
or provider configuration.

The mounted authoritative Directive MUST remain authoritative and MUST NOT be
replaced or overridden by its public projection. Skill Catalog metadata MUST
NOT install, activate, execute, configure, disclose, or grant authority to a
Skill or MCP requirement. CAR-owned `SkillBundleManifest` installation and
activation remain outside this specification.

### R8 Explicit exclusions

This specification MUST NOT change the Phase 58 resolver, Component identity,
manifest schemas or codecs, Help legacy behavior, HTTP/CLI wiring, runtime
lifecycle, authorization implementation, CAR packaging, publication,
installation, CBD Support, BoK, or any Phase 60/Admin behavior. No executable
specification is created or modified for this documentation promotion.

## Existing executable evidence

The following existing executable specifications are the sole cited evidence
for this Core contract:

| Contract evidence | Source |
| --- | --- |
| `ComponentKnowledgeManifestSpec` | [`src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestSpec.scala`](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestSpec.scala) |
| `FrameworkPublicationContextSpec` | [`src/test/scala/org/goldenport/cncf/knowledge/FrameworkPublicationContextSpec.scala`](../../src/test/scala/org/goldenport/cncf/knowledge/FrameworkPublicationContextSpec.scala) |
| `PortableModelResourceContextSpec` | [`src/test/scala/org/goldenport/cncf/knowledge/PortableModelResourceContextSpec.scala`](../../src/test/scala/org/goldenport/cncf/knowledge/PortableModelResourceContextSpec.scala) |
| `PublicDirectiveSkillCatalogContextSpec` | [`src/test/scala/org/goldenport/cncf/knowledge/PublicDirectiveSkillCatalogContextSpec.scala`](../../src/test/scala/org/goldenport/cncf/knowledge/PublicDirectiveSkillCatalogContextSpec.scala) |
| `ComponentKnowledgeManifestConsumerContractSpec` | [`src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestConsumerContractSpec.scala`](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestConsumerContractSpec.scala) |
| `ComponentKnowledgeHelpContractSpec` | [`src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHelpContractSpec.scala`](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHelpContractSpec.scala) |
| `ResolvedComponentResourcesConsumerSpec` | [`src/test/scala/org/goldenport/cncf/projection/ResolvedComponentResourcesConsumerSpec.scala`](../../src/test/scala/org/goldenport/cncf/projection/ResolvedComponentResourcesConsumerSpec.scala) |

The linked design/specification documents provide the narrower rule mapping;
this specification is not a competing field schema or executable test
registry.
