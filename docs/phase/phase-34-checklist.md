# Phase 34 - Process Execution Runtime Checklist

This checklist tracks Phase 34 implementation and evidence. The summary
dashboard is `phase-34.md`.

## PE-01: Normative Contract

Status: DONE (Jul. 17, 2026)

- [x] Promote the Process Execution ownership, capability, WorkArea, lifecycle,
  cancellation, and confidentiality boundaries to
  `docs/design/process-execution-runtime.md`.
- [x] Define request/result/termination/artifact/failure structure in
  `docs/spec/process-execution-runtime.md`.
- [x] Define the relationship to legacy `ShellCommandExec`, Job/Task execution,
  and Textus AI consumer ownership.

Acceptance evidence:

- Design and specification documents define all component-visible contracts
  before implementation is considered stable.
- The source note remains historical/non-normative and links to the canonical
  documents.

Evidence: `docs/design/process-execution-runtime.md` and
`docs/spec/process-execution-runtime.md` were added Jul. 17, 2026.

## PE-02: Capability and Program Model

Status: DONE (Jul. 17, 2026)

- [x] Add `ProcessCapabilityId`, request/result, termination, limits, input,
  output-declaration, and artifact models.
- [x] Add runtime-owned `ProcessProgramDefinition` and effective policy
  resolution.
- [x] Reject arbitrary executable paths, shell command strings, unconstrained
  environment inheritance, and unbounded limits before driver invocation.

Acceptance evidence:

- Specs prove deny-by-default capability admission and monotonic limit
  tightening.

Evidence: `ProcessExecutionModelSpec` passed with capability grammar,
WorkArea-relative path, deny-by-default resolution, monotonic limit, and
request-shape coverage.

## PE-03: Driver Resolution and Fake Execution

Status: DONE (Jul. 17, 2026)

- [x] Add inherited `ProcessExecutionDriver` resolution through `ScopeContext`.
- [x] Add deterministic fake-driver/test-profile support.
- [x] Preserve driver selection and policy-safe identity in diagnostics without
  exposing request payload values.

Acceptance evidence:

- Specs prove runtime inheritance, child-scope override, unavailable-driver
  failure, and no live program dependency for normal tests.

Evidence: `ProcessExecutionDriverSpec` passed with generated inheritance,
explicit override, unavailable-driver, deterministic result, and cancellation
coverage. The fake profile does not create a host process.

## PE-04: UnitOfWork and Internal DSL

Status: DONE (Jul. 17, 2026)

- [x] Add `UnitOfWorkOp.ProcessExec` and interpreter execution.
- [x] Add protected `process_exec` Behavior DSL methods for direct and Free
  execution paths.
- [x] Preserve capability admission, UnitOfWork, and action observability
  chokepoints.

Acceptance evidence:

- Specs prove direct and Free paths construct equivalent process-execution
  intent and denied admission never reaches the driver.

Evidence: `ProcessExecutionDslSpec` passed with generated direct/Free intent
equivalence, denied admission before driver invocation, resolved-only UoW
payload, unavailable-driver coverage, and safe success/failure CallTree output.

## PE-05: Local Driver Lifecycle

Status: DONE (Jul. 17, 2026)

- [x] Launch an approved executable with an argument vector and no shell
  interpolation.
- [x] Drain stdout and stderr concurrently with independent bounded capture.
- [x] Enforce launch/total timeout and graceful/forced termination policy.
- [x] Return non-zero exit as a provider-interpretable terminal result.

Acceptance evidence:

- Controlled local-program specs prove stream draining, timeout, cancellation,
  and no retained process or worker lifecycle after terminal completion.

Evidence: `LocalProcessExecutionDriverSpec` passed with a runtime-owned JVM
probe. It verifies literal vector arguments without shell interpolation,
concurrent stdout/stderr capture, non-zero exits, execution timeout,
idempotent cancellation, independently bounded stream capture, launch timeout,
and local active process/handle cleanup.

## PE-06: WorkArea and Artifacts

Status: DONE (Jul. 17, 2026)

- [x] Allocate execution-scoped WorkArea input, working, and output paths.
- [x] Reject traversal and symlink escape before launch and artifact collection.
- [x] Return only declared, bounded artifacts with logical identities.
- [x] Run cleanup for success, failure, timeout, and cancellation.

Evidence: `UnitOfWorkInterpreter` allocates and finally closes an execution
WorkArea for every `ProcessExec` invocation. `LocalProcessExecutionDriverSpec`
proves WorkArea-backed stdin, scoped working-directory execution, and logical
artifact projection without raw host paths. `ProcessExecutionWorkAreaSpec`
proves declared-only artifact collection, traversal/symlink rejection,
per-artifact and aggregate WorkArea quotas, and root cleanup.

Acceptance evidence:

- Specs prove confinement, quota enforcement, declared-output filtering, and
  finally-safe cleanup.

## PE-07: Job/Task and Diagnostics

Status: OPEN

- [ ] Register active process handles with execution cancellation.
- [ ] Propagate Job cancellation to process-tree termination idempotently.
- [ ] Add payload-safe CallTree, observability, and runtime metrics.
- [ ] Keep prompt/input/output/environment/credential/raw-path values absent
  from normal diagnostics.

Acceptance evidence:

- Specs prove Job cancellation reaches a process handle, terminal races preserve
  actual completion, and confidential values are absent from diagnostics.

## PE-08: Consumer Handoff and Closure

Status: OPEN

- [ ] Define the provider-neutral consumer handoff for a `codex-cli`-style
  capability without adding a Codex implementation to CNCF core.
- [ ] Run focused and full CNCF regression suites.
- [ ] Review the final design/spec/docs and record deferred scope.
- [ ] Update strategy/phase status and commit validated work.

Acceptance evidence:

- Textus AI can implement its provider adapter from the CNCF contract without
  needing a parallel shell/process API.
- Live Codex installation, account, and network access are not required by
  normal Phase 34 specs.
