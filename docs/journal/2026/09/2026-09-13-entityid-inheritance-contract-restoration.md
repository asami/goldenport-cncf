# 2026-09-13 - EntityId Inheritance Contract Restoration Planning

## Trigger

A reported JobDefinition lookup repair regenerated EntityId from its business
key, using a fixed timestamp and key-derived entropy. Current CNCF
`JobDefinitionEntity.entityId(key)` exhibits that route. The discussion first
considered closing all arbitrary-part construction, but that was too broad:
the user confirmed arbitrary entropy is intentionally supported for special
purposes, while ordinary issuance obtains UUID/entropy automatically.

The user then clarified the intended entity model: an entity Xxx has XxxId
extending abstract EntityId, analogous to UniversalId. Current
simplemodeling-model instead has a final case class EntityId, and CNCF
JobDefinitionEntity holds the generic EntityId with no JobDefinitionId.

## Evidence and Uncertainty

- `simplemodeling-model:src/main/scala/org/simplemodeling/model/datatype/EntityId.scala`
  currently declares final case class EntityId with explicit/default identity parts.
- `cncf:src/main/scala/org/goldenport/cncf/context/IdGenerationContext.scala`
  already provides context-based ordinary issuance, but returns generic EntityId.
- `simplemodeling-lib:src/main/scala/org/goldenport/id/UniversalId.scala`
  defines the abstract operational identifier foundation and special stable parts.
- In simplemodeling-lib, `b317e7b^` contains
  `src/main/scala/org/goldenport/datatype/EntityId.scala` with
  `abstract class EntityId() { def id: String }`. Initial commit `b42e84f`
  contains an abstract EntityId under `org.simplemodeling.datatype` as well.
  This verifies a historical abstract contract, but that minimal old class
  alone does not prove the later UniversalId-based API or the precise accident.
- The user attributes the concrete/final change to relocation into
  simplemodeling-model. EIR-01 will identify the actual transition and dependent
  API history; this journal does not present that attribution as verified causality.

## Planning Decision

The user explicitly requested a new Phase for the important abstract-base
recovery. Add CNCF Phase 74 after the already planned Phase 73 as the
cross-repository coordination unit, with simplemodeling-model owning the model
implementation and JobDefinitionId as the first CNCF consumer.

Restore inheritance while retaining Phase 52's complete collection-exact
encoding. Separate ordinary issuance, explicit special-purpose construction,
and restoration of saved identity. Typed IDs alone do not repair a key-derived
lookup design; remove that regeneration premise as part of first-consumer adoption.

Keep detailed proposals in
[the provisional note](../../../notes/entityid-inheritance-contract-restoration-provisional-specification.md)
and progress in [Phase 74](../../../phase/phase-74.md) and its checklist.
Strategy item 9.61 owns this recovery without reopening Phase 52 or expanding
the broader Phase 69 Job Management, Phase 72 lifecycle, or Phase 73 knowledge
hub commitments. No shared rule or skill is enlarged by this decision.

## Worktree and Execution Boundary

This session adds planning documents only. Existing CNCF JobDefinition/JCL,
Phase 53/69.3, and Phase 73 planning changes are preserved. No Scala change,
SBT execution, publishLocal, commit, or Phase implementation start occurs.
The future consumer edit pass must coordinate a settled handoff for shared
JobDefinition paths; adding this plan is not a reason to stop current development.

## 2026-09-16 Clarification - Typed Identity and Persisted Correspondence

The intended hierarchy is `UniversalId -> abstract EntityId -> JobDefinitionId`.
`UniversalId` remains the general identity foundation; `EntityId` adds the
entity-identity boundary, and `JobDefinitionId` supplies the concrete type for
the JobDefinition collection. Consequently `JobDefinitionEntity.id` is a
`JobDefinitionId`, so APIs that operate on a JobDefinition cannot accidentally
accept a JobId, Blob ID, or unrelated entity ID merely because all have a
universal textual representation.

The durable relation between a JobDefinition business key and its identity is
kept by the persisted entity itself. Creation issues a fresh JobDefinitionId and
saves `{ id, key, ... }`; update retains that ID; and lookup searches persisted
entities by key, then uses the matched entity's saved ID. No key-derived ID,
separate key-to-ID correspondence table, hash, or companion integrity value is
required. An index on `key` remains a later store-performance option, not part
of the identity model.

