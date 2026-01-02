Execution Model
===============

This document defines the authoritative execution semantics
of the Cloud-Native Component Framework (CNCF).

This document is NORMATIVE.
All implementations MUST conform to the semantics defined here.


----------------------------------------------------------------------
Overview
----------------------------------------------------------------------

CNCF execution is Action-centered and scenario-driven.

Primary flow:

args / http
  -> prepare
  -> ActionCall
  -> execute

Scenario and program execution are driven by run.


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
- UnitOfWork binding (runtime ownership)

ExecutionContext:
- MUST be constructed outside Engine
- MUST NOT be created or mutated by Engine
- MUST be immutable during execution
- MUST be bound to ActionCall

ExecutionContext MUST NOT be passed implicitly.


ActionCall
----------

ActionCall represents an executable plan with bound context.

Characteristics:
- ExecutionContext is bound at creation time
- execute takes no parameters and no implicits
- accessed resources are declared via:

    accesses: Seq[ResourceAccess]

ActionCall declares intent, not effect.

ActionCall is a complete executable unit.


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


Scenario
--------

Scenario is a run target.

Scenario:
- may cross multiple layers
- MUST avoid asserting internal implementation details
- SHOULD focus on observable outcomes


----------------------------------------------------------------------
Execution Phases
----------------------------------------------------------------------

Execution of an ActionCall is strictly divided into phases.


Phase 1: Authorization (Pre-Execution)
--------------------------------------

- Authorization is evaluated
- No observability events are emitted
- If authorization fails:
  - the action is considered not started
  - observe_enter MUST NOT be called
  - observe_leave MUST NOT be called

Authorization failure is not an action failure.


Phase 2: Action Start
---------------------

- observe_enter(action) is emitted
- This marks the true start of the action
- Tracing, auditing, and metrics MAY begin here


Phase 3: Execution and Completion
---------------------------------

Success:
- the action executes
- runtime.commit() is called
- observe_leave(action, Success(result)) is emitted

Failure:
- runtime.abort() is called
- observe_leave(action, Failure(conclusion)) is emitted
- the failure represents action failure

An action that starts MUST always produce exactly one
observe_leave event.


----------------------------------------------------------------------
Observability Semantics
----------------------------------------------------------------------

Observability MUST reflect truth.

Situation                | observe_enter | observe_leave
-------------------------|---------------|----------------
Authorization failure    | NO            | NO
Action success           | YES           | YES (Success)
Action failure           | YES           | YES (Failure)

Observability MUST NOT lie about whether an action started.


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
Execution Naming and Boundaries
----------------------------------------------------------------------

prepare
- constructs Action or Scenario meaning structure
- MUST NOT execute
- MAY be used for validation or preview in Executable Specs

execute
- runs a prepared ActionCall
- MUST be limited to ActionCall execution

run
- drives Scenario, program, or workflow
- run MAY trigger multiple prepare and execute steps

prepare is intentionally constrained today.
It may be surfaced in the public API in the future,
but current implementations MUST keep prepare internal.


----------------------------------------------------------------------
Relation to Core Execution Model
----------------------------------------------------------------------

CNCF depends on goldenport core execution principles.

Differences:
- CNCF is Action-centered and scenario-driven
- CNCF binds UnitOfWork into ExecutionContext for runtime ownership
- CNCF emphasizes run as the Scenario driver

Core principles remain authoritative for primitives and invariants.
CNCF adds composition and execution structure on top.


----------------------------------------------------------------------
Design Invariants
----------------------------------------------------------------------

The following invariants MUST always hold:

- Authorization failure != Action failure
- ExecutionContext is bound to ActionCall
- Engine is a pure execution boundary
- Observability must never lie about whether an action started

These invariants are foundational to CNCF.
