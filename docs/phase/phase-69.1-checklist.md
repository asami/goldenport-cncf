# Phase 69.1 Checklist - Persistent Job Storage and Process Recovery

status=closed
closed_at=2026-09-11
phase=[Phase 69.1](phase-69.1.md)

Phase 69 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-03: Persistent Storage and Process Recovery

Stage Status:
- Current status: DONE
- Owner: CNCF JobEngine, scheduler, persistence-provider, recovery, retry, and migration maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69 is CLOSED and its durable-contract handoff is accepted.
- Completion rule: Persistent Jobs reconstruct deterministically in a new process while Ephemeral Jobs remain runtime-only.

- [x] Implement canonical durable create/checkpoint/update/terminal writes, admission atomicity, and revision/concurrency protection.
- [x] Reconstruct terminal Jobs, Tasks, results/references, timelines, definition snapshots, retry state, and diagnostics in a new JobEngine process.
- [x] Rehydrate scheduled starts and delayed retries from durable due state; classify crash interruption as resumable, retryable, recovery-required, cancelled, or failed.
- [x] Refuse replay when idempotency, input, definition, authorization, provider, or compatibility evidence is absent; define UnitOfWork/external-effect checkpoint ordering.
- [x] Implement bounded startup recovery, continuation checkpoints, health, backpressure, and operator-visible incomplete recovery state.
- [x] Return structured outcomes for missing, corrupt, stale, incompatible, concurrent, or partial records and preserve Ephemeral non-persistence.
- [x] Add real new-process terminal/scheduled/retry restore, interrupted-work, corruption, migration, concurrency, and Ephemeral-absence Executable Specifications.

Evidence:
- `313daad08fd84b9db22f953f7bc309f6bdf55b3d` is the accepted `JM69-03`
  Step commit, with 56 focused acceptance tests passing.
- The mandatory Phase review lineage and the accepted bounded repair cycle
  close `CPB-P69.1-JM69-03O-001`; focused closure re-review 005 is clean at
  `f22cbe47b27d6d0b694bb9ce52af2c987b9e0b8f7a4f971e00e738c1d73486cc`.
- The distinct Phase release commit binds the final full-suite receipt and
  canonical Hygiene ledger for this closed checklist.

## Phase Completion Gate

- [x] JM69-03 is DONE with restart-safe durable reconstruction and recovery classification evidence.
- [x] Phase 69.2 receives the frozen recovery and durable-state handoff.
