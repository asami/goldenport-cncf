# Typed Git Execution

Status: normative specification for the intermediate CNCF Git producer.

## Intent and authority

A protected `git_*` Behavior method returns `ExecUowM[GitExecutionResult[A]]`.
It constructs exactly one `UnitOfWorkOp.GitExec(target, operation)` containing
only the logical `GitTarget(WorkspaceId)` and closed `GitOperation[A]`.
Declaration and generic program recording require no runtime services, perform
no authorization or parsing, and retain no construction-context authority.
Planning classifies each occurrence as one External boundary.

The current UnitOfWork interpreter admits the intent within
`uow:git:execute`. It uses its actual ExecutionContext and Component ancestry,
the inherited WorkspaceExecutionAdmission, a current valid binding and an
independent explicit component process capability for that operation and
selection. Read operations require ReadOnly or Manage. StageFullState,
IndexTree, Fetch, FastForward, PendingMerge, CommitPending, Push,
CreateCheckpoint, InstallCheckpoint, StageSelected, StageSelectedAll and
CommitSelected require Manage. In particular, write-tree writes Git objects.

The policy requires the identical binding object, not an equal workspace ID.
Scope labels do not establish component identity. A binding invalidated after
declaration or admission fails before launch. Generic request resolution and
generic ProcessExec interpretation reject every tagged Git execution, including
one resolved earlier under an authorized context. Generic Process behavior and
the independently approved SBT request site retain their contracts.

Exactly one private CNCF `resolveGitC` lowering site constructs a logical
ProcessExecutionRequest with adapter-rendered arguments. The owning human's
Git-UoW permission covers this site and its regression, separately from SBT
permission and runtime grants. It grants no primary generic Request use,
arbitrary argv, shell, endpoint or remote authority.

## Closed values and operations

