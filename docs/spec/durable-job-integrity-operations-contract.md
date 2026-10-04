# Durable Job Integrity Operations Contract

status=selected-validation-passed
review_status=independent-complete-Step-review-pending
phase=69.7
step=JM69-09
slice=JM69-09D1C-readonly-durable-integrity

This contract fixes the bounded, explicitly sourced, read-only inspection inside the original JM69-09D obligation. The original signed record, native provider, payment and ownership policies remain authoritative.

## I1

Add bounded read-only operational integrity inspection inside original JM69-09D. It inspects only the trusted explicitly supplied startup-source candidates, not a global census/backup certificate or execution permission. No repair, bootstrap, migration, checkpoint, put/delete, CAS, quota charge/release, ID allocation, clock/timer read, live-task registration, rehydration port, scheduler/worker activation or runtime replay. Existing signed schemas, native provider policy, ownership/payment and CPBs remain unchanged.

## I2

Public additive JobEngine.inspectDurableIntegrity(maxCandidates:Int=100)(using ExecutionContext):Consequence[JobDurableIntegrityReport], concrete default unsupported after exact new JobExperiencePolicy(JobExperienceScope.Operator).authorizeScope. InMemoryJobEngineDurableRuntimePart overrides it, authorizes the actual caller first, admits 1..1024 before source/provider access, selects actual owner under a short State monitor and executes outside monitors. JobExperienceService.operatorIntegrity(maxCandidates:Int=100) uses the same Operator policy and delegates this engine method. No new HTTP/JCL route, CLI config, catalog/metric/audit schema or separate facade.

## I3

Public JobDurableIntegrityStatus closed enum: Healthy, Inactive, Missing, Corrupt, AccessRefused, MigrationRequired, LifetimeRefused, PayloadRefused, ResourceBoundRefused, Stale, Unavailable. Immutable JobDurableIntegrityEntry(ordinal:Long,status:JobDurableIntegrityStatus) and JobDurableIntegrityReport(entries:Vector[JobDurableIntegrityEntry],physicalBytesRead:Long); isHealthy is true only for a nonempty vector whose entries are all Healthy. At most1024 entries, ordinal/order exactly source vector, physicalBytesRead only actual inspected payload bytes including overflow sentinel. No job IDs, stored records, subject/tenant/scopes, references, paths/URLs, digest strings, credentials, provider/cause/exception text, live contexts or tasks in public values.

## I4

New private[job] final DurableJobIntegrityInspection(source,store,payloads,lifetime,maxPhysicalBytes:Long=16777216) provides inspect(maxCandidates) using injected ExecutionContext. maxPhysicalBytes must be positive <=16777216, private lower-only construction for native specs, no public override/config. Reuse authoritative startup source whole-vector validation through one tiny package-private _admitted_candidates_c(maxcandidates) forwarding existing _source_candidates; do not call recover/recoverRuntime/recoverTerminal. Exactly one source call with exact admitted limit; original null/duplicate/ordinal/access/Option-container validation before any prefix native loads. Additionally reject whole vector if any ID fails original DurableJobStorageResourcePolicy.validId before any prefix load. Source refusal/null/throw or invalid constructor/bounds is only fixed Consequence.stateInvalid("durable integrity inspection refused").

## I5

For each admitted candidate, actual store.loadStartupRecovery(jobId, candidate.access), preserving exact native provider signature/canonical identity and tenant/subject/scopes. Missing->Missing, Corrupt->Corrupt, Refused->AccessRefused. No candidate access synthesized from Job, caller or Task. Authenticated nonActive Expired/Deleted/Tombstoned ->Inactive, no Blob/lifetime read or re-sign/resurrection. Authenticated Active V1->MigrationRequired, no automatic conversion or newly paid entry. Active V2 requires actual readonly lifetime.recoverySnapshot(snapshot,access,store); failure->LifetimeRefused, no recoverRoot/payment reconstruction/CAS. replayEvidence shape is required by trusted discovery only, never grants execution or replaces native proof.

## I6

