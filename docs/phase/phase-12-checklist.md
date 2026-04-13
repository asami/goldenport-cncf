# Phase 12 — Web Layer Checklist

This document contains detailed task tracking and execution decisions
for Phase 12.

It complements the summary-level phase document (`phase-12.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-12.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]`
  in the phase document.

---

## Status Semantics

- ACTIVE: currently being worked on (only one item may be ACTIVE).
- SUSPENDED: intentionally paused with a clear resume point.
- PLANNED: planned but not started.
- DONE: handling within this phase is complete.

---

## Recommended Work Procedure

Phase 12 implementation proceeds in this order:

1. WEB-01 (scope and design surface) must be fixed first.
2. WEB-02 (REST/Form API exposure and Static Form App mechanism) is
   implemented on top of the operation model.
3. WEB-03 (Web Descriptor) is defined after the exposure surface is clear.
4. WEB-04 (read-only dashboard) is defined as the first operational web app on
   top of the Static Form App mechanism.
5. WEB-05 (management console) and WEB-06 (manual/reference) are defined as
   separate web apps on the same mechanism.
6. WEB-07 (Executable Specifications and runtime hooks) is expanded alongside
   WEB-02 through WEB-06 and finalized last.

This order minimizes churn between the API surface, descriptor model, and
operator-facing web functions.

Practical validation:

- Develop Phase 12 alongside `textus-sample-app`.
- Use `textus-sample-app` as the first concrete consumer of the Static Form App
  mechanism.
- Keep CNCF-side changes generic; sample-specific behavior belongs in
  `textus-sample-app`.
- Promote only proven common mechanisms from the sample app back into CNCF.
- Enter the implementation through the Dashboard use case, but do not implement
  Dashboard as a special server route. The Dashboard path is used to drive the
  shared Static Form App and Form API mechanism first, then the read-only
  Dashboard is placed on that mechanism under WEB-04.
- Use the notice-board sample as the dashboard validation fixture: the baseline
  must be able to show runtime health/version plus Component / Service /
  Operation metadata for the sample app before adding management-console or
  manual behavior.

---

## WEB-01: Web Layer Scope and Canonical Design Surface

Status: DONE

### Objective

Promote the Web Layer from journal notes into a canonical Phase 12 development
surface without over-committing to broad UI generation.

### Detailed Tasks

- [x] Consolidate the Web Layer architecture from journal notes.
- [x] Define CNCF Web Layer as an operation-centric integration surface.
- [x] Separate operational web functions from business application UI generation.
- [x] Define which journal notes become design/spec inputs for Phase 12.
- [x] Record explicit non-goals for SPA hosting, wireframe DSL, and SDK work.
- [x] Record the dashboard-first validation path with `textus-sample-app`
      without bypassing the WEB-02 Static Form App mechanism.

### Inputs

- `docs/notes/cncf-web-layer-phase-12.md`
- `docs/journal/2026/04/web-application-integration-note.md`
- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/journal/2026/04/web-static-form-app-note.md`
- `docs/journal/2026/04/web-integration-spa.md`
- `docs/journal/2026/04/web-wireframe-dsl-note.md`

---

## WEB-02: REST/Form API and Static Form App Mechanism

Status: DONE

### Objective

Define operation-centric web API exposure and the Static Form App mechanism for
placing CNCF web apps on top of Component / Service / Operation metadata
without adding an intermediate API stub layer.

The application shape is Static Form App.
Internally, it uses Form API for schema-driven form definition and validation,
then invokes operations through the REST execution backbone.

### Detailed Tasks

- [x] Define canonical selector-to-path mapping.
- [x] Define REST invocation request and response shape.
- [x] Define Form API definition endpoint.
- [x] Define Form API validation endpoint.
- [x] Define Static Form App routing and page resolution contract.
- [x] Define how a Static Form App uses Form API internally and invokes operations through the REST backbone.
- [x] Define the shared mechanism used by Dashboard, Management Console, and Manual web apps.
- [x] Define validation and error response shape.
- [x] Clarify relationship with existing CLI/meta projections.
- [x] Add the first runtime hook for a read-only Static Form App entry point
      that can later host Dashboard, Management Console, and Manual without
      hard-coding dashboard-specific routes.

### Inputs

- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-static-form-app-note.md`
- `docs/journal/2026/04/web-api-response-note.md`
- `docs/journal/2026/04/query-update-request-translation-rule-note.md`
- `docs/journal/2026/04/record-v3-http-form-path-notation-note.md`

---

## WEB-03: Web Descriptor Model

Status: DONE

### Objective

Define the Web Descriptor as the deployment/configuration surface for web
exposure, security, form behavior, traffic control, and application hosting.

### Detailed Tasks

- [x] Define descriptor file location and loading precedence.
- [x] Define operation exposure levels: public, protected, internal.
- [x] Define authentication configuration keys.
- [x] Define Form API enable/disable controls.
- [x] Define application hosting entries without committing to a frontend framework.
- [x] Define how descriptor values can be overridden by configuration.
- [x] Define authorization configuration keys.
- [x] Decide minimum runtime loading hook for descriptor discovery.

### Inputs

- `docs/journal/2026/04/web-descriptor-note.md`
- `docs/journal/2026/04/web-descriptor-packaging-model-note.md`
- `docs/journal/2026/04/web-api-exposure-control-note.md`
- `docs/journal/2026/04/web-authentication-authorization-note.md`
- `docs/notes/cncf-web-descriptor-minimum-schema.md`

---

## WEB-04: Read-Only Dashboard Baseline

Status: DONE

### Objective

Define the first operational web app as a read-only dashboard over existing
CNCF runtime, observability, and meta capabilities.

### Detailed Tasks

- [x] Define read-only dashboard minimum: components, services, operations, health, version.
- [x] Define dashboard as a Static Form App instance with no mutation actions.
- [x] Validate the minimum Dashboard against `textus-sample-app` first.
- [x] Add Dashboard state contract tests for subsystem and component scopes.
- [x] Define calltree/action history visibility from existing observability design.
- [x] Define assembly warning visibility for dashboard/admin surfaces.
- [x] Define navigation links from dashboard to management console and manual without inline control actions.
- [x] Defer advanced visualizations unless required for the baseline.

### Inputs

- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/design/observability/calltree-runtime-result.md`
- `docs/design/assembly-descriptor.md`
- `docs/journal/2026/04/assembly-selection-and-observability-note.md`

---

## WEB-05: Management Console Baseline

Status: ACTIVE

### Objective

Define the active control web app separately from the read-only Dashboard.

### Detailed Tasks

- [x] Surface resolved Web Descriptor summary from system and component admin pages.
- [x] Define system admin as a configuration and operational-detail hub, not a dashboard clone.
- [x] Define component admin as component-scoped configuration and operation metadata view.
- [x] Define component admin managed-data entries: entity CRUD, data CRUD, aggregate CRUD, and view read.
- [x] Add read-only component entity administration page.
- [x] Add component data administration placeholder page.
- [x] Add read-only component aggregate administration page.
- [x] Add read-only component view administration page.
- [x] Define operation execution and result display.
- [x] Define console as a Static Form App instance that permits controlled operation execution.
- [x] Define resolved runtime configuration visibility and masking rules.
- [x] Define assembly report / warning drill-down links from admin pages.
- [x] Define execution history and calltree drill-down links from admin pages.
- [x] Define Web Descriptor drill-down or resolved descriptor JSON view.
- [x] Define job control entry points and minimum safety boundaries.
- [x] Define configuration mutation/view split and required authorization boundary.
- [x] Define links from dashboard/manual into console actions without making them inline dashboard/manual behavior.

### Inputs

- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-descriptor-note.md`
- `docs/notes/cncf-web-descriptor-minimum-schema.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/spec/admin-system-status.md`

---

## WEB-06: Manual / Reference Baseline

Status: PLANNED

### Objective

Define the self-documentation web app separately from Dashboard and Management
Console.

### Detailed Tasks

- [ ] Define manual minimum: help, describe, schema, OpenAPI, MCP links.
- [ ] Define manual as a Static Form App instance focused on read-only reference navigation.
- [ ] Define component/service/operation reference navigation.
- [ ] Define links from dashboard warnings and runtime objects into manual entries.
- [ ] Keep manual read-only and explanation-oriented.

### Inputs

- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/notes/doc-help-design.md`
- `docs/spec/output-format.md`

---

## WEB-07: Executable Specifications and Minimal Runtime Hooks

Status: PLANNED

### Objective

Protect the minimal Web Layer runtime path with executable specifications
before broad UI work begins.

### Detailed Tasks

- [ ] Add spec for selector-to-web-path mapping.
- [ ] Add spec for REST request-to-operation invocation mapping.
- [ ] Add spec for Form API definition projection from operation schema.
- [ ] Add spec for Web Descriptor exposure filtering.
- [ ] Add spec for protected/internal operation visibility.
- [ ] Add minimal runtime hook or adapter only after the contract is stable.

---

## Deferred / Next Phase Candidates

- SPA hosting and packaging.
- Wireframe DSL and UI generation.
- Public JavaScript SDK.
- Advanced dashboard visualization and SVG rendering.
- External API gateway integration.

---

## Completion Check

Phase 12 is complete when:

- WEB-01 through WEB-07 are marked DONE.
- `phase-12.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
