# Security and Authorization Design Summary

Date: 2026-04-07

## Vision

CNCF security should be stronger and more disciplined than ordinary ad-hoc
application authorization, but it should not become so strict or complex that it
is no longer usable in practical software development.

The design target is therefore:

- stricter than typical application-local authorization;
- lighter than a full SELinux-style policy system;
- realistic for ordinary application and component development;
- strong enough to support current security expectations.

SELinux is treated as a standing comparison point and design reference.
It is **not** the implementation target to reproduce completely.

The goal is to preserve the strengths of serious middleware-era security
thinking, while avoiding a level of complexity that would make the framework
hard to use.

More concretely, the intended target is:

- much stronger than the security usually found in mainstream TypeScript-based
  web applications brought directly into enterprise settings;
- still simple enough to be used as an ordinary application framework;
- safe and reasonable even without large amounts of explicit security
  configuration.

The framework should therefore prefer:

- secure-by-default behavior;
- ordinary, unsurprising defaults;
- explicit opt-in for exceptional privilege behavior;
- gradual refinement instead of mandatory large policy setup.

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

## Design Balance

The intended balance is:

- keep a clear subject/object authorization model;
- keep enforcement at explicit chokepoints;
- keep object-side permission as the ordinary base rule;
- allow subject-side extension through role, capability, privilege, and security
  level;
- avoid an overly heavy mandatory policy system too early.

This means CNCF should aim for a practical middle position:

- stronger than typical application-local checks;
- lighter than full SELinux-style type enforcement and labeling.

In practical terms, the current target is close to:

- UNIX-style DAC on the object side;
- role/capability extension on the subject side;
- explicit runtime enforcement and observability at framework chokepoints.

This is the current intended landing point unless experience proves it
insufficient.

The practical expectation is:

- significantly better security discipline than typical modern web application
  stacks;
- still within the complexity range that ordinary development teams can use
  without becoming security specialists.

## Comparison Guidance

SELinux should remain a standing comparison target when evaluating design
choices.

The comparison should focus on core questions such as:

- is the subject model explicit enough?
- is the object model explicit enough?
- is enforcement done at a consistent chokepoint?
- are override semantics explicit?
- is the decision observable and auditable?

The comparison should **not** force immediate adoption of:

- full label systems;
- full type enforcement;
- full policy language complexity;
- MLS / MCS class machinery.

Those are useful reference ideas, but not current adoption goals.

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

## Checkpoint Guidance

Security and observability should be reviewed continuously at the two
chokepoints:

- `ActionCall` execution;
- `UnitOfWorkInterpreter` execution.

The review guidance is:

1. `ActionCall` checkpoint
   - verify action admission;
   - verify explicit override semantics;
   - verify action-level observability;
   - avoid placing ordinary resource authorization logic here unless there is a
     clear reason.

2. `UnitOfWorkInterpreter` checkpoint
   - verify ordinary resource/system authorization;
   - verify object-side permission enforcement;
   - verify subject/object mediation;
   - verify execution observability;
   - treat this as the primary enforcement checkpoint.

If a new execution path bypasses both checkpoints, it should be treated as a
design problem until its enforcement point is made explicit.

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

## Role and Capability Direction

The current design direction is:

- object-side permission performs the base control;
- role, capability, privilege, and security level provide extension layers.

### Base Control

The base layer is still the `SimpleEntity`-side permission model:

- owner;
- group;
- other;
- rights.

This is the ordinary default control path for resource access.

### Role

`role` should be understood as a bundle of capabilities.

Assigning a role to a subject means the subject receives the capabilities tied
to that role.

### Capability

`capability` should be interpreted as a permission over:

- `{TARGET}`
- `{ACTION}`

Typical target categories are expected to include:

- entity type;
- service;
- operation;
- system function.

Examples:

- `UserAccount:read`
- `UserAccount:update`
- `ManagementService:listUserAccounts`
- `JobControl:execute`

### Privilege and Security Level

The intended interpretation is:

- `security level` represents ordered strength or rank;
- `privilege` represents a named meaning or named band over that strength.

This keeps the concepts separate:

- role = responsibility or job function;
- privilege / security level = authorization strength or elevation class.

### Promotion and Degradation

The design direction also assumes future exception semantics such as:

- promotion;
- degradation;
- bypass.

These should be treated as exceptional override semantics, not as the ordinary
authorization path.
