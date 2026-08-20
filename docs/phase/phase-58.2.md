# Phase 58.2 - Subcomponent Packaging and Publication Completeness

status=planned
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.1](phase-58.1.md)
successor=[Phase 58.3](phase-58.3.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.2 Checklist](phase-58.2-checklist.md)
consumes_handoff=RSC-02 registry schema, deterministic codec, and representative identities

## Goal

Package representative Documentation, Source, and external-platform
Subcomponent CAR fixtures deterministically and expose only complete,
integrity-validated logical releases.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected cross-repository implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-02 schema, codec, and fixture identities
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4–6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Define deterministic Subcomponent archive layout and primary/subordinate
  metadata for the frozen RSC-02 schema.
- Bind digests and signatures after packaging; validate required release
  membership before upload and visibility.
- Prove representative Documentation, Source, and executable-child fixture
  parity across the admitted packaging owners.

## Closure

Archive layout, artifact metadata, integrity binding, and atomic release
admission are deterministic; incomplete declared profiles are not visible.
Phase 58.3 consumes the accepted packaged fixture and admission evidence.

## Non-Goals

Resolver precedence, child activation, runtime operation modes, and consumer
projection behavior.
