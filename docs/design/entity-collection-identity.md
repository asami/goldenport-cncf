# Entity Collection Identity

Status: decided

## Purpose

This design defines the complete runtime identity of an Entity collection and
the ownership boundary for restoring that identity after datastore decoding.
It replaces the former current-release assumption that one runtime could not
contain multiple collections with the same logical name.

The complete `EntityCollectionId` selected by the runtime operation is the
authority. A logical collection name and a scalar `EntityId` are compatibility
inputs; neither is sufficient to reconstruct or prove the exact owner.

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

- newly constructed and parsed/materialized IDs compare equal after the exact
  owner is restored;
- identical physical keys in different collections remain distinct;
- `EntityId` values are safe map keys only after collection ownership has been
  canonicalized; and
- parsing a scalar ID cannot establish the complete collection namespace.

The general runtime namespace interpretation remains defined by
[ID Design](id.md).

## Exact EntitySpace Registration and Resolution

`EntitySpace` indexes collections by complete `EntityCollectionId`.

Exact lookup never falls back to another namespace. Logical-name lookup is an
ingress compatibility operation and succeeds only when one registered
collection matches. No match returns the normal not-found result. More than
one match returns a structured `entity-collection-name-ambiguous` failure with
the exact candidates.

Canonicalization therefore applies this order:

1. retain an exact registered collection ID;
2. otherwise resolve a unique logical-name match; and
3. fail on missing or ambiguous ownership.

Storage, revision, authorization, resident-cache, ActionCall, and UnitOfWork
paths use exact lookup after ingress canonicalization.

## Storage Decode Boundary

The selected `EntityCollection` descriptor supplies the exact decode context:

```scala
final case class EntityStoreDecodeContext(
  owningCollectionId: EntityCollectionId
)
```

`EntityPersistent` exposes one context-aware storage operation:

```scala
def fromStoreRecord(
  context: EntityStoreDecodeContext,
  record: Record
): Consequence[E]
```

The central persistence boundary:

1. resolves the exact owner;
2. calls the context-aware decoder exactly once with the original physical
   `Record`;
3. requires the returned Entity ID to contain that exact owner; and
4. projects only the validated Entity into `EntitySpace`.

The runtime does not rewrite the physical input, perform a second decode, or
infer adapter policy from the runtime type of the result.

## Adapter Responsibilities

### Generated and built-in codecs

Generated Entity codecs and CNCF built-in codecs decode the physical record
once and restore only the decoded `EntityId.collection` from
`EntityStoreDecodeContext`. They preserve the physical entry identity,
timestamp, entropy, and business values. The central boundary then enforces
the exact postcondition.

The ordinary `fromRecord` path remains the business/API decoder. The exact
collection restoration path does not turn arbitrary `Record` values into
persisted scalar values.

### Custom typed codecs

A custom typed codec may override the context-aware method when its physical
scalar representation cannot retain the complete collection namespace.
Otherwise the compatibility implementation decodes once and accepts the
result only if it already carries the exact owner.

Custom codecs receive no implicit exemption from the exact postcondition.

### Raw Record adapters

A raw `Record` adapter implements the context-aware method explicitly. It may
construct a domain record whose Entity ID carries the exact owner, but it
leaves the supplied physical record unchanged. Raw behavior is an adapter
contract rather than an `isInstanceOf[Record]` policy in CNCF.

## Compatibility and Migration

The legacy one-argument `fromStoreRecord(record)` remains a physical-decoder
compatibility method. A bridge that lacks the context-aware ABI invokes that
legacy method once. The result is accepted only when it already carries the
exact owner.

When an older generated codec loses the collection namespace during scalar
round-trip, CNCF returns
`entity-persistence-exact-collection-required` with regeneration guidance.
Regenerating the CAR supplies the context-aware codec. CNCF does not silently
infer ownership from a matching logical name.

Existing scalar Entity IDs remain valid datastore keys. Exact ownership is
restored from runtime context, so CI-01 requires neither stored-key rewriting
nor an application-local encoding or repair.

## Boundaries

This design does not:

- change the persisted scalar `EntityId` wire format;
- define general persisted scalar projection or nominal String restoration;
- rewrite a physical store record or decode it twice;
- select adapter behavior through runtime type tests;
- choose silently between same-name collections; or
- introduce application-specific collection inference.

General persisted scalar restoration is owned by Phase 51 SP-01.
