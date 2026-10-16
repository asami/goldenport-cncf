# Managed mechanical Workflow transition

Date: 2026-10-02
Scope: admitted Phase 88 dependency of the original sm-workflow Phase 2
Status: implementation candidate with focused validation passed; independent acceptance open

## Contract and ownership

The consumer's public advance must absorb all-known planning without issuing
an AI WorkOrder or fabricating a ContinuationResult. Existing managed issue/
completion APIs require an actual issued interaction; ordinary append does
not atomically publish application documents. The bounded producer design is
recorded in sm-workflow's journal
`docs/journal/2026/10/2026-10-02-split-phase-public-advance-mechanical-transition-plan.md`.
This is a necessary dependency, not a replacement Phase or storage-only Goal.

Execution owner is the parent under the retained user-directed limited recovery,
not a registered implementation/command agent or an independent review.
Current original Goal is active. Missing original typed authority/base/binding,
PLAN-owner and review evidence remains missing; no current HEAD, model/effort,
accepted review or new epoch is invented. Actual scoped platform submissions
admitted source writes to the fixed files only. No Git mutation, shared install,
remote publication or new pool/provider is performed.

## Candidate source and behavior

Five new Workflow files define mechanical Intent/Reference/Receipt, closed
version-1 codec, private Create-only ExecUowM[Unit] preparation, a borrowed
canonical pair adapter and WorkflowManagedMechanicalTransition. The sole
existing main-file change is WorkflowComponentStorage.bindMechanicalC.
Factory-time binding is metadata-only; each execution/observation uses the
Subsystem's existing verified Workflow DataStoreSpace and common physical
provider. All leases end before the method returns.

Only Active, unsuspended, exact one-append intents are admitted. Instance CAS,
immutable mechanical checkpoint Create, bounded application Creates and
EventStore-selected durable records use one ManagedCompletionUnitOfWork and
the existing canonical verified native transaction. Completed/Retained
witnesses have private constructors; decoded data is not a grant. No direct
commit fallback, split write, arbitrary opcode or fake Completed action exists.
Create-only preparation permits zero to 64 distinct targets and rejects local
guards and every unsupported effect before interpretation. Host-captured
collection/owner grants and reserved targets remain independently enforced.

Checkpoint storage reuses the authorized Continuations collection but has a
distinct mechanicalPayload envelope, never AvailableContinuation/WorkOrder/
Decision/result/claim state. Explicit identity/revision keys percent-escape
UTF-8 delimiters; no hash or latest-record choice is used. Keys over 1024 bytes
and receipts over 1 MiB refuse before effects. Any occupied target refuses
Create, including a normal continuation using the same physical key.

Recovery is an explicit authorized read, not an automatic resend. It checks
the physical envelope, exact bound owner/operation/reference, configuration,
typed codec/catalog and complete native history prefix. Later legitimate
history survives unchanged. Absent, malformed or divergent data cannot yield
a usable Retained witness. Application-specific input/document re-admission
remains the consumer's responsibility. Codec observation failure after native
commit returns the actual indeterminate managed outcome, never success or
an automatic retry. Event persistence selection and transient delivery reuse
the existing host contract without caller persistence flags or policy edits.

## Executable specifications and validation

Frozen Test files:

- org/goldenport/cncf/workflow/WorkflowMechanicalTransitionSpec.scala: four pure codec/admission behaviors.
- org/goldenport/cncf/workflow/WorkflowMechanicalProgramPreparationSpec.scala: four closed-program behaviors.
- org/simplemodeling/cncfclient/WorkflowMechanicalTransitionSpec.scala: seven native public behaviors outside private CNCF packages.

Each new behavior block uses adjacent Given/When/Then and should matchers.
Native fixtures use the actual configured Service/CLI and shared SQLite host,
not fabricated provider success: atomic success/reopen/later history, occupied
document/checkpoint targets, missing/foreign grants and Workflow composition,
expired record capabilities, committed receipt observation refusal, malformed
checkpoint payload and valid-but-divergent history. They are storage evidence,
not genuine human consent, sm-workflow default installation or public advance.

