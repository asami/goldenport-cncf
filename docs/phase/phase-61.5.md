# Phase 61.5 - Information Persisted-State Migration Admission

status=closed
planned_at=2026-08-30
split_from=[Phase 61](phase-61.md)
depends_on=[Phase 61.4](phase-61.4.md)
successor=[Phase 61.5.1](phase-61.5.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 61.5 Checklist](phase-61.5-checklist.md)
consumes_handoff=accepted IC-06 projection, managed-input, and authorization contract

## Goal

Complete IC-07A: admit supported persisted Information shapes to the canonical
generated model with deterministic migration or explicit incompatibility.

Phase Plan Gate: PROCEED
- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: accepted IC-07A PMR-D/PMR-I migration
  dossier, focused migration evidence, and the Phase 61.4 public projection,
  managed-input, revision, and authorization contract
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4--5h
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the added Phase 61.5.1 handoff keeps this Phase's
  persisted-state release gate independent from unfinished downstream
  generator/runtime/CAR compatibility work
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved Phase 61 split; current unit narrowed by
  D-P61.5-IC07B-SPLIT-001

## Scope

- Preserve the accepted supported/rejected persisted-shape boundary,
  deterministic migration/incompatibility diagnostics, identity, lifecycle,
  raw/working data, curation values, audit, and revision provenance.
- Close IC-07A with its required Phase review, final validation, and release
  evidence without absorbing downstream consumer acceptance.

## Closure

Supported persisted Information shapes use the canonical model with explicit
migration or rejection and accepted Phase closure evidence. Phase 61.5.1
consumes that handoff for downstream runtime-coordinate and consumer
acceptance; Phase 61.6 then consumes the complete IC-07 evidence.

## Non-Goals

Downstream runtime-coordinate admission, generated dependency identity,
packaged CAR/catalog validation, representative consumer acceptance, duplicate
removal, or canonical design/specification promotion.

## Current Status

Closed under closure binding `phase61.5-clb-ic07a-20260901`. IC-07A persisted
Information admission and migration completed its dedicated
design/compatibility and implementation review route, the mandatory Phase full
review, three monotonically narrowing closure-repair cycles, and the final
focused re-review. The approved nested split moves every unfinished IC-07B
downstream acceptance item to planned/not-started Phase 61.5.1; this Phase
accepts no IC-07B implementation or validation.

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

## Phase Closure Evidence

- Closure binding: `phase61.5-clb-ic07a-20260901`.
- Phase base: `eb8e59a55f51a595bd8bab74984c05c0fa07d078`.
- Accepted Step boundary: `0b808499f1e8cb1a230e88a15d30427ade3ead7c`.
- Mandatory Phase full review: completed exactly once. Its blocker lineage
  `P61.5-B01` was repaired through the bounded convergence route; no second
  full review was run.
- Repair cycle 2 sealed `CB-P61.5-RR-001`: revision validation precedes domain
  decode while the established physical-store `fromStoreRecord` codec dispatch
  remains selected.
- Repair cycle 3 sealed `CB-P61.5-RR-002`: `admitStoreRecord` and
  `decodeAdmittedStoreRecord` are package-private to `org.goldenport.cncf`; no
  new public persistence API remains.
- Final focused re-review: PASS, manifest
  `090118be98e3b06dbe92016da5234337addd203591542dccaaeaaeb928fe6aa1`,
  with zero Current Phase Blocker.
- Focused repair validation: `P61.5-B01-VAL-006`, invocation
  `49698-20260901T064628Z`; 5 suites / 70 tests passed, compilation succeeded,
  and the shared SBT lock was released.
- Final repository-full validation: `P61.5-RELEASE-VAL-001`, invocation
  `59047-20260901T070547Z`; 476 suites / 3,528 tests passed, 0 failed,
  compilation succeeded, and the shared SBT lock was released. This is the
  only repository-full test run for the Phase 61.5 release gate.
- Phase Hygiene Ledger: `HYG-P61.5-RR3-001` and `HYG-P61.5-RR3-002` are
  persisted in
  `docs/journal/2026/09/2026-09-01-phase-61.5-hygiene-follow-up.md` as
  nonblocking maintenance.
- Development Candidate Ledger: no item accepted; its canonical journal remains
  absent.
- Concurrent successor planning: Phase 61.5.1 and Phase 61.6 remain
  planned/not-started and are preserved outside this closure commit. Shared
  README/strategy synchronization is deferred with those exact planning bytes;
  this does not start either successor.

## Decision Resolution — IC-07B Split

- `decision_id=D-P61.5-IC07B-SPLIT-001`
- `resolved_at=2026-09-01 JST`
- `answer_source=direct developer instruction in this Phase task: "phaseを分割して"`
- `selected_option=SPLIT_PHASE`
- `affected_phase=61.5`, `affected_slice=IC-07B`,
  `phase_base_commit=0b808499f1e8cb1a230e88a15d30427ade3ead7c`
