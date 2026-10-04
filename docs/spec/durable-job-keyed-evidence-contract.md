# Durable Job Keyed Evidence Contract

status=normative-partial-implementation
spec=durable-job-keyed-evidence
phase=69.7
step=JM69-09
slice=JM69-09D1A
implementation=authored-selected-validation-passed-independent-complete-Step-pending
current_receipts=REP004-and-ACC001-selected-validation-passed
step_acceptance=pending
phase_acceptance=pending

## R1 Configured evidence addressing prerequisite

The original Admission, StartIntent and RunningIntent requests carry only a
boundary. They cannot independently address evidence for overlapping Jobs or
identify the retained signed snapshot's actual next semantic revision. This
additive private protocol supplies that address per invocation. It is a
production assembly prerequisite; configured owner, process and CBD Support
acceptance remain owed.

## R2 Closed value envelopes

DurableJobKeyedEvidenceRequest contains jobid: JobId, expectedrevision: Long
and the original DurableJobLifecycleWriteRequest. DurableJobKeyedEvidenceResponse
contains jobid, expectedrevision and the original closed evidence/access pair.
The six nested enum cases and their immutable exact ordered Task read models
remain unchanged. These fields are internal values, never serialized record
fields. No JobRecord, JobTask, ExecutionContext, TaskOutcome, raw body, result,
payload, provider, resolver, credential, clock or timer enters a request.

## R3 Optional source subtype and legacy compatibility

DurableJobKeyedEvidenceSource extends the original Function1 source type and
adds resolve(keyed request). Its final legacy apply refuses with
`durable keyed lifecycle evidence refused` without resolve.
The original source alias, old bridge constructor/defaults and old callers
remain unchanged. An ordinary legacy function still receives only the original
nested request.

## R4 One addressed invocation

fetch(jobId, expectedRevision, request, source) refuses null source, JobId or
request, nonpositive revision, or an ID refused by the existing
DurableJobStorageResourcePolicy.validId. Its native inclusive limit is512 UTF8
bytes; no new grammar, parsing, normalization, hashing or authentication is
inferred. It invokes resolve exactly once with the original key, expected
revision and identical nested request. Existing task equality/local registration
guards precede this dispatcher. There is no retry, discovery, clock/ID issuance,
global revision counter, task getter or retained production cache.

## R5 Response provenance and refusal ownership

A source Failure returns as the identical object, without translation.
Null Consequence, successful null response, null response key, unequal opaque
Job .value or unequal expectedrevision refuses with exactly
`durable keyed lifecycle evidence refused`. No ID, revision, payload, path or
provider failure is interpolated. A matched response returns its identical
evidence pair without copying or repair. Original bridge guards still own null
pair/projection/access, exact projection revision and task Pending-result
validation. Source exceptions retain the original bridge protection behavior.

## R6 Narrow bridge dispatch

All six _evidence callers pass the original validated record.id, original
request and existing expected revision. One subtype dispatch selects fetch for
the optional keyed source; ordinary functions retain evidence(request).
Keyed failure never invokes legacy apply. The existing guard block, validations,
root preflight/count/payment, V2 projection, OCC acknowledgement/entry replacement,
recovery/store equality, protected metadata, timeline/settlement/failure facts
and side-effect order retain their original meanings.

## R7 Retained signed revision authority

New roots request1/2/3 and contiguous actual task/terminal revisions.
Recovered normal JobRun starts from its retained signed revision N and requests
N+1/N+2 and subsequent contiguous task/terminal revisions without Admission.
Checked overflow, stale/ambiguous refusal and native paid authority remain
unchanged. Caller-owned independently configured evidence is not authority
reconstructed from runtime context.

## R8 Genuine native executable specifications

Specifications use the original bridge/engine/signed SQLite store and genuine
reserveRoot/create/publish, ordinary checkpoints and unchanged recovery helpers.
Every database owns one unique child under target/keyed-durable-evidence/work,
with finally-owned provider/worker closure and cleanup of only that child.
No Prepared, lifetime binding, quota singleton, canonical authority or Seed is
fabricated. Source recording observes order; envelope.expectedrevision alone
selects revision. Recipes are separately supplied immutable values.

## R9 Closed recipe and task evidence

Initial Pending evidence separately supplies one-operation descriptors,
authorization/access, definition, empty input and retention. Task requests use
the exact registered read models with the original descriptor helper.
Successful terminal evidence is Succeeded(Absent), with one genuine attempt
using actual started/finished times and maxAttempts3. Recovery preserves the
original protected body and max-attempt policy. This is fixture evidence for
these scenarios, not a production evidence-synthesis policy.

## R10 Executable specification authoring

All twelve leaves below use AnyWordSpec, Matchers, GivenWhenThen and semantic
afterWord metadata: spec:durable-job-keyed-evidence, example:KE/KI,
rules:Rn, phase:69.7, slice:JM69-09D1A. Actual setup/action/assertions have adjacent
Given/When/Then. Each new suite authors a normal shrinking property with
seed69709701 and at least24 successful checks. No new validation pass is
claimed; source authoring does not establish execution success.

