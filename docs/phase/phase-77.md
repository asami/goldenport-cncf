# Phase 77 - StateMachine API/SPI Runtime and First Skill-Driven Workflow Vertical Slice

status=planned
planned_at=2026-09-16
revised_at=2026-09-19
depends_on=[Phase 64](phase-64.md), [Phase 64.2](phase-64.2.md), and Cozy Phase 62.3 producer handoff
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-statemachine-api-spi-runtime-and-skill-driven-workflow)
checklist=[Phase 77 Checklist](phase-77-checklist.md)

## Purpose

Execute the first vertical slice on top of Phase 64's Composite
StateMachine/Workflow contract and Phase 64.2's canonical `ExecProgram`
contract. Phase 77 admits Cozy's generated CML `WORKFLOW`/StateMachine ABI
through CNCF without reparsing CML or creating a second Workflow language, then
implements the reusable StateMachine API/SPI execution foundation consumed by
Workflow and future StateMachine-based facilities.

For an entity-triggered Workflow, the only lifecycle input is the successful
Phase 63.2 `CommittedTransition` handoff. Phase 77 neither observes attempted
or rolled-back local transitions nor adds a competing Operation/event trigger
route.

The first vertical slice is Skill-driven Workflow execution: internal deterministic Actions complete in the runtime, an external semantic SPI Action suspends as a durable Continuation, a typed Skill result resumes the StateMachine, and internal closing Actions complete normally.

```text
CML WORKFLOW
  -> Cozy generated StateMachine/Workflow ABI
  -> Phase 64 Composite/Workflow semantic contract
  -> Phase 64.2 ExecProgram[UnitOfWorkOp, A] contract
  -> Phase 77 ABI admission + ComponentFactory bootstrap
  -> StateMachine API/SPI Runtime
       ^
       | entity-triggered start only: Phase 63.2 CommittedTransition
       | through the Phase 64 binding/derivation contract
       -> selected Action / Required SPI Provider
       -> ExecProgram[ActionExecution]
            = Program[UnitOfWorkOp, ActionExecution]
       -> internal provider -> Completed(Result)
       -> external provider -> Suspended(Continuation)
       -> failed provider   -> Failed(Error)
  -> Workflow projection / component-owned durable runtime
```

## Canonical Runtime Model

```text
StateMachine Runtime
  Provided API dispatch
  Required SPI provider resolution
  State / Action / Transition
  ActionExecution = Completed | Suspended | Failed
  durable Continuation / typed resume
        ^
        |
Workflow Runtime
  process-oriented projection and metadata
```

There is no Workflow-wide Orchestration/Continuation mode and no semantic `InvocationBinding = ORCHESTRATION | CONTINUATION` switch. Continuation is the durable suspension outcome of an Action/provider execution that requires an external result.

## Ownership Boundary

- Cozy Phase 62 owns CML `WORKFLOW` source and StateMachine lowering.
- Cozy Phase 62.1 owns the generic StateMachine Provided API / Required SPI, ActionExecution, Continuation, Context, Completion, and Evidence contracts.
- Cozy Phase 62.2 owns deterministic generated ABI and ComponentFactory bootstrap metadata.
- Cozy Phase 62.3 owns the producer fixture, compatibility evidence, aggregate validation, and CNCF consumer handoff.
- CNCF Phase 63.2 owns the local StateMachine post-commit
  `CommittedTransition` source contract. It does not start or execute
  Workflows.
- CNCF Phase 64 owns Composite/Workflow semantic identity, pure composite
  derivation, action provenance/causal order, and the independent
  `WorkflowInstance` persistence SPI contract. It does not admit generated
  API/SPI artifacts or execute Providers/Continuations.
- CNCF Phase 64.2 owns the one canonical `ExecProgram[UnitOfWorkOp, A]`
  compiler/planner/interpreter contract. It does not introduce a separate
  Workflow Action algebra.
