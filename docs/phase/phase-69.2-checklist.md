# Phase 69.2 Checklist - Job Query, Pagination, Result, and Control

status=planned
phase=[Phase 69.2](phase-69.2.md)

Phase 69.1 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-04: Query, Pagination, Result, and Control Completion

Stage Status:
- Current status: OPEN
- Owner: CNCF Job query/control, protocol, Help, HTTP, CLI, and authorization maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.1 is CLOSED.
- Completion rule: Exact and enumerated Job management reads and controls remain complete, bounded, authorized, and restart-safe.

- [ ] Define stable cursor/snapshot pagination, filters, ordering, continuation validity, and bounded administrative search for Job, Task, timeline, and history records.
- [ ] Implement exact Job and typed-result retrieval, externalized-payload verification, Task page/detail/tree, and timeline/history reads from durable state after restart.
- [ ] Preserve authorization and subject/tenant isolation before existence, counts, cursors, payload metadata, or diagnostics are revealed.
- [ ] Implement guarded idempotent cancel, retry, suspend, resume, recovery, and retention controls; retain `listJobs(limit)` only as the bounded compatibility facade.
- [ ] Project Help/meta/Record/JSON/HTTP/CLI contracts and structured invalid-cursor, expired-snapshot, unavailable-payload, and recovery outcomes.
- [ ] Add multi-page, concurrent-update, restart-continuation, authorization, payload, control, and compatibility Executable Specifications.

Evidence:
- Pending.

## Phase Completion Gate

- [ ] JM69-04 is DONE with canonical cursor, exact result, authorization, and control evidence.
- [ ] Phases 69.3 and 69.6 receive the frozen management-contract handoff.
