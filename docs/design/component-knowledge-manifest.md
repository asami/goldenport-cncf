# Component Knowledge Manifest

Status: stable design

## Purpose

The Component knowledge manifest is the Phase 59.2 read-only data boundary for
one Component release and its knowledge/model resource entries. Its v1 schema
is `cncf.component-knowledge.v1`. It records caller-supplied Phase 58 logical
resource identity, state, digest, and safe provenance as serializable evidence.

## Input and Authority Boundary

`ComponentKnowledgeManifest.createC` accepts a completed
`ResolvedComponentResources` value and a candidate manifest. It validates only
that supplied collection; it does not create a resolver, inspect an archive,
scan a filesystem, cache, repository, or network, read resource content, or
perform a second identity operation.

The safe provenance projection contains only source kind, artifact coordinate,
logical source, resolution step, external-deployment requirement, and matching
digest. Repository locations, normalized and host paths, physical sources,
bytes, credentials, activation, operation, MCP, deployment, and disclosure
authority are outside this contract.

## Framework Publication Context

An optional `frameworkPublication` records descriptive framework-publication
evidence beside, and independently from, the Component's Phase 58 resource
entries. It contains a typed product/version, canonical public HTTPS URL,
publication generation, logical document and optional section identities,
publication SHA-256, reference availability, and generated-from source
identity/digest. Product/version, URL, publication generation, document or
section identity, and byte digest are distinct values: URL or digest equality
never establishes Component, document, publication, or authoring authority.

The optional documentation Component snapshot identifies a Component/release
and repeats the publication digest only as attributable descriptive evidence.
It is not a `ComponentKnowledgeResourceBinding` or
`ComponentResourceLogicalIdentity`, and it supplies no resolver input,
resource identity, authoring authority, execution activation, or access grant.
`unavailable` is valid recorded evidence; neither availability nor snapshot
presence determines execution readiness.

Projection freshness is a pure comparison of caller-provided generated-from
source identity and SHA-256 with the recorded generated-from value. It reports
`current` only when both are equal and `stale` otherwise. It does not read a
resource, resolve an identity, access a filesystem/cache/repository/network,
or create a resolver.

## Portable Model and Diagram Resource Context

An optional `modelResources` root context records portable model and diagram
evidence by exact reference to values already in `resources`. It does not
create a resource binding, logical identity, resolver input, physical path,
resource read, renderer, cache/repository/network operation, execution,
activation, authority, or access grant. The referenced entry retains its
existing typed logical identity, role, media type, digest, safe provenance,
availability, integrity, and authorization as Phase 58 evidence.

`models` admits only Entity, Powertype, StateMachine, Value, Datatype, and
Relationship entries with the existing `model` / `application/json`
combination. `diagrams` admits only ClassDiagram and StateDiagram entries with
the existing `diagram` / `image/svg+xml` combination. Logical identities and
logical paths cannot repeat across the context.

Every diagram has nonempty generated-from evidence. Each generated source is
an admitted model logical identity plus that entry's matching SHA-256 digest;
it never derives a digest or grants source authority. A StateDiagram must name
at least one StateMachine source; a ClassDiagram names one or more admitted
model sources. Safe extension fields are retained only after recursive
protected-alias rejection.

## Public Directive and Skill Catalog Metadata

The optional `publicDirective` and `skillCatalog` roots record only descriptive
metadata for one exact existing `Directive` / `directive` /
`application/yaml` entry and one exact existing `SkillCatalog` /
`skill-catalog` / `application/json` entry, respectively. Each referenced
entry must occur once in `resources`, and the recorded source SHA-256 must
equal that entry's existing digest. These references create neither a new
Phase 58 binding nor a resolver input.

Directive metadata carries identity labels, an absolute origin, version, one
typed descriptive authority value (`mounted-directive-remains-authoritative`),
one typed visibility value, a canonical public HTTPS guide reference, and the
typed redaction statement `source-and-rule-content-withheld`. The mounted
Directive remains authoritative. No directive source, profile text, rule text,
prompt, credential, approval, configuration, or restricted material is part
of this context.

