# Phase 28 — Web UI DSL / Bootstrap Core / Material Design / UX Profile Checklist

This document contains detailed task tracking and decisions for Phase 28.
It complements the summary-level phase document (`phase-28.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-28.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.
- Reasoning, experiments, and deep dives should be recorded in journal entries
  when necessary.

---

## WU-01: Open Phase 28 and Freeze Web UI DSL Scope

Status: DONE

### Objective

Open Phase 28 as the implementation phase for `9.19 Web UI DSL / Bootstrap
Core / Material Design / UX Profile`. Freeze the scope as CNCF Web/platform
infrastructure, not as a Phase 27 Knowledge Editor continuation.

### Initial Tasks

- [x] Add Phase 28 dashboard and checklist documents.
- [x] Update the strategy current phase pointers to Phase 28.
- [x] Keep Phase 27 closed and record
      `/Users/asami/src/dev2026/textus-knowledge-editor` screens as drivers
      only.
- [x] Include editable repeated-row widgets in the initial Phase 28 scope.
- [x] Include Web Demo Assist Manifest in the initial Phase 28 scope.
- [x] Keep SPA mode, Web Island Architecture Runtime, API Gateway policy,
      Component-owned Admin Surface Discovery, Notification UX, and Structured
      Web/API Error Presentation outside Phase 28.

### Decisions

- Phase 28 owns the active `9.19` Web/platform development item.
- `textus:line-list` remains a display-oriented widget.
- Editable repeated-row form editing uses a distinct semantic widget,
  tentatively `textus:editable-line-list`.
- Bootstrap Core DOM is the stable generated-screen substrate.
- Material Design is a UX profile layered over semantic widgets and Bootstrap
  Core DOM, not a separate runtime.
- Semantic `data-textus-*` attributes are the preferred selector surface for
  generated screens.
- Web Demo Assist Manifest is exposed as an opt-in JSON endpoint controlled by
  runtime configuration.
- `/Users/asami/src/dev2026/textus-knowledge-editor` is the first application
  driver. CNCF owns the reusable Web UI DSL, widget, DOM, and UX profile
  contracts generalized from that driver.

### Guardrails

- Do not alter operation selectors, form field names, authorization semantics,
  data binding, or server-side execution paths for profile-specific rendering.
- Do not repurpose existing display `textus:line-list` as an editable form
  widget.
- Do not expose hidden sensitive values, session tokens, raw provider payloads,
  or confidential field values through demo assist metadata.
- Do not make Knowledge Editor application code the owner of the Web UI DSL
  architecture.

### Expected Output

- Phase 28 is visible from the strategy document.
- The first implementation slice can start from WU-02.

### Completion Notes

- Phase 28 dashboard and checklist documents were created.
- Strategy current status now points to Phase 28.
- Next active work is WU-02.

---

## WU-02: Web UI DSL Vocabulary and Projection Contract

Status: DONE

### Objective

Define the canonical semantic widget vocabulary and projection contract for
generated CNCF Web screens.

### Initial Tasks

- [x] Inventory existing widgets and classify them as display, layout, action,
      feedback, form-edit, or runtime-helper widgets.
- [x] Preserve existing display widget behavior for `textus:table`,
      `textus:card-list`, `textus:line-list`, `textus:summary-card`,
      `textus:action-form`, and `textus:error-panel`.
- [x] Define the first editable widget vocabulary entry for
      `textus:editable-line-list`.
- [x] Define how widget metadata is projected from CML/schema/view metadata,
      WebDescriptor/form descriptor metadata, operation result/page context,
      and widget attributes.
- [x] Add executable specs for the vocabulary shape selected in this slice.

### Expected Output

- A stable vocabulary baseline that implementation slices can render without
  ad hoc application-local UI contracts.

### Completion Notes

- `docs/spec/textus-widget.md` now contains the normative WU-02 vocabulary
  classification and projection contract.
- CNCF has a lightweight typed vocabulary/projection model:
  `TextusWidgetDefinition`, `TextusWidgetVocabulary`, and
  `TextusWidgetProjection`.
- `textus:line-list` remains display-only.
- `textus:editable-line-list` is a known `form-edit` vocabulary entry, but
  full renderer behavior remains deferred to WU-06.
- Focused vocabulary specs and existing renderer compatibility specs pass.

---

## WU-03: Bootstrap Core DOM and Semantic Selector Contract

Status: DONE

### Objective

Define stable generated DOM conventions that are Bootstrap-compatible and
machine-readable by tests, demo tooling, and AI-assisted authoring.

### Initial Tasks

- [x] Standardize `data-textus-page`, `data-textus-section`,
      `data-textus-form`, `data-textus-field`, `data-textus-action`, and
      `data-textus-widget`.
- [x] Prefer semantic selectors over brittle generated CSS paths.
- [x] Define Bootstrap Core DOM conventions for pages, sections, forms,
      fields, actions, widgets, empty states, and validation messages.
- [x] Add renderer specs proving generated pages expose stable selectors.

### Expected Output

- A selector and DOM contract that can support renderer specs, demo manifest
  generation, and future UX profiles.

### Completion Notes

- `docs/spec/textus-widget.md` now defines the WU-03 Bootstrap Core DOM and
  semantic selector contract.
- Static Form operation forms expose stable `data-textus-page`,
  `data-textus-section`, `data-textus-form`, `data-textus-field`, and
  `data-textus-action` selectors without changing form names, routes, methods,
  or validation behavior.
- Representative Textus widgets now expose `data-textus-widget` on their root
  elements for `textus:line-list`, `textus:summary-card`, `textus:alert`,
  `textus:empty-state`, and `textus:status-badge`.
- Focused renderer specs and the full CNCF test suite pass.

---

## WU-04: UX Profile Model with Bootstrap and Material Profile Support

Status: OPEN

### Objective

Add UX profile selection for generated/admin/application screens and establish
Material Design as a profile path over the same semantic widget model.

### Initial Tasks

- [ ] Define initial profile names: `bootstrap`, `material`, `compact`, and
      `admin`.
- [ ] Add profile configuration at WebDescriptor/app/page level where it fits
      existing Web configuration.
- [ ] Keep Bootstrap rendering as the baseline profile.
- [ ] Add Material profile metadata/assets/classes without changing runtime
      operation semantics.
- [ ] Add deterministic behavior for unknown profile names.

### Expected Output

- A profile mechanism that changes presentation while preserving form,
  operation, authorization, and binding contracts.

---

## WU-05: Static Form Renderer Integration Vertical Slice

Status: OPEN

### Objective

Integrate the Web UI DSL, Bootstrap Core DOM, and UX profile contracts into the
Static Form renderer through one executable vertical slice.

### Initial Tasks

- [ ] Select a small generated page or form as the first renderer driver.
- [ ] Emit the standardized semantic DOM markers.
- [ ] Preserve existing Static Form routes, field names, and submit behavior.
- [ ] Add focused renderer specs before broad visual polish.

### Expected Output

- A concrete renderer path that proves the Phase 28 contracts are executable.

---

## WU-06: Editable Line-List Widget Vertical Slice

Status: OPEN

### Objective

Implement editable repeated-row form input without changing the existing
display-oriented `textus:line-list` widget.

### Initial Tasks

- [ ] Add `textus:editable-line-list` or the final selected widget name.
- [ ] Support add row and delete row.
- [ ] Render a hidden template row.
- [ ] Define stable repeated-field naming.
- [ ] Render row-level and field-level validation anchors.
- [ ] Preserve no-JS fallback.
- [ ] Cover the TKE source fragment composition/editor pattern as a target
      use case so repeated source fragments do not require application-local
      JavaScript for the main add/delete/edit workflow.
- [ ] Defer drag-and-drop ordering, nested line-list, complex conditional rows,
      and advanced client-side editing.

### Expected Output

- A reusable editable collection widget suitable for authors, identifiers,
  relationships, and qualifiers.

---

## WU-07: TKE / InformationSpace / Knowledge Editor Driver Integration

Status: OPEN

### Objective

Use TKE InformationSpace / Knowledge Editor screens as drivers for the reusable
Web UI DSL and editable-line-list behavior.

### Initial Tasks

- [ ] Use `/Users/asami/src/dev2026/textus-knowledge-editor` as the concrete
      driver repository for this slice.
- [ ] Select one or more editor fields such as authors, identifiers,
      relationships, or qualifiers.
- [ ] Include the TKE fragments editor/source fragment composition screen as a
      driver for replacing remaining application-local JavaScript repeated-row
      editing with `textus:editable-line-list`.
- [ ] Render those fields through the common editable-line-list widget.
- [ ] Keep any TKE-specific JavaScript as optional progressive enhancement
      around the common widget, not as the primary repeated-row editing model.
- [ ] Keep Knowledge Editor application code as a driver, not the owner of the
      CNCF Web UI DSL contract.
- [ ] Add smoke coverage for the selected driver screen.

### Expected Output

- A real application driver proving the reusable Web/platform contracts.

---

## WU-08: Validation, Issue, Capability, and Empty-State Widget Alignment

Status: OPEN

### Objective

Align generated feedback widgets with the Web UI DSL and Bootstrap Core DOM
contracts.

### Initial Tasks

- [ ] Standardize validation summary and field validation feedback markers.
- [ ] Standardize row-level issue display for editable-line-list.
- [ ] Align capability-aware controls with existing authorization metadata.
- [ ] Align empty-state rendering for tables, lists, and editable collections.

### Expected Output

- Consistent feedback and affordance widgets across generated pages.

---

## WU-09: Web Demo Assist Manifest for Cozy Video and Demo Tooling

Status: OPEN

### Objective

Expose generated-screen structure and stable selectors as a machine-readable
manifest for cozy video and demo script generation.

### Initial Tasks

- [ ] Add runtime configuration for demo assist, with default disabled.
- [ ] Add a JSON manifest endpoint for enabled demo mode.
- [ ] Include page, sections, forms, fields, actions, widgets, and recommended
      selectors.
- [ ] Prefer semantic `data-textus-*` selectors in the manifest.
- [ ] Redact or omit hidden sensitive values, session tokens, raw provider
      payloads, and confidential field values.
- [ ] Add specs for disabled and enabled behavior.

### Expected Output

- Demo tooling can map human demo steps to stable selectors without scraping
  brittle DOM paths.

---

## WU-10: Phase 28 Verification and Closure

Status: OPEN

### Objective

Verify the implemented Phase 28 baseline and either close the phase or record
remaining work under independent future development items.

### Initial Tasks

- [ ] Confirm phase and strategy documents match implemented behavior.
- [ ] Run focused Web UI DSL / renderer / demo manifest specs.
- [ ] Run `sbt --batch Test/compile`.
- [ ] Run `git diff --check`.
- [ ] Record deferred work under the appropriate 9.x development item.

### Expected Output

- Phase 28 can close without hidden Web UI DSL implementation debt.
