# Event vs Message/Signal Boundary

## Context

CNCF currently uses an internal polling utility in tests and demos to wait for
read-side visibility after command-side processing. That is only a temporary
measure. The primary mechanism should be event-driven notification.

## Direction

The main line for CNCF is to use an `Event` mechanism.

This is especially natural for components running inside the same subsystem,
because they already share the same runtime context, correlation context, and
execution environment.

## Boundary

The notification model should be split into two scopes.

### 1. Intra-subsystem

Within a subsystem, CNCF components should communicate state changes through
`Event` publication and subscription.

Typical examples are:

- command accepted
- projection updated
- read model updated
- indexing completed
- job completed
- retry or failure state reached

Mechanically this is the easiest target, and it should be treated as the first
scope of the CNCF event mechanism.

### 2. Outside the local subsystem event scope

Outside that scope, a different delivery abstraction is needed.

There are two cases.

- Subsystems that share a Service Bus
- External applications such as web applications running outside CNCF

For subsystems sharing a Service Bus, event fan-out is still possible. In that
case the event mechanism can be extended across subsystem boundaries through the
shared bus.

For other boundaries, integration should be treated separately through
`Message` or `Signal` style mechanisms.

## Working Interpretation

Current working interpretation:

- same subsystem: `Event`
- different subsystems with shared Service Bus: `Event` fan-out is possible
- otherwise: use another mechanism such as `Message` or `Signal`

## Temporary Policy

Polling-based waiting remains an internal utility for tests, executable specs,
and demos only.

CNCF will likely need an application-facing asynchronous abstraction later,
probably job-oriented or built on top of an event mechanism. Until that design
is clarified, internal polling support must not be treated as the public model.
