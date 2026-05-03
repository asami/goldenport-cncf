# CNCF Authorization Model Article Notes

Date: 2026-05-03
Status: journal note

## Purpose

This note collects article-oriented source material for explaining the CNCF
authorization model.

This is not a normative design document. The current canonical references are:

- [authorization-concepts.md](../../../design/authorization-concepts.md)
- [entity-authorization-model.md](../../../design/entity-authorization-model.md)
- [operation-authorization-model.md](../../../design/operation-authorization-model.md)
- [phase-18.md](../../../phase/phase-18.md)
- [phase-18-checklist.md](../../../phase/phase-18-checklist.md)
- [cncf-development-strategy.md](../../../strategy/cncf-development-strategy.md)

## Short Thesis

CNCF authorization is a capability-oriented model with explicit guards.

The basic decision shape is:

```text
guards are satisfied
AND
effective capabilities satisfy required capabilities
```

The important point is that roles, permissions, relations, and future ACLs are
not competing authorization currencies. They are inputs that are normalized into
effective capabilities or guards at the runtime authorization chokepoints.

## Why This Model Exists

CNCF tries to avoid two failure modes:

- ad hoc application-local checks that are easy to bypass;
- a heavy policy system that ordinary component development cannot use.

The intended middle ground is:

- stronger than typical web-application authorization;
- simpler than a full SELinux-style mandatory policy system;
- secure by default for ordinary Entity work;
- extensible through policy when a component needs stricter rules.

## Main Layers

### Operation Authorization

Operation authorization is selector-level admission control.

It answers:

```text
May this subject invoke this Component / Service / Operation selector at all?
```

It runs before ActionCall construction/execution. Command, REST, Web, Form API,
server, client, job, and script entry points are expected to converge on the
same operation authorization checkpoint.

Operation authorization can check:

- anonymous access;
- operation mode;
- privilege ceiling;
- role, scope, and capability requirements;
- explicit deny rules for disabled surfaces.

### Entity And Resource Authorization

Entity/resource authorization is object/resource access control.

It answers:

```text
May this subject perform this action against this Entity, EntityCollection,
Association domain, store, or runtime resource?
```

It runs at the UnitOfWork/internal DSL boundary through `UnitOfWorkInterpreter`
and `OperationAccessPolicy`.

Operation admission does not replace Entity/resource authorization. A subject
may be allowed to call an operation and still be denied when the operation tries
to read, update, delete, attach, detach, or inspect a protected resource.

## Core Vocabulary

### Principal, SecurityContext, And SecuritySubject

A principal is the runtime caller identity.

`SecurityContext` is the ingress/runtime carrier. It holds principal identity,
security level, capabilities, and raw principal attributes supplied by runtime
or authentication providers.

`SecuritySubject` is the normalized CNCF authorization view. Authorization code
should use `SecuritySubject` rather than re-reading raw request attributes.

### Privilege

Privilege is a coarse runtime/system guard.

It answers:

```text
Is this subject allowed to reach this class of system capability at all?
```

Examples include:

- `anonymous`
- `user`
- `operator`
- `system`
- `internal`

Privilege is intentionally small. It is a final guard for high-risk surfaces,
especially production admin. It is not a replacement for roles, scopes,
capabilities, or Entity permissions.

### Role

A role is an operational responsibility assignment.

Examples:

- `system_admin`
- `component_operator`
- `application_operator`
- `support_operator`
- `audit_viewer`

In the current descriptor-backed model, role definitions expand into effective
subject capabilities. Role includes are transitive and cycle-safe.

Example shape:

```yaml
security:
  authorization:
    roles:
      blob_user:
        capabilities:
          - collection:blob:create
          - association:blob_attachment:create
      blob_operator:
        includes:
          - blob_user
        capabilities:
          - store:blobstore:status
```

Full role lifecycle administration and identity-provider role management remain
future Security work.

### Scope

Scope limits where a role or capability applies.

Examples:

- `component:cwitter`
- `application:storefront`
- `tenant:tenant-a`

Scope is a subject-side policy input. It is part of the operational policy
surface, not an Entity-local permission bit.

### Capability

A capability is the normalized positive grant.

It can appear on either side of the decision:

```text
effective capability: what this subject can do
required capability:  what this resource/action requires
```

Examples:

- `collection:blob:create`
- `collection:blob:read`
- `association:blob_attachment:create`
- `association:blob_attachment:delete`
- `store:blobstore:status`

Capabilities should describe what is allowed. They should not try to enumerate
every contextual denial or exception. Conditional and negative requirements are
guards.

### Guard

A guard is a condition that must be satisfied before or alongside capability
comparison.

It answers:

```text
Is this subject/context allowed to use this class of capability here and now?
```

Guard examples:

- privilege ceiling;
- ABAC/contextual predicates;
- production/develop/test mode;
- operation mode;
- manager-only;
- owner-or-manager;
- runtime feature enablement.

Guards are necessary because capability-only modeling is concise for positive
grants but becomes verbose when expressing denial, exception, or contextual
"not unless" rules.

### Permission

Permission is object-side access metadata.

For SimpleEntity, the canonical permission model is the compact
`SecurityAttributes` owner/group/other table:

```text
owner: read/write/execute
group: read/write/execute
other: read/write/execute
```

Permission is an object-local capability grant table. At runtime, CNCF classifies
the subject as owner, group, or other for the target Entity. The matching row
grants local read, write, and execute capabilities.

This is why the model can stay compact:

```text
permission = compact object-local grant table
capability = normalized ability used by authorization comparison
```

The permission bits are intentionally small. They are mapped to access kinds by
policy.

Default mapping:

```text
read        -> read
search/list -> read
update      -> write
write       -> write
delete      -> write or execute
execute     -> execute
```

`create` is not an Entity-instance permission because the target Entity does not
exist yet. It is a collection-level decision.

### Policy

Policy computes the authorization inputs.

Subject-side policy derives effective capabilities from direct capabilities,
roles, groups, scopes, and subject attributes.

Object/resource-side policy derives required capabilities from resource family,
resource type, collection, association domain, operation selector, target object,
access kind, and execution context.

Object-local grant policy derives target-local capabilities from permission,
future ACLs, and relations.

Guard policy evaluates non-grant constraints such as privilege ceiling, ABAC,
runtime mode, operation mode, and feature enablement.

### Relation

Relation is a context-dependent grant source.

Examples:

- current user is the author;
- current user is the assignee;
- current customer id matches the entity customer id;
- current tenant is a participant in the target tenant boundary.

Relations grant capabilities only when the subject/object relationship matches.
They are useful when owner/group/other permission is too coarse.

### ABAC

ABAC is treated as a contextual guard or filter.

Examples:

- post is published;
- lifecycle state is active;
- tenant boundary matches;
- publish window is open;
- environment is production or development.

ABAC should not be modeled as stored subject capability. It constrains whether a
capability or object-local grant is usable for the current context.

### ACL

CNCF does not currently have a first-class arbitrary ACL list.

The current object-side baseline is the compact owner/group/other permission
model, plus relation and ABAC support where needed. A future ACL feature should
be an explicit grant/deny list for cases that do not fit compact permission or
relation rules.

ACL administration, subject grant administration, role lifecycle UI, and
organization-grade policy management remain broader Security work.

## Resource Families

Authorization requests should identify the resource family being protected.

Current important families:

- `operation`: Component / Service / Operation selector admission.
- `domain`: ordinary Entity and Aggregate/View backing resources.
- `association`: generic Association domains such as `blob_attachment`.
- `store`: runtime stores such as BlobStore.
- `system`: system/admin runtime resources.

Blob is the first large driver for these generic families.

Blob examples:

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
  -> store/blobstore status
