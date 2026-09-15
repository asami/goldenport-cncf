# Phase 74 - EntityId Inheritance Contract Restoration

status=closed
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-74.3
aggregate_validation_sequence=["PHASE-74","PHASE-74.1","PHASE-74.2","PHASE-74.3"]
planned_at=2026-09-13
split_at=2026-09-16
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#961-entityid-inheritance-contract-restoration)
checklist=[Phase 74 Checklist](phase-74-checklist.md)
successor=[Phase 74.1](phase-74.1.md)

Phase 74 is closed as the EIR-01 authority/inventory handoff. The release
records the verified aggregate-deferred validation handoff to Phase 74.3; it
does not run the repository full suite in this intermediate Phase. The
2026-09-13 journal remains an immutable historical record. Its future-tense
transition-identification promise is superseded for EIR-01 closure by the
accepted authority note, without changing the journal's verified facts or
contemporaneous uncertainty.

## Purpose

Restore `EntityId` as the abstract conceptual base for entity-specific IDs,
preserving canonical identity and ordinary automatic generation, and adopt
`JobDefinitionId` as the first CNCF consumer. The intended hierarchy is
`UniversalId -> abstract EntityId -> concrete XxxId`, with JobDefinitionId as
the first concrete CNCF type: EntityId refines, rather than replaces, the
UniversalId foundation.

## Ownership and Scope

- CNCF owns this Phase/checklist and cross-repository acceptance coordination.
- `simplemodeling-model` owns the abstract EntityId contract, generic
  materialization, typed construction/decoding, and executable specifications.
- `simplemodeling-lib` owns the unchanged UniversalId foundation; its history
  is an inventory input, not authorization to duplicate EntityId there.
- CNCF owns this parent contract/inventory step. Its successors own model
  implementation, CNCF/generator adoption, and final closure respectively.
- `simplemodeling-model` owns the abstract EntityId implementation after the
  parent has frozen its contract. `simplemodeling-lib` retains the unchanged
  UniversalId foundation.
- CNCF owns the IdGenerationContext/JobDefinition adoption inventory; the
  affected consumer implementation is owned by Phase 74.2. `simple-modeler`
  owns only affected generated entity-specific IDs.

## EIR-01 Accepted Authority Handoff

The [accepted EIR-01 authority](../notes/entityid-inheritance-contract-restoration-provisional-specification.md)
freezes the hierarchy, ordinary issuance, and the generic boundaries. Bare
`EntityId` remains valid for generic EntityStore/common persistence, generic
decode, `parse`, `restore`, and admitted `bridgeFromParts` recovery, but is
not an ordinary new durable issuance surface. Ordinary issuance uses a
concrete subtype or `IdGenerationContext`; it obtains namespace, timestamp,
and fresh entropy once and never derives an ID from a business key.

For JobDefinition, creation issues and persists `JobDefinitionId` in
`{ id, key, ... }`; update/restart preserve it, and key lookup uses the ID in
the matched persisted entity. `a-b` and `a_b` remain distinct, with no
key-derived ID, key-to-ID table, hash, or companion integrity value.

The accepted authority records the exhaustive ten-site production matrix and
complete consumer map. The current CNCF consumer path
`src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala`
has no direct construction site; the ten sites are:

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

The consumer owners are `simplemodeling-model` (Phase 74.1 model contract),
`simplemodeling-lib` (read-only UniversalId foundation), `simple-modeler`
(read-only generated consumer inventory), and CNCF (Phase 74.2 context,
ten-site migration, JobDefinition adoption/lookup, and consumer
specifications).

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| EIR-01 | Historical contract and affected consumer inventory; source API, generation, canonical encoding, typed decoding, direct-construction classification, and validation boundary fixed. | accepted authority, complete |
| EIR-02 | Abstract EntityId base, normal/special generation, and generic/typed restoration accepted in simplemodeling-model. | successor Phase 74.1 |
| EIR-03 | CNCF context, audited direct-construction migration, and JobDefinitionId adoption; generated entity ID consumers use the restored base without key-derived ordinary IDs. | successor Phase 74.2 |
| EIR-04 | Producer/consumer validation, specification promotion, review, and release closure. | successor Phase 74.3 |

The former single Phase is now the ordered `PHASE-74` through `PHASE-74.3`
sequence. Each member is one calibrated approximately six-hour unit. This
parent retains the public-contract PLAN; bounded consumer work follows only
the released typed contract.

