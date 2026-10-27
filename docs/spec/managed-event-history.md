# Managed Event History

CC51-S2 Unit A, 2026-10-25. CNCF owns generic event persistence, correlation
metadata, bounded history reading, authorization and managed leases. Application
envelope decoding and subject-to-key mapping belong to each producer/consumer.
No application schema, namespace value, workflow vocabulary or payload type is
recognized by this API.

## Correlation and immutable storage

Existing EventRecord attributes optionally contain both `history.namespace` and
`history.key`. Their exact opaque values are nonblank, trimmed, free of control
characters, valid UTF-8, and at most 128 and 256 bytes respectively. Both absent
keeps the original event row shape. One absent, null or malformed is invalid.
Correlation is a lookup association, never authentication or acceptance evidence.

For persistent transactional sequenced events, V1/V2 Create helpers add generic
same-row fields `event_history_namespace`, `event_history_key`,
`event_history_name` and numeric Long `event_history_sequence`. Canonical encoded
event bytes and their decoders are unchanged. Existing events are not rewritten,
backfilled or moved. Correlation introduces no separate table, store or cursor.

## Explicit EventStore first installation (R1–R5)

R1. A trusted host may call
`SubsystemEventStoreBootstrap.initializeStorageC()` before its first persistent
publication. This explicit operation uses the captured persistence declaration,
existing managed lease/provider and one SQLite transaction. It creates missing
fixed cursor, events and delivery-index tables and exactly the genuine initial
cursor (revision 1, sequence 0). Events and indexes stay empty. The event schema
includes the nullable history projection from the beginning. Workflow instances,
continuations, business inputs, evidence and Journal entries are created only by
their genuine normal operations, never by infrastructure installation.

R2. Installation is create-only. Any existing cursor row, orphan event/index data
or incompatible existing infrastructure schema must cause rejection before any
write. Compatible empty tables have the fixed fields/affinities and text primary
key, nullable non-key columns and no synthesized defaults. They may be reused. Installation performs no ALTER,
reset, implicit migration or fake create/delete. The original `initializeCursorC`
signature and cursor-only behavior remain unchanged; an existing cursor-only
database is not silently upgraded by `initializeStorageC`.

R3. A known precommit failure rolls back schema and cursor together. Lost
admission prevents publication; uncertain commit acknowledgement remains
indeterminate, without automatic replay. Concurrent installers cannot both
succeed. The existing managed transaction and connection lifecycle own settlement.

R4. Repeated installation refuses existing state without changing it. Normal
close/reopen reads that state without installation. Persistent publication creates
only real selected events and actual delivery keys; uncorrelated events do not
gain phantom metadata through nullable schema columns.

R5. Host construction, ordinary Start and observation do not install storage
implicitly. Missing tables remain a structured observation/history failure.
Standalone/unmanaged providers and unsupported dialects cannot perform the new
initialization. No general schema API, new grant, Operation or independent
resource owner is introduced.

Executable examples E1–E8 are in the already selected
`EventStoreHistoryBindingSpec` and `ManagedEventObservationIsolationSpec`.
E1 covers public initial installation, E2 the first genuine guarded publication,
E3 duplicate initialization and retained reopen, E4 nullable uncorrelated event
round-trip, E5 missing/unowned/unsupported provider refusal, E6 compatible empty
schema subsets and invalid retained installations, E7 failure/uncertainty
settlement, and E8 concurrent independent installers.
Selected source validation and independent review remain caller-owned; authored
examples alone establish no execution or acceptance.

## Normal read capability

`ManagedEventHistory.bindForCallC(core: ActionCall.Core)` resolves only the
current Component's owning Subsystem. That Subsystem captures the existing
EventStore persistence declaration and named managed service store. Binding
requires ordinary EventPolicyEngine introspection authority and an already
active dispatch lease. A capability is thread/lease-bound; neither expired nor
cross-thread use acquires another lease. It has no write, replay or dispatch API.

`readC(query: ManagedEventHistory.Query)` accepts exact correlation namespace/key,
1..32 distinct bounded event names, and limit 1..100 (default 20). It applies
the existing introspection check on read as well. A provider must select the pair
and names before descending numeric sequence order and limit+1, with no schema
mutation, unbounded fallback, offset or total-count query. Results are a retained
indexed window, not complete history. Decimal sequence remains a Long in Scala;
wire consumers must preserve its precision.

Every fetched row, including the extra truncation row, must match the canonical
event ID, generic attributes, name, persistent transactional status and numeric
sequence. Reject duplicate IDs, unordered/duplicate sequences, crossed scope,
malformed envelopes, unknown storage prefixes and over-budget results. Do not
drop bad rows or sort/re-limit an invalid provider response into success.
Only validated rows form a snapshot with actual read time and truncation flag.

## Managed SQLite provider

The existing SqlDataStore.search calls `_ensure_query_columns` and may ALTER
TABLE while searching legacy rows. It cannot be silently reused for the strictly
read-only contract. `SqlDataStore.readCommittedEventHistoryC` instead implements
the internal narrow provider capability using the existing borrowed managed
SQLite connection and its admission callback. The operation does not open a new
resource, acquire a replacement lease, or invoke ordinary search.

Read-only schema inspection requires the existing six projected columns, with
SQLite integer affinity for sequence. An absent table, missing columns or
nonnumeric sequence schema produces explicit unavailability; reading never
initializes or migrates that schema. Legacy rows in a supported table lack the
projected pair and remain outside the indexed window. A fixed parameterized
SELECT applies the complete predicate before numeric descending order and
limit+1, reads only canonical/projected fields, and retains exact JDBC integer
values. Unsupported dialects and standalone/unmanaged resource owners are
refused. The internal provider method is not a public caller-selected SQL API.

The authored Executable Specs cover generic projection/validation, normal public
binding, successful bounded history amid unrelated/legacy rows, schema preservation,
managed transaction commit/rollback and history after host close/reopen. They also
cover exact Long order, canonical corruption in the look-ahead row, authorization
and expired/foreign/revoked leases. They have not been compiled or run by this
worker; actual checks and independent review remain with the owning workflow.
No completed Unit A/S2 acceptance follows from authored source alone. See the
[design](../design/managed-event-history.md) for connection and ownership details.
