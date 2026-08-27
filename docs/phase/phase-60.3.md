# Phase 60.3 - Component Admin Contract and Model Visibility

status=in-progress
planned_at=2026-08-28
started_at=2026-08-28
split_from=[Phase 60](phase-60.md)
depends_on=[Phase 60.2](phase-60.2.md)
successor=[Phase 60.4](phase-60.4.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 60.3 Checklist](phase-60.3-checklist.md)
consumes_handoff=ADM-03 authoritative configuration and resolved composition projection

## Goal

Expose Service, Operation, SPI, capability, dependency, schema, model types,
relationships, and Phase 59 diagrams from their authoritative contracts
without reconstruction, invocation, or management authority.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted ADM-03 projection and Phase 59 knowledge/model manifest contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 60

## Scope

- Project contract and model metadata with exact authority/provenance.
- Consume Phase 59 class/state diagrams and schema/model metadata.
- Prove that visibility grants neither operation invocation nor management.

## Closure

The accepted contract/model projection is read-only and is consumed by Phase
60.4 without changing identity, resolver, or model-generation ownership.

## Non-Goals

Model generation, resource resolution, runtime/datastore state, documentation
navigation, management, and surface acceptance.

## Current Status

Phase 60.3 is in progress under ADM-04. The admitted implementation target is
the package-private, value-only `ComponentAdminContractModelProjection` over
the Phase 59 `ComponentKnowledgeManifestConsumerContract`; Phase 60.4 has not
started.
