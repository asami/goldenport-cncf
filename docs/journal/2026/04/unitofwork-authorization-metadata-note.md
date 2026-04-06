# UnitOfWork Authorization Metadata Note

Date: 2026-04-07

## Summary

If `UnitOfWorkInterpreter` is the main authorization chokepoint, then `UnitOfWork`
must carry enough metadata for authorization to be evaluated correctly.

The auth subject itself should come from `ExecutionContext(SecurityContext)`.
Therefore the missing side is not "who is calling", but "what resource/action is
being requested" and "whether any override semantics are intended".

This note records the minimum metadata model needed for that direction.

## Principle

Authorization metadata on `UnitOfWork` should describe the requested access, not the
caller identity.

Caller identity is already supplied by:

- `ExecutionContext`
- `SecurityContext`

`UnitOfWork` should therefore carry only the metadata needed to interpret the target
resource and the requested effect.

## Minimum Required Dimensions

At minimum, authorization metadata should express:

- resource family;
- resource type;
- target identity;
- access kind;
- optional override semantics.

These are the minimum dimensions required to evaluate ordinary authorization in the
interpreter.

## Resource Family

`UnitOfWork` should distinguish the broad resource family:

- domain object;
- system object.

This matters because the authorization model differs:

- domain object uses resource-centered checks;
- system object uses capability-centered checks.

## Resource Type

`UnitOfWork` should identify the relevant resource type.

Examples:

- `UserAccount`
- `Credential`
- event bus
- job control
- runtime configuration

For domain objects, this is the entity/object type.
For system objects, this is the system capability or system resource kind.

## Target Identity

Where applicable, `UnitOfWork` should identify the concrete target resource.

Examples:

- entity id;
- aggregate id;
- subsystem name;
- job id;
- stream id.

Not every operation has a stable existing target, but update/read/delete style
operations usually do.

For search/list, the metadata may instead identify the query scope rather than a
single target id.

## Access Kind

`UnitOfWork` should identify the intended access kind.

For domain objects, the working categories are:

- create;
- read;
- update;
- delete;
- search/list.

For system objects, the categories may differ, but the same idea applies:

- read/introspect;
- control;
- publish;
- replay;
- administer.

This field is important because authorization is usually not uniform across action
categories.

## Override Semantics

`UnitOfWork` should be able to carry optional override semantics for exceptional
cases.

The current design direction is to distinguish:

- promotion;
- degradation;
- bypass.

These are not part of the ordinary path. They are explicit exception metadata.

They should normally originate from:

- generated service/operation declarations;
- framework-owned system behavior;
- explicitly trusted runtime paths.

## Domain Object Metadata

For domain objects, the likely metadata shape is:

- `resourceFamily = domain`
- `resourceType = UserAccount`
- `targetId = ...` when applicable
- `accessKind = read | update | delete | search | create`
- optional override metadata

This is enough for the interpreter to combine:

- the auth subject from `SecurityContext`;
- the resource request from `UnitOfWork`;
- the default domain policy.

## System Object Metadata

For system objects, the likely metadata shape is:

- `resourceFamily = system`
- `resourceType = EventBus | JobControl | RuntimeConfig | ...`
- optional target identity
- `accessKind = introspect | control | publish | replay | administer | ...`
- optional override metadata

This is enough for the interpreter to combine:

- the auth subject from `SecurityContext`;
- the requested system capability/resource;
- the default system policy.

## Search/List Special Case

Search/list should be treated explicitly rather than being collapsed into ordinary
read.

The metadata should be able to express:

- that the request is a query/search/list action;
- the relevant resource type;
- potentially the scope of the query.

This is needed because search/list authorization includes both:

- whether the caller may issue the query;
- which results remain visible after filtering.

## Create Special Case

Create also needs explicit treatment.

The metadata should be able to express:

- the resource type being created;
- the access kind `create`;
- any owner-assignment semantics if needed by the interpreter or downstream policy.

Create differs from read/update/delete because the target may not yet exist as a
stable resource.

## Suggested Shape

The exact Scala encoding can evolve, but the logical shape should be close to:

- `resourceFamily`
- `resourceType`
- `targetId`
- `accessKind`
- `overrideSemantics`

Anything beyond this should be justified by a concrete authorization use case.

## Relation to Existing Metadata

Current metadata such as:

- `ENTITY`
- `ACCESS`

should be interpreted as inputs that can help construct `UnitOfWork`
authorization metadata.

They are not the final enforcement model.

The final enforcement model is:

- metadata normalized into `UnitOfWork`;
- auth subject supplied by `ExecutionContext(SecurityContext)`;
- authorization enforced in `UnitOfWorkInterpreter`.

## Near-Term Direction

The next step is not to redesign every `UnitOfWorkOp` immediately.

The practical next step is:

1. identify which existing `UnitOfWorkOp.EntityStore*` operations need explicit
   authorization metadata first;
2. introduce the minimum metadata needed for those operations;
3. keep the shape simple enough that current transitional `ENTITY` / `ACCESS`
   declarations can map onto it.
