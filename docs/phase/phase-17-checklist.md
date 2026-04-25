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
3. SS-03 migrates CNCF internal storage call sites to purpose-specific API names.
4. SS-04 introduces typed SimpleEntity security/permission access so
   authorization stops depending on generic record-path lookup.
5. SS-05 implements the storage-shape policy for management fields, permission,
   and nested/repeated values.
6. SS-06 exposes the effective storage shape in manual/admin/projection surfaces.

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

Status: PLANNED

### Objective

Formalize existing `toStoreRecord` / `fromStoreRecord` as the storage API for the `EntityPersistent` family while preserving compatibility.

### Detailed Tasks

- [ ] Document `toStoreRecord` / `fromStoreRecord` as the formal storage API.
- [ ] Keep `toRecord` / `fromRecord` as compatibility bridges.
- [ ] Add compatibility specs for old-style implementations.
- [ ] Add behavior specs proving store paths use the store API when overridden.
- [ ] Do not introduce `toStorageRecord` in this phase.

### Expected Outcome

- Entity persistence APIs have explicit storage names.
- Existing generated/component implementations remain source-compatible.

### Guardrails

- Do not change physical DB record shape.
- Do not remove `RecordCodex` / `RecordEncoder` inheritance.

---

## SS-03: CNCF Storage Call Site Migration

Status: PLANNED

### Objective

Classify CNCF internal record use and migrate storage paths away from generic `toRecord` / `fromRecord` names.

### Detailed Tasks

- [ ] Migrate `EntityStore` create/load/save/update/search paths to explicit storage APIs where not already done.
- [ ] Migrate `EntityStoreSpace`, `UnitOfWorkInterpreter`, and runtime collection paths where they handle storage records.
- [ ] Leave admin/diagnostic/presentation records on presentation-specific APIs.
- [ ] Mark transitional record-path authorization call sites explicitly.
- [ ] Add regression specs for create/load/search/update after migration.

### Expected Outcome

- Storage call sites are visibly storage-oriented.
- Presentation/request/descriptor/debug records are not accidentally treated as persistence contracts.

### Guardrails

- Do not perform broad mechanical replacement of every `toRecord` call.
- Each migrated call site must be classified by purpose.

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
