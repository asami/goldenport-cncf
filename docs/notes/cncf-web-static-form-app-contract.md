# CNCF Static Form App Contract

status = implemented

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
GET  /form-api/{component}/admin/entities/{entity}
GET  /form-api/{component}/admin/data/{data}
GET  /form-api/{component}/admin/views/{view}
GET  /form-api/{component}/admin/aggregates/{aggregate}
POST /form-api/{component}/{service}/{operation}/validate
```

Admin Form API paths share the Management Console authorization model. Anonymous
requests may reach admin Form API paths only when both conditions are true:

- `textus.operation-mode` is `develop` or `test`.
- `textus.web.develop.anonymous-admin` is `true`.

The default is intentionally development-friendly:

```text
textus.operation-mode = develop
textus.web.develop.anonymous-admin = true
```

In `production` and `demo`, the built-in admin component's operation
authorization parameters deny anonymous admin access even if
`textus.web.develop.anonymous-admin` is set to `true`. Requests with explicit
principal/session information are not anonymous and are evaluated by the normal
Operation/Web authorization rules. Component/service/operation authorization
definitions may explicitly allow anonymous access or constrain operation modes
for a selector, and the framework enforces those rules before Operation
dispatch for every ingress root. The current principal-query/header support is
a temporary protocol-level stand-in for the future login/session tier.

The shared enforcement shape is `OperationAuthorizationRule`. WebDescriptor
authorization can narrow or supplement Web/Form admission, but it does not
replace the Operation checkpoint. Command, server, client, REST, and Web/Form
requests therefore converge on the same selector policy after ingress security
resolution.

This policy is independent of `RunMode`. Command, server, client, script, and
server-emulator roots may reach the same Web/Form API mechanisms, but the
effective admin policy is taken from `OperationMode` and the resolved runtime
configuration.

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

Calltree and retained execution history are detailed performance-analysis
surfaces. Dashboard state exposes ActionCall count/error summaries; the System
Performance page links to the existing admin execution operations:

```text
/form/admin/execution/history
/form/admin/execution/calltree
```

Web pages must reuse the existing retained execution history and calltree
semantics instead of defining a separate dashboard-only vocabulary.

Assembly warnings are also visible at the overview level. Dashboard state
exposes only the warning count and link:

```text
assembly.warnings.count
links.assemblyWarnings = /form/admin/assembly/warnings
```

Detailed warning records and the resolved assembly report remain existing admin
operation surfaces:

```text
/form/admin/assembly/warnings
/form/admin/assembly/report
```

Advanced visual projections such as SVG assembly diagrams or richer wiring
graphs are not part of the WEB-04 baseline. They should remain projections of
the existing assembly/admin data and may be added later without changing the
Dashboard state contract.

The System Admin page groups operational drill-down links by concern:

- Assembly: `/form/admin/assembly/warnings` and `/form/admin/assembly/report`.
- Execution: `/form/admin/execution/history` and `/form/admin/execution/calltree`.

These links remain existing admin operation surfaces. The WEB-05 baseline only
organizes their navigation from `/web/system/admin`.

The System Admin page also links to the resolved Web Descriptor drill-down:

```text
/web/system/admin/descriptor
```

This page renders the effective descriptor as read-only JSON for operator
inspection. It is an HTML admin page, not a business execution endpoint.

The System Admin page may expose resolved runtime configuration values for
operator inspection. The WEB-05 baseline applies these rules:

- Show only CNCF/Textus runtime-scoped keys, currently keys under `textus.*`
  and `cncf.*`.
- Render configuration as read-only information.
- Mask sensitive values when the key name indicates secrets, passwords,
  tokens, credentials, API keys, or private keys.
- Do not expose unrelated application configuration by default.

Runtime configuration display and mutation are separate concerns:

- `/web/system/admin` may show resolved configuration in a read-only form.
- Configuration mutation must use a separate admin action surface.
- Mutation actions must require explicit admin authorization and audit logging.
- Destructive or service-affecting mutations must also require confirmation.
- The WEB-05 baseline does not enable browser-side configuration mutation.

Job control entry points belong to the System Admin surface. The WEB-05
baseline keeps them read-only and links to the existing execution observation
surfaces:

```text
/form/admin/execution/history
/form/admin/execution/calltree
```

Browser-side job mutations such as cancel, retry, and force-complete are not
enabled in the baseline. Before they are added, they must require explicit
admin authorization, confirmation for destructive actions, and audit logging.

## Execution Model

REST operation execution remains the only business execution path.

HTML FORM submit is a browser-native execution layer:

- `POST /form/{component}/{service}/{operation}` accepts
  `application/x-www-form-urlencoded` or `multipart/form-data`.
- the request is converted to operation input.
- the operation is executed through the REST execution backbone.
- success and error outcomes resolve to Web HTML result pages.

The first runtime hook provides a generic component operation entry:

```text
GET  /form/{component}
GET  /form/{component}/{service}/{operation}
POST /form/{component}/{service}/{operation}
```

The `GET` routes render a Bootstrap-backed operation index and operation form.
The `POST` route converts submitted form data to operation input, invokes the
matching REST execution path, and renders an HTML result page. This hook is
generic CNCF behavior and does not contain sample-app-specific page content.

JSON Form API is an input preparation layer:

- `GET /form-api/...` returns form metadata for an operation.
- `GET /form-api/{component}/admin/entities/{entity}` returns Management
  Console entity form metadata from the resolved Web schema.
- `GET /form-api/{component}/admin/data/{data}` returns Management Console data
  form metadata from the resolved Web schema.
- `GET /form-api/{component}/admin/views/{view}` returns Management Console view
  form metadata from the resolved Web schema.
- `GET /form-api/{component}/admin/aggregates/{aggregate}` returns Management
  Console aggregate form metadata from the resolved Web schema.
- `POST /form-api/.../validate` validates input but does not execute the operation.
- operation execution still uses the REST operation path.

The stable JSON response contract for the definition endpoints is maintained in
`docs/design/web-form-api-schema.md`.

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

A Static Form App is a web app instance made from CML metadata, static HTML
pages, and convention based result resolution. Its value is that demo
applications and internal tools can be built in the CML+HTML range without
writing Web framework code.

The Static Form App boundary is intentionally conservative. The preferred
approach is to use static pages and filename conventions as far as practical,
then use WebDescriptor settings only for the parts that cannot be expressed
cleanly as static files. If an application still needs programming-style
routing rules, conditional page flow, rich client state management, or other
advanced Web behavior, it should be implemented with a normal Web framework and
integrated with CNCF through REST or `/form-api`.

The initial app registry is conceptual:

```text
console -> controlled operation execution
manual  -> read-only reference navigation
```

The baseline navigation paths are:

```text
/web/manual
/web/console
```

Dashboard may link to these pages, but it must not inline Console actions.
The Manual remains read-only reference navigation. The Console may link to
operation forms, but operation execution remains on the existing `/form/...`
submission path.

Dashboard and Manual may hand off to Console by linking to `/web/console`.
They must not embed operation forms or action buttons inline. Console itself is
also an entry/navigation surface: it links to `/form/{component}` indexes and
does not execute operations inline.

The implemented baseline keeps `/web/console` as a controlled operation entry
page. It does not execute operations inline. It links to component operation
form indexes under `/form/{component}`. Operation form results first resolve
static result pages by filename convention, then fall back to descriptor-provided
or built-in result rendering.

Component admin pages also expose managed-data entry points for entity CRUD,
data CRUD, aggregate CRUD, and view read. These are component-scoped management
links; the first baseline defines the navigation contract before enabling
write-side CRUD behavior.

The first managed-data pages are intentionally read-only or placeholder
surfaces. Entity, aggregate, and view pages expose registered runtime metadata.
The data page reserves the datastore-record management entry point until the
write-side CRUD contract is defined.

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

## Relationship With CLI And Meta Projections

Static Form App, REST execution, and JSON Form API do not replace existing CLI
or meta projections.

The boundary is:

- CLI remains the command-line projection of Component / Service / Operation
  metadata and execution results.
- Meta projections such as help, describe, schema, and OpenAPI remain
  reference/projection surfaces.
- Static Form App is the browser-native HTML projection for form entry,
  result pages, dashboard, console, and manual pages.
- REST remains the operation execution backbone shared by browser and
  non-browser clients.
- JSON Form API is an input-preparation API for web/form clients and must not
  become a separate execution path.

These surfaces may share the same normalized component/service/operation
metadata, but they must not share presentation-specific response contracts.
For example, a CLI result uses the CLI `Presentable` stdout/stderr policy, a
REST call uses the JSON response envelope, and a plain HTML FORM submit resolves
to an HTML result page.

Manual/reference web pages may link to existing meta projections such as schema
or OpenAPI views, but WEB-02 does not redefine those projections.

## Result Page Template And Widgets

Static Form App result pages use a small property expansion syntax:

```html
${operation.label}
${result.status}
${result.id}
${form.body}
```

The template language intentionally does not provide control structures.
Structured views are rendered through Textus widgets. The namespace form
`textus:xxx` is the preferred notation because it makes the server-side widget
boundary explicit. The hyphen form `textus-xxx` is also accepted for HTML-tooling
compatibility. Widgets are consumed before the response HTML is returned; an
unexpanded `textus:` or `textus-` tag in the final HTML is a rendering error.

The first widget contracts are:

```html
<textus-result-view source="result.body"></textus-result-view>
<textus-result-table source="result.body" page="paging.page" page-size="paging.pageSize" total="paging.total" href="paging.href"></textus-result-table>
<textus-property-list source="result"></textus-property-list>
<textus-property-list source="form"></textus-property-list>
<textus-error-panel source="result"></textus-error-panel>
<textus:action-link source="result.action.primary"></textus:action-link>
<textus-action-link source="result.action.primary"></textus-action-link>
```

`textus:action-link` and `textus-action-link` are equivalent. The widget renders
an anchor for `GET` actions and an inline POST form for non-GET actions. If the
referenced action has no `href`, it renders nothing.

Command operations keep the CQRS default: command submission is asynchronous
unless the operation explicitly chooses a synchronous mode. A plain HTML Form
submission that receives a JobId exposes job metadata and an await action:

```text
result.job.id
result.job.href
result.action.await.name
result.action.await.label
result.action.await.href
result.action.await.method
```

When the command result does not provide any explicit actions,
`result.action.primary` points to the same await action. This gives Static Form
Apps a synchronous-feeling path without making command execution itself
synchronous. The user may submit the await action only when they need the final
operation result.

When a command result contains `result.id`, the renderer may synthesize a detail
action by naming convention. For operations named `post-xxx`, `create-xxx`, or
`update-xxx`, the detail action points to:

```text
/form/{component}/{service}/get-xxx/result?id={result.id}
```

The generated properties are:

```text
result.action.detail.name
result.action.detail.label
result.action.detail.href
result.action.detail.method
```

If the result has no explicit actions and no pending JobId, `result.action.primary`
also points to this detail action. This convention is intentionally conservative;
applications that need another read operation should return explicit action
metadata or use descriptor/static-template overrides.

The baseline await endpoint is:

```text
POST /form/{component}/{service}/{operation}/jobs/{jobId}/await
```

It calls the framework job-control operation and renders the final result using
the original operation result-page convention.

The result page property set is:

- `component`, `service`, `operation`, `operation.label`
- submitted form values under their original names
- submitted form values under `form.*`
- `result.status`, `result.ok`, `result.contentType`, `result.body`
- extracted result metadata such as `result.id`
- extracted asynchronous command metadata such as `result.job.id` and
  `result.job.href`
- extracted result outcome/message metadata as `result.outcome` and
  `result.message`
- extracted action metadata as `result.actions.count`, `result.action.0.*`,
  `result.action.primary.*`, and `result.action.{name}.*`
- `error.status`, `error.body` on failed responses
- paging properties under `paging.*`

`WebDescriptor.Form.resultTemplate` can replace the default result page body
for a form. The template uses the same property expansion and Textus widget
contracts as the built-in result page. It is intentionally still
expression-only: applications should introduce new Textus widgets rather than
add control syntax when richer rendering is needed.

Static HTML result pages are the default customization mechanism for ordinary
Form Apps. Given an operation form whose normalized operation name is `xxx`,
the result page resolver searches the web template root in this order:

Success:

- `xxx__200.html`
- `xxx__success.html`

Failure:

- `xxx__{status}.html`
- `xxx__error.html`

If no operation-specific page is found, the resolver may fall back to common
pages:

Success:

- `__200.html`
- `__success.html`

Failure:

- `__{status}.html`
- `__error.html`

This makes shared pages such as a common validation error page or common system
error page ordinary static files rather than descriptor logic.

The same property expansion and Textus widget contracts are available in these
files. A full HTML document is returned as-is after expansion; a fragment is
wrapped in the built-in Bootstrap 5 shell. Descriptor-level
`resultTemplate`, `successRedirect`, and `failureRedirect` remain available as
supplemental configuration for gaps in the static convention model. The
ordinary demo/internal-tool path should prefer CML definitions plus static HTML
files first, then descriptor settings where the static convention is not enough.
Applications that require dynamic transition logic or programming-like page
flow should use an external Web framework and call CNCF through REST or
`/form-api`.

## Form Admission Validation

Static Form App validation is performed before Operation dispatch. The same
resolved Web schema is used by:

- HTML form submit under `/form`
- JSON validation API under `/form-api/.../validate`
- form definition metadata returned by `/form-api`

Supported admission constraints are:

- requiredness from multiplicity or WebDescriptor override
- enum/select candidates
- datatype checks for boolean, numeric, and date-like fields
- `min`, `max`, `minLength`, `maxLength`, and `pattern`

Schema and `ParameterDefinition` constraints are authoritative. WebDescriptor
may add or narrow validation hints, but it must not widen model constraints. For
example, a descriptor can reduce `maxLength` or raise `min`, but a lower
`minLength`, higher `max`, or looser numeric range is ignored by the effective
schema.

Validation failures redisplay the HTML form with submitted values and field
errors. The Operation is not dispatched when admission validation fails.

## CML Web Hint Propagation

CML-derived Web extension metadata must travel through the shared schema model:

```text
CML field/parameter extension
  -> Schema.Column.web / ParameterDefinition.web
  -> EntityRuntimeDescriptor.schema / OperationDefinition
  -> WebSchemaResolver.ResolvedWebSchema
  -> HTML form and JSON form definition
