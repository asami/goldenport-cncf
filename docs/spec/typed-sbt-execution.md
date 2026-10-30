# Typed SBT Execution

## SBT-JVM-01 — Runtime-owned maximum heap

Every typed Compile, Test, TestOnly and FixCheck admission resolves
`cncf.sbt.jvm.maximum-heap-mib` from the current UoW's ordinary configuration.
The absent default is 4096 MiB. An explicit value is an integral numeric MiB
count of at least 4096, representable as bytes in a Long. For example:

```json
{ "cncf.sbt.jvm.maximum-heap-mib": 8192 }
```

This is a maximum-heap baseline, not initial allocation, a memory reservation,
an execution grant or a promise that OOM cannot occur. Effective Xmx is the
largest of that baseline and explicit heap requirements from the admitted
workspace's `.jvmopts`, `.sbtopts`, selected shared sbtopts and the program's
explicit JAVA_OPTS/SBT_OPTS/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS. Xms requirements
are also considered so the selected maximum cannot be below an explicit initial
heap. Shared sbtopts follows SBT_ETC_FILE, XDG_CONFIG_HOME/HOME and /etc selection.
No ambient caller environment is imported. Plain numeric Xmx/Xms with optional
K/M/G units and sbtopts `--mem`/`-mem` MiB values are recognized; no shell code or
variable expansion is evaluated. Other JVM options remain the launcher's concern.

The runtime prefixes one `-J-Xmx<bytes>` argument and normalizes existing Xmx
tokens in the explicitly bound JVM option variables, because some SBT launchers
append those after command-line arguments. It retains every other option and
does not write config files, add per-check JAVA_OPTS, change the SBT executable,
or install a wrapper. Missing optional files mean no setting; actual read or
numeric resolution failures are configuration failures before dispatch.

The admitted command/selection, capability, workspace, effective limits and
empty request remain unchanged. Only runtime-derived JVM arguments/environment
are adjusted. Reading current config on each new admission allows a later
ordinary invocation to adopt a project change without replacing Session,
history, counters or prior results. Configuration change does not itself retry
an execution or turn prior failure into success. Generic ProcessExec and Git
do not consume this property. `SbtJvmParametersSpec` and the existing SBT DSL
specifications reserve these semantics.

## SBT test output policy

### TEST-OUTPUT-01 — Defaults and ordinary properties

Typed `Test` and `TestOnly` (including exact scenarios) use these independent
ordinary CNCF resolved properties, expressed in bytes:

| Property | Default |
| --- | ---: |
| `cncf.sbt.test.stdout.maximum-bytes` | 67108864 (64 MiB) |
| `cncf.sbt.test.stderr.maximum-bytes` | 8388608 (8 MiB) |

For example, a selected project host configuration may contain:

```json
{
  "cncf.sbt.test.stdout.maximum-bytes": 67108864,
  "cncf.sbt.test.stderr.maximum-bytes": 8388608
}
```

Only an absent property selects its default. Present values must be numeric,
positive, integral byte counts within the signed Long range. Text, null,
Booleans, fractional, zero, negative and overflowing values are structured
configuration errors before driver invocation, without reflecting supplied
values in diagnostics. Resolution uses the current UnitOfWork's ordinary
`ResolvedParameters`, including its existing inheritance/provenance rules;
there is no direct JVM-system-property or ambient-environment fallback.

### TEST-OUTPUT-02 — Explicit host ceilings and operation isolation

For each stream, the effective limit is the minimum of its selected property
(or default) and the already admitted host program's explicit stream ceiling.
A property cannot enlarge runtime grants or host authority. To permit the
defaults, a host's Test/TestOnly `sbtPrograms[].limits` must declare
`stdoutBytes >= 67108864` and `stderrBytes >= 8388608`; an intentionally smaller
explicit host ceiling remains authoritative. The strict host wire contract and
finite required process-limit fields do not change.

