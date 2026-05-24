Job Management
Cloud-Native Component Framework

This document defines the concept, responsibility, and semantics
of Job management within the Cloud-Native Component Framework.

Job management is a foundational runtime concern.
This document is normative.

For the canonical timer and scheduling boundary around Job management, see:

    - docs/design/timer-scheduling-boundary.md


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

In CNCF runtime management, a Job instance is also projected as a Job Entity.
This Job Entity is a `system` Entity and is the management/search/inspection
record for one execution lifecycle. It is not the reusable definition of how
Jobs should be launched.

Reusable Job definitions are managed separately as `system` JobDefinition
Entities.


----------------------------------------------------------------------
3. What a Job Is Not
----------------------------------------------------------------------

A Job is NOT:

    - a domain entity
    - a business transaction
    - an event
    - a workflow definition
    - a reusable Job definition

Jobs exist to manage execution,
not to model business meaning.


----------------------------------------------------------------------
4. Job Instance and JobDefinition
----------------------------------------------------------------------

CNCF distinguishes:

    - Job instance
    - JobDefinition

A Job instance is the concrete execution record. It has status, submitter,
runtime target, result summary, timeline summary, retry state, and control
state. The Job instance is represented by the Job Entity management projection.

A JobDefinition is a reusable definition record. It describes how a managed Job
may be launched and what behavior is intended.

JobDefinition should carry:

    - JCL source
    - normalized diagnostics profile
    - reserved future executable flow
    - reserved future executable events / onEvent section
    - version / revision / hash
    - draft / active / retired lifecycle state
    - target Action / Command binding metadata
    - owner / visibility / authorization metadata

Command, Action, or Operation metadata may bind to JobDefinition through a
future `jobDefinitionRef`. Inline JCL submission remains useful for
compatibility, debugging, and one-off operation, but it is not the normal
registry for reusable definitions.

When a Job is launched from a JobDefinition, the Job instance should retain:

    - jobDefinitionId
    - jobDefinitionVersion
    - jobDefinitionHash
    - declared profile snapshot
    - optional normalized JCL/source snapshot

This preserves auditability even when the reusable JobDefinition later changes.

Runtime residency is explicit for these system Entities. Active Jobs and Jobs
completed within the one-day operational confirmation window are Working Set
candidates. Active JobDefinitions are Working Set candidates so launch-time
definition lookup does not require store access in the common path.


----------------------------------------------------------------------
5. Relationship to Command and Event
----------------------------------------------------------------------

Command and Event execution
may be managed as Jobs when execution policy requires Job management.

    - Command → direct execution or Job with intent and expectation
    - Event   → Job with reactive execution

Key differences:

    - Commands usually expect completion or failure
    - Events may have zero or more independent Jobs

Job management provides a uniform execution layer
regardless of trigger type.


----------------------------------------------------------------------
6. Asynchronous Scheduling Boundary
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

The normative allowed/disallowed timing split is fixed in:

    - docs/design/timer-scheduling-boundary.md


----------------------------------------------------------------------
7. Job Lifecycle
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
8. Failure Semantics
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
9. Retry Semantics
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
10. Compensation and Abort
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

JM-04 fixes the Task boundary as the transaction boundary inside a managed Job.
For CNCF runtime purposes, an ActionTask or Aggregate execution is recorded as
one Task. The ActionEngine / UnitOfWork path still owns the actual commit and
abort mechanics; JobEngine records the transaction outcome as Task diagnostics:

    - `committed` when the Task completed successfully;
    - `failed` when the Task failed;
    - `compensation-committed` when an explicit compensation Task succeeded;
    - `compensation-failed` when explicit compensation failed.

Compensation between Tasks is explicit. A Task is compensatable only when its
JobTask / JobDefinition metadata names a compensation Action. When a later Task
fails after earlier Tasks have committed, JobEngine runs available compensation
Tasks in reverse committed order and records them as children in the Task
Execution Tree. CNCF does not infer business undo logic from Entity changes.

If a committed Task has no compensation action, or if compensation itself fails,
the Job is marked `recoveryRequired`, a `job.recovery-required` diagnostic event
is emitted, and the failure is preserved for human recovery. Compensation failure
is never hidden behind the original Task failure.


----------------------------------------------------------------------
11. Relationship to ExecutionContext
----------------------------------------------------------------------

Each Job is associated with an ExecutionContext.

ExecutionContext provides:

    - execution-scoped resources
    - UnitOfWork coordination
    - commit / abort integration

Job lifecycle transitions
must coordinate with ExecutionContext lifecycle.


----------------------------------------------------------------------
12. Persistence and Storage
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

Job Entity is a lightweight management projection. It should keep fields needed
for ordinary search, inspection, and control:

    - status
    - result summary
    - submitter
    - target component / service / operation
    - JobDefinition linkage
    - retry summary
    - task count
    - timeline summary
    - calltree reference or summary

Large or highly structured execution data should be stored separately:

    - full timeline
    - Task Execution Tree
    - task-local calltree
    - large result body
    - raw event history

Task Execution Tree is the canonical diagnostic structure for parent-child
execution relationships inside a Job. It records roots, subtasks, Event-driven
continuations, retries, compensation tasks, and causation metadata.

JM-04 exposes this tree through `job_control.get_task_execution_tree(jobId)` and
individual Task diagnostics through `job_control.get_task_detail(jobId, taskId)`.
These are inspection surfaces over JobEngine records; Job Entity remains a
lightweight management projection.

