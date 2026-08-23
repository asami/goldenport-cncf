# Phase 59.2 - Component Knowledge and Model Manifest Contract

status=planned
split_from=[Phase 59](phase-59.md)
depends_on=[Phase 59.1](phase-59.1.md)
successor=[Phase 59.3](phase-59.3.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59.2 Checklist](phase-59.2-checklist.md)
consumes_handoff=reviewed DOC-01 inventory, ownership map, and failing-first acceptance registry

## Goal

Implement DOC-02: the versioned Component knowledge/model manifest contract,
codec, validation, safety, provenance, and stable read-only consumer boundary
over the Phase 58 resource contract.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: reviewed DOC-01 inventory, ownership map, and acceptance registry
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 59

## Scope

- Define manifest/model schema, canonical resource paths, identity, role,
  language, media, digest, provenance, disclosure, and forward compatibility.
- Bind entries to Phase 58 logical resource identity and physical provenance
  without a second resolver.
- Implement deterministic codec, validation, property, hostile-path, and
  consumer-contract specifications.

## Closure

An exact, safe, versioned manifest and stable read-only consumer interface are
accepted with executable evidence. Phase 59.3 consumes the manifest contract.

## Non-Goals

Authoring/package generation, development context, Help routes, CBD Support,
BoK, representative profile validation, final security, or Phase 60 behavior.
