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

- Phase 28 owned the `9.19` Web/platform development item through closure.
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

Status: DONE

### Objective

Add UX profile selection for generated/admin/application screens and establish
Material Design as a profile path over the same semantic widget model.

### Initial Tasks

- [x] Define initial profile names: `bootstrap`, `material`, `compact`, and
      `admin`.
- [x] Add profile configuration at WebDescriptor global/app/form/page level
      where it fits existing Web configuration.
- [x] Keep Bootstrap rendering as the baseline profile.
- [x] Add Material profile metadata and contract support without changing
      runtime operation semantics.
- [x] Add deterministic behavior for unknown profile names.

### Expected Output

- A profile mechanism that changes presentation while preserving form,
  operation, authorization, and binding contracts.

### Completion Notes

- `WebUxProfile` defines the fixed built-in `bootstrap`, `material`,
  `compact`, and `admin` profile names.
- `WebDescriptor` loads, validates, merges, and resolves profile metadata for
  global, app, form, page, and admin generated-page scopes.
- Material remains metadata-only in WU-04; renderer class/asset changes remain
  open for later profile-rendering work.

---

## WU-05: Static Form Renderer Integration Vertical Slice

Status: DONE

### Objective

Integrate the Web UI DSL, Bootstrap Core DOM, and UX profile contracts into the
Static Form renderer through one executable vertical slice.

### Initial Tasks

- [x] Select a small generated page or form as the first renderer driver.
- [x] Emit the standardized semantic DOM markers.
- [x] Preserve existing Static Form routes, field names, and submit behavior.
- [x] Add focused renderer specs before broad visual polish.

### Expected Output

- A concrete renderer path that proves the Phase 28 contracts are executable.

### Completion Notes

- Static Form operation forms and default operation result pages now emit the
  resolved UX profile as `data-textus-ux-profile` on their semantic page roots.
- Static page templates receive `page.uxProfile` and `textus.uxProfile` so
  descriptor-resolved profile metadata can be placed by the template.
- Representative admin/generated pages emit `WebDescriptor.adminProfile`
  metadata without changing Bootstrap classes, routes, field names, or
  execution behavior.

---

## WU-06: Editable Line-List Widget Vertical Slice

Status: DONE

### Objective

Implement editable repeated-row form input without changing the existing
display-oriented `textus:line-list` widget.

### Initial Tasks

- [x] Add `textus:editable-line-list` or the final selected widget name.
- [x] Support add row and template-defined delete row controls.
- [x] Render a hidden template row.
- [x] Define stable repeated-field naming.
- [x] Render row-level and field-level validation anchors.
- [x] Preserve no-JS fallback.
- [x] Cover the TKE source fragment composition/editor pattern as a target
      use case so repeated source fragments do not require application-local
      JavaScript for the main add/delete/edit workflow.
- [x] Defer drag-and-drop ordering, nested line-list, complex conditional rows,
      and advanced client-side editing.

### Expected Output

- A reusable editable collection widget suitable for authors, identifiers,
  relationships, and qualifiers.

---

## WU-07: TKE / InformationSpace / Knowledge Editor Driver Integration

Status: DONE

### Objective

Use TKE InformationSpace / Knowledge Editor screens as drivers for the reusable
Web UI DSL and editable-line-list behavior.

### Initial Tasks

- [x] Use `/Users/asami/src/dev2026/textus-knowledge-editor` as the concrete
      driver repository for this slice.
- [x] Select one or more editor fields such as authors, identifiers,
      relationships, or qualifiers.
- [x] Include the TKE fragments editor/source fragment composition screen as a
      driver for replacing remaining application-local JavaScript repeated-row
      editing with `textus:editable-line-list`.
- [x] Render those fields through the common editable-line-list widget.
- [x] Keep any TKE-specific JavaScript as optional progressive enhancement
      around the common widget, not as the primary repeated-row editing model.
- [x] Keep Knowledge Editor application code as a driver, not the owner of the
      CNCF Web UI DSL contract.
- [x] Add smoke coverage for the selected driver screen.

### Expected Output

- A real application driver proving the reusable Web/platform contracts.

---

## WU-08: Validation, Issue, Capability, and Empty-State Widget Alignment

Status: DONE

### Objective

Align generated feedback widgets with the Web UI DSL and Bootstrap Core DOM
contracts.

### Initial Tasks

- [x] Standardize validation summary and field validation feedback markers.
- [x] Standardize row-level issue display for editable-line-list.
- [x] Align capability-aware controls with existing authorization metadata.
- [x] Align empty-state rendering for tables, lists, and editable collections.

### Expected Output

- Consistent feedback and affordance widgets across generated pages.

### Completion Notes

- Form validation summaries and field validation feedback now expose stable
  `data-textus-validation-*` and `data-textus-issue-scope` selectors.
- Editable-line-list rows can carry row issue markers, and generated empty rows
  expose `data-textus-empty-state`.
- Capability messages and disabled controls expose semantic capability state
  without changing authorization behavior.
- TKE related Information editors use the standardized row issue and
  empty-state hooks as the application driver.

---

## WU-09: Web Demo Assist Manifest for Cozy Video and Demo Tooling

Status: DONE

### Objective

Expose generated-screen structure and stable selectors as a machine-readable
manifest for cozy video and demo script generation.

### Initial Tasks

- [x] Add runtime configuration for demo assist, with default disabled.
- [x] Add a JSON manifest endpoint for enabled demo mode.
- [x] Include page, sections, forms, fields, actions, widgets, and recommended
      selectors.
- [x] Prefer semantic `data-textus-*` selectors in the manifest.
- [x] Redact or omit hidden sensitive values, session tokens, raw provider
      payloads, and confidential field values.
- [x] Add specs for disabled and enabled behavior.

### Expected Output

- Demo tooling can map human demo steps to stable selectors without scraping
  brittle DOM paths.

---

## WU-10: Phase 28 Verification and Closure

Status: DONE

### Objective

Verify the implemented Phase 28 baseline and either close the phase or record
remaining work under independent future development items.

### Initial Tasks

- [x] Confirm phase and strategy documents match implemented behavior.
- [x] Run focused Web UI DSL / renderer / demo manifest specs.
- [x] Run `sbt --batch test`.
- [x] Run `git diff --check`.
- [x] Record deferred work under the appropriate 9.x development item.

### Expected Output

- Phase 28 can close without hidden Web UI DSL implementation debt.

### Closure Result

- Phase 28 is closed.
- WU-01 through WU-09 remain documented as complete and covered by executable
  specs.
- Deferred Material visual rendering, broader generated-page selector coverage,
  Web Island Architecture Runtime, API Gateway / public REST exposure policy,
  and production visual theme marketplace remain outside Phase 28.
