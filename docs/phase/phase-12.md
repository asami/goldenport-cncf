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
- J (PLANNED): WEB-10 — Polish built-in Web pages with Bootstrap 5.
  Management Console, Dashboard, Manual, and Static Form App should use the
  local Bootstrap 5 baseline to provide clean responsive business/admin pages.
- K (PLANNED): WEB-11 — Expand the Textus widget set.
  Card widgets are the first focus, followed by layout, navigation, feedback,
  content, and form-helper widgets that keep Static Form App expression-oriented
  rather than control-structure based.
- L (PLANNED): WEB-12 — Define Web app packaging and deployment.
  Static Form Web App packaging should cover descriptor discovery, static
  result templates, local assets, optional island scripts, and archive/project
  deployment without requiring a SPA framework.

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
- [ ] WEB-10: Polish built-in Web pages with Bootstrap 5.
  Apply the Bootstrap 5 UI polish design to Management Console, Dashboard,
  Manual, and Static Form App pages without changing their runtime data
  contracts.
- [ ] WEB-11: Expand the Textus widget set.
  Implement the next Static Form App widget group, starting with
  `textus:record-card` and `textus:card-list`, then adding layout, navigation,
  feedback, content, and form-helper widgets as needed.
- [ ] WEB-12: Define Web app packaging and deployment.
  Specify and implement how Static Form Web App resources are packaged and
  deployed: Web Descriptor, static result templates, app-local assets, optional
  island scripts, project discovery, and packaged component/subsystem archive
  discovery.

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
