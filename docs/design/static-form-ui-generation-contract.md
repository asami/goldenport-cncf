# Static Form UI Generation Contract

## Purpose

This note defines the Phase 21 WN-09 contract for generated Web/UI surfaces.
It does not define a new DSL and does not require a generator implementation.
The contract fixes what future generators may produce and how that output
coexists with hand-written Static Form pages.

Generated UI output is ordinary server-rendered Bootstrap 5 and Textus widget
markup. It must use the same runtime routes, form actions, result templates,
asset completion, authorization, and no-JS behavior as hand-written pages.

## Inputs

Generation may use these existing metadata sources:

- CML operation, parameter, result, and schema metadata.
- `WebDescriptor.pages` for page title, heading, subtitle, field order, submit
  labels, and page-level control overrides.
- `WebDescriptor.form` for operation exposure, result template selection,
  stay-on-error, redirects, assets, and form control overrides.
- `WebDescriptor.admin` for admin surface fields and total-count policy.
- View/search metadata for summary/detail field sets, searchable fields,
  filter fields, sortable fields, default sort, paging, and search modes.
- Operation result action metadata for action groups, confirmation actions,
  job panels, and return links.

Hand-written static templates are authoritative. Generated UI is a fallback or
scaffold when no static template exists, or when an explicit later generator
chooses to emit files from this contract.

Reusable shell composition is provided by the WN-10 `WEB-INF` convention:

- app-level default layout: `WEB-INF/layouts/default.html`;
- explicit descriptor selection: `apps[].layout`, `pages.*.layout`, and
  `form.*.layout`;
- `layout: none` disables wrapping;
- `${content}` is the layout content slot;
- `${partial.name}` resolves private `WEB-INF/partials` HTML files.

Subsystem Web composition is provided by the WN-12 component-Web contract:

- Component Web apps opt in with `apps[].composition: article`.
- `pages.*.mode: article` embeds the Component page as `${content}` in the
  Subsystem shell.
- Form result templates follow the same article composition rule when the
  selected result page is article-mode.
- `pages.*.mode: screen` lets login/logout/account-style pages own the full
  screen and bypass article embedding.
- In multi-Component Subsystems and deemed-Subsystems, the shared shell owner is
  explicit descriptor state:
  `shell.component`, optional `shell.app`, and optional `shell.layout`.
  CNCF must not guess a shell from an unrelated Component Web root.
- Subsystem/deemed-subsystem `WEB-INF` layouts and shell partials provide the
  shared header, footer, sidebar, and navigation. Component pages may still use
  their own local includes inside the article content.
- `textus-blog` is the first driver: its CAR acts as a deemed-subsystem, Blog
  layouts own the shell, and Blog static/result fragments stay article content.

## Document Surface

Static Form Web exposes component documentation through a `document` surface.
This surface separates generated technical metadata from human-authored
component documents:

- `Document` is the top-level Web page for a component or system.
- `Specification` is the generated CNCF view derived from component, service,
  operation, schema, OpenAPI, and projection metadata.
- `User Guide` is a component-packaged task-oriented document for users.
- `Reference Manual` is a component-packaged human-authored reference that
  complements the generated specification.

Canonical routes are:

- `/web/system/document`
- `/web/system/document/specification`
- `/web/system/document/specification/openapi.json`
- `/web/{component}/document`
- `/web/{component}/document/specification`

Component-packaged documents are discovered from private component Web roots
under `docs/` or `documents/`. Typical files are `user-guide.md`,
`reference-manual.md`, and optional packaged `specification.md` or HTML/PDF
variants. The older term `manual` is not the canonical Web surface name; it is
reserved for human-authored packaged manuals such as a Reference Manual.

## Page Kinds

Supported generated page kinds are:

- `list`: search/filter controls, result table or card list, result summary,
  pagination, empty state, detail/create actions.
- `detail`: breadcrumb/nav, record or description card, action row, return
  context, optional association/media/tag sections supplied by the runtime.
- `form`: schema-driven Bootstrap form controls, grouped in cards or sections,
  validation summary, field validation, hidden context, submit/cancel actions.
- `result`: status alert, submitted/result summary, action group, optional job
  panel, optional development debug panel.
