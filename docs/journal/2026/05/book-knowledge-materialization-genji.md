# Book Knowledge Materialization for The Tale of Genji

status=draft
category=journal
tags=knowledge-graph,rdf,linked-data,book,sie,genji

# LEAD

When materializing books as knowledge,
bibliographic information obtained from ISBN alone is not sufficient.

Especially for classical literature such as The Tale of Genji,
it is necessary to integrate multilayered knowledge including
works, textual variants, people, places, historical eras,
research history, and cultural assets.

In SIE (Semantic Information Engine),
such knowledge can be integrated through RDF URI–based identifiers
and managed as KnowledgeNodes,
allowing books to be transformed into knowledge graphs.

# Knowledge Layers Around Books

Book knowledge is not derived from a single data source,
but rather from multiple knowledge layers.

text ISBN / Physical Book     ↓ Bibliographic Metadata     ↓ Work / Literary Entity     ↓ People / Place / Era     ↓ Concept / Theme     ↓ Research / Cultural Knowledge     ↓ RDF Linked Knowledge

# Basic Bibliographic Metadata

## openBD

- https://openbd.jp/

### Knowledge

Provides ISBN-based bibliographic metadata.

- ISBN
- title
- author
- publisher
- publication date
- price
- cover image
- description
- NDC classification

### Role in SIE

Used as the foundational metadata layer
for physical books.

# National Library Knowledge

## NDL Search

- https://ndlsearch.ndl.go.jp/

### Knowledge

- authority identifiers
- subject headings
- related books
- editions
- annotated editions
- translations
- bibliographic identifiers

### Important Point

For classical works such as The Tale of Genji,
relationships among editions,
reprints,
and annotated versions become important.

# Authority Knowledge

## Web NDL Authorities

- https://id.ndl.go.jp/auth/ndla/

### Knowledge

- people URI
- work URI
- subject URI
- relation knowledge

### Example

text Murasaki Shikibu     ↓ NDL Authority URI     ↓ stable KnowledgeNode

### Role in SIE

Can be used as stable identifiers
for KnowledgeNodes.

## VIAF

- https://viaf.org/

### Knowledge

- international person authority identifiers
- library authority crosswalks
- alternate names
- related authority identifiers

### Role in SIE

Useful for connecting Person KnowledgeNodes,
such as Murasaki Shikibu,
to international library authority ecosystems.

# Global Linked Data

## Wikidata

- https://www.wikidata.org/

### Knowledge

- multilingual labels
- literary genre
- historical period
- related people
- characters
- translations
- adaptations
- external identifiers

### Important Point

Wikidata contains extremely rich structured knowledge
around The Tale of Genji.

### Example Relations

text The Tale of Genji  ├─ author → Murasaki Shikibu  ├─ genre → Monogatari  ├─ period → Heian period  ├─ character → Hikaru Genji  └─ adaptation → films / anime / manga

# Narrative Knowledge

## Wikipedia

- https://ja.wikipedia.org/

### Knowledge

- summaries
- story explanations
- chapter structures
- research history
- character descriptions

### Role

Rather than RDF,
this serves as an important natural-language knowledge source
for LLMs.

# Classical Literature Knowledge

## National Institute of Japanese Literature

- https://www.nijl.ac.jp/

### Knowledge

- original texts
- manuscript information
- textual variants
- annotations
- historical materials

### Important Point

Provides knowledge closer to the literary work itself,
rather than merely the published book.

# Digital Humanities Knowledge

## CODH

- https://codh.rois.ac.jp/

### Knowledge

- classical book images
- IIIF metadata
- OCR data
- kuzushiji recognition
- manuscript images

### Interesting Point

The images themselves can be materialized
as KnowledgeNodes.

# Academic Research Knowledge

## CiNii Research

- https://cir.nii.ac.jp/

### Knowledge

- academic papers
- researchers
- citation relationships
- related studies

### Important Point

Enables acquisition of a massive knowledge network
surrounding Genji studies.

## OpenAlex

- https://openalex.org/

### Knowledge

- scholarly works
- authors
- institutions
- concepts
- citation relationships

### Role in SIE

Useful for connecting local research-oriented KnowledgeNodes
to global scholarly metadata
without copying the entire research graph locally.

# Cultural Heritage Knowledge

## Japan Search

- https://jpsearch.go.jp/

### Knowledge

- artworks
- cultural assets
- illustrated scrolls
- museum resources

### Example

text The Tale of Genji     ↓ Genji Monogatari Emaki     ↓ museum collections

# Geographic Knowledge

## Geonames

- https://www.geonames.org/

### Knowledge

- place names
- coordinates
- geographic hierarchy

### Example

- Uji
- Kyoto
- Rokujoin

# Knowledge Graph Structure

In SIE,
book knowledge is treated not merely as metadata,
but as interconnected KnowledgeNodes.

text Book  ├─ hasAuthor → Murasaki Shikibu  ├─ hasCharacter → Hikaru Genji  ├─ hasCharacter → Lady Murasaki  ├─ hasPlace → Uji  ├─ hasPeriod → Heian period  ├─ hasTheme → mono no aware  ├─ hasEdition → Iwanami Bunko Edition  ├─ hasResearch → academic papers  ├─ relatedArtwork → Genji scrolls  └─ sameAs → Wikidata / NDL URI

# Knowledge Materialization Flow

text ISBN     ↓ openBD     ↓ Book Metadata     ↓ NDL / Wikidata Linking     ↓ People / Place / Era Expansion     ↓ Research / Cultural Knowledge Expansion     ↓ Knowledge Graph Materialization

# Transition to RDF-Centric Expansion

This article explained
the knowledge sources
and knowledge acquisition layers
used for book knowledge materialization.

However,
SIE does not attempt
to completely internalize
all external knowledge
inside the local knowledge space.

Instead,
SIE establishes stable KnowledgeNodes
and connects them to external RDF nodes.

Subsequent semantic expansion
is delegated to the RDF and Linked Data ecosystem.

This architecture is discussed
in the next article.

# Why The Tale of Genji is Interesting

The Tale of Genji
is not merely a “book.”

It contains multilayered knowledge including
the literary work itself,
textual variants,
research history,
cultural assets,
human relationships,
historical background,
and geographic knowledge.

Therefore,
it serves as an excellent subject
for validating KnowledgeNode design
and RDF integration.

# Conclusion

The important aspect of book knowledge materialization
is not merely ISBN-based bibliographic metadata,
but the expansion of knowledge toward
works,
people,
concepts,
research,
and cultural heritage.

By integrating these elements through RDF URI–based linking,
SIE enables books to be handled as knowledge graphs.