Factor only the existing immutable metadata portion of DurableJobOwnedBlobPayloads.prepare into a private helper returning its original private VerifiedTarget vector. Original prepare still calls original _quota.prepareTargets and original metrics exactly as before; delete/put/rollback and released cleanup semantics unchanged. New inspect(record,budget) calls shared immutable metadata helper, strict readonly Published quota admission and bounded physical verification. Whole reference count<=1024, original source order distinct owned targets, original owner/binding/native-store/content-type/size/digest proofs retained. Protected shared/external/definition canonical-ID collisions and conflicting duplicate refs refuse before any physical get. Zero-owned returns Verified with no Blob/provider/quota requirement. No value-copy/normalization that weakens original guards.

## I7

Add readonly DurableJobOwnedBlobQuota.inspectTargets(metadata:Vector[DurableJobOwnedBlobQuotaMetadata]):Consequence[Unit]. Reuse _admit and _load, require an existing strictly decoded policy-matching native ledger, and for every exact metadata fact require matching entry with state Published. Missing/malformed/native failure, Pending, Released and wrong owner/binding/policy fail closed. Never call _change/_reconcile/_bootstrap/scan/create/update/CAS/release. Original prepareTargets acceptance of Released remains unchanged for idempotent cleanup. Collection registration by original _admit remains pure registry setup, not persisted repair.

## I8

Define private[job] DurableJobIntegrityByteBudget in the new inspector file: per-operation max16777216 bytes, positive lower-only test construction, accumulated reserved and actual Long counters; reserve whole candidate cost before any of its physical gets, count expected byteSize+1 sentinel per distinct owned target, reject excessive per-payload original policy.maxpayloadbytes and aggregate inclusive remaining capacity without allocating payload bytes. Checked bounded sum/remaining arithmetic, reservations never returned on refusal. private[job] DurableJobPayloadIntegrityOutcome enum in Payloads file: Verified, Refused, ResourceBoundRefused. inspect returns Consequence[outcome]; physical/metadata/quota failures map PayloadRefused, budget refusal maps ResourceBoundRefused. Repeated target within one Job checked once, no dedup across Jobs or transfer of ownership.

## I9

After complete candidate metadata, strict Published quota and aggregate byte admission, one BlobStore.get per distinct owned target. Returned result must be non-null and id/storageRef/contentType/byteSize/digest match exact immutable fact (digest case-insensitive SHA spelling) and BinaryBag non-null. Compute actual SHA-256 incrementally via an8192-byte buffer, read at most expected byteSize+1 (sentinel), require exact count and digest; never readAllBytes or ByteArrayOutputStream. Zero-length read uses one-byte fallback with same budget, EOF/truncation/overrun/digest mismatch/provider exception ->Refused; close the single opened stream in finally including zero-read/throw. PhysicalByteRead increments actual reads only. No new filesystem/path parsing product code. Existing data-integrity SHA-256 is original payload semantics, never management/control hashes.

## I10

After successful active V2 lifetime/payload proof, reread actual store.loadStartupRecovery with same access and require the exact initial DurableJobStoreSnapshot including provider revision; equal->Healthy, any mismatch or alternate facet->Stale. It is bounded point-in-time per-record evidence; no atomic cross-record/Job-Blob-quota snapshot or historical restore certificate is claimed. Original records/payment ledgers/Blob metadata+bytes retain exact canonical text and provider revisions throughout inspection in a quiescent fixture.

## I11

New Owner _inspect_integrity_c executes the whole source-to-final-read operation under its original actual SystemNode binding lease, including complete physical stream consumption/close. Use inputs.context/native source internally, caller ctx only admits public Operator scope. Short owner/engine Ready admission before effects; Starting/Unconfigured/Closing/Closed refuses, existing Degraded (internal Ready with sticky fault) may inspect without healing. No owner or engine monitor around source/provider/stream I/O; acquired lease retains actual node pool until completion during drain. Failed operation or any report status except Healthy/Inactive sets existing sticky _degraded; successful inspection/empty report never clears it. Existing health access remains pure/no-I/O and no new logger/event/metric framework.

## I12

