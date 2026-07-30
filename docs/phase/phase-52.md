# Phase 52 - Exact Entity ID Serialization and Collection Identity

status=in_progress
planned_at=2026-07-29
depends_on=[Phase 51](phase-51.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 52 Checklist](phase-52-checklist.md)

## Purpose

Make Entity identity a complete, lossless, independently serializable value.

An Entity ID written as a String must be restored as the same Entity ID from
that String alone:

```text
EntityId.parse(EntityId.serialize(id)) == id
```

The historical Phase 51 model did not satisfy this law. Its `EntityId.value`
omitted the independent `major` and `minor` of the exact `EntityCollectionId`,
so parsing constructed a synthetic collection from the Entity ID operational
namespace and runtime decode supplied a selected collection as context. Phase
52 is replacing that compensation model with complete serialization; EID-02
and EID-03 have established the canonical format, while the remaining phase
work removes every consumer-side reconstruction.

Phase 52 is an intentional clean break. It provides no compatibility parser,
legacy rebinding, stored-data migration, read repair, or mixed old/new
operation.

## Dependency

Phase 52 begins after Phase 51 closes.

Phase 51 remains the historical record of the preceding implementation. Its
context-aware restoration model is not a compatibility requirement for Phase
52 and may be removed wherever complete serialization makes it unnecessary.

## Selected Direction

- `EntityCollectionId` has one canonical, lossless String representation.
- `EntityId` contains one exact `EntityCollectionId`.
- `EntityId` has one canonical, lossless String representation containing the
  complete collection identity.
- `EntityId.value` is the complete canonical Entity ID, not an incomplete
  datastore-local scalar.
- `EntityId.parse` is a pure inverse of canonical serialization. It does not
  consult `EntitySpace`, runtime registration, a datastore, application
  metadata, or decode context.
- A parsed Entity ID is exact. There is no unresolved or synthetic
  `EntityId`.
- Datastore collection routing uses the exact collection carried by the parsed
  Entity ID.
- Datastore entry identity uses the canonical complete Entity ID string.
- The stored Entity `id` field uses the same canonical complete String.
- EntityId-valued attributes and Association targets use the same canonical
  complete String.
- Runtime code does not map an Entity ID to a collection by copying entry
  `major/minor`, matching only the logical collection name, or scanning
  registered collections.
- Equality, hashing, caches, UnitOfWork maps, aggregate locks, revision,
  authorization, and observability use the exact parsed Entity ID directly.
- Application code treats the canonical String as opaque and does not branch
  on its structure.

## Identity Model

The target model is:

```text
EntityCollectionId
  = complete collection namespace and logical collection name

EntityId
  = exact EntityCollectionId
  + Entity-local identity fields
```

The canonical Entity ID encoding includes both parts. Two IDs with the same
Entity-local fields but different exact collections are different identities
and have different canonical Strings:

```text
EntityId(collection=A, local=K) != EntityId(collection=B, local=K)
serialize(EntityId(collection=A, local=K))
  != serialize(EntityId(collection=B, local=K))
```

No public or persistence API exposes an incomplete String as an `EntityId`.

## Canonical Serialization Contract

The accepted encoding must be:

- deterministic and versioned;
- lossless for every admitted `EntityCollectionId` and `EntityId`;
- safe for Record, JSON, HTTP path/query, form, CLI, logging, and datastore
  transport under the applicable escaping rules;
- unambiguous without guessing label boundaries;
- parseable without external context; and
- extensible only through an explicit version rule.

The exact syntax is fixed by EID-02 through failing-first property evidence.
It is one CNCF/SimpleModeling contract. Applications and CARs do not add
prefixes, Base64 wrappers, alternate delimiters, or collection inference.

`toString` remains a debugging concern. Persistence and public transport use
the explicit canonical codec/value contract.

## Persistence Contract

For every write:

```text
datastore collection = id.collection
datastore entry key  = id.value
record["id"]         = id.value
```

`id.value` is the complete canonical Entity ID String in both positions. The
datastore collection and the collection encoded in the Entity ID must agree.
A mismatch fails before a datastore or resident-state side effect.

For every read:

```text
record["id"] -> EntityId.parse -> exact EntityId
```

The decoded Entity ID must agree with the datastore collection used for the
read. Validation does not repair, rebind, normalize, or replace the ID.

EntityId-valued attributes are decoded by the same pure parser and retain
their own exact target collections. The containing Entity's collection is
irrelevant to those references.

## Clean-Break Policy

- Existing incomplete scalar Entity IDs are unsupported input.
- Existing records containing the old incomplete form are unsupported.
- No automatic migration or dual-format reader is implemented.
- No `LegacyEntityId`, unresolved-ID, name-only fallback, or context-binding
  API is introduced.
- No CAR compatibility is required for Phase 52 closure.
- Core CNCF/SimpleModeling repositories adopt the new contract together.
- A CAR that later exposes an incompatibility is corrected in that CAR when
  the problem is observed.
- CAR-local corrections must adopt the canonical contract and must not
  reintroduce local parsing, rebinding, or old-format compatibility.

## Scope

- Define the exact `EntityCollectionId` and `EntityId` canonical encodings.
- Update `EntityId.value`, parsing, construction, equality, hashing, and
  rendering.
- Remove synthetic `EntityCollectionId(parts.major, parts.minor, name)`
  construction.
- Remove `entityIdInCollectionNamespace` behavior whose purpose is to make an
  incomplete scalar appear self-describing.
- Update CNCF datastore collection and entry-key construction.
- Update generated primary Entity ID persistence.
- Update generated EntityId-valued attribute persistence.
- Remove collection-identity restoration from normal
  `EntityStoreDecodeContext` processing.
- Remove name-only Entity ID canonicalization from identity-sensitive runtime
  paths.
