# Phase 27 Paper Editor Follow-Up

## Summary

KE-07 extends `textus-knowledge-editor` from the book-first workflow to paper
knowledge editing. The implementation keeps BookEditor stable by adding a
separate `PaperEditor` service, paper-specific CML values, and a paper resolver
layer that reuses the same `InformationSpace` lifecycle and reviewable
candidate pattern.

## Field Model

Paper editing uses the `paper` InformationSpace domain.

| Field | Role |
| --- | --- |
| `title` | Required presentation field and primary validation target. |
| `authors` | Recommended contributor text used for resolver query context. |
| `doi` | Local identifier anchor and lookup key. |
| `arxivId` | Local identifier anchor. |
| `pubmedId` | Local identifier anchor. |
| `semanticScholarId` | Local identifier anchor. |
| `openAlexId` | Local identifier anchor. |
| `wikidataId` | External knowledge anchor candidate. |
| `dbpediaUri` | External RDF subject candidate. |
| `venue` | Publication context and resolver query context. |
| `publicationDate` | Publication temporal value. |
| `language` | Presentation/semantic language metadata. |
| `abstract` | Presentation summary and evidence text. |
| `keywords` | Classification/search hints. |
| `citations` | Future relationship/fact input. |
| `sourceUrl` | Source/evidence reference and local identifier anchor. |

`title` is required. At least one of `authors`, `doi`, `arxivId`, `sourceUrl`,
or `venue` is recommended, but not blocking in KE-07.

## Resolver Policy

`PaperResolverProvider` is application-owned and lives in
`textus-knowledge-editor`. CNCF core remains provider-neutral.

KE-07 providers:

| Provider | Responsibility |
| --- | --- |
| `LocalPaperIdentifierResolver` | Converts supplied DOI/arXiv/PubMed/Semantic Scholar/OpenAlex/Wikidata/DBpedia/source URL values into reviewable identity candidates. |
| `DbpediaPaperResolver` | Performs best-effort DBpedia Lookup using title, authors, and venue. |
| `FakePaperResolver` | Provides deterministic automated validation. |

OpenAlex, Semantic Scholar, PubMed, Crossref, and richer scholarly metadata
lookup are deferred. Their ids are still preserved as distinct external
identifier anchors.

Raw DBpedia JSON, raw RDF payloads, and raw vector data are not stored in
`InformationSpace` or shown through editor projections. Resolver output is
reduced to label, source, RDF subject, confidence, evidence summary, and
external identifiers.

## Lifecycle And Materialization

Paper records follow the same flow as book records:

1. Seed or create a paper record.
2. Edit paper fields.
3. Validate required and recommended data.
4. Resolve local or DBpedia candidates.
5. Select or clear candidates.
6. Confirm the record into an InformationSpace item.
7. Publish local status.
8. Materialize through `InformationSpace.materializeItem(item)` into
   component-local `KnowledgeSpace`.

Identifiers, RDF subjects, Information item ids, and KnowledgeNode ids remain
distinct. Resolver candidates are reviewable InformationSpace data until a user
selects and confirms them.

## Completion

- Added CML `PaperEditor` service and generated paper operation/value types.
- Added paper runtime behavior in `ComponentFactory`.
- Added local, fake, and DBpedia paper resolver providers.
- Added tests for operation exposure, seed/save/validate/confirm/publish/
  materialize, local candidate creation, candidate selection/clearing, and
  DBpedia fake HTTP parsing.
- Added Static Form metadata and lightweight paper pages for listing, opening,
  seeding, and resolving paper records.

Next focus is KE-08, the web resource knowledge editor vertical slice.