Skill Catalog metadata carries identity, owner, purpose, trigger, ordered
nonempty unique requirements, permissions, side effects, and MCP requirements,
a canonical public HTTPS installation reference, visibility, version, and its
matching source digest. All such values describe a Skill only. They do not
install, activate, execute, configure, operate, disclose, or grant authority
to a Skill or MCP requirement.

Both public contexts retain safe extensions only after recursive normalized
alias rejection. In addition to the earlier protected evidence aliases, their
extension policy rejects repository/location/physical-or-normalized-path,
content/bytes, credential/token, authorization/approval/configuration,
installation/activation/execution/operation/MCP/deployment/disclosure
authority, resource binding, resolver, scan, and read aliases. Normalization
handles case, punctuation, camel case, and compound forms.

## Read-Only Consumer Contract

`ComponentKnowledgeManifestConsumerContract` is the stable, deterministic
`cncf.component-knowledge-consumer.v1` value projection for later consumers.
It contains only Component/release identity, safe manifest-resource evidence,
and optional framework-publication, model-resource, public Directive, and
Skill Catalog evidence. It canonicalizes resource and reference order and
retains only recursively safe unknown fields under the same public-context
protected-alias policy.

The projection has no resource bytes or content, physical path, repository,
credential, approval, configuration, resolver, resource read, route, Admin,
installation, activation, execution, operation, or authority-grant behavior.
It is a Phase 60-facing data shape only; it does not implement a Phase 60
consumer.

## Canonical Form

The codec uses duplicate-key-rejecting JSON parsing. It writes known fields in
a fixed order, resources ordered by their complete Phase 58 logical identity
and canonical logical path, and extension object keys in lexical order.
Unknown safe fields in the root, resource, binding, metadata, and
safe-provenance objects are retained as extensions and re-emitted without
overriding known v1 fields. Every extension-object key is checked recursively.
Its identifier is normalized across case, punctuation, camel-case, and compound
word forms; keys naming protected Phase 58 repository/location,
physical-or-normalized path, content/bytes, credential-token, authorization,
activation, operation, MCP, deployment, or disclosure-authority evidence are
rejected.

When `frameworkPublication` is absent, canonical v1 JSON retains the legacy
root shape. When present, it appears after `logicalRelease` and before
`resources`; its known fields have their own fixed order and safe unknown
fields are recursively lexical. In addition to protected Phase 58 aliases,
framework-context extensions reject normalized aliases for resource binding,
resolver, scan, and read evidence. This local rejection does not alter the
pre-existing extension behavior of other manifest objects.

When `modelResources`, `publicDirective`, and `skillCatalog` are absent, legacy
and framework-only encodings remain byte-stable. When present, their root order
is `frameworkPublication`, `modelResources`, `publicDirective`, `skillCatalog`,
then `resources`. The model context's `models` and `diagrams` are ordered by
referenced logical identity; diagrams order generated-from evidence by source
identity. Each context's extension maps are lexical recursively and reject its
protected aliases.

The optional resource `language` and binding `parentComponentId` fields may be
absent or JSON `null` when decoded; canonical output writes the known fields.
Every binding belongs to the manifest release and identifies either the manifest
Component itself without a parent or a child whose parent is that Component.

Resource kind, role, media type, authority, stability, source, and disclosure
are explicit typed vocabularies. Availability, integrity, and authorization
remain separate Phase 58 state values. No metadata value grants access or
changes resolver, authorization, integrity, or availability policy.

## Non-Goals

This model creates no Help, Admin, HTTP, CLI, CBD Support, BoK, MCP,
publication, packaging, generation, authoring, installation, routing, or
runtime-execution behavior. It neither exposes protected resource material nor
turns membership or a manifest entry into activation, operation, disclosure, or
deployment authority.
