# Phase 59.2.2 - Portable Model and Diagram Resource Contract

status=planned
split_from=[Phase 59.2](phase-59.2.md)
depends_on=[Phase 59.2.1](phase-59.2.1.md)
successor=[Phase 59.2.3](phase-59.2.3.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59.2.2 Checklist](phase-59.2.2-checklist.md)
consumes_handoff=accepted DOC-02B framework-context and projection-evidence contract

## Goal

Complete the portable Component model-resource and deterministic diagram part
of DOC-02 over the accepted manifest and framework-context contracts.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted DOC-02B framework-context contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3--4h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved nested split from Phase 59.2

## Scope

- Define portable Entity, Powertype, StateMachine, Value, Datatype, and
  Relationship resources with typed identity, role, media type, digest, and
  safe provenance.
- Define deterministic class-diagram and state-diagram resource identities and
  generated projection provenance without runtime reflection or rendering.
- Extend the deterministic codec, validation, and executable specifications for
  model/diagram identity, compatibility, duplicate, and hostile-path cases.

## Closure

The accepted portable model and diagram-resource contract is the sole model
resource handoff to Phase 59.2.3.

## Non-Goals

Framework publication context, Directive/Skill metadata, the read-only consumer
interface, generation/packaging, Help, CBD Support, BoK, and Phase 60 behavior
are out of scope.