Only the two effective capture ceilings are narrowed. Test selection, arguments,
workspace, capabilities, environment, deadlines, other limits and fixed empty
request stay intact. Compile, FixCheck, Git and generic Process execution do not consume
these test properties, including malformed test-only properties. No automatic
retry or execution follows configuration changes.

### TEST-OUTPUT-03 — Complete bounded evidence

Given/When/Then and ordinary test output remain visible. Each stream remains
finite; its existing output-limit termination, truncation and captured-prefix
facts are retained when exceeded. A truncated/incomplete result never becomes
test success. Original failed or unknown Workflow attempts remain unchanged;
their existing public continuation and independent Human rules still apply.

Executable evidence: `SbtTestOutputParametersSpec` E1–E4 and
`SbtExecutionDslSpec` E-OUTPUT1–E-OUTPUT3 exercise defaults, independent inherited
properties, invalid numeric boundaries, host tightening, actual current-context
admission, pre-driver failure and Compile/FixCheck isolation. Existing local-driver output
limit and SBT outcome specifications retain the termination/capture contract.

Status: normative normal-command producer, host and typed consumer connection contract

## Component intent

Behavior exposes protected `sbt_compile(target)`, `sbt_test(target)`,
`sbt_test_only(target)` and `sbt_fix_check(target)`, each
returning `ExecUowM[SbtExecutionResult]`. `SbtTarget(workspaceId, scenario = None)`
contains a `WorkspaceId` and an optional public
`SbtScenarioSelection(suiteClassName, testName)`; `SbtOperation` contains only
`Compile`, `Test`, `TestOnly` and `FixCheck`. The corresponding protected
`sbt_compile_attempt(target)`, `sbt_test_attempt(target)`,
`sbt_test_only_attempt(target)` and `sbt_fix_check_attempt(target)` return
`ExecUowM[SbtExecutionAttempt]`. There is no
task expression, extra argv, main, executable, host cwd,
launcher, environment or capability selector in this component API.

The trailing default preserves ordinary `SbtTarget(workspaceId)` source calls.
Its case-class extractor arity changes from one to two, so extractor consumers
must update. There is no binary-compatibility claim for precompiled SNAPSHOT
callers. Scenario `validateC` reuses the host class grammar below and requires
an exact nonempty already-trimmed control-free test name of at most 512
characters. Double quote, backslash and semicolon are explicitly unsupported
literal rendering. There is no trimming, escaping or fuzzy fallback. Null
options, selections or fields fail structurally; Compile/Test/FixCheck reject every
scenario. TestOnly with `None` preserves existing class-only behavior.

These methods only construct immutable `UnitOfWorkOp.SbtExec(target, operation)`
intent. Attempt methods construct the corresponding immutable
`UnitOfWorkOp.SbtExecAttempt(target, operation)` through the existing attempt
wrapper. Construction performs no context lookup, service or permission check,
workspace resolution, request creation or launch. It can be recorded without
runtime services using typed stub outcomes. Each SbtExec is one External UoW
occurrence describing Build; it is not lowered into a second ProcessExec
occurrence or classified as local work.

`WorkspaceId.parseC` accepts lowercase logical IDs beginning with a letter,
followed by letters, digits or hyphens, up to 64 characters.
`WorkspaceRelativePath.parseC` accepts normalized nonempty relative paths;
absolute/drive paths, backslashes, empty/dot/dot-dot segments, NUL and control
characters are rejected. The relative resolution seam adds no File content
read/write, find or streaming operation.

## Shared workspace and independent grants

Internal `WorkspaceExecutionAdmission.createC(roots, grants)` constructs one
immutable logical-ID-to-`WorkspaceBinding` map and grants by actual
`ComponentId`. Duplicate IDs or component/workspace grants, unknown grant
targets and unavailable/non-directory roots are rejected. Raw `Path` values
remain runtime-internal and are omitted from diagnostics and CAR arguments.
`ScopeContext` inherits the optional service; `ExecutionContext` obtains that
same service through `cncfCore.scope`. New defaulted wiring is appended and
preserves earlier argument positions. There is no component grant installer
or separate SBT root registry. Later File/Git tools must share these bindings.

