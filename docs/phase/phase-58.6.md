# Phase 58.6 - Resource Lifecycle, Concurrency, and Observability

status=planned
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.5](phase-58.5.md)
successor=[Phase 58.7](phase-58.7.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.6 Checklist](phase-58.6-checklist.md)
consumes_handoff=RSC-06 authorization, integrity, and disclosure constraints

## Goal

Make resolver and resource lifecycle behavior bounded, idempotent, safe under
concurrency, and observable without exposing sensitive evidence.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-06 authorization and non-leakage invariants
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Define cache reuse, refresh, invalidation, load/release/unload, and shutdown
  ownership.
- Prove safe multi-instance and concurrent resolution, including cancellation
  and refresh races.
- Add bounded, non-sensitive CallTree, metrics, and diagnostics evidence.

## Closure

Shared immutable artifacts, in-flight resolution, cache refresh/invalidation,
unload/shutdown, cancellation races, diagnostics, and metrics have deterministic
ownership and failure-isolation behavior. Phase 58.7 consumes the resulting
read-only operational contract.

## Non-Goals

Changing access policy, repository admission semantics, or providing Help/Admin
presentation beyond the stable read-only contract.
