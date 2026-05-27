# Phase 27 Book Knowledge Modeling Policy

Date: 2026-05-21

## Context

Phase 27 starts from `textus-knowledge-editor` as the development driver.
The first vertical slice is book knowledge editing on top of the Phase 26
`InformationSpace` foundation.

The discussion clarified that book knowledge should not use ISBN, DBpedia, or
any other external identifier as the CNCF identity itself. CNCF should allocate
its own Knowledge node name for each book, then connect that node to multiple
external identifiers and RDF anchors.

## Decision Summary

- Every book has a CNCF-owned Knowledge node name.
- ISBN is an import/lookup key and external identifier, not the CNCF node name.
- External knowledge linkage is multi-id and multi-anchor.
- DBpedia is one RDF anchor source, not the only one.
- Wikidata is a strong candidate for authority-oriented RDF anchoring.
- Open Library, Library of Congress, VIAF, ISNI, ORCID, DOI, OCLC/LCCN/NDL, and
  other identifiers may also attach to the same CNCF book node.
- External IDs/RDF anchors are reviewed in `InformationSpace` before becoming
  confirmed Knowledge node identity/structure data.

## CNCF Book Node

CNCF should allocate a stable book Knowledge node name, for example:

```text
cncf:book/<generated-id-or-stable-slug>
```

That node is the internal CNCF knowledge object that domain logic works with.
It can carry a CNCF RDF node name while still preserving separate external RDF
anchors.

The node must not collapse these identities into one value:

- CNCF `KnowledgeNodeId`
- CNCF RDF node name
- `InformationItemId`
- ISBN-10 / ISBN-13
- DOI
- DBpedia resource URI
- Wikidata entity URI
- Open Library work/edition URI
- Library authority ids
- Entity id or Tag id

## 1.5hop+ Meaning Node Organization

Phase 27 should connect this book modeling policy to the SimpleModeling
1.5hop+ concept neighborhood idea:

- Reference article:
  `https://www.simplemodeling.org/en/knowledge-development/onefive-hop-plus.html`
- Local development URL used during this discussion:
  `http://localhost:8085/en/knowledge-development/onefive-hop-plus.html`

1.5hop+ says that a concept neighborhood is not defined by a fixed hop count.
The neighborhood should include the elements that are semantically essential to
understand the concept, while still enforcing a safe upper bound.

For Phase 27, this means a CNCF book `KnowledgeNode` is not only a row-like
book record. It is a meaning node organized around:

- the CNCF-owned book node name;
- the selected/internal RDF node for the book;
- external RDF anchors such as Wikidata, DBpedia, Open Library, and LOC;
- identifier-only bindings such as ISBN and OCLC;
- first-order book relations such as author, publisher, work/edition, series,
  chapter, citation, subject, and source;
- semantically inseparable supporting nodes, such as author authority nodes,
  subject/category nodes, work/edition nodes, and source/evidence/provenance
  nodes.

The important point is that 1.5hop+ is not a graph-distance rule. It is a
semantic inclusion rule. A book node's operational neighborhood should include
the nodes and relationships required for domain logic, editor explanation, and
AI retrieval/explanation to understand the book.

Phase 27 should keep two layers separate:

- common semantic neighborhood;
- book-oriented extension.

The common semantic neighborhood is the CNCF-level contract. It should be
usable by book, paper, web, and later knowledge domains. It defines how a
focal `KnowledgeNode` is surrounded by meaning-bearing nodes, relationships,
facts, evidence, provenance, identity bindings, RDF anchors, and source
references. This layer belongs to the reusable KnowledgeNode / KnowledgeFrame
model and should not mention ISBN, author order, publisher, or other
book-specific concepts as core requirements.

The book-oriented extension is a domain profile on top of the common
neighborhood. It selects which book relationships and supporting nodes are
essential for book understanding, editor explanation, and retrieval. ISBN,
Open Library, Wikidata, DBpedia, author authority, work/edition, publisher,
series, chapter, subject, and citation handling belong here unless they prove
to be reusable across domains.