The Phase inventory is therefore expanded beyond the first JobDefinition repair:
normalise direct EntityId construction by distinguishing ordinary issuance,
saved-value restoration, and existing special bridges. JobDefinition is the
first semantic correction. JobId/durable-job and association/blob bridges retain
their current semantics unless their own affected-edge review proves a defect;
they are not removed merely because they preserve explicit identity parts.

Generic EntityId remains necessary and permitted for EntityStore and other
common boundaries, existing-record generic decode, and code without a concrete
entity type. The restriction applies to *new issuance*: callers must not obtain
a new durable entity identity merely through a convenient generic
`EntityId(...)` constructor. Normal issuance uses the concrete type or
IdGenerationContext. Generic parsing/restoration and admitted special bridges
remain available through names that reveal their purpose, such as `parse`,
`restore`, and `bridgeFromParts`; EIR-01 freezes the exact API surface.

## 2026-09-16 EIR-01 Accepted Authority Handoff

The Phase 74 base authority (SHA-256
`d563221ab3083423702730d7d15ccfeef0d8bdfc17de1c55a880420e32c74999`) and
accepted plan-complete guard promote the provisional note to the EIR-01
authority handoff. The frozen hierarchy is
`UniversalId -> abstract EntityId -> concrete XxxId`, with `JobDefinitionId`
as the first concrete CNCF consumer. Bare `EntityId` remains valid at generic
EntityStore/common persistence, generic decode, `parse`, `restore`, and
declared `bridgeFromParts` recovery, but not as an ordinary new durable
issuance route. Ordinary issuance uses a concrete subtype or
`IdGenerationContext`, taking namespace, timestamp, and fresh entropy once;
business keys never derive IDs.

The JobDefinition correspondence is the persisted entity itself: creation
issues and stores `{ id, key, ... }`, update and restart preserve the typed ID,
and key lookup uses the ID from the matched stored entity. Keys `a-b` and
`a_b` remain distinct. No key-derived ID, key-to-ID table, hash, or companion
integrity value is introduced.

The complete consumer map is `simplemodeling-model` (Phase 74.1 model
contract), `simplemodeling-lib` (read-only UniversalId foundation),
`simple-modeler` (read-only generated EntityId consumer inventory), and CNCF
(Phase 74.2 context, direct-construction migration, JobDefinition adoption,
lookup, and consumer specifications). The live JobControlComponent path is
`src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala`;
it has no direct construction site.

The exact current CNCF production matrix is ten sites:

| # | Current source path and site | Classification |
| ---: | --- | --- |
| 1 | `src/main/scala/org/goldenport/cncf/blob/BlobProjection.scala:102` (`_blob_entity_id`) | saved-value restoration / special bridge |
| 2 | `src/main/scala/org/goldenport/cncf/blob/BlobProjection.scala:133` (`_media_entity_id`) | saved-value restoration / special bridge |
| 3 | `src/main/scala/org/goldenport/cncf/context/IdGenerationContext.scala:120` (`Context._entity_id`) | ordinary issuance |
| 4 | `src/main/scala/org/goldenport/cncf/job/DurableJobStore.scala:355` (`DurableJobStoreEntity.entityId`) | named durable JobId bridge |
| 5 | `src/main/scala/org/goldenport/cncf/job/JobEntity.scala:107` (`JobDefinitionEntity._entity_id`) | erroneous ordinary JobDefinition key-derived issuance |
| 6 | `src/main/scala/org/goldenport/cncf/job/JobEntity.scala:302` (`JobEntity.entityId`, parsed branch) | JobId recovery / bridge |
| 7 | `src/main/scala/org/goldenport/cncf/job/JobEntity.scala:310` (`JobEntity.entityId`, fallback branch) | JobId recovery / bridge |
| 8 | `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:3254` (`_admin_entity_record`) | ordinary framework issuance |
| 9 | `src/main/scala/org/goldenport/cncf/component/builtin/blob/BlobComponent.scala:1484` (`_association_delete`) | association special bridge |
| 10 | `src/main/scala/org/goldenport/cncf/observability/DiagnosticPayloadExternalization.scala:544` (`_blob_entity_id`) | ordinary diagnostic-payload issuance |

This documentation-only handoff changes no Scala source, successor Phase
documents, executable specifications, or validation state. Phase 74.1 owns
the model contract implementation and typed parse/restore/bridge behavior;
Phase 74.2 owns context and ten-site consumer migration plus JobDefinition
lifecycle specifications; Phase 74.3 owns aggregate closure.
