# Display Model protocol v1

Document role: normative physical read protocol contract.
Status: P96-04A-WIRE pure codec accepted at local Step commit `625d533`, after
151/151 focused tests and independent whole-Step review. P96-05 service/typed
adapter execution and independent acceptance are complete in the
[Step acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance);
its local Step commit remains PREPARED/PENDING and later Phase gates remain pending.
This contract promotes only the first List/Detail read subset of the earlier proposal.

The [design](../design/display-model-protocol.md) records ownership and rationale.
[Generated ABI](logical-ui-generated-abi.md) and
[runtime semantics](display-model-runtime.md) remain independent authorities.
Samples and DSP-WIRE-01 provenance belong to the
[protocol journal](../journal/2026/10/2026-10-04-phase-96-protocol-contract.md).

## R1: Scope and explicit source

`DisplayProtocol.schema` is `cncf.display-model.v1`, `version` is integer `1`
and `mediaType` is `application/json`. `Source.createC(componentId,
resourceType)` admits a nonnull typed ComponentId and a nonnull, nonempty,
exactly trimmed resource type. Source has a private constructor, immutable
public members and no copy bypass. The application owns this registered logical
resource type; Source is not authorization proof. Serialize `componentId.name`.

`DisplayProtocolCodec.encodeJsonC(source, display): Consequence[io.circe.Json]`
purely encodes one admitted Display as the business object. `encodeC(source,
display): Consequence[String]` delegates to the same guarded typed payload and
the unchanged canonical text renderer. `decodeC(expectedSource, definitions, json):
Consequence[Display]` restores it against already-admitted trusted definitions.
Received metadata, source IDs, provenance and actions cannot register anything,
select a View/converter, reissue admission, authorize or execute an operation.
The codec performs no source query, callback, I/O or mutation.

## R2: Independent metadata and exact compatibility

Root members in canonical order are `schema`, `version`, `componentId`,
`resourceType`, `abi`, `semanticContract`, `provenance`, `definition`, `model`.
All objects have exact member sets. Nested metadata is:

| Object | Ordered members |
| --- | --- |
| abi | identity, version |
| semanticContract | schema, version, semanticAuthoritySchema, assurance, driver |
| driver | repository, revision, path, scenarioId |
| provenance | producerId, sourceId, revision |
| definition | screenId, revision |

ABI and the complete Phase 74 descriptor equal the selected trusted definition
exactly. Provenance is its original producer/source/revision, never normalized.
`definition.revision` maps `definitionRevision`. Definition and model screen IDs
must agree. Do not expose `viewBindingId`. Protocol, ABI, semantic contract,
definition, provenance, Entity revision and query snapshot retain separate
meanings. No context version or member is admitted in this read subset.
Version members are integer-valued JSON numbers equal to supported `1`; no
Boolean, string, range or SemVer inference is accepted.

## R3: Closed model and precise values

| Object | Ordered members (question mark means optional) |
| --- | --- |
| List | type, screenId, title, items, detailTarget, actions, snapshot? |
| Detail | type, screenId, title, resource, sections, actions |
| detailTarget | screenId |
| resource | entityId, revision |
| listItem | resource, fields, actions |
| section | id, title?, fields |
| field | id, label, role, value |
| action | id, label, bindingId, availability |
| snapshot | reference |

Model tags are `list` and `detail`. Role tags are `content`, `title`, `subtitle`,
`description`, `status`; availability tags are `hidden`, `disabled`, `enabled`.
Required arrays are present, including empty arrays, and preserve supplied
order. IDs remain scoped; DetailTarget is a logical screen ID, never a URL.
Only section title and List snapshot may be absent for None. Null is never a
present member or array entry. Existing Display semantic validation is unchanged.

| Value | Exact ordered members and payload |
| --- | --- |
| text | type, text: Java String, every UTF-16 code unit preserved |
| number | type, number: STRING equal to underlying java.math.BigDecimal.toString |
| boolean | type, boolean: JSON Boolean |
| timestamp | type, instant: STRING equal to Instant.toString |
| missing | type only, explicitly missing |