Example:

```text
CNCF Book KnowledgeNode
  -> title / labels / descriptions
  -> ISBN / DOI / OpenLibrary / Wikidata / DBpedia anchors
  -> author authority nodes
      -> VIAF / ISNI / ORCID / Wikidata anchors
  -> publisher node
  -> work / edition node
  -> chapter / section nodes when modeled
  -> subject / category nodes
  -> citation target nodes
  -> evidence / source / provenance nodes
```

This is a 1.5hop+ neighborhood because it includes nodes beyond simple 1-hop
edges when those nodes carry essential meaning, such as author authority
identity, subject hierarchy, work/edition distinction, or source provenance.

The mapping profile should therefore produce:

```text
RDF node / external id / imported source
  -> InformationSpace candidate
  -> semantic mapping profile
  -> KnowledgeNode meaning node
  -> 1.5hop+ KnowledgeFrame neighborhood
```

`KnowledgeFrame` is the natural unit for a materialized 1.5hop+ neighborhood.
It can group the focal book node, selected surrounding nodes, relationships,
facts, evidence, and provenance that make the book meaning operationally
usable.

## Person and Organization Knowledge Dependency

Book Knowledge cannot stop at plain string fields for contributors and
publishers. A materialized book neighborhood needs adjacent Person and
Organization knowledge when those identities are present or resolvable.

Person knowledge is required for:

- author;
- editor;
- translator;
- annotator/commentator;
- contributor;
- cited or related creator when relevant to the book neighborhood.

Organization knowledge is required for:

- publisher;
- imprint;
- institution;
- series owner;
- library or authority organization when it is part of the evidence trail.

These nodes are not embedded values of the book node. They are separate
Information/Knowledge candidates linked to the book through relationships and
evidence:

```text
Book Information
  -> authored-by -> Person Information / KnowledgeNode
  -> edited-by -> Person Information / KnowledgeNode
  -> translated-by -> Person Information / KnowledgeNode
  -> published-by -> Organization Information / KnowledgeNode
```

Identifiers for Person and Organization knowledge should remain separate from
book identifiers. Useful external anchors include Wikidata, DBpedia, VIAF,
ISNI, ORCID, LCNAF, NDL authority ids, ROR, and publisher-local ids. Imported
author or publisher names remain editable/candidate Information until authority
resolution confirms the binding.

The editor should therefore show unresolved contributor/publisher names as
resolution work, not as complete knowledge. A book can still be edited with
plain text, but Knowledge materialization should explain whether the
corresponding Person/Organization nodes are confirmed, unresolved, or omitted.

## Work, Edition, Series, and Volume Structure

Book identity also requires more than a single publication title when the
source represents an edition, series, or multi-volume set.

Example:

```text
Work:
  Genji monogatari

Edition / publication set:
  Iwanami edition of Genji monogatari

Volumes:
  Genji monogatari (1)
  Genji monogatari (2)
  ...
  Genji monogatari (9)

Organization:
  Iwanami Shoten

Person:
  Murasaki Shikibu
  editors / annotators / translators where applicable
```

The operational model should distinguish:

| Concept | Role |
| --- | --- |
| Work | Abstract intellectual work, such as Genji monogatari. |
| Edition | Specific publication/editorial realization of the work. |
| Series | Publication series or collection context, such as Iwanami Bunko. |
| Volume | One member of a multi-volume edition or publication set. |
| Publication/book item | ISBN-bearing publication unit, often aligned with a volume. |

The first TKE book UI does not need to implement a full FRBR/LRM model, but it
must not erase these distinctions. The book profile should allow fields or
resolver candidates such as:

- work title;
- edition title;
- series title;
- volume title;
- volume number;
- total volumes;
- source/original work;
- edition contributor roles;
- publisher/imprint organization.

Knowledge materialization should represent these distinctions as nodes and
relationships when they are known:

