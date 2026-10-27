# Managed Event History Binding

2026-10-27. Follow the [specification](../spec/managed-event-history.md).

## Explicit managed first installation

The trusted `SubsystemEventStoreBootstrap.initializeStorageC` is separate from
its retained cursor-only initializer. Subsystem passes only its captured
EventStore declaration through the existing managed dispatch lease and verified
SQLite view to a narrow internal SqlDataStore initializer. No caller-supplied
schema, raw store or replacement owner escapes this boundary.

The initializer reuses the existing conditional transaction/lifecycle machinery:
validate all fixed existing schemas and empty collections before writes; create
only missing tables; insert and decode the genuine initial cursor on that same
connection; revalidate admission before commit. Fixed column/type witnesses use
the existing dialect renderer without constructing a sample Record or Event.
Schema checks include the primary key, SQLite affinities, nullable non-key
fields and absence of synthesized defaults, with no automatic column repair.
Existing cursor data, including a cursor-only legacy install,
orphan events/indexes and incompatible schema are rejected without migration.
Precommit rollback, commit uncertainty and competing writers use the existing
native transaction outcomes. No automatic retry is introduced.

The cursor table holds cursor_schema/text, revision/integer and
last_sequence/text; the events table holds event_json/text and the four generic
history columns (sequence/integer, other fields/text); the delivery-index table
holds its five canonical text fields. Each table has the existing text primary
key id. Nullable history columns do not change uncorrelated logical Records:
the existing JDBC codec omits SQL NULL values. Events/indexes have zero rows
after initialization, and only genuine persistent publication advances cursor.

The normal host chain prepares infrastructure once, imports only genuine
selected business inputs, performs public Start, closes and reopens normally,
then reads native state/history. Workflow instance/continuation storage is
materialized by actual Start/checkpoint writes. Declaration of additional record
grants does not request eager provisioning; Development observation is not a
new physical collection and history uses the same EventStore projection.
Reopen never invokes initialization, and read paths retain missing-schema
rejection with no CREATE/ALTER. The existing first-install duplicate check,
authorization and node shutdown ownership remain unchanged.

`EventHistoryCorrelation` validates only an opaque namespace/key and creates a
generic same-row projection. EventRecordJsonV1/V2 append that projection during
their existing Create preparation. Their encoded bodies, retained readback and
managed commit/delivery owners remain unchanged. No application imports, hash
calculation, schema dispatch, lifecycle names or subject parsing is added.

`ManagedEventHistory` owns Query validation and strict canonical page admission.
It constructs a fixed DataStore QueryDirective: exact namespace/key, finite names,
positive sequence, descending sequence, limit+1 and fixed projection. An internal
Provider contract requires a non-mutating managed implementation. It is not a
caller-supplied engine, network service or alternate store.
The finite names use an OR of exact Eq predicates: the existing SQL builder
supports those predicates, whereas its generic In expression is silently omitted.
No new generic expression support is required for this fixed query.

`EventStoreHistoryBinding` captures the existing declaration and active dispatch
lease through Subsystem. It authorizes before resource resolution, resolves the
existing service datastore, captures issuing thread/lease and revalidates access
before every read. Only a managed SQL view with the narrow Provider capability
can execute the query. Subsystem and SystemNode keep all resource ownership.
The public entry is `ManagedEventHistory.bindForCallC(core)`; the returned
`readC(Query)` emits a typed Snapshot of validated EventRecords, observedAt and
truncated, with coverage `retained-correlated-window`.

## Non-mutating managed SQL connection

`SqlDataStore` implements the package-internal Provider method. Its ordinary
`search` -> `_search_empty` -> `_ensure_query_columns` path can add query columns
and is deliberately not used. The new path reuses only its non-owning borrowed
managed connection, read-only query helper and `_search_sql` generation.
The existing borrow admission and resource owner settle the connection as usual.
No history-specific resolver, pool, resource owner or lifecycle is introduced.

`ManagedEventHistory.Provider.readCommittedEventHistoryC(collection, query)`
requires borrowed managed SQLite. Within that borrowed connection, read-only
PRAGMA table_info checks the fixed columns and integer-affinity sequence type.
The fixed generated SELECT carries all predicates, order and limit+1. It reads
six explicit JDBC fields back into canonical generic keys without lossy string
conversion or configured naming aliases. The decoder rejects over-budget results;
ManagedEventHistory then validates every row against canonical bytes, including
look-ahead. No CREATE/ALTER, ordinary search, count, fallback or arbitrary caller
SQL is used. Unsupported dialects/standalone owners are refused explicitly.
An absent table, missing columns or nonnumeric sequence schema must return
explicit unavailability without schema migration on read. Legacy rows in an
otherwise supported table have no projected pair and do not enter the filtered
indexed window. Existing ordinary search behavior remains unchanged.

The four in-scope Specs include real provider integration: two unrelated
namespaces/payloads, unrelated-row source bounds, exact Long ordering, unchanged
SQLite catalog on missing/legacy/nonnumeric schema, rollback, history after
close/reopen, the public ActionCall binding, denied/expired/foreign/revoked lease
and canonical corruption. Runtime test execution, dependency publication and
validation selection remain with the owning Phase caller. No tests were run by
the semantic implementation worker; source completion is not validation or
independent acceptance. Application producers/API/consumer remain separate work.