InterruptedException restores thread interrupt and returns the fixed operational refusal, never continues or publishes a partial healthy report. NonFatal unexpected source/record/provider/stream errors return fixed batch refusal when not already classified per-record; no raw error text. Store read metrics remain their original actual read observations, all existing facade audit/transport frames unchanged. Positive fixtures use actual managed native SQLite plus LocalBlobStore, real source access and genuinely published lifetime+owned-Blob authority; no stand-alone/in-memory substitute, fake Published labels/paid seed/capacity reset or weakened production guards.

## I13

Create exactly IP1..IP10 and II1..II6 behavior leaves listed below, two AnyWordSpec+GivenWhenThen suites and one shared private fixture. Every leaf has adjacent concrete Given/setup, When/action, Then/should. IP10 and II6 use ScalaCheck seed69709703 and minimum24 successful cases with ordinary bounded generators/shrinking. Extend actual observed native fixtures by composition in new owned fixture only; leave prior JobDurableRuntimeFixtures/JobDurableLifetimeRecoveryFixtures byte-identical. Actual native write/CAS counters and canonical ledger plus native revision equality, Blob get/stream close, source/port/task/clock/ID/timer counters are behavioral oracles; no source-text/header/checklist assertion and no snapshot println. GWT remains visible.

## I14

New normative integrity contract and design with I1..I14 and explicit native lease/source/auth/readonly ledger/physical budgets/redaction/race semantics precede code; append docs/spec/job-operations-contract and Phase/checklist partial D1C authorship/test-pending, keep JM69-09 IN_PROGRESS/JM69-10 OPEN. Actual D1B REP00817suites192/192 and ACC00188suites712success+1existingpending zero failures/aborts/warnings, reader0/SBT0/lockreleased with exact saved receipt/log locators appended to D1B journal and Phase projections. Retain previous failed chronology192/190/2 and native12->5->2->0. New integrity journal records causal prepareTargets CAS issue, frozen19paths/minPython>=3.12 tooling, selected validation and pending results; no invented receipts/acceptance. Backup/restore/migration execution, all17 correlations, actualJM10 processes/CBD, ancestry/Step reviews/aggregate/solefullreview/localrelease remain explicit unfinished original obligations.

## Executable specification binding

The two AnyWordSpec suites display the contract and rule bindings with afterWord and adjacent Given/When/Then. These authored expectations passed the selected D1C validation recorded below; independent complete original Step review remains pending.