```

The point is that Blob does not need a private authorization model. It uses the
same collection, association, store, Entity permission, and admin-operation
surfaces as other resources.

## Where User List And Read Rights Are Managed

There are two different questions:

```text
May this subject access the collection/list operation?
May this subject see this specific row/object?
```

Collection/list admission is a collection or resource policy decision. It can
require subject capabilities such as collection read/list grants.

Specific row/object visibility is an Entity/resource decision. It is evaluated
through object-side permission, relation rules, ABAC guards, and policy mapping.

Search/list therefore has two steps:

1. authorize the search/list action itself;
2. filter result visibility using the target Entity/resource authorization
   model.

This is why search can return different rows depending on the caller.

## Chokepoints

CNCF authorization depends on consistent runtime chokepoints.

Primary chokepoints:

- operation selector authorization before ActionCall creation/execution;
- UnitOfWork/internal DSL authorization for EntityStore and resource access;
- explicit UnitOfWork authorization preflight where an operation must authorize
  before performing a non-Entity side effect;
- admin operation rules for admin surfaces.

Direct repository, store, or adapter access is not a public authorization
boundary. Component logic should go through ActionCall and UnitOfWork/internal
DSL paths so authorization, metrics, and observability stay aligned.

FunctionalActionCall is the preferred style for CNCF component work. Procedural
ActionCall support exists, but the model assumes protected runtime APIs rather
than arbitrary direct storage access.

## Admin Authorization

Admin authorization combines:

- production/development mode policy;
- privilege ceiling;
- role/scope/capability policy;
- resource policy where the admin operation touches protected collections,
  association domains, stores, or system resources.

Production admin is denied by default. Enabling an admin route is not enough; the
subject must still satisfy the privilege and configured role/capability policy.

## Failure Diagnostics

Authorization denial is represented as an ordinary
`Consequence.Failure(Conclusion)`.

The `Conclusion` observation carries machine-readable diagnostic structure:

- `Cause.Kind.Capability` for missing capabilities;
- `Cause.Kind.Permission` for owner/group/other permission-bit denial;
- `Cause.Kind.Guard` for privilege ceiling, manager-only, owner-or-manager,
  ABAC, or similar guard failure;
- `Cause.Kind.Relation` for relation-based access failure.

`Descriptor.Facet` carries details:

- `Reason(...)`;
- `Capability(...)`;
- `Permission(...)`;
- `Guard(...)`;
- `Relation(...)`;
- parameter, field-path, policy, algorithm, limit, expected, and actual facets
  when useful.

Metrics, dashboards, Web/admin diagnostics, and observability derive diagnostic
keys from the `Conclusion` structure. Reusable CNCF components should not create
component-local error structures or write framework diagnostic meanings into
application-owned `Status.detailCodes`.

## Current Implementation Status

Implemented baseline:

- operation selector authorization through `OperationAuthorizationRule`;
- Entity/resource authorization through UnitOfWork and `OperationAccessPolicy`;
- SimpleEntity owner/group/other permission evaluation;
- default access-kind to permission-bit mapping;
- create as collection-level authorization;
- search/list result visibility filtering;
- relation-based grants for Entity access;
- ABAC/natural conditions as mandatory guards;
- descriptor-backed role-to-capability expansion;
- descriptor-backed collection, association-domain, and store resource policy;
- Blob operation integration on generic authorization policies;
- read-only authorization visibility in projections/manual/admin surfaces;
- structured diagnostics projected from `Consequence.Failure(Conclusion)`.

Deferred or broader Security work:

- arbitrary ACL lists;
- general subject grant administration UI;
- role-definition lifecycle UI;
- identity-provider role lifecycle integration;
- organization-grade policy management;
- broader audit logging expansion;
- `Status.detailCode` / `detailCodes` / `strategies` redesign.

## Article Angles

Potential article structure:

1. Start from the problem: operation-level authorization alone is too far from
   the protected resource.
2. Explain the dual chokepoint model: operation admission plus UnitOfWork
   resource authorization.
3. Introduce capability as the shared positive-grant currency.
4. Explain why guards are necessary to avoid negative-capability explosion.
5. Explain permission as the compact object-local grant table.
6. Show how role, permission, relation, ABAC, and future ACLs fit without
   competing with each other.
7. Use Blob attach/detach as a concrete example of collection, Entity,
   Association, and store authorization working together.
8. Close with diagnostics: denials are structured `Conclusion` values, so
   metrics and dashboards are projections of the same error model.

## Compact Explanation

The shortest explanation is:

```text
CNCF authorizes operations at the selector boundary and resources at the
UnitOfWork boundary. Roles expand into subject capabilities. Entity permissions
derive object-local capabilities. Relations can grant contextual capabilities.
ABAC and privilege act as guards. Policy computes the effective and required
capabilities, then diagnostics are reported as structured Conclusion values.
```
