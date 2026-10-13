# Canonical observed-delete ownership

Document role: settled PHASE-96 / P96-07B2A design responsibilities.
Status: implementation authored; parent validation and independent acceptance pending.
The [canonical contract](../spec/entity-observed-delete-canonical.md) owns
observable behavior and links all five executable suites. The
[provider design](entity-observed-delete.md) and
[provider specification](../spec/entity-observed-delete.md) remain prerequisites.

## Typed boundary and compatibility

EntityStore owns the additive typed `deleteObserved` operation and a compatible
structured unsupported default. EntityDeletionResult owns only immutable target
identity and authoritative prior matched revision. It introduces no dependency
on Display models, public wire codecs, post-delete domain serialization or
request-selected storage policy. Existing Unit delete/restore/hard-delete paths
retain their behavior.

EntityStore's pure companion admission is shared by EntityStoreSpace,
StandardEntityStore and the active UnitOfWork interpreter. It runs before
identity-derived calltree/routing/collection operations. Constructor-impossible
identity shapes and representable malformed operation input remain distinct.
The canonical EntityCollectionId determines ownership directly; opaque IDs or
Display source tokens are not parsed for ownership inference.

## Server assembly, one provider atomic boundary

StandardEntityObservedDeletePart isolates the new operation without rewriting
existing create, revision, record, query or lifecycle traits. The registered
collection revision binding owns Embedded/Detached representation and field
name. The existing soft-delete predicate and audit builder own logical versus
physical selection, server clock/principal and trace/correlation fields.

ContentBodyStoragePolicy owns the existing canonical overflow mapping and a
pure Delete-only intent for physical deletion. StandardEntityStore combines it
with the root in one plan. Logical deletion carries no side effects and retains
the existing body/business data. A preliminary raw read admits representation
and selects server policy, but never replaces atomic provider comparison.

DataStoreSpace and the selected real B1 provider own shape/capability/result
admission, same-provider root/effects, authoritative revision comparison and
native publication or rollback. Canonical code delegates once and translates
only admitted outcomes. No caller lock, shadow provider, generic fallback,
eager overflow delete or read-after-delete reconstruction exists in this design.

The prior matched revision in the receipt and logical stored next revision have
different roles: the first proves which observed root was accepted; the second
advances its persisted tombstone. A physical receipt does not require a
remaining root. Invalid provider responses are failures, not fabricated success
or evidence that independently committed effects were rolled back.

## UnitOfWork and Component-local state

UnitOfWorkOp owns the typed command and Local classification. The interpreter
owns early mandatory admission, normal canonical collection resolution, actual
raw-record authorization and Component factory policy before persistence.
Binding supplied targetId, collectionName and delete access protects the
actual mutation target while retaining all other authorization evidence.
UserPermission is exercised directly; System/ServiceInternal bypass is not a
substitute for owner/nonowner or factory evidence. Internal None does not
authorize any later ordinary public route.

After admitted Deleted acknowledgement, the interpreter evicts the actual
Component entity resident and invalidates actual Component-local ViewSpace.
Existing stale/notfound reconciliation evicts stale residents without claiming
a successful delete or invalidating Views as success. Other prepublication
failures retain appropriate caches. Provider-native atomicity does not redesign
UnitOfWork commit/abort, add two-phase commit or promise outer abort compensation
after an independently acknowledged provider commit. Uncertain returned results
and projection failures must not trigger replay.

## Executable evidence boundaries

The two admitted canonical fixtures assemble actual EntityStoreSpace,
StandardEntityStore, managed collection binding, ExecutionContext, Component,
resident realm, computed View and active UnitOfWork interpreter. Memory counters
observe delegated real provider entry points; they do not replace persistence.
Injected faults override existing real checkpoint or commit hooks only.

File-backed SQLite and live MySQL use independent JDBC stores/readers. Owning
pools close through closeC. Required MySQL 8.4 acceptance executes positively
with Testcontainers and real JDBC, without mock/SQLite/H2 substitution or
cancellation counted as success. The property laws use total bounded generators
with at least 100 successful cases and zero discards. Program recording stubs
cover typed sequencing/Local planning separately from actual persistence.

The five executable suites are
[canonical behavior](../../src/test/scala/org/goldenport/cncf/entity/EntityObservedDeleteCanonicalSpec.scala),
[canonical laws](../../src/test/scala/org/goldenport/cncf/entity/EntityObservedDeleteCanonicalPropertySpec.scala),
[SQLite](../../src/test/scala/org/goldenport/cncf/entity/SqliteCanonicalObservedDeleteSpec.scala),
[required MySQL](../../src/test/scala/org/goldenport/cncf/entity/MysqlCanonicalObservedDeleteAcceptanceSpec.scala),
and [active UnitOfWork](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkObservedDeleteSpec.scala).
Their sample records and authoring/execution history belong in the
[journal](../journal/2026/10/2026-10-06-phase-96-entity-observed-delete-canonical.md).

## Mandatory later boundaries

B2A preserves existing creation implementation and supplies only positive
optional-ID create/INITIAL-one/load/search/logical-delete coherence. P96-07B2B
must separately assess creation root admission, overflow failure and commit
safety, especially eager overflow or orphan effects. This obligation is not
waived by the positive case or provider-delete rollback evidence.

P96-07C retains registered ordinary Entity/Aggregate delegation, explicit
ordinary-route authorization, committed mutation outcome and actual selected
contextual reload through ActionEngine.afterCommitResponseC. Delayed or failed
projection must preserve committed outcome without repeating the writer. The
existing A update bridge and B1 provider readiness remain prerequisites, not
whole-Step acceptance. All original P96-07 criteria and later Step/Phase
validation, independent reviews, commits and release obligations remain open.
