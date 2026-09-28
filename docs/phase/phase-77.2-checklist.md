# Phase 77.2 Checklist - Generic Skill Projection, Typed Protocol, and Consumer Handoff

status=closed
closed_at=2026-09-25
phase=[Phase 77.2](phase-77.2.md)
split_from=[Phase 77](phase-77.md)

## CWF-77-07: Generic Skill Workflow Projection

Stage Status:
- Current status: DONE on the manual Phase 77.2 closure boundary
- Owner: CNCF Generic Skill Workflow Support owner
- Update rule: Close only after an externally claimable Continuation SPI request projects to a Skill command without making Skill concepts canonical StateMachine semantics.

- [x] Project a `ContinuationRequest` into the closed `WORK_ORDER` Continuation form with typed WorkOrder input/result and Completion/Evidence information.
- [x] Preserve model-independent capability/risk/review requirements and the abstract `ReasoningLevel` vocabulary: `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL` (per the [accepted common-contract decision](../journal/2026/09/2026-09-20-phase-77-common-contract-reconciliation-decision.md)); do not introduce a separate complexity field.
- [x] Keep concrete model/provider/reasoning-level selection in host dispatch policy and record it only as execution evidence.
- [x] Project the minimum common `Presentation`: required title/current situation plus optional summary/next action/reason/progress; never use it as Workflow control input.
- [x] Normalize a Skill/Host-dispatched WorkOrder completion into typed `ExecutionEvidence` without making concrete worker/profile selection a Workflow control input.
- [x] Allow control-plane advance/status/submit operations to be called directly without a child AI invocation.
- [x] Do not emit internal deterministic Actions as Skill WorkOrders merely because a Skill drives the Workflow.
- [x] Normalize Skill worker output into typed `ContinuationResult`/Evidence rather than requiring parent conversation-history transfer.

Current evidence: a post-commit claimed Continuation projects to a token-free
typed WorkOrder, and `ContinuationResult` admission checks exact identity,
revision, snapshot, result type, completion facts, and required evidence.
Typed Skill/Host completion normalization now uses `SkillWorkResultNormalizerV1`
and records requested requirement, selected worker profile, and mapping-policy
version in `ExecutionEvidence.skillDispatch`. Focused protocol/fixture tests
passed 24/24 (`P772-SKILL-TEST-009`, lock released). Actual Codex dispatch and
the production `sm-workflow` consumer remain outside this Phase's proof.
An explicit Provided API fixture invokes advance/status/submit through
ComponentFactory and the active UnitOfWork without registering a child AI
Provider (`P772-REVIEW-FIX-TEST-027`, CNCF focused suites 26/26,
`lock=released`; semantic Given/When/Then recheck
`P772-REVIEW-FIX-TEST-028`, 26/26, `lock=released`). This proves direct control-plane invocation, not public
API/CLI expansion or production `sm-workflow` integration.

## CWF-77-08: Reference Vertical Slice

Stage Status:
- Current status: DONE on the placed-fixture vertical slice
- Owner: CNCF Phase 77.2 coordinating with Cozy Phase 62.3 and `sm-workflow`
- Update rule: Close only after the real Cozy fixture executes the complete internal -> suspended external -> resumed -> internal path through the IoC boundary.

- [x] Exercise the additive Cozy fixture's generated `WorkflowStartRequest -> bounded BuildProject/RunTests progression -> WorkflowStartResult/WorkflowHandle/WORK_ORDER -> ReviewChange resume -> CommitChanges -> TERMINAL`-equivalent path through ComponentFactory and the admitted StateMachine runtime; retain the frozen 62.3 fixture as an ABI regression.
- [x] Where entity-triggered, enter only through Phase 63.2 `CommittedTransition` and the Phase 64 binding, never a raw event or Operation shortcut. This Provided Operation fixture is not entity-triggered, so the conditional route is not exercised.
- [x] Prove Build/Test and Commit/closing Actions are interpreted as UnitOfWork programs, not direct callbacks.
- [x] Prove ReviewChange resolves to external SPI, persists durable Continuation, and becomes externally claimable only after commit.
- [x] Exercise the loose post-commit failure path: a committed UnitOfWork with failed or indeterminate Continuation persistence is reported incomplete and does not hand out work from that failed result; do not claim atomic rollback.
- [x] Submit typed `ContinuationResult`/`WorkResult` and prove the suspended Action resumes in a fresh UnitOfWork and reaches typed terminal output.
- [x] Bind a deterministic test Provider to ReviewChange and prove unchanged StateMachine semantics without an actual AI/Skill provider.
- [x] Prove Provider replacement does not change Workflow definition identity or transition semantics.

