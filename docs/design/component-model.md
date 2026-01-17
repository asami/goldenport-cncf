Component Model
Cloud-Native Component Framework

This document defines the component model used by
the Cloud-Native Component Framework.

It specifies the responsibilities, boundaries, and relationships
between Component, Componentlet, OperationCall, and Engine.

This document is normative.


----------------------------------------------------------------------
1. Purpose of the Component Model
----------------------------------------------------------------------

The component model exists to enforce a strict separation between:

    - domain logic
    - runtime infrastructure
    - execution orchestration

Its primary goals are:

    - to keep domain logic pure and replaceable
    - to make runtime behavior explicit and controllable
    - to prevent accidental coupling between layers

This model is foundational and must remain stable.


----------------------------------------------------------------------
2. Overview of Core Elements
----------------------------------------------------------------------

The component model consists of the following core elements:

    - Component
    - Componentlet
    - OperationCall
    - Engine

Each element has a clearly defined responsibility.
No element may subsume the role of another.


----------------------------------------------------------------------
2.1 Interaction Contract (Tentative)
----------------------------------------------------------------------

This section records a *tentative* but important conceptual extension
identified through the development of the Semantic Integration Engine (SIE).

While this concept is not yet formalized as part of the stable CNCF API,
it is preserved here to guide future evolution of the component model.

---------------------------------------------------------------------
2.1.1 Component Interaction Contract
---------------------------------------------------------------------

The Component Interaction Contract defines the complete set of interactions
that a Component accepts from its external environment.

It specifies:

    - which kinds of interactions are permitted
    - the structural and semantic constraints of those interactions
    - the boundary at which cross-cutting concerns (logging, tracing,
      retries, policies, etc.) are applied before execution

The contract intentionally encompasses both synchronous and asynchronous
interaction forms, without privileging either.

---------------------------------------------------------------------
2.1.2 Component Operation Language
---------------------------------------------------------------------

The Component Operation Language defines the synchronous interaction surface
of a Component.

It is a constrained domain-specific language used to express:

    - commands
    - queries
    - other request–response style operations

Operation Language expressions are *not executed directly*.
They are transformed into OperationCall instances, where execution context
binding and cross-cutting concerns are applied.

This language represents the contractual basis for constructing
OperationCalls, but does not itself imply execution semantics.

---------------------------------------------------------------------
2.1.3 Component Reception Language
---------------------------------------------------------------------

The Component Reception Language defines the asynchronous interaction surface
of a Component.

It specifies:

    - which events a Component may receive
    - the structure and semantics of those events
    - how external signals are admitted into the Component

Reception Language expressions are mapped to event-driven execution paths
(e.g. message consumers or event handlers), forming the asynchronous
counterpart to OperationCall-based execution.

---------------------------------------------------------------------
Design Note
---------------------------------------------------------------------

This Interaction Contract layer is intentionally documented as tentative.

The stable execution boundary of the CNCF remains the OperationCall.
However, this conceptual layer clarifies how Operations and Receptions are
*admitted* into the system before execution is coordinated by the Engine.

Future versions of the CNCF may formalize this layer explicitly, or may
subsume it into refined definitions of Component and OperationCall.

----------------------------------------------------------------------
3. Component
----------------------------------------------------------------------

A Component is a runtime container for execution.

Responsibilities:

    - lifecycle management
    - configuration binding
    - exposure of executable operations
    - integration with infrastructure
    - delegation to Engine

A Component:

    - owns no domain logic
    - does not perform business decisions
    - does not interpret domain semantics

A Component exists to host and coordinate execution,
not to define behavior.


----------------------------------------------------------------------
4. Componentlet
----------------------------------------------------------------------

A Componentlet encapsulates domain logic.

Characteristics:

    - pure or explicitly effect-controlled
    - deterministic given the same inputs
    - independent of infrastructure
    - generated or authored from domain models (e.g. Cozy)

A Componentlet:

    - must not access ExecutionContext directly
    - must not perform IO directly
    - must not depend on transport or runtime concerns

Componentlets are replaceable artifacts.
They define behavior, not execution.


