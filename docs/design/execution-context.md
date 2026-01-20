----------------------------------------------------------------------
ExecutionContext (CNCF Runtime Contract)
----------------------------------------------------------------------

## Audience
This document defines the ExecutionContext contract for consumers of CNCF,
such as the Semantic Integration Engine (SIE).

Consumers MUST rely only on this specification.
Knowledge of goldenport core specifications is not required.

## Purpose
CNCF ExecutionContext represents an immutable, action-scoped execution
assumption snapshot provided by CNCF.

ExecutionContext is composed from:
- SystemContext (system-scoped runtime assumptions)
- core ExecutionContext.Core (VM-level execution baseline)

The Action ExecutionContext is the only point where these layers merge.

## Provisioning
- CNCF constructs SystemContext during server startup.
- Construction is explicit.
- SystemContext is shared and read-only.
- ExecutionContext is created per action and bound to ActionCall.

Constraints:
- SystemContext MUST NOT hold core ExecutionContext.Core.
- core ExecutionContext.Core and SystemContext are parallel layers.
- Action ExecutionContext is the sole merge point.

## Admin system ping (runtime introspection)

The `admin.system.ping` operation provides minimal runtime
introspection information derived from the current
ExecutionContext and SystemContext.

This operation is intended for validation and inspection of
the active CNCF runtime, not as a sentinel connectivity check.

### Output (text)

The output is a human-readable text block with fixed field order:

- runtime: goldenport-cncf
- runtime.version: <current runtime version>
- mode: command | server | client
- subsystem: <subsystem name>
- subsystem.version: <subsystem version>

Notes:
- The previous sentinel-style response (e.g. `ok`) is not used.
- Structured output via suffix (e.g. `ping.json`) is intentionally
  deferred to Phase 2.8+.
- A blank line separates runtime and mode sections.

Output format semantics, canonical formats, and suffix-based selection
are defined in `docs/spec/output-format.md`.

Canonical output format: text (see docs/spec/output-format.md).

Design Notes (Security)
-----------------------
- Security policy definitions live in SystemContext as system-scoped policy handles.
- Policy evaluation uses the Action ExecutionContext as input at the execution boundary.

## Visibility
- Available to ingress adapters
- Available to operation call construction
- Available to egress adapters
- NOT visible in domain function signatures

## Mutability
- ExecutionContext MUST NOT be mutated.
- ExecutionContext MUST NOT be replaced during runtime.

## Relationship to CanonicalId
- ExecutionContext does NOT contain CanonicalId.
- CanonicalId is provided separately as a correlation identifier.
- CanonicalId flows alongside ExecutionContext but is not part of it.
- ExecutionContext no longer owns or wraps any UniversalId; TraceId/SpanId/CorrelationId normalization is performed inside the ScopeContext hierarchy as part of the GlobalRuntimeContext → ScopeContext → ObservabilityContext wiring before the identifiers reach ExecutionContext.
- Observability identifiers live in `ExecutionContext.cncfCore.observability` and are described by the canonical event shape, so refer to `docs/design/event-shape.md` for TraceId/SpanId/CorrelationId field semantics and to `docs/design/id.md` for overall UniversalId policy.
- CLI adapters ultimately render ExecutionContext results through the Presentable stdout/stderr policy documented in `docs/notes/phase-2.8-infrastructure-hygiene.md#purpose-aware-string-rendering-candidate` (see A-3 for the locked Phase 2.8 contract).

## Relationship to core ExecutionContext
CNCF ExecutionContext MUST satisfy the invariants defined by the
underlying core ExecutionContext principles.

These principles are internal to CNCF and are not part of the public
CNCF consumer contract.

## Non-goals
- Environment detection semantics
- Runtime state management
- Observability state
- Request-scoped data

----------------------------------------------------------------------
END OF DOCUMENT
----------------------------------------------------------------------
