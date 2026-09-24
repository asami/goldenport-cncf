# Phase 77.1 - StateMachine ActionExecution, Provider Runtime, and Durable Continuation

status=closed
closed_at=2026-09-25
repository_full_suite=deferred-not-run
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-77.2
aggregate_validation_sequence=["PHASE-77","PHASE-77.1","PHASE-77.2"]
split_from=[Phase 77](phase-77.md)
predecessor=[Phase 77](phase-77.md)
successor=[Phase 77.2](phase-77.2.md)
planned_at=2026-09-21
depends_on=[Phase 77](phase-77.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-statemachine-api-spi-runtime-and-skill-driven-workflow)
checklist=[Phase 77.1 Checklist](phase-77.1-checklist.md)

## Purpose

Phase 77.1 consumes the accepted `CWF77-FOUNDATION` handoff from Phase 77 and
implements the canonical StateMachine execution path: pure selection of an
admitted Action, lowering through `ExecProgram[UnitOfWorkOp, ActionExecution]`,
Provided API dispatch, Required SPI Provider resolution, durable suspension,
and fail-closed fresh-UnitOfWork resume.

It is the middle child of the Phase 77 split. It must not reinterpret Cozy CML,
replace the generated ABI, or define the Skill-facing protocol. Its closure
freezes the runtime handoff consumed by Phase 77.2.

CWF-77-04C is the accepted receiver for Cozy Phase 73's named fixture
`src/test/resources/modeler/candidate-admission-producer-abi.json`, a separate
versioned Candidate-Admission producer ABI. This subwork does not reopen or
alter the closed Cozy 62.1–62.3 `GeneratedWorkflowAbi` contract. Receiver
acceptance is recorded in `3006494c`.

## Incoming authority handoff

Input: Phase 77's accepted generated ABI admission, ComponentFactory metadata,
and independently durable WorkflowInstance identity/revision/history contract.
Action: close Phase 77. Output: frozen Provider/runtime inputs. Owner: CNCF
Phase 77. The handoff is invalidated by an incompatible ABI, source identity,
or persistence contract; this Phase must stop rather than infer a replacement.

## Scope

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-04 | Deterministic StateMachine progression selects only admitted Actions and produces `Completed`, `Suspended`, or `Failed` through the canonical program. | done on the focused runtime boundary; real Candidate fixture proves interpreted suspension, admitted Judgment routing, one-shot resume, and StateMachine-selected closing Action |
| CWF-77-04C | Receive Cozy Phase 73's named Candidate-Admission producer-ABI fixture as a separate versioned ABI, verify its schema, provenance, and compatibility, and record receiver acceptance without altering closed Cozy 62.1–62.3 `GeneratedWorkflowAbi`. | implemented (`3006494c`) |
| CWF-77-05 | Provided API dispatch and Required SPI Provider resolution use ComponentFactory construction and the canonical UnitOfWork execution infrastructure. | done on the focused runtime boundary; public Service Start/completion, selected closing Action, and terminal WorkflowInstance append are proved in the synthetic fixture; real Candidate completion declaration belongs to the downstream fixture |
| CWF-77-06 | Under the loose transaction baseline, expose Continuation work only after UnitOfWork commit and successful continuation persistence; resume validates a typed result in a fresh UnitOfWork with stale/duplicate rejection. | done on the loose runtime boundary; typed external adapter and reopened local JSON Continuation/WorkOrder stores are exercised; cross-process WorkflowInstance recovery belongs to sm-workflow Phase 2 and shared atomic persistence to Phase 92 |

## Closure contract

- State/Guard/Transition selection remains pure and does not infer semantics
  from names, effect labels, or provider placement.
- Every admitted executable Action uses the Phase 64.2 canonical
  `ExecProgram[UnitOfWorkOp, ActionExecution]`; no accepted Action executes
  through both a direct callback/effect path and the UnitOfWork path.
- ComponentFactory constructs component-specific Providers for generated
  Required SPI operations; Providers, not Action-specific factory overrides,
  implement the operation contract.
- `Completed`, `Suspended(Continuation)`, and `Failed` preserve typed result,
  identity, provenance, authorization, and UnitOfWork boundaries.
- This Phase uses a deliberately loose transaction boundary: the active
  UnitOfWork commits first, and a Continuation becomes externally claimable
  only after its post-commit persistence succeeds. A failed post-commit write
  is reported as committed-but-incomplete; the caller must not hand out work
  from that failed result, and an indeterminate store outcome may need
  reconciliation. It must not be described as an atomic rollback. Resume starts a fresh
  UnitOfWork and rejects stale, duplicate, expired, invalid, or incompatible
  typed results fail closed. Joint WorkflowInstance/Continuation/UnitOfWork
  atomicity belongs to [Phase 92](phase-92.md) and is not a condition for
  Phase 77.1, Phase 77.2, or the first `sm-workflow` vertical slice.
