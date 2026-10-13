# Textus Subsystem Runtime Architecture

status=initial
authority=canonical
updated_at=2026-10-14
related=[Availability Architecture](availability-architecture.md)
security=[Textus Security Architecture](textus-security-architecture.md)

## Purpose

This document is the canonical Textus architecture for long-lived Domain
Subsystem runtime instances. A Domain Subsystem may exploit memory-resident
state and caches for performance while remaining reconstructable from admitted
CAR/configuration and authoritative persistent state.

The runtime is replaceable; its memory is not the system of record.

## Three Lifetime Tiers

Textus separates three operational lifetimes:

```text
Application Tier       short-lived / Function / ephemeral
        |
        | typed Operation / message + bounded authority
        v
Domain Subsystem       long-lived / memory-resident / replaceable
        |
        v
Persistent State       authoritative / long-lived
```

The Application Tier absorbs the lifecycle and routing details of Domain
Subsystem replacement. It should remain stateless with respect to authoritative
business state and must not bypass the Domain Subsystem to reach its DataStore.

Domain Subsystems may keep Entity/runtime indexes, registries, dictionaries and
caches resident to gain performance. Such memory must be reconstructable or
discardable.

## Active / Standby Pair

The normal high-availability profile may run two compatible Subsystem instances:

- **Active** accepts the authoritative mutation workload and normal operations.
- **Standby** remains takeover-ready and may serve explicitly safe read-only
  query/cache workloads.

Standby is not implicitly a second writer. Mutation authority is absent unless
and until the instance is admitted as Active. Read-only use must be consistent
with the application's freshness/consistency contract.

The architecture does not require exactly two replicas for all deployments, but
Active/Standby is the canonical minimal HA/replacement profile.

## Runtime Lifecycle

A runtime instance moves through explicit states such as:

```text
STARTING -> WARMING -> READY -> ACTIVE
                         |        |
                         v        v
                      STANDBY <- DRAINING
                         |
                      REPLACING
                         |
                     TERMINATED
```

Exact ABI names may evolve. The semantic distinction is normative: process
liveness is not readiness, readiness is not mutation authority, and Standby is
not Active.

Warm-up may reconstruct caches and memory-resident projections from persistent
state or safe read sources. Warm-up state cannot become a hidden authority.

## Immutable Replacement

An admitted runtime release is replaced, not patched in place:

```text
A: vN ACTIVE
B: vN+1 STARTING/WARMING
B: vN+1 READY
routing/admission switch
B: vN+1 ACTIVE
A: vN DRAINING
A: vN TERMINATED
```

The old instance completes bounded in-flight work according to the drain
contract. New work is routed to the new Active. The same mechanism handles
ordinary release, security replacement and infrastructure refresh.

Durable Job/Workflow/Continuation state must not depend on the lifetime of one
runtime process. Replacement uses durable CNCF state/continuation semantics
rather than preserving arbitrary process memory.

## Application Tier

The preferred externally exposed application tier is Function-like and
ephemeral where deployment permits:

```text
request -> authentication/input binding -> Operation invocation
        -> Domain Subsystem -> result/display projection -> response
```

Application Functions carry only the authority needed for the target Operation.
They do not own authoritative business state or directly obtain broad
PostgreSQL/DataStore authority.

This separation makes Domain Subsystem rotation invisible to normal clients and
limits the persistence value of compromising a short-lived Web/application
instance.

## Runtime Memory and Cache

Memory residency is a performance mechanism, not a durability mechanism.

- authoritative changes are persisted through the declared domain/runtime path;
- cache loss is recoverable;
- a replacement instance can rebuild required runtime state;
- cache/read-only Standby traffic is explicitly classified;
- runtime replacement does not require proving the old memory image clean.

## Security Relationship

Replaceability is a containment/recovery property. A suspected runtime can be
removed and reconstructed from admitted artifacts and authoritative state.

Active mutation authority and Standby read-only/takeover authority are explicit
Capabilities/roles, not assumptions based on hostname or network position.

Replacement does not repair stolen credentials, malicious persistent data or a
compromised upstream authority. Those remain separate security concerns.
