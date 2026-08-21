# Phase 58.7 - Resolved-Resource Downstream Consumer Contract

status=planned
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.6.1](phase-58.6.1.md)
successor=[Phase 58.8](phase-58.8.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.7 Checklist](phase-58.7-checklist.md)
consumes_handoff=Phase 58.6.1 complete RSC-07 lifecycle, diagnostics, and provenance contract

## Goal

Prove a stable, read-only resource and provenance contract for Help and Admin
fixtures without allowing either consumer to scan physical artifacts or grant
content or activation authority.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: Phase 58.6.1 complete RSC-07 stable resource, provenance, and diagnostics contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Define read-only inventory and authorized content-access projections.
- Prove Help and Admin fixtures consume the same identity, availability,
  integrity, and provenance contract.
- Prohibit direct CAR, Subcomponent, cache, repository, and development-tree
  scans by either consumer.

## Closure

Help and Admin fixtures consume identical identity, availability, integrity,
and provenance projections; inventory visibility remains distinct from content
access. Phase 58.8 consumes the common consumer acceptance fixtures.

## Non-Goals

Final Help/AI or Admin presentation, content authoring, direct resource scans,
or a second repository/resolver path.
