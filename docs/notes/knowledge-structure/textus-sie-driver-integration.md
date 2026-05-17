# `textus-sie` Driver Integration

This note records the Phase 25 KS-06 driver validation decision. It is a
planning/validation note, not a runtime schema or implementation.

## Purpose

KS-06 validates the CNCF knowledge-structure boundary through the
`textus-sie` driver:

- `TagSpace` remains the strict-tree Tag boundary.
- `KnowledgeSpace` remains the future CNCF boundary for graph lookup,
  traversal, projection, explanation, evidence/provenance lookup, and
  WorkingSet inspection.
- `textus-sie` remains the application driver, not a CNCF-core special case.

## SemanticRetrieval Mapping

`SemanticRetrieval.query` maps to a KnowledgeSpace search/explanation
projection:

- input is SIE-owned natural-language retrieval input;
- output remains SIE-owned retrieval chunks for now;
- CNCF uses the operation as validation that a future KnowledgeSpace projection
  needs search result, evidence, and provenance visibility.

`SemanticRetrieval.explain` maps to node-centric explanation:

- `entityType` and `id` identify the explanation target from the SIE side;
- RDF graph context and Vector DB evidence are projected by SIE;
- CNCF should expose the generic node/evidence/provenance boundary later
  without assuming `Entity.id == RDF subject URI`.

`SemanticRetrieval.status` maps to readiness projection:

- RDF DB provider readiness;
- Vector DB provider readiness;
- embedding/runtime status;
- future KnowledgeSpace WorkingSet status when the CNCF runtime model exists.

## MCP Surface

SIE MCP-facing operations validate CNCF service/operation publication:

- `Mcp.initialize` returns server metadata and capability hints.
- `Mcp.listTools` returns SIE-owned tool descriptors.
- `Mcp.callTool` dispatches tool calls to SIE semantic integration behavior.

CNCF core should not add a SIE-specific MCP runtime path. MCP publication should
use CNCF service/operation publication and adapter boundaries.

## Responsibility Split

CNCF owns:

- KnowledgeSpace boundary design.
- query/projection surface shape.
- service/operation publication.
- admin/debug projection requirements.
- structured status visibility.

SIE owns:

- RDF DB SPI and provider behavior.
- Vector DB SPI and provider behavior.
- Fuseki and Chroma provider integration.
- embedding implementation.
- hybrid retrieval ranking.
- MCP tool semantics.
- `SemanticQueryResponse.results: SemanticChunk*` as retrieval output.

`SemanticChunk` is not the CNCF `KnowledgeNode` or `KnowledgeRelationship`
runtime schema.

## KS-06 Decisions

- Do not change `TagSpace`, `TagTree`, `TagAttachment`, or
  `tag_search_entities` default behavior.
- Do not introduce SIE provider/runtime implementation into CNCF core.
- Keep KnowledgeSpace runtime types, storage, and query implementation as
  future work.
- Treat SIE output as driver evidence for the future CNCF knowledge projection
  boundary, not as the final CNCF schema.
- KS-07 supersedes the earlier docs-only handoff: remaining implementation
  work continues inside Phase 25 through KS-08 and later slices until the
  `textus-sie` end-to-end driver path is validated.
