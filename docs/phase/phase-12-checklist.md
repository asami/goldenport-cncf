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
- [x] Define Web Descriptor drill-down with completed descriptor JSON and configured descriptor comparison.
- [x] Link component admin pages to `/web/{component}/admin/descriptor` and resolve component route placeholders there.
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

Status: DONE

### Objective

Define the self-documentation web app separately from Dashboard and Management
Console.

### Detailed Tasks

- [x] Define manual minimum: help, describe, schema, OpenAPI, MCP links.
- [x] Define manual as a Static Form App instance focused on read-only reference navigation.
- [x] Define component/service/operation reference navigation.
- [x] Define links from dashboard warnings and runtime objects into manual entries.
- [x] Keep manual read-only and explanation-oriented.

### Current Scope Status

- System manual entry point: `/web/system/manual`.
- Component manual entry point: `/web/{component}/manual`.
- Service manual entry point: `/web/{component}/manual/{service}`.
- Operation manual entry point: `/web/{component}/manual/{service}/{operation}`.
- OpenAPI JSON reference entry point: `/web/system/manual/openapi.json`.
- MCP endpoint reference link: `/mcp`.
- Manual pages render existing `HelpProjection`, `DescribeProjection`, and
  `SchemaProjection` data. The manual does not execute operations and does not
  render mutation forms.
- Dashboard state now exposes the canonical manual link for the current scope:
  `/web/system/manual` for subsystem scope and `/web/{component}/manual` for
  component scope.

### Inputs

- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/notes/doc-help-design.md`
- `docs/spec/output-format.md`

---

## WEB-07: Executable Specifications and Minimal Runtime Hooks

Status: DONE

### Objective

Protect the minimal Web Layer runtime path with executable specifications
before broad UI work begins.

### Detailed Tasks

- [x] Add spec for selector-to-web-path mapping.
- [x] Add spec for REST request-to-operation invocation mapping.
- [x] Add spec for Form API definition projection from operation schema.
- [x] Add spec for Web Descriptor exposure filtering.
- [x] Add spec for protected/internal operation visibility.
- [x] Add minimal runtime hook or adapter only after the contract is stable.

### Current Scope Status

- `StaticFormAppRendererSpec` fixes the canonical selector mapping from
  `component.service.operation` to Web HTML, JSON Form API, and REST operation
  paths.
- `StaticFormAppRendererSpec` fixes the Form API POST adapter contract: Web
  submission is converted into a canonical operation `HttpRequest` before
  crossing the `WebOperationDispatcher`.
- `StaticFormAppRendererSpec` fixes internal operation visibility: internal
  exposure is absent from HTML form rendering and returns `404` from JSON Form
  API definition routes.
- `StaticFormAppRendererSpec` fixes the minimal runtime hook path: operation
  adapter dispatch records HTML request, authorization, and DSL chokepoint
  dashboard metrics.
- `WebSchemaResolverSpec` and operation form definition specs fix the shared
  operation-schema-to-form-definition projection.
- `WebDescriptorSpec` fixes the exposure gate: public/protected selectors are
  form-visible by default, internal or unlisted selectors are not.

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

Status: DONE

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

### WEB-08 Closure

- Entity and data surfaces cover list, detail, edit/update, new/create, Form API
  definition, validation redisplay, hidden context preservation, and descriptor
  transition behavior.
- View and aggregate surfaces cover the read-oriented baseline. Aggregate
  mutation is intentionally operation-backed: the console discovers exposed
  create/update/command operations and routes submissions through the matching
  Component / Service / Operation entry point instead of mutating aggregate
  state directly.
- Authorization, operation mode policy, anonymous develop/test shortcuts, and
  explicit-principal production access are covered by executable
  specifications across admin HTML and Form API routes.
- The completion pass after the Static Form feature audit confirmed the
  Management Console does not depend on Static Form Web App result page
  conventions. Remaining refinements are future management-console polish, not
  WEB-08 blockers.

---

## WEB-09: Static Form Web App Validation with textus-sample-app

Status: DONE

### Objective

After the current Form feature audit and Management Console completion work,
move `textus-sample-app` toward a Static Form Web App and use it to discover
the next platform gaps.

This became the active validation driver after WEB-07A and WEB-08 were closed
enough for the sample app to reuse the Form foundation instead of driving
unfinished Management Console work.

### Detailed Tasks

- [x] Audit the current notice-board Static Form App shape and separate already
      covered static conventions from remaining widget/platform gaps.
- [x] Build the notice-board sample primarily from CML metadata and static HTML
      files.
- [x] Prefer static result page conventions before descriptor transition
      settings.
- [x] Add operation-specific result pages such as `post-notice__200.html`
      and `search-notices__200.html`.
- [x] Add common result/error pages such as `__400.html`, `__500.html`, and
      `__error.html`.
- [x] Use Textus widgets for result view, table, property list, error display,
      action links, and paging. Prefer `textus:xxx` notation while keeping
      `textus-xxx` compatible.
- [x] Identify which behavior is missing from the CML+HTML model.
- [x] Promote only generic missing behavior back into CNCF; keep
      sample-specific pages in `textus-sample-app`.
- [x] Record gaps as future Form widget, convention, or descriptor candidates.

### Current Audit

`textus-sample-app` already has the Static Form App page set needed for the
minimal notice-board flow:

- Operation result pages:
  - `config/post-notice__200.html`
  - `config/search-notices__200.html`
  - `config/get-notice__200.html`
- Common error/result pages:
  - `config/__400.html`
  - `config/__500.html`
  - `config/__error.html`
- CML metadata currently supplies the operation input shape, required fields,
  labels, placeholders, and the `Notice` summary/detail display basis.
- `config/web-descriptor.yaml` is still supplemental: it declares public
  exposure and the default `summary` view, but the ordinary post/search/get
  pages do not depend on descriptor transition rules.

The WEB-09 validation focused on the generic widget surface and app behavior:

- [x] Confirm `textus-result-view`, `textus-property-list`, and
  `textus:action-link` render clean final HTML in the sample app.
- [x] Confirm `textus-error-panel` renders through static error pages for
  Operation/runtime errors. Admission validation currently redisplays the
  operation form and does not use `__400.html`.
- [x] Confirm `textus-result-table` renders both table rows and paging controls
  for operation result objects.
- [x] Confirm `textus-result-table view="summary"` obtains its column selection
  from CML-derived view/schema metadata rather than application reflection.
- [x] Verify total-count-free paging behavior for `search-notices__200.html`;
  page 1 renders a table and a `Next` link without total count, and the
  `/continue/{id}?page=2&pageSize=20` route returns the retained result set.
- [x] Align continuation result rendering with the original static result page
  convention. The current `/continue/{id}` route returns the retained result
  set and reuses the same static status/success template resolution as the
  original operation form result.
- [x] Add or verify total-count opt-in paging behavior; the table must
  work without total count and then with explicit total-count opt-in where the
  runtime supports it.
- [x] Confirm the async command path is usable with static pages:
  `postNotice -> JobId -> await -> result.id -> getNotice detail action`.
- [x] Record any remaining gaps as generic CNCF widget, convention, or descriptor
  candidates, not as sample-app-specific runtime behavior.

Runtime verification on Apr. 19, 2026:

- `postNotice` returns a static `post-notice__200.html` page with no unexpanded
  `textus:` or `textus-` tags. The primary action is an async `Check result`
  POST action.
- Awaiting the job returns the same static page with no unexpanded Textus tags.
  The primary action becomes an `Open detail` GET link synthesized from
  `result.id`.
- The synthesized detail link opens `get-notice__200.html` and renders the
  created notice body through `textus-result-view`.
- `searchNotices` opens `search-notices__200.html` with no unexpanded Textus
  tags, and the paging control is rendered without total count.
- `textus-result-table source="result.body" view="summary"` now renders table
  rows for the current search result object shape. The table widget accepts
  operation result objects such as `{data: [...]}` as well as direct arrays, and
  column lookup can resolve `result.body.data` CML/view metadata from a
  `source="result.body"` widget.
- Total-count-free paging was verified with 21 notices addressed to the same
  recipient. The first search page rendered table rows, omitted total count,
  and emitted a `Next` link to `/continue/{id}?page=2&pageSize=20`. The
  continuation route returned the retained result set and showed page 2 context.
- Continuation rendering now preserves the same static result template
  convention across pages. The retained result set still supplies the page data,
  while the response body is rendered through the original operation's
  `xxx__200.html` / `xxx__success.html` / common static fallback path.
- Explicit total-count paging now uses only operation response fields that
  actually represent total count, such as `total_count` / `totalCount`.
  `fetched_count` remains a fetched-row count and does not drive last-page
  navigation. When a form posts `paging.includeTotal=true`, continuation links
  retain `includeTotal=true` across pages.
- Admission validation failure for `postNotice` returns the Bootstrap operation
  form with field-level errors and submitted values preserved. It does not use
  the static `__400.html` common error page, so the common error page convention
  should be verified separately for Operation/runtime errors.
- Static error page rendering now verifies `textus-error-panel source="result"`
  for both Operation failure pages and Web HTML error pages. The widget expands
  into final HTML and leaves no `textus-error-panel` tag in the response.
- The async command path was verified against `textus-sample-app` after
  publishing the current CNCF snapshot and refreshing the runtime classpath.
  `postNotice` returned a static `Notice posted` page with a `Check result`
  POST action and job id, the await page exposed `result.id` and an `Open
  detail` link, and the generated `getNotice` detail page rendered the created
  notice title/content without unexpanded Textus widget tags.

WEB-09 closure:

- The minimal notice-board sample now works as a Static Form Web App based on
  CML metadata plus static HTML result/error pages.
- Generic gaps found during validation were promoted into CNCF: result widgets,
  action links, static status/error page conventions, continuation rendering,
  CML-derived table columns, total-count-free paging, total-count opt-in
  propagation, and async command result/detail navigation.
- Remaining work is not a WEB-09 blocker. Broader Static Form App refinement,
  admin console completion, richer search, and embedding support are tracked as
  next-phase candidates or subsequent WEB-10 work.

### Inputs

- `textus-sample-app`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/journal/2026/04/web-static-form-app-note.md`

---

## WEB-10: Bootstrap 5 UI Polish

Status: DONE

### Objective

Polish the built-in Web pages so the Phase 12 Web foundation looks like a
normal responsive Bootstrap 5 business/admin application.

This work is visual and structural polish. It must not change the runtime data
contracts established by Dashboard, Manual, Management Console, Form API, and
Static Form App.

### Detailed Tasks

- [x] Apply the Bootstrap 5 UI polish design to built-in Web pages.
- [x] Polish Management Console list/detail/edit/new pages with Bootstrap
      tables, forms, action areas, and validation feedback.
      - [x] First pass for entity admin list/detail/edit/new pages:
            card layout, action buttons, hover tables, and form action areas.
      - [x] Apply the same polish pass to data, aggregate, and view admin
            pages where the page type supports equivalent controls.
      - [x] Polish empty, warning, error, and validation feedback surfaces with
            Bootstrap alert/empty-state conventions.
- [x] Polish Manual/reference pages with Bootstrap navigation, cards/sections,
      schema tables, and code/path presentation.
- [x] Polish Dashboard cards, tables, tabs, badges, and metric layout while
      keeping the existing dashboard state contract.
