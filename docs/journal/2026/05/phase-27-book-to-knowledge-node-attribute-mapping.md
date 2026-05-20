# Phase 27 Book-to-KnowledgeNode Attribute Mapping

Date: 2026-05-21

## Context

KE-03 maps the KE-02 book authoring vocabulary to the existing CNCF
`KnowledgeNode`, `KnowledgeRelationship`, `KnowledgeFact`, and
`KnowledgeFrame` model. This is a mapping-profile note, not runtime
implementation.

The target model is the current delegated `KnowledgeNode` shape:

```text
KnowledgeNode
  id
  category
  identity
  presentation
  semantics
  structure
  sources
  bindings
  similarity
  operations
  attributes
```

Book knowledge should use this object model so domain logic can traverse
structured knowledge without parsing raw RDF or provider payloads.

## Mapping Principles

- `KnowledgeNodeId`, CNCF RDF node, `InformationItemId`, ISBN, DOI,
  DBpedia URI, Wikidata URI, Open Library id, and Entity id are distinct.
- ISBN and other publication identifiers are import/lookup keys and external
  identifiers. They never become CNCF node ids.
- RDF-like URIs from DBpedia, Wikidata, Open Library, LOC, VIAF, ORCID, DOI,
  and related sources are external RDF anchor candidates until confirmed in
  `InformationSpace`.
- Confirmed InformationSpace choices drive canonical `KnowledgeNode`
  projection. Imported external data remains reviewable candidate/evidence
  data before confirmation.
- Canonical graph facts stay in `KnowledgeRelationship` and `KnowledgeFact`.
  `KnowledgeNode.structure` exposes derived traversal fields built by
  WorkingSet/projection.
- Stable book knowledge should not be hidden in `KnowledgeNode.attributes`.
  Use `attributes` only for temporary profile-specific values that have not yet
  earned a delegated field, relationship, or fact.
- Raw RDF triples, raw vector bodies, full provider payloads, and unbounded
  source bodies are not stored in nodes or frames.

## KnowledgeNode Section Mapping

| Section | Book mapping |
| --- | --- |
| `id` | CNCF-local `KnowledgeNodeId`, generated independently from ISBN, RDF URI, and Entity id. |
| `category` | Provisional book-oriented category when available; otherwise use a coarse operational category and set book/work/edition as semantic types. |
| `identity.rdfNode` | CNCF-owned book RDF node by default. An external RDF URI may become the node RDF identity only if the mapping profile explicitly selects it. |
| `identity.identityLinks` | Links to CNCF nodes representing same resource, same concept, equivalent work, edition, translation, alias, or external-subject projection. |
| `identity.externalIdentifiers` | ISBN-10/13, DOI, Open Library id/URI, Wikidata QID/URI, DBpedia URI, OCLC, LCCN, NDL, LOC URI, VIAF, ISNI, ORCID, source-local ids. |
| `presentation.labels` | Display title, localized titles, original title, alternative titles, series title when display-facing. |
| `presentation.names` | Canonical title/name chosen by the editor or profile. |
| `presentation.descriptions` | Summary, abstract, Open Library description, DBpedia abstract/comment, editor-written description. |
| `semantics.semanticTypes` | Book, work, edition, publication, creative work, document, authority subject, or source-specific class mappings. |
| `semantics.roles` | Operational roles such as textbook, reference work, source material, edition, translation, collection volume, or anthology item. |
| `semantics.confidence` | Confidence for identity merge, resolver match, or profile interpretation. |
| `semantics.temporal` | Publication date, edition date, assertion validity, import/observation time when appropriate. |
| `semantics.lifecycle` | Draft, confirmed, published, materialized, or other editor/runtime lifecycle state. |
| `structure.correspondences` | sameAs, exactMatch, closeMatch, source alignment, translation, localized version, alias, work/edition correspondence. |
| `structure.classifications` | Subjects, categories, genres, SKOS concepts, DBpedia categories, Wikidata classes, LCSH/NDL/LOC subject headings, projected Tags. |
| `structure.hierarchy` | Tree-like navigation view when selected by the book profile, such as series or chapter hierarchy. |
| `structure.partWhole` | Work-edition, series-volume, book-chapter, book-section, collection-member, citation-container structure. |
| `sources` | Manual input, import source, resolver source, RDF graph/source document, evidence, and provenance references. |
| `bindings` | Entity bindings and Tag bindings. Multiple domain Entities may bind to one book node after identity confirmation. |
| `similarity` | Similarity/search metadata: method, model, metric, context, provider collection/search id, status, and payload references only. |
| `operations` | Materialization time, frame membership, and validation status. |
| `attributes` | Temporary book-profile extension values only. Move stable fields into delegated sections, relationships, or facts. |

