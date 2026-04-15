# CNCF Name Inventory for CozyTextus Transition

Date: Apr. 15, 2026

## Purpose

CozyTextus is the product name. CNCF is the implementation name.

`textus` is the primary product-facing namespace for ordinary users,
applications, examples, and public configuration. Existing `cncf` names remain
valid where they are already exposed to applications or scripts.

`cncf` is also allowed as an implementation-facing namespace. Hidden options,
diagnostic options, internal development options, and implementation-specific
escape hatches may intentionally use a `cncf` prefix when exposing them as
CozyTextus product configuration would be misleading.

This note inventories remaining `cncf` usages and separates them into:

- names already handled by the Textus-primary transition
- compatibility names that should remain accepted
- user-facing configuration keys that should be migrated next
- implementation-facing or hidden options that may legitimately keep `cncf`
- public metadata names that need a compatibility decision
- internal package/artifact names that should not be renamed incidentally
- historical documents that should not be mass-edited

## Naming Rule

Use the following distinction:

- **CozyTextus**: product name.
- **Textus / `textus`**: primary user-facing spelling for runtime options,
  application configuration, examples, and documentation aimed at application
  developers.
- **CNCF / `cncf`**: implementation name. It may appear in package names,
  implementation class names, artifact names, compatibility options, and hidden
  implementation/debug options.

For ordinary public configuration, new names should prefer `textus.*` and keep
`cncf.*` only as compatibility fallback when needed.

For hidden or implementation-specific options, `cncf.*` can be the canonical
name. Such options should be documented as implementation/debug controls rather
than as CozyTextus application configuration.

## Already Transitioned

The following areas now use Textus as primary and keep CNCF as fallback:

- Runtime config directories:
  - `.textus/config.*`
  - `.cncf/config.*` as compatibility fallback
- Runtime config CLI/system-property keys:
  - `textus.config.file`
  - `textus.config.files`
  - `cncf.config.file`
  - `cncf.config.files` as compatibility fallback
- HTTP/runtime settings:
  - `textus.server.port`
  - `textus.http.baseurl`
  - `cncf.server.port`
  - `cncf.http.baseurl` as compatibility fallback
- Discovery environment variables:
  - `TEXTUS_DISCOVER_CLASSES`
  - `TEXTUS_DISCOVER_PREFIX`
  - `CNCF_DISCOVER_CLASSES`
  - `CNCF_DISCOVER_PREFIX` as compatibility fallback
- Runtime trace/log default path:
  - `.textus/data.d/trace.log`

## Keep as Compatibility

These names should continue to be recognized because existing applications,
scripts, or documents may already depend on them. They are compatibility names
even when the corresponding primary product-facing spelling is `textus`.

- `.cncf/config.*`
- `--cncf.config.file`
- `--cncf.config.files`
- `cncf.config.file`
- `cncf.config.files`
- `cncf.server.port`
- `cncf.http.baseurl`
- `CNCF_DISCOVER_CLASSES`
- `CNCF_DISCOVER_PREFIX`
- Security HTTP headers:
  - `x-cncf-role`
  - `x-cncf-roles`
  - `x-cncf-scope`
  - `x-cncf-scopes`
  - `x-cncf-capability`
  - `x-cncf-capabilities`

The primary spelling for new documents and examples should be `textus` or
`x-textus-*` where a Textus form exists.

## Keep as CNCF Implementation Options

Some options are not product configuration. They expose implementation details,
diagnostic controls, test hooks, or development-time behavior. These may keep a
`cncf` prefix intentionally.

Current candidates:

- low-level runtime diagnostics
- path-resolution debug switches
- implementation-only discovery/test hooks
- temporary migration flags
- options that are meaningful only to the CNCF runtime implementation

Before migrating such a name to `textus.*`, confirm that the option is intended
to be part of CozyTextus product configuration. If it is not, keep it under
`cncf.*` and describe it as hidden, diagnostic, or implementation-facing.

## Next Code Migration Targets

These are still product-facing or application-facing names and should be moved
to Textus-primary lookup with CNCF fallback.

