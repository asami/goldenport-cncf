# Phase 69 Checklist - Comprehensive Job Management

status=planned
phase=[Phase 69 - Comprehensive Job Management](phase-69.md)

This checklist is the authoritative Phase 69 state ledger after Phase 69
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 69 is a separately
selectable planning branch after Phase 22. No implementation starts before
JM69-01 freezes the effective contracts and accepted repository set.

## JM69-01: Inventory and Contract Reconciliation

Stage Status:
- Current status: OPEN
- Owner: CNCF Job, Task, JCL, JobDefinition, query, persistence, Web, security, and observability maintainers
- Update rule: Update when the complete inventory and failing-first acceptance identity set changes or is frozen.
- Entry rule: Phase 22 is closed and no other CNCF Phase is active.
- Completion rule: Every completed promise, implementation fact, gap, consumer, retained boundary, and failing-first acceptance identity is frozen.

- [ ] Reconcile Phase 6 Job CQRS requirements with current `JobEngine`, query,
      result, control, Task-first, Event, and persistence behavior.
- [ ] Reconcile Phase 14 delayed start/retry rehydration and Phase 22 Job,
      JobDefinition, execution-record, notification, and CompositeQuery
      boundaries.
- [ ] Inventory all strategy 9.14 items and prove that each is owned by exactly
      one Phase 69 stage or explicitly rejected as a non-goal.
- [ ] Inventory Persistent/Ephemeral submission paths, `InMemoryJobEngine.State`,
      Job Entity synchronization, durable providers, input retention,
      BlobStore, EventStore, CallTree externalization, and cleanup behavior.
- [ ] Inventory Job/Task/timeline/result/list/query/control public APIs, Help,
      HTTP, CLI, Web, MCP-adjacent, and compatibility consumers.
- [ ] Inventory JCL `profile`, reserved `flow`, `events`, `onEvent`, current
      parser/model/metadata, Workflow overlap, and unsupported-language risks.
- [ ] Inventory JobDefinition create/update/activate/retire, reconstruction,
      immutable launch snapshots, compatibility, and absent governance actions.
- [ ] Inventory CompositeQuery v1 consumers and requirements for bounded
      parallel and cross-subsystem v2 behavior.
- [ ] Inventory My Jobs, application Job, system admin, notification, read
      state, recovery, retention, and health UX surfaces.
- [ ] Inventory downstream persistent Job consumers, including CBD Support,
      Blog notification, Workflow, Event, debug trace-job, and representative
      generated Components.
- [ ] Distinguish non-paginated discovery from process-restart reconstruction
      and register separate failing-first evidence for each.
- [ ] Reconcile closed or active Phase 63--68 contracts that intersect Job
      execution without absorbing unrelated scope.
- [ ] Register exact failing-first Executable Specifications for every Phase 69
      acceptance group, including a real new-process restart fixture.

Evidence:
- Pending.

## JM69-02: Durable Job and Task Execution-Record Model

Stage Status:
- Current status: OPEN
- Owner: CNCF Job model, Entity persistence, BlobStore, EventStore, codec, retention, and migration maintainers
- Update rule: Update when the durable schema, ownership, retention, migration, or executable evidence changes.
- Entry rule: JM69-01 is DONE.
- Completion rule: One versioned durable authority defines every retained Job, Task, result, input, timeline, diagnostic, and definition-snapshot field.

- [ ] Define the canonical durable Job record and relationship to the
      lightweight Job Entity management projection.
- [ ] Define durable Task identity, parent/child tree, attempt, status,
      timestamps, target, result summary, compensation, retry, and recovery
      fields.
- [ ] Define full timeline and raw execution/Event history storage boundaries,
      ordering, pagination keys, and integrity records.
- [ ] Define typed Operation result retention with inline/externalized payload,
      content type/schema identity, byte size, digest, and availability state.
- [ ] Define Job input retention and reconstruction references without storing
      credentials, live provider state, or unsafe object graphs.
- [ ] Define task-local and Job-level CallTree reference/summary boundaries.
- [ ] Define immutable JobDefinition snapshot linkage and the closed execution
      descriptor required for safe reconstruction.
- [ ] Define schema versions, canonical encoding, compatibility, migration,
      unsupported-required-field, and corruption behavior.
- [ ] Define retention, expiry, deletion, legal hold where applicable,
      referential integrity, tombstone/audit, and dangling-reference policy.
- [ ] Define authorization, tenant/subject ownership, redaction, encryption or
      provider responsibility, and safe diagnostic facets for every record.
- [ ] Prohibit arbitrary task/object/closure/context/provider/classloader
      serialization at the model and codec boundaries.
