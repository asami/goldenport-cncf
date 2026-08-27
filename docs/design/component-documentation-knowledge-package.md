# Component Documentation Knowledge Package

Status: current P5910-DOC10-A1 design boundary

## Purpose and authority

This design records the completed Core boundary for Component documentation
and knowledge material. It reconciles the existing Phase 58 resource
composition and the Phase 59 manifest, consumer, Help, model, framework, and
public-metadata contracts. It describes responsibility and intent; the linked
specifications remain the authoritative detailed contracts.

The package is a read-only projection of already resolved Component resources.
It does not add a resolver, a second identity model, a carrier, a transport
endpoint, or a new authority boundary. Terms and behavior are governed by the
[narrower Component Resource Subcomponent design and specification](component-resource-subcomponent.md)
and [specification](../spec/component-resource-subcomponent.md), the
[Component Knowledge Manifest design and specification](component-knowledge-manifest.md),
the [Component Knowledge Help design and specification](component-knowledge-help-contract.md),
and the [Framework Documentation Profile design and specification](framework-documentation-profile.md).

## Logical release and physical resources

A Component's documentation and source are logical release material: they
explain and support one exact Component identity and logical release. Physical
packaging is governed by Phase 58. The release may therefore be represented by
the primary execution Component CAR together with independently identified,
declared Documentation and SourceCode Subcomponents. The physical split does
not create a second Component identity, a second user-facing documentation
namespace, or an implicit runtime dependency.

The primary CAR retains the runtime-required metadata and the composition
evidence needed to identify and diagnose the release. Documentation and source
resources retain their own Phase 58 identity, integrity, availability,
authorization, and provenance facts. Required membership is a release
completeness concern; it is not activation. Resolution, cache, repository,
archive, lifecycle, and operation-mode policy remain Phase 58/runtime
responsibilities. This package consumes the resulting caller-supplied
`ResolvedComponentResources` view.

The package consequently distinguishes two forms of identity evidence:

- `logicalPath` is the exact raw logical resource identity and membership key.
- A public Help or Direct-AI transport route is a separate encoded descriptor
  produced by the existing Help contract. Route encoding does not normalize,
  join, resolve, or replace the raw logical path.

Availability, integrity, and authorization remain separate facts. An
inventory entry can describe a restricted, unavailable, missing, stale,
incompatible, or corrupt resource without exposing its content or granting
access. Authorized access is delegated through the existing Phase 58 policy
and exact retained resource pair; discovery and inventory never activate a
resource or grant operation, MCP, disclosure, deployment, or Admin authority.

## Responsibility model

### Core Component documentation

Component-specific manuals, Help entries, API and configuration descriptions,
examples, model/diagram metadata, Scaladoc, and admitted source are logical
release material. The knowledge manifest indexes the existing resource
evidence; it does not become a new source of identity, content, or resolver
decisions. The manifest consumer is a deterministic, safe, read-only view.

Human Help and Direct-AI navigation consume the same canonical resource
inventory. Human navigation is descriptive. Direct-AI access may request one
already projected resource, but only after exact Component, release, path, and
retained-resource-pair matching. The existing
`ComponentKnowledgeHelpContract` delegates an admitted request to the
`ResolvedComponentResourcesConsumerProjection` with the `DirectAi` consumer.
Neither projection scans physical locations or re-resolves resources.

The Help contract also keeps development-context outcome evidence supplied by
the caller. A ready outcome is not recalculated by Help; an incomplete outcome
retains its existing `development-resource-incomplete` evidence. Operation
mode remains outside Component-domain APIs and is not created by this package.

### Framework and publication material

Shared CNCF, CML, Cozy, SmartDox, and related toolchain documentation has a
different subject: a framework or toolchain product and version. Its public
publication surface is SimpleModeling.org. An optional Framework Documentation
Component is a versioned snapshot of that same publication generation. It is
a distribution projection, not the authoring authority and not a
Component-specific Documentation or SourceCode Subcomponent.

Framework navigation is therefore a separate developer/toolchain value. It
retains caller-supplied publication identity, version, generation, canonical
reference, digest, and availability. An absent snapshot is valid descriptive
evidence and is not a Component startup, Help, manual, or development-context
precondition. Online evidence remains online; it is not relabeled local, and a
human `latest` alias is not an immutable evidence identity.

An optional framework snapshot cannot establish target Component identity,
Component documentation ownership, authoring authority, activation,
installation, or access. Framework material is not appended to the ordinary
Component resource inventory.

### CBD Support and BoK

Textus CBD Support is the primary mediated integration for exact Component
discovery, detail, usage, and review. It may consume the existing manifest and
resource evidence through its own contract. This Core package does not
implement that integration.

Textus BoK is complementary: it supplies terminology and semantic association
over admitted knowledge and can hand exact identity/evidence to CBD Support.
BoK is not a replacement for the Component manifest or the exact Component
authority boundary, and this package does not implement BoK indexing, RAG,
MCP, or handoff behavior.

## Public Directive and Skill metadata

Public Directive guidance and Skill Catalog entries are descriptive metadata
only. They may identify a public projection, visibility, version, digest,
purpose, trigger, requirements, permissions, side effects, or installation
reference according to the existing manifest contracts. They do not expose
private rule/prompt content, credentials, approvals, or protected source.

The mounted authoritative Directive remains authoritative; a public
projection cannot replace or override it. Skill metadata does not install,
activate, execute, configure, disclose, or grant authority to a Skill or MCP
requirement. CAR-owned `SkillBundleManifest` packaging and installation/
activation remain outside this Core documentation promotion and deferred to
the separately governed future work.

## Explicit boundary

This design changes no source code, executable specification, schema, codec,
resolver, route wiring, endpoint, CLI parser, authorization policy,
publication process, installation behavior, runtime lifecycle, CBD Support,
BoK, SimpleModeling.org, Cozy, Directive, or Skill implementation. It does
not start Phase 60 or assign Phase 60/Admin behavior to the Core package.

The implementation note is retained as historical consideration material in
[the implementation note](../notes/component-documentation-knowledge-package-implementation.md).
The current design and its paired specification supersede that note.

## Executable evidence

The design is grounded in existing executable specifications; it creates or
modifies none. The exact evidence is:

- [`ComponentKnowledgeManifestSpec`](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestSpec.scala)
- [`FrameworkPublicationContextSpec`](../../src/test/scala/org/goldenport/cncf/knowledge/FrameworkPublicationContextSpec.scala)
- [`PortableModelResourceContextSpec`](../../src/test/scala/org/goldenport/cncf/knowledge/PortableModelResourceContextSpec.scala)
- [`PublicDirectiveSkillCatalogContextSpec`](../../src/test/scala/org/goldenport/cncf/knowledge/PublicDirectiveSkillCatalogContextSpec.scala)
- [`ComponentKnowledgeManifestConsumerContractSpec`](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeManifestConsumerContractSpec.scala)
- [`ComponentKnowledgeHelpContractSpec`](../../src/test/scala/org/goldenport/cncf/knowledge/ComponentKnowledgeHelpContractSpec.scala)
- [`ResolvedComponentResourcesConsumerSpec`](../../src/test/scala/org/goldenport/cncf/projection/ResolvedComponentResourcesConsumerSpec.scala)

These suites are evidence for the existing contracts, not a new acceptance
surface for this design.