```text
Volume --volume-of--> Edition
Edition --realizes-work--> Work
Edition --published-by--> Organization
Volume --part-of-series--> Series
Book/Volume --authored-by--> Person
```

This means book Knowledge is not "one ISBN input -> one isolated
KnowledgeNode". It is "one focal publication or volume -> a bibliographic
meaning neighborhood". The closure unit is the `KnowledgeFrame`, not a single
flat node.

### Common Semantic Neighborhood

The common semantic neighborhood should be domain-neutral. It is the shared
shape that CNCF can provide for all knowledge authoring domains:

```text
focal KnowledgeNode
  -> identity / RDF node / external identifiers
  -> presentation labels and descriptions
  -> semantic types and classifications
  -> canonical KnowledgeRelationships
  -> KnowledgeFacts
  -> evidence and provenance
  -> source references and provider/source alignments
  -> derived node-convenience attributes
```

The reusable rule is:

```text
include surrounding knowledge when it is semantically required to operate on,
explain, validate, retrieve, or materialize the focal node.
```

This means CNCF should provide the projection and explanation vocabulary for
why a surrounding node or edge is included. The common layer should answer
questions such as:

- Is this surrounding node an identity anchor, classification, part-whole
  member, source, evidence, provenance, correspondence, or operational
  support node?
- Is the value canonical as a `KnowledgeRelationship` / `KnowledgeFact`, or is
  it a derived convenience field on the focal `KnowledgeNode`?
- Which InformationSpace record, source, or RDF/provider input produced it?
- Can it be shown in the editor without exposing raw RDF or provider payloads?

### Book-Oriented Extension

The book profile specializes the common semantic neighborhood:

```text
Book KnowledgeNode
  -> book identity bindings
  -> bibliographic presentation fields
  -> book/work/edition semantic types
  -> author/editor/publisher relationships
  -> work/edition/series/chapter part-whole structure
  -> subject/category/classification links
  -> citation links
  -> Open Library / Wikidata / DBpedia / LOC / VIAF / ISNI / ORCID anchors
  -> ISBN / DOI / OCLC / LCCN / NDL identifiers
  -> import source, resolver evidence, editor confirmation provenance
```

Book-specific values should enter the common model through a mapping profile,
not by adding bibliographic assumptions to the generic `KnowledgeNode`
contract. For example:

- ISBN is a book profile identity binding, not a generic node id.
- author order is a book relationship/fact qualifier, not a generic
  relationship kind.
- work/edition/manifestation pressure is a book-oriented part-whole/profile
  concern until it proves reusable enough for a common model.
- DBpedia category and Wikidata class mappings are source/profile
  interpretations before they become confirmed CNCF classifications.

## External ID / RDF Anchor Classification

RDF-node-like anchors:

- Wikidata entity URI, such as `https://www.wikidata.org/entity/Q...`
- DBpedia resource URI, such as `http://dbpedia.org/resource/...`
- Open Library work/edition URI when used as a dereferenceable resource
- Library of Congress linked-data URI
- VIAF authority URI
- ROR organization URI when relevant
- DOI URI when normalized as `https://doi.org/...`
- ORCID URI for contributors

Identifier-only or lookup-key values:

- ISBN-10
- ISBN-13
- OCLC number
- LCCN value before conversion to a LOC URI
- NDL id before conversion to an NDL resource URI
- Publisher-local ids

## Wikidata and DBpedia Roles

Wikidata should be treated as an authority-oriented external knowledge hub.
Its QID is a strong candidate external RDF anchor for books, authors,
publishers, concepts, and places.

DBpedia should be treated as a Wikipedia-derived RDF enrichment source. It is
useful for descriptions, categories, links, and Wikipedia-adjacent relationships.
It may align with Wikidata, but CNCF should not require DBpedia to be the
primary anchor.

Both can attach to the same CNCF book node:

