CNCF Query/Update Request Translation Rule Note (Journal)
=========================================================

status=journal
published_at=2026-04-06
category=request / translation / web

Overview
--------

This note defines the translation rule between:

- the canonical CNCF public request grammar
- the current Record-level internal request grammar

The immediate goal is to let CNCF expose a normalized public syntax without
requiring an immediate rewrite of the underlying Record implementation.

This note assumes the following canonical public syntax:

- query family: `__query_<operator>`
- update family: `__update_<operator>`

Canonical path syntax remains dot-based.


1. Translation Boundary
-----------------------

The translation boundary should sit above `RecordParser`.

Recommended flow:

1. web/form request arrives
2. CNCF request normalizer rewrites canonical public keys into internal Record keys
3. normalized key/value map is passed to `RecordParser`
4. existing Record query/update machinery handles the rest

This keeps:

- public syntax stable
- legacy implementation reusable
- migration incremental


2. Path Translation Rule
------------------------

No path translation is needed for canonical CNCF dot paths.

Examples:

- `user.name`
- `user.address.city`
- `items__1.name`
- `__1.name`

These should pass through unchanged.

If later CNCF accepts slash or bracket aliases, those should first be rewritten
to the canonical dot-based path before applying query/update translation.


3. Query Translation Rule
-------------------------

Canonical CNCF query syntax is already close to the current internal syntax.

### 3.1 Direct pass-through

Examples:

- `name__query_equal`
- `title__query_like`
- `deletedAt__query_is_null`

Translation rule:

- pass through unchanged

Reason:

- current query parsing already uses `ParameterKey` + `QueryExpression`
- the canonical CNCF query proposal intentionally matches this shape


4. Update Translation Rule
--------------------------

Canonical CNCF update syntax uses the `update` family.
Current Record internals do not.

So update keys require explicit rewriting.

Canonical shape:

- `<path>__update_<operator>`

Internal Record shape:

- `<path>__<internal-operator>`


5. Canonical-to-Internal Mapping
--------------------------------

### 5.1 Assignment

Canonical:

- `field__update_set`

Internal:

- `field__overwrite`

Rationale:

- current Record implementation uses overwrite/content semantics
- CNCF public syntax should expose `set`, not `overwrite`

### 5.2 Null assignment

Canonical:

- `field__update_null`

Internal:

- `field__null`

### 5.3 Append

Canonical:

- `field__update_append`

Internal:

- `field__append`

### 5.4 Prepend

Canonical:

- `field__update_prepend`

Internal:

- `field__prepend`

### 5.5 Remove

Canonical:

- `field__update_remove`

Internal:

- `field__remove`

### 5.6 Clear

Canonical:

- `field__update_clear`

Internal:

- `field__clear`


6. Translation Table
--------------------

| Canonical CNCF | Internal Record |
| --- | --- |
| `__query_equal` | `__query_equal` |
| `__query_not_equal` | `__query_not_equal` |
| `__query_greater` | `__query_greater` |
| `__query_greater_equal` | `__query_greater_equal` |
| `__query_lesser` | `__query_lesser` |
| `__query_lesser_equal` | `__query_lesser_equal` |
| `__query_like` | `__query_like` |
| `__query_regex` | `__query_regex` |
| `__query_is_null` | `__query_is_null` |
| `__query_is_not_null` | `__query_is_not_null` |
| `__query_is_empty` | `__query_is_empty` |
| `__query_is_not_empty` | `__query_is_not_empty` |
| `__query_all` | `__query_all` |
| `__update_set` | `__overwrite` |
| `__update_null` | `__null` |
| `__update_append` | `__append` |
| `__update_prepend` | `__prepend` |
| `__update_remove` | `__remove` |
| `__update_clear` | `__clear` |


7. Nested and Repeated Structure Examples
-----------------------------------------

### 7.1 Nested field set

Canonical input:

- `user.address.city__update_set=Tokyo`

Translated internal key:

- `user.address.city__overwrite=Tokyo`

### 7.2 Nested field null

Canonical input:

- `user.address.zip__update_null=1`

Translated internal key:

- `user.address.zip__null=1`

### 7.3 Repeated item append

Canonical input:

- `items__1.tags__update_append=featured`

Translated internal key:

- `items__1.tags__append=featured`

### 7.4 Whole-record sequence null

Canonical input:

- `__1.memo__update_null=1`

Translated internal key:

- `__1.memo__null=1`


8. Blank Input Policy Is Separate
---------------------------------

The translation layer should not rewrite blank input into explicit null commands.

That policy belongs to parser/update mode handling.

Meaning:

- `field__update_null=1`
  is an explicit grammar-level null request
- blank `field=`
  remains a parser-policy matter

This distinction is important because CNCF wants to preserve both:

- explicit null intent
- configurable blank-input policy


9. Operators Not Yet Canonically Exposed
----------------------------------------

The following internal operators should not be part of the primary CNCF public
surface yet:

- `__delete`
- `__delete_all`
- `__remove_all`
- `__unchange`
- `__content`

Reason:

- their semantics need tighter CNCF boundary definitions
- they may still be accepted internally or experimentally
- they should not be the first operators shown in public documentation

If later exposed, the same family model should be used:

- `__update_delete`
- `__update_delete_all`
- `__update_remove_all`
- `__update_unchange`

but this should wait for explicit semantics notes.


10. Translation Algorithm Sketch
--------------------------------

Given a field name:

1. split at the last `__`
2. inspect suffix
3. if suffix starts with `query_`
   - leave unchanged
4. if suffix starts with `update_`
   - map operator using canonical-to-internal update table
5. reassemble the field name

Examples:

- `name__query_equal`
  -> unchanged
- `memo__update_null`
  -> `memo__null`
- `tags__update_append`
  -> `tags__append`

This algorithm is intentionally simple and deterministic.


11. CNCF Implementation Guidance
--------------------------------

Recommended implementation placement:

- web/request normalization layer
- not inside generated model code
- not inside domain component code

Reason:

- this is boundary syntax normalization
- it belongs to ingress adaptation
- components and domain logic should see already-normalized requests


12. Recommended Status
----------------------

Recommended status of this rule:

- canonical for CNCF public syntax design
- transitional for implementation

That is:

- public docs should prefer canonical CNCF syntax
- implementation may still translate to current Record syntax underneath


13. Related Notes
-----------------

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/record-v3-http-form-path-notation-note.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/record-update-command-and-null-semantics-note.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/query-update-unified-request-grammar-proposal.md`
