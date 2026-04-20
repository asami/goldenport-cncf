# Web Layer

## Purpose

This document records the current CNCF/Textus Web Layer design.

The Web Layer is an operation-centric browser and HTTP integration surface. It
does not introduce a separate application API layer between Web clients and
CNCF Component / Service / Operation metadata. Operations remain the source of
truth for execution, authorization, observability, and domain behavior.

## Product And Runtime Naming

CozyTextus is the product-facing name. CNCF remains the implementation name.

User-facing Web routes should use product-oriented naming where the route is
visible to application users. Runtime internals may still expose CNCF naming
where the surface is explicitly implementation-oriented or hidden.

## Path Families

The Web Layer separates user-facing HTML, browser-native form submission,
machine-facing form metadata, and REST execution.

```text
/web/...       Web HTML pages
/form/...      plain HTML FORM submission routes
/form-api/...  JSON Form API definition and validation endpoints
REST paths     JSON operation execution envelopes
```

`/web` pages are human-facing HTML pages. `/form` routes accept browser-native
HTML FORM submissions and resolve to HTML result pages. `/form-api` routes are
for JSON-oriented clients and form preparation. REST operation paths remain the
canonical JSON execution surface.

## Static Form App

Static Form App is the first Web application shape.

It is based on:

- static HTML pages
- Bootstrap 5 responsive layout from local `/web/assets`
- schema-driven form generation and validation
- operation dispatch for execution
- convention-based result page resolution
- Textus widgets for result, action, table, and later card rendering

Static Form App intentionally avoids arbitrary template programming. It should
not grow loops, conditionals, or application logic in HTML. When an application
needs richer control flow, it should use an external Web framework and call
CNCF through REST or Form API.

## Island Architecture

The Web Layer is designed to allow Island Architecture on top of Static Form
Web App pages.

The baseline page remains server-rendered, static HTML:

- page shell, navigation, forms, result pages, and Textus widgets are rendered
  on the server.
- HTML FORM submission and convention-based result pages remain usable without
  client-side JavaScript.
- Form API and REST endpoints provide machine-readable integration points for
  progressive enhancement.

Interactive islands may be attached to specific page regions when a feature
needs local browser behavior, for example:

- dashboard auto-refresh and charts
- field-level dynamic assistance
- lookup/autocomplete controls
- client-side validation assistance
- async command await/status widgets
- richer table/card filtering where the server contract is still authoritative

Island code must be optional enhancement. It must not become the primary source
of domain behavior, authorization, persistence, or operation dispatch. The
server-rendered page and Operation/Form API contracts remain the fallback and
the source of truth.

Island assets should be local and scoped. The first-class baseline remains
Bootstrap 5 plus server-rendered HTML; a broad SPA framework or application-wide
client router is outside the Static Form Web App baseline.

## Web App Packaging

Static Form Web App resources need an explicit packaging and deployment model.

The early `textus-sample-app` validation shape used:

```text
.textus/config.yaml              runtime configuration
config/web-descriptor.yaml       Web Descriptor
config/*__200.html               operation-specific result templates
config/__400.html                common validation/error page
config/__500.html                common server-error page
config/__error.html              common fallback error page
```

When `textus.web.descriptor` pointed at `config/web-descriptor.yaml`, the
descriptor parent directory acted as the static template root. This was useful
for early validation, but it is not the target compatibility contract and is
not preserved as a packaging compatibility rule.

The canonical archive layout places Web application resources under `/web` in
both CAR and SAR packages.

```text
/web/web-descriptor.yaml            Web Descriptor
/web/{webApp}/index.html            Web app entry page
/web/{webApp}/*.html                Web app result templates
/web/{webApp}/assets/**             Web app local CSS/JS/images
/web/{webApp}/islands/**            Web app scoped island scripts
/web/*.html                         component/subsystem common fallback templates
/web/assets/**                      component/subsystem common assets
```

CAR `/web` packages a Component-local Web app. SAR `/web` packages a
Subsystem-level Web app and may also include subsystem-owned app assets or
composition pages. Runtime extraction should treat the archive `/web` directory
as the Web app root.

Descriptor discovery uses `web/web-descriptor.yaml` as the canonical packaged
descriptor name. `web/web.yaml` is accepted as a secondary descriptor name for
existing tests and small fixtures. When `textus.web.descriptor` points directly
at a descriptor file, the descriptor parent directory is the static template
root. When it points at a descriptor root, the runtime discovers the descriptor
under the root `/web` directory and uses that `/web` directory as the template
root.

