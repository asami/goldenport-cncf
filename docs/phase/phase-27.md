# Phase 27 — Knowledge Editor and Domain Knowledge Authoring

status = active

## 1. Purpose of This Document

This work document records Phase 27, which builds on the closed Phase 26
`InformationSpace` foundation.

Phase 26 created the curated/editable information lifecycle and validated paper
publication through `textus-sie`. Phase 27 makes that foundation usable as a
real authoring application: `textus-knowledge-editor` provides Web-based
knowledge editing, starting with book knowledge, with enough explanation and
guidance in the UI that users can enter and curate knowledge without a separate
manual.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Use `/Users/asami/src/dev2026/textus-knowledge-editor` as the development
  driver.
- Provide knowledge editing features on top of CNCF `InformationSpace`.
- Add application-facing Web editing screens, starting with book knowledge and
  then extending to paper and web knowledge.
- Define concrete attributes for book knowledge first, then paper and web
  knowledge records.
- Let users import or seed book information from ISBN and other publication
  identifiers, then review/edit it as CNCF `InformationSpace` data before
  KnowledgeNode materialization.
- Link book knowledge to external knowledge through multiple identifiers and
  RDF anchors, including ISBN, DOI, OpenLibrary, Wikidata, DBpedia, VIAF, ISNI,
  ORCID, library authority ids, and other source-specific ids.
- Use DBpedia as one external RDF knowledge source. DBpedia resource URI/RDF
  node is an external knowledge anchor for authority lookup, attribute
  enrichment, external identifier linkage, and RDF-oriented correspondence when
  book identifier/title/author data can be matched.
- Define the concrete `KnowledgeNode` attributes that edited/imported domain
  knowledge publishes/materializes into.
- Organize edited/imported book knowledge as a 1.5hop+ meaning neighborhood:
  the focal book/publication `KnowledgeNode` plus semantically essential
  surrounding Person, Organization, Textual Work, Edition, Series, Volume,
  identifier, RDF anchor, relationship, fact, evidence, and provenance nodes.
- Treat book KnowledgeSpace materialization as a bibliographic meaning
  neighborhood, not as one ISBN record mapped to one isolated book node.
  Author/editor/translator Person nodes, publisher/imprint Organization nodes,
  source Textual Work, concrete Edition, publication Series, and multi-volume
  Volume nodes are first-class candidates when they are required to understand
  the book.
- Treat `Book` Information as the concrete textual publication layer for v1.
  Do not add a separate `Textual Publication` Information domain in Phase 27.
  `Textual Volume` is available only when a logical volume needs to sit between
  a Textual Edition and one or more concrete Book publications; simple books may
  keep volume number/title directly on Book.
- Treat work-internal units such as Genji monogatari chapters/parts (`花散里`,
  `須磨`, and similar 帖) separately from publication volumes. These are future
  `Textual Part` / `Chapter` concepts under `Textual Work`; Edition/Volume
  nodes can later link to the parts they include.
- Use the Genji materialization and RDF-centric expansion journal notes as the
  working direction for KE-13 and later: ISBN/openBD metadata is only the
  physical-book metadata layer, while book-domain Textual Work, Edition,
  Series, Volume, Person, Organization, subject, cultural, research, and RDF
  anchor links form the broader knowledge neighborhood. Local `KnowledgeSpace`
  should establish stable semantic anchors and link them to external RDF
  identifiers rather than copying entire external knowledge graphs.
- Separate the reusable common semantic-neighborhood contract and
  `CulturalResource` foundation from the book-oriented extension profile, so
  CNCF core knowledge projection does not hard-code bibliographic assumptions.
- Add a follow-up common cultural-resource model for domains beyond books.
  Sculpture, paintings, buildings, and museum collection items should not be
  forced into the book-domain Textual Work concept. They use the shared
  `CulturalResource` family with domain-specific materialization profiles such
  as visual work, built work, physical object, holding, and collection item.
  In Phase 27 this is foundation-only: Visual Work, Physical Object, Holding,
  and Collection Item editor profiles remain deferred.
- Preserve the Phase 26 split:
  - `InformationSpace` owns editing, validation, resolution, confirmation, and
    publication state.
  - `KnowledgeSpace` owns runtime semantic materialization and traversal.
- Keep `textus-sie` as the RDF/vector/provider integration path when external
  knowledge publication, DBpedia lookup, or semantic retrieval is needed.
- Make authoring screens self-explanatory with field descriptions, examples,
  validation feedback, and publication/use explanations.

Scope boundaries:

- Phase 27 does not replace CNCF system admin/debug pages with the application
  editor.
- Phase 27 does not make `KnowledgeSpace` the editing surface.
- Phase 27 does not require raw RDF editing for normal users.
- Phase 27 does not make DBpedia or any other external identifier/source
  authoritative without InformationSpace review/confirmation.
- Phase 27 does not introduce a full ontology/value-system editor unless it is
  required by the paper/book/web authoring slices.
- Phase 27 does not require XML import unless a concrete source is selected
  during the phase.

## 3. Active Work Stack