Actual `Component.Context` ancestry supplies identity. A generic Component
scope kind or owner display label cannot impersonate it. Compile/Test/TestOnly/FixCheck require
workspace `Manage` because builds may write to the checkout; `ReadOnly`
cannot authorize a build. A matching, independently installed actual component
process capability grant is also required. Workspace permission grants no
process, remote or HTTP authority.

Configured root symlinks are canonicalized at admission. Before use, the
configured path must still identify the same admitted canonical directory and
recorded directory identity where the filesystem supplies one. Relative
resolution rejects symbolic-link segments and traversal. This bounded
preflight does not claim race-proof filesystem containment.

## Runtime definition and admission

Internal `SbtProgramDefinition.createC` receives the admitted binding,
operation, capability, safe identity, normal `sbtExecutable`, finite
`ProcessExecutionLimits`, fixed environment and a final defaulted
`testClasses: Vector[String] = Vector.empty`. Compile, Test and FixCheck reject nonempty
classes. TestOnly requires 1..16 distinct exact fully qualified ASCII names,
each 1..256 characters, matching
`[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+`. Distinct packages cannot
select the same simple class name. Whitespace, Unicode, controls, wildcards,
dollar, dash, semicolon, slash, empty and duplicate elements are rejected;
there is no trim, normalization or caller fallback. The total class surface
is bounded by 16*256 ASCII characters. The executable must resolve
from an absolute path to a real available executable regular file. Relative,
missing, directory and non-executable locations fail without reflecting host paths.
The runtime renders this fixed vector with one literal final command:

```text
<trusted normal sbt executable> --batch -Dsbt.server.autostart=false
     -Dsbt.supershell=false -Dsbt.color=false compile|test|fixCheck
```

The finite task and wire mapping is:

| Operation | Wire token | Final task argument |
| --- | --- | --- |
| Compile | `compile` | `compile` |
| Test | `test` | `test` |
| TestOnly | `test-only` | `testOnly <host-admitted classes>` |
| FixCheck | `fix-check` | `fixCheck` |

FixCheck selects exactly one fixed, runtime-owned `fixCheck` task argument with
the same four flags and admission rules. It accepts no class, scenario or
request override. The project's `fixCheck` task is responsible for invoking
Scalafix in check-only mode. CNCF executes this selected task; it cannot audit
arbitrary build-task internals or guarantee that arbitrary project code is
read-only. The consumer owns lint implementation, a nonempty syntactic rule
profile and separate durable check-result storage. No test-suite or test-count
evidence is synthesized from a check result.

TestOnly retains those same four flags and uses one final argv item containing
literal `testOnly ` followed by the trusted names joined with one space.
Class-only intent supplies no classes, tasks, options or additional arguments.
An exact typed scenario may select only one class already admitted by the
current host TestOnly allowlist. After binding/definition and actual component
grant resolution, the runtime derives an immutable narrowed tagged definition
with one final argv item:

```text
testOnly <host-admitted class> -- -t "<literal accepted exact name>"
```

The derived definition retains the identical binding, capability, safe identity,
executable, fixed environment, limits and all allowed projections. Only its
fixed arguments/argument policy and private class/scenario selection metadata
narrow. No request override, new request construction site or host-codec field
is added. The existing policy resolves the same empty request and checks actual
grant and finite limits. Generic tagged execution remains forbidden.

No Java-launcher compatibility route, shell or component main is used. The existing local launcher clears ambient
environment before installing validated fixed bindings. No per-task cache,
repository or permission-symlink workaround is added.

## Strict host V2/V3 and bootstrap