Task-local calltree is distinct from Job-level summary diagnostics. A task-local
calltree explains what happened inside one Task, such as ActionCall, UnitOfWork,
EntityStore, Event emission, external call, and compensation steps. Job Entity
should reference or summarize these records rather than embed every full tree.


----------------------------------------------------------------------
13. Observability and Metrics
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
    - explicit debug trace-job execution must make target requests inspectable through
      Job management without changing normal request result contracts
    - metrics must be collected from the JobEngine-owned scheduler path
    - queueing/execution timing must be attributable to a Job
    - metrics must be accurate enough for performance tuning and incident
      investigation

If asynchronous work bypasses Job management,
the model fails its observability obligation.

Query execution uses direct synchronous execution by default for performance.
When a caller supplies the debug trace-job option
(`textus.debug.trace-job=true` or CLI `--debug.trace-job`), Query execution
must use a persistent job and still return the same logical Query result. The
retained job becomes the canonical target for trace, timeline, result summary,
and calltree inspection.

HTTP adapters must publish the job reference as `X-Textus-Job-Id` whenever a
request creates or returns a managed job. CLI client adapters may use that
header for stderr-only debug guidance, but must not alter stdout/body result
contracts.

The job-control service must expose job-specific debug data from the persisted
job record. `job_control.job.get_job_calltree` returns the saved CallTree state
for a specific job and must not fall back to process-global "latest execution"
state.

----------------------------------------------------------------------
14. User Notification Provider
----------------------------------------------------------------------

Application user notifications are delivered through CNCF Event routing and a
service-provider boundary. JobEngine does not know user notification. It emits
ordinary Job lifecycle/recovery events, and a forwarding bridge converts
matching events to `UserNotificationProvider` requests.

CNCF defines `UserNotificationProvider` and resolves it from subsystem runtime
wiring:

    runtime:
      userNotification:
        providers:
          - name: textus-user-notification
            component: textus-user-notification
            channel: in-app
        eventForwarding:
          - event: job.succeeded
          - event: job.failed
          - event: job.cancelled
          - event: job.recovery-required

This provider is separate from `messageDelivery`. `messageDelivery` sends
external email/SMS-style messages. `userNotification` creates user-addressed
domain notifications, such as in-app notification records owned by
`textus-user-notification`.

The default forwarding policy treats a Job event as user-visible only when
application Web context such as `web.app` is present in Job event metadata.
System/admin or background async Jobs do not notify by default, though a
subsystem can explicitly opt in with an Event forwarding rule.

    - succeeded
    - failed
    - cancelled
    - recovery-required

Ordinary synchronous direct/no-Job Commands do not create user notifications.

The notification request includes:

    - recipient user id from the Job submitter subject/principal
    - Job id and status
    - application component/service/operation when available
    - result or failure summary
    - recovery-required flag
    - application Job detail URL such as `/web/{app}/jobs/{jobId}`

Provider absence or provider failure is non-fatal. Job status and result remain
authoritative. The Event forwarding bridge records forwarding diagnostics so
operators can see that the management notification projection is missing or
stale.

Duplicate terminal updates must not send duplicate notifications for the same
`(jobId, trigger)`.

`textus-blog` is the first deemed-subsystem driver for this policy. Its assembly
includes `textus-user-notification`, resolves the provider through
`runtime.userNotification.providers`, and uses explicit Job lifecycle/recovery
Event forwarding rules. Blog code does not write notification entities directly;
the Blog header only reads a user-facing summary from the notification component
and hides the badge when that component is absent or unavailable.


----------------------------------------------------------------------
15. Job Input Storage and Retention
----------------------------------------------------------------------

Large user-submitted work, such as bulk Information import, should be
represented as a Job. The Job is the durable work unit and the place from
which operators and applications navigate to the created or affected
Information.

Job input storage is split by size:

    - small input is stored inline on the Job entity;
    - large input is stored in BlobStore, while the Job entity stores only
      blob id, filename, content type, byte size, digest, and timestamps.

The default inline threshold is 64 KiB. Runtime configuration may adjust the
threshold.

Raw input must not be written to CallTree, provider spans, or summary Web
projections. These surfaces may show metadata such as filename, byte size,
digest, record count, skipped count, and result summary.

Job input retention defaults to TTL:

    - default policy: `ttl`
    - default TTL: 7 days
    - alternatives: `delete-on-completion`, `keep`

After a terminal Job state and the TTL has passed, inline raw bodies and
BlobStore payload references are cleaned while metadata, digest, and import
summary remain. Within TTL, a rerun may reuse the same Job input. After TTL,
the caller must submit the source file again.

Bulk Information import must not introduce an InformationSpace-specific
batch entity. If the application needs to group created Information by import
work unit, it records the Job id / task id in the Information import context
and filters Information by that context.


----------------------------------------------------------------------
16. Product Boundary
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

Job management must not accidentally grow into an unbounded workflow product.

JCL is the CNCF Job language. Its current implemented `profile` section is
diagnostics-only and is used for declared/observed comparison. Future executable
JCL may add procedural `flow` and Event-driven `events` / `onEvent` sections.
Those future sections are intended to express subtask launch, conditional Event
emission, Event reception, and Action chains while staying inside the Job
management boundary.

Executable JCL must remain distinct from the distributed Saga language.
Distributed, multi-subsystem, multi-machine, long-running coordination belongs
to Saga management.

For the execution-platform boundary, see:

    - `docs/design/execution-platform-boundary.md`

----------------------------------------------------------------------
17. Relationship to Consumers (e.g. SIE)
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
18. Final Note
----------------------------------------------------------------------

Job management exists to make execution failures
visible and survivable.

If execution can fail silently,
the system is already broken.
