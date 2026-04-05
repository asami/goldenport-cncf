Record v3 HTTP/Form Path Notation Note (Journal)
================================================

status=journal
published_at=2026-04-06
category=web / form / record

Overview
--------

This note extracts the HTTP/Form path notation used by Record v3 and
summarizes it as a reusable specification for CNCF web and form handling.

The implementation origin is:

- `goldenport-record`
  - `org.goldenport.record.parser.RecordParser`
  - `org.goldenport.record.v3.Record`
  - `org.goldenport.record.v3.HttpPart`
- `goldenport-scala-library`
  - `org.goldenport.values.ParameterKey`

The goal is not to copy internal implementation detail verbatim.
The goal is to identify the stable notation that CNCF can rely on for:

- HTML form field names
- HTTP query parameter names
- nested record submission
- sequence submission
- query-expression adornment


1. Functional Scope
-------------------

The notation supports four concerns:

1. nested object structure
2. sequence structure
3. whole-record sequence structure
4. query adornment on a parameter key

These concerns are orthogonal but can appear in the same request model.


2. Base Path Syntax
-------------------

The base path syntax is dot-separated.

Example:

- `user.name`
- `user.address.city`

Implementation basis:

- `ParameterKey.PATH_DELIMITER = "."`
- `Strings.totokens(path, ".")`

Meaning:

- `user.address.city`
  denotes nested property access
  `user -> address -> city`

For already-normalized `Record` access, this aligns with:

- `PathName`
- `Record.getField(path)`
- `Record.getValue(path)`
- `Record.getString(path)`


3. Adornment Syntax
-------------------

A parameter key may carry an adornment suffix.

Delimiter:

- `__`

Example:

- `name__query_equal`
- `address.city__query_like`

Implementation basis:

- `ParameterKey.ADORNMENT_DELIMITER = "__"`
- `ParameterKey.ADORNMENT_IN_DELIMITER = "_"`

Parsing model:

Given:

- `address.city__query_like`

`ParameterKey.parse` yields:

- `origin = "address.city__query_like"`
- `key = "address"`
- `path = "address.city"`
- `pathList = List("address", "city")`
- `adornment = Some("query")`
- `adornmentArguments = List("like")`

Interpretation:

- the left side of `__` is the structural path
- the right side is an adornment command plus optional underscore-separated arguments


4. Query Adornment
------------------

Currently the important adornment is:

- `query`

Example:

- `name__query_equal=Alice`
- `age__query_greater=20`
- `title__query_like=abc%`

Implementation basis:

- `QueryExpression.ADORNMENT_QUERY = "query"`
- `QueryExpression.activate`
- `QueryExpression.parse`

Behavior:

- if adornment is `query`, the field value is converted into a `QueryExpression`
- adornment arguments select the operator
- if no explicit operator argument is given, value-based query defaults are used

CNCF implication:

- this notation is suitable for form/query-driven search APIs
- it allows a flat HTTP parameter map to carry structured query semantics


5. Nested Record Submission
---------------------------

Record v3 can build nested objects from flat HTTP/Form keys.

Example:

- `user.name=Alice`
- `user.email=alice@example.com`

After `Record.createHttp*(...).http.request.build`, the logical structure is:

- `user`
  - `name = Alice`
  - `email = alice@example.com`

Implementation basis:

- `Record.createHttpArray`
- `HttpPart.http.request.normalize`
- `HttpPart.http.request.build`
- `Record.build`

The build logic tokenizes field names via `PathName` and groups child slots under
common prefixes.


6. Property Sequence Submission
-------------------------------

The notation supports indexed property groups.

Example:

- `items__1.name=Book`
- `items__1.price=1000`
- `items__2.name=Pen`
- `items__2.price=100`

Logical meaning:

- `items` is a sequence
- element `1` is `{ name, price }`
- element `2` is `{ name, price }`

Implementation basis:

- `Record.build`
- `_regex = "(.+)?__([^_]+)"`
- `SequenceSlot(index, value)`

Important point:

- the suffix after `__` is interpreted as an index-like sequence discriminator
- when the discriminator is numeric, whole-sequence ordering is normalized numerically

CNCF implication:

- repeated form groups can be transmitted without JSON payloads
- plain HTML forms can submit simple repeated structures


7. Whole Record Sequence Submission
-----------------------------------

