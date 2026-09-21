# Phase 77 Checklist - Generated Workflow ABI, Discovery, and Persistence SPI

status=closed
closed_at=2026-09-21
phase=[Phase 77](phase-77.md)

This checklist retains the original CWF identities. The 2026-09-21 split keeps
CWF-77-01 through CWF-77-03 in Phase 77. The remaining items are closed here
as split-relocation decisions and are owned exactly once by the linked child
checklists; their product acceptance is not claimed by this Phase.

## CWF-77-01: Generated ABI Admission

Stage Status:
- Current status: DONE
- Owner: CNCF generated-contract/runtime owner
- Update rule: Closed with the supported Cozy 62.1-62.3 ABI, compatibility policy, and structured fail-closed diagnostics frozen and executable.

- [x] Inventory Cozy Phase 62.1 API/SPI and ActionExecution schemas, Phase 62.2 generated ABI/bootstrap metadata, and Phase 62.3 fixture/handoff evidence.
- [x] Define supported ABI version admission and incompatible/unknown required-semantics failure behavior.
- [x] Preserve Workflow, StateMachine/Composite StateMachine, State/Action/Operation provenance, Provided API and Required SPI identity without CML parsing or name inference.
- [x] For an entity-triggered Workflow, accept only the Phase 63.2 `CommittedTransition` origin through the Phase 64 binding/derivation contract; do not observe attempted/rolled-back transitions or add a raw Operation/event trigger route.
- [x] Admit ActionExecution/Continuation/Context/Completion/Evidence schema shapes required by the reference vertical slice.

## CWF-77-02: ComponentFactory Discovery

Stage Status:
- Current status: DONE
- Owner: CNCF ComponentFactory owner
- Update rule: Closed with ComponentFactory discovery of admitted generated definitions and API/SPI metadata with explicit version/source diagnostics.

- [x] Bind generated StateMachine/Workflow bootstrap metadata to ComponentFactory.
- [x] Discover Provided API / Required SPI metadata from generated artifacts.
- [x] Reject absent, duplicate, or incompatible generated definitions rather than accepting handwritten canonical runtime definitions.
- [x] Preserve normal component capability/admission boundaries.

## CWF-77-03: Independent WorkflowInstance Persistence

Stage Status:
- Current status: DONE
- Owner: CNCF Workflow persistence/runtime owner
- Update rule: Closed with the provider-neutral WorkflowInstance persistence SPI and its independent process identity/revision/history contract accepted.

- [x] Define/version-bind the provider-neutral WorkflowInstance persistence SPI with independent instance/definition identity, revision, lifecycle/progression state, append-only history, and correlation/causation references.
- [x] Consume rather than redefine Phase 64 Composite/Workflow semantics and action provenance/causal order.
- [x] Prohibit entity fields, entity StateMachine records, and shared-table ownership from becoming the authoritative WorkflowInstance store.
- [x] Define explicitly configured same-store transaction behavior and idempotent/recoverable cross-store delivery from committed entity transitions without selecting a default CNCF datastore provider.
- [x] Freeze the suspension identity, expected revision/ContextSnapshot, Completion/Evidence requirements, and resume-correlation persistence boundary for Phase 77.1.
- [x] Require consuming components such as `sm-workflow` to supply datastore, migration, retention, and lease policy without changing the generic contract.

