Entity Runtime Architecture in CNCF
===================================

# Overview

CNCF provides a runtime architecture for domain systems based on
components, services, and working-set entity management.

The architecture integrates the following concepts:

- Component-based runtime structure
- Domain-driven design (DDD)
- Working-set entity model
- Partitioned concurrency
- Structured execution context

This architecture enables high-performance domain execution
while maintaining strong consistency boundaries.

This runtime architecture is based on the conceptual model
described in "Entity Management and Graph Execution Model".

In this model, entities are treated as nodes in a domain graph,
and domain operations are executed as graph mutations.

---

# Conceptual Execution Model

CNCF can also be understood through a domain graph perspective.

In this model:

- entities correspond to nodes in a domain resource graph
- relationships between entities correspond to graph edges
- aggregates correspond to mutable subgraphs
- domain actions correspond to graph mutations

The runtime architecture described in this document is the
execution structure that operates on this domain graph model.

In this perspective, the state of the system can be understood
as a domain resource graph composed of entities and their
relationships.

---

# Architectural Layers

The runtime architecture is composed of several layers.

Component
→ Service
→ Operation
→ Action
→ Domain Execution
→ Persistence

Each layer provides a clear responsibility.

---

# Component Layer

A Component is the primary runtime unit.

Responsibilities:

- provide services
- define domain resources
- configure entity realms
- integrate collaborators

Example:

SalesComponent

A component encapsulates its domain model
and exposes functionality through services.

---

# Service Layer

Services expose capabilities of a component.

Example:

SalesService

Services group related operations and act as
entry points for runtime invocation.

---

# Operation Layer

Operations represent executable capabilities.

Example:

createOrder
confirmOrder

Responsibilities:

- validate input parameters
- create execution context
- invoke domain actions

Operations are stateless.

---

# Action Layer

Actions implement domain logic.

Example:

ConfirmSalesOrderAction

Responsibilities:

- retrieve entities
- construct aggregates
- apply domain rules
- modify entities

Actions interact with repositories and aggregates.

---

# Domain Execution

Domain execution operates primarily on in-memory entities.

Execution flow:

Action
→ Repository
→ EntitySpace
→ EntityRealm
→ WorkingSet lookup
→ Aggregate construction

This execution flow corresponds to a traversal and mutation
of the domain resource graph.

Entities are activated when needed
and remain in the working set while active.

---

# Entity Runtime Architecture

Entity access follows the structure below.

Repository
→ EntitySpace
→ EntityRealm
→ EntityStore
→ DataStore

Each layer has a specific responsibility.

Repository

- domain-level entity access
- integration with UnitOfWork

EntitySpace

- routing entity access
- resolving entity realms

EntityRealm

- managing working sets
- activation and eviction
- concurrency control
- persistence coordination

EntityStore

- persistence abstraction
- entity serialization

DataStore

- physical storage layer

---

# Working Set Model

Entities are managed through a working set.

Active entities remain resident in memory
while participating in domain execution.

Benefits:

- fast entity access
- efficient aggregate construction
- reduced datastore dependency
- improved concurrency performance

Entities move between the datastore
and the working set through activation
and deactivation.

---

# Entity Lifecycle

Entities move through the following lifecycle.

Stored
→ Activated
→ Active
→ Modified
→ Persisted
→ Deactivated

Activation loads entities into the working set.

Persistence writes modified entities
back to storage.

---

# Partitioned Entity Realms

EntityRealm may be partitioned
to improve concurrency.

Example:

SalesOrderRealm
 ├─ Tokyo / 2026
 ├─ Tokyo / 2025
 ├─ Osaka / 2026
 └─ Osaka / 2025

Partitioning allows:

- parallel entity processing
- reduced contention
- scalable runtime performance

---

# Aggregate Execution

Domain operations execute on aggregates.

Example:

SalesOrder Aggregate

SalesOrder
 ├─ OrderLine
 ├─ Payment
 └─ Shipment

Aggregates are constructed from entities
retrieved from the working set.

From the conceptual model perspective,
an aggregate corresponds to a mutable subgraph
of the domain resource graph.

---

# UnitOfWork

UnitOfWork tracks entity modifications
during execution.

Responsibilities:

- track modified entities
- manage commit boundaries
- coordinate persistence

Commit flow:

UnitOfWork
→ EntityRealm
→ EntityStore
→ DataStore

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

The context exists only for the duration
of the operation.

---

# Collaborators

Components may integrate external capabilities
through collaborators.

Collaborators provide infrastructure services
or external integrations.

Examples:

PaymentGateway
ShippingService

Collaborators are dynamically loaded
and wired into components.

---

# Concurrency Model

Concurrency is achieved through:

- component-level isolation
- partitioned entity realms
- aggregate boundaries

Typical rule:

One aggregate is processed by
one execution context at a time.

Different aggregates can execute in parallel.

---

# Summary

The CNCF Entity Runtime Architecture combines:

- component-based architecture
- domain-driven design
- working-set entity management
- partitioned concurrency
- structured execution contexts

This architecture enables CNCF to support:

- high-performance domain execution
- scalable component systems
- flexible integration with external services
