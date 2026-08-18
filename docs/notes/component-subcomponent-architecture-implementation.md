# Component and SubComponent Architecture Implementation Proposal

status = proposed, non-normative
date = 2026-08-14
phase = Phase 58

This is the initial implementation proposal for the expanded Phase 58. It
does not create a runtime, archive, schema, repository, or launcher contract
by itself. The Phase 58 executable specifications, followed by the promoted
design and specification, are authoritative for implementation.

At Phase 58 closure, verified architecture and normative behavior must be
promoted to:

- `docs/design/component-subcomponent-architecture.md`; and
- `docs/spec/component-subcomponent-architecture.md`.

The existing
`component-resource-subcomponent-implementation.md` is an input for resource
payload packaging. It must be reconciled with this broader model during
RSC-01; it does not independently decide whether a declared Subcomponent is a
Component.

## Purpose

Define one Component composition model in which a parent Component remains an
executable CAR and every declared Subcomponent is independently a Component
with its own CAR. The model must make the parent relationship, role,
implementation technology, release membership, integrity, repository
admission, runtime boundary, and platform deployment responsibility explicit.

The proposal distinguishes three concepts that must not be conflated:

- a **Component** is the fundamental CBD unit with a Component identity;
- a **CAR** is the immutable, self-describing artifact for one Component
  snapshot; and
- a **Subsystem** is an executable composition of one or more Components. A
  single executable Component may be treated as an implicit single-Component
  Subsystem.

## Composition Classes

### Parent Component

A parent Component has a primary CNCF Runtime implementation and a primary
CAR. It is not a container-only artifact. Its CAR carries the authoritative
composition registry for the SubComponents that structurally belong to it.

### Subcomponent Component

Every declared Subcomponent is a Component with its own Component identity,
primary CAR, metadata, documentation, and parent reference. Its parent
registry records at least the parent reference, role, implementation
technology, coordinate, version, digest, requiredness, and release-membership
policy.

Each Subcomponent CAR has a CNCF-side Component representation for identity,
metadata, Help, diagnostics, MCP-readable information, and admitted
operations. A Documentation or Source Subcomponent can therefore be described
and queried independently even when the parent is the only Component activated
for ordinary runtime work. Its documentation/source payload does not itself
grant an Operation, capability, or authority.

A Subcomponent may also carry an artifact for another execution platform.
Examples include:

| Role | Example implementation artifact | Deployment owner |
| --- | --- | --- |
| `presentation` | React or KMP application | Web or mobile platform |
| `batch` | Scala/JVM batch application | Batch scheduler/runtime |
| `function` | Scala.js or other function artifact | Cloud-function platform |
| `documentation` | authored documentation artifact | documentation delivery surface |
| `source` | admitted source artifact | development tooling |

The CNCF Runtime admits and describes the CAR. It does not silently deploy an
external platform artifact. Platform-native deployment remains explicit and
outside the CAR admission path.

## Parent Registry and Identity

The Phase 58 model must decide and specify:

- the parent Component identity and the independent identity of every
  Subcomponent Component;
- which membership facts are versioned with the parent release and which are
  independently versioned child releases;
- the canonical parent registry schema, role vocabulary, implementation
  technology field, requiredness, integrity evidence, and compatibility rules;
- duplicate, cycle, missing-parent, incompatible-parent, and stale-membership
  outcomes; and
- the relation between a primary CAR, independently describable Subcomponent
  CARs, and an assembled Subsystem.

The registry references authoritative Component identities; it must not
duplicate mutable identity metadata, grant Operation authority, grant
unbounded MCP access, activate a child, disclose restricted content, or select
a deployment target.

## Repository and Resolution Direction

Publication is a complete, deterministic transaction for the declared release
profile. Required CARs and their payload artifacts must be admitted with exact
parent and integrity evidence before the profile becomes visible. Resolution
retains logical identity and physical provenance for every primary and
Subcomponent member.

The implementation must distinguish:

- parent CAR activation from Documentation/Source Subcomponent availability;
- Subcomponent discovery from child activation;
- CAR admission from external-platform deployment; and
- one Component's parent relationship from general Subsystem membership.

Development, test, demo, production, cache, local repository, remote
repository, and offline profiles must preserve those distinctions with stable
structured diagnostics.

## Initial Executable Specification Work

Before implementation, Phase 58 must create failing-first executable
specifications for:

1. parent and child Component identity, release, version, and cycle rules;
2. independent Subcomponent CAR identity, including Documentation and Source
   Components, versus the payload artifact each carries;
3. parent registry codec, unknown-field, duplicate, stale, and integrity
   behavior;
4. deterministic packaging and repository admission of representative parent
   plus child fixtures;
5. independent CAR description, MCP-visible metadata, and admitted execution
   of each child Component;
6. explicit external-platform deployment handoff with no CNCF fallback; and
7. single-Component and multi-Component Subsystem composition and diagnostics.

## Promotion and Closure

This note is intentionally provisional. Phase 58 may not treat it as a
normative implementation shortcut. Once the model, codecs, resolver behavior,
cross-repository packaging, and executable acceptance are verified, Phase 58
must promote the accepted architecture to `docs/design` and the behavior to
`docs/spec`, update dependent Phase 59 and Phase 60 contracts, and mark this
proposal historical.
