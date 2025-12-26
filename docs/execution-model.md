Execution Model
===============

This document defines the authoritative execution semantics
of the Cloud-Native Component Framework (CNCF).

This document is NORMATIVE.
All implementations MUST conform to the semantics defined here.


----------------------------------------------------------------------
Core Concepts
----------------------------------------------------------------------

ExecutionContext
----------------

ExecutionContext represents execution facts.

It includes, but is not limited to:
- security context (principal, capabilities, security level)
- runtime facilities
- configuration snapshot
- observability context

ExecutionContext:
- MUST be constructed outside Engine
- MUST NOT be created or mutated by Engine
- MUST be immutable during execution
- MUST be bound to OperationCall

ExecutionContext MUST NOT be passed implicitly.


OperationCall
-------------

OperationCall represents an executable plan with bound context.

Characteristics:
- ExecutionContext is bound at creation time
- apply() takes no parameters and no implicits
- accessed resources are declared via:

    accesses: Seq[ResourceAccess]

OperationCall declares intent, not effect.

OperationCall is a complete executable unit.


Engine
------

Engine is the execution boundary.

Engine responsibilities:
- invoke authorization (decision only)
- manage runtime lifecycle (commit / abort / dispose)
- emit observability events
- coordinate execution phases

Engine MUST NOT:
- construct ExecutionContext
- mutate ExecutionContext
- make authorization decisions
- perform authorization enforcement
- use implicit ExecutionContext


----------------------------------------------------------------------
Execution Phases
----------------------------------------------------------------------

Execution of an OperationCall is strictly divided into phases.


Phase 1: Authorization (Pre-Execution)
--------------------------------------

- Authorization is evaluated
- No observability events are emitted
- If authorization fails:
  - the operation is considered not started
  - observe_enter MUST NOT be called
  - observe_leave MUST NOT be called

Authorization failure is not an operation failure.


Phase 2: Operation Start
------------------------

- observe_enter(op) is emitted
- This marks the true start of the operation
- Tracing, auditing, and metrics MAY begin here


Phase 3: Execution and Completion
---------------------------------

Success:
- the operation executes
- runtime.commit() is called
- observe_leave(op, Success(result)) is emitted

Failure:
- runtime.abort() is called
- observe_leave(op, Failure(conclusion)) is emitted
- the failure represents operation failure

An operation that starts MUST always produce exactly one
observe_leave event.


----------------------------------------------------------------------
Observability Semantics
----------------------------------------------------------------------

Observability MUST reflect truth.

Situation                | observe_enter | observe_leave
-------------------------|---------------|----------------
Authorization failure    | NO            | NO
Operation success        | YES           | YES (Success)
Operation failure        | YES           | YES (Failure)

Observability MUST NOT lie about whether an operation started.


----------------------------------------------------------------------
Error Handling
----------------------------------------------------------------------

All execution paths use org.simplemodeling.Consequence.

- scala.util.Try MUST NOT be used
- exceptions are converted to Conclusion
- failures are represented as Consequence.Failure
- success is represented as Consequence.Success

This model ensures:
- semantic failures
- composability
- auditability


----------------------------------------------------------------------
Authorization Model
----------------------------------------------------------------------

Authorization is two-layered.

1. Operation-level authorization
   - declarative
   - based on declared accesses
   - evaluated before execution

2. Runtime / internal authorization (future)
   - imperative
   - fine-grained
   - evaluated during execution

The same AuthorizationEngine MAY be used in both layers.


----------------------------------------------------------------------
Design Invariants
----------------------------------------------------------------------

The following invariants MUST always hold:

- Authorization failure ≠ Operation failure
- ExecutionContext is bound to OperationCall
- Engine is a pure execution boundary
- Observability must never lie about whether an operation started

These invariants are foundational to CNCF.