The runtime represents each filesystem or archive Web root as a Web resource
root. Static result templates, common error pages, and app-local assets are read
through this root abstraction instead of direct filesystem `Path` access. For
CAR/SAR/ZIP archives, the archive `/web` directory is the resource root and the
same descriptor, template, and asset lookup rules apply as they do for a
development filesystem directory.

At runtime, Component-local CAR resources are mounted under the component URL
scope:

```text
CAR: /web/{webApp}/...
URL: /web/{component}/{webApp}/...
```

For example, `abc.car:/web/notice-board/index.html` is exposed as
`/web/abc/notice-board`, and
`abc.car:/web/notice-board/assets/app.css` is exposed as
`/web/abc/notice-board/assets/app.css`.

Subsystem-level SAR resources are mounted under the subsystem/system Web scope.
The archive resource root remains `/web/{webApp}/...`.

A typical SAR use case is a subsystem-level Web app that acts as a portal or
composition page. It is owned by the SAR `/web` package and links users to
Component-local Web apps mounted from CAR `/web` packages.

For example:

```text
SAR: /web/portal/index.html
URL: /web/portal

CAR: abc:/web/notice-board/index.html
URL: /web/abc/notice-board

CAR: inventory:/web/catalog/index.html
URL: /web/inventory/catalog
```

The SAR portal may render links or cards to `/web/abc/notice-board` and
`/web/inventory/catalog`. This composition pattern is different from aliasing:
the SAR app owns the portal page, while each Component app keeps its own
component-scoped URL, authorization, templates, and assets.

The SAR Web Descriptor may also define Web app route aliases. This lets a
Component-local Web app be promoted from its canonical component-scoped URL to a
subsystem-level URL:

```text
canonical CAR URL:
  /web/{component}/{webApp}/...

SAR route alias examples:
  /web/{webApp}/...
  /web/...
```

For example, an SAR containing component `abc` may route
`abc:/web/notice-board/...` in any of these ways:

```text
/web/abc/notice-board/...    canonical component-scoped route
/web/notice-board/...        SAR alias for the same Web app
/web/...                     SAR default Web app route
```

CAR-only execution creates an implicit SAR. That implicit SAR may apply the
same alias rule so a single-component application can expose its primary Web
app directly under `/web/{webApp}` or `/web` while preserving the canonical
`/web/{component}/{webApp}` route.

Alias routes must not change resource ownership. Authorization, form dispatch,
template lookup, and asset lookup still resolve to the original Component Web
app. Alias conflicts must be rejected or made explicit in the SAR Web
Descriptor.

Alias resolution is a SAR responsibility. The canonical component route is
always present; SAR aliases are additional routes selected by the SAR
descriptor or by the implicit SAR created for single-CAR execution. Alias
resolution must run before the built-in Static Form App fallback so
`/web/{webApp}` can select a component Web app when a SAR route explicitly
binds that path. If multiple component Web apps could claim the same alias, the
runtime must reject the descriptor unless one route is selected explicitly.

The implicit SAR may provide a convenience alias only when it can prove that
there is exactly one component and one exposed Web app. Otherwise it keeps only
the canonical `/web/{component}/{webApp}` route.

The descriptor route vocabulary is:

```yaml
web:
  apps:
    - name: notice-board
      # kind/root/route may be omitted when they follow convention.
      # completed descriptor:
      #   kind: static-form
      #   root: /web/notice-board
      #   route: /web/{component}/notice-board

  routes:
    - path: /web/notice-board
      target:
        component: abc
        app: notice-board
      kind: alias
    - path: /web
      target:
        component: abc
        app: notice-board
      kind: default
```

`apps` declares packaged Web apps. `root` is the archive-local resource root.
`route` is the canonical runtime route and may include `{component}` for CAR
resources. `routes` declares SAR-level aliases or the selected default Web app.
The target always identifies the owning Component and Web app so alias routes
do not change ownership.

Route entries are subsystem-level routing declarations. `path` is the visible
URL prefix. `target.component` and `target.app` identify the canonical owner.
`kind: alias` is a named subsystem-level shortcut to a component Web app.
`kind: default` selects the Web app served at `/web` and its descendants. The
default route is optional; if absent, `/web` remains reserved for built-in Web
layer paths and explicit subsystem Web apps.

The Management Console exposes the completed Web Descriptor at
`/web/system/admin/descriptor`. This page is the operator-facing reference for
the descriptor after framework defaults have been applied. It also shows the
configured descriptor for comparison, so omitted convention values such as
`kind: static-form`, `root: /web/{app}`, and
`route: /web/{component}/{app}` can be inspected without expanding the source
file by hand.

