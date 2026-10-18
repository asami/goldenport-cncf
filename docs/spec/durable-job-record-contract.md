# Durable Job Record Contract

status=normative
phase=JM69-03C
schema=`cncf.durable-job-record/v2`

This is the normative durable execution-record contract for `JM69-03C`.
It defines the provider-neutral value that a later durable provider may write
and that a later process-recovery implementation may consume. It is not a
provider API, recovery implementation, live-execution serialization format, or
management-route payload.

## Authority split

`JobRecord` remains the live execution authority: it owns active execution,
runtime scheduling, executable `JobTask` instances, Actions, current
`ExecutionContext`, and in-memory result mechanics. `JobEntity` remains the
store/search/admin management projection. It is not a recovery source and does
not gain the durable record's authority through this contract.

`DurableJobRecord` is a closed, immutable, provider-neutral reconstruction and
audit value. It contains only admitted value descriptors. It owns neither a
live task object nor provider residency. No current execution semantic changes
and no conversion from `JobRecord` or `JobEntity` is implied by this phase.

Phase 69.1 exclusively owns provider writes, durable reads at runtime, and
process recovery. A provider must not be inferred from an opaque reference.

## Closed record shape

The v1 and v2 wire roots have exactly `format`, `record`, and `integrity`. The
`format` has `schemaId = cncf.durable-job-record` and its admitted version.
Unknown fields are refused at the root and every nested object level.

`record` contains the following complete field groups:

- `identity`: stable job id, monotonic revision, creation time, and update
  time.
- `authorization`: tenant, submitting subject (`id` and `kind`), visibility,
  and required scopes.
- `lifecycle`: status, bounded priority, run mode, schedule state, and retry
  evidence. Attempts are one-based and contiguous; retry limits and exhaustion
  are explicit.
- `tasks`: an ordered reconstructible tree of task descriptors. A descriptor
  has its stable task id and optional parent id, admitted kind, target kind and
  operation reference, relation, transaction descriptor, and optional
  compensation descriptor. Parent ids must resolve within this record and the
  graph must be acyclic.
- `inputs` and `result`: typed outcomes and typed values. A value is exactly
  `inline-metadata`, `external-reference`, or `absent`. Inline data is only
  bounded metadata (type, content type, byte size, digest, classification),
  never an input body. External values are opaque references with storage kind,
  byte size, digest, and content type.
- `timeline`, `diagnostics`, and `calltreeReference`: strictly monotonic,
  ordered event evidence; bounded diagnostic summaries; and an optional opaque
  calltree reference. Calltree contents are outside the record.
- `definitionSnapshot`: immutable definition id, key, version, revision, hash,
  optional declared-source reference and format, and a bounded declared-profile
  map. Map keys are sorted by the canonical encoder.
- `retention`: retain-until, expiry, deletion state/time, and an optional
  tombstone. A tombstone repeats the deletion instant and records only a reason
  and optional replacement record identity.

`integrity` contains the fixed `sha-256` algorithm and lowercase SHA-256 digest
of the canonical unsigned body. The unsigned body is the explicit ordered JSON
object containing `format` and `record`, without `integrity`. The format is
therefore part of every digest boundary: a v1 and v2 record with the same body
have distinct integrity digests.

## V2 pending result

V2 adds exactly one result outcome, `pending`. It has no value and no failure
payload. It is admitted only when lifecycle status is `submitted`, `running`,
or `suspended`. Terminal lifecycle statuses remain paired with their matching
terminal result: `succeeded`, `failed`, or `cancelled`.

V1 remains terminal-only. It cannot carry `pending`; its canonical text,
integrity calculation, redaction boundary, and terminal decoding behavior are
retained. The metadata-only public projection exposes a V2 pending result as
`outcome = pending`, with no public value or failure detail.

`DurableJobRecord.create` remains the legacy V1 terminal-only constructor.
`DurableJobRecord.createV2` is the explicit V2 constructor, and
`DurableJobRecord.migrateV1ToV2` is the explicit one-way migration.

