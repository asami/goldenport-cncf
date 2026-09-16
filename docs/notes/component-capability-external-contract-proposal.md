# Component Capability External Contract Proposal

Status: exploratory
Date: 2026-09-16
Development item: DEV-011

## Purpose

Define how CNCF Component specifications expose Capability as a stable public
model contract. Capability is managed like a Use Case Model: it is a
non-instantiated model element and an intermediate representation connecting
requirements, component architecture, executable realizations, and evidence.

The runtime never executes a Capability object. It executes an admitted
Operation, Workflow, or StateMachine referenced by a separate realization
mapping.

## Ownership boundary

CNCF owns the externally consumable Component capability contract:

- stable Capability identity and version;
- provided and required Capability declarations;
- semantic description and optional input/output model references;
- realization references to Operation, Workflow, and StateMachine identities;
- source, component, package, and ABI provenance;
- compatibility and fail-closed admission rules.

CNCF does not own CML syntax or parse CML. Cozy owns source authoring,
normalization, validation, and generation of the admitted contract.
Textus CBD Support consumes the published contract for catalog, search, view,
and traceability without inventing Capability semantics.

## Semantic separation

```text
Capability     what the Component can provide or requires
Realization    which Operation / Workflow / StateMachine satisfies it
Availability   whether the realization is present in the current context
Authorization  whether the current principal may invoke it
Guard          whether the current state permits execution
```

Capability identity must not be inferred from an Operation name, endpoint,
Skill label, or documentation text. A realization may change while the
Capability identity remains stable, subject to an explicit compatibility
policy.

## Proposed external projection

A Component package exposes a versioned Capability projection alongside its
existing Component and Operation metadata. The projection contains provided
and required records, stable qualified identity and version, semantic
description, optional model references, explicit realization records, source
and generated-ABI provenance, and traceability links to Specification and
Evidence.

The projection is discoverable without loading the Component runtime. Missing,
duplicate, ambiguous, or incompatible identities fail admission rather than
being repaired by name matching.

## Development slices

1. Freeze Capability vocabulary, identity, versioning, and compatibility.
2. Define the external Component projection for provided and required
   Capabilities.
3. Define realization mapping to existing Operation, Workflow, and
   StateMachine contracts.
4. Integrate admission and discovery with ComponentFactory and package/CAR
   metadata without adding a second CML parser.
5. Prove one Cozy-produced fixture and one Textus CBD Support consumer path.

## Non-goals

- Instantiating or persisting Capability as an application/runtime entity.
- Treating Capability as an alias for Operation, Permission, Guard, endpoint,
  or Agent Tool.
- Defining CML syntax in CNCF.
- Adding Workflow ordering, retry, compensation, or state transition semantics
  to the Capability record.
- Implementing cbd-support presentation or search in CNCF.

## Open design questions

- Whether Capability identity belongs directly to Component ABI metadata or a
  separately versioned projection referenced by the Component manifest.
- Whether input/output model references are required in the first version.
- How realization compatibility is reported when an executable ABI changes
  without changing Capability meaning.
- Which existing CAR/package metadata surface is canonical for publication.
