# Partitioned EntityRealm Design
CNCF Entity Runtime Architecture Note

## Overview

CNCF's entity runtime adopts a **working-set architecture**, where frequently
used entities are kept in memory and accessed directly from the runtime.

Current structure:

```
EntityCollection
  └ EntitySpace
       └ EntityRealmSlot
            ├ StoreRealm
            └ MemoryRealm
```

This design performs very well for small and medium workloads.  
However, when the number of entities grows large, several issues emerge:

- Increased contention on the in-memory working set
- Concentration of updates on a single state structure
- Reduced parallelism in aggregate processing

To address these issues, CNCF introduces **Partitioned MemoryRealm**.

---

# Goals

Partitioned MemoryRealm aims to achieve:

- Scalable working-set memory management
- Localized update contention
- Parallel execution lanes for aggregates
- Support for very large entity counts

---

# Current MemoryRealm Structure

The current MemoryRealm maintains a single working set.

```
MemoryRealm
  └ state : Map[EntityId, Entity]
```

All updates and lookups operate on this single state.

This approach becomes a bottleneck when the number of entities grows.

---

# Partitioned MemoryRealm

The new structure partitions the working set.

```
PartitionedMemoryRealm
 ├ Partition A
 ├ Partition B
 └ Partition C
```

Conceptually:

```
PartitionedMemoryRealm
  └ partitions : Map[PartitionKey, MemoryRealm]
```

Each partition maintains an independent working set.

---

# Partition Strategy

Partitioning is determined by a **strategy**.

```
trait PartitionStrategy {
  def partitionKey(id: EntityId): String
}
```

Possible strategies:

```
hash
organization
time
organization + time
```

CNCF recommends the following default:

```
organization + time
```

### Reason

Enterprise web systems frequently perform:

- filtered searches
- list views
- reporting queries


Using business semantics as the partition boundary improves locality.

---

# Relation to EntityId Structure

CNCF EntityId is designed as:

```text
major-minor-"entity"-${entityname}-timestamp-uuid
```

Where:

```text
major-minor
  organization / tenant boundary

timestamp
  temporal boundary
```

Because of this structure, partition strategies such as:

```text
organization
time
organization + time
```

can be derived directly from EntityId without requiring additional routing metadata.

This alignment between identifier structure and partition strategy is one of the key advantages of the CNCF entity runtime model.

---

# PartitionedMemoryRealm Structure

```
PartitionedMemoryRealm[E]
 ├ strategy : PartitionStrategy
 └ partitions : Map[PartitionKey, MemoryRealm[E]]
```

Each partition behaves like an independent MemoryRealm.

---

# Entity Access Flow

Entity lookup process:

```
resolve(id)
   ↓
partitionKey(id)
   ↓
select partition MemoryRealm
   ↓
entity lookup
```

---

# Entity Insert Flow

```
put(entity)
   ↓
idOf(entity)
   ↓
partitionKey(id)
   ↓
select partition MemoryRealm
   ↓
insert entity
```

---

# Resolve Flow with Store Fallback

```
resolve(id)
  ↓
MemoryRealm lookup
  ↓
hit → return entity
miss → query StoreRealm
  ↓
load entity
  ↓
MemoryRealm.put(entity)
```

This preserves the **load-through caching model** used in CNCF.

---

# Interaction with AggregateLockRegistry

Aggregate updates are serialized using `AggregateLockRegistry`.

Current design:

```
lockKey = EntityId
```

Future alignment:

```
lockKey = partitionKey(id)
```

This ensures:

```
same aggregate
 → same partition
 → same execution lane
```

Which improves concurrency behavior.

---

# StoreRealm

StoreRealm remains **non-partitioned**.

```
StoreRealm
 = persistence access
 = fallback path
```

The primary performance concern is in MemoryRealm, not persistence.

---

# EntitySpace Integration

Future structure of `EntityRealmSlot`:

```
EntityRealmSlot
 ├ store  : EntityRealm
 └ memory : PartitionedMemoryRealm
```

---

# Runtime Architecture

Final entity runtime structure:

```
Component
 └ CollectionRegistry
      └ EntityCollection
           └ EntitySpace
                └ EntityRealmSlot
                     ├ StoreRealm
                     └ PartitionedMemoryRealm
                          ├ Partition A
                          ├ Partition B
                          └ Partition C
```

---

# Benefits

Partitioning provides:

```
• scalable working set
• reduced lock contention
• improved concurrency
• locality-aware query performance
```

---

# Implementation Plan

Safe incremental introduction:

```
1. Introduce PartitionStrategy
2. Implement PartitionedMemoryRealm
3. Allow coexistence with MemoryRealm
4. Replace MemoryRealm in EntityRealmSlot
```

This approach avoids breaking existing runtime behavior.

---

# Future Extensions

Possible enhancements:

```
partition eviction
dynamic partition scaling
partition metrics
partition-aware aggregate locking
```

---

# Summary

Partitioned EntityRealm extends CNCF's **working-set entity model**
to support large-scale systems.

It enables stable runtime behavior even under workloads such as:

```
1,000,000 entities
1,000 TPS
```

while preserving the simplicity of the existing entity runtime model.
