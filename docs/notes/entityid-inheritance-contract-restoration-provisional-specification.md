# EntityId Inheritance Contract Restoration - EIR-01 Accepted Authority

status: accepted authority for PHASE-74 / EIR-01; normative handoff
date: 2026-09-13
implementation-owner: simplemodeling-model; CNCF and affected generated consumers
planning-owner: [CNCF Phase 74](../phase/phase-74.md)
authority: Phase 74 base authority SHA-256 `d563221ab3083423702730d7d15ccfeef0d8bdfc17de1c55a880420e32c74999`; plan-complete guard accepted

This document supersedes its provisional status for the EIR-01 handoff. It is
the accepted source-level contract for the successor phases; executable
specifications and implementation remain successor-owned.

## Accepted EIR-01 Contract

The hierarchy is `UniversalId -> abstract EntityId -> concrete XxxId`, with
`JobDefinitionId` as the first concrete CNCF consumer. `UniversalId` remains
the general identity foundation and `EntityId` adds the entity-identity
boundary; this phase does not convert every `UniversalId` into an entity ID.

Bare `EntityId` values remain valid at generic `EntityStore`, common
persistence, generic decode, parse, restoration, and declared special bridges.
Bare `EntityId` is not an ordinary new durable issuance surface. Ordinary
issuance uses the concrete subtype or `IdGenerationContext`; the context
obtains namespace, timestamp, and fresh entropy once and never derives an ID
from a business key. The purpose-revealing exception operations are:

- `parse`: structural/canonical generic parsing without domain-subtype
  inference;
- `restore`: recovery of an already persisted identity without new entropy;
- `bridgeFromParts`: a named special bridge/recovery operation that declares
  its purpose and validates the required collection rather than rebasing a
  parsed ID.

For JobDefinition, creation issues and persists `JobDefinitionId` with
`{ id, key, ... }`; update and restart preserve it. Key lookup searches the
persisted entities and uses the matched stored ID. `a-b` and `a_b` remain
distinct keys. No key-derived ID, key-to-ID table, hash, or companion
integrity value is introduced.

## Intended Model

`UniversalId -> abstract EntityId -> XxxId` is the conceptual hierarchy.
An entity such as JobDefinition has its dedicated `JobDefinitionId`; the base
communicates entity identity rather than a universally instantiable record shape.
The concrete subclass fixes the entity's declared collection while operating
namespace and generated identity fields come from the normal generation context.

`EntityId` does not replace `UniversalId`: an entity-specific ID is still a
universal ID, but it must first pass through the entity-identity abstraction.
Thus `JobDefinitionId` extends `EntityId`, and `JobDefinitionEntity.id` has the
type `JobDefinitionId`, rather than the generic `EntityId`. This is a type
boundary, not just a naming convention: a `JobId`, Blob ID, or unrelated entity
ID must not be accepted where a JobDefinition ID is required. Not every
`UniversalId` is an entity ID, and this Phase does not force that conversion.

Restore that inheritance contract, not the exact old implementation. In
particular, retain the complete canonical identity accepted by Phase 52.
EntityId remains owned by simplemodeling-model; UniversalId remains owned by
simplemodeling-lib, which must not depend on CNCF.

## Generation and Restoration Are Different Operations

### Ordinary issuance

- Automatically obtain fresh UUID/entropy using the normal generator.
- Materialize timestamp and entropy once per issued ID; repeated access to its
  value must not regenerate parts.
- Do not derive entropy or an operating namespace from a business key merely
  to reproduce an ID during lookup.
- CNCF IdGenerationContext must support construction of the intended subtype,
  not force every caller to receive an unrelated generic concrete EntityId.
- Preserve default and deterministic-test context semantics. Fresh random IDs
  provide probabilistic uniqueness, not a mathematical no-collision guarantee.

### Special-purpose construction

- Preserve the ability to explicitly supply arbitrary permitted entropy values.
- Keep this operation distinguishable from ordinary issuance through the
  purpose-named `bridgeFromParts` contract when a bridge is admitted.
- Fixed timestamps, including EPOCH, are not invalid merely because they are
  fixed. Decide whether a use is special from its admitted semantic contract.
- Do not remove special-purpose construction as a workaround for an ordinary
  JobDefinition retrieval defect.

### Saved identity restoration

- Parse/decode the complete saved canonical value without generating new entropy.
- Preserve generic EntityStore decoding via an explicit concrete materialization
  behind the abstract base; do not instantiate an abstract class directly.
- Provide subtype-specific decoding/construction so JobDefinitionId is restored
  as that type and its expected exact collection is validated.
- Generic parse need not infer a domain subclass from an ID string; typed
  consumer metadata/factories supply the required type.
