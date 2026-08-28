# Phase 60.4 - Component Admin Runtime and Datastore Visibility

status=closed
planned_at=2026-08-28
started_at=2026-08-28
closed_at=2026-08-28
split_from=[Phase 60](phase-60.md)
depends_on=[Phase 60.3](phase-60.3.md)
successor=[Phase 60.5](phase-60.5.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 60.4 Checklist](phase-60.4-checklist.md)
consumes_handoff=ADM-04 read-only contract and model projection
absorbs_development_candidate=DEV-004 direct Admin canonical Entity-ID equality boundary

## Goal

Project authoritative runtime and datastore state with exact identity,
provenance, lifecycle, and instance isolation. Every admitted Admin Entity-ID
input must resolve only through its declared backing `EntityCollection` and
exact collection equality.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted ADM-04 projection and DEV-004 canonical-ID boundary
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 60

## Scope

- Project lifecycle, health, dependency, ClassLoader, datastore, schema,
  collection, Entity-ID, and collection-ID evidence from authoritative runtime
  contracts.
- Preserve configured, resolved, active, degraded, and failed distinctions.
- Reject scalar locators, foreign canonical IDs, entropy fallback, missing
  owners, and ambiguous owners deterministically.
- Add standalone and multi-user `ExecutionContext` acceptance.

## Closure

Runtime and datastore visibility preserves collection ownership and instance
isolation. Phase 60.5 consumes this state without creating a second runtime
resolver or identity interpretation.

## Non-Goals

Changing Entity identity semantics, resource resolution, model generation,
documentation navigation, management operations, or transport surfaces.

## Current Status

Phase 60.4 is closed under `phase60.4-clb-adm05-20260828`. ADM-05 delivers
the reviewed package-private, value-only
`ComponentAdminRuntimeDatastoreProjection`, including exact runtime identity
binding, lifecycle and datastore evidence, Entity-ID collection equality, and
instance-isolation rejection. The mandatory full Phase review's three Current
Phase Blockers were closed by repair cycle 1 and its focused closure re-review;
the final full suite is bound to this distinct release closure. This closure
records no accepted Hygiene or Development Candidate follow-up and does not
start Phase 60.5.
