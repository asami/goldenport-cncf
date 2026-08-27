# Phase 60.1 - Component Admin Identity and View Model

status=in-progress
started_at=2026-08-28
split_from=[Phase 60](phase-60.md)
depends_on=[Phase 60](phase-60.md)
successor=[Phase 60.2](phase-60.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 60.1 Checklist](phase-60.1-checklist.md)
consumes_handoff=ADM-01 reviewed inventory, identity ambiguity record, no-scan map, Help/Admin boundary, and failing-first acceptance registry

## Goal

Define one versioned Component Admin view model that distinguishes Component
class, release, loaded instance, Subsystem, implicit Subsystem, resource
provenance, and unavailable/failure state without silently selecting another
instance or version.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted ADM-01 inventory and acceptance registry
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 60

## Scope

- Define the versioned Admin view model and its identity/provenance axes.
- Preserve unavailable, forbidden, stale, incompatible, and corrupt states.
- Add codec, compatibility, ambiguity, and multi-instance executable evidence.

## Closure

One reviewed view model is the only identity-bearing input to later Admin
projections. Phase 60.2 consumes the accepted model and does not redefine
identity or resource resolution.

## Non-Goals

Configuration/resource projection, model/runtime visibility, documentation
navigation, management, surface delivery, and canonical documentation closure.

## Current Status

Phase 60.1 is in progress.
