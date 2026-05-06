# Phase 21 — Web Next Stage / Static Form UI Checklist

This document contains detailed task tracking and decisions for Phase 21.
It complements the summary-level phase document (`phase-21.md`) and may be
used as the closure record for completed Phase 21 work.

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-21.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## WN-01: Open Phase 21 and Freeze Static Form UI Scope

Status: DONE

### Objective

Open Phase 21 as the Web Next Stage and fix Static Form UI as the primary
implementation driver.

### Detailed Tasks

- [x] Create `docs/phase/phase-21.md`.
- [x] Create `docs/phase/phase-21-checklist.md`.
- [x] Mark Phase 21 as the current active phase in strategy.
- [x] Keep Phase 20 as the latest closed phase.
- [x] Record primary driver as Static Form UI.
- [x] Record concrete drivers:
  - CNCF admin/runtime pages;
  - Static Form App pages and result pages;
  - selected `textus-blog` pages.
- [x] Record that Bootstrap 5 is the Web output vocabulary.
- [x] Record that Textus widgets are server-rendered Bootstrap components.
- [x] Record Island Architecture as progressive enhancement and future work,
      not the primary Phase 21 implementation surface.

### Decisions

- Static Form UI is the Phase 21 driver.
- Pages remain server-rendered and usable without JavaScript.
- Textus widgets reuse existing property/result/action metadata.
- Dialog-style actions must reuse existing action metadata and provide no-JS
  fallbacks.
- Component-owned admin integration is deferred unless needed by a concrete UI
  slice.

### Guardrails

- Do not introduce a SPA framework.
- Do not introduce a separate visual design system.
- Do not add JavaScript-only behavior without server-rendered fallback.
- Do not change existing routes or response shapes for UI polish alone.

---

## WN-02: Normalize Bootstrap 5 Page Primitives

Status: DONE

### Objective

Consolidate common CNCF Web rendering patterns around Bootstrap 5 primitives.

### Detailed Tasks

- [x] Normalize page shell, breadcrumbs, nav/action bars, cards, alerts, empty
      states, responsive tables, and form layouts.
- [x] Keep admin and operational pages dense but readable.
- [x] Remove targeted ad hoc grid/link/list markup where Bootstrap primitives
      fit.
- [x] Keep local asset behavior and no-CDN deployment assumptions intact.

### Expected Output

- Generated/admin pages share recognizable Bootstrap page primitives.
- Responsive behavior does not depend on application-specific layout CSS.

### Completion Notes

- `_simple_page` no longer applies global card-like styling to every
  `<article>` element. Framed panels use explicit Bootstrap card classes.
- `StaticFormAppRenderer` now has shared private primitives for admin cards,
  action rows, list-group navigation, and responsive tables.
- Targeted system admin, component admin, system console, assembly diagnostics,
  job/admin result, and create/update result pages preserve existing routes
  while using Bootstrap 5 primitives.
- Focused `StaticFormAppRendererSpec`, `Test/compile`, and full CNCF
  `sbt --batch test` passed during the implementation slice. WN-07 now keeps
  the remaining Phase 21 backlog visible instead of closing the phase.

---

## WN-03: Expand Textus Widget Card/List/Feedback Surfaces

Status: DONE

### Objective

Add or harden reusable Textus widgets for common Static Form UI surfaces.

### Detailed Tasks

- [x] Implement/harden card, record-card, card-list, summary-card, feedback,
      empty-state, and navigation/list widgets.
- [x] Reuse existing source/view/property binding and schema field ordering.
- [x] Ensure widgets render Bootstrap 5 classes and work in fragment and full
      document templates.
- [x] Keep existing widget aliases and compatibility behavior.

### Expected Output

- Static Form templates can express common result/detail/list pages without
  hand-written repetitive Bootstrap markup.

### Completion Notes

- `textus:card-list` supports responsive `cols`, `md`, and `lg` layout
  attributes while preserving paging and view-column behavior.
- `textus:empty-state` can render a primary action with `action-label` and
  `action-href`.
- `textus:nav-list` supports JSON source arrays with `label`, `href`,
  optional `class`, and optional `method`; non-GET entries render forms.
- Focused `StaticFormAppRendererSpec` covers WN-03 widget aliases, Bootstrap
  output, hidden-field filtering, empty states, action rendering, and full HTML
  asset completion.

---

## WN-04: Reusable Dialog-Style Action Surfaces

Status: DONE

### Objective

Add dialog-style action UI for important or destructive actions without
introducing a separate client-side action model.

### Detailed Tasks

- [x] Add a minimal confirmation action widget or renderer primitive.
- [x] Bind dialog actions to existing action metadata: source, href, method,
      label, hidden context, and result page context.
- [x] Use Bootstrap modal markup when JavaScript is available.
- [x] Render a normal link/form fallback when JavaScript is unavailable.

### Expected Output

- Applications and admin pages can add confirmation dialogs while preserving
  server-rendered behavior and existing authorization/operation dispatch.

