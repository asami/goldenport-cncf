Entity Management Model in CNCF
===============================

# Overview

CNCF manages Entities using two complementary access models.

(a) Working Set in Memory
(b) DataStore Access with Cache

The primary model is the working set in memory.
The datastore-backed access is used as a fallback or auxiliary path.

This design enables high-performance domain execution while retaining
full persistence capabilities.

---

# Entity Access Modes

## (a) Working Set (In-Memory)

The working set keeps active Entities resident in memory.

Characteristics:

- Entities are directly accessible without datastore round trips
- Concurrency control can be performed at the entity level
- Aggregates can be constructed efficiently
- Access latency is minimized

This is the **primary access path** in CNCF.

---

## (b) DataStore Access + Cache

In this mode, Entities are retrieved from an EntityStore backed by a DataStore,
optionally using a cache layer.

This mode is used in the following situations:

- Immediately after system startup, while the working set is still being initialized
- Access to entities that are outside the working set
- When the application explicitly requires direct datastore access

---

# Performance Rationale

By making the working set the primary access model, CNCF achieves:

- Significant improvement in read performance
- Significant improvement in concurrent processing throughput
- More precise concurrency control
- Faster aggregate construction

---

# Entity Access Hierarchy

Entity access in CNCF follows the layered structure below:

Repository
→ EntitySpace
→ EntityRealm
→ EntityStore
→ DataStore

Each layer has a specific responsibility.

---

## Repository

Repository is the entry point for domain operations.

Responsibilities:

- Provide domain-level access to entities
- Coordinate with UnitOfWork
- Delegate entity resolution to EntitySpace

---

## EntitySpace

EntitySpace routes entity access to the appropriate EntityRealm.

Responsibilities:

- Locate the correct realm for an entity type
- Provide the execution context for entity resolution

---

## EntityRealm

EntityRealm defines the storage strategy for a specific entity type.

Two implementations exist:

- Memory Resident Realm
- Store-backed Realm (EntityStore + Cache)

---

## EntityStore

EntityStore provides persistence abstraction.

Responsibilities:

- Entity serialization/deserialization
- Communication with DataStore
- Cache integration

---

## DataStore

DataStore represents the physical persistence layer.

Examples:

- relational databases
- document stores
- distributed storage

---

# Memory Resident EntityRealm

Some entities are fully resident in memory as part of the working set.

Two main categories exist.

## Master Resource Entities

All valid instances are resident in memory.

Example:

Product

These entities change relatively infrequently and are commonly referenced.

## Task Resource Entities

All active instances are resident in memory.

Example:

SalesOrder

Inactive or completed entities may be evicted or archived.

---

# Update Mechanisms

Two update mechanisms are considered.

1. Direct Write to EntityStore / DataStore
2. Journal-based persistence

For the current implementation phase,
updates are performed via **direct writes to EntityStore/DataStore**.

Journal-based persistence may be introduced later
for improved durability, replayability, and event sourcing support.

---

# Summary

CNCF adopts a **working-set-centric entity model**.

The system operates primarily on in-memory entities,
while datastore access remains available as a secondary path.

This architecture balances:

- domain performance
- persistence reliability
- scalability of concurrent operations
