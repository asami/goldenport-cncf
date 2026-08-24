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

When `modelResources` is absent, legacy and framework-only encodings remain
byte-stable. When present it follows `frameworkPublication` and precedes
`resources`. Its `models` and `diagrams` are ordered by referenced logical
identity; diagrams order their generated-from evidence by source identity.
Its extension maps are lexical recursively and reject every protected Phase 58
alias plus resource-binding, resolver, scan, and read aliases.

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
