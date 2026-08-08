# Phase 57 - Action Execution Semantics

status=planned
planned_at=2026-08-08
depends_on=[Phase 56](phase-56.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 57 Checklist](phase-57-checklist.md)
origin=[Phase 56 CID-05 Runtime Identity Migration Plan](../notes/phase-56-cid05-cncf-runtime-identity-migration-plan.md)
planning_journal=[Phase 57 Action Execution Semantics and Forward Phase Renumbering](../journal/2026/08/2026-08-08-phase-57-action-execution-semantics-and-renumbering.md)

## Purpose

Make Action execution intent explicit, deterministic, and observable. A plain
unclassified `Action` is the simplest synchronous route and returns its direct
`OperationResponse`; job submission and a job-ID response require explicit
command or asynchronous intent.

This phase owns an adjacent public-contract defect discovered by Phase 56
runtime integration. It is independent from Component identity migration and
therefore does not extend CID-05 beyond its namespace-isolated integration
boundary.

## Dependency

Phase 57 begins after Phase 56 closes. Phase 56 supplies qualified Component,
Service, Operation, routing, and runtime integration identity. Phase 57 must
not reopen those identity contracts.

## Selected Direction

- Plain `Action` executes synchronously through the direct `ActionCall` route
  and returns the resulting `OperationResponse`.
- `QueryAction` retains its explicit query/read semantics.
- `CommandAction` retains its explicit command-policy semantics.
- Job submission, asynchronous continuation, persistence, and job
  observability require explicit admitted intent rather than falling back from
  an unclassified `Action`.
- Existing callers that depend on a job ID are inventoried and migrated to an
  explicit command/asynchronous declaration.
- Authorization, transaction, UnitOfWork, ExecutionContext, CallTree,
  observability, and error propagation remain common execution boundaries.
- If the current public Action model cannot express asynchronous intent, the
  API decision is frozen before implementation rather than overloading plain
  `Action` again.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| AES-01 | Inventory and contract freeze | Every plain, query, command, and job-dependent Action caller plus its observable response contract is recorded. | planned |
| AES-02 | Failing-first execution matrix | Direct response, query, command, explicit async/job, failure, and context behavior are executable before implementation. | planned |
| AES-03 | Plain Action direct execution | The unclassified fallback executes synchronously without implicit job creation. | planned |
| AES-04 | Explicit asynchronous migration | Every real job-dependent caller uses an explicit admitted declaration or policy. | planned |
| AES-05 | Transport and projection alignment | Request, HTTP/Form, Help, Admin, diagnostics, and job projections report the selected execution mode consistently. | planned |
| AES-06 | Test-suite hygiene, ecosystem validation, and contract promotion | Redundant Phase/CID closure specs and brittle document/source-string checks are removed while behavioral coverage is retained; representative components and downstream consumers pass; verified behavior is promoted to design/specification. | planned |

## Test Suite Hygiene Boundary

Phase 57 removes specs whose only purpose is Phase-completion evidence,
Markdown or plan-text matching, production-source formatting checks,
substantive duplication of existing unit/integration coverage, or permanent
direct use of `docs/journal` as a fixture. Real feature, boundary, and
regression tests remain. When a useful behavioral test depends on documentary
input, the minimal stable fixture may move to `src/test/resources`.

The initial inspection set is:

- `ComponentFactoryModeBoundarySpec`;
- the document-ledger comparison in
  `Phase56DeferredReleaseCompatibilitySpec` E1;
- `SecurityDeploymentProjectionSpec`;
- `SecurityDeploymentMarkdownProjectionSpec`; and
- other Phase/CID-only closure or acceptance specs matching the same criteria.

Removed specs are not replaced with new closure specs. This cleanup receives
no dedicated broad validation cycle; it is checked by the normal Phase 57 full
validation. `Phase56EcosystemNormativeClosureSpec` was already removed in
CID-08 and is not Phase 57 work.

## Acceptance

- A scalar/read operation implemented as plain `Action` returns its scalar
  `OperationResponse` synchronously and does not return a generated job ID.
- `QueryAction` and `CommandAction` preserve their accepted semantics.
- Explicit asynchronous execution still creates and exposes its documented
  job result, lifecycle, await, and observability surfaces.
- No caller silently changes from job-dependent behavior without an explicit
  compatibility decision and migration record.
- Authorization, transaction, UnitOfWork, ExecutionContext, CallTree,
  diagnostics, and failure behavior are equivalent across the appropriate
  direct and job routes.
- Help/Admin/transport projections distinguish synchronous responses from
  explicit job acceptance without inferring intent from response shape.
- Phase/CID-only documentation and source-string checks do not remain as
  permanent tests; equivalent behavioral coverage is preserved where useful.
- Full focused, representative integration, review, and phase validation
  evidence is recorded before closure.

## Non-Goals

- Reopening Phase 56 Component identity, CAR, repository, or routing work.
- Making every command asynchronous or every query synchronous by subtype
  name alone without inventory evidence.
- Removing the Job engine, job persistence, await, control, or observability.
- Changing authorization, transaction, UnitOfWork, or error semantics to make
  the route conversion easier.
- Treating compatibility fallout as hygiene.

## Planning References

- [Phase 57 Checklist](phase-57-checklist.md)
- [Phase 56](phase-56.md)
- [Phase 56 CID-05 Runtime Identity Migration Plan](../notes/phase-56-cid05-cncf-runtime-identity-migration-plan.md)
- [Phase 57 Action Execution Semantics and Forward Phase Renumbering](../journal/2026/08/2026-08-08-phase-57-action-execution-semantics-and-renumbering.md)
