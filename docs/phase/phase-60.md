# Phase 60 - Component Admin and Documentation Visibility

status=planned
planned_at=2026-07-31
depends_on=[Phase 59.10](phase-59.10.md)
successor=[Phase 60.1](phase-60.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 60 Checklist](phase-60-checklist.md)
implementation_note=[Component Admin and Documentation Visibility Implementation Proposal](../notes/component-admin-documentation-visibility-implementation.md)
adm01_handoff=[Phase 60 ADM-01A — Component Admin Inventory and Failing-First Contract](../notes/phase-60-adm01-component-admin-inventory-and-failing-first-contract.md)
planning_journal=[Component Admin and Documentation Visibility Planning (historical Phase 58)](../journal/2026/07/2026-07-31-phase-58-component-admin-documentation-visibility-planning.md)
canonical_architecture_design=[Component and Subcomponent Architecture](../design/component-subcomponent-architecture.md)
canonical_architecture_specification=[Component and Subcomponent Architecture Specification](../spec/component-subcomponent-architecture.md)
canonical_resource_design=[Component Resource Subcomponent](../design/component-resource-subcomponent.md)
canonical_resource_specification=[Component Resource Subcomponent Specification](../spec/component-resource-subcomponent.md)

## Split Record

On 2026-08-28, user decision `D-P60-SPLIT-001` partitioned the formerly
oversized Phase 60. The pre-split planning gate estimated the full ADM-01
through ADM-09 path at 40--55 hours and required independently closable
delivery units. This retained Phase owns ADM-01; Phases 60.1 through 60.8 own
ADM-02 through ADM-09 sequentially. The split adds planning, handoff, review,
validation, and release overhead, but isolates the one inventory/contract
freeze before protected implementation and keeps every unit within six hours.

Profile-transition handoff: Phase 60 produces the reviewed ADM-01 inventory,
identity ambiguity record, no-scan map, Help/Admin boundary, and failing-first
acceptance registry. Each later Phase consumes its predecessor's accepted
handoff and does not rediscover resolved Phase 55, Phase 58, or Phase 59
contracts.

Pre-split gate evidence (2026-08-28): `SPLIT_REQUIRED`, time-bound and
reasoning-cost isolation, conservative estimate 40--55 hours, critical path
ADM-01 -> ADM-02 -> ADM-03/04 -> ADM-05/06 -> ADM-07 -> ADM-08 -> ADM-09.
This is historical gate evidence; the current structural gate follows.

## Goal

Freeze the existing Admin, Help, configuration, model, runtime, datastore, and
management ownership map and exact failing-first acceptance registry without
implementing an Admin view, resolver, route, management action, or runtime
projection.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: reconcile the inherited identity, no-scan,
  authority, and acceptance boundaries into one ADM-01 contract handoff
- frozen_profile_transition_handoff: Phase 55 configuration provenance, Phase
  58 resolved resource contract, and Phase 59 knowledge/model manifest
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 60

## Purpose

Freeze the inventory and executable contract that later enables one
Component-owned Admin surface to make the operational and descriptive state of
a loaded Component visible and, where explicitly authorized, manageable.

Phase 60 consumes:

- Phase 55 effective configuration values and provenance;
- the same four-document Phase 58 canonical identity/resource contract and its
  already-resolved Subcomponent Component inventory, state, integrity, and
  provenance; and
- Phase 59 series Component knowledge and model manifests, closed by Phase
  59.10.

Admin must use those contracts rather than reconstructing Component state by
scanning CARs, repositories, development directories, source trees, or
documentation artifacts independently.

## Dependency

Phase 60 begins after Phase 59.10 closes the Phase 59 series.

The Phase 58 series supplies the canonical identity/resource contract and its
already-resolved output, and the Phase 59 series supplies the
documentation/model knowledge contract. Phase 60 is their operator-facing
consumer: it does not independently
scan or resolve resources, broaden the resource/mode policy, or reopen either
foundation.

References to “Phase 58” below mean the full Phase 58 series, whose closure is
Phase 58.9.

## Journal Candidate Merge

Phase 60 absorbs `DEV-004` from the Phase 52 direct-Admin canonical-ID journal.
Any Phase 60 Admin surface that accepts an Entity `id` must declare its backing
`EntityCollection` and require exact collection equality before resolution. It
must reject a scalar locator, foreign canonical-ID rebinding, entropy fallback,
missing owner, and ambiguous owner rather than treating any of them as a
compatibility shortcut.

## Series Invariants

ADM-01 records these as constraints for the sequential child Phases; it does
not implement their projections or management actions.

- Help remains the human and AI knowledge entry point.
- Admin is the operator-facing runtime inspection and management surface.
- Both use the same canonical Component identity/resource contract and its
  already-resolved projection, including integrity, availability, and
  provenance.
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
- Documentation and SourceCode Subcomponent CARs are shown with their own
  Component identities, payload-resource state, and parent relationship; Admin
  visibility does not activate them.
- Management actions require explicit Operation authorization, audit, and
  lifecycle safety. Resource discovery never grants management authority.
- Missing, remote, restricted, unavailable, incompatible, stale, and corrupt
  resources remain visibly distinct.

## Scope

- Inventory existing Admin, Help, configuration, model, runtime, datastore,
  and management surfaces.
- Record class/release/instance/Subsystem identity ambiguities and every direct
  physical-resource, repository, source, or documentation scan.
- Freeze Help as the knowledge surface and Admin as the operator surface.
- Register exact failing-first acceptance identities for the later ADM stages.

## Non-Goals

- Implementing a view model, projection, configuration/resource consumer,
  model/runtime view, navigation, management action, or acceptance surface.
- Reimplementing resource resolution, Help, manuals, AI retrieval, CBD
  Support, BoK, documentation generation, or source scanning.
- Granting management authority or changing operation-mode policy.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ADM-01 | Inventory and executable contract freeze | Existing Admin, Help, configuration, model, runtime, and management surfaces plus exact failing-first acceptance identities are fixed. | planned |

## Acceptance

- One reviewed ADM-01 inventory identifies existing surfaces, identity
  ambiguities, direct scans, owners, Help/Admin division, and exact
  failing-first acceptance identities.
- The accepted ADM-01 handoff gives each later child a single source for its
  inherited no-scan, identity, authority, and validation constraints.

## Planning References

- [Phase 55 - Configuration Binding and Provenance](phase-55.md)
- [Phase 58 series, closing in Phase 58.9](phase-58.9.md)
- [Phase 59.10 - Canonical Documentation and Phase Closure](phase-59.10.md)
- [Implementation Proposal](../notes/component-admin-documentation-visibility-implementation.md)
- [Planning Journal](../journal/2026/07/2026-07-31-phase-58-component-admin-documentation-visibility-planning.md)
- [Phase 52 direct-Admin canonical-ID boundary](../journal/2026/07/2026-07-30-phase-52-direct-admin-canonical-id-boundary-consideration.md)

## Current Status

Phase 60 is planned. Phase 59.10 is closed; ADM-01 is the first independently
closable unit of the approved Phase 60 series.
