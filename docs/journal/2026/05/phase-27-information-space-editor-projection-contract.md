# Phase 27 InformationSpace Editor Projection Contract

Date: 2026-05-21

## Context

KE-04 adds the editor-facing projection contract that
`textus-knowledge-editor` can consume before Web UI implementation begins.
This contract is separate from system admin/debug projection. Admin projection
shows compact operator state; editor projection explains editable fields,
validation, resolution, lifecycle actions, and Knowledge materialization
impact.

The first concrete profile is `book`.

## Editor Projection Boundary

The editor projection is read-only. It does not replace the existing
`InformationSpace` lifecycle API:

```text
register/update/validate/resolve/confirm/reject/reopen/publish/conflict
  -> InformationSpace lifecycle API
  -> InformationSpaceEditorProjection
  -> textus-knowledge-editor UI
```

The projection provides enough metadata for the editor to render forms and
guidance without hard-coding the book mapping in the UI.

## Projection Types

The runtime contract is:

- `InformationEditorProjection`: component/domain editor view;
- `InformationEditorRecordProjection`: import record or information item view;
- `InformationEditorFieldProjection`: field value plus issues, candidates, and
  conflicts;
- `InformationFieldDescriptor`: label, description, example, requiredness,
  validation hint, resolver-assisted marker;
- `InformationFieldMappingDescriptor`: Knowledge target and profile layer;
- `InformationEditorActionDescriptor`: action name, label, enabled state, and
  disabled reason.

The projection intentionally avoids raw RDF triples, provider payloads, raw
vector bodies, and source document bodies. Resolver and provider details should
appear as evidence, provenance, source reference, or explanation text.

## Book Profile

The `book` profile exposes the first editor field set:

- identity/import: ISBN-13, ISBN-10, DOI, Open Library ID, Wikidata QID,
  DBpedia URI;
- presentation: title, subtitle, localized titles, summary;
- contributor/publication: authors, editors, publisher, publication date,
  language;
- semantic neighborhood: subjects and citations.

Each field has editor-facing guidance and mapping metadata. Examples:

| Field | Projection use | Knowledge impact |
| --- | --- | --- |
| `isbn13` | recommended resolver-assisted identifier | `identity.externalIdentifiers` and book identity neighborhood |
| `title` | required display and matching value | `presentation.labels` and confirmed title fact |
| `authors` | resolver-assisted contributor field | authorship relationship and contributor neighborhood |
| `subjects` | resolver-assisted classification field | classification relationship and derived `structure.classifications` |
| `dbpediaUri` | resolver-assisted RDF anchor | external RDF anchor and evidence for imported labels/categories |

## Mapping Layers

Mapping descriptors label targets as either:

- `common-neighborhood`: reusable KnowledgeNode / KnowledgeFrame structure;
- `book-profile-extension`: book-specific bibliographic profile structure.

This keeps the editor aware of which fields are generic semantic-neighborhood
concepts and which belong to the book profile.

## Action Projection

The projection exposes the canonical actions expected by the editor:

- `save`
- `validate`
- `resolve`
- `confirm`
- `reject`
- `reopen`
- `publish`
- `materialize`

Actions are derived from current lifecycle state. The editor should use this
projection for button availability, but mutations still go through the normal
InformationSpace operation/API path.

## KE-04 Decisions

- `InformationSpaceProjection` remains system admin/debug projection.
- `InformationSpaceEditorProjection` is the application editor projection
  boundary.
- `book` is the only concrete KE-04 profile.
- Unknown profiles return deterministic failure instead of an empty editor
  projection.
- Editor projection includes mapping metadata, not actual RDF/Vector provider
  execution.
- KE-05 owns Web editor routes and UI navigation.
- KE-06 owns resolver execution and DBpedia/OpenLibrary/Wikidata candidate
  creation.