The notation also supports sequence roots without an outer property name.

Example:

- `__1.name=Alice`
- `__1.email=alice@example.com`
- `__2.name=Bob`
- `__2.email=bob@example.com`

Logical meaning:

- the entire request body denotes a sequence of records

Implementation basis:

- `Record.build`
- comment block in source explicitly documents:
  - `__1/x`
  - `__2/x`

Output model:

- `Record.build` may return `Left(NonEmptyVector[Record])`
  instead of a single `Record`

CNCF implication:

- a flat form/query submission can represent batch-like record input
- if CNCF adopts this form, the operation boundary must explicitly decide whether
  such multi-record input is accepted


8. Ordering Semantics
---------------------

For whole-sequence notation, numeric indices are sorted numerically.

Examples:

- `__2`
- `__10`
- `__1`

are normalized as:

1. `__1`
2. `__2`
3. `__10`

Implementation basis:

- `Record.ZZZZ._normalize_whole`
- leading `_` is stripped before integer parsing
- non-numeric keys fall back to original order behavior

CNCF implication:

- client-side repeated form controls may use simple 1-based numbering
- deterministic order can be preserved without JSON arrays


9. Notation Summary
-------------------

### 9.1 Nested object

- `a.b.c=value`

Meaning:

- nested object path

### 9.2 Property sequence

- `items__1.name=value`
- `items__2.name=value`

Meaning:

- sequence under property `items`

### 9.3 Whole record sequence

- `__1.name=value`
- `__2.name=value`

Meaning:

- top-level sequence of records

### 9.4 Query adornment

- `name__query_equal=value`
- `title__query_like=value`

Meaning:

- structural path + query operator adornment


10. Interpretation of the Example `/__1/x/y__query_xxx`
-------------------------------------------------------

The exact slash form `/__1/x/y__query_xxx` is not the native syntax used by the
current Record v3 implementation.

The native syntax is dot-based for path and double-underscore-based for adornment.
The closest native equivalent is:

- `__1.x.y__query_xxx`

Interpretation:

- `__1`
  = first element of a whole-record sequence
- `.x.y`
  = nested path inside that element
- `__query_xxx`
  = query adornment with operator `xxx`

If CNCF wants to expose a slash-looking external path form in HTML or routing,
it should be defined as a separate presentation-layer convention and then mapped
to the native Record key syntax.

Recommended canonical Record key:

- `__1.x.y__query_xxx`


11. Recommended CNCF Position
-----------------------------

CNCF should treat the following as the canonical Record-form notation for web/form
submission:

1. path delimiter: `.`
2. adornment delimiter: `__`
3. adornment argument delimiter: `_`
4. sequence discriminator: `__{index}` attached to the property or root

Recommended accepted examples:

- `user.name`
- `user.address.city`
- `items__1.name`
- `items__2.price`
- `__1.name`
- `name__query_equal`
- `title__query_like`
- `items__1.name__query_like`


12. CNCF Usage Guidance
-----------------------

### 12.1 Form API

Use this notation for:

- nested input objects
- repeated input groups
- query/search forms

### 12.2 Static Form Applications

This notation is especially suitable for:

- plain HTML forms
- server-side form parsing without JavaScript
- multi-row input patterns

### 12.3 Avoid Premature Slash Syntax

Do not define slash-based variants as canonical at the Record layer.

If slash notation is needed later for:

- route templates
- browser-facing aliases
- ergonomic web helpers

it should be treated as a web-layer alias and translated into the canonical
Record key notation before `RecordParser`.


13. Open Design Items
---------------------

The following are not yet fixed by this note:

1. whether CNCF should accept bracket notation aliases such as `items[1].name`
2. whether CNCF should accept slash aliases such as `/items/1/name`
3. whether camel/snake/kebab equivalence should also be applied inside path segments
4. how whole-record sequence input should be surfaced at operation boundaries
5. whether query adornment should remain Record-level or be wrapped by higher-level CNCF query conventions


14. Source References
---------------------

Primary implementation references:

- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/parser/RecordParser.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/v3/HttpPart.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/v3/Record.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/query/QueryExpression.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/v3/PathNamePart.scala`
- `/Users/asami/src/dev2025/goldenport-scala-library/src/main/scala/org/goldenport/values/ParameterKey.scala`
