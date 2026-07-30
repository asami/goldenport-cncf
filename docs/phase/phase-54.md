# Phase 54 - Subsystem Datastore Pool Ownership and Shutdown Closure

status=planned
planned_at=2026-07-29
depends_on=[Phase 53](phase-53.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 54 Checklist](phase-54-checklist.md)

## Purpose

Make the Subsystem runtime the deterministic owner of managed SQL datastore
resources.

Phase 54 replaces repeated action/helper-time Hikari pool creation with one
managed pool for each effective datastore identity in one Subsystem. It keeps
JDBC connections operation-scoped, makes pool ownership Subsystem-scoped, and
closes every Subsystem-owned pool exactly once during shutdown.

## Dependency

Phase 54 begins after Phase 53 closes.

Phase 54 does not change the exact Entity identity contract established by
Phase 52. Datastore identity selects and owns a runtime resource; it does not
infer, rewrite, or reinterpret Entity or Entity collection identity.

## Incident Evidence

The planning trigger was a long-running Control Center process that held:

- 2,524 Hikari housekeeper threads; and
- 10,095 file descriptors to the same `registry.sqlite`.

The configured pool maximum was four, and `2,524 * 4 - 1 = 10,095`. Source
inspection found repeated `HikariDataSource` creation during component
datastore resolution, while ordinary JDBC `Connection.close()` returned
connections to their pools without closing the pools themselves.

This evidence establishes a pool lifecycle leak. It does not by itself prove
that the observed HTTP connection resets were caused by the leak. Phase 54
must produce deterministic executable resource evidence rather than relying
on this process snapshot as a test fixture.

## Selected Direction

- The Subsystem runtime owns managed datastore pools.
- The cardinality contract is:
  `(subsystem runtime identity, canonical datastore identity) -> one managed pool`.
- Same-key resolution in one running Subsystem returns the same managed
  datastore and pool.
- Different datastore identities in one Subsystem use distinct pools.
- Different Subsystems never share a pool merely because their effective
  datastore definitions are equal.
- ActionCall, Entity helpers, and UnitOfWork borrow datastore access; they do
  not own or create the pool.
- JDBC connections remain operation/transaction scoped and return to the
  Subsystem-owned pool.
- One Subsystem-wide execution lease is the admission and shutdown
  linearization boundary for synchronous Action/HTTP work, Jobs, nested calls,
  and managed datastore borrows.
- Nested calls inherit the caller's lease instead of creating an unrelated
  admission.
- Same-key concurrent acquisition is linearizable and single-flight.
- Subsystem shutdown stops new acquisition, drains admitted work under a
  bounded policy, closes every owned pool exactly once, and aggregates cleanup
  failures.
- Caller-injected or otherwise external datastores remain caller-owned unless
  ownership is explicitly transferred.
- Partially created or unpublished resources are closed immediately when
  creation fails.

## Canonical Datastore Identity

The registry key is a typed, secret-safe identity derived from the fully
resolved effective datastore definition.

It includes:

- provider kind and SQL dialect;
- normalized target:
  - SQLite: canonical absolute normalized database path;
  - JDBC: normalized endpoint plus database/catalog/schema identity;
- effective principal identity and a mandatory credential
  reference/version/fingerprint;
- pool- and transaction-affecting configuration digest; and
- ownership mode.

Component name, logical datastore name, legacy aliases, and selection source
remain provenance. They do not create duplicate pools when their effective
canonical datastore identity is equal.

Passwords, tokens, and raw credentials never appear in the identity's display,
logs, metrics, CallTree, or diagnostics. Credential material that affects
connection establishment always contributes a bounded non-reversible keyed
fingerprint when no stable secret reference/version exists. Credential
rotation therefore cannot collide with or silently reuse a pool created from
the old credential.

## Lifecycle Contract

### Acquisition

- Registry state is `Running`, `Stopping`, or `Stopped`.
- Only `Running` grants a new Subsystem execution lease.
- Every managed datastore resolution or borrow carries that lease. Direct
  caller-owned construction is outside the registry and its owner must close
  it.
- An execution admitted before `Stopping` may resolve an existing datastore or
  complete a same-key creation while its lease remains valid, including lazy
  resolution after the state transition.
- No request without a pre-`Stopping` lease can resolve, create, borrow, or
  publish a managed datastore after `Stopping`.
- One same-key creation is published to all concurrent waiters.
- A creation failure publishes no registry entry, closes any partial resource,
  returns a structured failure to current waiters, and permits a later retry.
- A conflicting effective definition fails structurally instead of replacing
  or orphaning an existing pool.
