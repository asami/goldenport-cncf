Asynchronous Execution Model
Cloud-Native Component Framework

This document defines the asynchronous execution model
used by the Cloud-Native Component Framework.

It specifies the meaning and relationship of Command,
Event, and asynchronous execution semantics.

This document is normative.

Phase 22 note:

    - The Phase 6 baseline treated Commands as asynchronous Job executions by
      default.
    - Phase 22 supersedes that default for application Commands: ordinary
      one-Entity CRUD-style Commands are synchronous direct executions unless
      operation metadata or runtime configuration explicitly opts into Job
      management.
    - Asynchrony remains a first-class design choice, but it is no longer the
      implicit default for every Command.

For the canonical timer and scheduling boundary around asynchronous execution,
see:

    - docs/design/timer-scheduling-boundary.md


----------------------------------------------------------------------
1. Purpose of the Asynchronous Model
----------------------------------------------------------------------

The asynchronous model exists to:

    - decouple execution timing from invocation
    - enable resilient and scalable execution
    - prevent tight coupling between components

Asynchrony is a first-class design choice,
not an optimization detail.


----------------------------------------------------------------------
2. Fundamental Principle
----------------------------------------------------------------------

The fundamental principle is:

    - invocation does not imply immediate execution
    - execution does not imply synchronous completion
    - completion does not imply direct observation

This principle allows components to evolve independently
while preserving correctness.


----------------------------------------------------------------------
3. Command
----------------------------------------------------------------------

A Command represents an explicit request
to perform an operation.

Characteristics:

    - intent-oriented
    - targets a specific capability
    - expects execution to occur
    - synchronous direct execution by default for ordinary one-Entity
      CRUD-style operations
    - asynchronous Job execution when explicitly requested by command execution
      policy or runtime override
    - results are observed directly for synchronous Commands and indirectly for
      async Job Commands

A Command does not guarantee:

    - immediate execution
    - exclusive execution
    - successful completion

Commands express intent, not outcome.


----------------------------------------------------------------------
4. Event
----------------------------------------------------------------------

An Event represents a fact that has occurred.

Characteristics:

    - fact-oriented
    - immutable
    - zero or more consumers
    - no expectation of response

An Event:

    - does not request execution
    - does not encode intent
    - may trigger reactive behavior

Events emphasize decoupling
over coordination.


----------------------------------------------------------------------
5. Command vs Event
----------------------------------------------------------------------

Key differences:

    - Command
        * expresses intent
        * expects action
        * may initiate a Job when command execution policy requests Job
          management

    - Event
        * expresses fact
        * expects no action
        * may initiate zero or more Jobs

Commands coordinate.
Events inform.


----------------------------------------------------------------------
6. Job as the Execution Anchor
----------------------------------------------------------------------

Asynchronous execution is anchored by Jobs.

Rules:

    - async Command execution is tracked as a Job
    - synchronous direct Command execution is not necessarily tracked as a Job
    - Event-triggered execution is also tracked as Jobs
    - Jobs provide observability and lifecycle control
    - this rule applies at operation-call and event-driven execution granularity
    - asynchronous execution start is controlled by Job management
    - asynchronous execution must not bypass the JobEngine-owned scheduler
    - application-internal branching/selection remains application-owned for now

The normative timer/scheduling split for that scheduler is fixed in:

    - docs/design/timer-scheduling-boundary.md

Jobs decouple:

    - invocation from execution
    - execution from completion
    - completion from observation


----------------------------------------------------------------------
7. Execution Ordering
----------------------------------------------------------------------

The framework does not guarantee:

    - strict ordering between Jobs
    - synchronous visibility of results
    - causal ordering beyond explicit links

If ordering is required,
it must be modeled explicitly.

Implicit ordering is forbidden.


----------------------------------------------------------------------
8. Failure Semantics
----------------------------------------------------------------------

Failure is a valid and expected outcome.

Principles:

    - failure must be observable
    - failure must not corrupt system state
    - failure handling must be explicit

Failures are captured by Jobs,
not by synchronous call stacks.


----------------------------------------------------------------------
9. Retry and Idempotency
----------------------------------------------------------------------

Retry is an operational concern.

Rules:

    - retries must be explicit
    - retries create new execution attempts
    - idempotency must be considered by domain design

The framework does not assume idempotency.
It provides tools to manage it.


----------------------------------------------------------------------
10. Relationship to ExecutionContext
----------------------------------------------------------------------

ExecutionContext is bound per execution attempt.

Asynchronous execution implies:

    - ExecutionContext is not shared across Jobs
    - context propagation is explicit
    - state must not leak between executions

ExecutionContext exists to isolate execution,
not to unify it.


----------------------------------------------------------------------
11. Observability and Correlation
----------------------------------------------------------------------

Asynchronous execution requires strong observability.

Required capabilities include:

    - correlation identifiers
    - execution tracing
    - Job state inspection
    - queue/backlog visibility
    - scheduler timing visibility
    - retry/delay visibility

Observability is not optional
in asynchronous systems.


----------------------------------------------------------------------
12. Relationship to Consumers (e.g. SIE)
----------------------------------------------------------------------

Consumers such as Semantic Integration Engine:

    - issue Commands
    - publish Events
    - observe Job outcomes
    - define retry and recovery policies

Consumers must not:

    - assume synchronous behavior
    - block on execution completion by default
    - bypass Job tracking
    - bypass the JobEngine scheduler for asynchronous start

Asynchrony must remain visible.


----------------------------------------------------------------------
13. Final Note
----------------------------------------------------------------------

The asynchronous model exists to protect the system
from temporal coupling.

If correctness depends on timing assumptions,
the model has already failed.
