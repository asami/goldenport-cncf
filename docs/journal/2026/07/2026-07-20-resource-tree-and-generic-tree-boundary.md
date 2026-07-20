# ResourceTree and Generic Tree Boundary — 2026-07-20

status=decision-recorded
phase=43
updated_at=2026-07-20
tag=resource-tree, tree-ir, runtime-capability, bounded-query

## Context

During Phase 43 closure, a compatibility question arose: CNCF and its core
libraries already had several tree-shaped models before the bounded
ResourceTree query was introduced. The review asked whether the new
`ResourceTreeQuery` and related models duplicated or conflicted with those
existing facilities.

The relevant existing models were found to have different purposes:

- `org.goldenport.tree.Tree[A]` in simplemodeling-lib is an immutable,
  execution-oriented structural IR with directory and leaf nodes,
  order-preserving traversal, lookup, and value transformation.
- CNCF Docker command support uses `Tree[Bag]` as an already materialized
  in-memory file tree.
- `TagTree`, `JobTraceTree`, and introspection `TreeModel` are
  domain-specific read models.
- CNCF `ResourceTreeAccess` is a runtime-owned capability that admits a named
  physical resource through logical identity, limits, and structured failure
  semantics without exposing the physical root.

The common word "tree" therefore described structure in some places and a
bounded resource namespace/capability in another. There was no immediate
runtime conflict, but the relationship had not been stated explicitly in the
Phase 43 design and specification.

## Decision

The models remain separate and are related as follows:

```text
ResourceTreeAccess
  = runtime capability and provider/admission boundary

ResourceTreeSnapshot
  = complete, admitted, immutable resource entry set

ResourceTreeQueryResult
  = sparse selector result within an admitted tree

Tree[A]
  = generic structural IR used after admission when tree processing is useful
```

`ResourceTree*` does not replace `Tree[A]`. Conversely, constructing a generic
`Tree[A]` does not prove provider admission, authorization, limits, or snapshot
completeness.

Runtime-owned code may project a complete admitted `ResourceTreeSnapshot` into
a generic `Tree[A]` for structural processing or materialization. This is a
one-way loss of ResourceTree admission semantics. A generic tree cannot be
accepted back as an admitted ResourceTree without normal ResourceTree
validation and limit checks.

A `ResourceTreeQueryResult` must not be implicitly converted into or described
as a complete tree. It contains only selected entries. Missing parents,
siblings, and unrelated entries mean "not returned by this selector", not
"absent from the source tree".

## Why The Flat Resource Representation Remains

ResourceTree snapshots and query results use validated logical relative paths
and deterministic entry ordering rather than exposing generic directory and
leaf nodes. This representation directly supports:

- admission and limit revalidation;
- deterministic sparse query results;
- bounded per-entry and aggregate byte accounting;
- provider-independent serialization; and
- diagnostics that omit physical roots and unselected content.

Exposing generic tree traversal at the component capability boundary would
blur these properties and could make a sparse query result appear complete.

## Remaining Integration Points

The review identified non-blocking areas for future consolidation:

- ResourceTree relative-path validation and the core `PathName` model should
  remain semantically aligned; a future adapter must not introduce a second,
  weaker path interpretation.
- Process Execution currently materializes admitted snapshot entries directly,
  while Docker command support uses `Tree[Bag]`. A runtime-owned conversion may
  reduce implementation duplication if a concrete use case justifies it.
- `ResourceTreeEntry` and core `TreeEntry` have similar names but different
  roles: the former is an admitted resource payload with a logical path, while
  the latter is a structural parent-child entry.

These points do not require replacing the Phase 43 query model. Any future
adapter must preserve the capability boundary and must not make generic
`Tree[A]` a component-visible filesystem or admission bypass.

## Normative Promotion

The selected boundary was promoted to:

- `docs/design/component-runtime-boundary-capabilities.md`, section
  "Relationship To The Generic Tree IR";
- `docs/spec/component-runtime-boundary-capabilities.md`, section
  "Generic Tree IR Compatibility (R6b)"; and
- the Phase 43 selected contract and checklist.

This journal entry records the rationale and investigation only. The design
and specification documents remain authoritative.