- Phase 77.1 validates the CNCF runtime boundary with focused, deterministic
  fixtures, including runtime recreation and reopened persistence adapters.
  Cross-process restart, power-loss recovery, and combined application-store
  recovery are integration/hardening checks in sm-workflow Phase 2, not
  prerequisites for this runtime handoff. Deferring those checks does not
  relax typed identity, claim, commit-before-publication, or duplicate-resume
  rules.

The closure handoff is `CWF77-RUNTIME`: the admitted ActionExecution,
ExecProgram, Provider, Continuation, and resume-admission contract. Phase 77.2
consumes it to build Skill projection, JSON codecs, the real fixture proof, and
the consumer handoff.

## CWF-77-06 runtime boundary

The CNCF-owned `ContinuationRuntime` fills the continuation runtime boundary
without changing Cozy. `CWF-77-06B` (`f3fa5487`) adds the
`ContinuationRuntimePersistence` port, opaque claim ownership tokens,
recovery-only finalization after a post-commit persistence failure, and the
Component/Factory IoC hook. It stages publication through `UnitOfWork`
post-commit callbacks, rejects pre-commit claim, validates one typed resume in
a fresh `UnitOfWork`, and rejects stale, duplicate, incompatible, and
wrong-owner resumes. Focused validation `P77.1-CWF-77-06B-VAL-005` passed the
runtime and ComponentFactory bootstrap specifications with the SBT lock
released. This is not yet atomic `WorkflowInstance` progression persistence
or an external continuation-adapter implementation.

The uncommitted `CWF-77-06D` Slice releases a persistent claim after a
known pre-commit abort or a failed fresh-UnitOfWork factory, so a recreated
runtime can claim and retry. A committed post-commit completion failure keeps
the claim for recovery-only finalization. A failed release retains both
causes while keeping the original failure authoritative. Focused
`ContinuationRuntimeSpec` execution `P771-CWF06D-VAL-006` passed ten tests
with the shared SBT lock released; this is not Step or Phase closure evidence.

An additional uncommitted resume-admission guard now rejects a claim whose
Continuation differs from the persisted record and rejects an incomplete or
untyped result before creating the fresh UnitOfWork. The mismatched claim
remains owned; an invalid result releases the claim for a later valid retry.
Focused validation `P771-RESUME-ADMISSION-VAL-019` did not start SBT: its
receipt execution helper rejected an incorrectly shortened intake-parent path.
After the missing-claim guard was added, focused validation
`P771-RESUME-ADMISSION-VAL-020` passed 28
`ContinuationRuntimeSpec`/`GeneratedWorkflowAbiSpec` tests with the shared SBT
lock released. This remains development evidence, not Phase closure or an
external-adapter proof.

The next uncommitted slice adds trusted internal `recoverClaimC` lookup for a
`Claimed` Continuation. The persistence-backed runtime rehydrates the private
ownership token from its stored record after runtime recreation; no token is
added to the public WorkOrder. Missing, invalid, available, or completed
records fail closed. The first focused execution
`P771-CLAIM-RECOVERY-VAL-021` failed compilation because three concrete
implementations needed `override`; after that correction,
`P771-CLAIM-RECOVERY-VAL-022` passed 28 focused runtime/Provided-ABI tests
with the shared SBT lock released. This establishes recovery through the
shared persistence port in the fixture, not a disk/process-restart guarantee
or an external adapter.

An uncommitted workflow-plan entry now stages a typed suspension with the
active UnitOfWork and returns it only after commit and post-commit Continuation
persistence succeed. A failed post-commit write returns failure while the
UnitOfWork remains committed; no Continuation is returned from that failed
result. Focused development execution `P771-CWF04-SUSP-DEV-003` passed seven
`ExecutionPlanExecutorSpec` tests. This remains a loose boundary, not a shared
transaction or the complete Cozy reference-fixture proof.

The real Cozy Candidate-Admission fixture now exercises the complete focused
happy path from a Required-SPI Action and Program Provider through interpreted
`Suspended`, post-commit Continuation persistence, WorkflowInstance suspension
append, persisted WorkOrder issue, one-shot Judgment resume, and declared
StateMachine progression. `P771-CANDIDATE-ENDTOEND-VAL-044` passed all 9
`WorkflowInstancePersistenceSpec` tests with `lock=released`. The subsequent
`executeCommittingAfterC` path orders the instance suspension append before
Continuation publication and returns no work when that prerequisite fails.
`P771-CANDIDATE-ORDERED-VAL-045` passed all 17 focused tests across the
fixture and execution-plan suites. The writes remain non-atomic: a later
Continuation write failure can leave a suspended instance needing
reconciliation. Public application ingress is also pending; this is a
development proof, not Phase closure or cross-store atomicity.

The subsequent `P771-CWF06-ABORT-DEV-006` focused development run passed 39
tests across the execution-plan, Candidate-Admission receiver/router, and
Continuation runtime specifications. It also covers pre-commit abort cleanup
of staged Continuation identities and retry. This is development evidence,
not independent Phase acceptance or the missing end-to-end fixture proof.

## CWF-77-05 Provider program boundary

