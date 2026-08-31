# Phase 61.1 - Generated Information Type Adoption

status=closed
planned_at=2026-08-30
split_from=[Phase 61](phase-61.md)
depends_on=[Phase 61](phase-61.md)
successor=[Phase 61.2](phase-61.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61.1 Checklist](phase-61.1-checklist.md)
consumes_handoff=accepted IC-02 canonical CML, generated package/type, managed-revision, and lifecycle-transition contract

## Goal

Complete IC-03: adopt the generated Information Entity/value/powertype family
in CNCF runtime while preserving only explicitly bounded compatibility adapters.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted Phase 61 IC-02 generated-output
  and transition contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6--8h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: separate closure prevents runtime adapters from redefining
  generator semantics
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 61

## Scope

- Replace the handwritten Information model in runtime signatures and snapshots
  with the generated canonical family.
- Preserve only evidence-backed source, binary, wire, and `InformationId`
  compatibility adapters.
- Reject ambiguous legacy payloads and add runtime/compile-time identity
  evidence.

## Closure

Runtime uses one generated Information family, all remaining adapters have
explicit removal criteria, and the accepted handoff lets Phase 61.2 bind the
canonical Entity to standard persistence/OCC without changing type identity.

Phase 61.1 is closed after the generated-runtime adoption Step commit, the
mandatory full review, and the focused closure review resolved lifecycle
creation-provenance and executable-spec metadata findings. The final full-suite
gate and this distinct release commit bind the closure under
`phase61.1-clb-ic03-20260831`. Phase 61.2 is the next planned consumer; it is
not started by this closure.

## Non-Goals

Entity repository binding, OCC semantics, curation lifecycle behavior,
projections, downstream migration, duplicate removal, and canonical closure.

## Current Status

IC-03 is complete. Phase 61.2 is the next planned consumer of the accepted
generated runtime identity and bounded adapter policy, and requires its own
explicit Phase invocation.
