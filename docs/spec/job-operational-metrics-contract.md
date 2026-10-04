# Job Operational Metrics Contract

status=normative
phase=69.7
step=JM69-09
slice=JM69-09C1-operational-lifecycle-metrics
implementation_acceptance=authored-focused-validated-independent-review-and-Step-acceptance-pending

This C1 boundary supplements the [Job Operations Contract](job-operations-contract.md).
It exposes cumulative observations of existing engine lifecycle facts through the
actual runtime metrics service. Authoring is not successful validation, independent
review, Step acceptance, a Step commit or Phase completion.

## R1 Closed fact taxonomy and labels

`job.lifecycle` has metric name `facts` and exactly five labels: `family`,
`operation`, `outcome`, `persistence`, `run_mode`. The first three labels come only
from this complete whitelist. No other Event name contributes a cell.

| Existing Event name | family | operation | outcome | Error fact |
| --- | --- | --- | --- | --- |
| job.submitted | admission | submit | accepted | no |
| job.running | execution | run | started | no |
| job.succeeded | execution | run | succeeded | no |
| job.failed | execution | run | failed | yes |
| job.delayed.scheduled | queue | delay | scheduled | no |
| job.delayed.enqueued | queue | delay | enqueued | no |
| job.retry.immediate.submitted | retry | immediate | submitted | no |
| job.retry.delayed.scheduled | retry | delayed | scheduled | no |
| job.retry.delayed.submitted | retry | delayed | submitted | no |
| job.retry.submitted | retry | control | submitted | no |
| job.recovery-required | recovery | checkpoint | required | yes |
| job.cancelled | control | cancel | applied | no |
| job.suspended | control | suspend | applied | no |
| job.resumed | control | resume | applied | no |

Persistence is exactly `persistent` or `ephemeral`; run mode is exactly `sync` or
`async`, translated from the existing typed record fields. Null or unrecognized
inputs and arbitrary or high-cardinality names are ignored without retaining
their values. Observation accepts only the Event name and the two typed facets.

## R2 Finite cumulative counter semantics

Each recorder owns exactly 56 zero-initialized Long cells: fourteen facts times
two persistence policies times two run modes. A recognized observation increments
its exact cell once, saturating at `Long.MaxValue` without wrapping, reset or
eviction. Observation and snapshot synchronize only on the recorder; that lock
enters no State, provider or user hook.

Snapshots are deterministic Vectors of positive cells, in whitelist order, then
Persistent/Ephemeral and Sync/Async order. Empty recorders produce no points.
`count` is the cumulative observed fact count. `errorCount` equals `count` only
for failed execution and required recovery; it is zero for every other facet.
No durations, current inventory or usage gauges are collected. No event history,
deduplication table, dynamic registry, retained context or global singleton exists.

## R3 Native fact hook and absence of effects

The only `job.lifecycle` recording hook is the existing `_append_event` path, inside its exact
record lookup, before the unchanged Event metadata, time, identity,
materialization and publication flow. It observes name, persistence and run mode
once. Missing records contribute nothing. Timeline and Event paths are not both
instrumented for the same fact. No semantic Event is added, reordered, suppressed
or deduplicated.

Counters observe reaching this fact hook. They are non-authoritative, nonpersisted
evidence and certify neither EventStore/EventBus delivery nor signed native
acknowledgement. They cannot authorize work or prove durable usage. Reads and
recording add no clock, ID, URI, formatting, body, callback, native-write,
management or health effects. Existing scheduling, quotas, lifetime payments,
control authorization, cancellation settlement, results, identities and
Event payloads/attributes retain their original authority and order.

## R4 Optional capability and exact State lifetime

`JobEngine.operationalMetrics` is an additive concrete optional capability whose
default is `None`. None means unsupported counters; it is neither health nor
authoritative zero usage. `InMemoryJobEngine` returns Some of the lifecycle recorder
in its existing State body, followed by the separately contracted
[job.operation recorder](job-operation-metrics-contract.md). Each recorder snapshots
under its own lock; the combined vector claims no joint cross-facet atomicity. The State constructor fields/defaults/Product arity, engine
constructors, JobMetrics and all existing Products are unchanged.

Multiple genuine engines using that exact State share one recorder and the same
cumulative history. Snapshot requires no State lock or provider call. Bootstrap,
another owner's snapshot and owner shutdown do not reset that history. A fresh
State starts with zero cells; recovery does not reconstruct historical counters.

## R5 Closed metric data boundary

Metrics never accept or retain payloads, attributes, job/task/attempt/Event IDs,
exceptions, contexts, arbitrary label maps, tenant/subject/session/credentials,
trace/span/correlation data, notes, summaries, parameters, paths, provider names,
hashes or references. The labels are closed constants. Original authorized Job
read models and semantic Event metadata are preserved; this metric boundary does
not redefine their security policy or create a new identity/payload log.