The uncommitted `CWF-77-05C` Slice interprets a
`StateMachineProgramProvider`'s `ExecUowM[ActionExecution]` through the active
`UnitOfWorkInterpreter`; that subtype's direct `execute` route fails closed.
Identical active Provider requests are rejected as cycles, distinct requests
for the same Provider can make finite progress, and nesting is bounded at 64.
Focused development attempt `P771-CWF05C-FIX-DEV-006` passed all 16
`UnitOfWorkStateMachineProviderDispatchSpec` tests with the shared SBT lock
released; independent read-only development re-review found no new concrete
blocker in this Slice. The uncommitted `CWF-77-05D` scenario additionally
runs a `StateMachineRequiredOperationAction` through that bound Provider and
active UnitOfWork; focused attempt `P771-CWF05D-DEV-002` passed both
`StateMachineRequiredOperationActionSpec` scenarios. These checks are not
formal Step acceptance. The current path does not prove purity of legacy
direct Providers or connect the newly produced inbound Provided API descriptor
to typed dispatch, so CWF-77-05 remains open.

The original Cozy source-level `StateMachineApiSpi` supported caller-supplied
Provided operation values, but `WORKFLOW` CML did not declare them and the
closed generated Workflow ABI exposed only a Workflow descriptor. This was a
CML-to-producer gap, not merely a CNCF registration choice. An uncommitted
additive Cozy `WORKFLOW` `OPERATION` declaration now resolves an explicit
qualified Service Operation and emits a separate versioned Provided API ABI;
CNCF accepts it only with the matching admitted Workflow identity, revision,
and source. The closed Cozy 62.3 Workflow ABI v1 remains unchanged. At this
producer/receiver stage, typed inbound dispatch still needed an integration proof; no Action or
Required SPI name is used to synthesize an inbound API.

The uncommitted `CWF-77-05E` development slice now binds component-owned
Provided API programs to admitted Workflow/Service Operation identities at
ComponentFactory bootstrap. A typed Provided request is checked against the
generated identity, revision, input type, and context provenance before its
program runs in the active UnitOfWork; a completed result must match the
declared result type. The focused path explicitly links a distinct Provided
operation to a bound Required SPI program
Provider through the same UnitOfWork interpreter. This does not yet wire a
public Service ingress or prove the full `sm-workflow` fixture progression;
CWF-77-05 remains open.

The development fixture now keeps `beginReview` (Provided) distinct from
`reviewChange` (Required SPI), preventing an accidental name-based shortcut.
Public `sm-workflow` ingress still requires the application-specific
Operation/JSON binding described in `sm-workflow` Phase 1. Phase 77.2 has
started the CNCF common Start/Handle/Continuation/Result types; those partial
types do not make a public Service endpoint or a generic `startWorkflow`
operation.

