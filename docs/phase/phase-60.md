# Phase 60 - Component Admin and Documentation Visibility

status=planned
planned_at=2026-07-31
depends_on=[Phase 59](phase-59.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 60 Checklist](phase-60-checklist.md)
implementation_note=[Component Admin and Documentation Visibility Implementation Proposal](../notes/component-admin-documentation-visibility-implementation.md)
planning_journal=[Component Admin and Documentation Visibility Planning (historical Phase 58)](../journal/2026/07/2026-07-31-phase-58-component-admin-documentation-visibility-planning.md)

## Purpose

Provide one Component-owned Admin surface that makes the complete operational
and descriptive state of a loaded Component visible and, where explicitly
authorized, manageable.

Phase 60 consumes:

- Phase 55 effective configuration values and provenance;
- Phase 58 resolved Resource SubComponent inventory, state, integrity, and
  provenance; and
- Phase 59 Component knowledge and model manifests.

Admin must use those contracts rather than reconstructing Component state by
scanning CARs, repositories, development directories, source trees, or
documentation artifacts independently.

## Dependency

Phase 60 begins after Phase 59 closes.

Phase 58 supplies the physical Resource SubComponent resolver and Phase 59
supplies the documentation/model knowledge contract. Phase 60 is their
operator-facing consumer and does not reopen either foundation.

## Selected Direction

- Help remains the human and AI knowledge entry point.
- Admin is the operator-facing runtime inspection and management surface.
- Both use the same Component identity, resource, integrity, availability,
  and provenance contracts.
- Admin distinguishes Component class, loaded Component instance, Subsystem,
  and implicit Component Subsystem identities.
- Admin shows effective configuration values together with their typed value,
  scope, winning binding, overridden bindings, and provenance.
- Admin exposes Component model metadata for Entity, Powertype, StateMachine,
  Value, Datatype, and their relationships, including deterministic class and
  state diagrams supplied by Phase 59.
- Admin exposes Service, Operation, SPI, capability, dependency, datastore,
  schema, collection, lifecycle, health, and ClassLoader information where
  the runtime owns authoritative evidence.
- Documentation and SourceCode SubComponents are shown as resources of the
  logical Component release, not as executable Components.
- Management actions require explicit Operation authorization, audit, and
  lifecycle safety. Resource discovery never grants management authority.
- Missing, remote, restricted, unavailable, incompatible, stale, and corrupt
  resources remain visibly distinct.

## Scope

- Define a versioned Component Admin view model and discovery contract.
- Present Component class, release, instance, Subsystem, and implicit
  Component Subsystem identity without conflation.
- Present primary CAR plus Documentation and SourceCode SubComponent
  composition, availability, integrity, access state, and provenance through
  the Phase 58 resolver.
- Present Phase 59 manuals, Help, Scaladoc, source availability, model
  metadata, diagrams, schemas, examples, and troubleshooting navigation.
- Present Phase 55 effective configuration and binding provenance.
- Present Service, Operation, SPI, capability, dependency, runtime, health,
  lifecycle, ClassLoader, datastore, schema, collection, and Entity
  collection information.
- Define basic read-only inspection separately from authorized management
  actions.
- Define HTTP, Web, CLI, and machine-readable Admin projections from one view
  model.
- Define authorization, redaction, audit, path safety, integrity, disclosure,
  caching, multi-instance, and failure behavior.
- Validate representative standalone and multi-user Subsystem operation
  without exposing mode branches to Component implementation.

## Non-Goals

- Reimplementing Resource SubComponent resolution or repository access.
- Reimplementing Help, manuals, AI retrieval, CBD Support, or BoK.
- Generating documentation, source archives, model metadata, or diagrams.
- Treating Admin as an alternate configuration authority.
- Inferring Component metadata from arbitrary source or artifact scanning.
- Granting management authority because a resource or Operation is visible.
- Building a general external observability platform.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ADM-01 | Inventory and executable contract freeze | Existing Admin, Help, configuration, model, runtime, and management surfaces plus exact failing-first acceptance identities are fixed. | planned |
| ADM-02 | Identity and Admin view model | One versioned model distinguishes class, release, instance, Subsystem, implicit Subsystem, and physical resource identities. | planned |
| ADM-03 | Configuration and composition visibility | Effective typed configuration/provenance and primary/Documentation/SourceCode composition are projected from Phase 55/58 contracts. | planned |
| ADM-04 | Component contract and model visibility | Service, Operation, SPI, capability, schema, model types, relationships, and Phase 59 diagrams are visible without reconstruction. | planned |
| ADM-05 | Runtime and datastore visibility | Dependency, ClassLoader, lifecycle, health, datastore, collection, and Entity collection evidence is projected from authoritative runtime state. | planned |
| ADM-06 | Documentation navigation | Admin links exact manuals, Help, Scaladoc, source availability, examples, and troubleshooting resources through the Phase 59 manifest. | planned |
| ADM-07 | Authorized management | Explicitly admitted management actions enforce Operation authorization, audit, lifecycle safety, and deterministic failure. | planned |
| ADM-08 | Surface and security acceptance | Web, HTTP, CLI, machine-readable, standalone, multi-user, multi-instance, redaction, and hostile-input profiles pass. | planned |
| ADM-09 | Canonical documentation and closure | Verified behavior is promoted to design/specification and all planning records are reconciled. | planned |

## Acceptance

- Admin identifies exactly which Component class, release, instance, and
  Subsystem context is being inspected.
- Admin consumes the Phase 58 resolver; it does not scan physical resource
  locations independently.
- Admin consumes the Phase 59 knowledge/model manifest; it does not regenerate
  manuals, metadata, or diagrams.
- Effective configuration shows the winning typed binding, overridden
  bindings, scope, and provenance without duplicating public `textus` and
  internal `cncf` parameters.
- Primary CAR, Documentation SubComponent, and SourceCode SubComponent state
  is accurate and does not claim remote or restricted content is local.
- Entity, Powertype, StateMachine, Value, Datatype, relationships, class
  diagrams, and state diagrams are navigable from the Component.
- Service, Operation, SPI, capability, dependency, runtime, datastore,
  schema, collection, health, and lifecycle views use authoritative evidence.
- Read-only visibility grants no Operation or resource access authority.
- Management actions require explicit authorization and produce attributable
  audit evidence.
- Standalone and multi-user operation project identity through
  `ExecutionContext`; Component implementation remains mode-independent.
- Multiple loaded versions and instances do not cross-wire configuration,
  resources, runtime state, or management actions.
- Final design/specification and executable evidence agree before closure.

## Planning References

- [Phase 55 - Configuration Binding and Provenance](phase-55.md)
- [Phase 58 - Component Resource SubComponent Foundation](phase-58.md)
- [Phase 59 - Component Documentation and AI Knowledge Integration](phase-59.md)
- [Implementation Proposal](../notes/component-admin-documentation-visibility-implementation.md)
- [Planning Journal](../journal/2026/07/2026-07-31-phase-58-component-admin-documentation-visibility-planning.md)

## Current Status

Phase 60 is planned and must not start before Phase 59 closes.