```text
CNCF Book KnowledgeNode
  identity:
    cncfRdfNode: cncf:book/...
    externalRdfAnchors:
      - system: wikidata
        rdfNode: https://www.wikidata.org/entity/Q...
        match: exactMatch
      - system: dbpedia
        rdfNode: http://dbpedia.org/resource/...
        match: closeMatch
    externalIdentifiers:
      - system: isbn13
        value: 978...
      - system: doi
        value: ...
```

## InformationSpace Flow

The book editing flow should start from either manual entry or an identifier:

```text
ISBN / DOI / title+author / other id
  -> candidate lookup in Open Library, Wikidata, DBpedia, LOC, VIAF, ...
  -> InformationSpace staging record
  -> editor review and correction
  -> identity binding confirmation
  -> publication/materialization
  -> KnowledgeNode / KnowledgeRelationship in KnowledgeSpace
```

External data remains candidate data until reviewed. Even when an ISBN lookup or
DBpedia/Wikidata match is high confidence, it should be represented with source,
evidence, provenance, and confidence in `InformationSpace`.

## Domain Entity Multiplicity and Single Textus Knowledge

Applications may each own their own book-related Entity model. A reading-list
application, a library-holding application, a citation application, and a
publisher workflow may all have different Entity instances for what humans
would call the same book or work.

The target operating model is:

```text
many domain Entity instances
  -> paired / resolved InformationSpace items
  -> one Textus Knowledge meaning node when identity resolution confirms it
  -> KnowledgeFrame neighborhoods in KnowledgeSpace
```

This means Entity multiplicity is normal. The same external book can be
represented by multiple application-local entities because each entity carries
domain context, lifecycle, permissions, and operational state. Textus Knowledge
should act as the semantic integration point, not as a replacement for those
domain entities.

Examples:

```text
reading-app:ReadingItem(id = r-123, isbn = 978...)
library-app:LibraryHolding(id = h-456, isbn = 978..., shelf = ...)
citation-app:CitedWork(id = c-789, doi = ...)
  -> InformationSpace identity bindings and resolution evidence
  -> Textus KnowledgeNode cncf:book/...
```

Design rules:

- Domain `Entity.id`, `InformationItemId`, `KnowledgeNodeId`, RDF node URI,
  ISBN, DOI, and authority ids remain distinct.
- Entity-derived facts are important knowledge inputs, but they are still
  evidence-backed projections from a domain context.
- A domain Entity can bind to an existing Textus book Knowledge node, create a
  candidate for a new one, or remain unresolved.
- Multiple domain Entities can bind to one Knowledge node when resolution is
  confirmed.
- One domain Entity may produce multiple knowledge nodes when the domain record
  contains separable knowledge, such as book plus author plus publisher plus
  cited work.
- Textus Knowledge should preserve which application/domain Entity contributed
  each fact, relationship, or confirmation.

For Phase 27, book editing should therefore expose both sides:

- application/domain Entity context when a book record comes from an existing
  application model;
- Textus Knowledge identity and 1.5hop+ neighborhood when the record is
  resolved and materialized.

## KnowledgeNode Mapping Implications

The book Knowledge node should expose structured attributes that domain logic
can traverse without parsing raw RDF:

- identity: CNCF RDF node, external RDF anchors, external identifiers
- presentation: title, localized titles, subtitle, descriptions, summaries
- semantics: semantic types, genre/subject/classification, language, confidence
- structure: work/edition relation, chapters/sections, authorship, publisher,
  citations, same-as/exact-match/close-match/source alignment
- sources: Open Library, Wikidata, DBpedia, LOC, VIAF, local/manual input
- evidence/provenance: imported source, retrieval time, operation context,
  selected candidate, confirmation state

KnowledgeRelationship remains the source for graph edges. KnowledgeNode may
carry structured attributes derived from those relationships for convenient
domain logic traversal, but it should not own relationship ids as its primary
structure.

## KnowledgeNode Section Mapping

The current CNCF `KnowledgeNode` model is delegation-oriented:

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

For book knowledge, the sections should be used as follows.

