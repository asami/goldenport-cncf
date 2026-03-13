EntityRealm Architecture in CNCF
================================

# Overview

EntityRealm is the runtime manager of entities in CNCF.

It manages the **working set**, coordinates persistence,
and controls the lifecycle of entities.

Each entity type is assigned to a specific EntityRealm.

EntityRealm is responsible for ensuring that:

- entities are activated when needed
- entities remain consistent during concurrent operations
- entities are persisted correctly
- the working set remains manageable

---

# Position in Entity Architecture

EntityRealm sits between EntitySpace and EntityStore.

Repository
→ EntitySpace
→ EntityRealm
→ EntityStore
→ DataStore

Responsibilities are divided as follows.

EntitySpace
- routes entity access

EntityRealm
- manages entity runtime lifecycle

EntityStore
- performs persistence operations

---

# Core Responsibilities

EntityRealm performs the following functions:

1. Working set management
2. Entity activation
3. Concurrency control
4. Persistence coordination
5. Entity eviction

---

# Internal Structure

EntityRealm contains several internal subsystems.

EntityRealm
 ├─ WorkingSet
 ├─ ActivationPolicy
 ├─ EvictionPolicy
 ├─ ConcurrencyControl
 └─ PersistenceStrategy

These components together manage entity lifecycle and runtime behavior.

---

# WorkingSet

WorkingSet maintains active entities in memory.

Responsibilities:

- store active entity instances
- provide fast lookup by EntityId
- maintain entity registration

Typical structure:

Map[EntityId, Entity]

Operations:

- lookup
- register
- remove
- iterate

The working set is the primary source of entities during runtime.

---

# ActivationPolicy

ActivationPolicy determines how entities enter the working set.

Possible activation triggers:

- repository access
- aggregate construction
- explicit load
- domain operation

Typical flow:

DataStore
→ EntityStore
→ EntityRealm
→ WorkingSet

ActivationPolicy may include:

- lazy loading
- eager loading
- batch loading

---

# EvictionPolicy

EvictionPolicy determines when entities leave the working set.

Possible triggers:

- completion of task entities
- inactivity
- memory pressure
- administrative cleanup

Eviction strategies may include:

- LRU
- time-based expiration
- explicit unload

---

# ConcurrencyControl

ConcurrencyControl ensures safe concurrent access to entities.

Possible strategies:

- entity-level locking
- optimistic concurrency
- version-based updates

In CNCF, concurrency is typically managed at the **entity instance level**.

This avoids coarse-grained locking at the datastore layer.

---

# PersistenceStrategy

PersistenceStrategy coordinates writing entities back to storage.

Two approaches exist:

Direct persistence

WorkingSet
→ EntityStore
→ DataStore

Journal-based persistence

WorkingSet
→ Journal
→ asynchronous persistence

The current implementation uses direct persistence.

Journal-based persistence may be introduced later.

---

# Interaction with UnitOfWork

UnitOfWork tracks modified entities during domain execution.

Flow:

Repository
→ EntityRealm
→ WorkingSet entity

During commit:

UnitOfWork
→ EntityRealm
→ PersistenceStrategy

---

# Interaction with Repository

Repository delegates entity resolution to EntityRealm.

Flow:

Repository
→ EntitySpace
→ EntityRealm

If entity exists in WorkingSet:

return existing instance

Otherwise:

activate entity

---

# EntityRealm Variants

Two main implementations exist.

MemoryResidentRealm

- entities permanently resident in memory
- used for master resource entities

StoreBackedRealm

- entities activated on demand
- backed by EntityStore

---

# Summary

EntityRealm is the core runtime manager of entities in CNCF.

It integrates:

- working set management
- activation
- concurrency control
- persistence coordination

This architecture enables CNCF to support:

- high-performance domain execution
- scalable concurrency
- flexible persistence strategies
