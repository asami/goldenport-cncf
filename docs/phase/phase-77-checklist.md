# Phase 77 Checklist - StateMachine API/SPI Runtime and Skill-Driven Workflow Vertical Slice

status=planned
phase=[Phase 77](phase-77.md)

This checklist is aligned with the consolidated Phase 77. Historical protocol/binding addenda remain design history; closure does not require a Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` switch.

## CWF-77-01: Generated ABI Admission

Stage Status:
- Current status: OPEN
- Owner: CNCF generated-contract/runtime owner
- Update rule: Close only after supported Cozy StateMachine/Workflow ABI versions, compatibility policy, and structured fail-closed diagnostics are frozen and executable.

- [ ] Inventory the exact Cozy Phase 62 generated StateMachine/Workflow ABI, fixture, API/SPI schema, and source-identity contract.
- [ ] Define supported ABI version admission and incompatible/unknown required-semantics failure behavior.
- [ ] Preserve Workflow, StateMachine/Composite StateMachine, State/Action/Operation provenance, Provided API and Required SPI identity without CML parsing or name inference.
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
- [ ] Prohibit entity fields, entity StateMachine records, and shared-table ownership from becoming the authoritative WorkflowInstance store.
- [ ] Define explicitly configured same-store transaction behavior and idempotent/recoverable cross-store delivery from committed entity transitions.
- [ ] Persist suspension identity, expected revision/ContextSnapshot, completion/evidence requirements, and resume correlation without requiring a default CNCF datastore provider.
- [ ] Require consuming components such as `sm-workflow` to supply datastore, migration, retention and lease policy without changing the generic contract.

## CWF-77-04: StateMachine Progression and ActionExecution

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after bounded progression handles `Completed`, `Suspended`, `Failed`, terminal and structured policy stops deterministically.

- [ ] Select the next Action only from admitted StateMachine/Workflow semantics; do not infer from names/documentation/effect labels.
- [ ] Execute an internal provider and feed `Completed(Result)` back into StateMachine transition semantics.
- [ ] Return/persist `Suspended(Continuation)` when an external Required SPI provider/result is needed.
- [ ] Propagate/map `Failed(Error)` through declared failure semantics without silently crossing boundaries.
- [ ] Detect ambiguity, cycle/bound overflow, unavailable required input, stale state and unsupported execution as structured failures.
- [ ] Keep retry scheduling, client-turn policy and concrete model selection outside StateMachine semantics.

## CWF-77-05: StateMachine API/SPI Provider Runtime

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / UnitOfWork runtime owners
- Update rule: Close only after Provided API dispatch and Required SPI provider resolution reuse existing typed execution infrastructure without a Workflow-specific Action algebra.

- [ ] Dispatch admitted StateMachine Provided API operations through typed runtime contracts.
- [ ] Resolve Required SPI operations through provider bindings independent from Action implementation.
- [ ] Define the component-programmer API so `ComponentFactory` exposes generated/standard Provider factory methods and component implementations override those methods to construct component-specific Providers.
- [ ] Keep Action methods themselves out of the `ComponentFactory` override surface; Providers implement the typed Required SPI operations associated with Actions.
- [ ] Allow one Provider to implement a coherent group of Required SPI operations while retaining operation/SPI-specific runtime binding and selective test/external replacement.
- [ ] Support local/direct, external-continuation, and deterministic test provider forms at the contract/runtime level.
- [ ] Reuse `ExecProgram[UnitOfWorkOp, A]` and Phase 64.2 planner contracts for internal typed Operations/actions where applicable.
- [ ] Preserve identity, idempotency, authorization, provenance and normal UnitOfWork boundaries.
- [ ] Prohibit Workflow-specific low-level Action algebra, opaque callbacks, raw command execution and semantic protocol-mode switches.

## CWF-77-06: Durable Continuation and Resume

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after external SPI suspension can survive persistence/restart and resume only with a valid typed result.

- [ ] Create a durable Continuation carrying instance/run identity, suspended Action/SPI identity, expected revision/ContextSnapshot, typed result contract, Completion/Evidence contract and minimum context references.
- [ ] Validate typed result, identity, expected revision/snapshot and required evidence before completing the suspended Action.
- [ ] Reject stale, duplicate, expired/invalid or incompatible resume attempts fail closed.
- [ ] Make duplicate result submission obey the idempotency contract without duplicate transition/action execution.
- [ ] Prove restart/recovery retains the same suspension boundary and does not reissue an already completed external action.

## CWF-77-07: Generic Skill Workflow Projection

Stage Status:
- Current status: OPEN
- Owner: CNCF Generic Skill Workflow Support owner
- Update rule: Close only after suspended external SPI operations can be projected to Skill-facing commands without making Skill concepts canonical StateMachine semantics.

- [ ] Project a suspended external SPI operation into a compact Skill command/WorkOrder with typed input/result and completion/evidence information.
- [ ] Preserve model-independent capability/complexity/risk/review metadata where available.
- [ ] Keep concrete model/provider/reasoning-level selection in host dispatch policy.
- [ ] Allow control-plane advance/status/submit operations to be called directly without a child AI invocation.
- [ ] Do not emit internal deterministic Actions as Skill WorkOrders merely because a Skill drives the Workflow.
- [ ] Normalize Skill worker output into typed Result/Evidence rather than requiring parent conversation-history transfer.

## CWF-77-08: Reference Vertical Slice

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62 and `sm-workflow`
- Update rule: Close only after the real Cozy fixture executes the complete internal -> suspended external -> resumed -> internal path.

- [ ] Exercise Cozy's real `BuildProject -> RunTests -> ReviewChange -> CommitChanges`-equivalent fixture through ComponentFactory and the admitted StateMachine runtime.
- [ ] Prove Build/Test complete internally without Skill/model invocation.
- [ ] Prove ReviewChange resolves to external SPI and returns a durable Continuation.
- [ ] Submit a typed ReviewResult and prove the same suspended Action resumes and StateMachine transition proceeds.
- [ ] Prove Commit/closing executes internally after accepted ReviewResult and reaches terminal state.
- [ ] Bind a deterministic test provider to ReviewChange and prove the same StateMachine semantics execute without an actual AI/Skill provider.

## CWF-77-09: CML-First Evidence and Consumer Handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62 and Textus `sm-workflow`
- Update rule: Close only after reproducible cross-repository evidence and exact consumer handoff are frozen.

- [ ] Record exact Cozy source, generated ABI version/digest, CNCF revision and admitted schema compatibility evidence.
- [ ] Prove WorkflowInstance persistence remains independently owned from entity StateMachine persistence across create, suspension, resume, replay, restart and recovery cases.
- [ ] Record the Generic Skill projection and `sm-workflow` consumer contract without claiming `sm-workflow` SQLite/CLI completion.
- [ ] Verify no Workflow-wide orchestration/continuation mode or semantic InvocationBinding is required by the final runtime path.
- [ ] Complete focused validation, executable specifications, regression validation, independent review, clean re-review where required, and release closure.
- [ ] Do not claim assemble API/SPI binding, Workflow-to-Workflow proxy/REST, UI Workflow or Flutter completion.
