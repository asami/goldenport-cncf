# Phase 34 - Process Execution Runtime

Stage Status:
- Current status: OPEN
- Owner: Phase 34 Process Execution Runtime
- Update rule: Update this block and `phase-34-checklist.md` whenever a stable
  work-item state changes. Close the phase only when every in-scope checklist
  item is complete or explicitly relocated.

status = open

## 1. Purpose

Phase 34 implements strategy item `9.29 Process Execution Runtime`. It adds a
CNCF-owned, capability-constrained way for components and trusted providers to
run an approved operating-system program through the same UnitOfWork,
authorization, Job/Task, cancellation, WorkArea, and observability boundaries
as other CNCF effects.

The immediate downstream consumer is the Textus AI Codex provider. This phase
delivers the provider-neutral CNCF runtime; it does not implement a Codex
provider or run a live Codex account.

## 2. Scope

- Promote the Process Execution request, result, policy, driver, and failure
  contracts from `docs/notes/process-execution-design.md` to normative design
  and specification documents.
- Add a capability-based process admission model. Callers select a registered
  `ProcessCapabilityId`; they never provide an executable path or shell text.
- Add `ProcessExecutionDriver` resolution through `ScopeContext` and runtime
  configuration, with deterministic fake-driver support for executable specs.
- Add `UnitOfWorkOp.ProcessExec`, interpreter support, and protected
  `process_exec` ActionCall DSL methods.
- Add WorkArea-confined input, working-directory, declared-artifact, quota, and
  cleanup behavior.
- Add a local driver that launches an argument vector without shell
  interpolation and enforces bounded stdout/stderr, timeout, cancellation, and
  process-tree termination.
- Integrate process cancellation with active Job/Task execution and record
  payload-safe CallTree/metrics diagnostics.
- Provide a provider-neutral consumer handoff contract for Textus AI and other
  future component providers.

## 3. Boundaries

- New component/provider code uses `process_exec`; it must not call
  `ProcessBuilder`, raw `Process`, shell text, or an execution driver directly.
- `ProcessExec` is a new CNCF effect. It does not rename, reinterpret, or
  automatically migrate legacy `ShellCommandExec` and `ShellCommand` callers.
- Process admission is capability-specific and deny-by-default. General
  wildcard process execution is not a normal component capability.
- A program definition, its executable location, allowed arguments, environment
  inheritance, limits, and WorkArea policy are runtime-owned. They are not CAR
  request input.
- Captured input/output, stderr, environment values, credentials, raw host
  paths, and artifact contents are confidential by default and must not enter
  normal diagnostics.
- Job cancellation must signal and terminate an active process. No new Command
  execution mode or independent process scheduler is introduced.
- Container, remote, and live Codex drivers; process retention; legacy-shell
  migration; and BPM/process orchestration remain outside this phase.

## 4. Active Work Stack

- A (DONE): PE-01 - Freeze normative Process Execution design and static
  contracts.
- B (PLANNED): PE-02 - Add capability, request/result, limits, and runtime
  program-definition models.
- C (PLANNED): PE-03 - Add ScopeContext driver resolution and deterministic
  fake-driver execution support.
- D (PLANNED): PE-04 - Add UnitOfWork `ProcessExec` and protected ActionCall
  DSL integration.
- E (PLANNED): PE-05 - Implement local driver lifecycle, bounded streams, and
  timeout handling.
- F (PLANNED): PE-06 - Implement WorkArea confinement, artifacts, quotas, and
  cleanup.
- G (PLANNED): PE-07 - Integrate Job/Task cancellation, observability, and
  confidentiality.
- H (PLANNED): PE-08 - Verify the provider-neutral consumer contract and close
  Phase 34.

## 5. Development Items

- [x] PE-01: Freeze normative Process Execution design and static contracts.
- [ ] PE-02: Add capability, request/result, limits, and runtime
  program-definition models.
- [ ] PE-03: Add ScopeContext driver resolution and deterministic fake driver.
- [ ] PE-04: Add `UnitOfWorkOp.ProcessExec` and `process_exec` DSL support.
- [ ] PE-05: Implement the local driver lifecycle and bounded stream handling.
- [ ] PE-06: Add WorkArea-confined input/output artifacts, quotas, and cleanup.
- [ ] PE-07: Integrate Job/Task cancellation, CallTree, metrics, and
  confidentiality.
- [ ] PE-08: Verify the consumer contract, document the handoff, and close the
  phase.

Detailed task tracking and acceptance evidence are in
`phase-34-checklist.md`.

PE-01 completed Jul. 17, 2026. The canonical documents are
`docs/design/process-execution-runtime.md` and
`docs/spec/process-execution-runtime.md`; the original note remains an
explicitly non-normative handoff.

## 6. Completion Conditions

Phase 34 closes only when:

- Process Execution has normative design/specification documents and executable
  specifications;
- all component-visible requests select a registered process capability rather
  than an arbitrary executable or shell command;
- direct and Free/UnitOfWork paths execute the same `UnitOfWorkOp.ProcessExec`;
- a fake driver verifies policy, cancellation, confidentiality, and driver
  inheritance without requiring a host program;
- the local driver runs only an argument vector, enforces limits, drains both
  output streams safely, and terminates on timeout or cancellation;
- all input, working, and declared artifact paths remain WorkArea-confined;
- Job cancellation reaches an active process and a terminal process result
  cannot be overwritten by a later cancellation;
- normal CallTree, metrics, and structured failures do not expose confidential
  process content or raw host paths;
- legacy shell facilities, remote/container execution, and live Codex provider
  integration remain explicitly deferred or relocated.

## 7. Source Note

The initial design source is `docs/notes/process-execution-design.md`. It is
non-normative. PE-01 promoted its settled contracts into
`docs/design/process-execution-runtime.md` and
`docs/spec/process-execution-runtime.md`; those canonical documents govern
subsequent runtime implementation.
