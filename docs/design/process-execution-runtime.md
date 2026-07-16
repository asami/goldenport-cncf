# Process Execution Runtime

Status: normative design

## Purpose

Process Execution is the CNCF execution-platform boundary for a component or a
trusted provider that needs to invoke an approved external operating-system
program. It makes that effect subject to the same ActionCall, UnitOfWork,
authorization, Job/Task, cancellation, WorkArea, and observability boundaries
as other CNCF effects.

The feature is provider-neutral. A future Textus AI adapter may use a
`codex-cli` capability, but CNCF does not own a Codex account, CLI integration,
or provider-specific response interpretation.

## Ownership And Chokepoint

```text
Component or provider behavior
  -> protected process_exec DSL
  -> UnitOfWorkOp.ProcessExec
  -> UnitOfWork interpreter
  -> capability and program-policy admission
  -> ScopeContext ProcessExecutionDriver
  -> approved external program
```

Components and providers describe intent through `process_exec`. They MUST NOT
instantiate `ProcessBuilder`, obtain an operating-system process handle, call a
driver directly, concatenate shell text, or select a host executable. The
UnitOfWork interpreter owns admission, execution context, WorkArea allocation,
handle lifecycle, cancellation registration, and safe observability.

`ProcessExecutionDriver` is a runtime SPI resolved through `ScopeContext`.
Scope inheritance supplies the ordinary driver; a child scope may explicitly
override it for an executable specification, a subsystem, or a deployment
profile. A missing driver is a deterministic runtime failure, not a fallback to
ambient host execution.

## Capability Admission

Every request selects a registered `ProcessCapabilityId`. A capability is a
logical program permission, for example `codex-cli`; it is not a filesystem
path, a command line, or a shell interpreter.

The runtime resolves that capability to a `ProcessProgramDefinition`. The
definition owns the executable identity and location, fixed arguments,
argument rules, environment policy, driver selection, WorkArea policy, output
policy, resource limits, trust/sandbox profile, and safe diagnostic identity.
CAR input cannot replace or loosen this definition.

Admission has three independent checks:

1. subject and operation authorization decides whether the caller may invoke
   the operation;
2. component/provider capability admission decides whether the implementation
   may request this named program capability; and
3. program admission decides whether the concrete request satisfies the
   registered definition.

All three checks must succeed before the driver is invoked. Admission is
deny-by-default. Arbitrary or wildcard process execution is outside the normal
component capability model and requires a separately designed privileged hard
sandbox profile.

## Execution And Transaction Boundary

`UnitOfWorkOp.ProcessExec` is an explicit external effect. Direct ActionCall
execution and Free/UnitOfWork execution MUST create the same operation intent
and run through the same interpreter path. A separate direct process path is
not permitted.

The operation runs in the active Task transaction boundary. It does not create
a Command execution mode or an independent scheduler. A synchronous command
therefore follows its existing transaction semantics; a Job-managed command
runs in its Job/Task execution context. Job cancellation must signal every
active process registered by that execution before the Job reaches a cancelled
terminal state.

The process itself is an external effect, not a transaction participant. A
database rollback cannot undo an external program that has already changed the
outside world. Component design must therefore treat Process Execution as an
explicit side effect and use existing compensation or recovery policy when
needed.

## WorkArea And Artifact Boundary

Each invocation receives an execution-scoped WorkArea, or a child WorkArea
allocated from the active scope. CNCF owns input materialization, working
directory selection, output allocation, real-path verification, artifact
collection, quota enforcement, and cleanup.

Callers may select only normalized relative paths under that WorkArea. Path
traversal, symlink escape, undeclared outputs, and quota overflow are rejected.
The caller receives declared bounded artifacts with logical identities, not
unrestricted host `Path` values. Cleanup is finally-safe for success, ordinary
failure, timeout, and cancellation; diagnostic retention is an explicit future
policy, not accidental failed cleanup.

## Driver Lifecycle

The driver executes an already admitted and resolved request. It launches an
argument vector without shell interpolation, starts draining stdout and stderr
concurrently, applies bounded capture independently to both streams, and
supports idempotent cancellation.

The interpreter owns timeout and cancellation coordination. It registers a
handle immediately after launch, requests graceful termination when policy
allows, escalates to forced process-tree termination when required, waits for
stream readers to reach a stable terminal state, and unregisters the handle in
a finally-safe path. A terminal completion that wins a race with cancellation
keeps its actual terminal result; later cancellation is a no-op.

## Result And Failure Boundary

A process result separates transport lifecycle from provider/domain meaning. A
non-zero exit is an exited terminal result, not automatically a CNCF failure;
the consumer decides whether that exit represents a successful provider
response. Launch failure, timeout, cancellation, capture-limit termination,
and artifact-limit termination are explicit process outcomes.

Malformed requests, denied capabilities, absent program definitions, invalid
runtime configuration, WorkArea policy violations, unavailable drivers, and
driver contract violations use the normal `Consequence.Failure(Conclusion)`
path. Framework admission failure must remain distinguishable from a provider
program's terminal result.

## Confidentiality And Observability

CallTree, metrics, and structured diagnostics may record safe structural
metadata: capability, safe program identity, driver kind, termination, exit
code, elapsed time, byte counts, artifact counts, and limit category. They MUST
NOT record prompt/stdin content, stdout/stderr content, sensitive arguments,
environment values, credentials, account identity, raw host paths, or artifact
contents.

The driver and any provider adapter are responsible for returning safe failure
messages. The CNCF runtime must not interpolate confidential request or result
content into generic diagnostics.

## Relationship To Existing Shell Facilities

Process Execution is a new CNCF runtime effect. It neither renames nor changes
the semantics of legacy `UnitOfWorkOp.ShellCommandExec`, `ShellCommand`, or
existing Docker/shell adapters. Those facilities are separate migration
candidates. New managed-provider code uses Process Execution rather than using
the generic shell contract as its semantic boundary.

## Test Composition

Executable specifications use an explicitly installed deterministic fake
driver for admission, inheritance, cancellation, and confidentiality behavior.
Controlled local-program tests cover stream draining, timeout, and process-tree
termination. Normal tests do not require a live external CLI, network access,
or account credentials.

## Deferred Scope

This design excludes container and remote drivers, process retention, arbitrary
shell access, automatic legacy-shell migration, live Textus AI/Codex provider
integration, BPM/process orchestration, and an independent process scheduler.
