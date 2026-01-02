----------------------------------------------------------------------
ExecutionContext (CNCF Runtime Contract)
----------------------------------------------------------------------

## Audience
This document defines the ExecutionContext contract for consumers of CNCF,
such as the Semantic Integration Engine (SIE).

Consumers MUST rely only on this specification.
Knowledge of goldenport core specifications is not required.

## Purpose
CNCF ExecutionContext represents an immutable, runtime-wide execution
assumption snapshot provided by CNCF.

It is constructed at bootstrap and injected into runtime flows,
including ingress, operation call construction, and egress.

## Provisioning
- CNCF constructs ExecutionContext during server startup.
- Construction is explicit.
- ExecutionContext is shared and read-only.
- ExecutionContext is NOT created per request.

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