```

Generated companion schemas are expected to carry `Schema.Column.web`. During
Component bootstrap, `ComponentFactory` copies the generated companion schema
into `EntityRuntimeDescriptor.schema` when the descriptor does not already carry
an explicit schema. The Web layer must then consume that schema directly; it
must not recover Web form hints through reflection or a parallel field list.

Cozy generation should therefore emit Web hints as shared schema metadata:

- Entity and Value fields: `Schema.Column.web`
- Operation parameters: `ParameterDefinition.web`
- Descriptor/package overrides: `WebDescriptor.Form.controls` and
  `WebDescriptor.AdminSurface.fields`

The CNCF side treats these as the only supported portable route for CML Web
extensions.

Paging is part of the initial table contract. A table widget reads the current
page, page size, optional total count, and page navigation href template through
property paths. The default property names are:

```text
paging.page
paging.pageSize
paging.chunkSize
paging.total
paging.href
```

Total count is not requested by default. Search/list operations may require an
extra count query on DB-backed implementations, so the caller must explicitly
request total count when it is needed. When total count is absent, the widget
still renders the current page and previous/current/next navigation, but omits
total-page navigation and page-number ranges. This keeps simple operation
responses usable while allowing list/search operations to opt into total-count
paging when needed.

Display page size and continuation chunk size are separate. The default display
page size is 20 rows. The default continuation chunk size is 1000 rows. A widget
uses `paging.pageSize` for visible rows, while the Form continuation mechanism
may use `paging.chunkSize` when retaining or fetching a larger in-memory result
window.

EntitySpace currently keeps many working sets in memory, so total count is
often available with little or no additional work. That is best-effort behavior,
not a hard guarantee across all storage backends.

Structured list/search Query field names are resolved through the entity schema,
not through WebDescriptor-only field lists. See `query-field-resolution.md` for
the `EntityQueryFieldResolver` boundary. Full-text search and embedding search
are intentionally outside that resolver and are tracked as future search
planning work.

The `paging.href` value connects the HTML representation to the operation
execution mechanism. It is a URL template with `{page}` and `{pageSize}` tokens:

```text
/form/{component}/{service}/{operation}/continue/{id}?page={page}&pageSize={pageSize}
```

The table widget renders page links by expanding this template. For plain HTML
FORM pages, those links should re-enter the matching `/form/.../continue/{id}`
route with the requested paging values. The continuation route preserves the
operation result set server-side and returns the requested page from that result
set without re-executing the search operation. This is a good fit for the
current memory-resident working set model.

Total count opt-in uses an explicit parameter, conventionally:

```text
paging.includeTotal=true
```

For Management Console pages this runtime opt-in is design-gated. The Web
Descriptor admin surface must declare `totalCount=optional` or
`totalCount=required`; otherwise `includeTotal` is ignored and the page keeps
using `hasNext`-only paging.

Management Console view and aggregate list/read pages do not call ViewSpace or
AggregateSpace directly from HTML rendering. They execute admin Operations and
render the returned Operation response. Inside those Operations, generated
default View browsers and Aggregate collections use context-aware runtime
access:

- View: `find_with_context`, `query_with_context`, `count_with_context`
- Aggregate: `query_with_context`, `count_with_context`

This is required because generated default surfaces may need the active
`ExecutionContext` to resolve EntityStoreSpace, DataStoreSpace, authorization,
observability, and DataStore total-count capability. Context-free Browser
methods are still useful for simple manual implementations and tests, but the
Form Web runtime path should use the context-aware API.

The total count policy is evaluated in two stages. First, the Web Descriptor
admin surface decides whether total count is disabled, optional, or required.
Second, the runtime backing store decides whether total count is actually
available. Optional requests degrade to `hasNext` paging with a warning when the
store reports unsupported; required requests fail at the admin Operation
boundary.

## Management Console CRUD Flow

The Management Console is the first practical driver for Form Web CRUD
foundation features. The same foundation must be reusable by ordinary Form Web
applications.

Management Console and ordinary Static Form App input forms use
`WebSchemaResolver.ResolvedWebSchema` as the effective form schema. The primary
source is CML-derived `Schema` / `ParameterDefinition` metadata; WebDescriptor
is a presentation override layer. Form field order follows the resolved schema
field vector, while list pages may opt into an explicit `id`-first strategy.
See `docs/notes/cncf-web-descriptor-minimum-schema.md` for the current merge and
ordering rules.

Implementation checkpoint:

- Operation HTML forms and Operation form definition JSON share the same
  resolved schema.
- Admin Entity create/update HTML forms and Entity form definition JSON share
  the same resolved schema.
- Admin Entity create and update use separate JSON definition routes when their
  field sets differ. The collection-level route describes create; the id-scoped
  update route describes edit/update.
- Admin Entity create resolves fields from `create` view fields when present
  and may omit `id`; the server completes the id before storing through
  `EntityCollection`.
- Admin Entity edit/update resolves fields from `detail` view fields and uses
  that same field set for validation and validation-error redisplay.
- Admin Data create/update HTML forms and Data form definition JSON use the
  same resolver path. When no declared schema exists, Data can still infer a
  best-effort field set from admin Data records.
- Admin Data create and update use separate JSON definition routes. Data does
  not yet define entity-style `create/detail` view fields, so both routes share
  the same `resolveData` field set for now.
- Admin Entity/Data create/update result pages expose the same core
  `result.status`, `result.ok`, and `result.body` names as ordinary Operation
  result pages where applicable.
- Admin View and Aggregate read form definition JSON resolve from their root
  entity schema plus WebDescriptor admin controls.
- Aggregate create and command screens remain Operation forms, so their schema
  source is the Aggregate Operation parameter definition.
- Admin View and Aggregate generic Form API definitions are read-oriented. They
  expose GET list/detail navigation metadata and do not expose generic
  create/update submit actions.

The required browser-visible flow is:

- list page with paging
- list-to-detail navigation
- list-to-edit navigation and update submission
- list-to-new navigation and create submission

The Management Console has separate entry points for each managed-data kind:

```text
/web/{component}/admin/entities
/web/{component}/admin/data
/web/{component}/admin/aggregates
/web/{component}/admin/views
```

Current implementation status:

- `entities`: baseline list, detail, edit, new, update POST, and create POST
  are implemented against `EntityCollection`.
- `data`: baseline list, detail, edit, new, update POST, and create POST
  are implemented against `DataStore`.
- `aggregates`: baseline definition detail and read result are implemented
  against `AggregateSpace` / `AggregateCollection`; create/command/update flow
  is operation-backed and action links are rendered when matching operations are
  exposed.
- `views`: baseline definition detail and read result are implemented against
  `ViewSpace` / `Browser`.

Entity management uses a two-level resource path. The first level is the
managed-data kind (`entities`), and the second level is the entity type name:

```text
GET  /web/{component}/admin/entities
GET  /web/{component}/admin/entities/{entityName}
GET  /web/{component}/admin/entities/{entityName}/{id}
GET  /web/{component}/admin/entities/{entityName}/{id}/edit
POST /form/{component}/admin/entities/{entityName}/{id}/update
GET  /web/{component}/admin/entities/{entityName}/new
POST /form/{component}/admin/entities/{entityName}/create
GET  /form-api/{component}/admin/entities/{entityName}
GET  /form-api/{component}/admin/entities/{entityName}/{id}/update
```

For example, a `SalesOrder` entity uses the normalized entity type segment:

```text
/web/{component}/admin/entities/sales-order
```

`/web/{component}/admin/entities` lists entity types. `/web/{component}/admin/entities/sales-order`
lists SalesOrder records with paging. `/web/{component}/admin/entities/sales-order/{id}`
shows one SalesOrder record.

The entity Form API routes mirror the create/update split:

- `GET /form-api/{component}/admin/entities/{entityName}` returns the create
  definition (`mode = admin-entity`) and uses `create` view fields when present.
- `GET /form-api/{component}/admin/entities/{entityName}/{id}/update` returns
  the update definition (`mode = admin-entity-update`) and uses `detail` view
  fields.

Entity list/detail/edit/new page rendering uses the same field-profile policy:

- list uses `summary` view fields.
- detail, edit, and update validation use `detail` view fields.
- new and create validation use `create` view fields, falling back to `detail`
  and then to the effective schema.

When `create` view fields omit `id`, the visible HTML form and create Form API
do not expose `id`. The admin create Operation completes the id server-side
before persisting the record. This keeps user-facing create forms small while
preserving a complete entity identity at the `EntityCollection` boundary.

Data and aggregate management also keep their managed-data kind as the first
level, then add a resource type or collection segment before the record id:

```text
GET  /web/{component}/admin/data/{dataName}
GET  /web/{component}/admin/data/{dataName}/{id}
GET  /web/{component}/admin/data/{dataName}/{id}/edit
POST /form/{component}/admin/data/{dataName}/{id}/update
GET  /web/{component}/admin/data/{dataName}/new
POST /form/{component}/admin/data/{dataName}/create
GET  /form-api/{component}/admin/data/{dataName}
GET  /form-api/{component}/admin/data/{dataName}/{id}/update
GET  /web/{component}/admin/aggregates/{aggregateName}
GET  /web/{component}/admin/aggregates/{aggregateName}/{id}
GET  /form-api/{component}/admin/aggregates/{aggregateName}
GET  /form-api/{component}/admin/views/{viewName}
```

Aggregate create/command/update flows are operation-backed:

- Create is available only when the aggregate exposes an Aggregate Root creation
  operation.
- Update is available only when the aggregate exposes an update or command
  operation.
- Read may call an aggregate read operation when one is exposed.
- If the aggregate does not expose the corresponding operation, the Management
  Console does not render that action.

The Management Console does not create or patch aggregate state directly. It
uses Component / Service / Operation execution for aggregate mutation, and uses
the read baseline only for metadata and query-backed inspection when an
operation-backed read action is not available. Application logic is expected to
be assembled around Aggregate Root capabilities: operations are the external
entry points, while aggregate creation and mutation call the Aggregate Root
`create` function or domain methods.

The current baseline routes aggregate create/update/command submissions through
the discovered `/form/{component}/{service}/{operation}` entry point. A
successful operation-result fixture still requires an HTTP ingress-capable
component; a component without HTTP ingress reports the normal operation result
page with the ingress error instead of mutating aggregate state directly.

The generic aggregate Form API definition is read-oriented. It returns the
aggregate read/list metadata and list/detail navigation actions. Aggregate
create/update/command schemas are obtained from the discovered component
Operation forms, not from a generic aggregate submit definition.

The aggregate operation binding is resolved from aggregate metadata to component
operation metadata:

- `AggregateCommandDefinition.name` names the aggregate command.
- `AggregateCreateDefinition.name` names the Aggregate Root creation operation.
- CML `#### CREATE` maps to `AggregateCreateDefinition`; CML `#### COMMAND`
  maps to `AggregateCommandDefinition`.
