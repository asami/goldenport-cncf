# Phase 69 JM69-01B — Job Integration and Consumer Inventory

status=partial
date=2026-09-09
phase=[Phase 69](../../../phase/phase-69.md)
slice=JM69-01B

This journal records the source-backed integration and consumer inventory for
`JM69-01`. It is a partial evidence record, not a durable Job model, provider
selection, executable specification, or implementation plan. The prior
[JM69-01A Job Contract Inventory Baseline](2026-09-09-phase-69-job-contract-inventory.md)
remains the baseline for the current Job record and read-model inventory.

## Authority labels and boundary

- **Current source** means the checked-in CNCF implementation linked below.
- **Executable specification** means an existing specification that exercises
  the current source. No specification is added or changed by this Slice.
- **Closed Phase authority** means the retained Phase 6/14/22 contract; it is
  not reopened by this inventory.
- **Planned Phase boundary** means a Phase 63–68 or Phase 69 child scope that
  remains separately governed and planned. This journal does not absorb it.
- **Non-authoritative spike/design** means evidence that may expose a gap but
  cannot establish a CNCF or CBD Support contract. The [rejected CBD Support
  compatibility spike](../08/2026-08-15-cbd-review-job-compatibility-spike-for-phase-69.md)
  remains rejected for adoption.

The Phase 69 status remains `status=in_progress`; together with JM69-01A and
the closure matrix, this inventory supports `JM69-01` `DONE`. `JM69-02`
remains open/planned, and Phase 69.1–69.7 remain planned/not started. This
record does not select a codec, provider, retention policy, authorization
model, or new JobDefinition/CompositeQuery/JCL behavior.

## Current integration inventory

### JobEngine, JobRecord, and the management projection

**Current source:** [`JobEngine.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobEngine.scala)
defines `JobEngine` status/result/control/query entry points and
`InMemoryJobEngine`. The engine has optional `EventStore` and `EventBus`
attachments, but no `BlobStore` constructor/member that binds a Blob provider
to a complete Job record. Submission constructs a live `JobRecord` containing
the `JobTask` list, submitted `ExecutionContext`, result, persistence/run
policy, timeline, debug data, input, retry state, and task read models.

**Current source:** `InMemoryJobEngine.State` contains separate concurrent
`durableJobs` and `runtimeJobs` maps. `Persistent` therefore selects the
current engine's durable branch and `Ephemeral` selects its runtime branch, but
both values are in-memory `JobRecord` objects. Reusing one `State` between two
engine instances is not a configured durable provider and does not establish
fresh-process restoration.

**Current source:** `_put_record` stores a Persistent record in the
`durableJobs` map and synchronizes `JobEntity.from(_read_model(record))` to the
standard EntityStore. Ephemeral records remain in `runtimeJobs` and do not
receive a JobEntity projection. [`JobEntity.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobEntity.scala)
describes that entity as a lightweight management/search record: its fields
include lifecycle/result summaries, submitter and target summaries, retry and
recovery metadata, input metadata without raw payload, CallTree metadata,
lineage, continuation, and JobDefinition snapshot metadata. It does not carry
the complete task object graph, submitted execution context, or exact terminal
`OperationResponse` needed to reconstruct the live record in a fresh process.

**Executable specification:**
[`InMemoryJobEngineSpec`](../../../../src/test/scala/org/goldenport/cncf/job/InMemoryJobEngineSpec.scala)
proves Persistent-to-JobEntity synchronization, Ephemeral absence, input
metadata cleanup, and shared-State delayed-job rehydration. The delayed-job
case constructs the second engine with the same in-process `State`; it is not a
fresh-process provider or terminal-record restore. The
[`JobQueryReadModelSpec`](../../../../src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala)
proves current read-model result shape, deterministic task/timeline slices,
Persistent/Ephemeral origin, policy-visible reads, and task/trace diagnostics;
it does not prove process replacement recovery.

### Independent storage-adjacent surfaces