### Completion Notes

- `textus:confirm-action` / `textus-confirm-action` renders a Bootstrap modal
  confirmation surface over the existing action metadata model.
- GET actions render anchors and non-GET actions render forms in both modal
  confirm controls and no-JS fallback.
- Applying the widget to concrete admin/runtime pages is left to WN-05.

---

## WN-05: CNCF Admin/Runtime Driver Pages

Status: DONE

### Objective

Apply the Phase 21 primitives to targeted CNCF admin/runtime pages.

### Detailed Tasks

- [x] Apply Bootstrap primitive cleanup to selected dashboard/admin/detail
      pages.
- [x] Keep system/admin routes and response shapes stable.
- [x] Preserve existing job result and development debug panel behavior.
- [x] Add focused renderer specs for the targeted pages.

### Expected Output

- CNCF admin/runtime pages validate the common UI primitives under real
  framework output.

### Completion Notes

- Blob admin, generic Association admin, and Tag/Association result navigation
  now use Bootstrap admin cards, responsive tables, and action rows.
- Generic TagAttachment/Association/BlobAttachment detach controls use
  confirmation modal markup with no-JS fallback.
- Dashboard, performance anchors, job result pages, application job pages, and
  development debug panel behavior remain unchanged.

---

## WN-06: `textus-blog` Driver Pages

Status: DONE

### Objective

Apply selected Phase 21 UI improvements to `textus-blog` without changing Blog
domain behavior.

### Detailed Tasks

- [x] Apply primitives only to selected public/editor/user pages that already
      use Static Form/App patterns.
- [x] Preserve routes, form fields, operation inputs, and response shape.
- [x] Keep no-JS behavior available for forms and results.
- [x] Add or maintain focused `ComponentFactorySpec` coverage when Blog files
      are touched.

### Expected Output

- `textus-blog` remains the application driver for Bootstrap/Textus widget
  usability without becoming the owner of CNCF generic UI behavior.

### Completion Notes

- User post list/result pages and editor/fallback edit pages use Bootstrap
  cards, grid forms, selected-tag alerts, and existing Textus widgets.
- Image picker and import surfaces use Bootstrap modal markup while preserving
  the existing Blog form actions, field names, and JavaScript data hooks.
- Public Blog pages remain regression-covered and unchanged except through
  shared asset behavior.

---

## WN-07: Phase 21 Backlog / Status Synchronization

Status: DONE

### Objective

Re-expand the Phase 21 backlog so the remaining Web Next Stage development
items are visible and Phase 21 does not close prematurely.

### Detailed Tasks

- [x] Rename the previous closure item to status synchronization.
- [x] List all remaining Phase 21 Web Next Stage items as explicit WN items.
- [x] Update strategy so WN-01 through WN-06 are completed slices, not the
      whole phase.
- [x] Remove wording that implies Phase 21 should close before WN-08 through
      WN-15 are completed or explicitly moved out of Phase 21.
- [x] Keep Phase 21 status active.

### Expected Output

- Phase 21 remains the active Web Next Stage phase.
- No remaining Web Next Stage item is hidden under a generic closure task.

### Completion Notes

- WN-08 through WN-15 are now explicit Phase 21 backlog items.
- Phase 21 closure is blocked until all WN items are DONE or explicitly moved
  out of Phase 21 by decision.

---

## WN-08: Search UI / Query / Semantic Search Alignment

Status: DONE

### Objective

Design and implement the Web-facing search layer for Static Form UI.

### Detailed Tasks

- [x] Define the full-text search planning layer for Web forms and result pages.
- [x] Align CML, View, Query, and Web metadata for search-facing fields.
- [x] Define how semantic/embedding-backed search appears in generated and
      hand-written Static Form pages.
- [x] Add result summary, filter, sort, and pagination UI patterns that remain
      usable without JavaScript.

### Expected Output

- Search forms and results can be generated or authored consistently across
  admin/runtime and application pages.
- `WebSearchQueryPlanner` maps Web form input into existing `Query` /
  `EntityQuery` planning, including `q`, filters, sort, limit/offset, and
  include-total.
- Admin entity list pages expose a Bootstrap search card, active filter chips,
  clear links, pagination links that preserve query state, and deterministic
  empty/unsupported feedback.
- View metadata exposes searchable, filterable, sortable fields and supported
  search modes.
- `q` is the canonical generic search text parameter; `text` remains a
  compatibility alias.
- Ordinary full-text search uses existing `Query` / `EntityQuery` planning.
  Semantic and hybrid search modes are surfaced as capability choices and fail
  deterministically until a semantic backend is configured.

### Guardrails

- Do not require a semantic search backend for ordinary full-text search.
- Do not introduce SPA-only search behavior.

---

## WN-09: Web/UI Generation and Static Form Layout Composition

Status: ACTIVE

### Objective

Define the generation layer above Static Form primitives without replacing the
server-rendered baseline.

### Detailed Tasks

