# Phase 69 Checklist - Durable Job and Task Contract Foundation

status=closed
phase=[Phase 69](phase-69.md)

Only one stage may be `IN_PROGRESS`. This ledger owns only `JM69-01` and
`JM69-02`; all later original Phase 69 stages are owned exactly once by the
linked child checklists.

## JM69-01: Inventory and Contract Reconciliation

Stage Status:
- Current status: DONE
- Owner: CNCF Job, Task, JCL, JobDefinition, query, persistence, security, and observability maintainers
- Update rule: Update only when the complete inventory or failing-first acceptance identities change or are frozen.
- Entry rule: Phase 22 is closed and no other CNCF Phase is active.
- Completion rule: Every completed promise, implementation fact, gap, consumer, retained boundary, and failing-first acceptance identity is frozen.

- [x] Reconcile Phase 6/14/22 Job, Task, Event, retry, result, control, persistence, notification, and CompositeQuery boundaries with current behavior.
- [x] Inventory strategy 9.14 ownership, Persistent/Ephemeral submission, in-memory state, Job Entity synchronization, providers, BlobStore, EventStore, CallTree, cleanup, and all public/compatibility consumers.
- [x] Inventory reserved JCL surfaces, JobDefinition governance gaps, CompositeQuery v1 consumers, user/operator surfaces, and downstream Persistent Job consumers including CBD Support.
- [x] Distinguish non-paginated discovery from process-restart reconstruction; reconcile intersecting Phase 63--68 contracts without absorbing unrelated scope.
- [x] Register exact failing-first Executable Specifications, including a real new-process fixture, for every child acceptance group.

Evidence:
- Partial inventory baseline: [JM69-01A Job Contract Inventory Baseline](../journal/2026/09/2026-09-09-phase-69-job-contract-inventory.md).
- Partial integration and consumer inventory: [JM69-01B Job Integration and Consumer Inventory](../journal/2026/09/2026-09-09-phase-69-job-integration-consumer-inventory.md).
- Closure reconciliation: [JM69-01C Inventory Reconciliation Closure](../journal/2026/09/2026-09-09-phase-69-jm69-01-reconciliation-closure.md).

## JM69-02: Durable Job and Task Execution-Record Model

Stage Status:
- Current status: DONE
- Owner: CNCF Job model, Entity persistence, BlobStore, EventStore, codec, retention, and migration maintainers
- Update rule: Update only when the durable schema, ownership, retention, migration, or executable evidence changes.
- Entry rule: JM69-01 is DONE.
- Completion rule: One versioned durable authority defines every retained Job, Task, result, input, timeline, diagnostic, and definition-snapshot field.

- [x] Define canonical Job/Task/tree/attempt/timeline/Event, typed result/input, CallTree, definition snapshot, and closed reconstructible execution-descriptor records.
- [x] Define schema versioning, canonical encoding, compatibility, migration, corruption, retention, expiry, deletion, tombstone, integrity, authorization, tenant/subject isolation, redaction, and provider responsibilities.
- [x] Prohibit arbitrary task/object/closure/context/provider/classloader serialization at model and codec boundaries.
- [x] Add model, codec, round-trip, migration, integrity, redaction, hostile-record, and deterministic-encoding specifications.

Evidence:
- Normative handoff: [Durable Job Record Contract](../spec/durable-job-record-contract.md).
- Focused executable evidence: `DurableJobRecordSpec`, 6 passed, 0 failed.
- Accepted Step commit: `cfd0cecbd4692df1fad6172c64d973bc1c111e79`.
- Required Phase full review: `P69-FULL-REVIEW-001`, clean.

## Phase Completion Gate

- [x] JM69-01 and JM69-02 are DONE.
- [x] The frozen durable-contract handoff identifies record ownership, recovery descriptor, codec/migration, authorization, retention, and every child acceptance identity.
- [x] Phase 69.1 dependency and the exact owned child links are recorded.