Component Management Console pages expose the same descriptor reference under
`/web/{component}/admin/descriptor`. The component page keeps the configured
descriptor for comparison and resolves component route placeholders such as
`{component}` against the selected component, so operators can inspect the
effective route for that component without leaving the component admin context.
Completed descriptor JSON also exposes descriptor asset composition diagnostics:
global assets, app assets, form assets, and the resolved asset lists used by
component form indexes, operation input pages, and operation result pages.
The same information is rendered as Asset Composition tables before the raw
JSON so operators can inspect configured scopes and completed page assets
without reading the descriptor structure directly.
Descriptor pages also render Apps, Routes, Form Access And Authorization, and
Admin Surfaces tables before the raw JSON. These tables are the primary admin
inspection surface; the raw descriptor JSON remains available for exact
debugging and copy/paste.

Static result template lookup is route-local under the Web app root. For a
Component app `abc:/web/notice-board`, operation result templates are placed
directly under `/web/notice-board`:

```text
/web/notice-board/post-notice__200.html
/web/notice-board/search-notices__200.html
/web/notice-board/__400.html
/web/__500.html
```

Lookup precedence is executable-speced as:

1. Route-local operation-specific templates, such as
   `/web/notice-board/notice/post-notice__200.html` and
   `/web/notice-board/post-notice__200.html`.
2. Route-local common status/outcome templates, such as
   `/web/notice-board/notice/__200.html` and `/web/notice-board/__200.html`.
3. Component/subsystem common templates under the template root, such as
   `/web/post-notice__200.html` and `/web/__200.html`.
4. Descriptor-provided `resultTemplate`.
5. Built-in rendering.

Framework assets are served under `/web/assets/...` and are owned by the
runtime. Bootstrap 5 and Textus widget local assets currently use this route.

Static Web app HTML should link framework assets through the framework route:

```html
<link href="/web/assets/bootstrap.min.css" rel="stylesheet">
<link href="/web/assets/textus-widgets.css" rel="stylesheet">
<script src="/web/assets/bootstrap.bundle.min.js"></script>
<script src="/web/assets/textus-widgets.js"></script>
```

The framework asset route is stable across canonical component routes, SAR
aliases, and implicit single-CAR aliases. Static pages must not use CDN URLs
for baseline Bootstrap behavior or Textus widget behavior.

Static Form result rendering uses `web.assets` as composition input:

- `autoComplete: false` disables automatic framework asset insertion.
- `css` and `js` entries are inserted into result pages as application
  composition assets, with duplicate suppression.
- framework assets are inserted first, followed by descriptor-declared assets.

Descriptor assets can also be scoped to a Web app and to a form/operation:

```yaml
web:
  apps:
    - name: notice-board
      assets:
        css:
          - /web/notice-board/notice-board/assets/app.css
        js:
          - /web/notice-board/notice-board/assets/app.js
  form:
    notice-board.notice.search-notices:
      assets:
        css:
          - /web/notice-board/notice-board/assets/search.css
```

Result rendering merges asset scopes in the order `web.assets`,
`web.apps[].assets`, then `web.form.<selector>.assets`. This keeps global
assets available while allowing multi-app CAR/SAR packages to avoid applying
app-specific CSS/JS to unrelated pages.

Static Form input pages use the same scopes. Component form indexes receive
global and app assets. Operation input forms receive global, app, and matching
form assets.

App-local assets are served from the canonical component Web app route:

```text
CAR: /web/{webApp}/assets/{asset}
URL: /web/{component}/{webApp}/assets/{asset}
```

Static Web app HTML should link app-local assets by canonical component route:

```html
<link href="/web/{component}/{webApp}/assets/app.css" rel="stylesheet">
```

Even when a page is reached through `/web/{webApp}` or `/web`, app-local asset
links should still point at the canonical `/web/{component}/{webApp}/...`
route. This keeps browser-visible pages, descriptor inspection, executable
checks, and operator debugging aligned with the resource owner. Alias routes
may serve app-local assets for convenience, but generated/static HTML should
prefer the canonical route.

Static Web app HTML is served from the same canonical component Web app route:

```text
CAR: /web/{webApp}/index.html
URL: /web/{component}/{webApp}

CAR: /web/{webApp}/{page}.html
URL: /web/{component}/{webApp}/{page}
```

The asset lookup uses the same Web template root as result templates and keeps
component ownership in the URL. An alias may point to the same app later, but
the canonical component route remains the descriptor/debugging reference.

The `config/` shape should be migrated to the canonical `/web` layout rather
than preserved as a compatibility packaging contract. Packaged and generated
applications should place Web app resources under `/web`.

