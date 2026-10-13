# Entity Observed Delete Provider Design

Status: settled design for P96-07B1.

The [provider specification](../spec/entity-observed-delete.md) owns behavior.
This design extends the existing
[Entity conflict and conditional transition design](entity-conflict-and-conditional-transition.md)
and retains the canonical Entity/UoW ownership described there.

## Boundary and authoritative result

Observed deletion is an additive operation on
`EntityVersionedMutationDataStore`, not a new persistence service. Its typed
plan separates physical removal from logical lifecycle patching, carries the
strict observed revision, and limits side effects to bounded typed deletion
targets. Logical deletion cannot carry side effects because overflow remains
owned by the surviving logical root.

`Deleted` carries the actual last matched revision obtained inside the
provider atomic boundary. The memory provider captures it before staging;
the SQL provider captures it through its selected root on the mutation
connection. Logical persistence advances that revision once, but its result
still identifies the prior matched revision. Physical persistence has no
root to reload. Result validation checks correspondence, not authority by
reconstruction: it neither invents a result from the request nor reloads
after deletion.

`Stale` is a typed provider comparison outcome with the actual current
revision. The future canonical Entity layer retains responsibility for
mapping that outcome to its existing structured conflict contract. There is
no deletion no-op and no wire receipt at this layer.

## Shared admission and dispatch

`EntityObservedDeleteSupport` owns common shape, capability and result
admission. It reuses existing structured argument utilities and the existing
unsupported-capability vocabulary. Collection/entry and guard/effect
validation occurs without provider writes. The 64-effect bound, distinct
targets, root exclusion and logical-effect prohibition keep the operation's
atomic domain explicit.

The default port method is unsupported. The two new capabilities are separate
from the unchanged old guarded baseline; callers must require both, plus
`AtomicSideEffects` when needed. Existing custom providers cannot silently
inherit implemented deletion semantics from the older mutation interface.

`DataStoreSpace` first admits shape, resolves the existing real provider,
checks its capabilities and reuses `_ensure_same_provider` for side records.
It invokes the provider once, preserves/normalizes structured failures and
admits the result. No fallback dispatcher, check-then-delete sequence,
eager side effect, shadow state or caller serialization is added.

## Memory publication

The existing `InMemoryDataStore` monitor remains the single provider boundary.
Ordinary insert/load/save/update/delete and searchable access already share
that boundary. Observed deletion adds no second map or lock.

Inside that monitor, the provider reads the existing root, applies existing
exclusion admission and parses its revision. Comparison precedes any staged
change. A matching revision must also advance successfully, so physical
deletion cannot bypass exhaustion. The root and every side collection are
copied into the existing snapshot representation.

Physical staging removes the root. Logical staging applies the existing
native record merge to the lifecycle patch and writes the next revision,
preserving unselected business and overflow fields. Existing checkpoint and
side-effect helpers operate on the snapshots. Only after `RootPrepared`,
`SideEffectsPrepared` and `BeforePublish` succeed does `_collections` receive
one replacement. A failing checkpoint cannot publish a partial root/effect
state, and ordinary provider operations cannot observe the staged state.

## SQL transaction and qualified root mutation

SQL retains `_with_versioned_mutation` for connection acquisition, native
transaction setup, result capture, commit, rollback and cleanup. Existing
owning JDBC pools and borrowed datasource semantics are unchanged.
`_select_conditional_root` retains MySQL `FOR UPDATE` and SQLite's configured
`IMMEDIATE` behavior; concurrency is controlled by the database, not by an
application lock.

Logical patches use the existing normalized-column admission and schema
preparation. Managed revision and identity collisions are rejected after
normalization as well as by common exact-name admission. Existing SQL record
reads canonicalize column names, so observed-delete exclusion admission uses
the corresponding canonical read identity. This adaptation is confined to
the new operation and does not change old native/conditional behavior.

Logical root mutation delegates to `_native_mutation_update_count`. Physical
root mutation uses `_native_delete_count` with parameterized identity,
expected revision, supported exclusion predicates and the dialect's revision
validity/range predicate. It never uses the generic unconditional root delete.
The actual affected-row count must be exactly one before any side deletion.

For zero rows, diagnosis reselects the root on the same transaction
connection. Absence/exclusion, invalid or exhausted revision and different
revision are classified from that actual record. A still-valid unchanged
matching revision yields provider failure, because the provider has not
performed the required root mutation. No count-zero success or stale result
is fabricated.

After one qualified root row, the provider runs `RootPrepared`, then
`_apply_conditional_side_effects` on the same connection, then
`SideEffectsPrepared` and `BeforePublish`. Existing commit is the publication
boundary. All data changes roll back on checkpoint/effect/commit rejection;
the result does not escape before successful commit. MySQL schema preparation
remains outside this transaction, and no DDL rollback guarantee is introduced.

## Behavioral evidence and resource ownership

The [shared fixture](../../src/test/scala/org/goldenport/cncf/datastore/EntityObservedDeleteFixture.scala)
creates actual `DataStoreSpace`/`ExecutionContext` paths over memory or JDBC
stores. SQL assertions reopen through independent datasources/stores. Fault
subclasses override only native checkpoint/commit hooks. The SQLite zero-row
scenario uses a real database trigger rather than a mocked affected-row count.

Concurrent callers use bounded latches, futures and joins only to arrange
overlapping calls; they do not serialize the provider mutation. The memory
monitor-sharing scenario observes actual blocked ordinary provider operations.
SQLite and MySQL races use independent provider instances against one database.
Resource cleanup follows existing `closeC` semantics; connections/statements
are managed by the existing provider and fixture JDBC boundary, owning pools
are closed, and the MySQL container is stopped after its suite.

The [provider](../../src/test/scala/org/goldenport/cncf/datastore/EntityObservedDeleteProviderSpec.scala),
[SQLite](../../src/test/scala/org/goldenport/cncf/datastore/SqliteEntityObservedDeleteSpec.scala),
[property](../../src/test/scala/org/goldenport/cncf/datastore/EntityObservedDeletePropertySpec.scala)
and [required live MySQL](../../src/test/scala/org/goldenport/cncf/datastore/MysqlEntityObservedDeleteAcceptanceSpec.scala)
suites are executable behavior documents. Generated laws require at least
100 successes and zero discards. Live MySQL cancellation is explicitly not
evidence; positive MySQL 8.4 execution remains required alongside existing
native/versioned/conditional/lifecycle/UoW regressions.

## Retained canonical integration responsibilities

B1 does not alter Entity lifecycle query methods, UnitOfWork operations or
interpreter, create semantics, Display bindings, registered operation routes,
Aggregate mutation, descriptor/wire format or contextual reload. It supplies
only the provider primitive needed by the later canonical path.

B2 retains typed observed Entity/UoW deletion, admitted policy and identities,
authorization, storage/overflow planning, reconciliation, View invalidation
and positive create/delete lifecycle proof. C retains ordinary registered
Entity/Aggregate routes, the complete Detail/Edit/Save/refreshed Detail path,
committed canonical outcomes and contextual reload through
`ActionEngine.afterCommitResponseC` without mutation replay. All four original
P96-07 checklist criteria remain OPEN. No Step/Phase closure follows from
provider authoring or from unsupported-only deletion behavior.
