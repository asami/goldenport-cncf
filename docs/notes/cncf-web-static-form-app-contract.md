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

## Execution Model

REST operation execution remains the only business execution path.

HTML FORM submit is a browser-native execution layer:

- `POST /form/{component}/{service}/{operation}` accepts
  `application/x-www-form-urlencoded` or `multipart/form-data`.
- the request is converted to operation input.
- the operation is executed through the REST execution backbone.
- success and error outcomes resolve to Web HTML result pages.

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
