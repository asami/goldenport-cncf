# Web Operation Dispatcher

/*
 * @since   Apr. 15, 2026
 * @version Apr. 15, 2026
 * @author  ASAMI, Tomoharu
 */

## Purpose

Web tier logic dispatch must target CNCF Operations. The Web tier receives HTML
forms and browser requests, builds an Operation-oriented HTTP request, and sends
that request through a `WebOperationDispatcher`.

The dispatcher boundary exists so the same Web application can run in two
topologies:

- local: Web tier and Application tier run in the same CNCF process.
- rest: Web tier CNCF calls an Application tier CNCF process through REST.

The Web tier chokepoint is `web.operation.dispatch`. Domain chokepoints for
Aggregate, Entity, Data, and authorization should occur inside the target
Operation execution.

## Interface

`WebOperationDispatcher` is the runtime extension point:

```scala
trait WebOperationDispatcher {
  def targetName: String
  def dispatch(request: HttpRequest): HttpResponse
}
```

The first implementation is `WebOperationDispatcher.Local`, which delegates to
`HttpExecutionEngine.execute`.

## REST Dispatcher Contract

A future `WebOperationDispatcher.Rest` must preserve the same logical request
shape as the local dispatcher.

Required inputs:

- base URL of the Application tier CNCF endpoint
- request method
- operation path
- query parameters
- form parameters or request body
- selected request headers
- trace id and correlation id
- authentication or session-derived forwarding data

Required output:

- `HttpResponse` with status code, content type, headers, and body
- response shape compatible with existing Form result rendering
- error response that preserves Application tier status and diagnostic text

Required propagation:

- trace id
- correlation id
- causation id when present
- authenticated subject attributes when a trusted propagation model is enabled
- tenant or workspace selectors when present

The REST dispatcher must not bypass Operation authorization. It forwards the
request to the Application tier where normal Operation, ActionCall, Aggregate,
Entity, and Data chokepoints execute.

## Audit And Observability

The Web tier records:

- `web.operation.dispatch.enter`
- `web.operation.dispatch.method.success|failure`
- `web.operation.dispatch.success|failure`

The Application tier records the target Operation execution details. This keeps
the Web tier responsible for dispatch observability and the Application tier
responsible for business authorization and persistence audit.

## Admin Console

Admin console CRUD must use the same dispatcher path. HTML pages can provide
navigation and forms, but create, update, read, and list behavior should be
represented as Operations before the implementation is considered production
ready.

Initial admin Operation candidates:

- `admin.entity.list`: implemented as the admin entity list query
- `admin.entity.read`: implemented as the admin entity read query
- `admin.entity.create`: implemented as the admin entity create command
- `admin.entity.update`: implemented as the admin entity update command
- `admin.data.list`: implemented as the admin data list query
- `admin.data.read`: implemented as the admin data read query
- `admin.data.create`: implemented as the admin data create command
- `admin.data.update`: implemented as the admin data update command
- `admin.view.read`: implemented as the admin view read query
- `admin.aggregate.read`: implemented as the admin aggregate read query

Implemented admin create/update Operations run synchronously for HTML FORM
submissions. The Web tier still enters through `web.operation.dispatch`; the
target admin Operation owns the persistence call to EntityCollection or
DataStore. This keeps browser-native admin forms compatible with a future REST
dispatcher topology where the Web tier and Application tier are separate CNCF
processes.

Implemented admin read/list Operations provide the corresponding query surface
for EntityCollection, DataStore, ViewSpace, and AggregateSpace. Management
Console HTML rendering uses these Operations as the read boundary; it must not
fall back to direct EntityCollection, DataStore, ViewSpace, or AggregateSpace
reads for compatibility.

### Admin Operation Response Records

Admin query Operations return `OperationResponse.RecordResponse`.

List Operations return a record with:

- `kind`: `{surface}.list`
- `component`: normalized component name when the surface is component scoped
- `collection`: normalized entity, data, view, or aggregate name
- `ids`: visible record ids for entity/data list results
- `page`: requested page number, default `1`
- `pageSize`: requested page size, default `20`
- `hasNext`: whether another page is available, calculated by fetching one
  extra item beyond `pageSize`

Read Operations return a record with:

- `kind`: `{surface}.read`
- `component`: normalized component name when the surface is component scoped
- `collection`: normalized entity, data, view, or aggregate name
- `id`: requested record id for entity/data read results
- `record`: structured record for entity/data read results
- `fields`: newline-delimited display fields used by HTML rendering
- `values`: display values for view/aggregate read results
- `page`: requested page number for view/aggregate read results, default `1`
- `pageSize`: requested page size for view/aggregate read results, default `20`
- `hasNext`: whether another page is available for view/aggregate read results,
  calculated by fetching one extra item beyond `pageSize`

`page` and `pageSize` must be positive integers. Invalid values are argument
errors at the target admin Operation.

Create/update Operations are synchronous browser form commands in the current
baseline and return a scalar status message. Their persistence effect must occur
inside the target Operation, not in the Web HTML route.

## Open Items

- RuntimeConfig keys for dispatcher mode and REST base URL
- header allowlist for REST propagation
- trusted subject propagation model
- timeout and retry policy
- REST response body mapping for Form result pages
- executable specification for REST dispatcher request construction