- [x] Confirm local Bootstrap assets remain the only baseline dependency.
- [x] Confirm responsive behavior on desktop, tablet, and smartphone widths.
- [x] Add executable or screenshot-style checks where practical for non-broken
      Bootstrap rendering.

### Closure

WEB-10 is complete as the Phase 12 first-pass Bootstrap polish scope.

Completed scope:

- built-in Management Console list/detail/edit/new pages use Bootstrap cards,
  responsive tables, grouped actions, form action areas, and feedback surfaces.
- Manual/reference pages use Bootstrap navigation and card sections while
  remaining read-only.
- Dashboard pages use Bootstrap cards, badges, responsive tables, and the
  existing one-second dashboard state refresh contract.
- local Bootstrap assets remain the only baseline dependency.
- executable checks cover local asset usage, viewport metadata, no CDN
  references, and responsive Bootstrap structure.

Deferred scope:

- reusable Textus card/layout/feedback widgets are tracked by WEB-11.
- richer theme customization, visual identity, and screenshot-level responsive
  regression checks are future polish work recorded in
  `docs/journal/2026/04/web-bootstrap-polish-quality-future-note.md`.

### Inputs

- `docs/design/web-layer.md`
- `docs/spec/textus-widget.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/notes/cncf-web-layer-phase-12.md`
- `textus-sample-app`

---

## WEB-11: Textus Widget Set Expansion

Status: DONE

### Objective

Expand the Textus widget set beyond the Phase 12 baseline widgets so Static
Form App pages can render readable business/admin screens without control
structures or custom Web framework code.

Card widgets are the first implementation focus, but the design also includes
layout, navigation, feedback, content, and form-helper widgets.

### Detailed Tasks

- [x] Add executable specifications for the general Textus widget rendering
      contract.
- [x] Implement `textus:record-card` / `textus-record-card`.
- [x] Implement `textus:card-list` / `textus-card-list`.
- [x] Reuse existing result-table paging metadata for `card-list`.
- [x] Extract `textus:pagination` so table/card/custom pages can share paging
      metadata and rendering.
- [x] Implement `textus:summary-card` for dashboard/admin summary use.
- [x] Add feedback widgets such as `textus:alert` and `textus:empty-state`
      when card/table result pages need consistent messaging.
- [x] Keep `textus:xxx` as the preferred notation and `textus-xxx` as the
      HTML-compatible notation.
- [x] Validate the card-list path with `textus-sample-app` notice search
      results in addition to the current table view.

### Closure

WEB-11 is complete as the Phase 12 first-pass Textus widget expansion scope.

Completed scope:

- server-side widget expansion supports the preferred `textus:xxx` notation and
  the HTML-compatible `textus-xxx` notation.
- action/form-helper widgets cover action links, action forms, hidden context,
  and page-context-preserving POST actions.
- result widgets cover result view, result table, property list, error panel,
  and standalone pagination.
- card widgets cover record cards, card lists, and summary cards.
- feedback widgets cover alerts and empty states.
- `textus-sample-app` notice search validates card-list usage alongside the
  existing table view.

Deferred scope:

- richer layout/navigation/content widgets such as section, grid, breadcrumb,
  nav-list, status-badge, markdown, and action-card are future widget work.
- broader visual theme and screenshot-regression polish remains outside the
  Phase 12 first-pass widget scope.

### Candidate Widget Vocabulary

- Card: `textus:card`, `textus:record-card`, `textus:card-list`,
  `textus:summary-card`, `textus:action-card`.
- Layout: `textus:section`, `textus:grid`.
- Navigation: `textus:breadcrumb`, `textus:nav-list`, `textus:pagination`.
- Feedback: `textus:alert`, `textus:empty-state`, `textus:status-badge`.
- Content: `textus:description-list`, `textus:markdown`.
- Form helpers: `textus:form-errors`, `textus:hidden-context`.

### Inputs

- `docs/notes/web-textus-widget-design.md`
- `docs/spec/textus-widget.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/journal/2026/04/web-bootstrap-card-widget-note.md`
- `textus-sample-app`

---

## WEB-12: Web App Packaging And Deployment

Status: DONE

### Objective

Define and implement how a Static Form Web App is packaged, discovered, and
deployed with a Component/Subystem application.

The current `textus-sample-app` uses `config/` as the practical Web app root:
`config/web-descriptor.yaml` supplies the Web Descriptor and the same directory
contains static result templates such as `post-notice__200.html`. This works
as an early validation shape, but it is not a compatibility requirement. Phase
12 should move Web app packaging to an explicit `/web` layout before Static
Form Web App plus Island Architecture becomes a normal application delivery
path.

Canonical package placement is `/web` inside both CAR and SAR. CAR `/web`
contains Component-local Web app resources. SAR `/web` contains Subsystem-level
Web app resources and composition assets.

For a CAR, packaged `/web/{webAppName}/...` resources are mounted as
`/web/{componentName}/{webAppName}/...` at runtime.
SAR configuration, including the implicit SAR created for CAR-only execution,
may alias that canonical route to `/web/{webAppName}/...` or `/web/...` for a
selected default Web app.

A typical SAR `/web` application is a subsystem portal or composition page that
links to Component-local Web apps. This is separate from aliasing: the SAR app
owns the portal route, while Component apps keep their component-scoped routes.

### Design Direction

- Static Form Web App remains the baseline: server-rendered HTML, generated
  forms, static result templates, and Textus widgets.
- Island Architecture is supported by allowing local, scoped JS/CSS assets to
  be packaged beside static pages. Islands are optional progressive enhancement
  and must not replace Operation/Form API contracts.
- Web app packaging must not require a SPA framework or application-wide client
  router.
- The packaging model must work for development projects and for packaged
  component/subsystem archives.

### Detailed Tasks

- [x] Define the canonical `/web` Web app root layout for CAR and SAR.
      Baseline shape:
      - `/web/web-descriptor.yaml`
      - `/web/{webAppName}/index.html`
      - `/web/{webAppName}/*.html` for route-local result templates
      - `/web/{webAppName}/assets/**` for app-local CSS/JS/images
      - `/web/{webAppName}/islands/**` for scoped island scripts when needed
      - `/web/*.html` for component/subsystem common fallback templates
      - `/web/assets/**` for component/subsystem common assets
- [x] Define the Web Descriptor app/route vocabulary.
      Baseline shape:
      - `web.apps[].name`
      - `web.apps[].kind`
      - `web.apps[].root`
      - `web.apps[].route`
      - `web.routes[].path`
      - `web.routes[].target.component`
      - `web.routes[].target.app`
- [x] Define the mount rule:
      - CAR `/web/{webAppName}/...` becomes
        `/web/{componentName}/{webAppName}/...`.
      - SAR `/web/{webAppName}/...` is mounted under the subsystem/system Web
        scope according to the SAR Web Descriptor.
- [x] Define the SAR portal/composition pattern:
      - SAR `/web/{webAppName}/...` may render navigation/cards to Component
        Web apps.
      - Component Web apps remain mounted at
        `/web/{componentName}/{webAppName}/...` unless explicitly aliased.
      - Portal links must not change Component ownership, authorization,
        templates, or assets.
- [x] Define SAR Web route aliases:
      - map `/web/{componentName}/{webAppName}/...` to
        `/web/{webAppName}/...`.
      - map one selected default Web app to `/web/...`.
      - support the same alias behavior for an implicit SAR created from a
        single CAR.
      - reject or require explicit resolution for conflicting aliases.
      Explicit descriptor aliases and implicit SAR convenience aliases are
      implemented when the target is unambiguous by single component or a unique
      component/Web-app name match.
- [x] Migrate the current `config/web-descriptor.yaml` and `config/*.html`
      validation shape to the canonical `/web` layout.
      - Do not preserve `config/` as a packaging compatibility contract.
      - Package to CAR/SAR `/web` for distributable applications.
      - Prefer a product-facing `textus` naming convention over `cncf` naming.
- [x] Define discovery precedence for Web app roots:
      explicit `textus.web.descriptor`, project `.textus/config.*`, packaged
      component/subsystem metadata, and CAR/SAR `/web` roots.
- [x] Define how Web app assets are served:
      - framework assets under `/web/assets/...`
      - app-local CAR assets under
        `/web/{componentName}/{webAppName}/assets/...`.
      - Static HTML should link framework assets through `/web/assets/...` and
        app-local assets through the canonical
        `/web/{componentName}/{webAppName}/assets/...` route, even when the page
        is reached through an SAR alias or implicit `/web` route.
- [x] Define how static Web app HTML is served:
      - CAR `/web/{webAppName}/index.html` is available at
        `/web/{componentName}/{webAppName}`.
      - CAR `/web/{webAppName}/{page}.html` is available at
        `/web/{componentName}/{webAppName}/{page}`.
- [x] Define template lookup precedence for route-local operation-specific
      templates, route-local common status templates, component/subsystem common
      templates, and descriptor-provided result templates.
- [x] Define how `/web` is included and discovered in CAR/SAR archives.
      CAR/SAR/ZIP archive `/web` is treated as a Web resource root, so
      descriptor, template, and app-local asset discovery use the same rules as
      filesystem Web roots.
- [x] Add executable specifications for descriptor/template discovery and
      Web app route/package mapping.
- [x] Update `textus-sample-app` to use the chosen canonical packaging layout,
      replacing the current `config/` Web app layout.
- [x] Validate deployment with `textus-sample-app`:
      post/search/get result templates, common error pages, public exposure,
      admin policy, and completed descriptor visibility.

### Closure

WEB-12 is complete as the Phase 12 first-pass Web app packaging and deployment
scope.

Completed scope:

- canonical CAR/SAR `/web` layout is specified in `docs/design/web-layer.md`.
- Web Descriptor app/route vocabulary supports omitted convention values and a
  completed descriptor view.
- Component Web apps are mounted under `/web/{component}/{webApp}`.
- SAR routes can alias component Web apps to `/web/{webApp}` or `/web` without
  changing ownership, authorization, templates, or assets.
- filesystem and archive `/web` roots use the same descriptor/template/resource
  lookup rules.
- `textus-sample-app` uses the canonical `/web` layout for Static Form Web App
  validation.

Deferred scope:

- richer SPA hosting remains outside the Static Form Web App baseline.
- automatic CSS/JS dependency completion is tracked separately by WEB-15.
- additional packaged island runtime conventions may be added as concrete
  island use cases appear.

### Inputs

