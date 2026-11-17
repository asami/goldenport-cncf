# Startup Managed Data Import Specification

Status: normative

## Purpose and scope

Startup data seeds can populate collections later read through managed Component
record storage. A seed for a captured record collection must use the same
configured destination as its owning Component and named store, so ordinary
managed reads can observe imported values alongside existing records.

This specification covers datastore seeds during normal runtime startup. Host
storage declarations identify admitted collections; the selected Subsystem
configuration determines their destinations. Entity seeds and standalone startup
import retain their existing behavior.

## Rules

### R1 Captured collection destination

After normal Component and assembly-default composition, startup matches each
datastore seed collection against the captured
`ComponentRecordStorageDeclaration`. An exact collection match supplies the
canonical Component owner, named store and collection from that grant. Record
payload fields do not select an owner or grant additional authority.

The matched write uses the owning Subsystem's managed Component datastore
resolution, including its effective configuration and selected CAR defaults.
Existing records remain present. Import keeps its existing save behavior and
create fallback, including the existing handling of record IDs and source order.

A project may explicitly configure its fixed hidden storage directory through
CAR defaults, for example:

```yaml
config:
  textus.datastore.kind: sqlite
  textus.datastore.path: '${runtime.root}/.codex-workflow/storage/workflow.sqlite'
```

`${runtime.root}` is the selected runtime root. This example places storage
outside that project's disposable `target` directory. General Component-local
path selection remains governed by
[Component-Local Datastore Layout](component-local-datastore-layout.md).

### R2 Independent stores and existing fallback

Each granted collection resolves its own captured owner and named store. A
dedicated Component/store setting takes precedence according to the existing
Component datastore resolver. Distinct resolved destinations remain independent;
equal canonical destinations share the SystemNode-managed resource under its
normal ownership rules. Workflow and EventStore declarations continue to require
their shared destination.

Explicit global overrides retain their existing behavior when the override and
CAR defaults use the same spelling, either `textus.*` or `cncf.*`. When both
spellings occur in effective configuration, the existing resolver's key order
applies. Startup does not introduce source-sensitive precedence between an
explicit `cncf.*` value and a `textus.*` CAR default. A mixed-spelling override
therefore requires a separate configuration decision.

Relative SQLite paths keep their existing managed-resolver meaning relative to
the process working directory. Startup does not rebase them beneath the bootstrap
directory. `${runtime.root}` expansion provides an explicit project-root path
when that is the application's chosen configuration.

A collection without an exact captured record grant uses the original runtime
datastore space. Startup does not inspect or redirect unrelated Components to
find another destination. The public standalone `StartupImport.run` continues
to select every datastore seed through its supplied runtime datastore space.
Source decoding, URL/file reading and entity importing retain their existing
contracts.

### R3 Admission and lifetime

A matched startup write completes synchronously inside an active managed
SystemNode lease. The shared record-storage admission path validates the captured
declaration, reserved-collection exclusion and exact owner/store/collection grant
before checking the active lease and resolving its managed SQL resource. The
borrowed datastore view remains within that lease.

Ordinary record capability binding retains its identity checks, issuing-thread
restriction and lease lifetime. A missing declaration, foreign scope, expired
capability or stopped resource owner cannot acquire unauthorized storage. A
reserved collection in the host plan fails admission before Component
construction. These checks preserve any infrastructure resource already prepared
by a valid host plan and do not add an unauthorized resource.

### R4 Operator-owned relocation and retained evidence

Moving application state from a legacy `target` path to a fixed project hidden
directory is an operator responsibility under
[Component-Local Datastore Layout R8](component-local-datastore-layout.md#r8-operator-owned-migration).
Normal configuration can reopen a safely relocated database; runtime startup
does not copy, merge, delete or discover migration sources.

The application owner must identify the actual project, every source database,
the configured destination and all database or evidence references before
relocation. This inventory includes Sessions, histories, accepted results and
their references. Source reconciliation and absolute-reference mapping require
explicit owner decisions; a successful synthetic relocation does not qualify
those application mappings.

The operator procedure preserves state and rollback material:

1. Resolve source and destination from the application's authoritative
   configuration, including the existing meaning of relative paths. Inventory
   multiple sources before choosing or reconciling them.
2. Stop all writers and resource owners, including other processes using the
   database. Confirm stopped owners, zero active leases and closed managed
   resources before any filesystem copy.
3. Obtain a SQLite-consistent backup. Account for WAL and SHM state; checkpoint
   and close all owners or use SQLite's backup mechanism as appropriate. A raw
   main-file copy requires a fully closed source with no pending WAL state.
4. Preserve the source and backup. Copy to an absent destination without
   replacing an existing database. Verify integrity, expected records,
   ownership/permissions and evidence-reference payloads before enabling the
   new configuration.
5. Reopen using ordinary configuration, read retained records and references,
   and confirm new startup data joins the same database. Repeating a seed with
   explicit IDs must preserve its existing save semantics without duplicate
   rows.
6. Retain the original configuration, source and backup for rollback. The
   operator owns rollback coordination and reconciliation of any writes made
   after cutover; reverting a path alone cannot reconcile divergent writes.

An unchanged project-relative evidence reference can remain valid when its
payload stays at the same project location. Any changed absolute reference,
external payload location or multiple-source merge needs its own owner-approved
mapping and readback.

## Executable examples

The behavior examples are defined in
[StartupManagedDataImportSpec](../../src/test/scala/org/goldenport/cncf/cli/StartupManagedDataImportSpec.scala):

- E1 covers managed import, previous rows, reopen, dedicated and same-spelling
  global overrides, and the existing relative-path meaning.
- E2 covers independent named stores and the unmatched standalone fallback.
- E3 covers reserved/absent admission and managed capability lifetime.
- E4 covers operator copying of one fully closed synthetic SQLite database,
  ordinary reconfiguration, a stable project-relative evidence payload and
  repeated startup without duplicate records.
