# Wikidata and DBpedia in KnowledgeHub

Date: 2026-06-08
Status: Journal Note

## Purpose

This note clarifies the roles of Wikidata and DBpedia within the KnowledgeHub and SIE architecture.

The objective is to determine which RDF spaces should be treated as primary integration targets during Knowledge Materialization and how external RDF nodes should be linked to local Information objects.

## Background

Historically, DBpedia was the central RDF knowledge space derived from Wikipedia.

A common architecture looked like:

```text
Wikipedia
    ↓
DBpedia
```

However, the current ecosystem has evolved.

Today, Wikidata functions as the primary structured knowledge hub, while DBpedia increasingly acts as an RDF view over Wikipedia content.

The relationship is better represented as:

```text
Wikipedia
    ├── Wikidata
    └── DBpedia
            └── sameAs
                    ↓
                 Wikidata
```

## DBpedia

DBpedia is an RDF knowledge space generated from Wikipedia.

The extraction process is:

```text
Wikipedia
    ↓
Infobox Extraction
    ↓
RDF Triples
```

For example:

```text
The Tale of Genji
Author: Murasaki Shikibu
Period: Heian Period
```

may become:

```turtle
dbpedia:The_Tale_of_Genji
    dbo:author dbpedia:Murasaki_Shikibu .
```

### Characteristics

- derived from Wikipedia
- extraction-based
- language-dependent resources
- RDF-first representation

Examples:

```text
http://ja.dbpedia.org/resource/源氏物語
http://dbpedia.org/resource/The_Tale_of_Genji
```

Because DBpedia resources are language-specific, the same concept may have multiple URIs.

## Wikidata

Wikidata is a structured knowledge base maintained within the Wikimedia ecosystem.

Instead of page-centric resources, Wikidata uses globally unique identifiers.

Example:

```text
Q170583
```

The internal model is:

```text
Item
Property
Value
```

For example:

```text
The Tale of Genji
    author
        Murasaki Shikibu
```

becomes:

```text
Qxxxx
    P50
        Qyyyy
```

### Characteristics

- structured data source
- multilingual
- stable identifiers
- rich semantic model
- actively maintained

## Why Wikidata Should Be the Primary Hub

### Stable Identifiers

Wikidata uses language-independent identifiers.

Example:

```text
Q42
```

This identifier remains constant regardless of language.

By contrast:

```text
ja.dbpedia.org/resource/源氏物語
dbpedia.org/resource/The_Tale_of_Genji
```

represent the same concept using different URIs.

### Rich Semantics

Wikidata supports:

- qualifiers
- references
- rankings
- multilingual labels

This enables significantly richer modeling than simple RDF extraction.

### Ecosystem Position

Many modern linked data systems now use Wikidata as the central authority.

Examples include:

- authority files
- digital libraries
- cultural heritage datasets
- academic knowledge graphs

## Implications for KnowledgeHub

KnowledgeHub should treat Wikidata as the primary global knowledge authority.

The preferred materialization flow is:

```text
ISBN
    ↓
OpenLibrary
    ↓
Authority Identifiers
    ↓
Wikidata URI
```

rather than:

```text
ISBN
    ↓
OpenLibrary
    ↓
DBpedia Search
```

## Materialization Strategy

### Phase 1: Link Creation

KnowledgeHub stores links only.

Example:

```turtle
book:9784003101018
    kh:externalIdentifier <https://www.wikidata.org/entity/Qxxxx> .
```

No external RDF graph is imported.

### Phase 2: Optional Expansion

When additional knowledge is required:

```text
Wikidata URI
    ↓
External Resolution
    ↓
Additional RDF Knowledge
```

The RDF graph is retrieved on demand.

## Role of DBpedia

DBpedia remains useful.

Examples include:

- RDF datasets based on Wikipedia
- SPARQL exploration
- legacy linked data systems
- Wikipedia-oriented semantic navigation

However, DBpedia should be considered a secondary knowledge space.

A typical flow becomes:

```text
Book
    ↓
OpenLibrary
    ↓
Wikidata
    ↓
DBpedia (optional)
```

rather than:

```text
Book
    ↓
DBpedia
```

## Proposed RDF Space Hierarchy

For Book Knowledge Materialization, the recommended hierarchy is:

```text
Tier 1 ------- OpenLibrary
Tier 2 ------- Wikidata
Tier 3 ------- VIAF, GeoNames
Tier 4 ------- DBpedia, Library of Congress, National Diet Library
```

### Responsibilities

```text
OpenLibrary = Book Authority
VIAF        = Person Authority
GeoNames    = Place Authority
Wikidata    = Global Knowledge Hub
DBpedia     = Wikipedia RDF View
```

## Conclusion

KnowledgeHub should treat Wikidata as the primary RDF integration target.

DBpedia remains valuable, but it should generally be accessed through Wikidata rather than being used as the first authority.

The preferred architecture is:

```text
Information
    ↓
OpenLibrary
    ↓
Authority Identifiers
    ↓
Wikidata URI
    ↓
Optional RDF Expansion
```

This approach minimizes complexity, provides stable identifiers, and aligns with the modern linked-data ecosystem.
