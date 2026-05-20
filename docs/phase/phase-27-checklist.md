# Phase 27 — Knowledge Editor and Domain Knowledge Authoring Checklist

This document contains detailed task tracking and decisions for Phase 27.
It complements the summary-level phase document (`phase-27.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-27.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## KE-01: Open Phase 27 and Freeze Editor Scope

Status: ACTIVE

### Objective

Open Phase 27 as the book-first knowledge editor phase. Use
`/Users/asami/src/dev2026/textus-knowledge-editor` as the development driver and
fix the scope as application-grade knowledge editing on top of CNCF
`InformationSpace`.

### Initial Tasks

- [x] Add Phase 27 dashboard and checklist documents.
- [x] Update the strategy current phase pointers to Phase 27.
- [x] Keep Phase 26 closed and use its `InformationSpace` foundation as input.
- [ ] Add or confirm the `textus-knowledge-editor` driver repository.
- [ ] Record the editor scope in a design/journal note if implementation
      decisions need more detail than this checklist.

### Decisions

- `InformationSpace` remains the editable/curated knowledge boundary.
- `KnowledgeSpace` remains runtime semantic materialization and traversal.
- `textus-knowledge-editor` owns application authoring UX.
- Book knowledge is the first vertical slice.
- ISBN and other publication identifiers are import/seed keys for creating
  book `InformationSpace` data, not replacements for CNCF item ids,
  `KnowledgeNodeId`, or RDF subject ids.
- Book knowledge may link to multiple external identifiers and RDF anchors.
  Typical anchors include ISBN-10/13, DOI, OpenLibrary, Wikidata, DBpedia,
  VIAF, ISNI, ORCID, OCLC/LCCN/NDL and other library authority ids.
- DBpedia is one external RDF knowledge source. DBpedia resource URI/RDF node is
  an external anchor for authority lookup, attribute enrichment, and
  correspondence/linkage candidates. Imported DBpedia data must remain
  reviewable `InformationSpace` data before confirmation.
- CNCF owns reusable InformationSpace/editor projection boundaries needed by
  the driver.

### Guardrails

- Do not make `KnowledgeSpace` editable.
- Do not make raw RDF the default user editing surface.
- Do not treat DBpedia or any external id source as automatically
  authoritative; each source is an evidence-backed candidate/enrichment source.
- Do not collapse external RDF node URI, CNCF `KnowledgeNodeId`,
  `InformationItemId`, ISBN, DOI, authority id, or Entity id into one id. Store
  each external id/RDF node as an explicit identity binding.
- Do not hide required guidance in a separate manual when it can be shown in
  the editor.
- Do not add SIE/Fuseki/Chroma dependencies to CNCF core.

### Expected Output

- Phase 27 is visible from the strategy document.
- The first implementation slice can start from domain vocabulary and editor
  field modeling.

---

## KE-02: Book Authoring Vocabulary, Identifiers, and Field Model

Status: TODO

### Objective

Define concrete editable book knowledge fields and the identifier-driven import
shape used to create CNCF `InformationSpace` data for KnowledgeNode
materialization.

### Initial Tasks

- [ ] Define book fields: title, authors/editors, publisher, publication date,
      ISBN, chapters/sections, summary, keywords, citations, and source
      references.
- [ ] Define book identifier fields: ISBN-10, ISBN-13, DOI where relevant,
      OpenLibrary id, Wikidata id, DBpedia URI, VIAF/ISNI/ORCID for
      contributors, OCLC/LCCN/NDL/library identifiers, publisher identifier,
      source URL, and other external authority ids.
- [ ] Keep work/edition/chapter/manifestation distinctions explicit enough for
      the editor, but avoid freezing a full bibliographic ontology before the
      first book workflow proves the operational need.
- [ ] Define identifier import behavior: entering ISBN or another supported id
      creates or updates a book staging record with imported candidate fields
      and source/evidence/provenance.
- [ ] Define multi-id merge behavior: multiple identifiers may refer to the
      same book/work/edition, but they remain distinct bindings with source,
      confidence, and evidence.
- [ ] Define DBpedia lookup inputs and outputs for book enrichment: DBpedia
      resource URI/RDF node as the primary match result, plus title,
      author/editor, ISBN where available, publication date, publisher,
      abstract/comment, subjects/categories, sameAs links, and DBpedia URI.
- [ ] Define how DBpedia confidence/evidence is shown as reviewable
      InformationSpace candidates.
- [ ] Separate required, recommended, optional, and resolver-assisted fields.
- [ ] Define editor-facing descriptions and examples for each field.

---

## KE-03: Book-to-KnowledgeNode Attribute Mapping

Status: TODO

### Objective

Map edited/imported book information into concrete `KnowledgeNode` attributes
and related `KnowledgeRelationship` facts without id collapse.

### Initial Tasks

- [ ] Define node identity fields: RDF node name, external identifiers, Entity
      binding, Tag binding, and KnowledgeNode id.
- [ ] Define presentation fields: labels, localized labels, descriptions, and
      summaries.
- [ ] Define semantic fields: semantic types, roles, classifications,
      confidence, temporal values, lifecycle, and confidentiality.
- [ ] Define structure fields: authorship, publication venue, citations,
      part-of/chapter/section relations, same-as, aliases, related resources,
      and source alignments.
- [ ] Define evidence/provenance mapping from InformationSpace records and
      publication results.
- [ ] Define how ISBN/imported publication identifiers become external
      identifiers and identity bindings on the materialized book node.
- [ ] Define how multiple external ids/RDF anchors are represented as
      `sameAs`, `exactMatch`, `closeMatch`, source alignment, or weaker
      evidence-backed correspondence.
- [ ] Define edge-canonical / node-convenience projection: authorship,
      publisher, citation, classification, and part-whole facts remain
      relationships/facts, while node sections expose derived traversal fields.
- [ ] Identify relationship/fact qualifiers needed for book data, such as
      author order, contributor role, chapter order, edition number, volume
      number, translation language, citation context, and page range.
- [ ] Define how the DBpedia RDF node maps into `KnowledgeNode.identity.rdfNode`
      or RDF identity binding when selected.
- [ ] Define how DBpedia sameAs links, categories, abstracts, predicates, and
      ontology/resource classes map into identity, semantics, structure,
      sources, and evidence/provenance sections.

---

## KE-04: InformationSpace Editor API and Projection Contract

Status: TODO

### Objective

Add or refine CNCF APIs/projections that an application editor can use without
depending on system admin/debug representations.

### Initial Tasks

- [ ] Add editor-oriented projection records for editable information items.
- [ ] Add field metadata projection: label, help text, examples, requiredness,
      validation status, and publication mapping.
- [ ] Add mapping-profile projection metadata so the editor can explain how a
      field maps to KnowledgeNode section, KnowledgeRelationship, KnowledgeFact,
      and evidence/provenance.
- [ ] Add action projection for save, validate, resolve, confirm, reject,
      reopen, publish, and materialize.
- [ ] Keep system admin/debug projection separate from application editor
      projection.

---

## KE-05: Web Editor Shell and Book Navigation in `textus-knowledge-editor`

Status: TODO

### Objective

Create the first application Web surface for knowledge editing.

### Initial Tasks

- [ ] Add editor home/list screens.
- [ ] Add create/import/edit/detail navigation for book knowledge.
- [ ] Add ISBN/identifier entry flow for creating or seeding a book record.
- [ ] Add visible field guidance, examples, and validation feedback.
- [ ] Add status indicators for draft, validation, resolution, confirmation,
      publication, and KnowledgeSpace materialization.

---

## KE-06: Book Import/Editor Vertical Slice with DBpedia Lookup

Status: TODO

### Objective

Implement the first full editing workflow for book knowledge, including
identifier-driven import/seed behavior and DBpedia-backed enrichment
candidates.

### Initial Tasks

- [ ] Create book record manually.
- [ ] Create or seed book record from ISBN or another supported identifier.
- [ ] Add or reconcile multiple external identifiers on the same book record.
- [ ] Query DBpedia from ISBN/title/author data when available.
- [ ] Show the matched DBpedia RDF node URI as the external knowledge anchor.
- [ ] Show DBpedia labels, abstracts/comments, categories, sameAs links,
      predicates, and inferred candidates as reviewable suggestions.
- [ ] Edit and validate fields.
- [ ] Resolve author/publisher/work/concept candidates where available.
- [ ] Confirm and publish the book knowledge.
- [ ] Materialize the book into KnowledgeSpace and verify node/relationship
      projection.

---

## KE-07: Paper Editor Follow-Up

Status: TODO

### Objective

Implement the paper knowledge editing workflow after book-first validation.

### Initial Tasks

- [ ] Define paper fields: title, authors, publication identity, venue/date,
      abstract, keywords, citations, source URL, language, and publication
      metadata.
- [ ] Create paper record.
- [ ] Edit and validate fields.
- [ ] Resolve author/venue/concept candidates where available.
- [ ] Publish and materialize paper nodes and relationships.

---

## KE-08: Web Knowledge Editor Vertical Slice

Status: TODO

### Objective

Implement the web resource knowledge editing workflow.

### Initial Tasks

- [ ] Create web knowledge record from URL/manual input.
- [ ] Edit canonical URL, title, site/publisher, author, retrieved date,
      summary, language, keywords, and links.
- [ ] Preserve source/evidence/provenance for retrieved content.
- [ ] Publish and materialize web resource nodes and relationships.

---

## KE-09: Publish/Materialize Flow and Validation Feedback

Status: TODO

### Objective

Make the editor flow complete from draft editing to KnowledgeSpace
materialization.

### Initial Tasks

- [ ] Show validation issues in editor context.
- [ ] Show resolution candidates with evidence and confidence.
- [ ] Show confirmation and publication status.
- [ ] Publish through the Phase 26 Knowledge engine SPI path.
- [ ] Materialize published information into KnowledgeSpace.
- [ ] Show the resulting KnowledgeNode/KnowledgeRelationship summary to the
      editor user.

---

## KE-10: Usability Smoke and Phase 27 Closure

Status: TODO

### Objective

Close Phase 27 only after the editor can be used without a separate manual for
the selected paper/book/web workflows.

### Initial Tasks

- [ ] Run a book identifier import/editing smoke with DBpedia lookup.
- [ ] Run a paper editing smoke if included in the closure scope.
- [ ] Run a web knowledge editing smoke.
- [ ] Verify field guidance and validation messages are visible in the UI.
- [ ] Verify published knowledge appears in KnowledgeSpace.
- [ ] Record deferred hardening in strategy.
