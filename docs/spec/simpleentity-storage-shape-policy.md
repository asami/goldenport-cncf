# SimpleEntity Storage-Shape and Managed Attributes

Status: normative static contract

Architectural context is defined by
`docs/design/simpleentity-storage-shape-policy.md`.

## Scope

This specification defines the CNCF-managed `SimpleEntity` attribute catalog,
physical storage names, ownership, mutation effects, input protection, and the
separation between managed revision maintenance and OCC enforcement.

## Managed-Attribute Definition

A CNCF-managed attribute is standard Entity metadata whose authoritative
stored value is initialized, complemented, advanced, cleared, or protected by
CNCF. Component create/update records and patches MUST NOT directly control a
managed value. Component intent MUST cross an admitted framework operation,
after which CNCF derives the stored value from that intent and the
`ExecutionContext`.

Management MUST NOT imply that every field changes on every mutation.

## Canonical Attribute Catalog

The standard managed `SimpleEntity` storage shape MUST use:

| Concern | Logical field | Storage field |
| --- | --- | --- |
| identity | `id` | `id` |
| identity | `shortId` | `short_id` |
| creation audit | `createdAt` | `created_at` |
| creation audit | `createdBy` | `created_by` |
| update audit | `updatedAt` | `updated_at` |
| update audit | `updatedBy` | `updated_by` |
| logical lifecycle | `aliveness` | `aliveness` |
| logical lifecycle | `postStatus` | `post_status` |
| deletion lifecycle | `deletedAt` | `deleted_at` |
| deletion lifecycle | `deletedBy` | `deleted_by` |
| publication lifecycle | `publishAt` | `publish_at` |
| publication lifecycle | `publicAt` | `public_at` |
| publication lifecycle | `publishedBy` | `published_by` |
| tenancy scope | `tenantId` | `tenant_id` |
| tenancy scope | `organizationId` | `organization_id` |
| observability | `traceId` | `trace_id` |
| observability | `correlationId` | `correlation_id` |
| persistence generation | `revision` | `revision` |
| security identity | `ownerId` | `owner_id` |
| security identity | `groupId` | `group_id` |
| security identity | `privilegeId` | `privilege_id` |
| permission policy | typed owner/group/other rights | `permission` |

The permission value MUST be compact JSON text containing owner, group, and
other read/write/execute rights. Permission bits MUST NOT become separate
physical columns without an explicit later storage policy.

Fields not listed in this catalog MUST remain domain-owned unless another
normative CNCF contract explicitly admits them as managed metadata.

## Create Contract

An admitted create MUST:

- retain the admitted or generated `id` and derived `shortId`;
- initialize `createdAt`, `createdBy`, `updatedAt`, and `updatedBy`;
- initialize `postStatus` and `aliveness`;
- initialize security identity and permission from the admitted security
  context;
- initialize Embedded `revision` to `1`;
- record available trace and correlation context; and
- initialize publication fields only when the selected create profile or
  operation owns publication.

Create MUST NOT accept an application-provided managed revision.

## Ordinary Mutation Contract

A successful admitted `AlwaysWrite` persistent mutation MUST atomically:

- persist the admitted application change;
- replace `updatedAt` with the framework clock value;
- replace `updatedBy` with the admitted principal;
- record the available mutation trace/correlation context; and
- advance `revision` exactly once.

It MUST preserve `createdAt` and `createdBy`. It MUST preserve logical,
deletion, publication, tenancy, and security values unless the admitted
operation explicitly owns their change.

An admitted `WriteIfChanged` mutation whose normalized business state is equal
MUST publish no storage mutation. It MUST NOT change `updatedAt`, `updatedBy`,
trace/correlation metadata, or `revision`.

A failed, rejected, stale, or rolled-back mutation MUST change no application
or managed attribute. A read MUST change no managed attribute.

## Lifecycle Operation Contract

Soft delete MUST set `postStatus` to Archived, `aliveness` to Dead,
`deletedAt`, `deletedBy`, update audit/context metadata, and the next revision
in one admitted persistent mutation.

Restore MUST set `postStatus` to Draft, set `aliveness` to Alive, clear deletion
metadata, update audit/context metadata, and advance revision in one admitted
persistent mutation.

Publication and security values MUST change only through operations authorized
for those concerns. Ordinary component patches MUST NOT rewrite them.

## Revision Representation

Every persisted `SimpleEntity` MUST use Embedded representation and the
read-only logical/storage field `revision`.

An explicitly admitted non-`SimpleEntity` MAY use Detached representation and
physical managed field `cncf_revision`. Detached revision MUST NOT be treated
as a `SimpleEntity` attribute, alias, mirror, or fallback. One Entity MUST NOT
contain both managed representations.

Generated ordinary CRUD MUST use representation-neutral typed load and managed
save boundaries. Managed save MUST return the authoritative saved Entity and
MUST NOT require a business request parameter for framework revision.
Embedded, Detached, and unmanaged collections MUST retain their admitted
storage semantics through that common boundary. A generated adapter MUST use a
snapshot or Detached carrier only when its operation explicitly exposes
revision observation.

## Revision and Concurrency Separation

Revision maintenance and OCC enforcement MUST remain separate:

- `EntityConcurrencyPolicy.None` MUST advance managed revision without
  comparing an expected revision;
- explicit `Optimistic` mutation MUST use an atomic expected-revision
  compare-and-set contract; and
- Conditional Transition MUST retain its explicit atomic comparison,
  side-effect, rollback, and exactly-one-winner contract.

The ordinary `None + AlwaysWrite` steady-state path MUST NOT require a
target-record pre-read, `SELECT FOR UPDATE`, or mandatory authoritative record
readback merely to maintain managed attributes. The provider MUST apply the
application change, update audit/context metadata, and advance revision in the
same direct mutation.

Managed attributes therefore MUST NOT make the OCC execution path an implicit
default.

## Storage and Projection Boundary

`EntityPersistent.toStoreRecord` and `fromStoreRecord` own physical storage
conversion. View, admin, request, and diagnostic records MUST NOT define the
storage shape.

Managed attributes MAY be projected read-only where the surface contract
requires them. Application mutation inputs MUST omit or reject managed values.
Authorization MUST use typed security access rather than depending on
presentation or legacy record paths.

## Executable Evidence

Current behavioral evidence includes:

- `src/test/scala/org/goldenport/cncf/entity/SimpleEntityStorageShapePolicySpec.scala`;
- `src/test/scala/org/goldenport/cncf/entity/EntityManagedMutationSpec.scala`;
- `src/test/scala/org/goldenport/cncf/entity/EntityRevisionRepresentationSpec.scala`;
- `src/test/scala/org/goldenport/cncf/datastore/EntityRevisionProviderParitySpec.scala`; and
- `src/test/scala/org/goldenport/cncf/projection/EntityRevisionProjectionSpec.scala`.

Phase 50 PC-02 MUST add executable provider-path and statement-trace evidence
for the ordinary direct-update and optimistic compare-and-set requirements
before claiming those performance-visible clauses implemented.

## References

- `docs/design/simpleentity-storage-shape-policy.md`
- `docs/design/entity-conflict-and-conditional-transition.md`
- `docs/spec/entity-conflict-and-conditional-transition.md`
- `docs/phase/phase-50.md`
- `docs/phase/phase-50-checklist.md`
- `docs/journal/2026/07/2026-07-26-simpleentity-managed-attribute-catalog.md`
