# SBT test output policy

Date: 2026-10-22
Status: implemented; focused validation passed (82/82); not yet published

The Phase116 CC05 commit succeeded as
`365250dc6057933b7fdf8f007864516ff0605f7e`. Its following required repository-full
SBT process stopped at the configured 2097152-byte stdout ceiling with
`OutputLimitExceeded(Stdout)`, exit143. The receipt converter rejected the
truncated capture; the original Workflow attempt remains unknown rather than
a complete successful or ordinary failed check. The existing Session, Plan,
candidate, history and counts are retained. No new Phase is started.

The preceding development full-test log was 18245828 bytes. The user adopted
64 MiB stdout / 8 MiB stderr defaults for SBT tests and explicitly requested
property configuration. This supporting process-runtime change is separate
from the already accepted managed-cache candidate and does not supply Phase
acceptance or authority to replay that unknown check.

Canonical numeric ordinary properties:

- `cncf.sbt.test.stdout.maximum-bytes`: default 67108864.
- `cncf.sbt.test.stderr.maximum-bytes`: default 8388608.

Use the current UnitOfWork's existing resolved parameters for Test and TestOnly,
including exact scenarios. Missing properties use defaults; present values must
be positive integral numeric Long byte counts. No text coercion, ambient JVM
property lookup or environment fallback is added. Each effective stream ceiling
is the minimum of selected property/default and explicitly admitted host ceiling.
Compile, Git, grants, request arguments and non-capture limits are unchanged.
Complete bounded output retains normal Given/When/Then visibility and existing
termination/truncation evidence; there is no automatic retry.

Authored files: typed-sbt-execution specification, SbtTestOutputParameters,
SbtExecution admission, SbtTestOutputParametersSpec and SbtExecutionDslSpec.
Current local host Test/TestOnly declarations and both ordinary properties are
set to 64/8 MiB; previous host config is retained under the existing task in
sbt-test-output-policy-host-before-20261022-01.json. Other in-progress
process-execution changes remain untouched and uncommitted.

Focused validation passed: SbtTestOutputParametersSpec, SbtExecutionDslSpec,
SbtScenarioExecutionDslSpec and LocalProcessExecutionDriverSpec via registered
cncf_command_runner and native SBT locking. All 82 tests in four suites passed;
none failed, aborted, canceled, ignored or remained pending. Both SBT and wrapper
exited 0, with `process=terminated serialization=sbt-native`. The parent admitted
the actual typed receipt and retained log/summary after checking the selected
repository, HEAD, scope and exact arguments.

- Invocation: `sbt-test-output-policy-9521c5b02850456596f0d66c9c9c3e3e`.
- Log: `.codex-workflow/disposable/a-a30d5b57-9de1-437e-b9de-3f7ae0d31c71/16510-20261022T084002Z.log`.
- Summary: same directory, `16510-20261022T084002Z.summary.json`.
- Admitted receipt: existing Phase task's `sbt-test-output-policy-validation-admitted-20261022-01.json`.
- `git diff --check` passed. Existing build/Scala warnings were non-failing.

The new policy is source-tested and has not been published into the installed
sm-workflow runtime. Its current explicit host capture ceilings have been
updated, but consumption of the new ordinary properties by that installed
runtime is not established. These focused results do not establish a successful
repository-full run or formal Phase closure. The separate actual public
initial validation recovery target was source523/frontier524, repository-full
attempt0 on the CC05 commit. Its subsequent actual authorization and execution
are recorded below; the original unknown attempt remains unchanged.

## Authorized full-test attempt and receipt capacity

The user explicitly authorized one new repository-full attempt in actual message
`01a162d8-5252-77f1-9bfc-a2157411f48d`. The host independently observed that
message and retained its actual content and source under the existing task.
Normal `prepareEvidence` produced the genuine StartupImport, and normal
`recoverGoalPhaseValidation` accepted
`p116-repository-full-validation-recovery-human@1` with
`outcome=new-attempt-authorized`. An initial configuration mistake used a file
URI where the existing import property requires a path; startup refused before
the operation. The host corrected only that property, retained the error and
completed ordinary recovery without inventing another authorization or attempt.