The application-specific `JudgmentAction` specialization and actual Codex
worker are **not** CWF-77-08 closure conditions. They remain open in
`sm-workflow` Phase 1's executable-specification checklist (P1-A06 and P1-S06).
The CNCF fixture uses a deterministic Provider so that its generated Workflow
and durable Continuation contract can be accepted independently of that
consumer implementation. The placed fixture starts from a declared Provided
Operation; it is not entity-triggered, so the conditional Phase 63.2/64 entry
item above is not exercised by this test.

Partial evidence: CNCF's pinned Cozy 62.3 generated Workflow ABI matches the
test declaration's Workflow identity/revision, states, four Action identities,
operation types/bindings, Required SPI, and source locations. Focused
`GeneratedWorkflowAbiSpec` passed 14/14 (`P772-FIXTURE-TEST-001`). A second
focused test passed 15/15 (`P772-FIXTURE-TEST-002`): ABI-correlated test programs
run BuildProject/RunTests, stop at a ComponentFactory-bound ReviewChange SPI,
round-trip WorkOrder/result JSON, and run CommitChanges in a fresh UnitOfWork.
This is partial vertical-slice evidence, not closure: the Action programs are
fixture test programs. A further focused run passed 15/15
(`P772-FIXTURE-TEST-003`) with persisted WorkflowInstance suspension,
durably issued WorkOrder, saved-boundary rejection, and one-shot resume.
The next focused run passed 15/15 (`P772-FIXTURE-TEST-004`): the public Service
test now executes all four
ABI-correlated fixture programs and records Completed exactly once. The Start
and completion Operations remain explicit test bindings, and typed Terminal
output now passes focused validation (`P772-TERMINAL-TEST-005`, 21/21).
The public completion Service projects `TERMINAL` only after the matching
Completed record is persisted. The ABI-correlated fixture also normalizes a
test Skill/Host completion through the bound Continuation SPI adapter against
the stored WorkOrder and recovered claim, then resumes with the normalized
typed Result (`P772-SKILL-FIXTURE-TEST-010`, 24/24, lock released). This remains
fixture-program evidence rather than actual Codex dispatch, generated
executable-code, or production `sm-workflow` integration proof.
The public Service fixture additionally simulates an indeterminate
Continuation write: the backing store retains an `Available` record, but the
failed Start response does not reach WorkOrder issuance
(`P772-INDETERMINATE-TEST-011`, 15/15, lock released). Reconciliation of that
record remains necessary; this does not prove atomic rollback or global claim
exclusion.
Generated-execution gap: the frozen Cozy 62.3 CML fixture has four logical
`ACTION` declarations but no `CONSTITUENT-ACTION`/`DERIVED-ACTION` placement.
An additive Cozy fixture now supplies placements, and the Cozy generator emits
the nested WORKFLOW ActionProgram in deterministic occurrence order
(`P772-COZY-PLACED-TEST-003`, 1/1, lock released). CNCF now pins both new
sidecars and checks their Workflow identity, Action operation/source,
occurrence provenance, effect metadata, and ordinal/placement before binding
the occurrence sequence to `ExecutionPlan` (`P772-CNCF-PLACED-RECEIVER-TEST-017`,
3/3, lock released). A separate executor fixture runs that bound plan through
BuildProject/RunTests, suspends ReviewChange, then runs its generated entry
Action CommitChanges after recovery in a fresh UnitOfWork
(`P772-CNCF-PLACED-EXECUTION-TEST-018`, 4/4, lock released). At that stage the
public Service fixture ran test-owned programs; the later public generated-plan
proof is recorded below.
The generated plan now binds to a provenance-checked
`StateMachineProvidedApiProgram`, preserving suspension stop and completed
result semantics (`P772-CNCF-ADAPTER-RETEST-020`, 5/5, lock released). This is
not by itself the public Service or persisted WorkflowInstance execution proof;
that later proof is recorded below.
The placed fixture now has a separate exact identity/revision/source-SHA
admission profile, and its typed definition survives WorkflowInstance binding
and validation (`P772-CNCF-PLACED-ADMISSION-TEST-021`, 30/30 across three
suites, lock released). The additive fixture now explicitly declares
`beginReview`/`submitReview`; Cozy generates a deterministic Provided API
sidecar (`P772-COZY-PLACED-PROVIDED-TEST-005`, 1/1, lock released), and CNCF
admits it with the placed Workflow and ActionProgram sidecars
(`P772-CNCF-PROVIDED-ADMISSION-TEST-022`, 30/30 across three suites, lock
released). The public Service vertical slice now exercises both the unchanged
old fixture and the placed fixture. In the placed case, the generated plan
supplies the Start program and its generated entry Action supplies the closing
program; ComponentFactory, persisted Continuation and WorkOrder,
fresh-UnitOfWork resume, and Completed WorkflowInstance history pass together
(`P772-CNCF-PUBLIC-PLACED-TEST-023`, 15/15, lock released).
The old Workflow fixture, standalone ActionProgram fixture, and new fixture
passed together (`P772-COZY-PLACED-REGRESSION-004`, 7/7, lock released).
CWF-77-08's generated-sequence public Service/WorkflowInstance gate is met.
The same placed definition also reaches the same Action trace and terminal
history with two distinct registered ReviewChange Provider identities
(`P772-CNCF-PROVIDER-REPLACE-TEST-024`, 15/15, lock released). This proves the
Provider-replacement fixture gate, not actual Codex dispatch; descriptor array
order is not execution authority. Final validation and manual review are
recorded in the Phase 77.2 closure evidence below.

