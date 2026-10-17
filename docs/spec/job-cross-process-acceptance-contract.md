# Job Cross-Process Acceptance Contract

## Canonical owned-result process consumer

The [current-caller query contract](durable-job-operation-response-query-contract.md)
and [design](../design/durable-job-operation-response-query.md) define a separate
genuine Task-produced owned result consumer. Original CP1..CP4 retain their
default Supplied Absent, default-policy refusal and authorized management
UnavailableAfterRestart semantics, recipes, receipts and classpath sources.
The new isolated writer/reader/foreign-reader launcher uses the exposed original
runtime classpath algorithm and actual absolute Java executable. Actual writer
exit and owner/SQL close precede reader launch. Each child checks its own exact
Review or independently selected ASCII scalar domain, signed semantic6, native
reference/payment/effects and actual resource closure before a closed bounded
observation receipt. The reader imports no writer result, row, provider or
receipt. Eligible queryVisible exposure requires overview then independent
current raw durable authorization inside the same original ready owner's real
lease and existing B2 reader. Only a detached response escapes; raw projection
and management stay body-free, with no reader work, writes or cache.
The [query journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md)
owns authoring and execution accounting. Independent native Review/process
acceptance does not complete the actual CBD provider pipeline or its fresh
runtime completed Review acceptance, which remains mandatory.

status=normative-partial-implementation
implementation=focused_validated_independent_review_pending
initial_authoring_status=authored_test_pending
spec=job-cross-process-acceptance
phase=69.7
step=JM69-10
slice=JM69-10A
recorded_at=2026-10-04

This companion consumes existing production contracts. Exactly CP1..CP4 are
authored in [JobCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobCrossProcessAcceptanceSpec.scala).
Selected execution passed: PH697-JM10A-REP-002 completed five suites/25 succeeded
and PH697-JM10A-ACC-001 completed 111 suites/900 succeeded plus one existing
pending. Both had zero failures/aborted suites/compiler warnings, typed
reader/SBT/wrapper0 and released locks. Independent whole-Step review and
acceptance remain pending; see the [actual A journal](../journal/2026/10/2026-10-04-phase-69.7-job-cross-process-acceptance.md).

## XP1 Actual sequential process and native ownership

Each case owns a unique absolute `target/job-cross-process/work/case-*` tree,
with separate native, control and log directories. The parent launches the
public test-only probe using absolute `java.home/bin/java` and its actual test
runtime file classpath, including the probe and main code sources. Missing
Java or classpath is a failure. Writer exit is confirmed before a separate
reader JVM starts against the same physical `native/jobs.db` and LocalBlob
namespace. Parent, writer and reader carry actual PID/start-instant pairs and
independently generated JVM UUIDs. A new object, engine or classloader cannot
satisfy this process boundary. Native successful Subsystem, SystemNode and
managed SQLite closure must precede each child receipt and normal exit.

## XP2 Original configured composition and execution

Use [DurableJobBackupFixtures.Fixture](../../src/test/scala/org/goldenport/cncf/job/DurableJobBackupFixtures.scala)
and its original configured provider, explicit context, observed
StandardEntityStore, managed SQLite, LocalBlobStore and controlled scheduler.
The writer starts an empty configured owner, submits Persistent Async tasks
under its native owner lease, drains the original engine, and observes exactly
one execution and live `Scalar("ok")` per job. No preseed, row rewrite, paid
singleton fabrication, transferred task/provider/closure or component shadow
state supplies a positive result.

## XP3 Signed terminal and paid native authority

The original [keyed lifecycle contract](durable-job-keyed-evidence-contract.md)
and static independently supplied recipe own the six exact boundaries and
semantic revisions 1..6. The real signed terminal record is
`Succeeded(Absent)`, with the unchanged definition and native published paid
Seed `(2,96,1,1)`, remaining zero, one normal task association, no compensation
claims and no cancellation claim. The fresh reader admits candidates by opaque
native `JobId.parse`, authenticates original native records and paid authority,
and compares selected identity/revision/result/definition/payment observations.
Native record and quota rows are compared locally before and after reader
startup; they never travel in control receipts.

## XP4 Terminal registration and zero reader execution

Original startup reaches Ready with exactly Registered terminal projections,
no runtime task rehydration, no executable job maps, no body/getter/port/queue
execution and no Entity create/update/delete or lifetime-payment write.
Selected native rows remain equal across startup and reads. Closure is checked
against actual resource state. This consumes the
[runtime-owner](durable-job-runtime-owner-contract.md) and
[terminal-projection](durable-job-terminal-projection-contract.md) contracts.

## XP5 Existing result facade limit

The registered query has Succeeded status, durable origin, no input/calltree/raw
result body, `getResult=None`, and authorized management result
`UnavailableAfterRestart` with the original terminal summary. Writer live
results remain Available. Absent is the recipe's actual signed result; it is
not an exact CBD Review response. The
[management detail contract](durable-job-management-detail-contract.md) stays
unchanged. No response is synthesized from a summary.

## XP6 Genuinely empty native storage

CP2 has count zero and no candidates. Both configured children reach Ready
without jobs, executable maps, projections, task/port execution, Entity
mutations or fabricated storage/lifetime/blob quota roots. Actual singleton
native reads return None. Both native owners close successfully.

## XP7 Foreign startup refusal

CP3 starts from a genuinely completed CP1 writer. An independently constructed
foreign candidate subject/access invokes original startup authentication. The
reader expects exactly redacted `durable runtime startup refused`, never Ready,
with no terminal registration, body/port/queue execution or Entity mutation.
Original native rows remain unchanged under the trusted fixture context.
Normal child exit and a receipt occur only after expected-refusal assertions
and actual cleanup pass. An unexpected exception or child nonzero exit fails
the scenario. This is one hostile startup fixture, not complete security proof.

## XP8 Closed observations and bounded generated acceptance

CP4 uses `Gen.choose(1,3)`, ordinary ScalaCheck shrinking, seed `69710001L`,
minimum 24 successes, and a unique tree per generated case. Each case genuinely
launches sequential writer and reader JVMs; `checked.passed` and
`checked.succeeded >= 24` must pass. Receipts have fixed closed keys, at most
16 KiB, count 0..3 and selected metadata only. No signed record, payload, task,
provider, runtime state, content copy or control hash is transferred. Only IDs
are passed from writer observations to reader native candidate lookup; the
reader never reads the writer receipt. Arguments are arrays, never shell text.
Each child waits at most 60 seconds; timeout cleanup terminates and waits only
for that owned child. No sleeps, ports, server or SBT subprocess is introduced.
Logs stay in the owned target tree on failure, with bounded failure excerpts;
successful cases remove only their owned trees. Receipts are never printed.

## Four authored leaves

| Leaf | Rules | Given / When / Then boundary | Evidence |
| --- | --- | --- | --- |
| CP1 | XP1..XP5 | One native Persistent Async task / completed closed writer then fresh reader / exact native facts, facade and zero reader effects | focused_validated_independent_review_pending; REP002 five/25, ACC001 111/900+pending1 |
| CP2 | XP1,XP2,XP4,XP6 | Empty unseeded namespace / closed writer then fresh empty reader / Ready, empty native roots, zero effects | focused_validated_independent_review_pending; same A receipts |
| CP3 | XP1,XP3,XP7 | Completed native writer and foreign reader access / original startup authentication / fixed refusal, unchanged rows, closed resources | focused_validated_independent_review_pending; same A receipts |
| CP4 | XP1..XP5,XP8 | Ordinary bounded shrinking generator / real JVM pair per generated case / exact observations and minimum successful property checks | focused_validated_independent_review_pending; seed69710001L/min24 checked property; same A receipts |

## Original remaining JM69-10 matrix

| Obligation | Boundary after A authoring |
| --- | --- |
| Real scheduled/retry and crash-interrupted recovery | open; later frozen Slice |
| Cursor, payload, corruption, incompatible version, migration | open; later frozen Slice |
| JCL, governance, CompositeQuery, user/admin, notification, security | open; later frozen Slice |
| Independently fresh actual Textus CBD Support runtime and exact completed Review/result without shadow state | mandatory and open; current gateway consumes `queryVisible.model.result`, which this facade cannot provide |
| Focused compatibility regressions and accepted spec/design/guidance promotion | open |
| Phase 69.4..69.6 ancestry and reciprocal deferred full validation | open |
| Complete original JM69-10 independent Step review and local acceptance commit | open; four leaves cannot close the Step |
| Aggregate full CNCF/downstream validation, sole epoch-1 Phase review, version/closure evidence and final local release | open |

The [rejected CBD spike](../journal/2026/08/2026-08-15-cbd-review-job-compatibility-spike-for-phase-69.md)
remains rejected. A changes no production source/API/schema/result resolver,
build dependency or downstream source. JM69-09 remains DONE, JM69-10 OPEN and
Phase 69.7 IN_PROGRESS. No push, publication, deployment or successor work.

## B1 authored companion — 2026-10-04

The [OperationResponse codec contract](durable-job-operation-response-codec-contract.md)
retains revision1's eight authored domain-value specifications and historical
numeric SCOPE_MISMATCH. Revision2 adds ORC9..ORC12 under JM69-10B1 and completes
the frozen six-form numeric frontier, for twelve authored specifications. Its
implementation=authored_test_pending; A receipts supply no codec validation.
It changes none of XP1..XP8 or the original matrix. Framework-owned publication,
physical ownership, authorized exact reading and actual fresh CBD runtime
acceptance remain mandatory later parent-frozen boundaries.

## Current B1 evidence and B2 private payload authoring — 2026-10-04

The preceding B1 authoring-pending account remains historical. B1 subject5 is
focused_validated_independent_review_pending: `PH697-JM10B1-REP-005` completed
three suites/32 succeeded, failed/pending/aborted0; `PH697-JM10B1-ACC-001`
completed 112 suites/912 succeeded, failed/aborted0 and one existing
SqliteDataStoreSpec pending. Both have compilerwarnings0, typed
reader/SBT/wrapper0 and `lock=released`. ORC1..ORC12 passed selected execution;
ORC8 ordinary shrinking seed69710021L/min100 passed actual checked.passed and
checked.succeeded>=100 assertions. These results belong to B1 only.

The [codec journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-codec.md)
retains initial/failed histories, five shrink-map type corrections, semantic
refusal comparison and static expected-Consequence fixture typing, stale Cozy
prerequisite/successful refresh and one-use pre-submit retention recovery.
Complete original Step independent assurance remains unwaived.

The [payload contract](durable-job-operation-response-payload-contract.md),
[design](../design/durable-job-operation-response-payload.md) and
[journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-payload.md) record
B2 initial private native publication/read authoring and RP1..RP12,
authored_test_pending. The selected six-suite representative then 113-suite
accumulator remains pending. Its native domain/fresh-owner fixtures are
same-process evidence only, with no actual completed CBD or separate-JVM claim.
XP1..XP8, A's actual Absent result/facade behavior and all original matrix rows
remain unchanged.

Real terminal runtime publication and authenticated consumer integration,
independently fresh actual CBD completed Review/result without shadow state,
scheduled/retry/crash, cursor/payload/corruption/version/migration and JCL/
governance/CompositeQuery/user/admin/notification/security, compatibility and
accepted spec/design/guidance promotion, Phase69.4..69.6 ancestry/reciprocal
deferred full validation, complete JM69-10 independent Step review/local
acceptance, aggregate full CNCF/downstream validation, sole epoch1 Phase review
and final version/closure/local release remain mandatory. Raw/getResult/
queryVisible/management compatibility and the rejected component CBD spike
remain intact. JM69-09 DONE; JM69-10 OPEN; Phase69.7 IN_PROGRESS; no Slice-only
Step/Phase acceptance, push/publication/deployment or successor work.

## JM69-10B2 current selected execution — 2026-10-04

status=focused_validated_independent_review_pending
subject_revision=3
review_status=complete-original-Step-independent-assurance-pending
acceptance_status=not-accepted

Earlier authored/pending sections remain the original authoring history. Current
`PH697-JM10B2-REP-005` passed six completed suites/60 succeeded,
zero failed/pending/aborted; `PH697-JM10B2-ACC-005` passed the unchanged original
ordered113-suite accumulator/924 succeeded, zero failed/aborted, plus the
one existing SqliteDataStoreSpec pending example. Both had zero compiler
warnings, typed reader/SBT/wrapper exit0, consumed permissions and lock=released.
RP1..RP12 passed; RP12 ordinary shrinking seed69710022L/min24 retained its actual
checked.passed and checked.succeeded>=24 assertions, valid V2 Running/Pending
producer, and both selected V1/V2 completed consumers. Parent complete sixteen-path
mechanical/GWT/compliance admission passed; current Step union22 and twelve
unrelated dirty paths were preserved.

Failure history remains: REP001 stopped before compile/tests on stale Cozy
classpath; the existing authorized `cozyExportRuntimeClasspath` CP001 succeeded
with lock released (three noncompiler missing-credential notices). Its initial
request-retention stop had no submission and used one same-child V4 recovery.
REP002 had thirteen test compile errors from inherited ScalaTest withFixture
resolution; qualifying twelve existing native-helper calls corrected it. REP003
was a parent typed repair-identity preparation rejection, with no child or SBT
submission. REP004 compiled with no warnings and ran six suites:59 succeeded,
one RP12 failure from invalid V1/Pending fixture setup. Two local RP12 lines now
use the valid V2 producer and explicitly retain V1/V2 completed-consumer coverage.
No assertion was weakened, case removed, shrink disabled or snapshot printed.
These are selected test results, not independent acceptance.

JM69-09 remains DONE; JM69-10 remains OPEN and Phase69.7 IN_PROGRESS. Real terminal
runtime publication/authorized consumer integration, the complete process
matrix and independently fresh actual Textus CBD Support completed full result,
predecessor ancestry/deferred audits, complete original Step independent review
and local acceptance commit, aggregate full CNCF/downstream validation, sole
epoch1 comprehensive Phase review and final local release remain mandatory.
The representative native fixture is same-process and does not establish actual
CBD or separate-JVM acceptance. No B2-only review, acceptance, commit, push,
publication, deployment or successor work is recorded.

## Native scheduled and interrupted process companion

