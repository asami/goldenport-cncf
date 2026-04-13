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
- `EntityAuthorizationContext`;
- `EntityCreateDefaultsPolicy`;
- `RuntimeContext.entityCreateDefaultsPolicy`;
- `SecuritySubject` as a normalized view over `SecurityContext`;
- direct use of simplemodeling-model `SecurityAttributes`.

The implementation is a baseline for business applications as well as CMS-style
applications.

The model should be understood as ABAC-centered. RBAC, ReBAC, and DAC-style
permission checks are not intended to be separate peer layers. They are
specialized evaluation patterns connected through ABAC attributes:

- RBAC-style role evaluation uses subject role attributes;
- ReBAC-style relation evaluation uses subject/entity relationship attributes;
- DAC-style permission evaluation uses entity owner/group/other permission
  attributes.

The general ABAC natural evaluation path is still incomplete. The current
implementation has ABAC-based classification and profile derivation, but it does
not yet provide a general evaluator for non-role, non-relation, non-permission
attribute conditions.

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

`SecuritySubject` is now the documented internal subject model rather than a
temporary helper. `SecurityContext` stays as the runtime input shape, and
`SecuritySubject.from(SecurityContext)` derives normalized primary group, groups,
roles, privileges, capabilities, security level, and business boundary
attributes. Primary group, roles, and privileges should not be duplicated as
first-class `SecurityContext` fields until an ingress/runtime use case requires
that broader API change.

The current relation rule supports simple field equality such as:

```text
customerId=subject.customerId:read,search/list
```

The syntax is an implementation-level parser, not yet a finalized CML syntax.

The current ABAC-centered behavior is primarily profile selection:

- `EntityOperationKind`;
- `EntityApplicationDomain`;
- `ServiceOperationModel`;
- optional explicit access mode;
- optional relation rules.

It does not yet perform natural ABAC checks such as publication time,
visibility, tenant boundary, organization boundary, operation exposure, or
environment-based conditions.

The first natural ABAC hook is available as explicit
`UnitOfWorkAuthorization.naturalConditions`. It is intentionally opt-in so that
existing applications do not silently gain tenant/organization/publication
constraints. The initial condition form supports direct entity attribute equality
against a subject attribute or literal value, plus basic time-window comparison
against `now`, for example:

```text
tenantId=subject.tenantId:read,search/list
postStatus=Published:read,search/list
publishAt<=now:read,search/list
closeAt>now:read,search/list
```

In CML text, write `<` and `>` as HTML entities to avoid Markdown/XML parsing:

```text
publishAt&lt;=now:read,search/list
closeAt&gt;now:read,search/list
```

These conditions are evaluated before relation rules and owner/group/other
permissions for user-permission entity access.

The first CML surface is operation-level `ACCESS` / `CONDITION`. `CONDITION` is
the canonical section name:

```text
### ACCESS

#### POLICY
public

#### CONDITION
postStatus=Published:read,search/list;visibility=Public:read,search/list
```

`CONDITIONS`, `ABAC`, `ABAC_CONDITION`, `ABAC_CONDITIONS`,
`NATURAL_CONDITION`, and `NATURAL_CONDITIONS` are accepted as compatibility
aliases, but new CML should use `CONDITION`. The `now` value is currently the
first environment attribute.
For multiple conditions in CML, use `;` as the stable delimiter for now.

Entity classification is carried in the entity `FEATURES` section. New CML
should use camelCase names:

```text
### FEATURES
usageKind = "business-object"
operationKind = "resource"
applicationDomain = "business"
```

snake_case aliases remain accepted. Service-level and operation-level
`operationModel` are carried in `ACCESS`:

```text
### ACCESS
#### POLICY
owner_or_manager
#### OPERATION_MODEL
business-service
```

Natural condition evaluation now produces a minimal diagnostic result. When a
direct read/update/delete authorization path is denied by a missed ABAC natural
condition, the failure message includes the condition text plus actual and
expected values. Search/list filtering also emits an `authorization.abac.filter`
observability event when ABAC natural conditions hide one or more records. The
event records a summary count and the first missed condition. This is not yet
full audit telemetry for every condition evaluation.

Relation rules are positive grants. The current user-permission evaluation order
is natural conditions, public read policy, relation rules, then owner/group/other
permissions. A matching relation rule can therefore grant an explicitly listed
access kind even when `other` permission is false. Relation rules do not yet
represent explicit deny; if a rule does not list the requested access kind, it is
not applicable and the decision falls through to owner/group/other permissions.

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

- `operationKind`: `resource` for master/reference data suited to memory
  residency, or `task` for transactional data that should leave memory after it
  becomes inactive;
- `applicationDomain`: `business`, `cms`, or `generic`.

`usageKind` remains present for other classification purposes and compatibility.

Service/operation classification currently uses:

- `public-api`;
- `business-service`;
- `internal-service`;
- `system-task`.

The derivation policy is intentionally conservative:

- internal services derive service-internal mode;
- system tasks derive system mode;
- business/public APIs remain user-permission based unless overridden.

`operationKind=task` does not imply `execute=true` and does not by itself imply
service-internal access. Execute permission is reserved for a future
entity-provided operation invocation model and remains false by default for both
resource and task entities.

Create defaults now have minimal owner-id, group-id, tenant-id, and
organization-id selector hooks. The built-in default keeps the previous behavior:
it derives owner id from the current principal, uses the owner id as the default
group id, and does not add tenant or organization ids. A custom default policy
can replace the selectors, for example to make a `SalesOrder` owner id point at a
seller organization, group id point at a sales operations group, and tenant or
organization ids point at the current business boundary. Entity-level
registration can use `EntityCreateDefaultsPolicy.byEntityName`; it resolves the
same runtime collection identity as the collection-name registry while keeping
the CML entity-name intent visible at the call site. Application-level defaults
can use `EntityCreateDefaultsPolicy.withApplicationDefault`, with entity-level
overrides applied on top of the application default.

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
- explicit deny rules, if a future use case requires them.

Audit and observability are partially implemented. The runtime emits
`authorization.decision` for `UnitOfWork` authorization checks and search/list
visibility checks. The event includes outcome, access mode, resource
family/type, collection, target id, entity names, source/target component,
subject id, and normalized role/group/capability/security-level summaries.
Relation rule diagnostics are emitted as `authorization.relation.diagnostics`
debug events when relation rules are evaluated. They summarize applicable,
matched, missed, and not-applicable rules.
Natural ABAC diagnostics are emitted as `authorization.abac.diagnostics` debug
events when explicit natural conditions are evaluated. They summarize matched,
missed, and not-applicable conditions and include per-condition actual and
expected values.

Action-level authorization denial is separated from action execution
observability. `ActionEngine.executeAuthorized` commits an authorization-failed
`ActionEvent`, but it does not build the `ActionCall` and does not invoke
execution enter/leave observation hooks.

`ServiceInternal` and `System` bypass object permissions by design. The current
implementation emits an `authorization.permission.bypass` observability event
with access mode, resource information, target id, access kind, and subject id.
Authorization decisions are also counted in `RuntimeDashboardMetrics`.
Dashboard state exposes them as `authorization.decisions`, and the system
performance page shows total/day/hour/minute counts with denials represented as
errors.

The implemented derivation is operation-model based: `InternalService` derives
`ServiceInternal`, `SystemTask` derives `System`, and ordinary business
operations over `task` entities remain `UserPermission`. Entity `operationKind`
controls resource/task operational classification and does not grant internal or
system access by itself.

Cross-component grants now have an initial runtime check. `UnitOfWorkAuthorization`
can carry `sourceComponentName` and `targetComponentName`. If both are present
and differ, `ServiceInternal` requires a service grant capability such as
`service-grant:sales:inventory`. If either side is missing, the implementation
preserves the earlier same-boundary behavior for compatibility.

The descriptor/CML syntax for declaring these grants is not finalized yet.

Create default derivation is not fully tied to entity classification yet.

The policy supports profile-based CMS/public-content defaults and
collection-specific policies, but the higher-level descriptor classification
should eventually derive create default profiles automatically.

ABAC natural evaluation is only minimally implemented.

The desired structure is:

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

The intended natural ABAC decision vocabulary is `Allow`, `Deny`,
`NotApplicable`, and `Indeterminate`. In the current implementation this is only
partially represented: an applicable matched condition behaves as allow, an
applicable missed condition behaves as deny, and conditions that do not list the
requested access kind are not applicable. A future evaluator should represent
these states explicitly. Missing context or unsupported evaluators should be
treated as `Indeterminate`; for user-permission entity access this should fail
closed until a policy explicitly defines another behavior.

Composition is guard-first. Natural ABAC conditions constrain the grant paths:
RBAC-style manager role grants, ReBAC-style relation grants, and DAC-style
owner/group/other permission grants are considered only after applicable natural
conditions have allowed. Relation and DAC grants remain positive grants, not
explicit deny rules.

`EntityAuthorizationContext` is the first explicit context carrier for natural
ABAC evaluation. It exposes subject, entity, operation, application, and
environment attributes in one structure. The current evaluator can now use
subject/entity attributes and operation/application attributes such as
`operation.operationModel`, `application.entityOperationKind`, and
`application.entityApplicationDomain`. Environment-specific conditions remain
future work.

The first practical ABAC natural evaluators should cover:

- publication status and visibility;
- publish/unpublish time windows;
- tenant/organization/account/customer boundaries when represented as direct
  attributes;
- operation exposure based on `operationModel`;
- entity classification based on `operationKind` and `applicationDomain`.

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
