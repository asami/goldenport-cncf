# Entity Authorization Model

Status: implemented baseline  
Date: 2026-04-13

## Purpose

This document records how the common CNCF authorization concepts are applied to
Entity and UnitOfWork access.

Common vocabulary such as subject, privilege, role, scope, capability,
permission, access kind, access mapping policy, resource family, association
authorization, and admin authorization is defined in
`authorization-concepts.md`. This document is the Entity-specific application of
that model.

The model separates:

- entity object permissions;
- service/internal execution authority;
- business relation based authorization;
- contextual ABAC guards;
- coarse-grained entity and service classifications.

Architecturally, the model is capability-oriented with explicit guards.
RBAC-style role checks, ReBAC-style relation checks, and DAC-style
owner/group/other permission checks feed effective capabilities. ABAC-style
conditions are contextual guards that can deny or filter access when natural
conditions do not match.

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

## Subject Model

The common subject, principal, `SecurityContext`, and `SecuritySubject`
definitions are in `authorization-concepts.md`.

The internal subject model is `SecuritySubject`. It is the normalized CNCF view
over `SecurityContext`.

`SecurityContext` remains the ingress/runtime input shape: principal id,
principal attributes, capabilities, and security level. CNCF does not currently
copy primary group, roles, or privileges into first-class `SecurityContext`
fields. Instead, `SecuritySubject.from(SecurityContext)` derives:

- authentication state;
- access-token presence;
- primary group;
- groups;
- roles;
- privileges;
- capabilities;
- security level;
- raw and normalized attribute values.

This keeps the normalization and alias handling in one security-layer model
instead of spreading it through runtime context construction. Business boundary
attributes such as `tenantId`, `accountId`, and `customerId` are read from
principal attributes with camelCase/snake_case aliases, split as multi-valued
tokens, and normalized for policy matching.

## Access Modes

Entity authorization distinguishes these modes:

- `UserPermission`;
- `ServiceInternal`;
- `System`.

`UserPermission` checks object-side security attributes and relation rules.

`ServiceInternal` is for operations performed inside a service boundary. It does
not reinterpret entity owner/group/other permissions. It is intended for cases
such as `SalesOrderService` updating `SalesOrder` records it manages.
Formally, it is derived from `ServiceOperationModel.InternalService`, not from an
entity's operation kind. In the current implementation it bypasses object-side
owner/group/other permission checks and search/list visibility filtering for
same-service internal work. Cross-component service access is not yet granted by
this mode alone; it requires an explicit service grant capability. The initial
grant target accepts normalized capability forms such as
`service-grant:{sourceComponent}:{targetComponent}`.

`System` is for framework/system work such as migration, indexing, projection, or
administration tasks. It also bypasses owner/group/other permissions.
Formally, it is derived from `ServiceOperationModel.SystemTask`, not from an
entity's operation kind. In the current implementation it bypasses object-side
owner/group/other permission checks and search/list visibility filtering for
framework-controlled work. Application business services should not use this
mode for ordinary domain operations.

Neither `ServiceInternal` nor `System` gives special meaning to the entity
`execute` permission bit.

When `sourceComponentName` and `targetComponentName` are both present and differ,
`ServiceInternal` is treated as cross-component access. When either side is
missing, the current implementation preserves the earlier same-boundary behavior
for compatibility.

## Object-Side Permissions

The relationship between subject-side capability and object-side permission is
defined in `authorization-concepts.md`. In this Entity model, object-side
permission is the compact entity-local capability grant table.

Entity authorization follows the common capability comparison model:

```text
guards are satisfied
AND
effective capabilities satisfy required capabilities
```

For entity access, guards include privilege/mode constraints when applicable and
ABAC natural conditions. Effective capabilities may come from subject grants,
role expansion, object-side permission, and relation rules. Required
capabilities are derived from the target collection/resource, target entity,
access kind, and applicable access mapping policy.

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

If a future application needs setuid-like, setgid-like, sticky, delegation,
impersonation, or ownership-transfer behavior, it MUST introduce an explicit
attribute or policy name for that behavior. The `execute` bit MUST remain only
an execute permission for executable entity operations.

Compact textual permission forms, if accepted by loaders or compatibility
adapters, are compatibility input only. The canonical runtime representation is
the structured owner/group/other rights carried by `SecurityAttributes`.

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

## Access Mapping Policy

Entity `accessKind` values are mapped to compact permission bits by CNCF
authorization policy.

The default mapping is:

```text
read        -> read
search/list -> read
update      -> write
write       -> write
delete      -> write or execute
execute     -> execute
```

