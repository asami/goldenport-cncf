# Phase 78 - Component Capability External Contract

status=planned
planned_at=2026-09-16
reconciled_at=2026-09-18
development_item=DEV-011
predecessor=[Phase 77](phase-77.md)
checklist=[Phase 78 Checklist](phase-78-checklist.md)

## Purpose

Make provided and required Component Capability a first-class, externally
consumable Component specification contract. Capability remains a
non-instantiated model IR; CNCF admits and exposes its identity, semantics,
provenance, semantic grounding references, and explicit realization mapping
without executing Capability itself or parsing CML.

The reconciled SimpleModeling model distinguishes:

```text
Application Capability
  = stable application structure / development-management capability

Component Capability
  = architectural capability exposed/provided/required by Components
```

CNCF Phase 78 owns the latter. It must not absorb Application Capability
planning semantics, Use Case grouping, or application-management state into the
runtime Component contract.

## Model continuity

The intended continuity is:

```text
Goal / Use Case
      |
      v
Application Capability       (SimpleModeling / Cozy)
      |
      | requires / realized by
      v
Component Capability         (CNCF external contract)
      |
      +-- Operation
      +-- Workflow
      +-- StateMachine
```

Use Case and Application Capability may be many-to-many. CNCF does not infer
that relation and does not require Use Case Group containment. CNCF receives an
admitted Component Capability projection with stable identity and provenance
from Cozy.

## Semantic grounding boundary

Capability names are not sufficient semantic identity. When Cozy supplies
admitted references to Glossary / BoK or Domain Concepts, the CNCF projection
must preserve those references and their provenance in a versioned,
runtime-independent form suitable for discovery.

```text
Component Capability
      |
      +-- semantic references -> Glossary / BoK / Domain Concept
      |
      +-- realization refs ----> Operation / Workflow / StateMachine
```

CNCF does not build, reason over, or require a complete ontology. It preserves
and exposes admitted semantic references; unsupported relations remain absent.
Ontology-like organization is a bottom-up modeling concern upstream, not a
runtime admission requirement.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| CAP-78-01 | Freeze Component Capability identity, vocabulary, semantic-reference shape, versioning, and compatibility boundaries. | planned |
| CAP-78-02 | Define and implement the external Component projection for provided and required Capabilities, preserving admitted semantic grounding/provenance. | planned |
| CAP-78-03 | Bind explicit realization references to admitted Operation, Workflow, and StateMachine contracts without embedding orchestration procedure. | planned |
| CAP-78-04 | Integrate admission/discovery with ComponentFactory and package/CAR metadata, including semantic references where present. | planned |
| CAP-78-05 | Prove Cozy producer and Textus CBD Support consumer interoperability with exact identity, source, ABI, semantic-reference, and realization provenance. | planned |

## Ownership boundary

- SimpleModeling defines the conceptual role of Application Capability as the
  application structure / management model and its relation to Use Case.
- Cozy owns Capability syntax, Application/Component Capability IR, source
  normalization, validation, terminology/domain references, and generated
  Component Capability projection production.
- CNCF owns the Component Capability semantic contract, ABI/version admission,
  compatibility, realization references, package exposure, and
  runtime-independent discovery.
- Textus CBD Support owns catalog ingestion and presentation.
- Runtime invocation, authorization, state guards, and availability checks
  remain separate from Capability identity.

## Required behavior

- Provided and required Component Capabilities have stable qualified identity.
- Capability is non-instantiated and is not directly executable.
- Explicit realization references identify admitted Operation, Workflow, or
  StateMachine contracts; realization is mapping, not execution ordering.
- Admitted semantic references from Cozy survive CNCF normalization,
  packaging/CAR exposure, and discovery without name inference.
- Absence of a semantic reference or relation remains absence; CNCF does not
  synthesize ontology, grouping, actor, goal, or domain semantics.
- Application Capability lifecycle/development status is not projected into the
  Component Capability ABI unless a future contract explicitly requires a
  separate traceability surface.

## Completion conditions

- Design and specification documents define identity, provided and required
  declarations, semantic grounding references, realization mapping,
  compatibility, and failure boundaries.
- Executable specifications admit a valid generated projection and reject
  missing, duplicate, ambiguous, malformed, incompatible, or dangling admitted
  references as defined by the contract.
- ComponentFactory and the selected package/CAR surface expose matching
  semantics without name inference or CML reparsing.
- A real Cozy fixture is discoverable through CNCF and consumable by Textus CBD
  Support with exact source, ABI, semantic-reference, and realization
  provenance.
- Focused validation and review evidence are recorded without claiming
  downstream Phase closure.

## Non-goals

- Application Capability planning, lifecycle, or development-management state.
- Use Case Group or Use Case Map semantics.
- Building, completing, or reasoning over an ontology.
- Runtime Capability instances or Capability persistence.
- CML parsing or CML syntax ownership in CNCF.
- Workflow orchestration semantics inside Capability declarations.
- Permission, Authorization, Availability, or Guard consolidation.
- cbd-support UI/search implementation.

## References

- [Development note](../notes/component-capability-external-contract-proposal.md)
- [Development item journal](../journal/2026/09/2026-09-16-component-capability-external-contract-development-item.md)
- [Phase 78 Checklist](phase-78-checklist.md)
