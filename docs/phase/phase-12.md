# Phase 12 — Web Layer

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 12.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Promote the CNCF Web Layer from journal notes into an implementation track.
- Define CNCF as an operation-centric web integration surface.
- Establish the Static Form App mechanism first, then place the operational
  dashboard, management console, and manual/reference web apps on top of it.
- Define REST/Form API exposure around existing Component / Service /
  Operation metadata as the execution and validation backbone for that mechanism.
- Define Web Descriptor responsibilities for exposure, security, form behavior,
  and application hosting.
- Keep UI framework choices outside the CNCF core.

## 3. Non-Goals

- No full frontend framework implementation in this phase.
- No broad business UI generation beyond a minimal Static Form App path.
- No replacement of existing CLI/meta projections.
- No generalized external API gateway product in this phase.
- No completion of advanced visualizations such as SVG assembly diagrams unless
  they are needed as minimal projections for the web surface.

## 4. Current Work Stack

- A (DONE): WEB-01 — Consolidate Web Layer scope and canonical design surface.
- B (DONE): WEB-02 — Define operation-centric REST/Form API and Static Form App mechanism.
- C (DONE): WEB-03 — Define Web Descriptor model and configuration path.
- D (DONE): WEB-04 — Define read-only dashboard baseline.
- E (DONE): WEB-05 — Define management console baseline.
- F (DONE): WEB-06 — Define manual/reference baseline.
- G (DONE): WEB-07 — Add executable specifications and minimal runtime hooks.
- H (DONE): WEB-08 — Build Management Console CRUD flow as the Form Web foundation.
  Entity and data CRUD baselines are implemented, and view read baseline is
  implemented; aggregate read baseline and operation-backed
  create/command/update flow are implemented.
- I (DONE): WEB-09 — Validate Static Form Web App behavior with
  `textus-sample-app`.
  The notice-board sample runs on CML metadata and static HTML conventions,
  including result widgets, paging, total-count opt-in, error pages, and async
  command result waiting.
- J (DONE): WEB-10 — Polish built-in Web pages with Bootstrap 5.
  Management Console, Dashboard, Manual, and Static Form App use the local
  Bootstrap 5 baseline as the first-pass responsive business/admin polish.
- K (DONE): WEB-11 — Expand the Textus widget set.
  Static Form App has first-pass Textus widgets for action/form helpers,
  result/pagination rendering, cards, summaries, alerts, and empty states.
- L (DONE): WEB-12 — Define Web app packaging and deployment.
  Static Form Web App packaging is defined around canonical `/web` CAR/SAR
  roots, descriptor discovery, static result templates, local assets, optional
  island scripts, route aliases, archive discovery, and completed descriptor
  inspection.
- M (DONE): WEB-13 — Add shortid support for Web-facing entity references.
  EntityId remains the canonical identifier, but Web-facing routes and screens
  need a shorter entity-local identifier when the entity kind is already known.
- N (DONE): WEB-14 — Define application-user job result UX.
  Async command execution should expose a natural way for users to follow,
  wait for, and inspect job results without forcing all Form flows to become
  synchronous.
- O (DONE): WEB-15 — Auto-complete required widget assets.
  Textus widget rendering supplies local Bootstrap 5 CSS/JS for full HTML
  document templates that use widgets, while skipping duplicates and honoring
  descriptor-declared assets.
- P (DONE): WEB-16 — Provide Textus widget local assets.
  Textus widget rendering supplies framework-owned `/web/assets/textus-widgets.css`
  and `/web/assets/textus-widgets.js` after the local Bootstrap baseline.
  Built-in layouts include them, and full HTML document templates receive them
  automatically when they use Textus widgets.
- Q (DONE): WEB-17 — Validate Static Form result asset completion end to end.
  `textus-sample-app` has an executable check that posts real Static Form
  requests and verifies the result pages include Bootstrap and Textus widget
  framework assets with all Textus widget tags expanded.
- R (DONE): WEB-18 — Connect descriptor-declared assets to result rendering.
  Static Form result rendering now uses `web.assets.autoComplete`, `css`, and
  `js` when deciding whether to insert framework widget assets.
- S (DONE): WEB-19 — Insert descriptor assets into Static Form result pages.
  Descriptor-declared CSS/JS are now inserted into full HTML and fragment
  result pages with duplicate suppression. Framework assets remain first,
  followed by app/page composition assets.
- T (DONE): WEB-20 — Add scoped descriptor assets.
  Static Form result asset composition now merges global, app, and form
  descriptor assets so multi-app Web packages can avoid global over-application.
