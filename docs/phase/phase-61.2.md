# Phase 61.2 - InformationSpace Entity Persistence and OCC

status=in-progress
planned_at=2026-08-30
split_from=[Phase 61](phase-61.md)
depends_on=[Phase 61.1](phase-61.1.md)
successor=[Phase 61.3](phase-61.3.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61.2 Checklist](phase-61.2-checklist.md)
consumes_handoff=accepted IC-03 generated runtime identity and bounded adapter policy

## Goal

Complete IC-04: bind InformationSpace to the standard component-owned Entity
repository, UnitOfWork, managed revision, and atomic optimistic concurrency
path without a parallel persistence kernel.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: component-scoped Entity repository ownership,
  revision/OCC transition, and no-partial-mutation invariants
- frozen_profile_transition_handoff: accepted Phase 61.1 generated runtime
  identity and adapter-removal policy
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7--8h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: isolates persistence/OCC safety before curation behavior
  is remapped
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 61

## Scope

- Define the component-scoped Information Entity collection and deterministic
  descriptor registration.
- Replace private snapshot authority with repository-backed operations while
  preserving the InformationSpace API boundary.
- Apply managed revision and atomic stale-write rejection with in-memory,
  SQLite, provider, replay, restart, rollback, and concurrent-update evidence.

## Closure

InformationSpace uses the standard Entity repository/UnitOfWork boundary; each
effective mutation has managed revision and stale writes fail without partial
state. Phase 61.3 consumes this persistence/OCC handoff.

## Non-Goals

New curation behavior, lifecycle semantics, transport projections, downstream
migration, duplicate removal, and canonical documentation closure.

## Current Status

In progress. IC-04A has bound InformationSpace to a deterministic,
Component-owned EntityStore collection with StoreOnly memory policy and embedded
managed revision. Its focused Information and Component integration suite
passed 56 tests in 12 suites. IC-04B has added the explicit strict-ingress
update route: the adapter-observed revision remains execution metadata, while
the standard EntityStore path applies `WriteIfChanged + ObservedRequired` and
rejects a stale retry atomically without changing the stored or cached root.
Its focused persistence specification passed 5 tests; IC-04C remains.