- `docs/design/web-layer.md`
- `docs/spec/textus-widget.md`
- `docs/spec/config-resolution.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `textus-sample-app`

---

## WEB-13: Web-Facing Shortid Support

Status: DONE

### Objective

Introduce a Web-facing `shortid` mechanism for entity references.

EntityId remains the canonical, globally meaningful identifier. Its practical
structure is:

- entity name
- entity-local id / entropy
- additional identity material when needed

For general use, the entity name part is required. However, when the entity
kind is already fixed by route, form, table, aggregate context, or descriptor,
the entity-local entropy is enough to identify the target entity inside that
entity collection.

Web URLs and visible form fields should prefer the shorter entity-local id
where that context is explicit, because full EntityId strings are too long for
human-facing links and screens.

### Design Direction

- `id` is the canonical EntityId and must remain available for internal
  persistence, references, APIs, diagnostics, and cross-entity contexts.
- External integration and generic framework logic should continue to pass the
  canonical EntityId. It carries major/minor, entity name, timestamp, and
  entropy, which are needed for scalable routing, partitioning, and possible
  distributed ordering semantics. In particular, timestamp is reserved as a
  future ordering signal for distributed algorithms and must not be discarded
  by generic processing.
- `shortid` is derived from the entity-local entropy portion of EntityId and is
  stored/exposed as a `SimpleEntity` identity attribute for Web-facing
  interaction and DB lookup.
- `shortid` is only a complete reference when the entity kind is known from
  context, such as a fixed entity admin route or a component-local Web screen.
- URL conventions should use `shortid` for entity-scoped routes such as
  `/web/{component}/admin/entities/{entity}/{shortid}`.
- Screens should avoid showing both `id` and `shortid` noisily. Admin/detail
  pages may expose the canonical `id` in diagnostics or advanced sections,
  while user-facing lists and links should prefer `shortid`.

### Detailed Tasks

- [x] Define the canonical EntityId entropy extraction contract.
      `EntityId` is defined in `simplemodeling-model` as a specialization of
      `UniversalId`. The canonical Web-facing shortid source is
      `EntityId.parts.entropy`. This keeps `id` as the full canonical
      identifier while allowing entity-scoped URLs and forms to use the
      entropy portion when the route already fixes the entity collection.
- [x] Add `shortid` generation at the entity creation chokepoint before the
      entity is stored in resident memory or DataStore.
      `shortid` is no longer a `NameAttributes` field. It belongs to
      `SimpleEntity` and is complemented from `EntityId.parts.entropy` by CNCF
      create/save defaults so DataStore-backed lookup can use a persisted
      column.
- [x] Define how `shortid` is represented in Schema / EntityRuntimeDescriptor
      so Web forms, lists, details, and admin pages can discover it.
      `ComponentFactory` enriches effective entity runtime Schema with a
      `shortid` column immediately after canonical `id` when missing. The
      column is marked as a system, readonly, non-required Web field.
- [x] Define uniqueness and lookup behavior for `(entityName, shortid)`.
      `shortid` is unique only within an entity collection. Framework code
      must resolve `(entityName, shortid)` to canonical `EntityId` before
      loading or updating an entity.
- [x] Add entity-scoped lookup APIs that accept `shortid` without requiring
      full EntityId when the entity kind is explicit.
      `EntityCollection.resolveEntityId` and `EntitySpace.resolveEntityId`
      accept either canonical EntityId or entity-scoped shortid. The current
      implementation resolves canonical id first, then falls back to the
      persisted `shortid` field or `EntityId.parts.entropy`.
- [x] Update Web/admin route generation to prefer `shortid` for entity-scoped
      visible URLs.
      Admin entity list/detail/edit links now use shortid when available while
      update POST resolves back to canonical id before persistence.
- [x] Define display policy for `id` vs `shortid` in list, detail, edit, and
      diagnostic/admin views.
      The route-facing identifier is shortid. Canonical `id` remains available
      in schema/display data for admin diagnostics and advanced inspection, but
      is not the preferred route token for entity-scoped Web links.
- [x] Add executable specifications covering generation, lookup, routing, and
      display policy.
- [x] Validate with `textus-sample-app` notice URLs and admin links.
      After publishing the framework locally, the sample app compiles and its
      admin notice list emits shortid detail/edit links. Detail access by
      shortid returns the expected notice record.

### Current Findings

- Canonical EntityId format is inherited from `UniversalId`:
  `major-minor-entity-{entityName}-{timestamp}-{entropy}`.
- `EntityId.parts.entropy` is already available after parsing or generation,
  so no string-splitting should be introduced in CNCF.
- Creation chokepoints to update next are `EntityStore.create`, generated
  entity construction paths, and admin `EntityCollection.putRecordSynced`
  create/update flows.
- `NameAttributes.shortid` was removed from the model surface. `shortid` now
  belongs to `SimpleEntity`, `SimpleEntityCreate`, `SimpleEntityUpdate`, and
  `SimpleEntityQuery`.
- `EntityRuntimeDescriptor.schema` now exposes `shortid` for Web/admin
  discovery without replacing canonical `id`.
- `shortid` must not become the default external identifier. It intentionally
  omits major/minor, entity name, and timestamp.
- Admin entity read/update now accept either canonical id or shortid. Update
  requests normalize the id back to canonical EntityId before
  `EntityCollection.putRecordSynced`.
- Lookup/display chokepoints are the admin operation records returned by
  `AdminComponent` and the route/link rendering in `StaticFormAppRenderer`.
- `EntityRuntimeDescriptor.schema` remains the right metadata carrier for Web
  forms/lists/details. If `shortid` needs additional semantics, extend Schema
  metadata rather than adding ad hoc reflection fields.

### Inputs

- `docs/design/web-layer.md`
- `docs/spec/textus-widget.md`
- `textus-sample-app`

---

## WEB-14: Application User Job Result UX

Status: DONE

### Objective

Define and implement a natural user-facing UX for async Command job results.

Command execution should remain asynchronous by default so CQRS-style flows do
not collapse into synchronous Form processing. Static Form Web App pages still
need a simple way to show the user what happened, how to wait when they care,
and how to navigate to the resulting resource when enough tracking information
is already known.

### Design Direction

- Form command execution returns a job id as the stable result handle.
- When available, the initial response may also carry tracking references such
  as entity id, shortid, aggregate id, operation name, or route-local return
  targets.
- The default UX should be asynchronous: show accepted/queued status and offer
  an explicit wait/refresh/result action.
- Synchronous command execution remains an explicit option for cases that need
  it, but it is not the default Form behavior.
- Result pages should work with Static Form conventions and Textus widgets, not
  require custom JavaScript.
- Applications may either embed job UX using Textus job widgets or link to a
  system-provided job page such as `/web/system/jobs/{jobId}` when they do not
  want to compose the job UX into the application page.
- Operation-local await routes and system job pages must both enforce the same
  owner-only job visibility policy. Admin/content-manager style capabilities
  may inspect broader job surfaces, but ordinary users see only jobs submitted
  by the same subject/session.
- Later island enhancement may provide live polling or richer progress display
  without changing the underlying job result contract.

### Detailed Tasks

- [x] Define the application-user job result model exposed to Web/Form pages.
- [x] Define result properties produced from async command execution:
      `jobId`, status, optional entity id/shortid, operation name, and result
      links.
- [x] Add a default accepted/result template convention for command POST
      responses.
- [x] Add widgets or action helpers for wait, refresh, result detail, and
      resource navigation.
- [x] Define how job result lookup is authorized for anonymous, logged-in, and
      admin users.
- [x] Define how failed jobs surface structured error information in Static
      Form pages.
- [x] Evaluate whether the most natural UX is an accepted page, result ticket,
      inline wait button, polling island, or a combination of those patterns.
- [x] Add executable specifications for async accepted result, wait action,
      completed result, failed result, and missing/unauthorized job result.
- [x] Validate with `textus-sample-app` post/search flows where applicable.

### Current Closure

WEB-14 has a first-pass implementation for application-user job result UX.

Completed scope:

- async Command results expose `result.job.id`, `result.job.status`,
  `result.job.href`, and `result.action.await.*`.
- `textus:job-ticket` / `textus-job-ticket` render an embeddable job status
  card for Static Form Web App pages.
- `textus:job-actions` / `textus-job-actions` render await/detail actions for
  application pages that want their own layout.
- `/web/system/jobs/{jobId}` and `/web/system/jobs/{jobId}/await` provide a
  system-hosted job result page for applications that want to link out instead
  of composing job UI.
- Job query/result/await surfaces enforce owner visibility through the job
  submitter principal/session, while admin/content-manager capabilities keep
  broader operational visibility.
- `textus-sample-app` notice posting uses the job ticket widget on the static
  result page.

Deferred scope:

- anonymous-user isolation depends on session context becoming available at
  ingress. Until the session layer is completed, anonymous jobs can be scoped
  only to the anonymous subject or to an explicit session/principal supplied by
  the caller.
- richer polling/island UX and progress timeline widgets remain future
  enhancements.
- shortid/entity navigation improvements are tracked by WEB-13.

### Inputs

- `docs/design/web-layer.md`
- `docs/spec/textus-widget.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `textus-sample-app`

---

## WEB-15: Textus Widget Asset Auto-Completion

Status: DONE

### Objective

Define and implement automatic completion of CSS/JS assets required by Textus
widgets.

Static Form Web App pages should stay easy to write. If a page uses widgets
that require framework assets such as local Bootstrap 5 CSS/JS, the renderer
should be able to insert those dependencies when they are not already present.
If the page or descriptor has already declared them, the renderer must not
duplicate them.

### Design Direction

- Asset dependencies are declared by widget capability, not by ad hoc page
  scanning alone.
- Local packaged assets are the baseline. CDN references are not required and
  must not be introduced by default.
- Bootstrap 5 is the default built-in asset family for current admin/business
  widgets.
- The renderer should detect existing equivalent declarations in the page head
  and descriptor-provided asset list.
- Auto-completion must be deterministic and must not reorder or duplicate
  explicit page assets.
- Descriptor settings may disable auto-completion or provide an alternate asset
  family later, but the simple Static Form path should work without descriptor
  boilerplate.

### Detailed Tasks

- [x] Define widget asset dependency metadata.
- [x] Add an asset completion pass for Static Form Web App HTML rendering.
- [x] Detect existing CSS/JS declarations and skip duplicates.
- [x] Insert local Bootstrap 5 CSS/JS when a rendered widget requires it and no
      equivalent asset is already present.
- [x] Define descriptor hooks for enabling/disabling auto-completion and for
      declaring page/app-level assets.
- [x] Add executable specifications for missing assets, existing assets,
      descriptor-declared assets, and no-widget pages.
- [x] Validate with built-in admin pages and `textus-sample-app` static pages.

### Closure

WEB-15 is complete as the first-pass Textus widget asset auto-completion
mechanism.

Completed scope:

- full HTML document result templates with Textus widgets receive local
  Bootstrap 5 CSS/JS when the assets are missing.
- fragment templates continue to use the existing built-in Bootstrap layout.
- existing page-level Bootstrap declarations are not duplicated.
- descriptor-level asset declarations are represented by `web.assets` and are
  treated as already supplied by the surrounding app/page composition.
- pages without Textus widgets are left unchanged.

Deferred scope:

- wiring descriptor-declared custom CSS/JS into every Static Form App route is
  future packaging/composition work.
- richer widget-specific dependency sets beyond the Bootstrap baseline are
  handled by WEB-16.

### Inputs

