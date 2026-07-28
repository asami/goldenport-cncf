# Phase 51 CI-01 Exact Collection Identity Contract

Status: REVIEW_FIX complete; RE_REVIEW pending.

Authority: historical, non-normative implementation record. The authoritative
contract is the
[Entity Collection Identity design](../design/entity-collection-identity.md)
and its
[normative specification](../spec/entity-collection-identity.md).

## Purpose

CI-01 replaces the Phase 50 logical-name-only collection closure with an exact
runtime ownership contract.

A scalar `EntityId` is an entry identity. Its serialized form does not carry
the independent `major` and `minor` values of `EntityCollectionId`, so parsing
that scalar cannot reconstruct the complete owning collection. The scalar ID,
the decoded Entity, and the logical collection name therefore cannot be the
authority for storage ownership.

The exact storage owner is the `EntityCollection.descriptor.collectionId`
selected by the runtime operation. That complete `EntityCollectionId` must
survive registration, operation routing, storage decoding, and resident
EntitySpace projection.

## Promoted Contract Record

### Storage owner

- `EntityCollection.descriptor.collectionId` is the complete runtime owner.
- An identity-sensitive UnitOfWork operation carries or resolves that exact
  owner before datastore access.
- `EntityId.collection` must equal the owner after domain decoding, but its
  scalar representation is not used to infer the owner's namespace.
- A generated companion's `collectionId` is the declared model identity. The
  runtime registration must bind it to one exact operational collection and
  reject contradictory ownership.

### Decode context

CNCF adds one provider-neutral storage decode context:

```scala
final case class EntityStoreDecodeContext(
  owningCollectionId: EntityCollectionId
)
```

`EntityPersistent` gains a context-aware storage method:

```scala
def fromStoreRecord(
  context: EntityStoreDecodeContext,
  record: Record
): Consequence[E]
```

The legacy one-argument `fromStoreRecord(record)` remains the physical decoder
compatibility method. The default context-aware implementation delegates to it
once. CNCF then validates exact equality between
`persistent.id(entity).collection` and `context.owningCollectionId`.

The runtime:

1. resolves the exact owner;
2. invokes the context-aware method exactly once with the original physical
   `Record`;
3. validates the returned Entity against the exact owner; and
4. projects only the validated Entity into EntitySpace.

It must not rewrite the physical input, decode twice, or select policy from the
runtime type of the returned value.

### Generated Entity codecs

SimpleModeler-generated `EntityPersistent` implementations override the
context-aware method. They:

- decode the original store `Record` through the generated store decoder;
- replace only the decoded `EntityId.collection` with
  `context.owningCollectionId`;
- preserve the EntityId entry identity, timestamp, entropy, and business
  fields; and
- return one Entity for CNCF's exact postcondition check.

The generated codec does not mutate or retry the physical Record. Its ordinary
`fromRecord` remains the business/API decoder. Persisted scalar value
projection other than collection identity remains SP-01 work.

### Custom typed codecs

A custom typed `EntityPersistent` may override the context-aware method when
its physical scalar ID cannot retain the complete collection namespace.
Otherwise the default method decodes once and must already return the exact
owner.

Custom codecs do not receive an implicit raw-Record exemption. A codec that
returns a different exact collection fails with the same structured contract
error as generated code.

### Raw Record adapters

A raw `Record` persistence adapter must explicitly implement the
context-aware method. It may return a new domain Record whose ID value carries
the exact owner, but it must leave the physical input unchanged. Raw behavior
is an explicit adapter contract, not an `isInstanceOf[Record]` branch in CNCF.

## EntitySpace Identity

EntitySpace must index registered collections by complete
`EntityCollectionId`, not by logical name alone.

- Exact lookup uses the complete ID and never falls back to a different
  namespace.
- A logical-name lookup is an ingress compatibility operation only.
- Name lookup succeeds only when exactly one registered collection has that
  name.
- No match returns the existing not-found result.
- More than one match returns a structured
  `entity-collection-name-ambiguous` failure containing the name and candidate
  exact IDs.
- Storage, UnitOfWork, revision, authorization, and resident-cache paths use
  exact lookup after ingress resolution.

This permits two collections with the same logical name to coexist while
making accidental cross-collection access deterministic.

## Compatibility and Migration

### Older generated artifacts

An older binary without the context-aware method is adapted through the legacy
one-argument method exactly once. It is accepted only when the decoded Entity
already carries the exact owner.

If the scalar round-trip lost the collection namespace, CNCF returns a typed
`entity-persistence-exact-collection-required` diagnostic. The recovery action
is to regenerate the CAR with the CI-01 generator. CNCF does not silently
restore ownership by logical name.

Reflective adaptation detects the context-aware ABI by method signature. It
does not infer behavior from the Entity's runtime class.

### Existing scalar EntityId values

Existing scalar IDs remain valid datastore entry keys. Newly generated codecs
restore their exact collection from the runtime-owned decode context, so no
stored-ID rewrite or application migration is required.

