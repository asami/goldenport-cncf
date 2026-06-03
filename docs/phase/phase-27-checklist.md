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
- Book knowledge is not complete if contributor and publisher identities
  remain plain strings. Person and Organization knowledge must be available as
  surrounding Information/Knowledge candidates for authors, editors,
  translators, publishers, imprints, and related institutions.
- Multi-volume and edition-oriented books need explicit Textual Work / Edition /
  Series / Volume structure. For example, a nine-volume Iwanami edition of
  Genji monogatari should be modeled as source textual work, concrete edition
  or publication set, series/publisher context, and individual volume nodes
  such as Genji monogatari (1) through Genji monogatari (9), not as unrelated
  book records.
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
  `Information.id`, ISBN, DOI, authority id, or Entity id into one id. Store
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
- Completed by KE-06: DBpedia-backed lookup and local identifier candidates
  for the book vertical slice.

---

## KE-06: Book Import/Editor Vertical Slice with DBpedia Lookup

Status: DONE

### Objective

Implement the first full editing workflow for book knowledge, including
identifier-driven import/seed behavior and DBpedia-backed enrichment
candidates.

### Initial Tasks

- [x] Create book record manually.
- [x] Create or seed book record from ISBN or another supported identifier.
- [x] Add or reconcile multiple external identifiers on the same book record.
- [x] Query DBpedia from ISBN/title/author data when available.
- [x] Show the matched DBpedia RDF node URI as the external knowledge anchor.
- [x] Show DBpedia labels, abstracts/comments, categories, and sameAs links as
      reviewable suggestions.
- [x] Edit and validate fields.
- [x] Keep author/publisher/work/concept resolution as future candidate work
      while validating the book-level resolver path.
- [x] Confirm the selected-candidate book record through the existing lifecycle.
- [x] Materialize the book into KnowledgeSpace through the existing
      InformationSpace-to-KnowledgeSpace projection.

### Completion Notes

- `textus-knowledge-editor` now has resolver operations for resolving,
  listing, selecting, clearing, and enriching book candidates.
- DBpedia Lookup is the first live external RDF enrichment source. Automated
  tests use fake HTTP responses so external service availability does not gate
  validation.
- Local ISBN, DOI, Open Library, Wikidata, and DBpedia URI fields become
  reviewable identity-binding candidates.
- KE-06 does not add predicate-level candidate extraction or
  author/publisher/work/concept authority resolution; those remain follow-up
  resolver profile work.
- CNCF core remains provider-neutral; it only gained generic candidate clearing
  support in `InformationSpace`.
- Detailed implementation notes are recorded in
  `docs/journal/2026/05/phase-27-book-resolver-import-external-candidate-flow.md`.

---

## KE-07: Paper Editor Follow-Up

Status: DONE

### Objective

Implement the paper knowledge editing workflow after book-first validation.

### Initial Tasks

- [x] Define paper fields: title, authors, publication identity, venue/date,
      abstract, keywords, citations, source URL, language, and publication
      metadata.
- [x] Create paper record.
- [x] Edit and validate fields.
- [x] Resolve author/venue/concept candidates where available.
- [x] Publish and materialize paper nodes and relationships.

### Completion Notes

- Added `PaperEditor` as a separate service in `textus-knowledge-editor`, so
  book behavior remains stable while paper editing gains its own operation
  contract.
- Paper records use the `paper` InformationSpace domain and support title,
  authors, DOI, arXiv, PubMed, Semantic Scholar, OpenAlex, Wikidata, DBpedia
  URI, venue, publication date, language, abstract, keywords, citations, and
  source URL fields.
- Local DOI/arXiv/PubMed/Semantic Scholar/OpenAlex/Wikidata/DBpedia/source URL
  identifiers become reviewable identity-binding candidates. DBpedia Lookup is
  available as a best-effort RDF candidate source with fake HTTP test coverage.
