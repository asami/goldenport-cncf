# OpenLibrary-Based RDF Linking Strategy

Date: 2026-06-06
Status: Journal Note

## Purpose

This note describes a lightweight strategy for connecting Information objects to external RDF knowledge spaces using identifiers obtained from OpenLibrary.

The goal is not to import or mirror external RDF graphs. Instead, the goal is to establish stable semantic links from local Information objects to authoritative RDF nodes.

## Background

A common approach to knowledge integration is:

text Information   ↓ Search RDF Knowledge Space   ↓ Find Matching Node   ↓ Create owl:sameAs

While powerful, this approach introduces several challenges:

- external queries
- ambiguity resolution
- matching algorithms
- synchronization issues
- storage of imported RDF

For KnowledgeHub, this may be unnecessarily complex.

In many cases, OpenLibrary already provides authoritative identifiers that can be converted directly into RDF URIs.

This enables a much simpler pipeline.

text Information   ↓ OpenLibrary   ↓ Authority Identifiers   ↓ RDF URI   ↓ Link Triple

## Design Principle

KnowledgeHub should not initially function as an RDF repository.

Instead, it should function as a semantic connection hub.

The primary objective is:

text Information   ↓ External RDF URI

rather than:

text Information   ↓ Imported RDF Graph

External RDF spaces remain the source of truth.

KnowledgeHub stores only the links.

## Target RDF Spaces

The following RDF spaces can be linked directly from OpenLibrary metadata.

### OpenLibrary

Available identifiers:

- Edition ID
- Work ID
- Author ID

Example:

text https://openlibrary.org/books/OL12345M https://openlibrary.org/works/OL67890W

### Wikidata

OpenLibrary often exposes Wikidata identifiers.

Example:

text Q12345

Converted URI:

text https://www.wikidata.org/entity/Q12345

### VIAF

Example:

text 12345678

Converted URI:

text https://viaf.org/viaf/12345678

### ISNI

Example:

text 0000000123456789

Converted URI:

text https://isni.org/isni/0000000123456789

### Library of Congress Authorities

Example:

text n12345678

Converted URI:

text http://id.loc.gov/authorities/names/n12345678

## Triple Generation

### Book

turtle book:9784003101018     kh:externalIdentifier         <https://openlibrary.org/books/OL12345M> .

### Work

turtle book:9784003101018     kh:work         <https://openlibrary.org/works/OL67890W> .

### Author

turtle person:murasaki-shikibu     kh:externalIdentifier         <https://www.wikidata.org/entity/Q12345> .  person:murasaki-shikibu     kh:externalIdentifier         <https://viaf.org/viaf/12345678> .

## Why Not Use owl:sameAs Immediately?

The semantics of owl:sameAs are very strong.

It means:

text Everything known about A is also true about B

For this reason, blindly generating owl:sameAs links is risky.

Instead, the materialization phase should generate:

text kh:externalIdentifier

or

text kh:externalReference

Only after validation should stronger semantic relations such as:

text owl:sameAs

be introduced.

## Materialization Pipeline

### Step 1

Acquire metadata from OpenLibrary.

text ISBN   ↓ OpenLibrary

### Step 2

Extract authority identifiers.

text Author   ├ Wikidata   ├ VIAF   ├ ISNI   └ LOC

### Step 3

Convert identifiers to canonical RDF URIs.

text Q12345   ↓ https://www.wikidata.org/entity/Q12345

### Step 4

Generate RDF linking triples.

text Information   ↓ externalIdentifier   ↓ RDF URI

### Step 5

Store only the links.

No RDF expansion is performed.

## Lazy Knowledge Expansion

External RDF graphs are queried only when additional information is required.

text Information   ↓ External URI   ↓ Resolver   ↓ External RDF Space

Examples:

- concept explanation
- neighborhood exploration
- graph visualization
- semantic search augmentation

This keeps the materialization process fast and deterministic.

## Proposed Architecture

text Information Layer ────────────────────────  Book Person Organization Place          │          ▼  Knowledge Linking Layer ────────────────────────  External URI Links          │          ▼  External RDF Spaces ────────────────────────  OpenLibrary Wikidata VIAF ISNI Library of Congress

## Conclusion

The preferred strategy is not RDF ingestion but RDF linkage.

OpenLibrary already provides high-quality authority identifiers that can be transformed directly into RDF URIs.

Therefore, the first version of Knowledge Materialization should:

1. Retrieve metadata from OpenLibrary.
2. Extract authority identifiers.
3. Convert identifiers to RDF URIs.
4. Generate linking triples.
5. Defer RDF retrieval until explicitly needed.

This approach minimizes complexity while preserving future access to rich external knowledge spaces.
