# Entity Authorization Model

Status: implemented baseline  
Date: 2026-04-13

## Purpose

This document records the implemented CNCF entity authorization model.

The model separates:

- entity object permissions;
- service/internal execution authority;
- business relation based authorization;
- coarse-grained entity and service classifications.

Architecturally, the model is ABAC-centered. RBAC-style role checks,
ReBAC-style relation checks, and DAC-style owner/group/other permission checks
are treated as specialized evaluation patterns connected through subject,
entity, operation, application, and environment attributes. They are not
separate peer authorization layers beside ABAC.

The model is implemented at the `UnitOfWork` / internal DSL boundary. Operation
and descriptor metadata are inputs to that boundary, but application logic should
not perform ad hoc entity authorization checks.

## Runtime Chokepoint

Entity access authorization is enforced through `UnitOfWorkInterpreter` and
`OperationAccessPolicy`.

The canonical authorization carrier is `UnitOfWorkAuthorization`.

It carries:

- `resourceFamily`;
- `resourceType`;
- `collectionName`;
- `targetId`;
- `accessKind`;
- optional operation `access`;
- declared `entityNames`;
- derived or explicit `accessMode`;
- derived or explicit `relationRules`.

The supported entity access kinds are:

- `create`;
- `read`;
- `update`;
- `delete`;
- `search/list`.

Search/list also performs result visibility filtering for user-permission access.

## Access Modes

Entity authorization distinguishes these modes:

- `UserPermission`;
- `ServiceInternal`;
- `System`.

`UserPermission` checks object-side security attributes and relation rules.

`ServiceInternal` is for operations performed inside a service boundary. It does
not reinterpret entity owner/group/other permissions. It is intended for cases
such as `SalesOrderService` updating `SalesOrder` records it manages.

`System` is for framework/system work such as migration, indexing, projection, or
administration tasks. It also bypasses owner/group/other permissions.

Neither `ServiceInternal` nor `System` gives special meaning to the entity
`execute` permission bit.

## Object-Side Permissions

Object-side permissions are represented by
`org.simplemodeling.model.value.SecurityAttributes`.

The CNCF side uses the simplemodeling-model type directly. CNCF does not define a
parallel security attributes model.

The rights model is:

- owner permissions;
- group permissions;
- other permissions;
- each with `read`, `write`, and `execute`.

`execute` is only an execute permission for executable resources. It is not used
for Unix-like special behavior such as setuid, setgid, or sticky-bit semantics.
Special behavior must be added as explicit model/policy fields when needed.

Default owner permissions use read/write without execute for ordinary data:

```text
owner.read = true
owner.write = true
owner.execute = false
```

For business records, the default is private:

```text
group.read = false
group.write = false
group.execute = false
other.read = false
other.write = false
other.execute = false
```

For CMS/public-content entities, `other.read` can be enabled through a profile or
application policy.

## Create Defaults

Entity creation defaults are supplied through `EntityCreateDefaultsPolicy`,
available from `RuntimeContext.entityCreateDefaultsPolicy`.

The default policy sets:

- `id`;
- `name`;
- `createdAt`;
- `createdBy`;
- `updatedAt`;
- `updatedBy`;
- `postStatus = Published`;
- `aliveness`;
- `securityAttributes`;
- trace/correlation ids;
- publication timestamps when a publication-like profile is used.

Supported public-read profiles include:

- `public-read`;
- `publication`;
- `cms`;
- `public-content`.

Without those profiles, the default security attributes are private-owned.

Collection-specific defaults can be installed through
`EntityCreateDefaultsPolicy.byCollectionName`.

## Classification Axes

The implemented model supports coarse classification before low-level access
rules are specified.

Entity operation kind:

- `resource`;
- `task`.

Entity application domain:

- `business`;
- `cms`;
- `generic`.

Service operation model:

- `public-api`;
- `business-service`;
- `internal-service`;
- `system-task`.

