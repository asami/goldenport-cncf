# Durable Job OperationResponse Codec

status=design-partial-implementation
implementation=focused_validated_independent_review_pending
initial_authoring_status=authored_test_pending
phase=69.7
slice=JM69-10B1
recorded_at=2026-10-04

The [contract](../spec/durable-job-operation-response-codec-contract.md) fixes
one framework-owned domain serialization boundary outside signed durable
metadata. The codec serializes values only, using a closed typed wire and native
constructors. No execution object, provider, callback or normalization fallback
can supply a value. Record field order and duplicates, collection kind, numeric
width and raw bits, decimal context, JSON order/lexemes and verbatim YAML survive.

A separate resource policy owns strict Unicode and bounded pre-parser lexical
admission plus incremental rendering counters. The 8 MiB payload ceiling aligns
with existing owned-blob capacity; it does not enlarge the existing 1 MiB signed
record limit. Every nested envelope contributes to byte, depth and node budgets.
Unsupported bodies fail closed with one redacted refusal.

## Revision2 numeric capability frontier

The parent settled PH697-JM10B1-JSON-NUMBER-BOUNDS-01 after the historical
revision1 SCOPE_MISMATCH. Circe 0.14.6 JsonNumber.scala exposes six sealed final
case-class representations through public factories and Product. Tiny public
factory witnesses provide exact class identities. The adapter admits a class
before accessing its known arity/member: Long, Double, Float, raw JDK
BigDecimal, stored decimal String, or the second stored String of the
BiggerDecimal pair. It never reads BiggerDecimal, uses private-package code,
loads class names, reflects or serializes arbitrary Product values. The
current parser preserves original token strings without decimal conversion;
its complete input is still resource-preflighted before parsing.

RenderingBudget.literal takes a by-name string and admits its node first;
remainingBytes is read after that admission. A strict allocation-free lexeme
DFA preserves stored spellings and refuses malformed unsafe-factory strings.
Primitive conversion is constant bounded. Native decimal formatting first
checks exact raw JDK classes, then proves coefficient conversion <=remaining
bytes with bits<=4R, lower/upper decimal digit bounds and an exact TEN.pow(D)
comparison only at the uncertain boundary. The coefficient is converted once.
Long scale/exponent arithmetic computes exact canonical plain/scientific
length before the final builder allocation; coefficient ranges avoid copies.
This preserves full Int scale and canonical zero/trailing-zero spelling
without calling BigDecimal.toString/precision or introducing a magnitude or
exponent subset. Both coefficient text and final text individually fit R;
integer intermediates are bounded by 4R bits. Existing rolling counters still
admit the literal and remaining envelope punctuation.

Exact underlying JDK class guards also close native Record BigInt/BigDecimal
atom capabilities. ORC9..12 and the ordinary shrinking JSON generator cover
the six forms, independent canonical layout, full-wire byte boundaries and
hostile wrappers. Tests capture hostile BigInt counters after construction
because Scala's constructor itself reads bitLength. Initial implementation was
authored_test_pending; current selected execution is recorded below and
independent review belongs to the parent. A representation change in Circe needs a fresh
parent decision and validation, without a generic fallback.

The current terminal projection and getResult/queryVisible/management facades
retain their existing behavior. UnavailableAfterRestart does not provide the
full completed response. The actual CBD gateway reads queryVisible.model.result
and reportId/reportDigest, while ReviewPipelineTask currently reports
provider-pipeline-pending. No result is synthesized from that placeholder or a
summary. The rejected CBD component shadow-store spike remains rejected.

B1 itself supplies no payload ownership, authentication, authorization, durable
publication or fresh-runtime guarantee. Exact framework-owned publication and
authorized read/lifecycle integration need a later frozen parent boundary, then
actual independently fresh CBD pipeline/runtime acceptance. See the
[journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-codec.md).

## Current selected evidence and owned-payload companion — 2026-10-04

The parent records B1 subject5 as focused_validated_independent_review_pending:
`PH697-JM10B1-REP-005` completed three suites/32 succeeded, no failures, pending
or aborted suites; `PH697-JM10B1-ACC-001` completed 112 suites/912 succeeded,
no failures or aborted suites, plus one existing SqliteDataStoreSpec pending.
Both have compilerwarnings0, typed reader/SBT/wrapper0 and `lock=released`.
ORC1..ORC12 passed; ORC8 ordinary shrinking seed69710021L/min100 passed its
actual checked.passed/checked.succeeded>=100 assertions. Earlier authored and
failed histories remain in the linked journal: five shrink-map type
corrections, semantic refusal comparison, static expected-Consequence fixture
typing, stale Cozy prerequisite and successful refresh, and one-use pre-submit
retention recovery. Complete original Step independent assurance is unwaived.

The [B2 design](durable-job-operation-response-payload.md) defers normative
behavior to its [payload contract](../spec/durable-job-operation-response-payload-contract.md).
Its private native publication/verified-byte consumer and RP1..RP12 are
authored_test_pending, with six/113-suite execution pending. The B1 selections
are B1-only evidence. Real runtime terminal publication and authorized consumer
wiring, independently fresh actual CBD full completed result, the complete
original process matrix, compatibility and accepted guidance promotion,
Phase69.4..69.6 ancestry/reciprocal deferred validation, complete JM69-10
independent Step review/local acceptance, aggregate full CNCF/downstream
validation, sole epoch1 Phase review and final version/closure/local release
remain mandatory. Raw and management compatibility and the rejected
component-owned shadow store remain unchanged. JM69-09 DONE; JM69-10 OPEN;
Phase69.7 IN_PROGRESS; no Step/Phase or actual CBD acceptance is claimed.

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