Object IDs are exact lowercase hexadecimal 40- or 64-character values.
GitPath preserves relative Git filenames, including literal LF and backslash,
while rejecting NUL, absolute names, empty segments, dot, dotdot and .git
segments. It deliberately does not use the stricter WorkspaceRelativePath.
Branches obey strict ref-part rules: no leading option/slash, trailing
slash/dot, controls or space, forbidden Git ref punctuation, empty/dot-leading
or .lock-ending segments, double slash/dot or @{; bare @ is invalid.
Selection IDs are lowercase safe tokens of at most 64 characters.

Each row has a corresponding `git_<snake_case_operation>` wrapper with a
GitTarget first and only the listed typed parameters. There is no raw Git
execution or additional-operation API. Tracking ref means
`refs/remotes/REMOTE/REMOTE_BRANCH`; Push's branch must equal the configured
remote branch.

| Typed operation | Value A | Fixed command after shared prefix |
| --- | --- | --- |
| `Branch` | `String` | `symbolic-ref --quiet --short HEAD` |
| `Root` | `String` | `rev-parse --show-toplevel` |
| `RemoteTip` | `GitRemoteTip` | `ls-remote --exit-code --heads -- ENDPOINT refs/heads/REMOTE_BRANCH` |
| `Head` | `GitObjectId` | `rev-parse --verify HEAD^{commit}` |
| `Tree` | `GitObjectId` | `rev-parse --verify HEAD^{tree}` |
| `Status` | `Vector[GitStatusEntry]` | `status --porcelain=v1 -z --untracked-files=all` |
| `Shallow` | `Boolean` | `rev-parse --is-shallow-repository` |
| `Replacements` | `Vector[GitObjectId]` | `replace --list` |
| `MergeHead` | `Option[GitObjectId]` | `rev-parse --verify --quiet MERGE_HEAD` |
| `TrackingTip` | `Option[GitObjectId]` | `rev-parse --verify --quiet TRACKING_REF^{commit}` |
| `Relation` | `GitHistoryCounts` | `rev-list --left-right --count HEAD...TRACKING_REF` |
| `MergeBase` | `Option[GitObjectId]` | `merge-base --all HEAD TRACKING_REF` |
| `StageFullState` | `Unit` | `add --all -- .` |
| `IndexTree` | `GitObjectId` | `write-tree` |
| `UnmergedIndex` | `Vector[GitIndexEntry]` | `ls-files --unmerged -z` |
| `Fetch` | `Unit` | `fetch --no-tags --no-write-fetch-head --no-recurse-submodules --no-auto-maintenance --no-write-commit-graph --no-prune --refmap= -- ENDPOINT refs/heads/REMOTE_BRANCH:TRACKING_REF` |
| `FastForward(tip: GitObjectId)` | `Unit` | `merge --ff-only --no-edit --no-autostash --no-verify --no-gpg-sign -- TIP` |
| `PendingMerge(tip: GitObjectId)` | `Unit` | `IDENTITY merge --no-ff --no-commit --no-edit --no-autostash --no-verify --no-gpg-sign -- TIP` |
| `CommitPending` | `Unit` | `IDENTITY commit --no-verify --no-gpg-sign --no-edit` |
| `Push(tip: GitObjectId, branch: GitBranchName)` | `Unit` | `push --porcelain --no-verify --no-follow-tags --recurse-submodules=no -- ENDPOINT TIP:refs/heads/REMOTE_BRANCH` |
| `CreateCheckpoint(tree: GitObjectId, parent: GitObjectId)` | `GitObjectId` | `IDENTITY commit-tree --no-gpg-sign -m FIXED_CHECKPOINT_MESSAGE TREE -p PARENT` |
| `InstallCheckpoint(commit: GitObjectId, parent: GitObjectId)` | `Unit` | `update-ref --no-deref --create-reflog -m FIXED_CHECKPOINT_MESSAGE refs/heads/LOCAL_BRANCH COMMIT PARENT` |
| `CommitMetadata(commit: GitObjectId)` | `GitCommitMetadata` | `show --no-patch --no-show-signature --format=%H%n%T%n%P COMMIT --` |
| `BlobContent(blob: GitObjectId)` | `GitBlobContent` | `cat-file blob BLOB` |
| `CheckPendingIndex(parent: GitObjectId)` | `GitIndexCheck` | `diff --cached --no-ext-diff --no-textconv --check PARENT --` |
| `PendingDiff(parent: GitObjectId)` | `GitDiff` | `diff --cached --no-ext-diff --no-textconv --no-color --no-renames PARENT --` |
| `PendingPaths(parent: GitObjectId)` | `Vector[GitPath]` | `diff --cached --no-ext-diff --no-textconv --no-renames --name-only -z PARENT --` |
| `StageSelected(selectionId: GitSelectionId)` | `Unit` | `--literal-pathspecs add -- FIXED_SELECTED_PATH` |
| `StageSelectedAll(selectionId: GitSelectionId)` | `Unit` | `--literal-pathspecs add --all -- FIXED_SELECTED_PATH` |
| `CommitSelected(selectionId: GitSelectionId)` | `Unit` | `IDENTITY commit --no-verify --no-gpg-sign -m FIXED_SELECTED_MESSAGE` |

Every invocation directly starts the configured normal Git executable. The
shared fixed prefix is
`--no-optional-locks --no-replace-objects -c core.hooksPath=/dev/null -c core.fsmonitor=false -c credential.helper= -c gc.auto=0 -c maintenance.auto=false`.
There is no -C argument: cwd is the admitted canonical workspace directory.
IDENTITY expands to host-only `-c user.name=NAME -c user.email=EMAIL -c user.useConfigOnly=true`.
The fixed checkpoint message is exactly `sm-workflow full-state checkpoint`.
Selected paths and messages come solely from the fixed runtime declaration.

Only PATH, SSH_AUTH_SOCK and TMPDIR may be configured by the host environment.
CNCF injects GIT_TERMINAL_PROMPT=0, GIT_CONFIG_NOSYSTEM=1,
GIT_CONFIG_GLOBAL=/dev/null and LC_ALL=C; callers cannot override them.
The host declares a real available absolute regular executable, finite limits,
safe identity, strict local/remote branch names, a slash-free remote token and
a fixed non-option endpoint without control characters. Existing remote scope
and explicit per-program remote grants remain required.

## Typed result and parsing

GitExecutionResult retains target, typed operation, outcome, optional typed
value and the actual bounded ProcessExecutionResult. The outcomes are
Succeeded, Failed(exitCode), Incomplete(termination), InvalidOutput(reason)
and ConflictObservationRequired. `valueC` succeeds only for Succeeded with a
value; all other outcomes fail structurally while retaining the raw receipt.

Non-Exited terminals are Incomplete. Nonzero exits are Failed except exact
optional-ref/merge-base exit one with empty stdout, which succeeds with None,
and PendingMerge exit one, which requires conflict observation and has no
value. CheckPendingIndex also admits exact exit two as a completed findings
observation under the strict contract below. Exit one never proves a confirmed merge conflict. The primary must
consume later typed Status and UnmergedIndex plus its saved preconditions.
No stderr-text heuristic classifies conflicts or authorizes replay.

CNCF parses only complete bounded stdout: truncation or inconsistent byte
counts yield InvalidOutput. Text protocols require strict UTF-8. BlobContent
retains exact bytes and provides text only for valid UTF-8 without NUL;
an incomplete capture never becomes successful binary data.

CheckPendingIndex returns `GitIndexCheck(findingKeys: Set[String])`, whose
`isClean` is exactly `findingKeys.isEmpty`. This operation requires both actual
stdout and stderr captures to be complete: neither may be truncated and each
byteCount must equal its content size. Both streams require strict UTF-8 and
no NUL. Exit zero succeeds only with empty stdout and stderr, producing no
keys. Exit two succeeds only with empty stderr and at least one nonempty,
non-plus-prefixed diagnostic line in stdout. Split only on LF; retain all
other characters, including CR, whitespace and literal colons. A final line
without LF is accepted when the capture is complete. Duplicate diagnostics
collapse into the set; empty and plus-prefixed source/context lines contribute
no keys. Keys are opaque diagnostic identities, with no filename, line-number
or policy interpretation.

A decoded exit-two observation is Succeeded with findings and `isClean=false`;
it never establishes a clean validation. The actual ProcessExecutionResult,
including exit two and both captures, remains unchanged. Contradictory exit-zero
output, empty or context-only exit-two stdout, nonempty stderr, invalid text or
incomplete captures yield InvalidOutput with no value. Other exits, including
one, seven and 128, remain Failed with no value. Non-Exited terminals remain
Incomplete. The ordinary pure output-parser helper uses empty stderr for this
operation; the owning live GitExec decoder always passes both actual captures.

`GitIndexCheck.parseStoredC(exitCode: Int, stdout: String, stderr: String)`
decodes a previously stored ordinary validation Receipt through the same text
parser. It rejects null, NUL, malformed Unicode, unsupported exits and the same
contradictions. This pure codec starts no Git process and grants no execution.
It cannot establish completion, capture provenance or bounds; the consumer must
first retain its existing complete/bounded Receipt admission. The existing saved
Receipt schema is preserved. Live execution and decoding remain within the
current UoW; this compatibility codec supplies historical facts only.

The official [Git diff --check documentation](https://git-scm.com/docs/git-diff#Documentation/git-diff.txt---check)
specifies a nonzero status for errors. Exact exit two for the recognized
findings observation is the locally selected admitted normal-tool contract;
it is not a claim that every Git distribution mandates that exact status.

Root removes exactly one required trailing LF, preserving embedded or final
LF in the root name, and must equal the binding's current canonical path.
Branch, ref and object observations require the exact single-line protocol.
RemoteTip requires exactly one row for the fixed branch. Shallow accepts only
true/false. Relation requires two nonnegative tab-separated Long values;
overflow fails. Successful MergeBase requires exactly one OID.

Porcelain v1 -z has an exact XY header and separating space. Rename/copy
records contain destination then source, each NUL-terminated. Ordinary pairs
accept X in MTARC with Y space/M/T/D, X space with Y A/M/T/D/R/C, and D-space.
The only unmerged pairs are DD, AU, UD, UA, DU, AA and UU. ?? and !! are
accepted; space-space, unknown and incomplete pairs are rejected. T is a
valid type change.

UnmergedIndex requires a six-octal mode, canonical blob OID, stage 1/2/3,
tab and literal NUL-terminated path. Duplicate (path,stage) entries fail.
Commit metadata has exact commit/tree/parents lines. PendingPaths rejects
duplicate paths. GitDiff retains strict text plus a binary marker fact.
Domain budgets, saved-intent comparisons, workflow conflict decisions,
durable results and uncertain-effect no-replay policy remain primary-owned.

The protocol references are the official [Git status porcelain v1
documentation](https://git-scm.com/docs/git-status),
[Git ls-files output documentation](https://git-scm.com/docs/git-ls-files) and
[Git rev-parse documentation](https://git-scm.com/docs/git-rev-parse).

## Host V3 and compatibility

The strict schema `cncf.runtime-process-execution.v3` contains exactly
schema, programs, grants, workspaces, workspaceGrants, sbtPrograms and
mandatory gitPrograms. V1 and V2 retain their strict existing field sets and
creator signatures. Generic programs remain generic-only; semantic definitions
join the policy only after common workspace assembly.

Each Git declaration has exactly target, operation (stable kebab-case kind),
capability, identity, gitExecutable, localBranch, remote, remoteBranch,
endpoint, commitIdentity, selectionId, selectedPath, commitMessage, limits
and environment. commitIdentity is null or exactly {name,email}, required
only for PendingMerge, CommitPending, CreateCheckpoint and CommitSelected.
Selection ID is required only for the three selected kinds. Fixed path is
required only for StageSelected/StageSelectedAll. Fixed message is required
only for CommitSelected and is nonempty, non-NUL and at most 4096 UTF-8 bytes.
Unrelated optional fields must be null. Identity values are nonblank and
control-free. There is no executable fallback or generic argv schema.

One WorkspaceExecutionAdmission produces all SBT and Git bindings. Duplicate
capabilities, workspace/kind/selection keys, owners or grants fail.
Each process grant requires corresponding same-owner workspace permission.
Workspace permission alone creates no process grant. Per workspace, at most
128 StageSelected, 256 StageSelectedAll and 64 CommitSelected declarations
are admitted, using the existing conflict-path/goal-path/goal-step bounds.
A selection token carries no application-specific prefix or new family field.

## Lifecycle and qualification boundary

Git uses the existing shared process calltree, driver, UoW WorkArea and handle,
bounded captures, finite timeouts, Job cancellation, descendant cleanup and
closure. Typed decoding occurs inside that same GitExec callback. There is no
nested UoW, extra main/JVM, dedicated Git driver, shell, global lock or detached
process route. Checkout writes are external effects and are not database
rollback. The existing normal direct sbt adapter and native sbt locks remain.

GitExecutionSpec, GitExecutionDslSpec, the host codec/bootstrap specs and
LocalProcessExecutionDriverSpec author this producer contract. Real Git
fixtures use temporary local repositories and fixed temporary bare remotes;
fixture-only ProcessBuilder initializes preconditions. Subject Git execution
uses the typed UoW. No external remote endpoint is part of these fixtures.

This producer is intermediate. The actual sm-workflow all-consumer migration,
old Git definitions and executable launch-route removal, same-workflow actual
Git result proof and eight remaining original main methods (one of the original
nine already removed) remain mandatory unfinished parent work. Producer
qualification does not close the Step, Phase, original-nine-main obligation,
or whole-workflow proof and makes no publication claim.
