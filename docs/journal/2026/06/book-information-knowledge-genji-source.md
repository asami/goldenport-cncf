# Book Information and Knowledge Using The Tale of Genji

Date: 2026-06-01

Status: source material
Target article: `book-information-knowledge.dox`

## Purpose

This note provides source material for an article that follows:

- `book-knowledge-materialization-sie.dox`
- `book-knowledge-rdf-knowledge-space.dox`

The target article should explain the visible difference between:

- Book as `Information` / application `Entity`;
- Book as `Knowledge` backed by RDF links and embedding representations.

The running example is The Tale of Genji.

## Article Position

The previous articles explain that SIE can materialize Book knowledge and link
Book KnowledgeNodes to external RDF knowledge spaces.

This article should step back and explain what the user actually sees:

```text
Book Information
  -> editable bibliographic/entity data

Book Knowledge
  -> RDF-linked semantic graph
  -> embedding-backed semantic retrieval surface
```

The important message is that Information and Knowledge are not competing
models. They are two views over the same curated subject.

Information is the editable, accountable record. Knowledge is the semantic
runtime projection used for explanation, retrieval, traversal, and AI context.

## Core Distinction

For The Tale of Genji, a user may first enter or import a book record such as:

```text
Title: The Tale of Genji
Original title: Genji Monogatari
Author: Murasaki Shikibu
Language: Japanese
Subject: Japanese classical literature
Source: manual input, ISBN lookup, or library metadata
```

At this stage, the record is Information.

It is useful because it is editable, reviewable, and accountable. It can be
saved, validated, rejected, reopened, confirmed, and published.

After confirmation and materialization, the same subject becomes Knowledge.

Knowledge is not just "the same fields in another table." It is a semantic
projection:

```text
KnowledgeNode(Book or Work)
  -> author relationship to Murasaki Shikibu
  -> subject relationship to Japanese classical literature
  -> historical context relation to Heian period
  -> external RDF links to Wikidata, DBpedia, NDL, VIAF, or other authorities
  -> evidence/provenance explaining why those links were accepted
  -> embedding representation for semantic retrieval
```

## Information View

Information is the human-editable surface.

For The Tale of Genji, the Information view should look like a curated book
record:

```text
BookInformation
  id: information-record/book-...
  lifecycle: imported | needs_resolution | confirmed | published
  title: The Tale of Genji
  originalTitle: Genji Monogatari
  localizedTitles:
    - ja: 源氏物語
    - en: The Tale of Genji
  authors:
    - Murasaki Shikibu
  publicationDate:
    - original work: early 11th century
    - edition: depends on imported book
  language: ja
  summary: court narrative centered on Hikaru Genji
  subjects:
    - Japanese literature
    - Heian period
    - monogatari
  sourceUrl:
    - provider or editor-supplied source
  reviewerNote:
    - why this edition/work interpretation was accepted
```

This surface should prioritize:

- editor responsibility;
- lifecycle state;
- validation status;
- field-level guidance;
- candidate values;
- provenance hints;
- reviewer notes.

The Information view can include RDF-like identifiers, but it does not treat
them as final graph facts until review and confirmation.

## Entity View

Application Entity is the operational application object.

In a book editor application, the Entity-like surface may be:

```text
BookEntity
  entityId: book/genji
  screen label: The Tale of Genji
  form sections:
    - identity
    - contributors
    - publication
    - classification
    - external identifiers
    - review
  actions:
    - edit
    - resolve
    - select candidate
    - confirm
    - materialize
```

Entity is optimized for user workflows. It gives the application a stable
handle for forms, actions, permissions, navigation, and business logic.

Entity id, Information id, KnowledgeNode id, and RDF URI must remain distinct:

```text
Entity id       = application workflow identity
Information id  = curated/editable information identity
KnowledgeNodeId = local semantic runtime identity
RDF URI         = external or published semantic identity
```

The article should emphasize this because users often assume that an ISBN,
Wikidata URI, DBpedia URI, and local application id are interchangeable. They
are not.

## Knowledge View

Knowledge is the semantic runtime representation.

For The Tale of Genji, the Knowledge view should look more like a graph than a
form:

```text
KnowledgeNode: The Tale of Genji
  category: cultural-resource / book / work
  semanticTypes:
    - schema:CreativeWork
    - schema:Book
    - literary work
    - classical Japanese literature

Relationships:
  author -> Murasaki Shikibu
  about -> Heian court culture
  genre -> Monogatari
  period -> Heian period
  hasPart -> chapters / episodes when available
  relatedTo -> adaptations, translations, studies, manuscripts

External RDF links:
  same resource / exact match / close match:
    - Wikidata entity
    - DBpedia resource
    - NDL Authority URI
    - VIAF author authority for Murasaki Shikibu

Evidence:
  - editor confirmation
  - provider response summary
  - source URL
  - resolver confidence
```

