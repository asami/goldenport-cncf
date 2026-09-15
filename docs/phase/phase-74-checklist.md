# Phase 74 Checklist - EntityId Inheritance Contract Restoration

status=planned
phase=[Phase 74](phase-74.md)

## EIR-01: Contract and Consumer Inventory

Stage Status:
- Current status: ACCEPTED AUTHORITY HANDOFF
- Owner: CNCF and SimpleModeling maintainers
- Update rule: Close only from the following inventory and contract evidence.

EIR-01 authority is recorded in the [accepted specification](../notes/entityid-inheritance-contract-restoration-provisional-specification.md).
Authority: Phase 74 base SHA-256 `d563221ab3083423702730d7d15ccfeef0d8bdfc17de1c55a880420e32c74999`; the plan-complete guard is accepted.
The frozen hierarchy is `UniversalId -> abstract EntityId -> concrete XxxId`,
with `JobDefinitionId` as the first CNCF consumer. Bare `EntityId` remains
valid at generic EntityStore/common persistence, generic decode, `parse`,
`restore`, and declared `bridgeFromParts` boundaries, but not as an ordinary
new durable issuance surface. Ordinary issuance uses a concrete subtype or
`IdGenerationContext`, with one namespace/timestamp/entropy acquisition and
no business-key derivation. JobDefinition persists `{ id, key, ... }`, keeps
the ID on update/restart, and looks up the saved ID; `a-b` and `a_b` remain
distinct.

The accepted authority's exhaustive production matrix contains exactly ten
sites (the live JobControlComponent path is
`src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala`
and has no direct construction site):

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

Consumer ownership is complete and unchanged: `simplemodeling-model` owns the
Phase 74.1 model contract; `simplemodeling-lib` is the read-only
`UniversalId` foundation; `simple-modeler` is the read-only generated
consumer inventory; CNCF owns Phase 74.2 context, ten-site migration,
JobDefinition adoption/lookup, and consumer specifications.

- [x] Trace historical abstract EntityId and the concrete replacement; distinguish
      verified history from the reported migration incident.
- [x] Inventory constructors, apply/copy/pattern matching, EntityPersistent,
      EntityStore, Record/JSON codecs, IdGenerationContext, and generator consumers.
- [x] Freeze every CNCF production direct `EntityId` construction site and
      classify it as ordinary issuance, complete saved-value restoration, or a
      special bridge; do not infer a defect merely from explicit ID parts.
- [x] Freeze abstract-base API, ordinary automatic generation, explicit special
      generation, generic materialization, and subtype-preserving decode contracts.
- [x] Freeze the source-level prevention boundary: `UniversalId -> abstract
      EntityId -> XxxId`; a JobDefinition API must require JobDefinitionId and
      must not retain a bare generic construction path for business keys.
- [x] Preserve generic EntityId values and generic existing-record decode at
      common EntityStore boundaries, while forbidding generic new issuance.
      Freeze purpose-revealing generic exception APIs for parse, restore, and
      admitted bridge-from-parts use.
- [x] Preserve Phase 52's complete exact collection encoding and opaque identity.
- [x] Freeze exact repositories/paths, settled Phase 69 JobDefinition handoff,
      six-hour work estimates, and parent/worker execution profiles.

## Handoff Gate to Phase 74.1

- [x] Record the accepted typed API/exception boundary, construction matrix,
      affected repositories/paths, and Phase 69 input as an authority handoff.
- [x] Confirm that the handoff separates normal issue from saved-ID restoration
      and purpose-named special bridges, with no key-derived ordinary ID route.
- [x] Close this ledger only after the successor can implement the contract
      without revisiting the parent design decision.

## Split Record

- [ ] `P74-SPLIT-2026-09-16`: the unsplit entry gate's `SPLIT_REQUIRED`
      outcome is retained as pre-split evidence; the current Phase Plan Gate is
      `PROCEED` for EIR-01 only.
- [ ] EIR-02, EIR-03, and EIR-04 are tracked exclusively in the Phase 74.1,
      74.2, and 74.3 checklists.
