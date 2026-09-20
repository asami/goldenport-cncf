# Phase 77 Common Contract Reconciliation Decision

Date: 2026-09-20
Status: accepted design decision for Phase 77 and the `sm-workflow` Phase 1 handoff
Sources:
- [Phase 77 Common Workflow Contract and sm-workflow Phase 1 Scope Handoff](2026-09-20-phase-77-common-contract-sm-workflow-phase1-scope-handoff.md)
- [Phase 64/77 Critical-Path Reconciliation](2026-09-20-phase-64-77-critical-path-reconciliation.md)

## Decision

Phase 77 owns one minimum common, typed Workflow contract. Scala Value Objects
and algebraic types are canonical; schema-versioned, fail-closed JSON is the
encoding used at Skill/Codex and separate-process boundaries. `sm-workflow`
specializes that contract with software-development payloads and Workflow
definitions; it does not recreate a second generic protocol.

The minimum Phase 77 contract includes:

- `WorkflowStartRequest[I]`, `WorkflowStartResult[W, O]`, and
  `WorkflowHandle`;
- closed `Continuation = WORK_ORDER | DECISION | WAIT | TERMINAL`, typed
  WorkOrder/WorkResult, Completion, and Evidence;
- Workflow/Continuation identity, expected revision, `ContextSnapshot`, typed
  payload schemas, and typed terminal result;
- `ExecutionRequirement` with the abstract `ROUTINE`, `STANDARD`, `DEEP`, and
  `CRITICAL` `ReasoningLevel`, plus small `CapabilityRequirement` and
  `RiskLevel` Value Objects;
- `ExecutionEvidence` for a Skill/Host-dispatched WorkResult, containing the
  requested abstract requirement, selected worker profile, and mapping-policy
  version; and
- `Presentation` / `Progress` as a projection with required `title` and
  `currentSituation`, plus optional `summary`, `nextAction`, `reason`, and
  progress data.

## Boundaries

`ExecutionEvidence` records how the Host/Skill fulfilled an admitted WorkOrder.
It is required where the Skill/Host dispatch contract requires it and is
not-applicable for deterministic/local Providers; no Provider invents a worker
profile. Concrete model/provider selection and presentation text never control
Workflow guards, transitions, result admission, or completion.

Capability and risk remain small typed requirements. Phase 77 does not add a
provider-policy engine, dynamic dispatch surface, UI capability model, rich
layout semantics, concrete model-selection policy, parent/child orchestration,
Retry/Timeout, or REST/MCP/UI protocol surface.

For `sm-workflow`, `WorkflowInteraction` is the public projection of the
framework `WorkflowHandle` and current `Continuation`. It may provide
profile-specific start operations and `advanceWorkflow(handle, response?)`,
but it must preserve the framework Workflow/Continuation identity, revision,
typed response-admission, and terminal/suspension semantics. It must not
create a competing Workflow lifecycle or a second generic Start/Continuation
protocol.

## Phase 1 handoff

`sm-workflow` Phase 1 closes on application payload types, the GoalPhase,
SplitPhase, and RepositorySync Workflow definitions, deterministic/test
Providers and fixtures, common-contract JSON fixtures, and executable
specifications proving deterministic progression and semantic boundaries.

Production public skills, catalog/distribution work, broad CLI/UI surface,
SQLite operational tuning, lease/restart/concurrency hardening beyond fixture
needs, provider dispatch policy, recovery operations, cost dashboards, and
transport adapters remain post-Phase-1 operational hardening. This decision
does not reintroduce Phase 80/85/86 or other advanced facilities into the
critical path.