## CWF-77-09: CML-First Evidence and Consumer Handoff

Stage Status:
- Current status: DONE; consumer handoff released
- Owner: CNCF Phase 77.2 coordinating with Cozy Phase 62.1-62.3 and Textus `sm-workflow`
- Update rule: Close only after reproducible cross-repository evidence, the minimum typed Workflow protocol/Skill-Codex JSON encoding, and the exact consumer handoff are frozen.

- [x] Record exact Cozy 62.1 schema, 62.2 generated ABI, 62.3 fixture/handoff, CNCF revision, and admitted schema compatibility evidence.
- [x] Prove WorkflowInstance persistence remains independently owned from entity StateMachine persistence across the admitted first-slice create, suspension, resume, and replay cases without claiming a joint atomic transaction. Cross-process restart and application-store recovery are sm-workflow Phase 2 integration checks; the shared-domain upgrade belongs to [Phase 94](phase-94.md).
- [x] Record the minimum typed Start/Continuation/WorkOrder/Terminal protocol, Generic Skill projection, and `sm-workflow` consumer contract without claiming `sm-workflow` SQLite/CLI completion.
- [x] Verify no Workflow-wide orchestration/continuation mode or semantic `InvocationBinding` is required by the final runtime path.
- [x] Complete focused validation, executable specifications, regression validation, manual final review, full-suite validation, and release closure. The user-directed manual route does not claim a formal independent Phase Full Review Manifest.
- [x] Do not claim broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child composition, orchestration, REST/MCP/UI, or Flutter completion.

The Phase 77 provider-neutral WorkflowInstance SPI owns create/load/append,
definition/revision binding, and append-only history independently of entity
StateMachine storage. The Phase 77.2 generated-plan public Service fixture
creates the instance, persists the suspension, resumes from the saved boundary,
and rejects a second completion; the indeterminate Continuation-write fixture
retains its explicitly loose failure result. This is in-process first-slice
proof, not cross-process application-store recovery or a joint atomic commit.
The generated-plan public Service path uses explicit Provided/Required Operation
bindings, not a Workflow-wide orchestration/continuation mode or semantic
`InvocationBinding`; the CNCF Workflow source and selected fixture specs have
no such type or mode. Cross-repository revision evidence and the final
validation/review are recorded in the manual closure journal.
The production `sm-workflow` integration is not a Phase 77.2 closure gate.

## CWF-77-10: Minimum Typed Workflow Protocol and Skill/Codex JSON Encoding

Stage Status:
- Current status: DONE on the manual Phase 77.2 closure boundary
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after the minimum typed Start/Continuation/WorkOrder/Terminal model and its schema-versioned, fail-closed Skill/Codex JSON encoding can drive a separate-process/turn exchange without JSON becoming the canonical domain model.