Numbers retain arbitrary coefficient precision and Int scale, including signed
scale, trailing zeros and extreme scales. Parse sign/mantissa digits as an
unscaled BigInteger, compute fraction length minus signed exponent in BigInt,
check Int range, construct java.math.BigDecimal(unscaled, scale), and require
toString equality. This explicit algorithm covers the full admitted scale domain,
including positive exponent 2147483648 at scale Int.MIN_VALUE, without assuming
platform parser limits. No float, MathContext,
rounding, stripTrailingZeros or enormous plain-text expansion is permitted.
Timestamp parsing requires canonical toString equality and retains nanos and
negative epoch. Text is never trimmed, normalized or coerced.

EntityId is the exact existing canonical opaque `value`: use existing
EntityId.parse and require value equality, with no local identity grammar.
EntityRevision is a decimal STRING in 1..Long.MAX_VALUE: existing parseC/createC
and canonical equality reject signs, leading zeros, whitespace, fractions,
exponents and JSON numbers.

## R4: Canonical JSON

Encode fixed member order from R2/R3, without spaces or trailing newline.
Printable ASCII is literal except escaped quote/backslash. Use named JSON
escapes for backspace, formfeed, newline, return and tab. Escape every other
control and every non-ASCII UTF-16 unit as lowercase four-hex unicode. Preserve
both units of pairs and lone surrogate units. Do not apply locale or Record
formatting. Each golden is exact encoder text plus one LF; comparisons remove
only that explicitly required LF, never generic trim.

## R5: Strict sequential admission

Use existing Circe JawnParser with `allowDuplicateKeys=false`; do not add a JSON
parser. Reject syntax errors, duplicate members at every depth, nonobjects,
unknown/missing members, nulls and wrong primitives. Input key order and
insignificant whitespace may vary; encoding recanonicalizes it.

Admission precedence is input guards, strict JSON, root member shape, protocol,
source, ABI, complete semantic descriptor, definition selection/revision,
provenance, closed model/typed resources/values, screen agreement, then unchanged
Display.createC. Structural traversal follows canonical members and array
indices. Compatibility fails at its first differing canonical field. This
pure parsing sequence does not change ordinary applicative semantic request
validation. No input Definition is created or admitted and no partial model is
returned. Display.createC failures pass through with their original typed
DSP96_* diagnostic and Consequence.

## R6: Structured redacted diagnostics

`Diagnostic(code, path, reason)` is carried by `CodecException`, returned with
`Consequence.fail(Taxonomy.argumentInvalid, carrier)`, never thrown for validation.
Reasons are fixed and redacted, with no input/parser/exception details. Wire
paths start at `$`; API input paths name the argument. The closed code prefix is
`DSP96_WIRE_`:

| Suffix | Path |
| --- | --- |
| INPUT_INVALID | source, expectedSource, definitions, json, display, componentId or resourceType |
| JSON_INVALID | $ for syntax, duplicates or nonobject |
| MEMBER_INVALID | object for wrong member set; exact member/index for null or wrong primitive |
| PROTOCOL_INCOMPATIBLE | $.schema or $.version |
| SOURCE_MISMATCH | $.componentId or $.resourceType |
| ABI_INCOMPATIBLE | first differing $.abi member |
| CONTRACT_INCOMPATIBLE | first differing semanticContract/driver member |
| DEFINITION_MISMATCH | definition/provenance member or model.screenId |
| MODEL_KIND_INVALID | model.type |
| VALUE_KIND_INVALID | value.type |
| ROLE_INVALID | field.role |
| AVAILABILITY_INVALID | action.availability |
| RESOURCE_INVALID | resource.entityId or resource.revision |
| VALUE_INVALID | number or instant payload |

No uncaught NonFatal validation exception or string-only failure is permitted.
Wrong primitive shape uses MEMBER_INVALID; parse/canonical scalar defects use
the corresponding resource/value code.

## R7: Existing REST binding and later execution proof

