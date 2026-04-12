# SimpleEntity DB Storage Shape Note

Date: 2026-04-12
Status: draft
Scope: CNCF internal persistence / SimpleEntity storage

## Context

This note explores a change in how `SimpleEntity` records should be stored in a
DB-backed datastore.

`EntityPersistent` should be treated as the DB/storage persistence boundary.
`EntityPersistent.toRecord` is therefore the right place to express the
storage-oriented record shape, not merely a domain display record.

For `SimpleEntity`, the storage shape matters because many framework-level
features need to inspect common management attributes without decoding the full
object graph:

- authorization;
- owner/group filtering;
- lifecycle filtering;
- search/list visibility;
- admin/dashboard display;
- operational diagnostics.

## Proposed Direction

Use `EntityPersistent` as the storage projection policy for `SimpleEntity`,
with rules that distinguish between:

1. management-oriented value objects;
2. permission metadata;
3. semantically independent value objects;
4. repeated value objects.

The default policy should be:

- expand management-oriented value objects into DB columns;
- compress/store `permission` as one encoded field;
- encode semantically independent value objects when they should not participate
  in ordinary DB-level filtering;
- encode repeated value objects, especially list-like structures such as
  `SalesLine`.

## Rationale

### Expand management value objects

Even when a concept is modeled as a Value Object, CNCF may need individual
attributes as first-class DB columns for management and framework behavior.

Examples:

- `owner_id`;
- `group_id`;
- `created_by`;
- `updated_by`;
- `post_status`;
- `aliveness`;
- timestamps;
- simple classification/status attributes.

These should be expanded so that authorization, visibility filtering, and admin
views can operate without decoding an opaque blob.

This keeps DB storage useful for framework-level concerns while preserving Value
Object modeling at the domain/object layer.

### Compress permission

`permission` is different from ordinary management attributes.

It is structurally compact, security-oriented, and likely to evolve as a single
policy object. Storing it as one encoded field avoids premature schema coupling
and avoids spreading permission semantics across many physical columns.

The access policy layer can decode permission when needed, or use a normalized
permission projection later if performance requires it.

### Encode semantically independent value objects

Some Value Objects are domain concepts rather than management metadata.

For example, a `SalesLine` is not just a set of scalar management fields. It is a
semantically independent line item. If it is stored inside an aggregate or
SimpleEntity as a value, the default DB representation should be encoded rather
than blindly expanded into parent columns.

This avoids:

- column explosion;
- ambiguous parent/child ownership in the physical schema;
- accidental query semantics over nested domain values;
- fragile migrations when the nested value object changes.

### Encode repeated value objects

Repeated Value Objects should generally be encoded unless they are promoted to an
independent entity/collection.

Examples:

- `Vector[SalesLine]`;
- repeated address/history/detail rows that are conceptually part of the parent
  value;
- nested detail values with no independent lifecycle.

If repeated values need independent DB-level query/update behavior, they should
be modeled as entities or collections instead of being flattened into the parent
`SimpleEntity` table by default.

## Classification Rule

A practical first rule can be:

| Value kind | Default DB storage | Reason |
| --- | --- | --- |
| Simple scalar attribute | column | query/display/update friendly |
| Management Value Object | expanded columns | framework inspection and filtering |
| Permission | encoded field | policy object; avoid schema spread |
| Independent Value Object | encoded field | preserve semantic boundary |
| Repeated Value Object | encoded field | avoid column explosion; promote to entity if queryable |
| Independent lifecycle object | separate entity/collection | not parent-owned value storage |

## EntityPersistent Boundary

Current code usage confirms that `EntityPersistent` is not a UI/display
projection.

It is used as the entity persistence codec and identity typeclass:

- `EntityStore` uses `toRecord` for create/save/update and `fromRecord` for
  load/search decode;
- startup entity import uses `fromRecord` to decode imported records;
- `UnitOfWork` and `ActionCallFeaturePart` carry `EntityPersistent` through
  entity operations;
- `EntityRealm` uses `id` for memory-realm identity;
- generated component bootstrap resolves generated `EntityPersistent`
  instances.

There are still current framework consumers of the record shape produced by
`toRecord`:

- `OperationAccessPolicy` evaluates read visibility/authorization from the
  record;
- entity collection search uses `toRecord` for visibility filtering;
- logical deletion checks inspect lifecycle fields from the record.

Therefore, changing the DB storage shape through `EntityPersistent` is the right
direction, but the resulting record shape must continue to expose framework
management fields needed by runtime policy until the current policy/security
record dependency is removed.

