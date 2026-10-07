# Phase 88 S5: guarded record completion and retained result

Date: 2026-09-29
Status: bounded producer slice focused/regression-validated; full joint runtime remains open

## Evidence and decision

S4 atomically creates instance history plus an Available Continuation carrying
its immutable issued WorkOrder. The existing canonical conditional successor
supports Create and read-only Bind only. A Bind plus an unconditional side Save
cannot safely guard and update both existing instance and claimed Continuation;
overlapping root/successor side targets are also correctly rejected.

Add an opt-in EntityConditionalUpdateDataStore capability and a successor
Update wrapping the existing validated DataStoreConditionalRoot. Reuse the same
revision/exact-field guards, managed revision progression and change admission.
In-memory uses staged snapshots; canonical SQL locks/guards the second existing
row using the existing conditional-root read, stages its changes in the same
transaction and returns authoritative readback for both updated records.
Preserve Create/Bind and side-effect target rules unchanged. Do not advertise
this capability on a legacy compound-only provider.

## Bounded Workflow composition

Bind explicit component/completion Operation, codecs, admitted catalog and
distinct same-store EntityStore collections. Workflow owns the completion
history entry and exact internal claim token; the public semantic result contains
neither token nor a progression selector. Validate the exact current suspended
instance, issued WorkOrder, claimed Continuation, typed result, required completion
facts and execution-evidence references before writing.

One canonical transaction guards instance revision/policy and Continuation
storage revision/exact claimed payload (including token)/policy/issued bytes.
It appends the host-selected unsuspended history, marks the Continuation
Completed, preserves issued bytes and stores a strict versioned result envelope
including existing typed ExecutionEvidence and the completing instance revision.
No AI, closing program or external command executes in this transaction.

Recovery requires the exact codec/issued contracts, a Completed Continuation
and matching adjacent suspension/completion history. Missing or corrupted retained
results fail closed without retrofit. Applied but unadmittable readback is
transaction-indeterminate; no automatic replay, token reset or claim expiry.

## Frozen source ownership and executable acceptance

CNCF owned paths:
- datastore/EntityConditionalTransition.scala
- datastore/DataStore.scala
- datastore/sql/SqlDataStore.scala
- workflow/WorkflowInstanceDataStorePersistence.scala
- workflow/WorkflowContinuationDataStorePersistence.scala
- workflow/WorkflowCompletionRecordJsonV1.scala (new)
- workflow/WorkflowCompletionDataStorePersistence.scala (new)
- tests: datastore/DataStoreConditionalUpdateSpec.scala (new) and
  workflow/WorkflowCompletionDataStorePersistenceSpec.scala (new)

Source prefixes are src/main/scala/org/goldenport/cncf and
src/test/scala/org/goldenport/cncf respectively. Preserve S2 Factory, S4 issued
adapter/spec, unrelated Phase 96 documents and all other dirty work.
Only Phase 88 and matching sm-workflow Phase 2 notes/journal/checklist are edited.

Executable specs: canonical two-record update parity, invalid/foreign/overlapping
guards, stale second revision and exact-field mismatch, rollback and races;
exact completion/evidence and SQLite resource reopen; missing required result facts;
wrong/foreign/stale token and result rejection; release/reclaim movement between
read and commit; record failure checkpoints; authoritative readback uncertainty;
malformed recovery and single winner/replay rejection.

## Explicit remaining gates

This retains the generic ContinuationResult ExecutionEvidence envelope, not the
separate application AdmissionEvidence payload/scope collection. Application
admission authority still requires its own typed validation and atomic retention.
This is not active UnitOfWork event/data/closing-program enlistment,
AtomicWorkflowSuspensionEngine, production claim admission/Factory shared
service, launcher/server/MCP, separate-process restart, management visibility or
downstream artifact consumption. Policy identity does not execute migration,
retention or lease policy. Public sm-* skills remain semantic-result-only;
legacy cncf-* coexist unchanged. Phase 88 and Phase 2 remain in progress.

