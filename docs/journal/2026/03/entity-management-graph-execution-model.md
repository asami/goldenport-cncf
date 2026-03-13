CNCF Entity Management and Graph Execution Model
================================================

# HEAD

status=draft
published_at=2026-03-14

# Overview

CNCF manages domain entities using a **working-set–centric runtime model**.  
Entities are treated not merely as persistence records but as nodes in a
runtime **domain graph** that is actively executed by the system.

The architecture separates three concerns:

- Aggregate access and mutation
- Entity lifecycle and storage
- Query and projection

The result is a runtime that behaves more like a **domain execution engine**
than a traditional persistence-oriented application framework.

---

# Entity Access Model

CNCF supports two complementary entity access modes.

1. Working Set in Memory
2. DataStore Access with Cache

The **working set** is the primary execution model.

The datastore-backed path is used only when necessary.

## Working Set (Primary)

Entities that are actively used by the system are kept resident in memory.

Advantages:

- Very low access latency
- Efficient aggregate construction
- Precise concurrency control
- High parallel throughput

Typical working set contents:

- Master resource entities (Product, etc.)
- Active task entities (SalesOrder, etc.)

## DataStore Access (Fallback)

DataStore access is used in the following situations:

- System startup before working set initialization completes
- Access to entities outside the working set
- Explicit application requests for direct persistence access

In the current implementation phase, persistence is implemented using:

```
EntityStore → DataStore
```

Journal-based persistence may be introduced later.

---

# Entity Access Hierarchy

Entity access follows a layered architecture.

```
Repository
    ↓
EntitySpace
    ↓
EntityRealm
    ↓
EntityStore
    ↓
DataStore
```

Each layer has a clearly separated responsibility.

## Repository

Repository provides **aggregate access**.

Responsibilities:

- Load aggregate roots
- Coordinate aggregate construction
- Enforce aggregate boundaries

Repository does not directly manage persistence.

## EntitySpace

EntitySpace routes entity access to the appropriate realm.

Responsibilities:

- Resolve the entity type
- Determine the correct partition
- Provide execution context for entity lookup

## EntityRealm

EntityRealm manages entities for a specific type.

Two main implementations exist:

- Memory-resident realm
- Store-backed realm (EntityStore + cache)

An EntityRealm can be viewed as a **partition of the domain graph**.

## EntityStore

EntityStore abstracts persistence.

Responsibilities:

- Serialization and deserialization
- Communication with DataStore
- Optional cache integration

## DataStore

DataStore is the physical persistence layer.

Examples:

- relational databases
- document stores
- distributed storage systems

---

# Working Set and Memory Resident Realms

Some entities are fully resident in memory.

Two typical categories exist.

## Master Resource Entities

All valid instances are loaded into memory.

Example:

Product

Characteristics:

- relatively stable
- frequently referenced

## Task Resource Entities

Only active instances remain resident.

Example:

SalesOrder

Inactive instances may be archived or evicted.

---

# UniversalId and Resource Identity

CNCF uses a system-wide identifier called **UniversalId**.

```
major-minor-name-subname-timestamp-uuid
```

Meaning:

```
major      organization or tenant
minor      sub-organization
name       resource category
subname    resource identifier
timestamp  temporal dimension
uuid       uniqueness
```

Example:

```
tokyo-sales-entity-salesorder-20260314-550e8400
```

UniversalId serves multiple purposes simultaneously:

- global resource identity
- routing key
- partition key
- semantic classification

This eliminates the need for separate fields such as tenant_id,
resource_type, or created_at.

---

# EntityId Specialization

EntityId is a specialization of UniversalId.

```
major-minor-"entity"-${entityname}-timestamp-uuid
```

Example:

```
tokyo-sales-entity-salesorder-20260314-xxxx
```

This structure allows efficient routing:

```
EntityId
    ↓
entityname → EntityRealm
major/minor → organization partition
timestamp → temporal partition
```

---

# Partition Strategy

CNCF allows flexible partition strategies.

Possible strategies include:

- hash partition
- organization partition
- temporal partition
- hybrid partition

The **default model** is based on:

```
organization + time
```

Reasons:

- aligns with business structure
- improves query locality
- simplifies lifecycle management

Example partition structure:

```
SalesOrderRealm
    Tokyo
        2026
        2025
    Osaka
        2026
        2025
```

Hash partitioning may be used inside a partition for scaling.

---

# Resource Graph Model

The CNCF domain runtime can be viewed as a **resource graph**.

Definitions:

```
node = UniversalId
edge = relation
```

Examples:

```
SalesOrder ─references→ Product
SalesOrder ─createdBy→ User
SalesOrder ─contains→ SalesOrderLine
```

This produces a global structure:

```
System State = ResourceGraph
```

Entities, events, and resources all share the same identifier space.

---

# Aggregate as Graph Subgraph

In graph terms:

```
Aggregate = mutable subgraph
```

Example:

```
SalesOrder
    ├ SalesOrderLine
    ├ SalesOrderLine
    └ SalesOrderLine
```

External references remain outside the aggregate:

```
SalesOrder ─references→ Product
SalesOrder ─references→ User
```

Aggregate boundaries define **consistency boundaries**.

---

# Repository as Subgraph Loader

Repository loads an aggregate subgraph.

```
Repository.load(orderId)
```

internally constructs:

```
SalesOrder
SalesOrderLine*
```

Because entities are in memory, aggregate construction becomes
very inexpensive.

---

# Actions as Graph Mutations

Domain operations are executed as **graph mutations**.

Action types:

```
node creation
node update
edge creation
edge removal
```

Example:

CreateOrder

creates

```
SalesOrder node
SalesOrderLine nodes
```

This corresponds to a **subgraph mutation**.

---

# UnitOfWork

UnitOfWork manages mutation buffering.

It tracks:

```
created nodes
modified nodes
deleted nodes
```

On commit:

```
mutation
    ↓
EntityRealm
    ↓
EntityStore
```

This produces a graph update.

---

# Events and Journal

Domain events represent **mutation records**.

Example:

```
OrderCreatedEvent
    target → SalesOrder
    actor  → User
```

Journal records mutations for durability and replay.

---

# Views as Graph Projections

Views represent projections of the resource graph.

Example:

```
SalesOrderView
    = SalesOrder
    + ProductSummary
    + UserSummary
```

These are built through **graph traversal** rather than database joins.

Because entities are memory resident, view construction is fast.

View objects may be cached.

---

# Query Model

CNCF distinguishes between:

```
Aggregate Graph
    consistency boundary

View Graph
    query projection
```

This separation improves both:

- write performance
- query flexibility

---

# EntityRealm as Graph Partition

EntityRealm can be interpreted as a **graph shard**.

```
Global Resource Graph
        ↓
Graph Partitions
        ↓
EntityRealm
```

The organization + time partitioning scheme ensures:

- aggregate locality
- efficient queries
- simple archival policies

---

# Summary

CNCF implements a **domain graph execution architecture**.

Key concepts:

```
UniversalId
    global node identity

EntityRealm
    graph partition

Repository
    aggregate subgraph loader

Action
    graph mutation procedure

UnitOfWork
    mutation buffer

View
    graph projection
```

This architecture transforms the system from a
traditional CRUD application into a **domain execution engine**
capable of handling complex aggregates and queries efficiently.
