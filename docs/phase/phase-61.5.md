# Phase 61.5 - Information Downstream and Persisted-State Migration Acceptance

status=in-progress
planned_at=2026-08-30
split_from=[Phase 61](phase-61.md)
depends_on=[Phase 61.4](phase-61.4.md)
successor=[Phase 61.6](phase-61.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61.5 Checklist](phase-61.5-checklist.md)
consumes_handoff=accepted IC-06 projection, managed-input, and authorization contract

## Goal

Complete IC-07: migrate supported persisted Information and downstream Textus
flows to the canonical generated model without silent loss or ambiguity.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: deterministic persisted-state migration and
  source/binary responsibility boundaries across CNCF and Textus consumers
- frozen_profile_transition_handoff: accepted Phase 61.4 public projection,
  managed-input, revision, and authorization contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7--8h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: isolates data-loss and downstream compatibility risk from
  canonical duplicate removal
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 61

## Scope

- Define supported legacy persisted shapes and deterministic migration or
  explicit incompatibility diagnostics.
- Preserve identity, lifecycle, raw/working data, curation values, audit, and
  revision provenance.
- Validate Textus Knowledge Editor, Textus SIE, representative domain profiles,
  Tag/Knowledge flows, and packaged/development compatibility.

## Closure

Supported downstream and persisted Information flows use the canonical model
with explicit migration or rejection. Phase 61.6 consumes the accepted
migration evidence to remove temporary duplicates and adapters.

## Non-Goals

New downstream features, silent best-effort migration, duplicate removal, or
canonical design/specification promotion.

## Current Status

In progress. IC-07A persisted Information admission and migration has completed
its dedicated design/compatibility and implementation review route; remaining
IC-07 downstream acceptance work remains open.

## Decision Resolution

- `decision_id=P61.5-IC07A-REVIEW-001`
- `resolved_at=2026-09-01 JST`
- `answer_source=direct developer instruction in this Phase task`
- `affected_phase=61.5`, `affected_slice=IC-07A`,
  `phase_base_commit=eb8e59a55f51a595bd8bab74984c05c0fa07d078`
- `selected_option=phase-local protected persisted-migration review route`
- `decision=Keep IC-07A in Phase 61.5. Add a dedicated persisted-data
  migration route: design/compatibility review, implementation review, and a
  focused re-review when a review finding is fixed, before the Step commit.`
- `authorized_next_state=PARENT_CAPABILITY_CHECK`
- `consumed=true`

## IC-07A Protected Persisted-Migration Review Route

IC-07A changes persisted Information admission, migration, and rejection
semantics. It is therefore not eligible for the ordinary lightweight Step
review by itself. The following Phase-local route is the required acceptance
path for this slice; it does not replace the one comprehensive Phase review at
Phase closure.

1. **PMR-D — design and compatibility review.** Freeze a migration dossier
   before review: supported and rejected source shapes, canonical physical
   representation, alias/default policy, non-mutation/rollback guarantee,
   diagnostics, preservation matrix, and the corresponding executable
   scenarios. An independent read-only reviewer checks that the policy is
   deterministic, lossless for supported input, and explicit for unsupported
   input. A policy conflict returns to PLAN; this review does not accept code.
2. **PMR-I — implementation review.** After the dossier is accepted and the
   focused migration and accumulator evidence is current, an independent
   reviewer checks the owned implementation and specifications against that
   frozen policy, including the storage representation actually supplied by
   the entity store. A clean PMR-I is required before the IC-07A Step commit.
3. **PMR-R — focused re-review.** Every admitted PMR-I finding that changes
   bytes receives a fresh focused re-review with the finding-to-fix mapping,
   exact validation receipts, and the unchanged migration dossier. A policy,
   repository, or scope change invalidates this route and returns to PLAN.

The parent freezes reviewer profile, evidence identity, owned paths, and the
acceptance ledger during re-planning. Reviewers do not silently broaden legacy
support, introduce a best-effort repair, or substitute the final Phase review.

## IC-07A Review Evidence

- `P61.5-IC07A-PMR-D-001`: independent design/compatibility review found five
  bounded evidence and admission gaps; all were repaired without adding a
  persisted shape.
- `P61.5-IC07A-PMR-D-RR-001`: typed focused re-review accepted bundle
  `1683c161f584e251fa5729fbfee95c7838847c0917a762cb9a333063327e1343`
  and returned PASS with no Current Boundary Blocker.
- `P61.5-IC07A-PMR-I-001`: independent implementation review returned PASS;
  PMR-R is not required because PMR-I identified no byte-changing correction.
- Focused validation: `InformationPersistenceMigrationSpec`
  `15623-20260901T010636Z` (7/0), and
  `InformationSpaceEntityPersistenceSpec` `16405-20260901T010811Z` (16/0),
  both with the serialized SBT lock released.
