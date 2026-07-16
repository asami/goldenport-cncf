# Process Execution Design Note

status = proposed, non-normative
date = 2026-07-17

Source handoff:
`docs/journal/2026/07/managed-process-codex-provider-handoff-2026-07-17.md`

## Purpose

This note defines the working design for executing operating-system processes
through the CNCF runtime. The first concrete consumer is the Textus AI Codex
provider, but the contract is provider-neutral and is not specific to AI or
Codex.

The feature name is **Process Execution**.

`Managed Process` is not used as the feature name. Execution through CNCF is
managed by definition; putting `Managed` in the name would incorrectly imply
that an equivalent unmanaged feature exists at the same abstraction layer.

This note supersedes the source handoff's recommendation to evolve
`UnitOfWorkOp.ShellCommandExec`. The new direction is to add a CNCF-owned
Process Execution contract instead of extending the simplemodeling-lib
`ShellCommand` contract.

This document is a non-normative design note. Settled API and failure contracts
must be promoted to `docs/design/` and `docs/spec/` and covered by executable
specifications before the feature is treated as complete.

## Decision Summary

- CNCF owns Process Execution request, result, policy, lifecycle, driver, and
  observability contracts.
- `UnitOfWorkOp.ProcessExec` is the canonical executable intent.
- Component and provider code use a protected CNCF internal DSL method named
  `process_exec`.
- `ScopeContext` resolves a `ProcessExecutionDriver`, following the same
  inherited runtime-driver model used by other CNCF effects.
- A request selects a registered `ProcessCapabilityId`; it does not provide an
  arbitrary executable path or shell command string.
- The runtime resolves the capability to a program definition, validates the
  argument and environment policy, applies mandatory limits, and confines all
  file access to a CNCF `WorkArea`.
- simplemodeling-lib `ShellCommand` and CNCF `ShellCommandExec` remain legacy
  facilities for existing callers. New Process Execution consumers do not use
  them.
- A local driver may use JDK `ProcessBuilder` as a private implementation
  detail. `ProcessBuilder`, raw `Process`, and process handles are never exposed
  to component or provider code.

## Terminology and Candidate Names

The feature family uses `ProcessExecution` in names where a short `Process`
name would be ambiguous with a domain or workflow process.

Candidate CNCF types are:

```scala
ProcessCapabilityId
ProcessExecutionRequest
ProcessExecutionLimits
ProcessExecutionResult
ProcessExecutionTermination
ProcessExecutionArtifact
ProcessExecutionDriver
ProcessExecutionHandle
ProcessProgramDefinition
ResolvedProcessExecution
```

The UnitOfWork and internal DSL names are intentionally shorter because their
execution context is already explicit:

```scala
UnitOfWorkOp.ProcessExec
protected final def process_exec(...)
```

Avoid the following names:

- `ManagedProcess`: management is an invariant, not a variant;
- `ShellCommand`: Process Execution does not require a shell;
- `CommandExecution`: CNCF already uses command terminology for application
  operations and runtime command modes;
- `LocalProcess`: the driver may later use a container or remote execution
  environment;
- `NativeProcess`: this can be confused with native code or JNI.

## Ownership Boundary

### CNCF owns

- Process Execution types and UnitOfWork intent;
- capability admission and runtime program resolution;
- effective limits and environment policy;
- WorkArea allocation, confinement, artifact collection, and cleanup;
- driver selection and lifecycle;
- timeout and cancellation propagation;
- process-tree termination policy;
- structured result and failure vocabulary;
- CallTree and metrics integration;
- confidentiality policy and safe diagnostics;
- fake and local-driver executable specifications.

### A consumer such as Textus AI owns

- selection of a process capability such as `codex-cli`;
- construction of provider-specific arguments within the granted policy;
- prompt, schema, and provider response semantics;
- interpretation of exit status, stdout, stderr, and output artifacts;
- conversion from a Process Execution result to the provider's domain result;
- provider-specific retry decisions.

### The external program owns

- its authentication state and account configuration;
- its own internal behavior;
- the meaning of its command-line options and generated output.

For Codex CLI, CNCF does not read, copy, persist, or publish Codex account
credentials. The runtime may allow explicitly configured ambient environment
and home-directory access required by the installed Codex CLI, but those values
and locations are confidential and do not appear in normal diagnostics.

### simplemodeling-lib owns

