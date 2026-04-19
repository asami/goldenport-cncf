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

The Phase 12 work has already completed the scope/design, descriptor, and
Dashboard baseline. The current work order is:

1. Finish the remaining Form feature work by auditing the existing WEB-02,
   WEB-07, and WEB-08 checklist items.
2. Complete the Management Console on top of those Form features.
3. Move `textus-sample-app` toward a Static Form Web App and use it to find the
   next gaps.

This order keeps the current residual work first. Dashboard is treated as done;
Static Form Web App work is the next validation driver after Form features and
the Management Console are closed enough.

Practical validation:

- Develop Phase 12 alongside `textus-sample-app`.
- Use `textus-sample-app` as the first concrete consumer of the completed Form
  feature set, then as the Static Form Web App validation driver.
- Keep CNCF-side changes generic; sample-specific behavior belongs in
  `textus-sample-app`.
- Promote only proven common mechanisms from the sample app back into CNCF.
- Dashboard-driven validation is complete for this phase slice. Do not reopen
  Dashboard work unless a Form or Management Console change breaks the
  dashboard contract.
- Structured Query field name resolution is defined in
  `docs/design/query-field-resolution.md`. Generated entity/view search paths
  must use this contract; full-text search, embedding search, and aggregate
  search are separate future design items.

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
- [x] Establish Operation authorization as the canonical selector policy and
      keep WebDescriptor authorization as a Web ingress override/supplement.
- [x] Verify admin anonymous policy through the Subsystem dispatch checkpoint,
      not only through Web/Form routes.

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

Status: DONE

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

## WEB-07A: Current Form Feature Audit

Status: DONE

### Objective

Close the remaining Form feature work before moving deeper into Management
Console completion or Static Form Web App validation.

This section audits the work already started across WEB-02, WEB-07, and WEB-08.
It is not a new feature area; it is the current residual stack for Form
functionality.

### Already Closed

- [x] Separate HTML FORM paths under `/form` from JSON Form API paths under
      `/form-api`.
- [x] Generate operation HTML forms from Component / Service / Operation
      metadata.
- [x] Serve JSON form definitions for operation forms.
- [x] Serve JSON form definitions for admin entity, data, view, and aggregate
      surfaces.
- [x] Validate HTML operation form input before dispatch.
- [x] Validate JSON Form API input without dispatch.
- [x] Redisplay operation forms with submitted values and validation messages.
- [x] Use `WebSchemaResolver.ResolvedWebSchema` as the shared schema between
      HTML forms and JSON Form API.
- [x] Merge CML-derived Schema / ParameterDefinition Web hints with
      WebDescriptor presentation overrides.
- [x] Preserve schema field order as the default form field order.
- [x] Support basic field controls: hidden, readonly, required, select,
      textarea, placeholder, help, and validation hints.
- [x] Support operation result property expansion for result pages.
- [x] Support result widgets: result view, result table, property list, and
      error panel.
- [x] Support action-link result widgets in both preferred `textus:action-link`
      notation and compatible `textus-action-link` notation.
- [x] Extract operation result metadata for static pages: `result.id`,
      `result.outcome`, `result.message`, and `result.action.*`.
- [x] Treat Command Form submission as asynchronous by default and expose
      JobId-based `result.job.*` / `result.action.await.*` metadata for
      explicit result waiting.
- [x] Add Form job await route
      `/form/{component}/{service}/{operation}/jobs/{jobId}/await`.
- [x] Generate a conservative `result.action.detail.*` link from command
      `result.id` for `post-*`, `create-*`, and `update-*` operations.
- [x] Support paging properties and continuation-backed paging for form
      results.
- [x] Support optional/required/disabled total count policy for admin list
      pages.
- [x] Support descriptor-provided result templates as supplemental Form
      configuration.
- [x] Support static result page convention for operation result pages:
      operation-specific `xxx__200.html`, `xxx__success.html`,
      `xxx__{status}.html`, `xxx__error.html`, and common `__200.html`,
      `__success.html`, `__{status}.html`, `__error.html`.
- [x] Prefer exact status static result pages over success/error aliases.
- [x] Support common static error pages for Web HTML errors through
      app-specific and global `__{status}.html` / `__error.html`.
- [x] Preserve standard hidden form context for validation redisplay and result
      templates while stripping it before Operation dispatch.
- [x] Confirm admin entity/data validation uses the same schema merge and
      validation behavior as operation forms.
- [x] Confirm admin entity/data validation failures redisplay the submitted
      edit/new form with field-level messages.
- [x] Confirm admin entity create and update Form API definitions are separated
      when their field sets differ.
- [x] Confirm admin entity create Form API uses `create` view fields and admin
      entity update Form API uses `detail` view fields.
- [x] Confirm admin data create and update Form API definitions are separated
      by route and usage mode while sharing the current `resolveData` field set.
- [x] Confirm admin entity create can omit `id` from the visible/form-api field
      set and still persist through server-side id completion.
