# Durable Job OperationResponse Codec Contract

status=normative-partial-implementation
implementation=focused_validated_independent_review_pending
initial_authoring_status=authored_test_pending
spec=durable-job-operation-response-codec
phase=69.7
step=JM69-10
slice=JM69-10B1
recorded_at=2026-10-04

The package-private pure `DurableJobOperationResponseCodec` exposes
`encode(OperationResponse): Consequence[String]` and
`decode(String): Consequence[OperationResponse]`. This contract was initially
authored before its executable specification with execution pending. Current
selected B1 execution is recorded below; independent whole-Step review and
acceptance remain pending.

## Closed canonical wire

The root is exactly `{"format":{"schemaId":"cncf.job-operation-response","version":1},"response":...}`.
Descriptor members have the displayed order. Compact standard Circe
`Printer.noSpaces` string escaping is fixed. The response is exactly one of:

| kind | Members after kind |
| --- | --- |
| void | none |
| scalar | value: typed int, long, double, boolean or string atom |
| record | fields: ordered array of exactly {key:string,value:atom} |
| json | value: ordinary Circe JSON |
| yaml | value: verbatim string, never parsed or executed |

Atoms have `type` first and the following exact members:

| type | Members after type |
| --- | --- |
| null | none |
| string, boolean | value of the corresponding JSON type |
| byte, short, int, long | value: exact bounded JSON integer of that width |
| float, double | value: finite canonical Java toHexString string |
| bigint | value: canonical signed decimal string |
| bigdecimal | unscaled: canonical signed decimal string; scale: exact integer; precision: exact integer; rounding: enum string |
| record | fields: same ordered field array |
| vector, list | values: ordered typed atom array, preserving immutable collection kind |
| json | value: ordinary Circe JSON |

Only exact concrete native Record, Field and Field.Value.Single classes are
trusted. Subclasses are refused before their getters are read. Duplicate
Record keys, field order, null Single, empty Record, nested concrete types and
List/Vector distinctions survive. Reconstruction uses native constructors,
never generic Record encoders or normalization. Scalar uses only the five
existing closed ScalarValue givens. Http, Opaque, arbitrary objects, closures,
Map/Array/Set/custom Seq, unknown Field.Value and structural nulls refuse.

Finite float/double raw bits survive, including signed zero, subnormals and
maximum values. BigInt and BigDecimal unscaled values have at most 1,024
absolute decimal digits; producer bitLength must be at most 3,402 before decimal
conversion. BigDecimal scale is [-16384,16384], MathContext precision [0,1024],
and rounding exactly UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN or
UNNECESSARY. Reconstruction preserves unscaled/scale/context without rounding.
Consumer decimal lexemes are bounded before construction. JSON object insertion
order, array order and number lexemes remain exact, without numeric coercion.

Consumer grammar, version, field sets and types are closed. After reconstruction
the exact producer rendering must equal the original text. Whitespace, reordered
descriptors, duplicate JSON object keys, extra fields and alternate spellings
refuse. Duplicate Record keys remain valid array entries. This canonical domain
format check provides no workflow artifact immutability certificate.

## Closed Circe numeric admission

Revision2 follows parent decision PH697-JM10B1-JSON-NUMBER-BOUNDS-01.
All six current Circe 0.14.6 JsonNumber representations are supported. Exact
class witnesses come only from tiny public-factory values: fromLong,
fromDouble, fromFloat, fromBigDecimal, fromDecimalStringUnsafe and fromString.
Only after exact class identity admission may the codec read public Product
arity and the known native member (index0/arity1, except fromString's
index1/arity2). It never reads the stored BiggerDecimal member, enumerates a
Product, uses class names/reflection or admits an unknown representation.

