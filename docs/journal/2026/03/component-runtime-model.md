Component Runtime Model in CNCF
===============================

# Overview

In CNCF, application functionality is organized into components.

A component exposes services, and services provide operations.
Operations execute domain logic through actions.

This layered runtime model integrates:

- component structure
- service invocation
- domain execution
- entity management
- persistence coordination

---

# Runtime Structure

The runtime hierarchy is as follows.

Component
→ Service
→ Operation
→ Action
→ Domain Execution
→ Persistence

Each level defines a specific responsibility.

---

# Component

A Component is the primary runtime unit in CNCF.

Responsibilities:

- provide services
- encapsulate domain resources
- manage internal configuration
- integrate collaborators

A component defines:

- services
- entity realms
- repositories
- collaborators

Example:

SalesComponent

---

# Service

A Service represents a capability provided by a component.

Example:

SalesService

Responsibilities:

- expose domain capabilities
- define operations
- act as the entry point for runtime invocation

Services group related operations.

---

# Operation

An Operation represents a concrete executable action.

Example:

createOrder
confirmOrder

Responsibilities:

- validate input parameters
- construct execution context
- invoke domain actions

Operations are stateless and lightweight.

---

# Action

Action contains the actual domain logic.

Example:

ConfirmSalesOrderAction

Responsibilities:

- retrieve entities
- construct aggregates
- execute domain rules
- update entities

Actions interact with repositories and aggregates.

---

# Domain Execution

Domain execution occurs inside an execution context.

Execution flow:

Action
→ Repository
→ EntitySpace
→ EntityRealm
→ WorkingSet
→ Aggregate

All domain logic operates on in-memory entities.

---

# UnitOfWork

UnitOfWork tracks entity changes during execution.

Responsibilities:

- record modified entities
- control commit boundaries
- coordinate persistence

At the end of execution:

UnitOfWork
→ EntityRealm
→ EntityStore
→ DataStore

---

# Entity Management

Components define their entity realms.

Example:

SalesComponent
 ├─ SalesOrderRealm
 ├─ ProductRealm
 └─ CustomerRealm

Each realm manages:

- working set
- activation
- concurrency control
- persistence coordination

---

# Collaborators

Components may integrate external capabilities
through collaborators.

Collaborators provide:

- external services
- infrastructure integration
- specialized processing

Examples:

PaymentGateway
ShippingService

Collaborators are loaded and wired into components.

---

# Execution Context

Each operation runs inside an execution context.

ExecutionContext
 ├─ Component
 ├─ Service
 ├─ Operation
 ├─ Action
 ├─ Repository access
 ├─ EntityRealm access
 ├─ UnitOfWork
 └─ runtime parameters

The context exists only for the duration of the operation.

---

# Concurrency Model

Concurrency is managed at several levels.

Component level
- independent components execute in parallel

Realm level
- partitioned entity realms

Aggregate level
- single aggregate execution per context

This structure enables scalable parallel execution.

---

# Runtime Summary

CNCF runtime execution follows this structure.

Component
   │
   ▼
Service
   │
   ▼
Operation
   │
   ▼
Action
   │
   ▼
Repository
   │
   ▼
EntityRealm
   │
   ▼
Aggregate
   │
   ▼
UnitOfWork
   │
   ▼
Persistence

---

# Summary

The CNCF Component Runtime Model integrates:

- component-based architecture
- domain-driven design
- working-set entity management
- partitioned concurrency
- structured execution contexts

This architecture enables CNCF to support:

- high-performance domain execution
- scalable component systems
- flexible integration with external services
