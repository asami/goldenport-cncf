## Purpose of This Document

This document is a **specification exploration log** for Phase 3.1 Fat JAR Component.
It captures design discussions, decisions, trade-offs, and intermediate conclusions
prior to formal specification.

---

## Design Assumptions

- JVM-based execution
- Scala and Java components are treated equally
- Components may be stateful and host long-lived objects

---

## Provisional Conclusions

Provisional and confirmed conclusions derived from this journal
will be promoted to the formal design document:

- `phase-3.1.md`

This journal itself remains non-authoritative and exploratory.

## Fat JAR Component — Execution Model

In Phase 3.1, a Fat JAR Component is treated as a fully isolated
JVM-based execution unit.

Its internal implementation language (Scala, Java, or a mixture of both),
dependency graph, and build system are opaque to CNCF.
From the framework’s perspective, a Fat JAR Component is a black box.

A Fat JAR Component may host long-lived objects with internal state.
CNCF does not treat component execution as a mere subroutine invocation.
Instead, operations are invoked as method calls on a persistent component
instance that remains alive across multiple invocations.

Whether a component behaves in a stateful or stateless manner is entirely
an internal concern of the component and is not visible to CNCF.

---

## Execution Hub Responsibilities

The Execution Hub is responsible for managing the lifecycle and execution
boundaries of Fat JAR Components.

Its responsibilities include:

- Loading a Fat JAR Component within an isolated execution boundary
- Instantiating and maintaining a persistent component instance
- Dispatching operation invocations
- Enforcing declared concurrency policies
- Containing failures and returning Observations instead of propagating exceptions

The Execution Hub must ensure that failures inside a component never
crash or destabilize the CNCF runtime.

The following are explicitly NOT responsibilities of the Execution Hub
in Phase 3.1:

- Managing or interpreting component internal state
- Enforcing thread safety inside components
- Providing dependency injection frameworks
- Managing Docker environments or external processes
- Performing AI agent orchestration

---

## Concurrency Policy Model

Concurrency control is a framework-level responsibility.
Component implementations must not be required to implement their own
concurrency or synchronization mechanisms.

Concurrency policies are declared declaratively at one of the following levels:

- Component
- Service
- Operation

Lower-level declarations override higher-level ones.

The Execution Hub is responsible for enforcing these policies.

---

## Supported Concurrency Policies (Phase 3.1)

### serialize

Under the `serialize` policy, operation invocations are dispatched
sequentially with single-threaded semantics.

While one operation is executing, subsequent invocations wait until the
current execution completes.
No enqueuing or asynchronous job scheduling is involved.

This policy is the default for stateful components and provides
a simple, intuitive execution model.

Timeouts and cancellations are handled by the Execution Hub:

- A waiting invocation may time out before execution.
- For a running operation, the hub may attempt cancellation on timeout
  or explicit cancel requests.

Cancellation is best-effort and not guaranteed to immediately terminate
component execution.
In all cases, timeout or cancellation results in an Observation.

---

### concurrent

Under the `concurrent` policy, multiple operation invocations may be
executed in parallel.

The Execution Hub performs scheduling and dispatch, but does not impose
serialization.
Components declaring this policy are expected to tolerate concurrent
execution.

---

### queue

Under the `queue` policy, operation invocations are not executed immediately.
Instead, they are enqueued into CNCF’s job management system.

Execution is performed asynchronously by worker processes.
This policy aligns naturally with CNCF’s Command execution model and is
intended for high-throughput, non-blocking workloads.

The Execution Hub is responsible for enqueuing, scheduling, execution,
and result collection.

---

## Failure Semantics

Failures occurring at any stage of execution—including loading,
waiting, dispatching, execution, timeout, or cancellation—are treated
as data.

Such failures must be converted into Observations and returned through
normal execution paths.

At no point may a failure inside a Fat JAR Component cause the CNCF
runtime itself to terminate.

## Q3 — Observation Generation Points

This section explores where and how Observations are generated
during Fat JAR Component execution in Phase 3.1.
This is a design exploration, not a finalized specification.

---

### Observation as Data

In Phase 3.1, failures are treated as data.
Any abnormal condition occurring during execution must be converted
into an Observation and returned through the normal execution path.

No failure may directly terminate or destabilize the CNCF runtime.

---

### Execution Stages and Observation Points

Observation generation points can be classified by execution stage.

---

### 1. Load Phase

**Description**
- Loading the Fat JAR
- Establishing isolated execution boundaries (e.g., ClassLoader)

**Typical failures**
- JAR not found
- Invalid JAR format
- ClassLoader initialization failure
- Entry-point resolution failure

**Observation**
- Component is not instantiated
- No operation execution occurs
- Observation is returned immediately

---

### 2. Instantiation Phase

**Description**
- Creating a persistent component instance

**Typical failures**
- Constructor failure
- Static initialization error
- Dependency resolution error inside the component

**Observation**
- Component instance is not available
- Execution Hub remains alive
- Observation is returned

---

### 3. Dispatch / Policy Enforcement Phase

**Description**
- Applying concurrency policy (serialize / concurrent / queue)
- Deciding execution timing

**Typical failures**
- Policy violation
- Invalid policy declaration
- Resource exhaustion preventing dispatch

**Observation**
- Operation is not executed
- Failure is reported as Observation

---

### 4. Waiting Phase (serialize)

**Description**
- Operation invocation waits for prior execution to complete

**Typical failures**
- Wait timeout
- Explicit cancellation request

**Observation**
- Operation is never executed
- Observation represents waiting failure or cancellation

---

### 5. Execution Phase

**Description**
- Actual operation method invocation

**Typical failures**
- Unhandled exception in component code
- Execution timeout
- Best-effort cancellation failure

**Observation**
- Operation execution fails
- Component instance may remain alive
- Observation is returned

---

### 6. Queue / Job Execution Phase

**Description**
- Operation is enqueued as a Job
- Execution performed asynchronously

**Typical failures**
- Enqueue failure
- Worker crash
- Job execution failure

**Observation**
- Observation may be returned synchronously (enqueue failure)
- Or asynchronously via job result

---

### Observation Scope and Granularity

Observations are always scoped to an **operation invocation**.

- Component-level failures are surfaced through operation calls
- No global or process-level failure propagation is allowed

---

### Non-goals (Phase 3.1)

The following are intentionally not addressed in Phase 3.1:

- Retry semantics
- Failure aggregation
- Cross-operation compensation
- Distributed failure handling

These concerns are deferred to later phases.

---

### Provisional Conclusion

Observation generation is tied to execution stages rather than
implementation details.

This stage-based classification provides a stable foundation
for integrating Phase 2.9 Observation taxonomy in later refinement.