- `docs/design/web-layer.md`
- `docs/spec/textus-widget.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `textus-sample-app`

---

## WEB-16: Textus Widget Local Assets

Status: DONE

### Objective

Provide local Textus widget CSS/JS assets as a framework-owned baseline and
complete them automatically for Static Form Web App pages that use Textus
widgets.

WEB-15 established Bootstrap 5 asset completion. WEB-16 adds the Textus
widget-specific asset family so widgets can evolve beyond raw Bootstrap markup
without requiring every static page to repeat the same asset declarations.

### Design Direction

- Textus widget assets are packaged with the framework and served offline.
- Widget assets are added only when Textus widgets are present, unless a
  built-in framework page layout already includes them.
- Bootstrap remains the base visual/runtime dependency. Textus widget assets
  are layered after Bootstrap.
- Existing page declarations and descriptor-declared assets are treated as
  already supplied and must not be duplicated.
- The asset names are stable framework URLs:
  - `/web/assets/textus-widgets.css`
  - `/web/assets/textus-widgets.js`

### Detailed Tasks

- [x] Package `textus-widgets.css` and `textus-widgets.js` as local resources.
- [x] Serve the assets from `/web/assets`.
- [x] Extend Static Form App asset completion to add Textus widget assets for
      full HTML document templates that contain `textus:` or `textus-` widgets.
- [x] Keep insertion order deterministic: Bootstrap first, Textus widget assets
      second.
- [x] Avoid duplicate insertion when a page or descriptor already supplies the
      assets.
- [x] Include Textus widget assets in the built-in Bootstrap page layout used
      for framework-generated and fragment-wrapped pages.
- [x] Add executable specifications for insertion, duplicate suppression,
      no-widget pages, and packaged asset loading.
- [x] Update the widget specification.

### Closure

WEB-16 is complete as the first framework-owned Textus widget asset layer.
Future widget families may add more granular dependency metadata, but current
Static Form Web App pages can rely on local Bootstrap plus local Textus widget
assets without descriptor boilerplate.

### Inputs

- `docs/spec/textus-widget.md`
- `docs/phase/phase-12.md`
- `docs/phase/phase-12-checklist.md`

---

## WEB-17: Static Form Result Asset E2E

Status: DONE

### Objective

Validate that Textus widget asset completion works in real Static Form Web App
responses, not only in renderer-level specifications.

WEB-16 verifies the renderer and framework asset routes. WEB-17 adds a
sample-application executable check that sends actual form POST requests and
asserts that result pages receive the framework assets and do not leak
unexpanded `textus:` / `textus-` widget tags.

### Design Direction

- Use `textus-sample-app` as the practical validation driver.
- Exercise real form routes rather than only static file routes.
- Cover both command-style and query-style result pages.
- Check framework asset completion, widget expansion, and representative
  result-page content in the same flow.

### Detailed Tasks

- [x] Add a sample-app script that starts the local server.
- [x] POST to `post-notice` and verify the result page includes:
      - `/web/assets/bootstrap.min.css`
      - `/web/assets/textus-widgets.css`
      - `/web/assets/bootstrap.bundle.min.js`
      - `/web/assets/textus-widgets.js`
- [x] POST to `search-notices` and verify the same framework assets.
- [x] Verify the result pages include expected business headings.
- [x] Verify no raw `<textus...` widget tags remain in the responses.
- [x] Run the script against the published local CNCF runtime.

### Closure

WEB-17 is complete as the end-to-end confirmation that Static Form result pages
in `textus-sample-app` receive the WEB-16 framework assets and render Textus
widgets before returning HTML.

### Inputs

- `textus-sample-app/scripts/check-static-form-result-assets.sh`
- `docs/spec/textus-widget.md`

---

## WEB-18: Descriptor Asset Completion Control

Status: DONE

### Objective

Connect Web Descriptor asset settings to Static Form result rendering.

WEB-16 introduced framework-owned Bootstrap/Textus widget asset completion and
WEB-17 validated it end to end in the sample app. WEB-18 makes the descriptor
control path real: `web.assets.autoComplete`, `web.assets.css`, and
`web.assets.js` now influence the renderer options used for result pages.

### Design Direction

- Descriptor settings are app/page composition hints for the Static Form path.
- `web.assets.autoComplete: false` disables automatic framework asset
  insertion for result HTML documents.
- `web.assets.css` and `web.assets.js` are treated as assets already supplied
  by the surrounding app/page composition.
- Widget rendering still runs even when asset completion is disabled.
- Omitted descriptor settings preserve the zero-configuration default:
  Textus widget pages receive local Bootstrap and Textus widget assets.

### Detailed Tasks

- [x] Add an `autoComplete` switch to the renderer asset completion options.
- [x] Carry asset completion options through `FormResultProperties`.
- [x] Build result asset options from `engine.webDescriptor.assets`.
- [x] Use descriptor-declared CSS/JS as duplicate-suppression inputs.
- [x] Add executable specifications for disabled auto-completion.
- [x] Add executable specifications for descriptor-declared Bootstrap and
      Textus widget assets.
- [x] Keep direct renderer usage backward compatible through default options.

### Closure

WEB-18 is complete for Static Form result rendering. Descriptor-declared assets
now participate in the result-page asset completion decision without requiring
template programming. Broader route-level insertion of arbitrary descriptor
assets remains separate app/page composition work.

### Inputs

- `docs/spec/textus-widget.md`
- `docs/design/web-layer.md`
- `docs/phase/phase-12.md`

---

## WEB-19: Descriptor Asset Insertion

Status: DONE

### Objective

Use Web Descriptor asset declarations as actual Static Form result page
composition input.

WEB-18 connected `web.assets` to the asset completion decision. WEB-19 extends
that path so descriptor-declared CSS/JS are inserted into generated result
HTML, with duplicate suppression, for both full HTML document templates and
fragment templates wrapped by the built-in layout.

### Design Direction

- `web.assets.css` entries are inserted before `</head>`.
- `web.assets.js` entries are inserted before `</body>`.
- Framework assets remain first: Bootstrap, then Textus widget assets.
- Descriptor app/page composition assets are inserted after framework assets.
- Existing page declarations are not duplicated.
- `autoComplete: false` disables framework asset auto-completion only.
  Explicit descriptor CSS/JS are still inserted because they are not automatic
  inference; they are application composition declarations.
- WEB-19 implements global `web.assets`. App/form/page scoped assets remain
  future composition work.

### Detailed Tasks

- [x] Add descriptor asset insertion after framework asset completion.
- [x] Keep descriptor insertion active when `autoComplete` is false.
- [x] Apply descriptor insertion to full HTML document result templates.
- [x] Apply descriptor insertion to fragment result templates after built-in
      page wrapping.
- [x] Suppress duplicates when the page already contains a descriptor asset.
- [x] Add executable specifications for framework ordering, disabled
      auto-completion with explicit assets, and fragment pages.
- [x] Validate descriptor asset insertion with `textus-sample-app` result-page
      checks.
- [x] Update Web/widget design documentation.

### Closure

WEB-19 is complete for global Static Form result asset composition. The runtime
can now combine framework assets and descriptor-declared application assets
without requiring template authors to repeat shared links on every result page.

### Inputs

- `docs/spec/textus-widget.md`
- `docs/design/web-layer.md`
- `docs/phase/phase-12.md`

---

## WEB-20: Scoped Descriptor Assets

Status: DONE

### Objective

Allow Static Form Web App assets to be scoped below the global descriptor.

WEB-19 made `web.assets` an actual result-page composition input. WEB-20 adds
the next practical scope levels so multi-app packages can avoid applying every
CSS/JS file to every result page.

### Design Direction

- Asset scopes are merged in deterministic order:
  1. global `web.assets`
  2. app `web.apps[].assets`
  3. form/operation `web.form.<selector>.assets`
- Descriptor assets are inserted after framework assets.
- Duplicate asset URLs are suppressed across scopes.
- `autoComplete=false` in any merged scope disables framework asset
  auto-completion for that result page.
- WEB-20 implements global, app, and form scopes. Template/page-specific assets
  remain future composition work.

### Detailed Tasks

- [x] Add `assets` to `WebDescriptor.App`.
- [x] Add `assets` to `WebDescriptor.Form`.
- [x] Add descriptor asset merge helpers for result rendering.
- [x] Use merged result assets in `Http4sHttpServer` Static Form result
      rendering.
- [x] Add executable specifications for parsing and merge order.
- [x] Move `textus-sample-app` notice-board assets to app scope.
- [x] Validate app-scoped assets with Static Form result-page E2E checks.

### Closure

WEB-20 is complete for the global/app/form descriptor scopes needed by current
Static Form Web App result pages. The sample app now declares its app-local
assets under the `notice-board` Web app entry rather than the global asset
scope.

### Inputs

- `docs/spec/textus-widget.md`
- `docs/design/web-layer.md`
- `docs/phase/phase-12.md`
- `textus-sample-app/web/web-descriptor.yaml`

---

## WEB-21: Form-Scoped Asset Sample E2E

Status: DONE

### Objective

Validate form/operation-scoped descriptor assets with `textus-sample-app`.

WEB-20 added the runtime mechanism for global, app, and form asset scopes.
WEB-21 fixes the form-scope behavior in the sample application so the feature
is covered by real Static Form result pages, not only descriptor parsing specs.

### Design Direction

- App-scoped assets provide the notice-board Web app baseline.
- Form-scoped assets are used only for the `search-notices` result flow.
- `post-notice` result pages must keep app-scoped assets but must not receive
  `search-notices` form assets.
- Static Form result E2E checks should verify both inclusion and exclusion.

### Detailed Tasks

- [x] Add `web.form.notice-board.notice.search-notices.assets` to
      `textus-sample-app`.
- [x] Add search-result-specific CSS/JS under the notice-board Web app assets.
- [x] Extend result-page E2E checks so `search-notices` includes form-scoped
      assets.
- [x] Extend result-page E2E checks so `post-notice` excludes form-scoped
      assets.
- [x] Extend packaging checks so the form-scoped assets are served from both
      canonical and implicit app-local asset routes.
- [x] Add descriptor merge specs for global/app/form order, duplicate
      suppression, and scoped `autoComplete=false`.

### Closure

WEB-21 is complete as the practical sample validation for form-scoped assets.
The sample app now demonstrates the intended split: app assets apply to every
notice-board result page, while search result assets apply only to the
`search-notices` operation.

### Inputs

- `textus-sample-app/web/web-descriptor.yaml`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`
- `textus-sample-app/scripts/check-web-packaging.sh`
- `docs/spec/textus-widget.md`

---

## WEB-22: Input Form Scoped Assets

Status: DONE

### Objective

Apply descriptor asset composition to Static Form input pages, not only result
pages.

WEB-18 through WEB-21 established framework, descriptor, app, and form scoped
assets for result pages. WEB-22 extends the same asset model to the operation
input form surface.

### Design Direction

- Component form indexes use global and app scoped assets.
- Operation input forms use global, app, and form scoped assets.
- Form scoped assets must not appear on unrelated operation input forms.
- Form scoped assets must not appear on the component form index.
- Built-in Static Form layout still supplies the local framework baseline.

### Detailed Tasks

- [x] Insert app scoped descriptor assets into component form indexes.
- [x] Insert app and form scoped descriptor assets into operation input forms.
- [x] Add executable specifications for form index app assets.
- [x] Add executable specifications for operation input form app/form assets
      and selector mismatch exclusion.
- [x] Extend `textus-sample-app` E2E checks for form index and input forms.
- [x] Verify `search-notices` form scoped assets do not appear on
      `post-notice` input/result pages.

### Closure

WEB-22 is complete for the Static Form input side. Descriptor asset scopes now
apply consistently across component form indexes, operation input forms, and
operation result pages.

### Inputs

