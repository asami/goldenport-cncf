# Query Field Resolution

This note records the Phase 12 boundary for schema-aware Query field name
resolution.

The stable runtime contract is maintained in:

- `docs/design/query-field-resolution.md`

## Scope

`EntityQueryFieldResolver` resolves structured Query field paths to the
CML/schema logical field name. It is for ordinary structured conditions such as:

- equality: `recipient_name = bob`
- null checks: `recipientName is null`
- range comparisons: `publishAt <= now`
- sort paths and field-condition paths

The resolver compares names by CNCF normalized naming rules, so
`recipientName`, `recipient_name`, and `recipient-name` resolve to the same
schema field when the entity schema defines `recipientName`.

The resolver uses:

1. `Component.entityRuntimeDescriptor(entityName)`
2. `EntityRuntimeDescriptor.schema`
3. `Component.viewDefinitions` for view field sets when needed

The authoritative field source is the CML/generated schema carried by
`EntityRuntimeDescriptor.schema`. WebDescriptor may supplement presentation
metadata, but it must not become the canonical Query field list.

## Runtime Use

Generated entity and view search operations should wrap incoming Query values
with:

```scala
fields <- exec_pure(EntityQueryFieldResolver(core.component, "Notice"))
r <- entity_search(Notice.collectionId, fields.rewrite(query))
```

Hand-written `ActionCall` implementations should use the same resolver when
they construct structured Query expressions manually. This avoids application
code hard-coding a storage or transport spelling.

Cozy generation currently emits this resolver path for:

- `IMPLEMENTATION entity-search`
- `IMPLEMENTATION view-search`
- generated entity service search and record search operations
- generated view service search and record search operations
- generated named/projection view search operations

This generator behavior is covered by Cozy `ModelerGenerationSpec`.

DataStore implementations remain responsible for translating logical field
names to physical column/key names. For example, SQL storage may map
`recipientName` to `recipient_name` when its configuration normalizes column
names. The resolver must not perform storage-specific physical naming.

Aggregate search is not included in this contract yet. Aggregate queries may
refer to root fields, member fields, derived projection fields, or
operation-specific keys, so treating them as plain entity field resolution would
hide an unresolved modeling decision.

## Non-Scope

Full-text search is not part of Query field resolution. Parameters such as
`text`, `q`, or `keyword` express a search intent rather than a single
structured field condition. Expanding them into `Contains` over many fields is a
separate search planning problem.

Embedding and semantic search are also outside this layer. They need separate
planning for embedding targets, vector index lifecycle, update synchronization,
similarity thresholds, ranking, and interaction with structured Query filters.

Phase 12 tracks full-text search and embedding search as deferred/next-phase
candidates in `docs/phase/phase-12-checklist.md`.

## Practical Rule

Use `EntityQueryFieldResolver` for structured Query path normalization only.
Do not use it to decide which fields are searchable, how text is tokenized, or
which embedding/index backend should run.
