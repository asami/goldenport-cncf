# Job Operation Metrics Contract

status=normative
phase=69.7
step=JM69-09
slice=JM69-09C2-public-operation-metrics
implementation_acceptance=authored-current-validation-and-independent-review-pending

This original C2 Slice adds completed public-call observations to the
[Job Operations Contract](job-operations-contract.md) and the separately canonical
[Job Operational Metrics Contract](job-operational-metrics-contract.md).
It preserves the original Phase authority, Goal, base and epoch1. Authoring does
not close JM69-09, JM69-10 or Phase 69.7.

## R1 Eleven closed completed-result cells

Scope is exactly `job.operation`, metric name is `calls`, and labels are exactly
`operation` and `outcome`. The complete table, including snapshot order, is:

| operation | outcome | Result classification | errorCount |
| --- | --- | --- | --- |
| submit | accepted | Success | 0 |
| submit | failed | Failure | count |
| control | applied | Success with changed=true | 0 |
| control | replayed | Success with changed=false | 0 |
| control | failed | Failure | count |
| page | returned | Success | 0 |
| page | failed | Failure | count |
| tasks | returned | Success | 0 |
| tasks | failed | Failure | count |
| timeline | returned | Success | 0 |
| timeline | failed | Failure | count |

Each normally returned public Consequence contributes exactly one observation.
These are final result classifications. A failed control can follow applied
native effects: Sync Retry with timeoutMillis=0 queues the retry and emits
`job.retry.submitted` before the returned wait failure. Failed never proves zero
effects. Successful empty pages, hidden identities and missing/None task or
timeline results are `returned`, preserving the original visibility semantics.

## R2 Strict identical-result wrappers and finite storage

The private JobOperationMetrics recorder has exactly eleven zero-initialized
Long cells. It retains no supplied result, context, request, job identity,
Conclusion, exception or payload. Strict `observeSubmission`, `observeControl`
and `observePage` arguments classify already completed results and return the
identical supplied Consequence instance. Control examines only response.changed.
There is no normalization or invented failure fallback. A throw propagates
unchanged and records nothing; there is no catch/recover/finally metric hook.

The internal PageOperation enum is closed to Page, Tasks and Timeline, at offsets
5, 7 and 9. A null enum, if internally supplied, returns the supplied result
unchanged without recording. No arbitrary-string operation/outcome input API
exists. Each recognized cell uses the existing lifecycle recorder's production
saturatingIncrement primitive and remains at Long.MaxValue when saturated.
There is no reset, eviction, deduplication, user-keyed registry or global recorder.

Only `_record` and `snapshot` synchronize on this recorder. Neither enters State
nor calls an external provider or user callback. Snapshots contain only positive
cells in the table order. Counts are cumulative, errors are failed-only; duration
count/total remain zero and min/max/average remain None. No inventory gauge is
collected. Reads add no clock, ID, body, timer or native effects.

## R3 Native ordering and exactly one public observation

Both public submit overloads observe their actual outer returned result once.
The three-argument overload delegates to a private final-engine submit body
containing its original entire native expression. The two-argument overload
preserves its outer quiesce check, synchronized lifetime preflight, flatMap and
default-option getter order, then calls that private body. Both original lifetime
preflights and both original quiesce checks remain. The delegation bypasses the
instrumented public overload to prevent double counting. Admission, input/option
validation, IDs/time, quota and lifetime payment, native writes, debug metadata,
publication, Event, queue/timer/body and error-release order remain unchanged.

Control wraps exactly authorize(jobId,request).flatMap(_control). Policy remains
before request shape, deadline and identity lookup, including null requests.
There is no label derived from request.command, identity or failure text.

Only queryPage, queryManagementTasks and queryManagementTimeline wrap their
existing JobManagementReader result. The original snapshot remains by-name to
the reader and inside its original validation boundary. Shape, cursor/filter,
caller, snapshot, offset and authorization ordering, metadata and continuation
tokens remain intact. No eager snapshot, second validation or second policy call
is introduced. Raw/detail/result/tree/task-detail/await/annotation/cleanup and
same-Job task APIs, internal retry/drain/admission helpers and lifecycle hooks
remain uninstrumented by this recorder.

## R4 Exact State lifetime and independent snapshots

The recorder resides in the existing State body. Constructor fields, defaults,
Product arity and all public signatures remain unchanged. Exact same-State
engine owners share both cumulative histories; bootstrap and shutdown do not
reset them. Fresh State starts empty and recovery reconstructs no old counts.
The existing optional JobEngine capability defaults to None for unsupported
engines; None is not authoritative zero usage or healthy readiness.

InMemoryJobEngine returns Some(lifecycle snapshot ++ operation snapshot), with
lifecycle first and operation second. The recorder locks are acquired separately
and never held together. This vector does not claim a globally atomic cross-facet
snapshot and requires no State lock. Lifecycle remains independently canonical
with fourteen facts, 56 cells and five labels.

## R5 Closed labels and preserved diagnostic authority

All metric label names and values are the constants in R1. No tenant, subject,
session, credential, trace/span/correlation, job/task/attempt/Event identity,
parameters, notes, summary, error text, payload, path, provider, hash or reference
is admitted to point names, keys, values or retained metric state. This boundary
does not redact or change original authorized Job models or semantic Events.
Those original security and diagnostic contracts retain their authority.

Counters are completed observed calls, not durable acknowledgements, resource
credits, successful notification delivery, trace/audit evidence, availability or
readiness. They authorize neither work nor capacity.

