# DataStore Query Translation Note

Date: Apr. 15, 2026

## Context

SQLite `SqlDataStore` now implements `SearchableDataStore` for the current
DataStore query surface:

- `Query.Empty`
- projection
- order
- limit

This removes the immediate `datastore is not searchable` failure. Sample app
searches normally hit the EntitySpace working set first, but tests can bypass
resident memory to verify DataStore-backed behavior.

In the notice-board sample, `searchNotices` builds a query plan and calls:

```scala
entity_search[Notice](Notice.collectionId, query)
```

The application-side filter remains as a semantic backstop. In the normal
client/server SQLite run, the observed path was:

- `entity.search.try.entity-space`
- `entity.search.hit.entity-space`

So the ordinary successful SQLite-backed sample check verifies persistence and
searchability availability, but not SQL WHERE pushdown.

For DataStore-focused tests, runtime configuration can bypass the resident
path:

- `textus.entity.search.bypass-entity-space-resident`
  - bypasses the EntitySpace resident search path for entity search
  - falls through to EntityStore/DataStore search
- `textus.entity.search.bypass-resident`
  - broader resident-memory bypass option
  - currently implies EntitySpace resident bypass for entity search
  - reserved for applying the same policy to other resident paths as they gain
    explicit bypass hooks

The `cncf.*` names remain as compatibility aliases:

- `cncf.entity.search.bypass-entity-space-resident`
- `cncf.entity.search.bypass-resident`

The option names are intentionally different. EntitySpace resident bypass is
the narrow testing switch for query pushdown. Resident bypass is the broader
policy switch for all resident-memory shortcuts.

## Existing Query Model

CNCF already has a richer entity-level query model in:

- `org.goldenport.cncf.directive.Query`

It includes:

- `Query.True`
- `Query.False`
- `Query.And`
- `Query.Or`
- `Query.Not`
- `Query.Eq`
- `Query.Ne`
- `Query.Gt`
- `Query.Gte`
- `Query.Lt`
- `Query.Lte`
- `Query.In`
- `Query.NotIn`
- `Query.IsNull`
- `Query.IsNotNull`
- `Query.Like`
- `Query.StartsWith`
- `Query.EndsWith`
- `Query.Contains`
- `Query.Plan(condition, where, sort, limit, offset)`

This means CNCF does not need a second query DSL for DataStore pushdown.

## Gap

DataStore-level `org.goldenport.cncf.datastore.Query` currently has only:

- `Query.Empty`

`EntityStore.search` therefore calls DataStore with:

```scala
QueryDirective(
  query = org.goldenport.cncf.datastore.Query.Empty,
  order = _to_datastore_order(query.query.sort),
  limit = QueryLimit.Unbounded
)
```

and then performs:

- logical deletion filtering
- visibility filtering
- entity decoding
- `directive.Query.matches`
- sort
- offset/limit slicing

This is correct but cannot push ordinary entity query conditions into SQL.

## Direction

Use `org.goldenport.cncf.directive.Query.Expr` as the semantic source of truth.

The DataStore layer should receive a translated query expression, not invent a
parallel condition model.

Candidate DataStore query shape:

```scala
sealed trait Query
object Query {
  case object Empty extends Query
  final case class Expr(
    where: org.goldenport.cncf.directive.Query.Expr
  ) extends Query
}
```

Then `EntityStore.search` can pass:

```scala
QueryDirective(
  query = DataStoreQuery.Expr(EntityDirectiveQuery.whereOf(query.query)),
  order = _to_datastore_order(query.query.sort),
  limit = _to_datastore_limit(query.query.limit)
)
```

The in-memory path can continue to evaluate via `directive.Query.matches`, and
SQL DataStore can translate supported expressions to `WHERE`.

## SQL Translation Policy

SQL pushdown should be best-effort but explicit:

- Supported expressions are pushed into SQL.
- Unsupported expressions should not silently change semantics.
- If the caller requires pushdown, unsupported expressions should fail.
- If pushdown is optional, DataStore may return a superset and EntityStore
  still applies the existing in-memory `Query.matches` filter after decoding.

