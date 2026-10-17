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

## Configuration-driven Runtime

Application/domain code does not own replica creation, role switching, cache
warm-up orchestration, drain or scheduled rotation. These are runtime concerns.

Subsystem configuration selects the Availability Architecture profile. CNCF
Runtime and the Textus launcher/operations layer realize the declared desired
state and expose its actual state. Application code sees the stable typed
Operation boundary rather than A/B instance identities.

This separation is essential to the Function-like Application Tier: callers do
not need deployment-specific retry/switch logic merely because the Domain
Subsystem is being rotated.

## Deployment Driver Boundary

CNCF owns logical Subsystem lifecycle and availability semantics; deployment
technology owns physical container/process/host realization. A narrow deployment
driver boundary keeps those concerns separate.

Docker Compose is the preferred initial deployment target for ordinary business
systems and development/small-to-medium operations. It can realize local-ha and,
with appropriate host-level arrangement, participate in host-ha without making
Kubernetes a prerequisite for Textus availability semantics.

Kubernetes is an optional scale-out/enterprise infrastructure driver when node
scheduling, large replica counts, autoscaling, multi-node orchestration or a
customer standard platform justifies its operational cost. Kubernetes
readiness/restart/routing mechanisms are physical primitives; CNCF remains the
owner of Textus ACTIVE/STANDBY authority, warm-up admission, drain and rotation
meaning.

Other drivers such as cloud container/serverless platforms may be added later.
Application code and the logical Subsystem lifecycle must not depend on a
specific driver.

## Release-aware Replacement

The Replacement Engine consumes an admitted desired CAR/runtime version rather
than mutating an existing instance in place. Release detection, compatibility
admission and deployment policy precede physical replacement.

After switching to a new CAR, the former Active may remain in a
fallback-ready lifecycle state for the Availability Architecture retention
window. It must remain isolated from normal mutation traffic while preserving
the ability to reacquire Active authority through an authorized switch-back.

Only after deployment commit may the old instance be discarded/rebuilt as the
new Standby according to policy.

## Deployment Driver Examples

The Deployment Driver boundary is infrastructure-neutral. The following are
non-normative deployment examples and selection guidance.

### Docker Compose

Docker Compose is a simple initial choice for development, on-premises and
small-to-medium enterprise systems. A single host can realize local-ha and
exercise the complete Textus rotation/replacement semantics. Separate-host
operation requires host-level coordination beyond one ordinary Compose project.

### AWS ECS / Fargate

For ordinary AWS-hosted enterprise systems, ECS is a natural Docker deployment
driver and Fargate can remove most direct EC2 host management. AWS/ECS owns
physical task placement, restart and host/container failure recovery; Textus
still owns Subsystem ACTIVE/STANDBY authority, warm-up/readiness admission,
read-only Standby policy, drain, CAR version and Deployment Transaction.

Running two ECS tasks is therefore not by itself Textus Active/Standby. The
runtime roles and mutation authority remain CNCF semantics.

A host-failure-tolerant AWS deployment should place the Active and Standby
runtime capacity so one underlying compute failure does not remove both. The
exact ECS/Fargate placement mechanism is driver/deployment policy rather than
application code.

Stateful dependencies have independent failure boundaries. For example, an AWS
deployment may use RDS PostgreSQL and, when the business requirement justifies
it, an AWS-managed Multi-AZ database configuration. Textus must not claim
system-wide host/site availability merely because its application containers
are redundant.

### Kubernetes / EKS

Kubernetes/EKS remains an optional driver for environments that need or already
standardize on Kubernetes scheduling, scaling and orchestration. Kubernetes
Pods, Services and probes are physical primitives; they do not replace CNCF
Subsystem role/admission semantics.

A reasonable adoption path is therefore Compose for simple/local operation,
ECS/Fargate for ordinary AWS managed-container operation, and Kubernetes/EKS
when its additional orchestration capability is actually required.
