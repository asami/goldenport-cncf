# UnitOfWork Guideline Note

Date: 2026-05-02

## Purpose

This note records the working guideline for CNCF `UnitOfWork`.

`UnitOfWork` is the execution boundary for framework-owned effects. It owns the
concrete operation algebra, authorization metadata, lifecycle handling, metrics,
and the choice between `EntitySpace`, `EntityStore`, and lower-level
infrastructure.

This note incorporates the direction from
`docs/journal/2026/04/unitofwork-authorization-phase1-targets-note.md`.

## Guideline

For entity processing, `UnitOfWork` should normally delegate to `EntitySpace` and
`EntityStoreSpace`, not directly to `DataStoreSpace`.

Entity invariants that must not vary by application include:

- logical delete exclusion for normal and internal reads/searches;
- lifecycle defaulting and state transition hooks;
- authorization metadata and enforcement;
- visibility filtering for public/search paths;
- entity-space cache and working-set invalidation;
- entity access metrics and call-tree classification.

`DataStore` is the physical persistence substrate. For entity data it is an
implementation detail, not the normal `UnitOfWork` execution surface.

If `UnitOfWork` provides a direct entity operation, it should still preserve
entity invariants unless the operation is explicitly named and documented as a
raw repair/diagnostic path.

Logical delete is currently defined by `deletedAt` / `deleted_at`. Records with
that marker are absent from ordinary, direct, internal, uniqueness, and identity
lookup paths. `aliveness=dead` and content states such as
`postStatus=archived` are lifecycle/publication states, not logical delete
markers.

Identity and uniqueness checks are entity intents, not ad hoc searches. A
`UnitOfWork` identity/uniqueness operation should:

- check `EntitySpace` / working set first where available;
- fall back to safe `EntityStore` behavior;
- exclude `deletedAt` records;
- apply `EntityIdentityScope.CurrentContext` using tenant or organization data
  from `ExecutionContext` when present;
- behave as global scope when the context has no tenant data;
- allow explicit global or explicit field scopes for framework/admin cases.

## Entity Authorization Targets

The primary entity `UnitOfWorkOp` operations for ordinary application behavior
are:

- `EntityStoreCreate` -> `create`;
- `EntityStoreLoad` -> `read`;
- `EntityStoreUpdateById` -> `update`;
- `EntityStoreDelete` -> `delete`;
- `EntityStoreSearch` -> `search/list`.
- `EntityStoreUniqueValueExists` -> unique field collision check;
- `EntityStoreResolveIdentity` -> canonical entity id resolution.

These operations form the canonical authorization and lifecycle target set for
ordinary entity behavior.

`EntityStoreSave`, full `EntityStoreUpdate`, `EntityStoreDeleteHard`,
`EntityStoreLoadDirect`, and `EntityStoreSearchDirect` need stronger or more
precise semantics and should not be treated as casual application escape
hatches.

## DataStore Exceptions

Raw `DataStore` access for entity-backed collections is allowed only for
framework-owned special purposes.

Allowed categories:

- seed import and startup import;
- physical migration and repair;
- datastore admin/diagnostic screens;
- low-level datastore tests;
- `EntityStore` implementation internals.

Such APIs should make their nature visible in names, comments, metrics, or
routes. Prefer terms such as `raw`, `repair`, `diagnostic`, `seed`, `import`, or
`physical`.

Raw APIs that can return logically deleted records should say so explicitly.

## Transitional Interpretation

The current runtime is still transitional:

- `ActionCall.authorize()` exists and is active;
- `ENTITY` / `ACCESS` metadata feeds operation-level protection;
- `OperationAccessPolicy` contains manager and owner-oriented policy helpers;
- some framework code still contains low-level entity store or datastore access.

During this transition, new `UnitOfWork` behavior should be designed so that
authorization, lifecycle, and raw-store avoidance can move downward into the
internal DSL and `UnitOfWork` layers without changing the external operation
model.

## Review Checklist

When reviewing `UnitOfWork` behavior, check:

- Does entity processing flow through `EntitySpace` / `EntityStoreSpace`?
- Is raw `DataStore` access absent from ordinary entity operations?
- Are logical delete and lifecycle checks centralized?
- Does a new entity operation map to a clear access kind?
- Does a new direct operation preserve entity invariants?
- Do uniqueness and identity operations check `EntitySpace` before falling back
  to `EntityStore`?
- Do uniqueness and identity operations exclude `deletedAt` records and respect
  `ExecutionContext` tenant scope?
- If raw access is necessary, is the API explicitly named as raw/repair/
  diagnostic/seed/import/physical?

## Open Questions

- Should `EntityStoreLoadDirect` and `EntityStoreSearchDirect` be renamed to
  clarify that they are safe entity paths, not raw storage paths?
- Should raw repair/diagnostic entity access become a separate `UnitOfWorkOp`
  family?
- Should `UnitOfWorkInterpreter` reject raw entity `DataStore` access except
  through explicitly whitelisted operations?
