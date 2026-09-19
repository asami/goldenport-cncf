# Phase 63.1 - StateMachine Generation and Atomic CNCF Execution

status=complete
planned_at=2026-09-17
execution_started_at=2026-09-18
split_from=[Phase 63](phase-63.md)
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-63.2
aggregate_validation_sequence=["PHASE-63","PHASE-63.1","PHASE-63.2"]
depends_on=[Phase 63](phase-63.md)
successor=[Phase 63.2](phase-63.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 63.1 Checklist](phase-63.1-checklist.md)

## Purpose

Carry the accepted Phase 63 model through SimpleModeler generation and one
atomic CNCF candidate-state/local-effect/UnitOfWork execution path. It stops
before the workflow-facing committed-transition envelope and its projections.

## Split Provenance

Split from Phase 63 on 2026-09-17. It consumes the Phase 63 release handoff
and produces generated transition definitions plus atomic execution semantics
for Phase 63.2.

## Scope and Closure

Owns `SMR-04` and `SMR-05`: generated canonical definitions/ABI and CNCF
candidate-state execution, local effects, persistence, rollback, and bypass
rejection. It closes when those contracts are executable through the generated
provider and real UnitOfWork boundary, without claiming post-commit envelope
publication or cross-repository promotion.

`SMR-04` and `SMR-05` completed their accepted Step commits and the independent
full Phase review. The release record binds the verified legacy Phase 63
aggregate predecessor; Phase 63.2 remains the separately planned owner of
post-commit delivery and aggregate full-suite validation.

### Forced release baseline

The normal release adapter currently contradicts this Phase's sealed
`final-only` validation contract by requiring a duplicate full suite from a
validation-only Phase-base producer. By explicit user direction, this Phase is
closed as a local `forced / exceptions-recorded` baseline. The release commit's
`CNCF-Force-Release-*` trailers are the authoritative record: focused evidence
and the independent full review remain intact, while no repository full suite
is claimed. Phase 63.2 remains responsible for the one aggregate full suite;
it must explicitly decide how to accept this forced predecessor.

### Forced audit-projection repair

The original forced baseline commit did not persist its required
`CNCF-Force-Release-*` trailer projection despite the verified force record.
This follow-up records no implementation, specification, or validation result:
it creates a new forced audit baseline with the same exception ID so the later
aggregate owner can verify the predecessor without rewriting history. The
forced disposition and `exceptions-recorded` assurance remain unchanged.

## Work Stack and Validation Boundary

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SMR-04 | SimpleModeler generation and ABI | Generated code carries typed transition definitions, binding references, metadata, and explicit unsupported-semantics rejection. | done |
| SMR-05 | Atomic CNCF transition execution | CNCF executes exactly one admitted transition through candidate state, local effects, persistence, and rollback in one UnitOfWork. | done |

Focused generated-source/ABI and atomicity/rollback specifications are required.
The repository full suite remains owned by Phase 63.2. Phase 63.1 does not
claim a full-suite run.

### Scoped local-artifact compatibility diagnosis

The Phase 63.1 focused Cozy consumer proof consumes locally published SmartDox
and Goldenport development artifacts. The focused fixture was run on
2026-09-18 and failed before its generated-provider assertions while resolving
`goldenport-scala-lib_2.12:2.3.32-SNAPSHOT`: its Ivy descriptor lacks the
`master` configuration required by the consumer's transitive resolution.

A SmartDox-only metadata experiment was deliberately reverted after a local
republish and repeat fixture produced the identical Goldenport failure. That
proves SmartDox is not the repair owner; no SmartDox source, behavior, API,
version, or released coordinate remains changed.

The next repair, if separately authorized, belongs only to the Goldenport
artifact producer: publish a local descriptor that declares the required Ivy
configuration, then rerun this same focused Cozy fixture. It is dependency
artifact compatibility work for SMR-04, not product development. It excludes
remote publication, version changes, cross-repository promotion, and Phase
63.2 aggregate validation.

## Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable completed Phase; Stage 04/05 are the settled execution portion of the 16--21h pre-split estimate
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes Phase 63 canonical model; produces generated ABI and atomic UnitOfWork execution semantics for Phase 63.2
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5--6h; within the 8h ceiling
- incoming_semantic_handoffs: [{from_child: Phase 63, to_child: Phase 63.1, kind: release, input: accepted canonical CML transition and predicate contract, action_or_decision: close Phase 63 with focused evidence and release record, output: immutable generated-ABI admission input, owner: Phase 63, invalidation_reason: changing the canonical contract invalidates generated ABI and runtime planning}]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: Stage 04 and Stage 05 are jointly required to create one executable generated-to-UnitOfWork handoff; separating either leaves no independently closable runtime proof
- adjacent_merge_structural_rejection_evidence: merging with Phase 63 would re-open the canonical contract while generation and execution consume it; merging with Phase 63.2 would combine atomic mutation with post-commit delivery/observability and exceed the 8h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: one release handoff prevents downstream Event/Workflow-facing work from altering mutation atomicity
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 63

## Non-Goals

- Defining the canonical CML transition and predicate model (Phase 63).
- `CommittedTransition` identity, post-commit Event/Job handoff, observability
  projections, compatibility completion, or cross-repository promotion
  (Phase 63.2).
- Workflow orchestration, DbC, external I/O inside transition actions, or a
  second transition selector.

## Planning References

- [Phase 63](phase-63.md)
- [Phase 63.1 Checklist](phase-63.1-checklist.md)
- [Phase 63.2](phase-63.2.md)
- [Provisional Specification](../notes/cml-statemachine-runtime-completion-provisional-specification.md)
