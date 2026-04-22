Job Management
Cloud-Native Component Framework

This document defines the concept, responsibility, and semantics
of Job management within the Cloud-Native Component Framework.

Job management is a foundational runtime concern.
This document is normative.


----------------------------------------------------------------------
1. Purpose of Job Management
----------------------------------------------------------------------

Job management exists to make execution:

    - observable
    - controllable
    - recoverable

A Job represents a managed unit of execution.

Its purpose is to ensure that:

    - executions are traceable
    - failures are not silent
    - retries and compensation are explicit
    - runtime behavior is auditable


----------------------------------------------------------------------
2. What a Job Is
----------------------------------------------------------------------

A Job is a runtime record of execution.

A Job:

    - represents one execution attempt or lifecycle
    - has a well-defined start and end
    - records success, failure, or cancellation
    - may span asynchronous boundaries

A Job is not a domain concept.
It is an operational construct.


----------------------------------------------------------------------
3. What a Job Is Not
----------------------------------------------------------------------

A Job is NOT:

    - a domain entity
    - a business transaction
    - an event
    - a workflow definition

Jobs exist to manage execution,
not to model business meaning.


----------------------------------------------------------------------
4. Relationship to Command and Event
----------------------------------------------------------------------

Both Command and Event execution
are typically managed as Jobs.

    - Command → Job with intent and expectation
    - Event   → Job with reactive execution

Key differences:

    - Commands usually expect completion or failure
    - Events may have zero or more independent Jobs

Job management provides a uniform execution layer
regardless of trigger type.


----------------------------------------------------------------------
5. Asynchronous Scheduling Boundary
----------------------------------------------------------------------

Built-in asynchronous execution is owned by Job management.

Rules:

    - asynchronous execution must enter Job management first
    - asynchronous execution must not bypass the JobEngine scheduler
    - command operation execution and event-driven execution are the required
      granularity for this rule
    - command/event/workflow/JCL async paths at that granularity all execute
      through the same Job management path
    - built-in scheduling is job-centric, not a general scheduler platform
    - selection inside application logic remains application-owned unless a
      later design freezes a narrower runtime rule

The built-in scheduler is the JobEngine-owned job scheduler.

Its purpose is to:

    - control asynchronous start timing
    - flatten load
    - preserve traceability
    - make execution/backlog visible

This scheduler may own:

    - async job queueing
    - bounded worker concurrency
    - delayed retry enqueue/execution

It must not become:

    - a cron engine
    - a business-calendar scheduler
    - a workflow timer platform
    - a human-task scheduler


----------------------------------------------------------------------
6. Job Lifecycle
----------------------------------------------------------------------

A Job has a lifecycle.

Typical states include:

    - Created
    - Running
    - Succeeded
    - Failed
    - Aborted
    - Retried (logical, not necessarily persisted)

Transitions must be explicit and monotonic.

Once a Job reaches a terminal state,
it must not resume execution.


----------------------------------------------------------------------
7. Failure Semantics
----------------------------------------------------------------------

Failures are first-class outcomes.

Principles:

    - failures must be recorded
    - failures must be attributable
    - failures must not be silently swallowed

A failure may include:

    - error type
    - message
    - stack or causal information
    - execution context identifiers

Failure handling policy is not defined here.


----------------------------------------------------------------------
8. Retry Semantics
----------------------------------------------------------------------

Retries are an operational concern.

Job management provides:

    - retry tracking
    - attempt counting
    - correlation between attempts

Retry policy:

    - is defined by higher layers
    - may depend on error classification
    - must not alter domain semantics

A retry represents a new execution attempt
linked to the same logical Job.


----------------------------------------------------------------------
9. Compensation and Abort
----------------------------------------------------------------------

Abort represents intentional termination.

Abort semantics:

    - stop further execution
    - release resources
    - record abort reason

Compensation:

    - is not automatic
    - must be explicitly modeled
    - may be triggered by Job failure or abort

Job management coordinates execution state,
not business compensation logic.


----------------------------------------------------------------------
10. Relationship to ExecutionContext
----------------------------------------------------------------------

Each Job is associated with an ExecutionContext.

ExecutionContext provides:

    - execution-scoped resources
    - UnitOfWork coordination
    - commit / abort integration

Job lifecycle transitions
must coordinate with ExecutionContext lifecycle.


----------------------------------------------------------------------
11. Persistence and Storage
----------------------------------------------------------------------

Job persistence is implementation-dependent.

Possible storage backends include:

    - in-memory
    - database
    - log-based systems

Requirements:

    - Job identity must be stable
    - terminal state must be durable
    - partial state must be tolerable

Persistence strategy must not leak into domain logic.


----------------------------------------------------------------------
12. Observability and Metrics
----------------------------------------------------------------------

Job management enables observability.

Typical signals include:

    - start / end timestamps
    - duration
    - status
    - error summaries
    - correlation identifiers
    - queue backlog
    - scheduler start timing
    - retry delay timing
    - worker saturation indicators

Integration with tracing and metrics
is mandatory for asynchronous execution.

Rules:

    - asynchronous execution must remain traceable through Job management
    - metrics must be collected from the JobEngine-owned scheduler path
    - queueing/execution timing must be attributable to a Job
    - metrics must be accurate enough for performance tuning and incident
      investigation

If asynchronous work bypasses Job management,
the model fails its observability obligation.


----------------------------------------------------------------------
13. Product Boundary
----------------------------------------------------------------------

Job management is part of the CNCF built-in execution layer and follows
the Pareto 80/20 principle.

Its built-in scope is intentionally limited to the high-frequency 80%
of operational needs:

    - submission
    - bounded async scheduling
    - lifecycle/result visibility
    - await/query/history
    - retry/cancel/suspend/resume
    - sequential batch submission support

Job management must not grow into a workflow/orchestration platform.

JCL is treated as a submission language, not a workflow language.
Workflow progression belongs to the workflow layer, not to Job management.

For the execution-platform boundary, see:

    - `docs/design/execution-platform-boundary.md`

----------------------------------------------------------------------
14. Relationship to Consumers (e.g. SIE)
----------------------------------------------------------------------

Consumers such as Semantic Integration Engine:

    - initiate Jobs
    - observe Job outcomes
    - define retry and recovery policy

Consumers must not:

    - redefine Job semantics
    - bypass Job lifecycle tracking
    - treat execution as fire-and-forget by default

Job management is shared infrastructure.


----------------------------------------------------------------------
14. Final Note
----------------------------------------------------------------------

Job management exists to make execution failures
visible and survivable.

If execution can fail silently,
the system is already broken.
