# Durable Job Integrity Operations Design

status=selected-validation-passed
review_status=independent-complete-Step-review-pending
phase=69.7
step=JM69-09
slice=JM69-09D1C-readonly-durable-integrity

The [normative contract](../spec/durable-job-integrity-operations-contract.md) fixes I1..I14 before implementation. IP1..IP10 and II1..II6 are its executable leaves. Inspection is evidence for supplied candidates at a bounded point in time.

## Authorized owner and lease

The sole JobExperienceService Operator method and additive JobEngine method use the existing exact Operator policy before bounds or owner lookup (I2). The engine selects a Ready owner inside a brief State monitor; the owner checks Ready inside its own brief monitor. Existing Degraded is internally Ready and may inspect. Neither monitor encloses source, provider or stream I/O (I11).

The original actual SystemNode binding lease surrounds discovery, every native load, lifetime and Blob proof, physical stream consumption and close, and the final native reread. The internally supplied ExecutionContext owns native effects; the public caller admits only Operator scope. Logical close cannot invalidate an already acquired node lease before its complete operation drains. Existing sticky health faults are set for failure or any status other than Healthy/Inactive and are never cleared by inspection. Health reads remain pure (I11,I12).

## Discovery and native authority

One package-private forwarding seam shares the startup coordinator's existing whole-vector shape, cardinality, ordinal, duplicate, access and Option-container admission. The inspector additionally checks every ID against the original storage-resource bound before loading any prefix (I4). Replay evidence is discovery shape only; it grants no execution or payment authority.

Each candidate retains its exact original access for loadStartupRecovery. Missing, Corrupt and Refused map to closed ordinal-only statuses. Authenticated inactive records and Active V1 stop before lifetime or Blob access. Active V2 requires the original read-only Published lifetime recoverySnapshot and exact initial native snapshot (I5).

## Immutable ownership, Published quota and physical stream

The immutable metadata portion of prepare is shared without changing its original quota preparation, metrics or cleanup behavior (I6). Shared/external/definition ID collisions and conflicting duplicates refuse the complete candidate before any physical get. Zero owned targets require no Blob quota/provider. Nonempty targets require the existing, strictly decoded policy-matching native owned-Blob ledger and exact Published entries through inspectTargets. That method never invokes the mutation, reconciliation, census or bootstrap paths (I7).

A per-operation private byte budget reserves the complete candidate before gets. Each distinct target reserves expected size plus one overflow sentinel. Policy payload ceiling and aggregate inclusive remaining capacity use checked Long arithmetic. Reservations persist after a refusal; duplicate targets are read once within a Job, with no cross-Job deduplication or ownership transfer (I8).

One get per admitted target must return the exact immutable metadata and a non-null BinaryBag. A single opened stream is hashed incrementally with an 8192-byte buffer. At most expected size plus one bytes are consumed, zero reads use a one-byte fallback, and every opened stream closes in finally. Exact size and case-insensitive SHA-256 equality are required (I9). Actual consumed bytes alone enter the public report; metadata and reservation do not add bytes.

## Race and failure semantics

After successful proofs, the final authorized loadStartupRecovery must equal the initial snapshot, including provider revision. Any alternate facet or mismatch is Stale. This gives per-record evidence and never claims an atomic cross-record/Job-Blob-quota snapshot, historical restore certificate or census (I10).

Interrupt restores the current thread flag and refuses the batch. Unexpected NonFatal faults refuse with the fixed operational label; classified metadata/quota/physical faults remain PayloadRefused. No partial healthy report escapes. Public values carry only ordinal, closed status and byte count; empty/all-inactive reports are not isHealthy certificates (I3,I12).

## Native executable evidence and remaining work

The new private fixture composes the original managed native SQLite and LocalBlobStore fixtures, genuinely publishes root lifetime and owned-Blob authority, and observes actual writes/CAS, exact canonical rows and provider revisions, Blob gets/stream close, source/port/task/ID/clock/timer effects. Two seeded properties use seed 69709703 and at least 24 successful cases (I13). Prior fixtures remain byte-identical.

I14 retains D1B's immutable passing receipts separately from the actual D1C selected validation recorded below. Backup/restore and explicit migration execution, all 17 native correlations, actual JM69-10 processes/CBD acceptance, predecessor audit, complete Step review/commits, aggregate validation, sole epoch-1 Phase review and local release remain unfinished original obligations. JM69-09 remains IN_PROGRESS and JM69-10 remains OPEN.


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
