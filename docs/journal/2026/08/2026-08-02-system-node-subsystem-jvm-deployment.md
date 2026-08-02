# SystemNode, Subsystem, JVM, and Container Deployment

Date: 2026-08-02

Status: accepted architecture decision

## Context

The runtime hierarchy needed a clear separation between logical application
structure and the current deployment shape. The intended conceptual model is:

```text
System = N SystemNode
SystemNode = N Subsystem
Subsystem = N Component
```

The current implementation and operations normally place one SystemNode in one
JVM. It was initially possible to read that placement as also making the JVM
or machine node the Subsystem and runtime-resource owner.

Docker deployment makes that interpretation inaccurate. One physical or
virtual machine node can host multiple containers and JVMs, while each
container can carry an independently deployed application runtime. Machine,
container, process, and application identities therefore cannot be treated as
one concept.

## Decision

Keep the conceptual topology independent of deployment placement:

- one System contains multiple SystemNodes;
- one SystemNode may contain multiple Subsystems;
- one Subsystem contains multiple Components;
- the SystemNode owns shared runtime infrastructure and its physical
  lifecycle; and
- the Subsystem owns its application execution boundary and logical resource
  bindings.

Use this default deployment for the current supported operation:

```text
1 SystemNode = 1 Subsystem = 1 JVM = 1 container
1 machine node = N containers/JVMs/SystemNodes
```

This one-to-one mapping is an operational constraint, not semantic identity.
The model continues to permit multiple Subsystems in one SystemNode/JVM. A
future change to that placement must not require a new Subsystem identity or a
change to SystemNode-owned shared-resource contracts.

## Decision evolution

The first working record treated a managed connection pool as Subsystem-owned
because Subsystem is the application lifecycle boundary. That interpretation
was reconsidered against the intended `SystemNode = N Subsystem` topology.

The accepted decision supersedes the working record: a connection pool is
shared execution-node infrastructure and is physically owned by the
SystemNode. A Subsystem owns its logical datastore binding and its lease on the
pool. This preserves Subsystem-level configuration and admission while allowing
equal canonical datastore identities in one SystemNode to reuse one pool.

## Resource Ownership Consequence

Managed connection pools are owned by the SystemNode. Current
one-SystemNode-per-JVM deployment may make this look equivalent to JVM
ownership, but the contract is explicitly qualified by SystemNode identity and
is not an unscoped JVM-global singleton.

For managed datastore pools, the ownership cardinality is:

```text
(system node runtime identity, canonical datastore identity) -> one managed pool
```

Equal canonical datastore identities in different Subsystems of the same
SystemNode may share a pool. Equal definitions in different SystemNodes do not
share a pool. A Subsystem shutdown releases its binding and lease but does not
directly close the node-owned pool; SystemNode shutdown drains admitted use and
closes every node-owned pool exactly once.

## Consequences

- Docker remains a deployment mechanism rather than a domain identity.
- A machine node may host multiple independent SystemNodes and Subsystems.
- JVM-global singletons must not replace explicit SystemNode identity or
  ownership.
- SystemNode-level communication, placement, and shared-resource lifecycle
  remain distinct from Subsystem execution and binding ownership.
- Subsystem shutdown does not directly close a SystemNode-owned pool. Any
  zero-binding reclamation policy belongs to the SystemNode.
- The current simple one-to-one deployment remains supported without closing
  the future multi-Subsystem hosting model.

## Scope

This decision records topology, placement, and ownership only. It does not
change current runtime behavior, introduce multi-Subsystem hosting, change
configuration precedence, or modify Phase 55 deferrals. Phase 54 owns the
implementation and executable lifecycle contract.