This companion consumes the existing runtime-owner, keyed lifecycle, scheduling
and recovery contracts. The executable binding is
[JobScheduledCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala),
spec `job-scheduled-cross-process`, with the four mappings below. The
[design](../design/job-cross-process-acceptance.md#native-scheduled-and-interrupted-process-composition)
defines process responsibilities. Authoring and execution accounting belong to
the [scheduled process journal](../journal/2026/10/2026-10-04-phase-69.7-job-scheduled-cross-process.md).
XP1..XP8, CP1..CP4 and all earlier contracts remain unchanged.

### SC1 Genuine native scheduled admission

The writer MUST compose the original Controlled/Manual runtime, SystemNode and
Subsystem, managed SQLite and LocalBlob, static independently supplied keyed
recipe and genuine Task. Persistent Async submit under the original owner lease
MUST commit Admission semantic1, Submitted/Pending and the paid Seed
`(2,96,1,1)`, remaining1, no normal association, compensation or cancellation
claim. The independently selected delay is 1..20 seconds; fixed examples use10.
No body executes before due. No preseed, publication helper, rewritten terminal
row or synthetic checkpoint supplies the positive writer.

### SC2 Fresh independent unstarted recovery

The new JVM MUST receive only opaque JobId and independently selected delay,
construct original access/replay evidence and static recipe independently, and
provide its own fresh trusted Port/Task before startup. Runtime registration
MUST be Registered and terminal registration Refused. Discovery and Port are
called once. Native paid ownership and rows remain unchanged before execution;
no Admission, payment, refund or signed-record-derived recipe is introduced.

### SC3 Inclusive scheduling and fresh terminal facts

Manual advance to delay-1 MUST drain0 with Task/canonical0. Advance1 to the
inclusive deadline MUST drain1, with Task/canonical1, the five original tail
boundaries and exact semantic revisions2..6. Paid counters remain `(2,96,1,1)`;
remaining becomes0 with exactly one actual normal task association. The live
result is Scalar("ok"); the static recipe supplies signed Succeeded(Absent).
Maintenance callbacks may fire; total callback or clock counts are not the
scheduling oracle.

Only after this reader's graceful closure and exit may a third independent JVM
start on the same database. It supplies no Task and invokes no Port. Original
terminal registration is Registered, runtime registration is not Registered,
and semantic6/Succeeded/Absent/native payment persist. Raw result remains None;
original authorized management is UnavailableAfterRestart. Only that management
observation uses an explicitly composed job_admin context preserving the
original principal, tenant and installed datastore/entity view. Native Job,
storage and lifetime rows MUST remain equal locally across startup/query/drain,
with zero dispatch or entity/lifetime mutation. No exact CBD result is inferred.

### SC4 Actual owned abrupt interruption

Only the owned child JVM may halt itself with `Runtime.getRuntime.halt(71)`.
The unstarted crash writer does so after genuine Admission and pre-due native
assertions. The Running crash writer does so inside the genuine Task body after
committed TaskStart: Running semantic4, Pending result/transaction, paid
`(2,96,1,1)`, remaining0 and one actual normal association. Task runs1 while
canonical completion is0 because run has not returned. Each writes a bounded
pre-halt observation with all graceful closure flags false. The parent MUST
observe this exact child's exit71 and expected marker before launching recovery.
Unexpected normal completion or graceful writer closure cannot satisfy SC4.

### SC5 Safe refusal of interrupted started work

A fresh independently configured reader of the interrupted Running record MUST
observe the original classifier's RecoveryRequired with ActiveLifecycle and
ExecutionAlreadyStarted, and fixed `durable runtime startup refused`. It MUST
never be Ready or executionAvailable, and neither terminal nor runtime may be
Registered. No Port, Task, canonical completion, body retry, replay, mutation,
refund or reset occurs. Running4/Pending and original paid/association facts
remain unchanged. Local native observations use the original Subsystem managed
lease and actual observed entity store, without a ready-owner grant or another
backend. The reader still completes actual graceful resource closure. This
contract does not authorize automatic replay of ambiguous started work.

### SC6 Closed bounded process observations

Each case MUST own an absolute `target/job-scheduled-cross-process/work/case-*`
native/control/log tree and launch only the six closed modes: scheduled-writer,
unstarted-crash-writer, running-crash-writer, scheduled-reader, refused-reader,
terminal-reader. Absolute Java, the existing actual runtime classpath and array
arguments are required. Every actual child PID/start pair and JVM UUID MUST be
distinct. A child has a60-second bound;5-second graceful and5-second forced
cleanup act only on that owned child. Unexpected exit, missing runtime,
timeout or failed assertion is an ordinary specification failure.

Receipts MUST be unique closed Base64 key=value observations, at most16KiB,
containing only mode/root/SQLite/process identity, opaque JobId, status,
semantic/provider revisions, four paid counters, remaining/normal/claim counts,
readiness/refusal, Port/Task/canonical/queue/drain/native-write/source counts and
closure flags. They MUST NOT transfer native rows, signed records, credentials,
Tasks, canonical payloads or writer results. Readers never open writer receipts.
Normal exit0 receipts follow actual SQL/owner/node/Subsystem closure assertions;
pre-halt observations claim no graceful writer closure. Failure logs stay in
the owned tree with bounded excerpts; successful cleanup removes only that tree.

| Example | Scenario | Rules | Executable semantic boundary |
| --- | --- | --- | --- |
| E1_SCH1 | Graceful scheduled trio | SC1,SC2,SC3,SC6 | Genuine scheduled writer / fresh inclusive-due execution / third fresh terminal reader |
| E2_CR1 | Committed unstarted crash trio | SC1,SC2,SC3,SC4,SC6 | Native Submitted1 / actual writer halt71 / fresh due execution and terminal observation |
| E3_CR2 | Running crash and refusal pair | SC1,SC4,SC5,SC6 | Native TaskStart Running4/Pending / actual body halt71 / original safe refusal and unchanged native rows |
| E4_SCH4 | Generated scheduled trios | SC1,SC2,SC3,SC6 | Gen.choose(1,20), ordinary shrinking, seed69710051L/min24/workers1 / real trio per input / exact schedule/payment/closure oracle |

The actual retry process matrix, cursor/payload/corruption/version/migration,
JCL/governance/CompositeQuery/user/admin/notification/security, compatibility,
accepted guidance promotion, actual CBD provider pipeline and independently
fresh completed full Review/result remain mandatory. These four mappings do
not complete the original whole scheduled/retry row or any Step/Phase gate.

## Real automatic retry process companion

The executable binding is
[JobRetryCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala),
spec `job-retry-cross-process`. The
[design](../design/job-cross-process-acceptance.md#real-automatic-retry-process-composition)
defines the native/process responsibilities; the
[retry journal](../journal/2026/10/2026-10-05-phase-69.7-job-retry-cross-process.md)
owns authoring and execution accounting. XP1..XP8, SC1..SC6 and every original
matrix obligation remain unchanged.

### RC1 Genuine failure settlement and keyed evidence

A writer MUST use the original Controlled/Manual native runtime, managed
Subsystem/SystemNode SQLite lease, LocalBlob and original own/per-task Task.
Persistent Async submission MUST execute the Task: its first actual call returns
TaskFailed with RetryNow or RetryLater; its second returns Scalar("ok").
Canonical observation follows each real settlement. Native reserveRetry MUST
pay the actual expansion; no additional root Admission occurs.

The independently constructed immutable recipe supplies original authorization,
definition and retention. A separately counted Probe MUST be the selected
DurableJobRuntimeInputs keyed source. Every response matches request JobId and
expectedrevision; original pair() maps the current TaskStart/TaskOutcome models
and Pending versus supplied Absent result. Terminal retry history MUST comprise
two closed attempts derived solely from the current task models: sequential
numbers1/2, Failed/Succeeded, each own startedAt/finishedAt, a fixed independently
supplied retryable failure summary for the first and None for the second.
maxAttempts3, nextRetryAtNone, exhaustedfalse and recoveryRequiredfalse remain.
Neither signed records, live JobRecord, writer receipts nor response ordinals
may choose the evidence.

### RC2 Original immediate and inclusive delayed completion

Immediate drain MUST execute exactly two work items. The exact keyed boundaries
are Admission/Start/Running/TaskStart/TaskOutcome/TaskStart/TaskOutcome/Terminal,
with revisions1..8 and local retry.attemptCount1. Task/canonical counters MUST
be2/2, with no lifecycle write failure facts. The signed V2 terminal is
Succeeded8/Absent, with two distinct Failed then Committed descriptors and four
ordered exact task-id pair events. Native paid authority MUST be
`(4,192,2,2)`, remaining0, two normal associations matching signed task IDs,
no claims and cancelclaimedfalse. This includes genuine additional retry
payment; it MUST NOT be described as no additional payment.

The live task page presents tasks sorted by start timestamp then task ID;
equal fixed start timestamps do not establish attempt order. The page MUST
contain exactly two entries, with totalCount2 and fetchedCount2, pairing the
first and second actual TaskStartIntent IDs with Failed and Succeeded respectively.
Signed descriptors, task events and retry evidence remain chronological.

Default delayed retry remains1min/5min/15min, max3. First drain MUST be1:
live Submitted, retrycount1 and nextdue instant+60s with a pending timer;
signed state remains Running5/Pending, one Failed task, startedAt present,
completedAt equal to the first actual failed task finish at the original fixed
instant2026-10-01T00:00:00Z and no signed retry due; the job is not terminal.
Native retry payment already equals
`(4,192,2,2)`, remaining1 with one normal association. An independent split
1..59 selects advance split/drain0, advance59-split/drain0, then advance1/drain1
at the inclusive60s deadline. Keyed requests1..5 precede due and6..8 follow it.
Maintenance owns timers too; total callbacks MUST NOT be the retry oracle.
Completed native history, payment and Task/canonical2/2 MUST match immediate.

### RC3 Independent fresh terminal reading

Only after writer closure/actual exit0 may a distinct JVM receive the opaque
JobId and independent split. It MUST construct the configured provider,
original startup candidate/access/replay and keyed source independently, supply
no Task and invoke no Port. Terminal projection MUST be Registered and runtime
rehydration not Registered at Ready. Original Succeeded8, two closed task
descriptors, four pair events, Failed/Succeeded retry history and expanded native
paid authority MUST persist.

Raw query.result and getResult remain None. Only the authorized management
observation composes a trusted job_admin context preserving original principal,
tenant and native views; its result MUST be UnavailableAfterRestart.
Supplied Absent cannot establish exact CBD completion. Actual native Job,
storage and lifetime rows MUST remain locally equal before/after startup,
query and drain. Source/Port/Task/queue/drain/native/lifetime writes remain zero.
The actual owner, SQL resource, Subsystem and SystemNode MUST close normally.

### RC4 Actual paid pending writers

The pending-writer MUST exit gracefully only after the genuine first failed
attempt, canonical settlement1, native reserveRetry and real delayed scheduling.
The pending-crash-writer MUST assert the same boundary, write bounded pre-halt
observations with all closure flagsfalse, then halt only its own JVM with71.
The parent MUST observe actual exit71. Both preserve signed Running5/Pending,
one Failed descriptor/startedAt and paid `(4,192,2,2)`/remaining1/one association.
Shutdown MUST NOT be assumed to checkpoint Submitted or persist the live timer.

### RC5 Original refusal of actual pending retry

A fresh JVM MUST independently supply the original candidate plus fresh counted
trusted Port/Task. Startup MUST return exactly "durable runtime startup refused",
never Ready or executionAvailable, and neither runtime nor terminal Registered.
Original classification MUST be RecoveryRequired with exactly ActiveLifecycle
and ExecutionAlreadyStarted, with no failed evidence categories.

No Port/source/Task/getter/canonical execution, queue/drain/replay/reset/refund
or native mutation is permitted. Signed Running5 and expanded native paid facts
and rows remain unchanged. Observation uses the original Subsystem managed
lease, without a failed owner's ready grant. The normal reader closes its
actual Subsystem/node/SQL. An absent installed owner's ownerclosed observation
is vacuous, not evidence of a separately observed provisional owner's closure.

Actual failed-writer pending retry is distinct from NR15's manually seeded
eligible unstarted Submitted/due fixture. NR15 MUST NOT substitute for this
boundary. This contract supplies no positive automatic cross-process retry of
started work and adds no retry-schedule checkpoint or recovery policy.

### RC6 Owned actual processes and ordinary shrinking

Each case MUST own an absolute
`target/job-retry-cross-process/work/case-*/native/control/logs` namespace.
Only immediate-writer, delayed-writer, pending-writer, pending-crash-writer,
terminal-reader and refused-reader modes are admitted. Array argv, absolute
java.home/bin/java, actual runtimeClasspath, distinct actual PID/start/UUID,
sequential child exit0 (own crash71), a60s child bound and owned5+5s cleanup
are required. Receipts are closed unique <=16KiB observations, never signed
snapshots, payloads, credentials, writer results or certificates. A reader
never opens a writer receipt. Failure logs remain owned; successful cases remove
only their own tree. Missing runtime, timeout, assertion or exit failure is an
ordinary failure, with no skip or synthetic success.

RT5 MUST use ordinary shrinking Gen.oneOf immediate/delayed and Gen.choose(1,59),
seed69710061L/min24/workers1. Every generated case MUST execute a complete actual
writer and distinct fresh terminal JVM using the same native/domain/payment/
process/closure oracle; checked.passed and succeeded>=24 MUST hold.

| Example | Rules | Given / When / Then boundary |
| --- | --- | --- |
| E1_RT1 | RC1,RC2,RC3,RC6 | Real RetryNow failure/success / writer exit then fresh terminal JVM / Succeeded8, exact closed attempts/payment, no reader effects |
| E2_RT2 | RC1,RC2,RC3,RC6 | Real RetryLater failure/default60s / split and strict-before/inclusive due then fresh terminal JVM / drain0/0/1, exact native completion |
| E3_RT3 | RC1,RC4,RC5,RC6 | Real failed paid pending retry / graceful writer exit then fresh startup / unchanged Running5 and original safe refusal without replay/refund |
| E4_RT4 | RC1,RC4,RC5,RC6 | Same genuine pending retry boundary / own halt71 then fresh startup / actual crash, no graceful writer claim, unchanged safe refusal |
| E5_RT5 | RC1,RC2,RC3,RC6 | Ordinary mode/split generator / real completed pair per case / exact native/process oracle and actual minimum24 successes |

These mappings do not close the original whole scheduled/retry matrix.
Actual CBD provider pipeline and independently fresh exact completed full
Review/result without component shadow state, every remaining process and
compatibility/promotion obligation, predecessor audits, whole-Step assurance
and aggregate Phase gates remain mandatory. The rejected CBD spike remains
rejected.

## Native management cursor process companion

This companion delegates unchanged request, summary, authorization, ordering,
cursor binding, refusal precedence and resource bounds to
[Durable Job Management Query Contract R1..R4](durable-job-management-query-contract.md).
Its executable consumer is
[JobCursorCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobCursorCrossProcessAcceptanceSpec.scala).
It adds no production interface, persisted cursor or result reconstruction.

### UC1 Genuine native completion and independent registration

A writer starts the original configured managed SQLite, StandardEntityStore,
LocalBlob and registered durable owner. It submits two or three genuine
Persistent Async lifetime-recovery Tasks and drains each exactly once, with
one canonical outcome and the original six keyed boundaries/revisions 1..6.
Each signed record is Succeeded6/Absent, with one own/per-task committed
association, paid `(2,96,1,1)`, remaining zero, no compensation claims and no
cancellation claim. IDs come from the original production delegate.

Each reader independently constructs the original fixture and static trusted
startup candidates, ordered by opaque Job ID with original ordinal, access
and replay evidence. Startup reaches Ready with Registered terminal
projections, without Task installation or executable Job rehydration.
Native signed and per-Job paid facts are checked inside that JVM. Raw query
and getResult remain body-free; authorized management results remain
UnavailableAfterRestart. These summaries do not supply a completed CBD Review.

### UC2 API-only continuation over unchanged authorized native state

The first fresh reader uses the original unfiltered persistent-only query
and an independent limit in `1..count-1`. The counted read policy delegates
to JobQueryPolicy.default with its empty visibility key for every candidate.
Native updated times are equal and checked; summaries therefore follow the
canonical updated-time-descending, Job-ID-ascending order, rather than
submission order. The first page is the exact native prefix, with total count
and an actual opaque API continuation of at most 280 characters.

A separately fresh reader receives only opaque IDs, the actual API cursor,
independent count/first limit and closed mode. With the same restored caller
and filters it invokes queryPage at limit 100 and obtains the exact native
suffix, unchanged total and no next cursor. Combined pages exhaust all
original IDs once. Every summary has exactly the seven existing field names;
no subject, input, result, tasks, timeline, debug or parameters are exposed.
This positive contract starts with a durable reader cursor; it does not assert
that arbitrary live-writer metadata remains identical after restart.

### UC3 Filter and foreign-caller refusal

A fresh reader applying the original page cursor to status Failed receives
the original invalid-cursor request-filter mismatch, before caller, snapshot
or offset mismatch handling. A separate foreign-caller reader retains trusted
startup access but uses a new Principal/PrincipalId for the actual query, with
empty session and capabilities. The original default policy declines every
original submitter. Cursor continuation fails with caller-visibility mismatch;
its independent cursor-free foreign query returns empty entries, total zero
and no continuation. Refusal returns no mixed page, token, foreign count or
principal data and does not relax authorization.

### UC4 Expiry from a genuinely appended native root

Starting with two completed native Jobs and their durable first-reader cursor,
an independent appender opens and verifies the original signed/payment facts.
It genuinely submits and drains one additional Persistent Async Task, with a
distinct production-generated ID and its own six keyed checkpoints and one
canonical execution. Old per-Job rows, signatures and payment associations
remain unchanged; the global native Published quota correctly grows from two
to three normally paid roots. No global-quota immutability is required of
this producer.

After appender exit a separately fresh reader registers all three native Jobs
and uses the earlier actual cursor with the restored original caller. The
authorized snapshot has changed, so the original expired-snapshot authorized-
source-changed Consequence is required. No mixed page is returned. Fabricated
terminal records, source truncation or record corruption cannot replace this
genuine authorized-source change.

### UC5 Original structural bounds precede candidate policy

A fresh reader independently invokes queryPage with literal `%` and a
281-character opaque token. Both return the original malformed-cursor
structured failure before candidate-policy delegation. Another fresh reader
queries limits zero and 101; both return the original `1..100` limit-range
failure before policy calls. The actual failure Conclusion preserves its
classification and fixed message without echoing token or principal input.
No valid token is decoded or re-encoded by this consumer to forge a payload.

### UC6 Local no-effect, process and generated-case invariants

Each reader captures its own native Job/storage/lifetime/blob-quota rows
before startup through the original source callback and compares them around
actual paging and drain. Signed records, paid authority and native rows stay
unchanged. Startup source is called once; keyed checkpoint, Port replay,
Task/getter/canonical invocation, queue/drain, live/runtime Job maps, Entity
create/update/delete and lifetime writes are zero. Successful producer writes
are genuine native effects, never replay or manual terminal seeding.

The parent and each sequential child have distinct actual PID, start instant
and UUID. Each child is an actual JVM using absolute Java and the original
runtime classpath; normal exit follows successful Subsystem/node shutdown,
Stopped state, zero leases, closed managed SQL and closed installed owner.
Each owned child has a 60-second wait and owned-process-only graceful/forced
cleanup bounds of five seconds each. Failure logs remain owned and excerpts
are bounded; success removes only that case's owned tree.

Closed observational receipts are at most 16 KiB, admit counts two through
three and bounded pages, and contain selected identities, native revision/
status/payment/effect/query/closure observations plus the actual public API
cursor. They carry no signed record, canonical encoding, Task, payload,
credentials, copied runtime state or new integrity certificate. Children
never read another child's receipt. Ordinary shrinking chooses count two
through three and dependent first limit one through count minus one with
seed69710071L, one worker and at least 24 actual successful writer/reader/
continuation process cases, checked with the same full oracle.

| Leaf | Rules | Given / When / Then |
| --- | --- | --- |
| E1_CU1 | UC1,UC2,UC6 | Three genuine completed Jobs / two fresh reader pages / canonical exhaustive nonoverlapping native traversal and zero reader mutation |
| E2_CU2 | UC1,UC3,UC6 | Authorized first-page cursor / Failed filter in a fresh reader / original filter mismatch and unchanged native payment |
| E3_CU3 | UC1,UC3,UC6 | Original caller's cursor / genuinely foreign query context after trusted startup / caller mismatch and fresh empty zero-count page |
| E4_CU4 | UC1,UC5,UC6 | Genuine completed native Jobs / malformed and 281-character tokens / original structural failures with zero policy calls |
| E5_CU5 | UC1,UC4,UC6 | Two native Jobs and cursor / actual independent appender followed by fresh reader / newly paid third root and expired authorized snapshot |
| E6_CU6 | UC1,UC5,UC6 | Genuine completed native Jobs / zero and 101 limits / original structured bounds failures before policy |
| E7_CU7 | UC1,UC2,UC6 | Ordinary shrinking bounded count/limit / actual independent JVM trio per case / same full native oracle and checked successes at least 24 |

## Native refusal process companion

status=focused_validated_independent_review_pending
phase=69.7
step=JM69-10
slice=JM69-10C4
manifest=PHASE-69.7-JM69-10C4-validation-fix01@1
validation_status=REP002-ACC002-passed
acceptance_status=not-accepted

The [seven native refusal leaves](../../src/test/scala/org/goldenport/cncf/job/JobNativeRefusalCrossProcessAcceptanceSpec.scala)
consume the original record, startup, terminal-projection and runtime-owner
contracts through the [fresh-JVM probe](../../src/test/scala/org/goldenport/cncf/job/JobNativeRefusalCrossProcessProbe.scala)
and [owned launcher](../../src/test/scala/org/goldenport/cncf/job/JobNativeRefusalCrossProcessFixtures.scala).
The [composition](../design/job-cross-process-acceptance.md#native-refusal-process-composition)
defines the good-terminal/failed-runtime distinction; the
[C4 journal](../journal/2026/10/2026-10-05-phase-69.7-job-native-refusal-cross-process.md)
preserves initial authoring, actual failed REP001, the corrective Plan and
historical planned argv. Actual current revision2 REP002 passed54 tests in8
suites; ACC002 passed973 tests in119 suites with the original pending1.
Both had compilerwarnings0, reader/SBT/wrapper0, consumed permissions and
lock=released; exact selected argv and terminal evidence are recorded there.

### NF1 Genuine completion before negative input

Each writer independently builds the original DurableJobBackupFixtures.Fixture
with its registered provider, managed SQLite, observed StandardEntityStore,
LocalBlob and original lifetime-recovery Task. Original Persistent Async
submit/drain must execute each Task exactly once and complete its canonical
operation once. Original keyed requests have the exact six boundaries and
semantic revisions 1..6. Authenticated pre-fault native records are V2,
Succeeded6/Absent with one Own/PerTask/Committed task and the original definition.
Original Published paid Seed is (2,96,1,1), remaining zero, one normal association,
no compensation claims and no cancellation claim. Positive terminal/payment
fabrication cannot supply these observations.

### NF2 Exactly one native storage fault

After all producer assertions, the writer changes only the selected Job row
using the original fixture's managed native replaceText/removeRow path. The
closed faults are malformed fixed PRIVATE invalid canonical credential text;
replacement of only the exact unsignedBodySha256 by a distinct 64-character
lowercase value; root format.version 2 to 3 without body change or signing;
one trailing ASCII space; and deletion of only the native Job row. For a text
fault, the changed row's canonicalJson MUST equal the exact intended value,
and every field except canonicalJson MUST equal the original row's fields
after removing that key from both rows. Deletion MUST leave exactly None.
Every other complete Job/storage/lifetime/blob-quota row MUST remain equal.
Record.++ appends fields and cannot construct the expected replacement row.
This deliberate negative input
is neither positive recovery nor a product migration or downgrade.

### NF3 Original ordered startup classifications

A separate JVM creates its own original fixture, candidate access and replay
evidence from ordered unique opaque IDs, count 1..3 and the independent case
arguments. Its original discovery source is called once; source.before captures
all selected native rows under the actual startup lease before recovery.
Terminal reports preserve exact IDs/ordinals, Registered for unaffected Jobs,
Corrupt for each text fault and Missing for a removed row. Runtime reports
preserve the same ordered IDs/ordinals with all Refused. Original Conclusion
retains only the effective message `durable runtime startup refused`; health
is Degraded, never Ready, executionAvailable is false and no owner is installed.

### NF4 Healthy terminal observations survive failed startup

Healthy Registered prefix and suffix facts are observable even though the
whole runtime cannot start. Their raw queries retain Succeeded/durable origin,
Persistent policy, input/calltree/result None and getResult None. The bad
candidate has no raw query, status or result. No synthesized payload or false
all-or-nothing terminal registration is admitted. The reader installs no Task,
has no executable durable/runtime Job maps and drains zero work. Original
evidence requests, Port, queue, Task getters/bodies and native create/update/
delete/lifetime writes remain zero. Inspected original ID/clock counters have
zero startup/read/drain deltas and the original manual scheduler has no timers.

### NF5 Published payment retention and native Job authentication

Original lifetime.snapshot authenticates through _published, _authorized and
store.load of the original Job. Every pre-fault writer and healthy reader
candidate MUST authenticate with original access, and its returned snapshot
MUST equal the stored entry.snapshot. Every damaged or missing Job across all
five faults MUST instead fail with the exact effective message
`Durable job lifetime quota unavailable`.

Privileged local test SQL observation MUST decode the original lifetime row
with DurableJobLifetimeQuotaCodec, select exactly one entry for the opaque
Job ID and require Published. Its existing entry.snapshot provides only stored
numeric/normal-association observations: paid Seed (2,96,1,1), remaining zero,
exactly one normal association, empty claims and cancelclaimed false match NF1.
All eight numeric/association facts MUST equal the pre-fault writer facts;
total Published roots equal count. This observation grants no authenticated
spending or public API authority to the bad Job and exposes no token or record.
All selected native rows remain locally equal through startup, queries and
drain. The bad row stays Corrupt/Missing; unaffected records remain
authenticated V2 Succeeded6/Absent. No reader reserve, recoverRoot, refund,
payment reset, re-signing, migration, normalization or reconstruction occurs.

### NF6 Actual processes and resource ownership

Each case owns an absolute target/job-native-refusal-cross-process/work/case-*
tree with native/control/logs. Sequential array-argument launches use absolute
java.home/bin/java and the existing runtimeClasspath() helper. Actual parent,
writer and reader PID/start-instant identities and independently generated UUIDs
are distinct. Writer exit precedes reader launch. Exit0 receipts require
successful actual Subsystem/node shutdownC, Stopped node, activeLeaseCount0 and
closed managed SQL. The writer's installed owner is observed closed; the
reader explicitly records not-installed and claims no observed provisional
owner closure. Each child has a 60-second bound; only the owned child is
terminated/waited for 5+5 seconds on timeout. No sleeps, server, port, nested
SBT or user process manipulation supplies this evidence.

### NF7 Bounded selected observations

Receipts have fixed closed keys, unique IDs, count 1..3 and at most 16 KiB of
selected mode/fault/index/storage/process/closure/classification/payment/effect
facts. Each Job additionally has the closed quotaaccess fact: Authenticated
for every pre-fault writer and healthy reader; RefusedNativeObservation only
for the reader's selected bad Job. Numeric/association paymentFacts equality
excludes quotaaccess and remains complete. Writer terminal observations are
explicitly pre-fault; they do not
claim a post-fault valid signature for the damaged row. The reader never opens
a writer receipt or receives a record, payload, Task, credential or source copy.
Local native equality is a behavior assertion against the actual database,
without a management hash or content certificate. Successful cases remove only
their owned trees; failure logs remain with at most 2048-byte diagnostic
excerpts. Receipts are never printed.

### NF8 Seven executable leaves and ordinary shrinking

All seven leaves use AnyWordSpec/Matchers/GivenWhenThen, should/which/afterWord
metadata for this spec, example, NF1..NF8, Phase69.7 and JM69-10C4, and adjacent
actual GWT boundaries. The generated callback has its own setup/action/oracle
GWT. Ordinary Gen.oneOf five faults, Gen.choose(1,3) and dependent bad index
0..count-1 use seed69710081L/min24/workers1 and a unique real process pair per
case. checked.passed, checked.succeeded>=24 and zero discarded cases are
asserted. There is no skipped, pending, retried or synthetic success substitute.
The producer's Scalar("ok") is the original fixture result; this companion
does not establish actual CBD provider-pipeline or completed full Review acceptance.

| Leaf | Given / When / Then | Rules |
| --- | --- | --- |
| E1_NF1 | Genuine completed native paid Task then malformed text / writer exits and independent native reader starts / Corrupt, all Refused, Degraded, fixed bad quota API refusal, unchanged stored payment and authenticated pre-fault quota, no-effect/closure oracle | NF1..NF8 |
| E2_NF2 | Genuine signed V2 terminal then exact digest replacement / fresh original startup / Corrupt, fixed bad quota API refusal and retained stored payment matching authenticated pre-fault quota, without re-signing or repair | NF1..NF8 |
| E3_NF3 | Genuine signed V2 terminal then root version3 / fresh original version admission / Corrupt, fixed bad quota API refusal, unchanged stored payment and authenticated pre-fault quota, no Ready/downgrade/migration/write | NF1..NF8 |
| E4_NF4 | Exact genuine canonical terminal then one ASCII space / independent native canonical admission / Corrupt, fixed bad quota API refusal, retained stored payment and authenticated pre-fault quota, without normalization or execution | NF1..NF8 |
| E5_NF5 | Genuine completed paid root then only Job row removed / fresh opaque-candidate load / Missing distinct from Corrupt, fixed bad quota API refusal and retained stored payment matching authenticated pre-fault quota, no recreation/refund | NF1..NF8 |
| E6_NF6 | Three genuine completed roots with only middle malformed / original ordered vector in fresh JVM / Registered/Corrupt/Registered, all Refused, healthy body-free queries and authenticated quota, fixed bad quota API refusal with unchanged stored payment | NF1..NF8 |
| E7_NF7 | Ordinary dependent five-fault/count/index generator / actual writer/fresh-reader pair per case / same full oracle including bad quota API refusal, stored payment retention and authenticated healthy/pre-fault quota, at least24 successful generated cases | NF1..NF8 |

REP001 remains a historical failure; actual current revision2 REP002
representative8/54 and ACC002 accumulator119/973 plus original pending1 passed.
Independent whole-JM10
protected M2 assurance and acceptance remain pending. Previous C3 results are
historical evidence for C3 only. Actual CBD source scope/completion, the remaining
original process matrix, compatibility/promotion, aggregate full validation,
sole epoch1 Phase review and final local release remain required. No original
Step or Phase acceptance is inferred from authoring or selected execution of this companion.


## Migration process consumer companion

status=selected_validation_passed_review_pending
phase=69.7
step=JM69-10
slice=JM69-10C5
manifest=PHASE-69.7-JM69-10C5-implementation03@1
validation_status=REP003-ACC001-passed

MG1..MG8 consume the unchanged [M1..M12 migration contract](durable-job-migration-checkpoint-contract.md), its MC/MI examples, and [runtime owner R7](durable-job-runtime-owner-contract.md). This companion adds process acceptance, without changing migration, storage, payment, security or default policy. The [C5 journal](../journal/2026/10/2026-10-05-phase-69.7-job-migration-cross-process.md) owns authoring and execution history.

### MG1 Genuine production before legacy input

A real Persistent Async Job executes the original Task through the original registered managed provider. Each Task runs and canonicalizes once; six keyed evidence boundaries have expected revisions1..6. Signed Succeeded V2 has Absent result and one Own/PerTask/Committed descriptor. Published native paid authority is (2,96,1,1), remaining0, one normal association, no claims and cancelclaimed false. Only then may the existing signed checkpoint fixture deliberately prepare V1. It retains the whole body except the declared contiguous revision preparation. Writer observations never claim reader registration.

### MG2 Owned physical inputs

The optional owned input is exactly bytes[1,2,3], attached with original putObserved and signed checkpoint before legacy preparation. Independently observed original references, complete inputs/body, Blob bindings/metadata, bytes and Published Blob quota remain equal locally. Each enabled reader opens and closes one physical preflight stream per referenced payload; no put/delete/refund occurs. An absent payload retains its original absent Blob quota row.

These genuinely original putObserved-produced case entries retain their original reservation token after confirm: exactly32 lowercase hexadecimal characters. Complete native Blob row equality protects that token and all other stored fields before and after migration and independently fresh startup. This case-specific observation does not require a token on every Published inventory entry.

### MG3 Complete ordered migration and fresh V2

Independent readers receive only closed case parameters and opaque unique Job IDs. They construct original access/replay/static recipe independently and fetch candidates once under the original startup lease. Local observations precede the counter baseline. Every V1 changes to V2 with semantic revision+1/provider revision+1 and every other complete Job body field equal, including all Job times. Already V2 remains exactly equal. Original storage reservation/checkpoint/confirmation may change the storage ledger; its final committed metadata acknowledges the actual destination with no pending reservation. Lifetime and Blob paid rows remain exactly equal. A third enabled fresh V2 JVM starts after the migrating JVM exits and compares all signed snapshots, complete native rows, metadata and bytes locally equal with no checkpoint or re-signing.

### MG4 Original default refusal

Absent and explicit-false migration inputs preserve fixed `durable runtime startup refused` over V1, one source fetch and original bridge binding. No owner, terminal projection, local paid seed or maintenance timer is installed; no native or business mutation occurs. This does not change the production default.

### MG5 Original Operator refusal

Migration enabled with empty capabilities retains original principal/tenant/scopes and refuses before native migration loads beyond source-before observation. No privilege is borrowed. Bridge and installed owner are absent; complete native rows remain equal.

### MG6 Whole-vector invalid-tail refusal

Three genuinely completed legacy roots precede changing only the last canonicalJson to fixed malformed text. All other fields and native rows stay equal. Reader source observation retains Corrupt for this suffix, without inventing a signed snapshot or authenticated bad payment. Whole preflight refuses before any prefix checkpoint; native paid observation is privileged only, and all source/native rows remain equal.

### MG7 Original successful startup effects

Complete ordered terminal reports have exact IDs/ordinals and Registered facts; complete runtime reports have the same IDs/ordinals and Refused facts. Their original R7 XOR publishes Ready for completed Succeeded Jobs. Runtime Job maps and work queue stay empty; no Tasks are installed, Port/evidence/ID/body/drain effects occur. Original terminal registration retains n terminal identities and native lifetime binding keys, commits each authenticated paid seed locally, and leaves resources retained=n/pending=executing=0 with unchanged policy.

For m locally signed V1 sources, original measured native Job writes and acknowledgements each increase by m; total updateDetached writes and the independent Entity CountingClock each increase by exactly3*m (storage reserve, Job update, storage confirm). Creates/deletes/lifetime writes/Blob puts/deletes are zero. Fresh V2 and refused readers have all these native deltas zero. Entity audit time is not Job body time. Counters are neither reset nor normalized.

Successful migration and fresh V2 each register exactly one original manual maintenance timer: source-before pending0 -> Ready1, started/not closed, nextDueAt=engine.timeSource.now+1000ms, lastResult absent, retention source calls0. Manual control and engine runtime time remain initial; this scheduling clock is distinct from Entity CountingClock. Real shutdown cancels the timer and closes its original scheduler. Refused startup has no owner/timer/terminal seed and leaves manual time unchanged.

### MG8 Actual processes, bounded receipts and closure

Absolute java.home/bin/java and original runtimeClasspath launch strictly sequential owned JVMs in unique absolute target/job-migration-cross-process/work/case-* native/control/log trees. Parent and all children have distinct actual PID/start and UUID identities, zero exit and observed termination. Child wait is60s with owned-only5s+5s cleanup; failed logs remain with bounded2048-byte diagnostics, successful owned trees are removed. Closed receipts are <=16KiB, contain exact keys/count1..3/unique IDs and selected original numeric/fact observations only. Readers never open any prior receipt or accept domain state/credentials/Tasks. Full local native/body/paid/physical/counter oracles precede shutdown. Actual Subsystem/node shutdown observes Stopped/activeLeaseCount0/managed SQL not open; installed owners close, refused readers report not-installed only. Fresh raw queries are Succeeded/durable/Persistent/body-free/getResultNone; original Operator queryManagementResult remains UnavailableAfterRestart. No full CBD Review is inferred.

| Example | Given | When | Then |
| --- | --- | --- | --- |
| E1_MG1 | One genuinely completed paid Task, signed V1, no payload | Writer exits, migrating reader exits, fresh V2 reader starts | Complete MG1/MG3/MG7/MG8 body/revision/payment/effect/timer/seed/closure oracle |
| E2_MG2 | Same genuine completion plus original exclusive three-byte owned input | Two distinct enabled readers physically preflight | MG1..MG3/MG7/MG8 exact reference/metadata/bytes and balanced streams, no put/delete/payment change |
| E3_MG3 | Three genuine payload Jobs in V1,V2,V1 order | Entire cached migration then independent fresh V2 startup | Exactly two Job writes/six audit clocks, middle exactly equal, complete ordered facts and all physical/payment assertions |
| E4_MG4 | One closed genuine V1 without payload | Separate default and false JVMs sequentially start | Fixed original refusal with bridge bound, native equality, zero effects/seed/timer and real closure |
| E5_MG5 | One closed genuine paid V1, unchanged security identity, empty capabilities | Separate enabled unprivileged startup | Fixed refusal, native load baseline unchanged before verification, no bridge/owner/mutation |
| E6_MG6 | Three genuine V1 roots, only last canonicalJson malformed | Separate enabled Operator startup consumes whole vector | Zero prefix checkpoints, Corrupt suffix remains unsigned, privileged paid observation and complete native equality |
| E7_MG7 | Ordinary dependent all-V1 count1..3 or mixed count3, payload flag, job_admin/content_admin | Every generated input launches a real writer/migrating/fresh trio | Complete positive oracle, seed69710091L/min24/workers1, passed/succeeded>=24/discarded0 with ordinary shrinking |

All seven leaves in [JobMigrationCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobMigrationCrossProcessAcceptanceSpec.scala) use visible GWT and afterWord spec/example/MG1..MG8/phase69.7/sliceJM69-10C5. E7 has both generator/check/result and per-case setup/action/oracle GWT. The [frozen token-oracle Fix Manifest](../../.codex-workflow/phases/PHASE-69.7/JM69-10C5-validation-fix01.json) supplied the one-cause repair. REP001 stopped on the stale Cozy prerequisite before tests. Actual Cozy refresh exited0 with reader0, lock=released and480 valid entries; its three missing-credentials diagnostics are distinct from CNCF compiler warnings. Actual REP002 completed8 suites/55 tests:52 succeeded,3 failed, zero aborted/ignored/canceled/pending and zero compiler warnings; reader2/SBT1/wrapper1, permission consumed, lock=released. All three payload failures share C5-PUBLISHED-BLOB-TOKEN-ORACLE-01: the new None assertion contradicted unchanged reserve/confirm token retention. Historical pre-validation status was repair_planned_test_pending/REP002-failed-repair-planned, with REP003 representative8 and ACC001 accumulator120 planned-not-run.

Current revision2 selected results are actual: PH697-JM10C5-REP-003 completed8 suites/55 tests,55 succeeded/pending0; PH697-JM10C5-ACC-001 completed120 suites/980 tests,980 succeeded plus only original SqliteDataStoreSpec pending1. Both have failed0/aborted0/canceled0/ignored0, compilerwarnings0, typed reader0/SBT0/wrapper0, permission consumed and lock=released. The [C5 journal selected evidence](../journal/2026/10/2026-10-05-phase-69.7-job-migration-cross-process.md#actual-revision2-selected-validation) links JM69-10C5-representative03-result.json@1 and JM69-10C5-accumulator01-result.json@1. Actual one-cause narrowing1->0 is selected validation evidence only. Original whole-Step/Phase acceptance remains open; whole-JM10 protected M2/local Step commit, actual CBD, all remaining original obligations, aggregate full validation, sole epoch1 Phase review and final local release remain required.
## Fresh Job experience process companion

status=selected_validation_passed_review_pending
validation_status=REP002-ACC001-passed
phase=69.7
step=JM69-10
slice=JM69-10C6
review_status=complete-original-Step-independent-assurance-pending
acceptance_status=not-accepted

This companion proves the original direct `JobExperienceService` consumer over
the unchanged native B4 composition. It introduces no Job policy, authorization,
application annotation, HTTP or component ingress semantics. Its executable
binding is [JobExperienceCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobExperienceCrossProcessAcceptanceSpec.scala).

### UX1 Genuine paid owned result

Each writer runs one original Persistent Async scalar Task from independent
ASCII alpha1..8 input. Six keyed boundaries and revisions1..6, Task/canonical1,
native Published response bytes and paid `(2,96,1,1)`/remaining0 are checked
locally. Live engine and management results are Available. A graceful closed
writer exits0 before its independently composed fresh reader starts.

### UX2 Original fresh native facade

Each reader constructs the unchanged B4 Fixture and `open(id)` independently.
Its trusted startup grant and User recipe do not supply its current caller.
Terminal Registered, owner Ready, raw result None and actual
`new JobExperienceService(fixture.native.engine)` are required. No Task, port,
writer receipt, saved facade or copied result is supplied to the reader.

### UX3 Owner Mine vocabulary

Matching principal and User kind yield the single exact Mine id, total1 and no
cursor. Exact get has Succeeded/Persistent/Durable, Completed, English
Completed/View result, terminal progress/taskCount1, UnavailableAfterRestart,
empty controls and refreshablefalse. Signed creation/update times equal the
view. Original snapshot management does not invoke B4 queryVisible hydration.

### UX4 Original capabilities and applications

Separate foreign job_admin and content_admin callers can list/get Operator and
diagnose, but their Mine remains empty/not-found. job_view and content_manager
admit base read and Application, yet refuse Operator and diagnostics.
Application(blog) is empty because the original recovered debug parameters are
empty; no positive application recovery is inferred.

### UX5 Original refusals and ownership

Foreign ordinary and same-principal/different-kind Mine return an empty page
and not-found exact get; broad capabilities do not override Mine ownership.
Actual anonymous/no-capability Mine list refuses, exact get is not-found and
diagnostics refuses. Expectations use original structured Consequence meanings.

### UX6 Native bounded redacted diagnostics

Both original administrator capabilities admit offset0/limit100. The exact
native task identity, lifecycle dates, transaction and original timeline
identities/counts are checked against native facts and independent literal
operation/operation/root/own/per-task/committed vocabulary. Component, service,
operation, compensation action/failure, task result message and timeline notes
are absent. Independent offset-1, limit0 and limit101 cases refuse through the
actual facade. No redaction helper constructs expected results.

### UX7 Durable terminal control refusal

Owner and administrators expose empty controls. All Cancel/Suspend/Resume/Retry
calls refuse through the original facade's Runtime-origin requirement. Complete
signed/native/provider revision/times/payment/quota/physical bytes, retained
terminal seeds, timer, resources, maps and queue remain unchanged.

### UX8 Separate effect and native observations

Initial assertNative, complete row/physical observations precede the effect
baseline. Immediately after service calls, compare all original write, metadata,
Blob get/open/close/consume/put/delete, resolver, Source/port/Task/getter/canonical,
clock/ID and runtime observations. Final native equality verification follows
that comparison; its metadata reads are observation I/O, not facade effects.
Original Ready paid seeds and one maintenance timer persist until owned close.

### UX9 True processes and bounded observations

Unique `target/job-experience-cross-process/work/case-*` native/control/log trees
use absolute Java, original runtimeClasspath and exact array argv. Parent checks
actual distinct PID/start/UUID and graceful Subsystem/node/SQL/owner/lease closure.
Each child has a60s wait and only its own5s+5s cleanup. Failed logs remain, with
2048-byte excerpts; only successful owned namespaces are removed. Closed receipts
are <=16KiB and contain selected counts/opaque identities/facts/booleans only,
without scalar bodies, credentials, native records or writer control state.
E8 uses ordinary shrinking, alpha1..8 and six closed caller modes,
seed69710081L/min24/workers1; passed/succeeded>=24/discarded0 are required.

| Example | Rules | Actual fresh semantic boundary |
| --- | --- | --- |
| E1_UX1 | UX1,UX2,UX3,UX8,UX9 | Genuine writer / owner Mine list/get / exact vocabulary and unavailable result |
| E2_UX2 | UX1,UX2,UX4,UX8,UX9 | Separate job_admin/content_admin pairs / Operator then Mine / exact positive versus hidden scope |
| E3_UX3 | UX2,UX4,UX5,UX8,UX9 | Ordinary foreign, broad administrator and same-principal different-kind pairs / Mine / empty and not-found |
| E4_UX4 | UX2,UX5,UX8,UX9 | Anonymous subject / Mine and diagnostics / original refusals |
| E5_UX5 | UX2,UX4,UX8,UX9 | Separate job_view/content_manager pairs / Operator, diagnostics, Application and Mine / refused versus empty |
| E6_UX6 | UX2,UX4,UX6,UX8,UX9 | Both administrators and separate valid/invalid bounds / diagnostics / exact native redacted or refusal |
| E7_UX7 | UX2,UX3,UX4,UX7,UX8,UX9 | Owner and both administrators / views and four controls / unavailable controls and complete native preservation |
| E8_UX8 | UX1,UX2,UX3,UX4,UX5,UX8,UX9 | Normal generated input/caller / at least24 real sequential pairs / independent scope and zero-effect oracle |

All eight examples and generated inner/outer scenarios retain adjacent GWT,
should/which/in and afterWord spec/example/rules/phase69.7/sliceJM69-10C6.
Selected8REP/121ACC were planned-not-run at original authoring. Actual
`PH697-JM10C6-REP-001` completed8 suites/79 tests,79 succeeded, with
failed0/canceled0/ignored0/pending0/aborted0, SBT0/wrapper0/typed reader0,
permission consumed and lock=released. Compilation emitted two E121 warnings
at `JobOperationalMetrics.scala:19,24`; selected_validation_pass=false and
acceptance remains pending. The frozen
[compiler repair](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-validation-fix01.json)
changes only both exhaustive-enum wildcard fallbacks to `case null => -1`
and the Oct5 header, preserving R1 ignored-null behavior. Existing metrics
OM2 directly covers both null facets; no executable example changes.
At the historical repair-authoring point, revised10REP and unchanged121ACC
were planned-not-run and repaired-source admission was pending. Actual parent
[mechanical/source/header/GWT admission revision2](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-mechanical-admission-revision2.json)
now precedes current subject2 execution. [REP002](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-representative02-result.json)
completed10 suites/89 tests,89 succeeded/pending0; [ACC001](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-accumulator01-result.json)
completed121 suites/988 tests,988 succeeded plus only original SqliteDataStoreSpec
pending1. Both have failed0/canceled0/ignored0/aborted0, compilerwarnings0,
no compile failure, typed reader0/SBT0/wrapper0, consumed permissions and
lock=released. Actual one-code-unit narrowing warning2->0 is selected validation
evidence only; acceptance_status=not-accepted and original whole-JM10 protected
M2 independent assurance remain pending. The [current C6 journal](../journal/2026/10/2026-10-05-phase-69.7-job-experience-cross-process.md#actual-revision2-selected-validation)
retains actual result/log/summary links and infrastructure history. Prior A/B/C1..C5
results and all original histories remain separate evidence. Actual notification,
JCL/governance/CompositeQuery, remaining security/matrix, positive started-work
decision, actual completed CBD provider/Entity/full Review and fresh runtime,
compatibility/promotion, whole-JM10 protected M2/local acceptance commit,
aggregate full validation, sole epoch1 Phase review and local release remain
mandatory. JM69-10 OPEN and Phase69.7 IN_PROGRESS remain unchanged.

## Fresh Job notification process companion

JM69-10C7 authors NT1..NT9 under
[Manifest01](../../.codex-workflow/phases/PHASE-69.7/JM69-10C7-cycle-plan01.json).
status=focused_validation_passed
validation_status=representative-and-accumulator-passed
acceptance_status=not-accepted
The [executable specification](../../src/test/scala/org/goldenport/cncf/job/JobNotificationCrossProcessAcceptanceSpec.scala)
and [native composition](../design/job-cross-process-acceptance.md#fresh-job-notification-process-composition)
consume the existing native Job and default notification contracts.

### NT1 Genuine paid successful lifecycle

Every writer executes one original PublicationFixtures scalar Task with an
independent alpha1..8 input as Persistent Async. The original B4 native owner
executes submission and the controlled engine drains exactly one task. Its own
Task counters are runs1/canonical1/getters>0 and remain unchanged on observation;
B4's internal Task vector is empty, so its task/getter/canonical effect counts
are truthfully zero. Original Source requests use all six exact boundaries and
expected revisions1..6. Independent assertNative checks semantic revision6,
Succeeded, original definition/User authorization, owned physical scalar bytes,
Published Blob quota and paid seed2/96/1/1, remaining0, one normal association,
no claims and no cancellation claim. Live result is exact Success and management
result Available before any observation receipt.

### NT2 Actual default forwarding eligibility

Before native start/open, initialize a provider Component Core with the original
engine, add it through actual Subsystem.add, retrieve the actual installed
component and install the actual descriptor with default notification rules.
The original User context receives only that component's scope; native core,
clock/IDs, datastore and security persist. The genuine subsystem EventStore has
one matching job.succeeded with subject-a/User, async/persistent,
recovery-required=false and exact submitted web parameters. No-app keeps only
service/operation and forwards zero times. Disabled configures enabled=false
and has no default forwarding rule, call or diagnostic. All fresh readers
independently configure an enabled accepting provider even for those modes.

### NT3 Literal scoped provider request

Every eligible writer makes exactly one provider invocation under the original
subject-a/User and actual Component scope. Independently literal-equal request:
recipient subject-a, type cncf.job, channel in-app, status Queued, priority None,
title `Job succeeded: blog.post.publish`, body
`Job <id> is succeeded. See the Job page for details.`, dedupe
`cncf.job:<id>:succeeded`, safe action `/web/blog/jobs/<id>` and exact metadata
jobId/trigger=succeeded/jobStatus=succeeded/sourceEventName=job.succeeded/app=blog/
service=post/operation=publish/recoveryRequired=false. Correlation comes from the
original submitted context. No production forwarder builds the expected request.
Full equality reserves the body-free notification contract without an accidental
scalar-substring predicate.

### NT4 Fixed diagnostic and isolated failures

Accepted/unsafe-app emits one user-notification.forwarding.sent with note
notification-c7. Refused, structured failure and NonFatal throw each emit one
user-notification.forwarding.failed, respectively with the literal public notes
below. Name equals kind, persistent=true, status Stored, lane NonTransactional,
positive sequence, attributes exactly cncf.userNotification.forwarding=true and
payload exactly source-event-name/job-id/note/recipient-user-id/dedupe-key.
The failure/throw fixture uses provider-private-credential-c7; it is absent from
the request and allowed diagnostic. The actual Job event may contain a result
summary; no all-event redaction claim is made. Native Job success and payment
survive each provider failure. Deterministic provider results explicitly set
acceptedAt to the original fixture instant, notificationId Some(notification-c7),
providerNotificationId None and empty attributes.

| Writer mode | Calls / diagnostics | Diagnostic kind / literal note |
| --- | --- | --- |
| accepted | 1 / 1 | forwarding.sent / notification-c7 |
| no-app | 0 / 0 | None |
| disabled | 0 / 0 | None |
| refused | 1 / 1 | forwarding.failed / The notification provider refused delivery. |
| failure | 1 / 1 | forwarding.failed / The notification provider did not accept delivery. |
| throw | 1 / 1 | forwarding.failed / The notification provider failed while accepting delivery. |
| unsafe-app | 1 / 1 | forwarding.sent / notification-c7 |

### NT5 Fresh native registration never replays

After actual writer shutdown and exit, a separate native Fixture on the same
physical namespace opens only the opaque id using original recipe/access,
without a supplied Task or rehydration port. Ready/Registered retains raw result
None, debug.parameters empty, subject-a/User, getResult None and authorized
management UnavailableAfterRestart. No recovered application annotation is
invented. Enabled fresh provider calls0 and actual EventStore Vector.empty hold
after startup and every read. Original getStatus/query/getResult/management and
JobExperienceService Mine(None) list/get expose exactly one id/Succeeded/
Completed/resultUnavailableAfterRestart/controlsEmpty, without event append or
dispatch. No restart delivery hook or successful delivery claim is added.

### NT6 Unsafe application link

Unsafe-app submits original web.app ../private. Its exact request title is
`Job succeeded: ../private.post.publish`; allowed metadata retains app ../private
and actionUrl is None. Remaining literal fields and native/no-replay oracles
are identical to accepted mode.

### NT7 Separate read effects and native audits

Initial native/complete row/physical/quota audits precede the effects baseline.
Immediately after read surfaces, compare all original B4 effects, native clock/ID
counts, provider invocations, events, executable maps, bindings, paid seed,
resources, work sequence/queue, manual time, scheduler/retention and timer.
Ready terminal retained1/pending0/executing0, one id binding, seed2/96/1/1 and
maintenance timer1 persist; scheduler nextDueAt uses original engine manual
time plus1000ms, independent of CountingClock. drainAll is zero. Final audits
compare complete rows, signed record/provider revision, payment, quota and
physical bytes with initial observations. Only those audits add three metadata
loads; all other effects remain equal without resetting counters.

### NT8 True sequential JVMs and resource bounds

Each case owns target/job-notification-cross-process/work/case-* with native,
control and logs. Absolute java.home/bin/java plus the original read-only
runtimeClasspath helper launches array argv, with no shell. Parent/writer/reader
PID/start/UUID identities are distinct. Each child has a60s wait and only its
owned timeout process gets destroy/force with5s+5s waits; failure namespaces
remain with <=2048-byte log excerpts. Successful namespaces alone are removed.
Closed unique-key receipts are <=16KiB and contain opaque identity and selected
observations, never credentials/grants/native bodies/response/private errors.
Provider, SQL, owner/scheduler, subsystem/node close, leases0 and timers0 precede
receipt and normal exit. No child survives.

### NT9 Ordinary generated pairs and visible GWT

All eight leaves use AnyWordSpec should/which/in, adjacent Given/When/Then and
afterWord spec/example/rules/phase69.7/sliceJM69-10C7 metadata. E8 uses ordinary
Prop.forAll with normal shrinking over Gen alpha1..8 and seven closed writer
modes, seed69710091L/min24/workers1. Every generated pair has its own adjacent
GWT; the outer property has its own GWT and requires checked.passed,
succeeded>=24 and discarded0. No ignore/pending/noShrink/filter/implication or
metadata/source mirror substitutes a real pair.

| Example | Rules | Semantic process boundary |
| --- | --- | --- |
| E1_NT1 | NT1,NT2,NT3,NT4,NT5,NT7,NT8 | accepted / exact request and sent diagnostic / fresh no replay |
| E2_NT2 | NT1,NT2,NT5,NT7,NT8 | no-app / no call or diagnostic / fresh enabled no replay |
| E3_NT3 | NT1,NT2,NT5,NT7,NT8 | disabled / no rule, call or diagnostic / fresh enabled no replay |
| E4_NT4 | NT1,NT3,NT4,NT5,NT7,NT8 | refused / exact public refusal / successful native Job preserved |
| E5_NT5 | NT1,NT3,NT4,NT5,NT7,NT8 | failure / exact safe note / no private detail or replay |
| E6_NT6 | NT1,NT3,NT4,NT5,NT7,NT8 | throw / isolated NonFatal / safe note and no replay |
| E7_NT7 | NT1,NT2,NT3,NT4,NT5,NT6,NT7,NT8 | unsafe-app / original metadata and no action link |
| E8_NT8 | NT1,NT2,NT3,NT4,NT5,NT6,NT7,NT8,NT9 | ordinary generated input/mode / at least24 actual pairs |

Initial authored_validation_pending/planned-not-run remains historical and is
superseded by actual subject revision2 selected validation: REP002 completed7
suites/51 tests,51 succeeded/pending0; ACC001 completed123 suites/1006 tests,
1006 succeeded plus only original SqliteDataStoreSpec pending1. Both have
failed0/aborted0/canceled0/ignored0/compilerwarnings0, typed reader/SBT/wrapper0,
permission consumed and lock=released. The [C7 journal actual selected evidence](../journal/2026/10/2026-10-05-phase-69.7-job-notification-cross-process.md#actual-revision2-selected-validation)
links actual results/logs/summaries and [mechanical admission revision2](../../.codex-workflow/phases/PHASE-69.7/JM69-10C7-mechanical-admission-revision2.json),
retaining initial REP00143 successes/8 failures and the registered two-file M1
original-scope correction. C7-SELECTED-RESULT-PROJECTION-01 records this factual
projection; consumed pre-review unit1 narrows8->0 without changing reviewed-Step/
Phase counters. Prior evidence and all C6 repaired-warning,
capacity/permission/recovery history remain. Actual CBD provider/Entity/completed
full Review/fresh runtime, remaining matrix, compatibility/promoted guidance,
whole-JM10 protected M2/local commit, aggregate full validation, sole original
epoch1 Phase review and local release remain mandatory. No C7-only acceptance.

## CompositeQuery native process companion

status=focused_validation_passed
manifest=PHASE-69.7-JM69-10C8-implementation01@2
validation_status=subject8-REP010-ACC002-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted
spec=job-cross-process-acceptance
phase=69.7
step=JM69-10
slice=JM69-10C8
recorded_at=2026-10-05

This companion consumes CQ2-R1..CQ2-R12, the original configured native owner,
keyed lifecycle, closed V2 projection, lifetime quota and current-caller
terminal read contracts. It adds no Query response persistence. The
[five-leaf executable specification](../../src/test/scala/org/goldenport/cncf/job/CompositeQueryNativeCrossProcessAcceptanceSpec.scala)
and [native composition](../design/job-cross-process-acceptance.md#compositequery-native-process-composition)
are authored; selected representative8 and accumulator128 remain planned and
not run. Prior C7 actual receipts and all original acceptance obligations
remain in force.

| Rule | Required native/process behavior |
| --- | --- |
| CQNT1 | Each case owns one unique absolute target/composite-query-native-cross-process/work/case-* native/control/log tree. Actual absolute java.home/bin/java, original runtimeClasspath, ProcessBuilder array argv, confirmed writer exit before reader, distinct parent/writer/reader PID/start/UUID, child60s and owned5s+5s termination establish real processes. Unique closed receipts are <=16KiB; failed logs remain with <=2048-byte excerpts and only successful owned trees are removed. |
| CQNT2 | Writer uses original JobDurableRuntimeFixtures.Fixture(root,manual=false), actual native subsystem/jobEngine, configured SQLite/LocalBlob/provider/node/keyed owner and unchanged REALTIME worker pool. Reader independently constructs original Fixture(root,manual=true). Neither process installs a custom scheduler, submit delegate, Task wrapper, replacement rehydration port or shadow source authority. |
| CQNT3 | An independent existing CompositeQueryV2Fixture(marker=cq2-diagnostics,ownResourceScope=true), without suppliedJobEngine, supplies the immutable target-c8 Query subsystem. Every branch uses its payload request, independent fixture.branch ID and password marker. Only CompositeQueryEngine(native.subsystem).executeV2Blocking invokes real QueryAction protocol/authorization/context/UoW. One owned caller joins within10000ms, policy timeout5000/maxParallelism=count1..2, exact ordered usable scalars, original principal/session/token/trace/correlation, installed component runtime/scope and fresh sibling cells are local oracles. Target engines remain unused. |
| CQNT4 | Explicit PersistentDiagnostics selects only canonical wrapper Operation/operation/composite-query-v2/None/composite-query-v2/root/own/per-task metadata. Genuine returned diagnostic IDs each await live Success(Void)5000, then the native monitor read barrier observes original terminal settlement without lifecycle refusal. Six actual source boundaries and revisions1..6 use independently supplied User/cq2-diagnostics/absent-input/Pending descriptor evidence. Each terminal signed record is Succeeded6/Absent with Committed wrapper descriptor, paid2,96,1,1 remaining0, one original task and no claims/cancel claim. No private Query scalar/password/token/target selector/branch ID or content-bearing calltree is signed, and Blob get/put/publication remain0. |
| CQNT5 | Default Ephemeral traverses the same actual facade/query/canonical path and returns genuine diagnostic IDs with successful Void tasks. Original ephemeral metadata is retained. Source requests/pairs, signed native Job rows, global payment/storage rows and Blob publications remain0. Fresh readers receive no prior IDs and discover no candidates. |
| CQNT6 | Fresh MANUAL native startup uses only opaque persistent Job IDs, independently fixed access/replay/recipe and original caller resolver. Ready/Registered terminals expose raw None and authorized management UnavailableAfterRestart/queryVisible result None while signed Succeeded(Absent) remains. Foreign current User without fabricated privilege receives the exact original ownership refusal from queryVisible while management conceals existence as Success(None). No Query/Task/source map/body/native record/payment ledger is transferred as authority; no aggregate is reconstructed. |
| CQNT7 | Complete local signed Job/lifetime/payment/storage rows are observed before and after startup/reads. Actual Entity/NativeSql/source/port/blob/resolver and Query counters, local runtime maps/native bindings/resource seeds/IDs/clocks/manual time/queue/work sequence and one original maintenance registration remain unchanged through read APIs and one bounded drainAll returning0. Reader admits no work or effects. Observation loads are retained truthfully; counters are never reset. Writer awaits canonical settlement and permits ordinary empty realtime retention polls without asserting fixed counts. |
| CQNT8 | Finally cancels only the owned invocation, interrupts/joins only its still-live caller, shuts the target fixture engine and owned target subsystem/node, then original native subsystem/node. Every owned engine timer closes, original worker pool and optional scheduler termination each await5000ms, pending/executing0, maintenance closed, owner lease unavailable, logical bindings/nodes closed and native physical SQL closed precede a receipt. Remaining cleanup always runs; cleanup failures are suppressed on the primary failure. |
| CQNT9 | E5 uses normal Prop.forAll shrinking over Gen alpha1..8/count1..2/Ephemeral-or-PersistentDiagnostics, seed69710081L/minSuccessfulTests24/workers1. Every generated case executes real distinct sequential JVMs and the same local oracles. Adjacent inner and outer GWT, checked.passed/succeeded>=24/discarded0 and semantic rule/example/Phase/Slice metadata remain mandatory. |

| Example | Semantic Given/When/Then leaf |
| --- | --- |
| E1_CQNT1 | Original ready native owner and one real private scalar Query -> public realtime native facade/await/checkpoint/closed writer/fresh manual reader -> exact local aggregate and native paid6/Absent/body-free no-replay terminal. |
| E2_CQNT2 | Two required independent private Query scalars -> ordered public native diagnostics/closed writer/fresh reader -> separate genuine IDs, exact ordered aggregate, each paid6/Absent wrapper and no signed private content. |
| E3_CQNT3 | Default Ephemeral and one or two real Queries -> same native public facade then fresh empty-candidate reader -> actual local success and zero native Job/source/payment/publication/prior-ID evidence. |
| E4_CQNT4 | Genuine subject-a terminal and fresh foreign current User -> original registration then queryVisible/management -> exact queryVisible owner refusal and management Success(None) concealing existence, unchanged native rows/payment and zero replay/effects. |
| E5_CQNT5 | Ordinary independent bounded generator -> each actual sequential writer/reader pair, plus outer seeded ScalaCheck run -> minimum24 passed/zero-discard cases with full local semantic/native/confidentiality/cleanup oracles. |

The [Oct5 C8 journal](../journal/2026/10/2026-10-05-phase-69.7-composite-query-native-diagnostics.md)
preserves the initial no-edit binding mismatch and corrected revision2 native
public-facade binding. Initial C8 authoring consumed no repair allowance and invented
no execution result; five subsequent bounded pre-review corrections are retained
in the current journal chronology. Original whole-JM69-10 protected M2/local Step commit,
remaining JCL/governance/security/process matrix/positive started-work decision,
actual CBD provider -> Entity -> completed full Review -> independently fresh
runtime, compatibility/promoted guidance, aggregate full validation, sole
original epoch1 Phase review and final local release remain mandatory.


## Current C8 selected validation — 2026-10-06

Initial authored_validation_pending/planned-not-run is historical. Parent-admitted
current subject8 selected Slice validation is complete:
[JM69-10C8-representative10-result.json@1](../../.codex-workflow/phases/PHASE-69.7/JM69-10C8-representative10-result.json)
records PH697-JM10C8-REP-010,8 completed suites/66 tests,66 succeeded, pending0;
[JM69-10C8-accumulator02-result.json@1](../../.codex-workflow/phases/PHASE-69.7/JM69-10C8-accumulator02-result.json)
records PH697-JM10C8-ACC-002,128 completed suites/1050 tests,1050 succeeded,
with only original SqliteDataStoreSpec pending1. Both invocation summaries use
cncf.sbt.invocation-summary.v1 and report failed0/aborted0/canceled0/ignored0,
compile_failed=false, compilerwarnings0, typed reader exit0/SBT exit0/wrapper
exit0, consumed permission and serial lock=released. Result records retain the
actual log/summary paths and completion markers.

The [C8 journal chronology](../journal/2026/10/2026-10-05-phase-69.7-composite-query-native-diagnostics.md#current-c8-validation-chronology--2026-10-06)
retains the original no-edit binding mismatch, five bounded pre-review corrections,
historical REP008 subject6 success, failed ACC001/CP003 and REP009 subject7,
external human dependency recovery and actual generated-only CP004 PASS. Latest
selected Cozy0.3.3-SNAPSHOT remains; compatible external dependency recovery
resolved this run without establishing publisher identity or importing/accepting
future Phase100 APIs. CQNT1..CQNT9, genuine process/property/native/canonical/
privacy/payment/cleanup oracles and all historical receipts remain unchanged.
This documentation projection consumes no repair unit or counter reset.

JM69-09 remains DONE; JM69-10 remains OPEN; Phase69.7 remains IN_PROGRESS.
Original whole-JM10 protected M2 independent Step review and local acceptance
commit remain pending. Actual admitted CBD provider -> Entity -> completed full
Review, independent fresh real recovery, remaining original matrix/positive
started-work choice, compatibility/accepted guidance promotion, predecessor
deferred-full validation, aggregate full CNCF/downstream validation, sole original
epoch1 comprehensive Phase review and final local Phase release remain owed.
Original Goal/authority/base/binding/epochs/history/ledgers/budgets/counters and
other authors' work remain. Parent owns diff/link/status verification, validation,
review and transitions; this editor runs no validation or self-review.

## JCL native process companion

status=selected_validation_complete
manifest=PHASE-69.7-JM69-10C9-implementation01@3
validation_status=REP006-and-ACC001-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted
spec=job-cross-process-acceptance
phase=69.7
step=JM69-10
slice=JM69-10C9
recorded_at=2026-10-06

This companion consumes the existing [executable JCL contract](executable-jcl-runtime-contract.md),
[lightweight definition lifecycle](job-definition-lifecycle-contract.md), original
configured native owner, strict keyed lifecycle/task projection, paid lifetime
and current-caller terminal facade. The [seven-scenario specification](../../src/test/scala/org/goldenport/cncf/job/JobJclCrossProcessAcceptanceSpec.scala),
[native composition](../design/job-cross-process-acceptance.md#jcl-native-process-composition)
and [Oct6 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-cross-process.md)
record initial authoring, when selected6REP/129ACC were planned and no C9
execution result existed; the current selected results below supersede that status.
Previous CP/SC/CQNT contracts and actual C8 evidence retain their own meanings.

### JX1 Actual independent sequential JVMs

Each case owns a unique absolute `target/job-jcl-cross-process/work/case-*`
with native/control/log directories, original physical SQLite and LocalBlob.
Absolute `java.home/bin/java`, the existing actual runtimeClasspath and
ProcessBuilder array argv launch real children. Actual writer exit is confirmed
before reader launch. Parent/writer/reader PID/start-instant pairs, PIDs and
independently generated JVM UUIDs must be distinct. Only opaque Job IDs and
independently supplied bounded alpha/count/scenario selectors reach the reader;
no source, definition, Task, record, provider, paid ledger or writer receipt
becomes reader authority. Receipts are closed unique-key observations <=16KiB
after assertions and closure. Child waits are60s with owned5s+5s timeout
termination; failures retain logs with <=2048-byte excerpts. Only successful
owned namespaces are removed; no user-owned process is touched.

### JX2 Genuine public Action and configured native composition

The fixture composes original JobDurableRuntimeFixtures.Fixture, its actual
Subsystem/jobEngine/SystemNode/configured provider, managed SQLite,
ObservedEntityStore and LocalBlob. Real JobControl Factory/createPrimary and
normal ComponentFactory bootstrap install public protocol operations, alongside
one independently initialized CommandAction target in the same Subsystem.
Public submit_job_definition resolves an actual Action and executes it under
the original ready owner lease. The writer uses original REALTIME scheduling;
the reader independently constructs Controlled/Manual scheduling without work.
Actual caller User/subject-a/tenant-a, installed action scope, native datastore/
Entity context and canonical UnitOfWork commits are observed, not synthesized.
No direct engine submission, Task.run, substitute Task, fake acknowledgement,
replacement engine, reflection, seeded record or shadow state supplies proof.

### JX3 Exact root and finite-flow descriptors

For N initial tasks1..3, independent declarations select root/first/last
operations, component identity, service None, Action/action, Own/PerTask and
root parameters with step overrides. The first Root has parent/reference None.
Every later Child has its immediately preceding actual opaque task ID as both
parentTaskId and relation.reference. All final task IDs are distinct and remain
in declared order across actual action context, live model, signed descriptors
and fresh facade. The JCL-private prepared-task decorator selects default
root/child/continuation at preparation sites, preserves nonempty delegate
relation/transaction metadata and delegates all other metadata/run/canonical/
admission callbacks and prepared context. SameJob retains its source-task
parent, JclEventAwareTask stays outer, and the engine's explicit compensation
override remains authoritative. No global engine/projector/codec changes occur.

The independent initial Pending recipe declares one closed Root/Child ID chain.
Each genuine addressed boundary first calls unchanged
DurableJobKeyedEvidenceFixtures.pair, then copies only descriptor kind to
Action. TaskStart/Outcome/Terminal use actual accumulated model IDs and size;
pre-start boundaries use independent pending IDs/count. Expected parent/relation
and command target derive from position and preceding opaque ID, never copied
from observed metadata. Running/Pending then Succeeded/Committed progression
is checked at the real boundary prefix; no padding, detachment or final-count
requirement before all tasks start is permitted.

### JX4 Canonical scalar, signed terminal and exact native payment

Public await plus the original native state-monitor barrier observes canonical
Succeeded without lifecycle write refusal. Actual target inputs/order, final
scalar and successful UnitOfWork commit callbacks must match independent
declarations. Persistent signed semantic revision is `4+2*N`; boundaries are
Admission, StartIntent, RunningIntent, N TaskStart/Outcome pairs, and Terminal.
Original Supplied result remains Succeeded(Absent). Charged tasks are `2*N`,
history credits `32+64*N+noteCount` (noteCount1 for root-only,2 for finite flow),
admissions1/cancelCredits1, remaining0, N normal IDs, no compensation claims
and no cancel claim. N1 gives revision6/paid2,97,1,1; N3 gives revision10/
paid6,226,1,1. No payment counter is reset or fabricated, and no private scalar,
parameters, JCL body, content-bearing calltree, input or source Blob is signed.

### JX5 Dual native immutable-source authority

The writer publicly creates/gets active stored A with its legitimately issued
opaque ID/key, submits the stored reference and canonically completes A. Live
snapshot and the engine-created typed JobEntity retain A/id/key/format/profile,
without entitySync-failed markers. Public update stores different B under the
same ID, then public retire makes current B/Retired; only A's target runs.

Fresh reader performs exact-key Store-scope typed EntityQuery search, requires
one current JobDefinition, then loadDetached of its real saved ID and positive
revision carrier. It independently expects B/Retired. Under the same native
lease/context it loadDetacheds JobEntity.entityId(actualJobId), requiring a
positive carrier, exact Job identity/status/Persistent, saved definition ID/key,
nested submitted snapshot source A/format and debugParameters source A with
the independently declared profile. Missing entities/snapshots cannot pass.
The original signed record is separately authenticated, its complete fixed
definition metadata compared with the original recipe, and ID/key/format
cross-matched with both native entities. Signed metadata contains no source A
and does not authenticate its body; fixed legacy envelope version/revision/hash
and declaredSource None are not domain lifecycle controls. No source digest,
hash certificate, new Blob/schema/signature, key-derived identity, manually
saved JobEntity or writer-content comparison replaces these real store reads.
Registration must preserve JobEntity A and current definition B unchanged.

### JX6 Pre-execution refusals and explicit persistence

After legitimate setup of actual Draft and Retired definitions, counters/rows
form the refusal baseline. Four public attempts (Draft, Retired, malformed and
unsupported source) must return the expected typed pre-execution refusals,
without a new Job, admission, source pair, action invocation, Entity/lifetime/
storage/Blob write or altered existing definition. Explicit Ephemeral source
follows the same genuine public Action/scalar/canonical path while producing
no durable Job/source/payment/publication rows. Its fresh reader receives no
prior Job IDs and discovers a genuinely empty native durable namespace.
Persistent policy and refusal/security authority stay unchanged.

### JX7 Fresh current caller, body privacy and zero replay

Fresh Persistent startup is Ready with Registered terminal facts, no callable
Task maps, runtime Job maps, action/target/source/port execution or queue work.
Raw/getResult/queryVisible results remain None and authorized management remains
UnavailableAfterRestart, distinct from live Available scalar success. Submitted
native source A is visible only inside the trusted Entity fixture context;
fresh signed/raw/query/management bodies expose no private JCL/scalar. A fresh
foreign subject/tenant gets the original strict ownership refusal from
queryVisible and management Success(None) concealment; comparison may normalize
only independently generated Observation timestamps. Complete signed/native/
quota/JobEntity/current-definition rows and paid state are compared locally
before/after registration and reads. Entity create/update/delete, lifetime
payment and Blob publication/write effects remain0; legitimate typed read
counts stay observed. No replay or reconstructed result is invented.

### JX8 Native resource and process closure

All outcomes attempt actual Subsystem/node cleanup, native owner/maintenance
closure, unavailable owner lease, managed SQL physical closure, engine timer
shutdown, worker/optional scheduler bounded termination, pending/executing0,
queue empty and node activeLeaseCount0 before receipt/normal exit. Reader Manual
timers are empty after shutdown. Cleanup errors are retained on the primary
failure and cannot skip other owned cleanup. Receipt flags follow these local
assertions. Useful human-readable GWT output remains; native snapshot/receipt
printing is unnecessary and is omitted.

### JX9 Seven adjacent GWT scenarios and ordinary property

Every behavioral in-block places Given next to setup, When next to public or
process actions, and Then next to should oracles. E7 has both per-invocation and
outer-property GWT. Dependent Gen chooses ASCII alpha length1..8 and count1..3,
deriving closed canonical JCL. Ordinary Prop.forAll shrinking runs seed69710101L,
minSuccessfulTests24/workers1 and asserts checked.passed, succeeded>=24 and
discarded0. No filter, implication, noShrink, ignored/pending scenario or count
reduction replaces genuine sequential JVM pairs or any full native oracle.

| Scenario | Given / When / Then boundary | Rules |
| --- | --- | --- |
| E1_JX1 | Inline Persistent root / public canonical execution, closed exited writer then fresh reader / exact scalar, sole Root/no parent, paid/native/privacy/no replay | JX1..JX4,JX7,JX8 |
| E2_JX2 | Root plus two independently parameterized steps / one public three-task Job then fresh reader / exact ordered overrides, accumulated prefixes, Root/Child chain, revision10/paid6,226,1,1 | JX1..JX4,JX7,JX8 |
| E3_JX3 | Public active A and opaque saved ID / submit A, update B, retire, writer exit then fresh typed loads / native submitted A/current B plus separately authenticated signed metadata, no replay/source leak | JX1..JX5,JX7,JX8 |
| E4_JX4 | Draft/Retired definitions and post-setup baseline / four actual public invalid submissions / typed refusal before admission, unchanged definitions/native baseline | JX1,JX2,JX6,JX8 |
| E5_JX5 | Genuine completed Persistent writer and fresh foreign caller / original owner registration then foreign queryVisible/management / strict refusal/concealment, unchanged bytes/payment, zero effects | JX1..JX4,JX7,JX8 |
| E6_JX6 | Explicit Ephemeral canonical source / actual public scalar execution then independent empty reader / live success/canonical commit, no durable/source/paid/Blob roots | JX1,JX2,JX6..JX8 |
| E7_JX7 | Dependent bounded independent selectors / actual sequential JVMs per ordinary seeded generated invocation / full topology/domain/native/paid/privacy/no-replay/cleanup and min24/zero-discard property | JX1..JX4,JX7..JX9 |

C9-SIGNED-DEFINITION-SOURCE-01 was an actual revision1 pre-edit mismatch:
the existing signed definition envelope lacked source A. Parent revision2 froze
the canonical dual native-Entity proof and metadata distinction. Revision2
then stopped after four partial Scala programs/seven GWT leaves on actual
C9-FINITE-FLOW-ROOT-PARENT-02: all-root expectations contradicted the engine's
previous-task parents, and final-count expectations contradicted boundary
prefixes. Manifest3 freezes site-selected relations and genuine prefix oracles
without changing strict native authority. Neither mismatch was an executed
test/review failure. No C9 validation had run at initial authoring; actual
validation chronology is retained below and no repair counter is reset.

C8 actual8/66 and128/1050 plus original pending1, all history and stable HYG
records remain. C10 actual event/continuation processes, remaining security and
positive started-work decision, admitted actual CBD provider -> Entity ->
completed full Review -> independent fresh real recovery, compatibility/promoted
guidance, original whole-JM10 protected M2/local acceptance commit, predecessor
deferred full validation, aggregate full CNCF/downstream validation, sole original
epoch1 Phase review and final local release remain OPEN. Governance here means
the current lightweight definition lifecycle only. JM69-09 DONE; JM69-10 OPEN;
Phase69.7 IN_PROGRESS; C9 authoring supplies no Step/Phase acceptance.

## Current C9 selected validation — 2026-10-06

Initial `authored_validation_pending` / `planned-not-run` remains authoring
history and is superseded by `status=selected_validation_complete` and
`validation_status=REP006-and-ACC001-passed`. Current
`review_status=original-wholeStep-protected-M2-pending` and
`acceptance_status=not-accepted` remain unchanged.

Actual [REP006 representative result](../../.codex-workflow/phases/PHASE-69.7/JM69-10C9-representative06-result.json) completed6 suites/60 tests,
60 succeeded, aborted0/failed0/canceled0/ignored0/pending0/compilerwarnings0.
Actual [ACC001 accumulator result](../../.codex-workflow/phases/PHASE-69.7/JM69-10C9-accumulator01-result.json) completed129 suites/1057 tests,
1057 succeeded, aborted0/failed0/canceled0/ignored0/compilerwarnings0, plus
only the original `SqliteDataStoreSpec` pending1. Both typed readers, SBT and
wrappers exited0; both execution permissions were consumed and actual
`lock=released` was verified by the parent. These are selected-validation
results, with no full-repository, whole-Step or full-Phase acceptance inferred.

JX6 retains four typed pre-execution refusals and complete unchanged native
baselines/effects. The original malformed-YAML refusal retains `ParserException`,
the `expected the node content` diagnostic and exact `Taxonomy(System,Corrupted)`
(System-Corrupted). Draft/Retired exact messages and the unsupported exact-key
message retain `argumentInvalid`. This records existing branch-specific public
consequences; it introduces no parser/taxonomy policy and weakens no negative
assertion. Source A/current B, separate signed metadata, JX1..JX9, privacy,
no-reader-replay, cleanup and the ordinary seeded min24 property remain unchanged.

Stage remains IN_PROGRESS; JM69-09 DONE, JM69-10 OPEN and Phase69.7 IN_PROGRESS.
All original wider acceptance obligations above remain open, including actual
C10 processes, security/positive started-work decision, actual CBD full Review
and independent fresh recovery, compatibility/guidance promotion, protected
whole-JM10 M2/local acceptance commit, predecessor reciprocal deferred-full,
aggregate full CNCF/downstream validation, sole epoch1 full Phase review and
final local release. Original history, allowances, counters and HYG records
remain; selected validation neither resets them nor records review acceptance.

## JCL event and continuation native process companion

status=authored_validation_pending
manifest=PHASE-69.7-JM69-10C10-implementation01@1
validation_status=planned-not-run
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted
phase=69.7
step=JM69-10
slice=JM69-10C10
recorded_at=2026-10-06

The [nine-scenario executable specification](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessAcceptanceSpec.scala),
[native fixture](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessFixtures.scala)
and [writer/fresh-reader probe](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessProbe.scala)
consume the existing [Slice C runtime contract](executable-jcl-runtime-contract.md#slice-c-event-and-continuation-bridge).
The [composition](../design/job-cross-process-acceptance.md#jcl-event-and-continuation-native-process-composition)
and [C10 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-event-cross-process.md)
record selected authoring only. No C10 execution result is recorded.

### EC1..EC9 selected scenarios

| ID | Independent selection and public action | Required local oracle |
| --- | --- | --- |
| EC1 | PersistentRoot, N=2, persistent event after root | Root/no parent, two Action Continuations/reference-root, declaration order, root scalar |
| EC2 | PersistentFlow, N=3, persistent event after source | Root then Child/reference-root; every Continuation/reference-source, inherited root values and individual overrides, source scalar |
| EC3 | NonPersistentEvent, N=2, Persistent flow Job | One actual observer and successful handlers; selected writer EventStore count0; signed terminal/payment survive |
| EC4 | EphemeralJob, N=2, persistent event | Four real command commits and source scalar; writer Stored count1; durable native Job/payment/storage/Blob rows absent |
| EC5 | SourceFailure, N=3, Ephemeral | Fixed original argumentInvalid; root commits, source UoW aborts; no event, observer or continuation |
| EC6 | DispatchFailure, N=3, Ephemeral | Store append before observer refusal; original typed event.dispatch with previous cause; source wrapper fails, delegate commit remains, no handler |
| EC7 | HandlerFailure, N=3, Ephemeral | Successful root/source commits and observer; first continuation UoW aborts and source wrapper fails; later handlers do not run; event remains Stored |
| EC8 | ForeignReader, completed PersistentFlow, N=2 | Register under original signed authority, then independent foreign subject/tenant receives strict queryVisible refusal and management concealment |
| EC9 | GeneratedPersistentFlow, dependent alpha length1..8 and N1..3 | Ordinary shrinking, seed69710102L/min24/workers1; each invocation launches its own real writer/reader and runs all success oracles; passed, succeeded>=24, discarded0 |

All nine leaves and each generated invocation have adjacent Given/When/Then
and should matchers. The new target is a real CommandAction installed through
Component.Core's native JobEngine argument. Submission uses installed public
`submit_job_definition` and canonical inline `action/flow/events/onEvent` YAML
with string parameters. Fixture bootstrap adds one static observer before
submission; JCL adds no subscriptions. Neither target execution nor observer
publishes or directly submits/runs a Task.

### Independent topology and genuinely nested lifecycle

The initial pending recipe contains only base tasks: base1 for root emission,
base2 for flow emission. Actual native TaskStart/TaskOutcome IDs bind identities
as each task is admitted. Independent position/action selectors fix expected
Action targets, Root/no parent then finite Child/reference-root, and each
Continuation/reference-source. Own/PerTask is fixed independently. Actual
models are checked against these selectors before the genuine shared keyed
pair is supplied; the fixture-local pair changes only kind to Action. No
projector, bridge, codec or production source is changed.

Persistent success begins Admission, StartIntent, RunningIntent. Root starts;
for flow it completes before source starts. The source remains Running/Pending
while each handler starts/completes. Only after those nested handlers does the
source outcome commit, followed by TerminalOutcome. Actual vectors grow with
admitted tasks; earlier Running source entries are never fabricated as
Committed. For T=base+N, revisions are contiguous1..4+2*T. The final signed
vector equals the exact live order, contains T distinct IDs and all Committed
descriptors, and has Succeeded(Absent), with no fabricated failed terminal.

The native paid oracle is chargedtasks=2*T,
historycredits=32+64*T+32*N+notes, admissions=1+N and cancelcredits=1+N,
where notes3 for root emission and notes4 for flow. Each synchronous dynamic
continuation independently reserves one work ticket. Remaining0, the exact
actual T normal IDs, no compensation claims and cancelclaimed=false are
mandatory. The C9 single-ticket formula does not apply to this nested path.

### Actual command, event and fresh-reader effects

Every attempted action retains its real context/UoW, selected parameters,
job/task/currentTask, parent stack, User/subject-a/tenant-a and native datastore/
EntityStore identity. Post-commit callbacks prove successful delegate commits;
failed actions instead prove Aborted termination and no successful commit.
Wrapper failure after dispatch/handler failure cannot undo an already observed
delegate commit. Root/source return their own scalar, never the last handler's.

One original ReceptionDomainEvent is observed between source and handlers.
The probe checks exact id/after/name/kind, empty payload, occurrence bounded by
the real source/observer clock observations, actual job/task/correlation/
causation and observer context. Writer assertions read actual EventRecords:
count1 only when persistent=true and append occurred, count0 otherwise.
Record attributes/time/payload match the original event, Stored status and
positive sequence are checked, and native load returns that record.

The Subsystem EventStore remains its in-memory baseline. An independently fresh
reader starts with selected eventcount0 even after a persistent writer event;
this is no disk-backed EventStore, replay, deduplication or external-delivery
claim. Ephemeral and failure readers receive no Job ID and open the same empty
native Job namespace, with no invented persisted failed Job or event store.

Persistent readers receive only an opaque Job ID and independent scenario/
alpha/N. Ready/Registered startup restores authenticated terminal descriptors
and exact payments. Trusted native EntityStore reading loads the actual
positive-revision JobEntity and inline JCL debug metadata. Inline submission
has no saved definition/source snapshot; the signed metadata contains no JCL
body. Original public scalar management remains UnavailableAfterRestart.
Foreign queries retain strict refusal/concealment. Native row baselines/payment
remain unchanged after successful reads; actions, commits, source requests,
keyed pairs, recovery port, Blob operations/publications, SQL/Entity writes,
event observation and redispatch are all zero in the reader.

### Owned process bounds and pending parent validation

Unique absolute `target/job-jcl-event-cross-process/work/case-*` namespaces use
absolute java.home Java and existing runtimeClasspath. Sequential actual
writer exit precedes reader launch. Distinct parent/writer/reader PID/start
identities and JVM UUIDs, child60s waits, owned5s+5s termination, stderr excerpts
<=2048 bytes and closed scalar receipts<=16384 bytes are mandatory. Receipts
follow assertions and finally resource closure; they transfer no body, task,
recipe, record or ledger. Failed cases retain logs; only successful owned
cases are removed. Original native node/lease/SQL/owner/maintenance/worker/
timer/scheduler closure and empty execution queues are checked.

Parent-selected validation remains planned: eight representative suites and
the complete accumulated130 suites from the manifest, with Test/compile
coverage through testOnly. Actual discovered results, warnings, permissions
and shared lock release remain for the registered runner and parent to record.
C9 REP006/ACC001 evidence and all earlier failure histories remain unchanged.
Whole-JM69-10 protected M2, remaining security/positive started-work decision,
actual CBD full Review/fresh recovery, compatibility/promotion, predecessor
reciprocal deferred-full, aggregate CNCF/downstream/full-Phase validation and
sole epoch1 review/local release remain OPEN. JM69-09 DONE, JM69-10 OPEN and
Phase69.7 IN_PROGRESS remain unchanged.

Carried [HYG-PH697-SQL-SIZE-01](../journal/2026/10/2026-10-01-phase-69.7-hygiene-follow-up.md#hyg-ph697-sql-size-01--bounded-fix-and-inherited-debt),
[HYG-PH697-ROOTA-VALIDATION-PROJECTION-01 and HYG-PH697-SUBSYSTEM-SIZE-01](../journal/2026/10/2026-10-01-phase-69.7-hygiene-follow-up.md)
retain their original separately owned scope. C10 introduces no cleanup or new
Hygiene disposition and records no Step/Phase acceptance, commit or promotion.

## Native startup security cross-process acceptance

status=validated_review_pending
validation_status=REP002-passed-ACC001-passed
phase=69.7
step=JM69-10
slice=JM69-10C11
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted
recorded_at=2026-10-06

This companion consumes the existing [record authorization](durable-job-record-contract.md),
[runtime owner](durable-job-runtime-owner-contract.md), [terminal projection](durable-job-terminal-projection-contract.md),
[lifetime authority](durable-job-lifetime-quota-contract.md) and
[current result facade](durable-job-operation-response-query-contract.md).
It adds real process acceptance evidence without changing those contracts.
The seven leaves are authored in
[JobSecurityCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessAcceptanceSpec.scala);
[composition](../design/job-cross-process-acceptance.md#native-startup-security-cross-process-composition)
and the [C11 journal](../journal/2026/10/2026-10-06-phase-69.7-job-security-cross-process.md)
retain the authoring and validation boundary.

### SEC1 Genuine original execution precedes independent security startup

Each invocation owns `target/job-security-cross-process/work/case-*`. Its new
Case wraps the unchanged JobCrossProcessFixtures.Case and genuine writer with
count1..3. Persistent Async tasks execute once, committing the original six
keyed boundaries/revisions1..6 into managed native SQLite, LocalBlobStore and
published payment authority. Writer assertions, actual native closure and
normal exit precede the new reader. No synthetic terminal publication, copied
record or quota preseed substitutes for genuine execution.

### SEC2 Independent closed access selection

The reader constructs original static access and replay independently in its
own JVM. Only opaque typed-parsed native IDs, count, closed Mode and selected
index cross the argument boundary. Access never comes from a signed body or
writer receipt. Candidates retain exact ordered ordinals0..count-1 and unique
IDs; only the selected index receives Mode access, while others receive the
original exact access.

| Mode | Independently supplied change | Admission |
| --- | --- | --- |
| ExactAccess | Original tenant-a/subject-a/Set(job.read) | Positive |
| ExtraScopes | Set(job.read,unused.read) | Positive required-scope subset |
| ForeignTenant | tenantId=foreign-tenant | Refused |
| ForeignSubject | subjectId=foreign-subject | Refused |
| MissingScopes | Empty scopes | Refused |
| UnrelatedScopes | Set(job_admin,content_admin) | Refused |

Original tenant and submitting-subject equality and requiredScopes.subsetOf
remain authoritative. Extra grants do not require set equality. The actual
configured job_admin capability grants no record-authorization bypass.

### SEC3 Actual ordered terminal and runtime startup reports

Original configured startup consumes one source discovery and its cached
ordered candidates. Both terminal and runtime reports must preserve exact ID
and ordinal order. Every completed job has runtime Refused. Positive modes
have all terminal Registered, Ready health, execution available, runtime ready
and an installed owner. Any denied selection has exactly one terminal Refused,
fixed `durable runtime startup refused`, Degraded health, execution unavailable,
runtime not ready and no installed owner. Healthy prefixes and suffixes retain
their original body-free durable projections despite aggregate refusal; the
denied query, status and projection remain absent. No blanket rollback or
all-Refused result is substituted for the actual ordered report.

### SEC4 Separate trusted native and paid-authority observation

The reader's source.before captures actual native job rows and original
storage/lifetime/blob-quota singleton rows locally before startup. They remain
identical after startup, query, denial and payment observations, without
checkpoint, repair or refund. Under the real managed datastore lease, a
separate original-access observer loads every intact signed V2 revision6,
Succeeded(Absent), static definition and original Own/PerTask committed task.
It authenticates paid Seed `(2,96,1,1)`, remaining0, the exact original single
normal task ID, no claims and no cancellation claim. These selected scalar
facts equal writer observations in the parent. The trusted observer's access
is never supplied to a denied candidate; records and quota ledgers never cross
control receipts.

### SEC5 Zero reader execution and mutation

One source call accompanies an empty original Task map and keyed requests,
Port0, empty durable/runtime executable maps, work queue0 and drainAll0 under
the fixture lease. Entity create/write/delete/lifetime-write counters are zero,
as are body/getter observations and ID and Clock deltas. Job resource pending
and executing work are zero. Ready startup registers exactly one original
maintenance timer; the four denied modes register zero and install no scheduler.
The actual global pendingTimerCount and installed scheduler status remain
unchanged through query/native authentication and leased drainAll. Positive
status is started, not closed, not in flight, with nextDueAt present and no
lastResult. Actual shutdown cancels the registration: global timers0 and the
installed scheduler closed, not started, not in flight and without nextDueAt.
The receipt retains the actual preclosure timer count1 for Ready or0 for refusal;
no reset, subtraction, timer disable or time advance supplies these observations.
Reads publish no Blob/result and call no external provider. Healthy terminal
retained-memory quota is allowed; no invented zero-resource or rollback claim
replaces the actual retained projection semantics.

### SEC6 Body-free compatibility and redacted refusal

Positive queries remain Succeeded/Durable/Persistent with input/calltree/result
None, raw getResult None and original authorized management
UnavailableAfterRestart. Mixed-refusal healthy candidates assert only their
existing body-free query/status/projection; absent-owner management success is
not claimed. The selected denied access must produce the original typed native
loadStartupRecovery Refused, distinct from Missing or Corrupt. Separately
trusted native observation proves storage integrity. Receipts carry the fixed
startup message and bounded scalar observations, with no access grant, signed
body, payload, credential or private diagnostic conclusion. This is genuine
startup-security acceptance, with the existing Absent/facade limit; it supplies
no actual complete CBD Review or fresh downstream full-result acceptance.

### SEC7 Actual process ownership, bounds and closure

Parent, unchanged writer and new reader have different actual PID/start pairs,
PIDs and JVM UUIDs. The writer and reader share exactly the same native jobs.db
path while constructing fresh objects/ownership. Absolute java.home/bin/java
and the existing runtimeClasspath are used with argument-array ProcessBuilder.
Owned child wait is at most60s; timeout cleanup destroys/waits5s then, only if
needed, forces/waits5s for that owned child. Stderr excerpts are at most2048
bytes. Failed cases retain owned logs; successful cases remove only their own
tree. Original Subsystem/SystemNode/SQLite shutdown, nodeStopped,
activeLeaseCount0 and installed-owner closure precede a closed scalar receipt
of at most16384 bytes and normal exit0. No global process kill, home/cache
mutation, server or network operation is introduced.

### SEC8 Seven discoverable behavior leaves and generated invocation evidence

AnyWordSpec leaves S1..S7 use Matchers, visible Given/When/Then at actual
setup/action/assertion boundaries and trace `spec:job-security-cross-process`,
`phase:69.7`, `slice:JM69-10C11` and SEC rules. Every matrix and generated
invocation has its own adjacent clauses and full process oracle. S7 uses
ordinary integer shrinking with dependent six-mode/count1..3/index0..count-1 selection.
The named tuple shrink preserves mode, filters ordinary count shrink candidates
to1..3 with index<count, then ordinary index shrink candidates to0<=index<count;
standard recursive shrinking stays within the original valid domain. It retains
seed69710103L/min24/workers1. Each generated invocation launches a genuine
fresh sequential pair and asserts the full oracle; checked.passed,
checked.succeeded>=24 and checked.discarded0 are required. No skip, discard,
noShrink or fabricated pass is permitted.

| Leaf in JobSecurityCrossProcessAcceptanceSpec | Rules | Actual Given / When / Then behavior | Current evidence |
| --- | --- | --- | --- |
| S1 Exact and additional-scope native startup | SEC1..SEC7 | ExactAccess/ExtraScopes x count1/count3, index0 / genuine writer then independent reader / all Registered, Ready, original facade, maintenance1 unchanged, zero replay and closure0 | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |
| S2 Foreign tenant native startup | SEC1..SEC7 | ForeignTenant/count1/index0 / original configured startup / one Refused, fixed denial and concealed projection, intact native paid authority | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |
| S3 Foreign subject native startup | SEC1..SEC7 | ForeignSubject/count1/index0 in job_admin context / actual startup authorization / strict subject equality, fixed denial and zero effects | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |
| S4 Missing required scopes native startup | SEC1..SEC7 | MissingScopes/count1/index0 / original loader and startup / typed Refused with intact storage, no Missing/Corrupt substitution | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |
| S5 Unrelated administrative scope names native startup | SEC1..SEC7 | UnrelatedScopes/count1/index0 / required job.read subset check / administrative names/capability cannot bypass access | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |
| S6 Ordered mixed native authorization refusal | SEC1..SEC7 | Four denied modes x indices0/1/2, count3 / twelve real sequential JVM pairs / exact mixed reports, healthy prefix/suffix retained, Degraded no-owner, no mutation | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |
| S7 Generated independent native startup authorization | SEC1..SEC8 | Dependent mode/count/index and unique tree / min24 actual fresh pairs with valid ordinary integer shrinking / full oracle, maintenance1positive/0denied unchanged, zero replay, closure0, passed, succeeded>=24, discarded0 | validated_review_pending; REP0024/26 passed, ACC001131/1076 passed+originalpending1; whole-Step M2 pending |

Actual REP001 completed4 suites/26 tests:24 succeeded and2 failed (S1/S7),
compiler warnings/aborts/pending/ignored/canceled0; reader2/SBT1/wrapper1,
consumed permission and lock=released. C11-READY-MAINTENANCE-TIMER-ORACLE-01
corrects the preclosure timer0 premise to original maintenance1/refusal0 and
real closure0; C11-DEPENDENT-SHRINK-DOMAIN-01 corrects count0 tuple shrinking.
RePLAN controls125/126 froze the [seven-path Fix Manifest](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-validation-fix01.json)
and [CyclePlan revision2](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-cycle-plan02.json).
Initial ten-path authoring remains history. REP002same4 and ACC001same131 were
planned, not run at repair authoring.

Actual parent-admitted subject2 selected validation is now complete:
[REP002](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-representative02-result.json), invocation
PH697-JM10C11-REP-002, completed4 suites/26 tests,26 succeeded/pending0;
[ACC001](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-accumulator01-result.json), invocation
PH697-JM10C11-ACC-001, completed131 suites/1076 tests,1076 succeeded plus
only original SqliteDataStoreSpec: should store record fields as columns pending1.
Both have failed0/aborted0/canceled0/ignored0/compilerwarnings0, typed
reader0/SBT0/wrapper0, permission consumed and lock=released. Initial
repair_authored_test_pending/REP001-failed-REP002-ACC001-planned remains
historical; original failed REP001 and both repair causes are retained.
These are parent-selected focused/accumulated tests; repository/downstream
full validation and original whole-Step independent assurance/acceptance remain
pending. This factual projection changes no acceptance or repair counters.

Earlier CP/XP/C10
results and failures are retained. Original whole-JM69-10 protected M2, actual
CBD provider/Entity/completed full Review/fresh recovery, positive started-work
closure, compatibility/guidance, predecessor deferred-full, aggregate full
CNCF/downstream validation, sole original epoch1 Phase review and final local
release remain OPEN. Authoring supplies no Step/Phase acceptance or commit.


## JM69-10D2 consolidated restart acceptance matrix — 2026-10-06

status=authored_review_pending
stage_status=IN_PROGRESS
phase=69.7
step=JM69-10
slice=JM69-10D2
validation_status=retained-selected-ACC001-passed-static-checks-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

This appendix consolidates the thirteen frozen existing executable suite rows.
Each row has actual suite-head presence and a passed result in ACC001; its
status is selected-tests-passed/original-wholeStep-M2-pending. Existing scenario
histories, failed attempts and earlier authored/validated statuses remain
chronological evidence. This table changes no original acceptance checkbox.

The retained same unchanged program/configuration/inputs evidence is
[PH697-JM10C11-ACC-001](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-accumulator01-result.json):
131 completed suites, 1076 succeeded, pending1 (only the original
SqliteDataStoreSpec “should store record fields as columns”), failed0/aborted0/
canceled0/ignored0/compilerwarnings0, typed reader0/SBT0/wrapper0,
permission consumed and lock=released.
[PH697-JM10C11-REP-002](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-representative02-result.json)
passed four suites/26 succeeded, pending0 and the same zero failure/exit/warning
counts with consumed permission and released lock. REP002 covered its selected
four suites; it supplies no whole-matrix coverage claim. No D2 execution occurred.

| Existing executable specification | Exact existing behavior groups | Frozen area and limit | Actual ACC001 suite-head evidence / current status |
| --- | --- | --- | --- |
| [JobCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobCrossProcessAcceptanceSpec.scala) | CP1 Original native completion across actual JVMs<br>CP2 Empty native completion across actual JVMs<br>CP3 Foreign native candidate in an actual reader JVM<br>CP4 Bounded generated native completion across actual JVMs | Original genuine completion, empty namespace, foreign native startup and generated real pairs | log line 27107; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobScheduledCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala) | E1_SCH1 Native scheduled admission across three actual JVMs<br>E2_CR1 Actual abrupt unstarted writer exit<br>E3_CR2 Actual interruption after native Running TaskStart<br>E4_SCH4 Generated inclusive scheduling across real JVM trios | Scheduled Submitted admission, fresh inclusive due execution, unstarted halt71 recovery; actual Running4 crash refuses via SC5 | log line 29611; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobRetryCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala) | E1_RT1 Genuine immediate automatic retry and fresh terminal JVM<br>E2_RT2 Genuine default delayed retry and inclusive deadline<br>E3_RT3 Genuine paid pending retry after graceful writer exit<br>E4_RT4 Genuine pending retry interrupted by owned halt71<br>E5_RT5 Ordinary generated real completed retry process pairs | Actual immediate/default delayed retry completes in writer then fresh terminal read; genuine Running5 pending graceful/crash writers refuse via RC5, not positive fresh retry | log line 29708; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobCursorCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobCursorCrossProcessAcceptanceSpec.scala) | Durable management cursors<br>unchanged authorized native snapshots<br>bounded visibility and structural refusals<br>genuine authorized source change | Original durable management cursors across actual native JVMs, visibility, malformed cursor and real source change | log line 29811; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [DurableJobOperationResponseQuerySpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQuerySpec.scala) | current caller and detached result responsibility<br>malformed authority and observed native refusal<br>lifecycle and compatibility responsibility<br>true sequential JVM result responsibility | Exact job-owned OperationResponse external payload through current-caller queryVisible; genuine Q11/Q12 JVM cases, payload/redaction/compatibility refusals | log line 28836; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobNativeRefusalCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobNativeRefusalCrossProcessAcceptanceSpec.scala) | Malformed native canonical text<br>independently fresh original startup<br>Native integrity mismatch<br>the original signed-record consumer<br>Unsupported native format version<br>independent original version admission<br>Noncanonical native text<br>canonical storage admission in a fresh JVM<br>Missing native Job with retained payment<br>an independently declared original opaque candidate<br>Mixed healthy and malformed native Jobs<br>the full ordered original candidate vector<br>Bounded generated native refusal<br>ordinary dependent shrinking over all five faults | Malformed canonical/integrity/version/noncanonical/missing/mixed native startup and generated refusals | log line 29917; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobMigrationCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobMigrationCrossProcessAcceptanceSpec.scala) | E1_MG1 Genuine completed legacy Job<br>explicit migration followed by independently fresh V2 startup<br>E2_MG2 Original exclusive owned input<br>independent physical migration and fresh V2 preflight<br>E3_MG3 Complete mixed native vector<br>ordered original V1 V2 V1 candidates<br>E4_MG4 Unchanged default migration policy<br>absent and explicitly false original runtime inputs<br>E5_MG5 Original Operator admission<br>enabled migration with empty capabilities<br>E6_MG6 Whole-vector malformed tail<br>genuine legacy prefix with only the final canonicalJson changed<br>E7_MG7 Ordinary generated real migration<br>dependent all-V1 or mixed vectors and original Operator choices | Actual genuine V1 migration with fresh V2, owned input, mixed vector, policy/operator/tail refusal and generated modes | log line 30039; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobJclCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobJclCrossProcessAcceptanceSpec.scala) | JM69-10C9 real public JCL across sequential JVMs<br>E1_JX1 canonical inline Persistent root<br>E2_JX2 root and finite declaration-order flow<br>E3_JX3 lightweight stored definition immutable submitted source<br>E4_JX4 pre-execution refusals<br>E5_JX5 current foreign caller refusal and concealment<br>E6_JX6 explicitly Ephemeral canonical JCL<br>E7_JX7 generated canonical finite flow | Public canonical JCL root/flow, immutable stored lightweight definition governance, input/foreign refusal, Ephemeral and generated flow | log line 30824; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobJclEventCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessAcceptanceSpec.scala) | JM69-10C10 public JCL events across independently fresh processes<br>EC1 PersistentRoot<br>EC2 PersistentFlow<br>EC3 NonPersistentEvent<br>EC4 EphemeralJob<br>EC5 SourceFailure<br>EC6 DispatchFailure<br>EC7 HandlerFailure<br>EC8 ForeignReader<br>EC9 GeneratedPersistentFlow | Actual public event Root/Flow and native Continuation behavior, source/dispatch/handler/foreign refusal, nonpersistent/Ephemeral and generated flow | log line 30933; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [CompositeQueryNativeCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/CompositeQueryNativeCrossProcessAcceptanceSpec.scala) | E1_CQNT1 Actual single native diagnostic<br>one independently selected private scalar Query under original User subject-a<br>E2_CQNT2 Actual pair of native diagnostics<br>two real required payload queries on the independent immutable target alias<br>E3_CQNT3 Actual default ephemeral composition<br>one or two genuine target Queries with the unchanged default Ephemeral policy<br>E4_CQNT4 Actual foreign current caller<br>a completed original subject-a diagnostic and independently fresh foreign User<br>E5_CQNT5 Ordinary generated native composition<br>ordinary shrinking over independent alpha1..8, count1..2 and the two persistence policies | Actual original native scalar diagnostic queries, single/pair, default Ephemeral, foreign access and generated invocation | log line 30714; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobExperienceCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobExperienceCrossProcessAcceptanceSpec.scala) | E1_UX1 Original owner Mine<br>a genuine paid owned scalar terminal<br>E2_UX2 Original Operator capabilities<br>independent job_admin and content_admin native pairs<br>E3_UX3 Original Mine ownership<br>foreign ordinary, foreign administrator and different-kind current subjects<br>E4_UX4 Actual anonymous caller<br>an Anonymous subject with an empty capability set<br>E5_UX5 Original broad reader limits<br>separate job_view and content_manager current subjects<br>E6_UX6 Native redacted diagnostics<br>both administrators with independently selected valid and invalid bounds<br>E7_UX7 Durable terminal control<br>original authorized owner and both operator contexts<br>E8_UX8 Ordinary generated caller acceptance<br>normal shrinking over alpha1..8 input and six closed current callers | Original owner/operator/foreign/anonymous/broad-reader policies, redacted diagnostics, durable-control compatibility and generated subjects | log line 30161; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobNotificationCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobNotificationCrossProcessAcceptanceSpec.scala) | E1_NT1 Actual accepted lifecycle notification<br>a genuine paid Persistent Async scalar and enabled accepting provider<br>E2_NT2 Actual internal lifecycle<br>an enabled provider and real Async Job with service/operation and no web.app<br>E3_NT3 Actual disabled provider<br>an actual configured disabled component and application-visible Async Job<br>E4_NT4 Actual provider refusal<br>an actual provider returning accepted=false for the genuine successful Job<br>E5_NT5 Actual provider Consequence failure<br>a provider returning a structured failure with the fixed private fixture marker<br>E6_NT6 Actual NonFatal provider exception<br>an actual provider throwing IllegalStateException with the fixed private fixture marker<br>E7_NT7 Actual unsafe application reference<br>an enabled accepting provider and genuine Task with web.app ../private<br>E8_NT8 Ordinary generated lifecycle acceptance<br>normal shrinking over alpha1..8 inputs and seven closed writer modes | Actual writer lifecycle and provider acceptance/internal/disabled/refusal/failure/exception/unsafe application plus fresh no-replay reader and generated modes; no downstream persistent-inbox proof | log line 30359; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |
| [JobSecurityCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessAcceptanceSpec.scala) | S1 Exact and additional-scope native startup<br>S2 Foreign tenant native startup<br>S3 Foreign subject native startup<br>S4 Missing required scopes native startup<br>S5 Unrelated administrative scope names native startup<br>S6 Ordered mixed native authorization refusal<br>S7 Generated independent native startup authorization | Independent exact/additional-scope positive native startup and foreign/missing/unrelated/mixed refusals, generated pairs, raw maintenance registration/no-replay/real closure | log line 31052; suite-head present=true; passed=true; selected-tests-passed/original-wholeStep-M2-pending |

The suite-head locations refer to the retained original ACC001 log
`/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log`,
whose completed result above records the log and typed reader provenance.
The [parent frozen evidence](../../.codex-workflow/phases/PHASE-69.7/JM69-10D2-parent-plan-evidence01.json)
records all thirteen actual heads and passed rows. Generated leaves and
finite matrix invocations are bounded executable examples inside these suites;
they neither add aggregate assurance nor close original fixture categories,
whole-Step M2, downstream acceptance or Phase review. Original CP/native,
scheduling/retry/crash, cursor/payload, corruption/version/migration, JCL/
governance/event/CompositeQuery, user/operator/notification/security categories
retain their original scenario histories and closure obligations.

### Restart boundary and retained original obligation

The [recovery truth table](durable-job-recovery-contract.md)
continues to classify Running/Suspended or Submitted with startedAt present as
RecoveryRequired; existing ActiveLifecycle and ExecutionAlreadyStarted reasons
remain unchanged. The [rehydration R2/R3 contract](durable-job-runtime-rehydration-contract.md)
retains canonical/access admission, unchanged classification and eligible V2
Submitted-only port admission, with complete replay evidence, no startedAt and
no recoveryRequired. Its ordinary timer/queue owns due dispatch.

SC1..SC3 and CR1 demonstrate genuine unstarted/scheduled admission, independently
reconstructed live execution and a third terminal reader, including unstarted
halt71 recovery. Immediate/default delayed retry completes in its writer before
fresh terminal observation. SC5 actual Running4/Pending after TaskStart halt71
and RC5 actual Running5/Pending after Failed pending retry (graceful exit or
halt71) refuse before Port/Task/queue, retaining native rows/payment without
replay, refund or reset. Those refusal fixtures do not establish positive
automatic fresh retry or recovery of started work.

The original checklist calls for the real scheduled/retry/crash-interrupted
matrix; the retained original plan separately keeps positive-started-work
closure/design choice OPEN. This tension is retained parent planning evidence,
not an implementation decision or waiver. D2 introduces no recovery policy,
automatic retry, checkpoint/schema, proof-token rule or acceptance change.

Actual CBD provider/Entity/completed full Review and independently fresh recovery
remain OPEN. Its gateway is provider-pipeline-pending and read requires reportId/
reportDigest. Framework payload/Review fixtures cannot complete that downstream
obligation. The downstream checkout stays read-only and the unanswered source
authority request remains retained. Complete compatibility and accepted guidance
promotion, original whole-Step protected M2/local acceptance commit, predecessor
ancestry/reciprocal deferred-full, aggregate CNCF/downstream full validation,
sole original epoch1 Phase review, version/closure evidence and local release
also remain OPEN.


## JM69-10D3 existing focused compatibility evidence — 2026-10-06

status=authored_review_pending
stage_status=IN_PROGRESS
phase=69.7
step=JM69-10
slice=JM69-10D3
validation_status=retained-selected-ACC001-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

This documentary appendix maps nine frozen existing compatibility families
and twenty unique suites. Each selected existing case belongs to retained
ACC001 evidence: selected-tests-passed/original-wholeStep-M2-pending.
It preserves earlier scenario histories, passed/failed attempts and accepted
headers. It records evidence without accepted guidance promotion or an original
Step criterion closure. The [frozen parent source evidence](../../.codex-workflow/phases/PHASE-69.7/JM69-10D3-parent-plan-evidence01.json) and [D3 journal](../journal/2026/10/2026-10-06-phase-69.7-compatibility-evidence.md) retain provenance.

The retained unchanged program/configuration/execution-input evidence is
[PH697-JM10C11-ACC-001](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-accumulator01-result.json):
131 completed suites, 1076 succeeded, pending1 (sole original
SqliteDataStoreSpec “should store record fields as columns”), failed0/aborted0/
canceled0/ignored0/compilerwarnings0, typed reader0/SBT0/wrapper0,
permission consumed and lock=released. These are selected execution results;
original whole-Step protected M2 and aggregate full validation remain pending.
No D3 SBT or new execution occurred.

### COMPAT-01 Live/raw/result-facade compatibility

Original live response identity and getResponse/read model shape; Persistent/Runtime origins; default Supplied and negative legacy Absent/inline/shared/wrong-header/failed/cancelled retain body-free raw query/getResult=None and authorized UnavailableAfterRestart. Eligible owned queryVisible remains separate; no terminal-summary reconstruction or CBD proof.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [JobQueryReadModelSpec](../../src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala) | [return sync-equivalent result payload by JobId](../../src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala#L25)<br>[expose persistent vs ephemeral origin explicitly](../../src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala#L66) | [log line 9486](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L9486); `[info] JobQueryReadModelSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [DurableJobOperationResponseQuerySpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQuerySpec.scala) | [retain actual live response identity and authorize no missing Job](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQuerySpec.scala#L33)<br>[retain actual default Supplied, live failed cancelled nonterminal and negative legacy terminal behavior](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQuerySpec.scala#L438) | [log line 28836](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L28836); `[info] DurableJobOperationResponseQuerySpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [DurableJobTerminalProjectionSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobTerminalProjectionSpec.scala) | [E1 retain succeeded, failed, and cancelled closed facts in a distinct fresh engine](../../src/test/scala/org/goldenport/cncf/job/DurableJobTerminalProjectionSpec.scala#L43) | [log line 5454](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L5454); `[info] DurableJobTerminalProjectionSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-02 Canonical projection, strict Continuation and legacy migration

Retain exact V1/V2 projection/canonical authorized decoding, strict Continuation source correspondence and historical explicit compensation; genuine legacy migration with independently fresh V2 and absent/false default migration policy remain distinct positive/refusal cases. No schema or automatic migration policy changes.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [DurableJobProjectionSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobProjectionSpec.scala) | [preserve Running and Succeeded Action continuations through canonical V2 admission and terminal V1 compatibility](../../src/test/scala/org/goldenport/cncf/job/DurableJobProjectionSpec.scala#L29)<br>[refuse every targetless nonempty subset while preserving complete historical explicit correspondence](../../src/test/scala/org/goldenport/cncf/job/DurableJobProjectionSpec.scala#L79) | [log line 10190](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L10190); `[info] DurableJobProjectionSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [JobMigrationCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobMigrationCrossProcessAcceptanceSpec.scala) | [E1_MG1 Genuine completed legacy Job](../../src/test/scala/org/goldenport/cncf/job/JobMigrationCrossProcessAcceptanceSpec.scala#L24)<br>[E4_MG4 Unchanged default migration policy](../../src/test/scala/org/goldenport/cncf/job/JobMigrationCrossProcessAcceptanceSpec.scala#L78) | [log line 30039](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L30039); `[info] JobMigrationCrossProcessAcceptanceSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-03 Management authorization, exact detail and controls

Authorization before observation, denied/missing concealment, bounded existing live detail and exactly-once desired-state control replay. These controls do not authorize Retry/Resume for refused started native startup or bypass signed durable access.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [JobManagementDetailSpec](../../src/test/scala/org/goldenport/cncf/job/JobManagementDetailSpec.scala) | [return bounded live exact detail and the existing typed result](../../src/test/scala/org/goldenport/cncf/job/JobManagementDetailSpec.scala#L29)<br>[make denied and missing exact reads indistinguishable](../../src/test/scala/org/goldenport/cncf/job/JobManagementDetailSpec.scala#L84) | [log line 9467](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L9467); `[info] JobManagementDetailSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [JobManagementControlSpec](../../src/test/scala/org/goldenport/cncf/job/JobManagementControlSpec.scala) | [authorize before observing an existing or missing job](../../src/test/scala/org/goldenport/cncf/job/JobManagementControlSpec.scala#L25)<br>[apply each control command once and make the desired-state replay a no-op](../../src/test/scala/org/goldenport/cncf/job/JobManagementControlSpec.scala#L50) | [log line 5403](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L5403); `[info] JobManagementControlSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-04 Native owner composition, lease and shutdown defaults

Preserve original unconfigured/native identity, exact composition refusal, real managed owner lease/drain/cleanup and legacy status/health categories. No fallback runtime, late replacement owner or granted authority is introduced.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [JobDurableRuntimeOwnerSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala) | [retain its native identity without factory source or storage effects](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala#L32)<br>[reject mismatches and previous native work without starting replacement resources](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeOwnerSpec.scala#L104) | [log line 21711](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L21711); `[info] JobDurableRuntimeOwnerSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [JobDurableRuntimeLifecycleSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala) | [return fixed startup refusal and perform actual owned CLI cleanup without fallback execution](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala#L108)<br>[preserve legacy categories and refuse permission before health I/O across seeded shrinking native sequences](../../src/test/scala/org/goldenport/cncf/job/JobDurableRuntimeLifecycleSpec.scala#L217) | [log line 22293](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L22293); `[info] JobDurableRuntimeLifecycleSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-05 Descriptor/API, Web/HTTP, CSRF and polling

Preserve frozen descriptor-backed routes/parameters, canonical CSRF/PRG control replay, active-only bounded polling, accessible/localized terminal/unavailable first paint and actual route JSON. Existing server/provider fixtures prove these UI/API contracts; they do not establish actual CBD full Review or fresh persistent-inbox recovery.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [JobExperienceProtocolSpec](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobExperienceProtocolSpec.scala) | [P1 expose one descriptor-backed service with the frozen operations and HTTP methods](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobExperienceProtocolSpec.scala#L33)<br>[P3 return the actual canonical control changed=false replay record through the command resolver](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobExperienceProtocolSpec.scala#L150) | [log line 20751](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L20751); `[info] JobExperienceProtocolSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [JobExperienceWebSpec](../../src/test/scala/org/goldenport/cncf/http/JobExperienceWebSpec.scala) | [W1 render an active detail and terminal unavailable detail as complete initial HTML without relying on polling](../../src/test/scala/org/goldenport/cncf/http/JobExperienceWebSpec.scala#L73)<br>[W3 enforce original attempt budget across pause and resume while keeping Submitted and Running the only pollable states](../../src/test/scala/org/goldenport/cncf/http/JobExperienceWebSpec.scala#L127) | [log line 20781](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L20781); `[info] JobExperienceWebSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [JobExperienceHttpSpec](../../src/test/scala/org/goldenport/cncf/http/JobExperienceHttpSpec.scala) | [H3 obtain the real detail CSRF cookie and submit successful canonical control PRG including changed=false replay](../../src/test/scala/org/goldenport/cncf/http/JobExperienceHttpSpec.scala#L215)<br>[H7 render terminal and unavailable English/Japanese first paint without polling and return their real bounded REST JSON records](../../src/test/scala/org/goldenport/cncf/http/JobExperienceHttpSpec.scala#L533) | [log line 20811](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L20811); `[info] JobExperienceHttpSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-06 Public JCL and workflow contracts

Preserve existing JCL source normalization/admission, immutable submitted lightweight definition and original workflow instance/definition/Action correspondence. Existing canonical native JCL/events process rows remain in D2; no retired governance accept/apply/promotion/rollback is revived.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [JclJobControlComponentSpec](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala) | [describe a valid jobs[] YAML into normalized record form](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala#L50)<br>[bind the actual created and loaded definition to the submitted native Job and Action after its successful wait](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala#L1794) | [log line 26274](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L26274); `[info] JclJobControlComponentSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [WorkflowEngineSpec](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala) | [bootstrap component metadata and submit workflow-selected action as a new managed job](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala#L44)<br>[reuse the real instance and acknowledge two distinct jobs and actions for the exact priority-winning definition](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala#L405) | [log line 26216](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L26216); `[info] WorkflowEngineSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-07 CompositeQuery legacy and v2

Legacy query-only rejection and caller subject persist alongside v2 closed admission, target-owned authorization/context and declaration-ordered DAG behavior. D2 native default Ephemeral/persistent diagnostics pairs are separate evidence; no ordinary Query payload persistence or Domain presentation change.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [CompositeQueryEngineSpec](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryEngineSpec.scala) | [reject job-producing query execution modes](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryEngineSpec.scala#L76)<br>[preserve the caller security subject across the query-only boundary](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryEngineSpec.scala#L116) | [log line 30680](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L30680); `[info] CompositeQueryEngineSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [CompositeQueryV2EngineSpec](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryV2EngineSpec.scala) | [E01 reject malformed admission before a real QueryAction enters](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryV2EngineSpec.scala#L33)<br>[E03 execute reverse-declared diamond dependencies with declaration-order results](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryV2EngineSpec.scala#L106) | [log line 30488](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L30488); `[info] CompositeQueryV2EngineSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [CompositeQueryV2ProtocolSpec](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryV2ProtocolSpec.scala) | [when two actual component fixtures are registered](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryV2ProtocolSpec.scala#L28)<br>[when both real branches return context observations](../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryV2ProtocolSpec.scala#L106) | [log line 30571](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L30571); `[info] CompositeQueryV2ProtocolSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-08 Notification provider/default forwarding

Preserve provider-disabled refusal, default omission for synchronous/internal jobs, configured EventBus routing and provider-backed forwarding deduplication. Persistence, read state and inbox still belong to the provider; this is not generic independently fresh persistent-inbox proof.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [UserNotificationProviderRuntimeSpec](../../src/test/scala/org/goldenport/cncf/usernotification/UserNotificationProviderRuntimeSpec.scala) | [reject notify when the configured provider is disabled](../../src/test/scala/org/goldenport/cncf/usernotification/UserNotificationProviderRuntimeSpec.scala#L27)<br>[skip default Job event forwarding for synchronous managed jobs](../../src/test/scala/org/goldenport/cncf/usernotification/UserNotificationProviderRuntimeSpec.scala#L111)<br>[deduplicate forwarded Job event notifications by job id and trigger](../../src/test/scala/org/goldenport/cncf/usernotification/UserNotificationProviderRuntimeSpec.scala#L147) | [log line 30316](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L30316); `[info] UserNotificationProviderRuntimeSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

### COMPAT-09 Controlled scheduling/retry compatibility and evidence limit

Retain controlled due times, priority/retry/timer behavior. The restart-named retry example reuses one InMemoryJobEngine.State and the same Task in one process, so it is engine compatibility only; it cannot prove positive independently fresh native recovery of started work or close SC5/RC5 design choice.

| Existing suite/source | Exact selected existing case/source locations | Actual ACC001 suite-head / current evidence |
| --- | --- | --- |
| [ExecutionProfileJobSchedulingSpec](../../src/test/scala/org/goldenport/cncf/job/ExecutionProfileJobSchedulingSpec.scala) | [run equal-due Job work deterministically without host sleeping](../../src/test/scala/org/goldenport/cncf/job/ExecutionProfileJobSchedulingSpec.scala#L43)<br>[make a delayed retry eligible through the same runtime control](../../src/test/scala/org/goldenport/cncf/job/ExecutionProfileJobSchedulingSpec.scala#L94) | [log line 5335](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L5335); `[info] ExecutionProfileJobSchedulingSpec:`; selected-tests-passed/original-wholeStep-M2-pending |
| [JobRetryStateTransitionSpec](../../src/test/scala/org/goldenport/cncf/job/JobRetryStateTransitionSpec.scala) | [promote delayed RetryLater only after manual time advance](../../src/test/scala/org/goldenport/cncf/job/JobRetryStateTransitionSpec.scala#L50)<br>[rehydrate delayed retries into the manual timer after restart](../../src/test/scala/org/goldenport/cncf/job/JobRetryStateTransitionSpec.scala#L158) | [log line 5328](/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log#L5328); `[info] JobRetryStateTransitionSpec:`; selected-tests-passed/original-wholeStep-M2-pending |

The suite-head locations above refer to the original ACC001 log
`/private/tmp/skill.cncf.d/a-6a1d762e-e828-4c78-8d7b-a06784d49f15/33850-20261006T034013Z.log`. The retained typed result links that log and reader provenance.
Nine families/twenty suites are compatibility coverage, not new process scenarios
or additional whole-Step, CBD or Phase assurance. Existing D2 process mappings
and their genuine-process/refusal distinctions remain intact.

### Preserved restart and downstream acceptance boundary

The [normative RecoveryRequired truth table](durable-job-recovery-contract.md)
retains Running/Suspended and Submitted with startedAt present as
RecoveryRequired, with unchanged ActiveLifecycle/ExecutionAlreadyStarted reasons.
[Rehydration R2/R3](durable-job-runtime-rehydration-contract.md)
retains canonical/access admission before classification and port invocation,
with eligible V2 Submitted only, no startedAt, no recoveryRequired and complete
six-category replay evidence. Ordinary queue/timer owns due dispatch.
SC5 Running4 after TaskStart halt71 and RC5 Running5 pending retry after graceful
exit or halt71 refuse before Port/Task/queue, retaining rows/payment without
replay, refund or reset. Positive unstarted/scheduled recovery and completed
writer retry followed by fresh terminal observation remain separate evidence.
The original positive-started-work closure/design choice stays OPEN; D3 neither
resolves that parent choice nor changes the original scheduled/retry/crash matrix.

Live response identity/getResponse and raw durable body-free query remain
separate from eligible owned detached queryVisible. No terminal-summary result
reconstruction is inferred. The restart-named retry fixture reuses one
InMemoryJobEngine.State and the same Task in one process; it proves engine
compatibility only, not independently fresh native started-work recovery.
UI/API, framework Review/payload and notification-provider fixtures are not
actual CBD completed full Review or generic independently fresh persistent-inbox
proof. Actual CBD provider/Entity/completed full Review and independently fresh
runtime recovery remain OPEN. Its gateway remains provider-pipeline-pending;
read requires reportId/reportDigest. The CBD checkout is a read-only dependency
and its unanswered source authority request remains unchanged.

Original accepted spec/design/operator guidance promotion, whole-JM69-10
protected independent M2/local Step acceptance commit, predecessor ancestry
and reciprocal deferred-full validation, aggregate complete CNCF/downstream
validation, sole original epoch1 full Phase review, version/closure evidence and
final local phase-release commit remain mandatory. All four original JM69-10
acceptance criteria remain unchecked. This documentary appendix accepts no
Slice, Step or Phase and changes no contract, authority, API, schema or runtime.

## Current CNCF-only acceptance boundary — JM69-10D4 — 2026-10-06

status=authored_review_pending
restart_decision=decided-conformance-review-pending
acceptance_status=not-accepted

The [human scope amendment](../../.codex-workflow/phases/PHASE-69.7/PHASE-69.7-cncf-job-cbd-scope-amendment01.json)
excludes CBD from CNCF completion as HANDED_OFF_UNVALIDATED. The
[closure boundary](../journal/2026/10/2026-10-06-phase-69.7-cncf-job-closure-boundary.md)
records that delivered human authority and the retained native Step/Phase gates.
Earlier dated CBD requirements and OPEN design-question projections remain
historical; this dated boundary supersedes their current completion interpretation.
Every original SC/RC clause, fixture, oracle, failure and actual count is retained.

The exact parent decision is
[PHASE-69.7-JM69-10-native-restart-conformance-decision01@1](../../.codex-workflow/phases/PHASE-69.7/JM69-10-native-restart-conformance-decision01.json):
conform to the already normative recovery classification and native SC5/RC5
refusal contracts, without automatic replay of started external effects.
The [recovery truth table](durable-job-recovery-contract.md) and
[rehydration R2/R3](durable-job-runtime-rehydration-contract.md) remain unchanged.
Proof-complete eligible unstarted V2 Submitted work can reconstruct and execute
once through the original port/timer/queue, including writer halt71 before
TaskStart. Running/Suspended or startedAt-present interrupted work remains
RecoveryRequired even with complete proof, refusing before port/body/queue,
native mutation, refund or reset and preserving signed paid facts.

[Scheduled E1–E4](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala)
retains SC5 Running4 after TaskStart halt71 as refusal.
[Retry E1–E5](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala)
retains completed immediate/default delayed writer retry followed by exact
fresh terminal task/attempt/payment reads, while RC5 actual Running5 pending
retry at graceful writer exit or halt71 refuses. These are distinct oracles;
no positive started-work recovery is achieved or inferred.

The historical OPEN positive-started-work design question is now
decided-conformance-review-pending. Current representative9/accumulator132
verification, including later native UOW, is planned-not-run; retained
C11 accumulator131/1076 succeeded/pending1 does not substitute for it.
Complete original protected independent Step review must inspect every
scheduled/retry/crash scenario and reject acceptance if an attributable original
requirement or normative source mandates automatic positive started-work
restart absent from these fixtures. Report that exact conflict; no waiver,
oracle rewrite, skip or weakened assertion is authorized.

Native UOW, exact owned terminal result access, caller isolation, persistence,
authority and no-effect obligations remain CNCF-owned. CBD Provider/Entity/full
Review recovery remains excluded and unvalidated; framework payload fixtures
and Absent/UnavailableAfterRestart cannot establish it. Original Step validation,
compatibility/promotion, protected review/local commit and separate Phase-owned
aggregate full validation, sole original-epoch Phase review, version/local release
and closure audit remain pending. This appendix authors no acceptance.
