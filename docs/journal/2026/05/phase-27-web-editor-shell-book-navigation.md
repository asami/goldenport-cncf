# Phase 27 — KE-05 Web Editor Shell and Book Navigation

Date: 2026-05-21

## Summary

KE-05 initialized `/Users/asami/src/dev2026/textus-knowledge-editor` with
Cozy component init and replaced the generated notice sample with a book-first
knowledge editor component.

The slice proves the first application-facing Web shell on top of CNCF
`InformationSpaceEditorProjection`. External resolver execution remains KE-06.

## Implementation Notes

- Cozy initialization used a config-driven path. The durable init config is
  kept in `textus-knowledge-editor/etc/cozy-init.yaml`.
- The generated project uses the simplified component dependency shape:
  `goldenport-cncf` plus ScalaTest.
- The driver uses current CNCF development APIs, so the project points to
  `goldenport-cncf_3:0.4.10-SNAPSHOT`.
- `BookEditor` replaces the notice sample and exposes:
  `home`, `listBooks`, `newBook`, `seedBook`, `getBook`, `saveBook`,
  `validateBook`, `confirmBook`, `rejectBook`, `reopenBook`, `publishBook`,
  and `materializeBook`.
- Editor state is stored in the component-local `InformationSpace`.
- `seedBook` stores ISBN/identifier values as editable
  `InformationSpace` record data only. DBpedia/OpenLibrary/Wikidata lookup is
  intentionally deferred to KE-06.
- `materializeBook` uses the existing CNCF Information-to-Knowledge projection
  to store a local `KnowledgeSpace` snapshot for confirmed book items.
- `src/main/web` is the Static Form Web App source. The book editor is
  described with ordinary HTML pages, links, forms, and Static Form operation
  widgets.
- `src/main/web-inf/web.yaml` holds source Web app/page metadata.
  `src/main/web-inf/form.yaml` holds source operation exposure and form control
  defaults. Cozy packages the merged metadata into CAR
  `web/WEB-INF/web.yaml` and `web/WEB-INF/form.yaml`.
- `src/main/web/WEB-INF` is reserved for private layouts, partials, widgets, and
  helper resources copied into CAR `web/WEB-INF`; it is not descriptor source.
  KE-05 does not introduce generated operation-specific fragments.

## Validation

- `sbt --batch test` in `textus-knowledge-editor`.
- `sbt --batch cozyBuildCar` in `textus-knowledge-editor`.
- CAR inspection confirms:
  `component-descriptor.json`, `component/main.jar`, the Static Form pages and
  `WEB-INF/web.yaml` / `WEB-INF/form.yaml` under `web/`, `config/.keep`,
  `spi/.keep`, and `lib/.keep`.
- `cncf dev check --project /Users/asami/src/dev2026/textus-knowledge-editor`
  reports the main target as a local project, reports the Web app resource root
  under `src/main/web`, and reports the CAR Web descriptor under
  `src/main/car/web`.
- Local smoke confirmed `/web` and `/web/textus-knowledge-editor` render the
  CNCF form shell and BookEditor operation navigation.

## Deferred to KE-06

- External lookup/enrichment through DBpedia, OpenLibrary, Wikidata, or other
  authority sources.
- Resolver candidate creation and confirmation UI.
- Provider-backed publication flow.
- Resolver-backed candidate pages beyond the local Static Form shell.