## Implementation boundary

The canonical successor Update reuses DataStoreConditionalRoot admission.
Only InMemoryDataStore and canonical SqlDataStore advertise the opt-in existing
successor update capability. Workflow binding rejects a missing capability;
there is no Bind-plus-unguarded-Save fallback. The second SQL guard uses the
existing conditional-root read (including MySQL FOR UPDATE), not a new SQL
grammar. SQLite/in-memory two-record executable acceptance does not prove live
MySQL acceptance. Existing Create/Bind and ownership/overlap rules are preserved.

WorkflowCompletionDataStorePersistence owns no resource lifecycle. Its bound
host owns the completion entry and internal claim token. Strict typed result
codec round-trip and existing WorkflowProtocolV1 admission run before writing.
Both instance history and Continuation completion/result share one canonical
transaction. Continuation guards include storage revision, exact claimed bytes
with token, policy and immutable issued bytes. Result and ExecutionEvidence are
retained together using the existing WorkflowResultJsonV1 inside a strict
versioned completion envelope. An application codec exception is a typed
pre-write failure. Public result JSON contains no token or progression selector.

The existing Protocol permits absent Skill dispatch evidence. Preserve that
compatibility: validate provided dispatch against the exact issued requirement,
but do not invent evidence or add a new mandatory Skill dependency.
Required completion facts and execution-evidence references remain enforced.

Recovery checks canonical bytes, Completed status, exact issued contract and
adjacent suspension/completion history at the saved completing revision, even
after the host adds later history. It is only a query, never an execution
retry or claim reset. Replay cannot append history or duplicate completion.
Applied bad readback is transaction-indeterminate and recovery reads committed
facts without replay.

Canonical exact-value guards currently admit at most 4096 encoded characters.
Oversized issued/claim/policy bytes reject before writing without truncation;
a future indexed/digest guard design is separate work. Arbitrary raw datastore
tampering is not a cryptographic authentication boundary. SQL schema preparation
can precede the record transaction; rollback proof covers records, not absence
of DDL. SQLite reopen reconstructs resources in one process, not a separate
launcher process.

## Observed failures retained

250: terminal SBT failure before test execution because the project-owned Cozy
development runtime classpath was stale. The required validation-only
cozyExportRuntimeClasspath prerequisite 251 succeeded; Cozy source was not
edited by this task.

252: Test compile rejected a new fixture's key member, which collides with
ScalaTest KeyWord. Its command receipt additionally rejected tree drift and
was never published or accepted. The raw SBT summary/log remains readable,
records exits 1/1 and released lock. Parent inspection confirmed no wrapper,
launcher or receipt executor process and no invocation lock. Comparing with
the prior accepted tree identified other-owned CozyVideoProfileRenderSpec.scala
as newly modified; all that work was preserved. The lost nested terminal
stderr marker is not reconstructed and 252 is not acceptance evidence.
The consumed ID/start marker remains immutable. A source fix and newly settled
tree were validated in a fresh command; no denied action, intake, receipt or
permission artifact was replayed.

253: terminal Test compile failure because the fixture attempted private
DataStoreConditionalRoot.copy. Fix the fixture through the public typed builder
instead of relaxing canonical constructor/copy visibility. New source guards
also reject a null host suspension field before writing.

## Executable acceptance and remaining gates

DataStoreConditionalUpdateSpec has eight examples covering in-memory/SQLite
authoritative paired update, preserved fields, stale revision/exact token
mismatch, malformed/foreign/overlapping guards, publication rollback and
independent SQLite writer competition. WorkflowCompletionDataStorePersistenceSpec
has thirteen examples covering exact result/evidence completion, close/reopen,
later-history recovery, required facts/evidence/dispatch and foreign identity
rejection, stale host revision/token, release/reclaim between admission and
commit, five record-failure checkpoints, applied-readback uncertainty, one
completion winner, malformed/orphan/legacy recovery, codec failures, optional
dispatch compatibility and oversized guards.

