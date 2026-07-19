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
<field>__update_command=clear
<field>__update_command=null
```

`__update_command` accepts exactly `clear` or `null`. It does not accept a
dummy operand. The spellings `__update_clear`, `__clear`, and `__update_null`
are not CNCF parameter aliases.

## Typed Semantics

| Input | Effective generated update |
| --- | --- |
| field and carrier absent | no update |
| blank Form control for an update field | no update |
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

## Introspection And Form Contract

Help, describe, schema, OpenAPI, and Form projections advertise only commands
compatible with a parameter's generated source metadata. Generated Form
controls submit `<field>__update_command` only when the user selects a command;
they do not submit hidden dummy values.

## Executable Specifications

- `org.goldenport.cncf.http.OperationUpdateDirectiveNormalizerSpec`
- `org.goldenport.cncf.http.OperationTypedUpdateMapperSpec`
- `org.goldenport.cncf.http.OperationUpdateRequestNormalizerSpec`
- `org.goldenport.cncf.http.StaticFormAppRendererSpec`
- `org.goldenport.cncf.directive.UpdateCodecSpec`
- `org.goldenport.cncf.directive.UpdateSpec`

The downstream generated-code acceptance driver is
`textus-art-scene/scripts/acceptance/check-component-api-assembly.sh`.
