# Phase 69 - Comprehensive Job Management

status=planned
planned_at=2026-08-15
depends_on=[Phase 22](phase-22.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 69 Checklist](phase-69-checklist.md)

## Purpose

Complete CNCF Job Management as a production-capable execution, persistence,
recovery, orchestration, query, control, and user/operator-management facility.

Phase 69 promotes the complete strategy item `9.14 Job Management Follow-ups`
into an executable Phase. It reconciles the Phase 6 CQRS promises with the
Phase 22 lightweight Job/JobDefinition management baseline, makes Persistent
Job semantics survive a real process restart, and completes the deferred JCL,
JobDefinition, CompositeQuery, and Job UX work as one coherent management
surface.

## Dependency and Scheduling

Phase 69 begins after Phase 22 and is a separately selectable planning branch.
It does not renumber or change the existing Phase 63--68 dependency chains.
Only one CNCF Phase may be active at a time.

Before implementation starts, `JM69-01` must reconcile any Phase 63--68 work
that has closed or become active, especially StateMachine/Workflow execution,
DbC evaluation on Job paths, test invocation, and MCP task/result surfaces.

## Journal Deferred-Work Merge

Phase 69 may consume the Job-local retry and non-distributed failure-aggregation
portion of the Phase 3.1 Fat JAR Component deferral. That journal reference
does not assign retained result history or process-restart recovery; those are
independent Phase 69 and Strategy 9.14 commitments. The admitted retry and
aggregation evidence is bounded by versioned Job definitions, Task records,
persistence, authorization, and explicit replay policy.

This does not absorb generic event reception/outcome lanes (Strategy 9.2),
cross-operation compensation (9.10), distributed delivery/fencing (9.13), or
Saga coordination (9.15). Those items retain separate ownership even when a
Job supplies evidence consumed by them.

## Problem Statement

The current implementation uses `JobPersistencePolicy.Persistent`, but the
complete `JobRecord`, Task records, timelines, and result payloads remain in
`InMemoryJobEngine.State`. A second gateway sharing that state can rediscover a
Job, while a new process cannot reconstruct the same execution record from the
lightweight store-backed `JobEntity` projection.

The lightweight projection retains management fields and result summaries but
does not provide the complete task/result payload needed for restart-safe
query, reuse, or diagnosis. Separately, `JobEngine.listJobs` is bounded and
non-paginated, so consumers cannot enumerate an arbitrary retained set without
either an unsafe high bound or a false completeness assumption. Pagination and
process-restart recovery are distinct defects and must be solved independently.

This gap is visible in `textus-cbd-support`: a completed Persistent Review Job
can be rediscovered by a fresh gateway in the same JobEngine state, but the
current CNCF contract cannot guarantee the same result after a process restart.
The repair belongs in CNCF rather than in a ComponentFactory-local map or a
component-specific shadow Job store.

Phase 22 also intentionally deferred executable JCL `flow`, `events`, and
`onEvent`; JobDefinition accept/apply and rollout management; CompositeQuery
v2; durable execution diagnostics; and complete user/operator Job UX. Leaving
those concerns as separate implicit follow-ups would preserve an incomplete
Job Management boundary. Phase 69 therefore treats them as one production
completion program.

## Selected Direction

- `Persistent` means that admitted Job identity, lifecycle, binding metadata,
  Task execution records, result disposition, retained payload or payload
  reference, and required diagnostic history remain queryable according to
  retention policy after a real process restart.
- `Ephemeral` Jobs remain process/runtime records and never acquire durable
  state merely because they pass through a query, debug, or UI surface.
- The durable Job record is the recovery source for persistent execution;
  `JobEntity` remains the management representation but is extended or linked
  to versioned execution-record storage instead of carrying every large body
  inline.
- A terminal Persistent Job must restore exact stable identity, terminal
  status, typed result or retained result reference, Task summary/tree, and
  required timeline evidence. Missing or corrupt required state fails with a
  structured recovery outcome rather than an empty or successful projection.
- A non-terminal Job observed after a crash follows an explicit recovery
  classification: resumable, retryable, recovery-required, cancelled, or
  terminally failed. CNCF does not infer replay safety from a Scala object,
  operation name, or status label.
- CNCF never serializes arbitrary `JobTask` closures, live objects,
  `ExecutionContext`, credentials, provider handles, or classloader state.
  Resumption requires a closed, versioned, reconstructible execution
  descriptor and explicit replay/idempotency policy.