The selected Subsystem host file uses `cncf.runtime-process-execution.v2` with
exact root fields `schema`, `programs`, `grants`, `workspaces`, `workspaceGrants`
and `sbtPrograms`. Generic `programs` and `grants` retain their V1 wire shapes.
Workspace declarations contain exactly `identity` and `root`; workspace grants
contain `componentId`, `workspaceId` and `access` (`read-only` or `manage`).
Each Compile/Test/FixCheck SBT declaration contains exactly `target`, `operation`
(`compile`, `test` or `fix-check`),
`capability`, `identity`, `sbtExecutable`, `limits` and `environment`.
`javaExecutable` and `launcher` are unknown fields, never aliases.
V2 and V3 add the closed operation token `test-only`, which requires exactly
the same seven fields plus a `testClasses` string array. Missing, null, empty
or invalid TestOnly classes fail; Compile/Test/FixCheck reject `testClasses` even when
empty. Encoding writes that field only for TestOnly. V3 retains its existing
`gitPrograms` root field and all existing Git restrictions. Validated vectors
pass unchanged through declarations, factory and private metadata.

`fix-check` is an explicit new member of the closed operation set in the same
V2/V3 schemas; no schema version changes or old-token reinterpretation apply.
FixCheck rejects `scenario`, extra argv and all other unknown host fields
under the existing exact seven-field shape. Encoding emits neither selectors
nor extra task fields for it.

All eleven existing process limit fields are required finite positive integers;
the fixed environment retains the existing strict validation. Duplicate JSON
keys, workspace/capability identities, owner declarations, grants and
target-operation registrations, malformed IDs, unknown references and unknown
fields fail preflight. Each granted SBT capability needs Manage for the same
ComponentId and target. ReadOnly-only declarations may exist without Build
authority; workspace access alone never creates a process grant.

`RuntimeProcessExecutionPlan.createV2C` builds one WorkspaceExecutionAdmission,
then uses its exact binding objects for tagged SBT definitions in the existing
process policy. Generic template restrictions apply only to generic programs.
V2 roundtrip stores generic programs and typed declarations separately, never
reencodes tags as generic programs, and preserves configured root aliases and
host executable paths. Strict V1 decoding/encoding for generic declarations
continues unchanged; typed SBT has no V1 fallback.

The existing selected-file Home/Project/Cwd provenance gate remains authoritative.
CLI arguments and Component configuration cannot install this authority. Runtime
initialization forwards the plan's shared workspace admission beside the existing
process admission and driver into GlobalRuntimeContext/ScopeContext before
Component assembly. Bootstrap checks syntactic ComponentIds; only actual
Component.Context ancestry at execution supplies the eventual owner.

The existing process policy owns the immutable private SBT tag containing the
same binding object and operation. It rejects duplicate workspace/operation
definitions even with different capability IDs. Typed admission requires one
unambiguous definition for the identical selected shared binding, the actual
component capability grant, and existing limit/program validation. Unknown
target/operation, wrong owner, absent services/grants, ReadOnly access and
binding mismatch fail before the driver.

Host assembly validates configuration and definitions. Operation-time admission
occurs only when `UnitOfWorkInterpreter` interprets SbtExec, under its current
UnitOfWork `ExecutionContext`. That context supplies actual Component identity,
Manage permission, independent process capability, the identical shared binding
and finite policy limits. A Behavior construction context grants no retained
authority: a denied current context fails before the driver, and an allowed
current context selects its own definitions and binding even if construction
had no services or used a different root for the same logical workspace ID.

Generic `ProcessExecutionAdmission.admitC`, scoped generic admission and
`ProcessExecutionPolicy.resolveC` reject tagged SBT definitions. The interpreter
also rejects a directly supplied tagged `UnitOfWorkOp.ProcessExec` with a
structured policy failure before process calltree or driver allocation. The
same-package admission helper reads the existing private metadata without
widening its visibility.

Inside `uow:sbt:execute`, semantic admission uses the existing internal empty
`ProcessExecutionRequest` construction site, then the interpreter executes the
admitted definition once through the shared process calltree and managed
process lifecycle and converts the actual result to `SbtExecutionResult`.
There is no outward ProcessExec intent, nested UoW run or nested commit.
Public Process request/resolved/result constructor and extractor shapes, and
ordinary generic capability behavior, remain unchanged.

