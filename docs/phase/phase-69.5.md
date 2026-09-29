# Phase 69.5 - CompositeQuery v2

status=closed
planned_at=2026-09-09
closed_at=2026-09-29
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69.4](phase-69.4.md)
successor=[Phase 69.6](phase-69.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.5 Checklist](phase-69.5-checklist.md)
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-69.7
aggregate_validation_sequence=["PHASE-69.4","PHASE-69.5","PHASE-69.6","PHASE-69.7"]

## Purpose

Deliver bounded, deterministic, secure, cancellable, diagnosable parallel and
cross-subsystem CompositeQuery composition.

## Scope and Closure

Owns `JM69-07`: v1 inventory, typed branch/dependency semantics,
cross-subsystem protocol, bounded parallel execution, ordering, Job policy,
diagnostics, and executable specifications. Closes with a CompositeQuery v2
handoff for the user/operator experience.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69.4. Successor: Phase
69.6. Consumes the Phase 69.4 lightweight direct JobDefinition lifecycle
contract and produces the v2 composition contract.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-07 | CompositeQuery v2 | DONE |

Focused ordering, bounds, cancellation, failure, authorization, and
cross-subsystem specifications are required. The repository full suite is
deliberately deferred to the declared aggregate final owner, Phase 69.7; this
Phase still requires focused validation, independent review, and its own
release commit.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes the Phase 69.4 lightweight direct JobDefinition lifecycle contract; produces CompositeQuery v2 semantics for Phase 69.6
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--7h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: keeps query-composition execution independent of UI projection ownership
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Non-Goals

- Presentation composition in Domain logic or persistence of ordinary Query payloads by default.

## Closure Evidence

JM69-07 / JM69-07A is accepted and committed as `3736ce2c153cefbaf1f825b881bd761e4539527b`.
The [contract](../spec/composite-query-v2-contract.md) records CQ2-R1--R12 and
executable examples E01--E28; the [design](../design/composite-query-v2.md)
records their implementation boundaries. Focused acceptance passed 31 v2 and
41 affected-consumer specifications. The independent Step review and focused
repair re-review accepted the Step after one bounded repair.

The sole comprehensive Phase review, `P695-PHASE-FULL-01`, found one remaining
test-helper naming issue, `CB-P695-FULL-001`. Its exact local identifier
renames were accepted as M0 by `P695-PHASE-MECHANICAL-CLOSURE-01`: inverse
renaming restores the entire original file, and fresh `Test / compile` passed
with zero warnings. No second comprehensive review or independent M0
re-review was performed. The original findings remain part of the audit.
There are no unresolved Current Phase Blockers, Hygiene entries, or Development
Candidates. The [closure journal](../journal/2026/09/2026-09-29-phase-69.5-composite-query-v2.md)
records the validation and review identities.

Closure is under `Phase-Closure-Binding: PHASE-69.5` in the distinct final
Phase release commit. Assurance is focused acceptance with
`repository_full_suite=deferred-not-run`; aggregate acceptance remains pending.

## Frozen Successor and Aggregate Handoff

Phase 69.6 receives CQ2-R1--R12 and E01--E28 unchanged: caller-owned bounded
Job scheduling; target-owned query-only authorization; isolated request
context and per-branch resources; declaration-ordered outcomes;
timeout/cancellation/fallback rules; and redacted ephemeral/persistent Job
diagnostics. Presentation and operator views must consume this composition
contract without moving presentation composition into Domain logic or
persisting ordinary Query payloads. Phase 69.6 remains planned and not started.

Phase 69.7 remains the reciprocal aggregate final owner for
`[PHASE-69.4, PHASE-69.5, PHASE-69.6, PHASE-69.7]`. Its existing authority
accepts repository-full CNCF and required downstream validation after verifying
every predecessor release and ancestry. The helper-generated
`CNCF-Aggregate-Validation` trailer carries this Phase's immutable deferred
handoff. This closure neither starts Phase 69.6 nor claims aggregate validation.

## Planning References

- [Phase 69.4](phase-69.4.md)
- [Phase 69.5 Checklist](phase-69.5-checklist.md)
- [Phase 69.6](phase-69.6.md)
