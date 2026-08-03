# Phase 55 GCF-08L — Raw YAML Duplicate-Member Preservation

Date: 2026-08-03

GCF-08L closes the file-boundary gap left by map-based YAML decoding. The
generic source layer now transports a compatibility `Configuration` and an
optional raw `ConfigurationDocument.Object` together in one loaded source
snapshot. Standard YAML file loading keeps mapping fields in physical order,
including repeated field names; the legacy map remains available with its
existing last-wins behavior.

The default `loadSnapshot` implementation calls an existing custom source's
`load` once. Raw document data is therefore available only when a standard file
loader supplies it. No resource, environment, argument, launcher, diagnostic,
or custom source becomes a new raw-document authority.

`RuntimeFileConfigLoader` retains that document while preserving its flattened
compatibility configuration. `CncfRuntimeConfigurationProjection` consumes it
only for retained file snapshots: consolidated documents preserve their raw
hierarchy and canonical split documents flatten physical fields without a
`groupBy` collapse. The existing candidate constructor reports a duplicate
canonical `(parameter,target)` structurally, and CNCF never rereads the file.

Generic `Test/compile` plus `FileConfigLoaderSpec` and
`ConfigurationResolutionSnapshotSpec` passed 7/7 through serialized invocation
`20002-20260803T123545Z`. Development-local `publishLocal` completed through
`21867-20260803T123833Z`; it wrote only the local Ivy artifact and did not
publish externally. CNCF `Test/compile` plus
`CncfRuntimeConfigurationProjectionSpec`, `CncfRuntimeSnapshotBootstrapSpec`,
and `Phase55RuntimeConfigurationBoundarySpec` passed 20/20 through serialized
invocation `22594-20260803T123955Z`.

Independent review found the initial public-trait evolution lacked an additive
default; raw scalar construction could differ from accepted SnakeYAML values;
the duplicate test admitted an invalid user-mode value; one test retained
GCF-07C metadata; and same-file naming/status debt remained. The repair adds
the trait default, reuses SnakeYAML conversion for YAML-native scalar tags,
uses two valid values and asserts a duplicate failure, corrects metadata and
status, and completes the local naming fixes. Review-fix generic validation
passed 7/7 at `40442-20260803T130950Z`; local-only `publishLocal` passed at
`41294-20260803T131108Z`; CNCF validation passed 20/20 at
`42104-20260803T131218Z`.

Focused re-review then found that source-node tags must be retained as well as
scalar text: an explicit `!!null foo` must remain null rather than becoming a
raw String. The final repair reconstructs tagged scalar input before using the
same SnakeYAML conversion and adds that regression before the final focused
validation/re-review. Generic validation passed 7/7 at
`49886-20260803T132712Z`; local-only `publishLocal` passed at
`50783-20260803T132851Z`; final CNCF validation passed 20/20 at
`51341-20260803T132950Z`.

A subsequent focused re-review found that reconstructed YAML must also escape
all control characters and preserve timestamp as well as null/bool/number tag
semantics. The final scalar path now retains every non-string composed tag and
emits YAML-safe escapes; executable coverage includes `!!null "\\0"` and an
implicit timestamp before the final validation and focused re-review.
Generic validation passed 7/7 at `58753-20260803T134152Z`; local-only
`publishLocal` passed at `59698-20260803T134313Z`; final CNCF validation passed
20/20 at `60340-20260803T134406Z`.

The final focused re-review found binary scalar conversion was nondeterministic
because a constructed `byte[]` otherwise used its object identity string. The
generic scalar conversion now renders binary data as canonical Base64 for both
compatibility and raw paths; the regression compares raw bool, number, binary,
timestamp, and null values with the compatibility map. The file-loader specs
also create and remove their temporary directories below repository `target/`.
Generic validation passed 7/7 at `70744-20260803T135927Z`; local-only
`publishLocal` passed at `71967-20260803T140109Z`; final CNCF validation passed
20/20 at `72607-20260803T140206Z`.

The final focused re-review is clean. It verified deterministic binary scalar
conversion, raw/compatibility equality coverage, updated Scala version headers,
and cleanup of test directories under `target/`.
