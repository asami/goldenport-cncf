# Core development source selection

Status: normative static contract

## Explicit selection and path admission

`TEXTUS_GOLDENPORT_CORE_CNCF_SOURCE_ROOT` is the sole selector for a core source
dependency in CNCF. Its absence selects the existing artifact graph. A present
value must be a nonblank absolute path; an invalid explicit value fails without
artifact fallback, repository search, aliases, or implicit source selection.

`CncfCoreDevelopmentBuild.resolveSourceDirectory` returns `Right(None)` for
absence and the canonical selected directory for a valid explicit selection.
It rejects canonicalization failures, the consumer itself, a source containing
the consumer, and ordinary nested sources. Canonically disjoint roots are
admitted. The only contained placement is a direct child of the consumer's
`.codex-workflow/checkouts/` with an ASCII leaf matching
`[A-Za-z0-9_][A-Za-z0-9_.-]*`. Both normalized absolute lexical and canonical
paths must have that same lexical checkout parent. A symlinked namespace or
leaf cannot escape this rule.

The selected source must contain `build.sbt` and
`src/main/scala/org/goldenport/http/HttpResponse.scala` as files. The canonical
target of each marker must remain within the selected source. Diagnostics
identify the exact selector and the rejected path or coordinate requirement.

## Loaded coordinates and build settings

`cncfCoreDevelopmentDirectory` is the `Option[File]` setting exposing the
canonical selection. `validateCncfCoreDevelopment` is a `Unit` setting that
validates the actual loaded source project's `organization`, `moduleName`,
`version`, and `scalaVersion` during settings initialization. They must be
exactly `org.goldenport`, `goldenport-core`, `0.4.3-SNAPSHOT`, and `3.3.8`.
The pure helper's `validateCoordinates` compares those loaded values, rather
than interpreting source text. Each mismatched dimension is reported.

## Class ownership and metadata

Selected source mode uses an ordinary SBT `RootProject` dependency with
`compile->compile;runtime->runtime`. It removes only the direct core binary
dependency and excludes transitive `org.goldenport:goldenport-core_3`. The
selected source target owns the one `HttpStatus` class resource and its actual
CodeSource. No competing core artifact class resource, product source copy,
unmanaged product source injection, or implementation JAR copy is admitted.
Only the pure build helper is also included in `Test / unmanagedSources` so
its build contract can be specified in Scala 3.

The logical core ModuleID remains input to all three existing
`textusBaseProvidedModules` call sites: runtime descriptor generation, catalog
generation, and catalog export. Their `baseProvided` metadata therefore retains
`org.goldenport:goldenport-core_3` in source mode. Source selection does not
change the helper's semantics or the checked-in catalog.

With the selector absent, CNCF keeps its direct `0.4.3-SNAPSHOT` core artifact,
existing exclusions, versions, and default runtime graph. Source-only class
origin and 204 expectations apply only to an explicit source selection. The
default executable route remains compilable with the existing core artifact.

## Executable specifications

[CncfCoreDevelopmentRuntimeSpec](../../src/test/scala/org/goldenport/cncf/phase51/build/CncfCoreDevelopmentRuntimeSpec.scala)
defines CS1 absent and canonical source selection, CS2 path and coordinate
rejections plus at least fifty successful generated helper cases, and CS3
actual selected-source class origin, unique resource, public 204 mapping, and
generated runtime descriptor metadata. Fixtures belong to `target/`, own their
resources, and are removed in `finally`; the specification starts no nested
SBT build or wire server.