- [ ] Add model, codec, round-trip, migration, integrity, redaction, hostile
      record, and property-based deterministic encoding specifications.

Evidence:
- Pending.

## JM69-03: Persistent Storage and Process Recovery

Stage Status:
- Current status: OPEN
- Owner: CNCF JobEngine, scheduler, persistence-provider, recovery, retry, and migration maintainers
- Update rule: Update when recovery classification, implementation, or cross-process evidence changes.
- Entry rule: JM69-02 is DONE.
- Completion rule: Persistent Jobs reconstruct deterministically in a new process while Ephemeral Jobs remain runtime-only.

- [ ] Implement canonical durable create/checkpoint/update/terminal writes with
      revision or equivalent concurrency protection.
- [ ] Make Job submission atomic with the minimum durable identity and
      execution descriptor required to avoid an untraceable accepted Job.
- [ ] Reconstruct retained terminal Jobs, Task records, results/references,
      timelines, definition snapshots, retry state, and diagnostics in a new
      JobEngine process.
- [ ] Rehydrate scheduled starts and delayed retries exactly once according to
      durable due state and existing deterministic scheduler contracts.
- [ ] Classify crash-interrupted work as resumable, retryable,
      recovery-required, cancelled, or failed through explicit descriptor and
      policy evidence.
- [ ] Prevent automatic replay when idempotency, input, definition,
      authorization, provider, or compatibility evidence is absent.
- [ ] Define checkpoint ordering with UnitOfWork and external side effects;
      never infer exactly-once delivery from Job persistence alone.
- [ ] Implement bounded startup recovery, continuation checkpoints, health,
      backpressure, and operator-visible incomplete recovery state.
- [ ] Handle missing, corrupt, stale, incompatible, concurrently modified, or
      partially written records with structured outcomes and no false success.
- [ ] Preserve Ephemeral Job non-persistence across every submission, query,
      debug, notification, and observability path.
- [ ] Add real new-process terminal restore, scheduled/retry restore,
      interrupted-work classification, corruption, migration, concurrency,
      and Ephemeral absence Executable Specifications.

Evidence:
- Pending.

## JM69-04: Query, Pagination, Result, and Control Completion

Stage Status:
- Current status: OPEN
- Owner: CNCF Job query/control, protocol, Help, HTTP, CLI, and authorization maintainers
- Update rule: Update when public query/control contracts or their executable evidence changes.
- Entry rule: JM69-03 is DONE.
- Completion rule: Exact and enumerated Job management reads and controls remain complete, bounded, authorized, and restart-safe.

- [ ] Define cursor/snapshot pagination for Job, Task, timeline, history, and
      administrative search with stable ordering and continuation validity.
- [ ] Define filters for persistence, status, submitter, target, definition,
      time, parent/correlation, application, and recovery state without
      allowing unbounded scans.
- [ ] Implement exact Job and typed result retrieval from durable state after
      restart, including externalized payload verification and absence states.
- [ ] Implement Task page/detail/tree and timeline/history reads without
      loading every retained record or payload.
- [ ] Preserve query/control authorization and subject/tenant isolation before
      returning existence, counts, cursors, payload metadata, or diagnostics.
- [ ] Complete cancel, retry, suspend, resume, recovery acknowledge/retry, and
      retention controls with state-guarded idempotent outcomes.
- [ ] Keep `listJobs(limit)` as a bounded compatibility facade and document
      that cursor APIs are the completeness authority.
- [ ] Project stable Help/meta/Record/JSON/HTTP/CLI contracts and structured
      invalid-cursor, expired-snapshot, unavailable-payload, and recovery
      outcomes.
- [ ] Add multi-page no-duplicate/no-omission, concurrent insert/update,
      restart continuation, authorization, payload, control, and compatibility
      Executable Specifications.

Evidence:
- Pending.

## JM69-05: Executable JCL Runtime

Stage Status:
- Current status: OPEN
- Owner: CNCF JCL, JobDefinition, Action, Event, Operation, Task, Workflow, scheduler, and security maintainers
- Update rule: Update when executable JCL semantics, runtime behavior, or acceptance evidence changes.
- Entry rule: JM69-04 is DONE.
- Completion rule: Closed procedural and Event-driven JCL executes deterministically through existing CNCF runtime authorities.

- [ ] Freeze the executable grammar and typed model for `flow`, `events`, and
      `onEvent` without changing diagnostics-only `profile` semantics.
- [ ] Define stable step/event/action identities, sequencing, conditions,
      continuation, terminal outcomes, retry, timeout, cancellation, and
      bounded iteration semantics.
- [ ] Define the exact relationship between JCL and Phase 14/64 Workflow so
      neither language/runtime silently owns the same progression.