- [x] Confirm admin entity update validation is scoped to `detail` view fields:
      fields outside `detail` are not required, while required fields inside
      `detail` still fail validation before dispatch.
- [x] Confirm `textus-sample-app` executes the separated create/update admin
      entity Form API paths with the published CNCF runtime.
- [x] Confirm view and aggregate form-definition APIs expose enough schema for
      their read/operation surfaces.
- [x] Confirm view and aggregate generic Form API definitions are read-oriented:
      they expose GET list/detail navigation metadata and do not expose generic
      create/update submit actions.
- [x] Confirm operation result pages and admin create/update result pages expose
      the same core property model where applicable.
- [x] Confirm static result page convention is applied only to ordinary
      operation Form paths and does not accidentally change admin console
      built-in flows.
- [x] Confirm Form API and HTML FORM behavior stay aligned when WebDescriptor
      narrows validation hints.
- [x] Add or update executable specifications for any gaps found in this audit.
- [x] Update `docs/design/management-console.md` and
      `docs/notes/cncf-web-static-form-app-contract.md` when the audit changes
      the contract.

### Completion Criteria

- All current Form behavior needed by Management Console CRUD/read/operation
  flows is covered by executable specifications.
- The remaining behavior needed only by Static Form Web App is explicitly
  listed as Static Form Web App work rather than mixed into the Form foundation.
- Dashboard remains green without reopening Dashboard scope.

---

## WEB-08: Management Console CRUD Flow

Status: ACTIVE

### Objective

Use the Management Console as the practical driver for Form Web application
foundation features: list, detail, edit/update, and create flows.

These capabilities are required for Form Web applications in general, not only
for the built-in admin console.

### Detailed Tasks

- [x] Define common Form Web route model for collection/list, detail, edit, update, new, and create pages.
- [x] Define separate Management Console entry points for entity, data, aggregate, and view resources.
- [x] Define entity type entry points such as `/web/{component}/admin/entities/{entityName}`.
- [x] Define list page contract with paging as a first-class capability.
- [x] Define list-to-detail navigation contract.
- [x] Define list-to-edit navigation and update submission contract.
- [x] Define list-to-new navigation and create submission contract.
- [x] Decide how Form Web pages bind to Component / Service / Operation metadata.
- [x] Decide how result properties and continuation properties are passed between pages.
- [x] Define optimistic update or stale-form handling requirements.
- [x] Define validation error rendering and redisplay behavior for edit/create forms.
- [x] Implement the first Management Console CRUD page for entity resources.
- [x] Protect the entity list/detail/edit/new/update/create flow with executable specifications.
- [x] Add executable specifications that verify entity update/create POST reaches `EntityCollection.putRecord`.
- [x] Add executable specifications that verify entity create/update Form API
      field sets follow `create` and `detail` view fields respectively.
- [x] Add executable specifications that verify id-less entity create forms are
      accepted and completed by the server before `EntityCollection.putRecord`.
- [x] Define `OperationMode` (`production`, `demo`, `develop`, `test`) as the
      runtime operational policy separate from `RunMode`.
- [x] Define `OperationMode` policy as RunMode-independent so command, server,
      client, script, and server-emulator roots use the same admin policy for
      the same resolved configuration.
- [x] Gate anonymous Management Console access by operation mode and
      `textus.web.develop.anonymous-admin`: allowed only in `develop`/`test`
      when the switch is true, denied in `production`/`demo`.
- [x] Add framework-enforced Web authorization policy fields for
      component/service/operation selectors: `operationModes` and
      `allowAnonymous`.
- [x] Cover admin Form API and HTML admin route authorization with executable
      specifications for anonymous and explicit-principal requests.
- [x] Add executable specifications that verify entity update validation uses
      `detail` view fields and redisplays the same field set on validation
      error.
- [x] Add executable specifications that verify data update Form API uses an
      id-scoped update route and returns the inferred/descriptor-backed data
      field set.
- [x] Add executable specifications that verify view and aggregate Form API
      definitions stay read-oriented and mutation remains operation-backed.
- [x] Extend data management beyond the entry page to data list/detail/edit/new/update/create against `DataStore`.
- [x] Add executable specifications that verify data update/create POST reaches `DataStore`.
- [x] Extend aggregate management beyond the entry page to aggregate definition/read pages where an aggregate collection query contract is available.
- [x] Extend view management beyond the entry page to view list/read pages where a view browser/query contract is available.
- [x] Define aggregate create/command/update flow separately from read baseline.

### Current Scope Status

- Entity CRUD baseline: implemented for list, detail, edit, new, update POST,
  and create POST against `EntityCollection`. Form API definitions are split:
  `/form-api/{component}/admin/entities/{entity}` describes create, and
  `/form-api/{component}/admin/entities/{entity}/{id}/update` describes update.
- Data management: implemented for list, detail, edit, new, update POST, and
  create POST against `DataStore`. Form API definitions are split:
  `/form-api/{component}/admin/data/{data}` describes create, and
  `/form-api/{component}/admin/data/{data}/{id}/update` describes update.
