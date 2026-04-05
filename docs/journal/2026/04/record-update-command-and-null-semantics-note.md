Record Update Command and Null Semantics Note (Journal)
=======================================================

status=journal
published_at=2026-04-06
category=web / form / update

Overview
--------

This note summarizes the existing update-oriented request features available in
Record parsing and command interpretation.

The purpose is to clarify whether Record-based HTTP/Form input already supports:

- update-oriented field operations
- null assignment
- empty-string handling modes
- field-level update commands distinct from query commands

The conclusion is:

- query-oriented syntax exists
- update-oriented syntax also exists
- null assignment is supported
- update semantics and query semantics are implemented through different mechanisms


1. Two Different Extension Lines
--------------------------------

Record currently has two different request extension lines.

### 1.1 Query line

Used for search/filter semantics.

Examples:

- `name__query_equal=Alice`
- `title__query_like=abc%`

This line is implemented through:

- `ParameterKey`
- `QueryExpression`

### 1.2 Update line

Used for update-oriented field operations.

Examples:

- `name__overwrite=Alice`
- `tags__append=blue`
- `memo__null=1`

This line is implemented through:

- `PropertyOperation`
- `FieldCommand`
- `ValueCommand`

These two lines are related in purpose but separate in implementation.
They are not currently a unified grammar.


2. Update-Oriented Field Operations
-----------------------------------

The following field operations are defined at the parser / command layer.

From `PropertyOperation`:

- `content`
- `unchange`
- `append`
- `overwrite`
- `remove`
- `delete`
- `remove-all`
- `delete-all`
- `clear`
- `command`
- `search`

From `FieldCommand` constants:

- `content`
- `overwrite`
- `null`
- `unchange`
- `append`
- `prepend`
- `remove`
- `delete`
- `remove_all`
- `delete_all`
- `clear`

This means the existing Record model already includes explicit update commands,
not only plain overwrite behavior.


3. Null Assignment
------------------

Null assignment is supported.

Relevant types:

- `NullCommand`
- `NullValue`

Important behavior:

- `NullValue.getSqlLiteralForSet = Some("NULL")`

This means Record-level command interpretation explicitly preserves the intent
of assigning SQL `NULL`.

Canonical field example:

- `memo__null=1`

Interpretation:

- the value itself is not semantically important
- the postfix `__null` carries the operation intent

Practical meaning:

- a form request can explicitly request null assignment without relying only on
  omission or empty-string heuristics


4. Empty Input Handling Modes
-----------------------------

`UpdateMode` defines how empty input should be interpreted.

Defined modes:

- `NeutralMode`
- `FormMode`
- `NullifyMode`

### 4.1 NeutralMode

- empty value -> void
- empty string -> string

Interpretation:

- missing value tends to be ignored
- empty string tends to remain an empty string

### 4.2 FormMode

- empty value -> void
- empty string -> void

Interpretation:

- HTML form blank input is treated as "no update" / absent input

### 4.3 NullifyMode

- empty value -> null
- empty string -> null

Interpretation:

- blank form input is treated as explicit nullification

This is important because null assignment can be expressed in two ways:

1. explicit command
   - `field__null`
2. mode-driven interpretation
   - blank input under `NullifyMode`


5. Parser Flow for Form Updates
-------------------------------

The relevant parsing flow is:

1. `RecordParser.httpForm(...)`
2. `_key_structure`
3. `_command`
4. `FieldParser.command(...)`
5. `PropertyOperation.getOperation(...)`
6. command-oriented field/value interpretation

This means update semantics are intended to be available directly from HTTP/Form
submission, not only from programmatic Record construction.


6. Explicit Field Postfix Semantics
-----------------------------------

The update line uses postfix-based field naming.

Examples:

- `name__overwrite=Alice`
- `description__content=...`
- `tags__append=blue`
- `tags__remove=red`
- `memo__null=1`
- `attachments__clear=1`

This is different from the query line:

- `name__query_equal=Alice`

Key distinction:

- query postfix uses `ParameterKey` adornment
- update postfix uses `PropertyOperation` matching

So the current system has:

- `field__query_xxx`
- `field__overwrite`
- `field__append`
- `field__null`

but they do not come from the same parser abstraction.


7. Current Naming Inconsistency
-------------------------------

A naming inconsistency exists.

`PropertyOperation` uses hyphenated names:

- `remove-all`
- `delete-all`

`FieldCommand` constants use underscored names:

- `remove_all`
- `delete_all`

This means the update command vocabulary is not yet fully normalized.

For CNCF, this should be treated as an implementation inconsistency to be
resolved before public convention is frozen.


8. What Is Already Available
----------------------------

Already available today:

- explicit overwrite
- append/remove style collection mutation intent
- clear/delete style intent
- explicit null assignment
- mode-based blank-to-null conversion
- form parsing path that preserves these semantics

This is enough to say that Record already contains a meaningful update-oriented
request model.


9. What Is Not Yet Cleanly Unified
----------------------------------

The following are not yet unified:

1. query and update syntax are separate mechanisms
2. naming is inconsistent for some commands
3. public canonical syntax has not been stabilized for CNCF
4. semantics for `delete`, `clear`, `remove-all`, and `delete-all` should be
   documented more explicitly at the operation boundary level
5. nested path + update postfix combinations need an explicit CNCF convention

Example candidate that should be clarified later:

- `user.address.city__null`
- `items__1.name__overwrite`

These seem conceptually valid, but CNCF should define them explicitly before
making them part of a public web/form contract.


10. CNCF Implication
--------------------

CNCF does not need to invent update semantics from zero.

Instead, CNCF can build on the existing Record capabilities:

- query semantics for search forms
- update semantics for mutation forms
- null handling through explicit command or nullify mode

The immediate design task for CNCF is therefore not invention but normalization:

1. choose canonical public field postfixes
2. define whether hyphen or underscore is accepted
3. define how nested paths interact with update postfixes
4. define whether blank form input should map to void or null by default
5. define which update commands are allowed in web-facing form APIs


11. Recommended Near-Term Direction
-----------------------------------

Near-term recommendations:

1. preserve explicit null command support
   - `field__null`
2. keep `NullifyMode` as a separate policy, not the only null mechanism
3. normalize command vocabulary before exposing it as CNCF public syntax
4. keep query and update conventions conceptually distinct for now
5. later, define a unified CNCF-level request grammar if needed


12. Source References
---------------------

Primary implementation references:

- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/parser/RecordParser.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/parser/FieldParser.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/parser/PropertyOperation.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/command/FieldCommand.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/command/ValueCommand.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/command/UpdateMode.scala`
- `/Users/asami/src/dev2025/goldenport-record/src/main/scala/org/goldenport/record/query/QueryExpression.scala`