The uncommitted `CWF-77-05F` slice adds a dedicated
`UnitOfWorkInterpreter.runProvidedCommittingC` entry for an explicitly
declared Provided Operation. A suspended Required SPI is accepted only when
its identity, Action, Operation, input/result types, run, and context match
the admitted Workflow ABI and request. The existing ordinary UnitOfWork
operation still rejects a suspension; only the committing entry stages it,
commits the active UnitOfWork, and returns it after post-commit persistence
succeeds. The focused Cozy 62.3 declaration fixture exercises distinct
`beginReview` and `reviewChange`, commit-before-claim, and typed
fresh-UnitOfWork resume. Focused execution
`P771-PROVIDED-SUSPENSION-VAL-015` passed 28 tests, and follow-up
`P771-PROVIDED-SUSPENSION-VAL-016` passed 12 tests including rejection of
an undeclared Required SPI and a committed UnitOfWork whose post-commit
Continuation write fails. Both released the shared SBT lock. The failed
write returns no Continuation and exposes no claim through that rejecting
store; it is not an atomic rollback or proof for all indeterminate stores.
Follow-up `P771-PROVIDED-SUSPENSION-VAL-017` passed the focused fixture with
a Provided `WorkflowStartResult` type distinct from the Required SPI
`ReviewResult` type; no result-type equality is inferred between the
separate Operations.
Focused execution `P771-PROVIDED-RESTART-VAL-018` passed the same fixture
after recreating the persistence-backed runtime for claim and again for typed
resume. A second runtime could not claim the owned Continuation, and another
recreated runtime could not resume its completed claim. This proves the
claim/resume ownership boundary across runtime recreation for this fixture,
not an external adapter or durable WorkflowInstance progression.
The later focused fixture additionally resumes through a newly recreated
runtime's `recoverClaimC` result, instead of carrying the original `Claim`
object across runtime instances; recovery is rejected after completion.
The subsequent uncommitted `stageProvidedForExternalCommitC` entry separates
Provided evaluation and Continuation staging from commit ownership. A caller
such as the CNCF ActionEngine can commit its own UnitOfWork without invoking
the self-committing entry twice. The fixture rejects external claim before
that commit and after rollback, and allows claim only after the external
commit. `P771-SERVICE-STAGING-VAL-028` passed 28 focused
`GeneratedWorkflowAbiSpec`/`UnitOfWorkStateMachineProviderDispatchSpec` tests
with the SBT lock released. This establishes the staging contract, not a
registered public Service Operation or its request/response binding.
The subsequent uncommitted `StateMachineProvidedApiServiceOperation` binds an
explicit generated Provided Operation to a public Service definition. It
checks decoded Workflow and Operation identity, stages the Provided outcome in
the ActionEngine-owned UnitOfWork, and lets ActionEngine commit before the
Service response becomes externally visible. The focused fixture routes
`beginReview` through `Subsystem.executeOperationResponse`, proves the
Continuation is unclaimable before commit and claimable afterward, and rejects
a failed continuation write without exposing a claim. Run
`P771-PUBLIC-SERVICE-VAL-029` passed 29 focused tests with the shared SBT lock
released. This fixture supplies its own transport codec; a concrete
`sm-workflow` request/response JSON mapping and full reference progression
remain open.
The follow-up fixture rejects a Service request missing its run identity,
then carries a successful Service suspension through post-commit claim,
WorkOrder JSON round-trip, separate-turn Result JSON round-trip, and one-shot
resume on a recreated Continuation runtime. `P771-SERVICE-WORKORDER-VAL-030`
passed 17 focused `GeneratedWorkflowAbiSpec`/`WorkflowProtocolV1Spec` tests
with the shared SBT lock released. The request codec is still fixture-specific;
this does not implement the `sm-workflow` application Start/Result schemas,
append WorkflowInstance progression, or execute the closing Action.
The next fixture persists the issued WorkOrder in the in-memory issue port and
loads a separately seeded, suspended WorkflowInstance before resume. It rejects
a now-unsuspended instance, then accepts the matching result exactly once via
`resumePersistedWorkOrderC`. `P771-SERVICE-GUARDED-VAL-031` passed 21 focused
`GeneratedWorkflowAbiSpec`/`WorkflowInstancePersistenceSpec` tests with the
shared SBT lock released. The WorkflowInstance record is fixture-seeded, not
appended by the Service path; this does not implement application Start/Result
mapping, closing Action selection/execution, or terminal history persistence.
The follow-up Service binding now accepts an explicit application-owned
`SuspensionPersistence` port. After the ActionEngine-owned UnitOfWork commits,
the runtime calls that port before publishing the Continuation. The fixture
creates its suspended WorkflowInstance through this Service path, then issues
the WorkOrder and resumes against the stored record; an instance write failure
returns a Service failure and leaves the Continuation unclaimable.
`P771-SERVICE-INSTANCE-VAL-032` passed 29 focused
`GeneratedWorkflowAbiSpec`/`ContinuationRuntimeSpec` tests with the shared SBT
lock released. This remains loose post-commit persistence: a later Continuation
write failure can leave the instance record without claimable work, requiring
reconciliation. Application Start/Result mapping, closing Action execution,
and terminal history persistence remain open.
The public Service binding now also has an opt-in post-commit response
projection. Its focused fixture claims the persisted Continuation, loads the
Service-created WorkflowInstance, issues a WorkOrder, and returns its Handle
and WorkOrder JSON in the same Start response. Neither an instance-write nor a
Continuation-write failure invokes that projection. Run
`P771-POSTCOMMIT-RESPONSE-VAL-052` passed 13/13 `GeneratedWorkflowAbiSpec`
tests with `lock=released`. The codec remains fixture-specific; actual
`sm-workflow` request/response mapping, closing Action execution, and terminal
history persistence remain open. A post-commit projection failure reports
failure after commit and requires reconciliation under this loose boundary.
`P771-POSTCOMMIT-RESPONSE-VAL-053` passed 13/13 focused tests and additionally
proved that projection failure leaves the committed WorkflowInstance and
claimable Continuation intact; it is not a rollback.
The changed common ActionEngine path also passed 10/10 existing authorization,
observation, and failure-commit tests in `P771-POSTCOMMIT-ENGINE-VAL-054` with
`lock=released`.
The public Service fixture now accepts a profile-selected
`WorkflowStartJsonV1` request instead of an untyped run-ID argument. It binds
the declared Operation and Workflow identity/revision, rejects a missing
request, altered Operation, incompatible payload, and unknown JSON field
before dispatch, then returns the same post-commit Handle/WorkOrder response.
`P771-PUBLIC-START-JSON-VAL-055` stopped on a test-local `Argument` extractor
arity error; after correcting that fixture pattern,
`P771-PUBLIC-START-JSON-VAL-056` passed 13/13 focused tests with
`lock=released`. The fixture still supplies its own application mapping and
does not establish the `sm-workflow` profile schemas.
The uncommitted `WorkflowCompletionServiceOperation` now binds an explicitly
declared completion Operation to that same public Service. Its application
codec decodes `WorkflowResultJsonV1`, then the binding requires the declared
Workflow/result type and the issued WorkOrder's Handle and completion
Operation. After the ActionEngine commit, it reloads the issued WorkOrder,
checks the saved WorkflowInstance suspension, and resumes the persisted
Continuation once through a fresh UnitOfWork. The focused Service fixture
rejects missing or incompatible Result JSON and an unsuspended instance; a
valid submission returns the projected resume response and a duplicate fails.
`P771-PUBLIC-COMPLETION-VAL-057` passed 13/13 `GeneratedWorkflowAbiSpec`
tests with `lock=released`. This is still a loose transaction boundary: a
post-commit resume or response failure cannot roll back the Service commit.
The codec remains fixture-specific; actual `sm-workflow` transport schemas,
closing Action execution, and terminal WorkflowInstance history remain open.
The next uncommitted runtime slice adds `resumeWithProgramC`: a selected typed
`ExecUowM[ActionExecution]` is interpreted in the fresh resume UnitOfWork
before its commit and claim completion. Only `Completed` is accepted at this
single-continuation boundary; a failed or newly suspended closing Action
aborts that UnitOfWork and releases the claim for retry. The first focused
attempt, `P771-CLOSING-PROGRAM-VAL-058`, found a test-local missing Cats
syntax import; after correction, `P771-CLOSING-PROGRAM-VAL-059` passed 19/19
`ContinuationRuntimeSpec` tests with `lock=released`.
`WorkflowCompletionServiceOperation` can now receive a pure, application-
supplied selector for that typed closing program. The Service fixture selects
it from the submitted result and saved suspension before the outer Service
commit; the program itself executes only during the guarded resume in a fresh
UnitOfWork. `P771-CLOSING-SERVICE-VAL-060` passed 32/32 focused public-Service
and runtime tests with `lock=released`, including one execution and duplicate
rejection. This proves an opt-in program path, not automatic StateMachine plan
selection, application transport schemas, terminal WorkflowInstance history,
or cross-store atomicity.
If abort of a failed closing Action also fails, the runtime now retains the
claim instead of exposing an uncertain UnitOfWork for retry. The persistent
fault-injection test in `P771-CLOSING-ABORT-VAL-061` passed with 20/20
`ContinuationRuntimeSpec` tests and `lock=released`. This preserves ownership
for reconciliation; it does not make the loose transaction atomic.
The Candidate-Admission fixture now has an opt-in closing-program selector
that admits the typed Judgment result against its issued WorkOrder, follows
the explicit StateMachine route, checks the declared target against the saved
WorkflowInstance, and selects only the Action bound to that target. A missing
route or target Action fails before the resume UnitOfWork opens. Focused
`P771-STATE-CLOSING-VAL-062` passed 9/9
`WorkflowInstancePersistenceSpec` tests with `lock=released`. This connects
StateMachine-owned selection to the typed closing-program boundary, but the
public Service fixture does not yet use this Candidate-Admission selector and
terminal WorkflowInstance history is still not appended by that Service.
The public completion binding now accepts one selected closing program and
optional next WorkflowInstance record together. It checks the proposed append
against the loaded record before outer commit, rechecks that record before
claim consumption, executes the Action in the fresh resume UnitOfWork, then
appends the selected history entry only after successful resume. The public
Service fixture reaches `Lifecycle.Completed` at revision 2 and rejects a
duplicate submission without another Action or append. The first focused
`P771-PUBLIC-CLOSING-VAL-063` run found a test-local invalid terminal entry
that retained a current progression; after correction,
`P771-PUBLIC-CLOSING-VAL-064` passed 22/22 public-Service and Candidate
fixture tests with `lock=released`. The Candidate selector remains a separate
fixture proof rather than an end-to-end public Candidate Service proof.
Resume and history append are separate durable operations: append failure
after successful resume cannot be rolled back and needs reconciliation. No
Phase 92 atomicity is claimed.
The source-tracked Cozy Candidate-Admission CML fixture has no `WORKFLOW`
`OPERATION` declaration for a completion Service Operation; its generated
Workflow and Candidate-Admission sidecars therefore do not supply the
`GeneratedProvidedApiAbi` declaration required to bind this particular real
fixture through `WorkflowCompletionServiceOperation`. This is a missing
declaration in that fixture, not a demonstrated Cozy language-feature gap.
No Cozy fixture or source is changed here; the public Candidate-Admission
end-to-end proof stops at this explicit ABI boundary.
The uncommitted `WorkflowProtocolV1.resumeIssuedWorkOrderC` trusted adapter
now admits a separate-turn typed result, recovers the private claim, and
reprojects the stored Continuation before opening a fresh UnitOfWork. A
result with a different revision or an issued WorkOrder with altered
persisted Operation identity fails before that UnitOfWork is created; a valid
result resumes once and a duplicate fails. Focused execution
`P771-WORKORDER-RESUME-VAL-023` passed 20
`WorkflowProtocolV1Spec`/`ContinuationRuntimeSpec` tests with the shared SBT
lock released. This does not establish durable storage of the issued WorkOrder,
bind its Handle to a loaded WorkflowInstance, or provide a public Service
ingress. The subsequent uncommitted
`WorkflowProtocolV1.resumeIssuedWithInstanceGuardC` entry validates the
configured WorkflowInstance store, loads the current record, and requires the
trusted Component identity, Handle, active suspension Continuation identity,
and completion/evidence contract identities to match before the same one-shot
resume. Its first focused execution `P771-INSTANCE-GUARD-VAL-024` compiled
but failed in the test fixture's non-qualified `ComponentId`; after correcting
that fixture, `P771-INSTANCE-GUARD-VAL-025` passed 12
`WorkflowInstancePersistenceSpec`/`WorkflowProtocolV1Spec` tests with the
shared SBT lock released. The guard itself is read-only. The next uncommitted
slice adds an `IssuedWorkOrderPersistence` port and
`WorkflowProtocolV1.issueRecoveredWorkOrderC`/
`resumePersistedWorkOrderC`: issue is returned only after the separate store
accepts the exact projected WorkOrder; an identical issue can be retried,
whereas a conflicting one fails. Resume reloads the issue and applies the
WorkflowInstance guard before opening a fresh UnitOfWork. A failed issue write
leaves the claim recoverable, and a missing issue cannot resume.
`P771-ISSUED-WORKORDER-VAL-026` passed 12 focused
`WorkflowInstancePersistenceSpec`/`WorkflowProtocolV1Spec` tests with the
shared SBT lock released. That first store was an in-memory fixture. The
following uncommitted `IssuedWorkOrderPersistence.LocalJson` adapter uses the
existing Protocol V1 JSON codec and an atomic create-only file name in a
caller-owned local directory. The focused fixture opens a new adapter over
the same directory, reloads the exact issue, accepts an identical retry,
rejects a conflicting retry, and resumes through that reopened adapter.
`P771-WORKORDER-LOCAL-VAL-027` passed the same 12 focused tests with the SBT
lock released. This is a local-file/reopened-adapter proof, not a separate-JVM
restart or power-loss test. The Continuation, issued-WorkOrder, and
WorkflowInstance stores are not atomic; the opaque ContextSnapshotReference
is not compared to a runtime snapshot; and resume does not append
WorkflowInstance progression. Those gaps keep the full recovery claim open.
This is a development slice, not a public Service endpoint,
complete Build/Test/Commit progression, external adapter, or Phase closure.

