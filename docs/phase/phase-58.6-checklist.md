# Phase 58.6 Checklist - Resource Lifecycle, Concurrency, and Observability

status=planned
phase=[Phase 58.6 - Resource Lifecycle, Concurrency, and Observability](phase-58.6.md)
predecessor=[Phase 58.5](phase-58.5.md)
successor=[Phase 58.7](phase-58.7.md)

## RSC-07: Lifecycle, Concurrency, and Observability

Stage Status:
- Current status: PLANNED
- Owner: CNCF runtime, repository, cache, and observability maintainers
- Entry rule: Phase 58.5 RSC-06 is DONE.
- Completion rule: Resource and child lifecycle and concurrent resolution are bounded, idempotent, observable, and safe.

- [ ] Define cache reuse, refresh, invalidation, and stale detection.
- [ ] Define load, release, unload, and shutdown ownership.
- [ ] Verify multiple Component instances share immutable artifacts safely.
- [ ] Verify concurrent resolution does not duplicate or partially publish cache entries.
- [ ] Preserve actual terminal outcomes across cancellation and refresh races.
- [ ] Add bounded resolver CallTree nodes and metrics.
- [ ] Add structured repository/resolver diagnostics without sensitive data.
- [ ] Verify failure of one resource does not corrupt unrelated releases.

Evidence:
- Pending.
