# Phase 88 S3: paired suspension and durable Continuation claims

Date: 2026-09-29
Status: bounded producer slice focused/regression-validated; Phase 88 remains in progress
Consumer: sm-workflow Phase 2

## Implemented

WorkflowInstanceDataStorePersistence.suspendC uses existing pure suspension
admission and canonical EntityConditionalTransitionDataStore for one guarded
instance-history update and exact Available Continuation creation. Both
collections must be distinct explicit EntityStore collections in the same
borrowed datastore. Configuration identity, current record bytes and expected
revision are checked; cross-store or missing compound capability fails closed.
The canonical provider owns the transaction, schema and resource. No two-call
fallback, SQL/JDBC implementation, public Operation or alternative engine.

WorkflowContinuationDataStorePersistence implements the existing Continuation
SPI through canonical atomic Entity CAS with authoritative readback. Available
creation starts positive storage revision one. Claim/release/complete advance
that storage counter independently of immutable StateMachine expected revision.
One claim winner receives a durable token; release/completion require that exact
token and Claimed status. Completion replay and old/foreign/missing tokens fail.
No expiry, automatic release or uncertain external-effect replay is inferred.

Applied-but-unadmittable readback is transaction-indeterminate, not a safe retry
failure. Reopening canonical storage recovers actual committed records. No
event, issued payload, application evidence or external effect is staged by this
slice. The unchanged instance create/load/append SPI and S2 Factory binding
remain available; the new compound method is a borrowed host composition API.
The Continuation adapter is not yet bound through production Factory/runtime.

## Executable specification

WorkflowInstanceDataStoreSuspensionSpec executes ten examples:

1. Exact paired history and claim token survive SQLite resource close/reopen
   with a fresh separately admitted ABI catalog; completion is once-only.
2. Duplicate Continuation rolls back the instance; divergent and stale current
   records cannot produce an orphan.
3. Record rollback is proved at GuardAdmitted, SuccessorPrepared, RootPrepared,
   BeforePublish and rejected pre-commit checkpoints.
4. Independent SQLite suspension writers admit exactly one pair with no orphan.
5. Independent SQLite claim writers admit exactly one durable token.
6. Exact-token release/reclaim/completion, old-token and replay rejection retain
   immutable StateMachine revision and advance only the Entity counter.
7. Malformed envelope/JSON, policy drift and revision zero reject without repair.
8. Revision advancement between adapter read and compound commit is rejected
   without overwriting the intervening admitted history or creating an orphan.
9. Committed malformed authoritative readback is transaction-indeterminate for
   both paired suspension and claim; canonical recovery observes committed facts.
10. Same/non-Entity/missing collections, unsupported provider, foreign/claimed
    Continuation, null intent and cross-store delivery reject without progression.

Schema preparation can precede a transaction: these examples prove record
atomicity, not absence of schema DDL. Resource reconstruction is within one
process, not separate-process launcher restart. No AI/command is run in a write
transaction. No source changed after final successful focused validation.

## Validation

All executed attempts used registered cncf_command_runner, gpt-5.6-luna /
medium, receipt-first intake and the shared SBT serial wrapper.

- 239: product compile failed on an accidental patch-prefix character; repaired.
- 240: test compile failed on fixture constructor/import errors; repaired only
  EntityCollectionId arity and the instance ContinuationIdentity alias.
- 241: 22/22 focused examples passed; two boundary examples and conservative
  Continuation readback classification were added afterward.
- 242: final sources passed 24/24 tests in three suites, 0 failed/canceled/pending:
  ten S3, seven S1 provider and seven SQLite conditional-transition examples.
  SBT/wrapper exit 0 and lock=released.
- 243: broader Component/Workflow/datastore/UnitOfWork regression was not started.
  Separate platform permission review timed out before process creation;
  no terminal wrapper marker, receipt or regression result exists.

Current source SHA-256, verified against receipt 242:

- WorkflowInstanceDataStorePersistence.scala:
  244ec83845b3a2d41d96b7a4d6e512f409c92db3a30726249fa7fdb6ad961024
