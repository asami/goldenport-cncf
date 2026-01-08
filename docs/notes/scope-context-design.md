# ScopeContext Design — Typed Logic Boundary with Value-Backed Context

status = draft  
since = 2026-01-08

## Purpose

`ScopeContext` represents an execution scope in CNCF
(Subsystem / Component / Service / Action).

The purpose of this design is to:

- Define a **clear decision point** for execution logic
- Transport observability, logging, and tracing data as
  **semi-structured information**
- Strictly separate **logic-bearing data** from
  **descriptive / observational data** at the type level

## Core Design

### ScopeContext is a value-backed abstract class

```scala
abstract class ScopeContext {
  def kind: ScopeKind
  def name: String
  def attributes: Record
}
```

`attributes: Record` is optional and may be empty; it exists solely
to carry descriptive or observational data.

### ScopeKind is a fixed structural enum

```scala
enum ScopeKind {
  case Subsystem
  case Component
  case Service
  case Action
}
```

### ScopeContext is immutable and shareable

- All instances are immutable.
- Any execution context may share a ScopeContext.
- ScopeContext is *not* request-scoped by default.

### ObservabilityContext is attached, not owned

ScopeContext carries ObservabilityContext, but does not interpret it.
ObservabilityContext only provides correlation capability.

```scala
case class ScopeContext(
  kind: ScopeKind,
  name: String,
  parent: Option[ScopeContext],
  observabilityContext: ObservabilityContext
)
```

## Child Scopes

A single primitive method creates child scopes:

```scala
def createChildScope(kind: ScopeKind, name: String): ScopeContext
```

Convenience helpers such as `createActionScope` or `createServiceScope`
are intentionally avoided.

This preserves a single, uniform entry point and avoids policy creep.

## Attributes

Attributes are a `Record` (semi-structured key/value pairs).
They are used for observation and debugging only.
Attributes must not influence execution logic.

## Non-Goals

- Choosing logging backends
- Enforcing metrics collection
- Implementing tracing/OTel exporters
- Introducing configuration or policy

## Status

This note is a design contract for Phase 2.5.
Implementation details remain minimal and subject to later refinement.

See also:
- conclusion-observation-design.md
- observability-engine-build-emit-design.md
