# HTTP/Form Update Command Handoff (2026-07-19)

## Position of This Record

This journal entry records an implementation handoff. It is not yet a
normative CNCF contract. Promote the accepted grammar and behavior to CNCF
design/specification documents after implementation and executable
specifications settle the details.

> Post-implementation annotation (2026-07-19): Phase 40 is complete. The
> canonical contract is `docs/spec/http-form-typed-update-parameters.md`, with
> design context in `docs/design/web-operation-dispatcher.md`. This handoff
> remains the historical implementation input and does not override them.

## Problem

ArtScene needs to restore a repeated facility attribute to its default
behavior by clearing the stored value:

```cml
| fetch_methods | FetchMethod | * |
```

The generated Scala update model already represents the required distinction:

```scala
Update.noop
Update.set(Vector.empty)
Update.set(values)
```

The HTTP/Form boundary currently loses that distinction. Sending an empty
array or an empty form value is treated as absent/no-op, so ArtScene cannot
clear a previously stored `fetch_methods` value through generated automatic
REST.

The earlier public grammar proposal used dummy-valued command fields such as:

```text
fetch_methods__update_clear=1
```

The `=1` value has no domain meaning. It exists only to make an operand-less
command look like a normal form field. This is not suitable as the preferred
public interface.

## Required Public Contract

Keep the existing value-bearing update operators. Introduce one explicit
command field only for update operations that do not carry an operand.

### Plain assignment

```text
name=New Name
```

Plain empty form input keeps the existing Form parsing semantics. In
particular, `fetch_methods=` must not implicitly mean clear.

### Value-bearing operators

Existing operator forms remain available and retain their current meaning:

```text
tags__overwrite=A,B,C
tags__prepend=A,B,C
tags__append=A,B,C
tags__remove=A,B,C
```

The implementation must not require a separate mode parameter for these
operations. The operator suffix already expresses the operation and its field
value carries the operand.

### Operand-less commands

Use this public shape:

```text
<field>__update_command=<command>
```

Initial commands:

```text
fetch_methods__update_command=clear
memo__update_command=null
```

Semantics:

- `clear`: clear a collection by producing a typed empty collection update;
- `null`: explicitly assign null to an attribute that supports null;
- absent field and absent command: no update.

`__update_command` is a command carrier, not an update mode. It is needed only
when the operation has no value operand. Do not introduce a general
`<field>__update=<mode>` parameter that must accompany ordinary update fields.

## Typed Update Mapping

The HTTP/Form grammar must be translated into generated typed update values
before persistence.

For a repeated attribute:

```text
fetch_methods__update_command=clear
```

must become the equivalent of:

```scala
Update.set(Vector.empty[FetchMethod])
```

For an optional scalar attribute:

```text
memo__update_command=null
```

must become:

```scala
Update.setNull
```

Existing value-bearing forms continue to map to the corresponding typed
operation. Direct Scala operation calls remain unchanged and continue to use
`Update` directly; they have no HTTP empty-value ambiguity.

## Validation Rules

The request boundary must reject invalid combinations rather than silently
choosing one interpretation.

- Only one `__update_command` value is allowed per field.
- An unknown command returns HTTP 400 with a structured parameter error.
- A command incompatible with the generated attribute type returns HTTP 400.
- `clear` is valid for collection-valued update attributes.
- `null` is valid only where null assignment is supported.
- A field must not combine `__update_command` with `__append`, `__prepend`,
  `__remove`, `__overwrite`, or a plain assignment in the same request.
- Conflicting operations return HTTP 400; request ordering must not determine
  the result.

The same logical grammar should be applied consistently to URL-encoded forms,
multipart form fields, query-style operation input where supported, and JSON
Record input. JSON arrays remain normal value payloads; they do not replace
the explicit command needed to distinguish clear from omitted input in generic
operation binding.

## Existing Internal Compatibility

The Record layer already has internal command forms such as:

```text
field__clear
field__null
field__append
field__prepend
field__remove
field__overwrite
```

CNCF may normalize the new public command carrier into the existing internal
commands:

| Public request | Internal Record command |
| --- | --- |
| `field__update_command=clear` | `field__clear` |
| `field__update_command=null` | `field__null` |

Value-bearing operator keys can continue through the existing path unchanged.
The internal dummy value, if required by a legacy Record API, must not leak
back into the public HTTP/Form contract.

The older proposed aliases such as `field__update_clear=1` may be retained
temporarily for compatibility, but they should not be documented as the
preferred CNCF application interface.

## Confirmed Integration Gap

The concepts already exist below the HTTP layer, but current automatic REST
does not connect them end to end:

- `goldenport-record` has `PropertyOperation` and `ClearCommand` support;
- generated entity updates use typed `Update` values;
- current `Http4sHttpServer` request parsing creates a plain `Record`;
- current REST ingress maps exact operation parameter names and does not
  associate `<field>__update_command` with the generated `<field>` update
  parameter.

Live ArtScene checks confirmed that both of these requests currently become
no-op updates:

```text
fetch_methods__clear=1
fetch_methods__update_clear=1
```

The fix therefore belongs in CNCF request normalization/REST ingress and, if
needed, generated operation binding. ArtScene must not add a bespoke command
only to clear a generated repeated entity field.

## Suggested Implementation Boundary

1. Parse the incoming request into a Record without discarding operator keys.
2. Group plain fields, value-bearing operator fields, and
   `<field>__update_command` by generated operation parameter.
3. Validate conflicts and command/type compatibility against operation
   metadata.
4. Build the generated typed `Update` value.
5. Invoke the normal generated entity update and persistence path.

Do not infer clear from a blank HTML form value. Do not require applications to
know `PropertyOperation` implementation details.

## ArtScene Reproduction

ArtScene currently has facilities with an explicit old method order:

```text
ai_web_tools,museum_or_jp
```

The desired operation is to remove that override so the application uses its
default order:

```text
official_driver,ai_web_tools,museum_or_jp
```

The intended automatic REST request is:

```http
POST /rest/v1/art-scene/entity/update-facility-record
Content-Type: application/x-www-form-urlencoded

id=<facility-id>&fetch_methods__update_command=clear
```

After the CNCF fix, generated facility search/load must show no explicit
`fetch_methods`, and ArtScene must use its application default fetch policy.

## Acceptance Criteria

- `field__update_command=clear` clears a generated repeated entity attribute.
- The generated Scala value is `Update.set(empty collection)`, not
  `Update.noop` or `Update.setNull`.
- `field__update_command=null` maps to `Update.setNull` only for compatible
  attributes.
- Existing `field__prepend=A,B,C`, `field__append=A,B,C`,
  `field__remove=A,B,C`, and `field__overwrite=A,B,C` requests keep working.
- Plain `field=` retains Form empty-input behavior and is not redefined as
  clear.
- Invalid or conflicting directives produce a structured HTTP 400 response.
- Generated automatic REST and ordinary generated Form applications use the
  same request grammar.
- Direct Scala calls continue to use typed `Update` without transport-specific
  command fields.
- ArtScene can clear `Facility.fetch_methods` through generated automatic REST
  without a bespoke ArtScene operation.
- Executable CNCF coverage includes repeated powertype attributes and optional
  scalar null assignment.

## Related Records

- `docs/journal/2026/04/query-update-request-translation-rule-note.md`
- `docs/journal/2026/04/query-update-unified-request-grammar-proposal.md`
- `docs/journal/2026/04/record-update-command-and-null-semantics-note.md`
- `docs/journal/2026/04/record-v3-http-form-path-notation-note.md`