## Identity and External Anchor Mapping

The book profile classifies identifiers and anchors into four mapping roles.

| Role | Examples | Target |
| --- | --- | --- |
| CNCF identity | generated book node id, CNCF RDF node | `KnowledgeNode.id`, `identity.rdfNode` |
| External identifier | ISBN-13, ISBN-10, DOI, OCLC, LCCN, NDL, source-local id | `identity.externalIdentifiers` |
| External RDF anchor | Wikidata URI, DBpedia URI, Open Library URI, LOC URI, VIAF URI, ORCID URI, DOI URI | external identifier plus correspondence candidate |
| Domain binding | application Entity id, Tag id | `bindings` and matching external identifier convention |

External RDF anchors are not automatically `sameAs`. The mapping profile must
classify each confirmed anchor as one of:

- selected RDF node identity;
- same resource;
- same concept;
- exact match;
- close match;
- source alignment;
- weak evidence-backed correspondence.

DBpedia is an enrichment RDF source. Wikidata is a stronger authority-oriented
anchor candidate. Open Library is a book/work/edition source. None is
authoritative without InformationSpace review and confirmation.

## Relationship and Fact Mapping

`KnowledgeRelationship` is the canonical edge model. The book node may expose
derived traversal fields, but the source edge remains available.

| Book knowledge | Canonical representation | Node convenience projection |
| --- | --- | --- |
| Authorship | relationship from book/work to person/agent node | authorship structure and presentation hints |
| Editorship | relationship to person/agent with editor role | contributor structure |
| Publisher | relationship to organization/publisher node | publisher traversal field |
| Work-edition | relationship/fact between edition and work nodes | `structure.partWhole` or correspondence |
| Series-volume | part/member relationship with volume qualifier | hierarchy or part-whole view |
| Chapter/section | has-part relationship to section node | `structure.partWhole.hasPart` |
| Citation | citation relationship to cited work node | related/citation traversal |
| Subject/category | classification relationship or fact | `structure.classifications` |
| External same/near match | relationship/fact with evidence | `structure.correspondences` |

Relationship/fact qualifiers needed by the book profile:

- author order;
- contributor role;
- editor role;
- edition number;
- volume number;
- chapter/section order;
- translation language;
- citation context;
- page range;
- source profile;
- assertion confidence.

Facts record accepted statements such as ISBN assignment, publication date,
language, resolver-derived title, RDF-derived class, or Entity-derived
context. Relationship-backed facts should reference the relationship; node
facts should reference the subject node.

## RDF Vocabulary Mapping

Known RDF vocabulary families map into CNCF object-model sections. Raw
predicates are not stored as flat book fields.

### Identity and Correspondence

| RDF source | CNCF target |
| --- | --- |
| RDF subject IRI | `identity.rdfNode` only when selected; otherwise external RDF anchor |
| `owl:sameAs` | same-resource candidate with evidence |
| `schema:sameAs` | external RDF anchor or source alignment candidate |
| `skos:exactMatch` | exact-match correspondence candidate |
| `skos:closeMatch`, `skos:relatedMatch` | close-match or weak source-alignment candidate |
| `schema:isbn` | external identifier |
| DOI URI / DOI identifier | external identifier and optional RDF-like anchor |
| Open Library URI | external RDF anchor preserving work/edition role |
| Wikidata URI | authority-oriented external RDF anchor |
| DBpedia URI | enrichment external RDF anchor |

### Presentation

| RDF source | CNCF target |
| --- | --- |
| `schema:name`, `dc:title`, `dcterms:title` | labels / names |
| `schema:alternateName`, `skos:altLabel` | alternative labels or alias correspondence |
| `rdfs:label`, `skos:prefLabel` | localized labels |
| `schema:description`, `rdfs:comment`, `dbo:abstract` | descriptions |

### Semantics and Classification

