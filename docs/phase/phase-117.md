# Phase 117: Typed Flutter Execution DSL

status=planned
planned_at=2026-10-28
owner=CNCF development
predecessor=[Phase 116](phase-116.md) (closed)
consumer=sm-workflow Phase 18
checklist=[Phase 117 checklist](phase-117-checklist.md)

## Goal and boundary

Provide `flutter_build`, `flutter_test` and `flutter_analyze` through typed CNCF
operations and the existing UoW/process lifecycle. Return actual Flutter results
for sm-workflow's tool-neutral validation. Keep `sbt_xxx` and SBT result semantics
unchanged; do not execute Flutter inside an SBT DSL or move Workflow validation
and acceptance policy into CNCF.

Source: [design handoff](../journal/2026/10/2026-10-28-flutter-execution-dsl-handoff.md).
This numbered plan replaces the former unnumbered plan. C1–C5 remain the work-unit
identities; P117 items in the separate checklist own completion tracking.

## Roadmap position and ownership

Phase 117 is the next numbered producer delivery after closed Phase 116.
It does not reopen 116 or replace unfinished corrections and other planned
producer work. Planning does not start a Session or change active work priority.
CNCF owns Flutter requests/results, DSL/UoW integration, execution and output
interpretation. Existing process management owns launch, deadlines, cancellation,
termination confirmation and log/artifact collection.

sm-workflow Phase 18 owns common validation results, check selection, progression,
repair/review and formal acceptance. Its W1 can align with C1; W2–W3 can proceed
alongside C2–C4. Flutter consumer integration W4 requires the C5 delivery, not a
nested Workflow implementation or completion of unrelated roadmap features.

## Implementation order

| Unit | Depends on | Work | Connected result |
| --- | --- | --- | --- |
| C1 | Existing typed SBT/UoW/process contracts | Specify Flutter target, parameters, attempt/result and diagnostics; align the consumer contract | Exact DSL signatures, SDK selection, initial build target/mode and output format are defined without arbitrary shell input |
| C2 | C1 | Add the three DSLs and typed UoW/interpreter connections | Each request reaches its corresponding Flutter operation through existing managed execution; constructing intent alone performs no external effect |
| C3 | C2 | Interpret test events, analysis diagnostics and build artifacts | Actual selection coverage, counts, diagnostics and generated outputs are returned; missing evidence is never fabricated |
| C4 | C3 | Qualify the producer with executable specifications, real Flutter and SBT regression | Required checks and independent review establish the bounded producer delivery |
| C5 | C4 | Deliver a usable revision/artifact and usage contract | Consumer receives the tested version, usage, environment requirements, evidence and remaining limitations |

C1 fixes concrete type names and signatures, including the proposed
`FlutterExecutionResult`, against existing CNCF abstractions. Build selects target
and mode; test selects all or explicit tests; analyze retains its configured
diagnostic policy. Select a real project/SDK and at least one supported build
target before qualification. Missing SDK or unavailable platform is an execution
prerequisite gap, not a fabricated product-test failure.

## Validation and completion

Cover successful/nonzero exit, pre-start rejection, timeout, cancellation and
unconfirmed termination through existing lifecycle facilities. Test interpretation
must cover failures, zero tests, all-skipped tests, unverified selection, missing
terminal events and late errors. Preserve those facts for the consumer's explicit
acceptance policy. Analysis distinguishes actual diagnostics from inability to
run. Build evidence must correspond to the selected target/mode; a stale artifact
after a failed build is not success evidence.

Use repository-standard Executable Specifications and required validation.
Check existing SBT DSL, selection and result behavior for regressions. Exercise
all three operations on the recorded real Flutter project/SDK, including the
selected build target. Fixture results do not establish real SDK operation or
support for untested platforms. Parse and aggregate logs in programs, retaining
raw evidence and concise diagnostics for AI consumers.

Exclude device/emulator lifecycle, distribution/signing automation, public
publication, generic plugin frameworks and nested Workflow management from this
Phase. Normal authorized artifact delivery retains its existing execution route.

## Cross-project delivery and completion

The consumer plan is `docs/phase/phase-18.md` in the sm-workflow repository.
CNCF completes implementation, compilation, required tests and independent review
as its own development work. Return the Git revision, artifact version/location,
DSL examples, SDK/target requirements, actual check/review evidence and limitations.
Identify any uncommitted or undeployed state; source edits alone do not complete C5.

Producer completion does not wait for the whole consumer Phase, and producer
results do not certify consumer acceptance. The consumer owns integration and
formal acceptance of its Workflow. Retain active Sessions, accepted work and
history; this plan neither starts execution nor declares implementation success.
