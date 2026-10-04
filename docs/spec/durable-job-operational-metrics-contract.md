# Durable Job Operational Metrics Contract

status=normative
phase=69.7
step=JM69-09
slice=C3-durable-operation-metrics
implementation_status=authored-selected-validation-passed-independent-Step-assurance-pending

This companion to [Job Operations Contract](job-operations-contract.md) defines
completed native durable-operation diagnostics. The C3 implementation and fourteen
executable examples are authored. Actual C3 representative REP006 and accumulator
ACC001 validation passed; independent complete-Step assurance remains pending.
Original JM69-09 and JM69-10, the original epoch1 Phase review and the
final local release remain incomplete. No Step or Phase acceptance follows from
these counters.

## Rules

### R1 — Observe the normally returned native result

Evaluate each complete original expression once, then observe its normally returned
result by value. Every helper returns the identical Consequence or closed enum
instance; it retains no result afterward. An exception outside an existing domain
catch propagates unchanged and adds no completed-call observation. Existing bounded
failure conversions are observed only after they return. A failed acknowledgement
can follow an actual write or physical disposal: failed does not mean no effect.
No diagnostic retry, compensation, catch, provider call or conclusion substitution
is permitted.

### R2 — Closed ordered primitive counters

The scope is `job.durable.operation`, the name is `calls`, and the label keys are
exactly `operation` and `outcome`. Values are exclusively the fixed table below.
One zero-initialized array of 59 Long counters records and snapshots under its own
monitor, using `JobOperationalMetrics.saturatingIncrement`; Long.MaxValue stays
saturated. A snapshot is sparse (only positive counts), cumulative and ordered by
the table. Count is the cell count; errorCount equals count only for the marked
error cells, otherwise zero. durationCount/durationTotalMillis are zero,
durationMinMillis/durationMaxMillis/durationAvgMillis are absent. These are counts
of observations, without elapsed-time, health or successful-disposal interpretation.

### R3 — Preserve real native nesting

Checkpoint retains its public load and both boundaries count. Create retains its
private load, which has no load cell. Payload publication rollback calls the physical
Blob provider directly, so it does not add a public payload_delete cell. Retention
sweep discovers and validates its bounded batch before effects; the admitted branch
uses the original private maintenance body. Each actual successful report entry
contributes its facet once, then the sweep contributes returned once. Failed
bounds/discovery/batch processing contributes only retention_sweep/failed.
Normally returned coordinated-maintenance reports count returned even when entries
contain refusals. Only actual calls to retention, cleanup or Store APIs count nested
observations; early private maintenance refusals invent no retention observation.

### R4 — Primitive-only privacy and pure diagnostic reads

No request, context, result, canonical body, reference, provider, credential,
tenant, subject, path, correlation, trace/span, Job/Task/attempt/Event identity,
payload, exception or diagnostic string is retained. No user-bound label registry
exists. Reading `operationalMetrics` only snapshots counters and constructs fixed
points: it invokes no provider, codec, clock, ID generator, event, policy or callback.
It cannot repair a row, dispose bytes, release quota or establish readiness.

### R5 — Store-instance lifetime

Exactly one recorder belongs to a DurableJobStore instance. Real payload, retention,
cleanup and coordinated-maintenance adapters bound to that Store share it. Snapshot
reads and adapter/provider close do not reset it. A new Store against the same
persistent database begins empty: counters are never reconstructed from rows,
events, native metadata or quota. Counters are not persisted authority, delivery,
audit, runtime owner health or acceptance evidence.

### R6 — Additive native consumer contract

The actual RuntimeMetrics catalog contains the original 21 scopes followed by
job.lifecycle, job.operation and job.durable.operation: 24 scopes. The prior C2
catalog23 receipts remain historical receipts for that prior tree. The new catalog
entry declares exactly operation/outcome keys. Existing RuntimeMetricPoint Record
conversion and the existing optional fifth DefaultMetricsService supplier consume
actual Store points. Actual OTEL conversion produces cumulative monotonic count
sums and positive-only error sums with exactly two fixed attributes and no duration
gauge. Original registries, supplier-before-security ordering, unsupported-format
refusal and throwing-supplier behavior remain. This boundary introduces no configured
production Store/maintenance owner wiring or fake empty healthy fallback.