- Publish and materialize operations reuse the existing InformationSpace
  lifecycle and KnowledgeSpace materialization path.
- Detailed implementation notes are recorded in
  `docs/journal/2026/05/phase-27-paper-editor-follow-up.md`.

---

## KE-08: Web Knowledge Editor Vertical Slice

Status: DONE

### Objective

Implement the web resource knowledge editing workflow.

### Initial Tasks

- [x] Create web knowledge record from URL/manual input.
- [x] Edit canonical URL, title, site/publisher, author, retrieved date,
      summary, language, keywords, and links.
- [x] Preserve source/evidence/provenance for retrieved content.
- [x] Publish and materialize web resource nodes and relationships.

### Completion Notes

- Added `WebResourceEditor` as a separate service in
  `textus-knowledge-editor`, so book and paper behavior remain stable while web
  resource editing gains its own operation contract.
- Web resource records use the `web-resource` InformationSpace domain and
  support URL, canonical URL, title, site name, publisher, author, retrieved
  timestamp, summary, language, keywords, selected links, source URL, and
  reviewer notes.
- Added lightweight metadata fetching through an application-local provider.
  Fetched values are editable InformationSpace data; raw HTML bodies and full
  crawler payloads are not stored or projected.
- URL/canonical URL and selected RDF-like links become reviewable
  identity-binding candidates. Candidate selection remains explicit before
  confirmation.
- Publish and materialize operations reuse the existing InformationSpace
  lifecycle and KnowledgeSpace materialization path.
- Detailed implementation notes are recorded in
  `docs/journal/2026/05/phase-27-web-resource-editor-vertical-slice.md`.

---

## KE-09: Publish/Materialize Flow and Validation Feedback

Status: DONE

### Objective

Make the editor flow complete from draft editing to KnowledgeSpace
materialization.

### Initial Tasks

- [x] Show validation issues in editor context.
- [x] Show resolution candidates with evidence and confidence.
- [x] Show confirmation and publication status.
- [x] Publish through the Phase 26 Knowledge engine SPI path.
- [x] Materialize published information into KnowledgeSpace.
- [x] Show the resulting KnowledgeNode/KnowledgeRelationship summary to the
      editor user.

### Completion Notes

- `textus-knowledge-editor` now routes book, paper, and web-resource publish
  and materialize operations through an application-local
  `KnowledgeEngineProvider` implementation. The provider uses the current local
  CNCF InformationSpace-to-KnowledgeSpace materialization path and can later be
  replaced by SIE-backed wiring.
- Publish operations record provider status, publication target, message, and
  resulting KnowledgeFrame id only after provider success. Provider failures
  leave InformationSpace publication state unchanged.
- Materialize operations store the provider-produced snapshot in the
  component-local `KnowledgeSpace` and return deterministic KnowledgeSpace
  counts plus compact frame, node, relationship, fact, evidence, and provenance
  summaries.
- Editor responses for book, paper, and web-resource domains continue to show
  validation issues, resolution candidates, confidence/evidence, action state,
  and publication status, while excluding raw RDF triples, raw vector payloads,
  provider JSON, and raw HTML bodies.
- Detailed implementation notes are recorded in
  `docs/journal/2026/05/phase-27-ke-09-publish-materialize-feedback.md`.

---

## KE-10: Information Runtime Cleanup and Tag Support

Status: DONE

### Objective

Finish the Information identity cleanup and v1 tag support needed before the
final Phase 27 usability path can use one consistent `Information` entity.

### Initial Tasks

- [x] Migrate CNCF Information runtime and TKE public/editor surfaces from
      record/item split terminology to the single `Information` entity and
      lifecycle-state model.
- [x] Add v1 Information tag management using the dedicated `information` tag
      space, with tag filtering in TKE lists and tag bindings included in
      local KnowledgeSpace materialization.
