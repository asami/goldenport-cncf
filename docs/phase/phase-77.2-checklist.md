# Phase 77.2 Checklist - Generic Skill Projection, Typed Protocol, and Consumer Handoff

status=planned
phase=[Phase 77.2](phase-77.2.md)
split_from=[Phase 77](phase-77.md)

## CWF-77-07: Generic Skill Workflow Projection

Stage Status:
- Current status: IN PROGRESS
- Owner: CNCF Generic Skill Workflow Support owner
- Update rule: Close only after an externally claimable Continuation SPI request projects to a Skill command without making Skill concepts canonical StateMachine semantics.

- [ ] Project a `ContinuationRequest` into the closed `WORK_ORDER` Continuation form with typed WorkOrder input/result and Completion/Evidence information.
- [ ] Preserve model-independent capability/complexity/risk/review metadata and the abstract `ReasoningLevel` vocabulary: `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL`.
- [ ] Keep concrete model/provider/reasoning-level selection in host dispatch policy and record it only as execution evidence.
- [ ] Project the minimum common `Presentation`: required title/current situation plus optional summary/next action/reason/progress; never use it as Workflow control input.
- [ ] Normalize a Skill/Host-dispatched WorkOrder completion into typed `ExecutionEvidence` without making concrete worker/profile selection a Workflow control input.
- [ ] Allow control-plane advance/status/submit operations to be called directly without a child AI invocation.
- [ ] Do not emit internal deterministic Actions as Skill WorkOrders merely because a Skill drives the Workflow.
- [ ] Normalize Skill worker output into typed `ContinuationResult`/Evidence rather than requiring parent conversation-history transfer.

Current evidence: a post-commit claimed Continuation projects to a token-free
typed WorkOrder, and `ContinuationResult` admission checks exact identity,
revision, snapshot, result type, completion facts, and required evidence.
Actual Skill dispatch and completion normalization remain open.

## CWF-77-08: Reference Vertical Slice

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77.2 coordinating with Cozy Phase 62.3 and `sm-workflow`
- Update rule: Close only after the real Cozy fixture executes the complete internal -> suspended external -> resumed -> internal path through the IoC boundary.

- [ ] Exercise Cozy Phase 62.3's real `WorkflowStartRequest -> bounded BuildProject/RunTests progression -> WorkflowStartResult/WorkflowHandle/WORK_ORDER -> ReviewChange resume -> CommitChanges -> TERMINAL`-equivalent fixture through ComponentFactory and the admitted StateMachine runtime.
- [ ] Where entity-triggered, enter only through Phase 63.2 `CommittedTransition` and the Phase 64 binding, never a raw event or Operation shortcut.
- [ ] Prove Build/Test and Commit/closing Actions are interpreted as UnitOfWork programs, not direct callbacks.
- [ ] Prove ReviewChange resolves to external SPI, persists durable Continuation, and becomes externally claimable only after commit.
- [ ] Exercise the loose post-commit failure path: a committed UnitOfWork with failed or indeterminate Continuation persistence is reported incomplete and does not hand out work from that failed result; do not claim atomic rollback.
- [ ] Submit typed `ContinuationResult`/`WorkResult` and prove the suspended Action resumes in a fresh UnitOfWork and reaches typed terminal output.
- [ ] Bind a deterministic test Provider to ReviewChange and prove unchanged StateMachine semantics without an actual AI/Skill provider.
- [ ] Bind ReviewChange as `JudgmentAction` with typed goal, context, alternatives, criteria, and expected-result metadata.
- [ ] Exercise Codex as the initial external judgment worker through Generic Skill/durable Continuation and preserve typed decision, rationale, and evidence across fail-closed JSON.
- [ ] Prove Provider replacement does not change Workflow definition identity or transition semantics.

## CWF-77-09: CML-First Evidence and Consumer Handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77.2 coordinating with Cozy Phase 62.1-62.3 and Textus `sm-workflow`
- Update rule: Close only after reproducible cross-repository evidence, the minimum typed Workflow protocol/Skill-Codex JSON encoding, and the exact consumer handoff are frozen.

- [ ] Record exact Cozy 62.1 schema, 62.2 generated ABI, 62.3 fixture/handoff, CNCF revision, and admitted schema compatibility evidence.
- [ ] Prove WorkflowInstance persistence remains independently owned from entity StateMachine persistence across the admitted first-slice create, suspension, resume, and replay cases without claiming a joint atomic transaction. Cross-process restart and application-store recovery are sm-workflow Phase 2 integration checks; the shared-domain upgrade belongs to [Phase 92](phase-92.md).
- [ ] Record the minimum typed Start/Continuation/WorkOrder/Terminal protocol, Generic Skill projection, and `sm-workflow` consumer contract without claiming `sm-workflow` SQLite/CLI completion.
- [ ] Verify no Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` is required by the final runtime path.
- [ ] Complete focused validation, executable specifications, regression validation, independent review, clean re-review where required, and release closure.
- [ ] Do not claim broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child composition, orchestration, REST/MCP/UI, or Flutter completion.

## CWF-77-10: Minimum Typed Workflow Protocol and Skill/Codex JSON Encoding

Stage Status:
- Current status: IN PROGRESS
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after the minimum typed Start/Continuation/WorkOrder/Terminal model and its schema-versioned, fail-closed Skill/Codex JSON encoding can drive a separate-process/turn exchange without JSON becoming the canonical domain model.

- [ ] Define `WorkflowStartRequest[I]` / `WorkflowStartResult[W, O]`, `WorkflowHandle`, and the closed `Continuation = WORK_ORDER | DECISION | WAIT | TERMINAL` model with application-owned typed payloads.
- [ ] Encode/decode admitted Start, `ContinuationRequest`, and `ContinuationResult`/`WorkResult` forms with schema identity/version and fail closed on unknown or incompatible input.
- [ ] Preserve Workflow/Continuation identity, expected revision, ContextSnapshot, typed input/result, and Completion/Evidence requirements.
- [ ] Provide the minimum WorkflowHandle representation for terminal/suspension state reference.
- [ ] Define WorkOrder `ExecutionRequirement` with typed capability/risk requirements and the initial `ROUTINE` / `STANDARD` / `DEEP` / `CRITICAL` vocabulary without a policy engine or UI dispatch surface.
- [ ] Encode/decode typed `ExecutionEvidence` for Skill/Host-dispatched WorkResult and reject a missing or incompatible required evidence form.
- [ ] Define the minimum common Presentation with required title/current situation plus optional summary/next action/reason/progress; prevent it from controlling progression.
- [ ] Reject incorrect identity, stale revision/snapshot, incompatible payload, missing evidence, and duplicate/incompatible result according to the durable Continuation contract.
- [ ] Keep broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child composition, orchestration, and REST/MCP/UI surfaces out of this Phase.

Current evidence: `WorkflowProtocolV1Spec` passes 3/3 for typed Handle/Start,
post-commit WorkOrder projection, WorkOrder/result JSON round trips, and
fail-closed result admission. The latest focused receipt is
`P772-PROTOCOL-FINAL-VAL-014`. A preparatory, uncommitted
`WorkflowStartJsonV1` codec now round-trips the profile-selected Start request
and rejects unknown fields, unsupported schema, incompatible input, and a
bound Operation/Workflow mismatch. `P772-START-JSON-VAL-015` passed 5/5 focused
tests with `lock=released`. Phase 77.1 has not handed off an accepted runtime,
so this is not Phase 77.2 acceptance or a public Start Operation. Remaining
Decision/Wait/Terminal wire forms and the real fixture are still open.
