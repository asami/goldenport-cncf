# Phase 69.2 - Job Query, Pagination, Result, and Control

status=completed
completed_at=2026-09-11
planned_at=2026-09-09
split_from=[Phase 69](phase-69.md)
depends_on=[Phase 69.1](phase-69.1.md)
successor=[Phase 69.3](phase-69.3.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69.2 Checklist](phase-69.2-checklist.md)

## Purpose

Expose complete, bounded, authorized, restart-safe Job management reads and
controls over Phase 69.1 durable state.

## Scope and Closure

Owns `JM69-04`: stable cursor pagination, exact typed result retrieval,
Task/timeline reads, authorization, state-guarded idempotent `cancel`,
`retry`, `suspend`, and `resume` controls, compatibility `listJobs(limit)`,
and Help/HTTP/CLI projections. Durable recovery control remains the closed
Phase 69.1 boundary; retention, expiry, and deletion remain Phase 69.7
boundaries. Closes with a public management-contract handoff for later JCL and
UX work.

## Split Provenance

Split from Phase 69 on 2026-09-09. Predecessor: Phase 69.1. Successor: Phase
69.3. Consumes the durable recovery handoff and produces canonical query,
result, cursor, and control contracts.

## Work Stack and Validation Boundary

| ID | Stage | Status |
| --- | --- | --- |
| JM69-04 | Query, pagination, result, and control completion | DONE |

Focused cursor, restart, authorization, control, and compatibility
specifications are required; full-suite validation remains owned by Phase 69.7.

## Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4--8h band
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: consumes Phase 69.1 recovery semantics; produces canonical management APIs for Phase 69.3 and Phase 69.6
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6--8h; within the preferred band
- merge_attempts_for_every_sub_4h_child: none
- adjacent_merge_structural_rejection_evidence: none
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: one public-contract handoff avoids coupling JCL and UX to persistence implementation
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 69

## Closure Evidence

`JM69-04` is complete. The accepted focused receipt
`P69.2-JM69-04-STEP-COMMIT-VAL-002` records 12 completed suites, 63 successful tests,
0 failures, terminal SBT and wrapper success, and a released SBT lock. Coverage
includes query pagination and cursor validity, exact detail/result and
Task/timeline reads, authorization and guarded controls, restart recovery
including direct read-model compatibility and terminal unavailable-after-restart
evidence, compatibility, and generated `job_control` protocol/component/OpenAPI
projection.

This was focused acceptance only; no full-suite validation was run for Phase
69.2. Phase 69.7 retains ownership of full-suite, security, retention,
operations, publication, and downstream system acceptance.

The frozen successor handoff is:

- Phase 69.3 receives the canonical cursor, exact-read, and control contracts
  plus the generated `job_control` protocol as the only Job management
  authority; it must not create a raw `JobEngine` query/control alternative.
- Phase 69.6 receives bounded summary/detail/Task/timeline Records, the closed
  result vocabulary `available|pending|unavailable-after-restart`, and the
  `cancel|retry|suspend|resume` controls as the only UI/operator inputs. It
  must not expose payload, debug, or provider data or replace management
  authority.
- Phase 69.7 retains the full-suite, security, retention, operations,
  publication, and downstream system acceptance ownership.

## Non-Goals

- Redefining durable storage/recovery, executable JCL, or application-specific UX.
- Durable recovery control, retention/expiry/deletion, executable JCL
  implementation, UX implementation, strategy rewrite, and new validation
  scope remain excluded from this closure.

## Planning References

- [Phase 69.1](phase-69.1.md)
- [Phase 69.2 Checklist](phase-69.2-checklist.md)
- [Phase 69.3](phase-69.3.md)
- [CBD Review Job Compatibility Spike](../journal/2026/08/2026-08-15-cbd-review-job-compatibility-spike-for-phase-69.md)