| KnowledgeNode section | Book content |
| --- | --- |
| `id` | CNCF-local `KnowledgeNodeId`. Generated by CNCF; not ISBN, DBpedia, Wikidata, or Open Library id. |
| `category` | `book` or a provisional category under the current `KnowledgeNodeCategory` model. If the runtime keeps a small category set, use `concept` / `external-subject` plus semantic type `book`. |
| `identity.rdfNode` | CNCF-owned book RDF node, such as `cncf:book/<id>`, or a selected RDF node only when the profile explicitly chooses an external RDF node as the node identity. |
| `identity.identityLinks` | Links to other CNCF `KnowledgeNodeId`s that represent same/equivalent book, work, edition, translation, or external subject projections. |
| `identity.externalIdentifiers` | ISBN-10/13, DOI, Open Library id, Wikidata QID, DBpedia URI, LOC URI, VIAF/ISNI/ORCID for contributors, OCLC/LCCN/NDL, publisher ids, and source-local ids. |
| `presentation.labels` | Display title, localized title, alternative title, original title, series title when display-facing. |
| `presentation.names` | Canonical book title/name chosen by the editor/profile. |
| `presentation.descriptions` | Abstract, summary, DBpedia abstract/comment, Open Library description, editor-written description. |
| `semantics.semanticTypes` | Book/work/edition/publication/creative-work/document semantic types from RDF classes, schema.org types, BIBO/BIBFRAME classes, or editor/domain model. |
| `semantics.roles` | Operational roles such as reference work, textbook, source, edition, translation, collection volume, or source material. |
| `semantics.confidence` | Confidence of imported/merged identity and enrichment candidates. |
| `semantics.temporal` | Publication date, edition date, valid date range for assertions, observed/imported date where appropriate. |
| `semantics.lifecycle` | CNCF/editor lifecycle state such as draft, confirmed, published, materialized; not raw RDF lifecycle. |
| `structure.correspondences` | Same/equivalent resources, translations, localized versions, alias nodes, source alignments, work/edition correspondences when represented as node correspondence. |
| `structure.classifications` | Subject categories, genres, SKOS concepts, DBpedia categories, Wikidata classes, LCSH/NDL/LOC subjects, Tags if projected. |
| `structure.hierarchy` | Series/volume/chapter hierarchy only when the profile chooses a tree-like navigation view. |
| `structure.partWhole` | Work-edition, book-chapter, book-section, series-volume, collection-member, citation-container relations. |
| `sources` | Open Library, Wikidata, DBpedia, LOC, VIAF, Crossref/DataCite, manual editor input, import operation, source document/chunk references. |
| `bindings` | CNCF Entity/Tag bindings when the book is also represented as an Entity or classified by `TagSpace`. |
| `similarity` | Search representation for book text/metadata summaries, provider collection/search ids, similarity status. Raw vector bodies stay outside the node. |
| `operations` | Materialization time, frame membership, validation status. |
| `attributes` | Temporary or profile-specific extension values only. Stable book knowledge should move into the delegated sections above. |

This implies a design rule for Phase 27:

```text
book field / imported RDF statement
  -> InformationSpace editable field or candidate
  -> KnowledgeRelationship / KnowledgeFact with evidence
  -> canonical KnowledgeNode section when accepted by the mapping profile
```

## Book RDF Vocabulary Mapping

RDF vocabulary mapping should be profile-driven. CNCF should not store raw RDF
predicates as flat attributes. Instead, known RDF vocabulary families map into
`KnowledgeNode` sections and `KnowledgeRelationshipKind`s.

