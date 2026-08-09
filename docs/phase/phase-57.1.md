# Phase 57.1 - Plain Action Direct Execution

status=planned
split_from=[Phase 57](phase-57.md)
depends_on=[Phase 57](phase-57.md)
successor=[Phase 57.2](phase-57.2.md)
checklist=[Phase 57.1 Checklist](phase-57.1-checklist.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)

## Goal

Inventory the public Action execution contract, freeze its executable matrix,
and make an unclassified plain `Action` execute synchronously through its
direct `ActionCall`, returning the exact `OperationResponse` without creating
an implicit Job.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## Scope

- AES-01 Action/caller inventory and contract freeze;
- AES-02 direct/query/command/explicit-async failing-first matrix; and
- AES-03 plain Action direct execution.

## Closure

- Every admitted plain, query, command, and job-dependent caller is recorded.
- Plain `Action` returns its direct response and creates no implicit Job.
- `QueryAction`, `CommandAction`, authorization, UnitOfWork, context, tracing,
  diagnostics, and failure semantics remain covered.
- Focused Action/Component specifications pass and one independent review
  accepts the public-contract change.

## Non-Goals

- Migrating real asynchronous callers or public projections; Phase 57.2.
- Component/CAR compatibility retirement; Phase 57.3 and Phase 57.4.
- Broad test cleanup or release validation; Phase 57.5.
