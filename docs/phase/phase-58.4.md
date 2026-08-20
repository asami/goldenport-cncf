# Phase 58.4 - Operation-Mode and Development Composition

status=in_progress
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.3](phase-58.3.md)
successor=[Phase 58.5](phase-58.5.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.4 Checklist](phase-58.4-checklist.md)
consumes_handoff=RSC-04 resolved-resource API, provenance model, and fixture matrix

## Goal

Define deterministic Develop, Test, Demo, and Production composition policies
without exposing `OperationMode` to Component-domain code, implicitly
activating a child, or deploying an external-platform artifact.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-04 resolver API and provenance state matrix
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Define Develop precedence and structured readiness failures.
- Keep Test deterministic, make Demo remote Documentation explicit, and retain
  primary-only Production without automatic source access.
- Prove development/packaged parity while keeping operation mode outside the
  Component domain API.

## Closure

Each mode has one runtime-owned, deterministic composition policy; production
remains primary-only capable and never automatically resolves, mounts, or
fetches source. Phase 58.5 consumes the confirmed policy matrix.

## Non-Goals

New resource identities, repository admission, child deployment, or consumer
UI/API implementation.