----------------------------------------------------------------------
5. OperationCall
----------------------------------------------------------------------

An OperationCall defines the execution boundary
between domain logic and runtime infrastructure.

Responsibilities:

    - bind ExecutionContext
    - apply cross-cutting concerns
    - mediate sync / async execution
    - adapt effect interpretation
    - normalize error handling

OperationCall is the only permitted access point
from domain logic into runtime capabilities.

If logic bypasses OperationCall,
the component model is violated.


----------------------------------------------------------------------
6. Engine
----------------------------------------------------------------------

The Engine is the execution coordinator.

Responsibilities:

    - accept OperationCalls
    - schedule and execute operations
    - manage concurrency and isolation
    - apply policies (retry, timeout, tracing, metrics)
    - integrate with infrastructure

The Engine:

    - owns execution mechanics
    - does not own domain logic
    - must remain replaceable

Engines may differ by environment
(local, cloud, test),
but must preserve semantics.

Design Notes (Security)
-----------------------
- Component owns security and observability responsibilities at the runtime boundary.
- Domain logic remains pure and does not interpret security policy or outcomes.
- SecurityEvent is treated as a system-level event distinct from ActionEvent / DomainEvent.

Authorization Failure Handling
------------------------------

Authorization is evaluated *before* an ActionCall is constructed or executed.

If authorization fails:

- The ActionCall is **not created and not invoked**
- Action execution does not occur
- An ActionEvent with result = `AuthorizationFailed` is created
- The ActionEngine directly invokes `UnitOfWork.commit(events)`
- The event is persisted and published through the same 2-phase commit
  path as successful actions (UnitOfWork → EventEngine → DataStore)

This design ensures that authorization failures are:

- Fully observable and auditable
- Persisted using the same transactional guarantees as normal actions
- Clearly separated from action execution concerns

ActionCall remains the execution unit for *authorized* actions only.
Authorization failures are treated as first-class events, not exceptions.


----------------------------------------------------------------------
7. Execution Flow
----------------------------------------------------------------------

A typical execution flow is:

    1. A Component receives a request
    2. The Component constructs an OperationCall
    3. The Engine executes the OperationCall
    4. ExecutionContext is bound
    5. The Componentlet logic is invoked
    6. Effects are interpreted
    7. The Job lifecycle is completed

Domain logic never observes steps 1–4 or 6–7 directly.

Path resolution (CanonicalPath derivation) is defined in `docs/spec/path-resolution.md`; every incoming request must satisfy that specification before the flow above begins.


----------------------------------------------------------------------
8. Configuration Binding
----------------------------------------------------------------------

Configuration is bound at the Component level.

Rules:

    - configuration is resolved before execution
    - Componentlets receive configuration-derived values only
    - raw configuration objects must not leak into domain logic

Configuration resolution is external to this model.
See: config-resolution.md


----------------------------------------------------------------------
9. Error Semantics
----------------------------------------------------------------------

Error handling follows these principles:

    - domain logic describes failure, not transport
    - unchecked exceptions are avoided
    - errors are normalized by OperationCall
    - Engine applies retry and recovery policies

Error semantics must be consistent
across Components and Engines.


----------------------------------------------------------------------
10. Replaceability and Evolution
----------------------------------------------------------------------

Replaceability is a first-class design goal.

    - Componentlets may be regenerated
    - Engines may be swapped
    - Components may be redeployed

As long as contracts are preserved,
the system must continue to function.

Backward compatibility is defined
at the OperationCall boundary.


----------------------------------------------------------------------
11. Relationship to Consumers (e.g. SIE)
----------------------------------------------------------------------

Consumers such as Semantic Integration Engine:

    - assemble Components
    - configure Engines
    - supply ExecutionContext instances

Consumers must not:

    - alter the component model
    - embed consumer-specific assumptions
    - bypass OperationCall boundaries

This model is shared infrastructure.


----------------------------------------------------------------------
12. Final Note
----------------------------------------------------------------------

The component model exists to make execution boring
and domain logic free.

If domain code becomes aware of runtime mechanics,
the model has failed.
