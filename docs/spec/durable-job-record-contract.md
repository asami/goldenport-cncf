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
deletion time matches it. Retention, expiry, deletion, and tombstone are record
facts; actual cleanup scheduling is provider work owned by Phase 69.1.

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

## Explicit non-serialization boundary

This contract is not Java/Scala object serialization. In particular, it must
not serialize executable tasks, actions, contexts, component/provider objects,
classloaders, closures, functions, arbitrary object graphs, credentials, or
secrets. Reconstruction means resolving only the admitted descriptor identities
under a later explicit runtime policy; it never means reviving captured runtime
objects. Phase 69.1 owns both provider persistence and process recovery.