- Aggregate management: implemented for definition detail and read result
  against `AggregateSpace` / `AggregateCollection`; create/command/update flow is
  operation-backed and action rendering is available when matching operations
  are exposed. Aggregate create is operation-backed and available when the
  aggregate exposes an Aggregate Root creation operation.
- View management: implemented for definition detail and read result against
  `ViewSpace` / `Browser`.

### Aggregate Create / Command / Update Policy

- Aggregate create is available only when the aggregate exposes an Aggregate
  Root creation operation. The Management Console calls that operation; it does
  not create aggregate state directly.
- Aggregate update is available only when the aggregate exposes an update or
  command operation. The Management Console calls that operation; it does not
  patch aggregate state directly.
- Application logic is expected to use Aggregate Root capabilities: operations
  are external entry points, and their implementations call the Aggregate Root
  `create` function or domain methods.
- Aggregate read may call an aggregate read operation when one is exposed. If no
  read operation is available, the read baseline may use
  `AggregateSpace` / `AggregateCollection` metadata and query results.
- If the aggregate does not expose the corresponding operation, the UI must not
  render the create/update/command action.

### Remaining Required Work

- [x] Implement aggregate operation discovery for read/create/update-command actions.
- [x] Render aggregate create/update/command actions only when matching operations are exposed.
- [x] Route aggregate create/update/command submissions through the discovered
      Component / Service / Operation entry point.
- [x] Add executable specifications for aggregate operation-backed action
      discovery and rendering.
- [x] Add executable specifications for aggregate operation-backed create/update-command
      submission routing.
- [x] Add executable specifications for successful aggregate operation
      execution with an HTTP ingress-capable fixture.
- [x] Run a Management Console completion pass after the Form feature audit:
      entity, data, aggregate, and view routes must still work with the updated
      validation/result behavior.
- [x] Confirm the built-in admin result pages and descriptor-controlled admin
      transitions remain consistent after adding the common result property
      rows.
- [x] Define the standard hidden Form context needed for plain HTML FORM app
      behavior: origin/success/error links, paging state, search state,
      continuation id, optimistic update token, and security token placeholders.
- [x] Render standard hidden Form context fields on admin edit/new and
      operation forms where the values are present.
- [x] Preserve standard hidden Form context values through validation error
      redisplay and result rendering.
- [x] Add executable specifications for list/detail/edit/update/list-back flows
      that preserve paging/search context through hidden fields.
- [x] Confirm aggregate create/update/command action rendering is still clear
      enough for the built-in admin console and does not require Static Form Web
      App conventions.
- [x] Confirm view read/list pages are acceptable as the read-only baseline
      before moving to Static Form Web App validation.
- [x] Update `docs/design/management-console.md` if the completion pass changes
      the Management Console contract.

### Inputs

- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-operational-management-note.md`
- `textus-sample-app` as the practical validation driver.

---

## WEB-09: Static Form Web App Validation with textus-sample-app

Status: PLANNED

### Objective

After the current Form feature audit and Management Console completion work,
move `textus-sample-app` toward a Static Form Web App and use it to discover
the next platform gaps.

This is not the current active work. It starts after WEB-07A and WEB-08 are
closed enough for the sample app to reuse the Form foundation instead of driving
unfinished Management Console work.

### Detailed Tasks

- [ ] Build the notice-board sample primarily from CML metadata and static HTML
      files.
- [ ] Prefer static result page conventions before descriptor transition
      settings.
- [ ] Add operation-specific result pages such as `post-notice__200.html`
      and `search-notices__200.html`.
- [ ] Add common result/error pages such as `__400.html`, `__500.html`, and
      `__error.html`.
- [ ] Use Textus widgets for result view, table, property list, error display,
      action links, and paging. Prefer `textus:xxx` notation while keeping
      `textus-xxx` compatible.
- [ ] Identify which behavior is missing from the CML+HTML model.
- [ ] Promote only generic missing behavior back into CNCF; keep
      sample-specific pages in `textus-sample-app`.
- [ ] Record gaps as future Form widget, convention, or descriptor candidates.

### Inputs

- `textus-sample-app`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/journal/2026/04/web-static-form-app-note.md`

---

## Deferred / Next Phase Candidates

- SPA hosting and packaging.
- Wireframe DSL and UI generation.
- Public JavaScript SDK.
- Advanced dashboard visualization and SVG rendering.
- External API gateway integration.
- Full-text search support as a separate search planning layer from structured
  Query field resolution. This should cover searchable view/field selection,
  tokenizer/analyzer policy, locale handling, ranking, highlighting, and
  backend capability dispatch such as in-memory contains, SQL FTS, or an
  external search engine.
- Embedding / semantic search support as a future search backend. This should
  cover embedding target selection from CML/View metadata, vector index
  lifecycle, update synchronization, similarity threshold/ranking, and
  interaction with structured Query filters.

---

## Completion Check

Phase 12 is complete when:

- WEB-01 through WEB-09 are marked DONE, or explicitly deferred to a later
  phase.
- `phase-12.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
