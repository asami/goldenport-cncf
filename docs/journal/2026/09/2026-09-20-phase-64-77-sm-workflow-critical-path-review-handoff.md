# Phase 64 / 77 Review Handoff for sm-workflow Critical Path

Date: 2026-09-20
Status: review handoff / no implementation change in this record
Target: next CNCF planning/reconciliation task

## Context

Phase 63 / 63.1 / 63.2 are now closed/completed. Phase 64 and Phase 77 were updated from Codex review. This handoff records the remaining reconciliation needed to keep the shortest valid path to sm-workflow Phase 1 executable specifications.

## Accepted review improvements

Retain the following Phase 77 refinements.

### Canonical Action execution

Every effectful admitted Action should execute through:

```text
Action
  -> ExecProgram[UnitOfWorkOp, ActionExecution]
  -> planner/interpreter
  -> Completed | Suspended | Failed
```

Do not retain a canonical direct side-effect escape path such as `ResolvedAction.run(...)` / `Effect.execute` in parallel with UnitOfWork execution.

### Durable suspension boundary

For external Required SPI work:

```text
active UnitOfWork
  -> persist Workflow/StateMachine progression + durable Continuation
  -> commit
  -> after commit: make Continuation externally claimable
  -> external Skill/Human/UI result
  -> fresh UnitOfWork
  -> validate identity/revision/ContextSnapshot/evidence
  -> resume
```

Rollback must expose no claimable external work. Stale/duplicate/incompatible results fail closed.

### Phase 63.2 origin

For entity-triggered Workflow progression, the accepted origin is the Phase 63.2 `CommittedTransition` through the Phase 64 binding/derivation contract. Do not add a raw attempted-transition, event-name inference, or Operation shortcut.

## Finding 1: Phase 64.1 now overlaps completed Phase 63.1

Phase 63.1 is complete and already owns StateMachine generation plus atomic CNCF execution: candidate state, local effects, persistence, UnitOfWork commit/rollback, and rejection of partial mutation.

Current Phase 64.1, reduced to “Local UnitOfWork Atomic Execution Foundation,” substantially repeats that completed foundation.

### Recommended reconciliation

Remove Phase 64.1 from the sm-workflow critical path and make Phase 64.2 consume the completed Phase 63.1 atomic execution contract directly.

Expected path:

```text
Phase 63 / 63.1 / 63.2 COMPLETE
  -> Phase 64
  -> Phase 64.2
  -> Phase 77
  -> sm-workflow Phase 1 executable specification
```

Do not reopen Phase 63.1. Preserve Phase 64.1 as historical/superseded planning evidence or explicitly mark it obsolete/superseded rather than silently deleting history.

Phase 64.2 should retain only the incremental work not already closed by 63.1: ExecProgram planning/analysis, deterministic test/simulation interpreter, production alignment, Composite/Workflow executable acceptance, and algebra-gap review.

## Finding 2: Phase 77 protocol scope was reduced below sm-workflow Phase 1 needs

The Codex review correctly removed broad protocol ambitions, but the current Phase 77 now retains only a minimum Continuation JSON request/result wire contract and defers generic Start, Presentation, reasoning vocabulary, and composition.

That is too narrow for the agreed sm-workflow Phase 1 executable-specification boundary.

sm-workflow Phase 1 must be able to prove:

```text
application typed StartInput
  -> start Workflow
  -> WorkflowHandle + first Continuation
  -> WorkOrder / Decision / Wait
  -> typed Result/Evidence
  -> next Continuation
  -> ...
  -> typed Terminal result
```

It also requires a WorkOrder to carry an abstract reasoning requirement that the Skill/Host maps to a concrete worker profile, and enough human-readable structured presentation for Codex console progress visibility.

### Minimum protocol to retain in Phase 77

Retain a **Minimum Typed Workflow Protocol**, with Value Objects as the canonical model and JSON only as the Skill/Codex wire encoding.

Minimum required model:

```text
WorkflowStartRequest[I]
WorkflowStartResult[W, O]
  WorkflowHandle
  Continuation[W, O]

Continuation
  WORK_ORDER
    WorkOrder[W]
      ExecutionRequirement
        ReasoningLevel
        capability/risk requirements
  DECISION
  WAIT
  TERMINAL[O]

ContinuationResult[R] / WorkResult[R]
Evidence
MinimalPresentation
```

Minimum abstract reasoning vocabulary should remain model-independent. The previously agreed initial vocabulary is:

```text
ROUTINE
STANDARD
DEEP
CRITICAL
```

Concrete model/provider/reasoning effort remains Skill/Host mapping policy and may be returned as execution evidence; it must not become Workflow transition semantics.

Minimum Presentation should be structured enough to render at least:

- current situation;
- next action;
- reason where useful;
- progress where available.

Presentation is projection only and must never be parsed for Workflow control.

### Still defer from Phase 77

The Codex scope reduction remains correct for:

- rich Presentation/UI model;
- broad REST/MCP/UI surfaces;
- remote transport/service discovery;
- rich parent/child Workflow orchestration runtime;
- Workflow-to-Workflow generated proxy/connectors;
- application-specific Goal/Phase/Step semantics;
- concrete AI model selection;
- Retry/Timeout and later runtime-control facilities;
- advanced transaction/compensation/recovery.

Typed protocol design should remain compatible with future direct Scala Outer/Inner Workflow composition, but Phase 77 need not implement rich child orchestration.

## Phase 64 assessment

The revised Phase 64 boundary is otherwise sound.

Retain:

- constituent StateMachine role/identity;
- deterministic composite-state derivation;
- derived composite transition and causal correlation;
- constituent/composite Action ordering and provenance;
- minimal Workflow semantic identity/lifecycle/progression/correlation;
- real CML fixture and exact Phase 77 handoff;
- Phase 63.2 `CommittedTransition` as the committed upstream event boundary.

Do not move durable Continuation, API/SPI Provider execution, public typed protocol, or Skill projection back into Phase 64.

## Target ownership after reconciliation

```text
Phase 63.1 [complete]
  atomic StateMachine UnitOfWork execution
  commit / rollback / persistence

Phase 63.2 [complete]
  CommittedTransition + post-commit evidence

Phase 64
  Composite StateMachine semantics
  minimal Workflow semantics
  CommittedTransition -> composite/workflow binding

Phase 64.2
  ExecProgram planning
  deterministic test/simulation interpreter
  production alignment
  Composite/Workflow executable acceptance

Phase 77
  StateMachine API/SPI Provider runtime
  ActionExecution
  durable WorkflowInstance persistence SPI
  bounded advance
  durable Continuation/resume
  minimum typed Workflow protocol
  JSON Skill/Codex encoding
  abstract reasoning requirement
  minimum human-readable Presentation
  Generic Skill projection
  sm-workflow consumer handoff

sm-workflow Phase 1
  application-specific typed payloads
  GoalPhase / SplitPhase / RepositorySync executable specifications
  Skill/Host reasoning-profile mapping
```

## Required next action

Reconcile Phase 64.1 / 64.2 dependencies and Phase 77 protocol scope against this handoff. Update affected checklists, notes/journals, and sm-workflow Phase 1 dependency wording consistently. Do not expand the critical path with later operational features.

## Reconciliation status

The requested reconciliation was recorded on 2026-09-20 in
[Phase 64/77 Critical-Path Reconciliation](2026-09-20-phase-64-77-critical-path-reconciliation.md).
The current Phase/checklist ledgers retain the minimum typed protocol while
leaving the broad operational surface out of the `sm-workflow` Phase 1 path.
