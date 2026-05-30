Book Classification Strategy for SIE
======

# Purpose

The Book Knowledge Materialization model defines how books are represented as KnowledgeNodes.

However, representing books alone is not sufficient for knowledge discovery, navigation, recommendation, or reasoning. Classification systems provide the structure that connects books to broader knowledge spaces.

This note discusses the design strategy for handling book classifications within the Semantic Integration Engine (SIE).

# Problem Statement

The term "book classification" is often treated as a single concept, but in practice multiple independent classification systems exist.

Examples include:

- Library classifications
- Subject classifications
- Genre classifications
- Academic classifications
- Knowledge domain classifications

Each classification system serves a different purpose and represents a different view of knowledge.

Attempting to merge these systems into a single taxonomy typically results in loss of meaning and reduced flexibility.

SIE therefore treats them as independent but interconnected knowledge spaces.

# Classification Categories

## Library Classification

Library classifications are designed for cataloging and physical organization of collections.

Examples:

- NDC (Nippon Decimal Classification)
- DDC (Dewey Decimal Classification)
- LCC (Library of Congress Classification)

Characteristics:

- Strong hierarchical structure
- Optimized for collection management
- Widely adopted by libraries

Example:

```text
913.36
  Japanese Literature
    Heian Literature
```

## Subject Classification

Subject classifications describe what a book is about.

Examples:

- Japanese Literature
- Buddhism
- Artificial Intelligence
- Medieval History

Characteristics:

- A book may have multiple subjects
- Suitable for knowledge discovery
- Frequently used in linked data environments

Example:

```text
The Tale of Genji
  -> Japanese Literature
  -> Heian Culture
  -> Classical Literature
```

## Genre Classification

Genre classifications describe the reading experience or literary form.

Examples:

- Novel
- Mystery
- Science Fiction
- Biography

Characteristics:

- Commonly used by bookstores and e-commerce platforms
- Reader-oriented
- Often independent from academic classification systems

## Knowledge Domain Classification

Knowledge domain classifications represent the structure of knowledge itself.

Examples:

- Philosophy
- Science
- Technology
- Economics
- Law

Characteristics:

- Suitable for connecting books to broader Bodies of Knowledge (BoK)
- Highly compatible with SIE knowledge graphs
- Useful for cross-domain exploration

# Design Principles

SIE does not attempt to unify classification systems.

Instead, classifications are represented independently:

```text
Book
 ├─ Library Classification
 ├─ Subject
 ├─ Genre
 └─ Knowledge Domain
```

Each classification system preserves its own semantics and structure.

This approach enables federation rather than consolidation.

# Classification as KnowledgeNodes

In SIE, classifications are represented as KnowledgeNodes.

Example:

```text
KnowledgeNode
  id = ndc:913.36

label:
  Japanese Literature
```

Relationships between books and classifications are represented explicitly.

Library classification:

```text
Book
  -> hasClassification
       -> NDC:913.36
```

Subject classification:

```text
Book
  -> hasSubject
       -> Japanese Literature
```

Genre classification:

```text
Book
  -> hasGenre
       -> Novel
```

Knowledge domain classification:

```text
Book
  -> hasKnowledgeDomain
       -> Literature
```

# Knowledge Federation

Representing classifications as KnowledgeNodes transforms them from metadata into navigable knowledge structures.

Example:

```text
Book
 ↓
Classification
 ↓
Related Books
```

Or:

```text
Book
 ↓
Subject
 ↓
People
 ↓
Organizations
 ↓
Events
```

Classifications become connection points between independent knowledge spaces.

# External Classification Sources

## NDC

Nippon Decimal Classification.

Priority:

- First priority

Reasons:

- Strong compatibility with Japanese publishing ecosystems
- Potential integration with OpenBD
- Well-defined hierarchical structure

## DDC

Dewey Decimal Classification.

Priority:

- Second priority

Reasons:

- Global adoption
- Extensive coverage

## LCC

Library of Congress Classification.

Priority:

- Second priority

Reasons:

- Strong presence in academic collections
- Rich subject organization

## Wikidata Subjects

Priority:

- High

Reasons:

- Native RDF integration
- Direct linkage to broader knowledge graphs

## OpenLibrary Subjects

Priority:

- High

Reasons:

- Rich bibliographic coverage
- Natural extension of book knowledge

# Proposed Roadmap

Phase 1

- NDC KnowledgeNodes
- NDC hierarchy representation

Phase 2

- Wikidata subjects
- OpenLibrary subjects

Phase 3

- DDC integration
- LCC integration

Phase 4

- SimpleModeling Knowledge Domain taxonomy

# Future Direction

Book classifications should not be viewed merely as descriptive metadata.

They are independent knowledge spaces that connect books to authors, organizations, places, events, and other domains of knowledge.

The role of SIE is not to merge classification systems into a single taxonomy, but to federate them through KnowledgeNodes and semantic relationships.

This federated approach enables cross-classification exploration while preserving the semantics of each classification system.
