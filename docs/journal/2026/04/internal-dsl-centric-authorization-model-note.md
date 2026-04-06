# Internal DSL-Centric Authorization Model

Date: 2026-04-07

## Summary

CNCF should treat authorization primarily as a concern of the internal DSL used by
`ActionCall` implementations, rather than as a property declared at `SERVICE` or
`OPERATION` level.

The current `ENTITY` and `ACCESS` declarations remain useful, but they should be
re-positioned as exception and override mechanisms. The default path should be:

1. `ActionCall` logic uses internal DSL only.
2. Internal DSL enforces authorization for system objects and domain objects.
3. `SERVICE` / `OPERATION` declarations are used only when the default internal
   authorization behavior must be promoted, degraded, or bypassed.

## Motivation

If authorization is enforced only at service or operation boundaries, protection is
fragile:

- a new entry point can bypass the intended checks;
- handwritten `ActionCall` logic can forget to call the right authorization helper;
- the same domain object access policy gets re-declared repeatedly.

If authorization is enforced at the internal DSL layer, the protection boundary is
closer to the actual resource access.

This gives the following properties:

- CLI / HTTP / job / script entry points share the same protection;
- `SimpleEntity` access rules can be centralized;
- operation declarations become smaller and more intentional;
- custom `ActionCall` logic stays expressive without becoming a security hole.

## Resource Families

### System Objects

System objects are framework-owned resources and capabilities.

Examples:

- configuration;
- job control;
- event store;
- runtime diagnostics;
- system administration endpoints.

These resources should be accessed through internal DSL only. The internal DSL
should verify that the current execution context is allowed to use the relevant
system capability.

This is a capability-centered authorization model.

### Domain Objects

Domain objects are application-facing resources.

Examples:

- `SimpleEntity`;
- aggregates;
- views;
- domain-specific repositories.

These resources should also be accessed through internal DSL only. When the target
object is a `SimpleEntity`, the internal DSL should apply default resource
authorization using the available identity and security information.

This is a resource-centered authorization model.

## Default Policy for SimpleEntity

For `SimpleEntity`, the default policy should be enforced by the internal DSL, not
by handwritten application logic.

The authorization inputs are expected to include:

- owner;
- group;
- permission;
- role;
- privilege.

The exact evaluation order can evolve, but the framework direction is:

- read: check `owner / group / permission / role / privilege`;
- update: check `owner / group / permission / role / privilege`;
- delete: check `owner / group / permission / role / privilege`, possibly with a
  stricter default than read/update;
- search/list: check query visibility and result visibility through the same
  mechanism.

The current `owner_or_manager` support is a transitional subset of this broader
policy model.

## Role of SERVICE and OPERATION Declarations

`SERVICE` and `OPERATION` declarations should no longer be regarded as the primary
place where ordinary authorization is defined.

Instead, they should be used for exceptions to the default internal authorization
behavior.

Typical cases:

- promotion: temporarily run with stronger authority than the caller;
- degradation: intentionally restrict an operation beyond the default resource
  policy;
- bypass: explicitly disable the normal check for a special framework-controlled
  case.

This is similar in spirit to Unix `setuid`, but CNCF should distinguish promotion
from degradation and from full bypass.

## Transitional Position of Current ENTITY and ACCESS

Current CNCF support includes:

- `ENTITY` declarations for operation target entities;
- `ACCESS` declarations such as `manager_only`.

These features remain valid, but they should be treated as transitional surface
syntax and future override hooks.

They are still useful for:

- generated metadata;
- help and introspection;
- explicit exceptions where the default internal DSL policy is insufficient.

They should not be the long-term primary enforcement point.

## Near-Term Direction

The next design steps are:

1. define the protected internal DSL boundary explicitly;
2. define the default `SimpleEntity` authorization model in more detail;
3. define declarative override concepts for promotion, degradation, and bypass;
4. move application components away from direct store access and toward protected
   internal DSL access paths.

## Status

This note records the target architectural direction.

The current implementation is still in a transitional state:

- action-level authorization hooks exist;
- `ENTITY` and `ACCESS` metadata are available;
- `SimpleEntity` owner-oriented helpers exist;
- full internal DSL-centered enforcement is not yet complete.
