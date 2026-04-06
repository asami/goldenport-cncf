# Security and Authorization Design Summary

Date: 2026-04-07

## Scope

This note summarizes the current CNCF-side security and authorization design
direction discussed and partially implemented as of 2026-04-07.

It consolidates the following strands:

- internal DSL-centric authorization;
- protected boundary placement;
- `SimpleEntity` default authorization;
- `ActionCall` and `UnitOfWork` as dual chokepoints;
- `UnitOfWork` authorization metadata;
- transitional use of `ENTITY` / `ACCESS`.

This note is the current design summary, not a final normative spec.

## Core Direction

The main direction is:

- ordinary authorization should not be defined primarily at `SERVICE` or
  `OPERATION` level;
- ordinary authorization should be enforced through execution chokepoints;
- `SERVICE` / `OPERATION` metadata should become exception and override
  mechanisms.

The preferred enforcement path is:

1. `ActionCall` enters execution.
2. `ActionCall` uses protected internal DSL methods.
3. protected methods emit `UnitOfWork`.
4. `UnitOfWorkInterpreter` performs authorization and observability.
5. execution proceeds only after authorization succeeds.

## Two Chokepoints

### Outer Chokepoint: ActionCall

`ActionCall` execution is the outer chokepoint.

Responsibilities:

- action admission;
- coarse-grained pre-checks;
- metadata-driven operation-level checks;
- action-level observability.

`ActionCall.authorize()` remains meaningful, but it is not the complete security
model.

### Inner Chokepoint: UnitOfWorkInterpreter

`UnitOfWorkInterpreter` execution is the inner chokepoint.

Responsibilities:

- resource/system authorization;
- execution-time observability;
- final permission enforcement for actual store/engine access.

This is where ordinary authorization should converge.

## Authorization Subject

The authorization subject is supplied by:

- `ExecutionContext`
- specifically `ExecutionContext.security`, i.e. `SecurityContext`

This means auth subject information should not be duplicated in each action or
carried separately through handwritten application logic.

`SecurityContext` currently provides:

- principal;
- capabilities;
- level;
- principal attributes.

## Protected Internal DSL

The first candidate for the internal DSL is the protected method set on
`ActionCall`.

This is the intended canonical path for application logic.

Examples:

- `entity_load`
- `entity_search`
- `entity_create`
- `entity_update`
- `entity_delete`

The design goal is:

- handwritten action logic should use these protected methods;
- these methods should emit `UnitOfWork`;
- the interpreter should enforce authorization there.

## Protected Boundary

The protected boundary should be placed in front of framework-owned access APIs.

For domain objects, the practical boundary is:

- `UnitOfWorkOp.EntityStore*`
- interpreted by `UnitOfWorkInterpreter`
- delegated to `EntityStoreSpace`

For system objects, the same principle should apply to framework-owned engines and
system services:

- event system;
- job control;
- diagnostics;
- configuration;
- administrative runtime functions.

## SimpleEntity Default Policy

The target long-term policy for `SimpleEntity` is based on:

- owner;
- group;
- permission;
- role;
- privilege.

Action categories should be distinguished:

- create;
- read;
- update;
- delete;
- search/list.

Current implementation is still a subset:

- owner-or-manager style checks;
- manager-only policy;
- search/list owner-subset filtering;
- no full `group / rights / privilege` evaluation yet.

## Current Transitional Default

Current CNCF default behavior includes:

- `manager_only`
- owner-oriented `SimpleEntity` default for `read/update/delete`
- search/list filtering:
  - manager sees all;
  - ordinary user sees owner-visible subset

This is intentionally transitional.

The target model is broader and should eventually use:

- `SecurityAttributes.ownerId`
- `SecurityAttributes.groupId`
- `SecurityAttributes.rights`
- `SecurityAttributes.privilegeId`
- runtime role/privilege context from `SecurityContext`

## UnitOfWork Authorization Metadata

The current metadata direction for `UnitOfWork` is:

- `resourceFamily`
- `resourceType`
- `targetId`
- `accessKind`
- optional `access`
- `entityNames`

This metadata does not carry the caller identity.
Caller identity comes from `ExecutionContext(SecurityContext)`.

The metadata describes the requested access, not the auth subject.

## Phase 1 Coverage

Phase 1 introduced authorization metadata and interpreter-side enforcement for the
main entity-store paths:

- `EntityStoreCreate`
- `EntityStoreLoad`
- `EntityStoreSave`
- `EntityStoreUpdate`
- `EntityStoreUpdateById`
- `EntityStoreDelete`
- `EntityStoreSearch`

This means the canonical entity access path now has a security metadata channel at
`UnitOfWork` level.

## Transitional Role of ENTITY and ACCESS

`ENTITY` and `ACCESS` remain useful, but they are not the final security model.

Their current role is:

- generated metadata;
- action-level admission checks;
- inputs for `UnitOfWork` authorization metadata;
- explicit exception hooks.

They should be reinterpreted as override-oriented declarations.

## Override Semantics

The preferred long-term override categories are:

- promotion;
- degradation;
- bypass.

These are exceptional semantics.
They should not be treated as the normal place where ordinary permissions are
defined.

## ComponentFactory Role

The preferred ownership split is:

- default authorization behavior belongs to CNCF;
- `ComponentFactory` provides extension and domain-specific override behavior.

This means:

- CNCF owns ordinary default authorization;
- component code should not have to redefine ordinary `SimpleEntity` policy;
- component code may still supply special cases.

Current implementation has already moved in this direction:

- interpreter applies framework default first;
- component factory remains an extension point.

## Search/List Position

Search/list is treated as a first-class authorization case, not as ordinary read.

It requires two concerns:

1. query admission;
2. result visibility filtering.

Current implementation now includes a focused result visibility filter in the
interpreter path for canonical `EntityStoreSearch`.

## Current Gaps

The main remaining gaps are:

1. full use of `group / rights / privilege` in `SimpleEntity` authorization;
2. full definition of override semantics (`promotion / degradation / bypass`);
3. consistent treatment of system-object authorization through the same model;
4. gradual reduction of direct raw-store thinking in handwritten application logic.

## Practical Next Step

The most important next implementation step is:

- use actual `SecurityAttributes.rights`, `groupId`, and `privilegeId`
  in CNCF default authorization.

That is the point where the current transitional subset becomes a real
`SimpleEntity` policy.
