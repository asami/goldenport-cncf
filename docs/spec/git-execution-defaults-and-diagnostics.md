# Git execution defaults and diagnostics

Adopted: 2026-10-26. Normative target contract; delivery is tracked in the
[correction plan](../phase/git-execution-route-correction.md).

This contract refines [typed Git execution](typed-git-execution.md). It supersedes
requirements for a user-defined program per operation, mandatory executable
configuration, blanket disabling of normal Git authentication/configuration,
and diagnostics that expose only capture sizes. Other typed operation, target,
permission and managed lifecycle contracts remain in force. Adoption of this
specification is not evidence that the installed runtime implements it.

## GIT-1 — Operation definitions and executable selection

CNCF owns the supported `git_*` DSL, typed parameters and command construction.
A user need not register a command/program definition for every supported
operation. An absent per-operation definition is not, by itself, a
`git.execution.program-definition` error. Internal derived definitions may reuse
the existing interpreter and driver without becoming user configuration.

Use an explicit Git executable when configured. Otherwise resolve ordinary
`git` through the runtime execution environment's PATH, without a shell. Do not
require a new PATH value or an absolute executable path when the normal execution
environment already supplies them. A configured invalid executable fails with
its actual diagnostic; it must not silently select a different executable.
An unavailable default Git produces a truthful executable-resolution failure.

Reuse the admitted repository, branch, remote and operation parameters. A
repository-level declaration may supply overrides, but must not merely replace
many mandatory command declarations with one mandatory executable declaration.
Parameter availability and executable selection are distinct concerns.

## GIT-2 — Authority and existing execution owners

Executable defaults do not grant permission. The actual Component, target,
operation and selected path/remote authority remain checked through existing
CNCF admission. Do not expose arbitrary argv, introduce an alternate shell
route, or silently expand grants when a future DSL operation is added. Supported
operation scope belongs to the declared contract, not an unconstrained enum scan.

Use the current UoW, managed process driver, termination/cancellation and retained
result lifecycle. No new wrapper lock, permission ledger or per-command setup
workflow is required. sm-workflow supplies typed intent and consumes typed results;
it does not reconstruct CNCF's executable selection or Git parsing.

## GIT-3 — Normal configuration and authentication

The normal route must support the repository/operator's configured Git
authentication, including credential helpers, instead of unconditionally clearing
them or disabling all system/global Git configuration. Resolve the ordinary
execution environment at its existing runtime boundary; do not obtain it from
untrusted CAR-supplied overrides or copy unrelated environment secrets into logs.
Noninteractive behavior may report that credentials are unavailable, but must
not disable an already configured noninteractive credential mechanism.

Audit forced Git options by their concrete operation requirements. Keep options
needed for machine-readable output, non-force push and intended typed behavior;
do not add blanket switches solely to make a fixture hermetic. Hook, signing or
other behavioral changes must follow their actual contract, not be silently
bundled into an authentication repair. Fixture isolation is not a production
authentication policy.

## GIT-4 — Useful, truthful results

Retain operation/target, exit code, actual termination, typed parse failure reason,
and useful Git stderr diagnostics or an accessible retained evidence reference.
Capture byte counts alone are not a failure explanation. Keep actual stdout and
stderr available through existing evidence facilities, indicating any omitted or
truncated content without exposing credentials in public summaries.

Distinguish executable resolution, launch failure, Git rejection, malformed output,
timeout/cancellation and termination uncertainty. Parsing failure after a mutation
does not prove that the mutation failed. Workflow reconciliation belongs to
sm-workflow; CNCF must supply the real facts needed for it.

## Observable acceptance

- Default PATH Git executes supported typed operations with no user-maintained
  per-operation command definitions; an explicit executable takes precedence.
- Missing default Git and invalid explicit Git return distinct truthful errors.
- Existing credential-helper-based authentication works through the normal
  runtime route; permission denial and target restrictions remain effective.
- Git stderr and output-parser reasons reach the consumer or retained evidence.
- Existing driver/UoW cancellation and result collection are reused. This does
  not claim sm-workflow fetch or interrupted-effect recovery is complete.
