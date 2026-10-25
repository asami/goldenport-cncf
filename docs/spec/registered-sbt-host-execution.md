# Runtime-owned SBT execution

Status: public runtime and UnitOfWork contract, Oct. 20, 2026.

## Typed admission observations

`SbtExecutionAttempt` is a public closed ADT with:

- `Observed(result: SbtExecutionResult)`: an actual process observation, retaining
  the existing SBT outcome and TestOnly qualification semantics;
- `AdmissionRejected(target, operation, conclusion)`: current-context admission
  failed before execution, retaining its original `Conclusion`;
- `Uncertain(target, operation, conclusion)`: admission succeeded, but managed
  execution returned a failure and external execution cannot be inferred absent.

Protected `sbt_compile_attempt`, `sbt_test_attempt` and `sbt_test_only_attempt`
construct one `UnitOfWorkOp.SbtExecAttempt` each. Construction performs no
admission or launch. Recording and planning classify that occurrence as External.
The interpreter resolves the actual Component, shared workspace Manage grant,
independent process capability and fixed definition in its current UnitOfWork
context. Existing `sbt_compile`, `sbt_test`, `sbt_test_only`, `SbtExec` and
`SbtExecutionResult` retain their APIs and behavior.

Only actual admission failure becomes `AdmissionRejected`. A managed failure at
or after driver start becomes `Uncertain`. Nonzero exit, timeout, cancellation
and output overflow retain their existing outcomes. Observed TestOnly results
pass through the existing selected-report decoder. There is no automatic replay
or additional ProcessExecutionRequest construction site.

## Runtime ownership

The normal runtime factory installs `LocalProcessExecutionDriver` directly.
`SystemNode` owns that driver as a runtime resource. After UnitOfWork admission,
the driver launches the ordinary configured SBT executable with the admitted
fixed argument vector, runtime-owned environment and canonical workspace cwd.
The same program owns launch, waiting, process observation and result collection.
UnitOfWork retains the existing calltree, WorkArea, handle registration, Job
cancellation and resource cleanup responsibilities.

V1, V2 and V3 configuration retain their exact wire shapes, fixed declarations,
workspace authority and independent process grants. The retired
`cncf.runtime-process-execution.v4` schema is explicitly unsupported, including
when its underlying V3 declarations are valid. V1–V3 reject
`registeredSbtHost` as an unknown field. There is no V4-to-V3 fallback,
file rendezvous, provider response receipt or external runner in this product
execution route.

Independent SBT invocations use SBT's native boot, dependency-cache and workspace
locks. There is no application-owned machine-wide lock or manual response step.
Local-driver launch and execution limits remain runtime-owned. Normal callers
wait for the actual response without a 60/240-second cutoff or a deadline derived
from telemetry. Cancellation requests alone cannot establish actual process
termination or safe replay.

## CLI SIGTERM lifecycle

Only the normal CLI `run` path installs `RuntimeCommandTermination`, after the
Subsystem initializes. Embedding and `initializeHandle` install no signal hook.
The invocation-scoped JVM shutdown hook requests local-driver stop once and
waits for the main command and its normal owned Subsystem shutdown to complete.
The bounded hook grace is the existing `SystemNode.drainTimeoutMillis`; there
is no caller cleanup timer, process-group fallback or new configuration.

`requestStopC` closes process launch admission under the existing lifecycle lock
and cancels all active handles. It retains their capture resources and owning
awaits. Every UnitOfWork process using that owned driver participates
automatically, including generic ProcessExec operations; application code does
not opt in through a cancellation callback. Actual process termination does not
imply automatic rollback of business effects. A launch that raced closure returns its actual cancelled or unconfirmed
handle, never a prelaunch `NotStarted` claim. Storage remains available while
the command returns and persists its incomplete response and logs. Only the
main command's normal finally path invokes owned Subsystem shutdown; it always
signals hook completion, including when command or shutdown fails.

Cancel, await and output-limit termination share one synchronized completion.
Before TERM, the driver snapshots descendants and retains their handles even
when the root subsequently exits. It gives the tree the effective termination
grace, forces still-alive retained descendants, and bounds confirmation by that
runtime grace. Both root and retained descendants must be observed gone before
confirmed cancellation or timeout is published. Captures remain attached to
the actual result. A root already exited before any descendant observation,
failed tree inspection or unconfirmed cleanup produces
`ProcessExecutionTermination.TerminationUnconfirmed`, retaining the existing SBT
`Incomplete` interpretation. `cancelC` and `requestStopC` report an unconfirmed
stop as a failure while the main await retains that incomplete result and its
captures. Every active handle is still cancelled when one stop fails.

