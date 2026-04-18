# Query Field Resolution

## Purpose

Query field resolution is the CNCF runtime contract that connects API-facing
structured Query field names to the canonical entity schema field names carried
from CML-generated metadata.

The goal is to let clients, Web forms, REST calls, command calls, and generated
ActionCalls use logical names such as `recipientName`, `recipient_name`, or
`recipient-name` without making application code depend on storage-specific
column/key spelling.

## Responsibility Boundary

`EntityQueryFieldResolver` owns schema-aware logical field normalization.

It resolves Query paths against:

1. `Component.entityRuntimeDescriptor(entityName)`
2. `EntityRuntimeDescriptor.schema`
3. `Component.viewDefinitions` when view field metadata is needed

The authoritative source is the CML/generated schema exposed through
`EntityRuntimeDescriptor.schema`. Web Descriptor metadata may supplement
presentation and form behavior, but it must not replace the CML schema as the
canonical field source.

DataStore implementations own physical name translation. SQL, document, and
other stores may map canonical schema names such as `recipientName` to physical
names such as `recipient_name`, but that mapping is below this layer.

## Runtime Contract

Every generated or framework-provided entity/view search ActionCall that accepts
a structured `Query` must rewrite it before invoking the search DSL:

```scala
fields <- exec_pure(EntityQueryFieldResolver(core.component, "Notice"))
r <- entity_search(Notice.collectionId, fields.rewrite(query))
```

The same rule applies to:

- `IMPLEMENTATION entity-search`
- `IMPLEMENTATION view-search`
- generated entity service search operations
- generated entity service record search operations
- generated view service search operations
- generated view service record search operations
- generated named/projection view search operations

Hand-written ActionCalls should follow the same rule when they construct a
structured `Query` manually. The framework and generator should provide the
normal path so that ordinary application code does not need to duplicate field
resolution logic.

## Query Shape

The resolver rewrites structured Query field paths in:

- condition expressions
- where expressions
- sort expressions
- record/map based field conditions

It must preserve non-field values and Query controls such as paging controls.
Controls are not entity field conditions unless they explicitly appear inside
the structured condition tree.

## Aggregate Search

Aggregate search is intentionally not part of the current field resolution
contract.

An aggregate query may target:

- aggregate root fields
- member entity fields
- derived projection fields
- operation-specific search keys

Those targets need an aggregate-specific query model and authorization boundary.
Until that design is explicit, aggregate search must not be silently treated as
ordinary entity field resolution.

## Non-Scope

Full-text search is outside Query field resolution. Parameters such as `text`,
`q`, and `keyword` represent search intent, not a single structured field path.
Expanding those parameters into field predicates, tokenizer behavior, ranking,
or index selection belongs to a future search planning layer.

Embedding and semantic search are also outside this contract. They require
separate decisions for embedding target selection, vector index lifecycle,
update synchronization, similarity thresholds, ranking, and structured filter
composition.

## Design Rule

Use Query field resolution only for structured field path normalization.

Do not use it to decide:

- which fields are searchable
- how text is tokenized
- whether total counts are required
- which physical storage column/key is used
- which embedding or search index backend runs
