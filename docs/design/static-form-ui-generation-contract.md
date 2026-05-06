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

## Boundaries

WN-09 explicitly does not implement:

- a standalone wireframe DSL;
- file generation;
- a new runtime renderer separate from `StaticFormAppRenderer`;
- reusable header/footer/nav/sidebar partials, implemented by WN-10 through
  the `WEB-INF` layout/partial contract;
- component-owned admin page discovery/integration, which is WN-12;
- Island Architecture or SPA/API gateway hosting, which are WN-13 and WN-14.

The older wireframe DSL notes remain historical draft references for future
generation work. Future generators must emit this Bootstrap/Textus contract
rather than bypass it with a separate visual system.
