# Phase 63 - CML StateMachine Contract and Normalization

status=in-progress
planned_at=2026-08-12
split_at=2026-09-17
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-63.2
aggregate_validation_sequence=["PHASE-63","PHASE-63.1","PHASE-63.2"]
successor=[Phase 63.1](phase-63.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 63 Checklist](phase-63-checklist.md)
execution_plan=[Phase 63 Execution Plan](phase-63-execution-plan.md)

## Purpose

Freeze the canonical typed StateMachine contract and normalize CML declarations
to it. This is the first, independently closable member of the StateMachine
runtime sequence; it does not deliver generated code or CNCF execution.

## Dependency and Closure

Phase 63 has no dependency on Phase 62. Its foundations are Phase 4
StateMachine integration, the canonical `org.goldenport.statemachine`
primitives, and existing CML transition declarations.

It closes only when CML normalization produces the one closed transition and
predicate model with stable identities, ordering, validation, compatibility
admission, and failing-first specifications. Phase 63.1 alone may consume that
accepted contract for generation and runtime execution.

## Approved Split

On 2026-09-17, `P63-DEC-PACKING-SPLIT-001` partitioned the previously
unstarted Phase 63 into Phase 63 (`SMR-01`--`SMR-03`), Phase 63.1
(`SMR-04`--`SMR-05`), and Phase 63.2 (`SMR-06`--`SMR-08`). The pre-split
entry gate estimated 16--21 hours and returned `SPLIT_REQUIRED` for
`time-bound`; it is historical gate evidence, not this Phase's current gate.
No completed work or history moved.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SMR-01 | Inventory and semantic freeze | Existing CML, core StateMachine, Aggregate, UnitOfWork, and observability facts plus failing-first identities are frozen. | DONE |
| SMR-02 | Canonical transition and predicate contract | One typed pure selection, guard, effect-plan, outcome, and ordering contract is fixed. | DONE |
| SMR-03 | Already-parsed CML StateMachine normalization | Non-Workflow CML StateMachines normalize deterministically to the closed contract with explicit compatibility admission. | DONE |

## Acceptance

- One CML declaration normalizes to one canonical transition definition with
  stable machine, state, event, guard, action, and transition identities.
- Priority/declaration order, guard non-match, guard failure, explicit
  initial/final state, and one-level composite/named shallow-history semantics
  (including direct-leaf path ownership) are preserved without raw-expression
  fallback.
- Predicate evaluation is typed, bounded, pure, deterministic, and receives
  one explicit trigger-context schema.
- Invalid or ambiguous declarations fail with deterministic diagnostics.
- The accepted handoff gives Phase 63.1 exact model and compatibility
  semantics; it does not claim generated ABI or runtime execution.

## Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable completed Phase; pre-split 16--21h estimate is partitioned by the existing eight-stage dependency chain
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: canonical typed predicate, transition identity, compatibility, and CML-normalization contract
- frozen_profile_transition_handoff: produces the accepted canonical contract for Phase 63.1
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5.5--6.5h; within the 8h ceiling
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: two added release handoffs prevent generation, UnitOfWork execution, and workflow-facing delivery from re-deciding the contract
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 63

## Non-Goals

- Generated transition definitions or ABI propagation (Phase 63.1).
- CNCF candidate-state execution, UnitOfWork commit/rollback, or persistence
  behavior (Phase 63.1).
- `CommittedTransition`, Event/Job handoff, observability projections, or
  cross-repository runtime acceptance (Phase 63.2).
- Workflow orchestration, executable DbC, timers, parallel/human tasks,
  compensation, connectors, arbitrary scripting, or external I/O.
- Cozy's independently closed Phase 63 is Multi-CML provenance
  (`MCML-63-01`--`MCML-63-02`); its closed Phase 63.1 successor owns
  `MCML-63-03`--`MCML-63-04`. Neither is this Phase's StateMachine work. The
  shared number is not a handoff, predecessor, successor, validation claim, or
  authority. This Phase neither reopens nor consumes that provenance closure.
- Cozy's current, separate Phase 63 CML Workflow grammar workstream owns
  Workflow DSL syntax, parser admission, `CompositeStateMachineCml`,
  `WorkflowCmlSpec`, and local workflow-state evidence. This Phase may change only the
  already-parsed non-Workflow StateMachine projection bridge when necessary to
  preserve the canonical contract; it must not reinterpret or migrate Workflow
  grammar.

## Planning References

- [Phase 63 Checklist](phase-63-checklist.md)
- [Phase 63.1](phase-63.1.md)
- [Phase 63.2](phase-63.2.md)
- [Provisional Specification](../notes/cml-statemachine-runtime-completion-provisional-specification.md)
- [State Machine Boundary Contract](../design/statemachine-boundary-contract.md)
