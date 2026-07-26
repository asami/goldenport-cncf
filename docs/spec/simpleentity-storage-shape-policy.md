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

The framework MUST select the mutation path according to the requested
semantics and provider capabilities:

- ordinary `None + AlwaysWrite` MUST use direct provider mutation when the
  provider declares that capability;
- explicit `Optimistic` mutation MUST use provider-native compare-and-set when
  the provider declares that capability;
- `Managed` optimistic mutation without a transport observation MUST obtain
  the authoritative current revision once and supply it to the provider-native
  compare-and-set contract;
- when UnitOfWork authorization or transition validation has already resolved
  the current target, that resolved present-or-missing base MUST remain the
  mutation attempt base and MUST NOT be replaced by a later EntityStore load;
- `WriteIfChanged`, content-bearing mutation, atomic side effects, and an
  unsupported native capability MUST retain the guarded provider contract;
- a caller that requires an authoritative Entity result MUST request provider
  readback explicitly; and
- an acknowledgment-only success MUST NOT cause a mandatory datastore
  readback.

Native success MUST reconcile resident Entity state from provider-authoritative
readback where that result is requested. A stale compare-and-set MUST evict
resident state before returning its structured stale-revision failure.
Provider rejection because the mutation target is missing or logically deleted
MUST likewise evict resident Entity state.
Unsuccessful mutation MUST NOT publish View invalidation as if a mutation had
committed.

Managed attributes therefore MUST NOT make the OCC execution path an implicit
default.

## Storage and Projection Boundary

`EntityPersistent.toStoreRecord` and `fromStoreRecord` own physical storage
conversion. View, admin, request, and diagnostic records MUST NOT define the
storage shape.

An ordinary scalar `EntityId` retains the logical collection name but may not
retain the complete collection namespace expected by the runtime collection.
For the current release, the CNCF persistence boundary MUST decode the physical
Record exactly once and MUST validate that the decoded and requested logical
collection names are equal. A different logical collection name MUST fail as a
structured collection-contract error. CNCF MUST NOT rewrite a custom codec's
physical input and invoke `fromStoreRecord` a second time.

The current release assumes that a runtime does not install multiple Entity
collections with the same logical name. Exact collection identity restoration,
same-name ambiguity handling, and compatibility policy for generated and
custom codecs are Phase 51 work.

Aggregate create MUST canonicalize a typed `EntityId` and a scalar `EntityId`
to the selected runtime collection before persistence. This prevents generated
model placeholder collection namespaces from becoming the stored collection
identity without adding load-time inference or application-specific fallback.

Managed attributes MAY be projected read-only where the surface contract
requires them. Application mutation inputs MUST omit or reject managed values.
Authorization MUST use typed security access rather than depending on
presentation or legacy record paths.

## Executable Evidence

Current behavioral evidence includes:

- `src/test/scala/org/goldenport/cncf/entity/SimpleEntityStorageShapePolicySpec.scala`;
- `src/test/scala/org/goldenport/cncf/entity/EntityManagedMutationSpec.scala`;
- `src/test/scala/org/goldenport/cncf/entity/EntityPersistentCollectionIdentitySpec.scala`;
- `src/test/scala/org/goldenport/cncf/entity/EntityRevisionRepresentationSpec.scala`;
- `src/test/scala/org/goldenport/cncf/datastore/EntityRevisionProviderParitySpec.scala`;
- `src/test/scala/org/goldenport/cncf/datastore/EntityMutationProviderContractSpec.scala`;
- `src/test/scala/org/goldenport/cncf/datastore/EntityNativeMutationProviderSpec.scala`;
- `src/test/scala/org/goldenport/cncf/datastore/SqliteEntityMutationStatementTraceSpec.scala`;
- `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkPlainMutationProviderParitySpec.scala`;
- `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedMutationSpec.scala`; and
- `src/test/scala/org/goldenport/cncf/projection/EntityRevisionProjectionSpec.scala`.

Phase 50 PC-02 executable evidence fixes:

- in-memory, SQLite, and opt-in live MySQL direct/CAS revision, stale-writer,
  committed-state, and resident-cache parity;
- one successful target `UPDATE` and no target `SELECT` for direct
  acknowledgment;
- one revision-qualified target `UPDATE` and no target `SELECT` for successful
  compare-and-set acknowledgment;
- diagnostic target read only after a zero-row native mutation; and
- the retained guarded SQLite `SELECT` / `UPDATE` / `SELECT` sequence.

Representative elapsed-time samples are informational evidence only. The
specification does not define a wall-clock threshold or promise that one local
sample is faster than another.

## References

- `docs/design/simpleentity-storage-shape-policy.md`
- `docs/design/entity-conflict-and-conditional-transition.md`
- `docs/spec/entity-conflict-and-conditional-transition.md`
- `docs/phase/phase-50.md`
- `docs/phase/phase-50-checklist.md`
- `docs/journal/2026/07/2026-07-26-simpleentity-managed-attribute-catalog.md`
