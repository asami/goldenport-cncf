# Phase 59.5 - Unified Help and Direct AI Access

status=planned
split_from=[Phase 59](phase-59.md)
depends_on=[Phase 59.4](phase-59.4.md)
successor=[Phase 59.6](phase-59.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59.5 Checklist](phase-59.5-checklist.md)
consumes_handoff=accepted DOC-04 resolved knowledge and development-context contract

## Goal

Implement DOC-05: unified human Help and direct AI manifest/resource access
over the same resolved Component space, including the stable read-only contract
that Phase 60 may later consume.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted DOC-04 resolved knowledge and development-context contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 59

## Scope

- Define Help discovery, navigation, HTTP, CLI, authorization, visibility, and
  hostile-content behavior for exact manifest resources.
- Project Phase 58 resource states and development context without a second
  fetch, resolver, archive scan, or source mount.
- Publish the read-only manifest/resource contract, but do not implement
  Phase 60 Admin views or management actions.

## Closure

Humans and AI consume one verified resolved resource space and expose the same
stable consumer contract. Phase 59.6 consumes that exact contract.

## Non-Goals

CBD Support implementation before its own Phase, BoK integration, profile
acceptance, final security/release closure, and Phase 60 behavior.