- A create or command definition may bind to a component operation with the same
  normalized name.
- If multiple services contain matching operations, the binding must become
  explicit before the action is rendered.
- Create/read/update operation categories are inferred conservatively from
  aggregate metadata; name heuristics are only a fallback for the baseline when
  the aggregate does not yet provide explicit create metadata.

Views have a separate read-oriented entry point. The first level lists view
definitions; the second level selects a view definition or view name:

```text
GET /web/{component}/admin/views
GET /web/{component}/admin/views/{viewName}
```

List pages must use the paging property model already defined for result
widgets. Detail, edit, and new pages must receive enough page properties to
return to the originating list page after success or validation error.

CRUD pages pass navigation and continuation state through page properties. The
minimum property set is:

```text
crud.component
crud.resourceKind
crud.resourceName
crud.entityName
crud.id
crud.mode
crud.origin.href
crud.success.href
crud.error.href
paging.page
paging.pageSize
paging.chunkSize
paging.href
```

`crud.origin.href` is the page to return to when the user cancels or after a
validation error redisplay. `crud.success.href` is the preferred page after a
successful update or create. `crud.error.href` is the page used when the form
submission cannot be applied and must be redisplayed.

List pages own the paging continuation. Detail, edit, and new pages must carry
the originating list paging values forward without re-executing the list query.
Update and create result pages must preserve those properties so the user can
return to the same list context.

