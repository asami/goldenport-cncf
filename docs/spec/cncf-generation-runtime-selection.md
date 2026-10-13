# CNCF generation runtime selection

Status: selector source and executable specifications authored; current eight-suite
generation/native/persistence validation passed 85/85 tests, with SBT/wrapper exit 0
and serial lock released. Complete eighteen-path independent protected review
and implementation acceptance remain pending. The historical persistence-stage prerequisite remains separately accepted:
61/61 focused tests across five suites, SBT/wrapper exit 0, serial lock released,
and the complete eleven-path independent protected review
`tkw-cncf-persistence-stage-step-review-20261005@1` PASS. That evidence predates
this selector and the native scalar binding. Workbench behavior, provider
roundtrip, restart/recovery, project acceptance, and Phase acceptance are unfinished.

Owner: CNCF Phase 100; Workbench Phase 1 / Step 2 prerequisite
PREREQ-CNCF-DEDICATED-GENERATION.

Executable specification:
[Phase51Cv03BuildIntegrationSpec](../../src/test/scala/org/goldenport/cncf/phase51/build/Phase51Cv03BuildIntegrationSpec.scala).
Related contracts: [generation compatibility](generation-compatibility-contract.md),
[native scalar mapping](entity-store-native-scalar-mapping.md), and
[persistence stages](entity-store-persistence-stages.md).

## Project-owned exact versions (R1)

`build.sbt` owns the exact Cozy generator version through `cozyGeneratorVersion`
and the exact CNCF target version through `version.value`. Their current
declarations are `0.3.3-SNAPSHOT` and `0.5.3-SNAPSHOT`, respectively. Selecting
a development checkout changes the execution location while preserving these
declared versions, the runtime descriptor, and the Information CML source identity.
The helper does not read a selected checkout's version declaration or infer a
version from its directory name.

When no selector is supplied, `cncfCozyRuntimeDevDirectory` is `None` and both
operation plans retain their existing `cozy --runtime <exact-generator-version>`
prefix and complete argument vectors. The normal launcher resolves that exact
runtime according to its existing behavior.

## Optional explicit development directory (R2)

The CNCF-local SBT setting `cncfCozyRuntimeDevDirectory: Option[File]` reads only
the JVM property `cncf.cozy.runtimeDevDir`. It performs no sibling search,
automatic directory detection, environment fallback, or global configuration
mutation. `CncfGenerationInputs` and `resolve` accept the additive final
`cozyRuntimeDevDirectory: Option[File] = None` parameter, preserving existing
callers.

An explicit selection must be nonnull, absolute, an existing directory, and
contain a regular `build.sbt`. A null Option, `Some(null)`, relative path,
missing path, regular file used as the directory, or directory without a regular
`build.sbt` fails resolution with this deterministic diagnostic:

```text
The selected Cozy runtime development directory must be an absolute directory containing build.sbt.
```

An invalid supplied selector never becomes `None`. A valid absolute selector is
normalized once at resolution using `toPath.normalize().toFile`; spaces remain
part of its single directory argument. This is current filesystem input
validation, with no checkout hash, copy, or immutability guarantee.

## Shared generation and validation prefix (R3)

Both `command` and `validationCommand` use the same selector validation and
prefix helper. Explicit inputs produce:

```text
cozy --runtime <exact-generator-version> --runtime-dev-dir <normalized-absolute-directory> <operation-arguments...>
```

The prefix is an argv vector, so a directory containing spaces is one element.
Generation retains `modeler-scala-value` and its exact target, descriptor,
descriptor hash, source identity, and output arguments. Validation retains
`generation-provenance-validate` and its exact target, descriptor hash,
generator version, source identity, source hash, and output arguments. The same
resolved selection feeds ordinary, cold, and repeated generation.

Existing generation notices, failure reporting, source/product hashes,
artifact provenance, and output determinism behavior remain unchanged. This
selector adds no provenance fields or hashes.

## Revalidation before command planning (R4)

Each operation rechecks the supplied selector while planning its command.
Removal of the selected `build.sbt` after successful resolution makes both
plans fail with the R2 diagnostic. Neither plan falls back to another Cozy
checkout or an ordinary configured runtime.

Existing source and output diagnostics retain their order and meaning; a
selector diagnostic is appended after them. Generation also retains its
existing source-change check against the resolved source hash. Validation
retains its existing source-presence check.

## Normal development runtime preparation (R5)

Prepare the selected checkout with its normal `cozyExportRuntimeClasspath` SBT
task before CNCF generation. The task compiles through `Runtime / fullClasspath`
and writes `target/cozy.d/runtime-classpath.txt` and
`target/cozy.d/runtime-version.txt`. R2 validation does not establish that these
outputs are ready.

Portable shell example, with absolute directories supplied by the user:

```sh
COZY_RUNTIME_DIRECTORY='/absolute/path/to/cozy checkout'
CNCF_PROJECT_DIRECTORY='/absolute/path/to/cncf checkout'

(cd "$COZY_RUNTIME_DIRECTORY" && sbt --batch cozyExportRuntimeClasspath)
(cd "$CNCF_PROJECT_DIRECTORY" && sbt --batch \
  "-Dcncf.cozy.runtimeDevDir=$COZY_RUNTIME_DIRECTORY" generateInformationCmlModel)
(cd "$CNCF_PROJECT_DIRECTORY" && sbt --batch \
  "-Dcncf.cozy.runtimeDevDir=$COZY_RUNTIME_DIRECTORY" verifyInformationCmlGenerationDeterminism)
```

Run these invocations sequentially. Agents use the registered serialized SBT
executor required by the repository directives. The examples require no local
publication or machine-specific checkout layout. Ordinary no-selector generation
remains:

```sh
(cd "$CNCF_PROJECT_DIRECTORY" && sbt --batch generateInformationCmlModel)
```

The existing Cozy launcher checks the exact requested `--runtime` version
against the explicitly selected checkout. A mismatch reports
`Cozy development checkout version mismatch` with requested/actual version and
checkout details. Select a checkout matching the project-owned exact generator
version; directory placement does not choose the version.

The launcher also reports `development classpath missing` or
`development classpath stale` and directs the caller to run
`cozyExportRuntimeClasspath` in the selected checkout. Refresh that normal output
after changes to build inputs or runtime sources, then retry CNCF generation.
These launcher diagnostics remain launcher-owned and do not trigger selector
fallback.

## Executable examples

| Example | Scenario | Rules | Observable contract |
| --- | --- | --- | --- |
| E1 | GENDEV-01 | R1–R3 | Spaces and lexical dot segments normalize; both argv plans select one directory and preserve versions and operation arguments; None preserves ordinary plans. |
| E2 | GENDEV-02 | R1–R2 | Missing, relative, file, incomplete, and null explicit selections fail deterministically; ordinary None resolves. |
| E3 | GENDEV-03 | R2–R4 | Losing build.sbt after resolution fails both plans; existing source/output diagnostics precede the selector diagnostic. |

The existing five build integration examples and public normalization property
remain part of the compatibility specification. These examples passed in the
current 85/85 eight-suite validation; independent review remains pending. They
establish no SQL
roundtrip, restart, Workbench, project, or Phase acceptance.