- U (DONE): WEB-21 — Validate form-scoped assets in the sample app.
  `textus-sample-app` declares `search-notices`-specific assets at form scope
  and verifies that they appear only on the search result flow.
- V (DONE): WEB-22 — Apply scoped assets to input form pages.
  Static Form operation input pages and component form indexes now receive the
  same scoped descriptor assets as result pages, with form-specific assets kept
  off the component form index.
- W (DONE): WEB-23 — Expose descriptor asset composition in admin.
  Completed Web descriptor inspection now reports global, app, form, and
  resolved input/result asset composition so package authors can debug
  descriptor-driven asset behavior from the management console.
- X (DONE): WEB-24 — Render descriptor asset composition tables.
  Management Console descriptor pages now show configured asset scopes and
  resolved Static Form page assets as Bootstrap tables before the raw JSON.
- Y (DONE): WEB-25 — Render descriptor control tables.
  Management Console descriptor pages now show apps, routes, form access,
  authorization, and admin surfaces as readable tables before the raw JSON.
- Z (DONE): WEB-26 — Link descriptor admin table rows.
  Descriptor admin tables now link apps, routes, Static Forms, and component
  admin surfaces to their runtime/admin destinations.
- AA (DONE): WEB-27 — Add descriptor admin filtering.
  Descriptor admin pages now provide a lightweight client-side filter across
  descriptor control tables and tighten component-scope rows.
- AB (DONE): WEB-28 — Specify component descriptor scope rules.
  Component descriptor pages now apply explicit scope rules for apps, routes,
  form selectors, and local or component-qualified admin surfaces.
- AC (DONE): WEB-29 — Add descriptor table counts and filter empty state.
  Descriptor control tables now show row counts and the local filter reports
  when no descriptor rows match.
- AD (DONE): WEB-30 — Make raw descriptor JSON auxiliary.
  Descriptor admin pages now provide section navigation and fold completed/
  configured raw JSON behind details panels.
- AE (DONE): WEB-31 — Polish descriptor admin sections.
  Descriptor admin pages now expose Bootstrap navigation pills, section anchors,
  and table-to-JSON links so the tables remain the primary inspection surface.
- AF (DONE): WEB-32 — Recheck Management Console CRUD routes with the sample app.
  The sample app now validates the built-in admin entry points and an entity
  create/list/detail round trip against the published runtime.
- AG (DONE): WEB-33 — Recheck Management Console data surface.
  The sample app confirms the data admin entry route and records that full data
  CRUD E2E needs a data collection fixture rather than the current notice-only
  CAR sample.
- AH (DONE): WEB-34 — Recheck Management Console aggregate surface.
  The sample app confirms aggregate metadata, read baseline, and operation-backed
  create/update/read links for the `notice` aggregate.
- AI (DONE): WEB-35 — Recheck Management Console view surface.
  The sample app confirms view metadata, read baseline, and JSON Form API
  definition for the `notice` view.
- AJ (DONE): WEB-36 — Recheck Management Console read surfaces with created data.
  The sample app now creates a `Notice` through the admin entity form, then
  verifies that the same data is visible from entity list/detail, aggregate
  read, and view read pages.
- AK (DONE): WEB-37 — Recheck aggregate operation create/update round trip.
  The sample app now exercises aggregate operation forms end to end: submit
  create/update commands, wait for the async job result, and confirm aggregate
  read pages reflect the changes.
- AL (DONE): WEB-38 — Record Data CRUD fixture boundary.
  The current notice-board CAR sample has no meaningful data collection
  fixture, so full Data CRUD E2E remains deferred until a data-oriented sample
  or fixture is added. Current checks keep the data admin entrypoint visible.
- AM (DONE): WEB-39 — Polish Admin Console CRUD navigation by E2E.
  The sample app now verifies entity detail-to-edit navigation, edit form
  cancel/update actions, update result output, and post-update detail display.
- AN (DONE): WEB-40 — Validate Static Form result transition conventions.
  Static Form result pages are checked through the `xxx__200.html` convention,
  hidden context handoff, result properties, and async command await links.
- AO (DONE): WEB-41 — Validate Textus widget expansion in the sample app.
  The sample app now exercises table, card-list, generic card, job-ticket,
  action-link, property-list, error-panel, and hidden-context widgets with both
  namespace and HTML-compatible notation.
- AP (DONE): WEB-42 — Recheck Web packaging and descriptor routes.
  The sample app packaging check covers canonical and implicit Web app routes,
  framework/app/form assets, descriptor admin inspection, and admin surface
  entrypoints.