## Scope for this Parent Unit (EIR-01)

- Trace the historical abstract EntityId and concrete replacement; distinguish
  verified history from the reported migration incident.
- Inventory constructors, apply/copy/pattern matching, EntityPersistent,
  EntityStore, Record/JSON codecs, IdGenerationContext, and generator consumers.
- Freeze every CNCF production direct `EntityId` construction site as ordinary
  issuance, complete saved-value restoration, or a special bridge. Do not infer
  a defect merely from explicit ID parts.
- Freeze the abstract-base API, ordinary automatic generation, explicit special
  generation, generic materialization, subtype-preserving decode, and explicit
  generic `parse`, `restore`, and `bridgeFromParts` exception boundaries.
- Freeze the JobDefinition rule: a creation issues `JobDefinitionId`; a
  persisted `{ id, key, ... }` entity carries the correspondence; later key
  lookup loads that stored ID. No business-key-derived ID, key-to-ID table,
  hash, or companion integrity value is admitted.

## Split Provenance

- Applied decision: `P74-SPLIT-2026-09-16`.
- The 2026-09-16 Phase-entry gate for the original unsplit Phase 74 returned
  `SPLIT_REQUIRED` with reasons `time-bound` and `reasoning-cost-isolation`.
  Its calibrated estimate was 1,440 minutes (conservative 1,800), while the
  target/ceiling were 360/480 minutes.
- No Phase 74 implementation or goal had begun. The entry result is retained
  as dated pre-split evidence, not as the current plan gate.
- Split sequence: 74 freezes the design/inventory; 74.1 implements the model
  contract; 74.2 adopts it in CNCF/generator consumers; 74.3 owns integration
  closure and the one aggregate full suite.

## Phase Plan Gate: PROCEED

- target and ceiling: expected 6 hours (range 5–7); hard ceiling 8 hours.
- estimate calibration: no comparable completed slice exists; the original
  plan explicitly estimated each EIR group at approximately six hours.
- planning demand: protected decision; recommended profile: `gpt-5.6-terra /
  xhigh`; profile cost role: expensive reasoning kernel.
- expensive kernel: freeze one source-level EntityId inheritance, issuance,
  restoration, and special-bridge boundary that all later consumers can use
  without relitigating identity semantics.
- incoming semantic handoffs: none. Output handoff to `PHASE-74.1` is the
  accepted contract, exact consumer inventory, classified construction matrix,
  and executable identity-behavior acceptance definitions.
- merge/rebalance evidence: none; every original EIR unit independently fits
  the calibrated six-hour target. Splitting adds documentation/review overhead,
  but isolates the only expensive design judgment from routine consumer work.
- runtime suitability: re-evaluate at execution; this plan does not create a
  goal or start implementation.

## Coordination

- Consume Phase 52's accepted complete collection-exact canonical identity;
  do not reopen or replace its encoding contract.
- Coordinate the Phase 74.2 consumer inventory with the accepted Phase 69
  JobDefinition producer handoff; do not reopen its accepted semantics.
- Phases 71-73 retain their independent ownership and status.

## Parent Completion and Handoff Conditions

- EIR-01 is complete when the accepted authority, checklist, and journal all
  carry one hierarchy, issuance/parse/restore/bridge boundary, consumer map,
  and exhaustive ten-site matrix.
- The frozen contract explicitly preserves generic EntityStore/generic decode
  values while restricting only new durable issuance to concrete IDs or
  IdGenerationContext.
- The authority handoff to Phase 74.1 is recorded and sufficient to implement
  the model contract without rediscovering semantic intent. Phase 74.2 owns
  the ten-site consumer migration; Phase 74.3 owns aggregate closure.

## Non-Goals

- Model implementation, consumer migration, producer publishing, tests, full
  suite, review repair, and release: they belong to Phases 74.1–74.3.
- Broad uniqueness/index redesign, unrelated Job execution features, or shared
  skill/rule additions.

## References

- [Accepted EIR-01 Authority](../notes/entityid-inheritance-contract-restoration-provisional-specification.md)
- [Design Journal](../journal/2026/09/2026-09-13-entityid-inheritance-contract-restoration.md)
- [Phase 52](phase-52.md)
- [Phase 69.3](phase-69.3.md)
