# RDF-Centric Knowledge Expansion

status=draft
category=journal
tags=rdf,linked-data,knowledge-graph,sie,semantic-web

# LEAD

In the previous article,
we explored knowledge sources
and acquisition layers
for materializing book knowledge
using The Tale of Genji as an example.

However,
the goal of SIE
is not to completely copy
external knowledge into the local knowledge space.

Instead,
SIE focuses on constructing
stable semantic anchors locally,
and then connecting them
to the global RDF ecosystem.

# Basic Concept

The primary responsibility of SIE
is not full knowledge centralization.

Instead,
the local knowledge space focuses on:

- materializing core domain knowledge
- assigning stable KnowledgeNode identifiers
- linking nodes to external RDF identifiers

Once these links are established,
subsequent semantic expansion
is delegated to RDF and Linked Data technologies.

# Semantic Expansion Architecture

text Local Knowledge Space     ↓ Stable KnowledgeNode     ↓ External RDF Linking     ↓ Global Linked Knowledge Expansion

SIE therefore treats external RDF knowledge bases
not as import targets,
but as connected semantic knowledge spaces.

# Textus-SIE as a Contextual Knowledge Hub

Textus-SIE should be understood as a knowledge hub
for a particular operational and editorial context.

It does not claim to be the global truth source
for all knowledge.

Instead,
each Textus-SIE knowledge space curates
the knowledge that is meaningful
for its own project,
organization,
collection,
research activity,
or publication context.

External RDF spaces such as Wikidata,
DBpedia,
NDL Authorities,
VIAF,
OpenAlex,
GeoNames,
Japan Search,
CiNii Research,
NIJL,
and CODH remain autonomous knowledge spaces.

Textus-SIE creates local semantic anchors,
records why those anchors are meaningful
in the current context,
and links them to external RDF nodes.

text Contextual Textus-SIE Knowledge Hub     ↓ local semantic anchors     ↓ curated RDF links     ↓ external autonomous RDF spaces

# Knowledge Autonomy

This architecture also supports
knowledge autonomy.

Each Textus-SIE deployment can define:

- which external knowledge spaces are trusted
- which RDF links are accepted
- which mappings are exact,
  close,
  contextual,
  or rejected
- which local KnowledgeNodes remain canonical
  for the current context
- how editorial review and provenance are retained

This means that semantic federation
does not erase local judgment.

The local knowledge space remains autonomous,
while still participating in
larger distributed RDF traversal.

# Source Candidate Management

In an editor such as TKE,
external databases are therefore managed
as source candidates and RDF anchor candidates.

For The Tale of Genji,
the editor may show candidates from:

- openBD
- Open Library
- DBpedia
- Wikidata
- NDL Search
- Web NDL Authorities
- VIAF
- OpenAlex
- GeoNames
- Japan Search
- CiNii Research
- NIJL
- CODH
- manual RDF links
- classification RDF concepts

Some sources provide bibliographic metadata.
Some provide authority identifiers.
Some provide research,
cultural heritage,
geographic,
or digital humanities links.

The important design point is that
the editor records source data,
interpretation,
field-level provenance,
and RDF alignment separately.

This keeps imported facts,
local interpretation,
and external semantic links
reviewable.

# Example: The Tale of Genji

For example,
when materializing knowledge around The Tale of Genji,
the local knowledge space may internally manage:

- Book
- Work
- Author
- Characters
- Editions

However,
instead of duplicating all related knowledge locally,
the system links these nodes
to external RDF identifiers.

text KnowledgeNode(Book)     ├─ sameAs → Wikidata URI     ├─ sameAs → NDL Authority URI     └─ sameAs → DBpedia URI

# Delegating Knowledge Expansion

Once RDF links are established,
knowledge expansion can be handled externally.

For example:

- related historical figures
- cultural assets
- translations
- research papers
- geographic knowledge
- ontology relationships
- multilingual labels

can be resolved dynamically
through RDF technologies.

# Advantages

This architecture provides several advantages.

## Reduced Local Complexity

The local knowledge space remains relatively compact,
because it does not need to fully replicate
large external knowledge graphs.

## Stable Identity Management

The local system maintains its own stable identifiers,
while simultaneously participating
in global RDF ecosystems.

## Continuous Knowledge Growth

External knowledge bases such as Wikidata
continuously evolve.

By linking rather than copying,
the local knowledge graph can benefit from
ongoing external enrichment.

## Separation of Responsibilities

text SIE     → local semantic structure     → domain knowledge materialization     → identity management  RDF Ecosystem     → global knowledge expansion     → ontology reasoning     → linked open data integration

# RDF as a Semantic Expansion Layer

In this architecture,
RDF functions as a semantic expansion layer
outside the local knowledge space.

SIE therefore acts as:

- a semantic entry point
- a stable identity layer
- a local domain knowledge organizer

while RDF ecosystems provide:

- global connectivity
- semantic interoperability
- distributed knowledge expansion

# Knowledge Materialization Boundary

The boundary between SIE
and the RDF ecosystem
can be summarized as follows.

text SIE     → creates semantic anchors     → manages local KnowledgeNodes     → defines local semantic structure  RDF Ecosystem     → expands external relationships     → provides ontology-level reasoning     → enables linked knowledge traversal

# Distributed Knowledge Space

This architecture naturally forms
a distributed semantic knowledge space.

text Local KnowledgeNode     ↓ sameAs / RDF Links     ↓ Distributed Global Knowledge Graph

The local knowledge graph
therefore becomes part of
a much larger semantic network.

# Conclusion

The goal of knowledge materialization in SIE
is not complete knowledge centralization.

Instead,
the goal is to establish stable semantic anchors
inside the local knowledge space,
and then connect them
to the global RDF ecosystem.

This allows local domain knowledge
and global linked knowledge
to coexist
as a unified semantic structure.
