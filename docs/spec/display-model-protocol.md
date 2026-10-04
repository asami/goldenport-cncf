# Display Model protocol v1

Document role: normative physical read protocol contract.
Status: accepted for implementation in PHASE-96 / P96-04A-WIRE; executable
validation and independent acceptance are pending. This contract promotes only
the first List/Detail read subset of the earlier proposal.

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

`DisplayProtocolCodec.encodeC(source, display): Consequence[String]` encodes
one admitted Display. `decodeC(expectedSource, definitions, json):
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
