# Phase 69.5 - CompositeQuery v2

status=planned
planned_at=2026-09-09
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69.4](phase-69.4.md)
successor=[Phase 69.6](phase-69.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.5 Checklist](phase-69.5-checklist.md)
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-69.7
aggregate_validation_sequence=["PHASE-69.4","PHASE-69.5","PHASE-69.6","PHASE-69.7"]

## Purpose

Deliver bounded, deterministic, secure, cancellable, diagnosable parallel and
cross-subsystem CompositeQuery composition.

## Scope and Closure

Owns `JM69-07`: v1 inventory, typed branch/dependency semantics,
cross-subsystem protocol, bounded parallel execution, ordering, Job policy,
diagnostics, and executable specifications. Closes with a CompositeQuery v2
handoff for the user/operator experience.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69.4. Successor: Phase
69.6. Consumes the Phase 69.4 lightweight direct JobDefinition lifecycle
contract and produces the v2 composition contract.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-07 | CompositeQuery v2 | planned |

Focused ordering, bounds, cancellation, failure, authorization, and
cross-subsystem specifications are required. The repository full suite is
deliberately deferred to the declared aggregate final owner, Phase 69.7; this
Phase still requires focused validation, independent review, and its own
release commit.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes the Phase 69.4 lightweight direct JobDefinition lifecycle contract; produces CompositeQuery v2 semantics for Phase 69.6
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--7h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: keeps query-composition execution independent of UI projection ownership
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Non-Goals

- Presentation composition in Domain logic or persistence of ordinary Query payloads by default.

## Planning References

- [Phase 69.4](phase-69.4.md)
- [Phase 69.5 Checklist](phase-69.5-checklist.md)
- [Phase 69.6](phase-69.6.md)
