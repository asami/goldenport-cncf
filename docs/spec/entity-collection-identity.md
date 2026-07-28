# Entity Collection Identity

Status: normative static contract

## Scope

This specification defines exact collection ownership for Entity registration,
routing, persistence decoding, runtime projection, and `EntityId` equality. It
applies to generated, CNCF built-in, custom typed, raw `Record`, and legacy
persistence adapters.

The architectural rationale is in
[Entity Collection Identity](../design/entity-collection-identity.md).

## Collection Ownership

1. `EntityCollection.descriptor.collectionId` MUST be the complete runtime
   owner of the collection.
2. Identity-sensitive operations MUST resolve that exact owner before
   datastore access.
3. A decoded Entity's `EntityId.collection` MUST equal the exact owner before
   the Entity enters resident runtime state.
4. A scalar `EntityId` or logical collection name MUST NOT be treated as proof
   of the complete owner.
5. Runtime registration MUST reject duplicate exact collection IDs and
   contradictory declared ownership.

## EntitySpace Resolution

1. `EntitySpace` MUST index registered collections by complete
   `EntityCollectionId`.
2. Exact lookup MUST NOT fall back to a different namespace.
3. Logical-name lookup MAY remain as an ingress compatibility operation.
4. A logical-name lookup MUST succeed only when exactly one registered
   collection matches.
5. An ambiguous name MUST fail with reason
   `entity-collection-name-ambiguous` and deterministic exact-candidate
   evidence.
6. Canonicalization MUST retain an exact registered ID before considering
   unique-name compatibility.
7. Storage, revision, authorization, resident-cache, ActionCall, and
   UnitOfWork paths MUST use exact identity after ingress canonicalization.

## Storage Decode Contract

The runtime MUST supply:

```scala
EntityStoreDecodeContext(
  owningCollectionId: EntityCollectionId
)
```

to the context-aware operation:

```scala
fromStoreRecord(
  context: EntityStoreDecodeContext,
  record: Record
): Consequence[E]
```

For each physical store result:

1. the owning context MUST come from the selected runtime collection;
2. the context-aware decoder MUST be invoked exactly once;
3. the supplied physical `Record` MUST remain unchanged;
4. the returned Entity MUST satisfy
   `persistent.id(entity).collection == context.owningCollectionId`;
5. a mismatch MUST fail before resident projection; and
6. CNCF MUST NOT select decoding policy from the returned value's runtime
   type.

A failure for a legacy codec that cannot restore the exact owner MUST use
`entity-persistence-exact-collection-required`. A codec that returns a
different declared collection MUST fail through the collection-mismatch
contract.

## Adapter Contract

### Generated and CNCF built-in codecs

Generated and built-in codecs MUST:

- decode the original store record once;
- restore only the decoded `EntityId.collection` from the exact context;
- preserve the physical entry identity, timestamp, entropy, and business
  values; and
- return the Entity for the central exact postcondition.

Their ordinary `fromRecord` operations MUST remain business/API decoders.

### Custom typed codecs

A custom typed codec MAY override the context-aware method to restore exact
ownership. When it uses the compatibility implementation, its one physical
decode MUST already return the exact owner. A custom codec MUST NOT bypass the
exact postcondition.

### Raw Record adapters

A raw `Record` adapter MUST implement its context-aware behavior explicitly.
It MAY produce a domain record with the exact Entity ID, but MUST NOT mutate or
replace the supplied physical input before decoding. CNCF MUST NOT grant raw
records an implicit runtime-type exception.

### Legacy codecs

A legacy codec without usable context-aware behavior MAY be invoked through
the one-argument compatibility method exactly once. Its result MUST be
accepted only when it already carries the exact owner. Otherwise CNCF MUST
fail with deterministic regeneration guidance.

Regenerated codecs SHOULD restore existing scalar keys from the runtime-owned
context. CI-01 MUST NOT require a persisted-key rewrite, new scalar wire
format, Base64/prefix encoding, or application-local identity repair.

## EntityId Equality and Map-Key Contract

1. `EntityId.value` MUST remain the canonical physical datastore key.
2. In-memory equality and hashing MUST include both the canonical physical
   value and complete `EntityCollectionId`.
3. Parsed/materialized and newly constructed IDs with the same physical value
   and restored exact owner MUST compare equal.
4. Equal physical values owned by different collections MUST compare unequal.
5. Runtime code SHOULD canonicalize collection ownership before using an
   `EntityId` as an identity-sensitive map key.

## Required Executable Evidence

The contract is covered by:

- [EntityPersistentCollectionIdentitySpec](../../src/test/scala/org/goldenport/cncf/entity/EntityPersistentCollectionIdentitySpec.scala);
- [EntitySpaceCollectionIdentitySpec](../../src/test/scala/org/goldenport/cncf/entity/runtime/EntitySpaceCollectionIdentitySpec.scala);
- [ComponentFactoryGeneratedSchemaSpec](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedSchemaSpec.scala);
- [SimpleEntityRevisionGenerationSpec](../../../simple-modeler/src/test/scala/org/simplemodeling/SimpleModeler/transformers/scala/SimpleEntityRevisionGenerationSpec.scala);
- [ArtSceneExactCollectionIdentitySpec](../../../../dev2026/textus-art-scene/src/test/scala/org/simplemodeling/textus/artscene/ArtSceneExactCollectionIdentitySpec.scala); and
- [EntityIdSpec](../../../../dev2026/simplemodeling-model/src/test/scala/org/simplemodeling/model/datatype/EntityIdSpec.scala).

Together the evidence MUST demonstrate exact restoration, cross-collection
rejection, same-name coexistence and ambiguity, single invocation with
unchanged physical input, generated and raw adapter behavior, regeneration
failure, and physical-key-plus-collection equality.

## Non-Goals

This specification does not define general persisted scalar store projection,
nominal String restoration, a new EntityId wire format, or application-local
collection inference. Those scalar-projection requirements belong to Phase 51
SP-01.
