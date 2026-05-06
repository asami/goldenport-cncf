# Static Form Web App Bootstrap 5 Screen Guide

## Purpose

This note is a practical screen-construction guide for CNCF Static Form Web
Apps and framework-generated admin pages that use the local Bootstrap 5
baseline.

The goal is not to create a new visual system. Static Form Web Apps should use
ordinary Bootstrap 5 layout and components first, then add small application CSS
only for domain-specific details that Bootstrap does not cover.

Related formal/background documents:

- `docs/design/web-layer.md`
- `docs/design/static-form-ui-generation-contract.md`
- `docs/spec/textus-widget.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `docs/notes/cncf-web-static-form-app-contract.md`

## Asset Baseline

Static Form Web App pages should use the local CNCF Web assets:

```text
/web/assets/bootstrap.min.css
/web/assets/bootstrap.bundle.min.js
/web/assets/textus-widgets.css
/web/assets/textus-widgets.js
```

Do not depend on CDN Bootstrap assets in framework-generated pages or ordinary
Static Form Web App templates. Pages should remain usable in offline or
intranet deployments.

Fragment templates are normally wrapped by the built-in Bootstrap page shell.
Full document templates that opt out of that shell are responsible for
including equivalent local assets.

## Page Structure

Use a simple Bootstrap page shape:

```html
<main class="container-fluid px-3 px-md-4 py-4">
  <header class="mb-4">
    <nav aria-label="breadcrumb">
      <ol class="breadcrumb mb-2">
        <li class="breadcrumb-item"><a href="/web/system/dashboard">Dashboard</a></li>
        <li class="breadcrumb-item active" aria-current="page">Posts</li>
      </ol>
    </nav>
    <div class="d-flex flex-column flex-md-row justify-content-between gap-3">
      <div>
        <h1 class="h3 mb-1">Posts</h1>
        <p class="text-body-secondary mb-0">Manage public content.</p>
      </div>
      <div class="d-flex flex-wrap gap-2">
        <a class="btn btn-primary" href="./new">New</a>
        <a class="btn btn-outline-secondary" href="./help">Help</a>
      </div>
    </div>
  </header>

  <section class="card">
    <div class="card-body">
      ...
    </div>
  </section>
</main>
```

Use:

- `container-fluid px-3 px-md-4 py-4` for admin and operational pages.
- `container` for text-heavy public pages with a narrow reading width.
- `row g-*` and `col-*` for layout.
- `d-flex flex-wrap gap-*` for action groups.
- `card` for a primary panel, repeated item, or operational section.

Avoid:

- custom CSS grid for ordinary page layout when Bootstrap `row` / `col` works.
- fixed-width panels that overflow on small screens.
- nested cards unless the inner card is a repeated item with its own boundary.
- raw paragraphs containing long lists of links.

## Navigation

Use Bootstrap navigation primitives instead of ad hoc link dumps:

- `breadcrumb` for current location.
- `nav nav-pills flex-wrap gap-2` for page-level navigation.
- `list-group` for a vertical set of related navigation targets.
- `btn` or `btn-outline-*` for commands.

Example:

```html
<nav class="nav nav-pills flex-column flex-sm-row gap-2">
  <a class="nav-link active" href="/web/system/admin">Admin</a>
  <a class="nav-link" href="/web/admin/tags">Tags</a>
  <a class="nav-link" href="/web/system/admin/assembly/warnings">Warnings</a>
</nav>
```

Keep navigation and mutation actions visually separate. A tab, nav pill, or
breadcrumb should not look like a destructive command.

## Job Results And Development Debugging

Asynchronous Command results should use the framework job UX instead of custom
status pages. A result template may include:

```html
<textus:job-panel actions="result,await,jobs"></textus:job-panel>
```

If a result page returns a job id and the template does not include any job
widget, the framework appends the standard application job panel. The primary
user-facing links are:

- `/web/{app}/jobs/{jobId}` for the job result.
- `/web/{app}/jobs` for the current user's jobs in that application.
- `/form/{app}/{service}/{operation}/jobs/{jobId}/await` for the form-scoped
  await action.

In development mode, Form/HTML operation responses that carry execution
metadata may include a collapsed execution debug panel at the bottom of the
page. The panel is diagnostic only; production pages must not depend on it.
CallTree capture for development HTML responses is separate from application
job creation, so queries and synchronous commands are not turned into user
jobs merely because the debug panel is enabled.

## Forms

Static Form App forms should map schema fields to Bootstrap form controls:

```html
<form method="post" action="./save" class="card">
  <div class="card-body">
    <div class="row g-3">
      <div class="col-12 col-md-6">
        <label class="form-label" for="title">Title</label>
        <input class="form-control" id="title" name="title" value="">
        <div class="form-text">Short public title.</div>
      </div>
      <div class="col-12 col-md-6">
        <label class="form-label" for="status">Status</label>
        <select class="form-select" id="status" name="status">
          <option value="draft">Draft</option>
          <option value="published">Published</option>
        </select>
      </div>
      <div class="col-12">
        <label class="form-label" for="summary">Summary</label>
        <textarea class="form-control" id="summary" name="summary" rows="3"></textarea>
      </div>
    </div>
  </div>
  <div class="card-footer d-flex flex-wrap justify-content-end gap-2">
    <a class="btn btn-outline-secondary" href="./">Cancel</a>
    <button class="btn btn-primary" type="submit">Save</button>
  </div>
