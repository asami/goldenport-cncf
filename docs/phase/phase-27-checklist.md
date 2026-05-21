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

Status: DONE

### Objective

Open Phase 27 as the book-first knowledge editor phase. Use
`/Users/asami/src/dev2026/textus-knowledge-editor` as the development driver and
fix the scope as application-grade knowledge editing on top of CNCF
`InformationSpace`.

### Initial Tasks

- [x] Add Phase 27 dashboard and checklist documents.
- [x] Update the strategy current phase pointers to Phase 27.
- [x] Keep Phase 26 closed and use its `InformationSpace` foundation as input.
- [x] Add or confirm the `textus-knowledge-editor` driver repository.
- [x] Record the editor scope in a design/journal note if implementation
      decisions need more detail than this checklist.

### Decisions

- `InformationSpace` remains the editable/curated knowledge boundary.
- `KnowledgeSpace` remains runtime semantic materialization and traversal.
- `textus-knowledge-editor` owns application authoring UX.
- Book knowledge is the first vertical slice.
- Book knowledge is organized as a 1.5hop+ meaning neighborhood centered on a
  CNCF `KnowledgeNode`, not as a flat bibliographic record.
- Common semantic-neighborhood structure is reusable CNCF knowledge structure;
  book-specific identifiers, roles, and bibliographic relations are a domain
  profile extension on top of it.
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

### Completion Notes

- Driver repository confirmed at
  `/Users/asami/src/dev2026/textus-knowledge-editor`.
- Book-first editor scope, 1.5hop+ meaning-neighborhood modeling, common
  semantic-neighborhood vs book-profile split, and domain Entity to Textus
  Knowledge binding policy are recorded in
  `docs/journal/2026/05/phase-27-book-knowledge-modeling-policy.md`.
- Next active work is KE-02.

---

## KE-02: Book Authoring Vocabulary, Identifiers, and Field Model

Status: DONE

### Objective

Define concrete editable book knowledge fields and the identifier-driven import
shape used to create CNCF `InformationSpace` data for KnowledgeNode
materialization.

### Initial Tasks

- [x] Define book fields: title, authors/editors, publisher, publication date,
      ISBN, chapters/sections, summary, keywords, citations, and source
      references.
- [x] Define book identifier fields: ISBN-10, ISBN-13, DOI where relevant,
      OpenLibrary id, Wikidata id, DBpedia URI, VIAF/ISNI/ORCID for
      contributors, OCLC/LCCN/NDL/library identifiers, publisher identifier,
      source URL, and other external authority ids.
- [x] Keep work/edition/chapter/manifestation distinctions explicit enough for
      the editor, but avoid freezing a full bibliographic ontology before the
      first book workflow proves the operational need.
- [x] Define identifier import behavior: entering ISBN or another supported id
      creates or updates a book staging record with imported candidate fields
      and source/evidence/provenance.
- [x] Define multi-id merge behavior: multiple identifiers may refer to the
      same book/work/edition, but they remain distinct bindings with source,
      confidence, and evidence.
- [x] Define DBpedia lookup inputs and outputs for book enrichment: DBpedia
      resource URI/RDF node as the primary match result, plus title,
      author/editor, ISBN where available, publication date, publisher,
      abstract/comment, subjects/categories, sameAs links, and DBpedia URI.
- [x] Define how DBpedia confidence/evidence is shown as reviewable
      InformationSpace candidates.
- [x] Separate required, recommended, optional, and resolver-assisted fields.
- [x] Define editor-facing descriptions and examples for each field.

### Completion Notes

- Book authoring vocabulary, field groups, identifier behavior, resolver
  expectations, and editor guidance contract are recorded in
  `docs/journal/2026/05/phase-27-book-authoring-vocabulary-field-model.md`.
- Field classifications are `required`, `recommended`, `optional`,
  `resolver-assisted`, and `derived/materialized`.
- KE-03 owns the concrete mapping from these fields into `KnowledgeNode`,
  `KnowledgeRelationship`, `KnowledgeFact`, and 1.5hop+ `KnowledgeFrame`.

---

## KE-03: Book-to-KnowledgeNode Attribute Mapping

Status: DONE

### Objective

Map edited/imported book information into concrete `KnowledgeNode` attributes
and related `KnowledgeRelationship` facts without id collapse.

### Initial Tasks

- [x] Define node identity fields: RDF node name, external identifiers, Entity
      binding, Tag binding, and KnowledgeNode id.
- [x] Define presentation fields: labels, localized labels, descriptions, and
      summaries.
- [x] Define semantic fields: semantic types, roles, classifications,
      confidence, temporal values, lifecycle, and confidentiality.
- [x] Define structure fields: authorship, publication venue, citations,
      part-of/chapter/section relations, same-as, aliases, related resources,
      and source alignments.
- [x] Define evidence/provenance mapping from InformationSpace records and
      publication results.
- [x] Define how multiple application/domain Entity instances can bind through
      InformationSpace to one Textus book `KnowledgeNode` when identity
      resolution confirms they represent the same knowledge object.
- [x] Define how ISBN/imported publication identifiers become external
      identifiers and identity bindings on the materialized book node.
- [x] Define how multiple external ids/RDF anchors are represented as
      `sameAs`, `exactMatch`, `closeMatch`, source alignment, or weaker
      evidence-backed correspondence.
- [x] Define edge-canonical / node-convenience projection: authorship,
      publisher, citation, classification, and part-whole facts remain
      relationships/facts, while node sections expose derived traversal fields.
