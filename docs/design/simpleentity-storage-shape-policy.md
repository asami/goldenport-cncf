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
| management field | expanded column | framework filtering, lifecycle handling, revision concurrency, admin, and diagnostics |
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

## CNCF-Managed SimpleEntity Attribute Catalog

CNCF-managed attributes are standard Entity metadata whose authoritative
storage value is initialized, complemented, advanced, or protected by CNCF.
They are not ordinary component-owned patch fields. Management does not mean
that every field changes on every mutation.

The built-in expanded set is:

| Concern | Logical fields | Target storage names | Management rule |
| --- | --- | --- | --- |
| identity | `id`, `shortId` | `id`, `short_id` | Admitted or generated at create; stable afterward |
| creation audit | `createdAt`, `createdBy` | `created_at`, `created_by` | Set at create and preserved |
| update audit | `updatedAt`, `updatedBy` | `updated_at`, `updated_by` | Replaced on an admitted persistent mutation |
| logical lifecycle | `aliveness`, `postStatus` | `aliveness`, `post_status` | Initialized at create and changed by admitted lifecycle operations |
| deletion lifecycle | `deletedAt`, `deletedBy` | `deleted_at`, `deleted_by` | Set by soft delete and cleared by restore |
| publication lifecycle | `publishAt`, `publicAt`, `publishedBy` | `publish_at`, `public_at`, `published_by` | Set by an admitted publication profile or operation |
| tenancy scope | `tenantId`, `organizationId` | `tenant_id`, `organization_id` | Supplied by the admitted execution/storage scope |
| observability | `traceId`, `correlationId` | `trace_id`, `correlation_id` | Captures the admitted mutation context when available |
| persistence generation | `revision` | `revision` | Initialized to `1` and advanced once per successful persistent mutation |
| security identity | `ownerId`, `groupId`, `privilegeId` | `owner_id`, `group_id`, `privilege_id` | Initialized from admitted security context and changed only through an authorized security route |
| permission policy | typed rights | `permission` | Stored as compact owner/group/other JSON |

These fields stay queryable because CNCF runtime behavior, authorization,
admin surfaces, lifecycle processing, diagnostics, and concurrency handling
need them without decoding unrelated domain payloads.

`revision` is framework-managed persistence-generation metadata. OCC may use
it, but maintaining revision does not by itself enable OCC. An ordinary
`EntityConcurrencyPolicy.None` mutation still advances revision without an
expected-revision comparison. An explicit `Optimistic` mutation uses revision
as a compare-and-set guard. Conditional Transition retains its stronger atomic
comparison contract.

Application create/update records and patches cannot directly write or clear
CNCF-managed fields. The application may provide intent through an admitted
operation, such as create identity, publish, soft delete, restore, security
change, or strict revision observation; CNCF derives the stored management
values from that intent and the `ExecutionContext`.

An explicitly admitted non-`SimpleEntity` model may instead use Detached
revision storage in `cncf_revision`. Detached representation is an extension
surface, not a `SimpleEntity` attribute or fallback shape. Missing managed
revision, dual managed representation, and implicit Detached admission fail
deterministically. An application-owned `revision` field on a Detached domain
model remains ordinary data and is not interpreted as Embedded revision.

Generated ordinary CRUD uses the representation-neutral typed boundaries
`entity_load` and `entity_save_managed`. The latter returns the authoritative
saved Entity after CNCF applies the admitted revision policy. The same contract
therefore works for Embedded `SimpleEntity`, explicitly Detached
non-`SimpleEntity`, and unmanaged non-`SimpleEntity` collections without
requiring application logic to transport revision metadata. Snapshot and
Detached-carrier APIs remain explicit extension surfaces for workflows that
must observe a revision.

Domain-specific classification fields remain ordinary scalar attributes unless
the component declares a more specific storage policy. Other
`simplemodeling-model` value attributes, such as resource activation or
publication scheduling values not listed above, are not automatically
CNCF-managed merely because `SimpleObject` exposes their value objects.

## Managed Mutation Effects

Create initializes identity, creation/update audit, logical lifecycle,
security, revision, and available observability metadata. A publication create
profile also initializes its publication fields.

An admitted `AlwaysWrite` update changes the application state and, in the same
persistent mutation:

- replaces `updatedAt` and `updatedBy`;
- records the available trace/correlation context; and
- advances `revision` exactly once.

`createdAt` and `createdBy` remain unchanged. Logical, deletion, publication,
tenancy, and security attributes change only when the admitted operation owns
that concern.

An admitted `WriteIfChanged` whose normalized business state is equal is a
no-op. It changes neither application state nor `updatedAt`, `updatedBy`,
trace/correlation metadata, or `revision`. Failed, rejected, stale, and
rolled-back mutations likewise publish no management-field change.

Managed-field maintenance is independent of persistence-path selection.
Phase 50 PC-02 separates the provider paths:

- ordinary `None + AlwaysWrite` uses direct provider mutation when supported;
- explicit optimistic mutation uses provider-native compare-and-set when
  supported;
- managed optimistic mutation obtains the authoritative current revision once
  when no transport observation is present, then supplies that revision to the
  provider-native compare-and-set;
- authorization or transition validation that already resolves the current
  target passes a typed present-or-missing managed base to EntityStore; this
  prevents a later reload from changing the state against which the mutation
  was admitted;
- `WriteIfChanged`, content-bearing mutation, side-effect-bearing mutation,
  and unsupported providers retain the guarded provider contract; and
- Conditional Transition retains its dedicated multi-record atomic provider
  contract.

The native provider advances managed revision in the same mutation as the
business change. Acknowledgment-only execution does not require target
pre-read, lock-read, or post-success readback. Record/snapshot execution
requests provider-authoritative readback and installs that result directly
into resident Entity state. Stale compare-and-set evicts resident state so a
subsequent read cannot reuse the rejected snapshot. A provider-reported missing
or logically deleted mutation target also evicts resident Entity state. Failed
mutation does not invalidate View state as if it had committed.

UnitOfWork may still load the current Entity when user-permission
authorization or a non-noop transition-validation hook requires authoritative
object state. Such a load is policy work and establishes the managed mutation
base for that attempt, including an authoritative missing result. EntityStore
reuses it rather than reloading a newer target state. A load performed only
because the base remains unresolved is the sole managed optimistic pre-read.

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
- The current persistence boundary decodes a stored value exactly once and
  validates its logical collection name against the requested collection.
  Different logical collection names are rejected.
- The current release assumes that one runtime does not install multiple
  collections with the same logical name. Complete collection identity
  restoration and same-name ambiguity handling are deferred to Phase 51.
- CNCF does not rewrite a physical Record or invoke a custom codec a second
  time to compensate for scalar `EntityId` namespace loss.
- Aggregate create canonicalizes either a typed `EntityId` or its scalar form
  to the selected runtime collection before persistence.
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

- `docs/spec/simpleentity-storage-shape-policy.md`
- `docs/design/record-purpose-taxonomy.md`
- `docs/phase/phase-17.md`
- `docs/phase/phase-17-checklist.md`
- `docs/journal/2026/04/simpleentity-db-storage-shape-note.md`
- `docs/journal/2026/07/2026-07-26-simpleentity-managed-attribute-catalog.md`
