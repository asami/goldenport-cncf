# Phase 69 JM69-01A — Job Contract Inventory Baseline

status=open
date=2026-09-09
phase=[Phase 69](../../../phase/phase-69.md)
slice=JM69-01A

This journal is the source-backed inventory foundation for `JM69-01`. It
records current implementation and Executable Specification facts, reconciles
the retained closed Phase 6/14/22 boundaries, and identifies gaps for later
work. It does not define the durable record model, select a codec or provider,
or claim any later acceptance identity passes.

## Authority labels

- **Current source** means the checked-in CNCF implementation, principally
  [`JobEngine.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobEngine.scala)
  and [`JobEntity.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobEntity.scala).
- **Executable specification** means behavior demonstrated by the named
  specifications under `src/test/scala`; it is not evidence of a fresh-process
  provider when the fixture reuses process objects.
- **Closed Phase authority** means the retained boundary recorded by the
  Phase 6, 14, or 22 progress document.
- **Non-authoritative design/spike reference** means intent or exploration that
  this inventory may cite but does not adopt. In particular, the
  [CBD compatibility spike](../08/2026-08-15-cbd-review-job-compatibility-spike-for-phase-69.md)
  is explicitly rejected for adoption.

## Current source inventory

### In-memory state and execution authority

**Current source:** `InMemoryJobEngine.State` contains two concurrent maps,
`durableJobs` and `runtimeJobs`, whose values are complete `JobRecord`
instances. `InMemoryJobEngine` keeps references to those maps and rehydrates
delayed starts and delayed retries from them when a new engine instance is
constructed. The `Persistent` label therefore distinguishes the durable branch
from the runtime-only branch in the current implementation, but the maps are
still in-memory objects.

**Current source:** `JobRecord` retains the execution authority needed by the
current engine: `tasks: List[JobTask]`, `submittedContext: ExecutionContext`,
status, result, persistence/run mode, scheduling timestamps, task read models,
task definitions, timeline, debug/input data, retry/deferred result state, and
completion counters. `JobEngine` remains the execution authority; this Slice
does not change that ownership.

### Job Entity projection

**Current source:** `_put_record` stores a persistent `JobRecord` in the
`durableJobs` map and synchronizes `JobEntity.from(_read_model(record))` to the
standard `EntityStore`. Ephemeral records go only to `runtimeJobs` and the
Executable Specifications cover the absence of an Ephemeral `JobEntity`.

**Current source:** `JobEntity` is a management/read projection. It carries
identity, lifecycle/result summary, persistence/origin, submitter, target
summary, retry/recovery summary, counts, input metadata without raw payload,
debug fields, lineage/continuation summaries, calltree references, and
JobDefinition snapshot metadata. It does not carry the complete
`JobRecord.tasks`, the submitted `ExecutionContext`, or an exact terminal
`OperationResponse` sufficient to reconstruct the live record in a new process.
`resultAvailable` and the result summary are not the exact result body.

### Public Job reads and controls

**Current source:** the `JobEngine` contract exposes status/result reads,
`getResponse`, bounded `awaitResult`, policy-guarded `control`, `query`,
`listJobs(limit, persistentOnly)`, offset/limit `queryTasks` and
`queryTimeline`, task execution-tree/detail reads, `metrics`, input cleanup,
and `queryVisible` authorization. `InMemoryJobEngine.listJobs` reads the
in-memory maps, orders by `updatedAt` descending, and takes the requested
bound. The task and timeline methods are offset/limit pages over the current
record; none is a restart-safe cursor or snapshot authority.

**Executable specification:**
[`JobQueryReadModelSpec`](../../../../src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala)
demonstrates sync-equivalent result shape, deterministic task/timeline slices,
Persistent versus Ephemeral origin, task/timeline/debug/trace projections,
retry/compensation/recovery diagnostics, and policy-visible reads. It does not
demonstrate reconstruction from a new process.

### Shared-State delayed-start specification and its limit

**Executable specification:**
[`InMemoryJobEngineSpec`](../../../../src/test/scala/org/goldenport/cncf/job/InMemoryJobEngineSpec.scala)
contains `rehydrate persistent delayed jobs after engine restart`. The test
creates one `InMemoryJobEngine.State`, submits a delayed Persistent Job through
`engine1`, shuts that engine down, constructs `engine2` with the same `State`,
advances a manual clock/timer, drains the scheduler, and observes completion.

**Interpretation:** this proves delayed-start rehydration when a second engine
reuses the same in-process concurrent maps and task/context object graph. It is
not real fresh-process durability, a configured durable provider, or terminal
record restore after process replacement. A new process cannot obtain the full
`JobRecord` from the lightweight `JobEntity` projection described above.

## Retained closed boundaries