- [ ] Route every executable node through admitted Action/Event/Operation/Task/
      Job, authorization, UnitOfWork, execution-profile, and observability
      boundaries.
- [ ] Define event correlation, causation, replay, duplicate delivery,
      same/new-Job continuation, and required/possible/forbidden expectations.
- [ ] Reject cycles without an admitted bound, unknown targets, incompatible
      schemas/definitions, ambiguous handlers, unsupported effects, and
      script-like content before execution.
- [ ] Persist executable position and continuation state using the JM69-02/03
      durable record and recovery contracts.
- [ ] Preserve immutable accepted JobDefinition snapshots for every execution.
- [ ] Add positive, negative, property-based determinism, replay, retry,
      cancellation, restart, unsupported syntax, and no-bypass specifications.

Evidence:
- Pending.

## JM69-06: JobDefinition Governance and Rollout

Stage Status:
- Current status: OPEN
- Owner: CNCF JobDefinition model, application/admin, compatibility, audit, and rollout maintainers
- Update rule: Update when definition governance, rollout behavior, or executable evidence changes.
- Entry rule: JM69-05 is DONE.
- Completion rule: Authored and reconstructed definitions have a complete reviewed lifecycle and running Jobs retain immutable meaning.

- [ ] Define draft, proposed, reviewed, accepted, active, superseded, retired,
      rejected, and rollback state/transition semantics as required.
- [ ] Define reconstruction provenance, human/operator review, diff,
      compatibility, validation, and accept/apply boundaries.
- [ ] Define version/revision/hash identity, immutable accepted content,
      optimistic concurrency, duplicate/conflict, and history semantics.
- [ ] Define activation, staged rollout, compatibility gates, rollback,
      retirement, and new-submission selection without mutating running Jobs.
- [ ] Define authorization separation among author, reviewer, deployer,
      operator, and reader capabilities.
- [ ] Define migration behavior for legacy inline JCL and existing active
      JobDefinition records.
- [ ] Project safe definition source/profile/flow/event diagnostics and audit
      without exposing credentials or runtime payloads.
- [ ] Add lifecycle, review, conflict, rollout, rollback, immutable-snapshot,
      compatibility, authorization, and migration Executable Specifications.

Evidence:
- Pending.

## JM69-07: CompositeQuery v2

Stage Status:
- Current status: OPEN
- Owner: CNCF CompositeQuery, App/Domain protocol, subsystem, scheduler, cancellation, and observability maintainers
- Update rule: Update when CompositeQuery v2 contracts, implementation, or executable evidence changes.
- Entry rule: JM69-06 is DONE.
- Completion rule: Bounded parallel and cross-subsystem query composition is deterministic, secure, cancellable, and diagnosable.

- [ ] Inventory CompositeQuery v1 and page-view consumers without moving
      presentation composition into Domain logic.
- [ ] Define typed branch, dependency, ordering, timeout, cancellation,
      partial-failure, fallback, and aggregate-result semantics.
- [ ] Define a cross-subsystem protocol surface that preserves Component/
      Subsystem ownership, authorization, subject, tenant, and trace context.
- [ ] Implement bounded parallel execution using CNCF-owned scheduling and
      explicit concurrency/resource limits.
- [ ] Preserve deterministic output ordering independently of completion order.
- [ ] Define whether and when CompositeQuery executions use Ephemeral or
      Persistent Jobs without silently persisting ordinary Query payloads.
- [ ] Project branch outcome, latency, cancellation, and partial-failure
      evidence through bounded redacted diagnostics.
- [ ] Add sequential/parallel equivalence, dependency ordering, cancellation,
      timeout, partial failure, authorization, cross-subsystem, bound, and
      deterministic-result specifications.

Evidence:
- Pending.

## JM69-08: User and Operator Job Experience

Stage Status:
- Current status: OPEN
- Owner: CNCF Job application/admin, Web, Help, notification, accessibility, and operator maintainers
- Update rule: Update when user/operator surfaces, policy, or executable evidence changes.
- Entry rule: JM69-07 is DONE.
- Completion rule: Users and operators can discover, understand, control, and recover admitted Jobs through one canonical management model.

- [ ] Define My Jobs, application Job, and system administration projections
      with explicit ownership and capability boundaries.
- [ ] Define user-visible status, progress, completion, failure, cancellation,
      recovery-required, result availability, expiry, and actionable next-step
      vocabulary.
- [ ] Define notification creation/update, unread/read state, deduplication,
      links, expiry, and provider-unavailable behavior without moving delivery
      implementation into JobEngine.
