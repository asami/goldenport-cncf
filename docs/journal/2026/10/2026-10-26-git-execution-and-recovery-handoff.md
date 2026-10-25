# Git execution defaults and RepositorySync recovery handoff

Date: 2026-10-26 (Asia/Tokyo)
Recipients: SimpleModeling development and sm-workflow development.
Status: specification/plan adopted; existing producer repair in progress.

## Incident and findings

The CNCF synchronization Session `8f27ea7624b104a81e0b97e8ebe5eb50` started,
then its first Advance failed with `git.execution.program-definition`.
SimpleModeling development is already repairing the repository-level Git route
in RuntimeProcessExecutionPlan/codec and related specifications. Preserve that
work; do not start a competing implementation or restart the Session.

The user clarified that typed Git operations and parameters are appropriate,
but mandatory command definitions for each operation are not. With no explicit
Git executable, use normal PATH Git. Merely grouping mandatory executable
definitions into a repository-level declaration does not satisfy that requirement.

Read-only investigation also found blanket disabling of Git configuration and
credential helpers, loss of stderr/parser detail in valueC failure projection,
pre-fetch advertised-tip equality in sm-workflow, and incomplete intermediate
commit/push/fetch recovery. These are source-level findings; they have not all
been established as causes of the actual missing-definition incident.

## Authoritative handoff

- CNCF: [defaults/diagnostics specification](../../../spec/git-execution-defaults-and-diagnostics.md)
  and [GIT-C1–4 plan](../../../phase/git-execution-route-correction.md).
- sm-workflow: `docs/spec/repository-sync-git-continuation.md` and
  `docs/phase/repository-sync-git-correction.md` in the sm-workflow repository.
  A matching dated handoff is recorded in its journal.

SimpleModeling development keeps ownership of the existing route correction,
relevant verification, normal runtime delivery and actual same-Session sync.
Integrate authentication/diagnostic work if it actually blocks this route;
otherwise deliver it next. Do not wait for all downstream recovery work before
making ordinary Git execution available.

sm-workflow development then consumes the producer, uses the fetched revision
as integration input, and recovers completed effects before executing unfinished
work. Reuse existing public continuation, process lifecycle, Journal/Evidence
and native Git facts. No new management hash, lock, retry ledger or AI polling.

## Continuity and evidence

Retain accepted work, existing Session/scope, full history, failures, repair
counts and verification. Reuse unaffected results. Do not repeat successful
commands/publication merely to reconstruct a report; do not forge receipts or
rewrite runtime storage. Existing source-history/RRC work remains independent.
Only the runtime resolves obsolete waits through its public adoption path.

This update changes documentation only. Checklists remain open and existing
implementation/test results are not replaced. The receiving owners should
return consolidated implementation, verification, runtime availability and
consumer-continuation outcomes, with concrete remaining gaps.
