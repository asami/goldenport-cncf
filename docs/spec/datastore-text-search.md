# DataStore text search

## R1: Literal predicates and explicit patterns

`Query.Contains`, `Query.StartsWith` and `Query.EndsWith` take literal text.
SQL providers escape `!`, `%` and `_` in those arguments and bind the resulting
pattern with `ESCAPE '!'`. Backslash, quotes and other characters remain data.
`Query.Like` continues to accept an explicit SQL wildcard pattern. Existing
provider case policies, binding, column quoting, ordering and paging remain in
force; this change does not define a new raw SQLite case policy.

## R2: Explicit declared search field

`DataStoreTextSearchField(path: String, caseInsensitive: Boolean = false)`
declares a physical search column. Its path must be nonnull, nonblank and equal
to its trimmed form. The path is trusted declaration data, not client-selected
authority. All nonnull String text is admitted, including empty text,
whitespace, NUL, controls, unpaired surrogates and every UTF16 code unit.

`toStoreValue(value: String): String` emits version 1 derived search data:
`u16x1:` followed by `x` and exactly four lowercase hexadecimal digits per
UTF16 code unit. Sensitive `A` becomes `u16x1:x0041`, NUL becomes
`u16x1:x0000`, and empty text becomes `u16x1:`. No charset conversion,
trimming, Unicode normalization or invalid-unit replacement occurs.

## R3: Paired policy and predicates

With `caseInsensitive = true`, both stored text and each needle are first
lowercased using Java `Locale.ROOT`. Otherwise they remain unchanged. This is
the exact selected String policy, including contextual/length-changing Java
lowercasing; it is not delegated to database LOWER or collation.

The public builders return existing `Query.Expr` values, all with
`caseInsensitive = false`:

| Builder | Existing predicate | Argument |
| --- | --- | --- |
| `contains(value)` | `Query.Contains` | encoded normalized needle, no version prefix |
| `startsWith(value)` | `Query.StartsWith` | version prefix and encoded normalized needle |
| `endsWith(value)` | `Query.EndsWith` | encoded normalized needle, no version prefix |

Matching corresponds exactly to `contains`, `startsWith` and `endsWith` on
the original JVM Strings after the declared policy. Empty needles match every
present projection, including the empty source. Unit markers prohibit partial
hexadecimal/code-unit boundary matches.

## R4: Persistence and authority

This additive projection is not a canonical String codec and has no decoder.
Ordinary physical String and SQL NULL handling remain governed by
[logical type mapping](datastore-logical-type-mapping.md), especially R1, R2,
R7 and R9. No payload-shape inference or provider default changes apply.

Consumers retain original data separately and maintain the projection in the
same native Entity persistence mutation with the original. Existing identity,
authorization, revision, transaction and managed-field stages remain
authoritative. Consumers own declaration/version pairing and any explicit
future migration; there is no automatic migration or live rewrite. Projection
values remain data: they are neither safe-to-log tokens nor permissions.

This framework contract does not claim TKL consumer activation, ordinary title
search availability, public registration/read/search or Studio wiring.

## Executable specifications

- `DataStoreTextSearchFieldSpec`: declared representation, policy and arbitrary
  UTF16 correspondence through `Query.eval`.
- `SqliteTextSearchSpec`: actual embedded SQL literal and projected queries.
- `PostgresqlTextSearchSpec`: selected Slow acceptance on owned PostgreSQL
  17.11, requiring real startup and durable settings.

All three specify at least 100 successful, zero-discard arbitrary UTF16
samples, with a source, an arbitrary code-unit substring and an independent
needle, using independent original String expectations.
