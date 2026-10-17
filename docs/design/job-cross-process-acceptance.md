# Job Cross-Process Acceptance

status=design-partial-implementation
implementation=focused_validated_independent_review_pending
initial_authoring_status=authored_test_pending
phase=69.7
slice=JM69-10A
recorded_at=2026-10-04
contract=[Job Cross-Process Acceptance Contract](../spec/job-cross-process-acceptance-contract.md)

The test harness launches sequential independent JVMs over one unique native
namespace. Each child constructs DurableJobBackupFixtures.Fixture afresh, with
its original Subsystem/SystemNode, configured provider, managed SQLite,
StandardEntityStore and LocalBlobStore. The writer uses original Persistent
Async submit/drain and keyed checkpoints. The reader supplies only native IDs
and independently constructed static evidence and startup access. It never
receives executable tasks or reads a writer control receipt.

The domain authority is the existing signed durable record and native quota
ledger in SQLite. Minimal bounded receipts carry actual process identities,
storage paths and selected identity/revision/terminal/definition/payment/effect
observations after all assertions and successful native shutdown. They carry
no record, payload, serialized state or content hash. Local before/after native
row equality and original mutation counters establish reader no-write behavior.

The harness discovers the actual test runtime classpath from java.class.path,
the URLClassLoader parent chain and probe/main code sources, then uses an
argument-array ProcessBuilder with absolute Java. Each owned child has a
60-second bound and owned-process-only timeout termination/wait. Logs are
retained in its target tree on failure and bounded when read for diagnostics.
Successful fixtures remove their own unique tree. No runtime production
ownership, dependency, port or server is added.

Same-process reopen specifications remain useful compatibility evidence but
cannot prove process independence. This slice observes real terminal
Succeeded(Absent), Registered projections and the existing
UnavailableAfterRestart management facade. It cannot reconstruct an exact CBD
Review response. The actual Textus CBD Support gateway reads model.result;
fresh exact downstream completion remains a mandatory later parent-frozen
boundary. The rejected component-specific shadow-store spike remains rejected.
All remaining original JM69-10 and aggregate Phase gates remain open.

## Current selected evidence — 2026-10-04

PH697-JM10A-REP-002 passed five suites/25 tests; PH697-JM10A-ACC-001 passed
111 suites/900 tests plus the original pending example. Both have zero failures,
aborted suites/compiler warnings, reader/SBT/wrapper0 and lock=released.
CP4 uses actual separate JVM pairs, ordinary shrinking, seed69710001L/min24.
Independent whole-Step review remains pending. The [A journal](../journal/2026/10/2026-10-04-phase-69.7-job-cross-process-acceptance.md)
retains the failed stale-classpath prerequisite and successful refresh history.

The [B1 codec design](durable-job-operation-response-codec.md) retains the
historical revision1 eight-leaf numeric SCOPE_MISMATCH. Revision2 completes the
frozen six-form numeric adapter and ORC1..ORC12 authoring with execution/review
pending. Its full Review fixture is no actual completed CBD
pipeline; publication/ownership/authorized-read integration and fresh runtime
acceptance remain mandatory. The existing Absent/facade limit and rejected
shadow-store spike remain unchanged.

## Current B1 selected evidence and B2 private composition — 2026-10-04

B1 subject5 is now focused_validated_independent_review_pending; the preceding
authoring-pending account remains historical. Parent-recorded
`PH697-JM10B1-REP-005` completed three suites/32 succeeded, failed/pending/
aborted0; `PH697-JM10B1-ACC-001` completed 112 suites/912 succeeded, failed/
aborted0 plus one existing SqliteDataStoreSpec pending. Both have
compilerwarnings0, typed reader/SBT/wrapper0 and `lock=released`. ORC1..ORC12
passed, including ORC8 ordinary shrinking seed69710021L/min100 with actual
checked.passed/checked.succeeded>=100 assertions. The
[codec journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-codec.md)
preserves initial/failed histories, five shrink-map corrections, semantic
refusal comparisons, static expected-Consequence fixture typing, stale Cozy
prerequisite/successful refresh and one-use pre-submit retention recovery.
Complete original Step independent assurance remains unwaived.

The [B2 payload design](durable-job-operation-response-payload.md) relies on the
[payload contract](../spec/durable-job-operation-response-payload-contract.md)
for its private native publication and verified-byte consumer boundary.
RP1..RP12 are authored_test_pending with six/113-suite execution pending;
B1 selected results supply no B2 validation. Its separately constructed native
managed owners share one process, and its full Review value is a domain fixture.
Neither supplies actual completed CBD or separate-JVM acceptance. A's harness,
signed Absent result and current raw/management facade are retained.

Real runtime terminal publication and authenticated consumer integration,
independently fresh actual CBD full completed Review/result without shadow
storage, the complete original scheduled/retry/crash, cursor/payload/corruption/
version/migration and JCL/governance/CompositeQuery/user/admin/notification/
security matrix, compatibility/promotion, Phase69.4..69.6 ancestry/reciprocal
deferred validation, complete JM69-10 independent Step review/local acceptance,
aggregate full CNCF/downstream validation, sole epoch1 Phase review and final
version/closure/local release remain mandatory. The component-specific CBD
shadow-store spike remains rejected. JM69-09 DONE; JM69-10 OPEN; Phase69.7
IN_PROGRESS; no Step/Phase acceptance, push/publication/deployment or successor.

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

## Native scheduled and interrupted process composition

