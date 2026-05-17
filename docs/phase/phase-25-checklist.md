# Phase 25 — Knowledge Structure Follow-ups Checklist

This document contains detailed task tracking and decisions for Phase 25.
It complements the summary-level phase document (`phase-25.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-25.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## KS-01: Knowledge Structure Opening and `textus-sie` Driver Scope

Status: DONE

### Objective

Open Phase 25 and turn the 9.5 future follow-up item into an executable
knowledge-structure work stream. Use `textus-sie` as the concrete driver, while
keeping CNCF core generic.

### Detailed Tasks

- [x] Confirm the `textus-sie` repository/location and current development
      state.
- [x] Inventory the knowledge-structure requirements that `textus-sie` needs
      from CNCF.
- [x] Decide whether the first implementation slice starts from Tag graph
      extension, knowledge node/relationship modeling, or RDF/external graph
      boundary design.
- [x] Record the first normative design note for Phase 25.
- [x] Keep application-specific `textus-sie` details out of CNCF core.
- [x] Update phase/status documents after the opening scope is fixed.

### Guardrails

- Do not make `textus-sie` a hardcoded CNCF component.
- Do not replace Phase 20 strict Tag tree behavior without an explicit
  compatibility and migration plan.
- Do not introduce an RDF store or external graph database dependency before
  the CNCF boundary is defined.
- Do not use search/index implementation as a substitute for the knowledge
  structure model.

### Expected Output

- Phase 25 is active and visible from the strategy document.
- `textus-sie` is recorded as the driver.
- The first implementation slice can be planned without reopening the high-level
  phase boundary.

### Completion Notes

- `textus-sie` is fixed as
  `/Users/asami/src/dev2026/textus-semantic-integration-engine`.
- The driver enters from SIE Phase 3: Legacy SIE Provider Integration.
- Relevant SIE boundaries are RDF DB SPI, Vector DB SPI, Fuseki/Chroma provider
  boundaries, and `SemanticRetrieval.query` / `explain` / `status`.
- CNCF core owns generic knowledge model, query, and projection boundaries; SIE
  owns provider implementations and retrieval-specific behavior.
- The first implementation direction is boundary design and vocabulary/source
  inventory, not adding an RDF store to CNCF core.
- The Phase 25 knowledge-structure opening note is
  `docs/notes/knowledge-structure/knowledge-structure-opening.md`.

---

## KS-02: Knowledge Vocabulary / Source Model Inventory

Status: DONE

### Objective

Inventory existing CNCF/Textus concepts that participate in knowledge
structure, including Tag, category, topic, entity references, content source,
evidence, provenance, and external identifiers.

### Initial Tasks

- [x] Review Phase 20 Tag tree decisions and current Tag/runtime behavior.
- [x] Review existing Textus/CMS metadata that already behaves like knowledge
      structure.
- [x] Identify vocabulary that belongs in CNCF core versus application-owned
      vocabularies.
- [x] Record reserved terms and terms that must not be overloaded.

### Completion Notes

- Vocabulary and source-model inventory is recorded in
  `docs/notes/knowledge-structure/knowledge-vocabulary-source-inventory.md`.
- CNCF baseline vocabulary includes `SimpleEntity`, Entity identity,
  Association, `Tag`, `TagSpace`, strict Tag tree, `TagAttachment`, and
  Component/Subsystem context.
- `textus-sie` driver vocabulary includes source, document, chunk, RDF
  knowledge input, RDF DB, Vector DB, embedding, semantic query, explanation,
  and MCP-facing tool surface.
- CNCF foundation candidates are knowledge source, knowledge node, knowledge
  relationship, evidence/source reference, provenance, external identifier, and
  query/projection boundary.
- Fuseki/Chroma behavior, embedding implementation, hybrid retrieval ranking,
  SIE-specific MCP tool semantics, and provider configuration remain
  driver/application/provider-owned.
- `Tag` remains the Phase 20 strict-tree baseline. DAG/polyhierarchy starts in
  KS-03.

---

## KS-03: DAG / Polyhierarchy Tag Graph Model

Status: DONE

### Objective

Define how CNCF moves beyond strict tree tags when the same knowledge item needs
multiple parents, cross-links, aliases, or graph traversal.

### Initial Tasks

- [x] Define graph edge kinds and constraints.
- [x] Define cycle policy.
- [x] Define projection back to strict-tree navigation where needed.
- [x] Define migration/coexistence with Phase 20 Tag tree behavior.

### Completion Notes

- DAG/polyhierarchy Tag graph policy is recorded in
  `docs/notes/knowledge-structure/tag-graph-polyhierarchy-model.md`.
- `Tag.parentId`, `TagTree`, `TagAttachment`, and existing
  `tag_search_entities` default behavior remain unchanged.
- Canonical Tag hierarchy remains the Phase 20 strict tree.
- DAG/polyhierarchy is treated as a separate graph extension/projection layer.
- Candidate edge kinds include `additional-parent`, `related`, `alias`,
  `same-as`, `broader`, `narrower`, and `see-also`.
- Strict tree cycles remain forbidden. Graph-extension cycle policy is
  edge-kind specific and belongs to the future graph relationship layer.
- Concrete graph relationship storage/model moves to KS-04.
- Search/query behavior changes move to KS-05.

---

## KS-04: Knowledge Node / Relationship / Evidence Model

Status: DONE

### Objective

Define the reusable knowledge model for nodes, relationships, source/evidence
references, provenance, confidence, and external IDs.

### Initial Tasks

- [x] Define minimum node and relationship records.
- [x] Define evidence/source reference shape.
- [x] Define provenance and ownership fields.
- [x] Define how external graph IDs and CNCF entity IDs relate.

### Completion Notes

- Knowledge node, relationship, evidence, provenance, and external identifier
  mapping decisions are recorded in
  `docs/notes/knowledge-structure/knowledge-node-relationship-evidence-model.md`.
- `KnowledgeNode` is a graph-level projection boundary for Entity, Tag,
  concept, source, document/chunk, or external subject. It is not a synonym for
  every Entity.
- Entity-to-knowledge binding is a separate first-class use case: an ordinary
  business Entity can remain in EntitySpace / EntityStore while related
  knowledge nodes, relationships, evidence, provenance, and external
  identifiers are reached through KnowledgeSpace.
- `KnowledgeRelationship` is a typed graph edge between knowledge nodes. It is
  distinct from CNCF `Association`, though future implementation may use the
  Association foundation for some storage paths.
- Evidence traces a node, relationship, or fact back to Entity records, DB
  rows/versions, source documents/chunks, operation context, external URIs, or
  projected RDF statements.
- Provenance records origin, owner, generated-by, lifecycle/state, confidence,
  and generation/observation context. It is not authorization policy.
- External identifier mapping is explicit. Do not assume
  `Entity.id == RDF subject URI`.
- Query/traversal/projection behavior moves to KS-05.

---

## KS-05: Query and Projection Surfaces

Status: DONE

### Objective

Define CNCF query/projection surfaces for knowledge traversal, lookup,
navigation, and admin/debug inspection.

### Initial Tasks

- [x] Define query shapes needed by Web/App tier consumers.
- [x] Define admin/debug projections for graph structure and source evidence.
- [x] Define API output boundaries without exposing storage-specific details.
- [x] Record search/index boundaries if lookup requires a later index service.

### Completion Notes

- Query/projection surface decisions are recorded in
  `docs/notes/knowledge-structure/knowledge-query-projection-surfaces.md`.
- `TagSpace` remains the boundary for strict tree navigation, descendant
  expansion, resident `TagTree` lookup, and `TagAttachment` default search.
- `KnowledgeSpace` is the planned boundary for graph lookup, traversal,
  projection, explanation, evidence/provenance lookup, and WorkingSet
  inspection.
- `tag_search_entities` default behavior remains unchanged.
- Graph traversal and search expansion remain opt-in future runtime behavior.
- KS-06 validates this boundary through `textus-sie`
  `SemanticRetrieval.query` / `explain` / `status` and MCP-facing services.

---

## KS-06: `textus-sie` Driver Integration

Status: DONE

### Objective

Validate the selected knowledge-structure model against `textus-sie` without
hardcoding application semantics into CNCF.

### Initial Tasks

- [x] Add or update the driver configuration/model in `textus-sie`.
- [x] Exercise representative knowledge graph/tag/evidence workflows.
- [x] Confirm Web/API/Admin projections are sufficient for the driver.
- [x] Record remaining application-owned work separately from CNCF core work.

### Completion Notes

- CNCF driver validation is recorded in
  `docs/notes/knowledge-structure/textus-sie-driver-integration.md`.
- `textus-sie` driver context is recorded in
  `/Users/asami/src/dev2026/textus-semantic-integration-engine/docs/notes/cncf-knowledge-structure-driver.md`.
- `SemanticRetrieval.query` validates search/explanation projection needs.
- `SemanticRetrieval.explain` validates node-centric evidence/provenance
  projection needs.
- `SemanticRetrieval.status` validates provider readiness and future
  KnowledgeSpace WorkingSet readiness projection needs.
- `Mcp.initialize`, `Mcp.listTools`, and `Mcp.callTool` validate CNCF
  service/operation publication for MCP-facing behavior.
- SIE-owned provider/runtime behavior remains outside CNCF core: RDF DB SPI,
  Vector DB SPI, Fuseki/Chroma providers, embedding, hybrid ranking, and MCP
  tool semantics.
- `SemanticQueryResponse.results: SemanticChunk*` remains SIE retrieval output,
  not the CNCF `KnowledgeNode` / `KnowledgeRelationship` runtime schema.

---

## KS-07: Phase 25 Implementation Rebaseline

Status: DONE

### Objective

Rebaseline Phase 25 so closure requires concrete `textus-sie` realization on
CNCF, not only design notes.

### Initial Tasks

- [x] Record the revised Phase 25 active stack.
- [x] Make `textus-sie` realization the Phase 25 closure criterion.
- [x] Keep CNCF core/provider responsibility split explicit.
- [x] Record the end-to-end acceptance path.

### Completion Notes

- Phase 25 implementation rebaseline is recorded in
  `docs/notes/knowledge-structure/phase-25-implementation-rebaseline.md`.
- Phase 25 now closes only after `textus-sie` is materially realized on CNCF:
  RDF DB, Vector DB, semantic retrieval, status, and MCP publication paths.
- KS-08 through KS-12 replace the premature closure path.

---

## KS-08: CNCF `KnowledgeSpace` Core Model and WorkingSet Skeleton

Status: DONE

### Objective

Add a minimal CNCF `KnowledgeSpace` runtime skeleton with memory-resident
WorkingSet support.

### Initial Tasks

- [x] Add storage-neutral knowledge model types.
- [x] Add a memory-resident knowledge WorkingSet.
- [x] Add basic readiness/count/status behavior.
- [x] Keep `TagSpace`, `TagTree`, `TagAttachment`, and `tag_search_entities`
      unchanged.

### Completion Notes

- Added `org.goldenport.cncf.knowledge` with storage-neutral model types for
  nodes, relationships, evidence, provenance, source references, and external
  identifiers.
- Added memory-resident `KnowledgeWorkingSet` with id, relationship,
  evidence/provenance, and external identifier indexes.
- Added component-owned `KnowledgeSpace`; it is managed like `EntitySpace`,
  `AggregateSpace`, and `ViewSpace`.
- `Subsystem` does not own a global `KnowledgeSpace`; cross-component
  aggregation belongs to KS-09.
- Existing Tag strict-tree behavior remains unchanged and was covered by the
  focused Tag spec.

---

## KS-09: CNCF Knowledge Query / Projection / Admin Surface

Status: DONE

### Objective

Expose minimal CNCF knowledge lookup, traversal, status, and admin/debug
projection surfaces.

### Initial Tasks

- [x] Add lookup/traversal/explanation projection interfaces.
- [x] Add Entity-to-knowledge binding projection for Entity detail and
      business-logic use cases.
- [x] Add KnowledgeSpace status/admin projection.
- [x] Add provider/projection source diagnostics.
- [x] Keep surfaces storage-neutral and component-generic.

### Completion Notes

- Added compact Record projection for knowledge nodes, relationships,
  evidence, provenance, status, and counts.
- Added component-local KnowledgeSpace projection and cross-component read-only
  aggregation.
- Added projection source diagnostics for the KS-09 component-owned in-memory
  snapshot surface. Provider-backed RDF/Vector diagnostics remain later
  provider/runtime work.
- Added canonical Entity-to-knowledge binding through
  `ExternalKnowledgeIdentifier.entity(entityName, entityId)`.
- Added system admin KnowledgeSpace pages for component status/counts,
  component node/relationship summaries, and node detail.
- Raw RDF, Vector DB payloads, source documents, embedding, ranking, and MCP
  tool semantics remain outside KS-09.

---

## KS-10: Knowledge Operational Model Hardening

Status: DONE

### Objective

Harden the CNCF operational knowledge model before connecting concrete
`textus-sie` RDF DB / Vector DB provider paths. KS-10 must keep model design
separate from provider integration so that SIE validation does not obscure
core model decisions.

### Initial Tasks

- [x] Use `docs/notes/knowledge-structure/ks-10-knowledge-operational-model-hardening.md`
      as the KS-10 implementation design input.
- [x] Replace string-valued node/relationship kinds with typed
      provisional `KnowledgeNodeCategory` / `KnowledgeRelationshipKind` or an equivalent
      extensible typed model.
- [x] Clarify `KnowledgeNodeId` as a CNCF-internal node id, distinct from RDF
      subject URI, Entity id, Tag id, and provider ids.
- [x] Keep RDF subject URI, Entity id, Tag id, and provider ids in explicit
      external identifier mappings.
- [x] Replace display `label: Option[String]` with an I18n/localizable label
      shape suitable for RDF language-tagged labels.
- [x] Introduce `KnowledgeFrame` as the purpose/focus/source/query unit inside
      `KnowledgeWorkingSet`, including frame origin.
- [x] Split `KnowledgeNode` into SimpleEntity-style delegated value objects:
      identity, presentation, semantics, structure, sources, bindings,
      similarity, and operations.
- [x] Add similarity/distance metadata using meaning-level names; keep
      embedding/vector/index as implementation metadata and do not store raw
      vectors in `KnowledgeNode`.
- [x] Strengthen Entity-to-knowledge binding helpers and tests.
- [x] Treat Entity-derived facts, especially SimpleEntity-derived facts, as a
      first-class KnowledgeSpace fact category.
- [x] Keep fact/assertion/observation separation as a future hook unless KS-10
      needs a concrete type.

### Completion Notes

- Added typed CNCF knowledge operational model primitives:
  `KnowledgeNodeCategory`, `KnowledgeRelationshipKind`,
  `KnowledgeRelationshipSemanticType`, `RdfNodeName`, `RdfPredicateName`,
  `KnowledgeFact`, `KnowledgeFrame`, and `KnowledgeFrameOrigin`.
- Reworked `KnowledgeNode` into delegated value objects for identity,
  presentation, semantics, structure, sources, bindings, similarity,
  operations, and attributes.
- Extended `KnowledgeWorkingSet` with frames, facts, Entity/Tag binding
  indexes, and canonical node projection built from relationships and facts.
- Updated `KnowledgeSpace`, projections, admin rendering, and focused specs to
  use the hardened model while leaving TagSpace behavior unchanged.
- Validation: focused KS-10/admin/tag specs passed.

---

## KS-11: KnowledgeSpace Query / Projection Refinement

Status: DONE

### Objective

Update the KS-09 query/projection/admin surfaces to use the hardened KS-10
model before connecting SIE providers.

### Initial Tasks

- [x] Update Record projection and admin pages for typed node/relationship
      kinds, I18n labels, external identifier mappings, and vector references.
- [x] Preserve compact bounded previews for large graphs.
- [x] Add or refine Entity detail / business-logic lookup surfaces if needed
      before SIE integration.
- [x] Validate CNCF-only KnowledgeSpace query/projection behavior against the
      hardened model.

### Completion Notes

- Added `KnowledgeSpace.query` and `KnowledgeSpaceQueryResult` for
  component-local lookups by node, relationship, frame, fact, RDF node,
  external identifier, Entity binding, Tag binding, and semantic type.
- Refined system knowledge admin projection for KS-10 delegated node sections,
  relationship RDF predicates, frame/fact previews, bindings, and similarity
  metadata.
- Added `docs/notes/knowledge-structure/ks-11-knowledge-query-projection-refinement.md`.

---

## KS-12: `textus-sie` Provider / Runtime Realization

Status: ACTIVE

### Objective

Make `textus-sie` run against concrete RDF DB / Vector DB provider paths while
preserving CNCF/SIE responsibility boundaries.

### Initial Tasks

- [ ] Implement or wire real Fuseki-backed RDF DB behavior behind the SIE SPI.
- [ ] Implement or wire real Chroma-backed Vector DB behavior behind the SIE
      SPI.
- [ ] Validate RDF-backed knowledge input and Vector DB indexing.
- [ ] Validate `SemanticRetrieval.query`, `explain`, and `status` against the
      provider-backed runtime.

---

## KS-13: CNCF MCP End-to-End Validation for `textus-sie`

Status: PLANNED

### Objective

Validate that `textus-sie` is exposed to generative AI through CNCF's existing
MCP publication/runtime path.

### Initial Tasks

- [ ] Start CNCF with `textus-sie`.
- [ ] Confirm SIE operations are visible through `meta.mcp` /
      `spec.export.mcp`.
- [ ] Confirm CNCF `/mcp` can discover and invoke SIE query/explain/status.
- [ ] Record any MCP hardening follow-ups separately from Phase 25 baseline.

---

## KS-14: Phase 25 Verification and Closure

Status: PLANNED

### Objective

Close Phase 25 only after the `textus-sie` end-to-end driver path is validated.

### Initial Tasks

- [ ] Record completed runtime and driver scope in the Phase 25 dashboard.
- [ ] Move completed history into the strategy document.
- [ ] Record deferred knowledge-structure work as future candidates.
- [ ] Validate touched repositories and the `textus-sie` driver path.
