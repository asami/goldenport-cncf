# Phase 17 — SimpleEntity Storage Shape

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 17.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Treat `EntityPersistent` as the canonical entity storage boundary.
- Separate storage records from request, presentation, descriptor, diagnostic,
  and admin records.
- Formalize existing storage-oriented APIs such as `toStoreRecord` and
  `fromStoreRecord` after the record-purpose taxonomy is fixed.
- Define SimpleEntity storage-shape rules for management fields, security
  fields, permission, independent value objects, and repeated value objects.
- Move SimpleEntity authorization away from ad hoc `Record` path semantics toward
  typed security/permission access.
- Keep DB/store behavior compatible while tightening the boundary.

Current semantic direction:

- `EntityPersistent` is DB/storage-oriented, not a UI/display projection.
- `EntityPersistentCreate`, `EntityPersistentUpdate`, and
  `EntityPersistentQuery` are storage-adjacent variants with purpose-specific
  record shapes.
- Management and lifecycle fields remain inspectable for DB filtering,
  lifecycle behavior, admin visibility, and operational diagnostics.
- `permission` should become compact/encoded storage by default, with typed
  authorization access above it.
- Semantically independent value objects and repeated value objects should be
  encoded by default unless promoted to entities, collections, aggregate members,
  or child tables by explicit storage policy.
- Existing `toRecord` / `fromRecord` compatibility is transitional and must not
  be treated as the target architecture.

## 3. Non-Goals

- No broad persistence engine replacement.
- No DB migration framework in this phase.
- No Blob payload storage; Blob management is a separate candidate.
- No new UI/admin feature beyond exposing storage-shape metadata where useful.
- No permission model redesign beyond the access boundary required to stop using
  generic `Record` path lookup.
- No forced rewrite of every presentation/diagnostic `toRecord` call.

## 4. Current Work Stack

- A (DONE): SS-01 — Define Record Purpose Taxonomy and boundary rules.
- B (DONE): SS-02 — Formalize `EntityPersistent.toStoreRecord/fromStoreRecord` as the storage API.
- C1 (DONE): SS-03A — Migrate DB Record boundary call sites to store APIs.
- C2 (DONE): SS-03B — Add and use the View Record boundary API for entity presentation.
- C3 (DONE): SS-03C — Classify remaining Logic Record call sites for typed-access migration.
- D (DONE): SS-04 — Add typed SimpleEntity security/permission access for authorization.
- E1 (DONE): SS-05A — Fix SimpleEntity storage-shape policy in design/spec.
- E1a (DONE): SS-05A-A — Fix storage-shape classification order and rules.
- E2 (DONE): SS-05B — Implement SimpleEntity storage-shape policy.
- E3a (DONE): SS-05C-A — Add typed security override regression specs.
- E3b (DONE): SS-05C-B — Add runtime storage-shape coverage.
- E3c (DONE): SS-05C-C — Add value object storage encoding coverage.
- E3 (PLANNED): SS-05C — Add storage-shape executable coverage.
- F (PLANNED): SS-06 — Expose storage-shape metadata in manual/admin/projection surfaces.

Current note:

- Phase 16 is closed and remains the auth/session/Cwitter baseline.
- Phase 17 starts from the record-purpose taxonomy before changing APIs or
  physical storage shape.
- `docs/journal/2026/04/simpleentity-db-storage-shape-note.md` is the source
  exploration note for this phase.
- SS-01 is complete when `docs/design/record-purpose-taxonomy.md` defines the
  boundary and Phase 17 work order follows it.

## 5. Development Items

- [x] SS-01: Define Record Purpose Taxonomy and boundary rules.
- [x] SS-02: Formalize `EntityPersistent.toStoreRecord/fromStoreRecord` as the storage API.
- [x] SS-03A: Migrate DB Record boundary call sites to store APIs.
- [x] SS-03B: Add and use the View Record boundary API for entity presentation.
- [x] SS-03C: Classify remaining Logic Record call sites for typed-access migration.
- [x] SS-04: Add typed SimpleEntity security/permission access for authorization.
- [x] SS-05A: Fix SimpleEntity storage-shape policy in design/spec.
- [x] SS-05A-A: Fix storage-shape classification order and rules.
- [x] SS-05B: Implement SimpleEntity storage-shape policy.
- [x] SS-05C-A: Add typed security override regression specs.
- [x] SS-05C-B: Add runtime storage-shape coverage.
- [x] SS-05C-C: Add value object storage encoding coverage.
- [ ] SS-05C: Add storage-shape executable coverage.
- [ ] SS-06: Expose storage-shape metadata in manual/admin/projection surfaces.

## 6. Next Phase Candidates

- Builtin Blob management component.
- Search/index planning after storage-shape metadata becomes explicit.
- DB migration tooling if storage-shape changes require managed upgrades.

## 7. References

- `docs/journal/2026/04/simpleentity-db-storage-shape-note.md`
- `docs/journal/2026/04/simpleentity-default-authorization-model-note.md`
- `docs/journal/2026/04/entity-authorization-implementation-2026-04-13.md`
- `docs/design/record-purpose-taxonomy.md`
- `docs/design/simpleentity-storage-shape-policy.md`
- `src/main/scala/org/goldenport/cncf/entity/EntityPersistent.scala`
- `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala`
- `src/main/scala/org/goldenport/cncf/security/OperationAccessPolicy.scala`
