# CNCF Static Form App Contract

status = draft

## Purpose

This note defines the first WEB-02 contract for REST/Form API exposure and the
Static Form App mechanism.

It is intentionally small. The first runtime hook must support the read-only
Dashboard validation path for `textus-sample-app` without hard-coding Dashboard
as a special server feature.

## Path Model

Operation REST paths use normalized Component / Service / Operation segments:

```text
/{component}/{service}/{operation}
```

Examples:

```text
/notice-board/notice/post-notice
/notice-board/notice/search-notices
```

Plain HTML FORM submit paths are under `/form`:

```text
POST /form/{component}/{service}/{operation}
```

JSON Form API paths are under `/form-api` so they remain distinct from plain
HTML FORM submission:

```text
GET  /form-api/{component}/{service}/{operation}
POST /form-api/{component}/{service}/{operation}/validate
```

User-facing Static Form App HTML paths are under `/web`:

```text
/web/{componentName}
/web/{componentName}/{page}
/web/{componentName}/dashboard
/web/{componentName}/dashboard/state
```

The component name selects the application context. Built-in operational app
contexts such as `console` and `manual` may also be mounted under `/web`.

Subsystem-wide operational pages use a separate namespace:

```text
/web/system/dashboard
/web/system/dashboard/state
```

This avoids mixing the Subsystem Dashboard with a Component Dashboard.

Subsystem Dashboard and Component Dashboard present the same class of
information, but with different data scopes and screen composition:

- Subsystem Dashboard scopes data to the running subsystem.
- Component Dashboard scopes data to one component.
- Subsystem Dashboard emphasizes cross-component overview.
- Component Dashboard emphasizes local services and operations.
- Dashboard HTML may poll its matching `/state` endpoint at about one-second
  intervals to present current runtime state graphically.
- Dashboard state separates HTML-level request information from ActionCall-level
  execution information. The dashboard uses these for a health overview; detailed
  configuration and performance analysis are linked to dedicated admin or
  performance pages instead of being embedded inline.

Calltree and retained execution history are detailed performance-analysis
surfaces. Dashboard state exposes ActionCall count/error summaries; the System
Performance page links to the existing admin execution operations:

```text
/form/admin/execution/history
/form/admin/execution/calltree
```

Web pages must reuse the existing retained execution history and calltree
semantics instead of defining a separate dashboard-only vocabulary.

Assembly warnings are also visible at the overview level. Dashboard state
exposes only the warning count and link:

```text
assembly.warnings.count
links.assemblyWarnings = /form/admin/assembly/warnings
```

Detailed warning records and the resolved assembly report remain existing admin
operation surfaces:

```text
/form/admin/assembly/warnings
/form/admin/assembly/report
```

## Execution Model

REST operation execution remains the only business execution path.

HTML FORM submit is a browser-native execution layer:

- `POST /form/{component}/{service}/{operation}` accepts
  `application/x-www-form-urlencoded` or `multipart/form-data`.
- the request is converted to operation input.
- the operation is executed through the REST execution backbone.
- success and error outcomes resolve to Web HTML result pages.

The first runtime hook provides a generic component operation entry:

```text
GET  /form/{component}
GET  /form/{component}/{service}/{operation}
POST /form/{component}/{service}/{operation}
```

The `GET` routes render a Bootstrap-backed operation index and operation form.
The `POST` route converts submitted form data to operation input, invokes the
matching REST execution path, and renders an HTML result page. This hook is
generic CNCF behavior and does not contain sample-app-specific page content.

JSON Form API is an input preparation layer:

- `GET /form-api/...` returns form metadata for an operation.
- `POST /form-api/.../validate` validates input but does not execute the operation.
- operation execution still uses the REST operation path.

Static Form App may use JSON Form API when it needs schema-driven forms, but
plain HTML pages can submit directly through `/form/...`.

## Bootstrap 5 Assets

Static Form App HTML uses Bootstrap 5 by default.

The runtime must not depend on CDN access. Bootstrap assets are served by the
CNCF web server under `/web/assets`, starting with:

```text
/web/assets/bootstrap.min.css
/web/assets/bootstrap.bundle.min.js
```

This makes the same Form App pages usable from desktop, tablet, and smartphone
browsers in offline or closed-network environments. Application-specific pages
may add local CSS or JavaScript, but the baseline layout, responsive grid,
cards, buttons, tables, and form controls should assume Bootstrap 5 classes are
available.

## Static Form App Model

A Static Form App is a web app instance made from static pages and convention
based result resolution.

The initial app registry is conceptual:

```text
console -> controlled operation execution
manual  -> read-only reference navigation
```

Phase 12 implements the shared web route first. Subsystem Dashboard and
Component Dashboard are separate read-only operational surfaces backed by the
same metadata class. Component Dashboard is mounted under
`/web/{componentName}/dashboard` when a component needs a component-local view.