- Parsing proves syntactic/structural validity, not issuance provenance. No
  signature, trusted-issued-ID registry, or security capability is added here.

## Canonical and Source API Boundaries

Keep the accepted complete EntityCollectionId encoding, EntityId.value,
equality/hash identity, Record/JSON transport, persistence keys, references,
UnitOfWork, cache, and lock interoperability intact. A domain subclass must not
introduce an alternative string encoding or mutate identity fields on access.

Changing a final case class to an abstract base affects apply, copy, extractors,
field access, and codecs. Inventory those source APIs and migrate affected
consumers coherently; a generic concrete materialization may preserve needed
base-factory/extractor behavior. Do not add a legacy incomplete-ID parser or
blindly change the persisted format just because the Scala type shape changes.

The replacement source API must make the operation explicit. Ordinary issuance
uses `IdGenerationContext`; complete saved values use parsing/typed restoration;
and a retained special bridge uses a specifically named construction route. A
bare, universally available `EntityId(...)` construction surface must not
remain as an easy route to fabricate an entity ID from a business key. This is
the preventative boundary for future consumers; it is not a source-text ban on
special-purpose values with an admitted contract.

This restriction is about issuance, not the generic type itself. Generic
`EntityId` values remain valid at EntityStore, common persistence, and other
boundaries where the concrete entity type is unavailable. Generic decode also
remains necessary for existing records. What must not remain is convenient
generic *new issuance*: normal creation uses a concrete type such as
`JobDefinitionId.issue(...)` or IdGenerationContext. Generic recovery and
special conversion are admitted only through intention-revealing operations
such as `parse`, `restore`, or `bridgeFromParts`; their final spelling is frozen
from the affected-consumer inventory. This preserves generic interoperability
without making `EntityId(...)` an accidental identity generator.

## First Consumer: JobDefinition

JobDefinitionEntity stores a reusable JCL definition, its version/revision,
activation status, target, and source metadata. Its business key names the
definition; JobDefinitionId identifies the saved entity.

- Issue JobDefinitionId normally on creation and persist one entity record
  containing `{ id, key, ... }`. That record is the authoritative correspondence
  between the business key and its entity identity; no dedicated key-to-ID
  table, hash, or companion integrity value is required.
- Retain the persisted JobDefinitionId on update and restore that exact typed
  identity on read/restart.
- Resolve a business key through the store's key-search mechanism, obtain the
  `id` stored in that matching entity, and only then load by ID where needed.
  A store index for `key` is an optional later performance optimization, not a
  prerequisite for the correspondence or a second source of truth.
- Remove the ordinary key-to-ID regeneration dependency, not just its EPOCH
  constant. Introducing a typed JobDefinitionId with the same key-derived
  generation would leave the defect intact.
- Preserve exact keys such as `a-b` and `a_b` without collapsing them into an
  identity label. Preserve existing duplicate-key behavior; inventory atomicity
  separately rather than silently starting a new indexing subsystem.
- Accept the same saved identity after persistence/restart and through an
  execution snapshot. Do not rewrite records into a new derived ID on read.
- Keep JobId-to-EntityId bridges and other special cases separate; any needed
  adjustment must follow their existing contract and an actual affected edge.

## Executable Acceptance

Use semantic specifications and property-based coverage where appropriate:
real XxxId inheritance and generated Scala compilation; ordinary generation;
explicit special values; generic and typed canonical/Record/JSON round trips;
exact collection and equality/hash preservation; key lookup after save/restart;
distinct punctuation-bearing keys; existing duplicate rejection; and unchanged
identity on definition update.

These are behavioral/model contracts, not source-text bans on `EntityId(`,
`EPOCH`, or a particular method spelling. Refresh each modified producer's
publishLocal artifact before dependent validation and bind the exact producer
bytes/coordinates used by the consumer.

## Successor-Owned Implementation Detail

EIR-01 settles the semantic boundary and affected paths. The following remain
implementation details for the named successors and are not open contract
decisions: the historical transition commit, concrete constructor/factory
signatures, generic materialization and extractor mechanics after removal of
case-class base semantics, typed generation/decoder injection at
`IdGenerationContext` and `EntityPersistent`, generated-consumer edits, and
local artifact/validation order. No successor may weaken the accepted
hierarchy, exception boundary, canonical encoding, or JobDefinition
correspondence to resolve those details.

## Affected Consumer Map

