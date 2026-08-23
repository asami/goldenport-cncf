# Phase 59.3 - Documentation Authoring and Content Packaging Toolchain

status=planned
split_from=[Phase 59](phase-59.md)
depends_on=[Phase 59.2](phase-59.2.md)
successor=[Phase 59.4](phase-59.4.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59.3 Checklist](phase-59.3-checklist.md)
consumes_handoff=accepted DOC-02 manifest schema, codec, resource identities, and acceptance matrix

## Goal

Implement DOC-03 through the owning Cozy/sbt-cozy and SmartDox toolchains:
manuals, projections, model diagrams, Scaladoc, filtered release source,
managed-source provenance, and publication handoff through the already
accepted closed Phase 58 packaging/resource contract, without reopening or
modifying Phase 58.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted DOC-02 manifest schema, codec, resource identities, and acceptance matrix
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 59

## Scope

- Generate/validate manuals, HTML/PDF, Scaladoc, portable model metadata,
  Mermaid diagrams, structured publication identities, and catalog projections.
- Package filtered source and normalized generated sources with reproducibility
  provenance while excluding transient build and host-local state.
- Prove source-tree/packaged-CAR and public-projection identity/digest parity.

## Closure

The generated content inventory and provenance handoff is accepted by exact
toolchain evidence. Phase 59.4 consumes only those admitted Phase 58 resources.

## Non-Goals

Runtime context composition, Help/direct AI routes, CBD Support, BoK, profile
acceptance, final security/release closure, and Phase 60 behavior.
