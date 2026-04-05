CNCF Query/Update Unified Request Grammar Proposal (Journal)
============================================================

status=journal
published_at=2026-04-06
category=request / grammar / web

Overview
--------

This note proposes a unified request grammar for CNCF that can cover both:

- query-oriented request semantics
- update-oriented request semantics

The purpose is not to replace the current Record implementation immediately.
The purpose is to define a CNCF-level public grammar that can later be mapped to
existing Record capabilities.

The proposal is based on two existing facts:

1. query semantics already exist through `__query_*`
2. update semantics already exist through postfix commands such as `__overwrite`,
   `__append`, and `__null`

The current problem is that these two lines are implemented differently and are
not yet presented as one coherent public contract.


1. Design Goal
--------------

The unified grammar should satisfy the following goals:

1. support plain HTML form submission
2. support HTTP query parameter submission
3. support nested paths
4. support repeated structures
5. support explicit query operators
6. support explicit update operators
7. support null assignment
8. remain mappable to the current Record implementation


2. Canonical Shape
------------------

The canonical field form is proposed as:

- `<path>__<kind>_<operator>`

where:

- `<path>` = canonical dot path
- `<kind>` = semantic family
- `<operator>` = operation inside that family

Examples:

- `name__query_equal`
- `title__query_like`
- `memo__update_set`
- `memo__update_null`
- `tags__update_append`
- `tags__update_remove`

This gives CNCF one visible public rule:

- semantics live after `__`
- the first token after `__` is the family


3. Canonical Families
---------------------

### 3.1 Query family

Family name:

- `query`

Examples:

- `name__query_equal=Alice`
- `age__query_greater=20`
- `title__query_like=abc%`
- `memo__query_is_null=1`

### 3.2 Update family

Family name:

- `update`

Examples:

- `name__update_set=Alice`
- `memo__update_null=1`
- `tags__update_append=blue`
- `tags__update_remove=red`
- `attachments__update_clear=1`

This gives a symmetric top-level grammar:

- `__query_*`
- `__update_*`


4. Canonical Path Syntax
------------------------

Canonical path syntax should remain:

- dot-based

Examples:

- `user.name`
- `user.address.city`
- `items__1.name`
- `__1.name`

This aligns with existing Record path handling and avoids introducing a second
canonical path notation.

If CNCF later wants to accept:

- slash aliases
- bracket aliases

those should be treated as web-layer aliases, not canonical storage grammar.


5. Proposed Query Operators
---------------------------

Initial canonical query operators:

- `equal`
- `not_equal`
- `greater`
- `greater_equal`
- `lesser`
- `lesser_equal`
- `like`
- `regex`
- `is_null`
- `is_not_null`
- `is_empty`
- `is_not_empty`
- `all`

Examples:

- `status__query_equal=active`
- `deletedAt__query_is_null=1`
- `title__query_like=foo%`

These operators should map to the existing `QueryExpression` set.


6. Proposed Update Operators
----------------------------

Initial canonical update operators:

- `set`
- `null`
- `append`
- `prepend`
- `remove`
- `clear`
- `delete`
- `delete_all`
- `remove_all`
- `unchange`

Examples:

- `name__update_set=Alice`
- `memo__update_null=1`
- `tags__update_append=blue`
- `tags__update_remove=red`
- `attachments__update_clear=1`

Notes:

- `set` is preferred as the public canonical operator instead of `overwrite`
- internally, `set` can map to existing overwrite/content behavior
- `null` remains explicit and first-class


7. Public Canonical Names vs Internal Legacy Names
--------------------------------------------------

The public CNCF grammar should normalize names even if current internals differ.

### 7.1 Query

Public canonical names should use underscore-separated operator names where needed.

Examples:

- `is_null`
- `is_not_null`
- `is_empty`
- `is_not_empty`

### 7.2 Update

Public canonical names should use underscore-separated operator names.

Examples:

- `delete_all`
- `remove_all`

Rationale:

- current implementations already have naming inconsistency
- public syntax should not expose that inconsistency
- a stable public grammar should be normalized first, then mapped inward


8. Null Semantics
-----------------

Null semantics should be explicit in the unified grammar.

Canonical form:

- `field__update_null=1`