- `affected_worktree=textus-semantic-integration-engine@c252eeb7e02b31f6d86ea32f0542e09b6dad4bbe`,
  `owned_delta_sha256=3dc27399f23fa69df057a6ab2dceda695c16dc1c7ee83f81f925795b653c81e4`,
  `blocking_receipt=P61.5-IC07B-VAL-008/47287-20260901T023104Z`
- `decision=Create planned Phase 61.5.1 for all unfinished IC-07B downstream
  runtime-coordinate, generated dependency-identity, packaged-CAR/catalog,
  and representative consumer-acceptance work. It owns the uncommitted Cozy,
  Textus Knowledge Editor, and Textus SIE coordinate changes and the stale
  SIE Scraper dependency assertion. Phase 61.5 retains only the accepted
  IC-07A persisted-state migration admission.`
- `authorized_next_state=PARENT_CAPABILITY_CHECK`
- `consumed=true`

This decision authorizes planning and documentation of Phase 61.5.1 only. It
does not authorize implementation, validation, review, or commit work in that
child Phase; that requires an explicit `$cncf-goal-phase 61.5.1` invocation.

## Split Record

On 2026-09-01, the explicit `$cncf-split-phase Phase 61.5` invocation applied
approved decision `D-P61.5-IC07B-SPLIT-001` as the nested sequence
`61.5 -> 61.5.1 -> 61.6`.

- **Phase 61.5** keeps completed IC-07A history and its persisted-state
  migration release closure (4--5h, Terra high).
- **Phase 61.5.1** owns all unfinished IC-07B downstream runtime-coordinate,
  generated dependency-identity, packaged CAR/catalog, and representative
  consumer-acceptance work (6--7h, Terra high).
- **Phase 61.6** remains the distinct canonical-closure Phase (5--6h, Terra
  high), now consuming Phase 61.5.1's complete IC-07 handoff.

The split reason is release-gate isolation: merging the accepted persisted
migration closure with the unresolved downstream compatibility repair would
mix two independently testable outcomes and exceed their safe closure boundary.
No child is below the 4--8h preferred packing band. The split adds one Phase,
handoff, validation, review, and commit boundary; it avoids repeating the
persisted-migration review while allowing the settled downstream work to use
the lower-cost execution profile. There is no remaining expensive reasoning
kernel. The Phase 61.5.1 profile-transition handoff is the accepted IC-07A
migration dossier, focused evidence, and this Phase's release closure.

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

## Codec-Dispatch Repair Decision

- `decision_id=D-P61.5-RR-001`
- `finding_id=CB-P61.5-RR-001`
- `resolved_at=2026-09-01 JST`
- `answer_source=direct developer replan in the Phase 61.5 task`
- `selected_route=repair-cycle-2 codec-dispatch correction`
- `decision=After admission, revision-bound EntityStore reads dispatch through
  the established `fromStoreRecord` codec override without re-admission.
  Information public reads admit once and then use the generated physical-store
  decoder; their admitted decode uses that generated decoder directly.`
- `rule_id=R1`
- `rule=Revision validation receives the admitted canonical record before
  domain decoding, and codec-specific physical-store decoding remains selected
  after that validation.`
- `required_evidence=Information migration once-only reads; one revision-bound
  custom physical-store codec regression; focused validation; one focused
  re-review.`
- `focused_validation_receipt=P61.5-B01-VAL-004/22517-20260901T053932Z:
  5 suites, 70 succeeded, 0 failed, exit 0, serialized SBT lock released.`
- `non_goals=No new persisted shape, public API, generated/reflection contract,
  repository, or Phase 61.5.1 work.`

## Codec-Dispatch Repair Decision — API Visibility

- `decision_id=D-P61.5-RR-002`
- `finding_id=CB-P61.5-RR-002`
- `resolved_at=2026-09-01 JST`
- `answer_source=direct developer instruction in the Phase 61.5 task: "admission hook を private[cncf] に限定して進めて"`
- `selected_route=repair-cycle-3 internal-admission-hook correction`
- `decision=Restrict EntityPersistent.admitStoreRecord and the Information
  persistence override to private[cncf]. Retain the established public
  fromStoreRecord codec-dispatch API; do not alter its once-only admission or
  revision-bound decoding behavior.`
- `rule_id=R2`
- `rule=Admission is an internal EntityStore boundary. No public persistence
  extension point is introduced by this repair.`
- `required_evidence=Focused compile-and-regression validation and one focused
  re-review of CB-P61.5-RR-002.`
- `non_goals=No new persisted shape, public API, generated/reflection contract,
  repository, or Phase 61.5.1 work.`
- `authorized_next_state=PARENT_CAPABILITY_CHECK`
- `consumed=true`
