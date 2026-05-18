# Phase 25 Implementation Rebaseline

This note records the KS-07 decision to keep Phase 25 open until the
`textus-sie` driver is materially realized on CNCF.

## Rebaseline

KS-01 through KS-06 established the knowledge-structure vocabulary, model
boundary, query/projection boundary, and `textus-sie` driver mapping. That is
not sufficient to close Phase 25.

Phase 25 closes only after `textus-sie` can run on CNCF with a concrete
knowledge-structure path:

- RDF DB backed knowledge input.
- Vector DB backed resource/document indexing.
- semantic retrieval through `SemanticRetrieval.query`.
- entity explanation through `SemanticRetrieval.explain`.
- provider and KnowledgeSpace readiness through `SemanticRetrieval.status`.
- MCP discovery and tool execution through CNCF publication/runtime surfaces.

## Implementation Direction

CNCF owns the generic platform boundary:

- `KnowledgeSpace` model/runtime skeleton.
- memory-resident knowledge WorkingSet.
- storage-neutral query/projection/admin surface.
- operation/service publication and MCP exposure.

`textus-sie` owns application/provider behavior:

- RDF DB SPI and Fuseki provider behavior.
- Vector DB SPI and Chroma provider behavior.
- embedding and hybrid ranking.
- semantic retrieval semantics.
- SIE-specific MCP tool semantics.

`TagSpace` remains separate. Strict Tag tree behavior and
`tag_search_entities` default search must not be changed by the KnowledgeSpace
implementation.

## Revised Active Stack

- KS-07: Phase 25 implementation rebaseline.
- KS-08: CNCF `KnowledgeSpace` core model and WorkingSet skeleton.
- KS-09: CNCF knowledge query/projection/admin surface.
- KS-10: `textus-sie` provider/runtime realization.
- KS-11: CNCF MCP boundary validation for `textus-sie`.
- KS-12: Phase 25 verification and closure.

## Closure Criterion

Phase 25 closure requires an end-to-end driver path:

1. Start CNCF with `textus-sie`.
2. Register or load RDF-backed knowledge.
3. Index at least one RDF-described resource/document into Vector DB.
4. Run `SemanticRetrieval.query` and receive combined retrieval output.
5. Run `SemanticRetrieval.explain` and receive graph/evidence context.
6. Inspect KnowledgeSpace/provider readiness from CNCF surfaces.
7. Discover and invoke the relevant SIE operations through CNCF MCP.
