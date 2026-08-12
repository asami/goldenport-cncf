# Phase 57.2 - Explicit Async Migration and Projection Alignment

status=active
split_from=[Phase 57](phase-57.md)
depends_on=[Phase 57.1](phase-57.1.md)
successor=[Phase 57.3](phase-57.3.md)
checklist=[Phase 57.2 Checklist](phase-57.2-checklist.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)

## Goal

Migrate every real Job-dependent caller to explicit admitted asynchronous
intent and align public transports, Help/Admin, diagnostics, and observability
with the direct-versus-Job execution contract.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## Scope

- AES-04 explicit asynchronous caller migration, with completed/accepted Slices
  AES-04A freezing the admission/failure-handoff contract and failing-first
  matrix, and AES-04B completing the structured post-commit async handoff; and
- AES-05 transport and projection alignment.

## Active/Next Slice

- AES-04A — Explicit Async Contract and Failing-First Matrix: COMPLETED/ACCEPTED.
- AES-04B — Structured Post-Commit Async Handoff: COMPLETED/ACCEPTED.
- AES-05 — Transport and Projection Alignment: COMPLETED/ACCEPTED.
- AES-05A — Explicit Execution Transport Metadata: COMPLETED/ACCEPTED.
- AES-05B — Projection Alignment: COMPLETED/ACCEPTED.
  Implementation boundary: FormResultMetadata now consumes explicit
  X-Textus-Execution-Result and authoritative X-Textus-Job-Id headers for
  HTTP/Form annotation, template, and result-id extraction; legacy body
  inference remains unchanged when execution-result metadata is absent.
- Implementation steps complete; next: Phase 57 series release gate and final
  full-gate/release commit.

AES-04 closure evidence: focused validation invocation
`88139-20260812T033747Z` passed 6 suites with 67 passed and 2 existing pending,
all exits 0/0 with the lock released; focused re-review: PASS.

AES-05A acceptance evidence: behavior-batch invocations `16280` (3 suites,
40/40), `16689` (2 suites, 41/41), and `16985` (2 suites, 44/44), plus
CommandScript GWT validation `41677` (1 suite, 16/16); all sbt/wrapper exits
were 0/0 with the lock released. Sol full review accepted the
compatibility-safe ABI/concurrency/transport design. Luna focused re-review
found only local naming violations (`runtimeTokens`, `policyTokens`,
`deprecatedTokens` → flatcase), mechanically repaired by the parent (M0); no
re-review was required.

AES-05B closure evidence: focused validation invocation
`59778-20260812T092530Z` passed 4 suites with 341/341, all exits 0/0 with the
lock released. Explicit Form state alignment and Help/Describe/Schema static,
plus Admin/JobControl explicit boundaries, were verified. Terra full review
findings were repaired; Luna focused re-review: PASS.

## Closure

- Every Job-ID/await/persistence/continuation consumer declares explicit
  asynchronous intent.
- HTTP/Form/Request/Help/Admin/diagnostics distinguish direct results from Job
  acceptance without response-shape inference.
- Job lifecycle and observability remain intact and representative public
  integration passes.

## Non-Goals

- Reopening plain Action semantics accepted in Phase 57.1.
- Canonical Component/CAR compatibility retirement.
- Test-suite cleanup or the series release gate.