- AQ (DONE): WEB-43 — Recheck aggregate detail UX and instance operations.
  The sample app now follows aggregate list rows into instance detail pages and
  confirms instance operation links preserve the aggregate id context.
- AR (DONE): WEB-44 — Recheck Static Form user transition flow.
  The notice-board sample now validates post -> await -> search -> detail as
  a public Static Form Web App flow using static result templates.
- AS (DONE): WEB-45 — Expand widget composition coverage.
  The renderer now supports action-card and status-badge widgets, and the
  sample result page verifies nested card/action composition.
- AT (DONE): WEB-46 — Validate the sample app as a minimal Static Form Web App.
  The sample app now has an end-to-end browser-style check for the no-login
  notice-board flow from the public Web app entry point.
- AU (DONE): WEB-47 — Fix Static Form page transition contract.
  Static result pages remain convention-first: `xxx__200.html` and common
  status templates are the primary mechanism, while descriptor templates remain
  supplemental.
- AV (DONE): WEB-48 — Add application job result UX baseline.
  `textus:job-panel` now composes job ticket, local await action, and system
  job page handoff for applications that want a complete embedded job UX.
- AW (DONE): WEB-49 — Consolidate Textus widget catalog baseline.
  Widget coverage is documented around result, card/action, job, feedback,
  pagination, and hidden-context families with namespace and HTML-compatible
  notation.
- AX (DONE): WEB-50 — Polish the sample Static Form Web App.
  The notice-board sample result pages now use Bootstrap-oriented result
  sections, status badges, and job panels, with scripts checking the composed
  widgets.
- AY (DONE): WEB-51 — Verify system job page handoff.
  The sample public flow now follows the job-panel link to
  `/web/system/jobs/{jobId}` and verifies both the system ticket and await
  action.
- AZ (DONE): WEB-52 — Recheck Static Form UI quality criteria.
  The sample packaging and result checks now cover viewport readiness,
  Bootstrap-local assets, result framing, and smartphone-friendly spacing hooks.
- BA (DONE): WEB-53 — Add detail-oriented widget baseline.
  `textus:description-list` renders detail records from result JSON using
  resolved entity/view columns.
- BB (DONE): WEB-54 — Recheck Web app packaging for runtime mapping.
  The sample packaging check confirms canonical component Web app routes,
  aliases, local assets, descriptor visibility, and polished app assets.
- BC (DONE): WEB-55 — Add result detail navigation.
  Search result widgets can now render detail links from row fields, and the
  sample flow opens details from the search result page.
- BD (DONE): WEB-56 — Recheck description-list column selection.
  `textus:description-list` is verified with both CML/view columns and explicit
  column declarations.
- BE (DONE): WEB-57 — Add navigation list widget baseline.
  `textus:nav-list` / `textus-nav-list` renders static navigation links in
  button and list-group styles.
- BF (DONE): WEB-58 — Add result action-group baseline.
  `textus:action-group` / `textus-action-group` renders operation result action
  metadata as a reusable Bootstrap action row.
- BG (DONE): WEB-59 — Preserve detail return context.
  Static detail pages can receive `return.href` from list/detail links and
  render a framework-provided return action.
- BH (DONE): WEB-60 — Harden widget attribute parsing.
  Widget attributes now support single-quoted values and navigation items with
  URL colons.
- BI (DONE): WEB-61 — Recheck Static Form result conventions.
  The phase checklist records the current convention-first result template
  contract across exact status, success/error aliases, hidden context, and
  action widgets.

Current note:
- Web-related journal notes already exist for architecture, operational
  management, descriptor, Form API, API response, SPA integration, and
  wireframe generation.
- This phase should promote only the minimal coherent runtime path first and
  leave broader UI generation as later work.
- Development proceeds with `textus-sample-app` as the practical validation
  driver in a separate working thread.
- The first practical entry point is Dashboard, but implementation still follows
  WEB-01 -> WEB-02 -> WEB-04: Dashboard must be placed on the shared Static Form
  App / Form API mechanism rather than introduced as a special server route.

## 5. Development Items

