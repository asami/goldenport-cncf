# Phase 75 - Component/Service Purpose and Operation Statefulness

status=planned
planned_at=2026-09-14
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#962-service-purpose-and-operation-statefulness)
checklist=[Phase 75 Checklist](phase-75-checklist.md)

## Purpose

Introduce component/service purpose and independently overridable operation
statefulness as explicit, inspectable contracts. Establish component-supplied
application/domain defaults and preserve them through generated/runtime metadata.

## Ownership and Scope

- CNCF owns this phase, effective resolution, execution-contract validation,
  runtime consumption, and acceptance coordination.
- Shared protocol definitions remain with their upstream owner in
  simplemodeling-lib. SP75-01 freezes any necessary upstream update boundary.
- Simple-modeler/Cozy generator edges are admitted only after inventory, with
  representative producer/consumer evidence rather than ecosystem-wide migration.
- All source implementation remains unstarted by this documentation addition.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| SP75-01 | Owner APIs, metadata paths, compatibility boundary, and executable acceptance frozen; proposal promoted to design/spec. | planned |
| SP75-02 | Component/service purpose, statefulness declarations, defaults, inheritance, overrides, and structured validation implemented. | planned |
| SP75-03 | Effective contracts preserved in generated/runtime metadata and inspection, with checkable resident-state mismatches. | planned |
| SP75-04 | Producer/consumer specifications, compatibility evidence, final review, and release closure. | planned |

No work item is active. Resume at SP75-01 when this phase is selected.
Refine bounded work estimates and exact implementation profiles during inventory.

## Completion Conditions

- Component purpose supports domain/application/both with default domain.
  A service's explicit purpose wins; omitted purpose inherits a single-purpose
  component. SP75-01 fixes the mixed-component omission policy.
- Effective application/domain service purpose supplies stateless/stateful
  defaults respectively, independently of component purpose after resolution.
- Explicit service values and operation overrides resolve by one documented
  precedence rule and retain declaration provenance through round trips.
- Query/command stays independent; invalid explicit values fail structurally.
- Generated metadata, runtime inspection, and execution-contract checks agree
  on effective values without claiming static proof of arbitrary implementations.
- Unannotated existing definitions preserve compatible invocation behavior.
- A stateless application command using durable domain updates is covered,
  alongside a stateful requirement and a known stateless/resident-state mismatch.
- Owner design/spec promotion, executable evidence, required suites, review,
  and release records satisfy the checklist closure gates.

## Exclusions

Cloud-provider adapters or deployment, a new query/command replacement,
orchestrator flags, new session/continuous enums, automatic restart persistence,
and unrelated migrations are outside this phase.

## Coordination and References

Existing Phase 69, Phase 73, and Phase 74 work retains its status and ownership.
Freeze any shared source boundary after its current edits settle.

- [Design note](../notes/service-purpose-and-operation-statefulness-design-note.md)
- [Discussion journal](../journal/2026/09/2026-09-14-service-purpose-and-operation-statefulness.md)
- [Existing application modeling note](../notes/cml-application-modeling-guideline.md)