The existing generic `ShellCommand`, `ShellCommandResult`, and
`ShellCommandExecutor` contracts remain simplemodeling-lib facilities. They are
not the base contract for CNCF Process Execution.

This separation avoids forcing CNCF-specific concepts such as UnitOfWork,
ScopeContext, WorkArea, Job cancellation, execution capabilities, and CallTree
into a generic library API.

## Architecture

```text
Textus AI Codex provider
  -> protected process_exec DSL
  -> UnitOfWorkOp.ProcessExec
  -> UnitOfWorkInterpreter
  -> capability and policy resolution
  -> ScopeContext.processExecutionDriver
  -> ProcessExecutionDriver
  -> local, container, or remote process implementation
  -> external program
```

The UnitOfWork operation expresses the requested effect. The driver performs
the platform-specific lifecycle. The interpreter owns authorization, context,
WorkArea, observability, and conversion between the two.

Component code must not obtain the driver or a process handle directly.

## UnitOfWork Contract

The canonical intent is:

```scala
final case class ProcessExec(
  request: ProcessExecutionRequest
) extends UnitOfWorkOp[ProcessExecutionResult]
```

The protected internal DSL constructs this operation:

```scala
protected final def process_exec(
  request: ProcessExecutionRequest
): ExecUowM[ProcessExecutionResult]
```

Direct and Free/UnitOfWork execution paths must construct the same
`UnitOfWorkOp.ProcessExec` value. There must not be a separate direct
`ProcessBuilder` path for provider implementations.

The interpreter performs the following sequence:

1. Validate the request shape and requested capability.
2. Authorize the component/provider to use the capability.
3. Resolve the runtime `ProcessProgramDefinition`.
4. Validate arguments, environment requests, input, output declarations, and
   requested limits.
5. Calculate mandatory effective limits.
6. Allocate or select an execution-scoped WorkArea.
7. Resolve and verify all working and artifact paths inside that WorkArea.
8. Start the driver and register its handle with execution cancellation.
9. Await completion while enforcing timeout, cancellation, and output limits.
10. Collect declared artifacts and safe execution summaries.
11. Unregister the handle and clean up scoped resources.
12. Emit the Process Execution result and safe observability records.

## Request Model

A candidate request shape is:

```scala
final case class ProcessExecutionRequest(
  capability: ProcessCapabilityId,
  arguments: Vector[String],
  input: ProcessExecutionInput,
  workingDirectory: Option[WorkAreaRelativePath],
  outputs: Vector[ProcessExecutionOutputDeclaration],
  requestedLimits: Option[ProcessExecutionLimits]
)
```

The exact Scala types remain provisional, but the following semantics are
required.

### Capability instead of executable

The request contains a `ProcessCapabilityId`, for example:

```text
codex-cli
```

It must not contain:

- an executable path;
- a shell command string;
- an interpreter expression;
- a host working-directory root;
- arbitrary environment inheritance instructions.

The runtime maps the capability to a `ProcessProgramDefinition`.

### Arguments

Arguments are an argument vector. They are never concatenated into a shell
command and are never evaluated by a shell unless a future, separately
authorized program definition explicitly represents a shell interpreter.

Every argument vector is validated by the resolved program definition. A
definition may enforce:

- fixed argument prefixes;
- required flags and values;
- allowed and forbidden flags;
- value syntax;
- WorkArea-relative path arguments;
- mutually exclusive options;
- a maximum argument count and encoded size.

For a sensitive tool such as Codex CLI, the runtime definition and the Textus
AI adapter together must prevent callers from weakening the required sandbox,
workspace, session, schema, or output policy.

### Input

The first contract should support bounded variants such as:

```scala
sealed trait ProcessExecutionInput
case object EmptyInput
final case class ByteInput(content: Bag)
final case class WorkAreaFileInput(path: WorkAreaRelativePath)
```

Input size is checked before launch. Text input has an explicit charset, UTF-8
by default. Prompt content is confidential by default.

### Working directory

The request may select only a path relative to the allocated WorkArea. CNCF
owns the absolute execution root. The runtime verifies normalized and real
paths and rejects traversal or symlink escape.

The caller cannot select an arbitrary host directory.

### Output declarations

stdout and stderr capture are part of every execution and have independent
byte limits. File outputs must be declared before launch and must resolve under
the WorkArea.

