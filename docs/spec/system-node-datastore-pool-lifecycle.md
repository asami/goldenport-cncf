# SystemNode Datastore Pool Lifecycle Specification

Status: normative static specification

## Requirements

### Ownership (SNDP-R1)

A managed pool MUST be owned by exactly one SystemNode and keyed by `(SystemNode
runtime identity, canonical datastore identity)`. A Subsystem MUST NOT close a
node-owned pool. Equal identities MAY share within one SystemNode and MUST NOT
share across SystemNodes. Caller-owned and injected resources MUST NOT be
silently adopted or closed.

### Identity and Admission (SNDP-R2)

Canonical identity MUST contain normalized provider/target, principal,
credential reference/version or keyed non-reversible fingerprint, effective
connection/pool/transaction digest, and ownership mode, and MUST NOT render
raw credentials. SQLite filesystem/named shared-memory, MySQL/MariaDB, and
PostgreSQL are admitted identity grammars. A `Running` node admits a lease;
`Stopping` and `Stopped` reject new leases structurally.

### Shutdown (SNDP-R3)

Shutdown MUST transition to `Stopping`, bounded-drain admitted work, revoke
remaining leases on timeout, reject later borrows, attempt all node-owned
closes in ascending canonical-key order, aggregate failures, reach `Stopped`,
and be exactly-once. The timeout key is
`textus.system-node.shutdown.drain-timeout-millis`: missing means 30000 ms;
only positive integers 1..300000 are accepted; invalid values fail structured
SystemNode construction; typed Node override precedes Node-scoped resolved
configuration then default; aliases and ambient overrides are forbidden.

### Runtime Boundary (SNDP-R4)

`Subsystem.shutdownC` remains compatible but releases only Subsystem-local
work, bindings, and leases. A runtime-owned adapter MUST then shut down the
SystemNode. Bare embedding remains caller-managed.