No durable model or codec API admits `JobTask`, `Action`, `ActionEngine`,
`ExecutionContext`, `Component`, provider instances, `Class`, closures,
functions, arbitrary `Any` values, raw credentials, secret material, raw input
bodies, raw result bodies, or calltree internals.

## Canonical codec and migration

### Authored V2 live normal aggregate interpretation

C2B2B1 factors closed task projection into the private DurableJobTaskProjection,
without changing any signed field, codec, schema or public model. V1 retains
its strict descriptor correspondence. V2 permits a targetless normal Root/Child
model's optional nonblank declaration action with descriptor compensation=None.
Its aggregate fields remain on the exact typed terminal request and are checked
before signing; terminal preprocessing does not erase them.

Aggregate succeeded/failed requires a completed committed Succeeded original
and exactly one actual completed companion targeting it, with matching outcome
and compensationStatus. Failed requires both original/model and job retry
recoveryRequired. Missing requires those same recovery flags, nonblank failure,
no companion and no original target; its declaration action may be absent.
Running is intermediate only. Unknown status, stray/blank failure/action,
missing/multiple/mismatched companion or a missing recovery flag refuses.
Every actual companion requires complete action/target/status and unchanged
exact source equality. Complete historical normal descriptors with explicit
target retain their prior strict semantics. No self-target, invented action,
live getter/provider or descriptor normalization supplies missing authority.

NRT-P1..P3 cover pure projection; NRT-F/K cover actual configured engine and
real SQLite terminal Bridge observations. These additions passed current runtime VAL-003 focused validation; independent
Slice conformance, Step acceptance and commit remain pending. Root A's historical
VAL-005 (53 suites/463 passed/zero failures/zero warnings/pending1) and independent
conformance PASS remain bounded incremental proof. Mandatory B2 fresh-State
runtime/terminal registration and all later Phase acceptance remain open.

### Continuation strict compensation correspondence

An existing Continuation relation retains strict compensation correspondence
in Running and Succeeded V2 projection. When all live compensation fields are
empty, its supplied descriptor must have compensation=None. Any nonempty but
incomplete action/target/status metadata, including a targetless declaration,
status or failure summary, refuses before a durable record is returned.
A complete explicit target retains the existing strict descriptor equality,
including its operation, target, status and optional failure summary. Projection
does not invent a companion or normalize the live model or supplied source.

The targetless normal aggregate exception above remains Root/Child only.
Continuation still requires exact task identity, parent/reference, kind/target,
operation and transaction correspondence. V1 retains terminal-only admission
and strict correspondence; Running V1 remains refused. Schema, codec/wire,
live source authority and actual compensation-companion semantics are unchanged.

[DurableJobProjectionSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobProjectionSpec.scala)
authors CP1 (both lifecycles, canonical authorized roundtrip and V1 compatibility),
CP2 (all seven nonempty targetless subsets in both lifecycles and complete
explicit correspondence), and CP3 (six independent supplied-source divergences
in both lifecycles). The actual C10 EC1..EC9 process acceptance remains in
[JobJclEventCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessAcceptanceSpec.scala).
These repair additions passed parent-selected subject4 validation recorded below.
The [C10 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-event-cross-process.md)
retains actual failed-subject3 history and separate original whole-Step review
and Phase acceptance obligations.

`DurableJobRecordCodec` is the sole v1/v2 value codec. It produces explicit-field
order compact JSON and UTF-8 bytes. Set values are sorted and the only map,
the declared profile, is key-sorted. Equivalent admitted records therefore
produce byte-identical canonical text and the same digest.

The codec accepts only:

- v1: `format` plus the closed `record` body and valid integrity member; and
- v2: `format` plus the closed `record` body and valid integrity member, with
  the V2 pending-result rule; and
- v0: the identical closed body with `format.version = 0` and no integrity
  member.

v0 decoding is a one-way, explicit `v0 -> v1` migration: the v1 canonical
unsigned body is rendered and a valid v1 integrity digest is generated. v0 is
never re-emitted by the codec. An explicit `v1 -> v2` migration re-signs an
already admitted V1 body under the V2 format. It does not select a provider,
write storage, automatically migrate a record, or support downgrade. No other
version is compatible.

