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

### 2.3 Subsystem as the Compilation Unit

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
