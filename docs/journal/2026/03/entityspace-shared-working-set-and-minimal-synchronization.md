# EntitySpace Shared Working Set And Minimal Synchronization

## Decision

`EntitySpace` is shared within the subsystem.

It is not request-local and it is not recreated per action/job/request path.
The working set remains resident during subsystem lifetime.

This means:

- reads should benefit from a shared working set
- event/job/request paths should see the same resident entity state
- synchronization rules must be explicit

## Current Interpretation

`EntitySpace` should be treated as:

- a resident working set
- a read-through cache over `EntityStore` / `DataStore`
- not the sole source of truth

For now, the source of truth remains the store side:

- `EntityStore`
- `DataStore`

The shared `EntitySpace` is the in-memory execution surface.

## Why This Matters

The server/client CRUD memory issue in `02.d-crud-server-memory-lab` exposed the risk:

- write path and read path were expected to share one resident working set
- but visibility could diverge depending on runtime path
- therefore the synchronization contract between `EntitySpace` and store-side persistence must be made explicit

The answer is not to make `EntitySpace` request-local.
The answer is to keep `EntitySpace` shared and define minimal synchronization rules.

## Current 대응

The current short-term direction is:

- keep `EntitySpace` shared
- keep `EntityStore` / `DataStore` as store-side truth
- make `EntitySpace` a synchronized working set cache
- use store fallback when the shared working set cannot yet answer a request

In practical terms, current fixes are moving toward:

- request/client parameters reaching the server correctly
- write/read paths using the same subsystem-scoped runtime resources
- `EntitySpace` not masking store-side state permanently when its working set is empty

This is an incremental correction, not a redesign.

## Minimal Synchronization Rules

### 1. Shared Lifetime

`EntitySpace` is subsystem-shared.

- one subsystem
- one resident working set
- shared across server requests
- shared across job execution
- shared across event-triggered action execution

### 2. Store First On Write

For create/save/update/delete:

1. update the store side first
2. only after successful store-side completion, update or evict the working set

This avoids making the working set the source of truth.

### 3. Read Through

For load/search:

1. read from the shared working set first
2. if not found or not yet populated, fall back to `EntityStore` / `DataStore`
3. if loaded from store side, populate the working set with the resolved entity

This preserves the resident working set while allowing recovery from cache misses.

### 4. Cache Update Is Local And Synchronized

Working-set mutation must be synchronized at collection/realm level.

The immediate synchronization targets are:

- `EntityRealm.put/remove`
- `PartitionedMemoryRealm.put/remove/get`
- `EntityCollection.put/evict/resolve/search`

The requirement is modest:

- no torn updates in the shared map
- no partial remove/put state visible to concurrent readers

### 5. Delete Means Evict

Delete/delete-hard must evict the shared working set entry after store-side success.

This applies to:

- memory realm
- store realm cache layer if present

### 6. Working Set Is Cache, Not Commit Log

The working set must not become the authoritative transaction log.

Do not rely on:

- cache-only writes
- cache-before-store ordering
- request-local working set snapshots as the primary consistency mechanism

## Concurrency Scope

The immediate target is in-process subsystem concurrency.

This memo does **not** introduce:

- distributed locking
- cross-process coherence
- optimistic locking/version fields as a mandatory first step
- global transaction coordination beyond current unit-of-work boundaries

Those may become necessary later, but they are not the current minimum.

## What Is Not Decided Yet

The following remain future topics:

- optimistic locking on entity update
- version-based conflict detection
- invalidation events between components/processes
- striped locking vs collection-level locking
- transaction-aware delayed cache apply

## Immediate Engineering Rule

When a bug appears between:

- command/job write path
- server/client read path
- event-triggered follow-up path

assume first that the issue is in:

- shared runtime resource wiring
- store/working-set synchronization order
- missing read-through fallback

Do **not** solve it by making `EntitySpace` request-local.

