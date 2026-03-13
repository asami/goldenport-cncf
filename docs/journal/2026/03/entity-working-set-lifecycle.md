Working Set Entity Lifecycle in CNCF
====================================

# Overview

CNCF adopts a working-set-centric entity model.

Entities are not always resident in memory.
Instead, entities become **active** when they enter the working set,
and may later become **inactive** when they leave it.

This document describes the lifecycle of entities
between the persistent store and the working set.

---

# Entity States

An entity in CNCF can exist in the following conceptual states.

1. Stored
2. Activated
3. Active
4. Modified
5. Persisted
6. Deactivated

These states describe the movement of entities between
the datastore and the working set.

---

# Lifecycle

## 1 Stored

The entity exists only in persistent storage.

Location:

DataStore → EntityStore

The entity is not present in memory.

Example:

- historical SalesOrder
- archived entities
- entities not yet accessed

---

## 2 Activation

Activation occurs when an entity is required by the application
or domain operation.

Activation may be triggered by:

- Repository access
- Aggregate construction
- Domain operation
- Explicit loading

Flow:

DataStore  
→ EntityStore  
→ EntityRealm  
→ Working Set

During activation:

- the entity is deserialized
- the domain object is constructed
- the entity is registered in the working set

---

## 3 Active

The entity resides in the working set.

Characteristics:

- direct memory access
- no datastore round-trip
- concurrency control performed in memory
- domain operations executed on the in-memory instance

Active entities are managed by the **EntityRealm**.

---

## 4 Modification

Domain operations modify the entity.

Example:

SalesOrder.confirm()
SalesOrder.addLineItem()

Changes are applied to the in-memory instance.

Concurrency control is performed at the entity level.

The UnitOfWork tracks modified entities.

---

## 5 Persistence

Modified entities are written back to persistent storage.

Current implementation:

Direct write to EntityStore/DataStore.

Flow:

Working Set  
→ EntityRealm  
→ EntityStore  
→ DataStore

Future option:

Journal-based persistence.

---

## 6 Deactivation

Entities may leave the working set.

Possible triggers:

- completion of a task entity
- eviction policies
- memory pressure
- explicit unload

Flow:

Working Set  
→ EntityRealm removal

The entity may remain stored in the datastore.

---

# Working Set Scope

The working set contains entities that are currently relevant
to domain execution.

Typical members:

Master Resource Entities
- Product
- Customer
- Catalog

Task Resource Entities
- active SalesOrder
- active workflow instances
- running jobs

Inactive entities remain outside the working set.

---

# Role of EntityRealm

EntityRealm manages the lifecycle of entities in the working set.

Responsibilities:

- activation
- registration in working set
- concurrency control
- persistence coordination
- deactivation

Each entity type is mapped to a specific realm.

---

# Interaction with Repository

Repository is the entry point for entity access.

Flow:

Repository  
→ EntitySpace  
→ EntityRealm

If the entity is active:

- return the in-memory instance

If not:

- activate the entity

---

# Summary

CNCF manages entities through a **working-set lifecycle**.

Entities move between:

persistent storage  
and  
the working set.

This architecture enables:

- high-performance domain execution
- efficient aggregate construction
- scalable concurrency control
- flexible persistence strategies
