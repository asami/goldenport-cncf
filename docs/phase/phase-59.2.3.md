# Phase 59.2.3 - Public Metadata and Read-Only Consumer Contract

status=in-progress
split_from=[Phase 59.2](phase-59.2.md)
depends_on=[Phase 59.2.2](phase-59.2.2.md)
successor=[Phase 59.3](phase-59.3.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59.2.3 Checklist](phase-59.2.3-checklist.md)
consumes_handoff=accepted DOC-02C portable model and diagram-resource contract
protected_step_review_routing=D-P59.2-NESTED-SPLIT-NUMBERING-001 reserves this schema boundary for mandatory Phase full review; no lightweight Step review is prepared here

## Goal

Complete public Directive and Skill Catalog metadata plus the stable read-only
manifest consumer contract. These records are descriptive only and cannot grant
directive, installation, activation, execution, MCP, or disclosure authority.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted DOC-02C portable model and diagram-resource contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4--5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved nested split from Phase 59.2

## Scope

- Define public Directive projection identity, origin, version, authority,
  visibility, source digest, and redaction metadata without exposing restricted
  rule content or overriding the mounted directive.
- Define public Skill Catalog identity, owner, purpose, trigger, requirements,
  permissions, side effects, MCP requirements, installation reference,
  visibility, and digest metadata without installing, activating, or executing
  a Skill.
- Define the stable read-only manifest consumer contract later consumed by
  Phase 60, without a resolver, content read, route, or Admin behavior.
- Extend deterministic codec/validation and executable specifications for the
  public metadata and consumer boundary.

## Closure

The complete DOC-02 manifest contract is accepted with executable evidence and
is the sole handoff to Phase 59.3.

## Non-Goals

Publication generation, raw directive or Skill-bundle content, installation,
activation, execution, Help/direct-AI routes, CBD Support, BoK, and Phase 60
runtime behavior are out of scope.