- Job query and enumeration use stable bounded cursor pagination with snapshot
  or continuation semantics. `listJobs(limit)` remains only a compatibility
  facade and cannot be the completeness authority.
- Result bodies, Task calltrees, full timelines, and raw execution/event
  history use explicit inline/externalized storage, integrity, retention,
  authorization, redaction, and deletion policies.
- Executable JCL provides a closed, validated runtime for procedural `flow` and
  Event-driven `events` / `onEvent`. It composes existing Action, Event,
  Operation, Task, Job, Workflow, authorization, and observability boundaries;
  it is not an embedded scripting engine.
- JobDefinition gains an explicit propose/reconstruct, review, accept, apply,
  activate, rollout, rollback, retire, and compatibility lifecycle. A running
  Job remains bound to its immutable accepted definition snapshot.
- CompositeQuery v2 provides bounded parallel query composition, explicit
  cross-subsystem protocol behavior, deterministic aggregation, cancellation,
  partial-failure policy, and App-tier page-view diagnostics.
- User and operator Job UX share canonical Job query/control contracts while
  keeping subject ownership, application views, system administration,
  notification state, and privileged recovery operations distinct.
- All persistence, recovery, query, control, JCL, CompositeQuery, and UX paths
  preserve existing authorization, tenant/subject isolation, structured
  failure, UnitOfWork, idempotency, execution-profile, and CallTree contracts.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| JM69-01 | Inventory and contract reconciliation | Phase 6/22 promises, current runtime/storage behavior, all 9.14 follow-ups, consumers, and failing-first acceptance identities are frozen without hidden debt. | planned |
| JM69-02 | Durable Job/Task execution-record model | Versioned Job, Task, timeline, result, input, diagnostic, definition-snapshot, retention, and migration contracts define one durable authority. | planned |
| JM69-03 | Persistent storage and process recovery | Durable providers, checkpointing, reconstruction, crash classification, retry/resume, corruption handling, and restart behavior are implemented without arbitrary object serialization. | planned |
| JM69-04 | Query, pagination, result, and control completion | Cursor-based Job/Task/timeline search, exact typed result retrieval, authorization, cancellation/retry/recovery control, and compatibility APIs are complete. | planned |
| JM69-05 | Executable JCL runtime | Closed `flow`, `events`, and `onEvent` semantics execute through existing CNCF Action/Event/Operation/Task/Job boundaries with bounded deterministic behavior. | planned |
| JM69-06 | JobDefinition governance and rollout | Reconstructed and authored definitions have review, accept/apply, activation, rollout, rollback, retirement, immutable snapshot, and compatibility workflows. | planned |
| JM69-07 | CompositeQuery v2 | Bounded parallel and cross-subsystem query composition has deterministic ordering, cancellation, partial-failure, security, and App-tier projection semantics. | planned |
| JM69-08 | User and operator Job experience | My Jobs, application Job views, system administration, notifications, read state, recovery actions, and diagnostics use one authorized management contract. | planned |
| JM69-09 | Security, retention, observability, and operations | Payload protection, redaction, quotas, expiry/deletion, integrity, audit, metrics, CallTree linkage, health, and maintenance operations are production-ready. | planned |
| JM69-10 | Cross-process and downstream acceptance | Real process restart, migration, load/bound, hostile-state, and representative Textus/CBD acceptance pass before contracts are promoted and the Phase closes. | planned |

## Acceptance

- A Persistent Job submitted in one runtime process remains queryable by exact
  Job ID from a newly started process using only configured durable providers.
- A terminal Job restores the same stable identity, terminal status, typed
  Operation result or verified payload reference, definition snapshot, Task
  tree/summary, and required timeline evidence.
- Persistent Job enumeration traverses more than one page without duplicates,
  omissions, unstable ordering, or an unsafe unbounded read; filters and
  authorization remain deterministic across continuation.
- Ephemeral Jobs and payloads do not reappear after restart and are never
  copied into durable storage by management, observability, or compatibility
  paths.
- Scheduled starts and delayed retries resume once according to their durable
  due state. Crash-interrupted work is never silently reported as running or
  succeeded and never re-executes without an admitted replay policy.
- Missing, truncated, corrupt, incompatible, expired, or unauthorized Job,
  Task, result, input, timeline, calltree, and definition records produce
  stable structured outcomes without unsafe payload disclosure.
