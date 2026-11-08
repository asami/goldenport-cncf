# File Workspace DSL

Status: normative producer contract; source qualification and consumer migration remain separate

## Logical API and effects

Behavior exposes these protected operations in its ordinary `ExecUowM` program:

```scala
protected final def file_read_text(
  target: FileTarget, path: WorkspaceFilePath
): ExecUowM[FileReadTextResult]

protected final def file_write_text(
  target: FileTarget, path: WorkspaceFilePath, text: String, mode: FileWriteMode
): ExecUowM[FileWriteTextResult]

protected final def file_prepare_directory(
  target: FileTarget, path: WorkspaceFilePath
): ExecUowM[FilePrepareDirectoryResult]

protected final def file_write_bytes(
  target: FileTarget, path: WorkspaceFilePath, bytes: Vector[Byte],
  representation: FileRepresentation
): ExecUowM[FileWriteBytesResult]

protected final def file_read_bytes(
  target: FileTarget, path: WorkspaceFilePath, representation: FileRepresentation
): ExecUowM[FileReadBytesResult]
```

`FileTarget(workspaceId: WorkspaceId)` selects a trusted logical workspace.
`WorkspaceFilePath.parseC(value)` constructs a relative filename. The public
results are `FileReadTextResult(target, path, text: Option[String])` and
`FileWriteTextResult(target, path, outcome: FileWriteOutcome)`. Read `None`
means an absent final leaf under an existing ordinary parent; an empty file
returns `Some("")`. Missing parents, permission, root, policy, encoding and I/O
failures return failed `Consequence` values. They never masquerade as absence
or successful partial text.

`FileWriteMode` is `CreateNew` or `UpdateExisting(expectedText: String)`.
`FileWriteOutcome` is `Created`, `Updated` or `Conflict(reason)`, where
`FileWriteConflict` is `AlreadyExists`, `Missing` or `Changed`. Results describe
`ExternalEffect(FileRead, target.workspaceId)` or `FileWrite`; effect metadata
grants no permission. Generic displays of modes, intents and results omit
expected, replacement and read text and physical locations. Read text remains
the explicit typed consumer payload.

## Literal path and shared authority

File paths preserve exact slash-separated POSIX relative names, including
spaces, newline, tab, literal backslash, colon and Unicode. Parsing rejects
null, empty, NUL, leading slash, Windows absolute drive-plus-separator and UNC
forms, empty segments, `.` and `..`. Platform `Path` validation also refuses
absolute paths and parent traversal. There is no trimming, shell unquoting,
case folding or conversion of backslash into a separator. The target is local
macOS/Linux NIO; this contract claims no Windows portability. The existing
strict `WorkspaceRelativePath` parser remains distinct and unchanged.

File, Git and SBT reuse the same V3 `WorkspaceId`, trusted `WorkspaceBinding`
and workspace grants. A workspace-only V3 plan can contain no process
definitions or process grants. File never needs or invokes a process driver;
the existing host lifecycle may still allocate its ordinary driver when a
plan is installed. No separate File registry or process fallback is used.

The interpreter's actual current UnitOfWork context must have an actual
`Component.Context` ancestor and its inherited workspace service. Read needs
`ReadOnly` or `Manage`; write and directory preparation need `Manage`. Unknown IDs, missing grants,
other owners, scope-label impostors and missing services fail before file
effects. Construction context A grants no authority to interpretation in B:
B selects its own root, grant and limit. Read permissions imply no Git,
Build, process, HTTP or remote capability.

The binding canonicalizes an admitted configured root alias, rechecks that
alias and the canonical directory identity at use, enforces normalized root
containment and refuses relative symlink segments, including final links.
Copied path values are reparsed at interpretation. Text and byte operations
require existing ordinary parents; file leaves must be absent or ordinary
regular files under `NOFOLLOW_LINKS`. Only explicit directory preparation
creates directories. No per-file ACL is introduced. Git-specific `.git`
selection exclusion remains with the application/Git contract.

## Managed directory preparation

`FilePrepareDirectoryResult(target, path, outcome)` describes a `FileWrite`
effect. `FilePrepareDirectoryOutcome` is `Created`, `AlreadyPresent` or
`Conflict`. The operation creates missing literal nested segments inside the
unchanged admitted root; it never creates or replaces that root. `Created`
means the selected final directory was actually created by this invocation.
An already present ordinary directory, including a cooperating creation winner,
returns `AlreadyPresent`; an ordinary file at any selected segment returns
`Conflict`. Symlinks, nonregular occupants, changed observed directory identity,
missing/retargeted roots and other failures return failed `Consequence` values.

