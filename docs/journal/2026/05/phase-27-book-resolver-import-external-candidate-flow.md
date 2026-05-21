# Phase 27 Book Resolver Import and External Candidate Flow

Date: 2026-05-21

## Context

KE-06 adds the first resolver-backed book enrichment flow to
`textus-knowledge-editor`. The editor can now turn ISBN, DOI, DBpedia URI,
Wikidata ID, Open Library ID, title, and author input into reviewable
`InformationSpace` resolution candidates.

The important boundary remains unchanged:

```text
book editor input
  -> InformationSpace record
  -> resolver candidates / identity bindings / evidence
  -> user selection and confirmation
  -> publish/materialize flow
  -> KnowledgeSpace runtime projection
```

Resolver output is not treated as authoritative data until it is selected and
the information record is confirmed.

## Resolver Boundary

Resolver implementation is application-owned by `textus-knowledge-editor`.
CNCF core stays provider-neutral and only exposes the generic
`InformationSpace` candidate lifecycle.

The editor-side providers are:

- `LocalBookIdentifierResolver`: converts seeded ISBN, DOI, Open Library,
  Wikidata, and DBpedia URI fields into a local candidate.
- `DbpediaBookResolver`: calls the DBpedia Lookup API and maps labels,
  comments, categories, sameAs links, and resource URI into candidate summary
  data.
- `FakeBookResolver`: deterministic test provider.

Open Library and Wikidata are handled as supplied identifier anchors in KE-06.
Live lookup for those providers remains future work.

## Candidate Data Policy

Candidates store compact, reviewable information:

- source name, such as `local` or `dbpedia`;
- candidate label;
- RDF subject URI when available;
- external identifiers, including DBpedia resource URI and sameAs links;
- confidence;
- evidence summary.

Raw DBpedia JSON, raw RDF payloads, and raw vector bodies are not stored in
`InformationSpace` and are not displayed by the editor.

## Identity Policy

The editor keeps these identifiers distinct:

- `InformationRecordId`
- `InformationItemId`
- `KnowledgeNodeId`
- CNCF RDF node name
- ISBN / DOI / Open Library / Wikidata / DBpedia / OCLC / LCCN / NDL IDs

External identifiers are lookup keys and identity bindings. They are never
promoted to CNCF IDs by string equality.

## UI Flow

The book detail page now exposes resolver and candidate actions:

- resolve candidates for a record;
- list candidates;
- select a candidate;
- clear a candidate.

Candidate rows are returned through the same editor projection used by
`getBook`, so field guidance, validation issues, candidate state, and mapping
impact stay together.

## KE-06 Decisions

- DBpedia is the first live external RDF enrichment source.
- Resolver candidate selection uses the existing `InformationSpace`
  resolution-candidate API.
- CNCF added only generic candidate clearing support needed by the editor.
- `confirmBook`, `publishBook`, and `materializeBook` continue to use the
  existing lifecycle flow after candidate selection.
- KE-07 moves to the paper editor follow-up; broader provider coverage and
  richer external authority workflows remain future work.
