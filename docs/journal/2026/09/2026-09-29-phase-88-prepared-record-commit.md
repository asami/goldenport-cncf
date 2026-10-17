# Phase 88 S7: Prepared canonical Workflow record commits

Date: 2026-09-29
Status: bounded prepared-record seam focused/regression-validated; full joint runtime remains open
Work A: Phase 88 full joint runtime recovery (sm-workflow P2-U05, still open)
Work B: expose the existing guarded record plans before their single commit

## Evidence and bounded design

EventEngine.noop appends to its separate EventStore and calls DataStore.commit;
TransactionContext prepare/commit/abort currently provide no enlisted resource
transaction. A post-commit callback or two persistence calls cannot join active
UoW event/data/closing-program effects with Workflow records. Do not implement
or advertise AtomicWorkflowSuspensionEngine from that behavior.

Extract existing S3/S4 suspension and S5 completion preparation into the same
borrowed adapters. New prepareSuspensionC and prepareCompletionC return a typed
WorkflowPreparedDataStoreTransition. Preparation may read and admit exact stored
facts but writes no records, publishes no Continuation/result and takes no
transaction/claim/lease. The host may discard preparation with no abort callback.

The prepared value holds the same borrowed provider and immutable canonical
DataStoreConditionalTransitionPlan plus an internal authoritative-readback
projection, outside the provider plan. It exposes no result before commit.
withSideEffectsC appends only explicit normalized EntityVersionedSideEffect
values admitted by the existing canonical plan validator. Root, successor,
ownership, guards and correlation remain exact; overlap/duplicates/limits/null
or unsupported collections reject before publication. No callback, EventRecord
domain object, raw SQL, external effect or second provider call enters the plan.

commitC executes exactly one canonical conditionalTransition and admits its
authoritative readback; the provider rechecks all revision/policy/claim/issue
guards at that commit. The old suspendC/completeC delegate to preparation then
commit with no extra effects and preserve behavior. An uncertain provider throw
or post-commit readback/projection failure is transaction-indeterminate, never
rollback or automatic retry. No retry loop, timeout release or alternate engine.

## Ownership and executable acceptance

Owned CNCF Scala paths:
- workflow/WorkflowPreparedDataStoreTransition.scala (new)
- workflow/WorkflowInstanceDataStorePersistence.scala
- workflow/WorkflowIssuedWorkDataStorePersistence.scala
- workflow/WorkflowCompletionDataStorePersistence.scala
- tests workflow/WorkflowPreparedDataStoreTransitionSpec.scala (new)
- tests workflow/WorkflowIssuedWorkDataStorePersistenceSpec.scala
- tests workflow/WorkflowCompletionDataStorePersistenceSpec.scala

Use their existing src/main/scala/org/goldenport/cncf and
src/test/scala/org/goldenport/cncf prefixes. Preserve existing acceptance examples,
S6 codec/specs, canonical datastore implementation, Factory, unrelated dirty
SplitPhase/Phase96/Cozy work and all legacy cncf-* skills. CNCF remains
0.5.3-SNAPSHOT; Cozy is validation-only if a classpath prerequisite is required.
No commit, publication, artifact refresh or deployment is selected.

Executable acceptance: memory/SQLite preparation and composition publish nothing;
single commit retains guarded records plus explicit local effects; malformed/
overlapping/excessive effects reject without write; guards moving between prepare
and commit reject without side effects; rollback/resource reopen preserve all
records; readback/provider exceptions remain typed and uncertain; generated
normalized local values retain exact meaning; old immediate APIs and S6 payload/
scope retention stay compatible.

## Remaining full-runtime work

This is a necessary local record-plan seam, not an EventStore adapter, active
UoW data interception, AtomicWorkflowSuspensionEngine implementation, distributed
transaction/2PC, compensation or a closing program interpreter. Resource leases
must remain valid until commit; preparation is not durable or portable authority.
A process restart discards it and must query/re-admit current stored facts.