- `docs/spec/textus-widget.md`
- `docs/design/web-layer.md`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`

---

## WEB-23 — Expose Descriptor Asset Composition In Admin

Status: DONE

### Goal

Make descriptor-driven asset behavior inspectable from the management console.
Package authors need to see the configured scopes and the completed
composition used by Static Form pages without reading runtime code.

### Scope

- Completed descriptor JSON exposes global, app, and form asset scopes.
- Completed descriptor JSON exposes resolved form composition for component
  form indexes, operation input pages, and operation result pages.
- Configured descriptor JSON keeps the explicit asset entries for comparison.
- Component descriptor views filter resolved form composition to the component
  scope while preserving app scope visibility.

### Detailed Tasks

- [x] Add descriptor helper for component form index asset composition.
- [x] Include app assets in descriptor app JSON.
- [x] Include completed asset composition in admin descriptor JSON.
- [x] Add executable specification coverage for descriptor asset composition.

### Closure

WEB-23 is complete for descriptor/admin visibility. The management console now
shows which descriptor asset scopes are configured and which merged asset lists
are used by Static Form index, input, and result pages.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/WebDescriptor.scala`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-24 — Render Descriptor Asset Composition Tables

Status: DONE

### Goal

Make descriptor asset composition readable from the management console without
requiring operators to inspect JSON by hand.

### Scope

- System descriptor admin page shows configured descriptor asset scopes as a
  Bootstrap table.
- Component descriptor admin page shows the same scope table and filters
  resolved form rows to the selected component.
- Resolved form page rows show component form index, operation input, and
  operation result asset lists.
- Raw completed/configured descriptor JSON remains available for exact
  inspection.

### Detailed Tasks

- [x] Add Asset Composition section to descriptor admin pages.
- [x] Render configured global/app/form asset scopes in a table.
- [x] Render completed form page asset rows in a table.
- [x] Add executable specification checks for the table headings and row
      labels.

### Closure

WEB-24 is complete for the first readable admin surface. The descriptor page
now provides both operator-friendly tables and raw JSON for exact debugging.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-25 — Render Descriptor Control Tables

Status: DONE

### Goal

Make the descriptor admin page useful as a management-console inspection
surface, not only a raw JSON viewer.

### Scope

- Descriptor admin pages show completed Web app entries as a table.
- Descriptor admin pages show Web routes as a table.
- Descriptor admin pages show form exposure and authorization as a table.
- Descriptor admin pages show admin surfaces and field control summaries as a
  table.
- Component descriptor pages filter selector and route rows to the selected
  component scope where the descriptor carries component-qualified data.

### Detailed Tasks

- [x] Add Descriptor Controls section before Asset Composition.
- [x] Render Apps and Routes tables from completed descriptor values.
- [x] Render Form Access And Authorization table.
- [x] Render Admin Surfaces table.
- [x] Add executable specification checks for the new descriptor tables.

### Closure

WEB-25 is complete for the first descriptor-wide readable admin view. Raw JSON
remains available for exact inspection, but the normal admin path now exposes
the major descriptor contracts as Bootstrap tables.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-26 — Link Descriptor Admin Table Rows

Status: DONE

### Goal

Turn descriptor admin tables from passive inspection tables into navigation
surfaces for the related Web and admin functions.

### Scope

- Apps table links Web app paths when they are runtime Web paths.
- Routes table links route paths when they are runtime Web paths.
- Form Access And Authorization table links operation selectors to Static Form
  input pages.
- Admin Surfaces table links component-scoped surfaces to entity/data/
  aggregate/view admin pages.

### Detailed Tasks

- [x] Add Web path links to Apps table.
- [x] Add Web path links to Routes table.
- [x] Add Static Form links for component/service/operation selectors.
- [x] Add admin surface links when a component scope is known.
- [x] Add executable specification checks for descriptor admin links.

### Closure

WEB-26 is complete for first-pass descriptor navigation. The descriptor admin
page can now be used to jump from descriptor declarations to the runtime/admin
surface they configure.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-27 — Add Descriptor Admin Filtering

Status: DONE

### Goal

Improve descriptor admin usability when descriptor tables grow beyond a small
sample application.

### Scope

- Descriptor Controls section provides a client-side filter field.
- The filter applies to descriptor control table rows.
- Component descriptor pages keep component-scoped selectors and admin
  surfaces focused on the selected component context.

### Detailed Tasks

- [x] Add filter input to Descriptor Controls.
- [x] Add lightweight client-side row filtering for descriptor tables.
- [x] Tighten component-scoped admin surface matching for local and
      component-qualified selectors.
- [x] Add executable specification checks for the filter surface.

### Closure

WEB-27 is complete for the initial descriptor table filtering path. It is a
local page filter; server-side query/filter parameters can be added later if
descriptor pages become too large to render eagerly.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-28 — Specify Component Descriptor Scope Rules

Status: DONE

### Goal

Make component descriptor pages scope-aware by a documented rule instead of
ad-hoc display behavior.

### Scope

- Component Apps table shows app entries that match the component name or are
  targeted by a route for the component.
- Component Routes table shows routes whose `target.component` matches the
  selected component.
- Component Form Access And Authorization table shows selectors whose first
  selector segment matches the component.
- Component Admin Surfaces table shows component-local surfaces such as
  `entity.notice` and component-qualified surfaces that match the component.

### Detailed Tasks

- [x] Add component-scoped app matching.
- [x] Keep route and form selector matching component-qualified.
- [x] Keep admin surface matching local and component-qualified.
- [x] Add executable specification checks that other-component links are not
      emitted from component descriptor tables.

### Closure

WEB-28 is complete for the current descriptor admin rules. Raw JSON still shows
the configured descriptor for comparison, while tables are the scope-aware
inspection surface.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-29 — Add Descriptor Table Counts And Filter Empty State

Status: DONE

### Goal

Improve descriptor table readability and make client-side filtering less
ambiguous.

### Scope

- Descriptor control table headings show the number of rendered rows.
- The filter surface shows an explicit no-match message when every descriptor
  control row is hidden.
- Existing raw JSON and table content remain unchanged apart from the count and
  filter affordance.

### Detailed Tasks

- [x] Add row-count badges to descriptor control table headings.
- [x] Add no-match message to the descriptor filter surface.
- [x] Update the client-side filter script to toggle no-match state.
- [x] Add executable specification checks for the no-match surface.

### Closure

WEB-29 is complete for first-pass descriptor table usability. More advanced
server-side filtering remains deferred until descriptor pages become too large
to render eagerly.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-30 — Make Raw Descriptor JSON Auxiliary

Status: DONE

### Goal

Keep raw descriptor JSON available for exact debugging while making table-based
inspection the default path.

### Scope

- Descriptor admin pages include section navigation for controls, asset
  composition, completed JSON, and configured JSON.
- Completed descriptor JSON is placed in a `details` panel.
- Configured descriptor JSON is placed in a `details` panel.
- Raw JSON section anchors are stable for direct links and future table-to-JSON
  navigation.

### Detailed Tasks

- [x] Add descriptor section navigation.
- [x] Add stable anchors for completed and configured JSON.
- [x] Render completed/configured JSON in collapsible panels.
- [x] Add executable specification checks for navigation and details panels.

### Closure

WEB-30 is complete for the raw JSON visibility shift. JSON remains present in
the page, but the primary admin surface is now the rendered tables.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-31 — Polish Descriptor Admin Sections

Status: DONE

### Goal

Improve descriptor admin page readability without changing the descriptor data
contract.

### Scope

- Section navigation uses Bootstrap navigation pills.
- Descriptor Controls and Asset Composition headings link to completed JSON.
- Descriptor sections have stable anchors for browser navigation.
- JSON panels are visually distinct and collapsed by default.

### Detailed Tasks

- [x] Add Bootstrap section navigation styling.
- [x] Add table-to-completed-JSON links.
- [x] Add stable section anchors.
- [x] Add executable specification checks for the polished section surface.

### Closure

WEB-31 is complete for first-pass descriptor admin polish. Further visual
layout work can proceed with screenshots after the broader admin console flow
settles.

### Inputs

- `docs/phase/phase-12.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`

---

## WEB-32 — Management Console CRUD Route Recheck

Status: DONE

### Goal

Re-anchor the current Web development thread on the remaining Form/Admin path:
Management Console CRUD must continue to work as an actual browser route flow
from `textus-sample-app`, not only as renderer-level specifications.

### Scope

- Recheck component admin entry points for entity, data, aggregate, and view
  surfaces.
- Recheck entity type list, entity record list, and entity new form routes.
- Execute one real admin entity create POST through `/form`.
- Confirm the created entity appears in the admin entity list.
- Follow the list detail link and confirm the created values render on the
  entity detail page.

### Detailed Tasks

- [x] Add sample-app route checks for `/web/notice-board/admin/entities`,
      `/data`, `/aggregates`, and `/views`.
- [x] Add sample-app route checks for the `Notice` entity list and new form.
- [x] Add a sample-app admin CRUD check that posts a new `Notice` through the
      admin entity create form endpoint.
- [x] Verify list-to-detail navigation for the created `Notice` without using
      a hard-coded generated id.
- [x] Keep the check focused on current entity CRUD. Data CRUD and
      aggregate-command E2E remain separate follow-up validation items because
      the notice-board sample does not yet provide a meaningful data collection
      fixture for those flows.

### Closure

WEB-32 closes the immediate route-level recheck. CNCF-side executable specs
already cover renderer and dispatch behavior; the new sample check confirms the
published runtime can serve the same flow from a real CAR-style application.

### Inputs

- `textus-sample-app/scripts/check-web-packaging.sh`
- `textus-sample-app/scripts/check-admin-crud.sh`
- `docs/design/management-console.md`
- `docs/notes/cncf-web-static-form-app-contract.md`

---

## WEB-33 — Management Console Data Surface Recheck

Status: DONE

### Goal

Confirm the sample application still exposes the component-scoped Data admin
surface after the Form/Admin route work, and explicitly record the current
sample limitation for full Data CRUD E2E.

### Scope

- Recheck `/web/notice-board/admin/data`.
- Confirm the current baseline placeholder is visible.
- Keep full DataStore CRUD E2E separate until the sample app has a meaningful
  data collection fixture.

### Detailed Tasks

- [x] Add a sample-app check for the Data admin entrypoint.
- [x] Assert the current placeholder text so the limitation is visible rather
      than silently passing as an empty page.
- [x] Record that CNCF-side executable specs already cover DataStore-backed
      data list/detail/new/create/edit/update with an `audit` fixture, while
      the notice-board sample does not yet model an application data collection.

### Closure

WEB-33 closes the route-level recheck for the current CAR sample. A future
sample that includes a real data collection should add the full create/update
round trip, analogous to the WEB-32 entity CRUD check.

### Inputs

