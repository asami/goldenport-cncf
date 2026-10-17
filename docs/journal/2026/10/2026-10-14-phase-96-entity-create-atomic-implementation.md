# Phase 96 strict Entity create implementation

Document role: implementation history for P96-07B2B2B. Focused validation,
independent observation and all original Step/Phase acceptance remain pending.
This record does not establish an enclosing UnitOfWork or Display committed receipt.

The frozen [create contract](../../../spec/entity-create-atomic-provider.md) and
[settled design](../../../design/entity-create-atomic-provider.md) assign canonical
identity, authorization and managed INITIAL initialization to Entity/UoW.
Strict create now prepares ContentBody root and typed side effects purely, and
uses the additive provider port. The existing AtomicCreate enum addition and its
old feature ordering are retained.

DataStoreSpace owns real-provider, capability, same-provider and result admission.
Memory owns candidate staging and one complete collection-map publication.
The SQL create path prepares schema separately, resolves native table and text-id
collation identity, and owns one row transaction with authoritative readback and
commit acknowledgement. Known rejection and indeterminate acknowledgement have
separate outcomes; no automatic create replay or compensation is introduced.

## Intentional executable fixture roles

The existing CFS fixture retains canonical grants, registered revision bindings,
resident/View state, complete root/body snapshots and the original preservation
laws. Fault interception attaches to actual create checkpoints. Its stable SqlStore
is reused for SQLite and MySQL, including rejection before commit and an actual
connection commit followed by explicit indeterminate, thrown or null acknowledgement.

The provider fixture keeps wrapper state in actual delegates. Legacy/default-port
wrappers exercise compatibility rejection; a qualified wrapper delegates the real
port and capabilities. Plain and searchable memory specimens use real provider
maps. SQL readers own independent providers to actual task-owned databases.
Concurrent specimens use a bounded start barrier and owned executor, with separate
SQL connections/pools and no caller mutex.

Positive, duplicate, checkpoint, native alias, commit and race scenarios are
independently registered. The bounded properties retain complete maps and require
at least one hundred successful samples and zero discarded inputs. MySQL acceptance
requires a positively executing MySQL 8.4 container and fails when its required live
environment is absent.

P96-CREATE-HYG-001 (existing instance Binding), HYG-002 (upsert parameter naming)
and HYG-003 (old protected-hook naming) retain their separately recorded follow-up
ownership. Generic datastore and upsert/conditional/update/delete behavior, the
original Slice C integration, selected focused accumulator and final release gates
remain with their original owners.
