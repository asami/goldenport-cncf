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
authored before its executable specification with execution pending. HISTORICAL
Phase 69.7 B1 execution and its then-pending independent whole-Step review and
acceptance are recorded below; current Phase 115 adoption is separate.

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
| null | none; retained input only, omitted only at an owning Record field |
| string, boolean | value of the corresponding JSON type |
| byte, short, int, long | value: exact bounded JSON integer of that width |
| float, double | value: finite canonical Java toHexString string |
| bigint | value: canonical signed decimal string |
| bigdecimal | unscaled: canonical signed decimal string; scale: exact integer; precision: exact integer; rounding: enum string |
| record | fields: same ordered field array |
| vector, list | values: ordered typed atom array; retained list and vector decode as Vector |
| json | value: ordinary Circe JSON |

Only exact concrete native Record, Field and Field.Value.Single classes are
trusted. Subclasses are refused before their getters are read. Duplicate
Record keys, field order, empty Record, nested concrete types and ordered
collection elements survive. Core owns native Record construction and recursive
normalization. At this foreign codec ingress, a retained exactly
`{"type":"null"}` field atom is validated and omitted before native construction.
It is refused outside an owning Record field, including every direct or nested
sequence position; no element is filtered and no position shifts. Both retained
`list` and `vector` atoms reconstruct as Vector. New actual NonEmptyVector values
encode incrementally with the existing `vector` atom; declared nonempty types
are reconstructed through Core typed readers, which refuse empty input.
Normal semantic native-null atoms refuse; typed Json.Null follows the separate
raw JSON contract. There is no shadow Record or List restoration. Reconstruction
uses native constructors, never generic Record encoders or a duplicate
normalizer. Scalar uses only the five
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

Consumer grammar, version, ordered field sets and types are closed. After text
resource/Unicode preflight and Circe parsing, `root.noSpaces` must equal the
original text. Every closed grammar object's member keys must match the exact
declared order. Whitespace, reordered descriptors, duplicate JSON object keys,
extra fields and alternate string escapes or typed numeric spellings refuse.
Raw JSON application object names are not a closed grammar: their insertion
order, nulls and numeric lexemes remain intact, including empty objects/arrays.
Duplicate Record keys remain valid ordered array entries. Retained-input syntax
admission precedes semantic reconstruction; it does not compare the normalized
response's new encoding to the old text. A retained payload may reencode with
null-valued Record properties omitted and list tags replaced by vector tags,
without a new format/version/tag or live-data rewrite. This canonical domain
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

## Phase 115 retained-input and collection adoption — 2026-10-18

status=phase115_closed_all_five_rcp_done
step=RCP-CONSUMERS
slice=B1
initial_authoring_status=authored_test_pending
execution=selected_focused_validation_passed
review=independent_complete_protected_focused_step_review_pass

The current working semantics above adopt Core's canonical Record contract.
The dated Phase69.7 execution receipts below remain historical evidence for
their original subjects; they do not establish this adoption's validation or
acceptance. ORC1–ORC12, including the ordinary shrinking ORC8 law with at least
100 successes, remain required. The following table retains initial authoring
status; the current selected execution paragraph below supersedes its pending
status without changing those scenarios:

| Scenario | Required behavior | Evidence |
| --- | --- | --- |
| ORC13 | Independently handwritten full retained format1 input normalizes nested field nulls and old lists, retaining duplicates, empties, raw JSON nulls/order/exact lexemes; independent canonical new wire | authored_test_pending |
| ORC14 | Actual nested NonEmptyVector uses vector wire, decodes to Vector and reconstructs declared nonempty types through Core; empty nonempty read refuses | authored_test_pending |
| ORC15 | Root and direct/nested sequence null atoms refuse without filtering; malformed null atoms, unknown tags and reordered closed members refuse | authored_test_pending |
| ORC16 | Ordinary shrinking connected retained-shape law, seed 11516001L, at least 100 successes and zero discards, independently specified normalized values and new wire | authored_test_pending |

Current selected adoption execution: original Consumer B1 passed the 17/17
codec representatives, including ORC13–ORC16; its subsequent regression still
aborted with exit 255 and has no final regression total. Later Information
owner validation passed 17/17 in three suites. The original native regression
ran 58 (33 passed, 25 failed); missing `createEntity` forwarding in two measured
test wrappers was repaired. The selected native create repair ran 61 (60 passed,
one RP12 producer-wire expectation failed), with all original quota bootstrap
failures resolved and three native measured/direct quota routes passing. The
sole affected payload suite then passed 14/14, retaining its original 12 and
two explicit V1/V2 completed-consumer cases. The retained List golden remains
unchanged; the current producer's expected Vector wire is explicit separately.
Actual old-list native reads return Vector with physical bytes/rows/quota
unchanged. Genuine native SQL and write counts remain observed. Failed command
entries stay failed and these overlapping scopes are not summed.

Independent complete protected focused RCP-CONSUMERS Step review passed.
Phase 115 adoption is CLOSED with all five RCP obligations DONE, on the
[final Phase acceptance](../phase/phase-115.md#final-phase-acceptance--2026-10-18)
and [checklist](../phase/phase-115-checklist.md). Both connected Steps were
independently reviewed and committed locally; the sole original Phase full
review, retained Modeler repair1 focused closure and combined repair2–6 focused
closure passed. The selected CNCF full gate passed 6716 tests in 824 suites,
zero failed/aborted, with 5 canceled, 1 ignored and 46 pending. The Phase ledger
records all four selected full gates separately and retains earlier failures.
Closure takes effect through the local release commit containing
`phase115-final-closure-20261018-01@1`. The independent Phase69.7 obligations
below remain untouched. The original resource/numeric/Unicode bounds, constant refusal, exact concrete trust guards,
hostile zero-observation obligations and unrelated historical integration
requirements remain in force.

## HISTORICAL Phase 69.7 integration obligations — 2026-10-04

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

## HISTORICAL B1 selected execution and B2 authored companion — 2026-10-04

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

## HISTORICAL JM69-10B2 selected execution — 2026-10-04

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
