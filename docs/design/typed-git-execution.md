# Typed Git Execution

## Finite committed-entry projection

The explicit `TreeEntries(tree, selectionId)` intent uses the ordinary GitExec
interpreter and component/workspace/capability admission. Its declaration is
selected, read-only and bounded to 1..256 ordered `selectedPaths`. It remains
outside the closed repository-family expansion; adding a Git enum case supplies
no unselected default permission. V3/V5 host codecs preserve its finite selection.

The fixed command is the existing Git safety prefix followed by
`--literal-pathspecs ls-tree --full-tree -z <tree> -- <host-selected-paths>`.
Only the canonical tree object ID is dynamic. Selection paths remain host-owned
literal arguments and share existing process argument/output limits. The parser
reads complete UTF-8 NUL records with one metadata/path tab boundary, validates
regular mode/blob type/object ID and exact selected membership, rejects duplicate
records, and projects results into host selection order. Absent records become
explicit `GitSelectedTreeEntry(path, None)`; present records carry the closed
`GitRegularTreeEntry(mode, blob)` value. Directory entries are refused rather
than recursively widening the selected set.

Committed identities and bytes remain separate observations. Consumers bind the
requested tree to actual CommitMetadata, then may use BlobContent and managed
file observations for their own business comparison. This API does not observe
the live index/worktree or authorize source-base adoption, publication or commit.

## Execution responsibility refinement — 2026-10-26

CNCF owns typed operation/argument construction independently of optional
executable configuration. Resolve an explicit executable or ordinary PATH Git;
do not require users to register each operation. Existing current-UoW admission
owns permission, while the process driver owns lifecycle and diagnostic capture.
Normal Git configuration/authentication remains usable through that runtime
boundary. sm-workflow owns effect reconciliation and synchronization decisions.
The [defaults and diagnostics contract](../spec/git-execution-defaults-and-diagnostics.md)
supersedes the fixed-environment/per-operation-setup assumptions below. Derived
internal definitions are an implementation detail, not mandatory user setup.

Status: normative design for the intermediate CNCF Git producer.

## One semantic intent and one lifecycle

The [typed Git specification](../spec/typed-git-execution.md) owns the exact
thirty-seven-operation/parameter/result table and strict wire/parser contracts.

```text
protected git_* wrapper
  -> GitExec(target, typed operation), one External occurrence
  -> current UnitOfWork ExecutionContext and actual Component ancestry
  -> one inherited WorkspaceExecutionAdmission and current binding
  -> independent component per-operation Process capability
  -> closed Git program definition for the identical binding
  -> shared managed direct normal Git execution at canonical checkout cwd
  -> CNCF typed decoding inside the same GitExec interpreter callback
  -> primary workflow durable intent, result and judgment
```

Behavior constructs immutable intent only. It performs no service lookup,
authorization, host selection or decode. Consequently, constructing under
authorized context A cannot authorize denied interpretation B, and a program
constructed without services can run under authorized B using B's binding.
Malformed typed intent is rejected during interpretation before external work.

The canonical UnitOfWorkOp algebra owns GitExec. Planning and recording retain
one semantic occurrence; there is no outward generic ProcessExec, eager
exec_from admission, nested UoW or alternate process lifecycle. Generic
ProcessExec rejects Git and SBT semantic tags before driver allocation even
when a caller presents a valid previously resolved execution.

## Runtime ownership

CNCF owns the configured executable, complete argv rendering, canonical cwd,
fixed environment, finite limits and parsing. The private Git metadata stores
the shared WorkspaceBinding, closed kind, optional selection ID and trusted
host branch/remote/endpoint/identity/path/message values. OID template slots
are the only dynamic command slots. A branch argument on Push is checked
against the fixed remote branch; selected paths/messages never enter from CAR.

CommitSelected declarations carry logical allowed scopes. After the ordinary
current-context admission, the owning GitExec callback derives physical paths
using fixed literal cached name-only/no-renames NUL diff against HEAD within
those scopes. CNCF parses complete bounded captures into distinct GitPaths,
rejects empty or more than 256 paths and checks every path against the original
scope boundary. It retains staged deletions and both in-scope rename endpoints
without filesystem existence filtering. Unchanged and missing future declared
paths never become mandatory physical pathspecs.