## Development candidates from CWF-77-06B

- `DC-CWF77-06B-01`: resolved in the uncommitted `CWF-77-06D` Slice by a
  focused throwing-factory release-and-retry assertion. No retry policy or
  scheduler was added.
- `DC-CWF77-06B-02`: decide whether a Component that implements
  `ContinuationRuntimeSource` but returns `None` must suppress, or fall back
  to, its Factory source. Owner: ComponentFactory/Continuation IoC contract.
  Do not infer a fallback rule from the current precedence implementation.

## CWF-77-06C deferral: shared atomic-persistence ABI

Jointly committing WorkflowInstance progression and durable continuation
creation cannot be implemented from the current admitted ABI.
`WorkflowInstancePersistence` is explicitly a contract
only and exposes only `create`, `load`, and `append`; its conformance example
asserts that closed three-operation shape. `UnitOfWorkResource` is explicitly
non-transactional and releases resources only after a terminal UnitOfWork
outcome. Neither surface admits a shared prepare/commit/abort participant or
an atomic append-and-continuation transition. A post-commit callback would
reintroduce the split-commit failure that CWF-77-06B deliberately exposes for
recovery rather than calling it atomic.

The existing `CommitParticipant` type is not enlisted by `UnitOfWork.commit`;
that method prepares and commits only its `EventEngine`, while
`TransactionContext.prepare/commit/abort` have no implementation. Consequently,
even a new aggregate store would need an explicit same-transaction UnitOfWork
enlistment and rollback contract before this Phase could claim atomicity across
progression, continuation, and the active UnitOfWork.

