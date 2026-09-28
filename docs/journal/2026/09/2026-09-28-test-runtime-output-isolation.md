# CNCF test runtime output isolation

Status: RESOLVED — full and focused repair verified
Date: 2026-09-28
Incident: CNCF-TEST-OUTPUT-01

## Observation and causal evidence

The user reported print-driven snapshot output during CNCF tests and requested
an investigation of all such output. A fresh `sbt --batch test` succeeded with
3812 tests, 518 suites, 13 canceled, one ignored and 46 pending, while emitting
25 unrequested raw lines alongside normal ScalaTest reporting:

- 19 runtime clock, shutdown-drain, authorization and expected action-failure
  events, including timestamp and trace/execution identifiers.
- Three HTTP4s service-binding log lines and two HTTP startup announcements.
- One XML parser fatal diagnostic from intentional DOCTYPE rejection.

Invocation: `cncf-snapshot-print-diagnostic-3d8b86a7c88d40d2`.
The serialized SBT wrapper returned exit zero and released its shared lock.
The immutable command receipt is
`bde3dbdf42f84dd0f5d80c1b3504f4dd6efed3c9d5520145ff03980fce792d23`.

No active direct `println` call was found in the existing Scala test sources,
and the captured run contained no literal snapshot object dump. The confirmed
problem is runtime and parser output escaping from test scopes. The four
commented debugging prints in ArgsToStringScenarioSpec cannot cause output.
Java subprocess stdout/stderr fixture probes are intentional process contracts.

ServerOperationActivationSpec temporarily clears `textus.test` to exercise
real production Server startup. ComponentActivationLifecycleSpec does the same
for its completed-assembly activation case. Their runtime bootstraps select and
install the server console log backend; the original holder is not restored.
The immediate startup output leaks to the console, and the installed backend
also exposes subsequent aggregate authorization and plain-action failure events.
Both ObservabilityEngine and ActionEngine consult LogBackendHolder for those
events. The JCL unsafe-source case separately exercises a parser whose default
fatal-error handler prints to System.err before returning the expected failure.

## Frozen repair

Use a CNCF test-only RuntimeOutputCapture around the intentional runtime scopes,
capturing both Java stdout/stderr and Scala Console output. Retain the result
and captured text, preserve exception propagation, and restore the exact
original streams and shared log backend in finally. The existing sequential
test policy owns this scope; test-owned asynchronous work must be joined or
canceled before capture ends.

The two activation specs retain their runtime-flag restoration and all original
activation, readiness, cleanup and security assertions. Their existing final
restoration assertions also cover the shared log backend. The JCL spec captures
only its expected XML DOCTYPE diagnostic and retains every rejection check.

New capture lifetime scenarios verify direct Java and Scala output, a joined
child thread, and stream/backend restoration after an identical propagated
exception. Production runtime logging, CLI responses and parser behavior stay
at their existing contracts; the capture applies only to these test scopes.

Owned implementation:

- `src/test/scala/org/goldenport/cncf/testutil/RuntimeOutputCapture.scala`
- `src/test/scala/org/goldenport/cncf/testutil/RuntimeOutputCaptureSpec.scala`
- `src/test/scala/org/goldenport/cncf/cli/ServerOperationActivationSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala`
- `src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala`

ActionCallAggregateResolveSpec and ComponentLogicPlainActionExecutionSpec are
unchanged regression controls for the leaked log-backend state.

## First repair verification

### Test fixture contract

Specification ID: `test-runtime-output-isolation` (this journal section).
Scope marker: `phase:standalone-incident`; this repair is outside a numbered
development Phase. These rules govern only the test fixture introduced here.

- R1: A capture scope returns the body result and exact UTF-8 Java/Scala
  stdout and stderr, including writes through the installed stdout log backend.
  E1 exercises these routes and verifies the original binding identities.
- R2: Test-owned asynchronous writes completed and joined inside the capture
  scope remain in its returned output. E2 exercises a joined child thread.
