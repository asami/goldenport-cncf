# Phase 58.5 - Resource Authorization, Disclosure, and Integrity

status=planned
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.4](phase-58.4.md)
successor=[Phase 58.6](phase-58.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.5 Checklist](phase-58.5-checklist.md)
consumes_handoff=RSC-05 runtime composition-policy matrix

## Goal

Enforce authorized, integrity-checked, and non-leaking resource and child
access across every resolution form.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-05 runtime-policy matrix and resolved-resource outcomes
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Enforce role/resource authorization, parent/release/integrity validation,
  and safe archive/path handling before content access.
- Keep restricted source visible only as admitted state and prevent disclosure
  through manifests, diagnostics, metrics, or CallTree.
- Cover hostile archives, corrupt cache, unauthorized source, and manifest
  authority-denial regressions.

## Closure

Restricted source is represented without disclosure; integrity, authorization,
and path safety are enforced before content exposure; diagnostics cannot leak
credentials, content, host paths, or repository secrets.

## Non-Goals

New operation authority, manifest-granted authorization, cache lifecycle, or
consumer presentation.
