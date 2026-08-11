# Component-Local Datastore Layout Specification

Status: normative

## Authority

This specification defines component-local datastore identity, configuration,
path selection, and operator migration responsibilities. Design rationale is
non-normative and is recorded in
`docs/design/component-local-datastore-layout.md`.

## Rules

### R1 Canonical local datastore identity

A local component datastore requires an admitted, namespace-qualified
`ComponentId`. Its default path is:

```text
~/.cncf/components/<namespace>/<normalized-local-id>/datastores/<normalized-store-name>.db
```

The namespace is preserved exactly as the canonical identity namespace. The
local ID and logical store name use deterministic normalization. A canonical
identity has no version, catalog source, artifact name, display name, route, or
compatibility alias input.

### R2 Namespace-qualified component selector

Every component-specific datastore policy, dedicated datastore setting, local
directory override, and complete local-path override uses the canonical
selector:

```text
<namespace>.<normalized-local-id>
```

For `org.simplemodeling.textus.ArtScene`, the selector is
`org.simplemodeling.textus.art-scene`. Local-ID-only component keys are not a
fallback for a canonical request. Global datastore policy keys remain an
intentional non-component fallback.

### R3 Local path override precedence

For a canonical local request, CNCF resolves in this order:

1. `textus.local-data.<selector>.<store>.path` and its `cncf.*` compatibility
   alias;
2. `textus.local-data.<selector>.dir` and its `cncf.*` compatibility alias;
3. `textus.local-data.root` and its `cncf.*` compatibility alias; then
4. the default root from R1.

A directory override owns the directory above `datastores`; a complete-path
override intentionally owns the entire path.

### R4 Dedicated datastore configuration

Component-specific dedicated datastore settings use
`textus.component.<selector>.datastores.<store>.*`, with the corresponding
`cncf.*` compatibility spelling. The application-store compatibility alias is
`textus.component.<selector>.datastore.*`. A setting for one canonical selector
must not affect another selector with the same local ID.

### R5 No local-ID legacy fallback

CNCF must not probe, select, create, or migrate a local datastore from a
local-ID-only component key, flattened qualified name, alias, artifact name, or
route. A canonical request ignores such keys even if they name an existing
database.

### R6 Noncanonical local rejection

The public string `Request` compatibility shape remains available for external,
basic, and in-memory datastore selection. If its selected policy requires a
local datastore and its component name cannot be safely parsed as a
namespace-qualified `ComponentId`, selection fails with an explicit diagnostic;
it must not open a legacy local database.

### R7 Source exclusion

Component release version, DEV/LOCAL/PUBLIC catalog source, artifact name,
display name, and Web alias are excluded from local datastore identity and
default-path selection.

### R8 Operator-owned migration

CNCF does not automatically copy, merge, delete, or otherwise migrate a legacy
local database. An operator owns migration and retains rollback material.

## Examples

### E1 Same local ID in two namespaces

`org.example.alpha.ArtScene` and `org.example.beta.ArtScene` use distinct
canonical selectors, so their policy, directory override, complete-path
override, and dedicated datastore configuration are isolated. Executable
coverage: `ActionCallComponentDataStoreIdentitySpec` E1.

### E2 Canonical request and compatibility request

A canonical ArtScene request ignores `textus.component.art-scene.*` and
`textus.local-data.art-scene.*` keys. A local-only `Request("ArtScene")`
fails closed. Executable coverage: `ActionCallComponentDataStoreIdentitySpec`
E2.

### E3 Direct ActionCall path

A direct ActionCall carrying canonical Component metadata selects its canonical
local datastore under local-only policy. Executable coverage:
`ActionCallComponentDataStoreIdentitySpec` E3.

### E4 Managed ActionCall path

A managed FunctionalActionCall carrying canonical Component metadata selects
the same canonical local datastore under local-only policy. Executable coverage:
`ActionCallComponentDataStoreIdentitySpec` E4.

## Safe migration checklist

1. Identify every legacy source layout and the canonical destination selected
   by R1–R3.
2. Stop all writers and confirm no process retains the source database.
3. Make a SQLite-safe backup or copy, accounting for `-wal` and `-shm` files
   when WAL mode is active; use SQLite's backup mechanism when an online copy
   is unavoidable.
4. Verify source and destination integrity, expected row counts, content hash,
   and ownership/permissions before startup.
5. Start the new runtime only after the destination has passed verification.
6. Validate the application against the migrated data and retain the original
   source and backup for rollback.

This checklist describes an operator procedure, not automatic runtime
migration.
