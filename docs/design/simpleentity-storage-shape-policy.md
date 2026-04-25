# SimpleEntity Storage-Shape Policy

## Purpose

This document defines the target DB storage shape for SimpleEntity records.

It is part of Phase 17 SS-05A. SS-05A fixes the policy only; it does not add a
runtime API, change generated code, or migrate existing records.

The policy depends on the Record purpose taxonomy: Storage Records are DB
contracts, View Records are presentation contracts, and internal Property
Records must not be used as accidental storage contracts.

## Default Storage Shape

SimpleEntity storage is policy-driven. The default shape is:

| Model value | Default DB storage | Reason |
| --- | --- | --- |
| scalar attribute | column | ordinary filtering, sorting, update, and display support |
| management field | expanded column | framework filtering, lifecycle handling, admin, and diagnostics |
| security identity | expanded column | owner/group/privilege checks and operational inspection |
| permission rights | compact JSON text in `permission` | permission is a policy object, not a set of stable physical columns |
| independent value object | encoded JSON text | preserves domain boundary and avoids accidental query semantics |
| repeated value object | encoded JSON array text | avoids column explosion and ambiguous child ownership |
| independent lifecycle child | entity, collection, or aggregate member | parent-owned value storage is not enough when lifecycle/query/update is independent |

A component may later choose explicit storage policy to promote an encoded value
into an entity, collection, aggregate member, or child table. Without an explicit
policy, nested domain values are encoded, not flattened.

## Classification Rules

Storage-shape classification is resolved in this order:

1. CNCF management and security identity fields;
2. permission rights;
3. scalar domain attributes;
4. independent single value objects;
5. repeated value objects;
6. independent lifecycle children or promoted children.

Earlier categories win over later categories. For example, `ownerId` is a
security identity column even though it is scalar-shaped, and permission rights
are compact policy data rather than expanded scalar columns.

The classification rules are:

| Classification | Rule | Default storage |
| --- | --- | --- |
| CNCF management/security identity field | Required by DB filtering, lifecycle handling, admin, diagnostics, runtime policy, or authorization identity lookup | expanded column |
| Permission rights | Owner/group/other read/write/execute policy bits | compact JSON text in `permission` |
| Scalar domain attribute | Domain-owned scalar value without independent lifecycle | ordinary column |
| Independent single value object | Domain value object owned by the parent and not independently queried/updated | encoded JSON text |
| Repeated value object | Parent-owned list/sequence of value objects | encoded JSON array text |
| Independent lifecycle child / promoted child | Value needs independent query, update, lifecycle, ownership, or aggregate membership | entity, collection, aggregate member, or explicit child storage |

JSON text encoding is a complex-value container decision. It is not a fallback
for unsupported scalar types. `Instant`, identifiers, date/time values, and
other typed scalars must be supported as typed scalar storage or fail
deterministically. They must not silently degrade to `String`.

## Built-In Expanded Fields

The initial built-in management expansion set is:

| Group | Logical fields | Target storage names |
| --- | --- | --- |
| identity | `id`, `shortId` | `id`, `short_id` |
| lifecycle/audit | `createdAt`, `updatedAt`, `createdBy`, `updatedBy` | `created_at`, `updated_at`, `created_by`, `updated_by` |
| logical state | `aliveness`, `postStatus`, `deletedAt`, `deletedBy` | `aliveness`, `post_status`, `deleted_at`, `deleted_by` |
| security identity | `ownerId`, `groupId`, `privilegeId` | `owner_id`, `group_id`, `privilege_id` |

These fields stay queryable because CNCF runtime behavior, admin surfaces, and
operational diagnostics need them without decoding unrelated domain payloads.

This list is intentionally limited. Domain-specific classification fields are
ordinary scalar attributes unless the component declares a more specific storage
policy.

## Permission Storage

`org.simplemodeling.model.value.SecurityAttributes` remains the canonical
runtime permission/security model.

The target DB shape stores owner/group/other rights as compact JSON text in the
`permission` field. The permission JSON represents:

- owner read/write/execute;
- group read/write/execute;
- other read/write/execute.

The permission bits are not expanded into separate DB columns by default.
Derived/index columns for permission bits may be added later only for a concrete
query or performance requirement.

Legacy `securityAttributes` and `security_attributes.rights` structures are
compatibility input shapes. They are not the target SimpleEntity storage shape.
Authorization must continue to use typed security access, not record-path
permission expansion.

## Nested Value Storage

Independent domain value objects are encoded as JSON text in the parent storage
record. Repeated value objects are encoded as JSON array text.

Examples:

- an address value owned by an account record is encoded unless address fields
  are explicitly modeled as queryable scalar fields;
- a `SalesLine`-like value inside an order is encoded unless line items need
  independent query/update/lifecycle behavior;
- line items that need independent behavior must be modeled as an entity,
  collection, aggregate member, or explicit child storage policy.

JSON text encoding is a complex-value container format. It is not a fallback for
unknown scalar types. Date/time, `Instant`, identifiers, and other typed scalar
values must be supported by the model/storage layer or fail deterministically;
they must not silently degrade to `String` because storage policy chose JSON for
nested values.

## Boundary Rules

- `EntityPersistent.toStoreRecord` / `fromStoreRecord` own the DB storage shape.
- `toViewRecord` and admin/manual projections must not drive DB shape.
- Logic that needs permission must use typed security access. It must not depend
  on expanded permission record paths.
- Logic that needs lifecycle, state, or working-set timestamps may use expanded
  management columns until SS-05B adds stronger typed access where needed.
- Descriptor, request, diagnostic, and presentation records must be converted
  explicitly before crossing into storage.
- Existing records are compatibility data; migration is not part of SS-05A.

## SS-05C Coverage Targets

SS-05C must add executable coverage for:

- classification order is respected;
- management fields are expanded into target storage names;
- permission is stored as compact JSON text in `permission`;
- typed authorization still works when permission is compact;
- scalar domain attributes remain ordinary columns;
- independent value object storage is encoded JSON text;
- repeated value object storage is encoded JSON array text;
- entity/collection/aggregate-member children are not flattened into the parent
  storage record;
- unsupported typed scalar values do not fall back to `String`.

SS-05C should keep the implementation source-compatible unless a later plan
explicitly authorizes a breaking generated-code migration.

## References

- `docs/design/record-purpose-taxonomy.md`
- `docs/phase/phase-17.md`
- `docs/phase/phase-17-checklist.md`
- `docs/journal/2026/04/simpleentity-db-storage-shape-note.md`
