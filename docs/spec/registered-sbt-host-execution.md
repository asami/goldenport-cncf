# Registered SBT host execution

Status: public runtime and UnitOfWork contract, Oct. 20, 2026.

## Typed admission observations

`SbtExecutionAttempt` is a public closed ADT with:

- `Observed(result: SbtExecutionResult)`: an actual process observation, retaining
  the existing SBT outcome and TestOnly qualification semantics;
- `AdmissionRejected(target, operation, conclusion)`: the actual current-context
  admission failed before execution, retaining its original `Conclusion` without
  manufacturing a process receipt;
- `Uncertain(target, operation, conclusion)`: admission succeeded, but the managed
  execution returned a failure and external execution cannot be inferred absent.

Protected `sbt_compile_attempt`, `sbt_test_attempt` and `sbt_test_only_attempt`
construct one `UnitOfWorkOp.SbtExecAttempt` each. They perform no admission or
launch during construction. Recording and planning classify that one occurrence
as External. The owning interpreter resolves the actual Component, shared
workspace Manage grant, independent process capability and fixed definition in
its current UnitOfWork context. After admission, it uses the existing process
calltree, WorkArea, handle registration, Job cancellation and resource cleanup.
Only the actual admission failure becomes `AdmissionRejected`; a failure at or
after driver start becomes `Uncertain`. There is no automatic retry, new generic
ProcessExec occurrence or new ProcessExecutionRequest construction site.

Existing `sbt_compile`, `sbt_test`, `sbt_test_only`, `SbtExec` and
`SbtExecutionResult` retain their APIs and behavior. The attempt API does not
turn a nonzero exit, timeout receipt, cancellation receipt or output overflow
into build success. Observed TestOnly results pass through the existing
`SbtExecutionAdmission` selected-report decoder.

## Explicit runtime selection

`cncf.runtime-process-execution.v4` contains exactly the V3 root fields
`schema`, `programs`, `grants`, `workspaces`, `workspaceGrants`, `sbtPrograms`,
`gitPrograms`, plus `registeredSbtHost`. The added object contains exactly
`requestFile` and `responseFile` strings. Both identify distinct absolute,
normalized sibling files beneath a directory named `target`; neither path may
contain a symbolic-link segment. They are selected by the owning host for one
CLI invocation. V4 reuses actual V3 authority admission and fixed declarations.
The routing object grants no Component capability or workspace permission.

V1, V2 and V3 wire shapes and encoding remain unchanged. They do not accept the
new object and continue to select the ordinary runtime-owned local driver.
Only explicit V4 installs `RegisteredSbtHostDriver` around that local driver.
SystemNode owns the decorator and its local delegate. Non-SBT starts delegate
through the same local-driver overload. Tagged SBT has no direct-launch fallback.
The JVM does not invoke Codex tools or an executable shim.

One driver instance numbers tagged SBT starts monotonically from 1 in memory.
Start 1 uses the exact configured paths. Start N greater than 1 appends `.` and
decimal N to each configured filename in the same parent. Non-SBT starts do not
consume a number. Every request has its own UUID and actual numbered response
path. Earlier request and response files remain intact. The host knows the base
paths and next expected number, handles one issued check at a time, and never
scans directories. A fresh owning CLI invocation supplies fresh base paths.
This naming rule introduces no lock, registry, management hash or retry protocol.

## Issued request

After validating fixed typed intent and obtaining the actual canonical cwd from
the admitted private `SbtProgramMetadata`, start atomically publishes one complete
UTF-8 JSON request at the selected request path. An existing request file is a
configuration error and remains untouched. Publication first writes the complete
bounded bytes to a private temporary sibling, then atomically creates the request
path as a hard link to that file without replacing an existing path. Concurrent
publishers of the same path therefore retain exactly one complete winning request;
each publisher deletes only its own temporary path. Unsupported hard links or a
concurrent collision fail closed without a replacement fallback, shared lock or
retry. The initial existence check provides diagnostics, not exclusion. The wire
is bounded to 1 MiB.

The request has exactly these fields:

