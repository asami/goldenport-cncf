## Naming Policy Design Note

### Goal

Define a consistent naming policy across:

- object and Scala model definitions
- external output such as CLI/YAML/JSON
- database column naming
- external input such as command arguments and record fields

The goal is to keep internal definitions natural for Scala and model generation, while making external interfaces stable and user-friendly.

### Policy

#### 1. Object Definition Names

Object definitions use `camelCase` as the canonical internal form.

Examples:

- `totalCount`
- `fetchedCount`
- `traceId`
- `postStatus`

This applies to:

- Scala fields
- generated model fields
- canonical `Record`-level semantic field names before formatting

#### 2. Output Naming

Database column names and external output names are formatting concerns.

They should be selected by naming policy, not hard-coded in each domain object.

Default output naming is `snake_case`.

Examples:

- `totalCount` -> `total_count`
- `fetchedCount` -> `fetched_count`
- `traceId` -> `trace_id`
- `postStatus` -> `post_status`

Output targets include:

- CLI output
- YAML output
- JSON output
- database column naming

#### 3. Input Naming

External input should accept all of the following as equivalent:

- `camelCase`
- `snake_case`
- `kebab-case`

Examples:

- `traceId`
- `trace_id`
- `trace-id`

This applies to:

- command arguments
- record field names
- query parameters
- input payload field names

### Design Principle

Naming style should be handled at the boundary.

Internal model:

- canonical
- `camelCase`

External output:

- formatter-driven
- default `snake_case`

External input:

- normalized
- accepts camel/snake/kebab

This avoids leaking transport or storage naming decisions into domain model definitions.

### Current State

Current implementation is still mixed.

- some output records explicitly use `snake_case`
- some persistence paths choose between camel and snake by inspecting existing record keys
- input normalization already exists in several places, but not yet as a single explicit naming policy layer

### Direction

Introduce an explicit naming policy layer with the following responsibilities:

1. canonical field naming stays `camelCase`
2. output formatter transforms field names according to policy
3. input parser normalizes `camelCase`, `snake_case`, and `kebab-case`
4. database column naming follows the same output naming policy, defaulting to `snake_case`

### Migration Order

1. Normalize input field-name matching through a shared helper.
2. Add output key transformation support for structured output.
3. Move ad hoc `snake_case` record construction to formatter-driven conversion where practical.
4. Align database column naming policy with the same naming abstraction.

### Runtime Context Placement

The naming policy should not be treated as an isolated utility.

It should be placed under execution/runtime context so that output and input behavior can be controlled consistently at runtime.

A natural direction is:

- `ExecutionContext`
  - `runtime: RuntimeContext`
- `RuntimeContext`
  - `context: Context`

Where `Context` groups presentation and boundary concerns such as:

- property-name control
- formatting control
- i18n/locale control

For example:

- `PropertyNameContext`
  - canonical naming
  - output naming policy
  - accepted input naming styles
- `FormattingContext`
  - numeric formatting
  - date/time formatting
  - timezone-aware display formatting
  - duration formatting
- `I18nContext`
  - locale
  - language
  - translated labels/messages

This keeps naming, formatting, and locale concerns out of domain objects while still making them available to:

- CLI output
- YAML/JSON output
- database naming projection
- request/input normalization

### Recommended Realization Order

1. Introduce `RuntimeContext.Context`.
2. Add `PropertyNameContext` first.
3. Apply it to structured output and input normalization.
4. Add `FormattingContext`.
5. Add `I18nContext`.

### Implementation Status

The current implementation now includes the three runtime subcontexts:

- `PropertyNameContext`
- `FormattingContext`
- `I18nContext`

Current behavior:

- `PropertyNameContext`
  - drives structured output key conversion
  - normalizes query/input field names across camel/snake/kebab
  - is used by entity-store and collection paths for key alias resolution
- `FormattingContext`
  - is part of `RuntimeContext.Context`
  - transforms structured output values
  - defaults to plain numeric output
  - formats temporal values through configured timezone/formatters
  - supports localized numeric string rendering as an opt-in mode
- `I18nContext`
  - is part of `RuntimeContext.Context`
  - currently provides locale and message lookup with fallback text
  - serves as the runtime entry point for later formatter/projection localization

Remaining work is mainly broader adoption:

- applying formatter policy to more output paths
- applying i18n-aware message/label rendering in projections and user-facing responses
- reducing remaining ad hoc key handling where present

### Related Concern

`Recordable` is not an ideal name for the current role.

The actual meaning is closer to:

- convertible to `Record`
- representable as `Record`

A future rename such as `ToRecord` should be considered separately from the naming policy work.