The payload is the OBJECT `data` of the successful synchronous existing CNCF
envelope, not a quoted JSON string. Codec APIs own only this business object,
not `data`/`execution` or optional framework `job`/`continuation`/`debug` members.
Accepted-job `data=null` is not a Display payload. Structured failures retain
ordinary CNCF HTTP/error mapping.

GET `/rest/v1/{component}/display/query.json` binds `resourceType`, `screenId`;
GET `/rest/v1/{component}/display/read.json` additionally binds `resourceId`.
Canonical selectors are `component.display.query/read`, with no list/get alias
or new namespace. [Path resolution](path-resolution.md) and
[output format](output-format.md) govern selection. Transport is application/json
and UTF-8; machine callers explicitly select .json or the existing format
property and `textus.output.shape=envelope`. P96-05 owns normal registered
Operations and formatter/adaptor proof that data stays an object. P96-04 proves
pure payload behavior only and makes no live HTTP claim.

The existing formatter derives a recognized `json`, `yaml`, `xml` or `text`
suffix from the final path segment of retained `Request.Source.Http`. Explicit
request format properties take precedence; a recognized HTTP suffix precedes
configured format and mode fallback. An absent or unsupported suffix preserves
that fallback, and non-HTTP selection remains unchanged. This corresponds to
output-format General Rules 6/7 and path-resolution R4 without changing canonical
Operation identity.

## R8: DSP-WIRE-01 and later owners

DSP-WIRE-01 carries the strict v1 read-only List/Detail subset, exact source and
producer provenance, compatibility rules, two deterministic actual-generated
goldens and focused evidence only after execution. TFC owns transport/runtime,
TFAF visual realization, Editing Studio Phase 2 application semantics and app
Phase 5 real-server aggregate/E2E acceptance. P96-06/08/10 retain mutation,
Business Operation descriptors and context artifacts, requiring explicit future
schema/version decisions. No silent additive v1 tolerance, editable fields,
constraints, capabilities, mutation results, executable actions, Experiment or
Flutter objects are admitted. P96-11 retains final handoff and Phase closure.

## R9: Pure typed JSON and framework envelope adapter