An output declaration describes the expected logical artifact, relative path,
kind, and maximum size. Undeclared files are not returned as result artifacts.
The runtime may reject an execution that exceeds WorkArea file-count or total
size quotas.

## Runtime Program Definition

`ProcessProgramDefinition` is runtime configuration or framework registration,
not caller input. It binds a capability to an executable and its policy.

A definition contains at least:

```text
capability id
driver selector
executable identity and resolved location
fixed arguments
argument validation policy
environment policy
working-directory policy
maximum limits
allowed output declarations
trust and sandbox profile
safe observability identity
```

Executable resolution happens during runtime bootstrap or capability
resolution. A CAR must not override it.

Missing executable, ambiguous resolution, invalid policy, or a definition that
requests limits unsupported by its driver causes deterministic admission or
startup failure.

## Environment Policy

The process environment is built by CNCF from the resolved program definition.
It does not automatically inherit the complete parent environment.

The policy classifies environment entries as:

- fixed runtime values;
- explicitly inherited names;
- runtime-resolved confidential values;
- caller-overridable names with validators;
- forbidden names.

The request may provide a value only for an explicitly overridable name. It
cannot ask to inherit an arbitrary name. Environment names may be recorded when
safe; values are never recorded in ordinary CallTree attributes, metrics,
diagnostics, or error messages.

Credential-bearing values and credential locations are confidential even when
the external program requires them.

## Limits

Every resolved execution has mandatory effective limits. There is no
unbounded profile.

`ProcessExecutionLimits` covers at least:

- launch timeout;
- total execution timeout;
- graceful termination interval;
- stdin byte count;
- stdout byte count;
- stderr byte count;
- argument count and encoded size;
- output artifact count;
- per-artifact byte count;
- total WorkArea byte count.

The effective value is the strictest value from:

```text
runtime maximum
program-definition maximum
component/provider grant
request override
```

A request may tighten limits but cannot widen its grant. Unsupported or
internally inconsistent limits are rejected before launch.

## Driver and Lifecycle

The driver boundary separates process policy from platform mechanics.

A candidate lifecycle contract is:

```scala
trait ProcessExecutionDriver {
  def start(
    execution: ResolvedProcessExecution
  ): Consequence[ProcessExecutionHandle]
}

trait ProcessExecutionHandle {
  def await(
    control: ProcessExecutionControl
  ): Consequence[ProcessExecutionResult]

  def cancel(): Consequence[Unit]
}
```

These signatures are illustrative. The required behavior is:

- launch without shell interpolation;
- drain stdout and stderr concurrently from process start;
- enforce independent capture limits without pipe deadlock;
- react to timeout and execution cancellation;
- terminate the child and its descendants;
- attempt graceful termination before forced termination when policy permits;
- return only after stream readers and artifact collection reach a stable
  state;
- make `cancel` idempotent;
- prevent process and thread leakage after terminal completion.

The handle is interpreter/runtime-internal. It is registered with the active
execution cancellation scope immediately after launch and unregistered in a
finally-safe cleanup path.

### Driver resolution

`ScopeContext.Core` should gain an optional `ProcessExecutionDriver`, inherited
through parent scopes in the same style as `HttpDriver`.

The runtime scope supplies the default driver. A child scope may override it
for a subsystem, component, executable specification, container profile, or
remote execution environment.

`UnitOfWork` must resolve the driver from `ExecutionContext`/`ScopeContext`; it
must not instantiate a local driver directly.

## Cancellation and Job Integration

Job cancellation and process termination are one coordinated lifecycle.

```text
Job cancellation requested
  -> active execution cancellation scope is signalled
  -> registered ProcessExecutionHandle.cancel
  -> child and descendants are terminated
  -> ProcessExecutionTermination.Cancelled
  -> provider and Job result mapping
```

Marking a Job `Cancelled` without signalling and terminating its active process
is not sufficient.

Cancellation is cooperative at the CNCF boundary but forceful at the OS
boundary after the configured graceful interval. A completion that wins the
race with cancellation keeps its actual exit result. Cancellation after a
terminal result is an idempotent no-op.

Suspend and resume semantics are not part of the first Process Execution
contract. A Job containing an active process must not claim that suspension
pauses the OS process unless a later driver contract explicitly supports it.

## Result Model

The result separates lifecycle termination from provider interpretation.