An old codec that cannot consume the context fails explicitly. No Base64,
prefix encoding, second copy of the ID, or application-local repair is added.

### Name-only callers

Existing name-only ingress may continue only through the unique-name resolver.
It must fail on ambiguity. New generated and runtime-internal code uses exact
identity.

## Implementation Units

### CNCF

- Add `EntityStoreDecodeContext` and the context-aware
  `EntityPersistent.fromStoreRecord`.
- Replace logical-name validation in the central store decoder with exact
  equality.
- Pass one exact context through every store/revision/resident projection
  decode path.
- Key EntitySpace registration by complete collection ID and add a structured
  unique-name resolver.
- Remove name-first fallback from identity-sensitive UnitOfWork and EntitySpace
  paths.
- Preserve the context-aware method through generated/reflective persistence
  bridges and store-field-mapping decorators.
- Emit stable expected/actual/owner diagnostics for missing context,
  ambiguity, and exact mismatch.

Expected production files include:

- `entity/EntityPersistent.scala`;
- `entity/runtime/EntitySpace.scala`;
- `entity/EntityStore.scala`;
- `unitofwork/UnitOfWorkInterpreter.scala`; and
- `component/ComponentFactory.scala` only where persistence bridge forwarding
  is required.

### SimpleModeler

- Generate the context-aware Entity store decoder.
- Rebind only the decoded EntityId collection to the supplied exact owner.
- Keep `fromRecord` and unrelated storage projection unchanged.

Expected production file:

- `generator/scala/Scala3ClassGeneratorBase.scala`.

### Cozy

- Verify generated source contains the context-aware method and compiles
  against CNCF.
- Keep Cozy as the generation/integration owner; do not duplicate CNCF
  runtime resolution policy.

### ArtScene

- Regenerate representative Entity code.
- Prove Facility and Exhibition operations preserve distinct exact collection
  identities without repair outside an explicitly owned persistence adapter.
- Require ArtScene's handwritten raw `Record` adapter to consume
  `EntityStoreDecodeContext`, retain a canonical `EntityId` when one is already
  present, parse only a physical scalar ID, reject missing or malformed IDs,
  and restore only the collection owner on its returned domain record.
- Keep all ArtScene code outside that adapter free of collection namespace
  inference or rewriting.

## Failing-First Executable Specification

### CNCF

Extend `EntityPersistentCollectionIdentitySpec` and add focused EntitySpace /
UnitOfWork coverage for:

- scalar round-trip restored to the exact supplied owner;
- exact postcondition rejection for a different namespace with the same name;
- cross-logical-collection rejection;
- one custom codec invocation with unchanged physical input;
- one explicit raw Record adapter with unchanged physical input and exact
  returned identity;
- two same-name collections resolved independently by exact ID;
- ambiguous name-only lookup rejected with both exact candidates; and
- legacy codec failure with regeneration guidance when exact identity cannot be
  recovered.

### SimpleModeler and Cozy

- Verify generated Entity source implements the context-aware method.
- Compile generated create/read/update/persistent code against Scala 3.3.8 and
  the current CNCF API.
- Execute a generated Entity decode with an owner namespace different from the
  scalar EntityId namespace.

### ArtScene

- Execute Facility and Exhibition create/load/update paths through the
  canonical EntityStore boundary.
- Verify the resident EntitySpace and returned Entity retain each exact owner.
- Verify no handwritten identity repair is introduced.

## Validation

Focused validation is serialized through the CNCF SBT runner:

- CNCF collection identity, EntitySpace, EntityStore query, and UnitOfWork
  specifications;
- SimpleModeler generation specifications;
- Cozy generated-code and modeler specifications;
- ArtScene Facility/Exhibition identity-focused specifications; and
- normal ArtScene CAR lint.

Run `git diff --check` in every affected repository. Full suites remain the
Phase 51 final release gate.

## Implementation Evidence

The CI-01 core implementation now:

- passes `EntityStoreDecodeContext` through the central
  `EntityPersistent._decode_store_record` boundary exactly once;
- validates the decoded Entity against the complete owning
  `EntityCollectionId`;
- preserves the context-aware ABI through CNCF's store-field-mapping
  persistence decorator;
- gives generated Entity codecs an explicit exact-owner restoration path;
- applies the same explicit path to CNCF built-in Tag, Blob, Media,
  Association, and Job codecs;
- keys `EntitySpace` by complete collection identity and rejects ambiguous
  logical-name lookup with structured candidate evidence; and
- resolves identity-sensitive ActionCall and UnitOfWork paths by exact
  collection first, with unique logical-name compatibility second.

Focused CNCF validation passed all 20 scenarios across the two
collection-identity suites, ComponentFactory runtime-plan activation, and
UnitOfWork resource lifecycle. The SimpleModeler generation specification
passed two scenarios and confirms that the generated Entity persistence codec
contains the context-aware method and exact-owner restoration call. Current
diff checks pass in CNCF, SimpleModeler, Cozy, sbt-cozy, and ArtScene.