This retains generic ExecutionEvidence, not separate application
AdmissionEvidence payload/scope. Active UnitOfWork event/data/closing-program
enlistment, production claim admission/Factory shared-service binding,
registered application Operation/launcher/server/MCP composition, separate-process
restart, management visibility and downstream artifact consumption remain open.
Phase 88 and Phase 2 remain in progress. No AI or external command runs in the
write transaction. No stage, commit, publishLocal, release, push or deployment
was performed by this task.

Command routing uses cncf-command-execution, cncf-agent-execution,
cncf-sbt-serial-execution and cncf-workflow-protocol immutable receipt-first
intakes with original native user history. All current-turn runners are
registered custom role cncf_command_runner, gpt-5.6-luna / medium. The shared
serial wrapper and ordinary development caches are unchanged. Parent verifies
receipts, log/summary and all nine owned source hashes. S2 Factory, S3 suspension
spec, S4 issued adapter/spec and other-owned SplitPhase/Phase96/Cozy work are
preserved. Only management documentation changes after source validation;
those final journal bytes are not part of the captured test tree.

## Accepted producer validation

Focused-254: 77/77 tests, nine suites, zero failed/canceled/pending/aborted.
This includes all 21 new S5 examples (eight kernel and thirteen Workflow) and
56 existing Create/Bind and S1-S4 examples. Relevant main/test compilation is
covered by this successful focused command; no duplicate Test/compile was run.

Regression-255: 1054 successful tests, 127 suites, zero failed/aborted,
eight existing live MySQL opt-in examples canceled and five existing HTTP/SQLite/
UoW placeholders pending. Canceled/pending cases are not executed acceptance.
Both successful receipts bind the same complete SM/CNCF/Cozy tree:
087735173caba558d26a02a358f6d4dd0a1dc2adcc5b7797e68d0f5e291e8f48.
Both ended with exits 0/0 and lock=released. This is selected producer
regression, not whole-CNCF full test or downstream production acceptance.

## Invocation evidence

- SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-250; failure / terminal
  runner: /root/sbt_receipt_ea5a99d2c585e1de2ae0
  receipt SHA-256: 479d1c7cad78acce4b6030f8dbe812feccf860483a3f0ad3c6b76b57e795a3ee
  complete tree SHA-256: c6e55c61234663847a3894c92b8f229536eff80028439cfb6f6f94d856e45786
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-10db5835e493a229586bf073a32c3842d6b1f9f6e9b0729bbfdd17be643ed33a-7b8ba91911a143409d448be28f53d8c2/command-execution-sha256-479d1c7cad78acce4b6030f8dbe812feccf860483a3f0ad3c6b76b57e795a3ee`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-250 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-39deb3563128fa6d955b91f034cc00ada1a26f281f97aea1502b2b7acd52058b-afc3b5549487b2ac089972eb741e412c/49957-20260929T064715Z.log summary=/tmp/skill.cncf.d/cncf-sbt-39deb3563128fa6d955b91f034cc00ada1a26f281f97aea1502b2b7acd52058b-afc3b5549487b2ac089972eb741e412c/49957-20260929T064715Z.summary.json`
  observed counts: {"tests":{"total":null,"succeeded":null,"failed":null,"canceled":null,"ignored":null,"pending":null},"suites":{"completed":null,"aborted":null},"compile_failed":false}