```scala
enum ProcessExecutionTermination {
  case Exited(exitCode: Int)
  case LaunchFailed
  case TimedOut
  case Cancelled
  case OutputLimitExceeded(stream: ProcessExecutionStream)
  case ArtifactLimitExceeded
}
```

A candidate result contains:

```text
termination
bounded stdout capture
bounded stderr capture
declared artifacts collected under WorkArea policy
stdin/stdout/stderr byte counts
artifact count and byte counts
elapsed duration
truncation indicators
safe program identity
```

A non-zero exit code is an `Exited` result, not automatically a CNCF failure.
The consumer decides whether it represents a successful domain/provider
response.

Expected operational outcomes such as timeout, cancellation, output-limit
termination, and launch failure are represented explicitly. Request rejection,
capability denial, invalid runtime configuration, WorkArea confinement failure,
and an internal driver contract violation use the normal CNCF
`Consequence.Failure(Conclusion)` path.

The result never contains a raw host executable path, complete environment,
credential, account identity, or unrestricted host path.

## WorkArea and Artifact Rules

Each execution uses an execution-scoped WorkArea or a child area allocated from
the current scope.

CNCF owns:

- input-file materialization;
- schema-file materialization;
- output-file allocation;
- relative-to-absolute path resolution;
- traversal and symlink-escape checks;
- file-count and size quotas;
- artifact metadata collection;
- cleanup after result materialization.

An artifact returned to the caller is a CNCF value or handle with a logical
identity. Returning an unrestricted host `Path` is not the default contract.

Cleanup must occur after the caller-visible result has safely captured or
transferred the declared artifacts. Retention for diagnostics is an explicit
runtime policy, not an accidental consequence of failed cleanup.

## Capability and Sandbox Policy

Process Execution is denied to component code by default.

A grant is program-specific:

```text
process.exec:codex-cli
```

Wildcard arbitrary execution such as `process.exec:*` is not an ordinary soft
sandbox capability. It requires a hard sandbox or an explicitly privileged
framework profile.

A named definition such as `codex-cli` may be enabled for a trusted
CNCF-managed provider when all of the following are fixed or constrained:

- executable identity;
- argument policy;
- environment policy;
- WorkArea root;
- resource limits;
- output policy;
- observability policy;
- runtime trust profile.

Capability checks are separate from subject and operation authorization:

```text
subject authorization
  -> may this caller invoke the operation?

CAR/provider execution grant
  -> may this component request this process capability?

program admission
  -> does this concrete request satisfy the registered policy?
```

All applicable checks must pass.

## Observability and Confidentiality

Process Execution emits structured CallTree and metrics information without
publishing process content.

Safe candidate attributes include:

```text
process.capability
process.program_identity
process.driver_kind
process.termination
process.exit_code
process.elapsed_ms
process.stdin_bytes
process.stdout_bytes
process.stderr_bytes
process.artifact_count
process.artifact_bytes
process.limit_triggered
```

The following are confidential by default and must not be emitted as normal
attributes or interpolated into failures:

- stdin and prompt text;
- stdout and generated text;
- stderr content;
- argument values marked sensitive;
- environment values;
- API keys, tokens, account identity, and credential locations;
- raw host paths;
- schema and artifact contents.

Diagnostics may include safe argument names or normalized option categories,
but not complete command lines. Debug retention of content requires an explicit
confidentiality-aware policy and must be disabled by default.

## Textus AI Codex Provider Profile

The first program definition is expected to use capability id `codex-cli`.

The Textus AI adapter constructs a fixed `codex exec` argument vector that
requires:

- an explicit CNCF WorkArea workspace;
- the selected Codex sandbox policy;
- ephemeral-session behavior by default;
- bounded stdin, stdout, and stderr;
- CNCF-managed schema and output paths for structured generation;
- no shell interpolation;
- no arbitrary host path selection.

The Process Execution result is provider-neutral. Textus AI maps it to an AI
runner result, including schema validation and provider-specific interpretation
of non-zero exits.

Normal executable specifications use a fake Process Execution driver and do
not depend on a live Codex installation, Codex account, network connection, or
paid API. A separately labelled local integration profile may exercise the
installed CLI when explicitly enabled.

## Compatibility with ShellCommand

The first implementation adds `UnitOfWorkOp.ProcessExec`; it does not rename or
silently change `UnitOfWorkOp.ShellCommandExec`.