Validation errors must redisplay the originating edit or new page as HTML. The
redisplayed page receives the submitted values and structured error properties:

```text
validation.failed=true
validation.summary
validation.field.{fieldName}.message
validation.field.{fieldName}.code
```

The form must preserve the submitted field values. It must also preserve
`crud.origin.href`, `crud.success.href`, `crud.error.href`, and the paging
properties so cancel and retry keep the user in the same list context. Plain
HTML FORM users must not be forced through a JSON-only validation path.

Operation HTML forms use the same Web Form API validation boundary before
dispatch. If schema-level input validation fails, the Operation is not
dispatched. The submitted form is redisplayed as HTML with a validation summary,
field-level Bootstrap feedback, and the original submitted values.

Admin entity/data create and update forms follow the same rule. The schema-level
check runs before admin Operation dispatch; invalid submissions redisplay the
originating new/edit page as HTML with Bootstrap field feedback and submitted
values preserved.

Validation hint expansion should keep the same boundary. `min`, `max`, `step`,
`minLength`, `maxLength`, and `pattern` are Web input admission hints when they
can be evaluated without Operation dispatch. The canonical source should be CML
metadata carried through `org.goldenport.schema.Schema` or
`ParameterDefinition`. WebDescriptor may add or narrow hints for a deployment,
but should not widen or remove model constraints. HTML attributes generated from
these hints are client usability aids; server-side validation remains
authoritative before dispatch.

