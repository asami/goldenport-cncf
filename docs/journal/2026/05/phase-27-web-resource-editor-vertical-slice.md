# Phase 27 Web Resource Editor Vertical Slice

## Summary

KE-08 extends `textus-knowledge-editor` from book and paper workflows to web
resource knowledge editing. The implementation keeps BookEditor and PaperEditor
stable by adding a separate `WebResourceEditor` service, web-resource-specific
CML values, and a lightweight metadata provider layer that reuses the same CNCF
`InformationSpace` lifecycle and reviewable candidate pattern.

## Field Model

Web resource editing uses the `web-resource` InformationSpace domain.

| Field | Role |
| --- | --- |
| `url` | Required URL/source anchor unless canonical URL is present. |
| `canonicalUrl` | Required canonical source anchor unless URL is present. |
| `finalUrl` | Fetched final URL after redirect handling; metadata, not a replacement for original URL. |
| `title` | Required presentation field and primary validation target. |
| `siteName` | Site or container name. |
| `publisher` | Publisher organization or agent. |
| `author` | Author or creator text. |
| `retrievedAt` | Metadata retrieval/provenance timestamp. |
| `summary` | Meta description or editor summary. |
| `language` | Presentation/semantic language metadata. |
| `keywords` | Classification/search hints. |
| `links` | Selected outbound links retained as reviewable references. |
| `sourceUrl` | Source/evidence reference. |
| `reviewerNote` | Human curation note. |

`title` and at least one of `url` or `canonicalUrl` are required for
confirmation. URL and canonical URL remain external identifiers/source anchors;
they are not CNCF ids.

## Metadata Fetch Policy

`WebResourceMetadataProvider` is application-owned and lives in
`textus-knowledge-editor`. CNCF core remains provider-neutral.

KE-08 providers:

| Provider | Responsibility |
| --- | --- |
| `LocalWebResourceMetadataProvider` | Converts supplied URL, canonical URL, source URL, and selected links into reviewable identity candidates. |
| `HttpWebResourceMetadataProvider` | Fetches lightweight HTML metadata using Java standard HTTP. |
| `FakeWebResourceMetadataProvider` | Provides deterministic automated validation. |

The HTTP provider extracts final URI, title, meta description, language,
canonical link, selected outbound links, site name, publisher, author, and
retrieval timestamp. It does not store raw HTML, full page bodies, crawler
payloads, raw RDF, or raw vector data in `InformationSpace` or editor
projections.

## Candidate And Materialization Flow

Web resource records follow the same flow as book and paper records:

1. Seed or create a web resource record.
2. Optionally fetch lightweight metadata.
3. Edit web resource fields.
4. Validate required title and URL/canonical URL data.
5. Resolve local URL/RDF-like link candidates.
6. Select or clear candidates.
7. Confirm the record into an InformationSpace item.
8. Publish local status.
9. Materialize through `InformationSpace.materializeItem(item)` into
   component-local `KnowledgeSpace`.

Fetched metadata and resolver candidates remain reviewable InformationSpace
data until a user confirms them.

## Completion

- Added CML `WebResourceEditor` service and generated web resource
  operation/value types.
- Added web resource runtime behavior in `ComponentFactory`.
- Added fake, local, and HTTP metadata provider implementations.
- Added tests for operation exposure, seed/save/fetch/validate/confirm/publish/
  materialize, local candidate creation, candidate selection/clearing, and fake
  HTTP metadata parsing.
- Added Static Form metadata and lightweight web resource pages for listing,
  opening, seeding, fetching, editing, resolving, and lifecycle actions.

Next focus is KE-09, richer publication/materialization feedback and
editor-visible KnowledgeNode/Relationship summaries.