A second fixed literal diff --quiet observes worktree changes on exactly those
physical paths. Only complete empty exit-zero captures admit the final
commit --only. Staged-plus-unstaged selected content therefore stops before
index mutation. Closed internal constructors copy the original definition's
executable, binding, environment and capability and preserve effective limits;
the already admitted request is copied without another Request constructor.
Every probe and commit uses the interpreter's existing process-calltree and
managed-execution callback within the same GitExec. Other operations retain
their single execution. No application-visible operation or general rebinding
API is added. The actual commit result exposes only its physical paths on
success or failure; preparation retains actual typed failure and probe receipt
without inventing a commit exit or settling uncertain termination.

Only the exact private resolveGitC site creates a ProcessExecutionRequest for
closed typed Git lowering. The owning-human permission covers that purpose
and its regression. The pre-existing SBT request site remains separate and
unchanged. Neither permission grants primary generic Process use or remote
scope expansion. Runtime grants and platform execution permission remain
separate from that human source-code authorization.

ReadOnly/Manage workspace authority and an explicit actual-component process
grant are both required. IndexTree requires Manage because write-tree writes
objects. RemoteTip also needs its independent fixed remote capability.
Definition lookup checks workspace/kind/selection uniqueness and binding
object identity; the current directory identity is rechecked before launch.

## Common host assembly and compatibility

V3 extends strict V2 by a mandatory gitPrograms array, without altering V1/V2
field sets or public creator signatures. The common semantic creator builds
exactly one WorkspaceExecutionAdmission before rendering either SBT or Git
definitions. Generic definitions remain in plan.programs; tagged definitions
join the common ProcessExecutionPolicy. Bootstrap already forwards the
workspace service through GlobalRuntimeContext/ScopeContext before owning
components assemble, so no new registry, lifecycle or context source is needed.

Closed declaration fields reject unknown keys, malformed IDs/ref names,
invalid selectors, unrelated option fields, unsafe paths/messages/environment,
duplicate capabilities or semantic keys, and inconsistent owner/grant pairs.
The fixed per-workspace selection counts preserve old conflict/goal bounds.
V3 preserves literal Git filenames and root LF; V1/V2 parsing stays strict as
before. Serialization encodes semantic declarations separately and reconstructs
one shared SBT/Git binding on decode.

## V5 repository family assembly

