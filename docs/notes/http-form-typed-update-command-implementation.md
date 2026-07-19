# HTTP/Form Typed Update Command Implementation Note

status=implementation-planning
updated_at=2026-07-19

## 1. Role

This note turns the Jul. 19 HTTP/Form update-command handoff into an
implementation specification for Phase 40. It is non-normative. The public
parameter grammar becomes normative only after the implementation and its
end-to-end executable evidence are complete and the confirmed behavior is
promoted to `docs/design` and `docs/spec`.

Source record:

- `docs/journal/2026/07/2026-07-19-http-form-update-command-handoff.md`

Historical request-grammar notes remain useful audit input, but they do not
override the latest handoff or the behavior verified by Phase 40.

## 2. Problem Boundary

Generated update models already distinguish:

- no update;
- assignment of a typed value;
- assignment of a typed empty collection; and
- explicit null assignment.

The current HTTP/Form path loses operand-less update intent before generated
operation binding. A blank form value is intentionally interpreted by Form
mode as absent, so it cannot also mean clear. Applications must not add a
bespoke operation to recover a distinction already represented by generated
`Update` values.

Phase 40 owns the transport-to-operation normalization boundary. It does not
change direct Scala calls, entity persistence semantics, or the generated
`Update` model.

## 3. Provisional Public Grammar

The Phase 40 implementation target is:

```text
<field>=<value>
<field>__overwrite=<value>
<field>__prepend=<value>
<field>__append=<value>
<field>__remove=<value>
<field>__update_command=clear
<field>__update_command=null
```

Rules:

- plain assignment and the existing value-bearing operators keep their
  current behavior;
- `__update_command` carries only operand-less update commands;
- `clear` has no dummy value and means typed empty collection assignment;
- `null` has no dummy value and means explicit null assignment;
- an absent value and absent command remain no update; and
- a blank form value is not redefined as clear or null.

The older `field__update_clear=1`, `field__clear=1`, and general
`field__update_<operator>` proposals are not the preferred Phase 40 public
surface. Existing internal Record commands may be used behind the CNCF
normalizer, but an internal sentinel value must not enter generated help,
OpenAPI, Form definitions, or application documentation.

## 4. Normalization Model

Normalization must occur after transport decoding has preserved field
occurrences and before request values are reduced to exact operation
parameters.

```text
HTTP request
  -> transport field occurrences
  -> update-directive grouping
  -> operation-metadata validation
  -> normalized operation input
  -> generated request binding
  -> typed Update value
  -> normal ActionCall/persistence path
```

A plain `Record` is not sufficient as the first normalization input when it
has already collapsed duplicate keys. The normalizer must receive an ordered
or grouped multi-value representation so that repeated command values and
conflicts are rejected deterministically rather than resolved by request
order.

The normalizer should expose one framework-owned result per operation
parameter:

- `NoDirective`;
- `PlainAssignment(values)`;
- `ValueOperation(kind, values)`; or
- `OperandlessCommand(kind)`.

This is an ingress model, not a second public update domain model. It should
be translated immediately into the existing request/Record/generated binding
path after validation.

## 5. Parameter Metadata

Validation is based on the selected operation definition, not on naming
conventions alone. The normalizer needs these effective facts for each update
parameter:

- canonical parameter name;
- datatype;
- multiplicity or collection-valued status;
- whether explicit null assignment is supported;
- whether the parameter is an update-bearing generated field; and
- the element datatype used when creating an empty collection value.

Existing `ParameterDefinition`, generated operation metadata, and
`CmlOperationField` should be reused. If they cannot express null support or
update-bearing status, Phase 40 may add framework metadata, but must not infer
the answer from an application field name or add an ArtScene-specific API.

## 6. Typed Mapping

The required semantic mapping is:

| Effective directive | Generated update meaning |
| --- | --- |
| absent | `Update.noop` |
| plain/value-bearing assignment | existing typed `Update.set(value)` path |
| `clear` on repeated field | `Update.set(empty typed collection)` |
| `null` on nullable field | `Update.setNull` |

`clear` must not produce `Update.setNull`, and `null` must not produce an empty
collection. Direct Scala callers continue to construct `Update` values and do
not use transport command fields.