The default should be conservative:

- push down expressions that are directly safe
- always keep EntityStore post-filtering as the semantic backstop

## Initial SQLite Support

Initial SQL translation can support:

- `True`
- `False`
- `And`
- `Or`
- `Not`
- `Eq`
- `Ne`
- `Gt`
- `Gte`
- `Lt`
- `Lte`
- `In`
- `NotIn`
- `IsNull`
- `IsNotNull`
- `Like`
- `StartsWith`
- `EndsWith`
- `Contains`

String operations map naturally:

- `Like(path, pattern)` -> `column LIKE ?`
- `StartsWith(path, value)` -> `column LIKE 'value%'`
- `EndsWith(path, value)` -> `column LIKE '%value'`
- `Contains(path, value)` -> `column LIKE '%value%'`

Case-insensitive matching for SQLite can use `LOWER(column) LIKE LOWER(?)` or
normalize both sides before binding.

## EntitySpace Interaction

EntitySpace remains the preferred path when resident data exists. This is
appropriate for the current working-set model.

SQL pushdown becomes important when:

- EntitySpace has no resident collection
- a task/inactive entity has been evicted from memory
- `textus.entity.search.bypass-entity-space-resident` is enabled for tests
- `textus.entity.search.bypass-resident` is enabled for broader resident bypass
- admin/data browser needs DataStore-backed paging
- total count is requested and DataStore can support it

## Total Count Control

Entity search does not calculate total count by default. This is important for
DataStore-backed search because total count may require a separate query or may
be effectively unavailable on some middleware.

`Query.Plan` has an explicit `includeTotal` flag. `EntityStore.search` and
resident `EntityCollection.search` return `SearchResult.totalCount` only when
`includeTotal=true`.

Runtime request records may set the same control through:

- `query.includeTotal`
- `query.include_total`

When `includeTotal=true`, EntityStore must not fetch an unbounded candidate set
just to count rows. Instead it keeps the page query bounded and asks the
DataStore for a separate total count. SQLite uses `SELECT COUNT(*)` with the
same translated `WHERE` expression.

`QueryDirective` carries both `limit` and `offset`, so EntityStore pushes page
range directly into DataStore search. SQL DataStore translates this into
`LIMIT/OFFSET`; when an offset is requested without a limit, SQLite uses
`LIMIT -1 OFFSET n`.

Total count must ignore page range. EntityStore therefore runs the count
directive with `limit=Unbounded` and `offset=0`, while keeping the page search
bounded.

If a DataStore cannot support total count, an optional total request should
return no total, and a required total request should fail at the caller policy
layer. EntityStore must not emulate production total count by unbounded fetch.

## Next Implementation Steps

1. Extend DataStore `Query` with an expression-bearing case.
2. Translate entity `directive.Query.whereOf(...)` into DataStore query.
3. Add SQL WHERE generation with prepared-statement parameters.
4. Keep EntityStore post-filtering after decoding.
5. Add specs:
   - SQL Eq
   - SQL Contains case-insensitive
   - SQL And/Or
   - SQL order/limit with condition
   - EntityStore fallback post-filtering remains correct

## Sample App Follow-up

After DataStore query pushdown is available, update notice-board
`searchNotices` to construct a query plan instead of fetching all notices and
filtering entirely in application code.

For example:

```scala
Query.plan(
  Record.empty,
  where = Query.And(Vector(
    recipientName.map(v => Query.Eq("recipientName", v)),
    text.map(v => Query.Or(Vector(
      Query.Contains("senderName", v, caseInsensitive = true),
      Query.Contains("recipientName", v, caseInsensitive = true),
      Query.Contains("subject", v, caseInsensitive = true),
      Query.Contains("body", v, caseInsensitive = true)
    )))
  ).flatten)
)
```

The application may still apply an additional filter after loading if it needs
behavior that is not yet expressible in SQL.