The host may deliberately grant a repository family in the additive V5 schema.
The [R1–R6 contract](../spec/typed-git-execution.md#host-v5-repository-authority)
fixes its public fields, closed 28-kind operation scope and authority rules.
This removes per-operation host configuration for ordinary repository sync
while keeping operations, arguments and interpretation CNCF-owned.

The common semantic creator validates the public namespaces and family target
uniqueness before policy installation. It rejects explicit nonselected declarations on a
family target, generated/public name collisions and grants to generated names.
Each granted family requires the actual grantee's independent Manage workspace
grant. Only that owner's process grants expand into internal per-kind grants.
An installed executable or workspace does not imply a family grant.

Expansion produces ordinary GitRuntimeDeclarations with derived capability and
identity names and no selection, path or message. Required commit identity is
copied only to identity-bearing operations. The fixed list deliberately names
the current 28 nonselected kinds; future enum growth cannot expand authority.
All generated definitions pass through GitProgramDefinition.createC and share
the existing workspace admission, binding, ProcessExecutionPolicy and typed
GitExec interpreter. There is no second resolver or execution path.

The plan retains public repository declarations and public grants separately
from effective internal policy/grants so encode/decode stays one repository
entry rather than materializing an operation list. Existing V1/V2/V3 creators,
wire fields and deny behavior remain strict, and retired V4 remains rejected.
The nine selected operations retain explicit declarations and independent
grants for host-owned selections. Under adopted GIT-1, per-call finite
`GitSelectionParameters` can also narrow admitted repository capabilities.
Purpose operations require both `CreateCheckpoint` and `InstallCheckpoint`
grants and their intersected limits. Logical/physical scopes, fixed messages and
count bounds remain enforced; repository-family expansion supplies no selection.

The executable override is optional on the V5 public family. An omitted
override resolves ordinary `git` from the declaration's PATH, otherwise the
host PATH, in ordered absolute nonempty directories at this host assembly
boundary. The resolved real executable and effective PATH enter only generated
definitions. No configured PATH means inherited PATH is explicitly installed
for child execution. An explicit override uses existing executable validation
without fallback; missing Git or unsafe PATH yields an honest configuration
failure. The public optional state remains unchanged for serialization.

GitRepositoryExecutionSpec E1/E2/E4 covers the exact family and real temporary
checkout/bare-remote route through the current UoW, plus authority denial.
RuntimeProcessExecutionPlanCodecSpec E3 covers public round trips, malformed
authority, generated-grant spoofing, safe name bounds and legacy absence.
These producer specifications do not establish installed application acceptance
or authorize rewriting a saved Session or its history.

## Managed execution and uncertainty

Both driver entry points recheck metadata and select binding.workingDirectoryC.
No -C argument, WorkArea projection into the checkout, shell, new main/JVM,
second driver or global serialization protocol is introduced. Normal Git starts
directly through the existing launcher with the fixed no-hooks/no-fsmonitor/
no-credentials/no-auto-maintenance prefix and bounded environment. Normal sbt
continues to use its direct adapter and native locks without a scripted
prerequisite or external global lock.

The shared UoW lifecycle owns WorkArea allocation, handle registration, await,
Job cancellation, finite captures, timeout and cleanup. The typed result keeps
the actual bounded Process receipt while exposing only validated semantic data.
Only complete stdout can produce a successful typed value; CheckPendingIndex
also requires complete stderr. Binary blob bytes
are distinct from optional UTF-8 text. Non-Exited effects remain incomplete;
a failed capture is never interpreted as an empty successful observation.

PendingMerge exit one yields ConflictObservationRequired. It never asserts a
confirmed conflict based on an exit code or stderr. Typed porcelain status and
unmerged index facts feed the primary's saved-precondition and conflict logic.
The primary owns durable intent/results, domain budgets, policy, comparison and
uncertain-effect no-replay decisions; CNCF does not rewrite historical receipts.

CNCF owns the conversion of diff-check diagnostics into
`GitIndexCheck(findingKeys)`. The current GitExec callback validates both actual
captures and uses one text parser for an empty exit-zero clean check or an
exit-two findings observation with empty stderr. Exact nonempty non-plus lines
become opaque set keys, preserving CR, colons and whitespace without filename
parsing; LF alone separates lines, and a final line need not end in LF.
Succeeded with findings retains actual exit two and is never a clean check.
Contradictory, malformed or incomplete captures cannot supply usable facts.

The pure `GitIndexCheck.parseStoredC` compatibility codec uses that same text
parser for ordinary historical Receipt exit/stdout/stderr fields. It neither
executes Git nor establishes authority, capture provenance, completion or bounds.
The consuming application retains its existing complete/bounded Receipt
admission before calling the codec. No saved payload schema changes, generic
execution fallback or additional Request site follow from this API. All live
admission, execution and capture decoding stay inside the current UoW.

The primary retains comparison of local and remote finding sets, validation
policy and saved workflow intent/results. This producer unit does not migrate
the actual primary consumer; the same-parser historical codec enables that
mandatory subsequent migration without moving Git diagnostic parsing back
into the application.

The parser follows official [porcelain v1/status](https://git-scm.com/docs/git-status),
[ls-files stage output](https://git-scm.com/docs/git-ls-files) and
[rev-parse](https://git-scm.com/docs/git-rev-parse) protocols. It preserves NUL
record boundaries, literal LF/backslash filenames, destination/source rename
order, valid type-change codes and exact conflict-stage facts.

## Intermediate delivery

The executable specifications describe the producer, strict compatibility,
current-context security and existing shared lifecycle. Temporary local Git
fixtures initialize preconditions through fixture-only ProcessBuilder and run
subject operations through the typed DSL, using fixed temporary bare remotes.

The primary's complete real consumer/parser migration, old definitions and
launch-route retirement, actual same-workflow Git results and all eight
remaining original main methods remain mandatory parent work. This producer
design claims no Step/Phase closure, all-nine-main completion, publication or
whole-workflow acceptance.