- [x] Define `WorkflowStartRequest[I]` / `WorkflowStartResult[W, O]`, `WorkflowHandle`, and the closed `Continuation = WORK_ORDER | DECISION | WAIT | TERMINAL` model with application-owned typed payloads.
- [x] Encode/decode admitted Start, `ContinuationRequest`, and `ContinuationResult`/`WorkResult` forms with schema identity/version and fail closed on unknown or incompatible input.
- [x] Preserve Workflow/Continuation identity, expected revision, ContextSnapshot, typed input/result, and Completion/Evidence requirements.
- [x] Provide the minimum WorkflowHandle representation for terminal/suspension state reference.
- [x] Define WorkOrder `ExecutionRequirement` with typed capability/risk requirements and the initial `ROUTINE` / `STANDARD` / `DEEP` / `CRITICAL` vocabulary without a policy engine or UI dispatch surface.
- [x] Encode/decode typed `ExecutionEvidence` for Skill/Host-dispatched WorkResult and reject a missing or incompatible required evidence form.
- [x] Define the minimum common Presentation with required title/current situation plus optional summary/next action/reason/progress; prevent it from controlling progression.
- [x] Reject incorrect identity, stale revision/snapshot, incompatible payload, missing evidence, and duplicate/incompatible result according to the durable Continuation contract.
- [x] Keep broad Start/API expansion, rich Presentation/UI, additional reasoning vocabulary, parent/child composition, orchestration, and REST/MCP/UI surfaces out of this Phase.

Current evidence: `WorkflowProtocolV1Spec` passes 3/3 for typed Handle/Start,
post-commit WorkOrder projection, WorkOrder/result JSON round trips, and
fail-closed result admission. The latest focused receipt is
`P772-PROTOCOL-FINAL-VAL-014`. A preparatory
`WorkflowStartJsonV1` codec now round-trips the profile-selected Start request
and rejects unknown fields, unsupported schema, incompatible input, and a
bound Operation/Workflow mismatch. `P772-START-JSON-VAL-015` passed 5/5 focused
tests with `lock=released`. Phase 77.1 is closed; this focused test was
preparatory before the final full-suite run and does not create a public Start Operation. Terminal JSON is covered by
`P772-TERMINAL-TEST-005` after Completed-record projection. Wait status JSON
round-trips and fails closed on stale or incomplete state references
(`P772-WAIT-RETEST-007`, 7/7, lock released); it has no resume authority.
Decision JSON now round-trips the typed request without a WorkOrder execution
requirement and cannot be submitted through WorkOrder result admission
(`P772-DECISION-TEST-008`, 8/8, lock released). Start, WorkOrder, Result,
Decision, Wait, and Terminal have focused wire coverage. Decision submission
and execution semantics are not part of this minimum projection/encoding
contract; the Decision JSON is not WorkOrder resume authority. The production
consumer fixture remains outside this Phase. Skill completion
normalization and strict dispatch-evidence JSON are covered by
`P772-SKILL-TEST-009`; actual external Codex dispatch remains open.
The closed response entry point `WorkflowInteractionJsonV1` delegates each
of the four Continuation kinds to its strict codec and passed 10/10 focused
tests (`P772-INTERACTION-TEST-012`, lock released). It does not grant resume
authority or replace the application-owned payload codecs.
The public Service fixture uses this same entry point for Start WorkOrder and
completion Terminal (`P772-PUBLIC-INTERACTION-TEST-013`, 15/15, lock released).
The [consumer handoff](phase-77.2-sm-workflow-handoff.md) is released against
the accepted CNCF revision and full validation. Downstream integration remains
owned by `sm-workflow`.

## Final manual closure

Cozy producer commit `8d70aff3bc4b6e311bd7de38c209f08a7ad72bf0` and
CNCF implementation commit `74aa88f4c4e4a8b2e4d681fe285cfe57bca9ba49`
freeze the cross-repository boundary. CNCF's aggregate `sbt --batch test`
passed 3,823 tests with zero failures and `lock=released`
(`P772-MANUAL-FULL-001`). The manual final review found no remaining
closure blocker. The [closure journal](../journal/2026/09/2026-09-25-phase-77.2-manual-closure.md)
records the deliberate absence of a formal Phase Full Review Manifest and
the continuing `sm-workflow` and Phase 94 responsibilities.
