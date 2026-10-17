# Process Execution Runtime

Status: normative static contract

## Scope

This specification fixes the component-visible Process Execution contract. It
defines request admission, runtime-owned program definitions, WorkArea and
artifact rules, terminal outcomes, cancellation, and confidentiality. It does
not define a live provider, a concrete external executable, or a generic shell
API.

## Request Contract

A `ProcessExecutionRequest` contains only logical execution intent:

- a required `ProcessCapabilityId`;
- an argument vector;
- bounded input supplied as no input, immutable bytes, or a WorkArea-relative
  file;
- bounded runtime-materialized input files with logical names and
  WorkArea-relative paths;
- an optional WorkArea-relative working directory;
- declared output artifacts; and
- an optional request-level limit tightening.

A request MUST NOT contain an executable path, shell command text, interpreter
expression, host working-directory root, arbitrary environment inheritance, or
an unconstrained environment map. The runtime rejects a request that cannot be
resolved under its selected capability before it reaches a driver.

Arguments are values in a vector. They are not concatenated into shell text.
The resolved program definition validates fixed prefixes, flags, option values,
path arguments, mutually exclusive options, argument count, and encoded size
as applicable to that capability.

The argument policy fixed prefix MUST be element-for-element identical to the
runtime-owned fixed arguments. Program-definition construction rejects a
mismatch without reflecting either argument vector in diagnostics. The default
argument admission accepts only registered values or vectors. An explicitly
declared bounded-text admission may accept dynamic non-empty text arguments,
but finite argument-count and encoded-size limits remain mandatory and unsafe
control characters are rejected before driver selection.

Trusted runtime definitions may instead explicitly select Template admission
for complete ordered literal/lower-hex argument forms. The additive contract
and paired executable examples are in [Process argument templates](process-argument-templates.md).
Template matching does not replace native workflow authority or capability and
resource-limit admission, and registered values are never its fallback.

## Program Definition And Effective Policy

`ProcessProgramDefinition` is runtime configuration or framework registration,
not CAR request data. It binds a capability to a driver selector, safe program
identity, resolved executable location, fixed arguments, argument validation,
environment policy, WorkArea policy, output policy, maximum limits, and trust
or sandbox profile.

Program-definition construction is runtime-internal. Component and provider
behavior submits a `ProcessExecutionRequest` through protected
`process_exec(request)`. `ProcessExecutionAdmission`, resolved from the active
`ScopeContext`, MUST apply the runtime-installed program policy and grant before
the DSL creates the capability-admitted resolved execution. A missing admission
service MUST fail deterministically. `UnitOfWorkOp.ProcessExec` has that
resolved intent as its sole payload.

Runtime-owned semantic tools may use tagged definitions through their protected
typed DSL. [Typed SBT execution](typed-sbt-execution.md) admits only Compile/Test
on the shared Manage workspace and an independent actual component capability
grant. Generic `admitC` and `resolveC` reject those definitions. Public request,
resolved execution and terminal result shapes remain unchanged.

The effective limit is the strictest compatible value among the runtime maximum,
program-definition maximum, component/provider grant, and request override. A
request may tighten a limit but MUST NOT widen any granted limit. Every
execution has finite limits for launch/total duration, termination interval,
stdin, stdout, stderr, argument count/size, artifact count/size, and total
WorkArea size.

Environment values are runtime-built. A definition may classify names as fixed,
explicitly inherited, confidential runtime-resolved, caller-overridable with a
validator, or forbidden. A request can set only explicitly overridable names.
Normal diagnostics may record a safe name category but MUST NOT record an
environment value.

The initial runtime implementation supports fixed bindings only. A fixed
binding is validated when the runtime constructs the program definition, held
outside the component request model, and installed only after the local driver
clears ambient process environment state. Explicit inheritance, confidential
resolution, and caller overrides remain deferred until their runtime sources
and redaction rules are separately specified.

## Admission Contract

Before a driver starts, CNCF MUST verify all of the following:

1. the invoking subject is authorized for the operation;
2. the component or provider has an execution grant for the requested named
   capability; and
3. the request conforms to the resolved program definition and effective
   policy.

An unknown capability, missing definition, absent grant, malformed argument,
forbidden environment request, incompatible limit, unavailable executable, or
unsupported driver policy returns a structured `Consequence.Failure` and MUST
NOT invoke the driver.