This is preferred over relying only on blank input interpretation.

Blank input policy should remain separate and configurable.

Recommended policy distinction:

- grammar-level null:
  - explicit `__update_null`
- parser policy:
  - blank input under nullifying mode may also become null

This keeps intent explicit while still allowing ergonomic form policies.


9. Nested and Repeated Update Examples
--------------------------------------

The unified grammar should support nested and repeated structures directly.

Examples:

- `user.address.city__update_set=Tokyo`
- `user.address.zip__update_null=1`
- `items__1.name__update_set=Book`
- `items__1.tags__update_append=featured`
- `__1.memo__update_null=1`

This gives one consistent rule:

- path first
- semantics second


10. Mapping to Current Record Implementation
--------------------------------------------

The proposal does not require immediate reimplementation.

It can be mapped as follows.

### 10.1 Query mapping

- `field__query_equal`
  -> already close to current query handling

### 10.2 Update mapping

- `field__update_set`
  -> map to `field__overwrite` or equivalent internal set behavior
- `field__update_null`
  -> map to `field__null`
- `field__update_append`
  -> map to `field__append`
- `field__update_remove`
  -> map to `field__remove`
- `field__update_clear`
  -> map to `field__clear`

This means CNCF can introduce a normalized public contract before the Record
layer itself is refactored.


11. Why `update_set` Instead of `overwrite`
-------------------------------------------

`set` is proposed as the canonical public operator because:

1. it is symmetric with query operators
2. it is semantically simpler
3. it does not expose internal implementation wording
4. it generalizes better if richer update semantics are added later

So the public model becomes:

- `query_equal`
- `update_set`

instead of mixing:

- `query_equal`
- `overwrite`


12. Why Not Fully Merge Query and Update into One Operator Space
----------------------------------------------------------------

A fully merged operator space such as:

- `field__equal`
- `field__set`
- `field__append`

is not recommended.

Reason:

- query and update are different semantic families
- keeping the family token (`query`, `update`) improves readability
- it avoids ambiguity in HTML forms and generated web artifacts
- it makes routing to internal handlers simpler

So the recommended structure is:

- one unified grammar shape
- two explicit semantic families

not one flat operator namespace.


13. Recommended CNCF Policy
---------------------------

Recommended policy for CNCF public request grammar:

1. canonical path notation is dot-based
2. canonical semantic suffix is `__<family>_<operator>`
3. initial families are `query` and `update`
4. initial canonical update operator for assignment is `set`
5. explicit null assignment is supported as `update_null`
6. legacy/internal variants may be accepted during transition, but should not be
   documented as the primary public syntax

13.1 Frozen Canonical Update Operators
--------------------------------------

The following update operators are now the recommended canonical CNCF public vocabulary:

- `set`
- `null`
- `append`
- `prepend`
- `remove`
- `clear`

These should be treated as the primary documented operators for web/form-facing CNCF syntax.

The following remain secondary / provisional and should not be treated as the main public surface until semantics are tightened:

- `delete`
- `delete_all`
- `remove_all`
- `unchange`

Canonical examples:

- `name__update_set=Alice`
- `memo__update_null=1`
- `tags__update_append=blue`
- `tags__update_prepend=vip`
- `tags__update_remove=red`
- `attachments__update_clear=1`

Policy implication:

- CNCF public documentation should start with these six operators
- translation to internal legacy names remains an implementation detail
- operators outside this set should be documented only when a concrete CNCF boundary semantics note is prepared


14. Open Design Items
---------------------

The following remain open:

1. whether `content` should survive as a public operator or stay internal
2. whether `delete` and `clear` should be both public, and how their semantics differ
3. whether query/update suffixes should support kebab aliases in addition to underscore
4. whether CNCF should support public bracket or slash path aliases
5. whether batch/root-sequence update forms should be publicly supported from the beginning


15. Recommended Next Step
-------------------------

The next practical step is:

1. freeze canonical public names
2. implement a translation layer
3. keep current Record internals working underneath
4. document only the normalized CNCF syntax in web/form-facing documents


16. Related Notes
-----------------

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/record-v3-http-form-path-notation-note.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/04/record-update-command-and-null-semantics-note.md`