- [x] Add CNCF TagComponent application-facing TagSpace screen and link TKE to
      `/web/tag/tags?tagSpace=information` for Information Tag master
      reference/editing.

### Completion Notes

- CNCF Information runtime now uses a single `Information` entity with
  lifecycle state, nested validation/candidate/binding/publication/conflict
  values, and `Information.id` as the public identity.
- TKE book, paper, and web-resource operations now use `informationId` for
  detail, lifecycle, publication, materialization, and candidate actions.
- Information tags use the dedicated `information` tag space in v1. TKE can
  sync tags, filter lists by tag, show tag summaries in Information tables and
  detail projections, and materialize local Knowledge nodes with tag bindings.
- Tag master reference/editing for application users is provided by CNCF
  builtin `TagComponent` at `/web/tag/tags`; CNCF admin `/web/admin/tags`
  remains the operator/admin surface.
- Book Knowledge materialization now explicitly depends on surrounding
  Person/Organization and Textual Work/Edition/Series/Volume knowledge when
  those concepts are present in the source. This keeps author, publisher, and
  multi-volume edition structure out of untyped strings and makes the 1.5hop+
  KnowledgeFrame the closure unit for book understanding.

---

## KE-11: Person and Organization Knowledge Support

Status: DONE

### Objective

Add book-adjacent Person and Organization knowledge so contributors,
publishers, imprints, and related institutions are not reduced to untyped
strings during book editing and materialization.

### Initial Tasks

- [x] Add or confirm Information profiles for Person and Organization in the
      editor-facing vocabulary.
- [x] Add Person fields for author, editor, translator, annotator/commentator,
      contributor, and cited or related creator roles.
- [x] Add Organization fields for publisher, imprint, institution, series owner,
      library/authority organization, and source authority provider roles.
- [x] Add external identifier and RDF anchor handling for Person and
      Organization candidates, including Wikidata, DBpedia, VIAF, ISNI, ORCID,
      LCNAF, NDL authority ids, ROR, and publisher-local ids where applicable.
- [x] Extend book editing/materialization so contributor and publisher strings
      can produce unresolved, selected, confirmed, or materialized
      Person/Organization knowledge candidates.
- [x] Show Person/Organization candidate state in book detail and materialized
      Knowledge summary without exposing raw provider payloads.
- [x] Add focused TKE executable specifications for a book whose author and
      publisher become separate knowledge candidates.

### Completion Notes

- CNCF now exposes `person` and `organization` Information editor profiles.
- Book author, editor, and publisher descriptors explain that resolved names
  become Person/Organization authority candidates.
- TKE local resolver creates reviewable author/editor Person candidates and
  publisher Organization candidates from supplied book fields.
- DBpedia resolver preserves candidate field path and kind for book, person, or
  organization lookup results.
- Selected book author/editor/publisher candidates materialize into surrounding
  Person/Organization Knowledge nodes with `authored-by`, `edited-by`, or
  `published-by` relationships.
- Detailed policy notes are recorded in
  `docs/journal/2026/05/phase-27-ke-11-person-organization-authority-policy.md`.

---

## KE-12: Person and Organization Editor Screens

Status: DONE

### Objective

Add full app-facing editor screen sets for `person` and `organization`
Information, equivalent in structure to the existing book/paper/web-resource
flows. Person and Organization are no longer only book-adjacent authority
candidates; they must be directly searchable, editable, confirmable,
publishable, and materializable Information domains.

### Initial Tasks

- [x] Add `PersonEditor` and `OrganizationEditor` services to
      `textus-knowledge-editor.cml`.
- [x] Add dashboard, list, detail, edit, seed/create, resolve, validate,
      confirm, reject, reopen, publish, and materialize operations for both
      domains.
- [x] Add Static Form Web pages and metadata for person and organization
      dashboard/list/detail/edit flows.
- [x] Use CNCF `person` and `organization` InformationEditorProfiles so field
      guidance, external identifiers, RDF anchors, validation, and mapping
      metadata are displayed from the shared projection contract.