## Ordinary External Effect Admission

`ExternalEffectKind` and the workspace-bound `ExternalEffect` remain the shared
effect vocabulary. `Persistence` additionally describes recognized database CLI
effects. `ExternalEffectClassification(effectKinds, route)` describes effects
and provenance; classification never installs a permission or establishes a
safety PASS. Its closed routes and structured diagnostic policies are:

| Route | Diagnostic policy | Meaning |
| --- | --- | --- |
| Managed | `external.effect.managed` | CNCF-owned typed adapter provenance |
| Direct | `external.effect.direct` | Recognized shell, interpreter or wrapper |
| CapabilityLaundering | `external.effect.capability-laundering` | Recognized command access to another managed effect |
| Unclassified | `external.effect.unclassified` | Command-name inference provides no effect assurance |

The public pure `ProcessExternalEffectPolicy.classifyCommandName` normalizes
the basename across `/` and backslash separators, folds case with `Locale.ROOT`,
and removes an optional `.exe` suffix. It recognizes only the bounded names
below; it does not inspect or execute files, resolve symlinks, parse command
strings or argv, or infer arbitrary binary or script behavior.

| Names | Effect kinds | Route |
| --- | --- | --- |
| sh, bash, zsh, dash, ksh, csh, tcsh, fish, cmd, powershell, pwsh | Process | Direct |
| java, javaw, python, python2, python3, node, nodejs, ruby, perl, php, env, busybox, xargs, sudo, doas, command, exec | Process | Direct |
| python followed only by numeric dot-separated version components, such as python3.12 | Process | Direct |
| cat, head, tail, find, ls | FileRead | CapabilityLaundering |
| rm, mkdir, rmdir, touch, tee | FileWrite | CapabilityLaundering |
| cp, mv | FileRead, FileWrite | CapabilityLaundering |
| curl, wget | Http | CapabilityLaundering |
| psql, mysql, sqlite3, redis-cli | Persistence | CapabilityLaundering |
| untagged git | GitRead, GitWrite | CapabilityLaundering |
| untagged sbt, sbt.cmd, sbt.bat | Build | CapabilityLaundering |
| Other names | Process | Unclassified |

`classifyDefinition` uses CNCF-owned private SBT/Git tags for Managed Build or
Managed GitRead/GitWrite according to the typed Git operation's ReadOnly/Manage
workspace permission. A safe identity, a caller assertion, or the executable's
name cannot supply that provenance. The protected typed Git/SBT routes retain
their independent actual component, shared workspace, remote and finite-limit
admission. Their normal tool launchers and legitimate child processes are not
generic command opt-ins.

`validateGenericDefinitionC` refuses Managed definitions on the generic route
and refuses Direct and CapabilityLaundering definitions even when the host has
registered that exact program and declared a component capability grant.
`RuntimeProcessExecutionPlan.createC` applies this gate before policy and
admission installation; V2 and V3 reuse it for their generic declarations before
combining separate typed tools. Generic `UnitOfWorkOp.ProcessExec` applies the
same definition gate before driver resolution or WorkArea allocation, after
the existing SBT-then-Git typed-intent checks and their structured failures.
This does not redesign generic intent or re-resolve current component grants.

Unclassified leaves a definition eligible only for existing exact host program
registration and separate actual component capability admission, bounded
arguments, finite limits, environment policy and applicable declared resource
scopes. A request is not a grant. CAR metadata, a provider result, a safe
identity and a command name grant nothing. Unclassified is an explicit inference
limitation, and arbitrary registered programs may have effects beyond this
bounded vocabulary.

The owning UnitOfWork interpreter always refuses ordinary
`UnitOfWorkOp.ShellCommandExec` with policy `external.effect.ordinary-shell`,
including harmless argv and fake executors. Free program construction remains
passive; direct and Free interpretation refuse before obtaining or invoking
`shellCommandExecutor`. There is no ordinary shell opt-in. Refusals remain
inside the existing UoW/calltree failure path. These effect policy failures use
`Consequence.argumentPolicyViolation` with only fixed logical kind/route values;
they never echo executable paths, command text, argv, environment or provider
secrets.