- SMWF-P2-CNCF-P88-S5-COZY-CLASSPATH-20260929-251; success / terminal
  runner: /root/sbt_receipt_9f5ce72b1780a2f63934
  receipt SHA-256: f21887b644c2c60f1e6572c5f3831e54f9258726fcc87a3a626c0f0a5bd1f939
  complete tree SHA-256: bca4a8f6a56580c7d5381ce635324433f2bf7c198e0225dd22a4b885962ed608
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-bbf1a64362b3c75d8da8318ebd2e4d4faaf344453d83733b10b55eee597830cf-b9c8f944ede4214250ba6b11400d3f24/command-execution-sha256-f21887b644c2c60f1e6572c5f3831e54f9258726fcc87a3a626c0f0a5bd1f939`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S5-COZY-CLASSPATH-20260929-251 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-7c0d973191ecd24fee33bd1d5773c04c90a3fd30ac1f89fea76dccbfbdfbbd78-2d42493e2e82dd3a47410be95f6ab6f1/52283-20260929T064924Z.log summary=/tmp/skill.cncf.d/cncf-sbt-7c0d973191ecd24fee33bd1d5773c04c90a3fd30ac1f89fea76dccbfbdfbbd78-2d42493e2e82dd3a47410be95f6ab6f1/52283-20260929T064924Z.summary.json`
  observed counts: {"tests":{"total":null,"succeeded":null,"failed":null,"canceled":null,"ignored":null,"pending":null},"suites":{"completed":null,"aborted":null},"compile_failed":false}

- SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-253; failure / terminal
  runner: /root/sbt_receipt_e6fee8ef6df85f350368
  receipt SHA-256: 83eb2091da7e31e04e151c162ea4d96d9ee1f7269f753aa4aeaf74a65e34106d
  complete tree SHA-256: 9d33fa0fd7a21235004d619b54877b5de659936fe7a49d4a8ff4fb65f80ef794
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-d699560c9a738bab3ad3a10f909885f0e83ae01971052329bf0dc0efc603b8d5-33eaacf70ada280abdaabc087b7d8f9d/command-execution-sha256-83eb2091da7e31e04e151c162ea4d96d9ee1f7269f753aa4aeaf74a65e34106d`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-253 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-d239e7d83bffea5a7ba398ffe1678c992c2e79ccd7991a1f4dc29f75e68c0825-604f609ba8dded729e30d79e7528d805/57237-20260929T065645Z.log summary=/tmp/skill.cncf.d/cncf-sbt-d239e7d83bffea5a7ba398ffe1678c992c2e79ccd7991a1f4dc29f75e68c0825-604f609ba8dded729e30d79e7528d805/57237-20260929T065645Z.summary.json`
  observed counts: {"tests":{"total":null,"succeeded":null,"failed":null,"canceled":null,"ignored":null,"pending":null},"suites":{"completed":null,"aborted":null},"compile_failed":true}

- SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-254; success / terminal
  runner: /root/sbt_receipt_463c8602f4ec27a44e83
  receipt SHA-256: 5e515792e060e063668505606fd7435bcf23140aaf38b3bbf800c9ec8d6e75ee
  complete tree SHA-256: 087735173caba558d26a02a358f6d4dd0a1dc2adcc5b7797e68d0f5e291e8f48
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-9498eb6a64ca17723c5c45018b4f4ca4702a42f955ad308a62027da16c670015-b8ddb2a62b45ed9966850ddc82c134b5/command-execution-sha256-5e515792e060e063668505606fd7435bcf23140aaf38b3bbf800c9ec8d6e75ee`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-254 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-177855dc1cd4364d11dce92d98d0258e58355030774218daa13d747798532ca0-9a93d8ebde41630550e06449df5efa4f/58574-20260929T065840Z.log summary=/tmp/skill.cncf.d/cncf-sbt-177855dc1cd4364d11dce92d98d0258e58355030774218daa13d747798532ca0-9a93d8ebde41630550e06449df5efa4f/58574-20260929T065840Z.summary.json`
  observed counts: {"tests":{"total":77,"succeeded":77,"failed":0,"canceled":0,"ignored":0,"pending":0},"suites":{"completed":9,"aborted":0},"compile_failed":false}

