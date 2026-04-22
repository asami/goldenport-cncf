# Execution Platform Boundary

Status: draft

## Purpose

Define the product boundary for built-in execution in CNCF.

CNCF provides a built-in execution layer because common applications need more
than raw action invocation. They need a practical way to:

- submit managed jobs
- observe execution status and outcome
- control retry/cancel/suspend/resume
- express lightweight event-driven progression

The built-in layer is designed according to the Pareto 80/20 principle.
It should strongly support the high-frequency 80% while remaining
intentionally small.

This is the same product strategy used by Static Form Web App:

- provide a built-in capability that is useful and production-relevant
- do not grow it into a full specialist platform
- use external dedicated engines when advanced needs become primary

## Built-In Execution Scope

CNCF provides two built-in execution capabilities:

- basic Job Management
- basic Workflow

These capabilities cooperate, but they are not the same layer.

### Built-In Job Management

Built-in Job Management covers:

- job submission
- bounded async scheduling
- sequential batch submission
- job lifecycle/status/result
- await/query/history
- retry/cancel/suspend/resume
- lineage/failure disposition/inspection
- job-level failure hooks through named actions
- scheduler-driven metrics/traceability for async execution

Built-in Job Management does not cover:

- workflow graph semantics
- branch/loop/parallel execution language
- timer-rich orchestration
- business transition semantics

### Built-In Workflow

Built-in Workflow covers:

- event-triggered workflow progression
- workflow registration
- entity resolution
- use of entity status/state machine context
- next-action decision one step at a time
- workflow instance progress/status recording
- delegation of actual execution to the existing runtime/job path

Built-in Workflow does not cover:

- full BPM/workflow authoring
- arbitrary graph orchestration
- timer/scheduler orchestration
- compensation engine
- human-task engine
- connector automation platform
- rich programmable workflow language

The initial built-in workflow model is intentionally narrow:

- match by `event + entity status`
- produce `next action` one-at-a-time

## Role Split

### JCL

`JCL` is the batch submission language.

Its purpose is to start work, not to describe workflow semantics.

`JCL` may contain:

- target action or workflow entrypoint
- parameters
- submit policy
- sequential batch structure
- job-level failure hook

`JCL` must not contain:

- workflow graph semantics
- event wait semantics
- branch/loop/parallel execution semantics

### StateMachine

State machine owns domain transition semantics.

It remains the semantic source/planner for transition meaning.
It must not become the execution manager.

### WorkflowEngine

`WorkflowEngine` owns lightweight orchestration progression.

It consumes:

- incoming event
- entity status
- state machine context

It decides the next action to request, but it does not directly replace runtime
execution and does not replace `JobEngine`.

### JobEngine

`JobEngine` owns operational execution lifecycle.

It remains authoritative for:

- submission
- async scheduling
- lifecycle/result
- control/query
- execution visibility
- queue/backlog visibility
- scheduler metrics for tuning and incident investigation

Built-in scheduler ownership is job-centric:

- the built-in scheduler is the JobEngine-owned job scheduler
- asynchronous execution must pass through JobEngine so it remains tracked,
  traceable, and measurable
- this applies to operation-call execution and event-driven execution
  granularity
- application-internal selection logic remains outside the current built-in
  scheduling rule
- WorkflowEngine and JCL do not own independent schedulers
- timing support remains limited to operational job control rather than a
  general scheduling platform

Workflow instances are not Jobs.
Jobs remain the execution substrate used by command/event/workflow paths.

## External Engine Handoff Line

Advanced orchestration belongs outside the built-in CNCF execution layer.

External dedicated engines are the intended answer when any of these become
primary needs:

- DAG/job-net orchestration
- branch/loop/parallel execution
- timers/schedules as workflow semantics
- compensation/saga orchestration
- connector-heavy integration automation
- large-scale human task routing
- rich programmable workflow language

The correct response to those needs is not endless built-in expansion.
When a need clearly belongs to the specialized 20%, the correct response is
to integrate with a specialist engine.

## Design Rule

CNCF built-in execution should be judged by this standard:

- strong enough to cover the high-frequency 80%
- small enough to leave the specialized 20% to external dedicated engines
- small enough to avoid becoming a half-built specialist orchestrator

When an enhancement would push CNCF past that boundary, it should be treated as
an integration boundary decision, not as a routine built-in feature addition.