| Example | Suite | Required behavior |
| --- | --- | --- |
| IP1 | DurableJobIntegrityInspectionSpec | Reject invalid bounds and whole malformed/duplicate/oversize-ID source vectors before any prefix native load, including valid first candidate followed by invalid tail; exactly one admitted source fetch and constant redaction. |
| IP2 | DurableJobIntegrityInspectionSpec | Inspect genuinely published active V2 roots with zero-owned and owned actual local bytes ->Healthy; exact all three canonical quota/Job/native revision snapshots and Blob metadata/bytes unchanged, zero native writes/CAS/task/port/IDs/clock/timer. |
| IP3 | DurableJobIntegrityInspectionSpec | One valid whole source vector containing actual Missing, tampered canonical Corrupt and intact unauthorized AccessRefused produces ordered ordinal-only facets without raw provider or identity text; unaffected good candidate still inspected. |
| IP4 | DurableJobIntegrityInspectionSpec | Actual authorized signed Expired/Deleted/Tombstoned values ->Inactive without physical get/lifetime reads or revival; no false Healthy certificate. |
| IP5 | DurableJobIntegrityInspectionSpec | Authorized intact signed Active V1 ->MigrationRequired with byte/revision unchanged, no migrated record/lifetime reservation/Blob read. |
| IP6 | DurableJobIntegrityInspectionSpec | Active V2 with native missing/Pending/wrong-policy/wrong-paid lifetime authority ->LifetimeRefused; exact absence/canonical/revision retained and no repair or payload get. Start ready owner before deliberately damaging test-native ledger. |
| IP7 | DurableJobIntegrityInspectionSpec | Same owned/shared or definition-source canonical Blob ID, conflicting duplicates and metadata owner/binding/size mismatch refuse the whole candidate before first physical get; genuine owned bytes remain and no capacity refund. |
| IP8 | DurableJobIntegrityInspectionSpec | Strict existing native Blob quota Pending/Released/missing/malformed/wrong-binding/policy refuses with no CAS/bootstrap/reconcile/census and exact native rows unchanged; repeated owned refs check one target only. |
| IP9 | DurableJobIntegrityInspectionSpec | Actual local physical tamper/truncate/overrun and explicit BlobStore fault wrapper streams exercise digest/count/metadata refusal, zero-read fallback and thrown/interrupt paths; single opened stream closes finally and consumed bytes<=expected+1. |
| IP10 | DurableJobIntegrityInspectionSpec | Seeded finite physical budgets test inclusive expectedBytes+sentinels across two real distinct owned targets/jobs, lower-only private budgets exactfit/one-under, perpayload ceiling and duplicate order; excessive whole-candidate reservation causes zero get for that candidate, later total bytes never exceed cap. |
| II1 | JobDurableIntegrityIntegrationSpec | Actual configured original InMemory engine and sole JobExperienceService Operator methods agree with real native results; job_admin/content_admin callers allowed while no-capability caller even with malformed limit causes zero source/provider/owner reads. |
| II2 | JobDurableIntegrityIntegrationSpec | Legacy/unconfigured engine concrete unsupported default and actual Starting/Closing/Closed owner refuse with zero discovery/native effects; empty legitimate source report is nonhealthy and does not degrade a previously healthy owner. |
| II3 | JobDurableIntegrityIntegrationSpec | Actual managed node lease covers blocked physical Blob stream through complete close: explicit barriers show node shutdown cannot physically close managed pool until admitted integrity operation finishes; no engine monitor held during source/stream callbacks. Always release/join bounded workers finally, no Thread.sleep. |
| II4 | JobDurableIntegrityIntegrationSpec | Actual competing optimistic native Job checkpoint while stream verification is blocked yields Stale at final exact snapshot comparison, preserves the winning record and all quota charges, inspection creates no overwrite/revision. |
| II5 | JobDurableIntegrityIntegrationSpec | Repeated and fresh actual managed owner inspections preserve existing paid lifetime and owned-Blob usage/canonical native revisions captured after ordinary startup, cause no new task/port/ID/clock/timer effects; a real refusal sets sticky Degraded and later Healthy report does not heal it. |
| II6 | JobDurableIntegrityIntegrationSpec | Seeded bounded hostile secrets/control Unicode in actual corrupted native Job/provider-failure context remain absent from all public report and fixed failure projections; exact unauthorized access remains refused even for public Operator privilege and no authority borrowed from candidates/tasks. |


## Actual D1C selected validation and retained history — projected 2026-10-04

status=selected-validation-passed
review_status=independent-complete-Step-review-pending
step_JM69_09=IN_PROGRESS
step_JM69_10=OPEN

`PH697-D1C-REP-005` completed **14 suites, 132 successful tests, zero failed or
pending tests and zero compiler warnings**. `PH697-D1C-ACC-001` completed
**90 suites, 728 successful tests, zero failed tests, one existing SqliteDataStore
pending example and zero compiler warnings**. Both actual typed readers returned
0, SBT/wrapper returned 0, and both final serial markers reported `lock=released`.
The accumulator retains the original 88 suites in order and the two D1C suites.
IP1..IP10 and II1..II6, including the seed-69709703 shrinking properties with
minimum 24 successful cases, passed this selected validation. The receipts retain
`PHASE-69.7-integrity-operations` subject revisions 11 (representative) and 12
(accumulator); these historical subjects are not rebound to D2 programs or inputs.