This boundary does not provide hostile-CAR containment. HTTP destination
admission for the normal client DSL, loading/scanner breadth, and the actual
later CBD consumer of the public classifier remain separate unfinished work.
This slice does not complete P6-SBX04 or P6-SBX05 or their overall same-workflow
command and outbound-destination acceptance obligations.

Executable specifications:
[ProcessExternalEffectPolicySpec](../../src/test/scala/org/goldenport/cncf/processexecution/ProcessExternalEffectPolicySpec.scala),
[UnitOfWorkExternalEffectSpec](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkExternalEffectSpec.scala),
and [RuntimeProcessExecutionPlanCodecSpec](../../src/test/scala/org/goldenport/cncf/processexecution/RuntimeProcessExecutionPlanCodecSpec.scala).

## Driver Contract

`ProcessExecutionDriver` receives only a resolved and admitted execution. It
launches a program as an argument vector without shell interpolation. It MUST:

- start stdout and stderr draining concurrently;
- bound each captured stream independently;
- support idempotent cancellation;
- cooperate with timeout and execution cancellation;
- terminate the child and descendants according to the effective policy; and
- return only after stream readers and declared artifact collection are stable.

The driver is resolved from `ScopeContext`. A scope can inherit the runtime
driver or explicitly override it. An absent driver is a deterministic service
failure. A component does not obtain a driver, process handle, or host process
API directly.

The driver exposes only a safe logical identity and receives an already
resolved execution. `startC` returns a handle with `awaitC` and idempotent
`cancelC`; normal diagnostics may use the driver identity but MUST NOT infer a
host executable or provider account from it. `ProcessExecutionTestProfile` is
an explicit deterministic test fixture and MUST NOT create a host process.

## WorkArea And Artifact Contract

For ordinary generic executions, input files, working directories, and declared
outputs are confined to the execution WorkArea. Relative paths MUST be non-empty normalized relative paths
without traversal segments. Runtime path validation MUST prevent real-path and
symlink escape before launch or artifact collection.

The runtime-tagged Compile/Test tool uses the actual canonical checkout cwd
from its shared immutable workspace binding. Both local-driver entry points
recheck that binding. It admits no caller argv, cwd, stdin, input files,
resource trees, artifacts or request limit overrides, and projects no WorkArea
files under the checkout. The existing UnitOfWork WorkArea/handle ownership,
bounded captures, cancellation and closure still apply. This is a semantic
cwd exception, not an unrestricted host working-directory request.

A runtime-materialized input file is admitted by logical name, bounded before
write, materialized by CNCF before launch, and removed with the WorkArea. It is
not an unrestricted provider-controlled host file.

An admitted resource tree is represented by
`ProcessExecutionResourceTreeInput`. It is created from an opaque,
runtime-admitted `ResourceTreeSnapshot`, a validated WorkArea-relative target,
and limits no broader than the snapshot limits. Program definition and grant
policy MUST approve the tree identity and may narrow its limits before
materialization. Components MUST NOT substitute a host path, forge a snapshot,
or materialize the tree outside the UnitOfWork-owned WorkArea.

A materialized input path MUST NOT equal the effective WorkArea path of a
declared output. Admission rejects the collision before input materialization,
so caller-supplied bytes cannot be projected as process output without a
program write.

A declared output path is interpreted relative to the selected execution
working directory. When no working directory is declared, it is relative to the
WorkArea root.

Only predeclared outputs are returned. An artifact has a logical identity,
declared kind, and bounded content metadata; it is not an unrestricted host
path. The runtime enforces per-artifact, aggregate byte, and file-count limits.
It performs cleanup after result materialization for success, failure, timeout,
and cancellation.

## Terminal Result Contract

The result reports one terminal lifecycle outcome:

- `Exited(exitCode)`;
- `LaunchFailed`;
- `TimedOut`;
- `Cancelled`;
- `OutputLimitExceeded(stream)`; or
- `ArtifactLimitExceeded`.

It additionally reports bounded stdout/stderr captures, declared artifacts,
byte and artifact counts, elapsed duration, truncation indicators, and safe
program identity. It MUST NOT expose a raw executable path, full environment,
credential, account identity, unrestricted host path, or undeclared file.