- WorkflowContinuationDataStorePersistence.scala:
  abae4fa722e6cb1e9a146c43bd2042b5691722bad2221aaa4d0ca0db3517e29f
- WorkflowInstanceDataStoreSuspensionSpec.scala:
  57c3dca334b295d8b0c948acbfab179de24f3183682c4873b05d04ca3e58bd52

S2 source hashes and S1 codec/spec remain unchanged. Subsequent documentation
is outside the captured validation tree. This is focused producer evidence,
not wider/full-CNCF regression, production, release or commit acceptance.

## Execution-infrastructure stop

243 runner: registered cncf_command_runner, gpt-5.6-luna / medium,
/root/sbt_receipt_d1179f11f4801e23ac22.
Preflight accepted the existing attributable standing authority and exact scope.
Selected logical argv:
`["--batch", "testOnly org.goldenport.cncf.component.* org.goldenport.cncf.workflow.* org.goldenport.cncf.datastore.* org.goldenport.cncf.unitofwork.*"]`.

Scoped execution submission returned:

> The automatic permission approval review did not finish before its deadline.
> Do not assume the action is unsafe based on the timeout alone.
> You may retry once, or ask the user for guidance or explicit approval.

This is a platform review timeout, not missing user authority or a safety
prohibition. The stricter cncf-command-execution/registered-role single-attempt
stop contract was followed: no retry, new envelope, alternate agent or parent
execution. Intake remained mechanically unconsumed; the parent retires the
delegation in its ledger without claiming execution. Resume needs a permitted
platform recovery consistent with that contract, not repeated chat approval.
This is execution infrastructure, not a Phase repair, Hygiene or candidate item.

Retained accepted request/intake:
`/tmp/skill.cncf.d/cncf-command-execution-73bc9f58ebf2809928ce96aedddc948b024ae55291e5093801fb6e6ef43ad469-bd3a22ff9cd64e238be25b8a72d1f834/command-execution-request.json`.
`/tmp/skill.cncf.d/cncf-command-execution-73bc9f58ebf2809928ce96aedddc948b024ae55291e5093801fb6e6ef43ad469-bd3a22ff9cd64e238be25b8a72d1f834/command-execution-intake-sha256-2e92b701dbeb1f745b0cd25b08e8e9eb74c10a4ff3bdb13b71c5678385a6cb12.json`.

## Open boundaries

This does not implement AtomicWorkflowSuspensionEngine, active UnitOfWork/event
enlistment, issued-work/evidence codecs or joint completion/history recovery.
An independent pair transaction is not the required full prepare/commit/abort
boundary. Migration/retention policy identities are checked, not executed policy
mechanisms. Production Factory Continuation binding, shared service/CLI/server/
MCP, separate-process restart, resident references and Phase 87 visibility remain
open. No downstream artifact refresh/consumption, publishLocal, skill changes,
deployment, staging, commit, push or remote publication occurred.

See [Phase 88](../../../phase/phase-88.md).

## Recovery check after execution-contract update

On the subsequent go-ahead, the installed cncf-command-execution timeout
exception and deployed cncf_command_runner definition were inspected. They now
distinguish an explicitly retry-permitted pre-process review timeout from a
substantive denial and admit one same-runner, same-retained-request submission.

The original 243 intake still has no execution-start marker or receipt outputs.
The parent reserved the one-retry budget and resumed the same canonical runner
without changing its intake, command, route or authority. The runner returned
EXECUTION_REQUEST_NOT_RETAINED: its earlier definition had removed the original
structured execution request from persistent storage before platform submission.
It did not re-render or reconstruct the request, submit a second platform
execution request, start SBT, or create a new attempt. The reserved retry was
not used. Existing ledger/submission history was preserved, not reset.

The current exception cannot recover a missing retained request. Re-reading the
verified unconsumed intake in that same runner would be a distinct, narrowly
scoped recovery exception: it needs explicit direction before use. It must not
be represented as another approval of SBT, a safety denial override, an executed
regression, or a reason to select a replacement runner.