## Operational wire admission

The operational wire budget is **1,048,576 UTF-8 bytes inclusive** and JSON
container nesting depth **64 inclusive**. Received text includes all surrounding
and internal whitespace in its byte budget. UTF-16 `String.length` is only an
early refusal guard: strings longer than the byte budget are refused first;
UTF-8 bytes are then counted only for strings within that character guard.
Multibyte and supplementary Unicode therefore consume their encoded byte size.

Before strict JSON parsing, an iterative O(n) quote/escape-aware scan counts
objects and arrays, including the root container. Braces and brackets inside
JSON strings do not count. Escaped quotes and backslashes do not end a string
incorrectly. The scan owns resource admission only; the existing strict parser
continues to own grammar, duplicate-key refusal and closed-schema admission.

`decode` and startup admission refuse over-budget text before parser entry.
Signing checks the unsigned byte budget before computing SHA-256, then checks
the final signed canonical text including the integrity envelope. V1 and V2
signing, `canonicalJson`, `canonicalBytes`, `toRecord`, `publicProjection`, and
explicit V1-to-V2 migration all require that final signed text to fit. A
manually constructed record with a correct digest cannot bypass this budget.

The V0 canonical envelope has its own inclusive wire budget. An admitted V0
text may nevertheless be refused during migration if the added V1 integrity
envelope exceeds the final signed budget. No producer truncates or drops fields,
normalizes received text to evade limits, repairs a digest, or relaxes access
requirements. The V0/V1/V2 schema, canonical field ordering and SHA-256 algorithm
remain unchanged.

Limit refusals use constant bounded messages naming only the violated byte or
depth limit, without submitted text, payloads, references, subjects, tenants,
secrets or paths. Startup admission classifies these refusals as `Corrupt` and
returns no partial record; authorization refusal remains distinct.

Operational scope and later pending work are defined in the
[Job Operations Contract](job-operations-contract.md). The executable boundary
is authored in
[DurableJobRecordResourceBoundsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobRecordResourceBoundsSpec.scala);
JM69-09A focused receipt `PH697-A-003` passed 36 tests in 5 suites with no
compiler warnings; independent Step review and commit remain pending.

## Refusal categories

Codec admission returns `Consequence.Failure`; it does not throw, recover a
partial record, or silently discard data. The structured refusal boundary
includes malformed JSON/types/instants, duplicate JSON fields, absent or
unknown mandatory schema fields, unknown keys at any level, unknown versions,
invalid schema identity, duplicate task ids, orphaned or cyclic task parents,
unknown event task references, invalid or non-monotonic timeline sequence,
malformed digest, integrity mismatch, out-of-bound summaries or profile maps,
and invalid retention/expiry/deletion/tombstone combinations.

The codec also validates scope before returning an admitted record. Tenant
mismatch, submitting-subject mismatch, and missing required scope are failures.
The v1 subject equality rule deliberately does not imply an administrative or
delegated access bypass; a future such policy needs its own versioned contract.

## Retention and integrity lifecycle

Active records have neither deletion time nor tombstone. Expired records require
an expiry and no deletion/tombstone. Deleted records require a deletion time and
no tombstone. Tombstoned records require a deletion time and a tombstone whose
deletion time matches it. These are historical wire facts. Phase 69.7 owns their
operational retained-lifecycle policy, physical cleanup and maintenance
scheduling; Phase 69.1's persistence and process-recovery ownership is retained.

The [Job Operations Contract](job-operations-contract.md) defines signed Keep
(`expiresAt=None`), TTL (explicit expiry), and LegalHold (`retainUntil=Instant.MAX`)
meaning without a schema change. It fixes terminal-only inclusive expiry and
logical deletion, hold precedence, explicit replacement on hold release, the
additional manage capability with exact tenant/subject/scopes, and optimistic
current-snapshot checkpoints. Logical deletion preserves all external references
through physical cleanup and tombstoning. B1 implements these bounded operations;
focused receipt `PH697-B1-001` passed 58 tests in six suites, including 17 new
retention examples, with zero compiler warnings. Independent Step review and
commit remain pending.

