# Typed host-source read

The additive protected Behavior API reads one registered host source through
ordinary Process Execution admission and the existing UnitOfWork lifecycle:

```scala
HostSourceTarget.parseC("sm-workflow-codex-terminal")
HostSourcePayload.fromUtf8C(requestJson)
// Inside a Behavior:
host_source_read(target, payload)    // ExecUowM[HostSourceReadResult]
host_source_read_c(target, payload)  // Consequence[HostSourceReadResult], using UnitOfWork
```

## Rules

- **R1** A target is an exact logical identifier of 1–47 ASCII characters:
  `[a-z][a-z0-9]*(?:-[a-z0-9]+)*`. Parsing does not trim, lowercase, accept paths,
  or normalize an identity. The capability and safe program identity are both
  `host-source-<target>-read`, within the existing 64-character bound.
- **R2** A payload contains at most 65,536 strict UTF-8 bytes. Empty content is
  permitted. Invalid Unicode, including unpaired surrogates, is refused without
  replacement. Only the validated immutable bytes cross the process boundary.
- **R3** Package-owned `HostSourceRead.resolveC` constructs the dedicated internal
  `ProcessExecutionRequest`: derived capability, empty caller arguments, byte
  input, and defaults for every other field. It uses ordinary scoped
  `ProcessExecutionAdmission.resolveC`, preserving actual component grants.
  The resolved definition must have the exact derived identity and Template
  admission with precisely one template, the empty vector. It must grant no
  output artifacts, input files, resource trees, or working directory. Runtime
  fixed arguments remain permitted; callers cannot override them, the
  executable, environment, limits, or any file permission.
- **R4** `Complete(bytes)` requires `Exited(0)`, neither capture truncated, each
  capture's byte count equal to its retained byte length, and strict UTF-8
  stdout. Invalid complete stdout is a failure `Consequence`. Other terminal or
  incomplete capture results become `Failed(actualResult)`, except
  `TerminationUnconfirmed`, which becomes `Unconfirmed(actualResult)`.
  Nonzero exit, NotStarted, LaunchFailed, TimedOut, Cancelled, either output
  limit, and artifact limit never become Complete. There is no automatic retry.
- **R5** Both DSL methods execute the resolved-only existing `process_exec`
  path and `UnitOfWorkOp.ProcessExec`. Existing driver, limits, capture,
  cancellation, child ownership, resource cleanup and termination semantics
  remain authoritative. No new UnitOfWork operation or direct driver/process
  invocation is introduced. Applications do not construct a raw request for
  this source read or receive OS streams or process handles.
- **R6** Registration uses the unchanged `cncf.runtime-process-execution.v3`
  schema and existing `programs` and component `grants`. Append the fixed reader
  and its grant to the existing plan; preserve all Git/SBT declarations,
  workspace grants and host ceilings. An empty template is `[[]]`, whereas an
  empty template list `[]` supplies no admitted shape.

## Fixed one-shot registration

For the selected workstation, append this program to `programs` under the
existing `textus.subsystem.process-execution.plan` V3 plan:

```json
{
  "capability": "host-source-sm-workflow-codex-terminal-read",
  "identity": "host-source-sm-workflow-codex-terminal-read",
  "executable": "/Users/asami/src/development-workstation/common/codex/scripts/native-goal-terminal-source.py",
  "fixedArguments": [
    "--codex-executable",
    "/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex"
  ],
  "argumentTemplates": [[]],
  "limits": {
    "launchTimeoutMillis": 10000,
    "executionTimeoutMillis": 180000,
    "terminationGraceMillis": 5000,
    "stdinBytes": 65536,
    "stdoutBytes": 4194304,
    "stderrBytes": 1048576,
    "argumentCount": 1,
    "argumentBytes": 1,
    "artifactCount": 1,
    "artifactBytes": 1,
    "workAreaBytes": 1048576
  },
  "environment": {
    "HOME": "/Users/asami",
    "PATH": "/opt/homebrew/bin:/usr/bin:/bin",
    "LANG": "en_US.UTF-8"
  },
  "allowedArtifacts": [],
  "allowedInputFiles": {},
  "allowsWorkingDirectory": false
}
```

Add `host-source-sm-workflow-codex-terminal-read` to the existing capabilities
of the grant whose `componentId` is `org.simplemodeling.textus.SmWorkflow`.
Preserve that grant's other capabilities and every other component grant. If
that component has no grant, add its own grant with only this capability.
The paths and environment above belong to this selected host registration;
other hosts register their own fixed installed provider and executable paths.
No repository or live host configuration is changed by this document.

The host provider is a substantive fixed executable with managed Python
shebang and executable mode. It receives one bounded request on stdin, owns
its finite source dialogue, and returns one bounded response on stdout.
The Codex protocol, locator selection, historical-command attribution and
independent Human interpretation belong to the provider and consumer; CNCF
does not implement Codex RPC or interpret their JSON schemas. Existing
external-effect policy remains in force; this API grants no shell,
interpreter-forwarding, arbitrary argv or fallback execution authority.

This dedicated CNCF-internal request purpose was selected under
`P64-HOST-TERMINAL-SOURCE-CONTRACT@1` for repair
`DEV-SM-HOST-TERMINAL-INGEST-01`. Its development approval is separate from a
runtime Human choice; a Complete byte response alone does not authorize
Workflow recovery or continuation.

## Executable examples

| Example | Contract | Executable specification |
| --- | --- | --- |
| E1 | R1 exact bounded targets | `HostSourceReadSpec` |
| E2 | R2 strict UTF-8 and byte limits | `HostSourceReadSpec` |
| E3 | R3 component admission and default request fields | `HostSourceReadSpec` |
| E4 | R3 refusal of widened definitions and absent grants | `HostSourceReadSpec` |
| E5 | R4 failed and uncertain terminal receipts | `HostSourceReadSpec` |
| E6 | R4 complete captures and invalid UTF-8 | `HostSourceReadSpec` |
| E7 | R3–R5 direct/Free routing through actual ProcessExec | `ProcessExecutionDslSpec` |
| E8 | R3, R5 refusal before driver invocation | `ProcessExecutionDslSpec` |
| E9 | R3, R6 additive V3 empty-template round trip | `RuntimeProcessExecutionPlanCodecSpec` |
