# Phase 77 - StateMachine API/SPI Runtime and First Skill-Driven Workflow Vertical Slice

status=planned
planned_at=2026-09-16
depends_on=[Phase 64](phase-64.md), [Phase 64.2](phase-64.2.md), and Cozy Phase 62.3 producer handoff
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-statemachine-api-spi-runtime-and-skill-driven-workflow)
checklist=[Phase 77 Checklist](phase-77-checklist.md)

## Purpose

Admit Cozy's first-class generated CML `WORKFLOW`/StateMachine ABI through CNCF without reparsing CML or creating a second Workflow language, and implement the reusable StateMachine API/SPI execution foundation consumed by Workflow and future StateMachine-based facilities.

Phase 77 consumes Phase 64's minimum Composite StateMachine/Workflow semantics and Phase 64.2's canonical `ExecProgram[UnitOfWorkOp, A]` contract. It owns the provider-neutral independently durable WorkflowInstance persistence SPI required by the vertical slice. For an entity-triggered Workflow, the only lifecycle input is the successful Phase 63.2 `CommittedTransition` handoff; attempted or rolled-back transitions do not start or advance a Workflow.

The first vertical slice is Skill-driven Workflow execution: internal deterministic Actions complete in the runtime, an external semantic SPI Action suspends as a durable Continuation, a typed Skill result resumes the StateMachine, and internal closing Actions complete normally.

```text
CML WORKFLOW
  -> Cozy generated StateMachine/Workflow ABI
  -> Phase 64 Composite/Workflow semantic contract
  -> CNCF ABI admission + ComponentFactory bootstrap
  -> StateMachine API/SPI Runtime
       -> selected Action -> ExecProgram[UnitOfWorkOp, ActionExecution]
       -> internal provider -> Completed(Result)
       -> external provider -> Suspended(Continuation)
       -> failed provider   -> Failed(Error)
  -> Workflow projection / component-owned durable runtime
```

## Canonical runtime model

```text
StateMachine Runtime
  Provided API dispatch
  Required SPI provider resolution
  State / Action / Transition
  ActionExecution
    Completed(Result)
    Suspended(Continuation)
    Failed(Error)
  Continuation / typed resume
        ^
        |
Workflow Runtime
  process-oriented projection and metadata
```

There is no Workflow-wide Orchestration/Continuation mode and no semantic `InvocationBinding = ORCHESTRATION | CONTINUATION` switch. Continuation is the durable suspension outcome of an Action/provider execution that requires an external result.

## Ownership Boundary

- Cozy Phase 62 owns CML `WORKFLOW` source and StateMachine lowering; Phase 62.1 owns the generic StateMachine Provided API / Required SPI and ActionExecution contracts, Phase 62.2 the generated ABI/bootstrap metadata, and Phase 62.3 the producer fixture and CNCF handoff.
- CNCF Phase 63.2 owns the StateMachine-specific post-commit `CommittedTransition` source contract; it does not start or execute Workflows.
- CNCF Phase 64 owns minimum Composite/Workflow semantics and pure derivation. CNCF Phase 64.2 owns the canonical `ExecProgram[UnitOfWorkOp, A]` planning/interpreter boundary.
- CNCF Phase 77 owns the provider-neutral independently durable WorkflowInstance persistence SPI, generated ABI admission, ComponentFactory discovery, Provided API dispatch, Required SPI Provider resolution, Action execution through the canonical program, durable Continuation/resume, and deterministic progression.
- Workflow reuses/projects the StateMachine foundation; CNCF does not add a parallel Workflow-specific API/SPI or Continuation engine.
- An entity-local StateMachine owns only lifecycle data persisted with that entity. WorkflowInstance process persistence remains independently owned.
- Phase 77 defines the provider-neutral independently durable WorkflowInstance persistence SPI and its persisted revision/history/current-suspension contract. A consuming component supplies the concrete datastore/provider, migration, retention and lease policy plus public client behavior.
- Component implementation code supplies Required SPI implementations through Providers. `ComponentFactory` is the construction/injection boundary: component-specific Providers implement typed Required SPI operations rather than overriding individual Workflow/StateMachine Actions.
- Generic Skill Workflow Support projects externally claimable Continuation SPI requests into Skill commands/WorkOrders. Domain-specific software-development policy remains in `sm-workflow`.

## Terminology and Continuation Boundary

- **Required SPI** is the protocol-independent typed operation contract.
- **Provider SPI** is the component-programmer implementation boundary constructed and injected through `ComponentFactory`.
- **Continuation Protocol** is entered only when ActionExecution returns `Suspended(Continuation)`.
- **Continuation SPI Projection** is the durable external IoC port over that suspended Required SPI operation.