</form>
```

Rules:

- Small screens use one column.
- `md+` screens may use semantic columns.
- Every input has a visible `label`.
- Field help uses `form-text`.
- Validation uses `is-invalid`, `invalid-feedback`, and an `alert` summary.
- Button rows use `d-flex flex-wrap gap-2`, not fixed widths.

Do not hide labels just to save space. Dense admin pages still need stable
labels for scanning and accessibility.

## Tables And Lists

Dense result sets should use responsive Bootstrap tables:

```html
<div class="table-responsive">
  <table class="table table-sm table-hover align-middle mb-0">
    <thead>
      <tr>
        <th scope="col">Name</th>
        <th scope="col">Status</th>
        <th scope="col" class="text-end">Actions</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td><a href="./detail?id=...">example</a></td>
        <td><span class="badge text-bg-success">Active</span></td>
        <td class="text-end">
          <div class="d-inline-flex flex-wrap gap-1">
            <a class="btn btn-sm btn-outline-primary" href="./edit?id=...">Edit</a>
          </div>
        </td>
      </tr>
    </tbody>
  </table>
</div>
```

Rules:

- Always wrap tables in `.table-responsive`.
- Use `table-sm` for operational/admin lists.
- Use `align-middle` when rows contain buttons or badges.
- Put row actions in a wrapping flex group.
- Use `list-group` or `card` rows when the content is not naturally tabular.

## Search Forms And Results

Generated and hand-written Static Form search pages should use the CNCF Web
search convention:

- `q` is the canonical full-text search parameter.
- Existing application-specific `text` inputs remain compatibility aliases.
- Filter inputs should be generated only from visible/search-facing fields.
- Sort controls should expose only configured or visible sortable fields.
- Semantic and hybrid search modes may be shown as capabilities, but they must
  return deterministic unsupported/configuration feedback unless a semantic
  backend is configured.

Use Bootstrap cards and rows for the search controls:

```html
<section class="card mb-3">
  <div class="card-body">
    <form method="get" class="row g-2 align-items-end">
      <div class="col-12 col-md-5">
        <label class="form-label" for="q">Search</label>
        <input class="form-control" id="q" name="q" value="">
      </div>
      <div class="col-6 col-md-3">
        <label class="form-label" for="sort">Sort</label>
        <select class="form-select" id="sort" name="sort">...</select>
      </div>
      <div class="col-6 col-md-2">
        <button class="btn btn-primary w-100" type="submit">Search</button>
      </div>
    </form>
  </div>
</section>
```

Show active filters as compact badges or chips and provide a clear-search link.
Result summaries, pagination, and empty states should stay server-rendered so
the same page works without JavaScript.

## Generated Page Composition

Generated Static Form UI uses the same Bootstrap/Textus output as hand-written
templates. Generation is a fallback or scaffold path; a hand-written static
template with the same route/result convention remains authoritative.

The standard generated page kinds are:

- list: search card, active filter chips, result table or card list,
  pagination, empty state, detail/create actions.
- detail: breadcrumb/nav, record or description card, action row, return
  context, and runtime-provided relationship sections.
- form: schema-driven Bootstrap controls, validation summary, field validation,
  hidden context, submit/cancel actions.
- result: status alert, result/submitted summary, action group, job panel when
  a job is returned, and development debug panel when enabled.
- dashboard: summary cards, nav/list groups, diagnostics cards, badges, alerts,
  and responsive tables.

Generated UI may use CML schema/operation metadata, `WebDescriptor` page/form
and admin controls, View/search metadata, and operation result actions. It must
not introduce a separate visual DSL or JavaScript-only behavior.

## Result And Status Feedback

Use Bootstrap feedback components:

- `alert alert-success` for successful completion.
- `alert alert-warning` for recoverable warnings.
- `alert alert-danger` for errors.
- `alert alert-info` for empty or explanatory states.
- `badge text-bg-*` for compact state labels.
- `text-body-secondary` for secondary metadata.

Do not show framework errors as unstyled plain text when they are part of a
normal user/operator flow.

## Dashboard And Operational Panels

For dashboard-style pages, prefer Bootstrap cards and compact rows:

```html
<div class="row g-3">
  <div class="col-12 col-lg-6">
    <section class="card h-100">
      <div class="card-header d-flex justify-content-between align-items-center">
        <span>Jobs</span>
        <span class="badge text-bg-success">healthy</span>
      </div>
      <div class="list-group list-group-flush">
        <div class="list-group-item">
          <div class="d-flex justify-content-between gap-3">
            <span>Running</span>
            <span class="fw-semibold">4</span>
          </div>
          <div class="progress mt-2" role="progressbar" aria-valuenow="40" aria-valuemin="0" aria-valuemax="100">
            <div class="progress-bar" style="width: 40%"></div>
          </div>
        </div>
      </div>
    </section>
  </div>
