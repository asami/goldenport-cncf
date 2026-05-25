# Static Form Web App Bootstrap 5 Screen Guide

## Purpose

This note is a practical screen-construction guide for CNCF Static Form Web
Apps and framework-generated admin pages that use the local Bootstrap 5
baseline.

The primary Web application strategy remains REST-first: CNCF's application
tier exposes operations through REST, and the Web tier may choose any suitable
Web technology. Static Form Web UI is CNCF's lightweight built-in UI path for
the management console, development-time checking and debugging, prototypes,
and simple internal-use application screens.

The goal is not to create a new visual system. Static Form Web Apps should use
ordinary Bootstrap 5 layout and components first, then add small application CSS
only for domain-specific details that Bootstrap does not cover.

Related formal/background documents:

- `docs/design/web-layer.md`
- `docs/design/static-form-ui-generation-contract.md`
- `docs/spec/textus-widget.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
- `docs/notes/cncf-web-static-form-app-contract.md`

## Source Layout For Web Developers

Static Form Web App source is split by role. Edit the source files; do not edit
the generated CAR `web/WEB-INF/*.yaml` files directly.

```text
src/main/web/*.html                     public pages and result templates
src/main/web/assets/...                 public page assets
src/main/web/WEB-INF/layouts/...        private layouts copied to CAR WEB-INF
src/main/web/WEB-INF/partials/...       private partials copied to CAR WEB-INF
src/main/web/WEB-INF/widgets/...        private reusable snippets
src/main/web-inf/web.yaml               app, page, route, shell, theme, assets
src/main/web-inf/form.yaml              operation exposure and form controls
src/main/web-inf/admin.yaml             admin/debug metadata when needed
```

`src/main/web` is the authored Web application tree. Files under
`src/main/web/WEB-INF` are private Web resources like Java Web `WEB-INF`; they
are available to the renderer for layout/partial/widget composition but must
not be exposed as public pages or used as descriptor sources.

`src/main/web-inf` is the source metadata root. Cozy packages these descriptor
inputs into CAR runtime descriptors:

```text
src/main/web-inf/web.yaml   -> web/WEB-INF/web.yaml
src/main/web-inf/form.yaml  -> web/WEB-INF/form.yaml
src/main/web-inf/admin.yaml -> web/WEB-INF/admin.yaml
```

Do not place descriptor source at `src/main/web/WEB-INF/form.yaml` or
`src/main/form/form.yaml`. Those paths are intentionally not the normal Web
developer contract. If CML or generated metadata needs to contribute form or
admin information, materialize the result into `src/main/web-inf/*.yaml`
during generation before packaging.

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

Textus standard JavaScript should be split by source responsibility and may be
bundled for runtime delivery later. Do not grow `textus-widgets.js` into an
unstructured catch-all. Reusable behaviors should be introduced as named
modules such as:

```text
textus-form-validation.js
textus-capability.js
textus-job.js
textus-notification.js
textus-islands.js
```

The preferred future runtime bundle name is `textus-web.js`, but that bundle is
a delivery artifact. Source ownership remains module-based.

## JavaScript Enhancement Boundary

Static Form Web Apps may use application-local JavaScript to improve the user
experience, but JavaScript must enhance ordinary server-rendered HTML rather
than replace it as the primary behavior.

Use this pattern for app-local scripts:

- keep the page usable through links, forms, and server-rendered results without
  JavaScript;
- attach behavior through app-owned `data-*` hooks and keep selectors scoped to
  the relevant page region where practical;
- load the script as a normal app/component asset through the descriptor or
  layout/partial contract;
- treat failures as a fallback to the no-JS behavior.

Do not require CNCF to understand page-local JavaScript. A script such as
`textus-blog`'s `blog.js` is still a Static Form App enhancement when it
improves tag suggestions, image pickers, upload modals, or list/detail
navigation while preserving server-rendered forms and links.

For page-load data, prefer server rendering and Application-tier page context.
Header badges, job visibility, session display, tag summaries, and similar
screen support data should be gathered once as page context and expanded in
layout/partial templates with `${pageContext.*}` properties. Avoid page-load
Form API chains such as one call for notification count, another for job state,
and another for list content. `/form-api` should stay focused on assistance,
validation, optional refresh, async status, and editor/image-picker behavior.

Island Architecture is a later reusable JavaScript component contract. It would
need explicit island names, props, lifecycle, asset dependencies, and duplicate
initialization rules. CNCF does not currently provide a `data-textus-island`
standard attribute, island loader, island registry, or WebDescriptor island
schema. SPA hosting and API gateway boundaries are separate deployment concerns.

## Capability-Aware Write Controls

Static Form Web Apps should make read flows available as ordinary pages and
gate mutation flows at two levels:

- operation/form authorization in `src/main/web-inf/form.yaml`;
- visible HTML affordances in the page, using CNCF capability attributes.

The page-level capability controls are a user-experience layer. They prevent a
user from being led toward an action they cannot perform, but they do not
replace operation authorization.

Use capability attributes on mutation controls:

```html
<form
  method="post"
  action="/form/textus-knowledge-editor/book-editor/save-book"
  data-textus-capability="information:edit"
  data-textus-capability-mode="disable"
  data-textus-capability-policy="authenticated">
  ...
  <button class="btn btn-primary" type="submit">Save</button>
</form>
```

Supported attributes:

- `data-textus-capability`: canonical capability name, for example
  `information:edit`.
- `data-textus-capability-mode`: `hide` or `disable`.
- `data-textus-capability-policy`: `authenticated` for the current temporary
  login-based policy, or `subject` for subject capability checks.

Use `hide` for navigation links to write-only pages when anonymous users should
not see that entry point. Use `disable` for inline forms when the page should
show the available workflow but prevent submission. Keep read/detail/list links
ungated unless the information itself is protected by separate read
authorization.

Show a capability message near write-focused pages or panels:

```html
<textus:capability-message
  capability="information:edit"
  policy="authenticated"
  login="true"
  login-href="/web/textus-user-account/signin?returnTo=%2Fweb%2Fmy-app">
  Log in to edit this information.
</textus:capability-message>
```

Use generic capability vocabulary from CNCF where possible. Application-specific
capabilities are allowed only when the action is truly application-specific.
For InformationSpace applications, prefer:

- `information:import`
- `information:edit`
- `information:validate`
- `information:resolve`
- `information:confirm`
- `information:reject`
- `information:publish`

When an application needs shared Tag master management, link to the CNCF
builtin Tag application screen instead of building a local TagSpace editor. Use
`/web/tag/tags?tagSpace=<space>` for ordinary app users and keep
`/web/admin/tags` for operator/admin workflows. For example,
`textus-knowledge-editor` links to `/web/tag/tags?tagSpace=information` and
continues to use its own Information operations only for attaching/syncing tags
to a selected Information.

Do not implement application-specific JavaScript permission checks such as
`data-myapp-write`. The HTML should declare CNCF capability requirements, the
renderer should apply the visual state, and the operation layer should enforce
authorization.

## SPA / API Gateway Boundary

Static Form Web App screens should not be designed as hidden SPA shells. The
Bootstrap/Textus page must remain useful as server-rendered HTML, and any
JavaScript should be local enhancement rather than the owner of navigation,
state, authorization, or operation execution.

Use an external SPA or future explicit SPA hosting mode only when the
application really needs client-owned routing or rich client state. In that
case CNCF remains the operation/API provider:

- REST API executes domain operations.
- Form API supplies schema, validation, and input preparation.
- Web/session/UoW/authorization are not bypassed.
- Admin/system routes remain protected management surfaces.
- SPA bundles and assets are separately scoped and are not mixed into ordinary
  Static Form asset completion.

Do not add a global `/web` catch-all route for SPA fallback pages. A future
CNCF-hosted SPA mode must be explicit for the selected app or deployment
boundary.

The current CNCF Web host is still enough for a minimal same-origin SPA when
the app can live with one entry page and hash routing. That is useful for
internal tests and prototypes, but production SPA mode still needs explicit
fallback routing, asset lifecycle, auth/session/CSRF, API exposure, and
developer guidance. The detailed boundary is recorded in
`docs/notes/cncf-hosted-spa-boundary-note.md`.

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

## Document Pages

Use `Document` as the top-level label for component documentation. Do not use
`Manual` as the generated CNCF metadata surface label; it is too easily
confused with user guides or reference manuals.

Recommended structure:

- `Documents`: landing page for all component/system documentation.
- `Generated Specification`: CNCF-generated technical contract from component,
  service, operation, schema, and projection metadata.
- `User Guide`: component-packaged task guide for people using the feature.
- `Reference Manual`: component-packaged detailed human-authored reference.

Canonical routes:

- `/web/{component}/document`
- `/web/{component}/document/specification`
- `/web/system/document`
- `/web/system/document/specification`

Component packages may place documents in the private Web resource root under
`docs/` or `documents/`, for example `docs/user-guide.md` and
`docs/reference-manual.md`. The Document landing page should link to those
documents alongside the generated Specification.

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

- list: search card, active filter chips, table or card list,
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

- `textus:table` for ordinary tabular result data.
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

For a multi-Component Subsystem or deemed-Subsystem, declare the shell owner in
the Web descriptor instead of relying on component order:

```yaml
shell:
  component: blog-component
  app: blog
  layout: default
```

Without an explicit shell owner, CNCF may use a SAR/config shell or the single
Component fallback, but it must not pick another Component's `WEB-INF` shell in
a multi-Component runtime.

Use this ownership split:

- Subsystem/deemed-subsystem: header, footer, sidebar, global navigation,
  auth/session affordances, and common page shell.
- Component CAR: page content, local content partials, component assets, and
  component-specific form/result fragments.

The same composition rule applies to form result templates. The selected
`textus.form.page` / `cncf.form.page` page name determines whether the result
is article-mode or screen-mode. `textus-blog` uses this path as the first
deemed-subsystem driver: Blog layouts own the shell, while public/my/editor
static and result templates provide only the article content.

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
