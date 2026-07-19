# Phase 42 - Static Web Server Rendering Contract

Stage Status:
- Current status: ACTIVE
- Current step: Aggregate command forms
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

## 5. Resume Point

Bind aggregate command forms with server validation and Post/Redirect/Get.
