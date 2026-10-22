# Full-test failure repair

Date: 2026-10-22
Status: standalone repository-full validation passed; changes uncommitted

The developer requested repair of the actual repository-full test errors. This
is supporting source repair and standalone validation. The accepted Phase116
CC01–05 commits, existing GoalPhase Session, Plan, history, candidates and repair
counts are retained. These checks do not replay the native Advance or supply
formal Phase acceptance. Existing process-execution changes are preserved.

## Causes and repairs

- `ServerOperationActivationSpec` initialized a global runtime and shut down its
  subsystem without restoring `GlobalRuntimeContext.current`. Running this suite
  before the metrics, EventStore, Display update and Tag suites reproduced 38
  failures in 76 tests, including use of a released SystemNode datastore binding.
  Its fixture now restores the previous global reference in `finally`, alongside
  its existing property and output cleanup. Production lifecycle ownership is
  unchanged.
- `CommandExecuteComponentSpec` had the same leak at its legacy command/output
  helper, affecting suites later in the full order. That helper now restores
  both `GlobalRuntimeContext.current` and the legacy runtime object's prior
  context in `finally`, and closes its own capture streams.
- A complete inventory of runtime-creating suites found the same retained
  reference in `ComponentActivationLifecycleSpec`,
  `ComponentFactoryRuntimePlanActivationSpec`, `CncfRuntimeBootstrapTerminationSpec`
  and `ComponentRepositoryCarSpec`. Their existing per-test fixture boundaries
  now restore the previous runtime reference, including the companion reference
  for legacy command tests. The runtime-plan extra-component test also closes
  its owned Subsystem in `finally`. Existing CAR/WorkArea cleanup is retained.
- Runtime launch failure example E1 selected a local datastore without a path,
  but inherited the repository's real `.textus` datastore path. It therefore
  reached an unrelated missing-command failure. The test now exercises the
  existing legacy launch preflight with an explicit fixture cwd and isolated
  HOME, retaining the configuration category, required-path message and nonzero
  exit assertions. Fixture `.textus` and `.cncf` roots stop ancestor project
  discovery. This checks launch preflight and rendering, rather than claiming a
  complete public command invocation.
- Process-execution startup fixtures without a host plan now create empty local
  project roots and use an isolated test HOME. Previously an absent fixture
  `.textus` directory allowed ancestor discovery to import the developer's real
  process plan, contradicting the deny-by-absence fixture. Configuration-file
  test workspaces likewise own their project roots and temporary HOME, restoring
  the prior JVM property on exit.
- Display lifecycle generated inputs deliberately include unpaired UTF-16
  surrogates. Raw ScalaCheck failure clues caused JUnit XML UTF-8 output to throw
  `MalformedInputException`. Only diagnostic presentation now escapes surrogate
  code units; generators, shrinkers and semantic assertions are unchanged. A
  regression creates a genuine failing ScalaCheck counterexample and saves and
  reloads its escaped diagnostic through UTF-8 XML.
- The consumed local `goldenport-core_3:0.4.3-SNAPSHOT` JAR predated the current
  source's Conflict-to-HTTP-409 mapping. Bytecode inspection confirmed the missing
  branch. The clean producer at `bd552c6f725dfb7dd5da1059b7797b1e671b6e30`
  was refreshed with `publishLocal` through the registered runner; no core source
  was modified. The HTTP 409 expectation is retained.
- The named CAR startup resolver materialized the entire repository iterator
  with `toSeq.headOption`, evaluating a later remote repository even after the
  local canonical CAR matched. Managed-cache download correctly reported the
  subsequent HTTP 403, exposing this unnecessary lookup. The existing resolver
  now uses `nextOption()` to stop at the first match. The named-component
  regression substitutes a counted loopback repository returning 503 for remote
  fallbacks and requires zero requests together with the exact local CAR path.

## Validation

Changed source files in this repair:

- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeDiscoveryPart.scala`
- `src/test/scala/org/goldenport/cncf/cli/ServerOperationActivationSpec.scala`
- `src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala`
- `src/test/scala/org/goldenport/cncf/cli/RuntimeLaunchFailureSpec.scala`
- `src/test/scala/org/goldenport/cncf/cli/CncfRuntimeProcessExecutionInitializationSpec.scala`
- `src/test/scala/org/goldenport/cncf/cli/CncfRuntimeConfigFileSpec.scala`
- `src/test/scala/org/goldenport/cncf/cli/CncfRuntimeBootstrapTerminationSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/ComponentFactoryRuntimePlanActivationSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/repository/ComponentRepositoryCarSpec.scala`
- `src/test/scala/org/goldenport/cncf/display/DisplayMutationServiceLifecyclePropertySpec.scala`

No dependency repository source files were changed. This journal is additional
documentation; all other pre-existing dirty paths remain outside this repair.

The registered SBT runner completed the repaired ordered fixture check plus
runtime-launch and Display lifecycle laws: seven suites, 82 tests, all passed.
The requested HTTP suite used an incorrect package in this focused command and
was not selected; its actual `org.goldenport.cncf.http` suite is covered by the
subsequent full command. The parent admitted the actual successful typed result,
wrapper log and native-locking terminal summary.

- Focused invocation: `cncf-fullfix-6cf1f3f557264a4994f1d13fe34d1cdf`.
- Focused log: `.codex-workflow/disposable/a-f5677e1d-b112-46c6-a83b-7c1c366eff9c/21880-20261022T101307Z.log`.
- Core refresh: `cncf-core-refresh-aeba548be837459ca7cdbb19eb7f9749`, exit 0.
- Repository-full command: `sbt --batch test`, current working tree on
  `365250dc6057933b7fdf8f007864516ff0605f7e`, pending at this entry's creation.
- `git diff --check` passed after the test source edits.

The first standalone full check completed 875 suites and 6417 tests with 103
failures, four cancellations, one ignored test and 46 pending tests. It exposed
the additional command-helper leak and fixture root/HOME dependencies above.
It is a completed failed diagnostic check, not Phase acceptance. Its log is
`.codex-workflow/disposable/a-ee18a4d7-e72e-4d8e-950c-13a05e91d618/stdout.txt`.
These counts describe this current-source run; the earlier native run selected
728 suites and is retained as separate evidence.

After the command-helper and fixture isolation repairs, eleven focused suites
completed 202 tests: 201 passed and only named CAR resolution failed. The
EventStore, workflow, Display reference and management page tests in that
selection passed. The remaining CAR failure was repaired at the existing
resolver, preserving the fallback repositories and cache error reporting.

The subsequent focused startup configuration, canonical CAR resolver and HTTP
failure metadata check passed all 51 tests in three suites, including the actual
HTTP Conflict-to-409 assertion and zero-request local-CAR regression. Its
invocation was `cncf-fullfix-731e6717fec64c4581a7e7d3bab6aea1`; the parent
admitted the terminal successful result and log
`.codex-workflow/disposable/a-7481bf84-9ad9-4320-9de2-7f40d6dae669/24638-20261022T102439Z.log`.

A second full check completed the same 875 suites and 6417 tests with 6334
successes and 83 failures. Early Display and management-page failures were
resolved; later runtime-creating fixtures still contaminated the remaining
EventStore/Display/Tag/workflow suites. This led to the complete call-site
inventory and four additional fixture repairs above. Invocation
`cncf-fullfix-a1459ee2055b4e3ea1700f165fd458ae` is retained as a completed failed
check, with its log under
`.codex-workflow/disposable/a-4d026e99-88a5-4be6-91dc-6d0b5278b394/24917-20261022T102615Z.log`.

The separate test-output persistence requirement remains: use dedicated capture
storage and size transport envelopes for both streams plus JSON escaping and
metadata, without enlarging unrelated generic file-write ceilings. It is not a
reason to replay completed native operations or to suppress Given/When/Then.

The combined regression check after restoring all four late runtime fixtures
passed nine suites and all 174 tests. It runs the component activation,
runtime-plan activation, bootstrap termination and CAR suites before affected
EventStore, datastore observation, workflow, managed-event and generated Display
lifecycle suites. Invocation `cncf-fullfix-cce5d92beeae482a8f883ce426095deb`
terminated successfully with native SBT locking; the parent admitted its typed
result and retained log
`.codex-workflow/disposable/a-e3ed3a9a-1b18-43a6-ab0d-2aee2474cb03/25522-20261022T103217Z.log`.
A fresh repository-full `sbt --batch test` is running on this unchanged source.

## Final repository-full result

The fresh `sbt --batch test` finished successfully on the repaired working tree:
875 suites completed, zero suites aborted, 6417 tests run and all 6417 passed.
The report separately lists four canceled tests, one ignored test and 46 pending
tests. There are zero failed tests. SBT and wrapper both exited 0 with
`process=terminated serialization=sbt-native`.

- Invocation: `cncf-fullfix-c1cbcfcc291b45768053dbdb1dcc2349`.
- Typed result: `.codex-workflow/disposable/a-7edc68c7-b0cb-49a2-883a-ab754aff23e3/command-result.json`.
- Full stdout: `.codex-workflow/disposable/a-7edc68c7-b0cb-49a2-883a-ab754aff23e3/stdout.txt`.
- Retained log: `.codex-workflow/disposable/a-85637adf-ba97-4ae7-b236-5688e6701c19/25798-20261022T103557Z.log`.
- Terminal summary: the same log stem with `.summary.json`.
- Parent validation admission: `target/sm-workflow/tasks/phase-116-01a0f928-bec2-7703-8782-43e85f553dd7/fullfix-full-09-admitted.json`.

The final source passed `git diff --check`. No source was changed during the
successful full invocation; only this journal was updated. The validation
boundary is goldenport-cncf. No dependency source was modified, and no other
repository required full testing. There are no remaining full-test blockers in
this standalone repair. No separate Hygiene or Development Candidate was added.
No commit, push, CNCF publication or native GoalPhase continuation was performed.
The accepted Phase116 work, native Session/history/repair counters and the
pre-existing dirty process-execution work remain preserved. Formal Phase116
acceptance and its test-output persistence integration remain separate from this
successful standalone repository check.

## Formal continuation observation after repair

The owning host reread its exact Session routing and used the installed public
`describeGoalPhaseValidationRecovery` Operation, without Submit or Advance.
Installed selection `e887abd8-e58b-4cdd-9640-7c85f2e30995` reports the retained
known failed Phase test at source523/frontier530, attempt2/intent528, exit1.
It offers only a separately directed trial of the same retained candidate.
This does not admit the repaired working tree as a new candidate, and the old
failure must not be relabeled using the standalone success.

The actual response is retained under the existing task at
`phase-validation-post-repair-description-20261022-01/response.json`.
The parent also checked the current JUnit reports against the concrete cache
requirements: CAR11, SAR10, recovery16, repository81, loader ownership2, resource
lifecycle22 and runtime admission10, totaling152 passing related examples with
zero failures/errors/skips. WorkArea12 passed separately and is not counted as
shared-cache proof. The source example names and test bodies establish coverage
of same-JVM and separate-JVM lazy sharing, offline reconstruction, SNAPSHOT
old-reader continuity, process death, ordinary I/O failure, cheap hits and bounded
collection. This parent audit is not an independent review or native acceptance.

Retained parent audit:
`phase116-post-repair-requirement-audit-20261022-01.json`.
Prepared, unsent upstream handoff:
`phase116-repaired-candidate-continuation-handoff-20261022-01.md`.
The host asked for the required explicit authorization before sending this
additional request to the sm-workflow development chat. No live recovery
direction, source adoption, native check replay or formal closure was performed.

## Subsequent local artifact refresh

After standalone repair validation, the developer's earlier request to publish
the current version was fulfilled for the repaired source. Standard
`sbt --batch publishLocal` completed successfully at 20:29 JST and refreshed
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` in local Ivy. The original
publication request had stopped at an incorrect helper path before any SBT
start; it remains intact. This later refresh selects the newly repaired producer
subject and does not relabel that earlier stop or reuse its permission/result.
Detailed local publication evidence remains in the existing ignored task at
`post-fullfix-publishLocal-admitted-20261022-01.json`.

