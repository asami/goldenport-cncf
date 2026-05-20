# Phase 26 — Knowledge Import and InformationSpace Checklist

This document contains detailed task tracking and decisions for Phase 26.
It complements the summary-level phase document (`phase-26.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-26.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## KI-01: Open Phase 26 and Freeze InformationSpace Boundary

Status: DONE

### Objective

Open Phase 26 as the Knowledge Import and InformationSpace phase. Make the
Phase 25 KnowledgeSpace baseline an input, not active work, and fix
InformationSpace as the new editing/curation boundary.

### Detailed Tasks

- [x] Add Phase 26 dashboard and checklist documents.
- [x] Update the strategy current phase pointers to Phase 26.
- [x] Record Phase 25 as closed and leave KS items untouched.
- [x] Add a Phase 26 opening note if implementation decisions need more detail
      than this checklist.
- [x] Confirm `textus-sie` remains the driver and
      `textus-structured-knowledge` remains a separate parallel plan.

### Guardrails

- Do not make `KnowledgeSpace` editable.
- Do not add Fuseki/Chroma dependencies to CNCF core.
- Do not implement XML import in Phase 26 v1.
- Do not use raw RDF as the primary data-entry surface.

### Expected Output

- Phase 26 is visible from the strategy document.
- The first implementation slice can start from `InformationSpace` without
  reopening Phase 25.

---

## KI-02: InformationSpace Core Model and Component-Owned Skeleton

Status: DONE

### Objective

Add a component-owned `InformationSpace` that mirrors the management style of
`EntitySpace`, `AggregateSpace`, `ViewSpace`, and `KnowledgeSpace`.

### Initial Tasks

- [x] Add `org.goldenport.cncf.information`.
- [x] Add ids, lifecycle states, and minimum records for import batches,
      records, information items, validation issues, resolution candidates,
      identity bindings, publication status, and conflicts.
- [x] Add `Component.informationSpace`.
- [x] Add focused specs proving each Component owns a distinct
      `InformationSpace`.

### Completion Notes

- `InformationSpace` is component-owned and storage-neutral in this slice.
- No `Subsystem.informationSpace` was added.

---

## KI-03: Import Batch, Staging Record, Validation Issue, and Lifecycle API

Status: DONE

### Objective

Implement the first storage-neutral InformationSpace API for registering import
batches, editing records, validating records, and inspecting validation issues.

### Initial Tasks

- [x] Register import batches and raw records.
- [x] Preserve raw input separately from editable working data.
- [x] Update working records without mutating raw input.
- [x] Validate records and transition lifecycle state deterministically.
- [x] List records and validation issues by batch/item.

---

## KI-04: Identity Binding and Authority Resolution Candidate Model

Status: DONE

### Objective

Represent authority/RDF/entity/knowledge identity mappings explicitly.

### Initial Tasks

- [x] Add candidate, selected, confirmed, rejected, superseded, and conflict
      binding statuses.
- [x] Connect Information item id, RDF subject, external ids, Entity bindings,
      and KnowledgeNode id without id collapse.
- [x] Add resolution candidate selection and manual resolution APIs.
- [x] Add specs for lookup directions and audit-safe state transitions.

---

## KI-05: Paper Authoring Domain and Validation Profile

Status: DONE

### Objective

Add the first domain authoring model for the Phase 26 vertical slice.

### Initial Tasks

- [x] Add `paper` domain definition.
- [x] Validate title and at least one author.
- [x] Support publication identity, venue/date, abstract, keywords, citations,
      and resolver hooks as optional fields.
- [x] Keep YAML/JSON as primary input forms.
- [x] Keep normalized `KnowledgeNode` / `KnowledgeRelationship` import as
      internal/expert-only.

---

## KI-06: Confirmation, Publication Status, and Conflict Model

Status: DONE

### Objective

Add the curation lifecycle states needed before RDF/vector publication.

### Initial Tasks

- [x] Confirm, reject, and reopen information items.
- [x] Record publication status and result metadata.
- [x] Add conflict records for mapped field/predicate disagreements.
- [x] Keep conflict resolution explicit; do not silently overwrite RDB or RDF
      values.

---

## KI-07: Knowledge Engine SPI and `textus-sie` Provider Integration

Status: DONE

### Objective

Define CNCF provider-neutral SPI boundaries and implement the provider side in
`textus-sie`.

### Initial Tasks

- [x] Define authority resolution request/result types.
- [x] Define RDF publication and vector publication request/result types.
- [x] Define KnowledgeSpace materialization request/result types.
- [x] Implement `textus-sie` provider adapters using existing RDF DB and Vector
      DB SPI contracts.

### Completion Notes

- CNCF owns the provider-neutral `KnowledgeEngineProvider` boundary.
- `textus-sie` owns the concrete provider adapter and remains outside CNCF core.

---

## KI-08: RDF/Vector Publication and KnowledgeSpace Materialization Flow

Status: DONE

### Objective

Publish confirmed InformationSpace data and materialize it into
`KnowledgeSpace`.

### Initial Tasks

- [x] Publish confirmed paper information to RDF and vector providers.
- [x] Materialize published/search result data as `KnowledgeFrame` /
      `KnowledgeWorkingSetSnapshot`.
- [x] Preserve evidence/provenance and source references.
- [x] Avoid raw RDF/vector/source bodies in CNCF projections.

### Completion Notes

- Automated validation uses in-memory/fake provider boundaries. Docker-backed
  Fuseki/Chroma verification remains KI-10.

---

## KI-09: InformationSpace Admin/Debug Projection

Status: DONE

### Objective

Expose compact operator/debug views for InformationSpace state.

### Initial Tasks

- [x] Add `/web/system/admin/information`.
- [x] Add component detail route for InformationSpace records/items.
- [x] Show batches, records, validation issues, resolution candidates,
      confirmed items, publication status, and conflicts.
- [x] Keep KnowledgeSpace admin as runtime semantic inspection.

---

## KI-10: Docker-Backed SIE Smoke and Phase 26 Closure

Status: ACTIVE

### Objective

Validate the full Phase 26 paper flow with Docker-backed external stores and
close the phase.

### Initial Tasks

- [ ] Start Docker Fuseki and SIE-compatible Chroma adapter.
- [ ] Import paper fixture.
- [ ] Validate, resolve, confirm, publish.
- [ ] Query through `SemanticRetrieval`.
- [ ] Verify KnowledgeSpace admin counts and frame details.
- [ ] Verify MCP status/query path still works after publication.
- [ ] Record closure and deferred hardening in strategy.
