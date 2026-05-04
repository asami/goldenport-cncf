# CNCF Concurrency and Runtime Hardening Note

Date: 2026-05-05

## Summary

CNCF already has basic in-process concurrency support:

- asynchronous execution is routed through `JobEngine`;
- async work is bounded by scheduler worker count;
- event reception can select sync/async and same/new job policies;
- workflow execution uses the Job/Event foundation rather than a separate
  scheduler;
- Working Set and View cache are explicit runtime memory facilities, not
  canonical data stores.

The next hardening work should separate single-process correctness from future
distributed-system work and later read-side scalability work.

## Current In-Process Baseline

`JobEngine` is the main asynchronous execution boundary. It owns queued async
execution, priority ordering, delayed retry, delayed one-shot start, job result
read models, and scheduler metrics.

`EventReception` owns continuation policy selection. The current policy axes are
sync/async, same/new job, same/new saga, same/new transaction, and failure
policy. Same-subsystem default reception can run inline in the current job and
transaction. External or compatibility async paths submit new jobs.

`WorkflowEngine` is lightweight and should continue to inherit job behavior
through `JobEngine`. It should not grow a separate scheduler.

`MemoryRealm`, `PartitionedMemoryRealm`, `EntityCollection`, `ViewCollection`,
and `ViewSpace` provide in-process residency and derived read-side caching.
Canonical state remains in EntityStore/repository-backed records.

## Single-Process Hardening Items

### Workflow Active-State Working Set Policy

Workflow active-state residency should become an explicit Working Set policy.
`entityKind = workflow` is only an active-residency candidate until the
application defines the state field, active values, and inactive/completed
values. Transition/update handling should re-evaluate residency and evict
completed or cancelled records.

### Entity and Aggregate Version Conflict Policy

Entity and Aggregate updates need a version conflict policy. Ordinary save,
update, aggregate transition, and resident Working Set writes should carry or
derive an expected concurrency token and reject stale updates deterministically.
Intentional overwrite should be isolated in explicit force/repair APIs.

### Compensation Recovery Events

Compensation failure needs a recovery-required signal. When automatic
compensation cannot remove partial Entities, associations, media links, content
side-storage records, or other derived artifacts, CNCF should emit a structured
event for human recovery rather than silently leaving ambiguous state.

### ServiceCall Fallback

`ServiceCall` fallback should be explicit policy, not implicit retry. The
runtime should record the original failure, the selected fallback, the fallback
execution mode, and the fallback result in diagnostics.

Transaction outcome event policy should separate committed domain events from
rollback/failure events and compensation/recovery events. Failure or recovery
events must not be projected as if the domain mutation committed.

## Distributed-System Deferral

Distributed execution and distributed cache coherence are not part of the
current hardening slice. They belong to a later distributed-system phase.

The planned distributed component runtime has two axes.

### Distributed Component Runtime

First, component instances may be clustered. The intended shape is a
master/slave structure: the master handles read/write, slaves handle read-only
traffic, and one slave may be promoted if the master fails. That phase owns
master election, promotion, write ownership, slave cache refresh, cross-instance
Working Set/View cache coherence, and distributed Job/Event ownership.

### Inter-Component Cluster Saga

Second, component clusters may collaborate with other components or clusters.
That phase owns long-process / Saga sharing, cross-cluster correlation,
causation and saga identity propagation, remote retry, remote compensation, and
remote failure observability.

Current in-process code should preserve abstraction boundaries so future
distributed implementations can replace or extend `JobEngine`, Working Set, View
cache, and event transport without changing ordinary application logic.

## Scalability Deferral

`ViewCollection` remains a runtime memory cache. It is intentionally not a
persistent materialized view store.

### Persistent Materialized View Store

Persistent materialized view storage should be considered after CNCF adoption
creates real read-scale pressure. That later item should own durable projection
rows, incremental sync, replay/rebuild, stale projection detection, and indexed
query optimization.

Blog/CMS public lists, slug indexes, Atom feeds, and author dashboards are
candidate drivers for that future scalability work. Until then, canonical store
records plus invalidate-on-write runtime cache is the expected model.