REVIEW_FIX additionally preserves the registered plan entity name when its
physical collection name differs, and defines `EntityId` equality as canonical
physical identity plus the complete owning collection namespace. This keeps
newly constructed and parsed/materialized IDs equal once the exact owner is
restored while continuing to distinguish same-key entities in different
collections. The resulting regression gate passed all 351 CNCF tests across
six suites and all six simplemodeling-model `EntityIdSpec` scenarios, with
both repositories passing `Test/compile`. The model JAR was injected only into
the validation classpath; no SNAPSHOT was published.

The normal development dependency path was then exercised with SNAPSHOT
coordinates, without overwriting a release artifact:

- `org.simplemodeling:simplemodeler_2.12:1.1.25-SNAPSHOT`;
- `org.goldenport:goldenport-cncf_3:0.5.2-SNAPSHOT`;
- Cozy `0.3.1-SNAPSHOT`; and
- ArtScene compile/runtime metadata targeting CNCF `0.5.2-SNAPSHOT`.

Cozy generation validation passed all 27 scenarios. ArtScene regenerated 155
Scala sources, and the generated `Facility` and `Exhibition` codecs contain
the context-aware method and exact-owner restoration call. ArtScene main/test
compilation and all four focused scenarios passed, including an executable
Facility/Exhibition datastore round-trip that verifies each decoded Entity
retains its complete declared collection identity. ArtScene's raw `Record`
adapter uses the explicit CNCF context contract; no scalar-ID parser workaround
or application-specific collection inference was added.

The canonical-document promotion gate passed all 16 scenarios across
`EntityPersistentCollectionIdentitySpec`,
`EntitySpaceCollectionIdentitySpec`, and
`ComponentFactoryGeneratedSchemaSpec`, followed by successful
`Test/compile`. The focused document/link/terminology checks and all six
repository diff checks passed. Validation preserved every tracked/untracked
repository identity and the empty staged state; no SNAPSHOT was published.

The canonical-contract REVIEW_FIX makes the selected exact
`EntityCollection` authoritative for compatible scalar references, validates
the complete collection owner returned by custom store providers, labels this
note as historical rather than normative, and exposes executable evidence as
navigable specification links. The serialized validation gate passed all 27
scenarios across `EntityPersistentCollectionIdentitySpec`,
`EntitySpaceCollectionIdentitySpec`, `EntityDetachedRevisionSpec`, and
`ComponentFactoryGeneratedSchemaSpec`, followed by successful
`Test/compile`. The four repaired source/spec identities were unchanged by
validation, and no artifact was published.

The following accumulator RE_REVIEW found only compliance debt: four
`EntityIdSpec` decoding routes and multiple repository scenarios lacked
distributed Given/When/Then boundaries, four large CNCF specifications lacked
responsibility-level `which` sections, five current-month headers retained
redundant history lines, and two historical Cozy counts were stale.
REVIEW_FIX completes all six `EntityIdSpec` boundaries, all 68
`ComponentRepositoryCarSpec` boundaries, groups the affected 24-, 68-, 10-,
and 10-scenario specifications into three, four, two, and two sections,
compresses the five headers, and reconciles the Cozy totals to 11 and 32.
Serialized focused validation passed all six simplemodeling-model scenarios
and all 112 CNCF scenarios across the four repaired specifications, followed
by successful `Test/compile` in both repositories. These changes do not alter
the exact collection-identity behavior and still require a fresh independent
RE_REVIEW.

The next fresh accumulator RE_REVIEW found three remaining
executable-specification presentation issues rather than a collection-identity
defect. REVIEW_FIX moves standard SAR subsystem resolution after its `When`
boundary and divides the 14-scenario Cozy scaffold specification and
27-scenario Scala-generation specification into three and four
responsibility-level sections. Serialized validation passed all 41 Cozy
scenarios and all 68 CNCF repository scenarios plus `Test/compile` in both
repositories. The behavioral contract is unchanged and a fresh independent
RE_REVIEW remains required.

## Stop Conditions

Stop and return to design review if implementation would require:

- changing the persisted scalar `EntityId` wire format;
- treating arbitrary persisted `Record` values as scalar strings;
- a second store decode or physical Record rewrite;
- runtime `isInstanceOf` selection for generated/custom/raw codec policy;
- silent name-only selection when more than one collection matches; or
- an application-local ArtScene identity workaround.

Those outcomes contradict CI-01 or belong to SP-01.

## Deferred Work

- General persisted scalar restoration is SP-01.
- EntityId wire-format redesign is not part of Phase 51.
- Full migration tooling is unnecessary when regeneration plus exact context
  can read existing scalar keys.
- The verified contract is promoted to
  [Entity Collection Identity design](../design/entity-collection-identity.md)
  and its
  [normative specification](../spec/entity-collection-identity.md).