- [x] Connect book contributor/publisher candidate flows to Person/Organization
      Information creation or editing links, without adding TKE-specific
      duplicate authority UI.
- [x] Add list/search filters for name, external identifier, tag, lifecycle
      state, and updated time.
- [x] Add focused executable specifications for creating, editing, validating,
      confirming, publishing, and materializing Person and Organization
      Information.

### Completion Notes

- `PersonEditor` and `OrganizationEditor` services are available in
  `textus-knowledge-editor.cml`.
- TKE now exposes Person and Organization dashboards, lists, detail pages, edit
  pages, seed/create forms, and bulk import forms.
- Person and Organization bulk import is Job-backed with `execution ::
  async-job`.
- Local and DBpedia authority resolver candidates are supported without storing
  raw provider payloads.
- Overall dashboard counts and recent Information sections include Person and
  Organization.
- Detailed implementation notes are recorded in
  `docs/journal/2026/05/phase-27-ke-12-person-organization-editor-screens.md`.

---

## KE-13: Textual Work / Edition / Series / Volume + CulturalResource Foundation

Status: ACTIVE

### Objective

Extend book knowledge from a single publication node into explicit
book-domain Textual Work, Edition, Series, and Volume structures, including
multi-volume publications such as a nine-volume Iwanami Genji monogatari
edition. Establish the shared `CulturalResource` family at the same time so
later museum collection-item, visual-work, built-work, physical-object, and
holding profiles do not have to reuse the book-only Textual Work concept.

Reference direction:

- `docs/journal/2026/05/book-knowledge-materialization-genji.md`
- `docs/journal/2026/05/rdf-centric-knowledge-expansion.md`
- `docs/journal/2026/06/book-edition-volume-publication-note.md`

These notes define the intended expansion style: ISBN/openBD metadata remains
the concrete Book publication layer, while Textual Work, Edition, optional
Volume, Person, Organization, subject, cultural, research, and RDF anchor
knowledge form the broader meaning neighborhood. TKE should materialize stable
local KnowledgeNodes and RDF links, not copy complete external RDF graphs into
local Information.

Terminology boundary:

- `Textual Work` is the book/text domain abstraction for a work such as
  `源氏物語`; avoid using a global `Work` category for all knowledge domains.
- `CulturalResource` is the shared family for cultural knowledge materialized
  into KnowledgeSpace. Book profile nodes such as `publication`, `volume`,
  `edition`, and `textual-work` keep their categories but carry
  `resource_family=cultural-resource`.
- `Book` is the v1 Information domain for concrete textual publications. A
  separate `Textual Publication` Information domain is deferred.
- `Textual Volume` is a first-class Information domain only when a logical
  volume needs to sit between an edition and one or more concrete publications;
  simple one-publication volumes may remain Book fields.
- `Textual Part` / `Chapter` is the future work-internal structure for units
  such as Genji monogatari `花散里` or `須磨`. These are parts of a Textual Work,
  not Textual Volumes. Editions and volumes may later link to the parts they
  include.
- Sculpture, paintings, buildings, and museum collection objects are future
  domain profiles under the same family: `visual-work`, `built-work`,
  `physical-object`, `holding`, and `collection-item`.
- The common model classifies shared semantics, while materialization still
  creates domain-appropriate nodes instead of collapsing all cultural resources
  into one `Work` type.

### Initial Tasks

- [ ] Add `CulturalResource` vocabulary to Knowledge materialization with
      family `cultural-resource` and kinds `textual-work`, `edition`,
      `series`, `volume`, `publication`, `visual-work`, `built-work`,
      `physical-object`, `collection-item`, and `holding`.
- [ ] Keep Book as the concrete textual publication Information domain and do
      not add a separate `Textual Publication` domain in this slice.