`encodeJsonC` shares the exact existing source/display null guards and private
payload builder with `encodeC`; the latter maps only the canonical renderer.
No second encoder, schema, decode/re-admission, callback, source query or generic
Record/Any projection is introduced. Existing v1 canonical bytes and diagnostics
remain unchanged. [Runtime SR1..SR4](display-model-runtime.md#p96-05a-ordinary-component-scoped-read-service)
own ordinary registered read/query execution returning `OperationResponse.Json`.

Only resolved format `json` plus shape `envelope`, or existing inline debug,
selects the typed Json adapter. The ordinary formatter builds existing
execution/job/continuation/debug metadata with interface-shape `json`, using
existing framework helpers and placeholder `data=null`. Encode and parse this
trusted framework-only Record once, replace only `data` with the actual business
Json (or Json.Null for explicit AcceptedJob), and render with
`Printer.noSpaces.copy(escapeNonAscii=true)`. Business data is never a Scalar
containing JSON text, locale-transformed Record, or custom Http response.

Failure to parse generated framework metadata is an unrecoverable internal
formatting defect with fixed redacted IllegalStateException, not user-input
rejection or a successful fallback. Preserve ordinary optional-root semantics:
direct/job-result data remains exact; accepted-job data is null; only explicit
runtime job/continuation/debug metadata creates those roots. Data payload members
cannot impersonate framework roots. Existing HTTP failure mapping is unchanged.

Typed Json data-only and every non-json selection retain existing behavior.
Explicit request format precedes the retained HTTP final-path suffix, which
precedes configured/mode fallback. No YAML/XML Display envelope converter
is admitted. The machine contract remains `.json` or explicit JSON format plus
`textus.output.shape=envelope`. JSON adapter escaping preserves each UTF16 code
unit, including surrogate pairs/lone units, through UTF8 body bytes, without
normalization, rounding, field renaming or locale conversion. Required arrays and
optional omission retain R2/R3 semantics. This extends no v1 business member set.

## P96-05A executable correspondence

| Suite/examples | Rules | Required observable proof |
| --- | --- | --- |
| [DisplayServiceReadSpec](../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadSpec.scala) E2/E3 | R3/R4/R7/R9, runtime SR3 | Actual normal Http4s route body, object data, precise values, empty/reversed order and exact UTF8/UTF16 |
| Read E8 | R1/R4/R6/R7/R9 | Typed/text payload equivalence, original API guards and canonical operations |
| [DisplayServiceReadPropertySpec](../../src/test/scala/org/goldenport/cncf/display/DisplayServiceReadPropertySpec.scala) E1 | R1..R4/R7/R9 | >=100 successful zero-discard public HTTP/trusted-decode cases |
| [OperationResponseJsonEnvelopeSpec](../../src/test/scala/org/goldenport/cncf/protocol/OperationResponseJsonEnvelopeSpec.scala) E1 | R7/R9 | Arbitrary Json kinds, raw data/non-json and existing Record/Scalar/Http compatibility |
| Envelope E2 | R7/R9 | Accepted-job null, job-result object, continuation and inline-debug ownership/presence |
| Envelope E3 | R3/R4/R7/R9 | Nested controls/Unicode/lone surrogates, exact opaque values, UTF8 bytes and no locale conversion |
| Envelope E4 | R7/R9, output-format 6/7, path-resolution R4 | Real formatter HTTP suffix precedence across all modes, explicit aliases/last-wins/invalid selection, Record formats and configured/mode/non-HTTP fallback compatibility |

All are authored Given/When/Then specifications with visible suite-scoped E
metadata, not execution receipts. The
[read-service acceptance entry](../journal/2026/10/2026-10-05-phase-96-display-read-service.md#p96-05-step-acceptance)
owns actual receipts, samples/traces and independent P96-05 public-service/adapter
acceptance. It records current subject13 14/14 in two suites with normal
generation and production/Test compilation, separately from retained subject12
266/266 in 32 suites; the accumulator was not rerun on renamed source. Whole-Step
review and fresh independent focused PASS resolved the sole naming blocker and
were admitted in ledger revision 28. P96-05 is CLOSED as the PREPARED/PENDING
local Step commit candidate. Pure P96-04 acceptance retains its independent
history; later mutation/context, Phase and separate Cozy/app gates remain pending.
Actual in-process Http4s routing is not socket/live-server E2E acceptance.

## Executable correspondence

The [example suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecSpec.scala)
and [property suite](../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecPropertySpec.scala)
bind this document through afterWord spec/example/rules/phase metadata and
adjacent Given/When/Then with should expectations.

| Example | Governing rules | Observable contract |
| --- | --- | --- |
| E1 | R1,R2,R3,R4,R8 | Actual generated bootstrap/query/read, two exact goldens, trusted restore and idle sources |
| E2 | R1,R2,R3,R4 | Independent all-kind typed fixture, exact scalar representation |
| E3 | R2,R3,R4 | Order, scoped reuse, empty containers and optional absence |
| E4 | R2,R5,R6 | Every independent protocol/ABI/semantic compatibility dimension |
| E5 | R1,R5,R6 | Strict syntax, duplicates, every nested member shape and null API guard |
| E6 | R1,R2,R3,R5,R6 | Source, definition, provenance, closed tags and screen mismatch |
| E7 | R3,R5,R6 | Canonical resources and unchanged Display semantic diagnostics |
| E8 | R3,R4,R5,R6 | Recanonicalization, scalar limits and noncanonical rejection |
| Property E1 | R1,R2,R3,R5 | Valid typed List/Detail round trips |
| Property E2 | R3,R4,R5 | Canonical idempotence, UTF-16 and coefficient/scale preservation |
| Property E3 | R1,R2,R3,R5,R6 | One invalid member fails closed without authority/effects |

Each property requires at least 100 successes, zero discarded cases, and a
passed Test.check result. Authoring is not execution or Step acceptance.
