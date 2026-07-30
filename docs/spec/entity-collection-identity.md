# Entity Collection Identity

Status: normative Phase 52 static contract

## Scope

This specification defines exact collection ownership for Entity registration,
routing, persistence decoding, runtime projection, and `EntityId` equality. It
applies to generated, CNCF built-in, custom typed, and raw `Record`
persistence adapters.

The architectural rationale is in
[Entity Collection Identity](../design/entity-collection-identity.md).

## Evolution Boundary

This document specifies the Phase 52 clean-break contract. It defines a
complete canonical Entity ID String that contains the exact
`EntityCollectionId` and round-trips without external context. It does not
preserve old scalar input, context rebinding, legacy adapters, stored-data
migration, or mixed old/new operation.

## Phase 52 EID-01 Traceability Labels

The following stable labels bind EID-01 historical failing-first evidence to
this specification. They document the preceding identity-loss boundary and do
not authorize Phase 52 compatibility behavior.

- **R1**: an identity-sensitive boundary requires an exact collection owner.
- **R3**: primary and referenced Entity IDs must retain their own collection
  ownership across persistence boundaries.
- **R4**: a scalar Entity ID is not proof of a complete collection owner.
- **R5**: an Association, Blob, child-binding, or authorization consumer must
  not infer an exact owner from a scalar ID or logical collection name.

The registered examples are:

- **E1** persistence decoding under a selected owner;
- **E1b** selected-owner mismatch rejection registration;
- **E1c** generated primary and reference persistence;
- **E2** EntitySpace exact routing;
- **E3** datastore collection and entry routing;
- **E4** Association target parsing;
- **E5** Blob reference parsing;
- **E6** child-binding source identity; and
- **E7** UnitOfWork authorization target identity.

## Phase 52 EID-02 Traceability Labels

The following examples bind canonical exact serialization evidence to the
EID-02 clean replacement. They do not authorize old scalar compatibility,
collection inference, or rebinding.

- **E8** independently namespaced canonical EntityId round-trip;
- **E9** canonical EntityId rejection without synthetic ownership; and
- **E10** property-based exact canonical EntityId round-trip.

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
3. Logical-name lookup MAY support non-identity discovery only; it MUST NOT
   accept, canonicalize, or resolve an `EntityId`.
4. A logical-name discovery query MUST succeed only when exactly one registered
   collection matches.
5. An ambiguous discovery name MUST fail with reason
   `entity-collection-name-ambiguous` and deterministic exact-candidate
   evidence.
6. Storage, revision, authorization, resident-cache, ActionCall, and
   UnitOfWork paths MUST use the parsed exact identity directly.

## Storage Decode Contract

For each physical store result:

1. the selected runtime collection MUST be retained as the expected exact
   owner;
2. the ordinary `fromStoreRecord(record)` decoder MUST be invoked exactly
   once;
3. the supplied physical `Record` MUST remain unchanged;
4. the returned Entity MUST satisfy
   `persistent.id(entity).collection == expectedCollectionId`;
5. a mismatch MUST fail before resident projection; and
6. CNCF MUST NOT select decoding policy from the returned value's runtime
   type.

A failure for a codec that cannot provide the exact owner MUST use
`entity-persistence-exact-collection-required`. A codec that returns a
different declared collection MUST fail through the collection-mismatch
contract.

## Adapter Contract

### Generated and CNCF built-in codecs

Generated and built-in codecs MUST:

- decode the original store record once;
- parse and retain the complete canonical `EntityId` from that record;
- preserve the physical entry identity, timestamp, entropy, and business
  values; and
- return the Entity for the central exact postcondition.

Their ordinary `fromRecord` operations MUST remain business/API decoders.

### Custom typed codecs

A custom typed codec MUST make its one physical decode return the exact owner.
There is no context-aware override, collection restoration, or compatibility
implementation. A custom codec MUST NOT bypass the exact postcondition.

### Raw Record adapters

A raw `Record` adapter MUST parse canonical Entity ID fields explicitly. It
MAY produce a domain record with the exact Entity ID, but MUST NOT mutate or
replace the supplied physical input before decoding. CNCF MUST NOT grant raw
records an implicit runtime-type exception.

### Legacy codecs

There is no legacy context-aware compatibility codec in Phase 52. A codec
which cannot parse a canonical complete ID is unsupported input and MUST fail
deterministically. Regeneration MUST emit canonical complete IDs; it MUST NOT
restore ownership from runtime context or introduce an application-local
identity repair.

## EntityId Equality and Map-Key Contract

1. `EntityId.value` MUST remain the canonical physical datastore key.
2. In-memory equality and hashing MUST include both the canonical physical
   value and complete `EntityCollectionId`.
3. Parsed/materialized and newly constructed IDs with the same physical value
   and exact owner MUST compare equal.
4. Equal physical values owned by different collections MUST compare unequal.
5. Runtime code MUST use the parsed exact ID directly as an identity-sensitive
   map key and MUST NOT canonicalize or rebind its collection ownership.

## Required Executable Evidence

The contract is covered by:

- [EntityPersistentCollectionIdentitySpec](../../src/test/scala/org/goldenport/cncf/entity/EntityPersistentCollectionIdentitySpec.scala);
- [EntitySpaceCollectionIdentitySpec](../../src/test/scala/org/goldenport/cncf/entity/runtime/EntitySpaceCollectionIdentitySpec.scala);
- [ComponentFactoryGeneratedSchemaSpec](../../src/test/scala/org/goldenport/cncf/component/ComponentFactoryGeneratedSchemaSpec.scala);
- [SimpleEntityRevisionGenerationSpec](../../../simple-modeler/src/test/scala/org/simplemodeling/SimpleModeler/transformers/scala/SimpleEntityRevisionGenerationSpec.scala);
- [ArtSceneExactCollectionIdentitySpec](../../../../dev2026/textus-art-scene/src/test/scala/org/simplemodeling/textus/artscene/ArtSceneExactCollectionIdentitySpec.scala); and
- [EntityIdSpec](../../../../dev2026/simplemodeling-model/src/test/scala/org/simplemodeling/model/datatype/EntityIdSpec.scala).

Together the evidence MUST demonstrate exact canonical parsing,
cross-collection rejection, same-name coexistence and discovery ambiguity,
single invocation with
unchanged physical input, generated and raw adapter behavior, regeneration
failure, and physical-key-plus-collection equality.

## Non-Goals

This specification does not define general persisted scalar store projection,
nominal String restoration, alternate EntityId wire formats, or
application-local collection inference. Phase 52 rejects those inputs rather
than supporting a Phase 51 compatibility path.
