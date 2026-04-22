# Timer and Scheduling Boundary

Status: draft

## Purpose

Define the canonical boundary for built-in timer and scheduling behavior in
CNCF after the introduction of the shared `JobEngine` scheduler.

This document is normative.

Its purpose is to fix what built-in timing is allowed to do, what it must not
grow into, and how later timer-related requests should be judged.

## Core Rule

Built-in timing is allowed only when it is operational job control.

This means:

- built-in scheduling is owned by `JobEngine`
- built-in timing exists to control execution, retry, and bounded recovery
- built-in timing does not create a general scheduling platform
- built-in timing does not introduce workflow or JCL timer semantics

The built-in scheduler is the `JobEngine`-owned job scheduler.

## Allowed Built-In Timing

The following built-in timing behavior is in scope:

- async queue dispatch timing inside `JobEngine`
- bounded worker scheduling and concurrency control
- delayed retry scheduling
- re-enqueue of delayed retry into the shared job queue
- bounded timing directly tied to job survivability or recovery, if later
  admitted by `TM-02`

These are valid because they are:

- operational
- bounded
- internal to execution control
- not a user-authored scheduling language
- not business-calendar semantics

## Disallowed Built-In Timing

The following timing behavior is out of scope for built-in CNCF execution:

- cron or recurrence
- business or calendar schedules
- workflow timers or wait semantics
- delayed workflow start semantics
- schedule authoring in `JCL`
- human reminder or task scheduling
- general timer registry or scheduler-platform semantics
- external connector orchestration driven by built-in timers

These needs belong to specialist engines, not to continued built-in expansion.

## Ownership Split

### JobEngine

`JobEngine` owns:

- shared async scheduling
- bounded operational timing for execution control
- retry scheduling
- recovery-driven delayed execution if explicitly admitted by a later phase

`JobEngine` must not become:

- a general scheduler platform
- a cron engine
- a business scheduler

### WorkflowEngine

`WorkflowEngine` owns progression selection only.

It must not gain:

- timer orchestration
- wait semantics
- delayed progression semantics
- schedule control

### JCL

`JCL` remains submission-only.

It must not gain:

- delayed-start semantics
- recurrence
- schedule DSL semantics

### Application Logic

Application-internal branching and selection remain application-owned for now.

This means the built-in scheduling rule applies at operation-call execution and
event-driven execution granularity, not inside arbitrary application internals,
unless a later phase freezes a narrower rule.

### External Engines

External engines own:

- cron-like recurrence
- business and calendar timing
- timer-rich orchestration
- long-lived schedules
- specialist connector orchestration

## Current Implementation Classification

The current runtime behavior is classified as follows:

- `JS-01`
  - all async operation-call and event-driven execution enters the shared
    `JobEngine` scheduler
- `PR-01`
  - queue ordering is `priority asc, FIFO`
- `OPS-01`
  - `RetryLater` uses delayed retry scheduling and then rejoins the shared
    queue

This accepted behavior does **not** imply:

- a general delayed execution API
- a schedule authoring surface
- workflow timer or wait features
- calendar semantics
- general orchestration expansion

## Design Rule

Timer and scheduling enhancements must be judged by this rule:

- allow built-in timing only when it is operational job control
- reject enhancements that move CNCF toward a general scheduling platform
- move timer-rich orchestration to external specialist engines

For the broader built-in execution 80/20 boundary, see:

- `docs/design/execution-platform-boundary.md`