- CNCF owns ABI admission, ComponentFactory discovery, Provided API dispatch, Required SPI provider resolution, Action execution, Continuation/resume runtime contracts, provider admission, and deterministic progression.
- CNCF executes every admitted StateMachine/Workflow Action through the canonical `ExecProgram[A] = Program[UnitOfWorkOp, A]` algebra. It does not retain a second callback-style Action execution path for the accepted runtime.
- CNCF does not change Cozy's frozen transport-neutral `StateMachineProvider.execute(...): ActionExecution` ABI. CNCF's binding adapter admits that result into the UnitOfWork program under the restrictions below; effectful CNCF Providers use the program-producing Provider SPI.
- Workflow reuses/projects the StateMachine foundation; CNCF does not add a parallel Workflow-specific API/SPI or Continuation engine, nor does it redefine Phase 64 Composite/Workflow semantics.
- An entity-local StateMachine owns only lifecycle data persisted with that entity. WorkflowInstance process persistence remains independently owned.
- Component implementation code supplies Required SPI implementations through Providers. `ComponentFactory` constructs and injects component-specific Providers rather than exposing one Action override per Action.
- Generic Skill Workflow Support projects suspended external SPI operations through the Continuation SPI into Skill commands/WorkOrders. Domain-specific software-development policy remains in `sm-workflow`.

## Terminology and Protocol Boundary

- **Provided API** is the caller-facing StateMachine surface, including bounded `advance`, typed `resume`, and state/status access.
- **Required SPI** is the protocol-independent typed operation contract required by a StateMachine Action.
- **Provider SPI** is the component-programmer implementation surface used to satisfy Required SPI operations.
- **Continuation Protocol** is the durable external execution protocol entered when a Provider returns `Suspended(Continuation)`.
- **Continuation SPI Projection** is the external IoC port that projects one suspended Required SPI operation as a `ContinuationRequest` and accepts its typed `ContinuationResult`.

```text
Continuation SPI Projection
  = Required SPI contract
  + Continuation Protocol representation
```

Participant and capability metadata may constrain Provider selection, but neither is a semantic protocol-mode switch. Runtime/provider binding selects the implementation; `ActionExecution` reports whether that invocation completed, suspended, or failed.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-01 | Freeze supported Cozy 62.1-62.3 StateMachine/Workflow ABI versions, admission diagnostics, and compatibility policy. | planned |
| CWF-77-02 | Discover generated definitions and StateMachine API/SPI metadata through ComponentFactory while preserving source identity. | planned |
| CWF-77-03 | Bind admitted Workflow definitions to Phase 64's separate WorkflowInstance persistence SPI and identity/revision/history contract. | planned |
| CWF-77-04 | Lower and interpret every admitted executable Action through `ExecProgram[A] = Program[UnitOfWorkOp, A]`, with deterministic `Completed`, `Suspended`, and `Failed` progression. | planned |
| CWF-77-05 | Implement Required SPI provider resolution and ComponentFactory Provider construction without permitting direct side-effect execution outside the canonical UnitOfWork program. | planned |
| CWF-77-06 | Atomically persist transition/progression and suspension through UnitOfWork, then implement post-commit Continuation SPI claim and fresh-UnitOfWork resume. | planned |
| CWF-77-07 | Provide Generic Skill Workflow projection over the Continuation SPI without exposing internal deterministic Actions as WorkOrders. | planned |
| CWF-77-08 | Prove the real internal -> suspended external -> resumed -> internal reference vertical slice. | planned |
| CWF-77-09 | Freeze CML-first cross-repository evidence and the `sm-workflow` consumer handoff. | planned |

## WorkflowInstance Persistence Boundary

`WorkflowInstance` is the separately durable process record defined by the
Phase 64 persistence SPI: stable instance identity, definition identity/version,
revision, lifecycle state, current progression/suspension, append-only history,
and causal correlation to the entity transition, Operation, Job, or external
SPI completion that admitted it. Phase 77 binds and exercises that SPI; it does
not redefine its semantic ownership.

Entity StateMachine persistence remains authoritative only for entity state. Workflow progression never uses an entity StateMachine status as its process checkpoint. A physical database may host both only through separate logical store/schema ownership and explicitly configured transaction integration.

When a committed entity transition creates or resumes a WorkflowInstance, the
Phase 63.2 committed-transition occurrence, passed through Phase 64's binding,
is the durable correlation/idempotency key. External SPI resume similarly uses
Continuation identity plus expected revision/snapshot so replay cannot duplicate
completion or silently execute a stale result.

