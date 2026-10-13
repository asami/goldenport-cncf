# EventRecord canonical storage encoding

Status: Phase 88 S9 bounded producer contract; focused-270/regression-271 validated on 2026-09-30

This pure boundary encodes existing EventRecord facts for canonical same-store
record composition. It is not EventStore append/load/query/replay and allocates
no identity or sequence. EventId fields remain typed components; no print-string
parsing, hashing, routing or identity inference is introduced.

EventRecordJsonV1 encodes schema cncf.event-record.v1 with exact EventId fields,
name, kind, payload, attributes, createdAt, persistent, status, lane and a
strictly positive already-assigned sequence. EventId components must be
explicitly issued: timestamp and entropy must both be present. Unresolved
EventId options reject because UniversalId otherwise generates hidden identity
parts on construction; no codec-side resolution/regeneration is permitted.
Metadata fields are strict and
unknown/missing fields or unsupported schema/lane/status reject.

Payload has a closed tagged representation: String, Boolean, Int, Long,
BigDecimal, immutable Vector and immutable Map[String, Any]. Numeric types stay
distinct, decimal scale and long precision remain exact. Other values,
including null, mutable collections, arbitrary objects and floating-point
values, reject without fallback to display text. Keys are sorted for canonical
encoding. Bounds: 16 nested levels, 4096 payload nodes, 256 entries per
collection, 8192 characters per string, 128 characters per key/name/kind/ID
component, and 65536 encoded characters. Bounds reject, never truncate.

Encoding is pure and returns a plain String; invalid input is a DbC violation.
decodeC returns a structured failure for malformed/corrupt input and admits only
the exact canonical encoding produced by encode. This also rejects duplicate
JSON keys rather than retaining an ambiguous parser-selected value.

The existing private committed-delivery lookup discriminator has no admitted
durable representation. A record carrying it must reject, never lose it.
This limitation is explicit: S9 cannot store every existing EventRecord or
claim durable committed-delivery deduplication. Future catalog-bound identity
and sequence admission remain required.

createSideEffectC accepts only persistent transactional records with an assigned
positive sequence and an explicit canonical collection. It emits a closed
EntityVersionedSideEffect.Create with one event_json string field.
EntryId uses the opaque canonical EventId.value,
not its presentation print. The field contains the exact prefix
cncf.event-record.v1: followed by canonical JSON. encodeStorage produces this
storage text; decodeStorageC accepts only that exact prefix and canonical body.
The non-JSON prefix prevents the existing SQL provider from interpreting the
JSON-shaped string as a Record. Bare JSON, wrong/missing prefixes and noncanonical
bodies reject; no legacy fallback is admitted. The 65536-character body bound
excludes this fixed prefix. The bridge performs no read/write or store selection.
The containing S7 plan supplies same-store/target/limit admission and S8 owns
native atomic insertion/rollback. A Create collision remains a failure, not
EventStore's first-record-returning append/delivery-dedup behavior. A durable
EventStore must implement that semantic classification separately.

Executable specification: EventRecordJsonV1Spec; memory/SQLite composition,
resource reopen, collision rollback and generated payload round-trips.

No EventStore runtime, global sequence allocation, delivery-key retention,
active UoW data interception, closing program, atomic engine, production binding
or transport Operation is completed by this codec.

## Prepared Workflow batch composition (S10)

`WorkflowPreparedDataStoreTransition.withEventRecordsC` accepts a nonempty
Vector of already-issued persistent transactional EventRecords and one explicit
event collection. All IDs must be distinct and assigned positive sequences must
be distinct and strictly increasing in batch order. It converts every record
through the same strict V1 codec and attaches Create-only effects to the
existing canonical prepared plan. A plan that already contains an effect in
that collection rejects rather than accepting a second independent batch.
The strict codec validates every EventId before the batch derives and compares
the Create entry keys; missing timestamp/entropy cannot generate an identity
during the batch preflight.
Admission of the complete effect set and
same accepting datastore happens before any publication. A rejected item
cannot leave a partial batch; a late Create collision rolls back the guarded
Workflow records and every batch event in one native record transaction.

This method does not reserve globally unique sequences, classify committed
delivery lookup keys, provide EventStore append/replay, intercept active UoW
effects, or make the closing program transactional. The caller must obtain
sequence authority from a future admitted runtime boundary; the method never
infers it from an in-memory counter or from existing rows.

Executable specification: WorkflowPreparedDataStoreTransitionSpec, memory and
SQLite batch publication, rejection, and collision rollback examples.

## S11: authoritative event readback

The prepared event-batch composition now captures the exact canonical Create
bytes during admission. After the canonical provider acknowledges a
Transitioned commit, it loads every event by that prepared identity from the
same borrowed datastore and compares the retained `event_json` bytes before
running the Workflow result projection. Missing, unreadable or divergent event
readback is a transaction-indeterminate outcome: the guarded records may
already be committed, so the caller receives no success and gains no blind
retry authority. A NotMatched transition does not perform event readback.
Preparation still performs no event read; successful memory/SQLite readback is
covered by the existing S10 example, and candidate fault examples inject
missing/divergent post-commit readback.

This strengthens the acknowledgement boundary only. It does not make the
readback itself part of the write transaction or create a global sequence,
EventStore, active UnitOfWork, closing-program transaction or production host.
Focused-281 (45/45) and regression-282 (1191 succeeded / 143 suites) bind the
same complete tree `99047c6527bbcfd4c8369ee2b0b33f9e46892adeb6f63a281f5b8ee9dd65cdf9`,
with exits 0/0 and lock released. Prior S10 receipts apply only to the earlier
tree. P2-U14 is accepted; full P2-U05 remains open.
