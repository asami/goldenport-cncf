# Phase 78 - Component Capability External Contract

status=planned
planned_at=2026-09-16
development_item=DEV-011
predecessor=[Phase 77](phase-77.md)
checklist=[Phase 78 Checklist](phase-78-checklist.md)

## Purpose

Make provided and required Capability a first-class, externally consumable
Component specification contract. Capability remains a non-instantiated model
IR; CNCF admits and exposes its identity, semantics, provenance, and explicit
realization mapping without executing Capability itself or parsing CML.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| CAP-78-01 | Freeze Capability identity, vocabulary, versioning, and compatibility boundaries. | planned |
| CAP-78-02 | Define and implement the external Component projection for provided and required Capabilities. | planned |
| CAP-78-03 | Bind explicit realization references to admitted Operation, Workflow, and StateMachine contracts. | planned |
| CAP-78-04 | Integrate admission/discovery with ComponentFactory and package/CAR metadata. | planned |
| CAP-78-05 | Prove Cozy producer and Textus CBD Support consumer interoperability. | planned |

## Ownership boundary

- CNCF owns the semantic contract, ABI/version admission, compatibility,
  realization references, package exposure, and runtime-independent discovery.
- Cozy owns Capability syntax, source normalization, validation, and generated
  projection production.
- Textus CBD Support owns catalog ingestion and presentation.
- Runtime invocation, authorization, state guards, and availability checks
  remain separate from Capability identity.

## Completion conditions

- Design and specification documents define identity, provided and required
  declarations, realization mapping, compatibility, and failure boundaries.
- Executable specifications admit a valid generated projection and reject
  missing, duplicate, ambiguous, malformed, and incompatible declarations.
- ComponentFactory and the selected package/CAR surface expose matching
  semantics without name inference or CML reparsing.
- A real Cozy fixture is discoverable through CNCF and consumable by Textus
  CBD Support with exact source and ABI provenance.
- Focused validation and review evidence are recorded without claiming
  downstream Phase closure.

## Non-goals

- Runtime Capability instances or Capability persistence.
- CML parsing or CML syntax ownership in CNCF.
- Workflow orchestration semantics inside Capability declarations.
- Permission, Authorization, Availability, or Guard consolidation.
- cbd-support UI/search implementation.

## References

- [Development note](../notes/component-capability-external-contract-proposal.md)
- [Development item journal](../journal/2026/09/2026-09-16-component-capability-external-contract-development-item.md)
- [Phase 78 Checklist](phase-78-checklist.md)
