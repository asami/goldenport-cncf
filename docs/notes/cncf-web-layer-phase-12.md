# CNCF Web Layer Phase 12 Design Surface

status = draft

Current stable Web Layer design is maintained in
`docs/design/web-layer.md`. This note remains as Phase 12 development context
and rollout history.

## Purpose

This document fixes the Phase 12 Web Layer design surface before runtime
implementation starts.

Phase 12 uses `textus-sample-app` as the practical validation driver, but CNCF
side changes must remain generic. Sample-specific behavior stays in the sample
app until it proves a common mechanism.

## Canonical Scope

The CNCF Web Layer is an operation-centric integration surface.

It provides:

- REST execution over Component / Service / Operation metadata
- Form API definition and validation over operation input metadata
- Static Form App routing and page resolution
- a shared base for Dashboard, Management Console, and Manual web apps
- Web Descriptor based exposure and security controls

The Web Layer does not introduce an API stub layer between browser clients and
CNCF operations. Operations remain the source of truth.

## Implementation Order

Phase 12 implementation follows this order:

1. Fix the Web Layer scope and design surface.
2. Define REST/Form API exposure and the Static Form App mechanism.
3. Define the Web Descriptor after the exposure surface is clear.
4. Define the read-only Dashboard as the first operational web app on the
   Static Form App mechanism.
5. Define Management Console and Manual as separate web apps on the same
   mechanism.
6. Add executable specifications and runtime hooks around the stabilized
   contracts.

Dashboard is the first practical entry point, but it must not bypass this order.
User-facing Web HTML paths must start with `/web` so they remain distinct from
operation REST paths. Plain HTML FORM submit paths use `/form`. Machine-facing
JSON Form API paths use `/form-api`:

- `/web/...` for Web HTML pages
- `/form/...` for plain HTML FORM submission
- `/form-api/...` for JSON Form API endpoints

In particular, `/dashboard` must not become a special-purpose server route that
duplicates the shared Static Form App path.

## Static Form App Baseline

Static Form App is the first application shape for Phase 12.

It is a lightweight web app model based on:

- static HTML pages
- Bootstrap 5 based responsive layout served from local CNCF web assets
- Form API for schema-driven form definition and validation
- REST execution for operation invocation
- convention-based result page resolution

The longer-term Web shape is Static Form Web App plus Island Architecture:
server-rendered pages remain the baseline, while local interactive islands can
be attached to specific page regions for dashboard refresh, field assistance,
async command status, or richer filtering. Islands are optional enhancement and
must not replace Operation/Form API contracts, authorization, persistence, or
server-rendered fallback behavior. The stable design is maintained in
`docs/design/web-layer.md`.

Bootstrap 5 is the default UI basis for Static Form App pages, including
dashboard, admin, performance, console, and manual pages. The assets are served
locally from `/web/assets` so smartphone, tablet, and desktop browsers can use
the pages without internet access.

Dashboard, Management Console, and Manual are separate Static Form App
instances. Their differences are exposure policy and allowed actions, not a
different server mechanism.

## Dashboard-First Validation

The first validation target is a read-only Subsystem Dashboard over
`textus-sample-app`.

Phase 12 distinguishes two dashboard kinds:

- Subsystem Dashboard: operational overview for the whole running subsystem,
  available at `/web/system/dashboard`.
- Component Dashboard: component-local overview for one component, available at
  `/web/{componentName}/dashboard`.

Both dashboards expose the same class of information: health, version,
components, services, and operations. They differ by data scope. Subsystem
Dashboard scopes the data to the whole subsystem; Component Dashboard scopes the
data to one component.

Their screen composition may differ. Subsystem Dashboard should emphasize
cross-component overview and navigation. Component Dashboard should emphasize
the selected component's services, operations, and local navigation.

Dashboard is a graphical read-only web app. It may refresh its state by polling
`/web/system/dashboard/state` or `/web/{componentName}/dashboard/state` at about
one-second intervals.

Dashboard emphasizes at-a-glance health. It should show overview-level data only:

- basic configuration identity: CNCF version, Subsystem name/version, and
  Component names/versions
- Subsystem activity status: jobs and top-level runtime metrics
- HTML-level request volume trend and recent request summary
- ActionCall-level job counts such as completed, running, queued, and failed
- HTML request, ActionCall, and Job count/error summaries for cumulative, one-day,
  one-hour, and one-minute windows
- assembly warning count with a link to the structured admin warning surface
- traffic graph tabs for one-minute, one-hour, and one-day bucket views; the
  default graph view is one hour

Detailed configuration and performance analysis belong to dedicated pages, for
example an admin configuration page and a system performance page. Dashboard
should link to those pages rather than embedding the full detail surface inline.

Calltree and retained action execution history are performance-analysis
details. The Dashboard should show ActionCall count/error summaries only. The
System Performance page links to the existing admin execution operations:

- `/form/admin/execution/history`
- `/form/admin/execution/calltree`

Those operations reuse the calltree event semantics defined by the core
observability design and do not introduce a Web-specific calltree vocabulary.