Edit forms must support optimistic update and stale-form detection. The baseline
contract uses a hidden version token property:

```text
crud.version
```

The token may be derived from an entity revision, updated timestamp, ETag, or a
storage-specific version value. If no version source exists, the value may be
empty and the update proceeds without stale-form detection. When a submitted
`crud.version` does not match the current record version, the update must not be
applied. The page must redisplay the edit form with:

```text
validation.failed=true
validation.summary=Record was changed by another update.
validation.stale=true
```

The redisplayed form must preserve the submitted values and must provide links
back to the current detail page and originating list page.

Form submission remains under `/form`. User-visible pages remain under `/web`.
This keeps HTML page navigation separate from execution entry points.

### Standard Hidden Form Context

Plain HTML FORM applications need explicit hidden fields to carry page context
between list, detail, edit, update, result, and error pages. The baseline
context names are:

```text
crud.origin.href
crud.success.href
crud.error.href
paging.page
paging.pageSize
paging.chunkSize
paging.href
search.*
continuation.id
version
etag
csrf
```

`crud.origin.href` is the page the user came from. `crud.success.href` is the
preferred destination after a successful create/update/operation.
`crud.error.href` is the page to redisplay or link to after failure when a local
form redisplay is not available. Paging and search values preserve list
context. `continuation.id` identifies retained result sets for
continuation-backed paging. `version` or `etag` supports optimistic update and
stale-form detection. `csrf` is reserved for session/auth deployments.

The renderer should emit these fields as hidden inputs when values are present.
Validation error redisplay and result rendering must preserve them. Server-side
code must not blindly trust client-provided navigation or security values:
redirect destinations, continuation ids, version tokens, and CSRF values remain
server-validated.

Hidden form context is page context, not operation input. `/form` submission
handling preserves these values for validation redisplay and result templates,
but strips them before dispatching to the target Operation or admin mutation
handler. This keeps ordinary HTML FORM navigation state from leaking into
business parameters while still allowing static result pages to render links
back to the originating list/detail context.

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
