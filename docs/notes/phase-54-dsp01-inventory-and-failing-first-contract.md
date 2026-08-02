# Phase 54 DSP-01A — SystemNode Datastore Inventory and Failing-First Contract

status=non-normative
recorded_at=2026-08-02
phase=[Phase 54](../phase/phase-54.md)
checklist=[Phase 54 Checklist](../phase/phase-54-checklist.md)

## Purpose and Boundary

This note records the DSP-01A source inventory and executable red evidence.
It does not introduce a production registry, managed-resource close API, or
Subsystem/SystemNode lifecycle implementation. Those changes remain owned by
DSP-02 through DSP-06. The normative Phase 54 contract remains the phase and
checklist documents.

The runtime topology is fixed as follows:

```text
(SystemNode runtime identity, canonical datastore identity) -> one managed pool
```

A Subsystem owns only its logical datastore binding and lease. A SystemNode
owns physical managed pools, zero-binding reclamation, and final close. Equal
canonical identities may share one pool across resident Subsystems in a single
SystemNode; distinct SystemNodes never share a pool. The current deployment is
one SystemNode, one Subsystem, one JVM, and one container, although one machine
may host multiple containers/JVMs/SystemNodes.

## Current Creation and Resolution Inventory

| Source | Current path | Current owner classification | DSP target |
| --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/datastore/ComponentDataStore.scala` (`_local`, `_sqlite`, `_local_sql`) | local dedicated, component SQLite, and generic local branches call `SqlDataStore.sqlite` | direct ownerless construction | Direct ownerless resolution remains caller-owned; a future explicit Subsystem binding borrows a SystemNode-owned pool. |
| `src/main/scala/org/goldenport/cncf/datastore/ComponentDataStore.scala` (`_jdbc_auto`, `_jdbc`) | component and basic JDBC branches call `SqlDataStore.jdbc` | direct ownerless construction | Effective principal, credential reference/version or keyed fingerprint, and pool/transaction digest participate in identity. |
| `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` (`create`) | runtime local/SQLite branches call `SqlDataStore.sqlite` | runtime bootstrap direct construction | DSP-04 must use an explicit SystemNode owner. |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala` (`jdbc`, `sqlite`) | each factory creates `HikariDataSource` | factory has no managed close boundary | DSP-02 introduces lifecycle-capable managed-resource ownership. |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` (`ensure_component_application_datastore`) | binds `DataStoreSpace` through `bindApplicationDataStore` | currently resolves/binds directly; no SystemNode lease | DSP-04 makes the binding borrow through the owning Subsystem lease. |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` (`component_datastore`) | direct `ComponentDataStore.resolve` | caller-owned direct construction today | DSP-04 retains this direct path as explicitly ownerless and adds a distinct managed path. |
| Entity helpers and UnitOfWork | exact borrower families are recorded in the DSP-01B matrix below | borrower boundary only; no independent pool ownership | DSP-04 adopts the SystemNode lease. |
| `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` (`shutdownC`, `shutdown`) | Job, service-container, MCP, and evaluation cleanup | no datastore pool finalization exists | DSP-05 preserves the signature while SystemNode performs drain/revoke/close and aggregates failures. |
| `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` (`executeCommand`) | `finally { subsystem.shutdown() }` | command adapter performs only current Subsystem cleanup | DSP-06 invokes SystemNode finalization after adapter cleanup. |
| server and embedded runtime paths | exact current adapters are recorded in the DSP-01B matrix below | no verified SystemNode finalization path | DSP-06 converges each adapter. |
| `src/test/scala/org/goldenport/cncf/testutil/SubsystemTestFixture.scala` (`withSubsystem`) | `finally { subsystem.shutdown() }` | fixture performs only current Subsystem cleanup | DSP-06 adds deterministic SystemNode finalization to fixture ownership. |
| startup failure paths | exact post-construction windows are recorded in the DSP-01B matrix below | no verified node-owned cleanup boundary | DSP-06 closes unpublished node-owned resources. |

The source has no `SystemNode` runtime type yet. Consequently the physical
pool registry, lease linearization point, single-flight creation, and
exactly-once close are unimplemented behavior rather than a hidden ambient
Subsystem capability.

## DSP-01B Borrower and Binding Completion

