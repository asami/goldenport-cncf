# Phase 88 S4: atomic issued WorkOrder publication

Date: 2026-09-29
Status: bounded producer slice focused/regression-validated; full joint runtime remains open

## Decision

Extend the S3 canonical compound record transition, not UnitOfWork or an
alternative Workflow engine. Persist the strict WorkflowWorkOrderJsonV1 bytes
as an immutable opaque property of the newly created Continuation Entity.
The root history and the Available Continuation plus issued payload are then
published by the same guarded canonical transaction. Do not use a Save side
effect to emulate immutable create: canonical side-effect Save can overwrite.

Bind explicit component identity, application PayloadCodec, configured store,
admitted definition catalog and distinct EntityStore collections. Admit exact
handle/instance/definition, run, Continuation identity, expected StateMachine
revision, operation/input/result types, context and completion/evidence contract
facts before writing. Require exact codec round-trip. Recover only an exact
issued payload whose instance retains the matching suspension history.

Implement the existing IssuedWorkOrderPersistence read/retry boundary. Its
putIfAbsentC accepts only an already atomically published identical issue; it
must not fill missing issued payload on an independently published Continuation.
Claim/release/complete preserve the immutable property through canonical CAS.
Existing S3/SPIs remain compatible and do not imply issued-work publication.

## Frozen executable specification scope

- exact joint publication and SQLite resource reopen with issued work and a
  retained claim, then token release/completion without payload mutation;
- every mismatched typed binding rejected before publication;
- missing/malformed/drifted persisted issued bytes fail closed without repair;
- independent putIfAbsent cannot publish or retrofit work;
- duplicate/stale suspension and pre-commit failure leave no orphan issue;
- applied-but-unadmittable issued readback is transaction-indeterminate and
  recovery reads committed facts without automatic replay;
- independently bound writers have one guarded winner.

Owned source scope: WorkflowInstanceDataStorePersistence.scala,
WorkflowIssuedWorkDataStorePersistence.scala and
WorkflowIssuedWorkDataStorePersistenceSpec.scala. Existing S2 Factory and S3
Continuation implementation remain untouched. Documentation updates only in
Phase 88 and the corresponding sm-workflow Phase 2 records.

## Still open

This is not AtomicWorkflowSuspensionEngine, active UnitOfWork event/data
participation, retained result/admission evidence atomicity, compound completion,
claim admission policy, production Factory service/launcher/MCP binding,
separate-process restart or downstream artifact consumption. A read does not
claim work. The public sm-* skill contract remains semantic-result-only.

## Implementation and observed validation

Host binding fixes both the Component identity and completion Operation. The
caller cannot substitute a different completion selector. Pure Continuation
codec validation runs before typed work matching, including incomplete nested
metadata rejection. Application codec exceptions/lossy round-trips are typed
pre-publication failures. No SQL, engine, event callback, resource owner or
skill-selected authority is introduced. The unchanged Continuation CAS adapter
preserves the co-located immutable issued property through claim/release/complete.

246 and 247 compiled the implementation and passed all 25 existing S1-S3 tests,
but the new suite aborted during its fixture construction. 246 used an invalid
namespace; 247 corrected that namespace but still had a lower-case local ID.
The fixture now uses the same qualified PascalCase identity convention as
existing successfully executed specs. These are completed SBT failures, not
permission stops and not S4 test acceptance. No failed attempt was reused.

Focused-248 passed all 34/34 tests in four suites: nine S4, ten S3, seven S1 and
eight S2/bootstrap examples, with zero failed/canceled/pending/aborted.
Regression-249 completed 125 suites, 1033 succeeded, 0 failed, 8 canceled and 5 pending. The canceled/pending examples
are existing environmental/placeholder cases and not executed acceptance.
This is selected producer regression, not whole-CNCF full-test or downstream
production service acceptance. All four invocations reached matching terminal
markers with lock=released; both successful receipts bind the same three S4
source identities. Parent verification checked receipt, log/summary and source
hashes. S2 Factory, S3 Continuation/spec and the other-owned SplitPhase document
remain unchanged. No source or document was edited during a live invocation.

All runners: registered custom role cncf_command_runner, gpt-5.6-luna / medium.
Command routing used cncf-command-execution, cncf-agent-execution,
cncf-sbt-serial-execution and immutable cncf-workflow-protocol receipt-first
intakes; normal development caches and the shared serial wrapper were retained.

Validated S4 source SHA-256 values:

- src/main/scala/org/goldenport/cncf/workflow/WorkflowInstanceDataStorePersistence.scala
  1c85b32c0a0c1a6ed875a46751b0a1dee311d23f7b052ad89775a24a20b24eff
- src/main/scala/org/goldenport/cncf/workflow/WorkflowIssuedWorkDataStorePersistence.scala
  442e9bb8a5b1f1abe1fe1ff1f0842ccb616ce069d640a5f1a538d3c6b49f7607
- src/test/scala/org/goldenport/cncf/workflow/WorkflowIssuedWorkDataStorePersistenceSpec.scala
  7eca33d11ecaa326aaee7a8c98338dbcd4d0bf05b019f01a84089720e7a6b8ac

After validation only Phase/notes/journal management text is updated; it was
not part of the captured test tree. No publishLocal, stage, commit, push, remote
publication, skill deployment, private engine/SQL or launcher/server execution
occurred. Phase 88 and sm-workflow Phase 2 remain in progress. P2-U07 closes only
this bounded publication/recovery slice; P2-U05 and P2-D03 remain open.

## Next boundary

Define retained typed result/admission evidence and compound completion against
the exact claimed Continuation plus instance history. Separately provide a real
canonical active UnitOfWork/event/data prepare/commit/abort participant; do not
advertise AtomicWorkflowSuspensionEngine from S4. Require configured production
claim admission before exposing work: this adapter's read is not a claim and
ordinary Continuation SPI calls alone do not validate issued payload. Missing
issued bytes on a legacy pair remain a typed rejection, never a migration repair.
Validations reconstruct SQLite resources within one process; separate-process
launcher restart, management projections, server/MCP and artifact consumption
remain open. Store property immutability is enforced by these adapter APIs, not
a claim of tamper-proof storage against arbitrary direct datastore mutations.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/09/2026-09-29-phase-88-issued-work-publication.md.gz`