- [x] Identify relationship/fact qualifiers needed for book data, such as
      author order, contributor role, chapter order, edition number, volume
      number, translation language, citation context, and page range.
- [x] Define how the DBpedia RDF node maps into `KnowledgeNode.identity.rdfNode`
      or RDF identity binding when selected.
- [x] Define how DBpedia sameAs links, categories, abstracts, predicates, and
      ontology/resource classes map into identity, semantics, structure,
      sources, and evidence/provenance sections.
- [x] Define the book 1.5hop+ neighborhood: focal book node, external RDF
      anchors, author/publisher/work/edition/subject/citation nodes,
      relationships, facts, evidence, and provenance that are essential for
      meaning.
- [x] Separate common semantic-neighborhood fields from book-oriented
      extension fields, so generic KnowledgeNode/KnowledgeFrame projection and
      book profile projection remain distinguishable.

### Completion Notes

- Book-to-`KnowledgeNode` section mapping, external anchor classification,
  relationship/fact mapping, RDF vocabulary mapping, and 1.5hop+
  `KnowledgeFrame` mapping are recorded in
  `docs/journal/2026/05/phase-27-book-to-knowledge-node-attribute-mapping.md`.
- The mapping keeps `KnowledgeNodeId`, CNCF RDF node, Information item id,
  external identifiers, RDF anchors, and Entity ids distinct.
- Book-specific stable data should enter delegated `KnowledgeNode` sections,
  `KnowledgeRelationship`, or `KnowledgeFact`; `attributes` is only a
  temporary extension escape hatch.
- Next active work is KE-04: editor-facing InformationSpace API and projection
  metadata for these mappings.

---

## KE-04: InformationSpace Editor API and Projection Contract

Status: DONE

### Objective

Add or refine CNCF APIs/projections that an application editor can use without
depending on system admin/debug representations.

### Initial Tasks

- [x] Add editor-oriented projection records for editable information items.
- [x] Add field metadata projection: label, help text, examples, requiredness,
      validation status, and publication mapping.
- [x] Add mapping-profile projection metadata so the editor can explain how a
      field maps to KnowledgeNode section, KnowledgeRelationship, KnowledgeFact,
      and evidence/provenance.
- [x] Add projection metadata for the resulting 1.5hop+ `KnowledgeFrame`, so
      the editor can explain which surrounding nodes and relationships are part
      of the book meaning neighborhood.
- [x] Add projection metadata that labels each included node/relationship as
      common semantic-neighborhood structure or book-profile extension.
- [x] Add action projection for save, validate, resolve, confirm, reject,
      reopen, publish, and materialize.
- [x] Keep system admin/debug projection separate from application editor
      projection.

### Completion Notes

- Added `InformationSpaceEditorProjection` as the editor-facing read/projection
  boundary, separate from system admin/debug `InformationSpaceProjection`.
- Added the first concrete `book` editor profile with field descriptors,
  mapping descriptors, lifecycle action projection, and common-neighborhood vs
  book-profile-extension labels.
- The projection is read-only. Mutations still use the existing
  InformationSpace lifecycle API.
- Detailed contract notes are recorded in
  `docs/journal/2026/05/phase-27-information-space-editor-projection-contract.md`.
- Next active work is KE-05: Web editor shell and book navigation in
  `textus-knowledge-editor`.

---

## KE-05: Web Editor Shell and Book Navigation in `textus-knowledge-editor`

Status: DONE

### Objective

Create the first application Web surface for knowledge editing.

### Initial Tasks

- [x] Initialize or normalize the `textus-knowledge-editor` project using
      `cozy init component`, the Cozy-owned component init/scaffolding
      contract from Cozy Phase 6 CS-19.
- [x] Add editor home/list screens.
- [x] Add create/import/edit/detail navigation for book knowledge.
- [x] Add ISBN/identifier entry flow for creating or seeding a book record.
- [x] Add visible field guidance, examples, and validation feedback.
- [x] Add status indicators for draft, validation, resolution, confirmation,
      publication, and KnowledgeSpace materialization.

### Completion Notes

- Initialized and normalized `/Users/asami/src/dev2026/textus-knowledge-editor`
  using Cozy component init. The durable init config is
  `textus-knowledge-editor/etc/cozy-init.yaml`.
- Replaced the generated notice sample with `TextusKnowledgeEditor.BookEditor`
  and book lifecycle operations backed by component-local `InformationSpace`.
- Added Static Form Web pages under `src/main/web` for book list/detail/create/
  seed/edit, validation, confirmation, publication, and materialization
  navigation.
- Split Web metadata so source app/page information lives in
  `src/main/web-inf/web.yaml`, while source operation exposure and form control
  defaults live in `src/main/web-inf/form.yaml`. Cozy packages those inputs into
  CAR `web/WEB-INF/web.yaml` and `web/WEB-INF/form.yaml`. Application structure
  lives in ordinary HTML pages, not generated operation-specific fragments.
- Added focused tests for BookEditor operation exposure, identifier seed,
  title validation, confirmation, and local KnowledgeSpace materialization.
- Detailed implementation notes are recorded in
  `docs/journal/2026/05/phase-27-web-editor-shell-book-navigation.md`.
- Next active work is KE-06: DBpedia/OpenLibrary/Wikidata-backed lookup and
  enrichment candidates for the book vertical slice.

---

## KE-06: Book Import/Editor Vertical Slice with DBpedia Lookup

Status: ACTIVE

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