| Repository | Current source evidence | EIR-01 role and owner |
| --- | --- | --- |
| `simplemodeling-model` | `src/main/scala/org/simplemodeling/model/datatype/EntityId.scala` | Phase 74.1 owns abstract `EntityId`, concrete subtype, generic materialization, codec, and typed restoration implementation. |
| `simplemodeling-lib` | `src/main/scala/org/goldenport/id/UniversalId.scala` | Read-only `UniversalId` foundation; no implementation change in this phase. |
| `simple-modeler` | `src/main/scala/org/simplemodeling/model/MObject.scala` | Read-only generated `EntityId` type consumer; Phase 74.2 inventories generated-entity-specific ID impact before any generation change. |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/context/IdGenerationContext.scala`; `src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala`; and the ten sites below | Phase 74.2 owns context construction, all listed direct-construction migration, `JobDefinitionId` adoption, JobDefinition lookup, and consumer specifications. |

The live JobControlComponent path is under `component/builtin/jobcontrol`; it
contains no direct `EntityId(...)` construction site and is listed only as the
JobDefinition consumer surface.

## Exact Production Direct-Construction Matrix (10 Sites)

The following is exhaustive for the current CNCF production source inventory.
Line numbers identify the inspected current tree and are informational; the
path and enclosing method are the stable handoff identity.

| # | Current source path and site | Classification | Successor action |
| ---: | --- | --- | --- |
| 1 | `src/main/scala/org/goldenport/cncf/blob/BlobProjection.scala:102` (`_blob_entity_id`) | saved-value restoration / special bridge | Phase 74.2 retains a named bridge that checks the parsed collection against Blob expectations. |
| 2 | `src/main/scala/org/goldenport/cncf/blob/BlobProjection.scala:133` (`_media_entity_id`) | saved-value restoration / special bridge | Phase 74.2 retains a named bridge that checks the parsed collection against Media expectations. |
| 3 | `src/main/scala/org/goldenport/cncf/context/IdGenerationContext.scala:120` (`Context._entity_id`) | ordinary issuance | Phase 74.1/74.2 make context construction produce the requested concrete subtype or declared factory. |
| 4 | `src/main/scala/org/goldenport/cncf/job/DurableJobStore.scala:355` (`DurableJobStoreEntity.entityId`) | named durable JobId bridge | Preserve durable semantic identity and migrate only to a purpose-named bridge when the model contract requires it. |
| 5 | `src/main/scala/org/goldenport/cncf/job/JobEntity.scala:107` (`JobDefinitionEntity._entity_id`) | erroneous ordinary JobDefinition key-derived issuance | Phase 74.2 removes this creation/lookup route and issues `JobDefinitionId`. |
| 6 | `src/main/scala/org/goldenport/cncf/job/JobEntity.scala:302` (`JobEntity.entityId`, parsed branch) | JobId recovery / bridge | Keep only as a separately justified purpose-named recovery/bridge path. |
| 7 | `src/main/scala/org/goldenport/cncf/job/JobEntity.scala:310` (`JobEntity.entityId`, fallback branch) | JobId recovery / bridge | Keep only as a separately justified purpose-named recovery/bridge path. |
| 8 | `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:3254` (`_admin_entity_record`) | ordinary framework issuance | Phase 74.2 routes it through `IdGenerationContext` or an explicitly declared generic issuance policy. |
| 9 | `src/main/scala/org/goldenport/cncf/component/builtin/blob/BlobComponent.scala:1484` (`_association_delete`) | association special bridge | Phase 74.2 keeps a named association bridge with canonical collection validation. |
| 10 | `src/main/scala/org/goldenport/cncf/observability/DiagnosticPayloadExternalization.scala:544` (`_blob_entity_id`) | ordinary diagnostic-payload issuance | Phase 74.2 adopts concrete/context issuance or an explicitly typed payload-ID policy. |

This matrix classifies explicit parts by operation semantics; it does not
infer a defect merely from explicit identity parts. Generic EntityStore and
generic existing-record decode remain valid boundaries.

## EIR-01 Acceptance and Deferred Evidence

The four handoff documents—this accepted authority, [Phase 74](../phase/phase-74.md),
its [checklist](../phase/phase-74-checklist.md), and the [journal](../journal/2026/09/2026-09-13-entityid-inheritance-contract-restoration.md)—must keep this contract and the ten-site matrix consistent. This slice
requires only documentation reconciliation and direct-link consistency.
Successor evidence remains deferred: model compilation and typed
inheritance/codec/parse/restore/bridge specifications in Phase 74.1; CNCF
JobDefinition lifecycle, punctuation-key distinction, direct-site migration,
and producer/consumer specifications in Phase 74.2; and the aggregate full
suite and release closure in Phase 74.3.

No shared skill/rule change is proposed. This is concrete model/API recovery
with a bounded first consumer, not a universal new approval gate.