## R6 Actual metrics catalog, runtime and OTEL consumption

The existing catalog retains all 22 scopes and appends job.operation after
job.lifecycle, with exactly Vector(operation,outcome) label keys and cumulative
non-authoritative State-lifetime completed-call semantics. The deferred actual
subsystem engine supplier appends both recorders' exact points to the original
runtime snapshot before the existing exporter. Four-argument callers, unsupported
None, original registry sources, jobs-before-security order, supplier exception
identity, Actions, service/HTTP/public Products and response schema remain.

The actual Sync Nil fixture contributes three lifecycle points and one operation
point. OTEL emits exactly three cncf.job.lifecycle.facts.count metrics followed
by cncf.job.operation.calls.count, cumulative temporality2, monotonic sums, exact
five versus two prefixed label keys and bounded values, and no gauge. Existing
count/error rendering is reused; no exporter or endpoint is added.

## Executable specifications

| Example / scenario | Given / action / expected evidence |
| --- | --- |
| E1 / PM1 | Empty actual recorder and eleven independent result tuples; strict helper observations, including None/null enum; identical instances, exact cells, failed errors, zero durations and private-marker exclusion |
| E2 / PM2 | Finite closed observations, seed697097202/minimum24 ordinary shrinking; actual helpers; independent multiset, <=11 cells, count conservation and pure snapshots |
| E3 / PM3 | Two physical actors with bounded gates/join and captured failures; each2000 submit Success/Failure helpers; exact2000 counts and failed errors, finally cleanup |
| E4 / PN1 | Manual native engine and counted deterministic providers/EventStore; default Async Nil drain and explicit Sync Nil; accepted2 without double count, original three Events/Job, None results and twenty pure reads |
| E5 / PN2 | Fresh engine baseline; null task list/option/input-option and quiesced submit; failed4, original structured failures, no records/Events/credits/providers/timer effects |
| E6 / PN3 | Async native Job and real policies; null refusing request before shape, Suspend/Resume/Cancel apply/replay and sentinel throw; applied3/replayed3/failed1, original Events/credits/status and uncounted identical exception |
| E7 / PN4 | Native Cancelled Job; zero-timeout Sync Retry; failed final result with actual retry Event, paid facts and pending Submitted work, real drain adds no public cells |
| E8 / PN5 | Two Sync records and counted read policy; real limit1 cursor continuation and task/timeline metadata, invalid shapes and missing identity; exact ordered IDs/counts/cursorEnd, policy precedence and per-facet results |
| E9 / PN6 | Actual owner/foreign contexts and denying policy; owner-only page and hidden/missing readers; successful empty/None returned, zero failed, preserved subjects/models/Events and private exclusion |
| E10 / PN7 | Two actual same-State owners and fresh State; first submit/read/shutdown, second submit/read; shared cumulative completed calls, fresh zero and unchanged original Events |
| E11 / PN8 | Real finite1..12 actions, seed697097201/minimum24 shrinking; Sync/default Async submit, null failure, valid/invalid pages/tasks/timeline/Cancel/cursor; every return domain and independent multiset, Event conservation, pure reads and finally shutdown |

PM1..PM3 live in [JobOperationMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/JobOperationMetricsSpec.scala).
PN1..PN8 live in [JobOperationMetricsIntegrationSpec](../../src/test/scala/org/goldenport/cncf/job/JobOperationMetricsIntegrationSpec.scala).
The eight migrated ON1..ON6 and MC1..MC2 retain their C1 identities and all
original GWT/native assertions in
[JobOperationalMetricsIntegrationSpec](../../src/test/scala/org/goldenport/cncf/job/JobOperationalMetricsIntegrationSpec.scala)
and [MetricsJobOperationalIntegrationSpec](../../src/test/scala/org/goldenport/cncf/component/builtin/metrics/MetricsJobOperationalIntegrationSpec.scala).
They extend exact snapshots with operation cells while lifecycle/Event assertions
remain scope-specific and OTEL/catalog assertions remain exact.

## Current assurance and mandatory remaining scope

Predecessor C1 actual PH697-C1-REP-004 passed three suites12/12; actual
PH697-C1-ACC-001 passed61 suites519/519, zero failed and one existing pending.
Compiler warnings were zero; the known JVM Unsafe baseline is separate.
These retained receipts validate that C1 tree, not changed C2 source.
C2 current representative4-suite and complete63-suite accumulator validation
remain pending, as does independent review. C1's five fixture substitutions in
two Specs remain M2, pending independent complete original JM69-09 Step assurance.

Remaining original C includes authoritative queue/child/attempt/storage/expiry/
corruption/payload metrics, all correlation/redaction/audit entities and configured
health/readiness/refusal. Original D includes production lifecycle/write-bridge/
maintenance ownership, integrity, backup/restore/migration checkpoints and actual
owner-close/fresh-runtime behavior. All original JM69-10 real two-process and
Textus CBD Support fresh-runtime fixtures remain mandatory. Phase69.4–69.6
ancestry/deferred audits, complete independent JM69-09/10 Step reviews/local
acceptance commits, aggregate final full validation, original epoch1 sole Phase
full review, version/closure evidence and final local release remain open.
Original CPBs, Step1/3 and Phase0/3 repair histories and
HYG-PH697-SQL-SIZE-01/HYG-PH697-ROOTA-VALIDATION-PROJECTION-01 remain intact.
JM69-09 and Phase69.7 remain IN_PROGRESS; JM69-10 remains OPEN. No successor,
push, publication or deployment is authorized by this Slice.
