# Phase 42 - Static Web Server Rendering Contract

Stage Status:
- Current status: ACTIVE
- Current step: Aggregate command form CSRF enforcement
- Owner: CNCF Web runtime and Static Web renderer.

status = active

## 1. Purpose

Make one server-rendered Static Web document the normal application read path,
without browser REST fan-out for execution context or primary page content.

## 2. Scope

- Bind component page-context queries to a typed page View.
- Render page View data through server-side Textus widgets.
- Resolve locale messages and response language from one ExecutionContext.
- Bind aggregate command forms with server validation and PRG.
- Define subject-safe page cache behavior.

## 3. Boundaries

- REST and Form API remain machine-facing integration surfaces.
- JavaScript remains available for bounded progressive enhancement.
- Components do not introduce private template engines or browser locale
  authority.

## 4. Current Evidence

- `WebPageContext.view` carries a typed `Record` page model.
- Nested `pageContext.view.*` widget sources render on the server.
- The same model is safely embedded for progressive enhancement.
- The HTTP integration specification proves locale-correct HTML and primary
  View content without browser REST bootstrap.
- Component message catalogs are selected from the resolved request execution
  locale and projected as server-only `message.*` template properties.
- Generated Static Web pages carry typed response-language metadata so
  `html[lang]`, `Content-Language`, timezone, and page messages share one
  execution projection.
- `textus:operation-form` renders operation-schema controls inside Static Web
  pages and submits aggregate commands through the existing server-validated
  `/form` ingress. Executable coverage verifies hidden context and configured
  Post/Redirect/Get without `/form-api` or JavaScript.
- Redirecting operation forms can carry an explicitly declared, bounded
  message key in a short-lived component-scoped cookie. The next Static Web
  response resolves it through the request locale catalog, renders
  `textus:flash`, and expires the cookie without browser REST hydration or
  server-local flash state.

## 5. Resume Point

Complete session-backed CSRF enforcement, then define page cache/privacy
policy.