Suspension must be durably recorded before its Continuation SPI projection becomes externally claimable. CNCF does not invoke a Skill, AI, Human, UI, or remote worker inside the persistence transaction.

## UnitOfWork Execution Boundary

Phase 64 semantic contracts select composite/workflow Actions without
performing effects. Once an admitted Action is selected, its executable
calculation is represented
by the existing CNCF execution algebra:

```text
pure State/Guard/Transition decision
  -> selected Action
  -> ExecProgram[A] = Program[UnitOfWorkOp, A]
  -> UnitOfWork planning / interpreter
  -> ActionExecution = Completed | Suspended | Failed
```

Pure selection, predicate evaluation, and construction of the program remain
pure computations. State mutation, Action effects, events, WorkflowInstance
progression, and Continuation suspension records execute only through the
UnitOfWork program and its interpreter.

CNCF Provider binding resolves each Required SPI invocation to a typed
`ExecProgram[ActionExecution]`. A program-native CNCF Provider supplies that
program directly. A Cozy-generated Provider whose frozen ABI returns
`ActionExecution` directly may be lifted only when it is a pure deterministic
calculation or produces a suspension descriptor without performing the
external work. It does not justify bypassing the program boundary.

An effectful Provider must express datastore, process, HTTP, message,
filesystem, and other externally visible work as `UnitOfWorkOp` values. It
must not perform those effects before returning its program. If admission of a
frozen provider invocation needs a new generic UnitOfWork operation, Phase 77
must justify and add it through Phase 64.2's algebra-gap rule rather than call
the Provider directly or change the Cozy ABI.

The existing callback path based on
`ResolvedAction.run(...): Consequence[Unit]` and direct `Effect.execute` is a
legacy compatibility path, not the accepted Phase 77 runtime. The reference
vertical slice must either adapt such Actions into `ExecProgram` without
double execution or retire the direct path. No accepted Action may be executed
once directly and again by the UnitOfWork interpreter.

For `Suspended(Continuation)`, the current StateMachine/WorkflowInstance
progression and Continuation record commit atomically in the active UnitOfWork.
Only an after-commit step may expose the corresponding Continuation SPI request
for external claim. External Skill/Human/UI/remote work never runs inside that
UnitOfWork.

`resume` begins a new UnitOfWork. It validates Continuation identity, expected
revision and ContextSnapshot, records the typed result idempotently, completes
the suspended Action, advances the machine, and commits the resulting state,
history, and events atomically. A failed or rolled-back resume exposes no
successful transition or completed Continuation.

## StateMachine API/SPI Provider Runtime

A generated Required SPI operation carries stable operation identity, typed input/result, and generic Context/Completion/Evidence/capability requirements admitted from Cozy's ABI.

```text
Required SPI
  -> LocalProvider
  -> ExternalContinuationProvider
  -> DeterministicTestProvider
```

A local/test provider may produce a program that returns `Completed(Result)`. A provider needing a durable external result produces a program, or an admitted pure generated-ABI result, that returns `Suspended(Continuation)`. Provider placement does not duplicate State/Guard/Operation/Result semantics or bypass UnitOfWork interpretation.

`ComponentFactory` owns Provider construction and dependency injection; the Provider owns Required SPI implementation; the StateMachine/Workflow runtime owns provider resolution, Action-program interpretation and progression. Generated CML metadata must not embed arbitrary Scala functions or Provider instances.

`advance` executes bounded internal progress until suspension, terminal state, a declared wait/failure boundary, ambiguity/cycle/safety bound, or another explicit policy stop. `resume` validates Continuation identity, WorkflowInstance revision, ContextSnapshot, typed result, Completion/Evidence contract, and duplicate/stale conditions before completing the suspended Action.

## Continuation SPI IoC Boundary

The Workflow/StateMachine runtime never depends directly on Skill-specific APIs. A host binds a generic Continuation SPI adapter through ComponentFactory/provider construction:

```text
Workflow / StateMachine
  -> Suspended(ContinuationRequest)
  -> durable Continuation SPI port
  -> injected Skill/Human/UI/remote adapter
  -> ContinuationResult
  -> resume
```

