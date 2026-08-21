# Phase 58.6 Checklist - Core Resource Lifecycle, Concurrency, and Observability

status=done
phase=[Phase 58.6 - Core Resource Lifecycle, Concurrency, and Observability](phase-58.6.md)
predecessor=[Phase 58.5](phase-58.5.md)
successor=[Phase 58.6.1](phase-58.6.1.md)

## RSC-07A: Core Lifecycle, Concurrency, and Observability

Stage Status:
- Current status: DONE
- Owner: CNCF runtime, repository, cache, and observability maintainers
- Entry rule: Phase 58.5 RSC-06 is DONE.
- Completion rule: Core resource and child lifecycle and concurrent resolution are bounded, idempotent, observable, and safe; Phase 58.6.1 owns producer-cancellation plus provenance-mismatch and terminal-completion versus waiter-cancellation ordering.

- [x] Define cache reuse, refresh, invalidation, and stale detection.
- [x] Define load, release, unload, and shutdown ownership.
- [x] Verify multiple Component instances share immutable artifacts safely.
- [x] Verify concurrent resolution does not duplicate or partially publish cache entries.
- [x] Preserve actual terminal outcomes across refresh, ordinary waiter cancellation, interruption, release, unload, and shutdown races outside the Phase 58.6.1 terminal-ordering exclusions.
- [x] Add bounded resolver CallTree nodes and metrics.
- [x] Add structured repository/resolver diagnostics without sensitive data.
- [x] Verify failure of one resource does not corrupt unrelated releases.

Evidence:
- The accepted RSC-07A Step commit is `f79a54b3cafbd023c463f9b7d37cbf09f6770004` (`Implement component resource lifecycle policy`). The mandatory Phase review, ordinary closure repair, exceptional closure repair, and focused rereviews are retained historical evidence; their remaining terminal-ordering findings are exclusively owned by Phase 58.6.1.
- Historical focused lifecycle evidence recorded 18/18 successes, and the affected accumulator recorded 43/43 successes.
- Frozen-tree release validation `85269-20260821T210709Z` ran serialized `sbt --batch test`: 449 suites completed, 3,347 succeeded, 0 failed, 13 canceled, 1 ignored, and 46 pending; SBT and wrapper exits were zero and the shared lock was released.

Decision Record:
- 2026-08-21: `D-58.6-EXCEPTIONAL-CLOSURE-REPAIR` resolved by the developer as
  `AUTHORIZE_EXCEPTIONAL_CLOSURE_REPAIR`. One exceptional closure repair and
  focused rereview are authorized solely for `CB-58.6-02`, `CB-58.6-06`, and
  `CB-58.6-07` against the Phase 58.6 RSC-07 accumulator rooted at
  `f79a54b3cafbd023c463f9b7d37cbf09f6770004`; this does not reopen the Phase
  full review or authorize any further repair cycle.
- 2026-08-21: `D-58.6-POST-EXCEPTION-CONVERGENCE` selected `SPLIT_PHASE`, and
  `D-58.6-SPLIT-PROPOSAL` approved `58.6 -> 58.6.1 -> 58.7`. The only moved
  unfinished outcome is producer cancellation combined with a loader
  provenance mismatch; Phase 58.6.1 owns its code, executable specification,
  validation, review, and release.
- 2026-08-21: `D-58.6-BASELINE-CLOSURE-001` selected `SPLIT_PHASE` for the
  newly found waiter-cancellation terminal-ordering blocker and the RSC07B
  registry row-count contradiction. This decision authorizes preparation only
  through an explicit `cncf-split-phase` invocation; it authorizes no repair,
  test, review, or commit in this Phase state.
- 2026-08-21: `D-58.6-NESTED-SPLIT-001` approved
  `EXTEND_EXISTING_58.6.1`. The existing child is the sole owner of both
  deferred terminal-ordering recoveries and the registry correction; the order
  remains `58.6 -> 58.6.1 -> 58.7` with no nested decimal Phase.