- `textus-sample-app/scripts/check-admin-surfaces.sh`
- `textus-sample-app/scripts/check-web-packaging.sh`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`

---

## WEB-34 — Management Console Aggregate Surface Recheck

Status: DONE

### Goal

Confirm the sample application exposes aggregate metadata, read baseline, and
operation-backed aggregate actions through the built-in Management Console.

### Scope

- Recheck `/web/notice-board/admin/aggregates`.
- Recheck `/web/notice-board/admin/aggregates/notice`.
- Confirm create/read/update operations are rendered as Operation form links,
  not as direct aggregate mutation.
- Confirm aggregate JSON Form API metadata stays read-oriented.

### Detailed Tasks

- [x] Add a sample-app check for aggregate list metadata.
- [x] Add a sample-app check for the `notice` aggregate detail page.
- [x] Assert `createNotice`, `updateNotice`, and read operation links route to
      `/form/notice-board/aggregate/...`.
- [x] Assert the generic aggregate Form API definition remains
      `admin-aggregate` and exposes list/detail navigation metadata.

### Closure

WEB-34 confirms the current aggregate Management Console contract from the
sample runtime: aggregate create/update remain operation-backed and the
aggregate page is the administrative navigation surface.

### Inputs

- `textus-sample-app/scripts/check-admin-surfaces.sh`
- `docs/design/management-console.md`
- `docs/notes/aggregate-method-implementation-strategy.md`

---

## WEB-35 — Management Console View Surface Recheck

Status: DONE

### Goal

Confirm the sample application exposes view metadata and the read-only view
baseline through the built-in Management Console.

### Scope

- Recheck `/web/notice-board/admin/views`.
- Recheck `/web/notice-board/admin/views/notice`.
- Confirm summary/detail view metadata and the read result table are rendered.
- Confirm view JSON Form API metadata stays read-oriented.

### Detailed Tasks

- [x] Add a sample-app check for view list metadata.
- [x] Add a sample-app check for the `notice` view detail/read page.
- [x] Assert the view read table uses the expected CML-derived columns.
- [x] Assert the generic view Form API definition remains `admin-view` and
      exposes list/detail navigation metadata.

### Closure

WEB-35 confirms the current view Management Console contract from the sample
runtime: views remain read-only and use the same schema/view metadata path as
the rest of the Form/Admin surface.

### Inputs

- `textus-sample-app/scripts/check-admin-surfaces.sh`
- `docs/design/management-console.md`
- `docs/design/web-form-api-schema.md`

---

## WEB-36 — Management Console Read Surface E2E Recheck

Status: DONE

### Goal

Confirm that data created through the Management Console entity form is visible
from all read-oriented admin surfaces that should share the same runtime
metadata and entity access path.

### Scope

- Create one `Notice` through the admin entity create form.
- Confirm the created `Notice` appears in the entity list.
- Follow the generated entity detail link and confirm title/content render.
- Confirm the aggregate read page shows the created `Notice`.
- Confirm the view read page shows the created `Notice`.

### Detailed Tasks

- [x] Add a sample-app check dedicated to read-surface propagation.
- [x] Use a real `/form/notice-board/admin/entities/notice/create` POST rather
      than directly mutating test data.
- [x] Resolve the created entity detail URL from the rendered list page instead
      of hard-coding generated identifiers.
- [x] Verify entity detail, aggregate read, and view read pages with the same
      created title/content.

### Closure

WEB-36 confirms that the current Management Console read surfaces are wired to
the same runtime entity data path in the published sample runtime. It catches
regressions where a create path updates only one in-memory surface but aggregate
or view read pages drift out of sync.

### Inputs

- `textus-sample-app/scripts/check-admin-read-flows.sh`
- `docs/design/management-console.md`
- `docs/design/web-form-api-schema.md`

---

## WEB-37 — Aggregate Operation Create/Update E2E Recheck

Status: DONE

### Goal

Confirm that aggregate create/update actions exposed by the Management Console
execute as operation-backed Form flows, produce async job result pages, and
update the aggregate read output.

### Scope

- Recheck aggregate detail links to operation forms.
- Render aggregate create and update operation forms.
- Submit create/update through `/form/notice-board/aggregate/...`.
- Await the async job result through the generated job result action.
- Confirm aggregate read reflects both created and updated values.

### Detailed Tasks

- [x] Add sample-app checks for aggregate create/update operation form routes.
- [x] Submit `createNotice` through the aggregate operation form and await the
      generated job result.
- [x] Confirm the aggregate read page contains the created record.
- [x] Submit `updateNotice` through the aggregate operation form with the admin
      form context needed by the operation policy.
- [x] Preserve form/admin context when the job await route dispatches
      `job_control.job.await_job_result`.
- [x] Confirm the aggregate read page contains the updated title/content.

### Closure

WEB-37 confirms the current aggregate Management Console write path without
adding direct aggregate mutation routes. A bug found during the sample E2E was
fixed in the HTTP Form job-await path: POSTed form context is now propagated to
the internal job-control operation so ownership/capability checks see the same
admin subject context as the submitted form flow.

### Inputs

- `textus-sample-app/scripts/check-admin-aggregate-operations.sh`
- `textus-sample-app/scripts/check-web-packaging.sh`
- `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`
- `docs/notes/aggregate-method-implementation-strategy.md`

---

## WEB-38 — Data CRUD Fixture Boundary

Status: DONE

### Goal

Keep the Data admin surface honest while avoiding a misleading full CRUD E2E
test in a sample app that does not yet model a meaningful data collection.

### Scope

- Keep route-level validation for `/web/notice-board/admin/data`.
- Keep the current placeholder assertion visible in sample checks.
- Record that CNCF renderer specs already cover DataStore-backed data CRUD with
  a fixture.
- Defer sample-level Data CRUD E2E until a data-oriented sample or fixture is
  introduced.

### Detailed Tasks

- [x] Keep the sample-app Data admin entrypoint check in the packaging/admin
      surface validation.
- [x] Keep the placeholder text assertion so unsupported Data CRUD is explicit.
- [x] Record the fixture boundary in Phase 12 instead of treating the current
      notice-board sample as a data CRUD application.

### Closure

WEB-38 closes the immediate Phase 12 bookkeeping for Data CRUD validation. The
runtime surface remains visible, but a meaningful end-to-end create/update
round trip is reserved for a future sample that actually owns a data collection
model.

### Inputs

- `textus-sample-app/scripts/check-web-packaging.sh`
- `textus-sample-app/scripts/check-admin-surfaces.sh`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`

---

## WEB-39 — Admin Console CRUD Navigation Polish

Status: DONE

### Goal

Confirm the built-in Management Console entity CRUD flow is usable as a real
browser flow, not only as isolated create/list/detail endpoints.

### Scope

- Recheck entity create from the admin new form.
- Follow list-to-detail navigation.
- Follow detail-to-edit navigation.
- Submit the edit form through the admin update endpoint.
- Confirm the updated detail page reflects the submitted values.

### Detailed Tasks

- [x] Extend the sample admin CRUD script from create/detail to edit/update.
- [x] Assert the detail page exposes the edit link.
- [x] Assert the edit page exposes update and cancel controls.
- [x] Submit update through the generated `/form/.../update` action.
- [x] Reopen detail and confirm updated title/content render.

### Closure

WEB-39 closes the first practical Admin Console UX pass for entity CRUD. The
remaining polish is visual refinement, not missing route continuity for the
current entity path.

### Inputs

- `textus-sample-app/scripts/check-admin-crud.sh`
- `docs/design/management-console.md`

---

## WEB-40 — Static Form Result Transition Convention Recheck

Status: DONE

### Goal

Confirm the simple Static Form Web App convention remains the primary result
transition mechanism: operation result status selects static HTML templates and
the framework supplies result/form properties for that page.

### Scope

- Verify operation-specific `xxx__200.html` result pages for post/search flows.
- Verify hidden form context can be preserved into subsequent POST actions.
- Verify async command pages expose job await links.
- Keep descriptor redirects/templates as supplemental configuration rather than
  the default sample path.

### Detailed Tasks

- [x] Keep `post-notice__200.html` and `search-notices__200.html` as the sample
      result pages.
- [x] Check rendered result pages include result text from those templates.
- [x] Check search result refresh carries `recipientName` through POST body via
      hidden form fields.
- [x] Check command result pages expose a generated job await action.

### Closure

WEB-40 confirms that the Static Form Web App sample can express useful
application flow through static result conventions plus framework-provided
properties, without requiring descriptor redirects as the normal path.

### Inputs

- `textus-sample-app/web/notice-board/post-notice__200.html`
- `textus-sample-app/web/notice-board/search-notices__200.html`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`
- `docs/notes/cncf-web-static-form-app-contract.md`

---

## WEB-41 — Textus Widget Expansion Recheck

Status: DONE

### Goal

Validate the current Textus widget set against the sample app so future widget
expansion keeps both namespace and HTML-compatible notation working in actual
Static Form result pages.

### Scope

- Exercise result table and card-list widgets from search results.
- Exercise job-ticket, generic card, and action-link widgets from command
  result pages.
- Exercise property-list, error-panel, and hidden-context widgets from existing
  sample templates.
- Add the missing generic `textus:card` runtime renderer.

### Detailed Tasks

- [x] Implement generic `textus:card` / `textus-card` rendering as a Bootstrap
      card container.
- [x] Add renderer specification coverage for generic card expansion.
- [x] Add a generic card with a nested action-link to the sample post result
      template.
- [x] Extend sample checks so rendered pages contain card/action output and no
      unexpanded `textus` tags.

### Closure

WEB-41 confirms the current widget baseline is usable from Static Form result
pages. The widget contract remains intentionally small: table/card result
rendering, job actions, alerts/errors, properties, hidden context, and generic
Bootstrap card composition.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/post-notice__200.html`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`

---

## WEB-42 — Web Packaging and Descriptor Route Recheck

Status: DONE

### Goal

Keep Web application packaging executable in the sample app after the Admin
Console, Static Form, and widget changes.

### Scope

- Recheck canonical `/web/{component}/{webApp}` routing.
- Recheck implicit single-app aliases.
- Recheck framework, app, and form-scoped asset routing.
- Recheck completed descriptor admin inspection.
- Recheck admin surface entrypoints and operation form links.

### Detailed Tasks

- [x] Keep sample package file checks for `.textus/config.yaml`,
      `web/web-descriptor.yaml`, result pages, error pages, and local assets.
- [x] Confirm canonical and implicit Web app routes return the same application
      content and Bootstrap/app asset links.
- [x] Confirm framework Textus widget assets are served from `/web/assets`.
- [x] Confirm descriptor admin pages expose completed/configured descriptor
      sections, control tables, asset tables, and admin surface links.
- [x] Confirm admin entity, data, aggregate, and view entrypoints remain
      reachable from the packaged sample.

### Closure

WEB-42 confirms the current CAR-style Web package layout remains coherent:
`/web/{component}/{webApp}` is canonical, aliases are available for the
single-app sample, local assets resolve, and descriptor/admin inspection remains
available.

### Inputs

- `textus-sample-app/scripts/check-web-packaging.sh`
- `textus-sample-app/web/web-descriptor.yaml`
- `docs/design/web-layer.md`

---

## WEB-43 — Aggregate Detail UX and Instance Operation Recheck

Status: DONE

### Goal

Confirm aggregate Management Console pages work as a drill-down flow from
aggregate list/read output into a specific aggregate instance and its available
operations.

### Scope

- Create a `Notice` through the aggregate create operation.
- Follow the aggregate read result link to the aggregate instance detail page.
- Confirm the instance detail page renders the created data.
- Confirm instance operation links preserve aggregate id context.
- Update the aggregate through the operation-backed update form and reopen the
  same instance detail page.

### Detailed Tasks

- [x] Extend the sample aggregate operation check to extract an instance detail
      link from aggregate read output.
- [x] Verify the aggregate instance detail page after create.
- [x] Verify the instance update operation link and id handoff.
- [x] Verify the same instance detail page after update.

### Closure

WEB-43 confirms the current aggregate UX contract: aggregate pages remain
operation-backed, but users can still move naturally from read results to an
instance detail page and then to instance-scoped operations.

### Inputs

- `textus-sample-app/scripts/check-admin-aggregate-operations.sh`
- `docs/design/management-console.md`
- `docs/notes/aggregate-method-implementation-strategy.md`

---

## WEB-44 — Static Form User Transition Flow Recheck

Status: DONE

### Goal

Confirm the Static Form Web App convention can drive a real no-login user flow
without descriptor-specific redirect programming.

### Scope

- Start from the public Web app entry point.
- Open the generated post form.
- Submit a notice and render `post-notice__200.html`.
- Await the async job result through the generated action.
- Search for the notice by recipient name and render `search-notices__200.html`.
- Open the detail form/link and render `get-notice__200.html`.

### Detailed Tasks

- [x] Add a sample-app script for public Static Form App flow validation.
- [x] Verify operation-specific static result templates are used.
- [x] Verify async command wait is explicit through the generated job action.
- [x] Verify search and detail pages receive result/form properties without
      leaving unexpanded Textus widget tags.

### Closure

WEB-44 confirms the basic static convention remains viable for demo and
internal tools: CML + static HTML result files + framework property injection
can express the main notice-board flow.

### Inputs

- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `textus-sample-app/web/notice-board/post-notice__200.html`
- `textus-sample-app/web/notice-board/search-notices__200.html`
- `textus-sample-app/web/notice-board/get-notice__200.html`
- `docs/notes/cncf-web-static-form-app-contract.md`

---

## WEB-45 — Widget Composition Coverage Expansion

Status: DONE

### Goal

Expand the executable widget baseline from result/table/card primitives to the
small action/status widgets needed by Static Form application pages.

### Scope

- Implement `textus:action-card` / `textus-action-card`.
- Implement `textus:status-badge` / `textus-status-badge`.
- Keep both namespace and HTML-compatible notation supported.
- Validate widget composition in renderer specs and the sample result page.

### Detailed Tasks

- [x] Add action-card rendering using the same action resolution model as
      `textus:action-link`.
- [x] Add status-badge rendering with conservative Bootstrap status mapping.
- [x] Add renderer specs for action-card and status-badge.
- [x] Use action-card and status-badge from the sample post result page.
- [x] Confirm final sample responses contain no unexpanded `textus` tags.

### Closure

WEB-45 keeps widget growth focused on concrete needs discovered by the sample
app: job/result actions and simple status display. Larger layout/navigation
widgets remain future work until they remove real repetition.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/post-notice__200.html`

