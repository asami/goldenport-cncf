# Repository Git execution route repair

Date: 2026-10-26
Failure identity: GIT-ROUTE-20261026-01
RepositorySync Session: `8f27ea7624b104a81e0b97e8ebe5eb50`

The ordinary installed `sm-workflow advance-repository` route failed with
`git.execution.program-definition` at `ProcessExecutionModel.scala:1039`.
The canonical CNCF host V3 configuration supplied only 17 nonselected Git
declarations. RemoteTip, Fetch and other full-sync operations had no matching
definition, although ordinary Git worked and the application already emitted
typed operations. `RuntimeProcessExecutionPlan.createV3C` and
`ProcessExecutionPolicy.gitDefinitionC` required each operation to be explicitly
declared. The parent froze this cause and the repair strategy before editing.

The parent-decided protected contract adds strict
`cncf.runtime-process-execution.v5` repository family declarations. Each family
has host-owned executable selection/branches/remote/endpoint, required commit identity,
environment and limits. A public capability plus the same actual Component's
Manage workspace grant expands internally into the explicit closed list of 28
current nonselected Git kinds. Future enum additions require a deliberate
family contract update. All definitions retain the existing WorkspaceBinding,
typed arguments, admission, process driver and UoW lifecycle. Generated names
are internal and cannot be granted on the public wire. Selected declarations
and V1/V2/V3 remain unchanged; V4 remains rejected. No application-source
repair or generic argv route is introduced. PATH selection creates no new
grants; family authority remains explicit.

The subsequent user clarification, frozen by the parent within this same
repair, makes the Git executable override optional. Omission resolves ordinary
`git` from the declaration's effective PATH at host assembly; inherited PATH is
copied to generated environments. Explicit overrides remain truthful and fail
without fallback when invalid. Empty/relative PATH entries and unavailable Git
fail as configuration errors. The canonical local family omits `gitExecutable`
and retains its existing `/usr/bin:/bin` PATH. No new Session is created.
V5 codec conversion preserves the genuine structured plan-creator failure
diagnostic, including git/PATH unavailability. Legacy V1/V2/V3 diagnostic
conversion remains unchanged.

The local `.textus/config.json` migration backs up the complete original to
`target/sm-workflow/tasks/repository-sync-cncf-20261026-cbc74074-a744-4dbd-885b-9f9f9b179a68/git-route-fix/original-config.json`
before changing its inner process plan. It replaces the primary target's 17
nonselected declarations and their grants with one `sm-workflow-repository-git`
family granted only to `org.simplemodeling.textus.SmWorkflow`. The required
identity is Asami Tomoharu / asami.tomoharu@gmail.com. Family stdout is one MiB
to preserve the previous PendingDiff allowance. All 50 selected declarations,
six SBT declarations, seven workspaces and their grants remain. Outer host
configuration, including runtime definition, Goal, storage, imports and source
roots, remains intact. The existing Session and all history remain untouched.

Executable evidence added: GitRepositoryExecutionSpec E1/E2/E4 for the closed scope,
real temporary local Git route and authority denial; codec E3 for strict V5
round trips, malformed fields, namespace collisions, internal grant spoofing,
name bounds, optional executable defaults/overrides and failures, legacy
absent-operation denial and retired V4 rejection.

First focused validation: the registered SBT invocation terminated with exit 1.
Compilation succeeded; 94 tests ran, 91 passed and three failed. The retained
log is `.codex-workflow/disposable/a-a6e6ba3e-5a5c-4edd-95f1-4036be42e473/94270-20261025T185842Z.log`.
The V5 real-Git, default/override executable and codec cases passed. The parent
identified and froze the following verification corrections:

- GIT-ROUTE-T1: a canned fixture receipt reports `git-spec`; assert the admitted
  definition's actual selected-program identity instead, retaining success,
  all selected-operation denials and the exact execution count.
- GIT-ROUTE-T2: all 33 semantic GitExec wrappers succeed, while CommitSelected
  requires physical-selection and worktree probes before commit. Supply their
  ordered receipts and assert all exact typed values, physical selected paths,
  the 35-process kind sequence, capability and identical binding.
- GIT-ROUTE-T3: the committed SBT JVM policy already prepends the four-GiB
  `-J-Xmx4294967296`; retain exact bootstrap argv assertions with that prefix.
- GIT-ROUTE-W1: replace the new unreachable Option wildcard with explicit null
  handling, preserving the invalid-override failure.

Second focused validation (`focused02`): test compilation failed before any
tests ran. The parent-prescribed T2 assertions attempted to read
`private[processexecution]` Git metadata from the action package at lines 224
and 228. The same bounded mechanical correction replaces those inaccessible
assertions with exact public definition/request capability and safe program
identity sequences. All 33 typed results, selected physical paths and the
35-process count remain asserted. Identical binding is already directly
covered inside the processexecution package by GitRepositoryExecutionSpec and
GitExecutionSpec; production visibility remains unchanged.

