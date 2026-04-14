# Aggregate Method Implementation Strategy

## Status

Design note. This note records the direction for generated aggregate methods,
their implementation strategies, authorization chokepoints, and the later
alignment with Operation / ActionCall implementation.

## Background

CML aggregate definitions can now describe create and command surfaces, but the
runtime behavior is still too close to Operation / ActionCall plumbing. The
desired model is aggregate-oriented:

- Create behavior is expressed as an aggregate type method.
- Update behavior is expressed as an aggregate instance method such as
  `updateNotice` or `changeStatus`.
- Authorization is enforced by the ActionCall aggregate DSL chokepoints, not by
  generated aggregate methods.
- Persistence remains outside the aggregate method and is handled by the
  authorized ActionCall aggregate DSL after a method returns the next aggregate
  state.

This keeps the same aggregate behavior available from Web Form, REST, CLI,
internal service calls, and future application drivers.

## Required Model

CML aggregate methods should become first-class executable definitions.

Aggregate create:

```text
Aggregate type method:
  Notice.createNotice(input): Consequence[Notice]
```

Aggregate command:

```text
Aggregate instance method:
  notice.updateNotice(input): Consequence[Notice]
  notice.changeStatus(input): Consequence[Notice]
```

The generated ActionCall should be thin:

```scala
for {
  current <- aggregate_load[Notice](id)
  updated <- current.updateNotice(input)
  saved <- aggregate_update("notice", id, "updateNotice", Consequence.success(updated))
} yield OperationResponse.create(updated.toRecord())
```

Create follows the same pattern without a current instance:

```scala
for {
  notice <- aggregate_create("notice", "createNotice", Notice.createNotice(input))
} yield OperationResponse.create(notice.toRecord())
```

## Authorization Placement

Aggregate authorization is enforced by the ActionCall aggregate DSL
chokepoints:

- `aggregate_load` / `aggregate_load_option`
- `aggregate_search`
- `aggregate_create`
- `aggregate_update`

Generated aggregate methods must not call `AggregateAuthorization` directly.
This avoids relying on application-side convention and keeps the security
boundary in CNCF-owned chokepoints.

`aggregate_create` performs type-level authorization before running the create
action:

```text
resourceFamily = aggregate
resourceType   = notice
targetId       = None
accessKind     = create:createNotice
```

`aggregate_update` performs instance-level authorization before running the
update action:

```text
resourceFamily = aggregate
resourceType   = notice
targetId       = Some(notice.id)
accessKind     = command:updateNotice
```

`aggregate_load` and `aggregate_load_option` perform instance-level read
authorization:

```text
resourceFamily = aggregate
resourceType   = notice
targetId       = Some(notice.id)
accessKind     = read
```

`aggregate_search` performs type-level search authorization. The permission
access kind is normalized to read for DAC-style permission evaluation.

The low-level persistence helper is private to CNCF. Application code should
not be able to directly put aggregate records and bypass `aggregate_create` or
`aggregate_update`.

Executable specs now fix the create/update chokepoints:

- `aggregate_update` denies before running update logic when the target
  aggregate backing entity permission denies update.
- `aggregate_create` denies before running create logic when the operation
  access policy denies create.

Aggregate methods may still implement business-specific invariants, state
transition guards, or domain-level checks. Those checks are not a substitute for
the CNCF authorization chokepoints.

## DSL Chokepoint Pipeline

Aggregate chokepoints are implemented on top of the generic DSL chokepoint
runner. The runner is intentionally not aggregate-specific; the same mechanism
is expected to be reused for Entity, View, Data, Admin, and later Form/Web DSL
entry points.

The generic model is:

```text
DslChokepointContext
  domain       = aggregate | entity | view | data | admin | form | ...
  operation    = create | update | load | search | ...
  component    = optional component name
  resource     = aggregate/entity/view/data name
  targetId     = optional target id
  command      = optional command name

DslChokepointRunner
  enter
  phase enter/leave
  leave
```

Initial phases are:

- `authorization`
- `resolve`
- `query`
- `method`
- `persistence`

The first hooks are:

- calltree hook
- observability event hook
- audit hook
- metrics hook

The default hook set is owned by `DslChokepointRunner`. An execution can
override the hook set through `ExecutionContext.FrameworkParameter`; this keeps
the initial extension point close to the execution boundary and avoids making
application code responsible for audit or authorization observability. A later
runtime-level configuration can feed the same framework parameter when hook
selection needs to come from `RuntimeConfig` or deployment policy.

Log-level policy:

- calltree is structural state and is not emitted as a normal log line by this
  hook.
- normal DSL observability events are `debug`; they are useful for tracing a
  chokepoint but should not appear in the default `info` runtime log.
- DSL audit events are `info`; failed authorization/method/persistence phases
  and persistence outcomes remain visible by default.
- metrics are in-memory dashboard state and are not controlled by log level.

This makes the chokepoint the single place where later concerns can be added or
changed:

- audit
- metrics
- tracing
- diagnostics
- dashboard event aggregation
- policy-specific reporting

The aggregate DSL now records calltree nodes like:

```text
dsl:aggregate.update
  dsl:aggregate.update.authorization
  dsl:aggregate.update.method
  dsl:aggregate.update.persistence
```

For `load`:

```text
dsl:aggregate.load
  dsl:aggregate.load.authorization
  dsl:aggregate.load.resolve
```

The same tree shape should be preserved when the mechanism is expanded to other
DSL domains.

