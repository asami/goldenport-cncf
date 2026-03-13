Domain Execution Context in CNCF
================================

# Overview

In CNCF, domain logic is executed inside a structured execution context.

This context coordinates:

- service invocation
- entity access
- aggregate construction
- transaction management
- persistence

The execution context ensures that domain operations
are performed consistently and efficiently.

---

# Execution Flow

A typical execution flow in CNCF is:

Service
→ Operation
→ Action
→ Repository
→ EntityRealm
→ Aggregate
→ UnitOfWork
→ Persistence

Each layer has a specific responsibility.

---

# Service

Service represents the public capability of a component.

Example:

SalesService

Services define:

- operations
- input parameters
- execution entry points

Services are invoked through the CNCF runtime.

---

# Operation

Operation represents a specific executable capability.

Example:

createOrder
confirmOrder

Responsibilities:

- validate input parameters
- construct execution context
- coordinate domain actions

Operations are stateless.

---

# Action

Action performs domain logic.

Example:

ConfirmSalesOrderAction

Responsibilities:

- retrieve entities
- construct aggregates
- execute domain rules
- modify entities

Actions are the primary location of domain logic.

---

# Repository Access

Repositories provide access to entities.

Flow:

Action
→ Repository
→ EntitySpace
→ EntityRealm

If an entity is active in the working set:

- return in-memory instance

Otherwise:

- activate entity from datastore

---

# Aggregate Execution

Entities retrieved from the working set
are assembled into aggregates.

Example:

SalesOrderAggregate

Domain logic executes on the aggregate.

Example:

SalesOrder.confirm()

All changes occur on in-memory entities.

---

# UnitOfWork

UnitOfWork tracks modifications made during execution.

Responsibilities:

- track modified entities
- manage commit boundaries
- coordinate persistence

UnitOfWork ensures that
changes are committed atomically.

---

# Persistence

During commit, entities are written to persistent storage.

Flow:

UnitOfWork
→ EntityRealm
→ EntityStore
→ DataStore

Persistence is separated from domain execution.

---

# Execution Context Structure

The runtime execution context typically contains:

ExecutionContext
 ├─ Service
 ├─ Operation
 ├─ Action
 ├─ Repository access
 ├─ EntityRealm access
 ├─ UnitOfWork
 └─ Runtime parameters

This context is created when an operation begins
and disposed when execution completes.

---

# Concurrency Model

Concurrency is managed through:

- realm partitioning
- entity-level concurrency control
- aggregate boundaries

Typical rule:

One aggregate instance is processed
by one execution context at a time.

Different aggregates can execute in parallel.

---

# Error Handling

If an error occurs during execution:

- the UnitOfWork is rolled back
- the working set remains consistent
- no persistence occurs

Errors propagate back to the operation layer.

---

# Summary

CNCF executes domain logic inside a structured execution context.

The execution pipeline integrates:

Service
Operation
Action
Repository
EntityRealm
Aggregate
UnitOfWork

This model enables:

- high-performance domain execution
- consistent aggregate processing
- scalable concurrency
- clean separation between domain logic and persistence