## R6 Real runtime service and existing OTEL rendering

MetricsComponent.Factory supplies a deferred function reading
`params.subsystem.jobEngine.operationalMetrics.getOrElse(Vector.empty)`. Component
creation performs no engine lookup, creates no replacement engine and owns no
local counters. The internal DefaultMetricsService fifth supplier parameter has
an empty default, preserving existing four-argument source callers.

Loading runtime metrics first obtains the original RuntimeDashboard snapshot,
then appends the exact supplied Job points before passing that same snapshot to
the existing OTEL exporter. Existing scopes, canonical query Actions, responses,
security mode and export result remain. A throwing supplier's result is not
silently replaced with empty counters. The catalog appends `job.lifecycle` with
the exact five label keys and states cumulative non-authoritative State-lifetime
semantics without advertising durable availability or readiness.

The existing OTEL renderer emits cumulative monotonic count/error sums for these
facts; no gauge is represented as a monotonic sum. C1 introduces no trace/payload
export or new exporter implementation.

## Executable specifications and pending assurance

| Scenario | Executable behavior | Specification |
| --- | --- | --- |
| E1 / OM1 | Fourteen independent fact tuples across all 56 cells and empty snapshot | [JobOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/JobOperationalMetricsSpec.scala) |
| E2 / OM2 | Unknown/null/mixed observations, fixed seed 697097101, minimum 24 shrinking sequences | same Spec |
| E3 / OM3 | Production increment saturation at zero and both maximum boundaries | same Spec |
| E4 / OM4 | Two bounded joined physical actors conserve same/disjoint cell counts | same Spec |
| E5 / ON1 | Real sync Nil Job, exact Events/results/resources/lifetime and counted time/ID purity | [JobOperationalMetricsIntegrationSpec](../../src/test/scala/org/goldenport/cncf/job/JobOperationalMetricsIntegrationSpec.scala) |
| E6 / ON2 | Real future async Job and manual due promotion, once-only facts and empty drain | same Spec |
| E7 / ON3 | Actual authorized controls, refused Cancel, free replay and native Events | same Spec |
| E8 / ON4 | Exact same-State shared history, owner shutdown and fresh-State zero | same Spec |
| E9 / ON5 | Real RetryNow once, two body calls and observed Event counts | same Spec |
| E10 / ON6 | Real diagnostic markers excluded from labels with authorized metadata intact | same Spec |
| E11 / MC1 | Actual subsystem engine and canonical runtime/catalog component Actions | [MetricsJobOperationalIntegrationSpec](../../src/test/scala/org/goldenport/cncf/component/builtin/metrics/MetricsJobOperationalIntegrationSpec.scala) |
| E12 / MC2 | Four-argument compatibility, deferred source, None, service ordering and production OTEL payload | same Spec |

All twelve scenarios are authored with adjacent Given/When/Then and should
assertions. Actual `PH697-C1-REP-004` passed three suites,12/12 tests; actual
`PH697-C1-ACC-001` passed61 suites,519/519 tests, zero failed and one existing
SqliteDataStore pending. Compiler warnings were zero; the known JVM Unsafe baseline
is separate. Independent complete JM69-09 Step review/local acceptance and M2
assurance of the five fixture substitutions in two Specs remain pending. Changed
C2 source requires its own current representative/accumulator validation, still
pending. Retained B2
`PH697-B2-ACC-002` evidence is 55 suites, 503 succeeded, zero failed and one
existing pending; `PH697-B2-LR-005` is 6/6 focused. Those receipts are predecessor
evidence and cannot validate C1 source changes.

## Mandatory remaining scope

JM69-09C still owes authoritative refusal/admission, immediate queue/child/attempt,
pagination, storage, expiry, corruption and payload metrics; correlation,
redaction and audit facets for every originally listed entity; health/readiness
and configured-capability refusal. C1 closes none of those aggregate requirements.

JM69-09D still owes configured production lifecycle/write-bridge/maintenance
ownership, integrity, backup/restore, migration checkpoint operational evidence,
actual owner-close and fresh-runtime behavior. JM69-10 still owes every actual
two-process and Textus CBD Support fresh-runtime acceptance without component-local
or shadow state substitutes.

Original Phase 69.4–69.6 ancestry/deferred audits, complete independent JM69-09/10
Step acceptance reviews/local commits, aggregate final full validation, the
original epoch-1 sole full Phase review, version evidence and final local release
remain mandatory. JM69-09 and Phase 69.7 remain IN_PROGRESS; JM69-10 remains OPEN.
HYG-PH697-SQL-SIZE-01 and HYG-PH697-ROOTA-VALIDATION-PROJECTION-01 remain separate
maintenance. No push, publication, deployment or successor work is authorized.
