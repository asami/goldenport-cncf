# KS-12 textus-sie Provider Runtime Realization

Date: 2026-05-18

KS-12 connects the `textus-sie` driver runtime to CNCF's component-owned
`KnowledgeSpace` boundary. CNCF remains provider-neutral: Fuseki, Chroma,
embedding behavior, hybrid ranking, and MCP tool semantics stay in
`textus-sie` / application code.

## Runtime Shape

`SemanticRetrieval.query` now has three observable projections:

- SIE retrieval results as `SemanticChunk`.
- RDF-oriented graph results as `RdfConcept`.
- CNCF knowledge results as a `KnowledgeFrame` / `KnowledgeWorkingSetSnapshot`
  projection.

The Knowledge projection is not a raw RDF or vector payload. It materializes:

- `KnowledgeFrame(kind = retrieval-result | explanation)`
- `KnowledgeNode(category = document | chunk | concept)`
- `KnowledgeRelationship(kind = has-part)` for document-to-chunk structure
- `KnowledgeEvidence`
- `KnowledgeFact`
- `KnowledgeProvenance`

The response can either register this snapshot into the component
`KnowledgeSpace` or return the compact `KnowledgeFrame` payload directly.

## Registration and Direct Return

Both paths are intentional:

- `registerKnowledgeSpace=true` registers the materialized snapshot into the
  component-owned `KnowledgeSpace`.
- `registerKnowledgeSpace=false` leaves the component `KnowledgeSpace`
  untouched and returns frame metadata/results for the caller to consume.
- `includeKnowledgeFrame=true` includes the compact CNCF KnowledgeFrame
  projection in the operation response.
- `includeRdf=true` includes RDF-oriented graph results.

This supports two caller styles:

- domain/application logic that wants to persist a retrieval/explanation
  WorkingSet in `KnowledgeSpace` and traverse it later;
- clients that want a one-shot RDF or KnowledgeFrame result without mutating
  the component-local WorkingSet.

## Provider Boundary

The SIE provider SPI returns structured failure for mandatory query/explain
provider calls. Runtime `status` remains degraded/non-fatal and reports graph,
vector, embedding, and `KnowledgeSpace` readiness.

The Fuseki and Chroma providers are implemented in `textus-sie` only. CNCF core
does not depend on either service API.

## CNCF Validation

CNCF validates SIE-style snapshots at the `KnowledgeWorkingSet` boundary:

- `KnowledgeFrameInputRoute.SieRetrieval`
- document/chunk `has-part` relationships
- evidence/fact/provenance references
- canonical node projection from relationship/fact data

MCP end-to-end discovery/invocation remains KS-13.
