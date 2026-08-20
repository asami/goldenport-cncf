# Phase 58 - Component and SubComponent Contract Freeze

status=closed
planned_at=2026-07-31
split_approved_at=2026-08-20
closed_at=2026-08-20
split_decision=D-58-SPLIT
depends_on=[Phase 57.5](phase-57.5.md)
successor=[Phase 58.1](phase-58.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58 Checklist](phase-58-checklist.md)
implementation_note=[Component and SubComponent Architecture Implementation Proposal](../notes/component-subcomponent-architecture-implementation.md)
resource_implementation_note=[Component Resource SubComponent Implementation Proposal](../notes/component-resource-subcomponent-implementation.md)
rsc01_a_handoff=[RSC01-A owner inventory and reconciliation](../notes/component-subcomponent-architecture-implementation.md)
rsc01_b_acceptance_registry=[RSC01-B failing-first acceptance registry](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md)
rsc01_a_accepted_commit=46f276fadaaef73495b9796153f656fe6d970e53
rsc01_a_journal=[RSC01-A1 contract-freeze journal](../journal/2026/08/2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md)

## Purpose

Freeze the architecture, ownership inventory, and failing-first acceptance
identities for the Component and SubComponent foundation. This Phase produces
the durable contract handoff that later Phase 58 children consume; it does not
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

RSC01-A is recorded as accepted at commit
`46f276fadaaef73495b9796153f656fe6d970e53`. Its A1 owner-inventory and
proposal-reconciliation
handoff is recorded in
[`component-subcomponent-architecture-implementation.md`](../notes/component-subcomponent-architecture-implementation.md)
and its design history and separate ledgers are recorded in
[`2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md`](../journal/2026/08/2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md).
The handoff preserves this Phase's RSC-01-only boundary and assigns exact
failing-first acceptance identities to RSC01-B. It does not promote either
implementation note to a canonical design/specification.

## RSC01-B Handoff

RSC01-B's exact handoff is the [RSC01-B failing-first acceptance
registry](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md). It
registers one authoritative acceptance row for each stable group RSC02 through
RSC09, including the owner Phase, repository, future path, suite or scripted
identity, scenario IDs, shared Component/profile identities, invariant
traceability, and RED-to-GREEN protocol. The registry creates no executable
tests and makes no red-build claim; each successor must materialize its exact
identity before its first production implementation edit. Phase 58 is closed
and stops before Phase 58.1 starts.

## Closure

RSC-01 closed on 2026-08-20 after its proposal, inventory, ownership map,
conflicts, and failing-first executable acceptance identities were recorded in
three accepted Steps:

- split delivery plan: `308fdd6d830c9aa1fdc8e1dcb6421e1f6ec0d57e`;
- RSC01-A architecture and owner freeze:
  `46f276fadaaef73495b9796153f656fe6d970e53`; and
- RSC01-B acceptance identity registry:
  `5a850db392471374379b89cb95972e07536f0139`.

The mandatory Phase review covered
`c9b39e57f249b610d7fdcd31f7ae7641b448e4d3..5a850db392471374379b89cb95972e07536f0139`
with binary-diff SHA-256
`d94e14551580763f61e0e056f53c3dc81e6038dc741fa08047615a90f249c9e1`
and returned PASS with no current blocker. Final generation required Cozy to
admit an explicitly selected mutable development pair without registering it
as immutable release evidence. That prerequisite was accepted in Cozy commit
`44a26c8194ff8668503cabd37c95acd7f240dc32`, published locally only as
`0.3.2-SNAPSHOT`, and covered by 28 compatibility and 100 package-boundary
tests. CNCF final invocation `46026-20260820T040438Z` selected
`0.5.3-SNAPSHOT` / `0.3.2-SNAPSHOT`, completed 444 suites, and passed
3,262 tests with no failure. A supplemental focused release-gate review of
only that post-review prerequisite and the CNCF generator coordinate returned
PASS; it was not a second mandatory Phase review.

The reviewed handoff is the sole design input for Phase 58.1. Phase 58.1
remains planned and unstarted.

## Non-Goals

- Registry, packaging, repository-admission, resolver, mode-policy, security,
  lifecycle, and consumer implementation.
- Help, AI, Component Admin, Textus CBD Support, BoK ingestion, or
  platform-specific child deployment.

## Handoff

Phase 58.1 consumes the reviewed RSC-01 proposal, affected-owner inventory,
failing-first matrix, and frozen invariants. It must stop rather than
reinterpret an unresolved RSC-01 decision.
