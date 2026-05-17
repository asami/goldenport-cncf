# Knowledge Vocabulary and Source Model Inventory

This note records the Phase 25 KS-02 vocabulary and source-model inventory.
It is a planning note, not a runtime schema.

## Purpose

KS-02 identifies the CNCF/Textus concepts that participate in knowledge
structure and separates CNCF foundation vocabulary from driver/application
vocabulary.

The driver remains `textus-sie`
(`/Users/asami/src/dev2026/textus-semantic-integration-engine`). The separate
`textus-structured-knowledge` plan remains a parallel 9.5 development plan and
does not replace the Phase 25 driver.

## CNCF Baseline Vocabulary

Terms already present in CNCF and Phase 20:

- `SimpleEntity`: store-backed entity baseline for master/runtime records.
- Entity identity: the canonical CNCF identity for stored entities.
- Descriptive/content/lifecycle attributes: reusable entity metadata that may
  become knowledge evidence or display context.
- Association: external relationship storage between entities.
- `Tag`: CNCF builtin master data backed by `SimpleEntity`.
- `TagSpace`: boundary for independent Tag trees and runtime effective Tag
  selection.
- Tag strict tree: the Phase 20 hierarchy contract.
- `TagAttachment`: Association-backed Entity-to-Tag link.
- Component/Subsystem context: runtime context that can affect effective
  TagSpaces and later knowledge projections.

These terms remain valid. Phase 25 extends around them; it does not redefine
Phase 20 Tag tree semantics.

## `textus-sie` Driver Vocabulary

Terms from the SIE driver surface:

- source: registered input content or knowledge input handled by SIE.
- document: indexed source content used by retrieval.
- chunk: segmented document unit for vector retrieval.
- RDF knowledge input: graph-shaped knowledge supplied to SIE.
- RDF DB: structured graph store accessed through RDF DB SPI.
- Vector DB: semantic vector store accessed through Vector DB SPI.
- embedding: application/provider behavior that creates vector
  representation.
- semantic query: natural-language query resolved through retrieval.
- explanation: entity-focused retrieval/explanation output.
- MCP-facing tool surface: `Mcp.initialize`, `Mcp.listTools`, and
  `Mcp.callTool` exposed through CNCF publication/runtime mechanisms.

These terms are driver context for CNCF. Provider implementation and retrieval
ranking remain SIE/application/provider-owned.

## CNCF Foundation Candidates

Candidate vocabulary to raise into CNCF foundation work:

- knowledge source: origin of knowledge content, facts, or projected entity
  data.
- knowledge node: graph-level projection of an entity, tag, concept, source,
  or external subject.
- knowledge relationship: typed link between knowledge nodes.
- evidence/source reference: trace back to content, entity record, source
  document, row/version, operation, or external URI.
- provenance: ownership, origin, lifecycle, confidence, and generation context
  for knowledge facts.
- external identifier: URI or provider-specific subject identifier connected
  to CNCF identity without assuming equality.
- query/projection boundary: generic CNCF surface for traversal, lookup,
  explanation support, and admin/debug projection.

These candidates are not finalized runtime types in KS-02. Concrete model
shape belongs to KS-04, and query/projection shape belongs to KS-05.

## Application / Provider Owned Vocabulary

Terms that should not become CNCF-core behavior in Phase 25:

- Fuseki-specific RDF behavior.
- Chroma-specific Vector DB behavior.
- embedding implementation and model selection.
- hybrid retrieval ranking.
- SIE-specific MCP tool semantics.
- provider configuration details and deployment descriptors.
- RDF import/export implementation details.
- vector collection lifecycle beyond generic visibility/projection needs.

CNCF can expose generic boundaries and diagnostics for these behaviors, but the
behaviors themselves remain in the driver/application/provider layer.

## Reserved and Overloaded Terms

- `Tag` means the Phase 20 strict-tree CNCF Tag unless KS-03 explicitly defines
  a graph extension or projection.
- `KnowledgeNode` is reserved for graph-level projection and should not be used
  as a synonym for every Entity.
- `source` must be qualified when ambiguity matters:
  - content source;
  - knowledge source;
  - provider source;
  - diagnostic source.
- `relationship` should distinguish CNCF Association storage from future
  knowledge graph relationship projection.
- `evidence` is traceable support for a knowledge fact or projection. It is not
  the same as a retrieval result score.
- `provenance` records origin and generation context. It is not authorization
  policy.
- `external identifier` must not imply `Entity.id == RDF subject URI`.

## KS-02 Decisions

- CNCF core provides knowledge vocabulary, identity mapping boundaries, and
  query/projection boundaries.
- RDF DB, Vector DB, embedding, retrieval ranking, and MCP transport remain
  driver/application/provider responsibilities.
- `Tag` strict tree remains valid. DAG/polyhierarchy work starts in KS-03 as a
  Tag graph extension and coexistence design.
- Knowledge node, relationship, evidence, and provenance concrete model design
  starts in KS-04.
- Query/projection surface design starts in KS-05.
