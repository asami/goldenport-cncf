# Phase 58 - Component and SubComponent Contract Freeze

status=in_progress
planned_at=2026-07-31
split_approved_at=2026-08-20
split_decision=D-58-SPLIT
depends_on=[Phase 57.5](phase-57.5.md)
successor=[Phase 58.1](phase-58.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58 Checklist](phase-58-checklist.md)
implementation_note=[Component and SubComponent Architecture Implementation Proposal](../notes/component-subcomponent-architecture-implementation.md)
resource_implementation_note=[Component Resource SubComponent Implementation Proposal](../notes/component-resource-subcomponent-implementation.md)
rsc01_a_handoff=[RSC01-A owner inventory and reconciliation](../notes/component-subcomponent-architecture-implementation.md)
rsc01_a_journal=[RSC01-A1 contract-freeze journal](../journal/2026/08/2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md)

## Purpose

Freeze the architecture, ownership inventory, and failing-first acceptance
identity for the Component and SubComponent foundation. This Phase produces the
durable contract handoff that later Phase 58 children consume; it does not
implement the registry, archive, repository, resolver, or runtime policy.

## Approved Split

On 2026-08-20 the user approved Decision Request `D-58-SPLIT`. The former
ten-stage Phase exceeded the six-hour boundary (estimated 36–60 hours) and
combined one open-ended architecture/ownership kernel with later protected
implementation and settled validation work.

| Phase | Owned closure | Parent profile | Estimate |
| --- | --- | --- | --- |
| 58 | RSC-01 architecture, inventory, and acceptance freeze | Sol / high | 3–5h |
| [58.1](phase-58.1.md) | RSC-02 identity and composition codec | Terra / xhigh | 4–6h |
| [58.2](phase-58.2.md) | RSC-03 packaging and publication completeness | Terra / xhigh | 4–6h |
| [58.3](phase-58.3.md) | RSC-04 resolution, activation boundary, and provenance | Terra / xhigh | 4–6h |
| [58.4](phase-58.4.md) | RSC-05 operation-mode and development composition | Terra / high | 3–5h |
| [58.5](phase-58.5.md) | RSC-06 authorization, disclosure, and integrity | Terra / xhigh | 3–5h |
| [58.6](phase-58.6.md) | RSC-07 lifecycle, concurrency, and observability | Terra / xhigh | 3–5h |
| [58.7](phase-58.7.md) | RSC-08 downstream consumer contract | Terra / high | 3–5h |
| [58.8](phase-58.8.md) | RSC-09 end-to-end cross-repository validation | Terra / high | 3–5h |
| [58.9](phase-58.9.md) | RSC-10 canonical promotion and closure | Terra / high | 2–4h |

Pre-split gate evidence: `SPLIT_REQUIRED`; the critical path was the ten ordered
RSC stages across CNCF, Cozy, sbt-cozy, Component Repository, and sample
owners. The expensive reasoning kernel is owned only by this Phase: reconcile
the non-normative proposals, discover actual owner boundaries, and freeze the
parent/Subcomponent, payload/platform, registry, publication, activation,
resolver, provenance, operation-mode, and consumer invariants. Later children
consume explicit handoffs and do not rediscover those decisions.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: open-ended-discovery
- recommended_parent_profile: gpt-5.6-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: reconcile architecture alternatives, repository ownership, and acceptance evidence into one durable RSC-01 handoff
- frozen_profile_transition_handoff: none
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Reconcile the two non-normative Component/Subcomponent proposals.
- Inventory CAR/SAR layouts, manifests, repository/cache behavior, resolver
  precedence, Help/Admin assumptions, and source/archive evidence.
- Freeze the parent Component, independently identifiable Subcomponent
  Component, payload, external-platform, and Subsystem distinctions.
- Freeze the initial role, release-identity, publication/activation, and
  failing-first acceptance boundaries for the remaining children.

## RSC01-A Handoff

RSC01-A is in progress. Its A1 owner-inventory and proposal-reconciliation
handoff is recorded in
[`component-subcomponent-architecture-implementation.md`](../notes/component-subcomponent-architecture-implementation.md)
and its design history and separate ledgers are recorded in
[`2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md`](../journal/2026/08/2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md).
The handoff preserves this Phase's RSC-01-only boundary and assigns exact
failing-first acceptance identities to RSC01-B. It does not promote either
implementation note to a canonical design/specification.

## Closure

RSC-01 closes only when its proposal, inventory, ownership map, conflicts, and
failing-first executable acceptance identities are recorded. Its reviewed
handoff is the sole design input for Phase 58.1.

## Non-Goals

- Registry, packaging, repository-admission, resolver, mode-policy, security,
  lifecycle, and consumer implementation.
- Help, AI, Component Admin, Textus CBD Support, BoK ingestion, or
  platform-specific child deployment.

## Handoff

Phase 58.1 consumes the reviewed RSC-01 proposal, affected-owner inventory,
failing-first matrix, and frozen invariants. It must stop rather than
reinterpret an unresolved RSC-01 decision.