`create` is not an entity-instance permission because the entity does not exist
yet. It is a collection-level authorization decision.

Collections or resource classes may override the default mapping. For example, a
collection may require `execute` for delete while still using `write` for
update. This keeps `read/write/execute` compact while allowing stricter
operation semantics where needed.

`SecurityAttributes` remains the compact object-side value type. Mapping
`accessKind` to required permission bits is CNCF authorization policy, not a
simplemodeling-model responsibility.

Owner, group, tenant, and organization id selection are create-default variation
points. The built-in default uses the current principal as owner id, uses that
owner id as the default group id, and leaves tenant and organization ids unset,
preserving the earlier behavior. Custom selectors can choose
application-specific values such as `ownerId = seller organization`,
`groupId = sales operations group`, and `tenantId` or `organizationId` for
`SalesOrder`. `operationKind` does not affect these selections.

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
`EntityCreateDefaultsPolicy.byCollectionName`. Entity-name-oriented defaults
can use `EntityCreateDefaultsPolicy.byEntityName`, which resolves the same
runtime collection identity while keeping the CML entity name intent explicit.
Application-level defaults can use
`EntityCreateDefaultsPolicy.withApplicationDefault`; entity-level overrides win
over the application default.

Security defaults must be chosen at the entity/component boundary. The framework
must not treat missing `securityAttributes` as implicit public read. If a
resource should be public-read by default, that must be expressed through the
entity classification or through an explicit entity-scoped create-default
policy, for example `public-content`, `cms`, or `public-read`.

This keeps the default fail-closed for business/private entities while allowing
selected public-content entities to opt in to public visibility without turning
the whole component or subsystem public by accident.

## Projected Entity And Raw Record

Two data shapes are relevant during authorization:

- projected entity / in-memory entity;
- raw store record.

The projected entity is the runtime object used by actions, resident working
sets, and projections. It may intentionally omit framework-owned attributes such
as fully expanded lifecycle or security metadata.

The raw store record is the canonical persistence-facing shape after
`EntityCreateDefaultsPolicy` and related store-side complement logic have been
applied. It is the authoritative source for:

- `securityAttributes`;
- lifecycle defaults such as `postStatus` and `aliveness`;
- trace/correlation enrichment and other framework-owned record complements.

Search/list visibility filtering must therefore not assume that a projected
entity carries every authorization-relevant field. When projected entity data is
insufficient for permission evaluation, the runtime must evaluate visibility
against the canonical raw store record rather than granting access from missing
attributes.

This boundary is deliberate:

- projected entity: application/domain-facing runtime model;
- raw record: canonical authorization/persistence model for framework-owned
  defaults.

## Classification Axes

The implemented model supports coarse classification before low-level access
rules are specified.

Entity operation kind:

- `resource`: master/reference data that is suitable for memory residency;
- `task`: transactional data that should not remain resident after it becomes
  inactive.

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
`operationKind` does not derive `execute=true`. Execute permission is reserved
for a future entity-provided operation invocation model and remains false by
default for both resource and task entities.

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

When ACL-style owner/group/other permissions and relation rules disagree, the
implemented precedence is:

1. ABAC natural conditions are mandatory guards. A missed natural condition
   denies access before relation or owner/group/other checks.
2. Public read policy can grant read visibility.
3. Matching relation rules grant the access kinds they explicitly allow.
4. Owner/group/other permissions are evaluated as the fallback grant path.

This means a matching relation rule can grant read/search/update access even when
`other` permission is false. Relation rules do not currently model explicit deny;
they are positive grants only. A relation that does not include the requested
access kind is ignored for that request, and the decision falls through to the
owner/group/other permission checks.

## ABAC Guard Perspective

The implemented baseline uses ABAC attributes primarily for contextual guard
evaluation and profile derivation. ABAC is not a capability grant source in the
common model.

Current specialized patterns are:

- RBAC-style role evaluation through subject roles;
- ReBAC-style relation evaluation through subject/entity relation attributes;
- DAC-style permission evaluation through entity `ownerId`, `groupId`, and
  owner/group/other permissions.

The natural ABAC evaluation path is an incremental extension. The baseline has
an explicit `UnitOfWorkAuthorization.naturalConditions` carrier for direct
entity-attribute equality conditions. `EntityAuthorizationContext` is the
canonical carrier for natural ABAC evaluation input. It exposes subject, entity,
operation, application, and environment attributes. The current evaluator uses
subject/entity attributes and the first operation/application attributes:
`operationModel`, `entityOperationKind`, and `entityApplicationDomain`. Natural
ABAC covers conditions that are not simply role, relation, or owner/group/other
permission checks, such as:

- publication status and visibility;
- publish/unpublish time windows;
- tenant, organization, account, or customer boundaries represented as direct
  attributes;
- operation exposure based on service operation model;
- entity behavior based on operation kind and application domain.

Natural ABAC condition results should use four decision states:

- `Allow`: the condition is applicable and matched;
- `Deny`: the condition is applicable and missed;
- `NotApplicable`: the condition does not apply to the requested access kind or
  is not present in the policy set;
- `Indeterminate`: the condition could not be evaluated because required
  context, attributes, or evaluator support is missing.

For the current positive-condition model, natural ABAC is composed as a mandatory
guard before RBAC-style, ReBAC-style, and DAC-style grants. All applicable
natural conditions must allow. Any `Deny` denies the request before relation or
owner/group/other permission checks. `NotApplicable` has no effect. Until a full
policy language defines explicit error handling, `Indeterminate` is fail-closed
for user-permission access.

This avoids creating many state-specific capabilities such as
`post.read.when_published` or `blob.read.when_tenant_matches`. The capability
remains a positive grant; ABAC carries the negative or conditional constraint.

After natural ABAC guard evaluation, grants compose as follows:

1. RBAC-style manager privileges can grant broad management access.
2. Public read policy can grant read visibility for public profiles.
3. ReBAC-style relation rules grant only their listed access kinds.
4. DAC-style owner/group/other permissions are evaluated as the fallback grant
   path.

This keeps the authorization context unified without treating ABAC as a grant
source: RBAC-style roles, ReBAC-style relations, and DAC-style permissions grant
capabilities, while natural ABAC conditions constrain those grants as guards.

Further extensions should preserve the same UnitOfWork/internal DSL boundary.

The stable first CML surface for explicit natural ABAC is operation `ACCESS` /
`CONDITION`. `CONDITION` is the canonical section name.

```text
### ACCESS

#### POLICY
public

#### CONDITION
postStatus=Published:read,search/list;visibility=Public:read,search/list
publishAt&lt;=now:read,search/list;closeAt&gt;now:read,search/list
```

This condition is carried into `CmlOperationAccess.condition` and then into
`UnitOfWorkAuthorization.naturalConditions`.
For multiple conditions in CML, use `;` as the stable delimiter for now.
`CONDITIONS`, `ABAC`, `ABAC_CONDITION`, `ABAC_CONDITIONS`,
`NATURAL_CONDITION`, and `NATURAL_CONDITIONS` are accepted as compatibility
aliases, but new CML should use `CONDITION`.

Entity classification is declared in the entity `FEATURES` section:

```text
### FEATURES
usageKind = "business-object"
operationKind = "resource"
applicationDomain = "business"
```

Service-level `operationModel` is declared in service `ACCESS`; operation-level
`operationModel` uses the same `ACCESS` shape inside the operation and overrides
the service default:

```text
### ACCESS

#### POLICY
owner_or_manager

#### OPERATION_MODEL
business-service
```

Relation-based authorization and explicit low-level access mode overrides use
the same operation-level `ACCESS` surface. This keeps the effective
authorization input next to the operation that triggers entity access and avoids
splitting one `UnitOfWorkAuthorization` carrier across unrelated CML blocks.

```text
### ACCESS

#### POLICY
owner_or_manager

#### MODE
user-permission

#### RELATION
customerId=subject.customerId:read,search/list
```

`MODE` is optional and maps to `CmlOperationAccess.mode`. It accepts:

- `user-permission`;
- `service-internal`;
- `system`.

Most CML should omit `MODE` and allow the framework to derive access mode from
`ServiceOperationModel`. `MODE` exists as a low-level override for framework or
application cases where the derived model is intentionally insufficient.

`RELATION` is optional and maps to `CmlOperationAccess.relation`. Each relation
uses this stable form:

```text
entityField=subject.subjectField:access-kind[,access-kind]
```

The subject prefix may also be written as `principal.`. If access kinds are
omitted, the default is `read,search/list`. Multiple relation declarations are
written in the same `RELATION` section with `;` or newline separators:

```text
customerId=subject.customerId:read,search/list;
accountId=subject.accountId:read,search/list
```

The stable CML location decision is:

- relation rules are declared at operation `ACCESS` for the current supported
  user-level syntax;
- service-level declarations are reserved for coarse `operationModel` defaults;
- entity-level declarations and separate policy blocks are future reuse/default
  mechanisms, not the first stable relation syntax.

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