**Current source:** [`BlobModel.scala`](../../../../src/main/scala/org/goldenport/cncf/blob/BlobModel.scala)
defines the independent `BlobStore` SPI (`put`, `get`, `delete`, `accessUrl`,
and `status`). [`BlobStoreConfig.scala`](../../../../src/main/scala/org/goldenport/cncf/blob/BlobStoreConfig.scala)
defines configurable named backends and `BlobStoreProvider` registration;
[`BlobStores.scala`](../../../../src/main/scala/org/goldenport/cncf/blob/BlobStores.scala)
provides the in-memory compatibility backend and a local filesystem backend.
Neither the SPI nor a backend is a JobRecord provider, and no current
JobEngine-to-BlobStore binding reconstructs tasks, contexts, execution state,
exact results, authorization, or retention policy.

**Executable specification:**
[`BlobStoreSpec`](../../../../src/test/scala/org/goldenport/cncf/blob/BlobStoreSpec.scala)
demonstrates in-memory put/read/delete and provider-factory behavior. Its
LocalBlobStore restart case proves payload and sidecar metadata recovery only;
it does not prove JobRecord recovery.

**Current source:** [`EventStore.scala`](../../../../src/main/scala/org/goldenport/cncf/event/EventStore.scala)
defines append/load/query/replay plus policy-protected query/replay entry
points. Its `inMemory` implementation retains `EventRecord` values in an
in-process buffer. [`JobEventJournal.scala`](../../../../src/main/scala/org/goldenport/cncf/job/journal/JobEventJournal.scala)
defines append/list operations and `InMemoryJobEventJournal` retains entries in
an in-process map. Event records and Job event-log entries are useful
correlation/evidence surfaces, but neither independently reconstructs the Job
task graph, submitted context, exact result, provider bindings, credentials,
or retention decision.

**Executable specification:**
[`EventStoreBaselineSpec`](../../../../src/test/scala/org/goldenport/cncf/event/EventStoreBaselineSpec.scala)
proves deterministic in-memory append/load/query/replay ordering, while
[`EventStorePolicySpec`](../../../../src/test/scala/org/goldenport/cncf/event/EventStorePolicySpec.scala)
proves the current authorization entry points for event introspection and
replay. Those specifications do not turn EventStore into a JobRecord provider.

### Input cleanup and CallTree evidence

**Current source:** `JobInput` in [`JobEngine.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobEngine.scala)
retains inline/blob input metadata and supports a retention-policy predicate;
`cleanupExpiredInputs` replaces eligible payload bodies with cleaned metadata
on the current `JobRecord`. This is a JobEngine operation over current state,
not proof of durable retention, expiry, deletion, or tombstone semantics.

**Current source:** the same JobEngine file captures bounded CallTree evidence
into `JobDebugInfo`; [`JobEntity.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobEntity.scala)
projects `calltreeSaved`, storage/byte-count, payload-reference, and drop
reason fields. These are current diagnostics/read-model surfaces, not evidence
that a complete CallTree and JobRecord can be restored after process
replacement. Durable payload, integrity, redaction, and operational policy
remain later-owned concerns.

## JobControl, JCL, and JobDefinition baseline

**Current source:** [`JobDefinition.scala`](../../../../src/main/scala/org/goldenport/cncf/job/JobDefinition.scala)
parses the current supported JCL record formats, accepts exactly one `job` or a
non-empty `jobs` root, and models action/workflow targets, parameters,
submission options, profile diagnostics, and currently inert `flow`/`events`/
`onEvent` records. The parser rejects unsupported workflow-like target shapes
and malformed roots before submission.

**Current source:** [`JobControlComponent.scala`](../../../../src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)
exposes JCL describe/submit operations and the current JobDefinition
management surface: create, update, activate, retire, get, and search. A
referenced definition must currently be active and contain one job before
submission; submission attaches the current definition snapshot to the Job.
This is the existing JCL/JobDefinition baseline, not the later accept/apply,
review, rollout, rollback, or immutable-running-definition governance
contract.

