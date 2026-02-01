# Phase 3.1 — Execution / Orchestration Foundation

status = active

This document defines the **design intent, scope, and boundaries**
of Phase 3.1 in the CNCF roadmap.

Phase 3.1 establishes the **Execution / Orchestration Hub**
as the architectural core of Phase 3.

---

## Purpose

The purpose of Phase 3.1 is to establish CNCF as an
**execution-shape–agnostic orchestration foundation**.

CNCF must be able to execute Components regardless of:

- packaging form
- dependency size
- runtime environment
- deployment topology

without leaking those differences into the execution model.

---

## Position in Phase 3

Phase 3.1 is the **foundation phase** of Phase 3.

- All subsequent Phase 3 work depends on this phase
- No AI Agent integration is assumed here
- No model-driven generation is assumed here

Phase 3.1 answers the question:

> Can CNCF execute *realistic components*
> without caring how they are built or packaged?

---

## Baseline Execution Form

Phase 3.1 defines **Fat JAR Component** as the baseline execution form.

This choice is intentional:

- Fat JAR represents worst-case dependency aggregation
- Scala 2 / legacy ecosystems are explicitly included
- ClassLoader isolation becomes unavoidable

If CNCF can handle this case correctly,
other execution forms become strictly easier.

---

## ActionCall as the Unified Execution Unit

All executions in Phase 3.1 are represented as **ActionCall**.

ActionCall represents a single execution request and contains only
the information required for execution control, such as identity,
policy, context, and an opaque payload.

The ActionEngine applies execution semantics uniformly to all ActionCall instances,
without interpreting their payload.

---

## Component–ActionCall Relationship

Phase 3.1 adopts a strict separation between **state ownership** and
**executable behavior**.

### Component (State Owner)

A CNCF Component is the owner of state.

- Components construct and hold internal state during initialization
- Components do not execute behavior by themselves
- Components provide state access to ActionCall execution
- Authority to mutate state is delegated, not owned

A Component represents a stable execution context and state space.

---

### ActionCall (Executable Behavior)

ActionCall is the only executable entity in CNCF.

- ActionCall is executed by the ActionEngine
- ActionCall may read Component state
- ActionCall is delegated authority to update Component state
- ActionCall does not own persistent state

All runtime behavior is expressed through ActionCall execution.

---

### Ownership and Delegation Rule

- **Component owns state**
- **ActionCall performs behavior**
- **State mutation occurs through delegated authority**

This rule is fundamental to the Phase 3 execution model
and applies uniformly across all execution forms.

---

## ActionEngine and Execution Infrastructure Responsibilities

### ActionEngine (Primary Execution Controller)

In Phase 3.1, **ActionEngine is the primary execution controller** in CNCF.

All executions pass through the ActionEngine, including:

- Operation invocations
- Event-triggered executions
- Message-triggered executions

The ActionEngine applies execution control **uniformly to all ActionCall instances**:

- serialize / concurrent / queue
- wait / cancel / timeout

The ActionEngine does not interpret execution targets or payloads.
It is solely responsible for execution control semantics.

---

### IsolatedClassLoaderProvider (Execution Infrastructure)

The IsolatedClassLoaderProvider provides low-level execution
infrastructure services and is used internally by factories
that construct collaborators.

Its responsibilities are strictly limited to:

- Providing isolated ClassLoader instances
- Establishing JVM-level isolation boundaries
- Acting as a technical failure containment boundary

It does **not** perform:

- Execution control
- Orchestration
- Lifecycle coordination of execution logic
- Interpretation of ActionCall payloads

Execution control always remains the responsibility
of the ActionEngine.

---

## Failure Semantics

Execution failure must be:

- captured as Observation
- returned through normal execution paths
- never allowed to terminate the CNCF runtime

Failure is treated as **data**, not as control flow.

---

## Collaborators

CNCF distinguishes execution control from cooperative execution targets.

A **Collaborator** represents an entity that participates in execution
but is not itself an execution unit controlled by CNCF.

**ExternalCollaborator** extends Collaborator and represents collaborators
that exist outside CNCF management boundaries.

When execution targets a collaborator, the collaborator is bound to the ActionCall.
Such ActionCalls are referred to as **CollaboratorActionCall**.

CollaboratorActionCall does not introduce special execution semantics.
It is treated identically to any other ActionCall by the ActionEngine.

In Phase 3.1, only **JarCollaborator** is within execution scope.
Other collaborator types are recognized conceptually but excluded from implementation.


---

## Collaborator as an SPI Extension Point

In Phase 3.1, **Collaborator** also serves as a standardized
**SPI (Service Provider Interface) extension point** in CNCF.

While CNCF Components and Services may expose richer internal APIs,
Collaborator provides a minimal, stable execution boundary that allows
external or pluggable functionality to be integrated uniformly
into CNCF’s execution model.

---

### Purpose of Collaborator as SPI

The Collaborator SPI exists to:

- Provide a **uniform extension point** for external or pluggable logic
- Allow execution control to remain centralized in ActionEngine
- Enable integration of implementations that CNCF does not directly manage
- Support heterogeneous implementation styles (Java, Scala, tools, adapters)

Collaborator is not intended to replace internal APIs.
It is intended to **bridge execution boundaries**.

---

### Relationship to Internal APIs

Components and Services may define and use internal APIs freely, such as:

- Scala traits
- Internal service interfaces
- Domain-specific abstractions

These internal APIs are **not constrained** by the Collaborator model.

However, when such functionality needs to be:

- plugged in from outside CNCF,
- executed under CNCF’s unified execution semantics, or
- exposed to non-Scala or non-CNCF environments,

it can be **adapted into a Collaborator**.

