# Phase 55 GCF-09I: Startup Import Configuration

Date: 2026-08-04

## Decision

Migrate runtime startup-import source selection from direct
`ResolvedConfiguration` reads to the final admitted Subsystem binding
collection. The canonical parameter identities are:

- `textus.import.data.file`
- `textus.import.entity.file`

Both are optional `String`, SubsystemInstance-only parameters. The established
`textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` spellings remain decode-only
aliases. Canonical-plus-alias collisions, non-string values, and non-Subsystem
targets fail structurally during catalog admission.

## Runtime boundary

`Subsystem` projects the admitted values into
`StartupImportConfiguration(dataSource, entitySource)`. It trims source text
and turns blank text into absent values. `StartupImport` receives that
value-only configuration and no longer accesses raw configuration, bindings,
candidates, aliases, provenance, or trace data. Missing runtime binding
admission fails structurally; an admitted absence remains authoritative and
leaves the existing `data.d` / `entity.d` fallback intact.

For entity seed import, `StartupImport` receives only an entity-collection
resolver capability. It never receives the enclosing `Subsystem`, preventing
the Subsystem's raw configuration or binding APIs from crossing the boundary.

## Non-goals

This slice retains URL, file, directory, bootstrap-relative path, and
deterministic traversal behavior. It does not alter SystemNode/Subsystem pool
ownership, Phase 54 deployment topology, generic SPI, launchers, or
`application-mode`. `application-mode` remains presentation-only vocabulary;
its removal is a later compatibility and CML slice.

## Validation

Initial test compilation exposed only a new-spec package-visibility issue;
the corrected serialized `Test/compile` passed at
`53604-20260803T155650Z`. Independent review required a narrower entity
resolver boundary, removal of obsolete alias constants, exact catalog codec
and alias-vector assertions, missing-admission coverage, and a canonical
split-file runtime acceptance example. Review-fix `Test/compile` passed at
`63404-20260803T161527Z`; focused validation passed 21/21 at
`64171-20260803T161658Z`. The existing import integration suites remain
unchanged and continue to cover their established legacy runtime entrypoints.
Focused re-review then closed spec identity, resource cleanup, same-file
naming, and header-date findings; its `Test/compile` passed at
`68121-20260803T162530Z` and focused validation passed 21/21 at
`68800-20260803T162644Z`.