The packaging model should cover:

- Web Descriptor location and discovery precedence.
- static result template lookup.
- app-local assets such as CSS, JavaScript, and images.
- optional island scripts scoped to specific page regions.
- development project layout.
- packaged CAR/SAR or equivalent archive layout.
- migration of existing `config/` based sample applications to `/web`.

Packaging must preserve the Static Form Web App rule: server-rendered HTML and
Operation/Form API contracts remain authoritative, while assets and islands are
progressive enhancement.

## Built-In Web Apps

Dashboard, Management Console, Manual, Performance, and Console are built-in
Web apps on the same Web Layer foundation.

### Dashboard

Dashboard is a read-only operational view.

Routes:

```text
/web/system/dashboard
/web/system/dashboard/state
/web/{component}/dashboard
/web/{component}/dashboard/state
```

The system dashboard scopes data to the whole subsystem. The component
dashboard scopes data to one component. Both expose the same class of
information and may use different screen composition.

Dashboard state may be polled at about one-second intervals. Dashboard shows
overview-level health only:

- CNCF/Textus version
- subsystem name/version
- component names/versions
- HTML request, ActionCall, DSL chokepoint, authorization, and job metrics
- cumulative, one-day, one-hour, and one-minute count/error summaries
- one-minute, one-hour, and one-day traffic series, defaulting to one hour
- assembly warning count with a link to the structured admin surface
- links to admin, performance, manual, and console pages

Dashboard must not execute operations inline, mutate configuration, control
jobs, or replace Management Console.

### Management Console

Management Console is the built-in administrative CRUD and read surface.

Routes follow the component-scoped shape:

```text
/web/{component}/admin
/web/{component}/admin/entities
/web/{component}/admin/entities/{entity}
/web/{component}/admin/entities/{entity}/{id}
/web/{component}/admin/entities/{entity}/{id}/edit
/web/{component}/admin/entities/{entity}/new
/web/{component}/admin/data
/web/{component}/admin/data/{data}
/web/{component}/admin/data/{data}/{id}
/web/{component}/admin/data/{data}/{id}/edit
/web/{component}/admin/data/{data}/new
/web/{component}/admin/views
/web/{component}/admin/views/{view}
/web/{component}/admin/views/{view}/{id}
/web/{component}/admin/aggregates
/web/{component}/admin/aggregates/{aggregate}
/web/{component}/admin/aggregates/{aggregate}/{id}
```

Entity and data surfaces provide list, detail, edit/update, and new/create
flows. View surfaces are read-only. Aggregate surfaces provide list/detail and
operation execution when the aggregate exposes operations.

Management Console write paths must dispatch through admin Operations. The HTTP
server must not directly mutate EntityCollection, EntityStoreSpace, or
DataStoreSpace.

Current write Operations:

- `admin.entity.create`
- `admin.entity.update`
- `admin.data.create`
- `admin.data.update`

Current read/list Operations:

- `admin.entity.list`
- `admin.entity.read`
- `admin.data.list`
- `admin.data.read`
- `admin.view.read`
- `admin.aggregate.read`

### Manual

Manual is a read-only reference app.

Routes:

```text
/web/system/manual
/web/{component}/manual
/web/{component}/manual/{service}
/web/{component}/manual/{service}/{operation}
/web/system/manual/openapi.json
```

Manual renders Help, Describe, Schema, OpenAPI, MCP, and navigation references.
It must not render mutation forms or execute operations inline.

### Performance

Performance detail pages are separate from Dashboard. Dashboard shows overview
health; performance pages link to or present detail surfaces such as calltree,
execution history, assembly warnings, and assembly reports.

### Console

Console is the controlled operation entry page. It may link to generated
operation forms, but it must not make Dashboard or Manual execution surfaces.

## Form API

Form API is the JSON preparation layer for Web input.

Definition endpoints:

```text
GET /form-api/{component}/{service}/{operation}
GET /form-api/{component}/admin/entities/{entity}
GET /form-api/{component}/admin/data/{data}
GET /form-api/{component}/admin/views/{view}
GET /form-api/{component}/admin/aggregates/{aggregate}
```

Form API returns the resolved Web schema used by HTML renderers. It includes
field name, label, type, datatype, multiplicity, required, readonly,
hidden/system, values, multiple, placeholder, help, and validation hints.

Form API must not execute business logic. It validates Web input admission only:
required fields, datatype shape, candidate values, multiplicity, and
unknown-field warnings. Domain invariants, state-dependent rules, instance
authorization, optimistic locking, and datastore-backed checks remain
Operation-side responsibilities.

