# Phase 77 - StateMachine API/SPI Runtime and First Skill-Driven Workflow Vertical Slice

status=planned
planned_at=2026-09-16
depends_on=[Phase 64](phase-64.md), [Phase 64.2](phase-64.2.md), and Cozy Phase 62 producer handoff
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-first-class-cml-workflow-admission-and-progression)
checklist=[Phase 77 Checklist](phase-77-checklist.md)

## Purpose

Admit Cozy's first-class generated CML `WORKFLOW`/StateMachine ABI through CNCF without reparsing CML or creating a second Workflow language, and implement the reusable StateMachine API/SPI execution foundation consumed by Workflow and future StateMachine-based facilities.

The first vertical slice is Skill-driven Workflow execution: internal deterministic Actions complete in the runtime, an external semantic SPI Action suspends as a durable Continuation, a typed Skill result resumes the StateMachine, and internal closing Actions complete normally.

```text
CML WORKFLOW
  -> Cozy generated StateMachine/Workflow ABI
  -> CNCF ABI admission + ComponentFactory bootstrap
  -> StateMachine API/SPI Runtime
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

- Cozy Phase 62 owns CML syntax, StateMachine/Workflow normalization, validation, source identity, generated API/SPI and ActionExecution ABI, and producer fixtures.
- CNCF owns ABI version admission, ComponentFactory discovery, StateMachine Provided API dispatch, Required SPI provider resolution, Action execution, Continuation/resume runtime contracts, provider admission, and deterministic progression.
- Workflow reuses/projects the StateMachine foundation; CNCF does not add a parallel Workflow-specific API/SPI or Continuation engine.
- An entity-local StateMachine owns only lifecycle data persisted with that entity. WorkflowInstance process persistence remains independently owned.
- A consuming component binds WorkflowInstance persistence, retention, lease/idempotency policy, public operations and client behavior.
- Component implementation code supplies Required SPI implementations through Providers. `ComponentFactory` is the construction/injection boundary: the component overrides generated/standard Provider factory methods to create component-specific Providers rather than overriding individual Workflow/StateMachine Actions.
- Generic Skill Workflow Support projects suspended external SPI operations into Skill commands/WorkOrders. Domain-specific software-development policy remains in `sm-workflow`.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-01 | Freeze supported Cozy StateMachine/Workflow ABI versions, admission diagnostics, and compatibility policy. | planned |
| CWF-77-02 | Discover generated definitions and StateMachine API/SPI metadata through ComponentFactory while preserving source identity. | planned |
| CWF-77-03 | Bind admitted Workflow definitions to Phase 64's separate WorkflowInstance persistence SPI and identity/revision/history contract. | planned |
| CWF-77-04 | Implement deterministic StateMachine progression and bounded next-Action selection without inferring semantics from names/effects. | planned |
| CWF-77-05 | Implement ActionExecution handling for `Completed`, `Suspended`, and `Failed`, reusing the existing typed `ExecProgram[UnitOfWorkOp, A]` path for internal Actions. | planned |
| CWF-77-06 | Implement Required SPI provider resolution contracts and the ComponentFactory Provider-construction developer API for local/direct, external-continuation, and deterministic test providers. | planned |
| CWF-77-07 | Implement durable Continuation creation/resume validation including identity, revision/ContextSnapshot, typed result, completion/evidence, stale and duplicate rejection boundaries. | planned |
| CWF-77-08 | Provide Generic Skill Workflow projection for suspended external SPI operations without exposing internal deterministic Actions as WorkOrders. | planned |
| CWF-77-09 | Prove the CML-first producer-to-CNCF path with Cozy's real fixture and freeze the `sm-workflow` consumer handoff. | planned |

## WorkflowInstance Persistence Boundary

`WorkflowInstance` is a separately durable process record with stable instance identity, definition identity/version, revision, lifecycle state, current progression/suspension, append-only history, and causal correlation to the entity transition, Operation, Job, or external SPI completion that admitted it.

Entity StateMachine persistence remains authoritative only for entity state. Workflow progression never uses an entity StateMachine status as its process checkpoint. A physical database may host both only through separate logical store/schema ownership and explicitly configured transaction integration.

When a committed entity transition creates or resumes a WorkflowInstance, the committed-transition occurrence is the durable correlation/idempotency key. External SPI resume similarly uses Continuation identity plus expected revision/snapshot so replay cannot duplicate completion or silently execute a stale result.

## StateMachine API/SPI Runtime Contract

A generated Required SPI operation carries stable operation identity, typed input/result, and generic Context/Completion/Evidence/capability requirements admitted from Cozy's ABI.

Provider placement is resolved outside StateMachine semantics:

```text
Required SPI
  -> LocalProvider
  -> ExternalContinuationProvider
  -> DeterministicTestProvider
```

A local/test provider may return `Completed(Result)`. An external provider produces `Suspended(Continuation)` until a typed result is submitted. Provider placement does not duplicate State/Guard/Operation/Result semantics.

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

CNCF projects an external suspended SPI operation into a compact Skill-facing command/WorkOrder. The projection is not the source of truth; StateMachine SPI plus Continuation are canonical.

The Skill layer may expose work kind, required capability, complexity/risk and completion/evidence information, but concrete model/provider/reasoning selection belongs to host dispatch policy.

Control-plane operations such as advance/status/submit do not require a child AI invocation. Internal build/test/git-style Actions are not emitted as Skill WorkOrders merely because a Skill drives the overall Workflow.

## Reference vertical slice

```text
BuildProject  -> internal provider -> Completed
RunTests      -> internal provider -> Completed
ReviewChange  -> external SPI -> Suspended(Continuation)
ReviewResult  -> resume -> StateMachine transition
CommitChanges -> internal provider -> Completed
Terminal
```

This is the primary Phase 77 acceptance path and the handoff consumed by `sm-workflow`.

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
- Cross-repository evidence records exact Cozy source, generated ABI, CNCF revisions, and the `sm-workflow` consumer handoff.

## Non-Goals

- Parsing CML or independently reconstructing Workflow semantics in CNCF.
- A second Workflow DSL, BPMN/DAG language, arbitrary scripts, or opaque callbacks.
- Default SQLite/datastore provider, consumer-owned migration/retention, or `sm-workflow` public CLI implementation.
- Concrete AI model selection/dispatch policy.
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

Historical protocol/binding addenda and journals remain as design history. Where they conflict with this consolidated Phase 77, this document and the StateMachine API/SPI runtime foundation are normative.
