# Durable Job Migration Checkpoint Contract

status=selected-validation-passed
review_status=independent-complete-Step-review-pending
phase=69.7
step=JM69-09
slice=JM69-09D2
manifest=PHASE-69.7-migration-checkpoints-implementation-revision1
document_revision=1

This normative contract fixes explicitly enabled native migration of canonical terminal V1 records. Value migration alone does not checkpoint storage. Trusted provider composition opts in; the original engine, startup barrier, managed lease, canonical codec, native OCC and all paid authority remain the owners of their existing responsibilities. JM69-09 remains **IN_PROGRESS** and JM69-10 remains **OPEN**. Initial authoring established no validation, review or acceptance result. Current D2 selected validation passed; independent complete-Step review remains pending. See the [canonical migration journal](../journal/2026/10/2026-10-04-phase-69.7-migration-checkpoints.md#actual-d2-selected-validation-and-retained-execution-history).

## M1

Original JM69-09D explicit native V1-to-V2 migration checkpoint, not value-only conversion. Preserve the sole Subsystem engine and whole startup barrier, signed schemas, native provider OCC, lifetime/Blob/storage accounting, protected replay and both CPBs. All remaining backup/restore, all17 native correlations, actual JM10/CBD, ancestry/completeStep/fullPhase/release obligations remain original required work.

Executable trace: MC2, MI1.

## M2

Append migrateV1OnStartup:Boolean=false to private[job] DurableJobRuntimeInputs. Only trusted registered provider composition explicitly supplies true; no new config/catalog/env/JCL/HTTP flag or discovery. With false retain byte-for-byte original effect ordering/behavior: original source fetch, bridge bind and original recovery. With true, after native capability probes and the one original source fetch, run complete migration on that cached vector under the original provider-start real SystemNode binding lease while _begin_durable_runtime_c barrier is active. Construct helper using original owner _store/_payloads, inputs.lifetimepolicy and actual engine._resource_budget.policy, not a new default or modified State policy. Do not bind bridge, recover/register, publish owner/start workers or timer until migration succeeds. Do not fetch actual source again. Whole normal original recovery with same cached vector still follows and must independently pass before Ready.

Executable trace: MI1, MI2, MI3, MI4, MI5.

## M3

New private[job] final DurableJobMigrationCheckpoint(store,payloads,lifetimepolicy,resources:JobResourcePolicy,maxPhysicalBytes:Long=16777216) exposes migrate(candidates:Vector[DurableJobStartupRecoveryCandidate],maxCandidates:Int):Consequence[Unit] using supplied ExecutionContext. Internal constructor is lower-only byte-budget for specs; no public API/report/override/config. Operator policy authorizeScope first, before candidate validation/native effects. inputs.context must explicitly carry job_admin or content_admin when migration is enabled; no privilege synthesis/borrowing from record/source. Unauthorized returns only fixed Consequence.stateInvalid("durable job migration checkpoint refused"). Trust independently supplied candidate.access and let original native codec enforce exact tenant/subject/scopes. No reading credential bodies or deriving access from ctx/Job/Task.

Executable trace: MC1, MI2, MI6.

## M4

Validate nonnull constructor inputs/policies, positive<=16MiB budget, limit1..1024, and entire cached candidate vector through original DurableJobStartupRecoveryCoordinator._admitted_candidates_c using a local immutable cached source. Reject any invalid original512-byte Job ID, null/duplicates/wrong ordinal/access/evidence before first native load. Original actual startup source is called once only; this cached validation has no source/provider discovery or live rehydration. Full preflight every candidate before ANY migration checkpoint: missing/corrupt/unauthorized/nonActive/unknown native record refuses fixed batch; no healthy prefix mutation on an invalid tail. Raw V0 stored text and unknown versions remain original Corrupt because native storage requires canonical V1/V2; do not weaken canonical storage or add raw ingestion.

Executable trace: MC1, MC3, MC5, MI3.

## M5

For a signed admitted Active V1, require terminal-only matching status/result and revision<Long.MaxValue. First call original DurableJobRecord.migrateV1ToV2 to validate/re-sign exactly unchanged body; then sign destination V2 with ONLY identity.revision advanced by1 for original Store contiguous semantic checkpoint. Every other identity/createdAt, authorization, definition, input/result/external/CallTree reference, task, schedule, retry, retention/legalhold, timeline/diagnostic field remains exactly source. Owner/binding fingerprints remain unchanged. No downgrade, reset, resubmission, synthetic timestamp/history/ID generation or new lifetime root.

Executable trace: MC2, MC5.

## M6

Before any write, classify/project each V1 destination through original DurableJobRecovery.classify and DurableJobTerminalProjection.project with same candidate and a value-only destination snapshot; require original terminal projection success and exact terminal outcome. Use original DurableJobLifetimeRecovery.footprint with unchanged resource/lifetime policies; no guessed task IDs, normalized descriptor or fabricated evidence. Require the genuinely existing native Published lifetime record for source access and exact source snapshot; no recoverRoot/reserveRoot/publishRoot/paid reconstruction/refund. For already V2 candidates preflight original footprint plus existing prepare(...,liveCount=None), no checkpoint/re-sign/payment mutation.

Executable trace: MC2, MC3, MC4, MC5, MI5.

## M7

In DurableJobLifetimeRecovery factor ONLY the existing executed/tasks/paid/claims/normalids/cancelclaimed/liveCount guard into one private _paid_footprint_c helper, keeping existing prepare authentication/order/Prepared result unchanged. Add prepareMigration(store,source:DurableJobStoreSnapshot,destination:DurableJobRecord,access,context,policy,footprint):Consequence[Unit]. Require exact V1 Active source and V2 destination equals source.body with only contiguous revision; original store.load exact source snapshot and original quota.snapshot authenticate genuine Published source; reuse same pure paid-footprint guard with destination body/native seed/liveCount=None. Return Unit only, never Prepared/Binding/Seed from V1, and never widen original prepare/recoverySnapshot V2 requirement. Missing/Pending/malformed/policy-mismatched/insufficient paid source refuses before write. V1 without genuine payment remains refused/escalation, not made recoverable by migration.

Executable trace: MC2, MC4, MI5.

## M8

Preflight each candidate physical owned payload using existing payloads.inspect(source.record,one operation-local DurableJobIntegrityByteBudget) and require Verified. Existing immutable metadata/binding/strict Published Blob ledger/whole-target reservation/8MiB per payload/16MiB aggregate including sentinel/single8192-buffer stream/finally close are consumed unchanged. Zero-owned retains original no Blob requirement. Shared/definition collision, malformed/missing/Pending/Released authority, physical tamper/overrun/truncation/exception/budget refusal fails complete preflight before checkpoint. Never call prepare/delete/put/census/reconcile/CAS/refund from preflight. No second payload stream after checkpoint; refs/bindings identical, evidence is point-in-time and not an atomic backup certificate.

Executable trace: MC6, MC9, MC10, MI4.

## M9

Freeze preflight source snapshots/destinations/footprints in original candidate order, at most1024. Then for each V1 sequentially reread exact source using original store.loadStartupRecovery immediately before checkpoint and refuse if any record/providerrevision mismatch; call original store.checkpoint(source,destination,access) ONCE with its original optimistic/ObservedRequired/contiguous revision algorithm and bounded storage quota reservation/confirmation. Do not mutate Store or quota implementation, lower guards, retry checkpoint, reconstruct lost acknowledgements or write raw SQL. Native checkpoint may make conservative storage quota effects; only one original Job checkpoint call is claimed, not one total CAS across all accounting.

Executable trace: MC2, MC6, MC7, MC8.

## M10

After each acknowledged checkpoint require returned record exactly destination; call original LifetimeRecovery.footprint/prepare on actual destination snapshot (liveCount=None) and final original loadStartupRecovery exact acknowledgement. Original recovery of entire cached vector follows in Owner. A failed suffix/race/ambiguous ack/post-proof can retain genuinely migrated prefix/current committed destination and conservative native storage reservation. Return fixed failure, no report claiming whole success, no rollback/refund/re-sign/retry/Ready/business body. A later fresh explicitly enabled attempt sees actual V2 and validates without a second migration checkpoint; lack of actual paid authority still refuses. No multi-record transaction or atomic snapshot claim.

Executable trace: MC3, MC7, MC8, MI3, MI5.

## M11

Run complete source/preflight/physical close/all checkpoints/post-proof within original provider _start_c outer actual SystemNode binding lease. Add no engine/owner/Subsystem monitor across source/native/stream/migration or original recovery. Existing short begin/final publication and original registration monitors remain. Startup failure uses existing owner stop/gate close/admission-failed and CLI cleanup, preserves acknowledged prefix/paid debt; final original engine close flags prevent Ready. Admitted operations retain real managed pool while SystemNode drains, no second pool/worker/lease counter or physical close ownership. InterruptedException before/between actions restores interrupt and refuses; NonFatal maps constant refusal without raw provider/path/credential text. Never swallow native ambiguity as success.

Executable trace: MC6, MC9, MI1, MI3, MI4, MI6.

## M12

Native Specs must compose existing actual managed JobDurableRuntimeFixtures and/or IntegrityInspection fixtures in NEW owned fixture only. Seed legitimate V2 Submitted roots via original reserveRoot/create/publishRoot with real payment; associate actual normal TaskIds and complete Own/PerTask descriptors using original paid authority, then deliberately produce canonical terminal V1 via original signed Store.checkpoint (this is valid existing legacy terminal storage, not fabricated paid state). Do not use fake Published ledger/paid seed/standalone SQLite/in-memory positive. Strict legacy V1 without payment is a NEGATIVE. Real providerrevision decoder/current-lease managedSql adapter must be retained; raw SQL only intentional corruption/fault fixture, never positive authority reconstruction.

Executable trace: MC2, MC4, MI1, MI5.

## M13

Create exactly MC1..MC10 and MI1..MI6 leaves below in two AnyWordSpec+GivenWhenThen suites and new bounded fixture. Every leaf visible afterWord spec/example/rules/phase69.7/sliceD2, explicit adjacent Given->setup/When->action/Then->should. Preserve visible GWT and all existing native assertions. MC10 and MI6 ordinary shrinking ScalaCheck seed69709704 minimum24 successes per Spec. Native Job/quota canonical bytes+providerrevisions/Blob metadata+actual bytes/source/port/body/task/time/IDs/timer counters and deterministic bounded latches/finally joins are oracles; no source-text/header/checklist assertions or println/snapshot suppression.

Executable trace: MC1..MC10, MI1..MI6.

## M14

Initial authoring requirement (historical): Author new normative migration contract and design with M1..M16 and traceability before program edits; update runtime owner contract R3/R7 and design to explicitly delimit opt-in preparation/default unchanged, not silently replacing original barrier. New migration journal records native codec-versus-checkpoint gap, whole-vector staged preflight, pre-existing paid ownership, partial/ambiguous commit semantics and refusal/escalation; no invented runtime/test/review success. Append Phase/checklist/job operations D2 authored_test_pending and keep JM09 IN_PROGRESS/JM10 OPEN; no checkmark/Step/Phase/predecessor closure.

Executable trace: MC1..MC10, MI1..MI6 (authoring trace). Current selected results are recorded in the [migration journal](../journal/2026/10/2026-10-04-phase-69.7-migration-checkpoints.md#actual-d2-selected-validation-and-retained-execution-history); the initial pending status is history, with M16 closure obligations unchanged.

## M15

Synchronize D1C factual status in owned integrity contract/design/journal and Phase/checklist/job operations: REP00514suites132success0failed0pending0warnings; ACC00190suites728success0failed1existingpending0warnings; both reader/SBT/wrapper0 and lockreleased with exact immutable result/log paths from parent supplied evidence. Preserve REP00123compileerrors, REP002119pass13fail, REP003/REP004131pass1fail, all frozen FIXes, parent one-token fix, explicit pre-review predecessor3attempts/successor2attempts, unwaived M2/CPBs/Step1Phase0/epoch1 and DVL-PH697-D1C-START-FIELDS-01. Mark selected-validation-passed/independent-complete-Step-review-pending only; never rebind old receipts to new D2 program subject or claim Step acceptance. D1B historical results/chronology remain untouched.

Executable trace: Historical D1C receipt projection only.

## M16

Entire original JM69-09 closure before protected complete Step review: all17 correlations, backup/restore, original remaining C/D/security/retention/hostile fixtures and unwaived M2/CPBs. Entire JM69-10 actualtwo-process/scheduledretry/crash/cursor/payload/corruption/version/migration/JCL/governance/CompositeQuery/useradmin/notification/security/CBD fresh-runtime, 69.4-69.6 ancestry/deferred audit, independent completeStep/localcommits, aggregate finalfull/downstream, soleepoch1 Phase fullReview/final localrelease remain. No D2-only Step review/commit, push/publish/deploy/otherrepo/skill/successor. Keep active originalGoal/base/binding/ledger/epoch/budgets.

Executable trace: Original whole-Step/Phase obligations remain open.

## Executable specification examples

| Example | Suite | Observable contract |
| --- | --- | --- |
| MC1 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Unauthorized even malformed requests, constructor/bounds/source vector null/duplicate/badordinal/oversize-ID with valid prefix invalid tail: fixed refusal and zero prefix loads/writes/body. Operator auth precedes native validation. |
| MC2 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Genuinely paid V2 root -> original signed terminal V1; explicit migration checkpoints native canonical V2 at semantic+1/provider+1, all other body fields/legalhold and lifetime/Blob paid rows/revisions/bytes exact unchanged, storage quota original acknowledgement and no body/IDs/time/timer. |
| MC3 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Already V2 and mixed V1/V2 ordered vector; validate existing original authority and no V2 checkpoint/re-sign; repeat new helper/fresh managed context performs no second migration. Corrupt/missing/foreign valid-tail prevents any prefix migration. |
| MC4 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Real V1 no lifetime ledger and deliberate native Pending/malformed/wrong-policy/insufficient native payment refuse whole batch before checkpoint; no bootstrap/payment reconstruction/reset/refund and exact rows/absence remain. |
| MC5 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Active admitted terminalV1 Long.MaxValue, nonActive states, incompatible source evidence/task IDs/descriptors and deliberate rawV0/unknown stored text refuse before Job write; original value migration and closed schema remain unchanged. |
| MC6 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Bounded actual physical stream barrier permits competing native checkpoint during preflight; final exact source mismatch refuses, winning Job/quota state retained, no migration checkpoint or paid/Blob changes. |
| MC7 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Actual Job native commit-before-ack-loss wrapper: fixed refusal, genuine committed destination/conservative storage reservation preserved; no retry/rollback/refund/claimed success. Fresh authorized call observes actual state and follows original accounting/paid guards. |
| MC8 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Two preflight-valid V1 rows: native race/refusal injected at suffix application after real prefix acknowledgement. Prefix stays migrated, suffix winning row unchanged, no whole-success/body/compensation/refund. Full preflight invalid-tail alternative is zero-prefix-write. |
| MC9 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | Actual metadata/shared-collision/Pending Blob ledger and physical tamper/truncate/overrun/zero-read/stream throw/interrupt cases; original bounded physical verification/finally-close and no prefix migration before complete preflight. Interrupt retained and fixed redaction. |
| MC10 | [DurableJobMigrationCheckpointSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMigrationCheckpointSpec.scala) | 24 ordinary shrinking finite real managed cases of lower-only exactfit/one-under whole-operation physical budget and duplicate/reference source order; inclusive expected+sentinels/16MiB cap, no cross-Job dedup, capacity/paid/native rows unchanged on refusal. |
| MI1 | [JobDurableMigrationStartupSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableMigrationStartupSpec.scala) | True explicit trusted provider with job_admin/content_admin, managed genuine V1 terminal source: one actual fetch; preflight/checkpoint completes while original submit/manual drain barrier prevents body/IDs; original entire cached terminal/runtime recovery then Ready, one original scheduler registration. |
| MI2 | [JobDurableMigrationStartupSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableMigrationStartupSpec.scala) | Default false and explicit false preserve native V1 startup refusal and exact original unconfigured/V2 ready behavior/effect order; no helper/extra source/paid changes. True without migration Operator capability refuses before migration native I/O/Ready. |
| MI3 | [JobDurableMigrationStartupSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableMigrationStartupSpec.scala) | True valid V1 prefix + invalid native suffix causes no migration before complete preflight. Later native checkpoint/ack/port/recovery/timer failure after prefix retains actual migrated prefix/payment but owner never Ready and no Task body/refund/worker release; original startup cleanup/gate behavior. |
| MI4 | [JobDurableMigrationStartupSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableMigrationStartupSpec.scala) | Actual SystemNode drain while migration physical stream/pre-checkpoint is held by bounded barrier: managed pool stays open under original lease until stream finally-close/operation ends, final close flags prevent Ready; no new enclosing monitor/secondary lease/pool; always bounded finally release/join. |
| MI5 | [JobDurableMigrationStartupSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableMigrationStartupSpec.scala) | Fresh managed owner/context over same genuinely migrated SQLite/Blob native files registers original terminal facts via existing prepare/seed; exact lifetime/Blob paid text/revisions remain, no re-migration/reserveRoot/recoveryRoot/body; original Degraded/failure cleanup not healed by projection. |
| MI6 | [JobDurableMigrationStartupSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableMigrationStartupSpec.scala) | 24 seeded real signed authorized/foreign access/hostile provider/path/credential strings with failure/interrupt/batch refusal produce only fixed migration/startup refusal; exact source access required, native signed/paid states preserved and no Task/clock/IDs effects. |

MC10 and MI6 each use ordinary shrinking ScalaCheck properties with seed 69709704 and at least 24 successful cases. All sixteen leaves carry visible `spec`, `example`, `rules`, `phase:69.7`, `slice:D2` afterWord metadata and adjacent Given/When/Then clauses. Old D1C receipts remain bound to their immutable integrity subjects; current parent-selected migration evidence is selected-validation-passed / independent-complete-Step-review-pending in the [canonical migration journal](../journal/2026/10/2026-10-04-phase-69.7-migration-checkpoints.md#actual-d2-selected-validation-and-retained-execution-history).


## Historical D1C evidence boundary

D1C is selected-validation-passed / independent-complete-Step-review-pending:
REP-005 completed 14 suites/132 successes/zero failures/pending/warnings; ACC-001
completed 90 suites/728 successes/zero failures/warnings and one original pending.
Both reader/SBT/wrapper exits were 0 and final serial locks released. Exact actual
result/log/summary paths and the retained REP-001 23 compile errors, REP-002
119/13, REP-003/004 131/1, all four frozen FIX manifests, parent one-token DIRECT
correction, predecessor three/successor two attempts and
DVL-PH697-D1C-START-FIELDS-01 are projected in the
[integrity journal](../journal/2026/10/2026-10-03-phase-69.7-integrity-operations.md).
Those immutable integrity subjects are not D2 validation. Step1/3, Phase0/3,
epoch1, pending-not-waived M2 and both native CPBs remain; no original Step or
Phase acceptance is claimed.