Each prefix is reparsed and resolved through the current binding, inspected
without following links, and its observed directory identity is checked again
while progressing through later segments. The current root is checked again.
Preparation synchronizes on the same binding as writes. These are cooperative
checks with the same race limitations as other File operations, not a stronger
OS containment guarantee. If a later segment fails, already created ancestors
remain actual partial effects. There is no rollback, cleanup of those directories,
automatic retry or inferred success.

## Binary and gzip representations

`FileRepresentation` is `Identity` or `Gzip`. Public results are
`FileReadBytesResult(target, path, representation, bytes: Option[Vector[Byte]])`
and `FileWriteBytesResult(target, path, representation, outcome: FileWriteOutcome)`.
Read results describe `FileRead`; write results describe `FileWrite`. Their
generic displays and those of all corresponding UoW intents omit byte payloads
and physical paths. Bytes remain available only as the explicit typed payload.

Representation is always explicit. Identity preserves arbitrary bytes exactly;
Gzip is standard JVM gzip compression/decompression owned entirely by CNCF.
Filenames imply no representation. The caller selects the exact destination
before construction, including any `.gz` suffix; CNCF never appends, renames,
relocates, removes or otherwise modifies an original source. The adapter writes
only supplied bytes. Applications use the protected DSL rather than the private
adapter, application NIO or a shell compressor.

Byte writes support only CreateNew semantics: nonnull input is checked against
the current `GlobalRuntimeContext.config.resourceWriteLimits.maximumBytes`
before staging or compression.
The same cap independently bounds stored output during streaming, including
gzip headers and trailers. The staged file is forced and installed with the
same-parent non-overwriting hardlink procedure below. An occupied ordinary exact
name returns `Conflict(AlreadyExists)` and retains its bytes; linked and nonregular
occupants fail. No byte update mode or overwrite/move fallback exists. Gzip and
staged handles close on terminal paths, and staging is removed after success,
conflict or failure.

Byte reads first acquire the complete bounded stored representation through
`ResourceReadAcquisition`, using `READ` and `NOFOLLOW_LINKS`. Gzip then bounds
expanded output during decompression under that same independent cap. Input,
stored compressed bytes and expanded bytes must each fit; equality succeeds,
overflow fails without partial success or truncation. Malformed gzip headers,
trailers, CRC or compressed content fail privately. Missing final leaves return
`None` only with a valid existing parent; empty Identity or expanded Gzip data
returns `Some(Vector.empty)`. Invalid representation or null input fails. No new
per-call cap or alternate limit source is introduced.

## Complete text and current runtime policy

The actual current scope's `GlobalRuntimeContext.config.resourceReadLimits`
supplies the finite read cap; `resourceWriteLimits` supplies the independent
write cap for text input and binary input/output. Text CAS expected values and
existing-file observations retain the read cap, including the final observation
before atomic replacement. Missing global limits fail as service unavailable;
there is no ambient global lookup or per-call maximum.

Both file policies follow **individual override > `cncf.io.default-maximum-bytes`
> built-in 1,048,576 bytes (1 MiB)**. The common property accepts a positive
integral Long, with no 1 MiB ceiling. Its numeric or strict digit string value
must be valid even when individual overrides are supplied. Actual file policies
must remain representable in **1 through 268,435,456 bytes (256 MiB)**; when the
common value exceeds that range, explicit file overrides can select representable
values. The existing read override is admitted through these aliases:

- `textus.resource.read.max-bytes`;
- `textus.runtime.resource.read.max-bytes`;
- `cncf.resource.read.max-bytes`;
- `cncf.runtime.resource.read.max-bytes`.

The dedicated write override is exactly `cncf.resource.write.max-bytes`, with
the same file-specific range and no forwarding alias. A read override does not
select the write cap. Omitted process stdin/stdout/stderr limits inherit the
same common default at runtime activation, while explicit stream values remain
selected. Aggregate WorkArea and snapshot-total byte budgets are distinct and
do not inherit this per-file/per-stream default.

A larger selected limit permits more input; it is not a peak-memory bound.
Immutable byte, text and JSON values may require more memory than the input size.

Read opens with `READ` and `NOFOLLOW_LINKS`, acquires at most cap plus one
bytes through the shared owned-stream helper, closes the stream on success,
overflow and exception, then strictly decodes UTF-8. Equality succeeds;
overflow or malformed UTF-8 fails without truncation or replacement text.
Reads have no writable lock, lockfile, temporary file or process.