### R7 — Explicit retention facets and sweep results

All 18 real retention enum facets map explicitly as below, never through enum
stringification. Authorization precedes shape/codec/native reads. Original OCC,
revision, legal hold, terminal state and policy evaluation remain unchanged. A
successful report contributes each actual entry once; null report/entries/entry/facet
markers contribute no candidate facet. A normally returned Success(null) still
contributes retention_sweep/returned once. Null retention/cleanup/startup enum
markers contribute no outcome. No policy reevaluation, source call or report
reconstruction is added by observation.

### R8 — Explicit cleanup facets and physical authority

All 16 cleanup enum facets map explicitly as below. Observe the outer facet only
after the complete original authorization/shape/try-catch expression returns;
recorder code stays outside the domain catch. Ownership verification, native
payload reservation, acknowledgement and release, physical Blob disposal,
second startup read and signed tombstone checkpoint remain unchanged. Failed
physical deletion retains occupancy. Acknowledged physical deletion may release
its quota even if the later Job checkpoint refuses; that refusal certifies no
tombstone. Lost native acknowledgements preserve the original conservative
reservation semantics. Returned reports are not evidence of runtime readiness.

## Exact fixed cell order

| Cell | Operation | Outcome | errorCount |
| --- | --- | --- | --- |
| 1 | create | succeeded | 0 |
| 2 | create | failed | count |
| 3 | load | found | 0 |
| 4 | load | missing | 0 |
| 5 | load | failed | count |
| 6 | startup | admitted | 0 |
| 7 | startup | missing | 0 |
| 8 | startup | corrupt | count |
| 9 | startup | refused | count |
| 10 | checkpoint | succeeded | 0 |
| 11 | checkpoint | failed | count |
| 12 | storage_snapshot | succeeded | 0 |
| 13 | storage_snapshot | failed | count |
| 14 | payload_snapshot | succeeded | 0 |
| 15 | payload_snapshot | failed | count |
| 16 | payload_put | succeeded | 0 |
| 17 | payload_put | failed | count |
| 18 | payload_prepare | succeeded | 0 |
| 19 | payload_prepare | failed | count |
| 20 | payload_delete | succeeded | 0 |
| 21 | payload_delete | failed | count |
| 22 | retention_sweep | returned | 0 |
| 23 | retention_sweep | failed | count |
| 24 | retention | kept | 0 |
| 25 | retention | not_due | 0 |
| 26 | retention | held | 0 |
| 27 | retention | not_terminal | 0 |
| 28 | retention | already_expired | 0 |
| 29 | retention | already_deleted | 0 |
| 30 | retention | tombstoned | 0 |
| 31 | retention | expired | 0 |
| 32 | retention | deleted | 0 |
| 33 | retention | hold_applied | 0 |
| 34 | retention | hold_released | 0 |
| 35 | retention | missing | count |
| 36 | retention | corrupt | count |
| 37 | retention | refused | count |
| 38 | retention | stale | count |
| 39 | retention | invalid_transition | count |
| 40 | retention | revision_exhausted | count |
| 41 | retention | write_refused | count |
| 42 | cleanup | tombstoned | 0 |
| 43 | cleanup | already_tombstoned | 0 |
| 44 | cleanup | not_deleted | 0 |
| 45 | cleanup | held | 0 |
| 46 | cleanup | not_due | 0 |
| 47 | cleanup | not_terminal | 0 |
| 48 | cleanup | missing | count |
| 49 | cleanup | corrupt | count |
| 50 | cleanup | refused | count |
| 51 | cleanup | stale | count |
| 52 | cleanup | revision_exhausted | count |
| 53 | cleanup | invalid_time | count |
| 54 | cleanup | ownership_refused | count |
| 55 | cleanup | resource_bound_refused | count |
| 56 | cleanup | payload_delete_refused | count |
| 57 | cleanup | checkpoint_refused | count |
| 58 | maintenance_sweep | returned | 0 |
| 59 | maintenance_sweep | failed | count |

## Executable examples