## Dashboard Validation Contract

The read-only Dashboard metadata class must be able to obtain or render:

- runtime health
- runtime version
- loaded components
- services per component
- operations per service

For the first validation pass, `textus-sample-app` with `notice-board.cml` is
the fixture. Subsystem Dashboard must show the NoticeBoard component among the
loaded components. Component Dashboard for NoticeBoard must show its Notice
service operations without exposing mutation controls inline.

## Response Model

REST and JSON Form API responses use the Phase 12 response envelope:

```json
{"result": {}}
```

or:

```json
{"error": {"code": "VALIDATION_ERROR", "message": "Validation failed"}}
```

Async operation execution may return a job envelope later:

```json
{"job": {"id": "job-123", "status": "accepted"}}
```

Plain HTML FORM submit responses are HTML-oriented:

- success resolves to a success page or redirects to a success page.
- validation error resolves to an error page or redirects to an error page.
- system error resolves to an error page or returns an error response.

## Relationship With CLI And Meta Projections

Static Form App, REST execution, and JSON Form API do not replace existing CLI
or meta projections.

The boundary is:

- CLI remains the command-line projection of Component / Service / Operation
  metadata and execution results.
- Meta projections such as help, describe, schema, and OpenAPI remain
  reference/projection surfaces.
- Static Form App is the browser-native HTML projection for form entry,
  result pages, dashboard, console, and manual pages.
- REST remains the operation execution backbone shared by browser and
  non-browser clients.
- JSON Form API is an input-preparation API for web/form clients and must not
  become a separate execution path.

These surfaces may share the same normalized component/service/operation
metadata, but they must not share presentation-specific response contracts.
For example, a CLI result uses the CLI `Presentable` stdout/stderr policy, a
REST call uses the JSON response envelope, and a plain HTML FORM submit resolves
to an HTML result page.

Manual/reference web pages may link to existing meta projections such as schema
or OpenAPI views, but WEB-02 does not redefine those projections.

## Result Page Template And Widgets

Static Form App result pages use a small property expansion syntax:

```html
${operation.label}
${result.status}
```

The template language intentionally does not provide control structures.
Structured views are rendered through `textus-*` widgets. The first widget
contracts are:

```html
<textus-result-view source="result.body"></textus-result-view>
<textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus-result-table>
<textus-property-list source="result"></textus-property-list>
<textus-error-panel source="result"></textus-error-panel>
```

Paging is part of the initial table contract. A table widget reads the current
page, page size, optional total count, and page navigation href template through
property paths. The default property names are:

```text
paging.page
paging.pageSize
paging.chunkSize
paging.total
paging.href
```

Total count is not requested by default. Search/list operations may require an
extra count query on DB-backed implementations, so the caller must explicitly
request total count when it is needed. When total count is absent, the widget
still renders the current page and previous/current/next navigation, but omits
total-page navigation and page-number ranges. This keeps simple operation
responses usable while allowing list/search operations to opt into total-count
paging when needed.

Display page size and continuation chunk size are separate. The default display
page size is 20 rows. The default continuation chunk size is 1000 rows. A widget
uses `paging.pageSize` for visible rows, while the Form continuation mechanism
may use `paging.chunkSize` when retaining or fetching a larger in-memory result
window.

EntitySpace currently keeps many working sets in memory, so total count is
often available with little or no additional work. That is best-effort behavior,
not a hard guarantee across all storage backends.

The `paging.href` value connects the HTML representation to the operation
execution mechanism. It is a URL template with `{page}` and `{pageSize}` tokens:

```text
/form/{component}/{service}/{operation}/continue/{id}?page={page}&pageSize={pageSize}
```

The table widget renders page links by expanding this template. For plain HTML
FORM pages, those links should re-enter the matching `/form/.../continue/{id}`
route with the requested paging values. The continuation route preserves the
operation result set server-side and returns the requested page from that result
set without re-executing the search operation. This is a good fit for the
current memory-resident working set model.

Total count opt-in uses an explicit parameter, conventionally:

```text
paging.requireTotal=true
```

## Runtime Hook Rule

The first runtime hook must be generic:

- it may add an app HTML route such as `/web/{componentName}`
- it may add a plain FORM submit route such as `/form/{component}/{service}/{operation}`
- it may add a JSON Form API route such as `/form-api/{component}/{service}/{operation}`
- it may add a subsystem HTML route such as `/web/system/dashboard`
- it must not confuse Subsystem Dashboard with Component Dashboard
- it must share metadata and response logic with later console/manual paths
- it must keep sample-specific page content outside CNCF core

This keeps Dashboard as the first validation surface while preserving the WEB-02
mechanism-first order.
