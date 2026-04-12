# Entity Authorization Implementation Note

Date: 2026-04-13

## Summary

This note summarizes the current implementation of CNCF entity authorization and
the remaining direction.

The current implementation introduced:

- `EntityAccessMode`;
- `EntityAccessRelation`;
- `EntityOperationKind`;
- `EntityApplicationDomain`;
- `ServiceOperationModel`;
- `EntityAuthorizationProfile`;
- `EntityCreateDefaultsPolicy`;
- `RuntimeContext.entityCreateDefaultsPolicy`;
- `SecuritySubject` as a normalized view over `SecurityContext`;
- direct use of simplemodeling-model `SecurityAttributes`.

The implementation is a baseline for business applications as well as CMS-style
applications.

## Implemented Files

Main implementation points:

- `src/main/scala/org/goldenport/cncf/security/EntityAccessMode.scala`
- `src/main/scala/org/goldenport/cncf/security/EntityAccessRelation.scala`
- `src/main/scala/org/goldenport/cncf/security/EntityOperationKind.scala`
- `src/main/scala/org/goldenport/cncf/security/EntityApplicationDomain.scala`
- `src/main/scala/org/goldenport/cncf/security/ServiceOperationModel.scala`
- `src/main/scala/org/goldenport/cncf/security/EntityAuthorizationProfile.scala`
- `src/main/scala/org/goldenport/cncf/security/OperationAccessPolicy.scala`
- `src/main/scala/org/goldenport/cncf/security/SecuritySubject.scala`
- `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkAuthorization.scala`
- `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala`
- `src/main/scala/org/goldenport/cncf/entity/EntityCreateDefaultsPolicy.scala`
- `src/main/scala/org/goldenport/cncf/entity/runtime/EntityRuntimeDescriptor.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentDescriptor.scala`

Tests:

- `src/test/scala/org/goldenport/cncf/entity/EntityCreateDefaultsPolicySpec.scala`
- `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkTargetAuthorizationSpec.scala`
- `src/test/scala/org/goldenport/cncf/action/ActionCallEntityAccessMetricsSpec.scala`

The simplemodeling-model side now provides `SecurityAttributes` helpers such as
`privateOwnedBy`, `publicOwnedBy`, and `readWrite` permissions.

## Current Behavior

The default business behavior is:

- entity create default is private-owned;
- `other` has no read/write/execute rights;
- owner has read/write, but not execute;
- `postStatus` defaults to `Published`;
- CMS/public-content profile enables public read.

The current public-read profiles are:

- `public-read`;
- `publication`;
- `cms`;
- `public-content`.

The current authorization modes are:

- user permission;
- service internal;
- system.

The current relation rule supports simple field equality such as:

```text
customerId=subject.customerId:read,search/list
```

The syntax is an implementation-level parser, not yet a finalized CML syntax.

## SalesOrder Example

For a business entity such as `SalesOrder`, the intended defaults are:

```text
operationKind = resource
applicationDomain = business
ownerId = seller organization
groupId = sales or order operations group
other = none
execute = false
```

The customer relation is not modeled as `ownerId`. Instead, the entity carries a
business field such as `customerId`, and the authorization layer can allow read
when:

```text
SalesOrder.customerId == current subject customerId
```

Service methods such as `confirmOrder`, `allocateStock`, or `shipOrder` should
operate under `business-service` or `internal-service` defaults, depending on
whether the operation is still caller-permission based or service-internal.

## Implemented Classification Direction

Entity classification has two primary axes:

- `operationKind`: `resource` or `task`;
- `applicationDomain`: `business`, `cms`, or `generic`.

`usageKind` remains present for other classification purposes and compatibility.

Service/operation classification currently uses:

- `public-api`;
- `business-service`;
- `internal-service`;
- `system-task`.

The derivation policy is intentionally conservative:

- task entities derive service-internal handling unless explicitly overridden;
- internal services derive service-internal mode;
- system tasks derive system mode;
- business/public APIs remain user-permission based unless overridden.

## Unimplemented or Incomplete Areas

CML syntax is not finalized.

The runtime fields exist, but the generator/parser still needs a stable user-level
syntax for:

- entity `operationKind`;
- entity `applicationDomain`;
- service/operation `operationModel`;
- relation rules;
- explicit low-level override mode.

Descriptor propagation is partial.

`EntityRuntimeDescriptor` and `ComponentDescriptor` can carry the new entity
classification fields. Further work is needed to ensure generated CAR descriptor
files consistently include them.

Relation rules are minimal.

The implementation currently supports simple equality between an entity field and
a subject field. Future likely needs include:

- multiple relations per entity/operation;
- subject group/account expansion;
- relation lookup through another component;
- scoped relation kinds such as customer, tenant, organization, assignee;
- denial precedence rules.

Audit and observability are incomplete.

`ServiceInternal` and `System` bypass object permissions by design, but the
authorization decision should become visible in audit/security telemetry.

Cross-component grants are not yet complete.

`ServiceInternal` is appropriate inside a service boundary. When a service touches
another component's entity, the model should require an explicit service grant or
capability.

Create default derivation is not fully tied to entity classification yet.

The policy supports profile-based CMS/public-content defaults and
collection-specific policies, but the higher-level descriptor classification
should eventually derive create default profiles automatically.

## Relationship to Existing Documents

This note updates the implementation status of:

- `docs/journal/2026/04/internal-dsl-centric-authorization-model-note.md`;
- `docs/journal/2026/04/security-subject-model-note.md`;
- `docs/journal/2026/04/unitofwork-authorization-phase1-targets-note.md`.

The stable implemented contract is documented in:

- `docs/design/entity-authorization-model.md`.

The entity runtime storage and working-set behavior remain documented in:

- `docs/notes/entity-runtime-architecture.md`;
- `docs/notes/partitioned-entity-realm.md`.

The execution boundary remains documented in:

- `docs/design/execution-model.md`;
- `docs/design/free-unitofwork-execution-model.md`.
