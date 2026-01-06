# Memory-First Domain Architecture
## for Cloud Native Component Framework (CNCF)

status = draft
audience = architecture / domain / platform
scope = domain-tier / subsystem-runtime

---

## 1. Background

Cloud Native Component Framework (CNCF) adopts a component–subsystem–system
architecture to enable modular, cloud-native systems.

In this context, the **Domain Tier** plays a central role:
it is the authoritative place where business state, rules, and invariants live.

Historically, domain implementations have been *database-centric*:
entities were loaded on demand, mutated through transactional updates,
and persisted as the primary source of truth.

This document describes a different approach:

> **A Memory-First Domain Architecture**,  
> where entities are manipulated in memory,
> and persistence is delegated to a backend durability layer.

---

## 2. Why In-Memory Failed in the Early 2000s

In the early 2000s, in-memory domain models were explored but rarely succeeded.

The reasons were structural, not conceptual:

- Available RAM was limited (hundreds of MBs to a few GBs).
- JVM heap sizes were small and GC was expensive.
- Entire domain datasets could not fit in memory.

As a result, systems relied on:
- sliding windows,
- partial caching,
- frequent database fallbacks.

This reintroduced the database as the *execution model*,
negating most benefits of in-memory design.

---

## 3. Why It Works Today

The environment has fundamentally changed:

- Commodity servers with **32–128GB RAM** are common.
- NVMe and modern CPUs drastically reduce recovery time.
- Domain decomposition at the *subsystem level* reduces dataset size.

Most importantly, CNCF encourages:

> **One subsystem per functional boundary**.

This makes it realistic to hold the *entire working set*
of a domain subsystem in memory.

---

## 4. Three-Tier Architecture in CNCF

CNCF adopts a classic three-tier architecture:

```
UI Tier
  ↓
Application Tier
  ↓
Domain Tier
```

Each tier has a distinct responsibility and runtime model.

### 4.1 UI Tier
- Gateways, frontends, external interfaces
- No direct access to domain state
- Stateless and request-driven

### 4.2 Application Tier
- Use case orchestration
- Implemented as **Cloud Functions (e.g., AWS Lambda)**
- Stateless, short-lived, infinitely scalable
- Absorbs traffic spikes and retries

### 4.3 Domain Tier
- Business rules and invariants
- Stateful runtime
- Authoritative state holder
- Optimized for consistency, not elasticity

This separation enables **cost-efficient scaling**:
only the Domain Tier incurs steady-state cost.

---

## 5. Domain Tier as a Memory-First Runtime

In this architecture:

- **Entities are operated in memory**
- **Persistence is a durability concern, not an execution concern**

The database is no longer the “center of the system”;
it is a backend that supports recovery and durability.

The runtime truth is the in-memory domain state.

---

## 6. Subsystem-Level Deployment

The Domain Tier is deployed using the following principle:

> **One functional capability = one subsystem = one server (by default)**

Characteristics:
- Single-node consistency
- No distributed locks or transactions
- Predictable latency
- Simple failure modes

This is not an anti-scaling stance;
it is a **scale-later, not scale-first** strategy.

---

## 7. Entity Classification for Memory Strategy

To make memory usage explicit at the *model level*,
entities are classified by their role.

### 7.1 Resource Entities

**Resource entities** represent relatively static domain data.

Examples:
- Product
- PriceList
- InventoryDefinition
- Reference data

Memory policy:
- **All instances are kept in memory**
- Loaded at subsystem startup
- Rarely mutated

Persistence:
- Snapshot-oriented
- Bulk updates

---

### 7.2 Transaction / Task Entities

**Transaction / Task entities** represent active, stateful processes.

Examples:
- Active SalesOrder
- PaymentTransaction
- ShipmentTask
- WorkflowStep

Memory policy:
- **Only active instances are kept in memory**
- Evicted immediately after completion

Persistence:
- Event-driven or state snapshots
- Archived after completion

This naturally bounds the working set.

---

## 8. Defining the Working Set

The working set is defined as:

> **Entities whose state machines can still transition**

In an EC system, this typically means:
- active orders only
- not completed or archived data

A realistic estimate shows:
- tens of thousands of active tasks
- tens of KB per entity

This easily fits within modern memory limits.

---

## 9. Event Handling with In-Memory Tasks

Keeping active tasks in memory radically simplifies event handling.

### Traditional (DB-Centric):
```
event
 → query DB
 → lock row
 → update state
```

### Memory-First:
```
event
 → lookup task by ID
 → apply state transition
 → persist asynchronously
```

Benefits:
- O(1) routing
- No I/O in the critical path
- Natural idempotency via task state
- Fewer failure modes

In this model:
> **Events are messages, and tasks are receivers.**

---

## 10. Lifecycle of a Domain Runtime

1. Startup:
   - Load all resource entities
   - Load all active transaction/task entities

2. Execution:
   - Apply commands/events in memory
   - Emit domain events

3. Completion:
   - Persist final state
   - Evict completed tasks from memory

4. Recovery:
   - Reload snapshots/events
   - Reconstruct in-memory state

---

## 11. Scalability Strategy

This architecture scales in stages:

1. **Single-node (default)**  
   Simple, consistent, cost-efficient

2. **Subsystem sharding**  
   Split by domain boundary or key range

3. **Actor-based implementation (e.g., Akka)**  
   Only when memory or concurrency limits are reached

Crucially, **the domain model does not change**.
Only the runtime implementation does.

---

## 12. Key Takeaways

- Memory-First is a *design principle*, not a micro-optimization
- Modern hardware makes full in-memory working sets feasible
- Entity classification encodes memory strategy explicitly
- Event handling becomes simpler and more reliable
- Distribution is optional and deferred

---

## 13. Summary

> **The Domain Tier in CNCF is a Memory-First, single-node-consistent runtime,
> where entities live in memory and persistence serves durability.
> This approach is well-suited for modern hardware and
> enables simple, reliable, and cost-efficient systems,
> especially for small to mid-scale domains.**