A host may inject a Skill, Human, UI, or remote adapter through Provider construction. Participant/capability metadata may constrain Provider or host selection, but there is no semantic `InvocationBinding` or Workflow-wide protocol mode.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-01 | Freeze supported Cozy 62.1-62.3 StateMachine/Workflow ABI versions, admission diagnostics, and compatibility policy. | planned |
| CWF-77-02 | Discover generated definitions and StateMachine API/SPI metadata through ComponentFactory while preserving source identity. | planned |
| CWF-77-03 | Bind Phase 64's semantic WorkflowDefinition/WorkflowInstance identity/progression to Phase 77's provider-neutral independently durable persistence SPI and executable runtime. | planned |
| CWF-77-04 | Implement deterministic StateMachine progression and bounded next-Action selection without inferring semantics from names/effects. | planned |
| CWF-77-05 | Lower and interpret every admitted executable Action through `ExecProgram[UnitOfWorkOp, ActionExecution]`; direct callback/effect execution is not accepted. | planned |
| CWF-77-06 | Implement Required SPI Provider resolution and ComponentFactory Provider construction for local/direct, external-continuation, and deterministic test providers. | planned |
| CWF-77-07 | Implement durable Continuation creation/resume with atomic suspension persistence, after-commit claim, fresh-UnitOfWork resume, and stale/duplicate rejection. | planned |
| CWF-77-08 | Provide the minimum Generic Skill Workflow projection over the Continuation SPI without exposing internal deterministic Actions as WorkOrders. | planned |
| CWF-77-09 | Prove Cozy's real internal -> suspended external -> resumed -> internal vertical slice and freeze the `sm-workflow` consumer handoff. | planned |
| CWF-77-10 | Freeze the minimum typed Workflow protocol and its schema-versioned, fail-closed Skill/Codex JSON encoding; record exact CML-first cross-repository evidence and defer only broad protocol expansion. | planned |

## Minimum Typed Workflow Protocol and Skill/Codex JSON Wire Contract

Phase 77 includes the smallest application-neutral typed protocol needed to
start a Workflow, expose its next semantic boundary, accept a later Skill
result, and reach a typed terminal result. Value Objects/algebraic types are
the canonical Workflow model; JSON is the schema-versioned, fail-closed
Skill/Codex wire encoding. This is not a broad public REST/MCP/UI protocol
surface or a general-purpose Start API expansion.

```text
WorkflowStartRequest[I]
  -> WorkflowStartResult[W, O]
       WorkflowHandle
       Continuation[W, O]

Continuation
  WORK_ORDER -> WorkOrder[W] -> ExecutionRequirement
  DECISION
  WAIT
  TERMINAL[O]

ContinuationResult[R] / WorkResult[R]
Evidence
MinimalPresentation
```

- `WorkflowStartRequest` and `WorkflowStartResult` start only an admitted
  Workflow and return its `WorkflowHandle` plus first `Continuation` or typed
  terminal result; application input/output payloads remain application-owned.
- `Continuation` is a closed set of `WORK_ORDER`, `DECISION`, `WAIT`, and
  `TERMINAL`; `ContinuationRequest` is the externally claimable encoding of a
  durable suspended WorkOrder, and `ContinuationResult`/`WorkResult` supplies
  its typed completion.
- Request, result, and externally exchanged WorkOrder encodings carry schema
  identity/version and use fail-closed JSON codecs. They preserve Workflow and
  Continuation identity, expected revision, `ContextSnapshot`, typed
  input/result, and Completion/Evidence requirements.
- `WorkflowHandle` provides the minimum stable reference to the Workflow and
  its terminal or suspension state.
- `ExecutionRequirement` includes model-independent capability/risk
  requirements and the initial closed `ReasoningLevel` vocabulary:
  `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL`. Skill/Host maps this request
  to a concrete model/provider/reasoning effort and may return that mapping as
  execution evidence; it is not Workflow transition semantics.
- `MinimalPresentation` can project current situation, next action, optional
  reason, and available progress for console visibility. It is never parsed
  for Workflow control.
- Start/resume rejects unknown schema/version, incompatible typed payload,
  incorrect identity, stale revision/snapshot, missing required evidence, and
  duplicate/incompatible result according to the durable Continuation contract.

Phase 77 defers broad Start/API expansion beyond this typed entry/result,
rich Presentation/UI, additional reasoning vocabulary, parent/child Workflow
composition, orchestration, and REST/MCP/UI protocol surfaces to later phases.

## WorkflowInstance Persistence Boundary

`WorkflowInstance` is a separately durable process record with stable instance identity, definition identity/version, revision, lifecycle state, current progression/suspension, append-only history, and causal correlation. Phase 77 defines its provider-neutral persistence SPI and exercises that contract for the accepted vertical slice.