The important boundary is:

- `EntityPersistent.toRecord` is DB/storage-oriented;
- it is not a UI/display/domain projection;
- management and lifecycle fields needed by CNCF runtime can remain inspectable
  from its record shape;
- policy/security logic should not be modeled as a separate Record-form
  projection;
- if `permission` is compressed into one encoded field, the access policy layer
  should move to typed permission/security access instead of assuming expanded
  permission paths in `Record`.

## Record Projection Taxonomy

The current codebase has multiple record-producing mechanisms, but most of them
share the generic `toRecord` method name through core `RecordEncoder`,
`RecordCodex`, and `RecordPresentable`.

For this storage-shape work, the important distinction is not the method name
alone, but the record projection purpose.

Because `Record` is structurally untyped, mixing record purposes is a serious
program-structure error. A `toRecord` result must not be treated as a generic
interchangeable record unless its boundary and purpose are explicit.

| Purpose | Existing surface | Intended meaning |
| --- | --- | --- |
| Storage record | `EntityPersistent.toRecord` / `fromRecord` | DB/datastore persistence shape for an entity |
| Create storage record | `EntityPersistentCreate.toRecord` | create payload shape before framework complements id/metadata |
| Update storage record | `EntityPersistentUpdate.toRecord` | patch/change payload shape before update translation |
| Query record | `EntityPersistentQuery.toRecord`, directive `Query.toRecord` | query criteria or query directive representation |
| Import record | `EntityPersistent.fromRecord` via startup import | external seed record decoded into entity storage model |
| Runtime management record | currently `EntityPersistent.toRecord` | lifecycle/visibility fields inspected by runtime management logic |
| Presentation/response record | `RecordPresentable.toRecord`, admin/metrics/observability `toRecord` | API/admin/diagnostic display shape |
| Descriptor/config record | `RecordDecoder.fromRecord`, descriptor `toRecord` | component/subsystem metadata, not entity storage |
| Request record | `Request.toRecord(...)` | operation/action request parameter representation |

The storage work should avoid treating all `toRecord` outputs as equivalent.

Recommended naming and boundary rule:

- keep `EntityPersistent.toRecord` as the canonical entity storage record;
- treat `EntityPersistentCreate` and `EntityPersistentUpdate` as storage-adjacent
  create/update variants;
- do not use `RecordPresentable.toRecord` as an implicit storage contract unless
  it is explicitly wrapped by an `EntityPersistent` instance;
- do not use admin/metrics/observability/request records as persistence records;
- policy/security should not be added as a new Record-form projection; use typed
  policy/security objects or accessors instead.

Strict handling rule:

- every `toRecord` call site must be classified by purpose before reuse;
- do not pass presentation/admin/request/observability records into persistence
  or policy code;
- do not pass storage records into presentation code without an explicit
  presentation formatter/projection;
- do not infer policy/security semantics from a generic record unless the
  current code path is explicitly marked as transitional compatibility;
- new code should prefer purpose-specific names or wrapper types when a record
  crosses a boundary.

Candidate purpose-specific names for new or refactored APIs:

- `toStorageRecord`;
- `fromStorageRecord`;
- `toCreateRecord`;
- `toUpdateRecord`;
- `toRequestRecord`;
- `toPresentationRecord`;
- `toDescriptorRecord`.

Existing `EntityPersistent.toRecord` can remain as the inherited
`RecordEncoder` method, but documentation and call sites should treat it as
`toStorageRecord` semantically.

## EntityPersistent API Migration Candidate

Renaming the storage-side API from `toRecord` to `toStorageRecord` is likely
worth the migration cost.

Reason:

- `EntityPersistent` is CNCF-owned and already represents persistence;
- call sites are mostly inside CNCF entity/unit-of-work/runtime code and specs;
- the name `toStorageRecord` makes accidental reuse as presentation/request
  record visibly wrong;
- it gives generated code a clearer contract for SimpleEntity storage shape.

Recommended migration path:

1. Add explicit storage methods to `EntityPersistent`:

   ```scala
   trait EntityPersistent[E] extends Identified[E, EntityId] {
     def toStorageRecord(e: E): Record
     def fromStorageRecord(r: Record): Consequence[E]

     final def toRecord(e: E): Record = toStorageRecord(e)
     final def fromRecord(r: Record): Consequence[E] = fromStorageRecord(r)
   }
   ```