- A (DONE): KE-01 — Open Phase 27 and freeze editor scope.
- B (DONE): KE-02 — Book authoring vocabulary, identifiers, and field model.
- C (DONE): KE-03 — Book-to-KnowledgeNode attribute mapping.
- D (DONE): KE-04 — InformationSpace editor API and projection contract.
- E (DONE): KE-05 — Web editor shell and book navigation in
  `textus-knowledge-editor`.
- F (DONE): KE-06 — Book import/editor vertical slice with DBpedia lookup.
- G (DONE): KE-07 — Paper editor follow-up.
- H (DONE): KE-08 — Web knowledge editor vertical slice.
- I (DONE): KE-09 — Publish/materialize flow and validation feedback.
- J (DONE): KE-10 — Information runtime cleanup, v1 Information tags, and
  TagComponent app-facing TagSpace screen integration.
- K (DONE): KE-11 — Person and Organization knowledge support for book
  contributors, publishers, imprints, and related authority candidates.
- L (DONE): KE-12 — Person and Organization editor screens.
- M (DONE): KE-13 — Textual Work / Edition / Series / Volume book structure
  expansion plus `CulturalResource` foundation, including multi-volume editions
  such as a nine-volume Iwanami Genji monogatari publication. Direction notes:
  `docs/journal/2026/05/book-knowledge-materialization-genji.md` and
  `docs/journal/2026/05/rdf-centric-knowledge-expansion.md`, with
  `docs/journal/2026/06/book-edition-volume-publication-note.md` defining Book
  as the concrete publication layer and Textual Volume as optional.
- N (DONE): KE-14 — Relationship / Role / Qualifier editing for
  contributor, publisher, citation, part-whole, series, edition, and volume
  facts.
- O (ACTIVE): KE-15 — Authority resolution merge/split workflow for Person,
  Organization, Textual Work, Edition, Series, Volume, and book publication
  candidates.
- O1 (PENDING): KE-15.1 — CML / Cozy form descriptor generation cleanup so
  operation form exposure and redirects are not hand-maintained separately in
  `form.yaml`.
- P (PENDING): KE-16 — Multi-volume / book-set import workflow using Job-based
  import units.
- Q (PENDING): KE-17 — Usability smoke and Phase 27 closure.

Resume hint:

- Start from KE-15. Do not reopen Phase 26 KI items unless fixing a regression.

Next development candidate after Phase 27:

- Web UI DSL / Bootstrap Core / UX Profile implementation, based on
  `docs/notes/web-ui-dsl-bootstrap-core-ux-profile-design.md`.

## 4. Development Items

- [x] KE-01: Open Phase 27 and freeze editor scope.
- [x] KE-02: Book authoring vocabulary, identifiers, and field model.
- [x] KE-03: Book-to-KnowledgeNode attribute mapping.
- [x] KE-04: InformationSpace editor API and projection contract.
- [x] KE-05: Web editor shell and book navigation in `textus-knowledge-editor`.
- [x] KE-06: Book import/editor vertical slice with DBpedia lookup.
- [x] KE-07: Paper editor follow-up.
- [x] KE-08: Web knowledge editor vertical slice.
- [x] KE-09: Publish/materialize flow and validation feedback.
- [x] KE-10: Information runtime cleanup, v1 Information tags, and
      TagComponent app-facing TagSpace screen integration.
- [x] KE-11: Person and Organization knowledge support.
- [x] KE-12: Person and Organization editor screens.
- [x] KE-13: Textual Work / Edition / Series / Volume book structure expansion
      plus `CulturalResource` foundation.
- [x] KE-14: Relationship / Role / Qualifier editing.
- [ ] KE-15: Authority resolution merge/split workflow.
- [ ] KE-15.1: CML / Cozy form descriptor generation cleanup.
- [ ] KE-16: Multi-volume / book-set import workflow.
- [ ] KE-17: Usability smoke and Phase 27 closure.

Next development candidate after Phase 27:

- [ ] Web UI DSL / Bootstrap Core / UX Profile implementation.
- [ ] CulturalResource collection-item profile for museum collections, visual
      works, built works, physical objects, and holdings.

Detailed task breakdown and progress tracking are recorded in
`phase-27-checklist.md`.

## 5. Completion Conditions

Phase 27 can close when:

- `textus-knowledge-editor` can import or seed book knowledge from ISBN and
  other publication identifiers, then edit it through Web screens backed by
  CNCF `InformationSpace`.
- Multiple external identifiers and RDF anchors can provide reviewable
  authority/enrichment candidates for book records when matching data is
  available.
- Book KnowledgeSpace materialization can produce a 1.5hop+ `KnowledgeFrame`
  centered on the book/publication `KnowledgeNode`, with Person,
  Organization, Textual Work, Edition, Series, and Volume support nodes
  included when they are semantically required.
- Paper and web knowledge follow-up paths are implemented or explicitly scoped
  as deferred after the book-first validation.
- The field model for each knowledge type is concrete enough for validation,
  guidance, publication, and KnowledgeNode materialization.
- The concrete `KnowledgeNode` attribute mapping for edited knowledge is
  documented and implemented for the selected fields.
- The editor UI provides field descriptions, examples, validation feedback, and
  publish/use explanations so ordinary editing does not require a separate
  manual.
- Edited knowledge can be confirmed, published, and materialized into
  `KnowledgeSpace`.
- Deferred production/editor hardening is recorded in strategy follow-ups.
