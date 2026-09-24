# Phase 77.1 Checklist - ActionExecution, Provider Runtime, and Durable Continuation

status=closed
closed_at=2026-09-25
phase=[Phase 77.1](phase-77.1.md)
split_from=[Phase 77](phase-77.md)

The focused 77.1 runtime boundary closed with 105/105 tests in
`P771-FOCUSED-20260925-2` after private naming repair and focused review.
The sole unchecked item below is the explicitly deferred shared transaction
domain owned by Phase 92; it is not an unfulfilled 77.1 closure gate.

## CWF-77-04: StateMachine Progression and ActionExecution

Stage Status:
- Current status: DONE on the focused runtime boundary; the real Candidate fixture proves interpreted suspension, typed Judgment routing, one-shot resume, and selected closing Action; shared atomic persistence belongs to Phase 92
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after every admitted executable Action is interpreted through `ExecProgram[UnitOfWorkOp, ActionExecution]` and bounded progression handles terminal and structured stops deterministically.

- [x] Select the next Action only from admitted StateMachine/Workflow semantics; do not infer from names, documentation, or effect labels.
- [x] Keep State/Guard/Transition selection pure, then lower every selected executable Action to the canonical UnitOfWork program.
- [x] Interpret internal Action programs and feed `Completed(Result)` back into StateMachine transition semantics.
- [x] Return/persist `Suspended(Continuation)` only through the interpreted program when an external Required SPI provider/result is needed.
- [x] Propagate/map `Failed(Error)` through declared failure semantics without silently crossing boundaries.
- [x] Detect ambiguity, cycle/bound overflow, unavailable required input, stale state, and unsupported execution as structured failures.
- [x] Remove or adapt the canonical runtime direct `ResolvedAction.run(...): Consequence[Unit]` / `Effect.execute` path; prove an Action cannot execute both directly and through UnitOfWork.
- [x] Keep retry scheduling, client-turn policy, and concrete model selection outside StateMachine semantics.
- [x] Distinguish provider-neutral `OperationAction` and `JudgmentAction` semantics without a Workflow-wide execution mode or provider-specific Action subtype.
- [x] Admit typed `JudgmentResult` alternatives, rationale, and evidence; reject unknown alternatives and incompatible result payloads.
- [x] Prove the StateMachine, not a judgment worker, maps an admitted decision to the next transition, state, or Action.

### CWF-77-04 suspension propagation gap

The uncommitted `CWF-77-04D` Slice adds `ExecutionPlanExecutor.executeOutcome`
so an interpreted `Suspended` remains a typed value. The existing
`Consequence[Unit]` execution path now fails closed on suspension, preventing
the canonical `PlannedTransitionValidationHook` from treating an unpersisted
continuation as a successful entity update. Focused development execution
`P771-CWF04D-DEV-002` passed 15 tests across `ExecutionPlanExecutorSpec` and
`PlannedTransitionValidationHookSpec` and released the shared SBT lock. That
initial slice did not persist or return the continuation from the active
UnitOfWork path. The later real-fixture `-044` and ordered-persistence `-045`
proofs below supply the loose post-commit path used to check the suspension
item. Joint WorkflowInstance/Continuation/UnitOfWork atomicity is moved
to [Phase 92](phase-92.md); no Cozy change or post-commit atomicity claim is
implied.

### CWF-77-04C: Candidate-Admission Producer-ABI Receiver

Stage Status:
- Current status: IMPLEMENTED (`3006494c`)
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after the named Cozy Phase 73 fixture is received as
  a separate versioned producer ABI, its schema/provenance/compatibility is
  verified, and receiver acceptance is recorded without altering the closed
  Cozy 62.1–62.3 `GeneratedWorkflowAbi` contract.

- [x] Receive Cozy Phase 73's named fixture `src/test/resources/modeler/candidate-admission-producer-abi.json` as the Candidate-Admission producer ABI.
- [x] Verify its schema identity, producer provenance, and explicit compatibility boundary as a separate versioned ABI.
- [x] Record receiver acceptance only after the fixture satisfies the frozen receiver checks; until then, keep acceptance pending.
- [x] Preserve the closed Cozy 62.1–62.3 `GeneratedWorkflowAbi` contract without reopening or altering it.

### CWF-77-04 JudgmentResult payload-type gap

`CandidateAdmissionProducerAbi.admitJudgmentResultC` admits only a declared
Judgment identity and alternative with nonempty rationale and evidence fields.
Its `JudgmentResult` has no typed result payload or payload type reference, and
the producer sidecar's `expectedResult` is a source reference rather than a
runtime result-type contract. The uncommitted CNCF-owned
`TypedJudgmentResultV1` wrapper uses the declaration's Operation `resultType`
to admit a typed payload reference and rejects incompatible or missing types.
It does not infer a type from `expectedResult` text or change the frozen Cozy
ABI. Focused development execution `P771-CWF04-JUDGMENT-DEV-004` passed 18
receiver and StateMachine-router tests. At that point, this was not Phase
acceptance or a complete JudgmentAction execution path; the later integrated
fixture and closing selection below provide focused functional proof.

