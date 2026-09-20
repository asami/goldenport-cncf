# Phase 77 Common Workflow Contract and sm-workflow Phase 1 Scope Handoff

Date: 2026-09-20
Status: review/reconciliation handoff
Targets:
- asami/goldenport-cncf Phase 77
- asami/sm-workflow Phase 1

## Objective

Reconcile the common Workflow application-facing contract with the decision that Start, abstract reasoning requirements, and human-readable Presentation are CNCF framework capabilities, while reducing sm-workflow Phase 1 to executable-specification completion only.

The goal is a clean two-layer architecture:

```text
CNCF Workflow Framework
  common typed protocol Value Objects
  runtime semantics
  JSON encoding / generic rendering boundary
             ^
             |
sm-workflow
  software-development application payloads
  Workflow definitions
  executable specifications
```

## Part A — Phase 77 common Workflow contract

### Decision

Phase 77 must provide the minimum common application-facing Workflow contract needed by any CNCF Workflow consumer. Do not leave these concepts for sm-workflow to reinvent.

The canonical contract is typed Value Objects. JSON is a schema-versioned encoding for Skill/Codex/process boundaries, not the domain model.

### Minimum common Value Objects

Phase 77 should include at least:

```text
WorkflowStartRequest[I]
WorkflowStartResult[W, O]
WorkflowHandle

Continuation[W, O]
  WorkOrderContinuation[W]
  DecisionContinuation
  WaitContinuation
  TerminalContinuation[O]

WorkOrder[W]
ExecutionRequirement
  ReasoningLevel
  CapabilityRequirement
  RiskLevel

ContinuationResult[R] / WorkResult[R]
Evidence
ExecutionEvidence

Presentation
Progress
```

Application-specific `I/W/R/O` payloads remain typed and are supplied by the consuming component/application.

### Start is framework functionality

CNCF owns the generic ability to start a typed Workflow definition and return a stable handle plus the first semantic Continuation after bounded deterministic progression.

Conceptually:

```text
WorkflowStartRequest[I]
  -> admitted Workflow definition
  -> create WorkflowInstance
  -> bounded deterministic advance
  -> WorkflowStartResult(handle, continuation)
```

sm-workflow may expose convenience operations such as `startGoalPhase`, but they specialize/project this CNCF Start contract rather than defining a second generic start protocol.

### Abstract ReasoningLevel is framework functionality

A `WORK_ORDER` may carry:

```text
ExecutionRequirement
  reasoningLevel
  capabilities
  riskLevel
```

Initial abstract reasoning vocabulary:

```text
ROUTINE
STANDARD
DEEP
CRITICAL
```

These are model/provider independent requirements.

CNCF owns the vocabulary and typed field. The Skill/Host/application owns a versioned mapping policy from the abstract requirement to a concrete execution profile/model/reasoning effort.

Concrete model names must not become Workflow guard/transition semantics. Actual selection may be returned as ExecutionEvidence.

### Presentation is framework functionality

CNCF owns a minimum human-readable Presentation Value Object so Codex console, CLI, Web UI, Flutter UI, and future consumers can render common Workflow progress consistently.

Minimum fields should support:

```text
title
summary?
currentSituation
nextAction?
reason?
progress?
```

Presentation is a projection only. Runtime/Skill/application control must never parse presentation text to decide state, operation, result acceptance, reasoning level, or completion.

Applications populate/specialize the content; they do not redefine the Presentation structure.

### JSON encoding

Phase 77 must prove schema-versioned fail-closed JSON encoding for the minimum common protocol required by Skill/Codex:

```text
StartRequest
  -> StartResult(handle + continuation)
  -> Continuation / WorkOrder
  -> typed Result/Evidence
  -> next Continuation
  -> ...
  -> Terminal
```

This extends the current minimum Continuation-only wire contract just enough to make a CNCF Workflow application usable without an application-specific generic protocol.

### Keep deferred

Do not expand Phase 77 into a full integration platform. Keep the following later:

- rich Presentation/UI layout semantics;
- REST/MCP full protocol surfaces;
- remote transport/service discovery;
- rich parent/child Workflow orchestration runtime;
- Workflow-to-Workflow generated proxy/connectors;
- concrete AI model/provider selection;
- Retry/Timeout/Deadline/Timer/Cancellation;
- advanced failure/idempotency policy;
- 2PC/compensation/recovery;
- application-specific Goal/Phase/Step semantics.

Typed Value Objects should remain compatible with future direct Scala Outer/Inner Workflow composition, but Phase 77 need not implement rich child orchestration.

