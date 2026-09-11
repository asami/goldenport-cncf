# Phase 69.1 - Persistent Job Storage and Process Recovery

status=closed
closed_at=2026-09-11
planned_at=2026-09-09
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69](phase-69.md)
successor=[Phase 69.2](phase-69.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.1 Checklist](phase-69.1-checklist.md)

## Purpose

Implement the frozen durable Job/Task contract so Persistent Jobs recover
deterministically after process replacement while Ephemeral Jobs remain
runtime-only.

## Scope and Closure

Owns `JM69-03`: durable writes/checkpoints, reconstruction, crash
classification, scheduled/retry rehydration, recovery controls, corruption,
concurrency, and new-process acceptance. Closes with a durable recovery handoff
for Phase 69.2; it does not add cursor query/control APIs.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69. Successor: Phase
69.2. Consumes the frozen durable-record contract and produces restart-safe
record reconstruction and recovery classification.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-03 | Persistent storage and process recovery | planned |

Focused recovery and real-new-process Executable Specifications are required;
full-suite validation remains owned by Phase 69.7.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: crash classification, checkpoint ordering, safe replay refusal, and process-recovery semantics
- frozen_profile_transition_handoff: consumes Phase 69 durable contract; produces restart-safe durable record/recovery handoff for Phase 69.2
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 7--8h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: the recovery handoff isolates expensive failure semantics before public query/control work
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Closure Evidence

`JM69-03` is complete. The accepted Step accumulator is committed at
`313daad08fd84b9db22f953f7bc309f6bdf55b3d`; it establishes canonical durable
writes, provider-independent recovery classification, new-process terminal and
runtime rehydration, delayed due-state recovery, and Ephemeral exclusion.

The mandatory Phase full-review lineage was closed by the accepted bounded
repair cycle for `CPB-P69.1-JM69-03O-001`. Its focused closure re-review is
clean at tree identity
`f22cbe47b27d6d0b694bb9ce52af2c987b9e0b8f7a4f971e00e738c1d73486cc`.
The separate Phase release commit binds this final status, the complete
Hygiene ledger, and the repository full-suite validation.

Phase 69.2 receives the frozen durable-record and recovery handoff, but is
not started by this closure.

## Non-Goals

- Cursor pagination, result/query/control projection (Phase 69.2).
- JCL, governance, CompositeQuery, UX, or distributed/Saga execution.

## Planning References

- [Phase 69](phase-69.md)
- [Phase 69.1 Checklist](phase-69.1-checklist.md)
- [Phase 69.2](phase-69.2.md)
- [Job Task Execution Persistence Design](../journal/2026/03/job-task-execution-persistence-design.md)
