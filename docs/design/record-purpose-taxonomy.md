# Record Purpose Taxonomy

## Purpose

`Record` is a structural data container. It is not a single semantic contract.

CNCF code must not treat every `toRecord` / `fromRecord` result as
interchangeable. A record crossing a boundary must have an explicit purpose:
storage, presentation, logic, mutation, query, request, descriptor, or
diagnostic.

This taxonomy is the first Phase 17 boundary before changing SimpleEntity
storage shape.

## Top-Level Categories

The detailed taxonomy has eight purposes. At the design level these collapse
into three categories. Six of the eight detailed purposes are contextual
Property Records: they are property-shaped records used in different runtime,
request, descriptor, diagnostic, mutation, and query contexts.

Storage and presentation records are also property-shaped in a broad sense, but
they are separated because they are CNCF-external integration contracts:

- Storage Record crosses the CNCF boundary into a datastore/DB.
- Presentation Record crosses the CNCF boundary into browser/API/manual
  consumers.

The remaining six Property Record purposes stay inside CNCF-controlled
interpretation contexts unless explicitly converted at a boundary.

| Category | Role | Included purposes |
| --- | --- | --- |
| DB Record | Physical persistence representation used for datastore roundtrip. | Storage Record |
| Property Record | Context-specific property representation used to express mutation, query, runtime logic, request, metadata, or diagnostics. | Mutation Record, Query Record, Logic Record, Request Record, Descriptor Record, Diagnostic Record |
| View Record | Projection intended for human/API visibility. | Presentation Record |

The categories are intentionally separate:

- DB Records must not be shaped by screen rendering needs.
- Property Records must not depend on accidental DB physical paths.
- Property Records must not be persisted or displayed without explicit
  conversion to a Storage Record or View Record.
- View Records must not become persistence or authorization inputs.
- Mutation and Query Records are DB-adjacent Property Records, but they are not
  the stored entity record itself.
- Diagnostic Records are observability/debug Property Records, but they are not
  authoritative domain, storage, or authorization data.
- Logic Records are transitional where runtime logic inspects raw Record paths;
  target code should move to typed accessors.

## Taxonomy

| Purpose | Owner / typical surface | Meaning |
| --- | --- | --- |
| Storage Record | `EntityPersistent` | DB/datastore persistence shape for an entity. |
| Mutation Record | `EntityPersistentCreate`, `EntityPersistentUpdate` | Property changes requested by create/update before runtime complement/translation. |
| Query Record | `EntityPersistentQuery`, `Query` | Property criteria for search/filter/sort, not persisted entity data. |
| Logic Record | runtime logic, authorization, lifecycle, working-set policy | Property view used by runtime logic. Prefer typed accessors for new code. |
| Presentation Record | `EntityPersistent.toViewRecord`, Web/admin/API/manual response projections | Human/API display shape. Must not be used as persistence input without explicit conversion. |
| Request Record | operation/action request projection | Request property shape. Must not be persisted as entity data directly. |
| Descriptor Record | component/subsystem/config descriptors | Metadata/config property shape. Not entity storage. |
| Diagnostic Record | logs, debug panels, error envelopes, raw admin details | Operational diagnostic property shape. Not authoritative domain or storage data. |

## Logic Record Classification

Logic Records are temporary internal property views used by framework logic while
typed accessors are being introduced. They are not DB Records and must not be
treated as presentation output.

Phase 17 classifies remaining Logic Record use as follows:

| Logic context | Current shape | Target |
| --- | --- | --- |
| Authorization | entity or aggregate `toRecord` passed to authorization checks | typed security/permission access in SS-04 |
| Lifecycle | `deletedAt`, `postStatus`, `aliveness`, and audit fields read from a generic record | typed lifecycle access in SS-05 |
| Working-set policy | residency policy reads entity properties from a generic record | typed policy input/storage-shape policy in SS-05 |
| Identity lookup | runtime lookup reads management identity such as `shortid` from a generic record | typed management-field access in SS-05 |
| Aggregate operation | aggregate/member records are assembled from generic record projections | aggregate projection boundary review in SS-04/SS-05 |

## Boundary Rules

- Storage paths must use `EntityPersistent` storage semantics.
- Presentation/admin/request/diagnostic records must not be passed into
  persistence or policy code unless explicitly converted.
- Storage records must not be presented directly without an explicit presentation
  formatter/projection.
- Logic that needs entity management/security data should move toward typed
  accessors; raw `Record` path lookup is transitional compatibility.
- Descriptor/config records are configuration contracts and must not be reused as
  runtime entity records.
- Query records represent criteria, not stored entity state.

## Phase 17 Consequences

Phase 17 implementation proceeds in this order:

1. Define this taxonomy and use it as the design contract.
2. Formalize existing `EntityPersistent.toStoreRecord` / `fromStoreRecord` as
   the DB Record API in SS-02.
3. Migrate CNCF DB Record boundary call sites to store APIs in SS-03A.
4. Add and apply the View Record boundary API in SS-03B.
5. Classify remaining Logic Record call sites for typed-access migration in
   SS-03C.
6. Replace SimpleEntity authorization record-path assumptions with typed
   security/permission access in SS-04.
7. Implement SimpleEntity storage-shape rules in SS-05.
8. Expose storage-shape decisions in manual/admin/projections in SS-06.

## Compatibility

Existing `toRecord` / `fromRecord` methods remain compatibility surfaces during
migration. Their use is acceptable only when the purpose is known and documented
by the surrounding API.

`toStoreRecord` / `fromStoreRecord` are the formal DB Record API names for the
`EntityPersistent` family. Phase 17 does not introduce `toStorageRecord`.

`toRecord` / `fromRecord` may remain the compatibility bridge required by
`RecordCodex` / `RecordEncoder`, but code crossing the datastore boundary should
prefer the explicit store names.

`toViewRecord` is the formal View Record API for entity presentation boundaries.
It may delegate to `EntityDisplayable.toDisplayRecord` when an entity supplies a
view-specific projection, and otherwise falls back to compatibility `toRecord`
with optional field filtering.

## References

- `docs/phase/phase-17.md`
- `docs/phase/phase-17-checklist.md`
- `docs/journal/2026/04/simpleentity-db-storage-shape-note.md`
- `src/main/scala/org/goldenport/cncf/entity/EntityPersistent.scala`
