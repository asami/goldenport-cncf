# Managed EventStore cursor and delivery-index contract

Date: 2026-09-30
Status: bounded new-only managed V2/index/cursor composition validated; full runtime integration open

## Scope and compatibility

This contract extends the explicit managed verified storage route. It does not
change legacy EventStore.inMemory, ordinary SQL views, EventRecordJsonV1 or
cncf-* skills. Policy selection remains EventStore-owned. Subsystem declarations
admit storage/owners; they do not make every event persistent.

The existing CommittedDeliveryLookupKey is a tuple of invocationKey, typed
EntityId, complete CmlTransitionBinding and operationId. Memory equality uses
the complete tuple. A durable codec must preserve that equality, including
optional operation, optional trigger context, ordered context fields, source
path segments, target discriminator and shallow-history fallback. Invocation
key alone, rendered selectors, payload fields and toString are not identities.

## Cursor record: cncf.event-cursor.v1

One cursor belongs to the declared physical EventStore domain, shared across
its admitted Workflow grants. The host pins one opaque cursor entry identity
at assembly; a Workflow plan or AI result cannot choose a different domain.

Logical storage fields:
- cursor_schema: exact cncf.event-cursor.v1.
- revision: positive EntityRevision-compatible integral value.
- last_sequence: canonical nonnegative decimal Long encoded as text.

Physical SQL id is not a business field. last_sequence uses text to avoid
provider-dependent integer width/decoding; canonical forms are 0 or a nonzero
digit followed by digits, with no sign, whitespace or leading zero. Values
above Long.MaxValue reject. Revision remains the native guarded integral field.

Initial cursor is revision 1 and last_sequence "0". Trusted bootstrap performs
Create-only initialization, never an upsert/reset. A concurrent Create loser
may explicitly load and strictly validate the retained cursor; it cannot reset
it. Missing cursor during normal progression rejects. Cursor removal, domain
change, restoration and migration require explicit maintenance, never inference
from max(event.sequence).

A proposal for n new durable facts guards schema, exact revision and exact
last_sequence. It updates to revision+1 and last_sequence+n, with checked
overflow before staging. Event sequences are contiguous in durable input order.
Transient facts and already-retained facts consume no new slots. An empty or
all-transient batch neither creates nor updates a cursor; its Workflow records
still require a verified two-record commit.

Policy revision is a frozen selection fact, not cursor identity. Policy changes
do not restart sequence allocation or create a new cursor domain.

## Delivery discriminator and versioned event envelope

EventRecordJsonV1 continues rejecting a record with an internal delivery key.
Do not add an optional field to V1 or silently strip the key for encoding.

A separate cncf.event-record.v2 envelope contains the exact admitted V1 event
body plus an explicit delivery discriminator: absent or the complete typed key.
Its encoder/decoder must validate both portions and preserve the key on recovered
EventRecord. The V1 body's key-free projection is allowed only inside this V2
encoder while the full discriminator is retained in the envelope, never as a
general-purpose lossy copy helper. V1 and V2 use separate exact storage prefixes;
unknown schemas, duplicate/missing fields and noncanonical forms reject.
V1 compatibility is explicit, read-only for key-free facts; no opportunistic
migration or fallback when V2 decoding fails.

The discriminator codec must use a closed typed structural representation.
EntityId/EntityCollectionId typed constituents must be read from their own
producer API and reconstructed through admitted constructors, not parsed from
print strings. All binding constituents must round-trip with original equality.
Declaration data is inert: decoded CML binding grants no executable authority.
The scalar encoding/constructor ABI is now admitted for the bounded codecs
described below, grounded in producer source and the consumed jar. Runtime
integration remains a separate gate; decoded identities grant no execution.

## Delivery-index record: cncf.event-delivery-index.v1

Only new persistent facts with an explicit committed-delivery key create a
delivery-index row. Key-free persistent events use the event collection's
EventId identity and need no delivery-discriminator index. Transient events
create neither index nor event row. The index is a deduplication lookup, not
a delivered/acknowledged flag or an exactly-once dispatch receipt.

Logical index fields:
- index_schema: exact cncf.event-delivery-index.v1.
- lookup_key: exact canonical structural delivery-key storage bytes.
- event_reference: exact typed issued EventId components.
- sequence: canonical positive decimal Long text.
- event_body_digest: SHA-256 of the complete canonical stored event envelope.

The bounded index entry identity is a fixed schema-domain prefix and lowercase
SHA-256 of canonical lookup_key bytes. Hashes are lookup accelerators, never
identity/permission proof. On load, compare full lookup_key bytes; a digest
collision or mismatch fails closed rather than aliasing another key. Then load
the referenced event from the same provider and require exact EventId, sequence,
key and body digest. Dangling/mismatched rows reject; no repair, overwrite or
sequence reuse is implied. Index writes are Create-only in the same transaction
as cursor/Workflow updates and their event Creates.

## Duplicate classification and transaction boundary

Legacy memory append gives EventId lookup precedence over delivery-key lookup
and retains the first record. The managed append design must preserve that
lookup precedence, not replace it with payload equality or "last writer wins".
If both indexes name different retained facts, classify structural inconsistency
and reject rather than silently extending that inconsistency. This check is an
explicit stricter integrity rule for the new managed route, not a legacy change.

