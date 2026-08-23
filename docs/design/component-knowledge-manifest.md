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

## Canonical Form

The codec uses duplicate-key-rejecting JSON parsing. It writes known fields in
a fixed order, resources ordered by their complete Phase 58 logical identity
and canonical logical path, and extension object keys in lexical order.
Unknown safe fields in the root, resource, binding, metadata, and
safe-provenance objects are retained as extensions and re-emitted without
overriding known v1 fields. Extension keys that name protected Phase 58
location, content, credential, or authority evidence are rejected
case-insensitively, including `authorization`.

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