Enlistment alone is insufficient: the current `EventEngine.noop` appends to
`EventStore` and then commits `DataStore` as separate calls, and `EventStore`
has no transaction handle. A versioned atomic-transition extension must
therefore require a proven shared transaction domain for the WorkflowInstance,
Continuation, and active UnitOfWork participants; an unsupported or mixed-store
configuration must fail closed before publishing claimable work. Prepare
rejection, commit failure/indeterminacy, rollback, and recovery need executable
proof against that domain, not only an in-memory sequence of callbacks.

The 2026-09-24 priority decision moves this shared-transaction capability to
[Phase 92](phase-92.md), after a stable first `sm-workflow` vertical slice.
Phase 77.1 keeps the loose post-commit Continuation path and must explicitly
report its split-commit limitations. The uncommitted pure suspension-intent
admission and opt-in `AtomicWorkflowSuspensionEngine` prototypes passed focused
`WorkflowInstancePersistenceSpec` runs `P771-CWF06C1-DEV-001` and
`P771-CWF06C2-DEV-001` (seven tests each), but are Phase 92 candidates, not
Phase 77.1 closure evidence or a production transaction domain. No conforming
shared-transaction engine or durable backend exists yet. Do not simulate
atomicity in `ContinuationRuntime`, `UnitOfWorkResource`, or an in-memory
implementation. Cozy remains out of scope and unchanged.

