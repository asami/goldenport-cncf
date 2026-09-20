# Phase 77 Checklist - StateMachine API/SPI Runtime and Skill-Driven Workflow Vertical Slice

status=planned
phase=[Phase 77](phase-77.md)

This checklist is aligned with the consolidated Phase 77. It includes the minimum typed Workflow protocol and Skill/Codex JSON encoding required by `sm-workflow` Phase 1, but not broad protocol expansion. Historical protocol/binding addenda remain design history; closure does not require a Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` switch.

## CWF-77-01: Generated ABI Admission

Stage Status:
- Current status: OPEN
- Owner: CNCF generated-contract/runtime owner
- Update rule: Close only after the supported Cozy 62.1-62.3 ABI, compatibility policy, and structured fail-closed diagnostics are frozen and executable.

- [ ] Inventory Cozy Phase 62.1 API/SPI and ActionExecution schemas, Phase 62.2 generated ABI/bootstrap metadata, and Phase 62.3 fixture/handoff evidence.
- [ ] Define supported ABI version admission and incompatible/unknown required-semantics failure behavior.
- [ ] Preserve Workflow, StateMachine/Composite StateMachine, State/Action/Operation provenance, Provided API and Required SPI identity without CML parsing or name inference.
- [ ] For an entity-triggered Workflow, accept only the Phase 63.2 `CommittedTransition` origin through the Phase 64 binding/derivation contract; do not observe attempted/rolled-back transitions or add a raw Operation/event trigger route.
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
- Update rule: Close only after the admitted Workflow is bound to Phase 77's provider-neutral independently durable WorkflowInstance persistence SPI and external suspension/resume can be correlated durably.

- [ ] Define/version-bind Phase 77's provider-neutral WorkflowInstance persistence SPI with independent instance/definition identity, revision, lifecycle/progression state, append-only history, and correlation/causation references.
- [ ] Consume rather than redefine Phase 64 Composite/Workflow semantics and action provenance/causal order.
- [ ] Prohibit entity fields, entity StateMachine records, and shared-table ownership from becoming the authoritative WorkflowInstance store.
- [ ] Define explicitly configured same-store transaction behavior and idempotent/recoverable cross-store delivery from committed entity transitions.
- [ ] Persist suspension identity, expected revision/ContextSnapshot, completion/evidence requirements, and resume correlation without requiring a default CNCF datastore provider.
- [ ] Require consuming components such as `sm-workflow` to supply datastore, migration, retention and lease policy without changing the generic contract.

## CWF-77-04: StateMachine Progression and ActionExecution

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after every admitted executable Action is interpreted through `ExecProgram[UnitOfWorkOp, ActionExecution]` and bounded progression handles `Completed`, `Suspended`, `Failed`, terminal and structured policy stops deterministically.

- [ ] Select the next Action only from admitted StateMachine/Workflow semantics; do not infer from names/documentation/effect labels.
- [ ] Keep State/Guard/Transition selection pure, then lower every selected executable Action to the canonical UnitOfWork program.
- [ ] Interpret internal Action programs and feed `Completed(Result)` back into StateMachine transition semantics.
- [ ] Return/persist `Suspended(Continuation)` only through the interpreted program when an external Required SPI provider/result is needed.
- [ ] Propagate/map `Failed(Error)` through declared failure semantics without silently crossing boundaries.
- [ ] Detect ambiguity, cycle/bound overflow, unavailable required input, stale state and unsupported execution as structured failures.
- [ ] Remove or adapt the canonical runtime's direct `ResolvedAction.run(...): Consequence[Unit]` / `Effect.execute` path; prove the same Action cannot execute both directly and through UnitOfWork.
- [ ] Keep retry scheduling, client-turn policy and concrete model selection outside StateMachine semantics.

## CWF-77-05: StateMachine API/SPI Provider Runtime

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / UnitOfWork runtime owners
- Update rule: Close only after Provided API dispatch and Required SPI Provider resolution use the canonical UnitOfWork execution infrastructure without a direct side-effect escape path.

- [ ] Dispatch admitted StateMachine Provided API operations through typed runtime contracts.
- [ ] Resolve Required SPI operations through Provider bindings independent from Action implementation.
- [ ] Define the component-programmer API so `ComponentFactory` exposes generated/standard Provider factory methods and component implementations construct component-specific Providers.
- [ ] Keep Action methods out of the `ComponentFactory` override surface; Providers implement the typed Required SPI operations associated with Actions.
- [ ] Support local/direct, external-continuation, and deterministic test Provider forms.
- [ ] Resolve every effectful Provider invocation to a typed `ExecProgram[UnitOfWorkOp, ActionExecution]` interpreted by the Phase 64.2 planner/interpreter.
- [ ] Preserve Cozy's frozen `StateMachineProvider.execute(...): ActionExecution` ABI; admit a direct result only when it is pure/deterministic or returns suspension without performing external work.
- [ ] Preserve identity, idempotency, authorization, provenance and normal UnitOfWork boundaries.
- [ ] Prohibit Workflow-specific low-level Action algebra, opaque callbacks, raw command execution and semantic protocol-mode switches.

## CWF-77-06: Durable Continuation, Resume, and IoC

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after suspension commits atomically with runtime progression, external claim begins only after commit, resume runs in a fresh UnitOfWork, and an IoC-injected Continuation SPI adapter can return a valid typed result.

- [ ] Distinguish Required SPI, Provider SPI, Continuation Protocol, and Continuation SPI Projection.
- [ ] Create a durable Continuation carrying instance/run identity, suspended Action/SPI identity, expected revision/ContextSnapshot, typed result contract, Completion/Evidence contract and minimum context references.
- [ ] Persist StateMachine/WorkflowInstance progression and suspension atomically in the active UnitOfWork before making its Continuation SPI request externally claimable.
- [ ] Publish or expose claimable Continuation work only from an after-commit boundary; rollback must expose none.
- [ ] Inject Skill/Human/UI/remote adapters through ComponentFactory/provider construction rather than direct Workflow-to-Skill calls.
- [ ] Start a fresh UnitOfWork for resume; validate typed result, identity, expected revision/snapshot and evidence before completing the suspended Action and committing subsequent progression/events.
- [ ] Reject stale, duplicate, expired/invalid or incompatible resume attempts fail closed.
- [ ] Prove restart/recovery retains the same suspension boundary and does not reissue an already completed external action.

## CWF-77-07: Generic Skill Workflow Projection

Stage Status:
- Current status: OPEN
- Owner: CNCF Generic Skill Workflow Support owner
- Update rule: Close only after an externally claimable Continuation SPI request can be projected to a Skill command without making Skill concepts canonical StateMachine semantics.

- [ ] Project a `ContinuationRequest` into the closed `WORK_ORDER` Continuation form with typed WorkOrder input/result and Completion/Evidence information.
- [ ] Preserve model-independent capability/complexity/risk/review metadata and the initial abstract `ReasoningLevel` vocabulary: `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL`.
- [ ] Keep concrete model/provider/reasoning-level selection in host dispatch policy and record it only as execution evidence.
- [ ] Project the minimum common `Presentation`: required title/current situation plus optional summary/next action/reason/progress; never use it as Workflow control input.
- [ ] Normalize a Skill/Host-dispatched WorkOrder completion into typed `ExecutionEvidence` without making concrete worker/profile selection a Workflow control input.
- [ ] Allow control-plane advance/status/submit operations to be called directly without a child AI invocation.
- [ ] Do not emit internal deterministic Actions as Skill WorkOrders merely because a Skill drives the Workflow.
- [ ] Normalize Skill worker output into a typed `ContinuationResult`/Evidence rather than requiring parent conversation-history transfer.

## CWF-77-08: Reference Vertical Slice

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62.3 and `sm-workflow`
- Update rule: Close only after the real Cozy fixture executes the complete internal -> suspended external -> resumed -> internal path through the IoC boundary.

- [ ] Exercise Cozy Phase 62.3's real `WorkflowStartRequest -> bounded BuildProject/RunTests progression -> WorkflowStartResult/WorkflowHandle/WORK_ORDER -> ReviewChange resume -> CommitChanges -> TERMINAL`-equivalent fixture through ComponentFactory and the admitted StateMachine runtime.
- [ ] Where the fixture is entity-triggered, enter it only through the Phase 63.2 `CommittedTransition` and Phase 64 binding, never a raw event or Operation shortcut.
- [ ] Prove Build/Test and Commit/closing Actions are interpreted as UnitOfWork programs, not direct callbacks.
- [ ] Prove ReviewChange resolves to external SPI, persists its durable Continuation, and becomes externally claimable only after commit.
- [ ] Submit a typed `ContinuationResult`/`WorkResult` and prove the same suspended Action resumes in a fresh UnitOfWork and StateMachine transition proceeds to a typed terminal result.
- [ ] Bind a deterministic test Provider to ReviewChange and prove the same StateMachine semantics execute without an actual AI/Skill provider.

## CWF-77-09: CML-First Evidence and Consumer Handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62.1-62.3 and Textus `sm-workflow`
- Update rule: Close only after reproducible cross-repository evidence, the minimum typed Workflow protocol/Skill-Codex JSON encoding, and the exact consumer handoff are frozen.

- [ ] Record exact Cozy 62.1 schema, 62.2 generated ABI, 62.3 fixture/handoff, CNCF revision and admitted schema compatibility evidence.
- [ ] Prove WorkflowInstance persistence remains independently owned from entity StateMachine persistence across create, suspension, resume, replay, restart and recovery cases.
- [ ] Record the minimum typed Start/Continuation/WorkOrder/Terminal protocol, Generic Skill projection, and `sm-workflow` consumer contract without claiming `sm-workflow` SQLite/CLI completion.
- [ ] Verify no Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` is required by the final runtime path.
- [ ] Complete focused validation, executable specifications, regression validation, independent review, clean re-review where required, and release closure.
- [ ] Do not claim broad Start/API expansion beyond the minimum typed Start request/result, rich Presentation/UI, additional reasoning vocabulary, parent/child Workflow composition, orchestration, REST/MCP/UI, or Flutter completion.