JSON arrays remain ordinary values. A JSON empty array must not be rewritten
into an operand-less command merely because it is empty. The explicit
`__update_command` carrier remains available to every supported generic
transport; Phase 40 must record the observed generated-binding behavior of a
plain JSON empty array before the final parameter specification is written.

## 7. Conflict and Validation Matrix

For one target parameter, exactly zero or one effective update directive is
allowed.

Reject as structured argument failures:

- multiple `__update_command` occurrences;
- an unknown command;
- `clear` for a non-collection parameter;
- `null` for a parameter without null-assignment support;
- command plus plain assignment;
- command plus any value-bearing operator;
- more than one value-bearing operator; and
- a directive whose base field is not an operation parameter.

Request ordering must not select a winner. Failures use ordinary
`Consequence.Failure(Conclusion)` with parameter/field-path, expected,
actual, and policy facets where applicable. They must not introduce an
application error-code model or parse display messages for classification.

## 8. Transport Coverage

One normalization service must be reusable from:

- `application/x-www-form-urlencoded` Form submission;
- multipart form fields;
- automatic REST query-style input where the operation permits it; and
- JSON Record input.

Transport adapters may differ in how they preserve occurrences, but they must
produce the same effective directive and the same structured failure for an
equivalent request. Generated Static Form applications and automatic REST must
not maintain separate update grammars.

## 9. Generated Form and Introspection Surface

Generated update forms must be able to submit an available operand-less
command without a dummy domain value. The operation/Form projection should
derive available commands from the same effective parameter metadata used by
the normalizer.

The first implementation may use a dedicated clear/null action control or a
bounded command selector. In either case, the submitted field is exactly
`<field>__update_command` with `clear` or `null`; application templates must
not synthesize `=1` aliases.

Form definition, help, and API/schema projection must expose only commands
that are valid for the parameter. The exact additive metadata shape is settled
by implementation and recorded at the documentation promotion gate. This
slice does not require a general update-expression UI.

## 10. Implementation Slices

1. Audit the exact form, multipart, REST, and JSON decoding paths and identify
   where duplicate occurrences are currently lost.
2. Add the transport-neutral update-directive parser and conflict validator.
3. Add operation-metadata validation and typed mapping for repeated clear and
   nullable scalar null.
4. Integrate the normalizer into the shared operation request-construction
   boundary used by Form and automatic REST.
5. Project available commands into generated Form definitions and render a
   no-dummy-value clear/null control where the metadata permits it.
6. Preserve current value-bearing operator and blank Form behavior.
7. Add HTTP-level structured 400 evidence and prove normal ActionCall,
   authorization, observability, and persistence paths remain in use.
8. Run the ArtScene `Facility.fetch_methods` clear scenario through generated
   automatic REST.
9. Promote only the behavior proven by implementation and integration evidence
   to the canonical parameter design and static specification.

## 11. Executable Evidence

Required CNCF evidence includes:

- repeated powertype clear maps to a typed empty collection update;
- optional scalar null maps to `Update.setNull`;
- omitted and blank Form input remain no-op under existing Form behavior;
- append, prepend, remove, and overwrite retain their existing semantics;
- duplicate, unknown, incompatible, and conflicting directives return
  structured HTTP 400 responses independent of field order;
- URL-encoded, multipart, REST, and JSON paths share equivalent semantics;
- generated Form definitions advertise only compatible operand-less commands
  and generated controls submit the canonical command carrier without a dummy
  value;
- direct Scala `Update` calls are unchanged; and
- operation authorization and request-validation observability still execute
  through the normal ActionCall boundary.

The downstream acceptance is an ArtScene generated-REST smoke proving that
clearing `Facility.fetch_methods` removes the stored override and restores the
application default policy without a bespoke ArtScene operation.

## 12. Documentation Promotion Gate

The provisional parameter grammar in this note must not be copied into a
normative design/spec as an assumed contract. After the CNCF executable specs
and ArtScene smoke pass:

1. compare the observed behavior with this note and the April grammar notes;
2. settle canonical names, aliases, duplicate handling, JSON empty-array
   behavior, null support metadata, and error projection;
3. update or add the canonical request-parameter design document;
4. update or add the static parameter specification with links to executable
   specs; and
5. annotate historical journal/notes where their proposed grammar differs from
   the confirmed contract rather than rewriting their original text.

Phase 40 cannot close before this promotion is complete.
