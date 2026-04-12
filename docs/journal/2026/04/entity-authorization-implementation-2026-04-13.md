# Entity Authorization Implementation Journal

Date: 2026-04-13

## Context

The discussion started from entity creation defaults and permission defaults.

The immediate issue was how to set `SecurityAttributes` for newly created
entities:

- CMS-like entities often need `other.read = true`;
- business entities often need `other = false`;
- ordinary data entities should not get `execute = true`;
- executable entities should opt into execute semantics explicitly.

The initial question was whether CNCF should define its own security attributes
model or adapter. The decision was not to do that. CNCF should use the canonical
`SecurityAttributes` model from simplemodeling-model and place CNCF-specific
policy in CNCF runtime code.

## Main Decisions

### Use simplemodeling-model SecurityAttributes

CNCF should not define a competing object-side security attributes model.

The simplemodeling-model `SecurityAttributes` type remains canonical. CNCF adds
runtime policy around it.

### Do Not Overload Execute

Unix has historical special permission behavior around setuid, setgid, and sticky
bit semantics. CNCF should not encode comparable behavior into the `execute`
permission.

`execute` means execute permission only.

Any future behavior such as privilege elevation, inherited group control, or
delete restriction should be modeled explicitly.

### Separate Object ACL from Service Authority

Business services often operate on entities they manage regardless of the caller's
object-level owner/group/other rights.

For example, `SalesOrderService.shipOrder` should be able to update a
`SalesOrder` owned by a seller organization, even though the caller may not have
direct write permission on that entity.

This led to `EntityAccessMode`:

- user permission;
- service internal;
- system.

### SalesOrder Ownership

For `SalesOrder`, `ownerId` should normally represent the seller-side
organization, department, or tenant, not the customer user and not necessarily the
creator.

Customer visibility should be expressed through a business relation such as:

```text
SalesOrder.customerId == current subject customerId
```

This avoids making customer ownership and seller operational responsibility
compete inside one ACL field.

### Separate Entity Axes

The earlier `usageKind` field looked too overloaded.

The decision was to keep `usageKind` available, but introduce two more explicit
entity axes:

- `operationKind`: resource or task;
- `applicationDomain`: business, cms, or generic.

These are independent:

- a business application can have both resource entities and task entities;
- a CMS application can also have tasks;
- `usageKind` may still be useful for other classification or compatibility.

### Service/Operation Model

Service and operation behavior should be classified before detailed overrides.

The initial model is:

- public API;
- business service;
- internal service;
- system task.

The low-level `mode` and relation settings remain available, but they should be
used after the high-level classification has been tried.

### ABAC-Centered Hybrid Model

The authorization model should be described as ABAC-centered.

RBAC, ReBAC, and DAC-style permissions should not be treated as separate peer
layers beside ABAC. They are specialized evaluation patterns connected through
attributes:

- RBAC-style evaluation uses subject role attributes;
- ReBAC-style evaluation uses subject/entity relation attributes;
- DAC-style permission evaluation uses entity owner/group/other permission
  attributes.

This means the intended model is:

```text
ABAC-centered model =
  - ABAC natural evaluation
      via subject/entity/operation/application/environment attributes
  - RBAC-style role evaluation
      via subject.roles
  - ReBAC-style relation evaluation
      via subject/entity relation attributes
  - DAC-style permission evaluation
      via entity.ownerId/groupId/permission
```

The current implementation has ABAC-based profile derivation, but the ABAC
natural evaluation path is still a remaining item. Examples include publication
time windows, visibility, tenant or organization boundaries, and operation
exposure rules that are not simply role, relation, or owner/group/other
permission checks.

## Resulting Implementation

The implementation introduced:

- `EntityAccessMode`;
- `EntityAccessRelation`;
- `EntityOperationKind`;
- `EntityApplicationDomain`;
- `ServiceOperationModel`;
- `EntityAuthorizationProfile`;
- `EntityCreateDefaultsPolicy`;
- additional fields on `UnitOfWorkAuthorization`;
- high-level access fields on `CmlOperationAccess`;
- component factory variation points for entity/service classification.

It also extended simplemodeling-model `SecurityAttributes` helpers:

- `readWrite`;
- `ownedBy`;
- `publicOwnedBy`;
- `privateOwnedBy`;
- parsing and record conversion helpers.

## Important Open Points

The CML syntax has not been finalized.

The intended shape is to allow high-level declarations such as:

```text
ENTITY SalesOrder
  operationKind resource
  applicationDomain business
```

and service/operation-level declarations such as:

```text
SERVICE SalesOrderService
  operationModel business-service
```

The exact CML syntax remains a follow-up item.

Relation rules are still minimal and should become more expressive over time.

Cross-component service-internal access needs an explicit service grant model.

Audit/observability for authorization decisions needs to be strengthened,
especially for `ServiceInternal` and `System`.

ABAC natural evaluation needs to be added. The first concrete targets are
publication visibility, publish/unpublish windows, tenant or organization
boundaries, and operation exposure policies.

## Related Documents

Implemented specification:

- `docs/design/entity-authorization-model.md`

Implementation note and future work:

- `docs/notes/entity-authorization-implementation-note.md`

Earlier notes that this work advances:

- `docs/journal/2026/04/internal-dsl-centric-authorization-model-note.md`
- `docs/journal/2026/04/security-subject-model-note.md`
- `docs/journal/2026/04/unitofwork-authorization-phase1-targets-note.md`
