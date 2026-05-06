# Phase 21 — Web Next Stage / Static Form UI

status = active

## 1. Purpose of This Document

This work document records the active stack of work items for Phase 21.
It is authoritative for current scope, explicit deferrals, and closure status.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Make `8.1 Web Next Stage / Static Form UI` the active development item.
- Use Static Form UI as the primary driver for the Web Next Stage.
- Standardize CNCF admin/runtime, manual, and Static Form App pages around
  Bootstrap 5 primitives.
- Expand Textus widgets for reusable card, list, summary, feedback, action,
  job, and dialog-style surfaces.
- Preserve the convention-first Static Form baseline: pages are server-rendered
  and usable without JavaScript.
- Use progressive JavaScript only as enhancement for dialogs, picker flows,
  in-place refresh, and later Island Architecture work.
- Keep application driver pressure from CNCF admin/runtime pages and selected
  `textus-blog` screens.
- Strengthen developer documentation so component/application developers know
  when to use plain Bootstrap markup, Textus widgets, or component-owned pages.

Final semantic direction:

- Bootstrap 5 is the CNCF Web output vocabulary.
- Textus widgets are server-rendered Bootstrap components over the existing
  property/result/action metadata model.
- Dialog-style actions must reuse the existing action metadata contract rather
  than introduce a separate client-side action model.
- Application-level job UX and development debug panels are part of the Static
  Form UI baseline.
- Island Architecture is the future progressive-enhancement layer, not the
  Phase 21 primary implementation surface.

## 3. Non-Goals

- No SPA framework adoption.
- No API gateway or separate SPA hosting mode.
- No visual design system separate from Bootstrap 5.
- No broad UI generation, file generation, standalone wireframe DSL, or new
  runtime renderer outside the Static Form UI generation contract.
- No component-owned admin page discovery/integration unless a concrete Phase
  21 UI slice needs it.
- No JavaScript-only behavior without a server-rendered fallback.

## 4. Active Work Stack

- A (DONE): WN-01 — Open Phase 21 and freeze Static Form UI scope.
- B (DONE): WN-02 — Normalize Bootstrap 5 page primitives.
- C (DONE): WN-03 — Expand Textus widget card/list/feedback surfaces.
- D (DONE): WN-04 — Add reusable dialog-style action surfaces.
- E (DONE): WN-05 — Apply UI primitives to CNCF admin/runtime pages.
- F (DONE): WN-06 — Apply selected improvements to `textus-blog`.
- G (DONE): WN-07 — Phase 21 backlog / status synchronization.
- H (DONE): WN-08 — Search UI / Query / Semantic Search Alignment.
- I (DONE): WN-09 — Web/UI Generation and Static Form Layout Composition.
- J (DONE): WN-10 — Reusable Header/Footer/Nav/Sidebar/Layout Partials.
- K (ACTIVE): WN-11 — Broader Bootstrap 5 Admin/App Polish.
- L (DONE): WN-12 — Subsystem Web App Composition From Component Web.
- M (TODO): WN-13 — Island Architecture Progressive Enhancement.
- N (TODO): WN-14 — SPA Hosting / API Gateway Boundary Design.
- O (TODO): WN-15 — Application Developer Documentation Completion.

Resume hint:

- Continue with WN-11 Broader Bootstrap 5 Admin/App Polish.
  Phase 21 is not ready to close while WN-11 through WN-15 remain active or
  TODO.

## 5. Development Items

- [x] WN-01: Open Phase 21 and freeze Static Form UI scope.
- [x] WN-02: Normalize Bootstrap 5 page primitives.
- [x] WN-03: Expand Textus widget card/list/feedback surfaces.
- [x] WN-04: Add reusable dialog-style action surfaces.
- [x] WN-05: Apply UI primitives to CNCF admin/runtime pages.
- [x] WN-06: Apply selected improvements to `textus-blog`.
- [x] WN-07: Phase 21 backlog / status synchronization.
- [x] WN-08: Search UI / Query / Semantic Search Alignment.
- [x] WN-09: Web/UI Generation and Static Form Layout Composition.
- [x] WN-10: Reusable Header/Footer/Nav/Sidebar/Layout Partials.
- [ ] WN-11: Broader Bootstrap 5 Admin/App Polish.
- [x] WN-12: Subsystem Web App Composition From Component Web.
- [ ] WN-13: Island Architecture Progressive Enhancement.
- [ ] WN-14: SPA Hosting / API Gateway Boundary Design.
- [ ] WN-15: Application Developer Documentation Completion.

Detailed task breakdown and progress tracking are recorded in
`phase-21-checklist.md`.

## 6. Completion Conditions

Phase 21 can close only when:

- Strategy and phase documents identify Phase 21 as the active Web Next Stage.
- CNCF generated/admin/runtime pages use a documented Bootstrap 5 primitive
  set for page shell, navigation, forms, tables, cards, alerts, empty states,
  job panels, and debug panels.
- Textus widget docs/specs cover the added card/list/summary/feedback/dialog
  widgets and their Bootstrap output contract.
- Reusable dialog-style action surfaces work with existing action metadata and
  have no-JS fallbacks.
- At least one CNCF admin/runtime page group and one selected application page
  group validate the new UI primitives.
- Search UI, UI generation, reusable layout partials, broader Bootstrap polish,
  component-Web subsystem composition, Island Architecture, SPA/API gateway
  boundary design, and developer documentation are completed or explicitly
  moved out of Phase 21 by decision.
- Tests cover renderer/widget behavior and targeted driver pages.
- No Phase 21 work item remains implicitly untracked.