The uncommitted `CandidateAdmissionRouter.routeSubmittedC` admits a
separate-turn typed `JudgmentResult` against its issued WorkOrder, verifies
the outer and declared payload types, and selects only an explicit
StateMachine-owned route. Focused validation `P771-JUDGMENT-ROUTE-VAL-034`
passed 19 router and producer-ABI tests with `lock=released`. This was a pure
pre-resume admission boundary, not the complete JudgmentAction execution or
durable suspension/resume fixture. The later integrated fixture and selected
closing Action establish focused functional proof; independent review remains.
The next uncommitted source-binding check also requires the typed payload
reference to equal the submitted result reference and binds the admitted
Judgment declaration to the issued Handle's Workflow identity/revision and
WorkOrder Required SPI/Operation. It rejects a foreign Workflow, Operation,
capability, or result reference before routing. Initial attempt
`P771-JUDGMENT-BINDING-VAL-035` stopped at compilation due to a field-name
error; after correction, `P771-JUDGMENT-BINDING-VAL-036` passed the same 19
focused tests with `lock=released`. It still does not perform one-shot resume
or apply the selected StateMachine target.

The uncommitted `CandidateAdmissionRouter.resumePersistedSubmittedC` now loads
the issued WorkOrder once, admits and routes its typed Judgment result before
claim consumption, then invokes the existing WorkflowInstance guard and
one-shot Continuation resume. Focused run `P771-JUDGMENT-RESUME-VAL-039`
passed 14 router/instance-persistence tests with `lock=released`: an unknown
alternative leaves the claim intact, a valid synthetic matched fixture resumes
once, and a duplicate resume fails. The existing Cozy Candidate-Admission
fixture names `OrderProgress`/`workflow-v1`, while the closed CNCF generated
Workflow ABI test fixture is pinned to `WorkflowProducer`/
`workflow-producer-v1`; their direct pairing is rejected before resume. The
test's matched pairing was synthetic; the later real-fixture run below
replaces it. The target is returned but not applied to WorkflowInstance
progression; CWF-77-04 remains open.

### CWF-77-04 real-fixture receiver repair (2026-09-24)

Cozy's `candidate-admission-workflow.cml` generates both
`statemachine-workflow-abi.json` and `candidate-admission-producer-abi.json`
for `OrderProgress`/`workflow-v1`; the generated Candidate-Admission sidecar
matches Cozy's source-tracked handoff fixture byte-for-byte. This is not a
missing Cozy producer artifact. CNCF's closed `GeneratedWorkflowAbi` receiver
accepts only `workflow-producer-v1` and only the `OPERATION` ActionKind, while
the real Workflow ABI declares `JUDGMENT` and `ADMISSION`. Moreover,
`WorkflowInstancePersistence.bindDefinitionC` takes only that closed
`GeneratedWorkflowAbi.Definition`. The additive CNCF `CandidateWorkflowAbi`
receiver now checks the actual generated Workflow JSON against the same-source
Candidate-Admission sidecar, including identity/version, source lines,
`JUDGMENT`/`ADMISSION` Actions, Operations, input bindings, and Required SPI.
`bindCandidateDefinitionC` preserves its SHA-256 and source correlation in
the durable WorkflowInstance binding. No Cozy or closed Phase 62.3 receiver
change was made. `P771-CANDIDATE-WORKFLOW-VAL-041` passed 11 focused tests,
including the real-fixture persisted WorkOrder route and one-shot guarded
resume; `-040` was a test-local compilation failure corrected before the
passing run. The additive `resumeAndAdvancePersistedSubmittedC` path now
rejects an undeclared target before claim consumption, then appends the
selected `OrderProgress/Complete` State as the next WorkflowInstance
progression after guarded resume. The in-memory persistence fixture reloads
revision 2 with its suspension cleared; stale duplicate submission fails.
`P771-CANDIDATE-PROGRESSION-VAL-043` passed 16 focused tests with
`lock=released`. This is a loose resume-then-append boundary, not an atomic
cross-store transaction: a post-resume append failure may need reconciliation
under Phase 92. Other CWF-77-04 ActionExecution and suspension acceptance
items remain open.