| RDF source | CNCF target |
| --- | --- |
| `rdf:type schema:Book` | semantic type book |
| `rdf:type schema:CreativeWork` | semantic type creative work |
| `rdf:type bibo:Book` | semantic type bibliographic book |
| `rdf:type bf:Work`, `bf:Instance` | semantic type plus work/edition structure |
| `schema:genre`, `schema:keywords` | classifications or keyword concept nodes |
| `schema:about`, `dcterms:subject` | subject/classification relationship |
| DBpedia categories / Wikidata classes | classification candidates with source evidence |
| `skos:broader`, `skos:narrower` | classification graph, not book containment |

### Authorship, Publication, and Part-Whole

| RDF source | CNCF target |
| --- | --- |
| `schema:author`, `dcterms:creator`, `dc:creator`, `foaf:maker` | authorship relationship to person/agent |
| `schema:editor` | editor relationship |
| `schema:publisher`, `dcterms:publisher` | publisher relationship |
| `schema:datePublished`, `dcterms:issued` | temporal profile and publication fact |
| `schema:workExample`, `schema:exampleOfWork` | work-edition relationship |
| `schema:isPartOf`, `dcterms:isPartOf` | part/member relationship |
| `schema:hasPart`, `dcterms:hasPart` | has-part relationship |
| `schema:position` | relationship qualifier |
| `schema:volumeNumber`, `bibo:volume` | part-whole qualifier |

### Citation, Source, and Provenance

| RDF source | CNCF target |
| --- | --- |
| `schema:citation`, `dcterms:references`, `bibo:cites` | citation relationship |
| `dcterms:isReferencedBy`, `cito:isCitedBy` | inverse citation relationship |
| `schema:mentions`, `dcterms:relation` | related/source-alignment candidate |
| `prov:wasDerivedFrom`, `dcterms:source` | source refs / evidence |
| `prov:wasGeneratedBy` | provenance operation |
| `prov:generatedAtTime` | provenance timestamp or observed time |
| named graph / graph URI | evidence/provenance source graph |

## 1.5hop+ KnowledgeFrame Mapping

The book `KnowledgeFrame` is the materialization unit for the book meaning
neighborhood.

Common semantic-neighborhood content:

- focal `KnowledgeNode`;
- selected surrounding nodes;
- canonical relationships and facts;
- evidence and provenance;
- source references and provider/source alignments;
- derived node-convenience projections;
- explanation of why each surrounding element is included.

Book-profile extension content:

- ISBN/DOI/Open Library/Wikidata/DBpedia/LOC/VIAF/ISNI/ORCID/OCLC/LCCN/NDL
  bindings;
- author/editor/publisher nodes and authority anchors;
- work/edition/series/chapter/section nodes;
- subject/category/concept nodes;
- citation target nodes;
- source/resolver evidence and editor confirmation provenance.

The inclusion rule is semantic, not hop-count based:

```text
include surrounding knowledge when it is required to operate on, explain,
validate, retrieve, or materialize the focal book node.
```

The frame should remain bounded. Large citation lists, long source documents,
raw RDF graphs, and raw vector payloads should be referenced through summaries,
evidence, provenance, source refs, or payload references.

## InformationSpace to KnowledgeSpace Flow

The mapping profile supports this flow:

```text
book authoring field / imported RDF statement / external id
  -> InformationSpace field, candidate, identity binding, or conflict
  -> review and confirmation
  -> KnowledgeRelationship / KnowledgeFact with evidence
  -> KnowledgeNode delegated section projection
  -> 1.5hop+ KnowledgeFrame
```

Multiple application/domain Entity instances can point at one Textus book node
after identity confirmation:

```text
reading-app Entity
library-app Entity
citation-app Entity
  -> InformationSpace identity bindings
  -> one confirmed book KnowledgeNode
```

The reverse is also allowed: one domain Entity may produce several knowledge
nodes when it contains separable knowledge, such as book, author, publisher,
and cited works.

## KE-03 Decisions

- Existing delegated `KnowledgeNode` sections are sufficient for the first book
  mapping profile.
- Book-specific stable data should enter delegated sections, relationships, or
  facts, not generic attributes.
- Authorship, publisher, citation, classification, and part-whole statements
  are canonical relationships/facts with derived node convenience fields.
- External RDF anchors require InformationSpace confirmation before becoming
  canonical identity/correspondence data.
- The 1.5hop+ book `KnowledgeFrame` separates common semantic-neighborhood
  structure from book-profile extension content.
- KE-04 should expose this mapping as editor-facing projection metadata so the
  Web editor can explain what each field does.
