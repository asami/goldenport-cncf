# Knowledge Structure Opening

This note is the Phase 25 KS-01 planning entry point for CNCF knowledge
structure work.

## Purpose

Phase 25 starts from the Phase 20 Tag baseline and prepares the next knowledge
structure slices. The driver is `textus-sie`, whose repository is
`/Users/asami/src/dev2026/textus-semantic-integration-engine`.

`textus-sie` already has RDF DB and Vector DB provider boundaries. CNCF should
use those boundaries as driver input, not copy SIE-specific provider behavior
into CNCF core.

## Phase 20 Baseline

Phase 20 closed with these rules:

- `Tag` is CNCF builtin master data backed by `SimpleEntity`.
- Tag hierarchy is a strict tree.
- Tag records are grouped by `TagSpace`.
- Runtime lookup uses resident Tag trees for effective TagSpaces.
- Entity-to-Tag links are Association-backed through TagAttachment and remain
  outside the tagged Entity record.
- Parent Tag search expands descendants by default.
- RDF store integration, external knowledge graph integration, and
  DAG/polyhierarchy Tag graphs were explicitly deferred.

Phase 25 must not weaken this baseline by accident. Tag tree behavior stays
valid until a later slice defines coexistence, migration, and projection rules.

## Driver Boundary

`textus-sie` is the Phase 25 application driver. Its current roadmap is Phase 3:
Legacy SIE Provider Integration.

Driver capabilities that matter to CNCF knowledge structure:

- RDF DB SPI for graph lookup, concept search, entity explanation, and provider
  health.
- Vector DB SPI for collection management, document/chunk upsert, semantic
  search, rebuild behavior, and provider health.
- Fuseki and Chroma provider implementation boundaries.
- `SemanticRetrieval.query`, `SemanticRetrieval.explain`, and
  `SemanticRetrieval.status` routed through provider contracts.

CNCF core owns generic knowledge model, query, and projection boundaries.
SIE owns provider implementations and retrieval-specific behavior.

## Development Relationship

Phase 25 develops `textus-sie` and CNCF together.

`textus-sie` is the driver application that reveals concrete knowledge
structure needs. CNCF provides the reusable foundation capabilities that SIE
requires, but CNCF does not absorb SIE-specific retrieval or provider behavior.

The intended loop is:

1. Build or refine a `textus-sie` capability.
2. Identify the foundation behavior that should be reusable.
3. Implement that behavior in CNCF as a generic model, boundary, query, or
   projection surface.
4. Validate it through `textus-sie`.

This keeps SIE concrete and executable while preventing CNCF core from becoming
SIE-specific.

## `textus-sie` Driver Function

`textus-sie` provides a semantic integration function:

- RDF knowledge input is stored and managed in RDF DB.
- Resources described by RDF are embedded and managed in Vector DB.
- Retrieval combines RDF DB structured graph lookup and Vector DB semantic
  search.
- Results are exposed as semantic query/explanation output.
- The capability is exposed to generative AI through MCP using CNCF-provided
  publication/runtime surfaces.

For Phase 25, CNCF treats this as driver context. CNCF core should define
generic knowledge model, source model, query, and projection boundaries. CNCF
core should not directly own RDF DB, Vector DB, Fuseki, Chroma, embedding, or
SIE-specific MCP behavior.

## KS-01 Decisions

- The first implementation direction is not to add an RDF store to CNCF core.
- The next slice, KS-02, inventories vocabulary and source models before
  adding runtime types.
- DAG/polyhierarchy is treated as a Tag graph extension and belongs to KS-03.
- Knowledge node, relationship, evidence/source, and provenance records belong
  to KS-04.
- Query/projection surfaces belong to KS-05.
- `textus-sie` integration belongs to KS-06 and must not conflict with SIE
  Phase 3 provider migration.
- `textus-structured-knowledge` remains a separate parallel 9.5 development
  plan and does not replace the Phase 25 `textus-sie` driver.

## Guardrails

- Do not hardcode `textus-sie` into CNCF core.
- Do not make CNCF depend directly on Fuseki, Chroma, or a specific RDF/vector
  database.
- Do not implement an SIE-specific MCP server path outside CNCF
  publication/runtime mechanisms.
- Do not replace strict Tag trees without a coexistence and migration plan.
- Do not use search/index implementation as a substitute for knowledge model
  boundaries.
