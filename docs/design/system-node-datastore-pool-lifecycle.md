# SystemNode Datastore Pool Lifecycle

Status: normative design

## Ownership

`SystemNode` owns physical managed SQL pools. A `Subsystem` owns only a
logical datastore binding and lease. Pool cardinality is:

```text
(SystemNode runtime identity, canonical datastore identity) -> one managed pool
```

Equal identities share only within a SystemNode; different SystemNodes never
share. The current one-SystemNode/one-Subsystem/JVM/container deployment is
placement, not identity. Releasing the last binding retains the pool until
explicit SystemNode shutdown.

## Identity and Lifecycle

Canonical identity includes normalized provider/target, principal, credential
reference/version or a keyed non-reversible fingerprint, effective
connection/pool/transaction digest, and ownership mode. SQLite, MySQL/MariaDB,
and PostgreSQL are the initial identity grammars. PostgreSQL accepts ordinary
AWS RDS and Google Cloud SQL TCP/DNS endpoints without provider SDKs.

`Running` admits leases; `Stopping` rejects new ones; `Stopped` rejects all
managed borrows. Same-key creation is single-flight. Shutdown drains bounded
admitted work, revokes remaining leases on timeout, closes node-owned pools in
ascending canonical-key order, aggregates failures, and caches the terminal
result. Subsystem shutdown releases its logical state only; a runtime-owned
adapter then finalizes SystemNode. Bare embedding is caller-managed.