- Representative saved result: `/Users/asami/src/dev2026/goldenport-cncf/.codex-workflow/phases/PHASE-69.7/integrity-operations01-validation05-result.json`.
  Actual executor result: `/private/tmp/skill.cncf.d/a-a415765c-8b34-452b-b7a0-fe4eee7cbab1/command-result.json`.
  Actual log: `/private/tmp/skill.cncf.d/a-61c8003d-6a58-4462-a0b9-7215e7f239e5/12324-20261003T142940Z.log`.
  Actual summary: `/private/tmp/skill.cncf.d/a-61c8003d-6a58-4462-a0b9-7215e7f239e5/12324-20261003T142940Z.summary.json`.
- Accumulator saved result: `/Users/asami/src/dev2026/goldenport-cncf/.codex-workflow/phases/PHASE-69.7/integrity-operations01-accumulator01-result.json`.
  Actual executor result: `/private/tmp/skill.cncf.d/a-91be6c10-c201-4c60-b68c-ee4632aabf71/command-result.json`.
  Actual log: `/private/tmp/skill.cncf.d/a-2a5e001e-62ca-4176-8e2d-46c9c3ab0625/12684-20261003T143221Z.log`.
  Actual summary: `/private/tmp/skill.cncf.d/a-2a5e001e-62ca-4176-8e2d-46c9c3ab0625/12684-20261003T143221Z.summary.json`.

The failed chronology remains: REP-001 recorded **23 compile errors**, with no
tests run; REP-002 recorded **119 successful / 13 failed** native tests; REP-003
and REP-004 each recorded **131 successful / one failed**. The frozen revision-1
FIX manifests remain historical evidence: `PHASE-69.7-integrity-fixture-qualifier-fix`
qualified the 15 new fixture calls after ScalaTest shadowing;
`PHASE-69.7-integrity-native-fixture-fix` retained the actual current-lease managed
SQL view and original accepted descriptor/schema facts;
`PHASE-69.7-integrity-terminal-fixture-fix` supplied genuinely paid, representable
Own/PerTask terminal descriptors and actual TaskIds; and
`PHASE-69.7-integrity-terminal-id-token-fix` removed the sole deterministic entropy
separator (`terminal-$index` -> `terminal$index`). That final one-token correction
was executed by the parent under its recorded M1 DIRECT admission in
`integrity-operations01-parent-direct-fix01.json`; all other bytes were preserved.
The parent-owned selection spelling correction also remains recorded separately.

Pre-review predecessor `D1C-pre-review01` consumed **three attempts**
(REP-001/002/003). Its evidence-backed narrower successor
`D1C-pre-review-terminal-fixture02` consumed **two attempts** (REP-004/005),
including the parent token correction. Neither cumulative history is reset.
`DVL-PH697-D1C-START-FIELDS-01` remains recorded. Step repair cycle **1/3**, Phase
repair cycle **0/3**, original epoch **1**, all pending-not-waived M2 assurance and
both `CPB-PH697-NRT-DISPATCH-REFUSAL-01` and
`CPB-PH697-NRT-CANCEL-SETTLEMENT-01` remain unchanged. D1B's failed and passing
receipts, native 12 -> 5 -> 2 -> 0 progression, consumed compile/native attempts
and disclosure history remain historical and unchanged.

This is selected-validation-passed / independent-complete-Step-review-pending
only. Complete original JM69-09 C/D/security/retention/hostile fixtures, all 17
native lifecycle/global Event/Operation/Workflow/definition/CallTree/payload/
recovery correlations and actual backup/restore remain required. At this historical D1C boundary, D2 explicit
checkpoint authoring had its own pending validation. Current D2
[selected-validation-passed evidence](../journal/2026/10/2026-10-04-phase-69.7-migration-checkpoints.md#actual-d2-selected-validation-and-retained-execution-history)
is separate; these D1C receipts do not validate D2. Original JM69-10 actual
two-process/scheduled-retry/crash/cursor/payload/corruption/version/migration/JCL/
governance/CompositeQuery/user-admin/notification/security and CBD fresh-runtime
acceptance, predecessor 69.4–69.6 ancestry/deferred audit, complete independent Step
reviews and local acceptance commits, aggregate final full/downstream validation,
sole epoch-1 full-Phase review and final local version/release commit remain open.
No Step, Phase, predecessor or release acceptance is established by these receipts.
