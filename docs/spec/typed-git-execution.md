# Typed Git Execution

## Explicit selected committed-tree entries

`GitOperation.TreeEntries(tree, selectionId)` is a read-only operation returning
`Vector[GitSelectedTreeEntry]`. The host explicitly registers `tree-entries` with
the finite, ordered `selectedPaths` and matching `selectionId`; there must be
1..256 distinct canonical Git paths, with at most 64 such declarations per
workspace. Existing process argument/output limits still apply. Declarations sharing a workspace and
selection ID with other bounded selected operations must use the same ordered
paths. No repository-family or unselected enum expansion grants this operation.

Each selected path has exactly one result in selection order. `entry = None`
means absence in the requested tree; `Some(GitRegularTreeEntry(mode, blob))`
means an observed regular blob with mode `100644` or `100755`. These are native
Git observations, not content certificates, worktree-mode observations, Human
consent or acceptance. The explicit tree is supplied by the caller; consumers
must bind it to their independently observed commit/tree target.

CNCF uses fixed literal-path `ls-tree --full-tree -z` under the admitted workspace,
existing process limits and ordinary GitExec UoW. Links, submodules, directories,
unexpected/duplicate paths, invalid modes/IDs, incomplete capture/records and
resource overflow never produce a successful entry vector. Missing process or
workspace grants and unknown selections refuse through normal admission. No
arbitrary argv, shell interpretation, BlobContent fallback or new UoW operation
is introduced. Existing BlobContent can separately read observed blob bytes.

Executable contracts: `GitExecutionSpec`, `GitExecutionDslSpec` and
`RuntimeProcessExecutionPlanCodecSpec`.

V3/V5 `gitPrograms` register the exact operation token `tree-entries`, a
non-null `selectionId`, and the required `selectedPaths` array. As with the
existing bounded read declarations, `selectedPath`, `commitMessage` and
`commitIdentity` are null; the other existing declaration fields and independent
component/process/workspace grants remain required. The protected DSL is
`git_tree_entries(target, tree, selectionId)` and records one ordinary GitExec.

## Adopted execution correction — 2026-10-26

[Git execution defaults and diagnostics](git-execution-defaults-and-diagnostics.md)
supersedes the executable-setup, per-operation registration, blanket environment
restrictions and failure-diagnostic requirements below, including those in the
repository-family delivery. CNCF still owns typed operations and authorization;
ordinary use resolves PATH Git when no executable override is supplied. These
are adopted requirements, not a claim of installed implementation or validation.
Preserve the in-progress repository route repair and its evidence under the
[correction plan](../phase/git-execution-route-correction.md).

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
CommitSelected, PreparePurposeCandidate, MaterializePurposeCandidate and
CommitPurposeCandidate require Manage. In particular, write-tree writes Git objects.

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

## Read-only admission preflight — 2026-10-27

`GitExecutionPreflight.validateC[A](context, target, operation)` returns
`Consequence[Unit]` for an existing typed Git intent. It uses the same current
workspace, actual component ancestry, operation permission, selected-program
and capability/grant admission as ordinary UnitOfWork Git execution. It adds
no operation kind, executable selection mechanism or independent policy check.

- A null execution context, target or operation is a typed
  argument failure. A null/unknown selector, foreign workspace or missing
  component/workspace/process/capability authority is refused through the
  ordinary admission rules. Refusal starts no process.
- Success exposes only Unit. It runs no Git/process/UoW effect, changes no
  worktree/index/HEAD or workflow record, and returns no resolved execution,
  ticket, reservation or authority object.
- The result describes admission at that observation time. It does not
  authorize or guarantee future execution. The existing UnitOfWork Git path
  independently admits its intent against its actual current context again;
  a previously successful preflight cannot bypass a later refusal.

The paired `GitExecutionSpec` section **Read-only typed Git preflight** verifies
current-selection success with unchanged source/index/HEAD and no execution;
typed invalid/crossed-scope refusal including generated unregistered selectors;
and actual UoW refusal under another component after a successful observation.
These tests use local specification-owned fixtures, not consumer Session state.

## Closed values and operations