One subsequent ordinary Advance launched SBT PID 17565 and observed its actual
terminal `Exited(1)`, exit 1 after 162685 ms. The owning helper also terminated,
exit 2, after the installed CLI exited 1. There was no output-limit termination.
Instead, the CLI failed during mechanical receipt encoding with
`Mechanical receipt encoding is absent or exceeds its bound`; its response file
is empty. No valid native test receipt, check acceptance or Phase closure is
claimed. The actual runtime error and termination events are retained in
`phase-validation-recovery-advance-20261022-01/stderr.log` under the existing task.

The mechanical receipt has a separate capacity policy from process capture.
CNCF's generic default is 1 MiB and GoalPhase source selects 16 MiB by default;
the complete encoded envelope includes both histories and the captured payload.
The host now explicitly selects the already supported ordinary property
`workflow.mechanical-receipt.max-bytes=67108864` (64 MiB). The previous complete
host configuration is retained as
`phase-validation-receipt-capacity-host-before-20261022-01.json`. This changes no
schema, codec maximum, native store, historical receipt or retry authorization.
The finite 64 MiB envelope is not a guarantee for arbitrary maximum captures
and accumulated histories.

Diagnostic inspection selected XML reports whose mtime is at or after the
owning call's response-file creation time. It parsed 722 suites containing 5474
reported tests, 73 failures, 0 errors and 38 skipped entries. One additional
updated XML report is malformed and 148 older files were excluded. Thus these
are partial report-derived diagnostic counts, not an authoritative whole-run
summary or an imported native result. HTTP/launch, datastore lifecycle and
display/entity failures remain to be assessed; they are not all attributed to
managed caching. Exact selected files and actual failure details are retained in
`phase-validation-full-test-report-diagnostics-20261022-01.json` under the task.

The actual public description after this failure returns `decision-required`
for source523/frontier526, check repository-full ordinal6, attempt1/intent526,
on the unchanged CC05 HEAD/tree and Plan2. The original Session, history and
repair counts remain selected. The consumed attempt0 authorization does not
authorize attempt2; only a fresh genuine Human direction for this actual target
permits a later ordinary Advance. No second full-test execution has occurred.

## Second authorization: native result retention succeeded, full check failed

The user explicitly answered `call_0666742dae5f41fabfecf2c4f86b0426` in actual
message `01a162e7-f02d-74c1-9d9b-73094ca274ac`. Normal passive preparation and
independent provisioning produced
`p116-repository-full-validation-recovery-human@2`; public recovery returned
accepted/new-attempt-authorized. Exactly one subsequent ordinary Advance ran
the same repository-full check with the revised receipt capacity.

SBT PID 18332 actually terminated `Exited(1)`, exit 1 after 164315 ms. This time
the installed CLI terminated exit 0 and returned a valid, complete 17835343-byte
JSON response: `kind=Wait`, `condition=phase.Decision`, validation source523,
same candidate, `outcome=failed`, and an actual typed failed Test receipt.
Thus native result retention is established; this latest result is a KNOWN
FAILURE, not another unknown check. Earlier unknown attempts remain unchanged.

The actual SBT summary is 728 suites completed, 0 aborted; 5471 tests run,
5396 succeeded, 75 failed, 4 canceled, 1 ignored and 37 pending. It also reports
a forked JUnit XML harness MalformedInputException. This supersedes no old
results: the preceding 73-failure partial XML inspection described the earlier
run and excluded a malformed report. This run's stdout/stderr strings are
13012074/4685150 UTF-8 bytes, both below their admitted capture limits.

The outer native-goal-command helper exited 2 because the response exceeds its
separate fixed 8 MiB read bound, after native exit 0. Its actual response and
runtime events remain under `phase-validation-recovery-advance-20261022-02`.
The parent parsed the retained public response for diagnostics, without a new
Advance, result import, fabricated Submit or altered native row. Exact stdout
and stderr copies and a compact diagnostic summary are retained under the task
as `phase-validation-full-test-{stdout,stderr,summary}-20261022-02` with their
respective log/json extensions.

The returned current repair projection is automatic0/ordinary0/limit10; this
does not erase historical accepted CC04 repair count3/10. All five accepted
commits, base, Session and existing dirty sources remain preserved. The current
source route assigns automatic failed-check repair only inside Step Validation;
whole-Phase failure stops at Decision and supplied no semantic repair request.
An exact producer handoff, not yet sent or adopted, is retained as
`phase116-known-failed-full-check-handoff-20261022-01.md`. No successful full
validation, integrated Phase review or formal closure is claimed.
