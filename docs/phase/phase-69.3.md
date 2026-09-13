# Phase 69.3 - Executable JCL Runtime

status=in_progress
planned_at=2026-09-09
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69.2](phase-69.2.md)
successor=[Phase 69.4](phase-69.4.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.3 Checklist](phase-69.3-checklist.md)

## Purpose

Deliver closed, deterministic procedural and Event-driven JCL through existing
Action, Event, Operation, Task, Job, Workflow, authorization, and observability
boundaries.

## Scope and Closure

Owns `JM69-05`: grammar/model, runtime sequencing and continuation,
Workflow separation, event correlation, validation/rejection, durable position,
and executable behavior specifications. Closes with immutable JCL definition
snapshots and execution semantics for Phase 69.4.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69.2. Successor: Phase
69.4. Consumes management APIs and produces executable JCL and definition
snapshot handoffs.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-05 | Executable JCL runtime | IN_PROGRESS |

Focused grammar, determinism, replay, cancellation, restart, and no-bypass
specifications are required; full-suite validation remains owned by Phase 69.7.

## Current JM69-05G Compatibility Boundary

JM69-05G keeps the JobDefinition direct-identity repair local to
`JobDefinitionEntity`: new records use a versioned lossless key encoding, and
reads retain only an exact persisted-key legacy fallback. The repair also requires
safe YAML construction and exactly-one-target semantic validation before either
Action or Workflow dispatch. It does not migrate records, add store-wide key
indexing, or decide the deferred EntityId/UniversalId redesign. Focused
validation and the fresh lightweight Step review remain required before this
stage can advance.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: closed JCL semantics, Workflow ownership, deterministic event continuation, and pre-execution rejection
- frozen_profile_transition_handoff: consumes Phase 69.2 management APIs; produces immutable executable definition semantics for Phase 69.4
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7--8h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: isolates language/ownership decisions so rollout work is lower-cost execution
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Non-Goals

- General-purpose scripting, unbounded DAGs, BPMN, or distributed orchestration.

## Planning References

- [Phase 69.2](phase-69.2.md)
- [Phase 69.3 Checklist](phase-69.3-checklist.md)
- [Phase 69.4](phase-69.4.md)