</div>
```

Use custom CSS only for small presentation pieces that Bootstrap does not
provide, such as a tiny sparkline. The surrounding layout should still be
Bootstrap `card`, `row`, `col`, `list-group`, `progress`, and `badge`.

## Textus Widgets

Prefer Textus widgets when a template is data-driven and the widget already
expresses the required shape:

- `textus:result-table` for ordinary tabular result data.
- `textus:property-list` for detail/property display.
- `textus:error-panel` for error projection.
- `textus:action-group` for related actions.
- `textus:confirm-action` for destructive or important actions that need a
  Bootstrap modal with a no-JS fallback.
- `textus:nav-list` for simple navigation.
- `textus:card`, `textus:record-card`, and `textus:card-list` for repeated
  summaries and detail cards.
- `textus:summary-card`, `textus:alert`, `textus:empty-state`, and
  `textus:status-badge` for metric, status, warning, and empty states.

Widgets must render ordinary Bootstrap-compatible HTML. If a widget cannot
produce the expected Bootstrap structure, improve the shared widget rather than
adding a one-off application layout convention.

## Reusable Shell Parts

Use `WEB-INF` for shared page shell parts that should not be directly served as
static files:

- `WEB-INF/layouts/default.html` for the app shell.
- `WEB-INF/partials/header.html`, `navigation.html`, `sidebar.html`, and
  `footer.html` for reusable page regions.
- `WEB-INF/partials/{page}/{name}.html` when one page needs a local override.

Layouts should stay ordinary Bootstrap 5 HTML. Use `${content}` for the page
content slot and `${partial.header}` / `${partial.navigation}` /
`${partial.sidebar}` / `${partial.footer}` for shell parts. Hand-written pages
can include the same parts with `textus:include name="navigation"` or the
HTML-compatible `textus-include` notation.

Prefer HTML partials in v1. Older Jade/Pug-style layouts are historical
reference material, not the Static Form runtime contract.

Subsystem-owned Web shells can compose Component Web content when the Component
Web app explicitly opts in with `apps[].composition: article`. In that mode,
ordinary Component pages are rendered as article content inside the Subsystem
layout and shell partials. Pages such as login, logout, or account flows should
declare `pages.<name>.mode: screen` when they need to own the full page rather
than appear inside the shared article region.

Use this ownership split:

- Subsystem/deemed-subsystem: header, footer, sidebar, global navigation,
  auth/session affordances, and common page shell.
- Component CAR: page content, local content partials, component assets, and
  component-specific form/result fragments.

## Responsive Rules

Static Form Web App pages should work at desktop, tablet, and phone widths.

Use these defaults:

- `col-12` on small screens.
- `col-md-*` / `col-lg-*` only when the content benefits from columns.
- `flex-wrap` on action rows.
- `table-responsive` around all tables.
- `gap-*` utilities instead of custom margins between repeated controls.
- no viewport-scaled font sizes.

Check narrow screens for:

- overflowing button groups;
- table overflow breaking the whole page;
- labels detached from inputs;
- large secondary buttons dominating the panel;
- visible text clipped inside buttons or badges.

## Static Form Template Checklist

Before considering a Static Form App screen done:

- The page uses local Bootstrap/Textus assets.
- The first viewport shows the actual workflow, not a landing page.
- The page has a clear title, navigation, primary content, and actions.
- Forms use `form-control`, `form-select`, `form-label`, and validation
  feedback.
- Tables are inside `table-responsive`.
- Action groups wrap on narrow screens.
- Status, error, warning, and empty states use Bootstrap feedback components.
- The template does not introduce a custom grid when Bootstrap grid would work.
- Wire paths, field names, and result property names remain unchanged.
