# Phase 57 Checklist - Action Execution Semantics

status=planned
phase=[Phase 57 - Action Execution Semantics](phase-57.md)
origin=[Phase 56 CID-05 Runtime Identity Migration Plan](../notes/phase-56-cid05-cncf-runtime-identity-migration-plan.md)
planning_journal=[Phase 57 Action Execution Semantics and Forward Phase Renumbering](../journal/2026/08/2026-08-08-phase-57-action-execution-semantics-and-renumbering.md)

This checklist is the authoritative Phase 57 state ledger after Phase 57
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 56 closes.

## AES-01: Inventory and Contract Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF Action, Component, Subsystem, Job, and representative caller
  maintainers
- Entry rule: Phase 56 is closed.
- Completion rule: Every affected Action and caller has one recorded current
  response, job, persistence, continuation, and observability contract.

- [ ] Inventory every concrete plain `Action` reaching `ComponentLogic`.
- [ ] Inventory every `QueryAction`, `CommandAction`, and explicit job route.
- [ ] Record callers that consume a direct response, generated job ID, await
  result, persistence, continuation, or job observability.
- [ ] Record Request, HTTP/Form, internal DSL, Event, Workflow, and Job entry
  points separately.
- [ ] Freeze plain `Action` as the simplest synchronous execution route.
- [ ] Decide the explicit asynchronous declaration/policy boundary before
  implementation if the current public model is insufficient.

Evidence:
- Pending.

## AES-02: Failing-First Execution Matrix

Stage Status:
- Current status: PLANNED
- Owner: CNCF Action execution and executable-specification maintainers
- Entry rule: AES-01 is DONE.
- Completion rule: The target contract fails on current behavior and covers
  every admitted execution mode.

- [ ] Prove plain `Action` returns its direct `OperationResponse`.
- [ ] Prove plain `Action` creates no implicit job record or job ID.
- [ ] Prove `QueryAction` retains query/read semantics.
- [ ] Prove `CommandAction` retains command-policy semantics.
- [ ] Prove explicit asynchronous execution retains job submission, await,
  lifecycle, persistence, and observability.
- [ ] Prove direct and job routes preserve authorization, UnitOfWork,
  ExecutionContext, CallTree, and exact failures.
- [ ] Include public Request and representative HTTP/internal entry paths.

Evidence:
- Pending.

## AES-03: Plain Action Direct Execution

Stage Status:
- Current status: PLANNED
- Owner: CNCF ComponentLogic and Action execution maintainers
- Entry rule: AES-02 is DONE.
- Completion rule: Only the unclassified plain-Action fallback changes to the
  direct synchronous route.

- [ ] Route plain `Action` through direct `ActionCall` execution.
- [ ] Return the exact resulting `OperationResponse`.
- [ ] Remove implicit job creation from the plain fallback.
- [ ] Preserve authorization, transaction, UnitOfWork, context, observability,
  and failure boundaries.
- [ ] Keep query, command, and explicit async selection separate.

Evidence:
- Pending.

## AES-04: Explicit Asynchronous Migration

Stage Status:
- Current status: PLANNED
- Owner: CNCF Job, Workflow, Event, and caller maintainers
- Entry rule: AES-03 is DONE.
- Completion rule: Every inventoried job-dependent caller declares its intent
  explicitly and retains compatible observable behavior.

- [ ] Migrate every job-ID consumer to an explicit command/asynchronous route.
- [ ] Preserve required job persistence, continuation, control, and await
  behavior.
- [ ] Reject ambiguous or unsupported execution intent deterministically.
- [ ] Record source/public compatibility decisions and migration guidance.

Evidence:
- Pending.

## AES-05: Transport and Projection Alignment

Stage Status:
- Current status: PLANNED
- Owner: CNCF Request, HTTP/Form, Help, Admin, and observability maintainers
- Entry rule: AES-04 is DONE.
- Completion rule: Every public projection reports direct and job execution
  according to the admitted policy without heuristic inference.

- [ ] Align Request, HTTP/Form, Help, Admin, diagnostics, metrics, and CallTree.
- [ ] Preserve direct scalar/entity responses without synthetic job metadata.
- [ ] Preserve explicit job links, status, await, and control metadata.
- [ ] Add non-leakage and exact-diagnostic coverage.

Evidence:
- Pending.

## AES-06: Test Suite Hygiene, Validation, and Closure

Stage Status:
- Current status: PLANNED
- Owner: all Phase 57 repository and release maintainers
- Entry rule: AES-05 is DONE.
- Completion rule: Redundant Phase/CID closure and document/source-string
  specs are removed without losing behavioral coverage, and focused,
  representative downstream, full phase, review, documentation, and release
  evidence all pass.

- [ ] Inspect `ComponentFactoryModeBoundarySpec`.
- [ ] Remove the document-ledger comparison from
  `Phase56DeferredReleaseCompatibilitySpec` E1 while retaining its runtime
  compatibility behavior.
- [ ] Inspect `SecurityDeploymentProjectionSpec` and
  `SecurityDeploymentMarkdownProjectionSpec`.
- [ ] Inventory other Phase/CID-only closure and acceptance specs that only
  inspect Markdown/plan text, production-source formatting, duplicate existing
  behavioral coverage, or depend directly on `docs/journal` fixtures.
- [ ] Preserve real feature, boundary, and regression tests; move only useful
  stable documentary fixtures to `src/test/resources` when necessary.
- [ ] Do not create replacement closure specs for deleted specs.
- [ ] Do not run a dedicated broad cleanup validation; use the normal Phase 57
  full validation. `Phase56EcosystemNormativeClosureSpec` was removed in CID-08
  and is not a Phase 57 task.
- [ ] Run focused Action/Component/Subsystem/Job specifications serially.
- [ ] Run representative component and transport integration acceptance.
- [ ] Review public compatibility and all migrated callers independently.
- [ ] Promote verified execution semantics to canonical design/specification.
- [ ] Update Phase 58 entry contracts after Phase 57 closes.
- [ ] Record exact commands, counts, exits, hashes, and review verdicts.

Evidence:
- Pending.