| Field | Meaning |
| --- | --- |
| `schema` | `cncf.registered-sbt-request.v1` |
| `requestId` | New UUID for this actual issued request |
| `workspaceId` | Admitted logical workspace |
| `operation` | `compile`, `test` or `test-only` |
| `programIdentity` | Admitted safe program identity |
| `workingDirectory` | Actual admitted canonical checkout cwd |
| `executable` | Actual fixed runtime executable |
| `arguments` | Actual CNCF `effectiveArguments` vector |
| `environment` | Actual fixed runtime environment map |
| `executionTimeoutMillis` | Admitted finite execution deadline |
| `stdoutBytes`, `stderrBytes` | Independently admitted capture bounds |
| `responseFile` | Actual selected response path for this sequence |

Components cannot supply argv, environment, cwd, executables or host-file paths
through typed SBT intent. Scenario narrowing retains its existing fixed command
rendering. Host coordination uses these exact files, without stdout protocol
output. The external workstation host invokes the registered
`cncf_command_runner` through the normal SBT distribution; CNCF retains ownership
of target and command interpretation. Independent project executions may run
concurrently. SBT retains its native boot and dependency-cache locks; there is no
shared machine-wide application lock.

## Trusted host response

The response is trusted runtime/provider input produced only from genuine
registered-runner terminal evidence correlated with the exact issued CNCF
request. It is never an AI-supplied operation result. The JVM validates its
binding and structure; it does not authenticate an untrusted file writer.

The strict JSON object has exactly:

- `schema`: `cncf.registered-sbt-response.v1`;
- `requestId` and `programIdentity`, equal to the issued values;
- `termination`: `exited`, `timed-out`, `cancelled` or `output-limit-exceeded`;
- `exitCode`: an integer required only for `exited`;
- `exceededStream`: `stdout` or `stderr`, required only for
  `output-limit-exceeded`;
- `stdoutBase64`, `stderrBase64`: bounded captured bytes;
- `elapsedMillis`: a nonnegative integer;
- `processTerminated`: exactly `true`, proving actual terminal completion of the
  issued process;
- `runner`: exactly `logicalRole: "cncf_command_runner"`, nonempty `agentId`,
  `model: "gpt-6-luna"`, `reasoningEffort: "medium"`;
- nonempty `commandRequest` and `commandResult` strings identifying the actual
  typed registered-runner request and result accepted by the trusted producer.

Unknown fields, duplicate keys, malformed JSON/UTF-8/base64, crossed identities,
missing runner or process-termination proof, invalid terminal fields and captures
exceeding either admitted limit are failures. Response reading is bounded by
the base64 capture sizes plus 64 KiB of metadata. Validation occurs once at
the response-input boundary and the handle caches the resulting observation.
There is no `NotStarted` host response. Output overflow maps to the actual
`ProcessExecutionTermination.OutputLimitExceeded(stream)` with that capture
marked truncated; it cannot report success.

`awaitC` waits only until the admitted execution timeout. An absent, unreadable
or malformed response yields a failure stating that external execution is
unknown, which the attempt interpreter preserves as `Uncertain`. Absence of a
file establishes no prelaunch or safe-replay fact. A genuine bound `timed-out`
or `cancelled` response can establish its corresponding observed process
terminal, because it includes actual runner completion and process-termination
proof. A shared lock-release claim is not terminal evidence and is not an
accepted response field.

Cancellation and decorator closure stop local waiting and retain uncertainty;
they do not claim the external runner was stopped. SystemNode closes the owned
local delegate. UnitOfWork continues to own and reclaim its WorkArea and handle
resources. Host files are retained for the owning host and are separate from
that WorkArea cleanup. This contract adds no application lock, host arbitration,
automatic replay or safe-retry inference.

## Executable specifications and qualification

`SbtExecutionDslSpec` specifies actual UnitOfWork admission rejection, unchanged
legacy behavior, observed decoding and failures after admission.
`RuntimeProcessExecutionPlanCodecSpec` specifies V1–V3 compatibility and explicit
V4 round trips and refusals. `RegisteredSbtHostDriverSpec` uses explicitly
test-owned provider observations to specify exact requests, two consecutive
checks, real fixture bytes, crossed/malformed/unproven responses, output limits,
timeout, cancellation, local delegation and closure.

These authored fixtures are not installed registered-runner evidence. An actual
registered-host integration, validation, publication and real Session operations
remain the owning development workflow's separate responsibility.
