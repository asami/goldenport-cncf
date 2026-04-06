/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */

# Security Subject Model

## Purpose

This note defines the direction for the authorization **subject** model in CNCF.

The current implementation already has:

- `ExecutionContext`
- `SecurityContext`
- `principal`
- `capabilities`
- `security level`

However, this is still only a provisional subject model.

The goal is to move toward a Linux SE-like subject/object authorization model,
where:

- the **subject** is represented explicitly
- the **object** is represented by `SimpleEntity` security attributes
- CNCF mediates authorization between them at execution chokepoints

## Current State

Today, the effective subject information is obtained from:

- `ExecutionContext(SecurityContext)`

The currently usable fields are:

- `principal.id`
- `principal.attributes`
- `capabilities`
- `security level`

This is sufficient for Phase 1 authorization, but not yet sufficient as a
stable canonical subject model.

## Direction

The subject model should be normalized so that CNCF authorization logic does not
have to infer too much from arbitrary `principal.attributes`.

The target shape is conceptually:

- subject id
- primary group
- supplementary groups
- roles
- privileges
- capabilities
- security level

This does not require changing the authentication mechanism first.
Authentication can remain provisional.
The immediate task is to stabilize the representation of the authenticated
subject for authorization.

## Proposed Canonical Subject Elements

### Subject Identifier

The canonical subject identifier should be derived from:

- `principal.id`

This is the anchor used for owner matching.

### Primary Group

The model should distinguish a primary group from supplementary groups.

This is not yet modeled explicitly in current `SecurityContext`.

For now, group information is inferred from `principal.attributes`, but later
there should be an explicit field.

### Supplementary Groups

The model should support multiple groups.

This is needed to evaluate `SimpleEntity.groupId` and group-scoped rights.

Today this is approximated from:

- `group`
- `group_id`
- `groups`
- `group_ids`

inside `principal.attributes`.

### Roles

Roles should be represented explicitly as a normalized set.

Today they are approximated from:

- `role`
- `roles`
- `authority`
- `authorities`

inside `principal.attributes`.

### Privileges

Privileges should be represented explicitly as a normalized set.

Today they are approximated from:

- `privilege`
- `privilege_id`
- `privileges`

inside `principal.attributes`,
plus some fallback interpretation of capabilities and security level.

### Capabilities

Capabilities are already present in `SecurityContext`.

These are useful for framework-level authorization such as:

- `manager_only`

Capabilities should remain first-class.

### Security Level

Security level is already present in `SecurityContext`.

This is not the same thing as roles or privileges, but it can be used as an
additional subject property where needed.

## Relationship to Object Model

The corresponding object-side model is `SimpleEntity` security metadata:

- owner
- group
- rights / permissions
- privilege

Authorization is the mediation between:

- normalized subject model
- normalized object model

This mediation should happen primarily at the `UnitOfWorkInterpreter`
chokepoint.

## Current CNCF Position

At the moment, CNCF is using a hybrid approach:

- subject side: provisional extraction from `SecurityContext`
- object side: partial `SimpleEntity`-like interpretation
- mediation: default authorization in `OperationAccessPolicy`

This is enough for Phase 1, but it should not be considered the final model.

## Next Steps

The next step is to introduce a more explicit canonical subject view inside CNCF,
for example as an internal normalized structure derived from `SecurityContext`.

That would allow:

- owner checks
- group checks
- privilege checks
- manager checks

to stop depending on ad-hoc key lookup in `principal.attributes`.

## Summary

The current subject model is provisional but usable.

The design direction is:

- keep authentication provisional for now
- normalize the authorization subject representation
- use it together with `SimpleEntity` security metadata
- perform mediation at CNCF execution chokepoints
