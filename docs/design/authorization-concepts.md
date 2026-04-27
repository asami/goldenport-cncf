# Authorization Concepts

Status: design baseline
Date: 2026-04-28

## Purpose

This document defines the common CNCF authorization vocabulary used by
operation authorization, entity authorization, admin authorization, and later
resource-specific policies such as Blob and Association management.

Specific authorization surfaces apply these concepts:

- `operation-authorization-model.md` applies them to Component / Service /
  Operation dispatch.
- `entity-authorization-model.md` applies them to Entity / UnitOfWork access.

## Authorization Layers

CNCF authorization is evaluated at two main layers.

Operation authorization is selector-level admission control. It decides whether a
subject may invoke a Component / Service / Operation at all.

Entity/resource authorization is object/resource access control. It decides
whether a subject may perform an action against an Entity instance, Entity
collection, Association domain, store resource, or other runtime resource.

Operation admission does not replace entity/resource authorization. A request may
pass operation authorization and still be denied at the UnitOfWork/resource
boundary.

## Subject, Principal, and Security Context

A principal is the runtime identity of the caller.

`SecurityContext` is the ingress/runtime carrier. It holds the principal,
capabilities, security level, and raw principal attributes supplied by the
runtime or authentication provider.

`SecuritySubject` is the normalized CNCF security view derived from
`SecurityContext`. It derives authentication state, roles, scopes, groups,
privileges, capabilities, security level, and business boundary attributes from
canonical and compatibility attribute names.

Authorization code should use `SecuritySubject` for policy decisions instead of
re-reading raw request attributes.

## Privilege

`privilege` is a coarse runtime/system guard.

It answers:

```text
Is this subject allowed to reach this class of system capability at all?
```

Examples:

- `anonymous`
- `user`
- `operator`
- `system`
- `internal`

Privilege is intentionally small and coarse. It is a final guard for high-risk
surfaces such as production admin. It is not a replacement for roles,
capabilities, scopes, or entity permissions.

If a role policy is misconfigured, privilege should still prevent a low-level
subject from reaching system-level operations.

## Role and Scope

`role` is an operational responsibility assignment.

Examples:

- `system_admin`
- `component_operator`
- `application_operator`
- `support_operator`
- `audit_viewer`

`scope` limits where a role or capability applies.

Examples:

- `component:cwitter`
- `application:storefront`
- `tenant:tenant-a`

Roles and scopes are subject-side policy inputs. They are used mainly for
operation/admin admission and coarse operational policy.

Role definitions may expand a role into one or more capabilities. This is the
minimal subject-side grant configuration surface used before a full
organization-grade role registry exists.

Example:

```yaml
security:
  authorization:
    roles:
      blob_user:
        capabilities:
          - collection:blob:create
          - association:blob_attachment:create
          - association:blob_attachment:delete
      blob_operator:
        includes:
          - blob_user
        capabilities:
          - store:blobstore:status
```

At runtime, `SecuritySubject` expands role definitions into effective subject
capabilities. `includes` are transitive and cycle-safe. This descriptor surface
is intentionally small; role lifecycle administration and identity-provider
role management remain Security work outside Phase 18.

## Capability

A capability is the normalized authorization currency used to compare what the
subject can do against what the object/resource/action requires.

It answers either side of the authorization comparison:

```text
effective capability: what this subject can do
required capability: what this resource/action requires
```

Subject-side capabilities are appropriate for coarse or shared function grants
such as service grants, collection creation, association-domain mutation, store
status, import/export, or admin diagnostics.

Capabilities are positive grants. They should describe what is allowed, not
every condition under which it is denied. Modeling denial or contextual
exceptions as separate negative capabilities causes configuration growth and
ambiguous precedence. Use guards for those constraints.

Capabilities should not be physically enumerated for every entity-instance
read/write permission. Doing so makes subject metadata grow with entity count
and defeats the purpose of compact object-side permissions. Entity-local
capabilities should normally be derived at authorization time from the target
object's permission, relation, or ACL context.

## Guard