This is the view used by:

- graph traversal;
- explanation;
- RAG context construction;
- MCP-facing curated service responses;
- semantic retrieval;
- external RDF expansion.

## RDF Appearance

The RDF appearance is not just a list of fields. It is a statement model.

Conceptual Turtle-like shape:

```text
kh:book/genji
  a schema:Book ;
  schema:name "The Tale of Genji"@en ;
  schema:name "源氏物語"@ja ;
  schema:author kh:person/murasaki-shikibu ;
  schema:about kh:concept/heian-court-culture ;
  schema:genre kh:concept/monogatari ;
  schema:inLanguage "ja" ;
  schema:sameAs <https://www.wikidata.org/entity/...> ;
  schema:sameAs <https://dbpedia.org/resource/...> ;
  prov:wasDerivedFrom kh:evidence/genji-editor-confirmation .

kh:person/murasaki-shikibu
  a schema:Person ;
  schema:name "Murasaki Shikibu"@en ;
  schema:name "紫式部"@ja ;
  schema:sameAs <https://viaf.org/viaf/...> ;
  schema:sameAs <https://id.ndl.go.jp/auth/ndlna/...> .
```

The exact URIs are not the point of the article. The point is the shift from
field values to typed statements with relationships, source, evidence, and
curated correspondence.

## Embedding Appearance

Embedding is another Knowledge projection, but it serves a different purpose
from RDF.

RDF answers:

```text
What is connected to what?
Why is this relationship accepted?
Which source or authority supports this link?
```

Embedding answers:

```text
What is semantically similar?
Which book, passage, concept, or explanation should be retrieved for this query?
Which text can be used as AI context?
```

For The Tale of Genji, embedding material may include:

```text
EmbeddingDocument
  subject: kh:book/genji
  text:
    The Tale of Genji is a classical Japanese literary work attributed to
    Murasaki Shikibu. It is associated with Heian court culture, monogatari
    narrative form, aristocratic society, and later manuscript and translation
    traditions.
  representationContext:
    - book summary
    - subject explanation
    - RAG retrieval profile
  source:
    - confirmed Information
    - selected RDF anchors
    - editor-written summary
```

The embedding vector itself should normally remain in a vector store or payload
reference, not inline in an admin/debug screen or article example.

The visible Knowledge surface should show:

- embedding profile;
- source text or summary used for embedding;
- model/provider reference when relevant;
- vector store reference;
- indexed/stale/missing status;
- retrieval score at query time.

## RDF And Embedding Together

RDF and embedding are complementary.

For a query such as:

```text
books related to Heian court culture and Murasaki Shikibu
```

Embedding can retrieve semantically relevant Book nodes and explanations.

RDF can then explain why The Tale of Genji is relevant:

```text
The Tale of Genji
  -> author -> Murasaki Shikibu
  -> period/context -> Heian period
  -> subject -> court culture
  -> genre -> monogatari
```

This is the practical difference:

- embedding finds likely relevant knowledge;
- RDF verifies, explains, and traverses structured relationships;
- Information preserves editorial accountability for the accepted knowledge.

## User-Facing Article Flow

The target article can use this flow:

1. Start with a book form for The Tale of Genji.
2. Explain that this is Information / Entity.
3. Show that the same subject becomes a KnowledgeNode after confirmation.
4. Show the RDF-like graph view.
5. Show the embedding/search view.
6. Explain how RDF and embedding cooperate in RAG.
7. Conclude that SIE separates editable truth, graph semantics, and retrieval
   representation.

Suggested article section titles:

- `Book As Information`
- `Book As Entity`
- `Book As Knowledge`
- `RDF View`
- `Embedding View`
- `RDF + Embedding For RAG`
- `Why This Separation Matters`

## Key Message

The core message for `book-information-knowledge.dox` should be:

```text
Information is what humans curate.
Entity is what applications operate.
RDF Knowledge is what systems can explain and traverse.
Embedding Knowledge is what systems can retrieve semantically.
```

For The Tale of Genji, this means the same Book appears as:

```text
Editable Information
  -> application Book Entity
  -> local KnowledgeNode
  -> RDF-linked literary/cultural graph
  -> embedding-backed retrieval target
```

Keeping these layers separate makes SIE explainable, reviewable, and useful for
AI/RAG without turning local KnowledgeSpace into a raw RDF dump or a vector-only
search index.
