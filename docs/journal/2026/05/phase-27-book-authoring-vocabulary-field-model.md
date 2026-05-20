# Phase 27 Book Authoring Vocabulary and Field Model

Date: 2026-05-21

## Context

KE-02 defines the editable vocabulary for book knowledge before the
`textus-knowledge-editor` implementation starts. The goal is not to freeze a
complete bibliographic ontology. The goal is to define the first operational
field model that can drive editor screens, identifier import, reviewable
resolver candidates, and later `KnowledgeNode` / `KnowledgeFrame`
materialization.

This document builds on:

- `phase-27-book-knowledge-modeling-policy.md`;
- the Phase 26 `InformationSpace` foundation;
- the Phase 25 `KnowledgeNode` / `KnowledgeFrame` foundation.

## Field Classification

Book fields use five authoring classifications.

| Classification | Meaning |
| --- | --- |
| `required` | The editor should not confirm a book item without this value. |
| `recommended` | Strongly useful for resolution, display, or materialization, but not mandatory. |
| `optional` | Useful when available, but not required for the first book workflow. |
| `resolver-assisted` | The value may be imported, enriched, or reconciled from Open Library, Wikidata, DBpedia, LOC, VIAF, ISNI, ORCID, or another resolver. |
| `derived/materialized` | The value is produced from confirmed information, relationships, facts, or provider results and is not the primary manual editing field. |

These classifications are editor guidance and validation inputs. They are not
authorization capabilities and they are not CNCF id semantics.

## Field Groups

### Identity and Import Fields

| Field | Classification | Editor label | Example | Validation / guidance |
| --- | --- | --- | --- | --- |
| ISBN-13 | recommended, resolver-assisted | ISBN-13 | `9780134685991` | Normalize hyphenated input; do not use as CNCF id. |
| ISBN-10 | optional, resolver-assisted | ISBN-10 | `0134685997` | Preserve as external identifier when provided; prefer ISBN-13 for lookup. |
| DOI | optional, resolver-assisted | DOI | `10.1000/example` | Normalize DOI URI only when needed; DOI is an external id. |
| Open Library id | optional, resolver-assisted | Open Library ID | `OL12345W` / `OL12345M` | Preserve work/edition distinction. |
| Wikidata id | optional, resolver-assisted | Wikidata QID | `Q12345` | Strong authority-oriented RDF anchor candidate. |
| DBpedia URI | optional, resolver-assisted | DBpedia URI | `http://dbpedia.org/resource/...` | RDF enrichment source, not automatically authoritative. |
| OCLC number | optional, resolver-assisted | OCLC Number | `123456789` | Identifier-only until mapped to an authority resource. |
| LCCN | optional, resolver-assisted | LCCN | `2001023456` | Identifier-only until mapped to LOC linked data. |
| NDL id | optional, resolver-assisted | NDL ID | `...` | Identifier-only until mapped to an NDL resource. |
| Source URL | recommended, resolver-assisted | Source URL | `https://openlibrary.org/...` | Evidence/source reference for imported data. |

Identity/import fields create `InformationSpace` identity bindings and
resolver candidates. They never replace `InformationItemId`, `KnowledgeNodeId`,
Entity id, or CNCF RDF node name.

### Bibliographic Fields

| Field | Classification | Editor label | Example | Validation / guidance |
| --- | --- | --- | --- | --- |
| Title | required | Title | `Domain-Driven Design` | Main display and resolution field. |
| Subtitle | optional | Subtitle | `Tackling Complexity...` | Presentation field; keep separate from title. |
| Original title | optional, resolver-assisted | Original title | `...` | Useful for translations and localized versions. |
| Localized titles | optional, resolver-assisted | Localized titles | `ja: ...`, `en: ...` | Preserve language tags when known. |
| Authors | recommended, resolver-assisted | Authors | `Eric Evans` | Contributor list; order and role are qualifiers. |
| Editors | optional, resolver-assisted | Editors | `...` | Contributor list with editor role. |
| Publisher | recommended, resolver-assisted | Publisher | `Addison-Wesley` | Organization/agent candidate, not plain text only when resolvable. |
| Publication date | recommended, resolver-assisted | Publication date | `2003-08-30` | Preserve precision if only year/month is known. |
| Language | recommended, resolver-assisted | Language | `en`, `ja` | Affects labels, descriptions, and display. |
| Edition | optional, resolver-assisted | Edition | `2nd edition` | May become edition fact or work/edition structure. |
| Volume | optional | Volume | `Vol. 1` | Series/part-whole qualifier. |
| Series | optional, resolver-assisted | Series | `...` | Candidate related/part-whole node. |
| Work / edition distinction | resolver-assisted, derived/materialized | Work / edition | `work`, `edition` | Keep explicit enough for the editor; do not force full FRBR/LRM in KE-02. |

Bibliographic fields are the primary manual/editor fields. They can also be
seeded by identifier lookup and later refined by the editor.

### Content Fields

| Field | Classification | Editor label | Example | Validation / guidance |
| --- | --- | --- | --- | --- |
| Summary | recommended | Summary | Short human-written overview | Preferred editor-facing description. |
| Abstract / description | optional, resolver-assisted | Description | DBpedia abstract / Open Library description | Imported text remains reviewable candidate data. |
| Keywords | optional | Keywords | `domain modeling`, `architecture` | May become classification candidates. |
| Subjects / categories | recommended, resolver-assisted | Subjects | DBpedia category, LCSH, Wikidata class | Classification candidates with source/evidence. |
| Chapters / sections | optional | Chapters / sections | Chapter titles and order | May become part-whole nodes or structured fields. |
| Citations | optional | Citations | Cited works | Relationship candidates; citation context is a qualifier. |
| Related works | optional, resolver-assisted | Related works | Other editions, translations, source-aligned works | Keep relationship strength explicit. |

