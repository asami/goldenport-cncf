# HTTP/Form Typed Update Parameters

Status: normative static contract

## Scope

This specification defines generic CNCF HTTP/Form parameter syntax for typed
Entity update Operations. It applies to URL-encoded Form, multipart Form,
automatic REST/query input, and JSON Record input. Direct Scala callers use
typed `Update` values and do not use these transport carriers.

## Parameter Grammar

For an update-bearing operation parameter named `<field>`, CNCF recognizes:

```text
<field>=<value>
<field>__overwrite=<value>
<field>__prepend=<value>
<field>__append=<value>
<field>__remove=<value>
<field>__value=<value>
<field>__value_or_clear=<value>
<field>__value_or_null=<value>
<field>__update_command=clear
<field>__update_command=null
```

`__update_command` accepts exactly `clear` or `null`. It does not accept a
dummy operand. The spellings `__update_clear`, `__clear`, and `__update_null`
are not CNCF parameter aliases.

`__value` is an explicit value carrier. Its presence means assignment even
when its operand is the zero-length string. `__value_or_clear` and
`__value_or_null` are adaptive value carriers: a zero-length string selects
the corresponding operand-less command, while a non-empty operand remains an
ordinary typed assignment. Whitespace is a value and is not a zero-length
operand.

## Typed Semantics

| Input | Effective generated update |
| --- | --- |
| field and carrier absent | no update |
| blank Form control for an update field | no update |
| blank `<field>__value` | assign the zero-length string |
| blank `<field>__value_or_clear` on a collection source field | `Update.set` with an empty typed collection |
| blank `<field>__value_or_null` on a nullable scalar source field | `Update.setNull` |
| non-empty explicit/adaptive value carrier | existing typed assignment path |
| plain or value-bearing input | existing typed value-bearing update path |
| `clear` on a collection source field | `Update.set` with an empty typed collection |
| `null` on a nullable scalar source field | `Update.setNull` |

Command compatibility is derived from the generated source-field multiplicity
and null-assignment metadata retained in `CmlOperationField.update`. Request
multiplicity does not establish source nullability because generated update
request fields are optional so omission can mean no update.

An ordinary JSON empty array remains an ordinary collection value. CNCF does
not reinterpret it as an operand-less command.

## JSON Body Ownership And Null Policy

The canonical REST JSON body adapter resolves the selected Operation's actual
Core `ParameterDefinition` collection. A parameter is raw JSON only when its
datatype is the canonical `XJson` object. Parameter names and declared aliases
use the existing normalized naming contract. A CML datatype string, a type's
leaf name, or an application type named `Json` does not establish raw ownership.

Scalar raw Json parameters retain their original JSON node, including present
`Json.Null` and arrays containing null positions. Multiple raw Json parameters
require a syntax array and retain its ordered children as `Vector[Json]`, with
all duplicates, null positions and exact number lexemes. Source multiplicity
comes from matching generated update metadata when present, otherwise from the
actual protocol parameter. Optional generated request multiplicity must not
hide a multiple source field; typed construction continues to validate required
and bounded cardinality.

Plain raw parameters and the existing explicit/adaptive/value-operation carriers
retain their original keys and raw values. `__update_command` stays ordinary
command text. The adapter preserves every carrier and conflict for the existing
request normalizer and typed mapper, which own command legality and final base
parameter binding.

Core Record JSON conversion owns nested ordinary semantic structures. It omits
nested null-valued semantic object properties and retains empty objects and
arrays. A null semantic sequence position fails conversion; it must not
disappear, become an empty patch, or fall back to an opaque body.

At the HTTP object root, the adapter preserves every original property, including
ordinary and undeclared fields whose explicit null value remains a singular
`io.circe.Json.Null` in the received Record. It preserves root spelling, order
and presence; an absent property stays absent. Nested semantic omission does not
remove root presence. The selected Operation still owns key and type validation.
This boundary follows [HTTP JSON Body Admission](http-json-body-admission.md).
Malformed and non-object JSON alone retain the existing opaque Bag fallback
behavior.

For `ENTITY_UPDATE`, JSON syntax null at an ordinary root assignment or a
command/value carrier is a structured argument-policy error with an
`explicit-clear-required` reason, returned as HTTP 400 before ActionCall or
mutation. It is never converted to a zero-length string or implicit SQL clear.
A true declared scalar raw Json plain/value `Json.Null` remains a document
assignment. A raw collection null root fails the required array shape; null
positions inside its array remain valid document values. SQL clearing requires
the existing `__update_command=null` carrier and its later metadata validation.
Only a zero-length String operand selects an adaptive clear/null command, with
the established explicit-value and whitespace rules above. Ordinary non-update
nested semantic object null properties continue to follow Core omission;
explicit null at the HTTP object root retains the presence contract above.

The adapter's failure result uses the existing HTTP error projection. Normal
method, If-Match/revision transport, header/context/authentication/session,
ActionCall/UnitOfWork execution and the DisplayMutation raw-body bypass retain
their existing ownership and behavior. Form contracts and command spellings are
unchanged.

## Conflict Rules

For one target parameter, a request contains at most one effective directive.
CNCF rejects:

- repeated `__update_command` occurrences;
- an unknown command value;
- `clear` on a non-collection source field;
- `null` on a source field without scalar null-assignment support;
- a command combined with plain assignment;
- a command combined with a value-bearing operator;
- a plain assignment combined with an explicit/adaptive value carrier;
- an explicit/adaptive value carrier combined with a value-bearing operator;
- more than one explicit/adaptive carrier kind;
- empty and non-empty occurrences of one adaptive carrier;
- repeated empty occurrences of one adaptive carrier;
- `value_or_clear` on a non-collection source field;
- `value_or_null` on a source field without nullable scalar support;
- more than one value-bearing operator; and
- a carrier whose base field is not a selected Operation parameter.

Validation is independent of field order and combines occurrences from all
request carrier positions. A conflict must not be resolved by selecting the
first or last occurrence.

## Error And Execution Contract

Invalid directives produce ordinary structured
`Consequence.Failure(Conclusion)` argument errors. HTTP dispatch projects them
as status 400. Compatible requests continue through the selected Operation and
the normal authorization, request-validation observability, ActionCall,
UnitOfWork, and persistence path.

### Operation Binding Contract

Transport carrier names are not Operation parameter names. Before the selected
Operation constructs its `OperationRequest`, CNCF MUST materialize every valid
explicit or adaptive carrier as its base parameter:

| Transport occurrence | Bound Operation parameter |
| --- | --- |
| `nickname__value=` | `nickname` is present with the zero-length string |
| `tags__value_or_clear=` | `tags` is present with an empty typed collection |
| `nickname__value_or_null=` | `nickname` is present with the generated explicit-null marker |
| `tags__value_or_clear=a&tags__value_or_clear=b` | `tags` is present with the ordered values `a`, `b` |

The `__value`, `__value_or_clear`, and `__value_or_null` names MUST NOT reach
the generated request binder or `ActionCall`. The base parameter MUST remain
present even when the effective value is the zero-length string, an empty
collection, or explicit null. This presence distinguishes assignment from
omission.

URL-encoded Form and multipart Form MUST preserve zero-length explicit carrier
occurrences until this materialization completes. Ordinary blank update
controls remain omitted. REST/query and JSON Record inputs MUST produce the
same bound parameter values for equivalent carrier occurrences.

Carrier conflicts and metadata incompatibility MUST fail before ActionCall
construction. Successfully materialized carriers MUST continue through normal
`ComponentLogic` request binding and reach the ActionCall as ordinary present,
typed arguments.

## Introspection And Form Contract

Help, describe, schema, OpenAPI, and Form projections advertise only commands
and adaptive value carriers compatible with a parameter's generated source
metadata. Every update field supports `value`; collection fields additionally
support `value_or_clear`, and nullable scalar fields additionally support
`value_or_null`. Generated Form controls submit `<field>__update_command` only
when the user selects a command; they do not submit hidden dummy values.

## Executable Specifications

- [OperationJsonBodyDecoderSpec](../../src/test/scala/org/goldenport/cncf/http/OperationJsonBodyDecoderSpec.scala) — selected focused validation 52/52
- [Phase115JsonBodyDispatchSpec](../../src/test/scala/org/goldenport/cncf/http/Phase115JsonBodyDispatchSpec.scala) — selected continuation 19/19
- `org.goldenport.cncf.http.OperationUpdateDirectiveNormalizerSpec`
- `org.goldenport.cncf.http.OperationTypedUpdateMapperSpec`
- `org.goldenport.cncf.http.OperationUpdateRequestNormalizerSpec`
- `org.goldenport.cncf.http.StaticFormAppRendererSpec`, including the
  HTTP-to-ActionCall carrier binding scenarios
- `org.goldenport.cncf.directive.UpdateCodecSpec`
- `org.goldenport.cncf.directive.UpdateSpec`

### Current Phase 115 adoption status — 2026-10-18

The original B2 and owner-validation commands each passed decoder 52/52 but
failed five HTTP oracle cases after the expected status 400; the existing error
projection was HTML, not JSON. Those commands remain failed. The selected owner
continuation passed 106/106 in nine suites: HTTP dispatch 19, actual generated
policy 15, six existing HTTP regression suites 71 and native quota witness 1.
Overlapping decoder runs are not summed. These results establish selected focused
validation of this unchanged contract. Independent complete protected focused
RCP-CONSUMERS Step review passed.
Phase 115 adoption is CLOSED with all five RCP obligations DONE, on the
[final Phase acceptance](../phase/phase-115.md#final-phase-acceptance--2026-10-18)
and [checklist](../phase/phase-115-checklist.md). Both connected Steps were
independently reviewed and committed locally; the sole original Phase full
review, retained Modeler repair1 focused closure and combined repair2–6 focused
closure passed. The selected CNCF full gate passed 6716 tests in 824 suites,
zero failed/aborted, with 5 canceled, 1 ignored and 46 pending. The Phase ledger
records all four selected full gates separately and retains earlier failures.
Closure takes effect through the local release commit containing
`phase115-final-closure-20261018-01@1`.
Earlier authoring-pending status is historical.

The downstream generated-code acceptance driver is
`textus-art-scene/scripts/acceptance/check-component-api-assembly.sh`.