An existing identity returns its exact retained fact and consumes no sequence.
An additional key presented on an EventId match is not newly bound to that
retained fact; do not silently write an alias index. Batch-local duplicates
follow the same precedence in original order, so the earliest new admitted
fact becomes the retained fact. This semantic result is retention/deduplication,
not permission to redeliver an uncertain prior transaction.

Preparation performs canonical admission and classifies duplicates from one
host-admitted coherent provider snapshot. Existing-row observations must be
covered by cursor revision/sequence validation inside the native write
transaction; no unguarded preflight observation may decide publication.
Bootstrap/migration and every normal event/index writer must participate in this
same domain. Unmanaged bypass writers are not accepted participants; divergent
postcommit evidence remains indeterminate.

Cursor guard conflicts and late Create collisions roll back all staged writes.
The low-level Create composition remains fail-closed, not a "duplicate success"
adapter. Any reclassification is a separate explicitly initiated preparation,
never an automatic replay following rollback or indeterminate acknowledgement.
Commit uncertainty suppresses success and delivery and authorizes no blind retry.
Complete postcommit snapshot verifies Workflow records, cursor, event and every
new index before any application completion/dispatch authorization.

## Delivery and executable acceptance gates

The eventual runtime dispatches original-order mixed candidates only after its
full application/UnitOfWork completion boundary is admitted, not merely after
receiving a storage Verified value. Retained durable facts come from exact
storage evidence; transient facts remain in memory and never enter recovery.
A crash can lose transient delivery. Dispatch failure cannot roll back committed
storage. Durable replay must be explicitly requested/authorized.

Required executable specs before activation:
- Cursor canonical round-trip, overflow, malformed values, forbidden reset and
  bootstrap race; revision and sequence mismatches leave all records unchanged.
- Full typed delivery-key round-trip including every binding variant/option,
  field order and unsupported constituent; V1 retains its rejection behavior.
- V2 key absence/presence, exact prefixes and corrupt/unknown bytes; no lossy key
  copy; catalog/authority cannot be inferred from decoded declarations.
- Index exact key/digest/reference/sequence matching, simulated hash collision,
  dangling row rejection and no index for transient/key-free events.
- EventId-first and key-first lookup paths, batch-local duplicates, divergent
  identity/key indexes, retained-first payload and no extra sequence/index alias.
- Managed writer competition, revocation before commit, partial-write rollback,
  complete readback uncertainty and no automatic retry.
- Selected-only event/index writes, all-transient verified Workflow completion,
  zero actual dispatch on rollback/uncertainty, mixed-order actual dispatch and
  no transient recovery/replay through the configured EventStore service.

All these gates remain open unless separately evidenced. This design does not
close S12/P2-U15/P2-U16/P2-U05 or claim an implemented Operation/CLI boundary.

## Pure cursor executable progress (2026-09-30)

EventCursorRecordV1 implements logical Record encoding/strict decoding and pure
bounded proposals. Native Int/Long positive revisions are admitted; text,
decimal/floating revision coercions are rejected. Seven executable examples
passed focused-323 (183/183 tests in 21 suites), after Cozy compile/export-322.
This validates exact fields, canonical sequence text, extreme values, zero-work
and checked revision/sequence arithmetic only. No storage Create/reset, bootstrap
race, host-pinned identity or managed SQL cursor recovery is established.
An initial value or decoded record is data, never authority to replace storage.

## Host identity/bootstrap executable progress (2026-09-30)

Focused-325 passed 188/188 in 21 suites. The host declaration pins a bounded
StringEntryId (default global), and exact additional-guard entry checks enforce it.
Explicit trusted initialization rejects existing rows before Create/schema work;
ordinary managed loading strictly checks physical identity and logical codec.
Five host examples cover custom identity, retained progress/reopen, concurrent
one-winner Create and malformed/legacy non-repair. No normal-load initialization,
reset, migration, retry or launcher activation is introduced. Guarded versioned
proposal composition was subsequently exercised below; V2/index/runtime gates remain open.

## Versioned guarded progression executable progress (2026-09-30)

Focused-327 passed 191/191 tests in 21 suites. Internal WorkflowCursorEventProposal
derives selected event Creates and an exact host-pinned cursor guard from frozen
selection and loaded cursor facts, then assembles one admitted multi-guard plan.
Expected revision, schema and canonical decimal-text sequence are checked;
native provider-managed revision progression, event Creates and cursor update
share the existing managed transaction and complete verified readback route.
Cursor decoding/matching precedes candidate event projection. Values above 2^53
survive SQLite commit/reopen exactly. Schema/revision/sequence mismatches and
late Create collisions preserve all pre-state; stale root returns NotMatched.
Zero durable facts yield no cursor work and cannot use this multi-guard route.
No reset/migration/retry, duplicate reconciliation, index/V2 codec, dispatch
permission, public Operation/CLI activation or Phase acceptance is introduced.

## V2 / delivery-index pure codec progress (2026-09-30)

