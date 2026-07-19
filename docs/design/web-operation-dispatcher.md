# Web Operation Dispatcher

/*
 * @since   Apr. 15, 2026
 * @version Jul. 19, 2026
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
- `items`: visible list result items. Each item has `id`, `label`, and `value`.
  `id` is the stable identifier used by detail navigation; `label` is the
  browser display label; `value` is the rendered raw value. Entity/data list
  results keep `ids` as the compatibility id vector and also expose equivalent
  `items`.
- `page`: requested page number, default `1`
- `pageSize`: requested page size, default `20`
- `hasNext`: whether another page is available, calculated by fetching one
  extra item beyond `pageSize`
- `total`: total result count, only when `includeTotal=true` is requested and
  `totalCountPolicy` allows it and the surface can calculate it
- `totalAvailable`: whether `total` is present

Read Operations return a record with:

- `kind`: `{surface}.read`
- `component`: normalized component name when the surface is component scoped
- `collection`: normalized entity, data, view, or aggregate name
- `id`: requested record id for entity/data read results
- `record`: structured record for entity/data read results
- `item`: structured read item with `id`, `label`, and `value`
- `label`: browser display label for a single read result
- `value`: rendered raw value for a single read result
- `fields`: newline-delimited display fields used by HTML rendering
- `values`: legacy display values for view/aggregate list read results
- `page`: requested page number for view/aggregate read results, default `1`
- `pageSize`: requested page size for view/aggregate read results, default `20`
- `hasNext`: whether another page is available for view/aggregate read results,
  calculated by fetching one extra item beyond `pageSize`
- `total`: total result count for view/aggregate read results, only when
  `includeTotal=true` is requested, `totalCountPolicy` allows it, and the
  surface can calculate it
- `totalAvailable`: whether `total` is present

For list reads, `items` is the primary structured result shape. HTML rendering
must use `items[].id` for detail links and `items[].label` for display so that a
display value does not accidentally become the resource identifier. Entity/data
`ids` and view/aggregate `values` remain compatibility/display bridges; they
must not be treated as the canonical detail id source when `items` is present.

For single reads, `item` is the primary structured result shape. Top-level
`id`, `label`, and `value` are duplicated for simple renderers and form widgets.
Entity/data read results also expose the original structured `record`.
`fields` may be derived from the record or item fields for table rendering.

`page` and `pageSize` must be positive integers. Invalid values are argument
errors at the target admin Operation. `includeTotal` defaults to `false`
because some backing stores need a separate or expensive count query.
`totalCountPolicy` defaults to `disabled`, so browser requests cannot enable
total counting unless the application design declares it. Supported policy
values are `disabled`, `optional`, and `required`; current implementation treats
`optional` and `required` as total-count enabled only when the backing surface
reports total count support. `optional` degrades to no `total` on unsupported
stores and returns `totalUnavailableReason` plus a warning message. `required`
fails with an argument error when total count is unsupported.

View and aggregate read Operations resolve their backing access through the
runtime `ExecutionContext`. Generated default View and Aggregate surfaces must
not create a fresh execution context while serving browser reads. The runtime
context is the authority for EntityStoreSpace, DataStoreSpace, authorization,
observability, and future Web-tier/Application-tier dispatch separation.

Default generated View browsers use the context-aware Browser API:

- `find_with_context`
- `query_with_context`
- `count_with_context`

The context-free `find`, `query`, and `count` methods remain available for
simple manual Browser implementations and executable specifications, but are
not the primary runtime path for generated default Views. Generated default
Views may reject context-free calls when their implementation needs the runtime
context.

Default generated View and Aggregate total-count capability is resolved from
the root entity's backing DataStore at runtime. A View or Aggregate can expose a
count function and still report `Unsupported` for the current runtime if the
backing DataStore cannot provide total count. Admin paging policy must consult
the runtime capability before invoking count.

Create/update Operations are synchronous browser form commands in the current
baseline and return a scalar status message. Their persistence effect must occur
inside the target Operation, not in the Web HTML route.

## Typed Update Parameter Normalization

HTTP and Form adapters preserve update intent until the selected Operation and
its parameter metadata are available. The shared request-construction boundary
normalizes update directives before generated request binding and before any
single-value Record access can discard duplicate occurrences.

The public operand-less command carrier is
`<field>__update_command=clear|null`. Existing value-bearing carriers remain
`<field>__overwrite`, `<field>__prepend`, `<field>__append`, and
`<field>__remove`. A blank Form value remains absent/no-op and is not an alias
for either operand-less command.

Explicit value intent uses `<field>__value=<value>`. Unlike an ordinary Form
control, this carrier assigns a zero-length string when it is present with an
empty operand. Metadata-sensitive Form integrations may use
`<field>__value_or_clear=<value>` for collection fields and
`<field>__value_or_null=<value>` for nullable scalar fields. Their zero-length
operand selects the named command; a non-empty operand follows the ordinary
typed assignment path. Absence remains no-op, and whitespace is not treated as
an empty operand.

Compatibility is determined from generated source-field metadata, not request
multiplicity or field naming. Collection `clear` becomes an empty typed
collection assignment. Scalar `null` becomes `Update.setNull` only when the
source field permits null assignment. Incompatible, unknown, duplicate, or
conflicting directives fail before ActionCall construction as structured
argument failures.

URL-encoded Form, multipart Form, REST/query input, and JSON Record input use
the same normalizer. Ordinary JSON arrays, including an empty array, remain
ordinary values unless the explicit command carrier is present. The normalized
request continues through the normal Operation, authorization, observability,
ActionCall, UnitOfWork, and persistence boundaries.

### Typed Update Request Pipeline

Typed update handling is part of shared Operation request construction, not a
Form-renderer behavior and not an ActionCall-specific parser. The pipeline is:

```text
transport occurrences
  -> preserve explicit carrier occurrences
  -> normalize one directive per base parameter
  -> validate against CmlOperationField.update metadata
  -> materialize typed value under the base parameter name
  -> construct the OperationRequest
  -> create and execute the ActionCall
```

The transport adapter may remove an ordinary blank Form control only after it
has distinguished typed carrier names. It must preserve a zero-length
`__value`, `__value_or_clear`, or `__value_or_null` occurrence because presence
is semantic input for the shared normalizer.

Directive normalization owns occurrence grouping, duplicate/conflict checks,
and zero-length adaptive behavior. Metadata mapping owns collection/null
compatibility and generated `Update` values. Request materialization removes
the carrier suffix and supplies the base Operation parameter. Consequently,
generated request binders and ActionCalls consume only normal parameter names
and typed values; they do not know the HTTP carrier grammar.

This boundary also ensures that explicit empty string, empty collection, and
explicit null remain present ActionCall arguments. Omission remains the only
no-update representation. Invalid directives terminate request construction
before ActionCall creation, while valid directives use the ordinary
authorization, observability, ActionCall, and UnitOfWork execution path.

The static parameter contract is defined in
`docs/spec/http-form-typed-update-parameters.md`.

## Open Items

- RuntimeConfig keys for dispatcher mode and REST base URL
- header allowlist for REST propagation
- trusted subject propagation model
- timeout and retry policy
- REST response body mapping for Form result pages
- executable specification for REST dispatcher request construction