The next composition must encode transactional EventRecords into canonical
same-store record effects and enlist only proven staged data effects from the
active UoW. Unsupported participants must reject before execution. Production
Factory/claim/service/registered Operation/launcher/server/MCP, separate-process
recovery, downstream artifact consumption and management remain open.
P2-U05 and both phases remain in progress; this Work B does not supersede them.

## Implementation and observed validation

WorkflowPreparedDataStoreTransition preserves one immutable canonical plan and
the same borrowed provider. Its package-private constructor keeps result
projection in trusted Workflow adapters; neither preparation nor composition
executes it. commitC performs one guarded conditionalTransition, then the
existing authoritative readback admits the outcome. Completion preparation also
decodes its canonical result bytes before returning, retaining the exact admitted
result rather than deferring observation of a caller-owned value until commit.

WorkflowPreparedDataStoreTransitionSpec supplies twelve examples, including
fifty generated normalized local payload round-trips per property check.
The existing issued-work spec adds two delayed preparation/commit examples;
the completion spec adds three claim/result/history/local-effect examples.
These seventeen additions cover memory/SQLite nonpublication, same-store effect
composition, invalid/overlapping/duplicate/excessive/null/unsupported effects,
stale guards and replay, claim movement, pre-publish rollback/resource reopen,
provider throw and typed/throwing post-commit projection uncertainty.
The previous immediate API examples remain and S6's exact payload/scope codec
and persistence specs are unchanged.

Focused-259 passed 70/70 tests in seven suites, with no failed/canceled/pending/
aborted examples. Selected regression-260 passed 1158 tests in 141 suites,
with zero failed/aborted, eight existing opt-in MySQL examples canceled and nine
existing placeholders pending. Canceled/pending examples are not executed
acceptance. The selected regression includes component, workflow, datastore,
unitofwork and event packages; it is not a whole-CNCF full test run.
Both successful receipts bind the same complete SM/CNCF/Cozy tree:
c973843a341b5c81b7744277c797da1d102672df2047efe70e24045325a05c29.
All seven owned Scala identities match both receipts; all three S6 sources are
unchanged. The three existing Workflow adapters and two existing specs were
intentionally refactored; no claim that all earlier S5 sources remain unchanged
is made for S7. Canonical datastore implementation, EventEngine, UnitOfWork,
Factory and unrelated dirty work were not changed by this slice. Cozy was not
edited or separately invoked this turn.

P2-U10 closes only the prepared canonical record-plan seam. P2-U05 remains
unchecked: no EventRecordStore adapter, active UoW data interception, closing
program interpreter or AtomicWorkflowSuspensionEngine implementation is supplied.
Production claim/Factory/service/registered Operations/launcher/server/MCP,
separate-process restart, downstream artifact consumption and management
remain open. Preparation is process-local, does not own a resource lease, is not
durable authority and must be discarded/re-admitted after restart.
No AI or external command executes in the record transaction.

Routing used cncf-command-execution, cncf-agent-execution,
cncf-sbt-serial-execution and cncf-workflow-protocol with immutable receipt-first
intakes and original native authority history. Both runners used registered
custom role cncf_command_runner, gpt-5.6-luna / medium. The parent independently
verified receipt identity, actual terminal summaries/logs and source hashes.
No stage, commit, publication, artifact refresh or deployment was selected.
Documentation updated after validation is not part of the captured test tree.

## Invocation evidence

