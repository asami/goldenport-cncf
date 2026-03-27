# Component Port Direction

## Decision

Introduce a `Port` concept on `Component`.

`Port` is the entry point for typed service access inside the same process.

The external protocol surface remains:

- component
- service
- operation

But internal wiring should be able to obtain a typed Scala service interface from `Component.port`.

## Why

Builtin components are currently exposing service/operation routes while their operation bodies call framework internals directly.

That is acceptable for a first step, but it is not the right long-term wiring model.

We want:

- protocol surface for CLI / REST / help / discovery
- typed service access for in-process component wiring

These are different roles.

## Core Shape

The minimum first shape is:

- `Component` has `port`
- `port` can return typed service interfaces
- builtin components provide their own service traits through that port

Example direction:

```scala
trait ComponentPort {
  def get[T](clazz: Class[T]): Option[T]
}
```

or Scala-native equivalent with `ClassTag`.

The first line does not need a rich generic registry.

It only needs a stable place where typed service interfaces are exposed.

## Service Traits

Builtin components should define typed service traits that correspond to their protocol surface.

### JobControl

- `JobService`
- `JobAdminService`

### Event

- `EventService`
- `EventAdminService`

The builtin component provides these traits through `port`.

Its protocol operation implementations should become thin facades over those typed services.

## Immediate Scope

The immediate scope is builtin components only.

That means:

- `job-control`
- `event`

The first phase does **not** need:

- generic user component wiring
- distributed service discovery
- CML-driven trait generation
- arbitrary third-party component port registration

## Future Direction

Later, normal components should be able to expose custom typed interfaces as well.

The likely path is:

- `Factory`
- overridable `Service`
- overridable `Operation`

Those structures can provide a typed interface behind the same protocol surface.

But that is future work.

## Near-Term Consequence

For the next stage:

1. add minimal `Component.port`
2. add builtin service traits
3. make builtin operation bodies delegate to those traits
4. keep existing external selectors stable

This lets us move toward typed component wiring without forcing a large redesign right now.
