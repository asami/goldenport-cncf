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
2. WEB-02 (REST/Form API exposure) is implemented on top of the operation model.
3. WEB-03 (Web Descriptor) is defined after the exposure surface is clear.
4. WEB-04 (dashboard / console / manual) is defined from existing admin/meta
   capabilities.
5. WEB-05 (Executable Specifications and runtime hooks) is expanded alongside
   WEB-02 to WEB-04 and finalized last.

This order minimizes churn between the API surface, descriptor model, and
operator-facing web functions.

---

## WEB-01: Web Layer Scope and Canonical Design Surface

Status: ACTIVE

### Objective

Promote the Web Layer from journal notes into a canonical Phase 12 development
surface without over-committing to broad UI generation.

### Detailed Tasks

- [ ] Consolidate the Web Layer architecture from journal notes.
- [ ] Define CNCF Web Layer as an operation-centric integration surface.
- [ ] Separate operational web functions from business application UI generation.
- [ ] Define which journal notes become design/spec inputs for Phase 12.
- [ ] Record explicit non-goals for SPA hosting, wireframe DSL, and SDK work.

### Inputs

- `docs/journal/2026/04/web-application-integration-note.md`
- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/journal/2026/04/web-static-form-app-note.md`
- `docs/journal/2026/04/web-integration-spa.md`
- `docs/journal/2026/04/web-wireframe-dsl-note.md`

---

## WEB-02: REST/Form API Exposure

Status: PLANNED

### Objective

Define operation-centric web API exposure for Component / Service / Operation
without adding an intermediate API stub layer.

### Detailed Tasks

- [ ] Define canonical selector-to-path mapping.
- [ ] Define REST invocation request and response shape.
- [ ] Define Form API definition endpoint.
- [ ] Define Form API validation endpoint.
- [ ] Define validation and error response shape.
- [ ] Clarify relationship with existing CLI/meta projections.

### Inputs

- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-api-response-note.md`
- `docs/journal/2026/04/query-update-request-translation-rule-note.md`
- `docs/journal/2026/04/record-v3-http-form-path-notation-note.md`

---

## WEB-03: Web Descriptor Model

Status: PLANNED

### Objective

Define the Web Descriptor as the deployment/configuration surface for web
exposure, security, form behavior, traffic control, and application hosting.

### Detailed Tasks

- [ ] Define descriptor file location and loading precedence.
- [ ] Define operation exposure levels: public, protected, internal.
- [ ] Define authentication and authorization configuration keys.
- [ ] Define Form API enable/disable controls.
- [ ] Define application hosting entries without committing to a frontend framework.
- [ ] Define how descriptor values can be overridden by configuration.

### Inputs

- `docs/journal/2026/04/web-descriptor-note.md`
- `docs/journal/2026/04/web-descriptor-packaging-model-note.md`
- `docs/journal/2026/04/web-api-exposure-control-note.md`
- `docs/journal/2026/04/web-authentication-authorization-note.md`

---

## WEB-04: Dashboard / Management Console / Manual Baseline

Status: PLANNED

### Objective

Define the first operational web surface over existing CNCF runtime,
observability, and meta capabilities.

### Detailed Tasks

- [ ] Define dashboard minimum: components, services, operations, health, version.
- [ ] Define management console minimum: operation execution and result display.
- [ ] Define manual minimum: help, describe, schema, OpenAPI, MCP links.
- [ ] Define calltree/action history visibility from existing observability design.
- [ ] Define assembly warning visibility for dashboard/admin surfaces.
- [ ] Defer advanced visualizations unless required for the baseline.

### Inputs

- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/design/observability/calltree-runtime-result.md`
- `docs/design/assembly-descriptor.md`
- `docs/journal/2026/04/assembly-selection-and-observability-note.md`

---

## WEB-05: Executable Specifications and Minimal Runtime Hooks

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

- WEB-01 through WEB-05 are marked DONE.
- `phase-12.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