The authored B2 operational adapter issues only fresh, uniquely allocated
`job-owned-blob` references from current normally authorized Active snapshots.
Ownership is exclusive immutable Blob provider metadata, without a new Job
field: `exclusive-v1`, a length-prefixed SHA-256 creation/principal label, and a
length-prefixed SHA-256 exact ID/storage/version/etag/content/size/digest binding.
Absent optional values have a distinct tag. The
[Job Operations Contract](job-operations-contract.md#exclusive-job-owned-payload-authority)
defines the precise encoding and trusted-provider boundary. Shared, legacy,
external and definition assets remain under their owner's retention authority;
a protected canonical identity overlap refuses cleanup.

Terminal Deleted cleanup additionally requires manage capability, unchanged
ordinary authorization, valid canonical integrity and the current complete
optimistic snapshot. It preflights the entire inclusive 1024-reference vector
before physical effects, then disposes only exact provider-issued owned storage
references. Provider delete acknowledgements are disposal authority. Partial
failure keeps Deleted, every reference and all Blob metadata for native fresh
provider retry; read failures do not prove absence. Corruption is never repaired.
An optimistic race after physical disposal preserves current authoritative
state and permits a later eligible retry without restoring bytes.

The final format-preserving Tombstoned checkpoint advances revision once and
updates time, repeats the original logical deletion instant, records reason
`job-owned-payload-cleanup-complete` and no replacement ID, and retains every
non-retention body fact/reference. It certifies Job-owned byte disposal rather
than shared/external/definition disposal or metadata erasure. A repeated
tombstone is a stable no-op; non-owned-only Deleted may be tombstoned without
provider deletion. The bounded explicit coordinator and injected-clock/timer
lifecycle owner are defined in the operational contract. They add no wire,
signing, public ABI or automatic runtime activation change.

B2's physical cleanup, tombstone and scheduled-maintenance implementation and
O/P/M executable scenarios passed focused receipt `PH697-B2-003`, task
`PHASE-69.7-JM69-09B2-VAL-003`: 11 discovered suites, 106 tests, 23 new examples,
zero failures and zero compiler warnings. Independent Step review and commit
remain pending. Configured runtime-owner acceptance and real fresh-process
behavior remain Phase69.7 obligations.

The [B3A runtime resource rules](job-operations-contract.md#b3a-runtime-resource-rules)
bound retained identities (4096), pending (1024), executing (64), submitted tasks
(1024) and retry attempts (32), with private lower-only configuration. The actual
shared State owns opaque provisional/committed leases; reservation precedes IDs,
native admission and insertion. Due generations transfer their existing pending
lease once; worker/Sync/drain execution releases in finally; shutdown preserves
retained and persisted schedule/retry facts and injected shared timers. Complete
bootstrap and runtime/terminal registration refuse oversupply without truncation,
checkpoint or task dispatch, preserving canonical authorization and observed
entity-sync timestamps. Retry refusal keeps the original conclusion and actual
count with terminal checkpoint ordering. These are runtime bounds with unchanged
wire/ABI shapes, not persisted storage quotas. B3A focused task
`PHASE-69.7-JM69-09B3A-VAL-006` discovered 20 suites and passed 160 tests with
zero failures and zero compiler warnings; independent Step review, acceptance
and commit remain pending.

The separate [B3B1 native storage rules](job-operations-contract.md#b3b1-native-job-storage-resource-rules)
author actual provider-persisted retained Job cardinality (4096) and canonical
UTF-8 bytes (64MiB), with private positive lower-only policy persisted in one
StoreOnly/Detached operational quota ledger. Every native terminal, Deleted,
Tombstoned or malformed canonical text row consumes capacity. Complete bounded
native census, same actual searchable provider/native CAS proof, guarded
bootstrap, opaque pending reservations and original observed Job OCC preserve
retained identity across fresh providers. Missing/unchanged rows retain ambiguous
pending charge; only actual row existence for create or strictly advanced native
revision for checkpoint fences the old write. Missing committed rows remain
occupied ghosts. Native acknowledgement confirms actual bytes/hash/revision;
lost acknowledgement or confirmation refusal never deletes or rolls back an
admitted Job. Surviving malformed operational ledgers and policy mismatch refuse
without reset. Its closed version-1 JSON has independent inclusive 16MiB UTF-8
and depth-16 budgets, with no signed Job schema or public API change. This bounds
canonical native values and conservative reservations, excluding SQLite/WAL/page/index,
memory and Blob bytes. Ordinary authorized reads and prior corrupt/startup refusal
facts retain their original authority. B3B1 focused validation passed
`PHASE-69.7-JM69-09B3B1-VAL-006`: 34 discovered suites, 281 passed, zero failed,
zero compiler warnings and one existing SqliteDataStore pending case;
[DurableJobStorageQuotaSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStorageQuotaSpec.scala)
provides adjacent GWT/should native scenarios S1..S16.

The native SQL carrier preserves the exact JDBC String value of `canonicalJson`
for `EntityStore(EntityCollectionId("cncf", "builtin", "durableJob"))`,
`EntityStore(EntityCollectionId("cncf", "builtin", "durableJobStorageQuota"))` and
`EntityStore(EntityCollectionId("cncf", "builtin", "durableJobOwnedBlobStorageQuota"))`.
It also preserves exact opaque `jobId` strings in the former collection, including
IDs that resemble JSON objects or arrays. Load, bounded full/projected search and
authoritative native mutation readbacks share this collection-aware mapping.
With either `normalizeColumnNames` mode, the existing logical key normalization
precedes the text decision and `cncf_revision` keeps its managed storage key and
numeric value. Submitted whitespace, field order, duplicate keys, malformed text
and Unicode remain native evidence; the provider does not parse, trim, validate,
repair or re-encode these fields, or coerce non-String JDBC values into text.

Phase100 physical-provider activation dated 2026-10-18 explicitly supersedes
the historical closed SQL exemption: [R2/R8](datastore-logical-type-mapping.md#r2-physical-text-and-exact-string)
requires exact physical Strings for every collection and field. Ordinary
Instance collections, foreign major/minor/name identities and other builtin
fields receive the same text contract; names and payload shape supply no codec.
Callers explicitly encode known Record/owned/repeated values before plain SQL
writes and select their logical read codec separately, or use declared mappings.
Native numeric conversion remains unchanged. Signed Job and private quota parsers
continue to own strict grammar, duplicate-field refusal, wire/depth budgets,
integrity/schema checks and ordinary authorization; preserving corrupt text does
not admit it. Native detached revision and optimistic concurrency are unchanged.
[SqlDataStoreCanonicalTextSpec](../../src/test/scala/org/goldenport/cncf/datastore/SqlDataStoreCanonicalTextSpec.scala)
authors N1..N8 actual SQLite scenarios, including fresh-owner bytes, stale CAS
no-effect behavior and seeded generated strings. The exact third collection runs
the original positive cases under both naming modes and fresh providers; same-name
Instance and wrong-major/minor identities remain negative controls. B3B1 has the
focused receipt above. B3B2 is authored with validation pending; independent
review and later acceptance/release gates remain open.

The separate [B3B2 owned Blob storage rules](job-operations-contract.md#b3b2-native-owned-blob-storage-resource-rules)
author inclusive private ceilings of 4096 occupied IDs, 268435456 admitted bytes
and 8388608 bytes per payload. Strictly positive lower-only limits persist in the
StoreOnly/Detached/Optimistic operational singleton at Instant.EPOCH, entropy
`owned_blob_quota_v1`. This private version-1 ledger changes no signed V0/V1/V2
record field or public API/ABI. Current signed Active authorization precedes
one-stream bounded admission; actual detached bytes and lowercase SHA reserve
through native CAS before Blob put. Acknowledgement must prove exact allocated
ID/store/ref, size, requested header and digest content. Preserve valid provider
digest spelling in retained Blob metadata and compute the original exact binding
from that spelling; normalize only the ledger digest. Keep precisely the original
three ownership attributes and CNCF route, without adding token/state metadata.

One actual searchable native OCC provider must own Job, Blob and quota collections.
Complete ascending native Blob census uses pages of 32 and a 32768-row ceiling
plus one sentinel; guarded bootstrap and refreshed stale CAS retries are bounded
at eight. Partial ownership labels, malformed rows, inconsistent immutable facts,
unsupported/split routing and policy mismatch refuse without resetting authority.
Missing metadata, process restart and get refusal never release capacity. Every
exclusive-v1 asset counts, including protected shared/definition positions whose
deletion remains forbidden. Pending and Published consume exact admitted count
and bytes; Released consumes history only and permanently refuses ID reuse.

Closed operational entries retain exact owner/content/size/digest, optional issued
32-lowerhex token and binding, and exactly Pending/Published/Released states.
Sorted unique IDs, globally unique tokens, state/binding pairs and checked usage
are strict. Unknown/missing/duplicate fields, primitive/null shapes, version/policy
errors and invalid usage refuse. Inclusive wire/depth limits are 16777216 UTF-8
bytes and 16; historical entries 16384, Blob ID 1024 bytes, header 512, filename
1024, store 256 and storageRef.print 2048. Check String length before allocation
or hashing. The private snapshot returns only five numeric usage counts and three
limits with fixed redacted labels. Payload quotas exclude SQLite/WAL/indexes,
sidecar overhead, ordinary/shared/definition payloads and aggregate process memory;
they rely on the existing trusted unique allocator.

The full original reference/ownership/shared/definition and exact quota preflight
finishes before any cleanup delete. Zero-owned plans keep their no-Blob-provider
path. Exact physical delete acknowledgement precedes exact durable Released CAS,
whose response follows persistence. The current-call and later accounting outcomes
are independent:

| Boundary | Current call and signed fact | Authoritative later usage |
| --- | --- | --- |
| Physical delete refuses before effect | Failure; Deleted retained | Pending/Published occupied |
| Physical delete commits, response lost | Failure; Deleted retained, no release CAS | Pending/Published occupied despite absent files |
| Physical delete acknowledged, release CAS refuses before persistence | Failure; Deleted retained | Pending/Published occupied despite absent files |
| Physical delete acknowledged, release CAS commits, response lost | Failure; Deleted retained without new tombstone | Released frees count/bytes on fresh reads |
| Physical delete and release CAS acknowledged | Success; whole cleanup may checkpoint tombstone | Released frees count/bytes |
| Fresh Released-target retry | Renew original idempotent physical delete and exact release confirmation before success | Released remains Released, no second decrement |

Producer rollback applies the same matrix to its exact issued lease. Missing put
acknowledgement or invalid/foreign handle never authorizes deletion or release.
Publication always remains refusal after a failed publication, including successful
rollback. A failed CAS response has no same-call readback-success shortcut.
Retain Blob metadata and every signed Job reference after release/tombstone;
partial effects persist, fresh retries complete pending releases, and stale/failed
Job checkpoints never revive disposed bytes. No fourth state, TTL or history
compaction exists. [DurableJobOwnedBlobQuotaSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOwnedBlobQuotaSpec.scala)
authors P1..P19 with actual file SQLite/LocalBlobStore, independent fresh owners,
adjacent GWT/should, bounded barriers/finally joins and seeded finite sequences.
B3B2 remains authored with validation and independent Step acceptance pending.

B3B3 input/lifetime/history,
whole-vector 1..1024 recovery-source and page/control resource bounds remain
mandatory. C/D observability/health and operational
acceptance, every JM69-10 fixture, aggregate full validation, independent reviews
and release remain mandatory. JM69-09/Phase69.7 remain IN_PROGRESS and JM69-10
remains OPEN; B3A/B3B1/B3B2 close no aggregate criterion. B3B2 library fixtures
cannot replace mandatory real cross-process or fresh CBD runtime acceptance.

Every v1 or v2 read verifies the stored integrity digest against its canonical
format-bound unsigned body before authorization is accepted. Every canonical
write must recalculate the digest; a stale digest is refused rather than
repaired in place.

## Isolation and ordinary projection

An access request names tenant, subject, and scopes. Codec decode and ordinary
projection require the tenant and submitting subject to match and all required
scopes to be present. Cross-tenant and cross-subject requests are refused.

The ordinary public projection is metadata-only. It must redact raw input,
credential/secret material, raw result bodies, and calltree internals. It may
retain admitted opaque external references and bounded inline metadata, because
they are necessary for authorized durable audit and later recovery coordination
without revealing their bodies.

After validation and authorization, ordinary projection additionally requires
Active retention. Recovery classification also refuses Expired, Deleted and
Tombstoned with a fixed bounded refusal before lifecycle classification. Those
states remain canonically readable for authorized maintenance, and an Active
TTL remains readable until explicit maintenance checkpoints expiry. Neither
projection nor recovery infers expiry from a clock.

## Explicit non-serialization boundary

This contract is not Java/Scala object serialization. In particular, it must
not serialize executable tasks, actions, contexts, component/provider objects,
classloaders, closures, functions, arbitrary object graphs, credentials, or
secrets. Reconstruction means resolving only the admitted descriptor identities
under a later explicit runtime policy; it never means reviving captured runtime
objects. Phase 69.1 owns both provider persistence and process recovery.

Current runtime evidence `PHASE-69.7-JM69-09B3B3C2B2B1-VAL-003` records
54 completed / 0 aborted suites, 477 run / 477 succeeded / 0 failed tests,
zero compiler warnings and one existing SqliteDataStore pending example.
Independent incremental Slice conformance, independent Step acceptance and
commits remain pending. Mandatory B2 fresh-State recovery, C/D, JM69-10 actual
two-process/CBD Support and original full Phase obligations remain open.
See the [runtime validation chronology](../journal/2026/10/2026-10-02-phase-69.7-native-lifetime-runtime.md).

## Selected repaired-subject4 validation

| Selected actual evidence | Result |
| --- | --- |
| [Focused](../../.codex-workflow/phases/PHASE-69.7/JM69-10C10-fix03-focused02-result.json) | 2 suites; 25/25 passed; failed0; pending0; compilerwarnings0; reader0/SBT0/wrapper0; lock=released |
| [Representative](../../.codex-workflow/phases/PHASE-69.7/JM69-10C10-fix03-representative01-result.json) | 9 suites; 92/92 passed; failed0; pending0; compilerwarnings0; reader0/SBT0/wrapper0; lock=released |
| [Accumulator](../../.codex-workflow/phases/PHASE-69.7/JM69-10C10-fix03-accumulator01-result.json) | 130 suites; 1069/1069 passed; failed0; pending1; compilerwarnings0; reader0/SBT0/wrapper0; lock=released |

The first repaired [focused01 attempt](../../.codex-workflow/phases/PHASE-69.7/JM69-10C10-fix03-focused01-result.json) stopped before compilation/test
on a stale Cozy development classpath. Authorized [CP002 export](../../.codex-workflow/phases/PHASE-69.7/JM69-10C10-cozy-classpath-prerequisite02-result.json)
succeeded at the selected0.3.3-SNAPSHOT; its actual prerequisite notices remain
in that separate record. Only then was the same selected focused command run
under a fresh invocation. Every listed actual result consumed its own permission
and released the shared serial lock; no failed request was replayed.

The actual C10 failing surface narrowed8→7→5→0 through three consumed
pre-review repairs, nonconverging0. CP1..CP3 and all EC1..EC9 passed, including
the original min24 real-process shrinking property. The accumulator retains
only the previously admitted SqliteDataStoreSpec pending case.

This is selected incremental validation only. StageC10 remains IN_PROGRESS,
JM69-10 OPEN, Phase69.7 in_progress and acceptance not-accepted. Original
whole-JM69-10 independent protected M2/local Step commit, actual CBD provider→
Entity→completed full Review and independent fresh recovery, positive started-work
closure, predecessor deferred-full validations, aggregate final full validation,
sole epoch1 full Phase review and final local release remain OPEN.
Original authority/base/binding/history/counters and failure records are retained.
No push, publication, deployment or successor work occurred.
