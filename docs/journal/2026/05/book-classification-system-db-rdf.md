Book Classification Systems, Databases, and RDF Relationship
======

# Overview

This note organizes:

- major Book classification systems
- representative classification databases
- the relationship between each classification system/database and RDF/Linked Data

The purpose is to clarify how Book classification knowledge can be integrated into
Semantic Integration Engine (SIE) and KnowledgeSpace.

# Why Book Classification Matters

Book classification is not merely a labeling mechanism.

Classification systems provide:

- semantic hierarchy
- concept normalization
- graph traversal structure
- recommendation hints
- RAG expansion paths
- multilingual semantic anchors

In SIE, classification should therefore be treated as:

```text
classification
  ->
KnowledgeNode relation
```

rather than as plain strings.

# Classification System Categories

Book classification systems can broadly be divided into the following categories.

| category | purpose |
| --- | --- |
| Library Classification | physical/library organization |
| Subject Heading | semantic subject indexing |
| Genre Classification | reader-oriented categorization |
| Commercial Classification | bookstore / sales classification |
| Knowledge Graph Classification | graph-oriented semantic linkage |
| Folksonomy / Tags | lightweight user-generated categorization |

# Library Classification Systems

## Dewey Decimal Classification (DDC)

### Overview

DDC is one of the most widely used library classification systems.

Example:

```text
500 Science
510 Mathematics
512 Algebra
```

### Characteristics

- hierarchical numeric structure
- stable taxonomy
- academic orientation
- globally recognized

### RDF Relationship

DDC itself predates RDF, but its hierarchical structure maps naturally into RDF graphs.

Typical RDF mapping:

```text
ddc:510
  rdf:type skos:Concept
  skos:broader ddc:500
```

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | high |
| SKOS compatibility | high |
| Open availability | limited |
| Linked Data support | partial |

### Notes

DDC is managed by OCLC, and full datasets are commercially controlled.

# Library of Congress Classification (LCC)

## Overview

LCC is heavily used in academic and research libraries.

Example:

```text
QA76 = Computer Science
```

## RDF Relationship

Library of Congress actively supports Linked Data.

Especially important:

```text
id.loc.gov
```

which exposes:

- RDF
- SKOS
- Linked Data endpoints

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | very high |
| SKOS compatibility | high |
| Linked Data support | excellent |
| Open accessibility | high |

### Important RDF Concepts

- skos:Concept
- skos:broader
- skos:narrower
- madsrdf

# Nippon Decimal Classification (NDC)

## Overview

NDC is the major Japanese library classification system.

Example:

```text
000 General Works
100 Philosophy
400 Natural Science
900 Literature
```

## RDF Relationship

Compared to LCC and LCSH:

- RDF integration is weaker
- Linked Data support is limited

However:

- structurally RDF-compatible
- important for Japanese knowledge integration

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | medium |
| SKOS compatibility | possible |
| Linked Data support | limited |
| Japanese importance | extremely high |

# Subject Heading Systems

# Library of Congress Subject Headings (LCSH)

## Overview

LCSH is one of the most important semantic subject systems.

Unlike shelf classifications, LCSH behaves more like a semantic concept graph.

Example:

```text
Functional programming (Computer science)
```

## RDF Relationship

LCSH is highly RDF-oriented.

Especially through:

```text
id.loc.gov
```

LCSH concepts are exposed as:

- RDF resources
- SKOS concepts
- Linked Data nodes

### Typical RDF Structure

```text
lcsh:functional_programming
  rdf:type skos:Concept
  skos:broader lcsh:programming_languages
```

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | extremely high |
| SKOS compatibility | excellent |
| Linked Data support | excellent |
| SIE suitability | extremely high |

# FAST

## Overview

FAST is a simplified subject system derived from LCSH.

Designed for easier indexing and retrieval.

## RDF Relationship

FAST supports RDF and Linked Data representation.

Compared to LCSH:

- flatter structure
- easier operational handling
- simpler semantic graph

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | high |
| SKOS compatibility | high |
| Operational simplicity | high |

# Genre Classification Systems

# BISAC

## Overview

BISAC is widely used in publishing and commercial book distribution.

Example:

```text
COM051230
Computers / Programming / Functional
```

## RDF Relationship

BISAC itself is not RDF-native.

However:

- genre hierarchy
- category graph
- subject mapping

can be converted into RDF.

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | medium |
| SKOS compatibility | possible |
| Commercial importance | high |

# Knowledge Graph Databases

# Wikidata

## Overview

Wikidata is one of the most important graph-oriented Book knowledge sources.

Book entities typically contain:

- author
- publisher
- genre
- subject
- language
- identifiers

## RDF Relationship

Wikidata is fundamentally RDF-oriented.

Provides:

- RDF export
- SPARQL endpoint
- Linked Data graph

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | extremely high |
| Linked Data support | excellent |
| Multilingual support | excellent |
| Graph connectivity | excellent |

### Important Characteristics

Wikidata is not merely a classification system.

It is:

```text
classification
+
identity graph
+
relationship graph
```

# DBpedia

## Overview

DBpedia extracts structured graph information from Wikipedia.

Provides:

- categories
- semantic links
- entity graph

## RDF Relationship

DBpedia is RDF-native.

However:

- ontology noise exists
- category consistency varies

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | very high |
| Linked Data support | excellent |
| Semantic consistency | medium |

# Open Library

## Overview

Open Library provides:

- bibliographic metadata
- subject information
- edition/work structure

## RDF Relationship

Open Library is not primarily RDF-native.

However:

- graph-oriented identifiers exist
- semantic integration is possible

### RDF Compatibility

| aspect | status |
| --- | --- |
| RDF friendliness | medium |
| Linked Data support | partial |
| Metadata usefulness | high |

# Relationship Between Classification and RDF

# Classification As Graph Structure

Traditional library systems treat classification as:

```text
Book
  -> shelf position
```

RDF transforms this into:

```text
Book
  -> Concept node
  -> broader/narrower graph
```

This is a major conceptual shift.

# SKOS As The Central Vocabulary

Most classification systems map naturally into SKOS.

Key predicates:

| predicate | purpose |
| --- | --- |
| skos:Concept | classification node |
| skos:broader | parent concept |
| skos:narrower | child concept |
| skos:related | semantic relation |
| skos:prefLabel | canonical label |
| skos:altLabel | aliases |

# Why RDF Matters For SIE

Without RDF:

```text
classification = string
```

With RDF:

```text
classification
  ->
KnowledgeNode
  ->
graph traversal
```

This enables:

- semantic expansion
- explainable RAG
- ontology navigation
- recommendation
- relationship discovery
- multilingual knowledge integration

# Recommended Direction For SIE

For SIE, the most practical early combination appears to be:

```text
Wikidata
+
LCSH
+
Open Library
```

because this combination provides:

- RDF-native structures
- multilingual support
- graph-oriented semantics
- open accessibility
- semantic classification
- relationship connectivity

# Future Direction

Long term, SIE can evolve toward:

```text
Book
  -> Concept graph
  -> Person graph
  -> Organization graph
  -> Citation graph
```

where classification systems become:

```text
semantic navigation infrastructure
```

rather than mere category labels.