---

## WEB-46 — Minimal Static Form Web App Validation

Status: DONE

### Goal

Validate `textus-sample-app` as a minimal no-login Static Form Web App, not only
as a Management Console/runtime test driver.

### Scope

- Confirm the public Web entry point exposes user-facing post/search actions.
- Confirm no login/session is required for the ordinary notice-board path.
- Confirm the public flow is complete enough for a demo: post, wait, search,
  and open detail.
- Keep admin console checks separate from the public app flow.

### Detailed Tasks

- [x] Add a browser-style sample script that starts from `/web/notice-board`.
- [x] Drive the public post/search/detail operations through `/form`.
- [x] Confirm result templates and widgets render as user-facing pages.
- [x] Keep admin-only validation in the admin scripts.

### Closure

WEB-46 confirms the sample app can now serve as the Phase 12 Static Form Web App
driver. Remaining work should be guided by using the sample as an application,
not only by admin console route coverage.

### Inputs

- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `textus-sample-app/web/notice-board/index.html`
- `docs/design/web-layer.md`

---

## WEB-47 — Static Form Page Transition Contract Recheck

Status: DONE

### Goal

Freeze the practical Static Form page transition contract before adding more
application polish.

### Scope

- Keep operation-specific static status templates as the default result
  transition mechanism.
- Keep descriptor `resultTemplate` and redirects supplemental rather than
  required for ordinary Static Form Web Apps.
- Confirm hidden context and operation result properties are enough for the
  current notice-board flow.
- Keep POST/Body handoff for action widgets that wait for command results.

### Detailed Tasks

- [x] Reconfirm that public post/search/detail uses static `xxx__200.html`
      templates.
- [x] Reconfirm result action widgets submit POST for await actions.
- [x] Reconfirm hidden context is preserved by framework-owned widgets and is
      not dispatched as operation arguments.
- [x] Record that descriptor transitions are optional escape hatches for
      advanced applications.

### Closure

WEB-47 keeps the framework optimized for CML + static HTML development. The
default path does not require programmable descriptor routing; descriptor-level
transition controls remain available for applications that outgrow the static
convention.

### Inputs

- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/design/web-layer.md`
- `textus-sample-app/scripts/check-static-form-app-flow.sh`

---

## WEB-48 — Application Job Result UX Baseline

Status: DONE

### Goal

Provide an embeddable job UX that application pages can use without rebuilding
the same job ticket, wait action, and system fallback link by hand.

### Scope

- Add a composed job widget for Static Form result pages.
- Keep local await actions POST-based.
- Provide a system job page handoff for applications that do not want to own the
  full job UX.
- Preserve job ownership enforcement in the underlying job routes.

### Detailed Tasks

- [x] Implement `textus:job-panel` / `textus-job-panel`.
- [x] Compose the panel from job ticket data and selected job actions.
- [x] Add a system job page link to `/web/system/jobs/{jobId}`.
- [x] Add renderer spec coverage for the composed job panel.
- [x] Use the job panel from the sample post result page.

### Closure

WEB-48 establishes the application job-result baseline: pages can embed the job
UX directly, or link to the system job page. In both cases, the actual job
access check remains in the framework job routes, not in the template.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/post-notice__200.html`

---

## WEB-49 — Textus Widget Catalog Baseline Consolidation

Status: DONE

### Goal

Consolidate the current Textus widget surface into a catalog that can guide
Static Form Web App work without implying a full UI framework.

### Scope

- Keep namespace notation and HTML-compatible notation equivalent.
- Organize widgets by use: result display, cards/actions, jobs, feedback,
  pagination, and hidden context.
- Record future layout/navigation/content widgets as candidates, not required
  baseline features.
- Keep Bootstrap 5 as the default rendering target.

### Detailed Tasks

- [x] Document the implemented result/table/card/action/job/feedback/paging
      widget families.
- [x] Add the job-panel widget to the catalog.
- [x] Keep future layout/navigation widgets documented as deferred candidates.
- [x] Verify sample pages contain no unexpanded Textus widget tags.

### Closure

WEB-49 makes the widget catalog usable as the reference for Phase 12 work. New
widgets should be added when they remove repeated static-template markup or
connect framework-owned properties to Bootstrap-friendly HTML.

### Inputs