Both replacement and expected text must be nonnull, valid UTF-8 strings within
that same current cap. The existing finite UTF-8 counter bounds encoded
allocation; strict encoding also refuses unpaired surrogates. Empty replacement
is valid. Unicode and final newline survive exactly. This intentionally bounds
writes and preconditions so subsequent ordinary readback can consume them; it
adds no write-policy registry. Generic failures omit private payloads, host
paths and raw exception messages.

## Installation and conflicts

CreateNew installs only an absent final leaf. An occupied ordinary file yields
`Conflict(AlreadyExists)` even when text is equal; linked and nonregular
occupants are policy failures. Complete bytes are staged in a same-parent
`.cncf-file-*.tmp`, written and forced, then installed with `Files.createLink`
for atomic non-overwrite. An ordinary race winner produces AlreadyExists.
Unsupported atomic hardlink installation fails with no overwrite/move fallback.

UpdateExisting reads complete bounded strict current text. Absence produces
`Conflict(Missing)` and unequal text produces `Conflict(Changed)` without
mutation. Exact equality permits staging, preserving the original POSIX
permissions where supported, then resolving and reading the precondition again
immediately before `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`. `Updated` means
that atomic move actually completed; there is no upsert, non-atomic fallback,
retry or replay. Staging resources close and temporary paths are cleaned on
success, conflict and error.

Writes synchronize on the actual shared binding. This excludes cooperating
writers sharing that runtime object; reads remain unlocked. It is no arbitrary
editor or cross-process compare-and-swap, adversarial race-proof containment,
multi-file transaction, rollback, or directory-fsync durability guarantee. The
owning workflow retains saved intent, uncertain-result handling and no replay.

## Ordinary program

Inside a Behavior program, with logical values parsed from application input:

```scala
for {
  before <- file_read_text(target, path)
  written <- file_write_text(target, path, replacement,
    before.text.fold[FileWriteMode](FileWriteMode.CreateNew)(FileWriteMode.UpdateExisting.apply))
  after <- file_read_text(target, path)
} yield (written, after)
```

The application interprets conflicts and owns business comparison/edit intent.
Each read/write/directory preparation is one External occurrence in the existing UoW planner and
recorder, including repeated equal intents; File effects are not local database
rollback. Closing/mechanical preparation remains default-deny.

For selected binary archive bytes and a preselected final name, the same
ordinary program can explicitly prepare its parent and retain both actual
outcomes:

```scala
for {
  prepared <- file_prepare_directory(target, archiveDirectory)
  written <- file_write_bytes(target, exactGzipPath, selectedBytes, FileRepresentation.Gzip)
  read <- file_read_bytes(target, exactGzipPath, FileRepresentation.Gzip)
} yield (prepared, written, read)
```

The application must interpret directory and write conflicts before deciding
its business result; composition alone does not turn a conflict into success.
Every repeated equal intent remains a separate External occurrence. Failure,
conflict and uncertain physical results authorize no replay or automatic retry.

This is partial CNCF producer integration for sm-workflow Phase 6 / P6-SBX03,
paired with the [resource contract](resource-reference-dsl.md),
[Phase 6 handoff](../../../sm-workflow/docs/journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md)
and [security profile](../../../sm-workflow/docs/phase/phase-6-security-review-profile.md).
It claims no CNCF110 completion, deployment or primary migration. The five
File purposes, business compare/edit, durable intent and no replay remain
sm-workflow responsibilities. Retirement of generic File Request routes and
all eight remaining original main/launcher routes stays mandatory.

Executable contracts:
[`WorkspaceFileExecutionSpec`](../../src/test/scala/org/goldenport/cncf/workspace/WorkspaceFileExecutionSpec.scala),
[`FileExecutionDslSpec`](../../src/test/scala/org/goldenport/cncf/action/FileExecutionDslSpec.scala),
[`UnitOfWorkProgramPlanningSpec`](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkProgramPlanningSpec.scala).
Existing shared binding and acquisition lifetime contracts remain in
`WorkspaceExecutionSpec` and `ResourceReadLimitsSpec`.

## Selected workspace byte snapshots

The additional logical operation is:

```scala
protected final def file_read_snapshot(
  target: FileTarget,
  selection: ResourceTreeSelection,
  limits: ResourceTreeQueryLimits = FileSnapshotLimits.default
): ExecUowM[FileReadSnapshotResult]
```