Web-tier logic dispatch should target Operations. The current local execution
path calls the in-process CNCF subsystem, but the same dispatch point must later
support a client-like REST call from a Web-tier CNCF process to an
Application-tier CNCF process. The Web-tier chokepoint therefore belongs at
`web.operation.dispatch`, while Aggregate/Entity/Data authorization and audit
chokepoints should occur naturally inside the target Operation execution.
`WebOperationDispatcher` is the dispatch abstraction. The first implementation
is `WebOperationDispatcher.Local`, which delegates to the local
`HttpExecutionEngine`; a later REST implementation should keep the same
interface and change only the target behind the dispatcher. The stable design
contract is tracked in
[`docs/design/web-operation-dispatcher.md`](../design/web-operation-dispatcher.md).

Admin console CRUD should follow the same rule. The admin pages may provide HTML
forms and navigation, but create/update/read/list semantics should be represented
as admin Operations before the implementation is considered production-ready.
Direct renderer/store chokepoints are avoided because they couple Web tier pages
to Application-tier storage internals and bypass the intended operation boundary.

The initial audit hook emits DSL audit events for failed chokepoints and for
persistence phase outcomes. Authorization decisions still emit their own
authorization audit events; DSL audit events describe the higher-level DSL
operation attempt and the phase where it failed or persisted.

The initial metrics hook records aggregate DSL chokepoint totals and errors in
`RuntimeDashboardMetrics`. The dashboard can later expose this alongside HTML
requests, ActionCalls, and authorization decisions.

## IMPLEMENTATION Strategy

`IMPLEMENTATION` should describe how an executable aggregate method is realized.
It should not be limited to external Scala methods.

Initial implementation kinds:

- `not-implemented`
- `scala-external`
- `scala-inline`
- `pattern:*`

Examples:

```text
IMPLEMENTATION :: scala:org.example.NoticeAggregateImpl.updateNotice
```

```text
IMPLEMENTATION :: pattern:copy-update
```

```text
IMPLEMENTATION :: pattern:state-machine
TRANSITION :: publish
```

If no `IMPLEMENTATION` is specified, the generated method returns a structured
not-implemented error.

## Built-In Patterns

Common aggregate method patterns should be promoted into built-in
implementations as they become stable.

Expected candidates:

- `create`
- `copy-update`
- `patch-update`
- `state-machine`
- `state-transition`
- `publish`
- `unpublish`
- `activate`
- `deactivate`
- `approve`
- `reject`
- `assign`
- `unassign`
- `append-child`
- `remove-child`
- `soft-delete`
- `restore`
- `record-event-only`

State machine operations are especially important. A CML command should be able
to bind to a transition by name, allowing generated code to apply the
state-machine planner and keep authorization, invariant checks, state changes,
and events in one method contract.

## Factory Override

Generated components need an aggregate behavior variation point similar to
Operation / ActionCall factories.

Default generated aggregate behavior:

```scala
class NoticeAggregateFactory {
  def createNotice(input: CreateNoticeInput)(using ctx: ExecutionContext)
      : Consequence[Notice] =
    Consequence.notImplemented("Notice.createNotice")

  def updateNotice(current: Notice, input: UpdateNoticeInput)(using ctx: ExecutionContext)
      : Consequence[Notice] =
    Consequence.notImplemented("Notice.updateNotice")
}
```

Application code overrides this factory and implements the selected aggregate
methods.

The generated aggregate method should call the factory implementation after the
generated built-in prelude. Standard authorization remains in the ActionCall
DSL chokepoint. This makes the default path safe while preserving
application-specific business logic.

## Inline Scala

CML should eventually allow Scala implementation blocks for small, local
aggregate behavior.

The inline Scala execution scope should be fixed:

```text
current: Aggregate instance, if any
input: generated input DTO
ctx: ExecutionContext
```

Return type:

```text
Consequence[Aggregate]
```

Inline Scala is powerful, so it should be introduced after the external Scala
and built-in pattern paths are stable.

## Alignment With Operation / ActionCall

The aggregate method mechanism should be designed for later reuse by Operation
/ ActionCall generation.

Shared concepts:

- `ImplementationDefinition`
- `ImplementationKind`
- `ImplementationExecutor`
- `ImplementationContext`
- `notImplemented` fallback
- external Scala dispatch
- inline Scala generation
- built-in pattern dispatch
- authorization chokepoint selection
- validation
- event emission
- metrics and observability
- structured error handling

Differences to preserve:

- Operation / ActionCall returns `OperationResponse`.
- Aggregate method returns the next aggregate state.
- Operation / ActionCall may be submitted as a job.
- Aggregate method persistence is handled by the authorized ActionCall
  aggregate DSL.

The intended migration path is:

1. Introduce the shared implementation model for aggregate methods.
2. Generate aggregate method stubs and not-implemented defaults.
3. Add factory override points for aggregate behavior.
4. Add built-in patterns, starting with state-machine transitions.
5. Reuse the same implementation model in Operation / ActionCall generation.
6. Keep existing Operation factory override APIs as compatibility variation
   points.

## Open Items

- Define the exact CML grammar for `IMPLEMENTATION`.
- Decide how aggregate create input and command input DTOs are named.
- Move aggregate authorization from ActionCall-bound helpers to a service usable
  from generated aggregate methods.
- Define the formal repository API for `aggregate_create`, `aggregate_save`,
  and event emission.
- Decide how inline Scala code is compiled, isolated, and reported in errors.
- Add executable specifications for type-level and instance-level aggregate
  authorization.
