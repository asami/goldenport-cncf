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
aliases, but new CML should use `CONDITION`. This is only the first carrier and
evaluator. The `now` value is currently the first environment attribute. A richer
authorization context and entity-level syntax remain future work.
For multiple conditions in CML, use `;` as the stable delimiter for now.

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
- explicit deny rules, if a future use case requires them.

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
environment attributes in one structure. The current evaluator still only uses
subject and entity attributes, but the structure is now in place for
operation/application/environment conditions.

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