`usageKind` remains available as an additional or compatibility classification
axis. It is not the primary replacement for operation kind or application domain.

These classifications are used to derive an `EntityAuthorizationProfile`.
Explicit low-level `mode` and relation settings still override derived defaults.

## Relation-Based Authorization

Relation-based authorization is represented by `EntityAccessRelation`.

It supports cases where ACL-style owner/group/other permissions are not enough.
For example, a `SalesOrder` can be owned by the seller organization while still
being readable by the ordering customer:

```text
SalesOrder.ownerId = seller organization
SalesOrder.customerId = current subject customer id
SalesOrder.other.read = false
```

The relation rule grants read/search visibility when:

```text
entity.customerId == subject.customerId
```

Relation rules are evaluated before object-side owner/group/other permission
checks for user-permission access.

## ABAC Evaluation Perspective

The implemented baseline uses ABAC attributes primarily for profile derivation
and for connecting specialized authorization patterns.

Current specialized patterns are:

- RBAC-style role evaluation through subject roles;
- ReBAC-style relation evaluation through subject/entity relation attributes;
- DAC-style permission evaluation through entity `ownerId`, `groupId`, and
  owner/group/other permissions.

The natural ABAC evaluation path is an incremental extension. The baseline has
an explicit `UnitOfWorkAuthorization.naturalConditions` carrier for direct
entity-attribute equality conditions, but does not yet provide a full
authorization context or general policy language. Natural ABAC covers conditions
that are not simply role, relation, or owner/group/other permission checks, such
as:

- publication status and visibility;
- publish/unpublish time windows;
- tenant, organization, account, or customer boundaries represented as direct
  attributes;
- operation exposure based on service operation model;
- entity behavior based on operation kind and application domain.

Further extensions should preserve the same UnitOfWork/internal DSL boundary.

The stable first CML surface for explicit natural ABAC is operation `ACCESS` /
`CONDITION`. `CONDITION` is the canonical section name.

```text
### ACCESS

#### POLICY
public

#### CONDITION
postStatus=Published:read,search/list;visibility=Public:read,search/list
```

This condition is carried into `CmlOperationAccess.condition` and then into
`UnitOfWorkAuthorization.naturalConditions`.
For multiple conditions in CML, use `;` as the stable delimiter for now.
`CONDITIONS`, `ABAC`, `ABAC_CONDITION`, `ABAC_CONDITIONS`,
`NATURAL_CONDITION`, and `NATURAL_CONDITIONS` are accepted as compatibility
aliases, but new CML should use `CONDITION`.

## Variation Points

Component factories can provide coarse and fine-grained entity authorization
defaults through these variation points:

- `entity_usage_kind`;
- `entity_operation_kind`;
- `entity_application_domain`;
- `service_operation_model`;
- `entity_access_mode`;
- `entity_access_relations`;
- `authorize_unit_of_work`.

The intended order is:

1. derive defaults from operation kind, application domain, and service operation
   model;
2. apply explicit low-level access mode or relation settings when present;
3. allow component-specific `authorize_unit_of_work` as the final custom hook.

## Descriptor Support

`EntityRuntimeDescriptor` stores:

- `usageKind`;
- `operationKind`;
- `applicationDomain`.

Component descriptors can provide these fields using camelCase or snake_case
names.

## Related Documents

- `docs/design/execution-model.md`: execution and authorization boundary.
- `docs/design/free-unitofwork-execution-model.md`: internal DSL and UoW model.
- `docs/design/component-model.md`: component boundary responsibilities.
- `docs/notes/entity-authorization-implementation-note.md`: implementation
  details and open items.
- `docs/journal/2026/04/internal-dsl-centric-authorization-model-note.md`:
  earlier direction note.
- `docs/journal/2026/04/security-subject-model-note.md`: subject model direction.
- `docs/journal/2026/04/unitofwork-authorization-phase1-targets-note.md`:
  first target operations.