The following paths use a selected datastore or binding; none is a physical
pool creator or owner.

| Source | Exact current chain | Ownership conclusion | Future stage |
| --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | Entity create, upsert, claim, load, save, update, transition, delete, search, and identity helper families call `ensure_component_application_datastore`; it calls `DataStoreSpace.bindApplicationDataStore`, then `ComponentDataStore.resolveForDataStoreSpace`. | Logical binding selection only; current direct resolution has no SystemNode lease. | DSP-04 |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | `_aggregate_raw_record` and `_entity_store_record` use the already-bound store through `EntityStoreSpace` and `DataStoreSpace.dataStore`. | Borrower only. | DSP-04 |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | `ActionCallDataStorePart` emits `UnitOfWorkOp.DataStoreLoad`, `DataStoreSave`, and `DataStoreDelete`; `ActionCallEmbeddedDataStorePart` emits the separate embedded datastore operations. | Generic operations create no pool; embedded operations are distinct from runtime embedding. | DSP-04/DSP-06 |
| `src/main/scala/org/goldenport/cncf/action/ActionCall.scala` | `FunctionalActionCall.execute` captures and restores the thread-local datastore binding around the action. | Binding preservation, neither creation nor close. | DSP-04 |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | Entity operations delegate to `EntityStoreSpace`/`EntityStore`; generic `DataStore*` operations return `dataStoreUnavailable`; embedded operations delegate to `EmbeddedDataStoreRunner`. | Entity paths borrow an existing binding; generic paths are unwired. | DSP-04 |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala`, `EntityStore.scala`, and `ContentBodyStoragePolicy.scala` | create/upsert/load/save/update/delete/restore/search/unique/identity, collection mapping, and overflow-content families obtain stores through `ctx.dataStoreSpace.dataStore` or `search`. | Every listed family borrows the existing logical binding; it never creates or owns Hikari. | DSP-04 |
| `src/main/scala/org/goldenport/cncf/embedded/EmbeddedDataStore.scala` | `DriverManager.getConnection` is followed by `finally conn.close()`. | Operation-scoped raw JDBC, outside the managed Hikari-pool target unless Phase 54 scope is explicitly expanded. | no Phase 54 change |

`ComponentLogic`, `GlobalRuntimeContext`, `ScopeContext`, `DataStoreContext`,
and `ExecutionContext` carry the existing parent `DataStoreSpace` and scope
context into an ActionCall. They are inheritance plumbing for the current
shared logical space, not a SystemNode owner.

## DSP-01B Runtime Termination Completion

| Entry point | Construction / current finalizer | Uncovered current window | Future owner |
| --- | --- | --- | --- |
| `CncfRuntime.run`, `runWithExtraComponents`, and `_execute_command_args` in `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` | `_initialize` constructs the Subsystem; command mode has no enclosing finalizer in these methods. The separate `executeCommand(subsystem, args)` adapter uses `finally { subsystem.shutdown() }`. | Direct `run` command path and failures after construction need one node-aware finalizer. | DSP-06 |
| `CncfRuntime.startServer` in `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` and `ServerOperation.execute` in `src/main/scala/org/goldenport/cncf/cli/ServerOperation.scala` | create `Http4sHttpServer` and call `start`. `Http4sHttpServer._server` uses http4s resource management for the HTTP server. | HTTP server resource closure does not invoke Subsystem/SystemNode finalization. | DSP-06 |
| `CncfRuntime.initializeForEmbedding`, `initializeHandle`, `CncfHandle.close`, and `closeEmbedding` in `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` | `initializeForEmbedding` returns a caller-managed `Subsystem`; `CncfHandle.close` resets embedding globals through `closeEmbedding`. | Handle close currently does not call `Subsystem.shutdown`; bare embedding users retain explicit caller shutdown responsibility. | DSP-06 |
| `SubsystemTestFixture.Startup`, `withSubsystem`, and `TestComponentFactory.withSubsystem` in `src/test/scala/org/goldenport/cncf/testutil/SubsystemTestFixture.scala` and `TestComponentFactory.scala` | fixture creation is followed by `finally { subsystem.shutdown() }`. | Fixture finalizer needs SystemNode closure once it exists. | DSP-06 |
| `CncfRuntime._initialize`, `DefaultSubsystemFactory`, `GenericSubsystemFactory`, and `TextusIdentitySubsystemFactory` | post-construction work includes admission, component setup, extra-component installation, descriptor verification, runtime SPI resolution, and `StartupImport.run`. | Exceptions after Subsystem construction have no SystemNode partial-resource finalizer. | DSP-06 |
| client, server-emulator, and script dispatch in `CncfRuntime._run` | dispatch receives the constructed Subsystem but has no common node finalizer. | These adjacent modes must converge with command/server handling. | DSP-06 |

DSP-06 must make `CncfHandle` own both Subsystem and SystemNode finalization
before resetting embedding globals. Whether a bare `initializeForEmbedding`
result remains a supported caller-managed lifecycle surface is a compatibility
decision deferred to that stage; DSP-01B does not alter it.

## Frozen Timeout Contract

| Field | Contract |
| --- | --- |
| Canonical key | `textus.system-node.shutdown.drain-timeout-millis` |
| Default | `30000` milliseconds |
| Accepted values | positive base-10 integer milliseconds, inclusive range `1..300000` |
| Missing value | use the default |
| Invalid value | non-numeric, zero, negative, or over-bound values fail structurally during SystemNode construction; no fallback |
| Precedence | typed per-SystemNode override, then SystemNode-scoped resolved configuration, then default |
| Aliases and ambient configuration | no aliases; no Subsystem, Action, HTTP, Job, or Component ambient override |
| Observation | record only resolved source and bounded milliseconds |

On timeout the registry remains `Stopping`, revokes remaining node resource
leases across resident Subsystems, makes later managed borrows fail
structurally, best-effort closes node-owned pools, aggregates affected work and
cleanup failures structurally, and then becomes `Stopped`. Other SystemNodes
are unaffected. The current 1:1 runtime adapter first performs Subsystem
cleanup and then SystemNode shutdown.

## Failing-First Evidence

`SystemNodeDataStorePoolRuntimeSpec` records that an over-bound canonical value
is currently accepted. `SystemNodeDataStoreShutdownSpec` records that zero,
negative, and non-numeric canonical values are currently accepted. Each
assertion uses `RuntimeConfig.create`, which already reports construction
failures as a structured `Consequence`; each requires the canonical key and
rejected value in the eventual structured failure. Each suite also actively
executes a bounded ScalaCheck property over the corresponding invalid-value
range. These are provisional parser-admission counterexamples, not evidence
of a SystemNode construction boundary: the SystemNode type and its typed
override do not exist yet.

These registrations are deterministic and allocate neither a SQL datastore nor
a Hikari pool. Together with DSP-01C they establish private-port failing-first
evidence for invalid-value admission, reuse, single-flight creation, leases,
shutdown races, and close behavior. Default observation, accepted endpoint
values, no-alias behaviour, and typed per-SystemNode precedence require the
future SystemNode configuration surface. Production-bound behavior and real
descriptor/thread evidence remain DSP-02--DSP-06 work.

HTTP reset causality is not established by this inventory or its executable
evidence.

## DSP-01C Lifecycle and Registry Failing-First Decision

The Phase 54 owner accepted the following observable contracts on 2026-08-02:

- A pool whose resident Subsystem binding count falls to zero remains owned and
  retained by its SystemNode until that SystemNode shuts down. A Subsystem
  release never directly closes it. Opportunistic zero-binding reclamation is
  deliberately deferred to a later explicit SystemNode policy.
- SystemNode shutdown closes node-owned pools in ascending secret-safe
  canonical datastore identity order. This is independent of concurrent
  creation/insertion timing.

`SystemNodeDataStorePoolRuntimeSpec` and
`SystemNodeDataStoreShutdownSpec` now register the remaining reuse,
single-flight, retry, lease, shutdown, timeout, ownership-exclusion, and
exactly-once-close contracts through private semantic ports. Their current
adapters return an attributable `notImplemented` failure, so every scenario is
a registered `pendingUntilFixed` counterexample rather than a claim of existing
production behavior. The ports allocate no JDBC, Hikari, SQLite, file, or
thread resource. DSP-06 remains responsible for real resource-baseline,
descriptor, and housekeeper-thread acceptance.