Stored immutable lexemes are admitted by an allocation-free ASCII JSON-number
DFA after null and remaining-byte length checks. Optional minus, canonical
integer prefix, optional fraction with digits and optional e/E exponent with
optional sign and digits must consume the entire string. Spelling, trailing
zeros, negative zero and arbitrarily long admitted exponent text survive
without numerical parsing or coercion. Known finite Float/Double and Long use
their constant-bounded Java decimal spelling and the same lexeme admission.
Typed integer decoding admits 20 characters before its existing 19-digit and
Long range checks, retaining Long.MinValue.

Literal node admission precedes evaluation of numeric conversion. For native
JSON BigDecimal, require exact JDK BigDecimal and unscaled BigInteger classes
before any getter or conversion. With R remaining bytes, require bits<=4R.
With D=R-minus-sign, require D>=1 and use digit bounds
lower=floor(max(bits-1,0)*301029995/1000000000)+1 and
upper=floor((bits+1)*301029996/1000000000)+1. Refuse lower>D; when upper>D,
compare abs(coefficient) against TEN.pow(D) before textual conversion. Thus
the sole coefficient decimal conversion is proven to fit R, with exact
near-boundary admission and bounded integer work.

Use Long arithmetic for adjusted exponent=digits-1-scale and all lengths.
Plain layout applies exactly when scale>=0 and adjusted>=-6: its unsigned
length is digits for scale0, digits+1 for 0<scale<digits, otherwise scale+2.
Scientific length is digits plus optional dot, E, optional positive sign and
the bounded exponent spelling. Include the coefficient sign and refuse exact
length>R before allocating the final builder. Append admitted coefficient
ranges without substring copies. Preserve all Int scales, zero and trailing
zeros; do not call native BigDecimal.toString, precision or toPlainString.
Native Record BigInt/BigDecimal atoms also reject raw JDK subclasses before
getters/conversion, retaining their existing digit/scale/context domain bounds.
An updated dependency representation requires new parent inspection and
validation; this closed adapter is specific to the current library.

## Inclusive resource and refusal rules

The entire wire, including envelopes and embedded JSON, admits at most
8,388,608 UTF-8 bytes, 64 container levels and 32,768 value nodes. Every object,
array and scalar/null value is one node; object keys are excluded. UTF-16 must
have strictly paired surrogates; replacement encoding is forbidden. Parsed JSON
strings and keys receive the same Unicode validation.

ResourcePolicy.validateText checks null, strict Unicode and actual bytes before
a bounded quote/backslash-aware lexical container/node scan, before parsing.
A quoted token followed by colon is an object key. Other token starts count
once. This scan admits resources only; strict grammar remains parser-owned.
Quoted delimiters never add depth. Stop immediately at any excess.

Producer rendering is incremental: admit each append, node and descent against
rolling counters, pre-count escaped string bytes before allocation, and stop
collection traversal at the first excess. Never build an unbounded intermediate
JSON tree/string/collection then apply a limit. No truncation or dropped fields.
Every successful output is consumable under the same bounds.

All malformed, unsupported, resource and ordinary NonFatal failures return only
`Consequence.argumentInvalid("durable job operation response refused")`.
InterruptedException restores the interrupt flag and returns the same refusal.
Fatal errors are not caught. Unknown toString, print/display/show, toResponse,
single, provider, conversion and callback methods must never be invoked.

## Executable specification

[DurableJobOperationResponseCodecSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseCodecSpec.scala)
uses trace `spec:durable-job-operation-response-codec phase:69.7 slice:JM69-10B1`.

