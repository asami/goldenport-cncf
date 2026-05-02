# Internal DSL Guideline Note

Date: 2026-05-02

## Purpose

This note records the working guideline for CNCF internal DSLs.

The internal DSL is the application-facing framework boundary. It exposes
domain/system operations in terms that are safe for application authors and
translates those requests into `UnitOfWork` programs.

This note incorporates the direction from
`docs/journal/2026/04/internal-dsl-protected-boundary-note.md`.

## Protected Boundary

The protected boundary should be placed at framework-owned domain/system access
APIs used by `ActionCall` logic. It should not be placed only at CLI, HTTP, or
other surface entry points.

For domain objects, the protected boundary sits in front of:

- create;
- load;
- save/update;
- delete;
- search/list;
- internal/direct variants of load and search.

For system objects, the protected boundary sits in front of framework-owned
engines and stores, such as:

- job control;
- event publication, replay, and introspection;
- system configuration and diagnostics;
- runtime control and admin APIs.

The same rule applies on both sides: application logic asks the internal DSL for
domain/system behavior, and the DSL classifies and delegates through protected
framework execution paths.

## Guideline

The internal DSL should translate domain/system operations into `UnitOfWork`.

For entity operations, the DSL should emit `UnitOfWorkOp.EntityStore*` or a more
specific framework operation that eventually passes through the same `UnitOfWork`
entity path.

The internal DSL may provide `internal` or `direct` variants, but those names
must be precise:

- `internal` may bypass public exposure and application-level visibility;
- `direct` may bypass `EntitySpace` or resident working-set lookup when needed;
- neither term means "ignore logical delete";
- neither term means "use raw DataStore".

Internal entity lookup for shortid, slug, owner checks, or post-operation binding
should still use a safe entity path. If that path does not exist, add it to the
DSL instead of reaching into `DataStoreSpace`.

For identity and uniqueness work, prefer purpose-specific DSL helpers over
generic internal search. Examples include:

- unique field checks for `slug`, `shortid`, or application-defined names;
- identity resolution from full `EntityId`, `shortid`, entity-id entropy, or
  `slug`;
- tenant-aware lookup based on `ExecutionContext`.

These helpers should emit dedicated `UnitOfWork` intents, such as uniqueness or
identity resolution, rather than broad search operations. The dedicated intent
lets `UnitOfWork` check `EntitySpace` / working set first, fall back to
`EntityStore`, preserve `deletedAt` logical delete filtering, and apply tenant
scope consistently.

## Authorization Flow

The internal DSL should be the place where default access classification is
introduced.

The intended flow is:

1. `ActionCall` requests domain/system behavior through internal DSL.
2. The DSL classifies the target as domain object or system object.
3. The DSL attaches authorization and lifecycle metadata to the `UnitOfWork`
   request.
4. `UnitOfWorkInterpreter` enforces the metadata before delegating to the
   storage/runtime layer.

## Review Checklist

When reviewing an internal DSL helper, check:

- Does it emit `UnitOfWork` rather than performing effects directly?
- Does it classify domain object vs system object access clearly?
- Does it attach enough metadata for `UnitOfWorkInterpreter` to enforce
  authorization?
- Does entity read/search flow through a safe entity path?
- Does any `direct` or `internal` helper preserve logical delete semantics?
- Are identity and uniqueness helpers modeled as purpose-specific UoW intents
  instead of broad internal searches?
- Is tenant scope resolved from `ExecutionContext` rather than assembled in
  application code?
- Is raw access explicit, named, and limited to repair/diagnostic/seed/import
  style purposes?

## Open Questions

- Should CNCF add guard specs for raw entity `DataStore` access outside
  framework-owned infrastructure?
- Which internal DSL helpers are missing for common handwritten component
  patterns?
- Should `internal` and `direct` be renamed or split where their semantics are
  currently ambiguous?
