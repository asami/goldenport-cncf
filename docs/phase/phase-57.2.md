# Phase 57.2 - Explicit Async Migration and Projection Alignment

status=planned
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

- AES-04 explicit asynchronous caller migration; and
- AES-05 transport and projection alignment.

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