The next real-fixture scenario starts from an initial `OrderProgress` instance,
interprets a `StateMachineRequiredOperationAction` through a bound
`StateMachineProgramProvider` and the active UnitOfWork, and receives its
`Suspended(Continuation)` only after the loose commit and continuation write.
It then appends the suspension boundary, issues a persisted WorkOrder, rejects
an unknown Judgment alternative and an undeclared target before claim
consumption, and resumes once into `OrderProgress/Complete`. Focused
`WorkflowInstancePersistenceSpec` execution
`P771-CANDIDATE-ENDTOEND-VAL-044` passed 9/9 tests with `lock=released`.
The follow-up `ExecutionPlanExecutor.executeCommittingAfterC` uses the existing
post-commit prerequisite hook to append the WorkflowInstance suspension before
publishing the Continuation. The real fixture verifies that the Continuation
store is still empty during that append; a separate failure test verifies that
a rejected prerequisite returns no claimable Continuation. Focused
`P771-CANDIDATE-ORDERED-VAL-045` passed 17/17 tests with `lock=released`.
This remains a loose sequence: if the later Continuation write fails after the
instance append, reconciliation is still needed. It does not prove atomicity
or a production ingress adapter. Keep the remaining acceptance items open.

## CWF-77-05: StateMachine API/SPI Provider Runtime

Stage Status:
- Current status: DONE on the focused runtime boundary; typed Provided API and Required SPI dispatch, public Service Start/completion binding, committed suspension, selected closing Action, and terminal history append have focused fixture proofs; the real Candidate public completion declaration remains a downstream fixture gap
- Owner: CNCF StateMachine / UnitOfWork runtime owners
- Update rule: Close only after Provided API dispatch and Required SPI Provider resolution use the canonical UnitOfWork execution infrastructure without a direct side-effect escape path.

- [x] Dispatch admitted StateMachine Provided API operations through typed runtime contracts.
- [x] Resolve Required SPI operations through Provider bindings independent from Action implementation.
- [x] Define the component-programmer API so ComponentFactory exposes standard Provider-source methods and component implementations construct component-specific Providers.
- [x] Keep Action methods out of the ComponentFactory override surface; Providers implement typed Required SPI operations associated with Actions.
- [x] Support local/direct, external-continuation, and deterministic-test Provider forms.
- [x] Resolve every effectful Provider invocation to typed `ExecProgram[UnitOfWorkOp, ActionExecution]` interpreted by the Phase 64.2 planner/interpreter.
- [x] Preserve Cozy's frozen `StateMachineProvider.execute(...): ActionExecution` ABI; admit a direct result only when pure/deterministic or a suspension without external work.
- [x] Preserve identity, idempotency, authorization, provenance, and normal UnitOfWork boundaries.
- [x] Prohibit Workflow-specific low-level Action algebra, opaque callbacks, raw command execution, and semantic protocol-mode switches.

### CWF-77-05B: Direct Provider outcome admission

The uncommitted `CWF-77-05B` Slice rejects an incomplete Provider request before
dispatch, and rejects an incomplete, result-type-incompatible, or foreign-run/
foreign-contract Provider outcome after dispatch. Focused execution
`P771-CWF05B-VAL-002` passed ten `UnitOfWorkStateMachineProviderDispatchSpec`
tests and released the shared SBT lock. This is post-invocation contract
checking only: it does not prove that a direct Provider call is pure or
deterministic, prevent effects before rejection, provide an inbound Provided
API descriptor, or satisfy the remaining CWF-77-05 acceptance items.

### CWF-77-05C: Provider program interpretation

The uncommitted `CWF-77-05C` Slice adds a CNCF-owned
`StateMachineProgramProvider` path. The bound Provider constructs
`ExecUowM[ActionExecution]`; the current `UnitOfWorkInterpreter` folds it with
its active operation interpreter and admits the resulting typed outcome. Its
inherited direct `execute` method fails closed, so that Provider subtype has
no second execution route. The interpreter rejects identical active Provider
requests as cycles and bounds nested invocations at 64, while allowing a
distinct request for the same Provider to make finite progress. Focused
development attempt `P771-CWF05C-FIX-DEV-006` passed all 16
`UnitOfWorkStateMachineProviderDispatchSpec` tests and released the shared SBT
lock; a separate read-only development re-review found no remaining concrete
blocker in this Slice. These are development checks, not a formal Step or Phase
acceptance receipt. They do not prove a legacy direct Provider is
pure/deterministic, nor connect the separately generated inbound Provided API
descriptor to dispatch or close the remaining Provider forms. Keep CWF-77-05
open.

The subsequent `StateMachineDeterministicProvider` opt-in keeps the frozen
`StateMachineProvider.execute` signature but prevents an unmarked direct
Provider from being invoked by the UnitOfWork interpreter. Effectful Providers
must use `StateMachineProgramProvider`; a marked direct Provider's exception is
returned as a typed failure. `P771-DIRECT-PROVIDER-VAL-051` passed 18/18
focused dispatch tests with `lock=released` after the exception case was added
(`-050` passed the preceding 17/17). This establishes explicit admission at
the interpreter boundary, not a runtime proof that a marked implementation is
side-effect-free. CWF-77-05 remains partial pending the other checklist items.