- Repair Admin Entity delete, Association target binding/validation,
  `EntityLoader`, Blob, child-binding, and other direct storage paths.
- Make Association validation return and persist the parsed exact target ID.
- Update UnitOfWork, resident cache, snapshots, locks, revision,
  authorization, and diagnostics to use exact parsed identity.
- Update core generated fixtures and executable specifications.
- Promote the verified contract to canonical design/specification.

## Boundaries

- Phase 52 does not provide backward compatibility for old Entity ID Strings
  or stored records.
- Phase 52 does not migrate or rewrite existing application/CAR data.
- Phase 52 does not require all existing CARs to compile or pass before phase
  closure.
- Phase 52 does not derive collection ownership from Entity-local fields,
  collection name, or runtime registration.
- Phase 52 does not silently choose among same-name collections.
- Phase 52 does not make application logic interpret canonical ID structure.
- Phase 52 does not retain context rebinding as a normal persistence feature.
- Phase 52 does not make raw `Record` or custom codecs exempt from exact
  parsing and validation.
- Phase 52 does not redesign unrelated CanonicalId, JobId, EventId, security
  principal ID, or external provider identity contracts.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| EID-01 | Inventory and failing-first contract | Current lossy serialization, synthetic collection reconstruction, context repair, and affected core paths are fixed as failing evidence. | done |
| EID-02 | Canonical exact serialization | `EntityCollectionId` and `EntityId` use one versioned lossless encoding and satisfy round-trip and non-collision laws. | done |
| EID-03 | Model and generated-code adoption | SimpleModeling types and generated primary/reference codecs persist and parse only complete exact IDs. | done |
| EID-04 | CNCF persistence and routing simplification | Store addressing, decoding, UnitOfWork, direct APIs, and resident projection use exact IDs without rebinding or name-only resolution. | done |
| EID-05 | Identity consumers and built-ins | Admin, Association, loaders, Blob, child binding, caches, locks, revision, authorization, and diagnostics use exact identity. Admin detail/edit canonical-route revalidation passed. | done |
| EID-06 | Core validation and canonical closure | Core affected repositories pass and design/specification replace the Phase 51 compensation model. | in_progress |

## Acceptance

- `EntityCollectionId.parse(EntityCollectionId.serialize(id)) == id`.
- `EntityId.parse(EntityId.serialize(id)) == id`.
- Parsing requires no external collection context.
- `EntityId.parse` never fabricates a collection from Entity-local
  `major/minor` or logical name.
- The same Entity-local identity in two exact collections produces two
  unequal IDs and two different canonical Strings.
- New datastore records persist a complete canonical primary Entity ID.
- Datastore entry keys use the same complete canonical Entity ID.
- The datastore collection and parsed `id.collection` must match exactly.
- EntityId-valued attributes retain their exact target collections through
  persistence round-trip.
- Association target IDs retain exact target collections without scanning or
  name-only matching.
- Direct loaders and storage APIs route from exact parsed identity.
- UnitOfWork maps, resident caches, aggregate locks, revision keys, and
  authorization resources use exact Entity IDs.
- Old incomplete scalar IDs fail deterministically as unsupported input.
- No compatibility, migration, read-repair, or dual-format path is present.
- `simplemodeling-model`, `simple-modeler`, CNCF, Cozy, and affected sbt-cozy
  core acceptance pass.
- External CAR validation is not a Phase 52 closure gate; CARs are repaired
  individually when incompatibilities are observed.
- Final canonical design/specification defines complete String round-trip as
  the normal and only Entity ID contract.

## Required Repositories

| Repository | Phase 52 responsibility |
| --- | --- |
| `simplemodeling-model` | Exact collection and Entity ID codecs, value, parsing, equality, and hashing |
| `simple-modeler` | Generated primary/reference persistence and schema behavior |
| `cloud-native-component-framework` | Store addressing, routing, decode validation, UnitOfWork, built-ins, caches, locks, authorization |
| `cozy` | Generated-source acceptance for the new exact serialization contract |
| `sbt-cozy` | Build/generation acceptance where the contract changes its fixtures or bridge |
| `textus-sample-apps` | Migrate downstream `RuntimeConfig` public-constant callers to the canonical lower-camel API. |
| `textus-knowledge-editor` | Migrate downstream `InformationModel` public-constant callers to the canonical lower-camel API. |
| `textus-semantic-integration-engine` | Migrate downstream `InformationModel` public-constant callers to the canonical lower-camel API. |

CAR repositories are not required repositories for Phase 52. They adopt the
new contract through later CAR-local fixes when concrete failures are found.

## Attributable Scope Decision

### P52-COMP-01 (2026-07-30)

The phase owner selected the breaking public-API migration: replace the
PascalCase public constants in `RuntimeConfig` and `InformationModel` with
canonical lower-camel names, without compatibility aliases.  The admitted
Phase 52 repository set therefore includes `textus-sample-apps`,
`textus-knowledge-editor`, and `textus-semantic-integration-engine` for the
corresponding downstream source migration and validation.  This decision is a
scope reset; subsequent work resumes with a fresh Phase 52 plan.

## Planning References

- [Phase 51 - CNCF-Cozy CML Generation Version Alignment](phase-51.md)
- [Phase 52 Checklist](phase-52-checklist.md)
- [ID Design](../design/id.md)
- [Entity Collection Identity Design](../design/entity-collection-identity.md)
- [Entity Collection Identity Specification](../spec/entity-collection-identity.md)
- [Phase 51 CI-01 Exact Collection Identity Contract](../notes/phase-51-ci01-exact-collection-identity-contract.md)

## Current Status

Phase 52 is in progress. EID-01 through EID-05 are complete; EID-06 is in progress.
Phase 51 remains historical evidence, not a compatibility constraint on the
clean design.
