# Phase 61.3 - Information Curation and Knowledge Lifecycle Migration

status=closed
planned_at=2026-08-30
closed_at=2026-09-01
split_from=[Phase 61](phase-61.md)
depends_on=[Phase 61.2](phase-61.2.md)
successor=[Phase 61.4](phase-61.4.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61.3 Checklist](phase-61.3-checklist.md)
consumes_handoff=accepted IC-04 Entity repository, managed revision, and atomic OCC contract

## Goal

Complete IC-05: preserve Phase 26/27 Information curation, authorization, Tag,
publication, conflict, and Knowledge materialization behavior on the generated
revision-aware Entity.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted Phase 61.2 persistence/OCC and
  generated lifecycle contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6--8h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: consumes settled storage semantics before projection work
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 61

## Scope

- Migrate curation operations and lifecycle transitions onto the canonical
  generated Entity and accepted OCC boundary.
- Preserve Information-specific capability checks, identity distinction, Tag
  behavior, provider-failure semantics, and Knowledge materialization.
- Add lifecycle property/matrix and Phase 26/27 regression evidence.

## Closure

All curation and Knowledge lifecycle behavior is preserved on the generated
Entity with the accepted persistence/OCC semantics. Phase 61.4 consumes the
behavioral handoff for access-surface projections.

Phase 61.3 closes IC-05 under `phase61.3-clb-ic05-20260901` after the accepted
IC-05 Step, mandatory Phase review, and two focused closure-repair cycles. The
first cycle made every InformationSpace lifecycle transition admit the
generated CML before persistence or cache mutation; the second aligned editor
action availability with that same lifecycle. The distinct release commit binds
the final full-suite evidence: `sbt --batch test` passed 3,510 tests in 474
suites with no failure (`96580-20260831T175306Z`). It also binds the
nonblocking Hygiene ledger. Phase 61.4 remains planned and is not started by
this closure.

## Non-Goals

New access surfaces, downstream migration, duplicate removal, or design/spec
promotion.

## Current Status

IC-05 is complete. Information curation, Tag binding, publication, conflict,
and Knowledge materialization behavior now preserve the generated Entity,
revision/OCC, and CML lifecycle contracts. Phase 61.4 is the next planned
consumer and requires its own explicit Phase invocation.