### CWF-77-05D: Required-SPI Action to Provider program integration

The uncommitted `CWF-77-05D` executable scenario runs a
`StateMachineRequiredOperationAction` through the active `UnitOfWork` and
observes its bound `StateMachineProgramProvider` execute one typed authorization
operation before returning `Completed`. Focused development attempt
`P771-CWF05D-DEV-002` passed both
`StateMachineRequiredOperationActionSpec` scenarios and released the shared
SBT lock. The scoped development review found no Current Boundary Blocker in
the new scenario. This is not formal Step acceptance and does not establish
Provided API dispatch, direct Provider purity, or durable suspension.

### CWF-77-05 Provided API producer/receiver record

The Phase 77 `GeneratedWorkflowAbi.Definition` contains Workflow, StateMachine,
Action, and Required SPI descriptors, but no Provided API operation descriptor
or identity. `GeneratedWorkflowMetadataProvider` exposes only those definitions.
Cozy's `StateMachineApiSpi` source model does define an explicit
`StateMachineProvidedOperation` with identity, input type, and result type;
however, Cozy 62.3's generated `ComponentFactoryMetadata` contains only a
`WorkflowDescriptor`, and its generated bootstrap collects only that metadata.
The Provided API type declaration therefore does not supply an admitted
operation value to CNCF.

Thus the closed accepted ABI did not identify which inbound Provided API
operation could be dispatched or how its input and result types were bound.
The base Provider-resolution commit `20c484b2` covers outbound Required SPI
binding, not inbound Provided API dispatch. The uncommitted additive Cozy
producer now adds explicit `WORKFLOW` CML `OPERATION` declarations,
resolved against qualified Service Operations, and emits a separate versioned
Scala/JSON producer ABI. CNCF's uncommitted receiver admits that ABI only
alongside a matching accepted Workflow identity, revision, and source. Focused
development runs `P771-COZY-OPERATION-VAL-006` (15 Cozy tests, including legacy
Workflow ABI regression) and `P771-CNCF-PROVIDED-VAL-005` (10 CNCF admission
tests) passed with the shared SBT lock released. This is not
formal Phase acceptance or, by itself, the typed inbound dispatch proof. Keep CWF-77-05
open; do not infer a Provided API from Action or Required SPI names.

The uncommitted `CWF-77-05E` development slice adds a typed UnitOfWork
dispatch for an explicitly declared Provided operation. ComponentFactory
admits only component-owned programs whose Workflow and Service Operation
identities match the generated ABI. The focused `GeneratedWorkflowAbiSpec`
path uses a distinct `beginReview` Provided operation to reach the explicitly
bound `reviewChange` Required SPI program Provider, and rejects unknown
operations, incompatible input, and incompatible output. Development run
`P771-COZY-PROVIDED-SEPARATION-VAL-009` passed 15 producer tests and
`P771-CNCF-PROVIDED-SEPARATION-VAL-010` passed 27 receiver/dispatch tests,
both with the shared SBT lock released. A public Service
ingress and full reference-fixture progression are still unproven; this does
not close CWF-77-05 or constitute Phase acceptance.

