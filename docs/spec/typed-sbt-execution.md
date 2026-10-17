# Typed SBT Execution

Status: normative normal-command producer, host and typed consumer connection contract

## Component intent

Behavior exposes protected `sbt_compile(target)`, `sbt_test(target)` and
`sbt_test_only(target)`, each
returning `ExecUowM[SbtExecutionResult]`. `SbtTarget` contains only a
`WorkspaceId`; `SbtOperation` contains only `Compile`, `Test` and `TestOnly`. There is no
task expression, extra argv, test selector, main, executable, host cwd,
launcher, environment or capability selector in this component API.

These methods only construct immutable `UnitOfWorkOp.SbtExec(target, operation)`
intent. Construction performs no context lookup, service or permission check,
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
scope kind or owner display label cannot impersonate it. Compile/Test/TestOnly require
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
`testClasses: Vector[String] = Vector.empty`. Compile and Test reject nonempty
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
     -Dsbt.supershell=false -Dsbt.color=false compile|test
```

TestOnly retains those same four flags and uses one final argv item containing
literal `testOnly ` followed by the trusted names joined with one space.
Components supply no classes, tasks, options or additional arguments.

No Java-launcher compatibility route, shell or component main is used. The existing local launcher clears ambient
environment before installing validated fixed bindings. No per-task cache,
repository or permission-symlink workaround is added.

## Strict host V2/V3 and bootstrap

The selected Subsystem host file uses `cncf.runtime-process-execution.v2` with
exact root fields `schema`, `programs`, `grants`, `workspaces`, `workspaceGrants`
and `sbtPrograms`. Generic `programs` and `grants` retain their V1 wire shapes.
Workspace declarations contain exactly `identity` and `root`; workspace grants
contain `componentId`, `workspaceId` and `access` (`read-only` or `manage`).
Each Compile/Test SBT declaration contains exactly `target`, `operation` (`compile` or `test`),
`capability`, `identity`, `sbtExecutable`, `limits` and `environment`.
`javaExecutable` and `launcher` are unknown fields, never aliases.
V2 and V3 add the closed operation token `test-only`, which requires exactly
the same seven fields plus a `testClasses` string array. Missing, null, empty
or invalid TestOnly classes fail; Compile/Test reject `testClasses` even when
empty. Encoding writes that field only for TestOnly. V3 retains its existing
`gitPrograms` root field and all existing Git restrictions. Validated vectors
pass unchanged through declarations, factory and private metadata.

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
for TestOnly and its corresponding specs. No new request site is introduced.
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
source calls remain valid. Compile/Test `outcome` is `Succeeded` for `Exited(0)`,
`Failed(exitCode)` for nonzero exit, and `Incomplete(termination)` for launch
failure, timeout, cancellation, output limit or artifact limit. Captures,
truncation, byte/artifact counts, elapsed duration and safe identity are
preserved. Typed result conversion occurs within interpretation. Admission
failures remain structured `Consequence` failures.

For TestOnly `Exited(0)`, success additionally requires
`Verified(testClasses, completedSuites, testsRun)`. An absent selection yields
`UnverifiedTests(MissingSelection)`; `Rejected(reason)` yields
`UnverifiedTests(reason)`. The closed failures are `MissingSelection`,
`IncompleteCapture`, `InvalidReport`, `ZeroTests`, `SuiteMismatch` and
`FailedTests`. Nonzero and incomplete terminals retain the existing meanings
without inferring report success. The raw Process result is always retained.
No result serialization is added by this producer.

Pure decoding runs inside the existing SbtExec callback after exactly one
execution and uses only the expected classes from the actual resolved private
metadata. Both captures must be untruncated with nonnegative byte counts equal
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