- [ ] Add Textual Work and Textual Edition as first-class Information domains;
      add Textual Volume as an optional first-class domain for meaningful
      logical volume grouping, not a required layer for every Book.
- [ ] Add cultural-resource attributes to materialized Book layer nodes while
      preserving their existing `KnowledgeNode.category` values.
- [ ] Define editable fields for work title, original/source work, edition
      title, edition contributors, series title, volume title, volume number,
      total volume count, publisher/imprint, publication date, and ISBN-bearing
      publication unit.
- [ ] Define relationship mapping for `volume-of`, `realizes-work`,
      `part-of-series`, `published-by`, `authored-by`, `edited-by`, and
      `translated-by`.
- [ ] Define the boundary for work-internal `Textual Part` / `Chapter`
      concepts and keep them distinct from edition/publication-side
      `Textual Volume`.
- [ ] Extend book import/enrichment so multi-volume metadata can be staged as
      reviewable Information rather than flattened into one title string.
- [ ] Extend Knowledge materialization summaries so missing, unresolved,
      selected, or materialized Textual Work/Edition/Series/Volume nodes are
      visible in the 1.5hop+ book KnowledgeFrame.
- [ ] Make RDF anchor state visible for Textual Work/Edition/Series/Volume nodes,
      distinguishing local Textus KnowledgeNodes from sameAs/exactMatch/
      closeMatch/source-alignment links to external RDF spaces.
- [ ] Add focused TKE executable specifications for a multi-volume book
      example, using Iwanami Genji monogatari style structure as the reference
      scenario.

### Completion Notes

- Pending.

---

## KE-14: Relationship / Role / Qualifier Editing

Status: PENDING

### Objective

Add explicit relationship editing so Person, Organization, Textual Work,
Edition, Series, Volume, citation, and subject knowledge can be connected with
roles, order, qualifiers, confidence, evidence, and provenance instead of being
flattened into untyped fields.

### Initial Tasks

- [ ] Define the editor-facing relationship model for book-adjacent
      relationships such as `authored-by`, `edited-by`, `translated-by`,
      `published-by`, `volume-of`, `realizes-work`, `part-of-series`,
      `has-part`, `citation`, and `subject`.
- [ ] Add qualifier fields for author/contributor order, contributor role,
      edition number, volume number, chapter/section order, translation
      language, citation context, page range, confidence, evidence, and source
      provenance.
- [ ] Add relationship editing surfaces that let users review, add, update, and
      remove relationship candidates without editing raw RDF triples.
- [ ] Keep canonical relationship/fact data in Information/Knowledge
      relationship structures, while node/detail pages expose derived traversal
      convenience summaries.
- [ ] Add focused executable specifications for role-qualified authorship,
      translator/editor attribution, publisher/imprint relationship, and
      volume/series relationship editing.

### Completion Notes

- Pending.

---

## KE-15: Authority Resolution Merge/Split Workflow

Status: PENDING

### Objective

Add the workflow needed to handle same-name/different-entity and
different-name/same-entity authority problems for Person, Organization,
Textual Work, Edition, Series, Volume, and publication candidates.

### Initial Tasks

- [ ] Add duplicate/similar candidate detection and unresolved authority queues
      for Person, Organization, Textual Work, Edition, Series, Volume, and book
      publication candidates.
- [ ] Add merge workflow for candidates confirmed to represent the same
      knowledge object, while preserving external identifiers, evidence, and
      provenance.
- [ ] Add split/unmerge workflow for candidates that were incorrectly linked or
      should remain separate.
- [ ] Show selected, unresolved, merged, split, and rejected authority states in
      editor projections and Knowledge summaries.
- [ ] Ensure merge/split decisions do not collapse `Information.id`, external
      RDF URI, `KnowledgeNodeId`, application Entity id, ISBN, DOI, or authority
      ids into one identifier.
- [ ] Add focused executable specifications for same-name Person conflict,
      publisher alias merge, and mistaken authority split.

