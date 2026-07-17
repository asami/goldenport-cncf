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
  -> protected process_exec(request) DSL
  -> ScopeContext ProcessExecutionAdmission
  -> UnitOfWorkOp.ProcessExec
  -> UnitOfWork interpreter
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
CAR input cannot replace or loosen this definition. Construction of a program
definition is runtime-internal; component behavior receives only an already
admitted `ResolvedProcessExecution` through the protected DSL.

`ProcessExecutionAdmission` is the scoped runtime service that makes this
boundary usable by a consumer with dynamic logical arguments. Runtime assembly
installs the effective program policy and the grants for one component/provider
scope. Consumer behavior constructs a `ProcessExecutionRequest` and calls the
protected `process_exec(request)` DSL; the service validates the request and
creates the resolved-only intent used by `UnitOfWorkOp.ProcessExec`. Consumer
code does not construct `ProcessProgramDefinition`, select a driver, or receive
an executable location. Scope inheritance may supply an admission service, and
a child scope may explicitly replace it only as trusted runtime/test wiring.

The initial downstream pattern is a Textus AI provider requesting a logical
`codex-cli` capability. Its fixed CLI invocation, sandbox policy, executable
location, environment policy, and limits belong to runtime assembly; the
provider owns only provider-specific request arguments and interpretation of a
terminal `ProcessExecutionResult`. This is a handoff contract, not a Codex
implementation in CNCF core.

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

An admitted resource tree reaches this boundary as a
`ProcessExecutionResourceTreeInput` created from an opaque
`ResourceTreeSnapshot` and a WorkArea-relative target. The component can
request a narrower tree limit, but cannot name a host root or materialize the
tree itself. Program admission and the component/provider grant both restrict
the tree before the UnitOfWork interpreter copies it into the WorkArea.

Callers may select only normalized relative paths under that WorkArea. A
declared output path is relative to the selected working directory, or the
WorkArea root when no working directory is selected. Path traversal, symlink
escape, undeclared output projection, and quota overflow are rejected. The
caller receives declared bounded artifacts with logical identities, not
unrestricted host `Path` values. Cleanup is finally-safe for success, ordinary
failure, timeout, and cancellation; diagnostic retention is an explicit future
policy, not accidental failed cleanup.

## Driver Lifecycle

The driver executes an already admitted and resolved request. It launches an
argument vector without shell interpolation, starts draining stdout and stderr
concurrently, applies bounded capture independently to both streams, and
supports idempotent cancellation.

The initial environment policy is intentionally narrow: runtime program
definitions may contain validated fixed bindings, and the local driver clears
ambient environment state before installing those bindings. Components cannot
supply an environment map. Inheritance, confidential runtime resolution, and
caller overrides require separately specified runtime provenance and redaction
rules before they are enabled.

The runtime resolves a driver as a `ProcessExecutionDriver` with a safe logical
driver identity. `startC` returns a `ProcessExecutionHandle`; `awaitC` returns
one terminal result and `cancelC` is idempotent. The driver identity is safe
structural metadata only and must not be a host path, command string, account,
or provider credential. `ProcessExecutionTestProfile` is an explicit
deterministic fixture: it returns configured results and never creates a host
process.

The interpreter owns timeout and cancellation coordination. It registers a
handle immediately after launch with both the Job cancellation scope and the
owning UnitOfWork resource registry. The Job scope promptly signals cancellation;
the UnitOfWork resource releases a still-active handle by cancelling, awaiting,
and closing the WorkArea. Normal completion unregisters both registrations in a
finally-safe path. A terminal completion that wins a race with cancellation
keeps its actual terminal result; later cancellation is a no-op.

`JobCancellationScope` is the Job-owned generic active-work boundary. The Job
engine creates one scope for a submitted execution, passes it through the
`JobContext` of primary, same-Job, and compensation Tasks, and signals it when
Job control accepts cancellation. Process Execution registers only
`ProcessExecutionHandle.cancelC` with that scope; `JobEngine` never depends on
the Process Execution SPI. A handle that registers after an accepted
cancellation is signalled immediately, which closes the launch/cancel race.
Registration is removed when `awaitC` reaches a terminal result. Retrying a
cancelled Job creates a fresh scope, so an old task cannot cancel active work
from the retry.

The generic lifecycle contract is defined by
`docs/design/unit-of-work-resource-lifecycle.md`. A process handle is an
operational resource, not a transaction participant: database rollback cannot
undo an external program, but UnitOfWork termination must still stop and reap
the runtime resource it owns.

## Result And Failure Boundary

A process result separates transport lifecycle from provider/domain meaning. A
non-zero exit is an exited terminal result, not automatically a CNCF failure;
the consumer decides whether that exit represents a successful provider
response. Launch failure, timeout, cancellation, capture-limit termination,
and artifact-limit termination are explicit process outcomes.

Provider adapters are intentionally outside the generic runtime vocabulary.
They accept `ProcessExecutionResult` and convert each explicit termination to
their own domain outcome. This has deterministic fake-driver coverage without
requiring a live external tool or importing application/provider types into
CNCF.

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

`RuntimeDashboardMetrics` projects Process Execution as
`process.execution`. Its labels are limited to outcome, capability, safe driver
identity, terminal result, and structured `ConclusionDiagnostics` key. A
driver failure is projected from its `Conclusion`, not from a display message.

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