Focused-329 passed 199/199 in 22 suites after fixing a test-helper/ScalaTest DSL
name collision in compile-only failed-328. EventRecordJsonV1 is unchanged.
CommittedDeliveryKeyJsonV1 encodes schema/invocationKey/entity/binding/operationId
as canonical bounded structural JSON behind its separate storage prefix.
EntityId is restored via the producer's public restore API (confirmed in the
consumed 0.2.2-SNAPSHOT jar), with saved collection constituents, timestamp and
entropy; no rendered-ID parser or fresh issuance. Machine/trigger/context/version
references are shared only where CML constructors enforce equality. All three
target variants, optional operation/context and ordered scalar fields round-trip.
Unknown/missing/duplicate/noncanonical fields, tags or constructor facts reject.

EventRecordJsonV2 uses exact schemaVersion/body/delivery fields and a separate
prefix. The body is canonical V1; delivery is null or the complete structural
key. Its sole encoder-local key-free copy retains the key in that envelope.
Strict decode restores the discriminator; no V1 fallback or migration occurs.
Index codec fields are exact text; event_reference uses cncf.event-reference.v1
prefixed issued EventId constituent JSON. Sequence is positive decimal Long
text; digest hashes the complete stored V2 envelope including prefix. Entry ID
is delivery-v1- plus SHA-256 of complete canonical key storage bytes. Full key,
reference, sequence, persistent transactional lane and digest must match.
Transient/key-free facts produce no index. Eight examples include full typed
variants, same-row/different-key rejection and ordinary SQLite text recovery.

This is pure codec/integrity evidence, not managed event/index atomic composition
or deduplication. At focused-329 selected-event assembly used V1 and rejected
keyed durable facts; the later explicit V2 route is documented below. Coherent duplicate
classification, revocation/competition, all-transient completion and dispatch
remain open; S12/P2-U15/P2-U16/P2-U05 are not closed.

## New-only managed V2/index composition progress (2026-09-30)

Focused-331 passed 204/204 in 23 suites after correcting one spec import in
compile-only failed-330. Explicit trusted prepareV2C chooses V2 at construction;
the V1 factory/codec stays strict with no sniffing/fallback or migration.
Frozen persistent facts alone receive sequences and event Creates; keyed
persistent facts additionally receive index Creates. Workflow root/Continuation,
host-pinned versioned cursor, V2 events and indexes share one managed native
transaction and complete snapshot via the existing DataStoreSpace route.
Combined side-effect limit 64 and target distinctness remain fail-closed.

Candidate projection first verifies the cursor, then all planned indexes' exact
fields/key/entry/reference/sequence/digest and selected envelopes, then exact
durable canonical bytes. Mixed candidates preserve order, internal key and typed
payloads; transient/key-free facts never produce indexes. Five host examples
prove actual commit/reopen, late-index/cursor rollback, reused-key plan rejection,
missing/divergent index suppression and all-transient no-work. Native storage
Verified for a different/defective submitted index plan is insufficient for
semantic candidates; no postcommit repair or automatic retry is performed.

This new-only composition is not retained-first duplicate classification,
configured EventStore append/replay, application completion or dispatch admission.
Coherent EventId-first/full-key classification guarded by cursor facts, writer
competition/revocation, verified two-record all-transient completion and actual
delivery remain open. S12/P2-U15/P2-U16/P2-U05 remain open; no CLI activation or
typed Phase/PLAN acceptance is claimed.

## Pure duplicate classifier progress (2026-09-30)

Focused-335 passed 212/212 in 24 suites after enum naming repair-332 and stale
Cozy classpath-333 recovery through ordinary compile/export-334 (no source edit).
ManagedEventDuplicateClassifier interprets supplied bounded canonical event/
index inventories and cursor facts. It validates unique identities/sequences,
exact key/index/envelope integrity and keyed-event intrinsic index closure.
Explicit key-free V1 observation recovery is read-only, never V2 failure fallback.
EventId lookup precedes full typed-key lookup; divergent matches reject.
Retained-first results preserve canonical bytes and neither additional key nor
incoming EventId aliases are registered. Only earliest new durable facts consume
cursor slots; retained/transient-only results need no capacity even at exhaustion.
Frozen canonical templates yield fresh unissued clones so mutable hidden keys
cannot change the result. No Create, plan, provider access or delivery is emitted.

Eight specs establish pure semantics only. Inventory vectors do not prove live
snapshot provenance, row absence or physical IDs, and no managed commit adapter
exists. Current EventStore selection still rejects duplicate EventIds, while
batch-key reuse still rejects in new-only managed assembly; classifier fixtures
do not change those contracts. A future host-generated bounded lookup/reference
closure must collect cursor, present rows and absent observations in one native
managed snapshot, with opaque lease/domain/batch provenance. Native publication
must validate the observation cursor and retain/verify exact existing facts.
All-retained validation without cursor increment is distinct from all-transient
no-cursor work. Generic load loops/caller vectors must not authorize this path;
stale/revoked/uncertain evidence grants no reclassification or automatic retry.
These snapshot/commit/runtime gates and S12/P2-U15/P2-U16/P2-U05 remain open.
