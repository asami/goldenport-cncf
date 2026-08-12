# Phase 57.2 Checklist - Explicit Async Migration and Projection Alignment

status=active
phase=[Phase 57.2 - Explicit Async Migration and Projection Alignment](phase-57.2.md)
predecessor=[Phase 57.1](phase-57.1.md)
successor=[Phase 57.3](phase-57.3.md)

## AES-04: Explicit Asynchronous Migration

Stage Status:
- Current status: DONE
- AES-04A — Explicit Async Contract and Failing-First Matrix: COMPLETED/ACCEPTED.
- AES-04B — Structured Post-Commit Async Handoff: COMPLETED/ACCEPTED.
- Entry rule: Phase 57.1 is DONE.
- Completion rule: Every inventoried Job-dependent caller declares explicit
  intent and retains its required lifecycle.

- [x] Migrate Job-ID, await, persistence, continuation, and control consumers.
- [x] Reject ambiguous or unsupported intent deterministically.
- [x] Preserve authorization, transaction, UnitOfWork, and context boundaries.

AES-04 closure evidence: focused validation invocation
`88139-20260812T033747Z` passed 6 suites with 67 passed and 2 existing pending,
all exits 0/0 with the lock released; focused re-review: PASS.

## AES-05: Transport and Projection Alignment

Stage Status:
- Current status: IN_PROGRESS
- Completed Slice: AES-05A — Explicit Execution Transport Metadata:
  COMPLETED/ACCEPTED.
- Next Slice: AES-05B — Projection Alignment: ACTIVE PREPARATION
  (implementation not started).
- Owner: Phase 57.2 AES-05B.
- Update rule: Record only evidence-backed status changes; retain unchecked
  items until their closure evidence is accepted.
- Checklist closure basis: Every unchecked AES-05 item has accepted focused
  and representative integration evidence and accepted review evidence.
- Entry rule: AES-04 is DONE.
- Completion rule: Every public projection reports the admitted execution
  mode without heuristic inference.

AES-05A acceptance evidence: behavior-batch invocations `16280` (3 suites,
40/40), `16689` (2 suites, 41/41), and `16985` (2 suites, 44/44), plus
CommandScript GWT validation `41677` (1 suite, 16/16); all sbt/wrapper exits
were 0/0 with the lock released. Sol full review accepted the
compatibility-safe ABI/concurrency/transport design. Luna focused re-review
found only local naming violations (`runtimeTokens`, `policyTokens`,
`deprecatedTokens` → flatcase), mechanically repaired by the parent (M0); no
re-review was required.

- [ ] Align Request, HTTP/Form, Help, Admin, diagnostics, metrics, and CallTree.
- [ ] Preserve exact direct responses and explicit Job metadata.
- [ ] Run focused and representative integration once.
- [ ] Review once and commit the accepted Phase.
