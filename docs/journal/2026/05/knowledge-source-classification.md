# Knowledge Source Classification for SIE

status=draft
category=journal
tags=sie,knowledge-graph,rdf,linked-data,knowledge-source

# LEAD

When discussing knowledge acquisition,
sources are often classified by domain:

- libraries
- books
- research
- geography
- cultural heritage

While this classification is useful,
it is not sufficient for designing a knowledge platform such as SIE.

For SIE,
the more important question is:

> What semantic capabilities does a knowledge source provide?

The answer determines how the source can participate in
knowledge acquisition,
identity resolution,
semantic linking,
knowledge expansion,
and semantic reasoning.

This article proposes a capability-oriented classification
of knowledge sources.

# Why Domain-Based Classification Is Not Enough

Consider the following sources:

- openBD
- OpenLibrary
- Wikidata
- VIAF
- OpenAlex

All of them provide information about books,
authors,
or publications.

However,
their semantic capabilities differ significantly.

For example:

text openBD     → bibliographic metadata  VIAF     → authority identifiers  Wikidata     → knowledge graph  OpenAlex     → scholarly relationship graph

Although these sources may appear similar from a user perspective,
they play very different roles within a semantic architecture.

# Capability Levels

SIE classifies knowledge sources according to
the semantic capabilities they provide.

# Level 0: Metadata Source

A Metadata Source provides descriptive information,
but does not provide semantic identity resolution
or knowledge graph functionality.

## Typical Knowledge

- title
- author string
- publisher
- publication date
- description
- ISBN

## Examples

- openBD
- Google Books
- Amazon Books

## Role in SIE

text Metadata Acquisition

These sources are often the starting point
of knowledge materialization.

# Level 1: Authority Source

An Authority Source provides stable identifiers
for entities such as people,
works,
organizations,
or subjects.

## Typical Knowledge

- authority identifiers
- canonical names
- aliases
- identity mappings

## Examples

- VIAF
- ORCID
- NDL Authorities

## Role in SIE

text Identity Resolution

Authority sources help determine whether
different records refer to the same entity.

# Level 2: Linked Data Source

A Linked Data Source provides RDF-based identifiers
and semantic links to other resources.

## Typical Knowledge

- RDF URIs
- sameAs relationships
- broader/narrower relationships
- external links

## Examples

- NDL Linked Data
- DBpedia
- parts of Wikidata

## Role in SIE

text Semantic Linking

These sources allow local KnowledgeNodes
to become part of the wider Linked Data ecosystem.

# Level 3: Knowledge Graph

A Knowledge Graph contains rich semantic relationships
between entities.

## Typical Knowledge

- entities
- relationships
- properties
- semantic networks

## Examples

- Wikidata
- OpenAlex
- GeoNames

## Role in SIE

text Knowledge Expansion

Once linked,
these sources can provide large amounts
of additional knowledge.

For example:

text Book     ↓ Author     ↓ Historical Period     ↓ Related Works     ↓ Cultural Assets

# Level 4: Semantic Knowledge Base

A Semantic Knowledge Base supports
formal semantic reasoning.

## Typical Knowledge

- ontologies
- class hierarchies
- equivalence relations
- inference rules

## Examples

- OWL knowledge bases
- biomedical ontologies
- domain reasoning systems

## Role in SIE

text Semantic Reasoning

These systems enable automated inference
beyond explicit relationships.

# Knowledge Source Pipeline

The capability levels naturally form a semantic pipeline.

text L0 Metadata Source         ↓ L1 Authority Source         ↓ L2 Linked Data Source         ↓ L3 Knowledge Graph         ↓ L4 Semantic Knowledge Base

Each level builds upon the previous one.

# Example: The Tale of Genji

Consider the knowledge materialization of
The Tale of Genji.

## Metadata Acquisition

text openBD     ↓ ISBN Title Author String Publisher

## Identity Resolution

text NDL Authorities VIAF     ↓ Murasaki Shikibu

## Semantic Linking

text sameAs     ↓ Wikidata URI

## Knowledge Expansion

text Wikidata OpenAlex GeoNames Japan Search

## Semantic Reasoning

text ontology-based interpretation classification relationship inference

# Access Models

Knowledge sources can also be classified
by access mechanism.

## REST API Sources

Examples:

- openBD
- OpenLibrary
- OpenAlex

text Application     ↓ REST API     ↓ Knowledge Source

## SPARQL Sources

Examples:

- Wikidata
- DBpedia

text Application     ↓ SPARQL Query     ↓ Triple Store

## RDF Dumps

Examples:

- Wikidata Dumps
- DBpedia Dumps

text Application     ↓ Local Triple Store     ↓ RDF Dump

# Implications for SIE

This classification clarifies that
not all knowledge sources should be treated equally.

Different sources serve different purposes.

text Metadata Source     → acquisition  Authority Source     → identity resolution  Linked Data Source     → semantic linking  Knowledge Graph     → knowledge expansion  Semantic Knowledge Base     → reasoning

As a result,
SIE can select the most appropriate source
for each stage of knowledge materialization.

# Conclusion

The most important characteristic of a knowledge source
is not its domain,
but its semantic capability.

By classifying sources according to capability levels,
SIE can systematically organize
knowledge acquisition,
identity management,
semantic linking,
knowledge expansion,
and reasoning.

This capability-oriented approach provides a foundation
for building a scalable and interoperable
knowledge materialization architecture.
