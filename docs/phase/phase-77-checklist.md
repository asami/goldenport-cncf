# Phase 77 Checklist - StateMachine API/SPI Runtime and Skill-Driven Workflow Vertical Slice

status=planned
phase=[Phase 77](phase-77.md)

Historical protocol/binding addenda remain design history; closure does not require a Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` switch.

## CWF-77-01: Generated ABI Admission

Stage Status:
- Current status: OPEN
- Owner: CNCF generated-contract/runtime owner
- Update rule: Close only after the supported Cozy 62.1-62.3 ABI, compatibility policy, and structured fail-closed diagnostics are frozen and executable.

- [ ] Inventory Cozy Phase 62.1 API/SPI and ActionExecution schemas, Phase 62.2 generated ABI/bootstrap metadata, and Phase 62.3 fixture/handoff evidence.
- [ ] Define supported ABI version admission and incompatible/unknown required-semantics failure behavior.
- [ ] Preserve Workflow, StateMachine/Composite StateMachine, State/Action/Operation provenance, Provided API and Required SPI identity without CML parsing or name inference.
- [ ] For entity-triggered Workflow, accept only the Phase 63.2
  `CommittedTransition` origin through the Phase 64 binding/derivation
  contract; do not observe attempted/rolled-back transitions or add a raw
  Operation/event trigger route.
- [ ] Admit ActionExecution/Continuation/Context/Completion/Evidence schemas required by the reference vertical slice.

## CWF-77-02: ComponentFactory Discovery

Stage Status:
- Current status: OPEN
- Owner: CNCF ComponentFactory owner
- Update rule: Close only after ComponentFactory discovers admitted generated definitions and API/SPI metadata with explicit version/source diagnostics.

- [ ] Bind generated StateMachine/Workflow bootstrap metadata to ComponentFactory.
- [ ] Discover Provided API / Required SPI metadata from generated artifacts.
- [ ] Reject absent, duplicate, or incompatible generated definitions rather than accepting handwritten canonical runtime definitions.
- [ ] Preserve normal component capability/admission boundaries.

## CWF-77-03: Independent WorkflowInstance Persistence

Stage Status:
- Current status: OPEN
- Owner: CNCF Workflow persistence/runtime owner
- Update rule: Close only after the admitted Workflow is bound to Phase 64's independent WorkflowInstance store contract and external suspension/resume can be correlated durably.

- [ ] Consume/version-bind Phase 64's WorkflowInstance persistence SPI with independent instance/definition identity, revision, lifecycle/progression state, append-only history, and correlation/causation references.
- [ ] Consume rather than redefine Phase 64 Composite/Workflow semantics,
  action provenance/causal order, and WorkflowInstance ownership.
- [ ] Prohibit entity fields, entity StateMachine records, and shared-table ownership from becoming the authoritative WorkflowInstance store.
- [ ] Define explicitly configured same-store transaction behavior and idempotent/recoverable cross-store delivery from committed entity transitions.
- [ ] Persist suspension identity, expected revision/ContextSnapshot, completion/evidence requirements, and resume correlation without requiring a default CNCF datastore provider.

## CWF-77-04: StateMachine Progression and ActionExecution

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after every admitted executable Action is interpreted through `ExecProgram[A] = Program[UnitOfWorkOp, A]` and bounded progression handles `Completed`, `Suspended`, `Failed`, terminal and structured policy stops deterministically.

- [ ] Select the next Action only from admitted semantics; do not infer from names/documentation/effect labels.
- [ ] Preserve Phase 64 composite/workflow action semantics and Phase 64.2's
  single `ExecProgram[UnitOfWorkOp, A]` contract; do not introduce an Action
  algebra or semantic transition selector locally.
- [ ] Keep State/Guard/Transition selection pure, then lower every selected executable Action to `ExecProgram[A] = Program[UnitOfWorkOp, A]`.
- [ ] Interpret internal Action programs through UnitOfWork and feed `Completed(Result)` back into StateMachine transition semantics.
- [ ] Return `Suspended(Continuation)` from the interpreted program when a Provider requires a durable external result.
- [ ] Propagate `Failed(Error)` without silently crossing boundaries.
- [ ] Detect ambiguity, cycle/bound overflow, unavailable input, stale state and unsupported execution as structured failures.
- [ ] Remove or adapt the canonical runtime's direct `ResolvedAction.run(...): Consequence[Unit]` / `Effect.execute` path; prove the same Action cannot execute both directly and through UnitOfWork.

## CWF-77-05: StateMachine Provider SPI Runtime

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / UnitOfWork runtime owners
- Update rule: Close only after Provided API dispatch and Required SPI provider resolution use the canonical UnitOfWork execution infrastructure without a direct side-effect escape path.

- [ ] Dispatch admitted Provided API operations through typed runtime contracts.
- [ ] Resolve Required SPI operations through Provider bindings independent from Action implementation.
- [ ] Define Provider SPI construction hooks on ComponentFactory for component-specific Providers.
- [ ] Keep Action methods out of the ComponentFactory override surface.
- [ ] Support local/direct, external-continuation, and deterministic test Providers.
- [ ] Resolve every Provider invocation to a typed `ExecProgram[ActionExecution]` interpreted by the Phase 64.2 UnitOfWork planner/interpreter.
- [ ] Preserve Cozy's frozen `StateMachineProvider.execute(...): ActionExecution` ABI; admit a direct result only when the Provider is pure/deterministic or returns suspension without performing the external work.
- [ ] Require effectful CNCF Providers to use the program-producing Provider SPI and prohibit datastore, process, HTTP, message, filesystem, or other externally visible effects before they return their program.
- [ ] If no existing operation can admit a frozen Provider invocation, justify one generic `UnitOfWorkOp` primitive under Phase 64.2's algebra-gap rule; do not bypass UnitOfWork or change the Cozy ABI.
- [ ] Preserve identity, idempotency, authorization, provenance and UnitOfWork boundaries.

## CWF-77-06: Durable Continuation and Continuation SPI IoC

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after suspension commits atomically with runtime progression, external claim begins only after commit, resume runs in a fresh UnitOfWork, and an IoC-injected Continuation SPI adapter can return a valid typed result.

- [ ] Distinguish Required SPI, Provider SPI, Continuation Protocol, and Continuation SPI Projection.
- [ ] Create a durable Continuation carrying run, Action/SPI, revision/ContextSnapshot, result, Completion/Evidence and context-reference contracts.
- [ ] Persist StateMachine/WorkflowInstance progression and suspension atomically in the active UnitOfWork before making its Continuation SPI request externally claimable.
- [ ] For an entity-triggered Workflow, preserve the originating Phase 63.2
  `CommittedTransition` occurrence and Phase 64 correlation through suspension
  and resume.
- [ ] Publish or expose claimable Continuation work only from an after-commit boundary; rollback must expose none.
- [ ] Define generic ContinuationRequest/ContinuationResult independent of Skill, AI, Human, UI, and remote-worker types.
- [ ] Inject adapters through ComponentFactory/provider construction rather than direct Workflow-to-Skill calls.
- [ ] Start a fresh UnitOfWork for resume; validate result, identity, revision/snapshot and evidence before completing the suspended Action and committing subsequent progression/events.
- [ ] Reject stale, duplicate, expired/invalid or incompatible resume attempts fail closed.
- [ ] Prove restart/recovery neither loses the boundary nor reissues completed external work.
- [ ] Prove failed/rolled-back resume does not expose a successful transition or completed Continuation.

## CWF-77-07: Generic Skill Workflow Projection

Stage Status:
- Current status: OPEN
- Owner: CNCF Generic Skill Workflow Support owner
- Update rule: Close only after Continuation SPI requests can be projected to Skill commands without making Skill concepts canonical semantics.

- [ ] Project ContinuationRequest to a compact Skill command/WorkOrder with typed input/result and completion/evidence information.
- [ ] Preserve model-independent capability/complexity/risk/review metadata where available.
- [ ] Keep concrete model/provider/reasoning selection in host dispatch policy.
- [ ] Keep advance/status/submit free from unnecessary child AI invocation.
- [ ] Do not emit internal deterministic Actions as Skill WorkOrders.
- [ ] Normalize Skill output into typed Result/Evidence rather than conversation-history transfer.

## CWF-77-08: Reference Vertical Slice

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62.3 and `sm-workflow`
- Update rule: Close only after the real Cozy fixture executes internal -> suspended external -> resumed -> internal through the IoC boundary.

- [ ] Exercise Cozy Phase 62.3's real BuildProject -> RunTests -> ReviewChange -> CommitChanges fixture.
- [ ] Where the fixture is entity-triggered, enter it only through the Phase
  63.2 `CommittedTransition` and Phase 64 binding, never a raw event or
  Operation shortcut.
- [ ] Prove Build/Test complete internally without Skill/model invocation.
- [ ] Prove Build/Test and Commit/closing Actions are interpreted as `ExecProgram` values through UnitOfWork, not direct callbacks.
- [ ] Prove ReviewChange returns a durably persisted Continuation.
- [ ] Prove ReviewChange becomes externally claimable only after its suspension UnitOfWork commits.
- [ ] Consume the same Continuation SPI through injected Skill and deterministic test adapters.
- [ ] Submit typed ReviewResult and prove the same suspended Action resumes.
- [ ] Prove Commit/closing executes internally and reaches terminal state.
- [ ] Prove ReviewResult resume uses a fresh UnitOfWork and atomically commits completion, progression, history, and events.

## CWF-77-09: CML-First Evidence and Consumer Handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62.1-62.3 and Textus `sm-workflow`
- Update rule: Close only after reproducible cross-repository evidence and exact consumer handoff are frozen.

- [ ] Record exact Cozy 62.1 schema, 62.2 generated ABI, 62.3 fixture/handoff, CNCF revision and compatibility evidence.
- [ ] Prove independent WorkflowInstance persistence across create, suspension, resume, replay, restart and recovery.
- [ ] Record Continuation SPI, Generic Skill projection and `sm-workflow` consumer contract without claiming SQLite/CLI completion.
- [ ] Verify no Workflow-wide protocol mode or semantic InvocationBinding is required.
- [ ] Verify Phase 77 did not absorb Phase 64 composite derivation/definition
  semantics or Phase 63 local transition/commit ownership.
- [ ] Complete focused validation, executable specifications, regression validation, review, re-review where required, and release closure.
- [ ] Do not claim assemble binding, Workflow proxy/REST, UI Workflow or Flutter completion.
