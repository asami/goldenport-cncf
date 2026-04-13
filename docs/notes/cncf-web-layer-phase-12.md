# CNCF Web Layer Phase 12 Design Surface

status = draft

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

The Form API must not execute business logic by itself. Execution goes through
the REST API.

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
