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

Status: TODO

### Objective

Consolidate common CNCF Web rendering patterns around Bootstrap 5 primitives.

### Detailed Tasks

- [ ] Normalize page shell, breadcrumbs, nav/action bars, cards, alerts, empty
      states, responsive tables, and form layouts.
- [ ] Keep admin and operational pages dense but readable.
- [ ] Remove targeted ad hoc grid/link/list markup where Bootstrap primitives
      fit.
- [ ] Keep local asset behavior and no-CDN deployment assumptions intact.

### Expected Output

- Generated/admin pages share recognizable Bootstrap page primitives.
- Responsive behavior does not depend on application-specific layout CSS.

---

## WN-03: Expand Textus Widget Card/List/Feedback Surfaces

Status: TODO

### Objective

Add or harden reusable Textus widgets for common Static Form UI surfaces.

### Detailed Tasks

- [ ] Implement/harden card, record-card, card-list, summary-card, feedback,
      empty-state, and navigation/list widgets.
- [ ] Reuse existing source/view/property binding and schema field ordering.
- [ ] Ensure widgets render Bootstrap 5 classes and work in fragment and full
      document templates.
- [ ] Keep existing widget aliases and compatibility behavior.

### Expected Output

- Static Form templates can express common result/detail/list pages without
  hand-written repetitive Bootstrap markup.

---

## WN-04: Reusable Dialog-Style Action Surfaces

Status: TODO

### Objective

Add dialog-style action UI for important or destructive actions without
introducing a separate client-side action model.

### Detailed Tasks

- [ ] Add a minimal confirmation action widget or renderer primitive.
- [ ] Bind dialog actions to existing action metadata: source, href, method,
      label, hidden context, and result page context.
- [ ] Use Bootstrap modal markup when JavaScript is available.
- [ ] Render a normal link/form fallback when JavaScript is unavailable.

### Expected Output

- Applications and admin pages can add confirmation dialogs while preserving
  server-rendered behavior and existing authorization/operation dispatch.

---

## WN-05: CNCF Admin/Runtime Driver Pages

Status: TODO

### Objective

Apply the Phase 21 primitives to targeted CNCF admin/runtime pages.

### Detailed Tasks

- [ ] Apply Bootstrap primitive cleanup to selected dashboard/admin/detail
      pages.
- [ ] Keep system/admin routes and response shapes stable.
- [ ] Preserve existing job result and development debug panel behavior.
- [ ] Add focused renderer specs for the targeted pages.

### Expected Output

- CNCF admin/runtime pages validate the common UI primitives under real
  framework output.

---

## WN-06: `textus-blog` Driver Pages

Status: TODO

### Objective

Apply selected Phase 21 UI improvements to `textus-blog` without changing Blog
domain behavior.

### Detailed Tasks

- [ ] Apply primitives only to selected public/editor/user pages that already
      use Static Form/App patterns.
- [ ] Preserve routes, form fields, operation inputs, and response shape.
- [ ] Keep no-JS behavior available for forms and results.
- [ ] Add or maintain focused `ComponentFactorySpec` coverage when Blog files
      are touched.

### Expected Output

- `textus-blog` remains the application driver for Bootstrap/Textus widget
  usability without becoming the owner of CNCF generic UI behavior.

---

## WN-07: Verification, Documentation, and Phase Closure

Status: TODO

### Objective

Verify Phase 21 behavior, update developer-facing documentation, and close the
phase when the selected UI slices are complete.

### Detailed Tasks

- [ ] Update `docs/spec/textus-widget.md`.
- [ ] Update `docs/notes/static-form-web-app-bootstrap-guide.md`.
- [ ] Update Static Form App contract docs when routes/widgets/properties
      change.
- [ ] Run focused renderer/widget specs.
- [ ] Run CNCF `sbt --batch test`.
- [ ] Run `textus-blog` focused/full validation if Blog is touched.
- [ ] Run `git diff --check` in touched repos.
- [ ] Record deferred Island Architecture, SPA hosting, API gateway, broad UI
      generation, and component-owned admin integration work.
- [ ] Mark Phase 21 items DONE or explicitly deferred before closure.