The owning human expressly approved this CNCF internal Compile/Test request
lowering and its selected regression specs, and subsequently approved the exact
same existing empty-arguments `ProcessExecutionPolicy.resolveSbtC` request site
for TestOnly and its corresponding specs. The accepted
`P65-Q3-typed-sbt-fix-check@1` scope adds FixCheck through that same existing
empty request site. No new request site is introduced.
This chat approval is separate
from runtime grants and platform execution permission. It does not approve
sm-workflow direct Request/ProcessExec use, the six legacy sites, arbitrary
argv/shell, Process-based File/find substitution or future-purpose expansion.

## Cwd, lifecycle and result

Both local-driver `startC` variants use the binding's actual canonical checkout
cwd and recheck the binding before launch. Tagged SBT denies request argv,
cwd, stdin, input files, resource trees, outputs and request limit overrides.
No WorkArea projection is placed under the checkout. Generic executions keep
their existing WorkArea-relative cwd. The UnitOfWork still owns its allocated
WorkArea and handle; bounded concurrent captures, finite launch/execution/
termination limits, Job cancellation, descendant cleanup and closure apply.

`SbtExecutionResult` retains `target`, `operation` and the actual bounded
`processResult`, plus a fourth defaulted
`testSelection: Option[SbtTestSelectionResult] = None`; existing three-argument
source calls remain valid. Compile/Test/FixCheck `outcome` is `Succeeded` for `Exited(0)`,
`Failed(exitCode)` for nonzero exit, and `Incomplete(termination)` for launch
failure, timeout, cancellation, output limit or artifact limit. Captures,
truncation, byte/artifact counts, elapsed duration and safe identity are
preserved. Typed result conversion occurs within interpretation. Admission
failures remain structured `Consequence` failures.

For FixCheck, `Succeeded` means the selected build check actually exited zero;
nonzero is `Failed(exitCode)`, `NotStarted` is `NotExecuted`, and every other
termination remains `Incomplete(termination)`. Its `testSelection` stays absent
even when stdout resembles a test report. No test success is inferred.
The attempt API retains `Observed` for an actual result,
`AdmissionRejected(target, operation, conclusion)` for pre-execution admission
failure, and `Uncertain(target, operation, conclusion)` for transport or driver
uncertainty. It adds no replay, retry or fallback.

For TestOnly `Exited(0)`, success additionally requires
`Verified(testClasses, completedSuites, testsRun)`. An absent selection yields
`UnverifiedTests(MissingSelection)`; `Rejected(reason)` yields
`UnverifiedTests(reason)`. The closed failures are `MissingSelection`,
`IncompleteCapture`, `InvalidReport`, `ZeroTests`, `SuiteMismatch` and
`FailedTests`, plus `ScenarioMismatch`. Nonzero and incomplete terminals retain the existing meanings
without inferring report success. The raw Process result is always retained.
No result serialization is added by this producer.

Pure decoding runs inside the existing SbtExec callback after exactly one
execution and uses only the expected classes from the actual resolved private
metadata, including its defaulted optional scenario. Both captures must be untruncated with nonnegative byte counts equal
to captured bytes and strict UTF8 decoded with REPORT. Stdout `[info]` records
must contain exactly one complete ordinary ScalaTest terminal report: positive
Long `Total number of tests run: N`; `Suites: completed K, aborted 0` with K
equal to selected classes; `Tests: succeeded N, failed 0, canceled 0, ignored I,
pending P` with nonnegative I/P; and `All tests passed.`. Each selected suite
must have exactly one header, using its FQN or its admitted unique simple name.
Unknown or duplicate canonical headers, mismatched counts, missing, duplicate,
contradictory, negative or overflowed summaries cannot verify. Zero tests are
`ZeroTests`; positive failed/canceled/aborted counts are `FailedTests`.
Unrelated log lines are ignored. Unsupported formats remain unverified.
This trusted build-output interpretation is not an integrity certificate or
hostile-code authenticity claim. Decode refusals are typed result data and
retain the external receipt without automatic replay.