- Inline and externalized payloads have canonical digests, size/count bounds,
  retention, deletion, legal-hold/administrative boundaries where applicable,
  and no dangling success claim after payload loss.
- Executable JCL runs admitted sequential and Event-driven examples with
  deterministic identity, ordering, guards, retries, continuation, and
  failure behavior through existing runtime authorities.
- Unsupported, cyclic, unbounded, incompatible, or script-like JCL fails
  before execution and cannot become silently inactive metadata.
- A reconstructed JobDefinition can be reviewed, accepted, applied, activated,
  rolled out, rolled back, and retired without changing the immutable snapshot
  bound to already submitted Jobs.
- CompositeQuery v2 executes bounded parallel branches, cancels remaining work
  according to policy, preserves deterministic output ordering, and reports
  partial/total failure without bypassing subsystem or authorization borders.
- Users see only their admitted application Jobs and notifications; operators
  receive bounded administrative search, diagnosis, recovery, retention, and
  health controls according to explicit capabilities.
- A fresh `textus-cbd-support` runtime can discover the exact completed Review
  Job and canonical result through CNCF durable Job APIs without a
  ComponentFactory-local map or component-specific Job persistence kernel.
- Existing Phase 6, Phase 14, Phase 22, retry/dead-letter, Workflow, DbC,
  event-continuation, notification, debug trace-job, and synchronous result
  compatibility behavior passes focused regression evidence.
- Focused and full CNCF validation, cross-process acceptance, representative
  downstream validation, review convergence, documentation promotion, version
  evidence, and the Phase release commit complete before closure.

## Non-Goals

- Serializing arbitrary JVM objects, functions, closures, threads,
  `ExecutionContext`, open streams, provider handles, credentials, or
  classloader state.
- Claiming exactly-once external side effects across a crash without an
  explicit idempotent provider/Operation contract.
- Building a general-purpose script runtime, BPMN platform, unrestricted DAG
  engine, or external orchestrator replacement inside JCL.
- Distributed leader election, cluster ownership, fencing, remote scheduling,
  or cross-runtime Saga coordination; those remain under strategy items 9.13
  and 9.15.
- Replacing CNCF Entity persistence, BlobStore, EventStore, Observability, or
  authorization with a second Job-private infrastructure kernel.
- Adding application-specific business workflow, component-specific result
  schemas, or notification delivery logic to JobEngine.
- Treating every synchronous CRUD-style Command or ordinary Query as a
  Persistent Job.

## Development Candidate Alignment

| Strategy item | Phase 69 relationship | Retained scope |
| --- | --- | --- |
| 9.2 Event Mechanism Follow-ups | Implements only Event behavior required by executable JCL and durable Job continuation. | Generic Event policy unrelated to Job/JCL remains future work. |
| 9.4 Metrics and Observability | Implements Job-specific durable diagnostics, payload references, metrics, audit, and operations. | Platform-wide exporters, dashboards, and unrelated observability retention remain future work. |
| 9.13 Distributed Component Runtime | Fixes local durable identity and recovery interfaces that a distributed implementation may consume. | Cluster ownership, fencing, leader election, delivery, and coherence remain future work. |
| 9.14 Job Management Follow-ups | Phase 69 owns and closes the complete currently listed follow-up set. | Newly discovered follow-ups require explicit post-Phase disposition rather than implicit carryover. |
| 9.15 Saga Management | Supplies durable local Job/Task history, retry, and compensation evidence. | Distributed Saga identity, remote coordination, and cross-system compensation remain future work. |
| 9.43 Transport Idempotency | Supplies stable Job submission/recovery identities and replay evidence. | HTTP/MCP token, request fingerprint, response replay, and transport stores remain independent. |

## Planning References

- [Phase 69 Checklist](phase-69-checklist.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
- [Phase 6 Job Management](phase-6.md)
- [Phase 6 Checklist](phase-6-checklist.md)
- [Phase 22 Job Management](phase-22.md)
- [Phase 22 Checklist](phase-22-checklist.md)
- [Job Management Design](../design/job-management.md)
- [Job Task Execution Persistence Design](../journal/2026/03/job-task-execution-persistence-design.md)
- `textus-cbd-support:src/main/scala/org/simplemodeling/textus/cbdsupport/runtime/CncfCarReviewJobGateway.scala`
