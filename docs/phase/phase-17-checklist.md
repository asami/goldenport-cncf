# Phase 17 — SimpleEntity Storage Shape Checklist

This document contains detailed task tracking and execution decisions for Phase
17.

It complements the summary-level phase document (`phase-17.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-17.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]` in the phase
  document.

---

## Status Semantics

- ACTIVE: currently being worked on (only one item may be ACTIVE).
- SUSPENDED: intentionally paused with a clear resume point.
- PLANNED: planned but not started.
- DONE: handling within this phase is complete.

---

## Recommended Work Procedure

Phase 17 work proceeds in this order:

1. SS-01 defines the Record Purpose Taxonomy and boundary rules.
2. SS-02 formalizes existing `EntityPersistent.toStoreRecord/fromStoreRecord`
   as the storage API while preserving compatibility.
3. SS-03A migrates DB Record boundary call sites to store APIs.
4. SS-03B introduces and applies the View Record boundary API.
5. SS-03C classifies the remaining Logic Record call sites before typed-access
   migration.
6. SS-04 introduces typed SimpleEntity security/permission access so
   authorization stops depending on generic record-path lookup.
7. SS-05A fixes the storage-shape policy in design/spec.
8. SS-05A-A fixes storage-shape classification order and rules.
9. SS-05B implements the runtime storage-shape policy for management fields,
   security identity, and permission.
10. SS-05C-A adds typed security override regression specs.
11. SS-05C-B adds runtime storage-shape coverage.
12. SS-05C-C adds value object storage encoding coverage.
13. SS-05C-D adds promoted child storage boundary coverage.
14. SS-05C-E adds generated CML-derived storage-shape coverage.
15. SS-05C-F adds unsupported typed scalar fallback coverage.
16. SS-05C completes executable coverage for the target storage shape.
17. SS-06A exposes effective storage shape in projection metadata.
18. SS-06 exposes the effective storage shape in manual/admin/projection surfaces.

This order avoids changing DB record shape before Record purpose, API boundary,
and security access boundary are clear.

---

## SS-01: Record Purpose Taxonomy and Boundary

Status: DONE

### Objective

Formalize how CNCF uses `Record` at different boundaries before changing Store
API or SimpleEntity physical storage shape.

SS-01 must ensure:

- DB/datastore storage records are distinct from presentation records.
- Logic records used by runtime authorization/lifecycle/working-set logic are
  identified as transitional and should move toward typed accessors.
- Mutation records, query records, request records, descriptor records, and
  diagnostic records are named separately.
- New code must state the Record purpose when a `Record` crosses a boundary.
- Store API formalization is deferred to SS-02.

### Detailed Tasks

- [x] Add `docs/design/record-purpose-taxonomy.md`.
- [x] Promote the taxonomy from the SimpleEntity storage-shape note into design.
- [x] Update Phase 17 work order to make taxonomy first.
- [x] Defer `toStoreRecord` formalization to SS-02.
- [x] Keep typeclass/API and physical DB shape unchanged in SS-01.

### Expected Outcome

- Implementers know whether a `Record` is storage, presentation, logic,
  mutation, query, request, descriptor, or diagnostic data.
- Later SS-02+ work can migrate APIs and call sites without conflating Record
  purposes.

### Guardrails

- No `EntityPersistent` API changes in SS-01.
- No physical DB storage-shape changes in SS-01.
- No typed authorization accessor implementation in SS-01.

---

## SS-02: Formalize EntityPersistent Store APIs

Status: DONE

### Objective

Formalize existing `toStoreRecord` / `fromStoreRecord` as the DB Record API for the `EntityPersistent` family while preserving compatibility.

### Detailed Tasks

- [x] Document `toStoreRecord` / `fromStoreRecord` as the formal DB Record API.
- [x] Keep `toRecord` / `fromRecord` as compatibility bridges.
- [x] Add compatibility specs for old-style implementations.
- [x] Add behavior specs proving store paths use the store API when overridden.
- [x] Do not introduce `toStorageRecord` in this phase.

### Expected Outcome

- Entity persistence APIs have explicit storage names.
- Existing generated/component implementations remain source-compatible.
- `EntityStore` create/load/search paths are covered by executable specs for
  store API override behavior.

### Guardrails

- Do not change physical DB record shape.
- Do not remove `RecordCodex` / `RecordEncoder` inheritance.

---

## SS-03A: DB Record Boundary Call Site Migration

Status: DONE

### Objective

Migrate DB/datastore boundary paths away from generic `toRecord` / `fromRecord` names.

### Detailed Tasks

- [x] Migrate remaining DB boundary paths in `EntityStoreSpace` to explicit store APIs.
- [x] Ensure import seed writes use `EntityPersistent.toStoreRecord`.
- [x] Ensure patch update conversion uses `EntityPersistentUpdate.toStoreRecord`.
- [x] Ensure reflective `EntityPersistent` bridges prefer explicit `toStoreRecord/fromStoreRecord` when available.
- [x] Add regression specs for import seed, patch update, and reflective persistent bridge behavior.

### Expected Outcome

- DB boundary call sites are visibly storage-oriented.
- Store-specific physical field names are not lost through compatibility `toRecord`.

### Guardrails

- Do not change physical DB record shape.
- Do not perform broad mechanical replacement of every `toRecord` call.

---

## SS-03B: View Record Boundary API

Status: DONE

### Objective

Introduce an explicit entity View Record API and stop routing admin/entity presentation through storage-oriented APIs.

### Detailed Tasks

- [x] Add `EntityPersistent.toViewRecord` as the explicit entity View Record API.
- [x] Migrate admin entity display paths from persistence `toRecord` to `toViewRecord`.
- [x] Keep `EntityDisplayable.toDisplayRecord` as the view-specific projection hook under `toViewRecord`.
- [x] Add regression specs proving View Record output does not expose DB field shape.

### Expected Outcome

- View boundary call sites are visibly presentation-oriented.
- Admin/entity presentation no longer calls persistence `toRecord` directly.

### Guardrails

- Do not use View Records as persistence or authorization input.
- Do not redesign admin UI in this slice.

---

## SS-03C: Logic Record Call Site Classification

Status: DONE

### Objective

Classify remaining internal Logic Record paths that still inspect generic `Record` and prepare their migration to typed accessors in SS-04/SS-05.

### Detailed Tasks

- [x] Classify `UnitOfWorkInterpreter` authorization records as authorization Logic Record usage.
- [x] Classify `ActionCallFeaturePart` entity record lookups as authorization/operation Logic Record usage.
- [x] Classify runtime `Collection` lifecycle, visibility, and working-set policy record reads as lifecycle/working-set Logic Record usage.
- [x] Document which paths move to SS-04 typed security/permission access.
- [x] Document which paths move to SS-05 lifecycle/storage-shape policy.
- [x] Keep behavior unchanged; add focused specs only when SS-04/SS-05 changes behavior.

### Classification

| Call site | Current Record use | Purpose | Next work |
| --- | --- | --- | --- |
| `UnitOfWorkInterpreter` save/update authorization | `m.tc.toRecord(m.entity)` passed to authorization | Authorization Logic Record | SS-04 typed security/permission access |
| `ActionCallFeaturePart` entity authorization fallback | `tc.toRecord(entity)` used when store record lookup is unavailable | Authorization Logic Record | SS-04 typed security/permission access |
| `ActionCallFeaturePart` aggregate create/update authorization | `aggregate.toRecord()` passed to aggregate authorization | Operation/authorization Logic Record | SS-04 aggregate authorization review |
| `ActionCallFeaturePart` aggregate load from collection | `collection.descriptor.persistent.toRecord(entity)` produces aggregate member record | Operation Logic Record | SS-04/SS-05 aggregate projection boundary review |
| runtime `Collection` short-id lookup | `descriptor.persistent.toRecord(entity)` reads `shortid` | Identity/lookup Logic Record | SS-05 storage-shape policy for management fields |
| runtime `Collection` visibility filtering | `descriptor.persistent.toRecord(entity)` reads lifecycle fields | Lifecycle Logic Record | SS-05 typed lifecycle access |
| runtime `Collection` working-set policy | `descriptor.persistent.toRecord(entity)` feeds residency policy | Working-set Logic Record | SS-05 typed policy input / storage-shape policy |
| runtime `Collection` logical delete check | `descriptor.persistent.toRecord(entity)` reads `deletedAt`, `postStatus`, `aliveness` | Lifecycle Logic Record | SS-05 typed lifecycle access |
| `ComponentDescriptor` and descriptor loaders | `fromRecord` decodes component/subsystem metadata | Descriptor Record | Not SS-04/SS-05 |
| `AdminComponent` generic `RecordPresentable` helpers | `presentable.toRecord()` formats generic values | View/Diagnostic helper | Not entity DB/View boundary; keep as generic fallback |
| `EntityPersistent` default methods and derived helpers | compatibility `toRecord/fromRecord` bridge | Compatibility bridge | Keep until replacement APIs cover all call sites |

### Expected Outcome

- Remaining generic `toRecord` calls are accounted for by purpose.
- No Logic Record path is accidentally treated as DB Record or View Record.
- SS-04 and SS-05 can implement typed access without redoing taxonomy work.
- DB and View boundary paths remain separated from internal Logic Record paths.

### Guardrails

- Do not implement typed authorization/lifecycle access in SS-03C unless the task is explicitly promoted.
- Do not remove compatibility `toRecord` paths before SS-04/SS-05 replacements exist.

---

## SS-04: Typed SimpleEntity Security Access

Status: DONE

### Objective

Stop making SimpleEntity authorization depend on generic `Record` path shape.

### Detailed Tasks

- [x] Define typed security/permission accessor surface for stored entities.
- [x] Support owner/group/role/privilege/permission inspection without assuming `security_attributes` / `securityAttributes` record aliases.
- [x] Keep raw-record authorization as a transitional fallback only.
- [x] Add specs showing authorization can use typed access even when the entity record omits security attributes.

### Implementation Notes

- `EntityPersistent.securityAttributes(entity)` is the typed security access
  hook.
- `EntityPersistent.authorizationRecord(entity)` is a compatibility bridge for
  authorization paths that still need a `Record` while SS-05 is pending.
- `OperationAccessPolicy` now prefers typed `SecurityAttributes` when an
  `EntityPersistent` can provide them.
- Raw-record authorization remains a fallback for legacy persistent
  implementations and direct datastore records.
- Search/list visibility and save/update target authorization are covered by
  executable specs where the entity `toRecord` intentionally omits
  `security_attributes`.

### Expected Outcome

- Authorization semantics no longer constrain physical storage field layout.
- Permission can move toward compact storage without breaking access policy.

### Guardrails

- Do not create a new parallel authorization model.
- Do not encode security policy as another generic record projection.

---

## SS-05A: SimpleEntity Storage-Shape Policy Spec

Status: DONE

### Objective

Define the default SimpleEntity DB storage-shape rules before implementation.

### Detailed Tasks

- [x] Add `docs/design/simpleentity-storage-shape-policy.md`.
- [x] Define built-in management field expansion set.
- [x] Keep lifecycle, owner/group, state, and operational management fields queryable where required.
- [x] Store `permission` as compact JSON text by default.
- [x] Encode semantically independent value objects as JSON text by default.
- [x] Encode repeated value objects as JSON array text by default unless promoted by explicit model metadata.
- [x] Record required SS-05B/SS-05C executable coverage.

### Expected Outcome

- SimpleEntity storage shape is deterministic and policy-driven.
- DB-level filtering remains possible for framework management fields.
- Domain nested values do not explode into parent columns accidentally.

### Guardrails

- Do not make every value object queryable by physical flattening.
- Do not implement Blob payload storage here.
- Do not add runtime APIs or DB migration in SS-05A.

---

## SS-05A-A: Storage-Shape Classification Rules

Status: DONE

### Objective

Make the SimpleEntity storage-shape classification order decision-complete
before expanding runtime implementation or executable coverage.

### Detailed Tasks

- [x] Define classification order in `docs/design/simpleentity-storage-shape-policy.md`.
- [x] Fix CNCF management/security identity fields as the first classification.
- [x] Fix permission rights as compact policy data, not expanded scalar columns.
- [x] Fix domain scalar attributes as ordinary columns.
- [x] Fix independent single value objects as encoded JSON text.
- [x] Fix repeated value objects as encoded JSON array text.
- [x] Fix independent lifecycle children / promoted children as entity, collection, aggregate member, or explicit child storage.
- [x] Strengthen the rule that JSON text is not an unsupported scalar fallback.
- [x] Add SS-05C executable coverage expectations for classification order and unsupported typed scalar failure.

### Expected Outcome

- Implementers can classify a model value before deciding storage shape.
- SS-05C can turn the classification rules into executable documentation.

### Guardrails

- Do not add runtime APIs, DB migration, generated code changes, or codec implementation in SS-05A-A.
- Do not define the exact encoded JSON wire format beyond the classification rule.

---

## SS-05B: SimpleEntity Storage-Shape Policy Implementation

Status: DONE

### Objective

Implement the policy fixed in SS-05A.

### Detailed Tasks

- [x] Apply expanded management/security identity target names in runtime store records.
- [x] Store permission rights as compact JSON text in `permission`.
- [x] Keep typed authorization independent of expanded permission record paths.
- [x] Keep legacy `securityAttributes` / `security_attributes.rights` as read compatibility input.
- [x] Normalize runtime-created management/security fields to target snake_case names.
- [x] Defer generated independent/repeated value encoding to SS-05C coverage and follow-up implementation if needed.

### Implementation Notes

- `SimpleEntityStorageShapePolicy` owns target field names, permission JSON
  encoding/decoding, and compatibility security extraction.
- `EntityCreateDefaultsPolicy` writes runtime create defaults using target
  snake_case field names and compact `permission`.
- `EntityStore` save/update/delete complement paths write runtime management
  fields using target snake_case names.
- `OperationAccessPolicy` resolves compact target permission through typed
  security extraction rather than expanded permission record paths.

### Expected Outcome

- Runtime storage records follow the SS-05A target shape.
- Existing compatibility inputs remain readable where required.

### Guardrails

- Do not silently coerce unsupported scalar types to `String`.
- Do not remove compatibility bridges without a separate migration plan.
- Do not treat SS-05B as generator-wide storage-shape migration.

---

## SS-05C: Storage-Shape Executable Coverage

Status: DONE

### Objective

Lock the implemented SS-05B behavior with executable specs.

### Completed Sub-Slices

- [x] SS-05C-A: Typed security override regression specs.
- [x] SS-05C-B: Runtime storage-shape coverage.
- [x] SS-05C-C: Value object storage encoding coverage.
- [x] SS-05C-D: Promoted child storage boundary coverage.
- [x] SS-05C-E: Generated CML-derived storage-shape coverage.
- [x] SS-05C-F: Unsupported typed scalar fallback coverage.

### Detailed Tasks

- [x] Add specs proving management fields are expanded.
- [x] Add specs proving permission is compact JSON text.
- [x] Add specs proving typed authorization works from compact permission.
- [x] Add specs proving scalar domain attributes remain ordinary columns.
- [x] Add specs proving independent value objects are encoded.
- [x] Add specs proving repeated value objects are encoded.
- [x] Add specs proving promoted child/entity storage is not flattened into the parent.
- [x] Add generated-code specs for CML-derived storage shape.
- [x] Add specs proving unsupported typed scalar values do not fall back to `String`.

### Expected Outcome

- The target storage shape is executable documentation, not only prose.

### Guardrails

- Specs must describe behavior, not generated-code incidental details.

---

## SS-05C-F: Unsupported Typed Scalar Fallback Coverage

Status: DONE

### Objective

Lock the rule that unsupported typed scalar values fail deterministically and
do not silently fall back to `String` in model/generator storage paths.

### Completed Tasks

- [x] Prove supported date-time scalar modeling maps to an explicit Java time
  type instead of `String`.
- [x] Prove unsupported scalar marshalling fails deterministically instead of
  falling back to `String`.
- [x] Prove Cozy rejects unsupported CML `date-time` syntax without generating
  output.

### Guardrails

- JSON text storage is for complex value containers, not an unsupported scalar
  escape hatch.
- CML type aliases must be explicit; unknown spellings fail before generation.

---

## SS-05C-D: Promoted Child Storage Boundary Coverage

Status: DONE

### Objective

Lock the SS-05A rule that promoted children are stored as independent
entity/collection records and assembled by aggregate/view composition, not
flattened into the parent storage record.

### Completed Tasks

- [x] Prove parent aggregate root storage does not contain promoted `lines` /
  `customer` child fields.
- [x] Prove promoted child records are present in their own entity collections.
- [x] Prove default aggregate read composes child members from those collections.

### Guardrails

- Parent-owned value object JSON encoding remains SS-05C-C behavior.
- Unsupported scalar failure is covered by SS-05C-F.

---

## SS-05C-E: Generated CML-Derived Storage-Shape Coverage

Status: DONE

### Objective

Lock the rule that generated CML-derived entity persistence exposes explicit
Store Record APIs and does not use View/Presentation record shape as the DB
shape.

### Completed Tasks

- [x] Prove generated-style CNCF reflective `EntityPersistent` bridges prefer
  explicit `toStoreRecord` / `fromStoreRecord` methods.
- [x] Prove generated-style Store Records use target management/security names
  and compact `permission`.
- [x] Prove generated-style View Records remain separate from Store Records.
- [x] Prove Cozy/simple-modeler generated SimpleEntity code emits explicit
  `toStoreRecord` / `fromStoreRecord` and compact permission storage.

### Guardrails

- Unsupported typed scalar failure is covered by SS-05C-F.
- Runtime storage-shape behavior remains SS-05B/SS-05C-B behavior.

---

## SS-05C-C: Value Object Storage Encoding Coverage

Status: DONE

### Objective

Lock the SS-05A rule that parent-owned value objects are encoded inside the
parent storage record.

### Completed Tasks

- [x] Prove single parent-owned value objects are encoded as JSON text by the SQL datastore path.
- [x] Prove repeated parent-owned value objects are encoded as JSON array text by the SQL datastore path.
- [x] Prove scalar domain fields in the same record remain ordinary columns.
- [x] Prove `EntityPersistent.toStoreRecord` / `fromStoreRecord` own explicit value object storage decoding.

### Guardrails

- JSON encoding is for complex owned value containers, not unsupported scalar fallback.
- Promoted child/entity storage and generated CML-derived shape are covered by SS-05C-D and SS-05C-E.

---

## SS-05C-B: Runtime Storage-Shape Coverage

Status: DONE

### Objective

Lock the SS-05B runtime storage-shape implementation with executable specs.

### Completed Tasks

- [x] Prove runtime create defaults emit target snake_case management and
  security identity fields.
- [x] Prove runtime create defaults emit compact `permission` JSON and not
  legacy `rights` / `securityAttributes` containers.
- [x] Prove store save/update/delete complement paths write target snake_case
  audit/state/trace fields.
- [x] Prove runtime update audit fields override caller-supplied stale audit
  values.
- [x] Prove scalar domain attributes remain ordinary store columns.
- [x] Prove compact permission remains decodable by the storage-shape policy.

### Guardrails

- Compatibility input may still accept legacy security shapes, but runtime target
  output remains snake_case plus compact `permission`.
- Nested/repeated value object encoding and generated-code storage shape are
  covered by SS-05C-C and SS-05C-E.

---

## SS-05C-A: Typed Security Override Regression Specs

Status: DONE

### Objective

Lock the SS-04/SS-05 rule that typed security access is authoritative when
authorization records are built from a mix of resident entities and raw store
records.

### Detailed Tasks

- [x] Add specs proving stale target and legacy security fields are removed before typed security overlay.
- [x] Add specs proving stale record owner cannot pass save/update authorization when typed owner differs.
- [x] Add specs proving working-set resident read hit uses typed security over stale datastore security.
- [x] Add specs proving stale datastore owner is denied when typed resident security differs.

### Expected Outcome

- Review findings around typed security precedence are locked by executable specs.
- Compact permission storage and raw record compatibility cannot reintroduce stale-security precedence.

### Guardrails

- Do not broaden SS-05C-A into nested/repeated storage-shape implementation.
- Keep raw store records available only as non-security ABAC/relation context once typed security exists.

---

## SS-06A: Storage-Shape Metadata Projection

Status: DONE

### Objective

Expose effective SimpleEntity storage-shape decisions as component projection metadata before manual/admin rendering.

### Completed Tasks

- [x] Add entity collection storage-shape metadata to component describe projection.
- [x] Add the same entity collection storage-shape metadata to component schema projection.
- [x] Show management fields, security identity fields, compact permission, scalar fields, and delegated collection metadata.
- [x] Keep permission bit internals hidden.

### Guardrails

- Do not add manual/admin rendering in SS-06A.
- Do not inspect live records or mutate storage policy.

---

## SS-06B: Manual Storage-Shape Summary

Status: DONE

### Objective

Render effective SimpleEntity storage-shape decisions in component Web manual pages from the canonical projection metadata.

### Completed Tasks

- [x] Add a component manual `Storage shape` card sourced from `entityCollections.storageShape`.
- [x] Render entity-level summary rows for entity, collection, memory policy, working-set policy, and storage policy.
- [x] Render field-level rows for logical name, storage name, classification, storage kind, data type, and source.
- [x] Keep raw Describe/Schema details in the existing JSON/YAML tabs.
- [x] Keep the manual read-only and hide permission bit internals.

### Guardrails

- Do not add storage-policy mutation controls.
- Do not invent a separate manual-only storage-shape data source.
- Do not show legacy SimpleEntity attribute containers as storage fields.

---

## SS-06C: Admin Storage-Shape Summary

Status: DONE

### Objective

Render effective SimpleEntity storage-shape decisions in component admin entity pages from the canonical projection metadata.

### Completed Tasks

- [x] Add a component admin `Storage shape` card to entity list and entity type pages.
- [x] Render entity-level summary rows for entity, collection, memory policy, working-set policy, storage policy, and field classification counts.
- [x] Render entity-type field rows for logical name, storage name, classification, storage kind, data type, and source.
- [x] Keep the admin view read-only and hide permission bit internals.

### Guardrails

- Do not add storage-policy mutation controls.
- Do not invent a separate admin-only storage-shape data source.
- Do not show legacy SimpleEntity attribute containers as storage fields.

---

## SS-06: Storage-Shape Visibility

Status: DONE

### Objective

Expose storage-shape decisions so operators and developers can inspect the effective model.

### Detailed Tasks

- [x] Add storage-shape summary to projection where entity metadata is shown.
- [x] Add storage-shape summary to manual where entity metadata is shown.
- [x] Add storage-shape summary to admin where entity metadata is shown.
- [x] Show which fields are expanded, encoded, compact permission, or delegated to child/entity storage.
- [x] Keep raw technical metadata secondary and collapsible where rendered in Web manual pages.
- [x] Keep admin storage-shape rendering read-only and summary-first.

### Expected Outcome

- Storage behavior is inspectable without reading generated code.
- Sample and executable specs can demonstrate the boundary clearly.

### Guardrails

- Do not add mutation controls for storage policy in this item.
- Do not expose sensitive permission internals beyond appropriate admin scope.

---

## Deferred / Out-of-Scope Notes

- Blob payload storage and Blob management component.
- Full DB migration framework.
- Search/index backend expansion.
- Removing `toRecord` / `fromRecord` compatibility bridges entirely.
