Execution Context
Cloud-Native Component Framework

This document defines the concept, responsibility, and constraints
of ExecutionContext within the Cloud-Native Component Framework.

This document is normative.


----------------------------------------------------------------------
1. Purpose of ExecutionContext
----------------------------------------------------------------------

ExecutionContext represents explicit execution-time facts
bound to an OperationCall.

Its primary purpose is to:

    - separate domain logic from runtime concerns
    - provide controlled access to cross-cutting concerns
    - prevent leakage of infrastructure details into domain code

ExecutionContext is a runtime construct.
It is not a domain object.


----------------------------------------------------------------------
2. Scope and Responsibility
----------------------------------------------------------------------

ExecutionContext is responsible for:

    - managing UnitOfWork lifecycle
    - providing interpreters for effect execution
    - coordinating commit / abort semantics
    - representing execution-scoped runtime state

ExecutionContext does NOT:

    - contain domain logic
    - perform business rule evaluation
    - expose transport-level details
    - define application-specific semantics


----------------------------------------------------------------------
3. Visibility and Propagation
----------------------------------------------------------------------

ExecutionContext is not implicit.

Design constraints:

    - ExecutionContext must not appear
      in domain function signatures
    - domain logic must not construct or mutate it
    - ExecutionContext is bound explicitly
      to OperationCall at creation time


----------------------------------------------------------------------
4. Relationship to OperationCall
----------------------------------------------------------------------

ExecutionContext is bound to OperationCall
before execution begins.

OperationCall is responsible for:

    - holding the bound ExecutionContext
    - exposing controlled runtime capabilities
    - executing domain logic within that context

Engine does not inject or mutate ExecutionContext.


----------------------------------------------------------------------
5. UnitOfWork Integration
----------------------------------------------------------------------

ExecutionContext provides access to UnitOfWork.

Responsibilities include:

    - providing a UnitOfWork instance
    - providing interpreters for UnitOfWorkOp
    - controlling commit / abort semantics

Typical access pattern:

    - domain logic describes intent via UnitOfWorkOp
    - interpreters are applied at runtime boundaries
    - ExecutionContext coordinates lifecycle

UnitOfWork must be execution-scoped,
not global.


----------------------------------------------------------------------
6. Effect Interpretation
----------------------------------------------------------------------

ExecutionContext may expose interpreters for different effect types.

Examples include:

    - UnitOfWorkOp ~> Id
    - UnitOfWorkOp ~> Try
    - UnitOfWorkOp ~> Either[Throwable, *]

The choice of interpreter:

    - is a runtime decision
    - must not affect domain semantics
    - must not leak into domain code

Interpreters exist to adapt execution,
not to change meaning.


----------------------------------------------------------------------
7. Lifecycle Management
----------------------------------------------------------------------

ExecutionContext controls execution lifecycle.

Lifecycle operations include:

    - commit()
    - abort()
    - dispose()

Design constraints:

    - commit is idempotent or safely guarded
    - abort must leave no partial effects
    - dispose must release all runtime resources

Lifecycle operations must be invoked
by infrastructure layers only.


----------------------------------------------------------------------
8. Serialization and Tokens
----------------------------------------------------------------------

ExecutionContext may be convertible into a token.

Typical use cases:

    - logging
    - tracing
    - correlation identifiers
    - debugging

The token:

    - must not expose sensitive data
    - must not encode domain semantics
    - is for identification only

Round-trip reconstruction is optional
and implementation-dependent.


----------------------------------------------------------------------
9. Construction
----------------------------------------------------------------------

ExecutionContext instances are created by infrastructure.

Typical construction paths:

    - CLI / REST / MCP adapters
    - runtime adapters
    - test harnesses

Domain code must never construct ExecutionContext.

Factory methods may exist
for controlled creation and testing.


----------------------------------------------------------------------
10. Error Handling
----------------------------------------------------------------------

ExecutionContext must not throw unchecked exceptions
during normal operation.

Errors should be:

    - captured in effect types
    - mapped via interpreters
    - surfaced through OperationCall results

ExecutionContext is responsible for containment,
not for policy decisions.


----------------------------------------------------------------------
11. Relationship to Consumers (e.g. SIE)
----------------------------------------------------------------------

Consumers such as Semantic Integration Engine:

    - receive ExecutionContext from the framework
    - must not specialize or fork its semantics
    - may adapt it via thin adapters

ExecutionContext is shared infrastructure,
not a consumer-specific abstraction.


----------------------------------------------------------------------
12. Final Note
----------------------------------------------------------------------

ExecutionContext exists to be:

    - invisible to domain logic
    - unavoidable for infrastructure
    - boring in behavior
    - strict in boundaries

If ExecutionContext becomes visible in domain APIs,
the architecture has already failed.