`CWF-77-05F` adds a committing entry for an explicit Provided Operation.
It accepts a suspended Required SPI only when it matches the admitted
Workflow declaration, then stages it through the component's Continuation
runtime. The ordinary UnitOfWork operation continues to reject suspension.
The focused fixture proved commit-before-claim and fresh-UnitOfWork typed
resume; `P771-PROVIDED-SUSPENSION-VAL-015` passed 28 tests. Follow-up
`P771-PROVIDED-SUSPENSION-VAL-016` passed 12 tests, including undeclared
Required SPI rejection and a committed-but-incomplete rejected continuation
write. `P771-PROVIDED-SUSPENSION-VAL-017` then proved the distinct Provided
and Required SPI result types. `P771-PROVIDED-RESTART-VAL-018` passed the
focused fixture with persistence-backed claim and typed resume on separately
recreated runtimes, plus rejection of another claim and completed resume.
The subsequent uncommitted fixture recovers the private claim from the
persisted `Claimed` record in a recreated runtime rather than passing the
original claim object across runtime instances; it rejects recovery after
completion. No ownership token is exposed through the public WorkOrder.
The uncommitted `stageProvidedForExternalCommitC` path leaves commit ownership
to its caller, so the existing ActionEngine Service path need not double-commit.
Its fixture proves rollback exposes no claim and external commit exposes one;
`P771-SERVICE-STAGING-VAL-028` passed 28 focused tests with the shared SBT
lock released. The subsequent uncommitted
`StateMachineProvidedApiServiceOperation` binds a declared Provided Operation
to the public Service/ActionEngine route. Its codec checks decoded Workflow
and Operation identity; ActionEngine commits the staged UnitOfWork before the
response returns. The fixture proves commit-before-claim and failed-write
nonpublication through `Subsystem.executeOperationResponse`;
`P771-PUBLIC-SERVICE-VAL-029` passed 29 focused tests with the shared SBT
lock released. Its codec is fixture-specific: concrete `sm-workflow` JSON
request/response mapping remains open.
The follow-up fixture rejects a missing raw Service run identity and follows
the successful Service suspension through post-commit claim, WorkOrder JSON,
Result JSON, and one-shot resume on a recreated runtime.
`P771-SERVICE-WORKORDER-VAL-030` passed 17 focused tests with the SBT lock
released. Application Start/Result schemas, durable WorkflowInstance history
append, and closing Action execution are not proved by this focused path.
The next fixture stores the issued WorkOrder in memory, seeds a matching
suspended WorkflowInstance, rejects an unsuspended instance, and resumes the
matching result only once through `resumePersistedWorkOrderC`.
`P771-SERVICE-GUARDED-VAL-031` passed 21 focused tests with the shared SBT
lock released. Service-owned WorkflowInstance append, application Start/Result
mapping, and closing Action/terminal progression remain open.
The public Service binding now accepts an explicit `SuspensionPersistence`
port, executed after UnitOfWork commit and before Continuation publication.
Its fixture creates the suspended WorkflowInstance from the Service outcome,
then issues and resumes the WorkOrder against the saved record. Failed instance
persistence prevents a claimable Continuation. `P771-SERVICE-INSTANCE-VAL-032`
passed 29 focused tests with the shared SBT lock released. A Continuation
write failure can still leave an instance requiring reconciliation under this
loose transaction model; Start/Result transport and closing Action remain open.
The Service's opt-in `encodeAfterCommitC` projection now runs only after
successful UnitOfWork commit and post-commit persistence. Its fixture returns
the loaded WorkflowInstance Handle and issued WorkOrder JSON directly from the
Start response; failed instance/Continuation writes never invoke the
projection. `P771-POSTCOMMIT-RESPONSE-VAL-052` passed 13/13 focused tests with
`lock=released`. This remains a fixture codec, not the `sm-workflow` transport
contract. `P771-POSTCOMMIT-RESPONSE-VAL-053` passed 13/13 focused tests and
proved a failed post-commit projection leaves the committed WorkflowInstance
and claimable Continuation intact, rather than rolling back the commit.
`P771-POSTCOMMIT-ENGINE-VAL-054` passed 10/10 existing ActionEngine
authorization, observation, and failure-commit tests with `lock=released`.
The public Service fixture now decodes profile-selected `WorkflowStartJsonV1`
through the declared Operation binding, rejects missing/mismatched/malformed
Start inputs, and returns the existing post-commit Handle/WorkOrder response.
The first `P771-PUBLIC-START-JSON-VAL-055` run found a test-local `Argument`
extractor arity error; `P771-PUBLIC-START-JSON-VAL-056` passed 13/13 focused
tests after correction, with `lock=released`. The mapping remains
fixture-specific; `sm-workflow` application schemas remain open.
`WorkflowCompletionServiceOperation` now binds the declared completion
Operation to the same public Service. The fixture decodes
`WorkflowResultJsonV1`, checks the issued WorkOrder and stored suspended
WorkflowInstance, and resumes once after ActionEngine commit through a fresh
UnitOfWork. It rejects missing/incompatible Result JSON, an unsuspended
instance, and a duplicate submission. `P771-PUBLIC-COMPLETION-VAL-057`
passed 13/13 `GeneratedWorkflowAbiSpec` tests with `lock=released`. A
post-commit resume failure remains non-atomic and needs reconciliation;
application transport schemas, closing Action execution, and terminal
WorkflowInstance history are not proved.
The uncommitted `ContinuationRuntime.resumeWithProgramC` evaluates a selected
typed closing `ExecUowM[ActionExecution]` in the fresh resume UnitOfWork before
commit. A failed or newly suspended Action aborts and releases the claim;
`Completed` permits one-shot completion. `P771-CLOSING-PROGRAM-VAL-058`
stopped at a test-local missing Cats syntax import; after correction,
`P771-CLOSING-PROGRAM-VAL-059` passed 19/19 runtime tests with
`lock=released`. The public completion binding now accepts an opt-in pure
selector, chooses the program from the submitted Result and current saved
WorkflowInstance, then passes it through guarded resume. The focused
`P771-CLOSING-SERVICE-VAL-060` run passed 32/32 Service/runtime tests with
`lock=released`, including one execution and duplicate rejection. Automatic
StateMachine plan selection, application transport schemas, and terminal
WorkflowInstance history remain open; no shared transaction is claimed.
When closing Action abort itself fails, the persistent runtime retains the
claim for reconciliation instead of retrying an uncertain UnitOfWork.
`P771-CLOSING-ABORT-VAL-061` passed 20/20 runtime tests with `lock=released`.
The real Candidate-Admission fixture now selects its closing Action through
the admitted Judgment result, issued WorkOrder, explicit StateMachine route,
and declared target binding; a missing route or Action fails closed before
resume. `P771-STATE-CLOSING-VAL-062` passed 9/9 fixture tests with
`lock=released`. Public Service integration of this selector and terminal
WorkflowInstance history remain open.
The public completion binding now carries a selected closing program and
optional next WorkflowInstance record as one selection. It validates the
append before outer commit, rechecks the selected record before resume, and
appends only after successful one-shot Action resume. The synthetic public
Service fixture reaches `Lifecycle.Completed` at revision 2 and rejects a
duplicate without repeating the Action or append. `P771-PUBLIC-CLOSING-VAL-063`
found a test-local invalid terminal progression; corrected focused
`P771-PUBLIC-CLOSING-VAL-064` passed 22/22 tests with `lock=released`.
Candidate-Admission's selector is not yet exercised through that public
Service fixture, and append-after-resume failure remains non-atomic and
requires reconciliation; Phase 92 owns shared atomicity.
The source-tracked Cozy Candidate-Admission fixture contains no `WORKFLOW`
`OPERATION` declaration for completion, so it produces no matching Provided
API ABI for the real public Candidate Service binding. This fixture
declaration gap is recorded without changing Cozy or treating it as a
missing Cozy language feature.
The trusted `WorkflowProtocolV1.resumeIssuedWorkOrderC` adapter checks a
separate-turn typed result and reprojects the stored Continuation before
opening a fresh UnitOfWork. A changed result revision or persisted Operation
identity is rejected before execution; valid resume is one-shot.
`P771-WORKORDER-RESUME-VAL-023` passed 20 focused protocol/runtime tests with
the shared SBT lock released. The follow-up
`WorkflowProtocolV1.resumeIssuedWithInstanceGuardC` loads and validates the
current WorkflowInstance, matching its trusted Component/Handle and active
suspension identity plus completion/evidence contract identities before
resume. `P771-INSTANCE-GUARD-VAL-024` compiled but hit a non-qualified test
`ComponentId`; after fixture correction, `P771-INSTANCE-GUARD-VAL-025` passed
12 focused tests with the shared SBT lock released. The next uncommitted
`IssuedWorkOrderPersistence` port saves an exact projected issue before it is
returned, allows identical retry, rejects conflicting retry, and reloads the
issue before the read-only WorkflowInstance resume guard. Failed issue write
leaves the claim recoverable; missing issue fails before UnitOfWork creation.
`P771-ISSUED-WORKORDER-VAL-026` passed 12 focused tests with the shared SBT
lock released. The follow-up `IssuedWorkOrderPersistence.LocalJson` adapter
uses the Protocol V1 JSON codec in a caller-owned local directory; a reopened
adapter loads the same issue, permits identical retry, rejects conflicting
retry, and supplies the valid resume. `P771-WORKORDER-LOCAL-VAL-027` passed 12
focused tests with the lock released. A separate-JVM restart, power-loss
recovery, atomic three-store commit, opaque ContextSnapshotReference mapping,
and resumed WorkflowInstance history append remain unproven. Concrete
`sm-workflow` transport ingress and full recovery remain open.
Full fixture progression, indeterminate-store
reconciliation, and independent Phase acceptance remain open.

