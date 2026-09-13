# Phase 69.6 - User and Operator Job Experience

status=planned
planned_at=2026-09-09
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69.5](phase-69.5.md)
successor=[Phase 69.7](phase-69.7.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.6 Checklist](phase-69.6-checklist.md)
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-69.7
aggregate_validation_sequence=["PHASE-69.4","PHASE-69.5","PHASE-69.6","PHASE-69.7"]

## Purpose

Deliver authorized user, application, and administrative Job views and actions
through the canonical management contract without a parallel Job model.

## Scope and Closure

Owns `JM69-08`: My Jobs/application/admin projections, user vocabulary,
notification/read state, diagnostics and recovery operations, descriptor-backed
Web/Help/API surfaces, accessibility, polling, CSRF, redaction, and executable
specifications. Closes with UX contracts for final operational hardening.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69.5. Successor: Phase
69.7. Consumes canonical management and CompositeQuery contracts and produces
validated user/operator projections.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-08 | User and operator Job experience | planned |

Focused isolation, notification/read state, accessibility, control, recovery,
pagination, and provider-failure specifications are required. The repository
full suite is deliberately deferred to the declared aggregate final owner,
Phase 69.7; this Phase still requires focused validation, independent review,
and its own release commit.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes Phase 69.2 management and Phase 69.5 composition contracts; produces authorized UX evidence for Phase 69.7
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--7h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: keeps subject/app/admin policy separate from final security and retention operations
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Non-Goals

- Application-specific workflow or notification delivery implementation in JobEngine.

## Planning References

- [Phase 69.2](phase-69.2.md)
- [Phase 69.5](phase-69.5.md)
- [Phase 69.6 Checklist](phase-69.6-checklist.md)
- [Phase 69.7](phase-69.7.md)