Object IDs are exact lowercase hexadecimal 40- or 64-character values.
GitPath preserves relative Git filenames, including literal LF and backslash,
while rejecting NUL, absolute names, empty segments, dot, dotdot and .git
segments. It deliberately does not use the stricter WorkspaceRelativePath.
Branches obey strict ref-part rules: no leading option/slash, trailing
slash/dot, controls or space, forbidden Git ref punctuation, empty/dot-leading
or .lock-ending segments, double slash/dot or @{; bare @ is invalid.
Selection IDs are lowercase safe tokens of at most 64 characters.
The finite operation set has thirty-seven kinds. CheckSelectedIndex and
SelectedIndexChanges carry a selection ID and canonical parent OID;
TreeChanges carries two canonical OIDs without a selection. TreeEntries carries
an explicit tree OID and a selection ID. All four are
ReadOnly observations without a commit identity. CommitSelected remains
Manage and requires the fixed host commit identity.

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
| `CommitSelected(selectionId: GitSelectionId)` | `Unit` | fixed preparation below, then `IDENTITY --literal-pathspecs commit --only --no-verify --no-gpg-sign -m FIXED_SELECTED_MESSAGE -- PHYSICAL_SELECTED_PATHS` |
| `CheckSelectedIndex(selectionId: GitSelectionId, parent: GitObjectId)` | `GitIndexCheck` | `--literal-pathspecs diff --cached --no-ext-diff --no-textconv --check PARENT -- FIXED_SELECTED_PATHS` |
| `SelectedIndexChanges(selectionId: GitSelectionId, parent: GitObjectId)` | `Vector[GitTreeChange]` | `--literal-pathspecs diff --cached --no-ext-diff --no-textconv --raw --no-abbrev --no-renames -z PARENT -- FIXED_SELECTED_PATHS` |
| `TreeChanges(before: GitObjectId, after: GitObjectId)` | `Vector[GitTreeChange]` | `diff-tree --no-commit-id --no-ext-diff --no-textconv --raw --no-abbrev --no-renames -r -z BEFORE AFTER --` |
| `TreeEntries(tree: GitObjectId, selectionId: GitSelectionId)` | `Vector[GitSelectedTreeEntry]` | `--literal-pathspecs ls-tree --full-tree -z TREE -- FIXED_SELECTED_PATHS` |
| `PreparePurposeCandidate(selectionId, request)` | `GitPurposeCandidate` | bounded private-tree preparation and fixed-identity `commit-tree` |
| `MaterializePurposeCandidate(selectionId, candidate, workspace)` | `Unit` | revalidate candidate, then `worktree add --detach -- DESTINATION COMMIT` |
| `CommitPurposeCandidate(selectionId, candidate)` | `Unit` | revalidate candidate, install any selected worktree repair, compare-parent `update-ref`, then install projected index |

### Purpose-specific candidates

The three purpose operations use finite ordered selections and fixed host commit
identity/message. An explicit selected declaration admits host-owned paths and
message. Under adopted GIT-1, per-call `GitSelectionParameters` may instead narrow
admitted repository capabilities: all three purpose operations require both
`CreateCheckpoint` and `InstallCheckpoint` grants, and use their intersected
limits. A repository grant alone supplies no selected paths or message. Their protected DSL wrappers are
`git_prepare_purpose_candidate`, `git_materialize_purpose_candidate` and
`git_commit_purpose_candidate`; each records one ordinary GitExec intent.
No purpose operation is added to the fixed repository-family expansion.

`GitPurposeRequest` binds the expected native parent commit and index tree to 1..256
distinct selected regular-file paths, expected worktree text and intended
optional text content. Paths must equal an admitted scope or be within its
directory. NUL/binary input, unsupported entries, inconsistent existing modes,
stale branch/HEAD/index/worktree observations and inseparable staged remainders
are refused. Text is finite UTF-8, bounded by one eighth of work-area capacity
and the admitted stdin/stdout limits. Only the closed internal purpose lowering
can supply byte input and a private GIT_INDEX_FILE; ordinary Git requests keep
their existing input restrictions.

Preparation builds native candidate objects through private indexes without
changing source HEAD, real index or worktree. The projected index preserves
independent staged changes. An optional baseline tree allows a selected repair
to be projected onto the existing worktree while retaining its other content.
Candidate values and object IDs express observations, not human acceptance.

Materialization requires a separately admitted Manage workspace that is empty,
distinct from the source and outside its ancestry in either direction. It
revalidates the candidate before creating its detached Git worktree. Installation
revalidates the inputs and exact candidate tree, single parent and fixed message,
then installs the selected worktree projection, advances HEAD with the expected
parent, and installs the remaining staged tree. A failure after an effect must
be collected and reconciled by the owning consumer; it is not authority to
replay. Settled operations release their temporary inputs; an unconfirmed
process retains the work area it may still own.

Every invocation directly starts the configured normal Git executable. The
shared fixed prefix is
`--no-optional-locks --no-replace-objects -c core.hooksPath=/dev/null -c core.fsmonitor=false -c credential.helper= -c gc.auto=0 -c maintenance.auto=false`.
There is no -C argument: cwd is the admitted canonical workspace directory.
IDENTITY expands to host-only `-c user.name=NAME -c user.email=EMAIL -c user.useConfigOnly=true`.
The fixed checkpoint message is exactly `sm-workflow full-state checkpoint`.
Selected scopes and messages come solely from the fixed runtime declaration.
FIXED_SELECTED_PATHS expands to the exact ordered host vector of literal
GitPath arguments for selected observations. CommitSelected interprets that
vector as logical allowed scopes, which may include unchanged files,
directories and missing future files. After current workspace/component/
capability admission, CNCF runs the fixed normal Git preparation
`--literal-pathspecs diff --cached --no-ext-diff --no-textconv --no-renames --name-only -z HEAD -- FIXED_SELECTED_PATHS`.
Complete UTF-8 NUL records must identify one through 256 distinct GitPaths,
each equal to an allowed scope or within its slash-delimited directory.
Deletion paths and both in-scope rename endpoints remain eligible; filesystem
existence does not filter them. Empty, malformed, duplicate, out-of-scope,
oversized, incomplete or failed observations stop before commit dispatch.

CNCF then executes
`--literal-pathspecs diff --quiet --no-ext-diff --no-textconv -- PHYSICAL_SELECTED_PATHS`.
Only a complete exit-zero receipt with empty captures permits the commit.
Thus selected staged content that differs from the worktree is refused before
`commit --only` can stage it. The final commit uses exactly the observed
physical paths and the original fixed identity/message, disabled hooks and
no-GPG policy. Unrelated staged and unstaged paths retain their state, even
when a rename has an endpoint outside the admitted scopes. There is no
empty-commit or all-files fallback. Every subprocess uses the inherited
executable, identical workspace binding, environment, capability, effective
finite limits, process calltree and UoW lifecycle. Only closed internal
definitions and copies of the already admitted request are used; there is no
additional request constructor or application-selectable argv interface.

Only canonical OIDs occupy application-supplied dynamic slots; executable, cwd,
environment, limits, allowed scopes and message remain host-fixed. Existing process
argument limits still apply. No arbitrary suffix or pathspec interpretation
is admitted.

Exact host-fixed literal path slots preserve LF, CR and TAB, together with
literal colons and backslashes, as single arguments passed directly without a
shell. Literal matching remains exact; NUL and every other ISO control are
rejected, and lower-hex prefixes and suffixes remain control-free. Structural
GitPath validation, canonical OID slots, explicit grants, bounded captures and
finite argument and execution limits remain required. Quoted EOF diagnostics
cannot authorize correction when their path classification is unavailable.

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

For a dispatched CommitSelected, selectedPaths contains its exact physical
file paths on both success and failure. Before physical selection is validated,
preparation failures carry an empty selection; later worktree-check failures
carry the validated physical selection. Preparation retains the actual probe
receipt, including its real nonzero exit or termination uncertainty, and never
manufactures an exit-one commit receipt. A complete empty probe is InvalidOutput
with its actual exit-zero receipt and no successful commit value. Selected
observations continue to expose their original declared scope metadata.

GIT-DIAG-01: `valueC` failures retain the `argumentPolicyViolation` key `git`,
policy `git.execution.result` and expected `complete successful typed Git value`.
Their actual diagnostic is bounded, deterministic metadata: the operation kind's
wire name, a closed outcome tag with numeric exit code where present, the actual
process termination (including the output-limit stream or termination-unconfirmed),
and each stream's observed byte count, retained content byte size and truncation
flag. A Succeeded outcome without a value remains a failure. Captured stdout or
stderr text, parser reasons, program identity, argv, environment, workspace and
paths never enter this diagnostic. Raw results and selected paths remain unchanged;
truncation and byte-count evidence alongside TerminationUnconfirmed never settle
that uncertainty or reclassify it as confirmed output-limit termination or an
unexecuted operation.

CAND-BUDGET-01: the common `GitDiffParameters` policy resolves the canonical
ordinary CNCF parameter `cncf.git.diff.maximum-bytes`. Absence selects 1048576
bytes (one MiB); an explicit value must be an integral `NumberValue` from 1
through 16777216 inclusive (an explicit sixteen MiB ceiling). Null, text,
fractional, nonpositive and above-maximum
values fail configuration resolution without coercion, aliases, CLI flags or
system-property fallback. Its public `key`, `defaultBytes`, `maximumBytes` and
`resolveC(ExecutionContext)` / `resolveC(ResolvedParameters)` API provide the same
selected byte ceiling to consumers. `PendingDiff` admission tightens only the
effective stdout limit to the smaller of its independently admitted explicit
host limit and that selected ceiling; it never widens host authority. Invalid
configuration fails before driver launch. Other operations and limits retain
their existing admission behavior, and typed execution remains inside the
owning UoW with the existing request lowering and driver lifecycle.
The existing ordinary driver enforces that effective limit while capturing the
process output. A successfully exited `PendingDiff` requires complete stdout and
stderr: neither may be truncated, and each observed byte count must equal its
retained content byte size. Incomplete captures yield `InvalidOutput` with a
fixed payload-free reason and no typed value, retaining the actual raw result.
Non-Exited results keep their existing `Incomplete` termination, including
uncertainty. Consumers use the successful complete typed diff without repeating
its byte-limit check; workflow-specific policy and ownership checks remain
consumer responsibilities.

Non-Exited terminals are Incomplete. Nonzero exits are Failed except exact
optional-ref/merge-base exit one with empty stdout, which succeeds with None,
and PendingMerge exit one, which requires conflict observation and has no
value. CheckPendingIndex and CheckSelectedIndex also admit exact exit two as a completed findings
observation under the strict contract below. Exit one never proves a confirmed merge conflict. The primary must
consume later typed Status and UnmergedIndex plus its saved preconditions.
No stderr-text heuristic classifies conflicts or authorizes replay.

CNCF parses only complete bounded stdout: truncation or inconsistent byte
counts yield InvalidOutput. Text protocols require strict UTF-8. BlobContent
retains exact bytes and provides text only for valid UTF-8 without NUL;
an incomplete capture never becomes successful binary data.

Both index checks return `GitIndexCheck(findingKeys: Set[String],
eofBlankLinePaths: Set[GitPath] = Set.empty, onlyEofBlankLines: Boolean = false)`, whose
`isClean` is exactly `findingKeys.isEmpty`. Both require actual
stdout and stderr captures to be complete: neither may be truncated and each
byteCount must equal its content size. Both streams require strict UTF-8 and
no NUL. Exit zero succeeds only with empty stdout and stderr, producing no
keys, no EOF paths and a false EOF-only flag. Exit two succeeds only with empty stderr and at least one nonempty,
non-plus-prefixed diagnostic line in stdout. Split only on LF; retain all
other characters, including CR, whitespace and literal colons. A final line
without LF is accepted when the capture is complete. Duplicate diagnostics
collapse into the set; empty and plus-prefixed source/context lines contribute
no keys. Keys remain exact diagnostic identities. An additional EOF-only fact
is true only when every diagnostic key matches exactly
`<literal path>:<positive line>: new blank line at EOF.` and every captured
path passes GitPath validation. The matching paths become the exact path set.
Quoted, control-containing or backslash-escaped pathname renderings are
ambiguous and do not classify. Mixed findings, other whitespace diagnostics,
invalid paths or unmatched lines yield false and an empty EOF path set.

A decoded exit-two observation is Succeeded with findings and `isClean=false`;
it never establishes a clean validation. The actual ProcessExecutionResult,
including exit two and both captures, remains unchanged. Contradictory exit-zero
output, empty or context-only exit-two stdout, nonempty stderr, invalid text or
incomplete captures yield InvalidOutput with no value. Other exits, including
one, seven and 128, remain Failed with no value. Non-Exited terminals remain
Incomplete. The ordinary pure output-parser helper uses empty stderr for either
operation; the owning live GitExec decoder always passes both actual captures.

This classification is an observation of actual Git diagnostics. It grants no
authority to correct files, reuse review or infer a failed commit dispatch.
The read-only check cannot create a commit; the primary records whether it
dispatched a commit. Post-dispatch failure and unknown-effect classification
retain their existing contracts and never follow from an exit code alone.

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
SelectedIndexChanges and TreeChanges parse actual NUL raw diff output into
`GitTreeChange(oldMode: String, newMode: String, oldBlob: Option[GitObjectId],
newBlob: Option[GitObjectId], status: String, path: GitPath)`. Each record has
exactly five space-separated metadata fields (`:OLDMODE NEWMODE OLDOID NEWOID
STATUS`), NUL, a literal nonempty path and NUL. Both modes are six octal
digits, and both OIDs are canonical lowercase IDs of the same native 40- or
64-character length. A zero OID becomes None exactly when its mode is zero.
Only A, D, M and T are admitted: A has absent old and present new, D has
present old and absent new, and M/T require both present. Unmerged statuses,
scores, renames/copies, extra fields, odd record counts, incomplete captures,
invalid UTF-8, duplicate paths and invalid GitPaths fail. Empty complete
output yields an empty vector. Paths preserve colon, backslash and LF and
are never split on whitespace or newline.
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

Each Git declaration retains exactly the base fields target, operation (stable kebab-case kind),
capability, identity, gitExecutable, localBranch, remote, remoteBranch,
endpoint, commitIdentity, selectionId, selectedPath, commitMessage, limits
and environment. commitIdentity is null or exactly {name,email}, required
only for PendingMerge, CommitPending, CreateCheckpoint, CommitSelected and the
three purpose operations.
Exactly CommitSelected, CheckSelectedIndex, SelectedIndexChanges, TreeEntries,
PreparePurposeCandidate, MaterializePurposeCandidate and CommitPurposeCandidate also
require selectedPaths: a nonnull array of 1..256 unique valid literal GitPaths.
The operation is decoded before selecting its strict allowed and required
field set. Those seven kinds emit selectedPaths; every other kind omits it
and rejects it even when null or empty. Missing, null, empty, duplicate or
escaping path arrays fail. The private direct creator's trailing empty default
is only nonapplicable data; it never authorizes an old unbounded CommitSelected
declaration. There are no aliases or old-wire fallbacks.
Selection ID is required only for the nine selected kinds. Fixed single path is
required only for StageSelected/StageSelectedAll. Fixed message is required
only for CommitSelected and the three purpose operations and is nonempty,
non-NUL and at most 4096 UTF-8 bytes. Purpose messages must also be trimmed.
Unrelated optional fields must be null. Identity values are nonblank and
control-free. There is no executable fallback or generic argv schema.

One WorkspaceExecutionAdmission produces all SBT and Git bindings. Duplicate
capabilities, workspace/kind/selection keys, owners or grants fail.
Each process grant requires corresponding same-owner workspace permission.
Workspace permission alone creates no process grant. Per workspace, at most
128 StageSelected, 256 StageSelectedAll, 64 CommitSelected,
64 CheckSelectedIndex, 64 SelectedIndexChanges, 64 TreeEntries and 64 of each
purpose operation declarations
are admitted, using the existing conflict-path/goal-path/goal-step bounds.
A selection token carries no application-specific prefix or new family field.

## Host V5 repository authority

### R1 — Explicit closed repository family

`cncf.runtime-process-execution.v5` has exactly the V3 root fields plus the
mandatory `gitRepositories` array. V4 remains unsupported. Each repository
entry admits exactly `target`, `capability`, `identity`, `gitExecutable`,
`localBranch`, `remote`, `remoteBranch`, `endpoint`, `commitIdentity`, `limits`
and `environment`. All except `gitExecutable` are required. `commitIdentity` is a nonnull
`{name,email}` object validated by the existing Git identity rules. There is
no operation list, caller argv, selection scope or message field.

The repository family deliberately contains these 28 current kinds:
Branch, Root, RemoteTip, Head, Tree, Status, Shallow, Replacements, MergeHead,
TrackingTip, Relation, MergeBase, StageFullState, IndexTree, UnmergedIndex,
Fetch, FastForward, PendingMerge, CommitPending, Push, CreateCheckpoint,
InstallCheckpoint, CommitMetadata, BlobContent, CheckPendingIndex, PendingDiff,
PendingPaths and TreeChanges. This is a closed contract, not enumeration-based
authority discovery. A future kind requires an explicit family contract update.

### R2 — Same-owner independent authority

A public family capability grants those 28 operations only to the actual
Component named by that process grant. Each family grant also requires that
same Component's Manage permission on the target workspace, including when
only read operations are requested. Workspace existence, installed Git or a
different owner's grants cannot supply process authority. An ungranted family
may be declared but cannot execute. Undeclared targets remain denied.

### R3 — Existing typed route and binding

The host expands each family into existing private Git declarations with
capability and safe identity `BASE-KIND_WIRE_NAME`. Identity is supplied only
for kinds whose existing contract requires it. Selection, path and message
fields remain absent. Every definition is validated through
`GitProgramDefinition.createC`, shares the one admitted WorkspaceBinding and
uses the existing typed argument renderer, policy, admission, driver and UoW
lifecycle. CNCF owns executable, endpoint, branch, environment and limits;
applications continue to submit typed Git intent only.

### R4 — Selected and legacy contracts remain explicit

The nine selected kinds retain explicit `gitPrograms` declarations and
independent grants for host-owned selections. Adopted GIT-1 also permits finite
per-call selections narrowed from admitted repository capabilities; the purpose
operations require both checkpoint grants and their intersected limits as
specified above. A family never supplies an unbounded selection. Selection
counts, ordered logical scopes, fixed messages and derived physical path rules
remain unchanged. A family may coexist with explicit selected
declarations, but any explicit nonselected declaration on the family target
is rejected. V1/V2/V3 retain their exact schemas, encoding and grant behavior;
an operation absent from V3 remains denied without fallback.

### R5 — Strict public wire and internal namespace

Only one family is allowed per target. Capability and identity bases must each
match `[a-z][a-z0-9-]{0,31}`, keeping generated names within 64 characters.
Public family bases and generated names must be unique within their capability
and identity namespaces, including generic, SBT and explicit Git declarations.
Public grants may name only declared public capabilities; granting an internal
derived capability is invalid. Internal per-kind grants are expanded only for
the owner who received the public family grant. Encoding retains the original
public family entries and grants; it never emits expanded operation lists or
generated grants. Missing required fields, null fields, duplicate and unknown
fields are rejected.

### R6 — Ordinary Git discovery and truthful overrides

`gitExecutable` may be omitted. When supplied it must be an absolute string;
null and malformed overrides are invalid. The existing Git definition validator
requires the explicit path to resolve to a regular executable file. An invalid
override fails without searching PATH.

When omitted, host assembly searches for ordinary `git` in the ordered PATH
from the declaration's environment, or the host environment when no PATH was
configured. Every PATH directory must be absolute and nonempty; relative and
empty entries are rejected without searching the current working directory.
The first regular executable `git` is resolved to its real path. Missing Git
produces a configuration failure identifying git/PATH unavailability. There is
no shell or hardcoded executable fallback. An inherited PATH is copied to the
generated Git environment so host selection and child execution use the same
PATH. The public declaration retains its original environment and optional
override; encoding an omitted executable continues to omit the field.

### Examples and executable evidence

| Example | Rules | Executable specification |
| --- | --- | --- |
| E1 One repository family covers its exact closed scope and real local Root/RemoteTip/Fetch, full-state staging, index, checkpoint, install and push | R1,R2,R3 | [GitRepositoryExecutionSpec](../../src/test/scala/org/goldenport/cncf/processexecution/GitRepositoryExecutionSpec.scala) |
| E2 Actual-component authority, Manage, undeclared targets and independent selected grants | R2,R3,R4 | [GitRepositoryExecutionSpec](../../src/test/scala/org/goldenport/cncf/processexecution/GitRepositoryExecutionSpec.scala) |
| E3 Public V5 round trip, strict schema, namespace, default executable and legacy denial | R1,R2,R4,R5,R6 | [RuntimeProcessExecutionPlanCodecSpec](../../src/test/scala/org/goldenport/cncf/processexecution/RuntimeProcessExecutionPlanCodecSpec.scala) |
| E4 Ordinary PATH default, explicit override and truthful executable failure | R3,R5,R6 | [GitRepositoryExecutionSpec](../../src/test/scala/org/goldenport/cncf/processexecution/GitRepositoryExecutionSpec.scala) |

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
Selected-check fixtures stage a new selected file with an extra terminal LF,
observe actual EOF-only findings with HEAD unchanged, apply a bounded fixture
correction and restage before a clean check, selected raw observation and real
selected commit. The commit and typed tree diff contain only the selected
file. Unrelated staged and unstaged file bytes and index/worktree states are
preserved. Separate literal colon/backslash/LF selection coverage uses the
same selected observations and commit adapter.

This producer is intermediate. The actual sm-workflow all-consumer migration,
old Git definitions and executable launch-route removal, same-workflow actual
Git result proof and eight remaining original main methods (one of the original
nine already removed) remain mandatory unfinished parent work. Producer
qualification does not close the Step, Phase, original-nine-main obligation,
or whole-workflow proof and makes no publication claim.