## CWF-77-06: Durable Continuation, Resume, and IoC

Stage Status:
- Current status: DONE on the loose runtime boundary; durable claim ownership, recovery finalization, runtime IoC, committed-but-incomplete failure handling, typed external result admission, and reopened local JSON Continuation/WorkOrder stores have focused proofs; cross-process WorkflowInstance recovery belongs to sm-workflow Phase 2 and shared atomicity to Phase 92
- Owner: CNCF StateMachine / Workflow runtime owner
- Update rule: Close only after the explicitly non-atomic post-commit suspension path reports incomplete persistence without treating it as rollback, external claim begins after successful persistence, resume uses a fresh UnitOfWork, and an IoC-injected Continuation SPI adapter can return a typed result.

- [x] Distinguish Required SPI, Provider SPI, Continuation Protocol, and Continuation SPI Projection.
- [x] Create durable Continuation carrying run identity, suspended Action/SPI identity, expected revision/ContextSnapshot, typed result contract, Completion/Evidence contract, and minimum context references; bind WorkflowInstance identity through the issued WorkOrder/Handle. Cross-store recovery is not required here.
- [ ] Persist StateMachine/WorkflowInstance progression and suspension atomically in the active UnitOfWork before making Continuation work externally claimable. (Deferred to [Phase 92](phase-92.md); explicitly not a Phase 77.1 closure condition and not implemented.)
- [x] Publish or expose claimable Continuation work only from an after-commit boundary; rollback exposes none.
- [x] Prove the loose baseline reports a committed UnitOfWork with failed or indeterminate post-commit Continuation persistence as incomplete; do not present it as an atomic rollback or hand out work from that failed result.
- [x] Inject Skill/Human/UI/remote adapters through ComponentFactory/provider construction rather than direct Workflow-to-Skill calls at the generic adapter boundary.
- [x] Start a fresh UnitOfWork for resume and validate the declared result type before completing the runtime continuation.
- [x] Reject duplicate and incompatible runtime resume attempts fail closed.
- [x] Prove recreated CNCF runtimes and reopened local persistence adapters retain the suspension/claim boundary and reject reissue of an already completed external Action. Cross-process restart and application-store recovery belong to sm-workflow Phase 2.