- SMWF-P2-CNCF-P88-S5-REGRESSION-20260929-255; success / terminal
  runner: /root/sbt_receipt_23846287572ac20e12f3
  receipt SHA-256: 0a02cbbd6376e1b2221cb1e65dbc7b1098c1cbea039248d90cd8de8c06bdef7b
  complete tree SHA-256: 087735173caba558d26a02a358f6d4dd0a1dc2adcc5b7797e68d0f5e291e8f48
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-8359671298001967121e66e850ecf13d0e22a47bdc94a970f7b0b36c2fd8d325-9eac434a9122a48fcfef6eb6a11998c7/command-execution-sha256-0a02cbbd6376e1b2221cb1e65dbc7b1098c1cbea039248d90cd8de8c06bdef7b`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S5-REGRESSION-20260929-255 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-ce3b794dfba8e857b9322368f7278be824486b5e884d49fc1a4ead0a7836f38d-2a7f35149fe11e49197826066bbdb70d/60019-20260929T070049Z.log summary=/tmp/skill.cncf.d/cncf-sbt-ce3b794dfba8e857b9322368f7278be824486b5e884d49fc1a4ead0a7836f38d-2a7f35149fe11e49197826066bbdb70d/60019-20260929T070049Z.summary.json`
  observed counts: {"tests":{"total":1054,"succeeded":1054,"failed":0,"canceled":8,"ignored":0,"pending":5},"suites":{"completed":127,"aborted":0},"compile_failed":false}

252 rejected execution: SMWF-P2-CNCF-P88-S5-FOCUSED-20260929-252
runner: /root/sbt_receipt_815a2f07b89bbab1de37
intake: `/tmp/skill.cncf.d/cncf-command-execution-dc938ffa7c3ebc09bb9b6719c0b83215dea089faa64f4c6b23a02ce1c0d56b14-fc36f856ca29ec89ade4993f20c4ba7b/command-execution-intake-sha256-4d7f36d6d8530cc16e7691b1076def484943ce66d756f20dd098d316930af070.json`
raw result: `{"code":"COMMAND_EXECUTION_RECEIPT_INVALID","detail":"validation command changed the repository tree; validate the settled tree in a new attempt","status":"rejected"}`
No command-execution receipt exists or is claimed for 252.
Raw summary: `/tmp/skill.cncf.d/cncf-sbt-045d38ebc8ea5fc8aff107bbe313129f210b78afeb695588b2eb2b63bdefbf54-d9f30c1ca0b3d8c94d49fb4d0187639b/53896-20260929T065117Z.summary.json`

## Validated owned source identities

- src/main/scala/org/goldenport/cncf/datastore/EntityConditionalTransition.scala
  dca1817142d1e5f944bfd65a21055f459775e476869a6e44717e00168c476023
- src/main/scala/org/goldenport/cncf/datastore/DataStore.scala
  c43d2851e4a364835ce564d0d6310641a7f93f7020ec814cb118cd607fad37e6
- src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala
  9b640d07602e2dec669651aaf81066671ccc43470f88f0b92d838e531c075d9f
- src/main/scala/org/goldenport/cncf/workflow/WorkflowInstanceDataStorePersistence.scala
  553da6efe6a4629affd24426a2dda81407b11a7785a4185b6e5157d5da5c8363
- src/main/scala/org/goldenport/cncf/workflow/WorkflowContinuationDataStorePersistence.scala
  e358736e1991cdaa2e451b3ef569fd8d22d5da8101abc3176934e512ec023f95
- src/main/scala/org/goldenport/cncf/workflow/WorkflowCompletionRecordJsonV1.scala
  8eb4bd706c61cb7adc8b6de5cf65fb615839e271c6fead3d715e22f21453cb87
- src/main/scala/org/goldenport/cncf/workflow/WorkflowCompletionDataStorePersistence.scala
  3bb48c3b7b76dbb9a82b1e81fcc17f5bff80e445583ba96f495f14de265a2181
- src/test/scala/org/goldenport/cncf/datastore/DataStoreConditionalUpdateSpec.scala
  8319d2413cc1313470adb4b244aa85eda183043797b00021503426dbcab74c81
- src/test/scala/org/goldenport/cncf/workflow/WorkflowCompletionDataStorePersistenceSpec.scala
  30bb0cbea14f593b518df3858be69f9b90d4e804e4ac10702a89c6dca182eb6c