The port preserves continuation/run identity, Required SPI operation and typed input/result, expected revision and ContextSnapshot, Completion/Evidence requirements, capability constraints, and idempotent result correlation. Delivery, claim/lease, model selection, and host scheduling remain outside StateMachine semantics.

## Generic Skill Workflow Boundary

CNCF projects an externally claimable Continuation SPI request into a compact Skill-facing command/WorkOrder. The projection is not the source of truth; Required SPI, the durable Continuation, and the persisted WorkflowInstance are canonical.

The Skill layer may expose work kind, required capability, complexity/risk and completion/evidence information, but concrete model/provider/reasoning selection belongs to host dispatch policy. Control-plane operations such as advance/status/submit do not require a child AI invocation. Internal build/test/git-style Actions are not emitted as Skill WorkOrders merely because a Skill drives the overall Workflow.

## Reference Vertical Slice

```text
BuildProject  -> internal provider -> Completed
RunTests      -> internal provider -> Completed
ReviewChange  -> Continuation SPI -> Suspended(Continuation)
ReviewResult  -> resume -> StateMachine transition
CommitChanges -> internal provider -> Completed
Terminal
```

For an entity-triggered instance, its initial correlation originates only in a
Phase 63.2 `CommittedTransition` through Phase 64's binding. This is the
primary Phase 77 acceptance path and the handoff consumed by `sm-workflow`.

## Completion Conditions

- CNCF admits the real Cozy Phase 62.2/62.3 generated ABI and handoff by supported version and fails closed for incompatible required semantics.
- Phase 77 consumes rather than redefines the Phase 64 Composite/Workflow
  contract and `WorkflowInstance` SPI, and consumes a Phase 63.2
  `CommittedTransition` as the sole entity-triggered lifecycle origin.
- ComponentFactory discovers generated definitions/API/SPI metadata without CML reparsing.
- Required SPI, Provider SPI, Continuation Protocol, and Continuation SPI Projection are distinguishable in the public contract.
- Every admitted executable Action in the reference path is represented and interpreted as `ExecProgram[A] = Program[UnitOfWorkOp, A]`; direct callback/effect execution is absent from the canonical path.
- Internal Actions progress through a UnitOfWork program to `Completed(Result)` without Skill/model invocation.
- External Review SPI commits WorkflowInstance progression and its suspension record atomically before an after-commit step makes the Continuation externally claimable.
- IoC-injected deterministic and Skill adapters consume the same Continuation SPI without changing Workflow semantics.
- Typed ReviewResult resumes the same suspended Action in a fresh UnitOfWork only when identity/revision/snapshot/contracts match.
- Resume permits internal closing/commit Actions to execute and reach terminal state.
- UnitOfWork rollback exposes neither a successful transition, a completed Continuation, nor externally claimable uncommitted work.
- No Workflow-wide orchestration/continuation mode or InvocationBinding switch is required.
- Cross-repository evidence records exact Cozy 62.1-62.3 source, generated ABI, CNCF revisions, and the `sm-workflow` consumer handoff.

## Non-Goals

- Parsing CML or independently reconstructing Workflow semantics in CNCF.
- A second Workflow DSL, BPMN/DAG language, arbitrary scripts, or opaque callbacks.
- Default SQLite/datastore provider, consumer-owned migration/retention, or `sm-workflow` public CLI implementation.
- Concrete AI model selection/dispatch policy.
- A second StateMachine-specific executable algebra or a canonical direct `Consequence` callback execution path alongside `UnitOfWorkOp`.
- Reopening Phase 63 local transition/commit semantics, reinterpreting raw
  Operation/event input as a Workflow trigger, or moving Composite/Workflow
  semantic derivation and `WorkflowInstance` ownership out of Phase 64.
- Full assemble API/SPI binding syntax/runtime.
- Workflow-to-Workflow generated proxy, Local/REST Workflow connector, service discovery or deployment binding.
- UI Workflow/Flutter generation or client offline synchronization.

## References

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

Historical protocol/binding addenda and journals remain design history. Where they conflict with this Phase, the accepted Cozy StateMachine API/SPI contracts and this Phase's Continuation SPI IoC boundary are normative.