- [x] WEB-01: Consolidate Web Layer scope and canonical design surface.
- [x] WEB-02: Define REST/Form API exposure and Static Form App mechanism.
- [x] WEB-03: Define Web Descriptor model and configuration path.
- [x] WEB-04: Define read-only dashboard baseline.
- [x] WEB-05: Define management console baseline.
- [x] WEB-06: Define manual/reference baseline.
- [x] WEB-07: Add executable specifications and minimal runtime hooks.
- [x] WEB-08: Build Management Console CRUD flow as the Form Web foundation.
  Entity and data CRUD baselines cover list, detail, edit/update, new/create,
  Form API definitions, validation redisplay, hidden context preservation, and
  descriptor transitions. View and aggregate surfaces cover the read-oriented
  baseline, and aggregate create/update/command flows are operation-backed.
  Remaining work is management-console polish outside WEB-08.
- [x] WEB-09: Validate Static Form Web App behavior with `textus-sample-app`.
  The notice-board sample uses CML-derived operation/schema metadata and static
  HTML result conventions for post/search/get flows. Generic widget behavior
  covers result view, tables, property lists, error panels, action links,
  paging without total count, explicit total-count opt-in, and async command
  await/detail navigation.
- [x] WEB-10: Polish built-in Web pages with Bootstrap 5.
  First-pass Bootstrap 5 polish is complete for Management Console, Dashboard,
  Manual, and Static Form App pages without changing their runtime data
  contracts. Reusable card/layout/feedback widgets are tracked by WEB-11.
- [x] WEB-11: Expand the Textus widget set.
  First-pass widget expansion is complete for action/form helpers,
  result/pagination rendering, record/card-list/summary cards, alerts, and
  empty states. Richer layout/navigation/content widgets are future work.
- [x] WEB-12: Define Web app packaging and deployment.
  Static Form Web App resources are packaged under canonical CAR/SAR `/web`
  roots. Descriptor discovery, static template lookup, app-local asset routing,
  optional island directories, SAR aliases, implicit single-CAR aliases,
  archive discovery, sample migration, and completed descriptor inspection are
  covered by design notes and executable specifications.
- [x] WEB-13: Add shortid support for Web-facing entity references.
  Introduce a `shortid` value derived from the entity-local entropy portion of
  EntityId, define when it is safe to use it, and define how `id` and
  `shortid` are exposed in Web URLs, forms, lists, detail pages, and admin
  screens. The EntityId entropy extraction contract has been confirmed:
  `EntityId.parts.entropy` is the canonical `shortid` source. `shortid` is a
  `SimpleEntity` identity attribute, not a `NameAttributes` field. Canonical
  `id` remains the primary identifier for external integration and generic
  framework logic because it carries major/minor, entity name, timestamp, and
  entropy. Timestamp is also reserved for possible distributed ordering
  algorithms, so `shortid` must not replace canonical id in generic processing.
  Admin entity list/detail/edit links now prefer shortid for entity-scoped Web
  URLs, while read/update operations normalize the route token back to
  canonical EntityId before entity access or persistence.
- [x] WEB-14: Define application-user job result UX.
  Design and implement the user-facing result reference flow for async Command
  execution: job id handoff, optional wait/action buttons, result pages, and
  trace references such as entity id or shortid when available. Current work
  adds embeddable job widgets, a system job page, and owner-only job result
  visibility.
- [x] WEB-15: Auto-complete required widget assets.
  Full HTML document templates that use Textus widgets receive local Bootstrap
  5 CSS/JS when missing. Existing page declarations and descriptor-declared
  assets are not duplicated, and pages without widgets are left unchanged.
- [x] WEB-32: Recheck Management Console CRUD routes with the sample app.
  `textus-sample-app` verifies entity/admin entry pages and an actual admin
  entity create/list/detail flow. This keeps the current work order anchored on
  remaining Form/Admin behavior before deeper Static Form App expansion.
- [x] WEB-33: Recheck Management Console data surface.
  Data admin entrypoint behavior is covered in `textus-sample-app`. Full data
  CRUD E2E remains dependent on adding a meaningful data collection fixture to
  the sample app.
- [x] WEB-34: Recheck Management Console aggregate surface.
  Aggregate list/detail, read baseline, and operation-backed create/update/read
  action links are checked against the sample app runtime.
- [x] WEB-35: Recheck Management Console view surface.
  View list/detail, read baseline, and JSON Form API metadata are checked
  against the sample app runtime.
- [x] WEB-36: Recheck Management Console read surfaces with created data.
  `textus-sample-app` creates a `Notice` through the admin entity form and
  verifies that entity list/detail, aggregate read, and view read all expose
  the created title/content through the published runtime.
- [x] WEB-37: Recheck aggregate operation create/update round trip.
  Aggregate create/update operation forms are executed through `/form`, async
  job result pages are awaited, and aggregate read output is checked after
  create and update.
