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