Entity StateMachine persistence remains authoritative only for entity state. Workflow progression never uses an entity StateMachine status as its process checkpoint. A physical database may host both only through separate logical store/schema ownership and explicitly configured transaction integration.

When a committed entity transition creates or resumes a WorkflowInstance, the committed-transition occurrence is the durable correlation/idempotency key. External SPI resume similarly uses Continuation identity plus expected revision/snapshot so replay cannot duplicate completion or silently execute a stale result.

## UnitOfWork Execution Boundary

Phase 64 semantic contracts select composite/workflow Actions without performing effects. Every admitted executable Action in the Phase 77 vertical slice is represented as `ExecProgram[UnitOfWorkOp, ActionExecution]` and interpreted through the Phase 64.2 UnitOfWork boundary. Direct `ResolvedAction.run(...): Consequence[Unit]` or `Effect.execute` paths are legacy compatibility only and must not cause an accepted Action to execute twice.

Suspension records WorkflowInstance progression and the Continuation atomically in the active UnitOfWork. A Continuation SPI request becomes externally claimable only after commit. `resume` begins a fresh UnitOfWork, validates identity, expected revision and ContextSnapshot, records its typed result idempotently, and atomically commits resulting state, history, and events.

## StateMachine API/SPI Runtime Contract

A generated Required SPI operation carries stable operation identity, typed input/result, and generic Context/Completion/Evidence/capability requirements admitted from Cozy's ABI.

Provider placement is resolved outside StateMachine semantics:

```text
Required SPI
  -> LocalProvider
  -> ExternalContinuationProvider
  -> DeterministicTestProvider
```

A local/test provider may return `Completed(Result)` through the canonical program. An external provider produces `Suspended(Continuation)` without performing external work before the program is interpreted. Provider placement does not duplicate State/Guard/Operation/Result semantics.

### Component implementation / Provider construction

The component-programmer-facing implementation boundary is Provider construction through `ComponentFactory`.

A component does not implement a Workflow/StateMachine Action by overriding one factory method per Action. Generated/admitted Workflow metadata identifies Required SPI operations; the component supplies their implementation by overriding generated/standard Provider factory methods on its `ComponentFactory` and returning component-specific Provider implementations.

Conceptually:

```text
CML Workflow / StateMachine
  -> Action
  -> Required SPI
  -> runtime ProviderBinding
  -> ComponentFactory provider factory method
  -> component-specific Provider
  -> typed Required SPI implementation
```

The Provider may implement a coherent group of related Required SPI operations. Runtime binding remains operation/SPI-specific, so test or external Providers can replace selected bindings without changing Workflow semantics.

`ComponentFactory` owns Provider construction and dependency injection; the Provider owns Required SPI implementation; the StateMachine/Workflow runtime owns provider resolution, Action execution and progression. Generated CML metadata must not embed arbitrary Scala functions or Provider instances.

`advance` executes bounded internal progress: it selects the next admitted Action, executes internal providers, feeds Completed results back into the StateMachine, and continues until a suspension, terminal state, declared wait/failure boundary, ambiguity/cycle/safety bound, or other explicit policy stop is reached.

`resume` validates Continuation identity, WorkflowInstance revision, ContextSnapshot, typed result, Completion/Evidence contract and duplicate/stale conditions before completing the suspended Action and returning its result to StateMachine transition semantics.

## Generic Skill Workflow Boundary

CNCF projects an externally claimable Continuation SPI request into a compact
Skill-facing `WorkOrder` within the closed `Continuation` protocol. The
projection is not the source of truth; Required SPI, the durable Continuation,
and WorkflowInstance state are canonical.

The Skill layer exposes work kind, `ExecutionRequirement`, typed
input/result, Completion/Evidence, and `MinimalPresentation`. It maps the
initial abstract `ReasoningLevel` vocabulary to a concrete worker profile;
concrete model/provider/reasoning selection remains host dispatch policy.

Control-plane operations such as advance/status/submit do not require a child AI invocation. Internal build/test/git-style Actions are not emitted as Skill WorkOrders merely because a Skill drives the overall Workflow.

## Reference vertical slice

```text
WorkflowStartRequest[BuildProjectInput]
  -> bounded deterministic start/advance
       BuildProject  -> internal provider -> Completed
       RunTests      -> internal provider -> Completed
       ReviewChange  -> external SPI -> Suspended(WORK_ORDER Continuation)
  -> WorkflowStartResult[ReviewWorkOrder, CommitOutcome]
       WorkflowHandle + first Continuation
ReviewResult  -> ContinuationResult -> fresh-UnitOfWork resume
CommitChanges -> internal provider -> Completed
TERMINAL[CommitOutcome]
```

For an entity-triggered instance, initial correlation originates only in the Phase 63.2 `CommittedTransition` through Phase 64's binding. This is the primary Phase 77 acceptance path and the handoff consumed by `sm-workflow`.