- `docs/spec/textus-widget.md`
- `docs/notes/web-textus-widget-design.md`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`

---

## WEB-50 — Sample Static Form Web App Polish

Status: DONE

### Goal

Polish the notice-board sample enough to validate that Static Form Web Apps can
look like usable Bootstrap 5 applications while remaining static-template based.

### Scope

- Improve public result page structure without introducing application code.
- Use framework widgets for status and job UX.
- Keep the sample minimal: post, await, search, detail.
- Keep admin console checks separate from public app checks.

### Detailed Tasks

- [x] Add Bootstrap-oriented result sections to post/search/detail templates.
- [x] Add status badges to public result pages.
- [x] Use the job panel on the post result page.
- [x] Add sample CSS for result-page framing and mobile-friendly spacing.
- [x] Extend sample scripts to check the polished composed widgets.

### Closure

WEB-50 confirms the sample app can be used as a realistic Static Form Web App
driver while keeping the implementation intentionally simple: CML operations,
static HTML templates, Bootstrap assets, and Textus widgets.

### Inputs

- `textus-sample-app/web/notice-board/*.html`
- `textus-sample-app/web/notice-board/assets/app.css`
- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`

---

## WEB-51 — System Job Page Handoff Verification

Status: DONE

### Goal

Verify that application pages can hand job result UX to the framework-provided
system job page when they do not want to own the complete job interaction.

### Scope

- Follow the `textus:job-panel` system link to `/web/system/jobs/{jobId}`.
- Verify the system page renders a job ticket and await action.
- Verify system await POST returns the same framework result shape used by
  operation-local await.
- Keep job access enforcement in the system/job routes.

### Detailed Tasks

- [x] Extract the system job page link from the sample post result page.
- [x] GET the system job page and confirm the ticket/action UI.
- [x] POST to `/web/system/jobs/{jobId}/await`.
- [x] Keep the ordinary operation-local await path in the same public flow.

### Closure

WEB-51 confirms the job UX split: application pages can embed job controls, but
can also hand off to the system job page. Authorization remains framework-owned
because both local and system await routes dispatch through job-control
operations.

### Inputs

- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`
- `docs/design/web-layer.md`

---

## WEB-52 — Static Form UI Quality Criteria Recheck

Status: DONE

### Goal

Recheck the current Static Form sample against the Phase 12 Bootstrap polish
criteria without introducing a separate visual testing stack.

### Scope

- Ensure pages remain Bootstrap 5 local-asset based.
- Ensure public pages include viewport metadata for phone/tablet use.
- Ensure result pages use stable result containers and spacing hooks.
- Keep this as script-level structural validation for now.

### Detailed Tasks

- [x] Verify canonical app pages include the viewport meta tag.
- [x] Verify local Bootstrap and Textus widget assets are used.
- [x] Verify sample CSS contains result-page framing and responsive media
      hooks.
- [x] Keep screenshot/pixel-level validation deferred until a browser test
      harness is introduced.

### Closure

WEB-52 keeps the UI quality bar explicit: the current phase validates
responsive structure and local assets by scripts, while deeper visual regression
is deferred.

### Inputs

- `textus-sample-app/scripts/check-web-packaging.sh`
- `textus-sample-app/web/notice-board/assets/app.css`
- `docs/notes/web-bootstrap-ui-polish-design.md`

---

## WEB-53 — Detail-Oriented Widget Baseline

Status: DONE

### Goal

Add the first detail-oriented Textus widget so static detail pages can render
schema/view ordered records without hand-writing property rows.

### Scope

- Implement `textus:description-list` / `textus-description-list`.
- Use `source`, `entity`, and `view` attributes to resolve CML/schema-derived
  column order.
- Keep fallback behavior conservative when no object data is available.
- Use the widget in the notice detail page.

### Detailed Tasks

- [x] Add renderer support for `textus:description-list`.
- [x] Add executable spec coverage with CML/view-derived detail columns.
- [x] Document implemented attributes in the widget spec.
- [x] Use the widget in the sample `get-notice__200.html` page.
- [x] Extend sample result checks to confirm widget expansion.

### Closure

WEB-53 gives Static Form detail pages a framework-owned projection point. This
keeps display field order tied to schema/view metadata rather than ad hoc HTML
or case-class reflection.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/get-notice__200.html`

---

## WEB-54 — Web App Packaging Runtime Mapping Recheck

Status: DONE

### Goal

Recheck the CAR/SAR Web app packaging assumptions against the current
notice-board runtime mapping.

### Scope

- Confirm packaged `/web/{webApp}` resources are served from the canonical
  `/web/{component}/{webApp}` runtime route.
- Confirm subsystem aliases can expose the same app at `/web/{webApp}` or
  `/web`.
- Confirm local assets resolve under both canonical and alias routes.
- Confirm completed descriptor admin pages expose the effective route and asset
  composition.

### Detailed Tasks

- [x] Recheck canonical `/web/notice-board/notice-board`.
- [x] Recheck implicit `/web/notice-board` and `/web` aliases.
- [x] Recheck canonical and alias asset routes.
- [x] Recheck completed descriptor route/asset composition visibility.
- [x] Recheck polished app CSS is served through the packaged asset routes.

### Closure

WEB-54 keeps the packaging contract grounded in the sample app: source files
live under `web/notice-board`, while runtime routes expose the component-scoped
canonical path and configured aliases.

### Inputs

- `textus-sample-app/scripts/check-web-packaging.sh`
- `textus-sample-app/web/web-descriptor.yaml`
- `docs/design/web-layer.md`

---

## WEB-55 — Result Detail Navigation

Status: DONE

### Goal

Let Static Form search/result pages move naturally from a list result into a
detail page without hand-writing table or card action markup.

### Scope

- Add detail action support to `textus-result-table`.
- Reuse the same detail action support from `textus:card-list`.
- Expand record field placeholders from the current result row.
- Validate the notice-board search result -> detail flow.

### Detailed Tasks

- [x] Add `detail-href` and `detail-label` attributes to list/detail result
      widgets.
- [x] Add executable renderer coverage for table and card detail actions.
- [x] Use the detail action in the sample notice search result page.
- [x] Extend the sample flow check to open details from search results.

### Closure

WEB-55 makes search-result-to-detail navigation part of the framework-owned
widget contract. Static pages can keep using CML/view field selection while
getting a consistent Bootstrap action affordance.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/search-notices__200.html`
- `textus-sample-app/scripts/check-static-form-app-flow.sh`

---

## WEB-56 — Description List Column Selection Recheck

Status: DONE

### Goal

Confirm detail widgets obey schema/view-derived field order and also support
explicit column declarations when a static page needs a local projection.

### Scope

- Keep CML/view column selection as the primary route.
- Verify explicit `columns` fallback for `textus:description-list`.
- Ensure omitted fields remain omitted from detail output.

### Detailed Tasks

- [x] Keep existing CML/view-driven description-list spec coverage.
- [x] Add explicit `columns` description-list spec coverage.
- [x] Document the implemented attribute contract.

### Closure

WEB-56 keeps detail rendering tied to the same projection model as result
tables and cards, while retaining a narrow explicit-column escape hatch for
static templates.

### Inputs

- `docs/spec/textus-widget.md`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`

---

## WEB-57 — Navigation List Widget Baseline

Status: DONE

### Goal

Provide a small framework-owned navigation widget for static result pages so
common page-to-page links use consistent Bootstrap markup.

### Scope

- Implement `textus:nav-list` and `textus-nav-list`.
- Support explicit static item lists.
- Support button-style and list-group-style rendering.
- Use the widget in notice-board result pages.

### Detailed Tasks

- [x] Add renderer support for namespace and HTML-compatible notation.
- [x] Add executable spec coverage for button and list styles.
- [x] Document baseline attributes.
- [x] Use the widget in sample result pages.

### Closure

WEB-57 covers the immediate static-page navigation need without introducing a
larger menu model. Result-provided navigation sources can be added later when
the framework emits richer action collections.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/*.html`

---

## WEB-58 — Result Action Group Baseline

Status: DONE

### Goal

Provide a general action-row widget for operation result pages so application
pages do not need to hand-compose repeated action links and inline forms.

### Scope

- Implement `textus:action-group` and `textus-action-group`.
- Resolve action metadata from `result.action.{name}`.
- Render GET actions as links and non-GET actions as inline forms.
- Preserve standard hidden context for form-rendered actions.

### Detailed Tasks

- [x] Add renderer support for namespace and HTML-compatible notation.
- [x] Add executable spec coverage for GET and POST actions.
- [x] Document baseline attributes in the widget spec.
- [x] Use the widget from the notice-board result pages.

### Closure

WEB-58 separates operation/result actions from navigation links. `nav-list`
continues to cover page navigation, while `action-group` covers framework-owned
operation result actions.

### Inputs

- `docs/spec/textus-widget.md`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/post-notice__200.html`

---

## WEB-59 — Detail Return Context

Status: DONE

### Goal

Keep list/search-to-detail flows usable by carrying a return target into detail
pages without introducing template control syntax.

### Scope

- Treat `return.href` as standard hidden/page context.
- Convert `return.href` into `result.action.return` for result templates.
- Let detail templates render return actions through `textus:action-group`.
- Validate the notice-board search -> detail flow still works.

### Detailed Tasks

- [x] Add `return.href` to hidden context keys.
- [x] Add result return action metadata from `return.href`.
- [x] Add executable spec coverage for the return action.
- [x] Add return context to notice-board search detail links.

### Closure

WEB-59 gives Static Form detail pages a minimal return-navigation mechanism.
The mechanism stays property based and framework-owned, so static HTML does not
need procedural routing logic.

### Inputs

- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/search-notices__200.html`
- `textus-sample-app/web/notice-board/get-notice__200.html`

---

## WEB-60 — Widget Attribute Parser Recheck

Status: DONE

### Goal

Reduce fragility in static widget markup before more widgets depend on richer
attribute values.

### Scope

- Accept both double-quoted and single-quoted widget attributes.
- Keep URL values with `:` characters intact in navigation item parsing.
- Preserve existing widget notation compatibility.

### Detailed Tasks

- [x] Replace the double-quote-only widget attribute parser.
- [x] Rework `nav-list` item splitting so URL colons are not treated as
      structural delimiters.
- [x] Add executable spec coverage for single-quoted attributes and URL colons.
- [x] Document the supported baseline.

### Closure

WEB-60 keeps the current lightweight parser but removes the immediate hazards
seen in static HTML fragments. A full HTML parser remains outside this phase.

### Inputs

- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `docs/spec/textus-widget.md`

---

## WEB-61 — Static Form Result Convention Recheck

Status: DONE

### Goal

Reconfirm that Static Form Web App remains convention-first after the recent
widget additions.

### Scope

- Keep `xxx__200.html` and exact `xxx__{status}.html` templates as the primary
  operation result mechanism.
- Keep common `__400.html`, `__500.html`, and `__error.html` templates as the
  fallback mechanism.
- Keep descriptor result templates supplemental.
- Ensure result pages can use hidden context, action-group, nav-list, and
  detail widgets without procedural template logic.

### Detailed Tasks

- [x] Review existing executable coverage for exact status, success/error, and
      common status templates.
- [x] Record the current convention-first result template contract here.
- [x] Validate the sample result flow with action-group, return context,
      nav-list, result-table/card-list, and description-list widgets.

### Closure

WEB-61 closes the current Static Form result-template pass. The next work can
continue from concrete missing capabilities rather than reopening the basic
transition convention.

### Inputs

- `docs/phase/phase-12-checklist.md`
- `docs/spec/textus-widget.md`
- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`

---

## WEB-62 — Action Metadata Normalization

Status: DONE

### Goal

Clarify and implement how framework-generated actions and application-provided
operation result actions merge into the action metadata consumed by widgets.

### Scope

- Keep indexed, named, and primary action property projections.
- Treat `await`, `detail`, and `return` as framework-generated fallback
  actions.
- Let application-provided JSON actions override framework defaults when they
  use the same action names/properties.

### Detailed Tasks

- [x] Reorder action property construction so framework actions are fallback
      values.
- [x] Add executable spec coverage for app-provided action override.
- [x] Document the normalization and collision rule.

### Closure

WEB-62 keeps action metadata predictable as widgets and operation results grow:
framework actions provide useful defaults, but application result metadata is
authoritative when it supplies explicit actions.

### Inputs

- `src/main/scala/org/goldenport/cncf/http/FormResultMetadata.scala`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `docs/spec/textus-widget.md`

---

## WEB-63 — Action Group JSON Source Support

Status: DONE

### Goal

Let `textus:action-group` render operation result action arrays directly,
without requiring templates to know the projected property names.

### Scope

- Support `source="result.body.actions"` and similar JSON array sources.
- Parse action objects using the same action metadata parser.
- Preserve existing `actions="await,detail"` property-prefix behavior.

### Detailed Tasks

- [x] Expose action JSON parsing through `FormResultMetadata.Action`.
- [x] Add action-group JSON source rendering.
- [x] Add executable spec coverage for JSON source rendering.
- [x] Document the `source` attribute.

### Closure

WEB-63 gives Static Form pages a more direct path from operation result JSON to
action widgets while keeping the existing property projection contract.

### Inputs

- `src/main/scala/org/goldenport/cncf/http/FormResultMetadata.scala`
- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `docs/spec/textus-widget.md`

---

## WEB-64 — Detail Return Parameter Encoding

Status: DONE

### Goal

Avoid embedding raw nested URLs into detail links when a list/search page wants
to pass return context into a detail page.

### Scope

- Add `detail-param-{name}` attributes for result table and card-list detail
  actions.
- Expand record placeholders in parameter values.
- URL encode parameter names and values before appending them to `detail-href`.
- Use the encoded mechanism in the notice-board sample.

### Detailed Tasks

- [x] Add detail parameter appending in record detail link rendering.
- [x] Add executable spec coverage for encoded return parameters.
- [x] Replace raw sample `return.href` query construction.
- [x] Update sample checks to expect encoded return context.

### Closure

WEB-64 keeps Static Form detail navigation URL-safe while preserving the simple
static HTML contract.

### Inputs

- `src/main/scala/org/goldenport/cncf/http/StaticFormAppRenderer.scala`
- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/web/notice-board/search-notices__200.html`
- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`

---

## WEB-65 — Result Template Smoke Matrix

Status: DONE

### Goal

Tie the convention-first Static Form result template contract to repeatable
sample smoke checks.

### Scope

- Keep focused renderer specs as the exact convention matrix for
  operation-specific, common, success/error, and descriptor fallback templates.
- Keep sample scripts as runtime smoke checks for the public app result pages,
  action widgets, detail navigation, return context, and packaged routes.

### Detailed Tasks

- [x] Reuse existing renderer specs for exact status/success/error/common
      result template precedence.
- [x] Extend sample flow checks to cover action-group and encoded return
      context.
- [x] Keep packaging checks in the smoke matrix for result template files and
      route mappings.

### Closure

WEB-65 makes the current smoke matrix explicit: renderer specs cover
convention precedence, while `textus-sample-app` scripts cover the packaged
runtime behavior.

### Inputs

- `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala`
- `textus-sample-app/scripts/check-static-form-app-flow.sh`
- `textus-sample-app/scripts/check-static-form-result-assets.sh`
- `textus-sample-app/scripts/check-web-packaging.sh`

---

## Deferred / Next Phase Candidates

- SPA hosting as a separate mode beyond Static Form Web App plus islands.
- Wireframe DSL and UI generation.
- Public JavaScript SDK.
- Advanced dashboard visualization and SVG rendering.
- External API gateway integration.
- Full Bootstrap theme/customization beyond the local Bootstrap 5 baseline.
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

- WEB-01 through WEB-65 are marked DONE, or explicitly deferred to a later
  phase.
- `phase-12.md` summary checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
