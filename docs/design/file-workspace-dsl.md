# File Workspace DSL design

Status: normative design for the bounded current-UoW File producer

## Responsibility and interpretation

```text
Behavior.file_read_text(FileTarget, WorkspaceFilePath)
Behavior.file_write_text(FileTarget, WorkspaceFilePath, String, FileWriteMode)
  -> UnitOfWorkOp.FileReadText / FileWriteText (one typed intent)
  -> ordinary UnitOfWorkInterpreter using uow.executionContext
  -> uow:file:read-text / uow:file:write-text calltree
  -> WorkspaceFileExecution
  -> current Component.Context + shared WorkspaceExecutionAdmission
  -> ReadOnly / Manage binding + current GlobalRuntimeContext resourceReadLimits
  -> direct bounded NIO acquisition / atomic per-file installation
  -> FileReadTextResult / FileWriteTextResult
```

The protected methods only lift immutable intents into the existing Free/UoW
algebra. They capture no context authority, config, root or content and perform
no admission or filesystem access during construction. There is no second
algebra, File service registry, Process Request, executable, shell or JVM.
The ordinary interpreter/classifier contains the two cases; closing/mechanical
preparation continues to deny unselected external operations.

`FileTarget(workspaceId)` is logical. `WorkspaceFilePath` is privately
constructed, preserves literal POSIX filename characters, and is reparsed at
use to refuse copy/null bypass. The [specification](../spec/file-workspace-dsl.md)
defines exact protected signatures, result types, literal path grammar and
failure semantics. Existing strict `WorkspaceRelativePath` semantics are
unchanged. Only common binding path resolution is factored: File, Git and SBT
share canonical-root identity checks, containment, ancestor/final symlink
refusal and actual component coarse grants. Configured root aliases retain
their established canonicalization/retarget semantics. This is a macOS/Linux
NIO boundary with cooperative path admission, not an OS sandbox or per-file ACL.

The existing trusted V3 plan can declare only workspace roots/grants, with
empty process definitions and grants. Normal embedding assembly installs its
one inherited binding. Its existing lifecycle may allocate the standard process
driver for a plan; File does not select, depend on or invoke that driver.
No authority shortcut or File-specific host configuration is introduced.

## Policy, content and lifetime

The current actual UoW scope determines real Component ancestry, ReadOnly for
reads or Manage for writes, selected root and `GlobalRuntimeContext` limits.
Programs constructed under A use B's authority and policy when interpreted
under B. Missing services/global config fail structurally; ambient
`GlobalRuntimeContext.current` and constructor-cached limits are excluded.
Read permission implies no Build/Git/remote/HTTP/process permission.

The existing runtime resource byte policy defaults to 1 MiB (1,048,576 bytes),
allows 1..16 MiB (16,777,216 bytes), and retains its four aliases:
`textus.resource.read.max-bytes`, `textus.runtime.resource.read.max-bytes`,
`cncf.resource.read.max-bytes`, `cncf.runtime.resource.read.max-bytes`.
There is no app-selected cap or new write config. Shared
`ResourceReadAcquisition.readC` owns the `READ`/`NOFOLLOW_LINKS` stream, reads
at most cap plus one and closes on every terminal path. Decoding is strict
UTF-8 and returns complete text or failure. An absent final file differs from
an empty file; missing parents and nonregular/linked files fail.

Expected and replacement values use the same finite UTF-8 counter before an
encoded copy, followed by strict surrogate validation. Both must fit the
current cap so readback remains possible; empty strings, Unicode and final
newlines retain their literal bytes. Typed read text is an explicit consumer
payload. Generic mode/intent/result displays and failures omit private text,
host locations and raw exception messages. Effects describe FileRead/FileWrite
on a logical workspace and confer no permissions.

## Minimal cooperative installation

CreateNew checks an existing ordinary parent and installs a fully written,
forced same-parent `.cncf-file-*.tmp` through an atomic hardlink. Occupied
ordinary leaves, including equal content, produce AlreadyExists. A race winner
is checked as an ordinary file; symlink/nonregular occupants fail. Hardlink
unsupported/error has no overwrite or move fallback.

UpdateExisting requires complete bounded strict current text equal to the
supplied expected text. Missing and Changed are typed conflicts, not writes.
Under `binding.synchronized`, the adapter checks current text, stages complete
bytes, preserves POSIX permissions where supported, resolves/checks expected
text again and uses only atomic replace. Updated is returned after the real
move completes. All staged handles and paths close/clean in finally, including
errors and conflicts. Reads remain unlocked and need no writable lock or
lockfile. There is no hidden directory creation or application move/delete step.

The binding monitor covers cooperating writes through that exact shared
runtime object. Atomic install prevents partial per-file replacement but does
not promise arbitrary-editor/cross-process atomic compare-and-swap, hostile
race-proof containment, multi-file rollback or directory-fsync durability.
There is no machine-global lock, persisted replay protocol, retry or automatic
reexecution. Consumers retain durable intent and uncertain-result/no-replay
semantics. Planner/recorder preserve each File operation as a distinct External
occurrence, outside local atomic database segments.

The ordinary read/edit/write program is shown in the
[specification](../spec/file-workspace-dsl.md#ordinary-program); no launcher or
per-call limit is required. Executable specifications cover actual NIO results,
normal V3 assembly and current-context selection, literal filenames, strict
limits, privacy, permissions, conflicts, temporary cleanup, cooperating updates
and recorder/planner order. Shared root/grant and stream-lifetime evidence is
reused from existing workspace/resource specifications.

## Delivery boundary

This is partial CNCF producer integration for sm-workflow Phase 6 / P6-SBX03,
paired with the [ordinary resource design](resource-reference-dsl.md),
[File handoff](../../../sm-workflow/docs/journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md)
and [Phase 6 security profile](../../../sm-workflow/docs/phase/phase-6-security-review-profile.md).
No CNCF110 completion, deployment or primary migration is claimed. sm-workflow
owns the five File purposes, selected business compare/edit, durable intent and
no replay. Generic File Request retirement and all eight remaining original
main/launcher retirements remain mandatory. This slice introduces no new
Phase, authority, checklist stage or security review stage.