| Example | Actual executable leaf | Rules and observable behavior | Status |
|---|---|---|---|
| KE1 | [KE1 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L28) | R2,R4,R5; Every original boundary; exact Job/revision/request/pair, one resolve | selected-validation-passed; complete-Step-review-pending |
| KE2 | [KE2 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L50) | R4,R5; Foreign Job response refuses with fixed redacted diagnostic | selected-validation-passed; complete-Step-review-pending |
| KE3 | [KE3 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L67) | R4,R5; Stale/future response revision refuses; matched projection stays untouched | selected-validation-passed; complete-Step-review-pending |
| KE4 | [KE4 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L88) | R3,R5; Same provider Failure object; final legacy apply refuses without resolve | selected-validation-passed; complete-Step-review-pending |
| KE5 | [KE5 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L110) | R4; Null/nonpositive/oversized admission guards; inclusive native 512 UTF8 bytes | selected-validation-passed; complete-Step-review-pending |
| KE6 | [KE6 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L143) | R5; Null response envelopes refuse; matched missing pair remains bridge-owned | selected-validation-passed; complete-Step-review-pending |
| KE7 | [KE7 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L163) | R3,R6; Actual legacy Function1 admission singleton, original failure and no publication/body | selected-validation-passed; complete-Step-review-pending |
| KE8 | [KE8 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceSpec.scala#L192) | R2,R4,R5; Independent opaque key/revision property; seed69709701, shrinking, minimum24 | selected-validation-passed; complete-Step-review-pending |
| KI1 | [KI1 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L33) | R6,R7,R8,R9; Latch-controlled actual two-Job overlap; independent signed revisions1..6 | selected-validation-passed; complete-Step-review-pending |
| KI2 | [KI2 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L94) | R6,R7,R8,R9; Genuine Published root/ordinary revision2/fresh SQLite recovery; requested3..7 | selected-validation-passed; complete-Step-review-pending |
| KI3 | [KI3 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L168) | R5,R6,R7,R8; Four native faults at all six boundaries; exact prior ack/paid authority/body distinction | selected-validation-passed; complete-Step-review-pending |
| KI4 | [KI4 leaf](../../src/test/scala/org/goldenport/cncf/job/DurableJobKeyedEvidenceIntegrationSpec.scala#L259) | R6,R7,R8,R9; Independent admission/drain permutations and caller recipes; real SQLite seeded shrinking minimum24 | selected-validation-passed; complete-Step-review-pending |

## R11 Original closure obligations retained

D1A is an uncommitted partial Slice. Phase69.7 and JM69-09 remain IN_PROGRESS;
JM69-10 remains OPEN. Original complete independent protected Step review,
all prior M2 and CPBs, remaining native/all17 correlation, configured production
owner/lease/health/maintenance, integrity/backup/restore/migration, actual
JM69-10 two-process and CBD fresh-runtime acceptance, Phase69.4–69.6 ancestry
and deferred audits, aggregate full validation, sole epoch1 full Phase review
and local release remain required. All original full-Step/Phase gates remain
unchecked. No push, publication, deployment or successor work is authorized.

Historical C5 REP002 (55 tests/eight suites) and ACC001 (608 tests/75 suites,
one original pending) retain their exact old subjects and failure chronology.
They do not validate the new bridge edits. All original75 suites and that
pending example remain selected in order, with the two new suites appended.
Current representative and77-suite accumulator receipts passed; evidence is below.
Unwaived M2, MCR-PH697-C4-BOOTSTRAP-ISOLATION-01,
CPB-PH697-NRT-DISPATCH-REFUSAL-01 and
CPB-PH697-NRT-CANCEL-SETTLEMENT-01 remain original Step obligations.



## D1A selected validation evidence — 2026-10-03

`PH697-D1A-REP-004` passed **68/68 tests in5 suites**, zero failed/pending.
`PH697-D1A-ACC-001` passed **620/620 tests in77 suites**, zero failed
and one original pending example. The ordered accumulator keeps the original75
suites and appends the two keyed-evidence suites. All twelve KE1–KE8 and KI1–KI4
leaves passed; KE8 and KI4 retained seed69709701, ordinary shrinking and at least
24 successful checks each. Actual SBT/wrapper/current-subject reader exits were0,
permissions consumed and final serial locks released. Compiler warnings were0;
JVM runtime diagnostics remain separately classified.

The original failures remain recorded. REP001 failed Test/compile at nine
abstract Consequence.conclusion accesses; the exact two-Spec repair extracts the
original Conclusion via Failure and fails on unexpected Success. REP002 failed
at Conclusion.code; the one-line repair uses status.webCode.code while retaining
full Status equality and effective-message equality. REP003 compiled, with
66/68 tests passing: KI2 recovery refused and KI4 shrinking raised an invalid
JobId recovery bridge. Fixed hyphens in test entropy collided with the existing
UniversalId delimiter and EntityId alphanumeric/underscore policy. The two
fixed literals now use keyed_recovered_root and first_/second_; independent
generators, shrinking, GWT, assertions and numeric signed/native authority
expectations remain unchanged. All three failed receipts retain reader2,
SBT/wrapper1 and released locks. The three byte-changing pre-review repairs
are complete; no independent review or original repair-counter reset occurred.

These receipts retain their actual tested eleven-path subjects. This six-doc
factual projection changes no tested program or declared input and does not
rebind old receipts. Five tested programs,141 preserved paths and425 declared
source observations remain exact, including the admitted external Cozy context.
The additional EntityId source read confirmed its delimiter-safe entropy rule;
no upstream source was edited or external feature acceptance claimed.

Phase69.7/JM69-09 remain IN_PROGRESS; JM69-10 remains OPEN. D1A supplies one
production prerequisite. Complete original protected Step review, all prior
unwaived M2/CPBs, remaining native/all17 correlation, configured production
owner/lease/health/maintenance, integrity/backup/restore/migration, actual
two-process/CBD fresh-runtime acceptance, predecessor69.4–69.6 ancestry/deferred
audits, Step acceptance/local commits, aggregate full validation, sole epoch1
full Phase review and final local release remain required.