### Identity and Equivalence

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| RDF subject IRI | `identity.rdfNode` or external RDF identifier | CNCF keeps its own node id. External subject remains explicit. |
| `owl:sameAs` | `identity.identityLinks.sameAs` / `same-resource-as` relationship | Strong identity candidate. Must still be reviewable when imported. |
| `schema:sameAs` | `same-resource-as` / external RDF anchor | Often used for Wikidata/DBpedia/OpenLibrary cross-links. |
| `skos:exactMatch` | `same-concept-as` or `same-resource-as` depending on profile | Exact concept match; for books decide whether it identifies the same work/resource. |
| `skos:closeMatch` / `skos:relatedMatch` | `equivalentTo`, `closeMatch`, or weaker source alignment | Weaker than same-as. |
| DOI URI / `schema:identifier` DOI | `identity.externalIdentifiers` | DOI may also be represented as `https://doi.org/...` RDF-like anchor. |
| ISBN literal / `schema:isbn` | `identity.externalIdentifiers` | Lookup key and external id, not RDF node. |
| Open Library URI | external RDF anchor / source alignment | Work/edition distinction must be preserved. |
| Wikidata entity URI | external RDF anchor | Strong authority anchor candidate. |
| DBpedia resource URI | external RDF anchor / enrichment source | Useful for labels, abstract, categories, sameAs links. |

### Presentation

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| `schema:name` | `presentation.labels` / `presentation.names.canonical` | Candidate canonical title. |
| `schema:alternateName` | `presentation.labels.alternatives` or alias correspondence | Literal alias normally stays presentation; node alias uses relationship. |
| `rdfs:label` | `presentation.labels` | Preserve language tags. |
| `skos:prefLabel` | `presentation.labels.localized` / canonical label candidate | Useful for authority labels. |
| `skos:altLabel` | `presentation.labels.alternatives` | Alternative display labels. |
| `dc:title`, `dcterms:title` | `presentation.labels` / canonical title | Common in bibliographic metadata. |
| `schema:description` | `presentation.descriptions` | May need summary/redaction. |
| `rdfs:comment` | `presentation.descriptions` | DBpedia abstracts/comments can map here. |
| `dbo:abstract` | `presentation.descriptions` | DBpedia-specific abstract; preserve language. |

### Book Type and Semantics

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| `rdf:type schema:Book` | `semantics.semanticTypes` | Book semantic type. |
| `rdf:type schema:CreativeWork` | `semantics.semanticTypes` | Broader type. |
| `rdf:type bibo:Book` | `semantics.semanticTypes` | Bibliographic ontology type. |
| `rdf:type bf:Work` / `bf:Instance` | `semantics.semanticTypes` and structure | BIBFRAME can distinguish work/instance. |
| `schema:bookEdition` | `semantics.roles` / `structure.partWhole` | Edition-specific metadata. |
| `schema:inLanguage`, `dc:language`, `dcterms:language` | `semantics` / `presentation` | Language is semantic metadata and affects localized labels. |
| `schema:genre` | `structure.classifications` / semantic type | Genre may be classification node or literal candidate. |
| `schema:keywords` | `structure.classifications.additional` | May materialize as keyword/concept nodes. |
| `dcterms:subject`, `schema:about` | `structure.classifications` | Subject/concept classification. |
| `skos:broader`, `skos:narrower` | `structure.classifications.broader/narrower` | Classification graph, not necessarily book containment. |

### Authorship, Publication, and Organizations

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| `schema:author`, `dcterms:creator`, `dc:creator`, `foaf:maker` | `KnowledgeRelationship` to person/agent node; derived node structure for authorship | Do not flatten person identity into book node only. |
| `schema:editor` | relationship to editor node | Editor role should be explicit. |
| `schema:publisher`, `dcterms:publisher` | relationship to organization/publisher node | Publisher may have Wikidata/ROR/ISNI anchor. |
| `schema:datePublished`, `dcterms:issued` | `semantics.temporal` and publication fact | Preserve precision if only year/month is known. |
| `schema:copyrightYear` | publication/legal fact | May be separate from publication date. |
| `schema:license`, `dcterms:license` | `semantics.confidentiality` / source policy / rights metadata | Do not confuse source license with book rights unless profile says so. |
| `dcterms:rights`, `schema:copyrightHolder` | rights/provenance relationship | May need a rights holder node. |

