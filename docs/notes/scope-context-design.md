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

ScopeContext carries an `ObservabilityContext` derived from its parent scope.
Each scope owns the deterministic rules that sanitize labels, produce the next `TraceId`/`SpanId` pair, and promote a `CorrelationId` when a subsystem boundary is crossed.
ObservabilityContext is therefore not an independently owned capability; it is a child of the scope and exists solely to expose correlation information.

```scala
case class ScopeContext(
  kind: ScopeKind,
  name: String,
  parent: Option[ScopeContext],
  observabilityContext: ObservabilityContext
)
```

This replaces the earlier Phase 2.6 ExecutionContext-local identifier wrapper (a quick-hack bootstrap artifact); Phase 2.8 introduces the permanent GlobalRuntimeContext → ScopeContext → ObservabilityContext wiring so identifiers live only in the scope hierarchy. CLI adapters ultimately render these scope-derived values via the Presentable stdout/stderr policy captured in `docs/notes/phase-2.8-infrastructure-hygiene.md#purpose-aware-string-rendering-candidate`.

## Child Scopes

A single primitive method creates child scopes:

```scala
def createChildScope(kind: ScopeKind, name: String): ScopeContext
```

Convenience helpers such as `createActionScope` or `createServiceScope`
are intentionally avoided.

This preserves a single, uniform entry point and avoids policy creep.

In Phase 2.8 the implementation ensures each child scope:

- sanitizes subsystem/service/action names for observability labels,
- derives the next `SpanId` (span kind is determined by `ScopeKind` plus the sanitized name),
- propagates the parent `TraceId`,
- and promotes a `CorrelationId` whenever a new subsystem scope is created.

The `ScopeContext` hierarchy thus owns all identifier normalization logic instead of scattering it through ExecutionContext helpers.

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