Runtime metadata uses the exact prefix `[cncf-runtime-event] ` followed by one
compact JSON object and newline. Its required fields are `state` and the actual
positive current JVM `pid`. The closed states are:

- `runtime-started`: the invocation's hook was registered;
- `cancellation-requested`: that hook requested stop;
- `settled`: stop was confirmed and both command persistence and normal owned
  shutdown completed successfully, followed by a final driver audit confirming
  no in-flight launcher task, active handle/process or recorded unconfirmed
  cleanup;
- `uncertain`: the bounded wait, stop confirmation, command or shutdown did not
  complete successfully.

Observer failures cannot change these effects. Runtime events contain no stack,
configuration, argv, environment or source. Ordinary completion removes the
hook; JVM shutdown already in progress does not make hook removal an error.
SIGKILL and unknown effects remain unknown with no automatic replay. SIGINT
handback/resumption is deferred; this adds no suspension protocol.

The final driver audit tracks actual launcher Callable lifetime, including work
that outlives a launch timeout, and preserves unconfirmed cleanup as a sticky
fact across ordinary resource-registry clearing. Successful stop of an empty
handle snapshot therefore cannot hide an in-flight launch or later unconfirmed
post-launch cancellation.

## Progress observations

`LocalProcessExecutionDriver` accepts an optional `ProcessExecutionProgress`
observer, defaulting to `ProcessExecutionProgress.none`. Existing constructor
forms remain available. The normal runtime factory installs
`ProcessExecutionProgress.stderr`, which writes one compact JSON object per
line after the exact prefix `[cncf-sbt-event] `.

Only SBT-tagged executions emit these observations. Each
`ProcessExecutionProgressEvent` contains the typed operation, logical workspace
ID, safe program identity and lifecycle state. The JSON operation is `compile`,
`test` or `test-only`; state is `launching`, `started`, `waiting`, `terminal` or
`uncertain`.

- `launching` precedes launcher submission; it has no PID or terminal fields.
  It includes `launchTimeoutMillis`, `executionTimeoutMillis` and
  `terminationGraceMillis` from the actual execution's effective limits. These
  non-authoritative budget observations describe the runtime's limits; callers
  still wait without an execution cutoff. They do not grant admission or change
  effect/result decisions. These fields are omitted from all other lifecycle
  events.
- `started` follows an actually returned process. PID is included only when
  process inspection supplies it.
- `waiting` marks the handle's actual wait, retaining the observed PID when
  available. Repeated awaits use the cached result without repeating events.
- `terminal` includes the existing result termination and elapsed milliseconds
  only after an actual process exit is observed. `exitCode` is the actual
  process exit, including when the result termination records timeout or
  cancellation. A confirmed prelaunch `NotStarted` is terminal with no PID or
  exit code.
- `uncertain` records launch uncertainty, observation failure, a result whose
  process exit is not yet observed, `TerminationUnconfirmed` even when root exit
  was observed, or closure of an unawaited handle. It never manufactures a
  terminal result or a safe-not-started fact.

Optional fields are omitted when unobserved. Events contain no argv,
environment, captured stdout/stderr, source text or workspace filesystem paths.
Observer reporting is best effort: nonfatal inspection or observer failures are
swallowed and cannot skip launch, change effect decisions or replace the actual
result. Progress is supplementary visibility rather than execution authority or
acceptance evidence.

## Executable specifications

`SbtExecutionDslSpec` retains current-context admission rejection, legacy
behavior, observed decoding, uncertainty after admission and consecutive typed
operations. `RuntimeProcessExecutionPlanCodecSpec` specifies V1–V3 round trips,
strict host-field refusal and explicit unsupported V4 rejection.
`LocalProcessExecutionDriverSpec` specifies real local zero/nonzero exits,
timeout, cancellation and cleanup, plus observer order, cached awaits,
confidentiality, observer-failure isolation, confirmed prelaunch observations
and uncertain launch deadlines. It also specifies automatic participation of
generic UoW processes in runtime stop, retained captures across stop, root-first
termination with a TERM-resistant grandchild, unconfirmed tree inspection and
the final audit of delayed launchers. `RuntimeCommandTerminationSpec` specifies
that stop, command persistence, owned shutdown and final driver confirmation
must all complete before `settled`, and that bounded waits, failed callbacks or
observer failures preserve the required completion semantics. No response-file
fixture supplies a product SBT result.

Validation, local publication and real Session operations remain the owning
development workflow's separate responsibility.