### Completion Notes

- Pending.

---

## KE-16: Multi-Volume / Book-Set Import Workflow

Status: PENDING

### Objective

Add a book-set import workflow so ISBN lists, CSV/Excel files, or other import
sources can create a coordinated Textual Work / Edition / Series / Volume
structure as one Job-backed work unit.

### Initial Tasks

- [ ] Add import input shape for book sets and multi-volume publications,
      including work title, edition title, series title, volume title, volume
      number, total volumes, ISBN, publication date, publisher/imprint, and
      contributor columns.
- [ ] Use CNCF Job import units so a multi-volume import can be listed,
      inspected, retried within retention policy, and used as a work-unit
      filter for created Information.
- [ ] Map imported rows into related Information objects for Textual Work,
      Edition, Series, Volume, Person, Organization, and publication/book
      records.
- [ ] Preserve source-layer distinctions in the import result: ISBN/openBD
      physical publication metadata, library/authority metadata, RDF anchors,
      and inferred Textual Work/Edition/Series/Volume structure must be reviewable
      separately before merge/materialization.
- [ ] Provide import result summaries showing created, updated, skipped,
      unresolved, and candidate-linked Information counts.
- [ ] Add list/detail navigation from an import Job to the Information created
      by that import.
- [ ] Add focused executable specifications using an Iwanami Genji monogatari
      style multi-volume import fixture.

### Completion Notes

- Pending.

---

## KE-17: Usability Smoke and Phase 27 Closure

Status: PENDING

### Objective

Close Phase 27 only after the editor can be used without a separate manual for
the selected book/paper/web workflows, including the expanded book knowledge
structure added in KE-11 through KE-16.

### Initial Tasks

- [ ] Run a book identifier import/editing smoke with DBpedia lookup.
- [ ] Run a Person/Organization book-adjacent knowledge smoke.
- [ ] Run a Textual Work/Edition/Series/Volume multi-volume book structure smoke.
- [ ] Run a relationship/role/qualifier editing smoke.
- [ ] Run an authority merge/split workflow smoke.
- [ ] Run a multi-volume/book-set import Job smoke.
- [ ] Verify the book materialization summary can explain when author,
      publisher, work, edition, series, or volume knowledge is missing,
      unresolved, or materialized as part of the 1.5hop+ neighborhood.
- [ ] Run a paper editing smoke if included in the closure scope.
- [ ] Run a web knowledge editing smoke.
- [ ] Verify field guidance and validation messages are visible in the UI.
- [ ] Verify published knowledge appears in KnowledgeSpace.
- [ ] Record deferred hardening in strategy.

### Completion Notes

- Pending.

---

## Next Development Candidate After Phase 27: Web UI DSL / Bootstrap Core / UX Profile

Status: QUEUED

### Objective

After Phase 27 later closes, turn the Web UI DSL design note into the next
implementation slice so Static Form Web Apps and generated CNCF screens can use
semantic Textus widgets, stable Bootstrap Core DOM, and selectable UX profiles.

This is not part of KE-17 and does not imply Phase 27 is already ready to
close.

### Planned Tasks

- [ ] Select the final `web.yaml` metadata key for UX Profile selection.
- [ ] Add the minimal UX Profile model and default `bootstrap` profile.
- [ ] Normalize current Static Form widgets around canonical `textus:` widget
      names.
- [ ] Keep Bootstrap Core output stable and covered by renderer specs.
- [ ] Move reusable TKE layout/density/mobile decisions into profile or layout
      metadata where they are broadly useful.
- [ ] Add or extend widgets when screens need new output shape, instead of
      adding application-local JavaScript rendering.
- [ ] Add Web developer documentation for authoring with the DSL/profile model.

### Source Notes

- `docs/notes/web-ui-dsl-bootstrap-core-ux-profile-design.md`
- `docs/journal/2026/05/bootstrap-core-ux-profile-architecture.md`
