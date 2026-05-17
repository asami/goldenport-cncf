# Knowledge Query and Projection Surfaces

This note records the Phase 25 KS-05 query/projection surface decision. It is a
planning note, not a runtime schema or storage implementation.

## Purpose

KS-05 defines the CNCF boundary for knowledge lookup, traversal, projection,
and inspection before `textus-sie` driver integration starts.

The boundary must keep `TagSpace` and `KnowledgeSpace` separate:

- `TagSpace` owns strict tree Tags, paths, resident `TagTree` lookup,
  descendant expansion, and existing `TagAttachment` search behavior.
- `KnowledgeSpace` owns graph-level lookup, traversal, projection,
  explanation inputs, evidence/provenance lookup, and WorkingSet inspection.

## Query Surface

Candidate CNCF knowledge queries:

- node lookup by CNCF Entity id, Tag reference, external identifier, source
  reference, document reference, or chunk reference.
- knowledge lookup for a business Entity without requiring that Entity to be
  represented as the same KnowledgeNode id.
- Entity-to-knowledge binding lookup by Entity id, entity kind/name, Entity
  version/source reference, Association-backed link, or external identifier
  mapping.
- relationship traversal from a start node by direction, edge kind, depth, and
  limit.
- evidence/provenance lookup from a node, relationship, or fact to source
  material and generation context.
- explanation query that returns the relevant node, relationship, evidence,
  provenance, and external identifier mappings for a target.
- WorkingSet inspection for node count, relationship count, projection source
  status, and stale/error state.

These are CNCF-visible boundary shapes. They do not require a specific RDF DB,
Vector DB, graph store, or search index implementation.

## Projection Surface

Projection surfaces:

- strict tree navigation projection from `TagSpace`.
- optional graph projection from `KnowledgeSpace`.
- Entity detail projection that can attach related knowledge nodes,
  relationships, evidence, provenance, and external identifiers to an ordinary
  business Entity.
- admin/debug projection for nodes, relationships, evidence, provenance, and
  external identifier mappings.
- driver projection from application/provider data into CNCF-visible knowledge
  records.
- MCP-facing projection through CNCF service/operation publication surfaces.

`tag_search_entities` remains the default strict-tree descendant search. Graph
edges may be used for optional expansion only after a later runtime slice
defines explicit behavior.

## `textus-sie` Driver Mapping

KS-06 should validate this boundary against `textus-sie`:

- `SemanticRetrieval.query` maps to a knowledge explanation/search projection.
- `SemanticRetrieval.explain` maps to node-centric explanation with evidence
  and provenance.
- `SemanticRetrieval.status` maps to provider/WorkingSet readiness projection.
- SIE MCP services use CNCF operation/service publication and do not require a
  SIE-specific MCP runtime path in CNCF core.

RDF DB, Vector DB, embedding, hybrid ranking, provider configuration, Fuseki,
and Chroma remain SIE/application/provider-owned.

## KS-05 Decisions

- Do not change `TagSpace`, `TagTree`, `TagAttachment`, or
  `tag_search_entities` default behavior.
- Treat `KnowledgeSpace` as the future CNCF boundary for graph lookup,
  traversal, projection, explanation, and WorkingSet inspection.
- Treat Entity-to-knowledge binding as a first-class projection use case:
  Entity records remain authoritative business objects, while KnowledgeSpace
  supplies related knowledge.
- Keep query/projection surfaces storage-neutral.
- Keep graph traversal and search expansion opt-in until a later runtime slice
  implements behavior.
- Send concrete driver validation to KS-06.
