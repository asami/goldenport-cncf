# Entity Collection Identity

Status: decided

## Purpose

This design defines the complete runtime identity of an Entity collection and
the ownership boundary for validating that identity after datastore decoding.
It replaces the former current-release assumption that one runtime could not
contain multiple collections with the same logical name.

The complete `EntityCollectionId` selected by the runtime operation is the
authority. The former Phase 51 behavior accepted a logical collection name and
a scalar `EntityId` as compatibility input. Phase 52 rejects that input because
neither value can reconstruct or prove the exact owner.

The normative requirements are in
[Entity Collection Identity](../spec/entity-collection-identity.md).

## Complete Collection Ownership

`EntityCollection.descriptor.collectionId` owns the runtime collection. The
complete ID includes the namespace required to distinguish collections that
share one logical name.

An identity-sensitive operation resolves that owner before datastore access.
The exact ID remains authoritative through:

1. collection registration;
2. operation and UnitOfWork routing;
3. datastore decoding;
4. decoded-Entity validation; and
5. resident `EntitySpace` projection.

A generated companion's `collectionId` declares its model identity. Runtime
registration binds that declaration to one exact operational collection and
rejects contradictory ownership.

## EntityId Identity

`EntityId.value` remains the physical datastore entry key. In-memory
`EntityId` identity and equality use that canonical physical value together
with the complete owning `EntityCollectionId`.

Consequently:

- newly constructed and parsed/materialized IDs compare equal when their
  canonical values encode the same exact owner;
- identical physical keys in different collections remain distinct;
- `EntityId` values are safe map keys because collection ownership is encoded
  in their canonical values; and
- parsing a scalar ID cannot establish the complete collection namespace.

The general runtime namespace interpretation remains defined by
[ID Design](id.md).

## Phase 52 Exact Serialization

[Phase 52](../phase/phase-52.md) uses one complete canonical Entity ID String
instead of the former scalar reconstruction and decode-context restoration.

The target `EntityId` String contains its exact `EntityCollectionId` and
round-trips without runtime context. Datastore routing uses the collection
carried by the parsed ID, the datastore entry key uses the same complete
canonical String, and the stored `id` field uses that String. Primary IDs,
EntityId-valued attributes, and Association targets follow the same rule.

Phase 52 intentionally provides no old-format compatibility, legacy binding,
data migration, read repair, or mixed-version operation. The Phase 51
restoration design is historical evidence only and is not an executable
fallback.

## Exact EntitySpace Registration and Resolution

`EntitySpace` indexes collections by complete `EntityCollectionId`.

Exact lookup never falls back to another namespace or logical name. A
logical-name query may support non-identity discovery, but it neither accepts
nor canonicalizes an `EntityId` and cannot establish ownership for an
identity-sensitive operation.

Storage, revision, authorization, resident-cache, ActionCall, and UnitOfWork
paths use the parsed exact collection ID directly.

## Storage Decode Boundary

The central persistence boundary:

1. resolves the exact owner;
2. calls the ordinary store decoder exactly once with the original physical
   `Record`;
3. requires the returned Entity ID to contain that exact owner; and
4. projects only the validated Entity into `EntitySpace`.

The runtime does not rewrite the physical input, perform a second decode, or
infer adapter policy from the runtime type of the result.

## Adapter Responsibilities

### Generated and built-in codecs

Generated Entity codecs and CNCF built-in codecs decode the physical record
once and retain the decoded canonical `EntityId` without collection repair.
They preserve the physical entry identity,
timestamp, entropy, and business values. The central boundary then enforces
the exact postcondition.

The ordinary `fromRecord` path remains the business/API decoder. Store decode
uses the same one-argument canonical parser and validates exact collection
equality; it does not turn arbitrary `Record` values into persisted scalar
values.

### Custom typed codecs

A custom typed codec must decode one canonical complete identity and return it
unchanged. Its physical scalar representation cannot omit the collection
namespace.

Custom codecs receive no implicit exemption from the exact postcondition.

### Raw Record adapters

A raw `Record` adapter parses canonical ID fields explicitly. It may construct
a domain record whose Entity ID carries the exact owner, but it leaves the
supplied physical record unchanged. Raw behavior is an adapter contract rather
than an `isInstanceOf[Record]` policy in CNCF.

## Compatibility and Migration

Phase 52 has no context-aware ABI, dual decoder, legacy scalar reader, or
runtime-context restoration. A codec which loses collection identity is
unsupported and fails with deterministic regeneration guidance. Regeneration
must emit the canonical complete Entity ID; CNCF does not silently infer
ownership from a matching logical name.

The canonical complete Entity ID remains the datastore key. Exact ownership
is parsed from that String alone, so neither application-local encoding nor
identity repair is permitted.

## Boundaries

This design does not:

- retain the preceding Phase 51 incomplete scalar `EntityId` wire format;
- define general persisted scalar projection or nominal String restoration;
- rewrite a physical store record or decode it twice;
- select adapter behavior through runtime type tests;
- choose silently between same-name collections; or
- introduce application-specific collection inference.

General persisted scalar restoration is owned by Phase 51 SP-01.
