# Typed SBT Execution

Status: normative design for the normal sbt producer/host/consumer connection

## Responsibility boundary

```text
Behavior: sbt_compile(SbtTarget(workspaceId)) / sbt_test(...) / sbt_test_only(...)
  -> immutable UnitOfWorkOp.SbtExec(target, operation)
  -> UnitOfWorkInterpreter: current UnitOfWork ExecutionContext
  -> inherited WorkspaceExecutionAdmission
  -> Manage binding for actual Component.Context
  -> existing ProcessExecutionAdmission: component capability grant
  -> existing ProcessExecutionPolicy: tagged Compile/Test/TestOnly definition
  -> admitted execution inside the SbtExec interpreter callback
  -> shared process calltree, WorkArea, handle and cancellation ownership
  -> LocalProcessExecutionDriver: admitted canonical checkout cwd
  -> existing LocalProcessLauncher: normal sbt executable and fixed operation vector
  -> pure selected-report decoder inside the same SbtExec callback
  -> SbtExecutionResult: target, operation, actual bounded Process result, optional testSelection
```

CNCF owns target resolution, cwd, launch and SBT terminal meaning. Consumers
retain domain intent, durable receipt and progression. There is no dedicated
SBT executor, component-owned main, shell, application root registry
or component grant installer.

Typed intent enters UoW before admission so program construction and recording
need no runtime services and retain no authority from the Behavior context.
The interpreter's current context owns actual Component identity, Manage,
independent process capability, exact shared binding and finite policy checks.
It selects current definitions even when construction used another root with
the same logical ID. Host configuration validation remains assembly-time work;
it does not replace operation-time admission.

## Shared binding and identity

WorkspaceId and WorkspaceRelativePath are opaque public value types.
Runtime-only WorkspaceBinding retains configured/canonical roots and the
filesystem directory identity when available. Trusted construction accepts
a configured root symlink; use rechecks it against the admitted canonical
directory. Relative resolution rejects symlink segments. Raw paths never
enter CAR data or failure displays. These are bounded preflight checks, not
race-proof containment.

The one immutable service holds coarse ReadOnly/Manage grants by actual
component identity. Scope inheritance and ExecutionContext share it unchanged.
SBT needs Manage plus an independent actual component process capability
grant. Future File/Git adapters must use this same service. This foundation
implements no File/find or streaming operation.

## Protected intent and tagged policy

Protected Behavior methods expose a logical target, optional exact scenario
coordinates and Compile/Test/TestOnly. Scenario coordinates are typed intent;
the current host retains the allowed class population and execution authority.
The trusted definition factory validates a real absolute executable normal sbt
path, finite limits and fixed environment, and renders `--batch`,
server/supershell/color disabling flags and one literal compile/test command.
There is no Java/launcher overload or compatibility fallback. Existing program
validation constructs the definition with private immutable semantic metadata;
public generic factory signatures remain unchanged.

Trusted runtime TestOnly definitions own 1..16 distinct exact ASCII FQNs of
1..256 characters matching `[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+`,
with unique simple names across packages. No whitespace, Unicode, wildcards,
dollar, dash, semicolon, slash, controls, duplicates, empty elements or
normalization are admitted. The private metadata/declaration/factory append a
defaulted `testClasses` vector; Compile/Test require it empty. The existing four
flags remain exact. The final single argv item is `testOnly ` plus the admitted
classes joined with one space for ordinary targets. A target with
`Some(SbtScenarioSelection(suiteClassName, testName))` narrows only an exact
class already in that host allowlist and requests one literal name. The class
grammar is reused unchanged; the test name is exact trimmed nonempty,
control-free, at most 512 characters, and excludes double quote, backslash and
semicolon. There is no escaping or fuzzy interpretation. Compile/Test reject
scenario selection. Explicit null options/selections/fields fail structurally.

Actual admission resolves the identical shared binding, current host TestOnly
definition and actual owner capability grant before deriving a narrower
immutable definition in `ProcessProgramDefinition`. Binding, capability, safe
identity, executable, fixed environment, limits and allowed projections are
retained exactly; only fixed arguments/argument policy and private selection
metadata narrow. The final single command item is
`testOnly <class> -- -t "<exact name>"`. The existing `resolveSbtC` constructs the
same empty request and validates grant/finite limits. No request site, caller
override or host-codec field is introduced. Generic tagged denial still applies.

The trailing defaulted `SbtTarget.scenario` preserves ordinary source
construction. The case-class extractor changes from one field to two. There is
no precompiled SNAPSHOT binary-compatibility claim. Existing host declarations
and codec remain class-only, while the defaulted private metadata/decoder
scenario field preserves ordinary class-only behavior.

