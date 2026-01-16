# Configuration Model and Platform Compilation Architecture
## for Cloud Native Component Framework (CNCF)

status = draft
audience = architecture / platform / tooling
scope = system-dsl / config-dsl / model / platform-compilation

---

## 1. Motivation

In CNCF, systems are composed from Components and Subsystems,
and deployed onto cloud platforms such as AWS ECS, Lambda, or Kubernetes.

A naive approach would treat configuration files
as simple key–value property stores, directly accessed by runtime code.

CNCF explicitly rejects this approach.

> **Configuration is not “data to be read”  
> but “a model to be constructed.”**

This document defines how CNCF:
- separates logical structure from execution details,
- normalizes configuration into a semantic model,
- and compiles that model into platform-specific DSLs (e.g. CDK).

---

## 5. Configuration Propagation Model

This section consolidates the **propagation story** for CNCF’s runtime layers.
It defines how configuration is resolved, structured, and accessed within
CNCF’s runtime layers.

### Purpose

This document defines how configuration is **resolved, structured, and propagated**
within the CNCF runtime during Phase 2.x.

The goal is to:

- Keep configuration semantics **explicit and layered**
- Avoid premature abstraction (e.g. `ApplicationConfig`)
- Align configuration ownership with **deployment and execution boundaries**

---

### Core Principles

#### 1. Deployment Unit First

- The **primary deployment and execution unit is `Subsystem`**
- Configuration resolution, lifecycle, observability, and scaling are all scoped to a Subsystem

> Therefore, configuration modeling starts from **Subsystem**, not Application.

---

#### 2. No ApplicationConfig (for now)

`ApplicationConfig` is **intentionally not defined** in Phase 2.x.

Reasons:

- “Application” is an abstract composition concept
- No concrete lifecycle or deployment boundary exists for Application
- Introducing it early blurs ownership and responsibility

Application may later be defined as a **composition of Subsystems**,
but it is **not a configuration root**.

---

### Configuration Layers

Configuration is structured as **owner-centric nested models**.

```
System.Config
   ↓
Subsystem.Config
   ↓
Component.Config
```

Each layer:

- Lives next to its owning runtime object
- Reads from `ResolvedConfiguration`
- Does **not** mutate or re-resolve configuration

---

#### System.Config

**Scope**: Entire runtime / process  
**Owner**: Runtime (e.g. `CncfRuntime`)

Responsibilities:

- Execution environment assumptions
- Observability defaults
- Global runtime behavior

Examples:

- environment name
- observability flags
- logging / tracing modes
- clock / timezone / locale assumptions

Characteristics:

- Created once per runtime
- Independent of Subsystem count
- Passed *implicitly* to lower layers (via execution context, not as raw config)

---

#### Subsystem.Config

**Scope**: Deployment unit  
**Owner**: `Subsystem`

Responsibilities:

- How the subsystem is run
- How it communicates
- What role it plays

Examples:

- HTTP driver
- Run mode
- Datastore / event backend selection
- Subsystem-level capabilities

Characteristics:

- Built from `ResolvedConfiguration`
- Applicative style (error accumulation)
- Lives as `Subsystem.Config`
- Subsystem is the **root of semantic configuration**

---

#### Component.Config

**Scope**: Behavioral unit  
**Owner**: `Component`

Responsibilities:

- Component-specific behavior overrides
- Fine-grained runtime tuning

Examples:

- Component HTTP driver override
- Component run mode
- Feature toggles

Characteristics:

- Optional values
- Defaults inherited from Subsystem behaviorally (not structurally)
- Built via `Component.Config.from(ResolvedConfiguration)`

---

### Configuration Resolution

#### ResolvedConfiguration

- Produced by `ConfigurationResolver`
- Flat key/value store with trace
- No semantics, no validation

#### Semantic Builders

Each config layer defines a **semantic builder**:

```scala
Subsystem.Config.from(conf: ResolvedConfiguration): Consequence[Subsystem.Config]
Component.Config.from(conf: ResolvedConfiguration): Consequence[Component.Config]
```

Properties:

- **Applicative style** (`mapN`)
- Explicit defaults
- Explicit required keys
- No side effects
- No cascading construction

---

### Temporal Values and Context

Some configuration values (e.g. time) require execution assumptions.

Rules:

- Temporal parsing uses `TemporalValueReader[T]`
- Requires `ExecutionContext` injection
- Accessed via:

```scala
ResolvedConfiguration.getTemporal[T](key)
```

This ensures:

- Timezone / clock / locale correctness
- Deterministic behavior in tests
- No hidden global state

---

### Tier Model (Non-Configuration)

The following are **classification concepts**, not configuration scopes:

- PresentationTier
- ApplicationTier
- DomainTier

They:

- Do **not** own configuration
- Do **not** participate in resolution
- Are implemented as **Component compositions**

Configuration flows *through* them, not *from* them.

---

### Explicit Non-Goals (Phase 2.x)

- No schema validation framework
- No DSL for configuration
- No dynamic config mutation
- No ApplicationConfig
- No cross-layer implicit propagation

---

### Summary

- Configuration ownership follows **execution ownership**
- Subsystem is the semantic root
- Configuration remains explicit, layered, and composable
- The model is intentionally conservative and evolvable

This design is frozen for Phase 2.x and forms the basis for further refinement.


## 2. Design Principles

### 2.1 Configuration Is an Input Language

Typesafe Config (HOCON) is treated as:
- an **input DSL**, not
- a runtime property bag.

No production code should:
- query configuration paths directly,
- branch on raw strings from config,
- or embed platform logic in config lookups.

---

### 2.2 One-Way Flow

The architecture enforces a strict one-way flow:

```
System DSL
   ↓
Configuration DSL (HOCON)
   ↓
Configuration Model
   ↓
Platform DSL (CDK / Terraform / etc.)
```

There is no reverse dependency.

---

### 2.3 Core Config vs Configuration

CNCF distinguishes between two fundamentally different concepts
that are often conflated under the term “config”.

#### Core Config

Core Config represents **concrete runtime settings required for the core itself**,
such as locale, timezone, logging, encoding, or randomness.

These settings:
- are tightly coupled to core runtime behavior,
- are limited in scope and schema,
- and exist to ensure correct execution of the framework.

Core Config is not intended to describe system architecture.

#### Configuration (Architectural Configuration)

Configuration, in contrast, is a **general-purpose input language**
used to describe and constrain system and subsystem architecture.

It:
- accepts structured DSLs (e.g. HOCON),
- resolves multiple sources deterministically,
- and produces a normalized structural representation.

Configuration does **not** encode semantics by itself.
All interpretation, validation, and platform decisions
are performed in later stages (Configuration Model and Compilation).

For clarity and correctness,
CNCF treats these as separate concepts with separate responsibilities.

--

### 2.4 Subsystem as the Compilation Unit

The **Subsystem** is the minimal unit for:
- configuration,
- validation,
- deployment,
- and platform compilation.

This aligns with:
- deployment boundaries,
- runtime ownership,
- and scaling decisions.

---

## 3. Logical DSL vs Configuration DSL

CNCF uses two different DSL layers with distinct responsibilities.

---

### 3.1 System / Subsystem DSL (Logical)

The System DSL describes **what exists**.

Example (conceptual):

```
system EcommerceSystem {
  subsystem OrderDomain {
    tier domain
    components {
      OrderAggregate
      PaymentPolicy
    }
    capabilities {
      datastore
      event_bus
    }
  }
}
```

Characteristics:
- Purely logical
- Platform-agnostic
- No execution details
- Human- and AI-friendly

---

### 3.2 Configuration DSL (HOCON)

The Configuration DSL describes **how requirements are satisfied**.

Example:

```
subsystems.order-domain {
  tier = domain

  capabilities.datastore {
    type = postgres
  }

  deploy {
    platform = ecs
    cpu = 1024
    memory = 4096
  }
}
```

Characteristics:
- Environment- and platform-aware
- Supports inheritance and overrides
- Suitable for dev/prod separation

---

## 4. Why “Property Reading” Is Forbidden

A common anti-pattern is:

```
config.getString("subsystems.order.capabilities.datastore.type")
```

Problems:
- Structural knowledge leaks into code
- Validation is scattered
- Platform generation becomes ad-hoc
- Refactoring is fragile

Instead, CNCF mandates:

> **All configuration must be normalized
> into a Configuration Model before use.**

---

## 5. Configuration Model (Core Concept)

The Configuration Model is the semantic “truth”
derived from DSLs and config inputs.

It represents:
- validated structure,
- resolved defaults,
- explicit intent.

---

### 5.1 Minimal Configuration Model

```
SystemModel
 └─ SubsystemModel
     ├─ tier
     ├─ kind
     ├─ components
     ├─ capabilities
     └─ deploy
```

Key properties:
- Platform-independent
- Explicitly typed
- Free of config paths and strings

---

### 5.2 Subsystem Tier and Kind

Each subsystem is defined by two orthogonal axes:

- **tier**: logical responsibility
  - ui
  - application
  - domain

- **kind**: execution form
  - service
  - job
  - gateway
  - external

These axes:
- impose structural constraints,
- define defaults,
- and guide compilation.

---

## 6. Tier-Aware Defaults and Constraints

### 6.1 UI Tier
- datastore forbidden
- kind = gateway (typical)
- deploy required

### 6.2 Application Tier
- implemented as cloud functions
- deploy.platform defaults to lambda
- datastore forbidden
- event_bus allowed

### 6.3 Domain Tier
- stateful runtime
- deploy.platform defaults to ecs/k8s
- datastore required
- in-memory entities allowed

These rules are enforced at the **model validation stage**,
not in runtime code or platform generators.

---

## 7. Capability Binding

Capabilities represent infrastructure contracts
required by a subsystem.

Examples:
- datastore
- event_bus
- clock
- external_api

In HOCON:

```
capabilities.datastore {
  type = postgres
  url = ${?ORDER_DB_URL}
}
```

In the Configuration Model:
- `datastore` becomes a typed capability
- `postgres` becomes an implementation reference
- all remaining values become structured parameters

Platform code never sees raw config paths.

---

## 8. Configuration Model Validation

Validation occurs immediately after model construction.

### Required checks:
- tier/kind compatibility
- required capabilities present
- forbidden capability usage
- deploy defaults resolved
- unknown fields rejected (by default)

This ensures:
> **Invalid architectures fail early,
> before any platform code is generated.**

---

## 9. Platform Compilation

Platform compilation consumes the Configuration Model
and produces platform-specific artifacts.

### Key rule:

> **Platform backends only depend on the model,
> never on raw config or DSL text.**

---

### 9.1 Subsystem → Platform Mapping

Examples:

- (tier=application) → Lambda stack
- (tier=domain, kind=service) → ECS service
- (tier=ui, kind=gateway) → API Gateway

This mapping is deterministic and explicit.

---

### 9.2 CDK as a Backend Example

For each SubsystemModel:

- generate one CDK Stack
- generate compute resources
- generate capability resources
- generate IAM policies
- wire environment variables

The CDK backend:
- contains no business logic
- contains no architectural decisions
- only reflects the model

---

## 10. Benefits of the Model-Centric Approach

### 10.1 Architectural Integrity
- No accidental cross-tier coupling
- No config-driven spaghetti logic

### 10.2 Tooling and Automation
- Easy diagram generation
- Easy documentation generation
- Easy cost estimation

### 10.3 AI Compatibility
- AI can reason about the model
- AI can generate valid configurations
- AI can review architecture for violations

---

## 11. Relationship to Domain Architecture

This configuration architecture complements
the **Memory-First Domain Architecture**:

- Domain Tier defines runtime truth
- Configuration Model defines deployment truth
- Platform DSL realizes infrastructure truth

Each layer has a single responsibility.

---

## 12. Summary

> **CNCF treats configuration as a language,
> not a bag of properties.**
>
> Logical DSLs define intent,
> configuration DSLs bind reality,
> configuration models define truth,
> and platform DSLs realize execution.
>
> This separation enables correctness,
> scalability, automation,
> and long-term architectural stability.