Every example is an actual AnyWordSpec leaf with adjacent Given/When/Then,
should expectations and afterWord spec/example/rule/phase linkage. The helper
matrix is unit evidence; native authority examples use actual SQLite,
StandardEntityStore native mutation capabilities and LocalBlobStore. Work directories
are unique children of `target/durable-job-operational-metrics/work`; fixtures close
native providers, streams, actors and timers in finally and clean only their own
subtree. No user.home, network, process or shadow storage algorithm is used.

### E1/DU1 (R1,R2,R4,R7,R8)

All 59 helpers and all 18 retention/16 cleanup facets retain identical supplied objects. An independent explicit vector fixes cell order and error flags; two-label privacy, zero durations, saturation and repeated snapshot purity hold. Null enums and absent report candidate markers add no candidate cell.

Executable: [DurableJobOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationalMetricsSpec.scala).

### E2/DU2 (R1,R2,R4)

ScalaCheck seed697097302 runs at least24 ordinary shrinking finite 1..100 lists over 59 helper observations plus ignored null facets. At every prefix, snapshots equal an independently maintained multiset, count conservation and the 59-cell bound.

Executable: [DurableJobOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationalMetricsSpec.scala).

### E3/DU3 (R2,R4)

Two joined physical actors observe2000 success and2000 failure creates. Captured exceptions fail the leaf; bounded five-second joins and finally interruption/joins close both actors. Exact counts/error subsets and repeated snapshot purity remain.

Executable: [DurableJobOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationalMetricsSpec.scala).

### E4/DS1 (R1,R2,R3,R4)

Actual committed terminal V1/V2 create, found/missing load, startup, current checkpoint and stale checkpoint retain native results, canonical rows, revision/quota metadata and exact measured provider calls. Twenty diagnostics after the explicit resourceSnapshot evidence baseline invoke no providers/clock/IDs.

Executable: [DurableJobStoreOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStoreOperationalMetricsSpec.scala).

### E5/DS2 (R1,R3,R4)

A real one-row quota admits the first row, then refuses further capacity, malformed signed integrity and foreign access. Real stale checkpoint/foreign load failures remain distinct from successful missing. Raw rows/quota remain original except accepted writes; metric labels contain no private fixture markers.

Executable: [DurableJobStoreOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStoreOperationalMetricsSpec.scala).

### E6/DS3 (R1,R2,R4)

Actual raw SQLite corrupted canonical rows yield closed Corrupt, unauthorized valid rows Refused and absence Missing without repair. An armed EntityStore.loadDetached sentinel propagates the identical startup exception uncounted; bounded create after native write counts only its original final failure.

Executable: [DurableJobStoreOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStoreOperationalMetricsSpec.scala).

### E7/DS4 (R1,R3,R8)

Separate fresh V1/V2 fixtures lose create/checkpoint acknowledgement after actual super success. Failed call cells coexist with persisted canonical bytes and native revisions; conservative pending reservations and provider sequence remain unchanged by counter reads.

Executable: [DurableJobStoreOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStoreOperationalMetricsSpec.scala).

### E8/DS5 (R4,R5)

Two payload/retention/cleanup adapter sets bound to the same real Store share its history. A fresh Store against the same database starts empty; closing SQLite then reading20 snapshots neither resets the first history nor calls providers/time/IDs.

Executable: [DurableJobStoreOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobStoreOperationalMetricsSpec.scala).

### E9/DM1 (R1,R3,R7)

Real V1/V2 direct Hold/ReleaseHold/Expire/Delete and Held/NotDue/AlreadyDeleted/Refused/Stale results preserve canonical retention and exact native revisions with one observed facet per direct call and actual nested startup/checkpoint/load cells.

Executable: [DurableJobMaintenanceOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMaintenanceOperationalMetricsSpec.scala).

### E10/DM2 (R1,R3,R7)

A real bounded source supplies three ordinals: admitted due terminal Expired, absent Missing and capability-denied Refused. Exactly three candidate cells and one returned sweep appear, with no admitted duplication. Invalid bound, failed discovery and malformed ordinal batch add only failed sweep cells; valid empty source adds only returned.

Executable: [DurableJobMaintenanceOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMaintenanceOperationalMetricsSpec.scala).

### E11/DM3 (R1,R3,R4,R8)