The uncommitted `ContinuationSpiAdapter[W, S, R]` translates an
application-owned external submission into a typed `ContinuationResult[R]`.
`ComponentFactory.bindContinuationSpiAdapterC` binds it to the Component's
injected runtime and issued-WorkOrder persistence. The bound entry verifies
the persisted claim and exact issued WorkOrder before invoking the adapter,
then applies the existing result-type, completion, and evidence admission.
The real Cozy Judgment fixture exercises this path through runtime recreation
and rejects a second adapter call after completion. Initial
`P771-EXTERNAL-ADAPTER-VAL-046` stopped at a test-local duplicate variable;
after correction, `P771-EXTERNAL-ADAPTER-VAL-047` passed 11/11 focused tests
with `lock=released`. This is a typed submission boundary, not an automatic
Skill/remote execution scheduler, process-restart durability proof, or full
Phase acceptance. The adapter injection item has focused proof; process-restart
integration is deferred to sm-workflow Phase 2 rather than this checklist.

`ContinuationRuntimePersistence.LocalJson` persists the full Continuation
record, including private claim ownership and completed state, under a
caller-owned directory. `P771-LOCAL-RECOVERY-VAL-048` passed 17/17 runtime
tests after the store was reopened. The real Candidate-Admission fixture now
reopens both that store and `IssuedWorkOrderPersistence.LocalJson` before
external submission and one-shot progression; `P771-CANDIDATE-REOPEN-VAL-049`
passed 26/26 focused tests with `lock=released`. The fixture retains an
in-memory WorkflowInstance store, so this is neither a separate-JVM restart
nor a cross-store atomicity proof. The focused runtime-recreation item remains
open until Phase acceptance; cross-process recovery belongs to sm-workflow
Phase 2 and the shared transaction belongs to Phase 92.

### Runtime implementation record

`ContinuationRuntime` provides the CNCF continuation boundary: it publishes
only from the `UnitOfWork` post-commit hook, rejects claim before publication,
and resumes a claimed continuation exactly once through a fresh UnitOfWork.
`CWF-77-06B` adds a persistence port whose claim, release, and completion
transitions carry an opaque ownership token. It also adds ComponentFactory
runtime injection and a recovery-only finalization operation for a committed
UnitOfWork whose post-commit persistence completion initially failed. The
focused tests cover pre-commit rejection, incompatible typed-result release,
stale-token rejection, fresh-UnitOfWork failure release, post-commit recovery,
and Component/Factory runtime bootstrap.