## CWF-77-10: Minimum Typed Workflow Protocol and Skill/Codex JSON Encoding

Stage Status:
- Current status: OPEN
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after the minimum typed Start/Continuation/WorkOrder/Terminal model and its schema-versioned, fail-closed Skill/Codex JSON encoding can drive a separate-process/turn exchange without JSON becoming the canonical domain model.

- [ ] Define `WorkflowStartRequest[I]` / `WorkflowStartResult[W, O]`, `WorkflowHandle`, and the closed `Continuation = WORK_ORDER | DECISION | WAIT | TERMINAL` model with application-owned typed payloads.
- [ ] Encode/decode the admitted Start, `ContinuationRequest`, and `ContinuationResult`/`WorkResult` forms with schema identity/version and fail closed on unknown or incompatible input.
- [ ] Preserve Workflow/Continuation identity, expected revision, ContextSnapshot, typed input/result, and Completion/Evidence requirements.
- [ ] Provide the minimum `WorkflowHandle` representation for terminal/suspension state reference.
- [ ] Define `WorkOrder.ExecutionRequirement` with small typed `CapabilityRequirement` / `RiskLevel` and the initial abstract `ROUTINE` / `STANDARD` / `DEEP` / `CRITICAL` vocabulary; prevent concrete profile selection from controlling progression and do not introduce a policy engine or UI dispatch surface.
- [ ] Encode/decode typed `ExecutionEvidence` for a Skill/Host-dispatched WorkResult, including requested requirement, selected worker profile, and mapping-policy version; reject a missing or incompatible evidence form where that dispatch contract requires it.
- [ ] Define the minimum common `Presentation`: required title/current situation plus optional summary/next action/reason/`Progress`; prevent it from controlling progression.
- [ ] Reject incorrect identity, stale revision/snapshot, incompatible typed payload, missing evidence, and duplicate/incompatible result according to the durable Continuation contract.
- [ ] Keep broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child composition, orchestration, and REST/MCP/UI surfaces out of Phase 77.
