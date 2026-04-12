# UnitOfWork Authorization Phase 1 Targets Note

Date: 2026-04-07

## Summary

This note identifies which `UnitOfWorkOp.EntityStore*` operations should receive
authorization metadata support first.

The design goal is not to solve every `UnitOfWork` case immediately.
The goal is to start with the operations that matter most for ordinary
`SimpleEntity`-based application behavior.

## Principle

Phase 1 should target the smallest useful set that covers the most common domain
resource access patterns:

- read;
- update;
- delete;
- search/list;
- create.

This directly supports the current `SimpleEntity`-oriented application style used by
components such as `textus-user-account`.

## Phase 1 Target Operations

The recommended Phase 1 targets are:

- `UnitOfWorkOp.EntityStoreLoad`
- `UnitOfWorkOp.EntityStoreUpdateById`
- `UnitOfWorkOp.EntityStoreDelete`
- `UnitOfWorkOp.EntityStoreSearch`
- `UnitOfWorkOp.EntityStoreCreate`

## Why These Five

### EntityStoreLoad

This is the canonical read path for a concrete entity.

It is required for:

- self-service entity reads;
- owner-or-manager checks;
- ordinary application read behavior.

If the framework cannot authorize `EntityStoreLoad`, then ordinary resource reads
remain outside the intended authorization model.

### EntityStoreUpdateById

This is the practical patch/update route used by cozy-generated update shapes.

It is especially important because many current application updates use patch-style
mutation rather than full entity replacement.

This operation should become one of the first-class authorization targets for
resource-centered update semantics.

### EntityStoreDelete

Delete usually has stricter rules than read or update.

This means the interpreter must be able to distinguish delete from other access
kinds early.

Supporting `EntityStoreDelete` in Phase 1 is necessary if the framework wants to
express a real default `SimpleEntity` policy rather than a read/update-only subset.

### EntityStoreSearch

Search/list is not just a performance issue or query issue. It is an authorization
issue.

Supporting `EntityStoreSearch` early is necessary because:

- query admission must be checked;
- result visibility must eventually be filtered.

Without this operation in Phase 1, the authorization model remains incomplete for
list/search-heavy components.

### EntityStoreCreate

Create is special because the target entity does not yet exist as a stable resource,
but it is still one of the ordinary domain access categories.

Supporting create in Phase 1 ensures the model is not biased toward only existing
resource operations.

## Deferred from Phase 1

The following operations can be deferred initially:

- `EntityStoreSave`
- `EntityStoreUpdate`
- `EntityStoreDeleteHard`
- `EntityStoreLoadDirect`
- `EntityStoreSearchDirect`

## Why They Can Wait

### Save and Full Update

`EntityStoreSave` and `EntityStoreUpdate` matter, but many current application paths
can already be meaningfully covered by:

- create;
- patch update by id;
- ordinary read;
- delete;
- search.

They can follow once the authorization metadata shape is proven on the primary
working subset.

### DeleteHard

`EntityStoreDeleteHard` should exist under stronger control and is likely to need
explicitly stronger semantics. It is better handled after the ordinary delete path
is stabilized.

### Direct Operations

`EntityStoreLoadDirect` and `EntityStoreSearchDirect` are explicitly marked as
special-use direct paths.

These are not good Phase 1 targets because they represent escape-hatch behavior.
Their semantics should be designed after the ordinary canonical paths are settled.

## Practical Mapping to Access Kinds

The intended initial mapping is:

- `EntityStoreCreate` -> `create`
- `EntityStoreLoad` -> `read`
- `EntityStoreUpdateById` -> `update`
- `EntityStoreDelete` -> `delete`
- `EntityStoreSearch` -> `search/list`

This gives a simple initial bridge from concrete `UnitOfWorkOp` variants to the
authorization categories already identified for `SimpleEntity`.

## Why This Order Fits Current CNCF

Current `UnitOfWorkInterpreter` already treats these operations as the ordinary
execution path:

- `EntityStoreLoad` delegates through entity-space resolution;
- `EntityStoreSearch` delegates through entity-space search;
- `EntityStoreCreate` / `UpdateById` / `Delete` are where actual entity mutation is
  performed.

This means Phase 1 can strengthen existing canonical paths rather than inventing new
ones.

## Near-Term Direction

The next implementation step should be:

1. attach minimal authorization metadata to these five operations;
2. interpret that metadata in `UnitOfWorkInterpreter`;
3. keep `ENTITY` / `ACCESS` and action-level metadata as inputs and fallback
   mechanisms during transition.

Update on 2026-04-13: the baseline metadata and policy path is implemented for
entity store UoW operations. The implemented contract is documented in
`docs/design/entity-authorization-model.md`, with remaining work in
`docs/notes/entity-authorization-implementation-note.md`.
