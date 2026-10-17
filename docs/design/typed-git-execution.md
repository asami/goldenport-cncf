# Typed Git Execution

Status: normative design for the intermediate CNCF Git producer.

## One semantic intent and one lifecycle

The [typed Git specification](../spec/typed-git-execution.md) owns the exact
thirty-operation/parameter/result table and strict wire/parser contracts.

```text
protected git_* wrapper
  -> GitExec(target, typed operation), one External occurrence
  -> current UnitOfWork ExecutionContext and actual Component ancestry
  -> one inherited WorkspaceExecutionAdmission and current binding
  -> independent component per-operation Process capability
  -> closed Git program definition for the identical binding
  -> shared managed direct normal Git process at canonical checkout cwd
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
