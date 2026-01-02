Glossary
Cloud-Native Component Framework

This document defines core terms used consistently
throughout the Cloud-Native Component Framework.

The purpose of this glossary is not education,
but vocabulary stabilization.

If a term is ambiguous here,
the design is already drifting.


----------------------------------------------------------------------
1. Component
----------------------------------------------------------------------

A Component is a deployable runtime unit.

A Component is responsible for:

    - lifecycle management
    - configuration binding
    - integration with infrastructure
    - execution orchestration

A Component does NOT contain domain logic directly.

It provides the runtime environment in which
domain logic (Componentlets) is executed.


----------------------------------------------------------------------
2. Componentlet
----------------------------------------------------------------------

A Componentlet encapsulates pure domain logic.

Characteristics:

    - deterministic
    - side-effect free, or explicitly controlled
    - independent of infrastructure
    - generated or authored from domain models (e.g. Cozy)

Componentlets are executed only through OperationCall.

They are treated as replaceable artifacts.


----------------------------------------------------------------------
3. OperationCall
----------------------------------------------------------------------

An OperationCall defines the execution boundary between:

    - domain logic (Componentlet)
    - runtime infrastructure (Component / Engine)

An OperationCall is responsible for:

    - binding execution context
    - applying cross-cutting concerns
    - mediating sync / async execution
    - mapping errors into Consequence / Conclusion

Domain logic must not observe infrastructure details
outside of OperationCall.


----------------------------------------------------------------------
4. ExecutionContext
----------------------------------------------------------------------

ExecutionContext represents implicit runtime information
available during operation execution.

Typical contents include:

    - locale
    - time / clock
    - user and session information
    - rule sets
    - tracing identifiers

ExecutionContext is implicitly propagated.

It must not appear in domain function signatures.


----------------------------------------------------------------------
5. Command
----------------------------------------------------------------------

A Command represents an explicit execution request.

Characteristics:

    - intent-oriented
    - targets a specific capability
    - expects execution to occur
    - asynchronous by default
    - managed under Job control

Commands express intentional coordination
between components.


----------------------------------------------------------------------
6. Event
----------------------------------------------------------------------

An Event represents a fact that has occurred.

Characteristics:

    - fact-oriented
    - zero or more consumers
    - no expectation of response
    - triggers reactive behavior

Events emphasize decoupling over determinism.

They are first-class execution artifacts
in Event-Centered Architecture.


----------------------------------------------------------------------
7. Job
----------------------------------------------------------------------

A Job represents a managed execution unit.

Jobs provide:

    - lifecycle tracking
    - success / failure recording
    - retry and compensation control
    - historical execution records

Both Command and Event executions
are typically managed as Jobs.


----------------------------------------------------------------------
8. Engine
----------------------------------------------------------------------

An Engine is the runtime executor that:

    - accepts OperationCalls
    - coordinates execution
    - applies policies (retry, tracing, metrics)
    - integrates with infrastructure

The Engine owns execution mechanics,
but does not own domain logic.


----------------------------------------------------------------------
9. Configuration Resolution
----------------------------------------------------------------------

Configuration Resolution is the process of:

    - discovering configuration sources
    - resolving precedence
    - merging configuration values deterministically
    - producing a single evaluated configuration result

Configuration resolution is separate from
configuration semantics.

See: config-resolution.md


----------------------------------------------------------------------
10. Final Note
----------------------------------------------------------------------

This glossary defines how words are used,
not how they are taught.

If a new term is introduced without being added here,
it should be considered a design defect.