## Planning and validation

Estimated duration is 330 minutes (270-420), within the six-hour target and
eight-hour ceiling. The ABI/persistence decisions are frozen by Phase 77, so
this is bounded-settled execution with recommended parent profile
`gpt-5.6-terra / high` and standard reasoning-mode policy.

This Phase is aggregate-deferred. It requires focused validation, independent
review, closure ledger, and release commit, while Phase 77.2 runs the sole
repository-wide `sbt --batch test` for the serial sequence.

Phase 77.1 completes those own closure steps independently; neither Phase
77.2 implementation nor its later aggregate test is a prerequisite for
closing Phase 77.1. The aggregate deferral changes only full-suite ownership,
not this Phase's development or acceptance boundary.

The 77.1 acceptance tree must exclude the uncommitted Phase 92
`WorkflowInstanceAtomicTransitionV1` prototype. Its references in
`UnitOfWork.stageAtomicSuspensionC` and the atomic-transition scenario in
`WorkflowInstancePersistenceSpec` share files with 77.1 work, so release
selection must be at hunk/scenario granularity. Existing focused runs over a
mixed working tree are development evidence, not validation of a selected
77.1-only release tree. No Phase 92 prototype is accepted by this handoff.

## Prior manual closure audit (2026-09-24)

Status remains planned/open. The focused development runs and the
loose-transaction implementation do not satisfy the closure contract yet:
CWF-77-04 still lacks a complete JudgmentAction-to-suspension reference-fixture
proof. The uncommitted submitted-`JudgmentResult` admission and StateMachine
route entry passed 19 focused tests in `P771-JUDGMENT-ROUTE-VAL-034`, but is
only a pure pre-resume boundary, not that full fixture. Its subsequent
Workflow/Required-SPI/Operation and payload-reference binding passed 19
focused tests in `P771-JUDGMENT-BINDING-VAL-036`; that source-binding entry
alone did not load the persisted WorkOrder or perform one-shot resume.
The subsequent uncommitted `resumePersistedSubmittedC` entry now loads the
issued WorkOrder and couples route admission to the guarded one-shot resume.
Its synthetic matched-fixture validation `P771-JUDGMENT-RESUME-VAL-039`
passed 14 focused tests. That run used a synthetic pairing because the
closed Phase 62.3 `GeneratedWorkflowAbi` cannot admit the real Cozy
`JUDGMENT`/`ADMISSION` Actions. The additive `CandidateWorkflowAbi` now
admits Cozy's actual `OrderProgress`/`workflow-v1` Workflow JSON only when
it matches the separately admitted Candidate-Admission sidecar.
`WorkflowInstancePersistence.bindCandidateDefinitionC` binds that definition
without changing the closed receiver. The persisted WorkOrder test now uses
this real matched fixture and proves one-shot guarded resume.
`P771-CANDIDATE-WORKFLOW-VAL-041` passed 11 focused tests with
`lock=released`; its predecessor `-040` exposed a test-local compile error
that was corrected. The additive `resumeAndAdvancePersistedSubmittedC` path
now checks the declared State target and prospective append before consuming
the claim, then appends the next WorkflowInstance revision after guarded
resume. The real fixture selects `OrderProgress/Complete`, clears the
suspension, and reads the revision-2 record back through the persistence SPI.
`P771-CANDIDATE-PROGRESSION-VAL-043` passed 16 focused tests with
`lock=released`. Resume and append remain separate durable operations: an
append failure after successful resume is not an atomic rollback and may
require reconciliation; Phase 92 owns shared atomicity. CWF-77-04 remains
open for the other ActionExecution and suspension acceptance items.
CWF-77-05 now has a public Service-to-WorkOrder/result-resume focused
path but lacks application transport mapping and full fixture progression;
CWF-77-06 still lacks a full WorkflowInstance end-to-end recovery proof,
although the focused Provided suspension fixture proves persistence-backed
claim/resume across runtime recreation.
The uncommitted `ContinuationSpiAdapter` now supplies a typed external
submission boundary assembled by `ComponentFactory`: it loads the exact
issued WorkOrder and recoverable claim before calling the adapter, admits
the returned typed result, and rejects another adapter call after one-shot
completion. The real Candidate-Admission fixture passed 11/11 focused tests
in `P771-EXTERNAL-ADAPTER-VAL-047`; the first `-046` attempt failed only at
a test-local duplicate variable name. This narrows the adapter gap but does
not provide automatic external execution or full Phase closure.
The uncommitted `ContinuationRuntimePersistence.LocalJson` adapter stores
the complete Continuation record and private claim state in a caller-owned
local directory. A focused test reloads claim ownership and completion through
newly constructed adapters (`P771-LOCAL-RECOVERY-VAL-048`, 17/17). The real
Candidate-Admission fixture also reopens both the Continuation and issued-
WorkOrder JSON stores between suspension, claim, issue, typed external
submission, and one-shot progression. `P771-CANDIDATE-REOPEN-VAL-049` passed
26/26 focused tests with the shared SBT lock released. These are same-process
reopened-adapter proofs, not a separate-JVM restart or power-loss test;
WorkflowInstance persistence in that fixture remains in memory. No atomic
commit across the stores is claimed.
Independent Phase review, closure ledger, and the
release commit are also outstanding. No Phase 92 shared-transaction prototype
or unrelated working-tree change is accepted by this audit.

