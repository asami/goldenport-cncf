# Phase 63.2 - StateMachine Committed Transition, Observability, and Acceptance

status=completed
planned_at=2026-09-17
split_from=[Phase 63](phase-63.md)
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-63.2
aggregate_validation_sequence=["PHASE-63","PHASE-63.1","PHASE-63.2"]
depends_on=[Phase 63.1](phase-63.1.md)
successor=[Phase 64](phase-64.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 63.2 Checklist](phase-63.2-checklist.md)

## Purpose

Publish the post-commit `CommittedTransition` contract, make every transition
outcome safely observable, and prove the full CML-to-runtime path. This is the
final owner of the Phase 63 split sequence and the sole owner of its repository
full-suite validation.

## Split Provenance

Split from Phase 63 on 2026-09-17. It consumes the Phase 63.1 generated ABI
and atomic UnitOfWork handoff, then produces the committed-transition contract
that Phase 64 consumes for Composite StateMachine and Workflow progression.
It does not implement, plan, or execute that downstream progression.

## Scope and Closure

Owns `SMR-06` through `SMR-08`: explicit trigger bindings and
`CommittedTransition`, failure observability and compatibility, and generated
cross-repository acceptance/promotion. Focused evidence proves the exact
CML -> ComponentFactory -> UnitOfWork -> persisted state -> post-commit
envelope path. The force release records the aggregate-entry compatibility,
full-suite, full-review, and documentation-promotion deferrals explicitly; it
does not claim that those deferred assurance activities passed.

`CommittedTransition` is a post-commit handoff boundary, not an in-phase
Workflow trigger executor. Phase 63.2 records the canonical envelope and
proves its source; Phase 64 owns Composite StateMachine/Workflow interpretation
and independent `WorkflowInstance` progression, while Phase 77 owns generated
Workflow API/SPI admission, Provider execution, durable Continuation/resume,
and Skill projection.

## Work Stack and Validation Boundary

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SMR-06 | Trigger binding and committed transition | Only a successful UnitOfWork commit produces one stable, idempotent downstream envelope. | done |
| SMR-07 | Failure observability and compatibility | Every outcome is safely structured and observable after rollback without leaking inputs or expressions. | done |
| SMR-08 | Cross-repository acceptance and promotion | The focused generated `SalesOrder` runtime path is accepted; aggregate assurance is explicitly deferred by forced release. | done (forced) |

Focused trigger, replay, rollback, non-leakage, generated-sample, and affected
consumer suites passed. The requested expedited closure deliberately defers the
aggregate repository full suite for Phases 63, 63.1, and 63.2; that assurance
gap is durable force-release exception evidence, rather than a passing claim.

## Current Execution Record

The checklist is the closure authority. SMR-06 and SMR-07 have their committed
implementation and executable-specification evidence. SMR-08 has focused acceptance: the
Cozy generated `SalesOrder` fixture now carries typed FINAL semantics, while
the CNCF planner explicitly treats creation without a current record as no
transition and still rejects malformed existing-record FINAL updates.

`P632-E3-CNCF-SAVE-FOCUSED-020` passed the saved-record propagation and
planner-focused CNCF specifications. Its changed local artifact was refreshed
by successful `P632-E3-CNCF-REFRESH-021` `publishLocal`, and the exact generated
Cozy SalesOrder runtime passed in `P632-E3-COZY-SCRIPTED-024`. The scenario
proves the explicit terminal save route, post-commit committed-transition
evidence, and rejection without an extra envelope for an invalid saved
reversal. A focused re-review accepted the repair delta. By direct user
direction on 2026-09-20, this Phase closes through a forced release without the
aggregate full suite, a new full review, or further documentation promotion.
Those exceptions remain visible in the release audit.

## Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: no comparable completed Phase; Stage 06--08 are the final delivery/assurance portion of the 16--21h pre-split estimate
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes Phase 63.1 generated ABI and atomic execution; produces the committed-transition and verified StateMachine runtime handoff for Phase 64
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6--7.5h; within the 8h ceiling
- incoming_semantic_handoffs: [{from_child: Phase 63.1, to_child: Phase 63.2, kind: release, input: accepted generated transition ABI and atomic UnitOfWork execution contract, action_or_decision: close Phase 63.1 with focused generation and runtime evidence, output: immutable post-commit envelope and final-validation input, owner: Phase 63.1, invalidation_reason: changing generated ABI or atomic execution invalidates trigger, rollback, and end-to-end acceptance evidence}]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging with Phase 63.1 would couple pre-commit atomic mutation to post-commit Event/Job publication, observability, and aggregate final validation beyond the 8h ceiling
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: the final handoff prevents Phase 64 from creating an inferred or attempted-transition Workflow trigger
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 63

## Acceptance

- Explicit Operation/event bindings provide one versioned trigger context.
  They bind entry into the local Phase 63 StateMachine only; they do not select
  a Workflow action, Required SPI Provider, Operation/Job continuation, or
  external route.
- Only a successful commit emits one correlated, idempotent
  `CommittedTransition`; rejected, failed, canceled, and rolled-back attempts
  emit no success envelope.
- Structured outcomes retain bounded machine, transition, entity, trigger,
  operation, trace, and safe cause evidence after rollback without leaking
  state, payload, credentials, expression source, or evaluated values.
- A representative generated `SalesOrder`/`SalesStatus` scenario proves
  ordering, guards, Phase-63.1-owned local actions, already-admitted local
  create/update/patch/command enforcement, rollback, replay, and
  ComponentFactory automatic bootstrap. It does not prove Composite/Workflow
  actions, action-program interpretation, Provider resolution, suspension,
  resume, or Skill work projection.
- The aggregate full suite is deferred by this Phase's forced release; the
  accepted focused handoff is the only StateMachine runtime input for Phase 64.

## Non-Goals

- Reopening canonical transition/predicate semantics (Phase 63) or generated
  ABI/atomic mutation ownership (Phase 63.1).
- WorkflowInstance progression, Composite StateMachine semantics, executable
  DbC, timers, parallel/human tasks, compensation, connectors, or distributed
  transactions.
- Composite-state derivation, Workflow transition/action planning, generated
  Workflow API/SPI discovery, Provider construction or resolution,
  `ExecProgram`-based Workflow Action execution, Continuation suspension or
  resume, and WorkflowInstance persistence/recovery. Those belong to Phase 64
  / 64.2 and Phase 77.
- Selecting or invoking the next Workflow Operation/Job from a
  `CommittedTransition`, or projecting any Workflow work to Skill, Human, UI,
  or remote-worker adapters.
- Generic Event reception policy, generic transaction outcome lanes, or a
  second transition/workflow language.

## Planning References

- [Phase 63](phase-63.md)
- [Phase 63.1](phase-63.1.md)
- [Phase 63.2 Checklist](phase-63.2-checklist.md)
- [Phase 64](phase-64.md)
- [Provisional Specification](../notes/cml-statemachine-runtime-completion-provisional-specification.md)
