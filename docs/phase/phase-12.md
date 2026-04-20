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