Real Blob put/prepare/delete and lowered native payload quota preserve exact reference/metadata/bytes and effect order. Post-physical-put metadata acknowledgement loss triggers the original direct rollback delete but only payload_put/failed. Native disposal acknowledgements retain original occupancy/release semantics. Zero-owned prepare with null Store/providers remains successful.

Executable: [DurableJobMaintenanceOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMaintenanceOperationalMetricsSpec.scala).

### E12/DM4 (R1,R3,R8)

Real Deleted terminal V1/V2 and exclusive owned bytes produce signed Tombstoned, duplicate AlreadyTombstoned and Held/Refused/Stale facets. Separate actual provider deletion failure yields PayloadDeleteRefused and post-disposal native checkpoint refusal yields CheckpointRefused, without false tombstone or unacknowledged quota release. Raw rows, metadata, bytes and nested cells follow original authority.

Executable: [DurableJobMaintenanceOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMaintenanceOperationalMetricsSpec.scala).

### E13/DM5 (R1,R3,R4,R8)

Actual coordinated maintenance expires/deletes/disposes/tombstones native V1/V2 and returns its original report and nested cells. Invalid/source failures retain no effects. The existing scheduler with real ManualJobTimeSource/ManualJobTimer executes one due callback and closes; retained late/rearm callbacks add no completed call. This is local scheduler evidence, without production owner/fresh-process acceptance.

Executable: [DurableJobMaintenanceOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMaintenanceOperationalMetricsSpec.scala).

### E14/DM6 (R2,R4,R6)

Mixed actual native success/refusal/corrupt/missing points feed the existing fifth metrics supplier. Real component registry, jobs-before-security ordering, Record conversion and OTEL cumulative monotonic count/error sums with two attributes and no durations remain. Catalog24 preserves original scopes; unsupported protocol and identical supplier exception retain original behavior without empty readiness fallback.

Executable: [DurableJobMaintenanceOperationalMetricsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobMaintenanceOperationalMetricsSpec.scala).

MC1/E11 in the existing
[MetricsJobOperationalIntegrationSpec](../../src/test/scala/org/goldenport/cncf/component/builtin/metrics/MetricsJobOperationalIntegrationSpec.scala)
adds actual catalog24/two-label assertions and companion R6 linkage. Its original
subsystem Actions/resolver/None/runtime fixtures remain. MC2 retains its original
four exact Job lifecycle/operation OTEL points, supplier exceptions and ordering.

## Actual selected validation and pending original acceptance

Actual `PH697-C3-REP-006` passed the four-suite representative (the three new
Specs and existing metrics integration Spec):16 total/16 succeeded/0 failed/0
pending. `PH697-C3-ACC-001` passed the complete original63 plus three new suites:
66 suites,544 total/544 succeeded/0 failed and1 original pending. Both had0
aborted suites,0 compiler warnings, SBT/wrapper/native executor exits0, typed
readers0 and released serial locks. The [C3 journal](../journal/2026/10/2026-10-03-phase-69.7-durable-job-operational-metrics.md)
records actual log paths, failed REP001–005, CP001 and repairs. These receipts
retain their exact preprojection18-path subjects (REP006 revision6 and ACC001
revision7); the twelve tested program paths are unchanged by this separate
four-document prose projection. No old receipt is rebound. This selected
accumulator is not a full-repository or aggregate Phase acceptance. Prior C2
REP00219/19 and ACC00163 suites530/530 validate that prior tree only.

Both C1/C2 M2 fixture assurance items and C3 fixture-qualifier, message-qualifier
and native-assertion M2 assurance remain pending-not-waived at the original
complete JM69-09 Step review. The one-token clock override is M0 on exact-diff
and existing focused compile evidence; no independent review is invented. The
predecessor three-unit compiler batch and narrow one-unit native-assertion
successor are retained. Original CPBs,
Step1/3 and Phase0/3 repair histories and planepoch1 remain unchanged. Original
C correlation/redaction/audit/configured health, D production ownership/integrity/
backup/restore/migration/close, all JM69-10 real process and Textus CBD Support
fresh-result acceptance, 69.4–69.6 deferred audit, complete Step reviews/commits,
aggregate final validation, sole epoch1 Phase review, version/closure and final
local release remain mandatory. No push, publication, deployment or successor
work follows from this contract.