`Exited(nonZero)` is a terminal process result rather than an automatic CNCF
failure. A provider adapter or domain operation decides how to interpret it.
Admission and runtime failures remain `Consequence.Failure(Conclusion)` values.
Provider adapters consume the neutral `ProcessExecutionResult` and map its
termination to provider/domain outcomes. CNCF does not supply a Cozy-,
CAR Review-, or application-specific adapter vocabulary.

The CNCF-owned typed SBT adapter retains this actual result and maps build
meaning to Succeeded, Failed(exitCode) or Incomplete(termination), as specified
in [Typed SBT execution](typed-sbt-execution.md). The reusable ExternalEffect
vocabulary describes Build on a logical workspace without granting authority
or implying rollback or hard OS containment.

## Cancellation And Job Contract

The interpreter registers an active process handle with the active execution
cancellation scope immediately after launch and unregisters it on every
terminal path. Job cancellation signals the active handle and must lead to
process-tree termination according to the effective termination policy.

The interpreter MUST also register the handle and its WorkArea with the owning
UnitOfWork resource lifecycle. UnitOfWork commit, abort, rollback, or explicit
dispose before terminal completion MUST cancel, await, and reclaim the process
resource. A completed process MUST unregister before UnitOfWork completion and
MUST NOT be cancelled again.

The executable lifecycle rules are:

- R1: Active Job cancellation signals the registered Process Execution handle.
- R2: A confirmed terminal process completion unregisters before later Job
  cancellation and is not cancelled retroactively.
- R3: UnitOfWork termination cancels, awaits, and reclaims an active process and
  its WorkArea.
- R4: An `awaitC` failure that does not confirm terminal completion MUST retain
  UnitOfWork ownership; later UnitOfWork termination MUST still cancel, await,
  and reclaim the process resource.

The active execution cancellation scope is Job-owned and effect-neutral. A
Process Execution registration contains only a cancellation callback; the Job
runtime MUST NOT depend on a Process Execution driver or handle type. A
registration made after cancellation is accepted MUST be signalled immediately.
Retrying a Job MUST use a new cancellation scope so callbacks admitted by an
older cancelled run cannot affect the retry.

Cancellation is idempotent. If process completion wins the race, its terminal
result remains authoritative and a later cancellation request has no effect.
Process Execution does not add a Command mode, a scheduler, or a transaction
participant. It follows the existing ActionCall and Job/Task execution context.

## Observability Contract

Normal CallTree, metric, and diagnostic projection may expose only safe
structural metadata: capability, safe program identity, driver kind,
termination, exit code, elapsed time, byte counts, artifact counts, and limit
category. The projection MUST NOT include stdin, prompt, stdout, stderr,
sensitive argument values, environment values, credentials, raw host paths,
or artifact content.

The runtime metric scope is `process.execution`. It may use only `outcome`,
capability, safe driver identity, termination, and a `ConclusionDiagnostics`
key as metric labels. It MUST derive failed-operation classification from the
returned `Conclusion`; it MUST NOT parse a failure display message.

## Required Executable Behaviors

The implementation must demonstrate at least the following behaviors with
executable specifications:

1. direct and Free/UnitOfWork paths construct the same `ProcessExec` intent;
2. a scope-inherited driver can be explicitly overridden by a fake driver;
3. denied or unknown capabilities never reach a driver;
4. caller-supplied executable paths and shell text are rejected by the model;
5. argument vectors are passed without shell interpolation;
6. WorkArea traversal and symlink escape are rejected;
7. stdin, stdout, stderr, and artifact limits are independently enforced;
8. local-driver streams drain concurrently and timeout/cancellation terminate
   the process tree without leaks;
9. Job cancellation reaches an active handle and cannot overwrite an earlier
   terminal completion;
10. only declared, confined artifacts are returned and cleanup runs on every
    terminal path;
11. normal diagnostics omit confidential process values; and
12. a provider-style logical request reaches direct and Free DSL execution only
   through a scoped runtime admission service, without a live external CLI.
13. an explicit deterministic fake driver proves fixed runtime environment,
    command-template, bounded-input, and declared-output admission; and
14. provider-local result conversion preserves every Process terminal category
    until the adapter makes an explicit domain decision.

## Non-Goals

This contract does not define container or remote drivers, arbitrary shell
execution, process retention, live Codex integration, migration of legacy
Docker/shell adapters, BPM/process orchestration, or an independent
process scheduler.
