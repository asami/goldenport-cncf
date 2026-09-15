# Phase 69.4 - Lightweight JobDefinition Lifecycle

status=closed
planned_at=2026-09-09
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69.3](phase-69.3.md)
successor=[Phase 69.5](phase-69.5.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.4 Checklist](phase-69.4-checklist.md)
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-69.7
aggregate_validation_sequence=["PHASE-69.4","PHASE-69.5","PHASE-69.6","PHASE-69.7"]

## Purpose

Provide the small, usable direct JobDefinition management lifecycle without
changing the immutable snapshots bound to running Jobs.

## Scope and Closure

Owns `JM69-06`: direct create/update/activate/retire/get/search lifecycle,
immutable Job snapshots, and EntityStore-only internal conditional saves. The
closed Phase supplies its lightweight direct definition lifecycle contract to
the still-planned Phase 69.5.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69.3. Successor: Phase
69.5. Consumes immutable JCL semantics and produces directly managed definition
snapshots.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-06 | Lightweight JobDefinition lifecycle | DONE |

Focused lifecycle and immutable-snapshot specifications are required. The
repository full suite is deliberately deferred to the declared aggregate final
owner, Phase 69.7; this Phase still requires focused validation, independent
review, and its own release commit.

## Closure Evidence

- `P69.4-JM69-06-PHASE-TEST-FIX-001-FOCUSED-VAL-002` passed the focused
  JobControl lifecycle and DurableJobProjection specifications with `sbt_exit=0`
  and the shared SBT lock released.
- `FULL_REVIEW-PHASE-69.4-LIGHTWEIGHT-001` found the bounded repair items;
  `PHASE-69.4 / JM69-06 / PHASE_TEST_FIX-001 / RE_REVIEW-002` closed all of
  them with no Current Boundary Blocker.
- This release records `repository_full_suite=deferred-not-run`; Phase 69.7
  remains the explicit aggregate repository-full-suite owner for the serial
  Phase 69.4–69.7 delivery chain.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes Phase 69.3 executable definition semantics; produces lightweight immutable snapshots for Phase 69.5
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--7h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: preserves a small, directly usable lifecycle without carrying unproven governance machinery
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Non-Goals

- Reopening JCL language/runtime decisions, changing running Job snapshots, or
  adding governance accept/apply, review, promotion, rollout, rollback, audit,
  content history, alternate-revision, digest, or canonical-content control.
  Those capabilities are deliberately retired from the Phase 69 sequence; any
  future need requires newly authorized Phase work.

## Planning References

- [Phase 69.3](phase-69.3.md)
- [Phase 69.4 Checklist](phase-69.4-checklist.md)
- [Phase 69.5](phase-69.5.md)