| Area | Current Name | File | Direction |
| --- | --- | --- | --- |
| Path alias config | `cncf.path.aliases` | `src/main/scala/org/goldenport/cncf/path/Alias.scala` | Implemented: `textus.path.aliases` primary, CNCF fallback. |
| DataStore SQLite path | `cncf.datastore.sqlite.path` | `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` | Implemented: `textus.datastore.sqlite.path` primary, CNCF fallback. |
| DataStore SQL normalize flag | `cncf.datastore.sql.normalize-column-names` | `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` | Implemented: `textus.datastore.sql.normalize-column-names` primary, CNCF fallback. |
| DataStore SQLite normalize flag | `cncf.datastore.sqlite.normalize-column-names` | `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` | Implemented: `textus.datastore.sqlite.normalize-column-names` primary, CNCF fallback. |
| CLI JSON property emission | `cncf.format` | `src/main/scala/org/goldenport/cncf/cli/CliOperation.scala` | Implemented: emits `textus.format`; CNCF remains accepted. |
| MCP JSON property emission | `cncf.format` | `src/main/scala/org/goldenport/cncf/mcp/McpJsonRpcAdapter.scala` | Implemented: emits `textus.format`; CNCF remains accepted. |
| Component JSON response marker | `cncf.format` | `src/main/scala/org/goldenport/cncf/component/Component.scala` | Implemented: accepts Textus primary and CNCF fallback. |
| Output formatter compatibility | `cncf.format`, `cncf.output.*` | `src/main/scala/org/goldenport/cncf/protocol/OperationResponseFormatter.scala` | Implemented: Textus primary with CNCF fallback. |
| Runtime path-resolution keys | `cncf.path-resolution*` | `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala` | Keep as CNCF implementation/debug options unless promoted to product configuration. |
| Collaborator repository config | `cncf.collaborator.repositories` | `src/main/scala/org/goldenport/cncf/backend/collaborator/CollaboratorRepositorySpace.scala` | Implemented: `textus.collaborator.repositories` primary, CNCF fallback. |
| Runtime source application name | `applicationname = "cncf"` | `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala` | Implemented: config snapshot loads CNCF compatibility sources first, then Textus primary sources. |
| Runtime parameter parser source name | `applicationname = "cncf"` | `src/main/scala/org/goldenport/cncf/cli/RuntimeParameterParser.scala` | Implemented: runtime parameter parsing loads CNCF compatibility sources first, then Textus primary sources. |

## Public Metadata Decision Needed

OpenAPI/introspection currently emits CNCF vendor extension names:

- `x-cncf-aggregate-collections`
- `x-cncf-view-collections`
- `x-cncf-operation-definitions`

These are public metadata names. The likely transition is:

- emit `x-textus-*` as primary
- keep `x-cncf-*` only if compatibility clients require it
- document whether dual emission or read-compatibility is expected

Do not rename these casually because OpenAPI consumers may treat extension names
as stable contract fields.

## CNCF Implementation Names

These names are implementation names. They do not need to be changed merely
because the product name is CozyTextus:

- Scala package: `org.goldenport.cncf.*`
- Runtime entry point classes:
  - `org.goldenport.cncf.CncfMain`
  - `org.goldenport.cncf.cli.CncfRuntime`
  - `org.goldenport.cncf.CncfVersion`
- SBT artifact and assembly names:
  - `goldenport-cncf`
  - `goldenport-cncf.jar`
- Docker image names:
  - `goldenport-cncf:*`
  - `ghcr.io/asami/goldenport-cncf`
- Collaborator API artifact:
  - `cncf-collaborator-api`
- Packaged resource path:
  - `META-INF/cncf/collaborator`

These may eventually gain Textus aliases or product packaging wrappers, but
renaming them requires dependency, package, and release compatibility planning.

## Documentation Cleanup Targets

Current design/notes documents still contain user-facing CNCF examples. These
should be updated after the corresponding code paths have Textus-primary
support:

- `docs/design/client-component-action-api.md`
  - `cncf.http.driver`
  - `cncf.http.baseurl`
- `docs/design/path-alias.md`
  - `cncf.path.aliases`
- `docs/design/canonical-alias-suffix-resolution.md`
  - command examples and `.cncf/config.yaml`
- `docs/design/global-protocol.md`
  - command examples
- `docs/notes/cncf-web-static-form-app-contract.md`
  - `cncf.*` should be described as compatibility where appropriate
- `docs/journal/2026/04/web-operational-management-note.md`
  - framework configuration should point to `textus.*` as primary
- `docs/notes/omponent-discovery-from-classdir.md`
  - discovery environment variable has been updated, but command naming should
    be revisited when a `textus` launcher is introduced

Historical records under `docs/notes/history/**`, old March journals, and
archival handoff documents should not be mass-edited. They may mention CNCF as
the project name at that time.

## Recommended Next Batches

1. Commit the discovery environment variable migration already staged in the
   working tree after review.
2. Implement Textus-primary lookup for path alias and DataStore keys. Done.
3. Change emitted runtime properties from `cncf.format` to `textus.format`
   while keeping CNCF parse compatibility. Done.
4. Add Textus-primary collaborator repository config. Done.
5. Decide the `x-cncf-*` OpenAPI extension transition policy.
6. Keep hidden implementation/debug options under `cncf.*` unless they are
   explicitly promoted to product configuration.
7. Treat package/artifact/entry-point names as CNCF implementation names unless
   a separate product packaging plan says otherwise.