With a scenario, its class must agree with the single exact selected class and
the conservative successful summary must have `testsRun == 1`. Class-selection
or parsed test-count disagreement produces `ScenarioMismatch`; malformed,
truncated, failed or wrong-suite reports retain their existing conservative
rejection. `Verified(testClasses, completedSuites, testsRun)` keeps its existing
shape. `SbtExecutionResult.target` retains the exact scenario. A suite-only
receipt does not independently name an operation: the typed producer intent
requests the exact name, and parsing cannot authenticate forged build logs.

The adapter starts the normal tool directly once through the existing managed
lifecycle. SBT may start its legitimate JVM/tool children. Its own native locks
own SBT cache/build coordination: no external global serialization, scripted
owner prerequisite or extra JVM wrapper is installed. Independent distinct
projects may run concurrently; this connection slice does not prove runtime
parallelism.

`ExternalEffectKind` provides ResourceRead, FileRead, FileWrite, GitRead,
GitWrite, Build, Process and Http vocabulary. Typed SBT describes Build on the
selected workspace; a description grants no authority. Build writes are not
database-rollbackable and arbitrary build code gains no new hard OS sandbox.

## Qualification

`WorkspaceExecutionSpec`, `SbtExecutionDslSpec` and
`LocalProcessExecutionDriverSpec` author these behaviors. Typed unit scenarios
use deterministic profiles or controlled launchers and never launch SBT;
existing controlled local-probe coverage remains applicable.
FixCheck coverage in `SbtExecutionDslSpec`, `RuntimeProcessExecutionPlanCodecSpec`,
`LocalProcessExecutionDriverSpec`, `SbtTestSelectionSpec`,
`UnitOfWorkProgramPlanningSpec` and `ExternalEffectUsageSpec` specifies inert
normal/attempt recording, current independent authority, installed binding,
fixed argv and closed wire shapes, selector/override denial, Build effects and
actual controlled zero/nonzero results with absent test selection. Lifecycle
events spell this operation `fix-check`; existing termination and capture facts
remain authoritative. These specifications do not establish consumer delivery
or mutate an existing consumer Session, history, repair count or validation result.
`UnitOfWorkProgramPlanningSpec` and `UnitOfWorkProgramRecordingSpec` specify
single typed External occurrences, order, repeated intents, typed outcomes,
selected branches and failure propagation without real admission or execution.

`RuntimeProcessExecutionPlanCodecSpec` and
`CncfRuntimeProcessExecutionInitializationSpec` specify strict V1/V2, shared
bootstrap, provenance, independent grants and lifecycle. The primary
`ThreeWorkflowSbtOperationSpec` explicitly selected through `runtimeTestOnly`
authors actual normal-tool success/failure, saved intent/result and fresh-runtime
readback without replay. Ordinary testOnly does not prepare runtime artifacts.
Controlled producer vectors use `/usr/bin/true` as an executable placeholder and
never start SBT; they are distinct from actual connection evidence.

The primary consumes `SbtExecutionResult.processResult` in its existing durable
receipt converters. Full same-workflow continuation, all nine original product
main methods and launch routes, and actual-used typed Git/File migration remain
open obligations. This contract claims no SBX01 completion, Phase acceptance,
publication, rollback or hard isolation.

TestOnly producer specifications do not qualify an installed normal-tool
TestOnly connection or primary consumer. Before enabling TestOnly, the primary
must explicitly consume `outcome`, including `UnverifiedTests`, in its durable
receipt and progression logic. Existing primary Test behavior and all historical
delivery status and counts remain unchanged. `SbtTestSelectionSpec` authors the
strict selection/report profile; DSL, codec and recording specs author the
corresponding current-context, conditional wire and External-boundary behavior.

