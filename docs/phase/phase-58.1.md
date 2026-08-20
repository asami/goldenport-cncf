# Phase 58.1 - Component Identity and Composition Codec

status=done
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58](phase-58.md)
successor=[Phase 58.2](phase-58.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.1 Checklist](phase-58.1-checklist.md)
consumes_handoff=RSC-01 reviewed architecture proposal, owner inventory, and acceptance matrix

## Goal

Implement one deterministic parent/Subcomponent Component identity and
composition-registry codec without duplicating Component identity or granting
activation, capability, operation, or disclosure authority.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-01 proposal, ownership map, and acceptance matrix
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4–6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Define the root composition schema, identity fields, role/technology split,
  logical-versus-physical identity, and forward-compatible codec behavior.
- Reject duplicate, cyclic, stale, malformed, unsafe, or incompatible
  membership and integrity inputs.
- Add round-trip, property, hostile-input, and compatibility specifications.

## Closure

One codec represents exact release membership without duplicating Component
identity. Its accepted schema and fixture handoff are consumed by Phase 58.2.

## Release Evidence

- RSC-02 implementation was accepted in Step commit
  `bcbdd159a4b80e7783939463a550ba90ebbbf6c7`.
- The mandatory Phase review found one bounded child-identity blocker;
  Closure Fix Batch `F-58.1-RSC02-003` corrected it, and the focused closure
  re-review sealed `PASS`.
- The release commit is created only after the serialized `sbt --batch test`
  gate passes for this frozen tree.

## Non-Goals

Packaging, repository visibility, resolver lookup, operation-mode policy,
security enforcement beyond codec validation, lifecycle, and consumer APIs.