- [x] WEB-38: Record Data CRUD fixture boundary.
  Data admin route validation remains in place, while full Data CRUD E2E is
  explicitly held for a future data collection fixture/sample.
- [x] WEB-39: Polish Admin Console CRUD navigation by E2E.
  Entity CRUD validation covers create, list/detail navigation, edit form
  rendering, update submission, and updated detail output.
- [x] WEB-40: Validate Static Form result transition conventions.
  Sample checks confirm operation-specific `__200` result pages, hidden
  context handoff, job await links, and result property/widget expansion.
- [x] WEB-41: Validate Textus widget expansion in the sample app.
  Sample result templates exercise table, card-list, generic card, job-ticket,
  action-link, property-list, error-panel, and hidden-context widgets.
- [x] WEB-42: Recheck Web packaging and descriptor routes.
  Packaging validation covers canonical `/web/{component}/{webApp}` routing,
  implicit aliases, asset routing, descriptor admin pages, and admin surface
  entrypoints.
- [x] WEB-43: Recheck aggregate detail UX and instance operations.
  Aggregate read rows are followed into instance detail pages, and instance
  update operation links are checked for id handoff.
- [x] WEB-44: Recheck Static Form user transition flow.
  Public post, async await, search refresh, and detail navigation are validated
  through static result pages and framework-provided properties.
- [x] WEB-45: Expand widget composition coverage.
  `textus:action-card` and `textus:status-badge` are implemented and verified
  alongside card/action/widget composition.
- [x] WEB-46: Validate the sample app as a minimal Static Form Web App.
  `textus-sample-app` now verifies the no-login notice-board flow from the Web
  app entry point through posting, waiting, searching, and opening a notice.
- [x] WEB-47: Fix Static Form page transition contract.
  Static page conventions remain the default result transition mechanism for
  demo/internal applications.
- [x] WEB-48: Add application job result UX baseline.
  `textus:job-panel` provides an embeddable job UX and a system job page
  handoff.
- [x] WEB-49: Consolidate Textus widget catalog baseline.
  The widget spec records the current implemented catalog and future widget
  families.
- [x] WEB-50: Polish the sample Static Form Web App.
  Notice-board result pages use Bootstrap-oriented sections and composed
  widgets while staying static-template based.
- [x] WEB-51: Verify system job page handoff.
  The public flow checks `/web/system/jobs/{jobId}` and system await.
- [x] WEB-52: Recheck Static Form UI quality criteria.
  Sample checks cover viewport, local Bootstrap/widget assets, and responsive
  result-page polish hooks.
- [x] WEB-53: Add detail-oriented widget baseline.
  `textus:description-list` is implemented and used by the notice detail page.
- [x] WEB-54: Recheck Web app packaging for runtime mapping.
  Packaging checks verify canonical routes, aliases, descriptor pages, and
  polished app assets.
- [x] WEB-55: Add result detail navigation.
  Result table/card-list widgets can render detail links by expanding record
  fields into `detail-href`.
- [x] WEB-56: Recheck description-list column selection.
  Detail widgets are covered by CML/view columns and explicit column fallback.
- [x] WEB-57: Add navigation list widget baseline.
  Static Form pages can use `textus:nav-list` for simple Bootstrap navigation.
- [x] WEB-58: Add result action-group baseline.
  Result actions can be rendered as a reusable Bootstrap action row.
- [x] WEB-59: Preserve detail return context.
  Detail pages can use `return.href` and `result.action.return`.
- [x] WEB-60: Harden widget attribute parsing.
  Widget attributes handle single quotes and URL-colon navigation values.
- [x] WEB-61: Recheck Static Form result conventions.
  Static Form result conventions remain the primary app mechanism.

## 6. Next Phase Candidates

- NP-1201: Rich UI generation from wireframe DSL.
- NP-1202: SPA hosting as a separate mode beyond Static Form Web App plus
  islands.
- NP-1203: Advanced dashboard visualization such as SVG assembly diagrams.
- NP-1204: Public JavaScript SDK and generated client helpers.

## 7. References

- `docs/journal/2026/04/web-application-integration-note.md`
- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-descriptor-note.md`
- `docs/journal/2026/04/web-api-response-note.md`
- `docs/journal/2026/04/web-integration-spa.md`
- `docs/journal/2026/04/web-wireframe-dsl-note.md`
- `docs/journal/2026/04/web-cml-wireframe-generation-note.md`
- `docs/phase/phase-12-checklist.md`
