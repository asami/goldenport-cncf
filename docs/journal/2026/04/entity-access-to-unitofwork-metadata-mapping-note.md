# ENTITY and ACCESS to UnitOfWork Metadata Mapping Note

Date: 2026-04-07

## Summary

This note records how current surface-level metadata such as `ENTITY` and `ACCESS`
should map into Phase 1 `UnitOfWork` authorization metadata.

The purpose is transitional:

- keep current CML and generated metadata useful;
- avoid treating them as the final enforcement mechanism;
- normalize them into `UnitOfWork`-level metadata consumed by
  `UnitOfWorkInterpreter`.

## Principle

`ENTITY` and `ACCESS` are not the final authorization model.

They should be treated as inputs that help derive:

- resource family;
- resource type;
- target id;
- access kind;
- override semantics.

The final enforcement point remains:

- `UnitOfWorkInterpreter`
- using `ExecutionContext(SecurityContext)` as auth subject.

## Mapping from ENTITY

### ENTITY -> resourceFamily

When `ENTITY` is declared for an operation, the normalized `UnitOfWork` metadata
should treat it as:

- `resourceFamily = domain`

This is because `ENTITY` identifies a domain resource, not a system capability.

### ENTITY -> resourceType

When `ENTITY UserAccount` is declared, normalized metadata should carry:

- `resourceType = UserAccount`

If multiple entity names are declared, normalized metadata should either:

- carry the primary entity as `resourceType` and the others as related targets; or
- carry multiple resource types if the Phase 1 metadata shape allows it.

For Phase 1, a primary resource type is sufficient.

### ENTITY -> targetId

`ENTITY` alone does not determine the target id.

The target id must be resolved from:

- the request record;
- generated operation metadata;
- or future internal DSL conventions.

Current generated metadata such as:

- `target = userAccountId`

should be treated as a temporary input to derive `targetId`.

## Mapping from ACCESS

`ACCESS` should be mapped to override semantics rather than to the ordinary access
kind itself.

This is important because the ordinary access kind is derived from the actual
`UnitOfWorkOp`, not from surface syntax.

### manager_only

`ACCESS manager_only` should map to an override semantic representing degradation of
ordinary resource access into manager-only execution.

Logical meaning:

- ordinary resource policy is not enough;
- a stricter manager-only gate is requested.

### owner_or_manager

`ACCESS owner_or_manager` should map to a transitional override semantic that asks
the interpreter to use the owner-oriented `SimpleEntity` policy explicitly.

Long-term this should become unnecessary for ordinary `SimpleEntity` operations,
because owner/group/permission/role/privilege should be part of the default domain
policy.

### future promotion/degradation/bypass

When CNCF introduces explicit override declarations, `ACCESS` should naturally map
into:

- promotion;
- degradation;
- bypass.

The current known policies fit mainly into degradation or explicit policy selection.

## Mapping from UnitOfWorkOp

The ordinary access kind should be derived from `UnitOfWorkOp`, not from `ENTITY`
or `ACCESS`.

Phase 1 mapping:

- `EntityStoreCreate` -> `create`
- `EntityStoreLoad` -> `read`
- `EntityStoreUpdateById` -> `update`
- `EntityStoreDelete` -> `delete`
- `EntityStoreSearch` -> `search/list`

This is the stable part of the mapping.

## Combined Transitional Mapping

In transitional terms, the normalization process should look like this:

1. look at the actual `UnitOfWorkOp`;
2. derive `accessKind` from that operation;
3. derive `resourceFamily` and `resourceType` from `ENTITY` if available;
4. derive `targetId` from request/metadata conventions if applicable;
5. derive optional override semantics from `ACCESS`.

This gives a path from current generated metadata to interpreter-level enforcement
without pretending that current surface syntax is the final model.

## Example

For a current operation like:

- `ENTITY UserAccount`
- `ACCESS manager_only`
- `EntityStoreSearch(UserAccountQuery, ...)`

the normalized metadata should be read as:

- `resourceFamily = domain`
- `resourceType = UserAccount`
- `accessKind = search/list`
- `overrideSemantics = degradation(manager_only)`

For:

- `ENTITY UserAccount`
- no explicit `ACCESS`
- `EntityStoreLoad(userId, ...)`

the normalized metadata should be read as:

- `resourceFamily = domain`
- `resourceType = UserAccount`
- `targetId = userId`
- `accessKind = read`
- no override semantics

## Why This Matters

This mapping keeps current CML and generated metadata useful while moving CNCF
toward the stronger design:

- auth subject from `ExecutionContext(SecurityContext)`;
- resource/action metadata on `UnitOfWork`;
- enforcement in `UnitOfWorkInterpreter`.

It also keeps the migration incremental rather than requiring a disruptive
redesign of generated models all at once.

## Near-Term Direction

The next implementation step should be:

1. define the minimal Phase 1 metadata structure on the five target
   `UnitOfWorkOp.EntityStore*` operations;
2. normalize existing `ENTITY` / `ACCESS` metadata into that structure;
3. enforce authorization in `UnitOfWorkInterpreter` while preserving current
   action-level checks as fallback during transition.