| Scenario | Required behavior | Initial authoring evidence (historical) |
| --- | --- | --- |
| ORC1 | Independently handwritten full six-field completed Review Record wire and restoration | authored_test_pending; representative fixture only |
| ORC2 | Void/five scalars/ordered lexeme-preserving JSON/verbatim YAML known wires | authored_test_pending |
| ORC3 | Exact nested ordered types, duplicates, null, bits and decimal context; numeric refusal | authored_test_pending |
| ORC4 | Hostile unsupported bodies refused with zero getter/conversion observations | authored_test_pending |
| ORC5 | Closed malformed/noncanonical/version/numeric/Unicode refusal | authored_test_pending |
| ORC6 | Exact 8 MiB byte and 32,768 full-wire node limits and one over | authored_test_pending |
| ORC7 | Exact depth 64/65, quoted delimiters and extreme malformed preflight | authored_test_pending |
| ORC8 | Ordinary shrinking generated domain round trips, seed 69710021L, minimum 100 | authored_test_pending |
| ORC9 | All six public numeric factory forms in direct JSON and Record JSON atoms, independently literal wires | authored_test_pending |
| ORC10 | Independent plain/scientific layout table, negative mirrors, zero and full Int scale extremes | authored_test_pending |
| ORC11 | Exact full-wire native/stored numeric byte capacities, one over and pre-conversion giant coefficient refusal | authored_test_pending |
| ORC12 | Malformed unsafe numeric lexemes and hostile raw JDK wrappers refused without codec-time capability reads | authored_test_pending |

## Original integration obligations

B1 changes no signed DurableJobRecord, public API, query/management facade,
provider or runtime lifecycle. Framework-owned result publication, physical
reference ownership and authorization-first exact reading require a later
parent decision. The existing terminal facade carries no model.result, while
the actual CBD gateway needs queryVisible.model.result/reportId/reportDigest
and its current task yields provider-pipeline-pending. This domain fixture is
no proof of an actual completed pipeline or independently fresh CBD runtime.
The rejected component shadow-store spike stays rejected. All original JM69-10
process matrix, downstream, compatibility, ancestry, complete-Step and Phase
closure obligations in the [cross-process contract](job-cross-process-acceptance-contract.md)
remain open. JM69-09 DONE; JM69-10 OPEN; Phase69.7 IN_PROGRESS.

## Current B1 selected execution and B2 authored companion — 2026-10-04

B1 subject5 is focused_validated_independent_review_pending. The parent records
`PH697-JM10B1-REP-005`: three completed suites, 32 succeeded, zero failures,
pending or aborted suites; and `PH697-JM10B1-ACC-001`: 112 completed suites,
912 succeeded, zero failures or aborted suites, plus one existing
SqliteDataStoreSpec pending example. Both have zero compiler warnings,
typed reader/SBT/wrapper exit0 and `lock=released`. ORC1..ORC12 passed these
selected runs; ORC8's ordinary shrinking seed69710021L/min100 passed actual
`checked.passed` and `checked.succeeded >= 100` assertions. These totals belong
to B1 and do not validate B2 or accept the Step.

The initial table above and revision1 numeric SCOPE_MISMATCH remain historical.
The [B1 journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-codec.md)
retains the five shrink-map type corrections, semantic refusal assertions,
static expected-fixture Consequence supertype correction, stale Cozy
prerequisite/successful refresh and one-use pre-submit retention recovery.
Failed earlier attempts are not erased; complete original Step independent
assurance remains unwaived.

The [B2 payload contract](durable-job-operation-response-payload-contract.md),
[design](../design/durable-job-operation-response-payload.md) and
[journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-payload.md) now record
the authored private publication/read boundary and RP1..RP12, all
authored_test_pending with selected six/113-suite execution pending. B1's pure
wire and bounds are unchanged. Real terminal publication and authorized
consumer integration, independently fresh actual CBD completed Review/result
recovery, the complete original process matrix, compatibility/promotion,
Phase69.4..69.6 ancestry/reciprocal deferred validation, complete JM69-10
independent Step review/local acceptance, aggregate full CNCF/downstream
validation, sole epoch1 Phase review and final version/closure/local release
remain mandatory. Existing raw/getResult/queryVisible/management compatibility
and rejection of the component shadow-store spike remain intact. JM69-09 DONE;
JM69-10 OPEN; Phase69.7 IN_PROGRESS. No push/publication/deployment/successor.

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