- R3: Completion, including an exceptional body exit, restores the original
  Java streams and log backend; the original body exception propagates.
  E3 verifies identical exception and binding identities after failure.

Executable examples: `src/test/scala/org/goldenport/cncf/testutil/RuntimeOutputCaptureSpec.scala`.

Re-run the exact original `sbt --batch test`, inspect its saved stdout/stderr
for all 25 identified raw lines, and compare test/cancellation/pending counts.
Then run the six affected/control classes together with `testOnly`, including
RuntimeOutputCaptureSpec, and `git diff --check`.

Verification result:

- Exact reproducer `sbt --batch test`: exit zero, 3815 succeeded, 519 suites,
  13 canceled, one ignored and 46 pending; no compilation warnings. All three
  new lifetime examples passed. The original counts increased only by these
  three tests and their one suite.
- Raw output: 24 of the 25 originally identified lines disappeared. Runtime
  events, HTTP announcements and XML fatal diagnostics were absent. One
  HTTP4s service-binding INFO line remains in
  `src/test/scala/org/goldenport/cncf/http/McpStreamableHttpInteroperabilitySpec.scala`.
  This was the third HTTP4s line in the original reproducer, omitted from the
  frozen file list; it was not introduced by this repair. Its real loopback
  Ember server is independent of the captured activation fixtures.
- Nearest focused regression: `sbt --batch "testOnly
  org.goldenport.cncf.testutil.RuntimeOutputCaptureSpec
  org.goldenport.cncf.cli.ServerOperationActivationSpec
  org.goldenport.cncf.component.ComponentActivationLifecycleSpec
  org.goldenport.cncf.action.ActionCallAggregateResolveSpec
  org.goldenport.cncf.component.ComponentLogicPlainActionExecutionSpec
  org.goldenport.cncf.component.builtin.jobcontrol.JclJobControlComponentSpec"`:
  exit zero, 71 succeeded in six suites, no failed/pending/canceled/ignored
  tests and no warnings. In this separate-JVM `testOnly` path, two runtime
  startup events appear during ComponentActivationLifecycleSpec E2 and one
  authorization event appears in the unchanged aggregate control. E2's Server
  assembly is outside the scoped helper. The normal full-test run does not
  emit these three lines. Their removal is not claimed by the current repair.
- `git diff --check`: success. Both command receipts were verified against
  the exact current tree before this documentation-only result append; both
  serialized invocations released the shared SBT lock.

Full invocation: `cncf-snapshot-print-full-605cab809ea341b5`.
Full immutable receipt SHA-256:
`dafb65fd403f17df3aa3da92b9faea6587b0581d15cb2fbfb9119a87f3d3f82b`.
Focused invocation: `cncf-snapshot-print-focused-96699bdaf96c4d18`.
Focused immutable receipt SHA-256:
`a0cb74fd79e6a674f751a3d0673c8d0586fd401b77fd1b945279a775aef8aa20`.

First repair acceptance gap (resolved by the continuation below): the
zero-incident-output criterion remained unmet.
The cncf-diagnose-fix standalone one-batch boundary requires a developer decision
before expanding the repair to the omitted MCP fixture or E2 scope. Proposed
continuation: reuse RuntimeOutputCapture around the MCP `.build.use` lifetime
and E2 runtime assembly/cleanup, retain every interoperability and activation
assertion, restore global bindings, then verify both execution paths. Do not
silence the production logger or ScalaTest reporter globally.

## Separate ledgers

No incidental Hygiene or Development Candidate is admitted into this repair.
The first repair's remaining output belonged to this same incident; the
authorized continuation below resolves it.
Existing canceled, ignored and pending cases remain outside its boundary.
No commit or remote mutation is part of this diagnostic repair.

## Authorized continuation

The user instructed "修正を続けて" after the remaining full-test HTTP4s
line and three forked-test runtime events were reported. The continuation
adds McpStreamableHttpInteroperabilitySpec to the owned test fixtures and
captures ComponentActivationLifecycleSpec E2's complete setup/assembly/cleanup.
Both reuse RuntimeOutputCapture. Existing security, MCP protocol, activation
and cleanup assertions remain in place. E2 additionally checks the original
shared backend after the capture returns. Production logger and SBT settings
remain at their existing contracts.