A guard is a condition that must be satisfied before or alongside capability
comparison. Guards do not grant capabilities by themselves.

Guards provide the "not unless" side of the model. They keep negative,
conditional, and state-dependent rules out of the capability namespace.

Initial guard families:

- privilege ceiling;
- ABAC / contextual predicates;
- operation mode;
- production/develop/test mode;
- runtime feature enablement such as production admin enabled.

Guards answer:

```text
Is this subject/context allowed to use this class of capability here and now?
```

`privilege` is a coarse system-level guard. `ABAC` is a contextual resource
guard. Both are intentionally separate from capability grants.

Example:

```text
capability: collection:blob:read
guard:      blob is active, tenant matches, and publish window is open
```

The capability grants the positive ability to read Blob metadata. The guards
decide whether that ability is usable for the current object and context.

## Permission

Permission is object-side access metadata stored with or derived from a resource.

For SimpleEntity, the canonical permission model is
`SecurityAttributes`:

```text
owner: read/write/execute
group: read/write/execute
other: read/write/execute
```

Permission is a compact grant table for entity-local capabilities. At runtime,
the subject is classified as owner, group, or other for the target entity. The
matching row grants entity-local `read`, `write`, and `execute` capability.

In short:

```text
permission = object-local capability grant table
capability = subject-side or derived ability to perform an action
```

Capability can therefore appear in two scopes:

- entity-local capability derived from object permission;
- subject capability carried by `SecuritySubject.capabilities`.

Policy code must keep those scopes distinct.

## Policy

Policy computes guards and both sides of the capability comparison.

Subject-side policy derives effective capabilities from subject-side security
objects such as direct capabilities, roles, groups, scopes, and subject
attributes.

Object/resource-side policy derives required capabilities from resource family,
resource type, collection, association domain, operation selector, target object,
access kind, and execution context.

Object-local grant policy derives target-local effective capabilities from
object-side security objects such as permission, ACL, and relation.

Guard policy evaluates non-grant constraints such as privilege ceiling, ABAC
conditions, operation mode, runtime mode, and feature enablement.

The conceptual decision is:

```text
guards are satisfied
AND
effective capabilities satisfy required capabilities
```

where:

```text
guards =
  privilege ceiling
  + ABAC/contextual predicates
  + runtime/operation-mode predicates

effective capabilities =
  direct subject capabilities
  + capabilities expanded from roles
  + capabilities derived from object-local permission/ACL/relation

required capabilities =
  capabilities required by resource/action/context policy
```

Not all capabilities need to be materialized into one physical set. In
particular, permission-derived entity-local capabilities should usually be
computed against the current target object instead of stored on the subject.

## Grant Sources and Guard Predicates

Role, permission, ACL, and relation are grant sources. ABAC and privilege are
guard predicates. They are not separate authorization currencies.

The common normalization is:

```text
role       -> subject effective capabilities
permission -> object-local effective capabilities
ACL        -> object-local effective capabilities or denials
relation   -> conditional object-local effective capabilities
privilege  -> coarse guard
ABAC       -> contextual guard or filter
```

`role` is normally a bundle of capabilities assigned to a subject.

`permission` is a compact object-local grant table.

`ACL`, when introduced, should be an explicit grant/deny list for cases that do
not fit the compact owner/group/other table. CNCF currently has the compact
permission model but not a first-class arbitrary ACL list.

`relation` is a context-dependent grant mechanism. It may grant access when a
subject/object relationship such as author, recipient, assignee, member, or
tenant participant matches.

`ABAC` is a contextual guard mechanism. It should express conditions such as
published state, active lifecycle, tenant boundary, time window, classification
limit, or environment predicate. It may deny or filter access when natural
conditions do not match, but it should not be treated as a stored subject
capability.

## Access Kind and Permission Bits

`accessKind` is the requested action label carried by the authorization request.

Common access kinds include:

- `create`
- `read`
- `search/list`
- `update`
- `write`
- `delete`
- `execute`

Permission bits are intentionally compact. They do not need to name every
operation. CNCF maps an `accessKind` to one or more required permission bits.

