# Phase 69.1 Checklist - Persistent Job Storage and Process Recovery

status=planned
phase=[Phase 69.1](phase-69.1.md)

Phase 69 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-03: Persistent Storage and Process Recovery

Stage Status:
- Current status: OPEN
- Owner: CNCF JobEngine, scheduler, persistence-provider, recovery, retry, and migration maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69 is CLOSED and its durable-contract handoff is accepted.
- Completion rule: Persistent Jobs reconstruct deterministically in a new process while Ephemeral Jobs remain runtime-only.

- [ ] Implement canonical durable create/checkpoint/update/terminal writes, admission atomicity, and revision/concurrency protection.
- [ ] Reconstruct terminal Jobs, Tasks, results/references, timelines, definition snapshots, retry state, and diagnostics in a new JobEngine process.
- [ ] Rehydrate scheduled starts and delayed retries from durable due state; classify crash interruption as resumable, retryable, recovery-required, cancelled, or failed.
- [ ] Refuse replay when idempotency, input, definition, authorization, provider, or compatibility evidence is absent; define UnitOfWork/external-effect checkpoint ordering.
- [ ] Implement bounded startup recovery, continuation checkpoints, health, backpressure, and operator-visible incomplete recovery state.
- [ ] Return structured outcomes for missing, corrupt, stale, incompatible, concurrent, or partial records and preserve Ephemeral non-persistence.
- [ ] Add real new-process terminal/scheduled/retry restore, interrupted-work, corruption, migration, concurrency, and Ephemeral-absence Executable Specifications.

Evidence:
- Pending.

## Phase Completion Gate

- [ ] JM69-03 is DONE with restart-safe durable reconstruction and recovery classification evidence.
- [ ] Phase 69.2 receives the frozen recovery and durable-state handoff.