- [ ] Define operator search, Task/timeline/result diagnostics, retry/recovery,
      retention/deletion, definition, queue, scheduler, and health operations.
- [ ] Implement descriptor-backed Web/Help/API surfaces without a parallel Job
      model or application-specific hardcoding.
- [ ] Preserve accessibility, progressive enhancement, safe polling/refresh,
      bounded pages, CSRF, authorization, and redaction contracts.
- [ ] Add user/operator isolation, notification, read-state, accessibility,
      control, recovery, pagination, expired-result, and provider-failure
      Executable Specifications.

Evidence:
- Pending.

## JM69-09: Security, Retention, Observability, and Operations

Stage Status:
- Current status: OPEN
- Owner: CNCF security, persistence, retention, observability, metrics, audit, health, and operations maintainers
- Update rule: Update when operational policy, implementation, or executable evidence changes.
- Entry rule: JM69-08 is DONE.
- Completion rule: Production Job Management remains bounded, recoverable, diagnosable, and safe throughout its retained lifecycle.

- [ ] Define quotas and bounds for active/retained Jobs, Tasks, timeline/events,
      results, inputs, calltrees, definitions, pages, recovery, and JCL work.
- [ ] Define retention classes, expiry, deletion, legal hold where applicable,
      payload/reference cleanup, audit tombstones, and maintenance scheduling.
- [ ] Define integrity verification, backup/restore responsibility, migration
      checkpoints, health/readiness, repair refusal, and operator escalation.
- [ ] Define redaction and safe facets for inputs, results, errors, JCL,
      definitions, providers, credentials, subjects, tenants, and paths.
- [ ] Correlate Job, Task, attempt, Event, Operation, Workflow, definition,
      trace/span, CallTree, payload, and recovery identities without retaining
      unnecessary sensitive content.
- [ ] Define metrics for admission, queue, execution, retry, recovery,
      pagination, storage, expiry, corruption, payload, and control outcomes.
- [ ] Add load/bound, quota, retention, expiry/deletion, integrity, redaction,
      audit, health, maintenance, and hostile-access Executable Specifications.

Evidence:
- Pending.

## JM69-10: Cross-Process and Downstream Acceptance

Stage Status:
- Current status: OPEN
- Owner: CNCF, representative Textus consumers, documentation, validation, review, and release maintainers
- Update rule: Update when final acceptance, review, validation, or release evidence changes.
- Entry rule: JM69-09 is DONE.
- Completion rule: The complete Job Management contract passes real restart, downstream, review, and release gates before Phase closure.

- [ ] Run a real two-process fixture: submit/complete in process A, terminate
      A, start process B with the same durable provider, then query exact Job,
      result, Tasks, timeline, and definition snapshot.
- [ ] Run scheduled/retry and crash-interrupted fixtures across process
      replacement, proving admitted recovery and no unauthorized replay.
- [ ] Prove cursor traversal beyond the legacy `listJobs` limit before and
      after restart with deterministic filters and authorization.
- [ ] Prove inline/externalized result, input, timeline, calltree, expiry,
      deletion, corruption, incompatible-version, and migration behavior.
- [ ] Prove executable JCL, JobDefinition governance, CompositeQuery v2, user
      Job, admin/recovery, notification, and security paths through real
      runtime boundaries.
- [ ] Add CBD Support acceptance proving a fresh runtime can recover the exact
      completed Review Job/result without a ComponentFactory-local map or
      component-owned shadow Job store.
- [ ] Run focused regressions for Phase 6, 14, 22, Event, Workflow, DbC,
      notification, debug trace-job, and synchronous compatibility surfaces.
- [ ] Update design, specification, developer/operator/user guidance, Help,
      strategy, Phase ledger, and journal from accepted evidence.
- [ ] Run full CNCF and required downstream validation, review, review-fix,
      re-review, version checks, and release preparation.
- [ ] Record exact commands, counts, process identities, storage provider,
      fixtures, artifact/diff identity, accepted limitations, and release
      commit evidence before closing the Phase.

Evidence:
- Pending.

## Phase Completion Gate

- [ ] JM69-01 through JM69-10 are DONE.
- [ ] Every Phase 69 acceptance statement has executable or exact operational
      evidence.
- [ ] Strategy item 9.14 has no implicit remaining item; anything deliberately
      retained is assigned to an explicit named candidate with rationale.
- [ ] Phase 6/22 documentation and current implementation contracts agree.
- [ ] Real process restart and multi-page enumeration acceptance pass.
- [ ] Representative CBD Support acceptance passes without local shadow state.
- [ ] Required focused and full validations, review convergence, documentation
      promotion, version evidence, release commit, and clean intended tree are
      recorded.