The stable JSON response contract is defined in
`docs/design/web-form-api-schema.md`.

## Web Schema Resolution

Generated forms and admin pages consume a resolved Web schema.

Primary metadata source:

```text
CML entity/value/operation metadata
  -> org.goldenport.schema.Schema / ParameterDefinition
  -> EntityRuntimeDescriptor / operation metadata
  -> WebSchemaResolver.ResolvedWebSchema
  -> HTML renderer / Form API / Textus widget renderer
```

`WebDescriptor` is a presentation and deployment override layer. It may narrow
or reorder fields and supplement labels, help, placeholders, control hints, and
exposure policy. It must not become the primary schema source.

Field order must be preserved from the schema/view vector. Renderer code must
not depend on reflection order or unordered maps.

## Result Page Resolution

Plain HTML FORM submission resolves to HTML by convention before descriptor
customization.

Static result templates use status-specific names:

```text
{form}__200.html
{form}__404.html
{form}__500.html
{form}__success.html
{form}__error.html
```

The exact status template wins before the broader success/error template.
Common error/status templates may be used when an app-specific template is not
present. Operation results and submitted values are exposed as properties for
result-page embedding and Textus widgets.

Result-page rendering receives a single page-property context across static
templates, descriptor `resultTemplate`, and the built-in fallback. Operation
input values are exposed under `form.*`. Framework page context is exposed by
its original names, such as `crud.origin.href`, `paging.page`,
`paging.pageSize`, `paging.href`, `search.*`, `continuation.id`, `version`,
`etag`, and `csrf`. Framework context is not copied into `form.*` and is
stripped before Operation dispatch.

Validation redisplay uses the same split. The rendered form keeps framework
context as hidden fields, while validation and Operation dispatch see only the
operation input values.

Command-style operation execution should prefer asynchronous execution when the
operation semantics allow it. The Web response may expose a `jobId` plus
tracking identifiers such as an entity id, allowing a static page to offer an
await/detail link without forcing every command to be synchronous.

## Paging And Total Count

List surfaces use `page`, `pageSize`, and `hasNext`. The default list behavior
does not require total count.

Total count is design-gated:

- default: disabled
- optional: include total only when requested and supported
- required: fail when requested but unsupported

This preserves compatibility with stores where count is expensive or
effectively unavailable. Optional degradation should return a reason/warning
for HTML display rather than pretending that a total exists.

## Authorization

Web authorization is not Web-only. Command, server, client, REST, Web HTML, and
Form API routes must pass through the same operation authorization model.

Web Descriptor exposure is an ingress gate. It does not replace Operation,
Entity, Aggregate, or instance authorization. Operation implementations should
avoid ad hoc security checks when the framework can enforce policy from
component/service/operation/entity metadata and runtime context.

Anonymous execution is represented by an anonymous subject when no protocol
session is available. Development and test modes may allow anonymous admin
access by configuration, but production must deny anonymous admin access unless
an explicit policy says otherwise.

## Bootstrap 5 UI Baseline

Bootstrap 5 is the default UI vocabulary for framework-generated Web pages and
Static Form App pages. Assets are local:

```text
/web/assets/bootstrap.min.css
/web/assets/bootstrap.bundle.min.js
```

Generated pages must be usable offline with local Bootstrap assets and must not
require CDN access.

The Phase 12 polish baseline is:

- Bootstrap card sections for navigation and primary content
- responsive tables inside `.table-responsive`
- grouped action buttons
- Bootstrap form controls and validation feedback
- visible warning/error/empty-state feedback
- responsive layout for desktop, tablet, and smartphone widths

Richer CozyTextus visual identity, theme customization, and screenshot-level
visual regression are future UI quality work.

## Observability

Web dispatch must preserve runtime observability. Operation dispatch,
authorization decisions, DSL/chokepoint execution, HTML request metrics, job
activity, and assembly warnings are dashboard/performance inputs.

DSL chokepoints are both enforcement and observability points. They should be
structured so authorization, audit, metrics, and calltree integration can be
added or changed without relying on application-side convention.

## Related Documents

- `docs/design/web-form-api-schema.md`
- `docs/design/web-operation-dispatcher.md`
- `docs/design/management-console.md`
- `docs/design/operation-authorization-model.md`
- `docs/design/entity-authorization-model.md`
- `docs/spec/textus-widget.md`
- `docs/notes/cncf-web-layer-phase-12.md`
- `docs/notes/cncf-web-static-form-app-contract.md`
- `docs/notes/cncf-web-descriptor-minimum-schema.md`
- `docs/notes/web-bootstrap-ui-polish-design.md`
