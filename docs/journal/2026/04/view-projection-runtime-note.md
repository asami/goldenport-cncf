# View Projection Runtime Note

- date: 2026-04-02
- status: working spec
- scope: CNCF runtime

## Purpose

This note defines the formal CNCF runtime semantics for `VIEW`.

The concern here is:

- bootstrap
- load/search behavior
- named view alias behavior
- `VIEW > QUERY` runtime interpretation
- metadata projection

Grammar and ModelCompiler concerns are intentionally handled in the Cozy-side note.

## Runtime Position

`VIEW` is a read-model projection surface.

It is not:

- an aggregate boundary
- an update boundary
- an independent persistence model

The current runtime line treats a view as:

- an entity-backed projection
- built from source entity records
- exposed through the `view` service

## Default View Semantics

For a default generated view:

1. runtime loads source entities from entity storage
2. runtime converts source entity records into generated view objects
3. runtime exposes:
   - `view.load`
   - `view.search`

This is structurally parallel to aggregate assembly:

- aggregate
  - entity -> aggregate object
- view
  - entity -> view object

The difference is that view is projection-oriented rather than aggregate-boundary-oriented.

## Named View Aliases

`VIEWS :: ...` defines named aliases on top of the canonical generated view.

Current runtime meaning:

- named aliases are registered as named view/browser aliases
- predefined projections `summary` and `detail` also have distinct generated Scala types
- runtime may resolve those named browsers to projection-specific generated modules

So at the current line:

- `summary`
  - runtime name
  - generated type: `entity.view.summary.<Type>`
- `detail`
  - runtime name
  - generated type: `entity.view.detail.<Type>`

For arbitrary custom projection names, the current runtime line is still alias-oriented.

## VIEW > QUERY Semantics

`VIEW > QUERY` currently has first-line runtime semantics.

Example:

```text
#### QUERY

##### searchByCity

- EXPRESSION :: person.city == query.city
```

Current runtime interpretation is:

1. the query name is normalized and registered as a named browser alias
2. the expression is scanned for `query.*` references
3. only those referenced query fields are taken from the incoming search query
4. source entity search runs
5. the result set is filtered in memory using the extracted query fields
6. matched entities are projected into generated view objects

So the current runtime line is:

- alias-based query selection
- key extraction from `query.*`
- post-search filtering

For projection-fixed predefined operations, current runtime also supports:

- projection-fixed load
- projection-fixed search

where the operation is statically bound to:

- `entity.view.summary.<Type>`
- `entity.view.detail.<Type>`

It is not yet:

- a full expression compiler
- a datastore-native predicate compiler

## Source Events / Rebuildable

Current runtime treats:

- `EVENTS`
- `REBUILDABLE`

as projection metadata.

They are exposed through:

- `meta.describe`
- `meta.schema`
- OpenAPI vendor extensions

They are not yet used to drive:

- event subscription wiring
- rebuild execution workflow

## Introspection Contract

Current introspection exposes, for each view definition:

- `name`
- `entityName`
- `viewNames`
- `queries`
- `sourceEvents`
- `rebuildable`

This appears in:

- describe projection
- schema projection
- OpenAPI vendor metadata

## Formal Runtime Type Contract

The formal runtime type contract is:

- full projection
  - `entity.view.<Type>`
- named projection
  - `view.<projection>.<Type>`

Examples:

- `entity.view.Person`
- `entity.view.Item`
- `entity.view.summary.Person`
- `entity.view.detail.Person`

This is the target runtime contract for projection typing.

## Predefined Projection Contract

The predefined projection set is:

- `all`
- `summary`
- `detail`

Their runtime type mapping is:

- `all`
  - `entity.view.<Type>`
- `summary`
  - `entity.view.summary.<Type>`
- `detail`
  - `entity.view.detail.<Type>`

`all` is implicit.

So runtime treats:

- `entity.view.<Type>`

as the canonical full projection.

## EntityObject / SimpleObject Contract

`EntityObject` / `SimpleObject` standard runtime support only needs to cover:

- `all`
- `summary`
- `detail`

Domain models may define additional projection names, but runtime support for those is driven by domain-defined view metadata rather than by the standard part set itself.

## Loader Compatibility

Runtime module loading now supports the canonical full-projection package:

- `entity.view.<Type>`

and also retains compatibility fallback for:

- `entity.entity.view.<Type>`

Named projection runtime classes:

- `view.<projection>.<Type>`

are implemented for the predefined projections:

- `summary`
- `detail`

Current custom named projections remain metadata/runtime aliases unless separately realized.

## Implemented Current Line

The current implemented runtime line is:

1. default entity-backed view bootstrap
2. canonical full-projection load/search for `entity.view.<Type>`
3. predefined projection bootstrap for:
   - `entity.view.summary.<Type>`
   - `entity.view.detail.<Type>`
4. projection-fixed load/search for predefined projections
5. named view alias registration
6. named query alias registration
7. metadata projection for queries/source-events/rebuildable

## Explicit Non-Goals

The current runtime does not yet provide:

1. datastore-native execution of `VIEW > QUERY.expression`
2. full end-to-end distinct runtime classes for arbitrary custom projection names
3. event-driven projection rebuild flow
4. projection materialization lifecycle beyond entity-backed read projection

## Next Line

The next natural runtime line would be one or more of:

- compiled query semantics for `VIEW > QUERY`
- full custom `view.<projection>.<Type>` runtime realization
- event-driven projection update flow
- rebuild execution semantics

These are later extensions, not part of the current runtime contract.
