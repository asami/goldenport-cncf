# Declared validation check execution

Task: SYNC-20261103-03-COMPLETION-REPAIR, CNCF item 4.

This contract defines CNCF-owned execution of host-declared validation checks.
The model, v6 host schema, thin DSL and UnitOfWork interpreter wiring are authored
together. Compilation, executable specifications and installed consumer integration
require parent-owned execution and acceptance; authored code is not evidence that
those checks passed.

## Logical intent and ownership

`ValidationCheckId` is an exact, case-sensitive, nonempty logical identity. It
rejects controls and leading or trailing whitespace and has no additional length
ceiling. `ValidationCheckTarget(workspaceId, checkIdentity)` carries only logical
identities. `ValidationCheckOperation.Run` is the sole operation. Targets expose
no executable, arguments, environment or directory selection.

`ValidationCheckPurpose` expresses the host-owned, tool-independent meaning of a
declared check: Compilation (`compilation`), Behavior (`behavior`) or Lint (`lint`).
It is required in the host declaration and immutable program metadata. The caller
target and Run operation carry no purpose selection or override. Both direct and
attempt results obtain their required purpose from the admitted definition.

Declared purpose alone proves neither validation success nor source coverage.
Workflow admission still requires a selected, meaningful actual check with a
complete successful result, plus relevant independent workflow coverage and review.
The sm consumer uses the actual host-bound purpose instead of distinguishing
languages or claiming that a non-SBT check produced SBT facts or framework counts.

`validation_check(target)` and `validation_check_attempt(target)` each lift one
External UnitOfWork operation. Construction and recording perform no admission,
filesystem inspection, process allocation or driver call. The interpreter resolves
authority from its current execution context and actual Component.Context ancestry.
It requires the current component's Manage grant for the exact workspace and its
independent process capability grant for the exact declared check. The process
definition and workspace service share the same admitted binding object. Another
binding with the same logical workspace ID does not substitute for that object.

Unknown identities, absent configuration and missing grants fail admission. There
is no fallback adapter, executable or fabricated successful result. Generic process
admission and generic ProcessExec reject validation check tags. The effect is
`ExternalEffect(Build, target.workspaceId)` and the immutable host tag classifies
as Build/Managed.

## V6 host declaration

Schema `cncf.runtime-process-execution.v6` retains every v5 root array and requires
the additional `validationChecks` array. V1, V2, V3 and V5 wire forms retain their
existing encoding and decoding behavior. Every validation check entry has exactly
these keys:

| Key | Host-owned value |
| --- | --- |
| target | Exact declared WorkspaceId |
| checkIdentity | Exact ValidationCheckId |
| purpose | Required exact `compilation`, `behavior` or `lint` |
| adapter | Required string `fixed-process` |
| capability | Declared ProcessCapabilityId |
| identity | Safe program identity |
| executable | Available absolute real executable |
| fixedArguments | Fixed ordered argument vector |
| limits | Existing finite positive CNCF process limits |
| environment | Fixed host environment map |

Extra, missing and explicitly null fields fail configuration. Unknown adapters
and unknown purpose values or aliases fail configuration. This purpose requirement
is part of the unpublished initial v6 contract; it introduces no v7 schema or
historical record migration. Workspace/check pairs, capabilities and safe program identities
must be unambiguous across generic, SBT, Git, repository-expanded and check families.
A component check capability grant requires that same owner's Manage grant.

The fixed-process adapter refuses shell launcher names sh, bash, zsh, dash, ksh,
csh, tcsh, fish, cmd, powershell and pwsh, and generic env, xargs, sudo and doas
launchers, including resolved executable names. Ordinary managed interpreter tools
remain eligible under fixed host configuration. Languages and adapters belong to
CNCF; callers do not choose them. The existing local driver clears ambient process
environment values and installs the declared environment.

Common I/O defaults apply to check declarations as to existing families. Omitted
stdinBytes, stdoutBytes and stderrBytes retain their omission through unresolved
round trips; activation supplies the common value only for omitted fields. Explicit
finite overrides remain exact.

## Admission, execution and results

Only CNCF's internal declared-check policy resolution constructs an empty-arguments
ProcessExecutionRequest for this purpose. Tagged requests require empty arguments,
workingDirectory, outputs, inputFiles and resourceTrees, Empty input and empty
requested limits. Extra request state cannot override the fixed definition.

The interpreter uses normal calltree entries `uow:validation-check:execute` and
`uow:validation-check:attempt`, then the existing process execution calltree and
UnitOfWork lifecycle. The local driver resolves cwd from the admitted binding and
uses its ordinary launch, wait, capture, timeout, cancellation and close machinery.
Existing Git and SBT behavior, including SBT prelaunch handling, is preserved.

The direct operation propagates actual admission or driver failure. The attempt
operation distinguishes:

- AdmissionRejected: a real admission failure, with target, operation and conclusion;
  no driver execution occurs.
- Observed: the actual ProcessExecutionResult, wrapped with target, operation and
  the admitted host purpose.
- Uncertain: an admitted execution returned a driver or lifecycle failure. It does
  not assert NotExecuted and is never automatically replayed.

Every process result field is retained, including captures, artifacts, elapsed time,
safeProgramIdentity and termination. Outcome derives only from that receipt:

| Actual receipt | Outcome |
| --- | --- |
| NotStarted | NotExecuted |
| Exited with a nonzero code, including a negative code | Failed(code) |
| Exited(0), both captures complete | Succeeded |
| Exited(0), either capture incomplete | IncompleteOutput |
| Any remaining termination | Incomplete(termination) |

A complete capture is nonnull, has nonnull content, has byteCount equal to content
size and is not truncated. No second arbitrary size check is imposed on receipts.
Previous attempts, counters, summaries and hypothetical lack of effects never
substitute for the actual process result.

## Executable specification coverage

`ValidationCheckExecutionSpec` authors identity, outcome, capture, adapter, binding,
purpose, generic-admission and request-override examples. `ValidationCheckExecutionDslSpec`
authors construction/recording, current-context admission, receipt preservation,
exact admitted-purpose propagation in both interpreter branches, Uncertain failures
and generic ProcessExec refusal. Its real local-driver examples
use fixed grep against value.txt in a tiny admitted directory, sleep for actual
timeout and continuous-output yes for actual output-budget termination. The actual grep predicate
has Behavior purpose; it claims no compilation result or framework test count.
Fixtures own cleanup.

`RuntimeProcessExecutionPlanCodecSpec` adds v6 strict-field, adapter, exact-purpose
round trips and missing/null/unknown-purpose rejection, collision,
same-owner Manage, round-trip and common I/O omission/override examples while
retaining historical schema examples and inputs. The parent owns compilation,
selected spec execution, independent review, publication and consumer integration.
