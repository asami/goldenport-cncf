# Managed Process and Codex Provider Handoff (2026-07-17)

status=handoff
updated_at=2026-07-17
tag=process-execution, shell-command, textus-ai, codex, ar-06

## Position of This Record

This journal entry records the investigation state and implementation handoff
for the CNCF process boundary needed by Textus AI AR-06. It is non-normative.
Any settled process contract must be promoted to `docs/design/` and `docs/spec/`
with executable specifications before implementation is claimed complete.

## Driver

Textus AI Phase 1 was reopened after Codex CLI execution was confirmed as a
primary purpose of the runtime extension. The intended integration is an
explicit `codex` AiRunner provider that invokes `codex exec` with a read-only
sandbox, an explicit workspace, bounded input and output, and schema-constrained
record generation.

Textus AI must not construct JVM processes directly. CNCF owns the process
execution boundary, while Codex retains ownership of its authentication state.

## Confirmed Current State

- `UnitOfWorkOp.ShellCommandExec` is CNCF's existing process-execution
  operation.
- `UnitOfWorkInterpreter` delegates that operation to `ShellCommandExecutor`.
- The current `UnitOfWork` supplies `LocalShellCommandExecutor` as the local
  executor.
- `ShellCommandExec` names a shell-command abstraction; the operation itself
  does not require a shell interpreter. The executor determines the actual
  launch mechanism.
- The current operation does not yet express the controlled lifecycle contract
  required for an untrusted or bounded external-tool integration: explicit
  root confinement, mandatory capture limits, cancellation outcome, managed
  temporary files, and safe observability policy.

## Investigation Conclusion

Creating a permanent parallel `ManagedProcessExec` UnitOfWork operation is not
the preferred direction. It would duplicate the existing CNCF process boundary
and obscure which operation new components should use.

The preferred direction is to retain `ShellCommandExec` as the canonical
UnitOfWork operation and evolve its command/executor contract into a managed
process capability. Existing callers may retain compatibility behavior during
migration, but new external-tool integrations, including the Textus AI Codex
provider, must select the managed profile explicitly.

The process capability should remain independent of Codex. Codex is the first
consumer and supplies a concrete command policy, not a framework-level process
type.

## Candidate Managed Profile

The eventual managed command contract should provide the following semantics:

- argument-vector execution without shell interpolation;
- an explicit working root and a verified working directory contained by it;
- bounded stdin, stdout, and stderr capture;
- timeout, cancellation, start failure, output-limit, and non-zero-exit
  outcomes represented separately;
- allowlisted executable and environment policy, with no credential value
  publication;
- managed input, schema, and output files with scoped lifetime and cleanup;
- CallTree attributes limited to safe summaries such as executable identity,
  termination category, exit code, byte counts, and elapsed time.

The profile must not record prompt text, generated text, API keys, bearer
tokens, account identity, or credential locations in ordinary metadata or
CallTree attributes.

## Codex Consumer Shape

Textus AI should request the managed profile through the CNCF internal DSL and
form a fixed `codex exec` argument vector. The provider must set a read-only
sandbox, ephemeral-session default, and explicit working directory. Structured
record generation additionally requires CNCF-managed schema and output paths
for Codex's schema and last-message options.

The adapter must not use a raw `ProcessBuilder`, construct a shell command
string, or rely on a live Codex CLI in executable specifications.

## Open Design Questions

1. Whether the managed profile belongs inside `ShellCommand` and its directive
   model or beside it as a strongly typed process-execution request while
   retaining `ShellCommandExec` as the UnitOfWork operation.
2. How `ShellCommandExecutor` exposes cooperative cancellation and how the
   local executor terminates a timed-out process and any descendants.
3. Whether output-file materialization and cleanup belong to the executor or a
   dedicated CNCF managed-work-area capability.
4. Which environment names may be inherited, replaced, or injected without
   exposing values through observability or structured failures.
5. How executable authorization is resolved from runtime configuration without
   allowing a CAR to select arbitrary host commands.

## Promotion and Implementation Order

1. Promote the selected managed-process boundary to a CNCF design document.
2. Define static result and failure vocabulary in a CNCF specification.
3. Implement the contract through `ShellCommandExec` and add fake-executor
   executable specifications for lifecycle and confidentiality behavior.
4. Add the Textus AI `codex` provider against that contract, with no live Codex
   dependency in tests.
5. Record AR-06 validation evidence only after CNCF and Textus AI tests, CAR
   lint, and review pass.

## References

- `textus-ai/docs/phase/phase-1.md`
- `textus-ai/docs/phase/phase-1-checklist.md`
- `textus-ai/docs/notes/car-review-ai-runtime-design.md`
- `textus-ai/docs/journal/2026/07/2026-07-16-car-review-ai-runtime-requirements.md`
- `docs/journal/2026/07/execution-determinism-handoff-2026-07-15.md`