Existing shell-command and Docker adapters may continue using
simplemodeling-lib `ShellCommandExecutor`. They are migration candidates, not
prerequisites for Process Execution.

Do not implement Process Execution by translating every new request into the
old `ShellCommand` public contract. That would make the old contract the real
semantic boundary and would lose the CNCF-specific lifecycle guarantees.

A compatibility adapter may later translate a restricted legacy
`ShellCommand` into a Process Execution request when a safe capability mapping
is available. There is no reverse adapter from a general Process Execution
request to arbitrary shell text.

## Failure Categories

The specification phase should assign stable `Conclusion` diagnostics for at
least:

- capability not granted;
- process program not registered;
- executable unavailable;
- argument policy violation;
- environment policy violation;
- invalid WorkArea path;
- input limit exceeded before launch;
- driver unavailable or unsupported limits;
- launch failure;
- timeout;
- cancellation;
- stdout or stderr limit exceeded;
- artifact missing, invalid, or over limit;
- cleanup failure;
- driver contract violation.

Provider failure must remain distinct from framework admission failure. For
example, `codex exec` returning a non-zero exit is a provider result, whereas a
CAR without the `codex-cli` grant is an admission failure.

## Executable Specification Targets

The first executable specifications should prove:

1. The same `ProcessExec` intent is used by direct and Free/UnitOfWork paths.
2. A fake driver is inherited and overridden through `ScopeContext`.
3. An unknown or denied capability never reaches the driver.
4. An executable path cannot be supplied by the caller.
5. Arguments are passed as a vector without shell interpolation.
6. Working-directory traversal and symlink escape are rejected.
7. stdin, stdout, and stderr limits are independently enforced.
8. stdout and stderr are drained concurrently without deadlock.
9. Timeout terminates the child and descendants.
10. Job cancellation reaches the active handle and produces `Cancelled`.
11. Cancellation is idempotent and cannot overwrite an earlier terminal exit.
12. Only declared, confined, bounded artifacts are returned.
13. WorkArea cleanup runs for success, failure, timeout, and cancellation.
14. CallTree and failures omit prompt, output, environment values, credentials,
    and raw host paths.
15. A non-zero exit remains an `Exited` result for provider interpretation.
16. Normal tests do not require a live Codex CLI.

Local-driver integration specifications should use small controlled test
programs or scripts. Live external AI execution belongs to an opt-in
integration profile.

## Implementation Order

1. Promote the request, result, termination, capability, and failure vocabulary
   to CNCF design/spec documents.
2. Add `ProcessExecutionDriver` resolution to `ScopeContext` and runtime
   bootstrap.
3. Add `UnitOfWorkOp.ProcessExec`, interpreter support, and the protected
   `process_exec` DSL.
4. Implement a fake driver and lifecycle/confidentiality executable
   specifications.
5. Implement the local driver with concurrent stream draining, mandatory
   limits, timeout, cancellation, and descendant termination.
6. Complete WorkArea confinement, quotas, artifact transfer, and cleanup.
7. Propagate Job cancellation through the active execution scope.
8. Register the `codex-cli` program definition and implement the Textus AI
   Codex provider.
9. Add an explicitly enabled local Codex integration profile.
10. Evaluate legacy `ShellCommandExec` migration separately.

## Deferred Decisions

- Exact package placement of the Process Execution types.
- Whether the driver API exposes `start`/`handle` directly or an equivalent
  structured asynchronous operation internally.
- Exact `Bag`, byte-vector, stream, or artifact-handle representation for
  captured content.
- Runtime configuration schema for program definitions and capability grants.
- Container and remote driver protocols.
- Diagnostic retention policy for failed executions.
- Whether an execution may intentionally retain its WorkArea beyond operation
  completion.
- Compatibility schedule for existing `ShellCommandExec` consumers.

## Related Documents

- `docs/notes/unitofwork-guideline.md`
- `docs/notes/scope-context-design.md`
- `docs/notes/execution-determinism-capability-design.md`
- `docs/notes/car-capability-sandbox-design.md`
- `docs/journal/2026/07/managed-process-codex-provider-handoff-2026-07-17.md`
- `textus-ai/docs/notes/car-review-ai-runtime-design.md`
- `textus-ai/docs/phase/phase-1.md`
- `textus-ai/docs/phase/phase-1-checklist.md`