### Work, Edition, Series, Chapter, and Part-Whole

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| `schema:workExample` / `schema:exampleOfWork` | `structure.partWhole` / work-edition relationship | Useful for work vs edition distinction. |
| `schema:isPartOf`, `dcterms:isPartOf` | `structure.partWhole.partOf` | Series, collection, book/chapter containment depending on object type. |
| `schema:hasPart`, `dcterms:hasPart` | `structure.partWhole.hasPart` | Chapter/section/volume relation. |
| `schema:position` | relationship qualifier / chapter ordering fact | Order is not just a relationship kind. |
| `schema:pagination`, `bibo:pages` | attributes or fact on edition/chapter | Should be typed in a later field model. |
| `schema:volumeNumber`, `bibo:volume` | structure/part-whole qualifier | Series/volume modeling. |
| `schema:bookFormat` | semantic/profile metadata | Hardcover, ebook, etc. |

### Citations and Related Works

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| `schema:citation`, `dcterms:references`, `bibo:cites` | `KnowledgeRelationship` citation edge | Relationship from citing book/work to cited work. |
| `dcterms:isReferencedBy`, `cito:isCitedBy` | inverse citation edge | Can be indexed from relationship graph. |
| `schema:mentions`, `schema:about` | related concept/entity relationship | Often weaker than citation. |
| `dcterms:relation`, `schema:relatedLink` | source alignment or related relationship | Profile must classify strength. |

### Sources, Evidence, and Provenance

| RDF vocabulary / predicate | CNCF target | Notes |
| --- | --- | --- |
| `prov:wasDerivedFrom` | `sources.sourceRefs` / evidence | Source of imported claim. |
| `prov:wasGeneratedBy` | provenance operation/generator | Import, resolver, or materialization operation. |
| `prov:generatedAtTime` | provenance timestamp / `semantics.temporal.observedAt` | Distinguish observation/import time from publication date. |
| `dcterms:source` | `sources.sourceRefs.primarySource` | Source record or source graph. |
| `schema:mainEntityOfPage` | source document / identity link | Often Wikipedia/DBpedia page alignment. |
| named graph / graph URI | evidence/provenance | Important for RDF source trust. |

## Book-Specific KnowledgeRelationship Mapping

`KnowledgeRelationship` is the canonical edge model. The book node can expose
derived structure for common traversal, but the source edge should remain
available.

| Book relation | Likely relationship kind | RDF source candidates |
| --- | --- | --- |
| book same as Wikidata/DBpedia/OpenLibrary resource | `same-resource-as`, `same-as`, `source-aligned-with` | `owl:sameAs`, `schema:sameAs`, `skos:exactMatch`, source profile |
| book has author | profile-specific `authored-by` or generic `related` until a kind is added | `schema:author`, `dcterms:creator`, `foaf:maker` |
| book has editor | profile-specific `edited-by` or generic `related` until a kind is added | `schema:editor` |
| book published by publisher | profile-specific `published-by` or generic `related` until a kind is added | `schema:publisher`, `dcterms:publisher` |
| book is edition of work | `same-concept-as`, `source-aligned-with`, or future `edition-of` | `schema:exampleOfWork`, BIBFRAME Work/Instance |
| book is part of series | `part-of` / `member-of` | `schema:isPartOf`, `dcterms:isPartOf` |
| book has chapter/section | `has-part` | `schema:hasPart`, `dcterms:hasPart` |
| book cites another work | profile-specific `cites` or generic `related` until a kind is added | `schema:citation`, `bibo:cites`, `dcterms:references` |
| book about subject | `classified-by` / `related` | `schema:about`, `dcterms:subject`, SKOS concept links |

Phase 27 should avoid forcing all of these into the existing generic
relationship kinds if the domain needs clearer semantics. It can first record
the RDF predicate and semantic type on `KnowledgeRelationship`, then add
specific relationship kinds only where domain logic needs stable traversal.

## Mapping Profile Responsibilities