- Registry accounting includes active top-level leases, inherited nested
  usage, direct managed borrows, and in-flight creations.
- A shutdown/acquisition race is decided by the lease grant linearization
  point; a creation started by a valid lease is accounted for and drained
  before close.

### Shutdown

1. Atomically transition the Subsystem from `Running` to `Stopping`.
2. Stop granting new execution leases and quiesce the JobEngine.
3. Allow valid pre-`Stopping` leases, including their inherited nested calls
   and accounted creations/borrows, to complete under a bounded drain policy.
4. Close every Subsystem-owned pool exactly once in deterministic registry
   order.
5. Continue closing later pools after an earlier close failure.
6. Run existing managed-service, MCP, and evaluation cleanup even when
   datastore cleanup fails.
7. Aggregate structured cleanup failures without replacing the primary
   failure with a string-only result.
8. Make repeated and concurrent shutdown idempotent.
9. If the drain timeout expires, revoke the remaining leases, make subsequent
   managed borrows fail structurally, cancel work where its boundary supports
   cancellation, close owned pools best-effort, and report the timed-out work
   in the aggregated cleanup failure.
10. Transition to `Stopped`; no closed resource is reusable.

DSP-01 must freeze the configurable default drain timeout before
implementation. Tests use deterministic synchronization and injected time;
shutdown must not depend on an unbounded wait or wall-clock sleep.

## Compatibility

- Preserve existing public datastore configuration keys, precedence, policies,
  and legacy application alias behavior.
- Preserve CRUD, Entity/OCC, transaction, UnitOfWork, and nested ActionCall
  binding semantics.
- Keep `ensure_component_application_datastore` and `component_datastore`
  caller-facing behavior while routing resolution through the Subsystem
  registry.
- Preserve `ComponentDataStore.resolve` and other direct resolution without a
  Subsystem owner as explicitly caller-owned construction with an explicit
  close responsibility. Add a distinct managed resolution path that requires
  the owning Subsystem/registry; migrate runtime helpers to that path without
  silently changing direct callers' ownership.
- Do not force every `DataStore` implementation into one close contract.
  Introduce a bounded managed-resource contract for lifecycle-capable stores.
- Keep direct low-level SQL datastore construction available for
  tests/infrastructure, but make it explicitly caller-owned and closeable.
- Do not silently adopt or close injected in-memory/test datastores.
- Do not retain per-action pool creation as a compatibility path.
- Preserve the public
  `Subsystem.shutdownC(): Consequence[Vector[ServiceContainerCleanupOutcome]]`
  source and binary signature. On complete success it returns the existing
  service-container outcomes; datastore and other cleanup failures participate
  in the aggregated `Consequence.Failure`, while bounded datastore cleanup
  outcomes remain available through normal diagnostics/observation.
- Preserve `shutdown(): Unit`; it may discard the structured return only after
  the normal observation path has received the shutdown outcome.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| DSP-01 | Inventory and failing-first contract | Every SQL creation/resolution path, owner, identity input, timeout, and injected-store rule is fixed with failing executable evidence. | planned |
| DSP-02 | Managed SQL lifecycle and identity | SQL stores expose explicit owned close; canonical SQLite/JDBC identities are deterministic, secret-safe, and property-tested. | planned |
| DSP-03 | Subsystem registry and single-flight creation | One registry provides same-key reuse, distinct-key isolation, cross-Subsystem isolation, retry after failed creation, and conflict rejection. | planned |
| DSP-04 | Component and ActionCall adoption | Component datastore resolution and Entity helpers borrow the Subsystem-owned datastore without changing binding or configuration semantics. | planned |
| DSP-05 | Shutdown admission, drain, and close | Shutdown rejects new acquisition, drains admitted work, closes once, continues after failure, aggregates cleanup, and is idempotent. | planned |
| DSP-06 | Runtime and resource acceptance | Command, server, startup-failure, fixture, and embedded paths finalize ownership; bounded soak evidence proves stable resources and shutdown return to baseline. | planned |
| DSP-07 | Regression and canonical closure | Full compatibility, downstream acceptance, design/spec promotion, review, and phase closure evidence pass. | planned |

## Expected Executable Specifications

- `datastore/SubsystemDataStorePoolRuntimeSpec.scala`
- `datastore/SqlDataStoreLifecycleSpec.scala`
- `datastore/DataStoreIdentitySpec.scala`
- `datastore/DataStorePoolConcurrencySpec.scala`
- `action/ComponentDataStoreActionLifecycleSpec.scala`
- `subsystem/SubsystemDataStoreShutdownSpec.scala`
- `cli/CncfRuntimeDataStoreShutdownSpec.scala`
- `datastore/DataStorePoolResourceAcceptanceSpec.scala`