794 ran the exact frozen nine-suite accumulator through the shared serial
wrapper with fresh typed permission, full 462-entry prior ledger, fixed JVM
profile and normal caches. Main compiled 216 sources successfully; Test
compilation failed at public fixture line 157 with E049 name shadowing.
No test ran; SBT/wrapper exits 1/1 and lock=released. Log and summary:
`/tmp/skill.cncf.d/a-f148814c-4bee-4420-b870-0d12193eb512/34851-20261002T095941Z.log`
and adjacent `.summary.json`. The ID is consumed, not replayable.

Frozen correction 1/3 changes only the helper argument name and its one use;
no behavior, assertion, API or validation selector changes. Revalidation uses
a fresh 795 identity and the full 463-entry prior ledger. Predecessor counters
are preserved. No focused success, independent conformance review, repository
full validation, accepted Step commit or original Phase release is claimed.

## Remaining work

795 completed main/Test compile, then reported 88/93 passed, five failed and
one suite aborted; exits 1/1, lock=released. Log/summary:
`/tmp/skill.cncf.d/a-dcf8a489-d01c-4e67-88c6-bcaa814f63e7/36522-20261002T100331Z.log`
and adjacent `.summary.json`. The source-grounded repair is 2/3: correct
three test-only ComponentId namespace literals and separate exact logical
post-image decoding from exact physical-load decoding in the same adapter.
SqlDataStore._multi_guard_logical_record validates physical id, then removes
it from the verified logical record. Requiring id again there incorrectly
turned ordinary committed success into indeterminate. Ordinary load still
requires the exact id and all four physical fields; verified projection uses
all three logical fields, with duplicate/unknown fields refused. No optional-id
fallback, provider/policy change, assertion relaxation or new API is introduced.
Revalidation 796 uses the same accumulator and complete 464-entry prior ledger.

796 completed main/Test compilation and all nine suites with 96/97 tests
passing, one failing and none aborted/canceled/ignored/pending. Exits 1/1,
lock=released, no live SBT remains. Log and complete summary:
`/tmp/skill.cncf.d/a-d61bfffc-4a63-460c-b7d4-05ea4628414c/39306-20261002T100923Z.log`
and adjacent `.summary.json`. Exact native post-image projection now passes,
as do atomic success/reopen/later history, collision rollback, committed
uncertainty recovery and malformed/divergent-data refusal.

The only failure is the absent-grant test setup invoking declaration.createC
with an empty vector; that declaration deliberately requires nonempty grants.
Frozen correction 3/3 changes only the public mechanical spec host helper:
choose the existing six-argument bootstrap without any record declaration
for recordGrant=false, and the existing seven-argument bootstrap with the
nonempty declaration otherwise. No production permission rule, API, assertion,
provider, policy or test selector changes. Parent limited recovery is retained,
not ordinary registered-runner acceptance. Revalidation 797 uses the unchanged
accumulator, fresh typed permission and the full 465-entry prior ledger;
no fourth repair cycle or independent acceptance is asserted.

797 completed main/Test compilation and the unchanged nine-suite accumulator:
97/97 passed, nine completed suites, zero failed/aborted/canceled/ignored/pending.
SBT/wrapper exits 0/0 and lock=released. Complete summary read at
`/tmp/skill.cncf.d/a-281d2d01-f80e-4106-9ebd-d74dc401f030/42678-20261002T101731Z.summary.json`
with adjacent `.log`. The missing/foreign-grant behavior now reaches the actual
managed operation and denies progress without retaining a checkpoint; all
atomic/reopen/uncertain/tamper behaviors remain successful. Both repositories'
git diff --check passed. There were no source edits during SBT. The full ledger
retains all 466 consumed IDs, including all failed attempts, and no SBT is live.
Repair count remains 3/3. No publishLocal, consumer validation, source version,
Git index/history, remote publication, shared skill or independently accepted
Step/Phase result is claimed. Return to consumer integration PLAN under the
same original active Goal, not a replacement storage-only Goal.

After focused producer validation, refresh the unchanged local CNCF SNAPSHOT
before consumer API integration. All-known context preparation, mechanical
allocation anchors, generated public advance, multi-service post-resolution
Factory, genuine human ingress and generic bundled sm-* clients remain in
the original consumer acceptance. Independent focused conformance/Phase
review, exact commits and release full validation remain separate obligations.
