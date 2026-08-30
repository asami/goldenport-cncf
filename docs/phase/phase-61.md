# Phase 61 - Information CML Runtime Canonicalization

status=closed
planned_at=2026-07-26
depends_on=[Phase 60.8](phase-60.8.md)
successor=[Phase 61.1](phase-61.1.md)
series_successor=[Phase 62](phase-62.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61 Checklist](phase-61-checklist.md)
ic01_handoff=[Phase 61 IC-01A Information CML Runtime Inventory and Failing-First Contract](../notes/phase-61-ic01-information-cml-runtime-inventory-and-failing-first-contract.md)

## Split Record

On 2026-08-30, user decision `D-P61-SPLIT-001` split the remaining Phase 61
work into sequential, independently closable units. IC-01 remains completed
history in this Phase; this retained Phase owns IC-02. Phases 61.1 through
61.6 own IC-03 through IC-08 in that order.

The pre-split planning gate estimated IC-02 through IC-08 at 48--59 hours.
The split adds planning, handoff, validation, review, and release overhead,
but keeps each unit in the preferred 4--8 hour band. The split does not begin
any child Phase.

Profile-transition handoff: this Phase turns the IC-01 inventory into an
accepted canonical CML and generator contract. Each later Phase consumes the
accepted predecessor handoff and must not redefine the generated model,
compatibility, persistence, lifecycle, projection, migration, or closure
contract already frozen by its predecessor.

Pre-split gate evidence (2026-08-30): `SPLIT_REQUIRED`, time-bound,
48--59 hours, critical path IC-02 -> IC-03 -> IC-04 -> IC-05 -> IC-06 ->
IC-07 -> IC-08. This is historical gate evidence; the current structural gate
follows.

## Purpose

Complete IC-02: make `src/main/cozy/information.cml` and its generator output
one usable revision-aware Entity/value/powertype/lifecycle contract for the
later runtime migration.

## Dependency

Phase 61 begins after the Phase 60 series closes in Phase 60.8.

Technical foundations are Phase 26, Phase 27, Phase 49, and Phase 50.

## Scope

- Complete the canonical Information CML and required generator behavior.
- Verify generated Entity, value, powertype, input, schema, codec, and
  lifecycle output contracts, including managed revision boundaries.
- Produce the accepted IC-02 contract and executable evidence consumed by
  Phase 61.1.

## Non-Goals

- Moving CNCF Information into `simplemodeling-model`.
- Making KnowledgeSpace editable.
- Merging Information, Entity, RDF, external, Tag, or Knowledge identities.
- Replacing Information capabilities with generic Entity permissions.
- Exposing provider payloads or managed revision as application input.
- Retaining a parallel Information persistence kernel.
- Adopting generated types in runtime, persistence/OCC, curation, projections,
  downstream migration, duplicate removal, or canonical closure; those belong
  to Phases 61.1 through 61.6.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| IC-01 | Inventory and failing-first acceptance | The hand-written/generated split, downstream use, compatibility surface, and exact executable acceptance identities are fixed. | done |
| IC-02 | Canonical CML and generator contract | Information CML completely describes the canonical Entity/value/lifecycle contract and generates usable revision-aware outputs and transition evidence. | done |

## Acceptance

- Generated outputs and inputs obey the Phase 50 managed-revision contract.
- CML lifecycle output provides executable transition evidence rather than an
  empty record scaffold.
- The accepted IC-02 handoff lets Phase 61.1 adopt generated runtime types
  without redefining CML or generator semantics.

## Closure

Phase 61 is closed after IC-02 and its focused CML/generator evidence were
accepted. The mandatory independent full review found `CPB-61-001`, a
contradictory Phase 62 entry rule; the Phase 62 planning correction and its
focused re-review resolved that blocker without changing the IC-02 source or
test delta. The final full-suite gate and the distinct release commit bind this
closure under `phase61-clb-ic02-20260831`. Phase 61.1 is the next planned
consumer of the accepted handoff; it is not started by this closure.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: canonical generated lifecycle, package, and
  managed-revision contract across CNCF and generator ownership
- frozen_profile_transition_handoff: IC-01 inventory and failing-first
  acceptance registry
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7--8h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: split overhead is accepted to isolate later migration and
  downstream compatibility work
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 61

## Planning References

- [Phase 26 - Knowledge Import and InformationSpace](phase-26.md)
- [Phase 27 Checklist](phase-27-checklist.md)
- [Phase 49 - Entity Conflict and Conditional Transition](phase-49.md)
- [Phase 50 - SimpleEntity Revision and OCC Simplification](phase-50.md)
- [Information CML](../../src/main/cozy/information.cml)
- [InformationSpace working model](../journal/2026/05/knowledge-import-information-space-working-model.md)

## Current Status

IC-01 and IC-02 are complete. Phase 61.1 is the next planned consumer of the
accepted IC-02 handoff and requires its own explicit Phase invocation.