Existing `ComponentDataStoreSpec`, datastore, Entity, OCC, UnitOfWork,
Subsystem fixture, CLI, HTTP, and shutdown specifications remain regression
evidence.

## Acceptance

- Repeated same-key resolution inside one Subsystem creates exactly one pool.
- One hundred concurrent same-key resolutions publish exactly one pool.
- Different datastore identities create one pool each.
- Equal datastore identities in different Subsystems do not share pools.
- Failed creation closes partial resources, leaves no registry entry, and a
  later retry can succeed.
- Conflicting definitions fail without replacing or leaking the current pool.
- Credential rotation cannot reuse a pool created with the prior credential.
- Direct ownerless `ComponentDataStore` resolution remains caller-owned;
  managed runtime resolution requires an explicit Subsystem owner.
- Nested ActionCall binding restoration remains correct.
- Nested calls inherit one execution lease and do not inflate active admission
  accounting.
- No ActionCall or Entity helper owns a pool.
- Shutdown rejects new leases after `Stopping`, while a valid pre-`Stopping`
  lease may finish its accounted lazy resolution and work.
- Synchronous HTTP/Action work, Jobs, direct managed borrows, nested calls, and
  in-flight creation all participate in the frozen bounded drain policy.
- Drain timeout revokes remaining leases, prevents later borrow, reclaims
  owned resources best-effort, and reports structured affected-work evidence.
- Every owned pool closes exactly once, including concurrent/repeated shutdown.
- One close failure does not prevent later resources or existing runtime
  resources from being closed.
- Shutdown failures remain structured and aggregated.
- `shutdownC` retains its existing signature and successful
  service-container result contract while aggregating datastore cleanup
  failures.
- Caller-owned injected datastores are not closed.
- Repeated same-identity operations keep pool, housekeeper-thread, and SQLite
  descriptor counts bounded.
- After Subsystem shutdown, owned pool threads and file descriptors return to
  the accepted baseline.
- Command, server, startup-failure, fixture, and embedded paths all invoke the
  same ownership closure.
- Existing configuration, datastore selection, CRUD, Entity/OCC,
  transaction, UnitOfWork, and binding specifications remain green.
- No acceptance claim treats HTTP reset causality as proven without separate
  runtime evidence.

## Verification

Phase 54 implementation closure will require:

- failing-first executable specifications for every DSP-01 contract group;
- deterministic concurrency tests without timing-only assertions;
- managed SQL lifecycle and secret-safe identity property tests;
- ActionCall and nested binding regression specifications;
- command/server/startup-failure/fixture shutdown specifications;
- bounded resource soak evidence for pool, thread, and descriptor stability;
- focused datastore, Entity, OCC, UnitOfWork, Subsystem, CLI, and HTTP suites;
- the complete CNCF full suite;
- `Test/compile`;
- `git diff --check`;
- whole-file Scala naming and executable-specification compliance;
- independent review, review-fix when needed, and clean re-review; and
- final strategy/phase/checklist/design/spec reconciliation.

## Final Documentation Gate

After implementation is verified, DSP-07 must create or update:

- `docs/design/subsystem-datastore-pool-lifecycle.md`; and
- `docs/spec/subsystem-datastore-pool-lifecycle.md`.

The design records ownership, runtime integration, concurrency, and shutdown
rationale. The specification records stable identity, reuse, admission,
closure, failure, and compatibility behavior.

Phase 54 cannot close while the latest lifecycle contract exists only in this
phase plan, executable specifications, or implementation.

## Repository Boundary

The planned implementation owner is:

- `/Users/asami/src/dev2025/cloud-native-component-framework`

Control Center is the first observed driver and a future runtime acceptance
consumer. It is not a Phase 54 implementation repository. CAR-specific
failures discovered after the framework contract is corrected are fixed in
their owning CARs without reintroducing action-local pool ownership.

## Non-Goals

- Action-, request-, helper-, or UnitOfWork-level pool caching.
- One JVM-global pool shared by unrelated Subsystems.
- Changing datastore policy or configuration precedence.
- Entity identity, collection identity, schema, CRUD, OCC, or transaction
  redesign.
- Closing resources whose ownership was not transferred to the Subsystem.
- Hikari tuning, SQL performance work, or unrelated provider changes.
- Claiming that the observed HTTP resets were caused by this leak without
  independent evidence.

## Current Status

Phase 54 is planned and must not start before Phase 53 closes.