## User-authorized limited recovery completed

The user explicitly authorized limited recovery with "限定復旧して" in native
turn 01a0eb56-b9b0-7c80-a56b-40f4f75e000d. This permitted only the original
unconsumed intake to be re-read by the same runner, stored as a complete
structured execution request, then submitted once unchanged. No new runner,
intake, attempt, route, command scope or permission mode was introduced.

The same registered cncf_command_runner (gpt-5.6-luna / medium),
/root/sbt_receipt_d1179f11f4801e23ac22, completed that one resubmission.
The parent independently verified its immutable command-execution receipt.
Invocation SMWF-P2-CNCF-P88-S3-REGRESSION-20260929-243 reached:
sbt_exit=1, wrapper_exit=1, lock=released. The permission-review stop is resolved.

The command failed before tests because the project-owned Cozy development
classpath was stale:
"/Users/asami/src/dev2025/cozy/target/cozy.d/runtime-classpath.txt".
The observed generator diagnostic requests cozyExportRuntimeClasspath in the
Cozy checkout. No tests ran; summary test/suite counts are null, not a successful
zero-test run. This is a build/generator prerequisite failure, not a product test
failure or another permission denial. Focused-242 remains 24/24 historical
evidence for the unchanged sources.

The first permission-review submission never created a process; this limited
resubmission executed and consumed the original artifacts. Its retry budget is
now used, with original submission/ledger history preserved. No artifact or
ledger reset occurred. The three S3 source digests still match focused receipt
242; later documentation updates are outside receipt 243's captured tree.
The limited recovery is complete. It did not refresh Cozy, publish an artifact,
edit source, re-run tests, stage/commit, or create another SBT attempt.
The next ordinary validation prerequisite is the exact project-owned Cozy
classpath task followed by a newly admitted regression attempt, not reuse of 243.

## Classpath prerequisite and regression closed

On the next go-ahead, the existing immutable Cozy validation-only authority
anchor from prerequisite 231 was verified and reused, not reissued or widened.
The project-owned task definition writes runtime-classpath.txt and runtime-version.txt
under Cozy target/cozy.d; it invokes Runtime/fullClasspath. Existing unrelated
Cozy dirty source/documents were preserved. No source edit or publication was
selected. Every command ran through a fresh receipt-first intake and registered
cncf_command_runner with the shared serial SBT wrapper; the completed 243
attempt was never reused.

- 244: cozyExportRuntimeClasspath completed with SBT/wrapper exit 0 and
  lock=released. The parent verified its receipt before selecting regression.
- 245: Component/Workflow/datastore/UnitOfWork regression completed 124 suites,
  1024 tests succeeded, 0 failed, 8 canceled, 5 pending, no aborted suites.
  The eight canceled examples are existing opt-in live MySQL checks (seven
  datastore, one UnitOfWork provider-parity). The five pending examples are
  two ClientComponent HTTP cases, one SQLite columns case and two UnitOfWork
  placeholder examples. They are not executed acceptance.

The parent verified both receipts and observed summary/log counts, then checked
current S3 sources against receipt 245's captured tree. S2 bootstrap source
identities and the other-owned SplitPhase definition remain unchanged.
No target repository edits occurred during either live invocation. Subsequent
Phase/notes/journal updates are not part of the captured validation tree.
This is the selected producer regression, not whole-CNCF full-test, release,
commit or downstream application acceptance.

The permission-review and stale-classpath stops are now closed. P2-U06 has
focused and regression evidence; P2-U05/D03 remain unchecked. Next is typed
issued-work/evidence and active UnitOfWork joint prepare/commit/abort composition,
then production binding and separately admitted downstream artifact/launcher
recovery. No AtomicWorkflowSuspensionEngine capability, full completion/history
transaction, migration/retention execution or separate-process restart is
claimed from these record-transaction tests. No publishLocal, new skill
deployment, commit, push or remote publication occurred.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/09/2026-09-29-phase-88-paired-suspension.md.gz`