The process policy remains the sole program registry and rejects duplicate
workspace/operation pairs. Typed admission selects an unambiguous tagged
definition whose binding is the identical shared object and requires a
separate actual component capability grant. Generic request resolution rejects
every tagged SBT definition. Direct generic ProcessExec interpretation also
rejects tagged executions before driver allocation through the same-package
admission helper; private metadata visibility is unchanged. The typed SbtExec
case alone admits and executes this intent. Both admission routes reuse one
private policy-validation implementation. Generic capabilities retain current
behavior pending SBX04.

Internal request construction is confined to explicitly approved CNCF
Compile/Test lowering and selected regression specs, plus the owning human's
subsequent exact approval for TestOnly at the same existing empty-arguments
`ProcessExecutionPolicy.resolveSbtC` request site and corresponding specs. Owning-human chat
approval is separate from runtime grants/platform permission and approves no
consumer direct Process use, legacy-six-site use or future-purpose expansion.
The existing approved internal request construction site is unchanged. Common
process execution is an interpreter implementation detail, with no pre-admitted
external-CAR ProcessExec fallback or new request purpose.

## Lifecycle and effects

### JVM memory ownership

SbtExecutionAdmission applies SbtJvmParameters after target/scenario/installed
runtime binding and before driver execution. sm-workflow selects the logical
SBT intent; this shared CNCF producer resolves the ordinary
`cncf.sbt.jvm.maximum-heap-mib` property (default 4096 MiB), project JVM options
and explicitly bound launcher configuration. The resulting immutable definition
preserves the admitted capability, target, command and lifecycle while carrying
the effective heap into the existing local driver. The program registry is not
mutated. Config parsing is runtime preparation, not a shell subprocess or a
skill-owned lifecycle. There is no change to workflow persistence or receipts.

Manual shell SBT is outside this workflow route and uses standard SBT config.
It is not redirected through an additional global launcher. Source delivery of
this producer is distinct from adoption by an already installed runtime.

Semantic SBT changes cwd selection only. Both driver entry points revalidate
the shared binding and pass the actual checkout directory to the existing
launcher. Tagged SBT rejects caller argv/cwd/stdin, input files, trees, outputs
and limit overrides. Generic WorkArea behavior is retained, and no mixed
WorkArea projection is materialized into the checkout.

Within `uow:sbt:execute`, the interpreter admits SbtExec and invokes the existing
shared process calltree and execution implementation exactly once. It converts
the actual result to SbtExecutionResult inside that interpreted callback. No
nested UnitOfWork, run or commit is introduced. The interpreter still owns
WorkArea allocation, handle registration, Job
cancellation and UnitOfWork resource reclamation. The driver retains bounded
concurrent capture, finite timeout/termination, descendant cleanup and closure.
There is no detached execution or workspace lock protocol. Checkout writes
are not database-rollbackable and build code gains no new hard OS isolation.

ExternalEffect describes Build on the logical workspace without granting it.
Compile/Test results map exit zero to Succeeded, nonzero to Failed and other terminals
to Incomplete while preserving the actual Process result. TestOnly zero exit
requires `SbtTestSelectionResult.Verified(testClasses, completedSuites, testsRun)`;
absent or Rejected selections yield `UnverifiedTests(reason)`. The public
fourth `testSelection` result field defaults to None for three-argument source
compatibility; no result serialization is added. Admission failures
remain failures, with no empty success or synthetic result fallback.
Planning and recording retain one semantic External SbtExec occurrence for
each Compile/Test/TestOnly, including equal repeated operations. Build effects stay
outside local atomic segments; no second generic ProcessExec is recorded.

## One host plan and inherited authority

The strict `cncf.runtime-process-execution.v2` file keeps generic programs/grants
separate from workspace/workspace-grant/SBT declarations. SBT declares a logical
target, Compile/Test, capability, safe identity, normal `sbtExecutable`, limits
and environment. Unknown fields (including old Java/launcher fields), duplicate
keys/IDs/owners/grants/target-operation registrations and inconsistent authority
are refused. V1 remains the strict generic-only contract.

Both V2 and V3 preserve seven-field Compile/Test declarations exactly, rejecting
`testClasses` even when empty. Token `test-only` requires exactly eight fields,
adding the bounded nonempty `testClasses` string array. Encoder and decoder
preserve that vector through private metadata without duplicating semantic
definitions in generic programs. Existing V3 Git declarations, duplicate-key,
target/operation/capability checks and same-owner Manage plus independent
process capability restrictions remain authoritative.

