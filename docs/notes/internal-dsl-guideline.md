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
- runtime/component/action configuration lookup;
- structured DSL/config parsing;
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

For runtime configuration, component logic should use protected scalar helpers
instead of reading raw runtime maps or subsystem configuration directly. The
current scalar helper family is:

- `config_string(key)`;
- `config_string(primary, compatibility)`;
- `config_int(key)`;
- `config_double(key)`;
- `config_boolean(key)`.

For runtime time, component and provider logic should use the execution clock
through the protected internal DSL:

- `execution_clock` for APIs that require a `java.time.Clock`;
- `current_instant` for an absolute current timestamp;
- `current_zoned_datetime` for the current timestamp in the execution-context
  timezone.

Component and provider logic must not call `Clock.systemUTC()`,
`Clock.systemDefaultZone()`, `Instant.now()`, or `ZonedDateTime.now()` directly.
The runtime bootstrap owns system-clock and offset-clock selection, and every
component action observes the clock injected into its `ExecutionContext`.

For structured DSL/config parsing, component logic should use:

- `parse_dsl_document(path)`;
- `parse_dsl_document(filename, content)`.

These parsing helpers are part of the internal DSL boundary even though they do
not necessarily emit a `UnitOfWork` operation in v1. They still centralize
framework concerns: source naming, UTF-8 file handling, config decoder choice,
CallTree classification, future provenance, and future security policy.

For outbound HTTP, component and provider logic should use CNCF HTTP internal
DSL routes instead of constructing an HTTP client directly:

- `http_get(path, headers)`;
- `http_post(path, body, headers)`;
- `http_post_bag(path, body, headers)`;
- `http_put(path, body, headers)`;
- `UnitOfWorkOp.HttpGet`, `UnitOfWorkOp.HttpPost`, `UnitOfWorkOp.HttpPostBag`,
  or `UnitOfWorkOp.HttpPut` through the current `ExecutionContext` when the
  code is outside an `ActionCallFeaturePart` helper surface.

Application and provider code must not instantiate direct outbound HTTP
clients, such as `java.net.http.HttpClient`, sttp clients, requests clients, or
curl-style subprocesses, for normal component behavior. The internal DSL route
records the operation in the CallTree, gives the runtime HTTP driver a single
chokepoint, and keeps timeout policy, sandboxing, egress policy, metrics,
retry, audit, and deterministic fixture substitution inside CNCF. A provider
service obtained through `ExtensionPoint.provide(...)(using ExecutionContext)`
may capture that `ExecutionContext` and use it later to execute HTTP
`UnitOfWork` operations. It should not keep a global HTTP client outside the
CNCF runtime path.

For durable component data, application and domain logic should request a
purpose-specific persistence operation through its internal DSL or a
component-owned persistence port. The framework-owned adapter uses the CNCF
`DataStoreSpace` and component datastore abstraction. Application/domain code
must not open files or embedded databases, create database connections, choose
a backend, construct SQL, or use vendor-specific types.

The runtime binds a `ComponentDataStore` through declared configuration. The
development profile may select SQLite and the production profile may select a
shared external backend, but this is configuration/infrastructure work, not
component behavior. A component may use only its admitted component datastore
and its own named collections; it must not enumerate, query, or migrate another
component's data merely because both are hosted by one shared datastore.

This boundary also carries security and observability. It centralizes secret
handling, component/tenant scope, authorization, collection admission,
redaction, and audit; it also gives datastore effects one CallTree, metric, and
failure-attribution route. A direct backend escape may be technically possible
in infrastructure, but it loses those controls and must never become ordinary
component behavior.

Framework-owned embedded-datastore helpers remain an infrastructure adapter
for legacy/local provisioning. They are not an application-facing way to pass
SQL statements. New component persistence should first model the required
operation and record shape, then expose it through an internal DSL or
persistence port backed by `DataStoreSpace`. The default local provisioning
location and all backend-specific settings remain launcher/runtime concerns.

For identity and uniqueness work, prefer purpose-specific DSL helpers over
generic internal search. Examples include:

- unique field checks for `slug`, `shortid`, or application-defined names;
- identity resolution from full `EntityId`, `shortid`, entity-id entropy, or
  `slug`;
- tenant-aware lookup based on `ExecutionContext`.

`entity_upsert(create)` is the canonical create-or-update boundary when the
component has already assigned a stable entity identity to a generated entity
create shape. The create shape must expose that identity; omission is an
invalid operation rather than a request to generate an identity. CNCF checks
whether the identity exists, applies create authorization and creation
defaults to a new record, or applies update authorization and merges only the
domain fields into an existing record. Existing managed lifecycle and security
attributes are retained on update. The datastore save is an atomic upsert.
Components must not implement this distinction by calling a datastore directly
or by treating `entity_save` as an unauthorised upsert.

`entity_update_internal(id, patch)` is the canonical patch-update boundary for
a server-owned Entity. It canonicalizes the target identity, verifies the
component datastore boundary, and emits `EntityStoreUpdateById` with
`ServiceInternal` authorization. Component code must use this helper instead of
constructing `UnitOfWorkOp`, `UnitOfWorkAuthorization`, or collection security
metadata itself.

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

For helper families that do not yet emit `UnitOfWork`, the same chokepoint rule
still applies: the helper is the framework-owned API and component logic should
not bypass it with local parsing or configuration lookup.

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
- Do runtime settings flow through `config_*` helpers?
- Does runtime time flow through `execution_clock`, `current_instant`, or
  `current_zoned_datetime` instead of a process-global clock?
- Does structured input parsing flow through `parse_dsl_document`?
- Does outbound HTTP flow through `http_*` helpers or `UnitOfWorkOp.Http*`
  using the current `ExecutionContext`?
- Does durable component data flow through a purpose-specific internal DSL or
  persistence port backed by the component datastore, rather than raw files,
  SQL, JDBC, or a vendor API?
- Does the component access only its own admitted datastore collections when a
  shared `DataStoreSpace` is configured?
- Is raw access explicit, named, and limited to repair/diagnostic/seed/import
  style purposes?

## Open Questions

- Should CNCF add guard specs for raw entity `DataStore` access outside
  framework-owned infrastructure?
- Which internal DSL helpers are missing for common handwritten component
  patterns?
- Which helper families should be moved from protected direct behavior to
  explicit `UnitOfWork` intents?
- Should `internal` and `direct` be renamed or split where their semantics are
  currently ambiguous?