Default mapping:

```text
read        -> read
search/list -> read
update      -> write
write       -> write
delete      -> write or execute
execute     -> execute
```

`create` is not an entity-instance permission because the target entity does not
exist yet. It is a collection/resource-class authorization decision.

## Access Mapping Policy

Access mapping policy is the object/resource-side rule that maps an
`accessKind` to required permission bits or required capabilities.

This preserves compact owner/group/other permissions while allowing stricter
collections or resource classes.

Example intent:

```yaml
security:
  authorization:
    resources:
      collections:
        blob:
          create:
            capability: collection:blob:create
          delete:
            permission: execute
      associations:
        blob_attachment:
          create:
            capability: association:blob_attachment:create
          delete:
            capability: association:blob_attachment:delete
      stores:
        blobstore:
          status:
            capability: store:blobstore:status
```

The default `delete -> write or execute` keeps ordinary CRUD simple. A stricter
collection can require `execute` for delete while still allowing `write` for
update.

Policy lookup should prefer the most specific applicable rule:

1. collection/resource-specific mapping;
2. resource-family/resource-type mapping;
3. default mapping.

`SecurityAttributes` remains a compact primitive. Access mapping policy belongs
in CNCF authorization policy, not in the simplemodeling-model value type.

## Resource Families

Authorization requests should identify the resource family being protected.

Initial families:

- `operation`: Component / Service / Operation selector admission.
- `domain`: ordinary Entity and Aggregate/View backing resources.
- `association`: generic Association domains such as `blob_attachment` or
  `product_tag`.
- `store`: runtime stores such as BlobStore status or diagnostics.
- `system`: system/admin runtime resources.

Blob authorization should be expressed through these generic families rather
than Blob-specific capability names where possible.

Examples:

```text
Blob upload/register
  -> Blob EntityCollection create

Blob read payload/metadata
  -> Blob Entity read

Blob delete
  -> Blob Entity delete

Blob attach
  -> source Entity write
  -> target Blob read
  -> association domain blob_attachment create

Blob detach
  -> source Entity write
  -> association domain blob_attachment delete

BlobStore status
  -> store/blobstore status read
```

## Create Authorization

Create is a collection-level or resource-class decision.

It answers:

```text
Can this subject create a new member of this collection/resource class?
```

Examples:

```text
Blob upload/register
  -> create on Blob collection

Post creation
  -> create on Post collection

Product creation
  -> create on Product collection
```

Create should be controlled by collection/resource policy, subject capability,
role/scope, privilege, or a combination of these. It should not be modeled as an
owner/group/other instance permission, because there is no target instance yet.

## Association Authorization

Associations are generic relationships between entities. They should be
authorized by association domain and action, not by Blob-specific capability
names.

Examples:

```text
association:blob_attachment:create
association:blob_attachment:delete
association:user_like_product:create
association:product_tag:delete
```

Blob attach/detach is the first concrete use case:

```text
attach_blob_to_entity
  -> source entity write
  -> target Blob read
  -> blob_attachment association create

detach_blob_from_entity
  -> source entity write
  -> blob_attachment association delete
```

Entity-local permissions decide access to the source/target entities. Association
domain policy decides whether the subject may create or delete that relationship
kind.

## Admin Authorization

Admin authorization combines privilege ceiling and role policy.

Production admin is denied by default. When explicitly enabled, access still
requires a sufficient privilege ceiling and matching configured roles/scopes or
capabilities.

This means:

```text
privilege = final system-level ceiling
role/scope/capability = operational policy
```

A matching admin role must not grant access if the privilege ceiling is too low.

## Chokepoints

Entity/resource authorization must pass through UnitOfWork or an equivalent
internal DSL chokepoint. Application code should not bypass this with direct
repository or store access.

Current primary chokepoints:

- Operation selector authorization before ActionCall creation/execution.
- `UnitOfWorkInterpreter` for EntityStore and explicit authorization preflight.
- Admin operation rules for admin surfaces.

Direct storage/repository adapters are low-level implementation details. They are
not public authorization boundaries.