---

### Adapter-Based Representation

Internal or external functionality can be represented as a Collaborator
via an adapter.

Examples include:

- Wrapping an internal Scala Service as a Collaborator
- Adapting a Java program or library into a Collaborator
- Providing a façade over an external tool or runtime

In all cases:

- The adapter translates between the native API and the Collaborator API
- The execution entry point is always a Collaborator
- ActionEngine and ActionCall remain unaware of the underlying implementation

---

### Scope and Non-Goals

Collaborator as an SPI:

- **does not require** all extensions to use Collaborator
- **does not replace** internal service APIs
- **does not impose** language or implementation constraints

It defines a **standardized execution boundary**, not a universal abstraction.

---

### Summary

Collaborator is a stable SPI extension point that allows diverse
implementation styles to be integrated into CNCF while preserving
a single execution model.

Any functionality that needs to be externally pluggable or
executed under CNCF’s execution control can be represented
as a Collaborator through appropriate adapters.

---

## CollaboratorFactory

CollaboratorFactory is responsible for resolving and creating
Collaborator instances required by Components.

### Responsibility Boundary

CollaboratorFactory encapsulates all steps necessary to obtain
a usable Collaborator instance.

Its responsibilities include:

- Resolving collaborator artifacts (e.g., Fat JARs)
- Preparing isolated execution infrastructure as needed
- Creating Facade objects that implement Collaborator
- Applying collaborator-specific initialization parameters
- Hiding resolution and instantiation mechanics from Components

### Internal Dependencies

CollaboratorFactory may internally depend on:

- Artifact repositories (e.g., FatJarRepository)
- Execution infrastructure providers (e.g., IsolatedClassLoaderProvider)
- Reflection or language-specific instantiation mechanisms

These dependencies are **not visible** to Components or ActionCall.

### Component Interaction

Components do not resolve artifacts or construct Collaborators directly.

During initialization:

- A Component declares *what* collaborator it requires
- CollaboratorFactory determines *how* that collaborator is obtained

The resulting Collaborator instance becomes part of the Component’s internal state.

### Execution Semantics

CollaboratorFactory does not participate in execution control.

- It is not involved in ActionCall execution
- It does not apply execution policies
- It does not interpret ActionCall payloads

Execution semantics remain exclusively the responsibility
of ActionEngine and ActionCall.

---

## Facade and Adapter Resolution Rules

Phase 3.1 defines explicit and deterministic rules for resolving
Facade and Adapter responsibilities when creating a Collaborator
from a Fat JAR.

These rules are considered **fixed** for Phase 3.1 and must not be
changed without revisiting the core execution model.

---

### Descriptor Declarations

A Jar-based collaborator may declare the following entries:

```yaml
collaborator:
  type: jar

  facade:
    class: org.example.component.CozyFacade

  adapter:
    class: org.example.component.CozyCollaboratorAdapter
```

- `facade.class`  
  Specifies a Facade class provided by the Fat JAR itself.

- `adapter.class`  
  Specifies a CollaboratorAdapter provided by the Component.
  Programmatically, this may refer to an object instance.

---

### Resolution Priority

Resolution follows a strict priority order:

1. **Facade declaration takes precedence**
2. Adapter declaration is used only when no Facade is provided
3. If neither is declared, collaborator resolution fails with Observation

This priority is mandatory for Phase 3.1.

---

### Facade Case (Fat JAR Provides Facade)

When `facade.class` is declared:

- CollaboratorFactory loads and instantiates the Facade class
  from the Fat JAR
- The Facade object is always wrapped with
  `FacadeCollaboratorAdapter`
- The wrapped instance is returned as a Collaborator

```
Facade (Fat JAR)
  → FacadeCollaboratorAdapter
      → Collaborator
```

The use of `FacadeCollaboratorAdapter` is fixed and
non-configurable.

---

### Adapter Case (Component Provides Adapter)

When no Facade is declared and `adapter.class` is specified:

- CollaboratorFactory loads the raw Jar collaborator implementation
- The declared CollaboratorAdapter is instantiated
- The Adapter binds the raw collaborator and constructs
  the final Collaborator
- The resulting Collaborator is returned

```
Raw Jar Collaborator
  → Component-provided CollaboratorAdapter
      → Collaborator
```

The meaning and semantics of the Adapter are entirely defined
by the Component.

---

### Responsibility Boundaries

- **CollaboratorFactory**
  - Applies Facade / Adapter resolution rules
  - Instantiates Facade and/or Adapter
  - Returns a fully constructed Collaborator

- **Component**
  - Declares Adapter usage when required
  - Does not participate in resolution logic

- **ActionCall / ActionEngine**
  - Remain unaware of Facade or Adapter distinctions

These boundaries are considered **fixed** for Phase 3.1.

---

## Out of Scope (Explicit)

The following are intentionally excluded from Phase 3.1:

- Docker Component execution
- Antora integration
- CML → Component generation
- AI Agent Hub integration
- Performance optimization

These are addressed in later Phase 3.x work.

---

## Relationship to Checklist

Detailed task tracking and execution status are managed in:

- `phase-3.1-checklist.md`

This document must remain stable once Phase 3.1 is underway.
Execution details, experiments, and iteration notes belong elsewhere.

## Historical Reconstruction and Evidence

A fact-based reconstruction of Phase 3.1 discussions and decisions,
derived strictly from existing documents (without memory-based inference),
is recorded in the project journal.

See:
- docs/journal/2026/01/phase-3.1-fact-reconstruction.md

This phase document intentionally records **current intent and boundaries**.
Historical investigation, document archaeology, and evidence trails
belong in the journal and are referenced from here when needed.
