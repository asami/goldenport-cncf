# Phase 25 — Knowledge Structure Follow-ups

status = closed

## 1. Purpose of This Document

This work document records Phase 25, which selects
`9.5 Knowledge Structure Follow-ups` as the active development item.

Phase 25 uses `textus-sie` as the application driver for knowledge-structure
requirements. The phase starts by turning the future follow-up list into a
concrete knowledge model and query/projection plan, then carries that design
through enough runtime and driver implementation for `textus-sie` to run on
CNCF with RDF DB, Vector DB, semantic retrieval, and MCP publication.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Establish the CNCF knowledge-structure direction beyond the Phase 20 strict
  tree Tag model.
- Clarify how RDF-oriented representation, external knowledge graph
  integration, DAG/polyhierarchy tag graphs, and application knowledge nodes fit
  together.
- Use `textus-sie` as the driver for concrete application requirements and
  validation.
- Define the minimum model and runtime surfaces needed for knowledge nodes,
  relationships, evidence/source references, and graph traversal.
- Support Entity-to-knowledge binding so business Entities can be linked to
  related knowledge without making every Entity a KnowledgeNode.
- Implement a minimal CNCF `KnowledgeSpace` runtime skeleton and WorkingSet.
- Harden the CNCF operational knowledge model before connecting concrete
  `textus-sie` RDF DB / Vector DB providers. In particular, avoid mixing model
  design work and provider integration in the same slice.
- Validate `textus-sie` against concrete RDF DB / Vector DB provider behavior,
  semantic retrieval operations, and CNCF MCP publication.
- Keep Web/API/Admin projection requirements explicit without making the
  knowledge model depend on a specific UI.

Scope boundaries:

- Phase 25 does not replace Phase 20 Tag tree behavior unless a slice
  explicitly defines a compatibility and migration path.
- Phase 25 does not make CNCF core depend directly on Fuseki, Chroma, or a
  specific RDF/vector database.
- Search/index implementation remains application/provider-owned unless it is
  needed only as a narrow KnowledgeSpace projection helper.
- `textus-sie` is the driver, not a CNCF-core special case.

## 3. Non-Goals

- No broad rewrite of existing Tag or CMS behavior in the opening slice.
- No hard CNCF-core dependency on a specific external knowledge graph database.
- No application-specific `textus-sie` assumptions in CNCF core.
- No ontology authoring UI in the opening slice.

## 4. Active Work Stack

- A (DONE): KS-01 — Knowledge Structure Opening and `textus-sie` Driver Scope.
- B (DONE): KS-02 — Knowledge Vocabulary / Source Model Inventory.
- C (DONE): KS-03 — DAG / Polyhierarchy Tag Graph Model.
- D (DONE): KS-04 — Knowledge Node / Relationship / Evidence Model.
- E (DONE): KS-05 — Query and Projection Surfaces.
- F (DONE): KS-06 — `textus-sie` Driver Integration.
- G (DONE): KS-07 — Phase 25 implementation rebaseline.
- H (DONE): KS-08 — CNCF `KnowledgeSpace` core model and WorkingSet skeleton.
- I (DONE): KS-09 — CNCF knowledge query/projection/admin surface.
- J (DONE): KS-10 — Knowledge operational model hardening.
- K (DONE): KS-11 — KnowledgeSpace query/projection refinement against the
  hardened model.
- L (DONE): KS-12 — `textus-sie` provider/runtime realization.
- M (DONE): KS-13 — CNCF MCP boundary validation for `textus-sie`.
- N (DONE): KS-14 — Docker-based SIE live validation and Phase 25 closure.

Resume hint:

- Phase 25 closed; select next phase.

## 5. Development Items

- [x] KS-01: Knowledge Structure Opening and `textus-sie` Driver Scope.
- [x] KS-02: Knowledge Vocabulary / Source Model Inventory.
- [x] KS-03: DAG / Polyhierarchy Tag Graph Model.
- [x] KS-04: Knowledge Node / Relationship / Evidence Model.
- [x] KS-05: Query and Projection Surfaces.
- [x] KS-06: `textus-sie` Driver Integration.
- [x] KS-07: Phase 25 implementation rebaseline.
- [x] KS-08: CNCF `KnowledgeSpace` core model and WorkingSet skeleton.
- [x] KS-09: CNCF knowledge query/projection/admin surface.
- [x] KS-10: Knowledge operational model hardening.
- [x] KS-11: KnowledgeSpace query/projection refinement against the hardened
      model.
- [x] KS-12: `textus-sie` provider/runtime realization.
- [x] KS-13: CNCF MCP boundary validation for `textus-sie`.
- [x] KS-14: Docker-based SIE live validation and Phase 25 closure.

Detailed task breakdown and progress tracking are recorded in
`phase-25-checklist.md`.

## 6. Completion Conditions

Phase 25 can close when:

- The CNCF knowledge-structure model is explicitly documented.
- The relationship between strict Tag trees, DAG/polyhierarchy graphs, RDF-style
  representation, and external knowledge graph integration is clear.
- Required CNCF model/runtime/query/projection surfaces are implemented at a
  minimal practical level.
- `textus-sie` runs on CNCF with Docker-backed Fuseki and Chroma adapter,
  semantic retrieval, status, KnowledgeSpace registration, admin projection, and
  MCP facade invocation validated through the CNCF developer launcher.
- Deferred knowledge work is recorded as future scope in
  `docs/strategy/cncf-development-strategy.md`.

Closure note:

- Phase 25 is closed.
- KS-01 is done.
- KS-02 is done.
- KS-03 is done.
- KS-04 is done.
- KS-05 is done.
- KS-06 is done.
- KS-07 is done.
- KS-08 is done.
- KS-09 is done.
- KS-10 is done.
- KS-11 is done.
- KS-12 is done.
- KS-13 is done.
- KS-14 is done.
- Completed runtime scope includes the CNCF component-owned `KnowledgeSpace`
  model, memory-resident WorkingSet, query/projection/admin surface, hardened
  operational node/relationship/frame/fact model, and `textus-sie` projection
  into `KnowledgeFrame` / `KnowledgeWorkingSetSnapshot`.
- Completed driver validation used Docker-backed Fuseki plus the legacy-style
  Chroma adapter, explicit RDF/vector seed import, `cncf dev` launcher startup,
  `SemanticRetrieval.status` / `query` / `explain`, KnowledgeSpace admin counts,
  and `Mcp.callTool` for `sie.status`, `sie.query`, and `sie.explain`.
- Deferred production hardening and broader knowledge-structure work are
  recorded under `9.5 Knowledge Structure Follow-ups` in
  `docs/strategy/cncf-development-strategy.md`.
