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
7. SS-05 implements the storage-shape policy for management fields, permission,
   and nested/repeated values.
8. SS-06 exposes the effective storage shape in manual/admin/projection surfaces.

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

Status: PLANNED

### Objective

Stop making SimpleEntity authorization depend on generic `Record` path shape.

### Detailed Tasks

- [ ] Define typed security/permission accessor surface for stored entities.
- [ ] Support owner/group/role/privilege/permission inspection without assuming `security_attributes` / `securityAttributes` record aliases.
- [ ] Keep raw-record authorization as a transitional fallback only.
- [ ] Add specs showing compressed/encoded permission can still be authorized through typed access.

### Expected Outcome

- Authorization semantics no longer constrain physical storage field layout.
- Permission can move toward compact storage without breaking access policy.

### Guardrails

- Do not create a new parallel authorization model.
- Do not encode security policy as another generic record projection.

---

## SS-05: SimpleEntity Storage-Shape Policy

Status: PLANNED

### Objective

Define and implement the default SimpleEntity DB storage rules.

### Detailed Tasks

- [ ] Define built-in management field expansion set.
- [ ] Keep lifecycle, owner/group, state, and operational management fields queryable where required.
- [ ] Store `permission` as a compact encoded field by default.
- [ ] Encode semantically independent value objects by default.
- [ ] Encode repeated value objects by default unless promoted by explicit model metadata.
- [ ] Add specs for management expansion, permission compression, independent value encoding, and repeated value encoding.

### Expected Outcome

- SimpleEntity storage shape is deterministic and policy-driven.
- DB-level filtering remains possible for framework management fields.
- Domain nested values do not explode into parent columns accidentally.

### Guardrails

- Do not make every value object queryable by physical flattening.
- Do not implement Blob payload storage here.

---

## SS-06: Storage-Shape Visibility

Status: PLANNED

### Objective

Expose storage-shape decisions so operators and developers can inspect the effective model.

### Detailed Tasks

- [ ] Add storage-shape summary to manual/admin/projection where entity metadata is shown.
- [ ] Show which fields are expanded, encoded, compact permission, or delegated to child/entity storage.
- [ ] Keep raw technical metadata secondary and collapsible where rendered in Web manual/admin pages.

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