2. Apply the same direction to storage-adjacent variants:

   - `EntityPersistentCreate.toCreateRecord`;
   - `EntityPersistentUpdate.toUpdateRecord`;
   - `EntityPersistentQuery.toQueryRecord`, if query persistence remains needed.

3. Move CNCF internal call sites from `toRecord` to purpose-specific names.

4. Keep `toRecord` / `fromRecord` only as compatibility bridges while generated
   code and tests are migrated.

5. After generated code is updated, consider removing `RecordCodex` /
   `RecordEncoder` inheritance from `EntityPersistent` variants so the compiler
   no longer encourages generic `toRecord` use.

This is a source-level migration, so it should be driven by executable specs and
done separately from the actual SimpleEntity storage-shape change.

This means the near-term split should be:

1. keep management/lifecycle inspection on the storage record where DB filtering
   needs columns; and
2. move policy/security inspection away from ad hoc `Record` path lookup toward
   typed policy/security access.

The current `OperationAccessPolicy` record-path logic should be treated as an
implementation gap, not as a target architecture.

## Policy / Security Implementation Assessment

The current policy/security implementation has a practical reason, but not a
strong architectural necessity.

Practical reason:

- `UnitOfWorkInterpreter` can load an existing entity as a raw datastore
  `Record` before it has or wants a fully typed entity instance;
- `OperationAccessPolicy` therefore checks owner/group/privilege/rights through
  record paths such as `security_attributes.owner_id`;
- search result filtering reuses `EntityPersistent.toRecord` to avoid adding a
  separate security accessor surface.

This made the first implementation small and generic across entity types.

However, it is not a good target boundary:

- security and permission semantics are domain/framework concepts, not generic
  record-shape semantics;
- record path aliases (`security_attributes` vs `securityAttributes`) leak
  physical/serialized naming into authorization;
- compressed permission storage would make record-path authorization brittle;
- generated or handwritten entity code is a better place to provide typed
  access to owner/group/privilege/permission semantics.

Direction:

- keep raw-record authorization only as a bootstrap/transitional fallback;
- introduce typed security/permission access for SimpleEntity authorization;
- let `EntityPersistent` remain the DB/storage codec;
- do not define a new "policy Record" projection as the main abstraction.

## Possible Implementation Shape

Use `EntityPersistent.toRecord` as the storage projection stage:

```text
Entity object
  -> storage Record/columns (EntityPersistent.toRecord)
  -> DataStore record
```

For initial implementation, the storage-shape policy can be implemented in the
generated or handwritten `EntityPersistent` instance. The entity runtime
descriptor can still carry metadata that helps build or validate that instance.

Possible extension points:

- generated CML metadata marks management/security fields;
- generated metadata marks value object fields as `embedded`, `encoded`, or
  `entity_ref`;
- default SimpleEntity policy expands known framework fields and encodes unknown
  complex/repeated values;
- component-specific overrides can refine the default later.

## Permission Handling

`permission` should be stored as a compressed/encoded field by default.

Open decisions:

- exact format: JSON, compact string, binary-safe encoded text;
- whether to expose derived read/write/execute bits as optional generated indexes;
- whether DB-level filtering should ever depend on permission bits directly;
- how to migrate existing expanded permission-like records.

Near-term recommendation:

- store permission as encoded JSON-like value;
- keep authorization logic reading through the canonical record/security model;
- add derived/indexed permission columns only if a concrete performance need
  appears.

## SalesLine-Like Values

A `SalesLine`-like Value Object should be encoded when stored as part of a parent
SimpleEntity or aggregate value.

If business requirements need querying or updating individual lines independently,
then `SalesLine` should become one of:

- an entity collection;
- an aggregate member with explicit storage mapping;
- a child table/collection managed by aggregate storage policy.

It should not be flattened into arbitrary parent columns by default.

## Open Questions

1. Which framework fields are the initial built-in management expansion set?
2. Should `permission` be encoded as JSON text or a compact permission string?
3. Which generated metadata should guide the `EntityPersistent` storage shape?
4. How should migration from existing records be handled?
5. Do we need a storage-shape executable spec before implementation?

## Initial Recommendation

Adopt the policy direction by making the `EntityPersistent` instance produce the
DB/storage-oriented record shape directly.

This gives CNCF a stable internal persistence boundary:

- `EntityPersistent.toRecord` is DB/storage-oriented;
- UI/display/domain projections should use separate projection mechanisms;
- management fields stay queryable;
- permission stays compact;
- nested/repeated domain Value Objects stay semantically bounded.