- `dashboard`: summary cards, nav/list groups, diagnostics/status cards, and
  responsive tables for operational detail.
- `job-result`: job ticket, status/result/error panels, await/result/my-jobs
  actions, and system-admin handoff when allowed.
- `error`: status alert, user-facing message, optional structured debug detail
  only in development mode.

## Composition Rules

Generated UI must use Bootstrap 5 primitives and Textus widgets as the output
vocabulary:

- Page shell: Bootstrap container, breadcrumb/nav, heading/subtitle, and action
  row.
- List/search: Bootstrap search card, active filter badges, clear link,
  `textus:result-table` or `textus:card-list`, `textus:pagination`, and
  `textus:empty-state`.
- Detail: `textus:record-card`, `textus:description-list`, Bootstrap cards,
  `textus:action-group`, and return context.
- Form: Bootstrap `card`, `row g-*`, `form-control`, `form-select`,
  `form-text`, validation alerts, and `textus:hidden-context`.
- Result: `textus:alert`, `textus:status-badge`, `textus:action-group`,
  `textus:job-panel`, and development debug panel when configured.
- Dashboard: `textus:summary-card`, Bootstrap cards, `list-group`,
  responsive tables, badges, alerts, and action rows.

All generated pages must work without JavaScript. JavaScript may enhance modal
confirmation, picker, refresh, or future Island Architecture behavior, but it
must not be required for navigation, form submission, search, or result
inspection.

## Progressive Enhancement Boundary

Static Form Web App keeps server-rendered HTML as the authoritative behavior.
Application-local JavaScript may enhance a page when the same page remains
usable through ordinary links, forms, and rendered results without JavaScript.
This includes scripts such as `textus-blog`'s `blog.js`, which looks for
application `data-*` hooks and improves tag suggestions, picker dialogs, and
list/detail switching.

This page-local enhancement is not the same as CNCF Island Architecture:

- page-local JavaScript is owned by the app or component, loaded as an app
  asset, and is not interpreted by CNCF core;
- future Island Architecture would define explicit island names, props,
  lifecycle, asset dependencies, duplicate-initialization rules, and fallback
  policy for reusable JavaScript components;
- SPA hosting gives navigation, state, and primary rendering control to a
  client application and is a separate WN-14 deployment boundary.

WN-13 does not add `data-textus-island`, a core island loader, a registry,
WebDescriptor island schema, or island dependency resolution. Those remain
deferred until a real reusable JavaScript component contract is needed. Until
then, app-local progressive enhancement is the preferred extension point.

## SPA And API Gateway Boundary

WN-14 keeps SPA hosting and API gateway behavior outside the Static Form UI
generation contract. Generated UI still targets server-rendered
Bootstrap/Textus pages with no-JS links, forms, result pages, and optional
page-local enhancement.

When an application needs a full SPA, the SPA should be treated as a separate
frontend or future explicit hosting mode that calls CNCF through REST and Form
API. The generated UI contract does not add a client router, application-wide
state store, SPA catch-all route, gateway runtime, or WebDescriptor SPA schema.

The boundary is:

- generated/list/detail/form/result pages stay Static Form by default;
- REST API is the execution surface for domain operations;
- Form API is the schema and validation preparation surface;
- auth/session/UoW/authorization stay on CNCF runtime paths;
- admin/system endpoints keep their existing protected surface;
- future SPA assets must be scoped separately from Static Form and component
  app assets.

## Boundaries

WN-09 explicitly does not implement:

- a standalone wireframe DSL;
- file generation;
- a new runtime renderer separate from `StaticFormAppRenderer`;
- reusable header/footer/nav/sidebar partials, implemented by WN-10 through
  the `WEB-INF` layout/partial contract;
- component-owned admin page discovery beyond the WN-12 article/screen
  composition contract;
- Island Architecture runtime or SPA/API gateway hosting. WN-13 defines only
  the progressive-enhancement boundary, and WN-14 covers SPA/API gateway
  deployment modes.

The older wireframe DSL notes remain historical draft references for future
generation work. Future generators must emit this Bootstrap/Textus contract
rather than bypass it with a separate visual system.
