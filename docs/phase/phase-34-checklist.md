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

Status: OPEN

- [ ] Add `UnitOfWorkOp.ProcessExec` and interpreter execution.
- [ ] Add protected `process_exec` ActionCall DSL methods for direct and Free
  execution paths.
- [ ] Preserve authorization, UnitOfWork, and action observability chokepoints.

Acceptance evidence:

- Specs prove direct and Free paths construct equivalent process-execution
  intent and denied admission never reaches the driver.

## PE-05: Local Driver Lifecycle

Status: OPEN

- [ ] Launch an approved executable with an argument vector and no shell
  interpolation.
- [ ] Drain stdout and stderr concurrently with independent bounded capture.
- [ ] Enforce launch/total timeout and graceful/forced termination policy.
- [ ] Return non-zero exit as a provider-interpretable terminal result.

Acceptance evidence:

- Controlled local-program specs prove stream draining, timeout, cancellation,
  and no leaked process/thread after terminal completion.

## PE-06: WorkArea and Artifacts

Status: OPEN

- [ ] Allocate execution-scoped WorkArea input, working, and output paths.
- [ ] Reject traversal and symlink escape before launch and artifact collection.
- [ ] Return only declared, bounded artifacts with logical identities.
- [ ] Run cleanup for success, failure, timeout, and cancellation.

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
