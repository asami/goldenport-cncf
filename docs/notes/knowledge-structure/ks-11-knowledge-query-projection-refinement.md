# KS-11 Knowledge Query / Projection Refinement

Date: 2026-05-18

KS-11 refines the CNCF-side read surfaces for the KS-10 operational knowledge
model. The slice keeps `KnowledgeSpace` component-owned and read-only from the
admin/query perspective; provider-backed RDF DB / Vector DB loading remains
KS-12.

## Query Surface

`KnowledgeSpace.query` returns a small component-local query facade.

Supported v1 lookup targets:

- `KnowledgeNodeId`
- `KnowledgeRelationshipId`
- `KnowledgeFrameId`
- `KnowledgeFactId`
- `RdfNodeName`
- `ExternalKnowledgeIdentifier`
- `KnowledgeEntityBinding`
- `KnowledgeTagBinding`
- semantic type `(system, name)` across nodes and relationships

The query facade returns `KnowledgeSpaceQueryResult`, a typed result container
with vectors for nodes, relationships, frames, facts, evidence, and provenance.
Cross-component lookup remains a projection-layer aggregation concern and must
return component-qualified results.

## Projection Surface

Projection records continue to use `KnowledgeRecordCodec`, but the expected
shape is now the KS-10 delegated model:

- node identity, presentation, semantics, structure, sources, bindings,
  similarity, operations, and attributes
- relationship kind, RDF predicate, semantic types, evidence, provenance,
  qualifiers, similarity, and attributes
- frame origin, route, provider, purpose, query, members, facts, evidence, and
  provenance
- fact kind, subject, relationship, predicate, value, evidence, provenance, and
  attributes

Raw RDF triples, raw vector payload bodies, and source document bodies are not
part of KS-11 projection. Similarity is exposed as operational metadata such as
method, model, metric, provider, collection, and payload reference.

## Admin Surface

System knowledge admin pages remain compact operator/debug projections:

- system index shows component-level status and counts for nodes,
  relationships, frames, facts, evidence, provenance, Entity bindings, and Tag
  bindings
- component page shows bounded, deterministic previews for nodes,
  relationships, frames, and facts
- node page shows structured sections for identity, presentation, semantics,
  structure, Entity/Tag bindings, similarity, operations, frames, facts,
  relationships, evidence, and provenance
- relationship rows link source and target node pages when rendered inside a
  component context

The admin surface is not a raw RDF browser and not a Vector DB payload viewer.

## Deferred

- Provider-backed RDF DB / Vector DB loading: KS-12.
- CNCF MCP end-to-end validation for `textus-sie`: KS-13.
- Public component operations for knowledge query are not introduced in KS-11.