Assembly warnings are operational health signals. The Dashboard should expose
the warning count and link to the existing admin assembly warning operation:

- `/form/admin/assembly/warnings`

The System Performance page may also link to:

- `/form/admin/assembly/report`

The Web surface must reuse the structured assembly warning/report operations
instead of defining a dashboard-only warning record.

The baseline Subsystem Dashboard must be able to show:

- runtime health
- runtime version
- loaded components
- services per component
- operations per service

The initial Subsystem Dashboard must not:

- execute arbitrary operations inline
- mutate configuration
- control jobs
- embed Manual as the main reference surface
- merge Management Console actions into the read-only view

Management Console may later link from the Subsystem Dashboard, but remains a
separate Static Form App instance.

Dashboard navigation may link to:

- `/web/system/admin`
- `/web/system/performance`
- `/web/system/manual`
- `/web/console`

Those links do not make Dashboard an execution surface. Console operation
execution remains in the separate Console/Form App context; Dashboard only
offers navigation.

Advanced visualizations are deferred from the WEB-04 baseline. SVG assembly
diagrams, richer wiring graphs, and other visual projections should be built as
later projections of existing admin assembly/report data, not as new Dashboard
semantics. The baseline Dashboard keeps the at-a-glance health view, counts,
graph tabs, and links to detail surfaces.

A Component Dashboard must not replace the Subsystem Dashboard because it has a
narrower scope and different screen composition.

## REST And Form API Baseline

The REST API is the execution backbone.

Plain HTML FORM submit is the browser-native execution path for Static Form
App pages.

JSON Form API is the input preparation layer. It provides:

- form definition endpoint
- validation endpoint
- consistent validation error shape

The stable Form API definition response contract is maintained in
`docs/design/web-form-api-schema.md`. This note records the Phase 12
implementation surface and remaining rollout context.

The Form API definition endpoint is available at:

```text
GET /form-api/{component}/{service}/{operation}
GET /form-api/{component}/admin/entities/{entity}
GET /form-api/{component}/admin/data/{data}
GET /form-api/{component}/admin/views/{view}
GET /form-api/{component}/admin/aggregates/{aggregate}
```

These endpoints return the same `ResolvedWebSchema` field contract used by the
HTML form renderers: name, label, type, datatype, multiplicity, required,
readonly, hidden/system, values, multiple, placeholder, help, and later
validation hints. This keeps Static Form App HTML, JSON-oriented clients, and
later SPA/SDK code on the same schema source.

Operation form definitions are resolved from `ParameterDefinition` plus
WebDescriptor controls. Admin entity, view, and aggregate definitions are
resolved from the effective entity schema plus WebDescriptor controls. Admin
data definitions use descriptor/schema information when available and otherwise
fall back to best-effort inference from data read/list results. In fallback
inference, `id` is normalized to the first field when present because the
intermediate `Record`/map route may not preserve model declaration order.

The Form API must not execute business logic by itself. Definition and
validation endpoints prepare input; execution remains an Operation dispatch.
Validation is limited to Web input admission, such as required fields,
datatype shape, candidate values, multiplicity, and unknown-field warnings.
Domain invariants, state-dependent cross-field rules, instance authorization,
optimistic locking, and datastore-backed existence/uniqueness checks remain
Operation-side validation. The normative boundary is maintained in
`docs/design/web-form-api-schema.md`.
The next validation hint expansion is min/max, step, minLength/maxLength, and
pattern. These hints should be carried from CML through
`org.goldenport.schema.Schema` / `ParameterDefinition`; WebDescriptor is only a
secondary override layer and must not silently relax model constraints.
The current POST compatibility route under `/form-api/{component}/{service}/{operation}`
submits form data and returns the Operation response directly.

Management Console create/update form submissions follow the same rule. The
HTML submit route under `/form/{component}/admin/...` builds an Operation
request and dispatches it through the Web Operation Dispatcher. Current
implemented write Operations are:

- `admin.entity.create`
- `admin.entity.update`
- `admin.data.create`
- `admin.data.update`

The first read/list Operation surface is also in place:

- `admin.entity.list`
- `admin.entity.read`
- `admin.data.list`
- `admin.data.read`
- `admin.view.read`
- `admin.aggregate.read`

Those Operations are synchronous admin commands for browser-native form result
pages. They are the persistence chokepoints for the current Management Console
write path; the HTTP server no longer writes EntityCollection or DataStore
records directly for these forms.

The read/list Operations are query surfaces. Management Console HTML pages now
consume these Operation results directly for entity, data, view, and aggregate
read/list rendering. Compatibility fallbacks to direct local runtime reads are
intentionally not retained.

The current admin query response baseline is:

- `admin.entity.list`: `RecordResponse(kind, component, collection, ids,
  items, page, pageSize, hasNext, total?, totalAvailable)`
- `admin.entity.read`: `RecordResponse(kind, component, collection, id, record,
  label, value, item, fields)`
