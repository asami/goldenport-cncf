Aggregate Execution Model in CNCF
=================================

# Overview

CNCF executes domain operations primarily on **aggregates constructed
from the working set**.

Because entities are kept in memory when active,
aggregate construction becomes a lightweight operation that does not
require repeated datastore access.

This allows CNCF to support high-performance domain execution
while maintaining strong consistency boundaries.

---

# Aggregate Concept

An aggregate represents a consistency boundary
that groups a set of entities and value objects.

Typical example:

SalesOrder Aggregate

SalesOrder
 ├─ OrderLine
 ├─ Payment
 └─ Shipment

The aggregate root controls all state changes
within the aggregate boundary.

---

# Aggregate Construction

Aggregate construction occurs when a domain operation requires
a consistent set of related entities.

Flow:

Repository
→ EntitySpace
→ EntityRealm
→ WorkingSet
→ Aggregate Construction

Entities are retrieved from the working set whenever possible.

If an entity is not active,
it is activated through EntityRealm.

---

# Aggregate Execution

Once constructed, domain operations execute on the aggregate.

Example:

SalesOrder.confirm()

The execution flow:

Repository
→ Aggregate
→ Domain Logic
→ Entity Mutation

All changes are applied to in-memory entities.

No datastore round-trip is required during execution.

---

# Role of UnitOfWork

UnitOfWork tracks entity modifications during aggregate execution.

Responsibilities:

- track modified entities
- manage commit boundaries
- coordinate persistence

Flow:

Aggregate execution
→ entities modified
→ UnitOfWork records changes

During commit:

UnitOfWork
→ EntityRealm
→ PersistenceStrategy

---

# Aggregate Isolation

CNCF ensures that aggregate operations remain isolated.

Isolation is achieved through:

- entity-level concurrency control
- realm partitioning
- aggregate boundary discipline

Typical rule:

One aggregate is processed by one execution context at a time.

This avoids inconsistent intermediate states.

---

# Interaction with Partitioned Realms

Aggregate execution must respect realm partitioning.

Example:

SalesOrderId → partition 2

Flow:

Repository
→ EntitySpace
→ PartitionResolver
→ SalesOrderRealm-2
→ WorkingSet
→ Aggregate Construction

This ensures that all operations on the same aggregate
are routed to the same realm partition.

---

# Read Optimization

Reads benefit from the working set.

Typical read flow:

Query
→ Repository
→ EntitySpace
→ EntityRealm
→ WorkingSet

Because entities are already active in memory,
read latency is extremely low.

---

# Write Optimization

Writes are also optimized.

Changes occur only in memory during domain execution.

Persistence happens later during UnitOfWork commit.

Flow:

Aggregate execution
→ entity mutation
→ UnitOfWork tracking
→ persistence

This separates domain logic from persistence latency.

---

# Summary

CNCF executes domain logic using aggregates built
from the working set.

This architecture provides:

- fast aggregate construction
- high-performance domain execution
- reduced datastore dependency
- scalable concurrent processing

By combining:

Working Set
EntityRealm
Partitioning
UnitOfWork

CNCF enables efficient and consistent domain execution.