## Manual closure audit update (2026-09-25)

Phase 77.1 remains open on its own acceptance boundary, not because Phase 77.2
is unfinished. The real Candidate-Admission fixture has now selected a closing
Action from an admitted Judgment result, persisted WorkOrder, declared route,
and target (`P771-STATE-CLOSING-VAL-062`, 9/9). The public Service completion
fixture executes its selected closing program in a fresh resume UnitOfWork and
appends terminal WorkflowInstance revision 2 after one-shot resume
(`P771-PUBLIC-CLOSING-VAL-064`, 22/22). Both are focused development evidence;
they do not constitute independent Phase acceptance. The two fixtures remain
separate: the source-tracked Cozy Candidate fixture has no `WORKFLOW OPERATION`
completion declaration, so it cannot supply that real public Provided API
binding without a producer-fixture change. No Cozy change is made here, and
the gap is not a Cozy language-feature deficiency or a Phase 77.1 runtime
handoff closure condition.

The loose post-commit path still reports failed persistence as incomplete and
does not claim atomic rollback. Reopened local JSON Continuation/WorkOrder
adapters prove retained claim and issued-work identity, but the real Candidate
scenario's WorkflowInstance store remains in memory. Full WorkflowInstance
recovery across restart belongs to sm-workflow Phase 2, not Phase 77.1 closure.
The remaining 77.1 runtime checklist obligations, independent Phase review,
closure ledger, and release commit are still outstanding. Phase 92 alone owns
the shared atomic transaction upgrade; Phase 77.2 owns the later aggregate
full-suite validation and its own Skill/consumer slice.

## Manual closure evidence (2026-09-25)

An isolated candidate at `/Users/asami/src/.cncf-771-acceptance-20260925`
excluded the Phase 92 atomic-transition prototype, its UnitOfWork entry, and
its test scenario. The initial receipt-first `P771-FOCUSED-20260925-1` run
passed 105/105 tests across 11 focused suites. A focused read-only review
identified only private method naming violations in the selected runtime and
JSON support code. After their behavior-preserving mechanical correction,
`P771-FOCUSED-20260925-2` passed the same 105/105 tests with `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released` (receipt SHA-256
`eeaf35589909abc4afe1c8795cf2068c40fa1cc804a2fe4b29416d473c1da792`;
validated tree SHA-256
`ca1eb75e1a383b97c8d80ed804aee5d93470d27950f3edd470e5e3df06d7e030`).
The focused review found no remaining runtime-boundary blocker. This manual
closure deliberately makes no repository-full-suite claim; Phase 77.2 owns
that aggregate test.

The selected code includes preparatory Phase 77.2 protocol/JSON types used by
77.1 ingress and fixture paths. Their inclusion is an interface prerequisite,
not acceptance of Phase 77.2 Skill projection, real vertical slice, or
consumer handoff. The only unchecked checklist item is shared atomic
WorkflowInstance/Continuation/UnitOfWork persistence, explicitly owned by
Phase 92. Cross-process application-store recovery remains an sm-workflow
Phase 2 integration check. No Cozy or Phase 92 implementation is accepted by
this handoff. The release commit must stage the validated source selection
without the Phase 92-only hunks and preserve unrelated dirty planning work.

## Non-goals

- Generated ABI admission, ComponentFactory discovery, or persistence-SPI
  ownership; these remain Phase 77.
- Generic Skill WorkOrder projection, Skill/Codex JSON codecs, reference
  vertical-slice execution, and `sm-workflow` consumer handoff; these are
  Phase 77.2.
- A second Workflow Action algebra, raw callbacks, protocol-mode switches,
  retry scheduling, concrete model selection, default datastore provider, or
  broad orchestration/transport/UI expansion.

## References

- [Phase 77](phase-77.md) and [Phase 77.1 Checklist](phase-77.1-checklist.md)
- [Phase 77.2](phase-77.2.md)
- [Phase 92: shared transaction domain](phase-92.md)
- [Phase 64.2 forced minimum closure](../journal/2026/09/2026-09-21-phase-64.2-forced-minimum-closure.md)
- [Phase 77 common contract decision](../journal/2026/09/2026-09-20-phase-77-common-contract-reconciliation-decision.md)

## Erratum — 2026-09-25 Phase number reconciliation

References above to Phase 92 as the owner of shared Workflow transaction
atomicity retain the original closure text. That planned work was renumbered
to [Phase 94](phase-94.md) when the GitHub Phase 92, Platform Subcomponent CAR
Distribution, was integrated. This changes no Phase 77.1 closure claim.