- [ ] Define wireframe/UI generation strategy above Bootstrap/Textus primitives.
- [ ] Clarify responsibility split between generated UI and hand-written static
      pages.
- [ ] Define page layout composition rules for generated list, detail, form,
      result, and dashboard pages.
- [ ] Keep generated UI output as ordinary Bootstrap/Textus server-rendered
      markup.

### Expected Output

- Component/application developers can choose generated UI or hand-written
  static pages without changing runtime contracts.

### Guardrails

- Do not introduce a separate visual DSL that bypasses Bootstrap/Textus output.

---

## WN-10: Reusable Header/Footer/Nav/Sidebar/Layout Partials

Status: TODO

### Objective

Provide reusable page composition primitives for common Static Form App shells.

### Detailed Tasks

- [ ] Define reusable header, footer, navigation, sidebar, and layout partials.
- [ ] Support component-owned and application-owned page shells.
- [ ] Preserve local asset completion and no-CDN deployment.
- [ ] Keep partials usable from both generated pages and hand-written static
      pages.

### Expected Output

- Static Form Apps can share consistent shell/navigation structure without
  copy-pasting full HTML layouts.

### Guardrails

- Do not make JavaScript mandatory for navigation or layout.

---

## WN-11: Broader Bootstrap 5 Admin/App Polish

Status: TODO

### Objective

Continue aligning CNCF admin/runtime and application driver pages with
Bootstrap 5 standards.

### Detailed Tasks

- [ ] Identify remaining ad hoc layout CSS in CNCF admin/runtime pages.
- [ ] Identify selected app pages that still need Bootstrap/Textus polish.
- [ ] Normalize dense admin surfaces around cards, list groups, responsive
      tables, forms, alerts, badges, and action rows.
- [ ] Preserve existing routes, operation inputs, response shapes, and no-JS
      behavior.

### Expected Output

- Admin and app pages remain consistent with the Bootstrap 5 guidance.

### Guardrails

- Do not perform broad visual redesign without a concrete driver page.

---

## WN-12: Component-Owned Web Admin Page Integration

Status: TODO

### Objective

Allow component CARs to contribute component-specific admin pages while CNCF
keeps the system-level admin shell and authorization boundary.

### Detailed Tasks

- [ ] Define the descriptor contract for component-owned admin pages.
- [ ] Let the system admin console discover and link component admin pages.
- [ ] Keep CNCF responsible for admin authorization, navigation composition,
      and system-level framing.
- [ ] Keep component CARs responsible for component-specific admin content and
      local admin routes.

### Expected Output

- Component-specific admin functions can appear in the CNCF admin surface
  without hard-coding them into the core renderer.

### Guardrails

- Do not let component pages bypass CNCF admin authorization.

---

## WN-13: Island Architecture Progressive Enhancement

Status: TODO

### Objective

Define and implement the progressive-enhancement layer for interactive page
regions while preserving server-rendered Static Form behavior.

### Detailed Tasks

- [ ] Define the Island Architecture contract for scoped interactive regions.
- [ ] Define how islands receive data, actions, and assets from Static Form
      pages.
- [ ] Keep no-JS fallback behavior authoritative.
- [ ] Add one concrete admin or application driver only after the contract is
      stable.

### Expected Output

- Interactive enhancements can be added incrementally without turning Static
  Form pages into a SPA.

### Guardrails

- Do not introduce a global client-side app shell.

---

## WN-14: SPA Hosting / API Gateway Boundary Design

Status: TODO

### Objective

Define SPA hosting and API gateway as explicit deployment modes, separate from
the Static Form baseline.

### Detailed Tasks

- [ ] Define when a separate SPA hosting mode is appropriate.
- [ ] Define API gateway boundaries for Form API, REST API, auth/session, assets,
      and admin operations.
- [ ] Record compatibility with existing Static Form App routes.
- [ ] Keep Static Form UI as the default CNCF Web mode.

### Expected Output

- SPA/API gateway support can be planned without weakening the server-rendered
  baseline.

### Guardrails

- Do not make SPA hosting an implicit extension of every Static Form App.

---

## WN-15: Application Developer Documentation Completion

Status: TODO

### Objective

Complete developer-facing documentation for the Web Next Stage.

### Detailed Tasks

- [ ] Update `docs/spec/textus-widget.md` for all implemented widgets and
      attributes.
- [ ] Update `docs/notes/static-form-web-app-bootstrap-guide.md`.
- [ ] Update Static Form App contract docs when routes/widgets/properties
      change.
- [ ] Add an index or cross-reference path for component/application developers.
- [ ] Record examples for Bootstrap markup, Textus widgets, job UX, debug
      panels, and no-JS fallbacks.

### Expected Output

- Component/application developers can find the correct Web guidance without
  reading phase history.

### Guardrails

- Do not bury active developer guidance only in phase/checklist documents.

---

## Phase 21 Closure Gate

Status: BLOCKED

Phase 21 must not close until all WN items are DONE or explicitly moved out of
Phase 21 by decision.