Third focused validation (`focused03`): **PASS**. Compilation and all 94 tests
passed across GitRepositoryExecutionSpec, RuntimeProcessExecutionPlanCodecSpec,
GitExecutionSpec, GitExecutionDslSpec and
CncfRuntimeProcessExecutionInitializationSpec. The parent public typed reader
accepted this evidence. The retained log is
`.codex-workflow/disposable/a-7b98ef9f-f35a-4f6f-80e2-3726abb22bf5/95365-20261025T190622Z.log`.
The new main-source warning was fixed; pre-existing build-definition warnings
remain outside this bounded repair. Full release verification remains pending
at the owning release gate.

CNCF producer publication: **SUCCEEDED**. Normal `publishLocal` published
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, and the typed publication
evidence was accepted. The retained log is
`.codex-workflow/disposable/a-5f0c1647-9dd5-4a52-a09e-d7d5c7f54a1c/95940-20261025T190823Z.log`.
Producer qualification and publication do not establish installed application
or same-Session acceptance.

Installed delivery and same-Session acceptance: **PENDING**. Normal sm-workflow
`publishLocal` failed before runtime activation on five compile errors in
concurrent owner source-history work. The actual output is
`/Users/asami/src/dev2026/sm-workflow/.codex-workflow/disposable/a-2d7f1c8d-5adb-451e-a569-0a82ad9fa4f9/stdout.txt`.
The failure locations are GoalPhaseSourceHistoryNormalization.scala:308
(assignment as the first for enumerator), :221 (`HistoryEntry.identity`) and
:280 (`HistoryEntry.history`), plus
GoalPhaseNativeWorkContinuation.scala:1139 (`HistoryEntry` versus
`InstanceRecord`); the fifth error is the derivative `flatMap` error at :308.
No other owner's sources were edited by this repair.

RepositorySync Session `8f27ea7624b104a81e0b97e8ebe5eb50` remains the same.
There was no re-Start, and no successful fetch, push or synchronization is
claimed. Installed runtime activation and continuation remain parent-owned.

Existing Debt (Separate Follow-up): none admitted by this bounded repair.

## Installed activation and sync SBT binding correction

Failure identity: GIT-ROUTE-HOST-SBT-01.

The parent subsequently verified normal sm-workflow installation
`54ea51ef-6e14-405a-8938-532b6d2c9a23` and activation update
`9e0d06c6-dc27-4f00-820f-5f0b7976cd04` against the selected installation and
its `publication.json`. This supersedes the earlier delivery-pending state;
the prior failed publication evidence remains recorded above.

The same RepositorySync Session `8f27ea7624b104a81e0b97e8ebe5eb50` then
advanced through the normal installed route. `advance02` terminated with exit 1
and policy `sbt.execution.program-definition`; the actual diagnostic is retained
in `target/sm-workflow/tasks/repository-sync-cncf-20261026-cbc74074-a744-4dbd-885b-9f9f9b179a68/call-advance-repository-02/stderr.log`.
The configured repository validation check has identity `test`, ordinal 1 and
default kind Test. `WorkflowManagedProcessExecution.repositoryValidationC`
therefore requests SbtOperation.Test at
`sm-workflow-repository-validation-1`. The host declared only Compile at that
target, while its existing full Test declaration targeted validation-6. The
parent confirmed this exact binding mismatch and froze the configuration fix.

Before mutation, the complete current host configuration was backed up to
`target/sm-workflow/tasks/repository-sync-cncf-20261026-cbc74074-a744-4dbd-885b-9f9f9b179a68/git-route-fix/config-before-sync-sbt-binding.json`.
The inner process plan now has a seventh SBT declaration copied from the
validation-6 full Test declaration, with identical executable, environment and
limits, targeting `sm-workflow-repository-validation-1`. Its public capability
is `sm-workflow-sbt-sync-full-test` and safe identity is
`cncf-repository-sync-full-test`. Only the existing
`org.simplemodeling.textus.SmWorkflow` process grant receives this capability.
The six original SBT entries, 50 selected Git entries, repository Git family,
workspaces and existing grants remain. All outer configuration, including
repository validation policy and Goal, is retained. No ordinal, validation
policy or test limit was changed or reduced.

Same-Session acceptance remains **PENDING**. SBT admission failed, so no test
success is claimed for advance02. The call may have progressed through earlier
Git effects; this record does not assert that none occurred. Product checks
persist intent before execution, so an unknown retained validation may require
normal public recovery. This configuration correction neither edits Session
or datastore records nor replays an effect. The parent owns before/after
configuration comparison, public recovery and same-Session verification.
Full release verification remains pending.