**Executable specification:**
[`JclJobControlComponentSpec`](../../../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala)
demonstrates format-aware JCL description/submission, malformed-shape
rejection, current JobDefinition create/search/active-reference submission,
and snapshot attachment. These are current source-backed behaviors; no future
executable JCL or governance identity is claimed passing here.

## CompositeQuery and public consumers

### Query-only CompositeQuery v1

**Current source:** [`CompositeQuery.scala`](../../../../src/main/scala/org/goldenport/cncf/composite/CompositeQuery.scala)
validates duplicate names, unknown dependencies, and trace-job requests, then
executes the named requests with a sequential `foldLeft` through the
query-only subsystem boundary. Required failures fail the composite request;
optional failures become bounded diagnostics and later queries continue. The
engine preserves the caller `ExecutionContext` through that boundary; it is
not the later bounded-parallel, cross-subsystem CompositeQuery v2 model.

**Executable specification:**
[`CompositeQueryEngineSpec`](../../../../src/test/scala/org/goldenport/cncf/composite/CompositeQueryEngineSpec.scala)
demonstrates named query aggregation, sequential required/optional failure
behavior, trace-job rejection, non-Query rejection, and caller-subject
preservation. It does not establish a Job-producing or parallel v2 contract.

### Renderer, admin, and JobControl reads

**Current source:** [`StaticFormAppRendererJobPart.scala`](../../../../src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererJobPart.scala)
reads `JobQueryReadModel` values through `listJobs` and per-job queries to
render current system/application Job lists and details, including status,
result summary, CallTree, Task rows, and timeline rows. The current
[`JobControlComponent.scala`](../../../../src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala)
JobService similarly gates status, history, CallTree, Task detail, and result
reads through `queryVisible` and the current JobEngine query surface.

**Executable specification:**
[`StaticFormAppRendererSpec`](../../../../src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala)
and [`JobControlComponentSpec`](../../../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponentSpec.scala)
demonstrate current admin/read projections and policy-visible Job inspection.
These reads are not evidence of a complete authorized user/operator
experience, notification/read-state persistence, or operational controls.

### Downstream CBD Support

**Non-authoritative spike/design:** the [CBD compatibility spike](../08/2026-08-15-cbd-review-job-compatibility-spike-for-phase-69.md)
explicitly rejected synchronous waits over asynchronous Jobs, bounded full-list
correlation, current-process result dependency, and component-local shadow
state. Its focused results are historical experiment evidence only. CBD
Support has no CNCF-core durable Job consumer in this repository and must wait
for the CNCF contracts and the later downstream acceptance boundary.

## Retained boundaries

| Authority | Retained current boundary | JM69-01B disposition |
| --- | --- | --- |
| [Phase 6](../../../phase/phase-6.md) — **Closed Phase authority** | Job CQRS retains query/read-model and sync-equivalent result retrieval, control commands, lifecycle, and event-correlation/replay boundaries. | Inventory only; no replacement of the JobEngine execution authority or CQRS surface. |
| [Phase 14](../../../phase/phase-14.md) — **Closed Phase authority** | JCL remains submission-only; Workflow is event-triggered/entity-status-based and distinct from a Job; the built-in baseline is not a general orchestration platform. | Current JCL and Workflow consumers are named; executable JCL and later orchestration remain separately owned. |
| [Phase 22](../../../phase/phase-22.md) — **Closed Phase authority** | Ordinary CRUD Commands are synchronous by default; async Job execution is explicit. JobEntity is a synchronized management/search projection while JobEngine remains execution authority. JobDefinition is distinct from a Job instance; Task compensation, Event notification forwarding, and query-only CompositeQuery remain bounded. | These boundaries are retained; no durable schema, governance, UX, notification, or CompositeQuery v2 decision is made here. |

No arbitrary serialization of live task objects, closures, execution contexts,
providers, credentials, or classloaders is admissible as evidence or as a
recovery solution.

## Phase 63–68 intersections

The following are **Planned Phase boundaries**, recorded to prevent accidental
scope absorption:

| Phase | Retained intersection with Job inventory | Boundary preserved |
| --- | --- | --- |
| [Phase 63](../../../phase/phase-63.md) | A committed StateMachine transition may start external I/O through a separately governed Operation or Job. | Phase 63 owns transition planning/commit semantics; it does not grant a Job storage or recovery exception. |
| [Phase 64](../../../phase/phase-64.md) | Composite StateMachine/Workflow invokes Operations or Jobs through normal CNCF boundaries, with JobEngine as the asynchronous substrate. | Phase 64 owns composite/workflow semantics and progression; it does not redefine the Job durable record. |
| [Phase 65](../../../phase/phase-65.md) | Executable contracts may be evaluated on synchronous and Action/Task/Job paths at defined Operation/Aggregate checkpoints. | Phase 65 owns DbC evaluation and violation semantics; it does not own Job persistence, recovery, or authorization policy. |
| [Phase 66](../../../phase/phase-66.md) | The SkillBundleManifest work is a separate packaging/integrity contract that may be consumed by downstream tooling. | Phase 66 does not add Job record storage, Job protocol, or Job consumer ownership. |
| [Phase 67](../../../phase/phase-67.md) | Explicit test invocation isolates test home, logical datastores, runtime state, and provider/test doubles. | Phase 67 test-owned state is not a production Job provider and does not prove process-restart Job recovery. |
| [Phase 68](../../../phase/phase-68.md) | Legacy/modern MCP selection remains at the transport boundary while Components consume provider-neutral typed calls. | MCP protocol-era behavior does not create a Job storage/protocol exception or move Job ownership into MCP. |

## Later ownership of uncovered items

| Uncovered current item | Existing owner | Boundary retained |
| --- | --- | --- |
| Fresh-process Persistent Job/Task/input/CallTree/result/timeline reconstruction, record ownership, and recovery refusal | [Phase 69.1 / JM69-03](../../../phase/phase-69.1.md) | No provider or durable schema is selected by this journal. |
| Stable bounded Job discovery, cursor continuation, exact typed result retrieval, and completed control surface | [Phase 69.2 / JM69-04](../../../phase/phase-69.2.md) | Current `listJobs` and offset/limit reads remain query-only evidence. |
| Executable JCL grammar/runtime, continuation, and pre-execution rejection | [Phase 69.3 / JM69-05](../../../phase/phase-69.3.md) | Current submission-only parse/submit baseline remains intact. |
| JobDefinition review/accept/apply, lifecycle rollout/rollback, and immutable running snapshots | [Phase 69.4 / JM69-06](../../../phase/phase-69.4.md) | Current create/update/activate/retire/search and launch snapshot are not promoted to governance. |
| Bounded CompositeQuery v2 composition, parallelism, cancellation, and partial-failure contract | [Phase 69.5 / JM69-07](../../../phase/phase-69.5.md) | Current sequential query-only CompositeQuery remains v1 evidence. |
| Authorized user/operator projections, controls, diagnostics, notification, and read-state UX | [Phase 69.6 / JM69-08](../../../phase/phase-69.6.md) | Current renderer and JobControl reads remain projections, not complete UX. |
| Retention, expiry/deletion, integrity, redaction, audit/operations, and downstream fresh-runtime CBD Support adoption | [Phase 69.7 / JM69-09/10](../../../phase/phase-69.7.md) | The rejected CBD spike is not revived; no security, retention, or downstream implementation is added. |

The [Job Management design](../../../design/job-management.md) and the
JM69-01A inventory describe forward-looking durable and consumer requirements;
they are gap evidence, not proof that the current in-memory implementation
already satisfies them. Existing failing-first identities remain planning
targets from JM69-01A. This Slice registers no new executable identity and
claims no future test passes.

## Scope disposition

This Slice changes documentation evidence only. It does not modify Scala,
tests, schema, configuration, provider implementations, Phase 69 status,
JM69-01/JM69-02 status, child Phase records, or the rejected CBD Support
spike. The only checklist change is the partial-evidence link to this journal.
