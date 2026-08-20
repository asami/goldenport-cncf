# Phase 58.3 - Resource Resolution, Activation Boundary, and Provenance

status=done
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.2](phase-58.2.md)
successor=[Phase 58.4](phase-58.4.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.3 Checklist](phase-58.3-checklist.md)
consumes_handoff=RSC-03 complete-release admission and packaged fixture evidence

## Goal

Expose one resolved-resource and provenance model across embedded,
development-directory, expanded, local, cache, remote, and offline sources,
while keeping discovery, activation, and external deployment distinct.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-03 admitted release fixture and artifact evidence
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4–6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Resolve embedded, development, expanded, local, cache, remote, and offline
  evidence through one API with deterministic precedence.
- Preserve logical identity, physical origin, access, integrity, and
  resolution-step provenance.
- Keep Subcomponent discovery separate from activation and platform deployment.

## Closure

Every resolved resource retains logical identity and physical provenance;
availability and integrity outcomes remain distinct; no lookup activates a
child or deploys an external-platform artifact. Phase 58.4 consumes this API
and its deterministic fixture matrix.

## Release Evidence

RSC-04 was accepted in Step commit
`995e82fc4e65b6cb0437a617b607bc0dbb28dcb4` (`Implement RSC-04 component resource
resolution`). The full Phase review, performed by Terra xhigh, returned
`CPB-P58.3-001` (terminal same-source selection), `CPB-P58.3-002` (direct
integrity state validation), and `CPB-P58.3-003` (active ScalaCheck property
coverage). One Closure Fix Batch repaired all three, and focused re-review
returned `SEALED_PASS`. The accepted post-fix focused accumulator
`45210-20260820T201957Z` covered `ComponentSubcomponentCompositionCodecSpec`
and `ResolvedComponentResourcesSpec`: 48 succeeded, 0 failed, 2 suites.
The frozen-tree `sbt --batch test` gate remains pending and is run by the Phase
release Commit Manifest; this page is committed as DONE only if that gate and
the release commit succeed.

## Non-Goals

Operation-mode selection, production source policy, authorization enforcement,
cache lifecycle, and Help/Admin consumer implementation.
