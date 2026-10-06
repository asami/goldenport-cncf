# CompositeQuery v2 Contract

Status: accepted-focused; repository-full validation deferred to PHASE-69.7

Acceptance: [Phase 69.5 closure journal](../journal/2026/09/2026-09-29-phase-69.5-composite-query-v2.md).

CompositeQuery v2 composes a declared set of Query requests into one ordered,
bounded aggregate. It is an additive API; `CompositeQueryRequest`,
`NamedQuery`, `CompositeQueryPolicy`, `CompositeQueryResponse`, and the v1
`CompositeQueryEngine.execute` caller contract remain unchanged.

`CompositeQueryEngine.executeV2Blocking(request, targets, cancellation)` is
the compatible v2 facade. It delegates to an explicitly supplied, owning CNCF
`JobEngine`; composition creates neither an executor nor a scheduler and never
runs from an active Job worker.

## Rules

| Rule | Contract |
| --- | --- |
| CQ2-R1 | V1 constructors, defaults, unapply arities, execution behavior, and page consumers remain intact. V2 is additive and adapts only usable ordered values to the legacy response. |
| CQ2-R2 | A v2 request declares branches, targets, dependencies, deadlines, typed fallback, bounded policy, aggregate result, and typed diagnostic records. |
| CQ2-R3 | Version is exactly 2. Admission validates branch IDs, empty or duplicate declarations, references, targets, dependency edges and DAG, numeric bounds, supported fallback response, trace-job aliases, and nested active-job callers before submission. |
| CQ2-R4 | Ready DAG branches are selected in declaration order. `RequireValue` needs a succeeded or fallback predecessor; `AfterCompletion` waits only for terminal completion. Collect-all and explicit required fail-fast preserve every declaration in the result. |
| CQ2-R5 | The supplied JobEngine receives ordinary asynchronous `JobTask` submissions. Logical completion and physical query-body exit are tracked separately: a cancelled or timed-out running body retains its width reservation until it exits, and a body whose canonical durable callback is withheld retains its reservation until canonical settlement or logical timeout/cancel plus body exit. Job admission, cancellation, deadlines, and late completion are synchronized on a caller-thread coordinator monitor; no worker blocks awaiting another worker. |
| CQ2-R6 | Each branch retains caller security and correlation context while binding the chosen target component's scope, runtime, resources, UnitOfWork, response cell, and operation-evaluation buffer. Query-only target authorization remains authoritative. |
| CQ2-R7 | Local and registered Subsystem targets use a strict in-process V2 invocation/reply envelope with matching version, branch, and target IDs. Target aliases are immutable, explicit, non-empty, non-duplicate, and cannot be `local`. |
| CQ2-R8 | Branch terminal outcomes are `Succeeded`, `Fallback`, `Failed`, `Skipped`, `Cancelled`, and `TimedOut`. Fallback is a prevalidated constant response, allowed only for explicit `QueryFailed` or selected branch-local `TimedOut`; authorization, protocol, configuration, cancellation, and response violations never use fallback. |
| CQ2-R9 | Request and branch deadlines include queue time. Cancellation is caller-owned, inherited where available, and may control only captured Job IDs. The entry deadline includes preflight; a branch deadline starts when its coordinator reservation is created before provider submission and is checked again at canonical acceptance. Request expiry seals every incomplete branch without fallback. Terminal outcomes are immutable; already completed results remain. |
| CQ2-R10 | Valid admitted requests return a successful `Consequence` containing a complete ordered aggregate. Aggregate status identifies success, partial optional failure, required failure, cancellation, or timeout. `requiredSucceeded` is distinct from aggregate transport success. |
| CQ2-R11 | Retained values are limited to `Void`, `Json`, `Yaml`, `RecordResponse`, and scalar responses with a canonical UTF-8 size at or below the selected limit. `Http` and `Opaque` responses are rejected. Diagnostics retain only fixed IDs, status/code, timings, flags, and optional Job ID. |
| CQ2-R12 | Ephemeral is the default. Persistent diagnostics use existing Job lifecycle storage with empty input and parameters and `Void` task result; branch request and response payloads, raw conclusions, secrets, and content-bearing calltrees are excluded. A staged value is accepted only through canonical task settlement; an admission failure or withheld canonical callback becomes a safe terminal refusal or deadline, never accepted success. |

## Stable data model

`CompositeQueryV2Policy` defaults to at most four branches admitted in parallel,
30 seconds request duration, 64 branches, 256 KiB per retained response,
collect-all failure treatment, and ephemeral jobs. Validation constrains the
respective maxima to 16, 60 seconds, 64 branches, 256 dependency edges, and
256 KiB. A branch deadline is positive and cannot exceed the request deadline.

The response has exactly one terminal `CompositeQueryV2BranchResult` per
declared branch, in declaration order. A fallback remains visible as a
`Fallback` outcome and preserves the original fixed failure code. The safe
legacy adapter yields outer `Consequence.Failure` when a required result is not
usable; optional failures become bounded legacy diagnostics.

## Executable examples

| Examples | Behavior surface |
| --- | --- |
| E01–E02 | preflight validation, duplicate and cyclic graph rejection |
| E03–E05 | declaration ordering, dependency mode, sequential/parallel semantic equivalence |
| E06–E08 | bounded admission, collect-all, and explicit required fail-fast |
| E09–E12 | fallback eligibility, response bound/type, retained diagnostics, scheduler refusal |
| E13–E20 | cancellation, queued race, deadline, late completion, interruption, nested worker guard, own-ID control |
| E21–E27 | target directory/protocol, target authorization, isolated context cells, trace-job rejection, redaction |
| E28 | legacy adaptation and v1 consumer compatibility |