## Part B — sm-workflow Phase 1 scope reduction

### Completion line

sm-workflow Phase 1 completes when the application-specific Workflow definitions and payload types execute reproducibly as executable specifications on the CNCF Phase 77 foundation.

Phase 1 is not the production-readiness phase.

### Keep in Phase 1

Retain only what is needed to prove the application layer:

- application-specific typed Value Objects/codecs:
  - GoalPhaseStartInput / work input/result / GoalPhaseResult;
  - SplitPhase equivalents;
  - RepositorySync equivalents;
- CML Workflow/StateMachine definitions for the three reference workflows;
- binding to CNCF common Start/Continuation/Result/Terminal contracts;
- deterministic/test Providers needed by executable specifications;
- application-specific Presentation content projected into CNCF Presentation;
- application reasoning-profile mapping policy sufficient to prove abstract ReasoningLevel consumption;
- JSON fixtures proving the CNCF encoding with application payloads;
- executable specifications for:
  - GoalPhaseWorkflow;
  - SplitPhaseWorkflow;
  - RepositorySyncWorkflow;
- proof that automatic/deterministic progression does not require AI turns and semantic boundaries do.

### Remove from Phase 1 completion requirements

Move these to post-Phase-1 connectivity/operational hardening unless a tiny fixture adapter is strictly necessary for executable-spec evidence:

- production-quality public skills;
- skill catalog/install/update/uninstall;
- standalone/CAR bundle distribution completeness;
- full production CLI surface;
- production SQLite operational profile and tuning;
- broad restart/concurrency/lease hardening beyond what CNCF contract testing already requires;
- production recovery documentation;
- cost dashboard/large metric set;
- operational Git/SBT policy completeness beyond deterministic fixture needs;
- Retry/Timeout and other runtime-control extensions;
- MCP/server adapter;
- production AI model/provider dispatch;
- rich UI/console experience.

A minimal test/fixture adapter or console renderer may remain when needed to demonstrate the contract, but it must not turn Phase 1 back into an operational product phase.

### Phase 1 output

Expected Phase 1 handoff:

```text
CNCF Phase 77 common Workflow contract
          +
sm-workflow application payload types
          +
3 application Workflow definitions
          +
deterministic providers/fixtures
          +
executable specifications
          =
sm-workflow Phase 1 CLOSED
```

After closure:

```text
manual skill construction
  -> connectivity tests with Codex
  -> actual development use
  -> identify operational gaps
  -> feed generic gaps back to Cozy/CNCF
  -> harden skills/application
```

Retry/Timeout and other convenience/runtime-control features are introduced from this operational evidence, not as speculative Phase 1 blockers.

## Ownership summary

| Concern | CNCF Phase 77 | sm-workflow Phase 1 |
| --- | --- | --- |
| Generic Start | owns | specializes |
| WorkflowHandle | owns | uses |
| Continuation kinds | owns | uses |
| WorkOrder envelope | owns | supplies application payload |
| ReasoningLevel | owns abstract vocabulary | maps to concrete execution profile |
| Capability/Risk requirement | owns common VO | supplies application values |
| Presentation structure | owns | supplies application content |
| Result/Evidence envelope | owns | supplies typed application result/evidence |
| JSON encoding | owns common codec/envelope | supplies application codecs/schema |
| Goal/Phase/Step semantics | no | owns |
| 3 software-development Workflows | no | owns |
| Executable specs | framework specs | application specs |
| production skill quality | no | post-Phase-1 sm-workflow work |

## Required reconciliation actions

1. Update CNCF Phase 77 and checklist so generic Start, abstract ReasoningLevel, minimum Presentation, and their typed Value Objects/JSON encoding are explicit completion requirements.
2. Keep the stronger Codex-review decisions for UnitOfWork-only Action execution and durable-before-claim Continuation semantics.
3. Mark older journals/notes that defer all Start/Presentation/reasoning functionality as superseded in this minimum-common-contract respect; retain them as design history.
4. Rewrite sm-workflow Phase 1 goal/scope/deliverables/acceptance/checklist around executable-specification completion.
5. Remove application-owned redefinitions of CNCF generic protocol concepts from sm-workflow; retain only typed application payloads and specialization/mapping.
6. Ensure the final dependency path remains:
   ```text
   CNCF 63/63.1/63.2 complete
      -> CNCF 64
      -> CNCF 64.2
      -> CNCF 77
      -> sm-workflow Phase 1 executable specifications
   ```
7. Do not add Phase 80/85/86, Retry/Timeout, or other advanced facilities to this critical path.