The book mapping profile should decide:

- which external RDF anchor, if any, becomes `identity.rdfNode`;
- which anchors remain external identifiers only;
- whether a Wikidata/DBpedia/OpenLibrary match is `sameAs`, `exactMatch`,
  `closeMatch`, or source alignment;
- whether a RDF class becomes a semantic type, classification, or both;
- whether a relation becomes a node structured attribute, a
  `KnowledgeRelationship`, or both;
- how source confidence and human confirmation change candidate status;
- whether work/edition/chapter are separate nodes or sections of the same node
  for the current editor slice.

The profile should be conservative. Imported RDF creates candidates; confirmed
InformationSpace choices create canonical KnowledgeNode projections.

## Review Follow-Up: Runtime Pressure Points

The review confirmed the overall layering:

```text
RDF import
  -> InformationSpace candidate
  -> semantic interpretation profile
  -> KnowledgeNode projection
  -> runtime traversal
```

It also clarified several Phase 27 pressure points.

### Work / Edition / Manifestation

Book knowledge will eventually face FRBR/LRM-style pressure:

- work;
- expression;
- edition;
- manifestation;
- item;
- chapter/section.

Phase 27 should stay conservative. The book editor can model the fields needed
for the first workflow, but it should not prematurely freeze a full
bibliographic ontology as CNCF runtime structure. The mapping profile should
decide whether work/edition/chapter become separate nodes, part-whole
relationships, or structured fields for the current slice.

### Relationship Qualifiers

Several book facts are not captured by relationship kind alone:

- author order;
- contributor role;
- editor role;
- chapter order;
- edition number;
- volume number;
- translation language;
- citation context;
- page range;
- confidence and source-specific assertion status.

These should be treated as relationship/fact qualifiers. Phase 27 should avoid
creating many relationship kinds only to encode qualifier data. The canonical
edge remains `KnowledgeRelationship`; qualifier details should remain attached
to the relationship, fact, evidence, or mapping profile result.

### Edge-Canonical / Node-Convenience Projection

The review highlighted a core rule:

```text
edge canonical
node convenience projection
```

Authorship, publisher, citation, classification, work/edition, and part-whole
relations should remain canonical relationships. `KnowledgeNode` may expose
derived structured attributes for easy domain traversal, but those attributes
must remain traceable back to relationships, facts, evidence, and provenance.

### KnowledgeFact and Provenance Growth

Some book information is better understood as factual assertion rather than a
relationship:

- publication date;
- ISBN assertion;
- page count;
- copyright year;
- chapter numbering;
- edition statement;
- confidence-scored source claim.

Phase 27 can use the existing `KnowledgeFact` path where available, but should
not require a full claim/provenance subsystem before the first editor slice.
The model should leave room for future explicit structures such as Claim,
Observation, Source, ImportOperation, and ConfirmationOperation.

### Mapping Profile as Semantic Runtime Compiler

The mapping profile is effectively the semantic runtime compiler:

```text
RDF vocabulary / external source structure
  -> profile interpretation
  -> InformationSpace candidate fields
  -> KnowledgeRelationship / KnowledgeFact
  -> KnowledgeNode section projection
```

This layer is strategically important. CNCF should keep it explicit rather than
letting provider-specific RDF or DBpedia/Wikidata shapes leak directly into the
runtime object model.

## Phase 27 Impact

Phase 27 should implement the book-first editor around this policy:

- KE-02 defines book fields and identifier/anchor vocabulary.
- KE-03 defines book-to-KnowledgeNode mapping, including section-level mapping
  and RDF vocabulary mapping.
- KE-03/KE-04 should preserve edge-canonical / node-convenience projection and
  identify qualifier needs without overfitting relationship kinds.
- KE-06 implements ISBN/multi-id import, DBpedia/Wikidata-style RDF anchor
  review, and book KnowledgeSpace materialization.

Paper and web knowledge can follow once the book path proves the
multi-identifier and RDF-anchor model.