Additional owned file:
`src/test/scala/org/goldenport/cncf/http/McpStreamableHttpInteroperabilitySpec.scala`.

## Final continuation verification

- Exact original reproducer `sbt --batch test`: 3815 succeeded, 519 suites,
  zero failures and compiler warnings; 13 canceled, one ignored and 46 pending
  remain at their original counts. Identified incident output: **25 → 0 lines**.
  A broader audit also found zero nonstandard raw output lines in this run.
- Seven-class forked `testOnly` regression: the same six classes from the
  first focused command plus
  `org.goldenport.cncf.http.McpStreamableHttpInteroperabilitySpec`;
  **72 succeeded**, seven suites, zero failed/canceled/ignored/pending tests,
  zero compiler warnings. Runtime events, HTTP4s binding output, HTTP startup
  announcements and rejected-XML diagnostics are all absent. The previously
  observed three focused-path event lines are gone.
- E2's original backend-restoration assertion passed, as did the capture
  normal/child-thread/exception cases, existing server readiness and shutdown
  checks, aggregate audit, expected action failures, JCL rejection checks and
  real-loopback MCP protocol interoperability.
- Both serialized commands exited zero with `lock=released`. Their official
  immutable receipts were verified against the exact same Git tree before
  this documentation-only final append. No code changed after validation.
- `git diff --check`: success after final journal update. No additional full
  suite, clean build or external integration run is necessary for this scope.

Full invocation: `cncf-snapshot-print-full2-8b7a9c068ec04b84`.
Full receipt SHA-256:
`b9c5eab5d4312adf2f0f7fad1b0d666d9a69d231ca91a55351ba273a205309f8`.
Focused invocation: `cncf-snapshot-print-focused2-242d1be0c5e54677`.
Focused receipt SHA-256:
`6798b9db5efc43da2f4de32fdee6617abf8046a5bc8ccf0df0b3bb6547fd69ea`.
Validated tree SHA-256:
`4e08a0f75209472f4a07cfc8ed7aace1c4e076df4c910feec147b93809572321`.

Current Incident Blocker: none. The reported test snapshot/state/startup
output is resolved in both validated paths. Normal ScalaTest reporting and
production logging remain available. Test-global stream capture continues to
rely on the repository's sequential test execution policy and completed
test-owned asynchronous lifetimes.

Working tree at repair completion: seven intended uncommitted paths, comprising four modified
Scala fixtures, two new Scala test utility/spec files and this journal.
No staging, commit, push, dependency change or other repository mutation was
performed during the diagnostic repair.

### Separate Hygiene observation

HYG-CNCF-20260928-TESTONLY-UNSAFE — open, nonblocking, not admitted into this
incident repair. A forked `testOnly` JVM on Java 25.0.4 with Scala 3.3.8 emits
four native warning lines about `scala.runtime.LazyVals` invoking
`sun.misc.Unsafe::objectFieldOffset`. The exact same four lines occur in both
the earlier six-class focused log and the final seven-class log, before any
test suite starts. The ordinary full-test path has no such raw warning lines.
Discovery date: 2026-09-28, continuation verification. Status: OPEN.
Location: `build.sbt`, `Test / testOnly / fork`; `.jvmopts` and the Scala
runtime boundary. Category: JVM compatibility; priority: P3. Risk: noisy
forked-test startup and future removal of the upstream Unsafe method.
This is a separate JVM/Scala compatibility and fork-configuration concern;
it is not captured or hidden by changing the test fixtures. Owner:
goldenport-cncf build/toolchain. Any follow-up must preserve the project's
runtime policy and genuine warnings rather than filter the reporter.

Development Candidate ledger: none discovered or implemented.

Detailed immutable receipt locators, raw output audits and Agent Usage Summary
are retained in task-local workflow evidence; the durable invocation and receipt
identities are recorded above.