## CWF-77-04: StateMachine Progression and ActionExecution

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.1 Checklist](phase-77.1-checklist.md#cwf-77-04-statemachine-progression-and-actionexecution)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.1.

- [x] Relocated to Phase 77.1 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: select the next Action only from admitted StateMachine/Workflow semantics; do not infer from names, documentation, or effect labels.
- [x] Relocated: keep State/Guard/Transition selection pure, then lower every selected executable Action to the canonical UnitOfWork program.
- [x] Relocated: interpret internal Action programs and feed `Completed(Result)` back into StateMachine transition semantics.
- [x] Relocated: return/persist `Suspended(Continuation)` only through the interpreted program when an external Required SPI provider/result is needed.
- [x] Relocated: propagate/map `Failed(Error)` through declared failure semantics without silently crossing boundaries.
- [x] Relocated: detect ambiguity, cycle/bound overflow, unavailable required input, stale state, and unsupported execution as structured failures.
- [x] Relocated: remove or adapt the canonical runtime direct `ResolvedAction.run(...): Consequence[Unit]` / `Effect.execute` path; prove an Action cannot execute both directly and through UnitOfWork.
- [x] Relocated: keep retry scheduling, client-turn policy, and concrete model selection outside StateMachine semantics.
- [x] Relocated: distinguish provider-neutral `OperationAction` and `JudgmentAction` semantics without a Workflow-wide execution mode or provider-specific Action subtype.
- [x] Relocated: admit typed `JudgmentResult` alternatives, rationale, and evidence; reject unknown alternatives and incompatible result payloads.
- [x] Relocated: prove the StateMachine, not a judgment worker, maps an admitted decision to the next transition, state, or Action.

## CWF-77-05: StateMachine API/SPI Provider Runtime

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.1 Checklist](phase-77.1-checklist.md#cwf-77-05-statemachine-apispi-provider-runtime)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.1.

- [x] Relocated to Phase 77.1 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: dispatch admitted StateMachine Provided API operations through typed runtime contracts.
- [x] Relocated: resolve Required SPI operations through Provider bindings independent from Action implementation.
- [x] Relocated: define the component-programmer API so ComponentFactory exposes generated/standard Provider factory methods and component implementations construct component-specific Providers.
- [x] Relocated: keep Action methods out of the ComponentFactory override surface; Providers implement typed Required SPI operations associated with Actions.
- [x] Relocated: support local/direct, external-continuation, and deterministic-test Provider forms.
- [x] Relocated: resolve every effectful Provider invocation to typed `ExecProgram[UnitOfWorkOp, ActionExecution]` interpreted by the Phase 64.2 planner/interpreter.
- [x] Relocated: preserve Cozy's frozen `StateMachineProvider.execute(...): ActionExecution` ABI; admit a direct result only when pure/deterministic or a suspension without external work.
- [x] Relocated: preserve identity, idempotency, authorization, provenance, and normal UnitOfWork boundaries.
- [x] Relocated: prohibit Workflow-specific low-level Action algebra, opaque callbacks, raw command execution, and semantic protocol-mode switches.

## CWF-77-06: Durable Continuation, Resume, and IoC

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.1 Checklist](phase-77.1-checklist.md#cwf-77-06-durable-continuation-resume-and-ioc)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.1.

- [x] Relocated to Phase 77.1 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: distinguish Required SPI, Provider SPI, Continuation Protocol, and Continuation SPI Projection.
- [x] Relocated: create durable Continuation carrying instance/run identity, suspended Action/SPI identity, expected revision/ContextSnapshot, typed result contract, Completion/Evidence contract, and minimum context references.
- [x] Relocated: persist StateMachine/WorkflowInstance progression and suspension atomically in the active UnitOfWork before making Continuation work externally claimable.
- [x] Relocated: publish or expose claimable Continuation work only from an after-commit boundary; rollback exposes none.
- [x] Relocated: inject Skill/Human/UI/remote adapters through ComponentFactory/provider construction rather than direct Workflow-to-Skill calls.
- [x] Relocated: start a fresh UnitOfWork for resume; validate typed result, identity, expected revision/snapshot, and evidence before completing the suspended Action and committing subsequent progression/events.
- [x] Relocated: reject stale, duplicate, expired/invalid, and incompatible resume attempts fail closed.
- [x] Relocated: prove restart/recovery retains the suspension boundary and does not reissue an already completed external Action.

## CWF-77-07: Generic Skill Workflow Projection

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.2 Checklist](phase-77.2-checklist.md#cwf-77-07-generic-skill-workflow-projection)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.2.

- [x] Relocated to Phase 77.2 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: project a `ContinuationRequest` into the closed `WORK_ORDER` Continuation form with typed WorkOrder input/result and Completion/Evidence information.
- [x] Relocated: preserve model-independent capability/complexity/risk/review metadata and the abstract `ReasoningLevel` vocabulary: `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL`.
- [x] Relocated: keep concrete model/provider/reasoning-level selection in host dispatch policy and record it only as execution evidence.
- [x] Relocated: project the minimum common `Presentation`: required title/current situation plus optional summary/next action/reason/progress; never use it as Workflow control input.
- [x] Relocated: normalize a Skill/Host-dispatched WorkOrder completion into typed `ExecutionEvidence` without making concrete worker/profile selection a Workflow control input.
- [x] Relocated: allow control-plane advance/status/submit operations to be called directly without a child AI invocation.
- [x] Relocated: do not emit internal deterministic Actions as Skill WorkOrders merely because a Skill drives the Workflow.
- [x] Relocated: normalize Skill worker output into typed `ContinuationResult`/Evidence rather than requiring parent conversation-history transfer.

## CWF-77-08: Reference Vertical Slice

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.2 Checklist](phase-77.2-checklist.md#cwf-77-08-reference-vertical-slice)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.2.

- [x] Relocated to Phase 77.2 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: exercise Cozy Phase 62.3's real `WorkflowStartRequest -> bounded BuildProject/RunTests progression -> WorkflowStartResult/WorkflowHandle/WORK_ORDER -> ReviewChange resume -> CommitChanges -> TERMINAL`-equivalent fixture through ComponentFactory and the admitted StateMachine runtime.
- [x] Relocated: where the fixture is entity-triggered, enter it only through the Phase 63.2 `CommittedTransition` and Phase 64 binding, never a raw event or Operation shortcut.
- [x] Relocated: prove Build/Test and Commit/closing Actions are interpreted as UnitOfWork programs, not direct callbacks.
- [x] Relocated: prove ReviewChange resolves to external SPI, persists its durable Continuation, and becomes externally claimable only after commit.
- [x] Relocated: submit a typed `ContinuationResult`/`WorkResult` and prove the same suspended Action resumes in a fresh UnitOfWork and StateMachine transition proceeds to a typed terminal result.
- [x] Relocated: bind a deterministic test Provider to ReviewChange and prove the same StateMachine semantics execute without an actual AI/Skill provider.
- [x] Relocated: bind ReviewChange as a `JudgmentAction` carrying typed goal, context, alternatives, criteria, and expected-result metadata.
- [x] Relocated: exercise Codex as the initial external judgment worker through the Generic Skill / durable Continuation boundary and preserve typed decision, rationale, and evidence across the fail-closed JSON round trip.
- [x] Relocated: prove provider replacement does not change Workflow definition identity or transition semantics.

## CWF-77-09: CML-First Evidence and Consumer Handoff

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.2 Checklist](phase-77.2-checklist.md#cwf-77-09-cml-first-evidence-and-consumer-handoff)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.2.

- [x] Relocated to Phase 77.2 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: record exact Cozy 62.1 schema, 62.2 generated ABI, 62.3 fixture/handoff, CNCF revision, and admitted schema compatibility evidence.
- [x] Relocated: prove WorkflowInstance persistence remains independently owned from entity StateMachine persistence across create, suspension, resume, replay, restart, and recovery cases.
- [x] Relocated: record the minimum typed Start/Continuation/WorkOrder/Terminal protocol, Generic Skill projection, and `sm-workflow` consumer contract without claiming `sm-workflow` SQLite/CLI completion.
- [x] Relocated: verify no Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` is required by the final runtime path.
- [x] Relocated: complete focused validation, executable specifications, regression validation, independent review, clean re-review where required, and release closure.
- [x] Relocated: do not claim broad Start/API expansion beyond the minimum typed Start request/result, rich Presentation/UI, additional reasoning vocabulary, parent/child Workflow composition, orchestration, REST/MCP/UI, or Flutter completion.

## CWF-77-10: Minimum Typed Workflow Protocol and Skill/Codex JSON Encoding

Stage Status:
- Current status: CLOSED
- Owner: Relocated to [Phase 77.2 Checklist](phase-77.2-checklist.md#cwf-77-10-minimum-typed-workflow-protocol-and-skillcodex-json-encoding)
- Update rule: Split relocation on 2026-09-21; product acceptance belongs only to Phase 77.2.

- [x] Relocated to Phase 77.2 by the applied Phase 77 split; no Phase 77 product completion is asserted.
- [x] Relocated: define `WorkflowStartRequest[I]` / `WorkflowStartResult[W, O]`, `WorkflowHandle`, and the closed `Continuation = WORK_ORDER | DECISION | WAIT | TERMINAL` model with application-owned typed payloads.
- [x] Relocated: encode/decode the admitted Start, `ContinuationRequest`, and `ContinuationResult`/`WorkResult` forms with schema identity/version and fail closed on unknown or incompatible input.
- [x] Relocated: preserve Workflow/Continuation identity, expected revision, ContextSnapshot, typed input/result, and Completion/Evidence requirements.
- [x] Relocated: provide the minimum `WorkflowHandle` representation for terminal/suspension state reference.
- [x] Relocated: define `WorkOrder.ExecutionRequirement` with small typed `CapabilityRequirement` / `RiskLevel` and the initial abstract `ROUTINE` / `STANDARD` / `DEEP` / `CRITICAL` vocabulary; prevent concrete profile selection from controlling progression and do not introduce a policy engine or UI dispatch surface.
- [x] Relocated: encode/decode typed `ExecutionEvidence` for a Skill/Host-dispatched WorkResult, including requested requirement, selected worker profile, and mapping-policy version; reject a missing or incompatible evidence form where that dispatch contract requires it.
- [x] Relocated: define the minimum common `Presentation`: required title/current situation plus optional summary/next action/reason/`Progress`; prevent it from controlling progression.
- [x] Relocated: reject incorrect identity, stale revision/snapshot, incompatible typed payload, missing evidence, and duplicate/incompatible result according to the durable Continuation contract.
- [x] Relocated: keep broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child composition, orchestration, and REST/MCP/UI surfaces out of Phase 77.