The [SC1..SC6 contract](../spec/job-cross-process-acceptance-contract.md#native-scheduled-and-interrupted-process-companion)
is consumed by three test-only programs: an owned sequential launcher and
counted native fixture, a public six-mode fresh-JVM probe, and four executable
examples. The fixture wraps JobDurableRuntimeFixtures.Fixture, retaining its
original Controlled/Manual execution profile, engine, independent static keyed
Source, startup source, trusted Port and genuine lifetime-recovery Task. Its
observed StandardEntityStore uses the same configured managed SQLite lease and
original LocalBlob. The startup context keeps original native access; a separate
job_admin context is composed solely for the authorized terminal management
observation. No production provider, schema, recovery policy or management
policy changes.

The scheduled writer owns genuine Persistent Async Admission with an independent
1..20-second delay. A normal writer closes its native owner, Subsystem and node
before exit. An unstarted crash writer observes that same committed boundary
and halts itself with71. A fresh scheduled reader supplies only independent
access/replay/static evidence plus a new local Port/Task. Original registration
adopts the existing paid authority without Admission or refund. Manual time
controls the exact pre-due and inclusive-due boundary, with the genuine five
tail checkpoints. A third JVM starts only after the executing reader closes and
exits; it authenticates terminal facts without supplying work. Static Supplied
Absent and the original UnavailableAfterRestart facade remain the result limit.

The Running crash writer owns an immediate actual task whose body observes
TaskStart's committed Running4/Pending/native association, one invocation and
zero canonical completion, then halts its own JVM. The fresh refusal reader
supplies independent original evidence and fresh work, but original recovery
rejects dispatch as RecoveryRequired. It reads actual native state under the
original Subsystem managed lease through the installed observed entity store,
so observation requires no ready-owner grant or alternate backend. Local raw
row equality plus actual create/update/delete/lifetime counters establish zero
mutation. The reader closes normally; automatic ambiguous-work replay remains
outside the existing recovery policy.

The parent owns one unique absolute target native/control/log tree and sequential
array-argument launches. Each child owns all domain, paid, signed-revision,
registration and resource assertions. The closed <=16KiB observation carries
only opaque identity and selected facts. Actual exit71 plus false closure flags
identify pre-halt observations; actual exit0 follows native closure assertions.
Readers do not read writer receipts or receive rows, Task objects, payloads,
credentials or results. PID/start/UUID identity and bounded60-second child plus
5+5-second owned cleanup preserve the process boundary. Ordinary shrinking
uses a fresh real trio per generated delay, independently of runtime evidence.

The [scheduled process journal](../journal/2026/10/2026-10-04-phase-69.7-job-scheduled-cross-process.md)
owns work and validation history. The original retry/process matrix, full CBD
provider pipeline/fresh completed Review result, compatibility/promotion,
ancestry, independent whole-Step assurance and all aggregate Phase/release
requirements remain required. The rejected CBD shadow-store workaround remains
rejected; this composition supplies no CBD acceptance substitute.

## Real automatic retry process composition

The [RC1..RC6 contract](../spec/job-cross-process-acceptance-contract.md#real-automatic-retry-process-companion)
binds three test-only programs:
[fixture](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessFixtures.scala),
[public probe](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessProbe.scala)
and [five-leaf specification](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala).
The fixture composes the original Controlled/Manual runtime and actual
Subsystem/SystemNode managed SQLite, LocalBlob, observed entity store and paid
lifetime quota. Its own counted Probe is passed in its own runtime inputs.
Immutable recipe authorization/definition/retention and original pair()
descriptor mapping remain independent of live or signed records. Terminal
attempts derive only from the two current closed task models, preserving each
model's times and independent fixed failure evidence.

The writer submits one original own/per-task Task as Persistent Async. The
actual first failure returns RetryNow or RetryLater; the second invocation
returns Scalar("ok"). Original automatic settlement owns reserveRetry, live
state and dispatch. Immediate drain2 or original delayed60s split/drain0/0/1
produces eight signed revisions, two task pairs and Failed/Succeeded closed
attempts. Paid lifetime expansion to(4,192,2,2) is real retry payment, with one
root Admission and two actual normal associations.

The fresh terminal JVM starts only after writer exit, constructs original
candidate/access/replay/source independently, supplies no Task and invokes no
Port. Original terminal projection and Ready ownership expose signed
Succeeded8/Absent and original authorized UnavailableAfterRestart. Local native
rows and observed effects establish no reader work or mutation. Only management
observation adds job_admin to a derived context retaining principal/tenant and
native views. Raw and management contracts remain unchanged.

Pending writers stop after the real first failure and retry payment/scheduling:
live Submitted/due60 has no new signed Submitted/retry-due checkpoint. Signed
TaskOutcome retains Running5/Pending and Failed task with startedAt. Graceful
closure and own halt71 consume this same boundary. The fresh refusal JVM
supplies its own counted Port/Task, but original RecoveryRequired prevents
dispatch. Native observation uses the original managed Subsystem lease and
observed store without a ready-owner grant. No replay, refund, timer
reconstruction, record rewriting or recovery relaxation occurs. The installed
owner is absent after refusal; its vacuous closure flag cannot establish
independently observed provisional-owner closure.

The parent launcher owns sequential child processes, unique absolute target
native/control/log directories, array arguments, actual Java/classpath and
PID/start/UUID identity. Closed bounded observations follow normal actual
resource closure or explicitly precede own halt71. No writer receipt/result/
Task/evidence/rows cross into reader logic; only opaque JobId and independent
split are arguments. Ordinary shrinking runs an actual completed pair per
mode/split input with seed69710061L/min24/workers1 and the same domain/native/
payment/closure oracle.

The [retry journal](../journal/2026/10/2026-10-05-phase-69.7-job-retry-cross-process.md)
owns authoring and execution accounting. Genuine pending failure is not NR15's
synthetic eligible Submitted/due seed. Positive automatic cross-process retry
of started work requires a later explicit protected parent decision.
Production retry/checkpoint/recovery/ownership contracts remain unchanged.
The actual CBD provider pipeline and independently fresh completed full
Review/result remain mandatory; native Absent/facade observations supply no
substitute. The rejected CBD shadow-store workaround remains rejected, and
every remaining original whole-Step and aggregate Phase gate remains required.

## Native management cursor process composition

The [UC1..UC6 companion](../spec/job-cross-process-acceptance-contract.md#native-management-cursor-process-companion)
consumes the unchanged [management query contract](../spec/durable-job-management-query-contract.md).
Its [seven executable leaves](../../src/test/scala/org/goldenport/cncf/job/JobCursorCrossProcessAcceptanceSpec.scala),
[owned launcher](../../src/test/scala/org/goldenport/cncf/job/JobCursorCrossProcessFixtures.scala)
and [fresh-JVM probe](../../src/test/scala/org/goldenport/cncf/job/JobCursorCrossProcessProbe.scala)
compose the original DurableJobBackupFixtures.Fixture, registered configured
owner, managed SQLite, observed StandardEntityStore and LocalBlob. Writers
use original Persistent Async submit/drain and original six keyed checkpoints.
Fresh readers independently construct static candidates/access/replay, admit
the signed Succeeded6/Absent roots and register body-free terminal projections.
No production or existing support program changes, duplicate native/domain
persistence, new provider or public interface are introduced.

The first reader obtains an actual queryPage continuation. Only this public
opaque token, opaque Job IDs and independent count/first limit reach the next
process; children never open another receipt. A counted policy delegates
JobQueryPolicy.default with empty visibility key, retaining existing binding
and wire grammar. Each child checks equal native updated times and the
canonical Job-ID tie-break order before asserting prefix/suffix traversal.
Summary field shape stays the original seven fields. Token use neither
hydrates payloads nor changes the existing raw-result and authorized
UnavailableAfterRestart boundary.

Filter, genuinely foreign current-principal, malformed/overbound token and
limit-bound readers exercise actual engine queries and original Consequence
classifications. Trusted startup access stays independent of current query
authorization; the foreign query has no session or overview capability.
Actual native rows, signatures and paid authority are compared locally across
startup, queries and drain, with no Tasks, executable Job maps, replay or
mutation. The first-page cursor is sourced from a durable reader so no live
metadata equivalence is assumed.

Expiry uses an independent native appender that first authenticates the two
old completed roots, then genuinely submits/drains a third Task. Its new
paid association and six checkpoints expand global Published quota to three;
old per-Job rows, signed records and payment associations stay unchanged.
After real closure and exit, a fresh reader opens all three genuine native
roots and the old cursor expires because the authorized snapshot changed.
No source subset, record corruption, fake signed terminal or immutability
certificate supplies this effect.

The test-owned launcher retains sequential actual JVM identity, bounded child
waits, owned cleanup, closed observational receipts and retained failure logs.
Each child checks native ownership closure before receipt. Ordinary shrinking
uses count2..3 with dependent firstlimit1..count-1, seed69710071L/min24/workers1
and a fresh actual writer/first-reader/continuation trio for every case.

The [cursor journal](../journal/2026/10/2026-10-05-phase-69.7-job-cursor-cross-process.md)
owns authoring and selected validation accounting. C2 and D1 evidence remains
historical evidence for those boundaries. This composition supplies no
completed actual CBD provider pipeline or independently fresh full Review;
the rejected shadow-store spike remains rejected. Remaining original process
matrix, compatibility and guidance promotion, original whole-Step protected
M2 assurance/local acceptance, aggregate full validation, sole epoch1 Phase
review and final local release remain mandatory.

## Native refusal process composition

The [NF1..NF8 companion](../spec/job-cross-process-acceptance-contract.md#native-refusal-process-companion)
composes three new test-only programs around the original
DurableJobBackupFixtures.Fixture. A genuine Persistent Async Task completes
through the original registered provider, six signed native checkpoints and
Published lifetime authority. Only after those positive observations does the
writer change exactly one native Job row as a closed negative input: malformed
text, mismatched digest, unsupported root version, trailing space or absence.
Its selected native row comparison checks exact changed canonicalJson and
equality of every noncanonicalJson field; deletion is exactly None. Record.++
appends fields rather than constructing an expected replacement. Every other
complete Job and storage/lifetime/blob quota row remains equal.

A separate JVM receives only opaque IDs and bounded case arguments, constructs
its own access/replay/static recipe, and starts the original configured owner.
Discovery captures local selected native rows before any recovery under the
actual startup lease. The original terminal registration pass owns each
candidate's independent fact: healthy records remain Registered, the selected
bad input is Corrupt or Missing, and exact ordinal/identity order is retained.
The original runtime pass returns all Refused for these completed/bad inputs.
The whole-vector owner barrier therefore refuses Ready publication and leaves
Degraded execution with no installed owner. Healthy terminal summaries are an
existing observational effect of this failed startup; they remain body-free
and are not retracted or reinterpreted as successful runtime ownership.

Original lifetime.snapshot depends on _published -> _authorized -> store.load
of the original Job. Writer pre-fault and healthy reader snapshots authenticate
and equal the stored entry.snapshot. For all five faults, the bad Job's actual
quota API refuses with `Durable job lifetime quota unavailable`. Its Published
entry persists, including after Job-row deletion, and the privileged local SQL
test observation decodes the original lifetime row, selects exactly one entry
and observes its existing snapshot. Stored paid (2,96,1,1), remaining0, one
normal association, empty claims and cancelclaimed false equal pre-fault facts.
This local observation grants no spending or public API authority to the bad
Job and carries no token/record across the process boundary. Good signed native
records remain Succeeded6/Absent with original task association and definition;
bad native input remains Corrupt/Missing. Local selected native rows are equal
through startup, raw queries and drain. Original Task/Port/evidence/native
mutation counters and selected ID/clock/scheduler observations establish zero
reader work, repair, replay, normalization, refund or payment reconstruction.
The original production provider, store, recovery policy and schemas stay the
sole authorities consumed by this test composition.

The launcher owns sequential actual JVMs, absolute Java and the original
runtimeClasspath helper, unique native/control/log directories, PID/start/UUID
identity, bounded waits and owned cleanup. Closed bounded receipts follow
assertions and actual resource shutdown. The single per-Job quotaaccess fact
is Authenticated for pre-fault writers and healthy readers, and
RefusedNativeObservation for the selected bad reader Job only. The original
eight paymentFacts remain separately equal. Writer terminal/payment facts are
labelled pre-fault. Writer installed-owner closure is observed; a refused reader
records not-installed without turning optional-owner absence into a claim
about provisional-owner closure. No records, Tasks, payloads, credentials or
management integrity certificate cross the process boundary. Ordinary
dependent shrinking runs the complete native oracle for each actual pair.

The [C4 journal](../journal/2026/10/2026-10-05-phase-69.7-job-native-refusal-cross-process.md)
owns authoring and selected validation history. Native refusal observations
and the original scalar fixture supply no completed actual CBD provider
pipeline or independently fresh full Review result. Actual CBD scope and all
remaining original JM69-10/Phase acceptance, compatibility/promotion, aggregate
validation, sole epoch1 review and final local release remain mandatory.


## Migration process consumer composition

status=selected_validation_passed_review_pending
manifest=PHASE-69.7-JM69-10C5-implementation03@1
validation_status=REP003-ACC001-passed

The [MG1..MG8 companion](../spec/job-cross-process-acceptance-contract.md#migration-process-consumer-companion) consumes original native migration M1..M12/MC/MI and owner R7. Three test-only programs compose DurableJobMigrationCheckpointFixtures.Fixture around its actual configured provider, native measured Entity store, original LocalBlob adapter and real SystemNode leases. Genuine original Persistent Async Task completion and six checkpoints create native payment before deliberate signed legacy fixture preparation. The optional input uses original putObserved and original signed input checkpoint. Mixed inputs retain the middle genuine V2; only malformed-tail negative input uses raw SQL to replace one canonicalJson.

A closed CasePlan selects bounded all-V1/mixed/invalid-tail, payload and Operator capability. Readers receive independent fixed parameters and opaque candidate IDs only; they construct original access/replay/evidence recipe without reading writer or reader receipts. Source.before observes actual native rows, admits signed snapshots only from admitted loads, retains corrupt classifications, captures references/metadata/bytes and privileged stored paid rows, then freezes native effect counters under the startup lease. Exact full values stay in that JVM. Original source entries remain ordered and cached once for both registration passes.

Migration permits original storage reserve/Job update/confirmation only for V1. Complete destination body equals source body with identity.revision+1; providerrevision+1 and V2 format are separately observed. Every other body field/time, native lifetime and Blob payment, metadata and physical bytes remain equal. Storage committed entries match actual destination metadata and have no pending reservations; already-V2 Job rows and the entire independently fresh V2 native state are locally equal. Enabled fresh readers perform real physical preflight without any re-checkpoint.

The genuine original putObserved payload entries in these cases remain Published with the original32 lowercase hexadecimal reservation token retained after confirm. The shared payload oracle asserts that exact format, while complete native Blob row equality preserves the token and every other field across migration/fresh startup. This does not impose token presence on all Published inventory entries.

Native Entity audit clocks observe precisely three native updates per migrated V1, distinct from the engine-owned unchanged manual scheduling time. No clock replacement/reset or suppressed counters supply proof. Successful full startup also performs original local terminal/lifetime/resource seeding and registers one original maintenance timer. Exact complete terminal Registered/runtime Refused vectors and Ready consume original XOR, with empty executable Job maps/queue and zero Port/Task/evidence/ID/drain. Retained terminal identities and binding keys equal all candidate IDs; local paid snapshots equal authenticated original native seeds. Original scheduler is captured before shutdown, then real closure cancels its one timer. Default/false/no-operator/invalid-full-preflight failures have no owner/timer/local seed, zero native writes/clocks and unchanged manual time; default/false retain original bridge binding while early failures have no bridge.

The Case launcher reuses original runtimeClasspath/process identity, absolute Java, unique owned namespaces, strict exit-before-next-start ordering, bounded child wait/cleanup and failure excerpts. Exact closed <=16KiB receipts carry selected facts after full local assertions and actual resource shutdown, never signed bodies/native ledger text/bytes/certificates. Parent checks every selected field and all distinct process identities. Seven examples map one-to-one to MG1..MG7; E7 ordinary dependent shrinking drives at least24 actual trios. MG8 applies to every leaf.

The [C5 journal](../journal/2026/10/2026-10-05-phase-69.7-job-migration-cross-process.md) retains both pre-edit planning mismatches and original no-edit/no-test history. Manifest01's both-Registered premise contradicted R7; Manifest02's zero Entity clocks and unchanged full-startup timer/resource premise contradicted measured original effects. Manifest03 distinguishes these observations without product changes. No C5 execution result existed at initial authoring. Actual REP001 stopped before tests on the stale Cozy prerequisite; actual refresh exited0/reader0/lock=released with480 valid entries and three missing-credentials diagnostics distinct from CNCF compiler warnings. Actual REP002 completed8 suites/55 tests:52 succeeded,3 failed, zero aborted/ignored/canceled/pending/compiler warnings, reader2/SBT1/wrapper1, permission consumed and lock=released. C5-PUBLISHED-BLOB-TOKEN-ORACLE-01 explains all three payload failures: the new None predicate contradicted unchanged original reserve/confirm token retention. The [frozen Fix Manifest](../../.codex-workflow/phases/PHASE-69.7/JM69-10C5-validation-fix01.json) supplied the exact format predicate. Historical pre-validation status was repair_planned_test_pending/REP002-failed-repair-planned, with REP003 representative8 and ACC001 accumulator120 planned-not-run.

Current revision2 selected results are actual: PH697-JM10C5-REP-003 completed8 suites/55 tests,55 succeeded/pending0; PH697-JM10C5-ACC-001 completed120 suites/980 tests,980 succeeded plus only original SqliteDataStoreSpec pending1. Both have failed0/aborted0/canceled0/ignored0, compilerwarnings0, typed reader0/SBT0/wrapper0, permission consumed and lock=released. The [C5 journal selected evidence](../journal/2026/10/2026-10-05-phase-69.7-job-migration-cross-process.md#actual-revision2-selected-validation) links JM69-10C5-representative03-result.json@1 and JM69-10C5-accumulator01-result.json@1. Actual one-cause narrowing1->0 supplies selected validation evidence only. Whole-JM10 protected M2/local Step commit, actual CBD/all remaining original process matrix, compatibility/promotion, aggregate full validation, sole epoch1 Phase review and final local release remain required; no C5-only review/commit or whole-Step/Phase acceptance is claimed.
## Fresh Job experience process composition

JM69-10C6 is selected_validation_passed_review_pending under the
[UX1..UX9 companion](../spec/job-cross-process-acceptance-contract.md#fresh-job-experience-process-companion).
validation_status=REP002-ACC001-passed
acceptance_status=not-accepted
The three new test programs reuse the unchanged B4 native Fixture. The writer
submits one actual independently selected scalar Task, observes its six signed
checkpoints and owned physical response/payment, then shuts down its original
Subsystem/node/SQL before exit. Each fresh reader uses open(id) with its own
original User recipe, startup grant, configured provider and native owners.

The closed current-caller modes explicitly choose principal, subject kind and
exact capabilities after startup. They never borrow startup authorization or
derive current grants from a signed record. The actual production
JobExperienceService receives the original engine. Management snapshot queries
retain raw None and UnavailableAfterRestart even though the separate B4
queryVisible API can hydrate an authenticated owned response. C6 never calls
that hydration path in a fresh reader or manufactures application annotations.

Initial signed/payment/rows/quota/physical observations end before the service
baseline. Service actions and local domain expectations are followed immediately
by complete counter/runtime equality; final native verification then measures
its own observation metadata I/O separately. Audit output remains an observation
channel and supplies no storage authority or no-effect baseline. Ready terminal
paid/resource seeds and the original maintenance timer are preserved. Original
shutdown cancels that timer and drains leases; SystemNode owns physical SQL close.

One fresh writer-reader pair serves every subcase. Real ProcessBuilder argv,
actual PID/start/UUID checks, 60s child bound, owned5s+5s termination, <=16KiB
closed receipt and retained bounded failure logs use a unique C6 namespace.
Successful cleanup removes only that namespace. All bodies, native records and
authorization stay local; readers receive only closed selectors, namespace/new
receipt path, opaque Job ID and independent alpha input.

Eight semantic examples include ordinary shrinking over six caller modes with
seed69710081L/min24/workers1 and both generated scenario and property GWT.
This direct facade composition supplies neither separate HTTP/component ingress
proof nor notification/JCL/governance/CompositeQuery or actual completed CBD
pipeline evidence. Original whole-Step/Phase gates and prior histories remain.

Actual `PH697-JM10C6-REP-001` completed8 suites/79 tests, all79 succeeded,
with failed0/canceled0/ignored0/pending0/aborted0. Two E121 compiler warnings
leave selected_validation_pass=false and acceptance pending despite
SBT0/wrapper0/typed reader0, consumed permission and lock=released. The
separately owned metrics-source repair changes only two exhaustive-enum
wildcard fallbacks to explicit `case null => -1` and the Oct5 header; ignored
null inputs and all fixture/facade/process semantics remain unchanged.
Revised10REP then unchanged121ACC were planned-not-run at the historical repair
authoring point. Actual parent [mechanical/source/header/GWT admission revision2](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-mechanical-admission-revision2.json)
now precedes subject2 [REP002](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-representative02-result.json),
10 completed suites/89 tests,89 succeeded/pending0, and [ACC001](../../.codex-workflow/phases/PHASE-69.7/JM69-10C6-accumulator01-result.json),
121 completed suites/988 tests,988 succeeded plus only original SqliteDataStoreSpec
pending1. Both have failed0/canceled0/ignored0/aborted0/compilerwarnings0,
no compile failure, typed reader0/SBT0/wrapper0, consumed permissions and
lock=released. Actual one-code-unit narrowing warning2->0 supplies selected
validation only. The [current C6 journal](../journal/2026/10/2026-10-05-phase-69.7-job-experience-cross-process.md#actual-revision2-selected-validation)
retains result/log/summary and capacity/recovery history. The parent owns original
whole-JM10 protected M2 independent assurance; no remaining consumer gate is closed.

## Fresh Job notification process composition

JM69-10C7 is focused_validation_passed under the
[NT1..NT9 companion](../spec/job-cross-process-acceptance-contract.md#fresh-job-notification-process-companion).
validation_status=representative-and-accumulator-passed
acceptance_status=not-accepted
The new [fixture](../../src/test/scala/org/goldenport/cncf/job/JobNotificationCrossProcessFixtures.scala),
[probe](../../src/test/scala/org/goldenport/cncf/job/JobNotificationCrossProcessProbe.scala)
and [executable specification](../../src/test/scala/org/goldenport/cncf/job/JobNotificationCrossProcessAcceptanceSpec.scala)
compose unchanged B4 native ownership with actual Component/Subsystem descriptor
notification wiring. Before native start/open, Core uses the native engine,
actual Subsystem.add binds that engine to actual EventBus/EventStore and the
installed component supplies only scope to the original User context. Descriptor
configuration resets the controlled-test flag; the already-created engine and
original manual GlobalRuntimeContext still own scheduling. C7 never asserts
that flag or substitutes component default resources for native context.

The writer directly submits the original scalar PublicationFixtures Task with
independent alpha input and selected web parameters inside the original owner
lease, then drains the engine. B4 produce is unused because it cannot supply
those parameters; its internal Task counters remain zero. Own selected counters
report runs1/canonical1/getters>0 separately. Original six source boundaries,
signed definition/User authorization, physical owned bytes, Published quota and
paid2/96/1/1 native facts establish genuine publication. The actual terminal
lifecycle traverses EventBus/default forwarder to the configured provider; no
manual event or synthetic scalar stands in for this path.

Seven closed writer modes select accepted/no-app/disabled/refused/failure/throw/
unsafe-app. Provider outcomes explicitly set original acceptedAt, notificationId,
no provider-generated id and empty attributes. Local independent literal request
and exact safe diagnostic assertions reserve subject/scope/metadata/correlation,
safe link and public failure meanings. Failure/NonFatal marker assertions apply
only to the allowed diagnostic/request, because actual lifecycle events may
contain result summaries. No inbox, delivery shadow persistence, dedupe replacement
or successful external-delivery guarantee is introduced.

After writer's actual shutdown/exit, an independent accepting enabled-provider
reader opens the same physical native namespace with original recipe/access and
opaque id. It supplies no Task/port, reaches Ready/Registered and retains raw
None/empty debug parameters and management UnavailableAfterRestart. Original
status/query/result/management plus Mine(None) list/get expose the completed
native Job with unavailable result and empty controls. Provider calls and actual
EventStore remain empty. This preserves existing terminal-projection semantics
without a replay hook or invented recovered application annotation.

Initial native/rows/physical/quota audits precede a full effect/runtime baseline.
Read observations compare original B4 counters, clocks/IDs, provider/events,
maps/native bindings/paid seeds/resources/work sequence/queue/manual time,
scheduler/retention/timer before final native audits. drainAll is zero. Final
native equality incurs exactly three metadata observations; counters are never
reset. The original maintenance timer and terminal resource seed persist until
owned shutdown. Scheduler due time is engine manual time plus1000ms, independent
of CountingClock. Provider close, owner/scheduler close, node-owned SQL close,
subsystem close, leases0/timers0 precede each child receipt.

Unique C7 case namespaces, absolute Java/original runtimeClasspath, sequential
actual ProcessBuilder argv and distinct PID/start/UUID enforce true independent
JVMs. Child waits are60s with owned5s+5s termination; only successful owned trees
are removed. Failed logs remain with <=2048-byte excerpts. Closed <=16KiB
observational receipts transfer no native body, result, credentials, grants or
provider private error. Seven explicit scenarios plus the ordinary seeded
min24/workers1 generated property retain all leaf/inner/outer GWT and rule metadata.
Initial authored_validation_pending/planned-not-run is historical and superseded
by actual subject revision2 REP0027 suites/51 succeeded/pending0 and ACC001123
suites/1006 succeeded plus only original SqliteDataStoreSpec pending1. Both have
failed0/aborted0/canceled0/ignored0/compilerwarnings0, typed reader/SBT/wrapper0,
permission consumed and lock=released. The [C7 journal actual selected evidence](../journal/2026/10/2026-10-05-phase-69.7-job-notification-cross-process.md#actual-revision2-selected-validation)
links results/logs/summaries and [mechanical admission revision2](../../.codex-workflow/phases/PHASE-69.7/JM69-10C7-mechanical-admission-revision2.json).
Initial REP00143 successes/8 failures exposed one shared fresh-scope reference
defect. The registered M1 correction in the notification fixture/probe captures
the installed component scope once and compares the original submitted scope,
preserving original caller/core/clock/id/store/security and all eight GWT leaves.
Consumed pre-review unit1 narrowed8->0; C7-SELECTED-RESULT-PROJECTION-01 consumes
no additional reviewed-Step/Phase repair and resets no counters. Original wholeStep
promoted guidance, actual completed CBD provider/Entity/full Review/fresh runtime,
remaining matrix, protected independent M2/local commit, aggregate full validation,
sole original epoch1 Phase review and local release remain mandatory.

## CompositeQuery native process composition

status=focused_validation_passed
manifest=PHASE-69.7-JM69-10C8-implementation01@2
validation_status=subject8-REP010-ACC002-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [CQNT1..CQNT9 companion](../spec/job-cross-process-acceptance-contract.md#compositequery-native-process-companion)
is authored by exactly three new test programs: the
[owned fixture/launcher](../../src/test/scala/org/goldenport/cncf/job/CompositeQueryNativeCrossProcessFixtures.scala),
[child native probe](../../src/test/scala/org/goldenport/cncf/job/CompositeQueryNativeCrossProcessProbe.scala)
and [five-leaf executable specification](../../src/test/scala/org/goldenport/cncf/job/CompositeQueryNativeCrossProcessAcceptanceSpec.scala).
The original native Fixture owns the actual public facade subsystem and engine.
The writer selects manual=false and retains original REALTIME scheduling;
the reader independently selects manual=true and retains original controlled
time. The separate existing Query fixture is created without suppliedJobEngine,
with owned scope and marker cq2-diagnostics. Its fixture engine and subsystem
engine remain separate unused resources. Immutable target-c8 resolves only to
that independently admitted Query subsystem. The public facade asserts its
owning native subsystem engine identity before invocation.

One named owned caller Thread invokes executeV2Blocking with timeout5000 and
parallelism1..2; the original native workers execute ordinary Async branch
tasks. AtomicReference retains its actual result or failure and join10000
rejects missing/live/failed callers. No latch, manual writer drain, polling,
delegate, new scheduler, Task.run or direct V2Engine/Protocol invocation stands
in for this execution. The real Query action uses independent password scalar
and branch-ID properties. Local exact aggregate and captured installed-component
runtime/scope/caller-context/cell assertions consume its actual observations.

Returned diagnostics supply genuine IDs in declaration order. Each actual ID
awaits Success(Void)5000 and passes the native state-monitor read barrier;
the original engine holds that monitor across terminal signed checkpoint.
The fixture then observes no lifecycle refusal and six actual source requests
and pairs at revisions1..6. Source recipes independently fix original User
authorization, cq2-diagnostics definition, absent input and the canonical
Operation wrapper Pending descriptor. Original pair adapts only actual dynamic
task IDs/statuses; independent local descriptor/native oracles verify the fixed
identity and final Committed outcome. Paid native2/96/1/1 remaining0 with one
normal task/no claims and complete Job/global quota rows remain authoritative.
Live Void and signed Succeeded(Absent) are distinct from the invocation-local
Query scalar. Signed body checks exclude private values, password/token,
selector/target alias and branch-ID text; Blob content/publication stays empty.
Default Ephemeral follows the same real facade and query path with original
metadata and zero keyed/native Job/payment/storage/publication effects.

Only independent mode/alpha/count, opaque persistent IDs, owned namespace and
new receipt path cross the JVM boundary. Readers never read writer receipts.
The original startup source observes complete native rows under its real
startup lease before registration; it supplies independently fixed candidates.
Ready/Registered terminals and original runtime Refused facts install no live
Task/rehydration work. Raw result is None, authorized queryVisible remains None
and management is UnavailableAfterRestart. The foreign current User consumes
the exact original queryVisible ownership refusal while management conceals
existence as Success(None), without borrowing startup authority or inventing
privilege. Query/command/observation/source/port/Blob/native mutation
effects stay zero. Full local effect/runtime equality surrounds read APIs and
one bounded original reader drainAll returning0. Final signed/payment/complete
row audits preserve actual observation load counts. Manual clock, ID count,
native resource/binding seeds, queue/work sequence and the one maintenance
registration remain unchanged until cleanup. Original realtime writer retention
polls are permitted and never suppressed or fixed-count asserted.

Cleanup always attempts cancellation of its own invocation and bounded
interrupt/join of its own live caller, target fixture engine shutdown,
Subsystem.shutdownOwned(target subsystem), native subsystem.shutdownC and
native node.shutdownC. Each original owned engine timer closes, worker pool
and optional scheduler termination await5000 each, and pending/executing0,
owner/maintenance/lease closure, logical bindings/nodes/physical native SQL
closure precede any receipt. The primary failure retains all cleanup errors
as suppressed failures; a target cleanup failure cannot skip native close.
Retained completed records do not count as executing resources.

The launcher owns unique target/composite-query-native-cross-process/work/case-*
trees, absolute Java/original runtimeClasspath/array argv, confirmed writer
exit-before-reader and actual distinct PID/start/UUID checks. Child bounds are
60s plus owned5s+5s termination; successful trees alone are removed and failed
logs remain with <=2048-byte excerpts. Closed unique <=16KiB observational
receipts contain no Query scalar, native body, ledger, credentials or grants.
Five semantic GWT leaves include normal shrinking over alpha1..8/count1..2/two
policies, seed69710081L/min24/workers1, and both generated and outer-property
GWT. All domain assertions occur locally before receipt/exit.

The [C8 journal](../journal/2026/10/2026-10-05-phase-69.7-composite-query-native-diagnostics.md)
records the no-edit initial binding mismatch: suppliedJobEngine configured only
the Query fixture field, while the public facade selected its different
subsystem engine. Revision2 freezes the actual original native subsystem facade
and independent target. Subsystem is final, so the historical suggested override
is not used. No schema exception or source fabrication corrects that mismatch.
At initial authoring selected representative8/accumulator128 were planned-not-run.
The current dated summary below supersedes that validation status; independent
whole-Step acceptance remains pending. Prior C7 receipts, original authority/base/
binding/epoch/history/counters and whole-JM10/CBD/full-Phase obligations remain.


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

## JCL native process composition

status=selected_validation_complete
manifest=PHASE-69.7-JM69-10C9-implementation01@3
validation_status=REP006-and-ACC001-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [JX1..JX9 companion](../spec/job-cross-process-acceptance-contract.md#jcl-native-process-companion)
uses the [fixture/launcher](../../src/test/scala/org/goldenport/cncf/job/JobJclCrossProcessFixtures.scala),
[writer/fresh-reader probe](../../src/test/scala/org/goldenport/cncf/job/JobJclCrossProcessProbe.scala)
and [seven-scenario specification](../../src/test/scala/org/goldenport/cncf/job/JobJclCrossProcessAcceptanceSpec.scala).
The original JobDurableRuntimeFixtures.Fixture owns the same actual configured
Subsystem, native jobEngine, SystemNode, managed SQLite, observed EntityStore,
LocalBlob and owner lease. Normal JobControl Factory/createPrimary/bootstrap
installs the public protocol. A separate real CommandAction target is initialized
and installed in this same Subsystem; engine identity is asserted at startup.
Actual public operation resolution and control.logic execute the Action under
the native lease. The operation context explicitly supplies those native stores
while preserving installed Component/action scope and caller User/subject/tenant.
Actual ProcedureActionCall scalar inputs, context and canonical UnitOfWork
post-commit effects are observed. Writer REALTIME scheduling and fresh reader
Controlled/Manual scheduling remain original fixture capabilities.

The only production realization is a private JCL prepared-task decorator.
Root, finite flow and same-Job continuation preparation select root, child and
continuation defaults respectively. Existing relation/role/scope values remain
authoritative; missing role/scope use own/per-task. Every other original field,
run and canonical/admission callback delegates to the native prepared task,
including OperationEvaluationActionTask capture/finalization. Returned prepared
context is preserved and JclEventAwareTask remains the outer adapter. No runtime
model heuristic, action bypass, Task replacement, global engine/projector/codec
change or altered persistence/security policy is introduced. Compensation
preparation retains its existing behavior and the engine's explicit compensation
relation/actual failure or original parent overrides remain authoritative.

For finite N1..3 the first actual Root has no parent/reference; each later Child
uses the immediately preceding opaque task ID as both parent and relation
reference. Same-Job continuation uses its actual source-task parent. The initial
independently declared Pending recipe forms the same closed chain. The C9 source
calls unchanged addressed DurableJobKeyedEvidenceFixtures.pair and copies only
descriptor kind to Action. Started/outcome/terminal boundaries use the actual
accumulated model prefix, otherwise independent pending IDs/count. Expected
topology and target operation come from declared position, command declarations
and preceding opaque ID, never observed relation/target metadata. Live action
contexts, live models, final signed descriptors and fresh facade preserve N
distinct IDs/order; Running/Pending progresses to Succeeded/Committed. Models
are never detached or padded, and count2..3 generated cases remain included.

Canonical await plus the original monitor barrier observes the actual scalar
and terminal persistence. Persistent semantic revision4+2N and exact boundary
sequence accompany signed Succeeded(Absent). Native chargedTasks2N,
historyCredits32+64N+notes (notes1 root-only or2 finite flow), admissions1,
cancelCredits1, remaining0, N normal IDs and no claims/cancel claim stay exact.
Private command scalars/parameters/JCL source are excluded from the signed
body and no owned-result publication is fabricated. Explicit Ephemeral source
executes the same public scalar/canonical path with no durable/source/paid/Blob
rows; its reader receives no prior IDs. Invalid Draft/Retired/malformed/
unsupported submissions compare post-legitimate-setup native/effect baselines
and refuse before any new admission or target/source/storage effect.

The [canonical lightweight lifecycle](../spec/job-definition-lifecycle-contract.md)
owns definition identity and concurrency. The fixture installs its declared
JobDefinition typed collection/revision binding beside the existing Job
collection in the same EntityStore and managed SQLite. Public create/get/update/
retire exclusively own lifecycle writes; no seed, row rewrite or key-derived
identity is introduced. Public active A submission completes before current
source B update/retirement. The original JobEntity created by engine projection
retains submitted A in nested snapshot/debug parameters with the real saved
definition ID/key/format/profile. Fresh exact-key Store-scope EntityQuery search
finds current B, then loadDetached reads its actual ID. A separate typed
JobEntity.entityId(actualJobId) load requires a positive revision carrier and
the original submitted A. Original signed authentication independently checks
the full fixed metadata envelope and matches ID/key/format to native A/B.
Signed metadata has no source body and does not authenticate A; its fixed
legacy metadata version/revision/hash fields do not revive domain controls.

Startup's real lease observes complete native Job/signed/payment/quota/current
definition rows before Registered terminal projection. Readers compare those
rows and full paid state after registration and authorized reads without
resetting counters; permitted typed read counts remain observed. They install
no callable tasks and execute no target/source/port/queue work, Entity mutation,
payment or Blob publication. Raw/getResult/queryVisible remain body-free/None;
management is UnavailableAfterRestart. Independent foreign current caller
receives original exact ownership refusal and concealed management None.
Only Observation timestamp can be normalized in comparison copies. Actual
JobEntity source A is confined to trusted native fixture reads and cannot leak
through fresh signed/raw/query/management results.

The launcher owns target/job-jcl-cross-process/work/case-* trees, actual
absolute Java/original runtimeClasspath/array argv, writer exit before reader,
distinct real PID/start/UUID, child60s and owned5s+5s termination. Opaque Job IDs
and independent bounded selectors are the only cross-JVM authority. Closed
<=16KiB observation receipts follow all local assertions and resource cleanup;
the reader never consumes writer receipts. Failures retain bounded log excerpts
and successful own trees alone are removed. Cleanup always attempts native
Subsystem/node, owner/maintenance/lease, physical SQL, timer, original worker
pool/optional scheduler termination and verifies node leases0, queue empty,
pending/executing0; failures retain suppressed cleanup errors. Useful GWT
output is preserved and unnecessary native snapshots are not printed.

E1 inline root, E2 three-task flow/overrides, E3 dual-Entity immutable source,
E4 four pre-execution refusals, E5 foreign caller, E6 explicit Ephemeral, and
E7 ordinary generated finite flow retain adjacent Given/When/Then and should
oracles. Dependent alpha1..8/count1..3 generation uses seed69710101L/min24/
workers1 and normal shrinking with inner/outer GWT, checked.passed,
succeeded>=24/discarded0. No ignored/pending leaf, filtering, implication,
noShrink, metadata mirror or smaller property replaces real processes.

The [Oct6 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-cross-process.md)
retains revision1's actual pre-edit C9-SIGNED-DEFINITION-SOURCE-01 and
revision2's partial-authoring C9-FINITE-FLOW-ROOT-PARENT-02. Manifest3 freezes
canonical dual-native source authority and Root/Child/Continuation/prefix
semantics within the same eleven paths. At initial authoring selected6REP/129ACC
were planned and no C9 test success existed. Current selected validation is
recorded below; original whole-Step review and acceptance remain pending. C8 actual8/66 and128/1050+
original pending1 and all historical/HYG evidence remain. C10 actual event/
continuation processes, remaining security/positive started-work decision,
actual admitted CBD provider/Entity/completed full Review/fresh real recovery,
compatibility/guidance promotion, whole-JM10 protected independent M2/local
commit, aggregate full validation/sole original epoch1 Phase review/local
release remain OPEN. Current lightweight definition lifecycle is the complete
governance scope; no retired governance system is restored.

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

## JCL event and continuation native process composition

status=authored_validation_pending
manifest=PHASE-69.7-JM69-10C10-implementation01@1
validation_status=planned-not-run
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [EC1..EC9 companion](../spec/job-cross-process-acceptance-contract.md#jcl-event-and-continuation-native-process-companion)
is authored by the [C10 fixture](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessFixtures.scala),
[probe](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessProbe.scala)
and [nine-leaf specification](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessAcceptanceSpec.scala).
The unchanged C9 composition supplies JobDurableRuntimeFixtures' configured
native provider, original engine, leased SQLite/EntityStore, measured owned
Blob, lifetime owner and failure-preserving closure. C10 installs a distinct
real CommandAction target with Component.Core's native engine argument and
one static authorized EventBus observer, then submits canonical inline YAML
through installed public JobControl. The target and observer perform no direct
Job submission, Task running or event publication.

The closed scenario/alpha/N selectors own expected action order, parameters,
Root/Child/Continuation topology and Own/PerTask metadata. Request models bind
only actual newly issued task IDs after independent checks; the shared genuine
pair is retained with the same C9-local Action kind conversion. Expected
boundary plans preserve the Running source while nested handlers start/finish,
grow the admitted vector, and commit the source only after its handlers. All
terminal descriptors match live order and actual source-parent identities.
Per-handler native work reservations produce T=base+N, charged2*T,
history32+64*T+32*N+notes3/4 and admissions/cancelcredits1+N, remaining0 and
exact T normal IDs, without claims. No source/projector/runtime policy changes
are part of this composition.

Real attempted action contexts, canonical UoW post-commit effects and wrapper
outcomes are separate observations. SourceFailure aborts before emission;
DispatchFailure retains EventBus's typed event.dispatch/previous cause after
append; HandlerFailure aborts the first handler and prevents later handlers.
The latter two retain already successful source delegate commits. These
Ephemeral failure scenarios prove existing public behavior and empty durable
Job storage, with no signed failed recovery or new failure policy.

The original ReceptionDomainEvent and native EventRecords prove occurrence,
lineage, observer-before-handler ordering, persistence option and unchanged
subscriptions. EventStore remains the Subsystem in-memory baseline. All fresh
readers have selected eventcount0, even following persistent writer events.
Persistent readers independently register exact terminal/payment facts and
load positive native inline JobEntity debug metadata, with no saved source
snapshot invented. Public scalar management retains UnavailableAfterRestart;
foreign selection retains strict refusal/concealment. Read-only rows/payment,
zero execution/source/pair/port/Blob/publication/SQL/Entity-write effects and
original resource shutdown are asserted locally before bounded scalar receipts.

Each ordinary or generated case owns a unique native/control/log namespace and
uses absolute Java plus the existing runtimeClasspath. Writer exit precedes
fresh reader construction; actual process identities/UUIDs remain distinct.
Wait60s, stderr2048-byte and receipt16384-byte bounds, owned-only termination,
failure-log retention and successful-only cleanup apply. The generated leaf
uses dependent alpha1..8/N1..3, seed69710102L/min24/workers1 and ordinary
shrinking; every invocation launches the full real process pair.

The [C10 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-event-cross-process.md)
records authoring in exactly ten owned paths. Parent-selected8REP/130ACC remain
planned-not-run; original C9 green and failure evidence are retained. Current
Stage C10 is IN_PROGRESS; JM69-09 DONE, JM69-10 OPEN and Phase69.7 IN_PROGRESS.
Original protected whole-Step M2/commit, security/positive started-work choice,
actual CBD full Review/fresh recovery, compatibility/promotion, predecessor
deferred-full, aggregate/full-Phase validation/review and local release remain
OPEN. The three stable [HYG-PH697 findings](../journal/2026/10/2026-10-01-phase-69.7-hygiene-follow-up.md)
retain their separate follow-up scope. No Step/Phase acceptance or commit is
inferred from authoring.

## Native startup security cross-process composition

status=validated_review_pending
validation_status=REP002-passed-ACC001-passed
phase=69.7
step=JM69-10
slice=JM69-10C11
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted
recorded_at=2026-10-06

The [SEC1..SEC8 companion](../spec/job-cross-process-acceptance-contract.md#native-startup-security-cross-process-acceptance)
is implemented by three new test-only programs:
[fixtures](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessFixtures.scala),
[reader probe](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessProbe.scala)
and [seven-leaf specification](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessAcceptanceSpec.scala).
No shared fixture or production security/lifecycle/persistence semantics change.
The [C11 journal](../journal/2026/10/2026-10-06-phase-69.7-job-security-cross-process.md)
records the initial ten-path authoring history and current seven-path repair
boundary under the [Fix Manifest](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-validation-fix01.json)
and [CyclePlan revision2](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-cycle-plan02.json).

A new Case delegates the genuine healthy writer to the unchanged original
JobCrossProcessFixtures.Case. Its own new independent reader launches only
after that writer closes and exits. Count1..3 original Persistent Async tasks
produce native signed revision6/Succeeded(Absent) and original published paid
Seed `(2,96,1,1)` through actual six keyed checkpoints. Every invocation owns
a fresh `target/job-security-cross-process/work/case-*` native/control/log tree.
Only opaque Job IDs and closed count/Mode/index selectors reach the reader;
no writer receipt, domain-state object, Task or serialized signed body is read
by that child.

The reader creates DurableJobBackupFixtures.Fixture afresh, using the original
configured provider, Subsystem/SystemNode, managed SQLite, EntityStore,
LocalBlobStore and payment authority. Candidate access/replay are constructed
independently from static original constants. ExactAccess and ExtraScopes
prove the existing required-scope subset rule; ForeignTenant, ForeignSubject,
MissingScopes and UnrelatedScopes select only one denied candidate among
otherwise exact access. The original job_admin configured context cannot
change record tenant/subject/requiredScopes rules. No grant normalization,
policy extension or malformed-access decision is introduced.

Original startup discovers the candidate source once and caches its ordered
entries for terminal and runtime recovery. Every genuine completed job is
runtime Refused; positive terminal reports are all Registered and startup is
Ready with its owner installed. A selected denied candidate is terminal
Refused and prevents aggregate Ready: fixed durable runtime startup refused,
Degraded/execution unavailable/runtime not ready/no owner. Mixed healthy
prefixes and suffixes remain body-free durable projections; only the denied
query/status/projection is absent. The composition observes actual separate
reports and retained projections rather than deriving them from a summary or
asserting blanket rollback.

The source.before hook records selected native job and storage/lifetime/blob
quota rows locally before recovery. A distinct trusted original-access
observer uses the real managed datastore lease to authenticate each intact
signed record and payment snapshot, including the denied job. That observer
never overrides the denied startup access. Revision, static definition,
Succeeded(Absent), paid counters, remaining0, exact single normal ID, no claims
and no cancellation claim are compared as bounded scalar parent observations.
Denied native loading with the actual candidate access must be typed Refused;
trusted original access independently proves storage integrity. Local rows
remain equal after all startup, query/authentication and payment observations.
There is no repair, refund, checkpoint or domain-state receipt transport.

Reader assertions cover one source call, empty original tasks/keyed requests,
Port0, empty executable durable/runtime maps, queue0, leased drainAll0,
Entity create/write/delete/lifetime-write0, body/getter0, original ID/Clock
counter deltas0 and Job resource pending/executing0. Actual global
pendingTimerCount is1 for positive Ready or0 for denied startup. Ready's original
scheduler is started/not closed/not in flight, with nextDueAt present and no
lastResult. Its exact status and raw registration count stay unchanged through
reads/native authentication/leased drainAll; no timer reset/subtraction/disable
or time advance is used. Existing healthy terminal memory
retention is allowed. The positive public query remains body-free
Succeeded/Durable/Persistent; raw result is None and admitted management keeps
UnavailableAfterRestart. Mixed healthy candidates assert only retained
query/status/projection; no absent-owner management success is inferred.
The existing signed Absent/facade boundary supplies no actual full CBD Review.

Receipts follow all observations and actual Subsystem/SystemNode/SQLite
shutdown: nodeStopped, activeLeaseCount0 and installed-owner closed, global
timers0 and installed scheduler closed/not started/not in flight/no nextDueAt.
The timer receipt retains actual preclosure1positive/0denied. They
carry only closed bounded scalar keys and fixed refusal, no access values,
private conclusion, credential, record or payload. Parent/writer/reader actual
PID/start pairs, PIDs and JVM UUIDs are distinct over the same jobs.db path.
The unchanged actual classpath helper and absolute Java provide sequential
argument-array launches, wait60s, owned-only destroy/wait5/force/wait5,
stderr<=2048 bytes and receipt<=16384 bytes. Failed logs remain; successful
cases remove only their own namespace. No process-global kill, home mutation,
server, network or alternate cache is needed.

S1 runs four positive pairs; S2..S5 run one denied pair each; S6 runs twelve
mixed pairs with every denied mode at each ordinal. S7 uses dependent closed
mode/count1..3/index selection and ordinary integer shrinking preserving mode.
Count shrink candidates are filtered to1..3 with index<count, followed by
index shrink candidates filtered to0<=index<count, retaining ordinary recursive
shrinking within the valid dependent domain, seed69710103L/min24/workers1
and a fresh full-oracle pair for every invocation. Visible adjacent GWT clauses
surround the actual setup/action/assertion in all seven leaves and every
matrix/generated invocation. At least44 real pairs are required across a
successful complete suite, with actual passed/succeeded>=24/discarded0 property
checks. Actual REP0014 suites/26 tests produced24 success/2 failures (S1/S7),
compiler warnings/aborts/pending/ignored/canceled0, reader2/SBT1/wrapper1,
consumed permission and lock=released. C11-READY-MAINTENANCE-TIMER-ORACLE-01
and C11-DEPENDENT-SHRINK-DOMAIN-01 are repaired under rePLAN controls125/126.
REP002same4/ACC001same131 were planned, not run at repair authoring.

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

Initial C11 authoring also removed only the Oct.2 history line from each already-current Oct.6
DurableJobTaskProjection.scala and DurableJobProjectionSpec.scala header,
retaining original @since, author and the spec's Sep.14 history. The strict
C10 Continuation/Root/Child/V1 behavior and its parent-recorded subject4 green
focused2/25, REP9/92 and ACC130/1069+pending1 are preserved. Original complete
JM69-10 protected M2/commit, actual CBD full Review/fresh recovery, positive
started-work closure, compatibility/guidance promotion, predecessor reciprocal
deferred-full, aggregate validation, sole epoch1 Phase review and local release
remain OPEN. No Step/Phase acceptance, publication, push, deployment or
successor work is inferred from this authoring.


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

The [recovery truth table](../spec/durable-job-recovery-contract.md)
continues to classify Running/Suspended or Submitted with startedAt present as
RecoveryRequired; existing ActiveLifecycle and ExecutionAlreadyStarted reasons
remain unchanged. The [rehydration R2/R3 contract](../spec/durable-job-runtime-rehydration-contract.md)
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

The [normative RecoveryRequired truth table](../spec/durable-job-recovery-contract.md)
retains Running/Suspended and Submitted with startedAt present as
RecoveryRequired, with unchanged ActiveLifecycle/ExecutionAlreadyStarted reasons.
[Rehydration R2/R3](../spec/durable-job-runtime-rehydration-contract.md)
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
excludes CBD as HANDED_OFF_UNVALIDATED. The
[current closure boundary](../journal/2026/10/2026-10-06-phase-69.7-cncf-job-closure-boundary.md)
supersedes earlier dated CBD completion prerequisites and OPEN design-question
projections, preserving all original SC/RC clauses, fixtures, oracles and history.

The parent settled
[restart conformance decision01@1](../../.codex-workflow/phases/PHASE-69.7/JM69-10-native-restart-conformance-decision01.json)
as conformance to the already normative recovery classification and native
SC5/RC5 refusal contracts. The
[truth table](../spec/durable-job-recovery-contract.md) and
[rehydration R2/R3](../spec/durable-job-runtime-rehydration-contract.md)
already separate eligible proof-complete unstarted V2 Submitted work from
Running/Suspended/startedAt-present work. The original port reconstructs only
eligible work; original timer/queue processing executes it once, including
unstarted halt71 before TaskStart. Started work retains RecoveryRequired even
with full proof and refuses before port/body/queue/native mutation/refund/reset,
preserving signed paid authority. No started external effect is replayed.

[Scheduled E1–E4](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala)
keeps SC5 Running4 after TaskStart halt71 as refusal.
[Retry E1–E5](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala)
keeps completed immediate/default delayed writer retry and exact fresh terminal
task/attempt/payment reads distinct from RC5 Running5 pending graceful/crash
retry refusal. No failure fixture is rewritten as success. The previous OPEN
positive-started-work question is decided-conformance-review-pending; no positive
started-work restart is claimed.

Current representative9/accumulator132, including native UOW added after C11's
131-suite/1076-success/pending1 result, is planned-not-run. Complete original
protected independent Step conformance review remains required and must report
an exact conflict if an attributable original requirement or normative source
mandates automatic positive started-work restart absent from these fixtures.
There is no replay waiver, skipped scenario or new API/schema/recovery policy.

CNCF retains native UOW and exact owned-result/access/persistence/authority/
no-effect obligations, compatibility and accepted guidance promotion. CBD full
Provider/Entity/Review/fresh-runtime integration remains separate unvalidated
handoff work. Original Step review/local acceptance commit and separate
Phase-owned repository-full validation, sole original-epoch Phase review,
version/local release and committed closure audit remain pending.
