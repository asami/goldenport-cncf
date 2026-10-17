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
`ReadOnly` or `Manage`; write needs `Manage`. Unknown IDs, missing grants,
other owners, scope-label impostors and missing services fail before file
effects. Construction context A grants no authority to interpretation in B:
B selects its own root, grant and limit. Read permissions imply no Git,
Build, process, HTTP or remote capability.

The binding canonicalizes an admitted configured root alias, rechecks that
alias and the canonical directory identity at use, enforces normalized root
containment and refuses relative symlink segments, including final links.
Copied path values are reparsed at interpretation. Parents must already be
ordinary directories; leaves must be absent or ordinary regular files under
`NOFOLLOW_LINKS`. No mkdir or per-file ACL is introduced. Git-specific `.git`
selection exclusion remains with the application/Git contract.

## Complete text and current runtime policy

The actual current scope's `GlobalRuntimeContext.config.resourceReadLimits`
supplies the finite byte cap. Missing global limits fail as service unavailable;
there is no ambient global lookup or per-call maximum. The same existing
resource policy defaults to **1,048,576 bytes (1 MiB)** and admits **1 through
16,777,216 bytes (16 MiB)** inclusive through these aliases:

- `textus.resource.read.max-bytes`;
- `textus.runtime.resource.read.max-bytes`;
- `cncf.resource.read.max-bytes`;
- `cncf.runtime.resource.read.max-bytes`.

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
Each read/write is one External occurrence in the existing UoW planner and
recorder, including repeated equal intents; File effects are not local database
rollback. Closing/mechanical preparation remains default-deny.

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
