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

- `admin.entity.list`
- `admin.entity.read`
- `admin.entity.create`
- `admin.entity.update`
- `admin.data.list`
- `admin.data.read`
- `admin.data.create`
- `admin.data.update`
- `admin.view.read`
- `admin.aggregate.read`

## Open Items

- RuntimeConfig keys for dispatcher mode and REST base URL
- header allowlist for REST propagation
- trusted subject propagation model
- timeout and retry policy
- REST response body mapping for Form result pages
- executable specification for REST dispatcher request construction