`SbtScenarioExecutionDslSpec` authors the narrower scenario/current-context
path with `ProcessExecutionTestProfile`, without launching SBT. The public
`ValidationOperationBindingV1.bindC` connects actual discovery references to
that same typed `SbtExec`; see the [metadata contract](validation-test-metadata.md).
The parent-selected real top-level parameter-fixture test is separate literal
ScalaTest selection evidence. The actual sm-workflow adapter/fixture remains
separately admitted S4 work; authored producer specifications do not establish
consumer or Phase acceptance.

## Confirmed pre-submission result

`ProcessExecutionTermination.NotStarted` is a terminal producer fact that the
local execution owner confirmed before submitting a launcher task for an admitted
tagged SBT execution. `SbtExecutionResult.outcome` maps only this terminal to
`SbtExecutionOutcome.NotExecuted`. The actual `processResult`, target and operation
remain intact. Exited/TestOnly qualification and all other incomplete outcomes
retain their existing meanings; CNCF performs no automatic retry.

Both local-driver `startC` variants validate the tagged request before reporting
this fact. They may return an already-completed handle when the driver is already
closed before submission, or when direct `Files.isRegularFile` / `Files.isExecutable`
inspection finds that the previously resolved absolute executable is no longer a
regular executable file. Host definition validation and independent grants remain
required. Malformed requests still fail structurally, even when the executable is
unavailable or the driver is closed. A nonfatal filesystem inspection failure is
an ordinary structured failure and cannot establish `NotStarted`.

This path submits no Callable, invokes no launcher and allocates no active process
or active handle. It uses the existing completed-handle result with empty captures,
zero byte counts, no truncation or artifacts, actual finite elapsed time and the
same safe program identity. The handle stays completed after executable restoration;
only a separate fresh start can dispatch. Generic and Git closed-driver behavior
is unchanged.

An IOException from any launcher, ExecutionException, launch timeout, interruption,
closure after submission, cancellation, ambiguous failure or absent receipt does
not establish this fact. Such paths retain `LaunchFailed`, `Incomplete` or their
existing structured failure. The pre-submission observation does not claim
race-proof filesystem containment and creates no alternate command or authority
path, global serialization, wrapper or main.

`LocalProcessExecutionDriverSpec` authors both start variants, executable removal
and permission loss after admission, fresh dispatch after restoration, malformed
request rejection and the launch/closure ambiguity boundary. `SbtExecutionDslSpec`
authors `NotStarted` to `NotExecuted` while retaining one External `SbtExec` and the
actual receipt in the owning UnitOfWork. This producer fact alone does not implement
workflow continuation, consume Human authority, qualify actual installed SBT or
establish Phase acceptance.

## Test compilation and estimated test execution

`sbt_test_compile` and `sbt_test_compile_attempt` are thin typed UoW intents.
An independently declared `test-compile` host program runs normal SBT
`Test / compile`, without executing test bodies or accepting caller argv.
Current workspace/process grants and lifecycle policy are evaluated in the UoW.

`SbtTarget.validationEstimate` optionally carries the selected TestOnly leaf
inventory, source revision, reason, resolved per-leaf estimates/default markers,
once-only preparation costs and multiplier. The executing UoW recomputes
`ceil((multiplier * (sum(leaves) + preparation) - completedPreparation) / 1ms)` and applies that deadline
to the existing driver. It does not install individual leaf timers. Already
completed TestCompile preparation is included once in the aggregate and its
observed consumed duration is subtracted before starting TestOnly. The retained
typed estimate includes that duration; waiting between operations is not process
execution time. A nonpositive remainder refuses without restarting preparation.
Empty, duplicated, nonpositive, overflowing or crossed selections refuse before
process execution. The admitted fixed suite set must match the estimate; an exact
scenario must match its sole leaf. The explicit host execution ceiling remains
authoritative: an estimate above it refuses, rather than silently increasing it.
A complete successful report with a different executed leaf count is unverified
(`InventoryMismatch`), while its actual process exit remains retained.
Cancellation, termination grace, capture limits and uncertain effects retain
their existing meanings. No generic ProcessExecutionRequest route is granted.