## Completion Conditions

- CNCF admits a real Cozy-generated StateMachine/Workflow ABI by supported version and fails closed for incompatible required semantics.
- ComponentFactory discovers generated definitions/API/SPI metadata without CML reparsing.
- StateMachine Provided API and Required SPI are runtime concepts reusable by Workflow without a parallel Workflow-specific interface model.
- Internal Actions progress through `Completed(Result)` without Skill/model invocation.
- External Review SPI suspends durably and returns a typed Continuation.
- Typed ReviewResult resumes the same suspended Action only when identity/revision/snapshot/contracts match.
- Resume then permits internal closing/commit Actions to execute and reach terminal state.
- Deterministic test provider binding can exercise the same StateMachine semantics without an actual AI/UI provider.
- No Workflow-wide orchestration/continuation mode or InvocationBinding switch is required.
- Every admitted executable Action in the reference path is interpreted through `ExecProgram[UnitOfWorkOp, ActionExecution]`; no direct callback/effect execution remains in the canonical path.
- Suspension is durable before external claim, and resume runs in a fresh UnitOfWork with stale and duplicate rejection.
- The minimum typed Start/Continuation/WorkOrder/Terminal protocol and its schema-versioned, fail-closed Skill/Codex JSON encoding are executable across a separate Skill process/turn without making JSON the canonical domain model.
- A WorkOrder carries the initial abstract `ROUTINE` / `STANDARD` / `DEEP` / `CRITICAL` reasoning requirement, and `MinimalPresentation` is available for console visibility without controlling progression.
- Broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child Workflow composition, orchestration, and REST/MCP/UI protocol surfaces are later-phase work.
- Cross-repository evidence records exact Cozy source, generated ABI, CNCF revisions, and the `sm-workflow` consumer handoff.

## Phase Boundary to Phase 80

Phase 77 is the minimum complete runtime foundation required for `sm-workflow` to begin its Phase 1 executable-specification work. Closing Phase 77 must therefore produce a stable consumer handoff; `sm-workflow` does not wait for Phase 80.

Phase 80 and later phases may extend the minimum typed Workflow protocol/JSON encoding with broad Start/API expansion, rich presentation/context ergonomics, additional reasoning vocabulary, parent/child composition, participant integration, and orchestration or REST/MCP/UI protocol surfaces, but must not redefine the StateMachine API/SPI, ActionExecution, durable Continuation/resume, deterministic progression, minimum typed protocol, Generic Skill projection, or consumer handoff established here.

In particular, Phase 77 requires no Workflow-wide Orchestration/Continuation mode and no semantic `InvocationBinding` switch. Later extensions must remain compatible with this foundation.

## Non-Goals

- Parsing CML or independently reconstructing Workflow semantics in CNCF.
- A second Workflow DSL, BPMN/DAG language, arbitrary scripts, or opaque callbacks.
- Default SQLite/datastore provider, consumer-owned migration/retention, or `sm-workflow` public CLI implementation.
- Concrete AI model selection/dispatch policy.
- Broad Start/API expansion beyond the minimum typed Start request/result, rich Presentation/UI, additional reasoning vocabulary, parent/child Workflow composition, orchestration, and REST/MCP/UI protocol extensions.
- Full assemble API/SPI binding syntax/runtime.
- Workflow-to-Workflow generated proxy, Local/REST Workflow connector, service discovery or deployment binding.
- UI Workflow/Flutter generation or client offline synchronization.

## References

Current design:

- [Phase 64](phase-64.md)
- [Phase 64.2](phase-64.2.md)
- [Phase 77 Checklist](phase-77-checklist.md)
- `docs/notes/statemachine-api-spi-runtime.md`
- `docs/notes/workflow-spi-runtime.md`
- `docs/notes/generic-skill-workflow-support.md`
- `asami/cozy/docs/phase/phase-62.md`
- `asami/cozy/docs/phase/phase-62.1.md`
- `asami/cozy/docs/phase/phase-62.2.md`
- `asami/cozy/docs/phase/phase-62.3.md`
- [Minimum Skill Continuation JSON Contract](../journal/2026/09/2026-09-20-phase-77-minimum-skill-continuation-json-contract.md)
- [Phase 64/77 sm-workflow Critical-Path Review Handoff](../journal/2026/09/2026-09-20-phase-64-77-sm-workflow-critical-path-review-handoff.md)
- [Phase 64/77 Critical-Path Reconciliation](../journal/2026/09/2026-09-20-phase-64-77-critical-path-reconciliation.md)

Historical protocol/binding addenda and journals remain as design history. Where they conflict with this consolidated Phase 77, this document and the StateMachine API/SPI runtime foundation are normative.