## Conservative selected-report interpretation

After the single actual execution, SbtExecutionAdmission decodes only TestOnly
Exited(0), using the current resolved definition's expected classes. Both bounded
captures must be complete, byte-accurate and strict UTF8 (REPORT). The selected
ordinary ScalaTest stdout info profile requires one terminal report with positive
Long total N, completed suite count equal to selected classes, zero aborted,
succeeded N, zero failed/canceled, nonnegative ignored/pending and `All tests
passed.`. Exactly one FQN or unique simple-name header must map to each admitted
class. Unrelated log lines have no meaning. Missing, malformed, duplicate,
contradictory, negative, overflowed or mismatched reports remain unverified;
zero N yields ZeroTests, positive failed/canceled/aborted yields FailedTests.
Unknown or duplicate canonical suite headers yield SuiteMismatch. Unsupported
formats are never guessed successful. Closed failures also include
MissingSelection, IncompleteCapture and InvalidReport.

An exact scenario must agree with the single selected class and the ordinary
successful summary must report exactly one test. Parsed class-selection/count
disagreement yields the added `ScenarioMismatch`; existing malformed capture,
wrong suite and failed-report refusals remain. `Verified` retains its existing
three fields. The result's exact target retains operation selection intent;
the suite-only report does not independently identify the operation, and a
forged log is not authenticated by parsing. Binding/declarations contain no
runtime success evidence.

This pure result interpretation preserves every raw Process fact and returns
typed failure data without dropping the external receipt or retrying. Nonzero,
launch-failed, timed-out, cancelled and output/artifact-limit terminals keep
their actual meanings regardless of report text. The profile interprets trusted
build output; it provides no integrity or hostile-code authenticity guarantee.

One immutable WorkspaceExecutionAdmission is constructed before typed program
tagging, so the policy retains the identical binding inherited by real owning
Components. Generic template restrictions apply to generic declarations only.
Every granted SBT capability requires same-owner/same-target Manage, while
workspace grants never create process authority. ReadOnly-only declarations
remain valid for future consumers. Actual Component ancestry is resolved at
execution; bootstrap cannot know future Component factories.

Selected Subsystem Home/Project/Cwd file provenance stays in the existing
preflight. The lifecycle appends the optional workspace service to existing
GlobalRuntimeContext/ScopeContext wiring before assembly, alongside the same
driver and process admission. CLI/Component sources gain no installer. V2
serialization retains configured aliases and typed declarations separately;
it does not duplicate tagged programs in the generic array.

## Connected delivery and remaining proof

The primary now composes protected typed Test through its ProcedureActionCall
and UoW and supplies the actual Process result to existing durable receipt
conversion. Selected normal connection scenarios save intent before effect,
store actual typed/raw outcome and reopen the runtime for readback without
replay. They use the existing managed host_evidence collection and owning
SpecificationSetup Operation, with no retained closed core or concurrent
mutable callback reuse. Whole-workflow semantic cases remain unfinished until
the actual-used Git/File consumers migrate.

Typed controlled scenarios never invoke SBT. Actual-tool scenarios use an
absolute configured normal distribution executable and standard JAVA_HOME,
HOME/PATH/caches. `runtimeTestOnly` explicitly prepares official runtime/current
CAR artifacts; ordinary testOnly has no such dependency. SBT owns its native
locks; no scripted prerequisite, external global lock or extra JVM wrapper is
introduced. Independent projects may run concurrently, without a parallelism
claim from these scenarios. All-nine-main/launch-route retirement, actual-used
Git migration and same-workflow continuation remain required for Phase closure;
no SBX01 closure, Phase acceptance or publication is claimed here.

TestOnly is a producer prerequisite only at this delivery point. Its controlled
selection/DSL/codec/recording specifications do not establish an installed
normal-tool TestOnly connection. A later primary change must explicitly consume
the typed `outcome`, including UnverifiedTests, before enabling TestOnly in
receipt/progression logic. Existing primary Test consumption, historical
delivery status and counts remain unchanged.

The public `ValidationOperationBindingV1` binds actual discovery/query or
supplemental references to this same typed path without installing authority.
`SbtScenarioExecutionDslSpec` uses a deterministic existing process profile to
author actual-context narrowing and single dispatch. Real top-level literal
parameter selection is a separate parent validation obligation. The actual
sm-workflow source adapter/fixture requires separate S4 admission; the
[consumer handoff](../notes/validation-test-metadata-consumer-handoff.md)
records that boundary and remaining Phase acceptance gates.
