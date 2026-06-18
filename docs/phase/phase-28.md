# Phase 28 — Web UI DSL / Bootstrap Core / Material Design / UX Profile

status = active

## 1. Purpose of This Document

This work document records Phase 28, which implements the active `9.19 Web UI
DSL / Bootstrap Core / Material Design / UX Profile` development item.

Phase 27 closed the Knowledge Editor and Domain Knowledge Authoring baseline.
Phase 28 turns the Web lessons from that editor work into reusable CNCF
Web/platform infrastructure: semantic Web UI widgets, stable Bootstrap Core
DOM, selectable UX profiles, a Material Design profile path, editable repeated
form rows, and demo-assist metadata for generated screens.

This document is a phase dashboard, not a design journal.

## 2. Phase Scope

- Promote the Web UI DSL from ad hoc widget rendering into a canonical
  generated-screen vocabulary.
- Preserve existing display widgets such as `textus:table`,
  `textus:card-list`, `textus:line-list`, `textus:summary-card`,
  `textus:action-form`, and `textus:error-panel`.
- Add an editable repeated-row widget as a distinct semantic widget. Existing
  `textus:line-list` remains display-oriented.
- Define Bootstrap Core DOM conventions for generated pages, sections, forms,
  fields, actions, and widgets.
- Standardize semantic selector attributes such as `data-textus-page`,
  `data-textus-section`, `data-textus-form`, `data-textus-field`,
  `data-textus-action`, and `data-textus-widget`.
- Add a UX Profile model with initial `bootstrap`, `material`, `compact`, and
  `admin` profiles.
- Treat Material Design as a UX profile layered on the same semantic widget and
  Bootstrap Core DOM model, not as a separate Web runtime.
- Use `/Users/asami/src/dev2026/textus-knowledge-editor` as the first
  application driver. InformationSpace / Knowledge Editor screens provide the
  concrete pressure, while CNCF owns the reusable Web UI DSL, widget, DOM, and
  UX profile contracts for Static Form Web Apps and generated
  admin/application screens.
- Treat TKE source fragment composition/fragments editor repeated-row editing
  as a concrete `textus:editable-line-list` migration driver, replacing
  application-local JavaScript as the primary editing model where appropriate.
- Add a Web Demo Assist Manifest for cozy video and demo script generation.

Scope boundaries:

- Phase 28 does not reopen Phase 27 Knowledge Editor items except for targeted
  regressions.
- Phase 28 does not replace Static Form Web Apps with an SPA runtime.
- Phase 28 does not implement Web Island Architecture Runtime.
- Phase 28 does not implement API Gateway / public REST exposure policy.
- Phase 28 does not add a production visual theme marketplace.
- Phase 28 does not introduce a custom frontend package lifecycle or design
  system compiler.

## 3. Active Work Stack

- A (DONE): WU-01 — Open Phase 28 and freeze Web UI DSL scope.
- B (DONE): WU-02 — Web UI DSL vocabulary and projection contract.
- C (DONE): WU-03 — Bootstrap Core DOM and semantic selector contract.
- D (OPEN): WU-04 — UX Profile model with Bootstrap and Material profile
  support.
- E (OPEN): WU-05 — Static Form renderer integration vertical slice.
- F (OPEN): WU-06 — Editable line-list widget vertical slice.
- G (OPEN): WU-07 — TKE / InformationSpace / Knowledge Editor driver
  integration.
- H (OPEN): WU-08 — Validation, issue, capability, and empty-state widget
  alignment.
- I (OPEN): WU-09 — Web Demo Assist Manifest for cozy video and demo tooling.
- J (OPEN): WU-10 — Phase 28 verification and closure.

Resume hint:

- Continue with WU-04. Keep Phase 27 closed and treat Knowledge Editor screens
  as drivers for CNCF Web/platform infrastructure, not as the owner of the Web
  UI architecture.

## 4. Development Items

- [x] WU-01: Open Phase 28 and freeze Web UI DSL scope.
- [x] WU-02: Web UI DSL vocabulary and projection contract.
- [x] WU-03: Bootstrap Core DOM and semantic selector contract.
- [ ] WU-04: UX Profile model with Bootstrap and Material profile support.
- [ ] WU-05: Static Form renderer integration vertical slice.
- [ ] WU-06: Editable line-list widget vertical slice.
- [ ] WU-07: TKE / InformationSpace / Knowledge Editor driver integration.
- [ ] WU-08: Validation, issue, capability, and empty-state widget alignment.
- [ ] WU-09: Web Demo Assist Manifest for cozy video and demo tooling.
- [ ] WU-10: Phase 28 verification and closure.

Detailed task breakdown and progress tracking are recorded in
`phase-28-checklist.md`.

## 5. Completion Conditions

Phase 28 can close when:

- The Web UI DSL vocabulary is documented and backed by executable renderer or
  projection specs.
- Generated pages expose stable Bootstrap Core DOM and semantic
  `data-textus-*` selectors for pages, sections, forms, fields, actions, and
  widgets.
- Existing display widgets remain compatible.
- The editable repeated-row widget supports add row, delete row, hidden
  template row, stable field names, row-level validation anchors, field-level
  validation anchors, and no-JS fallback.
- UX profile selection supports at least the Bootstrap baseline and a Material
  profile path without changing operation selectors, form field names,
  authorization, data binding, or server-side execution paths.
- TKE InformationSpace / Knowledge Editor driver screens exercise the new
  widget and DOM contracts.
- TKE fragments editor/source fragment composition repeated-row editing is
  covered as an editable-line-list migration target.
- The Web Demo Assist Manifest can expose page/section/form/field/action/widget
  selector mappings when demo mode is explicitly enabled.
- Demo assist does not expose hidden sensitive values, session tokens, raw
  provider payloads, or confidential field values.
- Deferred Web work remains tracked under the independent 9.x Web/platform
  development items.