- `admin.data.list`: `RecordResponse(kind, component, collection, ids, items,
  page, pageSize, hasNext, total?, totalAvailable)`
- `admin.data.read`: `RecordResponse(kind, component, collection, id, record,
  label, value, item, fields)`
- `admin.view.read` list mode: `RecordResponse(kind, component, collection,
  items, values, fields, page, pageSize, hasNext, total?, totalAvailable)`
- `admin.view.read` single mode: `RecordResponse(kind, component, collection,
  id, label, value, item, fields)`
- `admin.aggregate.read` list mode: `RecordResponse(kind, component,
  collection, items, values, fields, page, pageSize, hasNext, total?,
  totalAvailable)`
- `admin.aggregate.read` single mode: `RecordResponse(kind, component,
  collection, id, label, value, item, fields)`

`fields` is the browser rendering bridge for the current Static Form App.
`ids` remains the entity/data list compatibility id slot. `items` is the
structured data slot for later JSON Form API and richer widgets across
entity/data/view/aggregate list results. Each item carries `id`, `label`, and
`value`; HTML detail links use `id`, while visible table text uses `label`.
`values` remains a compatibility/display slot for view/aggregate results and
must not be used as the canonical detail id source when `items` is present. For
single reads, `item` is the structured result and top-level `id`, `label`, and
`value` are duplicated for simple renderers. Entity/data read results also keep
the original structured `record`. `page` and `pageSize` are accepted as positive
integer Operation arguments and reflected in the response shape. Admin query
paging uses
`pageSize + 1` over-fetching to calculate `hasNext` without requiring a total
count. Entity paging slices the in-memory record id vector. DataStore paging
fetches enough records for the requested page and then slices locally because
the current DataStore query directive has no offset slot. View and aggregate
queries pass both offset and over-fetch limit through the entity query
directive.

Total count is optional and design-gated. `includeTotal=true` requests `total`,
but it is honored only when the Web Descriptor admin surface declares
`totalCount=optional` or `totalCount=required`; the default policy is
`disabled`. The default remains no total count so backing stores that require an
extra count query do not pay that cost unless the application design and caller
both ask for it. When a total count is returned, `totalAvailable=true`;
otherwise the response includes `totalAvailable=false` and omits `total`.
Middleware capability checks now gate DataStore total counting. `optional`
degrades to no `total` when the DataStore reports total count as unsupported or
effectively impossible, while `required` fails with a structured argument error.
Optional degradation returns `totalUnavailableReason` and a warning message for
HTML rendering. Richer count capability classes remain follow-up work.

The default generated view and aggregate implementation now resolves total
count capability at runtime from the root entity's backing DataStore. The
surface-level object can provide a count function, but admin paging treats total
count as usable only when the current `ExecutionContext` reports a supporting
DataStore. This keeps the same component definition usable with in-memory,
SQLite, and future NoSQL backends without pretending that every backend can
count cheaply.

Generated default View browsers use context-aware access paths:

- `find_with_context`
- `query_with_context`
- `count_with_context`

The implementation routes these through the caller's `ExecutionContext` instead
of creating a new context. That context carries the active EntityStoreSpace,
DataStoreSpace, authorization, observability, and future Web-tier to
Application-tier dispatch assumptions. Context-free Browser methods remain
usable for simple manual fixtures, but generated default Views should be
accessed through the context-aware API in runtime code.

Entity and data Management Console list pages read `page`, `pageSize`, and
`includeTotal` from the `/web/...` query string and pass them to the admin query
Operation together with the Descriptor-derived `totalCountPolicy`. The HTML
paging navigation uses the returned `page`, `pageSize`, `hasNext`, and optional
`total` values, so `Next` is disabled when the Operation response reports no
following page even when no total count is available.

CLI and meta projections remain separate surfaces. Phase 12 Web does not
replace command-line help, describe/schema output, OpenAPI projection, or the
CLI `Presentable` result rendering policy. Web paths share operation metadata
with those projections, but define browser-specific contracts:

- `/web/...` returns HTML pages.
- `/form/...` accepts plain HTML FORM submission and resolves to HTML result
  pages.
- `/form-api/...` returns JSON form metadata or validation results.
- REST operation paths return the JSON execution envelope.

## Non-Goals

Phase 12 does not commit to:

- a frontend framework
- broad business UI generation
- SPA packaging beyond minimal design boundaries
- JavaScript SDK generation
- advanced SVG or graph visualization unless required for the Dashboard baseline
- replacing CLI/meta projections

## Design Inputs

The canonical inputs for Phase 12 are:

- `docs/journal/2026/04/web-application-integration-note.md`
- `docs/journal/2026/04/web-operational-management-note.md`
- `docs/journal/2026/04/web-form-api-note.md`
- `docs/journal/2026/04/web-static-form-app-note.md`
- `docs/journal/2026/04/web-api-response-note.md`
- `docs/journal/2026/04/web-descriptor-note.md`
- `docs/journal/2026/04/web-integration-spa.md`
- `docs/journal/2026/04/web-wireframe-dsl-note.md`