No source changes were made after the successful full test. No consumer build,
installed runtime switch, commit/push, native Submit/Advance or formal Phase
acceptance accompanied this artifact refresh. The pending same-Session candidate
adoption and independent integrated review/closure obligations remain open.


## Same-Session CC-06 adoption (2026-10-23)

The sm-workflow development chat delivered and independently reviewed the
normal failed-Phase corrective-Step route and the historical readers needed
for this retained execution. The new runtime was locally published and
qualified with focused tests, installed CLI fixtures and an unchanged copy
of the actual Phase116 history. This does not constitute CNCF acceptance.

The owner used the public description against the original CNCF root, received
the actual source523/frontier530 target and obtained the developer's genuine
AdoptRepairPlan answer for Plan@3. Public adoption returned accepted=true;
the following ordinary Advance issued ImplementStep CC-06 in the SAME
Session06016330fa63b5eac6097ff2b8ff42c2. AcceptedCC01-05, originalbase,
failed receipts, all historical results and repair counts are retained.

CC-06 explicitly owns the eleven source/specification repair paths listed
above and this journal. The existing repairs are reused after source
inspection; no production shutdown guarantee, generator input, semantic
assertion or Given/When/Then output is weakened. Unrelated source-read and
process-execution changes remain outside this Step's commit scope.

The October22 standalone6417-test success applies only to its original
snapshot. Current native Compile/cache-lifecycle checks, independent
ReviewStep, Step commit, repository-full validation, independent integrated
ReviewPhase and formal closure remain pending at this entry. Do not infer
current acceptance from the historical successful check or artifact refresh.
