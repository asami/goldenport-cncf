# HTTP JSON Body Admission

Status: normative static contract

## Scope

This contract defines foreign HTTP object-root JSON admission into the selected
Operation's parameter Record. The adapter is
[`OperationJsonBodyDecoder`](../../src/main/scala/org/goldenport/cncf/http/OperationJsonBodyDecoder.scala).
It supplies syntax-presence evidence for later Operation validation.

## R1. Root Presence

After raw parameter selection and the Entity update null policy, the adapter
MUST preserve every original root property, its spelling and its order. An
ordinary or unknown root property whose JSON value is null MUST remain a
singular `io.circe.Json.Null` value in the Record. An absent property MUST remain
absent. The adapter MUST NOT insert Scala null.

Root reconstruction selects the declared raw value first, otherwise the
original root null node, otherwise the Core-normalized ordinary value. This
policy is confined to the HTTP object root and does not change Record's core
semantics.

## R2. Raw And Semantic Ownership

A canonical `XJson` parameter retains its original document node. Its nested
nulls, arrays, duplicate sequence positions and number lexemes remain raw.
Multiple raw parameters retain the existing syntax-array requirement and
ordered `Vector[Json]` children.

Core Record conversion owns nested ordinary semantic structures. Nested null
object properties continue to be omitted; empty objects and arrays remain
values. A null position in an ordinary semantic sequence continues to fail
conversion. A typed admission failure MUST NOT become an opaque-body fallback.
The existing fallback for malformed or non-object JSON remains unchanged.

## R3. Entity Update Policy

`ENTITY_UPDATE` continues to reject ordinary root assignments and command/value
carriers set to JSON null with the structured `explicit-clear-required` reason
before semantic construction. Declared scalar raw `XJson` null remains a
document assignment. Raw collection roots still require a syntax array.

`Json.Null` is document data; it is never an implicit SQL NULL or `Update.SetNull`
instruction. Explicit clearing and carrier conflicts follow the existing
[HTTP/Form Typed Update Parameters contract](http-form-typed-update-parameters.md).
That contract's ordinary omission rule continues to govern nested semantic
object properties; R1 defines the selected HTTP object-root presence boundary.

## R4. Operation Validation

Body admission MUST leave unknown root keys and invalid ordinary null values
visible to the selected Operation's existing key and type validation. Presence
does not authorize a key or make a null valid for its declared datatype. An
ordinary integer null, for example, remains present and is rejected by the
existing integer `ValueReader`. No application packet names or allowed-key
policy belong in this adapter.

## Executable Specification

[`OperationJsonBodyDecoderSpec`](../../src/test/scala/org/goldenport/cncf/http/OperationJsonBodyDecoderSpec.scala)
contains the connected scenarios:

- E1: root null presence with Core-owned nested omission and empty structures.
- E2: an absent ordinary parameter versus an explicit root `Json.Null`.
- E3: an absent unknown key versus a visible explicit null key in original order.
- E4: a retained integer null rejected by the actual typed reader, with absent
  and valid integer controls.
- E5: independent names, nonempty disjoint keys, null presence, ordinary values
  and varying order in a shrinking property requiring at least 100 successes
  and zero discards; raw children remain unchanged and no Scala null is added.

The existing raw-document, multiplicity, Entity update refusal, carrier-conflict
and semantic-sequence specifications continue to define their respective
boundaries.
