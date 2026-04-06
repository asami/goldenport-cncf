# SimpleEntity Default Authorization Model

Date: 2026-04-07

## Summary

This note defines the intended default authorization model for `SimpleEntity` when
authorization is enforced primarily through protected internal DSL entry points.

The purpose of this model is to provide a framework-level baseline so that ordinary
`SimpleEntity` access does not require repeated per-operation authorization
declarations.

`SERVICE` / `OPERATION` declarations remain useful, but only for exceptions,
promotion, degradation, and explicit override behavior.

## Inputs

The default `SimpleEntity` authorization model should use the following inputs when
available:

- owner;
- group;
- permission;
- role;
- privilege.

These inputs are intentionally broader than the current transitional
`owner_or_manager` helper.

The framework should be able to apply a useful default policy even when only a
subset of these inputs is available, but the long-term model should be expressed in
terms of the full set.

## Action Categories

The default policy should be defined by action category rather than by a single
uniform rule.

The categories are:

- create;
- read;
- update;
- delete;
- search/list.

## Create

Create is special because the target resource does not yet fully exist.

Default create policy should be:

- allowed when the caller is permitted to create that type of `SimpleEntity`;
- owner assignment should be established at creation time;
- creation may be limited by role or privilege rather than owner, because the
  created resource has no stable prior owner.

In other words, create is normally not an owner-based decision at authorization
time. It is a role/privilege/policy-based decision.

## Read

Default read policy should be:

- owner may read;
- authorized group member may read;
- permission may allow read;
- role may allow read;
- privilege may allow read.

This means read is a union-style decision over the available authorization inputs.

The exact precedence rules can evolve, but the model should not assume that owner is
the only valid basis for access.

## Update

Default update policy should be:

- owner may update, subject to permission restrictions;
- authorized group member may update only when group write semantics are defined;
- permission may allow or deny update;
- role may allow update;
- privilege may allow update.

Update is usually stricter than read. The framework should assume this by default.

## Delete

Default delete policy should be stricter than update.

Default delete policy should be:

- owner may delete only when the entity type allows owner deletion;
- group-based delete should be rare and explicit;
- permission may allow delete;
- role may allow delete;
- privilege may allow delete.

This means delete should not simply inherit read/update rules unchanged.

If a component wants a softer delete policy, it should be declared explicitly as an
override.

## Search and List

Search/list needs separate treatment because it involves both query execution and
result visibility.

Default search/list policy should be:

1. the caller must be allowed to perform the query itself;
2. the resulting entities must still pass per-entity visibility rules.

This means search/list authorization is not just "can call list action".
It also includes result filtering semantics.

Long-term, the framework should be able to:

- restrict whether a search is permitted;
- filter results based on entity visibility;
- degrade query scope based on caller authority.

## Transitional Subset

The current runtime already supports a transitional subset:

- manager-only policy;
- owner-oriented `SimpleEntity` policy;
- action-level metadata (`ENTITY`, `ACCESS`);
- `ActionCall.authorize()`.

This subset is acceptable as a temporary implementation strategy, but it is not the
full target model.

The target model is:

- default `SimpleEntity` authorization is enforced by protected internal DSL;
- owner/group/permission/role/privilege all become first-class inputs;
- action metadata is used for exceptions rather than for ordinary access.

## Override Categories

When the default policy is not sufficient, overrides should fall into these
categories:

- promotion: grant stronger authority than the caller normally has;
- degradation: impose a stricter rule than the default;
- bypass: explicitly suppress the ordinary check in a controlled framework-owned
  case.

This gives a cleaner long-term interpretation for service/operation access
declarations than treating them as the normal authorization definition point.

## Near-Term Direction

The next concrete design work should define:

1. how `owner`, `group`, `permission`, `role`, and `privilege` are represented at
   runtime for `SimpleEntity`;
2. how read/update/delete/search/list checks are exposed through protected internal
   DSL entry points;
3. how result filtering is handled for search/list;
4. how override declarations map onto promotion/degradation/bypass semantics.
