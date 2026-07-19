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

- `org.goldenport.cncf.http.OperationUpdateDirectiveNormalizerSpec`
- `org.goldenport.cncf.http.OperationTypedUpdateMapperSpec`
- `org.goldenport.cncf.http.OperationUpdateRequestNormalizerSpec`
- `org.goldenport.cncf.http.StaticFormAppRendererSpec`, including the
  HTTP-to-ActionCall carrier binding scenarios
- `org.goldenport.cncf.directive.UpdateCodecSpec`
- `org.goldenport.cncf.directive.UpdateSpec`

The downstream generated-code acceptance driver is
`textus-art-scene/scripts/acceptance/check-component-api-assembly.sh`.
