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
- `src/main/car/web/web.yaml` is the CAR Web descriptor and the only Web
  artifact added by KE-05. No `src/main/web` Web application resource tree is
  added yet; `cncf dev check` should report `src/main/car/web` as
  `web-descriptor`, not as a Web app root.

## Validation

- `sbt --batch test` in `textus-knowledge-editor`.
- `sbt --batch cozyBuildCar` in `textus-knowledge-editor`.
- CAR inspection confirms:
  `component-descriptor.json`, `component/main.jar`, `web/web.yaml`,
  `config/.keep`, `spi/.keep`, and `lib/.keep`.
- `cncf dev check --project /Users/asami/src/dev2026/textus-knowledge-editor`
  reports the main target as a local project, reports no Web app root, and
  reports the CAR Web descriptor under `src/main/car/web`.
- Local smoke confirmed `/web` and `/web/textus-knowledge-editor` render the
  CNCF form shell and BookEditor operation navigation.

## Deferred to KE-06

- External lookup/enrichment through DBpedia, OpenLibrary, Wikidata, or other
  authority sources.
- Resolver candidate creation and confirmation UI.
- Provider-backed publication flow.
- Rich custom editor pages beyond the Static Form shell.
