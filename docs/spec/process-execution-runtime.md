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

## Program Definition And Effective Policy

`ProcessProgramDefinition` is runtime configuration or framework registration,
not CAR request data. It binds a capability to a driver selector, safe program
identity, resolved executable location, fixed arguments, argument validation,
environment policy, WorkArea policy, output policy, maximum limits, and trust
or sandbox profile.

Program-definition construction is runtime-internal. Component and provider
behavior may receive only the capability-admitted resolved execution through
the protected `process_exec` DSL; `UnitOfWorkOp.ProcessExec` has that resolved
intent as its sole payload.

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

Input files, working directories, and declared outputs are confined to the
execution WorkArea. Relative paths MUST be non-empty normalized relative paths
without traversal segments. Runtime path validation MUST prevent real-path and
symlink escape before launch or artifact collection.

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

## Cancellation And Job Contract

The interpreter registers an active process handle with the active execution
cancellation scope immediately after launch and unregisters it on every
terminal path. Job cancellation signals the active handle and must lead to
process-tree termination according to the effective termination policy.

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
    terminal path; and
11. normal diagnostics omit confidential process values.

## Non-Goals

This contract does not define container or remote drivers, arbitrary shell
execution, process retention, live Codex integration, a compatibility migration
for legacy shell facilities, BPM/process orchestration, or an independent
process scheduler.