| Authority | Retained contract for Phase 69 inventory |
| --- | --- |
| [Phase 6](../../../phase/phase-6.md) | Job CQRS retains query/read-model, sync-equivalent result retrieval, control commands, lifecycle, and event-correlation boundaries. Its original asynchronous default is reconciled by the later Phase 22 policy for ordinary Commands. |
| [Phase 14](../../../phase/phase-14.md) | JCL remains submission-only and is not a workflow language. Workflow remains event-triggered/entity-status-based and distinct from a Job; the built-in scheduler is not expanded into a general orchestration platform. |
| [Phase 22](../../../phase/phase-22.md) | Ordinary CRUD-style Commands remain synchronous by default; async Job execution is explicit. `JobEntity` is the synchronized management/search projection while `JobEngine` is execution authority. `JobDefinition` remains distinct from a Job instance; Task transaction/compensation, Event-forwarded notification, and query-only CompositeQuery boundaries remain unchanged. |

These are retained boundaries, not a decision to implement the later durable
model in this Slice. No arbitrary task, closure, object, execution-context,
credential, provider, or classloader serialization is admitted as a solution.

## Older design and spike intent

The [Job Management design](../../../design/job-management.md) describes
forward-looking requirements such as durable terminal state, full execution
records, stable cursor enumeration, and reconstructible descriptors. Those
statements are design intent and gap evidence for later Phase 69 work; they do
not turn the current in-memory implementation into a durable provider.

The [CBD compatibility spike](../08/2026-08-15-cbd-review-job-compatibility-spike-for-phase-69.md)
is a non-authoritative rejected experiment. Its bounded full-list correlation,
current-process result dependency, and synchronous wait workaround are not
adopted. CBD Support must wait for the CNCF contracts and the later downstream
acceptance boundary.

## Gaps and downstream readers

The current baseline leaves the following explicit gaps for later child work:

- real new-process terminal/scheduled/retry reconstruction and Ephemeral
  absence;
- corrupt, incompatible, missing, partial, or unauthorized durable-record
  outcomes;
- complete bounded cursor/snapshot enumeration and continuation validity;
- typed exact result retrieval independent of live task objects;
- executable JCL and rejection semantics;
- governed JobDefinition lifecycle and immutable launch snapshots;
- bounded CompositeQuery v2 composition;
- authorized user/operator projections and notification/read-state behavior;
- retention, integrity, operations, and fresh downstream CBD Support adoption.

The readers are the Phase 69/69.1 planning and checklist records, Job
Management design, the CBD compatibility spike, and future Job Engine
Executable Specifications. CLI, HTTP, Web, MCP, SPI, generated, and package
surfaces are not changed by this documentation Slice.

## Later failing-first acceptance identities

The following identities are registered as future failing-first acceptance
targets. They are named here for planning only; none is implemented or claimed
passing by `JM69-01A`.

| Identity | Later acceptance target |
| --- | --- |
| `JM69-03-FF-NEW-PROCESS-TERMINAL-RESTORE` | Restore a terminal Persistent Job, exact result/reference, Task evidence, and timeline in a genuinely new process. |
| `JM69-03-FF-EPHEMERAL-ABSENCE` | Prove an Ephemeral Job and its runtime-only state are absent after process replacement. |
| `JM69-03-FF-CORRUPT-INCOMPATIBLE-RECORD` | Return structured refusal for corrupt or incompatible durable records without false success. |
| `JM69-04-FF-CURSOR-CONTINUATION` | Continue bounded Job enumeration across pages without omission, duplication, or unstable ordering. |
| `JM69-05-FF-JCL-EXECUTION-REJECTION` | Execute admitted JCL and reject unsupported, cyclic, unbounded, or incompatible content before execution. |
| `JM69-06-FF-JOBDEFINITION-LIFECYCLE` | Exercise review/accept/apply, activation, rollout, rollback, retirement, and immutable running-Job snapshots. |
| `JM69-07-FF-COMPOSITEQUERY-BOUNDS` | Enforce bounded deterministic CompositeQuery v2 composition, cancellation, and partial-failure behavior. |
| `JM69-08-FF-USER-OPERATOR-PROJECTION` | Exercise authorized user/operator Job views, controls, diagnostics, notification, and redaction boundaries. |
| `JM69-09-FF-LIFECYCLE-SAFETY-OPERATIONS` | Phase 69.7 / JM69-09 target only: one failing-first operational scenario covering bounded quota/admission, retained-record lifecycle/expiry/deletion/integrity, redacted audit/metrics/health/maintenance evidence, and hostile or unauthorized access without disclosure, false repair, or false success. No test, provider, policy, or acceptance pass is added or claimed. |
| `JM69-10-FF-CBD-SUPPORT-ADOPTION` | Discover and retrieve a completed Persistent CBD Support Job/result from a fresh downstream runtime without component-local shadow state. |

## Scope disposition

`JM69-01A` changes documentation status and inventory evidence only. It does
not define the later durable record model, start `JM69-02`, start any child
Phase 69.1–69.7, alter source/tests/schema/configuration, or choose a durable
codec/provider. Together with JM69-01B and the closure matrix, this inventory
supports the `JM69-01` `DONE` status. `JM69-02` remains open/planned, Phase 69
remains `status=in_progress`, and every child Phase remains planned/not started.
