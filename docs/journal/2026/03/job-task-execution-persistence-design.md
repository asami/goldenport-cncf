CNCF Job/Task Execution and Persistence Design (Handoff)
=======================================================

status=work-in-progress
published_at=2026-03-21

---

# 1. Overview

This document defines the execution and persistence model for CNCF Job and Task management.

The goal is to:

- unify all execution under Task/Job
- control persistence independently from execution
- enable observability-driven lightweight execution
- support future features such as retry, compensation, and distributed execution

---

# 2. Core Concepts

## 2.1 Task

Task is the smallest execution unit.

- corresponds to a single ActionCall
- always executed under a Job

Definition:

```
Task = execution unit of ActionCall
```

---

## 2.2 Job

Job is a collection of Tasks.

- represents a unit of asynchronous execution
- used for management, tracking, and control

Definition:

```
Job = collection of Tasks
```

Important:

- Job is NOT the execution unit
- Task is the actual execution unit

---

## 2.3 Execution Model

All execution MUST follow this structure:

```
ActionCall
  → Task
    → Job
      → execution
```

Rules:

- ActionCall MUST NOT execute directly
- Task MUST belong to a Job
- Event processing MUST also generate Tasks

---

# 3. JobContext Integration

ExecutionContext MUST include JobContext.

```
ExecutionContext
  └─ JobContext
       ├─ jobId
       ├─ currentTask
       ├─ taskStack
       └─ trace metadata
```

All ActionCall execution is performed within JobContext.

---

# 4. Event Integration

Event does not execute logic directly.

Instead:

```
Event
  → Subscription
    → ActionCall
      → Task
        → Job execution
```

Event acts as:

```
Event = Task generation trigger
```

---

## 4.1 Event Continuation

Event-driven execution supports two models:

### Same Job (synchronous)

```
Job A
  ├─ Task1
  ├─ Event
  └─ Task2
```

### New Job (asynchronous)

```
Job A
  ├─ Event
        ↓
Job B
```

Event metadata should include:

- correlationId
- causationId
- parentJobId (optional)

---

# 5. Persistence Model

Execution and persistence are separated.

```
Execution model = always Task/Job
Persistence     = policy-driven
```

---

## 5.1 Job Persistence Policy

```
Persistent Job
Ephemeral Job
```

---

### Persistent Job

Stored in DB.

Use cases:

- Command execution
- asynchronous processing
- retry / compensation
- failure analysis
- long-running jobs

---

### Ephemeral Job

NOT stored in DB.

Use cases:

- Query execution
- event with no matching subscription
- lightweight internal processing

---

# 6. Initial Policy

The initial persistence rules are:

```
NOT persisted:
- Query
- Event with no matched Subscription
```

All other executions are persisted.

---

# 7. Observability Model

Observability is used for all executions.

```
Persistent Job → DB + Observability
Ephemeral Job  → Observability only
```

Observability log MUST contain:

- execution trace
- event routing
- task lifecycle

---

# 8. Debug and Trace

## 8.1 Debug Information

Job MUST store debugging information:

- request summary
- parameters
- execution notes

---

## 8.2 TraceTree

TraceTree represents execution structure:

```
Job → Task → Event → Task
```

Example model:

```
TraceTree
  └─ TraceNode
       ├─ id
       ├─ kind
       ├─ name
       └─ children[]
```

---

## 8.3 Storage Strategy

Initial:

- store TraceTree as JSON

Future:

- structured storage
- OpenTelemetry integration

---

# 9. Task Model

Task structure:

```
Task
- taskId
- jobId
- parentTaskId
- actionCall
- status
- startedAt
- finishedAt
- result
```

---

# 10. Job Model

Job structure:

```
Job
- jobId
- rootTaskId
- tasks
- status
- createdAt
- completedAt
- persistencePolicy
- debugInfo
- traceTree
```

---

# 11. Status Model

## TaskStatus

```
Pending
Running
Succeeded
Failed
Cancelled
```

## JobStatus

```
Pending
Running
Succeeded
Failed
PartiallyFailed
Cancelled
```

Job status is derived from Task statuses.

---

# 12. Key Rules

1. All execution MUST go through Task/Job
2. ActionCall MUST NOT execute directly
3. Event MUST generate Tasks
4. ExecutionContext MUST contain JobContext
5. Persistence MUST be policy-driven
6. Observability MUST cover all executions

---

# 13. Future Extensions

- Job control (cancel / retry / suspend / resume)
- distributed execution
- saga / compensation
- OpenTelemetry integration
- persistent event store integration

---

End.