The corresponding `UnitOfWorkOp.FileReadSnapshot(target, selection, limits)`
is interpreted by the current owning UoW, classified External and observed as
`uow:file:read-snapshot`. It requires the existing target workspace ReadOnly
grant and current runtime resource-read policy. Building or recording the
program does not acquire a workspace, grant authority or read a file. There is
no process execution, new root configuration or independent snapshot store.

`ResourceTreeSelection(includes, excludes = Vector.empty, suffixes = Vector.empty)`
is a reusable rule, rather than an inventory of files observed at a prior time.
Includes and excludes are literal relative file-or-subtree prefixes matched at
path-segment boundaries; a sole `"."` include selects the root. An include such
as `"src"` recursively selects its current descendants. Excludes take precedence
and prune directories before descent, attributes, bytes and selected-entry
limits. Optional suffixes restrict files only, without pruning ancestor
directories. Empty suffixes select all files under included prefixes. For example:

```scala
ResourceTreeSelection(
  Vector("src", "project", "build.sbt"),
  Vector("src/main/scala-generated", "project/target"),
  Vector(".scala", ".sbt", ".cml")
)
```

Rules contain at most 128 unique members per vector. Prefixes use the existing
strict resource-tree relative-path grammar (no traversal, absolute path,
backslash, controls or surrounding whitespace), at most 4096 characters per
prefix. Suffixes are nonempty bare suffixes of at most 128 characters without
path separators or controls. Interpretation validates all members again,
including values created with case-class `copy`. A discovered relevant name
unsupported by that grammar fails acquisition; it is never silently omitted.
This stricter tree grammar does not change ordinary `WorkspaceFilePath`.

Requested bounds must tighten `FileSnapshotLimits.maximum`; no request
widens admission. `FileSnapshotLimits.default` uses the same finite bounds:
64 path segments, 100000 directories, 65536 selected files, 16 MiB per file and
128 MiB aggregate. The existing generic `ResourceTreeQueryLimits` defaults are
unchanged. The effective `maxEntryBytes` is additionally narrowed by the
current runtime `ResourceReadLimits.maximumBytes`. Depth counts relative path
segments. The directory cap includes the root; the entry cap counts selected
regular files. Discovery of nonexcluded prefix-relevant entries, including
suffix-rejected files, is bounded by `maxVisitedDirectories + maxEntries`.
Directory iteration is streamed; selected inventories and acquired bytes are
bounded. Per-file and aggregate byte caps apply before acquisition and again
to actual returned bytes. A zero byte cap permits empty selected files.

A successful `FileReadSnapshotResult` associates the exact admitted logical
`target`, validated `selection`, effective `limits` and sorted immutable
`entries: Vector[ResourceTreeEntry]`. Each entry holds an exact relative path
and `Vector[Byte]`, with no encoding or line-ending conversion; mediaType is
absent. `totalByteSize` sums those bytes. Its effect is FileRead; generic displays
omit selected paths, bytes and physical root locations. A selected absent path
is simply absent from a newly discovered complete inventory; additions,
deletions and renames are reflected in the next acquisition under the same rule.

The producer rechecks the same trusted root binding and compares complete
before/after inventories of relevant directories and files, including native
file identity, size, modification and creation metadata. Missing/unreadable
files, unsupported selected file kinds, relevant symbolic links, failed
listing, bounds and detected membership/identity/metadata changes return a
failed `Consequence`, never a truncated or partially successful result. Excluded
symlinks are pruned; relevant symlinks are refused rather than followed.

This is bounded observational coherence, not a transactional OS snapshot or a
promise to detect an adversarial mutation that restores every observable native
identity and timestamp between checks. No new lock or metric-only hash is used.
Consumers requiring stronger atomic snapshot isolation must use an existing
stronger source snapshot owner. Workflow metrics can retain this immutable result
once at each intended endpoint, convert acquisition failure to unavailable, and
reuse their retained payload without repeating acquisition. This producer does
not count lines, acquire usage or prescribe workflow closure policy.

## Purpose-specific File I/O budgets

File reads may explicitly select `FileReadPolicy(ResourceReadLimits(...))` for text or bytes, including both stored and expanded Gzip bytes. File writes may independently select `FileWritePolicy(ResourceReadLimits(...))` for text or bytes. The finite limits use the same validated bounds as Resource limits. Selection is immutable and applies only to that operation; it does not alter generic runtime defaults or confer workspace authority. Unselected operations retain their existing limits. A selected write policy does not change the read limit used for `UpdateExisting` comparison. All existing path, permission, encoding, atomicity and outcome rules apply.
