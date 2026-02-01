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


## Design Adjustment — Decomposition of Execution Hub

Originally, the Phase 3.1 design assumed a central **Execution Hub**
with broad orchestration responsibilities.

During specification exploration, this concept was intentionally decomposed
as responsibilities were clarified and separated.

### Original Assumption

The initial Execution Hub concept included:

- Centralized orchestration of execution
- Coordination between execution forms
- Management of execution environments
- Acting as a conceptual “hub” of runtime behavior

This naming implied a level of authority and coordination
that is no longer present in Phase 3.1.

---

### Phase 3.1 Resolution

In Phase 3.1, execution control is fully concentrated in **ActionEngine**.

What remains from the original Execution Hub concept is limited to
pure execution infrastructure:

- Providing isolated ClassLoader instances
- Establishing JVM-level isolation boundaries
- Acting as a technical failure containment boundary

This reduced responsibility no longer justifies the term “Hub”.

---

### Renaming Decision

To reflect its actual responsibility, the former Execution Hub concept
is renamed to:

- **IsolatedClassLoaderProvider**

This component:

- Provides isolated ClassLoader instances only
- Performs no execution control or orchestration
- Is used internally by CollaboratorFactory
- Remains invisible to ActionCall and higher-level execution logic

---

### Status

This renaming and responsibility decomposition is considered **fixed**
for Phase 3.1.

The term “Execution Hub” should be treated as deprecated
within the Phase 3.1 design context.

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


## ActionEngine as the Single Execution Gate

In CNCF, all executions are governed by the ActionEngine.

This includes not only explicit operation invocations,
but also executions triggered by reception of events and messages.

---

### Scope of Execution

The following execution forms must pass through the ActionEngine:

- Operation invocation
- Event handling triggered from Reception
- Message handling triggered from Reception

Operation execution is considered one specialized form of execution
within a unified execution control model.

---

### Role of Reception

Reception components act solely as input triggers.

Their responsibilities are limited to:

- Receiving external inputs (command, API call, event, message)
- Normalizing inputs into execution requests
- Delegating execution control to the ActionEngine

Reception components must never execute component logic directly.

---

### Relationship Between ActionEngine and Execution Hub

ActionEngine is the primary execution controller.

It determines *when*, *how*, and *under which concurrency policy*
an execution is performed.

Execution Hub provides execution infrastructure services
such as ClassLoader management and component lifecycle handling.

Execution control always flows from ActionEngine to Execution Hub,
never the other way around.

---

## ActionCall — Unified Execution Unit

## Collaborator and CollaboratorActionCall

### Collaborator

Collaborator represents a cooperative entity that participates in execution
but is not itself an execution unit controlled by CNCF.

Collaborators are used by CNCF-managed execution logic but are not managed,
scheduled, or lifecycle-controlled by CNCF.

This abstraction allows CNCF to interact with cooperative entities
without expanding the execution control surface.

---

### ExternalCollaborator

ExternalCollaborator extends Collaborator and represents collaborators
that exist outside CNCF management boundaries.

Typical examples include databases, external services, tools, or libraries.
ExternalCollaborators are fully managed by the Fat JAR side and are outside
CNCF execution control.

---

### Collaborator Classification

ExternalCollaborator may be classified by interaction form, including:

- JarCollaborator
- RestCollaborator
- SoaCollaborator
- DockerCollaborator

In Phase 3.1, only JarCollaborator is within execution scope.
Other collaborator types are recognized conceptually but excluded
from Phase 3.1 implementation.

---

### CollaboratorActionCall

When execution targets a collaborator, the collaborator is explicitly
bound to the ActionCall.

Such ActionCalls are referred to as CollaboratorActionCall.

CollaboratorActionCall does not introduce special execution semantics.
It is treated identically to any other ActionCall by the ActionEngine.

The ActionEngine applies execution control uniformly to all ActionCall instances,
without interpreting the bound Collaborator.

ActionCall represents the unified execution unit in CNCF.

All executions governed by the ActionEngine are normalized into ActionCall,
regardless of their original trigger.

This includes:

- Operation invocations
- Event handling executions
- Message handling executions

---

### Purpose of ActionCall

The purpose of ActionCall is to:

- Represent a single executable intent
- Carry execution context and parameters
- Serve as the sole object scheduled and controlled by the ActionEngine

ActionCall does not perform execution control itself.
It is interpreted and executed under the authority of the ActionEngine.

---

### Relationship to Components

ActionCall is the only entity allowed to invoke component logic.

Concretely, an ActionCall invokes a method on a
`FatJarComponentInstance`.

Components are never invoked directly by Reception,
Execution Hub, or any other framework element.

---

### Concurrency and Scheduling

All concurrency semantics, including:

- serialization
- parallel execution
- queuing
- waiting
- cancellation
- timeout

are applied by the ActionEngine **to ActionCall instances**.

Components remain unaware of these concerns.

---

### Provisional Conclusion

By unifying all execution forms into ActionCall,
CNCF maintains a single, consistent execution model
across operation-driven, event-driven, and message-driven workflows.

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

## ClassLoader Isolation — Minimal Strategy

This section records the minimal ClassLoader isolation strategy
for validating Phase 3.1 Fat JAR Component execution.
This is a specification exploration log, not a finalized design.

---

### Purpose of Isolation

The purpose of ClassLoader isolation in Phase 3.1 is:

- to prevent dependency leakage between CNCF and Fat JAR Components
- to allow heterogeneous dependency graphs (Scala versions, libraries)
- to ensure that component failures do not destabilize the CNCF runtime

Isolation is treated as an execution boundary, not as a security sandbox.

---

### Minimal Isolation Requirement

Phase 3.1 adopts the following minimal requirement:

- Each Fat JAR Component is loaded with a **dedicated ClassLoader**
- The ClassLoader uses **parent = null**
- No implicit delegation to CNCF’s application ClassLoader is allowed

This forces all component dependencies to be resolved exclusively
from within the Fat JAR itself.

---

### CNCF–Component Boundary Classes

To allow interaction between CNCF and a Fat JAR Component,
a **shared API surface** is required.

Minimal assumptions:

- Shared interfaces and value types must be:
  - loaded by a common ClassLoader
  - dependency-free or extremely stable
- These classes form the only allowed crossing point
  between CNCF and the isolated component ClassLoader

All other classes must remain fully isolated.

---

### Component Instantiation Model

The Execution Hub is responsible for:

1. Creating the isolated ClassLoader
2. Loading the Fat JAR entry-point class
3. Instantiating the component object
4. Retaining the instance for subsequent operation invocations

The component instance must never escape its ClassLoader boundary.

---

### Failure Modes and Observation

Typical isolation-related failures include:

- Class not found due to missing dependency
- Linkage errors caused by version mismatch
- Static initialization failures

All such failures must be:

- caught within the Execution Hub
- converted into Observations
- returned through normal execution paths

At no point may a ClassLoader-related failure crash the CNCF runtime.

---

### Non-goals (Phase 3.1)

The following are explicitly out of scope for Phase 3.1:

- Hierarchical or layered ClassLoader designs
- Dynamic reloading or hot swapping
- Security hardening or sandbox enforcement
- Dependency shading or relocation strategies

---

### Provisional Conclusion

A parent-null ClassLoader strategy provides the strongest and
simplest isolation guarantee for Phase 3.1.

If this strategy works for a realistic Fat JAR Component,
all other execution forms become strictly easier to support.

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