Remote transport, credential delegation, arbitrary URL routing, Saga behavior,
ordinary response persistence, and presentation composition are outside this
contract.

## Native persistent diagnostic mapping — JM69-10C8

status=focused_validation_passed
manifest=PHASE-69.7-JM69-10C8-implementation01@2
validation_status=subject8-REP010-ACC002-passed
acceptance_status=not-accepted

CQ2-R1..CQ2-R12 and the Phase69.5 accepted-focused/deferred repository-full
validation history remain authoritative. Explicit `PersistentDiagnostics`
selects the existing closed native diagnostic wrapper identity: task kind
`operation`, target kind `operation`, component and operation
`composite-query-v2`, no service, root relation, and own/per-task transaction.
The engine's actual task lifecycle records running then committed or failed.
This describes diagnostic-task settlement; it grants no Query mutation,
SQL-commit meaning or replacement of the target UnitOfWork. Default Ephemeral
retains its original `composite-query-v2`/`query` metadata, absent component,
relation and transaction metadata, empty request parameters and fixed summary.
The adapter's default persistence remains Ephemeral. No task-kind vocabulary,
wire/schema, native projection exception or alias normalization is added.

The public facade uses the owning `Subsystem.jobEngine`. The
[CQNT1..CQNT9 process companion](job-cross-process-acceptance-contract.md#compositequery-native-process-companion)
authors actual execution on the original configured native subsystem, with an
independently admitted read-only Query subsystem registered under immutable
`target-c8`. The ordinary native realtime worker pool runs Async branches. The
caller accepts the real successful scalar aggregate only through the existing
protocol/authorization/context/canonical callback path, then observes each
actual diagnostic ID through bounded `awaitResult` and the native engine's
settlement-monitor read barrier. A completed live diagnostic result is `Void`;
the independently signed native terminal result is `Succeeded(Absent)`.
Query request, password marker, selector, branch IDs, response scalar and
content-bearing calltree remain absent from the signed diagnostic body.

[CompositeQueryNativeCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/CompositeQueryNativeCrossProcessAcceptanceSpec.scala)
contains five semantic GWT leaves, including ordinary seeded shrinking over
alpha1..8/count1..2/two policies and a minimum24 actual JVM pairs. A fresh
MANUAL native reader consumes opaque persistent IDs and its independent
static access/recipe only; raw result remains absent, authorized management is
UnavailableAfterRestart and queryVisible remains body-free. No aggregate is
reconstructed or Query replayed. At initial authoring these oracles had no C8
run result; the current dated summary below now records selected validation. Original whole-JM69-10/CBD/Phase assurance and deferred full
validation remain mandatory.


## Current C8 selected validation — 2026-10-06

Initial authored_validation_pending/planned-not-run is historical. Parent-admitted
current subject8 selected Slice validation is complete:
[JM69-10C8-representative10-result.json@1](../../.codex-workflow/phases/PHASE-69.7/JM69-10C8-representative10-result.json)
records PH697-JM10C8-REP-010,8 completed suites/66 tests,66 succeeded, pending0;
[JM69-10C8-accumulator02-result.json@1](../../.codex-workflow/phases/PHASE-69.7/JM69-10C8-accumulator02-result.json)
records PH697-JM10C8-ACC-002,128 completed suites/1050 tests,1050 succeeded,
with only original SqliteDataStoreSpec pending1. Both invocation summaries use
cncf.sbt.invocation-summary.v1 and report failed0/aborted0/canceled0/ignored0,
compile_failed=false, compilerwarnings0, typed reader exit0/SBT exit0/wrapper
exit0, consumed permission and serial lock=released. Result records retain the
actual log/summary paths and completion markers.

The [C8 journal chronology](../journal/2026/10/2026-10-05-phase-69.7-composite-query-native-diagnostics.md#current-c8-validation-chronology--2026-10-06)
retains the original no-edit binding mismatch, five bounded pre-review corrections,
historical REP008 subject6 success, failed ACC001/CP003 and REP009 subject7,
external human dependency recovery and actual generated-only CP004 PASS. Latest
selected Cozy0.3.3-SNAPSHOT remains; compatible external dependency recovery
resolved this run without establishing publisher identity or importing/accepting
future Phase100 APIs. CQNT1..CQNT9, genuine process/property/native/canonical/
privacy/payment/cleanup oracles and all historical receipts remain unchanged.
This documentation projection consumes no repair unit or counter reset.

JM69-09 remains DONE; JM69-10 remains OPEN; Phase69.7 remains IN_PROGRESS.
Original whole-JM10 protected M2 independent Step review and local acceptance
commit remain pending. Actual admitted CBD provider -> Entity -> completed full
Review, independent fresh real recovery, remaining original matrix/positive
started-work choice, compatibility/accepted guidance promotion, predecessor
deferred-full validation, aggregate full CNCF/downstream validation, sole original
epoch1 comprehensive Phase review and final local Phase release remain owed.
Original Goal/authority/base/binding/epochs/history/ledgers/budgets/counters and
other authors' work remain. Parent owns diff/link/status verification, validation,
review and transitions; this editor runs no validation or self-review.