The uncommitted `CWF-77-06D` Slice releases a persistent claim when a fresh
resume UnitOfWork is known to have aborted before commit or its factory fails
before creating the UnitOfWork. A committed post-commit completion failure
retains the claim for `finalizeC`. If claim release itself fails, the original
resume failure remains authoritative and both causes remain inspectable.
Focused `ContinuationRuntimeSpec` execution `P771-CWF06D-VAL-006` passed ten
tests with the shared SBT lock released. Independent focused re-review closed
the error-precedence and validation-provenance findings for this Slice. This does not
establish WorkflowInstance/Continuation atomic persistence or an external
adapter and does not close CWF-77-06.
The next uncommitted guard compares a submitted claim's Continuation with the
persisted record and rejects an incomplete typed result before opening a fresh
UnitOfWork. `P771-RESUME-ADMISSION-VAL-019` stopped before SBT startup because
the receipt helper received a shortened intake-parent path. The subsequent
`P771-RESUME-ADMISSION-VAL-020` passed 28 focused runtime/Provided-ABI tests
after the missing-claim guard was added; this is development evidence only,
not acceptance of an external adapter or the Phase.
The trusted internal `recoverClaimC` lookup fails closed without a valid
persisted `Claimed` record. `P771-CLAIM-RECOVERY-VAL-021` failed compilation
because the three concrete implementations lacked `override`; after that
correction, `P771-CLAIM-RECOVERY-VAL-022` passed 28 focused tests with the
shared SBT lock released. This is recovery through the fixture's shared
persistence port, not proof of disk/process restart, an external adapter, or
full WorkflowInstance recovery.
Focused development execution `P771-CWF06-LOOSE-DEV-001` passed all twelve
`ContinuationRuntimeSpec` tests. New fault fixtures show that a rejected
post-commit `createC` leaves the UnitOfWork `Committed` and reports failure,
while a write-then-fail `createC` reports failure with a stored `Available`
continuation. The latter is an indeterminate-store reconciliation case, not
proof that a second runtime cannot claim it. No WorkOrder may be returned from
the failed commit result. This is focused development evidence only; the
unchecked acceptance item still needs the admitted end-to-end projection path.
Focused development run `P771-CWF06-ABORT-DEV-006` subsequently passed 39
tests across `ExecutionPlanExecutorSpec`, `CandidateAdmissionProducerAbiSpec`,
`CandidateAdmissionRouterSpec`, and `ContinuationRuntimeSpec`, including
pre-commit staged-identity cleanup and retry. It is not a Phase acceptance
receipt, independent review, or external-adapter proof.
The shared-transaction acceptance item is Future Development Candidate DP-01,
owned by Phase 92.

Development candidates retained after clean re-review:

- `DC-CWF77-06B-01`: resolved in the uncommitted `CWF-77-06D` Slice by the
  throwing-factory release-and-retry scenario.
- `DC-CWF77-06B-02`: freeze the `ContinuationRuntimeSource(None)` versus
  Factory fallback precedence policy before changing the IoC behavior.

### CWF-77-06C shared-transaction deferral (DP-01)

Atomic WorkflowInstance progression plus durable continuation creation is not
admitted by the current contracts. `WorkflowInstancePersistence` is closed to
`create`, `load`, and `append`; its conformance test asserts that exact SPI
shape. `UnitOfWorkResource` is non-transactional and has only terminal
resource release. `CommitParticipant` exists as a type, but the current
`UnitOfWork.commit` prepares and commits only its `EventEngine` and has no
registration path for a WorkflowInstance/Continuation participant;
`TransactionContext.prepare/commit/abort` are no-op hooks. Therefore neither
an ordered `append` then `createC` pair nor a post-commit continuation callback
is an atomic substitute. An aggregate atomic-transition store alone would
cover only its own records unless it also joins the active UnitOfWork's
transaction boundary. Further, `EventEngine.noop` appends to `EventStore` and
then commits `DataStore` separately; `EventStore` exposes no transaction handle.
Simply registering another `CommitParticipant` cannot make that configuration
atomic. The 2026-09-24 priority decision assigns the versioned CNCF-owned
shared-transaction extension, UnitOfWork enlistment, rollback, commit
failure/indeterminacy, and restart recovery to [Phase 92](phase-92.md). This
is Future Development Candidate DP-01, not a Phase 77.1 blocker. No Cozy
change is required or authorized.

`CWF-77-06C1` adds a versioned, pure suspension-intent admission boundary in
`WorkflowInstanceAtomicTransitionV1`. It derives the next immutable
WorkflowInstance record through the closed append rules and rejects an
unsupported schema, missing suspension, foreign Continuation identity,
already-claimed Continuation, or stale revision. It neither writes persistence
nor enlists a transaction, and it does not yet prove the WorkflowInstance/run
identity association or close the atomicity item. Its focused
SBT invocation `P771-CWF06C1-DEV-001` passed all seven
`WorkflowInstancePersistenceSpec` tests with the shared lock released. This
validates only pure admission behavior, not atomic persistence, UnitOfWork
enlistment, or recovery. The Slice remains uncommitted and is a Phase 92
candidate, not Phase 77.1 acceptance evidence.

`CWF-77-06C2` adds an opt-in `AtomicWorkflowSuspensionEngine` contract as the
single `EventEngine` commit owner for WorkflowInstance history, Continuation
state, and the active UnitOfWork's event/data effects. The new
`UnitOfWork.stageAtomicSuspensionC` admits an intent only through that engine;
the current split-commit `EventEngine.noop` fails closed before staging.
Focused `WorkflowInstancePersistenceSpec` execution `P771-CWF06C2-DEV-001`
passed seven tests with the shared SBT lock released. This is a contract and
negative-path proof only: no conforming shared-transaction engine, durable
backend, prepare/commit/abort proof, or recovery proof exists yet. Keep this
Phase 92 candidate uncommitted and out of Phase 77.1 closure.
