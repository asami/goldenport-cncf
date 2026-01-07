# Context and Scope Rules

Purpose
-------
This document defines the architectural rules for separating Context and Scope.
The separation is not observability-only; it is a general structure for
runtime boundaries and cross-cutting capabilities.

ScopeContext
------------
A ScopeContext represents an immutable, shareable execution boundary that owns and aggregates multiple contextual capabilities, including ObservabilityContext.

Rules:
- Immutable once created.
- Parent/child relationship is explicit.
- Shareable across Subsystem, Component, Service, and Action.
- Holds ObservabilityContext and other future contexts (e.g. ConfigContext, PolicyContext).

ScopeKind
---------
ScopeKind is an enum that expresses structural execution boundaries used by the system.

Values:
- Subsystem
- Component
- Service
- Action

Child Scope Creation
--------------------
Provide a single primitive API for creating child scopes.

Pseudocode:
```
createChildScope(kind, name)
```

Rationale:
- A single primitive avoids convenience methods that hide structure.
- The hierarchy remains explicit and predictable.

Context Ownership Rule
----------------------
- ScopeContext is the structural owner.
- ObservabilityContext is a capability attached to ScopeContext.
- No Context type should directly encode structural hierarchy.
- Parent/child relationships belong only to ScopeContext.

ObservabilityContext
--------------------
ObservabilityContext is a diagnostic capability held by ScopeContext, responsible only for trace/span correlation.

Rules:
- It is layered on top of ScopeContext.
- Output destinations (logs, metrics, traces) are deferred to later phases.
- Does not own ScopeKind or structural hierarchy; structure is provided by ScopeContext.

Design Principles
-----------------
- Avoid quick hacks during demos.
- Establish correct structure early.
- Defer backend and output details.

Non-Goals
---------
- Logging backend choice.
- Metrics/tracing implementation.
- OpenTelemetry integration.