- SMWF-P2-CNCF-P88-S7-FOCUSED-20260929-259; success / terminal
  runner: /root/sbt_receipt_aceea289b2bb0d7454ea
  receipt SHA-256: cafd8848276aefb3f5b79f91feea1efab79e2739fed9e9803e30cc98d0a5b58c
  complete tree SHA-256: c973843a341b5c81b7744277c797da1d102672df2047efe70e24045325a05c29
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-efb6a8a670d23731474a1ad09195aa88be0107fb11fdfc2f5d87f59d90717964-5d59d3eddd7918c29fe1d9eba2999c72/command-execution-sha256-cafd8848276aefb3f5b79f91feea1efab79e2739fed9e9803e30cc98d0a5b58c`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S7-FOCUSED-20260929-259 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-f0502caa9b0aad4c3a2ef196b2b6139bef14e21d662967c7acefa7a09aa71d17-951111f33c7d8078cd7dfb6334905235/21345-20260929T090955Z.log summary=/tmp/skill.cncf.d/cncf-sbt-f0502caa9b0aad4c3a2ef196b2b6139bef14e21d662967c7acefa7a09aa71d17-951111f33c7d8078cd7dfb6334905235/21345-20260929T090955Z.summary.json`
  observed counts: {"tests":{"total":70,"succeeded":70,"failed":0,"canceled":0,"ignored":0,"pending":0},"suites":{"completed":7,"aborted":0},"compile_failed":false}

- SMWF-P2-CNCF-P88-S7-REGRESSION-20260929-260; success / terminal
  runner: /root/sbt_receipt_556bf47f452d29056256
  receipt SHA-256: c9c008a8804ddb6b83e50ab64e8d1ee871cc599120dbbaa97adbcaf23c599e71
  complete tree SHA-256: c973843a341b5c81b7744277c797da1d102672df2047efe70e24045325a05c29
  receipt: `/tmp/skill.cncf.d/cncf-command-execution-a7f4da41ff0decb7f0f8840ad1018d8ee0aef08ed40e0d35850c327f46d148c1-29aec461f4ab535b33ad687ed0e8e6e4/command-execution-sha256-c9c008a8804ddb6b83e50ab64e8d1ee871cc599120dbbaa97adbcaf23c599e71`
  terminal: `[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S7-REGRESSION-20260929-260 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-be523dde552995d41f74b5c630a0832aa1984ecc591cb2e846faab1a8e87352d-4529de8c94220ea2a50c964e7d8c6411/24370-20260929T091536Z.log summary=/tmp/skill.cncf.d/cncf-sbt-be523dde552995d41f74b5c630a0832aa1984ecc591cb2e846faab1a8e87352d-4529de8c94220ea2a50c964e7d8c6411/24370-20260929T091536Z.summary.json`
  observed counts: {"tests":{"total":1158,"succeeded":1158,"failed":0,"canceled":8,"ignored":0,"pending":9},"suites":{"completed":141,"aborted":0},"compile_failed":false}

## Accepted owned source identities

- src/main/scala/org/goldenport/cncf/workflow/WorkflowPreparedDataStoreTransition.scala: 5f997dae89b71b43f501742a3068205bdd00ca0424f3c0ea564befd904ffbb6e
- src/main/scala/org/goldenport/cncf/workflow/WorkflowInstanceDataStorePersistence.scala: 083c2587cda9c48338beaf3a4ee80a0c6eb764eca8ee29a72e5aa3a9d084940e
- src/main/scala/org/goldenport/cncf/workflow/WorkflowIssuedWorkDataStorePersistence.scala: 87c77a05acf4936ee2e8cfdbea1582ea720faf4b9413e4d927bf15d19bd08ed9
- src/main/scala/org/goldenport/cncf/workflow/WorkflowCompletionDataStorePersistence.scala: 3d4dde60e692dd20ccc54c982ec11d0077cef8a66306296339c1f78861b78817
- src/test/scala/org/goldenport/cncf/workflow/WorkflowPreparedDataStoreTransitionSpec.scala: ecc0da324a7c27fb15f6e7ab4d697f35e4ab0d1e9aa4154213f2cc860405faf6
- src/test/scala/org/goldenport/cncf/workflow/WorkflowIssuedWorkDataStorePersistenceSpec.scala: 31849425d5c4912bdc64b3f5a22a5945c6e48b71896af243226063268b83a7ff
- src/test/scala/org/goldenport/cncf/workflow/WorkflowCompletionDataStorePersistenceSpec.scala: 22cf692129acc414b8905c78aa49fb0b8d99b5eeeb24c7b11c68a6ac4c380f63