Content fields drive the 1.5hop+ book neighborhood. They can introduce
supporting nodes and relationships when they are semantically required for
domain logic, editor explanation, retrieval, or materialization.

### Evidence and Provenance Fields

| Field | Classification | Editor label | Example | Validation / guidance |
| --- | --- | --- | --- | --- |
| Source | recommended, resolver-assisted | Source | `openlibrary`, `wikidata`, `dbpedia`, `manual` | Required for imported candidate values. |
| Resolver | resolver-assisted | Resolver | `isbn-lookup`, `dbpedia-search` | Records how a candidate was produced. |
| Confidence | resolver-assisted, derived/materialized | Confidence | `0.92` | Guides review; does not automatically confirm. |
| Imported timestamp | derived/materialized | Imported at | `2026-05-21T...` | Provenance, not an editor field. |
| Confirmation state | derived/materialized | Confirmation state | `candidate`, `confirmed`, `rejected` | InformationSpace lifecycle/projection field. |
| Reviewer/editor note | optional | Reviewer note | `Matched by ISBN and title.` | Human curation context. |

Evidence/provenance fields make imported values reviewable and explainable.
The editor should show these fields near candidate values rather than forcing
users to inspect raw RDF or provider payloads.

## Identifier Behavior

Book identifiers follow these rules:

- ISBN, DOI, OCLC, LCCN, NDL, Open Library id, Wikidata id, DBpedia URI, VIAF,
  ISNI, ORCID, and source-local ids are external bindings.
- They are never CNCF `InformationItemId`, `KnowledgeNodeId`, Entity id, or
  CNCF RDF node name.
- Multiple identifiers may refer to one book, work, edition, contributor, or
  publisher candidate, but each identifier keeps its source, evidence,
  confidence, and confirmation state.
- RDF-node-like identifiers, such as Wikidata entity URI, DBpedia resource URI,
  LOC linked-data URI, and Open Library resource URI, are external RDF anchors
  until a mapping profile confirms their role.
- Identifier conflicts do not overwrite fields silently. They create
  InformationSpace resolution candidates or conflicts.

The editor should let users start from one identifier, then progressively add
or reconcile other identifiers.

## Resolver Expectations

Resolvers are not authoritative by default. They provide candidate fields and
evidence for InformationSpace review.

### Lookup Inputs

The first book workflow should support these lookup inputs:

- ISBN-13 or ISBN-10;
- DOI;
- title plus author/editor;
- Open Library id;
- Wikidata id;
- DBpedia URI or title;
- known external ids from an application Entity binding.

### Lookup Outputs

Resolver output should be normalized into reviewable InformationSpace
candidates:

- candidate RDF node URI or external resource id;
- title, subtitle, localized titles, and descriptions;
- author/editor/publisher candidates;
- publication date, language, edition, series, and work/edition hints;
- ISBN/DOI/Open Library/Wikidata/DBpedia/OCLC/LCCN/NDL/authority ids;
- subjects/categories/classes;
- `sameAs`, `exactMatch`, `closeMatch`, and source alignment links;
- source metadata, confidence, evidence, resolver name, and retrieval time.

### Source Roles

| Source | Role |
| --- | --- |
| Open Library | Strong book/work/edition lookup and description source. |
| Wikidata | Authority-oriented RDF anchor for books, authors, publishers, and concepts. |
| DBpedia | Wikipedia-derived RDF enrichment source for labels, abstracts, categories, and links. |
| LOC / library authorities | Authority and subject heading source when available. |
| VIAF / ISNI / ORCID | Contributor identity resolution source. |
| DOI providers | Publication identifier and external reference source when relevant. |

Each source may provide useful candidates, but InformationSpace confirmation is
required before materialization.

## Editor Guidance Contract

Each book field exposed by `textus-knowledge-editor` should provide:

- a short label;
- a one- or two-sentence explanation;
- at least one example value where useful;
- validation guidance;
- import/resolver status when the value came from an external source;
- a short explanation of how the field affects Knowledge materialization.

Example guidance:

| Field | Label | Description | Example | Knowledge impact |
| --- | --- | --- | --- | --- |
| ISBN-13 | ISBN-13 | Use the 13-digit ISBN to seed or reconcile book information. | `9780134685991` | Creates an external identity binding and resolver lookup key. |
| Title | Title | Main title shown to users and used for candidate matching. | `Domain-Driven Design` | Becomes presentation label/name and resolution input. |
| Authors | Authors | People or organizations credited as authors. Preserve order when known. | `Eric Evans` | Creates authorship relationship candidates and contributor nodes. |
| DBpedia URI | DBpedia URI | RDF resource used as an external enrichment anchor. | `http://dbpedia.org/resource/...` | Creates an RDF anchor candidate and imported evidence. |
| Subjects | Subjects | Topics, categories, or concepts connected to the book. | `Software design` | Creates classification candidates in the 1.5hop+ neighborhood. |

## KE-02 Decisions

- The first implementation vocabulary is book-first and editor-oriented.
- YAML/JSON/manual/editor input is enough for KE-02. XML remains deferred.
- Work/edition/chapter distinctions must be visible, but a complete
  bibliographic ontology remains deferred until the workflow proves the need.
- Resolver output must be candidate/evidence data until confirmed in
  InformationSpace.
- KE-03 owns the exact mapping from these fields to `KnowledgeNode`,
  `KnowledgeRelationship`, `KnowledgeFact`, and `KnowledgeFrame`.
