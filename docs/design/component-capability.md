# Component Capability

Status: structural design

## Metadata admission stages

The structure has four pure metadata stages: external JSON, raw typed
`Projection`, explicit selected reference `Scope`, and private-constructed
`Admitted`. The strict codec owns transport decoding/validation and compact
encoding. Admission joins the acquired Component snapshot to caller-supplied
model/vocabulary facts, supplemental definitions and admitted target contract
facts. `Admitted` keeps the original projection, selected scope ID, combined
definitions and separate resolved edge/target records.

The Reference and Projection objects own their typed carriers. Internal
decoding, encoding and validation parts support the codec facade; a binding
part supports the admission facade. This package consumes canonical Component
identity/release authorities while keeping model/vocabulary identities in their
existing literal qualified domains. It receives no runtime Component or Factory
and performs no activation or source acquisition.

[R11](../spec/component-capability.md#r11-concrete-v1-format) owns the complete
wire/scalar/snapshot contract;
[R12](../spec/component-capability.md#r12-executable-metadata-admission) owns
scope, exact binding, compatibility, public APIs and diagnostics. The
[working specifications](../spec/component-capability.md#working-specifications-and-conformance-input)
use hand-authored external input and independently supplied ready facts.
Their validation and independent CAP-78-02 acceptance remain pending.

The next handoffs remain separate. CAP-78-03 extracts actual admitted
Operation/Workflow/StateMachine target contracts and proves their provenance.
CAP-78-04 selects the canonical ComponentFactory/package/CAR metadata source
and exposes discovery from it. CAP-78-05 uses a real Cozy-generated input and
records producer/admission/CBD interoperability. Pure binding against explicit
scope facts does not establish these integration surfaces.

## Authority and scope

This document describes Component Capability responsibilities and structure.
The [Component Capability Specification](../spec/component-capability.md)
is the self-contained normative authority for semantic behavior. Rule links
below identify that authority without defining a second behavioral contract.

## Ownership and non-instantiated Capability boundary

The responsibility boundary connects Cozy's model production, CNCF's
Component contract, and Textus CBD Support's catalog consumption. Application
Capability belongs to the upstream application model; Component Capability
belongs to the architectural description of a Component.

The model contract and the runtime execution boundary occupy separate layers.
[R1](../spec/component-capability.md#r1-ownership-and-non-instantiated-contract)
defines their ownership and separation. The
[CNCF Architecture Overview](cncf-architecture-overview.md) supplies the
surrounding Component and Subsystem runtime structure.

## Exact model identity versus Component identity

The structure has two identity domains: the upstream model element describing
the Capability and the owning Component described by the existing
[Component Identity Specification](../spec/component-identity.md).
The [Component Identity design](component-identity.md) describes the
Component's namespace and local-ID authority.

[R2](../spec/component-capability.md#r2-exact-qualified-identity)
defines the Capability identity and its relation to Component identity.
[R3](../spec/component-capability.md#r3-provided-and-required-declarations)
defines the declaration edges joining these domains and the ownership of
definitions and links.

## Distinct version and provenance domains

Projection schema, Capability contract, generated CNCF ABI, producer,
Component/package release, and source revision are separate domains in the
handoff. Their structure keeps contract meaning, production, distribution,
and source attribution independently representable.

[R4](../spec/component-capability.md#r4-distinct-version-domains)
defines version admission;
[R5](../spec/component-capability.md#r5-independent-provenance)
defines provenance;
[R8](../spec/component-capability.md#r8-directed-contract-compatibility)
defines compatibility and changed-realization boundaries. The surrounding
[Component and Subcomponent Architecture](component-subcomponent-architecture.md)
provides Component, CAR, and payload ownership context without assigning
Capability identity to physical artifact evidence.

## Semantic grounding and explicit realization graph

Capability declarations are the graph's architectural facts. Grounding,
realization, and Specification/Evidence traceability are independently owned
edges with their own target and source domains.

```text
Component -- declaration --> versioned Component Capability
                                  |
                                  +-- grounding --> model element / term
                                  |
                                  +-- realization --> Operation
                                  |                   Workflow
                                  |                   StateMachine
                                  |
                                  +-- traceability --> Specification / Evidence
```

[R6](../spec/component-capability.md#r6-admitted-semantic-grounding)
defines grounding;
[R7](../spec/component-capability.md#r7-explicit-realization-edges)
defines realization;
[R9](../spec/component-capability.md#r9-specification-and-evidence-traceability)
defines traceability. Each edge family has its own meaning; the diagram is
structural context for those rules.

## Producer/framework/consumer handoff

| Participant | Structural responsibility | Normative authority |
| --- | --- | --- |
| Cozy | Syntax, IR, normalization, and Component Capability projection production | R1, R2, R5, R6 |
| CNCF | Component Capability semantics, version admission, realization binding, and discovery | R1, R3, R4, R7, R8 |
| Textus CBD Support | Ingestion, catalog, and query consumption | R1, R9, R10 |

[R10](../spec/component-capability.md#r10-admission-and-scoped-query-outcomes)
defines admission and query outcome distinctions across this handoff.
The [specification's implementation ownership](../spec/component-capability.md#implementation-ownership)
locates projection/admission, target binding, factory/package discovery, and
real producer/consumer proof within [Phase 78](../phase/phase-78.md).
