/*
 * @since   Apr.  7, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */

# Create Authorization Phase 1 Policy

## Current Decision

For Phase 1 of CNCF default authorization, `create` on domain resources remains
open by default.

This means:

- `UnitOfWork` authorization metadata is attached to `EntityStoreCreate`
- CNCF default authorization interprets the metadata
- but ordinary domain `create` is not rejected by default at this stage

This is intentional.

## Rationale

At `create` time, ordinary authorization cannot rely on an already persisted
target record.

For `read`, `update`, `delete`, and `search/list`, CNCF can inspect the actual
resource record and evaluate:

- owner
- group
- permission / rights
- privilege

For `create`, that same mechanism is not available in the same form, because
there is no existing target record to load and inspect yet.

This does **not** mean that `create` is ungovernable.
It means `create` requires a different authorization rule.

Phase 1 focuses on establishing the authorization chokepoint and enforcing
default policies where the target resource already exists or can be evaluated as
an actual record.

## Scope of Phase 1

Phase 1 default authorization is defined as:

- `read`
  - enforce against the target entity record
- `update`
  - enforce against the target entity record
- `delete`
  - enforce against the target entity record
- `search/list`
  - enforce via result visibility filtering
- `create`
  - allow by default unless overridden

## Expected Override Routes

If a component needs stricter `create` rules now, it should use one of:

- service-level `ACCESS`
- operation-level `ACCESS`
- component-specific authorization extension in `ComponentFactory`

These are exception / override routes, not the primary default policy engine.

## Future Direction

`create` authorization should later be tightened using creation-time rules and
metadata, for example:

- whether the caller has create permission for the target entity class
- whether the caller may create into the target collection
- requested owner / group / privilege assignment
- caller privilege level
- target collection or resource type
- domain-specific creation policy

That later phase should still be enforced at the `UnitOfWorkInterpreter`
chokepoint.

## Current Summary

The current policy is:

- keep `create` open by default in Phase 1
- enforce ordinary resource authorization for existing target resources
- reserve stricter `create` semantics for a later phase
